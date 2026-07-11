package com.tenicana.dronecraft.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.Quaternion;
import com.tenicana.dronecraft.sim.Vec3;
import com.tenicana.dronecraft.sim.flight.FlightModel;

class SimulationFlightRuntimeTest {
	@Test
	void replacingConfigPreservesCanonicalKinematics() {
		SimulationFlightRuntime runtime = new SimulationFlightRuntime(DroneConfig.racingQuad());
		FlightModel previousModel = runtime.flightModel();
		Vec3 position = new Vec3(1.25, 2.5, -3.75);
		Vec3 velocity = new Vec3(-4.0, 5.5, 6.25);
		Quaternion orientation = new Quaternion(0.92, 0.08, -0.21, 0.32).normalized();
		Quaternion estimatedOrientation = new Quaternion(0.88, -0.12, 0.18, -0.41).normalized();
		Vec3 angularVelocity = new Vec3(0.7, -0.8, 0.9);

		runtime.state().setPositionMeters(position);
		runtime.state().setVelocityMetersPerSecond(velocity);
		runtime.state().setOrientation(orientation);
		runtime.state().setEstimatedOrientation(estimatedOrientation);
		runtime.state().setAngularVelocityBodyRadiansPerSecond(angularVelocity);

		runtime.replaceConfigPreservingKinematics(DroneConfig.hexLift());

		assertNotSame(previousModel, runtime.flightModel());
		assertEquals(DroneConfig.hexLift().rotors().size(), runtime.config().rotors().size());
		assertVecEquals(position, runtime.state().positionMeters());
		assertVecEquals(velocity, runtime.state().velocityMetersPerSecond());
		assertQuaternionEquals(orientation, runtime.state().orientation());
		assertQuaternionEquals(estimatedOrientation, runtime.state().estimatedOrientation());
		assertVecEquals(angularVelocity, runtime.state().angularVelocityBodyRadiansPerSecond());
	}

	@Test
	void sharedStencilGeometryUsesRepresentativeRadiusAndCanonicalBodyTransforms() {
		DroneConfig config = DroneConfig.racingQuad();
		SimulationFlightRuntime runtime = new SimulationFlightRuntime(config);
		double expectedRadius = 0.0;
		for (var rotor : config.rotors()) {
			expectedRadius += rotor.radiusMeters();
		}
		expectedRadius /= config.rotors().size();
		Quaternion orientation = new Quaternion(0.81, -0.14, 0.31, 0.47).normalized();
		Vec3 bodyVector = new Vec3(2.5, -1.25, 0.75);
		runtime.state().setOrientation(orientation);

		assertEquals(expectedRadius, runtime.representativeRotorRadiusMeters(), 1.0e-12);
		assertVecEquals(orientation.rotate(new Vec3(1.0, 0.0, 0.0)), runtime.bodyXWorldDirection());
		assertVecEquals(orientation.rotate(new Vec3(0.0, 0.0, 1.0)), runtime.bodyZWorldDirection());
		assertVecEquals(bodyVector, runtime.worldVectorToBody(orientation.rotate(bodyVector)));
		assertEquals(Vec3.ZERO, runtime.worldVectorToBody(null));
	}

	private static void assertVecEquals(Vec3 expected, Vec3 actual) {
		assertEquals(expected.x(), actual.x(), 1.0e-12);
		assertEquals(expected.y(), actual.y(), 1.0e-12);
		assertEquals(expected.z(), actual.z(), 1.0e-12);
	}

	private static void assertQuaternionEquals(Quaternion expected, Quaternion actual) {
		assertEquals(expected.w(), actual.w(), 1.0e-12);
		assertEquals(expected.x(), actual.x(), 1.0e-12);
		assertEquals(expected.y(), actual.y(), 1.0e-12);
		assertEquals(expected.z(), actual.z(), 1.0e-12);
	}
}
