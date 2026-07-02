package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCtCpJOpenFoamCaseManifest {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-OpenFOAM-Case-Manifest-Packet";
	public static final String CAVEAT =
			"OpenFOAM case manifest binds geometry-backed CT/CP/J validation targets to external case keys, dimensional reference materialization readiness, and required result payload channels; no solver code, case directory, or raw OpenFOAM output is vendored, and runtime/gameplay auto-apply stay closed.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 8;
	public static final int REQUIRED_CHANNEL_ROW_COUNT = 26;
	public static final int MANIFEST_CASE_ROW_COUNT = 6;
	public static final int SUMMARY_ROW_COUNT = 16;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ REQUIRED_CHANNEL_ROW_COUNT
			+ MANIFEST_CASE_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final String CASE_TEMPLATE_FAMILY = "external-openfoam-steady-rotor-case-manifest";
	public static final String NEXT_REQUIRED_ACTION =
			"author-external-openfoam-case-template-and-record-case-sha256";

	private static final List<OpenFoamCaseManifestChannel> REQUIRED_CHANNELS = List.of(
			new OpenFoamCaseManifestChannel("source_case_sha256", "sha256", "provenance",
					true, true, true, 0.0, "prove exact external case archive identity"),
			new OpenFoamCaseManifestChannel("reference_thrust_coefficient_ct", "coefficient",
					"coefficient_reference", true, true, false,
					PropellerArchiveCtCpJOpenFoamValidationPlan.MAX_CT_RESIDUAL,
					"bind reviewed wind-tunnel CT reference for coefficient residual"),
			new OpenFoamCaseManifestChannel("cfd_thrust_coefficient_ct", "coefficient",
					"coefficient_result", true, true, false,
					PropellerArchiveCtCpJOpenFoamValidationPlan.MAX_CT_RESIDUAL,
					"feed OpenFOAM force-derived CT residual comparison"),
			new OpenFoamCaseManifestChannel("ct_residual_to_wind_tunnel", "ratio",
					"coefficient_residual", true, true, false,
					PropellerArchiveCtCpJOpenFoamValidationPlan.MAX_CT_RESIDUAL,
					"gate CT residual consistency against paired coefficient values"),
			new OpenFoamCaseManifestChannel("reference_power_coefficient_cp", "coefficient",
					"coefficient_reference", true, true, false,
					PropellerArchiveCtCpJOpenFoamValidationPlan.MAX_CP_RESIDUAL,
					"bind reviewed wind-tunnel CP reference for coefficient residual"),
			new OpenFoamCaseManifestChannel("cfd_power_coefficient_cp", "coefficient",
					"coefficient_result", true, true, false,
					PropellerArchiveCtCpJOpenFoamValidationPlan.MAX_CP_RESIDUAL,
					"feed OpenFOAM power-derived CP residual comparison"),
			new OpenFoamCaseManifestChannel("cp_residual_to_wind_tunnel", "ratio",
					"coefficient_residual", true, true, false,
					PropellerArchiveCtCpJOpenFoamValidationPlan.MAX_CP_RESIDUAL,
					"gate CP residual consistency against paired coefficient values"),
			new OpenFoamCaseManifestChannel("reference_efficiency_eta", "ratio",
					"coefficient_reference", true, true, false,
					PropellerArchiveCtCpJOpenFoamValidationPlan.MAX_ETA_RESIDUAL,
					"bind reviewed wind-tunnel eta reference for coefficient residual"),
			new OpenFoamCaseManifestChannel("cfd_efficiency_eta", "ratio", "coefficient_result",
					true, true, false, PropellerArchiveCtCpJOpenFoamValidationPlan.MAX_ETA_RESIDUAL,
					"feed OpenFOAM CT CP J efficiency residual comparison"),
			new OpenFoamCaseManifestChannel("eta_residual_to_wind_tunnel", "ratio",
					"coefficient_residual",
					true, true, false, PropellerArchiveCtCpJOpenFoamValidationPlan.MAX_ETA_RESIDUAL,
					"gate eta residual consistency against paired coefficient values"),
			new OpenFoamCaseManifestChannel("reference_thrust_newtons", "N", "dimensional_reference",
					true, false, true,
					PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.MAX_THRUST_RESIDUAL_TO_REFERENCE,
					"bind dimensional thrust reference for SI residual"),
			new OpenFoamCaseManifestChannel("cfd_thrust_newtons", "N", "dimensional_residual",
					true, false, true,
					PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.MAX_THRUST_RESIDUAL_TO_REFERENCE,
					"feed SI thrust residual comparison against dimensional rotor response"),
			new OpenFoamCaseManifestChannel("thrust_residual_to_reference", "ratio",
					"dimensional_residual", true, false, true,
					PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.MAX_THRUST_RESIDUAL_TO_REFERENCE,
					"gate SI thrust residual consistency against paired values"),
			new OpenFoamCaseManifestChannel("reference_shaft_power_watts", "W",
					"dimensional_reference", true, false, true,
					PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.MAX_SHAFT_POWER_RESIDUAL_TO_REFERENCE,
					"bind dimensional shaft-power reference for SI residual"),
			new OpenFoamCaseManifestChannel("cfd_shaft_power_watts", "W", "dimensional_residual",
					true, false, true,
					PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.MAX_SHAFT_POWER_RESIDUAL_TO_REFERENCE,
					"feed SI shaft-power residual comparison against dimensional rotor response"),
			new OpenFoamCaseManifestChannel("shaft_power_residual_to_reference", "ratio",
					"dimensional_residual", true, false, true,
					PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.MAX_SHAFT_POWER_RESIDUAL_TO_REFERENCE,
					"gate SI shaft-power residual consistency against paired values"),
			new OpenFoamCaseManifestChannel("reference_shaft_torque_newton_meters", "N*m",
					"dimensional_reference", true, false, true,
					PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.MAX_SHAFT_TORQUE_RESIDUAL_TO_REFERENCE,
					"bind dimensional reaction-torque reference for SI residual"),
			new OpenFoamCaseManifestChannel("cfd_shaft_torque_newton_meters", "N*m",
					"dimensional_residual", true, false, true,
					PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.MAX_SHAFT_TORQUE_RESIDUAL_TO_REFERENCE,
					"feed SI reaction-torque residual comparison against dimensional rotor response"),
			new OpenFoamCaseManifestChannel("shaft_torque_residual_to_reference", "ratio",
					"dimensional_residual", true, false, true,
					PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.MAX_SHAFT_TORQUE_RESIDUAL_TO_REFERENCE,
					"gate SI reaction-torque residual consistency against paired values"),
			new OpenFoamCaseManifestChannel("reference_induced_velocity_meters_per_second", "m/s",
					"dimensional_reference", true, false, true,
					PropellerArchiveCtCpJOpenFoamDimensionalResidualContract
							.MAX_INDUCED_VELOCITY_RESIDUAL_TO_REFERENCE,
					"bind dimensional induced-velocity reference for SI residual"),
			new OpenFoamCaseManifestChannel("cfd_induced_velocity_meters_per_second", "m/s",
					"dimensional_residual", true, false, true,
					PropellerArchiveCtCpJOpenFoamDimensionalResidualContract
							.MAX_INDUCED_VELOCITY_RESIDUAL_TO_REFERENCE,
					"feed wake induced-velocity residual comparison against dimensional rotor response"),
			new OpenFoamCaseManifestChannel("induced_velocity_residual_to_reference", "ratio",
					"dimensional_residual", true, false, true,
					PropellerArchiveCtCpJOpenFoamDimensionalResidualContract
							.MAX_INDUCED_VELOCITY_RESIDUAL_TO_REFERENCE,
					"gate wake induced-velocity residual consistency against paired values"),
			new OpenFoamCaseManifestChannel("reference_momentum_power_watts", "W",
					"dimensional_reference", true, false, true,
					PropellerArchiveCtCpJOpenFoamDimensionalResidualContract
							.MAX_MOMENTUM_POWER_RESIDUAL_TO_REFERENCE,
					"bind dimensional momentum-power reference for SI residual"),
			new OpenFoamCaseManifestChannel("cfd_momentum_power_watts", "W",
					"dimensional_residual", true, false, true,
					PropellerArchiveCtCpJOpenFoamDimensionalResidualContract
							.MAX_MOMENTUM_POWER_RESIDUAL_TO_REFERENCE,
					"feed wake momentum-power residual comparison against dimensional rotor response"),
			new OpenFoamCaseManifestChannel("momentum_power_residual_to_reference", "ratio",
					"dimensional_residual", true, false, true,
					PropellerArchiveCtCpJOpenFoamDimensionalResidualContract
							.MAX_MOMENTUM_POWER_RESIDUAL_TO_REFERENCE,
					"gate wake momentum-power residual consistency against paired values"),
			new OpenFoamCaseManifestChannel("solver_convergence_residual", "ratio",
					"solver_convergence", true, true, true,
					PropellerArchiveCtCpJOpenFoamResultContract.MAX_SOLVER_CONVERGENCE_RESIDUAL,
					"reject non-converged external OpenFOAM cases")
	);

	private PropellerArchiveCtCpJOpenFoamCaseManifest() {
	}

	public record OpenFoamCaseManifestChannel(
			String channelName,
			String unit,
			String evidenceRole,
			boolean required,
			boolean coefficientResultContractChannel,
			boolean dimensionalResidualContractChannel,
			double maxResidualRatio,
			String downstreamUse
	) {
	}

	public record OpenFoamCaseManifestRow(
			String presetName,
			String caseName,
			String caseKey,
			String solverFamily,
			String caseTemplateFamily,
			String performanceMatchId,
			String meshGeometryId,
			double queryAdvanceRatioJ,
			double queryRpm,
			double equivalentProjectMu,
			int minimumNeighborRows,
			boolean staticAnchorCase,
			int requiredCoefficientChannelCount,
			int requiredDimensionalChannelCount,
			int requiredManifestChannelCount,
			boolean sourceCaseSha256Required,
			boolean currentSourceCaseSha256Available,
			boolean currentCaseRunnable,
			boolean postReviewCaseBuildable,
			String referenceMaterializationScenarioName,
			boolean referenceMaterializationReady,
			int blockedOpenFoamReferenceRowCount,
			String referenceMaterializationNextRequiredAction,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String nextRequiredAction,
			String note
	) {
	}

	public record OpenFoamCaseManifestSummary(
			int manifestCaseCount,
			int staticAnchorCaseCount,
			int nonStaticCaseCount,
			int postReviewCaseBuildableCount,
			int currentCaseRunnableCount,
			int sourceCaseSha256RequiredCount,
			int currentSourceCaseSha256AvailableCount,
			int referenceMaterializationReadyCaseCount,
			int blockedOpenFoamReferenceRowTotal,
			int requiredCoefficientChannelTotal,
			int requiredDimensionalChannelTotal,
			int requiredManifestChannelTotal,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String currentReferenceMaterializationScenarioName,
			String currentReferenceMaterializationNextRequiredAction
	) {
	}

	public record CtCpJOpenFoamCaseManifestAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int requiredChannelRowCount,
			int manifestCaseRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<OpenFoamCaseManifestChannel> channels,
			List<OpenFoamCaseManifestRow> rows,
			OpenFoamCaseManifestSummary summary
	) {
		public CtCpJOpenFoamCaseManifestAudit {
			channels = List.copyOf(channels);
			rows = List.copyOf(rows);
		}
	}

	public static CtCpJOpenFoamCaseManifestAudit audit() {
		List<OpenFoamCaseManifestRow> rows = PropellerArchiveCtCpJOpenFoamValidationPlan.audit()
				.cases()
				.stream()
				.filter(PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase
						::postReviewOpenFoamCaseRunnable)
				.map(PropellerArchiveCtCpJOpenFoamCaseManifest::row)
				.toList();
		return new CtCpJOpenFoamCaseManifestAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				REQUIRED_CHANNEL_ROW_COUNT,
				MANIFEST_CASE_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				REQUIRED_CHANNELS,
				rows,
				summary(rows)
		);
	}

	public static OpenFoamCaseManifestChannel channel(String channelName) {
		return REQUIRED_CHANNELS.stream()
				.filter(channel -> channel.channelName().equals(channelName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown OpenFOAM case manifest channel: " + channelName));
	}

	public static OpenFoamCaseManifestRow row(String presetName, String caseName) {
		return audit().rows()
				.stream()
				.filter(row -> row.presetName().equals(presetName) && row.caseName().equals(caseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown OpenFOAM case manifest row: " + presetName + "/" + caseName));
	}

	public static OpenFoamCaseManifestRow row(
			PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase validationCase
	) {
		return row(validationCase, PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGate
				.scenario("current_lookup_and_openfoam_blocked"));
	}

	public static OpenFoamCaseManifestRow row(
			PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase validationCase,
			PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGate
					.OpenFoamDimensionalReferenceMaterializationScenario materialization
	) {
		if (validationCase == null) {
			throw new IllegalArgumentException("validationCase must not be null.");
		}
		if (materialization == null) {
			throw new IllegalArgumentException("reference materialization scenario must not be null.");
		}
		if (materialization.scenarioName() == null || materialization.scenarioName().isBlank()) {
			throw new IllegalArgumentException("reference materialization scenario name must not be blank.");
		}
		if (materialization.nextRequiredAction() == null || materialization.nextRequiredAction().isBlank()) {
			throw new IllegalArgumentException("reference materialization next required action must not be blank.");
		}
		if (!validationCase.postReviewOpenFoamCaseRunnable()) {
			throw new IllegalArgumentException(
					"OpenFOAM case manifest accepts only post-review geometry-backed targets.");
		}
		return new OpenFoamCaseManifestRow(
				validationCase.presetName(),
				validationCase.caseName(),
				caseKey(validationCase.presetName(), validationCase.caseName()),
				validationCase.solverFamily(),
				CASE_TEMPLATE_FAMILY,
				validationCase.performanceMatchId(),
				validationCase.geometryMatchId(),
				validationCase.queryAdvanceRatioJ(),
				validationCase.queryRpm(),
				validationCase.equivalentProjectMu(),
				validationCase.minimumNeighborRows(),
				validationCase.staticAnchorCase(),
				coefficientContractChannelCount(),
				dimensionalContractChannelCount(),
				REQUIRED_CHANNEL_ROW_COUNT,
				true,
				false,
				false,
				validationCase.postReviewOpenFoamCaseRunnable(),
				materialization.scenarioName(),
				materialization.referenceMaterializationReady(),
				materialization.blockedOpenFoamReferenceRowCount(),
				materialization.nextRequiredAction(),
				false,
				false,
				"BLOCKED",
				nextRequiredAction(materialization),
				note(materialization)
		);
	}

	private static int coefficientContractChannelCount() {
		return (int) REQUIRED_CHANNELS.stream()
				.filter(OpenFoamCaseManifestChannel::coefficientResultContractChannel)
				.count();
	}

	private static int dimensionalContractChannelCount() {
		return (int) REQUIRED_CHANNELS.stream()
				.filter(OpenFoamCaseManifestChannel::dimensionalResidualContractChannel)
				.count();
	}

	private static OpenFoamCaseManifestSummary summary(List<OpenFoamCaseManifestRow> rows) {
		int staticAnchors = 0;
		int buildable = 0;
		int runnable = 0;
		int hashRequired = 0;
		int hashAvailable = 0;
		int materializationReady = 0;
		int blockedReferenceRows = 0;
		int coefficientChannels = 0;
		int dimensionalChannels = 0;
		int manifestChannels = 0;
		int runtime = 0;
		int gameplay = 0;
		String currentScenario = "";
		String currentMaterializationAction = "";
		for (OpenFoamCaseManifestRow row : rows) {
			if (row.staticAnchorCase()) {
				staticAnchors++;
			}
			if (row.postReviewCaseBuildable()) {
				buildable++;
			}
			if (row.currentCaseRunnable()) {
				runnable++;
			}
			if (row.sourceCaseSha256Required()) {
				hashRequired++;
			}
			if (row.currentSourceCaseSha256Available()) {
				hashAvailable++;
			}
			if (row.referenceMaterializationReady()) {
				materializationReady++;
			}
			blockedReferenceRows += row.blockedOpenFoamReferenceRowCount();
			coefficientChannels += row.requiredCoefficientChannelCount();
			dimensionalChannels += row.requiredDimensionalChannelCount();
			manifestChannels += row.requiredManifestChannelCount();
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
			if (currentScenario.isBlank()) {
				currentScenario = row.referenceMaterializationScenarioName();
				currentMaterializationAction = row.referenceMaterializationNextRequiredAction();
			}
		}
		return new OpenFoamCaseManifestSummary(
				rows.size(),
				staticAnchors,
				rows.size() - staticAnchors,
				buildable,
				runnable,
				hashRequired,
				hashAvailable,
				materializationReady,
				blockedReferenceRows,
				coefficientChannels,
				dimensionalChannels,
				manifestChannels,
				runtime,
				gameplay,
				currentScenario,
				currentMaterializationAction
		);
	}

	private static String nextRequiredAction(
			PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGate
					.OpenFoamDimensionalReferenceMaterializationScenario materialization
	) {
		if (!materialization.referenceMaterializationReady()) {
			return materialization.nextRequiredAction();
		}
		return NEXT_REQUIRED_ACTION;
	}

	private static String note(
			PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGate
					.OpenFoamDimensionalReferenceMaterializationScenario materialization
	) {
		if (!materialization.referenceMaterializationReady()) {
			return "external case authoring is blocked until dimensional reference materialization opens";
		}
		return "external case is buildable after review but current case hash is absent";
	}

	private static String caseKey(String presetName, String caseName) {
		return presetName + "/" + caseName;
	}
}
