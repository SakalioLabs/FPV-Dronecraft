#include <array>
#include <chrono>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <memory>
#include <new>
#include <sstream>
#include <string>
#include <vector>

#if defined(_WIN32)
#include <windows.h>
#define MCFPV_EXPORT extern "C" __declspec(dllexport)
#define MCFPV_CALL __stdcall
#else
#include <dlfcn.h>
#define MCFPV_EXPORT extern "C" __attribute__((visibility("default")))
#define MCFPV_CALL
#endif

namespace {

using CuResult = int;
using CuDevice = int;
using CuContext = void*;
using CuModule = void*;
using CuFunction = void*;
using CuDevicePointer = std::uint64_t;
using NvrtcResult = int;
using NvrtcProgram = void*;

constexpr CuResult CUDA_SUCCESS = 0;
constexpr NvrtcResult NVRTC_SUCCESS = 0;
constexpr int COMPUTE_CAPABILITY_MAJOR = 75;
constexpr int COMPUTE_CAPABILITY_MINOR = 76;
constexpr std::uint32_t ABI_VERSION = 1U;
constexpr std::size_t RAY_BYTES = 64U;
constexpr std::size_t RESULT_BYTES = 72U;
constexpr std::size_t CELL_BYTES = 24U;

struct BridgeConfig final {
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

struct SubmitRequest final {
	const void* rays;
	void* results;
	std::uint32_t ray_count;
	std::uint32_t reserved;
	void* metrics;
};

struct SubmitMetrics final {
	double h2d_ms;
	double kernel_ms;
	double d2h_ms;
	double total_ms;
};

struct BridgeInfo final {
	std::int32_t driver_version;
	std::int32_t nvrtc_major;
	std::int32_t nvrtc_minor;
	std::int32_t compute_major;
	std::int32_t compute_minor;
	std::uint32_t cell_count;
	std::uint32_t maximum_rays;
	std::uint32_t reserved;
};

static_assert(sizeof(BridgeConfig) == 56U);
static_assert(offsetof(BridgeConfig, cells) == 24U);
static_assert(offsetof(BridgeConfig, transmission) == 40U);
static_assert(sizeof(SubmitRequest) == 32U);
static_assert(offsetof(SubmitRequest, metrics) == 24U);
static_assert(sizeof(SubmitMetrics) == 32U);
static_assert(sizeof(BridgeInfo) == 32U);

using CuInit = CuResult(MCFPV_CALL*)(unsigned int);
using CuDriverGetVersion = CuResult(MCFPV_CALL*)(int*);
using CuDeviceGet = CuResult(MCFPV_CALL*)(CuDevice*, int);
using CuDeviceGetAttribute = CuResult(MCFPV_CALL*)(int*, int, CuDevice);
using CuCtxCreate = CuResult(MCFPV_CALL*)(
	CuContext*,
	unsigned int,
	CuDevice
);
using CuCtxDestroy = CuResult(MCFPV_CALL*)(CuContext);
using CuCtxSetCurrent = CuResult(MCFPV_CALL*)(CuContext);
using CuCtxSynchronize = CuResult(MCFPV_CALL*)();
using CuModuleLoadData = CuResult(MCFPV_CALL*)(CuModule*, const void*);
using CuModuleUnload = CuResult(MCFPV_CALL*)(CuModule);
using CuModuleGetFunction = CuResult(MCFPV_CALL*)(
	CuFunction*,
	CuModule,
	const char*
);
using CuMemAlloc = CuResult(MCFPV_CALL*)(CuDevicePointer*, std::size_t);
using CuMemFree = CuResult(MCFPV_CALL*)(CuDevicePointer);
using CuMemcpyHtoD = CuResult(MCFPV_CALL*)(
	CuDevicePointer,
	const void*,
	std::size_t
);
using CuMemcpyDtoH = CuResult(MCFPV_CALL*)(
	void*,
	CuDevicePointer,
	std::size_t
);
using CuLaunchKernel = CuResult(MCFPV_CALL*)(
	CuFunction,
	unsigned int,
	unsigned int,
	unsigned int,
	unsigned int,
	unsigned int,
	unsigned int,
	unsigned int,
	void*,
	void**,
	void**
);

using NvrtcCreateProgram = NvrtcResult(MCFPV_CALL*)(
	NvrtcProgram*,
	const char*,
	const char*,
	int,
	const char* const*,
	const char* const*
);
using NvrtcCompileProgram = NvrtcResult(MCFPV_CALL*)(
	NvrtcProgram,
	int,
	const char* const*
);
using NvrtcGetProgramLogSize = NvrtcResult(MCFPV_CALL*)(
	NvrtcProgram,
	std::size_t*
);
using NvrtcGetProgramLog = NvrtcResult(MCFPV_CALL*)(
	NvrtcProgram,
	char*
);
using NvrtcGetCubinSize = NvrtcResult(MCFPV_CALL*)(
	NvrtcProgram,
	std::size_t*
);
using NvrtcGetCubin = NvrtcResult(MCFPV_CALL*)(NvrtcProgram, char*);
using NvrtcDestroyProgram = NvrtcResult(MCFPV_CALL*)(NvrtcProgram*);
using NvrtcVersion = NvrtcResult(MCFPV_CALL*)(int*, int*);

thread_local std::string last_error;

#if defined(_WIN32)
using LibraryHandle = HMODULE;

LibraryHandle load_library(const char* utf8_path) {
	if (utf8_path == nullptr || *utf8_path == '\0') {
		return nullptr;
	}
	const int length = MultiByteToWideChar(
		CP_UTF8,
		MB_ERR_INVALID_CHARS,
		utf8_path,
		-1,
		nullptr,
		0
	);
	if (length <= 0) {
		return nullptr;
	}
	std::vector<wchar_t> wide(static_cast<std::size_t>(length));
	if (MultiByteToWideChar(
			CP_UTF8,
			MB_ERR_INVALID_CHARS,
			utf8_path,
			-1,
			wide.data(),
			length
		) <= 0) {
		return nullptr;
	}
	return LoadLibraryExW(
		wide.data(),
		nullptr,
		LOAD_WITH_ALTERED_SEARCH_PATH
	);
}

void unload_library(LibraryHandle library) {
	if (library != nullptr) {
		FreeLibrary(library);
	}
}

void* find_symbol(LibraryHandle library, const char* name) {
	return reinterpret_cast<void*>(GetProcAddress(library, name));
}
#else
using LibraryHandle = void*;

LibraryHandle load_library(const char* path) {
	return path == nullptr ? nullptr : dlopen(path, RTLD_NOW | RTLD_LOCAL);
}

void unload_library(LibraryHandle library) {
	if (library != nullptr) {
		dlclose(library);
	}
}

void* find_symbol(LibraryHandle library, const char* name) {
	return dlsym(library, name);
}
#endif

template <typename Function>
bool resolve(
		LibraryHandle library,
		const char* name,
		Function& destination
	) {
	destination = reinterpret_cast<Function>(find_symbol(library, name));
	if (destination == nullptr) {
		last_error = std::string("missing native symbol ") + name;
		return false;
	}
	return true;
}

struct DriverApi final {
	LibraryHandle library{};
	CuInit init{};
	CuDriverGetVersion driver_get_version{};
	CuDeviceGet device_get{};
	CuDeviceGetAttribute device_get_attribute{};
	CuCtxCreate context_create{};
	CuCtxDestroy context_destroy{};
	CuCtxSetCurrent context_set_current{};
	CuCtxSynchronize context_synchronize{};
	CuModuleLoadData module_load_data{};
	CuModuleUnload module_unload{};
	CuModuleGetFunction module_get_function{};
	CuMemAlloc memory_allocate{};
	CuMemFree memory_free{};
	CuMemcpyHtoD memcpy_h2d{};
	CuMemcpyDtoH memcpy_d2h{};
	CuLaunchKernel launch_kernel{};

	bool load() {
#if defined(_WIN32)
		library = LoadLibraryW(L"nvcuda.dll");
#else
		library = load_library("libcuda.so.1");
#endif
		if (library == nullptr) {
			last_error = "NVIDIA Driver API library is unavailable";
			return false;
		}
		return resolve(library, "cuInit", init)
			&& resolve(library, "cuDriverGetVersion", driver_get_version)
			&& resolve(library, "cuDeviceGet", device_get)
			&& resolve(
				library,
				"cuDeviceGetAttribute",
				device_get_attribute
			)
			&& resolve(library, "cuCtxCreate_v2", context_create)
			&& resolve(library, "cuCtxDestroy_v2", context_destroy)
			&& resolve(library, "cuCtxSetCurrent", context_set_current)
			&& resolve(library, "cuCtxSynchronize", context_synchronize)
			&& resolve(library, "cuModuleLoadData", module_load_data)
			&& resolve(library, "cuModuleUnload", module_unload)
			&& resolve(
				library,
				"cuModuleGetFunction",
				module_get_function
			)
			&& resolve(library, "cuMemAlloc_v2", memory_allocate)
			&& resolve(library, "cuMemFree_v2", memory_free)
			&& resolve(library, "cuMemcpyHtoD_v2", memcpy_h2d)
			&& resolve(library, "cuMemcpyDtoH_v2", memcpy_d2h)
			&& resolve(library, "cuLaunchKernel", launch_kernel);
	}

	~DriverApi() {
		unload_library(library);
	}
};

struct NvrtcApi final {
	LibraryHandle library{};
	NvrtcCreateProgram create_program{};
	NvrtcCompileProgram compile_program{};
	NvrtcGetProgramLogSize get_log_size{};
	NvrtcGetProgramLog get_log{};
	NvrtcGetCubinSize get_cubin_size{};
	NvrtcGetCubin get_cubin{};
	NvrtcDestroyProgram destroy_program{};
	NvrtcVersion version{};

	bool load(const char* path) {
		library = load_library(path);
		if (library == nullptr) {
			last_error = "NVRTC library is unavailable";
			return false;
		}
		return resolve(library, "nvrtcCreateProgram", create_program)
			&& resolve(library, "nvrtcCompileProgram", compile_program)
			&& resolve(
				library,
				"nvrtcGetProgramLogSize",
				get_log_size
			)
			&& resolve(library, "nvrtcGetProgramLog", get_log)
			&& resolve(
				library,
				"nvrtcGetCUBINSize",
				get_cubin_size
			)
			&& resolve(library, "nvrtcGetCUBIN", get_cubin)
			&& resolve(
				library,
				"nvrtcDestroyProgram",
				destroy_program
			)
			&& resolve(library, "nvrtcVersion", version);
	}

	~NvrtcApi() {
		unload_library(library);
	}
};

bool check_cuda(CuResult result, const char* operation) {
	if (result == CUDA_SUCCESS) {
		return true;
	}
	std::ostringstream message;
	message << operation << " failed with CUresult " << result;
	last_error = message.str();
	return false;
}

bool check_nvrtc(NvrtcResult result, const char* operation) {
	if (result == NVRTC_SUCCESS) {
		return true;
	}
	std::ostringstream message;
	message << operation << " failed with nvrtcResult " << result;
	last_error = message.str();
	return false;
}

struct ProgramGuard final {
	NvrtcApi* api{};
	NvrtcProgram program{};

	~ProgramGuard() {
		if (api != nullptr && program != nullptr) {
			api->destroy_program(&program);
		}
	}
};

struct Bridge final {
	DriverApi driver;
	NvrtcApi nvrtc;
	CuContext context{};
	CuModule module{};
	CuFunction aggregate_kernel{};
	CuDevicePointer device_cells{};
	CuDevicePointer device_transmission{};
	CuDevicePointer device_rays{};
	CuDevicePointer device_results{};
	std::uint32_t cell_count{};
	std::uint32_t material_count{};
	std::uint32_t maximum_rays{};
	int driver_version{};
	int nvrtc_major{};
	int nvrtc_minor{};
	int compute_major{};
	int compute_minor{};

	~Bridge() {
		if (context != nullptr) {
			driver.context_set_current(context);
			if (device_results != 0U) {
				driver.memory_free(device_results);
			}
			if (device_rays != 0U) {
				driver.memory_free(device_rays);
			}
			if (device_transmission != 0U) {
				driver.memory_free(device_transmission);
			}
			if (device_cells != 0U) {
				driver.memory_free(device_cells);
			}
			if (module != nullptr) {
				driver.module_unload(module);
			}
			driver.context_destroy(context);
		}
	}
};

bool compile_kernel(
		Bridge& bridge,
		const char* source,
		std::vector<char>& cubin
	) {
	ProgramGuard program{&bridge.nvrtc, nullptr};
	if (!check_nvrtc(
			bridge.nvrtc.create_program(
				&program.program,
				source,
				"dda_nvrtc_kernel.cu",
				0,
				nullptr,
				nullptr
			),
			"nvrtcCreateProgram"
		)) {
		return false;
	}
	const std::string architecture = "--gpu-architecture=sm_"
		+ std::to_string(bridge.compute_major)
		+ std::to_string(bridge.compute_minor);
	const std::array<const char*, 3> options = {
		architecture.c_str(),
		"--std=c++17",
		"--fmad=false"
	};
	const NvrtcResult compile_result = bridge.nvrtc.compile_program(
		program.program,
		static_cast<int>(options.size()),
		options.data()
	);
	if (compile_result != NVRTC_SUCCESS) {
		std::size_t log_size = 0U;
		std::string log;
		if (bridge.nvrtc.get_log_size(
				program.program,
				&log_size
			) == NVRTC_SUCCESS && log_size > 0U) {
			log.resize(log_size);
			bridge.nvrtc.get_log(program.program, log.data());
		}
		std::ostringstream message;
		message << "nvrtcCompileProgram failed with nvrtcResult "
			<< compile_result << ": " << log;
		last_error = message.str();
		return false;
	}
	std::size_t cubin_size = 0U;
	if (!check_nvrtc(
			bridge.nvrtc.get_cubin_size(program.program, &cubin_size),
			"nvrtcGetCUBINSize"
		)) {
		return false;
	}
	cubin.resize(cubin_size);
	return check_nvrtc(
		bridge.nvrtc.get_cubin(program.program, cubin.data()),
		"nvrtcGetCUBIN"
	);
}

bool initialize_bridge(Bridge& bridge, const BridgeConfig& config) {
	if (!bridge.driver.load() || !bridge.nvrtc.load(config.nvrtc_library_path)) {
		return false;
	}
	if (!check_cuda(bridge.driver.init(0U), "cuInit")) {
		return false;
	}
	CuDevice device = 0;
	if (!check_cuda(
			bridge.driver.device_get(
				&device,
				static_cast<int>(config.device_ordinal)
			),
			"cuDeviceGet"
		)
			|| !check_cuda(
				bridge.driver.device_get_attribute(
					&bridge.compute_major,
					COMPUTE_CAPABILITY_MAJOR,
					device
				),
				"cuDeviceGetAttribute(major)"
			)
			|| !check_cuda(
				bridge.driver.device_get_attribute(
					&bridge.compute_minor,
					COMPUTE_CAPABILITY_MINOR,
					device
				),
				"cuDeviceGetAttribute(minor)"
			)
			|| !check_cuda(
				bridge.driver.driver_get_version(&bridge.driver_version),
				"cuDriverGetVersion"
			)
			|| !check_nvrtc(
				bridge.nvrtc.version(
					&bridge.nvrtc_major,
					&bridge.nvrtc_minor
				),
				"nvrtcVersion"
			)
			|| !check_cuda(
				bridge.driver.context_create(
					&bridge.context,
					0U,
					device
				),
				"cuCtxCreate"
			)) {
		return false;
	}
	std::vector<char> cubin;
	if (!compile_kernel(bridge, config.kernel_source, cubin)
			|| !check_cuda(
				bridge.driver.module_load_data(
					&bridge.module,
					cubin.data()
				),
				"cuModuleLoadData"
			)
			|| !check_cuda(
				bridge.driver.module_get_function(
					&bridge.aggregate_kernel,
					bridge.module,
					"trace_aggregate_kernel"
				),
				"cuModuleGetFunction"
			)) {
		return false;
	}
	bridge.cell_count = config.cell_count;
	bridge.material_count = config.material_count;
	bridge.maximum_rays = config.maximum_rays;
	const std::size_t cell_bytes =
		static_cast<std::size_t>(config.cell_count) * CELL_BYTES;
	const std::size_t transmission_bytes =
		static_cast<std::size_t>(config.material_count) * 3U * sizeof(double);
	const std::size_t ray_bytes =
		static_cast<std::size_t>(config.maximum_rays) * RAY_BYTES;
	const std::size_t result_bytes =
		static_cast<std::size_t>(config.maximum_rays) * RESULT_BYTES;
	if (!check_cuda(
			bridge.driver.memory_allocate(
				&bridge.device_cells,
				cell_bytes
			),
			"cuMemAlloc(cells)"
		)
			|| !check_cuda(
				bridge.driver.memory_allocate(
					&bridge.device_transmission,
					transmission_bytes
				),
				"cuMemAlloc(transmission)"
			)
			|| !check_cuda(
				bridge.driver.memory_allocate(
					&bridge.device_rays,
					ray_bytes
				),
				"cuMemAlloc(rays)"
			)
			|| !check_cuda(
				bridge.driver.memory_allocate(
					&bridge.device_results,
					result_bytes
				),
				"cuMemAlloc(results)"
			)
			|| !check_cuda(
				bridge.driver.memcpy_h2d(
					bridge.device_cells,
					config.cells,
					cell_bytes
				),
				"cuMemcpyHtoD(cells)"
			)
			|| !check_cuda(
				bridge.driver.memcpy_h2d(
					bridge.device_transmission,
					config.transmission,
					transmission_bytes
				),
				"cuMemcpyHtoD(transmission)"
			)) {
		return false;
	}
	return true;
}

double elapsed_ms(
		const std::chrono::steady_clock::time_point start,
		const std::chrono::steady_clock::time_point end
	) {
	return std::chrono::duration<double, std::milli>(end - start).count();
}

int submit(Bridge& bridge, const SubmitRequest& request) {
	if (request.rays == nullptr
			|| request.results == nullptr
			|| request.metrics == nullptr
			|| request.ray_count == 0U
			|| request.ray_count > bridge.maximum_rays) {
		last_error = "invalid CUDA bridge submit request";
		return 2;
	}
	if (!check_cuda(
			bridge.driver.context_set_current(bridge.context),
			"cuCtxSetCurrent"
		)) {
		return 3;
	}
	auto* metrics = static_cast<SubmitMetrics*>(request.metrics);
	const std::size_t ray_bytes =
		static_cast<std::size_t>(request.ray_count) * RAY_BYTES;
	const std::size_t result_bytes =
		static_cast<std::size_t>(request.ray_count) * RESULT_BYTES;
	const auto total_start = std::chrono::steady_clock::now();
	auto stage_start = total_start;
	if (!check_cuda(
			bridge.driver.memcpy_h2d(
				bridge.device_rays,
				request.rays,
				ray_bytes
			),
			"cuMemcpyHtoD(rays)"
		)) {
		return 4;
	}
	const auto h2d_end = std::chrono::steady_clock::now();
	metrics->h2d_ms = elapsed_ms(stage_start, h2d_end);

	CuDevicePointer null_segments = 0U;
	int cell_count = static_cast<int>(bridge.cell_count);
	int ray_count = static_cast<int>(request.ray_count);
	void* kernel_arguments[] = {
		&bridge.device_cells,
		&cell_count,
		&bridge.device_rays,
		&ray_count,
		&bridge.device_transmission,
		&null_segments,
		&bridge.device_results
	};
	stage_start = h2d_end;
	if (!check_cuda(
			bridge.driver.launch_kernel(
				bridge.aggregate_kernel,
				(request.ray_count + 127U) / 128U,
				1U,
				1U,
				128U,
				1U,
				1U,
				0U,
				nullptr,
				kernel_arguments,
				nullptr
			),
			"cuLaunchKernel"
		)
			|| !check_cuda(
				bridge.driver.context_synchronize(),
				"cuCtxSynchronize"
			)) {
		return 5;
	}
	const auto kernel_end = std::chrono::steady_clock::now();
	metrics->kernel_ms = elapsed_ms(stage_start, kernel_end);

	stage_start = kernel_end;
	if (!check_cuda(
			bridge.driver.memcpy_d2h(
				request.results,
				bridge.device_results,
				result_bytes
			),
			"cuMemcpyDtoH(results)"
		)) {
		return 6;
	}
	const auto d2h_end = std::chrono::steady_clock::now();
	metrics->d2h_ms = elapsed_ms(stage_start, d2h_end);
	metrics->total_ms = elapsed_ms(total_start, d2h_end);
	return 0;
}

}  // namespace

MCFPV_EXPORT std::uint32_t mcfpv_cuda_bridge_abi_version() noexcept {
	return ABI_VERSION;
}

MCFPV_EXPORT const char* mcfpv_cuda_bridge_last_error() noexcept {
	return last_error.c_str();
}

MCFPV_EXPORT void* mcfpv_cuda_bridge_create(
		const BridgeConfig* config
	) noexcept {
	last_error.clear();
	if (config == nullptr
			|| config->abi_version != ABI_VERSION
			|| config->kernel_source == nullptr
			|| config->cells == nullptr
			|| config->cell_count == 0U
			|| config->transmission == nullptr
			|| config->material_count == 0U
			|| config->maximum_rays == 0U) {
		last_error = "invalid CUDA bridge configuration";
		return nullptr;
	}
	try {
		auto bridge = std::make_unique<Bridge>();
		if (!initialize_bridge(*bridge, *config)) {
			return nullptr;
		}
		return bridge.release();
	} catch (const std::exception& error) {
		last_error = std::string("CUDA bridge create exception: ")
			+ error.what();
		return nullptr;
	} catch (...) {
		last_error = "CUDA bridge create unknown exception";
		return nullptr;
	}
}

MCFPV_EXPORT int mcfpv_cuda_bridge_submit(
		void* handle,
		const SubmitRequest* request
	) noexcept {
	last_error.clear();
	if (handle == nullptr || request == nullptr) {
		last_error = "null CUDA bridge submit argument";
		return 1;
	}
	try {
		return submit(*static_cast<Bridge*>(handle), *request);
	} catch (const std::exception& error) {
		last_error = std::string("CUDA bridge submit exception: ")
			+ error.what();
		return 7;
	} catch (...) {
		last_error = "CUDA bridge submit unknown exception";
		return 7;
	}
}

MCFPV_EXPORT int mcfpv_cuda_bridge_info(
		void* handle,
		BridgeInfo* info
	) noexcept {
	last_error.clear();
	if (handle == nullptr || info == nullptr) {
		last_error = "null CUDA bridge info argument";
		return 1;
	}
	const auto& bridge = *static_cast<Bridge*>(handle);
	*info = {
		bridge.driver_version,
		bridge.nvrtc_major,
		bridge.nvrtc_minor,
		bridge.compute_major,
		bridge.compute_minor,
		bridge.cell_count,
		bridge.maximum_rays,
		0U
	};
	return 0;
}

MCFPV_EXPORT int mcfpv_cuda_bridge_destroy(void* handle) noexcept {
	last_error.clear();
	try {
		delete static_cast<Bridge*>(handle);
		return 0;
	} catch (...) {
		last_error = "CUDA bridge destroy exception";
		return 1;
	}
}
