#pragma once

#include <cstddef>
#include <cstdint>
#include <limits>
#include <stdexcept>
#include <vector>

namespace mcfpv::cuda_dda {

struct BatchLimits {
	std::uint64_t maximum_rays;
	std::uint64_t maximum_segments;
};

struct Batch {
	std::uint64_t first_ray;
	std::uint64_t ray_count;
	std::uint64_t segment_count;
};

[[nodiscard]] inline std::vector<Batch> plan_batches(
		const std::vector<std::int32_t>& maximum_cells,
		const BatchLimits limits
	) {
	if (limits.maximum_rays == 0U || limits.maximum_segments == 0U) {
		throw std::runtime_error("CUDA batch limits must be positive");
	}

	std::vector<Batch> batches;
	Batch current{};
	for (std::size_t index = 0; index < maximum_cells.size(); ++index) {
		const std::int32_t signed_segments = maximum_cells[index];
		if (signed_segments < 1) {
			throw std::runtime_error("ray maximum_cells must be positive");
		}
		const std::uint64_t segments = static_cast<std::uint64_t>(
				signed_segments
		);
		if (segments > limits.maximum_segments) {
			throw std::runtime_error(
					"single ray exceeds CUDA batch segment limit"
			);
		}
		const bool ray_limit_reached =
				current.ray_count == limits.maximum_rays;
		const bool segment_limit_reached =
				current.segment_count
						> limits.maximum_segments - segments;
		if (current.ray_count != 0U
				&& (ray_limit_reached || segment_limit_reached)) {
			batches.push_back(current);
			current = Batch{
					static_cast<std::uint64_t>(index),
					0U,
					0U
			};
		}
		if (current.ray_count == 0U) {
			current.first_ray = static_cast<std::uint64_t>(index);
		}
		++current.ray_count;
		current.segment_count += segments;
	}
	if (current.ray_count != 0U) {
		batches.push_back(current);
	}
	return batches;
}

[[nodiscard]] inline std::uint64_t checked_bytes(
		const std::uint64_t count,
		const std::uint64_t element_size
	) {
	if (element_size != 0U
			&& count > std::numeric_limits<std::uint64_t>::max()
					/ element_size) {
		throw std::runtime_error("CUDA batch byte count overflow");
	}
	return count * element_size;
}

}  // namespace mcfpv::cuda_dda
