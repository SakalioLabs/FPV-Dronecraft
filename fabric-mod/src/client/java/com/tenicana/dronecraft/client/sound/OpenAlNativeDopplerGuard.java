package com.tenicana.dronecraft.client.sound;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;

import com.mojang.blaze3d.audio.Channel;
import com.tenicana.dronecraft.client.mixin.ChannelHandleChannelAccessor;
import com.tenicana.dronecraft.client.mixin.ChannelSourceAccessor;
import com.tenicana.dronecraft.client.mixin.SoundEngineChannelsAccessor;
import com.tenicana.dronecraft.client.mixin.SoundEngineExecutorAccessor;
import com.tenicana.dronecraft.client.mixin.SoundManagerEngineAccessor;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;
import net.minecraft.client.sounds.SoundManager;

/**
 * Prevents OpenAL from Doppler-resampling procedural drone streams.
 *
 * <p>Dronecraft applies Doppler only to synthesized tonal orders. OpenAL's
 * native Doppler changes playback rate for the complete mono stream, including
 * broadband noise. The guard assigns each procedural drone source the current
 * listener velocity, making their relative native Doppler ratio exactly one
 * without changing Doppler behavior for any other sound source.
 */
final class OpenAlNativeDopplerGuard {
	private static final double EPSILON = 1.0e-6;

	private final AtomicReference<DesiredState> desired =
			new AtomicReference<>();
	private final AtomicBoolean drainScheduled = new AtomicBoolean();
	private final AtomicReference<Diagnostics> diagnostics =
			new AtomicReference<>(Diagnostics.inactive());

	// Sound-thread-owned, context-local state.
	private long activeContext;
	private final Map<Integer, float[]> originalVelocities = new HashMap<>();
	private int contextRebuilds;
	private int restoredSources;

	void tick(
			SoundManager soundManager,
			List<DroneLoopSoundInstance> sources
	) {
		Objects.requireNonNull(soundManager, "soundManager");
		Objects.requireNonNull(sources, "sources");
		boolean enabled = Boolean.parseBoolean(
				System.getProperty("fpvdrone.proceduralAudio", "true")
		);
		request(
				soundManager,
				enabled
						? enabledState(soundManager, sources)
						: DesiredState.disabled()
		);
	}

	void stop(SoundManager soundManager) {
		Objects.requireNonNull(soundManager, "soundManager");
		request(soundManager, DesiredState.disabled());
	}

	Diagnostics diagnostics() {
		return diagnostics.get();
	}

	private DesiredState enabledState(
			SoundManager soundManager,
			List<DroneLoopSoundInstance> sources
	) {
		SoundEngine soundEngine =
				((SoundManagerEngineAccessor) soundManager)
						.fpvdrone$getSoundEngine();
		Map<SoundInstance, ChannelAccess.ChannelHandle> channels =
				((SoundEngineChannelsAccessor) soundEngine)
						.fpvdrone$getInstanceToChannel();
		List<Target> targets = new ArrayList<>();
		for (DroneLoopSoundInstance instance : sources) {
			ChannelAccess.ChannelHandle handle = channels.get(instance);
			if (handle != null && !handle.isStopped()) {
				targets.add(new Target(
						handle,
						instance.internalDopplerFrequencyRatio()
				));
			}
		}
		return new DesiredState(true, List.copyOf(targets));
	}

	private void request(
			SoundManager soundManager,
			DesiredState next
	) {
		desired.set(next);
		if (!drainScheduled.compareAndSet(false, true)) {
			return;
		}
		SoundEngine soundEngine =
				((SoundManagerEngineAccessor) soundManager)
						.fpvdrone$getSoundEngine();
		SoundEngineExecutor executor =
				((SoundEngineExecutorAccessor) soundEngine)
						.fpvdrone$getExecutor();
		executor.schedule(this::drainOnSoundThread);
	}

	private void drainOnSoundThread() {
		try {
			DesiredState next;
			while ((next = desired.getAndSet(null)) != null) {
				applyOnSoundThread(next);
			}
		} finally {
			drainScheduled.set(false);
			if (desired.get() != null
					&& drainScheduled.compareAndSet(false, true)) {
				drainOnSoundThread();
			}
		}
	}

	private void applyOnSoundThread(DesiredState next) {
		long context = ALC10.alcGetCurrentContext();
		if (context != activeContext) {
			activeContext = context;
			originalVelocities.clear();
			contextRebuilds++;
		}
		if (context == 0L) {
			diagnostics.set(Diagnostics.inactive(contextRebuilds));
			return;
		}
		if (!next.enabled()) {
			restoreAll();
			diagnostics.set(Diagnostics.inactive(contextRebuilds));
			return;
		}

		clearErrors();
		float[] listenerPosition = new float[3];
		float[] listenerVelocity = new float[3];
		AL10.alGetListenerfv(AL10.AL_POSITION, listenerPosition);
		AL10.alGetListenerfv(AL10.AL_VELOCITY, listenerVelocity);
		double dopplerFactor = AL10.alGetFloat(AL10.AL_DOPPLER_FACTOR);
		double speedOfSound = AL10.alGetFloat(AL11.AL_SPEED_OF_SOUND);
		int distanceModel = AL10.alGetInteger(AL10.AL_DISTANCE_MODEL);

		Set<Integer> activeSources = new HashSet<>();
		double maxVelocityDifference = 0.0;
		double maxNativeDeviationBefore = 0.0;
		double maxNativeDeviationAfter = 0.0;
		double minimumInternalRatio = Double.POSITIVE_INFINITY;
		double maximumInternalRatio = Double.NEGATIVE_INFINITY;
		double minimumPitch = Double.POSITIVE_INFINITY;
		double maximumPitch = Double.NEGATIVE_INFINITY;
		int internallyShiftedSources = 0;
		for (Target target : next.targets()) {
			if (target.handle().isStopped()) {
				continue;
			}
			Channel channel =
					((ChannelHandleChannelAccessor) target.handle())
							.fpvdrone$getChannel();
			if (channel == null) {
				continue;
			}
			int source =
					((ChannelSourceAccessor) channel).fpvdrone$getSource();
			if (source == 0 || !AL10.alIsSource(source)) {
				continue;
			}
			activeSources.add(source);
			float[] sourcePosition = new float[3];
			float[] sourceVelocity = new float[3];
			AL10.alGetSourcefv(source, AL10.AL_POSITION, sourcePosition);
			AL10.alGetSourcefv(source, AL10.AL_VELOCITY, sourceVelocity);
			originalVelocities.putIfAbsent(
					source,
					sourceVelocity.clone()
			);
			double nativeBefore = nativeDopplerRatio(
					speedOfSound,
					dopplerFactor,
					sourcePosition,
					sourceVelocity,
					listenerPosition,
					listenerVelocity
			);
			AL10.alSourcefv(
					source,
					AL10.AL_VELOCITY,
					listenerVelocity
			);
			float[] guardedVelocity = new float[3];
			AL10.alGetSourcefv(
					source,
					AL10.AL_VELOCITY,
					guardedVelocity
			);
			double nativeAfter = nativeDopplerRatio(
					speedOfSound,
					dopplerFactor,
					sourcePosition,
					guardedVelocity,
					listenerPosition,
					listenerVelocity
			);
			double pitch = AL10.alGetSourcef(source, AL10.AL_PITCH);
			maxVelocityDifference = Math.max(
					maxVelocityDifference,
					distance(guardedVelocity, listenerVelocity)
			);
			maxNativeDeviationBefore = Math.max(
					maxNativeDeviationBefore,
					Math.abs(nativeBefore - 1.0)
			);
			maxNativeDeviationAfter = Math.max(
					maxNativeDeviationAfter,
					Math.abs(nativeAfter - 1.0)
			);
			minimumInternalRatio = Math.min(
					minimumInternalRatio,
					target.internalDopplerRatio()
			);
			maximumInternalRatio = Math.max(
					maximumInternalRatio,
					target.internalDopplerRatio()
			);
			if (Math.abs(target.internalDopplerRatio() - 1.0)
					> EPSILON) {
				internallyShiftedSources++;
			}
			minimumPitch = Math.min(minimumPitch, pitch);
			maximumPitch = Math.max(maximumPitch, pitch);
		}
		restoreStale(activeSources);
		int error = AL10.alGetError();
		int sourceCount = activeSources.size();
		diagnostics.set(new Diagnostics(
				true,
				error == AL10.AL_NO_ERROR,
				sourceCount,
				contextRebuilds,
				restoredSources,
				dopplerFactor,
				speedOfSound,
				distanceModel,
				magnitude(listenerVelocity),
				maxVelocityDifference,
				maxNativeDeviationBefore,
				maxNativeDeviationAfter,
				sourceCount == 0 ? 1.0 : minimumInternalRatio,
				sourceCount == 0 ? 1.0 : maximumInternalRatio,
				internallyShiftedSources,
				sourceCount == 0 ? 1.0 : minimumPitch,
				sourceCount == 0 ? 1.0 : maximumPitch,
				error
		));
	}

	private void restoreStale(Set<Integer> activeSources) {
		List<Integer> stale = originalVelocities.keySet().stream()
				.filter(source -> !activeSources.contains(source))
				.toList();
		for (int source : stale) {
			restore(source);
		}
	}

	private void restoreAll() {
		for (int source : List.copyOf(originalVelocities.keySet())) {
			restore(source);
		}
	}

	private void restore(int source) {
		float[] original = originalVelocities.remove(source);
		if (original != null && AL10.alIsSource(source)) {
			AL10.alSourcefv(source, AL10.AL_VELOCITY, original);
			restoredSources++;
		}
	}

	private static void clearErrors() {
		for (int attempt = 0; attempt < 16; attempt++) {
			if (AL10.alGetError() == AL10.AL_NO_ERROR) {
				return;
			}
		}
	}

	static double nativeDopplerRatio(
			double speedOfSound,
			double dopplerFactor,
			float[] sourcePosition,
			float[] sourceVelocity,
			float[] listenerPosition,
			float[] listenerVelocity
	) {
		if (!Double.isFinite(speedOfSound) || speedOfSound <= 0.0
				|| !Double.isFinite(dopplerFactor)
				|| dopplerFactor < 0.0) {
			throw new IllegalArgumentException(
					"OpenAL Doppler globals are invalid"
			);
		}
		requireVector(sourcePosition, "sourcePosition");
		requireVector(sourceVelocity, "sourceVelocity");
		requireVector(listenerPosition, "listenerPosition");
		requireVector(listenerVelocity, "listenerVelocity");
		if (dopplerFactor == 0.0) {
			return 1.0;
		}
		double dx = listenerPosition[0] - sourcePosition[0];
		double dy = listenerPosition[1] - sourcePosition[1];
		double dz = listenerPosition[2] - sourcePosition[2];
		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (length <= EPSILON) {
			return 1.0;
		}
		dx /= length;
		dy /= length;
		dz /= length;
		double listenerRadial = dx * listenerVelocity[0]
				+ dy * listenerVelocity[1]
				+ dz * listenerVelocity[2];
		double sourceRadial = dx * sourceVelocity[0]
				+ dy * sourceVelocity[1]
				+ dz * sourceVelocity[2];
		double maximum = speedOfSound / dopplerFactor;
		listenerRadial = Math.min(listenerRadial, maximum);
		sourceRadial = Math.min(sourceRadial, maximum);
		double numerator =
				speedOfSound - dopplerFactor * listenerRadial;
		double denominator =
				speedOfSound - dopplerFactor * sourceRadial;
		if (numerator <= 0.0 || denominator <= 0.0) {
			throw new IllegalArgumentException(
					"OpenAL Doppler ratio is outside stable domain"
			);
		}
		return numerator / denominator;
	}

	private static void requireVector(float[] value, String name) {
		if (value == null || value.length < 3) {
			throw new IllegalArgumentException(name + " requires 3 values");
		}
		for (int index = 0; index < 3; index++) {
			if (!Float.isFinite(value[index])) {
				throw new IllegalArgumentException(name + " must be finite");
			}
		}
	}

	private static double magnitude(float[] value) {
		return Math.sqrt(
				value[0] * value[0]
						+ value[1] * value[1]
						+ value[2] * value[2]
		);
	}

	private static double distance(float[] left, float[] right) {
		double dx = left[0] - right[0];
		double dy = left[1] - right[1];
		double dz = left[2] - right[2];
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	record Diagnostics(
			boolean enabled,
			boolean operational,
			int guardedSources,
			int contextRebuilds,
			int restoredSources,
			double dopplerFactor,
			double speedOfSoundMetersPerSecond,
			int distanceModel,
			double listenerVelocityMagnitude,
			double maximumGuardVelocityDifference,
			double maximumNativeRatioDeviationBefore,
			double maximumNativeRatioDeviationAfter,
			double minimumInternalDopplerRatio,
			double maximumInternalDopplerRatio,
			int internallyShiftedSources,
			double minimumSourcePitch,
			double maximumSourcePitch,
			int alErrorCode
	) {
		private static Diagnostics inactive() {
			return inactive(0);
		}

		private static Diagnostics inactive(int contextRebuilds) {
			return new Diagnostics(
					false,
					false,
					0,
					contextRebuilds,
					0,
					0.0,
					0.0,
					AL10.AL_NONE,
					0.0,
					0.0,
					0.0,
					0.0,
					1.0,
					1.0,
					0,
					1.0,
					1.0,
					AL10.AL_NO_ERROR
			);
		}

		boolean nativeDopplerNeutralized() {
			return enabled
					&& operational
					&& guardedSources > 0
					&& maximumGuardVelocityDifference <= EPSILON
					&& maximumNativeRatioDeviationAfter <= EPSILON;
		}
	}

	private record Target(
			ChannelAccess.ChannelHandle handle,
			double internalDopplerRatio
	) {
		private Target {
			Objects.requireNonNull(handle, "handle");
			if (!Double.isFinite(internalDopplerRatio)
					|| internalDopplerRatio <= 0.0) {
				throw new IllegalArgumentException(
						"internal Doppler ratio must be positive"
				);
			}
		}
	}

	private record DesiredState(
			boolean enabled,
			List<Target> targets
	) {
		private DesiredState {
			Objects.requireNonNull(targets, "targets");
		}

		private static DesiredState disabled() {
			return new DesiredState(false, List.of());
		}
	}
}
