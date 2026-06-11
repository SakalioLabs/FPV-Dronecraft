package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DrydenTurbulenceModelTest {
	@Test
	void lowAltitudeParametersMatchReferenceFormulaAtSixMeters() {
		DrydenTurbulenceModel.Parameters parameters = DrydenTurbulenceModel.lowAltitude(6.0, 10.0);

		assertEquals(43.1460, parameters.longitudinalScaleMeters(), 0.0002);
		assertEquals(43.1460, parameters.lateralScaleMeters(), 0.0002);
		assertEquals(6.0, parameters.verticalScaleMeters(), 1.0e-9);
		assertEquals(1.93017, parameters.longitudinalSigmaMetersPerSecond(), 0.00002);
		assertEquals(1.93017, parameters.lateralSigmaMetersPerSecond(), 0.00002);
		assertEquals(1.0, parameters.verticalSigmaMetersPerSecond(), 1.0e-9);
		assertEquals(4.31460, parameters.longitudinalTimeConstantSeconds(), 0.00002);
		assertEquals(0.6, parameters.verticalTimeConstantSeconds(), 1.0e-9);
	}

	@Test
	void transverseLeadLagCoefficientsMatchDrydenShape() {
		assertEquals(Math.sqrt(1.5), DrydenTurbulenceModel.TRANSVERSE_LEAD_LAG_SCALE, 1.0e-12);
		assertEquals(1.0 - 1.0 / Math.sqrt(3.0), DrydenTurbulenceModel.TRANSVERSE_LAG_WEIGHT, 1.0e-12);
		assertEquals(1.0 / Math.sqrt(2.0),
				DrydenTurbulenceModel.TRANSVERSE_LEAD_LAG_SCALE
						* (1.0 - DrydenTurbulenceModel.TRANSVERSE_LAG_WEIGHT),
				1.0e-12);
	}

	@Test
	void lowAltitudeParametersClampInvalidInputs() {
		DrydenTurbulenceModel.Parameters parameters = DrydenTurbulenceModel.lowAltitude(Double.NaN, Double.POSITIVE_INFINITY);

		assertEquals(6.0, parameters.altitudeMeters(), 1.0e-9);
		assertEquals(0.0, parameters.wind20MetersPerSecond(), 1.0e-9);
		assertEquals(0.0, parameters.longitudinalSigmaMetersPerSecond(), 1.0e-9);
		assertEquals(0.0, parameters.verticalSigmaMetersPerSecond(), 1.0e-9);
	}
}
