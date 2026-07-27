#pragma once

#include <cstdint>

#if defined(_WIN32)
#if defined(MCFPV_CUDA_BRIDGE_BUILD)
#define MCFPV_CUDA_API extern "C" __declspec(dllexport)
#else
#define MCFPV_CUDA_API extern "C" __declspec(dllimport)
#endif
#else
#define MCFPV_CUDA_API extern "C" __attribute__((visibility("default")))
#endif

struct McfpvCudaBridgeConfig final {
	std::uint32_t abi_version;
	std::uint32_t device_ordinal;
	const char* nvrtc_library_path;
	const char* kernel_source;
	const void* cells;
	std::uint32_t cell_count;
	std::uint32_t material_count;
	const double* transmission;
	std::uint32_t maximum_rays;
	std::uint32_t reserved;
};

struct McfpvCudaSubmitRequest final {
	const void* rays;
	void* results;
	std::uint32_t ray_count;
	std::uint32_t reserved;
	void* metrics;
};

struct McfpvCudaSubmitMetrics final {
	double h2d_ms;
	double kernel_ms;
	double d2h_ms;
	double total_ms;
};

struct McfpvCudaBridgeInfo final {
	std::int32_t driver_version;
	std::int32_t nvrtc_major;
	std::int32_t nvrtc_minor;
	std::int32_t compute_major;
	std::int32_t compute_minor;
	std::uint32_t cell_count;
	std::uint32_t maximum_rays;
	std::uint32_t reserved;
};

MCFPV_CUDA_API std::uint32_t mcfpv_cuda_bridge_abi_version() noexcept;
MCFPV_CUDA_API const char* mcfpv_cuda_bridge_last_error() noexcept;
MCFPV_CUDA_API void* mcfpv_cuda_bridge_create(
	const McfpvCudaBridgeConfig* config
) noexcept;
MCFPV_CUDA_API int mcfpv_cuda_bridge_submit(
	void* handle,
	const McfpvCudaSubmitRequest* request
) noexcept;
MCFPV_CUDA_API int mcfpv_cuda_bridge_info(
	void* handle,
	McfpvCudaBridgeInfo* info
) noexcept;
MCFPV_CUDA_API int mcfpv_cuda_bridge_destroy(void* handle) noexcept;
