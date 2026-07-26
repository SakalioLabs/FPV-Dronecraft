package com.tenicana.dronecraft.acoustics.reverb;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FdnEnvironmentMapperTest {
	@Test
	void openAnechoicEnvironmentHasNoWetSend() {
		FdnEnvironmentMapper.Controls controls = FdnEnvironmentMapper.map(
				new LateReverbEstimator.Parameters(
						1L,
						1.0,
						0.0,
						0.0,
						AcousticBands.SILENT,
						AcousticBands.SILENT,
						new AcousticBands(60.0, 60.0, 60.0),
						AcousticBands.SILENT
				)
		);

		assertEquals(0.0, controls.wetGain());
		assertEquals(AcousticBands.SILENT, controls.rt60Seconds());
	}

	@Test
	void strongerClosedRoomReflectionGetsLargerButBoundedWetSend() {
		FdnEnvironmentMapper.Controls weak = FdnEnvironmentMapper.map(
				parameters(
						2L,
						new AcousticBands(0.3, 0.2, 0.1),
						new AcousticBands(0.1, 0.05, 0.02)
				)
		);
		FdnEnvironmentMapper.Controls strong = FdnEnvironmentMapper.map(
				parameters(
						3L,
						new AcousticBands(2.0, 1.5, 1.0),
						new AcousticBands(1.0, 1.0, 1.0)
				)
		);

		assertTrue(strong.wetGain() > weak.wetGain());
		assertTrue(strong.wetGain() <= FdnEnvironmentMapper.MAXIMUM_WET_GAIN);
		assertEquals(
				FdnEnvironmentMapper.DEFAULT_TRANSITION_SECONDS,
				strong.transitionSeconds()
		);
	}

	private static LateReverbEstimator.Parameters parameters(
			long generation,
			AcousticBands rt60,
			AcousticBands firstReflection
	) {
		return new LateReverbEstimator.Parameters(
				generation,
				0.0,
				4.0,
				0.5,
				rt60,
				rt60,
				new AcousticBands(1.0, 1.0, 1.0),
				firstReflection
		);
	}
}
