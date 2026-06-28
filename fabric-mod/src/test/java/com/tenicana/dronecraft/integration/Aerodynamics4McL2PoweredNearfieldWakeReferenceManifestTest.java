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

class Aerodynamics4McL2PoweredNearfieldWakeReferenceManifestTest {
	@Test
	void auditBuildsBlockedCurrentAndReadyTargetManifest() {
		Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.PoweredNearfieldWakeReferenceManifestAudit audit =
				Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.audit();

		assertEquals("A4MC-L2-Powered-Nearfield-Wake-Reference-Manifest-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("combined lab gate"));
		assertEquals(140, audit.packetMetricRowCount());
		assertEquals(7, audit.sourceReferenceCount());
		assertEquals(2, audit.scenarioSampleCount());
		assertEquals(3, audit.artifactSampleCount());
		assertEquals(6, audit.manifestEntryCount());
		assertEquals(20, audit.entryMetricCount());
		assertEquals(12, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(6, audit.entries().size());

		List<Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.PoweredNearfieldWakeReferenceManifestEntry>
				current = entries(audit.entries(), "current_nearfield_reference_manifest_blocked");
		assertEquals(3, current.size());
		for (Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.PoweredNearfieldWakeReferenceManifestEntry entry
				: current) {
			assertFalse(entry.labValidationAccepted());
			assertFalse(entry.errorBudgetReady());
			assertFalse(entry.handoffExportAllowed());
			assertFalse(entry.nearfieldPackageExportAllowed());
			assertFalse(entry.artifactExportAllowed());
			assertEquals(0, entry.availableReferenceRowCount());
			assertEquals(entry.expectedReferenceRowCount(), entry.blockedReferenceRowCount());
			assertFalse(entry.runtimeCouplingAllowed());
			assertFalse(entry.gameplayAutoApplyAllowed());
			assertTrue(entry.playableReviewRequiredBeforeUse());
			assertEquals("complete-hover-surface-and-cruise-skew-wake-reference-handoffs",
					entry.nextRequiredAction());
			assertEquals("BLOCKED", entry.status());
			assertTrue(entry.message().endsWith("-blocked-by-nearfield-reference-gate"));
		}

		Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.PoweredNearfieldWakeReferenceManifestEntry
				currentCombined = entry(current, "combined_nearfield_wake_reference_package");
		assertEquals("combined-reference-package", currentCombined.artifactKind());
		assertEquals(80, currentCombined.expectedReferenceRowCount());
		assertEquals(80, currentCombined.blockedReferenceRowCount());

		List<Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.PoweredNearfieldWakeReferenceManifestEntry>
				ready = entries(audit.entries(), "synthetic_nearfield_reference_manifest_ready");
		assertEquals(3, ready.size());
		for (Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.PoweredNearfieldWakeReferenceManifestEntry entry
				: ready) {
			assertTrue(entry.labValidationAccepted());
			assertTrue(entry.errorBudgetReady());
			assertTrue(entry.handoffExportAllowed());
			assertTrue(entry.nearfieldPackageExportAllowed());
			assertTrue(entry.artifactExportAllowed());
			assertEquals(entry.expectedReferenceRowCount(), entry.availableReferenceRowCount());
			assertEquals(0, entry.blockedReferenceRowCount());
			assertFalse(entry.runtimeCouplingAllowed());
			assertFalse(entry.gameplayAutoApplyAllowed());
			assertTrue(entry.playableReviewRequiredBeforeUse());
			assertEquals("nearfield-wake-reference-package-ready-for-reviewed-export",
					entry.nextRequiredAction());
			assertEquals("READY", entry.status());
			assertTrue(entry.message().endsWith("-ready-for-reviewed-export"));
		}

		assertEquals(32, entry(ready, "hover_surface_wake_reference_table").availableReferenceRowCount());
		assertEquals(48, entry(ready, "cruise_skew_wake_reference_table").availableReferenceRowCount());
		assertEquals(80, entry(ready, "combined_nearfield_wake_reference_package").availableReferenceRowCount());

		assertEquals(6, audit.extrema().manifestEntryCount());
		assertEquals(2, audit.extrema().scenarioSampleCount());
		assertEquals(3, audit.extrema().artifactSampleCount());
		assertEquals(0, audit.extrema().currentArtifactExportAllowedCount());
		assertEquals(3, audit.extrema().readyArtifactExportAllowedCount());
		assertEquals(0, audit.extrema().currentTableAvailableReferenceRowCount());
		assertEquals(80, audit.extrema().readyTableAvailableReferenceRowCount());
		assertEquals(0, audit.extrema().currentCombinedPackageAvailableReferenceRowCount());
		assertEquals(80, audit.extrema().readyCombinedPackageAvailableReferenceRowCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
		assertEquals(6, audit.extrema().playableReviewRequiredBeforeUseCount());
	}

	@Test
	void manifestEntriesFollowCombinedGateRatherThanSingleTableReadiness() {
		Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.PoweredNearfieldWakeReferenceAudit gateAudit =
				Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.audit();
		Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.PoweredNearfieldWakeReferenceSummary hoverReadyCruiseBlocked =
				gate(gateAudit, "hover_ready_cruise_reference_blocked");

		List<Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.PoweredNearfieldWakeReferenceManifestEntry> entries =
				Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.manifestEntries(
						"hover_ready_cruise_blocked_manifest", hoverReadyCruiseBlocked);

		Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.PoweredNearfieldWakeReferenceManifestEntry hover =
				entry(entries, "hover_surface_wake_reference_table");
		assertTrue(hover.labValidationAccepted());
		assertTrue(hover.errorBudgetReady());
		assertTrue(hover.handoffExportAllowed());
		assertFalse(hover.nearfieldPackageExportAllowed());
		assertFalse(hover.artifactExportAllowed());
		assertEquals(0, hover.availableReferenceRowCount());
		assertEquals(32, hover.blockedReferenceRowCount());
		assertEquals("complete-cruise-skew-wake-reference-handoff", hover.nextRequiredAction());
	}

	@Test
	void rejectsInvalidInputs() {
		Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.PoweredNearfieldWakeReferenceSummary ready =
				gate(Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.audit(),
						"hover_and_cruise_reference_ready");

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.manifestEntries("", ready));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.manifestEntries("ready", null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.PoweredNearfieldWakeReferenceManifestAudit audit =
				Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_nearfield_wake_reference_manifest_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_nearfield_wake_reference_manifest_summary,all_entries,ready_combined_package_available_reference_row_count,80,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_nearfield_wake_reference_manifest,current_nearfield_reference_manifest_blocked:combined_nearfield_wake_reference_package,artifact_export_allowed,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_nearfield_wake_reference_manifest,synthetic_nearfield_reference_manifest_ready:combined_nearfield_wake_reference_package,available_reference_row_count,80,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_nearfield_wake_reference_manifest,synthetic_nearfield_reference_manifest_ready:combined_nearfield_wake_reference_package,gameplay_auto_apply_allowed,false,")));
	}

	private static Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.PoweredNearfieldWakeReferenceSummary gate(
			Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.PoweredNearfieldWakeReferenceAudit audit,
			String scenarioName
	) {
		return audit.scenarios().stream()
				.filter(scenario -> scenarioName.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static List<Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.PoweredNearfieldWakeReferenceManifestEntry> entries(
			List<Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.PoweredNearfieldWakeReferenceManifestEntry> entries,
			String scenarioName
	) {
		return entries.stream()
				.filter(entry -> scenarioName.equals(entry.scenarioName()))
				.toList();
	}

	private static Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.PoweredNearfieldWakeReferenceManifestEntry entry(
			List<Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest.PoweredNearfieldWakeReferenceManifestEntry> entries,
			String artifactId
	) {
		return entries.stream()
				.filter(entry -> artifactId.equals(entry.artifactId()))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_nearfield_wake_reference_manifest_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
