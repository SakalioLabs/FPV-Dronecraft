#include <cuda_runtime.h>

#define MCFPV_DDA_BUNDLE_CORE_ONLY
#include "../../dda-production-bundle/src/dda_bundle_verify.cpp"

#include "bounded_batch_plan.hpp"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <iomanip>
#include <iostream>
#include <limits>
#include <optional>
#include <sstream>
#include <stdexcept>
#include <string>
#include <string_view>
#include <vector>

namespace {

using Clock = std::chrono::steady_clock;

constexpr std::uint8_t kReachedFlag = 1U;
constexpr std::uint8_t kStoppedFlag = 2U;
constexpr std::uint8_t kTruncatedFlag = 4U;
constexpr std::size_t kBands = 3U;
constexpr std::uint64_t kDefaultMaximumRaysPerBatch = 8192U;
constexpr std::uint64_t kDefaultMaximumSegmentsPerBatch = 1048576U;

__constant__ double kDeviceTransmission[8 * kBands];

struct DeviceCell {
	std::uint64_t packed;
	std::int32_t material_id;
	double fill_fraction;
};

struct DeviceRay {
	double start[3];
	double end[3];
	std::int32_t maximum_cells;
	std::uint64_t segment_offset;
};

struct DeviceSegment {
	std::uint64_t packed;
	double length;
	std::int32_t material_id;
	double fill_fraction;
};
static_assert(
		sizeof(DeviceSegment) == 32U,
		"DeviceSegment memory budget contract requires 32-byte records"
);

struct DeviceResult {
	std::int32_t segment_count;
	std::int32_t material_cells;
	std::uint8_t flags;
	std::uint8_t has_first_material;
	std::uint16_t reserved;
	std::uint64_t first_material;
	double loss[kBands];
	double gain[kBands];
};

struct Arguments {
	std::filesystem::path bundle;
	std::optional<std::filesystem::path> expected_results;
	std::int32_t warmup = 20;
	std::int32_t iterations = 200;
	std::uint64_t maximum_rays_per_batch = kDefaultMaximumRaysPerBatch;
	std::uint64_t maximum_segments_per_batch =
			kDefaultMaximumSegmentsPerBatch;
};

struct StageSamples {
	std::vector<double> h2d_ms;
	std::vector<double> kernel_ms;
	std::vector<double> d2h_ms;
	std::vector<double> total_ms;
};

void require_cuda(
		const cudaError_t status,
		const std::string_view operation
	) {
	if (status != cudaSuccess) {
		throw std::runtime_error(
				std::string(operation) + ": "
				+ cudaGetErrorString(status)
		);
	}
}

[[nodiscard]] std::int32_t positive_integer(
		const char* text,
		const std::string_view name
	) {
	char* end = nullptr;
	const long value = std::strtol(text, &end, 10);
	if (end == text || *end != '\0' || value < 1
			|| value > std::numeric_limits<std::int32_t>::max()) {
		throw std::runtime_error(
				std::string(name) + " must be a positive int32"
		);
	}
	return static_cast<std::int32_t>(value);
}

[[nodiscard]] std::uint64_t positive_uint64(
		const char* text,
		const std::string_view name
	) {
	char* end = nullptr;
	if (text[0] == '-') {
		throw std::runtime_error(
				std::string(name) + " must be a positive uint64"
		);
	}
	const unsigned long long value = std::strtoull(text, &end, 10);
	if (end == text || *end != '\0' || value == 0U) {
		throw std::runtime_error(
				std::string(name) + " must be a positive uint64"
		);
	}
	return static_cast<std::uint64_t>(value);
}

[[nodiscard]] Arguments parse_arguments(int argc, char** argv) {
	Arguments result;
	for (int index = 1; index < argc; ++index) {
		const std::string_view argument = argv[index];
		if (argument == "--warmup") {
			if (++index >= argc) {
				throw std::runtime_error("--warmup requires one integer");
			}
			result.warmup = positive_integer(argv[index], "--warmup");
		} else if (argument == "--iterations") {
			if (++index >= argc) {
				throw std::runtime_error("--iterations requires one integer");
			}
			result.iterations = positive_integer(
					argv[index],
					"--iterations"
			);
		} else if (argument == "--expected-results") {
			if (++index >= argc || result.expected_results.has_value()) {
				throw std::runtime_error(
						"--expected-results requires one path"
				);
			}
			result.expected_results = argv[index];
		} else if (argument == "--maximum-rays-per-batch") {
			if (++index >= argc) {
				throw std::runtime_error(
						"--maximum-rays-per-batch requires one integer"
				);
			}
			result.maximum_rays_per_batch = positive_uint64(
					argv[index],
					"--maximum-rays-per-batch"
			);
		} else if (argument == "--maximum-segments-per-batch") {
			if (++index >= argc) {
				throw std::runtime_error(
						"--maximum-segments-per-batch requires one integer"
				);
			}
			result.maximum_segments_per_batch = positive_uint64(
					argv[index],
					"--maximum-segments-per-batch"
			);
		} else if (result.bundle.empty()) {
			result.bundle = argv[index];
		} else {
			throw std::runtime_error("unexpected command-line argument");
		}
	}
	if (result.bundle.empty()) {
		throw std::runtime_error(
				"usage: mcfpv_dda_cuda "
				"[--warmup N] [--iterations N] "
				"[--maximum-rays-per-batch N] "
				"[--maximum-segments-per-batch N] "
				"[--expected-results <sidecar>] <bundle>"
		);
	}
	return result;
}

__device__ std::uint64_t device_pack_cell(
		const std::int32_t x,
		const std::int32_t y,
		const std::int32_t z
	) {
	return (static_cast<std::uint64_t>(x) & 0x3ffffffULL) << 38U
			| (static_cast<std::uint64_t>(z) & 0x3ffffffULL) << 12U
			| (static_cast<std::uint64_t>(y) & 0xfffULL);
}

__device__ DeviceCell device_sample_cell(
		const DeviceCell* cells,
		const std::int32_t cell_count,
		const std::uint64_t packed
	) {
	std::int32_t low = 0;
	std::int32_t high = cell_count;
	while (low < high) {
		const std::int32_t middle = low + (high - low) / 2;
		if (cells[middle].packed < packed) {
			low = middle + 1;
		} else {
			high = middle;
		}
	}
	if (low < cell_count && cells[low].packed == packed) {
		return cells[low];
	}
	return DeviceCell{packed, 0, 0.0};
}

__device__ double device_initial_t(
		const double start,
		const double delta,
		const std::int32_t cell,
		const std::int32_t step
	) {
	if (step == 0) {
		return CUDART_INF;
	}
	const double boundary = step > 0
			? static_cast<double>(cell) + 1.0
			: static_cast<double>(cell);
	return (boundary - start) / delta;
}

__device__ void visit_cell(
		const DeviceCell* cells,
		const std::int32_t cell_count,
		DeviceSegment* segments,
		DeviceResult& result,
		const std::uint64_t segment_offset,
		const std::int32_t x,
		const std::int32_t y,
		const std::int32_t z,
		const double length
	) {
	const std::uint64_t packed = device_pack_cell(x, y, z);
	const DeviceCell cell = device_sample_cell(cells, cell_count, packed);
	const double effective_length = length * cell.fill_fraction;
	for (std::size_t band = 0; band < kBands; ++band) {
		result.loss[band] += kDeviceTransmission[
				static_cast<std::size_t>(cell.material_id) * kBands + band
		] * effective_length;
	}
	if (cell.material_id != 0 && effective_length > 0.0) {
		++result.material_cells;
		if (result.has_first_material == 0U) {
			result.has_first_material = 1U;
			result.first_material = packed;
		}
	}
	segments[segment_offset
			+ static_cast<std::uint64_t>(result.segment_count)] = {
				packed,
				length,
				cell.material_id,
				cell.fill_fraction
			};
	++result.segment_count;
}

__global__ void trace_kernel(
		const DeviceCell* cells,
		const std::int32_t cell_count,
		const DeviceRay* rays,
		const std::int32_t ray_count,
		DeviceSegment* segments,
		DeviceResult* results
	) {
	const std::int32_t ray_index = static_cast<std::int32_t>(
			blockIdx.x * blockDim.x + threadIdx.x
	);
	if (ray_index >= ray_count) {
		return;
	}

	const DeviceRay ray = rays[ray_index];
	DeviceResult result{};
	const double delta[3] = {
			ray.end[0] - ray.start[0],
			ray.end[1] - ray.start[1],
			ray.end[2] - ray.start[2]
	};
	const double total_length = sqrt(
			delta[0] * delta[0]
			+ delta[1] * delta[1]
			+ delta[2] * delta[2]
	);

	if (total_length <= 1.0e-12) {
		visit_cell(
				cells,
				cell_count,
				segments,
				result,
				ray.segment_offset,
				static_cast<std::int32_t>(floor(ray.start[0])),
				static_cast<std::int32_t>(floor(ray.start[1])),
				static_cast<std::int32_t>(floor(ray.start[2])),
				0.0
		);
		result.flags = kReachedFlag;
	} else {
		std::int32_t current[3] = {
				static_cast<std::int32_t>(floor(ray.start[0])),
				static_cast<std::int32_t>(floor(ray.start[1])),
				static_cast<std::int32_t>(floor(ray.start[2]))
		};
		const std::int32_t target[3] = {
				static_cast<std::int32_t>(floor(ray.end[0])),
				static_cast<std::int32_t>(floor(ray.end[1])),
				static_cast<std::int32_t>(floor(ray.end[2]))
		};
		std::int32_t step[3];
		double t_delta[3];
		double t_max[3];
		for (std::int32_t axis = 0; axis < 3; ++axis) {
			step[axis] = delta[axis] > 0.0
					? 1
					: delta[axis] < 0.0 ? -1 : 0;
			t_delta[axis] = step[axis] == 0
					? CUDART_INF
					: fabs(1.0 / delta[axis]);
			t_max[axis] = device_initial_t(
					ray.start[axis],
					delta[axis],
					current[axis],
					step[axis]
			);
		}
		double entry_t = 0.0;
		for (std::int32_t count = 0;
				count < ray.maximum_cells;
				++count) {
			const double exit_t = fmin(
					1.0,
					fmin(t_max[0], fmin(t_max[1], t_max[2]))
			);
			const double segment_length =
					fmax(0.0, exit_t - entry_t) * total_length;
			visit_cell(
					cells,
					cell_count,
					segments,
					result,
					ray.segment_offset,
					current[0],
					current[1],
					current[2],
					segment_length
			);
			if (current[0] == target[0]
					&& current[1] == target[1]
					&& current[2] == target[2]) {
				result.flags = kReachedFlag;
				break;
			}
			const double crossing = fmin(
					t_max[0],
					fmin(t_max[1], t_max[2])
			);
			const double epsilon = fmax(
					1.0e-12,
					fabs(crossing) * 1.0e-12
			);
			for (std::int32_t axis = 0; axis < 3; ++axis) {
				if (fabs(t_max[axis] - crossing) <= epsilon) {
					current[axis] += step[axis];
					t_max[axis] += t_delta[axis];
				}
			}
			entry_t = fmin(1.0, crossing);
		}
		if ((result.flags & kReachedFlag) == 0U
				&& (result.flags & kStoppedFlag) == 0U) {
			result.flags = kTruncatedFlag;
		}
	}
	for (std::size_t band = 0; band < kBands; ++band) {
		result.gain[band] = pow(10.0, -result.loss[band] / 10.0);
	}
	results[ray_index] = result;
}

[[nodiscard]] double milliseconds(
		const Clock::duration duration
	) {
	return std::chrono::duration<double, std::milli>(duration).count();
}

[[nodiscard]] double percentile(
		std::vector<double> values,
		const double quantile
	) {
	std::sort(values.begin(), values.end());
	const std::size_t index = static_cast<std::size_t>(
			std::ceil(quantile * static_cast<double>(values.size())) - 1.0
	);
	return values.at(std::min(index, values.size() - 1U));
}

[[nodiscard]] std::string stage_json(
		const std::string_view name,
		const std::vector<double>& samples
	) {
	std::ostringstream output;
	output << std::setprecision(17)
			<< "\"" << name << "\":{"
			<< "\"p50_ms\":" << percentile(samples, 0.50)
			<< ",\"p95_ms\":" << percentile(samples, 0.95)
			<< ",\"p99_ms\":" << percentile(samples, 0.99)
			<< '}';
	return output.str();
}

void compare_results(
		const Summary& summary,
		const std::vector<DeviceRay>& rays,
		const std::vector<DeviceResult>& results,
		const std::vector<DeviceSegment>& segments
	) {
	for (std::size_t ray_index = 0;
			ray_index < summary.rays.size();
			++ray_index) {
		const ExpectedTrace expected = trace_ray(
				summary.rays[ray_index],
				summary.cells
		);
		const DeviceResult& actual = results[ray_index];
		const std::uint8_t expected_flags = static_cast<std::uint8_t>(
				(expected.reached ? kReachedFlag : 0U)
				| (expected.stopped ? kStoppedFlag : 0U)
				| (expected.truncated ? kTruncatedFlag : 0U)
		);
		if (actual.segment_count
						!= static_cast<std::int32_t>(expected.segments.size())
				|| actual.material_cells != expected.material_cells
				|| actual.flags != expected_flags
				|| (actual.has_first_material != 0U)
						!= expected.first_material.has_value()
				|| (expected.first_material.has_value()
						&& actual.first_material
								!= expected.first_material.value())) {
			throw std::runtime_error(
					"CUDA aggregate mismatch for ray "
					+ std::to_string(ray_index)
			);
		}
		for (std::size_t band = 0; band < kBands; ++band) {
			const double loss_error = std::abs(
					actual.loss[band] - expected.loss[band]
			);
			const double gain_error = std::abs(
					actual.gain[band] - expected.gain[band]
			);
			if (loss_error > 1.0e-5 || gain_error > 1.0e-5) {
				throw std::runtime_error(
						"CUDA band mismatch for ray "
						+ std::to_string(ray_index)
				);
			}
		}
		for (std::size_t segment_index = 0;
				segment_index < expected.segments.size();
				++segment_index) {
			const ExpectedSegment& expected_segment =
					expected.segments[segment_index];
			const DeviceSegment& actual_segment = segments.at(
					static_cast<std::size_t>(rays[ray_index].segment_offset)
					+ segment_index
			);
			if (actual_segment.packed != expected_segment.packed
					|| actual_segment.material_id
							!= expected_segment.material_id
					|| actual_segment.fill_fraction
							!= expected_segment.fill_fraction
					|| !close_double(
							actual_segment.length,
							expected_segment.length
					)) {
				throw std::runtime_error(
						"CUDA segment mismatch for ray "
						+ std::to_string(ray_index)
						+ " segment " + std::to_string(segment_index)
				);
			}
		}
	}
}

void run(const Arguments& arguments) {
	const auto parse_start = Clock::now();
	const Summary summary = verify(read_file(arguments.bundle));
	const auto parse_end = Clock::now();
	if (!summary.complete) {
		throw std::runtime_error("production snapshot is incomplete");
	}
	if (arguments.expected_results.has_value()) {
		static_cast<void>(verify_expected_results(
				summary,
				read_file(arguments.expected_results.value())
		));
	}

	const auto flatten_start = Clock::now();
	std::vector<DeviceCell> cells;
	cells.reserve(summary.cells.size());
	for (const Cell& cell : summary.cells) {
		cells.push_back({
				cell.packed,
				cell.material_id,
				cell.fill_fraction
		});
	}
	std::vector<DeviceRay> rays;
	rays.reserve(summary.rays.size());
	std::vector<std::int32_t> maximum_cells;
	maximum_cells.reserve(summary.rays.size());
	std::uint64_t segment_capacity = 0U;
	for (const Ray& ray : summary.rays) {
		maximum_cells.push_back(ray.maximum_cells);
		rays.push_back({
				{ray.start[0], ray.start[1], ray.start[2]},
				{ray.end[0], ray.end[1], ray.end[2]},
				ray.maximum_cells,
				segment_capacity
		});
		segment_capacity += static_cast<std::uint64_t>(ray.maximum_cells);
	}
	const std::vector<mcfpv::cuda_dda::Batch> batch_plan =
			mcfpv::cuda_dda::plan_batches(
					maximum_cells,
					mcfpv::cuda_dda::BatchLimits{
							arguments.maximum_rays_per_batch,
							arguments.maximum_segments_per_batch
					}
			);
	if (batch_plan.size() != 1U) {
		throw std::runtime_error(
				"CUDA corpus requires bounded multi-batch executor; "
				"current executor refuses unbounded allocation"
		);
	}
	if (segment_capacity > std::numeric_limits<std::size_t>::max()
			/ sizeof(DeviceSegment)) {
		throw std::runtime_error("CUDA segment buffer is too large");
	}
	const auto flatten_end = Clock::now();

	double transmission[8 * kBands]{};
	for (std::size_t material = 0; material < kMaterials.size(); ++material) {
		for (std::size_t band = 0; band < kBands; ++band) {
			transmission[material * kBands + band] =
					kMaterials[material].transmission[band];
		}
	}
	require_cuda(
			cudaMemcpyToSymbol(
					kDeviceTransmission,
					transmission,
					sizeof(transmission)
			),
			"upload material table"
	);

	DeviceCell* device_cells = nullptr;
	DeviceRay* device_rays = nullptr;
	DeviceSegment* device_segments = nullptr;
	DeviceResult* device_results = nullptr;
	require_cuda(
			cudaMalloc(
					reinterpret_cast<void**>(&device_cells),
					cells.size() * sizeof(DeviceCell)
			),
			"allocate cells"
	);
	require_cuda(
			cudaMalloc(
					reinterpret_cast<void**>(&device_rays),
					rays.size() * sizeof(DeviceRay)
			),
			"allocate rays"
	);
	require_cuda(
			cudaMalloc(
					reinterpret_cast<void**>(&device_segments),
					static_cast<std::size_t>(segment_capacity)
							* sizeof(DeviceSegment)
			),
			"allocate segments"
	);
	require_cuda(
			cudaMalloc(
					reinterpret_cast<void**>(&device_results),
					rays.size() * sizeof(DeviceResult)
			),
			"allocate results"
	);
	require_cuda(
			cudaMemcpy(
					device_cells,
					cells.data(),
					cells.size() * sizeof(DeviceCell),
					cudaMemcpyHostToDevice
			),
			"upload resident cells"
	);

	std::vector<DeviceSegment> host_segments(
			static_cast<std::size_t>(segment_capacity)
	);
	std::vector<DeviceResult> host_results(rays.size());
	cudaEvent_t h2d_end{};
	cudaEvent_t kernel_end{};
	cudaEvent_t d2h_end{};
	require_cuda(cudaEventCreate(&h2d_end), "create H2D event");
	require_cuda(cudaEventCreate(&kernel_end), "create kernel event");
	require_cuda(cudaEventCreate(&d2h_end), "create D2H event");

	const std::int32_t ray_count = static_cast<std::int32_t>(rays.size());
	const dim3 block(128U);
	const dim3 grid(static_cast<unsigned int>(
			(ray_count + static_cast<std::int32_t>(block.x) - 1)
			/ static_cast<std::int32_t>(block.x)
	));
	StageSamples samples;
	const std::int32_t total_batches =
			arguments.warmup + arguments.iterations;
	for (std::int32_t batch = 0; batch < total_batches; ++batch) {
		const auto total_start = Clock::now();
		cudaEvent_t batch_start{};
		require_cuda(cudaEventCreate(&batch_start), "create batch event");
		require_cuda(cudaEventRecord(batch_start), "record batch start");
		require_cuda(
				cudaMemcpyAsync(
						device_rays,
						rays.data(),
						rays.size() * sizeof(DeviceRay),
						cudaMemcpyHostToDevice
				),
				"upload rays"
		);
		require_cuda(cudaEventRecord(h2d_end), "record H2D end");
		trace_kernel<<<grid, block>>>(
				device_cells,
				static_cast<std::int32_t>(cells.size()),
				device_rays,
				ray_count,
				device_segments,
				device_results
		);
		require_cuda(cudaGetLastError(), "launch DDA kernel");
		require_cuda(cudaEventRecord(kernel_end), "record kernel end");
		require_cuda(
				cudaMemcpyAsync(
						host_results.data(),
						device_results,
						host_results.size() * sizeof(DeviceResult),
						cudaMemcpyDeviceToHost
				),
				"read results"
		);
		require_cuda(
				cudaMemcpyAsync(
						host_segments.data(),
						device_segments,
						host_segments.size() * sizeof(DeviceSegment),
						cudaMemcpyDeviceToHost
				),
				"read segments"
		);
		require_cuda(cudaEventRecord(d2h_end), "record D2H end");
		require_cuda(cudaEventSynchronize(d2h_end), "synchronize batch");
		const double total_ms = milliseconds(Clock::now() - total_start);

		float h2d_ms = 0.0F;
		float kernel_ms = 0.0F;
		float d2h_ms = 0.0F;
		require_cuda(
				cudaEventElapsedTime(&h2d_ms, batch_start, h2d_end),
				"measure H2D"
		);
		require_cuda(
				cudaEventElapsedTime(&kernel_ms, h2d_end, kernel_end),
				"measure kernel"
		);
		require_cuda(
				cudaEventElapsedTime(&d2h_ms, kernel_end, d2h_end),
				"measure D2H"
		);
		require_cuda(cudaEventDestroy(batch_start), "destroy batch event");
		if (batch >= arguments.warmup) {
			samples.h2d_ms.push_back(h2d_ms);
			samples.kernel_ms.push_back(kernel_ms);
			samples.d2h_ms.push_back(d2h_ms);
			samples.total_ms.push_back(total_ms);
		}
	}

	compare_results(summary, rays, host_results, host_segments);
	const cudaDeviceProp properties = [&]() {
		int device = 0;
		require_cuda(cudaGetDevice(&device), "get CUDA device");
		cudaDeviceProp value{};
		require_cuda(
				cudaGetDeviceProperties(&value, device),
				"get CUDA device properties"
		);
		return value;
	}();

	std::cout << std::setprecision(17)
			<< "{\"status\":\"valid\""
			<< ",\"backend\":\"cuda-fp64-correctness\""
			<< ",\"schema\":1"
			<< ",\"device\":\"" << properties.name << "\""
			<< ",\"compute_capability\":\"" << properties.major
			<< '.' << properties.minor << "\""
			<< ",\"snapshot_generation\":" << summary.generation
			<< ",\"snapshot_sha256\":\""
			<< hex(summary.snapshot_hash) << "\""
			<< ",\"rays\":" << summary.rays.size()
			<< ",\"cells\":" << summary.cells.size()
			<< ",\"segment_capacity\":" << segment_capacity
			<< ",\"warmup_batches\":" << arguments.warmup
			<< ",\"measured_batches\":" << arguments.iterations
			<< ",\"parse_ms\":" << milliseconds(parse_end - parse_start)
			<< ",\"host_flatten_ms\":"
			<< milliseconds(flatten_end - flatten_start)
			<< ',' << stage_json("h2d", samples.h2d_ms)
			<< ',' << stage_json("kernel", samples.kernel_ms)
			<< ',' << stage_json("d2h", samples.d2h_ms)
			<< ',' << stage_json("submit_to_result", samples.total_ms)
			<< ",\"parity\":{"
			<< "\"visited_cells\":true"
			<< ",\"segments\":true"
			<< ",\"first_material\":true"
			<< ",\"flags\":true"
			<< ",\"bands_tolerance\":1e-5}}\n";

	require_cuda(cudaEventDestroy(h2d_end), "destroy H2D event");
	require_cuda(cudaEventDestroy(kernel_end), "destroy kernel event");
	require_cuda(cudaEventDestroy(d2h_end), "destroy D2H event");
	require_cuda(cudaFree(device_cells), "free cells");
	require_cuda(cudaFree(device_rays), "free rays");
	require_cuda(cudaFree(device_segments), "free segments");
	require_cuda(cudaFree(device_results), "free results");
}

}  // namespace

int main(int argc, char** argv) {
	try {
		run(parse_arguments(argc, argv));
		return 0;
	} catch (const std::exception& error) {
		std::cerr << "{\"status\":\"invalid\",\"error\":\""
				<< error.what() << "\"}\n";
		return 1;
	}
}
