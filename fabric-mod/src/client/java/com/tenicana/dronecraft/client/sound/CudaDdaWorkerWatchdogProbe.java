package com.tenicana.dronecraft.client.sound;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * CUDA-free fault-injection probe for the worker protocol and watchdog.
 */
public final class CudaDdaWorkerWatchdogProbe {
	private CudaDdaWorkerWatchdogProbe() {
	}

	public static void main(String[] arguments) throws Exception {
		if (arguments.length != 3) {
			throw new IllegalArgumentException(
				"usage: <worker.exe> <output.json> <deadline-ms>"
			);
		}
		Path worker = Path.of(arguments[0]).toAbsolutePath();
		Path output = Path.of(arguments[1]).toAbsolutePath();
		long deadlineMillis = Long.parseLong(arguments[2]);
		if (!Files.isRegularFile(worker) || deadlineMillis < 1L) {
			throw new IllegalArgumentException("invalid probe arguments");
		}
		Duration deadline = Duration.ofMillis(deadlineMillis);

		CaseResult ping = probe(
			worker,
			"--mode=protocol-only",
			deadline,
			CudaDdaWorkerClient.OPCODE_PING,
			CudaDdaWorkerClient.Failure.NONE
		);
		CaseResult workerError = probe(
			worker,
			"--mode=protocol-only",
			deadline,
			CudaDdaWorkerClient.OPCODE_INITIALIZE,
			CudaDdaWorkerClient.Failure.WORKER_ERROR
		);
		CaseResult mismatch = probe(
			worker,
			"--mode=mismatch",
			deadline,
			CudaDdaWorkerClient.OPCODE_PING,
			CudaDdaWorkerClient.Failure.PROTOCOL
		);
		CaseResult crash = probe(
			worker,
			"--mode=crash",
			deadline,
			CudaDdaWorkerClient.OPCODE_PING,
			CudaDdaWorkerClient.Failure.PROCESS_EXIT
		);
		CaseResult hang = probe(
			worker,
			"--mode=hang",
			deadline,
			CudaDdaWorkerClient.OPCODE_PING,
			CudaDdaWorkerClient.Failure.TIMEOUT
		);
		writeAtomic(
			output,
			json(
				worker,
				deadlineMillis,
				ping,
				workerError,
				mismatch,
				crash,
				hang
			)
		);
	}

	private static CaseResult probe(
			Path worker,
			String mode,
			Duration deadline,
			short opcode,
			CudaDdaWorkerClient.Failure expected
		) {
		long start = System.nanoTime();
		CudaDdaWorkerClient.Attempt first;
		CudaDdaWorkerClient.Attempt second = null;
		boolean aliveAfter;
		double requestMillis;
		try (CudaDdaWorkerClient client = new CudaDdaWorkerClient(
				worker,
				List.of(mode),
				deadline
			)) {
			first = client.request(
				opcode,
				new byte[0]
			);
			requestMillis = elapsedMillis(start);
			if (expected == CudaDdaWorkerClient.Failure.NONE) {
				if (first.failure() != expected
						|| first.status() != 0
						|| first.useCpuFallback()) {
					throw new IllegalStateException(mode + " ping failed");
				}
			} else {
				if (first.failure() != expected
						|| !first.useCpuFallback()) {
					throw new IllegalStateException(
						mode + " produced " + first.failure()
					);
				}
				second = client.request(
					CudaDdaWorkerClient.OPCODE_PING,
					new byte[0]
				);
				if (second.failure()
						!= CudaDdaWorkerClient.Failure.BACKOFF
						|| !second.useCpuFallback()) {
					throw new IllegalStateException(
						mode + " did not enter fail-fast backoff"
					);
				}
			}
			long terminationDeadline = System.nanoTime()
				+ Duration.ofSeconds(5).toNanos();
			while (expected != CudaDdaWorkerClient.Failure.NONE
					&& client.processAlive()
					&& System.nanoTime() < terminationDeadline) {
				try {
					Thread.sleep(25L);
				} catch (InterruptedException error) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(
						"probe interrupted",
						error
					);
				}
			}
			aliveAfter = client.processAlive();
		}
		if (expected != CudaDdaWorkerClient.Failure.NONE && aliveAfter) {
			throw new IllegalStateException(mode + " process survived failure");
		}
		return new CaseResult(
			mode.substring("--mode=".length()),
			first.failure().name(),
			first.useCpuFallback(),
			second == null ? "not_applicable" : second.failure().name(),
			aliveAfter,
			requestMillis,
			elapsedMillis(start)
		);
	}

	private static double elapsedMillis(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000.0;
	}

	private static String json(
			Path worker,
			long deadlineMillis,
			CaseResult... results
		) {
		StringBuilder builder = new StringBuilder()
			.append("{\n")
			.append("  \"status\": \"valid\",\n")
			.append("  \"schema\": 1,\n")
			.append("  \"cuda_executed\": false,\n")
			.append("  \"minecraft_started\": false,\n")
			.append("  \"physical_endpoint_opened\": false,\n")
			.append("  \"worker\": \"")
			.append(escape(worker.toString())).append("\",\n")
			.append("  \"deadline_ms\": ").append(deadlineMillis)
			.append(",\n")
			.append("  \"cases\": [\n");
		for (int index = 0; index < results.length; ++index) {
			if (index > 0) {
				builder.append(",\n");
			}
			builder.append(results[index].json());
		}
		return builder.append("\n  ],\n")
			.append("  \"cpu_fallback_contract_verified\": true,\n")
			.append("  \"claim_boundary\": \"CUDA-free protocol and ")
			.append("fault-injection evidence; not GPU timing or ")
			.append("Minecraft integration evidence.\"\n")
			.append("}\n")
			.toString();
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private static void writeAtomic(Path output, String payload)
			throws IOException {
		Files.createDirectories(output.getParent());
		Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
		Files.writeString(temporary, payload, StandardCharsets.UTF_8);
		Files.move(
			temporary,
			output,
			StandardCopyOption.REPLACE_EXISTING,
			StandardCopyOption.ATOMIC_MOVE
		);
	}

	private record CaseResult(
		String mode,
		String firstFailure,
		boolean cpuFallback,
		String immediateRetry,
		boolean processAliveAfter,
		double requestMillis,
		double elapsedMillis
	) {
		String json() {
			return String.format(
				Locale.ROOT,
				"    {\n"
					+ "      \"mode\": \"%s\",\n"
					+ "      \"first_failure\": \"%s\",\n"
					+ "      \"cpu_fallback\": %s,\n"
					+ "      \"immediate_retry\": \"%s\",\n"
					+ "      \"process_alive_after\": %s,\n"
					+ "      \"request_elapsed_ms\": %.6f,\n"
					+ "      \"elapsed_ms\": %.6f\n"
					+ "    }",
				mode,
				firstFailure,
				cpuFallback,
				immediateRetry,
				processAliveAfter,
				requestMillis,
				elapsedMillis
			);
		}
	}
}
