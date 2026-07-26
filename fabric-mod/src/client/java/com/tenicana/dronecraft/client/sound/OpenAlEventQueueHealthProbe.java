package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.client.mixin.SoundEngineExecutorAccessor;
import com.tenicana.dronecraft.client.mixin.SoundManagerEngineAccessor;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;
import net.minecraft.client.sounds.SoundManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.SOFTEventProc;
import org.lwjgl.openal.SOFTEvents;

import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded diagnostic ownership exercise for {@code AL_SOFT_events}.
 *
 * <p>The probe refuses to replace an existing context callback. Its callback
 * performs no AL calls and only appends fixed-shape event metadata.</p>
 */
final class OpenAlEventQueueHealthProbe {
	static final int CONTROL_SAMPLE_RATE_HZ = 48_000;
	static final int CONTROL_SAMPLE_FRAMES = 960;
	static final int CONTROL_SAMPLE_WIDTH_BITS = 16;
	private static final String EVENTS_EXTENSION = "AL_SOFT_events";
	private static final int[] EVENT_TYPES = {
			SOFTEvents.AL_EVENT_TYPE_BUFFER_COMPLETED_SOFT,
			SOFTEvents.AL_EVENT_TYPE_SOURCE_STATE_CHANGED_SOFT
	};

	private OpenAlEventQueueHealthProbe() {
	}

	static CompletableFuture<Session> start(
			SoundManager soundManager,
			int maximumEvents
	) {
		Objects.requireNonNull(soundManager, "soundManager");
		if (maximumEvents < 32 || maximumEvents > 4_096) {
			throw new IllegalArgumentException(
					"maximumEvents must be in [32, 4096]"
			);
		}
		SoundEngine soundEngine =
				((SoundManagerEngineAccessor) soundManager)
						.fpvdrone$getSoundEngine();
		SoundEngineExecutor executor =
				((SoundEngineExecutorAccessor) soundEngine)
						.fpvdrone$getExecutor();
		CompletableFuture<Session> result = new CompletableFuture<>();
		executor.schedule(() -> {
			Session session = new Session(executor, maximumEvents);
			try {
				session.registerOnSoundThread();
				result.complete(session);
			} catch (Throwable error) {
				session.rollbackOnSoundThread();
				result.completeExceptionally(error);
			}
		});
		return result;
	}

	static final class Session {
		private final SoundEngineExecutor executor;
		private final int maximumEvents;
		private final ConcurrentLinkedQueue<Event> events =
				new ConcurrentLinkedQueue<>();
		private final AtomicLong sequence = new AtomicLong();
		private final AtomicInteger droppedEvents = new AtomicInteger();
		private SOFTEventProc callback;
		private long registeredNs;
		private long existingCallbackPointer;
		private long existingUserPointer;
		private long ownedCallbackPointer;
		private String registrationThread = "";
		private boolean activeContext;
		private boolean extensionSupported;
		private boolean callbackPointerMatched;
		private int controlSource;
		private int controlBuffer;
		private int registrationAlError;
		private boolean registered;
		private boolean stopped;

		private Session(
				SoundEngineExecutor executor,
				int maximumEvents
		) {
			this.executor = executor;
			this.maximumEvents = maximumEvents;
		}

		private void registerOnSoundThread() {
			registrationThread = Thread.currentThread().getName();
			long context = ALC10.alcGetCurrentContext();
			long device = context == 0L
					? 0L
					: ALC10.alcGetContextsDevice(context);
			activeContext = context != 0L && device != 0L;
			extensionSupported = activeContext
					&& AL10.alIsExtensionPresent(EVENTS_EXTENSION)
					&& AL.getCapabilities().AL_SOFT_events;
			if (!activeContext || !extensionSupported) {
				throw new IllegalStateException(
						"AL_SOFT_events is unavailable on the active context"
				);
			}

			clearAlErrors();
			existingCallbackPointer = SOFTEvents.alGetPointerSOFT(
					SOFTEvents.AL_EVENT_CALLBACK_FUNCTION_SOFT
			);
			existingUserPointer = SOFTEvents.alGetPointerSOFT(
					SOFTEvents.AL_EVENT_CALLBACK_USER_PARAM_SOFT
			);
			if (existingCallbackPointer != 0L
					|| existingUserPointer != 0L) {
				throw new IllegalStateException(
						"refusing to replace an existing OpenAL event callback"
				);
			}

			callback = SOFTEventProc.create(this::onEvent);
			ownedCallbackPointer = callback.address();
			registeredNs = System.nanoTime();
			SOFTEvents.nalEventCallbackSOFT(ownedCallbackPointer, 0L);
			SOFTEvents.alEventControlSOFT(EVENT_TYPES, true);
			callbackPointerMatched = SOFTEvents.alGetPointerSOFT(
					SOFTEvents.AL_EVENT_CALLBACK_FUNCTION_SOFT
			) == ownedCallbackPointer;
			if (!callbackPointerMatched) {
				throw new IllegalStateException(
						"OpenAL event callback ownership could not be verified"
				);
			}

			controlSource = AL10.alGenSources();
			controlBuffer = AL10.alGenBuffers();
			ShortBuffer silence = BufferUtils.createShortBuffer(
					CONTROL_SAMPLE_FRAMES
			);
			silence.put(new short[CONTROL_SAMPLE_FRAMES]).flip();
			AL10.alBufferData(
					controlBuffer,
					AL10.AL_FORMAT_MONO16,
					silence,
					CONTROL_SAMPLE_RATE_HZ
			);
			AL10.alSourceQueueBuffers(controlSource, controlBuffer);
			AL10.alSourcePlay(controlSource);
			registrationAlError = AL10.alGetError();
			if (registrationAlError != AL10.AL_NO_ERROR) {
				throw new IllegalStateException(
						"OpenAL event positive control failed: "
								+ registrationAlError
				);
			}
			registered = true;
		}

		private void onEvent(
				int eventType,
				int object,
				int parameter,
				int messageLength,
				long message,
				long userParameter
		) {
			long next = sequence.getAndIncrement();
			if (next >= maximumEvents) {
				droppedEvents.incrementAndGet();
				return;
			}
			events.add(new Event(
					next,
					eventType,
					object,
					parameter,
					messageLength,
					System.nanoTime(),
					Thread.currentThread().getName(),
					userParameter
			));
		}

		boolean hasCoverage(
				Set<Integer> productionSources,
				int minimumCompletedBuffersPerSource
		) {
			if (productionSources.size() != 2
					|| minimumCompletedBuffersPerSource < 1) {
				return false;
			}
			for (int source : productionSources) {
				if (completedBufferCount(source)
						< minimumCompletedBuffersPerSource) {
					return false;
				}
			}
			return completedBufferCount(controlSource) >= 1
					&& events.stream().anyMatch(event ->
							event.eventType()
									== SOFTEvents
											.AL_EVENT_TYPE_SOURCE_STATE_CHANGED_SOFT
									&& event.object() == controlSource
									&& event.parameter() == AL10.AL_STOPPED
					);
		}

		private int completedBufferCount(int source) {
			return events.stream()
					.filter(event ->
							event.eventType()
									== SOFTEvents
											.AL_EVENT_TYPE_BUFFER_COMPLETED_SOFT
									&& event.object() == source
					)
					.mapToInt(Event::parameter)
					.sum();
		}

		CompletableFuture<Cleanup> stop() {
			CompletableFuture<Cleanup> result = new CompletableFuture<>();
			executor.schedule(() -> {
				try {
					result.complete(stopOnSoundThread());
				} catch (Throwable error) {
					result.completeExceptionally(error);
				}
			});
			return result;
		}

		void abort() {
			executor.schedule(() -> {
				if (registered && !stopped) {
					try {
						stopOnSoundThread();
					} catch (Throwable ignored) {
						rollbackOnSoundThread();
					}
				}
			});
		}

		private Cleanup stopOnSoundThread() {
			if (!registered || stopped) {
				throw new IllegalStateException(
						"OpenAL event session is not active"
				);
			}
			clearAlErrors();
			SOFTEvents.alEventControlSOFT(EVENT_TYPES, false);
			SOFTEvents.nalEventCallbackSOFT(0L, 0L);
			long callbackPointerAfter = SOFTEvents.alGetPointerSOFT(
					SOFTEvents.AL_EVENT_CALLBACK_FUNCTION_SOFT
			);
			long userPointerAfter = SOFTEvents.alGetPointerSOFT(
					SOFTEvents.AL_EVENT_CALLBACK_USER_PARAM_SOFT
			);
			int eventsAtCleanup = events.size();
			if (AL10.alIsSource(controlSource)) {
				AL10.alDeleteSources(controlSource);
			}
			if (AL10.alIsBuffer(controlBuffer)) {
				AL10.alDeleteBuffers(controlBuffer);
			}
			boolean sourceDeleted = !AL10.alIsSource(controlSource);
			boolean bufferDeleted = !AL10.alIsBuffer(controlBuffer);
			int cleanupAlError = AL10.alGetError();
			long cleanupNs = System.nanoTime();
			callback.free();
			callback = null;
			stopped = true;
			return new Cleanup(
					Thread.currentThread().getName(),
					cleanupNs,
					callbackPointerAfter == 0L,
					userPointerAfter == 0L,
					sourceDeleted,
					bufferDeleted,
					eventsAtCleanup,
					cleanupAlError
			);
		}

		Snapshot snapshotAfterQuietWindow(
				Cleanup cleanup,
				long quietWindowNs
		) {
			Objects.requireNonNull(cleanup, "cleanup");
			if (!stopped || quietWindowNs < 150_000_000L) {
				throw new IllegalStateException(
						"cleanup or quiet-window contract is incomplete"
				);
			}
			List<Event> ordered = new ArrayList<>(events);
			ordered.sort(Comparator.comparingLong(Event::sequence));
			return new Snapshot(
					activeContext,
					extensionSupported,
					registrationThread,
					existingCallbackPointer == 0L,
					existingUserPointer == 0L,
					registered,
					callbackPointerMatched,
					registeredNs,
					ownedCallbackPointer != 0L,
					controlSource,
					controlBuffer,
					registrationAlError,
					maximumEvents,
					droppedEvents.get(),
					List.copyOf(ordered),
					cleanup,
					quietWindowNs,
					ordered.size(),
					ordered.size() == cleanup.eventsAtCleanup()
			);
		}

		int eventCount() {
			return events.size();
		}

		private void rollbackOnSoundThread() {
			try {
				if (extensionSupported && callback != null) {
					SOFTEvents.alEventControlSOFT(EVENT_TYPES, false);
					SOFTEvents.nalEventCallbackSOFT(0L, 0L);
				}
				if (controlSource != 0 && AL10.alIsSource(controlSource)) {
					AL10.alDeleteSources(controlSource);
				}
				if (controlBuffer != 0 && AL10.alIsBuffer(controlBuffer)) {
					AL10.alDeleteBuffers(controlBuffer);
				}
			} finally {
				if (callback != null) {
					callback.free();
					callback = null;
				}
			}
		}
	}

	record Event(
			long sequence,
			int eventType,
			int object,
			int parameter,
			int messageLength,
			long monotonicNs,
			String callbackThread,
			long userParameter
	) {
	}

	record Cleanup(
			String threadName,
			long cleanupNs,
			boolean callbackPointerZero,
			boolean userPointerZero,
			boolean controlSourceDeleted,
			boolean controlBufferDeleted,
			int eventsAtCleanup,
			int alError
	) {
	}

	record Snapshot(
			boolean activeContext,
			boolean extensionSupported,
			String registrationThread,
			boolean existingCallbackPointerZero,
			boolean existingUserPointerZero,
			boolean callbackRegistered,
			boolean callbackPointerMatched,
			long registeredNs,
			boolean ownedCallbackPointerNonzero,
			int controlSource,
			int controlBuffer,
			int registrationAlError,
			int maximumEvents,
			int droppedEvents,
			List<Event> events,
			Cleanup cleanup,
			long quietWindowNs,
			int eventsAfterQuietWindow,
			boolean noEventsAfterCleanup
	) {
		Snapshot {
			events = List.copyOf(events);
		}
	}

	private static void clearAlErrors() {
		for (int attempt = 0; attempt < 16; attempt++) {
			if (AL10.alGetError() == AL10.AL_NO_ERROR) {
				return;
			}
		}
	}
}
