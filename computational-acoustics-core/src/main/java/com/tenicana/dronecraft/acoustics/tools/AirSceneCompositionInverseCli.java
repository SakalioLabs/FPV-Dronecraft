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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * D111 constrained scene-composition inverse followed by spatial voxel
 * holdout. The selected weights are explanatory surface mixtures, not an
 * identification of the furnishings in the measured AIR rooms.
 */
public final class AirSceneCompositionInverseCli {
	private static final double SOUND_SPEED_METERS_PER_SECOND = 343.0;
	private static final int GRID_DENOMINATOR = 200;
	private static final int LAYOUT_COUNT = 8;
	private static final String D110_SHA256 =
			"f6735684aad68eabcf1b12c2990357f66"
					+ "6f01d2191630732080c12eb523316f5";
	private static final String AIR_SHOEBOX_SHA256 =
			"4de568d12ac116ac2f58197fe9125ca18"
					+ "8ad5e2be3a4201c942e32e55953bb43";
	private static final String PTB_SHA256 =
			"8e506562d92ea131af8ee402736c9ce49"
					+ "8a7a3894acb883fa78637a1620d0bb9";
	private static final AcousticMaterial[] MATERIALS = {
			material(
					"ptb-stone",
					0.02666666666666667,
					0.035,
					0.07,
					AcousticMaterials.STONE.scattering()
			),
			material(
					"ptb-wood",
					0.12,
					0.09,
					0.10,
					AcousticMaterials.WOOD.scattering()
			),
			material(
					"ptb-wool",
					0.40,
					0.775,
					0.85,
					AcousticMaterials.SOFT.scattering()
			),
			material(
					"ptb-glass",
					0.17666666666666667,
					0.06,
					0.03,
					AcousticMaterials.GLASS.scattering()
			)
	};
	private static final List<RoomCase> ROOMS = List.of(
			new RoomCase(
					"air-booth",
					3,
					2,
					2,
					new AcousticBands(
							0.1268693589758476,
							0.13715287006894902,
							0.10821303407466658
					),
					new AcousticBands(
							0.3766449844102459,
							0.3541586048896441,
							0.425424509351638
					)
			),
			new RoomCase(
					"air-lecture",
					11,
					11,
					3,
					new AcousticBands(
							0.857164910011111,
							0.9030624220156351,
							0.6915336132873643
					),
					new AcousticBands(
							0.17079555900769963,
							0.16286483520119263,
							0.20717010229065597
					)
			)
	);
	private static final VoxelReflectionProbe.Config CONFIG =
			VoxelReflectionProbe.Config.researchBaseline();

	private AirSceneCompositionInverseCli() {
	}

	public static void main(String[] arguments) throws IOException {
		if (arguments.length != 1) {
			throw new IllegalArgumentException(
					"usage: AirSceneCompositionInverseCli <output-json>"
			);
		}
		Locale.setDefault(Locale.ROOT);
		List<RoomResult> results = new ArrayList<>();
		long candidateCount = 0;
		for (RoomCase room : ROOMS) {
			FitResult fit = fit(room.targetAbsorption());
			candidateCount = fit.candidateCount();
			results.add(holdout(room, fit));
		}
		validate(results);
		Path output = Path.of(arguments[0]).toAbsolutePath().normalize();
		writeAtomic(output, json(results, candidateCount));
		System.out.printf(
				Locale.ROOT,
				"{\"status\":\"valid-scene-composition-inverse\","
						+ "\"rooms\":%d,\"layouts_per_room\":%d,"
						+ "\"output\":\"%s\"}%n",
				results.size(),
				LAYOUT_COUNT,
				escape(output.toString())
		);
	}

	private static FitResult fit(AcousticBands target) {
		double bestMaximum = Double.POSITIVE_INFINITY;
		double bestSquared = Double.POSITIVE_INFINITY;
		double[] bestWeights = null;
		AcousticBands bestAbsorption = null;
		long count = 0;
		for (int stone = 0; stone <= GRID_DENOMINATOR; stone++) {
			for (int wood = 0;
					wood <= GRID_DENOMINATOR - stone;
					wood++) {
				for (int wool = 0;
						wool <= GRID_DENOMINATOR - stone - wood;
						wool++) {
					int glass =
							GRID_DENOMINATOR - stone - wood - wool;
					double[] weights = {
							(double) stone / GRID_DENOMINATOR,
							(double) wood / GRID_DENOMINATOR,
							(double) wool / GRID_DENOMINATOR,
							(double) glass / GRID_DENOMINATOR
					};
					AcousticBands predicted =
							meanLogAbsorption(weights);
					AcousticBands error =
							relativeError(predicted, target);
					double maximum = maximum(error);
					double squared = squared(error);
					count++;
					if (maximum < bestMaximum - 1.0e-15
							|| (Math.abs(maximum - bestMaximum)
							<= 1.0e-15
							&& squared < bestSquared - 1.0e-15)) {
						bestMaximum = maximum;
						bestSquared = squared;
						bestWeights = weights;
						bestAbsorption = predicted;
					}
				}
			}
		}
		if (bestWeights == null) {
			throw new IllegalStateException("composition grid is empty");
		}
		return new FitResult(
				bestWeights,
				bestAbsorption,
				relativeError(bestAbsorption, target),
				bestMaximum,
				count
		);
	}

	private static RoomResult holdout(RoomCase room, FitResult fit) {
		List<LayoutResult> layouts = new ArrayList<>();
		for (int layout = 0; layout < LAYOUT_COUNT; layout++) {
			VolumeResult volume = volume(room, fit.weights(), layout);
			ReflectionStatistics statistics = VoxelReflectionProbe.analyze(
					new AcousticVector(
							1.0 + room.length() * 0.5,
							1.0 + room.height() * 0.5,
							1.0 + room.width() * 0.5
					),
					volume.volume(),
					CONFIG
			);
			LateReverbEstimator.Parameters estimate =
					LateReverbEstimator.estimate(
							statistics,
							SOUND_SPEED_METERS_PER_SECOND
					);
			layouts.add(new LayoutResult(
					layout,
					volume.surfaceCellCounts(),
					statistics,
					estimate.rt60Seconds(),
					relativeError(
							estimate.rt60Seconds(),
							room.measuredRt60()
					)
			));
		}
		AcousticBands median = median(
				layouts.stream().map(LayoutResult::rt60).toList()
		);
		AcousticBands minimum = minimum(
				layouts.stream().map(LayoutResult::rt60).toList()
		);
		AcousticBands maximum = maximum(
				layouts.stream().map(LayoutResult::rt60).toList()
		);
		return new RoomResult(
				room,
				fit,
				layouts,
				median,
				minimum,
				maximum,
				relativeError(median, room.measuredRt60())
		);
	}

	private static VolumeResult volume(
			RoomCase room,
			double[] weights,
			int layout
	) {
		int maximumX = room.length() + 2;
		int maximumY = room.height() + 2;
		int maximumZ = room.width() + 2;
		List<int[]> surface = new ArrayList<>();
		for (int x = 0; x < maximumX; x++) {
			for (int y = 0; y < maximumY; y++) {
				for (int z = 0; z < maximumZ; z++) {
					if (x == 0 || x == maximumX - 1
							|| y == 0 || y == maximumY - 1
							|| z == 0 || z == maximumZ - 1) {
						surface.add(new int[]{x, y, z});
					}
				}
			}
		}
		int size = surface.size();
		int stride = coprimeStride(size);
		int phase = Math.floorMod(layout * 97, size);
		int[] counts = new int[MATERIALS.length];
		SparseMaterialSnapshot.Builder builder =
				SparseMaterialSnapshot.builder();
		for (int index = 0; index < size; index++) {
			double quantile = (
					Math.floorMod(index * stride + phase, size) + 0.5
			) / size;
			int materialIndex = choose(weights, quantile);
			counts[materialIndex]++;
			int[] cell = surface.get(index);
			builder.put(
					cell[0],
					cell[1],
					cell[2],
					DirectPathSolver.MaterialSample.full(
							MATERIALS[materialIndex]
					)
			);
		}
		return new VolumeResult(
				new ReflectionVolume(
						0,
						0,
						0,
						maximumX,
						maximumY,
						maximumZ,
						100 + layout,
						true,
						builder.build()
				),
				counts
		);
	}

	private static int coprimeStride(int size) {
		int candidate = Math.max(2, size / 2 + 1);
		while (gcd(candidate, size) != 1) {
			candidate++;
		}
		return candidate;
	}

	private static int gcd(int left, int right) {
		while (right != 0) {
			int next = left % right;
			left = right;
			right = next;
		}
		return Math.abs(left);
	}

	private static int choose(double[] weights, double quantile) {
		double cumulative = 0.0;
		for (int index = 0; index < weights.length; index++) {
			cumulative += weights[index];
			if (quantile < cumulative || index == weights.length - 1) {
				return index;
			}
		}
		throw new IllegalStateException("unreachable material selection");
	}

	private static AcousticBands meanLogAbsorption(double[] weights) {
		return new AcousticBands(
				equivalent(weights, Band.LOW),
				equivalent(weights, Band.MID),
				equivalent(weights, Band.HIGH)
		);
	}

	private static double equivalent(double[] weights, Band band) {
		double logRetention = 0.0;
		for (int index = 0; index < weights.length; index++) {
			logRetention += weights[index] * Math.log1p(
					-band.value(MATERIALS[index].surfaceAbsorption())
			);
		}
		return -Math.expm1(logRetention);
	}

	private static AcousticBands relativeError(
			AcousticBands actual,
			AcousticBands expected
	) {
		return new AcousticBands(
				Math.abs(actual.low() - expected.low()) / expected.low(),
				Math.abs(actual.mid() - expected.mid()) / expected.mid(),
				Math.abs(actual.high() - expected.high()) / expected.high()
		);
	}

	private static double squared(AcousticBands value) {
		return value.low() * value.low()
				+ value.mid() * value.mid()
				+ value.high() * value.high();
	}

	private static double maximum(AcousticBands value) {
		return Math.max(
				value.low(),
				Math.max(value.mid(), value.high())
		);
	}

	private static AcousticBands median(List<AcousticBands> values) {
		return new AcousticBands(
				median(values.stream().mapToDouble(AcousticBands::low).toArray()),
				median(values.stream().mapToDouble(AcousticBands::mid).toArray()),
				median(values.stream().mapToDouble(AcousticBands::high).toArray())
		);
	}

	private static double median(double[] values) {
		Arrays.sort(values);
		int middle = values.length / 2;
		return values.length % 2 == 0
				? (values[middle - 1] + values[middle]) * 0.5
				: values[middle];
	}

	private static AcousticBands minimum(List<AcousticBands> values) {
		return new AcousticBands(
				values.stream().mapToDouble(AcousticBands::low).min().orElseThrow(),
				values.stream().mapToDouble(AcousticBands::mid).min().orElseThrow(),
				values.stream().mapToDouble(AcousticBands::high).min().orElseThrow()
		);
	}

	private static AcousticBands maximum(List<AcousticBands> values) {
		return new AcousticBands(
				values.stream().mapToDouble(AcousticBands::low).max().orElseThrow(),
				values.stream().mapToDouble(AcousticBands::mid).max().orElseThrow(),
				values.stream().mapToDouble(AcousticBands::high).max().orElseThrow()
		);
	}

	private static void validate(List<RoomResult> results) {
		for (RoomResult result : results) {
			double weightSum = Arrays.stream(
					result.fit().weights()
			).sum();
			if (Math.abs(weightSum - 1.0) > 1.0e-12
					|| Arrays.stream(result.fit().weights())
					.anyMatch(value -> value < 0.0 || value > 1.0)) {
				throw new IllegalStateException("invalid simplex weights");
			}
			for (LayoutResult layout : result.layouts()) {
				if (!layout.statistics().complete()
						|| layout.statistics().escapedRays() != 0
						|| layout.statistics().truncatedLegs() != 0) {
					throw new IllegalStateException(
							"voxel layout probe is incomplete"
					);
				}
			}
		}
	}

	private static String json(
			List<RoomResult> results,
			long candidateCount
	) {
		return String.format(
				Locale.ROOT,
				"{%n"
						+ "  \"schema_version\":1,%n"
						+ "  \"status\":\"valid-scene-composition-inverse\",%n"
						+ "  \"source_d110_report_sha256\":\"%s\",%n"
						+ "  \"source_air_shoebox_sha256\":\"%s\",%n"
						+ "  \"source_ptb_material_sha256\":\"%s\",%n"
						+ "  \"material_order\":[\"stone\",\"wood\",\"wool\",\"glass\"],%n"
						+ "  \"material_absorption\":%s,%n"
						+ "  \"inverse_model\":\"simplex-grid-minimax-relative-absorption-error-mean-log-retention\",%n"
						+ "  \"grid_denominator\":%d,%n"
						+ "  \"candidates_per_room\":%d,%n"
						+ "  \"layouts_per_room\":%d,%n"
						+ "  \"ray_count\":%d,%n"
						+ "  \"maximum_bounces\":%d,%n"
						+ "  \"rooms\":[%n    %s%n  ],%n"
						+ "  \"production_change_required\":false,%n"
						+ "  \"physical_endpoint_opened\":false,%n"
						+ "  \"captures_audio\":false,%n"
						+ "  \"release_calibrated\":false,%n"
						+ "  \"claim_boundary\":\"Constrained PTB-prior surface mixtures are inverse explanations of AIR room-average absorption, followed by deterministic spatial voxel holdouts. They are not identified AIR furnishings, production presets, endpoint captures, perceptual validation, or release calibration.\"%n"
						+ "}%n",
				D110_SHA256,
				AIR_SHOEBOX_SHA256,
				PTB_SHA256,
				materialsJson(),
				GRID_DENOMINATOR,
				candidateCount,
				LAYOUT_COUNT,
				CONFIG.rayCount(),
				CONFIG.maximumBounces(),
				String.join(
						",\n    ",
						results.stream()
								.map(AirSceneCompositionInverseCli::roomJson)
								.toList()
				)
		);
	}

	private static String materialsJson() {
		List<String> values = new ArrayList<>();
		for (AcousticMaterial material : MATERIALS) {
			values.add(bands(material.surfaceAbsorption()));
		}
		return "[" + String.join(",", values) + "]";
	}

	private static String roomJson(RoomResult result) {
		List<String> layouts = result.layouts().stream()
				.map(AirSceneCompositionInverseCli::layoutJson)
				.toList();
		return String.format(
				Locale.ROOT,
				"{\"id\":\"%s\","
						+ "\"voxel_interior_cells\":{\"length\":%d,"
						+ "\"width\":%d,\"height\":%d},"
						+ "\"measured_rt60_s\":%s,"
						+ "\"target_effective_absorption\":%s,"
						+ "\"selected_weights\":%s,"
						+ "\"predicted_mean_log_absorption\":%s,"
						+ "\"inverse_relative_absorption_error\":%s,"
						+ "\"inverse_maximum_relative_error\":%.17g,"
						+ "\"voxel_median_rt60_s\":%s,"
						+ "\"voxel_minimum_rt60_s\":%s,"
						+ "\"voxel_maximum_rt60_s\":%s,"
						+ "\"voxel_median_relative_error\":%s,"
						+ "\"voxel_maximum_median_relative_error\":%.17g,"
						+ "\"layouts\":[%s]}",
				escape(result.room().id()),
				result.room().length(),
				result.room().width(),
				result.room().height(),
				bands(result.room().measuredRt60()),
				bands(result.room().targetAbsorption()),
				array(result.fit().weights()),
				bands(result.fit().predictedAbsorption()),
				bands(result.fit().relativeError()),
				result.fit().maximumRelativeError(),
				bands(result.medianRt60()),
				bands(result.minimumRt60()),
				bands(result.maximumRt60()),
				bands(result.medianRelativeError()),
				maximum(result.medianRelativeError()),
				String.join(",", layouts)
		);
	}

	private static String layoutJson(LayoutResult layout) {
		return String.format(
				Locale.ROOT,
				"{\"layout\":%d,\"surface_cell_counts\":%s,"
						+ "\"rt60_s\":%s,\"relative_error\":%s,"
						+ "\"surface_hits\":%d,\"escaped_rays\":%d,"
						+ "\"truncated_legs\":%d}",
				layout.layout(),
				integerArray(layout.surfaceCellCounts()),
				bands(layout.rt60()),
				bands(layout.relativeError()),
				layout.statistics().surfaceHits(),
				layout.statistics().escapedRays(),
				layout.statistics().truncatedLegs()
		);
	}

	private static String array(double[] values) {
		return String.format(
				Locale.ROOT,
				"[%.17g,%.17g,%.17g,%.17g]",
				values[0],
				values[1],
				values[2],
				values[3]
		);
	}

	private static String integerArray(int[] values) {
		return String.format(
				Locale.ROOT,
				"[%d,%d,%d,%d]",
				values[0],
				values[1],
				values[2],
				values[3]
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

	private static void writeAtomic(Path output, String text)
			throws IOException {
		Files.createDirectories(output.getParent());
		Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
		Files.writeString(temporary, text, StandardCharsets.UTF_8);
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

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private enum Band {
		LOW {
			@Override
			double value(AcousticBands value) {
				return value.low();
			}
		},
		MID {
			@Override
			double value(AcousticBands value) {
				return value.mid();
			}
		},
		HIGH {
			@Override
			double value(AcousticBands value) {
				return value.high();
			}
		};

		abstract double value(AcousticBands value);
	}

	private record RoomCase(
			String id,
			int length,
			int width,
			int height,
			AcousticBands measuredRt60,
			AcousticBands targetAbsorption
	) {
	}

	private record FitResult(
			double[] weights,
			AcousticBands predictedAbsorption,
			AcousticBands relativeError,
			double maximumRelativeError,
			long candidateCount
	) {
	}

	private record VolumeResult(
			ReflectionVolume volume,
			int[] surfaceCellCounts
	) {
	}

	private record LayoutResult(
			int layout,
			int[] surfaceCellCounts,
			ReflectionStatistics statistics,
			AcousticBands rt60,
			AcousticBands relativeError
	) {
	}

	private record RoomResult(
			RoomCase room,
			FitResult fit,
			List<LayoutResult> layouts,
			AcousticBands medianRt60,
			AcousticBands minimumRt60,
			AcousticBands maximumRt60,
			AcousticBands medianRelativeError
	) {
	}
}
