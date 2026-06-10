package com.tenicana.dronecraft.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import com.tenicana.dronecraft.control.DroneControlManager;
import com.tenicana.dronecraft.sim.DroneInput;
import com.tenicana.dronecraft.sim.FlightMode;

public final class DroneNetworking {
	private DroneNetworking() {
	}

	public static void initialize() {
		PayloadTypeRegistry.playC2S().register(DroneControlPayload.TYPE, DroneControlPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(DroneControlPayload.TYPE, (payload, context) -> {
			DroneControlManager.update(
					context.player().getUUID(),
					new DroneInput(payload.throttle(), payload.pitch(), payload.roll(), payload.yaw(), payload.armed(), true, FlightMode.byId(payload.flightMode())),
					context.player().tickCount
			);
		});
	}
}
