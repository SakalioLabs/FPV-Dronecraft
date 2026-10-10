package com.tenicana.dronecraft.acoustics.diffraction;

import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.voxel.VoxelDda;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoxelWedgeGeometryMapperTest {
	private static final VoxelDiffractionEdge EDGE = new VoxelDiffractionEdge(
			2,
			new VoxelDda.Cell(1, 0, 3),
			new VoxelDda.Cell(2, 0, 2),
			new AcousticVector(2.0, 0.5, 3.0),
			new VoxelDiffractionEdge.AxisDirection(0, 1, 0),
			new VoxelDiffractionEdge.AxisDirection(0, 0, 1),
			new VoxelDiffractionEdge.AxisDirection(1, 0, 0)
	);

	@Test
	void mapsHorizontalBlockCornerToThreeQuarterExteriorWedge() {
		VoxelWedgeGeometryMapper.Result result = VoxelWedgeGeometryMapper.map(
				EDGE,
				new AcousticVector(0.5, 0.5, 2.5),
				new AcousticVector(4.5, 0.5, 3.5),
				343.0
		).orElseThrow();

		assertEquals(3.0 * Math.PI / 2.0,
				result.geometry().exteriorWedgeAngleRadians(), 1.0e-12);
		assertEquals(Math.PI / 2.0,
				result.geometry().incidenceAngleRadians(), 1.0e-12);
		assertEquals(0.0, result.axialOffsetFromBlockEdgeCenter(), 1.0e-12);
		assertTrue(result.apexWithinPhysicalBlockEdge());
		assertTrue(result.geometry().sourceAzimuthRadians()
				< result.geometry().receiverAzimuthRadians());
		assertEquals(
				result.firstEndpointExcessTimeSeconds(),
				result.secondEndpointExcessTimeSeconds(),
				1.0e-12
		);
		assertTrue(result.firstEndpointExcessTimeSeconds() > 0.0);
	}

	@Test
	void optimizedApexEqualizesSourceAndReceiverIncidence() {
		VoxelWedgeGeometryMapper.Result result = VoxelWedgeGeometryMapper.map(
				EDGE,
				new AcousticVector(2.0, -0.5, 1.0),
				new AcousticVector(6.0, 3.5, 3.0),
				343.0
		).orElseThrow();

		assertEquals(1.0 / 3.0, result.axialOffsetFromBlockEdgeCenter(), 1.0e-12);
		assertTrue(result.apexWithinPhysicalBlockEdge());
		assertEquals(0.0, result.incidenceAngleMismatchRadians(), 1.0e-12);
	}

	@Test
	void marksInfiniteEdgeApexOutsidePhysicalVoxelSegment() {
		VoxelWedgeGeometryMapper.Result result = VoxelWedgeGeometryMapper.map(
				EDGE,
				new AcousticVector(2.0, 10.5, 1.0),
				new AcousticVector(6.0, 12.5, 3.0),
				343.0
		).orElseThrow();

		assertFalse(result.apexWithinPhysicalBlockEdge());
	}

	@Test
	void rejectsSourceInsideSolidQuarterPlane() {
		Optional<VoxelWedgeGeometryMapper.Result> result = VoxelWedgeGeometryMapper.map(
				EDGE,
				new AcousticVector(3.0, 0.5, 2.0),
				new AcousticVector(4.0, 0.5, 3.0),
				343.0
		);

		assertTrue(result.isEmpty());
	}
}
