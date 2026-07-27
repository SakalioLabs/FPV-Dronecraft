#pragma once

#include <cstddef>
#include <cstdint>

constexpr std::uint32_t MCFPV_WORKER_MAGIC = UINT32_C(0x5746434d);
constexpr std::uint16_t MCFPV_WORKER_PROTOCOL_VERSION = 1U;
constexpr std::uint32_t MCFPV_WORKER_MAXIMUM_PAYLOAD =
	UINT32_C(128) * UINT32_C(1024) * UINT32_C(1024);

enum class McfpvWorkerOpcode : std::uint16_t {
	initialize = 1U,
	submit = 2U,
	ping = 3U,
	shutdown = 4U
};

struct McfpvWorkerFrameHeader final {
	std::uint32_t magic;
	std::uint16_t version;
	std::uint16_t opcode;
	std::uint32_t payload_bytes;
	std::int32_t status;
	std::uint32_t deadline_millis;
	std::uint32_t reserved;
	std::uint64_t generation;
	std::uint64_t request_id;
	std::uint64_t payload_checksum;
};

struct McfpvWorkerInitializeHeader final {
	std::uint32_t cell_count;
	std::uint32_t material_count;
	std::uint32_t maximum_rays;
	std::uint32_t reserved;
};

struct McfpvWorkerSubmitHeader final {
	std::uint32_t ray_count;
	std::uint32_t reserved;
};

static_assert(sizeof(McfpvWorkerFrameHeader) == 48U);
static_assert(offsetof(McfpvWorkerFrameHeader, generation) == 24U);
static_assert(offsetof(McfpvWorkerFrameHeader, request_id) == 32U);
static_assert(offsetof(McfpvWorkerFrameHeader, payload_checksum) == 40U);
static_assert(sizeof(McfpvWorkerInitializeHeader) == 16U);
static_assert(sizeof(McfpvWorkerSubmitHeader) == 8U);
