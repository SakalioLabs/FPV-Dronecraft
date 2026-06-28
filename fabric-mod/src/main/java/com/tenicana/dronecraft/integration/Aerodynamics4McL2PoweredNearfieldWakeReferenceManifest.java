package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Nearfield-Wake-Reference-Manifest-Packet";
	public static final String CAVEAT =
			"Nearfield wake reference manifest lists only audit-reviewed hover and cruise wake reference artifacts; it remains blocked until the combined lab gate opens and never enables runtime coupling or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_COUNT = 7;
	public static final int SCENARIO_SAMPLE_COUNT = 2;
	public static final int ARTIFACT_SAMPLE_COUNT = 3;
	public static final int MANIFEST_ENTRY_COUNT = SCENARIO_SAMPLE_COUNT * ARTIFACT_SAMPLE_COUNT;
	public static final int ENTRY_METRIC_COUNT = 20;
	public static final int SUMMARY_METRIC_ROW_COUNT = 12;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ MANIFEST_ENTRY_COUNT * ENTRY_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private static final String CURRENT_SCENARIO = "current_nearfield_reference_manifest_blocked";
	private static final String READY_SCENARIO = "synthetic_nearfield_reference_manifest_ready";
	private static final String REFERENCE_TABLE_KIND = "reference-table";
	private static final String COMBINED_PACKAGE_KIND = "combined-reference-package";

	private Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest() {
	}

	public record PoweredNearfieldWakeReferenceManifestEntry(
			String scenarioName,
			String artifactId,
			String artifactKind,
			String sourcePacketId,
			String payloadKind,
			String exportScope,
			int expectedReferenceRowCount,
			int availableReferenceRowCount,
			int blockedReferenceRowCount,
			boolean labValidationAccepted,
			boolean errorBudgetReady,
			boolean handoffExportAllowed,
			boolean nearfieldPackageExportAllowed,
			boolean artifactExportAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			boolean playableReviewRequiredBeforeUse,
			String nextRequiredAction,
			String status,
			String message
	) {
	}

	public record PoweredNearfieldWakeReferenceManifestExtrema(
			int manifestEntryCount,
			int scenarioSampleCount,
			int artifactSampleCount,
			int currentArtifactExportAllowedCount,
			int readyArtifactExportAllowedCount,
			int currentTableAvailableReferenceRowCount,
			int readyTableAvailableReferenceRowCount,
			int currentCombinedPackageAvailableReferenceRowCount,
			int readyCombinedPackageAvailableReferenceRowCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			int playableReviewRequiredBeforeUseCount
	) {
	}

	public record PoweredNearfieldWakeReferenceManifestAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int scenarioSampleCount,
			int artifactSampleCount,
			int manifestEntryCount,
			int entryMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredNearfieldWakeReferenceManifestEntry> entries,
			PoweredNearfieldWakeReferenceManifestExtrema extrema
	) {
		public PoweredNearfieldWakeReferenceManifestAudit {
			entries = List.copyOf(entries);
		}
	}

	public static PoweredNearfieldWakeReferenceManifestAudit audit() {
		Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.PoweredNearfieldWakeReferenceAudit gateAudit =
				Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.audit();
		List<PoweredNearfieldWakeReferenceManifestEntry> entries = List.of(
				manifestEntries(CURRENT_SCENARIO,
						gate(gateAudit, "current_hover_and_cruise_reference_blocked")),
				manifestEntries(READY_SCENARIO,
						gate(gateAudit, "hover_and_cruise_reference_ready"))
		).stream()
				.flatMap(List::stream)
				.toList();
		return new PoweredNearfieldWakeReferenceManifestAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				SCENARIO_SAMPLE_COUNT,
				ARTIFACT_SAMPLE_COUNT,
				MANIFEST_ENTRY_COUNT,
				ENTRY_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				entries,
				extrema(entries)
		);
	}

	public static List<PoweredNearfieldWakeReferenceManifestEntry> manifestEntries(
			String scenarioName,
			Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.PoweredNearfieldWakeReferenceSummary summary
	) {
		if (scenarioName == null || scenarioName.isBlank()) {
			throw new IllegalArgumentException("scenarioName must not be blank.");
		}
		if (summary == null) {
			throw new IllegalArgumentException("summary must not be null.");
		}
		return List.of(
				entry(
						scenarioName,
						"hover_surface_wake_reference_table",
						REFERENCE_TABLE_KIND,
						Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceTable.SOURCE_ID,
						"aggregate-surface-wake-reference-table",
						"hover-ground-ceiling-surface-wake",
						summary.hoverExpectedReferenceRowCount(),
						summary.hoverLabValidationAccepted(),
						summary.hoverErrorBudgetReady(),
						summary.hoverReferenceMaterialExportAllowed(),
						summary.nearfieldReferencePackageExportAllowed(),
						summary.nextRequiredAction()
				),
				entry(
						scenarioName,
						"cruise_skew_wake_reference_table",
						REFERENCE_TABLE_KIND,
						Aerodynamics4McL2PoweredCruiseSkewWakeReferenceTable.SOURCE_ID,
						"aggregate-cruise-skew-wake-reference-table",
						"cruise-forward-skew-wake",
						summary.cruiseExpectedReferenceRowCount(),
						summary.cruiseLabValidationAccepted(),
						summary.cruiseErrorBudgetReady(),
						summary.cruiseReferenceMaterialExportAllowed(),
						summary.nearfieldReferencePackageExportAllowed(),
						summary.nextRequiredAction()
				),
				entry(
						scenarioName,
						"combined_nearfield_wake_reference_package",
						COMBINED_PACKAGE_KIND,
						Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.SOURCE_ID,
						summary.referencePayloadKind(),
						"combined-hover-surface-and-cruise-nearfield-wake",
						summary.totalExpectedReferenceRowCount(),
						summary.hoverLabValidationAccepted() && summary.cruiseLabValidationAccepted(),
						summary.hoverErrorBudgetReady() && summary.cruiseErrorBudgetReady(),
						summary.hoverReferenceMaterialExportAllowed() && summary.cruiseReferenceMaterialExportAllowed(),
						summary.nearfieldReferencePackageExportAllowed(),
						summary.nextRequiredAction()
				)
		);
	}

	private static PoweredNearfieldWakeReferenceManifestEntry entry(
			String scenarioName,
			String artifactId,
			String artifactKind,
			String sourcePacketId,
			String payloadKind,
			String exportScope,
			int expectedReferenceRowCount,
			boolean labValidationAccepted,
			boolean errorBudgetReady,
			boolean handoffExportAllowed,
			boolean nearfieldPackageExportAllowed,
			String nextRequiredAction
	) {
		boolean exportAllowed = labValidationAccepted
				&& errorBudgetReady
				&& handoffExportAllowed
				&& nearfieldPackageExportAllowed;
		int availableRows = exportAllowed ? expectedReferenceRowCount : 0;
		return new PoweredNearfieldWakeReferenceManifestEntry(
				scenarioName,
				artifactId,
				artifactKind,
				sourcePacketId,
				payloadKind,
				exportScope,
				expectedReferenceRowCount,
				availableRows,
				expectedReferenceRowCount - availableRows,
				labValidationAccepted,
				errorBudgetReady,
				handoffExportAllowed,
				nearfieldPackageExportAllowed,
				exportAllowed,
				false,
				false,
				true,
				nextRequiredAction,
				exportAllowed ? "READY" : "BLOCKED",
				exportAllowed
						? artifactId + "-ready-for-reviewed-export"
						: artifactId + "-blocked-by-nearfield-reference-gate"
		);
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

	private static PoweredNearfieldWakeReferenceManifestExtrema extrema(
			List<PoweredNearfieldWakeReferenceManifestEntry> entries
	) {
		int currentExports = 0;
		int readyExports = 0;
		int currentTableRows = 0;
		int readyTableRows = 0;
		int currentPackageRows = 0;
		int readyPackageRows = 0;
		int runtime = 0;
		int autoApply = 0;
		int playableReview = 0;
		for (PoweredNearfieldWakeReferenceManifestEntry entry : entries) {
			boolean readyScenario = READY_SCENARIO.equals(entry.scenarioName());
			boolean currentScenario = CURRENT_SCENARIO.equals(entry.scenarioName());
			if (entry.artifactExportAllowed()) {
				if (readyScenario) {
					readyExports++;
				}
				if (currentScenario) {
					currentExports++;
				}
			}
			if (REFERENCE_TABLE_KIND.equals(entry.artifactKind())) {
				if (readyScenario) {
					readyTableRows += entry.availableReferenceRowCount();
				}
				if (currentScenario) {
					currentTableRows += entry.availableReferenceRowCount();
				}
			}
			if (COMBINED_PACKAGE_KIND.equals(entry.artifactKind())) {
				if (readyScenario) {
					readyPackageRows += entry.availableReferenceRowCount();
				}
				if (currentScenario) {
					currentPackageRows += entry.availableReferenceRowCount();
				}
			}
			if (entry.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (entry.gameplayAutoApplyAllowed()) {
				autoApply++;
			}
			if (entry.playableReviewRequiredBeforeUse()) {
				playableReview++;
			}
		}
		return new PoweredNearfieldWakeReferenceManifestExtrema(
				entries.size(),
				SCENARIO_SAMPLE_COUNT,
				ARTIFACT_SAMPLE_COUNT,
				currentExports,
				readyExports,
				currentTableRows,
				readyTableRows,
				currentPackageRows,
				readyPackageRows,
				runtime,
				autoApply,
				playableReview
		);
	}
}
