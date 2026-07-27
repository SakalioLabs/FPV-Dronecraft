package com.tenicana.dronecraft.client.sound;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CudaDdaNativeBoundaryBenchmarkContractTest {
	@Test
	void javaProbeUsesExistingLwjglDirectBufferBoundary()
			throws IOException {
		String source = Files.readString(
			locate(
				"src/client/java/com/tenicana/dronecraft/client/sound/"
					+ "CudaDdaNativeBoundaryBenchmark.java",
				"fabric-mod/src/client/java/com/tenicana/dronecraft/"
					+ "client/sound/CudaDdaNativeBoundaryBenchmark.java"
			),
			StandardCharsets.UTF_8
		);

		assertTrue(source.contains("RAY_BYTES = 64"));
		assertTrue(source.contains("RESULT_BYTES = 72"));
		assertTrue(source.contains("BufferUtils.createByteBuffer("));
		assertTrue(source.contains("MemoryUtil.memAddress(rays)"));
		assertTrue(source.contains("JNI.invokePPI("));
		assertTrue(source.contains("\\\"cuda_executed\\\": false"));
		assertTrue(source.contains("\\\"minecraft_started\\\": false"));
		assertTrue(source.contains(
			"\\\"physical_endpoint_opened\\\": false"
		));
	}

	@Test
	void nativeProbeFreezesAbiWithoutCudaDependency() throws IOException {
		String source = Files.readString(
			locate(
				"../native/cuda-dda-boundary/src/dda_boundary.cpp",
				"native/cuda-dda-boundary/src/dda_boundary.cpp"
			),
			StandardCharsets.UTF_8
		);

		assertTrue(source.contains("static_assert(sizeof(Ray) == 64)"));
		assertTrue(source.contains("static_assert(sizeof(Result) == 72)"));
		assertTrue(source.contains(
			"offsetof(Ray, segment_offset) == 56"
		));
		assertTrue(source.contains("offsetof(Result, gain) == 48"));
		assertTrue(source.contains("mcfpv_dda_boundary_aggregate("));
		assertFalse(source.contains("cuda.h"));
		assertFalse(source.contains("cuLaunchKernel"));
	}

	@Test
	void cudaBridgeIsPersistentPairedAndFailClosed() throws IOException {
		String javaSource = Files.readString(
			locate(
				"src/client/java/com/tenicana/dronecraft/client/sound/"
					+ "CudaDdaNativeBridgeBenchmark.java",
				"fabric-mod/src/client/java/com/tenicana/dronecraft/"
					+ "client/sound/CudaDdaNativeBridgeBenchmark.java"
			),
			StandardCharsets.UTF_8
		);
		String nativeSource = Files.readString(
			locate(
				"../native/cuda-dda-boundary/src/cuda_bridge.cpp",
				"native/cuda-dda-boundary/src/cuda_bridge.cpp"
			),
			StandardCharsets.UTF_8
		);

		assertTrue(javaSource.contains("probeDriver(Duration.ofSeconds("));
		assertTrue(javaSource.contains("verifyParity("));
		assertTrue(javaSource.contains("cpuBatchChecksum("));
		assertTrue(javaSource.contains("index % 2 == 0"));
		assertTrue(javaSource.contains("\\\"resident_cells\\\": true"));
		assertTrue(javaSource.contains("\\\"cuda_executed\\\": true"));
		assertTrue(nativeSource.contains("LoadLibraryW(L\"nvcuda.dll\")"));
		assertTrue(nativeSource.contains("trace_aggregate_kernel"));
		assertTrue(nativeSource.contains("cuCtxSetCurrent"));
		assertTrue(nativeSource.contains("cuMemcpyHtoD_v2"));
		assertTrue(nativeSource.contains("cuLaunchKernel"));
		assertTrue(nativeSource.contains("cuMemcpyDtoH_v2"));
		assertFalse(nativeSource.contains("#include <cuda.h>"));
	}

	private static Path locate(String local, String root) {
		Path localPath = Path.of(local);
		return Files.isRegularFile(localPath) ? localPath : Path.of(root);
	}
}
