package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class ProceduralDroneAudioStream implements AudioStream {
	private static final AudioFormat FORMAT = new AudioFormat(
			DroneAcousticRenderState.SAMPLE_RATE,
			16,
			1,
			true,
			false
	);

	private final DroneAcousticRenderState state;
	private final PhaseContinuousSynthesizer synthesizer;
	private final PhaseContinuousSynthesizer.Layer layer;
	private volatile boolean closed;
	private float[] sampleBuffer = new float[0];

	ProceduralDroneAudioStream(
			DroneAcousticRenderState state,
			PhaseContinuousSynthesizer.Layer layer,
			int noiseSeed
	) {
		this.state = state;
		this.layer = layer;
		this.synthesizer = new PhaseContinuousSynthesizer(
				DroneAcousticRenderState.SAMPLE_RATE,
				noiseSeed
		);
	}

	@Override
	public AudioFormat getFormat() {
		return FORMAT;
	}

	@Override
	public ByteBuffer read(int requestedBytes) {
		if (closed || requestedBytes <= 0) {
			return BufferUtils.createByteBuffer(0);
		}
		int byteCount = requestedBytes & ~1;
		int sampleCount = byteCount / Short.BYTES;
		if (sampleBuffer.length < sampleCount) {
			sampleBuffer = new float[sampleCount];
		}
		DroneAcousticRenderState.AudioSnapshot snapshot =
				state.audioSnapshot();
		if (snapshot == null) {
			return BufferUtils.createByteBuffer(0);
		}
		boolean tracing = DopplerAudioChunkTrace.active();
		var before = tracing
				? synthesizer.oscillatorDiagnostics()
				: java.util.List
						.<PhaseContinuousSynthesizer
								.OscillatorDiagnostic>of();
		int smoothingCheckpointSamples = tracing
				? Math.min(
						sampleCount,
						synthesizer.frequencySmoothingSamples()
				)
				: sampleCount;
		synthesizer.render(
				snapshot.emission(),
				layer,
				sampleBuffer,
				0,
				smoothingCheckpointSamples
		);
		var smoothingCheckpoint = tracing
				? synthesizer.oscillatorDiagnostics()
				: java.util.List
						.<PhaseContinuousSynthesizer
								.OscillatorDiagnostic>of();
		if (smoothingCheckpointSamples < sampleCount) {
			synthesizer.render(
					snapshot.emission(),
					layer,
					sampleBuffer,
					smoothingCheckpointSamples,
					sampleCount - smoothingCheckpointSamples
			);
		}

		ByteBuffer output = BufferUtils.createByteBuffer(byteCount)
				.order(ByteOrder.LITTLE_ENDIAN);
		for (int index = 0; index < sampleCount; index++) {
			float sample = sampleBuffer[index];
			int pcm = Math.round(Math.max(-1.0f, Math.min(1.0f, sample)) * 32767.0f);
			output.putShort((short) pcm);
		}
		output.flip();
		OpenAlStreamingQueueProbe.onPcmRead(
				this,
				layer,
				snapshot,
				output
		);
		if (tracing) {
			DopplerAudioChunkTrace.record(
					layer,
					snapshot,
					synthesizer.frequencySmoothingSamples(),
					before,
					smoothingCheckpointSamples,
					smoothingCheckpoint,
					synthesizer.oscillatorDiagnostics(),
					output
			);
		}
		return output;
	}

	@Override
	public void close() {
		closed = true;
		sampleBuffer = new float[0];
	}
}
