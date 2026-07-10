package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.UiucDa4002AxialSurfaceV1RuntimeShadowAdapter.ShadowStatus;
import com.tenicana.dronecraft.sim.UiucDa4002AxialSurfaceV1RuntimeShadowAdapter.StateShadowSample;
import com.tenicana.dronecraft.sim.UiucDa4002MeasuredRotorModel.Propeller;

class UiucDa4002AxialSurfaceV1RuntimeShadowAdapterTest {
	private static final Propeller PROPELLER = Propeller.DA4002_5X3_75;
	private static final double RPM = 5_000.0;

	@Test
	void hoverShadowClosesTelemetryResidualsWithoutMutatingState() {
		DroneConfig config = exactFiveInchConfig();
		DroneState state = stateAtUniformRpm(config, RPM);
		StateShadowSample reference = UiucDa4002AxialSurfaceV1RuntimeShadowAdapter
				.sample(PROPELLER, config, state, DroneEnvironment.calm());
		for (var rotor : reference.rotorSamples()) {
			int index = rotor.rotorIndex();
			state.setRotorThrustNewtons(index, rotor.referenceThrustNewtons());
			state.setMotorShaftPowerWatts(index, rotor.referenceShaftPowerWatts());
			state.setMotorAerodynamicTorqueNewtonMeters(
					index, rotor.referenceShaftTorqueNewtonMeters());
			state.setRotorForceBodyNewtons(index, rotor.referenceForceBodyNewtons());
			state.setRotorTorqueBodyNewtonMeters(
					index, rotor.referenceTorqueBodyNewtonMeters());
		}
		StateSnapshot before = snapshot(state);
		StateShadowSample sample = UiucDa4002AxialSurfaceV1RuntimeShadowAdapter
				.sample(PROPELLER, config, state, DroneEnvironment.calm());

		assertEquals(UiucDa4002AxialSurfaceV1.VERSION_ID, sample.modelVersionId());
		assertFalse(sample.runtimeForceApplied());
		assertEquals(4, sample.comparableScalarRotorCount());
		assertEquals(4, sample.comparableVectorRotorCount());
		assertEquals(0, sample.blockedRotorCount());
		assertEquals(0.0, sample.thrustResidualNewtons(), 1.0e-14);
		assertEquals(0.0, sample.shaftPowerResidualWatts(), 1.0e-14);
		assertEquals(0.0, sample.shaftTorqueResidualNewtonMeters(), 1.0e-14);
		assertVectorEquals(Vec3.ZERO, sample.forceResidualBodyNewtons(), 1.0e-14);
		assertVectorEquals(Vec3.ZERO, sample.torqueResidualBodyNewtonMeters(), 1.0e-14);
		for (var rotor : sample.rotorSamples()) {
			assertEquals(ShadowStatus.AXIAL_REFERENCE_AVAILABLE, rotor.status());
			assertTrue(rotor.geometryMatch().matched());
			assertTrue(rotor.scalarReferenceComparable());
			assertTrue(rotor.vectorReferenceComparable());
			assertFalse(rotor.runtimeForceApplied());
			assertEquals(0.0, rotor.actualPowerClosureResidualWatts(), 1.0e-14);
		}
		assertStateEquals(before, state);
	}

	@Test
	void worldKinematicsUseLocalWindOrientationAndRotorArmVelocity() {
		DroneConfig config = exactFiveInchConfig();
		DroneState state = stateAtUniformRpm(config, RPM);
		double halfAngle = Math.PI * 0.25;
		Quaternion orientation = new Quaternion(
				Math.cos(halfAngle), 0.0, Math.sin(halfAngle), 0.0);
		state.setOrientation(orientation);
		state.setVelocityMetersPerSecond(new Vec3(3.0, 2.0, -1.0));
		state.setAngularVelocityBodyRadiansPerSecond(new Vec3(0.2, -0.1, 0.3));
		Vec3 baselineWind = new Vec3(0.5, 0.2, -0.4);
		Vec3[] localWind = {
				new Vec3(1.0, 0.1, 0.0)
		};
		DroneEnvironment environment = new DroneEnvironment(
				baselineWind, 1.0, Double.POSITIVE_INFINITY,
				0.0, 0.0, 0.0, Double.POSITIVE_INFINITY,
				null, null, null, null, 0.0, null, 0.0, 25.0, localWind);
		StateSnapshot before = snapshot(state);
		StateShadowSample sample = UiucDa4002AxialSurfaceV1RuntimeShadowAdapter
				.sample(PROPELLER, config, state, environment);

		for (int index = 0; index < config.rotors().size(); index++) {
			RotorSpec rotor = config.rotors().get(index);
			Vec3 wind = index == 0 ? localWind[0] : baselineWind;
			Vec3 expected = orientation.conjugate().rotate(
					state.velocityMetersPerSecond().subtract(wind)
			).add(state.angularVelocityBodyRadiansPerSecond().cross(
					rotor.positionBodyMeters().subtract(config.centerOfMassOffsetBodyMeters())
			));
			var rotorSample = sample.rotorSamples().get(index);
			assertVectorEquals(expected,
					rotorSample.relativeAirVelocityBodyMetersPerSecond(), 1.0e-14);
			assertEquals(ShadowStatus.OBLIQUE_AXIAL_PROJECTION_REFERENCE,
					rotorSample.status());
			assertTrue(rotorSample.scalarReferenceComparable());
			assertFalse(rotorSample.vectorReferenceComparable());
			assertFalse(rotorSample.runtimeForceApplied());
		}
		double positiveSpinTorque = sample.rotorSamples().get(0).reference()
				.orElseThrow().referenceReactionTorqueBodyNewtonMeters()
				.dot(config.rotors().get(0).thrustAxisBody());
		double negativeSpinTorque = sample.rotorSamples().get(1).reference()
				.orElseThrow().referenceReactionTorqueBodyNewtonMeters()
				.dot(config.rotors().get(1).thrustAxisBody());
		assertTrue(positiveSpinTorque > 0.0);
		assertTrue(negativeSpinTorque < 0.0);
		assertEquals(4, sample.comparableScalarRotorCount());
		assertEquals(0, sample.comparableVectorRotorCount());
		assertStateEquals(before, state);
	}

	@Test
	void zeroRpmGeometryReverseFlowAndHighJRemainExplicitlyBlocked() {
		DroneConfig exact = exactFiveInchConfig();
		DroneState stopped = new DroneState(exact.rotors().size());
		StateShadowSample stoppedSample = UiucDa4002AxialSurfaceV1RuntimeShadowAdapter
				.sample(PROPELLER, exact, stopped, DroneEnvironment.calm());
		assertEquals(4, stoppedSample.blockedRotorCount());
		assertTrue(stoppedSample.rotorSamples().stream().allMatch(rotor ->
				rotor.status() == ShadowStatus.BLOCKED_NON_POSITIVE_RPM
						&& rotor.reference().isEmpty()));

		DroneConfig wrongGeometry = DroneConfig.racingQuad();
		DroneState wrongGeometryState = stateAtUniformRpm(wrongGeometry, RPM);
		StateShadowSample geometrySample = UiucDa4002AxialSurfaceV1RuntimeShadowAdapter
				.sample(PROPELLER, wrongGeometry, wrongGeometryState,
						DroneEnvironment.calm());
		assertEquals(4, geometrySample.blockedRotorCount());
		assertTrue(geometrySample.rotorSamples().stream().allMatch(rotor ->
				rotor.status() == ShadowStatus.BLOCKED_GEOMETRY_MISMATCH
						&& rotor.message().contains("pitch+blade-count")
						&& rotor.reference().isEmpty()));

		DroneState reverse = stateAtUniformRpm(exact, RPM);
		reverse.setVelocityMetersPerSecond(new Vec3(0.0, -1.0, 0.0));
		StateShadowSample reverseSample = UiucDa4002AxialSurfaceV1RuntimeShadowAdapter
				.sample(PROPELLER, exact, reverse, DroneEnvironment.calm());
		assertTrue(reverseSample.rotorSamples().stream().allMatch(rotor ->
				rotor.status() == ShadowStatus.BLOCKED_REVERSE_AXIAL_FLOW
						&& !rotor.scalarReferenceComparable()));

		double highRpm = 4_000.0;
		DroneState highJ = stateAtUniformRpm(exact, highRpm);
		double highAxialSpeed = 0.90 * highRpm / 60.0 * PROPELLER.diameterMeters();
		highJ.setVelocityMetersPerSecond(new Vec3(0.0, highAxialSpeed, 0.0));
		StateShadowSample highJSample = UiucDa4002AxialSurfaceV1RuntimeShadowAdapter
				.sample(PROPELLER, exact, highJ, DroneEnvironment.calm());
		assertTrue(highJSample.rotorSamples().stream().allMatch(rotor ->
				rotor.status() == ShadowStatus.BLOCKED_COEFFICIENT_SURFACE
						&& !rotor.scalarReferenceComparable()
						&& !rotor.reference().orElseThrow().clamped()));
	}

	private static DroneConfig exactFiveInchConfig() {
		return DroneConfig.racingQuad()
				.withRotorBladeCount(2)
				.withRotorBladePitchToDiameterRatio(3.75 / 5.0);
	}

	private static DroneState stateAtUniformRpm(DroneConfig config, double rpm) {
		DroneState state = new DroneState(config.rotors().size());
		double omega = rpm * 2.0 * Math.PI / 60.0;
		for (int index = 0; index < config.rotors().size(); index++) {
			state.setMotorOmegaRadiansPerSecond(index, omega);
		}
		return state;
	}

	private static StateSnapshot snapshot(DroneState state) {
		return new StateSnapshot(
				state.positionMeters(),
				state.velocityMetersPerSecond(),
				state.orientation(),
				state.angularVelocityBodyRadiansPerSecond(),
				state.motorOmegaRadiansPerSecond(),
				state.rotorThrustNewtons(),
				state.motorShaftPowerWatts(),
				state.motorAerodynamicTorqueNewtonMeters(),
				state.rotorForceBodyNewtons(),
				state.rotorTorqueBodyNewtonMeters()
		);
	}

	private static void assertStateEquals(StateSnapshot expected, DroneState actual) {
		assertEquals(expected.positionMeters(), actual.positionMeters());
		assertEquals(expected.velocityMetersPerSecond(), actual.velocityMetersPerSecond());
		assertEquals(expected.orientation(), actual.orientation());
		assertEquals(expected.angularVelocityBodyRadiansPerSecond(),
				actual.angularVelocityBodyRadiansPerSecond());
		assertArrayEquals(expected.motorOmegaRadiansPerSecond(),
				actual.motorOmegaRadiansPerSecond(), 0.0);
		assertArrayEquals(expected.rotorThrustNewtons(), actual.rotorThrustNewtons(), 0.0);
		assertArrayEquals(expected.motorShaftPowerWatts(),
				actual.motorShaftPowerWatts(), 0.0);
		assertArrayEquals(expected.motorAerodynamicTorqueNewtonMeters(),
				actual.motorAerodynamicTorqueNewtonMeters(), 0.0);
		assertArrayEquals(expected.rotorForceBodyNewtons(), actual.rotorForceBodyNewtons());
		assertArrayEquals(expected.rotorTorqueBodyNewtonMeters(),
				actual.rotorTorqueBodyNewtonMeters());
	}

	private static void assertVectorEquals(Vec3 expected, Vec3 actual, double tolerance) {
		assertEquals(expected.x(), actual.x(), tolerance);
		assertEquals(expected.y(), actual.y(), tolerance);
		assertEquals(expected.z(), actual.z(), tolerance);
	}

	private record StateSnapshot(
			Vec3 positionMeters,
			Vec3 velocityMetersPerSecond,
			Quaternion orientation,
			Vec3 angularVelocityBodyRadiansPerSecond,
			double[] motorOmegaRadiansPerSecond,
			double[] rotorThrustNewtons,
			double[] motorShaftPowerWatts,
			double[] motorAerodynamicTorqueNewtonMeters,
			Vec3[] rotorForceBodyNewtons,
			Vec3[] rotorTorqueBodyNewtonMeters
	) {
	}
}
