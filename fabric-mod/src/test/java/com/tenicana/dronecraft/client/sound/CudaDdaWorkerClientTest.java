package com.tenicana.dronecraft.client.sound;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CudaDdaWorkerClientTest {
	@Test
	void rejectsDeadlinesThatCannotFitTheWireHeader() {
		Path missingWorker = Path.of("missing-worker");

		assertThrows(
			IllegalArgumentException.class,
			() -> new CudaDdaWorkerClient(
				missingWorker,
				List.of(),
				Duration.ZERO
			)
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> new CudaDdaWorkerClient(
				missingWorker,
				List.of(),
				Duration.ofMillis((long) Integer.MAX_VALUE + 1L)
			)
		);
	}

	@Test
	void invalidPerRequestDeadlineFailsClosedWithoutStartingWorker() {
		try (CudaDdaWorkerClient client = new CudaDdaWorkerClient(
				Path.of("missing-worker"),
				List.of(),
				Duration.ofSeconds(1)
			)) {
			CudaDdaWorkerClient.Attempt attempt = client.request(
				CudaDdaWorkerClient.OPCODE_PING,
				new byte[0],
				Duration.ZERO
			);

			assertEquals(
				CudaDdaWorkerClient.Failure.PROTOCOL,
				attempt.failure()
			);
			assertTrue(attempt.useCpuFallback());
			assertEquals(0, attempt.payload().length);
		}
	}
}
