package com.tenicana.dronecraft.acoustics.tools;

import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.propagation.DirectPathSolver;
import com.tenicana.dronecraft.acoustics.propagation.SparseMaterialSnapshot;
import com.tenicana.dronecraft.acoustics.voxel.DdaProductionSnapshotBundle;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Generates a large deterministic corpus in the current {@code MCFPDDA1}
 * production-bundle format so that a bounded multi-batch CUDA executor can be
 * verified against the same Java CPU oracle as the small fixture.
 *
 * <p>The scene and the ray fan are synthetic. This tool does not capture
 * Minecraft runtime state and must never be presented as a live-world
 * artifact. Every floating-point operation uses {@link StrictMath} so the
 * corpus is reproducible across platforms and JVM implementations.
 */
public final class DdaProductionCorpusCli {
	public static final int DEFAULT_RAY_COUNT = 100_008;
	public static final int ADVERSARIAL_RAY_COUNT = 8;
	public static final int DEFAULT_MAXIMUM_CELLS = 256;
	public static final int TRUNCATING_MAXIMUM_CELLS = 24;
	public static final int TRUNCATION_STRIDE = 37;
	private static final String CORPUS_IDENTITY =
			"mcfpv-dda-production-corpus-v1";
	private static final int ROOM_HALF_EXTENT = 64;
	private static final int ROOM_HEIGHT = 24;
	private static final int FLOOR_Y = -1;
	private static final int WINDOW_LOW_Y = 8;
	private static final int WINDOW_HIGH_Y = 11;
	private static final double RAY_RANGE_METERS = 96.0;
	private static final double QUANTIZATION = 256.0;
	private static final long SNAPSHOT_GENERATION = 7L;

	private DdaProductionCorpusCli() {
	}

	public static void main(String[] arguments) throws IOException {
		Path output = arguments.length > 0
				? Path.of(arguments[0])
				: Path.of("build/research/dda-production-corpus-v1.bin");
		int rayCount = arguments.length > 1
				? Integer.parseInt(arguments[1])
				: DEFAULT_RAY_COUNT;
		DdaProductionSnapshotBundle.Bundle bundle = corpus(rayCount);
		DdaProductionSnapshotBundle.writeAtomic(output, bundle);
		Path absolute = output.toAbsolutePath();
		long segmentCapacity = 0L;
		for (DdaProductionSnapshotBundle.Ray ray : bundle.rays()) {
			segmentCapacity += ray.maximumCells();
		}
		System.out.printf(
				"{\"schema\":%d,\"cells\":%d,\"rays\":%d,"
						+ "\"adversarial_rays\":%d,\"sources\":%d,"
						+ "\"total_segment_capacity\":%d,"
						+ "\"material_table_sha256\":\"%s\","
						+ "\"snapshot_sha256\":\"%s\","
						+ "\"file_sha256\":\"%s\",\"bytes\":%d,"
						+ "\"synthetic_scene\":true,"
						+ "\"minecraft_capture\":false}%n",
				DdaProductionSnapshotBundle.SCHEMA_VERSION,
				bundle.snapshot().size(),
				bundle.rays().size(),
				ADVERSARIAL_RAY_COUNT,
				sources().size(),
				segmentCapacity,
				AcousticMaterials.diagnosticSha256(),
				bundle.snapshot().diagnosticSha256(),
				sha256(absolute),
				Files.size(absolute)
		);
	}

	/**
	 * Builds the deterministic corpus. The same {@code rayCount} always yields
	 * an identical bundle.
	 */
	public static DdaProductionSnapshotBundle.Bundle corpus(int rayCount) {
		if (rayCount < ADVERSARIAL_RAY_COUNT) {
			throw new IllegalArgumentException(
					"rayCount must cover the adversarial prefix"
			);
		}
		return new DdaProductionSnapshotBundle.Bundle(
				new DdaProductionSnapshotBundle.Metadata(
						1,
						"corpus-1.21.11",
						"corpus-v1",
						contentFingerprint(),
						SNAPSHOT_GENERATION
				),
				scene(),
				rays(rayCount)
		);
	}

	/**
	 * A closed synthetic room with a stone floor, wooden ceiling, stone walls
	 * pierced by glass windows, metal pillars, a partially filled water pool,
	 * scattered foliage, and soft baffles.
	 */
	public static SparseMaterialSnapshot scene() {
		SparseMaterialSnapshot.Builder snapshot =
				SparseMaterialSnapshot.builder();
		DirectPathSolver.MaterialSample stone =
				DirectPathSolver.MaterialSample.full(AcousticMaterials.STONE);
		DirectPathSolver.MaterialSample wood =
				DirectPathSolver.MaterialSample.full(AcousticMaterials.WOOD);
		DirectPathSolver.MaterialSample glass =
				DirectPathSolver.MaterialSample.full(AcousticMaterials.GLASS);
		DirectPathSolver.MaterialSample metal =
				DirectPathSolver.MaterialSample.full(AcousticMaterials.METAL);
		DirectPathSolver.MaterialSample water =
				new DirectPathSolver.MaterialSample(
						AcousticMaterials.WATER,
						0.875
				);
		DirectPathSolver.MaterialSample foliage =
				new DirectPathSolver.MaterialSample(
						AcousticMaterials.FOLIAGE,
						0.375
				);
		DirectPathSolver.MaterialSample soft =
				new DirectPathSolver.MaterialSample(
						AcousticMaterials.SOFT,
						0.625
				);

		int half = ROOM_HALF_EXTENT;
		for (int x = -half; x <= half; x++) {
			for (int z = -half; z <= half; z++) {
				snapshot.put(x, FLOOR_Y, z, stone);
				snapshot.put(x, ROOM_HEIGHT, z, wood);
			}
		}
		for (int y = 0; y < ROOM_HEIGHT; y++) {
			DirectPathSolver.MaterialSample wall =
					y >= WINDOW_LOW_Y && y <= WINDOW_HIGH_Y ? glass : stone;
			for (int offset = -half; offset <= half; offset++) {
				snapshot.put(-half, y, offset, wall);
				snapshot.put(half, y, offset, wall);
				snapshot.put(offset, y, -half, wall);
				snapshot.put(offset, y, half, wall);
			}
		}
		for (int x = -half + 16; x < half; x += 16) {
			for (int z = -half + 16; z < half; z += 16) {
				for (int y = 0; y < ROOM_HEIGHT; y++) {
					snapshot.put(x, y, z, metal);
				}
			}
		}
		for (int x = -24; x <= 24; x++) {
			for (int z = 8; z <= 40; z++) {
				snapshot.put(x, 0, z, water);
			}
		}
		for (int x = -40; x <= -8; x++) {
			for (int z = -40; z <= -8; z++) {
				if (((x * 31 + z * 17) & 3) == 0) {
					for (int y = 0; y < 4; y++) {
						snapshot.put(x, y, z, foliage);
					}
				}
			}
		}
		for (int z = -40; z <= 40; z++) {
			if (((z * 13) & 7) == 0) {
				for (int y = 0; y < 6; y++) {
					snapshot.put(20, y, z, soft);
				}
			}
		}
		return snapshot.build();
	}

	/**
	 * Emitter positions. A realistic early-reflection update fans thousands of
	 * rays from a small number of sources rather than sampling independent
	 * endpoints, so the corpus mirrors that shape.
	 */
	public static List<AcousticVector> sources() {
		return List.of(
				new AcousticVector(-48.5, 2.5, -48.5),
				new AcousticVector(0.5, 6.5, 0.5),
				new AcousticVector(40.5, 3.5, -30.5),
				new AcousticVector(-20.5, 12.5, 35.5),
				new AcousticVector(55.5, 18.5, 55.5),
				new AcousticVector(-55.5, 1.5, 20.5),
				new AcousticVector(12.5, 20.5, -55.5),
				new AcousticVector(30.5, 8.5, 30.5),
				new AcousticVector(-35.5, 15.5, -10.5),
				new AcousticVector(5.5, 22.5, 45.5)
		);
	}

	private static List<DdaProductionSnapshotBundle.Ray> rays(int rayCount) {
		List<DdaProductionSnapshotBundle.Ray> rays =
				new ArrayList<>(rayCount);
		addAdversarialRays(rays);
		List<AcousticVector> sources = sources();
		int fanCount = rayCount - ADVERSARIAL_RAY_COUNT;
		int perSource = fanCount / sources.size();
		int remainder = fanCount % sources.size();
		for (int index = 0; index < sources.size(); index++) {
			int count = perSource + (index < remainder ? 1 : 0);
			AcousticVector source = sources.get(index);
			for (int step = 0; step < count; step++) {
				rays.add(fanRay(source, step, count, rays.size()));
			}
		}
		return rays;
	}

	private static DdaProductionSnapshotBundle.Ray fanRay(
			AcousticVector source,
			int step,
			int count,
			int rayIndex
	) {
		double offset = step + 0.5;
		double polar = StrictMath.acos(1.0 - 2.0 * offset / count);
		double azimuth = StrictMath.PI
				* (1.0 + StrictMath.sqrt(5.0)) * offset;
		double sinPolar = StrictMath.sin(polar);
		double directionX = StrictMath.cos(azimuth) * sinPolar;
		double directionY = StrictMath.cos(polar);
		double directionZ = StrictMath.sin(azimuth) * sinPolar;
		return new DdaProductionSnapshotBundle.Ray(
				source,
				new AcousticVector(
						quantize(source.x() + directionX * RAY_RANGE_METERS),
						quantize(source.y() + directionY * RAY_RANGE_METERS),
						quantize(source.z() + directionZ * RAY_RANGE_METERS)
				),
				rayIndex % TRUNCATION_STRIDE == 0
						? TRUNCATING_MAXIMUM_CELLS
						: DEFAULT_MAXIMUM_CELLS
		);
	}

	private static void addAdversarialRays(
			List<DdaProductionSnapshotBundle.Ray> rays
	) {
		rays.add(new DdaProductionSnapshotBundle.Ray(
				new AcousticVector(0.5, 0.5, 0.5),
				new AcousticVector(0.5, 0.5, 0.5),
				1
		));
		rays.add(new DdaProductionSnapshotBundle.Ray(
				new AcousticVector(-63.75, 0.5, 0.5),
				new AcousticVector(63.75, 0.5, 0.5),
				DEFAULT_MAXIMUM_CELLS
		));
		rays.add(new DdaProductionSnapshotBundle.Ray(
				new AcousticVector(63.75, 4.5, -0.5),
				new AcousticVector(-63.75, 4.5, -0.5),
				DEFAULT_MAXIMUM_CELLS
		));
		rays.add(new DdaProductionSnapshotBundle.Ray(
				new AcousticVector(-32.0, -1.0, -32.0),
				new AcousticVector(32.0, 23.0, 32.0),
				DEFAULT_MAXIMUM_CELLS
		));
		rays.add(new DdaProductionSnapshotBundle.Ray(
				new AcousticVector(-24.0, 6.0, 12.25),
				new AcousticVector(24.0, 18.0, 12.25),
				DEFAULT_MAXIMUM_CELLS
		));
		rays.add(new DdaProductionSnapshotBundle.Ray(
				new AcousticVector(8.5, 22.5, 24.5),
				new AcousticVector(8.5, -8.5, 24.5),
				DEFAULT_MAXIMUM_CELLS
		));
		rays.add(new DdaProductionSnapshotBundle.Ray(
				new AcousticVector(-60.5, 9.5, -60.5),
				new AcousticVector(60.5, 9.5, 60.5),
				TRUNCATING_MAXIMUM_CELLS
		));
		rays.add(new DdaProductionSnapshotBundle.Ray(
				new AcousticVector(20.5, 2.5, -40.5),
				new AcousticVector(-20.5, 20.5, 40.5),
				DEFAULT_MAXIMUM_CELLS
		));
	}

	private static double quantize(double value) {
		return StrictMath.floor(value * QUANTIZATION) / QUANTIZATION;
	}

	private static String contentFingerprint() {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(
							CORPUS_IDENTITY.getBytes(StandardCharsets.UTF_8)
					)
			);
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 is unavailable", error);
		}
	}

	private static String sha256(Path path) throws IOException {
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
}
