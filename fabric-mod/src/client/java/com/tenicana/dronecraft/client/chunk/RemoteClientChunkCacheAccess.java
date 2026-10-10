package com.tenicana.dronecraft.client.chunk;

/** Remote subscription updates and diagnostics without exposing vanilla storage arrays. */
public interface RemoteClientChunkCacheAccess {

	void fpvdrone$setRemoteCenters(boolean active, int bodyX, int bodyZ, int droneX, int droneZ);

	int fpvdrone$secondaryChunkCount();

	boolean fpvdrone$hasSecondaryChunk(int chunkX, int chunkZ);
}
