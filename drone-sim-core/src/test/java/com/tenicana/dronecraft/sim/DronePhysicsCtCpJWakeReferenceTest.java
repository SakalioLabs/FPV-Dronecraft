package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class DronePhysicsCtCpJWakeReferenceTest {
	private static final String[] REFERENCE_ARRAY_FIELDS = {
			"rotorCtCpJWakeReferenceApplied",
			"rotorCtCpJWakeReferenceIdealInducedVelocityMetersPerSecond",
			"rotorCtCpJWakeReferenceFarWakeAxialExcessVelocityMetersPerSecond",
			"rotorCtCpJWakeReferenceFarWakeEquivalentRadiusMeters",
			"rotorCtCpJWakeReferenceTangentialVelocityMetersPerSecond",
			"rotorCtCpJWakeReferenceDiskLoadingStrength"
	};

	@Test
	void pairLocalAxialWakeConsumesTheCommittedReferenceAndFallsBackExactlyWhenCleared()
			throws ReflectiveOperationException {
		DroneConfig config = DroneConfig.apDrone();
		DronePhysics physics = new DronePhysics(config);
		RotorSpec source = config.rotors().get(0);
		double fallback = 3.75;
		Method axialWake = privateMethod(
				"rotorCtCpJWakeAxialExcessVelocityMetersPerSecond",
				int.class,
				RotorSpec.class,
				double.class,
				double.class
		);

		double beforeCommit = (double) axialWake.invoke(physics, 0, source, 0.0, fallback);
		assertEquals(Double.doubleToRawLongBits(fallback), Double.doubleToRawLongBits(beforeCommit));

		commitAcceptedReference(physics, config, 0, 6_000.0, 0.4064);
		boolean[] applied = booleanArray(physics, "rotorCtCpJWakeReferenceApplied");
		double[] ideal = doubleArray(
				physics,
				"rotorCtCpJWakeReferenceIdealInducedVelocityMetersPerSecond"
		);
		double[] farWakeExcess = doubleArray(
				physics,
				"rotorCtCpJWakeReferenceFarWakeAxialExcessVelocityMetersPerSecond"
		);
		assertTrue(applied[0]);

		double nearWake = (double) axialWake.invoke(physics, 0, source, 0.0, fallback);
		double farWake = (double) axialWake.invoke(
				physics,
				0,
				source,
				source.radiusMeters() * 4.0,
				fallback
		);
		assertEquals(Double.doubleToRawLongBits(ideal[0]), Double.doubleToRawLongBits(nearWake));
		assertEquals(Double.doubleToRawLongBits(farWakeExcess[0]), Double.doubleToRawLongBits(farWake));
		assertFalse(Double.doubleToRawLongBits(beforeCommit) == Double.doubleToRawLongBits(nearWake));

		physics.resetControlLoops();
		double afterClear = (double) axialWake.invoke(physics, 0, source, 0.0, fallback);
		assertEquals(Double.doubleToRawLongBits(fallback), Double.doubleToRawLongBits(afterClear));
	}

	@Test
	void aCommittedSampleChangesOnlyTheNextWakePass() throws ReflectiveOperationException {
		DroneConfig config = DroneConfig.apDrone();
		DronePhysics physics = new DronePhysics(config);
		double rpm = 6_000.0;
		double omega = rpm * (2.0 * Math.PI) / 60.0;
		Vec3 currentWakeRelativeAir = new Vec3(14.0, 0.0, 0.0);
		for (int rotorIndex = 0; rotorIndex < config.rotors().size(); rotorIndex++) {
			physics.state().setMotorOmegaRadiansPerSecond(rotorIndex, omega);
			physics.state().setRotorInducedVelocityMetersPerSecond(rotorIndex, 3.0);
		}
		Method updateWake = privateMethod(
				"updateRotorWakeInterference",
				boolean.class,
				Vec3.class,
				double.class
		);

		updateWake.invoke(physics, true, currentWakeRelativeAir, 0.0);
		double[] fallbackTarget = doubleArray(
				physics,
				"rotorWakeInterferenceTargetIntensity"
		).clone();
		Vec3[] fallbackDownwash = ((Vec3[]) privateField(
				"rotorWakeInterferenceTargetDownwashVelocityBodyMetersPerSecond"
		).get(physics)).clone();
		assertTrue(max(fallbackTarget) > 0.0);

		for (int rotorIndex = 0; rotorIndex < config.rotors().size(); rotorIndex++) {
			commitAcceptedReference(physics, config, rotorIndex, rpm, 0.4064);
		}
		assertAllApplied(physics);
		assertArrayEquals(
				fallbackTarget,
				doubleArray(physics, "rotorWakeInterferenceTargetIntensity"),
				0.0
		);
		assertArrayEquals(
				fallbackDownwash,
				(Vec3[]) privateField(
						"rotorWakeInterferenceTargetDownwashVelocityBodyMetersPerSecond"
				).get(physics)
		);

		updateWake.invoke(physics, true, currentWakeRelativeAir, 0.0);
		double[] referenceTarget = doubleArray(
				physics,
				"rotorWakeInterferenceTargetIntensity"
		);
		Vec3[] referenceDownwash = (Vec3[]) privateField(
				"rotorWakeInterferenceTargetDownwashVelocityBodyMetersPerSecond"
		).get(physics);
		assertTrue(!Arrays.equals(fallbackTarget, referenceTarget)
				|| !Arrays.equals(fallbackDownwash, referenceDownwash));
	}

	@Test
	void publicStepUsesCurrentReferenceForForceAndCommittedReferenceForTheNextWakePass()
			throws ReflectiveOperationException {
		DroneConfig config = DroneConfig.apDrone();
		DronePhysics enabled = new DronePhysics(config);
		DronePhysics fallbackOnly = new DronePhysics(config);
		privateField("rotorCtCpJWakeReferenceEnabled").setBoolean(fallbackOnly, false);
		double[] motorPower = {1.0, 1.0, 1.0, 1.0};
		double[] motorRpm = {6_000.0, 6_000.0, 6_000.0, 6_000.0};
		double[] rotorThrust = {3.0, 3.0, 3.0, 3.0};
		DroneInput powered = new DroneInput(1.0, 0.0, 0.0, 0.0, true);
		primePoweredControl(enabled, powered);
		primePoweredControl(fallbackOnly, powered);
		enabled.restoreDirectFlightTelemetry(powered, motorPower, motorRpm, rotorThrust);
		fallbackOnly.restoreDirectFlightTelemetry(powered, motorPower, motorRpm, rotorThrust);

		prepareStateForFlow(enabled, new Vec3(0.0, 5.0, 0.0));
		prepareStateForFlow(fallbackOnly, new Vec3(0.0, 5.0, 0.0));
		enabled.step(powered, 1.0e-6);
		fallbackOnly.step(powered, 1.0e-6);

		assertWakeTargetsEqual(enabled, fallbackOnly);
		assertAllApplied(enabled);
		assertAllCleared(fallbackOnly);
		assertFalse(rawEqual(
				enabled.state().rotorPropellerPowerScale(0),
				fallbackOnly.state().rotorPropellerPowerScale(0)
		));
		assertFalse(rawEqual(
				enabled.state().rotorThrustNewtons(0),
				fallbackOnly.state().rotorThrustNewtons(0)
		));
		assertFalse(rawEqual(
				enabled.state().motorAerodynamicTorqueNewtonMeters(0),
				fallbackOnly.state().motorAerodynamicTorqueNewtonMeters(0)
		));
		assertFalse(rawEqual(
				enabled.state().rotorInducedVelocityMetersPerSecond(0),
				fallbackOnly.state().rotorInducedVelocityMetersPerSecond(0)
		));

		prepareStateForFlow(enabled, new Vec3(14.0, 0.0, 0.0));
		prepareStateForFlow(fallbackOnly, new Vec3(14.0, 0.0, 0.0));
		enabled.step(powered, 1.0e-6);
		fallbackOnly.step(powered, 1.0e-6);

		double[] enabledTarget = doubleArray(
				enabled,
				"rotorWakeInterferenceTargetIntensity"
		);
		double[] fallbackTarget = doubleArray(
				fallbackOnly,
				"rotorWakeInterferenceTargetIntensity"
		);
		Vec3[] enabledDownwash = (Vec3[]) privateField(
				"rotorWakeInterferenceTargetDownwashVelocityBodyMetersPerSecond"
		).get(enabled);
		Vec3[] fallbackDownwash = (Vec3[]) privateField(
				"rotorWakeInterferenceTargetDownwashVelocityBodyMetersPerSecond"
		).get(fallbackOnly);
		assertTrue(!Arrays.equals(enabledTarget, fallbackTarget)
				|| !Arrays.equals(enabledDownwash, fallbackDownwash));
	}

	@Test
	void invalidCurrentSampleClearsTheRotorOnlyAfterItsPriorReferenceWasAvailable()
			throws ReflectiveOperationException {
		DroneConfig config = DroneConfig.apDrone();
		DronePhysics physics = new DronePhysics(config);
		commitAcceptedReference(physics, config, 0, 6_000.0, 0.4064);
		boolean[] applied = booleanArray(physics, "rotorCtCpJWakeReferenceApplied");
		assertTrue(applied[0]);

		invokeReferenceUpdate(
				physics,
				config.rotors().get(0),
				0,
				new Vec3(0.0, -1.0, 0.0),
				6_000.0
		);

		assertFalse(applied[0]);
		assertEquals(0.0, doubleArray(
				physics,
				"rotorCtCpJWakeReferenceIdealInducedVelocityMetersPerSecond"
		)[0], 0.0);
		assertEquals(0.0, doubleArray(
				physics,
				"rotorCtCpJWakeReferenceFarWakeAxialExcessVelocityMetersPerSecond"
		)[0], 0.0);
		assertEquals(0.0, doubleArray(
				physics,
				"rotorCtCpJWakeReferenceFarWakeEquivalentRadiusMeters"
		)[0], 0.0);
		assertEquals(0.0, doubleArray(
				physics,
				"rotorCtCpJWakeReferenceTangentialVelocityMetersPerSecond"
		)[0], 0.0);
		assertEquals(0.0, doubleArray(
				physics,
				"rotorCtCpJWakeReferenceDiskLoadingStrength"
		)[0], 0.0);
	}

	@Test
	void lifecycleInvalidationKeepsTheFixedReferenceBuffers() throws ReflectiveOperationException {
		DroneConfig config = DroneConfig.apDrone();
		DronePhysics physics = new DronePhysics(config);
		Object[] identities = referenceArrayIdentities(physics);
		commitAcceptedReferenceForEveryRotor(physics, config);
		assertAllApplied(physics);
		double[] filteredIntensity = doubleArray(physics, "rotorWakeInterferenceIntensity");
		Vec3[] filteredDownwash = (Vec3[]) privateField(
				"rotorWakeInterferenceDownwashVelocityBodyMetersPerSecond"
		).get(physics);
		Vec3[] filteredSwirl = (Vec3[]) privateField(
				"rotorWakeInterferenceSwirlVelocityBodyMetersPerSecond"
		).get(physics);
		filteredIntensity[0] = 0.42;
		filteredDownwash[0] = new Vec3(0.0, -2.25, 0.0);
		filteredSwirl[0] = new Vec3(0.35, 0.0, -0.18);

		physics.applyConfig(config.withCenterOfMassOffsetBodyMeters(new Vec3(0.004, -0.002, 0.003)));
		assertReferenceArrayIdentities(physics, identities);
		assertAllCleared(physics);
		assertSame(filteredIntensity, privateField("rotorWakeInterferenceIntensity").get(physics));
		assertSame(
				filteredDownwash,
				privateField("rotorWakeInterferenceDownwashVelocityBodyMetersPerSecond").get(physics)
		);
		assertSame(
				filteredSwirl,
				privateField("rotorWakeInterferenceSwirlVelocityBodyMetersPerSecond").get(physics)
		);
		assertRawValueEquals(0.42, filteredIntensity[0], "filteredIntensity");
		assertEquals(new Vec3(0.0, -2.25, 0.0), filteredDownwash[0]);
		assertEquals(new Vec3(0.35, 0.0, -0.18), filteredSwirl[0]);

		commitAcceptedReferenceForEveryRotor(physics, physics.config());
		physics.resetControlLoops();
		assertReferenceArrayIdentities(physics, identities);
		assertAllCleared(physics);

		commitAcceptedReferenceForEveryRotor(physics, physics.config());
		physics.restoreDirectFlightTelemetry(
				DroneInput.idle(),
				new double[4],
				new double[4],
				new double[4]
		);
		assertReferenceArrayIdentities(physics, identities);
		assertAllCleared(physics);

		commitAcceptedReferenceForEveryRotor(physics, physics.config());
		physics.clearDirectFlightTelemetry(DroneInput.idle());
		assertReferenceArrayIdentities(physics, identities);
		assertAllCleared(physics);

		DronePhysics.RotorDynamicState snapshot = physics.rotorDynamicStateSnapshot();
		commitAcceptedReferenceForEveryRotor(physics, physics.config());
		physics.restoreRotorDynamicState(snapshot);
		assertReferenceArrayIdentities(physics, identities);
		assertAllCleared(physics);

		DronePhysics.AerodynamicTransientState aerodynamicSnapshot =
				physics.aerodynamicTransientStateSnapshot();
		commitAcceptedReferenceForEveryRotor(physics, physics.config());
		physics.restoreAerodynamicTransientState(aerodynamicSnapshot);
		assertReferenceArrayIdentities(physics, identities);
		assertAllCleared(physics);
	}

	@Test
	void sameCountConfigSwitchDisablesAndReenablesTheReferenceWithoutReplacingBuffers()
			throws ReflectiveOperationException {
		DroneConfig apDrone = DroneConfig.apDrone();
		DronePhysics physics = new DronePhysics(apDrone);
		Object[] identities = referenceArrayIdentities(physics);
		commitAcceptedReferenceForEveryRotor(physics, apDrone);
		assertAllApplied(physics);

		physics.applyConfig(DroneConfig.racingQuad());
		assertFalse(privateField("rotorCtCpJWakeReferenceEnabled").getBoolean(physics));
		assertReferenceArrayIdentities(physics, identities);
		assertAllCleared(physics);

		physics.applyConfig(apDrone);
		assertTrue(privateField("rotorCtCpJWakeReferenceEnabled").getBoolean(physics));
		assertReferenceArrayIdentities(physics, identities);
		assertAllCleared(physics);
	}

	private static void commitAcceptedReferenceForEveryRotor(DronePhysics physics, DroneConfig config)
			throws ReflectiveOperationException {
		for (int rotorIndex = 0; rotorIndex < config.rotors().size(); rotorIndex++) {
			commitAcceptedReference(physics, config, rotorIndex, 6_000.0, 0.4064);
		}
	}

	private static void commitAcceptedReference(
			DronePhysics physics,
			DroneConfig config,
			int rotorIndex,
			double rpm,
			double advanceRatioJ
	) throws ReflectiveOperationException {
		RotorSpec rotor = config.rotors().get(rotorIndex);
		double axialSpeed = advanceRatioJ
				* (rpm / 60.0)
				* (rotor.radiusMeters() * 2.0);
		invokeReferenceUpdate(
				physics,
				rotor,
				rotorIndex,
				rotor.thrustAxisBody().multiply(axialSpeed),
				rpm
		);
	}

	private static void invokeReferenceUpdate(
			DronePhysics physics,
			RotorSpec rotor,
			int rotorIndex,
			Vec3 relativeAir,
			double rpm
	) throws ReflectiveOperationException {
		DronePhysics.AtmosphereCache atmosphere = new DronePhysics.AtmosphereCache();
		atmosphere.resolve(DroneEnvironment.calm());
		Method update = privateMethod(
				"updateRotorCtCpJWakeReference",
				int.class,
				RotorSpec.class,
				Vec3.class,
				double.class,
				double.class,
				double.class,
				double.class
		);
		update.invoke(
				physics,
				rotorIndex,
				rotor,
				relativeAir,
				rpm * (2.0 * Math.PI) / 60.0,
				ApDroneCtCpJRuntimeWakeReference.runtimeAirDensityKgPerCubicMeter(1.0),
				atmosphere.speedOfSoundMetersPerSecond(),
				ApDroneCtCpJRuntimeWakeReference.runtimeDensityViscosityRatio(
						ApDroneCtCpJRuntimeWakeReference.runtimeAirDensityKgPerCubicMeter(1.0),
						atmosphere.dynamicViscosityRatio()
				)
		);
	}

	private static void assertAllApplied(DronePhysics physics) throws ReflectiveOperationException {
		boolean[] applied = booleanArray(physics, "rotorCtCpJWakeReferenceApplied");
		for (int index = 0; index < applied.length; index++) {
			assertTrue(
					applied[index],
					() -> "reference=" + Arrays.toString(applied)
							+ " omega=" + Arrays.toString(physics.state().motorOmegaRadiansPerSecond())
			);
		}
	}

	private static void prepareStateForFlow(DronePhysics physics, Vec3 velocityMetersPerSecond) {
		physics.state().setPositionMeters(new Vec3(0.0, 20.0, 0.0));
		physics.state().setVelocityMetersPerSecond(velocityMetersPerSecond);
		physics.state().setOrientation(Quaternion.IDENTITY);
		physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
	}

	private static void primePoweredControl(DronePhysics physics, DroneInput input)
			throws ReflectiveOperationException {
		privateField("lastLinkedControlInput").set(physics, input);
		privateField("smoothedControlInput").set(physics, input);
		privateField("receiverFrameInput").set(physics, input);
		DroneInput[] delayBuffer = (DroneInput[]) privateField("controlDelayBuffer").get(physics);
		Arrays.fill(delayBuffer, input);
	}

	private static void assertWakeTargetsEqual(DronePhysics left, DronePhysics right)
			throws ReflectiveOperationException {
		assertArrayEquals(
				doubleArray(left, "rotorWakeInterferenceTargetIntensity"),
				doubleArray(right, "rotorWakeInterferenceTargetIntensity"),
				0.0
		);
		assertArrayEquals(
				(Vec3[]) privateField(
						"rotorWakeInterferenceTargetDownwashVelocityBodyMetersPerSecond"
				).get(left),
				(Vec3[]) privateField(
						"rotorWakeInterferenceTargetDownwashVelocityBodyMetersPerSecond"
				).get(right)
		);
		assertArrayEquals(
				(Vec3[]) privateField(
						"rotorWakeInterferenceTargetSwirlVelocityBodyMetersPerSecond"
				).get(left),
				(Vec3[]) privateField(
						"rotorWakeInterferenceTargetSwirlVelocityBodyMetersPerSecond"
				).get(right)
		);
	}

	private static void assertDroneStatesRawEqual(DroneState left, DroneState right)
			throws IllegalAccessException {
		for (Field field : DroneState.class.getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers())) {
				continue;
			}
			field.setAccessible(true);
			assertRawValueEquals(field.get(left), field.get(right), field.getName());
		}
	}

	private static void assertRawValueEquals(Object left, Object right, String path) {
		if (left instanceof Double leftDouble && right instanceof Double rightDouble) {
			assertEquals(
					Double.doubleToRawLongBits(leftDouble),
					Double.doubleToRawLongBits(rightDouble),
					path
			);
			return;
		}
		if (left != null && right != null && left.getClass().isArray() && right.getClass().isArray()) {
			int length = Array.getLength(left);
			assertEquals(length, Array.getLength(right), path + ".length");
			for (int index = 0; index < length; index++) {
				assertRawValueEquals(Array.get(left, index), Array.get(right, index), path + "[" + index + "]");
			}
			return;
		}
		assertEquals(left, right, path);
	}

	private static double max(double[] values) {
		double max = Double.NEGATIVE_INFINITY;
		for (double value : values) {
			max = Math.max(max, value);
		}
		return max;
	}

	private static boolean rawEqual(double left, double right) {
		return Double.doubleToRawLongBits(left) == Double.doubleToRawLongBits(right);
	}

	private static void assertAllCleared(DronePhysics physics) throws ReflectiveOperationException {
		boolean[] applied = booleanArray(physics, "rotorCtCpJWakeReferenceApplied");
		assertArrayEquals(new boolean[applied.length], applied);
		for (int index = 1; index < REFERENCE_ARRAY_FIELDS.length; index++) {
			double[] values = doubleArray(physics, REFERENCE_ARRAY_FIELDS[index]);
			assertArrayEquals(new double[values.length], values, 0.0);
		}
	}

	private static Object[] referenceArrayIdentities(DronePhysics physics)
			throws ReflectiveOperationException {
		Object[] identities = new Object[REFERENCE_ARRAY_FIELDS.length];
		for (int index = 0; index < REFERENCE_ARRAY_FIELDS.length; index++) {
			identities[index] = privateField(REFERENCE_ARRAY_FIELDS[index]).get(physics);
		}
		return identities;
	}

	private static void assertReferenceArrayIdentities(DronePhysics physics, Object[] identities)
			throws ReflectiveOperationException {
		for (int index = 0; index < REFERENCE_ARRAY_FIELDS.length; index++) {
			assertSame(identities[index], privateField(REFERENCE_ARRAY_FIELDS[index]).get(physics));
		}
	}

	private static boolean[] booleanArray(DronePhysics physics, String name)
			throws ReflectiveOperationException {
		return (boolean[]) privateField(name).get(physics);
	}

	private static double[] doubleArray(DronePhysics physics, String name)
			throws ReflectiveOperationException {
		return (double[]) privateField(name).get(physics);
	}

	private static Field privateField(String name) throws NoSuchFieldException {
		Field field = DronePhysics.class.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}

	private static Method privateMethod(String name, Class<?>... parameterTypes)
			throws NoSuchMethodException {
		Method method = DronePhysics.class.getDeclaredMethod(name, parameterTypes);
		method.setAccessible(true);
		return method;
	}
}
