package com.tenicana.dronecraft.sim.tools;

import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJDimensionalRotorResponse;
import com.tenicana.dronecraft.sim.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CtCpJActuatorDiskWakePlaneProbeSummaryExporter {
	private static final String HEADER = String.join(",",
			"preset",
			"case",
			"row_kind",
			"rotor_index",
			"source_name",
			"probe_distance_radius",
			"reference_lookup_status",
			"reference_clamped",
			"reference_blocked",
			"reference_source_enabled",
			"query_j",
			"query_rpm",
			"effective_j",
			"effective_rpm",
			"ct",
			"cp",
			"eta",
			"total_samples",
			"comparable_samples",
			"core_samples",
			"outer_samples",
			"core_reference_axial_mean_mps",
			"core_cfd_axial_mean_mps",
			"core_axial_residual_mean_mps",
			"core_axial_residual_rms_mps",
			"core_velocity_residual_rms_mps",
			"core_speed_residual_rms_mps",
			"core_transverse_cfd_mean_mps",
			"center_cfd_axial_mps",
			"edge_core_cfd_axial_mean_mps",
			"center_edge_axial_spread_mps",
			"outer_cfd_speed_max_mps",
			"outer_cfd_speed_mean_mps",
			"outer_leak_fraction_of_core_ref",
			"core_cfd_p_field_mean",
			"center_cfd_p_field",
			"edge_core_cfd_p_field_mean",
			"center_edge_cfd_p_field_delta",
			"outer_cfd_p_field_mean",
			"outer_cfd_p_field_max_abs",
			"core_outer_cfd_p_field_delta",
			"probe_point_residual_max_m",
			"comparable",
			"message"
	);
	private static final double EPSILON = 1.0e-12;

	private CtCpJActuatorDiskWakePlaneProbeSummaryExporter() {
	}

	public record SummaryRow(
			String presetName,
			String caseName,
			String rowKind,
			int rotorIndex,
			String sourceName,
			double probeDistanceRadius,
			String lookupStatus,
			boolean clamped,
			boolean blocked,
			boolean sourceEnabled,
			double queryJ,
			double queryRpm,
			double effectiveJ,
			double effectiveRpm,
			double ct,
			double cp,
			double eta,
			int totalSamples,
			int comparableSamples,
			int coreSamples,
			int outerSamples,
			double coreReferenceAxialMeanMetersPerSecond,
			double coreCfdAxialMeanMetersPerSecond,
			double coreAxialResidualMeanMetersPerSecond,
			double coreAxialResidualRootMeanSquareMetersPerSecond,
			double coreVelocityResidualRootMeanSquareMetersPerSecond,
			double coreSpeedResidualRootMeanSquareMetersPerSecond,
			double coreTransverseCfdMeanMetersPerSecond,
			double centerCfdAxialMetersPerSecond,
			double edgeCoreCfdAxialMeanMetersPerSecond,
			double centerEdgeAxialSpreadMetersPerSecond,
			double outerCfdSpeedMaxMetersPerSecond,
			double outerCfdSpeedMeanMetersPerSecond,
			double outerLeakFractionOfCoreReference,
			double coreCfdPFieldMean,
			double centerCfdPField,
			double edgeCoreCfdPFieldMean,
			double centerEdgeCfdPFieldDelta,
			double outerCfdPFieldMean,
			double outerCfdPFieldMaxAbs,
			double coreOuterCfdPFieldDelta,
			double probePointResidualMaxMeters,
			boolean comparable,
			String message
	) {
	}

	public static void main(String[] args) throws IOException {
		if (args.length < 2 || args.length > 4) {
			throw new IllegalArgumentException(
					"usage: CtCpJActuatorDiskWakePlaneProbeSummaryExporter "
							+ "<input.csv> <output.csv> [defaultDensityKgM3] [defaultSourceThicknessM]");
		}
		double defaultDensity = args.length >= 3 && !args[2].isBlank()
				? Double.parseDouble(args[2])
				: PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
		double defaultSourceThickness = args.length >= 4 && !args[3].isBlank()
				? Double.parseDouble(args[3])
				: CtCpJActuatorDiskSourceTermExporter.DEFAULT_SOURCE_THICKNESS_METERS;
		write(Path.of(args[0]), Path.of(args[1]), defaultDensity, defaultSourceThickness);
	}

	public static void write(
			Path input,
			Path output,
			double defaultAirDensityKgPerCubicMeter,
			double defaultSourceThicknessMeters
	) throws IOException {
		if (input == null || output == null) {
			throw new IllegalArgumentException("input and output paths must not be null.");
		}
		List<String> lines = csvLines(
				Files.readString(input, StandardCharsets.UTF_8),
				defaultAirDensityKgPerCubicMeter,
				defaultSourceThicknessMeters
		);
		Path parent = output.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.writeString(output, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
	}

	public static List<String> csvLines(
			String inputCsv,
			double defaultAirDensityKgPerCubicMeter,
			double defaultSourceThicknessMeters
	) {
		List<SummaryRow> rows = summarize(inputCsv, defaultAirDensityKgPerCubicMeter, defaultSourceThicknessMeters);
		List<String> lines = new ArrayList<>();
		lines.add(HEADER);
		for (SummaryRow row : rows) {
			lines.add(csvLine(row));
		}
		return List.copyOf(lines);
	}

	public static List<SummaryRow> summarize(
			String inputCsv,
			double defaultAirDensityKgPerCubicMeter,
			double defaultSourceThicknessMeters
	) {
		List<CtCpJActuatorDiskWakePlaneProbeComparisonImporter.ComparisonRow> comparisons =
				CtCpJActuatorDiskWakePlaneProbeComparisonImporter.compare(
						inputCsv,
						defaultAirDensityKgPerCubicMeter,
						defaultSourceThicknessMeters
				);
		Map<GroupKey, Accumulator> groups = new LinkedHashMap<>();
		for (CtCpJActuatorDiskWakePlaneProbeComparisonImporter.ComparisonRow comparison : comparisons) {
			GroupKey key = GroupKey.from(comparison);
			groups.computeIfAbsent(key, ignored -> new Accumulator(key, comparison)).add(comparison);
		}
		return groups.values().stream()
				.map(Accumulator::summary)
				.toList();
	}

	private record GroupKey(
			String presetName,
			String caseName,
			String rowKind,
			int rotorIndex,
			String sourceName,
			double probeDistanceRadius
	) {
		static GroupKey from(CtCpJActuatorDiskWakePlaneProbeComparisonImporter.ComparisonRow row) {
			CtCpJActuatorDiskWakePlaneProbeComparisonImporter.ReferenceWakePlaneProbeRow reference =
					row.reference();
			return new GroupKey(
					reference.presetName(),
					reference.caseName(),
					reference.rowKind(),
					reference.rotorIndex(),
					reference.sourceName(),
					reference.probeDistanceRadius()
			);
		}
	}

	private static final class Accumulator {
		private final GroupKey key;
		private final CtCpJActuatorDiskWakePlaneProbeComparisonImporter.ReferenceWakePlaneProbeRow first;
		private int totalSamples;
		private int comparableSamples;
		private int coreSamples;
		private int outerSamples;
		private final RunningStat coreReferenceAxial = new RunningStat();
		private final RunningStat coreCfdAxial = new RunningStat();
		private final RunningStat coreAxialResidual = new RunningStat();
		private final RunningSquares coreAxialResidualSquares = new RunningSquares();
		private final RunningSquares coreVelocityResidualSquares = new RunningSquares();
		private final RunningSquares coreSpeedResidualSquares = new RunningSquares();
		private final RunningStat coreTransverseCfd = new RunningStat();
		private final RunningStat edgeCoreCfdAxial = new RunningStat();
		private final RunningStat outerCfdSpeed = new RunningStat();
		private final RunningStat coreCfdPField = new RunningStat();
		private final RunningStat edgeCoreCfdPField = new RunningStat();
		private final RunningStat outerCfdPField = new RunningStat();
		private double centerCfdAxial = Double.NaN;
		private double centerCfdPField = Double.NaN;
		private double outerCfdSpeedMax = Double.NaN;
		private double outerCfdPFieldMaxAbs = Double.NaN;
		private double probePointResidualMax = Double.NaN;

		Accumulator(
				GroupKey key,
				CtCpJActuatorDiskWakePlaneProbeComparisonImporter.ComparisonRow firstRow
		) {
			this.key = key;
			this.first = firstRow.reference();
		}

		void add(CtCpJActuatorDiskWakePlaneProbeComparisonImporter.ComparisonRow row) {
			totalSamples++;
			if (row.comparable()) {
				comparableSamples++;
			}
			addMax(row.probePointResidualWorldMeters().length(), true);
			String region = row.reference().probeRegion();
			boolean core = "centerline".equals(region) || "wake_core_top_hat".equals(region);
			if (core) {
				coreSamples++;
				coreReferenceAxial.add(row.reference().expectedAxialVelocityMetersPerSecond());
				coreCfdAxial.add(row.cfdProbeAxialVelocityMetersPerSecond());
				coreAxialResidual.add(row.axialVelocityResidualMetersPerSecond());
				coreAxialResidualSquares.add(row.axialVelocityResidualMetersPerSecond());
				coreVelocityResidualSquares.add(row.probeVelocityResidualWorldMetersPerSecond().length());
				coreSpeedResidualSquares.add(row.speedResidualMetersPerSecond());
				coreTransverseCfd.add(row.cfdProbeTransverseVelocityMetersPerSecond());
				coreCfdPField.add(row.cfd().cfdProbePField());
				if ("centerline".equals(region)) {
					centerCfdAxial = row.cfdProbeAxialVelocityMetersPerSecond();
					centerCfdPField = row.cfd().cfdProbePField();
				} else {
					edgeCoreCfdAxial.add(row.cfdProbeAxialVelocityMetersPerSecond());
					edgeCoreCfdPField.add(row.cfd().cfdProbePField());
				}
			} else if ("outer_reference".equals(region)) {
				outerSamples++;
				outerCfdSpeed.add(row.cfdProbeSpeedMetersPerSecond());
				outerCfdSpeedMax = maxFinite(outerCfdSpeedMax, row.cfdProbeSpeedMetersPerSecond());
				outerCfdPField.add(row.cfd().cfdProbePField());
				outerCfdPFieldMaxAbs = maxFinite(outerCfdPFieldMaxAbs, Math.abs(row.cfd().cfdProbePField()));
			}
		}

		SummaryRow summary() {
			double coreReference = coreReferenceAxial.mean();
			double edgeMean = edgeCoreCfdAxial.mean();
			double coreP = coreCfdPField.mean();
			double edgeP = edgeCoreCfdPField.mean();
			double outerP = outerCfdPField.mean();
			double outerMax = outerCfdSpeedMax;
			boolean comparable = totalSamples > 0 && comparableSamples == totalSamples;
			return new SummaryRow(
					key.presetName(),
					key.caseName(),
					key.rowKind(),
					key.rotorIndex(),
					key.sourceName(),
					key.probeDistanceRadius(),
					first.lookupStatus(),
					first.clamped(),
					first.blocked(),
					first.sourceEnabled(),
					first.queryJ(),
					first.queryRpm(),
					first.effectiveJ(),
					first.effectiveRpm(),
					first.ct(),
					first.cp(),
					first.eta(),
					totalSamples,
					comparableSamples,
					coreSamples,
					outerSamples,
					coreReference,
					coreCfdAxial.mean(),
					coreAxialResidual.mean(),
					coreAxialResidualSquares.rootMeanSquare(),
					coreVelocityResidualSquares.rootMeanSquare(),
					coreSpeedResidualSquares.rootMeanSquare(),
					coreTransverseCfd.mean(),
					centerCfdAxial,
					edgeMean,
					centerCfdAxial - edgeMean,
					outerMax,
					outerCfdSpeed.mean(),
					ratio(outerMax, Math.abs(coreReference)),
					coreP,
					centerCfdPField,
					edgeP,
					finiteDifference(centerCfdPField, edgeP),
					outerP,
					outerCfdPFieldMaxAbs,
					finiteDifference(coreP, outerP),
					probePointResidualMax,
					comparable,
					comparable
							? "ct-cp-j-actuator-disk-wake-plane-summary-ready"
							: "wake-plane-summary-has-non-comparable-samples"
			);
		}

		private void addMax(double value, boolean probePoint) {
			if (!probePoint) {
				return;
			}
			probePointResidualMax = maxFinite(probePointResidualMax, value);
		}
	}

	private static final class RunningStat {
		private double sum;
		private int count;

		void add(double value) {
			if (Double.isFinite(value)) {
				sum += value;
				count++;
			}
		}

		double mean() {
			return count == 0 ? Double.NaN : sum / count;
		}
	}

	private static final class RunningSquares {
		private double sumSquares;
		private int count;

		void add(double value) {
			if (Double.isFinite(value)) {
				sumSquares += value * value;
				count++;
			}
		}

		double rootMeanSquare() {
			return count == 0 ? Double.NaN : Math.sqrt(sumSquares / count);
		}
	}

	private static String csvLine(SummaryRow row) {
		return String.join(",",
				escape(row.presetName()),
				escape(row.caseName()),
				escape(row.rowKind()),
				Integer.toString(row.rotorIndex()),
				escape(row.sourceName()),
				number(row.probeDistanceRadius()),
				escape(row.lookupStatus()),
				Boolean.toString(row.clamped()),
				Boolean.toString(row.blocked()),
				Boolean.toString(row.sourceEnabled()),
				number(row.queryJ()),
				number(row.queryRpm()),
				number(row.effectiveJ()),
				number(row.effectiveRpm()),
				number(row.ct()),
				number(row.cp()),
				number(row.eta()),
				Integer.toString(row.totalSamples()),
				Integer.toString(row.comparableSamples()),
				Integer.toString(row.coreSamples()),
				Integer.toString(row.outerSamples()),
				number(row.coreReferenceAxialMeanMetersPerSecond()),
				number(row.coreCfdAxialMeanMetersPerSecond()),
				number(row.coreAxialResidualMeanMetersPerSecond()),
				number(row.coreAxialResidualRootMeanSquareMetersPerSecond()),
				number(row.coreVelocityResidualRootMeanSquareMetersPerSecond()),
				number(row.coreSpeedResidualRootMeanSquareMetersPerSecond()),
				number(row.coreTransverseCfdMeanMetersPerSecond()),
				number(row.centerCfdAxialMetersPerSecond()),
				number(row.edgeCoreCfdAxialMeanMetersPerSecond()),
				number(row.centerEdgeAxialSpreadMetersPerSecond()),
				number(row.outerCfdSpeedMaxMetersPerSecond()),
				number(row.outerCfdSpeedMeanMetersPerSecond()),
				number(row.outerLeakFractionOfCoreReference()),
				number(row.coreCfdPFieldMean()),
				number(row.centerCfdPField()),
				number(row.edgeCoreCfdPFieldMean()),
				number(row.centerEdgeCfdPFieldDelta()),
				number(row.outerCfdPFieldMean()),
				number(row.outerCfdPFieldMaxAbs()),
				number(row.coreOuterCfdPFieldDelta()),
				number(row.probePointResidualMaxMeters()),
				Boolean.toString(row.comparable()),
				escape(row.message())
		);
	}

	private static double maxFinite(double current, double candidate) {
		if (!Double.isFinite(candidate)) {
			return current;
		}
		if (!Double.isFinite(current)) {
			return candidate;
		}
		return Math.max(current, candidate);
	}

	private static double finiteDifference(double first, double second) {
		return Double.isFinite(first) && Double.isFinite(second) ? first - second : Double.NaN;
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= EPSILON) {
			return Double.NaN;
		}
		return numerator / denominator;
	}

	private static String number(double value) {
		return Double.isFinite(value) ? String.format(Locale.ROOT, "%.15g", value) : "";
	}

	private static String escape(String value) {
		if (value == null) {
			return "";
		}
		if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}
}
