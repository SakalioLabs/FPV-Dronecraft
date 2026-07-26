package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticEmissionFrame;
import com.tenicana.dronecraft.acoustics.TonalComponent;
import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListenerReverbStateTest {
	@Test
	void sourceAndEnvironmentPublicationsPreserveTheOtherHalf() {
		ListenerReverbState state = new ListenerReverbState();
		ListenerReverbState.Source source = new ListenerReverbState.Source(
				7,
				emission(),
				0.5,
				0.75
		);
		FdnEnvironmentMapper.Controls environment =
				new FdnEnvironmentMapper.Controls(
						42L,
						new AcousticBands(1.0, 0.7, 0.4),
						0.3,
						0.2
				);

		state.publishSources(List.of(source));
		state.publishEnvironment(environment);

		assertEquals(List.of(source), state.snapshot().sources());
		assertEquals(environment, state.snapshot().environment());
		state.clearSources();
		assertTrue(state.snapshot().sources().isEmpty());
		assertEquals(environment, state.snapshot().environment());
		state.reset();
		assertTrue(state.snapshot().sources().isEmpty());
		assertEquals(-1L, state.snapshot().environment().snapshotGeneration());
		assertEquals(0.0, state.snapshot().environment().wetGain());
	}

	@Test
	void keepsBoundedShadowHistoryAndTransfersExclusivePhaseOwnership() {
		ListenerReverbState state = new ListenerReverbState();
		state.publishSources(List.of(new ListenerReverbState.Source(
				7,
				emission(),
				0.5,
				0.75
		)));
		for (int tick = 0; tick < 12; tick++) {
			state.advanceShadowHistory();
		}

		ListenerReverbHistoryProducer.Diagnostics shadow =
				state.historyDiagnostics();
		assertTrue(shadow.shadowActive());
		assertFalse(shadow.wetActive());
		assertEquals(
				ListenerReverbHistoryProducer.HISTORY_FRAMES,
				shadow.historyFrames()
		);
		assertEquals(1, shadow.synthesizerCount());

		state.requestWetHistoryOwner();
		ListenerReverbHistoryProducer.ActiveSession first =
				state.acquireWetSession();
		assertEquals(
				ListenerReverbHistoryProducer.HISTORY_FRAMES,
				first.history().length
		);
		assertFalse(state.historyDiagnostics().shadowActive());
		assertTrue(state.historyDiagnostics().wetActive());
		assertEquals(1, state.historyDiagnostics().synthesizerCount());

		ListenerReverbHistoryProducer.ActiveSession replacement =
				state.acquireWetSession();
		float[] invalidated = new float[256];
		float[] active = new float[256];
		assertFalse(first.render(
				state.snapshot().sources(),
				invalidated,
				invalidated.length
		));
		assertTrue(replacement.render(
				state.snapshot().sources(),
				active,
				active.length
		));
		assertEquals(0, nonZeroSamples(invalidated));
		assertTrue(nonZeroSamples(active) > 0);

		first.close();
		assertTrue(state.historyDiagnostics().wetActive());
		replacement.close();
		assertFalse(state.historyDiagnostics().wetActive());

		state.enterShadowHistoryMode();
		assertTrue(state.historyDiagnostics().shadowActive());
		assertEquals(0, state.historyDiagnostics().historyFrames());
		assertEquals(0, state.historyDiagnostics().synthesizerCount());
	}

	private static int nonZeroSamples(float[] samples) {
		int count = 0;
		for (float sample : samples) {
			if (sample != 0.0F) {
				count++;
			}
		}
		return count;
	}

	static AcousticEmissionFrame emission() {
		return new AcousticEmissionFrame(
				List.of(
						new TonalComponent(
								TonalComponent.Kind.ELECTRICAL,
								0,
								1,
								700.0,
								0.3,
								0.0
						),
						new TonalComponent(
								TonalComponent.Kind.BLADE_PASS,
								0,
								1,
								1_000.0,
								0.4,
								0.5
						)
				),
				new AcousticBands(0.01, 0.02, 0.03)
		);
	}
}
