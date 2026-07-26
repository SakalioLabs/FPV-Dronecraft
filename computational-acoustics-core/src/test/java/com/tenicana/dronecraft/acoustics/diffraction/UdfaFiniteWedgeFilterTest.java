package com.tenicana.dronecraft.acoustics.diffraction;

import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.voxel.VoxelDda;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UdfaFiniteWedgeFilterTest {
	private static final UdfaInfiniteWedgeFilter INFINITE =
			new UdfaInfiniteWedgeFilter(
					UdfaInfiniteWedgeFilter.Parameters.published2023()
			);
	private static final UdfaFiniteWedgeFilter FINITE =
			new UdfaFiniteWedgeFilter(
					UdfaInfiniteWedgeFilter.Parameters.published2023()
			);
	private static final InfiniteWedgeGeometry GEOMETRY =
			new InfiniteWedgeGeometry(
					2.0,
					3.0,
					0.0,
					Math.PI + 0.6,
					2.0 * Math.PI,
					Math.PI / 2.0,
					343.0
			);
	private static final VoxelDiffractionEdge VOXEL_EDGE = new VoxelDiffractionEdge(
			2,
			new VoxelDda.Cell(1, 0, 3),
			new VoxelDda.Cell(2, 0, 2),
			new AcousticVector(2.0, 0.5, 3.0),
			new VoxelDiffractionEdge.AxisDirection(0, 1, 0),
			new VoxelDiffractionEdge.AxisDirection(0, 0, 1),
			new VoxelDiffractionEdge.AxisDirection(1, 0, 0)
	);

	@Test
	void finiteGainMatchesPublishedEquationTen() {
		double cutoff = 800.0;
		double time = 0.002;
		double expected = 2.0 / Math.PI * Math.atan(
				Math.PI * Math.sqrt(2.0 * cutoff * time)
		);

		assertEquals(
				expected,
				UdfaFiniteWedgeFilter.finiteGain(cutoff, time),
				1.0e-12
		);
	}

	@Test
	void veryLongSymmetricEdgeConvergesToInfiniteWedge() {
		VoxelWedgeGeometryMapper.Result finite = mapped(true, 1.0e8, 1.0e8);
		for (double frequency : new double[] {20.0, 250.0, 1_000.0, 8_000.0}) {
			assertEquals(
					INFINITE.pressureMagnitude(frequency, GEOMETRY),
					FINITE.pressureMagnitude(frequency, finite),
					2.0e-4
			);
		}
	}

	@Test
	void zeroLengthEdgeHasNoDiffractedContribution() {
		VoxelWedgeGeometryMapper.Result zero = mapped(true, 0.0, 0.0);
		assertEquals(0.0, FINITE.pressureMagnitude(1_000.0, zero), 1.0e-12);
	}

	@Test
	void shorterEdgeReducesLowFrequencyGain() {
		double shortGain = FINITE.pressureMagnitude(
				0.0, mapped(true, 0.0001, 0.0001)
		);
		double longGain = FINITE.pressureMagnitude(
				0.0, mapped(true, 0.1, 0.1)
		);

		assertTrue(shortGain < longGain);
	}

	@Test
	void farOutsideApexApproachesFirstOrderHighFrequencySlope() {
		VoxelWedgeGeometryMapper.Result outside =
				mapped(false, 0.1000, 0.1001);
		double first = FINITE.pressureMagnitude(100_000.0, outside);
		double octave = FINITE.pressureMagnitude(200_000.0, outside);
		double changeDb = 20.0 * Math.log10(octave / first);

		assertEquals(-6.0206, changeDb, 0.15);
	}

	@Test
	void endpointBlendStaysBelowThreeDbPerGeometryUpdate() {
		double previous = Double.NaN;
		double maximumStepDb = 0.0;
		for (int index = 0; index <= 80; index++) {
			double sourceAxial = 0.8 + index * 0.005;
			VoxelWedgeGeometryMapper.Result mapped =
					VoxelWedgeGeometryMapper.map(
							VOXEL_EDGE,
							new AcousticVector(2.0, 0.5 + sourceAxial, 1.0),
							new AcousticVector(4.0, 0.5, 3.0),
							343.0
					).orElseThrow();
			double magnitude = FINITE.pressureMagnitude(1_000.0, mapped);
			if (Double.isFinite(previous)) {
				maximumStepDb = Math.max(
						maximumStepDb,
						Math.abs(20.0 * Math.log10(magnitude / previous))
				);
			}
			previous = magnitude;
		}

		assertTrue(
				maximumStepDb < 3.0,
				"maximum endpoint step was " + maximumStepDb + " dB"
		);
	}

	private static VoxelWedgeGeometryMapper.Result mapped(
			boolean inside,
			double firstTime,
			double secondTime
	) {
		return new VoxelWedgeGeometryMapper.Result(
				GEOMETRY,
				UdfaZoneClassifier.classify(GEOMETRY),
				AcousticVector.ZERO,
				inside ? 0.0 : 2.0,
				inside,
				0.0,
				firstTime,
				secondTime
		);
	}
}
