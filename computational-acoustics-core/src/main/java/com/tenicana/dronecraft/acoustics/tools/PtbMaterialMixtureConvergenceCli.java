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
 * Decomposes heterogeneous-room probe error across ray/bounce budgets and
 * audits material identity against the geometric hit normal.
 */
public final class PtbMaterialMixtureConvergenceCli {
	private static final int LENGTH = 11;
	private static final int WIDTH = 11;
	private static final int HEIGHT = 3;
	private static final double SOUND_SPEED_METERS_PER_SECOND = 343.0;
	private static final int[] RAY_COUNTS =
			{64, 128, 256, 512, 1_024, 2_048, 4_096};
	private static final int[] BOUNCE_COUNTS = {4, 8, 12, 24, 48};
	private static final ShoeboxRoomDecay ROOM =
			new ShoeboxRoomDecay(LENGTH, WIDTH, HEIGHT);
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
	private static final AcousticMaterial[] CONFIGURED_MATERIALS =
			{STONE, WOOL, WOOD, GLASS};
	private static final double[] EXPECTED_FRACTIONS = expectedFractions();
	private static final AcousticBands AREA_MEAN_LOG_ABSORPTION =
			areaMeanLogAbsorption();
	private static final AcousticBands AREA_MEAN_LOG_RT60 =
			ROOM.eyringRt60Seconds(
					AREA_MEAN_LOG_ABSORPTION,
					SOUND_SPEED_METERS_PER_SECOND
			);

	private PtbMaterialMixtureConvergenceCli() {
	}

	public static void main(String[] arguments) throws IOException {
		if (arguments.length < 1 || arguments.length > 2) {
			throw new IllegalArgumentException(
					"usage: PtbMaterialMixtureConvergenceCli "
							+ "<output-json> "
							+ "[configured|diffuse|late-diffuse-2]"
			);
		}
		Locale.setDefault(Locale.ROOT);
		String mode = arguments.length == 1 ? "configured" : arguments[1];
		AcousticMaterial[] materials = switch (mode) {
			case "configured" -> CONFIGURED_MATERIALS;
			case "diffuse" -> diffuseMaterials();
			case "late-diffuse-2" -> CONFIGURED_MATERIALS;
			default -> throw new IllegalArgumentException(
					"mode must be configured, diffuse or late-diffuse-2"
			);
		};
		VoxelReflectionProbe.ReflectionDirectionPolicy directionPolicy =
				switch (mode) {
					case "late-diffuse-2" ->
							(material, bounce, cumulativePathMeters) ->
									bounce < 2
									? material.scattering()
									: 1.0;
					default ->
							VoxelReflectionProbe.ReflectionDirectionPolicy
									.MATERIAL_SCATTERING;
				};
		ReflectionVolume volume = volume(materials);
		List<Result> results = new ArrayList<>();
		for (int bounces : BOUNCE_COUNTS) {
			for (int rays : RAY_COUNTS) {
				results.add(run(
						volume,
						materials,
						directionPolicy,
						rays,
						bounces
				));
			}
		}
		Result reference = results.get(results.size() - 1);
		validate(results, reference);
		String json = json(mode, materials, results, reference);
		Path output = Path.of(arguments[0]).toAbsolutePath().normalize();
		Files.createDirectories(output.getParent());
		Files.writeString(output, json, StandardCharsets.UTF_8);
		System.out.printf(
				Locale.ROOT,
				"{\"status\":\"valid-diagnostic\",\"runs\":%d,"
						+ "\"reference_rays\":%d,\"reference_bounces\":%d,"
						+ "\"output\":\"%s\"}%n",
				results.size(),
				reference.rays(),
				reference.bounces(),
				escape(output.toString())
		);
	}

	private static Result run(
			ReflectionVolume volume,
			AcousticMaterial[] materials,
			VoxelReflectionProbe.ReflectionDirectionPolicy directionPolicy,
			int rays,
			int bounces
	) {
		HitAudit audit = new HitAudit(materials);
		VoxelReflectionProbe.Config config =
				new VoxelReflectionProbe.Config(
						rays,
						bounces,
						96.0,
						256,
						1.0e-300
				);
		ReflectionStatistics statistics = VoxelReflectionProbe.analyze(
				new AcousticVector(6.5, 2.5, 6.5),
				volume,
				config,
				audit,
				directionPolicy
		);
		AcousticBands rt60 = LateReverbEstimator.estimate(
				statistics,
				SOUND_SPEED_METERS_PER_SECOND
		).rt60Seconds();
		double[] hitFractions = audit.fractions();
		double maximumFractionError = 0.0;
		for (int index = 0; index < materials.length; index++) {
			maximumFractionError = Math.max(
					maximumFractionError,
					Math.abs(
							hitFractions[index] - EXPECTED_FRACTIONS[index]
					)
			);
		}
		return new Result(
				rays,
				bounces,
				rt60,
				statistics.meanFreePathMeters(),
				audit.hits,
				hitFractions,
				maximumFractionError,
				audit.axisMismatches,
				(double) audit.axisMismatches / audit.hits
		);
	}

	private static void validate(List<Result> results, Result reference) {
		if (results.size() != RAY_COUNTS.length * BOUNCE_COUNTS.length) {
			throw new IllegalStateException("convergence matrix is incomplete");
		}
		for (Result result : results) {
			if (result.hits() != result.rays() * result.bounces()) {
				throw new IllegalStateException(
						"closed-room hit count changed"
				);
			}
		}
	}

	private static String json(
			String mode,
			AcousticMaterial[] materials,
			List<Result> results,
			Result reference
	) {
		List<String> rows = results.stream()
				.map(result -> resultJson(result, reference))
				.toList();
		return String.format(
				Locale.ROOT,
				"{%n"
						+ "  \"schema_version\": 1,%n"
						+ "  \"status\": \"valid-diagnostic\",%n"
						+ "  \"source_manifest_sha256\": "
						+ "\"8183526e5be701c6dd118579834084e1"
						+ "d2eac6f0b591845700ce66af55c474ef\",%n"
						+ "  \"release_calibrated\": false,%n"
						+ "  \"scattering_mode\": \"%s\",%n"
						+ "  \"surface_scattering\": %s,%n"
						+ "  \"room_interior_cells\": "
						+ "{\"length\":%d,\"width\":%d,\"height\":%d},%n"
						+ "  \"expected_diffuse_surface_hit_fractions\": %s,%n"
						+ "  \"area_mean_log_absorption\": %s,%n"
						+ "  \"area_mean_log_rt60_s\": %s,%n"
						+ "  \"convergence_reference\": "
						+ "{\"rays\":%d,\"bounces\":%d},%n"
						+ "  \"claim_boundary\": \"The largest budget is a "
						+ "numerical convergence reference, not measured room "
						+ "truth. The observer audits edge/corner material "
						+ "identity against each crossed face normal. "
						+ "late-diffuse-2 preserves material direction "
						+ "scattering for bounce indices 0 and 1 only.\",%n"
						+ "  \"runs\": [%n    %s%n  ]%n"
						+ "}%n",
				escape(mode),
				scatteringJson(materials),
				LENGTH,
				WIDTH,
				HEIGHT,
				fractionsJson(EXPECTED_FRACTIONS),
				bands(AREA_MEAN_LOG_ABSORPTION),
				bands(AREA_MEAN_LOG_RT60),
				reference.rays(),
				reference.bounces(),
				String.join(",\n    ", rows)
		);
	}

	private static String resultJson(Result result, Result reference) {
		return String.format(
				Locale.ROOT,
				"{\"rays\":%d,\"bounces\":%d,\"hits\":%d,"
						+ "\"rt60_s\":%s,"
						+ "\"relative_error_vs_area_mean_log\":%s,"
						+ "\"relative_error_vs_largest_budget\":%s,"
						+ "\"mean_free_path_m\":%.17g,"
						+ "\"hit_fractions\":%s,"
						+ "\"maximum_absolute_hit_fraction_error\":%.17g,"
						+ "\"normal_material_axis_mismatches\":%d,"
						+ "\"normal_material_axis_mismatch_fraction\":%.17g}",
				result.rays(),
				result.bounces(),
				result.hits(),
				bands(result.rt60()),
				bandsRelativeError(result.rt60(), AREA_MEAN_LOG_RT60),
				bandsRelativeError(result.rt60(), reference.rt60()),
				result.meanFreePathMeters(),
				fractionsJson(result.hitFractions()),
				result.maximumHitFractionError(),
				result.axisMismatches(),
				result.axisMismatchFraction()
		);
	}

	private static ReflectionVolume volume(AcousticMaterial[] materials) {
		int maximumX = LENGTH + 2;
		int maximumY = HEIGHT + 2;
		int maximumZ = WIDTH + 2;
		SparseMaterialSnapshot.Builder builder =
				SparseMaterialSnapshot.builder();
		for (int x = 0; x < maximumX; x++) {
			for (int y = 0; y < maximumY; y++) {
				for (int z = 0; z < maximumZ; z++) {
					AcousticMaterial material = null;
					if (y == 0) {
						material = materials[0];
					} else if (y == maximumY - 1) {
						material = materials[1];
					} else if (x == 0 || x == maximumX - 1) {
						material = materials[2];
					} else if (z == 0 || z == maximumZ - 1) {
						material = materials[3];
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
				73L,
				true,
				builder.build()
		);
	}

	private static double[] expectedFractions() {
		double horizontal = LENGTH * WIDTH;
		double wallPair = 2.0 * WIDTH * HEIGHT;
		double total = ROOM.surfaceAreaSquareMeters();
		return new double[] {
				horizontal / total,
				horizontal / total,
				wallPair / total,
				wallPair / total
		};
	}

	private static AcousticBands areaMeanLogAbsorption() {
		return new AcousticBands(
				equivalent(Band.LOW),
				equivalent(Band.MID),
				equivalent(Band.HIGH)
		);
	}

	private static double equivalent(Band band) {
		double logRetention = 0.0;
		for (int index = 0; index < CONFIGURED_MATERIALS.length; index++) {
			logRetention += EXPECTED_FRACTIONS[index] * Math.log1p(
					-band.value(
							CONFIGURED_MATERIALS[index].surfaceAbsorption()
					)
			);
		}
		return -Math.expm1(logRetention);
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

	private static AcousticMaterial[] diffuseMaterials() {
		AcousticMaterial[] diffuse =
				new AcousticMaterial[CONFIGURED_MATERIALS.length];
		for (int index = 0; index < diffuse.length; index++) {
			AcousticMaterial source = CONFIGURED_MATERIALS[index];
			diffuse[index] = new AcousticMaterial(
					source.id() + "-diffuse-control",
					source.transmissionLossDbPerMeter(),
					source.surfaceAbsorption(),
					1.0
			);
		}
		return diffuse;
	}

	private static int materialIndex(
			AcousticMaterial[] materials,
			AcousticMaterial material
	) {
		for (int index = 0; index < materials.length; index++) {
			if (materials[index].equals(material)) {
				return index;
			}
		}
		throw new IllegalArgumentException(
				"unexpected material " + material.id()
		);
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

	private static String fractionsJson(double[] fractions) {
		return String.format(
				Locale.ROOT,
				"{\"stone_floor\":%.17g,\"wool_ceiling\":%.17g,"
						+ "\"wood_x_walls\":%.17g,"
						+ "\"glass_z_walls\":%.17g}",
				fractions[0],
				fractions[1],
				fractions[2],
				fractions[3]
		);
	}

	private static String scatteringJson(AcousticMaterial[] materials) {
		return String.format(
				Locale.ROOT,
				"{\"stone_floor\":%.17g,\"wool_ceiling\":%.17g,"
						+ "\"wood_x_walls\":%.17g,"
						+ "\"glass_z_walls\":%.17g}",
				materials[0].scattering(),
				materials[1].scattering(),
				materials[2].scattering(),
				materials[3].scattering()
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

	private static double relativeError(double actual, double expected) {
		return Math.abs(actual - expected) / expected;
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
		private final AcousticMaterial[] materials;
		private final long[] counts;
		private long hits;
		private long axisMismatches;

		private HitAudit(AcousticMaterial[] materials) {
			this.materials = materials;
			counts = new long[materials.length];
		}

		@Override
		public void onHit(
				int rayIndex,
				int bounce,
				AcousticMaterial material,
				AcousticVector normal,
				double distanceMeters
		) {
			int index = materialIndex(materials, material);
			counts[index]++;
			hits++;
			if (!axisMatches(index, normal)) {
				axisMismatches++;
			}
		}

		private double[] fractions() {
			double[] result = new double[counts.length];
			for (int index = 0; index < counts.length; index++) {
				result[index] = (double) counts[index] / hits;
			}
			return result;
		}
	}

	private record Result(
			int rays,
			int bounces,
			AcousticBands rt60,
			double meanFreePathMeters,
			long hits,
			double[] hitFractions,
			double maximumHitFractionError,
			long axisMismatches,
			double axisMismatchFraction
	) {
	}
}
