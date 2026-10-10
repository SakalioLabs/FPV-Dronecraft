package com.tenicana.dronecraft.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DroneRenderAttitudeHistoryTest {
	private static final float EPSILON = 1.0e-5f;

	@Test
	void telemetryReceivedBetweenFramesDoesNotRetargetTheRenderedInterval() {
		DroneRenderAttitudeHistory history = new DroneRenderAttitudeHistory();
		history.reset(0.0f, 0.0f, 0.0f);
		history.receive(0.2f, 0.4f, 0.6f);
		history.tick();
		float beforePacket = history.yawRadians(0.6f);

		history.receive(0.4f, 0.8f, 1.2f);

		assertEquals(beforePacket, history.yawRadians(0.6f), EPSILON);
		assertEquals(0.4f, history.yawRadians(1.0f), EPSILON);
		history.tick();
		assertEquals(0.4f, history.yawRadians(0.0f), EPSILON);
		assertEquals(0.6f, history.yawRadians(0.5f), EPSILON);
		assertEquals(0.8f, history.yawRadians(1.0f), EPSILON);
	}

	@Test
	void multiplePacketsBeforeTheTickUseTheLatestCompleteAttitude() {
		DroneRenderAttitudeHistory history = new DroneRenderAttitudeHistory();
		history.reset(0.0f, 0.0f, 0.0f);
		history.receive(0.2f, 0.4f, 0.6f);
		history.receive(0.4f, 0.8f, 1.2f);
		assertEquals(0.0f, history.yawRadians(0.75f), EPSILON);

		history.tick();

		assertEquals(0.2f, history.pitchRadians(0.5f), EPSILON);
		assertEquals(0.4f, history.yawRadians(0.5f), EPSILON);
		assertEquals(0.6f, history.rollRadians(0.5f), EPSILON);
	}

	@Test
	void anglesCrossThePiBoundaryAlongTheShortArc() {
		DroneRenderAttitudeHistory history = new DroneRenderAttitudeHistory();
		float nearPi = (float) Math.toRadians(179.0);
		history.reset(nearPi, -nearPi, nearPi);
		history.receive(-nearPi, nearPi, -nearPi);
		history.tick();

		assertEquals((float) Math.PI, history.pitchRadians(0.5f), EPSILON);
		assertEquals((float) -Math.PI, history.yawRadians(0.5f), EPSILON);
		assertEquals((float) Math.PI, history.rollRadians(0.5f), EPSILON);
	}

	@Test
	void aTickWithoutTelemetryReachesAndHoldsThePreviousEndpoint() {
		DroneRenderAttitudeHistory history = new DroneRenderAttitudeHistory();
		history.reset(0.0f, 0.0f, 0.0f);
		history.receive(0.2f, 0.4f, 0.6f);
		history.tick();
		history.tick();

		assertEquals(0.4f, history.yawRadians(0.0f), EPSILON);
		assertEquals(0.4f, history.yawRadians(0.5f), EPSILON);
		assertEquals(0.4f, history.yawRadians(1.0f), EPSILON);
	}

	@Test
	void teleportResetDropsThePendingTargetAndOldRenderInterval() {
		DroneRenderAttitudeHistory history = new DroneRenderAttitudeHistory();
		history.reset(0.0f, 0.0f, 0.0f);
		history.receive(0.2f, 0.4f, 0.6f);
		history.tick();
		history.receive(0.4f, 0.8f, 1.2f);

		history.reset(-0.1f, -0.2f, -0.3f);
		history.tick();

		assertEquals(-0.1f, history.pitchRadians(0.5f), EPSILON);
		assertEquals(-0.2f, history.yawRadians(0.0f), EPSILON);
		assertEquals(-0.2f, history.yawRadians(1.0f), EPSILON);
		assertEquals(-0.3f, history.rollRadians(0.5f), EPSILON);
	}

	@Test
	void continuousPitchAndRollLoopsKeepTheUnchangedYawAtVerticalAndTurnBoundaries() {
		for (float boundary : new float[] {-720.0f, -360.0f, -180.0f, -90.0f, 90.0f, 180.0f, 360.0f, 720.0f}) {
			DroneRenderAttitudeHistory history = new DroneRenderAttitudeHistory();
			float yaw = (float) Math.toRadians(37.0);
			float before = (float) Math.toRadians(boundary - 2.0);
			float after = (float) Math.toRadians(boundary + 2.0);
			history.reset(before, yaw, before);
			history.receive(after, yaw, after);
			history.tick();

			assertEquals(yaw, history.yawRadians(0.5f), EPSILON);
			assertEquals((float) Math.toRadians(boundary), history.pitchRadians(0.5f), EPSILON);
			assertEquals((float) Math.toRadians(boundary), history.rollRadians(0.5f), EPSILON);
			// A packet received during rendering must remain pending until the next tick.
			history.receive(after + 0.1f, yaw, after + 0.1f);
			assertEquals((float) Math.toRadians(boundary), history.pitchRadians(0.5f), EPSILON);
		}
	}
}
