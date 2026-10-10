package com.tenicana.dronecraft.integrationvalidation;

import java.util.LinkedHashMap;
import java.util.Map;

import com.tenicana.dronecraft.integration.Aerodynamics4McAtmosphereBridge.AtmosphereSample;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** An independent observation of the actual public GAMEPLAY_SERVER_ONLY API. */
record NativeA4mcObservation(boolean flow, boolean trusted, boolean temperature, boolean humidity,
		boolean local, long l1Epoch, long worldDeltaEpoch, long l2Epoch, long currentRuntimeEpoch, Map<String, Double> values) {
	static Map<String, Object> runtimeReport() throws ReflectiveOperationException {
		Class<?> type = Class.forName("com.aerodynamics4mc.runtime.AeroServerRuntime");
		Object runtime = type.getMethod("getInstance").invoke(null);
		Map<String, Object> result = new LinkedHashMap<>();
		for (String method : new String[]{"isStreamingEnabled", "getTickCounter", "getSimulationTicks",
				"getLastBackgroundRefreshAppliedTick", "getLastBackgroundRefreshWorldCount",
				"getLastSolverError", "getLastCoordinatorError", "getLastCoordinatorState",
				"getLastCoordinatorNoPublishReason"}) {
			result.put(method, type.getMethod(method).invoke(runtime));
		}
		result.put("worldEnvironmentCount", ((Map<?, ?>) type.getMethod("getWorldEnvironmentSnapshots").invoke(runtime)).size());
		return result;
	}

	static NativeA4mcObservation sample(ServerLevel level, Vec3 position) throws ReflectiveOperationException {
		Class<?> policy = Class.forName("com.aerodynamics4mc.api.SamplePolicy");
		Object serverOnly = policy.getField("GAMEPLAY_SERVER_ONLY").get(null);
		Class<?> api = Class.forName("com.aerodynamics4mc.api.minecraft.AeroMinecraftWindApi");
		Object sample = api.getMethod("sampleGameplay", ServerLevel.class, Vec3.class, policy)
				.invoke(null, level, position, serverOnly);
		if (sample == null) throw new IllegalStateException("A4MC public API returned null");
		Map<String, Double> values = new LinkedHashMap<>();
		for (String field : new String[]{"meanX", "meanY", "meanZ", "gustX", "gustY", "gustZ",
				"pressure", "temperatureKelvin", "humidity", "turbulenceIntensity", "updraftMetersPerSecond",
				"windShearMagnitudePerBlock", "shelterFactor", "ablStability", "ablMixingStrength", "confidence"}) {
			values.put(field, ((Number) sample.getClass().getMethod(field).invoke(sample)).doubleValue());
		}
		return new NativeA4mcObservation(flag(sample, "hasFlow"), flag(sample, "isTrustedForGameplay"),
				flag(sample, "hasTemperature"), flag(sample, "hasHumidity"), flag(sample, "hasLocalL2Modifier"),
				epoch(sample, "l1Epoch"), epoch(sample, "worldDeltaEpoch"), epoch(sample, "l2Epoch"), runtimeEpoch(), values);
	}

	private static long runtimeEpoch() throws ReflectiveOperationException {
		Class<?> runtimeType = Class.forName("com.aerodynamics4mc.runtime.AeroServerRuntime");
		Object runtime = runtimeType.getMethod("getInstance").invoke(null);
		return ((Number) runtimeType.getMethod("getTickCounter").invoke(runtime)).longValue();
	}

	boolean finite() {
		return values.entrySet().stream()
				.filter(entry -> temperature || !"temperatureKelvin".equals(entry.getKey()))
				.filter(entry -> humidity || !"humidity".equals(entry.getKey()))
				.allMatch(entry -> Double.isFinite(entry.getValue()));
	}

	boolean matches(AtmosphereSample bridge) {
		if (!flow || !finite() || !bridge.hasFlow() || bridge.trustedForGameplay() != trusted
				|| bridge.hasTemperature() != temperature || bridge.hasHumidity() != humidity
				|| bridge.localVoxelFlow() != local || !bridge.hasMeanVelocity()) return false;
		long newest = Math.max(l1Epoch, Math.max(worldDeltaEpoch, l2Epoch));
		long age = newest < 0L ? -1L : Math.max(0L, currentRuntimeEpoch - newest);
		return bridge.freshnessAgeTicks() == age
				&& close(bridge.confidence(), clamp("confidence", 0.0, 1.0))
				&& close(bridge.meanVelocityX(), clamp("meanX", -30.0, 30.0))
				&& close(bridge.meanVelocityY(), clamp("meanY", -30.0, 30.0))
				&& close(bridge.meanVelocityZ(), clamp("meanZ", -30.0, 30.0))
				&& close(bridge.gustVelocityXMetersPerSecond(), clamp("gustX", -30.0, 30.0))
				&& close(bridge.gustVerticalMetersPerSecond(), clamp("gustY", -30.0, 30.0))
				&& close(bridge.gustVelocityZMetersPerSecond(), clamp("gustZ", -30.0, 30.0))
				&& close(bridge.gustSpeedMetersPerSecond(), Math.min(60.0,
						Math.sqrt(Math.pow(clamp("gustX", -30.0, 30.0), 2.0)
								+ Math.pow(clamp("gustY", -30.0, 30.0), 2.0)
								+ Math.pow(clamp("gustZ", -30.0, 30.0), 2.0))))
				&& close(bridge.pressureAnomalyPascals(), clamp("pressure", -5000.0, 5000.0))
				&& close(bridge.turbulenceIntensity(), clamp("turbulenceIntensity", 0.0, 1.5))
				&& close(bridge.windShearMagnitudePerBlock(), clamp("windShearMagnitudePerBlock", 0.0, 5.0))
				&& close(bridge.shelterFactor(), clamp("shelterFactor", 0.0, 1.0))
				&& close(bridge.updraftMetersPerSecond(), clamp("updraftMetersPerSecond", -12.0, 12.0))
				&& close(bridge.ablStability(), clamp("ablStability", -1.0, 1.0))
				&& close(bridge.ablMixingStrength(), clamp("ablMixingStrength", 0.0, 1.0))
				&& (!temperature || close(bridge.temperatureCelsius(),
						Math.max(-40.0, Math.min(65.0, values.get("temperatureKelvin") - 273.15))))
				&& (!humidity || close(bridge.humidity(), clamp("humidity", 0.0, 1.0)));
	}

	Map<String, Object> report() {
		Map<String, Object> report = new LinkedHashMap<>();
		report.put("hasFlow", flow);
		report.put("trustedForGameplay", trusted);
		report.put("hasTemperature", temperature);
		report.put("hasHumidity", humidity);
		report.put("localL2Modifier", local);
		report.put("l1Epoch", l1Epoch);
		report.put("worldDeltaEpoch", worldDeltaEpoch);
		report.put("l2Epoch", l2Epoch);
		report.put("currentRuntimeEpoch", currentRuntimeEpoch);
		long newest = Math.max(l1Epoch, Math.max(worldDeltaEpoch, l2Epoch));
		report.put("ageInNativeEpochDomain", newest < 0L ? -1L : Math.max(0L, currentRuntimeEpoch - newest));
		Map<String, Object> fields = new LinkedHashMap<>();
		values.forEach((key, value) -> fields.put(key, Double.isFinite(value) ? value : value.toString()));
		report.put("fields", fields);
		return report;
	}

	private double clamp(String field, double min, double max) {
		return Math.max(min, Math.min(max, values.get(field)));
	}
	private static boolean close(double left, double right) {
		return Double.isFinite(left) && Double.isFinite(right) && Math.abs(left - right) <= 1.0e-5;
	}
	private static boolean flag(Object sample, String method) throws ReflectiveOperationException {
		return (boolean) sample.getClass().getMethod(method).invoke(sample);
	}
	private static long epoch(Object sample, String method) throws ReflectiveOperationException {
		return ((Number) sample.getClass().getMethod(method).invoke(sample)).longValue();
	}
}
