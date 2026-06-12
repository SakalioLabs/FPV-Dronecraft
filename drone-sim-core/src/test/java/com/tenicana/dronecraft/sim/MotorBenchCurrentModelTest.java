package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MotorBenchCurrentModelTest {
	@Test
	void mqtbHq5x4x3PowerLawMatchesFitRows() {
		assertEquals(
				2.0466825,
				MotorBenchCurrentModel.mqtbHq5x4x3CurrentAmpsForThrustNewtons(1.36312435),
				1.0e-7
		);
		assertEquals(
				24.5217693,
				MotorBenchCurrentModel.mqtbHq5x4x3CurrentAmpsForThrustNewtons(11.64049355),
				1.0e-7
		);
		assertEquals(
				115.9858820,
				MotorBenchCurrentModel.mqtbHq5x4x3ElectricalPowerWattsForThrustNewtons(4.0795664),
				1.0e-7
		);
		assertEquals(0.0, MotorBenchCurrentModel.mqtbHq5x4x3CurrentAmpsForThrustNewtons(Double.NaN), 1.0e-12);
		assertEquals(0.0, MotorBenchCurrentModel.mqtbHq5x4x3ElectricalPowerWattsForThrustNewtons(-1.0), 1.0e-12);
	}

	@Test
	void stateAuditReportsTotalCurrentRatioAndResidual() {
		DroneState state = new DroneState(4);
		for (int i = 0; i < 4; i++) {
			state.setRotorThrustNewtons(i, 2.69682875);
			state.setMotorCurrentAmps(i, 4.75);
		}

		double referenceCurrent = MotorBenchCurrentModel.mqtbHq5x4x3TotalCurrentAmps(state);
		assertEquals(18.0390770, referenceCurrent, 1.0e-6);
		assertEquals(288.1544128, MotorBenchCurrentModel.mqtbHq5x4x3TotalElectricalPowerWatts(state), 1.0e-6);
		assertEquals(19.0 / referenceCurrent, MotorBenchCurrentModel.mqtbHq5x4x3CurrentRatio(state), 1.0e-12);
		assertEquals(19.0 - referenceCurrent, MotorBenchCurrentModel.mqtbHq5x4x3CurrentResidualAmps(state), 1.0e-12);
	}

	@Test
	void mqtbHq5x4x3RotorSimilaritySelectsFiveInchTriBladeProps() {
		assertEquals(
				1.0,
				MotorBenchCurrentModel.mqtbHq5x4x3RotorSimilarity(DroneConfig.racingQuad().rotors().get(0)),
				1.0e-12
		);
		assertEquals(
				0.0,
				MotorBenchCurrentModel.mqtbHq5x4x3RotorSimilarity(
						DroneConfig.racingQuad().withRotorBladeCount(2).rotors().get(0)
				),
				1.0e-12
		);
		assertEquals(
				0.0,
				MotorBenchCurrentModel.mqtbHq5x4x3RotorSimilarity(DroneConfig.cinewhoop().rotors().get(0)),
				1.0e-12
		);
		assertEquals(
				0.0,
				MotorBenchCurrentModel.mqtbHq5x4x3RotorSimilarity(DroneConfig.heavyLift().rotors().get(0)),
				1.0e-12
		);
	}
}
