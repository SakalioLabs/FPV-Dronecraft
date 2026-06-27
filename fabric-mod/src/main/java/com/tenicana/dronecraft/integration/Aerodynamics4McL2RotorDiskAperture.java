package com.tenicana.dronecraft.integration;

import java.util.ArrayList;
import java.util.List;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.MathUtil;
import com.tenicana.dronecraft.sim.RotorSpec;
import com.tenicana.dronecraft.sim.Vec3;

public final class Aerodynamics4McL2RotorDiskAperture {
	public static final String SOURCE_ID = "A4MC-L2-Rotor-Disk-Aperture-Packet";
	public static final String CAVEAT =
			"Geometry-only actuator-disk aperture packet estimates static airframe blockage on each rotor disk; use live A4MC L2 force and pressure outputs before fitting powered rotor source terms.";
	public static final int SOURCE_REFERENCE_COUNT = 5;
	public static final int PRESET_SAMPLE_COUNT = 4;
	public static final int RADIAL_SAMPLE_COUNT = 4;
	public static final int AZIMUTH_SAMPLE_COUNT = 16;
	public static final int ROTOR_SAMPLE_COUNT = RADIAL_SAMPLE_COUNT * AZIMUTH_SAMPLE_COUNT;
	public static final int PRESET_METRIC_COUNT = 19;
	public static final int SUMMARY_METRIC_ROW_COUNT = 7;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ PRESET_SAMPLE_COUNT * PRESET_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2RotorDiskAperture() {
	}

	public record RotorDiskAperture(
			int rotorIndex,
			Vec3 centerBodyMeters,
			Vec3 thrustAxisBody,
			Vec3 radialAxisBody,
			Vec3 tangentialAxisBody,
			double radiusMeters,
			double diskAreaSquareMeters,
			double representativeBladeChordMeters,
			double axialInletSpeedMetersPerSecond,
			double inPlaneInletSpeedMetersPerSecond,
			int radialSampleCount,
			int azimuthSampleCount,
			int sampleCount,
			int openSampleCount,
			double openFraction,
			double openAreaSquareMeters,
			double blockedAreaSquareMeters
	) {
	}

	public record PresetApertureSummary(
			String presetName,
			int rotorCount,
			int radialSampleCount,
			int azimuthSampleCount,
			int sampleCount,
			int openSampleCount,
			double openFraction,
			double totalDiskAreaSquareMeters,
			double openDiskAreaSquareMeters,
			double blockedDiskAreaSquareMeters,
			double minRotorOpenFraction,
			double maxRotorOpenFraction,
			double meanRotorRadiusMeters,
			double meanRepresentativeBladeChordMeters,
			double maxAxialInletSpeedMetersPerSecond,
			double meanAxialInletSpeedMetersPerSecond,
			double maxInPlaneInletSpeedMetersPerSecond,
			double meanInPlaneInletSpeedMetersPerSecond,
			double maxRotorDiskRadiusCells
	) {
	}

	public record RotorDiskApertureExtrema(
			double minPresetOpenFraction,
			double maxPresetOpenFraction,
			double maxBlockedDiskAreaSquareMeters,
			double maxRotorDiskRadiusCells,
			double maxAxialInletSpeedMetersPerSecond,
			double maxInPlaneInletSpeedMetersPerSecond,
			int maxPresetSampleCount
	) {
	}

	public record RotorDiskApertureAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int presetSampleCount,
			int presetMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			int radialSampleCount,
			int azimuthSampleCount,
			int rotorSampleCount,
			PresetApertureSummary racingQuad,
			PresetApertureSummary apDrone,
			PresetApertureSummary cinewhoop,
			PresetApertureSummary heavyLift,
			RotorDiskApertureExtrema extrema
	) {
		public List<PresetApertureSummary> presets() {
			return List.of(racingQuad, apDrone, cinewhoop, heavyLift);
		}
	}

	public static RotorDiskApertureAudit audit() {
		PresetApertureSummary racingQuad = summary(
				"racingQuad",
				DroneConfig.racingQuad(),
				new Vec3(0.0, 0.0, -18.0),
				72
		);
		PresetApertureSummary apDrone = summary(
				"apDrone",
				DroneConfig.apDrone(),
				new Vec3(0.0, 0.0, -14.0),
				64
		);
		PresetApertureSummary cinewhoop = summary(
				"cinewhoop",
				DroneConfig.cinewhoop(),
				new Vec3(0.0, 0.0, -8.0),
				48
		);
		PresetApertureSummary heavyLift = summary(
				"heavyLift",
				DroneConfig.heavyLift(),
				new Vec3(0.0, 0.0, -12.0),
				80
		);
		return new RotorDiskApertureAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				PRESET_SAMPLE_COUNT,
				PRESET_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				RADIAL_SAMPLE_COUNT,
				AZIMUTH_SAMPLE_COUNT,
				ROTOR_SAMPLE_COUNT,
				racingQuad,
				apDrone,
				cinewhoop,
				heavyLift,
				extrema(List.of(racingQuad, apDrone, cinewhoop, heavyLift))
		);
	}

	public static PresetApertureSummary summary(String presetName, DroneConfig config, Vec3 inletVelocity, int steps) {
		if (config == null || config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}
		Aerodynamics4McL2DroneProbe.DroneWindTunnelProbe probe =
				Aerodynamics4McL2DroneProbe.forceMomentProbe(config, inletVelocity, steps);
		List<RotorDiskAperture> apertures = apertures(config, probe);
		int sampleCount = 0;
		int openSampleCount = 0;
		double totalDiskArea = 0.0;
		double openDiskArea = 0.0;
		double minRotorOpenFraction = 1.0;
		double maxRotorOpenFraction = 0.0;
		double radiusSum = 0.0;
		double chordSum = 0.0;
		double maxAxialInlet = 0.0;
		double axialInletSum = 0.0;
		double maxInPlaneInlet = 0.0;
		double inPlaneInletSum = 0.0;
		double maxRotorDiskRadiusCells = 0.0;
		for (RotorDiskAperture aperture : apertures) {
			sampleCount += aperture.sampleCount();
			openSampleCount += aperture.openSampleCount();
			totalDiskArea += aperture.diskAreaSquareMeters();
			openDiskArea += aperture.openAreaSquareMeters();
			minRotorOpenFraction = Math.min(minRotorOpenFraction, aperture.openFraction());
			maxRotorOpenFraction = Math.max(maxRotorOpenFraction, aperture.openFraction());
			radiusSum += aperture.radiusMeters();
			chordSum += aperture.representativeBladeChordMeters();
			maxAxialInlet = Math.max(maxAxialInlet, aperture.axialInletSpeedMetersPerSecond());
			axialInletSum += aperture.axialInletSpeedMetersPerSecond();
			maxInPlaneInlet = Math.max(maxInPlaneInlet, aperture.inPlaneInletSpeedMetersPerSecond());
			inPlaneInletSum += aperture.inPlaneInletSpeedMetersPerSecond();
			maxRotorDiskRadiusCells = Math.max(maxRotorDiskRadiusCells,
					aperture.radiusMeters() / Math.max(1.0e-9, probe.cellSizeMeters()));
		}
		double openFraction = sampleCount <= 0 ? 0.0 : openSampleCount / (double) sampleCount;
		String name = presetName == null || presetName.isBlank() ? "custom" : presetName;
		return new PresetApertureSummary(
				name,
				apertures.size(),
				RADIAL_SAMPLE_COUNT,
				AZIMUTH_SAMPLE_COUNT,
				sampleCount,
				openSampleCount,
				openFraction,
				totalDiskArea,
				openDiskArea,
				Math.max(0.0, totalDiskArea - openDiskArea),
				minRotorOpenFraction,
				maxRotorOpenFraction,
				radiusSum / Math.max(1, apertures.size()),
				chordSum / Math.max(1, apertures.size()),
				maxAxialInlet,
				axialInletSum / Math.max(1, apertures.size()),
				maxInPlaneInlet,
				inPlaneInletSum / Math.max(1, apertures.size()),
				maxRotorDiskRadiusCells
		);
	}

	public static List<RotorDiskAperture> apertures(
			DroneConfig config,
			Aerodynamics4McL2DroneProbe.DroneWindTunnelProbe probe
	) {
		if (config == null || config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}
		if (probe == null) {
			throw new IllegalArgumentException("probe must not be null.");
		}
		Vec3 inlet = new Vec3(
				probe.requestSpec().inletVx(),
				probe.requestSpec().inletVy(),
				probe.requestSpec().inletVz()
		);
		List<RotorDiskAperture> apertures = new ArrayList<>(config.rotors().size());
		for (int rotorIndex = 0; rotorIndex < config.rotors().size(); rotorIndex++) {
			RotorSpec rotor = config.rotors().get(rotorIndex);
			Vec3 thrustAxis = rotor.thrustAxisBody().normalized();
			double axialInlet = Math.abs(inlet.dot(thrustAxis));
			double inletSpeedSquared = inlet.lengthSquared();
			double inPlaneInlet = Math.sqrt(Math.max(0.0, inletSpeedSquared - axialInlet * axialInlet));
			Vec3 radialAxis = diskPlaneAxis(rotor.positionBodyMeters(), thrustAxis);
			Vec3 tangentialAxis = thrustAxis.cross(radialAxis).normalized();
			if (tangentialAxis.lengthSquared() <= 1.0e-12) {
				tangentialAxis = diskPlaneAxis(new Vec3(0.0, 0.0, 1.0), thrustAxis);
			}
			int openSampleCount = countOpenSamples(probe, rotor, radialAxis, tangentialAxis);
			double diskArea = Math.PI * rotor.radiusMeters() * rotor.radiusMeters();
			double openFraction = openSampleCount / (double) ROTOR_SAMPLE_COUNT;
			apertures.add(new RotorDiskAperture(
					rotorIndex,
					rotor.positionBodyMeters(),
					thrustAxis,
					radialAxis,
					tangentialAxis,
					rotor.radiusMeters(),
					diskArea,
					rotor.representativeBladeChordMeters(),
					axialInlet,
					inPlaneInlet,
					RADIAL_SAMPLE_COUNT,
					AZIMUTH_SAMPLE_COUNT,
					ROTOR_SAMPLE_COUNT,
					openSampleCount,
					openFraction,
					diskArea * openFraction,
					diskArea * (1.0 - openFraction)
			));
		}
		return List.copyOf(apertures);
	}

	private static RotorDiskApertureExtrema extrema(List<PresetApertureSummary> summaries) {
		double minPresetOpenFraction = Double.POSITIVE_INFINITY;
		double maxPresetOpenFraction = 0.0;
		double maxBlockedArea = 0.0;
		double maxRotorDiskRadiusCells = 0.0;
		double maxAxialInlet = 0.0;
		double maxInPlaneInlet = 0.0;
		int maxPresetSampleCount = 0;
		for (PresetApertureSummary summary : summaries) {
			minPresetOpenFraction = Math.min(minPresetOpenFraction, summary.openFraction());
			maxPresetOpenFraction = Math.max(maxPresetOpenFraction, summary.openFraction());
			maxBlockedArea = Math.max(maxBlockedArea, summary.blockedDiskAreaSquareMeters());
			maxRotorDiskRadiusCells = Math.max(maxRotorDiskRadiusCells, summary.maxRotorDiskRadiusCells());
			maxAxialInlet = Math.max(maxAxialInlet, summary.maxAxialInletSpeedMetersPerSecond());
			maxInPlaneInlet = Math.max(maxInPlaneInlet, summary.maxInPlaneInletSpeedMetersPerSecond());
			maxPresetSampleCount = Math.max(maxPresetSampleCount, summary.sampleCount());
		}
		return new RotorDiskApertureExtrema(
				minPresetOpenFraction,
				maxPresetOpenFraction,
				maxBlockedArea,
				maxRotorDiskRadiusCells,
				maxAxialInlet,
				maxInPlaneInlet,
				maxPresetSampleCount
		);
	}

	private static int countOpenSamples(
			Aerodynamics4McL2DroneProbe.DroneWindTunnelProbe probe,
			RotorSpec rotor,
			Vec3 radialAxis,
			Vec3 tangentialAxis
	) {
		int count = 0;
		for (int radialIndex = 0; radialIndex < RADIAL_SAMPLE_COUNT; radialIndex++) {
			double radius = equalAreaRadiusFraction(radialIndex) * rotor.radiusMeters();
			for (int azimuthIndex = 0; azimuthIndex < AZIMUTH_SAMPLE_COUNT; azimuthIndex++) {
				double angle = 2.0 * Math.PI * azimuthIndex / AZIMUTH_SAMPLE_COUNT;
				Vec3 offset = radialAxis.multiply(Math.cos(angle) * radius)
						.add(tangentialAxis.multiply(Math.sin(angle) * radius));
				Vec3 sample = rotor.positionBodyMeters().add(offset);
				if (!probe.solidAtBodyPosition(sample)) {
					count++;
				}
			}
		}
		return count;
	}

	private static double equalAreaRadiusFraction(int radialIndex) {
		double index = MathUtil.clamp(radialIndex, 0, RADIAL_SAMPLE_COUNT - 1);
		return Math.sqrt((index + 0.5) / RADIAL_SAMPLE_COUNT);
	}

	private static Vec3 diskPlaneAxis(Vec3 candidate, Vec3 normal) {
		Vec3 safeNormal = normal == null || !normal.isFinite() || normal.lengthSquared() <= 1.0e-12
				? new Vec3(0.0, 1.0, 0.0)
				: normal.normalized();
		Vec3 seed = candidate == null || !candidate.isFinite() || candidate.lengthSquared() <= 1.0e-12
				? fallbackAxisSeed(safeNormal)
				: candidate;
		Vec3 projected = seed.subtract(safeNormal.multiply(seed.dot(safeNormal)));
		if (projected.lengthSquared() <= 1.0e-12) {
			Vec3 fallback = fallbackAxisSeed(safeNormal);
			projected = fallback.subtract(safeNormal.multiply(fallback.dot(safeNormal)));
		}
		return projected.normalized();
	}

	private static Vec3 fallbackAxisSeed(Vec3 normal) {
		if (Math.abs(normal.y()) < 0.85) {
			return new Vec3(0.0, 1.0, 0.0);
		}
		return new Vec3(1.0, 0.0, 0.0);
	}
}
