package com.tenicana.dronecraft.client.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;

class AcousticBackendRuntimeTelemetryTest {
	@Test
	void recordsBackendEnvironmentAndFailureWithoutAudio() {
		AcousticBackendRuntimeTelemetry telemetry =
				new AcousticBackendRuntimeTelemetry();
		FdnEnvironmentMapper.Controls environment =
				new FdnEnvironmentMapper.Controls(
						42L,
						new AcousticBands(1.2, 0.8, 0.4),
						0.25,
						0.2
				);
		OpenAlEfxController.Diagnostics efx =
				new OpenAlEfxController.Diagnostics(
						OpenAlEfxController.Status.CONTEXT_FAILED,
						true,
						false,
						true,
						false,
						0,
						0,
						3,
						1,
						0xA004,
						OpenAlEfxController.FaultStage.PARAMETER_WRITE,
						1,
						false
				);

		telemetry.publish(
				99L,
				AcousticBackendSelector.RuntimeBackend.JAVA_FDN,
				environment,
				efx,
				new ListenerReverbHistoryProducer.Diagnostics(
						false,
						true,
						24_000,
						2L,
						10L,
						1L,
						1
				)
		);

		AcousticBackendRuntimeTelemetry.Snapshot snapshot =
				telemetry.snapshot();
		assertEquals(1L, snapshot.sequence());
		assertEquals(
				AcousticBackendSelector.RuntimeBackend.JAVA_FDN,
				snapshot.backend()
		);
		assertEquals(
				OpenAlEfxController.Status.CONTEXT_FAILED,
				snapshot.efxStatus()
		);
		assertEquals(42L, snapshot.environmentGeneration());
		assertEquals(environment.rt60Seconds(), snapshot.rt60Seconds());
		assertEquals(0.25, snapshot.wetGain());
		assertEquals(3, snapshot.contextRebuilds());
		assertEquals(0xA004, snapshot.alErrorCode());
		assertEquals(
				OpenAlEfxController.FaultStage.PARAMETER_WRITE,
				snapshot.lastFaultStage()
		);
		assertEquals(1, snapshot.faultInjectionCount());
		assertTrue(snapshot.javaStreamActive());
		assertFalse(snapshot.javaShadowActive());
		assertTrue(snapshot.javaWetActive());
		assertEquals(24_000, snapshot.javaHistoryFrames());
		assertFalse(snapshot.efxOperational());
		assertFalse(snapshot.doubleWetPath());
		assertFalse(snapshot.capturesAudio());
		assertEquals(1, telemetry.timelineSnapshot().size());
		assertEquals(
				99L,
				telemetry.timelineSnapshot().getFirst().minecraftTick()
		);
		String status = telemetry.status();
		assertTrue(status.contains("backend=JAVA_FDN"));
		assertTrue(status.contains("environmentGeneration=42"));
		assertTrue(status.contains("alError=0xA004"));
		assertTrue(status.endsWith("capturesAudio=false"));
		String timelineJson = telemetry.timelineJson();
		assertTrue(timelineJson.contains(
				"\"status\": \"valid-backend-metadata-timeline\""
		));
		assertTrue(timelineJson.contains("\"event_count\": 1"));
		assertTrue(timelineJson.contains("\"java_shadow_active\":false"));
		assertTrue(timelineJson.contains("\"java_wet_active\":true"));
		assertTrue(timelineJson.contains("\"java_history_frames\":24000"));
		assertTrue(timelineJson.contains("\"captures_audio\": false"));
	}

	@Test
	void distinguishesSilentShadowHistoryFromWetOwnership() {
		AcousticBackendRuntimeTelemetry telemetry =
				new AcousticBackendRuntimeTelemetry();
		OpenAlEfxController.Diagnostics efx =
				new OpenAlEfxController.Diagnostics(
						OpenAlEfxController.Status.OPERATIONAL,
						true,
						true,
						true,
						true,
						2,
						2,
						1,
						0,
						0,
						OpenAlEfxController.FaultStage.NONE,
						0,
						false
				);
		telemetry.publish(
				101L,
				AcousticBackendSelector.RuntimeBackend.OPENAL_EFX,
				new FdnEnvironmentMapper.Controls(
						8L,
						new AcousticBands(0.7, 0.5, 0.3),
						0.2,
						0.2
				),
				efx,
				new ListenerReverbHistoryProducer.Diagnostics(
						true,
						false,
						12_000,
						1L,
						5L,
						0L,
						1
				)
		);
		AcousticBackendRuntimeTelemetry.Snapshot snapshot =
				telemetry.snapshot();
		assertTrue(snapshot.javaShadowActive());
		assertFalse(snapshot.javaWetActive());
		assertFalse(snapshot.javaStreamActive());
		assertEquals(12_000, snapshot.javaHistoryFrames());
		assertFalse(snapshot.doubleWetPath());
	}

	@Test
	void exposesDoubleWetPathAsAContractViolationSignal() {
		AcousticBackendRuntimeTelemetry telemetry =
				new AcousticBackendRuntimeTelemetry();
		OpenAlEfxController.Diagnostics efx =
				new OpenAlEfxController.Diagnostics(
						OpenAlEfxController.Status.OPERATIONAL,
						true,
						true,
						true,
						true,
						2,
						2,
						1,
						0,
						0,
						OpenAlEfxController.FaultStage.NONE,
						0,
						false
				);
		telemetry.publish(
				100L,
				AcousticBackendSelector.RuntimeBackend.OPENAL_EFX,
				new FdnEnvironmentMapper.Controls(
						7L,
						new AcousticBands(0.7, 0.5, 0.3),
						0.2,
						0.2
				),
				efx,
				new ListenerReverbHistoryProducer.Diagnostics(
						false,
						true,
						24_000,
						2L,
						10L,
						1L,
						1
				)
		);
		assertTrue(telemetry.snapshot().doubleWetPath());
	}
}
