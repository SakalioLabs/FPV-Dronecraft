package com.tenicana.dronecraft.client.mixincontract;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class MinecraftAttackSanitizerMixinTest {
	@Test
	void sanitizerRunsBeforeVanillaAttackPacketCanBeSent() throws IOException {
		String source = Files.readString(mixinSource(), StandardCharsets.UTF_8);

		assertTrue(
				source.contains("@Inject(method = \"startAttack\", at = @At(\"HEAD\"), cancellable = true)"),
				"stale entity targets must be sanitized before Minecraft sends the attack packet"
		);
		assertTrue(
				source.contains("CallbackInfoReturnable<Boolean>"),
				"startAttack returns boolean, so the sanitizer must be able to cancel the attack cleanly"
		);
		assertTrue(
				source.contains("@Inject(method = \"startUseItem\", at = @At(\"HEAD\"), cancellable = true)"),
				"stale entity targets must also be sanitized before vanilla sends use/interact packets"
		);
		assertTrue(
				source.contains("CallbackInfo ci"),
				"startUseItem returns void, so the sanitizer must be able to cancel use handling cleanly"
		);
		assertTrue(
				source.contains("cir.setReturnValue(false);"),
				"invalid drone entity attacks must be cancelled instead of reaching vanilla attack handling"
		);
		assertTrue(
				source.contains("ci.cancel();"),
				"invalid drone entity use interactions must be cancelled instead of reaching vanilla use handling"
		);
		String crosshairInvalidBranch = source.substring(
				source.indexOf("if (fpvdrone$isInvalidEntityTarget(crosshairPickEntity))"),
				source.indexOf("return invalidTarget;")
		);
		assertTrue(
				crosshairInvalidBranch.contains("invalidTarget = true;"),
				"stale crosshair-only drone targets must also cancel before vanilla can use them"
		);
	}

	@Test
	void sanitizerOnlyDropsInvalidEntityTargets() throws IOException {
		String source = Files.readString(mixinSource(), StandardCharsets.UTF_8);
		String invalidTargetCheck = source.substring(
				source.indexOf("private boolean fpvdrone$isInvalidEntityTarget"),
				source.lastIndexOf("}")
		);

		assertTrue(
				source.contains("private boolean fpvdrone$clearInvalidEntityTarget()"),
				"attack and use sanitizers should share the same stale-target clearing path"
		);
		assertTrue(
				source.contains("hitResult instanceof EntityHitResult entityHit"),
				"the sanitizer should only inspect entity hit results"
		);
		assertTrue(
				invalidTargetCheck.contains("entity != null"),
				"null hit targets must be ignored"
		);
		assertTrue(
				invalidTargetCheck.contains("ClientCameraSafety.isInvalidEntityTarget(")
						&& invalidTargetCheck.contains("level != null")
						&& invalidTargetCheck.contains("entity != null && entity.level() == level")
						&& invalidTargetCheck.contains("entity != null && entity.isRemoved()")
						&& invalidTargetCheck.contains("entity != null && entity.isAlive()"),
				"stale targets must include null-level, cross-world, removed and dead entities"
		);
		assertTrue(
				source.contains("hitResult = null;"),
				"invalid stale entity hit results must be cleared before the cancelled attack returns"
		);
		assertTrue(
				source.contains("crosshairPickEntity = null;"),
				"stale crosshair drone entity picks must be cleared with the hit result"
		);
	}

	@Test
	void neoForgeMetadataKeepsAllSafetyMixinsClientOnly() throws IOException {
		String metadata = Files.readString(modulePath("src/main/templates/META-INF/neoforge.mods.toml"), StandardCharsets.UTF_8);
		JsonObject config = JsonParser.parseString(
				Files.readString(modulePath("src/main/resources/fpvdrone.client.mixins.json"), StandardCharsets.UTF_8)
		).getAsJsonObject();
		assertTrue(config.has("client"), "client safety mixins must be declared in the client-only config section");
		Set<String> clientMixins = new HashSet<>();
		config.getAsJsonArray("client").forEach(element -> clientMixins.add(element.getAsString()));

		assertTrue(
				metadata.contains("[[mixins]]") && metadata.contains("config=\"fpvdrone.client.mixins.json\""),
				"NeoForge metadata must register the dedicated client mixin config"
		);
		assertFalse(config.has("mixins"), "client safety mixins must not be loaded from the common mixin section");
		assertFalse(config.has("server"), "the client mixin config must not declare server mixins");
		assertTrue(
				clientMixins.containsAll(Set.of("CameraMixin", "GameRendererMixin", "MinecraftAttackSanitizerMixin")),
				"the client config must include all three concrete safety mixins"
		);

		assertTrue(readSource("CameraMixin.java").contains("@Mixin(Camera.class)"), "CameraMixin must target the client Camera class");
		assertTrue(readSource("GameRendererMixin.java").contains("@Mixin(GameRenderer.class)"), "GameRendererMixin must target the client GameRenderer class");
		assertTrue(readSource("MinecraftAttackSanitizerMixin.java").contains("@Mixin(Minecraft.class)"), "the sanitizer must target the client Minecraft class");
	}

	private static String readSource(String fileName) throws IOException {
		return Files.readString(
				modulePath("src/main/java/com/tenicana/dronecraft/client/mixin/" + fileName),
				StandardCharsets.UTF_8
		);
	}

	private static Path mixinSource() {
		return modulePath("src/main/java/com/tenicana/dronecraft/client/mixin/MinecraftAttackSanitizerMixin.java");
	}

	private static Path modulePath(String relativePath) {
		return neoForgeModuleRoot().resolve(relativePath);
	}

	private static Path neoForgeModuleRoot() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		while (current != null) {
			if (current.getFileName() != null
					&& current.getFileName().toString().equals("neoforge-mod")
					&& Files.isDirectory(current.resolve("src/main"))) {
				return current;
			}
			Path child = current.resolve("neoforge-mod");
			if (Files.isDirectory(child.resolve("src/main"))) {
				return child;
			}
			current = current.getParent();
		}
		fail("Cannot locate neoforge-mod from user.dir");
		return Path.of("neoforge-mod");
	}
}
