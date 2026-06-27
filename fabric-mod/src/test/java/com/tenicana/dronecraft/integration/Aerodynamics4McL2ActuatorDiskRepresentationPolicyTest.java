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

class Aerodynamics4McL2ActuatorDiskRepresentationPolicyTest {
	@Test
	void auditBuildsStableActuatorDiskRepresentationPolicy() {
		Aerodynamics4McL2ActuatorDiskRepresentationPolicy.ActuatorDiskRepresentationPolicyAudit audit =
				Aerodynamics4McL2ActuatorDiskRepresentationPolicy.audit();

		assertEquals("A4MC-L2-Actuator-Disk-Representation-Policy-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("rotor disks open"));
		assertEquals(116, audit.packetMetricRowCount());
		assertEquals(5, audit.sourceReferenceCount());
		assertEquals(4, audit.presetSampleCount());
		assertEquals(25, audit.policyMetricCount());
		assertEquals(10, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(4, audit.policies().size());

		for (Aerodynamics4McL2ActuatorDiskRepresentationPolicy.ActuatorDiskRepresentationPolicy policy : audit.policies()) {
			assertEquals(4, policy.rotorCount());
			assertTrue(policy.openFraction() > 0.70);
			assertTrue(policy.minRotorOpenFraction() > 0.70);
			assertTrue(policy.maxRotorOpenFraction() <= 1.0);
			assertTrue(policy.openDiskAreaSquareMeters() > 0.0);
			assertTrue(policy.blockedDiskAreaSquareMeters() >= 0.0);
			assertEquals("keep_rotor_disk_open", policy.binarySolidMaskRotorDiskPolicy());
			assertFalse(policy.solidDiskMaskAllowed());
			assertTrue(policy.actuatorDiskRepresentationRequired());
			assertTrue(policy.porousOrBodyForceSourceRequired());
			assertFalse(policy.poweredSourceApiAvailable());
			assertTrue(policy.actuatorDiskSourceMapReady());
			assertTrue(policy.poweredHoverExperimentPlanReady());
			assertTrue(policy.poweredCruiseExperimentPlanReady());
			assertTrue(policy.poweredHoverAcceptanceGateRequired());
			assertTrue(policy.poweredCruiseAcceptanceGateRequired());
			assertFalse(policy.poweredHoverGameplayCouplingAllowed());
			assertFalse(policy.poweredCruiseGameplayCouplingAllowed());
			assertTrue(policy.overBlockageRiskIfSolidFilled());
			assertTrue(policy.validationBeforeRuntimeRequired());
			assertEquals("porous_or_body_force_source_api", policy.recommendedApiSurface());
			assertFalse(policy.runtimeMutationAllowed());
			assertEquals("BLOCKED", policy.status());
		}
		assertEquals(4, audit.extrema().policyCount());
		assertEquals(0, audit.extrema().solidDiskMaskAllowedCount());
		assertEquals(4, audit.extrema().porousOrBodyForceSourceRequiredCount());
		assertEquals(0, audit.extrema().poweredSourceApiAvailableCount());
		assertEquals(0, audit.extrema().poweredHoverGameplayCouplingAllowedCount());
		assertEquals(0, audit.extrema().poweredCruiseGameplayCouplingAllowedCount());
		assertEquals(4, audit.extrema().overBlockageRiskIfSolidFilledCount());
		assertEquals(0, audit.extrema().runtimeMutationAllowedCount());
		assertTrue(audit.extrema().minOpenFraction() > 0.70);
		assertTrue(audit.extrema().maxBlockedDiskAreaSquareMeters() >= 0.0);
	}

	@Test
	void policyMirrorsRotorDiskApertureGeometry() {
		Aerodynamics4McL2RotorDiskAperture.PresetApertureSummary racing =
				Aerodynamics4McL2RotorDiskAperture.audit().racingQuad();
		Aerodynamics4McL2ActuatorDiskRepresentationPolicy.ActuatorDiskRepresentationPolicy policy =
				Aerodynamics4McL2ActuatorDiskRepresentationPolicy.audit().policies().stream()
						.filter(candidate -> "racingQuad".equals(candidate.presetName()))
						.findFirst()
						.orElseThrow();

		assertEquals(racing.openFraction(), policy.openFraction(), 1.0e-12);
		assertEquals(racing.openDiskAreaSquareMeters(), policy.openDiskAreaSquareMeters(), 1.0e-12);
		assertEquals(racing.blockedDiskAreaSquareMeters(), policy.blockedDiskAreaSquareMeters(), 1.0e-12);
		assertEquals(racing.maxInPlaneInletSpeedMetersPerSecond(),
				policy.maxInPlaneInletSpeedMetersPerSecond(), 1.0e-12);
	}

	@Test
	void policyCanRepresentFutureReadyStateWithoutChangingSolidMaskRule() {
		Aerodynamics4McL2RotorDiskAperture.PresetApertureSummary aperture =
				Aerodynamics4McL2RotorDiskAperture.audit().apDrone();
		Aerodynamics4McL2ActuatorDiskRepresentationPolicy.ActuatorDiskRepresentationPolicy policy =
				Aerodynamics4McL2ActuatorDiskRepresentationPolicy.policy(aperture, true, true);

		assertTrue(policy.poweredSourceApiAvailable());
		assertTrue(policy.poweredHoverGameplayCouplingAllowed());
		assertTrue(policy.poweredCruiseGameplayCouplingAllowed());
		assertTrue(policy.runtimeMutationAllowed());
		assertEquals("READY_FOR_REVIEW", policy.status());
		assertEquals("keep_rotor_disk_open", policy.binarySolidMaskRotorDiskPolicy());
		assertFalse(policy.solidDiskMaskAllowed());
	}

	@Test
	void policyRejectsInvalidInput() {
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2ActuatorDiskRepresentationPolicy.policy(null, false, false));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2ActuatorDiskRepresentationPolicy.ActuatorDiskRepresentationPolicyAudit audit =
				Aerodynamics4McL2ActuatorDiskRepresentationPolicy.audit();
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_actuator_disk_representation_policy_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_actuator_disk_representation_policy_summary,all_presets,solid_disk_mask_allowed_count,0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_actuator_disk_representation_policy_preset,racingQuad,binary_solid_mask_rotor_disk_policy,keep_rotor_disk_open,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve("docs/data/a4mc_l2_actuator_disk_representation_policy_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
