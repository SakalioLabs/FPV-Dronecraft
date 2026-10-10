package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.path.VoxelPathMetrics;

import java.util.Objects;

/**
 * Auditable [H] baseline for RQ2 option B/C. This is not a wave solver or an
 * implementation copied from UDFA.
 */
public final class EmpiricalDiffraction {
	private EmpiricalDiffraction() {
	}

	public static Result evaluate(
			AcousticBands directEnergyGain,
			VoxelPathMetrics path,
			Parameters parameters
	) {
		Objects.requireNonNull(directEnergyGain, "directEnergyGain");
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(parameters, "parameters");
		AcousticBands loss = new AcousticBands(
				parameters.baseLossDb().low()
						+ parameters.lossPerExtraMeterDb().low() * path.extraPathLengthMeters()
						+ parameters.lossPerTurnDb().low() * path.turnCount(),
				parameters.baseLossDb().mid()
						+ parameters.lossPerExtraMeterDb().mid() * path.extraPathLengthMeters()
						+ parameters.lossPerTurnDb().mid() * path.turnCount(),
				parameters.baseLossDb().high()
						+ parameters.lossPerExtraMeterDb().high() * path.extraPathLengthMeters()
						+ parameters.lossPerTurnDb().high() * path.turnCount()
		);
		AcousticBands diffractedGain = loss.map(value -> Math.pow(10.0, -value / 10.0));
		AcousticBands combined = new AcousticBands(
				Math.min(1.0, directEnergyGain.low() + diffractedGain.low()),
				Math.min(1.0, directEnergyGain.mid() + diffractedGain.mid()),
				Math.min(1.0, directEnergyGain.high() + diffractedGain.high())
		);
		return new Result(diffractedGain, combined, loss);
	}

	public record Parameters(
			AcousticBands baseLossDb,
			AcousticBands lossPerExtraMeterDb,
			AcousticBands lossPerTurnDb
	) {
		public Parameters {
			Objects.requireNonNull(baseLossDb, "baseLossDb");
			Objects.requireNonNull(lossPerExtraMeterDb, "lossPerExtraMeterDb");
			Objects.requireNonNull(lossPerTurnDb, "lossPerTurnDb");
		}

		public static Parameters researchDefaults() {
			return new Parameters(
					new AcousticBands(4.0, 10.0, 18.0),
					new AcousticBands(0.6, 1.2, 2.0),
					new AcousticBands(1.5, 3.0, 5.0)
			);
		}
	}

	public record Result(
			AcousticBands diffractedEnergyGain,
			AcousticBands combinedEnergyGain,
			AcousticBands diffractionLossDb
	) {
	}
}
