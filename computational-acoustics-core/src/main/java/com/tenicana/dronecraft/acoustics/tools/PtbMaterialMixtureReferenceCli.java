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
import java.util.List;
import java.util.Locale;

/**
 * Compares current [H] materials and PTB-derived research candidates in a
 * heterogeneous closed voxel room. This deliberately exposes the difference
 * between arithmetic mean absorption and the probe's mean-log path decay.
 */
public final class PtbMaterialMixtureReferenceCli {
	private static final double SOUND_SPEED_METERS_PER_SECOND = 343.0;
	private static final int LENGTH = 11;
	private static final int WIDTH = 11;
	private static final int HEIGHT = 3;
	private static final ShoeboxRoomDecay ROOM =
			new ShoeboxRoomDecay(LENGTH, WIDTH, HEIGHT);
	private static final VoxelReflectionProbe.Config CONFIG =
			VoxelReflectionProbe.Config.researchBaseline();
	private static final AcousticMaterial PTB_STONE = material(
			"ptb-stone-candidate",
			0.02666666666666667,
			0.035,
			0.07,
			AcousticMaterials.STONE.scattering()
	);
	private static final AcousticMaterial PTB_WOOD = material(
			"ptb-wood-candidate",
			0.12,
			0.09,
			0.10,
			AcousticMaterials.WOOD.scattering()
	);
	private static final AcousticMaterial PTB_GLASS = material(
			"ptb-glass-candidate",
			0.17666666666666667,
			0.06,
			0.03,
			AcousticMaterials.GLASS.scattering()
	);
	private static final AcousticMaterial PTB_WOOL = material(
			"ptb-wool-candidate",
			0.40,
			0.775,
			0.85,
			AcousticMaterials.SOFT.scattering()
	);

	private PtbMaterialMixtureReferenceCli() {
	}

	public static void main(String[] arguments) throws IOException {
		if (arguments.length != 1) {
			throw new IllegalArgumentException(
					"usage: PtbMaterialMixtureReferenceCli <output-json>"
			);
		}
		Locale.setDefault(Locale.ROOT);
		Result current = analyze(
				"current-hypotheses",
				new Materials(
						AcousticMaterials.STONE,
						AcousticMaterials.SOFT,
						AcousticMaterials.WOOD,
						AcousticMaterials.GLASS
				),
				1L
		);
		Result candidate = analyze(
				"ptb-research-candidates",
				new Materials(PTB_STONE, PTB_WOOL, PTB_WOOD, PTB_GLASS),
				2L
		);
		validate(current);
		validate(candidate);
		Path output = Path.of(arguments[0]).toAbsolutePath().normalize();
		writeAtomic(output, json(current, candidate));
		System.out.printf(
				Locale.ROOT,
				"{\"status\":\"valid-diagnostic\",\"schema\":1,"
						+ "\"output\":\"%s\"}%n",
				escape(output.toString())
		);
	}

	private static Result analyze(
			String id,
			Materials materials,
			long generation
	) {
		ReflectionStatistics statistics = VoxelReflectionProbe.analyze(
				new AcousticVector(6.5, 2.5, 6.5),
				volume(materials, generation),
				CONFIG
		);
		LateReverbEstimator.Parameters estimate =
				LateReverbEstimator.estimate(
						statistics,
						SOUND_SPEED_METERS_PER_SECOND
				);
		AcousticBands arithmetic = arithmeticMeanAbsorption(materials);
		AcousticBands meanLog = meanLogEquivalentAbsorption(materials);
		return new Result(
				id,
				materials,
				arithmetic,
				meanLog,
				ROOM.eyringRt60Seconds(
						arithmetic,
						SOUND_SPEED_METERS_PER_SECOND
				),
				ROOM.eyringRt60Seconds(
						meanLog,
						SOUND_SPEED_METERS_PER_SECOND
				),
				statistics,
				estimate
		);
	}

	private static AcousticBands arithmeticMeanAbsorption(
			Materials materials
	) {
		double floorArea = LENGTH * WIDTH;
		double wallArea = 2.0 * WIDTH * HEIGHT;
		double total = ROOM.surfaceAreaSquareMeters();
		return materials.floor().surfaceAbsorption().multiply(floorArea)
				.add(materials.ceiling().surfaceAbsorption().multiply(floorArea))
				.add(materials.xWalls().surfaceAbsorption().multiply(wallArea))
				.add(materials.zWalls().surfaceAbsorption().multiply(wallArea))
				.multiply(1.0 / total);
	}

	private static AcousticBands meanLogEquivalentAbsorption(
			Materials materials
	) {
		double floorArea = LENGTH * WIDTH;
		double wallArea = 2.0 * WIDTH * HEIGHT;
		double total = ROOM.surfaceAreaSquareMeters();
		List<WeightedMaterial> weighted = List.of(
				new WeightedMaterial(materials.floor(), floorArea),
				new WeightedMaterial(materials.ceiling(), floorArea),
				new WeightedMaterial(materials.xWalls(), wallArea),
				new WeightedMaterial(materials.zWalls(), wallArea)
		);
		return new AcousticBands(
				equivalent(weighted, total, Band.LOW),
				equivalent(weighted, total, Band.MID),
				equivalent(weighted, total, Band.HIGH)
		);
	}

	private static double equivalent(
			List<WeightedMaterial> materials,
			double totalArea,
			Band band
	) {
		double logRetention = 0.0;
		for (WeightedMaterial item : materials) {
			double absorption = band.value(
					item.material().surfaceAbsorption()
			);
			logRetention += item.area() * Math.log1p(-absorption);
		}
		return -Math.expm1(logRetention / totalArea);
	}

	private static ReflectionVolume volume(
			Materials materials,
			long generation
	) {
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
						material = materials.floor();
					} else if (y == maximumY - 1) {
						material = materials.ceiling();
					} else if (x == 0 || x == maximumX - 1) {
						material = materials.xWalls();
					} else if (z == 0 || z == maximumZ - 1) {
						material = materials.zWalls();
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
				generation,
				true,
				builder.build()
		);
	}

	private static String json(Result current, Result candidate) {
		return String.format(
				Locale.ROOT,
				"{%n"
						+ "  \"schema_version\": 1,%n"
						+ "  \"status\": \"valid-diagnostic\",%n"
						+ "  \"source_manifest_sha256\": "
						+ "\"8183526e5be701c6dd118579834084e1"
						+ "d2eac6f0b591845700ce66af55c474ef\",%n"
						+ "  \"room_interior_cells\": "
						+ "{\"length\":%d,\"width\":%d,\"height\":%d},%n"
						+ "  \"ray_count\": %d,%n"
						+ "  \"maximum_bounces\": %d,%n"
						+ "  \"release_calibrated\": false,%n"
						+ "  \"claim_boundary\": "
						+ "\"PTB-selected absorption-only diagnostic; "
						+ "scattering remains current [H], and voxel edge "
						+ "cells approximate intersecting surfaces.\",%n"
						+ "  \"cases\": [%n    %s,%n    %s%n  ]%n"
						+ "}%n",
				LENGTH,
				WIDTH,
				HEIGHT,
				CONFIG.rayCount(),
				CONFIG.maximumBounces(),
				resultJson(current),
				resultJson(candidate)
		);
	}

	private static String resultJson(Result result) {
		AcousticBands probe = result.estimate().rt60Seconds();
		return String.format(
				Locale.ROOT,
				"{\"id\":\"%s\","
						+ "\"surface_absorption\":{"
						+ "\"floor\":%s,\"ceiling\":%s,"
						+ "\"x_walls\":%s,\"z_walls\":%s},"
						+ "\"arithmetic_mean_absorption\":%s,"
						+ "\"mean_log_equivalent_absorption\":%s,"
						+ "\"arithmetic_eyring_rt60_s\":%s,"
						+ "\"mean_log_eyring_rt60_s\":%s,"
						+ "\"voxel_probe_rt60_s\":%s,"
						+ "\"voxel_relative_error_vs_arithmetic\":%s,"
						+ "\"voxel_relative_error_vs_mean_log\":%s,"
						+ "\"surface_hits\":%d,\"truncated_legs\":%d}",
				escape(result.id()),
				bands(result.materials().floor().surfaceAbsorption()),
				bands(result.materials().ceiling().surfaceAbsorption()),
				bands(result.materials().xWalls().surfaceAbsorption()),
				bands(result.materials().zWalls().surfaceAbsorption()),
				bands(result.arithmeticAbsorption()),
				bands(result.meanLogAbsorption()),
				bands(result.arithmeticRt60()),
				bands(result.meanLogRt60()),
				bands(probe),
				bandsRelativeError(probe, result.arithmeticRt60()),
				bandsRelativeError(probe, result.meanLogRt60()),
				result.statistics().surfaceHits(),
				result.statistics().truncatedLegs()
		);
	}

	private static void validate(Result result) {
		if (!result.statistics().complete()) {
			throw new IllegalStateException("mixed-room probe was truncated");
		}
		if (result.statistics().escapedRays() != 0) {
			throw new IllegalStateException("mixed-room probe escaped");
		}
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

	private static void writeAtomic(Path output, String json)
			throws IOException {
		Files.createDirectories(output.getParent());
		Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
		Files.writeString(temporary, json, StandardCharsets.UTF_8);
		try {
			Files.move(
					temporary,
					output,
					StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING
			);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(
					temporary,
					output,
					StandardCopyOption.REPLACE_EXISTING
			);
		}
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

	private record WeightedMaterial(AcousticMaterial material, double area) {
	}

	private record Materials(
			AcousticMaterial floor,
			AcousticMaterial ceiling,
			AcousticMaterial xWalls,
			AcousticMaterial zWalls
	) {
	}

	private record Result(
			String id,
			Materials materials,
			AcousticBands arithmeticAbsorption,
			AcousticBands meanLogAbsorption,
			AcousticBands arithmeticRt60,
			AcousticBands meanLogRt60,
			ReflectionStatistics statistics,
			LateReverbEstimator.Parameters estimate
	) {
	}
}
