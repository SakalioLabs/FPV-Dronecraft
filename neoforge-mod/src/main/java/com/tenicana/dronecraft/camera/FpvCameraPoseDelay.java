package com.tenicana.dronecraft.camera;

public final class FpvCameraPoseDelay {
	private static final int CAPACITY = 96;
	private static final double MAX_DELAY_SECONDS = 0.35;
	private static final double REWIND_TOLERANCE_SECONDS = 1.0e-4;
	private static final double DUPLICATE_TOLERANCE_SECONDS = 1.0e-6;
	private static final double FULL_TURN_DEGREES = 360.0;
	private static final double HALF_TURN_DEGREES = 180.0;
	private static final double FULL_TURN_RADIANS = Math.PI * 2.0;

	private final Pose[] samples = new Pose[CAPACITY];
	private int newestIndex = -1;
	private int size;

	public void reset() {
		newestIndex = -1;
		size = 0;
	}

	public Pose sample(Pose sample, double latencySeconds) {
		if (!isFinite(sample) || !Double.isFinite(latencySeconds)) {
			reset();
			return sample;
		}

		if (size > 0 && sample.timeSeconds() < newest().timeSeconds() - REWIND_TOLERANCE_SECONDS) {
			reset();
		}

		if (latencySeconds <= REWIND_TOLERANCE_SECONDS) {
			reset();
			push(sample);
			return sample;
		}

		push(sample);

		double delay = clamp(latencySeconds, 0.0, MAX_DELAY_SECONDS);
		double targetTime = sample.timeSeconds() - delay;
		Pose newest = sampleFromNewest(0);
		if (size == 1 || targetTime >= newest.timeSeconds()) {
			return newest.withTimeSeconds(targetTime);
		}

		Pose oldest = sampleFromNewest(size - 1);
		if (targetTime <= oldest.timeSeconds()) {
			return oldest.withTimeSeconds(targetTime);
		}

		for (int offset = 0; offset < size - 1; offset++) {
			Pose newer = sampleFromNewest(offset);
			Pose older = sampleFromNewest(offset + 1);
			if (targetTime >= older.timeSeconds() && targetTime <= newer.timeSeconds()) {
				double span = newer.timeSeconds() - older.timeSeconds();
				double blend = span <= DUPLICATE_TOLERANCE_SECONDS ? 0.0 : (targetTime - older.timeSeconds()) / span;
				return Pose.interpolate(older, newer, blend, targetTime);
			}
		}

		return oldest.withTimeSeconds(targetTime);
	}

	private void push(Pose sample) {
		if (size > 0 && sample.timeSeconds() <= newest().timeSeconds() + DUPLICATE_TOLERANCE_SECONDS) {
			samples[newestIndex] = sample;
			return;
		}

		newestIndex = (newestIndex + 1) % CAPACITY;
		samples[newestIndex] = sample;
		if (size < CAPACITY) {
			size++;
		}
	}

	private Pose newest() {
		return samples[newestIndex];
	}

	private Pose sampleFromNewest(int offset) {
		int index = newestIndex - offset;
		if (index < 0) {
			index += CAPACITY;
		}
		return samples[index];
	}

	private static boolean isFinite(Pose pose) {
		return Double.isFinite(pose.xMeters())
				&& Double.isFinite(pose.yMeters())
				&& Double.isFinite(pose.zMeters())
				&& Float.isFinite(pose.yawDegrees())
				&& Float.isFinite(pose.pitchDegrees())
				&& Float.isFinite(pose.rollRadians())
				&& Double.isFinite(pose.timeSeconds());
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static float interpolateAngleDegrees(float older, float newer, double blend) {
		return (float) (older + wrapDegrees(newer - older) * blend);
	}

	private static float interpolateAngleRadians(float older, float newer, double blend) {
		return (float) (older + wrapRadians(newer - older) * blend);
	}

	private static double wrapDegrees(double degrees) {
		double wrapped = degrees % FULL_TURN_DEGREES;
		if (wrapped >= HALF_TURN_DEGREES) {
			wrapped -= FULL_TURN_DEGREES;
		}
		if (wrapped < -HALF_TURN_DEGREES) {
			wrapped += FULL_TURN_DEGREES;
		}
		return wrapped;
	}

	private static double wrapRadians(double radians) {
		double wrapped = radians % FULL_TURN_RADIANS;
		if (wrapped >= Math.PI) {
			wrapped -= FULL_TURN_RADIANS;
		}
		if (wrapped < -Math.PI) {
			wrapped += FULL_TURN_RADIANS;
		}
		return wrapped;
	}

	public record Pose(
			double xMeters,
			double yMeters,
			double zMeters,
			float yawDegrees,
			float pitchDegrees,
			float rollRadians,
			double timeSeconds
	) {
		private static Pose interpolate(Pose older, Pose newer, double blend, double timeSeconds) {
			double boundedBlend = clamp(blend, 0.0, 1.0);
			return new Pose(
					lerp(older.xMeters(), newer.xMeters(), boundedBlend),
					lerp(older.yMeters(), newer.yMeters(), boundedBlend),
					lerp(older.zMeters(), newer.zMeters(), boundedBlend),
					interpolateAngleDegrees(older.yawDegrees(), newer.yawDegrees(), boundedBlend),
					interpolateAngleDegrees(older.pitchDegrees(), newer.pitchDegrees(), boundedBlend),
					interpolateAngleRadians(older.rollRadians(), newer.rollRadians(), boundedBlend),
					timeSeconds
			);
		}

		private Pose withTimeSeconds(double timeSeconds) {
			return new Pose(xMeters, yMeters, zMeters, yawDegrees, pitchDegrees, rollRadians, timeSeconds);
		}

		private static double lerp(double older, double newer, double blend) {
			return older + (newer - older) * blend;
		}
	}
}
