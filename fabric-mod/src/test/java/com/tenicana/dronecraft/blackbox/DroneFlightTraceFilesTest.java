package com.tenicana.dronecraft.blackbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DroneFlightTraceFilesTest {
	@TempDir
	Path tempDir;

	@Test
	void traceFileNameIncludesTimeCommitAndPlayer() {
		UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000123");

		String fileName = DroneFlightTraceFiles.fileName(
				playerId,
				LocalDateTime.of(2026, 6, 21, 18, 30, 45),
				"603CF117"
		);

		assertEquals("fpvdiag-20260621-183045-603cf117-00000000-0000-0000-0000-000000000123.csv", fileName);
	}

	@Test
	void writeCreatesDirectoryAndCsvFile() throws Exception {
		UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000456");
		Path directory = tempDir.resolve(DroneFlightTraceFiles.DIRECTORY_NAME);

		Path output = DroneFlightTraceFiles.write(
				directory,
				playerId,
				LocalDateTime.of(2026, 6, 21, 19, 1, 2),
				"abcdef1234567890",
				"tick,value\n1,2\n"
		);

		assertTrue(output.startsWith(directory));
		assertTrue(Files.exists(output));
		assertEquals("tick,value\n1,2\n", Files.readString(output, StandardCharsets.UTF_8));
		assertTrue(output.getFileName().toString().contains("abcdef123456"));
	}
}
