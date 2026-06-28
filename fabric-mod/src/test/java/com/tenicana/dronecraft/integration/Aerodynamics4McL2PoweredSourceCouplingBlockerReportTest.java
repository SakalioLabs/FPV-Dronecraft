package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class Aerodynamics4McL2PoweredSourceCouplingBlockerReportTest {
	@Test
	void auditBuildsStableCouplingBlockerScenarios() {
		Aerodynamics4McL2PoweredSourceCouplingBlockerReport.PoweredSourceCouplingBlockerAudit audit =
				Aerodynamics4McL2PoweredSourceCouplingBlockerReport.audit(getClass().getClassLoader());

		assertEquals("A4MC-L2-Powered-Source-Coupling-Blocker-Report-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("does not enable runtime coupling"));
		assertEquals(104, audit.packetMetricRowCount());
		assertEquals(5, audit.sourceReferenceCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(22, audit.scenarioMetricCount());
		assertEquals(10, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(4, audit.scenarios().size());

		Aerodynamics4McL2PoweredSourceCouplingBlockerReport.PoweredSourceCouplingBlockerSummary current =
				find(audit.scenarios(), "current_handoff_and_policy_blocked").summary();
		assertFalse(current.runtimePoweredSourceCouplingAllowed());
		assertEquals(4, current.blockerCount());
		assertTrue(current.acceptanceHandoffBlocker());
		assertTrue(current.policyRuntimeMutationBlocker());
		assertTrue(current.poweredSourceApiBlocker());
		assertTrue(current.gameplayCouplingBlocker());
		assertFalse(current.solidDiskMaskBlocker());
		assertFalse(current.rotorDiskMaskPolicyBlocker());
		assertFalse(current.hoverAcceptanceHandoffReady());
		assertFalse(current.cruiseAcceptanceHandoffReady());
		assertEquals(0, current.readyHandoffCount());
		assertEquals(2, current.expectedHandoffCount());
		assertEquals(4, current.policyCount());
		assertEquals(0, current.runtimeMutationAllowedPolicyCount());
		assertEquals(0, current.poweredSourceApiAvailablePolicyCount());
		assertEquals(4, current.keepRotorDiskOpenPolicyCount());
		assertEquals("complete-hover-and-cruise-powered-source-acceptance-handoffs",
				current.nextRequiredAction());
		assertEquals("BLOCKED", current.status());
		assertEquals("powered-source-coupling-blocked", current.message());

		Aerodynamics4McL2PoweredSourceCouplingBlockerReport.PoweredSourceCouplingBlockerSummary handoffsOnly =
				find(audit.scenarios(), "handoffs_ready_policy_blocked").summary();
		assertEquals(3, handoffsOnly.blockerCount());
		assertFalse(handoffsOnly.acceptanceHandoffBlocker());
		assertTrue(handoffsOnly.poweredSourceApiBlocker());
		assertEquals("wait-for-porous-or-body-force-powered-source-api",
				handoffsOnly.nextRequiredAction());

		Aerodynamics4McL2PoweredSourceCouplingBlockerReport.PoweredSourceCouplingBlockerSummary policyOnly =
				find(audit.scenarios(), "policy_ready_handoffs_blocked").summary();
		assertEquals(1, policyOnly.blockerCount());
		assertTrue(policyOnly.acceptanceHandoffBlocker());
		assertFalse(policyOnly.poweredSourceApiBlocker());

		Aerodynamics4McL2PoweredSourceCouplingBlockerReport.PoweredSourceCouplingBlockerSummary ready =
				find(audit.scenarios(), "handoffs_and_policy_ready").summary();
		assertTrue(ready.runtimePoweredSourceCouplingAllowed());
		assertEquals(0, ready.blockerCount());
		assertEquals("READY", ready.status());
		assertEquals("powered-source-coupling-clear", ready.message());
		assertEquals("runtime-powered-source-coupling-ready-for-reviewed-activation",
				ready.nextRequiredAction());

		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().readyScenarioCount());
		assertEquals(3, audit.extrema().blockedScenarioCount());
		assertEquals(4, audit.extrema().maxBlockerCount());
		assertEquals(2, audit.extrema().acceptanceHandoffBlockerScenarioCount());
		assertEquals(2, audit.extrema().policyRuntimeMutationBlockerScenarioCount());
		assertEquals(2, audit.extrema().poweredSourceApiBlockerScenarioCount());
		assertEquals(2, audit.extrema().gameplayCouplingBlockerScenarioCount());
		assertEquals(0, audit.extrema().solidDiskMaskBlockerScenarioCount());
		assertEquals(0, audit.extrema().rotorDiskMaskPolicyBlockerScenarioCount());
	}

	@Test
	void reportSeparatesSolidDiskMaskFromAcceptanceAndSourceApiBlockers() {
		Aerodynamics4McL2PoweredSourceCouplingReadinessGate.PoweredSourceCouplingReadinessSummary solidDisk =
				Aerodynamics4McL2PoweredSourceCouplingReadinessGate.gate(
						List.of(readyHandoff("hover"), readyHandoff("cruise")),
						List.of(solidDiskPolicy(readyPolicies().get(0))));

		Aerodynamics4McL2PoweredSourceCouplingBlockerReport.PoweredSourceCouplingBlockerSummary report =
				Aerodynamics4McL2PoweredSourceCouplingBlockerReport.report(solidDisk);

		assertFalse(report.runtimePoweredSourceCouplingAllowed());
		assertFalse(report.acceptanceHandoffBlocker());
		assertFalse(report.poweredSourceApiBlocker());
		assertFalse(report.policyRuntimeMutationBlocker());
		assertFalse(report.gameplayCouplingBlocker());
		assertTrue(report.solidDiskMaskBlocker());
		assertTrue(report.rotorDiskMaskPolicyBlocker());
		assertEquals(2, report.blockerCount());
		assertEquals("keep-rotor-disks-open-and-reject-solid-disk-masks",
				report.nextRequiredAction());
	}

	@Test
	void rejectsInvalidInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceCouplingBlockerReport.audit((ClassLoader) null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceCouplingBlockerReport.audit(
						(Aerodynamics4McL2PoweredSourceCouplingReadinessGate.PoweredSourceCouplingReadinessAudit) null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceCouplingBlockerReport.report(null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredSourceCouplingBlockerReport.PoweredSourceCouplingBlockerAudit audit =
				Aerodynamics4McL2PoweredSourceCouplingBlockerReport.audit(getClass().getClassLoader());
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_powered_source_coupling_blocker_report_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_coupling_blocker_report_summary,all_scenarios,max_blocker_count,4,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_coupling_blocker_report_scenario,current_handoff_and_policy_blocked,blocker_count,4,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_coupling_blocker_report_scenario,handoffs_and_policy_ready,runtime_powered_source_coupling_allowed,true,")));
	}

	private static Aerodynamics4McL2PoweredSourceCouplingBlockerReport.PoweredSourceCouplingBlockerScenario find(
			List<Aerodynamics4McL2PoweredSourceCouplingBlockerReport.PoweredSourceCouplingBlockerScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary readyHandoff(
			String spinState
	) {
		boolean hover = "hover".equals(spinState);
		return new Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary(
				spinState,
				hover ? Aerodynamics4McL2PoweredHoverValidation.SOURCE_ID : Aerodynamics4McL2PoweredCruiseValidation.SOURCE_ID,
				hover ? Aerodynamics4McL2PoweredHoverAcceptanceGate.SOURCE_ID : Aerodynamics4McL2PoweredCruiseAcceptanceGate.SOURCE_ID,
				4,
				4,
				4,
				4,
				4,
				4,
				0,
				0,
				0,
				0,
				0.0,
				0.0,
				true,
				true,
				true,
				"READY",
				"acceptance-handoff-ready"
		);
	}

	private static List<Aerodynamics4McL2ActuatorDiskRepresentationPolicy.ActuatorDiskRepresentationPolicy> readyPolicies() {
		return Aerodynamics4McL2RotorDiskAperture.audit().presets().stream()
				.map(aperture -> Aerodynamics4McL2ActuatorDiskRepresentationPolicy.policy(aperture, true, true))
				.toList();
	}

	private static Aerodynamics4McL2ActuatorDiskRepresentationPolicy.ActuatorDiskRepresentationPolicy solidDiskPolicy(
			Aerodynamics4McL2ActuatorDiskRepresentationPolicy.ActuatorDiskRepresentationPolicy policy
	) {
		return new Aerodynamics4McL2ActuatorDiskRepresentationPolicy.ActuatorDiskRepresentationPolicy(
				policy.presetName(),
				policy.rotorCount(),
				policy.openFraction(),
				policy.minRotorOpenFraction(),
				policy.maxRotorOpenFraction(),
				policy.openDiskAreaSquareMeters(),
				policy.blockedDiskAreaSquareMeters(),
				policy.maxAxialInletSpeedMetersPerSecond(),
				policy.maxInPlaneInletSpeedMetersPerSecond(),
				"solid_disk_filled",
				true,
				policy.actuatorDiskRepresentationRequired(),
				policy.porousOrBodyForceSourceRequired(),
				policy.poweredSourceApiAvailable(),
				policy.actuatorDiskSourceMapReady(),
				policy.poweredHoverExperimentPlanReady(),
				policy.poweredCruiseExperimentPlanReady(),
				policy.poweredHoverAcceptanceGateRequired(),
				policy.poweredCruiseAcceptanceGateRequired(),
				policy.poweredHoverGameplayCouplingAllowed(),
				policy.poweredCruiseGameplayCouplingAllowed(),
				policy.overBlockageRiskIfSolidFilled(),
				policy.validationBeforeRuntimeRequired(),
				policy.recommendedApiSurface(),
				policy.runtimeMutationAllowed(),
				policy.status()
		);
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_source_coupling_blocker_report_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
