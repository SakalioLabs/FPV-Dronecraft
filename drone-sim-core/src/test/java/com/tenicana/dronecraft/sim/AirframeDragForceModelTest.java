package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AirframeDragForceModelTest {
	@Test
	void forwardFlowSampleClosesQuadraticLinearAndPowerTerms() {
		DroneConfig config = DroneConfig.apDrone()
				.withBodyDragCoefficients(new Vec3(0.20, 0.30, 0.40))
				.withLinearDragCoefficient(0.50);
		Vec3 relativeAirVelocityBody = new Vec3(0.0, 0.0, 10.0);

		AirframeDragForceModel.AirframeDragForceSample sample =
				AirframeDragForceModel.sampleSteady(config, relativeAirVelocityBody, 1.0, 0.0);

		assertEquals(0.0, sample.targetSeparationIntensity(), 1.0e-15);
		assertEquals(0.0, sample.effectiveSeparationIntensity(), 1.0e-15);
		assertVectorEquals(new Vec3(0.0, 0.0, -40.0),
				sample.baseQuadraticDragForceBodyNewtons(), 1.0e-12);
		assertVectorEquals(Vec3.ZERO, sample.separatedFlowDragForceBodyNewtons(), 1.0e-12);
		assertVectorEquals(new Vec3(0.0, 0.0, -40.0),
				sample.bodyDragForceBodyNewtons(), 1.0e-12);
		assertVectorEquals(new Vec3(0.0, 0.0, -5.0),
				sample.linearDampingDragForceBodyNewtons(), 1.0e-12);
		assertVectorEquals(new Vec3(0.0, 0.0, -45.0),
				sample.totalDragForceBodyNewtons(), 1.0e-12);
		assertEquals(45.0, sample.dragAlongFlowNewtons(), 1.0e-12);
		assertEquals(400.0, sample.bodyDragPowerDissipationWatts(), 1.0e-12);
		assertEquals(50.0, sample.linearDampingPowerDissipationWatts(), 1.0e-12);
		assertEquals(450.0, sample.totalDragPowerDissipationWatts(), 1.0e-12);
		assertEquals(4.5, sample.equivalentLinearCoefficient(), 1.0e-12);
		assertTrue(sample.equivalentCdAMetersSquared() > 0.0);
		assertTrue(sample.imavReferenceDragRatio() > 0.0);
	}

	@Test
	void densityScalingAndReverseFlowPreserveOddDragSymmetry() {
		DroneConfig config = DroneConfig.apDrone()
				.withBodyDragCoefficients(new Vec3(0.12, 0.08, 0.16))
				.withLinearDragCoefficient(0.24);
		Vec3 forwardFlow = new Vec3(1.5, 0.0, 8.0);
		AirframeDragForceModel.AirframeDragForceSample standard =
				AirframeDragForceModel.sampleSteady(config, forwardFlow, 1.0, 0.0);
		AirframeDragForceModel.AirframeDragForceSample thinAir =
				AirframeDragForceModel.sampleSteady(config, forwardFlow, 0.62, 0.0);
		AirframeDragForceModel.AirframeDragForceSample reverse =
				AirframeDragForceModel.sampleSteady(config, forwardFlow.multiply(-1.0), 1.0, 0.0);

		assertVectorEquals(standard.totalDragForceBodyNewtons().multiply(0.62),
				thinAir.totalDragForceBodyNewtons(), 1.0e-12);
		assertVectorEquals(standard.totalDragForceBodyNewtons().multiply(-1.0),
				reverse.totalDragForceBodyNewtons(), 1.0e-12);
		assertEquals(standard.totalDragPowerDissipationWatts() * 0.62,
				thinAir.totalDragPowerDissipationWatts(), 1.0e-12);
		assertEquals(standard.totalDragPowerDissipationWatts(),
				reverse.totalDragPowerDissipationWatts(), 1.0e-12);
	}

	@Test
	void settledHighSideslipBuildsMoreSeparatedDragThanImmediateResponse() {
		DroneConfig config = DroneConfig.racingQuad()
				.withBodyDragCoefficients(new Vec3(0.36, 0.18, 0.04))
				.withLinearDragCoefficient(0.0);
		Vec3 highSideslipVelocity = new Vec3(10.0, 0.0, 2.0);
		AirframeDragForceModel.AirframeDragForceSample immediate =
				AirframeDragForceModel.sampleSteady(config, highSideslipVelocity, 1.0, 0.0);
		AirframeDragForceModel.AirframeDragForceSample settled =
				AirframeDragForceModel.sampleSettled(config, highSideslipVelocity, 1.0);

		assertTrue(immediate.targetSeparationIntensity() > 0.95);
		assertEquals(immediate.targetSeparationIntensity(), settled.targetSeparationIntensity(), 1.0e-15);
		assertEquals(immediate.targetSeparationIntensity() * 0.32,
				immediate.effectiveSeparationIntensity(), 1.0e-12);
		assertEquals(settled.targetSeparationIntensity(), settled.effectiveSeparationIntensity(), 1.0e-15);
		assertTrue(settled.separatedFlowDragForceBodyNewtons().length()
				> immediate.separatedFlowDragForceBodyNewtons().length() * 3.0);
		assertTrue(settled.totalDragForceBodyNewtons().dot(highSideslipVelocity) < 0.0);
		assertTrue(settled.totalDragPowerDissipationWatts()
				> immediate.totalDragPowerDissipationWatts());

		Quaternion bodyToWorld = new Quaternion(
				Math.cos(Math.PI * 0.25),
				0.0,
				Math.sin(Math.PI * 0.25),
				0.0
		);
		assertEquals(settled.totalDragForceBodyNewtons().length(),
				settled.totalDragForceWorldNewtons(bodyToWorld).length(), 1.0e-12);
	}

	private static void assertVectorEquals(Vec3 expected, Vec3 actual, double tolerance) {
		assertEquals(expected.x(), actual.x(), tolerance);
		assertEquals(expected.y(), actual.y(), tolerance);
		assertEquals(expected.z(), actual.z(), tolerance);
	}
}
