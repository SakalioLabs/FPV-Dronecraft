package com.tenicana.dronecraft.acoustics.tools;

import com.sun.management.ThreadMXBean;
import com.tenicana.dronecraft.acoustics.propagation.BoundedAcousticWorldEventTrace;
import com.tenicana.dronecraft.acoustics.propagation.CanonicalAcousticWorldEventPort;
import com.tenicana.dronecraft.acoustics.propagation.CellCaptureBounds;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;

import static com.tenicana.dronecraft.acoustics.propagation.CanonicalAcousticWorldEventPort.EventType.BLOCK_APPLY;
import static com.tenicana.dronecraft.acoustics.propagation.CanonicalAcousticWorldEventPort.EventType.BLOCK_ROLLBACK;
import static com.tenicana.dronecraft.acoustics.propagation.CanonicalAcousticWorldEventPort.EventType.CHUNK_LOAD;
import static com.tenicana.dronecraft.acoustics.propagation.CanonicalAcousticWorldEventPort.EventType.CHUNK_REPLACE;
import static com.tenicana.dronecraft.acoustics.propagation.CanonicalAcousticWorldEventPort.EventType.CHUNK_UNLOAD;

/** D121o offline replay of the bounded native callback trace contract. */
public final class NativeWorldEventTraceReplayCli {
	private static final int CAPACITY = 8;
	private static final CellCaptureBounds COVERAGE =
			new CellCaptureBounds(0, 0, 0, 31, 15, 31);

	private NativeWorldEventTraceReplayCli() {
	}

	public static void main(String[] args) throws IOException {
		Locale.setDefault(Locale.ROOT);
		if (args.length != 2) {
			throw new IllegalArgumentException(
					"usage: <output-json> <D121o-contract>"
			);
		}
		Path output = Path.of(args[0]);
		Path contract = Path.of(args[1]);
		Replay replay = replay();
		long[] allocations = allocationWindows();
		boolean chronological = true;
		boolean oneThread = true;
		long previousOrdinal = 0L;
		for (BoundedAcousticWorldEventTrace.Entry entry
				: replay.entries()) {
			chronological &= entry.ordinal() > previousOrdinal;
			previousOrdinal = entry.ordinal();
			oneThread &= entry.threadId() == replay.threadId();
		}
		String report = String.format(
				Locale.ROOT,
				"""
				{
				  "schema_version": 1,
				  "status": "valid-native-world-event-trace-replay",
				  "source_contract_sha256": "%s",
				  "trace_enabled": true,
				  "capacity": %d,
				  "recorded": %d,
				  "retained": %d,
				  "overwritten": %d,
				  "producer_thread_id": %d,
				  "retained_entries": %s,
				  "disabled_probe": {
				    "enabled": false,
				    "recorded": %d,
				    "retained": %d,
				    "overwritten": %d
				  },
				  "steady_record_allocation_windows_bytes": %s,
				  "gates": {
				    "capacity_bounded": %s,
				    "overwrites_oldest": %s,
				    "chronological_export": %s,
				    "one_producer_thread": %s,
				    "disabled_is_strict_noop": %s,
				    "zero_steady_record_allocation": %s
				  },
				  "captures_audio": false,
				  "physical_endpoint_opened": false,
				  "minecraft_client_started": false,
				  "client_level_read": false,
				  "native_callback_delivery_measured": false,
				  "cuda_executed": false,
				  "release_calibrated": false
				}
				""",
				sha256(Files.readAllBytes(contract)),
				replay.trace().capacity(),
				replay.trace().recorded(),
				replay.trace().size(),
				replay.trace().overwritten(),
				replay.threadId(),
				entriesJson(replay.entries()),
				replay.disabled().recorded(),
				replay.disabled().size(),
				replay.disabled().overwritten(),
				longs(allocations),
				replay.trace().capacity() == CAPACITY
						&& replay.trace().size() == CAPACITY,
				replay.trace().recorded() == 12L
						&& replay.trace().overwritten() == 4L
						&& replay.entries()[0].ordinal() == 5L
						&& replay.entries()[7].ordinal() == 12L,
				chronological,
				oneThread,
				!replay.disabled().enabled()
						&& replay.disabled().recorded() == 0L
						&& replay.disabled().size() == 0,
				Arrays.stream(allocations).allMatch(value -> value == 0L)
		);
		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.writeString(output, report, StandardCharsets.UTF_8);
		System.out.printf(
				Locale.ROOT,
				"{\"status\":\"valid-native-world-event-trace-replay\","
						+ "\"recorded\":%d,\"retained\":%d,"
						+ "\"overwritten\":%d,"
						+ "\"allocation_windows\":%s}%n",
				replay.trace().recorded(),
				replay.trace().size(),
				replay.trace().overwritten(),
				longs(allocations)
		);
	}

	private static Replay replay() {
		CanonicalAcousticWorldEventPort port =
				new CanonicalAcousticWorldEventPort();
		BoundedAcousticWorldEventTrace trace =
				new BoundedAcousticWorldEventTrace(CAPACITY, true);
		long threadId = Thread.currentThread().threadId();
		port.replaceWorld(1L);
		port.replaceActiveCoverage(
				new CellCaptureBounds[] {COVERAGE},
				1
		);
		recordWorld(trace, 1L, threadId);
		recordBlock(
				trace, port, 1L, 1L, threadId,
				BLOCK_APPLY, 4, 2, 4
		);
		recordBlock(
				trace, port, 1L, 2L, threadId,
				BLOCK_APPLY, 40, 2, 40
		);
		recordBlock(
				trace, port, 1L, 3L, threadId,
				BLOCK_ROLLBACK, 4, 2, 4
		);
		recordChunk(
				trace, port, 1L, 4L, threadId,
				CHUNK_UNLOAD, 0, 0, 15, 15
		);
		recordChunk(
				trace, port, 1L, 5L, threadId,
				CHUNK_REPLACE, 16, 16, 31, 31
		);
		recordChunk(
				trace, port, 1L, 5L, threadId,
				CHUNK_REPLACE, 16, 16, 31, 31
		);
		recordBlock(
				trace, port, 1L, 5L, threadId,
				BLOCK_APPLY, 5, 2, 4
		);
		recordChunk(
				trace, port, 1L, 4L, threadId,
				CHUNK_LOAD, 48, 48, 63, 63
		);
		port.replaceWorld(2L);
		recordWorld(trace, 2L, threadId);
		trace.record(
				2L,
				0L,
				threadId,
				BoundedAcousticWorldEventTrace.Kind.BLOCK_APPLY,
				BoundedAcousticWorldEventTrace.Disposition
						.ADAPTER_WORLD_MISMATCH_REJECTED,
				1, 1, 1, 1, 1, 1
		);
		port.replaceActiveCoverage(
				new CellCaptureBounds[] {COVERAGE},
				1
		);
		recordBlock(
				trace, port, 2L, 1L, threadId,
				BLOCK_APPLY, 1, 1, 1
		);
		BoundedAcousticWorldEventTrace.Entry[] entries =
				entries(trace.size());
		trace.copyChronological(entries);

		BoundedAcousticWorldEventTrace disabled =
				new BoundedAcousticWorldEventTrace(1, false);
		disabled.record(
				0L, -1L, 0L, null, null,
				1, 1, 1, 0, 0, 0
		);
		return new Replay(trace, entries, disabled, threadId);
	}

	private static void recordWorld(
			BoundedAcousticWorldEventTrace trace,
			long epoch,
			long threadId
	) {
		trace.record(
				epoch,
				0L,
				threadId,
				BoundedAcousticWorldEventTrace.Kind.WORLD_REPLACE,
				BoundedAcousticWorldEventTrace.Disposition.WORLD_REPLACED,
				0, 0, 0, 0, 0, 0
		);
	}

	private static void recordBlock(
			BoundedAcousticWorldEventTrace trace,
			CanonicalAcousticWorldEventPort port,
			long epoch,
			long sequence,
			long threadId,
			CanonicalAcousticWorldEventPort.EventType type,
			int x,
			int y,
			int z
	) {
		CanonicalAcousticWorldEventPort.Outcome outcome =
				port.acceptBlock(epoch, sequence, type, x, y, z);
		trace.record(
				epoch,
				sequence,
				threadId,
				kind(type),
				disposition(outcome),
				x, y, z, x, y, z
		);
	}

	private static void recordChunk(
			BoundedAcousticWorldEventTrace trace,
			CanonicalAcousticWorldEventPort port,
			long epoch,
			long sequence,
			long threadId,
			CanonicalAcousticWorldEventPort.EventType type,
			int minimumX,
			int minimumZ,
			int maximumX,
			int maximumZ
	) {
		CanonicalAcousticWorldEventPort.Outcome outcome = port.acceptChunk(
				epoch,
				sequence,
				type,
				minimumX,
				minimumZ,
				maximumX,
				maximumZ
		);
		trace.record(
				epoch,
				sequence,
				threadId,
				kind(type),
				disposition(outcome),
				minimumX, 0, minimumZ, maximumX, 0, maximumZ
		);
	}

	private static long[] allocationWindows() {
		BoundedAcousticWorldEventTrace trace =
				new BoundedAcousticWorldEventTrace(8, true);
		long threadId = Thread.currentThread().threadId();
		for (int index = 0; index < 10_000; index++) {
			trace.record(
					1L, index + 1L, threadId,
					BoundedAcousticWorldEventTrace.Kind.BLOCK_APPLY,
					BoundedAcousticWorldEventTrace.Disposition
							.ACCEPTED_DIRTY,
					1, 1, 1, 1, 1, 1
			);
		}
		ThreadMXBean bean = allocationBean();
		bean.getThreadAllocatedBytes(threadId);
		long sequence = 10_001L;
		long primingBefore = bean.getThreadAllocatedBytes(threadId);
		for (int index = 0; index < 100_000; index++) {
			trace.record(
					1L, sequence++, threadId,
					BoundedAcousticWorldEventTrace.Kind.BLOCK_APPLY,
					BoundedAcousticWorldEventTrace.Disposition
							.ACCEPTED_DIRTY,
					1, 1, 1, 1, 1, 1
			);
		}
		bean.getThreadAllocatedBytes(threadId);
		if (primingBefore < 0L) {
			throw new IllegalStateException(
					"allocation counter returned a negative value"
			);
		}
		long[] windows = new long[5];
		for (int window = -1; window < windows.length; window++) {
			long before = bean.getThreadAllocatedBytes(threadId);
			for (int index = 0; index < 100_000; index++) {
				trace.record(
						1L, sequence++, threadId,
						BoundedAcousticWorldEventTrace.Kind.BLOCK_APPLY,
						BoundedAcousticWorldEventTrace.Disposition
								.ACCEPTED_DIRTY,
						1, 1, 1, 1, 1, 1
				);
			}
			long allocated =
					bean.getThreadAllocatedBytes(threadId) - before;
			if (window >= 0) {
				windows[window] = allocated;
			}
		}
		return windows;
	}

	private static ThreadMXBean allocationBean() {
		java.lang.management.ThreadMXBean base =
				ManagementFactory.getThreadMXBean();
		if (!(base instanceof ThreadMXBean bean)
				|| !bean.isThreadAllocatedMemorySupported()) {
			throw new IllegalStateException("allocation counter unavailable");
		}
		if (!bean.isThreadAllocatedMemoryEnabled()) {
			bean.setThreadAllocatedMemoryEnabled(true);
		}
		return bean;
	}

	private static BoundedAcousticWorldEventTrace.Kind kind(
			CanonicalAcousticWorldEventPort.EventType type
	) {
		return BoundedAcousticWorldEventTrace.Kind.valueOf(type.name());
	}

	private static BoundedAcousticWorldEventTrace.Disposition disposition(
			CanonicalAcousticWorldEventPort.Outcome outcome
	) {
		return BoundedAcousticWorldEventTrace.Disposition.valueOf(
				outcome.name()
		);
	}

	private static BoundedAcousticWorldEventTrace.Entry[] entries(
			int count
	) {
		BoundedAcousticWorldEventTrace.Entry[] output =
				new BoundedAcousticWorldEventTrace.Entry[count];
		for (int index = 0; index < count; index++) {
			output[index] = new BoundedAcousticWorldEventTrace.Entry();
		}
		return output;
	}

	private static String entriesJson(
			BoundedAcousticWorldEventTrace.Entry[] entries
	) {
		StringBuilder output = new StringBuilder("[");
		for (int index = 0; index < entries.length; index++) {
			if (index > 0) {
				output.append(',');
			}
			BoundedAcousticWorldEventTrace.Entry entry = entries[index];
			output.append(String.format(
					Locale.ROOT,
					"{\"ordinal\":%d,\"active_world_epoch\":%d,"
							+ "\"local_sequence\":%d,"
							+ "\"thread_id\":%d,\"kind\":\"%s\","
							+ "\"disposition\":\"%s\","
							+ "\"bounds\":[%d,%d,%d,%d,%d,%d]}",
					entry.ordinal(),
					entry.worldEpoch(),
					entry.sequence(),
					entry.threadId(),
					entry.kind(),
					entry.disposition(),
					entry.minimumX(),
					entry.minimumY(),
					entry.minimumZ(),
					entry.maximumX(),
					entry.maximumY(),
					entry.maximumZ()
			));
		}
		return output.append(']').toString();
	}

	private static String longs(long[] values) {
		StringBuilder output = new StringBuilder("[");
		for (int index = 0; index < values.length; index++) {
			if (index > 0) {
				output.append(',');
			}
			output.append(values[index]);
		}
		return output.append(']').toString();
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(bytes)
			);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private record Replay(
			BoundedAcousticWorldEventTrace trace,
			BoundedAcousticWorldEventTrace.Entry[] entries,
			BoundedAcousticWorldEventTrace disabled,
			long threadId
	) {
	}
}
