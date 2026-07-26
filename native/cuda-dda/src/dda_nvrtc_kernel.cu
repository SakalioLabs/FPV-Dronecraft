// Device-only CUDA DDA kernel compiled by docs/scripts/run_cuda_dda_nvrtc.py.
// Keep the traversal semantics aligned with dda_cuda.cu and the Java oracle.

namespace {

constexpr unsigned char REACHED_FLAG = 1U;
constexpr unsigned char STOPPED_FLAG = 2U;
constexpr unsigned char TRUNCATED_FLAG = 4U;
constexpr int BAND_COUNT = 3;

struct DeviceCell {
	unsigned long long packed;
	int material_id;
	double fill_fraction;
};

struct DeviceRay {
	double start[3];
	double end[3];
	int maximum_cells;
	unsigned long long segment_offset;
};

struct DeviceSegment {
	unsigned long long packed;
	double length;
	int material_id;
	double fill_fraction;
};

struct DeviceResult {
	int segment_count;
	int material_cells;
	unsigned char flags;
	unsigned char has_first_material;
	unsigned short reserved;
	unsigned long long first_material;
	double loss[BAND_COUNT];
	double gain[BAND_COUNT];
};

__device__ unsigned long long pack_cell(
		const int x,
		const int y,
		const int z
	) {
	return (static_cast<unsigned long long>(x) & 0x3ffffffULL) << 38U
			| (static_cast<unsigned long long>(z) & 0x3ffffffULL) << 12U
			| (static_cast<unsigned long long>(y) & 0xfffULL);
}

__device__ DeviceCell sample_cell(
		const DeviceCell* cells,
		const int cell_count,
		const unsigned long long packed
	) {
	int low = 0;
	int high = cell_count;
	while (low < high) {
		const int middle = low + (high - low) / 2;
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

__device__ double initial_t(
		const double start,
		const double delta,
		const int cell,
		const int step
	) {
	if (step == 0) {
		return __longlong_as_double(0x7ff0000000000000ULL);
	}
	const double boundary = step > 0
			? static_cast<double>(cell) + 1.0
			: static_cast<double>(cell);
	return (boundary - start) / delta;
}

__device__ void visit_cell(
		const DeviceCell* cells,
		const int cell_count,
		const double* transmission,
		DeviceSegment* segments,
		DeviceResult& result,
		const unsigned long long segment_offset,
		const int x,
		const int y,
		const int z,
		const double length
	) {
	const unsigned long long packed = pack_cell(x, y, z);
	const DeviceCell cell = sample_cell(cells, cell_count, packed);
	const double effective_length = length * cell.fill_fraction;
	for (int band = 0; band < BAND_COUNT; ++band) {
		result.loss[band] += transmission[
				cell.material_id * BAND_COUNT + band
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
			+ static_cast<unsigned long long>(result.segment_count)] = {
				packed,
				length,
				cell.material_id,
				cell.fill_fraction
			};
	++result.segment_count;
}

}  // namespace

extern "C" __global__ void trace_kernel(
		const DeviceCell* cells,
		const int cell_count,
		const DeviceRay* rays,
		const int ray_count,
		const double* transmission,
		DeviceSegment* segments,
		DeviceResult* results
	) {
	const int ray_index = static_cast<int>(
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
				transmission,
				segments,
				result,
				ray.segment_offset,
				static_cast<int>(floor(ray.start[0])),
				static_cast<int>(floor(ray.start[1])),
				static_cast<int>(floor(ray.start[2])),
				0.0
		);
		result.flags = REACHED_FLAG;
	} else {
		int current[3] = {
				static_cast<int>(floor(ray.start[0])),
				static_cast<int>(floor(ray.start[1])),
				static_cast<int>(floor(ray.start[2]))
		};
		const int target[3] = {
				static_cast<int>(floor(ray.end[0])),
				static_cast<int>(floor(ray.end[1])),
				static_cast<int>(floor(ray.end[2]))
		};
		int step[3];
		double t_delta[3];
		double t_max[3];
		const double infinity = __longlong_as_double(
				0x7ff0000000000000ULL
		);
		for (int axis = 0; axis < 3; ++axis) {
			step[axis] = delta[axis] > 0.0
					? 1
					: delta[axis] < 0.0 ? -1 : 0;
			t_delta[axis] = step[axis] == 0
					? infinity
					: fabs(1.0 / delta[axis]);
			t_max[axis] = initial_t(
					ray.start[axis],
					delta[axis],
					current[axis],
					step[axis]
			);
		}
		double entry_t = 0.0;
		for (int count = 0; count < ray.maximum_cells; ++count) {
			const double exit_t = fmin(
					1.0,
					fmin(t_max[0], fmin(t_max[1], t_max[2]))
			);
			const double segment_length =
					fmax(0.0, exit_t - entry_t) * total_length;
			visit_cell(
					cells,
					cell_count,
					transmission,
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
				result.flags = REACHED_FLAG;
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
			for (int axis = 0; axis < 3; ++axis) {
				if (fabs(t_max[axis] - crossing) <= epsilon) {
					current[axis] += step[axis];
					t_max[axis] += t_delta[axis];
				}
			}
			entry_t = fmin(1.0, crossing);
		}
		if ((result.flags & REACHED_FLAG) == 0U
				&& (result.flags & STOPPED_FLAG) == 0U) {
			result.flags = TRUNCATED_FLAG;
		}
	}
	for (int band = 0; band < BAND_COUNT; ++band) {
		result.gain[band] = pow(10.0, -result.loss[band] / 10.0);
	}
	results[ray_index] = result;
}
