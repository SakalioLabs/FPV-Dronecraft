package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;

import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class DroneAtmosphereCacheAllocationTest {
	private static final int WARMUP_ITERATIONS = 100_000;
	private static final int MEASURED_ITERATIONS = 100_000;
	private static final int MEASUREMENT_SAMPLES = 3;

	@Test
	void atmosphereCacheResolveAllocatesZeroBytesAfterWarmup() {
		ThreadMXBean threadBean = allocationTrackingBean();
		long threadId = Thread.currentThread().threadId();

		DroneEnvironment dry = atmosphereEnvironment(Vec3.ZERO, 1.0, 25.0, 25.0, 0.0, 0.0);
		DroneEnvironment equalAtmosphereWindA = atmosphereEnvironment(
				new Vec3(4.0, -2.0, 1.0),
				0.87,
				35.0,
				41.0,
				0.85,
				0.65
		);
		DroneEnvironment equalAtmosphereWindB = atmosphereEnvironment(
				new Vec3(-9.0, 3.0, 7.0),
				0.87,
				35.0,
				41.0,
				0.85,
				0.65
		);
		DroneEnvironment wet = atmosphereEnvironment(Vec3.ZERO, 0.90, 15.0, 45.0, 1.0, 0.80);

		DronePhysics.AtmosphereCache sameIdentityCache = new DronePhysics.AtmosphereCache();
		DronePhysics.AtmosphereCache equalAtmosphereCache = new DronePhysics.AtmosphereCache();
		DronePhysics.AtmosphereCache alternatingCache = new DronePhysics.AtmosphereCache();
		BenchmarkState warmupState = new BenchmarkState();
		BenchmarkState measuredState = new BenchmarkState();

		assertTrue(sameIdentityCache.resolve(dry));
		assertTrue(equalAtmosphereCache.resolve(equalAtmosphereWindA));
		assertTrue(alternatingCache.resolve(wet));

		exerciseResolvePaths(
				sameIdentityCache,
				equalAtmosphereCache,
				alternatingCache,
				dry,
				equalAtmosphereWindA,
				equalAtmosphereWindB,
				wet,
				WARMUP_ITERATIONS,
				warmupState
		);
		assertEquals(0L, warmupState.sameIdentityRecomputations);
		assertEquals(0L, warmupState.equalAtmosphereRecomputations);
		assertEquals(2L * WARMUP_ITERATIONS, warmupState.alternatingRecomputations);
		assertTrue(Double.isFinite(warmupState.checksum));
		assertTrue(warmupState.checksum > 0.0);

		long probe = threadBean.getThreadAllocatedBytes(threadId);
		Assumptions.assumeTrue(probe >= 0L);
		long totalAllocatedBytes = 0L;
		for (int sample = 0; sample < MEASUREMENT_SAMPLES; sample++) {
			long allocatedBefore = threadBean.getThreadAllocatedBytes(threadId);
			exerciseResolvePaths(
					sameIdentityCache,
					equalAtmosphereCache,
					alternatingCache,
					dry,
					equalAtmosphereWindA,
					equalAtmosphereWindB,
					wet,
					MEASURED_ITERATIONS,
					measuredState
			);
			long allocatedAfter = threadBean.getThreadAllocatedBytes(threadId);
			totalAllocatedBytes += allocatedAfter - allocatedBefore;
		}

		long expectedAlternatingRecomputations =
				2L * MEASUREMENT_SAMPLES * MEASURED_ITERATIONS;
		assertEquals(0L, measuredState.sameIdentityRecomputations);
		assertEquals(0L, measuredState.equalAtmosphereRecomputations);
		assertEquals(expectedAlternatingRecomputations, measuredState.alternatingRecomputations);
		assertTrue(Double.isFinite(measuredState.checksum));
		assertTrue(measuredState.checksum > 0.0);
		assertEquals(0L, totalAllocatedBytes);
	}

	private static ThreadMXBean allocationTrackingBean() {
		java.lang.management.ThreadMXBean platformBean = ManagementFactory.getThreadMXBean();
		Assumptions.assumeTrue(platformBean instanceof ThreadMXBean);
		ThreadMXBean threadBean = (ThreadMXBean) platformBean;
		Assumptions.assumeTrue(threadBean.isThreadAllocatedMemorySupported());
		if (!threadBean.isThreadAllocatedMemoryEnabled()) {
			try {
				threadBean.setThreadAllocatedMemoryEnabled(true);
			} catch (SecurityException | UnsupportedOperationException exception) {
				Assumptions.assumeTrue(false);
			}
		}
		Assumptions.assumeTrue(threadBean.isThreadAllocatedMemoryEnabled());
		return threadBean;
	}

	private static void exerciseResolvePaths(
			DronePhysics.AtmosphereCache sameIdentityCache,
			DronePhysics.AtmosphereCache equalAtmosphereCache,
			DronePhysics.AtmosphereCache alternatingCache,
			DroneEnvironment dry,
			DroneEnvironment equalAtmosphereWindA,
			DroneEnvironment equalAtmosphereWindB,
			DroneEnvironment wet,
			int iterations,
			BenchmarkState state
	) {
		for (int iteration = 0; iteration < iterations; iteration++) {
			boolean recomputed = sameIdentityCache.resolve(dry);
			state.sameIdentityRecomputations += recomputed ? 1L : 0L;
			state.checksum += sameIdentityCache.effectiveAirDensityRatio();
		}
		for (int iteration = 0; iteration < iterations; iteration++) {
			boolean recomputedA = equalAtmosphereCache.resolve(equalAtmosphereWindA);
			state.equalAtmosphereRecomputations += recomputedA ? 1L : 0L;
			state.checksum += equalAtmosphereCache.speedOfSoundMetersPerSecond();
			boolean recomputedB = equalAtmosphereCache.resolve(equalAtmosphereWindB);
			state.equalAtmosphereRecomputations += recomputedB ? 1L : 0L;
			state.checksum += equalAtmosphereCache.speedOfSoundMetersPerSecond();
		}
		for (int iteration = 0; iteration < iterations; iteration++) {
			boolean dryRecomputed = alternatingCache.resolve(dry);
			state.alternatingRecomputations += dryRecomputed ? 1L : 0L;
			state.checksum += alternatingCache.moistAirCoolingMultiplier();
			boolean wetRecomputed = alternatingCache.resolve(wet);
			state.alternatingRecomputations += wetRecomputed ? 1L : 0L;
			state.checksum += alternatingCache.moistAirCoolingMultiplier();
		}
	}

	private static DroneEnvironment atmosphereEnvironment(
			Vec3 wind,
			double airDensityRatio,
			double ambientTemperatureCelsius,
			double effectiveAmbientTemperatureCelsius,
			double ambientHumidity,
			double adoptedSourceHumidity
	) {
		return new DroneEnvironment(
				wind,
				airDensityRatio,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				null,
				0.0,
				ambientTemperatureCelsius,
				null,
				effectiveAmbientTemperatureCelsius,
				ambientHumidity,
				adoptedSourceHumidity
		);
	}

	private static final class BenchmarkState {
		private long sameIdentityRecomputations;
		private long equalAtmosphereRecomputations;
		private long alternatingRecomputations;
		private double checksum;
	}
}
