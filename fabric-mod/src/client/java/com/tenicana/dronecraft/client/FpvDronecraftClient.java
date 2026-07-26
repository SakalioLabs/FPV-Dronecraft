package com.tenicana.dronecraft.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import com.tenicana.dronecraft.client.control.DroneClientControls;
import com.tenicana.dronecraft.client.command.AcousticDiagnosticClientCommands;
import com.tenicana.dronecraft.client.command.NativeEventTraceDiagnosticClientCommands;
import com.tenicana.dronecraft.client.hud.DroneHud;
import com.tenicana.dronecraft.client.render.DroneEntityRenderer;
import com.tenicana.dronecraft.client.render.DroneModelLayers;
import com.tenicana.dronecraft.client.sound.DroneSoundManager;
import com.tenicana.dronecraft.client.sound.AcousticProfileResources;
import com.tenicana.dronecraft.client.sound.MinecraftAcousticWorldDirtyTracker;
import com.tenicana.dronecraft.client.sound.MinecraftNativeEventTraceDiagnosticMode;
import com.tenicana.dronecraft.acoustics.propagation.NativeEventTraceDiagnosticGate;
import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.registry.DroneEntityTypes;

public final class FpvDronecraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		NativeEventTraceDiagnosticGate.Decision traceMode =
				MinecraftNativeEventTraceDiagnosticMode.evaluate();
		if (traceMode.state()
				== NativeEventTraceDiagnosticGate.State.REJECTED) {
			throw new IllegalStateException(
					"native acoustic event trace launch rejected: "
							+ traceMode.reason()
			);
		}
		DroneModelLayers.initialize();
		EntityRendererRegistry.register(DroneEntityTypes.DRONE, DroneEntityRenderer::new);
		DroneClientControls.initialize();
		DroneHud.initialize();
		AcousticProfileResources.initialize();
		MinecraftAcousticWorldDirtyTracker.initialize();
		if (traceMode.armed()) {
			NativeEventTraceDiagnosticClientCommands.initialize();
			FpvDronecraftMod.LOGGER.warn(
					"Native acoustic event trace armed with exclusive "
							+ "OpenAL Soft null backend; DroneSoundManager "
							+ "and general acoustic commands are suppressed"
			);
		} else {
			AcousticDiagnosticClientCommands.initialize();
			DroneSoundManager.initialize();
		}
	}
}
