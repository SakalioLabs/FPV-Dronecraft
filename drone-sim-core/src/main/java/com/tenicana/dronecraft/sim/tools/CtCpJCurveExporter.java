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
			"thrust_force_body_x_n",
			"thrust_force_body_y_n",
			"thrust_force_body_z_n",
			"reaction_torque_body_x_nm",
			"reaction_torque_body_y_nm",
			"reaction_torque_body_z_nm"
	);
	private static final double RPM_PER_RADIAN_PER_SECOND = 60.0 / (2.0 * Math.PI);
	private static final double REVERSE_AXIAL_DIAGNOSTIC_SPEED_METERS_PER_SECOND = -4.5;
	private static final double OUT_OF_ENVELOPE_DIAGNOSTIC_ADVANCE_RATIO_J = 1.20;

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
		write(presetName, output, airDensity, diameter);
	}

	public static void write(
			String presetName,
			Path output,
			double airDensityKgPerCubicMeter,
			double propellerDiameterMeters
	) throws IOException {
		if (output == null) {
			throw new IllegalArgumentException("output path must not be null.");
		}
		List<String> lines = csvLines(presetName, airDensityKgPerCubicMeter, propellerDiameterMeters);
		Path parent = output.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.write(output, lines, StandardCharsets.UTF_8);
	}

	public static List<String> csvLines(
			String presetName,
			double airDensityKgPerCubicMeter,
			double propellerDiameterMeters
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
		for (PropellerArchiveCtCpJLookupEvaluator.LookupQuery query : queries) {
			PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample sample =
					PropellerArchiveCtCpJLookupEvaluator.sampleRotor(query);
			lines.add(csvLine(sample, rotor));
		}
		for (StaticCurvePoint point : staticCurvePoints(presetName, config, rotor)) {
			lines.add(csvLine(RotorStaticCtCpModel.sample(
					presetName,
					point.caseName(),
					rotor,
					point.rpm(),
					airDensityKgPerCubicMeter
			), rotor));
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
										PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
								));
				lines.add(csvLine(sample, sample.axialAdvanceSpeedMetersPerSecond()));
			}
		}
		for (EnvelopeDiagnosticPoint point : envelopeDiagnosticCurvePoints(
				presetName,
				config,
				rotor,
				airDensityKgPerCubicMeter
		)) {
			lines.add(csvLine(point.sample(), point.querySignedAxialSpeedMetersPerSecond()));
		}
		return List.copyOf(lines);
	}

	private static String csvLine(
			PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample sample,
			RotorSpec rotor
	) {
		PropellerArchiveCtCpJLookupEvaluator.LookupResult lookup = sample.lookup();
		double queryAxialSpeed = lookup.queryAdvanceRatioJ()
				* Math.max(0.0, lookup.queryRpm())
				/ 60.0
				* sample.propellerDiameterMeters();
		Vec3 axis = rotorAxisBody(rotor);
		return csvLine(
				sample,
				false,
				queryAxialSpeed,
				axis.multiply(sample.thrustNewtons()),
				axis.multiply(rotor.spinDirection() * sample.shaftTorqueNewtonMeters())
		);
	}

	private static String csvLine(
			PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample,
			double querySignedAxialSpeedMetersPerSecond
	) {
		return csvLine(
				sample.dimensionalSample(),
				sample.runtimeForceReplacementAccepted(),
				querySignedAxialSpeedMetersPerSecond,
				sample.thrustForceBodyNewtons(),
				sample.reactionTorqueBodyNewtonMeters()
		);
	}

	private static String csvLine(
			PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample sample,
			boolean runtimeForceReplacementAccepted,
			double querySignedAxialSpeedMetersPerSecond,
			Vec3 thrustForceBodyNewtons,
			Vec3 reactionTorqueBodyNewtonMeters
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
				number(thrustForceBodyNewtons.x()),
				number(thrustForceBodyNewtons.y()),
				number(thrustForceBodyNewtons.z()),
				number(reactionTorqueBodyNewtonMeters.x()),
				number(reactionTorqueBodyNewtonMeters.y()),
				number(reactionTorqueBodyNewtonMeters.z())
		);
	}

	private static String csvLine(RotorStaticCtCpModel.StaticRotorSample sample, RotorSpec rotor) {
		Vec3 axis = rotorAxisBody(rotor);
		Vec3 thrustForce = axis.multiply(sample.thrustNewtons());
		Vec3 reactionTorque = axis.multiply(rotor.spinDirection() * sample.shaftTorqueNewtonMeters());
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
				number(thrustForce.x()),
				number(thrustForce.y()),
				number(thrustForce.z()),
				number(reactionTorque.x()),
				number(reactionTorque.y()),
				number(reactionTorque.z())
		);
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
			double airDensityKgPerCubicMeter
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
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.CLAMP_TO_ENVELOPE
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
								PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
						));
		double blockedAxialSpeed = OUT_OF_ENVELOPE_DIAGNOSTIC_ADVANCE_RATIO_J
				* hoverRpm
				/ 60.0
				* rotor.radiusMeters()
				* 2.0;
		return List.of(
				new EnvelopeDiagnosticPoint(
						REVERSE_AXIAL_DIAGNOSTIC_SPEED_METERS_PER_SECOND,
						reverseAxialClamp
				),
				new EnvelopeDiagnosticPoint(
						blockedAxialSpeed,
						highAdvanceBlocked
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
