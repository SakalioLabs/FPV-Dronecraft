#include <algorithm>
#include <cmath>
#include <cstddef>
#include <cstdint>

#if defined(_WIN32)
#define MCFPV_EXPORT extern "C" __declspec(dllexport)
#else
#define MCFPV_EXPORT extern "C" __attribute__((visibility("default")))
#endif

namespace {

struct Ray final {
	double start_x;
	double start_y;
	double start_z;
	double end_x;
	double end_y;
	double end_z;
	std::uint64_t maximum_cells;
	std::uint64_t segment_offset;
};

struct Result final {
	std::uint32_t count;
	std::uint32_t flags;
	std::uint64_t first_packed;
	std::uint32_t first_material_id;
	std::uint32_t padding;
	double loss[3];
	double gain[3];
};

static_assert(sizeof(Ray) == 64);
static_assert(offsetof(Ray, maximum_cells) == 48);
static_assert(offsetof(Ray, segment_offset) == 56);
static_assert(sizeof(Result) == 72);
static_assert(offsetof(Result, loss) == 24);
static_assert(offsetof(Result, gain) == 48);

std::uint64_t mix(std::uint64_t value) noexcept {
	value ^= value >> 30U;
	value *= UINT64_C(0xbf58476d1ce4e5b9);
	value ^= value >> 27U;
	value *= UINT64_C(0x94d049bb133111eb);
	return value ^ (value >> 31U);
}

}  // namespace

MCFPV_EXPORT std::uint32_t mcfpv_dda_boundary_abi_version() noexcept {
	return 1U;
}

MCFPV_EXPORT int mcfpv_dda_boundary_aggregate(
	const Ray* rays,
	Result* results,
	std::uint32_t ray_count
) noexcept {
	if (rays == nullptr || results == nullptr || ray_count == 0U) {
		return 1;
	}
	for (std::uint32_t index = 0; index < ray_count; ++index) {
		const auto& ray = rays[index];
		auto& result = results[index];
		const double dx = ray.end_x - ray.start_x;
		const double dy = ray.end_y - ray.start_y;
		const double dz = ray.end_z - ray.start_z;
		const double distance = std::sqrt(dx * dx + dy * dy + dz * dz);
		const auto estimated = static_cast<std::uint64_t>(
			std::ceil(std::abs(dx) + std::abs(dy) + std::abs(dz))
		);
		result.count = static_cast<std::uint32_t>(
			std::min(ray.maximum_cells, std::max(UINT64_C(1), estimated))
		);
		result.flags = distance == 0.0 ? 1U : 0U;
		result.first_packed = mix(
			ray.segment_offset
			^ static_cast<std::uint64_t>(index)
			^ ray.maximum_cells
		);
		result.first_material_id = static_cast<std::uint32_t>(
			result.first_packed & UINT64_C(0x7fffffff)
		);
		result.padding = 0U;
		for (std::size_t band = 0; band < 3; ++band) {
			const double scale = 1.0 + static_cast<double>(band);
			result.loss[band] = distance * scale * 0.001;
			result.gain[band] = std::exp(-result.loss[band]);
		}
	}
	return 0;
}
