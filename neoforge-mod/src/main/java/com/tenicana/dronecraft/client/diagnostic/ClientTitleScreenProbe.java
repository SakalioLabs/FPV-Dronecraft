package com.tenicana.dronecraft.client.diagnostic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;

import com.tenicana.dronecraft.FpvDronecraftMod;

public final class ClientTitleScreenProbe {
	static final String ENABLED_PROPERTY = "fpvdrone.client_smoke";
	static final String RUN_ID_PROPERTY = "fpvdrone.client_smoke.run_id";
	static final String REPORT_DIRECTORY = "fpvdrone-client-smoke";
	static final String REPORT_FILE = "report.json";
	private static final int REQUIRED_STABLE_TICKS = 20;
	private static final ResourceLocation CONTROLLER_MODEL =
			ResourceLocation.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID, "models/item/drone_controller.json");

	private static boolean initialized;
	private static boolean enabled;
	private static String runId;
	private static int stableTicks;
	private static boolean finished;
	private static long startedAt;

	private ClientTitleScreenProbe() {
	}

	public static void onClientTick(Minecraft client) {
		initialize();
		if (!enabled || finished) {
			return;
		}

		// Fresh, isolated development runs have no accessibility preference yet.
		// The explicit smoke-test launch may dismiss that initial prompt after loading.
		if (client.getOverlay() == null && client.screen != null
				&& client.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen")) {
			client.setScreen(new TitleScreen());
		}
		boolean titleScreenReady = client.screen instanceof TitleScreen && client.getOverlay() == null;
		boolean resourceReady = client.getResourceManager().getResource(CONTROLLER_MODEL).isPresent()
				&& ClientModelReadiness.controllerModelReady(client);
		boolean cncReady = ClientModelReadiness.cncModelsReady(client);
		stableTicks = titleScreenReady && resourceReady && cncReady ? stableTicks + 1 : 0;
		if (stableTicks < REQUIRED_STABLE_TICKS && System.nanoTime() - startedAt < 180_000_000_000L) {
			return;
		}

		finished = true;
		Path report = client.gameDirectory.toPath().resolve(REPORT_DIRECTORY).resolve(REPORT_FILE);
		try {
			writeReport(report, runId, stableTicks, titleScreenReady, resourceReady, cncReady);
			FpvDronecraftMod.LOGGER.info("FPV Dronecraft client startup evidence: {} (title={}, bakedController={}, stableTicks={})",
					report, titleScreenReady, resourceReady, stableTicks);
			client.stop();
		} catch (IOException exception) {
			FpvDronecraftMod.LOGGER.error("Could not write FPV Dronecraft production client smoke report", exception);
			client.stop();
		}
	}

	static void writeReport(Path report, String reportRunId, int reportStableTicks,
			boolean titleScreenReady, boolean resourceReady, boolean cncReady) throws IOException {
		JsonObject json = new JsonObject();
		json.addProperty("schema_version", 1);
		json.addProperty("run_id", reportRunId);
		json.addProperty("passed", titleScreenReady && resourceReady && cncReady && reportStableTicks >= REQUIRED_STABLE_TICKS);
		json.addProperty("title_screen_ready", titleScreenReady);
		json.addProperty("controller_model_ready", resourceReady);
		json.addProperty("cnc_and_carbon_plate_models_ready", cncReady);
		json.addProperty("stable_ticks", reportStableTicks);

		Files.createDirectories(report.getParent());
		Path temporary = report.resolveSibling(report.getFileName() + ".tmp");
		Files.writeString(temporary, json.toString() + System.lineSeparator(), StandardCharsets.UTF_8);
		try {
			Files.move(temporary, report, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(temporary, report, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void initialize() {
		if (initialized) {
			return;
		}
		initialized = true;
		enabled = Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "false"));
		startedAt = System.nanoTime();
		if (!enabled) {
			return;
		}
		String configuredRunId = System.getProperty(RUN_ID_PROPERTY, "").trim();
		try {
			runId = UUID.fromString(configuredRunId).toString();
		} catch (IllegalArgumentException exception) {
			enabled = false;
			FpvDronecraftMod.LOGGER.error("FPV Dronecraft client smoke requires a valid UUID in -D{}", RUN_ID_PROPERTY);
		}
	}
}
