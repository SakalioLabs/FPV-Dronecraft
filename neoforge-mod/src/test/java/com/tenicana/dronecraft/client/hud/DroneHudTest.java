package com.tenicana.dronecraft.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.client.DroneClientState.InputSource;
import com.tenicana.dronecraft.client.config.DroneClientConfig;

class DroneHudTest {
	@Test
	void idleRpmUsesSemanticDisplayInsteadOfLargeNumericValue() {
		assertTrue(DroneHud.isIdleRpm(true, 0.0f, 13_500.0f));
		assertTrue(DroneHud.isIdleRpm(true, 0.05f, 18_000.0f));
	}

	@Test
	void activeThrottleKeepsNumericRpmVisible() {
		assertFalse(DroneHud.isIdleRpm(true, 0.07f, 13_500.0f));
		assertEquals("13.5k", DroneHud.compactRpmText(true, 13_500.0f));
	}

	@Test
	void disarmedOrStoppedDroneShowsZeroRpm() {
		assertFalse(DroneHud.isIdleRpm(false, 0.0f, 18_000.0f));
		assertFalse(DroneHud.isIdleRpm(true, 0.0f, 0.0f));
		assertEquals("0", DroneHud.compactRpmText(false, 18_000.0f));
		assertEquals("0", DroneHud.compactRpmText(true, 0.0f));
	}

	@Test
	void invalidRpmDoesNotLeakNanIntoHud() {
		assertFalse(DroneHud.isIdleRpm(true, 0.0f, Float.NaN));
		assertEquals("0", DroneHud.compactRpmText(true, Float.NaN));
		assertEquals("0", DroneHud.compactRpmText(true, Float.POSITIVE_INFINITY));
	}

	@Test
	void armReadinessMatchesSharedSafetyRules() {
		assertTrue(DroneHud.isArmReadyForHud(false, 0.02f, 0.0f, 0.0f, 0.0f));
		assertTrue(DroneHud.isArmReadyForHud(false, 0.03f, -0.80f, 0.80f, -0.80f));

		assertFalse(DroneHud.isArmBlockedForHud(true, 0.60f, 0.80f, 0.80f, 0.80f));
		assertFalse(DroneHud.isArmReadyForHud(true, 0.02f, 0.0f, 0.0f, 0.0f));
	}

	@Test
	void armBlockedHintCatchesUnsafeOrInvalidInputs() {
		assertTrue(DroneHud.isArmBlockedForHud(false, 0.42f, 0.0f, 0.0f, 0.0f));
		assertTrue(DroneHud.isArmBlockedForHud(false, 0.02f, 0.38f, 0.0f, 0.0f));
		assertTrue(DroneHud.isArmBlockedForHud(false, Float.NaN, 0.0f, 0.0f, 0.0f));
	}

	@Test
	void inputSourceLabelsUseExistingTranslations() {
		assertEquals("hud.fpvdrone.source_keyboard", DroneHud.inputSourceKey(InputSource.KEYBOARD));
		assertEquals("hud.fpvdrone.source_keyboard", DroneHud.inputSourceKey(null));
		assertEquals("hud.fpvdrone.source_gamepad", DroneHud.inputSourceKey(InputSource.GAMEPAD));
	}

	@Test
	void controlFeelLabelsReuseControllerPresetTranslations() {
		assertEquals("screen.fpvdrone.feel_training", DroneHud.controlFeelKey(null));
		assertEquals("screen.fpvdrone.feel_training", DroneHud.controlFeelKey(DroneClientConfig.ControlFeelPreset.TRAINING));
		assertEquals("screen.fpvdrone.feel_sport", DroneHud.controlFeelKey(DroneClientConfig.ControlFeelPreset.SPORT));
		assertEquals("screen.fpvdrone.feel_acro", DroneHud.controlFeelKey(DroneClientConfig.ControlFeelPreset.ACRO));
		assertEquals("screen.fpvdrone.feel_custom", DroneHud.controlFeelKey(DroneClientConfig.ControlFeelPreset.CUSTOM));
	}

	@Test
	void throttleCalibrationLabelsPrioritizeActiveCalibration() {
		assertEquals("hud.fpvdrone.throttle_calibrating", DroneHud.throttleCalibrationKey(true, true));
		assertEquals("hud.fpvdrone.throttle_calibrating", DroneHud.throttleCalibrationKey(false, true));
		assertEquals("hud.fpvdrone.throttle_calibrated", DroneHud.throttleCalibrationKey(true, false));
		assertEquals("hud.fpvdrone.throttle_uncalibrated", DroneHud.throttleCalibrationKey(false, false));
	}

	@Test
	void minimalHudOnlyShowsThrottleCalibrationWhenActionable() {
		assertFalse(DroneHud.shouldShowMinimalThrottleCalibrationStatus(InputSource.KEYBOARD, true, false));
		assertFalse(DroneHud.shouldShowMinimalThrottleCalibrationStatus(InputSource.GAMEPAD, true, false));
		assertTrue(DroneHud.shouldShowMinimalThrottleCalibrationStatus(InputSource.KEYBOARD, false, false));
		assertTrue(DroneHud.shouldShowMinimalThrottleCalibrationStatus(InputSource.GAMEPAD, true, true));
	}

	@Test
	void fullHudShowsGamepadThrottleCalibrationState() {
		assertFalse(DroneHud.shouldShowThrottleCalibrationStatus(InputSource.KEYBOARD, true, false));
		assertTrue(DroneHud.shouldShowThrottleCalibrationStatus(InputSource.GAMEPAD, true, false));
		assertTrue(DroneHud.shouldShowThrottleCalibrationStatus(InputSource.KEYBOARD, false, false));
		assertTrue(DroneHud.shouldShowThrottleCalibrationStatus(InputSource.KEYBOARD, true, true));
	}

	@Test
	void fullHudShowsControlFeelOnlyForGamepadInput() {
		assertFalse(DroneHud.shouldShowControlFeelStatus(InputSource.KEYBOARD));
		assertFalse(DroneHud.shouldShowControlFeelStatus(null));
		assertTrue(DroneHud.shouldShowControlFeelStatus(InputSource.GAMEPAD));
	}

	@Test
	void neoForgeGuiLayerEventDelegatesToHudRegistrationBoundary() throws IOException {
		String hudSource = Files.readString(modulePath("src/main/java/com/tenicana/dronecraft/client/hud/DroneHud.java"), StandardCharsets.UTF_8);
		String clientSource = Files.readString(modulePath("src/main/java/com/tenicana/dronecraft/client/FpvDronecraftClient.java"), StandardCharsets.UTF_8)
				.replace("\r\n", "\n");

		assertTrue(
				hudSource.contains("public static void registerGuiLayers(RegisterGuiLayersEvent event)")
						&& hudSource.contains("event.registerAbove(VanillaGuiLayers.CROSSHAIR, HUD_ID, DroneHud::render);"),
				"DroneHud must expose one narrow RegisterGuiLayersEvent boundary while keeping rendering internal"
		);
		assertTrue(
				clientSource.contains("@SubscribeEvent\n\tpublic static void onRegisterGuiLayers(RegisterGuiLayersEvent event)")
						&& clientSource.contains("DroneHud.registerGuiLayers(event);"),
				"the client-only NeoForge event subscriber must delegate GUI layer registration to DroneHud"
		);
	}

	private static Path modulePath(String relativePath) {
		return neoForgeModuleRoot().resolve(relativePath);
	}

	private static Path neoForgeModuleRoot() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		while (current != null) {
			if (current.getFileName() != null
					&& current.getFileName().toString().equals("neoforge-mod")
					&& Files.isDirectory(current.resolve("src"))) {
				return current;
			}
			Path child = current.resolve("neoforge-mod");
			if (Files.isDirectory(child.resolve("src"))) {
				return child;
			}
			current = current.getParent();
		}
		fail("Cannot locate neoforge-mod from user.dir");
		return Path.of("neoforge-mod");
	}
}
