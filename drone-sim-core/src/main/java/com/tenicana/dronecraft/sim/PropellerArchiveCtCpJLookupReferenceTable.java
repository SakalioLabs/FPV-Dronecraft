package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCtCpJLookupReferenceTable {
	public static final String SOURCE_ID = "User-Propeller-Archive-CT-CP-J-Lookup-Reference-Table-Packet";
	public static final String CAVEAT =
			"CT/CP/J lookup reference table exposes accepted query-reference rows only after lookup acceptance and compact-reference review; current rows keep zero weights and never enable runtime coupling or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int REFERENCE_ROW_COUNT = PropellerArchiveCtCpJLookupAcceptanceGate.TARGET_ROW_COUNT;
	public static final int SUMMARY_ROW_COUNT = 16;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ REFERENCE_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final String REFERENCE_PAYLOAD_KIND =
			"compact-propeller-ct-cp-j-lookup-reference";

	private PropellerArchiveCtCpJLookupReferenceTable() {
	}

	public record LookupReferenceRow(
			String presetName,
			String caseName,
			String performanceMatchId,
			String geometryMatchId,
			double queryAdvanceRatioJ,
			double queryRpm,
			double equivalentProjectMu,
			int minimumNeighborRows,
			boolean lookupAcceptanceReady,
			boolean compactReferenceReviewed,
			boolean referenceMaterialExportAllowed,
			boolean performanceReferenceRowAvailable,
			boolean fullSimulationReferenceRowAvailable,
			boolean staticAnchorReferenceRow,
			double ctReferenceWeight,
			double cpReferenceWeight,
			double etaReferenceWeight,
			double staticAnchorReferenceWeight,
			double fullSimulationReferenceWeight,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message,
			String referencePayloadKind,
			String sourceRuntimeInfo
	) {
	}

	public record LookupReferenceTableExtrema(
			int rowCount,
			int performanceReferenceRowAvailableCount,
			int fullSimulationReferenceRowAvailableCount,
			int performanceOnlyReferenceRowAvailableCount,
			int blockedRowCount,
			int staticAnchorReferenceRowCount,
			int staticAnchorReferenceAvailableCount,
			int maxMinimumNeighborRows,
			int totalMinimumNeighborRows,
			double maxCtReferenceWeight,
			double maxFullSimulationReferenceWeight,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount
	) {
	}

	public record CtCpJLookupReferenceTableAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int referenceRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<LookupReferenceRow> rows,
			LookupReferenceTableExtrema extrema
	) {
		public CtCpJLookupReferenceTableAudit {
			rows = List.copyOf(rows);
		}
	}

	public static CtCpJLookupReferenceTableAudit audit() {
		PropellerArchiveCtCpJLookupReferenceHandoff.LookupReferenceHandoffSummary current =
				handoff("current_acceptance_blocked");
		return audit(current);
	}

	public static CtCpJLookupReferenceTableAudit audit(
			PropellerArchiveCtCpJLookupReferenceHandoff.LookupReferenceHandoffSummary handoff
	) {
		if (handoff == null) {
			throw new IllegalArgumentException("handoff summary must not be null.");
		}
		List<LookupReferenceRow> rows = PropellerArchiveCtCpJLookupAcceptanceGate.targets()
				.stream()
				.map(target -> row(handoff, target))
				.toList();
		return new CtCpJLookupReferenceTableAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				REFERENCE_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				rows,
				extrema(rows)
		);
	}

	public static LookupReferenceRow row(String presetName, String caseName) {
		return audit().rows().stream()
				.filter(row -> row.presetName().equals(presetName) && row.caseName().equals(caseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown CT/CP/J lookup reference row: " + presetName + " / " + caseName));
	}

	public static LookupReferenceRow row(
			PropellerArchiveCtCpJLookupReferenceHandoff.LookupReferenceHandoffSummary handoff,
			PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceTarget target
	) {
		if (handoff == null || target == null) {
			throw new IllegalArgumentException("handoff and target are required.");
		}
		PropellerArchiveCtCpJLookupInterpolationPolicy.QueryInterpolationContract contract =
				PropellerArchiveCtCpJLookupInterpolationPolicy.contract(target.presetName(), target.caseName());
		boolean exportAllowed = handoff.referenceMaterialExportAllowed();
		boolean performanceAvailable = exportAllowed;
		boolean fullSimulation = target.downstreamUse().startsWith("full-simulation");
		boolean fullSimulationAvailable = exportAllowed && fullSimulation;
		double performanceWeight = performanceAvailable ? 1.0 : 0.0;
		double staticWeight = performanceAvailable && target.requiresStaticAnchorPreservation() ? 1.0 : 0.0;
		double fullWeight = fullSimulationAvailable ? 1.0 : 0.0;
		return new LookupReferenceRow(
				target.presetName(),
				target.caseName(),
				contract.performanceMatchId(),
				contract.geometryMatchId(),
				contract.queryAdvanceRatioJ(),
				contract.queryRpm(),
				contract.queryAdvanceRatioJ() / Math.PI,
				target.minNeighborRows(),
				handoff.lookupAcceptanceReady(),
				handoff.compactReferenceReviewed(),
				exportAllowed,
				performanceAvailable,
				fullSimulationAvailable,
				target.requiresStaticAnchorPreservation(),
				performanceWeight,
				performanceWeight,
				performanceWeight,
				staticWeight,
				fullWeight,
				false,
				false,
				performanceAvailable ? "AVAILABLE" : "BLOCKED",
				messageFor(handoff, fullSimulationAvailable, fullSimulation),
				REFERENCE_PAYLOAD_KIND,
				handoff.sourceRuntimeInfo()
		);
	}

	private static PropellerArchiveCtCpJLookupReferenceHandoff.LookupReferenceHandoffSummary handoff(
			String scenarioName
	) {
		return PropellerArchiveCtCpJLookupReferenceHandoff.audit()
				.scenarios()
				.stream()
				.filter(scenario -> scenarioName.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static LookupReferenceTableExtrema extrema(List<LookupReferenceRow> rows) {
		int performanceAvailable = 0;
		int fullSimulationAvailable = 0;
		int staticRows = 0;
		int staticAvailable = 0;
		int runtime = 0;
		int gameplay = 0;
		int maxNeighbors = 0;
		int totalNeighbors = 0;
		double maxCtWeight = 0.0;
		double maxFullWeight = 0.0;
		for (LookupReferenceRow row : rows) {
			if (row.performanceReferenceRowAvailable()) {
				performanceAvailable++;
			}
			if (row.fullSimulationReferenceRowAvailable()) {
				fullSimulationAvailable++;
			}
			if (row.staticAnchorReferenceRow()) {
				staticRows++;
				if (row.staticAnchorReferenceWeight() > 0.0) {
					staticAvailable++;
				}
			}
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
			maxNeighbors = Math.max(maxNeighbors, row.minimumNeighborRows());
			totalNeighbors += row.minimumNeighborRows();
			maxCtWeight = Math.max(maxCtWeight, row.ctReferenceWeight());
			maxFullWeight = Math.max(maxFullWeight, row.fullSimulationReferenceWeight());
		}
		return new LookupReferenceTableExtrema(
				rows.size(),
				performanceAvailable,
				fullSimulationAvailable,
				performanceAvailable - fullSimulationAvailable,
				rows.size() - performanceAvailable,
				staticRows,
				staticAvailable,
				maxNeighbors,
				totalNeighbors,
				maxCtWeight,
				maxFullWeight,
				runtime,
				gameplay
		);
	}

	private static String messageFor(
			PropellerArchiveCtCpJLookupReferenceHandoff.LookupReferenceHandoffSummary handoff,
			boolean fullSimulationAvailable,
			boolean fullSimulationTarget
	) {
		if (!handoff.lookupAcceptanceReady()) {
			return "lookup-acceptance-not-ready";
		}
		if (!handoff.compactReferenceReviewed()) {
			return "lookup-reference-review-missing";
		}
		if (!fullSimulationTarget) {
			return "performance-reference-only-full-simulation-blocked";
		}
		if (!fullSimulationAvailable) {
			return "full-simulation-reference-export-blocked";
		}
		return "ct-cp-j-lookup-reference-row-available";
	}
}
