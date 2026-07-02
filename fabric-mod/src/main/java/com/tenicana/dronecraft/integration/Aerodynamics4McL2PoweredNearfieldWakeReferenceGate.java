package com.tenicana.dronecraft.integration;

import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGate;
import java.util.List;

public final class Aerodynamics4McL2PoweredNearfieldWakeReferenceGate {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Nearfield-Wake-Reference-Gate-Packet";
	public static final String CAVEAT =
			"Nearfield wake reference gate combines hover surface-wake, cruise skew-wake, and OpenFOAM CT/CP/J dimensional rotor-reference handoffs with coefficient lookup shape-guard and inherited archive curve-shape diagnostics into audit-only export readiness; it does not enable runtime coupling or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_COUNT = 10;
	public static final int SCENARIO_SAMPLE_COUNT = 5;
	public static final int SCENARIO_METRIC_COUNT = 59;
	public static final int SUMMARY_METRIC_ROW_COUNT = 31;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredNearfieldWakeReferenceGate() {
	}

	public record PoweredNearfieldWakeReferenceSummary(
			boolean nearfieldReferencePackageExportAllowed,
			int blockerCount,
			boolean hoverSurfaceWakeReferenceBlocker,
			boolean cruiseSkewWakeReferenceBlocker,
			boolean openFoamDimensionalReferenceBlocker,
			String openFoamReferenceMaterializationScenarioName,
			boolean openFoamLookupReferenceMaterialExportAllowed,
			boolean openFoamReferenceMaterializationReady,
			String openFoamReferenceMaterializationNextRequiredAction,
			boolean hoverLabValidationAccepted,
			boolean cruiseLabValidationAccepted,
			boolean hoverErrorBudgetReady,
			boolean cruiseErrorBudgetReady,
			boolean hoverReferenceMaterialExportAllowed,
			boolean cruiseReferenceMaterialExportAllowed,
			boolean openFoamLookupExecutionContractReady,
			boolean openFoamDimensionalSupportReady,
			boolean openFoamSolverQualityContractReady,
			int openFoamSolverQualityBlockerCount,
			int openFoamSolverQualityBlockerRowCount,
			String openFoamSolverQualityNextRequiredAction,
			boolean openFoamCoefficientLookupShapeGuardReady,
			int openFoamCoefficientLookupShapeGuardReadyRowCount,
			int openFoamCoefficientLookupShapeGuardInheritedScenarioCount,
			int openFoamCoefficientLookupShapeGuardBlockedScenarioCount,
			int openFoamCoefficientNegativeThrustTailExecutionInputRowCount,
			double openFoamCoefficientArchiveCurveEtaFormulaResidual,
			double openFoamCoefficientArchiveCurveCtIncrease,
			int openFoamArchiveCurveShapeGuardInheritedReferenceCount,
			int openFoamNegativeThrustTailReferenceCount,
			double openFoamMaxArchiveCurveEtaFormulaResidual,
			double openFoamMaxArchiveCurveCtIncrease,
			int openFoamArchiveCurveShapeGuardCompleteRowCount,
			boolean openFoamDimensionalReferenceReviewed,
			boolean openFoamReferenceMaterialExportAllowed,
			int hoverExpectedReferenceRowCount,
			int cruiseExpectedReferenceRowCount,
			int openFoamExpectedReferenceRowCount,
			int openFoamAvailableReferenceRowCount,
			int openFoamBlockedReferenceRowCount,
			int totalExpectedReferenceRowCount,
			int hoverReadyErrorBudgetGroupCount,
			int cruiseReadyErrorBudgetGroupCount,
			int hoverBlockedErrorBudgetGroupCount,
			int cruiseBlockedErrorBudgetGroupCount,
			double hoverMaxPressureErrorRatio,
			double hoverMaxWakeVelocityErrorRatio,
			double hoverMaxArrivalBandErrorRatio,
			double cruiseMaxPressureErrorRatio,
			double cruiseMaxWakeVelocityErrorRatio,
			double cruiseMaxArrivalWindowErrorRatio,
			double cruiseMaxMomentumErrorRatio,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String referencePayloadKind,
			String openFoamReferencePayloadKind,
			String nextRequiredAction,
			String status,
			String message
	) {
	}

	public record PoweredNearfieldWakeReferenceScenario(
			String scenarioName,
			PoweredNearfieldWakeReferenceSummary summary
	) {
	}

	public record OpenFoamDimensionalReferenceReadiness(
			boolean lookupExecutionContractReady,
			boolean dimensionalSupportReady,
			String referenceMaterializationScenarioName,
			boolean lookupReferenceMaterialExportAllowed,
			boolean referenceMaterializationReady,
			String referenceMaterializationNextRequiredAction,
			boolean openFoamSolverQualityContractReady,
			int openFoamSolverQualityBlockerCount,
			int openFoamSolverQualityBlockerRowCount,
			String openFoamSolverQualityNextRequiredAction,
			boolean openFoamCoefficientLookupShapeGuardReady,
			int openFoamCoefficientLookupShapeGuardReadyRowCount,
			int openFoamCoefficientLookupShapeGuardInheritedScenarioCount,
			int openFoamCoefficientLookupShapeGuardBlockedScenarioCount,
			int openFoamCoefficientNegativeThrustTailExecutionInputRowCount,
			double openFoamCoefficientArchiveCurveEtaFormulaResidual,
			double openFoamCoefficientArchiveCurveCtIncrease,
			int archiveCurveShapeGuardInheritedReferenceCount,
			int negativeThrustTailReferenceCount,
			double maxArchiveCurveEtaFormulaResidual,
			double maxArchiveCurveCtIncrease,
			int archiveCurveShapeGuardCompleteRowCount,
			boolean dimensionalReferenceReviewed,
			boolean referenceMaterialExportAllowed,
			int expectedReferenceRowCount,
			int availableReferenceRowCount,
			int blockedReferenceRowCount,
			String referencePayloadKind
	) {
		public OpenFoamDimensionalReferenceReadiness {
			if (expectedReferenceRowCount < 0 || availableReferenceRowCount < 0 || blockedReferenceRowCount < 0) {
				throw new IllegalArgumentException("OpenFOAM reference row counts must be non-negative.");
			}
			if (openFoamCoefficientLookupShapeGuardReadyRowCount < 0
					|| openFoamCoefficientLookupShapeGuardInheritedScenarioCount < 0
					|| openFoamCoefficientLookupShapeGuardBlockedScenarioCount < 0
					|| openFoamCoefficientNegativeThrustTailExecutionInputRowCount < 0) {
				throw new IllegalArgumentException(
						"OpenFOAM coefficient lookup shape-guard counts must be non-negative.");
			}
			if (openFoamSolverQualityBlockerCount < 0 || openFoamSolverQualityBlockerRowCount < 0) {
				throw new IllegalArgumentException("OpenFOAM solver-quality blocker counts must be non-negative.");
			}
			if (archiveCurveShapeGuardInheritedReferenceCount < 0
					|| negativeThrustTailReferenceCount < 0
					|| archiveCurveShapeGuardCompleteRowCount < 0) {
				throw new IllegalArgumentException("OpenFOAM archive curve-shape counts must be non-negative.");
			}
			if (!Double.isFinite(maxArchiveCurveEtaFormulaResidual)
					|| maxArchiveCurveEtaFormulaResidual < 0.0
					|| !Double.isFinite(maxArchiveCurveCtIncrease)
					|| maxArchiveCurveCtIncrease < 0.0) {
				throw new IllegalArgumentException("OpenFOAM archive curve-shape residuals must be finite.");
			}
			if (!Double.isFinite(openFoamCoefficientArchiveCurveEtaFormulaResidual)
					|| openFoamCoefficientArchiveCurveEtaFormulaResidual < 0.0
					|| !Double.isFinite(openFoamCoefficientArchiveCurveCtIncrease)
					|| openFoamCoefficientArchiveCurveCtIncrease < 0.0) {
				throw new IllegalArgumentException(
						"OpenFOAM coefficient lookup shape-guard residuals must be finite.");
			}
			if (availableReferenceRowCount + blockedReferenceRowCount != expectedReferenceRowCount) {
				throw new IllegalArgumentException("OpenFOAM available and blocked rows must sum to expected rows.");
			}
			if (openFoamCoefficientLookupShapeGuardReadyRowCount > expectedReferenceRowCount) {
				throw new IllegalArgumentException(
						"OpenFOAM coefficient lookup shape-guard ready rows must not exceed expected rows.");
			}
			if (openFoamSolverQualityBlockerRowCount > expectedReferenceRowCount) {
				throw new IllegalArgumentException(
						"OpenFOAM solver-quality blocker rows must not exceed expected rows.");
			}
			if (archiveCurveShapeGuardCompleteRowCount > expectedReferenceRowCount) {
				throw new IllegalArgumentException(
						"OpenFOAM archive curve-shape complete rows must not exceed expected rows.");
			}
			if (openFoamSolverQualityNextRequiredAction == null || openFoamSolverQualityNextRequiredAction.isBlank()) {
				throw new IllegalArgumentException(
						"OpenFOAM solver-quality next required action must not be blank.");
			}
			if (referenceMaterializationScenarioName == null || referenceMaterializationScenarioName.isBlank()) {
				throw new IllegalArgumentException(
						"OpenFOAM reference materialization scenario name must not be blank.");
			}
			if (referenceMaterializationNextRequiredAction == null
					|| referenceMaterializationNextRequiredAction.isBlank()) {
				throw new IllegalArgumentException(
						"OpenFOAM reference materialization next required action must not be blank.");
			}
			if (referencePayloadKind == null || referencePayloadKind.isBlank()) {
				throw new IllegalArgumentException("OpenFOAM reference payload kind must not be blank.");
			}
		}
	}

	public record PoweredNearfieldWakeReferenceExtrema(
			int scenarioCount,
			int readyScenarioCount,
			int blockedScenarioCount,
			int maxBlockerCount,
			int hoverReferenceBlockerScenarioCount,
			int cruiseReferenceBlockerScenarioCount,
			int openFoamReferenceBlockerScenarioCount,
			int referencePackageExportAllowedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			int maxTotalExpectedReferenceRowCount,
			int maxHoverBlockedErrorBudgetGroupCount,
			int maxCruiseBlockedErrorBudgetGroupCount,
			int maxOpenFoamBlockedReferenceRowCount,
			int maxOpenFoamAvailableReferenceRowCount,
			int openFoamLookupReferenceMaterialExportAllowedScenarioCount,
			int openFoamReferenceMaterializationReadyScenarioCount,
			int maxOpenFoamSolverQualityBlockerCount,
			int maxOpenFoamSolverQualityBlockerRowCount,
			int maxOpenFoamCoefficientLookupShapeGuardReadyRowCount,
			int maxOpenFoamCoefficientLookupShapeGuardInheritedScenarioCount,
			int maxOpenFoamCoefficientLookupShapeGuardBlockedScenarioCount,
			int maxOpenFoamCoefficientNegativeThrustTailExecutionInputRowCount,
			double maxOpenFoamCoefficientArchiveCurveEtaFormulaResidual,
			double maxOpenFoamCoefficientArchiveCurveCtIncrease,
			int maxOpenFoamArchiveCurveShapeGuardInheritedReferenceCount,
			int maxOpenFoamNegativeThrustTailReferenceCount,
			double maxOpenFoamArchiveCurveEtaFormulaResidual,
			double maxOpenFoamArchiveCurveCtIncrease,
			int maxOpenFoamArchiveCurveShapeGuardCompleteRowCount,
			double maxCruiseMomentumErrorRatio
	) {
	}

	public record PoweredNearfieldWakeReferenceAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int scenarioSampleCount,
			int scenarioMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredNearfieldWakeReferenceScenario> scenarios,
			PoweredNearfieldWakeReferenceExtrema extrema
	) {
		public PoweredNearfieldWakeReferenceAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static PoweredNearfieldWakeReferenceAudit audit() {
		Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffAudit hoverAudit =
				Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.audit();
		Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffAudit cruiseAudit =
				Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.audit();
		Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffSummary hoverCurrent =
				hover(hoverAudit, "current_lab_validation_blocked");
		Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffSummary hoverReady =
				hover(hoverAudit, "lab_accepted_error_budget_ready");
		Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffSummary cruiseCurrent =
				cruise(cruiseAudit, "current_lab_validation_blocked");
		Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffSummary cruiseReady =
				cruise(cruiseAudit, "lab_accepted_error_budget_ready");
		OpenFoamDimensionalReferenceReadiness openFoamCurrent = currentOpenFoamReferenceReadiness();
		OpenFoamDimensionalReferenceReadiness openFoamReady = reviewedOpenFoamReferenceReadiness();
		List<PoweredNearfieldWakeReferenceScenario> scenarios = List.of(
				new PoweredNearfieldWakeReferenceScenario(
						"current_hover_cruise_and_openfoam_reference_blocked",
						gate(hoverCurrent, cruiseCurrent, openFoamCurrent)),
				new PoweredNearfieldWakeReferenceScenario(
						"hover_ready_cruise_and_openfoam_reference_blocked",
						gate(hoverReady, cruiseCurrent, openFoamCurrent)),
				new PoweredNearfieldWakeReferenceScenario(
						"cruise_ready_hover_and_openfoam_reference_blocked",
						gate(hoverCurrent, cruiseReady, openFoamCurrent)),
				new PoweredNearfieldWakeReferenceScenario(
						"hover_and_cruise_ready_openfoam_reference_blocked",
						gate(hoverReady, cruiseReady, openFoamCurrent)),
				new PoweredNearfieldWakeReferenceScenario(
						"hover_cruise_and_openfoam_reference_ready",
						gate(hoverReady, cruiseReady, openFoamReady))
		);
		return new PoweredNearfieldWakeReferenceAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				SCENARIO_SAMPLE_COUNT,
				SCENARIO_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				scenarios,
				extrema(scenarios)
		);
	}

	public static PoweredNearfieldWakeReferenceSummary gate(
			Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffSummary hover,
			Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffSummary cruise
	) {
		if (hover == null || cruise == null) {
			throw new IllegalArgumentException(
					"hover, cruise, and OpenFOAM reference handoff summaries are required.");
		}
		return gate(hover, cruise, currentOpenFoamReferenceReadiness());
	}

	public static PoweredNearfieldWakeReferenceSummary gate(
			Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffSummary hover,
			Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffSummary cruise,
			OpenFoamDimensionalReferenceReadiness openFoam
	) {
		if (hover == null || cruise == null || openFoam == null) {
			throw new IllegalArgumentException(
					"hover, cruise, and OpenFOAM reference handoff summaries are required.");
		}
		boolean hoverBlocker = !hover.referenceMaterialExportAllowed();
		boolean cruiseBlocker = !cruise.referenceMaterialExportAllowed();
		boolean openFoamExportAllowed = openFoam.referenceMaterialExportAllowed()
				&& openFoam.availableReferenceRowCount() == openFoam.expectedReferenceRowCount()
				&& openFoam.openFoamCoefficientLookupShapeGuardReady()
				&& openFoam.openFoamCoefficientLookupShapeGuardReadyRowCount() == openFoam.expectedReferenceRowCount()
				&& openFoam.archiveCurveShapeGuardCompleteRowCount() == openFoam.expectedReferenceRowCount();
		boolean openFoamBlocker = !openFoamExportAllowed;
		int blockerCount = countTrue(hoverBlocker, cruiseBlocker, openFoamBlocker);
		boolean exportAllowed = blockerCount == 0;
		return new PoweredNearfieldWakeReferenceSummary(
				exportAllowed,
				blockerCount,
				hoverBlocker,
				cruiseBlocker,
				openFoamBlocker,
				openFoam.referenceMaterializationScenarioName(),
				openFoam.lookupReferenceMaterialExportAllowed(),
				openFoam.referenceMaterializationReady(),
				openFoam.referenceMaterializationNextRequiredAction(),
				hover.labValidationAccepted(),
				cruise.labValidationAccepted(),
				hover.allErrorBudgetGroupsReady(),
				cruise.allErrorBudgetGroupsReady(),
				hover.referenceMaterialExportAllowed(),
				cruise.referenceMaterialExportAllowed(),
				openFoam.lookupExecutionContractReady(),
				openFoam.dimensionalSupportReady(),
				openFoam.openFoamSolverQualityContractReady(),
				openFoam.openFoamSolverQualityBlockerCount(),
				openFoam.openFoamSolverQualityBlockerRowCount(),
				openFoam.openFoamSolverQualityNextRequiredAction(),
				openFoam.openFoamCoefficientLookupShapeGuardReady(),
				openFoam.openFoamCoefficientLookupShapeGuardReadyRowCount(),
				openFoam.openFoamCoefficientLookupShapeGuardInheritedScenarioCount(),
				openFoam.openFoamCoefficientLookupShapeGuardBlockedScenarioCount(),
				openFoam.openFoamCoefficientNegativeThrustTailExecutionInputRowCount(),
				openFoam.openFoamCoefficientArchiveCurveEtaFormulaResidual(),
				openFoam.openFoamCoefficientArchiveCurveCtIncrease(),
				openFoam.archiveCurveShapeGuardInheritedReferenceCount(),
				openFoam.negativeThrustTailReferenceCount(),
				openFoam.maxArchiveCurveEtaFormulaResidual(),
				openFoam.maxArchiveCurveCtIncrease(),
				openFoam.archiveCurveShapeGuardCompleteRowCount(),
				openFoam.dimensionalReferenceReviewed(),
				openFoamExportAllowed,
				Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceTable.REFERENCE_SAMPLE_COUNT,
				Aerodynamics4McL2PoweredCruiseSkewWakeReferenceTable.REFERENCE_SAMPLE_COUNT,
				openFoam.expectedReferenceRowCount(),
				openFoam.availableReferenceRowCount(),
				openFoam.blockedReferenceRowCount(),
				Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceTable.REFERENCE_SAMPLE_COUNT
						+ Aerodynamics4McL2PoweredCruiseSkewWakeReferenceTable.REFERENCE_SAMPLE_COUNT
						+ openFoam.expectedReferenceRowCount(),
				hover.readyErrorBudgetGroupCount(),
				cruise.readyErrorBudgetGroupCount(),
				hover.blockedErrorBudgetGroupCount(),
				cruise.blockedErrorBudgetGroupCount(),
				hover.maxPressureErrorRatio(),
				hover.maxWakeVelocityErrorRatio(),
				hover.maxArrivalBandErrorRatio(),
				cruise.maxPressureErrorRatio(),
				cruise.maxWakeVelocityErrorRatio(),
				cruise.maxArrivalWindowErrorRatio(),
				cruise.maxMomentumErrorRatio(),
				false,
				false,
				"combined-hover-surface-cruise-skew-and-openfoam-rotor-reference-package",
				openFoam.referencePayloadKind(),
				nextRequiredAction(hoverBlocker, cruiseBlocker, openFoamBlocker,
						openFoam.openFoamSolverQualityNextRequiredAction(),
						openFoam.referenceMaterializationReady(),
						openFoam.referenceMaterializationNextRequiredAction()),
				exportAllowed ? "READY" : "BLOCKED",
				exportAllowed
						? "nearfield-wake-and-openfoam-reference-package-ready"
						: "nearfield-wake-and-openfoam-reference-package-blocked"
		);
	}

	private static String nextRequiredAction(
			boolean hoverBlocker,
			boolean cruiseBlocker,
			boolean openFoamBlocker,
			String openFoamNextRequiredAction,
			boolean openFoamReferenceMaterializationReady,
			String openFoamReferenceMaterializationNextRequiredAction
	) {
		if (hoverBlocker && cruiseBlocker && openFoamBlocker) {
			return "complete-hover-surface-cruise-skew-and-openfoam-dimensional-reference-handoffs";
		}
		if (hoverBlocker && cruiseBlocker) {
			return "complete-hover-surface-and-cruise-skew-wake-reference-handoffs";
		}
		if (hoverBlocker && openFoamBlocker) {
			return "complete-hover-surface-wake-and-openfoam-dimensional-reference-handoffs";
		}
		if (cruiseBlocker && openFoamBlocker) {
			return "complete-cruise-skew-wake-and-openfoam-dimensional-reference-handoffs";
		}
		if (hoverBlocker) {
			return "complete-hover-surface-wake-reference-handoff";
		}
		if (cruiseBlocker) {
			return "complete-cruise-skew-wake-reference-handoff";
		}
		if (openFoamBlocker) {
			if (!openFoamReferenceMaterializationReady) {
				return openFoamReferenceMaterializationNextRequiredAction;
			}
			if (!"openfoam-solver-quality-blockers-clear".equals(openFoamNextRequiredAction)) {
				return openFoamNextRequiredAction;
			}
			return "complete-openfoam-dimensional-rotor-reference-handoff";
		}
		return "nearfield-wake-and-openfoam-reference-package-ready-for-reviewed-export";
	}

	public static OpenFoamDimensionalReferenceReadiness currentOpenFoamReferenceReadiness() {
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.CtCpJOpenFoamDimensionalReferenceTableAudit audit =
				PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.audit();
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGate
				.OpenFoamDimensionalReferenceMaterializationScenario materialization =
						PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGate
								.scenario("current_lookup_and_openfoam_blocked");
		int expectedRows = audit.referenceRowCount();
		int availableRows = audit.extrema().referenceRowAvailableCount();
		return new OpenFoamDimensionalReferenceReadiness(
				audit.extrema().lookupExecutionContractReadyCount() == expectedRows,
				audit.extrema().dimensionalSupportReadyCount() == expectedRows,
				materialization.scenarioName(),
				materialization.lookupReferenceMaterialExportAllowed(),
				materialization.referenceMaterializationReady(),
				materialization.nextRequiredAction(),
				audit.extrema().openFoamSolverQualityContractReadyCount() == expectedRows,
				audit.extrema().maxOpenFoamSolverQualityBlockerCount(),
				audit.extrema().openFoamSolverQualityBlockerRowCount(),
				openFoamSolverQualityNextRequiredAction(audit.rows()),
				audit.extrema().openFoamCoefficientLookupShapeGuardReadyRowCount() == expectedRows,
				audit.extrema().openFoamCoefficientLookupShapeGuardReadyRowCount(),
				audit.extrema().maxOpenFoamCoefficientLookupShapeGuardInheritedScenarioCount(),
				audit.extrema().maxOpenFoamCoefficientLookupShapeGuardBlockedScenarioCount(),
				audit.extrema().maxOpenFoamCoefficientNegativeThrustTailExecutionInputRowCount(),
				audit.extrema().maxOpenFoamCoefficientArchiveCurveEtaFormulaResidual(),
				audit.extrema().maxOpenFoamCoefficientArchiveCurveCtIncrease(),
				audit.extrema().maxArchiveCurveShapeGuardInheritedReferenceCount(),
				audit.extrema().maxNegativeThrustTailReferenceCount(),
				audit.extrema().maxArchiveCurveEtaFormulaResidual(),
				audit.extrema().maxArchiveCurveCtIncrease(),
				audit.extrema().archiveCurveShapeGuardCompleteRowCount(),
				audit.extrema().dimensionalReferenceReviewedCount() == expectedRows,
				availableRows == expectedRows,
				expectedRows,
				availableRows,
				expectedRows - availableRows,
				audit.extrema().referencePayloadKind()
		);
	}

	public static OpenFoamDimensionalReferenceReadiness reviewedOpenFoamReferenceReadiness() {
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGate
				.OpenFoamDimensionalReferenceMaterializationScenario materialization =
						PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGate
								.scenario("lookup_reference_and_openfoam_ready");
		return new OpenFoamDimensionalReferenceReadiness(
				true,
				true,
				materialization.scenarioName(),
				materialization.lookupReferenceMaterialExportAllowed(),
				materialization.referenceMaterializationReady(),
				materialization.nextRequiredAction(),
				true,
				0,
				0,
				"openfoam-solver-quality-blockers-clear",
				true,
				PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.REFERENCE_ROW_COUNT,
				5,
				1,
				9,
				0.00027500814692071884,
				0.000071,
				PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.REFERENCE_ROW_COUNT,
				0,
				0.0,
				0.0,
				PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.REFERENCE_ROW_COUNT,
				true,
				true,
				PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.REFERENCE_ROW_COUNT,
				PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.REFERENCE_ROW_COUNT,
				0,
				PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.REFERENCE_PAYLOAD_KIND
		);
	}

	private static String openFoamSolverQualityNextRequiredAction(
			List<PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable.OpenFoamDimensionalReferenceRow> rows
	) {
		return rows.stream()
				.filter(row -> row.openFoamSolverQualityBlockerCount() > 0)
				.map(PropellerArchiveCtCpJOpenFoamDimensionalReferenceTable
						.OpenFoamDimensionalReferenceRow::openFoamSolverQualityNextRequiredAction)
				.findFirst()
				.orElse("openfoam-solver-quality-blockers-clear");
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffSummary hover(
			Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffAudit audit,
			String scenarioName
	) {
		return audit.scenarios().stream()
				.filter(scenario -> scenarioName.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffSummary cruise(
			Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffAudit audit,
			String scenarioName
	) {
		return audit.scenarios().stream()
				.filter(scenario -> scenarioName.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static int countTrue(boolean... values) {
		int count = 0;
		for (boolean value : values) {
			if (value) {
				count++;
			}
		}
		return count;
	}

	private static PoweredNearfieldWakeReferenceExtrema extrema(
			List<PoweredNearfieldWakeReferenceScenario> scenarios
	) {
		int ready = 0;
		int maxBlockers = 0;
		int hover = 0;
		int cruise = 0;
		int openFoam = 0;
		int exportAllowed = 0;
		int runtime = 0;
		int autoApply = 0;
		int maxRows = 0;
		int maxHoverBlocked = 0;
		int maxCruiseBlocked = 0;
		int maxOpenFoamBlocked = 0;
		int maxOpenFoamAvailable = 0;
		int lookupReferenceExportAllowed = 0;
		int materializationReady = 0;
		int maxQualityBlockers = 0;
		int maxQualityBlockedRows = 0;
		int maxCoefficientShapeReadyRows = 0;
		int maxCoefficientShapeInherited = 0;
		int maxCoefficientShapeBlocked = 0;
		int maxCoefficientNegativeTail = 0;
		double maxCoefficientEta = 0.0;
		double maxCoefficientCt = 0.0;
		int maxShapeInherited = 0;
		int maxNegativeTail = 0;
		double maxShapeEta = 0.0;
		double maxShapeCt = 0.0;
		int maxShapeCompleteRows = 0;
		double maxMomentum = 0.0;
		for (PoweredNearfieldWakeReferenceScenario scenario : scenarios) {
			PoweredNearfieldWakeReferenceSummary summary = scenario.summary();
			if (summary.nearfieldReferencePackageExportAllowed()) {
				ready++;
				exportAllowed++;
			}
			maxBlockers = Math.max(maxBlockers, summary.blockerCount());
			if (summary.hoverSurfaceWakeReferenceBlocker()) {
				hover++;
			}
			if (summary.cruiseSkewWakeReferenceBlocker()) {
				cruise++;
			}
			if (summary.openFoamDimensionalReferenceBlocker()) {
				openFoam++;
			}
			if (summary.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (summary.gameplayAutoApplyAllowed()) {
				autoApply++;
			}
			maxRows = Math.max(maxRows, summary.totalExpectedReferenceRowCount());
			maxHoverBlocked = Math.max(maxHoverBlocked, summary.hoverBlockedErrorBudgetGroupCount());
			maxCruiseBlocked = Math.max(maxCruiseBlocked, summary.cruiseBlockedErrorBudgetGroupCount());
			maxOpenFoamBlocked = Math.max(maxOpenFoamBlocked, summary.openFoamBlockedReferenceRowCount());
			maxOpenFoamAvailable = Math.max(maxOpenFoamAvailable, summary.openFoamAvailableReferenceRowCount());
			if (summary.openFoamLookupReferenceMaterialExportAllowed()) {
				lookupReferenceExportAllowed++;
			}
			if (summary.openFoamReferenceMaterializationReady()) {
				materializationReady++;
			}
			maxQualityBlockers = Math.max(maxQualityBlockers, summary.openFoamSolverQualityBlockerCount());
			maxQualityBlockedRows = Math.max(maxQualityBlockedRows,
					summary.openFoamSolverQualityBlockerRowCount());
			maxCoefficientShapeReadyRows = Math.max(maxCoefficientShapeReadyRows,
					summary.openFoamCoefficientLookupShapeGuardReadyRowCount());
			maxCoefficientShapeInherited = Math.max(maxCoefficientShapeInherited,
					summary.openFoamCoefficientLookupShapeGuardInheritedScenarioCount());
			maxCoefficientShapeBlocked = Math.max(maxCoefficientShapeBlocked,
					summary.openFoamCoefficientLookupShapeGuardBlockedScenarioCount());
			maxCoefficientNegativeTail = Math.max(maxCoefficientNegativeTail,
					summary.openFoamCoefficientNegativeThrustTailExecutionInputRowCount());
			maxCoefficientEta = Math.max(maxCoefficientEta,
					summary.openFoamCoefficientArchiveCurveEtaFormulaResidual());
			maxCoefficientCt = Math.max(maxCoefficientCt,
					summary.openFoamCoefficientArchiveCurveCtIncrease());
			maxShapeInherited = Math.max(maxShapeInherited,
					summary.openFoamArchiveCurveShapeGuardInheritedReferenceCount());
			maxNegativeTail = Math.max(maxNegativeTail, summary.openFoamNegativeThrustTailReferenceCount());
			maxShapeEta = Math.max(maxShapeEta, summary.openFoamMaxArchiveCurveEtaFormulaResidual());
			maxShapeCt = Math.max(maxShapeCt, summary.openFoamMaxArchiveCurveCtIncrease());
			maxShapeCompleteRows = Math.max(maxShapeCompleteRows,
					summary.openFoamArchiveCurveShapeGuardCompleteRowCount());
			maxMomentum = Math.max(maxMomentum, summary.cruiseMaxMomentumErrorRatio());
		}
		return new PoweredNearfieldWakeReferenceExtrema(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				maxBlockers,
				hover,
				cruise,
				openFoam,
				exportAllowed,
				runtime,
				autoApply,
				maxRows,
				maxHoverBlocked,
				maxCruiseBlocked,
				maxOpenFoamBlocked,
				maxOpenFoamAvailable,
				lookupReferenceExportAllowed,
				materializationReady,
				maxQualityBlockers,
				maxQualityBlockedRows,
				maxCoefficientShapeReadyRows,
				maxCoefficientShapeInherited,
				maxCoefficientShapeBlocked,
				maxCoefficientNegativeTail,
				maxCoefficientEta,
				maxCoefficientCt,
				maxShapeInherited,
				maxNegativeTail,
				maxShapeEta,
				maxShapeCt,
				maxShapeCompleteRows,
				maxMomentum
		);
	}
}
