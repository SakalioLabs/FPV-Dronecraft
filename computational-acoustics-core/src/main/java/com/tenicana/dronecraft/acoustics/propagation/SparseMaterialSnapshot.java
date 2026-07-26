package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticMaterial;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable sparse voxel material snapshot safe to read from a worker thread.
 */
public final class SparseMaterialSnapshot implements DirectPathSolver.MaterialQuery {
	private final Map<Long, DirectPathSolver.MaterialSample> samples;
	private final boolean complete;
	private volatile String diagnosticSha256;

	private SparseMaterialSnapshot(
			Map<Long, DirectPathSolver.MaterialSample> samples,
			boolean complete
	) {
		this.samples = Map.copyOf(samples);
		this.complete = complete;
	}

	@Override
	public DirectPathSolver.MaterialSample sampleAt(int x, int y, int z) {
		return samples.getOrDefault(pack(x, y, z), DirectPathSolver.MaterialSample.AIR);
	}

	public boolean complete() {
		return complete;
	}

	public int size() {
		return samples.size();
	}

	/**
	 * Returns a stable, allocation-heavy view for offline parity export. The
	 * product propagation path should continue to use {@link #sampleAt}.
	 */
	public List<CellSample> diagnosticEntries() {
		List<CellSample> entries = new ArrayList<>(samples.size());
		for (Map.Entry<Long, DirectPathSolver.MaterialSample> entry
				: samples.entrySet()) {
			entries.add(new CellSample(entry.getKey(), entry.getValue()));
		}
		entries.sort(
				(first, second) -> Long.compareUnsigned(
						first.packedCell(),
						second.packedCell()
				)
		);
		return List.copyOf(entries);
	}

	/**
	 * Lazily hashes the complete ordered snapshot contract for CUDA/shadow-mode
	 * diagnostics. Normal product reads do not trigger sorting or hashing.
	 */
	public String diagnosticSha256() {
		String existing = diagnosticSha256;
		if (existing != null) {
			return existing;
		}
		String computed = computeDiagnosticSha256();
		diagnosticSha256 = computed;
		return computed;
	}

	public static Builder builder() {
		return new Builder();
	}

	private static long pack(int x, int y, int z) {
		return ((long) x & 0x3ffffffL) << 38
				| ((long) z & 0x3ffffffL) << 12
				| (long) y & 0xfffL;
	}

	private String computeDiagnosticSha256() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (DataOutputStream output = new DataOutputStream(bytes)) {
				byte[] magic = "MCFPV-SparseMaterialSnapshot-v1"
						.getBytes(StandardCharsets.US_ASCII);
				output.writeInt(magic.length);
				output.write(magic);
				output.writeBoolean(complete);
				List<CellSample> ordered = diagnosticEntries();
				output.writeInt(ordered.size());
				for (CellSample cell : ordered) {
					output.writeLong(cell.packedCell());
					writeMaterialSample(output, cell.sample());
				}
			}
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256")
							.digest(bytes.toByteArray())
			);
		} catch (IOException error) {
			throw new IllegalStateException(
					"unexpected in-memory snapshot I/O failure",
					error
			);
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 is unavailable", error);
		}
	}

	private static void writeMaterialSample(
			DataOutputStream output,
			DirectPathSolver.MaterialSample sample
	) throws IOException {
		AcousticMaterial material = sample.material();
		byte[] id = material.id().getBytes(StandardCharsets.UTF_8);
		output.writeInt(id.length);
		output.write(id);
		writeBands(output, material.transmissionLossDbPerMeter());
		writeBands(output, material.surfaceAbsorption());
		output.writeDouble(material.scattering());
		output.writeDouble(sample.fillFraction());
	}

	private static void writeBands(
			DataOutputStream output,
			AcousticBands bands
	) throws IOException {
		output.writeDouble(bands.low());
		output.writeDouble(bands.mid());
		output.writeDouble(bands.high());
	}

	public record CellSample(
			long packedCell,
			DirectPathSolver.MaterialSample sample
	) {
		public CellSample {
			Objects.requireNonNull(sample, "sample");
		}

		public int x() {
			return (int) (packedCell >> 38);
		}

		public int y() {
			return (int) (packedCell << 52 >> 52);
		}

		public int z() {
			return (int) (packedCell << 26 >> 38);
		}
	}

	public static final class Builder {
		private final Map<Long, DirectPathSolver.MaterialSample> samples = new HashMap<>();
		private boolean complete = true;

		private Builder() {
		}

		public Builder put(int x, int y, int z, DirectPathSolver.MaterialSample sample) {
			samples.put(pack(x, y, z), Objects.requireNonNull(sample, "sample"));
			return this;
		}

		public Builder markIncomplete() {
			complete = false;
			return this;
		}

		public SparseMaterialSnapshot build() {
			return new SparseMaterialSnapshot(samples, complete);
		}
	}
}
