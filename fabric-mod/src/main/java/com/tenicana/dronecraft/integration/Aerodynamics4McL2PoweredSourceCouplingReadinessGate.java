package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2PoweredSourceCouplingReadinessGate {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Source-Coupling-Readiness-Gate-Packet";
	public static final String CAVEAT =
			"Runtime powered-source coupling remains closed until the API surface audit includes physical source-term contract readiness, the acceptance budget gate is ready, and every actuator-disk representation policy row keeps rotor disks open with runtime coupling explicitly allowed.";
	public static final int SOURCE_REFERENCE_COUNT = 8;
	public static final int SCENARIO_SAMPLE_COUNT = 5;
	public static final int SCENARIO_METRIC_COUNT = 40;
	public static final int SUMMARY_METRIC_ROW_COUNT = 13;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredSourceCouplingReadinessGate() {
	}

	public record PoweredSourceCouplingReadinessSummary(
			boolean hoverAcceptanceHandoffReady,
			boolean cruiseAcceptanceHandoffReady,
			int handoffCount,
			int readyHandoffCount,
			int expectedHandoffCount,
			boolean acceptanceBudgetGateReady,
			boolean allValidationBudgetsReady,
			boolean hoverValidationBudgetCandidate,
			boolean cruiseValidationBudgetCandidate,
			int validationBudgetGroupCount,
			int validationBudgetCandidateCount,
			int expectedValidationBudgetGroupCount,
			boolean poweredSourceApiSurfaceReady,
			boolean poweredSourceExecutorWiringAllowed,
			int poweredSourceApiSurfaceCount,
			int requiredPoweredSourceApiSurfaceCount,
			String missingPoweredSourceApiList,
			boolean poweredSourcePhysicalContractReady,
			int poweredSourcePhysicalContractCount,
			int requiredPoweredSourcePhysicalContractCount,
			String missingPoweredSourcePhysicalContractList,
			int policyCount,
			int runtimeMutationAllowedPolicyCount,
			int solidDiskMaskAllowedPolicyCount,
			int actuatorDiskRepresentationRequiredPolicyCount,
			int porousOrBodyForceSourceRequiredPolicyCount,
			int poweredSourceApiAvailablePolicyCount,
			int poweredHoverGameplayCouplingAllowedPolicyCount,
			int poweredCruiseGameplayCouplingAllowedPolicyCount,
			int validationBeforeRuntimeRequiredPolicyCount,
			int keepRotorDiskOpenPolicyCount,
			int overBlockageRiskIfSolidFilledPolicyCount,
			boolean allHandoffsReady,
			boolean allPoliciesRuntimeAllowed,
			boolean allPoliciesKeepRotorDisksOpen,
			boolean allPoliciesRequirePoweredSourceApi,
			boolean hoverAndCruiseCouplingAllowed,
			boolean runtimePoweredSourceCouplingAllowed,
			String status,
			String message
	) {
	}

	public record PoweredSourceCouplingReadinessScenario(
			String scenarioName,
			PoweredSourceCouplingReadinessSummary summary
	) {
	}

	public record PoweredSourceCouplingReadinessExtrema(
			int scenarioCount,
			int allowedScenarioCount,
			int blockedScenarioCount,
			int maxReadyHandoffCount,
			int maxValidationBudgetCandidateCount,
			int maxRuntimeMutationAllowedPolicyCount,
			int maxPolicyCount,
			int maxSolidDiskMaskAllowedPolicyCount,
			int poweredSourceApiSurfaceReadyScenarioCount,
			int maxPoweredSourceApiSurfaceCount,
			int poweredSourcePhysicalContractReadyScenarioCount,
			int maxPoweredSourcePhysicalContractCount,
			int maxPoweredSourceApiAvailablePolicyCount
	) {
	}

	public record PoweredSourceCouplingReadinessAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int scenarioSampleCount,
			int scenarioMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredSourceCouplingReadinessScenario> scenarios,
			PoweredSourceCouplingReadinessExtrema extrema
	) {
		public PoweredSourceCouplingReadinessAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static PoweredSourceCouplingReadinessAudit audit() {
		return audit(
				Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.audit(),
				Aerodynamics4McL2ActuatorDiskRepresentationPolicy.audit()
		);
	}

	public static PoweredSourceCouplingReadinessAudit audit(ClassLoader loader) {
		if (loader == null) {
			throw new IllegalArgumentException("loader must not be null.");
		}
		return audit(
				Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.audit(loader),
				Aerodynamics4McL2ActuatorDiskRepresentationPolicy.audit(),
				Aerodynamics4McL2PoweredSourceApiSurfaceAudit.currentSummary(loader),
				Aerodynamics4McL2PoweredSourceApiSurfaceAudit.syntheticReadySummary()
		);
	}

	public static PoweredSourceCouplingReadinessAudit audit(
			Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffAudit handoffAudit,
			Aerodynamics4McL2ActuatorDiskRepresentationPolicy.ActuatorDiskRepresentationPolicyAudit policyAudit
	) {
		if (handoffAudit == null || policyAudit == null) {
			throw new IllegalArgumentException("handoff and representation policy audits are required.");
		}
		return audit(
				Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.audit(
						handoffAudit,
						Aerodynamics4McL2PoweredSourceValidationErrorBudget.audit()),
				policyAudit);
	}

	public static PoweredSourceCouplingReadinessAudit audit(
			Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.PoweredSourceAcceptanceBudgetAudit budgetGateAudit,
			Aerodynamics4McL2ActuatorDiskRepresentationPolicy.ActuatorDiskRepresentationPolicyAudit policyAudit
	) {
		return audit(
				budgetGateAudit,
				policyAudit,
				Aerodynamics4McL2PoweredSourceApiSurfaceAudit.currentSummary(),
				Aerodynamics4McL2PoweredSourceApiSurfaceAudit.syntheticReadySummary());
	}

	private static PoweredSourceCouplingReadinessAudit audit(
			Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.PoweredSourceAcceptanceBudgetAudit budgetGateAudit,
			Aerodynamics4McL2ActuatorDiskRepresentationPolicy.ActuatorDiskRepresentationPolicyAudit policyAudit,
			Aerodynamics4McL2PoweredSourceApiSurfaceAudit.PoweredSourceApiSurfaceSummary currentApiSurface,
			Aerodynamics4McL2PoweredSourceApiSurfaceAudit.PoweredSourceApiSurfaceSummary readyApiSurface
	) {
		if (budgetGateAudit == null || policyAudit == null) {
			throw new IllegalArgumentException("budget gate and representation policy audits are required.");
		}
		if (currentApiSurface == null || readyApiSurface == null) {
			throw new IllegalArgumentException("API surface summaries are required.");
		}
		Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.PoweredSourceAcceptanceBudgetSummary currentBudget =
				scenario(budgetGateAudit, "current_handoff_and_budget_blocked");
		Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.PoweredSourceAcceptanceBudgetSummary readyBudget =
				scenario(budgetGateAudit, "handoff_ready_budget_ready");
		List<Aerodynamics4McL2ActuatorDiskRepresentationPolicy.ActuatorDiskRepresentationPolicy> currentPolicies =
				policyAudit.policies();
		List<Aerodynamics4McL2ActuatorDiskRepresentationPolicy.ActuatorDiskRepresentationPolicy> readyPolicies =
				readyPolicies();
		List<PoweredSourceCouplingReadinessScenario> scenarios = List.of(
				new PoweredSourceCouplingReadinessScenario(
						"current_handoff_and_policy_blocked",
						gate(currentApiSurface, currentBudget, currentPolicies)
				),
				new PoweredSourceCouplingReadinessScenario(
						"handoffs_ready_policy_blocked",
						gate(currentApiSurface, readyBudget, currentPolicies)
				),
				new PoweredSourceCouplingReadinessScenario(
						"policy_ready_handoffs_blocked",
						gate(currentApiSurface, currentBudget, readyPolicies)
				),
				new PoweredSourceCouplingReadinessScenario(
						"handoffs_and_policy_ready",
						gate(currentApiSurface, readyBudget, readyPolicies)
				),
				new PoweredSourceCouplingReadinessScenario(
						"handoffs_policy_and_api_surface_ready",
						gate(readyApiSurface, readyBudget, readyPolicies)
				)
		);
		return new PoweredSourceCouplingReadinessAudit(
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

	public static PoweredSourceCouplingReadinessSummary gate(
			List<Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary> handoffs,
			List<Aerodynamics4McL2ActuatorDiskRepresentationPolicy.ActuatorDiskRepresentationPolicy> policies
	) {
		if (handoffs == null || policies == null) {
			throw new IllegalArgumentException("handoffs and policies must not be null.");
		}
		return gate(
				Aerodynamics4McL2PoweredSourceApiSurfaceAudit.currentSummary(),
				Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.gate(
						handoffs,
						Aerodynamics4McL2PoweredSourceValidationErrorBudget.audit().groups(),
						"handoff-policy-coupling-readiness-current-validation-budget"),
				policies);
	}

	public static PoweredSourceCouplingReadinessSummary gate(
			Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.PoweredSourceAcceptanceBudgetSummary acceptanceBudget,
			List<Aerodynamics4McL2ActuatorDiskRepresentationPolicy.ActuatorDiskRepresentationPolicy> policies
	) {
		return gate(Aerodynamics4McL2PoweredSourceApiSurfaceAudit.currentSummary(), acceptanceBudget, policies);
	}

	public static PoweredSourceCouplingReadinessSummary gate(
			Aerodynamics4McL2PoweredSourceApiSurfaceAudit.PoweredSourceApiSurfaceSummary apiSurface,
			Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.PoweredSourceAcceptanceBudgetSummary acceptanceBudget,
			List<Aerodynamics4McL2ActuatorDiskRepresentationPolicy.ActuatorDiskRepresentationPolicy> policies
	) {
		if (acceptanceBudget == null || policies == null) {
			throw new IllegalArgumentException("acceptance budget and policies must not be null.");
		}
		if (apiSurface == null) {
			throw new IllegalArgumentException("apiSurface must not be null.");
		}
		int runtimeAllowed = 0;
		int solidAllowed = 0;
		int actuatorRequired = 0;
		int sourceRequired = 0;
		int sourceApiAvailable = 0;
		int hoverAllowed = 0;
		int cruiseAllowed = 0;
		int validationBeforeRuntime = 0;
		int keepOpen = 0;
		int overBlockageRisk = 0;
		for (Aerodynamics4McL2ActuatorDiskRepresentationPolicy.ActuatorDiskRepresentationPolicy policy : policies) {
			if (policy == null) {
				throw new IllegalArgumentException("policies must not contain null entries.");
			}
			if (policy.runtimeMutationAllowed()) {
				runtimeAllowed++;
			}
			if (policy.solidDiskMaskAllowed()) {
				solidAllowed++;
			}
			if (policy.actuatorDiskRepresentationRequired()) {
				actuatorRequired++;
			}
			if (policy.porousOrBodyForceSourceRequired()) {
				sourceRequired++;
			}
			if (policy.poweredSourceApiAvailable()) {
				sourceApiAvailable++;
			}
			if (policy.poweredHoverGameplayCouplingAllowed()) {
				hoverAllowed++;
			}
			if (policy.poweredCruiseGameplayCouplingAllowed()) {
				cruiseAllowed++;
			}
			if (policy.validationBeforeRuntimeRequired()) {
				validationBeforeRuntime++;
			}
			if ("keep_rotor_disk_open".equals(policy.binarySolidMaskRotorDiskPolicy())) {
				keepOpen++;
			}
			if (policy.overBlockageRiskIfSolidFilled()) {
				overBlockageRisk++;
			}
		}
		boolean allHandoffsReady = acceptanceBudget.allAcceptanceHandoffsReady();
		boolean allPoliciesRuntimeAllowed = !policies.isEmpty() && runtimeAllowed == policies.size();
		boolean allPoliciesKeepOpen = !policies.isEmpty() && keepOpen == policies.size() && solidAllowed == 0;
		boolean allPoliciesRequireSource = !policies.isEmpty()
				&& actuatorRequired == policies.size()
				&& sourceRequired == policies.size()
				&& validationBeforeRuntime == policies.size();
		boolean poweredSourceApiSurfaceReady =
				apiSurface.poweredSourceApiReady() && apiSurface.poweredSourceExecutorWiringAllowed();
		boolean hoverCruiseAllowed = !policies.isEmpty()
				&& hoverAllowed == policies.size()
				&& cruiseAllowed == policies.size()
				&& sourceApiAvailable == policies.size();
		boolean allowed = poweredSourceApiSurfaceReady
				&& acceptanceBudget.acceptanceBudgetGateReady()
				&& allPoliciesRuntimeAllowed
				&& allPoliciesKeepOpen
				&& allPoliciesRequireSource
				&& hoverCruiseAllowed;
		return new PoweredSourceCouplingReadinessSummary(
				acceptanceBudget.hoverAcceptanceHandoffReady(),
				acceptanceBudget.cruiseAcceptanceHandoffReady(),
				acceptanceBudget.handoffCount(),
				acceptanceBudget.readyHandoffCount(),
				acceptanceBudget.expectedHandoffCount(),
				acceptanceBudget.acceptanceBudgetGateReady(),
				acceptanceBudget.allValidationBudgetsReady(),
				acceptanceBudget.hoverValidationBudgetCandidate(),
				acceptanceBudget.cruiseValidationBudgetCandidate(),
				acceptanceBudget.validationBudgetGroupCount(),
				acceptanceBudget.validationBudgetCandidateCount(),
				acceptanceBudget.expectedValidationBudgetGroupCount(),
				poweredSourceApiSurfaceReady,
				apiSurface.poweredSourceExecutorWiringAllowed(),
				apiSurface.poweredSourceApiSurfaceCount(),
				apiSurface.requiredPoweredSourceApiSurfaceCount(),
				apiSurface.missingPoweredSourceApiList(),
				apiSurface.poweredSourcePhysicalContractReady(),
				apiSurface.poweredSourcePhysicalContractCount(),
				apiSurface.requiredPoweredSourcePhysicalContractCount(),
				apiSurface.missingPoweredSourcePhysicalContractList(),
				policies.size(),
				runtimeAllowed,
				solidAllowed,
				actuatorRequired,
				sourceRequired,
				sourceApiAvailable,
				hoverAllowed,
				cruiseAllowed,
				validationBeforeRuntime,
				keepOpen,
				overBlockageRisk,
				allHandoffsReady,
				allPoliciesRuntimeAllowed,
				allPoliciesKeepOpen,
				allPoliciesRequireSource,
				hoverCruiseAllowed,
				allowed,
				allowed ? "READY_FOR_POWERED_SOURCE_RUNTIME_COUPLING" : "BLOCKED",
				allowed ? "runtime-coupling-ready" : "runtime-coupling-evidence-incomplete"
		);
	}

	private static Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.PoweredSourceAcceptanceBudgetSummary scenario(
			Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.PoweredSourceAcceptanceBudgetAudit audit,
			String name
	) {
		return audit.scenarios().stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary readyHandoff(
			String spinState
	) {
		String validationPacketId = "hover".equals(spinState)
				? Aerodynamics4McL2PoweredHoverValidation.SOURCE_ID
				: Aerodynamics4McL2PoweredCruiseValidation.SOURCE_ID;
		String acceptanceGatePacketId = "hover".equals(spinState)
				? Aerodynamics4McL2PoweredHoverAcceptanceGate.SOURCE_ID
				: Aerodynamics4McL2PoweredCruiseAcceptanceGate.SOURCE_ID;
		return new Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary(
				spinState,
				validationPacketId,
				acceptanceGatePacketId,
				4,
				4,
				4,
				4,
				4,
				4,
				0,
				0,
				"none",
				0,
				0,
				0,
				0.0,
				0.0,
				true,
				true,
				true,
				"READY",
				"acceptance-handoff-ready"
		);
	}

	private static List<Aerodynamics4McL2ActuatorDiskRepresentationPolicy.ActuatorDiskRepresentationPolicy> readyPolicies() {
		return Aerodynamics4McL2RotorDiskAperture.audit().presets().stream()
				.map(aperture -> Aerodynamics4McL2ActuatorDiskRepresentationPolicy.policy(aperture, true, true))
				.toList();
	}

	private static PoweredSourceCouplingReadinessExtrema extrema(
			List<PoweredSourceCouplingReadinessScenario> scenarios
	) {
		int allowed = 0;
		int maxReadyHandoffs = 0;
		int maxValidationBudgetCandidates = 0;
		int maxRuntimePolicies = 0;
		int maxPolicies = 0;
		int maxSolidAllowed = 0;
		int surfaceReady = 0;
		int maxApiSurface = 0;
		int physicalReady = 0;
		int maxPhysical = 0;
		int maxSourceApi = 0;
		for (PoweredSourceCouplingReadinessScenario scenario : scenarios) {
			PoweredSourceCouplingReadinessSummary summary = scenario.summary();
			if (summary.runtimePoweredSourceCouplingAllowed()) {
				allowed++;
			}
			maxReadyHandoffs = Math.max(maxReadyHandoffs, summary.readyHandoffCount());
			maxValidationBudgetCandidates = Math.max(
					maxValidationBudgetCandidates,
					summary.validationBudgetCandidateCount());
			maxRuntimePolicies = Math.max(maxRuntimePolicies, summary.runtimeMutationAllowedPolicyCount());
			maxPolicies = Math.max(maxPolicies, summary.policyCount());
			maxSolidAllowed = Math.max(maxSolidAllowed, summary.solidDiskMaskAllowedPolicyCount());
			if (summary.poweredSourceApiSurfaceReady()) {
				surfaceReady++;
			}
			maxApiSurface = Math.max(maxApiSurface, summary.poweredSourceApiSurfaceCount());
			if (summary.poweredSourcePhysicalContractReady()) {
				physicalReady++;
			}
			maxPhysical = Math.max(maxPhysical, summary.poweredSourcePhysicalContractCount());
			maxSourceApi = Math.max(maxSourceApi, summary.poweredSourceApiAvailablePolicyCount());
		}
		return new PoweredSourceCouplingReadinessExtrema(
				scenarios.size(),
				allowed,
				scenarios.size() - allowed,
				maxReadyHandoffs,
				maxValidationBudgetCandidates,
				maxRuntimePolicies,
				maxPolicies,
				maxSolidAllowed,
				surfaceReady,
				maxApiSurface,
				physicalReady,
				maxPhysical,
				maxSourceApi
		);
	}
}
