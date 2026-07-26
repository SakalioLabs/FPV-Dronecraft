package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SparseMaterialSnapshotTest {
	@Test
	void preservesNegativeAndWorldHeightCoordinates() {
		DirectPathSolver.MaterialSample stone = DirectPathSolver.MaterialSample.full(AcousticMaterials.STONE);
		SparseMaterialSnapshot snapshot = SparseMaterialSnapshot.builder()
				.put(-30_000_000, -64, 29_999_999, stone)
				.build();

		assertEquals(stone, snapshot.sampleAt(-30_000_000, -64, 29_999_999));
		assertSame(DirectPathSolver.MaterialSample.AIR, snapshot.sampleAt(0, 0, 0));
		assertEquals(1, snapshot.size());
	}

	@Test
	void carriesSnapshotCompleteness() {
		SparseMaterialSnapshot snapshot = SparseMaterialSnapshot.builder()
				.markIncomplete()
				.build();

		assertFalse(snapshot.complete());
	}

	@Test
	void diagnosticHashIsStableAcrossInsertionOrder() {
		DirectPathSolver.MaterialSample stone =
				DirectPathSolver.MaterialSample.full(
						AcousticMaterials.STONE
				);
		SparseMaterialSnapshot first = SparseMaterialSnapshot.builder()
				.put(2, 3, 4, stone)
				.put(-1, 7, 9, DirectPathSolver.MaterialSample.AIR)
				.build();
		SparseMaterialSnapshot second = SparseMaterialSnapshot.builder()
				.put(-1, 7, 9, DirectPathSolver.MaterialSample.AIR)
				.put(2, 3, 4, stone)
				.build();

		assertEquals(first.diagnosticSha256(), second.diagnosticSha256());
		assertEquals(first.diagnosticEntries(), second.diagnosticEntries());
		assertEquals(64, first.diagnosticSha256().length());
	}

	@Test
	void diagnosticHashIncludesCompletenessMaterialAndFill() {
		DirectPathSolver.MaterialSample stone =
				DirectPathSolver.MaterialSample.full(
						AcousticMaterials.STONE
				);
		SparseMaterialSnapshot complete = SparseMaterialSnapshot.builder()
				.put(1, 2, 3, stone)
				.build();
		SparseMaterialSnapshot incomplete = SparseMaterialSnapshot.builder()
				.put(1, 2, 3, stone)
				.markIncomplete()
				.build();
		SparseMaterialSnapshot partial = SparseMaterialSnapshot.builder()
				.put(
						1,
						2,
						3,
						new DirectPathSolver.MaterialSample(
								AcousticMaterials.STONE,
								0.5
						)
				)
				.build();

		assertNotEquals(
				complete.diagnosticSha256(),
				incomplete.diagnosticSha256()
		);
		assertNotEquals(
				complete.diagnosticSha256(),
				partial.diagnosticSha256()
		);
	}
}
