package com.tenicana.dronecraft.sound;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class DroneSoundResourcesTest {
	private static final Path ASSET_ROOT = assetRoot();
	private static final byte[] OGG_MAGIC = {'O', 'g', 'g', 'S'};
	private static final byte[] VORBIS_MARKER = {1, 'v', 'o', 'r', 'b', 'i', 's'};

	@Test
	void soundManifestDefinesEverySimulationLayer() throws IOException {
		String json = Files.readString(ASSET_ROOT.resolve("sounds.json"), StandardCharsets.UTF_8);
		JsonObject sounds = JsonParser.parseString(json).getAsJsonObject();

		assertTrue(sounds.keySet().containsAll(Set.of(
				"drone.motor_loop",
				"drone.propeller_loop",
				"drone.impact"
		)));
	}

	@Test
	void positionalAssetsArePresentOggStreams() throws IOException {
		Set<Path> expected = Set.of(
				Path.of("sounds/drone/motor_loop.ogg"),
				Path.of("sounds/drone/propeller_loop.ogg"),
				Path.of("sounds/drone/impact_1.ogg"),
				Path.of("sounds/drone/impact_2.ogg"),
				Path.of("sounds/drone/impact_3.ogg")
		);
		Set<Path> referenced = referencedSoundAssets();
		assertEquals(expected, referenced, "sounds.json must reference exactly the shipped drone assets");

		for (Path relativeAsset : referenced) {
			Path asset = ASSET_ROOT.resolve(relativeAsset);
			String name = relativeAsset.getFileName().toString();
			assertTrue(Files.size(asset) > OGG_MAGIC.length, name + " must not be empty");
			byte[] header = new byte[64];
			try (var input = Files.newInputStream(asset)) {
				int read = input.read(header);
				assertTrue(read == header.length, name + " must contain an Ogg/Vorbis header");
			}
			assertArrayEquals(OGG_MAGIC, java.util.Arrays.copyOf(header, OGG_MAGIC.length), name + " must be Ogg");
			assertTrue(contains(header, VORBIS_MARKER), name + " must contain a Vorbis identification header");
		}
	}

	private static Set<Path> referencedSoundAssets() throws IOException {
		String json = Files.readString(ASSET_ROOT.resolve("sounds.json"), StandardCharsets.UTF_8);
		JsonObject events = JsonParser.parseString(json).getAsJsonObject();
		Set<Path> assets = new HashSet<>();
		for (var event : events.entrySet()) {
			for (var sound : event.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
				JsonObject definition = sound.getAsJsonObject();
				String name = definition.get("name").getAsString();
				assertTrue(name.startsWith("fpvdrone:"), "sound must use the fpvdrone namespace: " + name);
				assertTrue(definition.get("attenuation_distance").getAsInt() > 0, "sound must define attenuation: " + name);
				assets.add(Path.of("sounds").resolve(name.substring("fpvdrone:".length()) + ".ogg"));
			}
		}
		return assets;
	}

	private static boolean contains(byte[] haystack, byte[] needle) {
		for (int start = 0; start <= haystack.length - needle.length; start++) {
			boolean match = true;
			for (int index = 0; index < needle.length; index++) {
				if (haystack[start + index] != needle[index]) {
					match = false;
					break;
				}
			}
			if (match) {
				return true;
			}
		}
		return false;
	}

	private static Path assetRoot() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		while (current != null) {
			Path direct = current.resolve("src/main/resources/assets/fpvdrone");
			if (Files.exists(direct)) {
				return direct;
			}
			Path child = current.resolve("neoforge-mod/src/main/resources/assets/fpvdrone");
			if (Files.exists(child)) {
				return child;
			}
			current = current.getParent();
		}
		throw new IllegalStateException("Cannot locate NeoForge FPV Dronecraft assets");
	}
}
