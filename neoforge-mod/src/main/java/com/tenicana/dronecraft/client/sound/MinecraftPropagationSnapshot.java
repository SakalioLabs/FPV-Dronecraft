package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.path.SparseAirGrid;
import com.tenicana.dronecraft.acoustics.propagation.SparseMaterialSnapshot;
import com.tenicana.dronecraft.acoustics.voxel.VoxelDda;

import java.util.Objects;
import java.util.Optional;

record MinecraftPropagationSnapshot(
		SparseMaterialSnapshot materials,
		Optional<SparseAirGrid> airGrid,
		VoxelDda.Cell sourceCell,
		VoxelDda.Cell listenerCell
) {
	MinecraftPropagationSnapshot {
		Objects.requireNonNull(materials, "materials");
		Objects.requireNonNull(airGrid, "airGrid");
		Objects.requireNonNull(sourceCell, "sourceCell");
		Objects.requireNonNull(listenerCell, "listenerCell");
	}
}
