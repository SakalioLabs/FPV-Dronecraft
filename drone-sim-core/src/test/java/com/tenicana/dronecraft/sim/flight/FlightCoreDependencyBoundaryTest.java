package com.tenicana.dronecraft.sim.flight;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class FlightCoreDependencyBoundaryTest {
	private static final List<String> FORBIDDEN_DEPENDENCIES = List.of(
			"net.minecraft",
			"net.fabricmc",
			"net.neoforged",
			"com.mojang",
			"Vec3d",
			"Entity"
	);

	@Test
	void flightContractPackageHasNoMinecraftOrLoaderDependencies() throws IOException {
		Path flightPackage = projectDir().resolve("src/main/java/com/tenicana/dronecraft/sim/flight");
		try (var files = Files.walk(flightPackage)) {
			for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
				String source = Files.readString(file, StandardCharsets.UTF_8);
				for (String forbidden : FORBIDDEN_DEPENDENCIES) {
					assertFalse(source.contains(forbidden), () -> file + " must not depend on " + forbidden);
				}
			}
		}
	}

	private static Path projectDir() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		while (current != null) {
			Path direct = current.resolve("src/main/java/com/tenicana/dronecraft/sim/DronePhysics.java");
			if (Files.exists(direct)) {
				return current;
			}
			Path child = current.resolve("drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DronePhysics.java");
			if (Files.exists(child)) {
				return current.resolve("drone-sim-core");
			}
			current = current.getParent();
		}
		fail("Cannot locate drone-sim-core project directory");
		return Path.of(".");
	}
}
