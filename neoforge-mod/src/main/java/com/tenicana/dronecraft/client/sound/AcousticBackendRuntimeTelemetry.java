package com.tenicana.dronecraft.client.sound;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;

/**
 * Metadata-only runtime evidence for backend ownership and environment state.
 *
 * <p>No PCM, microphone, endpoint, player identity, or world coordinates are
 * retained. The snapshot is intentionally sufficient to prove which wet path
 * owned a tick and why a fallback occurred.
 */
final class AcousticBackendRuntimeTelemetry {
	private static final int MAXIMUM_TIMELINE_EVENTS = 256;
	private final AtomicLong sequence = new AtomicLong();
	private final AtomicReference<Snapshot> current =
			new AtomicReference<>(Snapshot.inactive());
	private final ArrayDeque<Event> timeline = new ArrayDeque<>();
	private TransitionKey lastTimelineKey;

	Snapshot snapshot() {
		return current.get();
	}

	String status() {
		Snapshot value = snapshot();
		return String.format(
				Locale.ROOT,
				"backend=%s, efx=%s, environmentGeneration=%d, "
						+ "rt60Seconds=[%.6f,%.6f,%.6f], wetGain=%.6f, "
						+ "transitionSeconds=%.6f, contextRebuilds=%d, "
						+ "alError=0x%04X, lastFaultStage=%s, "
						+ "faultInjectionCount=%d, javaShadowActive=%s, "
						+ "javaWetActive=%s, javaHistoryFrames=%d, "
						+ "efxOperational=%s, doubleWetPath=%s, "
						+ "capturesAudio=false",
				value.backend(),
				value.efxStatus(),
				value.environmentGeneration(),
				value.rt60Seconds().low(),
				value.rt60Seconds().mid(),
				value.rt60Seconds().high(),
				value.wetGain(),
				value.transitionSeconds(),
				value.contextRebuilds(),
				value.alErrorCode(),
				value.lastFaultStage(),
				value.faultInjectionCount(),
				value.javaShadowActive(),
				value.javaWetActive(),
				value.javaHistoryFrames(),
				value.efxOperational(),
				value.doubleWetPath()
		);
	}

	void publish(
			long minecraftTick,
			AcousticBackendSelector.RuntimeBackend backend,
			FdnEnvironmentMapper.Controls environment,
			OpenAlEfxController.Diagnostics efx,
			ListenerReverbHistoryProducer.Diagnostics javaHistory
	) {
		Objects.requireNonNull(backend, "backend");
		Objects.requireNonNull(environment, "environment");
		Objects.requireNonNull(efx, "efx");
		Objects.requireNonNull(javaHistory, "javaHistory");
		boolean efxActive = efx.operational();
		boolean javaWetActive = javaHistory.wetActive();
		boolean doubleWetPath = javaWetActive && efxActive;
		long nextSequence = sequence.incrementAndGet();
		Snapshot next = new Snapshot(
				nextSequence,
				backend,
				efx.status(),
				environment.snapshotGeneration(),
				environment.rt60Seconds(),
				environment.wetGain(),
				environment.transitionSeconds(),
				efx.contextRebuilds(),
				efx.alErrorCode(),
				efx.lastFaultStage(),
				efx.faultInjectionCount(),
				javaWetActive,
				javaHistory.shadowActive(),
				javaWetActive,
				javaHistory.historyFrames(),
				efxActive,
				doubleWetPath,
				false
		);
		current.set(next);
		recordTransition(minecraftTick, next);
	}

	synchronized List<Event> timelineSnapshot() {
		return List.copyOf(new ArrayList<>(timeline));
	}

	String timelineJson() {
		List<Event> events = timelineSnapshot();
		String payload = events.stream()
				.map(Event::toJson)
				.reduce((left, right) -> left + ",\n    " + right)
				.orElse("");
		return String.format(
				Locale.ROOT,
				"{%n"
						+ "  \"schema_version\": 1,%n"
						+ "  \"status\": \"valid-backend-metadata-timeline\",%n"
						+ "  \"capacity\": %d,%n"
						+ "  \"event_count\": %d,%n"
						+ "  \"events\": [%n    %s%n  ],%n"
						+ "  \"captures_audio\": false,%n"
						+ "  \"physical_endpoint_controlled\": false,%n"
						+ "  \"release_calibrated\": false,%n"
						+ "  \"claim_boundary\": \"Transition metadata only; "
						+ "host monotonic time and Minecraft ticks can align a "
						+ "separately authorized recorder, but no PCM, endpoint, "
						+ "microphone, player identity, or coordinates are "
						+ "captured.\"%n"
						+ "}%n",
				MAXIMUM_TIMELINE_EVENTS,
				events.size(),
				payload
		);
	}

	private synchronized void recordTransition(
			long minecraftTick,
			Snapshot snapshot
	) {
		TransitionKey key = new TransitionKey(
				snapshot.backend(),
				snapshot.efxStatus(),
				snapshot.environmentGeneration(),
				snapshot.contextRebuilds(),
				snapshot.alErrorCode(),
				snapshot.lastFaultStage(),
				snapshot.faultInjectionCount(),
				snapshot.javaStreamActive(),
				snapshot.javaShadowActive(),
				snapshot.javaWetActive(),
				snapshot.javaHistoryFrames(),
				snapshot.efxOperational(),
				snapshot.doubleWetPath()
		);
		if (key.equals(lastTimelineKey)) {
			return;
		}
		lastTimelineKey = key;
		timeline.addLast(new Event(
				snapshot.sequence(),
				System.nanoTime(),
				minecraftTick,
				snapshot.backend(),
				snapshot.efxStatus(),
				snapshot.environmentGeneration(),
				snapshot.rt60Seconds(),
				snapshot.wetGain(),
				snapshot.transitionSeconds(),
				snapshot.contextRebuilds(),
				snapshot.alErrorCode(),
				snapshot.lastFaultStage(),
				snapshot.faultInjectionCount(),
				snapshot.javaStreamActive(),
				snapshot.javaShadowActive(),
				snapshot.javaWetActive(),
				snapshot.javaHistoryFrames(),
				snapshot.efxOperational(),
				snapshot.doubleWetPath(),
				false
		));
		while (timeline.size() > MAXIMUM_TIMELINE_EVENTS) {
			timeline.removeFirst();
		}
	}

	record Snapshot(
			long sequence,
			AcousticBackendSelector.RuntimeBackend backend,
			OpenAlEfxController.Status efxStatus,
			long environmentGeneration,
			AcousticBands rt60Seconds,
			double wetGain,
			double transitionSeconds,
			int contextRebuilds,
			int alErrorCode,
			OpenAlEfxController.FaultStage lastFaultStage,
			int faultInjectionCount,
			boolean javaStreamActive,
			boolean javaShadowActive,
			boolean javaWetActive,
			int javaHistoryFrames,
			boolean efxOperational,
			boolean doubleWetPath,
			boolean capturesAudio
	) {
		Snapshot {
			Objects.requireNonNull(backend, "backend");
			Objects.requireNonNull(efxStatus, "efxStatus");
			Objects.requireNonNull(rt60Seconds, "rt60Seconds");
			Objects.requireNonNull(lastFaultStage, "lastFaultStage");
			if (sequence < 0L || contextRebuilds < 0
					|| faultInjectionCount < 0 || javaHistoryFrames < 0
					|| javaHistoryFrames
					> ListenerReverbHistoryProducer.HISTORY_FRAMES) {
				throw new IllegalArgumentException("negative telemetry counter");
			}
			if (javaStreamActive != javaWetActive
					|| javaShadowActive && javaWetActive) {
				throw new IllegalArgumentException(
						"invalid Java reverb ownership telemetry"
				);
			}
			if (!Double.isFinite(wetGain)
					|| !Double.isFinite(transitionSeconds)) {
				throw new IllegalArgumentException(
						"non-finite environment telemetry"
				);
			}
			if (capturesAudio) {
				throw new IllegalArgumentException(
						"runtime backend telemetry must not capture audio"
				);
			}
		}

		private static Snapshot inactive() {
			return new Snapshot(
					0L,
					AcousticBackendSelector.RuntimeBackend.CLEAN,
					OpenAlEfxController.Status.INACTIVE,
					-1L,
					AcousticBands.SILENT,
					0.0,
					0.0,
					0,
					0,
					OpenAlEfxController.FaultStage.NONE,
					0,
					false,
					true,
					false,
					0,
					false,
					false,
					false
			);
		}
	}

	record Event(
			long telemetrySequence,
			long hostMonotonicNanos,
			long minecraftTick,
			AcousticBackendSelector.RuntimeBackend backend,
			OpenAlEfxController.Status efxStatus,
			long environmentGeneration,
			AcousticBands rt60Seconds,
			double wetGain,
			double transitionSeconds,
			int contextRebuilds,
			int alErrorCode,
			OpenAlEfxController.FaultStage lastFaultStage,
			int faultInjectionCount,
			boolean javaStreamActive,
			boolean javaShadowActive,
			boolean javaWetActive,
			int javaHistoryFrames,
			boolean efxOperational,
			boolean doubleWetPath,
			boolean capturesAudio
	) {
		Event {
			Objects.requireNonNull(backend, "backend");
			Objects.requireNonNull(efxStatus, "efxStatus");
			Objects.requireNonNull(rt60Seconds, "rt60Seconds");
			Objects.requireNonNull(lastFaultStage, "lastFaultStage");
			if (telemetrySequence < 1L || hostMonotonicNanos <= 0L
					|| minecraftTick < 0L || javaHistoryFrames < 0
					|| javaHistoryFrames
					> ListenerReverbHistoryProducer.HISTORY_FRAMES
					|| javaStreamActive != javaWetActive
					|| javaShadowActive && javaWetActive
					|| capturesAudio) {
				throw new IllegalArgumentException(
						"invalid backend timeline event"
				);
			}
		}

		private String toJson() {
			return String.format(
					Locale.ROOT,
					"{\"telemetry_sequence\":%d,"
							+ "\"host_monotonic_nanos\":%d,"
							+ "\"minecraft_tick\":%d,"
							+ "\"backend\":\"%s\","
							+ "\"efx_status\":\"%s\","
							+ "\"environment_generation\":%d,"
							+ "\"rt60_seconds\":{\"low\":%.17g,"
							+ "\"mid\":%.17g,\"high\":%.17g},"
							+ "\"wet_gain\":%.17g,"
							+ "\"transition_seconds\":%.17g,"
							+ "\"context_rebuilds\":%d,"
							+ "\"al_error_code\":%d,"
							+ "\"last_fault_stage\":\"%s\","
							+ "\"fault_injection_count\":%d,"
							+ "\"java_stream_active\":%s,"
							+ "\"java_shadow_active\":%s,"
							+ "\"java_wet_active\":%s,"
							+ "\"java_history_frames\":%d,"
							+ "\"efx_operational\":%s,"
							+ "\"double_wet_path\":%s,"
							+ "\"captures_audio\":false}",
					telemetrySequence,
					hostMonotonicNanos,
					minecraftTick,
					backend,
					efxStatus,
					environmentGeneration,
					rt60Seconds.low(),
					rt60Seconds.mid(),
					rt60Seconds.high(),
					wetGain,
					transitionSeconds,
					contextRebuilds,
					alErrorCode,
					lastFaultStage.token(),
					faultInjectionCount,
					javaStreamActive,
					javaShadowActive,
					javaWetActive,
					javaHistoryFrames,
					efxOperational,
					doubleWetPath
			);
		}
	}

	private record TransitionKey(
			AcousticBackendSelector.RuntimeBackend backend,
			OpenAlEfxController.Status efxStatus,
			long environmentGeneration,
			int contextRebuilds,
			int alErrorCode,
			OpenAlEfxController.FaultStage lastFaultStage,
			int faultInjectionCount,
			boolean javaStreamActive,
			boolean javaShadowActive,
			boolean javaWetActive,
			int javaHistoryFrames,
			boolean efxOperational,
			boolean doubleWetPath
	) {
	}
}
