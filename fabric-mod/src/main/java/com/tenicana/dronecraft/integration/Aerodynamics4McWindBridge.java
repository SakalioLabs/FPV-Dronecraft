package com.tenicana.dronecraft.integration;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;

import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.sim.MathUtil;
import com.tenicana.dronecraft.sim.Vec3;

public final class Aerodynamics4McWindBridge {
	private static final String MOD_ID = "aerodynamics4mc";
	private static final String API_CLASS = "com.aerodynamics4mc.api.minecraft.AeroMinecraftWindApi";
	private static final String POLICY_CLASS = "com.aerodynamics4mc.api.SamplePolicy";
	private static volatile Sampler sampler;

	private Aerodynamics4McWindBridge() {
	}

	public static WindSample sampleGameplay(ServerLevel level, Vec3 positionWorldMeters) {
		if (level == null || positionWorldMeters == null || !positionWorldMeters.isFinite()) {
			return WindSample.unavailable();
		}
		return sampler().sample(level, positionWorldMeters);
	}

	private static Sampler sampler() {
		Sampler current = sampler;
		if (current != null) {
			return current;
		}
		synchronized (Aerodynamics4McWindBridge.class) {
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
			Object gameplayServerOnlyPolicy = enumConstant(policyClass, "GAMEPLAY_SERVER_ONLY");
			Method sampleGameplay = apiClass.getMethod(
					"sampleGameplay",
					ServerLevel.class,
					net.minecraft.world.phys.Vec3.class,
					policyClass
			);
			Class<?> sampleClass = sampleGameplay.getReturnType();
			Class<?> vectorClass = Class.forName("com.aerodynamics4mc.api.A4mcVec3");
			FpvDronecraftMod.LOGGER.info("Aerodynamics4MC wind bridge enabled");
			return new ReflectionSampler(
					sampleGameplay,
					gameplayServerOnlyPolicy,
					sampleClass.getMethod("hasFlow"),
					sampleClass.getMethod("isTrustedForGameplay"),
					sampleClass.getMethod("meanVelocityVector"),
					sampleClass.getMethod("effectiveVelocityVector"),
					methodOrNull(sampleClass, "gustVelocityVector"),
					sampleClass.getMethod("turbulenceIntensity"),
					sampleClass.getMethod("windShearMagnitudePerBlock"),
					sampleClass.getMethod("shelterFactor"),
					sampleClass.getMethod("updraftMetersPerSecond"),
					sampleClass.getMethod("hasTemperature"),
					sampleClass.getMethod("temperatureKelvin"),
					sampleClass.getMethod("hasHumidity"),
					sampleClass.getMethod("humidity"),
					sampleClass.getMethod("confidence"),
					methodOrNull(sampleClass, "pressure"),
					methodOrNull(sampleClass, "hasLocalL2Modifier"),
					methodOrNull(sampleClass, "sourceLevel"),
					methodOrNull(sampleClass, "authority"),
					methodOrNull(sampleClass, "l1Epoch"),
					methodOrNull(sampleClass, "worldDeltaEpoch"),
					methodOrNull(sampleClass, "l2Epoch"),
					methodOrNull(sampleClass, "ablStability"),
					methodOrNull(sampleClass, "ablMixingStrength"),
					vectorClass.getMethod("x"),
					vectorClass.getMethod("y"),
					vectorClass.getMethod("z")
			);
		} catch (ReflectiveOperationException | LinkageError error) {
			FpvDronecraftMod.LOGGER.warn("Aerodynamics4MC is loaded but its wind API could not be bound", error);
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

	private interface Sampler {
		WindSample sample(ServerLevel level, Vec3 positionWorldMeters);
	}

	private enum UnavailableSampler implements Sampler {
		INSTANCE;

		@Override
		public WindSample sample(ServerLevel level, Vec3 positionWorldMeters) {
			return WindSample.unavailable();
		}
	}

	private static final class ReflectionSampler implements Sampler {
		private final Method sampleGameplay;
		private final Object gameplayServerOnlyPolicy;
		private final Method hasFlow;
		private final Method isTrustedForGameplay;
		private final Method meanVelocityVector;
		private final Method effectiveVelocityVector;
		private final Method gustVelocityVector;
		private final Method turbulenceIntensity;
		private final Method windShearMagnitudePerBlock;
		private final Method shelterFactor;
		private final Method updraftMetersPerSecond;
		private final Method hasTemperature;
		private final Method temperatureKelvin;
		private final Method hasHumidity;
		private final Method humidity;
		private final Method confidence;
		private final Method pressure;
		private final Method hasLocalL2Modifier;
		private final Method sourceLevel;
		private final Method authority;
		private final Method l1Epoch;
		private final Method worldDeltaEpoch;
		private final Method l2Epoch;
		private final Method ablStability;
		private final Method ablMixingStrength;
		private final Method vecX;
		private final Method vecY;
		private final Method vecZ;

		private ReflectionSampler(
				Method sampleGameplay,
				Object gameplayServerOnlyPolicy,
				Method hasFlow,
				Method isTrustedForGameplay,
				Method meanVelocityVector,
				Method effectiveVelocityVector,
				Method gustVelocityVector,
				Method turbulenceIntensity,
				Method windShearMagnitudePerBlock,
				Method shelterFactor,
				Method updraftMetersPerSecond,
				Method hasTemperature,
				Method temperatureKelvin,
				Method hasHumidity,
				Method humidity,
				Method confidence,
				Method pressure,
				Method hasLocalL2Modifier,
				Method sourceLevel,
				Method authority,
				Method l1Epoch,
				Method worldDeltaEpoch,
				Method l2Epoch,
				Method ablStability,
				Method ablMixingStrength,
				Method vecX,
				Method vecY,
				Method vecZ
		) {
			this.sampleGameplay = sampleGameplay;
			this.gameplayServerOnlyPolicy = gameplayServerOnlyPolicy;
			this.hasFlow = hasFlow;
			this.isTrustedForGameplay = isTrustedForGameplay;
			this.meanVelocityVector = meanVelocityVector;
			this.effectiveVelocityVector = effectiveVelocityVector;
			this.gustVelocityVector = gustVelocityVector;
			this.turbulenceIntensity = turbulenceIntensity;
			this.windShearMagnitudePerBlock = windShearMagnitudePerBlock;
			this.shelterFactor = shelterFactor;
			this.updraftMetersPerSecond = updraftMetersPerSecond;
			this.hasTemperature = hasTemperature;
			this.temperatureKelvin = temperatureKelvin;
			this.hasHumidity = hasHumidity;
			this.humidity = humidity;
			this.confidence = confidence;
			this.pressure = pressure;
			this.hasLocalL2Modifier = hasLocalL2Modifier;
			this.sourceLevel = sourceLevel;
			this.authority = authority;
			this.l1Epoch = l1Epoch;
			this.worldDeltaEpoch = worldDeltaEpoch;
			this.l2Epoch = l2Epoch;
			this.ablStability = ablStability;
			this.ablMixingStrength = ablMixingStrength;
			this.vecX = vecX;
			this.vecY = vecY;
			this.vecZ = vecZ;
		}

		@Override
		public WindSample sample(ServerLevel level, Vec3 positionWorldMeters) {
			try {
				net.minecraft.world.phys.Vec3 minecraftPosition = new net.minecraft.world.phys.Vec3(
						positionWorldMeters.x(),
						positionWorldMeters.y(),
						positionWorldMeters.z()
				);
				Object sample = sampleGameplay.invoke(null, level, minecraftPosition, gameplayServerOnlyPolicy);
				if (sample == null) {
					return WindSample.unavailable();
				}
				boolean hasUsableFlow = bool(hasFlow, sample);
				boolean trusted = bool(isTrustedForGameplay, sample);
				if (!hasUsableFlow || !trusted) {
					return WindSample.unavailable();
				}
				boolean hasSampleTemperature = bool(hasTemperature, sample);
				double temperatureCelsius = hasSampleTemperature
						? number(temperatureKelvin.invoke(sample)) - 273.15
						: 0.0;
				boolean hasSampleHumidity = bool(hasHumidity, sample);
				long freshnessAgeTicks = freshnessAgeTicks(
						level.getGameTime(),
						optionalLong(l1Epoch, sample),
						optionalLong(worldDeltaEpoch, sample),
						optionalLong(l2Epoch, sample)
				);
				Vec3 meanVelocity = vec(meanVelocityVector.invoke(sample));
				Vec3 effectiveVelocity = vec(effectiveVelocityVector.invoke(sample));
				Vec3 gustVelocity = gustVelocityVector == null
						? effectiveVelocity.subtract(meanVelocity)
						: vec(gustVelocityVector.invoke(sample));
				return new WindSample(
						true,
						meanVelocity,
						effectiveVelocity,
						gustVelocity,
						number(turbulenceIntensity.invoke(sample)),
						number(windShearMagnitudePerBlock.invoke(sample)),
						number(shelterFactor.invoke(sample)),
						number(updraftMetersPerSecond.invoke(sample)),
						hasSampleTemperature,
						temperatureCelsius,
						hasSampleHumidity,
						number(humidity.invoke(sample)),
						number(confidence.invoke(sample)),
						optionalNumber(pressure, sample),
						trusted,
						hasLocalL2Modifier != null && bool(hasLocalL2Modifier, sample),
						optionalName(sourceLevel, sample, "none"),
						optionalName(authority, sample, "none"),
						freshnessAgeTicks,
						optionalNumber(ablStability, sample),
						optionalNumber(ablMixingStrength, sample)
				);
			} catch (InvocationTargetException error) {
				disableAfterFailure(error.getCause() == null ? error : error.getCause());
				return WindSample.unavailable();
			} catch (ReflectiveOperationException | RuntimeException error) {
				disableAfterFailure(error);
				return WindSample.unavailable();
			}
		}

		private Vec3 vec(Object vector) throws ReflectiveOperationException {
			if (vector == null) {
				return Vec3.ZERO;
			}
			return new Vec3(
					number(vecX.invoke(vector)),
					number(vecY.invoke(vector)),
					number(vecZ.invoke(vector))
			);
		}
	}

	private static boolean bool(Method method, Object target) throws ReflectiveOperationException {
		return Boolean.TRUE.equals(method.invoke(target));
	}

	private static double number(Object value) {
		return value instanceof Number number ? number.doubleValue() : 0.0;
	}

	private static double optionalNumber(Method method, Object target) throws ReflectiveOperationException {
		return method == null ? 0.0 : number(method.invoke(target));
	}

	private static long optionalLong(Method method, Object target) throws ReflectiveOperationException {
		Object value = method == null ? null : method.invoke(target);
		return value instanceof Number number ? number.longValue() : -1L;
	}

	private static String optionalName(Method method, Object target, String fallback) throws ReflectiveOperationException {
		Object value = method == null ? null : method.invoke(target);
		return value == null ? fallback : value.toString();
	}

	private static long freshnessAgeTicks(long currentTick, long l1Epoch, long worldDeltaEpoch, long l2Epoch) {
		long latest = Math.max(l1Epoch, Math.max(worldDeltaEpoch, l2Epoch));
		if (latest < 0L) {
			return -1L;
		}
		return Math.max(0L, currentTick - latest);
	}

	private static void disableAfterFailure(Throwable error) {
		sampler = UnavailableSampler.INSTANCE;
		FpvDronecraftMod.LOGGER.warn("Aerodynamics4MC wind bridge disabled after sampling failure", error);
	}

	public record WindSample(
			boolean hasFlow,
			Vec3 meanVelocityWorldMetersPerSecond,
			Vec3 effectiveVelocityWorldMetersPerSecond,
			Vec3 gustVelocityWorldMetersPerSecond,
			double turbulenceIntensity,
			double windShearMagnitudePerBlock,
			double shelterFactor,
			double updraftMetersPerSecond,
			boolean hasAmbientTemperature,
			double ambientTemperatureCelsius,
			boolean hasHumidity,
			double humidity,
			double confidence,
			double pressureAnomalyPascals,
			boolean trustedForGameplay,
			boolean localVoxelFlow,
			String sourceLevel,
			String sourceAuthority,
			long freshnessAgeTicks,
			double ablStability,
			double ablMixingStrength
	) {
		public WindSample(
				boolean hasFlow,
				Vec3 meanVelocityWorldMetersPerSecond,
				Vec3 effectiveVelocityWorldMetersPerSecond,
				double turbulenceIntensity,
				double windShearMagnitudePerBlock,
				double shelterFactor,
				double updraftMetersPerSecond,
				boolean hasAmbientTemperature,
				double ambientTemperatureCelsius,
				boolean hasHumidity,
				double humidity,
				double confidence,
				double pressureAnomalyPascals,
				boolean trustedForGameplay,
				boolean localVoxelFlow,
				String sourceLevel,
				String sourceAuthority,
				long freshnessAgeTicks,
				double ablStability,
				double ablMixingStrength
		) {
			this(
					hasFlow,
					meanVelocityWorldMetersPerSecond,
					effectiveVelocityWorldMetersPerSecond,
					null,
					turbulenceIntensity,
					windShearMagnitudePerBlock,
					shelterFactor,
					updraftMetersPerSecond,
					hasAmbientTemperature,
					ambientTemperatureCelsius,
					hasHumidity,
					humidity,
					confidence,
					pressureAnomalyPascals,
					trustedForGameplay,
					localVoxelFlow,
					sourceLevel,
					sourceAuthority,
					freshnessAgeTicks,
					ablStability,
					ablMixingStrength
			);
		}

		public WindSample {
			meanVelocityWorldMetersPerSecond = sanitizeVec(meanVelocityWorldMetersPerSecond);
			effectiveVelocityWorldMetersPerSecond = sanitizeVec(effectiveVelocityWorldMetersPerSecond);
			gustVelocityWorldMetersPerSecond = gustVelocityWorldMetersPerSecond == null
					? effectiveVelocityWorldMetersPerSecond.subtract(meanVelocityWorldMetersPerSecond)
					: sanitizeVec(gustVelocityWorldMetersPerSecond);
			turbulenceIntensity = finiteClamped(turbulenceIntensity, 0.0, 1.5, 0.0);
			windShearMagnitudePerBlock = finiteClamped(windShearMagnitudePerBlock, 0.0, 5.0, 0.0);
			shelterFactor = finiteClamped(shelterFactor, 0.0, 1.0, 0.0);
			updraftMetersPerSecond = finiteClamped(updraftMetersPerSecond, -12.0, 12.0, 0.0);
			if (!hasAmbientTemperature || !Double.isFinite(ambientTemperatureCelsius)) {
				hasAmbientTemperature = false;
				ambientTemperatureCelsius = 0.0;
			} else {
				ambientTemperatureCelsius = MathUtil.clamp(ambientTemperatureCelsius, -40.0, 65.0);
			}
			if (!hasHumidity || !Double.isFinite(humidity)) {
				hasHumidity = false;
				humidity = 0.0;
			} else {
				humidity = MathUtil.clamp(humidity, 0.0, 1.0);
			}
			confidence = finiteClamped(confidence, 0.0, 1.0, 0.0);
			pressureAnomalyPascals = finiteClamped(pressureAnomalyPascals, -5000.0, 5000.0, 0.0);
			sourceLevel = sanitizeToken(sourceLevel, "none");
			sourceAuthority = sanitizeToken(sourceAuthority, "none");
			freshnessAgeTicks = freshnessAgeTicks < 0L ? -1L : Math.min(freshnessAgeTicks, 1_000_000L);
			ablStability = finiteClamped(ablStability, -1.0, 1.0, 0.0);
			ablMixingStrength = finiteClamped(ablMixingStrength, 0.0, 1.0, 0.0);
			hasFlow = hasFlow && trustedForGameplay;
		}

		public static WindSample unavailable() {
			return new WindSample(
					false,
					Vec3.ZERO,
					Vec3.ZERO,
					0.0,
					0.0,
					0.0,
					0.0,
					false,
					0.0,
					false,
					0.0,
					0.0,
					0.0,
					false,
					false,
					"none",
					"none",
					-1L,
					0.0,
					0.0
			);
		}

		public double gustSpeedMetersPerSecond() {
			return gustVelocityWorldMetersPerSecond().length();
		}

		private static Vec3 sanitizeVec(Vec3 vector) {
			return vector == null || !vector.isFinite() ? Vec3.ZERO : vector.clamp(-30.0, 30.0);
		}

		private static double finiteClamped(double value, double min, double max, double fallback) {
			return Double.isFinite(value) ? MathUtil.clamp(value, min, max) : fallback;
		}

		private static String sanitizeToken(String value, String fallback) {
			if (value == null || value.isBlank()) {
				return fallback;
			}
			String trimmed = value.trim();
			StringBuilder builder = new StringBuilder(trimmed.length());
			for (int i = 0; i < trimmed.length(); i++) {
				char c = trimmed.charAt(i);
				if (c >= 'A' && c <= 'Z') {
					builder.append((char) (c + 32));
				} else if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-') {
					builder.append(c);
				} else {
					builder.append('_');
				}
			}
			return builder.length() == 0 ? fallback : builder.toString();
		}
	}
}
