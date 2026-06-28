package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class Aerodynamics4McL2PoweredSourceAcceptanceHandoffTest {
	@Test
	void auditBuildsBlockedHandoffsFromSkippedValidationRuns() {
		Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffAudit audit =
				Aerodynamics4McL2PoweredSourceAcceptanceHandoff.audit(getClass().getClassLoader());

		assertEquals("A4MC-L2-Powered-Source-Acceptance-Handoff-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("current rows stay blocked"));
		assertEquals(56, audit.packetMetricRowCount());
		assertEquals(7, audit.sourceReferenceCount());
		assertEquals(2, audit.handoffSampleCount());
		assertEquals(20, audit.handoffMetricCount());
		assertEquals(8, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(2, audit.handoffs().size());

		Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary hover =
				find(audit.handoffs(), "hover");
		Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary cruise =
				find(audit.handoffs(), "cruise");
		for (Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary handoff :
				List.of(hover, cruise)) {
			assertEquals(4, handoff.expectedPresetCount());
			assertEquals(4, handoff.observedValidationRunCount());
			assertEquals(0, handoff.validationSeedReadyCount());
			assertEquals(0, handoff.validationInvokedCount());
			assertEquals(0, handoff.validationPassedCount());
			assertEquals(0, handoff.acceptanceCandidateCount());
			assertEquals(4, handoff.skippedValidationRunCount());
			assertEquals(0, handoff.failedValidationRunCount());
			assertEquals(0, handoff.missingPresetCount());
			assertEquals(0, handoff.unexpectedPresetCount());
			assertEquals(0.0, handoff.maxForceErrorRatio(), 1.0e-12);
			assertEquals(0.0, handoff.maxCenterOfForceErrorMeters(), 1.0e-12);
			assertTrue(handoff.allExpectedRunsPresent());
			assertFalse(handoff.allCandidatesPassed());
			assertFalse(handoff.acceptanceHandoffReady());
			assertEquals("BLOCKED", handoff.status());
			assertEquals("acceptance-candidates-incomplete", handoff.message());
		}
		assertEquals(Aerodynamics4McL2PoweredHoverValidation.SOURCE_ID, hover.validationPacketId());
		assertEquals(Aerodynamics4McL2PoweredHoverAcceptanceGate.SOURCE_ID, hover.acceptanceGatePacketId());
		assertEquals(Aerodynamics4McL2PoweredCruiseValidation.SOURCE_ID, cruise.validationPacketId());
		assertEquals(Aerodynamics4McL2PoweredCruiseAcceptanceGate.SOURCE_ID, cruise.acceptanceGatePacketId());

		assertEquals(2, audit.extrema().handoffCount());
		assertEquals(0, audit.extrema().readyHandoffCount());
		assertEquals(2, audit.extrema().blockedHandoffCount());
		assertEquals(4, audit.extrema().maxExpectedPresetCount());
		assertEquals(0, audit.extrema().maxMissingPresetCount());
		assertEquals(0, audit.extrema().maxAcceptanceCandidateCount());
	}

	@Test
	void handoffAllowsOnlyCompletePassingCandidates() {
		List<Aerodynamics4McL2PoweredSourceValidationRunMatrix.PoweredSourceValidationRunSummary> runs =
				List.of(
						run("racingQuad", "hover", true),
						run("apDrone", "hover", true),
						run("cinewhoop", "hover", true),
						run("heavyLift", "hover", true),
						run("racingQuad", "cruise", true)
				);

		Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary hover =
				Aerodynamics4McL2PoweredSourceAcceptanceHandoff.handoff("hover", runs);
		Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary cruise =
				Aerodynamics4McL2PoweredSourceAcceptanceHandoff.handoff("cruise", runs);

		assertTrue(hover.allExpectedRunsPresent());
		assertTrue(hover.allCandidatesPassed());
		assertTrue(hover.acceptanceHandoffReady());
		assertEquals("READY", hover.status());
		assertEquals("acceptance-handoff-ready", hover.message());

		assertFalse(cruise.allExpectedRunsPresent());
		assertEquals(3, cruise.missingPresetCount());
		assertFalse(cruise.acceptanceHandoffReady());
	}

	@Test
	void handoffBlocksFailedMissingUnexpectedAndDuplicateRuns() {
		List<Aerodynamics4McL2PoweredSourceValidationRunMatrix.PoweredSourceValidationRunSummary> failed =
				new ArrayList<>(List.of(
						run("racingQuad", "hover", true),
						run("apDrone", "hover", true),
						run("cinewhoop", "hover", false),
						run("heavyLift", "hover", true)
				));
		Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary failedSummary =
				Aerodynamics4McL2PoweredSourceAcceptanceHandoff.handoff("hover", failed);
		assertFalse(failedSummary.acceptanceHandoffReady());
		assertEquals(1, failedSummary.failedValidationRunCount());
		assertEquals(3, failedSummary.acceptanceCandidateCount());
		assertTrue(failedSummary.maxForceErrorRatio() > 0.0);

		List<Aerodynamics4McL2PoweredSourceValidationRunMatrix.PoweredSourceValidationRunSummary> unexpected =
				new ArrayList<>(failed);
		unexpected.remove(0);
		unexpected.add(run("experimentalQuad", "hover", true));
		Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary unexpectedSummary =
				Aerodynamics4McL2PoweredSourceAcceptanceHandoff.handoff("hover", unexpected);
		assertFalse(unexpectedSummary.acceptanceHandoffReady());
		assertEquals(1, unexpectedSummary.missingPresetCount());
		assertEquals(1, unexpectedSummary.unexpectedPresetCount());

		List<Aerodynamics4McL2PoweredSourceValidationRunMatrix.PoweredSourceValidationRunSummary> duplicate =
				List.of(run("racingQuad", "hover", true), run("racingQuad", "hover", true));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceAcceptanceHandoff.handoff("hover", duplicate));
	}

	@Test
	void handoffRejectsInvalidInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceAcceptanceHandoff.audit((ClassLoader) null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceAcceptanceHandoff.audit(
						(Aerodynamics4McL2PoweredSourceValidationRunMatrix.PoweredSourceValidationRunMatrixAudit) null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceAcceptanceHandoff.handoff("idle", List.of()));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceAcceptanceHandoff.handoff("hover", null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffAudit audit =
				Aerodynamics4McL2PoweredSourceAcceptanceHandoff.audit(getClass().getClassLoader());
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_powered_source_acceptance_handoff_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_acceptance_handoff_summary,all_handoffs,ready_handoff_count,0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_acceptance_handoff,hover,status,BLOCKED,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_acceptance_handoff,cruise,skipped_validation_run_count,4,")));
	}

	private static Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary find(
			List<Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary> handoffs,
			String spinState
	) {
		return handoffs.stream()
				.filter(handoff -> spinState.equals(handoff.spinState()))
				.findFirst()
				.orElseThrow();
	}

	private static Aerodynamics4McL2PoweredSourceValidationRunMatrix.PoweredSourceValidationRunSummary run(
			String presetName,
			String spinState,
			boolean passed
	) {
		boolean hover = "hover".equals(spinState);
		double forceErrorRatio = passed ? 0.0 : 0.50;
		double centerError = passed ? 0.0 : 0.12;
		return new Aerodynamics4McL2PoweredSourceValidationRunMatrix.PoweredSourceValidationRunSummary(
				presetName,
				spinState,
				hover ? Aerodynamics4McL2PoweredHoverValidation.SOURCE_ID : Aerodynamics4McL2PoweredCruiseValidation.SOURCE_ID,
				hover ? Aerodynamics4McL2PoweredHoverAcceptanceGate.SOURCE_ID : Aerodynamics4McL2PoweredCruiseAcceptanceGate.SOURCE_ID,
				true,
				true,
				true,
				passed,
				hover,
				!hover,
				10.0,
				10.0,
				forceErrorRatio * 10.0,
				forceErrorRatio,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				centerError,
				passed,
				passed ? "PASSED" : "FAILED",
				passed ? "validation-result-ready" : "validation-result-failed",
				"test-runtime",
				"live-runtime"
		);
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_source_acceptance_handoff_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
