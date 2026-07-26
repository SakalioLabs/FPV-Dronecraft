package com.tenicana.dronecraft.acoustics.propagation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.tenicana.dronecraft.acoustics.propagation.BoundedAcousticWorldEventTrace.Disposition.ACCEPTED_DIRTY;
import static com.tenicana.dronecraft.acoustics.propagation.BoundedAcousticWorldEventTrace.Kind.BLOCK_APPLY;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeEventTraceEvidenceExporterTest {
	@TempDir
	Path temporary;

	@Test
	void writesOnlyValidatedNullBackendEvidence() throws IOException {
		BoundedAcousticWorldEventTrace.Entry[] entries = entries();
		Path output = temporary.resolve("valid.json");
		NativeEventTraceEvidenceExporter.write(
				output,
				armed(),
				new NativeEventTraceEvidenceExporter.AlcSnapshot(
						true, true, "No Output", ""
				),
				entries,
				entries.length,
				3L,
				true
		);
		String json = Files.readString(output);
		assertTrue(json.contains(
				"\"status\": \"valid-native-trace-alc-evidence\""
		));
		assertTrue(json.contains("\"minecraft_client_started\": true"));
		assertTrue(json.contains("\"physical_endpoint_opened\": false"));
		assertTrue(json.contains("\"ordinal\":1"));
		assertTrue(json.contains("\"ordinal\":2"));
	}

	@Test
	void rejectsBeforeCreatingOutput() {
		assertRejected(
				"physical.json",
				armed(),
				new NativeEventTraceEvidenceExporter.AlcSnapshot(
						true, true, "OpenAL Soft on Speakers", ""
				)
		);
		assertRejected(
				"capture.json",
				armed(),
				new NativeEventTraceEvidenceExporter.AlcSnapshot(
						true, true, "No Output", "Microphone"
				)
		);
		assertRejected(
				"unarmed.json",
				NativeEventTraceDiagnosticGate.evaluate(
						false, false, null, false, false, false
				),
				new NativeEventTraceEvidenceExporter.AlcSnapshot(
						true, true, "No Output", ""
				)
		);
	}

	private void assertRejected(
			String filename,
			NativeEventTraceDiagnosticGate.Decision decision,
			NativeEventTraceEvidenceExporter.AlcSnapshot snapshot
	) {
		Path output = temporary.resolve(filename);
		assertThrows(
				IllegalStateException.class,
				() -> NativeEventTraceEvidenceExporter.write(
						output,
						decision,
						snapshot,
						entries(),
						2,
						0,
						true
				)
		);
		assertFalse(Files.exists(output));
	}

	private static NativeEventTraceDiagnosticGate.Decision armed() {
		return NativeEventTraceDiagnosticGate.evaluate(
				true, true, "null", false, true, true
		);
	}

	private static BoundedAcousticWorldEventTrace.Entry[] entries() {
		BoundedAcousticWorldEventTrace trace =
				new BoundedAcousticWorldEventTrace(2, true);
		trace.record(
				1, 1, 7, BLOCK_APPLY, ACCEPTED_DIRTY,
				1, 2, 3, 1, 2, 3
		);
		trace.record(
				1, 2, 7, BLOCK_APPLY, ACCEPTED_DIRTY,
				2, 2, 3, 2, 2, 3
		);
		BoundedAcousticWorldEventTrace.Entry[] entries = {
			new BoundedAcousticWorldEventTrace.Entry(),
			new BoundedAcousticWorldEventTrace.Entry()
		};
		trace.copyChronological(entries);
		return entries;
	}
}
