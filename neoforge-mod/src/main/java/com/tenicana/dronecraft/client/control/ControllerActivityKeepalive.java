package com.tenicana.dronecraft.client.control;

import com.tenicana.dronecraft.client.DroneClientState.InputSource;

final class ControllerActivityKeepalive {
	static final int REPORT_INTERVAL_TICKS = 15;
	static final float AXIS_CHANGE_THRESHOLD = 0.030f;
	static final float CONTROL_ACTIVITY_THRESHOLD = 0.025f;

	private boolean hasPreviousFrame;
	private float previousRawThrottle;
	private float previousRawPitch;
	private float previousRawRoll;
	private float previousRawYaw;

	Result sample(
			int tick,
			GamepadInputPath.Decision decision,
			ClientControlInput outputInput,
			boolean hasLinkedDrone,
			boolean controlAuthorized,
			boolean armed,
			ControllerButtonEdgeTracker.ButtonEdges buttonEdges,
			UserActivityNotifier notifier
	) {
		UserActivityNotifier safeNotifier = notifier == null ? NoopUserActivityNotifier.INSTANCE : notifier;
		boolean blockedByWindowState = !safeNotifier.canNotifyUserActivity();
		boolean eligible = isEligible(decision, outputInput, hasLinkedDrone, controlAuthorized, blockedByWindowState);
		boolean axisChange = eligible && hasMeaningfulAxisChange(decision.frame());
		boolean buttonEdge = eligible && buttonEdges != null && buttonEdges.anyPressed();
		boolean sustainedControl = eligible && armed && hasNonStationaryControl(outputInput);
		boolean meaningfulActivity = axisChange || buttonEdge || sustainedControl;
		int lastReportTick = safeNotifier.status().lastReportTick();
		boolean rateLimited = meaningfulActivity
				&& lastReportTick != Integer.MIN_VALUE
				&& tick - lastReportTick < REPORT_INTERVAL_TICKS;
		boolean reported = false;
		if (meaningfulActivity && !rateLimited) {
			safeNotifier.notifyUserActivity(tick);
			reported = true;
		}
		updatePrevious(decision);
		return new Result(
				meaningfulActivity,
				reported,
				axisChange,
				buttonEdge,
				sustainedControl,
				blockedByWindowState,
				rateLimited,
				safeNotifier.status()
		);
	}

	void reset() {
		hasPreviousFrame = false;
		previousRawThrottle = 0.0f;
		previousRawPitch = 0.0f;
		previousRawRoll = 0.0f;
		previousRawYaw = 0.0f;
	}

	private static boolean isEligible(
			GamepadInputPath.Decision decision,
			ClientControlInput outputInput,
			boolean hasLinkedDrone,
			boolean controlAuthorized,
			boolean blockedByWindowState
	) {
		return decision != null
				&& decision.hasUsableGamepadInput()
				&& outputInput != null
				&& outputInput.source() == InputSource.GAMEPAD
				&& hasLinkedDrone
				&& controlAuthorized
				&& !blockedByWindowState;
	}

	private boolean hasMeaningfulAxisChange(GamepadInputFrame frame) {
		if (frame == null || !frame.usable()) {
			return false;
		}
		if (!hasPreviousFrame) {
			return false;
		}
		return Math.abs(frame.rawThrottle() - previousRawThrottle) > AXIS_CHANGE_THRESHOLD
				|| Math.abs(frame.rawPitch() - previousRawPitch) > AXIS_CHANGE_THRESHOLD
				|| Math.abs(frame.rawRoll() - previousRawRoll) > AXIS_CHANGE_THRESHOLD
				|| Math.abs(frame.rawYaw() - previousRawYaw) > AXIS_CHANGE_THRESHOLD;
	}

	private static boolean hasNonStationaryControl(ClientControlInput input) {
		return input != null
				&& (Math.abs(input.throttle()) > CONTROL_ACTIVITY_THRESHOLD
						|| Math.abs(input.pitch()) > CONTROL_ACTIVITY_THRESHOLD
						|| Math.abs(input.roll()) > CONTROL_ACTIVITY_THRESHOLD
						|| Math.abs(input.yaw()) > CONTROL_ACTIVITY_THRESHOLD);
	}

	private void updatePrevious(GamepadInputPath.Decision decision) {
		GamepadInputFrame frame = decision == null ? null : decision.frame();
		if (frame == null || !frame.usable()) {
			reset();
			return;
		}
		hasPreviousFrame = true;
		previousRawThrottle = frame.rawThrottle();
		previousRawPitch = frame.rawPitch();
		previousRawRoll = frame.rawRoll();
		previousRawYaw = frame.rawYaw();
	}

	record Result(
			boolean meaningfulControllerActivity,
			boolean userActivityReported,
			boolean axisChange,
			boolean buttonEdge,
			boolean sustainedControl,
			boolean blockedByWindowState,
			boolean rateLimited,
			UserActivityNotifier.Status status
	) {
		static Result idle(UserActivityNotifier notifier) {
			UserActivityNotifier safeNotifier = notifier == null ? NoopUserActivityNotifier.INSTANCE : notifier;
			return new Result(false, false, false, false, false, !safeNotifier.canNotifyUserActivity(), false, safeNotifier.status());
		}
	}

	private enum NoopUserActivityNotifier implements UserActivityNotifier {
		INSTANCE;

		@Override
		public void notifyUserActivity(int tick) {
		}

		@Override
		public boolean canNotifyUserActivity() {
			return false;
		}

		@Override
		public Status status() {
			return Status.unavailable(Integer.MIN_VALUE);
		}
	}
}
