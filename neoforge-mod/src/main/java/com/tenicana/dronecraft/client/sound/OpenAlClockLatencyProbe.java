package com.tenicana.dronecraft.client.sound;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.SOFTDeviceClock;
import org.lwjgl.openal.SOFTSourceLatency;

import com.tenicana.dronecraft.client.mixin.ChannelSourceAccessor;
import com.tenicana.dronecraft.client.mixin.SoundEngineChannelsAccessor;
import com.tenicana.dronecraft.client.mixin.SoundManagerEngineAccessor;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;

/**
 * Reads optional OpenAL Soft device-clock and source-latency telemetry.
 *
 * <p>All native calls run through an active Dronecraft channel callback on
 * Minecraft's sound thread. The probe is read-only and does not alter source,
 * context, device, or EFX state.
 */
final class OpenAlClockLatencyProbe {
	private static final double STREAM_BUFFER_DURATION_SECONDS = 1.0;
	private static final double MAXIMUM_PAIR_HOST_INTERVAL_SECONDS = 0.8;
	private static final double MINIMUM_OFFSET_ADVANCE_TOLERANCE_SECONDS =
			0.1;
	private static final String DEVICE_CLOCK_EXTENSION =
			"ALC_SOFT_device_clock";
	private static final String SOURCE_LATENCY_EXTENSION =
			"AL_SOFT_source_latency";

	private OpenAlClockLatencyProbe() {
	}

	static CompletableFuture<Sample> capture(SoundManager soundManager) {
		Objects.requireNonNull(soundManager, "soundManager");
		SelectedChannel selected = findHandle(soundManager);
		if (selected == null) {
			return CompletableFuture.completedFuture(
					Sample.missingChannel()
			);
		}
		CompletableFuture<Sample> result = new CompletableFuture<>();
		selected.handle().execute(channel -> {
			try {
				result.complete(captureOnSoundThread(
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
		SelectedChannel fallback = null;
		for (Map.Entry<
				SoundInstance,
				ChannelAccess.ChannelHandle> entry : channels.entrySet()) {
			if (entry.getKey() instanceof DroneLoopSoundInstance droneSound
					&& !entry.getValue().isStopped()) {
				SelectedChannel selected = new SelectedChannel(
						entry.getKey(),
						entry.getValue()
				);
				if (droneSound.layer()
						== DroneLoopSoundInstance.Layer.MOTOR) {
					return selected;
				}
				fallback = selected;
			}
		}
		return fallback;
	}

	private static Sample captureOnSoundThread(
			int source,
			String instanceType
	) {
		long monotonicNs = System.nanoTime();
		long context = ALC10.alcGetCurrentContext();
		long device = context == 0L
				? 0L
				: ALC10.alcGetContextsDevice(context);
		boolean activeContext = context != 0L && device != 0L;
		String deviceName = activeContext
				? stringOrEmpty(ALC10.alcGetString(
						device,
						ALC10.ALC_DEVICE_SPECIFIER
				))
				: "";
		boolean deviceClockSupported = activeContext
				&& ALC10.alcIsExtensionPresent(
						device,
						DEVICE_CLOCK_EXTENSION
				)
				&& ALC.getCapabilities().ALC_SOFT_device_clock;
		boolean sourceLatencySupported = activeContext
				&& AL10.alIsExtensionPresent(SOURCE_LATENCY_EXTENSION)
				&& AL.getCapabilities().AL_SOFT_source_latency;
		boolean deviceClockTelemetryAvailable =
				deviceClockSupported && sourceLatencySupported;
		boolean nativeTelemetryAvailable = sourceLatencySupported;
		long deviceClockNs = 0L;
		long deviceLatencyNs = 0L;
		double sourceOffsetSeconds = 0.0;
		double sourceLatencySeconds = 0.0;
		double sourceClockOffsetSeconds = 0.0;
		double sourceDeviceClockSeconds = 0.0;
		int alError = AL10.AL_NO_ERROR;
		int alcError = ALC10.ALC_NO_ERROR;

		if (sourceLatencySupported) {
			clearErrors(device);
			double[] offsetAndLatency = new double[2];
			SOFTSourceLatency.alGetSourcedvSOFT(
					source,
					SOFTSourceLatency.AL_SEC_OFFSET_LATENCY_SOFT,
					offsetAndLatency
			);
			sourceOffsetSeconds = offsetAndLatency[0];
			sourceLatencySeconds = offsetAndLatency[1];
			if (deviceClockTelemetryAvailable) {
				long[] clockAndLatency = new long[2];
				SOFTDeviceClock.alcGetInteger64vSOFT(
						device,
						SOFTDeviceClock.ALC_DEVICE_CLOCK_LATENCY_SOFT,
						clockAndLatency
				);
				double[] offsetAndClock = new double[2];
				SOFTSourceLatency.alGetSourcedvSOFT(
						source,
						SOFTDeviceClock.AL_SEC_OFFSET_CLOCK_SOFT,
						offsetAndClock
				);
				deviceClockNs = clockAndLatency[0];
				deviceLatencyNs = clockAndLatency[1];
				sourceClockOffsetSeconds = offsetAndClock[0];
				sourceDeviceClockSeconds = offsetAndClock[1];
			}
			alError = AL10.alGetError();
			alcError = ALC10.alcGetError(device);
		}
		return new Sample(
				true,
				instanceType,
				Thread.currentThread().getName(),
				monotonicNs,
				activeContext,
				deviceName,
				deviceClockSupported,
				sourceLatencySupported,
				nativeTelemetryAvailable,
				deviceClockNs,
				deviceLatencyNs,
				sourceOffsetSeconds,
				sourceLatencySeconds,
				sourceClockOffsetSeconds,
				sourceDeviceClockSeconds,
				alError,
				alcError
		);
	}

	private static void clearErrors(long device) {
		for (int attempt = 0; attempt < 16; attempt++) {
			if (AL10.alGetError() == AL10.AL_NO_ERROR) {
				break;
			}
		}
		for (int attempt = 0; attempt < 16; attempt++) {
			if (ALC10.alcGetError(device) == ALC10.ALC_NO_ERROR) {
				break;
			}
		}
	}

	static PairResult validatePair(Sample first, Sample second) {
		Objects.requireNonNull(first, "first");
		Objects.requireNonNull(second, "second");
		if (!first.sourceFound() || !second.sourceFound()) {
			throw new IllegalArgumentException(
					"both samples require an active Dronecraft source"
			);
		}
		if (!first.activeContext() || !second.activeContext()) {
			throw new IllegalArgumentException(
					"both samples require an active OpenAL context"
			);
		}
		if (!first.deviceName().equals(second.deviceName())) {
			throw new IllegalArgumentException(
					"device changed within a sample pair"
			);
		}
		if (first.nativeTelemetryAvailable()
				!= second.nativeTelemetryAvailable()
				|| first.deviceClockSupported()
				!= second.deviceClockSupported()
				|| first.sourceLatencySupported()
				!= second.sourceLatencySupported()) {
			throw new IllegalArgumentException(
					"extension support changed within a sample pair"
			);
		}
		long hostElapsedNs = second.monotonicNs() - first.monotonicNs();
		if (hostElapsedNs <= 0L) {
			throw new IllegalArgumentException(
					"host monotonic time did not advance"
			);
		}
		if (!first.nativeTelemetryAvailable()) {
			return new PairResult(
					first,
					second,
					hostElapsedNs,
					0L,
					0.0,
					0.0,
					false,
					false
			);
		}
		for (Sample sample : new Sample[] {first, second}) {
			if (!Double.isFinite(sample.sourceOffsetSeconds())
					|| sample.sourceOffsetSeconds() < 0.0
					|| !Double.isFinite(sample.sourceLatencySeconds())
					|| sample.sourceLatencySeconds() < 0.0
					|| sample.alErrorCode() != AL10.AL_NO_ERROR
					|| sample.alcErrorCode() != ALC10.ALC_NO_ERROR) {
				throw new IllegalArgumentException(
						"native telemetry sample is invalid"
				);
			}
			if (sample.deviceClockSupported()
					&& (sample.deviceClockNs() < 0L
							|| sample.deviceLatencyNs() < 0L
							|| !Double.isFinite(
									sample.sourceClockOffsetSeconds()
							)
							|| sample.sourceClockOffsetSeconds() < 0.0
							|| !Double.isFinite(
									sample.sourceDeviceClockSeconds()
							)
							|| sample.sourceDeviceClockSeconds() < 0.0)) {
				throw new IllegalArgumentException(
						"device-clock telemetry sample is invalid"
				);
			}
		}
		long clockElapsedNs = 0L;
		double clockRateRatio = 0.0;
		double sourceOffsetAdvanceSeconds = 0.0;
		boolean sourceOffsetWrapped = false;
		if (first.sourceLatencySupported()) {
			double hostElapsedSeconds = hostElapsedNs / 1.0e9;
			if (hostElapsedSeconds >= MAXIMUM_PAIR_HOST_INTERVAL_SECONDS) {
				throw new IllegalArgumentException(
						"source-offset sample interval is too long "
								+ "for an unambiguous one-buffer wrap"
				);
			}
			double rawAdvance = second.sourceOffsetSeconds()
					- first.sourceOffsetSeconds();
			sourceOffsetWrapped = rawAdvance <= 0.0;
			sourceOffsetAdvanceSeconds = rawAdvance
					+ (sourceOffsetWrapped
							? STREAM_BUFFER_DURATION_SECONDS
							: 0.0);
			double toleranceSeconds = Math.max(
					MINIMUM_OFFSET_ADVANCE_TOLERANCE_SECONDS,
					hostElapsedSeconds * 0.5
			);
			if (sourceOffsetAdvanceSeconds <= 0.0
					|| Math.abs(
							sourceOffsetAdvanceSeconds
									- hostElapsedSeconds
					) > toleranceSeconds) {
				throw new IllegalArgumentException(
						"active source offset advance is inconsistent "
								+ "with host elapsed time"
				);
			}
		}
		if (first.deviceClockSupported()) {
			clockElapsedNs =
					second.deviceClockNs() - first.deviceClockNs();
			if (clockElapsedNs <= 0L) {
				throw new IllegalArgumentException(
						"active device clock did not advance"
				);
			}
			clockRateRatio =
					(double) clockElapsedNs / (double) hostElapsedNs;
		}
		return new PairResult(
				first,
				second,
				hostElapsedNs,
				clockElapsedNs,
				clockRateRatio,
				sourceOffsetAdvanceSeconds,
				sourceOffsetWrapped,
				true
		);
	}

	private static String stringOrEmpty(String value) {
		return value == null ? "" : value;
	}

	record Sample(
			boolean sourceFound,
			String instanceType,
			String threadName,
			long monotonicNs,
			boolean activeContext,
			String deviceName,
			boolean deviceClockSupported,
			boolean sourceLatencySupported,
			boolean nativeTelemetryAvailable,
			long deviceClockNs,
			long deviceLatencyNs,
			double sourceOffsetSeconds,
			double sourceLatencySeconds,
			double sourceClockOffsetSeconds,
			double sourceDeviceClockSeconds,
			int alErrorCode,
			int alcErrorCode
	) {
		Sample {
			Objects.requireNonNull(instanceType, "instanceType");
			Objects.requireNonNull(threadName, "threadName");
			Objects.requireNonNull(deviceName, "deviceName");
		}

		private static Sample missingChannel() {
			return new Sample(
					false,
					"",
					"",
					System.nanoTime(),
					false,
					"",
					false,
					false,
					false,
					0L,
					0L,
					0.0,
					0.0,
					0.0,
					0.0,
					AL10.AL_NO_ERROR,
					ALC10.ALC_NO_ERROR
			);
		}

		String toJson() {
			return String.format(
					Locale.ROOT,
					"{\"source_found\":%s,"
							+ "\"instance_type\":\"%s\","
							+ "\"thread_name\":\"%s\","
							+ "\"host_monotonic_ns\":%d,"
							+ "\"active_context\":%s,"
							+ "\"device_name\":\"%s\","
							+ "\"device_clock_supported\":%s,"
							+ "\"source_latency_supported\":%s,"
							+ "\"native_telemetry_available\":%s,"
							+ "\"device_clock_ns\":%d,"
							+ "\"device_latency_ns\":%d,"
							+ "\"source_offset_seconds\":%.17g,"
							+ "\"source_latency_seconds\":%.17g,"
							+ "\"source_clock_offset_seconds\":%.17g,"
							+ "\"source_device_clock_seconds\":%.17g,"
							+ "\"al_error_code\":%d,"
							+ "\"alc_error_code\":%d}",
					sourceFound,
					escape(instanceType),
					escape(threadName),
					monotonicNs,
					activeContext,
					escape(deviceName),
					deviceClockSupported,
					sourceLatencySupported,
					nativeTelemetryAvailable,
					deviceClockNs,
					deviceLatencyNs,
					sourceOffsetSeconds,
					sourceLatencySeconds,
					sourceClockOffsetSeconds,
					sourceDeviceClockSeconds,
					alErrorCode,
					alcErrorCode
			);
		}
	}

	record PairResult(
			Sample first,
			Sample second,
			long hostElapsedNs,
			long deviceClockElapsedNs,
			double deviceToHostClockRateRatio,
			double sourceOffsetAdvanceSeconds,
			boolean sourceOffsetWrapped,
			boolean nativeTelemetryValidated
	) {
		PairResult {
			Objects.requireNonNull(first, "first");
			Objects.requireNonNull(second, "second");
		}

		String toJson() {
			return String.format(
					Locale.ROOT,
					"{\"first\":%s,\"second\":%s,"
							+ "\"host_elapsed_ns\":%d,"
							+ "\"device_clock_elapsed_ns\":%d,"
							+ "\"device_to_host_clock_rate_ratio\":%.17g,"
							+ "\"source_offset_advance_seconds\":%.17g,"
							+ "\"source_offset_wrapped\":%s,"
							+ "\"native_telemetry_validated\":%s}",
					first.toJson(),
					second.toJson(),
					hostElapsedNs,
					deviceClockElapsedNs,
					deviceToHostClockRateRatio,
					sourceOffsetAdvanceSeconds,
					sourceOffsetWrapped,
					nativeTelemetryValidated
			);
		}
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\")
				.replace("\"", "\\\"");
	}

	private record SelectedChannel(
			SoundInstance instance,
			ChannelAccess.ChannelHandle handle
	) {
	}
}
