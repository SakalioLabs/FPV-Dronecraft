package com.tenicana.dronecraft.acoustics.voxel;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.propagation.SparseMaterialSnapshot;
import com.tenicana.dronecraft.acoustics.tools.DdaProductionCorpusCli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

class DdaProductionCorpusCliTest {
	private static final int SAMPLE_RAYS = 512;

	@Test
	void corpusIsDeterministic() {
		DdaProductionSnapshotBundle.Bundle first =
				DdaProductionCorpusCli.corpus(SAMPLE_RAYS);
		DdaProductionSnapshotBundle.Bundle second =
				DdaProductionCorpusCli.corpus(SAMPLE_RAYS);
		assertEquals(
				first.snapshot().diagnosticSha256(),
				second.snapshot().diagnosticSha256()
		);
		assertEquals(first.rays(), second.rays());
		assertEquals(SAMPLE_RAYS, first.rays().size());
	}

	@Test
	void writtenCorpusRoundTripsThroughTheStrictReader(@TempDir Path directory)
			throws IOException {
		Path path = directory.resolve("corpus.bin");
		DdaProductionSnapshotBundle.Bundle written =
				DdaProductionCorpusCli.corpus(SAMPLE_RAYS);
		DdaProductionSnapshotBundle.writeAtomic(path, written);
		DdaProductionSnapshotBundle.Bundle read =
				DdaProductionSnapshotBundle.read(path);
		assertEquals(written.rays(), read.rays());
		assertEquals(
				written.snapshot().diagnosticSha256(),
				read.snapshot().diagnosticSha256()
		);
		assertTrue(read.snapshot().complete());
	}

	@Test
	void raysFanOutFromTheDeclaredSources() {
		DdaProductionSnapshotBundle.Bundle bundle =
				DdaProductionCorpusCli.corpus(SAMPLE_RAYS);
		Set<AcousticVector> sources =
				new HashSet<>(DdaProductionCorpusCli.sources());
		Set<AcousticVector> endpoints = new HashSet<>();
		for (int index = DdaProductionCorpusCli.ADVERSARIAL_RAY_COUNT;
				index < bundle.rays().size();
				index++) {
			DdaProductionSnapshotBundle.Ray ray = bundle.rays().get(index);
			assertTrue(
					sources.contains(ray.start()),
					"fan ray must start at a declared source"
			);
			assertTrue(endpoints.add(ray.end()), "fan endpoints are unique");
		}
	}

	@Test
	void truncationBudgetsAreMixed() {
		DdaProductionSnapshotBundle.Bundle bundle =
				DdaProductionCorpusCli.corpus(SAMPLE_RAYS);
		Set<Integer> budgets = new HashSet<>();
		for (DdaProductionSnapshotBundle.Ray ray : bundle.rays()) {
			budgets.add(ray.maximumCells());
		}
		assertTrue(
				budgets.contains(DdaProductionCorpusCli.DEFAULT_MAXIMUM_CELLS),
				"corpus must contain full-budget rays"
		);
		assertTrue(
				budgets.contains(
						DdaProductionCorpusCli.TRUNCATING_MAXIMUM_CELLS
				),
				"corpus must contain truncating rays"
		);
	}

	@Test
	void sceneUsesEveryDiagnosticMaterial() {
		SparseMaterialSnapshot scene = DdaProductionCorpusCli.scene();
		assertTrue(scene.complete(), "scene must be complete");
		Set<String> materials = new HashSet<>();
		Set<Double> fills = new HashSet<>();
		for (SparseMaterialSnapshot.CellSample cell : scene.diagnosticEntries()) {
			materials.add(cell.sample().material().id());
			fills.add(cell.sample().fillFraction());
		}
		assertTrue(materials.contains("stone"), "scene needs stone");
		assertTrue(materials.contains("wood"), "scene needs wood");
		assertTrue(materials.contains("glass"), "scene needs glass");
		assertTrue(materials.contains("metal"), "scene needs metal");
		assertTrue(materials.contains("water"), "scene needs water");
		assertTrue(materials.contains("foliage"), "scene needs foliage");
		assertTrue(materials.contains("soft"), "scene needs soft");
		assertTrue(fills.size() > 1, "scene needs partial fill fractions");
	}

	@Test
	void distinctRayCountsProduceDistinctCorpora() {
		assertNotEquals(
				DdaProductionCorpusCli.corpus(SAMPLE_RAYS).rays(),
				DdaProductionCorpusCli.corpus(SAMPLE_RAYS * 2).rays()
		);
	}

	@Test
	void tooFewRaysAreRejected() {
		assertThrows(
				IllegalArgumentException.class,
				() -> DdaProductionCorpusCli.corpus(
						DdaProductionCorpusCli.ADVERSARIAL_RAY_COUNT - 1
				)
		);
	}

	@Test
	void generatedCorpusFileIsByteIdentical(@TempDir Path directory)
			throws IOException {
		Path first = directory.resolve("first.bin");
		Path second = directory.resolve("second.bin");
		DdaProductionSnapshotBundle.writeAtomic(
				first,
				DdaProductionCorpusCli.corpus(SAMPLE_RAYS)
		);
		DdaProductionSnapshotBundle.writeAtomic(
				second,
				DdaProductionCorpusCli.corpus(SAMPLE_RAYS)
		);
		assertEquals(-1L, Files.mismatch(first, second));
	}
}
