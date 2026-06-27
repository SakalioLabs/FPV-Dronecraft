package com.tenicana.dronecraft.integration;

import java.util.List;

import com.tenicana.dronecraft.sim.Vec3;

public final class Aerodynamics4McL2StaticAirframeCoefficientSeed {
	public static final String SOURCE_ID = "A4MC-L2-Static-Airframe-Coefficient-Seed-Packet";
	public static final String CAVEAT =
			"Coefficient seed normalizes static-airframe L2 force/moment summaries for comparison; do not treat test-runtime values as calibrated gameplay coefficients.";
	public static final double AIR_DENSITY_KG_M3 = 1.225;
	public static final int SOURCE_REFERENCE_COUNT = 5;
	public static final int PRESET_SAMPLE_COUNT = 4;
	public static final int COEFFICIENT_METRIC_COUNT = 19;
	public static final int SUMMARY_METRIC_ROW_COUNT = 8;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ PRESET_SAMPLE_COUNT * COEFFICIENT_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2StaticAirframeCoefficientSeed() {
	}

	public record StaticAirframeCoefficientSeed(
			String presetName,
			boolean coefficientFitReady,
			boolean sourceRunAvailable,
			double referenceAreaSquareMeters,
			double referenceLengthMeters,
			double airDensityKgM3,
			double dynamicPressurePascals,
			double forceCoefficientX,
			double forceCoefficientY,
			double forceCoefficientZ,
			double dragCoefficient,
			double sideForceCoefficient,
			double liftCoefficient,
			double momentCoefficientX,
			double momentCoefficientY,
			double momentCoefficientZ,
			double momentCoefficientMagnitude,
			double pressureCenterOffsetRatio,
			String sourceRunStatus,
			String sourceRuntimeInfo
	) {
	}

	public record StaticAirframeCoefficientExtrema(
			int coefficientSeedCount,
			int fitReadyCount,
			double maxReferenceAreaSquareMeters,
			double maxDynamicPressurePascals,
			double maxDragCoefficient,
			double maxForceCoefficientMagnitude,
			double maxMomentCoefficientMagnitude,
			double maxPressureCenterOffsetRatio
	) {
	}

	public record StaticAirframeCoefficientAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int presetSampleCount,
			int coefficientMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<StaticAirframeCoefficientSeed> seeds,
			StaticAirframeCoefficientExtrema extrema
	) {
		public StaticAirframeCoefficientAudit {
			seeds = List.copyOf(seeds);
		}
	}

	public static StaticAirframeCoefficientAudit audit() {
		return audit(Aerodynamics4McL2StaticAirframeRunMatrix.audit(), Aerodynamics4McL2DroneProbeAudit.audit());
	}

	public static StaticAirframeCoefficientAudit audit(ClassLoader loader) {
		if (loader == null) {
			throw new IllegalArgumentException("loader must not be null.");
		}
		return audit(Aerodynamics4McL2StaticAirframeRunMatrix.audit(loader), Aerodynamics4McL2DroneProbeAudit.audit());
	}

	public static StaticAirframeCoefficientAudit audit(
			Aerodynamics4McL2StaticAirframeRunMatrix.StaticAirframeRunMatrixAudit runMatrix,
			Aerodynamics4McL2DroneProbeAudit.DroneProbeGeometryAudit geometryAudit
	) {
		if (runMatrix == null || geometryAudit == null) {
			throw new IllegalArgumentException("run matrix and geometry audit are required.");
		}
		List<StaticAirframeCoefficientSeed> seeds = runMatrix.runs().stream()
				.map(run -> seed(run, findGeometry(geometryAudit.presets(), run.presetName())))
				.toList();
		return new StaticAirframeCoefficientAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				PRESET_SAMPLE_COUNT,
				COEFFICIENT_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				seeds,
				extrema(seeds)
		);
	}

	public static StaticAirframeCoefficientSeed seed(
			Aerodynamics4McL2StaticAirframeRunMatrix.StaticAirframeRunSummary run,
			Aerodynamics4McL2DroneProbeAudit.PresetProbeSummary geometry
	) {
		if (run == null || geometry == null) {
			throw new IllegalArgumentException("run and geometry summaries are required.");
		}
		if (!run.presetName().equals(geometry.presetName())) {
			throw new IllegalArgumentException("run and geometry preset names must match.");
		}
		double referenceArea = Math.PI * geometry.bodyHalfX() * geometry.bodyHalfY();
		double referenceLength = geometry.rotorSpanMeters();
		double dynamicPressure = 0.5 * AIR_DENSITY_KG_M3
				* run.inletSpeedMetersPerSecond()
				* run.inletSpeedMetersPerSecond();
		double forceScale = dynamicPressure * Math.max(1.0e-12, referenceArea);
		double momentScale = forceScale * Math.max(1.0e-12, referenceLength);
		Vec3 force = new Vec3(run.forceXNewtons(), run.forceYNewtons(), run.forceZNewtons());
		Vec3 inlet = new Vec3(
				geometry.inletVxMetersPerSecond(),
				geometry.inletVyMetersPerSecond(),
				geometry.inletVzMetersPerSecond()
		);
		Vec3 dragDirection = run.inletSpeedMetersPerSecond() <= 1.0e-12
				? Vec3.ZERO
				: inlet.multiply(-1.0 / run.inletSpeedMetersPerSecond());
		double dragCoefficient = force.dot(dragDirection) / forceScale;
		boolean ready = run.available()
				&& run.hasForceMoment()
				&& dynamicPressure > 1.0e-12
				&& referenceArea > 1.0e-12
				&& referenceLength > 1.0e-12;
		return new StaticAirframeCoefficientSeed(
				run.presetName(),
				ready,
				run.available(),
				referenceArea,
				referenceLength,
				AIR_DENSITY_KG_M3,
				dynamicPressure,
				run.forceXNewtons() / forceScale,
				run.forceYNewtons() / forceScale,
				run.forceZNewtons() / forceScale,
				dragCoefficient,
				run.forceXNewtons() / forceScale,
				run.forceYNewtons() / forceScale,
				run.momentXNewtonMeters() / momentScale,
				run.momentYNewtonMeters() / momentScale,
				run.momentZNewtonMeters() / momentScale,
				run.momentMagnitudeNewtonMeters() / momentScale,
				run.pressureCenterOffsetMeters() / Math.max(1.0e-12, referenceLength),
				run.status(),
				run.runtimeInfo()
		);
	}

	private static Aerodynamics4McL2DroneProbeAudit.PresetProbeSummary findGeometry(
			List<Aerodynamics4McL2DroneProbeAudit.PresetProbeSummary> geometries,
			String presetName
	) {
		for (Aerodynamics4McL2DroneProbeAudit.PresetProbeSummary geometry : geometries) {
			if (geometry.presetName().equals(presetName)) {
				return geometry;
			}
		}
		throw new IllegalArgumentException("missing geometry for preset: " + presetName);
	}

	private static StaticAirframeCoefficientExtrema extrema(List<StaticAirframeCoefficientSeed> seeds) {
		int ready = 0;
		double maxArea = 0.0;
		double maxDynamicPressure = 0.0;
		double maxDrag = 0.0;
		double maxForceMagnitude = 0.0;
		double maxMomentMagnitude = 0.0;
		double maxPressureCenter = 0.0;
		for (StaticAirframeCoefficientSeed seed : seeds) {
			if (seed.coefficientFitReady()) {
				ready++;
			}
			maxArea = Math.max(maxArea, seed.referenceAreaSquareMeters());
			maxDynamicPressure = Math.max(maxDynamicPressure, seed.dynamicPressurePascals());
			maxDrag = Math.max(maxDrag, seed.dragCoefficient());
			maxForceMagnitude = Math.max(maxForceMagnitude, magnitude(
					seed.forceCoefficientX(),
					seed.forceCoefficientY(),
					seed.forceCoefficientZ()
			));
			maxMomentMagnitude = Math.max(maxMomentMagnitude, seed.momentCoefficientMagnitude());
			maxPressureCenter = Math.max(maxPressureCenter, seed.pressureCenterOffsetRatio());
		}
		return new StaticAirframeCoefficientExtrema(
				seeds.size(),
				ready,
				maxArea,
				maxDynamicPressure,
				maxDrag,
				maxForceMagnitude,
				maxMomentMagnitude,
				maxPressureCenter
		);
	}

	private static double magnitude(double x, double y, double z) {
		return Math.sqrt(x * x + y * y + z * z);
	}
}
