package com.tenicana.dronecraft.playtest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.client.performance.RuntimePerformanceMonitor;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;

/** Functional hook/export validation under regulated GameTest ticks; never FPS evidence. */
final class RuntimePerformanceSmoke {
    private static final String MARKER = "fabric_client_gametest_functional_only";
    private final ClientGameTestContext context;
    private boolean started, hiddenHud;
    private int poseFixtureId = -1;
    private Path outputRoot;

    RuntimePerformanceSmoke(ClientGameTestContext context) {
        this.context = context;
    }

    void start() {
        context.waitFor(client -> ClientCommandManager.getActiveDispatcher() != null, 200);
        context.runOnClient(client -> {
            check(System.getProperty("fabric.client.gametest") != null,
                    "runtime monitor functional smoke must be explicitly labeled as Fabric Client GameTest");
            check(!RuntimePerformanceMonitor.enabled(), "runtime monitor must start disabled");
            outputRoot = client.gameDirectory.toPath().toAbsolutePath().normalize().resolve("performance-diagnostics");
            validateCommandParsing(client);
            execute(client, "fpvperf hud off");
            hiddenHud = true;
            execute(client, "fpvperf start 600");
            started = RuntimePerformanceMonitor.enabled();
            check(started && !RuntimePerformanceMonitor.gpuTimingEnabled(),
                    "registered fpvperf start must enable CPU capture without GPU queries");
            execute(client, "fpvperf status");
            execute(client, "fpvperf mark " + MARKER);
        });
    }

    void recordPoseFixture(int entityId) {
        poseFixtureId = entityId;
    }

    JsonObject finishAndVerify() {
        check(poseFixtureId >= 0, "packet fixture ID must be retained for exact monitor event checks");
        CompletableFuture<Path> future = context.computeOnClient(client -> {
            check(RuntimePerformanceMonitor.enabled(), "CPU monitor must remain active through the playable workflow");
            check(RuntimePerformanceMonitor.currentFrameToken() != 0,
                    "registered stop must execute during an open frame");
            execute(client, "fpvperf stop");
            started = false;
            check(!RuntimePerformanceMonitor.enabled(), "registered stop must disable capture immediately");
            execute(client, "fpvperf hud on");
            hiddenHud = false;
            CompletableFuture<Path> export = RuntimePerformanceMonitor.lastExport();
            check(export != null, "registered stop must schedule a background export");
            return export;
        });
        long deadline = System.nanoTime() + 30_000_000_000L;
        context.waitFor(client -> {
            check(System.nanoTime() < deadline || future.isDone(), "runtime monitor export exceeded 30 seconds");
            check(!RuntimePerformanceMonitor.enabled(), "capture must remain disabled while exporting");
            return future.isDone();
        }, 400);
        // Already completed: join cannot wait on the render thread or block a test tick.
        Path directory = future.join().toAbsolutePath().normalize();
        check(directory.startsWith(outputRoot), "monitor export must stay inside the disposable game directory");
        check(!directory.getFileName().toString().endsWith(".partial"), "monitor report must be fully published");
        try {
            return inspect(directory);
        } catch (IOException failure) {
            throw new IllegalStateException("could not inspect functional runtime monitor report", failure);
        }
    }

    void close() {
        if (!started && !hiddenHud) return;
        context.runOnClient(client -> {
            try {
                if (RuntimePerformanceMonitor.enabled()) RuntimePerformanceMonitor.stop(client);
            } finally {
                if (hiddenHud && client.getConnection() != null) execute(client, "fpvperf hud on");
                started = hiddenHud = false;
            }
        });
    }

    private JsonObject inspect(Path directory) throws IOException {
        for (String name : List.of("frames.csv", "events.csv", "summary.json", "report.html", "README.md")) {
            check(Files.isRegularFile(directory.resolve(name)), "monitor report missing " + name);
        }
        JsonObject summary = JsonParser.parseString(Files.readString(directory.resolve("summary.json"))).getAsJsonObject();
        check("mcfpv-runtime-performance-v1".equals(summary.get("schema").getAsString()), "unexpected runtime report schema");
        check(summary.getAsJsonObject("metadata").get("fabric_client_gametest_active").getAsBoolean(),
                "export metadata must identify regulated Fabric Client GameTest capture");
        CsvTable frames = CsvTable.read(directory.resolve("frames.csv"));
        CsvTable events = CsvTable.read(directory.resolve("events.csv"));
        check(frames.rows.size() >= 2, "CPU monitor must export completed frame rows");
        long previousToken = 0;
        boolean render = false, swap = false, ticks = false;
        for (String[] row : frames.rows) {
            long token = Long.parseLong(frames.value(row, "frame_token"));
            check(token > previousToken, "completed frame tokens must be unique and chronological");
            previousToken = token;
            check(frames.number(row, "total_wall_ms") >= 0, "frame wall time must be finite and nonnegative");
            render |= frames.number(row, "render_wall_ms") > 0;
            swap |= frames.number(row, "swap_wall_ms") > 0;
            ticks |= frames.number(row, "client_tick_sum_ms") > 0 && frames.number(row, "client_tick_count") > 0;
            check(frames.value(row, "gpu_render_ms").isEmpty(), "CPU capture must not report GPU query results");
        }
        check(render && swap && ticks, "CPU monitor must record positive render, native swap and client tick timings");
        int metadata = 0, movement = 0, server = 0;
        boolean marker = false;
        for (String[] row : events.rows) {
            String type = events.value(row, "type");
            boolean fixture = Integer.toString(poseFixtureId).equals(events.value(row, "entity_id"));
            if (fixture && type.equals("METADATA")) metadata++;
            if (fixture && type.equals("MOVEMENT")) movement++;
            if (type.equals("SERVER_TICK") && events.number(row, "duration_ms") > 0) server++;
            if (type.equals("MARKER") && MARKER.equals(events.value(row, "marker"))) marker = true;
        }
        check(metadata > 0 && movement > 0,
                "actual packet fixture must appear in both METADATA and MOVEMENT monitor events");
        check(server > 0, "CPU monitor must record positive integrated-server tick timings");
        check(marker, "registered mark command must appear in exported events");
        JsonObject retention = summary.getAsJsonObject("retention");
        check(retention.get("retained_frame_rows").getAsInt() == frames.rows.size(), "summary frame count must match CSV");
        check(retention.get("retained_event_rows").getAsInt() == events.rows.size(), "summary event count must match CSV");
        check(retention.get("incomplete_frames").getAsLong() >= 1, "midframe stop must discard the open partial frame");
        JsonObject result = new JsonObject();
        result.addProperty("passed", true);
        result.addProperty("scope", "Fabric Client GameTest functional hook and export coverage only; not natural-loop FPS evidence");
        result.addProperty("startedVia", "registered Fabric fpvperf client command dispatcher");
        result.addProperty("cpuOnly", true);
        result.addProperty("stoppedDuringOpenFrame", true);
        result.addProperty("backgroundExportCompleted", true);
        result.addProperty("reportDirectory", directory.toString());
        result.addProperty("packetFixtureId", poseFixtureId);
        result.addProperty("packetFixtureMetadataEvents", metadata);
        result.addProperty("packetFixtureMovementEvents", movement);
        result.addProperty("completedFrameRows", frames.rows.size());
        result.addProperty("positiveServerTickEvents", server);
        result.addProperty("positiveRenderSwapClientTicks", true);
        result.addProperty("registeredCommandParsingAndBounds", true);
        result.addProperty("captureDisabledAfterStop", true);
        FpvDronecraftMod.LOGGER.info("Playtest runtime monitor functional smoke passed: {}", directory);
        return result;
    }

    private static void validateCommandParsing(Minecraft client) {
        CommandDispatcher<FabricClientCommandSource> dispatcher = dispatcher();
        FabricClientCommandSource source = source(client);
        for (String command : List.of("fpvperf", "fpvperf start", "fpvperf start 600", "fpvperf startgpu 5",
                "fpvperf stop", "fpvperf status", "fpvperf mark " + MARKER,
                "fpvperf hud on", "fpvperf hud off", "fpvperf target 120")) {
            check(parses(dispatcher.parse(command, source)), "registered command must parse: " + command);
        }
        for (String command : List.of("fpvperf start 4", "fpvperf start 601", "fpvperf startgpu 4",
                "fpvperf startgpu 601", "fpvperf target 29", "fpvperf target 1001", "fpvperf mark",
                "fpvperf hud invalid")) {
            check(!parses(dispatcher.parse(command, source)), "registered command must reject: " + command);
        }
    }

    private static boolean parses(ParseResults<FabricClientCommandSource> parsed) {
        return !parsed.getReader().canRead() && parsed.getExceptions().isEmpty() && parsed.getContext().getCommand() != null;
    }

    private static void execute(Minecraft client, String command) {
        try {
            check(dispatcher().execute(command, source(client)) == 1, "client command did not succeed: " + command);
        } catch (CommandSyntaxException failure) {
            throw new AssertionError("registered client command failed: " + command, failure);
        }
    }

    private static CommandDispatcher<FabricClientCommandSource> dispatcher() {
        CommandDispatcher<FabricClientCommandSource> value = ClientCommandManager.getActiveDispatcher();
        check(value != null && value.getRoot().getChild("fpvperf") != null, "fpvperf must be registered in the active client dispatcher");
        return value;
    }

    private static FabricClientCommandSource source(Minecraft client) {
        check(client.getConnection() != null, "registered command smoke needs an active client connection");
        return (FabricClientCommandSource) (Object) client.getConnection().getSuggestionsProvider();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record CsvTable(List<String> columns, List<String[]> rows) {
        static CsvTable read(Path path) throws IOException {
            List<String> lines = Files.readAllLines(path);
            check(!lines.isEmpty(), "CSV needs a header: " + path);
            List<String> columns = parseLine(lines.getFirst());
            List<String[]> rows = new ArrayList<>();
            for (String line : lines.subList(1, lines.size())) {
                if (line.isEmpty()) continue;
                List<String> values = parseLine(line);
                check(values.size() == columns.size(), "CSV row width differs from header: " + path);
                rows.add(values.toArray(String[]::new));
            }
            return new CsvTable(columns, rows);
        }

        String value(String[] row, String name) {
            int column = columns.indexOf(name);
            check(column >= 0, "CSV missing column " + name);
            return row[column];
        }

        double number(String[] row, String name) {
            double value = Double.parseDouble(value(row, name));
            check(Double.isFinite(value), "CSV value must be finite: " + name);
            return value;
        }

        private static List<String> parseLine(String line) {
            List<String> fields = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            boolean quoted = false;
            for (int index = 0; index < line.length(); index++) {
                char value = line.charAt(index);
                if (value == '"') {
                    if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else quoted = !quoted;
                } else if (value == ',' && !quoted) {
                    fields.add(field.toString());
                    field.setLength(0);
                } else field.append(value);
            }
            check(!quoted, "CSV field must close its quotes");
            fields.add(field.toString());
            return fields;
        }
    }
}
