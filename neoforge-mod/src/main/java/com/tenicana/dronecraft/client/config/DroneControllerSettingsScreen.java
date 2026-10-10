package com.tenicana.dronecraft.client.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.tenicana.dronecraft.client.DroneClientState;
import com.tenicana.dronecraft.client.control.ControllerInputDiagnostics;
import com.tenicana.dronecraft.client.control.GamepadDeviceResolver;
import com.tenicana.dronecraft.client.control.GamepadControlPreview;
import com.tenicana.dronecraft.client.control.GlfwJoystickProvider;
import com.tenicana.dronecraft.client.control.JoystickProvider;
import com.tenicana.dronecraft.client.control.JoystickSnapshot;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.sim.DroneConfig;

public final class DroneControllerSettingsScreen extends Screen {
	private static final String BUTTON_PREFIX = "button.fpvdrone.";
	private static final float MIN_AXIS_CAPTURE_DELTA = 0.05f;
	private static final float MIN_THROTTLE_CALIBRATION_RANGE = 0.05f;
	private static final float MAX_CENTER_CAPTURE_OFFSET = 0.45f;
	private static final int REMOTE_STICK_MIN_SIZE = 64;
	private static final int REMOTE_STICK_MAX_SIZE = 148;
	private static final int CONTENT_COLUMN_GAP = 12;
	private static final int REMOTE_BUTTON_HEIGHT = 20;
	private static final int REMOTE_BUTTON_GAP = 4;
	private static final byte BUTTON_PRESSED = 1;

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
	private Button leftStickSwapButton;
	private Button rightStickSwapButton;
	private Button armButtonButton;
	private Button disarmButtonButton;
	private Button calibrateButtonButton;
	private Button throttleCalibrateButton;
	private Button stickCenterCalibrateButton;
	private Button feelPresetButton;
	private Button gamepadEnabledButton;
	private Button refreshButton;
	private Button closeButton;
	private ControlsPanel controlsPanel;
	private int mappedOutputY;

	private final JoystickProvider joystickProvider = GlfwJoystickProvider.INSTANCE;
	private GamepadDeviceResolver.Resolution controllerResolution = new GamepadDeviceResolver.Resolution(
			java.util.List.of(),
			null,
			GamepadDeviceResolver.Status.NO_DEVICES,
			false
	);
	private JoystickSnapshot selectedController;
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
		refreshSelectedController(true);

		int panelWidth = Math.max(80, Math.min(600, width - 24));
		controlsPanel = addRenderableWidget(new ControlsPanel(
				(width - panelWidth) / 2, 70, panelWidth, Math.max(24, height - 102)
		));
		int stickSize = remoteStickSize();
		int stickY = controlsPanel.getY() + 14;
		int leftX = controlsPanel.getX() + 2;
		int rightX = leftX + contentColumnWidth() + CONTENT_COLUMN_GAP;
		int rowHeight = REMOTE_BUTTON_HEIGHT + REMOTE_BUTTON_GAP;
		int buttonWidth = contentColumnWidth();
		int halfButtonWidth = (buttonWidth - REMOTE_BUTTON_GAP) / 2;
		int controlY = stickY + stickSize + 18;

		yawAxisButton = addButton(leftX, controlY, buttonWidth, Component.translatable(BUTTON_PREFIX + "yaw_axis"), button -> startAxisCapture(CaptureTarget.YAW_AXIS));
		throttleAxisButton = addButton(
				leftX,
				controlY + rowHeight,
				buttonWidth,
				Component.translatable(BUTTON_PREFIX + "throttle_axis"),
				button -> startAxisCapture(CaptureTarget.THROTTLE_AXIS)
		);
		yawInvertButton = addButton(leftX, controlY + rowHeight * 2, halfButtonWidth, labelInvert(config.yawInverted()), button -> toggleYawInvert());
		throttleInvertButton = addButton(
				leftX + halfButtonWidth + REMOTE_BUTTON_GAP,
				controlY + rowHeight * 2,
				halfButtonWidth,
				labelInvert(config.throttleInverted()),
				button -> toggleThrottleInvert()
		);
		leftStickSwapButton = addButton(
				leftX,
				controlY + rowHeight * 3,
				buttonWidth,
				Component.translatable("screen.fpvdrone.swap_left_stick"),
				button -> swapLeftStickAxes()
		);

		rollAxisButton = addButton(rightX, controlY, buttonWidth, Component.translatable(BUTTON_PREFIX + "roll_axis"), button -> startAxisCapture(CaptureTarget.ROLL_AXIS));
		pitchAxisButton = addButton(rightX, controlY + rowHeight, buttonWidth, Component.translatable(BUTTON_PREFIX + "pitch_axis"), button -> startAxisCapture(CaptureTarget.PITCH_AXIS));
		rollInvertButton = addButton(rightX, controlY + rowHeight * 2, halfButtonWidth, labelInvert(config.rollInverted()), button -> toggleRollInvert());
		pitchInvertButton = addButton(
				rightX + halfButtonWidth + REMOTE_BUTTON_GAP,
				controlY + rowHeight * 2,
				halfButtonWidth,
				labelInvert(config.pitchInverted()),
				button -> togglePitchInvert()
		);
		rightStickSwapButton = addButton(
				rightX,
				controlY + rowHeight * 3,
				buttonWidth,
				Component.translatable("screen.fpvdrone.swap_right_stick"),
				button -> swapRightStickAxes()
		);

		mappedOutputY = controlY + rowHeight * 4 + 6;
		int utilityY = mappedOutputY + 28;
		throttleCalibrateButton = addButton(
				leftX,
				utilityY,
				buttonWidth,
				Component.translatable("screen.fpvdrone.start_throttle_calibration"),
				button -> toggleScreenThrottleCalibration()
		);
		stickCenterCalibrateButton = addButton(
				rightX,
				utilityY,
				buttonWidth,
				Component.translatable("screen.fpvdrone.calibrate_stick_center"),
				button -> calibrateStickCenters()
		);
		feelPresetButton = addButton(
				leftX,
				utilityY + rowHeight,
				buttonWidth,
				Component.translatable("screen.fpvdrone.feel_entry", Component.translatable(config.gamepadFeelPreset().translationKey())),
				button -> cycleGamepadFeelPreset()
		);
		gamepadEnabledButton = addButton(
				rightX,
				utilityY + rowHeight,
				buttonWidth,
				Component.translatable(config.gamepadEnabled() ? "screen.fpvdrone.gamepad_enabled" : "screen.fpvdrone.gamepad_disabled"),
				button -> toggleGamepadEnabled()
		);

		int bindY = utilityY + rowHeight * 2 + 6;
		armButtonButton = addButton(leftX, bindY, buttonWidth, Component.translatable(BUTTON_PREFIX + "arm_button"), button -> startButtonCapture(CaptureTarget.ARM_BUTTON));
		disarmButtonButton = addButton(
				rightX,
				bindY,
				buttonWidth,
				Component.translatable(BUTTON_PREFIX + "disarm_button"),
				button -> startButtonCapture(CaptureTarget.DISARM_BUTTON)
		);
		calibrateButtonButton = addButton(
				leftX,
				bindY + rowHeight,
				buttonWidth,
				Component.translatable(BUTTON_PREFIX + "calibrate_button"),
				button -> startButtonCapture(CaptureTarget.CALIBRATE_BUTTON)
		);
		refreshButton = addButton(
				rightX,
				bindY + rowHeight,
				buttonWidth,
				Component.translatable("screen.fpvdrone.refresh"),
				button -> {
					config.clearPreferredGamepadDevice();
					refreshSelectedController(true);
					config.save();
					status = selectedController != null
							? Component.translatable("screen.fpvdrone.detected_controller", selectedController.name())
							: Component.translatable("screen.fpvdrone.no_controller");
					updateButtonLabels();
				}
		);
		controlsPanel.totalContentHeight = bindY + rowHeight + REMOTE_BUTTON_HEIGHT + 8 - controlsPanel.getY();
		int closeWidth = Math.min(120, width - 24);
		closeButton = addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
				.bounds((width - closeWidth) / 2, height - 26, closeWidth, REMOTE_BUTTON_HEIGHT).build());
		updateButtonLabels();
	}

	@Override
	public void tick() {
		super.tick();
		refreshSelectedController(true);

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
		drawFittedCenteredString(graphics, title, 10, 0xFFE8EEF9);
		Component controllerStatus = selectedController != null
				? Component.translatable("screen.fpvdrone.connected_controller", selectedController.name())
				: Component.translatable("screen.fpvdrone.no_controller");
		drawFittedCenteredString(graphics, controllerStatus, 25,
				selectedController != null && config.gamepadEnabled() ? 0xFF66C0FF : 0xFFFFAA66);
		drawFittedCenteredString(graphics,
				Component.translatable(config.gamepadEnabled() ? "screen.fpvdrone.gamepad_input_enabled" : "screen.fpvdrone.gamepad_input_disabled"),
				35, config.gamepadEnabled() ? 0xFF66FFAA : 0xFFFFAA66);

		if (captureThrottleInProgress) {
			drawFittedCenteredString(graphics, Component.translatable("screen.fpvdrone.throttle_calibration_running"), 47, 0xFFFFCC66);
			drawFittedCenteredString(graphics, Component.translatable(
					"screen.fpvdrone.throttle_calibration_range",
					String.format(Locale.ROOT, "%.3f", capturedThrottleMin),
					String.format(Locale.ROOT, "%.3f", capturedThrottleMax)), 57, 0xFFFFCC66);
		} else if (captureTarget != CaptureTarget.NONE) {
			drawFittedCenteredString(graphics, Component.translatable("screen.fpvdrone.binding_target", bindingTargetLabel()), 47, 0xFFFFCC99);
		} else if (!status.getString().isEmpty()) {
			drawFittedCenteredString(graphics, status, 47, 0xFFFFCC99);
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
			int direction = keyCode == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1;
			controlsPanel.setScrollAmount(controlsPanel.scrollAmount() + direction * controlsPanel.getHeight());
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private void drawFittedCenteredString(GuiGraphics graphics, Component message, int y, int color) {
		String fitted = font.plainSubstrByWidth(message.getString(), Math.max(1, width - 24));
		graphics.drawCenteredString(font, fitted, width / 2, y, color);
	}

	private void renderControllerContents(GuiGraphics graphics) {
		int stickSize = remoteStickSize();
		int scroll = (int) controlsPanel.scrollAmount();
		int stickY = controlsPanel.getY() + 14 - scroll;
		int leftX = controlsPanel.getX() + 2 + (contentColumnWidth() - stickSize) / 2;
		int rightX = leftX + contentColumnWidth() + CONTENT_COLUMN_GAP;
		GamepadControlPreview.Preview preview = axesSnapshot.length == 0
				? null
				: GamepadControlPreview.fromAxes(config, axesSnapshot, previewHoverThrottle());
		drawRemoteStick(graphics, leftX, stickY, stickSize,
				Component.translatable("screen.fpvdrone.left_stick"),
				Component.translatable("screen.fpvdrone.axis_yaw"),
				Component.translatable("screen.fpvdrone.axis_throttle"),
				preview == null ? 0.0f : preview.yawStick(),
				preview == null ? 0.0f : preview.calibratedThrottle() * 2.0f - 1.0f,
				preview == null || preview.yawAxisPresent(), preview == null || preview.throttleAxisPresent());
		drawRemoteStick(graphics, rightX, stickY, stickSize,
				Component.translatable("screen.fpvdrone.right_stick"),
				Component.translatable("screen.fpvdrone.axis_roll"),
				Component.translatable("screen.fpvdrone.axis_pitch"),
				preview == null ? 0.0f : preview.rollStick(), preview == null ? 0.0f : preview.pitchStick(),
				preview == null || preview.rollAxisPresent(), preview == null || preview.pitchAxisPresent());
		drawMappedOutputPreview(graphics, width / 2, mappedOutputY - scroll, preview);
	}

	@Override
	public void onClose() {
		config.save();
		super.onClose();
	}

	private int contentColumnWidth() {
		return (controlsPanel.getWidth() - CONTENT_COLUMN_GAP - 12) / 2;
	}

	private int remoteStickSize() {
		int widthDriven = contentColumnWidth() - 4;
		int heightDriven = Math.max(REMOTE_STICK_MIN_SIZE, height - 260);
		return Math.max(32, Math.min(REMOTE_STICK_MAX_SIZE, Math.min(widthDriven, heightDriven)));
	}

	private Button addButton(int x, int y, int width, Component message, Button.OnPress onPress) {
		Button button = Button.builder(message, onPress).bounds(x, y, width, REMOTE_BUTTON_HEIGHT).build();
		controlsPanel.addContentButton(button);
		return button;
	}

	private record ContentButton(Button button, int originalY) {
	}

	private final class ControlsPanel extends ScrollableControlsContainer {
		private final List<Button> buttons = new ArrayList<>();
		private final List<ContentButton> positions = new ArrayList<>();
		private int totalContentHeight;

		private ControlsPanel(int x, int y, int width, int height) {
			super(x, y, width, height, DroneControllerSettingsScreen.this.title);
		}

		private void addContentButton(Button button) {
			buttons.add(button);
			positions.add(new ContentButton(button, button.getY()));
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return buttons;
		}

		@Override
		protected int contentHeight() {
			return totalContentHeight;
		}

		@Override
		protected double scrollRate() {
			return REMOTE_BUTTON_HEIGHT + REMOTE_BUTTON_GAP;
		}

		@Override
		public void setScrollAmount(double amount) {
			super.setScrollAmount(amount);
			for (ContentButton position : positions) {
				position.button().setY(position.originalY() - (int) scrollAmount());
			}
		}

		@Override
		public void setFocused(GuiEventListener listener) {
			super.setFocused(listener);
			if (listener instanceof Button button) {
				if (button.getY() < getY()) {
					setScrollAmount(scrollAmount() + button.getY() - getY());
				} else if (button.getBottom() > getBottom()) {
					setScrollAmount(scrollAmount() + button.getBottom() - getBottom());
				}
			}
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			return isMouseOver(mouseX, mouseY) && super.mouseClicked(mouseX, mouseY, button);
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
			graphics.enableScissor(getX(), getY(), getRight() - 8, getBottom());
			renderControllerContents(graphics);
			for (Button button : buttons) {
				if (button.getBottom() > getY() && button.getY() < getBottom()) {
					button.render(graphics, mouseX, mouseY, partialTicks);
				}
			}
			graphics.disableScissor();
			renderScrollbar(graphics, mouseX, mouseY);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			if (getFocused() instanceof Button button) {
				button.updateNarration(output);
			} else {
				defaultButtonNarrationText(output);
			}
		}
	}

	private void updateButtonLabels() {
		rollAxisButton.setMessage(labelAxis(config.rollAxis(), "screen.fpvdrone.axis_roll"));
		pitchAxisButton.setMessage(labelAxis(config.pitchAxis(), "screen.fpvdrone.axis_pitch"));
		yawAxisButton.setMessage(labelAxis(config.yawAxis(), "screen.fpvdrone.axis_yaw"));
		throttleAxisButton.setMessage(labelAxis(config.throttleAxis(), "screen.fpvdrone.axis_throttle"));

		rollInvertButton.setMessage(labelInvert("screen.fpvdrone.axis_roll", config.rollInverted()));
		pitchInvertButton.setMessage(labelInvert("screen.fpvdrone.axis_pitch", config.pitchInverted()));
		yawInvertButton.setMessage(labelInvert("screen.fpvdrone.axis_yaw", config.yawInverted()));
		throttleInvertButton.setMessage(labelInvert("screen.fpvdrone.axis_throttle", config.throttleInverted()));
		leftStickSwapButton.setMessage(Component.translatable("screen.fpvdrone.swap_left_stick"));
		rightStickSwapButton.setMessage(Component.translatable("screen.fpvdrone.swap_right_stick"));

		armButtonButton.setMessage(labelButton(config.armButton(), "screen.fpvdrone.btn_arm"));
		disarmButtonButton.setMessage(labelButton(config.disarmButton(), "screen.fpvdrone.btn_disarm"));
		calibrateButtonButton.setMessage(labelButton(config.throttleCalibrateButton(), "screen.fpvdrone.btn_calibrate"));
		feelPresetButton.setMessage(Component.translatable(
				"screen.fpvdrone.feel_entry",
				Component.translatable(config.gamepadFeelPreset().translationKey())
		));
		gamepadEnabledButton.setMessage(Component.translatable(
				config.gamepadEnabled()
						? "screen.fpvdrone.gamepad_enabled"
						: "screen.fpvdrone.gamepad_disabled"
		));

		throttleCalibrateButton.setMessage(Component.translatable(
				captureThrottleInProgress
						? "screen.fpvdrone.finish_throttle_calibration"
						: "screen.fpvdrone.start_throttle_calibration"
		));
		stickCenterCalibrateButton.setMessage(Component.translatable("screen.fpvdrone.calibrate_stick_center"));
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

	private Component labelInvert(String axisKey, boolean value) {
		return Component.translatable(
				"screen.fpvdrone.axis_direction_entry",
				Component.translatable(axisKey),
				Component.translatable(value ? "screen.fpvdrone.axis_inverted" : "screen.fpvdrone.axis_normal")
		);
	}

	private void startAxisCapture(CaptureTarget target) {
		if (captureThrottleInProgress) {
			status = Component.translatable("screen.fpvdrone.status_finish_calibration_first");
			return;
		}

		refreshSelectedController(true);
		if (selectedController == null) {
			status = Component.translatable("screen.fpvdrone.no_controller");
			return;
		}
		float[] axes = selectedController.axes();
		if (axes.length == 0) {
			status = Component.translatable("screen.fpvdrone.no_axis_data");
			return;
		}

		captureAxisSnapshot = axes;
		captureTarget = target;
		status = Component.translatable("screen.fpvdrone.status_move_axis");
	}

	private void startButtonCapture(CaptureTarget target) {
		if (captureThrottleInProgress) {
			status = Component.translatable("screen.fpvdrone.status_finish_calibration_first");
			return;
		}

		refreshSelectedController(true);
		if (selectedController == null) {
			status = Component.translatable("screen.fpvdrone.no_controller");
			return;
		}
		previousButtons = snapshotButtons(selectedJoystick);
		captureTarget = target;
		status = Component.translatable("screen.fpvdrone.status_press_button");
	}

	private void pollAxisCapture() {
		refreshSelectedController(false);
		if (selectedController == null) {
			return;
		}
		float[] axes = selectedController.axes();
		if (axes.length != captureAxisSnapshot.length) {
			return;
		}

		int bestAxis = -1;
		float bestDelta = MIN_AXIS_CAPTURE_DELTA;
		for (int i = 0; i < axes.length; i++) {
			float delta = Math.abs(axes[i] - captureAxisSnapshot[i]);
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
			if (buttons[i] == BUTTON_PRESSED && (i >= previousButtons.length || previousButtons[i] != BUTTON_PRESSED)) {
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

	private void swapLeftStickAxes() {
		if (captureThrottleInProgress) {
			status = Component.translatable("screen.fpvdrone.status_finish_calibration_first");
			return;
		}
		config.swapYawThrottleAxes();
		config.save();
		updateButtonLabels();
		status = Component.translatable("screen.fpvdrone.status_left_stick_swapped");
	}

	private void swapRightStickAxes() {
		if (captureThrottleInProgress) {
			status = Component.translatable("screen.fpvdrone.status_finish_calibration_first");
			return;
		}
		config.swapRollPitchAxes();
		config.save();
		updateButtonLabels();
		status = Component.translatable("screen.fpvdrone.status_right_stick_swapped");
	}

	private void cycleGamepadFeelPreset() {
		DroneClientConfig.ControlFeelPreset preset = config.nextGamepadFeelPreset();
		config.save();
		updateButtonLabels();
		status = Component.translatable("screen.fpvdrone.status_feel_preset", Component.translatable(preset.translationKey()));
	}

	private void toggleScreenThrottleCalibration() {
		if (!captureThrottleInProgress) {
			refreshSelectedController(true);
			if (selectedController == null || config.throttleAxis() >= axesSnapshot.length) {
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

	private void toggleGamepadEnabled() {
		config.setGamepadEnabled(!config.gamepadEnabled());
		config.save();
		updateButtonLabels();
		status = Component.translatable(config.gamepadEnabled()
				? "screen.fpvdrone.status_gamepad_enabled"
				: "screen.fpvdrone.status_gamepad_disabled");
	}

	private void calibrateStickCenters() {
		if (captureThrottleInProgress) {
			status = Component.translatable("screen.fpvdrone.status_finish_calibration_first");
			return;
		}
		refreshSelectedController(true);
		if (selectedController == null || !hasStickAxesSnapshot()) {
			status = Component.translatable("screen.fpvdrone.status_no_center_target");
			return;
		}

		float rollCenter = DroneClientConfig.orientedStickAxis(axesSnapshot[config.rollAxis()], config.rollInverted());
		float pitchCenter = DroneClientConfig.orientedStickAxis(axesSnapshot[config.pitchAxis()], config.pitchInverted());
		float yawCenter = DroneClientConfig.orientedStickAxis(axesSnapshot[config.yawAxis()], config.yawInverted());
		if (Math.abs(rollCenter) > MAX_CENTER_CAPTURE_OFFSET
				|| Math.abs(pitchCenter) > MAX_CENTER_CAPTURE_OFFSET
				|| Math.abs(yawCenter) > MAX_CENTER_CAPTURE_OFFSET) {
			status = Component.translatable("screen.fpvdrone.status_center_sticks_first");
			return;
		}

		config.setStickCenters(rollCenter, pitchCenter, yawCenter);
		config.save();
		status = Component.translatable(
				"screen.fpvdrone.status_center_saved",
				String.format(Locale.ROOT, "% .3f", rollCenter),
				String.format(Locale.ROOT, "% .3f", pitchCenter),
				String.format(Locale.ROOT, "% .3f", yawCenter)
		);
	}

	private boolean hasStickAxesSnapshot() {
		return axesSnapshot.length > config.rollAxis()
				&& axesSnapshot.length > config.pitchAxis()
				&& axesSnapshot.length > config.yawAxis();
	}

	private void sampleThrottleCalibration() {
		if (selectedController == null || config.throttleAxis() >= axesSnapshot.length) {
			captureThrottleInProgress = false;
			status = Component.translatable("screen.fpvdrone.status_calib_stopped");
			updateButtonLabels();
			return;
		}
		float rawThrottle = throttleAxisRaw(axesSnapshot[config.throttleAxis()], config.throttleInverted());
		capturedThrottleMin = Math.min(capturedThrottleMin, rawThrottle);
		capturedThrottleMax = Math.max(capturedThrottleMax, rawThrottle);
	}

	private void drawRemoteStick(
			GuiGraphics graphics,
			int x,
			int y,
			int size,
			Component title,
			Component horizontalLabel,
			Component verticalLabel,
			float horizontal,
			float vertical,
			boolean horizontalAxisPresent,
			boolean verticalAxisPresent
	) {
		int centerX = x + size / 2;
		int centerY = y + size / 2;
		int radius = Math.max(28, size / 2 - 15);
		int pointerX = centerX + Math.round(clamp(horizontal, -1.0f, 1.0f) * radius);
		int pointerY = centerY - Math.round(clamp(vertical, -1.0f, 1.0f) * radius);
		int frameColor = horizontalAxisPresent && verticalAxisPresent ? 0xFF31445D : 0xFF76512D;

		graphics.fill(x - 2, y - 2, x + size + 2, y + size + 2, 0xAA05080D);
		graphics.fill(x, y, x + size, y + size, 0xE5121A26);
		graphics.fill(x, y, x + size, y + 1, frameColor);
		graphics.fill(x, y + size - 1, x + size, y + size, frameColor);
		graphics.fill(x, y, x + 1, y + size, frameColor);
		graphics.fill(x + size - 1, y, x + size, y + size, frameColor);
		graphics.fill(centerX - 1, y + 8, centerX + 1, y + size - 8, 0xFF445C7C);
		graphics.fill(x + 8, centerY - 1, x + size - 8, centerY + 1, 0xFF445C7C);
		graphics.fill(pointerX - 5, pointerY - 5, pointerX + 6, pointerY + 6, 0xFFE9F2FF);
		graphics.fill(pointerX - 3, pointerY - 3, pointerX + 4, pointerY + 4, 0xFFFFB74D);

		graphics.drawCenteredString(font, title, centerX, y - 12, 0xFFE8EEF9);
		graphics.drawCenteredString(font, horizontalLabel, centerX, y + size + 3, horizontalAxisPresent ? 0xFFAACCEE : 0xFFFFAA66);
		graphics.drawString(font, verticalLabel, x + 5, y + 5, verticalAxisPresent ? 0xFFAACCEE : 0xFFFFAA66);
	}

	private void drawMappedOutputPreview(GuiGraphics graphics, int centerX, int y, GamepadControlPreview.Preview preview) {
		if (preview == null) {
			return;
		}
		Component output = Component.translatable(
				"screen.fpvdrone.mapped_output",
				percent(preview.throttleCommand()),
				signed(preview.pitchCommand()),
				signed(preview.rollCommand()),
				signed(preview.yawCommand())
		);
		graphics.drawCenteredString(font, output, centerX, y, preview.allAxesPresent() ? 0xFFBFE8C8 : 0xFFFFAA66);
		if (!preview.allAxesPresent()) {
			graphics.drawCenteredString(font, Component.translatable("screen.fpvdrone.mapped_axes_missing"), centerX, y + 10, 0xFFFFAA66);
		}
	}

	private float previewHoverThrottle() {
		DroneEntity drone = DroneClientState.controlledDrone();
		double hoverThrottle = drone == null ? DroneConfig.racingQuad().hoverThrottle() : drone.config().hoverThrottle();
		return (float) Math.max(0.05, Math.min(0.75, hoverThrottle));
	}

	private static String percent(float value) {
		return String.format(Locale.ROOT, "%3.0f%%", value * 100.0f);
	}

	private static String signed(float value) {
		return String.format(Locale.ROOT, "%+.3f", value);
	}

	private boolean isCaptureTargetButton(CaptureTarget target) {
		return target == CaptureTarget.ARM_BUTTON || target == CaptureTarget.DISARM_BUTTON || target == CaptureTarget.CALIBRATE_BUTTON;
	}

	private Component bindingTargetLabel() {
		return switch (captureTarget) {
			case NONE -> Component.literal("...");
			case ROLL_AXIS -> Component.translatable("screen.fpvdrone.axis_roll");
			case PITCH_AXIS -> Component.translatable("screen.fpvdrone.axis_pitch");
			case YAW_AXIS -> Component.translatable("screen.fpvdrone.axis_yaw");
			case THROTTLE_AXIS -> Component.translatable("screen.fpvdrone.axis_throttle");
			case ARM_BUTTON -> Component.translatable("screen.fpvdrone.btn_arm");
			case DISARM_BUTTON -> Component.translatable("screen.fpvdrone.btn_disarm");
			case CALIBRATE_BUTTON -> Component.translatable("screen.fpvdrone.btn_calibrate");
		};
	}

	private void refreshSelectedController(boolean allowLegacyMigration) {
		controllerResolution = GamepadDeviceResolver.resolve(config, joystickProvider, allowLegacyMigration);
		if (controllerResolution.migrated()) {
			config.save();
		}
		selectedController = controllerResolution.selected();
		selectedJoystick = selectedController == null ? -1 : selectedController.glfwId();
		axesSnapshot = selectedController == null ? new float[0] : selectedController.axes();
		ControllerInputDiagnostics.updateCalibrationDevice(selectedController);
	}

	private byte[] snapshotButtons(int joystick) {
		if (joystick < 0 || selectedController == null || selectedController.glfwId() != joystick) {
			return new byte[0];
		}
		return selectedController.buttons();
	}

	private float throttleAxisRaw(float axisValue, boolean inverted) {
		if (inverted) {
			axisValue = -axisValue;
		}
		return (float) Math.max(0.0, Math.min(1.0, (axisValue + 1.0) * 0.5));
	}

	private static float clamp(float value, float min, float max) {
		if (!Float.isFinite(value)) {
			return min;
		}
		return Math.max(min, Math.min(max, value));
	}
}
