package com.tenicana.dronecraft.sim.tools;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJDimensionalRotorResponse;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJLookupEvaluator;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJRotorForceModel;
import com.tenicana.dronecraft.sim.RotorSpec;
import com.tenicana.dronecraft.sim.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class CtCpJConfigurationCurveExporter {
	private static final String HEADER = String.join(",",
			"preset",
			"case",
			"query_j",
			"query_rpm",
			"target_thrust_n",
			"target_thrust_residual_n",
			"target_thrust_solve_status",
			"target_thrust_solve_iterations",
			"trim_solve_status",
			"trim_target_body_torque_x_nm",
			"trim_target_body_torque_y_nm",
			"trim_target_body_torque_z_nm",
			"trim_body_torque_residual_x_nm",
			"trim_body_torque_residual_y_nm",
			"trim_body_torque_residual_z_nm",
			"trim_min_allocated_rotor_thrust_n",
			"trim_max_allocated_rotor_thrust_n",
			"effective_j_min",
			"effective_j_max",
			"effective_rpm_min",
			"effective_rpm_max",
			"rotor_count",
			"accepted_rotor_count",
			"runtime_force_replacement_accepted_rotor_count",
			"blocked_rotor_count",
			"clamped_rotor_count",
			"query_signed_axial_speed_mps",
			"relative_air_body_x_mps",
			"relative_air_body_y_mps",
			"relative_air_body_z_mps",
			"body_angular_rate_x_rad_s",
			"body_angular_rate_y_rad_s",
			"body_angular_rate_z_rad_s",
			"transverse_air_speed_mps",
			"inflow_angle_deg",
			"total_thrust_force_body_x_n",
			"total_thrust_force_body_y_n",
			"total_thrust_force_body_z_n",
			"total_reaction_torque_body_x_nm",
			"total_reaction_torque_body_y_nm",
			"total_reaction_torque_body_z_nm",
			"total_thrust_moment_body_x_nm",
			"total_thrust_moment_body_y_nm",
			"total_thrust_moment_body_z_nm",
			"total_body_torque_x_nm",
			"total_body_torque_y_nm",
			"total_body_torque_z_nm",
			"total_thrust_n",
			"total_shaft_power_w",
			"total_shaft_torque_nm",
			"total_disk_mass_flow_kg_s",
			"total_useful_axial_thrust_power_w",
			"total_ideal_induced_power_w",
			"total_ideal_momentum_power_w",
			"total_ideal_momentum_power_over_shaft_power",
			"total_wake_swirl_kinetic_power_w",
			"total_wake_kinetic_power_w",
			"total_wake_kinetic_power_residual_w",
			"total_wake_kinetic_power_over_shaft_power",
			"runtime_replacement_total_thrust_force_body_x_n",
			"runtime_replacement_total_thrust_force_body_y_n",
			"runtime_replacement_total_thrust_force_body_z_n",
			"runtime_replacement_total_reaction_torque_body_x_nm",
			"runtime_replacement_total_reaction_torque_body_y_nm",
			"runtime_replacement_total_reaction_torque_body_z_nm",
			"runtime_replacement_total_thrust_moment_body_x_nm",
			"runtime_replacement_total_thrust_moment_body_y_nm",
			"runtime_replacement_total_thrust_moment_body_z_nm",
			"runtime_replacement_total_body_torque_x_nm",
			"runtime_replacement_total_body_torque_y_nm",
			"runtime_replacement_total_body_torque_z_nm",
			"runtime_replacement_total_thrust_n",
			"runtime_replacement_total_shaft_power_w",
			"runtime_replacement_total_shaft_torque_nm",
			"runtime_replacement_disk_mass_flow_kg_s",
			"runtime_replacement_useful_axial_thrust_power_w",
			"runtime_replacement_ideal_induced_power_w",
			"runtime_replacement_total_ideal_momentum_power_w",
			"runtime_replacement_ideal_momentum_power_over_shaft_power",
			"runtime_replacement_wake_swirl_kinetic_power_w",
			"runtime_replacement_wake_kinetic_power_w",
			"runtime_replacement_wake_kinetic_power_residual_w",
			"runtime_replacement_wake_kinetic_power_over_shaft_power",
			"runtime_eligibility_status",
			"lookup_status_summary",
			"source_id_summary"
	);
	private static final double RPM_PER_RADIAN_PER_SECOND = 60.0 / (2.0 * Math.PI);
	private static final double REVERSE_AXIAL_DIAGNOSTIC_SPEED_METERS_PER_SECOND = -4.5;
	private static final double OUT_OF_ENVELOPE_DIAGNOSTIC_ADVANCE_RATIO_J = 1.20;
	private static final double TRANSVERSE_INFLOW_DIAGNOSTIC_ADVANCE_RATIO_J = 0.4064;
	private static final double TRANSVERSE_INFLOW_DIAGNOSTIC_SPEED_METERS_PER_SECOND = 2.5;
	private static final double BODY_RATE_DIAGNOSTIC_ADVANCE_RATIO_J = 0.4064;
	private static final double BODY_RATE_DIAGNOSTIC_ROLL_RATE_RADIANS_PER_SECOND = 5.0;
	private static final double TARGET_THRUST_FORWARD_ADVANCE_RATIO_J = 0.4064;
	private static final double TARGET_THRUST_HIGH_J_BLOCK_SPEED_METERS_PER_SECOND = 22.0;
	private static final Vec3 TRIM_DIAGNOSTIC_BODY_TORQUE_NEWTON_METERS =
			new Vec3(0.045, 0.012, -0.035);

	private CtCpJConfigurationCurveExporter() {
	}

	public static void main(String[] args) throws IOException {
		String presetName = args.length >= 1 && !args[0].isBlank()
				? args[0]
				: PropellerArchiveCtCpJLookupEvaluator.DEFAULT_PRESET_NAME;
		Path output = args.length >= 2 && !args[1].isBlank()
				? Path.of(args[1])
				: Path.of("build", "ct-cp-j-configuration-curves", presetName + ".csv");
		double airDensity = args.length >= 3 && !args[2].isBlank()
				? Double.parseDouble(args[2])
				: PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
		double ambientTemperatureCelsius = args.length >= 4 && !args[3].isBlank()
				? Double.parseDouble(args[3])
				: 25.0;
		double ambientHumidity = args.length >= 5 && !args[4].isBlank()
				? Double.parseDouble(args[4])
				: 0.0;
		write(presetName, output, airDensity, ambientTemperatureCelsius, ambientHumidity);
	}

	public static void write(
			String presetName,
			Path output,
			double airDensityKgPerCubicMeter
	) throws IOException {
		write(presetName, output, airDensityKgPerCubicMeter, 25.0, 0.0);
	}

	public static void write(
			String presetName,
			Path output,
			double airDensityKgPerCubicMeter,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) throws IOException {
		if (output == null) {
			throw new IllegalArgumentException("output path must not be null.");
		}
		List<String> lines = csvLines(
				presetName,
				airDensityKgPerCubicMeter,
				ambientTemperatureCelsius,
				ambientHumidity
		);
		Path parent = output.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.writeString(output, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
	}

	public static List<String> csvLines(
			String presetName,
			double airDensityKgPerCubicMeter
	) {
		return csvLines(presetName, airDensityKgPerCubicMeter, 25.0, 0.0);
	}

	public static List<String> csvLines(
			String presetName,
			double airDensityKgPerCubicMeter,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		DroneConfig config = configForPreset(presetName);
		if (config.rotors().isEmpty()) {
			throw new IllegalArgumentException("DroneConfig preset has no rotors: " + presetName);
		}
		RotorSpec rotor = config.rotors().get(0);
		double diameter = rotor.radiusMeters() * 2.0;
		List<Double> advanceRatios = acceptedAdvanceShapeSampleAdvanceRatios(
				presetName,
				diameter,
				airDensityKgPerCubicMeter
		);
		List<String> lines = new ArrayList<>();
		lines.add(HEADER);
		for (StaticCurvePoint point : staticAnchoredConfigurationCurvePoints(config, rotor)) {
			for (double advanceRatioJ : advanceRatios) {
				lines.add(csvLine(sampleUniformSignedAxial(
						presetName,
						point.caseName(),
						config,
						advanceRatioJ,
						point.rpm(),
						airDensityKgPerCubicMeter,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE,
						ambientTemperatureCelsius,
						ambientHumidity
				)));
			}
		}
		for (ConfigurationDiagnosticPoint point : diagnosticCurvePoints(
				presetName,
				config,
				rotor,
				airDensityKgPerCubicMeter,
				ambientTemperatureCelsius,
				ambientHumidity
		)) {
			lines.add(csvLine(point));
		}
		for (ConfigurationDiagnosticPoint point : targetThrustCurvePoints(
				presetName,
				config,
				rotor,
				airDensityKgPerCubicMeter,
				ambientTemperatureCelsius,
				ambientHumidity
		)) {
			lines.add(csvLine(point));
		}
		for (ConfigurationDiagnosticPoint point : trimCurvePoints(
				presetName,
				config,
				rotor,
				airDensityKgPerCubicMeter,
				ambientTemperatureCelsius,
				ambientHumidity
		)) {
			lines.add(csvLine(point));
		}
		return List.copyOf(lines);
	}

	private static ConfigurationDiagnosticPoint sampleUniformSignedAxial(
			String presetName,
			String caseName,
			DroneConfig config,
			double advanceRatioJ,
			double rpm,
			double airDensityKgPerCubicMeter,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		double axialSpeed = advanceRatioJ
				* rpm
				/ 60.0
				* config.rotors().get(0).radiusMeters()
				* 2.0;
		double[] axialSpeeds = fill(config.rotors().size(), axialSpeed);
		double[] omegas = fill(config.rotors().size(), rpm / RPM_PER_RADIAN_PER_SECOND);
		PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample aggregate =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredConfigurationFromSignedAxialAdvanceSpeeds(
						presetName,
						caseName,
						config,
						axialSpeeds,
						omegas,
						airDensityKgPerCubicMeter,
						envelopePolicy,
						ambientTemperatureCelsius,
						ambientHumidity
				);
		Vec3 relativeAirVelocity = rotorAxisBody(config.rotors().get(0)).multiply(axialSpeed);
		return directPoint(axialSpeed, relativeAirVelocity, Vec3.ZERO, aggregate);
	}

	private static List<ConfigurationDiagnosticPoint> diagnosticCurvePoints(
			String presetName,
			DroneConfig config,
			RotorSpec rotor,
			double airDensityKgPerCubicMeter,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		double hoverRpm = hoverRpm(config, rotor);
		double hoverOmega = hoverRpm / RPM_PER_RADIAN_PER_SECOND;
		ConfigurationDiagnosticPoint reverseClamp = sampleUniformSignedAxialSpeed(
				presetName,
				"static_anchored_configuration_reverse_axial_clamp",
				config,
				REVERSE_AXIAL_DIAGNOSTIC_SPEED_METERS_PER_SECOND,
				hoverOmega,
				airDensityKgPerCubicMeter,
				PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.CLAMP_TO_ENVELOPE,
				ambientTemperatureCelsius,
				ambientHumidity
		);
		double blockedAxialSpeed = OUT_OF_ENVELOPE_DIAGNOSTIC_ADVANCE_RATIO_J
				* hoverRpm
				/ 60.0
				* rotor.radiusMeters()
				* 2.0;
		ConfigurationDiagnosticPoint highAdvanceBlocked = sampleUniformSignedAxialSpeed(
				presetName,
				"static_anchored_configuration_high_j_block",
				config,
				blockedAxialSpeed,
				hoverOmega,
				airDensityKgPerCubicMeter,
				PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE,
				ambientTemperatureCelsius,
				ambientHumidity
		);
		double transverseAxialSpeed = TRANSVERSE_INFLOW_DIAGNOSTIC_ADVANCE_RATIO_J
				* hoverRpm
				/ 60.0
				* rotor.radiusMeters()
				* 2.0;
		Vec3 relativeAirVelocity = rotorAxisBody(rotor)
				.multiply(transverseAxialSpeed)
				.add(diagnosticTransverseAirVelocity(
						rotor,
						TRANSVERSE_INFLOW_DIAGNOSTIC_SPEED_METERS_PER_SECOND
				));
		Vec3[] relativeAirVelocities = fill(config.rotors().size(), relativeAirVelocity);
		double[] omegas = fill(config.rotors().size(), hoverOmega);
		PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample transverseAggregate =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredConfigurationFromRelativeAirVelocities(
						presetName,
						"static_anchored_configuration_transverse_inflow_diagnostic",
						config,
						relativeAirVelocities,
						omegas,
						airDensityKgPerCubicMeter,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE,
						ambientTemperatureCelsius,
						ambientHumidity
				);
		ConfigurationDiagnosticPoint transverseDiagnostic =
				directPoint(transverseAxialSpeed, relativeAirVelocity, Vec3.ZERO, transverseAggregate);
		double bodyRateAxialSpeed = BODY_RATE_DIAGNOSTIC_ADVANCE_RATIO_J
				* hoverRpm
				/ 60.0
				* rotor.radiusMeters()
				* 2.0;
		Vec3 bodyRateRelativeAirVelocity = rotorAxisBody(rotor).multiply(bodyRateAxialSpeed);
		Vec3 bodyRate = new Vec3(BODY_RATE_DIAGNOSTIC_ROLL_RATE_RADIANS_PER_SECOND, 0.0, 0.0);
		PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample bodyRateAggregate =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredConfigurationFromBodyKinematics(
						presetName,
						"static_anchored_configuration_body_rate_roll_diagnostic",
						config,
						bodyRateRelativeAirVelocity,
						bodyRate,
						omegas,
						airDensityKgPerCubicMeter,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE,
						ambientTemperatureCelsius,
						ambientHumidity
				);
		ConfigurationDiagnosticPoint bodyRateDiagnostic =
				directPoint(bodyRateAxialSpeed, bodyRateRelativeAirVelocity, bodyRate, bodyRateAggregate);
		return List.of(reverseClamp, highAdvanceBlocked, transverseDiagnostic, bodyRateDiagnostic);
	}

	private static List<ConfigurationDiagnosticPoint> targetThrustCurvePoints(
			String presetName,
			DroneConfig config,
			RotorSpec rotor,
			double airDensityKgPerCubicMeter,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		double hoverOmega = hoverRpm(config, rotor) / RPM_PER_RADIAN_PER_SECOND;
		double weightThrust = config.massKg() * config.gravityMetersPerSecondSquared();
		double forwardAxialSpeed = TARGET_THRUST_FORWARD_ADVANCE_RATIO_J
				* hoverRpm(config, rotor)
				/ 60.0
				* rotor.radiusMeters()
				* 2.0;
		double maxOmega = rotor.maxOmegaRadiansPerSecond();
		List<ConfigurationDiagnosticPoint> points = new ArrayList<>();
		points.add(targetSolutionPoint(
				0.0,
				Vec3.ZERO,
				PropellerArchiveCtCpJRotorForceModel.solveStaticAnchoredConfigurationRpmForTargetThrust(
						presetName,
						"static_anchored_configuration_target_hover_solve",
						config,
						0.0,
						weightThrust,
						hoverOmega * 0.55,
						hoverOmega * 1.45,
						airDensityKgPerCubicMeter,
						ambientTemperatureCelsius,
						ambientHumidity
				)
		));
		points.add(targetSolutionPoint(
				forwardAxialSpeed,
				rotorAxisBody(rotor).multiply(forwardAxialSpeed),
				PropellerArchiveCtCpJRotorForceModel.solveStaticAnchoredConfigurationRpmForTargetThrust(
						presetName,
						"static_anchored_configuration_target_forward_solve",
						config,
						forwardAxialSpeed,
						weightThrust,
						hoverOmega * 0.60,
						hoverOmega * 1.80,
						airDensityKgPerCubicMeter,
						ambientTemperatureCelsius,
						ambientHumidity
				)
		));
		PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample maxStatic =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredConfigurationFromSignedAxialAdvanceSpeeds(
						presetName,
						"static_anchored_configuration_target_upper_reference",
						config,
						fill(config.rotors().size(), 0.0),
						fill(config.rotors().size(), maxOmega),
						airDensityKgPerCubicMeter,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE,
						ambientTemperatureCelsius,
						ambientHumidity
				);
		points.add(targetSolutionPoint(
				0.0,
				Vec3.ZERO,
				PropellerArchiveCtCpJRotorForceModel.solveStaticAnchoredConfigurationRpmForTargetThrust(
						presetName,
						"static_anchored_configuration_target_upper_below_target",
						config,
						0.0,
						maxStatic.totalThrustNewtons() * 1.20,
						hoverOmega * 0.60,
						maxOmega,
						airDensityKgPerCubicMeter,
						ambientTemperatureCelsius,
						ambientHumidity
				)
		));
		points.add(targetSolutionPoint(
				TARGET_THRUST_HIGH_J_BLOCK_SPEED_METERS_PER_SECOND,
				rotorAxisBody(rotor).multiply(TARGET_THRUST_HIGH_J_BLOCK_SPEED_METERS_PER_SECOND),
				PropellerArchiveCtCpJRotorForceModel.solveStaticAnchoredConfigurationRpmForTargetThrust(
						presetName,
						"static_anchored_configuration_target_high_j_block",
						config,
						TARGET_THRUST_HIGH_J_BLOCK_SPEED_METERS_PER_SECOND,
						weightThrust,
						3_000.0 / RPM_PER_RADIAN_PER_SECOND,
						6_000.0 / RPM_PER_RADIAN_PER_SECOND,
						airDensityKgPerCubicMeter,
						ambientTemperatureCelsius,
						ambientHumidity
				)
		));
		return List.copyOf(points);
	}

	private static List<ConfigurationDiagnosticPoint> trimCurvePoints(
			String presetName,
			DroneConfig config,
			RotorSpec rotor,
			double airDensityKgPerCubicMeter,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		double hoverOmega = hoverRpm(config, rotor) / RPM_PER_RADIAN_PER_SECOND;
		double weightThrust = config.massKg() * config.gravityMetersPerSecondSquared();
		double maxOmega = rotor.maxOmegaRadiansPerSecond();
		List<ConfigurationDiagnosticPoint> points = new ArrayList<>();
		points.add(trimSolutionPoint(
				0.0,
				Vec3.ZERO,
				PropellerArchiveCtCpJRotorForceModel.solveStaticAnchoredConfigurationTrim(
						presetName,
						"static_anchored_configuration_trim_body_torque",
						config,
						0.0,
						weightThrust,
						TRIM_DIAGNOSTIC_BODY_TORQUE_NEWTON_METERS,
						hoverOmega * 0.50,
						hoverOmega * 1.60,
						airDensityKgPerCubicMeter,
						ambientTemperatureCelsius,
						ambientHumidity
				)
		));
		points.add(trimSolutionPoint(
				0.0,
				Vec3.ZERO,
				PropellerArchiveCtCpJRotorForceModel.solveStaticAnchoredConfigurationTrim(
						presetName,
						"static_anchored_configuration_trim_upper_below_target",
						config,
						0.0,
						config.totalMaxThrustNewtons() * 1.20,
						Vec3.ZERO,
						2_500.0 / RPM_PER_RADIAN_PER_SECOND,
						maxOmega * 0.55,
						airDensityKgPerCubicMeter,
						ambientTemperatureCelsius,
						ambientHumidity
				)
		));
		return List.copyOf(points);
	}

	private static ConfigurationDiagnosticPoint sampleUniformSignedAxialSpeed(
			String presetName,
			String caseName,
			DroneConfig config,
			double signedAxialSpeedMetersPerSecond,
			double omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		double[] axialSpeeds = fill(config.rotors().size(), signedAxialSpeedMetersPerSecond);
		double[] omegas = fill(config.rotors().size(), omegaRadiansPerSecond);
		PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample aggregate =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredConfigurationFromSignedAxialAdvanceSpeeds(
						presetName,
						caseName,
						config,
						axialSpeeds,
						omegas,
						airDensityKgPerCubicMeter,
						envelopePolicy,
						ambientTemperatureCelsius,
						ambientHumidity
				);
		Vec3 relativeAirVelocity = rotorAxisBody(config.rotors().get(0)).multiply(signedAxialSpeedMetersPerSecond);
		return directPoint(signedAxialSpeedMetersPerSecond, relativeAirVelocity, Vec3.ZERO, aggregate);
	}

	private static String csvLine(ConfigurationDiagnosticPoint point) {
		PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample aggregate = point.aggregate();
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample first = firstSample(aggregate);
		PropellerArchiveCtCpJLookupEvaluator.LookupResult lookup = first.lookup();
		return String.join(",",
				escape(lookup.presetName()),
				escape(lookup.caseName()),
				number(lookup.queryAdvanceRatioJ()),
				number(lookup.queryRpm()),
				number(point.targetThrustNewtons()),
				number(point.targetThrustResidualNewtons()),
				escape(point.targetThrustSolveStatus()),
				Integer.toString(point.targetThrustSolveIterations()),
				escape(point.trimSolveStatus()),
				number(point.trimTargetBodyTorqueNewtonMeters().x()),
				number(point.trimTargetBodyTorqueNewtonMeters().y()),
				number(point.trimTargetBodyTorqueNewtonMeters().z()),
				number(point.trimBodyTorqueResidualNewtonMeters().x()),
				number(point.trimBodyTorqueResidualNewtonMeters().y()),
				number(point.trimBodyTorqueResidualNewtonMeters().z()),
				number(point.trimMinAllocatedRotorThrustNewtons()),
				number(point.trimMaxAllocatedRotorThrustNewtons()),
				number(minEffectiveAdvanceRatioJ(aggregate)),
				number(maxEffectiveAdvanceRatioJ(aggregate)),
				number(minEffectiveRpm(aggregate)),
				number(maxEffectiveRpm(aggregate)),
				Integer.toString(aggregate.rotorSamples().size()),
				Integer.toString(aggregate.acceptedRotorCount()),
				Integer.toString(aggregate.runtimeForceReplacementAcceptedRotorCount()),
				Integer.toString(aggregate.blockedRotorCount()),
				Integer.toString(aggregate.clampedRotorCount()),
				number(point.querySignedAxialSpeedMetersPerSecond()),
				number(point.relativeAirVelocityBodyMetersPerSecond().x()),
				number(point.relativeAirVelocityBodyMetersPerSecond().y()),
				number(point.relativeAirVelocityBodyMetersPerSecond().z()),
				number(point.angularVelocityBodyRadiansPerSecond().x()),
				number(point.angularVelocityBodyRadiansPerSecond().y()),
				number(point.angularVelocityBodyRadiansPerSecond().z()),
				number(first.transverseAirSpeedMetersPerSecond()),
				number(Math.toDegrees(first.inflowAngleRadians())),
				number(aggregate.totalThrustForceBodyNewtons().x()),
				number(aggregate.totalThrustForceBodyNewtons().y()),
				number(aggregate.totalThrustForceBodyNewtons().z()),
				number(aggregate.totalReactionTorqueBodyNewtonMeters().x()),
				number(aggregate.totalReactionTorqueBodyNewtonMeters().y()),
				number(aggregate.totalReactionTorqueBodyNewtonMeters().z()),
				number(aggregate.totalThrustMomentBodyNewtonMeters().x()),
				number(aggregate.totalThrustMomentBodyNewtonMeters().y()),
				number(aggregate.totalThrustMomentBodyNewtonMeters().z()),
				number(aggregate.totalBodyTorqueNewtonMeters().x()),
				number(aggregate.totalBodyTorqueNewtonMeters().y()),
				number(aggregate.totalBodyTorqueNewtonMeters().z()),
				number(aggregate.totalThrustNewtons()),
				number(aggregate.totalShaftPowerWatts()),
				number(aggregate.totalShaftTorqueNewtonMeters()),
				number(aggregate.totalDiskMassFlowKilogramsPerSecond()),
				number(aggregate.totalUsefulAxialThrustPowerWatts()),
				number(aggregate.totalIdealInducedPowerWatts()),
				number(aggregate.totalIdealMomentumPowerWatts()),
				number(ratio(aggregate.totalIdealMomentumPowerWatts(), aggregate.totalShaftPowerWatts())),
				number(aggregate.totalWakeSwirlKineticPowerWatts()),
				number(aggregate.totalWakeKineticPowerWatts()),
				number(aggregate.totalWakeKineticPowerResidualWatts()),
				number(aggregate.totalWakeKineticPowerOverShaftPower()),
				number(aggregate.runtimeForceReplacementThrustForceBodyNewtons().x()),
				number(aggregate.runtimeForceReplacementThrustForceBodyNewtons().y()),
				number(aggregate.runtimeForceReplacementThrustForceBodyNewtons().z()),
				number(aggregate.runtimeForceReplacementReactionTorqueBodyNewtonMeters().x()),
				number(aggregate.runtimeForceReplacementReactionTorqueBodyNewtonMeters().y()),
				number(aggregate.runtimeForceReplacementReactionTorqueBodyNewtonMeters().z()),
				number(aggregate.runtimeForceReplacementThrustMomentBodyNewtonMeters().x()),
				number(aggregate.runtimeForceReplacementThrustMomentBodyNewtonMeters().y()),
				number(aggregate.runtimeForceReplacementThrustMomentBodyNewtonMeters().z()),
				number(aggregate.runtimeForceReplacementTotalBodyTorqueNewtonMeters().x()),
				number(aggregate.runtimeForceReplacementTotalBodyTorqueNewtonMeters().y()),
				number(aggregate.runtimeForceReplacementTotalBodyTorqueNewtonMeters().z()),
				number(aggregate.runtimeForceReplacementThrustNewtons()),
				number(aggregate.runtimeForceReplacementShaftPowerWatts()),
				number(aggregate.runtimeForceReplacementShaftTorqueNewtonMeters()),
				number(aggregate.runtimeForceReplacementDiskMassFlowKilogramsPerSecond()),
				number(aggregate.runtimeForceReplacementUsefulAxialThrustPowerWatts()),
				number(aggregate.runtimeForceReplacementIdealInducedPowerWatts()),
				number(aggregate.runtimeForceReplacementIdealMomentumPowerWatts()),
				number(ratio(
						aggregate.runtimeForceReplacementIdealMomentumPowerWatts(),
						aggregate.runtimeForceReplacementShaftPowerWatts())),
				number(aggregate.runtimeForceReplacementWakeSwirlKineticPowerWatts()),
				number(aggregate.runtimeForceReplacementWakeKineticPowerWatts()),
				number(aggregate.runtimeForceReplacementWakeKineticPowerResidualWatts()),
				number(aggregate.runtimeForceReplacementWakeKineticPowerOverShaftPower()),
				escape(runtimeEligibilityStatus(aggregate)),
				escape(lookupStatusSummary(aggregate)),
				escape(sourceIdSummary(aggregate))
		);
	}

	private static PropellerArchiveCtCpJRotorForceModel.RotorForceSample firstSample(
			PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample aggregate
	) {
		if (aggregate.rotorSamples().isEmpty()) {
			throw new IllegalArgumentException("configuration aggregate has no rotor samples.");
		}
		return aggregate.rotorSamples().get(0);
	}

	private static double minEffectiveAdvanceRatioJ(
			PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample aggregate
	) {
		return aggregate.rotorSamples().stream()
				.mapToDouble(sample -> sample.lookup().effectiveAdvanceRatioJ())
				.min()
				.orElse(0.0);
	}

	private static double maxEffectiveAdvanceRatioJ(
			PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample aggregate
	) {
		return aggregate.rotorSamples().stream()
				.mapToDouble(sample -> sample.lookup().effectiveAdvanceRatioJ())
				.max()
				.orElse(0.0);
	}

	private static double minEffectiveRpm(
			PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample aggregate
	) {
		return aggregate.rotorSamples().stream()
				.mapToDouble(sample -> sample.lookup().effectiveRpm())
				.min()
				.orElse(0.0);
	}

	private static double maxEffectiveRpm(
			PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample aggregate
	) {
		return aggregate.rotorSamples().stream()
				.mapToDouble(sample -> sample.lookup().effectiveRpm())
				.max()
				.orElse(0.0);
	}

	private static String runtimeEligibilityStatus(
			PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample aggregate
	) {
		if (aggregate.blockedRotorCount() > 0) {
			return firstSample(aggregate).lookup().status();
		}
		if (aggregate.clampedRotorCount() > 0) {
			return "CLAMPED";
		}
		if (aggregate.runtimeForceReplacementAcceptedRotorCount() == aggregate.rotorSamples().size()) {
			return "ACCEPTED";
		}
		if (aggregate.runtimeForceReplacementAcceptedRotorCount() > 0) {
			return "PARTIAL_ACCEPTED";
		}
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample first = firstSample(aggregate);
		if (!first.momentumPowerClosureSatisfied()) {
			return "MOMENTUM_POWER_CLOSURE_FAILED";
		}
		if (!first.wakePowerClosureSatisfied()) {
			return "WAKE_POWER_CLOSURE_FAILED";
		}
		if (!first.runtimeInflowEnvelopeSatisfied()) {
			return "OBLIQUE_INFLOW_OUTSIDE_RUNTIME_ENVELOPE";
		}
		if (!first.runtimeOperatingPointEnvelopeSatisfied()) {
			return "OPERATING_POINT_OUTSIDE_RUNTIME_ENVELOPE";
		}
		return "NOT_RUNTIME_CANDIDATE";
	}

	private static String lookupStatusSummary(
			PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample aggregate
	) {
		List<String> statuses = new ArrayList<>();
		for (PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample : aggregate.rotorSamples()) {
			addDistinct(statuses, sample.lookup().status());
		}
		return String.join("|", statuses);
	}

	private static String sourceIdSummary(
			PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample aggregate
	) {
		List<String> sources = new ArrayList<>();
		for (PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample : aggregate.rotorSamples()) {
			addDistinct(sources, sample.lookup().dataSourceId());
		}
		return String.join("|", sources);
	}

	private static List<StaticCurvePoint> staticAnchoredConfigurationCurvePoints(
			DroneConfig config,
			RotorSpec rotor
	) {
		List<StaticCurvePoint> points = new ArrayList<>();
		addStaticPoint(points, "static_anchored_configuration_hover", hoverRpm(config, rotor));
		addStaticPoint(points, "static_anchored_configuration_half_max", rotor.maxOmegaRadiansPerSecond()
				* RPM_PER_RADIAN_PER_SECOND * 0.5);
		addStaticPoint(points, "static_anchored_configuration_max", rotor.maxOmegaRadiansPerSecond()
				* RPM_PER_RADIAN_PER_SECOND);
		return points.stream()
				.sorted(Comparator.comparingDouble(StaticCurvePoint::rpm))
				.toList();
	}

	private static List<Double> acceptedAdvanceShapeSampleAdvanceRatios(
			String presetName,
			double propellerDiameterMeters,
			double airDensityKgPerCubicMeter
	) {
		List<Double> advanceRatios = new ArrayList<>();
		for (PropellerArchiveCtCpJLookupEvaluator.LookupQuery query
				: PropellerArchiveCtCpJLookupEvaluator.acceptedReferenceCurveQueries(
						presetName,
						propellerDiameterMeters,
						airDensityKgPerCubicMeter
				)) {
			addDistinct(advanceRatios, query.advanceRatioJ());
		}
		advanceRatios.sort(Double::compare);
		return List.copyOf(advanceRatios);
	}

	private static void addStaticPoint(List<StaticCurvePoint> points, String caseName, double rpm) {
		if (!Double.isFinite(rpm) || rpm <= 0.0) {
			return;
		}
		for (StaticCurvePoint point : points) {
			if (Math.abs(point.rpm() - rpm) < 1.0e-6) {
				return;
			}
		}
		points.add(new StaticCurvePoint(caseName, rpm));
	}

	private static double hoverRpm(DroneConfig config, RotorSpec rotor) {
		double perRotorHoverThrust = config.massKg()
				* config.gravityMetersPerSecondSquared()
				/ config.rotors().size();
		return Math.sqrt(perRotorHoverThrust / rotor.thrustCoefficient()) * RPM_PER_RADIAN_PER_SECOND;
	}

	private static DroneConfig configForPreset(String presetName) {
		return switch (presetName) {
			case "apDrone" -> DroneConfig.apDrone();
			case "racingQuad" -> DroneConfig.racingQuad();
			case "cinewhoop" -> DroneConfig.cinewhoop();
			case "heavyLift" -> DroneConfig.heavyLift();
			default -> throw new IllegalArgumentException("unknown DroneConfig preset: " + presetName);
		};
	}

	private static Vec3 rotorAxisBody(RotorSpec rotor) {
		Vec3 axis = rotor.thrustAxisBody();
		if (axis == null || !axis.isFinite() || axis.lengthSquared() <= 1.0e-9) {
			return new Vec3(0.0, 1.0, 0.0);
		}
		return axis.normalized();
	}

	private static Vec3 diagnosticTransverseAirVelocity(RotorSpec rotor, double speedMetersPerSecond) {
		Vec3 axis = rotorAxisBody(rotor);
		Vec3 basis = Math.abs(axis.x()) < 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 0.0, 1.0);
		Vec3 transverse = basis.subtract(axis.multiply(basis.dot(axis)));
		if (!transverse.isFinite() || transverse.lengthSquared() <= 1.0e-9) {
			transverse = new Vec3(0.0, 0.0, 1.0).subtract(axis.multiply(axis.z()));
		}
		return transverse.normalized().multiply(speedMetersPerSecond);
	}

	private static double[] fill(int count, double value) {
		double[] values = new double[count];
		for (int i = 0; i < values.length; i++) {
			values[i] = value;
		}
		return values;
	}

	private static Vec3[] fill(int count, Vec3 value) {
		Vec3[] values = new Vec3[count];
		for (int i = 0; i < values.length; i++) {
			values[i] = value;
		}
		return values;
	}

	private static void addDistinct(List<String> values, String candidate) {
		if (candidate == null || candidate.isBlank()) {
			return;
		}
		if (!values.contains(candidate)) {
			values.add(candidate);
		}
	}

	private static void addDistinct(List<Double> values, double candidate) {
		for (double value : values) {
			if (Math.abs(value - candidate) <= 1.0e-9) {
				return;
			}
		}
		values.add(candidate);
	}

	private static ConfigurationDiagnosticPoint directPoint(
			double querySignedAxialSpeedMetersPerSecond,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			Vec3 angularVelocityBodyRadiansPerSecond,
			PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample aggregate
	) {
		return new ConfigurationDiagnosticPoint(
				querySignedAxialSpeedMetersPerSecond,
				relativeAirVelocityBodyMetersPerSecond,
				angularVelocityBodyRadiansPerSecond,
				aggregate,
				aggregate.totalThrustNewtons(),
				0.0,
				"DIRECT_SAMPLE",
				0,
				"DIRECT_SAMPLE",
				Vec3.ZERO,
				Vec3.ZERO,
				0.0,
				0.0
		);
	}

	private static ConfigurationDiagnosticPoint targetSolutionPoint(
			double querySignedAxialSpeedMetersPerSecond,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			PropellerArchiveCtCpJRotorForceModel.ConfigurationTargetThrustSolution solution
	) {
		return new ConfigurationDiagnosticPoint(
				querySignedAxialSpeedMetersPerSecond,
				relativeAirVelocityBodyMetersPerSecond,
				Vec3.ZERO,
				solution.solutionSample(),
				solution.targetThrustNewtons(),
				solution.thrustResidualNewtons(),
				solution.status().name(),
				solution.iterations(),
				"TARGET_THRUST_ONLY",
				Vec3.ZERO,
				Vec3.ZERO,
				0.0,
				0.0
		);
	}

	private static ConfigurationDiagnosticPoint trimSolutionPoint(
			double querySignedAxialSpeedMetersPerSecond,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			PropellerArchiveCtCpJRotorForceModel.ConfigurationTrimSolution solution
	) {
		return new ConfigurationDiagnosticPoint(
				querySignedAxialSpeedMetersPerSecond,
				relativeAirVelocityBodyMetersPerSecond,
				Vec3.ZERO,
				solution.solutionSample(),
				solution.targetThrustNewtons(),
				solution.thrustResidualNewtons(),
				"TRIM_SOLVE",
				0,
				solution.status().name(),
				solution.targetBodyTorqueNewtonMeters(),
				solution.bodyTorqueResidualNewtonMeters(),
				solution.minAllocatedRotorThrustNewtons(),
				solution.maxAllocatedRotorThrustNewtons()
		);
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

	private static String number(double value) {
		return String.format(Locale.ROOT, "%.15g", value);
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= 1.0e-12) {
			return 0.0;
		}
		return numerator / denominator;
	}

	private record StaticCurvePoint(String caseName, double rpm) {
	}

	private record ConfigurationDiagnosticPoint(
			double querySignedAxialSpeedMetersPerSecond,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			Vec3 angularVelocityBodyRadiansPerSecond,
			PropellerArchiveCtCpJRotorForceModel.RotorForceAggregateSample aggregate,
			double targetThrustNewtons,
			double targetThrustResidualNewtons,
			String targetThrustSolveStatus,
			int targetThrustSolveIterations,
			String trimSolveStatus,
			Vec3 trimTargetBodyTorqueNewtonMeters,
			Vec3 trimBodyTorqueResidualNewtonMeters,
			double trimMinAllocatedRotorThrustNewtons,
			double trimMaxAllocatedRotorThrustNewtons
	) {
		private ConfigurationDiagnosticPoint {
			relativeAirVelocityBodyMetersPerSecond = relativeAirVelocityBodyMetersPerSecond == null
					? Vec3.ZERO
					: relativeAirVelocityBodyMetersPerSecond;
			angularVelocityBodyRadiansPerSecond = angularVelocityBodyRadiansPerSecond == null
					? Vec3.ZERO
					: angularVelocityBodyRadiansPerSecond;
			trimTargetBodyTorqueNewtonMeters = trimTargetBodyTorqueNewtonMeters == null
					? Vec3.ZERO
					: trimTargetBodyTorqueNewtonMeters;
			trimBodyTorqueResidualNewtonMeters = trimBodyTorqueResidualNewtonMeters == null
					? Vec3.ZERO
					: trimBodyTorqueResidualNewtonMeters;
		}
	}
}
