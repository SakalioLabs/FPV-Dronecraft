package com.tenicana.dronecraft.client.sound;

import com.mojang.blaze3d.audio.ListenerTransform;
import com.tenicana.dronecraft.acoustics.AcousticEmissionFrame;
import com.tenicana.dronecraft.acoustics.AcousticListenerFrame;
import com.tenicana.dronecraft.acoustics.AcousticPropagation;
import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;
import net.minecraft.client.sounds.ChannelAccess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable controls committed together by the audio owner. */
public final class DroneAudioControlFrame {
	private static final int MAXIMUM_SOURCES = 6;

	private final long generation;
	private final long version;
	private final ListenerTransform listenerTransform;
	private final AcousticListenerFrame listener;
	private final List<SourceControl> sources;
	private final ListenerReverbState wetState;
	private final FdnEnvironmentMapper.Controls environment;
	private final ListenerReverbSoundInstance wetSound;
	private final ChannelAccess.ChannelHandle wetHandle;
	private final Object wetStream;
	private final float wetNativeGain;
	private final float wetNativePitch;
	private final ListenerReverbState.Snapshot wetSnapshot;

	public DroneAudioControlFrame(long generation, long version,
			ListenerTransform listenerTransform, AcousticListenerFrame listener,
			List<SourceControl> sources, ListenerReverbState wetState,
			FdnEnvironmentMapper.Controls environment, ListenerReverbSoundInstance wetSound,
			ChannelAccess.ChannelHandle wetHandle, Object wetStream,
			float wetNativeGain, float wetNativePitch) {
		if (generation < 0L || version < 0L) {
			throw new IllegalArgumentException("generation and version must be non-negative");
		}
		this.generation = generation;
		this.version = version;
		this.listenerTransform = Objects.requireNonNull(listenerTransform, "listenerTransform");
		this.listener = Objects.requireNonNull(listener, "listener");
		this.wetState = Objects.requireNonNull(wetState, "wetState");
		this.environment = Objects.requireNonNull(environment, "environment");
		this.wetSound = wetSound;
		this.wetHandle = wetHandle;
		this.wetStream = wetStream;
		requireNonnegativeFinite(wetNativeGain, "wetNativeGain");
		requirePitch(wetNativePitch, "wetNativePitch");
		this.wetNativeGain = wetNativeGain;
		this.wetNativePitch = wetNativePitch;

		List<SourceControl> copy = List.copyOf(Objects.requireNonNull(sources, "sources"));
		if (copy.size() > MAXIMUM_SOURCES) {
			throw new IllegalArgumentException("at most six source controls are supported");
		}
		Set<Integer> entityIds = new java.util.HashSet<>();
		Set<DroneAcousticRenderState> states = Collections.newSetFromMap(new IdentityHashMap<>());
		List<ListenerReverbState.Source> wetSources = new ArrayList<>(copy.size());
		for (SourceControl source : copy) {
			Objects.requireNonNull(source, "source control");
			if (!entityIds.add(source.entityId())) {
				throw new IllegalArgumentException("duplicate entity id " + source.entityId());
			}
			if (!states.add(source.state())) {
				throw new IllegalArgumentException("duplicate acoustic state identity");
			}
			if (source.audioSnapshot().source().entityId() != source.entityId()) {
				throw new IllegalArgumentException("audio snapshot entity id differs from source control");
			}
			if (source.audioSnapshot().listener() != listener) {
				throw new IllegalArgumentException("source snapshot was captured for a different listener frame");
			}
			double distance = source.audioSnapshot().source().positionMeters()
					.subtract(listener.positionMeters()).length();
			double distanceGain = AcousticPropagation.pressureDistanceGain(distance);
			var calibration = source.audioSnapshot().calibration();
			wetSources.add(new ListenerReverbState.Source(source.entityId(),
					source.unoccludedEmission(),
					distanceGain * source.motorEffectiveEnvelope() * calibration.motorPlaybackAmplitude(),
					distanceGain * source.propellerEffectiveEnvelope() * calibration.propellerPlaybackAmplitude()));
		}
		this.sources = copy;
		this.wetSnapshot = new ListenerReverbState.Snapshot(wetSources, environment);
	}

	public long generation() { return generation; }
	public long version() { return version; }
	public ListenerTransform listenerTransform() { return listenerTransform; }
	public AcousticListenerFrame listener() { return listener; }
	public List<SourceControl> sources() { return sources; }
	public ListenerReverbState wetState() { return wetState; }
	public FdnEnvironmentMapper.Controls environment() { return environment; }
	public ListenerReverbSoundInstance wetSound() { return wetSound; }
	public ChannelAccess.ChannelHandle wetHandle() { return wetHandle; }
	public Object wetStream() { return wetStream; }
	public float wetNativeGain() { return wetNativeGain; }
	public float wetNativePitch() { return wetNativePitch; }
	public ListenerReverbState.Snapshot wetSnapshot() { return wetSnapshot; }

	DroneAcousticRenderState.AudioSnapshot drySnapshot(DroneAcousticRenderState state,
			com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer.Layer layer, Object stream) {
		for (int index = 0; index < sources.size(); index++) {
			SourceControl source = sources.get(index);
			if (source.state() != state) continue;
			return switch (layer) {
				case MOTOR -> source.motorStream() == stream ? source.audioSnapshot() : null;
				case PROPELLER -> source.propellerStream() == stream ? source.audioSnapshot() : null;
			default -> null;
			};
		}
		return null;
	}

	ListenerReverbState.Snapshot wetSnapshotFor(ListenerReverbState state, Object stream) {
		return wetState == state && wetStream == stream ? wetSnapshot : null;
	}

	private static void requireNonnegativeFinite(double value, String name) {
		if (!Double.isFinite(value) || value < 0.0) {
			throw new IllegalArgumentException(name + " must be finite and non-negative");
		}
	}

	private static void requirePitch(float value, String name) {
		if (!Float.isFinite(value) || value < 0.5F || value > 2.0F) {
			throw new IllegalArgumentException(name + " must be finite and in [0.5, 2]");
		}
	}

	public record SourceControl(int entityId, DroneAcousticRenderState state,
			DroneAcousticRenderState.AudioSnapshot audioSnapshot,
			AcousticEmissionFrame unoccludedEmission,
			DroneLoopSoundInstance motorSound, DroneLoopSoundInstance propellerSound,
			ChannelAccess.ChannelHandle motorHandle, ChannelAccess.ChannelHandle propellerHandle,
			Object motorStream, Object propellerStream,
			double motorEnvelope, double propellerEnvelope,
			float motorNativeGain, float propellerNativeGain,
			float motorNativePitch, float propellerNativePitch) {
		public SourceControl {
			Objects.requireNonNull(state, "state");
			Objects.requireNonNull(audioSnapshot, "audioSnapshot");
			Objects.requireNonNull(unoccludedEmission, "unoccludedEmission");
			requireNonnegativeFinite(motorEnvelope, "motorEnvelope");
			requireNonnegativeFinite(propellerEnvelope, "propellerEnvelope");
			requireNonnegativeFinite(motorNativeGain, "motorNativeGain");
			requireNonnegativeFinite(propellerNativeGain, "propellerNativeGain");
			requirePitch(motorNativePitch, "motorNativePitch");
			requirePitch(propellerNativePitch, "propellerNativePitch");
		}

		public double motorEffectiveEnvelope() { return Math.min(1.0, motorEnvelope); }
		public double propellerEffectiveEnvelope() { return Math.min(1.0, propellerEnvelope); }
	}
}
