package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class Aerodynamics4McL2PoweredHoverValidationTest {
	@Test
	void auditBuildsStablePoweredHoverValidationTargets() {
		Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationAudit audit =
				Aerodynamics4McL2PoweredHoverValidation.audit();

		assertEquals("A4MC-L2-Powered-Hover-Validation-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("static-airframe baseline"));
		assertEquals(86, audit.packetMetricRowCount());
		assertEquals(5, audit.sourceReferenceCount());
		assertEquals(4, audit.presetSampleCount());
		assertEquals(18, audit.targetMetricCount());
		assertEquals(8, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(4, audit.targets().size());

		for (Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationTarget target : audit.targets()) {
			assertTrue(target.staticBaselineRequired());
			assertTrue(target.poweredRunRequired());
			assertTrue(target.staticBaselineSubtractRequired());
			assertTrue(target.poweredSourceApiRequired());
			assertEquals(0.0, target.targetForceXNewtons(), 1.0e-12);
			assertEquals(0.0, target.targetForceZNewtons(), 1.0e-12);
			assertEquals(target.targetForceYNewtons(), target.targetForceMagnitudeNewtons(), 1.0e-12);
			assertEquals(0.08, target.forceRelativeTolerance(), 1.0e-12);
			assertTrue(target.forceToleranceNewtons() >= 0.20);
			assertEquals(0.05, target.momentToleranceNewtonMeters(), 1.0e-12);
			assertEquals(0.05, target.centerOfForceToleranceMeters(), 1.0e-12);
			assertEquals(0.0, target.targetMomentMagnitudeNewtonMeters(), 1.0e-12);
			assertEquals(0.0, target.targetCenterOfThrustOffsetMeters(), 1.0e-12);
			assertTrue(target.targetMeanPressureJumpPascals() > 0.0);
		}
		assertEquals(4, audit.extrema().targetCount());
		assertEquals(4, audit.extrema().poweredSourceApiRequiredCount());
		assertEquals(4, audit.extrema().staticBaselineSubtractRequiredCount());
		assertEquals(audit.targets().stream()
						.mapToDouble(Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationTarget::targetForceMagnitudeNewtons)
						.max().orElseThrow(),
				audit.extrema().maxTargetForceMagnitudeNewtons(), 1.0e-12);
		assertEquals(audit.targets().stream()
						.mapToDouble(Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationTarget::forceToleranceNewtons)
						.max().orElseThrow(),
				audit.extrema().maxForceToleranceNewtons(), 1.0e-12);
	}

	@Test
	void perfectPoweredRunPassesAfterSubtractingStaticBaseline() {
		Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationTarget target =
				Aerodynamics4McL2PoweredHoverValidation.audit().targets().get(0);
		Aerodynamics4McL2Bridge.L2ForceMomentSample baseline = sample(1.25, -0.50, 3.00, 0.20, -0.10, 0.40);
		Aerodynamics4McL2Bridge.L2ForceMomentSample powered = sample(
				1.25 + target.targetForceXNewtons(),
				-0.50 + target.targetForceYNewtons(),
				3.00 + target.targetForceZNewtons(),
				0.20 + target.targetMomentXNewtonMeters(),
				-0.10 + target.targetMomentYNewtonMeters(),
				0.40 + target.targetMomentZNewtonMeters()
		);

		Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationResult result =
				Aerodynamics4McL2PoweredHoverValidation.evaluate(target, baseline, powered);

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
		Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationTarget target =
				Aerodynamics4McL2PoweredHoverValidation.audit().targets().get(3);
		Aerodynamics4McL2Bridge.L2ForceMomentSample baseline = sample(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
		Aerodynamics4McL2Bridge.L2ForceMomentSample underPowered =
				sample(0.0, target.targetForceYNewtons() * 0.50, 0.0, 0.0, 0.0, 0.0);

		Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationResult result =
				Aerodynamics4McL2PoweredHoverValidation.evaluate(target, baseline, underPowered);

		assertFalse(result.passed());
		assertFalse(result.forceMatched());
		assertTrue(result.momentMatched());
		assertTrue(result.centerOfForceMatched());
		assertTrue(result.forceErrorNewtons() > target.forceToleranceNewtons());
	}

	@Test
	void validationRequiresTargetAndSamples() {
		Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationTarget target =
				Aerodynamics4McL2PoweredHoverValidation.audit().targets().get(0);
		Aerodynamics4McL2Bridge.L2ForceMomentSample sample = sample(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverValidation.target(null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverValidation.evaluate(
						(Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationTarget) null,
						sample,
						sample));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverValidation.evaluate(target, null, sample));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverValidation.evaluate(target, sample, null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationAudit audit =
				Aerodynamics4McL2PoweredHoverValidation.audit();
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_powered_hover_validation_packet.csv");

		assertEquals(audit.packetMetricRowCount() + 1, Files.readAllLines(packet).size());
		assertTrue(Files.readAllLines(packet).stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_validation_summary,all_presets,target_count,4,")));
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
					&& Files.isRegularFile(path.resolve("docs/data/a4mc_l2_powered_hover_validation_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
