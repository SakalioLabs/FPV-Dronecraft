package com.tenicana.dronecraft.sim.tools;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJDimensionalRotorResponse;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJLookupEvaluator;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJRotorForceModel;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJWorldForceApplicationProvider;
import com.tenicana.dronecraft.sim.Quaternion;
import com.tenicana.dronecraft.sim.RotorSpec;
import com.tenicana.dronecraft.sim.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CtCpJActuatorDiskSourceTermExporter {
	public static final double DEFAULT_SOURCE_THICKNESS_METERS = 0.05;
	private static final String HEADER = String.join(",",
			"preset",
			"case",
			"row_kind",
			"rotor_index",
			"query_j",
			"query_rpm",
			"effective_j",
			"effective_rpm",
			"ct",
			"cp",
			"eta",
			"interpolation_status",
			"lookup_status",
			"clamped",
			"blocked",
			"applied",
			"runtime_force_replacement_accepted",
			"source_thickness_m",
			"query_signed_axial_speed_mps",
			"relative_air_body_x_mps",
			"relative_air_body_y_mps",
			"relative_air_body_z_mps",
			"body_angular_rate_x_rad_s",
			"body_angular_rate_y_rad_s",
			"body_angular_rate_z_rad_s",
			"body_to_world_qw",
			"body_to_world_qx",
			"body_to_world_qy",
			"body_to_world_qz",
			"disk_center_world_x_m",
			"disk_center_world_y_m",
			"disk_center_world_z_m",
			"disk_normal_world_x",
			"disk_normal_world_y",
			"disk_normal_world_z",
			"disk_area_m2",
			"disk_radius_m",
			"source_volume_m3",
			"pressure_jump_pa",
			"mass_flux_kg_s_m2",
			"ideal_momentum_power_loading_w_m2",
			"thrust_surface_force_world_x_n_m2",
			"thrust_surface_force_world_y_n_m2",
			"thrust_surface_force_world_z_n_m2",
			"integrated_thrust_force_world_x_n",
			"integrated_thrust_force_world_y_n",
			"integrated_thrust_force_world_z_n",
			"far_wake_axial_velocity_world_x_mps",
			"far_wake_axial_velocity_world_y_mps",
			"far_wake_axial_velocity_world_z_mps",
			"body_force_density_world_x_n_m3",
			"body_force_density_world_y_n_m3",
			"body_force_density_world_z_n_m3",
			"equivalent_body_force_integral_world_x_n",
			"equivalent_body_force_integral_world_y_n",
			"equivalent_body_force_integral_world_z_n",
			"shaft_power_w",
			"shaft_torque_nm",
			"disk_loading_n_m2",
			"ideal_induced_velocity_mps",
			"ideal_momentum_power_w",
			"ideal_momentum_power_over_shaft_power"
	);
	private static final double RPM_PER_RADIAN_PER_SECOND = 60.0 / (2.0 * Math.PI);
	private static final double MID_ADVANCE_RATIO_J = 0.4064;
	private static final double HIGH_ADVANCE_RATIO_J = 0.73152;
	private static final double OUT_OF_ENVELOPE_DIAGNOSTIC_ADVANCE_RATIO_J = 1.20;
	private static final double REVERSE_AXIAL_DIAGNOSTIC_SPEED_METERS_PER_SECOND = -4.5;

	private CtCpJActuatorDiskSourceTermExporter() {
	}

	public static void main(String[] args) throws IOException {
		String presetName = args.length >= 1 && !args[0].isBlank()
				? args[0]
				: PropellerArchiveCtCpJLookupEvaluator.DEFAULT_PRESET_NAME;
		Path output = args.length >= 2 && !args[1].isBlank()
				? Path.of(args[1])
				: Path.of("build", "ct-cp-j-actuator-disk-source-terms", presetName + ".csv");
		double airDensity = args.length >= 3 && !args[2].isBlank()
				? Double.parseDouble(args[2])
				: PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
		double sourceThickness = args.length >= 4 && !args[3].isBlank()
				? Double.parseDouble(args[3])
				: DEFAULT_SOURCE_THICKNESS_METERS;
		double ambientTemperatureCelsius = args.length >= 5 && !args[4].isBlank()
				? Double.parseDouble(args[4])
				: 25.0;
		double ambientHumidity = args.length >= 6 && !args[5].isBlank()
				? Double.parseDouble(args[5])
				: 0.0;
		write(presetName, output, airDensity, sourceThickness, ambientTemperatureCelsius, ambientHumidity);
	}

	public static void write(
			String presetName,
			Path output,
			double airDensityKgPerCubicMeter
	) throws IOException {
		write(
				presetName,
				output,
				airDensityKgPerCubicMeter,
				DEFAULT_SOURCE_THICKNESS_METERS,
				25.0,
				0.0
		);
	}

	public static void write(
			String presetName,
			Path output,
			double airDensityKgPerCubicMeter,
			double sourceThicknessMeters,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) throws IOException {
		if (output == null) {
			throw new IllegalArgumentException("output path must not be null.");
		}
		List<String> lines = csvLines(
				presetName,
				airDensityKgPerCubicMeter,
				sourceThicknessMeters,
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
		return csvLines(
				presetName,
				airDensityKgPerCubicMeter,
				DEFAULT_SOURCE_THICKNESS_METERS,
				25.0,
				0.0
		);
	}

	public static List<String> csvLines(
			String presetName,
			double airDensityKgPerCubicMeter,
			double sourceThicknessMeters,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		if (!Double.isFinite(sourceThicknessMeters) || sourceThicknessMeters <= 0.0) {
			throw new IllegalArgumentException("sourceThicknessMeters must be finite and positive.");
		}
		DroneConfig config = configForPreset(presetName);
		if (config.rotors().isEmpty()) {
			throw new IllegalArgumentException("DroneConfig preset has no rotors: " + presetName);
		}
		List<String> lines = new ArrayList<>();
		lines.add(HEADER);
		for (SourceTermCase sourceCase : sourceTermCases(config)) {
			PropellerArchiveCtCpJWorldForceApplicationProvider.WorldForceApplicationSample sample =
					sampleSourceTermCase(
							presetName,
							config,
							sourceCase,
							airDensityKgPerCubicMeter,
							ambientTemperatureCelsius,
							ambientHumidity
					);
			for (int i = 0; i < sample.rotorActuatorDiskSourceTerms().size(); i++) {
				lines.add(csvLine(sample, sourceCase, "raw_source", i,
						sample.rotorActuatorDiskSourceTerms().get(i), sourceThicknessMeters));
				lines.add(csvLine(sample, sourceCase, "runtime_replacement_source", i,
						sample.runtimeReplacementRotorActuatorDiskSourceTerms().get(i), sourceThicknessMeters));
			}
		}
		return List.copyOf(lines);
	}

	private static PropellerArchiveCtCpJWorldForceApplicationProvider.WorldForceApplicationSample sampleSourceTermCase(
			String presetName,
			DroneConfig config,
			SourceTermCase sourceCase,
			double airDensityKgPerCubicMeter,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		RotorSpec rotor = config.rotors().get(0);
		Vec3 relativeAirBody = rotorAxisBody(rotor).multiply(sourceCase.signedAxialSpeedMetersPerSecond());
		Vec3 vehicleVelocityWorld = sourceCase.bodyToWorldOrientation().rotate(relativeAirBody);
		return PropellerArchiveCtCpJWorldForceApplicationProvider
				.sampleStaticAnchoredConfigurationFromWorldKinematics(
						presetName,
						sourceCase.caseName(),
						config,
						Vec3.ZERO,
						sourceCase.bodyToWorldOrientation(),
						vehicleVelocityWorld,
						sourceCase.angularVelocityBodyRadiansPerSecond(),
						Vec3.ZERO,
						null,
						fill(config.rotors().size(), sourceCase.omegaRadiansPerSecond()),
						airDensityKgPerCubicMeter,
						sourceCase.envelopePolicy(),
						ambientTemperatureCelsius,
						ambientHumidity
				);
	}

	private static String csvLine(
			PropellerArchiveCtCpJWorldForceApplicationProvider.WorldForceApplicationSample worldSample,
			SourceTermCase sourceCase,
			String rowKind,
			int rotorIndex,
			PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm,
			double sourceThicknessMeters
	) {
		PropellerArchiveCtCpJRotorForceModel.RotorForceSample rotorSample =
				worldSample.aggregate().rotorSamples().get(rotorIndex);
		PropellerArchiveCtCpJLookupEvaluator.LookupResult lookup = rotorSample.lookup();
		PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample dimensional =
				rotorSample.dimensionalSample();
		Vec3 integratedThrustForce =
				sourceTerm.thrustSurfaceForceWorldNewtonsPerSquareMeter()
						.multiply(sourceTerm.diskAreaSquareMeters());
		Vec3 bodyForceDensity =
				sourceTerm.equivalentBodyForceWorldNewtonsPerCubicMeter(sourceThicknessMeters);
		double diskRadius = Math.sqrt(sourceTerm.diskAreaSquareMeters() / Math.PI);
		double sourceVolume = sourceTerm.diskAreaSquareMeters() * sourceThicknessMeters;
		Vec3 equivalentBodyForceIntegral = bodyForceDensity.multiply(sourceVolume);
		Vec3 relativeAir = rotorSample.relativeAirVelocityBodyMetersPerSecond();
		Vec3 angularRate = sourceCase.angularVelocityBodyRadiansPerSecond();
		Quaternion orientation = sourceCase.bodyToWorldOrientation();
		return String.join(",",
				escape(lookup.presetName()),
				escape(lookup.caseName()),
				escape(rowKind),
				Integer.toString(sourceTerm.rotorIndex()),
				number(lookup.queryAdvanceRatioJ()),
				number(lookup.queryRpm()),
				number(lookup.effectiveAdvanceRatioJ()),
				number(lookup.effectiveRpm()),
				number(lookup.thrustCoefficientCt()),
				number(lookup.powerCoefficientCp()),
				number(lookup.propulsiveEfficiencyEta()),
				escape(lookup.interpolationStatus().name()),
				escape(lookup.status()),
				Boolean.toString(lookup.clamped()),
				Boolean.toString(lookup.blocked()),
				Boolean.toString(sourceTerm.applied()),
				Boolean.toString(sourceTerm.runtimeForceReplacementAccepted()),
				number(sourceThicknessMeters),
				number(sourceCase.signedAxialSpeedMetersPerSecond()),
				number(relativeAir.x()),
				number(relativeAir.y()),
				number(relativeAir.z()),
				number(angularRate.x()),
				number(angularRate.y()),
				number(angularRate.z()),
				number(orientation.w()),
				number(orientation.x()),
				number(orientation.y()),
				number(orientation.z()),
				number(sourceTerm.diskCenterWorldMeters().x()),
				number(sourceTerm.diskCenterWorldMeters().y()),
				number(sourceTerm.diskCenterWorldMeters().z()),
				number(sourceTerm.diskNormalWorld().x()),
				number(sourceTerm.diskNormalWorld().y()),
				number(sourceTerm.diskNormalWorld().z()),
				number(sourceTerm.diskAreaSquareMeters()),
				number(diskRadius),
				number(sourceVolume),
				number(sourceTerm.pressureJumpPascals()),
				number(sourceTerm.massFluxKilogramsPerSecondSquareMeter()),
				number(sourceTerm.idealMomentumPowerLoadingWattsPerSquareMeter()),
				number(sourceTerm.thrustSurfaceForceWorldNewtonsPerSquareMeter().x()),
				number(sourceTerm.thrustSurfaceForceWorldNewtonsPerSquareMeter().y()),
				number(sourceTerm.thrustSurfaceForceWorldNewtonsPerSquareMeter().z()),
				number(integratedThrustForce.x()),
				number(integratedThrustForce.y()),
				number(integratedThrustForce.z()),
				number(sourceTerm.farWakeAxialVelocityWorldMetersPerSecond().x()),
				number(sourceTerm.farWakeAxialVelocityWorldMetersPerSecond().y()),
				number(sourceTerm.farWakeAxialVelocityWorldMetersPerSecond().z()),
				number(bodyForceDensity.x()),
				number(bodyForceDensity.y()),
				number(bodyForceDensity.z()),
				number(equivalentBodyForceIntegral.x()),
				number(equivalentBodyForceIntegral.y()),
				number(equivalentBodyForceIntegral.z()),
				number(dimensional.shaftPowerWatts()),
				number(dimensional.shaftTorqueNewtonMeters()),
				number(dimensional.diskLoadingNewtonsPerSquareMeter()),
				number(dimensional.idealInducedVelocityMetersPerSecond()),
				number(dimensional.idealMomentumPowerWatts()),
				number(dimensional.idealMomentumPowerOverShaftPower())
		);
	}

	private static List<SourceTermCase> sourceTermCases(DroneConfig config) {
		RotorSpec rotor = config.rotors().get(0);
		double hoverRpm = hoverRpm(config, rotor);
		double diameter = rotor.radiusMeters() * 2.0;
		double midAxialSpeed = axialSpeedForJ(MID_ADVANCE_RATIO_J, hoverRpm, diameter);
		double highAxialSpeed = axialSpeedForJ(HIGH_ADVANCE_RATIO_J, hoverRpm, diameter);
		double blockedAxialSpeed = axialSpeedForJ(
				OUT_OF_ENVELOPE_DIAGNOSTIC_ADVANCE_RATIO_J,
				hoverRpm,
				diameter
		);
		double hoverOmega = hoverRpm / RPM_PER_RADIAN_PER_SECOND;
		Quaternion yawedWorldProjection =
				new Quaternion(Math.cos(Math.PI / 4.0), 0.0, 0.0, Math.sin(Math.PI / 4.0));
		return List.of(
				new SourceTermCase(
						"static_anchored_source_hover",
						0.0,
						hoverOmega,
						Quaternion.IDENTITY,
						Vec3.ZERO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				),
				new SourceTermCase(
						"static_anchored_source_mid_j",
						midAxialSpeed,
						hoverOmega,
						Quaternion.IDENTITY,
						Vec3.ZERO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				),
				new SourceTermCase(
						"static_anchored_source_high_j",
						highAxialSpeed,
						hoverOmega,
						yawedWorldProjection,
						Vec3.ZERO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				),
				new SourceTermCase(
						"static_anchored_source_reverse_axial_clamp",
						REVERSE_AXIAL_DIAGNOSTIC_SPEED_METERS_PER_SECOND,
						hoverOmega,
						Quaternion.IDENTITY,
						Vec3.ZERO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.CLAMP_TO_ENVELOPE
				),
				new SourceTermCase(
						"static_anchored_source_high_j_block",
						blockedAxialSpeed,
						hoverOmega,
						Quaternion.IDENTITY,
						Vec3.ZERO,
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				)
		);
	}

	private static double axialSpeedForJ(double advanceRatioJ, double rpm, double propellerDiameterMeters) {
		return advanceRatioJ * rpm / 60.0 * propellerDiameterMeters;
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

	private static double[] fill(int count, double value) {
		double[] values = new double[count];
		for (int i = 0; i < values.length; i++) {
			values[i] = value;
		}
		return values;
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

	private record SourceTermCase(
			String caseName,
			double signedAxialSpeedMetersPerSecond,
			double omegaRadiansPerSecond,
			Quaternion bodyToWorldOrientation,
			Vec3 angularVelocityBodyRadiansPerSecond,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy
	) {
		private SourceTermCase {
			bodyToWorldOrientation = bodyToWorldOrientation == null
					? Quaternion.IDENTITY
					: bodyToWorldOrientation.normalized();
			angularVelocityBodyRadiansPerSecond = angularVelocityBodyRadiansPerSecond == null
					? Vec3.ZERO
					: angularVelocityBodyRadiansPerSecond;
		}
	}
}
