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
		assertEquals(266, staticRow.peakSampleIndex());
		assertEquals(1.335, staticRow.peakTimeSeconds(), 1.0e-12);
		assertEquals(0.982140927076222, staticRow.contractThrustRatio(), 1.0e-12);
		assertEquals(0.6317537107590055, staticRow.observedThrustRatio(), 1.0e-12);
		assertEquals(0.35038721631721637, staticRow.thrustLossOverContractRatio(), 1.0e-12);
		assertEquals(1.1542443118185286, staticRow.omegaSquaredProxyRatio(), 1.0e-12);
		assertEquals(0.6925393735653913, staticRow.advanceProxyRatio(), 1.0e-12);
		assertEquals(0.6834103164455918, staticRow.compressibilityProxyRatio(), 1.0e-12);
		assertEquals(0.2987306106306301, staticRow.proxyDeficitVsContractRatio(), 1.0e-12);
		assertEquals(0.051656605686586254, staticRow.residualThrustDeficitRatio(), 1.0e-12);
		assertEquals("explicit-omega-advance-inflow-compressibility-proxy",
				staticRow.dominantDeficitBucket());
		assertTrue(staticRow.omegaSquaredProxyAboveContract());
		assertTrue(staticRow.compressibilityProxyAboveObserved());
		assertFalse(staticRow.runtimeCouplingAllowed());
		assertFalse(staticRow.playableReferenceAllowed());
		assertFalse(staticRow.gameplayAutoApplyAllowed());
		assertEquals("FAIL", staticRow.status());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic
				.SettledThrustModelBreakdownRow blackboxSpeed =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic
								.row(22.0);
		assertEquals(14, blackboxSpeed.peakSampleIndex());
		assertEquals(0.075, blackboxSpeed.peakTimeSeconds(), 1.0e-12);
		assertEquals(0.9700417477711062, blackboxSpeed.observedThrustRatio(), 1.0e-12);
		assertEquals(0.012099179305115717, blackboxSpeed.thrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.988541844435582, blackboxSpeed.omegaSquaredProxyRatio(), 1.0e-12);
		assertEquals(0.9809780970903091, blackboxSpeed.advanceProxyRatio(), 1.0e-12);
		assertEquals(0.9809878207539645, blackboxSpeed.inflowProxyRatio(), 1.0e-12);
		assertEquals(0.983280058565587, blackboxSpeed.compressibilityProxyRatio(), 1.0e-12);
		assertEquals(0.9865365816390166, blackboxSpeed.residualAfterCompressibilityRatio(), 1.0e-12);
		assertEquals(0.0, blackboxSpeed.proxyDeficitVsContractRatio(), 1.0e-12);
		assertEquals(0.01323831079448079, blackboxSpeed.residualThrustDeficitRatio(), 1.0e-12);
		assertTrue(blackboxSpeed.omegaSquaredProxyAboveContract());
		assertTrue(blackboxSpeed.compressibilityProxyAboveObserved());
		assertEquals("residual-unmodeled-thrust-scale",
				blackboxSpeed.dominantDeficitBucket());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic
				.SettledThrustModelBreakdownRow fastRow =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic
								.row(28.0);
		assertEquals(301, fastRow.peakSampleIndex());
		assertEquals(1.51, fastRow.peakTimeSeconds(), 1.0e-12);
		assertEquals(1.069245520967631, fastRow.omegaSquaredProxyRatio(), 1.0e-12);
		assertEquals(0.8091071143081303, fastRow.advanceProxyRatio(), 1.0e-12);
		assertEquals(0.8032507968424158, fastRow.compressibilityProxyRatio(), 1.0e-12);
		assertEquals(0.7403421511070829, fastRow.observedThrustRatio(), 1.0e-12);
		assertEquals(0.2025117681330001,
				fastRow.neutralAveragePropellerThrustScale()
						- fastRow.deratedAveragePropellerThrustScale(),
				1.0e-12);
		assertTrue(fastRow.omegaSquaredProxyAboveContract());
		assertTrue(fastRow.advanceProxyRatio() < fastRow.omegaSquaredProxyRatio());

		assertEquals(5, audit.summary().rowCount());
		assertEquals(5, audit.summary().failedRowCount());
		assertEquals(4, audit.summary().omegaSquaredProxyAboveContractRowCount());
		assertEquals(5, audit.summary().compressibilityProxyAboveObservedRowCount());
		assertEquals(4, audit.summary().explicitProxyDominantRowCount());
		assertEquals(1, audit.summary().residualDominantRowCount());
		assertEquals(0.6317537107590055, audit.summary().minObservedThrustRatio(), 1.0e-12);
		assertEquals(0.9674805213308764, audit.summary().minOmegaSquaredProxyRatio(), 1.0e-12);
		assertEquals(1.1542443118185286, audit.summary().maxOmegaSquaredProxyRatio(), 1.0e-12);
		assertEquals(0.6925393735653913, audit.summary().minAdvanceProxyRatio(), 1.0e-12);
		assertEquals(0.6932585774909216, audit.summary().minInflowProxyRatio(), 1.0e-12);
		assertEquals(0.6834103164455918, audit.summary().minCompressibilityProxyRatio(), 1.0e-12);
		assertEquals(0.9216824359432605,
				audit.summary().minResidualAfterCompressibilityRatio(), 1.0e-12);
		assertEquals(0.2987306106306301,
				audit.summary().maxProxyDeficitVsContractRatio(), 1.0e-12);
		assertEquals(0.06290864573533295,
				audit.summary().maxResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.9700417477711062,
				audit.summary().blackboxSpeedObservedThrustRatio(), 1.0e-12);
		assertEquals(0.983280058565587,
				audit.summary().blackboxSpeedCompressibilityProxyRatio(), 1.0e-12);
		assertEquals(0.01323831079448079,
				audit.summary().blackboxSpeedResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.3481504747303752,
				audit.summary().maxNeutralToDeratedPropellerScaleDrop(), 1.0e-12);
		assertEquals(0.1859448941704381, audit.summary().maxRotorAdvanceRatio(), 1.0e-12);
		assertEquals(0.5635711275769744, audit.summary().maxRotorTipMach(), 1.0e-12);
		assertEquals("explicit-omega-advance-inflow-compressibility-proxy",
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
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_settled_thrust_model_breakdown_diagnostic_summary,all,all,all,all,dominant_deficit_bucket,explicit-omega-advance-inflow-compressibility-proxy,text,")));
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
