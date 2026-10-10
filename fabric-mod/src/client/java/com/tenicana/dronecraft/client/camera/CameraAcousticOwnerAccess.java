package com.tenicana.dronecraft.client.camera;

import net.minecraft.world.entity.Entity;

/** Describes the owner of the pose already installed in Camera.setup. */
public interface CameraAcousticOwnerAccess {
	Entity fpvdrone$acousticVelocityOwner();

	int fpvdrone$acousticViewMode();
}
