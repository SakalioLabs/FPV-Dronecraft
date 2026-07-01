package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCtCpJLookupCoefficientPayloadSourceWindow {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-Lookup-Coefficient-Payload-Source-Window-Packet";
	public static final String CAVEAT =
			"CT/CP/J lookup coefficient payload source windows bind every reviewed grid slot to compact archive J/RPM support before reviewed coefficients are filled; raw archive rows stay unvendored and runtime/gameplay coupling remain closed.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 8;
	public static final int SOURCE_WINDOW_ROW_COUNT =
			PropellerArchiveCtCpJLookupReviewedGridInput.GRID_INPUT_SLOT_ROW_COUNT;
	public static final int SUMMARY_ROW_COUNT = 16;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ SOURCE_WINDOW_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final String NEXT_REQUIRED_ACTION =
			"review-source-license-then-fill-coefficient-payload-source-windows";

	private static final List<SourceBracket> SOURCE_BRACKETS = List.of(
			new SourceBracket("da4052 5.0x3.75 - 3", "static_anchor_low_rpm", "static-anchor",
					0.000000, 0.000000, 1477.778, 1477.778, 1),
			new SourceBracket("da4052 5.0x3.75 - 3", "mid_domain_mid_rpm", "j0-r0",
					0.324629, 0.329413, 4025.000, 4500.000, 0),
			new SourceBracket("da4052 5.0x3.75 - 3", "mid_domain_mid_rpm", "j1-r0",
					0.474387, 0.489270, 4025.000, 4500.000, 0),
			new SourceBracket("da4052 5.0x3.75 - 3", "mid_domain_mid_rpm", "j0-r1",
					0.324629, 0.329413, 5030.000, 5433.334, 0),
			new SourceBracket("da4052 5.0x3.75 - 3", "mid_domain_mid_rpm", "j1-r1",
					0.474387, 0.489270, 5030.000, 5433.334, 0),
			new SourceBracket("da4052 5.0x3.75 - 3", "high_domain_max_rpm", "j0-rmax",
					0.637874, 0.655440, 7946.667, 7946.667, 0),
			new SourceBracket("da4052 5.0x3.75 - 3", "high_domain_max_rpm", "j1-rmax",
					0.812815, 0.812815, 7946.667, 7946.667, 0),
			new SourceBracket("ancf 10.0x5.0 - 2", "static_anchor_low_rpm", "static-anchor",
					0.000000, 0.000000, 1060.000, 1060.000, 1),
			new SourceBracket("ancf 10.0x5.0 - 2", "mid_domain_mid_rpm", "j0-r0",
					0.260182, 0.282563, 3540.000, 4017.000, 0),
			new SourceBracket("ancf 10.0x5.0 - 2", "mid_domain_mid_rpm", "j1-r0",
					0.396974, 0.402336, 3540.000, 4017.000, 0),
			new SourceBracket("ancf 10.0x5.0 - 2", "mid_domain_mid_rpm", "j0-r1",
					0.260182, 0.282563, 4460.000, 4960.000, 0),
			new SourceBracket("ancf 10.0x5.0 - 2", "mid_domain_mid_rpm", "j1-r1",
					0.396974, 0.402336, 4460.000, 4960.000, 0),
			new SourceBracket("ancf 10.0x5.0 - 2", "high_domain_max_rpm", "j0-rmax",
					0.522286, 0.539501, 7440.000, 7440.000, 0),
			new SourceBracket("ancf 10.0x5.0 - 2", "high_domain_max_rpm", "j1-rmax",
					0.667043, 0.667043, 7440.000, 7440.000, 0)
	);

	private PropellerArchiveCtCpJLookupCoefficientPayloadSourceWindow() {
	}

	private record SourceBracket(
			String performanceMatchId,
			String caseName,
			String slotId,
			double lowerSourceAdvanceRatioJ,
			double upperSourceAdvanceRatioJ,
			double lowerSourceRpm,
			double upperSourceRpm,
			int directArchiveSampleCount
	) {
	}

	public record CoefficientPayloadSourceWindowRow(
			String presetName,
			String caseName,
			String performanceMatchId,
			String slotId,
			String slotKind,
			String slotCoordinateRole,
			double slotAdvanceRatioJ,
			double slotRpm,
			String sourceArchiveFileName,
			int sourcePerformanceSampleRowCount,
			int sourceStaticSampleRowCount,
			int sourceNonstaticSampleRowCount,
			int sourceDistinctRpmBinCount,
			double lowerSourceAdvanceRatioJ,
			double upperSourceAdvanceRatioJ,
			double lowerSourceRpm,
			double upperSourceRpm,
			double sourceJBracketWidth,
			double sourceRpmBracketWidth,
			int directArchiveSampleCount,
			boolean directArchiveSampleAvailable,
			boolean directStaticAnchorBinding,
			boolean scatteredFitRequired,
			boolean postReviewFullSimulationLookupAllowed,
			boolean archiveCurveShapeGuardPassed,
			boolean sourceRowsReviewed,
			boolean coefficientPayloadReviewed,
			boolean payloadSourceWindowReady,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String nextRequiredAction,
			String status,
			String message
	) {
	}

	public record CoefficientPayloadSourceWindowSummary(
			int sourceWindowRowCount,
			int directStaticAnchorSourceWindowCount,
			int scatteredFitSourceWindowCount,
			int directArchiveSampleAvailableSlotCount,
			int directNonstaticArchiveSampleSlotCount,
			int postReviewFullSimulationSourceWindowCount,
			int postReviewPerformanceOnlySourceWindowCount,
			int archiveCurveShapeGuardReadySlotCount,
			int sourceRowsReviewedSlotCount,
			int coefficientPayloadReviewedSlotCount,
			int payloadSourceWindowReadySlotCount,
			double maxSourceJBracketWidth,
			double maxSourceRpmBracketWidth,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String nextRequiredAction
	) {
	}

	public record CtCpJLookupCoefficientPayloadSourceWindowAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int sourceWindowRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<CoefficientPayloadSourceWindowRow> rows,
			CoefficientPayloadSourceWindowSummary summary
	) {
		public CtCpJLookupCoefficientPayloadSourceWindowAudit {
			rows = List.copyOf(rows);
		}
	}

	public static CtCpJLookupCoefficientPayloadSourceWindowAudit audit() {
		List<CoefficientPayloadSourceWindowRow> rows = PropellerArchiveCtCpJLookupReviewedGridInput.audit()
				.slots()
				.stream()
				.map(PropellerArchiveCtCpJLookupCoefficientPayloadSourceWindow::window)
				.toList();
		return new CtCpJLookupCoefficientPayloadSourceWindowAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				SOURCE_WINDOW_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				rows,
				summary(rows)
		);
	}

	public static CoefficientPayloadSourceWindowRow window(
			String presetName,
			String caseName,
			String slotId
	) {
		return audit().rows()
				.stream()
				.filter(row -> row.presetName().equals(presetName)
						&& row.caseName().equals(caseName)
						&& row.slotId().equals(slotId))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown CT/CP/J coefficient payload source window: "
								+ presetName + " / " + caseName + " / " + slotId));
	}

	private static CoefficientPayloadSourceWindowRow window(
			PropellerArchiveCtCpJLookupReviewedGridInput.ReviewedGridInputSlot slot
	) {
		PropellerArchiveCtCpJArchiveLookupGridCoverage.ArchiveLookupGridTopology topology =
				PropellerArchiveCtCpJArchiveLookupGridCoverage.topology(slot.presetName());
		PropellerArchiveCtCpJArchiveCurveShapeReview.ArchiveCurveShapeRow shape =
				PropellerArchiveCtCpJArchiveCurveShapeReview.row(slot.presetName());
		SourceBracket bracket = bracket(slot.performanceMatchId(), slot.caseName(), slot.slotId());
		boolean sourceRowsReviewed = false;
		boolean directArchiveSampleAvailable = bracket.directArchiveSampleCount() > 0;
		boolean directNonstaticArchiveSample = directArchiveSampleAvailable
				&& slot.slotAdvanceRatioJ() > 1.0e-12;
		boolean ready = sourceRowsReviewed
				&& slot.coefficientPayloadReviewed()
				&& shape.shapeGuardPassed()
				&& (directArchiveSampleAvailable || slot.scatteredFitRequired());
		return new CoefficientPayloadSourceWindowRow(
				slot.presetName(),
				slot.caseName(),
				slot.performanceMatchId(),
				slot.slotId(),
				slot.slotKind(),
				slot.slotCoordinateRole(),
				slot.slotAdvanceRatioJ(),
				slot.slotRpm(),
				sourceArchiveFileName(slot.performanceMatchId()),
				topology.performanceSampleRowCount(),
				topology.staticSampleRowCount(),
				topology.nonstaticSampleRowCount(),
				topology.distinctRpmBinCount(),
				bracket.lowerSourceAdvanceRatioJ(),
				bracket.upperSourceAdvanceRatioJ(),
				bracket.lowerSourceRpm(),
				bracket.upperSourceRpm(),
				bracket.upperSourceAdvanceRatioJ() - bracket.lowerSourceAdvanceRatioJ(),
				bracket.upperSourceRpm() - bracket.lowerSourceRpm(),
				bracket.directArchiveSampleCount(),
				directArchiveSampleAvailable,
				slot.directNeighborBindingReady() && directArchiveSampleAvailable,
				slot.scatteredFitRequired(),
				slot.postReviewFullSimulationLookupAllowed(),
				shape.shapeGuardPassed(),
				sourceRowsReviewed,
				slot.coefficientPayloadReviewed(),
				ready,
				false,
				false,
				nextRequiredActionFor(sourceRowsReviewed, slot, shape, directArchiveSampleAvailable),
				ready ? "READY" : "BLOCKED",
				messageFor(sourceRowsReviewed, slot, shape, directArchiveSampleAvailable, directNonstaticArchiveSample)
		);
	}

	private static CoefficientPayloadSourceWindowSummary summary(
			List<CoefficientPayloadSourceWindowRow> rows
	) {
		List<CoefficientPayloadSourceWindowRow> windowRows = List.copyOf(rows);
		int direct = 0;
		int scattered = 0;
		int directAvailable = 0;
		int directNonstatic = 0;
		int full = 0;
		int performanceOnly = 0;
		int shape = 0;
		int reviewed = 0;
		int payload = 0;
		int ready = 0;
		double maxJWidth = 0.0;
		double maxRpmWidth = 0.0;
		int runtime = 0;
		int gameplay = 0;
		for (CoefficientPayloadSourceWindowRow row : windowRows) {
			if (row.directStaticAnchorBinding()) {
				direct++;
			}
			if (row.scatteredFitRequired()) {
				scattered++;
			}
			if (row.directArchiveSampleAvailable()) {
				directAvailable++;
			}
			if (row.directArchiveSampleAvailable() && row.slotAdvanceRatioJ() > 1.0e-12) {
				directNonstatic++;
			}
			if (row.postReviewFullSimulationLookupAllowed()) {
				full++;
			} else {
				performanceOnly++;
			}
			if (row.archiveCurveShapeGuardPassed()) {
				shape++;
			}
			if (row.sourceRowsReviewed()) {
				reviewed++;
			}
			if (row.coefficientPayloadReviewed()) {
				payload++;
			}
			if (row.payloadSourceWindowReady()) {
				ready++;
			}
			maxJWidth = Math.max(maxJWidth, row.sourceJBracketWidth());
			maxRpmWidth = Math.max(maxRpmWidth, row.sourceRpmBracketWidth());
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		return new CoefficientPayloadSourceWindowSummary(
				windowRows.size(),
				direct,
				scattered,
				directAvailable,
				directNonstatic,
				full,
				performanceOnly,
				shape,
				reviewed,
				payload,
				ready,
				maxJWidth,
				maxRpmWidth,
				runtime,
				gameplay,
				NEXT_REQUIRED_ACTION
		);
	}

	private static SourceBracket bracket(String performanceMatchId, String caseName, String slotId) {
		return SOURCE_BRACKETS.stream()
				.filter(bracket -> bracket.performanceMatchId().equals(performanceMatchId)
						&& bracket.caseName().equals(caseName)
						&& bracket.slotId().equals(slotId))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"missing archive source bracket for CT/CP/J payload slot: "
								+ performanceMatchId + " / " + caseName + " / " + slotId));
	}

	private static String sourceArchiveFileName(String performanceMatchId) {
		return switch (performanceMatchId) {
			case "da4052 5.0x3.75 - 3" -> "volume2_exp.csv";
			case "ancf 10.0x5.0 - 2" -> "volume3_exp.csv";
			default -> throw new IllegalArgumentException(
					"unknown CT/CP/J performance source file for: " + performanceMatchId);
		};
	}

	private static String nextRequiredActionFor(
			boolean sourceRowsReviewed,
			PropellerArchiveCtCpJLookupReviewedGridInput.ReviewedGridInputSlot slot,
			PropellerArchiveCtCpJArchiveCurveShapeReview.ArchiveCurveShapeRow shape,
			boolean directArchiveSampleAvailable
	) {
		if (!sourceRowsReviewed) {
			return "review-source-license-before-coefficient-payload-source-window";
		}
		if (!shape.shapeGuardPassed()) {
			return shape.nextRequiredAction();
		}
		if (slot.directNeighborBindingReady() && directArchiveSampleAvailable) {
			return "bind-reviewed-static-source-row-to-coefficient-payload";
		}
		if (slot.scatteredFitRequired()) {
			return "fit-reviewed-scattered-source-window-to-coefficient-payload";
		}
		return "resolve-coefficient-payload-source-window";
	}

	private static String messageFor(
			boolean sourceRowsReviewed,
			PropellerArchiveCtCpJLookupReviewedGridInput.ReviewedGridInputSlot slot,
			PropellerArchiveCtCpJArchiveCurveShapeReview.ArchiveCurveShapeRow shape,
			boolean directArchiveSampleAvailable,
			boolean directNonstaticArchiveSample
	) {
		if (!sourceRowsReviewed && slot.directNeighborBindingReady() && directArchiveSampleAvailable) {
			return "source-review-blocks-direct-static-payload-binding";
		}
		if (!sourceRowsReviewed && slot.scatteredFitRequired()) {
			return "source-review-blocks-scattered-fit-payload-source-window";
		}
		if (!sourceRowsReviewed) {
			return "source-review-blocks-coefficient-payload-source-window";
		}
		if (!shape.shapeGuardPassed()) {
			return "archive-curve-shape-guard-blocks-payload-source-window";
		}
		if (directNonstaticArchiveSample) {
			return "direct-nonstatic-payload-source-window-ready";
		}
		if (slot.directNeighborBindingReady() && directArchiveSampleAvailable) {
			return "direct-static-payload-source-window-ready";
		}
		if (slot.scatteredFitRequired()) {
			return "scattered-fit-payload-source-window-ready";
		}
		return "coefficient-payload-source-window-blocked";
	}
}
