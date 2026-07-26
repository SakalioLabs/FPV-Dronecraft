package com.tenicana.dronecraft.client.sound;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;

import com.mojang.blaze3d.audio.Channel;
import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;
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
import net.fabricmc.loader.api.FabricLoader;

/**
 * Opt-in shared OpenAL EFX environment backend.
 *
 * <p>All native state lives on Minecraft's sound thread. One effect and one
 * auxiliary slot are shared by every active drone layer; each source owns only
 * a send filter. The feature is disabled unless {@value #ENABLED_PROPERTY} is
 * explicitly true.
 */
final class OpenAlEfxController {
	static final String ENABLED_PROPERTY = "fpvdrone.openalEfx";
	static final String FAULT_STAGE_PROPERTY =
			"fpvdrone.acoustics.efxFaultStage";
	private static final String EFX_EXTENSION = "ALC_EXT_EFX";
	private static final double MINIMUM_RT60_SECONDS = 0.1;
	private static final double MAXIMUM_RT60_SECONDS = 20.0;

	private final AtomicReference<DesiredState> desired =
			new AtomicReference<>();
	private final AtomicBoolean drainScheduled = new AtomicBoolean();
	private final AtomicReference<Diagnostics> diagnostics =
			new AtomicReference<>(Diagnostics.inactive());
	private final AtomicLong soundEngineGeneration = new AtomicLong();

	// Sound-thread-owned state.
	private long activeContext;
	private long activeSoundEngineGeneration;
	private long failedContext;
	private int effect;
	private int auxiliarySlot;
	private final Map<Integer, Integer> sourceFilters = new HashMap<>();
	private int contextRebuilds;
	private int cleanupCount;
	private long lastFaultContext;
	private FaultStage lastFaultStage = FaultStage.NONE;
	private int faultInjectionCount;

	enum Status {
		INACTIVE(false),
		WAITING_CONTEXT(false),
		WAITING_SOURCES(false),
		EXTENSION_UNAVAILABLE(true),
		CONTEXT_FAILED(true),
		OPERATIONAL(false);

		private final boolean terminalFailure;

		Status(boolean terminalFailure) {
			this.terminalFailure = terminalFailure;
		}

		boolean terminalFailure() {
			return terminalFailure;
		}
	}

	enum FaultStage {
		NONE("none"),
		RESOURCE_CREATE("resource-create"),
		PARAMETER_WRITE("parameter-write"),
		SOURCE_ROUTE("source-route");

		private final String token;

		FaultStage(String token) {
			this.token = token;
		}

		String token() {
			return token;
		}
	}

	static boolean enabled() {
		return AcousticBackendSelector.openAlEfxEnabled()
				&& Boolean.parseBoolean(
				System.getProperty("fpvdrone.proceduralAudio", "true")
		);
	}

	void tick(
			SoundManager soundManager,
			List<? extends SoundInstance> sources,
			FdnEnvironmentMapper.Controls environment
	) {
		Objects.requireNonNull(soundManager, "soundManager");
		Objects.requireNonNull(sources, "sources");
		Objects.requireNonNull(environment, "environment");
		request(
				soundManager,
				enabled()
						? enabledState(soundManager, sources, environment)
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

	void onSoundEngineReload() {
		soundEngineGeneration.incrementAndGet();
	}

	private DesiredState enabledState(
			SoundManager soundManager,
			List<? extends SoundInstance> sources,
			FdnEnvironmentMapper.Controls environment
	) {
		SoundEngine soundEngine =
				((SoundManagerEngineAccessor) soundManager)
						.fpvdrone$getSoundEngine();
		Map<SoundInstance, ChannelAccess.ChannelHandle> channels =
				((SoundEngineChannelsAccessor) soundEngine)
						.fpvdrone$getInstanceToChannel();
		List<ChannelAccess.ChannelHandle> handles = new ArrayList<>();
		for (SoundInstance source : sources) {
			ChannelAccess.ChannelHandle handle = channels.get(source);
			if (handle != null && !handle.isStopped()) {
				handles.add(handle);
			}
		}
		return new DesiredState(true, List.copyOf(handles), environment);
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
				// We are already on the owning executor.
				drainOnSoundThread();
			}
		}
	}

	private void applyOnSoundThread(DesiredState next) {
		long engineGeneration = soundEngineGeneration.get();
		long context = ALC10.alcGetCurrentContext();
		long device = context == 0L
				? 0L
				: ALC10.alcGetContextsDevice(context);
		if (context != activeContext
				|| engineGeneration != activeSoundEngineGeneration) {
			// The previous context owns and destroys its object names. Never
			// delete stale IDs in the replacement context.
			activeContext = context;
			activeSoundEngineGeneration = engineGeneration;
			failedContext = 0L;
			effect = 0;
			auxiliarySlot = 0;
			sourceFilters.clear();
			contextRebuilds++;
		}
		if (!next.enabled()) {
			cleanupCurrentContext();
			if (context != 0L && context == failedContext) {
				publishDiagnostics(
						Status.CONTEXT_FAILED,
						false,
						false,
						0,
						diagnostics.get().alErrorCode()
				);
				return;
			}
			publishDiagnostics(
					Status.INACTIVE,
					false,
					false,
					0,
					AL10.AL_NO_ERROR
			);
			return;
		}
		boolean efx = device != 0L
				&& ALC10.alcIsExtensionPresent(device, EFX_EXTENSION);
		if (context == 0L) {
			cleanupCurrentContext();
			publishDiagnostics(
					Status.WAITING_CONTEXT,
					true,
					false,
					0,
					AL10.AL_NO_ERROR
			);
			return;
		}
		if (!efx) {
			cleanupCurrentContext();
			publishDiagnostics(
					Status.EXTENSION_UNAVAILABLE,
					true,
					false,
					0,
					AL10.AL_NO_ERROR
			);
			return;
		}
		if (context == failedContext) {
			cleanupCurrentContext();
			publishDiagnostics(
					Status.CONTEXT_FAILED,
					true,
					false,
					0,
					diagnostics.get().alErrorCode()
			);
			return;
		}
		if (next.handles().isEmpty()) {
			cleanupCurrentContext();
			publishDiagnostics(
					Status.WAITING_SOURCES,
					true,
					false,
					0,
					AL10.AL_NO_ERROR
			);
			return;
		}

		clearAlErrors();
		ensureSharedResources();
		injectDevelopmentFault(FaultStage.RESOURCE_CREATE);
		int resourceError = AL10.alGetError();
		if (effect == 0 || auxiliarySlot == 0
				|| resourceError != AL10.AL_NO_ERROR) {
			failCurrentContext(resourceError);
			return;
		}
		configureReverbEffect(effect, next.environment());
		EXTEfx.alAuxiliaryEffectSloti(
				auxiliarySlot,
				EXTEfx.AL_EFFECTSLOT_EFFECT,
				effect
		);
		injectDevelopmentFault(FaultStage.PARAMETER_WRITE);
		Set<Integer> activeSources = new HashSet<>();
		for (ChannelAccess.ChannelHandle handle : next.handles()) {
			if (handle.isStopped()) {
				continue;
			}
			Channel channel =
					((ChannelHandleChannelAccessor) handle)
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
			int filter = sourceFilters.computeIfAbsent(
					source,
					ignored -> createSendFilter()
			);
			if (filter == 0) {
				failCurrentContext(AL10.alGetError());
				return;
			}
			configureSendFilter(
					filter,
					next.environment().rt60Seconds()
			);
			AL11.alSource3i(
					source,
					EXTEfx.AL_AUXILIARY_SEND_FILTER,
					auxiliarySlot,
					0,
					filter
			);
		}
		injectDevelopmentFault(FaultStage.SOURCE_ROUTE);
		removeStaleSources(activeSources);
		int error = AL10.alGetError();
		if (error != AL10.AL_NO_ERROR) {
			failCurrentContext(error);
			return;
		}
		publishDiagnostics(
				Status.OPERATIONAL,
				true,
				true,
				activeSources.size(),
				AL10.AL_NO_ERROR
		);
	}

	private void ensureSharedResources() {
		if (effect != 0 && auxiliarySlot != 0) {
			return;
		}
		effect = EXTEfx.alGenEffects();
		EXTEfx.alEffecti(
				effect,
				EXTEfx.AL_EFFECT_TYPE,
				EXTEfx.AL_EFFECT_REVERB
		);
		auxiliarySlot = EXTEfx.alGenAuxiliaryEffectSlots();
		EXTEfx.alAuxiliaryEffectSloti(
				auxiliarySlot,
				EXTEfx.AL_EFFECTSLOT_EFFECT,
				effect
		);
	}

	static void configureReverbEffect(
			int effect,
			FdnEnvironmentMapper.Controls environment
	) {
		AcousticBands rt60 = environment.rt60Seconds();
		float decay = (float) clamp(
				rt60.mid(),
				MINIMUM_RT60_SECONDS,
				MAXIMUM_RT60_SECONDS
		);
		float gain = (float) clamp(environment.wetGain(), 0.0, 1.0);
		float gainHigh = (float) highFrequencyRatio(rt60);
		EXTEfx.alEffectf(
				effect,
				EXTEfx.AL_REVERB_DECAY_TIME,
				decay
		);
		EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_GAIN, gain);
		EXTEfx.alEffectf(
				effect,
				EXTEfx.AL_REVERB_GAINHF,
				gainHigh
		);
	}

	private int createSendFilter() {
		int filter = EXTEfx.alGenFilters();
		EXTEfx.alFilteri(
				filter,
				EXTEfx.AL_FILTER_TYPE,
				EXTEfx.AL_FILTER_LOWPASS
		);
		return filter;
	}

	static void configureSendFilter(int filter, AcousticBands rt60) {
		EXTEfx.alFilterf(
				filter,
				EXTEfx.AL_LOWPASS_GAIN,
				1.0f
		);
		EXTEfx.alFilterf(
				filter,
				EXTEfx.AL_LOWPASS_GAINHF,
				(float) highFrequencyRatio(rt60)
		);
	}

	private void removeStaleSources(Set<Integer> activeSources) {
		List<Integer> stale = sourceFilters.keySet().stream()
				.filter(source -> !activeSources.contains(source))
				.toList();
		for (int source : stale) {
			int filter = sourceFilters.remove(source);
			if (AL10.alIsSource(source)) {
				detachSource(source);
			}
			EXTEfx.alDeleteFilters(filter);
		}
	}

	private void cleanupCurrentContext() {
		if (activeContext == 0L) {
			effect = 0;
			auxiliarySlot = 0;
			sourceFilters.clear();
			return;
		}
		boolean hadResources = effect != 0
				|| auxiliarySlot != 0
				|| !sourceFilters.isEmpty();
		clearAlErrors();
		for (Map.Entry<Integer, Integer> entry :
				sourceFilters.entrySet()) {
			if (AL10.alIsSource(entry.getKey())) {
				detachSource(entry.getKey());
			}
			EXTEfx.alDeleteFilters(entry.getValue());
		}
		sourceFilters.clear();
		if (auxiliarySlot != 0) {
			EXTEfx.alDeleteAuxiliaryEffectSlots(auxiliarySlot);
		}
		if (effect != 0) {
			EXTEfx.alDeleteEffects(effect);
		}
		effect = 0;
		auxiliarySlot = 0;
		if (hadResources) {
			cleanupCount++;
		}
		AL10.alGetError();
	}

	private void detachSource(int source) {
		AL11.alSource3i(
				source,
				EXTEfx.AL_AUXILIARY_SEND_FILTER,
				EXTEfx.AL_EFFECTSLOT_NULL,
				0,
				EXTEfx.AL_FILTER_NULL
		);
	}

	private void failCurrentContext(int error) {
		failedContext = activeContext;
		cleanupCurrentContext();
		publishDiagnostics(
				Status.CONTEXT_FAILED,
				true,
				false,
				0,
				error
		);
	}

	private void injectDevelopmentFault(FaultStage stage) {
		if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
			return;
		}
		String requested = System.getProperty(
				FAULT_STAGE_PROPERTY,
				FaultStage.NONE.token()
		);
		if (!stage.token().equals(requested)
				|| (lastFaultContext == activeContext
						&& lastFaultStage == stage)) {
			return;
		}
		lastFaultContext = activeContext;
		lastFaultStage = stage;
		faultInjectionCount++;
		// Invalid enum is context-local, deterministic, and does not mutate
		// an unrelated OpenAL object. The normal production error check owns
		// detection and cleanup.
		AL10.alGetInteger(Integer.MAX_VALUE);
	}

	private void publishDiagnostics(
			Status status,
			boolean requested,
			boolean operational,
			int attachedSources,
			int error
	) {
		diagnostics.set(new Diagnostics(
				status,
				requested,
				operational,
				activeContext != 0L,
				effect != 0 && auxiliarySlot != 0,
				attachedSources,
				sourceFilters.size(),
				contextRebuilds,
				cleanupCount,
				error,
				lastFaultStage,
				faultInjectionCount,
				false
		));
	}

	private static double highFrequencyRatio(AcousticBands rt60) {
		if (rt60.mid() <= 0.0 || rt60.high() <= 0.0) {
			return 0.1;
		}
		return clamp(
				Math.sqrt(rt60.high() / rt60.mid()),
				0.1,
				1.0
		);
	}

	private static double clamp(double value, double minimum, double maximum) {
		if (!Double.isFinite(value)) {
			return minimum;
		}
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static void clearAlErrors() {
		for (int attempt = 0; attempt < 16; attempt++) {
			if (AL10.alGetError() == AL10.AL_NO_ERROR) {
				return;
			}
		}
	}

	record Diagnostics(
			Status status,
			boolean requested,
			boolean operational,
			boolean activeContext,
			boolean sharedResourcesCreated,
			int attachedSources,
			int allocatedSourceFilters,
			int contextRebuilds,
			int cleanupCount,
			int alErrorCode,
			FaultStage lastFaultStage,
			int faultInjectionCount,
			boolean releaseCalibrated
	) {
		Diagnostics {
			Objects.requireNonNull(status, "status");
			Objects.requireNonNull(lastFaultStage, "lastFaultStage");
			if (operational != (status == Status.OPERATIONAL)) {
				throw new IllegalArgumentException(
						"operational must match status"
				);
			}
		}

		private static Diagnostics inactive() {
			return new Diagnostics(
					Status.INACTIVE,
					false,
					false,
					false,
					false,
					0,
					0,
					0,
					0,
					AL10.AL_NO_ERROR,
					FaultStage.NONE,
					0,
					false
			);
		}
	}

	private record DesiredState(
			boolean enabled,
			List<ChannelAccess.ChannelHandle> handles,
			FdnEnvironmentMapper.Controls environment
	) {
		private static final FdnEnvironmentMapper.Controls ANECHOIC =
				new FdnEnvironmentMapper.Controls(
						-1L,
						AcousticBands.SILENT,
						0.0,
						FdnEnvironmentMapper.DEFAULT_TRANSITION_SECONDS
				);

		private DesiredState {
			handles = List.copyOf(handles);
			Objects.requireNonNull(environment, "environment");
		}

		private static DesiredState disabled() {
			return new DesiredState(false, List.of(), ANECHOIC);
		}
	}
}
