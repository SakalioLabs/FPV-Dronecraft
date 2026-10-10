package com.tenicana.dronecraft.client.sound;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundEngine;

/**
 * Client-thread state machine for synchronized external audio capture.
 *
 * <p>The lab never records audio. It selects one mutually-exclusive backend,
 * emits three finite audible markers, optionally reloads Minecraft's sound
 * engine, and writes the authoritative event timeline used to align a
 * separately authorized recorder.
 */
final class AudioLabController {
	private static final int PRE_MARKER_TICK = 40;
	private static final int BOUNDARY_TICK = 50;
	private static final int MINIMUM_POST_BOUNDARY_TICK = 60;
	private static final int NATIVE_TIMING_FIRST_TICK = 20;
	private static final int NATIVE_TIMING_INTERVAL_TICKS = 4;
	private static final long NATIVE_TIMING_MINIMUM_HOST_INTERVAL_NS =
			150_000_000L;
	private static final int POST_TAIL_TICKS = 100;
	private static final int MAXIMUM_SESSION_TICKS = 400;
	private static final double START_MARKER_HZ = 880.0;
	private static final double PRE_BOUNDARY_MARKER_HZ = 1320.0;
	private static final double POST_BOUNDARY_MARKER_HZ = 1760.0;

	enum Variant {
		CONTROL("control", false),
		RELOAD("reload", true);

		private final String token;
		private final boolean reload;

		Variant(String token, boolean reload) {
			this.token = token;
			this.reload = reload;
		}
	}

	private final AtomicReference<String> status =
			new AtomicReference<>("audio lab idle");
	private Session session;

	Path start(
			Path output,
			AcousticBackendSelector.Mode backend,
			Variant variant
	) {
		Objects.requireNonNull(output, "output");
		Objects.requireNonNull(backend, "backend");
		Objects.requireNonNull(variant, "variant");
		if (session != null) {
			throw new IllegalStateException("an audio-lab session is active");
		}
		if (!Boolean.parseBoolean(
				System.getProperty("fpvdrone.proceduralAudio", "true")
		)) {
			throw new IllegalStateException(
					"audio lab requires fpvdrone.proceduralAudio=true"
			);
		}
		Path normalized = output.toAbsolutePath().normalize();
		if (Files.exists(normalized)) {
			throw new IllegalArgumentException(
					"refusing to overwrite audio-lab report: " + normalized
			);
		}
		if (!AcousticBackendSelector.activate(backend)) {
			throw new IllegalStateException(
					"another acoustic backend override is active"
			);
		}
		session = new Session(normalized, backend, variant);
		status.set(
				"audio lab active: backend=" + backend.token()
						+ ", variant=" + variant.token
						+ ", output=" + normalized
		);
		return normalized;
	}

	String status() {
		return status.get();
	}

	boolean active() {
		return session != null;
	}

	void tick(
			Minecraft client,
			int activeDroneSoundSets,
			boolean javaReverbActive,
			OpenAlEfxController.Diagnostics efx
	) {
		Session current = session;
		if (current == null) {
			return;
		}
		try {
			current.tick(
					client,
					activeDroneSoundSets,
					javaReverbActive,
					efx
			);
			if (current.complete) {
				complete(current);
			}
		} catch (RuntimeException error) {
			abort("audio lab failed: " + error.getMessage());
			throw error;
		}
	}

	void abort(String reason) {
		if (session == null) {
			return;
		}
		session = null;
		AcousticBackendSelector.restoreDefault();
		status.set(reason);
	}

	private void complete(Session completed) {
		AcousticBackendSelector.restoreDefault();
		Path temporary = completed.output.resolveSibling(
				completed.output.getFileName() + ".tmp-"
						+ Long.toUnsignedString(System.nanoTime())
		);
		try {
			Files.createDirectories(completed.output.getParent());
			Files.writeString(
					temporary,
					completed.toJson(),
					StandardCharsets.UTF_8
			);
			try {
				Files.move(
						temporary,
						completed.output,
						StandardCopyOption.ATOMIC_MOVE
				);
			} catch (AtomicMoveNotSupportedException unsupported) {
				Files.move(temporary, completed.output);
			}
		} catch (IOException error) {
			try {
				Files.deleteIfExists(temporary);
			} catch (IOException ignored) {
				// Preserve the original report failure.
			}
			abort("audio lab report write failed: " + error.getMessage());
			throw new IllegalStateException(
					"could not write audio-lab report",
					error
			);
		}
		session = null;
		status.set("audio lab complete: " + completed.output);
	}

	private static final class Session {
		private final Path output;
		private final AcousticBackendSelector.Mode backend;
		private final Variant variant;
		private final long startedNs = System.nanoTime();
		private final List<Event> events = new ArrayList<>();
		private int tick;
		private int postMarkerTick = -1;
		private boolean boundaryApplied;
		private boolean complete;
		private BackendState beforeBoundary;
		private BackendState afterBoundary;
		private long reloadCallDurationNs;
		private CompletableFuture<OpenAlClockLatencyProbe.Sample>
				beforeFirstPending;
		private CompletableFuture<OpenAlClockLatencyProbe.Sample>
				beforeSecondPending;
		private CompletableFuture<OpenAlClockLatencyProbe.Sample>
				afterFirstPending;
		private CompletableFuture<OpenAlClockLatencyProbe.Sample>
				afterSecondPending;
		private OpenAlClockLatencyProbe.Sample beforeFirst;
		private OpenAlClockLatencyProbe.Sample beforeSecond;
		private OpenAlClockLatencyProbe.Sample afterFirst;
		private OpenAlClockLatencyProbe.Sample afterSecond;
		private OpenAlClockLatencyProbe.PairResult beforeTiming;
		private OpenAlClockLatencyProbe.PairResult afterTiming;

		private Session(
				Path output,
				AcousticBackendSelector.Mode backend,
				Variant variant
		) {
			this.output = output;
			this.backend = backend;
			this.variant = variant;
		}

		private void tick(
				Minecraft client,
				int activeDroneSoundSets,
				boolean javaReverbActive,
				OpenAlEfxController.Diagnostics efx
		) {
			BackendState state = BackendState.capture(
					activeDroneSoundSets,
					javaReverbActive,
					efx
			);
			progressBeforeTiming(client);
			progressAfterTiming(client);
			if (tick == 0) {
				playMarker(client, "session_start_marker", START_MARKER_HZ);
			}
			if (tick == PRE_MARKER_TICK) {
				playMarker(
						client,
						"pre_boundary_marker",
						PRE_BOUNDARY_MARKER_HZ
				);
			}
			if (!boundaryApplied && tick == BOUNDARY_TICK) {
				if (beforeTiming == null) {
					throw new IllegalStateException(
							"pre-boundary native timing did not complete"
					);
				}
				beforeBoundary = state;
				if (variant.reload) {
					event("sound_engine_reload_requested", null);
					long before = System.nanoTime();
					client.getSoundManager().reload();
					reloadCallDurationNs = System.nanoTime() - before;
					event("sound_engine_reload_returned", null);
				} else {
					event("control_boundary_no_reload", null);
				}
				boundaryApplied = true;
			}
			if (boundaryApplied
					&& postMarkerTick < 0
					&& tick >= MINIMUM_POST_BOUNDARY_TICK
					&& backendReady(state)) {
				afterBoundary = state;
				playMarker(
						client,
						"post_boundary_marker",
						POST_BOUNDARY_MARKER_HZ
				);
				postMarkerTick = tick;
				afterFirstPending = OpenAlClockLatencyProbe.capture(
						client.getSoundManager()
				);
			}
			if (postMarkerTick >= 0
					&& afterTiming != null
					&& tick >= postMarkerTick + POST_TAIL_TICKS) {
				event("session_complete", null);
				complete = true;
			}
			if (!complete && tick >= MAXIMUM_SESSION_TICKS) {
				throw new IllegalStateException(
						"backend did not become ready before timeout"
				);
			}
			tick++;
		}

		private void progressBeforeTiming(Minecraft client) {
			if (tick == NATIVE_TIMING_FIRST_TICK) {
				beforeFirstPending = OpenAlClockLatencyProbe.capture(
						client.getSoundManager()
				);
			}
			if (beforeFirst == null
					&& beforeFirstPending != null
					&& beforeFirstPending.isDone()) {
				beforeFirst = beforeFirstPending.join();
				beforeFirstPending = null;
			}
			if (beforeFirst != null
					&& beforeSecond == null
					&& beforeSecondPending == null
					&& tick >= NATIVE_TIMING_FIRST_TICK
							+ NATIVE_TIMING_INTERVAL_TICKS
					&& System.nanoTime() - beforeFirst.monotonicNs()
							>= NATIVE_TIMING_MINIMUM_HOST_INTERVAL_NS) {
				beforeSecondPending = OpenAlClockLatencyProbe.capture(
						client.getSoundManager()
				);
			}
			if (beforeSecond == null
					&& beforeSecondPending != null
					&& beforeSecondPending.isDone()) {
				beforeSecond = beforeSecondPending.join();
				beforeSecondPending = null;
				beforeTiming = OpenAlClockLatencyProbe.validatePair(
						beforeFirst,
						beforeSecond
				);
			}
		}

		private void progressAfterTiming(Minecraft client) {
			if (postMarkerTick < 0) {
				return;
			}
			if (afterFirst == null
					&& afterFirstPending != null
					&& afterFirstPending.isDone()) {
				afterFirst = afterFirstPending.join();
				afterFirstPending = null;
			}
			if (afterFirst != null
					&& afterSecond == null
					&& afterSecondPending == null
					&& tick >= postMarkerTick
							+ NATIVE_TIMING_INTERVAL_TICKS
					&& System.nanoTime() - afterFirst.monotonicNs()
							>= NATIVE_TIMING_MINIMUM_HOST_INTERVAL_NS) {
				afterSecondPending = OpenAlClockLatencyProbe.capture(
						client.getSoundManager()
				);
			}
			if (afterSecond == null
					&& afterSecondPending != null
					&& afterSecondPending.isDone()) {
				afterSecond = afterSecondPending.join();
				afterSecondPending = null;
				afterTiming = OpenAlClockLatencyProbe.validatePair(
						afterFirst,
						afterSecond
				);
			}
		}

		private boolean backendReady(BackendState state) {
			if (state.activeDroneSoundSets < 1) {
				return false;
			}
			return switch (backend) {
				case DRY -> !state.javaReverbActive
						&& !state.efxOperational
						&& !state.efxResources;
				case JAVA_FDN -> state.javaReverbActive
						&& !state.efxOperational
						&& !state.efxResources;
				case OPENAL_EFX -> !state.javaReverbActive
						&& state.efxOperational
						&& state.efxResources
						&& state.efxAttachedSources >= 2
						&& state.efxSourceFilters
								== state.efxAttachedSources;
				case DEFAULT -> false;
			};
		}

		private void playMarker(
				Minecraft client,
				String name,
				double frequencyHz
		) {
			SoundEngine.PlayResult result = client.getSoundManager().play(
					new AudioLabMarkerSoundInstance(frequencyHz)
			);
			event(name, result.name() + "@" + frequencyHz + "Hz");
			if (result == SoundEngine.PlayResult.NOT_STARTED) {
				throw new IllegalStateException(
						"audio-lab marker did not start: " + name
				);
			}
		}

		private void event(String name, String detail) {
			events.add(new Event(
					name,
					tick,
					System.nanoTime() - startedNs,
					detail
			));
		}

		private String toJson() {
			if (beforeTiming == null || afterTiming == null) {
				throw new IllegalStateException(
						"native timing evidence is incomplete"
				);
			}
			boolean supportStable =
					beforeTiming.first().deviceClockSupported()
							== afterTiming.first()
									.deviceClockSupported()
					&& beforeTiming.first().sourceLatencySupported()
							== afterTiming.first()
									.sourceLatencySupported()
					&& beforeTiming.first().nativeTelemetryAvailable()
							== afterTiming.first()
									.nativeTelemetryAvailable();
			boolean deviceStable = beforeTiming.first().deviceName()
					.equals(afterTiming.first().deviceName());
			if (!supportStable || !deviceStable) {
				throw new IllegalStateException(
						"native timing identity changed across boundary"
				);
			}
			StringBuilder eventJson = new StringBuilder();
			for (int index = 0; index < events.size(); index++) {
				if (index > 0) {
					eventJson.append(",\n");
				}
				eventJson.append("    ")
						.append(events.get(index).toJson());
			}
			return String.format(
					Locale.ROOT,
					"{\n"
							+ "  \"schema_version\": 2,\n"
							+ "  \"status\": "
							+ "\"valid-audio-lab-timeline\",\n"
							+ "  \"backend\": \"%s\",\n"
							+ "  \"variant\": \"%s\",\n"
							+ "  \"procedural_audio_required\": true,\n"
							+ "  \"backend_override_default_before\": true,\n"
							+ "  \"backend_override_restored_after\": true,\n"
							+ "  \"marker_contract\": {"
							+ "\"duration_s\":%.6f,"
							+ "\"start_hz\":%.1f,"
							+ "\"pre_boundary_hz\":%.1f,"
							+ "\"post_boundary_hz\":%.1f},\n"
							+ "  \"sound_engine_reload_exercised\": %s,\n"
							+ "  \"reload_call_duration_ns\": %d,\n"
							+ "  \"before_boundary\": %s,\n"
							+ "  \"after_boundary\": %s,\n"
							+ "  \"native_timing\": {"
							+ "\"probe\":\"OpenAlClockLatencyProbe\","
							+ "\"read_only\":true,"
							+ "\"before_boundary\":%s,"
							+ "\"after_boundary\":%s,"
							+ "\"support_stable_across_boundary\":true,"
							+ "\"device_name_stable_across_boundary\":true,"
							+ "\"audio_path_changed\":false,"
							+ "\"end_to_end_latency_measured\":false,"
							+ "\"callback_underrun_counter_available\":"
							+ "false},\n"
							+ "  \"events\": [\n%s\n  ],\n"
							+ "  \"real_audio_capture\": false,\n"
							+ "  \"physical_output_loopback_confirmed\": "
							+ "false,\n"
							+ "  \"release_calibrated\": false,\n"
							+ "  \"claim_boundary\": \"Minecraft client "
							+ "backend selection, audible marker scheduling, "
							+ "event timeline, and read-only available "
							+ "OpenAL timing telemetry only; no recorder or "
							+ "loopback endpoint was opened, and renderer "
							+ "latency is not end-to-end latency.\"\n"
							+ "}\n",
					backend.token(),
					variant.token,
					AudioLabMarkerAudioStream.DURATION_SECONDS,
					START_MARKER_HZ,
					PRE_BOUNDARY_MARKER_HZ,
					POST_BOUNDARY_MARKER_HZ,
					variant.reload,
					reloadCallDurationNs,
					beforeBoundary.toJson(),
					afterBoundary.toJson(),
					beforeTiming.toJson(),
					afterTiming.toJson(),
					eventJson
			);
		}
	}

	private record Event(
			String name,
			int tick,
			long relativeNs,
			String detail
	) {
		private String toJson() {
			return String.format(
					Locale.ROOT,
					"{\"name\":\"%s\",\"tick\":%d,"
							+ "\"relative_ns\":%d,\"detail\":%s}",
					name,
					tick,
					relativeNs,
					detail == null
							? "null"
							: "\"" + detail + "\""
			);
		}
	}

	private record BackendState(
			int activeDroneSoundSets,
			boolean javaReverbActive,
			boolean efxOperational,
			boolean efxResources,
			int efxAttachedSources,
			int efxSourceFilters,
			int efxContextRebuilds,
			int efxAlErrorCode
	) {
		private static BackendState capture(
				int activeDroneSoundSets,
				boolean javaReverbActive,
				OpenAlEfxController.Diagnostics efx
		) {
			return new BackendState(
					activeDroneSoundSets,
					javaReverbActive,
					efx.operational(),
					efx.sharedResourcesCreated(),
					efx.attachedSources(),
					efx.allocatedSourceFilters(),
					efx.contextRebuilds(),
					efx.alErrorCode()
			);
		}

		private String toJson() {
			return String.format(
					Locale.ROOT,
					"{\"active_drone_sound_sets\":%d,"
							+ "\"java_reverb_active\":%s,"
							+ "\"efx_operational\":%s,"
							+ "\"efx_resources\":%s,"
							+ "\"efx_attached_sources\":%d,"
							+ "\"efx_source_filters\":%d,"
							+ "\"efx_context_rebuilds\":%d,"
							+ "\"efx_al_error_code\":%d}",
					activeDroneSoundSets,
					javaReverbActive,
					efxOperational,
					efxResources,
					efxAttachedSources,
					efxSourceFilters,
					efxContextRebuilds,
					efxAlErrorCode
			);
		}
	}
}
