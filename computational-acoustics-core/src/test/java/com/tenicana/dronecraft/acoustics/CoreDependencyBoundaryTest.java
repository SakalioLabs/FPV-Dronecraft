package com.tenicana.dronecraft.acoustics;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreDependencyBoundaryTest {
	private static final List<String> FORBIDDEN = List.of(
			"net.minecraft",
			"net.fabricmc",
			"com.mojang",
			"org.lwjgl"
	);

	@Test
	void productionSourcesStayEngineIndependent() throws IOException {
		Path sourceRoot = Path.of("src", "main", "java");
		try (var paths = Files.walk(sourceRoot)) {
			List<String> violations = paths
					.filter(path -> path.toString().endsWith(".java"))
					.flatMap(path -> violations(path).stream())
					.toList();
			assertTrue(violations.isEmpty(), () -> "Forbidden engine dependencies: " + violations);
		}
	}

	private static List<String> violations(Path source) {
		try {
			String text = Files.readString(source);
			return FORBIDDEN.stream()
					.filter(text::contains)
					.map(token -> source + " -> " + token)
					.toList();
		} catch (IOException exception) {
			throw new IllegalStateException("Could not inspect " + source, exception);
		}
	}
}
