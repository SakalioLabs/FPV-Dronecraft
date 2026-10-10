package com.tenicana.dronecraft.acoustics.propagation;

import java.util.Arrays;
import java.util.Objects;

/**
 * Fixed-capacity control-rate matching and crossfade for explicit early
 * reflection clusters. Input band values are energy; output gains are linear
 * amplitude. Audio rendering is intentionally outside this class.
 */
public final class EarlyReflectionClusterSlew {
	public static final int MAXIMUM_INPUT_CLUSTERS =
			LocalPlaneSceneSolver.MAXIMUM_CANDIDATES;
	public static final int MAXIMUM_RENDER_SLOTS =
			MAXIMUM_INPUT_CLUSTERS * 2;
	public static final int DEFAULT_RAMP_SAMPLES = 2_400;
	public static final double DEFAULT_MATCH_WINDOW_SAMPLES = 96.0;

	private final double matchWindowSamples;
	private final int rampSamples;
	private final boolean[] active =
			new boolean[MAXIMUM_RENDER_SLOTS];
	private final double[] delay =
			new double[MAXIMUM_RENDER_SLOTS];
	private final double[] low =
			new double[MAXIMUM_RENDER_SLOTS];
	private final double[] mid =
			new double[MAXIMUM_RENDER_SLOTS];
	private final double[] high =
			new double[MAXIMUM_RENDER_SLOTS];
	private final double[] directionX =
			new double[MAXIMUM_RENDER_SLOTS];
	private final double[] directionY =
			new double[MAXIMUM_RENDER_SLOTS];
	private final double[] directionZ =
			new double[MAXIMUM_RENDER_SLOTS];
	private final boolean[] used =
			new boolean[MAXIMUM_RENDER_SLOTS];

	public EarlyReflectionClusterSlew() {
		this(DEFAULT_MATCH_WINDOW_SAMPLES, DEFAULT_RAMP_SAMPLES);
	}

	public EarlyReflectionClusterSlew(
			double matchWindowSamples,
			int rampSamples
	) {
		if (!Double.isFinite(matchWindowSamples)
				|| matchWindowSamples < 0.0) {
			throw new IllegalArgumentException(
					"match window must be finite and non-negative"
			);
		}
		if (rampSamples < 1) {
			throw new IllegalArgumentException(
					"ramp samples must be positive"
			);
		}
		this.matchWindowSamples = matchWindowSamples;
		this.rampSamples = rampSamples;
	}

	public Frame update(
			Input input,
			Frame output
	) {
		Objects.requireNonNull(input, "input");
		Objects.requireNonNull(output, "output");
		for (int slot = 0; slot < active.length; slot++) {
			if (active[slot]
					&& low[slot] == 0.0
					&& mid[slot] == 0.0
					&& high[slot] == 0.0) {
				active[slot] = false;
			}
		}
		Arrays.fill(used, false);
		output.reset(rampSamples);
		int inputCount = input.complete()
				? Math.min(
						input.clusterCount(),
						MAXIMUM_INPUT_CLUSTERS
				)
				: 0;
		for (int cluster = 0; cluster < inputCount; cluster++) {
			double nextDelay = input.clusterArrivalSamples(cluster);
			int slot = nearestSlot(nextDelay);
			if (slot < 0) {
				slot = firstInactiveSlot();
			}
			if (slot < 0) {
				throw new IllegalStateException(
						"early reflection render slots exhausted"
				);
			}
			double nextLow = amplitude(input.clusterLow(cluster));
			double nextMid = amplitude(input.clusterMid(cluster));
			double nextHigh = amplitude(input.clusterHigh(cluster));
			double nextX = input.clusterDirectionX(cluster);
			double nextY = input.clusterDirectionY(cluster);
			double nextZ = input.clusterDirectionZ(cluster);
			double length = Math.sqrt(
					nextX * nextX + nextY * nextY + nextZ * nextZ
			);
			if (length > 1.0e-15) {
				nextX /= length;
				nextY /= length;
				nextZ /= length;
			} else {
				nextX = 0.0;
				nextY = 0.0;
				nextZ = 1.0;
			}
			if (active[slot]) {
				output.append(
						slot,
						delay[slot], nextDelay,
						low[slot], nextLow,
						mid[slot], nextMid,
						high[slot], nextHigh,
						directionX[slot], directionY[slot],
						directionZ[slot],
						nextX, nextY, nextZ
				);
			} else {
				output.append(
						slot,
						nextDelay, nextDelay,
						0.0, nextLow,
						0.0, nextMid,
						0.0, nextHigh,
						nextX, nextY, nextZ,
						nextX, nextY, nextZ
				);
				active[slot] = true;
			}
			delay[slot] = nextDelay;
			low[slot] = nextLow;
			mid[slot] = nextMid;
			high[slot] = nextHigh;
			directionX[slot] = nextX;
			directionY[slot] = nextY;
			directionZ[slot] = nextZ;
			used[slot] = true;
		}
		for (int slot = 0; slot < active.length; slot++) {
			if (!active[slot] || used[slot]) {
				continue;
			}
			output.append(
					slot,
					delay[slot], delay[slot],
					low[slot], 0.0,
					mid[slot], 0.0,
					high[slot], 0.0,
					directionX[slot], directionY[slot],
					directionZ[slot],
					directionX[slot], directionY[slot],
					directionZ[slot]
			);
			low[slot] = 0.0;
			mid[slot] = 0.0;
			high[slot] = 0.0;
		}
		return output;
	}

	private int nearestSlot(double nextDelay) {
		int selected = -1;
		double selectedDistance = Double.POSITIVE_INFINITY;
		for (int slot = 0; slot < active.length; slot++) {
			if (!active[slot] || used[slot]) {
				continue;
			}
			double distance = Math.abs(delay[slot] - nextDelay);
			if (distance <= matchWindowSamples
					&& distance < selectedDistance) {
				selected = slot;
				selectedDistance = distance;
			}
		}
		return selected;
	}

	private int firstInactiveSlot() {
		for (int slot = 0; slot < active.length; slot++) {
			if (!active[slot]) {
				return slot;
			}
		}
		return -1;
	}

	private static double amplitude(double energy) {
		if (!Double.isFinite(energy) || energy < 0.0) {
			throw new IllegalArgumentException(
					"cluster energy must be finite and non-negative"
			);
		}
		return Math.sqrt(Math.min(1.0, energy));
	}

	public interface Input {
		boolean complete();

		int clusterCount();

		double clusterArrivalSamples(int index);

		double clusterLow(int index);

		double clusterMid(int index);

		double clusterHigh(int index);

		double clusterDirectionX(int index);

		double clusterDirectionY(int index);

		double clusterDirectionZ(int index);
	}

	public static final class Frame {
		private int rampSamples;
		private int slotCount;
		private final int[] slot = new int[MAXIMUM_RENDER_SLOTS];
		private final double[] startDelay =
				new double[MAXIMUM_RENDER_SLOTS];
		private final double[] targetDelay =
				new double[MAXIMUM_RENDER_SLOTS];
		private final double[] startLow =
				new double[MAXIMUM_RENDER_SLOTS];
		private final double[] targetLow =
				new double[MAXIMUM_RENDER_SLOTS];
		private final double[] startMid =
				new double[MAXIMUM_RENDER_SLOTS];
		private final double[] targetMid =
				new double[MAXIMUM_RENDER_SLOTS];
		private final double[] startHigh =
				new double[MAXIMUM_RENDER_SLOTS];
		private final double[] targetHigh =
				new double[MAXIMUM_RENDER_SLOTS];
		private final double[] startDirectionX =
				new double[MAXIMUM_RENDER_SLOTS];
		private final double[] startDirectionY =
				new double[MAXIMUM_RENDER_SLOTS];
		private final double[] startDirectionZ =
				new double[MAXIMUM_RENDER_SLOTS];
		private final double[] targetDirectionX =
				new double[MAXIMUM_RENDER_SLOTS];
		private final double[] targetDirectionY =
				new double[MAXIMUM_RENDER_SLOTS];
		private final double[] targetDirectionZ =
				new double[MAXIMUM_RENDER_SLOTS];

		private void reset(int nextRampSamples) {
			rampSamples = nextRampSamples;
			slotCount = 0;
		}

		private void append(
				int nextSlot,
				double nextStartDelay,
				double nextTargetDelay,
				double nextStartLow,
				double nextTargetLow,
				double nextStartMid,
				double nextTargetMid,
				double nextStartHigh,
				double nextTargetHigh,
				double nextStartDirectionX,
				double nextStartDirectionY,
				double nextStartDirectionZ,
				double nextTargetDirectionX,
				double nextTargetDirectionY,
				double nextTargetDirectionZ
		) {
			int index = slotCount++;
			slot[index] = nextSlot;
			startDelay[index] = nextStartDelay;
			targetDelay[index] = nextTargetDelay;
			startLow[index] = nextStartLow;
			targetLow[index] = nextTargetLow;
			startMid[index] = nextStartMid;
			targetMid[index] = nextTargetMid;
			startHigh[index] = nextStartHigh;
			targetHigh[index] = nextTargetHigh;
			startDirectionX[index] = nextStartDirectionX;
			startDirectionY[index] = nextStartDirectionY;
			startDirectionZ[index] = nextStartDirectionZ;
			targetDirectionX[index] = nextTargetDirectionX;
			targetDirectionY[index] = nextTargetDirectionY;
			targetDirectionZ[index] = nextTargetDirectionZ;
		}

		public int rampSamples() {
			return rampSamples;
		}

		public int slotCount() {
			return slotCount;
		}

		public int slot(int index) {
			return slot[checked(index)];
		}

		public double startDelaySamples(int index) {
			return startDelay[checked(index)];
		}

		public double targetDelaySamples(int index) {
			return targetDelay[checked(index)];
		}

		public double startLow(int index) {
			return startLow[checked(index)];
		}

		public double targetLow(int index) {
			return targetLow[checked(index)];
		}

		public double startMid(int index) {
			return startMid[checked(index)];
		}

		public double targetMid(int index) {
			return targetMid[checked(index)];
		}

		public double startHigh(int index) {
			return startHigh[checked(index)];
		}

		public double targetHigh(int index) {
			return targetHigh[checked(index)];
		}

		public double startDirectionX(int index) {
			return startDirectionX[checked(index)];
		}

		public double startDirectionY(int index) {
			return startDirectionY[checked(index)];
		}

		public double startDirectionZ(int index) {
			return startDirectionZ[checked(index)];
		}

		public double targetDirectionX(int index) {
			return targetDirectionX[checked(index)];
		}

		public double targetDirectionY(int index) {
			return targetDirectionY[checked(index)];
		}

		public double targetDirectionZ(int index) {
			return targetDirectionZ[checked(index)];
		}

		private int checked(int index) {
			if (index < 0 || index >= slotCount) {
				throw new IndexOutOfBoundsException(index);
			}
			return index;
		}
	}
}
