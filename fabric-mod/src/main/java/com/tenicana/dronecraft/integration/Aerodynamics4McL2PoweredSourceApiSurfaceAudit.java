package com.tenicana.dronecraft.integration;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

public final class Aerodynamics4McL2PoweredSourceApiSurfaceAudit {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Source-API-Surface-Audit-Packet";
	public static final String CAVEAT =
			"Powered-source API surface audit records whether A4MC exposes public source-term extension points and physical contract semantics; it never fabricates rotor source output, runtime coupling, or gameplay tuning data.";
	public static final String UPSTREAM_REFERENCE_COMMIT = "62a52a584e9c65246e50226b29a1f0449e43995e";
	public static final int SOURCE_REFERENCE_COUNT = 7;
	public static final int SCENARIO_SAMPLE_COUNT = 3;
	public static final int SCENARIO_METRIC_COUNT = 37;
	public static final int SUMMARY_METRIC_ROW_COUNT = 13;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int REQUIRED_BASE_CAPABILITY_COUNT = 6;
	public static final int REQUIRED_POWERED_SOURCE_API_SURFACE_COUNT = 5;
	public static final int REQUIRED_POWERED_SOURCE_PHYSICAL_CONTRACT_COUNT = 5;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private static final String REQUEST_CLASS = "com.aerodynamics4mc.api.AeroL2Request";
	private static final String RESULT_CLASS = "com.aerodynamics4mc.api.AeroL2Result";

	private Aerodynamics4McL2PoweredSourceApiSurfaceAudit() {
	}

	public record PoweredSourceApiSurfaceSummary(
			boolean l2WindApiAvailable,
			boolean l2RequestBuilderAvailable,
			boolean l2RequestMaskAvailable,
			boolean l2InitialFlowAvailable,
			boolean l2FlowAtlasAvailable,
			boolean l2ForceMomentAvailable,
			int baseCapabilityCount,
			int requiredBaseCapabilityCount,
			boolean bodyForceSourceApiAvailable,
			boolean porousSourceApiAvailable,
			boolean rotorSourceTermApiAvailable,
			boolean sourceTermEnvelopeApiAvailable,
			boolean sourceTermRuntimeResultAvailable,
			int poweredSourceApiSurfaceCount,
			int requiredPoweredSourceApiSurfaceCount,
			boolean sourceTermUnitsApiAvailable,
			boolean sourceTermBodyFrameApiAvailable,
			boolean sourceTermTemporalApiAvailable,
			boolean runtimeForceMomentDeltaApiAvailable,
			boolean runtimeConservationResidualApiAvailable,
			int poweredSourcePhysicalContractCount,
			int requiredPoweredSourcePhysicalContractCount,
			boolean poweredSourcePhysicalContractReady,
			boolean poweredSourceApiReady,
			boolean poweredSourceExecutorWiringAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String missingBaseCapabilityList,
			String missingPoweredSourceApiList,
			String missingPoweredSourcePhysicalContractList,
			String discoveredRequestExtensionMethods,
			String discoveredResultExtensionMethods,
			String upstreamReferenceCommit,
			String status,
			String message,
			String sourceRuntimeInfo,
			String caveat
	) {
	}

	public record PoweredSourceApiSurfaceScenario(
			String scenarioName,
			PoweredSourceApiSurfaceSummary summary
	) {
	}

	public record PoweredSourceApiSurfaceExtrema(
			int scenarioCount,
			int readyScenarioCount,
			int blockedScenarioCount,
			int maxBaseCapabilityCount,
			int maxPoweredSourceApiSurfaceCount,
			int maxPoweredSourcePhysicalContractCount,
			int maxMissingBaseCapabilityCount,
			int maxMissingPoweredSourceApiCount,
			int maxMissingPoweredSourcePhysicalContractCount,
			int physicalContractReadyScenarioCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			int executorWiringAllowedCount
	) {
	}

	public record PoweredSourceApiSurfaceAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int scenarioSampleCount,
			int scenarioMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredSourceApiSurfaceScenario> scenarios,
			PoweredSourceApiSurfaceExtrema extrema
	) {
		public PoweredSourceApiSurfaceAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static PoweredSourceApiSurfaceAudit audit() {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		if (loader == null) {
			loader = Aerodynamics4McL2PoweredSourceApiSurfaceAudit.class.getClassLoader();
		}
		return audit(loader);
	}

	public static PoweredSourceApiSurfaceAudit audit(ClassLoader loader) {
		if (loader == null) {
			throw new IllegalArgumentException("loader must not be null.");
		}
		List<PoweredSourceApiSurfaceScenario> scenarios = List.of(
				new PoweredSourceApiSurfaceScenario(
						"current_a4mc_l2_surface",
						currentSummary(loader)),
				new PoweredSourceApiSurfaceScenario(
						"missing_a4mc_l2_surface",
						summary(
								Aerodynamics4McL2Bridge.inspect(new ClassLoader(null) {
								}),
								List.of(),
								List.of(),
								"missing-a4mc-l2-api-surface")),
				new PoweredSourceApiSurfaceScenario(
						"synthetic_powered_source_api_ready",
						syntheticReadySummary())
		);
		return new PoweredSourceApiSurfaceAudit(
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

	public static PoweredSourceApiSurfaceSummary currentSummary() {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		if (loader == null) {
			loader = Aerodynamics4McL2PoweredSourceApiSurfaceAudit.class.getClassLoader();
		}
		return currentSummary(loader);
	}

	public static PoweredSourceApiSurfaceSummary currentSummary(ClassLoader loader) {
		if (loader == null) {
			throw new IllegalArgumentException("loader must not be null.");
		}
		return summary(
				Aerodynamics4McL2Bridge.inspect(loader),
				requestExtensionMethods(loader),
				resultExtensionMethods(loader),
				"current-a4mc-l2-api-surface");
	}

	public static PoweredSourceApiSurfaceSummary summary(
			Aerodynamics4McL2Bridge.L2Capabilities capabilities,
			List<String> requestExtensionMethods,
			List<String> resultExtensionMethods,
			String sourceRuntimeInfo
	) {
		if (capabilities == null) {
			throw new IllegalArgumentException("capabilities must not be null.");
		}
		if (requestExtensionMethods == null || resultExtensionMethods == null) {
			throw new IllegalArgumentException("extension method lists must not be null.");
		}
		if (sourceRuntimeInfo == null || sourceRuntimeInfo.isBlank()) {
			throw new IllegalArgumentException("sourceRuntimeInfo must not be blank.");
		}
		List<String> requestMethods = List.copyOf(requestExtensionMethods);
		List<String> resultMethods = List.copyOf(resultExtensionMethods);
		boolean baseWind = capabilities.runL2Available();
		boolean baseBuilder = capabilities.requestBuilderAvailable();
		boolean baseMask = capabilities.requestMaskAvailable();
		boolean baseInitialFlow = capabilities.requestMaskAvailable();
		boolean baseAtlas = capabilities.flowAtlasAvailable();
		boolean baseForceMoment = capabilities.forceMomentAvailable();
		boolean bodyForce = containsAny(requestMethods, "bodyforce", "body_force", "body-force");
		boolean porous = containsAny(requestMethods, "porous");
		boolean rotor = containsAny(requestMethods, "rotor", "actuator");
		boolean sourceEnvelope = containsAny(requestMethods, "source", "sourceterm", "source_term", "source-term");
		boolean runtimeResult = containsAny(resultMethods, "source", "sourceterm", "source_term", "source-term", "delta");
		boolean sourceUnits = containsAny(requestMethods, "newton", "newtons", "pascal", "pascals", "pressurejump",
				"pressure_jump", "siunit", "si_unit", "si-unit");
		boolean sourceBodyFrame = containsAny(requestMethods, "bodyframe", "body_frame", "body-frame",
				"sourcecenter", "source_center", "offset");
		boolean sourceTemporal = containsAny(requestMethods, "timestep", "time_step", "time-step", "substep", "dt");
		boolean forceMomentDelta = containsAllInOne(resultMethods, "force", "moment", "delta");
		boolean conservationResidual = containsAny(resultMethods, "residual", "conservation", "momentum");
		int baseCount = countTrue(baseWind, baseBuilder, baseMask, baseInitialFlow, baseAtlas, baseForceMoment);
		int poweredCount = countTrue(bodyForce, porous, rotor, sourceEnvelope, runtimeResult);
		int physicalCount = countTrue(sourceUnits, sourceBodyFrame, sourceTemporal, forceMomentDelta, conservationResidual);
		boolean baseReady = baseCount == REQUIRED_BASE_CAPABILITY_COUNT;
		boolean poweredReady = poweredCount == REQUIRED_POWERED_SOURCE_API_SURFACE_COUNT;
		boolean physicalReady = physicalCount == REQUIRED_POWERED_SOURCE_PHYSICAL_CONTRACT_COUNT;
		boolean ready = baseReady && poweredReady && physicalReady;
		return new PoweredSourceApiSurfaceSummary(
				baseWind,
				baseBuilder,
				baseMask,
				baseInitialFlow,
				baseAtlas,
				baseForceMoment,
				baseCount,
				REQUIRED_BASE_CAPABILITY_COUNT,
				bodyForce,
				porous,
				rotor,
				sourceEnvelope,
				runtimeResult,
				poweredCount,
				REQUIRED_POWERED_SOURCE_API_SURFACE_COUNT,
				sourceUnits,
				sourceBodyFrame,
				sourceTemporal,
				forceMomentDelta,
				conservationResidual,
				physicalCount,
				REQUIRED_POWERED_SOURCE_PHYSICAL_CONTRACT_COUNT,
				physicalReady,
				ready,
				ready,
				false,
				false,
				missingBase(baseWind, baseBuilder, baseMask, baseInitialFlow, baseAtlas, baseForceMoment),
				missingPowered(bodyForce, porous, rotor, sourceEnvelope, runtimeResult),
				missingPhysicalContract(sourceUnits, sourceBodyFrame, sourceTemporal, forceMomentDelta,
						conservationResidual),
				joinOrNone(requestMethods),
				joinOrNone(resultMethods),
				UPSTREAM_REFERENCE_COMMIT,
				ready ? "READY" : "BLOCKED",
				messageFor(baseReady, poweredReady, physicalReady),
				sourceRuntimeInfo,
				CAVEAT
		);
	}

	public static PoweredSourceApiSurfaceSummary syntheticReadySummary() {
		return summary(
				new Aerodynamics4McL2Bridge.L2Capabilities(
						true,
						true,
						true,
						true,
						true,
						true,
						true,
						true,
						true,
						true,
						true,
						true,
						true,
						"synthetic powered-source API surface ready"),
				List.of(
						"bodyForceSourceNewtons",
						"porousSourcePressureJumpPascals",
						"rotorSourceTermsBodyFrame",
						"poweredSourceEnvelopeTimeStepSeconds"),
				List.of(
						"poweredSourceForceMomentDeltaNewtons",
						"poweredSourceMomentumConservationResidual"),
				"synthetic-powered-source-api-ready");
	}

	private static List<String> requestExtensionMethods(ClassLoader loader) {
		List<String> methods = new ArrayList<>();
		try {
			Class<?> requestClass = Class.forName(REQUEST_CLASS, false, loader);
			for (Method method : requestClass.getMethods()) {
				addExtensionMethod(methods, "AeroL2Request." + method.getName());
			}
			Method builder = requestClass.getMethod("builder", int.class, int.class, int.class);
			for (Method method : builder.getReturnType().getMethods()) {
				addExtensionMethod(methods, "AeroL2Request.Builder." + method.getName());
			}
		} catch (ReflectiveOperationException | LinkageError ignored) {
			return List.of();
		}
		return methods.stream().sorted().distinct().toList();
	}

	private static List<String> resultExtensionMethods(ClassLoader loader) {
		List<String> methods = new ArrayList<>();
		try {
			Class<?> resultClass = Class.forName(RESULT_CLASS, false, loader);
			for (Method method : resultClass.getMethods()) {
				addExtensionMethod(methods, "AeroL2Result." + method.getName());
			}
		} catch (ReflectiveOperationException | LinkageError ignored) {
			return List.of();
		}
		return methods.stream().sorted().distinct().toList();
	}

	private static void addExtensionMethod(List<String> methods, String name) {
		String normalized = name.toLowerCase(Locale.ROOT);
		if (normalized.contains("source")
				|| normalized.contains("bodyforce")
				|| normalized.contains("body_force")
				|| normalized.contains("body-force")
				|| normalized.contains("porous")
				|| normalized.contains("rotor")
				|| normalized.contains("actuator")) {
			methods.add(name);
		}
	}

	private static boolean containsAny(List<String> methods, String... needles) {
		for (String method : methods) {
			String normalized = method.toLowerCase(Locale.ROOT);
			for (String needle : needles) {
				if (normalized.contains(needle)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean containsAllInOne(List<String> methods, String... needles) {
		for (String method : methods) {
			String normalized = method.toLowerCase(Locale.ROOT);
			boolean allPresent = true;
			for (String needle : needles) {
				if (!normalized.contains(needle)) {
					allPresent = false;
					break;
				}
			}
			if (allPresent) {
				return true;
			}
		}
		return false;
	}

	private static String missingBase(
			boolean wind,
			boolean builder,
			boolean mask,
			boolean initialFlow,
			boolean atlas,
			boolean forceMoment
	) {
		List<String> missing = new ArrayList<>();
		if (!wind) {
			missing.add("run_l2");
		}
		if (!builder) {
			missing.add("request_builder");
		}
		if (!mask) {
			missing.add("solid_mask");
		}
		if (!initialFlow) {
			missing.add("initial_flow_state");
		}
		if (!atlas) {
			missing.add("flow_atlas_result");
		}
		if (!forceMoment) {
			missing.add("force_moment_result");
		}
		return joinOrNone(missing);
	}

	private static String missingPowered(
			boolean bodyForce,
			boolean porous,
			boolean rotor,
			boolean sourceEnvelope,
			boolean runtimeResult
	) {
		List<String> missing = new ArrayList<>();
		if (!bodyForce) {
			missing.add("body_force_source_api");
		}
		if (!porous) {
			missing.add("porous_source_api");
		}
		if (!rotor) {
			missing.add("rotor_or_actuator_source_term_api");
		}
		if (!sourceEnvelope) {
			missing.add("source_term_envelope_api");
		}
		if (!runtimeResult) {
			missing.add("source_term_runtime_result_api");
		}
		return joinOrNone(missing);
	}

	private static String missingPhysicalContract(
			boolean sourceUnits,
			boolean sourceBodyFrame,
			boolean sourceTemporal,
			boolean forceMomentDelta,
			boolean conservationResidual
	) {
		List<String> missing = new ArrayList<>();
		if (!sourceUnits) {
			missing.add("source_term_si_units");
		}
		if (!sourceBodyFrame) {
			missing.add("source_term_body_frame");
		}
		if (!sourceTemporal) {
			missing.add("source_term_time_step_or_substep");
		}
		if (!forceMomentDelta) {
			missing.add("runtime_force_moment_delta_result");
		}
		if (!conservationResidual) {
			missing.add("runtime_momentum_conservation_residual");
		}
		return joinOrNone(missing);
	}

	private static String joinOrNone(List<String> values) {
		if (values.isEmpty()) {
			return "none";
		}
		StringJoiner joiner = new StringJoiner(";");
		for (String value : values) {
			joiner.add(value);
		}
		return joiner.toString();
	}

	private static String messageFor(boolean baseReady, boolean poweredReady, boolean physicalReady) {
		if (!baseReady) {
			return "l2-base-api-surface-missing";
		}
		if (!poweredReady) {
			return "powered-source-api-surface-missing";
		}
		if (!physicalReady) {
			return "powered-source-physical-contract-missing";
		}
		return "powered-source-api-surface-ready";
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

	private static PoweredSourceApiSurfaceExtrema extrema(List<PoweredSourceApiSurfaceScenario> scenarios) {
		int ready = 0;
		int executor = 0;
		int runtime = 0;
		int gameplay = 0;
		int maxBase = 0;
		int maxPowered = 0;
		int maxPhysical = 0;
		int maxMissingBase = 0;
		int maxMissingPowered = 0;
		int maxMissingPhysical = 0;
		int physicalReady = 0;
		for (PoweredSourceApiSurfaceScenario scenario : scenarios) {
			PoweredSourceApiSurfaceSummary summary = scenario.summary();
			if (summary.poweredSourceApiReady()) {
				ready++;
			}
			if (summary.poweredSourcePhysicalContractReady()) {
				physicalReady++;
			}
			if (summary.poweredSourceExecutorWiringAllowed()) {
				executor++;
			}
			if (summary.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (summary.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
			maxBase = Math.max(maxBase, summary.baseCapabilityCount());
			maxPowered = Math.max(maxPowered, summary.poweredSourceApiSurfaceCount());
			maxPhysical = Math.max(maxPhysical, summary.poweredSourcePhysicalContractCount());
			maxMissingBase = Math.max(maxMissingBase, missingCount(summary.missingBaseCapabilityList()));
			maxMissingPowered = Math.max(maxMissingPowered, missingCount(summary.missingPoweredSourceApiList()));
			maxMissingPhysical = Math.max(maxMissingPhysical,
					missingCount(summary.missingPoweredSourcePhysicalContractList()));
		}
		return new PoweredSourceApiSurfaceExtrema(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				maxBase,
				maxPowered,
				maxPhysical,
				maxMissingBase,
				maxMissingPowered,
				maxMissingPhysical,
				physicalReady,
				runtime,
				gameplay,
				executor
		);
	}

	private static int missingCount(String values) {
		if (values == null || values.isBlank() || "none".equals(values)) {
			return 0;
		}
		return values.split(";").length;
	}
}
