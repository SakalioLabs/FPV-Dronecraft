package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer;
import com.tenicana.dronecraft.acoustics.PreparedPhaseContinuousSynthesizer;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class ProceduralDroneAudioStream implements InternalDistanceAudioStream, CallbackPcmStream {
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
	private final boolean internalDistanceOwnership;
	private final int noiseSeed;
	private final SingleOwnerRenderGate callbackGate = new SingleOwnerRenderGate();
	private PreparedPhaseContinuousSynthesizer callbackSynthesizer;
	private float[] callbackSamples;
	private volatile boolean callbackPrepared;
	private volatile boolean callbackControlsEnrolled;
	private volatile DroneAcousticRenderState.AudioSnapshot previousCallbackControl;
	private boolean queuedReadStarted;
	private volatile boolean closed;
	private float[] sampleBuffer = new float[0];

	ProceduralDroneAudioStream(
			DroneAcousticRenderState state,
			PhaseContinuousSynthesizer.Layer layer,
			int noiseSeed
	) {
		this(state, layer, noiseSeed, false);
	}

	ProceduralDroneAudioStream(
			DroneAcousticRenderState state,
			PhaseContinuousSynthesizer.Layer layer,
			int noiseSeed,
			boolean internalDistanceOwnership
	) {
		this.state = state;
		this.layer = layer;
		this.internalDistanceOwnership = internalDistanceOwnership;
		this.noiseSeed = noiseSeed;
		if (internalDistanceOwnership) state.registerDistanceStream(layer, this);
		this.synthesizer = new PhaseContinuousSynthesizer(
				DroneAcousticRenderState.SAMPLE_RATE,
				noiseSeed
		);
	}

	@Override public boolean internalDistanceOwnership() {
		return internalDistanceOwnership;
	}

	@Override public DroneAcousticRenderState distanceOwnershipMonitor() { return state; }

	@Override public boolean distanceOwnerMayConfigure() {
		return !closed && state.distanceStreamMayConfigure(layer, this);
	}

	@Override public boolean distanceOwnerVerified() {
		return !closed && state.distanceStreamVerified(layer, this);
	}

	@Override public boolean nativeDistanceOwnerConfigured(boolean verified) {
		return !closed && state.configureDistanceStream(layer, this, verified);
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
		double calibration = layer == PhaseContinuousSynthesizer.Layer.MOTOR
				? snapshot.calibration().motorPlaybackAmplitude()
				: snapshot.calibration().propellerPlaybackAmplitude();
		for (int index = 0; index < sampleCount; index++) {
			float sample = (float) (sampleBuffer[index] * calibration);
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
	public synchronized void close() {
		closed = true;
		callbackGate.closeForControl();
		synchronized (state) {
			if (internalDistanceOwnership) state.closeDistanceStream(layer, this);
		}
		sampleBuffer = new float[0];
	}

	@Override public synchronized boolean prepareCallback() {
		// Native dry voices use the two separately calibrated production layers.
		if (closed || queuedReadStarted || layer == PhaseContinuousSynthesizer.Layer.FULL) return false;
		if (callbackPrepared) return true;
		callbackSynthesizer = new PreparedPhaseContinuousSynthesizer(
				DroneAcousticRenderState.SAMPLE_RATE, noiseSeed, 128);
		callbackSamples = new float[MAXIMUM_CALLBACK_FRAMES];
		callbackPrepared = true;
		return true;
	}

	@Override public int readCallback(long samples, int requestedBytes) {
		if (closed || !callbackPrepared || !Pcm16NativeWriter.validRequest(samples, requestedBytes)
				|| !callbackGate.tryEnter()) return 0;
		try {
			int frames = requestedBytes / Short.BYTES;
			DroneAcousticRenderState.AudioSnapshot snapshot = state.callbackAudioSnapshot(this, layer);
			if (snapshot == null) {
				org.lwjgl.system.MemoryUtil.memSet(samples, 0, requestedBytes);
				return requestedBytes;
			}
			var prepared = layer == PhaseContinuousSynthesizer.Layer.MOTOR
					? snapshot.preparedMotor() : snapshot.preparedPropeller();
			if (!callbackSynthesizer.render(prepared, callbackSamples, 0, frames)) return 0;
			double calibration = layer == PhaseContinuousSynthesizer.Layer.MOTOR
					? snapshot.calibration().motorPlaybackAmplitude() : snapshot.calibration().propellerPlaybackAmplitude();
			Pcm16NativeWriter.write(callbackSamples, frames, calibration, samples);
			return requestedBytes;
		} finally { callbackGate.leave(); }
	}

	@Override public Object callbackControlOwner() { return state; }
	@Override public int callbackControlLayer() { return layer.ordinal(); }
	@Override public void enrollCallbackControls(Object snapshot) {
		previousCallbackControl = (DroneAcousticRenderState.AudioSnapshot) snapshot;
		callbackControlsEnrolled = true;
	}
	@Override public boolean callbackControlsEnrolled() { return callbackControlsEnrolled; }
	@Override public Object previousCallbackControl() { return previousCallbackControl; }
}
