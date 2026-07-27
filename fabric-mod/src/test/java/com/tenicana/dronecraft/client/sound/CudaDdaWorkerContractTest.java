package com.tenicana.dronecraft.client.sound;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CudaDdaWorkerContractTest {
	@Test
	void wireHeaderCarriesBoundedFreshnessAndIntegrityFields()
			throws IOException {
		String protocol = read(
			"../native/cuda-dda-boundary/include/"
				+ "mcfpv_cuda_worker_protocol.h",
			"native/cuda-dda-boundary/include/"
				+ "mcfpv_cuda_worker_protocol.h"
		);

		assertTrue(protocol.contains(
			"MCFPV_WORKER_MAXIMUM_PAYLOAD"
		));
		assertTrue(protocol.contains("deadline_millis"));
		assertTrue(protocol.contains("generation"));
		assertTrue(protocol.contains("request_id"));
		assertTrue(protocol.contains("payload_checksum"));
		assertTrue(protocol.contains(
			"sizeof(McfpvWorkerFrameHeader) == 48U"
		));
	}

	@Test
	void watchdogFailsClosedWithoutWaitingForCudaAgain()
			throws IOException {
		String client = read(
			"src/client/java/com/tenicana/dronecraft/client/sound/"
				+ "CudaDdaWorkerClient.java",
			"fabric-mod/src/client/java/com/tenicana/dronecraft/client/"
				+ "sound/CudaDdaWorkerClient.java"
		);

		assertTrue(client.contains("pending.get("));
		assertTrue(client.contains("Duration requestDeadline"));
		assertTrue(client.contains("Failure.TIMEOUT"));
		assertTrue(client.contains("Failure.PROCESS_EXIT"));
		assertTrue(client.contains("Failure.PROTOCOL"));
		assertTrue(client.contains("Failure.WORKER_ERROR"));
		assertTrue(client.contains("Attempt.fallback(Failure.BACKOFF)"));
		assertTrue(client.contains("destroyForcibly()"));
		assertTrue(client.contains("doomed.onExit()"));
		assertTrue(client.contains("frame.generation != processGeneration"));
		assertTrue(client.contains(
			"frame.payloadChecksum"
		));
		assertFalse(client.contains("org.lwjgl"));
		assertFalse(client.contains("nvcuda"));
	}

	@Test
	void ipcBenchmarkIncludesParityAndCompleteBoundaryTiming()
			throws IOException {
		String benchmark = read(
			"src/client/java/com/tenicana/dronecraft/client/sound/"
				+ "CudaDdaWorkerBenchmark.java",
			"fabric-mod/src/client/java/com/tenicana/dronecraft/client/"
				+ "sound/CudaDdaWorkerBenchmark.java"
		);

		assertTrue(benchmark.contains("probeDriver("));
		assertTrue(benchmark.contains("OPCODE_INITIALIZE"));
		assertTrue(benchmark.contains("OPCODE_SUBMIT"));
		assertTrue(benchmark.contains("verifyParity("));
		assertTrue(benchmark.contains("DirectPathSolver.solve("));
		assertTrue(benchmark.contains("index % 2 == 0"));
		assertTrue(benchmark.contains("ipc_round_trip"));
		assertTrue(benchmark.contains("\"h2d\""));
		assertTrue(benchmark.contains("\"kernel\""));
		assertTrue(benchmark.contains("\"d2h\""));
		assertTrue(benchmark.contains("\\\"cuda_executed\\\": true"));
		assertTrue(benchmark.contains(
			"\\\"minecraft_started\\\": false"
		));
		assertTrue(benchmark.contains(
			"\\\"physical_endpoint_opened\\\": false"
		));
	}

	@Test
	void workerOwnsCudaAndProvidesDeterministicFaultModes()
			throws IOException {
		String worker = read(
			"../native/cuda-dda-boundary/src/cuda_worker.cpp",
			"native/cuda-dda-boundary/src/cuda_worker.cpp"
		);

		assertTrue(worker.contains("--mode=protocol-only"));
		assertTrue(worker.contains("--mode=hang"));
		assertTrue(worker.contains("--mode=crash"));
		assertTrue(worker.contains("--mode=mismatch"));
		assertTrue(worker.contains("mcfpv_cuda_bridge_create"));
		assertTrue(worker.contains("mcfpv_cuda_bridge_submit"));
		assertTrue(worker.contains("payload checksum mismatch"));
		assertFalse(worker.contains("#include <cuda.h>"));
	}

	private static String read(String local, String root) throws IOException {
		Path localPath = Path.of(local);
		return Files.readString(
			Files.isRegularFile(localPath) ? localPath : Path.of(root),
			StandardCharsets.UTF_8
		);
	}
}
