package com.tenicana.dronecraft.client.mixin;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class MinecraftAttackSanitizerMixinTest {
	@Test
	void sanitizerRunsBeforeVanillaAttackPacketCanBeSent() throws IOException {
		String source = Files.readString(mixinSource(), StandardCharsets.UTF_8);

		assertTrue(
				source.contains("@Inject(method = \"startAttack\", at = @At(\"HEAD\"), cancellable = true)"),
				"stale drone entity targets must be sanitized before Minecraft sends the attack packet"
		);
		assertTrue(
				source.contains("CallbackInfoReturnable<Boolean>"),
				"startAttack returns boolean, so the sanitizer must be able to cancel the attack cleanly"
		);
		assertTrue(
				source.contains("cir.setReturnValue(false);"),
				"invalid drone entity attacks must be cancelled instead of reaching vanilla attack handling"
		);
		String crosshairInvalidBranch = source.substring(
				source.indexOf("if (fpvdrone$isInvalidDroneTarget(crosshairPickEntity))"),
				source.indexOf("if (invalidAttackTarget)")
		);
		assertTrue(
				crosshairInvalidBranch.contains("invalidAttackTarget = true;"),
				"stale crosshair-only drone targets must also cancel the attack before vanilla can use them"
		);
	}

	@Test
	void sanitizerOnlyDropsInvalidDroneEntityTargets() throws IOException {
		String source = Files.readString(mixinSource(), StandardCharsets.UTF_8);
		String invalidTargetCheck = source.substring(
				source.indexOf("private boolean fpvdrone$isInvalidDroneTarget"),
				source.lastIndexOf("}")
		);

		assertTrue(
				source.contains("hitResult instanceof EntityHitResult entityHit"),
				"the sanitizer should only inspect entity hit results"
		);
		assertTrue(
				invalidTargetCheck.contains("entity instanceof DroneEntity drone"),
				"ordinary entities must not be treated as stale drone camera targets"
		);
		assertTrue(
				invalidTargetCheck.contains("level == null || drone.level() != level || drone.isRemoved() || !drone.isAlive()"),
				"stale drone targets must include null-level, cross-world, removed and dead entities"
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
	void sanitizerIsRegisteredAsClientMixin() throws IOException {
		String mixins = Files.readString(clientMixinsSource(), StandardCharsets.UTF_8);

		assertTrue(
				mixins.contains("\"MinecraftAttackSanitizerMixin\""),
				"the attack sanitizer must be loaded on the client"
		);
	}

	private static Path mixinSource() {
		return locate("src/client/java/com/tenicana/dronecraft/client/mixin/MinecraftAttackSanitizerMixin.java",
				"fabric-mod/src/client/java/com/tenicana/dronecraft/client/mixin/MinecraftAttackSanitizerMixin.java");
	}

	private static Path clientMixinsSource() {
		return locate("src/main/resources/fpvdrone.client.mixins.json",
				"fabric-mod/src/main/resources/fpvdrone.client.mixins.json");
	}

	private static Path locate(String directPath, String childPath) {
		Path current = Path.of("").toAbsolutePath();
		for (int i = 0; i < 8 && current != null; i++) {
			Path direct = current.resolve(directPath);
			if (Files.exists(direct)) {
				return direct;
			}
			Path child = current.resolve(childPath);
			if (Files.exists(child)) {
				return child;
			}
			current = current.getParent();
		}
		fail("Cannot locate " + childPath);
		return Path.of(childPath);
	}
}
