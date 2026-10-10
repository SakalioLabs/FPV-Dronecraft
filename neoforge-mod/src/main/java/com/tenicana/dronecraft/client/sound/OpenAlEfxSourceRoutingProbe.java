package com.tenicana.dronecraft.client.sound;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;

import com.tenicana.dronecraft.client.mixin.ChannelSourceAccessor;
import com.tenicana.dronecraft.client.mixin.SoundEngineChannelsAccessor;
import com.tenicana.dronecraft.client.mixin.SoundManagerEngineAccessor;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;

/**
 * Exercises EFX routing on one already-playing Dronecraft channel, then
 * restores the vanilla routing before completing.
 */
final class OpenAlEfxSourceRoutingProbe {
	private OpenAlEfxSourceRoutingProbe() {
	}

	static boolean hasActiveDroneChannel(SoundManager soundManager) {
		return findHandle(soundManager) != null;
	}

	static CompletableFuture<Result> capture(SoundManager soundManager) {
		Objects.requireNonNull(soundManager, "soundManager");
		SelectedChannel selected = findHandle(soundManager);
		if (selected == null) {
			return CompletableFuture.completedFuture(
					Result.missingChannel()
			);
		}
		CompletableFuture<Result> result = new CompletableFuture<>();
		selected.handle().execute(channel -> {
			try {
				result.complete(exercise(
						((ChannelSourceAccessor) channel)
								.fpvdrone$getSource(),
						selected.instance().getClass().getSimpleName()
				));
			} catch (Throwable error) {
				result.completeExceptionally(error);
			}
		});
		return result;
	}

	private static SelectedChannel findHandle(SoundManager soundManager) {
		SoundEngine soundEngine =
				((SoundManagerEngineAccessor) soundManager)
						.fpvdrone$getSoundEngine();
		Map<SoundInstance, ChannelAccess.ChannelHandle> channels =
				((SoundEngineChannelsAccessor) soundEngine)
						.fpvdrone$getInstanceToChannel();
		for (Map.Entry<
				SoundInstance,
				ChannelAccess.ChannelHandle> entry : channels.entrySet()) {
			if (entry.getKey() instanceof DroneLoopSoundInstance
					&& !entry.getValue().isStopped()) {
				return new SelectedChannel(
						entry.getKey(),
						entry.getValue()
				);
			}
		}
		return null;
	}

	private static Result exercise(int source, String instanceType) {
		long context = ALC10.alcGetCurrentContext();
		long device = context == 0L
				? 0L
				: ALC10.alcGetContextsDevice(context);
		boolean efx = device != 0L
				&& ALC10.alcIsExtensionPresent(device, "ALC_EXT_EFX");
		if (!efx) {
			return new Result(
					true,
					instanceType,
					Thread.currentThread().getName(),
					false,
					false,
					false,
					false,
					AL10.AL_NO_ERROR
			);
		}

		clearAlErrors();
		int effect = 0;
		int filter = 0;
		int slot = 0;
		boolean resourcesCreated = false;
		boolean attached = false;
		boolean detached = false;
		boolean resourcesReleased = false;
		int error = AL10.AL_NO_ERROR;
		try {
			effect = EXTEfx.alGenEffects();
			EXTEfx.alEffecti(
					effect,
					EXTEfx.AL_EFFECT_TYPE,
					EXTEfx.AL_EFFECT_REVERB
			);
			filter = EXTEfx.alGenFilters();
			EXTEfx.alFilteri(
					filter,
					EXTEfx.AL_FILTER_TYPE,
					EXTEfx.AL_FILTER_LOWPASS
			);
			EXTEfx.alFilterf(
					filter,
					EXTEfx.AL_LOWPASS_GAIN,
					0.8f
			);
			EXTEfx.alFilterf(
					filter,
					EXTEfx.AL_LOWPASS_GAINHF,
					0.35f
			);
			slot = EXTEfx.alGenAuxiliaryEffectSlots();
			EXTEfx.alAuxiliaryEffectSloti(
					slot,
					EXTEfx.AL_EFFECTSLOT_EFFECT,
					effect
			);
			error = AL10.alGetError();
			resourcesCreated = error == AL10.AL_NO_ERROR;
			if (resourcesCreated) {
				AL10.alSourcei(
						source,
						EXTEfx.AL_DIRECT_FILTER,
						filter
				);
				AL11.alSource3i(
						source,
						EXTEfx.AL_AUXILIARY_SEND_FILTER,
						slot,
						0,
						filter
				);
				error = AL10.alGetError();
				attached = error == AL10.AL_NO_ERROR;
			}
		} finally {
			if (source != 0) {
				AL11.alSource3i(
						source,
						EXTEfx.AL_AUXILIARY_SEND_FILTER,
						EXTEfx.AL_EFFECTSLOT_NULL,
						0,
						EXTEfx.AL_FILTER_NULL
				);
				AL10.alSourcei(
						source,
						EXTEfx.AL_DIRECT_FILTER,
						EXTEfx.AL_FILTER_NULL
				);
				int detachError = AL10.alGetError();
				detached = detachError == AL10.AL_NO_ERROR;
				if (error == AL10.AL_NO_ERROR) {
					error = detachError;
				}
			}
			if (slot != 0) {
				EXTEfx.alDeleteAuxiliaryEffectSlots(slot);
			}
			if (filter != 0) {
				EXTEfx.alDeleteFilters(filter);
			}
			if (effect != 0) {
				EXTEfx.alDeleteEffects(effect);
			}
			int releaseError = AL10.alGetError();
			resourcesReleased = releaseError == AL10.AL_NO_ERROR;
			if (error == AL10.AL_NO_ERROR) {
				error = releaseError;
			}
		}
		return new Result(
				true,
				instanceType,
				Thread.currentThread().getName(),
				resourcesCreated,
				attached,
				detached,
				resourcesReleased,
				error
		);
	}

	private static void clearAlErrors() {
		for (int attempt = 0; attempt < 16; attempt++) {
			if (AL10.alGetError() == AL10.AL_NO_ERROR) {
				return;
			}
		}
	}

	record Result(
			boolean sourceFound,
			String instanceType,
			String threadName,
			boolean resourcesCreated,
			boolean filterAndSendAttached,
			boolean vanillaRoutingRestored,
			boolean resourcesReleased,
			int alErrorCode
	) {
		Result {
			Objects.requireNonNull(instanceType, "instanceType");
			Objects.requireNonNull(threadName, "threadName");
		}

		private static Result missingChannel() {
			return new Result(
					false,
					"",
					"",
					false,
					false,
					false,
					false,
					AL10.AL_NO_ERROR
			);
		}

		String toJson() {
			return String.format(
					Locale.ROOT,
					"{%n"
							+ "  \"schema_version\": 1,%n"
							+ "  \"status\": \"valid-routing-probe\",%n"
							+ "  \"thread_name\": \"%s\",%n"
							+ "  \"source_found\": %s,%n"
							+ "  \"instance_type\": \"%s\",%n"
							+ "  \"resources_created\": %s,%n"
							+ "  \"direct_filter_and_aux_send_attached\": %s,%n"
							+ "  \"vanilla_routing_restored\": %s,%n"
							+ "  \"resources_released\": %s,%n"
							+ "  \"al_error_code\": %d,%n"
							+ "  \"persistent_efx_enabled\": false,%n"
							+ "  \"release_calibrated\": false,%n"
							+ "  \"claim_boundary\": \"Transient routing probe "
							+ "on one active DroneLoopSoundInstance; no "
							+ "persistent EFX controller or audible "
							+ "calibration.\"%n"
							+ "}%n",
					escape(threadName),
					sourceFound,
					escape(instanceType),
					resourcesCreated,
					filterAndSendAttached,
					vanillaRoutingRestored,
					resourcesReleased,
					alErrorCode
			);
		}

		private static String escape(String value) {
			return value.replace("\\", "\\\\")
					.replace("\"", "\\\"");
		}
	}

	private record SelectedChannel(
			SoundInstance instance,
			ChannelAccess.ChannelHandle handle
	) {
	}
}
