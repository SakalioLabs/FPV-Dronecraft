package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCtCpJOpenFoamRunSetup {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-OpenFOAM-Run-Setup-Packet";
	public static final String CAVEAT =
			"OpenFOAM run setup derives external CFD case inputs from the manifest, dimensional reference materialization gate, DroneConfig rotor geometry, and CT/CP/J run coordinates; it is audit-only and cannot vendor solver files, mutate runtime physics, or auto-apply gameplay tuning.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 9;
	public static final int RUN_SETUP_RULE_ROW_COUNT = 9;
	public static final int RUN_SETUP_ROW_COUNT =
			PropellerArchiveCtCpJOpenFoamCaseManifest.MANIFEST_CASE_ROW_COUNT;
	public static final int SUMMARY_ROW_COUNT = 20;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ RUN_SETUP_RULE_ROW_COUNT
			+ RUN_SETUP_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final double STANDARD_AMBIENT_TEMPERATURE_CELSIUS = 15.0;
	public static final double STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
	public static final double REFERENCE_AIR_DYNAMIC_VISCOSITY_PASCAL_SECONDS =
			PropellerArchiveRotorSpecRetuneReynoldsBudgetGate
					.REFERENCE_AIR_DYNAMIC_VISCOSITY_PASCAL_SECONDS;
	public static final double REYNOLDS_REFERENCE_STATION_FRACTION = 0.75;
	public static final String NEXT_REQUIRED_ACTION =
			"author-external-openfoam-case-template-and-record-case-sha256";

	private static final List<OpenFoamRunSetupRule> RULES = List.of(
			new OpenFoamRunSetupRule("case_manifest_required", true, true, true,
					"derive setup rows only from geometry-backed OpenFOAM manifest cases",
					"keep-openfoam-case-manifest-current"),
			new OpenFoamRunSetupRule("reference_materialization_required", true, false, true,
					"allow external case setup authoring only after reviewed CT/CP/J lookup and OpenFOAM dimensional reference materialization",
					"execute-clearance-evidence-ledger-before-reviewed-payload-output"),
			new OpenFoamRunSetupRule("exact_j_rpm_run_point", true, true, true,
					"preserve manifest J and RPM coordinates for external CFD case authoring",
					"bind-openfoam-case-template-to-manifest-run-point"),
			new OpenFoamRunSetupRule("freestream_from_advance_ratio", true, true, true,
					"use V=J*n*D so coefficient and SI result contracts share the same freestream",
					"author-openfoam-inlet-or-moving-reference-frame-from-run-setup"),
			new OpenFoamRunSetupRule("rotor_geometry_from_drone_config", true, true, true,
					"use DroneConfig rotor radius pitch blade count and representative chord for setup dimensions",
					"review-config-geometry-before-openfoam-case-export"),
			new OpenFoamRunSetupRule("standard_air_reference", true, true, true,
					"use explicit standard density temperature viscosity and speed of sound references",
					"keep-air-reference-visible-in-openfoam-results"),
			new OpenFoamRunSetupRule("mach_and_reynolds_diagnostics", true, true, true,
					"record helical tip Mach and 0.75R chord Reynolds for mesh and timestep review",
					"review-openfoam-mesh-yplus-and-time-step-against-run-setup"),
			new OpenFoamRunSetupRule("source_case_hash_required", true, false, true,
					"external case archive SHA-256 is still required before results can enter contracts",
					NEXT_REQUIRED_ACTION),
			new OpenFoamRunSetupRule("no_runtime_or_gameplay_auto_apply", true, true, true,
					"setup rows are offline CFD authoring evidence only",
					"keep-runtime-coupling-and-gameplay-auto-apply-closed")
	);

	private PropellerArchiveCtCpJOpenFoamRunSetup() {
	}

	public record OpenFoamRunSetupRule(
			String ruleName,
			boolean required,
			boolean currentSatisfied,
			boolean postReviewSatisfied,
			String requirement,
			String nextRequiredAction
	) {
	}

	public record OpenFoamRunSetupRow(
			String presetName,
			String caseName,
			String caseKey,
			String solverFamily,
			String caseTemplateFamily,
			String meshGeometryId,
			double queryAdvanceRatioJ,
			double queryRpm,
			double equivalentProjectMu,
			boolean staticAnchorCase,
			boolean sourceCaseSha256Required,
			boolean currentSourceCaseSha256Available,
			boolean currentCaseRunnable,
			boolean postReviewCaseBuildable,
			String referenceMaterializationScenarioName,
			boolean referenceMaterializationReady,
			int blockedOpenFoamReferenceRowCount,
			String referenceMaterializationNextRequiredAction,
			double airDensityKgPerCubicMeter,
			double ambientTemperatureCelsius,
			double speedOfSoundMetersPerSecond,
			double dynamicViscosityPascalSeconds,
			double rotorRadiusMeters,
			double propellerDiameterMeters,
			double diskAreaSquareMeters,
			double bladePitchMeters,
			double bladePitchToDiameterRatio,
			int bladeCount,
			double representativeBladeChordMeters,
			double bladeBetaAtSeventyPercentRadiusRadians,
			double revolutionsPerSecond,
			double angularVelocityRadiansPerSecond,
			double axialFreestreamSpeedMetersPerSecond,
			double tipSpeedMetersPerSecond,
			double helicalTipSpeedMetersPerSecond,
			double helicalTipMach,
			double reynoldsStationFraction,
			double reynoldsStationSpeedMetersPerSecond,
			double reynoldsStationChordNumber,
			boolean runSetupReadyForExternalAuthoring,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String nextRequiredAction,
			String note
	) {
	}

	public record OpenFoamRunSetupSummary(
			int runSetupRowCount,
			int currentRunnableCount,
			int postReviewCaseBuildableCount,
			int sourceCaseSha256RequiredCount,
			int currentSourceCaseSha256AvailableCount,
			int referenceMaterializationReadySetupCount,
			int runSetupReadyForExternalAuthoringCount,
			int blockedOpenFoamReferenceRowTotal,
			int staticAnchorCaseCount,
			double maxQueryAdvanceRatioJ,
			double maxQueryRpm,
			double maxAxialFreestreamSpeedMetersPerSecond,
			double maxHelicalTipMach,
			double minReynoldsStationChordNumber,
			double maxReynoldsStationChordNumber,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String currentReferenceMaterializationScenarioName,
			String currentReferenceMaterializationNextRequiredAction,
			String nextRequiredAction
	) {
	}

	public record CtCpJOpenFoamRunSetupAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int runSetupRuleRowCount,
			int runSetupRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<OpenFoamRunSetupRule> rules,
			List<OpenFoamRunSetupRow> rows,
			OpenFoamRunSetupSummary summary
	) {
		public CtCpJOpenFoamRunSetupAudit {
			rules = List.copyOf(rules);
			rows = List.copyOf(rows);
		}
	}

	public static CtCpJOpenFoamRunSetupAudit audit() {
		List<OpenFoamRunSetupRow> rows = PropellerArchiveCtCpJOpenFoamCaseManifest.audit()
				.rows()
				.stream()
				.map(PropellerArchiveCtCpJOpenFoamRunSetup::row)
				.toList();
		return new CtCpJOpenFoamRunSetupAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				RUN_SETUP_RULE_ROW_COUNT,
				RUN_SETUP_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				RULES,
				rows,
				summary(rows)
		);
	}

	public static OpenFoamRunSetupRule rule(String ruleName) {
		return RULES.stream()
				.filter(rule -> rule.ruleName().equals(ruleName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown OpenFOAM run setup rule: " + ruleName));
	}

	public static OpenFoamRunSetupRow row(String presetName, String caseName) {
		return audit().rows()
				.stream()
				.filter(row -> row.presetName().equals(presetName) && row.caseName().equals(caseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown OpenFOAM run setup row: " + presetName + "/" + caseName));
	}

	public static OpenFoamRunSetupRow row(
			PropellerArchiveCtCpJOpenFoamCaseManifest.OpenFoamCaseManifestRow manifestRow
	) {
		if (manifestRow == null) {
			throw new IllegalArgumentException("manifestRow must not be null.");
		}
		DroneConfig config = configFor(manifestRow.presetName());
		RotorSpec rotor = config.rotors().get(0);
		double radius = rotor.radiusMeters();
		double diameter = radius * 2.0;
		double diskArea = Math.PI * radius * radius;
		double revolutionsPerSecond = manifestRow.queryRpm() / 60.0;
		double angularVelocity = revolutionsPerSecond * 2.0 * Math.PI;
		double axialFreestreamSpeed = manifestRow.queryAdvanceRatioJ() * revolutionsPerSecond * diameter;
		double tipSpeed = Math.abs(angularVelocity) * radius;
		double helicalTipSpeed = Math.hypot(tipSpeed, axialFreestreamSpeed);
		double speedOfSound = DroneEnvironment.speedOfSoundMetersPerSecond(
				STANDARD_AMBIENT_TEMPERATURE_CELSIUS);
		double helicalTipMach = helicalTipSpeed / Math.max(1.0, speedOfSound);
		double stationTangentialSpeed = tipSpeed * REYNOLDS_REFERENCE_STATION_FRACTION;
		double stationSpeed = Math.hypot(stationTangentialSpeed, axialFreestreamSpeed);
		double stationReynolds = STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER
				* stationSpeed
				* rotor.representativeBladeChordMeters()
				/ Math.max(1.0e-9, REFERENCE_AIR_DYNAMIC_VISCOSITY_PASCAL_SECONDS);
		return new OpenFoamRunSetupRow(
				manifestRow.presetName(),
				manifestRow.caseName(),
				manifestRow.caseKey(),
				manifestRow.solverFamily(),
				manifestRow.caseTemplateFamily(),
				manifestRow.meshGeometryId(),
				manifestRow.queryAdvanceRatioJ(),
				manifestRow.queryRpm(),
				manifestRow.equivalentProjectMu(),
				manifestRow.staticAnchorCase(),
				manifestRow.sourceCaseSha256Required(),
				manifestRow.currentSourceCaseSha256Available(),
				manifestRow.currentCaseRunnable(),
				manifestRow.postReviewCaseBuildable(),
				manifestRow.referenceMaterializationScenarioName(),
				manifestRow.referenceMaterializationReady(),
				manifestRow.blockedOpenFoamReferenceRowCount(),
				manifestRow.referenceMaterializationNextRequiredAction(),
				STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER,
				STANDARD_AMBIENT_TEMPERATURE_CELSIUS,
				speedOfSound,
				REFERENCE_AIR_DYNAMIC_VISCOSITY_PASCAL_SECONDS,
				radius,
				diameter,
				diskArea,
				rotor.bladePitchMeters(),
				rotor.bladePitchToDiameterRatio(),
				rotor.bladeCount(),
				rotor.representativeBladeChordMeters(),
				rotor.geometricBladePitchAngleRadians(
						RotorSpec.BLADE_GEOMETRY_REFERENCE_STATION_FRACTION),
				revolutionsPerSecond,
				angularVelocity,
				axialFreestreamSpeed,
				tipSpeed,
				helicalTipSpeed,
				helicalTipMach,
				REYNOLDS_REFERENCE_STATION_FRACTION,
				stationSpeed,
				stationReynolds,
				manifestRow.postReviewCaseBuildable() && manifestRow.referenceMaterializationReady(),
				false,
				false,
				"BLOCKED",
				nextRequiredAction(manifestRow),
				note(manifestRow)
		);
	}

	private static OpenFoamRunSetupSummary summary(List<OpenFoamRunSetupRow> rows) {
		int currentRunnable = 0;
		int buildable = 0;
		int hashRequired = 0;
		int hashAvailable = 0;
		int materializationReady = 0;
		int authoringReady = 0;
		int blockedReferenceRows = 0;
		int staticAnchors = 0;
		int runtime = 0;
		int gameplay = 0;
		double maxJ = 0.0;
		double maxRpm = 0.0;
		double maxAxial = 0.0;
		double maxMach = 0.0;
		double minReynolds = Double.POSITIVE_INFINITY;
		double maxReynolds = 0.0;
		String currentScenario = "";
		String currentMaterializationAction = "";
		String nextAction = NEXT_REQUIRED_ACTION;
		for (OpenFoamRunSetupRow row : rows) {
			if (row.currentCaseRunnable()) {
				currentRunnable++;
			}
			if (row.postReviewCaseBuildable()) {
				buildable++;
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
			if (row.runSetupReadyForExternalAuthoring()) {
				authoringReady++;
			}
			blockedReferenceRows += row.blockedOpenFoamReferenceRowCount();
			if (row.staticAnchorCase()) {
				staticAnchors++;
			}
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
			maxJ = Math.max(maxJ, row.queryAdvanceRatioJ());
			maxRpm = Math.max(maxRpm, row.queryRpm());
			maxAxial = Math.max(maxAxial, row.axialFreestreamSpeedMetersPerSecond());
			maxMach = Math.max(maxMach, row.helicalTipMach());
			minReynolds = Math.min(minReynolds, row.reynoldsStationChordNumber());
			maxReynolds = Math.max(maxReynolds, row.reynoldsStationChordNumber());
			if (currentScenario.isBlank()) {
				currentScenario = row.referenceMaterializationScenarioName();
				currentMaterializationAction = row.referenceMaterializationNextRequiredAction();
				nextAction = row.nextRequiredAction();
			}
		}
		if (!Double.isFinite(minReynolds)) {
			minReynolds = 0.0;
		}
		return new OpenFoamRunSetupSummary(
				rows.size(),
				currentRunnable,
				buildable,
				hashRequired,
				hashAvailable,
				materializationReady,
				authoringReady,
				blockedReferenceRows,
				staticAnchors,
				maxJ,
				maxRpm,
				maxAxial,
				maxMach,
				minReynolds,
				maxReynolds,
				runtime,
				gameplay,
				currentScenario,
				currentMaterializationAction,
				nextAction
		);
	}

	private static String nextRequiredAction(
			PropellerArchiveCtCpJOpenFoamCaseManifest.OpenFoamCaseManifestRow manifestRow
	) {
		if (!manifestRow.referenceMaterializationReady()) {
			return manifestRow.referenceMaterializationNextRequiredAction();
		}
		return NEXT_REQUIRED_ACTION;
	}

	private static String note(
			PropellerArchiveCtCpJOpenFoamCaseManifest.OpenFoamCaseManifestRow manifestRow
	) {
		if (!manifestRow.referenceMaterializationReady()) {
			return "external OpenFOAM case setup is computable, but reference materialization is still blocked";
		}
		return "external OpenFOAM case setup is computable, but case archive hash and solver output remain absent";
	}

	private static DroneConfig configFor(String presetName) {
		return switch (presetName) {
			case "racingQuad" -> DroneConfig.racingQuad();
			case "apDrone" -> DroneConfig.apDrone();
			case "cinewhoop" -> DroneConfig.cinewhoop();
			case "heavyLift" -> DroneConfig.heavyLift();
			default -> throw new IllegalArgumentException("unknown DroneConfig preset: " + presetName);
		};
	}
}
