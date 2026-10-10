package com.tenicana.dronecraft.client.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class PerformanceReportWriterTest {
    private static final long MS = 1_000_000;

    @TempDir
    Path directory;

    @Test
    void htmlPairsIntervalWithPreviousFrameWhileCsvAndSummaryKeepCurrentFrameValues() throws IOException {
        PerformanceCapture capture = new PerformanceCapture(MS, 4, 2);
        frame(capture, 1, 10 * MS, 35 * MS, 30 * MS, 2 * MS, MS);
        capture.gpu(1, 29 * MS);
        frame(capture, 2, 50 * MS, 6 * MS, 4 * MS, MS / 2, 0);
        frame(capture, 3, 57 * MS, 100 * MS, 99 * MS, 0, 0);
        capture.gpu(3, 98 * MS);
        capture.seal(160 * MS);

        Path report = export(capture);
        JsonArray points = points(report);
        assertEquals(3, points.size());
        assertTrue(points.get(0).getAsJsonArray().get(1).isJsonNull());
        assertMissingComponents(points.get(0).getAsJsonArray());
        assertPoint(points.get(1).getAsJsonArray(), 49, 40, 30, 2, 29.0, 32, 1);
        assertPoint(points.get(2).getAsJsonArray(), 56, 7, 4, 0.5, null, 5.5, 0);

        String[] csvRows = Files.readString(report.resolve("frames.csv")).split("\n");
        assertEquals("4.000000", csvRows[2].split(",", -1)[4]);
        assertEquals("99.000000", csvRows[3].split(",", -1)[4]);
        JsonObject summary = JsonParser.parseString(Files.readString(report.resolve("summary.json"))).getAsJsonObject();
        assertEquals(99, summary.getAsJsonObject("all").getAsJsonObject("render_wall_ms").get("max_ms").getAsDouble());
    }

    @Test
    void overwrittenPredecessorLeavesComponentsMissingAndMissingGpuDoesNotEraseOtherTimings() throws IOException {
        PerformanceCapture capture = new PerformanceCapture(MS, 2, 2);
        frame(capture, 1, 10 * MS, 6 * MS, 3 * MS, MS, 0);
        frame(capture, 2, 20 * MS, 8 * MS, 5 * MS, MS, MS);
        frame(capture, 3, 30 * MS, 7 * MS, 4 * MS, MS, 0);
        capture.gpu(3, 6 * MS);
        capture.seal(40 * MS);

        JsonArray points = points(export(capture));
        assertEquals(2, points.size());
        assertEquals(10, points.get(0).getAsJsonArray().get(1).getAsDouble());
        assertMissingComponents(points.get(0).getAsJsonArray());
        assertPoint(points.get(1).getAsJsonArray(), 29, 10, 5, 1, null, 6, 1);
    }

    @Test
    void discardedFrameGapDoesNotBorrowTimingsFromAnEarlierRetainedFrame() throws IOException {
        PerformanceCapture capture = new PerformanceCapture(MS, 4, 2);
        frame(capture, 1, 10 * MS, 6 * MS, 3 * MS, MS, 0);
        capture.beginFrame(2, 20 * MS, 0, 1, 0, 0);
        capture.finishFrame(2, 8 * MS, false);
        frame(capture, 3, 50 * MS, 12 * MS, 9 * MS, MS, 0);
        capture.gpu(3, 7 * MS);
        frame(capture, 4, 60 * MS, 7 * MS, 4 * MS, MS, 0);
        capture.seal(80 * MS);

        JsonArray points = points(export(capture));
        assertEquals(3, points.size());
        JsonArray gap = points.get(1).getAsJsonArray();
        assertEquals(30, gap.get(1).getAsDouble());
        assertMissingComponents(gap);
        assertPoint(points.get(2).getAsJsonArray(), 59, 10, 9, 1, 7.0, 11, 0);
    }

    @Test
    void emptyAndSingleFrameCapturesDoNotInventAnIntervalOrComponents() throws IOException {
        PerformanceCapture empty = new PerformanceCapture(MS, 2, 2);
        empty.seal(2 * MS);
        assertEquals(0, points(export(empty)).size());

        PerformanceCapture single = new PerformanceCapture(MS, 2, 2);
        frame(single, 1, 10 * MS, 6 * MS, 3 * MS, MS, 0);
        single.gpu(1, 2 * MS);
        single.seal(20 * MS);
        JsonArray points = points(export(single));
        assertEquals(1, points.size());
        assertTrue(points.get(0).getAsJsonArray().get(1).isJsonNull());
        assertMissingComponents(points.get(0).getAsJsonArray());
    }

    @Test
    void downsamplingKeepsLargestIntervalAndItsVerifiedPredecessorTimings() throws IOException {
        PerformanceCapture capture = new PerformanceCapture(MS, 6_000, 2);
        long start = MS;
        for (int row = 0; row < 6_000; row++) {
            if (row > 0) start += (row == 1_001 ? 80 : 10) * MS;
            long render = (row + 1) * 1_000L;
            frame(capture, row + 1, start, render + 2 * MS, render, MS / 2, MS / 4);
            if (row == 1_000) capture.gpu(row + 1, 4 * MS);
            if (row == 1_001) capture.gpu(row + 1, 40 * MS);
        }
        capture.seal(start + 20 * MS);

        JsonArray points = points(export(capture));
        assertEquals(3_000, points.size());
        long[][] frames = capture.snapshot().frames();
        assertPoint(points.get(500).getAsJsonArray(),
                (frames[PerformanceCapture.START][1_001] - MS) / (double) MS,
                80, 1.001, 0.5, 4.0, 2.251, 0.25);
    }

    private Path export(PerformanceCapture capture) throws IOException {
        return PerformanceReportWriter.export(directory, "alignment", capture.snapshot(), Map.of(), 120, "test");
    }

    private static JsonArray points(Path report) throws IOException {
        String html = Files.readString(report.resolve("report.html"));
        String prefix = "const data=";
        int start = html.indexOf(prefix);
        assertTrue(start >= 0);
        start += prefix.length();
        int end = html.indexOf(";\n", start);
        assertTrue(end > start);
        return JsonParser.parseString(html.substring(start, end)).getAsJsonObject().getAsJsonArray("points");
    }

    private static void assertMissingComponents(JsonArray point) {
        for (int column = 2; column < 7; column++) {
            assertTrue(point.get(column).isJsonNull(), "Component " + column + " needs a retained matching predecessor");
        }
    }

    private static void assertPoint(JsonArray point, double time, double interval, double render, double swap,
            Double gpu, double work, double limiter) {
        Double[] expected = { time, interval, render, swap, gpu, work, limiter };
        assertEquals(expected.length, point.size());
        for (int column = 0; column < expected.length; column++) {
            if (expected[column] == null) assertTrue(point.get(column).isJsonNull());
            else assertEquals(expected[column], point.get(column).getAsDouble(), 1e-9, "Column " + column);
        }
    }

    private static void frame(PerformanceCapture capture, long token, long start, long elapsed, long render,
            long swap, long limiter) {
        capture.beginFrame(token, start, 0, 1, 0, 0);
        capture.render(token, render, true);
        capture.swap(token, swap);
        capture.limit(token, limiter);
        capture.finishFrame(token, elapsed, true);
    }
}
