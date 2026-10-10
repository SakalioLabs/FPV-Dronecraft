package com.tenicana.dronecraft.acoustics.propagation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EarlyReflectionClusterSlewTest {
	private static final double EPSILON = 1.0e-12;

	@Test
	void newClusterFadesInUsingAmplitudeFromEnergy() {
		EarlyReflectionClusterSlew slew =
				new EarlyReflectionClusterSlew();
		EarlyReflectionClusterSlew.Frame frame =
				new EarlyReflectionClusterSlew.Frame();

		slew.update(input(
				true,
				new double[]{120},
				new double[]{0.25},
				new double[]{0.36},
				new double[]{0.49},
				new double[]{2},
				new double[]{0},
				new double[]{0}
		), frame);

		assertEquals(1, frame.slotCount());
		assertEquals(2_400, frame.rampSamples());
		assertEquals(120, frame.startDelaySamples(0), EPSILON);
		assertEquals(120, frame.targetDelaySamples(0), EPSILON);
		assertEquals(0, frame.startLow(0), EPSILON);
		assertEquals(0.5, frame.targetLow(0), EPSILON);
		assertEquals(0.6, frame.targetMid(0), EPSILON);
		assertEquals(0.7, frame.targetHigh(0), EPSILON);
		assertEquals(1, frame.targetDirectionX(0), EPSILON);
	}

	@Test
	void nearbyClusterKeepsSlotAndRampsDelayGainAndDirection() {
		EarlyReflectionClusterSlew slew =
				new EarlyReflectionClusterSlew();
		EarlyReflectionClusterSlew.Frame frame =
				new EarlyReflectionClusterSlew.Frame();
		slew.update(single(100, 0.25, 1, 0, 0), frame);
		int slot = frame.slot(0);

		slew.update(single(140, 0.81, 0, 3, 0), frame);

		assertEquals(1, frame.slotCount());
		assertEquals(slot, frame.slot(0));
		assertEquals(100, frame.startDelaySamples(0), EPSILON);
		assertEquals(140, frame.targetDelaySamples(0), EPSILON);
		assertEquals(0.5, frame.startMid(0), EPSILON);
		assertEquals(0.9, frame.targetMid(0), EPSILON);
		assertEquals(1, frame.startDirectionX(0), EPSILON);
		assertEquals(1, frame.targetDirectionY(0), EPSILON);
	}

	@Test
	void topologyReplacementCrossfadesAcrossTwoSlots() {
		EarlyReflectionClusterSlew slew =
				new EarlyReflectionClusterSlew();
		EarlyReflectionClusterSlew.Frame frame =
				new EarlyReflectionClusterSlew.Frame();
		slew.update(single(100, 0.25, 1, 0, 0), frame);
		int oldSlot = frame.slot(0);

		slew.update(single(300, 0.64, 0, 0, 1), frame);

		assertEquals(2, frame.slotCount());
		int newIndex = frame.slot(0) == oldSlot ? 1 : 0;
		int oldIndex = 1 - newIndex;
		assertEquals(0, frame.startMid(newIndex), EPSILON);
		assertEquals(0.8, frame.targetMid(newIndex), EPSILON);
		assertEquals(0.5, frame.startMid(oldIndex), EPSILON);
		assertEquals(0, frame.targetMid(oldIndex), EPSILON);
	}

	@Test
	void incompleteResultFadesThenRetiresSlots() {
		EarlyReflectionClusterSlew slew =
				new EarlyReflectionClusterSlew();
		EarlyReflectionClusterSlew.Frame frame =
				new EarlyReflectionClusterSlew.Frame();
		slew.update(single(100, 0.25, 1, 0, 0), frame);

		slew.update(input(
				false,
				new double[0],
				new double[0],
				new double[0],
				new double[0],
				new double[0],
				new double[0],
				new double[0]
		), frame);
		assertEquals(1, frame.slotCount());
		assertEquals(0.5, frame.startMid(0), EPSILON);
		assertEquals(0, frame.targetMid(0), EPSILON);

		slew.update(input(
				false,
				new double[0],
				new double[0],
				new double[0],
				new double[0],
				new double[0],
				new double[0],
				new double[0]
		), frame);
		assertEquals(0, frame.slotCount());
	}

	private static Input single(
			double arrival,
			double energy,
			double x,
			double y,
			double z
	) {
		return input(
				true,
				new double[]{arrival},
				new double[]{energy},
				new double[]{energy},
				new double[]{energy},
				new double[]{x},
				new double[]{y},
				new double[]{z}
		);
	}

	private static Input input(
			boolean complete,
			double[] arrival,
			double[] low,
			double[] mid,
			double[] high,
			double[] x,
			double[] y,
			double[] z
	) {
		return new Input(
				complete, arrival, low, mid, high, x, y, z
		);
	}

	private record Input(
			boolean complete,
			double[] arrival,
			double[] low,
			double[] mid,
			double[] high,
			double[] x,
			double[] y,
			double[] z
	) implements EarlyReflectionClusterSlew.Input {
		@Override
		public int clusterCount() {
			return arrival.length;
		}

		@Override
		public double clusterArrivalSamples(int index) {
			return arrival[index];
		}

		@Override
		public double clusterLow(int index) {
			return low[index];
		}

		@Override
		public double clusterMid(int index) {
			return mid[index];
		}

		@Override
		public double clusterHigh(int index) {
			return high[index];
		}

		@Override
		public double clusterDirectionX(int index) {
			return x[index];
		}

		@Override
		public double clusterDirectionY(int index) {
			return y[index];
		}

		@Override
		public double clusterDirectionZ(int index) {
			return z[index];
		}
	}
}
