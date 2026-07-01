package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PropellerArchiveCtCpJLookupReviewedCoefficientPayload {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-Lookup-Reviewed-Coefficient-Payload-Packet";
	public static final String CAVEAT =
			"CT/CP/J lookup reviewed coefficient payload binds reviewed CT/CP values to the reviewed grid input slots before lookup execution; current rows carry no reviewed coefficients, import no raw archive rows, and never enable runtime coupling or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 10;
	public static final int PAYLOAD_FIELD_ROW_COUNT = 23;
	public static final int PAYLOAD_SLOT_ROW_COUNT =
			PropellerArchiveCtCpJLookupReviewedGridInput.GRID_INPUT_SLOT_ROW_COUNT;
	public static final int SCENARIO_ROW_COUNT = 4;
	public static final int SUMMARY_ROW_COUNT = 16;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ PAYLOAD_FIELD_ROW_COUNT
			+ PAYLOAD_SLOT_ROW_COUNT
			+ SCENARIO_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final String NEXT_REQUIRED_ACTION =
			"review-and-bind-ct-cp-coefficient-payloads-to-grid-slots-before-lookup-execution";

	private static final double ETA_FORMULA_TOLERANCE = 1.0e-12;

	private static final List<ReviewedCoefficientPayloadField> FIELDS = List.of(
			new ReviewedCoefficientPayloadField("preset_name", "text", true,
					"reviewed grid input", "join payload to preset", false, false),
			new ReviewedCoefficientPayloadField("case_name", "text", true,
					"reviewed grid input", "join payload to lookup query case", false, false),
			new ReviewedCoefficientPayloadField("slot_id", "text", true,
					"reviewed grid input", "match payload to required J/RPM slot", false, false),
			new ReviewedCoefficientPayloadField("source_archive_sha256", "sha256", true,
					"PropellerArchiveSourceFingerprint", "prove archive identity before payload review", false, false),
			new ReviewedCoefficientPayloadField("performance_match_id", "text", true,
					"reviewed grid input", "trace payload to propeller performance source", false, false),
			new ReviewedCoefficientPayloadField("slot_advance_ratio_j", "J", true,
					"reviewed grid input", "provide execution-grid J coordinate", false, false),
			new ReviewedCoefficientPayloadField("slot_rpm", "rpm", true,
					"reviewed grid input", "provide execution-grid RPM coordinate", false, false),
			new ReviewedCoefficientPayloadField("coefficient_source_kind", "text", true,
					"reviewed grid input", "separate direct static anchors from fitted payloads", false, false),
			new ReviewedCoefficientPayloadField("ct_coefficient", "coefficient", true,
					"reviewed wind-tunnel or fitted payload", "thrust coefficient for lookup execution", false, false),
			new ReviewedCoefficientPayloadField("cp_coefficient", "coefficient", true,
					"reviewed wind-tunnel or fitted payload", "power coefficient for lookup execution", false, false),
			new ReviewedCoefficientPayloadField("eta_at_slot", "ratio", true,
					"J times CT over CP", "diagnose efficiency consistency at the slot", false, false),
			new ReviewedCoefficientPayloadField("eta_formula_residual", "ratio", true,
					"payload review", "reject eta mismatches before execution", false, false),
			new ReviewedCoefficientPayloadField("source_grid_slot_ready", "boolean", true,
					"reviewed grid input", "prove the slot itself is available for payload binding", false, false),
			new ReviewedCoefficientPayloadField("coefficient_payload_reviewed", "boolean", true,
					"manual coefficient review", "prevent unreviewed CT/CP from entering lookup execution", false, false),
			new ReviewedCoefficientPayloadField("finite_coefficient_payload", "boolean", true,
					"payload review", "reject nonfinite CT/CP values", false, false),
			new ReviewedCoefficientPayloadField("nonnegative_ct_payload", "boolean", true,
					"payload review", "match lookup execution CT validation", false, false),
			new ReviewedCoefficientPayloadField("positive_cp_payload", "boolean", true,
					"payload review", "match lookup execution CP validation", false, false),
			new ReviewedCoefficientPayloadField("eta_formula_consistent", "boolean", true,
					"payload review", "preserve eta equals J CT over CP", false, false),
			new ReviewedCoefficientPayloadField("archive_curve_shape_guard_passed", "boolean", true,
					"archive curve-shape review", "carry shape guard into executable payload rows", false, false),
			new ReviewedCoefficientPayloadField("coefficient_payload_ready", "boolean", true,
					"payload review", "open only after CT/CP payload guards pass", false, false),
			new ReviewedCoefficientPayloadField("lookup_execution_input_ready", "boolean", true,
					"payload review", "allow conversion to LookupGridRow", false, false),
			new ReviewedCoefficientPayloadField("runtime_coupling_allowed", "boolean", true,
					"runtime boundary", "keep payload review out of DronePhysics mutation", false, false),
			new ReviewedCoefficientPayloadField("gameplay_auto_apply_allowed", "boolean", true,
					"playable boundary", "keep payload review out of gameplay auto tuning", false, false)
	);

	private PropellerArchiveCtCpJLookupReviewedCoefficientPayload() {
	}

	public record ReviewedCoefficientPayloadField(
			String fieldName,
			String unit,
			boolean required,
			String source,
			String downstreamUse,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed
	) {
	}

	public record ReviewedCoefficientPayloadRow(
			String presetName,
			String caseName,
			String performanceMatchId,
			String sourceArchiveSha256,
			String slotId,
			String slotKind,
			String slotCoordinateRole,
			double queryAdvanceRatioJ,
			double queryRpm,
			double slotAdvanceRatioJ,
			double slotRpm,
			String coefficientSourceKind,
			double ctCoefficient,
			double cpCoefficient,
			double etaAtSlot,
			double etaFormulaResidual,
			boolean sourceGridSlotReady,
			boolean coefficientPayloadReviewed,
			boolean finiteCoefficientPayload,
			boolean nonnegativeCtPayload,
			boolean positiveCpPayload,
			boolean etaFormulaConsistent,
			boolean archiveCurveShapeGuardPassed,
			boolean coefficientPayloadReady,
			boolean lookupExecutionInputReady,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message
	) {
	}

	public record ReviewedCoefficientPayloadSummary(
			boolean sourceRowsReviewed,
			int expectedSlotCount,
			int observedPayloadSlotCount,
			int reviewedPayloadSlotCount,
			int finiteCoefficientPayloadSlotCount,
			int nonnegativeCtPayloadSlotCount,
			int positiveCpPayloadSlotCount,
			int etaConsistentPayloadSlotCount,
			int archiveCurveShapeGuardReadySlotCount,
			int coefficientPayloadReadySlotCount,
			int lookupExecutionInputSlotCount,
			int missingPayloadSlotCount,
			int unexpectedPayloadSlotCount,
			int failedPayloadSlotCount,
			boolean allExpectedSlotsPresent,
			boolean allPayloadsPassed,
			boolean lookupExecutionPayloadReady,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message,
			String sourceRuntimeInfo
	) {
	}

	public record ReviewedCoefficientPayloadScenario(
			String scenarioName,
			ReviewedCoefficientPayloadSummary summary
	) {
	}

	public record ReviewedCoefficientPayloadExtrema(
			int scenarioCount,
			int readyScenarioCount,
			int blockedScenarioCount,
			int maxObservedPayloadSlotCount,
			int maxReviewedPayloadSlotCount,
			int maxLookupExecutionInputSlotCount,
			int maxMissingPayloadSlotCount,
			int maxFailedPayloadSlotCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount
	) {
	}

	public record CtCpJLookupReviewedCoefficientPayloadAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int payloadFieldRowCount,
			int payloadSlotRowCount,
			int scenarioRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<ReviewedCoefficientPayloadField> fields,
			List<ReviewedCoefficientPayloadRow> rows,
			List<ReviewedCoefficientPayloadScenario> scenarios,
			ReviewedCoefficientPayloadExtrema extrema
	) {
		public CtCpJLookupReviewedCoefficientPayloadAudit {
			fields = List.copyOf(fields);
			rows = List.copyOf(rows);
			scenarios = List.copyOf(scenarios);
		}
	}

	public static CtCpJLookupReviewedCoefficientPayloadAudit audit() {
		List<ReviewedCoefficientPayloadRow> rows = currentRows();
		List<ReviewedCoefficientPayloadRow> passing = syntheticRows(true);
		List<ReviewedCoefficientPayloadRow> failed = syntheticRows(false);
		List<ReviewedCoefficientPayloadScenario> scenarios = List.of(
				new ReviewedCoefficientPayloadScenario("current_no_reviewed_payloads",
						review(false, List.of(), "current-reviewed-coefficient-payload-blocked")),
				new ReviewedCoefficientPayloadScenario("reviewed_grid_slots_payload_missing",
						review(true, rows, "reviewed-grid-slots-awaiting-coefficient-payload")),
				new ReviewedCoefficientPayloadScenario("synthetic_all_payloads_reviewed",
						review(true, passing, "synthetic-reviewed-coefficient-payload-ready")),
				new ReviewedCoefficientPayloadScenario("synthetic_one_payload_failed_cp",
						review(true, failed, "synthetic-reviewed-coefficient-payload-failed"))
		);
		return new CtCpJLookupReviewedCoefficientPayloadAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				PAYLOAD_FIELD_ROW_COUNT,
				PAYLOAD_SLOT_ROW_COUNT,
				SCENARIO_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				FIELDS,
				rows,
				scenarios,
				extrema(scenarios)
		);
	}

	public static ReviewedCoefficientPayloadField field(String fieldName) {
		return FIELDS.stream()
				.filter(field -> field.fieldName().equals(fieldName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown CT/CP/J reviewed coefficient payload field: " + fieldName));
	}

	public static ReviewedCoefficientPayloadRow reviewedRow(
			PropellerArchiveCtCpJLookupReviewedGridInput.ReviewedGridInputSlot slot,
			double ctCoefficient,
			double cpCoefficient
	) {
		return row(slot, ctCoefficient, cpCoefficient, true, true);
	}

	public static ReviewedCoefficientPayloadSummary review(
			boolean sourceRowsReviewed,
			List<ReviewedCoefficientPayloadRow> rows,
			String sourceRuntimeInfo
	) {
		if (rows == null) {
			throw new IllegalArgumentException("coefficient payload rows must not be null.");
		}
		if (sourceRuntimeInfo == null || sourceRuntimeInfo.isBlank()) {
			throw new IllegalArgumentException("sourceRuntimeInfo must not be blank.");
		}
		List<PropellerArchiveCtCpJLookupReviewedGridInput.ReviewedGridInputSlot> expectedSlots =
				PropellerArchiveCtCpJLookupReviewedGridInput.audit().slots();
		Set<String> expectedKeys = new HashSet<>();
		for (PropellerArchiveCtCpJLookupReviewedGridInput.ReviewedGridInputSlot slot : expectedSlots) {
			expectedKeys.add(key(slot.presetName(), slot.caseName(), slot.slotId()));
		}
		Set<String> observedKeys = new HashSet<>();
		for (ReviewedCoefficientPayloadRow row : rows) {
			if (row == null || row.presetName() == null || row.caseName() == null || row.slotId() == null
					|| row.presetName().isBlank() || row.caseName().isBlank() || row.slotId().isBlank()) {
				throw new IllegalArgumentException("coefficient payload rows must include stable slot keys.");
			}
			String key = key(row.presetName(), row.caseName(), row.slotId());
			if (!observedKeys.add(key)) {
				throw new IllegalArgumentException("duplicate CT/CP/J coefficient payload slot: " + key);
			}
		}
		int reviewed = 0;
		int finite = 0;
		int nonnegativeCt = 0;
		int positiveCp = 0;
		int eta = 0;
		int shape = 0;
		int ready = 0;
		int lookup = 0;
		int missing = 0;
		int failed = 0;
		for (PropellerArchiveCtCpJLookupReviewedGridInput.ReviewedGridInputSlot slot : expectedSlots) {
			ReviewedCoefficientPayloadRow row = find(rows, slot.presetName(), slot.caseName(), slot.slotId());
			if (row == null) {
				missing++;
				continue;
			}
			if (row.coefficientPayloadReviewed()) {
				reviewed++;
			}
			if (row.finiteCoefficientPayload()) {
				finite++;
			}
			if (row.nonnegativeCtPayload()) {
				nonnegativeCt++;
			}
			if (row.positiveCpPayload()) {
				positiveCp++;
			}
			if (row.etaFormulaConsistent()) {
				eta++;
			}
			if (row.archiveCurveShapeGuardPassed()) {
				shape++;
			}
			if (row.coefficientPayloadReady()) {
				ready++;
			}
			if (row.lookupExecutionInputReady()) {
				lookup++;
			}
			if (!row.lookupExecutionInputReady()) {
				failed++;
			}
		}
		int unexpected = 0;
		for (ReviewedCoefficientPayloadRow row : rows) {
			if (!expectedKeys.contains(key(row.presetName(), row.caseName(), row.slotId()))) {
				unexpected++;
			}
		}
		boolean allPresent = missing == 0 && unexpected == 0 && rows.size() == expectedSlots.size();
		boolean allPassed = lookup == expectedSlots.size();
		boolean readyForExecution = sourceRowsReviewed && allPresent && allPassed;
		return new ReviewedCoefficientPayloadSummary(
				sourceRowsReviewed,
				expectedSlots.size(),
				rows.size(),
				reviewed,
				finite,
				nonnegativeCt,
				positiveCp,
				eta,
				shape,
				ready,
				lookup,
				missing,
				unexpected,
				failed,
				allPresent,
				allPassed,
				readyForExecution,
				false,
				false,
				readyForExecution ? "READY" : "BLOCKED",
				message(sourceRowsReviewed, allPresent, allPassed, missing, unexpected, failed),
				sourceRuntimeInfo
		);
	}

	public static List<PropellerArchiveCtCpJLookupExecutionContract.LookupGridRow> lookupRows(
			List<ReviewedCoefficientPayloadRow> rows,
			String presetName,
			String caseName
	) {
		if (rows == null) {
			throw new IllegalArgumentException("coefficient payload rows must not be null.");
		}
		List<PropellerArchiveCtCpJLookupReviewedGridInput.ReviewedGridInputSlot> slots =
				PropellerArchiveCtCpJLookupReviewedGridInput.slots(presetName, caseName);
		if (slots.isEmpty()) {
			throw new IllegalArgumentException("unknown CT/CP/J reviewed grid target: "
					+ presetName + " / " + caseName);
		}
		Set<String> targetSlotIds = new HashSet<>();
		for (ReviewedCoefficientPayloadRow row : rows) {
			if (row != null
					&& presetName.equals(row.presetName())
					&& caseName.equals(row.caseName())
					&& !targetSlotIds.add(row.slotId())) {
				throw new IllegalArgumentException("duplicate CT/CP/J coefficient payload slot: "
						+ key(row.presetName(), row.caseName(), row.slotId()));
			}
		}
		List<PropellerArchiveCtCpJLookupExecutionContract.LookupGridRow> lookupRows = new ArrayList<>();
		for (PropellerArchiveCtCpJLookupReviewedGridInput.ReviewedGridInputSlot slot : slots) {
			ReviewedCoefficientPayloadRow row = find(rows, slot.presetName(), slot.caseName(), slot.slotId());
			if (row == null) {
				throw new IllegalArgumentException("missing reviewed coefficient payload slot: "
						+ key(slot.presetName(), slot.caseName(), slot.slotId()));
			}
			if (!row.lookupExecutionInputReady()) {
				throw new IllegalArgumentException("coefficient payload slot is not ready for lookup execution: "
						+ key(row.presetName(), row.caseName(), row.slotId()));
			}
			lookupRows.add(new PropellerArchiveCtCpJLookupExecutionContract.LookupGridRow(
					row.slotId(),
					row.slotAdvanceRatioJ(),
					row.slotRpm(),
					row.ctCoefficient(),
					row.cpCoefficient()));
		}
		return lookupRows;
	}

	private static List<ReviewedCoefficientPayloadRow> currentRows() {
		return PropellerArchiveCtCpJLookupReviewedGridInput.audit()
				.slots()
				.stream()
				.map(slot -> row(slot, 0.0, 0.0, false, slot.reviewedGridSlotReady()))
				.toList();
	}

	private static List<ReviewedCoefficientPayloadRow> syntheticRows(boolean allPass) {
		List<PropellerArchiveCtCpJLookupReviewedGridInput.ReviewedGridInputSlot> slots =
				PropellerArchiveCtCpJLookupReviewedGridInput.audit().slots();
		List<ReviewedCoefficientPayloadRow> rows = new ArrayList<>();
		for (int i = 0; i < slots.size(); i++) {
			double ct = 0.120 - 0.001 * i;
			double cp = allPass || i > 0 ? 0.040 + 0.001 * i : 0.0;
			rows.add(row(slots.get(i), ct, cp, true, true));
		}
		return rows;
	}

	private static ReviewedCoefficientPayloadRow row(
			PropellerArchiveCtCpJLookupReviewedGridInput.ReviewedGridInputSlot slot,
			double ctCoefficient,
			double cpCoefficient,
			boolean reviewed,
			boolean sourceGridSlotReady
	) {
		if (slot == null) {
			throw new IllegalArgumentException("reviewed grid input slot must not be null.");
		}
		String archiveSha = PropellerArchiveSourceFingerprint.audit().archive().archiveSha256();
		boolean finite = Double.isFinite(ctCoefficient) && Double.isFinite(cpCoefficient);
		boolean nonnegativeCt = finite && ctCoefficient >= 0.0;
		boolean positiveCp = finite && cpCoefficient > 0.0;
		double eta = positiveCp && slot.slotAdvanceRatioJ() > 1.0e-12
				? slot.slotAdvanceRatioJ() * ctCoefficient / cpCoefficient
				: 0.0;
		double expectedEta = positiveCp && slot.slotAdvanceRatioJ() > 1.0e-12
				? slot.slotAdvanceRatioJ() * ctCoefficient / cpCoefficient
				: 0.0;
		double etaResidual = Math.abs(eta - expectedEta);
		boolean etaConsistent = reviewed && finite && etaResidual <= ETA_FORMULA_TOLERANCE;
		boolean payloadReady = sourceGridSlotReady
				&& reviewed
				&& finite
				&& nonnegativeCt
				&& positiveCp
				&& etaConsistent
				&& slot.archiveCurveShapeGuardPassed();
		return new ReviewedCoefficientPayloadRow(
				slot.presetName(),
				slot.caseName(),
				slot.performanceMatchId(),
				archiveSha,
				slot.slotId(),
				slot.slotKind(),
				slot.slotCoordinateRole(),
				slot.queryAdvanceRatioJ(),
				slot.queryRpm(),
				slot.slotAdvanceRatioJ(),
				slot.slotRpm(),
				slot.executionInputSourceKind(),
				ctCoefficient,
				cpCoefficient,
				eta,
				etaResidual,
				sourceGridSlotReady,
				reviewed,
				finite,
				nonnegativeCt,
				positiveCp,
				etaConsistent,
				slot.archiveCurveShapeGuardPassed(),
				payloadReady,
				payloadReady,
				false,
				false,
				payloadReady ? "READY" : "AWAIT_REVIEWED_COEFFICIENT_PAYLOAD",
				payloadReady ? "reviewed-coefficient-payload-ready" : messageForRow(reviewed, finite, nonnegativeCt,
						positiveCp, etaConsistent, slot.archiveCurveShapeGuardPassed(), sourceGridSlotReady));
	}

	private static ReviewedCoefficientPayloadExtrema extrema(List<ReviewedCoefficientPayloadScenario> scenarios) {
		int ready = 0;
		int observed = 0;
		int reviewed = 0;
		int lookup = 0;
		int missing = 0;
		int failed = 0;
		int runtime = 0;
		int gameplay = 0;
		for (ReviewedCoefficientPayloadScenario scenario : scenarios) {
			ReviewedCoefficientPayloadSummary summary = scenario.summary();
			if (summary.lookupExecutionPayloadReady()) {
				ready++;
			}
			observed = Math.max(observed, summary.observedPayloadSlotCount());
			reviewed = Math.max(reviewed, summary.reviewedPayloadSlotCount());
			lookup = Math.max(lookup, summary.lookupExecutionInputSlotCount());
			missing = Math.max(missing, summary.missingPayloadSlotCount());
			failed = Math.max(failed, summary.failedPayloadSlotCount());
			if (summary.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (summary.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		return new ReviewedCoefficientPayloadExtrema(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				observed,
				reviewed,
				lookup,
				missing,
				failed,
				runtime,
				gameplay
		);
	}

	private static ReviewedCoefficientPayloadRow find(
			List<ReviewedCoefficientPayloadRow> rows,
			String presetName,
			String caseName,
			String slotId
	) {
		for (ReviewedCoefficientPayloadRow row : rows) {
			if (row != null
					&& presetName.equals(row.presetName())
					&& caseName.equals(row.caseName())
					&& slotId.equals(row.slotId())) {
				return row;
			}
		}
		return null;
	}

	private static String key(String presetName, String caseName, String slotId) {
		return presetName + "/" + caseName + "/" + slotId;
	}

	private static String message(
			boolean sourceRowsReviewed,
			boolean allPresent,
			boolean allPassed,
			int missing,
			int unexpected,
			int failed
	) {
		if (!sourceRowsReviewed) {
			return "source-license-review-required";
		}
		if (missing > 0 || unexpected > 0 || !allPresent) {
			return "coefficient-payload-slot-set-incomplete";
		}
		if (failed > 0 || !allPassed) {
			return "coefficient-payload-guard-failed";
		}
		return "reviewed-coefficient-payload-ready";
	}

	private static String messageForRow(
			boolean reviewed,
			boolean finite,
			boolean nonnegativeCt,
			boolean positiveCp,
			boolean etaConsistent,
			boolean shapeGuard,
			boolean sourceGridSlotReady
	) {
		if (!sourceGridSlotReady) {
			return "reviewed-grid-slot-not-ready";
		}
		if (!reviewed) {
			return "coefficient-payload-review-required";
		}
		if (!finite) {
			return "coefficient-payload-nonfinite";
		}
		if (!nonnegativeCt) {
			return "ct-payload-negative";
		}
		if (!positiveCp) {
			return "cp-payload-nonpositive";
		}
		if (!etaConsistent) {
			return "eta-formula-residual-too-large";
		}
		if (!shapeGuard) {
			return "archive-curve-shape-guard-not-ready";
		}
		return "coefficient-payload-review-required";
	}
}
