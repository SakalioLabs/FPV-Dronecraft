package com.tenicana.dronecraft.sim.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.tenicana.dronecraft.sim.UiucDa4002MeasuredRotorCrossValidation;
import com.tenicana.dronecraft.sim.UiucDa4002MeasuredRotorCrossValidation.BlockedCandidate;
import com.tenicana.dronecraft.sim.UiucDa4002MeasuredRotorCrossValidation.Report;
import com.tenicana.dronecraft.sim.UiucDa4002MeasuredRotorCrossValidation.ResidualSample;
import com.tenicana.dronecraft.sim.UiucDa4002MeasuredRotorCrossValidation.ResidualSummary;
import com.tenicana.dronecraft.sim.UiucDa4002MeasuredRotorCrossValidation.ZeroThrustBracket;

/** Exports reproducible internal interpolation residuals for the DA4002 surface. */
public final class UiucDa4002MeasuredRotorCrossValidationExporter {
	public static final String RESIDUAL_FILE_NAME = "residuals.csv";
	public static final String SUMMARY_FILE_NAME = "summary.md";
	private static final String RESIDUAL_HEADER = String.join(",",
			"data_source_id",
			"validation_kind",
			"propeller_id",
			"target_id",
			"target_rpm",
			"advance_ratio_j",
			"support_axis",
			"lower_support_coordinate",
			"upper_support_coordinate",
			"interpolation_fraction",
			"zero_thrust_bracket_neighbor",
			"measured_ct",
			"predicted_ct",
			"signed_ct_residual",
			"absolute_ct_residual",
			"relative_ct_residual_available",
			"signed_relative_ct_residual_fraction",
			"measured_cp",
			"predicted_cp",
			"signed_cp_residual",
			"absolute_cp_residual",
			"relative_cp_residual_available",
			"signed_relative_cp_residual_fraction",
			"measured_thrust_n",
			"predicted_thrust_n",
			"signed_thrust_residual_n",
			"measured_shaft_power_w",
			"predicted_shaft_power_w",
			"signed_shaft_power_residual_w",
			"measured_shaft_torque_nm",
			"predicted_shaft_torque_nm",
			"signed_shaft_torque_residual_nm"
	);

	private UiucDa4002MeasuredRotorCrossValidationExporter() {
	}

	public static void main(String[] args) throws IOException {
		Path outputDirectory = args.length >= 1 && !args[0].isBlank()
				? Path.of(args[0])
				: Path.of("build", "uiuc-da4002-cross-validation");
		write(outputDirectory);
	}

	public static void write(Path outputDirectory) throws IOException {
		if (outputDirectory == null) {
			throw new IllegalArgumentException("outputDirectory must not be null.");
		}
		Report report = UiucDa4002MeasuredRotorCrossValidation.analyze();
		Files.createDirectories(outputDirectory);
		Files.writeString(
				outputDirectory.resolve(RESIDUAL_FILE_NAME),
				String.join("\n", residualCsvLines(report)) + "\n",
				StandardCharsets.UTF_8
		);
		Files.writeString(
				outputDirectory.resolve(SUMMARY_FILE_NAME),
				String.join("\n", summaryMarkdownLines(report)) + "\n",
				StandardCharsets.UTF_8
		);
	}

	public static List<String> residualCsvLines(Report report) {
		if (report == null) {
			throw new IllegalArgumentException("report must not be null.");
		}
		List<String> lines = new ArrayList<>();
		lines.add(RESIDUAL_HEADER);
		for (ResidualSample sample : report.residualSamples()) {
			lines.add(residualCsvLine(sample));
		}
		return List.copyOf(lines);
	}

	public static List<String> summaryMarkdownLines(Report report) {
		if (report == null) {
			throw new IllegalArgumentException("report must not be null.");
		}
		List<ResidualSummary> aggregates = report.residualSummaries().stream()
				.filter(summary -> summary.targetId().equals(
						UiucDa4002MeasuredRotorCrossValidation.AGGREGATE_TARGET_ID))
				.toList();
		List<ResidualSummary> targets = report.residualSummaries().stream()
				.filter(summary -> !summary.targetId().equals(
						UiucDa4002MeasuredRotorCrossValidation.AGGREGATE_TARGET_ID))
				.toList();
		List<String> lines = new ArrayList<>();
		lines.add("# UIUC DA4002 Interpolation Cross-Validation");
		lines.add("");
		lines.add("This is internal interpolation validation, not independent aerodynamic validation.");
		lines.add("Every source-row prediction removes the target row before calling the production lookup. "
				+ "Every nominal-RPM prediction uses only the adjacent lower and upper tracks.");
		lines.add("Dimensional residuals use standard air density `"
				+ number(UiucDa4002MeasuredRotorCrossValidation
						.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER)
				+ " kg/m^3`.");
		lines.add("");
		lines.add("## Aggregate Coverage");
		lines.add("");
		lines.add("| Validation | Propeller | Candidates | Supported | Blocked | Zero-thrust neighbors |");
		lines.add("| --- | --- | ---: | ---: | ---: | ---: |");
		for (ResidualSummary summary : aggregates) {
			lines.add(String.format(Locale.ROOT,
					"| %s | %s | %d | %d | %d | %d |",
					summary.validationKind().name(),
					summary.propeller().id(),
					summary.candidateCount(),
					summary.supportedCount(),
					summary.blockedCount(),
					summary.zeroThrustBracketNeighborCount()));
		}
		lines.add("");
		lines.add("## Aggregate Coefficient Residuals");
		lines.add("");
		lines.add("| Validation | Propeller | Mean abs CT | Max abs CT | Mean abs CT rel | "
				+ "CT rel count | Mean abs CP | Max abs CP | Mean abs CP rel |");
		lines.add("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |");
		for (ResidualSummary summary : aggregates) {
			lines.add(String.format(Locale.ROOT,
					"| %s | %s | %s | %s | %s | %d | %s | %s | %s |",
					summary.validationKind().name(),
					summary.propeller().id(),
					number(summary.meanAbsoluteCtResidual()),
					number(summary.maximumAbsoluteCtResidual()),
					percent(summary.meanAbsoluteRelativeCtResidualFraction()),
					summary.relativeCtSampleCount(),
					number(summary.meanAbsoluteCpResidual()),
					number(summary.maximumAbsoluteCpResidual()),
					percent(summary.meanAbsoluteRelativeCpResidualFraction())));
		}
		lines.add("");
		lines.add("## Aggregate Dimensional Residuals");
		lines.add("");
		lines.add("| Validation | Propeller | Mean abs T (N) | Max abs T (N) | "
				+ "Mean abs P (W) | Max abs P (W) | Mean abs Q (Nm) | Max abs Q (Nm) |");
		lines.add("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |");
		for (ResidualSummary summary : aggregates) {
			lines.add(String.format(Locale.ROOT,
					"| %s | %s | %s | %s | %s | %s | %s | %s |",
					summary.validationKind().name(),
					summary.propeller().id(),
					number(summary.meanAbsoluteThrustResidualNewtons()),
					number(summary.maximumAbsoluteThrustResidualNewtons()),
					number(summary.meanAbsolutePowerResidualWatts()),
					number(summary.maximumAbsolutePowerResidualWatts()),
					number(summary.meanAbsoluteTorqueResidualNewtonMeters()),
					number(summary.maximumAbsoluteTorqueResidualNewtonMeters())));
		}
		lines.add("");
		lines.add("## Target Coverage");
		lines.add("");
		lines.add("| Validation | Propeller | Target | Candidates | Supported | Blocked |");
		lines.add("| --- | --- | --- | ---: | ---: | ---: |");
		for (ResidualSummary summary : targets) {
			lines.add(String.format(Locale.ROOT,
					"| %s | %s | `%s` | %d | %d | %d |",
					summary.validationKind().name(),
					summary.propeller().id(),
					summary.targetId(),
					summary.candidateCount(),
					summary.supportedCount(),
					summary.blockedCount()));
		}
		lines.add("");
		lines.add("## Blocked Candidates");
		lines.add("");
		lines.add("| Validation | Propeller | Target | RPM | J | Reason |");
		lines.add("| --- | --- | --- | ---: | ---: | --- |");
		for (BlockedCandidate candidate : report.blockedCandidates()) {
			lines.add(String.format(Locale.ROOT,
					"| %s | %s | `%s` | %s | %s | `%s` |",
					candidate.validationKind().name(),
					candidate.propeller().id(),
					candidate.targetId(),
					number(candidate.targetRpm()),
					number(candidate.advanceRatioJ()),
					candidate.reason()));
		}
		lines.add("");
		lines.add("## Measured Zero-Thrust Brackets");
		lines.add("");
		lines.add("| Propeller | Source curve | RPM | Lower J | Lower CT | Upper J | Upper CT | Linear J0 |");
		lines.add("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |");
		for (ZeroThrustBracket bracket : report.zeroThrustBrackets()) {
			lines.add(String.format(Locale.ROOT,
					"| %s | `%s` | %s | %s | %s | %s | %s | %s |",
					bracket.propeller().id(),
					bracket.sourceCurveId(),
					number(bracket.sourceRpm()),
					number(bracket.lowerAdvanceRatioJ()),
					number(bracket.lowerThrustCoefficientCt()),
					number(bracket.upperAdvanceRatioJ()),
					number(bracket.upperThrustCoefficientCt()),
					number(bracket.linearZeroThrustAdvanceRatioJ())));
		}
		lines.add("");
		lines.add("Relative CT residuals omit measured rows adjacent to a CT sign change; "
				+ "their absolute CT and dimensional residuals remain in every aggregate.");
		lines.add("Endpoint rows are not leave-one-out candidates because removing an endpoint "
				+ "would require extrapolation.");
		return List.copyOf(lines);
	}

	private static String residualCsvLine(ResidualSample sample) {
		return String.join(",",
				escape(UiucDa4002MeasuredRotorCrossValidation.DATA_SOURCE_ID),
				escape(sample.validationKind().name()),
				escape(sample.propeller().id()),
				escape(sample.targetId()),
				number(sample.targetRpm()),
				number(sample.advanceRatioJ()),
				escape(sample.supportAxis().name()),
				number(sample.lowerSupportCoordinate()),
				number(sample.upperSupportCoordinate()),
				number(sample.interpolationFraction()),
				Boolean.toString(sample.zeroThrustBracketNeighbor()),
				number(sample.measuredCoefficients().thrustCoefficientCt()),
				number(sample.predictedCoefficients().thrustCoefficientCt()),
				number(sample.signedCtResidual()),
				number(sample.absoluteCtResidual()),
				Boolean.toString(sample.relativeCtResidualAvailable()),
				sample.relativeCtResidualAvailable()
						? number(sample.signedRelativeCtResidualFraction()) : "",
				number(sample.measuredCoefficients().powerCoefficientCp()),
				number(sample.predictedCoefficients().powerCoefficientCp()),
				number(sample.signedCpResidual()),
				number(sample.absoluteCpResidual()),
				Boolean.toString(sample.relativeCpResidualAvailable()),
				sample.relativeCpResidualAvailable()
						? number(sample.signedRelativeCpResidualFraction()) : "",
				number(sample.measuredLoads().thrustNewtons()),
				number(sample.predictedLoads().thrustNewtons()),
				number(sample.signedThrustResidualNewtons()),
				number(sample.measuredLoads().shaftPowerWatts()),
				number(sample.predictedLoads().shaftPowerWatts()),
				number(sample.signedPowerResidualWatts()),
				number(sample.measuredLoads().shaftTorqueNewtonMeters()),
				number(sample.predictedLoads().shaftTorqueNewtonMeters()),
				number(sample.signedTorqueResidualNewtonMeters())
		);
	}

	private static String number(double value) {
		return String.format(Locale.ROOT, "%.15g", value);
	}

	private static String percent(double fraction) {
		return String.format(Locale.ROOT, "%.6g%%", fraction * 100.0);
	}

	private static String escape(String value) {
		String text = value == null ? "" : value;
		return '"' + text.replace("\"", "\"\"") + '"';
	}
}
