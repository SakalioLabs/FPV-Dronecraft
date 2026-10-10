package com.tenicana.dronecraft.client.sound;

import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

final class ListenerReverbAudioStream implements CallbackPcmStream {
	private static final AudioFormat FORMAT = new AudioFormat(
			DroneAcousticRenderState.SAMPLE_RATE,
			16,
			1,
			true,
			false
	);

	private final ListenerReverbState state;
	private final ListenerReverbHistoryProducer.ActiveSession session;
	private float[] mixedInput = new float[0];
	private float[] wetOutput = new float[0];
	private final int prerollFrames;
	private final long prerollNanos;
	private volatile boolean closed;
	private final SingleOwnerRenderGate callbackGate = new SingleOwnerRenderGate();
	private float[] callbackMixed, callbackWet;
	private volatile boolean callbackPrepared;
	private volatile boolean callbackControlsEnrolled;
	private volatile ListenerReverbState.Snapshot previousCallbackControl;
	private boolean queuedReadStarted;

	ListenerReverbAudioStream(ListenerReverbState state) {
		this.state = state;
		this.session = state.acquireWetSession();
		state.registerCallbackStream(this);
		prerollFrames = session.prerollFrames();
		prerollNanos = session.prerollNanos();
	}

	@Override
	public AudioFormat getFormat() {
		return FORMAT;
	}

	@Override
	public ByteBuffer read(int requestedBytes) {
		if (closed || callbackPrepared || requestedBytes <= 0) {
			return BufferUtils.createByteBuffer(0);
		}
		queuedReadStarted = true;
		int byteCount = requestedBytes & ~1;
		int sampleCount = byteCount / Short.BYTES;
		// Capture references before lifecycle close clears the stream fields.
		float[] mixed = mixedInput, wet = wetOutput;
		if (mixed.length < sampleCount || wet.length < sampleCount) {
			mixed = new float[sampleCount]; wet = new float[sampleCount];
		}
		mixedInput = mixed; wetOutput = wet;
		if (closed) {
			mixedInput = new float[0]; wetOutput = new float[0];
			return BufferUtils.createByteBuffer(0);
		}
		session.renderWet(state.snapshot(), mixed, wet, sampleCount);

		ByteBuffer output = BufferUtils.createByteBuffer(byteCount);
		for (int index = 0; index < sampleCount; index++) {
			float sample = wet[index];
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
		if (closed) { mixedInput = new float[0]; wetOutput = new float[0]; }
		return output;
	}

	@Override
	public synchronized void close() {
		closed = true;
		state.closeCallbackStream(this);
		callbackGate.closeForControl();
		session.close();
		mixedInput = new float[0];
		wetOutput = new float[0];
	}

	@Override public synchronized boolean prepareCallback() {
		if (closed || queuedReadStarted) return false;
		if (callbackPrepared) return true;
		callbackMixed = new float[MAXIMUM_CALLBACK_FRAMES];
		callbackWet = new float[MAXIMUM_CALLBACK_FRAMES];
		callbackPrepared = true;
		return true;
	}

	@Override public int readCallback(long samples, int requestedBytes) {
		if (closed || !callbackPrepared || !Pcm16NativeWriter.validRequest(samples, requestedBytes)
				|| !callbackGate.tryEnter()) return 0;
		try {
			int frames = requestedBytes / Short.BYTES;
			// Lifecycle transitions reject a block with zeros, as the queued path does.
			// A temporary producer gate closure must not become native end-of-stream.
			ListenerReverbState.Snapshot snapshot = state.callbackSnapshot(this);
			session.renderWet(snapshot, callbackMixed, callbackWet, frames);
			Pcm16NativeWriter.write(callbackWet, frames, 1.0, samples);
			return requestedBytes;
		} finally { callbackGate.leave(); }
	}

	@Override public Object callbackControlOwner() { return state; }
	@Override public int callbackControlLayer() { return 2; }
	@Override public void enrollCallbackControls(Object snapshot) {
		previousCallbackControl = (ListenerReverbState.Snapshot) snapshot;
		callbackControlsEnrolled = true;
	}
	@Override public boolean callbackControlsEnrolled() { return callbackControlsEnrolled; }
	@Override public Object previousCallbackControl() { return previousCallbackControl; }

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

}
