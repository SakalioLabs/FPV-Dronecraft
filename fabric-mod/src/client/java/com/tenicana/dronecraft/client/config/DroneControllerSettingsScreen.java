package com.tenicana.dronecraft.client.config;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Locale;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class DroneControllerSettingsScreen extends Screen {
	private static final int MAX_AXES_TO_DISPLAY = 8;
	private static final String BUTTON_PREFIX = "button.fpvdrone.";
	private static final float MIN_AXIS_CAPTURE_DELTA = 0.05f;
	private static final float MIN_THROTTLE_CALIBRATION_RANGE = 0.05f;
	private static final int STICK_RENDER_SIZE = 34;
	private static final int STICK_RENDER_RADIUS = 12;

	private enum CaptureTarget {
		NONE,
		ROLL_AXIS,
		PITCH_AXIS,
		YAW_AXIS,
		THROTTLE_AXIS,
		ARM_BUTTON,
		DISARM_BUTTON,
		CALIBRATE_BUTTON
	}

	private final DroneClientConfig config;
	private Button rollAxisButton;
	private Button pitchAxisButton;
	private Button yawAxisButton;
	private Button throttleAxisButton;
	private Button rollInvertButton;
	private Button pitchInvertButton;
	private Button yawInvertButton;
	private Button throttleInvertButton;
	private Button armButtonButton;
	private Button disarmButtonButton;
	private Button calibrateButtonButton;
	private Button throttleCalibrateButton;
	private Button feelPresetButton;
	private Button refreshButton;
	private Button closeButton;

	private int selectedJoystick;
	private float[] axesSnapshot = new float[0];
	private byte[] previousButtons = new byte[0];
	private float[] captureAxisSnapshot = new float[0];
	private CaptureTarget captureTarget = CaptureTarget.NONE;
	private float capturedThrottleMin;
	private float capturedThrottleMax;
	private boolean captureThrottleInProgress;
	private Component status = Component.empty();

	public DroneControllerSettingsScreen(DroneClientConfig config) {
		super(Component.translatable("screen.fpvdrone.controller_settings"));
		this.config = config;
	}

	public static DroneControllerSettingsScreen open(DroneClientConfig config) {
		return new DroneControllerSettingsScreen(config);
	}

	@Override
	protected void init() {
		super.init();
		selectedJoystick = resolveConnectedJoystick();
		refreshSnapshot();

		int xAxis = 12;
		int xInvert = 192;
		int y = 36;
		int rowHeight = 22;

		rollAxisButton = addButton(xAxis, y, 170, Component.translatable(BUTTON_PREFIX + "roll_axis"), button -> startAxisCapture(CaptureTarget.ROLL_AXIS));
		rollInvertButton = addButton(xInvert, y, 96, labelInvert(config.rollInverted()), button -> toggleRollInvert());
		y += rowHeight;

		pitchAxisButton = addButton(xAxis, y, 170, Component.translatable(BUTTON_PREFIX + "pitch_axis"), button -> startAxisCapture(CaptureTarget.PITCH_AXIS));
		pitchInvertButton = addButton(xInvert, y, 96, labelInvert(config.pitchInverted()), button -> togglePitchInvert());
		y += rowHeight;

		yawAxisButton = addButton(xAxis, y, 170, Component.translatable(BUTTON_PREFIX + "yaw_axis"), button -> startAxisCapture(CaptureTarget.YAW_AXIS));
		yawInvertButton = addButton(xInvert, y, 96, labelInvert(config.yawInverted()), button -> toggleYawInvert());
		y += rowHeight;

		throttleAxisButton = addButton(
				xAxis,
				y,
				170,
				Component.translatable(BUTTON_PREFIX + "throttle_axis"),
				button -> startAxisCapture(CaptureTarget.THROTTLE_AXIS)
		);
		throttleInvertButton = addButton(xInvert, y, 96, labelInvert(config.throttleInverted()), button -> toggleThrottleInvert());
		y += rowHeight + 8;

		armButtonButton = addButton(xAxis, y, 170, Component.translatable(BUTTON_PREFIX + "arm_button"), button -> startButtonCapture(CaptureTarget.ARM_BUTTON));
		y += rowHeight;

		disarmButtonButton = addButton(xAxis, y, 170, Component.translatable(BUTTON_PREFIX + "disarm_button"), button -> startButtonCapture(CaptureTarget.DISARM_BUTTON));
		y += rowHeight;

		calibrateButtonButton = addButton(
				xAxis,
				y,
				170,
				Component.translatable(BUTTON_PREFIX + "calibrate_button"),
				button -> startButtonCapture(CaptureTarget.CALIBRATE_BUTTON)
		);
		y += rowHeight + 8;

		throttleCalibrateButton = addButton(
				xAxis,
				y,
				170,
				Component.translatable("screen.fpvdrone.start_throttle_calibration"),
				button -> toggleScreenThrottleCalibration()
		);
		y += rowHeight;

		feelPresetButton = addButton(
				xAxis,
				y,
				170,
				Component.translatable("screen.fpvdrone.feel_entry", Component.translatable(config.gamepadFeelPreset().translationKey())),
				button -> cycleGamepadFeelPreset()
		);

		refreshButton = addButton(
				xAxis + 182,
				y,
				80,
				Component.translatable("screen.fpvdrone.refresh"),
				button -> {
					selectedJoystick = resolveConnectedJoystick();
					refreshSnapshot();
					status = selectedJoystick >= 0
							? Component.translatable("screen.fpvdrone.detected_controller", selectedJoystick)
							: Component.translatable("screen.fpvdrone.no_controller");
					updateButtonLabels();
				}
		);

		closeButton = addButton(width - 92, y, 80, Component.translatable("gui.done"), button -> onClose());
		updateButtonLabels();
	}

	@Override
	public void tick() {
		super.tick();
		selectedJoystick = resolveConnectedJoystick();
		refreshSnapshot();

		if (captureTarget == CaptureTarget.NONE) {
			if (captureThrottleInProgress) {
				sampleThrottleCalibration();
			}
			return;
		}

		if (isCaptureTargetButton(captureTarget)) {
			pollButtonCapture();
		} else {
			pollAxisCapture();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		super.render(graphics, mouseX, mouseY, partialTicks);

		int y = 12;
		graphics.drawCenteredString(font, title, width / 2, y, 0xE0E0E0);
		y += 16;
		graphics.drawString(font, Component.translatable("screen.fpvdrone.instructions"), 12, y, 0xAAAAAA);
		y += 20;

		if (selectedJoystick >= 0) {
			graphics.drawString(font, Component.translatable("screen.fpvdrone.connected_controller", selectedJoystick), 12, y, 0x66C0FF);
		} else {
			graphics.drawString(font, Component.translatable("screen.fpvdrone.no_controller"), 12, y, 0xFF6666);
		}
		y += 14;

		drawAxisPreview(graphics, 12, y);
		if (captureThrottleInProgress) {
			graphics.drawString(font, Component.translatable("screen.fpvdrone.throttle_calibration_running"), 12, y + 66, 0xFFCC66);
			graphics.drawString(
					font,
					Component.translatable(
							"screen.fpvdrone.throttle_calibration_range",
							String.format(Locale.ROOT, "%.3f", capturedThrottleMin),
							String.format(Locale.ROOT, "%.3f", capturedThrottleMax)
					),
					12,
					y + 76,
					0xFFCC66
			);
		}

		if (!status.getString().isEmpty()) {
			graphics.drawString(font, status, 12, y + 88, 0xFFCC99);
		}

		graphics.drawString(font, Component.translatable("screen.fpvdrone.stick_pair_hint"), 12, y + 102, 0x999999);
	}

	@Override
	public void onClose() {
		config.save();
		super.onClose();
	}

	private Button addButton(int x, int y, int width, Component message, Button.OnPress onPress) {
		Button button = Button.builder(message, onPress).bounds(x, y, width, 20).build();
		addRenderableWidget(button);
		return button;
	}

	private void updateButtonLabels() {
		rollAxisButton.setMessage(labelAxis(config.rollAxis(), "screen.fpvdrone.axis_roll"));
		pitchAxisButton.setMessage(labelAxis(config.pitchAxis(), "screen.fpvdrone.axis_pitch"));
		yawAxisButton.setMessage(labelAxis(config.yawAxis(), "screen.fpvdrone.axis_yaw"));
		throttleAxisButton.setMessage(labelAxis(config.throttleAxis(), "screen.fpvdrone.axis_throttle"));

		rollInvertButton.setMessage(labelInvert(config.rollInverted()));
		pitchInvertButton.setMessage(labelInvert(config.pitchInverted()));
		yawInvertButton.setMessage(labelInvert(config.yawInverted()));
		throttleInvertButton.setMessage(labelInvert(config.throttleInverted()));

		armButtonButton.setMessage(labelButton(config.armButton(), "screen.fpvdrone.btn_arm"));
		disarmButtonButton.setMessage(labelButton(config.disarmButton(), "screen.fpvdrone.btn_disarm"));
		calibrateButtonButton.setMessage(labelButton(config.throttleCalibrateButton(), "screen.fpvdrone.btn_calibrate"));
		feelPresetButton.setMessage(Component.translatable(
				"screen.fpvdrone.feel_entry",
				Component.translatable(config.gamepadFeelPreset().translationKey())
		));

		throttleCalibrateButton.setMessage(Component.translatable(
				captureThrottleInProgress
						? "screen.fpvdrone.finish_throttle_calibration"
						: "screen.fpvdrone.start_throttle_calibration"
		));
		refreshButton.setMessage(Component.translatable("screen.fpvdrone.refresh"));
		closeButton.setMessage(Component.translatable("gui.done"));
	}

	private Component labelAxis(int axis, String labelKey) {
		return Component.translatable("screen.fpvdrone.axis_entry", Component.translatable(labelKey), axis);
	}

	private Component labelButton(int button, String labelKey) {
		return Component.translatable(
				"screen.fpvdrone.button_entry",
				Component.translatable(labelKey),
				button < 0 ? Component.translatable("screen.fpvdrone.unset") : Component.literal(String.valueOf(button))
		);
	}

	private Component labelInvert(boolean value) {
		return Component.translatable(value ? "screen.fpvdrone.axis_inverted" : "screen.fpvdrone.axis_normal");
	}

	private void startAxisCapture(CaptureTarget target) {
		if (captureThrottleInProgress) {
			status = Component.translatable("screen.fpvdrone.status_finish_calibration_first");
			return;
		}

		selectedJoystick = resolveConnectedJoystick();
		if (selectedJoystick < 0) {
			status = Component.translatable("screen.fpvdrone.no_controller");
			return;
		}
		FloatBuffer axes = GLFW.glfwGetJoystickAxes(selectedJoystick);
		if (axes == null || axes.limit() == 0) {
			status = Component.translatable("screen.fpvdrone.no_axis_data");
			return;
		}

		captureAxisSnapshot = new float[axes.limit()];
		for (int i = 0; i < axes.limit(); i++) {
			captureAxisSnapshot[i] = axes.get(i);
		}
		captureTarget = target;
		status = Component.translatable("screen.fpvdrone.status_move_axis");
	}

	private void startButtonCapture(CaptureTarget target) {
		if (captureThrottleInProgress) {
			status = Component.translatable("screen.fpvdrone.status_finish_calibration_first");
			return;
		}

		selectedJoystick = resolveConnectedJoystick();
		if (selectedJoystick < 0) {
			status = Component.translatable("screen.fpvdrone.no_controller");
			return;
		}
		previousButtons = snapshotButtons(selectedJoystick);
		captureTarget = target;
		status = Component.translatable("screen.fpvdrone.status_press_button");
	}

	private void pollAxisCapture() {
		FloatBuffer axes = GLFW.glfwGetJoystickAxes(selectedJoystick);
		if (axes == null || axes.limit() != captureAxisSnapshot.length) {
			return;
		}

		int bestAxis = -1;
		float bestDelta = MIN_AXIS_CAPTURE_DELTA;
		for (int i = 0; i < axes.limit(); i++) {
			float delta = Math.abs(axes.get(i) - captureAxisSnapshot[i]);
			if (delta > bestDelta) {
				bestDelta = delta;
				bestAxis = i;
			}
		}
		if (bestAxis < 0) {
			return;
		}

		switch (captureTarget) {
			case ROLL_AXIS -> config.setRollAxis(bestAxis);
			case PITCH_AXIS -> config.setPitchAxis(bestAxis);
			case YAW_AXIS -> config.setYawAxis(bestAxis);
			case THROTTLE_AXIS -> config.setThrottleAxis(bestAxis);
			default -> {
			}
		}
		captureTarget = CaptureTarget.NONE;
		captureAxisSnapshot = new float[0];
		config.save();
		updateButtonLabels();
		status = Component.translatable("screen.fpvdrone.status_axis_bound", bestAxis);
	}

	private void pollButtonCapture() {
		byte[] buttons = snapshotButtons(selectedJoystick);
		if (buttons.length == 0) {
			return;
		}
		for (int i = 0; i < buttons.length; i++) {
			if (buttons[i] == GLFW.GLFW_PRESS && (i >= previousButtons.length || previousButtons[i] != GLFW.GLFW_PRESS)) {
				assignButtonBinding(captureTarget, i);
				break;
			}
		}
		previousButtons = buttons;
	}

	private void assignButtonBinding(CaptureTarget target, int button) {
		switch (target) {
			case ARM_BUTTON -> config.setArmButton(button);
			case DISARM_BUTTON -> config.setDisarmButton(button);
			case CALIBRATE_BUTTON -> config.setThrottleCalibrateButton(button);
			default -> {
			}
		}

		captureTarget = CaptureTarget.NONE;
		config.save();
		updateButtonLabels();
		status = Component.translatable("screen.fpvdrone.status_button_bound", button);
	}

	private void toggleRollInvert() {
		config.setRollInverted(!config.rollInverted());
		config.save();
		updateButtonLabels();
	}

	private void togglePitchInvert() {
		config.setPitchInverted(!config.pitchInverted());
		config.save();
		updateButtonLabels();
	}

	private void toggleYawInvert() {
		config.setYawInverted(!config.yawInverted());
		config.save();
		updateButtonLabels();
	}

	private void toggleThrottleInvert() {
		config.setThrottleInverted(!config.throttleInverted());
		config.save();
		updateButtonLabels();
	}

	private void cycleGamepadFeelPreset() {
		DroneClientConfig.ControlFeelPreset preset = config.nextGamepadFeelPreset();
		config.save();
		updateButtonLabels();
		status = Component.translatable("screen.fpvdrone.status_feel_preset", Component.translatable(preset.translationKey()));
	}

	private void toggleScreenThrottleCalibration() {
		if (!captureThrottleInProgress) {
			selectedJoystick = resolveConnectedJoystick();
			refreshSnapshot();
			if (selectedJoystick < 0 || config.throttleAxis() >= axesSnapshot.length) {
				status = Component.translatable("screen.fpvdrone.status_no_calib_target");
				return;
			}

			captureThrottleInProgress = true;
			capturedThrottleMin = throttleAxisRaw(axesSnapshot[config.throttleAxis()], config.throttleInverted());
			capturedThrottleMax = capturedThrottleMin;
			status = Component.translatable("screen.fpvdrone.status_calib_start");
		} else {
			captureThrottleInProgress = false;
			if (capturedThrottleMax - capturedThrottleMin >= MIN_THROTTLE_CALIBRATION_RANGE) {
				config.setThrottleCalibration(capturedThrottleMin, capturedThrottleMax);
				config.save();
				status = Component.translatable(
						"screen.fpvdrone.status_calib_saved",
						String.format(Locale.ROOT, "%.3f", capturedThrottleMin),
						String.format(Locale.ROOT, "%.3f", capturedThrottleMax)
				);
			} else {
				status = Component.translatable("screen.fpvdrone.status_calib_small_range");
			}
		}
		updateButtonLabels();
	}

	private void sampleThrottleCalibration() {
		if (selectedJoystick < 0 || config.throttleAxis() >= axesSnapshot.length) {
			captureThrottleInProgress = false;
			status = Component.translatable("screen.fpvdrone.status_calib_stopped");
			updateButtonLabels();
			return;
		}
		float rawThrottle = throttleAxisRaw(axesSnapshot[config.throttleAxis()], config.throttleInverted());
		capturedThrottleMin = Math.min(capturedThrottleMin, rawThrottle);
		capturedThrottleMax = Math.max(capturedThrottleMax, rawThrottle);
	}

	private void drawAxisPreview(GuiGraphics graphics, int x, int y) {
		int lines = Math.min(axesSnapshot.length, MAX_AXES_TO_DISPLAY);
		for (int i = 0; i < lines; i++) {
			graphics.drawString(
					font,
					Component.translatable("screen.fpvdrone.axis_value", i, String.format(Locale.ROOT, "% .3f", axesSnapshot[i])),
					x,
					y + (i * 10),
					0xCCD7E0
			);
		}

		int pairCount = lines / 2;
		int pairX = x + 210;
		int baseY = y + pairCount * 10 + 4;
		graphics.drawString(font, Component.translatable("screen.fpvdrone.stick_positions"), 12, baseY - 12, 0x99CCFF);
		for (int pair = 0; pair < pairCount; pair++) {
			int axisX = pair * 2;
			int axisY = axisX + 1;
			float valueX = axesSnapshot[axisX];
			float valueY = axesSnapshot[axisY];

			graphics.drawString(
					font,
					Component.translatable(
							"screen.fpvdrone.stick_entry",
							pair + 1,
							axisX,
							axisY,
							String.format(Locale.ROOT, "% .3f", valueX),
							String.format(Locale.ROOT, "% .3f", valueY)
					),
					pairX,
					baseY + (pair * 34),
					0xAACCEE
			);

			drawStickPreview(graphics, pairX + 140, baseY + (pair * 34) - 2, pair, valueX, valueY);
		}

		if (captureTarget != CaptureTarget.NONE) {
			graphics.drawString(font, Component.translatable("screen.fpvdrone.binding_target", bindingTargetLabel()), x, y + 84, 0xFFCC99);
		}
	}

	private boolean isCaptureTargetButton(CaptureTarget target) {
		return target == CaptureTarget.ARM_BUTTON || target == CaptureTarget.DISARM_BUTTON || target == CaptureTarget.CALIBRATE_BUTTON;
	}

	private Component bindingTargetLabel() {
		return Component.translatable(
				"screen.fpvdrone.binding_target",
				switch (captureTarget) {
					case NONE -> Component.literal("...");
					case ROLL_AXIS -> Component.translatable("screen.fpvdrone.axis_roll");
					case PITCH_AXIS -> Component.translatable("screen.fpvdrone.axis_pitch");
					case YAW_AXIS -> Component.translatable("screen.fpvdrone.axis_yaw");
					case THROTTLE_AXIS -> Component.translatable("screen.fpvdrone.axis_throttle");
					case ARM_BUTTON -> Component.translatable("screen.fpvdrone.btn_arm");
					case DISARM_BUTTON -> Component.translatable("screen.fpvdrone.btn_disarm");
					case CALIBRATE_BUTTON -> Component.translatable("screen.fpvdrone.btn_calibrate");
				}
		);
	}

	private int resolveConnectedJoystick() {
		for (int joystick = GLFW.GLFW_JOYSTICK_1; joystick <= GLFW.GLFW_JOYSTICK_LAST; joystick++) {
			if (GLFW.glfwJoystickPresent(joystick)) {
				return joystick;
			}
		}
		return -1;
	}

	private void refreshSnapshot() {
		FloatBuffer axes = selectedJoystick >= 0 ? GLFW.glfwGetJoystickAxes(selectedJoystick) : null;
		float[] nextAxes = axes == null ? new float[0] : new float[axes.limit()];
		if (axes != null) {
			for (int i = 0; i < axes.limit(); i++) {
				nextAxes[i] = axes.get(i);
			}
		}
		axesSnapshot = nextAxes;
	}

	private byte[] snapshotButtons(int joystick) {
		if (joystick < 0) {
			return new byte[0];
		}
		ByteBuffer buttons = GLFW.glfwGetJoystickButtons(joystick);
		if (buttons == null) {
			return new byte[0];
		}
		byte[] snapshot = new byte[buttons.limit()];
		for (int i = 0; i < buttons.limit(); i++) {
			snapshot[i] = buttons.get(i);
		}
		return snapshot;
	}

	private float throttleAxisRaw(float axisValue, boolean inverted) {
		if (inverted) {
			axisValue = -axisValue;
		}
		return (float) Math.max(0.0, Math.min(1.0, (axisValue + 1.0) * 0.5));
	}

	private void drawStickPreview(GuiGraphics graphics, int x, int y, int stickIndex, float axisXValue, float axisYValue) {
		int boxX = x;
		int boxY = y;
		int centerX = boxX + STICK_RENDER_SIZE / 2;
		int centerY = boxY + STICK_RENDER_SIZE / 2;
		int innerRadius = STICK_RENDER_RADIUS;
		int pointerX = Math.round(axisXValue * innerRadius);
		int pointerY = Math.round(-axisYValue * innerRadius);

		graphics.fill(boxX, boxY, boxX + STICK_RENDER_SIZE, boxY + STICK_RENDER_SIZE, 0xFF2A2F40);
		graphics.fill(
				centerX - 1,
				boxY,
				centerX + 1,
				boxY + STICK_RENDER_SIZE,
				0xFF6A8CC9
		);
		graphics.fill(
				boxX,
				centerY - 1,
				boxX + STICK_RENDER_SIZE,
				centerY + 1,
				0xFF6A8CC9
		);
		graphics.fill(
				centerX + pointerX,
				centerY + pointerY,
				centerX + pointerX + 2,
				centerY + pointerY + 2,
				0xFFFFB74D
		);
		graphics.drawCenteredString(font, Component.translatable("screen.fpvdrone.stick_label", stickIndex + 1), x + STICK_RENDER_SIZE / 2 + 50, y + 2, 0xE8EEF9);
	}
}
