package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCtCpJOpenFoamCaseManifest {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-OpenFOAM-Case-Manifest-Packet";
	public static final String CAVEAT =
			"OpenFOAM case manifest binds geometry-backed CT/CP/J validation targets to external case keys and required output channels; no solver code, case directory, or raw OpenFOAM output is vendored, and runtime/gameplay auto-apply stay closed.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 7;
	public static final int REQUIRED_CHANNEL_ROW_COUNT = 10;
	public static final int MANIFEST_CASE_ROW_COUNT = 6;
	public static final int SUMMARY_ROW_COUNT = 12;
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
					true, false, false, 0.0, "prove exact external case archive identity"),
			new OpenFoamCaseManifestChannel("thrust_coefficient_ct", "coefficient",
					"coefficient_residual", true, true, false,
					PropellerArchiveCtCpJOpenFoamValidationPlan.MAX_CT_RESIDUAL,
					"feed CT residual comparison against reviewed wind-tunnel lookup"),
			new OpenFoamCaseManifestChannel("power_coefficient_cp", "coefficient",
					"coefficient_residual", true, true, false,
					PropellerArchiveCtCpJOpenFoamValidationPlan.MAX_CP_RESIDUAL,
					"feed CP residual comparison against reviewed wind-tunnel lookup"),
			new OpenFoamCaseManifestChannel("efficiency_eta", "ratio", "coefficient_residual",
					true, true, false, PropellerArchiveCtCpJOpenFoamValidationPlan.MAX_ETA_RESIDUAL,
					"feed eta residual comparison against reviewed wind-tunnel lookup"),
			new OpenFoamCaseManifestChannel("cfd_thrust_newtons", "N", "dimensional_residual",
					true, false, true,
					PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.MAX_THRUST_RESIDUAL_TO_REFERENCE,
					"feed SI thrust residual comparison against dimensional rotor response"),
			new OpenFoamCaseManifestChannel("cfd_shaft_power_watts", "W", "dimensional_residual",
					true, false, true,
					PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.MAX_SHAFT_POWER_RESIDUAL_TO_REFERENCE,
					"feed SI shaft-power residual comparison against dimensional rotor response"),
			new OpenFoamCaseManifestChannel("cfd_shaft_torque_newton_meters", "N*m",
					"dimensional_residual", true, false, true,
					PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.MAX_SHAFT_TORQUE_RESIDUAL_TO_REFERENCE,
					"feed SI reaction-torque residual comparison against dimensional rotor response"),
			new OpenFoamCaseManifestChannel("cfd_induced_velocity_mps", "m_per_s",
					"dimensional_residual", true, false, true,
					PropellerArchiveCtCpJOpenFoamDimensionalResidualContract
							.MAX_INDUCED_VELOCITY_RESIDUAL_TO_REFERENCE,
					"feed wake induced-velocity residual comparison against dimensional rotor response"),
			new OpenFoamCaseManifestChannel("cfd_momentum_power_watts", "W",
					"dimensional_residual", true, false, true,
					PropellerArchiveCtCpJOpenFoamDimensionalResidualContract
							.MAX_MOMENTUM_POWER_RESIDUAL_TO_REFERENCE,
					"feed wake momentum-power residual comparison against dimensional rotor response"),
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
			int requiredCoefficientChannelTotal,
			int requiredDimensionalChannelTotal,
			int requiredManifestChannelTotal,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount
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
		if (validationCase == null) {
			throw new IllegalArgumentException("validationCase must not be null.");
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
				PropellerArchiveCtCpJOpenFoamResultContract.REQUIRED_RESULT_CHANNEL_COUNT,
				PropellerArchiveCtCpJOpenFoamDimensionalResidualContract
						.REQUIRED_DIMENSIONAL_RESULT_CHANNEL_COUNT,
				REQUIRED_CHANNEL_ROW_COUNT,
				true,
				false,
				false,
				validationCase.postReviewOpenFoamCaseRunnable(),
				false,
				false,
				"BLOCKED",
				NEXT_REQUIRED_ACTION,
				"external OpenFOAM case must be authored and hashed outside the repository before results can enter contracts"
		);
	}

	private static OpenFoamCaseManifestSummary summary(List<OpenFoamCaseManifestRow> rows) {
		int staticAnchors = 0;
		int buildable = 0;
		int runnable = 0;
		int hashRequired = 0;
		int hashAvailable = 0;
		int coefficientChannels = 0;
		int dimensionalChannels = 0;
		int manifestChannels = 0;
		int runtime = 0;
		int gameplay = 0;
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
			coefficientChannels += row.requiredCoefficientChannelCount();
			dimensionalChannels += row.requiredDimensionalChannelCount();
			manifestChannels += row.requiredManifestChannelCount();
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
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
				coefficientChannels,
				dimensionalChannels,
				manifestChannels,
				runtime,
				gameplay
		);
	}

	private static String caseKey(String presetName, String caseName) {
		return presetName + "/" + caseName;
	}
}
