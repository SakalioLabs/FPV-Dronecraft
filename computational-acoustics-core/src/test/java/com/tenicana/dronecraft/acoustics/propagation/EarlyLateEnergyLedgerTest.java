package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticMaterial;
import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.propagation.DdaFirstOrderPathSolver.RoomBounds;
import com.tenicana.dronecraft.acoustics.reverb.LateReverbEstimator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarlyLateEnergyLedgerTest {
	@Test
	void partitionConservesEveryEnvironmentBand() {
		BoundedFirstOrderGainSolver.Workspace gains = gains();
		EarlyLateEnergyLedger.Workspace ledger =
				new EarlyLateEnergyLedger.Workspace();
		LateReverbEstimator.Parameters environment = environment(
				0.15,
				new AcousticBands(0.30, 0.20, 0.10)
		);

		EarlyLateEnergyLedger.partition(gains, environment, ledger);

		for (int band = 0; band < 3; band++) {
			assertEquals(
					ledger.environmentBudget(band),
					ledger.explicitAllocated(band)
							+ ledger.lateResidual(band),
					1.0e-15
			);
			assertTrue(
					ledger.explicitAllocated(band)
							<= ledger.explicitCandidate(band)
			);
			assertTrue(ledger.explicitRejected(band) >= 0.0);
		}
		assertTrue(ledger.lateWetGain() >= 0.0);
		assertTrue(ledger.lateWetGain() <= 0.45);
	}

	@Test
	void zeroEnvironmentBudgetRejectsEarlyAndSilencesLateSend() {
		BoundedFirstOrderGainSolver.Workspace gains = gains();
		EarlyLateEnergyLedger.Workspace ledger =
				new EarlyLateEnergyLedger.Workspace();

		EarlyLateEnergyLedger.partition(
				gains,
				environment(1.0, AcousticBands.SILENT),
				ledger
		);

		for (int band = 0; band < 3; band++) {
			assertEquals(0.0, ledger.explicitAllocated(band));
			assertEquals(0.0, ledger.lateResidual(band));
			assertEquals(
					ledger.explicitCandidate(band),
					ledger.explicitRejected(band)
			);
		}
		assertEquals(0.0, ledger.lateWetGain());
	}

	private static BoundedFirstOrderGainSolver.Workspace gains() {
		DdaFirstOrderBatchSolver.Workspace paths =
				new DdaFirstOrderBatchSolver.Workspace();
		DdaFirstOrderBatchSolver.solve(
				1.2, 1.1, 1.0,
				3.2, 2.7, 1.4,
				new RoomBounds(5.705, 5.965, 2.355),
				32,
				paths
		);
		BoundedFirstOrderGainSolver.Workspace gains =
				new BoundedFirstOrderGainSolver.Workspace();
		AcousticMaterial[] materials = {
				AcousticMaterials.STONE,
				AcousticMaterials.SOFT,
				AcousticMaterials.WOOD,
				AcousticMaterials.GLASS,
				AcousticMaterials.METAL,
				AcousticMaterials.FOLIAGE
		};
		double[] zero = new double[6];
		BoundedFirstOrderGainSolver.solve(
				paths, materials, zero, zero, zero, false, gains
		);
		return gains;
	}

	private static LateReverbEstimator.Parameters environment(
			double openness,
			AcousticBands firstReflection
	) {
		return new LateReverbEstimator.Parameters(
				1,
				openness,
				3.0,
				0.3,
				new AcousticBands(0.8, 0.6, 0.4),
				new AcousticBands(0.5, 0.4, 0.3),
				new AcousticBands(5.0, 6.0, 7.0),
				firstReflection
		);
	}
}
