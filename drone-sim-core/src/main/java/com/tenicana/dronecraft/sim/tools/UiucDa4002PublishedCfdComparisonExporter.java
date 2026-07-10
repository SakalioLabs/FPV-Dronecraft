package com.tenicana.dronecraft.sim.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.tenicana.dronecraft.sim.UiucDa4002PublishedCfdComparison;
import com.tenicana.dronecraft.sim.UiucDa4002PublishedCfdComparison.CfxComparison;
import com.tenicana.dronecraft.sim.UiucDa4002PublishedCfdComparison.CfxTurbulenceModel;
import com.tenicana.dronecraft.sim.UiucDa4002PublishedCfdComparison.OpenFoamSummaryComparison;
import com.tenicana.dronecraft.sim.UiucDa4002PublishedCfdComparison.Report;

/** Exports the measured DA4002 surface beside published CFD evidence. */
public final class UiucDa4002PublishedCfdComparisonExporter {
	public static final String COMPARISON_FILE_NAME = "cfx-comparison.csv";
	public static final String SUMMARY_FILE_NAME = "summary.md";
	private static final String COMPARISON_HEADER = String.join(",",
			"data_source_id",
			"source_locator",
			"turbulence_model",
			"rpm",
			"diameter_m",
			"comparison_density_kg_m3",
			"advance_ratio_j",
			"published_freestream_m_s",
			"expected_freestream_m_s",
			"freestream_residual_m_s",
			"published_thrust_n",
			"published_torque_nm",
			"published_kt",
			"published_kp",
			"published_eta",
			"published_eta_closure_residual",
			"inferred_density_from_thrust_kg_m3",
			"inferred_density_from_torque_kg_m3",
			"source_density_closure_fraction",
			"torque_coefficient_closure_satisfied",
			"coefficient_normalized_thrust_n",
			"coefficient_normalized_power_w",
			"coefficient_normalized_torque_nm",
			"measured_model_ct",
			"measured_model_cp",
			"measured_model_eta",
			"measured_model_thrust_n",
			"measured_model_power_w",
			"measured_model_torque_nm",
			"model_minus_published_ct",
			"model_minus_published_cp",
			"model_minus_published_eta",
			"model_minus_normalized_thrust_n",
			"model_minus_normalized_power_w",
			"model_minus_normalized_torque_nm",
			"model_minus_raw_thrust_n",
			"model_minus_raw_torque_nm",
			"interpolation_status",
			"lower_source_curve_id",
			"upper_source_curve_id"
	);

	private UiucDa4002PublishedCfdComparisonExporter() {
	}

	public static void main(String[] args) throws IOException {
		Path outputDirectory = args.length >= 1 && !args[0].isBlank()
				? Path.of(args[0])
				: Path.of("build", "uiuc-da4002-published-cfd-comparison");
		write(outputDirectory);
	}

	public static void write(Path outputDirectory) throws IOException {
		if (outputDirectory == null) {
			throw new IllegalArgumentException("outputDirectory must not be null.");
		}
		Report report = UiucDa4002PublishedCfdComparison.compare();
		Files.createDirectories(outputDirectory);
		Files.writeString(
				outputDirectory.resolve(COMPARISON_FILE_NAME),
				String.join("\n", comparisonCsvLines(report)) + "\n",
				StandardCharsets.UTF_8
		);
		Files.writeString(
				outputDirectory.resolve(SUMMARY_FILE_NAME),
				String.join("\n", summaryMarkdownLines(report)) + "\n",
				StandardCharsets.UTF_8
		);
	}

	public static List<String> comparisonCsvLines(Report report) {
		if (report == null) {
			throw new IllegalArgumentException("report must not be null.");
		}
		List<String> lines = new ArrayList<>();
		lines.add(COMPARISON_HEADER);
		for (CfxComparison comparison : report.cfxComparisons()) {
			lines.add(comparisonCsvLine(comparison));
		}
		return List.copyOf(lines);
	}

	public static List<String> summaryMarkdownLines(Report report) {
		if (report == null) {
			throw new IllegalArgumentException("report must not be null.");
		}
		long closedRows = report.cfxComparisons().stream()
				.filter(CfxComparison::publishedTorqueCoefficientClosureSatisfied)
				.count();
		double maximumFreestreamResidual = report.cfxComparisons().stream()
				.mapToDouble(comparison -> Math.abs(
						comparison.publishedFreestreamClosureResidualMetersPerSecond()))
				.max()
				.orElseThrow();
		List<String> lines = new ArrayList<>();
		lines.add("# UIUC DA4002 Published CFD Comparison");
		lines.add("");
		lines.add("Published source: [" + UiucDa4002PublishedCfdComparison.SOURCE_TITLE
				+ "](" + UiucDa4002PublishedCfdComparison.SOURCE_URL + ").");
		lines.add("Source PDF SHA-256: `"
				+ UiucDa4002PublishedCfdComparison.SOURCE_PDF_SHA256 + "`.");
		lines.add("The source PDF is not vendored. Table values are transcribed at their "
				+ "printed precision and are not silently corrected.");
		lines.add("");
		lines.add("## Comparison Basis");
		lines.add("");
		lines.add("- Propeller: DA4002 9x6.75, diameter `"
				+ number(UiucDa4002PublishedCfdComparison.SOURCE_DIAMETER_METERS)
				+ " m`, speed `"
				+ number(UiucDa4002PublishedCfdComparison.SOURCE_RPM) + " RPM`.");
		lines.add("- Definitions: `J = V/(nD)`, `KT = T/(rho n^2 D^4)`, "
				+ "`KP = P/(rho n^3 D^5)`, and `eta = J KT/KP`.");
		lines.add("- CFX rows: `" + UiucDa4002PublishedCfdComparison.CFX_SOURCE_LOCATOR
				+ "`; OpenFOAM anchors: `"
				+ UiucDa4002PublishedCfdComparison.OPENFOAM_SOURCE_LOCATOR + "`.");
		lines.add("- Dimensional residuals normalize both coefficient surfaces to `"
				+ number(UiucDa4002PublishedCfdComparison
						.INFERRED_AIR_DENSITY_KG_PER_CUBIC_METER)
				+ " kg/m^3`, rounded from the internally consistent published-load "
				+ "median `" + number(UiucDa4002PublishedCfdComparison
						.consistentPublishedDensityMedianKgPerCubicMeter()) + " kg/m^3`.");
		lines.add("");
		lines.add("## Source Consistency");
		lines.add("");
		lines.add("- " + closedRows + " of " + report.cfxComparisons().size()
				+ " CFX rows close printed torque against printed KP within `"
				+ percent(UiucDa4002PublishedCfdComparison
						.SOURCE_LOAD_CLOSURE_RELATIVE_TOLERANCE) + "`.");
		lines.add("- The k-omega `J=0.2` row prints `0.076 Nm`; it is retained, "
				+ "flagged as inconsistent, and not corrected.");
		lines.add("- Maximum printed freestream closure residual against `J n D` is `"
				+ number(maximumFreestreamResidual) + " m/s`.");
		lines.add("");
		lines.add("## CFX Coefficient Residuals");
		lines.add("");
		lines.add("| CFX model | Rows | Mean abs CT | Max abs CT | Mean abs CP | "
				+ "Max abs CP | Mean abs eta |");
		lines.add("| --- | ---: | ---: | ---: | ---: | ---: | ---: |");
		for (CfxTurbulenceModel model : CfxTurbulenceModel.values()) {
			Aggregate aggregate = aggregate(report, model);
			lines.add(String.format(Locale.ROOT,
					"| %s | %d | %s | %s | %s | %s | %s |",
					model.name(),
					aggregate.count(),
					number(aggregate.meanAbsoluteCt()),
					number(aggregate.maximumAbsoluteCt()),
					number(aggregate.meanAbsoluteCp()),
					number(aggregate.maximumAbsoluteCp()),
					number(aggregate.meanAbsoluteEta())));
		}
		lines.add("");
		lines.add("## Density-Normalized Dimensional Residuals");
		lines.add("");
		lines.add("| CFX model | Mean abs T (N) | Max abs T (N) | Mean abs P (W) | "
				+ "Max abs P (W) | Mean abs Q (Nm) | Max abs Q (Nm) |");
		lines.add("| --- | ---: | ---: | ---: | ---: | ---: | ---: |");
		for (CfxTurbulenceModel model : CfxTurbulenceModel.values()) {
			Aggregate aggregate = aggregate(report, model);
			lines.add(String.format(Locale.ROOT,
					"| %s | %s | %s | %s | %s | %s | %s |",
					model.name(),
					number(aggregate.meanAbsoluteThrustNewtons()),
					number(aggregate.maximumAbsoluteThrustNewtons()),
					number(aggregate.meanAbsolutePowerWatts()),
					number(aggregate.maximumAbsolutePowerWatts()),
					number(aggregate.meanAbsoluteTorqueNewtonMeters()),
					number(aggregate.maximumAbsoluteTorqueNewtonMeters())));
		}
		OpenFoamSummaryComparison openFoam = report.openFoamSummaryComparison();
		lines.add("");
		lines.add("## OpenFOAM Rounded Summary Anchors");
		lines.add("");
		lines.add("Table 17 reports rounded OpenFOAM summary anchors, not a tabulated "
				+ "point-by-point curve.");
		lines.add("");
		lines.add("| Quantity | Published | Measured model | Model - published |");
		lines.add("| --- | ---: | ---: | ---: |");
		lines.add(row("Static CT",
				openFoam.publishedStaticThrustCoefficientKt(),
				openFoam.measuredStaticModel().lookup().thrustCoefficientCt(),
				openFoam.modelMinusPublishedStaticCt()));
		lines.add(row("Static CP",
				openFoam.publishedStaticPowerCoefficientKp(),
				openFoam.measuredStaticModel().lookup().powerCoefficientCp(),
				openFoam.modelMinusPublishedStaticCp()));
		lines.add(row("Maximum eta at J="
				+ number(openFoam.publishedMaximumEfficiencyAdvanceRatioJ()),
				openFoam.publishedMaximumEfficiency(),
				openFoam.measuredEfficiencyModel().lookup().propulsiveEfficiencyEta(),
				openFoam.modelMinusPublishedMaximumEfficiency()));
		lines.add("");
		lines.add("## Scope");
		lines.add("");
		lines.add("This is independent numerical comparison evidence, not a calibration "
				+ "target or proof that either turbulence model is ground truth.");
		lines.add("Coverage is limited to the DA4002 9x6.75 at 2000 RPM, axial flow, "
				+ "and `0 <= J <= 0.6`; it does not validate the DA4002 5x3.75 surface.");
		return List.copyOf(lines);
	}

	private static String comparisonCsvLine(CfxComparison comparison) {
		var published = comparison.published();
		var measured = comparison.measuredModel();
		var normalized = comparison.publishedCoefficientLoads();
		return String.join(",",
				escape(UiucDa4002PublishedCfdComparison.DATA_SOURCE_ID),
				escape(UiucDa4002PublishedCfdComparison.CFX_SOURCE_LOCATOR),
				escape(published.turbulenceModel().name()),
				number(UiucDa4002PublishedCfdComparison.SOURCE_RPM),
				number(UiucDa4002PublishedCfdComparison.SOURCE_DIAMETER_METERS),
				number(UiucDa4002PublishedCfdComparison
						.INFERRED_AIR_DENSITY_KG_PER_CUBIC_METER),
				number(published.advanceRatioJ()),
				number(published.publishedFreestreamVelocityMetersPerSecond()),
				number(comparison.expectedFreestreamVelocityMetersPerSecond()),
				number(comparison.publishedFreestreamClosureResidualMetersPerSecond()),
				number(published.publishedThrustNewtons()),
				number(published.publishedTorqueNewtonMeters()),
				number(published.publishedThrustCoefficientKt()),
				number(published.publishedPowerCoefficientKp()),
				number(published.publishedEfficiency()),
				number(comparison.publishedEtaClosureResidual()),
				number(comparison.inferredDensityFromPublishedThrust()),
				number(comparison.inferredDensityFromPublishedTorque()),
				number(comparison.sourceDensityClosureResidualFraction()),
				Boolean.toString(comparison.publishedTorqueCoefficientClosureSatisfied()),
				number(normalized.thrustNewtons()),
				number(normalized.shaftPowerWatts()),
				number(normalized.shaftTorqueNewtonMeters()),
				number(measured.lookup().thrustCoefficientCt()),
				number(measured.lookup().powerCoefficientCp()),
				number(measured.lookup().propulsiveEfficiencyEta()),
				number(measured.thrustNewtons()),
				number(measured.shaftPowerWatts()),
				number(measured.shaftTorqueNewtonMeters()),
				number(comparison.modelMinusPublishedCt()),
				number(comparison.modelMinusPublishedCp()),
				number(comparison.modelMinusPublishedEta()),
				number(comparison.modelMinusPublishedNormalizedThrustNewtons()),
				number(comparison.modelMinusPublishedNormalizedPowerWatts()),
				number(comparison.modelMinusPublishedNormalizedTorqueNewtonMeters()),
				number(comparison.modelMinusPublishedThrustNewtons()),
				number(comparison.modelMinusPublishedTorqueNewtonMeters()),
				escape(measured.lookup().interpolationStatus().name()),
				escape(measured.lookup().lowerSourceCurveId()),
				escape(measured.lookup().upperSourceCurveId())
		);
	}

	private static Aggregate aggregate(Report report, CfxTurbulenceModel model) {
		List<CfxComparison> rows = report.cfxComparisons().stream()
				.filter(row -> row.published().turbulenceModel() == model)
				.toList();
		return new Aggregate(
				rows.size(),
				meanAbsolute(rows, CfxComparison::modelMinusPublishedCt),
				maximumAbsolute(rows, CfxComparison::modelMinusPublishedCt),
				meanAbsolute(rows, CfxComparison::modelMinusPublishedCp),
				maximumAbsolute(rows, CfxComparison::modelMinusPublishedCp),
				meanAbsolute(rows, CfxComparison::modelMinusPublishedEta),
				meanAbsolute(rows,
						CfxComparison::modelMinusPublishedNormalizedThrustNewtons),
				maximumAbsolute(rows,
						CfxComparison::modelMinusPublishedNormalizedThrustNewtons),
				meanAbsolute(rows,
						CfxComparison::modelMinusPublishedNormalizedPowerWatts),
				maximumAbsolute(rows,
						CfxComparison::modelMinusPublishedNormalizedPowerWatts),
				meanAbsolute(rows,
						CfxComparison::modelMinusPublishedNormalizedTorqueNewtonMeters),
				maximumAbsolute(rows,
						CfxComparison::modelMinusPublishedNormalizedTorqueNewtonMeters)
		);
	}

	private static double meanAbsolute(
			List<CfxComparison> rows,
			Metric metric
	) {
		return rows.stream().mapToDouble(row -> Math.abs(metric.value(row)))
				.average().orElseThrow();
	}

	private static double maximumAbsolute(
			List<CfxComparison> rows,
			Metric metric
	) {
		return rows.stream().mapToDouble(row -> Math.abs(metric.value(row)))
				.max().orElseThrow();
	}

	private static String row(String quantity, double published, double measured,
			double residual) {
		return String.format(Locale.ROOT, "| %s | %s | %s | %s |",
				quantity, number(published), number(measured), number(residual));
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

	@FunctionalInterface
	private interface Metric {
		double value(CfxComparison comparison);
	}

	private record Aggregate(
			int count,
			double meanAbsoluteCt,
			double maximumAbsoluteCt,
			double meanAbsoluteCp,
			double maximumAbsoluteCp,
			double meanAbsoluteEta,
			double meanAbsoluteThrustNewtons,
			double maximumAbsoluteThrustNewtons,
			double meanAbsolutePowerWatts,
			double maximumAbsolutePowerWatts,
			double meanAbsoluteTorqueNewtonMeters,
			double maximumAbsoluteTorqueNewtonMeters
	) {
	}
}
