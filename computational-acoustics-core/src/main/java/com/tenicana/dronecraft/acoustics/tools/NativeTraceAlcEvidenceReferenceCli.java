package com.tenicana.dronecraft.acoustics.tools;

import com.tenicana.dronecraft.acoustics.propagation.BoundedAcousticWorldEventTrace;
import com.tenicana.dronecraft.acoustics.propagation.NativeEventTraceDiagnosticGate;
import com.tenicana.dronecraft.acoustics.propagation.NativeEventTraceEvidenceExporter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import static com.tenicana.dronecraft.acoustics.propagation.BoundedAcousticWorldEventTrace.Disposition.ACCEPTED_DIRTY;
import static com.tenicana.dronecraft.acoustics.propagation.BoundedAcousticWorldEventTrace.Disposition.ACCEPTED_OUTSIDE_COVERAGE;
import static com.tenicana.dronecraft.acoustics.propagation.BoundedAcousticWorldEventTrace.Disposition.WORLD_REPLACED;
import static com.tenicana.dronecraft.acoustics.propagation.BoundedAcousticWorldEventTrace.Kind.BLOCK_APPLY;
import static com.tenicana.dronecraft.acoustics.propagation.BoundedAcousticWorldEventTrace.Kind.CHUNK_UNLOAD;
import static com.tenicana.dronecraft.acoustics.propagation.BoundedAcousticWorldEventTrace.Kind.WORLD_REPLACE;

/** D121q pure-memory trace/ALC evidence reference. */
public final class NativeTraceAlcEvidenceReferenceCli {
	private NativeTraceAlcEvidenceReferenceCli() {
	}

	public static void main(String[] args) throws IOException {
		Locale.setDefault(Locale.ROOT);
		if (args.length != 2) {
			throw new IllegalArgumentException(
					"usage: <output-json> <D121q-contract>"
			);
		}
		Path output = Path.of(args[0]);
		Path contract = Path.of(args[1]);
		BoundedAcousticWorldEventTrace.Entry[] entries = entries();
		NativeEventTraceDiagnosticGate.Decision armed =
				NativeEventTraceDiagnosticGate.evaluate(
						true, true, "null", false, true, true
				);
		NativeEventTraceEvidenceExporter.AlcSnapshot nullAlc =
				new NativeEventTraceEvidenceExporter.AlcSnapshot(
						true, true, "No Output", ""
				);
		String evidence =
				NativeEventTraceEvidenceExporter.referenceJson(
						armed, nullAlc, entries, entries.length, 2L
				);
		boolean unarmedRejected = rejected(
				NativeEventTraceDiagnosticGate.evaluate(
						false, false, null, false, false, false
				),
				nullAlc,
				entries
		);
		boolean physicalRejected = rejected(
				armed,
				new NativeEventTraceEvidenceExporter.AlcSnapshot(
						true, true, "OpenAL Soft on Speakers", ""
				),
				entries
		);
		boolean captureRejected = rejected(
				armed,
				new NativeEventTraceEvidenceExporter.AlcSnapshot(
						true, true, "No Output", "Microphone"
				),
				entries
		);
		boolean inactiveRejected = rejected(
				armed,
				new NativeEventTraceEvidenceExporter.AlcSnapshot(
						false, false, "", ""
				),
				entries
		);
		BoundedAcousticWorldEventTrace.Entry[] reversed = entries();
		BoundedAcousticWorldEventTrace.Entry temporary = reversed[0];
		reversed[0] = reversed[1];
		reversed[1] = temporary;
		boolean nonchronologicalRejected = rejected(
				armed, nullAlc, reversed
		);
		String report = String.format(
				Locale.ROOT,
				"""
				{
				  "schema_version": 1,
				  "status": "valid-native-trace-alc-evidence-reference",
				  "source_contract_sha256": "%s",
				  "evidence": %s,
				  "rejection_probes": {
				    "unarmed_launch": %s,
				    "physical_device_name": %s,
				    "capture_device_present": %s,
				    "inactive_context": %s,
				    "nonchronological_trace": %s
				  },
				  "minecraft_client_started": false,
				  "alc_read_performed": false,
				  "physical_endpoint_opened": false,
				  "captures_audio": false,
				  "native_callback_delivery_measured": false,
				  "cuda_executed": false,
				  "release_calibrated": false
				}
				""",
				sha256(Files.readAllBytes(contract)),
				evidence,
				unarmedRejected,
				physicalRejected,
				captureRejected,
				inactiveRejected,
				nonchronologicalRejected
		);
		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.writeString(output, report, StandardCharsets.UTF_8);
		System.out.printf(
				Locale.ROOT,
				"{\"status\":\"valid-native-trace-alc-evidence-reference\","
						+ "\"retained\":%d,\"all_rejections\":%s}%n",
				entries.length,
				unarmedRejected && physicalRejected && captureRejected
						&& inactiveRejected
						&& nonchronologicalRejected
		);
	}

	private static boolean rejected(
			NativeEventTraceDiagnosticGate.Decision decision,
			NativeEventTraceEvidenceExporter.AlcSnapshot alc,
			BoundedAcousticWorldEventTrace.Entry[] entries
	) {
		try {
			NativeEventTraceEvidenceExporter.referenceJson(
					decision, alc, entries, entries.length, 2L
			);
			return false;
		} catch (IllegalArgumentException | IllegalStateException expected) {
			return true;
		}
	}

	private static BoundedAcousticWorldEventTrace.Entry[] entries() {
		BoundedAcousticWorldEventTrace trace =
				new BoundedAcousticWorldEventTrace(4, true);
		trace.record(
				1, 0, 41, WORLD_REPLACE, WORLD_REPLACED,
				0, 0, 0, 0, 0, 0
		);
		trace.record(
				1, 1, 41, BLOCK_APPLY, ACCEPTED_DIRTY,
				1, 2, 3, 1, 2, 3
		);
		trace.record(
				1, 2, 41, BLOCK_APPLY, ACCEPTED_OUTSIDE_COVERAGE,
				40, 2, 40, 40, 2, 40
		);
		trace.record(
				1, 3, 41, CHUNK_UNLOAD, ACCEPTED_DIRTY,
				0, 0, 0, 15, 15, 15
		);
		BoundedAcousticWorldEventTrace.Entry[] entries =
				new BoundedAcousticWorldEventTrace.Entry[4];
		for (int index = 0; index < entries.length; index++) {
			entries[index] =
					new BoundedAcousticWorldEventTrace.Entry();
		}
		trace.copyChronological(entries);
		return entries;
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(bytes)
			);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
