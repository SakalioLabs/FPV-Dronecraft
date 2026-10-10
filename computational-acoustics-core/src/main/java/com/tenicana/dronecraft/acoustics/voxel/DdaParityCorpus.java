package com.tenicana.dronecraft.acoustics.voxel;

import com.tenicana.dronecraft.acoustics.AcousticVector;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Versioned, language-neutral CPU reference corpus for accelerated voxel DDA
 * parity tests. All integer and floating-point fields are big-endian.
 */
public final class DdaParityCorpus {
	public static final int SCHEMA_VERSION = 1;
	public static final int MATERIAL_ALGORITHM_ID = 1;
	public static final int TIE_POLICY_ID = 1;
	public static final int DEFAULT_MAXIMUM_CELLS = 192;
	private static final byte[] MAGIC = "MCFDDAC1".getBytes(StandardCharsets.US_ASCII);
	private static final int MAXIMUM_RAYS = 1_000_000;
	private static final int MAXIMUM_SEGMENTS_PER_RAY = 1_000_000;
	private static final int FLAG_REACHED_END = 1;
	private static final int FLAG_STOPPED_EARLY = 2;
	private static final int FLAG_TRUNCATED = 4;
	private static final int MATERIAL_COUNT = 9;
	private static final int SHA256_BYTES = 32;

	private DdaParityCorpus() {
	}

	public static Corpus generate(
			int randomRayCount,
			long raySeed,
			long materialSeed
	) {
		if (randomRayCount < 0 || randomRayCount > MAXIMUM_RAYS - 8) {
			throw new IllegalArgumentException("randomRayCount is out of range");
		}
		List<RayInput> inputs = new ArrayList<>();
		addAdversarialInputs(inputs);
		SplitMix64 random = new SplitMix64(raySeed);
		for (int index = 0; index < randomRayCount; index++) {
			inputs.add(new RayInput(
					randomPoint(random),
					randomPoint(random),
					DEFAULT_MAXIMUM_CELLS
			));
		}
		List<RayRecord> rays = new ArrayList<>(inputs.size());
		for (int rayId = 0; rayId < inputs.size(); rayId++) {
			RayInput input = inputs.get(rayId);
			DdaParityOracle.RayTrace expected = trace(
					input,
					materialSeed
			);
			rays.add(new RayRecord(
					rayId,
					input,
					expected,
					accumulateBands(expected)
			));
		}
		return new Corpus(
				raySeed,
				materialSeed,
				materialTableSha256(),
				MATERIAL_ALGORITHM_ID,
				TIE_POLICY_ID,
				rays
		);
	}

	public static void verify(Corpus corpus) {
		Objects.requireNonNull(corpus, "corpus");
		if (corpus.materialAlgorithmId() != MATERIAL_ALGORITHM_ID) {
			throw new IllegalArgumentException(
					"unsupported material algorithm id"
			);
		}
		if (corpus.tiePolicyId() != TIE_POLICY_ID) {
			throw new IllegalArgumentException("unsupported tie policy id");
		}
		if (!corpus.materialTableSha256().equals(materialTableSha256())) {
			throw new IllegalArgumentException("material table hash changed");
		}
		for (RayRecord ray : corpus.rays()) {
			DdaParityOracle.RayTrace actual = trace(
					ray.input(),
					corpus.materialSeed()
			);
			if (!actual.equals(ray.expected())) {
				throw new IllegalArgumentException(
						"CPU parity mismatch for ray " + ray.rayId()
				);
			}
			BandResult actualBands = accumulateBands(actual);
			if (!actualBands.equals(ray.expectedBands())) {
				throw new IllegalArgumentException(
						"band accumulation mismatch for ray " + ray.rayId()
				);
			}
		}
	}

	public static void write(Path path, Corpus corpus) throws IOException {
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(corpus, "corpus");
		verify(corpus);
		Path parent = path.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		try (DataOutputStream output = new DataOutputStream(
				new BufferedOutputStream(Files.newOutputStream(path))
		)) {
			writeHeader(
					output,
					corpus.raySeed(),
					corpus.materialSeed(),
					corpus.materialTableSha256(),
					corpus.materialAlgorithmId(),
					corpus.tiePolicyId(),
					corpus.rays().size()
			);
			for (RayRecord ray : corpus.rays()) {
				writeRay(output, ray);
			}
		}
	}

	public static void writeGenerated(
			Path path,
			int randomRayCount,
			long raySeed,
			long materialSeed
	) throws IOException {
		if (randomRayCount < 0 || randomRayCount > MAXIMUM_RAYS - 8) {
			throw new IllegalArgumentException(
					"randomRayCount is out of range"
			);
		}
		Objects.requireNonNull(path, "path");
		Path parent = path.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		try (DataOutputStream output = new DataOutputStream(
				new BufferedOutputStream(Files.newOutputStream(path))
		)) {
			writeHeader(
					output,
					raySeed,
					materialSeed,
					materialTableSha256(),
					MATERIAL_ALGORITHM_ID,
					TIE_POLICY_ID,
					8 + randomRayCount
			);
			List<RayInput> adversarial = new ArrayList<>();
			addAdversarialInputs(adversarial);
			int rayId = 0;
			for (RayInput input : adversarial) {
				writeRay(
						output,
						record(rayId++, input, materialSeed)
				);
			}
			SplitMix64 random = new SplitMix64(raySeed);
			for (int index = 0; index < randomRayCount; index++) {
				RayInput input = new RayInput(
						randomPoint(random),
						randomPoint(random),
						DEFAULT_MAXIMUM_CELLS
				);
				writeRay(
						output,
						record(rayId++, input, materialSeed)
				);
			}
		}
	}

	public static Corpus read(Path path) throws IOException {
		Objects.requireNonNull(path, "path");
		try (DataInputStream input = new DataInputStream(
				new BufferedInputStream(Files.newInputStream(path))
		)) {
			Header header = readHeader(input);
			List<RayRecord> rays = new ArrayList<>(header.rayCount());
			for (int index = 0; index < header.rayCount(); index++) {
				rays.add(readRay(input));
			}
			if (input.read() != -1) {
				throw new IOException("trailing bytes after DDA corpus");
			}
			Corpus corpus = new Corpus(
					header.raySeed(),
					header.materialSeed(),
					header.materialTableSha256(),
					header.materialAlgorithmId(),
					header.tiePolicyId(),
					rays
			);
			try {
				verify(corpus);
			} catch (IllegalArgumentException error) {
				throw new IOException(error.getMessage(), error);
			}
			return corpus;
		} catch (EOFException error) {
			throw new IOException("truncated DDA corpus", error);
		} catch (IllegalArgumentException error) {
			throw new IOException("invalid DDA corpus: " + error.getMessage(), error);
		}
	}

	public static StreamingSummary verifyStreaming(Path path)
			throws IOException {
		Objects.requireNonNull(path, "path");
		try (DataInputStream input = new DataInputStream(
				new BufferedInputStream(Files.newInputStream(path))
		)) {
			Header header = readHeader(input);
			validateHeader(header);
			long segments = 0L;
			for (int index = 0; index < header.rayCount(); index++) {
				RayRecord ray = readRay(input);
				if (ray.rayId() != index) {
					throw new IOException(
							"ray ids must be contiguous"
					);
				}
				DdaParityOracle.RayTrace expected = trace(
						ray.input(),
						header.materialSeed()
				);
				if (!expected.equals(ray.expected())) {
					throw new IOException(
							"CPU parity mismatch for ray " + index
					);
				}
				if (!accumulateBands(expected).equals(ray.expectedBands())) {
					throw new IOException(
							"band accumulation mismatch for ray " + index
					);
				}
				segments += expected.segments().size();
			}
			if (input.read() != -1) {
				throw new IOException(
						"trailing bytes after DDA corpus"
				);
			}
			return new StreamingSummary(
					header.rayCount(),
					segments,
					header.raySeed(),
					header.materialSeed(),
					header.materialTableSha256()
			);
		} catch (EOFException error) {
			throw new IOException("truncated DDA corpus", error);
		} catch (IllegalArgumentException error) {
			throw new IOException(
					"invalid DDA corpus: " + error.getMessage(),
					error
			);
		}
	}

	public static int materialIdAt(
			int x,
			int y,
			int z,
			long materialSeed
	) {
		long value = materialSeed;
		value ^= Integer.toUnsignedLong(x) * 0x9E3779B185EBCA87L;
		value ^= Integer.toUnsignedLong(y) * 0xC2B2AE3D27D4EB4FL;
		value ^= Integer.toUnsignedLong(z) * 0x165667B19E3779F9L;
		long mixed = mix64(value);
		return (mixed & 31L) == 0L
				? 1 + (int) ((mixed >>> 5) & 7L)
				: DdaParityOracle.AIR_MATERIAL_ID;
	}

	public static String materialTableSha256() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (DataOutputStream output = new DataOutputStream(bytes)) {
				for (int materialId = 0; materialId < MATERIAL_COUNT; materialId++) {
					output.writeInt(materialId);
					output.writeDouble(lossPerMeter(materialId, 0));
					output.writeDouble(lossPerMeter(materialId, 1));
					output.writeDouble(lossPerMeter(materialId, 2));
				}
			}
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256")
							.digest(bytes.toByteArray())
			);
		} catch (IOException error) {
			throw new IllegalStateException(
					"unexpected in-memory material table I/O failure",
					error
			);
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 is unavailable", error);
		}
	}

	private static DdaParityOracle.RayTrace trace(
			RayInput input,
			long materialSeed
	) {
		return DdaParityOracle.trace(
				input.start(),
				input.end(),
				(x, y, z) -> materialIdAt(x, y, z, materialSeed),
				input.maximumCells()
		);
	}

	private static RayRecord record(
			int rayId,
			RayInput input,
			long materialSeed
	) {
		DdaParityOracle.RayTrace expected = trace(input, materialSeed);
		return new RayRecord(
				rayId,
				input,
				expected,
				accumulateBands(expected)
		);
	}

	private static void addAdversarialInputs(List<RayInput> inputs) {
		inputs.add(new RayInput(
				new AcousticVector(0.5, 0.5, 0.5),
				new AcousticVector(0.5, 0.5, 0.5),
				DEFAULT_MAXIMUM_CELLS
		));
		inputs.add(new RayInput(
				new AcousticVector(0.25, 0.5, 0.5),
				new AcousticVector(63.75, 0.5, 0.5),
				DEFAULT_MAXIMUM_CELLS
		));
		inputs.add(new RayInput(
				new AcousticVector(-0.25, -0.5, -0.5),
				new AcousticVector(-63.75, -0.5, -0.5),
				DEFAULT_MAXIMUM_CELLS
		));
		inputs.add(new RayInput(
				new AcousticVector(0.5, 0.5, 0.5),
				new AcousticVector(32.5, 32.5, 32.5),
				DEFAULT_MAXIMUM_CELLS
		));
		inputs.add(new RayInput(
				new AcousticVector(0.5, 0.5, 0.25),
				new AcousticVector(32.5, 32.5, 0.25),
				DEFAULT_MAXIMUM_CELLS
		));
		inputs.add(new RayInput(
				new AcousticVector(-2.0, 3.0, -4.0),
				new AcousticVector(2.0, -3.0, 4.0),
				DEFAULT_MAXIMUM_CELLS
		));
		inputs.add(new RayInput(
				new AcousticVector(0.1, 0.1, 0.1),
				new AcousticVector(512.1, 0.1, 0.1),
				16
		));
		inputs.add(new RayInput(
				new AcousticVector(1.0, 1.0, 1.0),
				new AcousticVector(-1.0, -1.0, -1.0),
				DEFAULT_MAXIMUM_CELLS
		));
	}

	private static AcousticVector randomPoint(SplitMix64 random) {
		return new AcousticVector(
				randomCoordinate(random),
				randomCoordinate(random),
				randomCoordinate(random)
		);
	}

	private static double randomCoordinate(SplitMix64 random) {
		int cell = (int) Long.remainderUnsigned(random.nextLong(), 129L) - 64;
		int fraction = 1
				+ (int) Long.remainderUnsigned(random.nextLong(), 1022L);
		return cell + fraction / 1024.0;
	}

	private static void writeRay(
			DataOutputStream output,
			RayRecord ray
	) throws IOException {
		output.writeInt(ray.rayId());
		writeVector(output, ray.input().start());
		writeVector(output, ray.input().end());
		output.writeInt(ray.input().maximumCells());
		DdaParityOracle.RayTrace expected = ray.expected();
		output.writeInt(expected.segments().size());
		output.writeInt(expected.visitedCellCount());
		int flags = expected.reachedEnd() ? FLAG_REACHED_END : 0;
		flags |= expected.stoppedEarly() ? FLAG_STOPPED_EARLY : 0;
		flags |= expected.truncated() ? FLAG_TRUNCATED : 0;
		output.writeByte(flags);
		BandResult bands = ray.expectedBands();
		output.writeDouble(bands.lowLossDb());
		output.writeDouble(bands.midLossDb());
		output.writeDouble(bands.highLossDb());
		output.writeDouble(bands.lowEnergyGain());
		output.writeDouble(bands.midEnergyGain());
		output.writeDouble(bands.highEnergyGain());
		output.writeBoolean(expected.firstMaterialCell().isPresent());
		if (expected.firstMaterialCell().isPresent()) {
			writeCell(output, expected.firstMaterialCell().orElseThrow());
		}
		for (DdaParityOracle.Segment segment : expected.segments()) {
			writeCell(output, segment.cell());
			output.writeDouble(segment.lengthMeters());
			output.writeInt(segment.materialId());
		}
	}

	private static void writeHeader(
			DataOutputStream output,
			long raySeed,
			long materialSeed,
			String materialTableHash,
			int materialAlgorithmId,
			int tiePolicyId,
			int rayCount
	) throws IOException {
		if (rayCount < 1 || rayCount > MAXIMUM_RAYS) {
			throw new IllegalArgumentException(
					"ray count is out of range"
			);
		}
		output.write(MAGIC);
		output.writeInt(SCHEMA_VERSION);
		output.writeInt(materialAlgorithmId);
		output.writeInt(tiePolicyId);
		output.writeLong(raySeed);
		output.writeLong(materialSeed);
		output.write(HexFormat.of().parseHex(materialTableHash));
		output.writeInt(rayCount);
	}

	private static Header readHeader(DataInputStream input) throws IOException {
		byte[] magic = input.readNBytes(MAGIC.length);
		if (!java.util.Arrays.equals(magic, MAGIC)) {
			throw new IOException("invalid DDA corpus magic");
		}
		int schemaVersion = input.readInt();
		if (schemaVersion != SCHEMA_VERSION) {
			throw new IOException(
					"unsupported DDA corpus schema " + schemaVersion
			);
		}
		int materialAlgorithmId = input.readInt();
		int tiePolicyId = input.readInt();
		long raySeed = input.readLong();
		long materialSeed = input.readLong();
		byte[] materialTableHash = input.readNBytes(SHA256_BYTES);
		if (materialTableHash.length != SHA256_BYTES) {
			throw new EOFException();
		}
		int rayCount = boundedCount(
				input.readInt(),
				MAXIMUM_RAYS,
				"ray count"
		);
		return new Header(
				raySeed,
				materialSeed,
				HexFormat.of().formatHex(materialTableHash),
				materialAlgorithmId,
				tiePolicyId,
				rayCount
		);
	}

	private static void validateHeader(Header header) throws IOException {
		if (header.materialAlgorithmId() != MATERIAL_ALGORITHM_ID) {
			throw new IOException(
					"unsupported material algorithm id"
			);
		}
		if (header.tiePolicyId() != TIE_POLICY_ID) {
			throw new IOException("unsupported tie policy id");
		}
		if (!header.materialTableSha256().equals(materialTableSha256())) {
			throw new IOException("material table hash changed");
		}
	}

	private static RayRecord readRay(DataInputStream input) throws IOException {
		int rayId = input.readInt();
		AcousticVector start = readVector(input);
		AcousticVector end = readVector(input);
		int maximumCells = boundedCount(
				input.readInt(),
				MAXIMUM_SEGMENTS_PER_RAY,
				"maximum cells"
		);
		int segmentCount = boundedCount(
				input.readInt(),
				Math.min(maximumCells, MAXIMUM_SEGMENTS_PER_RAY),
				"segment count"
		);
		int visitedCellCount = boundedCount(
				input.readInt(),
				MAXIMUM_SEGMENTS_PER_RAY,
				"visited cell count"
		);
		int flags = input.readUnsignedByte();
		if ((flags & ~(FLAG_REACHED_END | FLAG_STOPPED_EARLY | FLAG_TRUNCATED)) != 0) {
			throw new IOException("unknown DDA ray flags");
		}
		BandResult expectedBands = new BandResult(
				input.readDouble(),
				input.readDouble(),
				input.readDouble(),
				input.readDouble(),
				input.readDouble(),
				input.readDouble()
		);
		boolean hasFirstMaterial = input.readBoolean();
		VoxelDda.Cell firstMaterial = hasFirstMaterial
				? readCell(input)
				: null;
		List<DdaParityOracle.Segment> segments = new ArrayList<>(segmentCount);
		for (int index = 0; index < segmentCount; index++) {
			segments.add(new DdaParityOracle.Segment(
					readCell(input),
					input.readDouble(),
					input.readInt()
			));
		}
		RayInput rayInput = new RayInput(start, end, maximumCells);
		DdaParityOracle.RayTrace trace = new DdaParityOracle.RayTrace(
				start,
				end,
				maximumCells,
				segments,
				java.util.Optional.ofNullable(firstMaterial),
				visitedCellCount,
				(flags & FLAG_REACHED_END) != 0,
				(flags & FLAG_STOPPED_EARLY) != 0,
				(flags & FLAG_TRUNCATED) != 0
		);
		return new RayRecord(rayId, rayInput, trace, expectedBands);
	}

	private static void writeVector(
			DataOutputStream output,
			AcousticVector vector
	) throws IOException {
		output.writeDouble(vector.x());
		output.writeDouble(vector.y());
		output.writeDouble(vector.z());
	}

	private static AcousticVector readVector(DataInputStream input) throws IOException {
		try {
			return new AcousticVector(
					input.readDouble(),
					input.readDouble(),
					input.readDouble()
			);
		} catch (IllegalArgumentException error) {
			throw new IOException("invalid vector in DDA corpus", error);
		}
	}

	private static void writeCell(
			DataOutputStream output,
			VoxelDda.Cell cell
	) throws IOException {
		output.writeInt(cell.x());
		output.writeInt(cell.y());
		output.writeInt(cell.z());
	}

	private static VoxelDda.Cell readCell(DataInputStream input) throws IOException {
		return new VoxelDda.Cell(
				input.readInt(),
				input.readInt(),
				input.readInt()
		);
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

	private static long mix64(long value) {
		value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
		value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
		return value ^ (value >>> 31);
	}

	private static BandResult accumulateBands(
			DdaParityOracle.RayTrace trace
	) {
		double lowLoss = 0.0;
		double midLoss = 0.0;
		double highLoss = 0.0;
		for (DdaParityOracle.Segment segment : trace.segments()) {
			lowLoss += segment.lengthMeters()
					* lossPerMeter(segment.materialId(), 0);
			midLoss += segment.lengthMeters()
					* lossPerMeter(segment.materialId(), 1);
			highLoss += segment.lengthMeters()
					* lossPerMeter(segment.materialId(), 2);
		}
		return new BandResult(
				lowLoss,
				midLoss,
				highLoss,
				Math.pow(10.0, -lowLoss / 10.0),
				Math.pow(10.0, -midLoss / 10.0),
				Math.pow(10.0, -highLoss / 10.0)
		);
	}

	private static double lossPerMeter(int materialId, int band) {
		if (materialId < 0 || materialId >= MATERIAL_COUNT) {
			throw new IllegalArgumentException(
					"material id is outside the parity table"
			);
		}
		return switch (band) {
			case 0 -> materialId * 0.25;
			case 1 -> materialId * 0.75;
			case 2 -> materialId * 1.5;
			default -> throw new IllegalArgumentException("unknown band");
		};
	}

	public record RayInput(
			AcousticVector start,
			AcousticVector end,
			int maximumCells
	) {
		public RayInput {
			Objects.requireNonNull(start, "start");
			Objects.requireNonNull(end, "end");
			if (maximumCells < 1) {
				throw new IllegalArgumentException(
						"maximumCells must be positive"
				);
			}
		}
	}

	public record RayRecord(
			int rayId,
			RayInput input,
			DdaParityOracle.RayTrace expected,
			BandResult expectedBands
	) {
		public RayRecord {
			if (rayId < 0) {
				throw new IllegalArgumentException(
						"rayId must be non-negative"
				);
			}
			Objects.requireNonNull(input, "input");
			Objects.requireNonNull(expected, "expected");
			Objects.requireNonNull(expectedBands, "expectedBands");
			if (
					!input.start().equals(expected.start())
							|| !input.end().equals(expected.end())
							|| input.maximumCells() != expected.maximumCells()
			) {
				throw new IllegalArgumentException(
						"ray input does not match expected trace"
				);
			}
		}
	}

	public record BandResult(
			double lowLossDb,
			double midLossDb,
			double highLossDb,
			double lowEnergyGain,
			double midEnergyGain,
			double highEnergyGain
	) {
		public BandResult {
			for (double value : new double[]{
					lowLossDb,
					midLossDb,
					highLossDb,
					lowEnergyGain,
					midEnergyGain,
					highEnergyGain
			}) {
				if (!Double.isFinite(value) || value < 0.0) {
					throw new IllegalArgumentException(
							"band values must be finite and non-negative"
					);
				}
			}
		}
	}

	public record Corpus(
			long raySeed,
			long materialSeed,
			String materialTableSha256,
			int materialAlgorithmId,
			int tiePolicyId,
			List<RayRecord> rays
	) {
		public Corpus {
			Objects.requireNonNull(
					materialTableSha256,
					"materialTableSha256"
			);
			if (!materialTableSha256.matches("[0-9a-f]{64}")) {
				throw new IllegalArgumentException(
						"materialTableSha256 must be lowercase SHA-256"
				);
			}
			rays = List.copyOf(rays);
			if (rays.isEmpty() || rays.size() > MAXIMUM_RAYS) {
				throw new IllegalArgumentException(
						"ray count is out of range"
				);
			}
			for (int index = 0; index < rays.size(); index++) {
				if (rays.get(index).rayId() != index) {
					throw new IllegalArgumentException(
							"ray ids must be contiguous"
					);
				}
			}
		}
	}

	public record StreamingSummary(
			int rays,
			long segments,
			long raySeed,
			long materialSeed,
			String materialTableSha256
	) {
		public StreamingSummary {
			if (rays < 1 || segments < 1L) {
				throw new IllegalArgumentException(
						"streaming counts must be positive"
				);
			}
			Objects.requireNonNull(
					materialTableSha256,
					"materialTableSha256"
			);
		}
	}

	private record Header(
			long raySeed,
			long materialSeed,
			String materialTableSha256,
			int materialAlgorithmId,
			int tiePolicyId,
			int rayCount
	) {
	}

	private record SplitMix64(long[] state) {
		private SplitMix64(long seed) {
			this(new long[]{seed});
		}

		private long nextLong() {
			state[0] += 0x9E3779B97F4A7C15L;
			return mix64(state[0]);
		}
	}
}
