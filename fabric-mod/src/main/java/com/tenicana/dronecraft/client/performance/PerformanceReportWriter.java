package com.tenicana.dronecraft.client.performance;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** Writes a sealed primitive capture on the background export thread. */
public final class PerformanceReportWriter {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final Gson INLINE_JSON = new GsonBuilder().serializeNulls().create();
    private static final DateTimeFormatter DIRECTORY_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private static final int PLOT_POINT_LIMIT = 3_000;
    private static final String[] FRAME_METRICS = {
            "interval_ms", "total_wall_ms", "render_wall_ms", "swap_wall_ms", "limiter_wait_ms",
            "work_wall_ms", "gpu_render_ms", "client_tick_sum_ms", "client_tick_max_ms",
            "server_tick_age_ms", "pose_delivery_age_ms"
    };
    private static final int[] FRAME_METRIC_COLUMNS = {
            PerformanceCapture.INTERVAL, PerformanceCapture.TOTAL, PerformanceCapture.RENDER,
            PerformanceCapture.SWAP, PerformanceCapture.LIMIT, -1, PerformanceCapture.GPU,
            PerformanceCapture.CLIENT_TICK_SUM, PerformanceCapture.CLIENT_TICK_MAX,
            PerformanceCapture.SERVER_AGE, PerformanceCapture.POSE_AGE
    };

    private PerformanceReportWriter() { }

    public static Path export(
            Path baseDirectory,
            String captureLabel,
            PerformanceCapture.Snapshot snapshot,
            Map<String, Object> metadata,
            double targetFps,
            String gpuStatus
    ) throws IOException {
        Objects.requireNonNull(baseDirectory, "baseDirectory");
        Objects.requireNonNull(snapshot, "snapshot");
        if (!Double.isFinite(targetFps) || targetFps <= 0) {
            throw new IllegalArgumentException("targetFps must be finite and positive");
        }
        String label = captureLabel == null ? "capture" : captureLabel;
        String safeLabel = label.replaceAll("[^A-Za-z0-9_-]+", "-").replaceAll("^-+|-+$", "");
        if (safeLabel.isEmpty()) safeLabel = "capture";
        if (safeLabel.length() > 40) safeLabel = safeLabel.substring(0, 40);
        Path base = baseDirectory.toAbsolutePath().normalize();
        Files.createDirectories(base);
        String name = "perf-" + DIRECTORY_TIME.format(Instant.now()) + "-" + safeLabel + "-" + UUID.randomUUID();
        Path reportDirectory = base.resolve(name);
        Path stagingDirectory = base.resolve(name + ".partial");
        Files.createDirectory(stagingDirectory);

        double budgetMs = 1_000.0 / targetFps;
        List<SaveSpan> saves = saveSpans(snapshot);
        Map<String, Object> summary = summary(snapshot, label, metadata, targetFps, budgetMs, gpuStatus, saves);
        writeFrames(stagingDirectory.resolve("frames.csv"), snapshot);
        writeEvents(stagingDirectory.resolve("events.csv"), snapshot, saves);
        writeNew(stagingDirectory.resolve("summary.json"), JSON.toJson(summary) + "\n");
        writeNew(stagingDirectory.resolve("README.md"), README);
        writeNew(stagingDirectory.resolve("report.html"), html(snapshot, label, budgetMs, saves));

        // A random, previously unused name and no REPLACE_EXISTING option preserve prior exports.
        if (Files.exists(reportDirectory)) throw new IOException("Report destination already exists");
        try {
            Files.move(stagingDirectory, reportDirectory, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(stagingDirectory, reportDirectory);
        }
        return reportDirectory;
    }

    private static void writeNew(Path path, String text) throws IOException {
        Files.writeString(path, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static void writeFrames(Path path, PerformanceCapture.Snapshot snapshot) throws IOException {
        long[][] f = snapshot.frames();
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            writer.write("frame_token,start_ms,interval_ms,total_wall_ms,render_wall_ms,swap_wall_ms,"
                    + "limiter_wait_ms,work_wall_ms,gpu_render_ms,client_tick_count,client_tick_sum_ms,"
                    + "client_tick_max_ms,flags,has_level,screen,paused,focused,fpv,entity_id,heap_used_bytes,"
                    + "gc_delta_ms,server_tick_age_ms,pose_delivery_age_ms,save_overlap\n");
            for (int row = 0; row < f[0].length; row++) {
                int flags = (int) f[PerformanceCapture.FLAGS][row];
                String[] values = {
                        number(f[PerformanceCapture.TOKEN][row]), ms(f[PerformanceCapture.START][row] - snapshot.origin()),
                        optionalMs(f[PerformanceCapture.INTERVAL][row]), ms(f[PerformanceCapture.TOTAL][row]),
                        ms(f[PerformanceCapture.RENDER][row]), ms(f[PerformanceCapture.SWAP][row]),
                        ms(f[PerformanceCapture.LIMIT][row]), ms(workNs(f, row)),
                        optionalMs(f[PerformanceCapture.GPU][row]), number(f[PerformanceCapture.CLIENT_TICKS][row]),
                        ms(f[PerformanceCapture.CLIENT_TICK_SUM][row]), ms(f[PerformanceCapture.CLIENT_TICK_MAX][row]),
                        number(flags), flag(flags, PerformanceCapture.LEVEL), flag(flags, PerformanceCapture.SCREEN),
                        flag(flags, PerformanceCapture.PAUSED), flag(flags, PerformanceCapture.FOCUSED),
                        flag(flags, PerformanceCapture.FPV), number(f[PerformanceCapture.ENTITY][row]),
                        number(f[PerformanceCapture.HEAP][row]), number(f[PerformanceCapture.GC_DELTA][row]),
                        optionalMs(f[PerformanceCapture.SERVER_AGE][row]), optionalMs(f[PerformanceCapture.POSE_AGE][row]),
                        number(f[PerformanceCapture.SAVE_OVERLAP][row])
                };
                writeCsvRow(writer, values);
            }
        }
    }

    private static void writeEvents(Path path, PerformanceCapture.Snapshot snapshot, List<SaveSpan> saves) throws IOException {
        Map<Long, SaveSpan> saveByStart = new LinkedHashMap<>();
        for (SaveSpan save : saves) saveByStart.put(save.startNs, save);
        long[][] e = snapshot.events();
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            writer.write("event_index,type,start_ms,duration_ms,interval_ms,entity_id,save_silent,save_flush,"
                    + "save_force,save_result,gc_delta_ms,heap_used_bytes,marker,value_a,value_b,value_c,"
                    + "value_d,value_e,value_a_semantics,value_b_semantics\n");
            for (int row = 0; row < e[0].length; row++) {
                int kind = (int) e[0][row];
                long a = e[3][row], b = e[4][row];
                SaveSpan save = saveByStart.get(e[1][row]);
                boolean saveEvent = kind == PerformanceCapture.SAVE || kind == PerformanceCapture.SAVE_BEGIN;
                Integer saveFlags = saveEvent && save != null ? save.flags : null;
                String marker = kind == PerformanceCapture.MARKER && a >= 0 && a < snapshot.markers().size()
                        ? snapshot.markers().get((int) a) : "";
                String[] values = {
                        number(row), eventName(kind), ms(e[1][row] - snapshot.origin()),
                        timedEvent(kind) ? ms(e[2][row]) : "", intervalEvent(kind) && a > 0 ? ms(a) : "",
                        droneEvent(kind) ? number(b) : "", saveFlags == null ? "" : flag(saveFlags, 1),
                        saveFlags == null ? "" : flag(saveFlags, 2), saveFlags == null ? "" : flag(saveFlags, 4),
                        kind == PerformanceCapture.SAVE ? saveResult((int) a) : "",
                        kind == PerformanceCapture.GC_OBSERVATION ? number(a) : "",
                        kind == PerformanceCapture.GC_OBSERVATION ? number(b) : "", marker,
                        number(a), number(b), number(e[5][row]), number(e[6][row]), number(e[7][row]),
                        valueASemantics(kind), droneEvent(kind) ? "entity_id" :
                                kind == PerformanceCapture.GC_OBSERVATION ? "heap_used_bytes" : "unused"
                };
                writeCsvRow(writer, values);
            }
        }
    }

    private static Map<String, Object> summary(
            PerformanceCapture.Snapshot snapshot, String label, Map<String, Object> metadata,
            double targetFps, double budgetMs, String gpuStatus, List<SaveSpan> saves
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schema", "mcfpv-runtime-performance-v1");
        result.put("capture_label", label);
        result.put("capture_duration_ms", (snapshot.end() - snapshot.origin()) / 1e6);
        result.put("target_fps", targetFps);
        result.put("frame_budget_ms", budgetMs);
        result.put("gpu_status", gpuStatus == null ? "unknown" : gpuStatus);
        result.put("metadata", metadata == null ? Map.of() : new LinkedHashMap<>(metadata));
        result.put("percentile_method", "nearest_rank");
        result.put("jitter_method", "population_standard_deviation_ms");
        result.put("gameplay_filter", "LEVEL && FOCUSED && !SCREEN && !PAUSED");
        result.put("all", frameStatistics(snapshot.frames(), budgetMs, false));
        result.put("gameplay", frameStatistics(snapshot.frames(), budgetMs, true));
        Map<String, Object> retention = new LinkedHashMap<>();
        retention.put("total_completed_frames", snapshot.totalFrames());
        retention.put("retained_frame_rows", snapshot.frames()[0].length);
        retention.put("overwritten_frame_rows", Math.max(0, snapshot.totalFrames() - snapshot.frames()[0].length));
        retention.put("total_events", snapshot.totalEvents());
        retention.put("retained_event_rows", snapshot.events()[0].length);
        retention.put("overwritten_event_rows", Math.max(0, snapshot.totalEvents() - snapshot.events()[0].length));
        retention.put("incomplete_frames", snapshot.incompleteFrames());
        retention.put("gpu_skipped_query_results", snapshot.gpuSkipped());
        retention.put("incomplete_saves_at_seal", snapshot.incompleteSaves());
        retention.put("save_begins_without_retained_finish", saves.stream().filter(s -> !s.finished).count());
        retention.put("save_finishes_without_retained_begin", saves.stream().filter(s -> !s.began).count());
        result.put("retention", retention);
        result.put("events", eventStatistics(snapshot.events()));
        List<Map<String, Object>> timeline = new ArrayList<>();
        for (SaveSpan save : saves) timeline.add(saveJson(save, snapshot));
        result.put("save_timeline", timeline);
        result.put("plot", Map.of("maximum_points", PLOT_POINT_LIMIT,
                "downsampling", "one maximum frame interval per contiguous bucket; other series use its retained predecessor when adjacent start delta matches the interval, otherwise null"));
        return result;
    }

    private static Map<String, Object> frameStatistics(long[][] frames, double budgetMs, boolean gameplayOnly) {
        Map<String, Object> result = new LinkedHashMap<>();
        int rows = 0, missingGpu = 0, saveOverlap = 0;
        for (int row = 0; row < frames[0].length; row++) {
            if (gameplayOnly && !gameplay((int) frames[PerformanceCapture.FLAGS][row])) continue;
            rows++;
            if (frames[PerformanceCapture.GPU][row] < 0) missingGpu++;
            if (frames[PerformanceCapture.SAVE_OVERLAP][row] != 0) saveOverlap++;
        }
        result.put("frame_rows", rows);
        result.put("gpu_missing_frames", missingGpu);
        result.put("save_overlap_frames", saveOverlap);
        for (int metric = 0; metric < FRAME_METRICS.length; metric++) {
            double[] values = new double[rows];
            int count = 0;
            for (int row = 0; row < frames[0].length; row++) {
                if (gameplayOnly && !gameplay((int) frames[PerformanceCapture.FLAGS][row])) continue;
                long ns = FRAME_METRIC_COLUMNS[metric] == -1 ? workNs(frames, row) : frames[FRAME_METRIC_COLUMNS[metric]][row];
                if (ns >= 0) values[count++] = ns / 1e6;
            }
            result.put(FRAME_METRICS[metric], statistics(Arrays.copyOf(values, count), budgetMs));
        }
        return result;
    }

    private static Map<String, Object> eventStatistics(long[][] events) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int kind = PerformanceCapture.SERVER_TICK; kind <= PerformanceCapture.SAVE_BEGIN; kind++) {
            int count = 0;
            for (long storedKind : events[0]) if (storedKind == kind) count++;
            if (count == 0) continue;
            double[] durations = new double[count], intervals = new double[count];
            int durationCount = 0, intervalCount = 0;
            for (int row = 0; row < events[0].length; row++) {
                if (events[0][row] != kind) continue;
                if (timedEvent(kind)) durations[durationCount++] = events[2][row] / 1e6;
                if (intervalEvent(kind) && events[3][row] > 0) intervals[intervalCount++] = events[3][row] / 1e6;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("event_rows", count);
            entry.put("duration_ms", statistics(Arrays.copyOf(durations, durationCount), 50));
            entry.put("interval_ms", statistics(Arrays.copyOf(intervals, intervalCount), 50));
            entry.put("comparison_budget_ms", 50);
            result.put(eventName(kind), entry);
        }
        return result;
    }

    private static Map<String, Object> statistics(double[] values, double budgetMs) {
        PerformanceCapture.Statistics stats = PerformanceCapture.statistics(values, budgetMs, false);
        boolean present = stats.samples() > 0;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("samples", stats.samples());
        result.put("mean_ms", present ? stats.meanMs() : null);
        result.put("p50_ms", present ? stats.p50Ms() : null);
        result.put("p95_ms", present ? stats.p95Ms() : null);
        result.put("p99_ms", present ? stats.p99Ms() : null);
        result.put("max_ms", present ? stats.maxMs() : null);
        result.put("jitter_stddev_ms", present ? stats.standardDeviationMs() : null);
        result.put("over_budget", stats.overBudget());
        result.put("over_twice_budget", stats.overTwiceBudget());
        result.put("over_budget_percent", present ? stats.overBudget() * 100.0 / stats.samples() : null);
        return result;
    }

    private static List<SaveSpan> saveSpans(PerformanceCapture.Snapshot snapshot) {
        Map<Long, SaveSpan> byStart = new LinkedHashMap<>();
        long[][] e = snapshot.events();
        for (int row = 0; row < e[0].length; row++) {
            int kind = (int) e[0][row];
            if (kind != PerformanceCapture.SAVE && kind != PerformanceCapture.SAVE_BEGIN) continue;
            long start = e[1][row];
            SaveSpan save = byStart.computeIfAbsent(start, SaveSpan::new);
            if (kind == PerformanceCapture.SAVE_BEGIN) {
                save.began = true;
                save.flags = (int) e[3][row];
            } else {
                save.finished = true;
                save.durationNs = e[2][row];
                save.result = (int) e[3][row];
            }
        }
        List<SaveSpan> result = new ArrayList<>(byStart.values());
        result.sort(Comparator.comparingLong(s -> s.startNs));
        return result;
    }

    private static Map<String, Object> saveJson(SaveSpan save, PerformanceCapture.Snapshot snapshot) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("start_ms", (save.startNs - snapshot.origin()) / 1e6);
        result.put("end_ms", save.finished ? (save.startNs - snapshot.origin() + save.durationNs) / 1e6 : null);
        result.put("duration_ms", save.finished ? save.durationNs / 1e6 : null);
        result.put("begin_retained", save.began);
        result.put("finish_retained", save.finished);
        result.put("silent", save.flags == null ? null : (save.flags & 1) != 0);
        result.put("flush", save.flags == null ? null : (save.flags & 2) != 0);
        result.put("force", save.flags == null ? null : (save.flags & 4) != 0);
        result.put("result", save.result == null ? null : saveResult(save.result));
        return result;
    }

    private static String html(PerformanceCapture.Snapshot snapshot, String label, double budgetMs, List<SaveSpan> saves) {
        long[][] f = snapshot.frames();
        int rows = f[0].length, points = Math.min(rows, PLOT_POINT_LIMIT);
        List<List<Object>> plotRows = new ArrayList<>(points);
        for (int bucket = 0; bucket < points; bucket++) {
            int first = (int) ((long) bucket * rows / points);
            int end = (int) ((long) (bucket + 1) * rows / points);
            int worst = first;
            for (int row = first + 1; row < end; row++) {
                if (f[PerformanceCapture.INTERVAL][row] > f[PerformanceCapture.INTERVAL][worst]) worst = row;
            }
            int previous = previousFrameForInterval(f, worst);
            List<Object> point = new ArrayList<>(7);
            point.add((f[PerformanceCapture.START][worst] - snapshot.origin()) / 1e6);
            point.add(nullableMs(f[PerformanceCapture.INTERVAL][worst]));
            // An interval ends at this row's start, so its work belongs to the
            // preceding frame. Retention or discarded frames can remove that
            // predecessor; never attribute an unrelated retained frame instead.
            point.add(previous < 0 ? null : f[PerformanceCapture.RENDER][previous] / 1e6);
            point.add(previous < 0 ? null : f[PerformanceCapture.SWAP][previous] / 1e6);
            point.add(previous < 0 ? null : nullableMs(f[PerformanceCapture.GPU][previous]));
            point.add(previous < 0 ? null : workNs(f, previous) / 1e6);
            point.add(previous < 0 ? null : f[PerformanceCapture.LIMIT][previous] / 1e6);
            plotRows.add(point);
        }
        List<Map<String, Object>> plotSaves = new ArrayList<>(), plotMarkers = new ArrayList<>();
        for (SaveSpan save : saves) plotSaves.add(saveJson(save, snapshot));
        long[][] e = snapshot.events();
        for (int row = 0; row < e[0].length; row++) {
            if (e[0][row] == PerformanceCapture.MARKER && e[3][row] >= 0 && e[3][row] < snapshot.markers().size()) {
                plotMarkers.add(Map.of("time", (e[1][row] - snapshot.origin()) / 1e6,
                        "label", snapshot.markers().get((int) e[3][row])));
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("points", plotRows);
        data.put("saves", plotSaves);
        data.put("markers", plotMarkers);
        data.put("duration", Math.max(1, (snapshot.end() - snapshot.origin()) / 1e6));
        data.put("budget", budgetMs);
        data.put("retained", rows);
        return HTML_PREFIX.replace("{{TITLE}}", escapeHtml(label)) + INLINE_JSON.toJson(data) + HTML_SUFFIX;
    }

    private static int previousFrameForInterval(long[][] frames, int row) {
        if (row == 0 || frames[PerformanceCapture.INTERVAL][row] < 0) return -1;
        long delta = frames[PerformanceCapture.START][row] - frames[PerformanceCapture.START][row - 1];
        return delta >= 0 && delta == frames[PerformanceCapture.INTERVAL][row] ? row - 1 : -1;
    }

    private static boolean gameplay(int flags) {
        return (flags & (PerformanceCapture.LEVEL | PerformanceCapture.FOCUSED))
                == (PerformanceCapture.LEVEL | PerformanceCapture.FOCUSED)
                && (flags & (PerformanceCapture.SCREEN | PerformanceCapture.PAUSED)) == 0;
    }

    private static long workNs(long[][] f, int row) {
        long total = Math.max(0, f[PerformanceCapture.TOTAL][row]);
        long afterSwap = total - Math.min(total, Math.max(0, f[PerformanceCapture.SWAP][row]));
        return afterSwap - Math.min(afterSwap, Math.max(0, f[PerformanceCapture.LIMIT][row]));
    }

    private static boolean timedEvent(int kind) {
        return kind == PerformanceCapture.SERVER_TICK || kind == PerformanceCapture.PAUSED_SERVER_TICK
                || kind == PerformanceCapture.SAVE || kind == PerformanceCapture.CLIENT_DRONE
                || kind == PerformanceCapture.SERVER_DRONE;
    }

    private static boolean intervalEvent(int kind) {
        return kind == PerformanceCapture.SERVER_TICK || kind == PerformanceCapture.PAUSED_SERVER_TICK
                || droneEvent(kind);
    }

    private static boolean droneEvent(int kind) {
        return kind == PerformanceCapture.CLIENT_DRONE || kind == PerformanceCapture.SERVER_DRONE
                || kind == PerformanceCapture.METADATA || kind == PerformanceCapture.MOVEMENT;
    }

    private static String eventName(int kind) {
        return switch (kind) {
            case PerformanceCapture.SERVER_TICK -> "SERVER_TICK";
            case PerformanceCapture.PAUSED_SERVER_TICK -> "PAUSED_SERVER_TICK";
            case PerformanceCapture.SAVE -> "SAVE";
            case PerformanceCapture.CLIENT_DRONE -> "CLIENT_DRONE";
            case PerformanceCapture.SERVER_DRONE -> "SERVER_DRONE";
            case PerformanceCapture.METADATA -> "METADATA";
            case PerformanceCapture.MOVEMENT -> "MOVEMENT";
            case PerformanceCapture.GC_OBSERVATION -> "GC_OBSERVATION";
            case PerformanceCapture.MARKER -> "MARKER";
            case PerformanceCapture.SAVE_BEGIN -> "SAVE_BEGIN";
            default -> "UNKNOWN_" + kind;
        };
    }

    private static String valueASemantics(int kind) {
        if (intervalEvent(kind)) return "interval_ns; first/missing <=0";
        return switch (kind) {
            case PerformanceCapture.SAVE -> "result_code: 1=true,0=false,-1=exception";
            case PerformanceCapture.SAVE_BEGIN -> "flags: silent=1,flush=2,force=4";
            case PerformanceCapture.GC_OBSERVATION -> "collection_time_delta_ms";
            case PerformanceCapture.MARKER -> "marker_index";
            default -> "unused";
        };
    }

    private static String saveResult(int result) {
        return switch (result) {
            case 1 -> "returned_true";
            case 0 -> "returned_false";
            case -1 -> "exception";
            default -> "unknown_" + result;
        };
    }

    private static String number(long value) { return Long.toString(value); }
    private static String flag(int flags, int flag) { return (flags & flag) != 0 ? "1" : "0"; }
    private static String ms(long ns) { return BigDecimal.valueOf(ns, 6).toPlainString(); }
    private static String optionalMs(long ns) { return ns < 0 ? "" : ms(ns); }
    private static Double nullableMs(long ns) { return ns < 0 ? null : ns / 1e6; }

    private static void writeCsvRow(BufferedWriter writer, String[] values) throws IOException {
        for (int column = 0; column < values.length; column++) {
            if (column > 0) writer.write(',');
            String value = values[column];
            if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                writer.write('"');
                writer.write(value.replace("\"", "\"\""));
                writer.write('"');
            } else writer.write(value);
        }
        writer.write('\n');
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static final class SaveSpan {
        private final long startNs;
        private long durationNs;
        private Integer flags, result;
        private boolean began, finished;
        private SaveSpan(long startNs) { this.startNs = startNs; }
    }

    private static final String README = """
            # MCFPV 运行时性能捕获

            打开 `report.html` 查看曲线，完整保留的逐帧和事件数据在 `frames.csv` / `events.csv`，统计和保存时间线在 `summary.json`。所有时间相对本次捕获起点，单位由字段名说明；空白或 JSON null 表示缺失，不能当作零。

            - `interval_ms` 是连续帧开始之间的实际间隔，包含等待。首帧没有上一帧，间隔为空。
            - `total_wall_ms` 是帧调用的墙钟耗时；`render_wall_ms` 是渲染调用的墙钟耗时；`swap_wall_ms` 是交换缓冲区调用耗时，可能包含垂直同步或驱动等待。
            - `limiter_wait_ms` 是显式限帧等待。`work_wall_ms = max(0, total_wall_ms - swap_wall_ms - limiter_wait_ms)` 只去掉这两段等待，仍是墙钟时间，不能视为 CPU 利用率或纯 CPU 执行时间。各项可能嵌套，不能直接相加。
            - `gpu_render_ms` 是异步 OpenGL 时间查询覆盖的 `GameRenderer.render` GPU 时间跨度，可能包含 CPU 提交期间的 GPU 空闲；不等于 GPU 活跃利用率。它不覆盖主目标清屏、最终 blit、交换缓冲、限帧等待或显示扫描输出。缺失表示查询不支持、尚未返回或被跳过，不能当作零。
            - `all` 包含所有保留帧；`gameplay` 只包含有世界、窗口有焦点、无界面且未暂停的帧。两组都使用同一个目标 FPS 预算。P50/P95/P99 使用 nearest-rank；抖动是总体标准差；超预算严格按 `>` 统计。
            - `SERVER_TICK` 和 `PAUSED_SERVER_TICK` 分开记录；服务器和无人机事件统计中的比较预算为 50 ms。事件间隔与无人机 ID 共同解释，多架无人机汇总不能当作单架无人机频率。
            - `METADATA` 是整包同步数据完成后的姿态目标接收；`MOVEMENT` 是位置/朝向插值目标接收，并非每次插值执行。静止或未变化时可以没有更新；间隔和 `pose_delivery_age_ms` 不能单独证明丢包或网络延迟。`server_tick_age_ms` 是距最近服务器 tick 开始的时间。
            - `SAVE_BEGIN` 的标志位为 silent=1、flush=2、force=4；与同一起点的 `SAVE` 配对。结果 1/0/-1 分别是返回 true、返回 false、抛出异常；这不是独立的落盘或存档重开验证。保存与帧重叠只说明时间相关，不能单独证明因果。
            - GC 仅记录 JVM 收集耗时计数的观测增量，不能定位每次暂停。CSV 的 `value_a` / `value_b` 保留原始事件值，其含义见同一行的 semantics 字段；原始间隔为纳秒，GC 增量为毫秒。
            - 捕获使用有界环形存储；覆盖、未完成帧、未完成保存和 GPU 缺失计数见 summary。统计仅针对保留行。HTML 在帧间隔终点显示前一帧的渲染、交换缓冲、GPU、去显式等待墙钟和限帧时间；只有相邻保留帧的开始时间差与记录间隔完全相等时才配对，前一帧缺失时这些曲线为空。最后一帧没有下一帧间隔，其耗时仍保留在 CSV 和 summary。HTML 最多 3000 点，每个连续桶保留最大的帧间隔及其配对耗时，因此其他曲线的独立尖峰可能被省略；精确分析请用 CSV。

            导出仅在捕获封存后的后台写入，先生成唯一 `.partial` 目录，再移动为完整报告目录；导出失败时残留的 `.partial` 不应作为完整报告。报告不额外采集用户名、世界路径或原始姿态坐标；metadata 由调用方的允许列表提供。HTML 无外部资源或网络请求。
            """;

    private static final String HTML_PREFIX = """
            <!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>MCFPV 性能报告 · {{TITLE}}</title><style>
            body{margin:0;background:#111820;color:#e6edf5;font:15px system-ui,sans-serif}main{max-width:1320px;margin:auto;padding:28px}
            h1{font-size:24px;margin:0 0 12px}.muted{color:#a6b6c8;line-height:1.7}.panel{padding:18px;background:#1a2531;border:1px solid #324354;border-radius:12px;margin:18px 0}
            .controls{display:flex;gap:18px;flex-wrap:wrap;align-items:center}label{display:inline-flex;gap:6px;align-items:center}button,input{accent-color:#5fcbd9}
            button{background:#314355;color:#fff;border:0;padding:7px 12px;border-radius:6px;cursor:pointer}input[type=number]{width:75px;background:#111820;color:#fff;border:1px solid #53687b;padding:5px}
            canvas{display:block;width:100%;height:460px;cursor:crosshair;margin:18px 0 6px}#tip{min-height:44px;line-height:1.5;font-variant-numeric:tabular-nums}.ranges{margin-top:12px}.ranges input{width:230px}
            a{color:#6fd4e5}@media(max-width:650px){main{padding:16px}canvas{height:360px}.ranges input{width:150px}}
            </style><main><h1>MCFPV 性能报告 · {{TITLE}}</h1><p class="muted">帧间隔在终点显示，耗时曲线对应产生该间隔的前一帧；前一帧缺失时耗时为空。墙钟时间与 GPU 查询分别显示。橙色区间为保存；虚线为目标帧预算。悬停看数值，滚轮缩放，滑块选择时间范围。</p>
            <div class="panel"><div class="controls" id="series"></div><div class="controls ranges"><label>起点 <input id="from" type="range" min="0" max="9999" value="0"></label><label>终点 <input id="to" type="range" min="1" max="10000" value="10000"></label><label>Y 上限 ms <input id="ymax" type="number" min="1" placeholder="自动"></label><button id="reset">重置视图</button></div><canvas id="chart"></canvas><div id="tip" class="muted"></div></div>
            <p id="retention" class="muted"></p><p class="muted">完整统计见 <a href="summary.json">summary.json</a>；全部保留样本见 <a href="frames.csv">frames.csv</a> / <a href="events.csv">events.csv</a>。指标定义和限制见 <a href="README.md">README.md</a>。</p></main><script>
            const data=
            """;

    private static final String HTML_SUFFIX = """
            ;
            const canvas=document.getElementById('chart'),ctx=canvas.getContext('2d'),tip=document.getElementById('tip');
            const startInput=document.getElementById('from'),endInput=document.getElementById('to'),yInput=document.getElementById('ymax');
            const series=[['帧间隔',1,'#6bd9e8',true],['渲染墙钟',2,'#b9a2ff',true],['交换缓冲',3,'#edb665',true],['GPU 渲染',4,'#85df95',true],['去显式等待墙钟',5,'#eeeeee',false],['限帧等待',6,'#e589b3',false]];
            for(const s of series){const label=document.createElement('label'),input=document.createElement('input');input.type='checkbox';input.checked=s[3];input.onchange=()=>{s[3]=input.checked;draw()};label.style.color=s[2];label.append(input,document.createTextNode(s[0]));document.getElementById('series').append(label)}
            document.getElementById('retention').textContent=`保留 ${data.retained} 帧；绘图 ${data.points.length} 点。超过 3000 帧时，每桶只保留最大的帧间隔及其配对耗时；其他曲线的独立尖峰可能被省略。最后一帧耗时见 CSV。`;
            let geometry={};const fmt=n=>n===null||n===undefined?'缺失':Number(n).toFixed(3);
            function range(){let a=Number(startInput.value)/10000*data.duration,b=Number(endInput.value)/10000*data.duration;if(b<=a)b=a+Math.max(.01,data.duration/10000);return[a,b]}
            function draw(){const r=canvas.getBoundingClientRect(),dpr=window.devicePixelRatio||1;canvas.width=Math.round(r.width*dpr);canvas.height=Math.round(r.height*dpr);ctx.setTransform(dpr,0,0,dpr,0,0);ctx.clearRect(0,0,r.width,r.height);
              const left=62,top=20,right=r.width-18,bottom=r.height-42,[a,b]=range();let max=data.budget*1.2;for(const p of data.points)if(p[0]>=a&&p[0]<=b)for(const s of series)if(s[3]&&p[s[1]]!==null)max=Math.max(max,p[s[1]]);max=Number(yInput.value)>0?Number(yInput.value):max*1.05;
              const x=t=>left+(t-a)/(b-a)*(right-left),y=v=>bottom-v/max*(bottom-top);geometry={left,top,right,bottom,a,b,x};ctx.font='12px system-ui';ctx.lineWidth=1;
              for(let i=0;i<=5;i++){const v=max*i/5,yy=y(v);ctx.strokeStyle='#334354';ctx.beginPath();ctx.moveTo(left,yy);ctx.lineTo(right,yy);ctx.stroke();ctx.fillStyle='#a6b6c8';ctx.fillText(v.toFixed(v<10?1:0),8,yy+4);const t=a+(b-a)*i/5;ctx.fillText((t/1000).toFixed(2)+'s',x(t)-18,bottom+25)}
              ctx.save();ctx.beginPath();ctx.rect(left,top,right-left,bottom-top);ctx.clip();for(const s of data.saves){const end=s.end_ms===null?data.duration:s.end_ms;if(end<a||s.start_ms>b)continue;ctx.fillStyle=s.result==='exception'?'#d5545540':'#f6a24830';ctx.fillRect(x(s.start_ms),top,Math.max(2,x(end)-x(s.start_ms)),bottom-top)}
              ctx.setLineDash([5,5]);ctx.strokeStyle='#dfdfdf99';ctx.beginPath();ctx.moveTo(left,y(data.budget));ctx.lineTo(right,y(data.budget));ctx.stroke();ctx.setLineDash([]);
              for(const m of data.markers){if(m.time<a||m.time>b)continue;ctx.strokeStyle='#ffffff66';ctx.beginPath();ctx.moveTo(x(m.time),top);ctx.lineTo(x(m.time),bottom);ctx.stroke();ctx.fillStyle='#dfe9f1';ctx.fillText(m.label.slice(0,26),x(m.time)+4,top+14)}
              for(const s of series){if(!s[3])continue;ctx.strokeStyle=s[2];ctx.lineWidth=1.5;ctx.beginPath();let active=false;for(const p of data.points){if(p[0]<a||p[0]>b||p[s[1]]===null){active=false;continue}if(active)ctx.lineTo(x(p[0]),y(p[s[1]]));else ctx.moveTo(x(p[0]),y(p[s[1]]));active=true}ctx.stroke()}ctx.restore();if(!data.points.length){ctx.fillStyle='#a6b6c8';ctx.fillText('没有已完成的保留帧',left+20,top+45)}}
            startInput.oninput=()=>{if(Number(startInput.value)>=Number(endInput.value))endInput.value=Math.min(10000,Number(startInput.value)+1);draw()};endInput.oninput=()=>{if(Number(endInput.value)<=Number(startInput.value))startInput.value=Math.max(0,Number(endInput.value)-1);draw()};yInput.oninput=draw;
            document.getElementById('reset').onclick=()=>{startInput.value=0;endInput.value=10000;yInput.value='';draw()};window.addEventListener('resize',draw);
            canvas.addEventListener('pointermove',event=>{const r=canvas.getBoundingClientRect(),px=event.clientX-r.left,g=geometry,t=g.a+(px-g.left)/(g.right-g.left)*(g.b-g.a);let nearest=null,distance=Infinity;for(const p of data.points)if(p[0]>=g.a&&p[0]<=g.b&&Math.abs(p[0]-t)<distance){nearest=p;distance=Math.abs(p[0]-t)}if(!nearest)return;const fields=[`t=${fmt(nearest[0])} ms`,...series.filter(s=>s[3]).map(s=>s[0]+' '+fmt(nearest[s[1]])+' ms')];for(const s of data.saves)if(nearest[0]>=s.start_ms&&nearest[0]<=(s.end_ms===null?data.duration:s.end_ms))fields.push('保存 '+(s.result||'未完成'));for(const m of data.markers)if(Math.abs(g.x(m.time)-px)<8)fields.push('标记 '+m.label);tip.textContent=fields.join(' · ')});
            canvas.addEventListener('wheel',event=>{event.preventDefault();const g=geometry,r=canvas.getBoundingClientRect(),anchor=Math.max(0,Math.min(1,(event.clientX-r.left-g.left)/(g.right-g.left))),factor=event.deltaY>0?1.3:1/1.3,width=Math.max(data.duration/10000,Math.min(data.duration,(g.b-g.a)*factor)),center=g.a+(g.b-g.a)*anchor,a=Math.max(0,Math.min(data.duration-width,center-width*anchor));startInput.value=Math.round(a/data.duration*10000);endInput.value=Math.round((a+width)/data.duration*10000);draw()},{passive:false});draw();
            </script></html>
            """;
}
