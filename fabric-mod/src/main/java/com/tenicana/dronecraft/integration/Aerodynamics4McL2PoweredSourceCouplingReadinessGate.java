package com.tenicana.dronecraft.integration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Aerodynamics4McL2PoweredSourceCouplingReadinessGate {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Source-Coupling-Readiness-Gate-Packet";
	public static final String CAVEAT =
			"Runtime powered-source coupling remains closed until hover and cruise acceptance handoffs are ready and every actuator-disk representation policy row keeps rotor disks open with runtime coupling explicitly allowed.";
	public static final int SOURCE_REFERENCE_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT = 4;
	public static final int SCENARIO_METRIC_COUNT = 24;
	public static final int SUMMARY_METRIC_ROW_COUNT = 8;
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
			int maxRuntimeMutationAllowedPolicyCount,
			int maxPolicyCount,
			int maxSolidDiskMaskAllowedPolicyCount,
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
				Aerodynamics4McL2PoweredSourceAcceptanceHandoff.audit(),
				Aerodynamics4McL2ActuatorDiskRepresentationPolicy.audit()
		);
	}

	public static PoweredSourceCouplingReadinessAudit audit(ClassLoader loader) {
		if (loader == null) {
			throw new IllegalArgumentException("loader must not be null.");
		}
		return audit(
				Aerodynamics4McL2PoweredSourceAcceptanceHandoff.audit(loader),
				Aerodynamics4McL2ActuatorDiskRepresentationPolicy.audit()
		);
	}

	public static PoweredSourceCouplingReadinessAudit audit(
			Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffAudit handoffAudit,
			Aerodynamics4McL2ActuatorDiskRepresentationPolicy.ActuatorDiskRepresentationPolicyAudit policyAudit
	) {
		if (handoffAudit == null || policyAudit == null) {
			throw new IllegalArgumentException("handoff and representation policy audits are required.");
		}
		List<Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary> currentHandoffs =
				handoffAudit.handoffs();
		List<Aerodynamics4McL2ActuatorDiskRepresentationPolicy.ActuatorDiskRepresentationPolicy> currentPolicies =
				policyAudit.policies();
		List<Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary> readyHandoffs =
				List.of(readyHandoff("hover"), readyHandoff("cruise"));
		List<Aerodynamics4McL2ActuatorDiskRepresentationPolicy.ActuatorDiskRepresentationPolicy> readyPolicies =
				readyPolicies();
		List<PoweredSourceCouplingReadinessScenario> scenarios = List.of(
				new PoweredSourceCouplingReadinessScenario(
						"current_handoff_and_policy_blocked",
						gate(currentHandoffs, currentPolicies)
				),
				new PoweredSourceCouplingReadinessScenario(
						"handoffs_ready_policy_blocked",
						gate(readyHandoffs, currentPolicies)
				),
				new PoweredSourceCouplingReadinessScenario(
						"policy_ready_handoffs_blocked",
						gate(currentHandoffs, readyPolicies)
				),
				new PoweredSourceCouplingReadinessScenario(
						"handoffs_and_policy_ready",
						gate(readyHandoffs, readyPolicies)
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
		Set<String> observedHandoffs = new HashSet<>();
		boolean hoverReady = false;
		boolean cruiseReady = false;
		int readyHandoffs = 0;
		for (Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary handoff : handoffs) {
			if (handoff == null || handoff.spinState() == null || handoff.spinState().isBlank()) {
				throw new IllegalArgumentException("handoffs must include stable spin-state names.");
			}
			if (!observedHandoffs.add(handoff.spinState())) {
				throw new IllegalArgumentException("duplicate handoff spin state: " + handoff.spinState());
			}
			if (handoff.acceptanceHandoffReady()) {
				readyHandoffs++;
			}
			if ("hover".equals(handoff.spinState())) {
				hoverReady = handoff.acceptanceHandoffReady();
			} else if ("cruise".equals(handoff.spinState())) {
				cruiseReady = handoff.acceptanceHandoffReady();
			}
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
		boolean allHandoffsReady = hoverReady
				&& cruiseReady
				&& observedHandoffs.contains("hover")
				&& observedHandoffs.contains("cruise")
				&& observedHandoffs.size() == 2;
		boolean allPoliciesRuntimeAllowed = !policies.isEmpty() && runtimeAllowed == policies.size();
		boolean allPoliciesKeepOpen = !policies.isEmpty() && keepOpen == policies.size() && solidAllowed == 0;
		boolean allPoliciesRequireSource = !policies.isEmpty()
				&& actuatorRequired == policies.size()
				&& sourceRequired == policies.size()
				&& validationBeforeRuntime == policies.size();
		boolean hoverCruiseAllowed = !policies.isEmpty()
				&& hoverAllowed == policies.size()
				&& cruiseAllowed == policies.size()
				&& sourceApiAvailable == policies.size();
		boolean allowed = allHandoffsReady
				&& allPoliciesRuntimeAllowed
				&& allPoliciesKeepOpen
				&& allPoliciesRequireSource
				&& hoverCruiseAllowed;
		return new PoweredSourceCouplingReadinessSummary(
				hoverReady,
				cruiseReady,
				handoffs.size(),
				readyHandoffs,
				2,
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
		int maxRuntimePolicies = 0;
		int maxPolicies = 0;
		int maxSolidAllowed = 0;
		int maxSourceApi = 0;
		for (PoweredSourceCouplingReadinessScenario scenario : scenarios) {
			PoweredSourceCouplingReadinessSummary summary = scenario.summary();
			if (summary.runtimePoweredSourceCouplingAllowed()) {
				allowed++;
			}
			maxReadyHandoffs = Math.max(maxReadyHandoffs, summary.readyHandoffCount());
			maxRuntimePolicies = Math.max(maxRuntimePolicies, summary.runtimeMutationAllowedPolicyCount());
			maxPolicies = Math.max(maxPolicies, summary.policyCount());
			maxSolidAllowed = Math.max(maxSolidAllowed, summary.solidDiskMaskAllowedPolicyCount());
			maxSourceApi = Math.max(maxSourceApi, summary.poweredSourceApiAvailablePolicyCount());
		}
		return new PoweredSourceCouplingReadinessExtrema(
				scenarios.size(),
				allowed,
				scenarios.size() - allowed,
				maxReadyHandoffs,
				maxRuntimePolicies,
				maxPolicies,
				maxSolidAllowed,
				maxSourceApi
		);
	}
}
