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
		assertEquals(234, staticRow.peakSampleIndex());
		assertEquals(1.175, staticRow.peakTimeSeconds(), 1.0e-12);
		assertEquals(0.9821409270762219, staticRow.contractThrustRatio(), 1.0e-12);
		assertEquals(0.5074986608177752, staticRow.observedThrustRatio(), 1.0e-12);
		assertEquals(0.47464226625844674, staticRow.thrustLossOverContractRatio(), 1.0e-12);
		assertEquals(1.1939524226971883, staticRow.omegaSquaredProxyRatio(), 1.0e-12);
		assertEquals(0.5644194065018452, staticRow.advanceProxyRatio(), 1.0e-12);
		assertEquals(0.5527371282892592, staticRow.compressibilityProxyRatio(), 1.0e-12);
		assertEquals(0.42940379878696266, staticRow.proxyDeficitVsContractRatio(), 1.0e-12);
		assertEquals(0.04523846747148408, staticRow.residualThrustDeficitRatio(), 1.0e-12);
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
		assertEquals(353, blackboxSpeed.peakSampleIndex());
		assertEquals(1.77, blackboxSpeed.peakTimeSeconds(), 1.0e-12);
		assertEquals(0.7860252319233677, blackboxSpeed.observedThrustRatio(), 1.0e-12);
		assertEquals(0.1961156951528542, blackboxSpeed.thrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.9296234282054403, blackboxSpeed.omegaSquaredProxyRatio(), 1.0e-12);
		assertEquals(0.8351891708775564, blackboxSpeed.advanceProxyRatio(), 1.0e-12);
		assertEquals(0.8349215070952363, blackboxSpeed.inflowProxyRatio(), 1.0e-12);
		assertEquals(0.8378499613133623, blackboxSpeed.compressibilityProxyRatio(), 1.0e-12);
		assertEquals(0.9381455728556013, blackboxSpeed.residualAfterCompressibilityRatio(), 1.0e-12);
		assertEquals(0.1442909657628596, blackboxSpeed.proxyDeficitVsContractRatio(), 1.0e-12);
		assertEquals(0.051824729389994606, blackboxSpeed.residualThrustDeficitRatio(), 1.0e-12);
		assertFalse(blackboxSpeed.omegaSquaredProxyAboveContract());
		assertTrue(blackboxSpeed.compressibilityProxyAboveObserved());
		assertEquals("explicit-omega-advance-inflow-compressibility-proxy",
				blackboxSpeed.dominantDeficitBucket());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic
				.SettledThrustModelBreakdownRow fastRow =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic
								.row(28.0);
		assertEquals(359, fastRow.peakSampleIndex());
		assertEquals(1.8, fastRow.peakTimeSeconds(), 1.0e-12);
		assertEquals(1.0517486912432192, fastRow.omegaSquaredProxyRatio(), 1.0e-12);
		assertEquals(0.8488744430110099, fastRow.advanceProxyRatio(), 1.0e-12);
		assertEquals(0.8420303248793838, fastRow.compressibilityProxyRatio(), 1.0e-12);
		assertEquals(0.8617626178116597, fastRow.observedThrustRatio(), 1.0e-12);
		assertEquals(0.14444171718757015,
				fastRow.neutralAveragePropellerThrustScale()
						- fastRow.deratedAveragePropellerThrustScale(),
				1.0e-12);
		assertTrue(fastRow.omegaSquaredProxyAboveContract());
		assertTrue(fastRow.advanceProxyRatio() < fastRow.omegaSquaredProxyRatio());

		assertEquals(5, audit.summary().rowCount());
		assertEquals(5, audit.summary().failedRowCount());
		assertEquals(3, audit.summary().omegaSquaredProxyAboveContractRowCount());
		assertEquals(4, audit.summary().compressibilityProxyAboveObservedRowCount());
		assertEquals(5, audit.summary().explicitProxyDominantRowCount());
		assertEquals(0, audit.summary().residualDominantRowCount());
		assertEquals(0.5074986608177752, audit.summary().minObservedThrustRatio(), 1.0e-12);
		assertEquals(0.9296234282054403, audit.summary().minOmegaSquaredProxyRatio(), 1.0e-12);
		assertEquals(1.1939524226971883, audit.summary().maxOmegaSquaredProxyRatio(), 1.0e-12);
		assertEquals(0.5644194065018452, audit.summary().minAdvanceProxyRatio(), 1.0e-12);
		assertEquals(0.5647165843157105, audit.summary().minInflowProxyRatio(), 1.0e-12);
		assertEquals(0.5527371282892592, audit.summary().minCompressibilityProxyRatio(), 1.0e-12);
		assertEquals(0.9181555478071489,
				audit.summary().minResidualAfterCompressibilityRatio(), 1.0e-12);
		assertEquals(0.42940379878696266,
				audit.summary().maxProxyDeficitVsContractRatio(), 1.0e-12);
		assertEquals(0.06686369268551773,
				audit.summary().maxResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.7860252319233677,
				audit.summary().blackboxSpeedObservedThrustRatio(), 1.0e-12);
		assertEquals(0.8378499613133623,
				audit.summary().blackboxSpeedCompressibilityProxyRatio(), 1.0e-12);
		assertEquals(0.051824729389994606,
				audit.summary().blackboxSpeedResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.5054526604279256,
				audit.summary().maxNeutralToDeratedPropellerScaleDrop(), 1.0e-12);
		assertEquals(0.21182624071222794, audit.summary().maxRotorAdvanceRatio(), 1.0e-12);
		assertEquals(0.5541523433775322, audit.summary().maxRotorTipMach(), 1.0e-12);
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
