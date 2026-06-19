package com.tenicana.dronecraft.camera;

import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class FpvCameraOrientation {
	private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);
	private static final Vector3f FORWARD_REFERENCE = new Vector3f(0.0f, 0.0f, -1.0f);
	private static final Vector3f UP_REFERENCE = new Vector3f(0.0f, 1.0f, 0.0f);
	private static final Vector3f LEFT_REFERENCE = new Vector3f(-1.0f, 0.0f, 0.0f);

	private FpvCameraOrientation() {
	}

	public static Orientation fromFpvMount(
			float bodyYawDegrees,
			float bodyPitchDegrees,
			float bodyRollRadians,
			float cameraTiltDegrees,
			float shakePitchDegrees,
			float shakeYawDegrees,
			float shakeRollRadians
	) {
		float cameraYawDegrees = bodyYawDegrees + finiteOrZero(shakeYawDegrees);
		float cameraPitchDegrees = bodyPitchDegrees - finiteOrZero(cameraTiltDegrees) + finiteOrZero(shakePitchDegrees);
		float cameraRollRadians = bodyRollRadians + finiteOrZero(shakeRollRadians);
		return fromCameraAngles(cameraYawDegrees, cameraPitchDegrees, cameraRollRadians);
	}

	public static Orientation fromCameraAngles(float yawDegrees, float pitchDegrees, float rollRadians) {
		float safeYawDegrees = finiteOrZero(yawDegrees);
		float safePitchDegrees = finiteOrZero(pitchDegrees);
		float safeRollRadians = finiteOrZero(rollRadians);
		Quaternionf rotation = new Quaternionf().rotationYXZ(
				(float) Math.PI - safeYawDegrees * DEG_TO_RAD,
				-safePitchDegrees * DEG_TO_RAD,
				-safeRollRadians
		).normalize();
		Vector3f forwards = FORWARD_REFERENCE.rotate(rotation, new Vector3f()).normalize();
		Vector3f up = UP_REFERENCE.rotate(rotation, new Vector3f()).normalize();
		Vector3f left = LEFT_REFERENCE.rotate(rotation, new Vector3f()).normalize();
		return new Orientation(rotation, forwards, up, left, safeYawDegrees, safePitchDegrees);
	}

	private static float finiteOrZero(float value) {
		return Float.isFinite(value) ? value : 0.0f;
	}

	public record Orientation(
			Quaternionf rotation,
			Vector3f forwards,
			Vector3f up,
			Vector3f left,
			float yawDegrees,
			float pitchDegrees
	) {
	}
}
