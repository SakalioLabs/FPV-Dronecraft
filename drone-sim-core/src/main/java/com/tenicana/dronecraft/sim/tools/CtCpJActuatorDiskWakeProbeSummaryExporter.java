package com.tenicana.dronecraft.sim.tools;

import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJDimensionalRotorResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CtCpJActuatorDiskWakeProbeSummaryExporter {
	private static final String HEADER = String.join(",",
			"preset",
			"case",
			"row_kind",
			"rotor_index",
			"source_name",
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
			"reference_axial_mean_mps",
			"cfd_axial_mean_mps",
			"axial_residual_mean_mps",
			"axial_residual_rms_mps",
			"velocity_residual_rms_mps",
			"speed_residual_rms_mps",
			"cfd_transverse_mean_mps",
			"cfd_transverse_max_mps",
			"cfd_axial_0p5r_mps",
			"cfd_axial_1p0r_mps",
			"cfd_axial_2p0r_mps",
			"cfd_axial_4p0r_mps",
			"cfd_axial_4r_over_0p5r",
			"cfd_p_field_0p5r",
			"cfd_p_field_1p0r",
			"cfd_p_field_2p0r",
			"cfd_p_field_4p0r",
			"cfd_p_field_delta_4r_minus_0p5r",
			"probe_point_residual_max_m",
			"comparable",
			"message"
	);
	private static final double EPSILON = 1.0e-12;

	private CtCpJActuatorDiskWakeProbeSummaryExporter() {
	}

	public record SummaryRow(
			String presetName,
			String caseName,
			String rowKind,
			int rotorIndex,
			String sourceName,
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
			double referenceAxialMeanMetersPerSecond,
			double cfdAxialMeanMetersPerSecond,
			double axialResidualMeanMetersPerSecond,
			double axialResidualRootMeanSquareMetersPerSecond,
			double velocityResidualRootMeanSquareMetersPerSecond,
			double speedResidualRootMeanSquareMetersPerSecond,
			double cfdTransverseMeanMetersPerSecond,
			double cfdTransverseMaxMetersPerSecond,
			double cfdAxial0p5rMetersPerSecond,
			double cfdAxial1p0rMetersPerSecond,
			double cfdAxial2p0rMetersPerSecond,
			double cfdAxial4p0rMetersPerSecond,
			double cfdAxial4rOver0p5r,
			double cfdPField0p5r,
			double cfdPField1p0r,
			double cfdPField2p0r,
			double cfdPField4p0r,
			double cfdPFieldDelta4rMinus0p5r,
			double probePointResidualMaxMeters,
			boolean comparable,
			String message
	) {
	}

	public static void main(String[] args) throws IOException {
		if (args.length < 2 || args.length > 4) {
			throw new IllegalArgumentException(
					"usage: CtCpJActuatorDiskWakeProbeSummaryExporter "
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
		List<CtCpJActuatorDiskWakeProbeComparisonImporter.ComparisonRow> comparisons =
				CtCpJActuatorDiskWakeProbeComparisonImporter.compare(
						inputCsv,
						defaultAirDensityKgPerCubicMeter,
						defaultSourceThicknessMeters
				);
		Map<GroupKey, Accumulator> groups = new LinkedHashMap<>();
		for (CtCpJActuatorDiskWakeProbeComparisonImporter.ComparisonRow comparison : comparisons) {
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
			String sourceName
	) {
		static GroupKey from(CtCpJActuatorDiskWakeProbeComparisonImporter.ComparisonRow row) {
			CtCpJActuatorDiskWakeProbeComparisonImporter.ReferenceWakeProbeRow reference = row.reference();
			return new GroupKey(
					reference.presetName(),
					reference.caseName(),
					reference.rowKind(),
					reference.rotorIndex(),
					reference.sourceName()
			);
		}
	}

	private static final class Accumulator {
		private final GroupKey key;
		private final CtCpJActuatorDiskWakeProbeComparisonImporter.ReferenceWakeProbeRow first;
		private int totalSamples;
		private int comparableSamples;
		private final RunningStat referenceAxial = new RunningStat();
		private final RunningStat cfdAxial = new RunningStat();
		private final RunningStat axialResidual = new RunningStat();
		private final RunningSquares axialResidualSquares = new RunningSquares();
		private final RunningSquares velocityResidualSquares = new RunningSquares();
		private final RunningSquares speedResidualSquares = new RunningSquares();
		private final RunningStat cfdTransverse = new RunningStat();
		private double cfdTransverseMax = Double.NaN;
		private double cfdAxial0p5r = Double.NaN;
		private double cfdAxial1p0r = Double.NaN;
		private double cfdAxial2p0r = Double.NaN;
		private double cfdAxial4p0r = Double.NaN;
		private double cfdPField0p5r = Double.NaN;
		private double cfdPField1p0r = Double.NaN;
		private double cfdPField2p0r = Double.NaN;
		private double cfdPField4p0r = Double.NaN;
		private double probePointResidualMax = Double.NaN;

		Accumulator(
				GroupKey key,
				CtCpJActuatorDiskWakeProbeComparisonImporter.ComparisonRow firstRow
		) {
			this.key = key;
			this.first = firstRow.reference();
		}

		void add(CtCpJActuatorDiskWakeProbeComparisonImporter.ComparisonRow row) {
			totalSamples++;
			if (row.comparable()) {
				comparableSamples++;
			}
			referenceAxial.add(row.referenceExpectedAxialVelocityMetersPerSecond());
			cfdAxial.add(row.cfdProbeAxialVelocityMetersPerSecond());
			axialResidual.add(row.axialVelocityResidualMetersPerSecond());
			axialResidualSquares.add(row.axialVelocityResidualMetersPerSecond());
			velocityResidualSquares.add(row.probeVelocityResidualWorldMetersPerSecond().length());
			speedResidualSquares.add(row.speedResidualMetersPerSecond());
			cfdTransverse.add(row.cfdProbeTransverseVelocityMetersPerSecond());
			cfdTransverseMax = maxFinite(cfdTransverseMax, row.cfdProbeTransverseVelocityMetersPerSecond());
			probePointResidualMax = maxFinite(probePointResidualMax, row.probePointResidualWorldMeters().length());
			double distance = row.reference().probeDistanceRadius();
			if (Math.abs(distance - 0.5) <= EPSILON) {
				cfdAxial0p5r = row.cfdProbeAxialVelocityMetersPerSecond();
				cfdPField0p5r = row.cfd().cfdProbePField();
			} else if (Math.abs(distance - 1.0) <= EPSILON) {
				cfdAxial1p0r = row.cfdProbeAxialVelocityMetersPerSecond();
				cfdPField1p0r = row.cfd().cfdProbePField();
			} else if (Math.abs(distance - 2.0) <= EPSILON) {
				cfdAxial2p0r = row.cfdProbeAxialVelocityMetersPerSecond();
				cfdPField2p0r = row.cfd().cfdProbePField();
			} else if (Math.abs(distance - 4.0) <= EPSILON) {
				cfdAxial4p0r = row.cfdProbeAxialVelocityMetersPerSecond();
				cfdPField4p0r = row.cfd().cfdProbePField();
			}
		}

		SummaryRow summary() {
			boolean comparable = totalSamples > 0 && comparableSamples == totalSamples;
			return new SummaryRow(
					key.presetName(),
					key.caseName(),
					key.rowKind(),
					key.rotorIndex(),
					key.sourceName(),
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
					referenceAxial.mean(),
					cfdAxial.mean(),
					axialResidual.mean(),
					axialResidualSquares.rootMeanSquare(),
					velocityResidualSquares.rootMeanSquare(),
					speedResidualSquares.rootMeanSquare(),
					cfdTransverse.mean(),
					cfdTransverseMax,
					cfdAxial0p5r,
					cfdAxial1p0r,
					cfdAxial2p0r,
					cfdAxial4p0r,
					ratio(cfdAxial4p0r, cfdAxial0p5r),
					cfdPField0p5r,
					cfdPField1p0r,
					cfdPField2p0r,
					cfdPField4p0r,
					finiteDifference(cfdPField4p0r, cfdPField0p5r),
					probePointResidualMax,
					comparable,
					comparable
							? "ct-cp-j-actuator-disk-wake-probe-summary-ready"
							: "wake-probe-summary-has-non-comparable-samples"
			);
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
				number(row.referenceAxialMeanMetersPerSecond()),
				number(row.cfdAxialMeanMetersPerSecond()),
				number(row.axialResidualMeanMetersPerSecond()),
				number(row.axialResidualRootMeanSquareMetersPerSecond()),
				number(row.velocityResidualRootMeanSquareMetersPerSecond()),
				number(row.speedResidualRootMeanSquareMetersPerSecond()),
				number(row.cfdTransverseMeanMetersPerSecond()),
				number(row.cfdTransverseMaxMetersPerSecond()),
				number(row.cfdAxial0p5rMetersPerSecond()),
				number(row.cfdAxial1p0rMetersPerSecond()),
				number(row.cfdAxial2p0rMetersPerSecond()),
				number(row.cfdAxial4p0rMetersPerSecond()),
				number(row.cfdAxial4rOver0p5r()),
				number(row.cfdPField0p5r()),
				number(row.cfdPField1p0r()),
				number(row.cfdPField2p0r()),
				number(row.cfdPField4p0r()),
				number(row.cfdPFieldDelta4rMinus0p5r()),
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
