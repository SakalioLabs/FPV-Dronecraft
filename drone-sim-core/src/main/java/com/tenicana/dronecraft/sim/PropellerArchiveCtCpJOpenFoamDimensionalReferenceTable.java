package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-OpenFOAM-Dimensional-Reference-Table-Packet";
	public static final String CAVEAT =
			"OpenFOAM dimensional reference table materializes the six geometry-backed SI CFD reference rows after the dimensional reference handoff; current rows keep zero weights and never enable runtime coupling or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 8;
	public static final int REFERENCE_ROW_COUNT = 6;
	public static final int SUMMARY_ROW_COUNT = 15;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ REFERENCE_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final String REFERENCE_PAYLOAD_KIND =
			PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.REFERENCE_PAYLOAD_KIND;

	private PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable() {
	}

	public record OpenFoamDimensionalReferenceRow(
			String presetName,
			String caseName,
			String sourceArchiveSha256,
			String solverFamily,
			String meshGeometryId,
			double queryAdvanceRatioJ,
			double queryRpm,
			double equivalentProjectMu,
			boolean staticAnchorReferenceRow,
			boolean lookupExecutionContractReady,
			boolean dimensionalSupportReady,
			boolean dimensionalReferenceReviewed,
			boolean referenceMaterialExportAllowed,
			boolean openFoamDimensionalReferenceRowAvailable,
			double thrustReferenceWeight,
			double shaftPowerReferenceWeight,
			double shaftTorqueReferenceWeight,
			double inducedVelocityReferenceWeight,
			double momentumPowerReferenceWeight,
			double residualReferenceWeight,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message,
			String referencePayloadKind,
			String sourceRuntimeInfo
	) {
	}

	public record OpenFoamDimensionalReferenceTableExtrema(
			int rowCount,
			int referenceRowAvailableCount,
			int blockedRowCount,
			int staticAnchorReferenceRowCount,
			int staticAnchorReferenceAvailableCount,
			double maxQueryAdvanceRatioJ,
			double maxQueryRpm,
			double maxThrustReferenceWeight,
			double maxResidualReferenceWeight,
			int lookupExecutionContractReadyCount,
			int dimensionalSupportReadyCount,
			int dimensionalReferenceReviewedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String referencePayloadKind
	) {
	}

	public record CtCpJOpenFoamDimensionalReferenceTableAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int referenceRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<OpenFoamDimensionalReferenceRow> rows,
			OpenFoamDimensionalReferenceTableExtrema extrema
	) {
		public CtCpJOpenFoamDimensionalReferenceTableAudit {
			rows = List.copyOf(rows);
		}
	}

	public static CtCpJOpenFoamDimensionalReferenceTableAudit audit() {
		return audit(handoff("current_dimensional_support_blocked"));
	}

	public static CtCpJOpenFoamDimensionalReferenceTableAudit audit(
			PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.OpenFoamDimensionalReferenceHandoffSummary handoff
	) {
		if (handoff == null) {
			throw new IllegalArgumentException("OpenFOAM dimensional reference handoff summary must not be null.");
		}
		List<OpenFoamDimensionalReferenceRow> rows = targets()
				.stream()
				.map(target -> row(handoff, target))
				.toList();
		return new CtCpJOpenFoamDimensionalReferenceTableAudit(
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

	public static OpenFoamDimensionalReferenceRow row(String presetName, String caseName) {
		return audit().rows()
				.stream()
				.filter(row -> row.presetName().equals(presetName) && row.caseName().equals(caseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown OpenFOAM dimensional reference row: " + presetName + " / " + caseName));
	}

	public static OpenFoamDimensionalReferenceRow row(
			PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.OpenFoamDimensionalReferenceHandoffSummary handoff,
			PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase target
	) {
		if (handoff == null || target == null) {
			throw new IllegalArgumentException("handoff and target are required.");
		}
		boolean exportAllowed = handoff.referenceMaterialExportAllowed();
		boolean available = exportAllowed && target.postReviewOpenFoamCaseRunnable();
		double weight = available ? 1.0 : 0.0;
		return new OpenFoamDimensionalReferenceRow(
				target.presetName(),
				target.caseName(),
				PropellerArchiveSourceFingerprint.ARCHIVE_SHA256,
				target.solverFamily(),
				target.geometryMatchId(),
				target.queryAdvanceRatioJ(),
				target.queryRpm(),
				target.equivalentProjectMu(),
				target.staticAnchorCase(),
				handoff.lookupExecutionContractReady(),
				handoff.dimensionalSupportReady(),
				handoff.dimensionalReferenceReviewed(),
				exportAllowed,
				available,
				weight,
				weight,
				weight,
				weight,
				weight,
				weight,
				false,
				false,
				available ? "AVAILABLE" : "BLOCKED",
				messageFor(handoff, available),
				REFERENCE_PAYLOAD_KIND,
				handoff.sourceRuntimeInfo()
		);
	}

	private static List<PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase> targets() {
		return PropellerArchiveCtCpJOpenFoamValidationPlan.audit()
				.cases()
				.stream()
				.filter(PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase
						::postReviewOpenFoamCaseRunnable)
				.toList();
	}

	private static PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.OpenFoamDimensionalReferenceHandoffSummary
			handoff(String scenarioName) {
		return PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.audit()
				.scenarios()
				.stream()
				.filter(scenario -> scenarioName.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static OpenFoamDimensionalReferenceTableExtrema extrema(
			List<OpenFoamDimensionalReferenceRow> rows
	) {
		int available = 0;
		int staticRows = 0;
		int staticAvailable = 0;
		int executionReady = 0;
		int supportReady = 0;
		int reviewed = 0;
		int runtime = 0;
		int gameplay = 0;
		double maxJ = 0.0;
		double maxRpm = 0.0;
		double maxThrustWeight = 0.0;
		double maxResidualWeight = 0.0;
		for (OpenFoamDimensionalReferenceRow row : rows) {
			if (row.openFoamDimensionalReferenceRowAvailable()) {
				available++;
			}
			if (row.staticAnchorReferenceRow()) {
				staticRows++;
				if (row.openFoamDimensionalReferenceRowAvailable()) {
					staticAvailable++;
				}
			}
			if (row.lookupExecutionContractReady()) {
				executionReady++;
			}
			if (row.dimensionalSupportReady()) {
				supportReady++;
			}
			if (row.dimensionalReferenceReviewed()) {
				reviewed++;
			}
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
			maxJ = Math.max(maxJ, row.queryAdvanceRatioJ());
			maxRpm = Math.max(maxRpm, row.queryRpm());
			maxThrustWeight = Math.max(maxThrustWeight, row.thrustReferenceWeight());
			maxResidualWeight = Math.max(maxResidualWeight, row.residualReferenceWeight());
		}
		return new OpenFoamDimensionalReferenceTableExtrema(
				rows.size(),
				available,
				rows.size() - available,
				staticRows,
				staticAvailable,
				maxJ,
				maxRpm,
				maxThrustWeight,
				maxResidualWeight,
				executionReady,
				supportReady,
				reviewed,
				runtime,
				gameplay,
				REFERENCE_PAYLOAD_KIND
		);
	}

	private static String messageFor(
			PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.OpenFoamDimensionalReferenceHandoffSummary handoff,
			boolean available
	) {
		if ("lookup-execution-contract-not-ready".equals(handoff.message())) {
			return "lookup-execution-contract-not-ready";
		}
		if (!handoff.dimensionalSupportReady()) {
			if (!handoff.lookupSupportReady()) {
				return "openfoam-dimensional-lookup-support-not-ready";
			}
			if (!handoff.dimensionalResidualReady()) {
				return "openfoam-dimensional-residual-not-ready";
			}
			return "openfoam-dimensional-support-not-ready";
		}
		if (!handoff.dimensionalReferenceReviewed()) {
			return "openfoam-dimensional-reference-review-missing";
		}
		if (!available) {
			return "openfoam-dimensional-reference-export-blocked";
		}
		return "openfoam-dimensional-reference-row-available";
	}
}
