package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardAdvanceScaleGapDiagnosticTest {
	@Test
	void auditShowsAdvanceScaleFormulaIsInsideCompactArchiveTolerance() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardAdvanceScaleGapDiagnostic
				.ForwardAdvanceScaleGapAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardAdvanceScaleGapDiagnostic
								.audit();

		assertEquals(
				"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-Forward-Advance-Scale-Gap-Diagnostic-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("compact DA4052 archive CT/static interpolation"));
		assertEquals(33, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(5, audit.speedSampleCount());
		assertEquals(5, audit.gapRowCount());
		assertEquals(21, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(0.06, audit.formulaArchiveGapTolerance(), 1.0e-12);
		assertEquals(5, audit.rows().size());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardAdvanceScaleGapDiagnostic
				.ForwardAdvanceScaleGapRow blackboxSpeed =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardAdvanceScaleGapDiagnostic
								.row(22.0);
		assertEquals("synthetic_derate_validation_all_pass", blackboxSpeed.scenarioName());
		assertEquals("apDrone", blackboxSpeed.presetName());
		assertEquals("cold_sea_level_minus10c", blackboxSpeed.ambientCaseName());
		assertEquals(14, blackboxSpeed.peakSampleIndex());
		assertEquals(0.075, blackboxSpeed.peakTimeSeconds(), 1.0e-12);
		assertEquals(0.012099179305115717, blackboxSpeed.thrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.3790335741884251, blackboxSpeed.equivalentAdvanceRatioJ(), 1.0e-12);
		assertEquals(0.8128, blackboxSpeed.archiveAdvanceRatioMax(), 1.0e-12);
		assertEquals(0.4663306769050506, blackboxSpeed.archiveCoverageRatio(), 1.0e-12);
		assertEquals(0.6438119922118046, blackboxSpeed.currentFormulaThrustScale(), 1.0e-12);
		assertEquals(0.612195779760975, blackboxSpeed.archiveInterpolatedCtRatio(), 1.0e-12);
		assertEquals(0.031616212450829684, blackboxSpeed.formulaMinusArchiveCtRatio(), 1.0e-12);
		assertEquals(0.031616212450829684, blackboxSpeed.absoluteFormulaArchiveScaleGap(), 1.0e-12);
		assertTrue(blackboxSpeed.formulaArchiveGapWithinTolerance());
		assertEquals(0.7157532134737403, blackboxSpeed.neutralAveragePropellerThrustScale(), 1.0e-12);
		assertEquals(0.7103332486403016, blackboxSpeed.deratedAveragePropellerThrustScale(), 1.0e-12);
		assertEquals(0.005419964833438762,
				blackboxSpeed.neutralToDeratedPropellerScaleDrop(), 1.0e-12);
		assertEquals(0.17142992197019252,
				blackboxSpeed.propellerScaleDropOverFormulaArchiveGap(), 1.0e-12);
		assertEquals(4.5, blackboxSpeed.targetPitchInches(), 1.0e-12);
		assertEquals(3.75, blackboxSpeed.matchedPitchInches(), 1.0e-12);
		assertEquals(0.8333333333333334, blackboxSpeed.matchedOverTargetPitchRatio(), 1.0e-12);
		assertEquals(3, blackboxSpeed.targetBladeCount());
		assertEquals(3, blackboxSpeed.matchedBladeCount());
		assertTrue(blackboxSpeed.bladeCountMatches());
		assertTrue(blackboxSpeed.sourceLicenseReviewRequired());
		assertFalse(blackboxSpeed.runtimeCouplingAllowed());
		assertFalse(blackboxSpeed.playableReferenceAllowed());
		assertFalse(blackboxSpeed.gameplayAutoApplyAllowed());
		assertEquals("residual-unmodeled-thrust-scale",
				blackboxSpeed.dominantDeficitBucket());
		assertEquals("BLOCKED", blackboxSpeed.status());
		assertEquals("advance-scale-formula-gap-is-not-dominant-blocker", blackboxSpeed.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardAdvanceScaleGapDiagnostic
				.ForwardAdvanceScaleGapRow fastRow =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardAdvanceScaleGapDiagnostic
								.row(28.0);
		assertEquals(0.39589918190203643, fastRow.equivalentAdvanceRatioJ(), 1.0e-12);
		assertEquals(0.604774975617014, fastRow.currentFormulaThrustScale(), 1.0e-12);
		assertEquals(0.484872044400078, fastRow.archiveInterpolatedCtRatio(), 1.0e-12);
		assertEquals(0.11990293121693602, fastRow.absoluteFormulaArchiveScaleGap(), 1.0e-12);
		assertEquals(0.2025117681330001,
				fastRow.neutralToDeratedPropellerScaleDrop(), 1.0e-12);
		assertEquals(1.6889642820041064,
				fastRow.propellerScaleDropOverFormulaArchiveGap(), 1.0e-12);
		assertFalse(fastRow.formulaArchiveGapWithinTolerance());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardAdvanceScaleGapDiagnostic
				.ForwardAdvanceScaleGapSummary summary = audit.summary();
		assertEquals(5, summary.rowCount());
		assertEquals(5, summary.failedRowCount());
		assertEquals(5, summary.archiveCoveredRowCount());
		assertEquals(1, summary.formulaWithinArchiveToleranceRowCount());
		assertEquals(0.8333333333333334, summary.matchedOverTargetPitchRatio(), 1.0e-12);
		assertEquals(0.5733534424091972, summary.maxEquivalentAdvanceRatioJ(), 1.0e-12);
		assertEquals(0.7054053179247013, summary.maxArchiveCoverageRatio(), 1.0e-12);
		assertEquals(0.244610545941252, summary.minArchiveInterpolatedCtRatio(), 1.0e-12);
		assertEquals(0.3396895853652977, summary.minCurrentFormulaThrustScale(), 1.0e-12);
		assertEquals(0.17416076186323715, summary.maxFormulaMinusArchiveCtRatio(), 1.0e-12);
		assertEquals(0.17416076186323715,
				summary.maxAbsoluteFormulaArchiveScaleGap(), 1.0e-12);
		assertEquals(0.3481504747303752,
				summary.maxNeutralToDeratedPropellerScaleDrop(), 1.0e-12);
		assertEquals(3.6616953309514315,
				summary.maxPropellerScaleDropOverFormulaArchiveGap(), 1.0e-12);
		assertEquals(0.3790335741884251,
				summary.blackboxSpeedEquivalentAdvanceRatioJ(), 1.0e-12);
		assertEquals(0.031616212450829684,
				summary.blackboxSpeedFormulaMinusArchiveCtRatio(), 1.0e-12);
		assertEquals(0.031616212450829684,
				summary.blackboxSpeedAbsoluteFormulaArchiveScaleGap(), 1.0e-12);
		assertEquals(0.005419964833438762,
				summary.blackboxSpeedNeutralToDeratedPropellerScaleDrop(), 1.0e-12);
		assertEquals(0.2025117681330001,
				summary.fastSpeedNeutralToDeratedPropellerScaleDrop(), 1.0e-12);
		assertEquals("advance-scale-formula-needs-archive-curve-refit",
				summary.dominantInterpretation());
		assertEquals("investigate-rotor-state-dispersion-and-residual-thrust-terms-before-changing-advance-scale",
				summary.nextRequiredAction());
		assertFalse(summary.forwardAdvanceScaleGapDiagnosticPassed());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardAdvanceScaleGapDiagnostic
						.row(12.0));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardAdvanceScaleGapDiagnostic
				.ForwardAdvanceScaleGapAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardAdvanceScaleGapDiagnostic
								.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_forward_advance_scale_gap_diagnostic_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_forward_advance_scale_gap_diagnostic,synthetic_derate_validation_all_pass,apDrone,cold_sea_level_minus10c,22.0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_forward_advance_scale_gap_diagnostic,synthetic_derate_validation_all_pass,apDrone,cold_sea_level_minus10c,28.0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_forward_advance_scale_gap_diagnostic_summary,all,all,all,all,formula_within_archive_tolerance_row_count,1,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_forward_advance_scale_gap_diagnostic_summary,all,all,all,all,next_required_action,investigate-rotor-state-dispersion-and-residual-thrust-terms-before-changing-advance-scale,text,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_forward_advance_scale_gap_diagnostic_method,all,all,all,all,method,compact-da4052-ct-ratio-vs-current-advance-scale,text,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_forward_advance_scale_gap_diagnostic_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
