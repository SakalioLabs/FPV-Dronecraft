package com.tenicana.dronecraft.acoustics.propagation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Objects;

/** Validates and writes the bounded native callback/ALC evidence bundle. */
public final class NativeEventTraceEvidenceExporter {
	public static final String REQUIRED_DEVICE_NAME = "No Output";

	private NativeEventTraceEvidenceExporter() {
	}

	public static Path write(
			Path output,
			NativeEventTraceDiagnosticGate.Decision launchDecision,
			AlcSnapshot alc,
			BoundedAcousticWorldEventTrace.Entry[] entries,
			int count,
			long overwritten,
			boolean minecraftClientStarted
	) throws IOException {
		Objects.requireNonNull(output, "output");
		validate(launchDecision, alc, entries, count, overwritten);
		Path normalized = output.toAbsolutePath().normalize();
		Path parent = normalized.getParent();
		if (parent == null) {
			throw new IllegalArgumentException(
					"evidence output must have a parent"
			);
		}
		String json = json(
				launchDecision,
				alc,
				entries,
				count,
				overwritten,
				minecraftClientStarted
		);
		Files.createDirectories(parent);
		Files.writeString(
				normalized,
				json,
				StandardCharsets.UTF_8,
				StandardOpenOption.CREATE_NEW,
				StandardOpenOption.WRITE
		);
		return normalized;
	}

	public static String referenceJson(
			NativeEventTraceDiagnosticGate.Decision launchDecision,
			AlcSnapshot alc,
			BoundedAcousticWorldEventTrace.Entry[] entries,
			int count,
			long overwritten
	) {
		validate(launchDecision, alc, entries, count, overwritten);
		return json(
				launchDecision,
				alc,
				entries,
				count,
				overwritten,
				false
		);
	}

	private static void validate(
			NativeEventTraceDiagnosticGate.Decision launchDecision,
			AlcSnapshot alc,
			BoundedAcousticWorldEventTrace.Entry[] entries,
			int count,
			long overwritten
	) {
		Objects.requireNonNull(launchDecision, "launch decision");
		Objects.requireNonNull(alc, "ALC snapshot");
		Objects.requireNonNull(entries, "trace entries");
		if (!launchDecision.armed()) {
			throw new IllegalStateException(
					"native trace evidence requires an armed launch"
			);
		}
		if (!alc.activeContext() || !alc.activeDevice()) {
			throw new IllegalStateException(
					"native trace evidence requires an active ALC device"
			);
		}
		if (!REQUIRED_DEVICE_NAME.equals(alc.deviceName())) {
			throw new IllegalStateException(
					"native trace evidence requires OpenAL Soft No Output"
			);
		}
		if (!alc.captureDeviceSpecifier().isEmpty()) {
			throw new IllegalStateException(
					"native trace evidence found a capture device"
			);
		}
		if (count < 0 || count > entries.length) {
			throw new IllegalArgumentException(
					"trace entry count out of range"
			);
		}
		if (overwritten < 0L) {
			throw new IllegalArgumentException(
					"trace overwritten count must not be negative"
			);
		}
		long previousOrdinal = 0L;
		for (int index = 0; index < count; index++) {
			BoundedAcousticWorldEventTrace.Entry entry =
					Objects.requireNonNull(
							entries[index],
							"trace entry"
					);
			if (entry.ordinal() <= previousOrdinal) {
				throw new IllegalArgumentException(
						"trace entries are not chronological"
				);
			}
			previousOrdinal = entry.ordinal();
		}
	}

	private static String json(
			NativeEventTraceDiagnosticGate.Decision launchDecision,
			AlcSnapshot alc,
			BoundedAcousticWorldEventTrace.Entry[] entries,
			int count,
			long overwritten,
			boolean minecraftClientStarted
	) {
		StringBuilder traceJson = new StringBuilder("[");
		for (int index = 0; index < count; index++) {
			if (index > 0) {
				traceJson.append(',');
			}
			BoundedAcousticWorldEventTrace.Entry entry = entries[index];
			traceJson.append(String.format(
					Locale.ROOT,
					"{\"ordinal\":%d,\"active_world_epoch\":%d,"
							+ "\"local_sequence\":%d,"
							+ "\"thread_id\":%d,\"kind\":\"%s\","
							+ "\"disposition\":\"%s\","
							+ "\"bounds\":[%d,%d,%d,%d,%d,%d]}",
					entry.ordinal(),
					entry.worldEpoch(),
					entry.sequence(),
					entry.threadId(),
					entry.kind(),
					entry.disposition(),
					entry.minimumX(),
					entry.minimumY(),
					entry.minimumZ(),
					entry.maximumX(),
					entry.maximumY(),
					entry.maximumZ()
			));
		}
		traceJson.append(']');
		return String.format(
				Locale.ROOT,
				"""
				{
				  "schema_version": 1,
				  "status": "valid-native-trace-alc-evidence",
				  "launch_gate": {
				    "state": "%s",
				    "reason": "%s"
				  },
				  "alc": {
				    "active_context": %s,
				    "active_device": %s,
				    "device_name": "%s",
				    "capture_device_specifier": "%s"
				  },
				  "trace": {
				    "retained": %d,
				    "overwritten": %d,
				    "entries": %s
				  },
				  "reference_fixture": %s,
				  "minecraft_client_started": %s,
				  "alc_read_performed": %s,
				  "alc_device_opened": %s,
				  "physical_endpoint_opened": false,
				  "captures_audio": false,
				  "cuda_executed": false,
				  "release_calibrated": false
				}
				""",
				launchDecision.state(),
				launchDecision.reason(),
				alc.activeContext(),
				alc.activeDevice(),
				escape(alc.deviceName()),
				escape(alc.captureDeviceSpecifier()),
				count,
				overwritten,
				traceJson,
				!minecraftClientStarted,
				minecraftClientStarted,
				minecraftClientStarted,
				minecraftClientStarted
		);
	}

	private static String escape(String input) {
		Objects.requireNonNull(input, "JSON string");
		StringBuilder output = new StringBuilder(input.length() + 8);
		for (int index = 0; index < input.length(); index++) {
			char value = input.charAt(index);
			switch (value) {
				case '"' -> output.append("\\\"");
				case '\\' -> output.append("\\\\");
				case '\b' -> output.append("\\b");
				case '\f' -> output.append("\\f");
				case '\n' -> output.append("\\n");
				case '\r' -> output.append("\\r");
				case '\t' -> output.append("\\t");
				default -> {
					if (value < 0x20) {
						output.append(String.format(
								Locale.ROOT,
								"\\u%04x",
								(int) value
						));
					} else {
						output.append(value);
					}
				}
			}
		}
		return output.toString();
	}

	public record AlcSnapshot(
			boolean activeContext,
			boolean activeDevice,
			String deviceName,
			String captureDeviceSpecifier
	) {
		public AlcSnapshot {
			Objects.requireNonNull(deviceName, "device name");
			Objects.requireNonNull(
					captureDeviceSpecifier,
					"capture device specifier"
			);
		}
	}
}
