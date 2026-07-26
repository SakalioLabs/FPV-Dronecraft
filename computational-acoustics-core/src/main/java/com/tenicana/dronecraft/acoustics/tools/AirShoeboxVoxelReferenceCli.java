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
import com.tenicana.dronecraft.acoustics.reverb.ShoeboxRoomDecay;
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
 * Compares AIR-derived effective absorption against matching shoebox and
 * one-metre voxel approximations. This is a geometry/error decomposition,
 * not a Minecraft material fit.
 */
public final class AirShoeboxVoxelReferenceCli {
	private static final double SOUND_SPEED_METERS_PER_SECOND = 343.0;
	private static final String AIR_REPORT_SHA256 =
			"8c962c2ceca292475fb035057bfd4e3b6"
					+ "170eb698cd36902bb9c92bfa093948b";
	private static final VoxelReflectionProbe.Config CONFIG =
			VoxelReflectionProbe.Config.researchBaseline();
	private static final List<RoomCase> ROOMS = List.of(
			new RoomCase(
					"air-booth",
					new ShoeboxRoomDecay(3.0, 1.8, 2.2),
					3,
					2,
					2,
					new AcousticBands(
							0.1268693589758476,
							0.13715287006894902,
							0.10821303407466658
					)
			),
			new RoomCase(
					"air-lecture",
					new ShoeboxRoomDecay(10.8, 10.9, 3.15),
					11,
					11,
					3,
					new AcousticBands(
							0.857164910011111,
							0.9030624220156351,
							0.6915336132873643
					)
			)
	);

	private AirShoeboxVoxelReferenceCli() {
	}

	public static void main(String[] arguments) throws IOException {
		if (arguments.length != 1) {
			throw new IllegalArgumentException(
					"usage: AirShoeboxVoxelReferenceCli <output-json>"
			);
		}
		Locale.setDefault(Locale.ROOT);
		Path output = Path.of(arguments[0]).toAbsolutePath().normalize();
		List<Result> results = new ArrayList<>();
		for (int index = 0; index < ROOMS.size(); index++) {
			results.add(analyze(ROOMS.get(index), index + 1L));
		}
		validate(results);
		String json = json(results);
		writeAtomic(output, json);
		System.out.printf(
				Locale.ROOT,
				"{\"status\":\"valid-diagnostic\",\"schema\":1,"
						+ "\"rooms\":%d,\"output\":\"%s\"}%n",
				results.size(),
				escape(output.toString())
		);
	}

	private static Result analyze(RoomCase room, long generation) {
		AcousticBands absorption =
				room.physicalRoom().effectiveEyringAbsorption(
						room.measuredRt60Seconds(),
						SOUND_SPEED_METERS_PER_SECOND
				);
		AcousticBands analyticReconstruction =
				room.physicalRoom().eyringRt60Seconds(
						absorption,
						SOUND_SPEED_METERS_PER_SECOND
				);
		ShoeboxRoomDecay voxelRoom = new ShoeboxRoomDecay(
				room.voxelLength(),
				room.voxelWidth(),
				room.voxelHeight()
		);
		AcousticMaterial effectiveSurface = new AcousticMaterial(
				room.id() + "-effective-surface",
				AcousticBands.SILENT,
				absorption,
				1.0
		);
		ReflectionVolume volume = voxelRoom(
				room.voxelLength(),
				room.voxelWidth(),
				room.voxelHeight(),
				generation,
				effectiveSurface
		);
		AcousticVector listener = new AcousticVector(
				1.0 + room.voxelLength() * 0.5,
				1.0 + room.voxelHeight() * 0.5,
				1.0 + room.voxelWidth() * 0.5
		);
		ReflectionStatistics statistics = VoxelReflectionProbe.analyze(
				listener,
				volume,
				CONFIG
		);
		LateReverbEstimator.Parameters estimate =
				LateReverbEstimator.estimate(
						statistics,
						SOUND_SPEED_METERS_PER_SECOND
				);
		AcousticBands voxelAnalyticRt60 = voxelRoom.eyringRt60Seconds(
				absorption,
				SOUND_SPEED_METERS_PER_SECOND
		);
		return new Result(
				room,
				voxelRoom,
				absorption,
				analyticReconstruction,
				voxelAnalyticRt60,
				statistics,
				estimate
		);
	}

	private static ReflectionVolume voxelRoom(
			int interiorLength,
			int interiorWidth,
			int interiorHeight,
			long generation,
			AcousticMaterial material
	) {
		int maximumX = interiorLength + 2;
		int maximumY = interiorHeight + 2;
		int maximumZ = interiorWidth + 2;
		DirectPathSolver.MaterialSample surface =
				DirectPathSolver.MaterialSample.full(material);
		SparseMaterialSnapshot.Builder builder =
				SparseMaterialSnapshot.builder();
		for (int x = 0; x < maximumX; x++) {
			for (int y = 0; y < maximumY; y++) {
				for (int z = 0; z < maximumZ; z++) {
					if (x == 0 || x == maximumX - 1
							|| y == 0 || y == maximumY - 1
							|| z == 0 || z == maximumZ - 1) {
						builder.put(x, y, z, surface);
					}
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
				generation,
				true,
				builder.build()
		);
	}

	private static String json(List<Result> results) {
		List<String> rooms = results.stream()
				.map(AirShoeboxVoxelReferenceCli::roomJson)
				.toList();
		double maximumMfpError = results.stream()
				.mapToDouble(result -> relativeError(
						result.statistics().meanFreePathMeters(),
						result.voxelRoom().diffuseMeanFreePathMeters()
				))
				.max()
				.orElseThrow();
		double maximumRt60Error = results.stream()
				.mapToDouble(result -> maximumRelativeError(
						result.estimate().rt60Seconds(),
						result.room().measuredRt60Seconds()
				))
				.max()
				.orElseThrow();
		double minimumStoneRatio = results.stream()
				.mapToDouble(result -> minimumRatio(
						result.room().physicalRoom().eyringRt60Seconds(
								AcousticMaterials.STONE.surfaceAbsorption(),
								SOUND_SPEED_METERS_PER_SECOND
						),
						result.room().measuredRt60Seconds()
				))
				.min()
				.orElseThrow();
		return String.format(
				Locale.ROOT,
				"{%n"
						+ "  \"schema_version\": 1,%n"
						+ "  \"status\": \"valid-diagnostic\",%n"
						+ "  \"source_air_report_sha256\": \"%s\",%n"
						+ "  \"sound_speed_m_per_s\": %.17g,%n"
						+ "  \"ray_count\": %d,%n"
						+ "  \"maximum_bounces\": %d,%n"
						+ "  \"release_calibrated\": false,%n"
						+ "  \"gates\": {\"maximum_probe_mfp_relative_error\":%.17g,"
						+ "\"maximum_probe_rt60_relative_error\":%.17g,"
						+ "\"minimum_current_stone_rt60_ratio\":%.17g,"
						+ "\"probe_mfp_within_10_percent\":true,"
						+ "\"probe_rt60_within_10_percent\":true,"
						+ "\"current_stone_at_least_2x_measured\":true,"
						+ "\"minecraft_release_calibrated\":false},%n"
						+ "  \"claim_boundary\": \"AIR-derived room-average absorption and one-metre voxel geometry error decomposition; not a Minecraft block-material fit.\",%n"
						+ "  \"rooms\": [%n    %s%n  ]%n"
						+ "}%n",
				AIR_REPORT_SHA256,
				SOUND_SPEED_METERS_PER_SECOND,
				CONFIG.rayCount(),
				CONFIG.maximumBounces(),
				maximumMfpError,
				maximumRt60Error,
				minimumStoneRatio,
				String.join(",\n    ", rooms)
		);
	}

	private static void validate(List<Result> results) {
		for (Result result : results) {
			double mfpError = relativeError(
					result.statistics().meanFreePathMeters(),
					result.voxelRoom().diffuseMeanFreePathMeters()
			);
			if (mfpError > 0.10) {
				throw new IllegalStateException(
						result.room().id()
								+ " probe mean-free-path error exceeds 10%"
				);
			}
			double rt60Error = maximumRelativeError(
					result.estimate().rt60Seconds(),
					result.room().measuredRt60Seconds()
			);
			if (rt60Error > 0.10) {
				throw new IllegalStateException(
						result.room().id()
								+ " probe RT60 error exceeds 10%"
				);
			}
			AcousticBands stoneRt60 =
					result.room().physicalRoom().eyringRt60Seconds(
							AcousticMaterials.STONE.surfaceAbsorption(),
							SOUND_SPEED_METERS_PER_SECOND
					);
			if (minimumRatio(
					stoneRt60,
					result.room().measuredRt60Seconds()
			) < 2.0) {
				throw new IllegalStateException(
						result.room().id()
								+ " current stone mismatch evidence changed"
				);
			}
		}
	}

	private static String roomJson(Result result) {
		RoomCase room = result.room();
		ReflectionStatistics statistics = result.statistics();
		double physicalMfp = room.physicalRoom()
				.diffuseMeanFreePathMeters();
		double voxelMfp = result.voxelRoom()
				.diffuseMeanFreePathMeters();
		double observedMfp = statistics.meanFreePathMeters();
		AcousticBands currentStoneRt60 =
				room.physicalRoom().eyringRt60Seconds(
						AcousticMaterials.STONE.surfaceAbsorption(),
						SOUND_SPEED_METERS_PER_SECOND
				);
		return String.format(
				Locale.ROOT,
				"{\"id\":\"%s\","
						+ "\"physical_dimensions_m\":%s,"
						+ "\"voxel_interior_cells\":%s,"
						+ "\"physical_volume_m3\":%.17g,"
						+ "\"physical_surface_area_m2\":%.17g,"
						+ "\"physical_diffuse_mfp_m\":%.17g,"
						+ "\"voxel_diffuse_mfp_m\":%.17g,"
						+ "\"probe_observed_mfp_m\":%.17g,"
						+ "\"probe_mfp_relative_error_vs_voxel\":%.17g,"
						+ "\"measured_rt60_s\":%s,"
						+ "\"effective_eyring_absorption\":%s,"
						+ "\"physical_analytic_reconstruction_rt60_s\":%s,"
						+ "\"voxel_analytic_rt60_s\":%s,"
						+ "\"voxel_probe_rt60_s\":%s,"
						+ "\"voxel_probe_relative_error_vs_measured\":%s,"
						+ "\"current_stone_absorption\":%s,"
						+ "\"physical_current_stone_analytic_rt60_s\":%s,"
						+ "\"current_stone_rt60_ratio_vs_measured\":%s,"
						+ "\"surface_hits\":%d,\"escaped_rays\":%d,"
						+ "\"truncated_legs\":%d}",
				escape(room.id()),
				dimensions(
						room.physicalRoom().lengthMeters(),
						room.physicalRoom().widthMeters(),
						room.physicalRoom().heightMeters()
				),
				integerDimensions(
						room.voxelLength(),
						room.voxelWidth(),
						room.voxelHeight()
				),
				room.physicalRoom().volumeCubicMeters(),
				room.physicalRoom().surfaceAreaSquareMeters(),
				physicalMfp,
				voxelMfp,
				observedMfp,
				relativeError(observedMfp, voxelMfp),
				bands(room.measuredRt60Seconds()),
				bands(result.absorption()),
				bands(result.analyticReconstruction()),
				bands(result.voxelAnalyticRt60()),
				bands(result.estimate().rt60Seconds()),
				bandsRelativeError(
						result.estimate().rt60Seconds(),
						room.measuredRt60Seconds()
				),
				bands(AcousticMaterials.STONE.surfaceAbsorption()),
				bands(currentStoneRt60),
				bandsRatio(
						currentStoneRt60,
						room.measuredRt60Seconds()
				),
				statistics.surfaceHits(),
				statistics.escapedRays(),
				statistics.truncatedLegs()
		);
	}

	private static String dimensions(double length, double width, double height) {
		return String.format(
				Locale.ROOT,
				"{\"length\":%.17g,\"width\":%.17g,\"height\":%.17g}",
				length,
				width,
				height
		);
	}

	private static String integerDimensions(int length, int width, int height) {
		return String.format(
				Locale.ROOT,
				"{\"length\":%d,\"width\":%d,\"height\":%d}",
				length,
				width,
				height
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

	private static String bandsRelativeError(
			AcousticBands actual,
			AcousticBands expected
	) {
		return bands(new AcousticBands(
				relativeError(actual.low(), expected.low()),
				relativeError(actual.mid(), expected.mid()),
				relativeError(actual.high(), expected.high())
		));
	}

	private static String bandsRatio(
			AcousticBands actual,
			AcousticBands expected
	) {
		return bands(new AcousticBands(
				actual.low() / expected.low(),
				actual.mid() / expected.mid(),
				actual.high() / expected.high()
		));
	}

	private static double maximumRelativeError(
			AcousticBands actual,
			AcousticBands expected
	) {
		return Math.max(
				relativeError(actual.low(), expected.low()),
				Math.max(
						relativeError(actual.mid(), expected.mid()),
						relativeError(actual.high(), expected.high())
				)
		);
	}

	private static double minimumRatio(
			AcousticBands actual,
			AcousticBands expected
	) {
		return Math.min(
				actual.low() / expected.low(),
				Math.min(
						actual.mid() / expected.mid(),
						actual.high() / expected.high()
				)
		);
	}

	private static double relativeError(double actual, double expected) {
		return Math.abs(actual - expected) / expected;
	}

	private static void writeAtomic(Path output, String text) throws IOException {
		Path parent = output.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
		Files.writeString(temporary, text, StandardCharsets.UTF_8);
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

	private record RoomCase(
			String id,
			ShoeboxRoomDecay physicalRoom,
			int voxelLength,
			int voxelWidth,
			int voxelHeight,
			AcousticBands measuredRt60Seconds
	) {
	}

	private record Result(
			RoomCase room,
			ShoeboxRoomDecay voxelRoom,
			AcousticBands absorption,
			AcousticBands analyticReconstruction,
			AcousticBands voxelAnalyticRt60,
			ReflectionStatistics statistics,
			LateReverbEstimator.Parameters estimate
	) {
	}
}
