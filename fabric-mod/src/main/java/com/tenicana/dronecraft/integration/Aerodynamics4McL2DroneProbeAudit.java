package com.tenicana.dronecraft.integration;

import java.util.List;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.RotorSpec;
import com.tenicana.dronecraft.sim.Vec3;

public final class Aerodynamics4McL2DroneProbeAudit {
	public static final String SOURCE_ID = "A4MC-L2-Drone-Wind-Tunnel-Geometry-Packet";
	public static final String CAVEAT =
			"Geometry-only packet mirrors the current Java L2 drone probe mask and request builder; fit force/moment coefficients against live A4MC L2 runs before treating them as calibrated aerodynamics.";
	public static final int SOURCE_REFERENCE_COUNT = 5;
	public static final int PRESET_SAMPLE_COUNT = 4;
	public static final int PROBE_METRIC_COUNT = 34;
	public static final int SUMMARY_METRIC_ROW_COUNT = 6;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ PRESET_SAMPLE_COUNT * PROBE_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2DroneProbeAudit() {
	}

	public record PresetProbeSummary(
			String presetName,
			int rotorCount,
			double massKg,
			int nx,
			int ny,
			int nz,
			int gridCellCount,
			int maskByteLength,
			double cellSizeMeters,
			int solidCellCount,
			double solidFraction,
			double maxRotorRadiusMeters,
			double rotorSpanMeters,
			double rotorDiskRadiusCells,
			double bodyHalfX,
			double bodyHalfY,
			double bodyHalfZ,
			double armRadiusMeters,
			double hubRadiusMeters,
			double inletVxMetersPerSecond,
			double inletVyMetersPerSecond,
			double inletVzMetersPerSecond,
			double inletSpeedMetersPerSecond,
			int steps,
			boolean outputFlowAtlas,
			boolean computeForceMoment,
			double referenceX,
			double referenceY,
			double referenceZ,
			boolean solidMaskPresent,
			boolean bodyCenterSolid,
			int rotorHubSolidCount,
			boolean allRotorHubsSolid,
			int openRotorDiskSampleCount,
			boolean allRotorDiskSamplesOpen
	) {
	}

	public record DroneProbeGeometryExtrema(
			int maxGridCellCount,
			double minSolidFraction,
			double maxSolidFraction,
			double maxRotorSpanMeters,
			double maxRotorDiskRadiusCells,
			double maxInletSpeedMetersPerSecond
	) {
	}

	public record DroneProbeGeometryAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int presetSampleCount,
			int probeMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			PresetProbeSummary racingQuad,
			PresetProbeSummary apDrone,
			PresetProbeSummary cinewhoop,
			PresetProbeSummary heavyLift,
			DroneProbeGeometryExtrema extrema
	) {
		public List<PresetProbeSummary> presets() {
			return List.of(racingQuad, apDrone, cinewhoop, heavyLift);
		}
	}

	public static DroneProbeGeometryAudit audit() {
		PresetProbeSummary racingQuad = summary(
				"racingQuad",
				DroneConfig.racingQuad(),
				new Vec3(0.0, 0.0, -18.0),
				72
		);
		PresetProbeSummary apDrone = summary(
				"apDrone",
				DroneConfig.apDrone(),
				new Vec3(0.0, 0.0, -14.0),
				64
		);
		PresetProbeSummary cinewhoop = summary(
				"cinewhoop",
				DroneConfig.cinewhoop(),
				new Vec3(0.0, 0.0, -8.0),
				48
		);
		PresetProbeSummary heavyLift = summary(
				"heavyLift",
				DroneConfig.heavyLift(),
				new Vec3(0.0, 0.0, -12.0),
				80
		);
		return new DroneProbeGeometryAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				PRESET_SAMPLE_COUNT,
				PROBE_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				racingQuad,
				apDrone,
				cinewhoop,
				heavyLift,
				extrema(List.of(racingQuad, apDrone, cinewhoop, heavyLift))
		);
	}

	public static PresetProbeSummary summary(String presetName, DroneConfig config, Vec3 inletVelocity, int steps) {
		if (config == null || config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}
		String name = presetName == null || presetName.isBlank() ? "custom" : presetName;
		Aerodynamics4McL2DroneProbe.DroneWindTunnelProbe probe =
				Aerodynamics4McL2DroneProbe.forceMomentProbe(config, inletVelocity, steps);
		Aerodynamics4McL2Bridge.L2RequestSpec request = probe.requestSpec();
		int gridCells = probe.nx() * probe.ny() * probe.nz();
		byte[] mask = request.solidMask();
		boolean bodyCenterSolid = probe.solidAtBodyPosition(Vec3.ZERO);
		int hubSolidCount = countSolidRotorHubs(config, probe);
		int openDiskSamples = countOpenRotorDiskSamples(config, probe);
		return new PresetProbeSummary(
				name,
				config.rotors().size(),
				config.massKg(),
				probe.nx(),
				probe.ny(),
				probe.nz(),
				gridCells,
				mask == null ? 0 : mask.length,
				probe.cellSizeMeters(),
				probe.solidCellCount(),
				probe.solidCellCount() / (double) Math.max(1, gridCells),
				probe.maxRotorRadiusMeters(),
				probe.rotorSpanMeters(),
				probe.maxRotorRadiusMeters() / Math.max(1.0e-9, probe.cellSizeMeters()),
				probe.bodyHalfX(),
				probe.bodyHalfY(),
				probe.bodyHalfZ(),
				probe.armRadiusMeters(),
				probe.hubRadiusMeters(),
				request.inletVx(),
				request.inletVy(),
				request.inletVz(),
				new Vec3(request.inletVx(), request.inletVy(), request.inletVz()).length(),
				request.steps(),
				request.outputFlowAtlas(),
				request.computeForceMoment(),
				request.referenceX(),
				request.referenceY(),
				request.referenceZ(),
				mask != null && mask.length == gridCells,
				bodyCenterSolid,
				hubSolidCount,
				hubSolidCount == config.rotors().size(),
				openDiskSamples,
				openDiskSamples == config.rotors().size()
		);
	}

	private static DroneProbeGeometryExtrema extrema(List<PresetProbeSummary> summaries) {
		int maxGridCellCount = 0;
		double minSolidFraction = Double.POSITIVE_INFINITY;
		double maxSolidFraction = 0.0;
		double maxRotorSpan = 0.0;
		double maxRotorDiskRadiusCells = 0.0;
		double maxInletSpeed = 0.0;
		for (PresetProbeSummary summary : summaries) {
			maxGridCellCount = Math.max(maxGridCellCount, summary.gridCellCount());
			minSolidFraction = Math.min(minSolidFraction, summary.solidFraction());
			maxSolidFraction = Math.max(maxSolidFraction, summary.solidFraction());
			maxRotorSpan = Math.max(maxRotorSpan, summary.rotorSpanMeters());
			maxRotorDiskRadiusCells = Math.max(maxRotorDiskRadiusCells, summary.rotorDiskRadiusCells());
			maxInletSpeed = Math.max(maxInletSpeed, summary.inletSpeedMetersPerSecond());
		}
		return new DroneProbeGeometryExtrema(
				maxGridCellCount,
				minSolidFraction,
				maxSolidFraction,
				maxRotorSpan,
				maxRotorDiskRadiusCells,
				maxInletSpeed
		);
	}

	private static int countSolidRotorHubs(
			DroneConfig config,
			Aerodynamics4McL2DroneProbe.DroneWindTunnelProbe probe
	) {
		int count = 0;
		for (RotorSpec rotor : config.rotors()) {
			if (probe.solidAtBodyPosition(rotor.positionBodyMeters())) {
				count++;
			}
		}
		return count;
	}

	private static int countOpenRotorDiskSamples(
			DroneConfig config,
			Aerodynamics4McL2DroneProbe.DroneWindTunnelProbe probe
	) {
		int count = 0;
		for (RotorSpec rotor : config.rotors()) {
			Vec3 radial = new Vec3(rotor.positionBodyMeters().x(), 0.0, rotor.positionBodyMeters().z());
			Vec3 outward = radial.lengthSquared() <= 1.0e-12 ? new Vec3(1.0, 0.0, 0.0) : radial.normalized();
			Vec3 diskSample = rotor.positionBodyMeters().add(outward.multiply(rotor.radiusMeters() * 0.75));
			if (!probe.solidAtBodyPosition(diskSample)) {
				count++;
			}
		}
		return count;
	}
}
