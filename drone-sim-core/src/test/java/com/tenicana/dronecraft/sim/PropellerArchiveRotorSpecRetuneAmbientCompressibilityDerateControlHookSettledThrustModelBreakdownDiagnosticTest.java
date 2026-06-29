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
		assertEquals(255, staticRow.peakSampleIndex());
		assertEquals(1.28, staticRow.peakTimeSeconds(), 1.0e-12);
		assertEquals(0.982140927076222, staticRow.contractThrustRatio(), 1.0e-12);
		assertEquals(0.6169981537709639, staticRow.observedThrustRatio(), 1.0e-12);
		assertEquals(0.365142773305258, staticRow.thrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.9179318211076188, staticRow.omegaSquaredProxyRatio(), 1.0e-12);
		assertEquals(0.7132360851619984, staticRow.advanceProxyRatio(), 1.0e-12);
		assertEquals(0.716812715545108, staticRow.compressibilityProxyRatio(), 1.0e-12);
		assertEquals(0.26532821153111397, staticRow.proxyDeficitVsContractRatio(), 1.0e-12);
		assertEquals(0.09981456177414405, staticRow.residualThrustDeficitRatio(), 1.0e-12);
		assertEquals("explicit-omega-advance-inflow-compressibility-proxy",
				staticRow.dominantDeficitBucket());
		assertFalse(staticRow.omegaSquaredProxyAboveContract());
		assertTrue(staticRow.compressibilityProxyAboveObserved());
		assertFalse(staticRow.runtimeCouplingAllowed());
		assertFalse(staticRow.playableReferenceAllowed());
		assertFalse(staticRow.gameplayAutoApplyAllowed());
		assertEquals("FAIL", staticRow.status());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic
				.SettledThrustModelBreakdownRow blackboxSpeed =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic
								.row(22.0);
		assertEquals(345, blackboxSpeed.peakSampleIndex());
		assertEquals(1.73, blackboxSpeed.peakTimeSeconds(), 1.0e-12);
		assertEquals(0.8612963571223922, blackboxSpeed.observedThrustRatio(), 1.0e-12);
		assertEquals(0.1208445699538298, blackboxSpeed.thrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.9938711640935051, blackboxSpeed.omegaSquaredProxyRatio(), 1.0e-12);
		assertEquals(0.9038590293025921, blackboxSpeed.advanceProxyRatio(), 1.0e-12);
		assertEquals(0.9039944600527192, blackboxSpeed.inflowProxyRatio(), 1.0e-12);
		assertEquals(0.9039344176041539, blackboxSpeed.compressibilityProxyRatio(), 1.0e-12);
		assertEquals(0.9528305818968898, blackboxSpeed.residualAfterCompressibilityRatio(), 1.0e-12);
		assertEquals(0.07820650947206811, blackboxSpeed.proxyDeficitVsContractRatio(), 1.0e-12);
		assertEquals(0.042638060481761775, blackboxSpeed.residualThrustDeficitRatio(), 1.0e-12);
		assertTrue(blackboxSpeed.omegaSquaredProxyAboveContract());
		assertTrue(blackboxSpeed.compressibilityProxyAboveObserved());
		assertEquals("explicit-omega-advance-inflow-compressibility-proxy",
				blackboxSpeed.dominantDeficitBucket());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic
				.SettledThrustModelBreakdownRow fastRow =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic
								.row(28.0);
		assertEquals(277, fastRow.peakSampleIndex());
		assertEquals(1.39, fastRow.peakTimeSeconds(), 1.0e-12);
		assertEquals(1.066330284708266, fastRow.omegaSquaredProxyRatio(), 1.0e-12);
		assertEquals(0.8064032172531467, fastRow.advanceProxyRatio(), 1.0e-12);
		assertEquals(0.7999631993000701, fastRow.compressibilityProxyRatio(), 1.0e-12);
		assertEquals(0.7506749545342822, fastRow.observedThrustRatio(), 1.0e-12);
		assertEquals(0.19639631397321566,
				fastRow.neutralAveragePropellerThrustScale()
						- fastRow.deratedAveragePropellerThrustScale(),
				1.0e-12);
		assertTrue(fastRow.omegaSquaredProxyAboveContract());
		assertTrue(fastRow.advanceProxyRatio() < fastRow.omegaSquaredProxyRatio());

		assertEquals(5, audit.summary().rowCount());
		assertEquals(5, audit.summary().failedRowCount());
		assertEquals(2, audit.summary().omegaSquaredProxyAboveContractRowCount());
		assertEquals(5, audit.summary().compressibilityProxyAboveObservedRowCount());
		assertEquals(4, audit.summary().explicitProxyDominantRowCount());
		assertEquals(1, audit.summary().residualDominantRowCount());
		assertEquals(0.6169981537709639, audit.summary().minObservedThrustRatio(), 1.0e-12);
		assertEquals(0.9179318211076188, audit.summary().minOmegaSquaredProxyRatio(), 1.0e-12);
		assertEquals(1.066330284708266, audit.summary().maxOmegaSquaredProxyRatio(), 1.0e-12);
		assertEquals(0.7132360851619984, audit.summary().minAdvanceProxyRatio(), 1.0e-12);
		assertEquals(0.7126787949713781, audit.summary().minInflowProxyRatio(), 1.0e-12);
		assertEquals(0.716812715545108, audit.summary().minCompressibilityProxyRatio(), 1.0e-12);
		assertEquals(0.8607522444712229,
				audit.summary().minResidualAfterCompressibilityRatio(), 1.0e-12);
		assertEquals(0.26532821153111397,
				audit.summary().maxProxyDeficitVsContractRatio(), 1.0e-12);
		assertEquals(0.09981456177414405,
				audit.summary().maxResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.8612963571223922,
				audit.summary().blackboxSpeedObservedThrustRatio(), 1.0e-12);
		assertEquals(0.9039344176041539,
				audit.summary().blackboxSpeedCompressibilityProxyRatio(), 1.0e-12);
		assertEquals(0.042638060481761775,
				audit.summary().blackboxSpeedResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.19639631397321566,
				audit.summary().maxNeutralToDeratedPropellerScaleDrop(), 1.0e-12);
		assertEquals(0.20095774813129608, audit.summary().maxRotorAdvanceRatio(), 1.0e-12);
		assertEquals(0.5376140324847326, audit.summary().maxRotorTipMach(), 1.0e-12);
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
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_settled_thrust_model_breakdown_diagnostic_summary,all,all,all,all,omega_squared_proxy_above_contract_row_count,2,count,")));
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
