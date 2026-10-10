package com.tenicana.dronecraft.client.sound;

/** Pure channel-budget policy for reserving streaming voices for drone audio. */
public final class StreamingVoiceBudget {
	public static final int MAX_DRONES = 6;
	public static final int LAYERS_PER_DRONE = 2;
	public static final int WET_BUSES = 1;
	public static final int RESERVED_DRONE_STREAMS = MAX_DRONES * LAYERS_PER_DRONE + WET_BUSES;
	private static final int MIN_STATIC_CHANNELS = 8;
	private static final int MIN_VANILLA_STREAMING_LIMIT = 2;
	private static final int MAX_VANILLA_STREAMING_LIMIT = 8;

	private StreamingVoiceBudget() { }

	/**
	 * Returns the stream limit without changing Minecraft's ordinary allowance when
	 * the device is too small to leave the static-channel reserve.
	 */
	public static int streamingLimit(int totalChannels, int vanillaStreamingLimit) {
		if (totalChannels < 0) {
			throw new IllegalArgumentException("total channels must be nonnegative");
		}
		if (vanillaStreamingLimit < MIN_VANILLA_STREAMING_LIMIT
				|| vanillaStreamingLimit > MAX_VANILLA_STREAMING_LIMIT) {
			throw new IllegalArgumentException("vanilla streaming limit must be in [2,8]");
		}
		if (totalChannels < MIN_STATIC_CHANNELS + MIN_VANILLA_STREAMING_LIMIT) {
			return Math.min(totalChannels, vanillaStreamingLimit);
		}
		int requested = Math.min(vanillaStreamingLimit + RESERVED_DRONE_STREAMS,
				Math.max(vanillaStreamingLimit, totalChannels - MIN_STATIC_CHANNELS));
		return Math.min(totalChannels, requested);
	}
}
