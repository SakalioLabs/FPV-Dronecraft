package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.propagation.MaterialBoxUnionSurfaceExtractor.MaterialBox;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialBoxUnionSurfaceExtractorTest {
	@Test
	void fullCubeHasSixBoundaryPatches() {
		List<AxisAlignedPlanePatch> patches =
				MaterialBoxUnionSurfaceExtractor.extract(List.of(
						box(0, 0, 0, 1, 1, 1)
				));

		assertEquals(6, patches.size());
	}

	@Test
	void adjacentBoxesDoNotEmitInternalInterface() {
		List<AxisAlignedPlanePatch> patches =
				MaterialBoxUnionSurfaceExtractor.extract(List.of(
						box(0, 0, 0, 0.5, 1, 1),
						box(0.5, 0, 0, 1, 1, 1)
				));

		assertFalse(patches.stream().anyMatch(
				patch -> patch.axis() == 0
						&& patch.coordinateMeters() == 0.5
		));
	}

	@Test
	void stairKeepsExposedHalfHeightTreadOnly() {
		List<AxisAlignedPlanePatch> patches =
				MaterialBoxUnionSurfaceExtractor.extract(List.of(
						box(0, 0, 0, 1, 0.5, 1),
						box(0, 0.5, 0, 0.5, 1, 1)
				));

		List<AxisAlignedPlanePatch> halfHeight = patches.stream()
				.filter(patch -> patch.axis() == 1
						&& patch.normalSign() == 1
						&& patch.coordinateMeters() == 0.5)
				.toList();
		assertEquals(1, halfHeight.size());
		assertEquals(0.5, halfHeight.getFirst().minimumFirstMeters());
		assertEquals(1.0, halfHeight.getFirst().maximumFirstMeters());
		assertTrue(patches.stream().anyMatch(
				patch -> patch.axis() == 1
						&& patch.normalSign() == 1
						&& patch.coordinateMeters() == 1.0
		));
	}

	private static MaterialBox box(
			double minimumX,
			double minimumY,
			double minimumZ,
			double maximumX,
			double maximumY,
			double maximumZ
	) {
		return new MaterialBox(
				minimumX,
				minimumY,
				minimumZ,
				maximumX,
				maximumY,
				maximumZ,
				AcousticMaterials.STONE
		);
	}
}
