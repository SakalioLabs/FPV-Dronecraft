package com.tenicana.dronecraft.sim.tools;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.MotorBenchCurrentModel;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJDimensionalRotorResponse;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJLookupEvaluator;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJRotorForceModel;
import com.tenicana.dronecraft.sim.RotorSpec;
import com.tenicana.dronecraft.sim.RotorStaticCtCpModel;
import com.tenicana.dronecraft.sim.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CtCpJCurveExporter {
	private static final String HEADER = String.join(",",
			"preset",
			"case",
			"query_j",
			"query_rpm",
			"effective_j",
			"effective_rpm",
			"interpolation_status",
			"clamped",
			"blocked",
			"ct",
			"cp",
			"eta",
			"axial_speed_mps",
			"thrust_n",
			"shaft_power_w",
			"shaft_torque_nm",
			"disk_loading_n_m2",
			"ideal_induced_velocity_mps",
			"ideal_momentum_power_w",
			"ideal_momentum_power_over_shaft_power",
			"source_id",
			"lookup_status",
			"lookup_message",
			"runtime_force_replacement_accepted",
			"query_signed_axial_speed_mps",
			"relative_air_body_x_mps",
			"relative_air_body_y_mps",
			"relative_air_body_z_mps",
			"transverse_air_body_x_mps",
			"transverse_air_body_y_mps",
			"transverse_air_body_z_mps",
			"transverse_air_speed_mps",
			"inflow_angle_deg",
			"thrust_force_body_x_n",
			"thrust_force_body_y_n",
			"thrust_force_body_z_n",
			"reaction_torque_body_x_nm",
			"reaction_torque_body_y_nm",
			"reaction_torque_body_z_nm",
			"thrust_moment_body_x_nm",
			"thrust_moment_body_y_nm",
			"thrust_moment_body_z_nm",
			"total_torque_body_x_nm",
			"total_torque_body_y_nm",
			"total_torque_body_z_nm",
			"momentum_power_closure_satisfied",
			"runtime_eligibility_status",
			"shaft_power_residual_w",
			"shaft_power_residual_fraction",
			"operating_point_temperature_c",
			"operating_point_humidity",
			"operating_point_dynamic_viscosity_pa_s",
			"operating_point_speed_of_sound_mps",
			"rotational_tip_speed_mps",
			"helical_tip_speed_mps",
			"tip_mach",
			"representative_blade_station_speed_mps",
			"representative_blade_chord_m",
			"reynolds_number",
			"reynolds_index"
	);
	private static final double MOMENTUM_POWER_CLOSURE_TOLERANCE = 1.0e-6;
	private static final double RPM_PER_RADIAN_PER_SECOND = 60.0 / (2.0 * Math.PI);
	private static final double REVERSE_AXIAL_DIAGNOSTIC_SPEED_METERS_PER_SECOND = -4.5;
	private static final double OUT_OF_ENVELOPE_DIAGNOSTIC_ADVANCE_RATIO_J = 1.20;
	private static final double TRANSVERSE_INFLOW_DIAGNOSTIC_ADVANCE_RATIO_J = 0.4064;
	private static final double TRANSVERSE_INFLOW_DIAGNOSTIC_SPEED_METERS_PER_SECOND = 2.5;

	private CtCpJCurveExporter() {
	}

	public static void main(String[] args) throws IOException {
		String presetName = args.length >= 1 && !args[0].isBlank()
				? args[0]
				: PropellerArchiveCtCpJLookupEvaluator.DEFAULT_PRESET_NAME;
		Path output = args.length >= 2 && !args[1].isBlank()
				? Path.of(args[1])
				: Path.of("build", "ct-cp-j-curves", presetName + ".csv");
		double airDensity = args.length >= 3 && !args[2].isBlank()
				? Double.parseDouble(args[2])
				: PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
		double diameter = args.length >= 4 && !args[3].isBlank()
				? Double.parseDouble(args[3])
				: defaultPropellerDiameterMeters(presetName);
		double ambientTemperatureCelsius = args.length >= 5 && !args[4].isBlank()
				? Double.parseDouble(args[4])
				: 25.0;
		double ambientHumidity = args.length >= 6 && !args[5].isBlank()
				? Double.parseDouble(args[5])
				: 0.0;
		write(presetName, output, airDensity, diameter, ambientTemperatureCelsius, ambientHumidity);
	}

	public static void write(
			String presetName,
			Path output,
			double airDensityKgPerCubicMeter,
			double propellerDiameterMeters
	) throws IOException {
		write(presetName, output, airDensityKgPerCubicMeter, propellerDiameterMeters, 25.0, 0.0);
	}

	public static void write(
			String presetName,
			Path output,
			double airDensityKgPerCubicMeter,
			double propellerDiameterMeters,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) throws IOException {
		if (output == null) {
			throw new IllegalArgumentException("output path must not be null.");
		}
		List<String> lines = csvLines(
				presetName,
				airDensityKgPerCubicMeter,
				propellerDiameterMeters,
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
			double airDensityKgPerCubicMeter,
			double propellerDiameterMeters
	) {
		return csvLines(presetName, airDensityKgPerCubicMeter, propellerDiameterMeters, 25.0, 0.0);
	}

	public static List<String> csvLines(
			String presetName,
			double airDensityKgPerCubicMeter,
			double propellerDiameterMeters,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		List<PropellerArchiveCtCpJLookupEvaluator.LookupQuery> queries =
				PropellerArchiveCtCpJLookupEvaluator.acceptedReferenceCurveQueries(
						presetName,
						propellerDiameterMeters,
						airDensityKgPerCubicMeter
				);
		List<String> lines = new ArrayList<>();
		lines.add(HEADER);
		DroneConfig config = configForPreset(presetName);
		RotorSpec rotor = config.rotors().get(0).withRadiusMeters(propellerDiameterMeters * 0.5);
		Vec3 rotorArmBody = rotor.positionBodyMeters().subtract(config.centerOfMassOffsetBodyMeters());
		for (PropellerArchiveCtCpJLookupEvaluator.LookupQuery query : queries) {
			PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample sample =
					PropellerArchiveCtCpJLookupEvaluator.sampleRotor(query);
			lines.add(csvLine(sample, rotor, rotorArmBody, ambientTemperatureCelsius, ambientHumidity));
		}
		for (StaticCurvePoint point : staticCurvePoints(presetName, config, rotor)) {
			lines.add(csvLine(RotorStaticCtCpModel.sample(
					presetName,
					point.caseName(),
					rotor,
					point.rpm(),
					airDensityKgPerCubicMeter
			), rotor, rotorArmBody, ambientTemperatureCelsius, ambientHumidity));
		}
		List<Double> advanceRatios = acceptedAdvanceShapeSampleAdvanceRatios(
				presetName,
				propellerDiameterMeters,
				airDensityKgPerCubicMeter
		);
		for (StaticCurvePoint point : staticAnchoredRuntimeCurvePoints(config, rotor)) {
			for (double advanceRatioJ : advanceRatios) {
				PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample =
						PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchored(
								new PropellerArchiveCtCpJRotorForceModel.RotorForceQuery(
										presetName,
										point.caseName(),
										rotor,
										advanceRatioJ,
										point.rpm(),
										airDensityKgPerCubicMeter,
										PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE,
										ambientTemperatureCelsius,
										ambientHumidity
								),
								config.centerOfMassOffsetBodyMeters());
				lines.add(csvLine(
						sample,
						sample.axialAdvanceSpeedMetersPerSecond(),
						ambientTemperatureCelsius,
						ambientHumidity
				));
			}
		}
		for (EnvelopeDiagnosticPoint point : envelopeDiagnosticCurvePoints(
				presetName,
				config,
				rotor,
				airDensityKgPerCubicMeter,
				ambientTemperatureCelsius,
				ambientHumidity
		)) {
			lines.add(csvLine(
					point.sample(),
					point.querySignedAxialSpeedMetersPerSecond(),
					ambientTemperatureCelsius,
					ambientHumidity
			));
		}
		return List.copyOf(lines);
	}

	private static String csvLine(
			PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample sample,
			RotorSpec rotor,
			Vec3 rotorArmBody,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		PropellerArchiveCtCpJLookupEvaluator.LookupResult lookup = sample.lookup();
		double queryAxialSpeed = lookup.queryAdvanceRatioJ()
				* Math.max(0.0, lookup.queryRpm())
				/ 60.0
				* sample.propellerDiameterMeters();
		Vec3 axis = rotorAxisBody(rotor);
		Vec3 thrustForce = axis.multiply(sample.thrustNewtons());
		Vec3 reactionTorque = axis.multiply(rotor.spinDirection() * sample.shaftTorqueNewtonMeters());
		Vec3 thrustMoment = rotorArmBody.cross(thrustForce);
		Vec3 relativeAirVelocity = axis.multiply(queryAxialSpeed);
		PropellerArchiveCtCpJRotorForceModel.RotorOperatingPoint operatingPoint =
				PropellerArchiveCtCpJRotorForceModel.operatingPoint(
						rotor,
						relativeAirVelocity,
						sample.angularVelocityRadiansPerSecond(),
						sample.airDensityKgPerCubicMeter(),
						ambientTemperatureCelsius,
						ambientHumidity
				);
		return csvLine(
				sample,
				false,
				queryAxialSpeed,
				relativeAirVelocity,
				Vec3.ZERO,
				0.0,
				0.0,
				thrustForce,
				reactionTorque,
				thrustMoment,
				thrustMoment.add(reactionTorque),
				runtimeEligibilityStatus(sample, false),
				operatingPoint
		);
	}

	private static String csvLine(
			PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample,
			double querySignedAxialSpeedMetersPerSecond
	) {
		return csvLine(sample, querySignedAxialSpeedMetersPerSecond, 25.0, 0.0);
	}

	private static String csvLine(
			PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample,
			double querySignedAxialSpeedMetersPerSecond,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		boolean runtimeForceReplacementAccepted =
				sample.runtimeForceReplacementAccepted(ambientTemperatureCelsius, ambientHumidity);
		return csvLine(
				sample.dimensionalSample(),
				runtimeForceReplacementAccepted,
				querySignedAxialSpeedMetersPerSecond,
				sample.relativeAirVelocityBodyMetersPerSecond(),
				sample.transverseAirVelocityBodyMetersPerSecond(),
				sample.transverseAirSpeedMetersPerSecond(),
				Math.toDegrees(sample.inflowAngleRadians()),
				sample.thrustForceBodyNewtons(),
				sample.reactionTorqueBodyNewtonMeters(),
				sample.thrustMomentBodyNewtonMeters(),
				sample.totalTorqueBodyNewtonMeters(),
				runtimeEligibilityStatus(
						sample,
						runtimeForceReplacementAccepted,
						ambientTemperatureCelsius,
						ambientHumidity
				),
				sample.operatingPoint(ambientTemperatureCelsius, ambientHumidity)
		);
	}

	private static String csvLine(
			PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample sample,
			boolean runtimeForceReplacementAccepted,
			double querySignedAxialSpeedMetersPerSecond,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			Vec3 transverseAirVelocityBodyMetersPerSecond,
			double transverseAirSpeedMetersPerSecond,
			double inflowAngleDegrees,
			Vec3 thrustForceBodyNewtons,
			Vec3 reactionTorqueBodyNewtonMeters,
			Vec3 thrustMomentBodyNewtonMeters,
			Vec3 totalTorqueBodyNewtonMeters,
			String runtimeEligibilityStatus,
			PropellerArchiveCtCpJRotorForceModel.RotorOperatingPoint operatingPoint
	) {
		PropellerArchiveCtCpJLookupEvaluator.LookupResult lookup = sample.lookup();
		return String.join(",",
				escape(lookup.presetName()),
				escape(lookup.caseName()),
				number(lookup.queryAdvanceRatioJ()),
				number(lookup.queryRpm()),
				number(lookup.effectiveAdvanceRatioJ()),
				number(lookup.effectiveRpm()),
				escape(lookup.interpolationStatus().name()),
				Boolean.toString(lookup.clamped()),
				Boolean.toString(lookup.blocked()),
				number(lookup.thrustCoefficientCt()),
				number(lookup.powerCoefficientCp()),
				number(lookup.propulsiveEfficiencyEta()),
				number(sample.axialAdvanceSpeedMetersPerSecond()),
				number(sample.thrustNewtons()),
				number(sample.shaftPowerWatts()),
				number(sample.shaftTorqueNewtonMeters()),
				number(sample.diskLoadingNewtonsPerSquareMeter()),
				number(sample.idealInducedVelocityMetersPerSecond()),
				number(sample.idealMomentumPowerWatts()),
				number(sample.idealMomentumPowerOverShaftPower()),
				escape(lookup.dataSourceId()),
				escape(lookup.status()),
				escape(lookup.message()),
				Boolean.toString(runtimeForceReplacementAccepted),
				number(querySignedAxialSpeedMetersPerSecond),
				number(relativeAirVelocityBodyMetersPerSecond.x()),
				number(relativeAirVelocityBodyMetersPerSecond.y()),
				number(relativeAirVelocityBodyMetersPerSecond.z()),
				number(transverseAirVelocityBodyMetersPerSecond.x()),
				number(transverseAirVelocityBodyMetersPerSecond.y()),
				number(transverseAirVelocityBodyMetersPerSecond.z()),
				number(transverseAirSpeedMetersPerSecond),
				number(inflowAngleDegrees),
				number(thrustForceBodyNewtons.x()),
				number(thrustForceBodyNewtons.y()),
				number(thrustForceBodyNewtons.z()),
				number(reactionTorqueBodyNewtonMeters.x()),
				number(reactionTorqueBodyNewtonMeters.y()),
				number(reactionTorqueBodyNewtonMeters.z()),
				number(thrustMomentBodyNewtonMeters.x()),
				number(thrustMomentBodyNewtonMeters.y()),
				number(thrustMomentBodyNewtonMeters.z()),
				number(totalTorqueBodyNewtonMeters.x()),
				number(totalTorqueBodyNewtonMeters.y()),
				number(totalTorqueBodyNewtonMeters.z()),
				Boolean.toString(momentumPowerClosureSatisfied(sample.idealMomentumPowerOverShaftPower())),
				escape(runtimeEligibilityStatus),
				number(sample.shaftPowerResidualWatts()),
				number(sample.shaftPowerResidualFraction()),
				number(operatingPoint.ambientTemperatureCelsius()),
				number(operatingPoint.ambientHumidity()),
				number(operatingPoint.dynamicViscosityPascalSeconds()),
				number(operatingPoint.speedOfSoundMetersPerSecond()),
				number(operatingPoint.rotationalTipSpeedMetersPerSecond()),
				number(operatingPoint.helicalTipSpeedMetersPerSecond()),
				number(operatingPoint.tipMach()),
				number(operatingPoint.representativeBladeStationSpeedMetersPerSecond()),
				number(operatingPoint.representativeBladeChordMeters()),
				number(operatingPoint.reynoldsNumber()),
				number(operatingPoint.reynoldsIndex())
		);
	}

	private static String csvLine(
			RotorStaticCtCpModel.StaticRotorSample sample,
			RotorSpec rotor,
			Vec3 rotorArmBody,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		Vec3 axis = rotorAxisBody(rotor);
		Vec3 thrustForce = axis.multiply(sample.thrustNewtons());
		Vec3 reactionTorque = axis.multiply(rotor.spinDirection() * sample.shaftTorqueNewtonMeters());
		Vec3 thrustMoment = rotorArmBody.cross(thrustForce);
		Vec3 totalTorque = thrustMoment.add(reactionTorque);
		PropellerArchiveCtCpJRotorForceModel.RotorOperatingPoint operatingPoint =
				PropellerArchiveCtCpJRotorForceModel.operatingPoint(
						rotor,
						Vec3.ZERO,
						sample.angularVelocityRadiansPerSecond(),
						sample.airDensityKgPerCubicMeter(),
						ambientTemperatureCelsius,
						ambientHumidity
				);
		return String.join(",",
				escape(sample.presetName()),
				escape(sample.caseName()),
				number(0.0),
				number(sample.rpm()),
				number(0.0),
				number(sample.rpm()),
				escape("STATIC_ROTOR_SPEC"),
				Boolean.toString(false),
				Boolean.toString(false),
				number(sample.thrustCoefficientCt()),
				number(sample.powerCoefficientCp()),
				number(sample.propulsiveEfficiencyEta()),
				number(0.0),
				number(sample.thrustNewtons()),
				number(sample.shaftPowerWatts()),
				number(sample.shaftTorqueNewtonMeters()),
				number(sample.diskLoadingNewtonsPerSquareMeter()),
				number(sample.idealInducedVelocityMetersPerSecond()),
				number(sample.idealMomentumPowerWatts()),
				number(sample.idealMomentumPowerOverShaftPower()),
				escape(sample.sourceId()),
				escape("STATIC_ROTOR_SPEC"),
				escape("rotor-spec-static-reference-sample"),
				Boolean.toString(false),
				number(0.0),
				number(0.0),
				number(0.0),
				number(0.0),
				number(0.0),
				number(0.0),
				number(0.0),
				number(0.0),
				number(0.0),
				number(thrustForce.x()),
				number(thrustForce.y()),
				number(thrustForce.z()),
				number(reactionTorque.x()),
				number(reactionTorque.y()),
				number(reactionTorque.z()),
				number(thrustMoment.x()),
				number(thrustMoment.y()),
				number(thrustMoment.z()),
				number(totalTorque.x()),
				number(totalTorque.y()),
				number(totalTorque.z()),
				Boolean.toString(momentumPowerClosureSatisfied(sample.idealMomentumPowerOverShaftPower())),
				escape(staticReferenceEligibilityStatus(sample)),
				number(sample.shaftPowerResidualWatts()),
				number(sample.shaftPowerResidualFraction()),
				number(operatingPoint.ambientTemperatureCelsius()),
				number(operatingPoint.ambientHumidity()),
				number(operatingPoint.dynamicViscosityPascalSeconds()),
				number(operatingPoint.speedOfSoundMetersPerSecond()),
				number(operatingPoint.rotationalTipSpeedMetersPerSecond()),
				number(operatingPoint.helicalTipSpeedMetersPerSecond()),
				number(operatingPoint.tipMach()),
				number(operatingPoint.representativeBladeStationSpeedMetersPerSecond()),
				number(operatingPoint.representativeBladeChordMeters()),
				number(operatingPoint.reynoldsNumber()),
				number(operatingPoint.reynoldsIndex())
		);
	}

	private static boolean momentumPowerClosureSatisfied(double idealMomentumPowerOverShaftPower) {
		return Double.isFinite(idealMomentumPowerOverShaftPower)
				&& idealMomentumPowerOverShaftPower > 0.0
				&& idealMomentumPowerOverShaftPower <= 1.0 + MOMENTUM_POWER_CLOSURE_TOLERANCE;
	}

	private static String runtimeEligibilityStatus(
			PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample,
			boolean runtimeForceReplacementAccepted,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		if (runtimeForceReplacementAccepted) {
			return "ACCEPTED";
		}
		PropellerArchiveCtCpJLookupEvaluator.LookupResult lookup = sample.lookup();
		if (lookup.blocked()) {
			return lookup.status();
		}
		if (lookup.clamped()) {
			return "CLAMPED";
		}
		if (!sample.momentumPowerClosureSatisfied()) {
			return "MOMENTUM_POWER_CLOSURE_FAILED";
		}
		if (!sample.runtimeInflowEnvelopeSatisfied()) {
			return "OBLIQUE_INFLOW_OUTSIDE_RUNTIME_ENVELOPE";
		}
		if (!sample.runtimeOperatingPointEnvelopeSatisfied(ambientTemperatureCelsius, ambientHumidity)) {
			return "OPERATING_POINT_OUTSIDE_RUNTIME_ENVELOPE";
		}
		return "NOT_RUNTIME_CANDIDATE";
	}

	private static String runtimeEligibilityStatus(
			PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample sample,
			boolean runtimeForceReplacementAccepted
	) {
		if (runtimeForceReplacementAccepted) {
			return "ACCEPTED";
		}
		PropellerArchiveCtCpJLookupEvaluator.LookupResult lookup = sample.lookup();
		if (lookup.blocked()) {
			return lookup.status();
		}
		if (lookup.clamped()) {
			return "CLAMPED";
		}
		if (!momentumPowerClosureSatisfied(sample.idealMomentumPowerOverShaftPower())) {
			return "MOMENTUM_POWER_CLOSURE_FAILED";
		}
		return "NOT_RUNTIME_CANDIDATE";
	}

	private static String staticReferenceEligibilityStatus(RotorStaticCtCpModel.StaticRotorSample sample) {
		return momentumPowerClosureSatisfied(sample.idealMomentumPowerOverShaftPower())
				? "NOT_RUNTIME_CANDIDATE"
				: "MOMENTUM_POWER_CLOSURE_FAILED";
	}

	private static double defaultPropellerDiameterMeters(String presetName) {
		return configForPreset(presetName).rotors().get(0).radiusMeters() * 2.0;
	}

	private static DroneConfig configForPreset(String presetName) {
		DroneConfig config = switch (presetName) {
			case "apDrone" -> DroneConfig.apDrone();
			case "racingQuad" -> DroneConfig.racingQuad();
			case "cinewhoop" -> DroneConfig.cinewhoop();
			case "heavyLift" -> DroneConfig.heavyLift();
			default -> throw new IllegalArgumentException("unknown DroneConfig preset: " + presetName);
		};
		return config;
	}

	private static List<StaticCurvePoint> staticCurvePoints(
			String presetName,
			DroneConfig config,
			RotorSpec rotor
	) {
		List<StaticCurvePoint> points = new ArrayList<>();
		addStaticPoint(points, "static_rotor_spec_low_rpm_anchor", 1_477.8);
		addStaticPoint(points, "static_rotor_spec_hover", hoverRpm(config, rotor));
		if ("apDrone".equals(presetName)) {
			addStaticPoint(points, "static_rotor_spec_aiio_low_dynamic_mean",
					MotorBenchCurrentModel.AIIO_LOW_DYNAMIC_STRICT_ROTOR_RPM_MEAN);
			addStaticPoint(points, "static_rotor_spec_urban_p95",
					MotorBenchCurrentModel.APDRONE_URBAN_MECHANICAL_RPM_P95);
			addStaticPoint(points, "static_rotor_spec_foxeer_public_test",
					MotorBenchCurrentModel.FOXEER_DONUT_5145_PUBLIC_TEST_RPM);
		}
		addStaticPoint(points, "static_rotor_spec_half_max", rotor.maxOmegaRadiansPerSecond()
				* RPM_PER_RADIAN_PER_SECOND * 0.5);
		addStaticPoint(points, "static_rotor_spec_max", rotor.maxOmegaRadiansPerSecond()
				* RPM_PER_RADIAN_PER_SECOND);
		return points.stream()
				.sorted((first, second) -> Double.compare(first.rpm(), second.rpm()))
				.toList();
	}

	private static List<EnvelopeDiagnosticPoint> envelopeDiagnosticCurvePoints(
			String presetName,
			DroneConfig config,
			RotorSpec rotor,
			double airDensityKgPerCubicMeter,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		double hoverRpm = hoverRpm(config, rotor);
		double hoverOmega = hoverRpm / RPM_PER_RADIAN_PER_SECOND;
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample reverseAxialClamp =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredFromSignedAxialAdvanceSpeed(
						presetName,
						"static_anchored_runtime_reverse_axial_clamp",
						rotor,
						REVERSE_AXIAL_DIAGNOSTIC_SPEED_METERS_PER_SECOND,
						hoverOmega,
						airDensityKgPerCubicMeter,
						config.centerOfMassOffsetBodyMeters(),
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.CLAMP_TO_ENVELOPE,
						ambientTemperatureCelsius,
						ambientHumidity
				);
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample highAdvanceBlocked =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchored(
						new PropellerArchiveCtCpJRotorForceModel.RotorForceQuery(
								presetName,
								"static_anchored_runtime_high_j_block",
								rotor,
								OUT_OF_ENVELOPE_DIAGNOSTIC_ADVANCE_RATIO_J,
								hoverRpm,
								airDensityKgPerCubicMeter,
								PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE,
								ambientTemperatureCelsius,
								ambientHumidity
						),
						config.centerOfMassOffsetBodyMeters());
		double blockedAxialSpeed = OUT_OF_ENVELOPE_DIAGNOSTIC_ADVANCE_RATIO_J
				* hoverRpm
				/ 60.0
				* rotor.radiusMeters()
				* 2.0;
		double transverseDiagnosticAxialSpeed = TRANSVERSE_INFLOW_DIAGNOSTIC_ADVANCE_RATIO_J
				* hoverRpm
				/ 60.0
				* rotor.radiusMeters()
				* 2.0;
		Vec3 transverseDiagnosticAirVelocity = rotorAxisBody(rotor)
				.multiply(transverseDiagnosticAxialSpeed)
				.add(diagnosticTransverseAirVelocity(
						rotor,
						TRANSVERSE_INFLOW_DIAGNOSTIC_SPEED_METERS_PER_SECOND
				));
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample transverseInflowDiagnostic =
				PropellerArchiveCtCpJRotorForceModel.sampleStaticAnchoredFromRelativeAirVelocity(
						presetName,
						"static_anchored_runtime_transverse_inflow_diagnostic",
						rotor,
						transverseDiagnosticAirVelocity,
						hoverOmega,
						airDensityKgPerCubicMeter,
						config.centerOfMassOffsetBodyMeters(),
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE,
						ambientTemperatureCelsius,
						ambientHumidity
				);
		return List.of(
				new EnvelopeDiagnosticPoint(
						REVERSE_AXIAL_DIAGNOSTIC_SPEED_METERS_PER_SECOND,
						reverseAxialClamp
				),
				new EnvelopeDiagnosticPoint(
						blockedAxialSpeed,
						highAdvanceBlocked
				),
				new EnvelopeDiagnosticPoint(
						transverseDiagnosticAxialSpeed,
						transverseInflowDiagnostic
				)
		);
	}

	private static List<StaticCurvePoint> staticAnchoredRuntimeCurvePoints(
			DroneConfig config,
			RotorSpec rotor
	) {
		List<StaticCurvePoint> points = new ArrayList<>();
		addStaticPoint(points, "static_anchored_runtime_hover", hoverRpm(config, rotor));
		addStaticPoint(points, "static_anchored_runtime_half_max", rotor.maxOmegaRadiansPerSecond()
				* RPM_PER_RADIAN_PER_SECOND * 0.5);
		addStaticPoint(points, "static_anchored_runtime_max", rotor.maxOmegaRadiansPerSecond()
				* RPM_PER_RADIAN_PER_SECOND);
		return points.stream()
				.sorted((first, second) -> Double.compare(first.rpm(), second.rpm()))
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

	private static void addDistinct(List<Double> values, double candidate) {
		for (double value : values) {
			if (Math.abs(value - candidate) <= 1.0e-9) {
				return;
			}
		}
		values.add(candidate);
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

	private record StaticCurvePoint(String caseName, double rpm) {
	}

	private record EnvelopeDiagnosticPoint(
			double querySignedAxialSpeedMetersPerSecond,
			PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample
	) {
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
}
