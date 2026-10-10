package com.tenicana.dronecraft.acoustics.tools;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticMaterial;
import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.propagation.DirectPathSolver;
import com.tenicana.dronecraft.acoustics.propagation.SparseMaterialSnapshot;
import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;
import com.tenicana.dronecraft.acoustics.reverb.LateReverbEstimator;
import com.tenicana.dronecraft.acoustics.reverb.ReflectionStatistics;
import com.tenicana.dronecraft.acoustics.reverb.ReflectionVolume;
import com.tenicana.dronecraft.acoustics.reverb.ShoeboxRoomDecay;
import com.tenicana.dronecraft.acoustics.reverb.VoxelReflectionProbe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Opens one boundary wall in nested doorway steps and records escape/decay
 * continuity for the 2-MFP late-diffuse research policy.
 */
public final class DoorwayReverbContinuityCli {
	private static final int LENGTH = 11;
	private static final int WIDTH = 11;
	private static final int HEIGHT = 5;
	private static final int RAYS = 4_096;
	private static final int BOUNCES = 48;
	private static final double SOUND_SPEED_METERS_PER_SECOND = 343.0;
	private static final int[][] APERTURES = {
			{0, 0},
			{1, 1},
			{1, 2},
			{1, 3},
			{3, 3},
			{3, 4},
			{5, 4},
			{7, 4},
			{11, 5}
	};
	private static final ShoeboxRoomDecay ROOM =
			new ShoeboxRoomDecay(LENGTH, WIDTH, HEIGHT);
	private static final double PATH_THRESHOLD_METERS =
			2.0 * ROOM.diffuseMeanFreePathMeters();

	private DoorwayReverbContinuityCli() {
	}

	public static void main(String[] arguments) throws IOException {
		if (arguments.length != 1) {
			throw new IllegalArgumentException(
					"usage: DoorwayReverbContinuityCli <output-json>"
			);
		}
		Locale.setDefault(Locale.ROOT);
		List<Result> results = new ArrayList<>();
		for (int[] aperture : APERTURES) {
			results.add(run(aperture[0], aperture[1]));
		}
		Path output = Path.of(arguments[0]).toAbsolutePath().normalize();
		Files.createDirectories(output.getParent());
		Files.writeString(output, json(results), StandardCharsets.UTF_8);
		System.out.printf(
				Locale.ROOT,
				"{\"status\":\"valid-diagnostic\",\"steps\":%d,"
						+ "\"output\":\"%s\"}%n",
				results.size(),
				escape(output.toString())
		);
	}

	static Result run(int doorWidth, int doorHeight) {
		ReflectionStatistics statistics = VoxelReflectionProbe.analyze(
				new AcousticVector(6.5, 3.5, 6.5),
				volume(doorWidth, doorHeight),
				new VoxelReflectionProbe.Config(
						RAYS,
						BOUNCES,
						160.0,
						384,
						1.0e-300
				),
				VoxelReflectionProbe.SurfaceHitObserver.NONE,
				(material, bounce, cumulativePathMeters) ->
						cumulativePathMeters >= PATH_THRESHOLD_METERS
								? 1.0
								: material.scattering()
		);
		LateReverbEstimator.Parameters parameters =
				LateReverbEstimator.estimate(
						statistics,
						SOUND_SPEED_METERS_PER_SECOND
				);
		FdnEnvironmentMapper.Controls controls =
				FdnEnvironmentMapper.map(parameters);
		return new Result(
				doorWidth,
				doorHeight,
				doorWidth * doorHeight,
				statistics.escapedRays(),
				statistics.surfaceHits(),
				parameters.openness(),
				parameters.meanFreePathMeters(),
				parameters.rt60Seconds(),
				parameters.edtSeconds(),
				parameters.directToReverberantDb(),
				parameters.firstReflectionEnergy(),
				controls.wetGain()
		);
	}

	private static ReflectionVolume volume(int doorWidth, int doorHeight) {
		int maximumX = LENGTH + 2;
		int maximumY = HEIGHT + 2;
		int maximumZ = WIDTH + 2;
		Set<Long> aperture = apertureCells(doorWidth, doorHeight);
		SparseMaterialSnapshot.Builder builder =
				SparseMaterialSnapshot.builder();
		DirectPathSolver.MaterialSample stone =
				DirectPathSolver.MaterialSample.full(AcousticMaterials.STONE);
		for (int x = 0; x < maximumX; x++) {
			for (int y = 0; y < maximumY; y++) {
				for (int z = 0; z < maximumZ; z++) {
					if (x != 0 && x != maximumX - 1
							&& y != 0 && y != maximumY - 1
							&& z != 0 && z != maximumZ - 1) {
						continue;
					}
					if (z == 0 && aperture.contains(key(x, y))) {
						continue;
					}
					builder.put(x, y, z, stone);
				}
			}
		}
		return new ReflectionVolume(
				0,
				0,
				0,
				maximumX,
				maximumY,
				maximumZ,
				78L,
				true,
				builder.build()
		);
	}

	private static Set<Long> apertureCells(int width, int height) {
		Set<Long> cells = new HashSet<>();
		int minimumX = 1 + (LENGTH - width) / 2;
		for (int x = minimumX; x < minimumX + width; x++) {
			for (int y = 1; y <= height; y++) {
				cells.add(key(x, y));
			}
		}
		return cells;
	}

	private static long key(int x, int y) {
		return ((long) x << 32) ^ Integer.toUnsignedLong(y);
	}

	private static String json(List<Result> results) {
		return String.format(
				Locale.ROOT,
				"{%n"
						+ "  \"schema_version\": 1,%n"
						+ "  \"status\": \"valid-diagnostic\",%n"
						+ "  \"room_interior_cells\": "
						+ "{\"length\":%d,\"width\":%d,\"height\":%d},%n"
						+ "  \"ray_count\": %d,%n"
						+ "  \"maximum_bounces\": %d,%n"
						+ "  \"late_diffuse_path_threshold_m\": %.17g,%n"
						+ "  \"late_diffuse_path_threshold_s\": %.17g,%n"
						+ "  \"release_calibrated\": false,%n"
						+ "  \"claim_boundary\": \"Nested voxel doorway "
						+ "continuity diagnostic; not an analytic open-room "
						+ "RT60 reference or measured RIR.\",%n"
						+ "  \"steps\": [%n    %s%n  ]%n"
						+ "}%n",
				LENGTH,
				WIDTH,
				HEIGHT,
				RAYS,
				BOUNCES,
				PATH_THRESHOLD_METERS,
				PATH_THRESHOLD_METERS / SOUND_SPEED_METERS_PER_SECOND,
				String.join(
						",\n    ",
						results.stream().map(
								DoorwayReverbContinuityCli::resultJson
						).toList()
				)
		);
	}

	private static String resultJson(Result result) {
		return String.format(
				Locale.ROOT,
				"{\"door_width_cells\":%d,\"door_height_cells\":%d,"
						+ "\"aperture_cells\":%d,\"escaped_rays\":%d,"
						+ "\"surface_hits\":%d,\"openness\":%.17g,"
						+ "\"mean_free_path_m\":%.17g,"
						+ "\"rt60_s\":%s,\"edt_s\":%s,\"drr_db\":%s,"
						+ "\"first_reflection_energy\":%s,"
						+ "\"wet_gain\":%.17g}",
				result.doorWidth(),
				result.doorHeight(),
				result.apertureCells(),
				result.escapedRays(),
				result.surfaceHits(),
				result.openness(),
				result.meanFreePathMeters(),
				bands(result.rt60()),
				bands(result.edt()),
				bands(result.drr()),
				bands(result.firstReflectionEnergy()),
				result.wetGain()
		);
	}

	private static String bands(AcousticBands value) {
		return String.format(
				Locale.ROOT,
				"{\"low\":%.17g,\"mid\":%.17g,\"high\":%.17g}",
				value.low(),
				value.mid(),
				value.high()
		);
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	record Result(
			int doorWidth,
			int doorHeight,
			int apertureCells,
			int escapedRays,
			int surfaceHits,
			double openness,
			double meanFreePathMeters,
			AcousticBands rt60,
			AcousticBands edt,
			AcousticBands drr,
			AcousticBands firstReflectionEnergy,
			double wetGain
	) {
	}
}
