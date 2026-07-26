package com.tenicana.dronecraft.client.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OpenAlClockLatencyProbeTest {
	@Test
	void validatesAdvancingNativeTelemetry() {
		OpenAlClockLatencyProbe.PairResult result =
				OpenAlClockLatencyProbe.validatePair(
						sample(
								1_000_000_000L,
								5_000_000_000L,
								20_000_000L,
								true
						),
						sample(
								1_200_000_000L,
								5_200_000_000L,
								21_000_000L,
								true
						)
				);
		assertTrue(result.nativeTelemetryValidated());
		assertEquals(200_000_000L, result.hostElapsedNs());
		assertEquals(200_000_000L, result.deviceClockElapsedNs());
		assertEquals(1.0, result.deviceToHostClockRateRatio(), 1.0e-12);
		assertEquals(0.2, result.sourceOffsetAdvanceSeconds(), 1.0e-12);
		assertFalse(result.sourceOffsetWrapped());
	}

	@Test
	void acceptsConsistentUnsupportedFallbackWithoutInventingValues() {
		OpenAlClockLatencyProbe.PairResult result =
				OpenAlClockLatencyProbe.validatePair(
						sample(10L, 0L, 0L, false),
						sample(20L, 0L, 0L, false)
				);
		assertFalse(result.nativeTelemetryValidated());
		assertEquals(0L, result.deviceClockElapsedNs());
		assertEquals(0.0, result.deviceToHostClockRateRatio());
		assertEquals(0.0, result.sourceOffsetAdvanceSeconds());
		assertFalse(result.sourceOffsetWrapped());
	}

	@Test
	void validatesSourceLatencyWhenDeviceClockIsUnavailable() {
		OpenAlClockLatencyProbe.PairResult result =
				OpenAlClockLatencyProbe.validatePair(
						sourceLatencyOnlySample(100L, 0.10, 0.025),
						sourceLatencyOnlySample(
								100_000_100L,
								0.20,
								0.026
						)
				);
		assertTrue(result.nativeTelemetryValidated());
		assertEquals(0L, result.deviceClockElapsedNs());
		assertEquals(0.0, result.deviceToHostClockRateRatio());
		assertEquals(0.1, result.sourceOffsetAdvanceSeconds(), 1.0e-12);
		assertFalse(result.sourceOffsetWrapped());
	}

	@Test
	void acceptsOneSecondStreamingBufferRollover() {
		OpenAlClockLatencyProbe.PairResult result =
				OpenAlClockLatencyProbe.validatePair(
						sourceLatencyOnlySample(
								1_000_000_000L,
								0.90,
								0.025
						),
						sourceLatencyOnlySample(
								1_200_000_000L,
								0.10,
								0.026
						)
				);
		assertEquals(0.2, result.sourceOffsetAdvanceSeconds(), 1.0e-12);
		assertTrue(result.sourceOffsetWrapped());
	}

	@Test
	void rejectsNonadvancingClockAndInvalidLatency() {
		assertThrows(
				IllegalArgumentException.class,
				() -> OpenAlClockLatencyProbe.validatePair(
						sample(100L, 500L, 20L, true),
						sample(200L, 500L, 20L, true)
				)
		);
		assertThrows(
				IllegalArgumentException.class,
				() -> OpenAlClockLatencyProbe.validatePair(
						sample(100L, 500L, 20L, true),
						sample(200L, 600L, -1L, true)
				)
		);
		assertThrows(
				IllegalArgumentException.class,
				() -> OpenAlClockLatencyProbe.validatePair(
						sourceLatencyOnlySample(100L, 0.10, 0.025),
						sourceLatencyOnlySample(
								100_000_100L,
								0.10,
								0.026
						)
				)
		);
	}

	private static OpenAlClockLatencyProbe.Sample sample(
			long hostNs,
			long clockNs,
			long latencyNs,
			boolean telemetry
	) {
		return new OpenAlClockLatencyProbe.Sample(
				true,
				"DroneLoopSoundInstance",
				"Sound engine",
				hostNs,
				true,
				"fixture-device",
				telemetry,
				telemetry,
				telemetry,
				clockNs,
				latencyNs,
				telemetry ? hostNs / 1.0e9 : 0.0,
				telemetry ? latencyNs / 1.0e9 : 0.0,
				telemetry ? hostNs / 1.0e9 : 0.0,
				telemetry ? clockNs / 1.0e9 : 0.0,
				0,
				0
		);
	}

	private static OpenAlClockLatencyProbe.Sample sourceLatencyOnlySample(
			long hostNs,
			double offsetSeconds,
			double latencySeconds
	) {
		return new OpenAlClockLatencyProbe.Sample(
				true,
				"DroneLoopSoundInstance",
				"Sound engine",
				hostNs,
				true,
				"fixture-device",
				false,
				true,
				true,
				0L,
				0L,
				offsetSeconds,
				latencySeconds,
				0.0,
				0.0,
				0,
				0
		);
	}
}
