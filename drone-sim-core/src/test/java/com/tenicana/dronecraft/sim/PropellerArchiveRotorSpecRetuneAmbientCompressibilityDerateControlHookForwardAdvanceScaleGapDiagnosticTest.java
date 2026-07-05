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
		assertEquals(46, blackboxSpeed.peakSampleIndex());
		assertEquals(0.23500000000000001, blackboxSpeed.peakTimeSeconds(), 1.0e-12);
		assertEquals(0.009642732412038528, blackboxSpeed.thrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.1238630739809382, blackboxSpeed.equivalentAdvanceRatioJ(), 1.0e-12);
		assertEquals(0.8128, blackboxSpeed.archiveAdvanceRatioMax(), 1.0e-12);
		assertEquals(0.152390592988359, blackboxSpeed.archiveCoverageRatio(), 1.0e-12);
		assertEquals(1.0, blackboxSpeed.currentFormulaThrustScale(), 1.0e-12);
		assertEquals(0.612195779760975, blackboxSpeed.archiveInterpolatedCtRatio(), 1.0e-12);
		assertEquals(0.38780422023902505, blackboxSpeed.formulaMinusArchiveCtRatio(), 1.0e-12);
		assertEquals(0.38780422023902505, blackboxSpeed.absoluteFormulaArchiveScaleGap(), 1.0e-12);
		assertFalse(blackboxSpeed.formulaArchiveGapWithinTolerance());
		assertEquals(1.0, blackboxSpeed.neutralAveragePropellerThrustScale(), 1.0e-12);
		assertEquals(1.0, blackboxSpeed.deratedAveragePropellerThrustScale(), 1.0e-12);
		assertEquals(0.0,
				blackboxSpeed.neutralToDeratedPropellerScaleDrop(), 1.0e-12);
		assertEquals(0.0,
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
		assertEquals("advance-scale-formula-outside-compact-archive-ct-ratio-tolerance", blackboxSpeed.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardAdvanceScaleGapDiagnostic
				.ForwardAdvanceScaleGapRow fastRow =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardAdvanceScaleGapDiagnostic
								.row(28.0);
		assertEquals(0.39937882442470585, fastRow.equivalentAdvanceRatioJ(), 1.0e-12);
		assertEquals(0.5975130680659957, fastRow.currentFormulaThrustScale(), 1.0e-12);
		assertEquals(0.484872044400078, fastRow.archiveInterpolatedCtRatio(), 1.0e-12);
		assertEquals(0.11264102366591772, fastRow.absoluteFormulaArchiveScaleGap(), 1.0e-12);
		assertEquals(0.13606002608911938,
				fastRow.neutralToDeratedPropellerScaleDrop(), 1.0e-12);
		assertEquals(1.207908288304092,
				fastRow.propellerScaleDropOverFormulaArchiveGap(), 1.0e-12);
		assertFalse(fastRow.formulaArchiveGapWithinTolerance());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardAdvanceScaleGapDiagnostic
				.ForwardAdvanceScaleGapSummary summary = audit.summary();
		assertEquals(5, summary.rowCount());
		assertEquals(5, summary.failedRowCount());
		assertEquals(5, summary.archiveCoveredRowCount());
		assertEquals(0, summary.formulaWithinArchiveToleranceRowCount());
		assertEquals(0.8333333333333334, summary.matchedOverTargetPitchRatio(), 1.0e-12);
		assertEquals(0.46672730576074817, summary.maxEquivalentAdvanceRatioJ(), 1.0e-12);
		assertEquals(0.5742215868119441, summary.maxArchiveCoverageRatio(), 1.0e-12);
		assertEquals(0.244610545941252, summary.minArchiveInterpolatedCtRatio(), 1.0e-12);
		assertEquals(0.5349220856041895, summary.minCurrentFormulaThrustScale(), 1.0e-12);
		assertEquals(0.38780422023902505, summary.maxFormulaMinusArchiveCtRatio(), 1.0e-12);
		assertEquals(0.38780422023902505,
				summary.maxAbsoluteFormulaArchiveScaleGap(), 1.0e-12);
		assertEquals(0.3117772294073716,
				summary.maxNeutralToDeratedPropellerScaleDrop(), 1.0e-12);
		assertEquals(1.207908288304092,
				summary.maxPropellerScaleDropOverFormulaArchiveGap(), 1.0e-12);
		assertEquals(0.1238630739809382,
				summary.blackboxSpeedEquivalentAdvanceRatioJ(), 1.0e-12);
		assertEquals(0.38780422023902505,
				summary.blackboxSpeedFormulaMinusArchiveCtRatio(), 1.0e-12);
		assertEquals(0.38780422023902505,
				summary.blackboxSpeedAbsoluteFormulaArchiveScaleGap(), 1.0e-12);
		assertEquals(0.0,
				summary.blackboxSpeedNeutralToDeratedPropellerScaleDrop(), 1.0e-12);
		assertEquals(0.13606002608911938,
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
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_forward_advance_scale_gap_diagnostic_summary,all,all,all,all,formula_within_archive_tolerance_row_count,0,count,")));
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
