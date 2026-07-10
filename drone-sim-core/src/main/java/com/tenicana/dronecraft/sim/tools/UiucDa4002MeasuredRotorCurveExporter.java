package com.tenicana.dronecraft.sim.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJDimensionalRotorResponse;
import com.tenicana.dronecraft.sim.UiucDa4002MeasuredRotorModel;

/** Exports a propeller-specific measured DA4002 CT/CP/J surface slice. */
public final class UiucDa4002MeasuredRotorCurveExporter {
	public static final double DEFAULT_DYNAMIC_VISCOSITY_PASCAL_SECONDS = 1.81e-5;
	public static final double DEFAULT_ADVANCE_RATIO_STEP = 0.025;
	public static final double DEFAULT_MAXIMUM_ADVANCE_RATIO = 1.0;
	private static final String HEADER = String.join(",",
			"data_source_id",
			"propeller_id",
			"geometry_id",
			"rpm",
			"advance_ratio_j",
			"interpolation_status",
			"blocked",
			"clamped",
			"lower_rpm",
			"upper_rpm",
			"rpm_interpolation_fraction",
			"lower_source_curve_id",
			"upper_source_curve_id",
			"lower_track_interpolation_status",
			"upper_track_interpolation_status",
			"ct",
			"cp",
			"eta",
			"propulsive_regime",
			"positive_thrust_runtime_eligible",
			"axial_freestream_velocity_mps",
			"source_rotational_reynolds_75",
			"resultant_section_reynolds_75",
			"thrust_n",
			"shaft_power_w",
			"shaft_torque_nm",
			"torque_coefficient_cq",
			"useful_propulsive_power_w",
			"signed_disk_loading_n_m2",
			"ideal_induced_velocity_mps",
			"ideal_induced_power_w",
			"ideal_momentum_power_w",
			"ideal_momentum_power_over_shaft_power",
			"message"
	);

	private UiucDa4002MeasuredRotorCurveExporter() {
	}

	public static void main(String[] args) throws IOException {
		Path output = args.length >= 1 && !args[0].isBlank()
				? Path.of(args[0])
				: Path.of("build", "uiuc-da4002-measured-rotor", "curve.csv");
		UiucDa4002MeasuredRotorModel.Propeller propeller = args.length >= 2
				&& !args[1].isBlank()
						? UiucDa4002MeasuredRotorModel.Propeller.fromId(args[1])
						: UiucDa4002MeasuredRotorModel.Propeller.DA4002_9X6_75;
		double rpm = args.length >= 3 && !args[2].isBlank()
				? Double.parseDouble(args[2])
				: 4_000.0;
		double advanceRatioStep = args.length >= 4 && !args[3].isBlank()
				? Double.parseDouble(args[3])
				: DEFAULT_ADVANCE_RATIO_STEP;
		double maximumAdvanceRatio = args.length >= 5 && !args[4].isBlank()
				? Double.parseDouble(args[4])
				: DEFAULT_MAXIMUM_ADVANCE_RATIO;
		double airDensity = args.length >= 6 && !args[5].isBlank()
				? Double.parseDouble(args[5])
				: PropellerArchiveCtCpJDimensionalRotorResponse
						.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
		double dynamicViscosity = args.length >= 7 && !args[6].isBlank()
				? Double.parseDouble(args[6])
				: DEFAULT_DYNAMIC_VISCOSITY_PASCAL_SECONDS;
		write(output, propeller, rpm, advanceRatioStep, maximumAdvanceRatio,
				airDensity, dynamicViscosity);
	}

	public static void write(
			Path output,
			UiucDa4002MeasuredRotorModel.Propeller propeller,
			double rpm,
			double advanceRatioStep,
			double maximumAdvanceRatio,
			double airDensityKgPerCubicMeter,
			double dynamicViscosityPascalSeconds
	) throws IOException {
		if (output == null) {
			throw new IllegalArgumentException("output path must not be null.");
		}
		List<String> lines = csvLines(propeller, rpm, advanceRatioStep,
				maximumAdvanceRatio, airDensityKgPerCubicMeter,
				dynamicViscosityPascalSeconds);
		Path parent = output.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.writeString(output, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
	}

	public static List<String> csvLines(
			UiucDa4002MeasuredRotorModel.Propeller propeller,
			double rpm,
			double advanceRatioStep,
			double maximumAdvanceRatio,
			double airDensityKgPerCubicMeter,
			double dynamicViscosityPascalSeconds
	) {
		validateInputs(propeller, rpm, advanceRatioStep, maximumAdvanceRatio,
				airDensityKgPerCubicMeter, dynamicViscosityPascalSeconds);
		List<String> lines = new ArrayList<>();
		lines.add(HEADER);
		for (double advanceRatio : advanceRatios(advanceRatioStep, maximumAdvanceRatio)) {
			UiucDa4002MeasuredRotorModel.DimensionalSample sample =
					UiucDa4002MeasuredRotorModel.sample(
							propeller,
							advanceRatio,
							rpm,
							airDensityKgPerCubicMeter,
							dynamicViscosityPascalSeconds
					);
			lines.add(csvLine(sample));
		}
		return List.copyOf(lines);
	}

	private static List<Double> advanceRatios(double step, double maximum) {
		List<Double> values = new ArrayList<>();
		int fullSteps = (int) Math.floor(maximum / step + 1.0e-12);
		for (int index = 0; index <= fullSteps; index++) {
			values.add(index * step);
		}
		double last = values.get(values.size() - 1);
		if (maximum - last > 1.0e-12) {
			values.add(maximum);
		}
		return List.copyOf(values);
	}

	private static String csvLine(UiucDa4002MeasuredRotorModel.DimensionalSample sample) {
		UiucDa4002MeasuredRotorModel.LookupResult lookup = sample.lookup();
		return String.join(",",
				escape(UiucDa4002MeasuredRotorModel.DATA_SOURCE_ID),
				escape(lookup.query().propeller().id()),
				escape(lookup.query().propeller().geometry().id()),
				number(lookup.query().rpm()),
				number(lookup.query().advanceRatioJ()),
				escape(lookup.interpolationStatus().name()),
				Boolean.toString(lookup.blocked()),
				Boolean.toString(lookup.clamped()),
				number(lookup.lowerRpm()),
				number(lookup.upperRpm()),
				number(lookup.rpmInterpolationFraction()),
				escape(lookup.lowerSourceCurveId()),
				escape(lookup.upperSourceCurveId()),
				escape(lookup.lowerTrackInterpolationStatus().name()),
				escape(lookup.upperTrackInterpolationStatus().name()),
				number(lookup.thrustCoefficientCt()),
				number(lookup.powerCoefficientCp()),
				number(lookup.propulsiveEfficiencyEta()),
				escape(sample.propulsiveRegime().name()),
				Boolean.toString(sample.positiveThrustRuntimeEligible()),
				number(sample.axialFreestreamVelocityMetersPerSecond()),
				number(sample.sourceRotationalReynoldsNumberAtSeventyFivePercentRadius()),
				number(sample.resultantSectionReynoldsNumberAtSeventyFivePercentRadius()),
				number(sample.thrustNewtons()),
				number(sample.shaftPowerWatts()),
				number(sample.shaftTorqueNewtonMeters()),
				number(sample.torqueCoefficientCq()),
				number(sample.usefulPropulsivePowerWatts()),
				number(sample.signedDiskLoadingNewtonsPerSquareMeter()),
				number(sample.idealInducedVelocityMetersPerSecond()),
				number(sample.idealInducedPowerWatts()),
				number(sample.idealMomentumPowerWatts()),
				number(sample.idealMomentumPowerOverShaftPower()),
				escape(lookup.message())
		);
	}

	private static void validateInputs(
			UiucDa4002MeasuredRotorModel.Propeller propeller,
			double rpm,
			double advanceRatioStep,
			double maximumAdvanceRatio,
			double airDensityKgPerCubicMeter,
			double dynamicViscosityPascalSeconds
	) {
		if (propeller == null) {
			throw new IllegalArgumentException("propeller must not be null.");
		}
		if (!Double.isFinite(rpm) || rpm <= 0.0) {
			throw new IllegalArgumentException("rpm must be finite and positive.");
		}
		if (!Double.isFinite(advanceRatioStep) || advanceRatioStep <= 0.0) {
			throw new IllegalArgumentException("advanceRatioStep must be finite and positive.");
		}
		if (!Double.isFinite(maximumAdvanceRatio) || maximumAdvanceRatio < 0.0) {
			throw new IllegalArgumentException(
					"maximumAdvanceRatio must be finite and non-negative."
			);
		}
		if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= 0.0) {
			throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
		}
		if (!Double.isFinite(dynamicViscosityPascalSeconds)
				|| dynamicViscosityPascalSeconds <= 0.0) {
			throw new IllegalArgumentException(
					"dynamicViscosityPascalSeconds must be finite and positive."
			);
		}
	}

	private static String number(double value) {
		return String.format(Locale.ROOT, "%.15g", value);
	}

	private static String escape(String value) {
		String text = value == null ? "" : value;
		return '"' + text.replace("\"", "\"\"") + '"';
	}
}
