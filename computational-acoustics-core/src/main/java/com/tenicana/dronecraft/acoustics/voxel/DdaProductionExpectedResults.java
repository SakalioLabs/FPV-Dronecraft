package com.tenicana.dronecraft.acoustics.voxel;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticMaterial;
import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.propagation.DirectPathSolver;
import com.tenicana.dronecraft.acoustics.propagation.SparseMaterialSnapshot;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Java CPU expected-results sidecar for a schema-v1 production snapshot
 * bundle. The input bundle remains immutable; this file is keyed by its hash.
 */
public final class DdaProductionExpectedResults {
	public static final int SCHEMA_VERSION = 1;
	private static final byte[] MAGIC =
			"MCFPREF1".getBytes(StandardCharsets.US_ASCII);
	private static final int SHA256_BYTES = 32;
	private static final int FLAG_REACHED_END = 1;
	private static final int FLAG_STOPPED_EARLY = 2;
	private static final int FLAG_TRUNCATED = 4;

	private DdaProductionExpectedResults() {
	}

	public static void writeGenerated(
			Path bundlePath,
			Path outputPath
	) throws IOException {
		Path absoluteBundle = Objects.requireNonNull(
				bundlePath,
				"bundlePath"
		).toAbsolutePath().normalize();
		Path absoluteOutput = Objects.requireNonNull(
				outputPath,
				"outputPath"
		).toAbsolutePath().normalize();
		if (absoluteBundle.equals(absoluteOutput)) {
			throw new IllegalArgumentException(
					"expected-results path must differ from bundle path"
			);
		}
		DdaProductionSnapshotBundle.Bundle bundle =
				DdaProductionSnapshotBundle.read(absoluteBundle);
		Path parent = absoluteOutput.getParent();
		if (parent == null) {
			throw new IOException("expected-results path has no parent");
		}
		Files.createDirectories(parent);
		Path temporary = Files.createTempFile(
				parent,
				absoluteOutput.getFileName().toString() + ".",
				".tmp"
		);
		try {
			writeTo(
					temporary,
					bundle,
					fileSha256(absoluteBundle)
			);
			try {
				Files.move(
						temporary,
						absoluteOutput,
						StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING
				);
			} catch (AtomicMoveNotSupportedException error) {
				Files.move(
						temporary,
						absoluteOutput,
						StandardCopyOption.REPLACE_EXISTING
				);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	public static VerificationSummary verifyStreaming(
			Path bundlePath,
			Path expectedResultsPath
	) throws IOException {
		Path absoluteBundle = Objects.requireNonNull(
				bundlePath,
				"bundlePath"
		).toAbsolutePath().normalize();
		DdaProductionSnapshotBundle.Bundle bundle =
				DdaProductionSnapshotBundle.read(absoluteBundle);
		try (DataInputStream input = new DataInputStream(
				new BufferedInputStream(Files.newInputStream(
						Objects.requireNonNull(
								expectedResultsPath,
								"expectedResultsPath"
						)
				))
		)) {
			requireBytes(input, MAGIC, "magic");
			int schema = input.readInt();
			if (schema != SCHEMA_VERSION) {
				throw new IOException(
						"unsupported expected-results schema " + schema
				);
			}
			String storedBundleHash = readHash(input);
			String actualBundleHash = fileSha256(absoluteBundle);
			if (!storedBundleHash.equals(actualBundleHash)) {
				throw new IOException("input bundle hash mismatch");
			}
			String storedSnapshotHash = readHash(input);
			if (!storedSnapshotHash.equals(
					bundle.snapshot().diagnosticSha256()
			)) {
				throw new IOException("snapshot hash mismatch");
			}
			int rayCount = positive(input.readInt(), "ray count");
			if (rayCount != bundle.rays().size()) {
				throw new IOException("ray count does not match bundle");
			}
			long segmentCount = 0L;
			for (int rayId = 0; rayId < rayCount; rayId++) {
				ExpectedRay stored = readRay(
						input,
						bundle.rays().get(rayId).maximumCells()
				);
				ExpectedRay actual = generateRay(bundle, rayId);
				if (!stored.equals(actual)) {
					throw new IOException(
							"expected result mismatch for ray " + rayId
					);
				}
				segmentCount += stored.segments().size();
			}
			if (input.read() != -1) {
				throw new IOException(
						"trailing bytes after expected results"
				);
			}
			return new VerificationSummary(
					rayCount,
					segmentCount,
					storedBundleHash,
					storedSnapshotHash
			);
		} catch (EOFException error) {
			throw new IOException("truncated expected results", error);
		} catch (IllegalArgumentException error) {
			throw new IOException(
					"invalid expected results: " + error.getMessage(),
					error
			);
		}
	}

	private static void writeTo(
			Path path,
			DdaProductionSnapshotBundle.Bundle bundle,
			String bundleHash
	) throws IOException {
		try (DataOutputStream output = new DataOutputStream(
				new BufferedOutputStream(Files.newOutputStream(path))
		)) {
			output.write(MAGIC);
			output.writeInt(SCHEMA_VERSION);
			output.write(HexFormat.of().parseHex(bundleHash));
			output.write(HexFormat.of().parseHex(
					bundle.snapshot().diagnosticSha256()
			));
			output.writeInt(bundle.rays().size());
			for (int rayId = 0; rayId < bundle.rays().size(); rayId++) {
				writeRay(output, generateRay(bundle, rayId));
			}
		}
	}

	private static ExpectedRay generateRay(
			DdaProductionSnapshotBundle.Bundle bundle,
			int rayId
	) {
		DdaProductionSnapshotBundle.Ray ray = bundle.rays().get(rayId);
		List<ExpectedSegment> segments = new ArrayList<>();
		AcousticBands[] loss = {AcousticBands.SILENT};
		int[] materialCellCount = {0};
		long[] firstMaterialCell = {0L};
		boolean[] hasFirstMaterialCell = {false};
		VoxelDda.WalkResult walk = VoxelDda.walk(
				ray.start(),
				ray.end(),
				(x, y, z, lengthMeters) -> {
					DirectPathSolver.MaterialSample sample =
							bundle.snapshot().sampleAt(x, y, z);
					AcousticMaterial material = sample.material();
					int materialId =
							AcousticMaterials.diagnosticMaterialId(material);
					double effectiveLength =
							lengthMeters * sample.fillFraction();
					loss[0] = loss[0].add(
							material.transmissionLossDbPerMeter()
									.multiply(effectiveLength)
					);
					if (materialId != 0 && effectiveLength > 0.0) {
						materialCellCount[0]++;
						if (!hasFirstMaterialCell[0]) {
							firstMaterialCell[0] = pack(x, y, z);
							hasFirstMaterialCell[0] = true;
						}
					}
					segments.add(new ExpectedSegment(
							pack(x, y, z),
							lengthMeters,
							materialId,
							sample.fillFraction()
					));
					return true;
				},
				ray.maximumCells()
		);
		AcousticBands gain = loss[0].map(
				value -> Math.pow(10.0, -value / 10.0)
		);
		ExpectedRay expected = new ExpectedRay(
				rayId,
				segments,
				walk.visitedCellCount(),
				materialCellCount[0],
				walk.reachedEnd(),
				walk.stoppedEarly(),
				!walk.reachedEnd() && !walk.stoppedEarly(),
				loss[0],
				gain,
				hasFirstMaterialCell[0]
						? OptionalLong.of(firstMaterialCell[0])
						: OptionalLong.empty()
		);
		verifyDirectPath(bundle.snapshot(), ray, expected);
		return expected;
	}

	private static void verifyDirectPath(
			SparseMaterialSnapshot snapshot,
			DdaProductionSnapshotBundle.Ray ray,
			ExpectedRay expected
	) {
		DirectPathSolver.Result direct = DirectPathSolver.solve(
				ray.start(),
				ray.end(),
				snapshot,
				ray.maximumCells()
		);
		if (!direct.transmissionLossDb().equals(expected.lossDb())
				|| !direct.transmissionEnergyGain().equals(
						expected.energyGain()
				)
				|| direct.visitedCellCount() != expected.visitedCellCount()
				|| direct.materialCellCount()
						!= expected.materialCellCount()
				|| direct.complete() != expected.reachedEnd()) {
			throw new IllegalStateException(
					"expected-results trace disagrees with DirectPathSolver"
			);
		}
	}

	private static void writeRay(
			DataOutputStream output,
			ExpectedRay ray
	) throws IOException {
		output.writeInt(ray.rayId());
		output.writeInt(ray.segments().size());
		output.writeInt(ray.visitedCellCount());
		output.writeInt(ray.materialCellCount());
		int flags = ray.reachedEnd() ? FLAG_REACHED_END : 0;
		flags |= ray.stoppedEarly() ? FLAG_STOPPED_EARLY : 0;
		flags |= ray.truncated() ? FLAG_TRUNCATED : 0;
		output.writeByte(flags);
		writeBands(output, ray.lossDb());
		writeBands(output, ray.energyGain());
		output.writeBoolean(ray.firstMaterialCell().isPresent());
		if (ray.firstMaterialCell().isPresent()) {
			output.writeLong(ray.firstMaterialCell().orElseThrow());
		}
		for (ExpectedSegment segment : ray.segments()) {
			output.writeLong(segment.packedCell());
			output.writeDouble(segment.lengthMeters());
			output.writeInt(segment.materialId());
			output.writeDouble(segment.fillFraction());
		}
	}

	private static ExpectedRay readRay(
			DataInputStream input,
			int maximumCells
	) throws IOException {
		int rayId = input.readInt();
		int segmentCount = boundedCount(
				input.readInt(),
				maximumCells,
				"segment count"
		);
		int visitedCellCount = boundedCount(
				input.readInt(),
				maximumCells,
				"visited cell count"
		);
		int materialCellCount = nonNegative(
				input.readInt(),
				"material cell count"
		);
		if (materialCellCount > visitedCellCount) {
			throw new IOException(
					"material cell count exceeds visited cells"
			);
		}
		int flags = input.readUnsignedByte();
		if ((flags & ~(FLAG_REACHED_END
				| FLAG_STOPPED_EARLY
				| FLAG_TRUNCATED)) != 0) {
			throw new IOException("unknown expected-result flags");
		}
		AcousticBands loss = readBands(input);
		AcousticBands gain = readBands(input);
		OptionalLong firstMaterial = input.readBoolean()
				? OptionalLong.of(input.readLong())
				: OptionalLong.empty();
		List<ExpectedSegment> segments = new ArrayList<>(segmentCount);
		for (int index = 0; index < segmentCount; index++) {
			segments.add(new ExpectedSegment(
					input.readLong(),
					input.readDouble(),
					input.readInt(),
					input.readDouble()
			));
		}
		return new ExpectedRay(
				rayId,
				segments,
				visitedCellCount,
				materialCellCount,
				(flags & FLAG_REACHED_END) != 0,
				(flags & FLAG_STOPPED_EARLY) != 0,
				(flags & FLAG_TRUNCATED) != 0,
				loss,
				gain,
				firstMaterial
		);
	}

	private static void writeBands(
			DataOutputStream output,
			AcousticBands bands
	) throws IOException {
		output.writeDouble(bands.low());
		output.writeDouble(bands.mid());
		output.writeDouble(bands.high());
	}

	private static AcousticBands readBands(DataInputStream input)
			throws IOException {
		return new AcousticBands(
				input.readDouble(),
				input.readDouble(),
				input.readDouble()
		);
	}

	private static String fileSha256(Path path) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (DigestInputStream input = new DigestInputStream(
					new BufferedInputStream(Files.newInputStream(path)),
					digest
			)) {
				input.transferTo(java.io.OutputStream.nullOutputStream());
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 is unavailable", error);
		}
	}

	private static String readHash(DataInputStream input) throws IOException {
		byte[] bytes = input.readNBytes(SHA256_BYTES);
		if (bytes.length != SHA256_BYTES) {
			throw new EOFException();
		}
		return HexFormat.of().formatHex(bytes);
	}

	private static void requireBytes(
			DataInputStream input,
			byte[] expected,
			String name
	) throws IOException {
		byte[] actual = input.readNBytes(expected.length);
		if (!java.util.Arrays.equals(actual, expected)) {
			throw new IOException("invalid expected-results " + name);
		}
	}

	private static int positive(int value, String name) throws IOException {
		if (value < 1) {
			throw new IOException(name + " must be positive");
		}
		return value;
	}

	private static int nonNegative(int value, String name)
			throws IOException {
		if (value < 0) {
			throw new IOException(name + " must be non-negative");
		}
		return value;
	}

	private static int boundedCount(
			int value,
			int maximum,
			String name
	) throws IOException {
		if (value < 1 || value > maximum) {
			throw new IOException(name + " is out of range");
		}
		return value;
	}

	private static long pack(int x, int y, int z) {
		return ((long) x & 0x3ffffffL) << 38
				| ((long) z & 0x3ffffffL) << 12
				| (long) y & 0xfffL;
	}

	public record VerificationSummary(
			int rays,
			long segments,
			String bundleSha256,
			String snapshotSha256
	) {
	}

	private record ExpectedSegment(
			long packedCell,
			double lengthMeters,
			int materialId,
			double fillFraction
	) {
		private ExpectedSegment {
			if (!Double.isFinite(lengthMeters) || lengthMeters < 0.0) {
				throw new IllegalArgumentException(
						"segment length must be finite and non-negative"
				);
			}
			if (materialId < 0
					|| materialId
							>= AcousticMaterials.diagnosticTable().size()) {
				throw new IllegalArgumentException(
						"material id is out of range"
				);
			}
			if (!Double.isFinite(fillFraction)
					|| fillFraction < 0.0
					|| fillFraction > 1.0) {
				throw new IllegalArgumentException(
						"fill fraction is out of range"
				);
			}
		}
	}

	private record ExpectedRay(
			int rayId,
			List<ExpectedSegment> segments,
			int visitedCellCount,
			int materialCellCount,
			boolean reachedEnd,
			boolean stoppedEarly,
			boolean truncated,
			AcousticBands lossDb,
			AcousticBands energyGain,
			OptionalLong firstMaterialCell
	) {
		private ExpectedRay {
			if (rayId < 0) {
				throw new IllegalArgumentException(
						"ray id must be non-negative"
				);
			}
			segments = List.copyOf(segments);
			if (visitedCellCount != segments.size()) {
				throw new IllegalArgumentException(
						"visited cell count must equal segment count"
				);
			}
			if (materialCellCount < 0
					|| materialCellCount > visitedCellCount) {
				throw new IllegalArgumentException(
						"material cell count is out of range"
				);
			}
			if (truncated == (reachedEnd || stoppedEarly)) {
				throw new IllegalArgumentException(
						"truncated must be inverse of terminal flags"
				);
			}
			Objects.requireNonNull(lossDb, "lossDb");
			Objects.requireNonNull(energyGain, "energyGain");
			Objects.requireNonNull(firstMaterialCell, "firstMaterialCell");
		}
	}
}
