#define MCFPV_DDA_BUNDLE_CORE_ONLY
#include "../../dda-production-bundle/src/dda_bundle_verify.cpp"

#include <algorithm>
#include <chrono>
#include <cstdint>
#include <cstdlib>
#include <iomanip>
#include <iostream>
#include <optional>
#include <stdexcept>
#include <string_view>
#include <vector>

namespace {

using Clock = std::chrono::steady_clock;

struct Arguments {
	std::filesystem::path bundle;
	std::optional<std::filesystem::path> expected_results;
	std::int32_t warmup = 20;
	std::int32_t iterations = 200;
	std::optional<std::uint64_t> ray_limit;
	bool self_test = false;
};

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
		if (argument == "--self-test") {
			result.self_test = true;
		} else if (argument == "--warmup") {
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
		} else if (argument == "--ray-limit") {
			if (++index >= argc || result.ray_limit.has_value()) {
				throw std::runtime_error(
						"--ray-limit requires one integer"
				);
			}
			result.ray_limit = positive_uint64(
					argv[index],
					"--ray-limit"
			);
		} else if (argument == "--expected-results") {
			if (++index >= argc || result.expected_results.has_value()) {
				throw std::runtime_error(
						"--expected-results requires one path"
				);
			}
			result.expected_results = argv[index];
		} else if (result.bundle.empty()) {
			result.bundle = argv[index];
		} else {
			throw std::runtime_error("unexpected command-line argument");
		}
	}
	if (!result.self_test && result.bundle.empty()) {
		throw std::runtime_error(
				"usage: mcfpv_dda_cpu_reference "
				"[--warmup N] [--iterations N] "
				"[--ray-limit N] "
				"[--expected-results <sidecar>] <bundle>"
		);
	}
	return result;
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

[[nodiscard]] double milliseconds(
		const Clock::duration duration
	) {
	return std::chrono::duration<double, std::milli>(duration).count();
}

[[nodiscard]] double trace_batch(
		const Summary& summary,
		const std::size_t ray_count
	) {
	double checksum = 0.0;
	for (std::size_t index = 0; index < ray_count; ++index) {
		const Ray& ray = summary.rays[index];
		const ExpectedTrace trace = trace_ray(ray, summary.cells);
		checksum += trace.gain[0] + trace.gain[1] + trace.gain[2];
		checksum += static_cast<double>(trace.segments.size());
		checksum += static_cast<double>(trace.material_cells);
	}
	return checksum;
}

void run(const Arguments& arguments) {
	const auto parse_start = Clock::now();
	const Summary summary = verify(read_file(arguments.bundle));
	const auto parse_end = Clock::now();
	if (!summary.complete) {
		throw std::runtime_error("production snapshot is incomplete");
	}
	const std::size_t ray_count = arguments.ray_limit.has_value()
			? static_cast<std::size_t>(arguments.ray_limit.value())
			: summary.rays.size();
	if (ray_count > summary.rays.size()) {
		throw std::runtime_error(
				"--ray-limit exceeds production bundle ray count"
		);
	}

	double expected_ms = 0.0;
	if (arguments.expected_results.has_value()) {
		const auto expected_start = Clock::now();
		static_cast<void>(verify_expected_results(
				summary,
				read_file(arguments.expected_results.value())
		));
		expected_ms = milliseconds(Clock::now() - expected_start);
	}

	double checksum = 0.0;
	for (std::int32_t index = 0; index < arguments.warmup; ++index) {
		checksum += trace_batch(summary, ray_count);
	}

	std::vector<double> samples;
	samples.reserve(static_cast<std::size_t>(arguments.iterations));
	for (std::int32_t index = 0;
			index < arguments.iterations;
			++index) {
		const auto start = Clock::now();
		checksum += trace_batch(summary, ray_count);
		samples.push_back(milliseconds(Clock::now() - start));
	}

	const double p50 = percentile(samples, 0.50);
	const double p95 = percentile(samples, 0.95);
	const double p99 = percentile(samples, 0.99);
	std::cout << std::setprecision(17)
			<< "{\"status\":\"valid\""
			<< ",\"backend\":\"cpu-reference\""
			<< ",\"schema\":1"
			<< ",\"snapshot_generation\":" << summary.generation
			<< ",\"snapshot_sha256\":\""
			<< hex(summary.snapshot_hash) << "\""
			<< ",\"bundle_rays\":" << summary.rays.size()
			<< ",\"rays\":" << ray_count
			<< ",\"cells\":" << summary.cells.size()
			<< ",\"warmup_batches\":" << arguments.warmup
			<< ",\"measured_batches\":" << arguments.iterations
			<< ",\"parse_ms\":" << milliseconds(parse_end - parse_start)
			<< ",\"expected_verify_ms\":" << expected_ms
			<< ",\"batch_p50_ms\":" << p50
			<< ",\"batch_p95_ms\":" << p95
			<< ",\"batch_p99_ms\":" << p99
			<< ",\"p95_ns_per_ray\":"
			<< p95 * 1.0e6 / static_cast<double>(ray_count)
			<< ",\"checksum\":" << checksum << "}\n";
}

}  // namespace

int main(int argc, char** argv) {
	try {
		const Arguments arguments = parse_arguments(argc, argv);
		if (arguments.self_test) {
			self_test();
			return 0;
		}
		run(arguments);
		return 0;
	} catch (const std::exception& error) {
		std::cerr << "{\"status\":\"invalid\",\"error\":\""
				<< error.what() << "\"}\n";
		return 1;
	}
}
