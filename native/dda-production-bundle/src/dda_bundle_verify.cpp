#include <algorithm>
#include <array>
#include <bit>
#include <cmath>
#include <cstdint>
#include <filesystem>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <limits>
#include <optional>
#include <span>
#include <sstream>
#include <stdexcept>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

namespace {

constexpr std::string_view kBundleMagic = "MCFPDDA1";
constexpr std::string_view kMaterialMagic = "MCFMAT01";
constexpr std::string_view kSnapshotMagic =
		"MCFPV-SparseMaterialSnapshot-v1";
constexpr std::string_view kExpectedResultsMagic = "MCFPREF1";
constexpr std::int32_t kBundleSchema = 1;
constexpr std::int32_t kMaterialSchema = 1;
constexpr std::int32_t kExpectedResultsSchema = 1;
constexpr std::int32_t kMaximumStringBytes = 1 << 20;
constexpr std::int32_t kMaximumCells = 16'777'216;
constexpr std::int32_t kMaximumRays = 1'000'000;
constexpr std::uint8_t kFlagReached = 1U;
constexpr std::uint8_t kFlagStopped = 2U;
constexpr std::uint8_t kFlagTruncated = 4U;
constexpr std::string_view kExpectedMaterialHash =
		"8bed6d00ec4e433107ad44ad8178dc72fb4d6fb7633577c5cf9ea618f5ae84d8";
constexpr std::string_view kFixtureSnapshotHash =
		"d7fbeb8e26df3c85bb937c91c59d993a55726310f18e5c22273a2ac85871a990";
constexpr std::string_view kFixtureFileHash =
		"f905c3039d4e0815f4ece5e1fc91aed9b6ab07ff907cdcc889da9fd3b159e9ff";

using Digest = std::array<std::uint8_t, 32>;

constexpr std::array<std::uint32_t, 64> kSha256Constants = {
		0x428a2f98U, 0x71374491U, 0xb5c0fbcfU, 0xe9b5dba5U,
		0x3956c25bU, 0x59f111f1U, 0x923f82a4U, 0xab1c5ed5U,
		0xd807aa98U, 0x12835b01U, 0x243185beU, 0x550c7dc3U,
		0x72be5d74U, 0x80deb1feU, 0x9bdc06a7U, 0xc19bf174U,
		0xe49b69c1U, 0xefbe4786U, 0x0fc19dc6U, 0x240ca1ccU,
		0x2de92c6fU, 0x4a7484aaU, 0x5cb0a9dcU, 0x76f988daU,
		0x983e5152U, 0xa831c66dU, 0xb00327c8U, 0xbf597fc7U,
		0xc6e00bf3U, 0xd5a79147U, 0x06ca6351U, 0x14292967U,
		0x27b70a85U, 0x2e1b2138U, 0x4d2c6dfcU, 0x53380d13U,
		0x650a7354U, 0x766a0abbU, 0x81c2c92eU, 0x92722c85U,
		0xa2bfe8a1U, 0xa81a664bU, 0xc24b8b70U, 0xc76c51a3U,
		0xd192e819U, 0xd6990624U, 0xf40e3585U, 0x106aa070U,
		0x19a4c116U, 0x1e376c08U, 0x2748774cU, 0x34b0bcb5U,
		0x391c0cb3U, 0x4ed8aa4aU, 0x5b9cca4fU, 0x682e6ff3U,
		0x748f82eeU, 0x78a5636fU, 0x84c87814U, 0x8cc70208U,
		0x90befffaU, 0xa4506cebU, 0xbef9a3f7U, 0xc67178f2U
};

class Sha256 {
public:
	void update(std::span<const std::uint8_t> bytes) {
		total_bytes_ += bytes.size();
		for (const std::uint8_t value : bytes) {
			buffer_[buffer_size_++] = value;
			if (buffer_size_ == buffer_.size()) {
				process_block(buffer_);
				buffer_size_ = 0;
			}
		}
	}

	[[nodiscard]] Digest finish() {
		const std::uint64_t bit_count =
				static_cast<std::uint64_t>(total_bytes_) * 8U;
		buffer_[buffer_size_++] = 0x80U;
		if (buffer_size_ > 56U) {
			while (buffer_size_ < buffer_.size()) {
				buffer_[buffer_size_++] = 0U;
			}
			process_block(buffer_);
			buffer_size_ = 0;
		}
		while (buffer_size_ < 56U) {
			buffer_[buffer_size_++] = 0U;
		}
		for (int shift = 56; shift >= 0; shift -= 8) {
			buffer_[buffer_size_++] = static_cast<std::uint8_t>(
					bit_count >> shift
			);
		}
		process_block(buffer_);
		Digest digest{};
		for (std::size_t index = 0; index < state_.size(); ++index) {
			for (int shift = 24; shift >= 0; shift -= 8) {
				digest[index * 4U + static_cast<std::size_t>(
						(24 - shift) / 8
				)] = static_cast<std::uint8_t>(state_[index] >> shift);
			}
		}
		return digest;
	}

private:
	static std::uint32_t rotate_right(
			const std::uint32_t value,
			const int count
	) {
		return (value >> count) | (value << (32 - count));
	}

	void process_block(const std::array<std::uint8_t, 64>& block) {
		std::array<std::uint32_t, 64> words{};
		for (std::size_t index = 0; index < 16U; ++index) {
			const std::size_t offset = index * 4U;
			words[index] =
					(static_cast<std::uint32_t>(block[offset]) << 24U)
					| (static_cast<std::uint32_t>(
							block[offset + 1U]
					) << 16U)
					| (static_cast<std::uint32_t>(
							block[offset + 2U]
					) << 8U)
					| static_cast<std::uint32_t>(block[offset + 3U]);
		}
		for (std::size_t index = 16U; index < words.size(); ++index) {
			const std::uint32_t s0 =
					rotate_right(words[index - 15U], 7)
					^ rotate_right(words[index - 15U], 18)
					^ (words[index - 15U] >> 3U);
			const std::uint32_t s1 =
					rotate_right(words[index - 2U], 17)
					^ rotate_right(words[index - 2U], 19)
					^ (words[index - 2U] >> 10U);
			words[index] = words[index - 16U] + s0
					+ words[index - 7U] + s1;
		}
		std::uint32_t a = state_[0];
		std::uint32_t b = state_[1];
		std::uint32_t c = state_[2];
		std::uint32_t d = state_[3];
		std::uint32_t e = state_[4];
		std::uint32_t f = state_[5];
		std::uint32_t g = state_[6];
		std::uint32_t h = state_[7];
		for (std::size_t index = 0; index < words.size(); ++index) {
			const std::uint32_t sum1 =
					rotate_right(e, 6) ^ rotate_right(e, 11)
					^ rotate_right(e, 25);
			const std::uint32_t choice = (e & f) ^ ((~e) & g);
			const std::uint32_t temporary1 = h + sum1 + choice
					+ kSha256Constants[index] + words[index];
			const std::uint32_t sum0 =
					rotate_right(a, 2) ^ rotate_right(a, 13)
					^ rotate_right(a, 22);
			const std::uint32_t majority =
					(a & b) ^ (a & c) ^ (b & c);
			const std::uint32_t temporary2 = sum0 + majority;
			h = g;
			g = f;
			f = e;
			e = d + temporary1;
			d = c;
			c = b;
			b = a;
			a = temporary1 + temporary2;
		}
		state_[0] += a;
		state_[1] += b;
		state_[2] += c;
		state_[3] += d;
		state_[4] += e;
		state_[5] += f;
		state_[6] += g;
		state_[7] += h;
	}

	std::array<std::uint32_t, 8> state_ = {
			0x6a09e667U,
			0xbb67ae85U,
			0x3c6ef372U,
			0xa54ff53aU,
			0x510e527fU,
			0x9b05688cU,
			0x1f83d9abU,
			0x5be0cd19U
	};
	std::array<std::uint8_t, 64> buffer_{};
	std::size_t buffer_size_ = 0;
	std::size_t total_bytes_ = 0;
};

[[nodiscard]] Digest sha256(std::span<const std::uint8_t> bytes) {
	Sha256 hasher;
	hasher.update(bytes);
	return hasher.finish();
}

[[nodiscard]] std::string hex(const Digest& digest) {
	std::ostringstream output;
	output << std::hex << std::setfill('0');
	for (const std::uint8_t value : digest) {
		output << std::setw(2) << static_cast<unsigned int>(value);
	}
	return output.str();
}

class Bytes {
public:
	void append_u8(const std::uint8_t value) {
		values_.push_back(value);
	}

	void append_i32(const std::int32_t value) {
		append_u32(std::bit_cast<std::uint32_t>(value));
	}

	void append_u32(const std::uint32_t value) {
		for (int shift = 24; shift >= 0; shift -= 8) {
			values_.push_back(static_cast<std::uint8_t>(value >> shift));
		}
	}

	void append_u64(const std::uint64_t value) {
		for (int shift = 56; shift >= 0; shift -= 8) {
			values_.push_back(static_cast<std::uint8_t>(value >> shift));
		}
	}

	void append_double(const double value) {
		append_u64(std::bit_cast<std::uint64_t>(value));
	}

	void append_text(const std::string_view value) {
		append_i32(static_cast<std::int32_t>(value.size()));
		values_.insert(values_.end(), value.begin(), value.end());
	}

	void append_raw(const std::string_view value) {
		values_.insert(values_.end(), value.begin(), value.end());
	}

	[[nodiscard]] const std::vector<std::uint8_t>& values() const {
		return values_;
	}

private:
	std::vector<std::uint8_t> values_;
};

struct Material {
	std::string_view id;
	std::array<double, 3> transmission;
	std::array<double, 3> absorption;
	double scattering;
};

constexpr std::array<Material, 8> kMaterials = {{
		{"air", {0.0, 0.0, 0.0}, {0.0, 0.0, 0.0}, 0.0},
		{"foliage", {0.3, 1.2, 3.0}, {0.10, 0.35, 0.65}, 0.75},
		{"wood", {4.0, 9.0, 15.0}, {0.12, 0.22, 0.35}, 0.45},
		{"glass", {3.0, 8.0, 14.0}, {0.05, 0.08, 0.12}, 0.08},
		{"stone", {12.0, 24.0, 36.0}, {0.03, 0.05, 0.08}, 0.18},
		{"metal", {8.0, 20.0, 35.0}, {0.02, 0.04, 0.06}, 0.12},
		{"water", {6.0, 18.0, 30.0}, {0.08, 0.20, 0.42}, 0.05},
		{"soft", {2.0, 7.0, 13.0}, {0.25, 0.55, 0.78}, 0.65}
}};

[[nodiscard]] Digest material_table_hash() {
	Bytes bytes;
	bytes.append_raw(kMaterialMagic);
	bytes.append_i32(kMaterialSchema);
	bytes.append_i32(static_cast<std::int32_t>(kMaterials.size()));
	for (const Material& material : kMaterials) {
		bytes.append_text(material.id);
		for (const double value : material.transmission) {
			bytes.append_double(value);
		}
		for (const double value : material.absorption) {
			bytes.append_double(value);
		}
		bytes.append_double(material.scattering);
	}
	return sha256(bytes.values());
}

[[nodiscard]] bool valid_utf8(const std::string_view value) {
	std::size_t index = 0;
	while (index < value.size()) {
		const auto first = static_cast<std::uint8_t>(value[index]);
		if (first <= 0x7fU) {
			++index;
			continue;
		}
		std::size_t count = 0;
		std::uint32_t code_point = 0;
		if (first >= 0xc2U && first <= 0xdfU) {
			count = 1;
			code_point = first & 0x1fU;
		} else if (first >= 0xe0U && first <= 0xefU) {
			count = 2;
			code_point = first & 0x0fU;
		} else if (first >= 0xf0U && first <= 0xf4U) {
			count = 3;
			code_point = first & 0x07U;
		} else {
			return false;
		}
		if (index + count >= value.size()) {
			return false;
		}
		for (std::size_t offset = 1; offset <= count; ++offset) {
			const auto next = static_cast<std::uint8_t>(
					value[index + offset]
			);
			if ((next & 0xc0U) != 0x80U) {
				return false;
			}
			code_point = (code_point << 6U) | (next & 0x3fU);
		}
		if ((count == 2U && code_point < 0x800U)
				|| (count == 3U && code_point < 0x10000U)
				|| (code_point >= 0xd800U && code_point <= 0xdfffU)
				|| code_point > 0x10ffffU) {
			return false;
		}
		index += count + 1U;
	}
	return true;
}

class Reader {
public:
	explicit Reader(std::span<const std::uint8_t> bytes) : bytes_(bytes) {
	}

	[[nodiscard]] std::span<const std::uint8_t> take(
			const std::size_t count
	) {
		if (count > bytes_.size() - offset_) {
			throw std::runtime_error("truncated production bundle");
		}
		const auto result = bytes_.subspan(offset_, count);
		offset_ += count;
		return result;
	}

	[[nodiscard]] std::uint8_t u8() {
		return take(1U)[0];
	}

	[[nodiscard]] std::uint32_t u32() {
		const auto bytes = take(4U);
		return (static_cast<std::uint32_t>(bytes[0]) << 24U)
				| (static_cast<std::uint32_t>(bytes[1]) << 16U)
				| (static_cast<std::uint32_t>(bytes[2]) << 8U)
				| static_cast<std::uint32_t>(bytes[3]);
	}

	[[nodiscard]] std::int32_t i32() {
		return std::bit_cast<std::int32_t>(u32());
	}

	[[nodiscard]] std::uint64_t u64() {
		const auto bytes = take(8U);
		std::uint64_t value = 0;
		for (const std::uint8_t byte : bytes) {
			value = (value << 8U) | byte;
		}
		return value;
	}

	[[nodiscard]] std::int64_t i64() {
		return std::bit_cast<std::int64_t>(u64());
	}

	[[nodiscard]] double f64() {
		return std::bit_cast<double>(u64());
	}

	[[nodiscard]] std::string text() {
		const std::int32_t length = i32();
		if (length < 0 || length > kMaximumStringBytes) {
			throw std::runtime_error("string byte count is out of range");
		}
		const auto bytes = take(static_cast<std::size_t>(length));
		const std::string value(bytes.begin(), bytes.end());
		if (!valid_utf8(value)) {
			throw std::runtime_error(
					"metadata string is not valid UTF-8"
			);
		}
		return value;
	}

	[[nodiscard]] std::size_t offset() const {
		return offset_;
	}

private:
	std::span<const std::uint8_t> bytes_;
	std::size_t offset_ = 0;
};

[[nodiscard]] Digest digest_from(Reader& reader) {
	Digest result{};
	const auto bytes = reader.take(result.size());
	std::copy(bytes.begin(), bytes.end(), result.begin());
	return result;
}

struct Cell {
	std::uint64_t packed;
	std::int32_t material_id;
	double fill_fraction;
};

struct Ray {
	std::array<double, 3> start;
	std::array<double, 3> end;
	std::int32_t maximum_cells;
};

struct Summary {
	std::int32_t mapping_version;
	std::string minecraft_version;
	std::string mod_version;
	Digest content_fingerprint;
	std::int64_t generation;
	bool complete;
	Digest snapshot_hash;
	std::vector<Cell> cells;
	std::vector<Ray> rays;
	Digest file_hash;
	std::size_t file_bytes;
};

struct ExpectedSegment {
	std::uint64_t packed;
	double length;
	std::int32_t material_id;
	double fill_fraction;
};

struct ExpectedTrace {
	std::vector<ExpectedSegment> segments;
	std::int32_t material_cells;
	bool reached;
	bool stopped;
	bool truncated;
	std::array<double, 3> loss;
	std::array<double, 3> gain;
	std::optional<std::uint64_t> first_material;
};

struct ExpectedResultsSummary {
	std::int32_t rays;
	std::uint64_t segments;
	Digest file_hash;
	std::size_t file_bytes;
};

[[nodiscard]] std::uint64_t pack_cell(
		const std::int32_t x,
		const std::int32_t y,
		const std::int32_t z
	) {
	return (static_cast<std::uint64_t>(x) & 0x3ffffffULL) << 38U
			| (static_cast<std::uint64_t>(z) & 0x3ffffffULL) << 12U
			| (static_cast<std::uint64_t>(y) & 0xfffULL);
}

[[nodiscard]] std::pair<std::int32_t, double> sample_cell(
		const std::vector<Cell>& cells,
		const std::uint64_t packed
	) {
	const auto iterator = std::lower_bound(
			cells.begin(),
			cells.end(),
			packed,
			[](const Cell& cell, const std::uint64_t value) {
				return cell.packed < value;
			}
	);
	if (iterator == cells.end() || iterator->packed != packed) {
		return {0, 0.0};
	}
	return {iterator->material_id, iterator->fill_fraction};
}

[[nodiscard]] double initial_t(
		const double start,
		const double delta,
		const std::int32_t cell,
		const std::int32_t step
	) {
	if (step == 0) {
		return std::numeric_limits<double>::infinity();
	}
	const double boundary = step > 0
			? static_cast<double>(cell) + 1.0
			: static_cast<double>(cell);
	return (boundary - start) / delta;
}

[[nodiscard]] ExpectedTrace trace_ray(
		const Ray& ray,
		const std::vector<Cell>& cells
	) {
	const std::array<double, 3> delta = {
			ray.end[0] - ray.start[0],
			ray.end[1] - ray.start[1],
			ray.end[2] - ray.start[2]
	};
	const double total_length = std::sqrt(
			delta[0] * delta[0]
			+ delta[1] * delta[1]
			+ delta[2] * delta[2]
	);
	ExpectedTrace result{
			{},
			0,
			false,
			false,
			false,
			{0.0, 0.0, 0.0},
			{0.0, 0.0, 0.0},
			std::nullopt
	};
	auto visit = [&](const std::int32_t x,
			const std::int32_t y,
			const std::int32_t z,
			const double length) {
		const std::uint64_t packed = pack_cell(x, y, z);
		const auto [material_id, fill] = sample_cell(cells, packed);
		const Material& material = kMaterials.at(
				static_cast<std::size_t>(material_id)
		);
		const double effective_length = length * fill;
		for (std::size_t band = 0; band < result.loss.size(); ++band) {
			result.loss[band] +=
					material.transmission[band] * effective_length;
		}
		if (material_id != 0 && effective_length > 0.0) {
			++result.material_cells;
			if (!result.first_material.has_value()) {
				result.first_material = packed;
			}
		}
		result.segments.push_back({
				packed,
				length,
				material_id,
				fill
		});
	};
	if (total_length <= 1.0e-12) {
		visit(
				static_cast<std::int32_t>(std::floor(ray.start[0])),
				static_cast<std::int32_t>(std::floor(ray.start[1])),
				static_cast<std::int32_t>(std::floor(ray.start[2])),
				0.0
		);
		result.reached = true;
	} else {
		std::array<std::int32_t, 3> current = {
				static_cast<std::int32_t>(std::floor(ray.start[0])),
				static_cast<std::int32_t>(std::floor(ray.start[1])),
				static_cast<std::int32_t>(std::floor(ray.start[2]))
		};
		const std::array<std::int32_t, 3> target = {
				static_cast<std::int32_t>(std::floor(ray.end[0])),
				static_cast<std::int32_t>(std::floor(ray.end[1])),
				static_cast<std::int32_t>(std::floor(ray.end[2]))
		};
		std::array<std::int32_t, 3> step{};
		std::array<double, 3> t_delta{};
		std::array<double, 3> t_max{};
		for (std::size_t axis = 0; axis < step.size(); ++axis) {
			step[axis] = delta[axis] > 0.0
					? 1
					: delta[axis] < 0.0 ? -1 : 0;
			t_delta[axis] = step[axis] == 0
					? std::numeric_limits<double>::infinity()
					: std::abs(1.0 / delta[axis]);
			t_max[axis] = initial_t(
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
			const double exit_t = std::min(
					1.0,
					std::min(t_max[0], std::min(t_max[1], t_max[2]))
			);
			const double segment_length =
					std::max(0.0, exit_t - entry_t) * total_length;
			visit(
					current[0],
					current[1],
					current[2],
					segment_length
			);
			if (current == target) {
				result.reached = true;
				break;
			}
			const double crossing = std::min(
					t_max[0],
					std::min(t_max[1], t_max[2])
			);
			const double epsilon = std::max(
					1.0e-12,
					std::abs(crossing) * 1.0e-12
			);
			for (std::size_t axis = 0; axis < step.size(); ++axis) {
				if (std::abs(t_max[axis] - crossing) <= epsilon) {
					current[axis] += step[axis];
					t_max[axis] += t_delta[axis];
				}
			}
			entry_t = std::min(1.0, crossing);
		}
	}
	result.truncated = !result.reached && !result.stopped;
	for (std::size_t band = 0; band < result.gain.size(); ++band) {
		result.gain[band] = std::pow(
				10.0,
				-result.loss[band] / 10.0
		);
	}
	return result;
}

[[nodiscard]] bool close_double(
		const double first,
		const double second
	) {
	return std::abs(first - second)
			<= std::max(
					1.0e-12,
					1.0e-12 * std::max(
							std::abs(first),
							std::abs(second)
					)
			);
}

[[nodiscard]] ExpectedResultsSummary verify_expected_results(
		const Summary& bundle,
		const std::vector<std::uint8_t>& payload
	) {
	Reader reader(payload);
	const auto magic = reader.take(kExpectedResultsMagic.size());
	if (!std::equal(
			magic.begin(),
			magic.end(),
			kExpectedResultsMagic.begin(),
			kExpectedResultsMagic.end()
	)) {
		throw std::runtime_error("invalid expected-results magic");
	}
	if (reader.i32() != kExpectedResultsSchema) {
		throw std::runtime_error(
				"unsupported expected-results schema"
		);
	}
	if (digest_from(reader) != bundle.file_hash) {
		throw std::runtime_error("input bundle hash mismatch");
	}
	if (digest_from(reader) != bundle.snapshot_hash) {
		throw std::runtime_error("snapshot hash mismatch");
	}
	const std::int32_t ray_count = reader.i32();
	if (ray_count != static_cast<std::int32_t>(bundle.rays.size())) {
		throw std::runtime_error("ray count does not match bundle");
	}
	std::uint64_t total_segments = 0U;
	for (std::int32_t ray_id = 0; ray_id < ray_count; ++ray_id) {
		const ExpectedTrace actual = trace_ray(
				bundle.rays[static_cast<std::size_t>(ray_id)],
				bundle.cells
		);
		if (reader.i32() != ray_id) {
			throw std::runtime_error("expected ray id mismatch");
		}
		const std::int32_t segment_count = reader.i32();
		const std::int32_t visited = reader.i32();
		const std::int32_t material_cells = reader.i32();
		if (segment_count < 1
				|| segment_count
						> bundle.rays[static_cast<std::size_t>(
								ray_id
						)].maximum_cells
				|| visited != segment_count
				|| visited
						!= static_cast<std::int32_t>(
								actual.segments.size()
						)
				|| material_cells != actual.material_cells) {
			throw std::runtime_error(
					"expected result mismatch for ray"
			);
		}
		const std::uint8_t flags = reader.u8();
		if ((flags & ~(kFlagReached
				| kFlagStopped
				| kFlagTruncated)) != 0U) {
			throw std::runtime_error(
					"unknown expected-result flags"
			);
		}
		const std::uint8_t actual_flags =
				(actual.reached ? kFlagReached : 0U)
				| (actual.stopped ? kFlagStopped : 0U)
				| (actual.truncated ? kFlagTruncated : 0U);
		if (flags != actual_flags) {
			throw std::runtime_error(
					"expected flags mismatch for ray"
			);
		}
		for (std::size_t band = 0; band < actual.loss.size(); ++band) {
			if (!close_double(reader.f64(), actual.loss[band])) {
				throw std::runtime_error("loss mismatch for ray");
			}
		}
		for (std::size_t band = 0; band < actual.gain.size(); ++band) {
			if (!close_double(reader.f64(), actual.gain[band])) {
				throw std::runtime_error("gain mismatch for ray");
			}
		}
		const std::uint8_t first_flag = reader.u8();
		if (first_flag > 1U) {
			throw std::runtime_error(
					"first-material flag is not boolean"
			);
		}
		const std::optional<std::uint64_t> first_material =
				first_flag == 1U
				? std::optional<std::uint64_t>(reader.u64())
				: std::nullopt;
		if (first_material != actual.first_material) {
			throw std::runtime_error(
					"first material mismatch for ray"
			);
		}
		for (const ExpectedSegment& segment : actual.segments) {
			const std::uint64_t packed = reader.u64();
			const double length = reader.f64();
			const std::int32_t material_id = reader.i32();
			const double fill = reader.f64();
			if (packed != segment.packed
					|| material_id != segment.material_id
					|| fill != segment.fill_fraction
					|| !close_double(length, segment.length)) {
				throw std::runtime_error(
						"segment mismatch for ray"
				);
			}
		}
		total_segments += static_cast<std::uint64_t>(segment_count);
	}
	if (reader.offset() != payload.size()) {
		throw std::runtime_error(
				"trailing bytes after expected results"
		);
	}
	return {
			ray_count,
			total_segments,
			sha256(payload),
			payload.size()
	};
}

[[nodiscard]] Digest snapshot_hash(
		const bool complete,
		const std::vector<Cell>& cells
	) {
	Bytes bytes;
	bytes.append_i32(static_cast<std::int32_t>(kSnapshotMagic.size()));
	bytes.append_raw(kSnapshotMagic);
	bytes.append_u8(complete ? 1U : 0U);
	bytes.append_i32(static_cast<std::int32_t>(cells.size()));
	for (const Cell& cell : cells) {
		bytes.append_u64(cell.packed);
		const Material& material = kMaterials.at(
				static_cast<std::size_t>(cell.material_id)
		);
		bytes.append_text(material.id);
		for (const double value : material.transmission) {
			bytes.append_double(value);
		}
		for (const double value : material.absorption) {
			bytes.append_double(value);
		}
		bytes.append_double(material.scattering);
		bytes.append_double(cell.fill_fraction);
	}
	return sha256(bytes.values());
}

void require_magic(Reader& reader, const std::string_view expected) {
	const auto actual = reader.take(expected.size());
	if (!std::equal(
			actual.begin(),
			actual.end(),
			expected.begin(),
			expected.end()
	)) {
		throw std::runtime_error("invalid production bundle magic");
	}
}

[[nodiscard]] Summary verify(
		const std::vector<std::uint8_t>& payload
	) {
	Reader reader(payload);
	require_magic(reader, kBundleMagic);
	if (reader.i32() != kBundleSchema) {
		throw std::runtime_error(
				"unsupported production bundle schema"
		);
	}
	const std::int32_t mapping_version = reader.i32();
	if (mapping_version < 1) {
		throw std::runtime_error(
				"mapping algorithm version must be positive"
		);
	}
	if (reader.i32() != kMaterialSchema) {
		throw std::runtime_error("unsupported material table schema");
	}
	const Digest stored_material_hash = digest_from(reader);
	if (stored_material_hash != material_table_hash()) {
		throw std::runtime_error("material table hash changed");
	}
	std::string minecraft_version = reader.text();
	std::string mod_version = reader.text();
	if (minecraft_version.empty() || mod_version.empty()) {
		throw std::runtime_error("version strings must not be blank");
	}
	const Digest content_fingerprint = digest_from(reader);
	const std::int64_t generation = reader.i64();
	if (generation < 0) {
		throw std::runtime_error(
				"snapshot generation must be non-negative"
		);
	}
	const std::uint8_t complete_value = reader.u8();
	if (complete_value > 1U) {
		throw std::runtime_error(
				"snapshot complete flag is not boolean"
		);
	}
	const bool complete = complete_value == 1U;
	const Digest stored_snapshot_hash = digest_from(reader);
	const std::int32_t cell_count = reader.i32();
	if (cell_count < 1 || cell_count > kMaximumCells) {
		throw std::runtime_error("cell count is out of range");
	}
	std::vector<Cell> cells;
	cells.reserve(static_cast<std::size_t>(cell_count));
	for (std::int32_t index = 0; index < cell_count; ++index) {
		const std::uint64_t packed = reader.u64();
		if (!cells.empty() && packed <= cells.back().packed) {
			throw std::runtime_error(
					"snapshot cells must be strictly ordered"
			);
		}
		const std::int32_t material_id = reader.i32();
		if (material_id < 0
				|| material_id >= static_cast<std::int32_t>(
						kMaterials.size()
				)) {
			throw std::runtime_error("material id is out of range");
		}
		const double fill_fraction = reader.f64();
		if (!std::isfinite(fill_fraction)
				|| fill_fraction < 0.0
				|| fill_fraction > 1.0) {
			throw std::runtime_error("fill fraction is out of range");
		}
		cells.push_back({packed, material_id, fill_fraction});
	}
	if (stored_snapshot_hash != snapshot_hash(complete, cells)) {
		throw std::runtime_error("snapshot hash mismatch");
	}
	const std::int32_t ray_count = reader.i32();
	if (ray_count < 1 || ray_count > kMaximumRays) {
		throw std::runtime_error("ray count is out of range");
	}
	std::vector<Ray> rays;
	rays.reserve(static_cast<std::size_t>(ray_count));
	for (std::int32_t ray = 0; ray < ray_count; ++ray) {
		Ray input{};
		for (double& coordinate : input.start) {
			coordinate = reader.f64();
			if (!std::isfinite(coordinate)) {
				throw std::runtime_error(
						"ray coordinates must be finite"
				);
			}
		}
		for (double& coordinate : input.end) {
			coordinate = reader.f64();
			if (!std::isfinite(coordinate)) {
				throw std::runtime_error(
						"ray coordinates must be finite"
				);
			}
		}
		input.maximum_cells = reader.i32();
		if (input.maximum_cells < 1) {
			throw std::runtime_error(
					"maximum cells must be positive"
			);
		}
		rays.push_back(input);
	}
	if (reader.offset() != payload.size()) {
		throw std::runtime_error(
				"trailing bytes after production bundle"
		);
	}
	return {
			mapping_version,
			std::move(minecraft_version),
			std::move(mod_version),
			content_fingerprint,
			generation,
			complete,
			stored_snapshot_hash,
			std::move(cells),
			std::move(rays),
			sha256(payload),
			payload.size()
	};
}

[[nodiscard]] std::int32_t sign_extend(
		const std::uint64_t value,
		const int bits
	) {
	const std::uint64_t sign = std::uint64_t{1} << (bits - 1);
	const std::uint64_t mask = (std::uint64_t{1} << bits) - 1U;
	const std::uint64_t truncated = value & mask;
	if ((truncated & sign) == 0U) {
		return static_cast<std::int32_t>(truncated);
	}
	return static_cast<std::int32_t>(
			static_cast<std::int64_t>(truncated)
			- (std::int64_t{1} << bits)
	);
}

[[nodiscard]] std::array<std::int32_t, 3> unpack_cell(
		const std::uint64_t packed
	) {
	return {
			sign_extend(packed >> 38U, 26),
			sign_extend(packed, 12),
			sign_extend(packed >> 12U, 26)
	};
}

[[nodiscard]] std::vector<std::uint8_t> read_file(
		const std::filesystem::path& path
	) {
	std::ifstream input(path, std::ios::binary);
	if (!input) {
		throw std::runtime_error("cannot open production bundle");
	}
	input.seekg(0, std::ios::end);
	const std::streamoff length = input.tellg();
	if (length < 0) {
		throw std::runtime_error("cannot determine bundle size");
	}
	input.seekg(0, std::ios::beg);
	std::vector<std::uint8_t> result(
			static_cast<std::size_t>(length)
	);
	if (!result.empty()) {
		input.read(
				reinterpret_cast<char*>(result.data()),
				static_cast<std::streamsize>(result.size())
		);
	}
	if (!input) {
		throw std::runtime_error("cannot read production bundle");
	}
	return result;
}

void require_fixture(const Summary& summary) {
	if (summary.mapping_version != 1
			|| summary.minecraft_version != "fixture-1.21.11"
			|| summary.mod_version != "fixture-v1"
			|| summary.generation != 42
			|| !summary.complete
			|| summary.cells.size() != 8U
			|| summary.rays.size() != 3U
			|| hex(summary.snapshot_hash) != kFixtureSnapshotHash
			|| hex(summary.file_hash) != kFixtureFileHash) {
		throw std::runtime_error(
				"bundle is not the canonical Java fixture"
		);
	}
}

void print_summary(const Summary& summary) {
	const auto first = unpack_cell(summary.cells.front().packed);
	const auto last = unpack_cell(summary.cells.back().packed);
	std::cout
			<< "{\"status\":\"valid\",\"schema\":1"
			<< ",\"mapping_algorithm_version\":"
			<< summary.mapping_version
			<< ",\"material_table_sha256\":\""
			<< hex(material_table_hash()) << "\""
			<< ",\"minecraft_version\":\""
			<< summary.minecraft_version << "\""
			<< ",\"mod_version\":\"" << summary.mod_version << "\""
			<< ",\"snapshot_generation\":" << summary.generation
			<< ",\"snapshot_complete\":"
			<< (summary.complete ? "true" : "false")
			<< ",\"snapshot_sha256\":\""
			<< hex(summary.snapshot_hash) << "\""
			<< ",\"cells\":" << summary.cells.size()
			<< ",\"rays\":" << summary.rays.size()
			<< ",\"first_cell\":[" << first[0] << ',' << first[1]
			<< ',' << first[2] << ']'
			<< ",\"last_cell\":[" << last[0] << ',' << last[1]
			<< ',' << last[2] << ']'
			<< ",\"file_sha256\":\"" << hex(summary.file_hash) << "\""
			<< ",\"bytes\":" << summary.file_bytes << "}\n";
}

void print_expected_summary(
		const ExpectedResultsSummary& summary
	) {
	std::cout
			<< "{\"status\":\"expected-results-valid\",\"schema\":1"
			<< ",\"rays\":" << summary.rays
			<< ",\"segments\":" << summary.segments
			<< ",\"expected_results_sha256\":\""
			<< hex(summary.file_hash) << "\""
			<< ",\"bytes\":" << summary.file_bytes << "}\n";
}

void self_test() {
	const std::string_view abc = "abc";
	const auto bytes = std::span(
			reinterpret_cast<const std::uint8_t*>(abc.data()),
			abc.size()
	);
	if (hex(sha256(bytes))
			!= "ba7816bf8f01cfea414140de5dae2223"
					"b00361a396177a9cb410ff61f20015ad") {
		throw std::runtime_error("SHA-256 self-test failed");
	}
	if (hex(material_table_hash()) != kExpectedMaterialHash) {
		throw std::runtime_error("material table self-test failed");
	}
	std::cout << "{\"status\":\"self-test-passed\"}\n";
}

}  // namespace

/*
 * The standalone CPU/CUDA research executors include this translation unit as
 * an amalgamated, internal-only reader.  Keeping the strict parser and CPU
 * oracle in one implementation avoids a second production-bundle dialect.
 */
#ifndef MCFPV_DDA_BUNDLE_CORE_ONLY
int main(int argc, char** argv) {
	try {
		if (argc == 2 && std::string_view(argv[1]) == "--self-test") {
			self_test();
			return 0;
		}
		bool expect_fixture = false;
		bool require_complete = false;
		std::filesystem::path path;
		std::optional<std::filesystem::path> expected_results_path;
		for (int index = 1; index < argc; ++index) {
			const std::string_view argument = argv[index];
			if (argument == "--expect-fixture") {
				expect_fixture = true;
			} else if (argument == "--require-complete") {
				require_complete = true;
			} else if (argument == "--expected-results") {
				if (index + 1 >= argc
						|| expected_results_path.has_value()) {
					throw std::runtime_error(
							"--expected-results requires one path"
					);
				}
				expected_results_path = argv[++index];
			} else if (path.empty()) {
				path = argv[index];
			} else {
				throw std::runtime_error(
						"unexpected command-line argument"
				);
			}
		}
		if (path.empty()) {
			throw std::runtime_error(
					"usage: mcfpv_dda_bundle_verify "
					"[--expect-fixture] [--require-complete] "
					"[--expected-results <sidecar>] <bundle>"
			);
		}
		const Summary summary = verify(read_file(path));
		if (require_complete && !summary.complete) {
			throw std::runtime_error(
					"production snapshot is incomplete"
			);
		}
		if (expect_fixture) {
			require_fixture(summary);
		}
		print_summary(summary);
		if (expected_results_path.has_value()) {
			print_expected_summary(verify_expected_results(
					summary,
					read_file(expected_results_path.value())
			));
		}
		return 0;
	} catch (const std::exception& error) {
		std::cerr << "{\"status\":\"invalid\",\"error\":\""
				<< error.what() << "\"}\n";
		return 1;
	}
}
#endif
