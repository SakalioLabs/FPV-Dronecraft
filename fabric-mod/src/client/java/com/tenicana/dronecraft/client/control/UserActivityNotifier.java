package com.tenicana.dronecraft.client.control;

public interface UserActivityNotifier {
	void notifyUserActivity(int tick);

	boolean canNotifyUserActivity();

	Status status();

	record Status(
			long inactivityElapsedMillis,
			String reduceFpsWhen,
			String fpsCapReason,
			int currentFps,
			int lastReportTick
	) {
		public static Status unavailable(int lastReportTick) {
			return new Status(-1L, "unknown", "unknown", -1, lastReportTick);
		}
	}
}
