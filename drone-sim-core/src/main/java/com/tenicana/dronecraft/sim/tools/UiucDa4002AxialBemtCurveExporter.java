package com.tenicana.dronecraft.sim.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJDimensionalRotorResponse;
import com.tenicana.dronecraft.sim.RotorAxialBladeElementModel;
import com.tenicana.dronecraft.sim.RotorHoverBladeElementModel;
import com.tenicana.dronecraft.sim.Sda1075XfoilSectionPolar;
import com.tenicana.dronecraft.sim.SnelMcCrinkRotationalAugmentation;
import com.tenicana.dronecraft.sim.UiucDa4002AdvancePerformanceLookup;

/** Exports UIUC advancing-flow measurements beside untuned geometry BEMT predictions. */
public final class UiucDa4002AxialBemtCurveExporter {
	public static final double DEFAULT_DYNAMIC_VISCOSITY_PASCAL_SECONDS = 1.81e-5;
	private static final String HEADER = String.join(",",
			"case_id",
			"source_url",
			"rpm",
			"diameter_m",
			"advance_ratio_j",
			"axial_freestream_velocity_mps",
			"reynolds_75",
			"reference_ct",
			"reference_cp",
			"reference_eta",
			"reference_eta_closure_residual",
			"reference_thrust_n",
			"reference_shaft_power_w",
			"reference_shaft_torque_nm",
			"bemt_status",
			"bemt_raw_solution_available",
			"bemt_raw_ct",
			"bemt_raw_cp",
			"bemt_raw_eta",
			"bemt_raw_thrust_n",
			"bemt_raw_shaft_power_w",
			"bemt_raw_shaft_torque_nm",
			"bemt_ct_residual",
			"bemt_ct_residual_fraction",
			"bemt_cp_residual",
			"bemt_cp_residual_fraction",
			"bemt_eta_residual",
			"bemt_ideal_induced_velocity_mps",
			"bemt_disk_axial_velocity_mps",
			"bemt_minimum_reynolds",
			"bemt_maximum_reynolds",
			"bemt_minimum_alpha_deg",
			"bemt_maximum_alpha_deg",
			"bemt_clamped_annuli",
			"bemt_annulus_count",
			"bemt_reynolds_supported_thrust_weight_fraction",
			"bemt_fully_supported_thrust_weight_fraction",
			"augmented_bemt_status",
			"augmented_bemt_raw_solution_available",
			"augmented_bemt_raw_ct",
			"augmented_bemt_raw_cp",
			"augmented_bemt_raw_eta",
			"augmented_bemt_ct_residual_fraction",
			"augmented_bemt_cp_residual_fraction",
			"rotational_augmentation_applied_annuli",
			"rotational_augmentation_max_abs_delta_cl",
			"rotational_augmentation_max_abs_delta_cd",
			"rotational_augmentation_requires_propeller_specific_validation",
			"reference_data_source_id",
			"section_polar_data_source_id",
			"rotational_augmentation_source_id"
	);

	private UiucDa4002AxialBemtCurveExporter() {
	}

	public static void main(String[] args) throws IOException {
		Path output = args.length >= 1 && !args[0].isBlank()
				? Path.of(args[0])
				: Path.of("build", "uiuc-da4002-axial-bemt", "comparison.csv");
		double airDensity = args.length >= 2 && !args[1].isBlank()
				? Double.parseDouble(args[1])
				: PropellerArchiveCtCpJDimensionalRotorResponse
						.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
		double dynamicViscosity = args.length >= 3 && !args[2].isBlank()
				? Double.parseDouble(args[2])
				: DEFAULT_DYNAMIC_VISCOSITY_PASCAL_SECONDS;
		int annuliPerGeometryInterval = args.length >= 4 && !args[3].isBlank()
				? Integer.parseInt(args[3])
				: RotorHoverBladeElementModel.DEFAULT_ANNULI_PER_GEOMETRY_INTERVAL;
		write(output, airDensity, dynamicViscosity, annuliPerGeometryInterval);
	}

	public static void write(
			Path output,
			double airDensityKgPerCubicMeter,
			double dynamicViscosityPascalSeconds,
			int annuliPerGeometryInterval
	) throws IOException {
		if (output == null) {
			throw new IllegalArgumentException("output path must not be null.");
		}
		List<String> lines = csvLines(
				airDensityKgPerCubicMeter,
				dynamicViscosityPascalSeconds,
				annuliPerGeometryInterval
		);
		Path parent = output.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.writeString(output, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
	}

	public static List<String> csvLines() {
		return csvLines(
				PropellerArchiveCtCpJDimensionalRotorResponse
						.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER,
				DEFAULT_DYNAMIC_VISCOSITY_PASCAL_SECONDS,
				RotorHoverBladeElementModel.DEFAULT_ANNULI_PER_GEOMETRY_INTERVAL
		);
	}

	public static List<String> csvLines(
			double airDensityKgPerCubicMeter,
			double dynamicViscosityPascalSeconds,
			int annuliPerGeometryInterval
	) {
		validateInputs(
				airDensityKgPerCubicMeter,
				dynamicViscosityPascalSeconds,
				annuliPerGeometryInterval
		);
		List<String> lines = new ArrayList<>();
		lines.add(HEADER);
		for (UiucDa4002AdvancePerformanceLookup.AdvanceCurve curve
				: UiucDa4002AdvancePerformanceLookup.curves()) {
			for (UiucDa4002AdvancePerformanceLookup.AdvanceRow row : curve.rows()) {
				UiucDa4002AdvancePerformanceLookup.DimensionalSample reference =
						UiucDa4002AdvancePerformanceLookup.sample(
								curve,
								row.advanceRatioJ(),
								airDensityKgPerCubicMeter,
								dynamicViscosityPascalSeconds,
								UiucDa4002AdvancePerformanceLookup.EnvelopePolicy
										.BLOCK_OUT_OF_ENVELOPE
						);
				RotorAxialBladeElementModel.AxialSample baseline = solve(
						curve,
						row.advanceRatioJ(),
						airDensityKgPerCubicMeter,
						dynamicViscosityPascalSeconds,
						annuliPerGeometryInterval,
						SnelMcCrinkRotationalAugmentation.Policy.NONE
				);
				RotorAxialBladeElementModel.AxialSample augmented = solve(
						curve,
						row.advanceRatioJ(),
						airDensityKgPerCubicMeter,
						dynamicViscosityPascalSeconds,
						annuliPerGeometryInterval,
						SnelMcCrinkRotationalAugmentation.Policy.SNEL_MCCRINK_NORMAL_FORCE
				);
				lines.add(csvLine(curve, reference, baseline, augmented));
			}
		}
		return List.copyOf(lines);
	}

	private static RotorAxialBladeElementModel.AxialSample solve(
			UiucDa4002AdvancePerformanceLookup.AdvanceCurve curve,
			double advanceRatio,
			double airDensityKgPerCubicMeter,
			double dynamicViscosityPascalSeconds,
			int annuliPerGeometryInterval,
			SnelMcCrinkRotationalAugmentation.Policy augmentationPolicy
	) {
		double angularVelocity = curve.rpm() * 2.0 * Math.PI / 60.0;
		double axialFreestreamVelocity = advanceRatio
				* curve.rpm() / 60.0
				* curve.referenceDiameterMeters();
		return RotorAxialBladeElementModel.solve(
				new RotorAxialBladeElementModel.AxialQuery(
						curve.geometry(),
						curve.referenceDiameterMeters() * 0.5,
						airDensityKgPerCubicMeter,
						dynamicViscosityPascalSeconds,
						angularVelocity,
						axialFreestreamVelocity,
						annuliPerGeometryInterval,
						Sda1075XfoilSectionPolar.EnvelopePolicy.CLAMP_TO_ENVELOPE,
						RotorHoverBladeElementModel.WakeRotationPolicy.AXIAL_MOMENTUM_ONLY,
						augmentationPolicy
				)
		);
	}

	private static String csvLine(
			UiucDa4002AdvancePerformanceLookup.AdvanceCurve curve,
			UiucDa4002AdvancePerformanceLookup.DimensionalSample reference,
			RotorAxialBladeElementModel.AxialSample baseline,
			RotorAxialBladeElementModel.AxialSample augmented
	) {
		RotorHoverBladeElementModel.HoverSample rawBaseline = baseline.bladeElementSample();
		RotorHoverBladeElementModel.HoverSample rawAugmented = augmented.bladeElementSample();
		double referenceCt = reference.lookup().thrustCoefficientCt();
		double referenceCp = reference.lookup().powerCoefficientCp();
		double referenceEta = reference.lookup().propulsiveEfficiencyEta();
		double baselineEta = rawEfficiency(rawBaseline, reference.axialFreestreamVelocityMetersPerSecond());
		double augmentedEta = rawEfficiency(rawAugmented, reference.axialFreestreamVelocityMetersPerSecond());
		double baselineCtResidual = rawBaseline.thrustCoefficientCt() - referenceCt;
		double baselineCpResidual = rawBaseline.powerCoefficientCp() - referenceCp;
		return String.join(",",
				escape(curve.id()),
				escape(curve.sourceUrl()),
				number(curve.rpm()),
				number(curve.referenceDiameterMeters()),
				number(reference.lookup().effectiveAdvanceRatioJ()),
				number(reference.axialFreestreamVelocityMetersPerSecond()),
				number(reference.reynoldsNumberAtSeventyFivePercentRadius()),
				number(referenceCt),
				number(referenceCp),
				number(referenceEta),
				number(reference.lookup().etaClosureResidual()),
				number(reference.thrustNewtons()),
				number(reference.shaftPowerWatts()),
				number(reference.shaftTorqueNewtonMeters()),
				escape(baseline.status().name()),
				Boolean.toString(rawBaseline.solved()),
				number(rawBaseline.thrustCoefficientCt()),
				number(rawBaseline.powerCoefficientCp()),
				number(baselineEta),
				number(rawBaseline.thrustNewtons()),
				number(rawBaseline.shaftPowerWatts()),
				number(rawBaseline.shaftTorqueNewtonMeters()),
				number(baselineCtResidual),
				number(ratio(baselineCtResidual, referenceCt)),
				number(baselineCpResidual),
				number(ratio(baselineCpResidual, referenceCp)),
				number(baselineEta - referenceEta),
				number(rawBaseline.idealInducedVelocityMetersPerSecond()),
				number(reference.axialFreestreamVelocityMetersPerSecond()
						+ rawBaseline.idealInducedVelocityMetersPerSecond()),
				number(rawBaseline.minimumReynoldsNumber()),
				number(rawBaseline.maximumReynoldsNumber()),
				number(rawBaseline.minimumAngleOfAttackDegrees()),
				number(rawBaseline.maximumAngleOfAttackDegrees()),
				number(rawBaseline.clampedAnnulusCount()),
				number(rawBaseline.annulusCount()),
				number(rawBaseline.reynoldsSupportedThrustWeightFraction()),
				number(rawBaseline.fullySupportedThrustWeightFraction()),
				escape(augmented.status().name()),
				Boolean.toString(rawAugmented.solved()),
				number(rawAugmented.thrustCoefficientCt()),
				number(rawAugmented.powerCoefficientCp()),
				number(augmentedEta),
				number(ratio(rawAugmented.thrustCoefficientCt() - referenceCt, referenceCt)),
				number(ratio(rawAugmented.powerCoefficientCp() - referenceCp, referenceCp)),
				number(rawAugmented.rotationalAugmentationAppliedAnnulusCount()),
				number(rawAugmented.maximumAbsoluteRotationalLiftCoefficientDelta()),
				number(rawAugmented.maximumAbsoluteRotationalDragCoefficientDelta()),
				Boolean.toString(rawAugmented
						.rotationalAugmentationRequiresPropellerSpecificValidation()),
				escape(UiucDa4002AdvancePerformanceLookup.DATA_SOURCE_ID),
				escape(Sda1075XfoilSectionPolar.DATA_SOURCE_ID),
				escape(SnelMcCrinkRotationalAugmentation.DATA_SOURCE_ID)
		);
	}

	private static double rawEfficiency(
			RotorHoverBladeElementModel.HoverSample sample,
			double axialFreestreamVelocityMetersPerSecond
	) {
		return sample.solved()
				? ratio(sample.thrustNewtons() * axialFreestreamVelocityMetersPerSecond,
						sample.shaftPowerWatts())
				: 0.0;
	}

	private static void validateInputs(
			double airDensityKgPerCubicMeter,
			double dynamicViscosityPascalSeconds,
			int annuliPerGeometryInterval
	) {
		if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= 0.0) {
			throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
		}
		if (!Double.isFinite(dynamicViscosityPascalSeconds)
				|| dynamicViscosityPascalSeconds <= 0.0) {
			throw new IllegalArgumentException(
					"dynamicViscosityPascalSeconds must be finite and positive."
			);
		}
		if (annuliPerGeometryInterval <= 0) {
			throw new IllegalArgumentException("annuliPerGeometryInterval must be positive.");
		}
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator)
				|| !Double.isFinite(denominator)
				|| Math.abs(denominator) <= 1.0e-12) {
			return 0.0;
		}
		return numerator / denominator;
	}

	private static String number(double value) {
		return String.format(Locale.ROOT, "%.15g", value);
	}

	private static String escape(String value) {
		String text = value == null ? "" : value;
		return '"' + text.replace("\"", "\"\"") + '"';
	}
}
