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

class Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGateTest {
	@Test
	void auditBuildsStableCruiseSkewWakeReadinessScenarios() {
		Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.PoweredCruiseSkewWakeReadinessAudit audit =
				Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.audit();

		assertEquals("A4MC-L2-Powered-Cruise-Skew-Wake-Readiness-Gate-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("runtime coupling remains closed"));
		assertEquals(155, audit.packetMetricRowCount());
		assertEquals(6, audit.sourceReferenceCount());
		assertEquals(6, audit.scenarioSampleCount());
		assertEquals(23, audit.scenarioMetricCount());
		assertEquals(10, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(6, audit.scenarios().size());

		Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.PoweredCruiseSkewWakeReadinessSummary current =
				find(audit.scenarios(), "current_api_unavailable_tick_rate_only").summary();
		assertFalse(current.poweredSourceApiAvailable());
		assertFalse(current.skewWakeProbeApiAvailable());
		assertFalse(current.transientProbeApiAvailable());
		assertEquals(0.05, current.appliedMaxSamplePeriodSeconds(), 1.0e-12);
		assertEquals(192, current.expectedTargetCount());
		assertEquals(192, current.observedTargetCount());
		assertEquals(16, current.sourceTermTargetCount());
		assertEquals(64, current.centerlineSweepTargetCount());
		assertEquals(128, current.lateralSweepTargetCount());
		assertEquals(0, current.transientProbeApiAvailableTargetCount());
		assertEquals(0, current.runtimeCouplingAllowedTargetCount());
		assertEquals(0, current.invalidTargetCount());
		assertEquals(0, current.missingTargetCount());
		assertEquals(0, current.unexpectedTargetCount());
		assertTrue(current.requiredMaxSamplePeriodSeconds() > 0.0);
		assertTrue(current.maxRequiredSampleRateHertz() > 0.0);
		assertTrue(current.allExpectedTargetsPresent());
		assertFalse(current.allTargetProbeApisAvailable());
		assertFalse(current.temporalResolutionSufficient());
		assertFalse(current.poweredSourceAndProbeApisAvailable());
		assertTrue(current.runtimeCouplingBlockedForAllTargets());
		assertFalse(current.liveValidationRunAllowed());
		assertEquals("BLOCKED", current.status());

		Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.PoweredCruiseSkewWakeReadinessSummary ready =
				find(audit.scenarios(), "powered_and_skew_probe_api_available_substepped").summary();
		assertTrue(ready.poweredSourceApiAvailable());
		assertTrue(ready.skewWakeProbeApiAvailable());
		assertTrue(ready.transientProbeApiAvailable());
		assertEquals(192, ready.transientProbeApiAvailableTargetCount());
		assertTrue(ready.allExpectedTargetsPresent());
		assertTrue(ready.allTargetProbeApisAvailable());
		assertTrue(ready.temporalResolutionSufficient());
		assertTrue(ready.poweredSourceAndProbeApisAvailable());
		assertTrue(ready.runtimeCouplingBlockedForAllTargets());
		assertTrue(ready.liveValidationRunAllowed());
		assertEquals("READY_FOR_LIVE_CRUISE_SKEW_WAKE_VALIDATION", ready.status());

		Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.PoweredCruiseSkewWakeReadinessSummary missing =
				find(audit.scenarios(), "api_available_one_target_missing").summary();
		assertEquals(191, missing.observedTargetCount());
		assertEquals(1, missing.missingTargetCount());
		assertFalse(missing.allExpectedTargetsPresent());
		assertFalse(missing.liveValidationRunAllowed());

		Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.PoweredCruiseSkewWakeReadinessSummary underresolved =
				find(audit.scenarios(), "api_available_tick_rate_underresolved").summary();
		assertTrue(underresolved.allTargetProbeApisAvailable());
		assertFalse(underresolved.temporalResolutionSufficient());
		assertFalse(underresolved.liveValidationRunAllowed());

		Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.PoweredCruiseSkewWakeReadinessSummary skewApiUnavailable =
				find(audit.scenarios(), "powered_api_available_skew_probe_unavailable").summary();
		assertFalse(skewApiUnavailable.skewWakeProbeApiAvailable());
		assertFalse(skewApiUnavailable.poweredSourceAndProbeApisAvailable());
		assertFalse(skewApiUnavailable.liveValidationRunAllowed());

		Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.PoweredCruiseSkewWakeReadinessSummary targetApiUnavailable =
				find(audit.scenarios(), "powered_api_available_probe_target_unavailable").summary();
		assertTrue(targetApiUnavailable.transientProbeApiAvailable());
		assertEquals(0, targetApiUnavailable.transientProbeApiAvailableTargetCount());
		assertFalse(targetApiUnavailable.allTargetProbeApisAvailable());
		assertFalse(targetApiUnavailable.liveValidationRunAllowed());

		assertEquals(6, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().allowedScenarioCount());
		assertEquals(5, audit.extrema().blockedScenarioCount());
		assertEquals(192, audit.extrema().maxExpectedTargetCount());
		assertEquals(1, audit.extrema().maxMissingTargetCount());
		assertEquals(0, audit.extrema().maxInvalidTargetCount());
		assertEquals(0, audit.extrema().maxUnexpectedTargetCount());
		assertEquals(current.requiredMaxSamplePeriodSeconds(),
				audit.extrema().minRequiredMaxSamplePeriodSeconds(), 1.0e-16);
		assertEquals(0.05, audit.extrema().maxAppliedMaxSamplePeriodSeconds(), 1.0e-12);
	}

	@Test
	void gateAllowsOnlyCompleteApiBackedSubsteppedValidationTargets() {
		List<Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget> readyTargets =
				readyTargets();
		double requiredMaxSamplePeriod = requiredMaxSamplePeriod(readyTargets);

		Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.PoweredCruiseSkewWakeReadinessSummary open =
				Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.gate(
						true,
						true,
						true,
						requiredMaxSamplePeriod,
						readyTargets
				);
		assertTrue(open.liveValidationRunAllowed());

		assertFalse(Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate
				.gate(false, true, true, requiredMaxSamplePeriod, readyTargets).liveValidationRunAllowed());
		assertFalse(Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate
				.gate(true, false, true, requiredMaxSamplePeriod, readyTargets).liveValidationRunAllowed());
		assertFalse(Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate
				.gate(true, true, false, requiredMaxSamplePeriod, readyTargets).liveValidationRunAllowed());
		assertFalse(Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate
				.gate(true, true, true, 0.05, readyTargets).liveValidationRunAllowed());
	}

	@Test
	void gateRejectsMissingUnexpectedInvalidDuplicateAndRuntimeCoupledTargets() {
		List<Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget> readyTargets =
				readyTargets();
		double requiredMaxSamplePeriod = requiredMaxSamplePeriod(readyTargets);

		Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.PoweredCruiseSkewWakeReadinessSummary missing =
				Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.gate(
						true, true, true, requiredMaxSamplePeriod, readyTargets.subList(0, readyTargets.size() - 1));
		assertEquals(1, missing.missingTargetCount());
		assertFalse(missing.liveValidationRunAllowed());

		List<Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget> unexpected =
				new ArrayList<>(readyTargets);
		unexpected.set(0, copy(readyTargets.get(0), "experimentalQuad", readyTargets.get(0).spinState(),
				readyTargets.get(0).axialPlaneIndex(), readyTargets.get(0).sweepColumnIndex(), true, false));
		Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.PoweredCruiseSkewWakeReadinessSummary unexpectedSummary =
				Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.gate(
						true, true, true, requiredMaxSamplePeriod, unexpected);
		assertEquals(1, unexpectedSummary.missingTargetCount());
		assertEquals(1, unexpectedSummary.unexpectedTargetCount());
		assertFalse(unexpectedSummary.liveValidationRunAllowed());

		List<Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget> invalid =
				new ArrayList<>(readyTargets);
		invalid.set(0, copy(readyTargets.get(0), readyTargets.get(0).presetName(), "hover",
				readyTargets.get(0).axialPlaneIndex(), readyTargets.get(0).sweepColumnIndex(), true, false));
		Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.PoweredCruiseSkewWakeReadinessSummary invalidSummary =
				Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.gate(
						true, true, true, requiredMaxSamplePeriod, invalid);
		assertEquals(1, invalidSummary.invalidTargetCount());
		assertFalse(invalidSummary.liveValidationRunAllowed());

		List<Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget> runtimeCoupled =
				new ArrayList<>(readyTargets);
		runtimeCoupled.set(0, copy(readyTargets.get(0), readyTargets.get(0).presetName(),
				readyTargets.get(0).spinState(), readyTargets.get(0).axialPlaneIndex(),
				readyTargets.get(0).sweepColumnIndex(), true, true));
		Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.PoweredCruiseSkewWakeReadinessSummary runtimeSummary =
				Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.gate(
						true, true, true, requiredMaxSamplePeriod, runtimeCoupled);
		assertEquals(1, runtimeSummary.runtimeCouplingAllowedTargetCount());
		assertFalse(runtimeSummary.runtimeCouplingBlockedForAllTargets());
		assertFalse(runtimeSummary.liveValidationRunAllowed());

		List<Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget> duplicate =
				new ArrayList<>(readyTargets);
		duplicate.set(1, readyTargets.get(0));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.gate(
						true, true, true, requiredMaxSamplePeriod, duplicate));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.gate(
						true, true, true, -1.0, readyTargets));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.gate(
						true, true, true, requiredMaxSamplePeriod, null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.PoweredCruiseSkewWakeReadinessAudit audit =
				Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_cruise_skew_wake_readiness_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_readiness_summary,all_scenarios,allowed_scenario_count,1,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_readiness_scenario,current_api_unavailable_tick_rate_only,live_validation_run_allowed,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_readiness_scenario,powered_and_skew_probe_api_available_substepped,live_validation_run_allowed,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_readiness_scenario,api_available_tick_rate_underresolved,temporal_resolution_sufficient,false,")));
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.PoweredCruiseSkewWakeReadinessScenario find(
			List<Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate.PoweredCruiseSkewWakeReadinessScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static List<Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget> readyTargets() {
		return Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.audit().targets().stream()
				.map(target -> copy(target, target.presetName(), target.spinState(), target.axialPlaneIndex(),
						target.sweepColumnIndex(), true, false))
				.toList();
	}

	private static double requiredMaxSamplePeriod(
			List<Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget> targets
	) {
		return targets.stream()
				.mapToDouble(Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget::recommendedMaxSamplePeriodSeconds)
				.min()
				.orElseThrow();
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget copy(
			Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget target,
			String presetName,
			String spinState,
			int axialPlaneIndex,
			int sweepColumnIndex,
			boolean transientProbeApiAvailable,
			boolean runtimeCouplingAllowed
	) {
		return new Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget(
				presetName,
				spinState,
				target.rotorIndex(),
				axialPlaneIndex,
				sweepColumnIndex,
				target.axialPlaneFraction(),
				target.centerlineResultantTransitSeconds(),
				target.lateralAdjustedTransitSeconds(),
				target.transitBandSeconds(),
				target.configuredDynamicInflowTauSeconds(),
				target.tauOverCenterlineTransit(),
				target.tauOverLateralTransit(),
				target.minimumSamplesPerFastTransit(),
				target.recommendedMaxSamplePeriodSeconds(),
				target.recommendedMinSampleRateHertz(),
				target.minecraftTickRateHertz(),
				target.minecraftTickSamplesPerCenterlineTransit(),
				target.minecraftTickResolvesCenterlineTransit(),
				target.requiredSubstepsPerMinecraftTick(),
				target.sixtyHertzSamplesPerCenterlineTransit(),
				target.sixtyHertzResolvesCenterlineTransit(),
				target.requiredSubstepsPerSixtyHertzFrame(),
				target.oneTwentyHertzSamplesPerCenterlineTransit(),
				target.oneTwentyHertzResolvesCenterlineTransit(),
				target.requiredSubstepsPerOneTwentyHertzFrame(),
				target.twoFortyHertzSamplesPerCenterlineTransit(),
				target.twoFortyHertzResolvesCenterlineTransit(),
				target.requiredSubstepsPerTwoFortyHertzFrame(),
				target.oneKilohertzSamplesPerCenterlineTransit(),
				target.oneKilohertzResolvesCenterlineTransit(),
				target.requiredSubstepsPerOneKilohertzFrame(),
				target.oneKilohertzSamplesPerLateralAdjustedTransit(),
				target.oneKilohertzResolvesLateralAdjustedTransit(),
				transientProbeApiAvailable,
				runtimeCouplingAllowed,
				target.validationBeforeRuntimeRequired(),
				target.status(),
				target.runtimeInfo()
		);
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_cruise_skew_wake_readiness_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
