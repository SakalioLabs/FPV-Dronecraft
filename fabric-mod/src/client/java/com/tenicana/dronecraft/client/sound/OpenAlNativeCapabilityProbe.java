package com.tenicana.dronecraft.client.sound;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;
import org.lwjgl.openal.SOFTHRTF;

import com.tenicana.dronecraft.client.mixin.SoundEngineExecutorAccessor;
import com.tenicana.dronecraft.client.mixin.SoundManagerEngineAccessor;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;
import net.minecraft.client.sounds.SoundManager;

/**
 * Probes the active Minecraft OpenAL context on its owning sound thread.
 *
 * <p>The resource exercise creates and immediately releases one standard
 * reverb effect, low-pass filter, and auxiliary slot. It never attaches them
 * to a Minecraft source and therefore does not change the default audio path.
 */
final class OpenAlNativeCapabilityProbe {
	private static final String EFX_EXTENSION = "ALC_EXT_EFX";
	private static final String HRTF_EXTENSION = "ALC_SOFT_HRTF";

	private OpenAlNativeCapabilityProbe() {
	}

	static CompletableFuture<Result> capture(SoundManager soundManager) {
		Objects.requireNonNull(soundManager, "soundManager");
		CompletableFuture<Result> result = new CompletableFuture<>();
		SoundEngine soundEngine =
				((SoundManagerEngineAccessor) soundManager)
						.fpvdrone$getSoundEngine();
		SoundEngineExecutor executor =
				((SoundEngineExecutorAccessor) soundEngine)
						.fpvdrone$getExecutor();
		executor.schedule(() -> {
			try {
				result.complete(captureOnSoundThread());
			} catch (Throwable error) {
				result.completeExceptionally(error);
			}
		});
		return result;
	}

	private static Result captureOnSoundThread() {
		long context = ALC10.alcGetCurrentContext();
		long device = context == 0L
				? 0L
				: ALC10.alcGetContextsDevice(context);
		boolean activeContext = context != 0L && device != 0L;
		String deviceName = activeContext
				? stringOrEmpty(
						ALC10.alcGetString(
								device,
								ALC10.ALC_DEVICE_SPECIFIER
						)
				)
				: "";
		String vendor = activeContext
				? stringOrEmpty(AL10.alGetString(AL10.AL_VENDOR))
				: "";
		String renderer = activeContext
				? stringOrEmpty(AL10.alGetString(AL10.AL_RENDERER))
				: "";
		String version = activeContext
				? stringOrEmpty(AL10.alGetString(AL10.AL_VERSION))
				: "";
		boolean efx = activeContext
				&& ALC10.alcIsExtensionPresent(device, EFX_EXTENSION);
		int maximumAuxiliarySends = efx
				? ALC10.alcGetInteger(
						device,
						EXTEfx.ALC_MAX_AUXILIARY_SENDS
				)
				: 0;
		boolean hrtfExtension = activeContext
				&& ALC10.alcIsExtensionPresent(device, HRTF_EXTENSION);
		boolean hrtfEnabled = hrtfExtension
				&& ALC10.alcGetInteger(device, SOFTHRTF.ALC_HRTF_SOFT)
						== ALC10.ALC_TRUE;
		ResourceExercise exercise = efx
				? exerciseEfxResources()
				: new ResourceExercise(false, false, AL10.AL_NO_ERROR);
		return new Result(
				Thread.currentThread().getName(),
				activeContext,
				deviceName,
				vendor,
				renderer,
				version,
				renderer.toLowerCase(Locale.ROOT).contains("openal soft"),
				efx,
				maximumAuxiliarySends,
				hrtfExtension,
				hrtfEnabled,
				exercise.created(),
				exercise.released(),
				exercise.errorCode()
		);
	}

	private static ResourceExercise exerciseEfxResources() {
		clearAlErrors();
		int effect = 0;
		int filter = 0;
		int slot = 0;
		boolean created = false;
		boolean released = false;
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
			slot = EXTEfx.alGenAuxiliaryEffectSlots();
			EXTEfx.alAuxiliaryEffectSloti(
					slot,
					EXTEfx.AL_EFFECTSLOT_EFFECT,
					effect
			);
			error = AL10.alGetError();
			created = error == AL10.AL_NO_ERROR;
		} finally {
			if (slot != 0) {
				EXTEfx.alDeleteAuxiliaryEffectSlots(slot);
			}
			if (filter != 0) {
				EXTEfx.alDeleteFilters(filter);
			}
			if (effect != 0) {
				EXTEfx.alDeleteEffects(effect);
			}
			int cleanupError = AL10.alGetError();
			released = cleanupError == AL10.AL_NO_ERROR;
			if (error == AL10.AL_NO_ERROR) {
				error = cleanupError;
			}
		}
		return new ResourceExercise(created, released, error);
	}

	private static void clearAlErrors() {
		for (int attempt = 0; attempt < 16; attempt++) {
			if (AL10.alGetError() == AL10.AL_NO_ERROR) {
				return;
			}
		}
	}

	private static String stringOrEmpty(String value) {
		return value == null ? "" : value;
	}

	record Result(
			String threadName,
			boolean activeContext,
			String deviceName,
			String vendor,
			String renderer,
			String version,
			boolean openAlSoft,
			boolean efxSupported,
			int maximumAuxiliarySends,
			boolean hrtfExtensionSupported,
			boolean hrtfEnabled,
			boolean efxResourcesCreated,
			boolean efxResourcesReleased,
			int alErrorCode
	) {
		Result {
			Objects.requireNonNull(threadName, "threadName");
			Objects.requireNonNull(deviceName, "deviceName");
			Objects.requireNonNull(vendor, "vendor");
			Objects.requireNonNull(renderer, "renderer");
			Objects.requireNonNull(version, "version");
		}

		String toJson() {
			return String.format(
					Locale.ROOT,
					"{%n"
							+ "  \"schema_version\": 1,%n"
							+ "  \"status\": \"valid-capability-probe\",%n"
							+ "  \"thread_name\": \"%s\",%n"
							+ "  \"active_context\": %s,%n"
							+ "  \"device_name\": \"%s\",%n"
							+ "  \"vendor\": \"%s\",%n"
							+ "  \"renderer\": \"%s\",%n"
							+ "  \"version\": \"%s\",%n"
							+ "  \"openal_soft\": %s,%n"
							+ "  \"efx_supported\": %s,%n"
							+ "  \"maximum_auxiliary_sends\": %d,%n"
							+ "  \"hrtf_extension_supported\": %s,%n"
							+ "  \"hrtf_enabled\": %s,%n"
							+ "  \"efx_resources_created\": %s,%n"
							+ "  \"efx_resources_released\": %s,%n"
							+ "  \"al_error_code\": %d,%n"
							+ "  \"default_audio_path_changed\": false,%n"
							+ "  \"release_calibrated\": false,%n"
							+ "  \"claim_boundary\": \"Capability and temporary "
							+ "resource-lifecycle probe only; no Minecraft "
							+ "source has an EFX filter or auxiliary send.\"%n"
							+ "}%n",
					escape(threadName),
					activeContext,
					escape(deviceName),
					escape(vendor),
					escape(renderer),
					escape(version),
					openAlSoft,
					efxSupported,
					maximumAuxiliarySends,
					hrtfExtensionSupported,
					hrtfEnabled,
					efxResourcesCreated,
					efxResourcesReleased,
					alErrorCode
			);
		}

		private static String escape(String value) {
			return value.replace("\\", "\\\\")
					.replace("\"", "\\\"");
		}
	}

	private record ResourceExercise(
			boolean created,
			boolean released,
			int errorCode
	) {
	}
}
