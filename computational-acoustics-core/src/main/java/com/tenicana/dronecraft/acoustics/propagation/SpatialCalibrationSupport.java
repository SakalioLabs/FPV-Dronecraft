package com.tenicana.dronecraft.acoustics.propagation;

import java.util.Objects;

/**
 * Separate source/listener domains prevent a fixed training source from
 * accidentally expanding the admissible listener interpolation region.
 */
public record SpatialCalibrationSupport(
		SpatialSupportBounds sourceBounds,
		SpatialSupportBounds listenerBounds
) {
	public SpatialCalibrationSupport {
		Objects.requireNonNull(sourceBounds, "sourceBounds");
		Objects.requireNonNull(listenerBounds, "listenerBounds");
	}

	public boolean supports(
			double sourceX,
			double sourceY,
			double sourceZ,
			double listenerX,
			double listenerY,
			double listenerZ
	) {
		return sourceBounds.contains(sourceX, sourceY, sourceZ)
				&& listenerBounds.contains(listenerX, listenerY, listenerZ);
	}
}
