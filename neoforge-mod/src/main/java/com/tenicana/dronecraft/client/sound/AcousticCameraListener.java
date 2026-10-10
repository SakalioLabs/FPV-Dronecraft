package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.AcousticListenerFrame;
import com.tenicana.dronecraft.acoustics.AcousticVector;

import java.util.Objects;

/**
 * Shares the actual audio camera position without differentiating visual shake.
 * Only the client thread mutates this tracker; workers receive the epoch and
 * immutable acoustic frame, never the world or entity references in View.
 */
final class AcousticCameraListener {
	private static final double DISCONTINUITY_DISTANCE_METERS = 16.0;
	private long epoch;
	private Sample latest;

	Sample capture(View view, AcousticVector position, AcousticVector ownerVelocity) {
		Objects.requireNonNull(view, "view");
		Objects.requireNonNull(position, "position");
		Objects.requireNonNull(ownerVelocity, "ownerVelocity");
		if (latest == null || !latest.view().sameView(view)
				|| position.subtract(latest.frame().positionMeters()).length()
						> DISCONTINUITY_DISTANCE_METERS) {
			epoch++;
		}
		latest = new Sample(epoch, view, new AcousticListenerFrame(position, ownerVelocity));
		return latest;
	}

	Sample latestFor(Object world) {
		return latest != null && latest.view().world() == world ? latest : null;
	}

	boolean isCurrent(long candidateEpoch) {
		return latest != null && latest.epoch() == candidateEpoch;
	}

	void reset() {
		latest = null;
	}

	record View(Object world, Object cameraEntity, Object velocityOwner, int viewMode) {
		View {
			Objects.requireNonNull(world, "world");
			Objects.requireNonNull(cameraEntity, "cameraEntity");
			Objects.requireNonNull(velocityOwner, "velocityOwner");
		}

		private boolean sameView(View other) {
			return world == other.world && cameraEntity == other.cameraEntity
					&& velocityOwner == other.velocityOwner && viewMode == other.viewMode;
		}
	}

	record Sample(long epoch, View view, AcousticListenerFrame frame) {
	}
}
