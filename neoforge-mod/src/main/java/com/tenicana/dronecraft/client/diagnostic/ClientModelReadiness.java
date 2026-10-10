package com.tenicana.dronecraft.client.diagnostic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;

import com.tenicana.dronecraft.FpvDronecraftMod;

/** Native baked model lookup, replaced by later-version item-model adapters. */
public final class ClientModelReadiness {
	private ClientModelReadiness() { }

	public static boolean controllerModelReady(Minecraft client) {
		var manager = client.getModelManager();
		var model = manager.getModel(ModelResourceLocation.inventory(
				ResourceLocation.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID, "drone_controller")));
		return model != null && model != manager.getMissingModel()
				&& !model.getQuads(null, null, net.minecraft.util.RandomSource.create(0L)).isEmpty();
	}
}
