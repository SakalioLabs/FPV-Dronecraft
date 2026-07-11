package com.tenicana.dronecraft.integration;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;

import com.tenicana.dronecraft.sim.Vec3;

/**
 * Optional, single-point atmosphere and flow sampling from Aerodynamics4MC.
 *
 * <p>The bridge deliberately keeps the gameplay contract compact: one body-center sample becomes
 * scalar flow, pressure, shelter, and thermal primitives. It does not expose L2 solver types,
 * source metadata, per-rotor arrays, or any additional world sampling.</p>
 */
public final class Aerodynamics4McAtmosphereBridge {
	private static final String MOD_ID = "aerodynamics4mc";
	private static final String API_CLASS = "com.aerodynamics4mc.api.minecraft.AeroMinecraftWindApi";
	private static final String POLICY_CLASS = "com.aerodynamics4mc.api.SamplePolicy";
	private static volatile Sampler sampler;

	private Aerodynamics4McAtmosphereBridge() {
	}

	public static AtmosphereSample sampleGameplay(ServerLevel level, Vec3 positionWorldMeters) {
		if (level == null || positionWorldMeters == null || !positionWorldMeters.isFinite()) {
			return AtmosphereSample.unavailable();
		}
		return sampleGameplay(
				level,
				positionWorldMeters.x(),
				positionWorldMeters.y(),
				positionWorldMeters.z()
		);
	}

	public static AtmosphereSample sampleGameplay(
			ServerLevel level,
			double positionX,
			double positionY,
			double positionZ
	) {
		if (level == null
				|| !Double.isFinite(positionX)
				|| !Double.isFinite(positionY)
				|| !Double.isFinite(positionZ)) {
			return AtmosphereSample.unavailable();
		}
		return sampler().sample(level, positionX, positionY, positionZ, level.getGameTime());
	}

	private static Sampler sampler() {
		Sampler current = sampler;
		if (current != null) {
			return current;
		}
		synchronized (Aerodynamics4McAtmosphereBridge.class) {
			if (sampler == null) {
				sampler = initializeSampler();
			}
			return sampler;
		}
	}

	private static Sampler initializeSampler() {
		if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
			return UnavailableSampler.INSTANCE;
		}
		try {
			Class<?> apiClass = Class.forName(API_CLASS);
			Class<?> policyClass = Class.forName(POLICY_CLASS);
			return bindIfAvailable(true, apiClass, policyClass);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
			return UnavailableSampler.INSTANCE;
		}
	}

	static Sampler bindIfAvailable(boolean modLoaded, Class<?> apiClass, Class<?> policyClass) {
		if (!modLoaded) {
			return UnavailableSampler.INSTANCE;
		}
		try {
			Object gameplayServerOnlyPolicy = enumConstant(policyClass, "GAMEPLAY_SERVER_ONLY");
			Method sampleGameplay = apiClass.getMethod(
					"sampleGameplay",
					ServerLevel.class,
					net.minecraft.world.phys.Vec3.class,
					policyClass
			);
			Class<?> sampleClass = sampleGameplay.getReturnType();
			Method meanVelocityVector = methodOrNull(sampleClass, "meanVelocityVector");
			Method gustVelocityVector = methodOrNull(sampleClass, "gustVelocityVector");
			return new ReflectionSampler(
					sampleGameplay,
					gameplayServerOnlyPolicy,
					sampleClass.getMethod("hasFlow"),
					sampleClass.getMethod("isTrustedForGameplay"),
					sampleClass.getMethod("confidence"),
					meanVelocityVector,
					vectorMethodOrNull(meanVelocityVector, "x"),
					vectorMethodOrNull(meanVelocityVector, "y"),
					vectorMethodOrNull(meanVelocityVector, "z"),
					gustVelocityVector,
					vectorMethodOrNull(gustVelocityVector, "x"),
					vectorMethodOrNull(gustVelocityVector, "y"),
					vectorMethodOrNull(gustVelocityVector, "z"),
					methodOrNull(sampleClass, "turbulenceIntensity"),
					methodOrNull(sampleClass, "windShearMagnitudePerBlock"),
					methodOrNull(sampleClass, "shelterFactor"),
					methodOrNull(sampleClass, "updraftMetersPerSecond"),
					methodOrNull(sampleClass, "hasLocalL2Modifier"),
					firstMethodOrNull(sampleClass, "pressureAnomalyPascals", "pressure"),
					methodOrNull(sampleClass, "hasTemperature"),
					methodOrNull(sampleClass, "temperatureKelvin"),
					methodOrNull(sampleClass, "hasHumidity"),
					methodOrNull(sampleClass, "humidity"),
					methodOrNull(sampleClass, "l1Epoch"),
					methodOrNull(sampleClass, "worldDeltaEpoch"),
					methodOrNull(sampleClass, "l2Epoch")
			);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
			return UnavailableSampler.INSTANCE;
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static Object enumConstant(Class<?> enumClass, String name) {
		return Enum.valueOf((Class) enumClass.asSubclass(Enum.class), name);
	}

	private static Method methodOrNull(Class<?> owner, String name) {
		try {
			return owner.getMethod(name);
		} catch (NoSuchMethodException ignored) {
			return null;
		}
	}

	private static Method firstMethodOrNull(Class<?> owner, String... names) {
		for (String name : names) {
			Method method = methodOrNull(owner, name);
			if (method != null) {
				return method;
			}
		}
		return null;
	}

	private static Method vectorMethodOrNull(Method vectorAccessor, String name) {
		return vectorAccessor == null ? null : methodOrNull(vectorAccessor.getReturnType(), name);
	}

	interface Sampler {
		AtmosphereSample sample(
				ServerLevel level,
				double positionX,
				double positionY,
				double positionZ,
				long currentTick
		);
	}

	private enum UnavailableSampler implements Sampler {
		INSTANCE;

		@Override
		public AtmosphereSample sample(
				ServerLevel level,
				double positionX,
				double positionY,
				double positionZ,
				long currentTick
		) {
			return AtmosphereSample.unavailable();
		}
	}

	private static final class ReflectionSampler implements Sampler {
		private final Method sampleGameplay;
		private final Object gameplayServerOnlyPolicy;
		private final Method hasFlow;
		private final Method isTrustedForGameplay;
		private final Method confidence;
		private final Method meanVelocityVector;
		private final Method meanVectorX;
		private final Method meanVectorY;
		private final Method meanVectorZ;
		private final Method gustVelocityVector;
		private final Method gustVectorX;
		private final Method gustVectorY;
		private final Method gustVectorZ;
		private final Method turbulenceIntensity;
		private final Method windShearMagnitudePerBlock;
		private final Method shelterFactor;
		private final Method updraftMetersPerSecond;
		private final Method hasLocalL2Modifier;
		private final Method pressureAnomalyPascals;
		private final Method hasTemperature;
		private final Method temperatureKelvin;
		private final Method hasHumidity;
		private final Method humidity;
		private final Method l1Epoch;
		private final Method worldDeltaEpoch;
		private final Method l2Epoch;
		private volatile boolean disabled;

		private ReflectionSampler(
				Method sampleGameplay,
				Object gameplayServerOnlyPolicy,
				Method hasFlow,
				Method isTrustedForGameplay,
				Method confidence,
				Method meanVelocityVector,
				Method meanVectorX,
				Method meanVectorY,
				Method meanVectorZ,
				Method gustVelocityVector,
				Method gustVectorX,
				Method gustVectorY,
				Method gustVectorZ,
				Method turbulenceIntensity,
				Method windShearMagnitudePerBlock,
				Method shelterFactor,
				Method updraftMetersPerSecond,
				Method hasLocalL2Modifier,
				Method pressureAnomalyPascals,
				Method hasTemperature,
				Method temperatureKelvin,
				Method hasHumidity,
				Method humidity,
				Method l1Epoch,
				Method worldDeltaEpoch,
				Method l2Epoch
		) {
			this.sampleGameplay = sampleGameplay;
			this.gameplayServerOnlyPolicy = gameplayServerOnlyPolicy;
			this.hasFlow = hasFlow;
			this.isTrustedForGameplay = isTrustedForGameplay;
			this.confidence = confidence;
			this.meanVelocityVector = meanVelocityVector;
			this.meanVectorX = meanVectorX;
			this.meanVectorY = meanVectorY;
			this.meanVectorZ = meanVectorZ;
			this.gustVelocityVector = gustVelocityVector;
			this.gustVectorX = gustVectorX;
			this.gustVectorY = gustVectorY;
			this.gustVectorZ = gustVectorZ;
			this.turbulenceIntensity = turbulenceIntensity;
			this.windShearMagnitudePerBlock = windShearMagnitudePerBlock;
			this.shelterFactor = shelterFactor;
			this.updraftMetersPerSecond = updraftMetersPerSecond;
			this.hasLocalL2Modifier = hasLocalL2Modifier;
			this.pressureAnomalyPascals = pressureAnomalyPascals;
			this.hasTemperature = hasTemperature;
			this.temperatureKelvin = temperatureKelvin;
			this.hasHumidity = hasHumidity;
			this.humidity = humidity;
			this.l1Epoch = l1Epoch;
			this.worldDeltaEpoch = worldDeltaEpoch;
			this.l2Epoch = l2Epoch;
		}

		@Override
		public AtmosphereSample sample(
				ServerLevel level,
				double positionX,
				double positionY,
				double positionZ,
				long currentTick
		) {
			if (disabled) {
				return AtmosphereSample.unavailable();
			}
			try {
				net.minecraft.world.phys.Vec3 minecraftPosition = new net.minecraft.world.phys.Vec3(
						positionX,
						positionY,
						positionZ
				);
				Object sample = sampleGameplay.invoke(null, level, minecraftPosition, gameplayServerOnlyPolicy);
				if (sample == null || !bool(hasFlow, sample)) {
					return AtmosphereSample.unavailable();
				}

				boolean sampleHasTemperature = hasTemperature != null
						&& temperatureKelvin != null
						&& bool(hasTemperature, sample);
				double temperatureCelsius = sampleHasTemperature
						? number(temperatureKelvin.invoke(sample)) - 273.15
						: 0.0;
				boolean sampleHasHumidity = hasHumidity != null
						&& humidity != null
						&& bool(hasHumidity, sample);
				double sampleHumidity = sampleHasHumidity ? number(humidity.invoke(sample)) : 0.0;
				Object meanVector = vector(
						meanVelocityVector,
						meanVectorX,
						meanVectorY,
						meanVectorZ,
						sample
				);
				boolean hasMeanVelocity = meanVector != null;
				double meanVelocityX = hasMeanVelocity ? vectorNumber(meanVectorX, meanVector) : 0.0;
				double meanVelocityY = hasMeanVelocity ? vectorNumber(meanVectorY, meanVector) : 0.0;
				double meanVelocityZ = hasMeanVelocity ? vectorNumber(meanVectorZ, meanVector) : 0.0;
				hasMeanVelocity = hasMeanVelocity
						&& Double.isFinite(meanVelocityX)
						&& Double.isFinite(meanVelocityY)
						&& Double.isFinite(meanVelocityZ);

				Object gustVector = vector(
						gustVelocityVector,
						gustVectorX,
						gustVectorY,
						gustVectorZ,
						sample
				);
				double gustX = gustVector == null ? 0.0 : vectorNumber(gustVectorX, gustVector);
				double gustY = gustVector == null ? 0.0 : vectorNumber(gustVectorY, gustVector);
				double gustZ = gustVector == null ? 0.0 : vectorNumber(gustVectorZ, gustVector);
				boolean hasGustVelocity = gustVector != null
						&& Double.isFinite(gustX)
						&& Double.isFinite(gustY)
						&& Double.isFinite(gustZ);
				if (hasGustVelocity) {
					gustX = clamp(gustX, -30.0, 30.0);
					gustY = clamp(gustY, -30.0, 30.0);
					gustZ = clamp(gustZ, -30.0, 30.0);
				}
				long freshnessAgeTicks = freshnessAgeTicks(
						currentTick,
						optionalLong(l1Epoch, sample),
						optionalLong(worldDeltaEpoch, sample),
						optionalLong(l2Epoch, sample)
				);
				return new AtmosphereSample(
						true,
						bool(isTrustedForGameplay, sample),
						number(confidence.invoke(sample)),
						freshnessAgeTicks,
						hasMeanVelocity,
						meanVelocityX,
						meanVelocityY,
						meanVelocityZ,
						optionalNumber(turbulenceIntensity, sample),
						optionalNumber(windShearMagnitudePerBlock, sample),
						optionalNumber(shelterFactor, sample),
						optionalNumber(updraftMetersPerSecond, sample),
						hasGustVelocity ? Math.sqrt(gustX * gustX + gustY * gustY + gustZ * gustZ) : 0.0,
						hasGustVelocity ? gustY : 0.0,
						optionalBool(hasLocalL2Modifier, sample),
						optionalNumber(pressureAnomalyPascals, sample),
						sampleHasTemperature,
						temperatureCelsius,
						sampleHasHumidity,
						sampleHumidity
				);
			} catch (InvocationTargetException error) {
				disableAfterFailure(this);
				return AtmosphereSample.unavailable();
			} catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
				disableAfterFailure(this);
				return AtmosphereSample.unavailable();
			}
		}
	}

	private static boolean bool(Method method, Object target) throws ReflectiveOperationException {
		return Boolean.TRUE.equals(method.invoke(target));
	}

	private static double number(Object value) {
		return value instanceof Number number ? number.doubleValue() : Double.NaN;
	}

	private static double optionalNumber(Method method, Object target) throws ReflectiveOperationException {
		return method == null ? 0.0 : number(method.invoke(target));
	}

	private static boolean optionalBool(Method method, Object target) throws ReflectiveOperationException {
		return method != null && bool(method, target);
	}

	private static Object vector(
			Method accessor,
			Method x,
			Method y,
			Method z,
			Object target
	) throws ReflectiveOperationException {
		return accessor == null || x == null || y == null || z == null ? null : accessor.invoke(target);
	}

	private static double vectorNumber(Method component, Object vector) throws ReflectiveOperationException {
		return number(component.invoke(vector));
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static long optionalLong(Method method, Object target) throws ReflectiveOperationException {
		Object value = method == null ? null : method.invoke(target);
		return value instanceof Number number ? number.longValue() : -1L;
	}

	static long freshnessAgeTicks(long currentTick, long l1Epoch, long worldDeltaEpoch, long l2Epoch) {
		long latestEpoch = Math.max(l1Epoch, Math.max(worldDeltaEpoch, l2Epoch));
		if (currentTick < 0L || latestEpoch < 0L) {
			return -1L;
		}
		return latestEpoch >= currentTick ? 0L : currentTick - latestEpoch;
	}

	private static void disableAfterFailure(ReflectionSampler failedSampler) {
		failedSampler.disabled = true;
		if (sampler == failedSampler) {
			sampler = UnavailableSampler.INSTANCE;
		}
	}

	public record AtmosphereSample(
			boolean hasFlow,
			boolean trustedForGameplay,
			double confidence,
			long freshnessAgeTicks,
			boolean hasMeanVelocity,
			double meanVelocityX,
			double meanVelocityY,
			double meanVelocityZ,
			double turbulenceIntensity,
			double windShearMagnitudePerBlock,
			double shelterFactor,
			double updraftMetersPerSecond,
			double gustSpeedMetersPerSecond,
			double gustVerticalMetersPerSecond,
			boolean localVoxelFlow,
			double pressureAnomalyPascals,
			boolean hasTemperature,
			double temperatureCelsius,
			boolean hasHumidity,
			double humidity
	) {
		private static final AtmosphereSample UNAVAILABLE = new AtmosphereSample(
				false,
				false,
				0.0,
				-1L,
				false,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				false,
				0.0,
				false,
				0.0,
				false,
				0.0
		);

		public AtmosphereSample {
			confidence = finiteClamped(confidence, 0.0, 1.0, 0.0);
			freshnessAgeTicks = freshnessAgeTicks < 0L ? -1L : freshnessAgeTicks;
			if (!hasFlow) {
				trustedForGameplay = false;
				hasMeanVelocity = false;
				localVoxelFlow = false;
			}
			if (!hasMeanVelocity
					|| !Double.isFinite(meanVelocityX)
					|| !Double.isFinite(meanVelocityY)
					|| !Double.isFinite(meanVelocityZ)) {
				hasMeanVelocity = false;
				meanVelocityX = 0.0;
				meanVelocityY = 0.0;
				meanVelocityZ = 0.0;
			} else {
				meanVelocityX = clamp(meanVelocityX, -30.0, 30.0);
				meanVelocityY = clamp(meanVelocityY, -30.0, 30.0);
				meanVelocityZ = clamp(meanVelocityZ, -30.0, 30.0);
			}
			turbulenceIntensity = finiteClamped(turbulenceIntensity, 0.0, 1.5, 0.0);
			windShearMagnitudePerBlock = finiteClamped(windShearMagnitudePerBlock, 0.0, 5.0, 0.0);
			shelterFactor = finiteClamped(shelterFactor, 0.0, 1.0, 0.0);
			updraftMetersPerSecond = finiteClamped(updraftMetersPerSecond, -12.0, 12.0, 0.0);
			gustSpeedMetersPerSecond = finiteClamped(gustSpeedMetersPerSecond, 0.0, 60.0, 0.0);
			gustVerticalMetersPerSecond = finiteClamped(gustVerticalMetersPerSecond, -30.0, 30.0, 0.0);
			pressureAnomalyPascals = finiteClamped(pressureAnomalyPascals, -5000.0, 5000.0, 0.0);
			if (!hasTemperature || !Double.isFinite(temperatureCelsius)) {
				hasTemperature = false;
				temperatureCelsius = 0.0;
			} else {
				temperatureCelsius = clamp(temperatureCelsius, -40.0, 65.0);
			}
			if (!hasHumidity || !Double.isFinite(humidity)) {
				hasHumidity = false;
				humidity = 0.0;
			} else {
				humidity = clamp(humidity, 0.0, 1.0);
			}
		}

		public static AtmosphereSample unavailable() {
			return UNAVAILABLE;
		}

		private static double finiteClamped(double value, double min, double max, double fallback) {
			return Double.isFinite(value) ? clamp(value, min, max) : fallback;
		}

		private static double clamp(double value, double min, double max) {
			return Math.max(min, Math.min(max, value));
		}
	}
}
