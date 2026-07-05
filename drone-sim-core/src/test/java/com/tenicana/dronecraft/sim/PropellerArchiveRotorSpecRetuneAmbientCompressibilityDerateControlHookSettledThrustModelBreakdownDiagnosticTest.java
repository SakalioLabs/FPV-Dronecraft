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

class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnosticTest {
	@Test
	void auditSeparatesSettledApDroneThrustLossIntoModelProxiesAndResiduals() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic
				.SettledThrustModelBreakdownAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic
								.audit();

		assertEquals(
				"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-Settled-Thrust-Model-Breakdown-Diagnostic-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("omega-squared, advance, inflow, and compressibility proxies"));
		assertEquals(36, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(5, audit.speedSampleCount());
		assertEquals(5, audit.breakdownRowCount());
		assertEquals(24, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals("DronePhysics.targetOmega=maxOmega*targetMaxRpmScale-before-motor-response",
				audit.controlBoundary());
		assertEquals(5, audit.rows().size());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic
				.SettledThrustModelBreakdownRow staticRow =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic
								.row(0.0);
		assertEquals("apDrone", staticRow.presetName());
		assertEquals("cold_sea_level_minus10c", staticRow.ambientCaseName());
		assertEquals(339, staticRow.peakSampleIndex());
		assertEquals(1.7, staticRow.peakTimeSeconds(), 1.0e-12);
		assertEquals(0.982140927076222, staticRow.contractThrustRatio(), 1.0e-12);
		assertEquals(0.8439484695949374, staticRow.observedThrustRatio(), 1.0e-12);
		assertEquals(0.13819245748128453, staticRow.thrustLossOverContractRatio(), 1.0e-12);
		assertEquals(1.1783241553337716, staticRow.omegaSquaredProxyRatio(), 1.0e-12);
		assertEquals(0.7937431867861795, staticRow.advanceProxyRatio(), 1.0e-12);
		assertEquals(0.7764582265641451, staticRow.compressibilityProxyRatio(), 1.0e-12);
		assertEquals(0.20568270051207682, staticRow.proxyDeficitVsContractRatio(), 1.0e-12);
		assertEquals(0.0, staticRow.residualThrustDeficitRatio(), 1.0e-12);
		assertEquals("explicit-omega-advance-inflow-compressibility-proxy",
				staticRow.dominantDeficitBucket());
		assertTrue(staticRow.omegaSquaredProxyAboveContract());
		assertFalse(staticRow.compressibilityProxyAboveObserved());
		assertFalse(staticRow.runtimeCouplingAllowed());
		assertFalse(staticRow.playableReferenceAllowed());
		assertFalse(staticRow.gameplayAutoApplyAllowed());
		assertEquals("FAIL", staticRow.status());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic
				.SettledThrustModelBreakdownRow blackboxSpeed =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic
								.row(22.0);
		assertEquals(46, blackboxSpeed.peakSampleIndex());
		assertEquals(0.23500000000000001, blackboxSpeed.peakTimeSeconds(), 1.0e-12);
		assertEquals(0.9724981946641834, blackboxSpeed.observedThrustRatio(), 1.0e-12);
		assertEquals(0.009642732412038528, blackboxSpeed.thrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.9929391538783235, blackboxSpeed.omegaSquaredProxyRatio(), 1.0e-12);
		assertEquals(0.9929391538783235, blackboxSpeed.advanceProxyRatio(), 1.0e-12);
		assertEquals(0.9928762561316229, blackboxSpeed.inflowProxyRatio(), 1.0e-12);
		assertEquals(0.9933675867486617, blackboxSpeed.compressibilityProxyRatio(), 1.0e-12);
		assertEquals(0.9789912693318444, blackboxSpeed.residualAfterCompressibilityRatio(), 1.0e-12);
		assertEquals(0.0, blackboxSpeed.proxyDeficitVsContractRatio(), 1.0e-12);
		assertEquals(0.020869392084478244, blackboxSpeed.residualThrustDeficitRatio(), 1.0e-12);
		assertTrue(blackboxSpeed.omegaSquaredProxyAboveContract());
		assertTrue(blackboxSpeed.compressibilityProxyAboveObserved());
		assertEquals("residual-unmodeled-thrust-scale",
				blackboxSpeed.dominantDeficitBucket());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic
				.SettledThrustModelBreakdownRow fastRow =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic
								.row(28.0);
		assertEquals(216, fastRow.peakSampleIndex());
		assertEquals(1.085, fastRow.peakTimeSeconds(), 1.0e-12);
		assertEquals(1.0488073812489271, fastRow.omegaSquaredProxyRatio(), 1.0e-12);
		assertEquals(0.8691842510359027, fastRow.advanceProxyRatio(), 1.0e-12);
		assertEquals(0.8650819552732446, fastRow.compressibilityProxyRatio(), 1.0e-12);
		assertEquals(0.8076501353119606, fastRow.observedThrustRatio(), 1.0e-12);
		assertEquals(0.13606002608911938,
				fastRow.neutralAveragePropellerThrustScale()
						- fastRow.deratedAveragePropellerThrustScale(),
				1.0e-12);
		assertTrue(fastRow.omegaSquaredProxyAboveContract());
		assertTrue(fastRow.advanceProxyRatio() < fastRow.omegaSquaredProxyRatio());

		assertEquals(5, audit.summary().rowCount());
		assertEquals(5, audit.summary().failedRowCount());
		assertEquals(4, audit.summary().omegaSquaredProxyAboveContractRowCount());
		assertEquals(4, audit.summary().compressibilityProxyAboveObservedRowCount());
		assertEquals(2, audit.summary().explicitProxyDominantRowCount());
		assertEquals(3, audit.summary().residualDominantRowCount());
		assertEquals(0.8076501353119606, audit.summary().minObservedThrustRatio(), 1.0e-12);
		assertEquals(0.9622484232931819, audit.summary().minOmegaSquaredProxyRatio(), 1.0e-12);
		assertEquals(1.1783241553337716, audit.summary().maxOmegaSquaredProxyRatio(), 1.0e-12);
		assertEquals(0.7937431867861795, audit.summary().minAdvanceProxyRatio(), 1.0e-12);
		assertEquals(0.7930324382564363, audit.summary().minInflowProxyRatio(), 1.0e-12);
		assertEquals(0.7764582265641451, audit.summary().minCompressibilityProxyRatio(), 1.0e-12);
		assertEquals(0.9332791910164063,
				audit.summary().minResidualAfterCompressibilityRatio(), 1.0e-12);
		assertEquals(0.20568270051207682,
				audit.summary().maxProxyDeficitVsContractRatio(), 1.0e-12);
		assertEquals(0.06285299649224374,
				audit.summary().maxResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.9724981946641834,
				audit.summary().blackboxSpeedObservedThrustRatio(), 1.0e-12);
		assertEquals(0.9933675867486617,
				audit.summary().blackboxSpeedCompressibilityProxyRatio(), 1.0e-12);
		assertEquals(0.020869392084478244,
				audit.summary().blackboxSpeedResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.3117772294073716,
				audit.summary().maxNeutralToDeratedPropellerScaleDrop(), 1.0e-12);
		assertEquals(0.15136485291074253, audit.summary().maxRotorAdvanceRatio(), 1.0e-12);
		assertEquals(0.5552805345604392, audit.summary().maxRotorTipMach(), 1.0e-12);
		assertEquals("residual-unmodeled-thrust-scale",
				audit.summary().dominantDeficitBucket());
		assertEquals("investigate-apDrone-forward-advance-propeller-scale-and-residual-thrust-terms",
				audit.summary().nextRequiredAction());
		assertFalse(audit.summary().settledThrustModelBreakdownPassed());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic
						.row(12.0));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic
				.SettledThrustModelBreakdownAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic
								.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_settled_thrust_model_breakdown_diagnostic_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_settled_thrust_model_breakdown_diagnostic,synthetic_derate_validation_all_pass,apDrone,cold_sea_level_minus10c,22.0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_settled_thrust_model_breakdown_diagnostic,synthetic_derate_validation_all_pass,apDrone,cold_sea_level_minus10c,28.0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_settled_thrust_model_breakdown_diagnostic_summary,all,all,all,all,omega_squared_proxy_above_contract_row_count,4,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_settled_thrust_model_breakdown_diagnostic_summary,all,all,all,all,dominant_deficit_bucket,residual-unmodeled-thrust-scale,text,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_settled_thrust_model_breakdown_diagnostic_method,all,all,all,all,method,settled-apDrone-full-throttle-thrust-model-proxy-breakdown,text,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_settled_thrust_model_breakdown_diagnostic_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
