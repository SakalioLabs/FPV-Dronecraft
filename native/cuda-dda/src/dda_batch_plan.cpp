#define MCFPV_DDA_BUNDLE_CORE_ONLY
#include "../../dda-production-bundle/src/dda_bundle_verify.cpp"

#include "bounded_batch_plan.hpp"

#include <algorithm>
#include <cstdint>
#include <cstdlib>
#include <iostream>
#include <string_view>

namespace {

using mcfpv::cuda_dda::Batch;
using mcfpv::cuda_dda::BatchLimits;
using mcfpv::cuda_dda::checked_bytes;
using mcfpv::cuda_dda::plan_batches;

constexpr std::uint64_t kDefaultMaximumRays = 8192U;
constexpr std::uint64_t kDefaultMaximumSegments = 1048576U;
constexpr std::uint64_t kDeviceSegmentBytes = 32U;

struct Arguments {
	std::filesystem::path bundle;
	std::uint64_t maximum_rays = kDefaultMaximumRays;
	std::uint64_t maximum_segments = kDefaultMaximumSegments;
	bool self_test = false;
};

[[nodiscard]] std::uint64_t positive_integer(
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
		if (argument == "--self-test") {
			result.self_test = true;
		} else if (argument == "--maximum-rays-per-batch") {
			if (++index >= argc) {
				throw std::runtime_error(
						"--maximum-rays-per-batch requires one integer"
				);
			}
			result.maximum_rays = positive_integer(
					argv[index],
					"--maximum-rays-per-batch"
			);
		} else if (argument == "--maximum-segments-per-batch") {
			if (++index >= argc) {
				throw std::runtime_error(
						"--maximum-segments-per-batch requires one integer"
				);
			}
			result.maximum_segments = positive_integer(
					argv[index],
					"--maximum-segments-per-batch"
			);
		} else if (result.bundle.empty()) {
			result.bundle = argv[index];
		} else {
			throw std::runtime_error("unexpected command-line argument");
		}
	}
	if (!result.self_test && result.bundle.empty()) {
		throw std::runtime_error(
				"usage: mcfpv_dda_batch_plan "
				"[--maximum-rays-per-batch N] "
				"[--maximum-segments-per-batch N] <bundle>"
		);
	}
	return result;
}

void require(
		const bool condition,
		const std::string_view message
	) {
	if (!condition) {
		throw std::runtime_error(std::string(message));
	}
}

template <typename Callable>
void require_rejection(
		Callable&& callable,
		const std::string_view message
	) {
	try {
		callable();
	} catch (const std::runtime_error&) {
		return;
	}
	throw std::runtime_error(std::string(message));
}

void batch_plan_self_test() {
	const std::vector<Batch> segment_limited = plan_batches(
			{4, 4, 3, 2},
			BatchLimits{8U, 10U}
	);
	require(segment_limited.size() == 2U, "segment split batch count");
	require(
			segment_limited[0].first_ray == 0U
					&& segment_limited[0].ray_count == 2U
					&& segment_limited[0].segment_count == 8U,
			"segment split first batch"
	);
	require(
			segment_limited[1].first_ray == 2U
					&& segment_limited[1].ray_count == 2U
					&& segment_limited[1].segment_count == 5U,
			"segment split second batch"
	);
	const std::vector<Batch> ray_limited = plan_batches(
			{1, 1, 1, 1, 1},
			BatchLimits{2U, 10U}
	);
	require(ray_limited.size() == 3U, "ray split batch count");
	require(ray_limited[2].first_ray == 4U, "ray split final offset");
	require(
			plan_batches({}, BatchLimits{1U, 1U}).empty(),
			"empty corpus plan"
	);
	require_rejection(
			[]() {
				static_cast<void>(plan_batches(
						{11},
						BatchLimits{1U, 10U}
				));
			},
			"oversize ray must reject"
	);
	require_rejection(
			[]() {
				static_cast<void>(plan_batches(
						{1},
						BatchLimits{0U, 1U}
				));
			},
			"zero ray limit must reject"
	);
	require_rejection(
			[]() {
				static_cast<void>(checked_bytes(
						std::numeric_limits<std::uint64_t>::max(),
						2U
				));
			},
			"byte overflow must reject"
	);
	std::cout
			<< "{\"status\":\"valid\",\"schema\":1,"
			<< "\"cases\":7,\"cuda_compiled\":false,"
			<< "\"cuda_executed\":false}\n";
}

void run(const Arguments& arguments) {
	const Summary summary = verify(read_file(arguments.bundle));
	if (!summary.complete) {
		throw std::runtime_error("production snapshot is incomplete");
	}
	std::vector<std::int32_t> maximum_cells;
	maximum_cells.reserve(summary.rays.size());
	std::uint64_t total_segments = 0U;
	for (const Ray& ray : summary.rays) {
		const std::uint64_t segments = static_cast<std::uint64_t>(
				ray.maximum_cells
		);
		if (total_segments
				> std::numeric_limits<std::uint64_t>::max() - segments) {
			throw std::runtime_error("total segment count overflow");
		}
		total_segments += segments;
		maximum_cells.push_back(ray.maximum_cells);
	}
	const BatchLimits limits{
			arguments.maximum_rays,
			arguments.maximum_segments
	};
	const std::vector<Batch> batches = plan_batches(maximum_cells, limits);
	std::uint64_t peak_segments = 0U;
	std::uint64_t peak_rays = 0U;
	for (const Batch& batch : batches) {
		peak_segments = std::max(peak_segments, batch.segment_count);
		peak_rays = std::max(peak_rays, batch.ray_count);
	}
	std::cout
			<< "{\"status\":\"valid\",\"schema\":1"
			<< ",\"snapshot_generation\":" << summary.generation
			<< ",\"rays\":" << summary.rays.size()
			<< ",\"total_segment_capacity\":" << total_segments
			<< ",\"batch_count\":" << batches.size()
			<< ",\"maximum_rays_per_batch\":" << limits.maximum_rays
			<< ",\"maximum_segments_per_batch\":"
			<< limits.maximum_segments
			<< ",\"peak_rays\":" << peak_rays
			<< ",\"peak_segments\":" << peak_segments
			<< ",\"peak_segment_bytes\":"
			<< checked_bytes(peak_segments, kDeviceSegmentBytes)
			<< ",\"device_segment_size_assumption\":"
			<< kDeviceSegmentBytes
			<< ",\"cuda_compiled\":false"
			<< ",\"cuda_executed\":false"
			<< ",\"claim_boundary\":\"host-only deterministic plan\"}\n";
}

}  // namespace

int main(int argc, char** argv) {
	try {
		const Arguments arguments = parse_arguments(argc, argv);
		if (arguments.self_test) {
			batch_plan_self_test();
		} else {
			run(arguments);
		}
		return 0;
	} catch (const std::exception& error) {
		std::cerr << "{\"status\":\"invalid\",\"error\":\""
				<< error.what() << "\"}\n";
		return 1;
	}
}
