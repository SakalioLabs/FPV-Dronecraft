package com.tenicana.dronecraft.client.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class AudioLabMarkerAudioStreamTest {
	@Test
	void finiteMarkerHasExactDurationAndStopsAtEof() {
		AudioLabMarkerAudioStream stream =
				new AudioLabMarkerAudioStream(880.0);
		List<Short> samples = new ArrayList<>();
		while (true) {
			ByteBuffer buffer = stream.read(317)
					.order(ByteOrder.LITTLE_ENDIAN);
			if (!buffer.hasRemaining()) {
				break;
			}
			while (buffer.remaining() >= 2) {
				samples.add(buffer.getShort());
			}
		}
		assertEquals(
				Math.round(
						AudioLabMarkerAudioStream.SAMPLE_RATE
								* AudioLabMarkerAudioStream
										.DURATION_SECONDS
				),
				samples.size()
		);
		assertEquals(0, stream.read(512).remaining());
		assertTrue(Math.abs(samples.get(1)) < 500);
		assertTrue(
				Math.abs(samples.get(samples.size() - 1)) < 500
		);
		assertTrue(
				samples.stream().anyMatch(value -> Math.abs(value) > 9_000)
		);
	}

	@Test
	void closeMakesFurtherReadsEmpty() {
		AudioLabMarkerAudioStream stream =
				new AudioLabMarkerAudioStream(1320.0);
		assertTrue(stream.read(256).hasRemaining());
		stream.close();
		assertFalse(stream.read(256).hasRemaining());
	}
}
