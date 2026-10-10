package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.propagation.DirectPathSolver;
import com.tenicana.dronecraft.acoustics.propagation.SparseMaterialSnapshot;
import com.tenicana.dronecraft.acoustics.reverb.ReflectionVolume;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

final class MinecraftReflectionSnapshot {
	private MinecraftReflectionSnapshot() {
	}

	static ReflectionVolume capture(
			ClientLevel level,
			AcousticVector listener,
			int horizontalRadius,
			int verticalRadius,
			long generation
	) {
		if (horizontalRadius < 1 || verticalRadius < 1) {
			throw new IllegalArgumentException(
					"reflection snapshot radii must be positive"
			);
		}
		int listenerX = (int) Math.floor(listener.x());
		int listenerY = (int) Math.floor(listener.y());
		int listenerZ = (int) Math.floor(listener.z());
		int minimumX = listenerX - horizontalRadius;
		int minimumY = listenerY - verticalRadius;
		int minimumZ = listenerZ - horizontalRadius;
		int maximumXExclusive = listenerX + horizontalRadius + 1;
		int maximumYExclusive = listenerY + verticalRadius + 1;
		int maximumZExclusive = listenerZ + horizontalRadius + 1;

		SparseMaterialSnapshot.Builder materials =
				SparseMaterialSnapshot.builder();
		BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
		for (int x = minimumX; x < maximumXExclusive; x++) {
			for (int z = minimumZ; z < maximumZExclusive; z++) {
				if (!level.hasChunk(
						Math.floorDiv(x, 16),
						Math.floorDiv(z, 16)
				)) {
					materials.markIncomplete();
					continue;
				}
				for (int y = minimumY; y < maximumYExclusive; y++) {
					position.set(x, y, z);
					DirectPathSolver.MaterialSample sample =
							MinecraftAcousticMaterials.sample(
									level,
									position,
									level.getBlockState(position)
							);
					if (sample.fillFraction() > 0.0) {
						materials.put(x, y, z, sample);
					}
				}
			}
		}
		SparseMaterialSnapshot snapshot = materials.build();
		return new ReflectionVolume(
				minimumX,
				minimumY,
				minimumZ,
				maximumXExclusive,
				maximumYExclusive,
				maximumZExclusive,
				generation,
				snapshot.complete(),
				snapshot
		);
	}
}
