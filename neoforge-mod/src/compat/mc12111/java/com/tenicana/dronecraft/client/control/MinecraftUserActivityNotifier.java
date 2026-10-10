package com.tenicana.dronecraft.client.control;

import com.mojang.blaze3d.platform.FramerateLimitTracker;

import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;

import com.tenicana.dronecraft.client.mixin.FramerateLimitTrackerAccessor;
import com.tenicana.dronecraft.client.mixin.MinecraftFramerateLimitTrackerAccessor;

public final class MinecraftUserActivityNotifier implements UserActivityNotifier {
	public static final MinecraftUserActivityNotifier INSTANCE = new MinecraftUserActivityNotifier();

	private int lastReportTick = Integer.MIN_VALUE;

	private MinecraftUserActivityNotifier() {
	}

	@Override
	public void notifyUserActivity(int tick) {
		if (!canNotifyUserActivity()) {
			return;
		}
		FramerateLimitTracker tracker = tracker();
		if (tracker == null) {
			return;
		}
		tracker.onInputReceived();
		lastReportTick = tick;
	}

	@Override
	public boolean canNotifyUserActivity() {
		Minecraft client = Minecraft.getInstance();
		return client != null
				&& client.player != null
				&& client.level != null
				&& !client.isPaused()
				&& client.getWindow() != null
				&& !client.getWindow().isIconified()
				&& !client.getWindow().isMinimized();
	}

	@Override
	public Status status() {
		Minecraft client = Minecraft.getInstance();
		FramerateLimitTracker tracker = tracker(client);
		if (client == null || tracker == null) {
			return Status.unavailable(lastReportTick);
		}

		long latestInputTime = ((FramerateLimitTrackerAccessor) tracker).fpvdrone$getLatestInputTime();
		long elapsedMillis = latestInputTime <= 0L ? -1L : Math.max(0L, System.currentTimeMillis() - latestInputTime);
		InactivityFpsLimit setting = client.options.inactivityFpsLimit().get();
		FramerateLimitTracker.FramerateThrottleReason reason = tracker.getThrottleReason();
		return new Status(
				elapsedMillis,
				setting == null ? "unknown" : setting.name(),
				reason == null ? "unknown" : reason.name(),
				client.getFps(),
				lastReportTick
		);
	}

	private static FramerateLimitTracker tracker() {
		return tracker(Minecraft.getInstance());
	}

	private static FramerateLimitTracker tracker(Minecraft client) {
		if (client == null) {
			return null;
		}
		return ((MinecraftFramerateLimitTrackerAccessor) client).fpvdrone$getFramerateLimitTracker();
	}
}
