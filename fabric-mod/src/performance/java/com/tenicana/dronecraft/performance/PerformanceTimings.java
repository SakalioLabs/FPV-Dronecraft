package com.tenicana.dronecraft.performance;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Primitive, bounded timing storage; no world objects leave their owning thread. */
public final class PerformanceTimings {
	public enum Phase { OPENING, SETUP, WARMUP, BASELINE, CAMERA_RETURN, AUTOSAVE, AFTER_SAVE,
		RUNTIME_CPU_EXPORT, RUNTIME_GPU, RUNTIME_GPU_EXPORT, RUNTIME_RESTART, RUNTIME_DISCONNECT_EXPORT,
		FLUSH, CLOSING, REOPENING, VERIFYING, REPORT }
	public enum Metric {
		FRAME_INTERVAL, FRAME_DURATION, RENDER_DURATION, CLIENT_TICK_DURATION,
		SERVER_TICK_DURATION, SERVER_TICK_INTERVAL, SERVER_PAUSED_TICK_DURATION,
		CLIENT_TO_SERVER_QUEUE_DELAY, FRAME_INTERVAL_OVERLAPPING_SAVE,
		FRAME_DURATION_OVERLAPPING_SAVE, SERVER_TICK_INTERVAL_OVERLAPPING_SAVE,
		FRAME_INTERVAL_CROSSING_PHASE_BOUNDARY,
		CLIENT_DRONE_TICK_INTERVAL, SERVER_DRONE_TICK_INTERVAL,
		CLIENT_DRONE_TICK_INTERVAL_OVERLAPPING_SAVE, SERVER_DRONE_TICK_INTERVAL_OVERLAPPING_SAVE
	}

	private static final int MAX_SAMPLES_PER_SERIES = 16_384;
	private static final EnumMap<Phase, EnumMap<Metric, Series>> SERIES = new EnumMap<>(Phase.class);
	private static final List<SaveSpan> SAVES = new ArrayList<>();
	private static final List<PhaseStart> PHASES = new ArrayList<>();
	private static final ThreadLocal<ArrayDeque<OpenSave>> OPEN_SAVES = ThreadLocal.withInitial(ArrayDeque::new);
	private static final AtomicInteger SAVE_DEPTH = new AtomicInteger();
	private static final AtomicLong SAVE_REVISION = new AtomicLong();
	private static final AtomicLong CONTROL_SUBMISSIONS = new AtomicLong();
	private static final AtomicLong CONTROL_COMPLETIONS = new AtomicLong();
	private static final AtomicLong CONTROL_DROPPED = new AtomicLong();
	private static final long ORIGIN = System.nanoTime();
	private static volatile Phase phase = Phase.OPENING;
	private static volatile Phase lastSavePhase = Phase.OPENING;
	private static long lastClientDroneTick;
	private static long lastServerDroneTick;
	private static Phase lastClientDronePhase;
	private static Phase lastServerDronePhase;
	private static long lastClientDroneSaveRevision;
	private static long lastServerDroneSaveRevision;

	static {
		for (Phase value : Phase.values()) {
			EnumMap<Metric, Series> metrics = new EnumMap<>(Metric.class);
			for (Metric metric : Metric.values()) metrics.put(metric, new Series());
			SERIES.put(value, metrics);
		}
		PHASES.add(new PhaseStart(phase, ORIGIN));
	}

	private PerformanceTimings() { }
	public static Phase phase() { return phase; }
	public static long saveRevision() { return SAVE_REVISION.get(); }
	public static Phase lastSavePhase() { return lastSavePhase; }
	public static boolean saveActive() { return SAVE_DEPTH.get() > 0; }
	public static void phase(Phase value) {
		phase = value;
		synchronized (PHASES) { PHASES.add(new PhaseStart(value, System.nanoTime())); }
	}
	public static void add(Phase samplePhase, Metric metric, long nanoseconds) {
		if (nanoseconds >= 0 && samplePhase != Phase.REPORT) SERIES.get(samplePhase).get(metric).add(nanoseconds);
	}
	public static void controlSubmitted() { CONTROL_SUBMISSIONS.incrementAndGet(); }
	public static void controlCompleted() { CONTROL_COMPLETIONS.incrementAndGet(); }
	public static void controlDropped() { CONTROL_DROPPED.incrementAndGet(); }
	public static void droneTick(boolean client) {
		long now = System.nanoTime();
		Phase current = phase;
		long revision = SAVE_REVISION.get();
		if (client) {
			if (lastClientDroneTick != 0 && lastClientDronePhase == current) add(current, Metric.CLIENT_DRONE_TICK_INTERVAL, now - lastClientDroneTick);
			if (lastClientDroneTick != 0 && (saveActive() || revision != lastClientDroneSaveRevision)) add(lastSavePhase, Metric.CLIENT_DRONE_TICK_INTERVAL_OVERLAPPING_SAVE, now - lastClientDroneTick);
			lastClientDroneTick = now;
			lastClientDronePhase = current;
			lastClientDroneSaveRevision = revision;
		} else {
			if (lastServerDroneTick != 0 && lastServerDronePhase == current) add(current, Metric.SERVER_DRONE_TICK_INTERVAL, now - lastServerDroneTick);
			if (lastServerDroneTick != 0 && (saveActive() || revision != lastServerDroneSaveRevision)) add(lastSavePhase, Metric.SERVER_DRONE_TICK_INTERVAL_OVERLAPPING_SAVE, now - lastServerDroneTick);
			lastServerDroneTick = now;
			lastServerDronePhase = current;
			lastServerDroneSaveRevision = revision;
		}
	}
	public static void saveBegin(boolean silent, boolean flush, boolean force) {
		lastSavePhase = phase;
		OPEN_SAVES.get().push(new OpenSave(System.nanoTime(), phase, silent, flush, force));
		SAVE_DEPTH.incrementAndGet();
		SAVE_REVISION.incrementAndGet();
	}
	public static void saveEnd(boolean result) {
		ArrayDeque<OpenSave> stack = OPEN_SAVES.get();
		if (stack.isEmpty()) return;
		OpenSave open = stack.pop();
		SaveSpan span = new SaveSpan(open.start, System.nanoTime(), open.phase, open.silent, open.flush, open.force, result, Thread.currentThread().getName());
		synchronized (SAVES) { if (SAVES.size() < 128) SAVES.add(span); }
		SAVE_DEPTH.decrementAndGet();
		SAVE_REVISION.incrementAndGet();
	}

	public static JsonObject report() {
		JsonObject report = new JsonObject();
		JsonObject timings = new JsonObject();
		for (Phase value : Phase.values()) {
			JsonObject metrics = new JsonObject();
			for (Metric metric : Metric.values()) {
				Series series = SERIES.get(value).get(metric);
				if (series.count() != 0) metrics.add(metric.name().toLowerCase(), series.report());
			}
			timings.add(value.name().toLowerCase(), metrics);
		}
		report.add("timings", timings);
		JsonArray spans = new JsonArray();
		synchronized (SAVES) {
			for (SaveSpan span : SAVES) {
				JsonObject json = new JsonObject();
				json.addProperty("phase", span.phase.name().toLowerCase());
				json.addProperty("start_ms", millis(span.start - ORIGIN));
				json.addProperty("duration_ms", millis(span.end - span.start));
				json.addProperty("silent", span.silent);
				json.addProperty("flush", span.flush);
				json.addProperty("force", span.force);
				json.addProperty("returned_success", span.result);
				json.addProperty("thread", span.thread);
				spans.add(json);
			}
		}
		report.add("save_spans", spans);
		JsonArray phases = new JsonArray();
		synchronized (PHASES) {
			for (PhaseStart start : PHASES) {
				JsonObject json = new JsonObject();
				json.addProperty("phase", start.phase.name().toLowerCase());
				json.addProperty("start_ms", millis(start.start - ORIGIN));
				phases.add(json);
			}
		}
		report.add("phase_starts", phases);
		report.addProperty("open_save_spans", SAVE_DEPTH.get());
		report.addProperty("queue_probes_submitted", CONTROL_SUBMISSIONS.get());
		report.addProperty("queue_probes_completed", CONTROL_COMPLETIONS.get());
		report.addProperty("queue_probes_skipped_at_capacity", CONTROL_DROPPED.get());
		return report;
	}

	private static double millis(long nanos) { return nanos / 1_000_000.0; }
	private record OpenSave(long start, Phase phase, boolean silent, boolean flush, boolean force) { }
	private record SaveSpan(long start, long end, Phase phase, boolean silent, boolean flush, boolean force, boolean result, String thread) { }
	private record PhaseStart(Phase phase, long start) { }
	private static final class Series {
		private final long[] samples = new long[MAX_SAMPLES_PER_SERIES];
		private int size;
		private long dropped;
		synchronized void add(long value) {
			if (size == samples.length) dropped++;
			else samples[size++] = value;
		}
		synchronized int count() { return size; }
		synchronized JsonObject report() {
			long[] ordered = Arrays.copyOf(samples, size);
			Arrays.sort(ordered);
			double total = 0;
			int over8 = 0, over16 = 0, over50 = 0, over100 = 0;
			for (long value : ordered) {
				total += value;
				if (value > 8_695_652) over8++;
				if (value > 16_666_667) over16++;
				if (value > 50_000_000) over50++;
				if (value > 100_000_000) over100++;
			}
			JsonObject json = new JsonObject();
			json.addProperty("samples", size);
			json.addProperty("dropped_samples", dropped);
			json.addProperty("mean_ms", total / size / 1_000_000.0);
			json.addProperty("p50_ms", percentile(ordered, 0.50));
			json.addProperty("p95_ms", percentile(ordered, 0.95));
			json.addProperty("p99_ms", percentile(ordered, 0.99));
			json.addProperty("max_ms", millis(ordered[size - 1]));
			json.addProperty("over_115_hz_budget", over8);
			json.addProperty("over_16_67_ms", over16);
			json.addProperty("over_50_ms", over50);
			json.addProperty("over_100_ms", over100);
			return json;
		}
		private static double percentile(long[] values, double fraction) {
			return millis(values[Math.min(values.length - 1, (int) Math.ceil(values.length * fraction) - 1)]);
		}
	}
}
