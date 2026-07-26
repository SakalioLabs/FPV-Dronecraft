package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListenerReverbAudioStreamTest {
	@Test
	void oneSharedStreamMixesSourcesAndPreservesTailAfterRemoval() {
		ListenerReverbState state = new ListenerReverbState();
		state.publishEnvironment(new FdnEnvironmentMapper.Controls(
				9L,
				new AcousticBands(1.2, 0.9, 0.6),
				0.35,
				0.2
		));
		state.publishSources(List.of(
				new ListenerReverbState.Source(
						1,
						ListenerReverbStateTest.emission(),
						0.5,
						0.5
				),
				new ListenerReverbState.Source(
						2,
						ListenerReverbStateTest.emission(),
						0.25,
						0.25
				)
		));
		ListenerReverbAudioStream stream =
				new ListenerReverbAudioStream(state);

		ByteBuffer first = stream.read(16_384);
		assertEquals(16_384, first.remaining());
		assertTrue(nonZeroSamples(first) > 0);

		state.clearSources();
		ByteBuffer tail = stream.read(16_384);
		assertTrue(nonZeroSamples(tail) > 0);
		assertEquals(0, stream.synthesizerCount());

		stream.close();
		assertTrue(stream.closed());
		assertEquals(0, stream.bufferCapacitySamples());
		assertEquals(0, stream.read(4_096).remaining());
		stream.close();
		assertEquals(0, stream.read(4_096).remaining());
	}

	@Test
	void replacementStreamSurvivesClosingPrecedingStream() {
		ListenerReverbState state = new ListenerReverbState();
		state.publishEnvironment(new FdnEnvironmentMapper.Controls(
				10L,
				new AcousticBands(1.0, 0.7, 0.4),
				0.3,
				0.0
		));
		state.publishSources(List.of(new ListenerReverbState.Source(
				3,
				ListenerReverbStateTest.emission(),
				0.5,
				0.5
		)));
		ListenerReverbAudioStream preceding =
				new ListenerReverbAudioStream(state);
		ListenerReverbAudioStream replacement =
				new ListenerReverbAudioStream(state);
		assertEquals(0, nonZeroSamples(preceding.read(4_096)));
		assertTrue(nonZeroSamples(replacement.read(4_096)) > 0);

		preceding.close();
		assertEquals(0, preceding.read(4_096).remaining());
		assertTrue(nonZeroSamples(replacement.read(4_096)) > 0);
		assertEquals(1, replacement.synthesizerCount());

		replacement.close();
		assertEquals(0, replacement.synthesizerCount());
	}

	private static int nonZeroSamples(ByteBuffer buffer) {
		int nonZero = 0;
		while (buffer.remaining() >= Short.BYTES) {
			if (buffer.getShort() != 0) {
				nonZero++;
			}
		}
		return nonZero;
	}
}
