package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

class A4mcDiskGradientCalibrationTest {
	@Test
	void racingQuadAuditMatchesGeneratedDiskGradientPacketSummary() {
		A4mcDiskGradientCalibration.A4mcDiskGradientAudit audit =
				A4mcDiskGradientCalibration.racingQuadAudit();

		assertEquals("A4MC-Disk-Gradient-Response-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("blackbox fitting"));
		assertEquals(6227, audit.packetMetricRowCount());
		assertEquals(4, audit.sourceReferenceCount());
		assertEquals(384, audit.responseMatrixScenarioCount());
		assertEquals(6144, audit.responseMatrixMetricRowCount());
		assertEquals(64, audit.wallSkimReferenceMetricRowCount());
		assertEquals(14, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(0.33, audit.wallSkimReferenceRawPressureGradientMetersPerSecond(), 1.0e-12);
		assertEquals(0.86, audit.wallSkimReferenceSourceQuality(), 1.0e-12);
		assertEquals(0.311758935325, audit.hoverTiltStartRawGradientAtWallSkimQualityMetersPerSecond(), 1.0e-12);
		assertEquals(0.484958343838, audit.hoverThrustLossStartRawGradientAtWallSkimQualityMetersPerSecond(), 1.0e-12);

		A4mcDiskGradientCalibration.DiskGradientResponse wallSkim = audit.wallSkimHover();
		assertEquals("racingQuad", wallSkim.presetName());
		assertEquals("hover", wallSkim.spinState());
		assertEquals(0.33, wallSkim.rawGradientMetersPerSecond(), 1.0e-12);
		assertEquals(0.86, wallSkim.sourceQualityFactor(), 1.0e-12);
		assertEquals(0.44695088387, wallSkim.spinRatio(), 1.0e-12);
		assertEquals(0.33, wallSkim.adoptedGradientMetersPerSecond(), 1.0e-15);
		assertEquals(86.5997042568, wallSkim.tipSpeedMetersPerSecond(), 1.0e-10);
		assertEquals(0.00381063657009, wallSkim.gradientOverTipSpeed(), 1.0e-14);
		assertEquals(0.199765092593, wallSkim.steadyThrustFractionProxy(), 1.0e-12);
		assertEquals(2.69682875, wallSkim.steadyThrustNewtonsProxy(), 1.0e-12);
		assertEquals(1.0, wallSkim.thrustScale(), 1.0e-15);
		assertEquals(0.0, wallSkim.thrustLossFraction(), 1.0e-15);
		assertEquals(0.00650857245551, wallSkim.loadFactor(), 1.0e-14);
		assertEquals(0.0108958315556, wallSkim.vibration(), 1.0e-14);
		assertEquals(0.0, wallSkim.stallIntensity(), 1.0e-15);
		assertEquals(0.000177494850399, wallSkim.flappingTiltDegrees(), 1.0e-15);
		assertEquals(0.999999999995, wallSkim.verticalForceScaleProxy(), 1.0e-12);
	}

	@Test
	void responseMatrixExtremaAndQualityGateMatchPacketAnchors() {
		A4mcDiskGradientCalibration.A4mcDiskGradientAudit audit =
				A4mcDiskGradientCalibration.racingQuadAudit();

		A4mcDiskGradientCalibration.DiskGradientResponse max = audit.maxGradient();
		assertEquals("max", max.spinState());
		assertEquals(12.0, max.rawGradientMetersPerSecond(), 1.0e-12);
		assertEquals(1.0, max.sourceQualityFactor(), 1.0e-12);
		assertEquals(1.0, max.spinRatio(), 1.0e-12);
		assertEquals(193.75664616, max.tipSpeedMetersPerSecond(), 1.0e-8);
		assertEquals(4.5, max.flappingTiltDegrees(), 1.0e-12);
		assertEquals(0.0377216543967, max.thrustLossFraction(), 1.0e-13);
		assertEquals(0.0803353077685, max.loadFactor(), 1.0e-13);
		assertEquals(0.106039389417, max.vibration(), 1.0e-12);
		assertEquals(0.14, max.stallIntensity(), 1.0e-15);
		assertEquals(1.0602875205865552, max.lateralFlappingForceNewtonsProxy(), 1.0e-15);
		assertEquals(0.959305846234, max.verticalForceScaleProxy(), 1.0e-12);

		A4mcDiskGradientCalibration.DiskGradientResponse qualityZero = audit.qualityZero();
		assertEquals(12.0, qualityZero.rawGradientMetersPerSecond(), 1.0e-12);
		assertEquals(0.0, qualityZero.sourceQualityFactor(), 1.0e-12);
		assertEquals(0.0, qualityZero.adoptedGradientMetersPerSecond(), 1.0e-12);
		assertEquals(0.0, qualityZero.thrustLossFraction(), 1.0e-12);
		assertEquals(0.0, qualityZero.loadFactor(), 1.0e-12);
		assertEquals(0.0, qualityZero.vibration(), 1.0e-12);
		assertEquals(0.0, qualityZero.flappingTiltDegrees(), 1.0e-12);

		A4mcDiskGradientCalibration.ResponseMatrixExtrema extrema = audit.matrixExtrema();
		assertEquals(4.5, extrema.maxFlappingTiltDegrees(), 1.0e-12);
		assertEquals(0.045, extrema.maxThrustLossFraction(), 1.0e-15);
		assertEquals(0.18, extrema.maxLoadFactor(), 1.0e-15);
		assertEquals(0.18, extrema.maxVibration(), 1.0e-15);
		assertEquals(0.14, extrema.maxStallIntensity(), 1.0e-15);
	}

	@Test
	void calibrationResponseTracksDronePhysicsDiskGradientMethods() throws ReflectiveOperationException {
		DroneConfig config = DroneConfig.racingQuad();
		RotorSpec rotor = config.rotors().get(0);
		double rawGradient = 6.0;
		double sourceQuality = 0.86;
		double spinRatio = A4mcDiskGradientCalibration.hoverSpinRatio(config, rotor);
		A4mcDiskGradientCalibration.DiskGradientResponse response =
				A4mcDiskGradientCalibration.response(config, "racingQuad", "hover", rawGradient, sourceQuality, spinRatio);
		Vec3 adoptedGradient = new Vec3(response.adoptedGradientMetersPerSecond(), 0.0, 0.0);
		double omega = rotor.maxOmegaRadiansPerSecond() * spinRatio;
		double thrust = rotor.maxThrustNewtons() * spinRatio * spinRatio;

		assertEquals(
				invokeDouble("rotorDiskWindGradientThrustScale", rotor, adoptedGradient, omega),
				response.thrustScale(),
				1.0e-15
		);
		assertEquals(
				invokeDouble("rotorDiskWindGradientLoadFactor", rotor, adoptedGradient, omega),
				response.loadFactor(),
				1.0e-15
		);
		assertEquals(
				invokeDouble("rotorDiskWindGradientVibration", rotor, adoptedGradient, omega),
				response.vibration(),
				1.0e-15
		);
		assertEquals(
				invokeDouble("rotorDiskWindGradientStallIntensity", rotor, adoptedGradient, omega),
				response.stallIntensity(),
				1.0e-15
		);

		RotorFlappingForceModel.RotorFlappingForceSample flappingSample =
				RotorFlappingForceModel.sampleSteady(
						rotor,
						Vec3.ZERO,
						Vec3.ZERO,
						adoptedGradient,
						omega,
						thrust
				);
		assertEquals(
				Math.toDegrees(flappingSample.flappingTiltMagnitudeRadians()),
				response.flappingTiltDegrees(),
				1.0e-15
		);
		assertEquals(
				flappingSample.transverseFlappingForceBodyNewtons().length(),
				response.lateralFlappingForceNewtonsProxy(),
				1.0e-15
		);
	}

	@Test
	void diskGradientAuditRequiresRotorConfiguration() {
		assertThrows(IllegalArgumentException.class, () -> A4mcDiskGradientCalibration.audit(null, "missing"));
	}

	private static double invokeDouble(String methodName, RotorSpec rotor, Vec3 gradient, double omega)
			throws ReflectiveOperationException {
		Method method = DronePhysics.class.getDeclaredMethod(methodName, RotorSpec.class, Vec3.class, double.class);
		method.setAccessible(true);
		try {
			return (double) method.invoke(null, rotor, gradient, omega);
		} catch (InvocationTargetException exception) {
			throw new AssertionError(exception.getCause());
		}
	}

}
