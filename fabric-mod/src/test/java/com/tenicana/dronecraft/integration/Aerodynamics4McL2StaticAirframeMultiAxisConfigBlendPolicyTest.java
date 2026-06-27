package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.DroneConfig;

class Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicyTest {
	@Test
	void auditBuildsStableConfigBlendPolicyScenarios() {
		Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendAudit audit =
				Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.audit();

		assertEquals("A4MC-L2-Static-Airframe-Multi-Axis-Config-Blend-Policy-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("audit-only"));
		assertEquals(515, audit.packetMetricRowCount());
		assertEquals(5, audit.sourceReferenceCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(4, audit.presetSampleCount());
		assertEquals(31, audit.blendMetricCount());
		assertEquals(13, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(4, audit.scenarios().size());

		Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendScenario unavailable =
				find(audit.scenarios(), "current_runtime_unavailable");
		assertEquals(4, unavailable.candidates().size());
		assertTrue(unavailable.candidates().stream()
				.noneMatch(Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendCandidate::multiAxisCandidateReady));
		assertTrue(unavailable.candidates().stream().allMatch(candidate -> candidate.bodyDragBlendFraction() == 0.0));
		assertTrue(unavailable.candidates().stream().allMatch(candidate -> candidate.pressureCenterBlendFraction() == 0.0));

		Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendScenario testRuntime =
				find(audit.scenarios(), "test_runtime_fit_ready_blocked");
		assertTrue(testRuntime.candidates().stream()
				.noneMatch(Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendCandidate::multiAxisCandidateReady));
		assertTrue(testRuntime.candidates().stream().allMatch(candidate -> candidate.bodyDragBlendFraction() == 0.0));

		Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendScenario live =
				find(audit.scenarios(), "live_runtime_full_multi_axis_candidate");
		assertTrue(live.candidates().stream()
				.allMatch(Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendCandidate::multiAxisCandidateReady));
		assertTrue(live.candidates().stream()
				.allMatch(Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendCandidate::pressureCenterVectorResolved));
		assertTrue(live.candidates().stream()
				.allMatch(Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendCandidate::angularDragReviewRequired));
		assertTrue(live.candidates().stream()
				.noneMatch(Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendCandidate::blendPolicyReviewed));
		assertTrue(live.candidates().stream()
				.noneMatch(Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendCandidate::runtimeConfigAutoApplyAllowed));
		assertTrue(live.candidates().stream().allMatch(candidate ->
				candidate.bodyDragBlendFraction()
						== Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MAX_BODY_DRAG_BLEND_FRACTION));
		assertTrue(live.candidates().stream().allMatch(candidate ->
				candidate.pressureCenterBlendFraction()
						== Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MAX_PRESSURE_CENTER_BLEND_FRACTION));

		Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendScenario signBroken =
				find(audit.scenarios(), "live_runtime_forward_reverse_sign_broken");
		assertTrue(signBroken.candidates().stream()
				.noneMatch(Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendCandidate::multiAxisCandidateReady));
		assertTrue(signBroken.candidates().stream().allMatch(candidate -> candidate.bodyDragBlendFraction() == 0.0));

		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(16, audit.extrema().blendCandidateCount());
		assertEquals(4, audit.extrema().suggestedBodyDragCandidateCount());
		assertEquals(4, audit.extrema().suggestedPressureCenterCandidateCount());
		assertEquals(0, audit.extrema().runtimeConfigAutoApplyAllowedCount());
		assertEquals(4, audit.extrema().angularDragReviewRequiredCount());
	}

	@Test
	void blendCandidateCapsBodyDragAndPressureCenterMoves() {
		Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendCandidate candidate =
				findCandidate(find(
						Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.audit().scenarios(),
						"live_runtime_full_multi_axis_candidate").candidates(), "racingQuad");
		DroneConfig config = DroneConfig.racingQuad();
		double bodyFraction =
				Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MAX_BODY_DRAG_BLEND_FRACTION;
		double pressureCenterFraction =
				Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MAX_PRESSURE_CENTER_BLEND_FRACTION;

		assertEquals(config.bodyDragCoefficients().x(), candidate.currentBodyDragXCoefficient(), 1.0e-15);
		assertEquals(config.bodyDragCoefficients().y(), candidate.currentBodyDragYCoefficient(), 1.0e-15);
		assertEquals(config.bodyDragCoefficients().z(), candidate.currentBodyDragZCoefficient(), 1.0e-15);
		assertEquals(blend(candidate.currentBodyDragXCoefficient(), candidate.targetBodyDragXCoefficient(), bodyFraction),
				candidate.suggestedBodyDragXCoefficient(), 1.0e-15);
		assertEquals(blend(candidate.currentBodyDragYCoefficient(), candidate.targetBodyDragYCoefficient(), bodyFraction),
				candidate.suggestedBodyDragYCoefficient(), 1.0e-15);
		assertEquals(blend(candidate.currentBodyDragZCoefficient(), candidate.targetBodyDragZCoefficient(), bodyFraction),
				candidate.suggestedBodyDragZCoefficient(), 1.0e-15);
		double expectedPressureCenterX = blend(candidate.currentPressureCenterOffsetXBodyMeters(),
				candidate.targetPressureCenterOffsetXBodyMeters(), pressureCenterFraction);
		double expectedPressureCenterY = blend(candidate.currentPressureCenterOffsetYBodyMeters(),
				candidate.targetPressureCenterOffsetYBodyMeters(), pressureCenterFraction);
		double expectedPressureCenterZ = blend(candidate.currentPressureCenterOffsetZBodyMeters(),
				candidate.targetPressureCenterOffsetZBodyMeters(), pressureCenterFraction);
		assertEquals(expectedPressureCenterX, candidate.suggestedPressureCenterOffsetXBodyMeters(), 1.0e-15);
		assertEquals(expectedPressureCenterY, candidate.suggestedPressureCenterOffsetYBodyMeters(), 1.0e-15);
		assertEquals(expectedPressureCenterZ, candidate.suggestedPressureCenterOffsetZBodyMeters(), 1.0e-15);
		assertEquals(Math.sqrt(
				expectedPressureCenterX * expectedPressureCenterX
						+ expectedPressureCenterY * expectedPressureCenterY
						+ expectedPressureCenterZ * expectedPressureCenterZ
		), candidate.maxPressureCenterDeltaMeters(), 1.0e-15);
		assertEquals(pressureCenterFraction, candidate.pressureCenterBlendFraction(), 1.0e-15);
		assertFalse(candidate.runtimeConfigAutoApplyAllowed());
	}

	@Test
	void scenarioRejectsInvalidInputs() {
		List<Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.MultiAxisGameplayFitCandidate> candidates =
				Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.audit().scenarios().get(0).candidates();

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.scenario("", candidates));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.scenario("null", null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.candidate(null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendAudit audit =
				Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_static_airframe_multi_axis_config_blend_policy_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_static_airframe_multi_axis_config_blend_policy_summary,all_scenarios,suggested_body_drag_candidate_count,4,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_static_airframe_multi_axis_config_blend_policy_summary,all_scenarios,runtime_config_auto_apply_allowed_count,0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_static_airframe_multi_axis_config_blend_policy_summary,all_scenarios,angular_drag_review_required_count,4,")));
	}

	private static Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendScenario find(
			List<Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendCandidate findCandidate(
			List<Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendCandidate> candidates,
			String presetName
	) {
		return candidates.stream()
				.filter(candidate -> presetName.equals(candidate.presetName()))
				.findFirst()
				.orElseThrow();
	}

	private static double blend(double current, double target, double fraction) {
		return current + (target - current) * fraction;
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_static_airframe_multi_axis_config_blend_policy_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
