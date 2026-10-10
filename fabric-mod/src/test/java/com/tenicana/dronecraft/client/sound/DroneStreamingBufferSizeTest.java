package com.tenicana.dronecraft.client.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.sounds.AudioStream;
import org.junit.jupiter.api.Test;

class DroneStreamingBufferSizeTest {
	private static final int FRAME_SIZE = Short.BYTES;
	private static final int SIXTY_TWO_POINT_FIVE_MS_OF_MONO_S16 = 6_000;

	@Test
	void proceduralStreamKeepsTwoHundredMsGapPlusFiftyMsOfQueueReserve() {
		ProceduralDroneAudioStream stream = new ProceduralDroneAudioStream(
				new DroneAcousticRenderState(), PhaseContinuousSynthesizer.Layer.MOTOR, 1);
		try {
			assertOwnedBuffer(stream);
		} finally {
			stream.close();
		}
	}

	@Test
	void calibratedAssetStreamUsesVerifiedSyntheticPcmFormat() {
		byte[] pcm = littleEndian(new short[]{1000, -1000});
		double digitalRms = 1000.0 / 32768.0;
		AudioAssetCalibration reference = new AudioAssetCalibration(
				VerifiedAssetPcm.sha256(pcm), VerifiedAssetPcm.sha256(pcm),
				48_000, 2, 0, 2, digitalRms, digitalRms, 1.0,
				10_000, 0,
				List.of(5_000.0, 10_000.0, 20_000.0),
				List.of(-1.0, 0.0, 1.0),
				List.of(List.of(0.0, 0.0, 0.0),
						List.of(0.0, 0.0, 0.0),
						List.of(0.0, 0.0, 0.0)));
		VerifiedAssetPcm verified = VerifiedAssetPcm.verify(reference, pcm);
		CalibratedAssetAudioStream stream = new CalibratedAssetAudioStream(
				verified, true, () -> 1.0);
		try {
			assertOwnedBuffer(stream);
		} finally {
			stream.close();
		}
	}

	@Test
	void listenerWetStreamUsesTheSameWholeFrameDuration() {
		ListenerReverbAudioStream stream = new ListenerReverbAudioStream(
				new ListenerReverbState());
		try {
			assertOwnedBuffer(stream);
		} finally {
			stream.close();
		}
	}

	@Test
	void foreignStreamKeepsVanillaSizeWithoutQueryingItsFormat() throws IOException {
		AtomicBoolean formatRead = new AtomicBoolean();
		AudioStream foreign = new AudioStream() {
			@Override
			public AudioFormat getFormat() {
				formatRead.set(true);
				throw new AssertionError("foreign format must not be inspected");
			}

			@Override
			public ByteBuffer read(int requestedBytes) {
				return ByteBuffer.allocate(0);
			}

			@Override
			public void close() {}
		};

		assertEquals(12_345, DroneStreamingBufferSize.bytesPerBuffer(foreign, 12_345));
		assertFalse(formatRead.get());
	}

	@Test
	void ownedSizeRejectsInvalidVanillaDomainAndNeverReturnsPartialOrZeroFrame() {
		ProceduralDroneAudioStream stream = new ProceduralDroneAudioStream(
				new DroneAcousticRenderState(), PhaseContinuousSynthesizer.Layer.MOTOR, 2);
		try {
			assertThrows(IllegalArgumentException.class,
					() -> DroneStreamingBufferSize.bytesPerBuffer(stream, FRAME_SIZE - 1));
			assertThrows(IllegalArgumentException.class,
					() -> DroneStreamingBufferSize.bytesPerBuffer(stream, FRAME_SIZE + 1));

			int bytes = DroneStreamingBufferSize.bytesPerBuffer(stream, FRAME_SIZE);
			assertTrue(bytes >= FRAME_SIZE, "buffer must contain at least one complete frame");
			assertEquals(0, bytes % FRAME_SIZE, "buffer must end on a complete frame");
			assertEquals(FRAME_SIZE, bytes);
			assertEquals(4_000, DroneStreamingBufferSize.bytesPerBuffer(stream, 4_000));
		} finally {
			stream.close();
		}
	}

	private static void assertOwnedBuffer(AudioStream stream) {
		AudioFormat format = stream.getFormat();
		assertEquals(48_000.0f, format.getSampleRate());
		assertEquals(16, format.getSampleSizeInBits());
		assertEquals(1, format.getChannels());
		int frameSize = format.getFrameSize();
		assertEquals(FRAME_SIZE, frameSize);

		int bytes = DroneStreamingBufferSize.bytesPerBuffer(stream, 16_384);
		int oneSecondBytes = (int) format.getFrameRate() * frameSize;
		int expected = SIXTY_TWO_POINT_FIVE_MS_OF_MONO_S16;
		assertEquals(12_000, expected / frameSize * 4);
		assertEquals(2_400, expected / frameSize * 4 - 9_600);
		assertEquals(expected, bytes);
		assertEquals(expected, DroneStreamingBufferSize.bytesPerBuffer(stream, oneSecondBytes));
		assertTrue(bytes > 0);
		assertEquals(0, bytes % frameSize);
	}

	private static byte[] littleEndian(short[] samples) {
		ByteBuffer bytes = ByteBuffer.allocate(samples.length * Short.BYTES)
				.order(ByteOrder.LITTLE_ENDIAN);
		for (short sample : samples) bytes.putShort(sample);
		return bytes.array();
	}
}
