package com.tenicana.dronecraft.sim;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.tenicana.dronecraft.sim.UiucDa4002AxialSurfaceV1RuntimeShadowAdapter.RotorShadowSample;
import com.tenicana.dronecraft.sim.UiucDa4002AxialSurfaceV1RuntimeShadowAdapter.StateShadowSample;
import com.tenicana.dronecraft.sim.UiucDa4002MeasuredRotorModel.Propeller;

/** Deterministic current-runtime versus DA4002 v1 shadow scenarios. */
public final class UiucDa4002AxialSurfaceV1ShadowScenarios {
	public static final double RUNTIME_TELEMETRY_STEP_SECONDS = 1.0e-5;
	public static final double OBLIQUE_TRANSVERSE_SPEED_METERS_PER_SECOND = 2.0;
	private static final Vec3 DEFAULT_AXIS = new Vec3(0.0, 1.0, 0.0);
	private static final Vec3 TILTED_AXIS = new Vec3(0.25, 0.93, 0.27).normalized();

	private static final List<ScenarioDefinition> DEFINITIONS = List.of(
			definition("five_hover", ScenarioKind.HOVER,
					Propeller.DA4002_5X3_75, 5_000.0, 0.0, DEFAULT_AXIS, Vec3.ZERO),
			definition("five_mid_j", ScenarioKind.MID_ADVANCE_RATIO,
					Propeller.DA4002_5X3_75, 5_000.0, 0.40, DEFAULT_AXIS, Vec3.ZERO),
			definition("five_high_j", ScenarioKind.HIGH_ADVANCE_RATIO,
					Propeller.DA4002_5X3_75, 5_000.0, 0.80, DEFAULT_AXIS, Vec3.ZERO),
			definition("five_zero_below", ScenarioKind.ZERO_THRUST_BELOW,
					Propeller.DA4002_5X3_75, 4_031.0, 0.84, DEFAULT_AXIS, Vec3.ZERO),
			definition("five_zero_above", ScenarioKind.ZERO_THRUST_ABOVE,
					Propeller.DA4002_5X3_75, 4_031.0, 0.85, DEFAULT_AXIS, Vec3.ZERO),
			definition("five_outside", ScenarioKind.OUT_OF_ENVELOPE,
					Propeller.DA4002_5X3_75, 4_031.0, 0.90, DEFAULT_AXIS, Vec3.ZERO),
			definition("five_tilted_axis", ScenarioKind.TILTED_AXIS,
					Propeller.DA4002_5X3_75, 5_000.0, 0.40, TILTED_AXIS, Vec3.ZERO),
			definition("five_oblique", ScenarioKind.OBLIQUE_FLOW,
					Propeller.DA4002_5X3_75, 5_000.0, 0.40, DEFAULT_AXIS,
					new Vec3(OBLIQUE_TRANSVERSE_SPEED_METERS_PER_SECOND, 0.0, 0.0)),
			definition("nine_hover", ScenarioKind.HOVER,
					Propeller.DA4002_9X6_75, 3_000.0, 0.0, DEFAULT_AXIS, Vec3.ZERO),
			definition("nine_mid_j", ScenarioKind.MID_ADVANCE_RATIO,
					Propeller.DA4002_9X6_75, 3_000.0, 0.40, DEFAULT_AXIS, Vec3.ZERO),
			definition("nine_high_j", ScenarioKind.HIGH_ADVANCE_RATIO,
					Propeller.DA4002_9X6_75, 3_000.0, 0.80, DEFAULT_AXIS, Vec3.ZERO),
			definition("nine_zero_below", ScenarioKind.ZERO_THRUST_BELOW,
					Propeller.DA4002_9X6_75, 2_999.0, 0.86, DEFAULT_AXIS, Vec3.ZERO),
			definition("nine_zero_above", ScenarioKind.ZERO_THRUST_ABOVE,
					Propeller.DA4002_9X6_75, 2_999.0, 0.87, DEFAULT_AXIS, Vec3.ZERO),
			definition("nine_outside", ScenarioKind.OUT_OF_ENVELOPE,
					Propeller.DA4002_9X6_75, 2_999.0, 0.90, DEFAULT_AXIS, Vec3.ZERO),
			definition("nine_tilted_axis", ScenarioKind.TILTED_AXIS,
					Propeller.DA4002_9X6_75, 3_000.0, 0.40, TILTED_AXIS, Vec3.ZERO),
			definition("nine_oblique", ScenarioKind.OBLIQUE_FLOW,
					Propeller.DA4002_9X6_75, 3_000.0, 0.40, DEFAULT_AXIS,
					new Vec3(OBLIQUE_TRANSVERSE_SPEED_METERS_PER_SECOND, 0.0, 0.0))
	);

	private UiucDa4002AxialSurfaceV1ShadowScenarios() {
	}

	public enum ScenarioKind {
		HOVER,
		MID_ADVANCE_RATIO,
		HIGH_ADVANCE_RATIO,
		ZERO_THRUST_BELOW,
		ZERO_THRUST_ABOVE,
		OUT_OF_ENVELOPE,
		TILTED_AXIS,
		OBLIQUE_FLOW
	}

	public static List<ScenarioDefinition> definitions() {
		return DEFINITIONS;
	}

	public static List<ScenarioResult> runAll() {
		return DEFINITIONS.stream()
				.map(UiucDa4002AxialSurfaceV1ShadowScenarios::run)
				.toList();
	}

	public static ScenarioResult run(ScenarioDefinition definition) {
		if (definition == null) {
			throw new IllegalArgumentException("definition must not be null.");
		}
		DroneConfig config = exactConfig(definition.propeller(), definition.rotorAxisBody());
		DronePhysics physics = new DronePhysics(config);
		DroneState state = physics.state();
		double revolutionsPerSecond = definition.initialRpm() / 60.0;
		double axialSpeed = definition.targetAdvanceRatioJ()
				* revolutionsPerSecond * definition.propeller().diameterMeters();
		Vec3 targetVelocity =
				definition.rotorAxisBody().multiply(axialSpeed)
						.add(definition.transverseVelocityBodyMetersPerSecond());
		setFixedKinematics(state, targetVelocity);
		double omega = definition.initialRpm() * 2.0 * Math.PI / 60.0;
		double throttle = MathUtil.clamp(
				omega / config.rotors().get(0).maxOmegaRadiansPerSecond(),
				0.0,
				1.0
		);
		restoreRotorState(physics, config.rotors().size(), omega, definition.initialRpm(),
				throttle);
		physics.step(
				new DroneInput(throttle, 0.0, 0.0, 0.0, true, true, FlightMode.ACRO),
				RUNTIME_TELEMETRY_STEP_SECONDS,
				DroneEnvironment.calm()
		);
		setFixedKinematics(state, targetVelocity);
		StateShadowSample shadow = UiucDa4002AxialSurfaceV1RuntimeShadowAdapter.sample(
				definition.propeller(),
				config,
				state,
				DroneEnvironment.calm()
		);
		return new ScenarioResult(
				definition,
				state.averageMotorRpm(),
				meanAdvanceRatio(shadow.rotorSamples()),
				shadow
		);
	}

	private static void setFixedKinematics(DroneState state, Vec3 velocityMetersPerSecond) {
		state.setPositionMeters(new Vec3(0.0, 100.0, 0.0));
		state.setOrientation(Quaternion.IDENTITY);
		state.setEstimatedOrientation(Quaternion.IDENTITY);
		state.setVelocityMetersPerSecond(velocityMetersPerSecond);
		state.setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
	}

	private static DroneConfig exactConfig(Propeller propeller, Vec3 rotorAxisBody) {
		DroneConfig base = switch (propeller) {
			case DA4002_5X3_75 -> DroneConfig.racingQuad()
					.withRotorRadiusMeters(propeller.diameterMeters() * 0.5)
					.withRotorBladePitchToDiameterRatio(3.75 / 5.0)
					.withRotorBladeCount(2);
			case DA4002_9X6_75 -> DroneConfig.heavyLift()
					.withRotorRadiusMeters(propeller.diameterMeters() * 0.5)
					.withRotorBladePitchToDiameterRatio(6.75 / 9.0)
					.withRotorBladeCount(2);
		};
		DroneConfig fixedControlConfig = base.withControlLink(
				0.0,
				0.0,
				base.rcFailsafeTimeoutSeconds()
		);
		return fixedControlConfig.withRotors(fixedControlConfig.rotors().stream()
				.map(rotor -> rotor.withThrustAxisBody(rotorAxisBody))
				.toList());
	}

	private static void restoreRotorState(
			DronePhysics physics,
			int rotorCount,
			double omega,
			double rpm,
			double throttle
	) {
		double[] motorOmega = filled(rotorCount, omega);
		double[] escOutput = filled(rotorCount, throttle);
		double[] telemetryRpm = filled(rotorCount, rpm);
		double[] ones = filled(rotorCount, 1.0);
		double[] zeros = new double[rotorCount];
		physics.restoreRotorDynamicState(new DronePhysics.RotorDynamicState(
				motorOmega,
				escOutput,
				escOutput,
				telemetryRpm,
				ones,
				zeros,
				ones,
				zeros,
				zeros,
				zeros,
				zeros,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0
		));
	}

	private static double[] filled(int length, double value) {
		double[] values = new double[length];
		Arrays.fill(values, value);
		return values;
	}

	private static double meanAdvanceRatio(List<RotorShadowSample> samples) {
		return samples.stream()
				.map(RotorShadowSample::reference)
				.filter(Optional::isPresent)
				.map(Optional::orElseThrow)
				.mapToDouble(UiucDa4002MeasuredRotorForceModel.ForceSample::signedAdvanceRatioJ)
				.average()
				.orElse(0.0);
	}

	private static ScenarioDefinition definition(
			String id,
			ScenarioKind kind,
			Propeller propeller,
			double initialRpm,
			double targetAdvanceRatioJ,
			Vec3 rotorAxisBody,
			Vec3 transverseVelocityBodyMetersPerSecond
	) {
		return new ScenarioDefinition(
				id,
				kind,
				propeller,
				initialRpm,
				targetAdvanceRatioJ,
				rotorAxisBody,
				transverseVelocityBodyMetersPerSecond
		);
	}

	public record ScenarioDefinition(
			String id,
			ScenarioKind kind,
			Propeller propeller,
			double initialRpm,
			double targetAdvanceRatioJ,
			Vec3 rotorAxisBody,
			Vec3 transverseVelocityBodyMetersPerSecond
	) {
		public ScenarioDefinition {
			id = id == null ? "" : id.trim();
			if (id.isEmpty() || kind == null || propeller == null) {
				throw new IllegalArgumentException(
						"id, kind, and propeller must be present."
				);
			}
			if (!Double.isFinite(initialRpm) || initialRpm <= 0.0
					|| !Double.isFinite(targetAdvanceRatioJ)
					|| targetAdvanceRatioJ < 0.0) {
				throw new IllegalArgumentException("scenario RPM and J must be valid.");
			}
			if (rotorAxisBody == null || !rotorAxisBody.isFinite()
					|| rotorAxisBody.lengthSquared() <= 1.0e-12) {
				throw new IllegalArgumentException("rotorAxisBody must be finite and nonzero.");
			}
			rotorAxisBody = rotorAxisBody.normalized();
			transverseVelocityBodyMetersPerSecond =
					transverseVelocityBodyMetersPerSecond == null
							? Vec3.ZERO : transverseVelocityBodyMetersPerSecond;
			if (!transverseVelocityBodyMetersPerSecond.isFinite()
					|| Math.abs(transverseVelocityBodyMetersPerSecond.dot(rotorAxisBody))
					> 1.0e-9) {
				throw new IllegalArgumentException(
						"transverse velocity must be finite and perpendicular to the rotor axis."
				);
			}
		}
	}

	public record ScenarioResult(
			ScenarioDefinition definition,
			double actualMeanRpm,
			double actualMeanAdvanceRatioJ,
			StateShadowSample shadow
	) {
		public ScenarioResult {
			if (definition == null || shadow == null) {
				throw new IllegalArgumentException("definition and shadow must not be null.");
			}
		}

		public double thrustResidualFraction() {
			return ratio(shadow.thrustResidualNewtons(),
					Math.abs(shadow.referenceTotalThrustNewtons()));
		}

		public double shaftPowerResidualFraction() {
			return ratio(shadow.shaftPowerResidualWatts(),
					Math.abs(shadow.referenceTotalShaftPowerWatts()));
		}

		public double shaftTorqueResidualFraction() {
			return ratio(shadow.shaftTorqueResidualNewtonMeters(),
					Math.abs(shadow.referenceTotalShaftTorqueNewtonMeters()));
		}

		private static double ratio(double numerator, double denominator) {
			return denominator > 1.0e-9 ? numerator / denominator : 0.0;
		}
	}
}
