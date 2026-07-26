package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;
import com.tenicana.dronecraft.acoustics.reverb.ListenerSharedFdn;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

final class ListenerReverbAudioStream implements AudioStream {
	private static final AudioFormat FORMAT = new AudioFormat(
			DroneAcousticRenderState.SAMPLE_RATE,
			16,
			1,
			true,
			false
	);

	private final ListenerReverbState state;
	private final ListenerReverbHistoryProducer.ActiveSession session;
	private final ListenerSharedFdn fdn =
			new ListenerSharedFdn(DroneAcousticRenderState.SAMPLE_RATE);
	private float[] mixedInput = new float[0];
	private float[] wetOutput = new float[0];
	private long environmentGeneration = Long.MIN_VALUE;
	private final int prerollFrames;
	private final long prerollNanos;
	private volatile boolean closed;

	ListenerReverbAudioStream(ListenerReverbState state) {
		this.state = state;
		this.session = state.acquireWetSession();
		ListenerReverbState.Snapshot snapshot = state.snapshot();
		FdnEnvironmentMapper.Controls environment = snapshot.environment();
		fdn.configure(
				environment.rt60Seconds(),
				environment.wetGain(),
				0.0
		);
		environmentGeneration = environment.snapshotGeneration();
		float[] history = session.history();
		prerollFrames = history.length;
		long started = System.nanoTime();
		if (history.length > 0) {
			float[] discardedWet = new float[history.length];
			fdn.process(history, discardedWet, 0, history.length);
		}
		prerollNanos = System.nanoTime() - started;
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
		ensureCapacity(sampleCount);

		ListenerReverbState.Snapshot snapshot = state.snapshot();
		FdnEnvironmentMapper.Controls environment = snapshot.environment();
		if (environment.snapshotGeneration() != environmentGeneration) {
			fdn.configure(
					environment.rt60Seconds(),
					environment.wetGain(),
					environmentGeneration == Long.MIN_VALUE
							? 0.0
							: environment.transitionSeconds()
			);
			environmentGeneration = environment.snapshotGeneration();
		}

		boolean ownsWetPath = session.render(
				snapshot.sources(),
				mixedInput,
				sampleCount
		);
		if (ownsWetPath) {
			fdn.process(mixedInput, wetOutput, 0, sampleCount);
		} else {
			java.util.Arrays.fill(
					wetOutput,
					0,
					sampleCount,
					0.0F
			);
		}

		ByteBuffer output = BufferUtils.createByteBuffer(byteCount);
		for (int index = 0; index < sampleCount; index++) {
			float sample = wetOutput[index];
			int pcm = Math.round(
					Math.max(-1.0F, Math.min(1.0F, sample)) * 32767.0F
			);
			output.putShort((short) pcm);
		}
		output.flip();
		ListenerReverbQueueHandoffProbe.onPcmRead(
				this,
				output,
				prerollFrames,
				prerollNanos
		);
		return output;
	}

	@Override
	public void close() {
		closed = true;
		session.close();
		mixedInput = new float[0];
		wetOutput = new float[0];
		fdn.reset();
	}

	int synthesizerCount() {
		return closed
				? 0
				: state.historyDiagnostics().synthesizerCount();
	}

	int bufferCapacitySamples() {
		return mixedInput.length;
	}

	boolean closed() {
		return closed;
	}

	int prerollFrames() {
		return prerollFrames;
	}

	long prerollNanos() {
		return prerollNanos;
	}

	private void ensureCapacity(int sampleCount) {
		if (mixedInput.length >= sampleCount) {
			return;
		}
		mixedInput = new float[sampleCount];
		wetOutput = new float[sampleCount];
	}
}
