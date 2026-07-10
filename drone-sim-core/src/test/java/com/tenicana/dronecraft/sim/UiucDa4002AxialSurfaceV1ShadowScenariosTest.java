package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.UiucDa4002AxialSurfaceV1RuntimeShadowAdapter.ShadowStatus;
import com.tenicana.dronecraft.sim.UiucDa4002AxialSurfaceV1ShadowScenarios.ScenarioKind;
import com.tenicana.dronecraft.sim.UiucDa4002AxialSurfaceV1ShadowScenarios.ScenarioResult;
import com.tenicana.dronecraft.sim.UiucDa4002MeasuredRotorModel.Propeller;

class UiucDa4002AxialSurfaceV1ShadowScenariosTest {
	@Test
	void coversTheFixedScenarioSetWithFiniteClosedReferenceLoads() {
		List<ScenarioResult> results = UiucDa4002AxialSurfaceV1ShadowScenarios.runAll();

		assertEquals(16, results.size());
		for (Propeller propeller : Propeller.values()) {
			EnumSet<ScenarioKind> kinds = EnumSet.noneOf(ScenarioKind.class);
			results.stream()
					.filter(result -> result.definition().propeller() == propeller)
					.forEach(result -> kinds.add(result.definition().kind()));
			assertEquals(EnumSet.allOf(ScenarioKind.class), kinds);
		}
		assertEquals(64, results.stream()
				.mapToInt(result -> result.shadow().rotorSamples().size()).sum());
		assertEquals(56, results.stream()
				.mapToInt(result -> result.shadow().comparableScalarRotorCount()).sum());
		assertEquals(40, results.stream()
				.mapToInt(result -> result.shadow().comparableVectorRotorCount()).sum());
		assertEquals(8, results.stream()
				.mapToInt(result -> result.shadow().blockedRotorCount()).sum());

		for (ScenarioResult result : results) {
			assertEquals(result.definition().initialRpm(), result.actualMeanRpm(), 0.01);
			assertEquals(result.definition().targetAdvanceRatioJ(),
					result.actualMeanAdvanceRatioJ(), 1.0e-5);
			assertFalse(result.shadow().runtimeForceApplied());
			assertFinite(result.shadow().actualTotalThrustNewtons());
			assertFinite(result.shadow().referenceTotalThrustNewtons());
			assertFinite(result.shadow().actualTotalShaftPowerWatts());
			assertFinite(result.shadow().referenceTotalShaftPowerWatts());
			assertFinite(result.shadow().actualTotalShaftTorqueNewtonMeters());
			assertFinite(result.shadow().referenceTotalShaftTorqueNewtonMeters());
			for (var rotor : result.shadow().rotorSamples()) {
				assertTrue(rotor.geometryMatch().matched());
				assertFalse(rotor.runtimeForceApplied());
				rotor.reference().flatMap(sample -> sample.dimensionalSample())
						.ifPresent(sample -> assertEquals(
								sample.shaftPowerWatts(),
								sample.shaftTorqueNewtonMeters()
										* rotor.rpm() * 2.0 * Math.PI / 60.0,
								1.0e-10
						));
			}
		}
	}

	@Test
	void referenceFallsWithAdvanceRatioCrossesZeroAndBlocksOutside() {
		List<ScenarioResult> results = UiucDa4002AxialSurfaceV1ShadowScenarios.runAll();
		for (String prefix : List.of("five", "nine")) {
			double hover = referenceThrust(results, prefix + "_hover");
			double mid = referenceThrust(results, prefix + "_mid_j");
			double high = referenceThrust(results, prefix + "_high_j");
			assertTrue(hover > mid);
			assertTrue(mid > high);
			assertTrue(high > 0.0);

			ScenarioResult below = result(results, prefix + "_zero_below");
			ScenarioResult above = result(results, prefix + "_zero_above");
			ScenarioResult outside = result(results, prefix + "_outside");
			assertTrue(below.shadow().referenceTotalThrustNewtons() > 0.0);
			assertTrue(above.shadow().referenceTotalThrustNewtons() < 0.0);
			assertTrue(above.shadow().rotorSamples().stream().allMatch(rotor ->
					rotor.status() == ShadowStatus.NON_POSITIVE_THRUST_REFERENCE
							&& rotor.scalarReferenceComparable()
							&& !rotor.vectorReferenceComparable()));
			assertEquals(4, outside.shadow().blockedRotorCount());
			assertTrue(outside.shadow().rotorSamples().stream().allMatch(rotor ->
					rotor.status() == ShadowStatus.BLOCKED_COEFFICIENT_SURFACE
							&& !rotor.scalarReferenceComparable()));
		}
	}

	@Test
	void tiltedAndObliqueScenariosRetainAxisAndSpinSemantics() {
		List<ScenarioResult> results = UiucDa4002AxialSurfaceV1ShadowScenarios.runAll();
		for (String prefix : List.of("five", "nine")) {
			ScenarioResult tilted = result(results, prefix + "_tilted_axis");
			assertEquals(4, tilted.shadow().comparableVectorRotorCount());
			for (var rotor : tilted.shadow().rotorSamples()) {
				Vec3 axis = rotor.rotor().thrustAxisBody();
				Vec3 force = rotor.referenceForceBodyNewtons();
				assertTrue(force.dot(axis) > 0.0);
				assertEquals(0.0, force.cross(axis).length(), 1.0e-12);
				double reactionAboutAxis = rotor.reference().orElseThrow()
						.referenceReactionTorqueBodyNewtonMeters().dot(axis);
				assertEquals(rotor.rotor().spinDirection(),
						(int) Math.signum(reactionAboutAxis));
			}

			ScenarioResult oblique = result(results, prefix + "_oblique");
			assertEquals(4, oblique.shadow().comparableScalarRotorCount());
			assertEquals(0, oblique.shadow().comparableVectorRotorCount());
			assertTrue(oblique.shadow().rotorSamples().stream().allMatch(rotor ->
					rotor.status() == ShadowStatus.OBLIQUE_AXIAL_PROJECTION_REFERENCE));
		}
	}

	private static ScenarioResult result(List<ScenarioResult> results, String id) {
		return results.stream()
				.filter(result -> result.definition().id().equals(id))
				.findFirst()
				.orElseThrow();
	}

	private static double referenceThrust(List<ScenarioResult> results, String id) {
		return result(results, id).shadow().referenceTotalThrustNewtons();
	}

	private static void assertFinite(double value) {
		assertTrue(Double.isFinite(value));
	}
}
