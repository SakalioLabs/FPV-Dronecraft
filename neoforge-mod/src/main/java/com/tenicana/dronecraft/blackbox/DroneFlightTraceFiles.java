package com.tenicana.dronecraft.blackbox;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

public final class DroneFlightTraceFiles {
	public static final String DIRECTORY_NAME = "fpvdiag-traces";

	private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private DroneFlightTraceFiles() {
	}

	public static Path write(Path directory, UUID playerId, LocalDateTime timestamp, String commitSha, String csv) throws IOException {
		Files.createDirectories(directory);
		Path output = directory.resolve(fileName(playerId, timestamp, commitSha));
		Files.writeString(output, csv == null ? "" : csv, StandardCharsets.UTF_8);
		return output;
	}

	public static String fileName(UUID playerId, LocalDateTime timestamp, String commitSha) {
		UUID safePlayerId = playerId == null ? new UUID(0L, 0L) : playerId;
		LocalDateTime safeTimestamp = timestamp == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : timestamp;
		return String.format(
				Locale.ROOT,
				"fpvdiag-%s-%s-%s.csv",
				safeTimestamp.format(FILE_TIME),
				safeCommitSha(commitSha),
				safePlayerId
		);
	}

	public static String safeCommitSha(String commitSha) {
		if (commitSha == null || commitSha.isBlank()) {
			return "unknown";
		}
		StringBuilder safe = new StringBuilder();
		for (int i = 0; i < commitSha.length(); i++) {
			char c = Character.toLowerCase(commitSha.charAt(i));
			if ((c >= 'a' && c <= 'f') || (c >= '0' && c <= '9')) {
				safe.append(c);
			}
		}
		if (safe.length() == 0) {
			return "unknown";
		}
		return safe.substring(0, Math.min(12, safe.length()));
	}
}
