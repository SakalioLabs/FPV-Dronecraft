package com.tenicana.dronecraft.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class DroneEntityModelTest {
	@Test
	void positivePlayablePitchLowersVisibleNoseInLineOfSightView() {
		float playablePitch = (float) Math.toRadians(18.0);
		float modelPitch = DroneEntityModel.bodyPitchRotationRadians(playablePitch);
		float finalForwardYOffset = DroneEntityModel.renderedBodyForwardYOffsetAfterRendererTransform(playablePitch);

		assertTrue(modelPitch < 0.0f);
		assertEquals(-playablePitch, modelPitch, 1.0e-6f);
		assertTrue(finalForwardYOffset < 0.0f);
	}

	@Test
	void negativePlayablePitchRaisesVisibleNoseInLineOfSightView() {
		float playablePitch = (float) Math.toRadians(-18.0);
		float finalForwardYOffset = DroneEntityModel.renderedBodyForwardYOffsetAfterRendererTransform(playablePitch);

		assertTrue(finalForwardYOffset > 0.0f);
		assertEquals(
				-DroneEntityModel.renderedBodyForwardYOffsetAfterRendererTransform(-playablePitch),
				finalForwardYOffset,
				1.0e-6f
		);
	}

	@Test
	void neoForgeClientEventsWireModelRendererAndSoundLifecycles() throws IOException {
		String clientSource = readSource("client/FpvDronecraftClient.java");
		String layersSource = readSource("client/render/DroneModelLayers.java");
		String soundSource = readSource("client/sound/DroneSoundManager.java");

		assertTrue(
				layersSource.contains("public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event)")
						&& layersSource.contains("event.registerLayerDefinition(DRONE, DroneEntityModel::createBodyLayer);"),
				"DroneModelLayers must expose a narrow NeoForge layer-definition registration boundary"
		);

		String layersHandler = eventHandler(clientSource, "public static void onRegisterLayerDefinitions");
		assertTrue(layersHandler.contains("EntityRenderersEvent.RegisterLayerDefinitions event"));
		assertTrue(layersHandler.contains("DroneModelLayers.registerLayerDefinitions(event);"));

		String renderersHandler = eventHandler(clientSource, "public static void onRegisterRenderers");
		assertTrue(renderersHandler.contains("EntityRenderersEvent.RegisterRenderers event"));
		assertTrue(
				renderersHandler.contains("event.registerEntityRenderer(DroneEntityTypes.drone(), DroneEntityRenderer::new);"),
				"the NeoForge renderer event must bind the drone type to its renderer"
		);

		assertTrue(soundSource.contains("public static void onClientTick(Minecraft client)"));
		assertTrue(soundSource.contains("public static void onClientStopping(Minecraft client)"));
		String tickHandler = eventHandler(clientSource, "public static void onClientTick");
		assertTrue(tickHandler.contains("DroneClientControls.onClientTick(client);"));
		assertTrue(tickHandler.contains("DroneSoundManager.onClientTick(client);"));
		String stoppingHandler = eventHandler(clientSource, "public static void onClientStopping");
		assertTrue(stoppingHandler.contains("DroneClientControls.onClientStopping(event.getClient());"));
		assertTrue(stoppingHandler.contains("DroneSoundManager.onClientStopping(event.getClient());"));
	}

	private static String eventHandler(String source, String methodSignature) {
		int methodStart = source.indexOf(methodSignature);
		assertTrue(methodStart >= 0, "missing NeoForge event handler: " + methodSignature);
		int handlerStart = source.lastIndexOf("@SubscribeEvent", methodStart);
		assertTrue(handlerStart >= 0, "event handler must be annotated with @SubscribeEvent: " + methodSignature);
		assertTrue(
				source.substring(handlerStart + "@SubscribeEvent".length(), methodStart).isBlank(),
				"@SubscribeEvent must directly annotate the event handler: " + methodSignature
		);
		int handlerEnd = source.indexOf("@SubscribeEvent", methodStart);
		return source.substring(handlerStart, handlerEnd >= 0 ? handlerEnd : source.length());
	}

	private static String readSource(String relativePath) throws IOException {
		return Files.readString(
				modulePath("src/main/java/com/tenicana/dronecraft/" + relativePath),
				StandardCharsets.UTF_8
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
