package com.tenicana.dronecraft.camera;

/** Local camera selection; both mounted views share the server's drone camera target. */
public enum DroneViewMode {
	LINE_OF_SIGHT("message.fpvdrone.fpv_view_disabled", "hud.fpvdrone.view_los"),
	FPV("message.fpvdrone.fpv_view_enabled", "hud.fpvdrone.view_fpv"),
	THIRD_PERSON("message.fpvdrone.third_person_view_enabled", "hud.fpvdrone.view_third_person");

	private final String messageKey;
	private final String hudKey;

	DroneViewMode(String messageKey, String hudKey) {
		this.messageKey = messageKey;
		this.hudKey = hudKey;
	}

	public DroneViewMode next() {
		return switch (this) {
			case LINE_OF_SIGHT -> FPV;
			case FPV -> THIRD_PERSON;
			case THIRD_PERSON -> LINE_OF_SIGHT;
		};
	}

	public boolean isDroneCamera() {
		return this != LINE_OF_SIGHT;
	}

	public String messageKey() {
		return messageKey;
	}

	public String hudKey() {
		return hudKey;
	}
}
