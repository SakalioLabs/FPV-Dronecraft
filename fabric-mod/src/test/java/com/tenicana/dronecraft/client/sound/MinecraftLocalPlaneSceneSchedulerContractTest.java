package com.tenicana.dronecraft.client.sound;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftLocalPlaneSceneSchedulerContractTest {
	@Test
	void schedulerIsOptInBoundedAndSnapshotOnly() throws IOException {
		String source = read(
				"src/client/java/com/tenicana/dronecraft/client/sound/"
						+ "MinecraftLocalPlaneSceneScheduler.java",
				"fabric-mod/src/client/java/com/tenicana/dronecraft/"
						+ "client/sound/"
						+ "MinecraftLocalPlaneSceneScheduler.java"
		);

		assertTrue(source.contains(
				"fpvdrone.acoustics.localPlaneSchedulerResearch"
		));
		assertTrue(source.contains(
				"enabled = Boolean.getBoolean(ENABLE_PROPERTY)"
		));
		assertTrue(source.contains("MAXIMUM_SOURCES = 6"));
		assertTrue(source.contains(
				"MinecraftAcousticWorldDirtyTracker.replaceActiveCoverage"
		));
		assertTrue(source.contains("LocalPlaneSnapshotCache.Lookup"));
		assertTrue(source.contains("handoff.submit("));
		assertFalse(source.contains("OpenAL"));
		assertFalse(source.contains("SoundManager"));
		assertFalse(source.contains("DroneLoopSoundInstance"));
	}

	@Test
	void managerDoesNotApplyEarlyResultsToAudio() throws IOException {
		String manager = read(
				"src/client/java/com/tenicana/dronecraft/client/sound/"
						+ "DroneSoundManager.java",
				"fabric-mod/src/client/java/com/tenicana/dronecraft/"
						+ "client/sound/DroneSoundManager.java"
		);

		assertTrue(manager.contains(
				"internalPropagation && LOCAL_PLANE_RESEARCH.enabled()"
		));
		assertTrue(manager.contains("LOCAL_PLANE_RESEARCH.tick("));
		assertTrue(manager.contains("LOCAL_PLANE_RESEARCH.close()"));
		assertFalse(manager.contains(
				"LOCAL_PLANE_RESEARCH.apply"
		));
	}

	private static String read(String local, String root) throws IOException {
		Path localPath = Path.of(local);
		Path path = Files.isRegularFile(localPath)
				? localPath
				: Path.of(root);
		return Files.readString(path, StandardCharsets.UTF_8);
	}
}
