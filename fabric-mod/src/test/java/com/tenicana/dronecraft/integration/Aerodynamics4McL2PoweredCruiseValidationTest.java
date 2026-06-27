package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class Aerodynamics4McL2PoweredCruiseValidationTest {
	@Test
	void auditBuildsStablePoweredCruiseValidationTargets() {
		Aerodynamics4McL2PoweredCruiseValidation.PoweredCruiseValidationAudit audit =
				Aerodynamics4McL2PoweredCruiseValidation.audit();

		assertEquals("A4MC-L2-Powered-Cruise-Validation-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("forward-flight"));
		assertEquals(121, audit.packetMetricRowCount());
		assertEquals(5, audit.sourceReferenceCount());
		assertEquals(4, audit.presetSampleCount());
		assertEquals(26, audit.targetMetricCount());
		assertEquals(11, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(4, audit.targets().size());

		for (Aerodynamics4McL2PoweredCruiseValidation.PoweredCruiseValidationTarget target : audit.targets()) {
			assertTrue(target.staticBaselineRequired());
			assertTrue(target.poweredRunRequired());
			assertTrue(target.staticBaselineSubtractRequired());
			assertTrue(target.poweredSourceApiRequired());
			assertTrue(target.edgewiseValidationRequired());
			assertEquals(0.0, target.targetForceXNewtons(), 1.0e-12);
			assertEquals(0.0, target.targetForceZNewtons(), 1.0e-12);
			assertEquals(target.targetForceYNewtons(), target.targetForceMagnitudeNewtons(), 1.0e-12);
			assertEquals(0.08, target.forceRelativeTolerance(), 1.0e-12);
			assertTrue(target.forceToleranceNewtons() >= 0.20);
			assertEquals(0.05, target.momentToleranceNewtonMeters(), 1.0e-12);
			assertEquals(0.05, target.centerOfForceToleranceMeters(), 1.0e-12);
			assertEquals(0.0, target.targetMomentMagnitudeNewtonMeters(), 1.0e-12);
			assertTrue(target.targetMeanPressureJumpPascals() > 0.0);
			assertTrue(target.targetEdgewiseAdvanceRatio() > 0.0);
			assertEquals(2.0 * target.targetIdealInducedVelocityMetersPerSecond(),
					target.targetFarWakeVelocityMetersPerSecond(), 1.0e-12);
			assertTrue(target.targetTipSpeedMetersPerSecond() > 0.0);
			assertTrue(target.targetRepresentativeBladeReynoldsNumber() > 0.0);
			assertTrue(target.targetInletSpeedMetersPerSecond() > 0.0);
			assertEquals(0.65, target.targetSpinRatio(), 1.0e-12);
			assertEquals("plan-only-powered-source-api-unavailable", target.runtimeInfo());
		}
		assertEquals(4, audit.extrema().targetCount());
		assertEquals(4, audit.extrema().poweredSourceApiRequiredCount());
		assertEquals(4, audit.extrema().staticBaselineSubtractRequiredCount());
		assertEquals(4, audit.extrema().edgewiseValidationRequiredCount());
		assertEquals(audit.targets().stream()
						.mapToDouble(Aerodynamics4McL2PoweredCruiseValidation.PoweredCruiseValidationTarget::targetForceMagnitudeNewtons)
						.max().orElseThrow(),
				audit.extrema().maxTargetForceMagnitudeNewtons(), 1.0e-12);
		assertEquals(audit.targets().stream()
						.mapToDouble(Aerodynamics4McL2PoweredCruiseValidation.PoweredCruiseValidationTarget::targetEdgewiseAdvanceRatio)
						.max().orElseThrow(),
				audit.extrema().maxTargetEdgewiseAdvanceRatio(), 1.0e-12);
		assertEquals(audit.targets().stream()
						.mapToDouble(Aerodynamics4McL2PoweredCruiseValidation.PoweredCruiseValidationTarget::targetRepresentativeBladeReynoldsNumber)
						.max().orElseThrow(),
				audit.extrema().maxTargetRepresentativeBladeReynoldsNumber(), 1.0e-12);
	}

	@Test
	void perfectPoweredRunPassesAfterSubtractingForwardFlightBaseline() {
		Aerodynamics4McL2PoweredCruiseValidation.PoweredCruiseValidationTarget target =
				Aerodynamics4McL2PoweredCruiseValidation.audit().targets().get(0);
		Aerodynamics4McL2Bridge.L2ForceMomentSample baseline = sample(-2.50, 0.40, -7.00, 0.12, -0.09, 0.35);
		Aerodynamics4McL2Bridge.L2ForceMomentSample powered = sample(
				-2.50 + target.targetForceXNewtons(),
				0.40 + target.targetForceYNewtons(),
				-7.00 + target.targetForceZNewtons(),
				0.12 + target.targetMomentXNewtonMeters(),
				-0.09 + target.targetMomentYNewtonMeters(),
				0.35 + target.targetMomentZNewtonMeters()
		);

		Aerodynamics4McL2PoweredCruiseValidation.PoweredCruiseValidationResult result =
				Aerodynamics4McL2PoweredCruiseValidation.evaluate(target, baseline, powered);

		assertTrue(result.passed());
		assertTrue(result.forceMatched());
		assertTrue(result.momentMatched());
		assertTrue(result.centerOfForceMatched());
		assertEquals(target.targetForceYNewtons(), result.observedForceDeltaYNewtons(), 1.0e-12);
		assertEquals(0.0, result.forceErrorNewtons(), 1.0e-12);
		assertEquals(0.0, result.momentErrorNewtonMeters(), 1.0e-12);
		assertEquals(0.0, result.centerOfForceErrorMeters(), 1.0e-12);
	}

	@Test
	void forceDeficitFailsValidation() {
		Aerodynamics4McL2PoweredCruiseValidation.PoweredCruiseValidationTarget target =
				Aerodynamics4McL2PoweredCruiseValidation.audit().targets().get(3);
		Aerodynamics4McL2Bridge.L2ForceMomentSample baseline = sample(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
		Aerodynamics4McL2Bridge.L2ForceMomentSample underPowered =
				sample(0.0, target.targetForceYNewtons() * 0.50, 0.0, 0.0, 0.0, 0.0);

		Aerodynamics4McL2PoweredCruiseValidation.PoweredCruiseValidationResult result =
				Aerodynamics4McL2PoweredCruiseValidation.evaluate(target, baseline, underPowered);

		assertFalse(result.passed());
		assertFalse(result.forceMatched());
		assertTrue(result.momentMatched());
		assertTrue(result.centerOfForceMatched());
		assertTrue(result.forceErrorNewtons() > target.forceToleranceNewtons());
	}

	@Test
	void offCenterMomentFailsCruiseValidation() {
		Aerodynamics4McL2PoweredCruiseValidation.PoweredCruiseValidationTarget target =
				Aerodynamics4McL2PoweredCruiseValidation.audit().targets().get(1);
		Aerodynamics4McL2Bridge.L2ForceMomentSample baseline = sample(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
		Aerodynamics4McL2Bridge.L2ForceMomentSample offCenter =
				sample(0.0, target.targetForceYNewtons(), 0.0, 0.0, 0.0, 2.0);

		Aerodynamics4McL2PoweredCruiseValidation.PoweredCruiseValidationResult result =
				Aerodynamics4McL2PoweredCruiseValidation.evaluate(target, baseline, offCenter);

		assertFalse(result.passed());
		assertTrue(result.forceMatched());
		assertFalse(result.momentMatched());
		assertFalse(result.centerOfForceMatched());
		assertTrue(result.centerOfForceErrorMeters() > target.centerOfForceToleranceMeters());
	}

	@Test
	void validationRequiresTargetAndSamples() {
		Aerodynamics4McL2PoweredCruiseValidation.PoweredCruiseValidationTarget target =
				Aerodynamics4McL2PoweredCruiseValidation.audit().targets().get(0);
		Aerodynamics4McL2Bridge.L2ForceMomentSample sample = sample(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseValidation.target(null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseValidation.evaluate(
						(Aerodynamics4McL2PoweredCruiseValidation.PoweredCruiseValidationTarget) null,
						sample,
						sample));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseValidation.evaluate(target, null, sample));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseValidation.evaluate(target, sample, null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredCruiseValidation.PoweredCruiseValidationAudit audit =
				Aerodynamics4McL2PoweredCruiseValidation.audit();
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_powered_cruise_validation_packet.csv");

		assertEquals(audit.packetMetricRowCount() + 1, Files.readAllLines(packet).size());
		assertTrue(Files.readAllLines(packet).stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_validation_summary,all_presets,target_count,4,")));
		assertTrue(Files.readAllLines(packet).stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_validation_target,racingQuad,target_edgewise_advance_ratio,")));
	}

	private static Aerodynamics4McL2Bridge.L2ForceMomentSample sample(
			double forceX,
			double forceY,
			double forceZ,
			double momentX,
			double momentY,
			double momentZ
	) {
		return new Aerodynamics4McL2Bridge.L2ForceMomentSample(
				forceX,
				forceY,
				forceZ,
				momentX,
				momentY,
				momentZ,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0
		);
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve("docs/data/a4mc_l2_powered_cruise_validation_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
