package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DroneEnvironmentNearfieldGeometryTest {
	@Test
	void weightedSurfaceEffectsGateFinitePartialSupport() {
		DroneConfig config = DroneConfig.racingQuad();
		double rotorRadius = config.rotors().get(0).radiusMeters();
		double fullGround = DroneEnvironment.groundEffectThrustMultiplier(config, rotorRadius);
		double fullCeiling = DroneEnvironment.ceilingEffectThrustMultiplier(config, rotorRadius);
		double[] evenWeights = {1.0, 1.0, 1.0, 1.0};
		double[] fullSurface = {rotorRadius, rotorRadius, rotorRadius, rotorRadius};

		double weightedFullGround = DroneEnvironment.weightedGroundEffectThrustMultiplier(
				config,
				fullSurface,
				evenWeights
		);
		double weightedFullCeiling = DroneEnvironment.weightedCeilingEffectThrustMultiplier(
				config,
				fullSurface,
				evenWeights
		);
		assertEquals(Double.doubleToRawLongBits(fullGround), Double.doubleToRawLongBits(weightedFullGround));
		assertEquals(Double.doubleToRawLongBits(fullCeiling), Double.doubleToRawLongBits(weightedFullCeiling));

		double[] quarterSupported = {
				rotorRadius,
				Double.POSITIVE_INFINITY,
				Double.POSITIVE_INFINITY,
				Double.POSITIVE_INFINITY
		};
		assertEquals(0.25,
				DroneEnvironment.weightedSurfaceEffectSupportCoverage(quarterSupported, evenWeights),
				1.0e-12);
		assertEquals(rotorRadius,
				DroneEnvironment.partialSurfaceCoveragePatchDiameterMeters(config, 0.25),
				1.0e-12);
		assertEquals(0.0, DroneEnvironment.partialSurfaceCoverageGate(config, 0.25), 1.0e-12);
		assertEquals(1.0,
				DroneEnvironment.weightedGroundEffectThrustMultiplier(config, quarterSupported, evenWeights),
				1.0e-12);
		assertEquals(1.0,
				DroneEnvironment.weightedCeilingEffectThrustMultiplier(config, quarterSupported, evenWeights),
				1.0e-12);

		double[] partialSupported = {rotorRadius, Double.POSITIVE_INFINITY};
		double[] partialWeights = {0.5625, 0.4375};
		assertEquals(0.5625,
				DroneEnvironment.weightedSurfaceEffectSupportCoverage(partialSupported, partialWeights),
				1.0e-12);
		assertEquals(rotorRadius * 1.5,
				DroneEnvironment.partialSurfaceCoveragePatchDiameterMeters(config, 0.5625),
				1.0e-12);
		assertEquals(0.5, DroneEnvironment.partialSurfaceCoverageGate(config, 0.5625), 1.0e-12);
		assertEquals(1.0 + (fullGround - 1.0) * 0.5,
				DroneEnvironment.weightedGroundEffectThrustMultiplier(config, partialSupported, partialWeights),
				1.0e-12);
		assertEquals(1.0 + (fullCeiling - 1.0) * 0.5,
				DroneEnvironment.weightedCeilingEffectThrustMultiplier(config, partialSupported, partialWeights),
				1.0e-12);
	}

	@Test
	void wallForceFactorsPreserveOldEnvironmentsAndSanitizeExplicitGeometry() {
		DroneEnvironment oldEnvironment = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				new double[] {0.7},
				new Vec3[] {new Vec3(1.0, 0.0, 0.0)}
		);
		assertEquals(1.0, oldEnvironment.rotorFlowObstructionWallForceFactor(0), 0.0);
		assertEquals(0.0, oldEnvironment.rotorFlowObstructionWallForceFactor(1), 0.0);

		DroneEnvironment explicitGeometry = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				new double[] {0.7, 0.7, 0.7, 0.7},
				new Vec3[] {
						new Vec3(1.0, 0.0, 0.0),
						new Vec3(1.0, 0.0, 0.0),
						new Vec3(1.0, 0.0, 0.0),
						new Vec3(1.0, 0.0, 0.0)
				},
				new double[] {0.35, Double.NaN, -1.0, 2.0}
		);
		assertEquals(0.35, explicitGeometry.rotorFlowObstructionWallForceFactor(0), 0.0);
		assertEquals(1.0, explicitGeometry.rotorFlowObstructionWallForceFactor(1), 0.0);
		assertEquals(0.0, explicitGeometry.rotorFlowObstructionWallForceFactor(2), 0.0);
		assertEquals(1.0, explicitGeometry.rotorFlowObstructionWallForceFactor(3), 0.0);
	}

	@Test
	void wallForceGeometryFactorScalesOnlyTheLateralWallForce() {
		RotorSpec rotor = DroneConfig.racingQuad().rotors().get(0);
		double omega = rotor.maxOmegaRadiansPerSecond() * 0.72;
		double thrust = rotor.maxThrustNewtons() * 0.55;
		Vec3 full = DronePhysics.calculateSteadyRotorWallEffectForce(
				rotor,
				Vec3.ZERO,
				omega,
				thrust,
				0.70,
				new Vec3(1.0, 0.0, 0.0)
		);
		Vec3 gated = DronePhysics.calculateSteadyRotorWallEffectForce(
				rotor,
				Vec3.ZERO,
				omega,
				thrust,
				0.70,
				new Vec3(1.0, 0.0, 0.0),
				0.35
		);
		Vec3 suppressed = DronePhysics.calculateSteadyRotorWallEffectForce(
				rotor,
				Vec3.ZERO,
				omega,
				thrust,
				0.70,
				new Vec3(1.0, 0.0, 0.0),
				0.0
		);

		assertTrue(full.length() > 0.01);
		assertEquals(full.x() * 0.35, gated.x(), 1.0e-12);
		assertEquals(full.y() * 0.35, gated.y(), 1.0e-12);
		assertEquals(full.z() * 0.35, gated.z(), 1.0e-12);
		assertEquals(Vec3.ZERO, suppressed);
	}

	@Test
	void rotorWallCushionConsumesThePerRotorGeometryFactor() {
		DroneConfig config = DroneConfig.racingQuad();
		DronePhysics fullGeometry = new DronePhysics(config);
		DronePhysics explicitFullGeometry = new DronePhysics(config);
		DronePhysics gatedGeometry = new DronePhysics(config);
		DroneInput hover = new DroneInput(config.hoverThrottle() + 0.05, 0.0, 0.0, 0.0, true);
		double[] obstruction = {0.70, 0.70, 0.70, 0.70};
		Vec3[] directions = {
				new Vec3(1.0, 0.0, 0.0),
				new Vec3(1.0, 0.0, 0.0),
				new Vec3(1.0, 0.0, 0.0),
				new Vec3(1.0, 0.0, 0.0)
		};
		DroneEnvironment fullEnvironment = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				obstruction,
				directions
		);
		DroneEnvironment gatedEnvironment = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				obstruction,
				directions,
				new double[] {0.35, 0.35, 0.35, 0.35}
		);
		DroneEnvironment explicitFullEnvironment = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				obstruction,
				directions,
				new double[] {1.0, 1.0, 1.0, 1.0}
		);

		for (int i = 0; i < 150; i++) {
			holdInStillAir(fullGeometry);
			holdInStillAir(explicitFullGeometry);
			holdInStillAir(gatedGeometry);
			fullGeometry.step(hover, 0.005, fullEnvironment);
			explicitFullGeometry.step(hover, 0.005, explicitFullEnvironment);
			gatedGeometry.step(hover, 0.005, gatedEnvironment);
		}

		double fullForce = fullGeometry.state().rotorWallEffectForceBodyNewtons().length();
		double gatedForce = gatedGeometry.state().rotorWallEffectForceBodyNewtons().length();
		assertEquals(
				fullGeometry.state().rotorWallEffectForceBodyNewtons(),
				explicitFullGeometry.state().rotorWallEffectForceBodyNewtons()
		);
		assertEquals(fullGeometry.state().positionMeters(), explicitFullGeometry.state().positionMeters());
		assertEquals(fullGeometry.state().velocityMetersPerSecond(), explicitFullGeometry.state().velocityMetersPerSecond());
		assertEquals(
				fullGeometry.state().angularVelocityBodyRadiansPerSecond(),
				explicitFullGeometry.state().angularVelocityBodyRadiansPerSecond()
		);
		for (int i = 0; i < config.rotors().size(); i++) {
			assertEquals(
					Double.doubleToRawLongBits(fullGeometry.state().rotorThrustNewtons(i)),
					Double.doubleToRawLongBits(explicitFullGeometry.state().rotorThrustNewtons(i))
			);
		}
		assertTrue(fullForce > 0.07, () -> "fullForce=" + fullForce);
		assertTrue(gatedForce / fullForce > 0.30 && gatedForce / fullForce < 0.40,
				() -> "fullForce=" + fullForce + " gatedForce=" + gatedForce);
	}

	private static void holdInStillAir(DronePhysics physics) {
		physics.state().setPositionMeters(new Vec3(0.0, 20.0, 0.0));
		physics.state().setVelocityMetersPerSecond(Vec3.ZERO);
		physics.state().setOrientation(Quaternion.IDENTITY);
		physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
	}
}
