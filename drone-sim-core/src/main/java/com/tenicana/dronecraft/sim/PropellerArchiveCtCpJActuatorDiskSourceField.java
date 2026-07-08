package com.tenicana.dronecraft.sim;

import java.util.List;

public record PropellerArchiveCtCpJActuatorDiskSourceField(
		List<PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample> sourceTerms,
		double sourceThicknessMeters
) {
	private static final double EPSILON = 1.0e-9;

	public PropellerArchiveCtCpJActuatorDiskSourceField {
		sourceTerms = List.copyOf(sourceTerms == null ? List.of() : sourceTerms);
		if (!Double.isFinite(sourceThicknessMeters) || sourceThicknessMeters <= EPSILON) {
			throw new IllegalArgumentException("sourceThicknessMeters must be finite and positive.");
		}
	}

	public record SourceFieldSample(
			Vec3 samplePointWorldMeters,
			int contributingSourceCount,
			Vec3 bodyForceDensityWorldNewtonsPerCubicMeter,
			Vec3 wakeAngularMomentumTorqueDensityWorldNewtonMetersPerCubicMeter,
			double pressureJumpPascals,
			double massFluxKilogramsPerSecondSquareMeter,
			double idealMomentumPowerLoadingWattsPerSquareMeter,
			Vec3 farWakeAxialVelocityWorldMetersPerSecond,
			Vec3 wakeSwirlVelocityWorldMetersPerSecond,
			Vec3 targetWakeVelocityWorldMetersPerSecond
	) {
		public SourceFieldSample {
			samplePointWorldMeters = finiteVecOrZero(samplePointWorldMeters);
			contributingSourceCount = Math.max(0, contributingSourceCount);
			bodyForceDensityWorldNewtonsPerCubicMeter =
					finiteVecOrZero(bodyForceDensityWorldNewtonsPerCubicMeter);
			wakeAngularMomentumTorqueDensityWorldNewtonMetersPerCubicMeter =
					finiteVecOrZero(wakeAngularMomentumTorqueDensityWorldNewtonMetersPerCubicMeter);
			pressureJumpPascals = finiteNonnegative(pressureJumpPascals);
			massFluxKilogramsPerSecondSquareMeter = finiteNonnegative(massFluxKilogramsPerSecondSquareMeter);
			idealMomentumPowerLoadingWattsPerSquareMeter =
					finiteNonnegative(idealMomentumPowerLoadingWattsPerSquareMeter);
			farWakeAxialVelocityWorldMetersPerSecond =
					finiteVecOrZero(farWakeAxialVelocityWorldMetersPerSecond);
			wakeSwirlVelocityWorldMetersPerSecond =
					finiteVecOrZero(wakeSwirlVelocityWorldMetersPerSecond);
			targetWakeVelocityWorldMetersPerSecond =
					finiteVecOrZero(targetWakeVelocityWorldMetersPerSecond);
		}

		public boolean insideAnySource() {
			return contributingSourceCount > 0;
		}
	}

	public SourceFieldSample sampleAt(Vec3 samplePointWorldMeters) {
		Vec3 point = finiteVecOrZero(samplePointWorldMeters);
		int contributingSources = 0;
		Vec3 bodyForceDensity = Vec3.ZERO;
		Vec3 wakeTorqueDensity = Vec3.ZERO;
		double pressureJump = 0.0;
		double massFlux = 0.0;
		double powerLoading = 0.0;
		Vec3 farWakeAxialVelocity = Vec3.ZERO;
		Vec3 wakeSwirlVelocity = Vec3.ZERO;
		for (PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm : sourceTerms) {
			if (!containsActuatorDiskVolume(sourceTerm, point)) {
				continue;
			}
			contributingSources++;
			bodyForceDensity = bodyForceDensity.add(
					sourceTerm.equivalentBodyForceWorldNewtonsPerCubicMeter(sourceThicknessMeters));
			wakeTorqueDensity = wakeTorqueDensity.add(
					sourceTerm.equivalentWakeAngularMomentumTorqueDensityWorldNewtonMetersPerCubicMeter(
							sourceThicknessMeters));
			pressureJump += sourceTerm.pressureJumpPascals();
			massFlux += sourceTerm.massFluxKilogramsPerSecondSquareMeter();
			powerLoading += sourceTerm.idealMomentumPowerLoadingWattsPerSquareMeter();
			farWakeAxialVelocity = farWakeAxialVelocity.add(sourceTerm.farWakeAxialVelocityWorldMetersPerSecond());
			wakeSwirlVelocity = wakeSwirlVelocity.add(sourceTerm.wakeSwirlVelocityWorldMetersPerSecond(point));
		}
		return new SourceFieldSample(
				point,
				contributingSources,
				bodyForceDensity,
				wakeTorqueDensity,
				pressureJump,
				massFlux,
				powerLoading,
				farWakeAxialVelocity,
				wakeSwirlVelocity,
				farWakeAxialVelocity.add(wakeSwirlVelocity)
		);
	}

	public boolean containsActuatorDiskVolume(
			PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm,
			Vec3 samplePointWorldMeters
	) {
		if (sourceTerm == null || !sourceTerm.applied() || sourceTerm.diskAreaSquareMeters() <= EPSILON) {
			return false;
		}
		Vec3 normal = finiteVecOrZero(sourceTerm.diskNormalWorld()).normalized();
		if (normal.lengthSquared() <= EPSILON) {
			return false;
		}
		Vec3 offset = finiteVecOrZero(samplePointWorldMeters).subtract(sourceTerm.diskCenterWorldMeters());
		double axialDistance = offset.dot(normal);
		if (Math.abs(axialDistance) > sourceThicknessMeters * 0.5 + EPSILON) {
			return false;
		}
		Vec3 radial = offset.subtract(normal.multiply(axialDistance));
		double diskRadius = Math.sqrt(sourceTerm.diskAreaSquareMeters() / Math.PI);
		return radial.lengthSquared() <= diskRadius * diskRadius + EPSILON;
	}

	public Vec3 integratedBodyForceWorldNewtons() {
		Vec3 sum = Vec3.ZERO;
		for (PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm : sourceTerms) {
			if (sourceTerm != null && sourceTerm.applied()) {
				sum = sum.add(sourceTerm.equivalentBodyForceWorldNewtonsPerCubicMeter(sourceThicknessMeters)
						.multiply(sourceTerm.sourceVolumeCubicMeters(sourceThicknessMeters)));
			}
		}
		return sum;
	}

	public Vec3 integratedWakeAngularMomentumTorqueWorldNewtonMeters() {
		Vec3 sum = Vec3.ZERO;
		for (PropellerArchiveCtCpJRotorForceModel.RotorActuatorDiskSourceTermSample sourceTerm : sourceTerms) {
			if (sourceTerm != null && sourceTerm.applied()) {
				sum = sum.add(sourceTerm
						.equivalentWakeAngularMomentumTorqueDensityWorldNewtonMetersPerCubicMeter(
								sourceThicknessMeters)
						.multiply(sourceTerm.sourceVolumeCubicMeters(sourceThicknessMeters)));
			}
		}
		return sum;
	}

	private static Vec3 finiteVecOrZero(Vec3 value) {
		return value == null || !value.isFinite() ? Vec3.ZERO : value;
	}

	private static double finiteNonnegative(double value) {
		return Double.isFinite(value) && value > 0.0 ? value : 0.0;
	}
}
