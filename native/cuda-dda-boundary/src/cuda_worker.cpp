#include "mcfpv_cuda_bridge.h"
#include "mcfpv_cuda_worker_protocol.h"

#include <algorithm>
#include <chrono>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <iterator>
#include <limits>
#include <stdexcept>
#include <string>
#include <thread>
#include <utility>
#include <vector>

#if defined(_WIN32)
#include <fcntl.h>
#include <io.h>
#endif

namespace {

constexpr std::size_t CELL_BYTES = 24U;
constexpr std::size_t RAY_BYTES = 64U;
constexpr std::size_t RESULT_BYTES = 72U;
constexpr std::uint64_t FNV_OFFSET = UINT64_C(14695981039346656037);
constexpr std::uint64_t FNV_PRIME = UINT64_C(1099511628211);

enum class TestMode {
	normal,
	protocol_only,
	hang,
	crash,
	mismatch,
	request_checksum_mismatch,
	response_checksum_mismatch
};

struct Options final {
	std::string nvrtc_path;
	std::string kernel_path;
	TestMode mode{TestMode::normal};
};

bool read_exact(void* destination, std::size_t bytes) {
	auto* output = static_cast<unsigned char*>(destination);
	std::size_t offset = 0U;
	while (offset < bytes) {
		const std::size_t read = std::fread(
			output + offset,
			1U,
			bytes - offset,
			stdin
		);
		if (read == 0U) {
			return false;
		}
		offset += read;
	}
	return true;
}

bool write_exact(const void* source, std::size_t bytes) {
	const auto* input = static_cast<const unsigned char*>(source);
	std::size_t offset = 0U;
	while (offset < bytes) {
		const std::size_t written = std::fwrite(
			input + offset,
			1U,
			bytes - offset,
			stdout
		);
		if (written == 0U) {
			return false;
		}
		offset += written;
	}
	return std::fflush(stdout) == 0;
}

std::uint64_t checksum(const void* source, std::size_t bytes) {
	const auto* input = static_cast<const unsigned char*>(source);
	std::uint64_t result = FNV_OFFSET;
	for (std::size_t index = 0U; index < bytes; ++index) {
		result ^= input[index];
		result *= FNV_PRIME;
	}
	return result;
}

bool response(
		const McfpvWorkerFrameHeader& request,
		std::int32_t status,
		const void* payload,
		std::uint32_t payload_bytes
	) {
	const McfpvWorkerFrameHeader header{
		MCFPV_WORKER_MAGIC,
		MCFPV_WORKER_PROTOCOL_VERSION,
		request.opcode,
		payload_bytes,
		status,
		request.deadline_millis,
		0U,
		request.generation,
		request.request_id,
		checksum(payload, payload_bytes)
	};
	return write_exact(&header, sizeof(header))
		&& (payload_bytes == 0U || write_exact(payload, payload_bytes));
}

bool error_response(
		const McfpvWorkerFrameHeader& request,
		std::int32_t status,
		const std::string& message
	) {
	const auto size = static_cast<std::uint32_t>(
		std::min<std::size_t>(
			message.size(),
			MCFPV_WORKER_MAXIMUM_PAYLOAD
		)
	);
	return response(request, status, message.data(), size);
}

bool checked_multiply(
		std::uint32_t count,
		std::size_t stride,
		std::size_t& result
	) {
	if (count > std::numeric_limits<std::size_t>::max() / stride) {
		return false;
	}
	result = static_cast<std::size_t>(count) * stride;
	return true;
}

std::string read_text(const std::string& path) {
	std::ifstream input(path, std::ios::binary);
	if (!input) {
		return {};
	}
	return std::string(
		std::istreambuf_iterator<char>(input),
		std::istreambuf_iterator<char>()
	);
}

Options parse_options(int count, char** values) {
	Options options;
	for (int index = 1; index < count; ++index) {
		const std::string argument = values[index];
		if (argument == "--nvrtc" && index + 1 < count) {
			options.nvrtc_path = values[++index];
		} else if (argument == "--kernel" && index + 1 < count) {
			options.kernel_path = values[++index];
		} else if (argument == "--mode=protocol-only") {
			options.mode = TestMode::protocol_only;
		} else if (argument == "--mode=hang") {
			options.mode = TestMode::hang;
		} else if (argument == "--mode=crash") {
			options.mode = TestMode::crash;
		} else if (argument == "--mode=mismatch") {
			options.mode = TestMode::mismatch;
		} else if (argument == "--mode=request-checksum-mismatch") {
			options.mode = TestMode::request_checksum_mismatch;
		} else if (argument == "--mode=response-checksum-mismatch") {
			options.mode = TestMode::response_checksum_mismatch;
		} else {
			throw std::invalid_argument("invalid worker argument");
		}
	}
	if (options.mode == TestMode::normal
			&& (options.nvrtc_path.empty() || options.kernel_path.empty())) {
		throw std::invalid_argument(
			"normal worker requires --nvrtc and --kernel"
		);
	}
	return options;
}

class Worker final {
public:
	explicit Worker(Options options)
		: options_(std::move(options)),
		  kernel_source_(
			  options_.mode == TestMode::normal
				  ? read_text(options_.kernel_path)
				  : std::string{}
		  ) {
	}

	~Worker() {
		if (bridge_ != nullptr) {
			mcfpv_cuda_bridge_destroy(bridge_);
		}
	}

	int run() {
		for (;;) {
			McfpvWorkerFrameHeader header{};
			if (!read_exact(&header, sizeof(header))) {
				return 0;
			}
			if (header.magic != MCFPV_WORKER_MAGIC
					|| header.version != MCFPV_WORKER_PROTOCOL_VERSION
					|| header.payload_bytes > MCFPV_WORKER_MAXIMUM_PAYLOAD
					|| header.status != 0
					|| header.deadline_millis == 0U
					|| header.reserved != 0U
					|| header.generation == 0U
					|| header.request_id == 0U) {
				return 2;
			}
			std::vector<unsigned char> payload(header.payload_bytes);
			if (header.payload_bytes > 0U
					&& !read_exact(payload.data(), payload.size())) {
				return 3;
			}
			if (options_.mode == TestMode::request_checksum_mismatch) {
				++header.payload_checksum;
			}
			if (header.payload_checksum
					!= checksum(payload.data(), payload.size())) {
				if (!error_response(
						header,
						11,
						"worker payload checksum mismatch"
					)) {
					return 8;
				}
				continue;
			}
			if (options_.mode == TestMode::hang) {
				for (;;) {
					std::this_thread::sleep_for(
						std::chrono::hours(24)
					);
				}
			}
			if (options_.mode == TestMode::crash) {
				return 86;
			}
			if (options_.mode == TestMode::mismatch) {
				++header.generation;
			}
			if (options_.mode == TestMode::response_checksum_mismatch) {
				const unsigned char corrupt_payload = 0x41U;
				const McfpvWorkerFrameHeader corrupt_header{
					MCFPV_WORKER_MAGIC,
					MCFPV_WORKER_PROTOCOL_VERSION,
					header.opcode,
					1U,
					0,
					header.deadline_millis,
					0U,
					header.generation,
					header.request_id,
					checksum(&corrupt_payload, 1U) ^ UINT64_C(1)
				};
				if (!write_exact(
						&corrupt_header,
						sizeof(corrupt_header)
					)
						|| !write_exact(&corrupt_payload, 1U)) {
					return 9;
				}
				continue;
			}
			switch (static_cast<McfpvWorkerOpcode>(header.opcode)) {
				case McfpvWorkerOpcode::ping:
					if (!response(header, 0, nullptr, 0U)) {
						return 4;
					}
					break;
				case McfpvWorkerOpcode::initialize:
					if (!initialize(header, payload)) {
						return 5;
					}
					break;
				case McfpvWorkerOpcode::submit:
					if (!submit(header, payload)) {
						return 6;
					}
					break;
				case McfpvWorkerOpcode::shutdown:
					response(header, 0, nullptr, 0U);
					return 0;
				default:
					if (!error_response(
							header,
							10,
							"unsupported worker opcode"
						)) {
						return 7;
					}
			}
		}
	}

private:
	bool initialize(
			const McfpvWorkerFrameHeader& request,
			const std::vector<unsigned char>& payload
		) {
		if (options_.mode == TestMode::protocol_only) {
			return error_response(
				request,
				20,
				"protocol-only worker rejects CUDA initialization"
			);
		}
		if (bridge_ != nullptr
				|| kernel_source_.empty()
				|| payload.size() < sizeof(McfpvWorkerInitializeHeader)) {
			return error_response(
				request,
				21,
				"invalid worker initialization"
			);
		}
		McfpvWorkerInitializeHeader header{};
		std::memcpy(&header, payload.data(), sizeof(header));
		std::size_t cell_bytes = 0U;
		std::size_t transmission_bytes = 0U;
		if (header.cell_count == 0U
				|| header.material_count == 0U
				|| header.maximum_rays == 0U
				|| !checked_multiply(
					header.cell_count,
					CELL_BYTES,
					cell_bytes
				)
				|| !checked_multiply(
					header.material_count,
					3U * sizeof(double),
					transmission_bytes
				)
				|| payload.size() != sizeof(header)
					+ cell_bytes + transmission_bytes) {
			return error_response(
				request,
				22,
				"worker initialization payload mismatch"
			);
		}
		const auto* cells = payload.data() + sizeof(header);
		const auto* transmission = reinterpret_cast<const double*>(
			cells + cell_bytes
		);
		const McfpvCudaBridgeConfig config{
			1U,
			0U,
			options_.nvrtc_path.c_str(),
			kernel_source_.c_str(),
			cells,
			header.cell_count,
			header.material_count,
			transmission,
			header.maximum_rays,
			0U
		};
		bridge_ = mcfpv_cuda_bridge_create(&config);
		if (bridge_ == nullptr) {
			return error_response(
				request,
				23,
				mcfpv_cuda_bridge_last_error()
			);
		}
		McfpvCudaBridgeInfo info{};
		const int status = mcfpv_cuda_bridge_info(bridge_, &info);
		if (status != 0) {
			return error_response(
				request,
				24,
				mcfpv_cuda_bridge_last_error()
			);
		}
		return response(
			request,
			0,
			&info,
			static_cast<std::uint32_t>(sizeof(info))
		);
	}

	bool submit(
			const McfpvWorkerFrameHeader& request,
			const std::vector<unsigned char>& payload
		) {
		if (bridge_ == nullptr
				|| payload.size() < sizeof(McfpvWorkerSubmitHeader)) {
			return error_response(
				request,
				30,
				"worker is not initialized"
			);
		}
		McfpvWorkerSubmitHeader header{};
		std::memcpy(&header, payload.data(), sizeof(header));
		std::size_t ray_bytes = 0U;
		std::size_t result_bytes = 0U;
		if (header.ray_count == 0U
				|| !checked_multiply(
					header.ray_count,
					RAY_BYTES,
					ray_bytes
				)
				|| !checked_multiply(
					header.ray_count,
					RESULT_BYTES,
					result_bytes
				)
				|| payload.size() != sizeof(header) + ray_bytes
				|| result_bytes > MCFPV_WORKER_MAXIMUM_PAYLOAD
					- sizeof(McfpvCudaSubmitMetrics)) {
			return error_response(
				request,
				31,
				"worker submit payload mismatch"
			);
		}
		std::vector<unsigned char> output(
			sizeof(McfpvCudaSubmitMetrics) + result_bytes
		);
		auto* metrics = reinterpret_cast<McfpvCudaSubmitMetrics*>(
			output.data()
		);
		McfpvCudaSubmitRequest submit_request{
			payload.data() + sizeof(header),
			output.data() + sizeof(McfpvCudaSubmitMetrics),
			header.ray_count,
			0U,
			metrics
		};
		const int status = mcfpv_cuda_bridge_submit(
			bridge_,
			&submit_request
		);
		if (status != 0) {
			return error_response(
				request,
				32,
				mcfpv_cuda_bridge_last_error()
			);
		}
		return response(
			request,
			0,
			output.data(),
			static_cast<std::uint32_t>(output.size())
		);
	}

	Options options_;
	std::string kernel_source_;
	void* bridge_{};
};

}  // namespace

int main(int count, char** values) {
#if defined(_WIN32)
	_setmode(_fileno(stdin), _O_BINARY);
	_setmode(_fileno(stdout), _O_BINARY);
#endif
	try {
		return Worker(parse_options(count, values)).run();
	} catch (const std::exception& error) {
		std::fprintf(stderr, "worker startup error: %s\n", error.what());
		return 64;
	}
}
