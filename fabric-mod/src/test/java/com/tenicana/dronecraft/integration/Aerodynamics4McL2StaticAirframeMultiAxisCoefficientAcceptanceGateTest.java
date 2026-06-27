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

class Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGateTest {
	@Test
	void auditBuildsStableMultiAxisAcceptanceScenarios() {
		Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.MultiAxisCoefficientAcceptanceAudit audit =
				Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.audit();

		assertEquals("A4MC-L2-Static-Airframe-Multi-Axis-Coefficient-Acceptance-Gate-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("sign-consistent"));
		assertEquals(104, audit.packetMetricRowCount());
		assertEquals(5, audit.sourceReferenceCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(22, audit.scenarioMetricCount());
		assertEquals(10, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(4, audit.scenarios().size());

		Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.MultiAxisCoefficientAcceptanceSummary unavailable =
				find(audit.scenarios(), "current_runtime_unavailable").summary();
		assertEquals(24, unavailable.expectedSeedCount());
		assertEquals(24, unavailable.observedSeedCount());
		assertEquals(0, unavailable.fitReadyCount());
		assertEquals(0, unavailable.liveRuntimeCount());
		assertFalse(unavailable.multiAxisGameplayFitAllowed());

		Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.MultiAxisCoefficientAcceptanceSummary testRuntime =
				find(audit.scenarios(), "test_runtime_fit_ready_blocked").summary();
		assertEquals(24, testRuntime.fitReadyCount());
		assertEquals(0, testRuntime.liveRuntimeCount());
		assertEquals(8, testRuntime.forwardDragSeedCount());
		assertEquals(8, testRuntime.sideforceSeedCount());
		assertEquals(8, testRuntime.liftSeedCount());
		assertEquals(24, testRuntime.momentPressureCenterSeedCount());
		assertEquals(4, testRuntime.forwardReverseSignConsistentPresetCount());
		assertTrue(testRuntime.allSeedsPresent());
		assertTrue(testRuntime.allSeedsFitReady());
		assertFalse(testRuntime.allSeedsLiveRuntime());
		assertTrue(testRuntime.allForwardReverseSignConsistent());
		assertFalse(testRuntime.multiAxisGameplayFitAllowed());

		Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.MultiAxisCoefficientAcceptanceSummary live =
				find(audit.scenarios(), "live_runtime_all_bounded_sign_consistent").summary();
		assertTrue(live.allSeedsPresent());
		assertTrue(live.allSeedsFitReady());
		assertTrue(live.allSeedsLiveRuntime());
		assertTrue(live.allCoefficientsBounded());
		assertTrue(live.allForwardReverseSignConsistent());
		assertTrue(live.multiAxisGameplayFitAllowed());

		Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.MultiAxisCoefficientAcceptanceSummary signBroken =
				find(audit.scenarios(), "live_runtime_forward_reverse_sign_broken").summary();
		assertTrue(signBroken.allSeedsLiveRuntime());
		assertTrue(signBroken.allCoefficientsBounded());
		assertFalse(signBroken.allForwardReverseSignConsistent());
		assertEquals(3, signBroken.forwardReverseSignConsistentPresetCount());
		assertFalse(signBroken.multiAxisGameplayFitAllowed());
		assertEquals(1, audit.extrema().allowedScenarioCount());
		assertEquals(3, audit.extrema().blockedScenarioCount());
		assertEquals(24, audit.extrema().maxExpectedSeedCount());
	}

	@Test
	void gateRequiresLiveRuntimeAndSignConsistency() {
		List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> testSeeds =
				Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.audit(getClass().getClassLoader()).seeds();
		List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> liveSeeds =
				testSeeds.stream().map(seed -> withRuntime(seed, "a4mc-live-calibration")).toList();
		List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> signBroken =
				new ArrayList<>(liveSeeds);
		int reverseIndex = findIndex(signBroken, "racingQuad", "reverse_drag_symmetry");
		double forwardSignedAxial = find(signBroken, "racingQuad", "forward_drag").signedAxialForceCoefficient();
		signBroken.set(reverseIndex, withSignedAxial(signBroken.get(reverseIndex), forwardSignedAxial));

		Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.MultiAxisCoefficientAcceptanceSummary testSummary =
				Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.gate(testSeeds);
		Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.MultiAxisCoefficientAcceptanceSummary liveSummary =
				Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.gate(liveSeeds);
		Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.MultiAxisCoefficientAcceptanceSummary signBrokenSummary =
				Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.gate(signBroken);

		assertFalse(testSummary.multiAxisGameplayFitAllowed());
		assertTrue(testSummary.allSeedsFitReady());
		assertFalse(testSummary.allSeedsLiveRuntime());
		assertTrue(liveSummary.multiAxisGameplayFitAllowed());
		assertFalse(signBrokenSummary.allForwardReverseSignConsistent());
		assertFalse(signBrokenSummary.multiAxisGameplayFitAllowed());
	}

	@Test
	void gateRejectsMissingUnexpectedDuplicateAndOutOfBoundsSeeds() {
		List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> liveSeeds =
				Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.audit(getClass().getClassLoader()).seeds()
						.stream().map(seed -> withRuntime(seed, "a4mc-live-calibration")).toList();

		Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.MultiAxisCoefficientAcceptanceSummary missing =
				Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.gate(liveSeeds.subList(0, 23));
		assertEquals(1, missing.missingSeedCount());
		assertFalse(missing.multiAxisGameplayFitAllowed());

		List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> unexpected =
				new ArrayList<>(liveSeeds);
		unexpected.set(0, withPresetName(liveSeeds.get(0), "unexpected"));
		Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.MultiAxisCoefficientAcceptanceSummary unexpectedSummary =
				Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.gate(unexpected);
		assertEquals(1, unexpectedSummary.missingSeedCount());
		assertEquals(1, unexpectedSummary.unexpectedSeedCount());
		assertFalse(unexpectedSummary.multiAxisGameplayFitAllowed());

		List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> duplicate =
				new ArrayList<>(liveSeeds);
		duplicate.set(1, liveSeeds.get(0));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.gate(duplicate));

		List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> outOfBounds =
				new ArrayList<>(liveSeeds);
		outOfBounds.set(0, withForceCoefficientX(outOfBounds.get(0), 40.0));
		assertFalse(Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.gate(outOfBounds)
				.multiAxisGameplayFitAllowed());
	}

	@Test
	void gateRejectsNullInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.gate(null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.MultiAxisCoefficientAcceptanceAudit audit =
				Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_static_airframe_multi_axis_coefficient_acceptance_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_static_airframe_multi_axis_coefficient_acceptance_gate_summary,all_scenarios,allowed_scenario_count,1,")));
	}

	private static Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.MultiAxisCoefficientAcceptanceScenario find(
			List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.MultiAxisCoefficientAcceptanceScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed find(
			List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> seeds,
			String presetName,
			String sweepName
	) {
		return seeds.stream()
				.filter(seed -> presetName.equals(seed.presetName()) && sweepName.equals(seed.sweepName()))
				.findFirst()
				.orElseThrow();
	}

	private static int findIndex(
			List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> seeds,
			String presetName,
			String sweepName
	) {
		for (int index = 0; index < seeds.size(); index++) {
			Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed seed =
					seeds.get(index);
			if (presetName.equals(seed.presetName()) && sweepName.equals(seed.sweepName())) {
				return index;
			}
		}
		throw new IllegalArgumentException("Seed not found: " + presetName + ":" + sweepName);
	}

	private static Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed withRuntime(
			Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed seed,
			String runtimeInfo
	) {
		return copy(seed, seed.presetName(), seed.sweepName(), seed.forceCoefficientX(),
				seed.signedAxialForceCoefficient(), runtimeInfo);
	}

	private static Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed withPresetName(
			Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed seed,
			String presetName
	) {
		return copy(seed, presetName, seed.sweepName(), seed.forceCoefficientX(),
				seed.signedAxialForceCoefficient(), seed.sourceRuntimeInfo());
	}

	private static Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed withForceCoefficientX(
			Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed seed,
			double forceCoefficientX
	) {
		return copy(seed, seed.presetName(), seed.sweepName(), forceCoefficientX,
				seed.signedAxialForceCoefficient(), seed.sourceRuntimeInfo());
	}

	private static Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed withSignedAxial(
			Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed seed,
			double signedAxialForceCoefficient
	) {
		return copy(seed, seed.presetName(), seed.sweepName(), seed.forceCoefficientX(),
				signedAxialForceCoefficient, seed.sourceRuntimeInfo());
	}

	private static Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed copy(
			Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed seed,
			String presetName,
			String sweepName,
			double forceCoefficientX,
			double signedAxialForceCoefficient,
			String runtimeInfo
	) {
		return new Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed(
				presetName,
				sweepName,
				seed.fitRole(),
				seed.coefficientFitReady(),
				seed.sourceRunAvailable(),
				seed.forwardDragFitSample(),
				seed.sideforceFitSample(),
				seed.liftFitSample(),
				seed.momentPressureCenterFitSample(),
				seed.airDensityKgM3(),
				seed.dynamicPressurePascals(),
				seed.projectedReferenceAreaSquareMeters(),
				seed.referenceLengthMeters(),
				forceCoefficientX,
				seed.forceCoefficientY(),
				seed.forceCoefficientZ(),
				signedAxialForceCoefficient,
				seed.sideForceCoefficient(),
				seed.liftCoefficient(),
				seed.momentCoefficientX(),
				seed.momentCoefficientY(),
				seed.momentCoefficientZ(),
				seed.momentCoefficientMagnitude(),
				seed.pressureCenterOffsetRatio(),
				seed.pressureCenterOffsetXRatio(),
				seed.pressureCenterOffsetYRatio(),
				seed.pressureCenterOffsetZRatio(),
				seed.sourceRunStatus(),
				runtimeInfo
		);
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_static_airframe_multi_axis_coefficient_acceptance_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
