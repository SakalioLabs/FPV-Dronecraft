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
 * Measures listener-room to adjacent-room transport and return through a
 * nested portal in a closed two-room volume.
 */
public final class TwoRoomPortalCouplingCli {
	private static final int ROOM_LENGTH = 9;
	private static final int WIDTH = 9;
	private static final int HEIGHT = 5;
	private static final int DIVIDER_X = ROOM_LENGTH + 1;
	private static final int MAXIMUM_X = ROOM_LENGTH * 2 + 3;
	private static final int MAXIMUM_Y = HEIGHT + 2;
	private static final int MAXIMUM_Z = WIDTH + 2;
	private static final int RAYS = 4_096;
	private static final int BOUNCES = 48;
	private static final double SOUND_SPEED_METERS_PER_SECOND = 343.0;
	private static final int[][] PORTALS = {
			{0, 0},
			{1, 1},
			{1, 2},
			{1, 3},
			{3, 3},
			{5, 3},
			{9, 5}
	};
	private static final AcousticMaterial ROOM_A = AcousticMaterials.STONE;
	private static final AcousticMaterial ROOM_B = AcousticMaterials.SOFT;
	private static final AcousticMaterial DIVIDER = AcousticMaterials.GLASS;

	private TwoRoomPortalCouplingCli() {
	}

	public static void main(String[] arguments) throws IOException {
		if (arguments.length != 1) {
			throw new IllegalArgumentException(
					"usage: TwoRoomPortalCouplingCli <output-json>"
			);
		}
		Locale.setDefault(Locale.ROOT);
		List<Result> results = new ArrayList<>();
		for (int[] portal : PORTALS) {
			results.add(run(portal[0], portal[1]));
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

	private static Result run(int portalWidth, int portalHeight) {
		double mixingMfp = effectiveMeanFreePath(
				portalWidth * portalHeight
		);
		PortalAudit audit = new PortalAudit();
		ReflectionStatistics statistics = VoxelReflectionProbe.analyze(
				new AcousticVector(5.5, 3.5, 5.5),
				volume(portalWidth, portalHeight),
				new VoxelReflectionProbe.Config(
						RAYS,
						BOUNCES,
						192.0,
						512,
						1.0e-300
				),
				audit,
				(material, bounce, cumulativePathMeters) ->
						cumulativePathMeters >= 2.0 * mixingMfp
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
				portalWidth,
				portalHeight,
				portalWidth * portalHeight,
				mixingMfp,
				audit.roomBHits,
				(double) audit.roomBHits / statistics.surfaceHits(),
				audit.enteredRoomBRays,
				audit.returnedToRoomARays,
				statistics.escapedRays(),
				parameters.openness(),
				parameters.rt60Seconds(),
				parameters.edtSeconds(),
				controls.wetGain()
		);
	}

	private static ReflectionVolume volume(int portalWidth, int portalHeight) {
		Set<Long> portal = portalCells(portalWidth, portalHeight);
		SparseMaterialSnapshot.Builder builder =
				SparseMaterialSnapshot.builder();
		for (int x = 0; x < MAXIMUM_X; x++) {
			for (int y = 0; y < MAXIMUM_Y; y++) {
				for (int z = 0; z < MAXIMUM_Z; z++) {
					AcousticMaterial material = null;
					if (x == DIVIDER_X) {
						if (!portal.contains(key(y, z))) {
							material = DIVIDER;
						}
					} else if (x == 0 || x == MAXIMUM_X - 1
							|| y == 0 || y == MAXIMUM_Y - 1
							|| z == 0 || z == MAXIMUM_Z - 1) {
						material = x < DIVIDER_X ? ROOM_A : ROOM_B;
					}
					if (material != null) {
						builder.put(
								x,
								y,
								z,
								DirectPathSolver.MaterialSample.full(material)
						);
					}
				}
			}
		}
		return new ReflectionVolume(
				0,
				0,
				0,
				MAXIMUM_X,
				MAXIMUM_Y,
				MAXIMUM_Z,
				79L,
				true,
				builder.build()
		);
	}

	private static Set<Long> portalCells(int width, int height) {
		Set<Long> cells = new HashSet<>();
		int minimumZ = 1 + (WIDTH - width) / 2;
		for (int z = minimumZ; z < minimumZ + width; z++) {
			for (int y = 1; y <= height; y++) {
				cells.add(key(y, z));
			}
		}
		return cells;
	}

	private static double effectiveMeanFreePath(int apertureCells) {
		double volume = 2.0 * ROOM_LENGTH * WIDTH * HEIGHT;
		double externalSurface = 2.0 * (
				(2.0 * ROOM_LENGTH) * WIDTH
						+ (2.0 * ROOM_LENGTH) * HEIGHT
						+ WIDTH * HEIGHT
		);
		double dividerTwoSided = 2.0 * (
				WIDTH * HEIGHT - apertureCells
		);
		return 4.0 * volume / (externalSurface + dividerTwoSided);
	}

	private static long key(int y, int z) {
		return ((long) y << 32) ^ Integer.toUnsignedLong(z);
	}

	private static String json(List<Result> results) {
		return String.format(
				Locale.ROOT,
				"{%n"
						+ "  \"schema_version\": 1,%n"
						+ "  \"status\": \"valid-diagnostic\",%n"
						+ "  \"room_interior_cells_each\": "
						+ "{\"length\":%d,\"width\":%d,\"height\":%d},%n"
						+ "  \"ray_count\": %d,%n"
						+ "  \"maximum_bounces\": %d,%n"
						+ "  \"release_calibrated\": false,%n"
						+ "  \"claim_boundary\": \"Closed two-room portal "
						+ "transport diagnostic with distinct room materials; "
						+ "not measured coupled-room decay truth.\",%n"
						+ "  \"steps\": [%n    %s%n  ]%n"
						+ "}%n",
				ROOM_LENGTH,
				WIDTH,
				HEIGHT,
				RAYS,
				BOUNCES,
				String.join(
						",\n    ",
						results.stream().map(
								TwoRoomPortalCouplingCli::resultJson
						).toList()
				)
		);
	}

	private static String resultJson(Result result) {
		return String.format(
				Locale.ROOT,
				"{\"portal_width_cells\":%d,\"portal_height_cells\":%d,"
						+ "\"aperture_cells\":%d,"
						+ "\"effective_mfp_m\":%.17g,"
						+ "\"room_b_surface_hits\":%d,"
						+ "\"room_b_hit_fraction\":%.17g,"
						+ "\"rays_entering_room_b\":%d,"
						+ "\"rays_returning_to_room_a\":%d,"
						+ "\"escaped_rays\":%d,\"openness\":%.17g,"
						+ "\"rt60_s\":%s,\"edt_s\":%s,"
						+ "\"wet_gain\":%.17g}",
				result.portalWidth(),
				result.portalHeight(),
				result.apertureCells(),
				result.effectiveMfpMeters(),
				result.roomBHits(),
				result.roomBHitFraction(),
				result.enteredRoomBRays(),
				result.returnedToRoomARays(),
				result.escapedRays(),
				result.openness(),
				bands(result.rt60()),
				bands(result.edt()),
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

	private static final class PortalAudit
			implements VoxelReflectionProbe.SurfaceHitObserver {
		private final boolean[] enteredB = new boolean[RAYS];
		private final boolean[] returnedA = new boolean[RAYS];
		private int roomBHits;
		private int enteredRoomBRays;
		private int returnedToRoomARays;

		@Override
		public void onHit(
				int rayIndex,
				int bounce,
				AcousticMaterial material,
				AcousticVector normal,
				double distanceMeters
		) {
			if (material.equals(ROOM_B)) {
				roomBHits++;
				if (!enteredB[rayIndex]) {
					enteredB[rayIndex] = true;
					enteredRoomBRays++;
				}
			} else if (material.equals(ROOM_A)
					&& enteredB[rayIndex] && !returnedA[rayIndex]) {
				returnedA[rayIndex] = true;
				returnedToRoomARays++;
			}
		}
	}

	private record Result(
			int portalWidth,
			int portalHeight,
			int apertureCells,
			double effectiveMfpMeters,
			int roomBHits,
			double roomBHitFraction,
			int enteredRoomBRays,
			int returnedToRoomARays,
			int escapedRays,
			double openness,
			AcousticBands rt60,
			AcousticBands edt,
			double wetGain
	) {
	}
}
