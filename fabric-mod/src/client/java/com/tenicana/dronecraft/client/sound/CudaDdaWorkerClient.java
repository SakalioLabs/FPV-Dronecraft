package com.tenicana.dronecraft.client.sound;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Single-flight, fail-closed client for the out-of-process CUDA DDA worker.
 * A failed request never waits for the worker again and tells the caller to
 * use the Java CPU solver.
 */
public final class CudaDdaWorkerClient implements AutoCloseable {
	public static final int MAGIC = 0x5746434d;
	public static final short VERSION = 1;
	public static final short OPCODE_INITIALIZE = 1;
	public static final short OPCODE_SUBMIT = 2;
	public static final short OPCODE_PING = 3;
	public static final short OPCODE_SHUTDOWN = 4;
	public static final int MAXIMUM_PAYLOAD = 128 * 1024 * 1024;
	private static final int HEADER_BYTES = 48;
	private static final long FNV_OFFSET = 0xcbf29ce484222325L;
	private static final long FNV_PRIME = 0x100000001b3L;
	private static final long MAXIMUM_BACKOFF_NANOS =
		Duration.ofSeconds(30).toNanos();

	public enum Failure {
		NONE,
		BACKOFF,
		TIMEOUT,
		PROCESS_EXIT,
		PROTOCOL,
		WORKER_ERROR,
		IO
	}

	public record Attempt(
		int status,
		byte[] payload,
		Failure failure,
		boolean useCpuFallback
	) {
		static Attempt reply(int status, byte[] payload) {
			return new Attempt(status, payload, Failure.NONE, false);
		}

		static Attempt fallback(Failure failure) {
			return new Attempt(0, new byte[0], failure, true);
		}
	}

	private final Path executable;
	private final List<String> arguments;
	private final Duration deadline;
	private Process process;
	private ExecutorService reader;
	private long nextRequestId = 1L;
	private long nextGeneration = 1L;
	private long processGeneration;
	private long retryAfterNanos;
	private int consecutiveFailures;
	private boolean closed;
	private Process terminatingProcess;

	public CudaDdaWorkerClient(
			Path executable,
			List<String> arguments,
			Duration deadline
	) {
		this.executable = Objects.requireNonNull(executable)
			.toAbsolutePath();
		this.arguments = List.copyOf(arguments);
		this.deadline = Objects.requireNonNull(deadline);
		if (deadline.isZero() || deadline.isNegative()) {
			throw new IllegalArgumentException("deadline must be positive");
		}
	}

	public synchronized Attempt request(short opcode, byte[] payload) {
		Objects.requireNonNull(payload);
		if (closed) {
			return Attempt.fallback(Failure.BACKOFF);
		}
		if (payload.length > MAXIMUM_PAYLOAD) {
			return fail(Failure.PROTOCOL);
		}
		long now = System.nanoTime();
		if (now < retryAfterNanos) {
			return Attempt.fallback(Failure.BACKOFF);
		}
		try {
			startIfNeeded();
			long requestId = nextRequestId++;
			int deadlineMillis = (int) Math.min(
				Integer.MAX_VALUE,
				Math.max(1L, deadline.toMillis())
			);
			writeFrame(
				process.getOutputStream(),
				opcode,
				processGeneration,
				requestId,
				deadlineMillis,
				payload
			);
			Future<Frame> pending = reader.submit(
				() -> readFrame(process.getInputStream())
			);
			Frame frame;
			try {
				frame = pending.get(
					deadline.toNanos(),
					TimeUnit.NANOSECONDS
				);
			} catch (TimeoutException error) {
				pending.cancel(true);
				return fail(Failure.TIMEOUT);
			} catch (InterruptedException error) {
				Thread.currentThread().interrupt();
				return fail(Failure.IO);
			} catch (ExecutionException error) {
				return fail(
					error.getCause() instanceof EOFException
						|| !process.isAlive()
							? Failure.PROCESS_EXIT
							: Failure.IO
				);
			}
			if (frame.magic != MAGIC
					|| frame.version != VERSION
					|| frame.opcode != opcode
					|| frame.deadlineMillis != deadlineMillis
					|| frame.generation != processGeneration
					|| frame.requestId != requestId
					|| frame.payload.length > MAXIMUM_PAYLOAD
					|| frame.payloadChecksum
						!= checksum(frame.payload)) {
				return fail(Failure.PROTOCOL);
			}
			if (frame.status != 0) {
				return fail(Failure.WORKER_ERROR);
			}
			consecutiveFailures = 0;
			retryAfterNanos = 0L;
			return Attempt.reply(frame.status, frame.payload);
		} catch (IOException error) {
			return fail(
				process != null && !process.isAlive()
					? Failure.PROCESS_EXIT
					: Failure.IO
			);
		}
	}

	public synchronized boolean processAlive() {
		return process != null && process.isAlive()
			|| terminatingProcess != null && terminatingProcess.isAlive();
	}

	private void startIfNeeded() throws IOException {
		if (process != null && process.isAlive()) {
			return;
		}
		if (terminatingProcess != null && terminatingProcess.isAlive()) {
			throw new IOException("previous CUDA worker is still terminating");
		}
		List<String> command = new ArrayList<>();
		command.add(executable.toString());
		command.addAll(arguments);
		process = new ProcessBuilder(command)
			.redirectError(ProcessBuilder.Redirect.INHERIT)
			.start();
		processGeneration = nextGeneration++;
		reader = Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, "mcfpv-cuda-worker-reader");
			thread.setDaemon(true);
			return thread;
		});
	}

	private Attempt fail(Failure failure) {
		stopProcess();
		consecutiveFailures = Math.min(consecutiveFailures + 1, 30);
		long shift = Math.min(consecutiveFailures - 1, 5);
		long backoff = Math.min(
			Duration.ofSeconds(1L << shift).toNanos(),
			MAXIMUM_BACKOFF_NANOS
		);
		retryAfterNanos = System.nanoTime() + backoff;
		return Attempt.fallback(failure);
	}

	private void stopProcess() {
		if (process != null) {
			Process doomed = process;
			process = null;
			terminatingProcess = doomed;
			doomed.destroyForcibly();
			doomed.onExit().thenRun(() -> {
				closeQuietly(doomed.getOutputStream());
				closeQuietly(doomed.getInputStream());
			});
		}
		if (reader != null) {
			reader.shutdownNow();
			reader = null;
		}
	}

	@Override
	public synchronized void close() {
		closed = true;
		stopProcess();
		if (terminatingProcess != null && terminatingProcess.isAlive()) {
			terminatingProcess.destroyForcibly();
		}
	}

	private static void writeFrame(
			OutputStream output,
			short opcode,
			long generation,
			long requestId,
			int deadlineMillis,
			byte[] payload
		) throws IOException {
		ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES)
			.order(ByteOrder.LITTLE_ENDIAN);
		header.putInt(MAGIC);
		header.putShort(VERSION);
		header.putShort(opcode);
		header.putInt(payload.length);
		header.putInt(0);
		header.putInt(deadlineMillis);
		header.putInt(0);
		header.putLong(generation);
		header.putLong(requestId);
		header.putLong(checksum(payload));
		output.write(header.array());
		output.write(payload);
		output.flush();
	}

	private static Frame readFrame(InputStream input) throws IOException {
		byte[] headerBytes = readExact(input, HEADER_BYTES);
		ByteBuffer header = ByteBuffer.wrap(headerBytes)
			.order(ByteOrder.LITTLE_ENDIAN);
		int magic = header.getInt();
		short version = header.getShort();
		short opcode = header.getShort();
		int payloadBytes = header.getInt();
		int status = header.getInt();
		int deadlineMillis = header.getInt();
		int reserved = header.getInt();
		long generation = header.getLong();
		long requestId = header.getLong();
		long payloadChecksum = header.getLong();
		if (payloadBytes < 0 || payloadBytes > MAXIMUM_PAYLOAD) {
			throw new IOException("invalid worker payload length");
		}
		if (deadlineMillis < 1 || reserved != 0) {
			throw new IOException("invalid worker response header");
		}
		return new Frame(
			magic,
			version,
			opcode,
			status,
			deadlineMillis,
			generation,
			requestId,
			payloadChecksum,
			readExact(input, payloadBytes)
		);
	}

	private static long checksum(byte[] payload) {
		long result = FNV_OFFSET;
		for (byte value : payload) {
			result ^= Byte.toUnsignedLong(value);
			result *= FNV_PRIME;
		}
		return result;
	}

	private static byte[] readExact(InputStream input, int bytes)
			throws IOException {
		byte[] result = new byte[bytes];
		int offset = 0;
		while (offset < result.length) {
			int read = input.read(result, offset, result.length - offset);
			if (read < 0) {
				throw new EOFException("CUDA worker closed its output");
			}
			offset += read;
		}
		return result;
	}

	private static void closeQuietly(AutoCloseable closeable) {
		try {
			closeable.close();
		} catch (Exception ignored) {
			// The worker is already considered unusable.
		}
	}

	private record Frame(
		int magic,
		short version,
		short opcode,
		int status,
		int deadlineMillis,
		long generation,
		long requestId,
		long payloadChecksum,
		byte[] payload
	) {
	}
}
