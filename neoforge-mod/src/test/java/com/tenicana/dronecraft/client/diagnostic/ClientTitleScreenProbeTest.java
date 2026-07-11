package com.tenicana.dronecraft.client.diagnostic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClientTitleScreenProbeTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void writesACompletePassingReportAtomically() throws Exception {
		Path report = temporaryDirectory.resolve("nested/report.json");
		String runId = UUID.randomUUID().toString();

		ClientTitleScreenProbe.writeReport(report, runId, 20, true, true);

		JsonObject json = JsonParser.parseString(Files.readString(report, StandardCharsets.UTF_8)).getAsJsonObject();
		assertEquals(1, json.get("schema_version").getAsInt());
		assertEquals(runId, json.get("run_id").getAsString());
		assertTrue(json.get("passed").getAsBoolean());
		assertTrue(json.get("title_screen_ready").getAsBoolean());
		assertTrue(json.get("controller_model_ready").getAsBoolean());
		assertEquals(20, json.get("stable_ticks").getAsInt());
		assertFalse(Files.exists(report.resolveSibling("report.json.tmp")));
	}

	@Test
	void neverMarksAnIncompleteObservationAsPassing() throws Exception {
		Path report = temporaryDirectory.resolve("report.json");

		ClientTitleScreenProbe.writeReport(report, UUID.randomUUID().toString(), 19, true, true);

		JsonObject json = JsonParser.parseString(Files.readString(report)).getAsJsonObject();
		assertFalse(json.get("passed").getAsBoolean());
	}
}
