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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tests a physically scaled early/late transition across multiple shoebox
 * aspect ratios. This is a diagnostic, not a release mixing-time model.
 */
public final class PtbPathMixingTimeReferenceCli {
	private static final double SOUND_SPEED_METERS_PER_SECOND = 343.0;
	private static final int RAYS = 4_096;
	private static final int BOUNCES = 48;
	private static final double[] PATH_MULTIPLIERS =
			{0.0, 0.5, 1.0, 1.5, 2.0, 3.0, 4.0};
	private static final Room[] ROOMS = {
			new Room("low-square", 11, 11, 3),
			new Room("cube", 7, 7, 7),
			new Room("corridor", 21, 5, 3),
			new Room("hall", 15, 9, 5)
	};
	private static final AcousticMaterial STONE = material(
			"ptb-stone-candidate",
			0.02666666666666667,
			0.035,
			0.07,
			AcousticMaterials.STONE.scattering()
	);
	private static final AcousticMaterial WOOL = material(
			"ptb-wool-candidate",
			0.40,
			0.775,
			0.85,
			AcousticMaterials.SOFT.scattering()
	);
	private static final AcousticMaterial WOOD = material(
			"ptb-wood-candidate",
			0.12,
			0.09,
			0.10,
			AcousticMaterials.WOOD.scattering()
	);
	private static final AcousticMaterial GLASS = material(
			"ptb-glass-candidate",
			0.17666666666666667,
			0.06,
			0.03,
			AcousticMaterials.GLASS.scattering()
	);
	private static final AcousticMaterial[] MATERIALS =
			{STONE, WOOL, WOOD, GLASS};

	private PtbPathMixingTimeReferenceCli() {
	}

	public static void main(String[] arguments) throws IOException {
		if (arguments.length != 1) {
			throw new IllegalArgumentException(
					"usage: PtbPathMixingTimeReferenceCli <output-json>"
			);
		}
		Locale.setDefault(Locale.ROOT);
		List<Result> results = new ArrayList<>();
		for (Room room : ROOMS) {
			results.add(run(room, "configured", Double.POSITIVE_INFINITY));
			results.add(run(room, "fixed-two-hit", Double.NaN));
			for (double multiplier : PATH_MULTIPLIERS) {
				results.add(run(room, "path-scaled", multiplier));
			}
		}
		validate(results);
		Path output = Path.of(arguments[0]).toAbsolutePath().normalize();
		Files.createDirectories(output.getParent());
		Files.writeString(output, json(results), StandardCharsets.UTF_8);
		System.out.printf(
				Locale.ROOT,
				"{\"status\":\"valid-diagnostic\",\"rooms\":%d,"
						+ "\"runs\":%d,\"output\":\"%s\"}%n",
				ROOMS.length,
				results.size(),
				escape(output.toString())
		);
	}

	private static Result run(
			Room room,
			String mode,
			double pathMultiplier
	) {
		ShoeboxRoomDecay shoebox = room.shoebox();
		double diffuseMfp = shoebox.diffuseMeanFreePathMeters();
		double threshold = pathMultiplier * diffuseMfp;
		VoxelReflectionProbe.ReflectionDirectionPolicy policy =
				switch (mode) {
					case "configured" ->
							VoxelReflectionProbe.ReflectionDirectionPolicy
									.MATERIAL_SCATTERING;
					case "fixed-two-hit" ->
							(material, bounce, cumulativePathMeters) ->
									bounce < 2
											? material.scattering()
											: 1.0;
					case "path-scaled" ->
							(material, bounce, cumulativePathMeters) ->
									cumulativePathMeters >= threshold
											? 1.0
											: material.scattering();
					default -> throw new IllegalArgumentException("mode");
				};
		HitAudit audit = new HitAudit();
		ReflectionStatistics statistics = VoxelReflectionProbe.analyze(
				room.listener(),
				volume(room),
				new VoxelReflectionProbe.Config(
						RAYS,
						BOUNCES,
						160.0,
						384,
						1.0e-300
				),
				audit,
				policy
		);
		AcousticBands expectedAbsorption = areaMeanLogAbsorption(room);
		AcousticBands expectedRt60 = shoebox.eyringRt60Seconds(
				expectedAbsorption,
				SOUND_SPEED_METERS_PER_SECOND
		);
		AcousticBands actualRt60 = LateReverbEstimator.estimate(
				statistics,
				SOUND_SPEED_METERS_PER_SECOND
		).rt60Seconds();
		double[] expectedFractions = expectedFractions(room);
		double[] actualFractions = audit.fractions();
		double maximumFractionError = 0.0;
		for (int index = 0; index < MATERIALS.length; index++) {
			maximumFractionError = Math.max(
					maximumFractionError,
					Math.abs(
							actualFractions[index] - expectedFractions[index]
					)
			);
		}
		return new Result(
				room,
				mode,
				pathMultiplier,
				threshold,
				diffuseMfp,
				threshold / SOUND_SPEED_METERS_PER_SECOND,
				expectedRt60,
				actualRt60,
				maximumRelativeError(actualRt60, expectedRt60),
				statistics.meanFreePathMeters(),
				maximumFractionError,
				audit.axisMismatches,
				statistics.surfaceHits()
		);
	}

	private static void validate(List<Result> results) {
		int expected = ROOMS.length * (PATH_MULTIPLIERS.length + 2);
		if (results.size() != expected) {
			throw new IllegalStateException("mixing-time matrix incomplete");
		}
		for (Result result : results) {
			if (result.hits() != RAYS * BOUNCES) {
				throw new IllegalStateException("closed-room hit count changed");
			}
			if (result.axisMismatches() != 0) {
				throw new IllegalStateException("material axis mismatch");
			}
		}
	}

	private static ReflectionVolume volume(Room room) {
		int maximumX = room.length() + 2;
		int maximumY = room.height() + 2;
		int maximumZ = room.width() + 2;
		SparseMaterialSnapshot.Builder builder =
				SparseMaterialSnapshot.builder();
		for (int x = 0; x < maximumX; x++) {
			for (int y = 0; y < maximumY; y++) {
				for (int z = 0; z < maximumZ; z++) {
					AcousticMaterial material = null;
					if (y == 0) {
						material = STONE;
					} else if (y == maximumY - 1) {
						material = WOOL;
					} else if (x == 0 || x == maximumX - 1) {
						material = WOOD;
					} else if (z == 0 || z == maximumZ - 1) {
						material = GLASS;
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
				maximumX,
				maximumY,
				maximumZ,
				77L,
				true,
				builder.build()
		);
	}

	private static double[] expectedFractions(Room room) {
		double horizontal = room.length() * room.width();
		double xWalls = 2.0 * room.width() * room.height();
		double zWalls = 2.0 * room.length() * room.height();
		double total = room.shoebox().surfaceAreaSquareMeters();
		return new double[] {
				horizontal / total,
				horizontal / total,
				xWalls / total,
				zWalls / total
		};
	}

	private static AcousticBands areaMeanLogAbsorption(Room room) {
		double[] fractions = expectedFractions(room);
		return new AcousticBands(
				equivalent(fractions, Band.LOW),
				equivalent(fractions, Band.MID),
				equivalent(fractions, Band.HIGH)
		);
	}

	private static double equivalent(double[] fractions, Band band) {
		double logRetention = 0.0;
		for (int index = 0; index < MATERIALS.length; index++) {
			logRetention += fractions[index] * Math.log1p(
					-band.value(MATERIALS[index].surfaceAbsorption())
			);
		}
		return -Math.expm1(logRetention);
	}

	private static String json(List<Result> results) {
		return String.format(
				Locale.ROOT,
				"{%n"
						+ "  \"schema_version\": 1,%n"
						+ "  \"status\": \"valid-diagnostic\",%n"
						+ "  \"source_manifest_sha256\": "
						+ "\"8183526e5be701c6dd118579834084e1"
						+ "d2eac6f0b591845700ce66af55c474ef\",%n"
						+ "  \"ray_count\": %d,%n"
						+ "  \"maximum_bounces\": %d,%n"
						+ "  \"path_threshold_mfp_multipliers\": %s,%n"
						+ "  \"release_calibrated\": false,%n"
						+ "  \"claim_boundary\": \"Path thresholds are scaled "
						+ "by analytic 4V/S and tested only in closed voxel "
						+ "shoeboxes; they are not measured mixing times.\",%n"
						+ "  \"runs\": [%n    %s%n  ]%n"
						+ "}%n",
				RAYS,
				BOUNCES,
				array(PATH_MULTIPLIERS),
				String.join(
						",\n    ",
						results.stream().map(
								PtbPathMixingTimeReferenceCli::resultJson
						).toList()
				)
		);
	}

	private static String resultJson(Result result) {
		String multiplier = !Double.isFinite(result.pathMultiplier())
				? "null"
				: String.format(Locale.ROOT, "%.17g", result.pathMultiplier());
		String threshold = !Double.isFinite(result.thresholdMeters())
				? "null"
				: String.format(Locale.ROOT, "%.17g", result.thresholdMeters());
		String seconds = !Double.isFinite(result.thresholdSeconds())
				? "null"
				: String.format(Locale.ROOT, "%.17g", result.thresholdSeconds());
		return String.format(
				Locale.ROOT,
				"{\"room\":\"%s\",\"dimensions_m\":"
						+ "{\"length\":%d,\"width\":%d,\"height\":%d},"
						+ "\"mode\":\"%s\","
						+ "\"path_threshold_mfp_multiplier\":%s,"
						+ "\"path_threshold_m\":%s,"
						+ "\"path_threshold_s\":%s,"
						+ "\"analytic_mfp_m\":%.17g,"
						+ "\"probe_mfp_m\":%.17g,"
						+ "\"expected_rt60_s\":%s,"
						+ "\"probe_rt60_s\":%s,"
						+ "\"maximum_relative_error_vs_diffuse_formula\":"
						+ "%.17g,"
						+ "\"maximum_absolute_hit_fraction_error\":%.17g,"
						+ "\"normal_material_axis_mismatches\":%d,"
						+ "\"hits\":%d}",
				escape(result.room().id()),
				result.room().length(),
				result.room().width(),
				result.room().height(),
				escape(result.mode()),
				multiplier,
				threshold,
				seconds,
				result.analyticMfpMeters(),
				result.probeMfpMeters(),
				bands(result.expectedRt60()),
				bands(result.actualRt60()),
				result.maximumRelativeError(),
				result.maximumHitFractionError(),
				result.axisMismatches(),
				result.hits()
		);
	}

	private static AcousticMaterial material(
			String id,
			double low,
			double mid,
			double high,
			double scattering
	) {
		return new AcousticMaterial(
				id,
				AcousticBands.SILENT,
				new AcousticBands(low, mid, high),
				scattering
		);
	}

	private static double maximumRelativeError(
			AcousticBands actual,
			AcousticBands expected
	) {
		return Math.max(
				Math.abs(actual.low() - expected.low()) / expected.low(),
				Math.max(
						Math.abs(actual.mid() - expected.mid()) / expected.mid(),
						Math.abs(actual.high() - expected.high())
								/ expected.high()
				)
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

	private static String array(double[] values) {
		List<String> items = new ArrayList<>();
		for (double value : values) {
			items.add(String.format(Locale.ROOT, "%.17g", value));
		}
		return "[" + String.join(",", items) + "]";
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private enum Band {
		LOW {
			@Override
			double value(AcousticBands bands) {
				return bands.low();
			}
		},
		MID {
			@Override
			double value(AcousticBands bands) {
				return bands.mid();
			}
		},
		HIGH {
			@Override
			double value(AcousticBands bands) {
				return bands.high();
			}
		};

		abstract double value(AcousticBands bands);
	}

	private static final class HitAudit
			implements VoxelReflectionProbe.SurfaceHitObserver {
		private final long[] counts = new long[MATERIALS.length];
		private long hits;
		private long axisMismatches;

		@Override
		public void onHit(
				int rayIndex,
				int bounce,
				AcousticMaterial material,
				AcousticVector normal,
				double distanceMeters
		) {
			int index = materialIndex(material);
			counts[index]++;
			hits++;
			if (!axisMatches(index, normal)) {
				axisMismatches++;
			}
		}

		private double[] fractions() {
			double[] values = new double[counts.length];
			for (int index = 0; index < counts.length; index++) {
				values[index] = (double) counts[index] / hits;
			}
			return values;
		}
	}

	private static int materialIndex(AcousticMaterial material) {
		for (int index = 0; index < MATERIALS.length; index++) {
			if (MATERIALS[index].equals(material)) {
				return index;
			}
		}
		throw new IllegalArgumentException("unexpected material");
	}

	private static boolean axisMatches(
			int materialIndex,
			AcousticVector normal
	) {
		return switch (materialIndex) {
			case 0, 1 -> Math.abs(normal.y()) > 0.999;
			case 2 -> Math.abs(normal.x()) > 0.999;
			case 3 -> Math.abs(normal.z()) > 0.999;
			default -> throw new IllegalArgumentException("material index");
		};
	}

	private record Room(String id, int length, int width, int height) {
		private ShoeboxRoomDecay shoebox() {
			return new ShoeboxRoomDecay(length, width, height);
		}

		private AcousticVector listener() {
			return new AcousticVector(
					1.0 + length / 2.0,
					1.0 + height / 2.0,
					1.0 + width / 2.0
			);
		}
	}

	private record Result(
			Room room,
			String mode,
			double pathMultiplier,
			double thresholdMeters,
			double analyticMfpMeters,
			double thresholdSeconds,
			AcousticBands expectedRt60,
			AcousticBands actualRt60,
			double maximumRelativeError,
			double probeMfpMeters,
			double maximumHitFractionError,
			long axisMismatches,
			int hits
	) {
	}
}
