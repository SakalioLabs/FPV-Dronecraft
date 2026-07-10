package com.tenicana.dronecraft.sim.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJDimensionalRotorResponse;
import com.tenicana.dronecraft.sim.RotorHoverBladeElementModel;
import com.tenicana.dronecraft.sim.RotorHoverPowerLossDecomposition;
import com.tenicana.dronecraft.sim.Sda1075XfoilSectionPolar;
import com.tenicana.dronecraft.sim.UiucDa4002StaticPerformanceLookup;

/** Exports measured UIUC DA4002 static loads beside the untuned hover BEMT response. */
public final class UiucDa4002HoverBemtCurveExporter {
	public static final double DEFAULT_DYNAMIC_VISCOSITY_PASCAL_SECONDS = 1.81e-5;
	private static final String HEADER = String.join(",",
			"case_id",
			"source_url",
			"rpm",
			"wake_rotation_policy",
			"reynolds_75",
			"reference_ct",
			"bemt_ct",
			"ct_residual",
			"ct_residual_fraction",
			"reference_cp",
			"bemt_cp",
			"cp_residual",
			"cp_residual_fraction",
			"reference_thrust_n",
			"bemt_thrust_n",
			"thrust_residual_n",
			"thrust_residual_fraction",
			"reference_shaft_power_w",
			"bemt_shaft_power_w",
			"shaft_power_residual_w",
			"shaft_power_residual_fraction",
			"reference_shaft_torque_nm",
			"bemt_shaft_torque_nm",
			"shaft_torque_residual_nm",
			"shaft_torque_residual_fraction",
			"reference_conditioned_lift_induced_power_w",
			"reference_conditioned_lift_induced_cp",
			"reference_conditioned_modeled_power_w",
			"required_non_lift_power_w",
			"required_non_lift_cp",
			"required_non_lift_torque_nm",
			"conditioned_unresolved_power_w",
			"conditioned_unresolved_power_fraction",
			"conditioned_unresolved_cp",
			"conditioned_unresolved_torque_nm",
			"effective_profile_power_scale_if_all_non_lift_loss_were_profile",
			"effective_profile_power_scale_available",
			"required_power_closure_residual_w",
			"required_torque_closure_residual_nm",
			"reference_disk_loading_n_m2",
			"bemt_disk_loading_n_m2",
			"reference_ideal_induced_velocity_mps",
			"bemt_ideal_induced_velocity_mps",
			"reference_hover_figure_of_merit",
			"bemt_hover_figure_of_merit",
			"bemt_lift_induced_power_w",
			"bemt_profile_power_w",
			"bemt_lift_induced_power_over_uniform_ideal",
			"bemt_momentum_wake_torque_nm",
			"bemt_angular_momentum_closure_residual_nm",
			"bemt_wake_swirl_kinetic_power_w",
			"bemt_wake_swirl_kinetic_power_over_shaft_power",
			"bemt_minimum_reynolds",
			"bemt_maximum_reynolds",
			"bemt_minimum_alpha_deg",
			"bemt_maximum_alpha_deg",
			"bemt_clamped_annuli",
			"bemt_reynolds_clamped_annuli",
			"bemt_alpha_clamped_annuli",
			"bemt_annulus_count",
			"bemt_unclamped_polar_coverage_fraction",
			"bemt_maximum_tangential_induced_velocity_mps",
			"bemt_maximum_tangential_induction_to_blade_speed",
			"bemt_status",
			"reference_data_source_id",
			"section_polar_data_source_id"
	);

	private UiucDa4002HoverBemtCurveExporter() {
	}

	public static void main(String[] args) throws IOException {
		Path output = args.length >= 1 && !args[0].isBlank()
				? Path.of(args[0])
				: Path.of("build", "uiuc-da4002-hover-bemt", "comparison.csv");
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
		for (UiucDa4002StaticPerformanceLookup.StaticCurve curve
				: UiucDa4002StaticPerformanceLookup.curves()) {
			for (UiucDa4002StaticPerformanceLookup.StaticRow row : curve.rows()) {
				UiucDa4002StaticPerformanceLookup.DimensionalSample reference =
						UiucDa4002StaticPerformanceLookup.sample(
								curve,
								row.rpm(),
								airDensityKgPerCubicMeter,
								dynamicViscosityPascalSeconds,
								UiucDa4002StaticPerformanceLookup.EnvelopePolicy
										.BLOCK_OUT_OF_ENVELOPE
						);
				for (RotorHoverBladeElementModel.WakeRotationPolicy wakeRotationPolicy
						: RotorHoverBladeElementModel.WakeRotationPolicy.values()) {
					RotorHoverBladeElementModel.HoverSample bemt = RotorHoverBladeElementModel.solve(
							new RotorHoverBladeElementModel.HoverQuery(
									curve.geometry(),
									curve.referenceDiameterMeters() * 0.5,
									airDensityKgPerCubicMeter,
									dynamicViscosityPascalSeconds,
									row.rpm() * 2.0 * Math.PI / 60.0,
									annuliPerGeometryInterval,
									Sda1075XfoilSectionPolar.EnvelopePolicy.CLAMP_TO_ENVELOPE,
									wakeRotationPolicy
							)
					);
					lines.add(csvLine(curve, reference, bemt));
				}
			}
		}
		return List.copyOf(lines);
	}

	private static String csvLine(
			UiucDa4002StaticPerformanceLookup.StaticCurve curve,
			UiucDa4002StaticPerformanceLookup.DimensionalSample reference,
			RotorHoverBladeElementModel.HoverSample bemt
	) {
		RotorHoverPowerLossDecomposition.PowerLossSample powerLoss =
				RotorHoverPowerLossDecomposition.decompose(reference, bemt);
		double ctResidual = bemt.thrustCoefficientCt()
				- reference.lookup().thrustCoefficientCt();
		double cpResidual = bemt.powerCoefficientCp()
				- reference.lookup().powerCoefficientCp();
		double thrustResidual = bemt.thrustNewtons() - reference.thrustNewtons();
		double powerResidual = bemt.shaftPowerWatts() - reference.shaftPowerWatts();
		double torqueResidual = bemt.shaftTorqueNewtonMeters()
				- reference.shaftTorqueNewtonMeters();
		return String.join(",",
				escape(curve.id()),
				escape(curve.sourceUrl()),
				number(reference.lookup().effectiveRpm()),
				escape(bemt.query().wakeRotationPolicy().name()),
				number(reference.reynoldsNumberAtSeventyFivePercentRadius()),
				number(reference.lookup().thrustCoefficientCt()),
				number(bemt.thrustCoefficientCt()),
				number(ctResidual),
				number(ratio(ctResidual, reference.lookup().thrustCoefficientCt())),
				number(reference.lookup().powerCoefficientCp()),
				number(bemt.powerCoefficientCp()),
				number(cpResidual),
				number(ratio(cpResidual, reference.lookup().powerCoefficientCp())),
				number(reference.thrustNewtons()),
				number(bemt.thrustNewtons()),
				number(thrustResidual),
				number(ratio(thrustResidual, reference.thrustNewtons())),
				number(reference.shaftPowerWatts()),
				number(bemt.shaftPowerWatts()),
				number(powerResidual),
				number(ratio(powerResidual, reference.shaftPowerWatts())),
				number(reference.shaftTorqueNewtonMeters()),
				number(bemt.shaftTorqueNewtonMeters()),
				number(torqueResidual),
				number(ratio(torqueResidual, reference.shaftTorqueNewtonMeters())),
				number(powerLoss.referenceConditionedLiftInducedPowerWatts()),
				number(powerLoss.referenceConditionedLiftInducedPowerCoefficientCp()),
				number(powerLoss.referenceConditionedModeledPowerWatts()),
				number(powerLoss.requiredNonLiftPowerWatts()),
				number(powerLoss.requiredNonLiftPowerCoefficientCp()),
				number(powerLoss.requiredNonLiftTorqueNewtonMeters()),
				number(powerLoss.conditionedUnresolvedPowerWatts()),
				number(powerLoss.conditionedUnresolvedPowerFraction()),
				number(powerLoss.conditionedUnresolvedPowerCoefficientCp()),
				number(powerLoss.conditionedUnresolvedTorqueNewtonMeters()),
				number(powerLoss.effectiveProfilePowerScaleIfAllNonLiftLossWereProfile()),
				Boolean.toString(powerLoss.effectiveProfilePowerScaleAvailable()),
				number(powerLoss.requiredPowerClosureResidualWatts()),
				number(powerLoss.requiredTorqueClosureResidualNewtonMeters()),
				number(reference.diskLoadingNewtonsPerSquareMeter()),
				number(bemt.diskLoadingNewtonsPerSquareMeter()),
				number(reference.idealInducedVelocityMetersPerSecond()),
				number(bemt.idealInducedVelocityMetersPerSecond()),
				number(reference.hoverFigureOfMerit()),
				number(bemt.hoverFigureOfMerit()),
				number(bemt.liftInducedPowerWatts()),
				number(bemt.profilePowerWatts()),
				number(bemt.liftInducedPowerOverUniformIdeal()),
				number(bemt.momentumWakeTorqueNewtonMeters()),
				number(bemt.angularMomentumClosureResidualNewtonMeters()),
				number(bemt.wakeSwirlKineticPowerWatts()),
				number(bemt.wakeSwirlKineticPowerOverShaftPower()),
				number(bemt.minimumReynoldsNumber()),
				number(bemt.maximumReynoldsNumber()),
				number(bemt.minimumAngleOfAttackDegrees()),
				number(bemt.maximumAngleOfAttackDegrees()),
				Integer.toString(bemt.clampedAnnulusCount()),
				Integer.toString(bemt.reynoldsClampedAnnulusCount()),
				Integer.toString(bemt.angleOfAttackClampedAnnulusCount()),
				Integer.toString(bemt.annulusCount()),
				number(bemt.unclampedPolarCoverageFraction()),
				number(bemt.maximumTangentialInducedVelocityMetersPerSecond()),
				number(bemt.maximumTangentialInductionToBladeSpeed()),
				escape(bemt.status().name()),
				escape(UiucDa4002StaticPerformanceLookup.DATA_SOURCE_ID),
				escape(Sda1075XfoilSectionPolar.DATA_SOURCE_ID)
		);
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
