package com.tenicana.dronecraft.acoustics.voxel;

import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.propagation.DirectPathSolver;
import com.tenicana.dronecraft.acoustics.propagation.SparseMaterialSnapshot;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Language-neutral production snapshot and ray bundle for offline CPU/native
 * parity. This is deliberately not used by the product propagation hot path.
 * All numeric fields are big-endian.
 */
public final class DdaProductionSnapshotBundle {
	public static final int SCHEMA_VERSION = 1;
	private static final byte[] MAGIC =
			"MCFPDDA1".getBytes(StandardCharsets.US_ASCII);
	private static final int SHA256_BYTES = 32;
	private static final int MAXIMUM_STRING_BYTES = 1 << 20;
	private static final int MAXIMUM_CELLS = 16_777_216;
	private static final int MAXIMUM_RAYS = 1_000_000;

	private DdaProductionSnapshotBundle() {
	}

	public static void write(Path path, Bundle bundle) throws IOException {
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(bundle, "bundle");
		Path parent = path.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		try (DataOutputStream output = new DataOutputStream(
				new BufferedOutputStream(Files.newOutputStream(path))
		)) {
			output.write(MAGIC);
			output.writeInt(SCHEMA_VERSION);
			output.writeInt(bundle.metadata().mappingAlgorithmVersion());
			output.writeInt(AcousticMaterials.DIAGNOSTIC_SCHEMA_VERSION);
			output.write(HexFormat.of().parseHex(
					AcousticMaterials.diagnosticSha256()
			));
			writeString(output, bundle.metadata().minecraftVersion());
			writeString(output, bundle.metadata().modVersion());
			output.write(HexFormat.of().parseHex(
					bundle.metadata().contentFingerprintSha256()
			));
			output.writeLong(bundle.metadata().snapshotGeneration());
			SparseMaterialSnapshot snapshot = bundle.snapshot();
			output.writeBoolean(snapshot.complete());
			output.write(HexFormat.of().parseHex(snapshot.diagnosticSha256()));
			List<SparseMaterialSnapshot.CellSample> cells =
					snapshot.diagnosticEntries();
			output.writeInt(cells.size());
			for (SparseMaterialSnapshot.CellSample cell : cells) {
				output.writeLong(cell.packedCell());
				output.writeInt(AcousticMaterials.diagnosticMaterialId(
						cell.sample().material()
				));
				output.writeDouble(cell.sample().fillFraction());
			}
			output.writeInt(bundle.rays().size());
			for (Ray ray : bundle.rays()) {
				writeVector(output, ray.start());
				writeVector(output, ray.end());
				output.writeInt(ray.maximumCells());
			}
		}
	}

	public static void writeAtomic(Path path, Bundle bundle)
			throws IOException {
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(bundle, "bundle");
		Path absolute = path.toAbsolutePath();
		Path parent = absolute.getParent();
		if (parent == null) {
			throw new IOException("production bundle path has no parent");
		}
		Files.createDirectories(parent);
		Path temporary = Files.createTempFile(
				parent,
				absolute.getFileName().toString() + ".",
				".tmp"
		);
		try {
			write(temporary, bundle);
			try {
				Files.move(
						temporary,
						absolute,
						StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING
				);
			} catch (AtomicMoveNotSupportedException error) {
				Files.move(
						temporary,
						absolute,
						StandardCopyOption.REPLACE_EXISTING
				);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	public static Bundle read(Path path) throws IOException {
		Objects.requireNonNull(path, "path");
		try (DataInputStream input = new DataInputStream(
				new BufferedInputStream(Files.newInputStream(path))
		)) {
			requireBytes(input, MAGIC, "magic");
			int schema = input.readInt();
			if (schema != SCHEMA_VERSION) {
				throw new IOException("unsupported production bundle schema " + schema);
			}
			int mappingAlgorithmVersion = positive(
					input.readInt(),
					"mapping algorithm version"
			);
			int materialSchema = input.readInt();
			if (materialSchema != AcousticMaterials.DIAGNOSTIC_SCHEMA_VERSION) {
				throw new IOException(
						"unsupported material table schema " + materialSchema
				);
			}
			String materialHash = readHash(input);
			if (!materialHash.equals(AcousticMaterials.diagnosticSha256())) {
				throw new IOException("material table hash changed");
			}
			String minecraftVersion = readString(input);
			String modVersion = readString(input);
			String contentFingerprint = readHash(input);
			long generation = input.readLong();
			if (generation < 0L) {
				throw new IOException("snapshot generation must be non-negative");
			}
			boolean complete = input.readBoolean();
			String expectedSnapshotHash = readHash(input);
			int cellCount = boundedCount(
					input.readInt(),
					MAXIMUM_CELLS,
					"cell count"
			);
			SparseMaterialSnapshot.Builder snapshot =
					SparseMaterialSnapshot.builder();
			if (!complete) {
				snapshot.markIncomplete();
			}
			long previousPacked = 0L;
			for (int index = 0; index < cellCount; index++) {
				long packed = input.readLong();
				if (index > 0
						&& Long.compareUnsigned(previousPacked, packed) >= 0) {
					throw new IOException(
							"snapshot cells must be strictly ordered"
					);
				}
				previousPacked = packed;
				int materialId = boundedIndex(
						input.readInt(),
						AcousticMaterials.diagnosticTable().size(),
						"material id"
				);
				double fillFraction = input.readDouble();
				SparseMaterialSnapshot.CellSample coordinates =
						new SparseMaterialSnapshot.CellSample(
								packed,
								DirectPathSolver.MaterialSample.AIR
						);
				try {
					snapshot.put(
							coordinates.x(),
							coordinates.y(),
							coordinates.z(),
							new DirectPathSolver.MaterialSample(
									AcousticMaterials.diagnosticTable()
											.get(materialId),
									fillFraction
							)
					);
				} catch (IllegalArgumentException error) {
					throw new IOException("invalid material sample", error);
				}
			}
			SparseMaterialSnapshot builtSnapshot = snapshot.build();
			if (!builtSnapshot.diagnosticSha256().equals(expectedSnapshotHash)) {
				throw new IOException("snapshot hash mismatch");
			}
			int rayCount = boundedCount(
					input.readInt(),
					MAXIMUM_RAYS,
					"ray count"
			);
			List<Ray> rays = new ArrayList<>(rayCount);
			for (int index = 0; index < rayCount; index++) {
				try {
					rays.add(new Ray(
							readVector(input),
							readVector(input),
							positive(input.readInt(), "maximum cells")
					));
				} catch (IllegalArgumentException error) {
					throw new IOException("invalid ray " + index, error);
				}
			}
			if (input.read() != -1) {
				throw new IOException("trailing bytes after production bundle");
			}
			return new Bundle(
					new Metadata(
							mappingAlgorithmVersion,
							minecraftVersion,
							modVersion,
							contentFingerprint,
							generation
					),
					builtSnapshot,
					rays
			);
		} catch (EOFException error) {
			throw new IOException("truncated production bundle", error);
		} catch (IllegalArgumentException error) {
			throw new IOException(
					"invalid production bundle: " + error.getMessage(),
					error
			);
		}
	}

	private static void writeVector(
			DataOutputStream output,
			AcousticVector vector
	) throws IOException {
		output.writeDouble(vector.x());
		output.writeDouble(vector.y());
		output.writeDouble(vector.z());
	}

	private static AcousticVector readVector(DataInputStream input)
			throws IOException {
		return new AcousticVector(
				input.readDouble(),
				input.readDouble(),
				input.readDouble()
		);
	}

	private static void writeString(DataOutputStream output, String value)
			throws IOException {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		if (bytes.length > MAXIMUM_STRING_BYTES) {
			throw new IllegalArgumentException("metadata string is too long");
		}
		output.writeInt(bytes.length);
		output.write(bytes);
	}

	private static String readString(DataInputStream input) throws IOException {
		int length = boundedIndex(
				input.readInt(),
				MAXIMUM_STRING_BYTES + 1,
				"string byte count"
		);
		byte[] bytes = input.readNBytes(length);
		if (bytes.length != length) {
			throw new EOFException();
		}
		try {
			return StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(bytes))
					.toString();
		} catch (CharacterCodingException error) {
			throw new IOException("metadata string is not valid UTF-8", error);
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
			throw new IOException("invalid production bundle " + name);
		}
	}

	private static int positive(int value, String name) throws IOException {
		if (value < 1) {
			throw new IOException(name + " must be positive");
		}
		return value;
	}

	private static int boundedCount(int value, int maximum, String name)
			throws IOException {
		if (value < 1 || value > maximum) {
			throw new IOException(name + " is out of range");
		}
		return value;
	}

	private static int boundedIndex(int value, int bound, String name)
			throws IOException {
		if (value < 0 || value >= bound) {
			throw new IOException(name + " is out of range");
		}
		return value;
	}

	public record Metadata(
			int mappingAlgorithmVersion,
			String minecraftVersion,
			String modVersion,
			String contentFingerprintSha256,
			long snapshotGeneration
	) {
		public Metadata {
			if (mappingAlgorithmVersion < 1) {
				throw new IllegalArgumentException(
						"mappingAlgorithmVersion must be positive"
				);
			}
			if (Objects.requireNonNull(
					minecraftVersion,
					"minecraftVersion"
			).isBlank()) {
				throw new IllegalArgumentException(
						"minecraftVersion must not be blank"
				);
			}
			if (Objects.requireNonNull(modVersion, "modVersion").isBlank()) {
				throw new IllegalArgumentException("modVersion must not be blank");
			}
			contentFingerprintSha256 = requireSha256(
					contentFingerprintSha256,
					"contentFingerprintSha256"
			);
			if (snapshotGeneration < 0L) {
				throw new IllegalArgumentException(
						"snapshotGeneration must be non-negative"
				);
			}
		}
	}

	public record Ray(
			AcousticVector start,
			AcousticVector end,
			int maximumCells
	) {
		public Ray {
			Objects.requireNonNull(start, "start");
			Objects.requireNonNull(end, "end");
			if (maximumCells < 1) {
				throw new IllegalArgumentException(
						"maximumCells must be positive"
				);
			}
		}
	}

	public record Bundle(
			Metadata metadata,
			SparseMaterialSnapshot snapshot,
			List<Ray> rays
	) {
		public Bundle {
			Objects.requireNonNull(metadata, "metadata");
			Objects.requireNonNull(snapshot, "snapshot");
			rays = List.copyOf(Objects.requireNonNull(rays, "rays"));
			if (snapshot.size() < 1 || snapshot.size() > MAXIMUM_CELLS) {
				throw new IllegalArgumentException(
						"snapshot cell count is out of range"
				);
			}
			if (rays.isEmpty() || rays.size() > MAXIMUM_RAYS) {
				throw new IllegalArgumentException(
						"ray count is out of range"
				);
			}
		}
	}

	private static String requireSha256(String value, String name) {
		String normalized = Objects.requireNonNull(value, name)
				.toLowerCase(Locale.ROOT);
		if (!normalized.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException(name + " must be a SHA-256 hex");
		}
		return normalized;
	}
}
