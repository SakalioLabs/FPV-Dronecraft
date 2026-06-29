package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class PropellerArchiveRotorSpecRetuneCompressibilityImpactGateTest {
	@Test
	void auditBuildsCompressibilityImpactOnlyAfterReynoldsAcceptance() {
		PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.RetuneCompressibilityImpactAudit audit =
				PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.audit();

		assertEquals("User-Propeller-Archive-RotorSpec-Retune-Compressibility-Impact-Gate-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("runtime thrust loss"));
		assertEquals(87, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(2, audit.impactRowCount());
		assertEquals(16, audit.scenarioMetricRowCount());
		assertEquals(14, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(15.0, audit.ambientTemperatureCelsius(), 1.0e-12);
		assertEquals(DroneEnvironment.speedOfSoundMetersPerSecond(15.0),
				audit.speedOfSoundMetersPerSecond(), 1.0e-12);
		assertEquals(0.46, audit.compressibilityOnsetMach(), 1.0e-12);
		assertEquals(10.0, audit.maxThrustLossPercentLimit(), 1.0e-12);
		assertEquals(0.22, audit.maxLoadFactorLimit(), 1.0e-12);
		assertEquals(1.18, audit.maxReactionTorqueScaleLimit(), 1.0e-12);
		assertEquals(0.12, audit.maxVibrationProxyLimit(), 1.0e-12);
		assertEquals(2, audit.rows().size());
		assertEquals(4, audit.scenarios().size());

		PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.RetuneCompressibilityImpactScenarioSummary current =
				find(audit.scenarios(), "current_retune_validation_blocked").summary();
		assertFalse(current.compressibilityImpactAvailable());
		assertEquals(0, current.impactRowCount());
		assertEquals("validation-run-not-planned", current.message());

		PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.RetuneCompressibilityImpactScenarioSummary allPass =
				find(audit.scenarios(), "synthetic_ready_validation_all_pass").summary();
		assertTrue(allPass.validationAcceptanceReady());
		assertTrue(allPass.reynoldsBudgetAvailable());
		assertTrue(allPass.compressibilityImpactAvailable());
		assertEquals(2, allPass.impactRowCount());
		assertEquals(2, allPass.compressibilityImpactAcceptedCount());
		assertEquals(2, allPass.compressibilityReviewRequiredCount());
		assertTrue(allPass.maxCandidateMaxCompressibilityIntensity() > 0.0);
		assertTrue(allPass.maxCandidateMaxThrustLossPercent() > 7.0);
		assertTrue(allPass.maxCandidateMaxThrustLossPercent() < audit.maxThrustLossPercentLimit());
		assertTrue(allPass.maxCandidateMaxLoadFactor() < audit.maxLoadFactorLimit());
		assertTrue(allPass.maxCandidateMaxReactionTorqueScale() < audit.maxReactionTorqueScaleLimit());
		assertTrue(allPass.maxCandidateMaxVibrationProxy() < audit.maxVibrationProxyLimit());
		assertEquals(0, allPass.configPatchAllowedCount());
		assertEquals(0, allPass.runtimeCouplingAllowedCount());
		assertEquals(0, allPass.gameplayAutoApplyAllowedCount());
		assertEquals("READY", allPass.status());
		assertEquals("compressibility-impact-bounded-review-required", allPass.message());

		PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.RetuneCompressibilityImpactScenarioSummary failed =
				find(audit.scenarios(), "synthetic_ready_validation_one_failed").summary();
		assertFalse(failed.compressibilityImpactAvailable());
		assertEquals("validation-result-failed", failed.message());

		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().compressibilityImpactAvailableScenarioCount());
		assertEquals(2, audit.extrema().totalImpactRowCount());
		assertEquals(2, audit.extrema().maxImpactRowCount());
		assertEquals(2, audit.extrema().maxCompressibilityImpactAcceptedCount());
		assertEquals(2, audit.extrema().maxCompressibilityReviewRequiredCount());
		assertTrue(audit.extrema().maxCandidateMaxThrustLossPercent() < audit.maxThrustLossPercentLimit());
		assertTrue(audit.extrema().maxCandidateMaxLoadFactor() < audit.maxLoadFactorLimit());
		assertTrue(audit.extrema().maxCandidateMaxReactionTorqueScale() < audit.maxReactionTorqueScaleLimit());
		assertTrue(audit.extrema().maxCandidateMaxVibrationProxy() < audit.maxVibrationProxyLimit());
		assertEquals(0, audit.extrema().configPatchAllowedScenarioCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedScenarioCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedScenarioCount());
	}

	@Test
	void impactRowsMatchRuntimeCompressibilityHelpers() throws ReflectiveOperationException {
		PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.RetuneCompressibilityImpactRow impact =
				PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.row(
						"synthetic_ready_validation_all_pass", "racingQuad");
		PropellerArchiveRotorSpecRetunePhysicalImpactPreview.RetunePhysicalImpactRow physical =
				PropellerArchiveRotorSpecRetunePhysicalImpactPreview.row(
						"synthetic_ready_validation_all_pass", "racingQuad");
		RotorSpec candidate = candidateRotor("racingQuad");
		double candidateMaxOmega = omegaFromRpm(physical.candidateMaxRpm());
		Method intensity = privateDoubleMethod("rotorCompressibilityIntensity", double.class);
		Method thrustScale = privateDoubleMethod("rotorCompressibilityThrustScale", double.class);
		Method loadFactor = privateDoubleMethod("rotorCompressibilityLoadFactor", double.class);
		Method torqueScale = privateDoubleMethod("rotorCompressibilityReactionTorqueScale", double.class);
		Method vibration = DronePhysics.class.getDeclaredMethod(
				"rotorCompressibilityVibration",
				RotorSpec.class,
				double.class,
				double.class
		);
		vibration.setAccessible(true);

		assertEquals((double) intensity.invoke(null, impact.currentHoverTipMach()),
				impact.currentHoverCompressibilityIntensity(), 1.0e-12);
		assertEquals((double) intensity.invoke(null, impact.candidateHoverTipMach()),
				impact.candidateHoverCompressibilityIntensity(), 1.0e-12);
		assertEquals((double) intensity.invoke(null, impact.currentMaxTipMach()),
				impact.currentMaxCompressibilityIntensity(), 1.0e-12);
		assertEquals((double) intensity.invoke(null, impact.candidateMaxTipMach()),
				impact.candidateMaxCompressibilityIntensity(), 1.0e-12);
		assertEquals((double) thrustScale.invoke(null, impact.candidateHoverTipMach()),
				impact.candidateHoverThrustScale(), 1.0e-12);
		assertEquals((double) thrustScale.invoke(null, impact.candidateMaxTipMach()),
				impact.candidateMaxThrustScale(), 1.0e-12);
		assertEquals((1.0 - impact.candidateMaxThrustScale()) * 100.0,
				impact.candidateMaxThrustLossPercent(), 1.0e-12);
		assertEquals((double) loadFactor.invoke(null, impact.candidateMaxTipMach()),
				impact.candidateMaxLoadFactor(), 1.0e-12);
		assertEquals((double) torqueScale.invoke(null, impact.candidateMaxTipMach()),
				impact.candidateMaxReactionTorqueScale(), 1.0e-12);
		assertEquals((double) vibration.invoke(null, candidate, candidateMaxOmega, impact.candidateMaxTipMach()),
				impact.candidateMaxVibrationProxy(), 1.0e-12);
		assertTrue(impact.candidateHoverCompressibilityFree());
		assertTrue(impact.candidateMaxCompressibilityReviewRequired());
		assertTrue(impact.candidateImpactWithinBudget());
		assertTrue(impact.compressibilityImpactAccepted());
		assertTrue(impact.reynoldsBudgetAccepted());
		assertTrue(impact.manualPatchReviewAllowed());
		assertFalse(impact.configPatchAllowed());
		assertFalse(impact.runtimeCouplingAllowed());
		assertFalse(impact.gameplayAutoApplyAllowed());
		assertEquals("READY", impact.status());
		assertEquals("compressibility-impact-bounded-review-required", impact.message());

		PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.RetuneCompressibilityImpactRow apDrone =
				PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.row(
						"synthetic_ready_validation_all_pass", "apDrone");
		assertTrue(apDrone.candidateMaxThrustLossPercent() < impact.candidateMaxThrustLossPercent());
		assertTrue(apDrone.candidateMaxVibrationProxy() < impact.candidateMaxVibrationProxy());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.row(
						"synthetic_ready_validation_all_pass", "cinewhoop"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.scenario("missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.RetuneCompressibilityImpactAudit audit =
				PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_compressibility_impact_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_compressibility_impact_summary,all_scenarios,total_impact_row_count,,,,,,2,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_compressibility_impact_scenario,synthetic_ready_validation_all_pass,compressibility_impact_accepted_count,,,,,,2,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_compressibility_impact,synthetic_ready_validation_all_pass,racingQuad,15.0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_compressibility_impact,synthetic_ready_validation_all_pass,apDrone,15.0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_compressibility_impact_summary,all_scenarios,runtime_coupling_allowed_scenario_count,,,,,,0,count,")));
	}

	private static Method privateDoubleMethod(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
		Method method = DronePhysics.class.getDeclaredMethod(name, parameterTypes);
		method.setAccessible(true);
		return method;
	}

	private static RotorSpec candidateRotor(String presetName) {
		DroneConfig config = PropellerArchiveRotorSpecConfigDeltaReport.configFor(presetName);
		RotorSpec current = config.rotors().get(0);
		PropellerArchiveRotorSpecConfigDeltaReport.RotorSpecConfigDeltaRow delta =
				PropellerArchiveRotorSpecConfigDeltaReport.row(presetName);
		return current
				.withRadiusMeters(delta.candidateRadiusMeters())
				.withBladePitchMeters(delta.candidateBladePitchMeters())
				.withBladeCount(delta.candidateBladeCount())
				.withThrustCoefficient(delta.candidateThrustCoefficient())
				.withYawTorquePerThrustMeter(delta.candidateYawTorquePerThrustMeters());
	}

	private static double omegaFromRpm(double rpm) {
		return rpm * 2.0 * Math.PI / 60.0;
	}

	private static PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.RetuneCompressibilityImpactScenario find(
			List<PropellerArchiveRotorSpecRetuneCompressibilityImpactGate.RetuneCompressibilityImpactScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_rotor_spec_retune_compressibility_impact_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
