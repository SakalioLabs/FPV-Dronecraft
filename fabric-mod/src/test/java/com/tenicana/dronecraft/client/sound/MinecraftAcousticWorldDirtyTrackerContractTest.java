package com.tenicana.dronecraft.client.sound;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftAcousticWorldDirtyTrackerContractTest {
	@Test
	void adapterUsesNativeLifecycleEventsAndBoundedCoverage() throws IOException {
		String source = Files.readString(
				locate(
						"src/client/java/com/tenicana/dronecraft/client/sound/"
								+ "MinecraftAcousticWorldDirtyTracker.java",
						"fabric-mod/src/client/java/com/tenicana/dronecraft/"
								+ "client/sound/"
								+ "MinecraftAcousticWorldDirtyTracker.java"
				),
				StandardCharsets.UTF_8
		);

		assertTrue(source.contains("ClientChunkEvents.CHUNK_LOAD.register"));
		assertTrue(source.contains("ClientChunkEvents.CHUNK_UNLOAD.register"));
		assertTrue(source.contains(
				"ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register"
		));
		assertTrue(source.contains("MAXIMUM_ACTIVE_COVERAGES = 4"));
		assertTrue(source.contains("CanonicalAcousticWorldEventPort PORT"));
		assertTrue(source.contains("PORT.acceptBlock("));
		assertTrue(source.contains("PORT.acceptChunk("));
		assertTrue(source.contains("PORT.replaceWorld(worldEpoch)"));
		assertTrue(source.contains("nextSequence()"));
		assertFalse(source.contains("tracker.markDirty("));
		assertTrue(source.contains("NATIVE_EVENT_TRACE_CAPACITY = 256"));
		assertTrue(source.contains(
				"fpvdrone.acoustics.nativeEventTrace"
		));
		assertTrue(source.contains("NATIVE_EVENT_TRACE.enabled()"));
		assertTrue(source.contains("Thread.currentThread().threadId()"));
		assertTrue(source.contains("copyNativeEventTrace("));
		assertFalse(source.contains("OpenAL"));
		assertFalse(source.contains("SoundEngine"));
	}

	@Test
	void successfulClientLevelSetBlockIsTheOnlyBlockHook()
			throws IOException {
		String mixin = Files.readString(
				locate(
						"src/client/java/com/tenicana/dronecraft/client/mixin/"
								+ "ClientLevelAcousticDirtyMixin.java",
						"fabric-mod/src/client/java/com/tenicana/dronecraft/"
								+ "client/mixin/"
								+ "ClientLevelAcousticDirtyMixin.java"
				),
				StandardCharsets.UTF_8
		);
		String mixinConfig = Files.readString(
				locate(
						"src/main/resources/fpvdrone.client.mixins.json",
						"fabric-mod/src/main/resources/"
								+ "fpvdrone.client.mixins.json"
				),
				StandardCharsets.UTF_8
		);

		assertTrue(mixin.contains(
				"@Inject(method = \"setBlock\", at = @At(\"RETURN\"))"
		));
		assertTrue(mixin.contains(
				"Boolean.TRUE.equals(callback.getReturnValue())"
		));
		assertTrue(mixinConfig.contains(
				"\"ClientLevelAcousticDirtyMixin\""
		));
	}

	private static Path locate(String local, String root) {
		Path localPath = Path.of(local);
		return Files.isRegularFile(localPath) ? localPath : Path.of(root);
	}
}
