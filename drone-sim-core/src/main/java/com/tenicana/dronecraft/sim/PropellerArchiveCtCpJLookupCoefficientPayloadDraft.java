package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCtCpJLookupCoefficientPayloadDraft {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-Lookup-Coefficient-Payload-Draft-Packet";
	public static final String CAVEAT =
			"CT/CP/J lookup coefficient payload draft records compact archive-derived coefficient candidates for every reviewed grid slot; draft values are not reviewed payloads, do not enter lookup execution, and never enable runtime or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 8;
	public static final int DRAFT_ROW_COUNT =
			PropellerArchiveCtCpJLookupReviewedGridInput.GRID_INPUT_SLOT_ROW_COUNT;
	public static final int SUMMARY_ROW_COUNT = 18;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ DRAFT_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final String NEXT_REQUIRED_ACTION =
			"manual-review-coefficient-payload-drafts-before-lookup-execution";

	private static final double ETA_FORMULA_TOLERANCE = 1.0e-6;

	private static final List<DraftEstimate> DRAFT_ESTIMATES = List.of(
			new DraftEstimate("da4052 5.0x3.75 - 3", "static_anchor_low_rpm", "static-anchor",
					"DIRECT_STATIC_SAMPLE_DRAFT", 0.139069000, 0.116804000, 0.000000000,
					1, 0.000000000, 0.000000000),
			new DraftEstimate("da4052 5.0x3.75 - 3", "mid_domain_mid_rpm", "j0-r0",
					"LOCAL_IDW_8_NEIGHBOR_DRAFT", 0.106433175, 0.082779485, 0.418020889,
					8, 0.042344994, 0.209475143),
			new DraftEstimate("da4052 5.0x3.75 - 3", "mid_domain_mid_rpm", "j1-r0",
					"LOCAL_IDW_8_NEIGHBOR_DRAFT", 0.069933908, 0.065757757, 0.518651636,
					8, 0.011270377, 0.184887815),
			new DraftEstimate("da4052 5.0x3.75 - 3", "mid_domain_mid_rpm", "j0-r1",
					"LOCAL_IDW_8_NEIGHBOR_DRAFT", 0.111286686, 0.082094577, 0.440729809,
					8, 0.055829977, 0.153541416),
			new DraftEstimate("da4052 5.0x3.75 - 3", "mid_domain_mid_rpm", "j1-r1",
					"LOCAL_IDW_8_NEIGHBOR_DRAFT", 0.071510549, 0.063755407, 0.547000894,
					8, 0.062777508, 0.149978621),
			new DraftEstimate("da4052 5.0x3.75 - 3", "high_domain_max_rpm", "j0-rmax",
					"LOCAL_IDW_8_NEIGHBOR_DRAFT", 0.027183414, 0.035710827, 0.494968737,
					8, 0.139691400, 0.242265676),
			new DraftEstimate("da4052 5.0x3.75 - 3", "high_domain_max_rpm", "j1-rmax",
					"LOCAL_IDW_8_NEIGHBOR_DRAFT", 0.002618372, 0.020673248, 0.102945235,
					8, 0.146105106, 0.316538365),
			new DraftEstimate("ancf 10.0x5.0 - 2", "static_anchor_low_rpm", "static-anchor",
					"DIRECT_STATIC_SAMPLE_DRAFT", 0.071796000, 0.018547000, 0.000000000,
					1, 0.000000000, 0.000000000),
			new DraftEstimate("ancf 10.0x5.0 - 2", "mid_domain_mid_rpm", "j0-r0",
					"LOCAL_IDW_8_NEIGHBOR_DRAFT", 0.063736997, 0.032567112, 0.522153480,
					8, 0.067822167, 0.142675631),
			new DraftEstimate("ancf 10.0x5.0 - 2", "mid_domain_mid_rpm", "j1-r0",
					"LOCAL_IDW_8_NEIGHBOR_DRAFT", 0.042710882, 0.027306346, 0.625967850,
					8, 0.065759099, 0.139496471),
			new DraftEstimate("ancf 10.0x5.0 - 2", "mid_domain_mid_rpm", "j0-r1",
					"LOCAL_IDW_8_NEIGHBOR_DRAFT", 0.067996431, 0.032701050, 0.554766527,
					8, 0.028332645, 0.139213440),
			new DraftEstimate("ancf 10.0x5.0 - 2", "mid_domain_mid_rpm", "j1-r1",
					"LOCAL_IDW_8_NEIGHBOR_DRAFT", 0.046175839, 0.027995409, 0.660092914,
					8, 0.022317160, 0.136166882),
			new DraftEstimate("ancf 10.0x5.0 - 2", "high_domain_max_rpm", "j0-rmax",
					"LOCAL_IDW_8_NEIGHBOR_DRAFT", 0.024105782, 0.020314001, 0.633200986,
					8, 0.218215047, 0.250997226),
			new DraftEstimate("ancf 10.0x5.0 - 2", "high_domain_max_rpm", "j1-rmax",
					"LOCAL_IDW_8_NEIGHBOR_DRAFT", -0.008978626, 0.006045000, -0.990693680,
					8, 0.217554868, 0.217570086)
	);

	private PropellerArchiveCtCpJLookupCoefficientPayloadDraft() {
	}

	private record DraftEstimate(
			String performanceMatchId,
			String caseName,
			String slotId,
			String draftCoefficientSourceKind,
			double draftCtCoefficient,
			double draftCpCoefficient,
			double draftEtaAtSlot,
			int fitNeighborRowCount,
			double nearestSourceNormalizedDistance,
			double maxFitSourceNormalizedDistance
	) {
	}

	public record CoefficientPayloadDraftRow(
			String presetName,
			String caseName,
			String performanceMatchId,
			String slotId,
			String slotKind,
			String slotCoordinateRole,
			double slotAdvanceRatioJ,
			double slotRpm,
			String sourceArchiveFileName,
			String draftCoefficientSourceKind,
			double draftCtCoefficient,
			double draftCpCoefficient,
			double draftEtaAtSlot,
			double draftEtaFormulaResidual,
			int fitNeighborRowCount,
			double nearestSourceNormalizedDistance,
			double maxFitSourceNormalizedDistance,
			boolean directStaticSampleUsed,
			boolean postReviewFullSimulationLookupAllowed,
			boolean finiteDraftCoefficient,
			boolean nonnegativeDraftCt,
			boolean positiveDraftCp,
			boolean etaFormulaConsistent,
			boolean sourceRowsReviewed,
			boolean coefficientPayloadReviewed,
			boolean coefficientPayloadDraftReady,
			boolean lookupExecutionInputReady,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message
	) {
	}

	public record CoefficientPayloadDraftSummary(
			int draftRowCount,
			int directStaticDraftCount,
			int localIdwDraftCount,
			int finiteDraftCoefficientCount,
			int nonnegativeDraftCtCount,
			int negativeDraftCtCount,
			int positiveDraftCpCount,
			int etaFormulaConsistentCount,
			int sourceRowsReviewedCount,
			int coefficientPayloadReviewedCount,
			int coefficientPayloadDraftReadyCount,
			int lookupExecutionInputReadyCount,
			int postReviewFullSimulationDraftCount,
			int postReviewPerformanceOnlyDraftCount,
			double maxNearestSourceNormalizedDistance,
			double maxFitSourceNormalizedDistance,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount
	) {
	}

	public record CtCpJLookupCoefficientPayloadDraftAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int draftRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<CoefficientPayloadDraftRow> rows,
			CoefficientPayloadDraftSummary summary
	) {
		public CtCpJLookupCoefficientPayloadDraftAudit {
			rows = List.copyOf(rows);
		}
	}

	public static CtCpJLookupCoefficientPayloadDraftAudit audit() {
		List<PropellerArchiveCtCpJLookupCoefficientPayloadSourceWindow.CoefficientPayloadSourceWindowRow>
				sourceWindows = PropellerArchiveCtCpJLookupCoefficientPayloadSourceWindow.audit().rows();
		List<CoefficientPayloadDraftRow> rows = PropellerArchiveCtCpJLookupReviewedGridInput.audit()
				.slots()
				.stream()
				.map(slot -> draft(slot, sourceWindows))
				.toList();
		return new CtCpJLookupCoefficientPayloadDraftAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				DRAFT_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				rows,
				summary(rows)
		);
	}

	public static CoefficientPayloadDraftRow draft(String presetName, String caseName, String slotId) {
		return audit().rows()
				.stream()
				.filter(row -> row.presetName().equals(presetName)
						&& row.caseName().equals(caseName)
						&& row.slotId().equals(slotId))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown CT/CP/J coefficient payload draft: "
								+ presetName + " / " + caseName + " / " + slotId));
	}

	private static CoefficientPayloadDraftRow draft(
			PropellerArchiveCtCpJLookupReviewedGridInput.ReviewedGridInputSlot slot,
			List<PropellerArchiveCtCpJLookupCoefficientPayloadSourceWindow.CoefficientPayloadSourceWindowRow>
					sourceWindows
	) {
		PropellerArchiveCtCpJLookupCoefficientPayloadSourceWindow.CoefficientPayloadSourceWindowRow sourceWindow =
				sourceWindow(sourceWindows, slot);
		DraftEstimate estimate = estimate(slot.performanceMatchId(), slot.caseName(), slot.slotId());
		boolean finite = Double.isFinite(estimate.draftCtCoefficient())
				&& Double.isFinite(estimate.draftCpCoefficient())
				&& Double.isFinite(estimate.draftEtaAtSlot());
		boolean nonnegativeCt = finite && estimate.draftCtCoefficient() >= 0.0;
		boolean positiveCp = finite && estimate.draftCpCoefficient() > 0.0;
		double expectedEta = positiveCp && slot.slotAdvanceRatioJ() > 1.0e-12
				? slot.slotAdvanceRatioJ() * estimate.draftCtCoefficient() / estimate.draftCpCoefficient()
				: 0.0;
		double etaResidual = Math.abs(estimate.draftEtaAtSlot() - expectedEta);
		boolean etaConsistent = finite && etaResidual <= ETA_FORMULA_TOLERANCE;
		boolean sourceRowsReviewed = false;
		boolean coefficientPayloadReviewed = false;
		boolean ready = sourceRowsReviewed
				&& coefficientPayloadReviewed
				&& sourceWindow.payloadSourceWindowReady()
				&& nonnegativeCt
				&& positiveCp
				&& etaConsistent;
		return new CoefficientPayloadDraftRow(
				slot.presetName(),
				slot.caseName(),
				slot.performanceMatchId(),
				slot.slotId(),
				slot.slotKind(),
				slot.slotCoordinateRole(),
				slot.slotAdvanceRatioJ(),
				slot.slotRpm(),
				sourceWindow.sourceArchiveFileName(),
				estimate.draftCoefficientSourceKind(),
				estimate.draftCtCoefficient(),
				estimate.draftCpCoefficient(),
				estimate.draftEtaAtSlot(),
				etaResidual,
				estimate.fitNeighborRowCount(),
				estimate.nearestSourceNormalizedDistance(),
				estimate.maxFitSourceNormalizedDistance(),
				"DIRECT_STATIC_SAMPLE_DRAFT".equals(estimate.draftCoefficientSourceKind()),
				sourceWindow.postReviewFullSimulationLookupAllowed(),
				finite,
				nonnegativeCt,
				positiveCp,
				etaConsistent,
				sourceRowsReviewed,
				coefficientPayloadReviewed,
				ready,
				ready,
				false,
				false,
				ready ? "READY" : "BLOCKED",
				messageFor(sourceRowsReviewed, coefficientPayloadReviewed, nonnegativeCt, positiveCp, etaConsistent)
		);
	}

	private static CoefficientPayloadDraftSummary summary(List<CoefficientPayloadDraftRow> rows) {
		List<CoefficientPayloadDraftRow> draftRows = List.copyOf(rows);
		int direct = 0;
		int idw = 0;
		int finite = 0;
		int nonnegative = 0;
		int negative = 0;
		int positiveCp = 0;
		int eta = 0;
		int sourceReviewed = 0;
		int payloadReviewed = 0;
		int ready = 0;
		int lookup = 0;
		int full = 0;
		int performanceOnly = 0;
		double maxNearest = 0.0;
		double maxFit = 0.0;
		int runtime = 0;
		int gameplay = 0;
		for (CoefficientPayloadDraftRow row : draftRows) {
			if (row.directStaticSampleUsed()) {
				direct++;
			}
			if ("LOCAL_IDW_8_NEIGHBOR_DRAFT".equals(row.draftCoefficientSourceKind())) {
				idw++;
			}
			if (row.finiteDraftCoefficient()) {
				finite++;
			}
			if (row.nonnegativeDraftCt()) {
				nonnegative++;
			} else {
				negative++;
			}
			if (row.positiveDraftCp()) {
				positiveCp++;
			}
			if (row.etaFormulaConsistent()) {
				eta++;
			}
			if (row.sourceRowsReviewed()) {
				sourceReviewed++;
			}
			if (row.coefficientPayloadReviewed()) {
				payloadReviewed++;
			}
			if (row.coefficientPayloadDraftReady()) {
				ready++;
			}
			if (row.lookupExecutionInputReady()) {
				lookup++;
			}
			if (row.postReviewFullSimulationLookupAllowed()) {
				full++;
			} else {
				performanceOnly++;
			}
			maxNearest = Math.max(maxNearest, row.nearestSourceNormalizedDistance());
			maxFit = Math.max(maxFit, row.maxFitSourceNormalizedDistance());
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		return new CoefficientPayloadDraftSummary(
				draftRows.size(),
				direct,
				idw,
				finite,
				nonnegative,
				negative,
				positiveCp,
				eta,
				sourceReviewed,
				payloadReviewed,
				ready,
				lookup,
				full,
				performanceOnly,
				maxNearest,
				maxFit,
				runtime,
				gameplay
		);
	}

	private static PropellerArchiveCtCpJLookupCoefficientPayloadSourceWindow.CoefficientPayloadSourceWindowRow
			sourceWindow(
					List<PropellerArchiveCtCpJLookupCoefficientPayloadSourceWindow.CoefficientPayloadSourceWindowRow>
							sourceWindows,
					PropellerArchiveCtCpJLookupReviewedGridInput.ReviewedGridInputSlot slot
			) {
		return sourceWindows.stream()
				.filter(row -> row.presetName().equals(slot.presetName())
						&& row.caseName().equals(slot.caseName())
						&& row.slotId().equals(slot.slotId()))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"missing CT/CP/J coefficient payload source window for draft: "
								+ slot.presetName() + " / " + slot.caseName() + " / " + slot.slotId()));
	}

	private static DraftEstimate estimate(String performanceMatchId, String caseName, String slotId) {
		return DRAFT_ESTIMATES.stream()
				.filter(estimate -> estimate.performanceMatchId().equals(performanceMatchId)
						&& estimate.caseName().equals(caseName)
						&& estimate.slotId().equals(slotId))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"missing CT/CP/J coefficient payload draft estimate: "
								+ performanceMatchId + " / " + caseName + " / " + slotId));
	}

	private static String messageFor(
			boolean sourceRowsReviewed,
			boolean coefficientPayloadReviewed,
			boolean nonnegativeCt,
			boolean positiveCp,
			boolean etaConsistent
	) {
		if (!sourceRowsReviewed) {
			return "source-review-blocks-draft-coefficient-payload";
		}
		if (!coefficientPayloadReviewed) {
			return "manual-review-required-before-coefficient-payload";
		}
		if (!nonnegativeCt) {
			return "draft-negative-ct-requires-review-before-lookup";
		}
		if (!positiveCp) {
			return "draft-nonpositive-cp-requires-review-before-lookup";
		}
		if (!etaConsistent) {
			return "draft-eta-formula-mismatch";
		}
		return "coefficient-payload-draft-ready";
	}
}
