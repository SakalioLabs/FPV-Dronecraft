package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;
import com.tenicana.dronecraft.acoustics.reverb.LateReverbEstimator;

import java.util.Objects;

/**
 * D121 energy ledger between six explicit first-order candidates and the
 * listener-shared late-reverb send. Reused workspaces allocate no heap memory.
 */
public final class EarlyLateEnergyLedger {
	public static final int EXPLICIT_CANDIDATE_COUNT = 6;
	public static final double WET_ENERGY_SCALE = 0.4;

	private EarlyLateEnergyLedger() {
	}

	public static void partition(
			BoundedFirstOrderGainSolver.Workspace gains,
			LateReverbEstimator.Parameters environment,
			Workspace output
	) {
		Objects.requireNonNull(gains, "gains");
		Objects.requireNonNull(environment, "environment");
		Objects.requireNonNull(output, "output");
		double candidateLow = 0.0;
		double candidateMid = 0.0;
		double candidateHigh = 0.0;
		for (int path = 1; path < DdaFirstOrderBatchSolver.PATH_COUNT; path++) {
			candidateLow += gains.low(path);
			candidateMid += gains.mid(path);
			candidateHigh += gains.high(path);
		}
		partition(
				candidateLow,
				candidateMid,
				candidateHigh,
				EXPLICIT_CANDIDATE_COUNT,
				environment,
				output
		);
	}

	public static void partition(
			double explicitLowSum,
			double explicitMidSum,
			double explicitHighSum,
			int normalizationCandidates,
			LateReverbEstimator.Parameters environment,
			Workspace output
	) {
		Objects.requireNonNull(environment, "environment");
		Objects.requireNonNull(output, "output");
		if (normalizationCandidates < 1) {
			throw new IllegalArgumentException(
					"normalizationCandidates must be positive"
			);
		}
		double candidateLow = explicitLowSum / normalizationCandidates;
		double candidateMid = explicitMidSum / normalizationCandidates;
		double candidateHigh = explicitHighSum / normalizationCandidates;
		AcousticBands budget = environment.firstReflectionEnergy();
		partitionBand(candidateLow, clampUnit(budget.low()), 0, output);
		partitionBand(candidateMid, clampUnit(budget.mid()), 1, output);
		partitionBand(candidateHigh, clampUnit(budget.high()), 2, output);
		double meanLateResidual = (
				output.lateResidual[0]
						+ output.lateResidual[1]
						+ output.lateResidual[2]
		) / 3.0;
		output.lateWetGain = Math.min(
				FdnEnvironmentMapper.MAXIMUM_WET_GAIN,
				Math.sqrt(meanLateResidual)
						* WET_ENERGY_SCALE
						* (1.0 - environment.openness())
		);
	}

	private static void partitionBand(
			double candidate,
			double budget,
			int band,
			Workspace output
	) {
		double finiteCandidate = Double.isFinite(candidate)
				? Math.max(0.0, candidate)
				: 0.0;
		double early = Math.min(finiteCandidate, budget);
		output.explicitCandidate[band] = finiteCandidate;
		output.explicitAllocated[band] = early;
		output.explicitRejected[band] =
				Math.max(0.0, finiteCandidate - early);
		output.lateResidual[band] = Math.max(0.0, budget - early);
		output.environmentBudget[band] = budget;
	}

	private static double clampUnit(double value) {
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException(
					"environment first-reflection energy must be finite"
			);
		}
		return Math.max(0.0, Math.min(1.0, value));
	}

	public static final class Workspace {
		private final double[] explicitCandidate = new double[3];
		private final double[] explicitAllocated = new double[3];
		private final double[] explicitRejected = new double[3];
		private final double[] lateResidual = new double[3];
		private final double[] environmentBudget = new double[3];
		private double lateWetGain;

		public double explicitCandidate(int band) {
			return explicitCandidate[checkedBand(band)];
		}

		public double explicitAllocated(int band) {
			return explicitAllocated[checkedBand(band)];
		}

		public double explicitRejected(int band) {
			return explicitRejected[checkedBand(band)];
		}

		public double lateResidual(int band) {
			return lateResidual[checkedBand(band)];
		}

		public double environmentBudget(int band) {
			return environmentBudget[checkedBand(band)];
		}

		public double lateWetGain() {
			return lateWetGain;
		}

		private static int checkedBand(int band) {
			if (band < 0 || band >= 3) {
				throw new IndexOutOfBoundsException(band);
			}
			return band;
		}
	}
}
