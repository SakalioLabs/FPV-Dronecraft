package com.tenicana.dronecraft.client.sound;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.sound.sampled.AudioFormat;

import org.lwjgl.BufferUtils;

import net.minecraft.client.sounds.AudioStream;

final class AudioLabMarkerAudioStream implements AudioStream {
	static final int SAMPLE_RATE = 48_000;
	static final double DURATION_SECONDS = 0.08;
	private static final int TOTAL_SAMPLES =
			(int) Math.round(SAMPLE_RATE * DURATION_SECONDS);
	private static final int FADE_SAMPLES =
			(int) Math.round(SAMPLE_RATE * 0.005);
	private static final AudioFormat FORMAT = new AudioFormat(
			SAMPLE_RATE,
			16,
			1,
			true,
			false
	);

	private final double frequencyHz;
	private int position;
	private boolean closed;

	AudioLabMarkerAudioStream(double frequencyHz) {
		if (!Double.isFinite(frequencyHz)
				|| frequencyHz < 100.0
				|| frequencyHz > 8_000.0) {
			throw new IllegalArgumentException(
					"marker frequency must be finite and in [100, 8000] Hz"
			);
		}
		this.frequencyHz = frequencyHz;
	}

	@Override
	public AudioFormat getFormat() {
		return FORMAT;
	}

	@Override
	public ByteBuffer read(int requestedBytes) {
		if (closed || requestedBytes <= 1 || position >= TOTAL_SAMPLES) {
			return BufferUtils.createByteBuffer(0);
		}
		int requestedSamples = requestedBytes / 2;
		int sampleCount = Math.min(
				requestedSamples,
				TOTAL_SAMPLES - position
		);
		ByteBuffer output = BufferUtils.createByteBuffer(sampleCount * 2)
				.order(ByteOrder.LITTLE_ENDIAN);
		for (int offset = 0; offset < sampleCount; offset++) {
			int index = position + offset;
			double envelope = Math.min(
					1.0,
					Math.min(
							(index + 1.0) / FADE_SAMPLES,
							(TOTAL_SAMPLES - index)
									/ (double) FADE_SAMPLES
					)
			);
			double phase = 2.0 * Math.PI * frequencyHz
					* index / SAMPLE_RATE;
			short sample = (short) Math.round(
					Math.sin(phase) * envelope * 0.35 * Short.MAX_VALUE
			);
			output.putShort(sample);
		}
		position += sampleCount;
		return output.flip();
	}

	@Override
	public void close() {
		closed = true;
	}
}
