package com.tenicana.dronecraft.acoustics.propagation;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalPlaneCapturePlannerTest {
	private static final LocalPlaneCapturePlanner.Config CONFIG =
			new LocalPlaneCapturePlanner.Config(
					2, 1, 1,
					4, 4096,
					32, 16
			);

	@Test
	void coincidentSourcesShareOneCapture() {
		int[] x = new int[16];
		int[] y = new int[16];
		int[] z = new int[16];
		Arrays.fill(y, 2);
		LocalPlaneCapturePlanner.Workspace result =
				new LocalPlaneCapturePlanner.Workspace();

		LocalPlaneCapturePlanner.plan(
				0, 2, 0,
				x, y, z, 16,
				CONFIG,
				result
		);

		assertEquals(1, result.groupCount());
		assertEquals(16, result.assignedCount());
		assertEquals(0, result.fallbackCount());
		assertEquals(245, result.totalCellCount());
	}

	@Test
	void sourceOutsideSpanUsesConservativeFallback() {
		int[] x = {40};
		int[] y = {2};
		int[] z = {0};
		LocalPlaneCapturePlanner.Workspace result =
				new LocalPlaneCapturePlanner.Workspace();

		LocalPlaneCapturePlanner.plan(
				0, 2, 0,
				x, y, z, 1,
				CONFIG,
				result
		);

		assertEquals(0, result.groupCount());
		assertEquals(0, result.assignedCount());
		assertEquals(1, result.fallbackCount());
		assertEquals(-1, result.sourceGroup(0));
	}

	@Test
	void sharedCellsNeverExceedIndependentAssignedCells() {
		int[] x = {-6, -3, 0, 3, 6, 8, 10, 12};
		int[] y = {2, 2, 2, 2, 2, 2, 2, 2};
		int[] z = {0, 1, -1, 1, -1, 0, 1, -1};
		LocalPlaneCapturePlanner.Workspace result =
				new LocalPlaneCapturePlanner.Workspace();

		LocalPlaneCapturePlanner.plan(
				0, 2, 0,
				x, y, z, x.length,
				CONFIG,
				result
		);

		long independent = 0L;
		for (int source = 0; source < x.length; source++) {
			if (result.sourceGroup(source) >= 0) {
				independent += (
						Math.abs(x[source]) + 7L
				) * 5L * (
						Math.abs(z[source]) + 7L
				);
			}
		}
		assertTrue(result.totalCellCount() <= independent);
	}
}
