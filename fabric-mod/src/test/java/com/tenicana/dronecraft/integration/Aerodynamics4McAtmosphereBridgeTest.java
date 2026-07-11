package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.server.level.ServerLevel;

class Aerodynamics4McAtmosphereBridgeTest {
	@Test
	void missingModReturnsAllocationFreeUnavailableSingletonWithoutBindingClasses() {
		Aerodynamics4McAtmosphereBridge.Sampler absent = Aerodynamics4McAtmosphereBridge.bindIfAvailable(
				false,
				null,
				null
		);

		Aerodynamics4McAtmosphereBridge.AtmosphereSample first = absent.sample(null, 0.0, 0.0, 0.0, 0L);
		Aerodynamics4McAtmosphereBridge.AtmosphereSample second = absent.sample(null, 0.0, 0.0, 0.0, 1L);

		assertSame(Aerodynamics4McAtmosphereBridge.AtmosphereSample.unavailable(), first);
		assertSame(first, second);
		assertNeutralOptionalFlow(first);
	}

	@Test
	void thermalLessOldApiKeepsRequiredQualityContractAndReportsUnknownThermals() {
		OldApi.lastPolicy = null;
		Aerodynamics4McAtmosphereBridge.Sampler sampler = Aerodynamics4McAtmosphereBridge.bindIfAvailable(
				true,
				OldApi.class,
				StubPolicy.class
		);

		Aerodynamics4McAtmosphereBridge.AtmosphereSample sample = sampler.sample(
				null,
				1.25,
				2.5,
				-3.75,
				120L
		);

		assertTrue(sample.hasFlow());
		assertTrue(sample.trustedForGameplay());
		assertEquals(0.6, sample.confidence(), 0.0);
		assertEquals(-1L, sample.freshnessAgeTicks());
		assertFalse(sample.hasTemperature());
		assertFalse(sample.hasHumidity());
		assertSame(StubPolicy.GAMEPLAY_SERVER_ONLY, OldApi.lastPolicy);
		assertNeutralOptionalFlow(sample);
	}

	@Test
	void incompatibleOldTwoArgumentApiSafelyFallsBackToUnavailable() {
		Aerodynamics4McAtmosphereBridge.Sampler sampler = Aerodynamics4McAtmosphereBridge.bindIfAvailable(
				true,
				TwoArgumentApi.class,
				StubPolicy.class
		);

		assertSame(
				Aerodynamics4McAtmosphereBridge.AtmosphereSample.unavailable(),
				sampler.sample(null, 0.0, 0.0, 0.0, 0L)
		);
	}

	@Test
	void samplesServerOnlyPolicyConvertsKelvinAndSanitizesCompactFields() {
		CurrentApi.lastPosition = null;
		CurrentApi.lastPolicy = null;
		CurrentApi.next = new CurrentSample(
				true,
				true,
				1.4,
				true,
				400.0,
				true,
				-0.2,
				91L,
				95L,
				93L
		);
		Aerodynamics4McAtmosphereBridge.Sampler sampler = Aerodynamics4McAtmosphereBridge.bindIfAvailable(
				true,
				CurrentApi.class,
				StubPolicy.class
		);

		Aerodynamics4McAtmosphereBridge.AtmosphereSample sample = sampler.sample(
				null,
				4.5,
				-2.25,
				7.75,
				100L
		);

		assertTrue(sample.hasFlow());
		assertTrue(sample.trustedForGameplay());
		assertEquals(1.0, sample.confidence(), 0.0);
		assertEquals(5L, sample.freshnessAgeTicks());
		assertTrue(sample.hasTemperature());
		assertEquals(65.0, sample.temperatureCelsius(), 0.0);
		assertTrue(sample.hasHumidity());
		assertEquals(0.0, sample.humidity(), 0.0);
		assertNeutralOptionalFlow(sample);
		assertSame(StubPolicy.GAMEPLAY_SERVER_ONLY, CurrentApi.lastPolicy);
		assertEquals(4.5, CurrentApi.lastPosition.x, 0.0);
		assertEquals(-2.25, CurrentApi.lastPosition.y, 0.0);
		assertEquals(7.75, CurrentApi.lastPosition.z, 0.0);

		CurrentApi.next = new CurrentSample(
				true,
				true,
				-0.4,
				true,
				300.0,
				true,
				1.3,
				101L,
				90L,
				80L
		);
		Aerodynamics4McAtmosphereBridge.AtmosphereSample converted = sampler.sample(
				null,
				0.0,
				0.0,
				0.0,
				100L
		);
		assertEquals(0.0, converted.confidence(), 0.0);
		assertEquals(0L, converted.freshnessAgeTicks());
		assertEquals(26.85, converted.temperatureCelsius(), 1.0e-12);
		assertEquals(1.0, converted.humidity(), 0.0);

		CurrentApi.next = new CurrentSample(
				true,
				false,
				Double.NaN,
				true,
				Double.NaN,
				true,
				Double.POSITIVE_INFINITY,
				-1L,
				-1L,
				-1L
		);
		Aerodynamics4McAtmosphereBridge.AtmosphereSample nonFinite = sampler.sample(
				null,
				0.0,
				0.0,
				0.0,
				100L
		);
		assertEquals(0.0, nonFinite.confidence(), 0.0);
		assertEquals(-1L, nonFinite.freshnessAgeTicks());
		assertFalse(nonFinite.hasTemperature());
		assertFalse(nonFinite.hasHumidity());
		assertNeutralOptionalFlow(nonFinite);
	}

	@Test
	void richFlowApiSamplesAndSanitizesEveryCompactPrimitive() {
		RichFlowApi.next = new RichFlowSample(
				true,
				true,
				0.75,
				new StubVector(40.0, -50.0, 3.0),
				new StubVector(3.0, 4.0, 12.0),
				1.8,
				7.0,
				1.4,
				-14.0,
				true,
				6000.0,
				-321.0,
				true,
				310.0,
				true,
				0.65,
				95L,
				90L,
				80L
		);
		Aerodynamics4McAtmosphereBridge.Sampler sampler = Aerodynamics4McAtmosphereBridge.bindIfAvailable(
				true,
				RichFlowApi.class,
				StubPolicy.class
		);

		Aerodynamics4McAtmosphereBridge.AtmosphereSample sample = sampler.sample(
				null,
				1.0,
				2.0,
				3.0,
				100L
		);

		assertTrue(sample.hasFlow());
		assertTrue(sample.trustedForGameplay());
		assertEquals(0.75, sample.confidence(), 0.0);
		assertEquals(5L, sample.freshnessAgeTicks());
		assertTrue(sample.hasMeanVelocity());
		assertEquals(30.0, sample.meanVelocityX(), 0.0);
		assertEquals(-30.0, sample.meanVelocityY(), 0.0);
		assertEquals(3.0, sample.meanVelocityZ(), 0.0);
		assertEquals(1.5, sample.turbulenceIntensity(), 0.0);
		assertEquals(5.0, sample.windShearMagnitudePerBlock(), 0.0);
		assertEquals(1.0, sample.shelterFactor(), 0.0);
		assertEquals(-12.0, sample.updraftMetersPerSecond(), 0.0);
		assertEquals(13.0, sample.gustSpeedMetersPerSecond(), 0.0);
		assertEquals(4.0, sample.gustVerticalMetersPerSecond(), 0.0);
		assertEquals(3.0, sample.gustVelocityXMetersPerSecond(), 0.0);
		assertEquals(12.0, sample.gustVelocityZMetersPerSecond(), 0.0);
		assertTrue(sample.localVoxelFlow());
		assertEquals(5000.0, sample.pressureAnomalyPascals(), 0.0,
				"pressureAnomalyPascals must take precedence over the legacy pressure accessor");
		assertTrue(sample.hasTemperature());
		assertEquals(36.85, sample.temperatureCelsius(), 1.0e-12);
		assertTrue(sample.hasHumidity());
		assertEquals(0.65, sample.humidity(), 0.0);

		RichFlowApi.next = new RichFlowSample(
				true,
				true,
				1.0,
				new StubVector(Double.NaN, 2.0, 3.0),
				new StubVector(Double.POSITIVE_INFINITY, 4.0, 0.0),
				Double.NaN,
				-3.0,
				Double.POSITIVE_INFINITY,
				Double.NaN,
				false,
				Double.NaN,
				123.0,
				false,
				0.0,
				false,
				0.0,
				-1L,
				-1L,
				-1L
		);
		Aerodynamics4McAtmosphereBridge.AtmosphereSample nonFinite = sampler.sample(
				null,
				0.0,
				0.0,
				0.0,
				100L
		);

		assertFalse(nonFinite.hasMeanVelocity());
		assertEquals(0.0, nonFinite.meanVelocityX(), 0.0);
		assertEquals(0.0, nonFinite.meanVelocityY(), 0.0);
		assertEquals(0.0, nonFinite.meanVelocityZ(), 0.0);
		assertEquals(0.0, nonFinite.turbulenceIntensity(), 0.0);
		assertEquals(0.0, nonFinite.windShearMagnitudePerBlock(), 0.0);
		assertEquals(0.0, nonFinite.shelterFactor(), 0.0);
		assertEquals(0.0, nonFinite.updraftMetersPerSecond(), 0.0);
		assertEquals(0.0, nonFinite.gustSpeedMetersPerSecond(), 0.0);
		assertEquals(0.0, nonFinite.gustVerticalMetersPerSecond(), 0.0);
		assertEquals(0.0, nonFinite.gustVelocityXMetersPerSecond(), 0.0);
		assertEquals(0.0, nonFinite.gustVelocityZMetersPerSecond(), 0.0);
		assertFalse(nonFinite.localVoxelFlow());
		assertEquals(0.0, nonFinite.pressureAnomalyPascals(), 0.0);
	}

	@Test
	void legacyPressureAccessorRemainsCompatibleWhenPreferredNameIsAbsent() {
		Aerodynamics4McAtmosphereBridge.Sampler sampler = Aerodynamics4McAtmosphereBridge.bindIfAvailable(
				true,
				LegacyPressureApi.class,
				StubPolicy.class
		);

		Aerodynamics4McAtmosphereBridge.AtmosphereSample sample = sampler.sample(
				null,
				0.0,
				0.0,
				0.0,
				0L
		);

		assertTrue(sample.hasFlow());
		assertEquals(-321.0, sample.pressureAnomalyPascals(), 0.0);
		assertFalse(sample.hasTemperature());
		assertFalse(sample.hasHumidity());
	}

	@Test
	void reflectiveSamplingFailurePermanentlyDisablesBoundSampler() {
		FailingApi.invocations = 0;
		Aerodynamics4McAtmosphereBridge.Sampler sampler = Aerodynamics4McAtmosphereBridge.bindIfAvailable(
				true,
				FailingApi.class,
				StubPolicy.class
		);

		Aerodynamics4McAtmosphereBridge.AtmosphereSample first = sampler.sample(null, 0.0, 0.0, 0.0, 0L);
		Aerodynamics4McAtmosphereBridge.AtmosphereSample second = sampler.sample(null, 0.0, 0.0, 0.0, 1L);

		assertSame(Aerodynamics4McAtmosphereBridge.AtmosphereSample.unavailable(), first);
		assertSame(first, second);
		assertEquals(1, FailingApi.invocations, "a failed reflection path must never be retried");
	}

	@Test
	void freshnessUsesNewestOptionalEpochAndHandlesUnknownOrFutureEpochs() {
		assertEquals(-1L, Aerodynamics4McAtmosphereBridge.freshnessAgeTicks(100L, -1L, -1L, -1L));
		assertEquals(-1L, Aerodynamics4McAtmosphereBridge.freshnessAgeTicks(-1L, 90L, 80L, 70L));
		assertEquals(10L, Aerodynamics4McAtmosphereBridge.freshnessAgeTicks(100L, 90L, 80L, 70L));
		assertEquals(0L, Aerodynamics4McAtmosphereBridge.freshnessAgeTicks(100L, 101L, 80L, 70L));
	}

	private static void assertNeutralOptionalFlow(
			Aerodynamics4McAtmosphereBridge.AtmosphereSample sample
	) {
		assertFalse(sample.hasMeanVelocity());
		assertEquals(0.0, sample.meanVelocityX(), 0.0);
		assertEquals(0.0, sample.meanVelocityY(), 0.0);
		assertEquals(0.0, sample.meanVelocityZ(), 0.0);
		assertEquals(0.0, sample.turbulenceIntensity(), 0.0);
		assertEquals(0.0, sample.windShearMagnitudePerBlock(), 0.0);
		assertEquals(0.0, sample.shelterFactor(), 0.0);
		assertEquals(0.0, sample.updraftMetersPerSecond(), 0.0);
		assertEquals(0.0, sample.gustSpeedMetersPerSecond(), 0.0);
		assertEquals(0.0, sample.gustVerticalMetersPerSecond(), 0.0);
		assertEquals(0.0, sample.gustVelocityXMetersPerSecond(), 0.0);
		assertEquals(0.0, sample.gustVelocityZMetersPerSecond(), 0.0);
		assertFalse(sample.localVoxelFlow());
		assertEquals(0.0, sample.pressureAnomalyPascals(), 0.0);
	}

	public enum StubPolicy {
		GAMEPLAY_SERVER_ONLY
	}

	public static final class OldApi {
		private static StubPolicy lastPolicy;

		public static OldSample sampleGameplay(
				ServerLevel level,
				net.minecraft.world.phys.Vec3 position,
				StubPolicy policy
		) {
			lastPolicy = policy;
			return new OldSample();
		}
	}

	public static final class OldSample {
		public boolean hasFlow() {
			return true;
		}

		public boolean isTrustedForGameplay() {
			return true;
		}

		public double confidence() {
			return 0.6;
		}
	}

	public static final class TwoArgumentApi {
		public static OldSample sampleGameplay(ServerLevel level, net.minecraft.world.phys.Vec3 position) {
			return new OldSample();
		}
	}

	public static final class CurrentApi {
		private static CurrentSample next;
		private static net.minecraft.world.phys.Vec3 lastPosition;
		private static StubPolicy lastPolicy;

		public static CurrentSample sampleGameplay(
				ServerLevel level,
				net.minecraft.world.phys.Vec3 position,
				StubPolicy policy
		) {
			lastPosition = position;
			lastPolicy = policy;
			return next;
		}
	}

	public record CurrentSample(
			boolean hasFlow,
			boolean isTrustedForGameplay,
			double confidence,
			boolean hasTemperature,
			double temperatureKelvin,
			boolean hasHumidity,
			double humidity,
			long l1Epoch,
			long worldDeltaEpoch,
			long l2Epoch
	) {
	}

	public static final class RichFlowApi {
		private static RichFlowSample next;

		public static RichFlowSample sampleGameplay(
				ServerLevel level,
				net.minecraft.world.phys.Vec3 position,
				StubPolicy policy
		) {
			return next;
		}
	}

	public record StubVector(double x, double y, double z) {
	}

	public record RichFlowSample(
			boolean hasFlow,
			boolean isTrustedForGameplay,
			double confidence,
			StubVector meanVelocityVector,
			StubVector gustVelocityVector,
			double turbulenceIntensity,
			double windShearMagnitudePerBlock,
			double shelterFactor,
			double updraftMetersPerSecond,
			boolean hasLocalL2Modifier,
			double pressureAnomalyPascals,
			double pressure,
			boolean hasTemperature,
			double temperatureKelvin,
			boolean hasHumidity,
			double humidity,
			long l1Epoch,
			long worldDeltaEpoch,
			long l2Epoch
	) {
	}

	public static final class LegacyPressureApi {
		public static LegacyPressureSample sampleGameplay(
				ServerLevel level,
				net.minecraft.world.phys.Vec3 position,
				StubPolicy policy
		) {
			return new LegacyPressureSample();
		}
	}

	public static final class LegacyPressureSample {
		public boolean hasFlow() {
			return true;
		}

		public boolean isTrustedForGameplay() {
			return true;
		}

		public double confidence() {
			return 1.0;
		}

		public double pressure() {
			return -321.0;
		}
	}

	public static final class FailingApi {
		private static int invocations;

		public static CurrentSample sampleGameplay(
				ServerLevel level,
				net.minecraft.world.phys.Vec3 position,
				StubPolicy policy
		) {
			invocations++;
			throw new IllegalStateException("synthetic A4MC sampling failure");
		}
	}
}
