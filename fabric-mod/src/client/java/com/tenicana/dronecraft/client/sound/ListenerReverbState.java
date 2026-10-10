package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticEmissionFrame;
import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

final class ListenerReverbState {
	// Schema-valid +24 dB calibration is approximately 15.8489 in amplitude.
	private static final double MAXIMUM_SOURCE_AMPLITUDE = Math.pow(10.0, 24.0 / 20.0);
	private static final FdnEnvironmentMapper.Controls ANECHOIC =
			new FdnEnvironmentMapper.Controls(
					-1L,
					AcousticBands.SILENT,
					0.0,
					FdnEnvironmentMapper.DEFAULT_TRANSITION_SECONDS
			);
	private final AtomicReference<Snapshot> current =
			new AtomicReference<>(new Snapshot(List.of(), ANECHOIC));
	private final ListenerReverbHistoryProducer historyProducer =
			new ListenerReverbHistoryProducer();
	private DroneCallbackControlCoordinator callbackControls;
	private volatile Object callbackStream;

	Snapshot snapshot() {
		return current.get();
	}

	void useCallbackControls(DroneCallbackControlCoordinator controls) { callbackControls = controls; }
	void registerCallbackStream(Object stream) { callbackStream = stream; }
	void closeCallbackStream(Object stream) { if (callbackStream == stream) callbackStream = null; }
	Object callbackStream() { return callbackStream; }

	Snapshot callbackSnapshot(Object stream) {
		DroneCallbackControlCoordinator controls = callbackControls;
		if (controls == null || !(stream instanceof CallbackPcmStream candidate)
				|| !candidate.callbackControlsEnrolled()) return current.get();
		DroneAudioControlFrame frame = controls.committedFrame();
		Snapshot selected = frame == null ? null : frame.wetSnapshotFor(this, stream);
		return selected != null ? selected : (Snapshot) candidate.previousCallbackControl();
	}

	void publishSources(List<Source> sources) {
		List<Source> immutable = List.copyOf(sources);
		var prepared = PreparedListenerReverbMixer.prepare(immutable);
		current.updateAndGet(existing ->
				new Snapshot(immutable, existing.environment(), prepared)
		);
	}

	void publishEnvironment(FdnEnvironmentMapper.Controls environment) {
		Objects.requireNonNull(environment, "environment");
		current.updateAndGet(existing ->
				new Snapshot(existing.sources(), environment, existing.preparedSources())
		);
	}

	void clearSources() {
		publishSources(List.of());
	}

	void advanceShadowHistory() {
		historyProducer.advanceShadow(snapshot().preparedSources());
	}

	void requestWetHistoryOwner() {
		historyProducer.requestWet();
	}

	void enterShadowHistoryMode() {
		historyProducer.enterShadow();
	}

	ListenerReverbHistoryProducer.ActiveSession acquireWetSession() {
		return historyProducer.acquireWet(snapshot());
	}

	ListenerReverbHistoryProducer.Diagnostics historyDiagnostics() {
		return historyProducer.diagnostics();
	}

	void reset() {
		current.set(new Snapshot(List.of(), ANECHOIC));
		historyProducer.reset();
	}

	record Snapshot(
			List<Source> sources,
			FdnEnvironmentMapper.Controls environment,
			PreparedListenerReverbMixer.PreparedSources preparedSources
	) {
		Snapshot(List<Source> sources, FdnEnvironmentMapper.Controls environment) {
			this(List.copyOf(sources), environment, PreparedListenerReverbMixer.prepare(sources));
		}
		Snapshot {
			sources = List.copyOf(sources);
			Objects.requireNonNull(environment, "environment");
			Objects.requireNonNull(preparedSources, "preparedSources");
			if (!sources.equals(preparedSources.originalSources())) {
				throw new IllegalArgumentException("prepared sources differ from publication");
			}
			// Reject unsupported FDN controls on the publisher, before render entry.
			if (environment.transitionSeconds() > 10.0
					|| environment.rt60Seconds().low() > 20.0
					|| environment.rt60Seconds().mid() > 20.0
					|| environment.rt60Seconds().high() > 20.0) {
				throw new IllegalArgumentException("environment exceeds FDN control bounds");
			}
		}
	}

	record Source(
			int entityId,
			AcousticEmissionFrame emission,
			double motorAmplitude,
			double propellerAmplitude
	) {
		Source {
			Objects.requireNonNull(emission, "emission");
			requireAmplitude(motorAmplitude, "motorAmplitude");
			requireAmplitude(propellerAmplitude, "propellerAmplitude");
		}

		private static void requireAmplitude(double value, String name) {
			if (!Double.isFinite(value) || value < 0.0 || value > MAXIMUM_SOURCE_AMPLITUDE) {
				throw new IllegalArgumentException(
						name + " exceeds the schema-valid nonnegative playback amplitude"
				);
			}
		}
	}
}
