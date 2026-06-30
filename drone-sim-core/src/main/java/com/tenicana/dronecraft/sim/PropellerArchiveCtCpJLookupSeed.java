package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCtCpJLookupSeed {
	public static final String SOURCE_ID = "User-Propeller-Archive-CT-CP-J-Lookup-Seed-Packet";
	public static final String CAVEAT =
			"CT/CP/J lookup seed defines the offline lookup payload and readiness boundary after reviewed import; it imports no raw rows, emits no fitted coefficients, and never enables runtime coupling or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 7;
	public static final int LOOKUP_FIELD_ROW_COUNT = 13;
	public static final int PRESET_SEED_ROW_COUNT = 4;
	public static final int LOOKUP_STAGE_ROW_COUNT = 7;
	public static final int SUMMARY_ROW_COUNT = 14;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ LOOKUP_FIELD_ROW_COUNT
			+ PRESET_SEED_ROW_COUNT
			+ LOOKUP_STAGE_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final String LOOKUP_INTERPOLATION_POLICY =
			"reviewed-j-grid-shape-preserving-interpolation-inside-rpm-bin";
	public static final String RPM_BIN_POLICY =
			"bracketed-rpm-bin-with-j-zero-static-anchor-preserved";
	public static final String EQUIVALENT_MU_POLICY =
			"equivalent-project-mu=j-over-pi-for-current-advance-scale-audit-only";
	public static final String NEXT_REQUIRED_ACTION =
			"review-source-license-then-import-reviewed-ct-cp-j-rows-into-offline-lookup";

	private static final List<LookupField> LOOKUP_FIELDS = List.of(
			new LookupField("preset_name", "text", true, "DroneConfig preset key",
					"join lookup seed to preset reference rows", false, false),
			new LookupField("source_archive_sha256", "sha256", true,
					"PropellerArchiveSourceFingerprint archive hash", "prove source identity", false, false),
			new LookupField("performance_match_id", "text", true,
					"reviewed propeller performance source", "trace CT/CP/J curve provenance", false, false),
			new LookupField("geometry_match_id", "text", true,
					"reviewed blade geometry source or blocker", "trace full-simulation lookup coverage", false, false),
			new LookupField("advance_ratio_grid", "J", true,
					"reviewed performance rows", "bound CT/CP lookup domain", false, false),
			new LookupField("rpm_bin", "rpm", true,
					"reviewed performance rows", "preserve speed-dependent coefficient variation", false, false),
			new LookupField("ct_lookup_curve", "coefficient", true,
					"accepted CT(J,rpm) fit", "thrust coefficient lookup target", false, false),
			new LookupField("cp_lookup_curve", "coefficient", true,
					"accepted CP(J,rpm) fit", "shaft-power coefficient lookup target", false, false),
			new LookupField("eta_consistency", "ratio", true,
					"eta equals J*CT/CP consistency check", "reject thrust and power mismatch", false, false),
			new LookupField("static_ct_anchor", "coefficient", true,
					"J-zero reviewed rows", "hover/static thrust anchor", false, false),
			new LookupField("static_cp_anchor", "coefficient", true,
					"J-zero reviewed rows", "hover/static power anchor", false, false),
			new LookupField("equivalent_project_mu", "ratio", true,
					"J/pi conversion policy", "compare axial propeller J with current advance-scale audit", false, false),
			new LookupField("lookup_weight", "ratio", true,
					"compact reference handoff gate", "keep playable reference weight zero until accepted", false, false)
	);

	private static final List<LookupStage> LOOKUP_STAGES = List.of(
			new LookupStage("source_license_review", false, true, true,
					"review-source-license-before-importing-raw-rows"),
			new LookupStage("reviewed_raw_row_import", false, true, true,
					"import-reviewed-raw-rows-into-offline-fitting-pipeline"),
			new LookupStage("lookup_seed_generation", false, true, true,
					"materialize-reviewed-ct-cp-j-domain-and-static-anchors"),
			new LookupStage("curve_fit_quality_acceptance", false, false, true,
					"accept-fit-quality-before-lookup-reference-export"),
			new LookupStage("full_simulation_coverage", false, false, true,
					"resolve-cinewhoop-blade-count-and-heavy-lift-geometry-coverage"),
			new LookupStage("compact_reference_handoff", false, false, true,
					"review-compact-reference-before-playable-handoff"),
			new LookupStage("runtime_leak_guard", true, true, true,
					"keep-runtime-coupling-and-gameplay-auto-apply-closed")
	);

	private PropellerArchiveCtCpJLookupSeed() {
	}

	public record LookupField(
			String fieldName,
			String unit,
			boolean required,
			String source,
			String downstreamUse,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed
	) {
	}

	public record PresetLookupSeed(
			String presetName,
			String sourceArchiveSha256,
			String performanceMatchId,
			String geometryMatchId,
			int targetBladeCount,
			int matchedBladeCount,
			int performanceSampleRowCount,
			int staticSampleRowCount,
			double advanceRatioMin,
			double advanceRatioMax,
			double rpmMin,
			double rpmMax,
			double staticCtMean,
			double staticCpMean,
			double maxEfficiency,
			boolean currentLookupSeedAllowed,
			boolean postReviewCtCpJLookupSeedAllowed,
			boolean postReviewFullSimulationLookupSeedAllowed,
			boolean compactReferenceExportAllowedAfterReview,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String interpolationPolicy,
			String rpmBinPolicy,
			String equivalentMuPolicy,
			String currentBlocker,
			String postReviewBlocker,
			String currentNextRequiredAction,
			String postReviewNextRequiredAction
	) {
	}

	public record LookupStage(
			String stageName,
			boolean currentSatisfied,
			boolean reviewedImportSatisfied,
			boolean syntheticTargetSatisfied,
			String nextRequiredAction
	) {
	}

	public record LookupSeedSummary(
			int lookupFieldCount,
			int presetSeedCount,
			int currentLookupSeedAllowedCount,
			int postReviewCtCpJLookupSeedAllowedCount,
			int postReviewFullSimulationLookupSeedAllowedCount,
			int compactReferenceExportAllowedAfterReviewCount,
			int runtimeCouplingAllowedPresetCount,
			int gameplayAutoApplyAllowedPresetCount,
			boolean currentRawImportAllowed,
			boolean reviewedRawImportContractReady,
			boolean fullLookupRuntimeReady,
			boolean playableReferenceAllowed,
			String sourceArchiveSha256,
			String nextRequiredAction
	) {
	}

	public record CtCpJLookupSeedAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int lookupFieldRowCount,
			int presetSeedRowCount,
			int lookupStageRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<LookupField> fields,
			List<PresetLookupSeed> presets,
			List<LookupStage> stages,
			LookupSeedSummary summary
	) {
		public CtCpJLookupSeedAudit {
			fields = List.copyOf(fields);
			presets = List.copyOf(presets);
			stages = List.copyOf(stages);
		}
	}

	public static CtCpJLookupSeedAudit audit() {
		List<PresetLookupSeed> presets = PropellerArchiveFitImportContract.audit()
				.presets()
				.stream()
				.map(input -> preset(input.presetName()))
				.toList();
		return new CtCpJLookupSeedAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				LOOKUP_FIELD_ROW_COUNT,
				PRESET_SEED_ROW_COUNT,
				LOOKUP_STAGE_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				LOOKUP_FIELDS,
				presets,
				LOOKUP_STAGES,
				summary(presets)
		);
	}

	public static LookupField field(String fieldName) {
		return LOOKUP_FIELDS.stream()
				.filter(field -> field.fieldName().equals(fieldName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("unknown CT/CP/J lookup field: " + fieldName));
	}

	public static PresetLookupSeed preset(String presetName) {
		PropellerArchiveDatasetTriage.PresetPerformanceMatch match =
				PropellerArchiveDatasetTriage.presetMatch(presetName);
		PropellerArchiveFitImportContract.PresetFitInputContract input =
				PropellerArchiveFitImportContract.preset(presetName);
		PropellerArchiveCurveFitPlan.PresetCurveFitPlan fit =
				PropellerArchiveCurveFitPlan.preset(presetName);
		String archiveSha256 = PropellerArchiveSourceFingerprint.audit().archive().archiveSha256();
		boolean currentAllowed = PropellerArchiveSourceFingerprint.CURRENT_IMPORT_ALLOWED
				&& input.currentImportAllowed();
		boolean ctCpJAllowedAfterReview = fit.ctCpCurveFitAllowedAfterReview();
		boolean fullLookupAllowedAfterReview = fit.fullSimulationCurveFitAllowedAfterReview();
		boolean referenceExportAllowed = fit.compactReferenceExportAllowedAfterReview();
		return new PresetLookupSeed(
				match.presetName(),
				archiveSha256,
				match.matchedPropName(),
				input.geometryMatchId(),
				match.targetBladeCount(),
				match.matchedBladeCount(),
				match.sampleRowCount(),
				match.staticRowCount(),
				match.advanceRatioMin(),
				match.advanceRatioMax(),
				match.rpmMin(),
				match.rpmMax(),
				match.staticCtMean(),
				match.staticCpMean(),
				match.maxEfficiency(),
				currentAllowed,
				ctCpJAllowedAfterReview,
				fullLookupAllowedAfterReview,
				referenceExportAllowed,
				false,
				false,
				LOOKUP_INTERPOLATION_POLICY,
				RPM_BIN_POLICY,
				EQUIVALENT_MU_POLICY,
				currentAllowed ? "none" : "source-license-review-required",
				fit.postReviewBlocker(),
				currentAllowed ? fit.nextRequiredAction() : NEXT_REQUIRED_ACTION,
				fit.nextRequiredAction()
		);
	}

	public static LookupStage stage(String stageName) {
		return LOOKUP_STAGES.stream()
				.filter(stage -> stage.stageName().equals(stageName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("unknown CT/CP/J lookup stage: " + stageName));
	}

	private static LookupSeedSummary summary(List<PresetLookupSeed> presets) {
		int current = 0;
		int ctCpJ = 0;
		int full = 0;
		int reference = 0;
		int runtime = 0;
		int gameplay = 0;
		for (PresetLookupSeed preset : presets) {
			if (preset.currentLookupSeedAllowed()) {
				current++;
			}
			if (preset.postReviewCtCpJLookupSeedAllowed()) {
				ctCpJ++;
			}
			if (preset.postReviewFullSimulationLookupSeedAllowed()) {
				full++;
			}
			if (preset.compactReferenceExportAllowedAfterReview()) {
				reference++;
			}
			if (preset.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (preset.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		PropellerArchiveSourceFingerprint.SourceFingerprintSummary sourceSummary =
				PropellerArchiveSourceFingerprint.audit().summary();
		PropellerArchiveFitImportContract.ImportContractSummary importSummary =
				PropellerArchiveFitImportContract.audit().summary();
		boolean fullRuntimeReady = sourceSummary.currentImportAllowed()
				&& current == presets.size()
				&& reference == presets.size();
		boolean playableReferenceAllowed = reference == presets.size()
				&& runtime == 0
				&& gameplay == 0;
		return new LookupSeedSummary(
				LOOKUP_FIELDS.size(),
				presets.size(),
				current,
				ctCpJ,
				full,
				reference,
				runtime,
				gameplay,
				sourceSummary.currentImportAllowed(),
				importSummary.reviewedRawImportContractReady(),
				fullRuntimeReady,
				playableReferenceAllowed,
				PropellerArchiveSourceFingerprint.ARCHIVE_SHA256,
				NEXT_REQUIRED_ACTION
		);
	}
}
