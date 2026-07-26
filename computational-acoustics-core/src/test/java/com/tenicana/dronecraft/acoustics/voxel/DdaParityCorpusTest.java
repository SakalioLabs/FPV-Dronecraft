package com.tenicana.dronecraft.acoustics.voxel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DdaParityCorpusTest {
	private static final long RAY_SEED = 0x123456789ABCDEFL;
	private static final long MATERIAL_SEED = 0x0FEDCBA987654321L;

	@Test
	void generationIsDeterministicAndIncludesAdversarialRays() {
		DdaParityCorpus.Corpus first = DdaParityCorpus.generate(
				32,
				RAY_SEED,
				MATERIAL_SEED
		);
		DdaParityCorpus.Corpus second = DdaParityCorpus.generate(
				32,
				RAY_SEED,
				MATERIAL_SEED
		);

		assertEquals(first, second);
		assertEquals(40, first.rays().size());
		assertEquals(1, first.rays().getFirst().expected().segments().size());
		assertTrue(first.rays().get(6).expected().truncated());
		assertEquals(
				Math.pow(
						10.0,
						-first.rays().get(1).expectedBands().midLossDb()
								/ 10.0
				),
				first.rays().get(1).expectedBands().midEnergyGain()
		);
		assertEquals(
				java.util.List.of(
						new VoxelDda.Cell(0, 0, 0),
						new VoxelDda.Cell(1, 1, 1),
						new VoxelDda.Cell(2, 2, 2)
				),
				first.rays().get(3).expected().segments().stream()
						.limit(3)
						.map(DdaParityOracle.Segment::cell)
						.toList()
		);
	}

	@Test
	void binaryRoundTripPreservesAndReverifiesCorpus(
			@TempDir Path temporaryDirectory
	) throws IOException {
		DdaParityCorpus.Corpus expected = DdaParityCorpus.generate(
				64,
				RAY_SEED,
				MATERIAL_SEED
		);
		Path path = temporaryDirectory.resolve("corpus.bin");

		DdaParityCorpus.write(path, expected);
		DdaParityCorpus.Corpus actual = DdaParityCorpus.read(path);

		assertEquals(expected, actual);
		DdaParityCorpus.verify(actual);
		assertTrue(Files.size(path) > 0L);
	}

	@Test
	void streamingWriterMatchesInMemorySchemaAndVerifier(
			@TempDir Path temporaryDirectory
	) throws IOException {
		DdaParityCorpus.Corpus corpus = DdaParityCorpus.generate(
				64,
				RAY_SEED,
				MATERIAL_SEED
		);
		Path inMemoryPath = temporaryDirectory.resolve("in-memory.bin");
		Path streamingPath = temporaryDirectory.resolve("streaming.bin");

		DdaParityCorpus.write(inMemoryPath, corpus);
		DdaParityCorpus.writeGenerated(
				streamingPath,
				64,
				RAY_SEED,
				MATERIAL_SEED
		);
		DdaParityCorpus.StreamingSummary summary =
				DdaParityCorpus.verifyStreaming(streamingPath);

		assertEquals(
				-1L,
				Files.mismatch(inMemoryPath, streamingPath)
		);
		assertEquals(corpus.rays().size(), summary.rays());
		assertEquals(
				corpus.rays().stream()
						.mapToLong(
								ray -> ray.expected().segments().size()
						)
						.sum(),
				summary.segments()
		);
		assertEquals(
				DdaParityCorpus.materialTableSha256(),
				summary.materialTableSha256()
		);
	}

	@Test
	void readerRejectsCorruptedMagic(@TempDir Path temporaryDirectory)
			throws IOException {
		Path path = temporaryDirectory.resolve("corrupt.bin");
		Files.write(path, new byte[64]);

		IOException error = assertThrows(
				IOException.class,
				() -> DdaParityCorpus.read(path)
		);
		assertTrue(error.getMessage().contains("magic"));
	}

	@Test
	void materialFieldIsDeterministicAcrossSignedCoordinates() {
		assertEquals(
				DdaParityCorpus.materialIdAt(-17, 23, -41, MATERIAL_SEED),
				DdaParityCorpus.materialIdAt(-17, 23, -41, MATERIAL_SEED)
		);
		assertTrue(
				DdaParityCorpus.materialIdAt(-17, 23, -41, MATERIAL_SEED)
						>= DdaParityOracle.AIR_MATERIAL_ID
		);
		assertEquals(64, DdaParityCorpus.materialTableSha256().length());
	}
}
