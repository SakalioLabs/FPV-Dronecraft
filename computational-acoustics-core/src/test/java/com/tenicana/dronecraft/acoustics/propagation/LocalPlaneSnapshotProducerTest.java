package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneSnapshotProducer.FrozenBlockView;
import com.tenicana.dronecraft.acoustics.propagation.MaterialBoxUnionSurfaceExtractor.MaterialBox;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalPlaneSnapshotProducerTest {
	private static final CellCaptureBounds THREE_CUBE =
			new CellCaptureBounds(-1, -1, -1, 1, 1, 1);

	@Test
	void completeFrozenViewPublishesImmutableInnerSurface() {
		LocalPlaneSnapshotProducer.Result result =
				LocalPlaneSnapshotProducer.capture(
						new FloorView(),
						THREE_CUBE,
						1
				);

		assertTrue(result.complete());
		assertTrue(result.generationStable());
		assertEquals(27, result.sampledCells());
		assertEquals(0, result.unloadedCells());
		assertEquals(18, result.emptyCells());
		assertEquals(9, result.materialBoxCount());
		assertEquals(2, result.patchCount());
		assertThrows(
				UnsupportedOperationException.class,
				() -> result.materialBoxes().clear()
		);
	}

	@Test
	void unloadedCellPublishesNoGeometry() {
		FrozenBlockView view = new FloorView() {
			@Override
			public boolean isLoaded(int x, int y, int z) {
				return !(x == 0 && y == 0 && z == 0);
			}
		};

		LocalPlaneSnapshotProducer.Result result =
				LocalPlaneSnapshotProducer.capture(view, THREE_CUBE, 1);

		assertFalse(result.complete());
		assertEquals(1, result.unloadedCells());
		assertTrue(result.materialBoxes().isEmpty());
		assertTrue(result.patches().isEmpty());
	}

	@Test
	void generationChangePublishesNoGeometry() {
		FloorView view = new FloorView() {
			private int calls;

			@Override
			public long generation() {
				return ++calls;
			}
		};

		LocalPlaneSnapshotProducer.Result result =
				LocalPlaneSnapshotProducer.capture(view, THREE_CUBE, 1);

		assertFalse(result.complete());
		assertFalse(result.generationStable());
		assertTrue(result.materialBoxes().isEmpty());
	}

	@Test
	void coordinateGridBudgetExceededPublishesNoGeometry() {
		FrozenBlockView view = new FloorView() {
			@Override
			public void appendMaterialBoxes(
					int x,
					int y,
					int z,
					List<MaterialBox> output
			) {
				for (int index = 0; index < 65; index++) {
					double minimum = index * 2.0 / 130.0;
					double maximum = (index * 2.0 + 1.0) / 130.0;
					output.add(new MaterialBox(
							minimum, minimum, minimum,
							maximum, maximum, maximum,
							AcousticMaterials.STONE
					));
				}
			}
		};

		LocalPlaneSnapshotProducer.Result result =
				LocalPlaneSnapshotProducer.capture(
						view,
						new CellCaptureBounds(0, 0, 0, 0, 0, 0),
						0
				);

		assertFalse(result.complete());
		assertTrue(result.unionGridBudgetExceeded());
		assertEquals(65, result.materialBoxCount());
		assertTrue(result.materialBoxes().isEmpty());
	}

	@Test
	void boxEscapingSampledCellIsRejected() {
		FrozenBlockView view = new FloorView() {
			@Override
			public void appendMaterialBoxes(
					int x,
					int y,
					int z,
					List<MaterialBox> output
			) {
				output.add(new MaterialBox(
						x, y, z,
						x + 1.01, y + 1.0, z + 1.0,
						AcousticMaterials.STONE
				));
			}
		};

		assertThrows(
				IllegalArgumentException.class,
				() -> LocalPlaneSnapshotProducer.capture(
						view,
						new CellCaptureBounds(0, 0, 0, 0, 0, 0),
						0
				)
		);
	}

	private static class FloorView implements FrozenBlockView {
		@Override
		public long generation() {
			return 7L;
		}

		@Override
		public boolean isLoaded(int x, int y, int z) {
			return true;
		}

		@Override
		public void appendMaterialBoxes(
				int x,
				int y,
				int z,
				List<MaterialBox> output
		) {
			if (y == 0) {
				output.add(new MaterialBox(
						x, y, z,
						x + 1.0, y + 1.0, z + 1.0,
						AcousticMaterials.STONE
				));
			}
		}
	}
}
