package com.tenicana.dronecraft.client.control;

final class ControllerButtonEdgeTracker {
	private boolean armButtonDown;
	private boolean disarmButtonDown;
	private boolean calibrateButtonDown;

	ButtonEdges sample(GamepadInputFrame input) {
		boolean armDown = input != null && input.armButtonPressed();
		boolean disarmDown = input != null && input.disarmButtonPressed();
		boolean calibrateDown = input != null && input.calibrateButtonPressed();
		ButtonEdges edges = new ButtonEdges(
				armDown && !armButtonDown,
				disarmDown && !disarmButtonDown,
				calibrateDown && !calibrateButtonDown
		);
		armButtonDown = armDown;
		disarmButtonDown = disarmDown;
		calibrateButtonDown = calibrateDown;
		return edges;
	}

	void reset() {
		armButtonDown = false;
		disarmButtonDown = false;
		calibrateButtonDown = false;
	}

	record ButtonEdges(boolean armPressed, boolean disarmPressed, boolean calibratePressed) {
		static ButtonEdges none() {
			return new ButtonEdges(false, false, false);
		}

		boolean anyPressed() {
			return armPressed || disarmPressed || calibratePressed;
		}
	}
}
