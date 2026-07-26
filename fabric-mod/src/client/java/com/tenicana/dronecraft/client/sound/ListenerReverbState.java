package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticEmissionFrame;
import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

final class ListenerReverbState {
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

	Snapshot snapshot() {
		return current.get();
	}

	void publishSources(List<Source> sources) {
		List<Source> immutable = List.copyOf(sources);
		current.updateAndGet(existing ->
				new Snapshot(immutable, existing.environment())
		);
	}

	void publishEnvironment(FdnEnvironmentMapper.Controls environment) {
		Objects.requireNonNull(environment, "environment");
		current.updateAndGet(existing ->
				new Snapshot(existing.sources(), environment)
		);
	}

	void clearSources() {
		publishSources(List.of());
	}

	void advanceShadowHistory() {
		historyProducer.advanceShadow(snapshot().sources());
	}

	void requestWetHistoryOwner() {
		historyProducer.requestWet();
	}

	void enterShadowHistoryMode() {
		historyProducer.enterShadow();
	}

	ListenerReverbHistoryProducer.ActiveSession acquireWetSession() {
		return historyProducer.acquireWet(snapshot().sources());
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
			FdnEnvironmentMapper.Controls environment
	) {
		Snapshot {
			sources = List.copyOf(sources);
			Objects.requireNonNull(environment, "environment");
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
			if (!Double.isFinite(value) || value < 0.0 || value > 4.0) {
				throw new IllegalArgumentException(
						name + " must be in [0, 4]"
				);
			}
		}
	}
}
