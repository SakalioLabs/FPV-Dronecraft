package com.tenicana.dronecraft.integration;

import java.util.List;

import com.tenicana.dronecraft.sim.Vec3;

public final class Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed {
	public static final String SOURCE_ID = "A4MC-L2-Static-Airframe-Multi-Axis-Coefficient-Seed-Packet";
	public static final String CAVEAT =
			"Multi-axis coefficient seed normalizes compact sweep force/moment summaries into signed body-axis and inlet-axis coefficients; test-runtime values prove the fitting shape only and must not tune gameplay.";
	public static final double AIR_DENSITY_KG_M3 = 1.225;
	public static final int SOURCE_REFERENCE_COUNT = 5;
	public static final int SWEEP_CASE_SAMPLE_COUNT = 24;
	public static final int COEFFICIENT_METRIC_COUNT = 27;
	public static final int SUMMARY_METRIC_ROW_COUNT = 11;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SWEEP_CASE_SAMPLE_COUNT * COEFFICIENT_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed() {
	}

	public record StaticAirframeMultiAxisCoefficientSeed(
			String presetName,
			String sweepName,
			String fitRole,
			boolean coefficientFitReady,
			boolean sourceRunAvailable,
			boolean forwardDragFitSample,
			boolean sideforceFitSample,
			boolean liftFitSample,
			boolean momentPressureCenterFitSample,
			double airDensityKgM3,
			double dynamicPressurePascals,
			double projectedReferenceAreaSquareMeters,
			double referenceLengthMeters,
			double forceCoefficientX,
			double forceCoefficientY,
			double forceCoefficientZ,
			double signedAxialForceCoefficient,
			double sideForceCoefficient,
			double liftCoefficient,
			double momentCoefficientX,
			double momentCoefficientY,
			double momentCoefficientZ,
			double momentCoefficientMagnitude,
			double pressureCenterOffsetRatio,
			double pressureCenterOffsetXRatio,
			double pressureCenterOffsetYRatio,
			double pressureCenterOffsetZRatio,
			String sourceRunStatus,
			String sourceRuntimeInfo
	) {
	}

	public record StaticAirframeMultiAxisCoefficientExtrema(
			int coefficientSeedCount,
			int fitReadyCount,
			int forwardDragSeedCount,
			int sideforceSeedCount,
			int liftSeedCount,
			int momentPressureCenterSeedCount,
			double maxDynamicPressurePascals,
			double maxAbsForceCoefficient,
			double maxAbsSignedAxialForceCoefficient,
			double maxMomentCoefficientMagnitude,
			double maxPressureCenterOffsetRatio
	) {
	}

	public record StaticAirframeMultiAxisCoefficientAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int sweepCaseSampleCount,
			int coefficientMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<StaticAirframeMultiAxisCoefficientSeed> seeds,
			StaticAirframeMultiAxisCoefficientExtrema extrema
	) {
		public StaticAirframeMultiAxisCoefficientAudit {
			seeds = List.copyOf(seeds);
		}
	}

	public static StaticAirframeMultiAxisCoefficientAudit audit() {
		return audit(Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.audit());
	}

	public static StaticAirframeMultiAxisCoefficientAudit audit(ClassLoader loader) {
		if (loader == null) {
			throw new IllegalArgumentException("loader must not be null.");
		}
		return audit(Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.audit(loader));
	}

	public static StaticAirframeMultiAxisCoefficientAudit audit(
			Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.StaticAirframeMultiAxisRunMatrixAudit runMatrix
	) {
		if (runMatrix == null) {
			throw new IllegalArgumentException("run matrix is required.");
		}
		List<StaticAirframeMultiAxisCoefficientSeed> seeds = runMatrix.runs().stream()
				.map(Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed::seed)
				.toList();
		return new StaticAirframeMultiAxisCoefficientAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				SWEEP_CASE_SAMPLE_COUNT,
				COEFFICIENT_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				seeds,
				extrema(seeds)
		);
	}

	public static StaticAirframeMultiAxisCoefficientSeed seed(
			Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.StaticAirframeMultiAxisRunSummary run
	) {
		if (run == null) {
			throw new IllegalArgumentException("run summary is required.");
		}
		double dynamicPressure = 0.5 * AIR_DENSITY_KG_M3
				* run.inletSpeedMetersPerSecond()
				* run.inletSpeedMetersPerSecond();
		double forceScale = dynamicPressure * Math.max(1.0e-12, run.projectedReferenceAreaSquareMeters());
		double momentScale = forceScale * Math.max(1.0e-12, run.referenceLengthMeters());
		Vec3 force = new Vec3(run.forceXNewtons(), run.forceYNewtons(), run.forceZNewtons());
		Vec3 inlet = new Vec3(
				run.inletVxMetersPerSecond(),
				run.inletVyMetersPerSecond(),
				run.inletVzMetersPerSecond()
		);
		Vec3 dragDirection = run.inletSpeedMetersPerSecond() <= 1.0e-12
				? Vec3.ZERO
				: inlet.multiply(-1.0 / run.inletSpeedMetersPerSecond());
		double forceCoefficientX = run.forceXNewtons() / forceScale;
		double forceCoefficientY = run.forceYNewtons() / forceScale;
		double forceCoefficientZ = run.forceZNewtons() / forceScale;
		double signedAxialForceCoefficient = force.dot(dragDirection) / forceScale;
		boolean ready = run.coefficientFitReady()
				&& run.available()
				&& run.hasForceMoment()
				&& dynamicPressure > 1.0e-12
				&& run.projectedReferenceAreaSquareMeters() > 1.0e-12
				&& run.referenceLengthMeters() > 1.0e-12;
		return new StaticAirframeMultiAxisCoefficientSeed(
				run.presetName(),
				run.sweepName(),
				run.fitRole(),
				ready,
				run.available(),
				forwardDragFitSample(run.fitRole()),
				sideforceFitSample(run.fitRole()),
				liftFitSample(run.fitRole()),
				true,
				AIR_DENSITY_KG_M3,
				dynamicPressure,
				run.projectedReferenceAreaSquareMeters(),
				run.referenceLengthMeters(),
				forceCoefficientX,
				forceCoefficientY,
				forceCoefficientZ,
				signedAxialForceCoefficient,
				forceCoefficientX,
				forceCoefficientY,
				run.momentXNewtonMeters() / momentScale,
				run.momentYNewtonMeters() / momentScale,
				run.momentZNewtonMeters() / momentScale,
				run.momentMagnitudeNewtonMeters() / momentScale,
				run.pressureCenterOffsetMeters() / Math.max(1.0e-12, run.referenceLengthMeters()),
				run.pressureCenterOffsetXBodyMeters() / Math.max(1.0e-12, run.referenceLengthMeters()),
				run.pressureCenterOffsetYBodyMeters() / Math.max(1.0e-12, run.referenceLengthMeters()),
				run.pressureCenterOffsetZBodyMeters() / Math.max(1.0e-12, run.referenceLengthMeters()),
				run.status(),
				run.runtimeInfo()
		);
	}

	private static boolean forwardDragFitSample(String fitRole) {
		return fitRole != null && fitRole.startsWith("drag_z");
	}

	private static boolean sideforceFitSample(String fitRole) {
		return fitRole != null && fitRole.startsWith("sideforce_");
	}

	private static boolean liftFitSample(String fitRole) {
		return fitRole != null && fitRole.startsWith("lift_");
	}

	private static StaticAirframeMultiAxisCoefficientExtrema extrema(List<StaticAirframeMultiAxisCoefficientSeed> seeds) {
		int ready = 0;
		int forward = 0;
		int sideforce = 0;
		int lift = 0;
		int momentPressure = 0;
		double maxDynamicPressure = 0.0;
		double maxAbsForceCoefficient = 0.0;
		double maxAbsAxial = 0.0;
		double maxMoment = 0.0;
		double maxPressureCenter = 0.0;
		for (StaticAirframeMultiAxisCoefficientSeed seed : seeds) {
			if (seed.coefficientFitReady()) {
				ready++;
			}
			if (seed.forwardDragFitSample()) {
				forward++;
			}
			if (seed.sideforceFitSample()) {
				sideforce++;
			}
			if (seed.liftFitSample()) {
				lift++;
			}
			if (seed.momentPressureCenterFitSample()) {
				momentPressure++;
			}
			maxDynamicPressure = Math.max(maxDynamicPressure, seed.dynamicPressurePascals());
			maxAbsForceCoefficient = Math.max(maxAbsForceCoefficient, maxAbs(
					seed.forceCoefficientX(),
					seed.forceCoefficientY(),
					seed.forceCoefficientZ()
			));
			maxAbsAxial = Math.max(maxAbsAxial, Math.abs(seed.signedAxialForceCoefficient()));
			maxMoment = Math.max(maxMoment, seed.momentCoefficientMagnitude());
			maxPressureCenter = Math.max(maxPressureCenter, seed.pressureCenterOffsetRatio());
		}
		return new StaticAirframeMultiAxisCoefficientExtrema(
				seeds.size(),
				ready,
				forward,
				sideforce,
				lift,
				momentPressure,
				maxDynamicPressure,
				maxAbsForceCoefficient,
				maxAbsAxial,
				maxMoment,
				maxPressureCenter
		);
	}

	private static double maxAbs(double x, double y, double z) {
		return Math.max(Math.max(Math.abs(x), Math.abs(y)), Math.abs(z));
	}
}
