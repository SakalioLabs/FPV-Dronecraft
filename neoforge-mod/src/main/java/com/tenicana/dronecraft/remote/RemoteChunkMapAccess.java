package com.tenicana.dronecraft.remote;

import net.minecraft.server.level.ServerPlayer;

public interface RemoteChunkMapAccess {
	int fpvdrone$playerViewDistance(ServerPlayer player);
}
