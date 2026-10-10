package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticSourceFrame;
import com.tenicana.dronecraft.acoustics.AcousticVector;

import java.util.List;
import java.util.Objects;

/**
 * Five-point rotor-disk proxy used to make partial occlusion continuous.
 */
public final class RotorDiskProbes {
	private static final double CENTER_WEIGHT = 0.40;
	private static final double RIM_WEIGHT = 0.15;
	private static final double RIM_RADIUS_SCALE = 0.85;

	private RotorDiskProbes() {
	}

	public static List<MultiPathSolver.Probe> generate(AcousticSourceFrame source) {
		Objects.requireNonNull(source, "source");
		AcousticVector normal = source.rotorDiskNormal();
		AcousticVector reference = Math.abs(normal.y()) < 0.9
				? new AcousticVector(0.0, 1.0, 0.0)
				: new AcousticVector(1.0, 0.0, 0.0);
		AcousticVector tangentU = normal.cross(reference).normalized();
		AcousticVector tangentV = normal.cross(tangentU).normalized();
		double rimRadius = source.acousticApertureRadiusMeters() * RIM_RADIUS_SCALE;
		AcousticVector center = source.positionMeters();
		AcousticVector uOffset = tangentU.multiply(rimRadius);
		AcousticVector vOffset = tangentV.multiply(rimRadius);
		return List.of(
				new MultiPathSolver.Probe(center, CENTER_WEIGHT),
				new MultiPathSolver.Probe(center.add(uOffset), RIM_WEIGHT),
				new MultiPathSolver.Probe(center.subtract(uOffset), RIM_WEIGHT),
				new MultiPathSolver.Probe(center.add(vOffset), RIM_WEIGHT),
				new MultiPathSolver.Probe(center.subtract(vOffset), RIM_WEIGHT)
		);
	}
}
