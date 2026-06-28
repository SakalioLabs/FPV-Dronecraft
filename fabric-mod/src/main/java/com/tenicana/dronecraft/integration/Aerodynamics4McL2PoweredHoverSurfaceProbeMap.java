package com.tenicana.dronecraft.integration;

import java.util.ArrayList;
import java.util.List;

import com.tenicana.dronecraft.sim.SurfaceNearfieldCalibration;
import com.tenicana.dronecraft.sim.Vec3;

public final class Aerodynamics4McL2PoweredHoverSurfaceProbeMap {
	private static final double[] CLEARANCE_OVER_RADIUS_SAMPLES = { 0.5, 1.0, 2.0, 4.0 };
	private static final String[] SURFACE_SAMPLES = { "ground", "ceiling" };
	private static final double EPSILON = 1.0e-12;

	public static final String SOURCE_ID = "A4MC-L2-Powered-Hover-Surface-Probe-Map-Packet";
	public static final String CAVEAT =
			"Rotor-resolved powered-hover surface probe map for future A4MC local wake validation; keep audit-only until live powered-source pressure and velocity probes recover the expected per-rotor wake and surface-reaction targets.";
	public static final int SOURCE_REFERENCE_COUNT = 6;
	public static final int PRESET_SAMPLE_COUNT = 4;
	public static final int ROTOR_SAMPLE_COUNT = 4;
	public static final int CLEARANCE_SAMPLE_COUNT = 4;
	public static final int SURFACE_SAMPLE_COUNT = 2;
	public static final int PROBE_SAMPLE_COUNT =
			PRESET_SAMPLE_COUNT * ROTOR_SAMPLE_COUNT * CLEARANCE_SAMPLE_COUNT * SURFACE_SAMPLE_COUNT;
	public static final int PROBE_METRIC_COUNT = 36;
	public static final int SUMMARY_METRIC_ROW_COUNT = 12;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ PROBE_SAMPLE_COUNT * PROBE_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredHoverSurfaceProbeMap() {
	}

	public record PoweredHoverSurfaceProbe(
			String presetName,
			String spinState,
			String surfaceType,
			double clearanceOverRadius,
			double clearanceMeters,
			int rotorIndex,
			double rotorCenterXBodyMeters,
			double rotorCenterYBodyMeters,
			double rotorCenterZBodyMeters,
			double thrustAxisXBody,
			double thrustAxisYBody,
			double thrustAxisZBody,
			double probeXBodyMeters,
			double probeYBodyMeters,
			double probeZBodyMeters,
			double surfaceNormalXBody,
			double surfaceNormalYBody,
			double surfaceNormalZBody,
			double surfaceOffsetAxisSign,
			double rotorDiskRadiusMeters,
			double rotorOpenAreaSquareMeters,
			double rotorThrustNewtons,
			double rotorPressureJumpPascals,
			double perRotorFarWakeAreaSquareMeters,
			double perRotorFarWakeEquivalentRadiusMeters,
			double expectedWakeSpeedMetersPerSecond,
			double perRotorImpingementPressurePascals,
			double surfaceCurveMultiplier,
			double surfaceExtraLiftFraction,
			double perRotorSurfaceCushionForceNewtons,
			double surfaceCushionForceOverRotorThrust,
			double perRotorSurfaceReactionPressurePascals,
			boolean localProbeApiAvailable,
			boolean runtimeCouplingAllowed,
			boolean validationBeforeRuntimeRequired,
			String status,
			String runtimeInfo
	) {
	}

	public record PoweredHoverSurfaceProbeExtrema(
			int probeCount,
			int groundProbeCount,
			int ceilingProbeCount,
			int rotorProbeCount,
			double maxPerRotorImpingementPressurePascals,
			double maxPerRotorSurfaceCushionForceNewtons,
			double maxPerRotorSurfaceReactionPressurePascals,
			double maxClearanceMeters,
			double maxSurfaceCurveMultiplier,
			double minSurfaceCurveMultiplier,
			int runtimeCouplingAllowedCount,
			int localProbeApiAvailableCount
	) {
	}

	public record PoweredHoverSurfaceProbeAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int presetSampleCount,
			int rotorSampleCount,
			int clearanceSampleCount,
			int surfaceSampleCount,
			int probeSampleCount,
			int probeMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredHoverSurfaceProbe> probes,
			PoweredHoverSurfaceProbeExtrema extrema
	) {
		public PoweredHoverSurfaceProbeAudit {
			probes = List.copyOf(probes);
		}
	}

	public static PoweredHoverSurfaceProbeAudit audit() {
		Aerodynamics4McL2PoweredHoverSourceMap.PoweredHoverSourceMapAudit sourceMapAudit =
				Aerodynamics4McL2PoweredHoverSourceMap.audit();
		List<PoweredHoverSurfaceProbe> probes = new ArrayList<>(PROBE_SAMPLE_COUNT);
		for (Aerodynamics4McL2PoweredHoverSourceMap.PoweredHoverSourceMap sourceMap : sourceMapAudit.sourceMaps()) {
			probes.addAll(probes(sourceMap));
		}
		return new PoweredHoverSurfaceProbeAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				PRESET_SAMPLE_COUNT,
				ROTOR_SAMPLE_COUNT,
				CLEARANCE_SAMPLE_COUNT,
				SURFACE_SAMPLE_COUNT,
				PROBE_SAMPLE_COUNT,
				PROBE_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				probes,
				extrema(probes)
		);
	}

	public static List<PoweredHoverSurfaceProbe> probes(
			Aerodynamics4McL2PoweredHoverSourceMap.PoweredHoverSourceMap sourceMap
	) {
		if (sourceMap == null || !"hover".equals(sourceMap.spinState())) {
			throw new IllegalArgumentException("sourceMap must be a powered hover source-map row.");
		}
		List<PoweredHoverSurfaceProbe> probes = new ArrayList<>(
				sourceMap.sourceTerms().size() * CLEARANCE_SAMPLE_COUNT * SURFACE_SAMPLE_COUNT
		);
		for (String surfaceType : SURFACE_SAMPLES) {
			for (double clearanceOverRadius : CLEARANCE_OVER_RADIUS_SAMPLES) {
				for (Aerodynamics4McL2ActuatorDiskSourceMap.RotorDiskSourceTerm sourceTerm : sourceMap.sourceTerms()) {
					probes.add(probe(sourceMap, sourceTerm, surfaceType, clearanceOverRadius));
				}
			}
		}
		return List.copyOf(probes);
	}

	public static PoweredHoverSurfaceProbe probe(
			Aerodynamics4McL2PoweredHoverSourceMap.PoweredHoverSourceMap sourceMap,
			Aerodynamics4McL2ActuatorDiskSourceMap.RotorDiskSourceTerm sourceTerm,
			String surfaceType,
			double clearanceOverRadius
	) {
		if (sourceMap == null || sourceTerm == null) {
			throw new IllegalArgumentException("source map and source term are required.");
		}
		if (!"hover".equals(sourceMap.spinState())) {
			throw new IllegalArgumentException("surface probe map requires a hover source map.");
		}
		String surface = surfaceType == null ? "" : surfaceType.trim();
		if (!"ground".equals(surface) && !"ceiling".equals(surface)) {
			throw new IllegalArgumentException("surfaceType must be ground or ceiling.");
		}
		double normalizedClearance = Math.max(0.0, finiteOrZero(clearanceOverRadius));
		double clearanceMeters = sourceTerm.diskRadiusMeters() * normalizedClearance;
		Vec3 thrustAxis = sourceTerm.thrustAxisBody().normalized();
		double surfaceOffsetSign = "ceiling".equals(surface) ? 1.0 : -1.0;
		Vec3 probePoint = sourceTerm.centerBodyMeters().add(thrustAxis.multiply(surfaceOffsetSign * clearanceMeters));
		Vec3 surfaceNormal = thrustAxis.multiply(-surfaceOffsetSign);
		double farWakeArea = 0.5 * sourceTerm.openAreaSquareMeters();
		double surfaceMultiplier = "ceiling".equals(surface)
				? SurfaceNearfieldCalibration.jirsCeilingCurveFitMultiplier(normalizedClearance)
				: SurfaceNearfieldCalibration.jirsGroundCurveFitMultiplier(normalizedClearance);
		double surfaceExtra = Math.max(0.0, surfaceMultiplier - 1.0);
		double cushionForce = sourceTerm.thrustNewtons() * surfaceExtra;
		return new PoweredHoverSurfaceProbe(
				sourceMap.presetName(),
				sourceMap.spinState(),
				surface,
				normalizedClearance,
				clearanceMeters,
				sourceTerm.rotorIndex(),
				sourceTerm.centerBodyMeters().x(),
				sourceTerm.centerBodyMeters().y(),
				sourceTerm.centerBodyMeters().z(),
				thrustAxis.x(),
				thrustAxis.y(),
				thrustAxis.z(),
				probePoint.x(),
				probePoint.y(),
				probePoint.z(),
				surfaceNormal.x(),
				surfaceNormal.y(),
				surfaceNormal.z(),
				surfaceOffsetSign,
				sourceTerm.diskRadiusMeters(),
				sourceTerm.openAreaSquareMeters(),
				sourceTerm.thrustNewtons(),
				sourceTerm.pressureJumpPascals(),
				farWakeArea,
				equivalentRadius(farWakeArea),
				sourceMap.farWakeVelocityMetersPerSecond(),
				ratio(sourceTerm.thrustNewtons(), farWakeArea),
				surfaceMultiplier,
				surfaceExtra,
				cushionForce,
				ratio(cushionForce, sourceTerm.thrustNewtons()),
				ratio(cushionForce, farWakeArea),
				false,
				false,
				true,
				"target-only-rotor-surface-probe-unavailable",
				"audit-only-unvalidated-rotor-surface-probe"
		);
	}

	private static PoweredHoverSurfaceProbeExtrema extrema(List<PoweredHoverSurfaceProbe> probes) {
		int ground = 0;
		int ceiling = 0;
		double maxImpingementPressure = 0.0;
		double maxCushionForce = 0.0;
		double maxReactionPressure = 0.0;
		double maxClearance = 0.0;
		double maxMultiplier = 0.0;
		double minMultiplier = Double.POSITIVE_INFINITY;
		int runtimeAllowed = 0;
		int localApiAvailable = 0;
		for (PoweredHoverSurfaceProbe probe : probes) {
			if ("ground".equals(probe.surfaceType())) {
				ground++;
			}
			if ("ceiling".equals(probe.surfaceType())) {
				ceiling++;
			}
			maxImpingementPressure = Math.max(maxImpingementPressure, probe.perRotorImpingementPressurePascals());
			maxCushionForce = Math.max(maxCushionForce, probe.perRotorSurfaceCushionForceNewtons());
			maxReactionPressure = Math.max(maxReactionPressure, probe.perRotorSurfaceReactionPressurePascals());
			maxClearance = Math.max(maxClearance, probe.clearanceMeters());
			maxMultiplier = Math.max(maxMultiplier, probe.surfaceCurveMultiplier());
			minMultiplier = Math.min(minMultiplier, probe.surfaceCurveMultiplier());
			if (probe.runtimeCouplingAllowed()) {
				runtimeAllowed++;
			}
			if (probe.localProbeApiAvailable()) {
				localApiAvailable++;
			}
		}
		return new PoweredHoverSurfaceProbeExtrema(
				probes.size(),
				ground,
				ceiling,
				probes.size(),
				maxImpingementPressure,
				maxCushionForce,
				maxReactionPressure,
				maxClearance,
				maxMultiplier,
				probes.isEmpty() ? 0.0 : minMultiplier,
				runtimeAllowed,
				localApiAvailable
		);
	}

	private static double equivalentRadius(double areaSquareMeters) {
		return areaSquareMeters <= EPSILON ? 0.0 : Math.sqrt(areaSquareMeters / Math.PI);
	}

	private static double finiteOrZero(double value) {
		return Double.isFinite(value) ? value : 0.0;
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= EPSILON) {
			return 0.0;
		}
		return numerator / denominator;
	}
}
