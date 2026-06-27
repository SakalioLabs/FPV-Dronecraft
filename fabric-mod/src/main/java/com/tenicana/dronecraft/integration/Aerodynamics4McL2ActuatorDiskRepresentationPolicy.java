package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2ActuatorDiskRepresentationPolicy {
	public static final String SOURCE_ID = "A4MC-L2-Actuator-Disk-Representation-Policy-Packet";
	public static final String CAVEAT =
			"Representation policy keeps rotor disks open in the binary solid mask and requires a future porous or body-force source API before actuator-disk loads can affect gameplay.";
	public static final int SOURCE_REFERENCE_COUNT = 5;
	public static final int PRESET_SAMPLE_COUNT = 4;
	public static final int POLICY_METRIC_COUNT = 25;
	public static final int SUMMARY_METRIC_ROW_COUNT = 10;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ PRESET_SAMPLE_COUNT * POLICY_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2ActuatorDiskRepresentationPolicy() {
	}

	public record ActuatorDiskRepresentationPolicy(
			String presetName,
			int rotorCount,
			double openFraction,
			double minRotorOpenFraction,
			double maxRotorOpenFraction,
			double openDiskAreaSquareMeters,
			double blockedDiskAreaSquareMeters,
			double maxAxialInletSpeedMetersPerSecond,
			double maxInPlaneInletSpeedMetersPerSecond,
			String binarySolidMaskRotorDiskPolicy,
			boolean solidDiskMaskAllowed,
			boolean actuatorDiskRepresentationRequired,
			boolean porousOrBodyForceSourceRequired,
			boolean poweredSourceApiAvailable,
			boolean actuatorDiskSourceMapReady,
			boolean poweredHoverExperimentPlanReady,
			boolean poweredCruiseExperimentPlanReady,
			boolean poweredHoverAcceptanceGateRequired,
			boolean poweredCruiseAcceptanceGateRequired,
			boolean poweredHoverGameplayCouplingAllowed,
			boolean poweredCruiseGameplayCouplingAllowed,
			boolean overBlockageRiskIfSolidFilled,
			boolean validationBeforeRuntimeRequired,
			String recommendedApiSurface,
			boolean runtimeMutationAllowed,
			String status
	) {
	}

	public record ActuatorDiskRepresentationExtrema(
			int policyCount,
			int solidDiskMaskAllowedCount,
			int porousOrBodyForceSourceRequiredCount,
			int poweredSourceApiAvailableCount,
			int poweredHoverGameplayCouplingAllowedCount,
			int poweredCruiseGameplayCouplingAllowedCount,
			int overBlockageRiskIfSolidFilledCount,
			int runtimeMutationAllowedCount,
			double minOpenFraction,
			double maxBlockedDiskAreaSquareMeters
	) {
	}

	public record ActuatorDiskRepresentationPolicyAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int presetSampleCount,
			int policyMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<ActuatorDiskRepresentationPolicy> policies,
			ActuatorDiskRepresentationExtrema extrema
	) {
		public ActuatorDiskRepresentationPolicyAudit {
			policies = List.copyOf(policies);
		}
	}

	public static ActuatorDiskRepresentationPolicyAudit audit() {
		Aerodynamics4McL2RotorDiskAperture.RotorDiskApertureAudit apertureAudit =
				Aerodynamics4McL2RotorDiskAperture.audit();
		boolean hoverAllowed = currentHoverGameplayCouplingAllowed();
		boolean cruiseAllowed = currentCruiseGameplayCouplingAllowed();
		List<ActuatorDiskRepresentationPolicy> policies = apertureAudit.presets().stream()
				.map(summary -> policy(summary, hoverAllowed, cruiseAllowed))
				.toList();
		return new ActuatorDiskRepresentationPolicyAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				PRESET_SAMPLE_COUNT,
				POLICY_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				policies,
				extrema(policies)
		);
	}

	public static ActuatorDiskRepresentationPolicy policy(
			Aerodynamics4McL2RotorDiskAperture.PresetApertureSummary aperture,
			boolean poweredHoverGameplayCouplingAllowed,
			boolean poweredCruiseGameplayCouplingAllowed
	) {
		if (aperture == null) {
			throw new IllegalArgumentException("aperture must not be null.");
		}
		boolean poweredSourceApiAvailable = poweredHoverGameplayCouplingAllowed && poweredCruiseGameplayCouplingAllowed;
		boolean runtimeAllowed = poweredSourceApiAvailable
				&& poweredHoverGameplayCouplingAllowed
				&& poweredCruiseGameplayCouplingAllowed;
		return new ActuatorDiskRepresentationPolicy(
				aperture.presetName(),
				aperture.rotorCount(),
				aperture.openFraction(),
				aperture.minRotorOpenFraction(),
				aperture.maxRotorOpenFraction(),
				aperture.openDiskAreaSquareMeters(),
				aperture.blockedDiskAreaSquareMeters(),
				aperture.maxAxialInletSpeedMetersPerSecond(),
				aperture.maxInPlaneInletSpeedMetersPerSecond(),
				"keep_rotor_disk_open",
				false,
				true,
				true,
				poweredSourceApiAvailable,
				true,
				true,
				true,
				true,
				true,
				poweredHoverGameplayCouplingAllowed,
				poweredCruiseGameplayCouplingAllowed,
				true,
				true,
				"porous_or_body_force_source_api",
				runtimeAllowed,
				runtimeAllowed ? "READY_FOR_REVIEW" : "BLOCKED"
		);
	}

	private static boolean currentHoverGameplayCouplingAllowed() {
		return Aerodynamics4McL2PoweredHoverAcceptanceGate.audit().scenarios().stream()
				.filter(scenario -> "current_api_unavailable_no_results".equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary()
				.gameplayCouplingAllowed();
	}

	private static boolean currentCruiseGameplayCouplingAllowed() {
		return Aerodynamics4McL2PoweredCruiseAcceptanceGate.audit().scenarios().stream()
				.filter(scenario -> "current_api_unavailable_no_results".equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary()
				.gameplayCouplingAllowed();
	}

	private static ActuatorDiskRepresentationExtrema extrema(List<ActuatorDiskRepresentationPolicy> policies) {
		int solidAllowed = 0;
		int sourceRequired = 0;
		int sourceApiAvailable = 0;
		int hoverAllowed = 0;
		int cruiseAllowed = 0;
		int overBlockageRisk = 0;
		int runtimeAllowed = 0;
		double minOpenFraction = Double.POSITIVE_INFINITY;
		double maxBlockedArea = 0.0;
		for (ActuatorDiskRepresentationPolicy policy : policies) {
			if (policy.solidDiskMaskAllowed()) {
				solidAllowed++;
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
			if (policy.overBlockageRiskIfSolidFilled()) {
				overBlockageRisk++;
			}
			if (policy.runtimeMutationAllowed()) {
				runtimeAllowed++;
			}
			minOpenFraction = Math.min(minOpenFraction, policy.openFraction());
			maxBlockedArea = Math.max(maxBlockedArea, policy.blockedDiskAreaSquareMeters());
		}
		return new ActuatorDiskRepresentationExtrema(
				policies.size(),
				solidAllowed,
				sourceRequired,
				sourceApiAvailable,
				hoverAllowed,
				cruiseAllowed,
				overBlockageRisk,
				runtimeAllowed,
				policies.isEmpty() ? 0.0 : minOpenFraction,
				maxBlockedArea
		);
	}
}
