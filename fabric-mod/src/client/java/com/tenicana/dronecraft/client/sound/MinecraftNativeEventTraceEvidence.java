package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.propagation.BoundedAcousticWorldEventTrace;
import com.tenicana.dronecraft.acoustics.propagation.NativeEventTraceDiagnosticGate;
import com.tenicana.dronecraft.acoustics.propagation.NativeEventTraceEvidenceExporter;
import com.tenicana.dronecraft.client.mixin.SoundEngineExecutorAccessor;
import com.tenicana.dronecraft.client.mixin.SoundManagerEngineAccessor;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;
import net.minecraft.client.sounds.SoundManager;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Read-only ALC snapshot on Minecraft's sound thread followed by off-thread
 * bounded trace export. It does not depend on the production drone audio
 * manager.
 */
public final class MinecraftNativeEventTraceEvidence {
	private MinecraftNativeEventTraceEvidence() {
	}

	public static CompletableFuture<Path> export(
			SoundManager soundManager,
			Path output
	) {
		Objects.requireNonNull(soundManager, "sound manager");
		Objects.requireNonNull(output, "output");
		NativeEventTraceDiagnosticGate.Decision launchDecision =
				MinecraftNativeEventTraceDiagnosticMode.evaluate();
		if (!launchDecision.armed()) {
			return CompletableFuture.failedFuture(
					new IllegalStateException(
							"native trace evidence export rejected: "
									+ launchDecision.reason()
					)
			);
		}
		CompletableFuture<
				NativeEventTraceEvidenceExporter.AlcSnapshot
		> alcFuture = new CompletableFuture<>();
		SoundEngine soundEngine =
				((SoundManagerEngineAccessor) soundManager)
						.fpvdrone$getSoundEngine();
		SoundEngineExecutor executor =
				((SoundEngineExecutorAccessor) soundEngine)
						.fpvdrone$getExecutor();
		executor.schedule(() -> {
			try {
				alcFuture.complete(captureAlcOnSoundThread());
			} catch (Throwable error) {
				alcFuture.completeExceptionally(error);
			}
		});
		return alcFuture.thenApplyAsync(alc -> {
			int capacity =
					MinecraftAcousticWorldDirtyTracker
							.nativeEventTraceCapacity();
			BoundedAcousticWorldEventTrace.Entry[] entries =
					new BoundedAcousticWorldEventTrace.Entry[capacity];
			for (int index = 0; index < capacity; index++) {
				entries[index] =
						new BoundedAcousticWorldEventTrace.Entry();
			}
			int copied =
					MinecraftAcousticWorldDirtyTracker
							.copyNativeEventTrace(entries);
			try {
				return NativeEventTraceEvidenceExporter.write(
						output,
						launchDecision,
						alc,
						entries,
						copied,
						MinecraftAcousticWorldDirtyTracker
								.nativeEventTraceOverwritten(),
						true
				);
			} catch (IOException error) {
				throw new CompletionException(error);
			}
		});
	}

	private static NativeEventTraceEvidenceExporter.AlcSnapshot
	captureAlcOnSoundThread() {
		long context = ALC10.alcGetCurrentContext();
		long device = context == 0L
				? 0L
				: ALC10.alcGetContextsDevice(context);
		String deviceName = device == 0L
				? ""
				: stringOrEmpty(ALC10.alcGetString(
						device,
						ALC10.ALC_DEVICE_SPECIFIER
				));
		String captureDeviceSpecifier = stringOrEmpty(
				ALC10.alcGetString(
						0L,
						ALC11.ALC_CAPTURE_DEVICE_SPECIFIER
				)
		);
		return new NativeEventTraceEvidenceExporter.AlcSnapshot(
				context != 0L,
				device != 0L,
				deviceName,
				captureDeviceSpecifier
		);
	}

	private static String stringOrEmpty(String value) {
		return value == null ? "" : value;
	}
}
