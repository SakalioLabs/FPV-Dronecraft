package com.tenicana.dronecraft.acoustics.tools;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticMaterial;
import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.propagation.DirectPathSolver;
import com.tenicana.dronecraft.acoustics.propagation.SparseMaterialSnapshot;
import com.tenicana.dronecraft.acoustics.reverb.LateReverbEstimator;
import com.tenicana.dronecraft.acoustics.reverb.ReflectionStatistics;
import com.tenicana.dronecraft.acoustics.reverb.ReflectionVolume;
import com.tenicana.dronecraft.acoustics.reverb.VoxelReflectionProbe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Writes deterministic, explicitly heuristic environment ordering evidence.
 */
public final class ReverbEnvironmentReferenceCli {
	private static final AcousticVector LISTENER =
			new AcousticVector(0.5, 0.5, 0.5);
	private static final double SOUND_SPEED_METERS_PER_SECOND = 343.0;

	private ReverbEnvironmentReferenceCli() {
	}

	public static void main(String[] arguments) throws IOException {
		if (arguments.length != 1) {
			throw new IllegalArgumentException(
					"usage: ReverbEnvironmentReferenceCli <output-json>"
			);
		}
		Locale.setDefault(Locale.ROOT);
		Path output = Path.of(arguments[0]).toAbsolutePath().normalize();
		VoxelReflectionProbe.Config config =
				VoxelReflectionProbe.Config.researchBaseline();
		long start = System.nanoTime();
		List<EnvironmentResult> environments = List.of(
				analyzeOpen(config),
				analyzeRoom("stone-room", AcousticMaterials.STONE, 2L, config),
				analyzeRoom("wood-room", AcousticMaterials.WOOD, 3L, config),
				analyzeRoom("soft-room", AcousticMaterials.SOFT, 4L, config)
		);
		String json = json(config, environments);
		writeAtomic(output, json);
		double elapsedMilliseconds = (System.nanoTime() - start) / 1.0e6;
		System.out.printf(
				Locale.ROOT,
				"{\"status\":\"valid\",\"schema\":1,"
						+ "\"environments\":%d,\"rays_per_environment\":%d,"
						+ "\"elapsed_ms\":%.6f,\"output\":\"%s\"}%n",
				environments.size(),
				config.rayCount(),
				elapsedMilliseconds,
				escape(output.toString())
		);
	}

	private static EnvironmentResult analyzeOpen(
			VoxelReflectionProbe.Config config
	) {
		SparseMaterialSnapshot snapshot =
				SparseMaterialSnapshot.builder().build();
		return analyze(
				"open-air",
				new ReflectionVolume(
						-16,
						-16,
						-16,
						17,
						17,
						17,
						1L,
						true,
						snapshot
				),
				config
		);
	}

	private static EnvironmentResult analyzeRoom(
			String id,
			AcousticMaterial material,
			long generation,
			VoxelReflectionProbe.Config config
	) {
		int minimumX = -4;
		int minimumY = -3;
		int minimumZ = -5;
		int maximumX = 5;
		int maximumY = 4;
		int maximumZ = 6;
		SparseMaterialSnapshot.Builder builder =
				SparseMaterialSnapshot.builder();
		DirectPathSolver.MaterialSample surface =
				DirectPathSolver.MaterialSample.full(material);
		for (int x = minimumX; x < maximumX; x++) {
			for (int y = minimumY; y < maximumY; y++) {
				for (int z = minimumZ; z < maximumZ; z++) {
					if (x == minimumX || x == maximumX - 1
							|| y == minimumY || y == maximumY - 1
							|| z == minimumZ || z == maximumZ - 1) {
						builder.put(x, y, z, surface);
					}
				}
			}
		}
		return analyze(
				id,
				new ReflectionVolume(
						minimumX,
						minimumY,
						minimumZ,
						maximumX,
						maximumY,
						maximumZ,
						generation,
						true,
						builder.build()
				),
				config
		);
	}

	private static EnvironmentResult analyze(
			String id,
			ReflectionVolume volume,
			VoxelReflectionProbe.Config config
	) {
		ReflectionStatistics statistics = VoxelReflectionProbe.analyze(
				LISTENER,
				volume,
				config
		);
		LateReverbEstimator.Parameters parameters =
				LateReverbEstimator.estimate(
						statistics,
						SOUND_SPEED_METERS_PER_SECOND
				);
		return new EnvironmentResult(id, statistics, parameters);
	}

	private static String json(
			VoxelReflectionProbe.Config config,
			List<EnvironmentResult> environments
	) {
		List<String> entries = new ArrayList<>();
		for (EnvironmentResult environment : environments) {
			entries.add(environmentJson(environment));
		}
		return String.format(
				Locale.ROOT,
				"{%n"
						+ "  \"schema_version\": 1,%n"
						+ "  \"model\": \"deterministic-listener-voxel-reflection-probe-v1\",%n"
						+ "  \"evidence_class\": \"H\",%n"
						+ "  \"release_calibrated\": false,%n"
						+ "  \"claim_boundary\": \"Directional synthetic environment reference; not measured RIR evidence.\",%n"
						+ "  \"sound_speed_m_per_s\": %.17g,%n"
						+ "  \"ray_count\": %d,%n"
						+ "  \"maximum_bounces\": %d,%n"
						+ "  \"maximum_leg_distance_m\": %.17g,%n"
						+ "  \"maximum_cells_per_leg\": %d,%n"
						+ "  \"energy_floor\": %.17g,%n"
						+ "  \"environments\": [%n    %s%n  ]%n"
						+ "}%n",
				SOUND_SPEED_METERS_PER_SECOND,
				config.rayCount(),
				config.maximumBounces(),
				config.maximumLegDistanceMeters(),
				config.maximumCellsPerLeg(),
				config.energyFloor(),
				String.join(",\n    ", entries)
		);
	}

	private static String environmentJson(EnvironmentResult environment) {
		ReflectionStatistics statistics = environment.statistics();
		LateReverbEstimator.Parameters parameters = environment.parameters();
		return String.format(
				Locale.ROOT,
				"{\"id\":\"%s\",\"snapshot_generation\":%d,"
						+ "\"escaped_rays\":%d,\"surface_hits\":%d,"
						+ "\"truncated_legs\":%d,\"openness\":%.17g,"
						+ "\"mean_free_path_m\":%.17g,\"diffusion\":%.17g,"
						+ "\"rt60_s\":%s,\"edt_s\":%s,\"drr_db\":%s,"
						+ "\"first_reflection_energy\":%s}",
				escape(environment.id()),
				statistics.snapshotGeneration(),
				statistics.escapedRays(),
				statistics.surfaceHits(),
				statistics.truncatedLegs(),
				parameters.openness(),
				parameters.meanFreePathMeters(),
				parameters.diffusion(),
				bands(parameters.rt60Seconds()),
				bands(parameters.edtSeconds()),
				bands(parameters.directToReverberantDb()),
				bands(parameters.firstReflectionEnergy())
		);
	}

	private static String bands(AcousticBands bands) {
		return String.format(
				Locale.ROOT,
				"{\"low\":%.17g,\"mid\":%.17g,\"high\":%.17g}",
				bands.low(),
				bands.mid(),
				bands.high()
		);
	}

	private static void writeAtomic(Path output, String text) throws IOException {
		Path parent = output.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
		Files.writeString(
				temporary,
				text,
				StandardCharsets.UTF_8
		);
		try {
			Files.move(
					temporary,
					output,
					StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING
			);
		} catch (AtomicMoveNotSupportedException unsupported) {
			Files.move(
					temporary,
					output,
					StandardCopyOption.REPLACE_EXISTING
			);
		}
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private record EnvironmentResult(
			String id,
			ReflectionStatistics statistics,
			LateReverbEstimator.Parameters parameters
	) {
	}
}
