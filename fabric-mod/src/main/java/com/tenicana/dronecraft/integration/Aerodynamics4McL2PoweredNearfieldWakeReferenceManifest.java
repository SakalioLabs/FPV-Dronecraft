package com.tenicana.dronecraft.integration;

import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable;
import java.util.List;

public final class Aerodynamics4McL2PoweredNearfieldWakeReferenceManifest {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Nearfield-Wake-Reference-Manifest-Packet";
	public static final String CAVEAT =
			"Nearfield wake reference manifest lists only audit-reviewed hover, cruise, OpenFOAM rotor-response, and inherited curve-shape diagnostic reference artifacts; it remains blocked until the combined lab gate opens and never enables runtime coupling or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_COUNT = 10;
	public static final int SCENARIO_SAMPLE_COUNT = 2;
	public static final int ARTIFACT_SAMPLE_COUNT = 4;
	public static final int MANIFEST_ENTRY_COUNT = SCENARIO_SAMPLE_COUNT * ARTIFACT_SAMPLE_COUNT;
	public static final int ENTRY_METRIC_COUNT = 28;
	public static final int SUMMARY_METRIC_ROW_COUNT = 21;
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
			int openFoamSolverQualityBlockerCount,
			int openFoamSolverQualityBlockerRowCount,
			String openFoamSolverQualityNextRequiredAction,
			int openFoamArchiveCurveShapeGuardInheritedReferenceCount,
			int openFoamNegativeThrustTailReferenceCount,
			double openFoamArchiveCurveEtaFormulaResidual,
			double openFoamArchiveCurveCtIncrease,
			int openFoamArchiveCurveShapeGuardCompleteRowCount,
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
			int currentOpenFoamAvailableReferenceRowCount,
			int readyOpenFoamAvailableReferenceRowCount,
			int currentCombinedPackageAvailableReferenceRowCount,
			int readyCombinedPackageAvailableReferenceRowCount,
			int maxOpenFoamSolverQualityBlockerCount,
			int maxOpenFoamSolverQualityBlockerRowCount,
			int maxOpenFoamArchiveCurveShapeGuardInheritedReferenceCount,
			int maxOpenFoamNegativeThrustTailReferenceCount,
			double maxOpenFoamArchiveCurveEtaFormulaResidual,
			double maxOpenFoamArchiveCurveCtIncrease,
			int maxOpenFoamArchiveCurveShapeGuardCompleteRowCount,
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
						gate(gateAudit, "current_hover_cruise_and_openfoam_reference_blocked")),
				manifestEntries(READY_SCENARIO,
						gate(gateAudit, "hover_cruise_and_openfoam_reference_ready"))
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
						0,
						0,
						"openfoam-solver-quality-blockers-clear",
						0,
						0,
						0.0,
						0.0,
						0,
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
						0,
						0,
						"openfoam-solver-quality-blockers-clear",
						0,
						0,
						0.0,
						0.0,
						0,
						summary.nextRequiredAction()
				),
				entry(
						scenarioName,
						"openfoam_dimensional_rotor_reference_table",
						REFERENCE_TABLE_KIND,
						PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.SOURCE_ID,
						summary.openFoamReferencePayloadKind(),
						"ct-cp-j-openfoam-dimensional-rotor-response",
						summary.openFoamExpectedReferenceRowCount(),
						summary.openFoamDimensionalSupportReady(),
						summary.openFoamSolverQualityContractReady()
								&& summary.openFoamDimensionalReferenceReviewed(),
						summary.openFoamReferenceMaterialExportAllowed(),
						summary.nearfieldReferencePackageExportAllowed(),
						summary.openFoamSolverQualityBlockerCount(),
						summary.openFoamSolverQualityBlockerRowCount(),
						summary.openFoamSolverQualityNextRequiredAction(),
						summary.openFoamArchiveCurveShapeGuardInheritedReferenceCount(),
						summary.openFoamNegativeThrustTailReferenceCount(),
						summary.openFoamMaxArchiveCurveEtaFormulaResidual(),
						summary.openFoamMaxArchiveCurveCtIncrease(),
						summary.openFoamArchiveCurveShapeGuardCompleteRowCount(),
						summary.nextRequiredAction()
				),
				entry(
						scenarioName,
						"combined_nearfield_wake_reference_package",
						COMBINED_PACKAGE_KIND,
						Aerodynamics4McL2PoweredNearfieldWakeReferenceGate.SOURCE_ID,
						summary.referencePayloadKind(),
						"combined-hover-surface-cruise-skew-and-openfoam-rotor-reference",
						summary.totalExpectedReferenceRowCount(),
						summary.hoverLabValidationAccepted()
								&& summary.cruiseLabValidationAccepted()
								&& summary.openFoamDimensionalSupportReady(),
						summary.hoverErrorBudgetReady()
								&& summary.cruiseErrorBudgetReady()
								&& summary.openFoamSolverQualityContractReady()
								&& summary.openFoamDimensionalReferenceReviewed(),
						summary.hoverReferenceMaterialExportAllowed()
								&& summary.cruiseReferenceMaterialExportAllowed()
								&& summary.openFoamReferenceMaterialExportAllowed(),
						summary.nearfieldReferencePackageExportAllowed(),
						summary.openFoamSolverQualityBlockerCount(),
						summary.openFoamSolverQualityBlockerRowCount(),
						summary.openFoamSolverQualityNextRequiredAction(),
						summary.openFoamArchiveCurveShapeGuardInheritedReferenceCount(),
						summary.openFoamNegativeThrustTailReferenceCount(),
						summary.openFoamMaxArchiveCurveEtaFormulaResidual(),
						summary.openFoamMaxArchiveCurveCtIncrease(),
						summary.openFoamArchiveCurveShapeGuardCompleteRowCount(),
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
			int openFoamSolverQualityBlockerCount,
			int openFoamSolverQualityBlockerRowCount,
			String openFoamSolverQualityNextRequiredAction,
			int openFoamArchiveCurveShapeGuardInheritedReferenceCount,
			int openFoamNegativeThrustTailReferenceCount,
			double openFoamArchiveCurveEtaFormulaResidual,
			double openFoamArchiveCurveCtIncrease,
			int openFoamArchiveCurveShapeGuardCompleteRowCount,
			String nextRequiredAction
	) {
		if (openFoamSolverQualityBlockerCount < 0 || openFoamSolverQualityBlockerRowCount < 0) {
			throw new IllegalArgumentException("OpenFOAM solver-quality blocker counts must be non-negative.");
		}
		if (openFoamArchiveCurveShapeGuardInheritedReferenceCount < 0
				|| openFoamNegativeThrustTailReferenceCount < 0
				|| openFoamArchiveCurveShapeGuardCompleteRowCount < 0) {
			throw new IllegalArgumentException("OpenFOAM archive curve-shape counts must be non-negative.");
		}
		if (!Double.isFinite(openFoamArchiveCurveEtaFormulaResidual)
				|| openFoamArchiveCurveEtaFormulaResidual < 0.0
				|| !Double.isFinite(openFoamArchiveCurveCtIncrease)
				|| openFoamArchiveCurveCtIncrease < 0.0) {
			throw new IllegalArgumentException("OpenFOAM archive curve-shape residuals must be finite.");
		}
		if (openFoamArchiveCurveShapeGuardCompleteRowCount > expectedReferenceRowCount) {
			throw new IllegalArgumentException(
					"OpenFOAM archive curve-shape complete rows must not exceed expected rows.");
		}
		if (openFoamSolverQualityNextRequiredAction == null || openFoamSolverQualityNextRequiredAction.isBlank()) {
			throw new IllegalArgumentException("OpenFOAM solver-quality next required action must not be blank.");
		}
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
				openFoamSolverQualityBlockerCount,
				openFoamSolverQualityBlockerRowCount,
				openFoamSolverQualityNextRequiredAction,
				openFoamArchiveCurveShapeGuardInheritedReferenceCount,
				openFoamNegativeThrustTailReferenceCount,
				openFoamArchiveCurveEtaFormulaResidual,
				openFoamArchiveCurveCtIncrease,
				openFoamArchiveCurveShapeGuardCompleteRowCount,
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
		int currentOpenFoamRows = 0;
		int readyOpenFoamRows = 0;
		int currentPackageRows = 0;
		int readyPackageRows = 0;
		int maxQualityBlockers = 0;
		int maxQualityBlockedRows = 0;
		int maxShapeInherited = 0;
		int maxNegativeTail = 0;
		double maxShapeEta = 0.0;
		double maxShapeCt = 0.0;
		int maxShapeCompleteRows = 0;
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
			if ("openfoam_dimensional_rotor_reference_table".equals(entry.artifactId())) {
				if (readyScenario) {
					readyOpenFoamRows += entry.availableReferenceRowCount();
				}
				if (currentScenario) {
					currentOpenFoamRows += entry.availableReferenceRowCount();
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
			maxQualityBlockers = Math.max(maxQualityBlockers, entry.openFoamSolverQualityBlockerCount());
			maxQualityBlockedRows = Math.max(maxQualityBlockedRows,
					entry.openFoamSolverQualityBlockerRowCount());
			maxShapeInherited = Math.max(maxShapeInherited,
					entry.openFoamArchiveCurveShapeGuardInheritedReferenceCount());
			maxNegativeTail = Math.max(maxNegativeTail, entry.openFoamNegativeThrustTailReferenceCount());
			maxShapeEta = Math.max(maxShapeEta, entry.openFoamArchiveCurveEtaFormulaResidual());
			maxShapeCt = Math.max(maxShapeCt, entry.openFoamArchiveCurveCtIncrease());
			maxShapeCompleteRows = Math.max(maxShapeCompleteRows,
					entry.openFoamArchiveCurveShapeGuardCompleteRowCount());
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
				currentOpenFoamRows,
				readyOpenFoamRows,
				currentPackageRows,
				readyPackageRows,
				maxQualityBlockers,
				maxQualityBlockedRows,
				maxShapeInherited,
				maxNegativeTail,
				maxShapeEta,
				maxShapeCt,
				maxShapeCompleteRows,
				runtime,
				autoApply,
				playableReview
		);
	}
}
