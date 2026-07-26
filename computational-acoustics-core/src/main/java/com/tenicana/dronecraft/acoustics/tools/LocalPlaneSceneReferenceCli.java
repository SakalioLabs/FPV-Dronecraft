package com.tenicana.dronecraft.acoustics.tools;

import com.sun.management.ThreadMXBean;
import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.propagation.AxisAlignedPlanePatch;
import com.tenicana.dronecraft.acoustics.propagation.EarlyLateEnergyLedger;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneReflectionSolver;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneSceneCacheKey;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneSceneSolver;
import com.tenicana.dronecraft.acoustics.propagation.MaterialBoxSegmentBlockQuery;
import com.tenicana.dronecraft.acoustics.propagation.MaterialBoxUnionSurfaceExtractor;
import com.tenicana.dronecraft.acoustics.propagation.MaterialBoxUnionSurfaceExtractor.MaterialBox;
import com.tenicana.dronecraft.acoustics.reverb.LateReverbEstimator;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Offline D121b scene-selection, shape-occlusion and energy-ledger evidence. */
public final class LocalPlaneSceneReferenceCli {
	private static final LocalPlaneReflectionSolver.CellBlockQuery OPEN =
			(x, y, z) -> false;
	private static final double[] SLAB_SOURCE = {0.2, 1.5, 0.4};
	private static final double[] SLAB_LISTENER = {0.8, 1.5, 0.6};
	private static final List<MaterialBox> SLAB_BOXES = List.of(
			new MaterialBox(
					0.0, 0.0, 0.0,
					1.0, 0.5, 1.0,
					AcousticMaterials.STONE
			)
	);
	private static final List<AxisAlignedPlanePatch> SLAB_PATCHES =
			MaterialBoxUnionSurfaceExtractor.extract(SLAB_BOXES);
	private static final MaterialBoxSegmentBlockQuery SLAB_OCCLUSION =
			new MaterialBoxSegmentBlockQuery(SLAB_BOXES);
	private static final List<AxisAlignedPlanePatch> EIGHT_PATCHES = List.of(
			patch(1, 1, 0.0, -3.0, 3.0, -3.0, 3.0),
			patch(1, -1, 4.0, -3.0, 3.0, -3.0, 3.0),
			patch(0, 1, -3.0, 0.0, 4.0, -3.0, 3.0),
			patch(0, -1, 3.0, 0.0, 4.0, -3.0, 3.0),
			patch(2, 1, -3.0, -3.0, 3.0, 0.0, 4.0),
			patch(2, -1, 3.0, -3.0, 3.0, 0.0, 4.0),
			patch(1, 1, -1.0, -3.0, 3.0, -3.0, 3.0),
			patch(1, 1, -2.0, -3.0, 3.0, -3.0, 3.0)
	);
	private static final LateReverbEstimator.Parameters ENVIRONMENT =
			new LateReverbEstimator.Parameters(
					41L,
					0.2,
					3.0,
					0.35,
					new AcousticBands(1.4, 1.1, 0.8),
					new AcousticBands(0.9, 0.7, 0.5),
					new AcousticBands(3.0, 4.0, 5.0),
					new AcousticBands(0.55, 0.45, 0.35)
			);

	private LocalPlaneSceneReferenceCli() {
	}

	public static void main(String[] args) throws IOException {
		Locale.setDefault(Locale.ROOT);
		if (args.length != 2) {
			throw new IllegalArgumentException(
					"usage: <output-json> <D121-scene-contract>"
			);
		}
		Path output = Path.of(args[0]);
		Path contract = Path.of(args[1]);
		LocalPlaneSceneSolver.Workspace slab =
				new LocalPlaneSceneSolver.Workspace();
		solveSlab(slab);
		LocalPlaneReflectionSolver.Workspace self =
				new LocalPlaneReflectionSolver.Workspace();
		AxisAlignedPlanePatch slabTop = findSlabTop();
		solveSingle(slabTop, SLAB_OCCLUSION, self);
		MaterialBoxSegmentBlockQuery obstructed =
				new MaterialBoxSegmentBlockQuery(List.of(
						SLAB_BOXES.getFirst(),
						new MaterialBox(
								0.28, 0.85, 0.40,
								0.42, 1.20, 0.50,
								AcousticMaterials.WOOD
						)
				));
		LocalPlaneReflectionSolver.Workspace obstacle =
				new LocalPlaneReflectionSolver.Workspace();
		solveSingle(slabTop, obstructed, obstacle);
		LocalPlaneSceneSolver.Workspace bounded =
				new LocalPlaneSceneSolver.Workspace();
		solveEight(bounded);
		EarlyLateEnergyLedger.Workspace ledger =
				new EarlyLateEnergyLedger.Workspace();
		EarlyLateEnergyLedger.partition(
				bounded.selectedLowSum(),
				bounded.selectedMidSum(),
				bounded.selectedHighSum(),
				LocalPlaneSceneSolver.MAXIMUM_CANDIDATES,
				ENVIRONMENT,
				ledger
		);
		CacheEvidence cache = cacheEvidence();
		Benchmark benchmark = benchmark(slab, bounded);
		String report = String.format(
				Locale.ROOT,
				"""
				{
				  "schema_version": 1,
				  "status": "valid-minecraft-local-plane-scene-reference",
				  "source_contract_sha256": "%s",
				  "subvoxel_slab": %s,
				  "exact_shape_visibility": {
				    "self_occluded": %s,
				    "obstacle_occluded": %s,
				    "air_side_offset_m": %.17g,
				    "interior_epsilon_m": %.17g
				  },
				  "bounded_scene": %s,
				  "energy_ledger": %s,
				  "cache_invalidation": %s,
				  "benchmark": {
				    "scene_pairs_per_window": %d,
				    "allocation_windows_bytes": %s,
				    "median_allocated_bytes_per_scene": %.17g,
				    "p99_ns_per_scene": %.17g,
				    "checksum": %.17g
				  },
				  "gates": {
				    "subvoxel_shape_not_promoted_to_full_cell": %s,
				    "reflecting_box_does_not_self_occlude": %s,
				    "other_shape_occludes_leg": %s,
				    "six_candidate_bound": %s,
				    "early_late_energy_conserved": %s,
				    "moving_cache_invalidates": %s,
				    "zero_allocation_scene_solve": %s,
				    "p99_below_100_microseconds": %s
				  },
				  "captures_audio": false,
				  "physical_endpoint_opened": false,
				  "cuda_executed": false,
				  "minecraft_client_started": false,
				  "minecraft_integration_enabled": false,
				  "worker_handoff_measured": false,
				  "release_calibrated": false
				}
				""",
				sha256(Files.readAllBytes(contract)),
				sceneJson(SLAB_PATCHES.size(), slab),
				!self.topologyVisible(),
				!obstacle.topologyVisible(),
				LocalPlaneReflectionSolver.AIR_SIDE_OFFSET_METERS,
				MaterialBoxSegmentBlockQuery.INTERIOR_EPSILON_METERS,
				sceneJson(EIGHT_PATCHES.size(), bounded),
				ledgerJson(ledger),
				cacheJson(cache),
				benchmark.scenePairsPerWindow(),
				longArray(benchmark.allocationWindows()),
				benchmark.allocatedBytesPerScene(),
				benchmark.p99NanosPerScene(),
				benchmark.checksum(),
				slab.selectedCount() == 1,
				self.topologyVisible(),
				!obstacle.topologyVisible(),
				bounded.selectedCount()
						== LocalPlaneSceneSolver.MAXIMUM_CANDIDATES,
				ledgerConserved(ledger),
				cache.invalidates(),
				benchmark.allocatedBytesPerScene() == 0.0,
				benchmark.p99NanosPerScene() <= 100_000.0
		);
		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.writeString(output, report, StandardCharsets.UTF_8);
		System.out.printf(
				"{\"status\":\"valid-minecraft-local-plane-scene-reference\","
						+ "\"selected\":%d,\"allocation_bytes\":%.17g,"
						+ "\"p99_ns\":%.17g}%n",
				bounded.selectedCount(),
				benchmark.allocatedBytesPerScene(),
				benchmark.p99NanosPerScene()
		);
	}

	private static AxisAlignedPlanePatch findSlabTop() {
		for (AxisAlignedPlanePatch patch : SLAB_PATCHES) {
			if (patch.axis() == 1
					&& patch.normalSign() == 1
					&& patch.coordinateMeters() == 0.5) {
				return patch;
			}
		}
		throw new IllegalStateException("slab top missing");
	}

	private static void solveSingle(
			AxisAlignedPlanePatch patch,
			MaterialBoxSegmentBlockQuery exact,
			LocalPlaneReflectionSolver.Workspace output
	) {
		LocalPlaneReflectionSolver.solve(
				SLAB_SOURCE[0], SLAB_SOURCE[1], SLAB_SOURCE[2],
				SLAB_LISTENER[0], SLAB_LISTENER[1], SLAB_LISTENER[2],
				patch, OPEN, exact, 64, output
		);
	}

	private static void solveSlab(LocalPlaneSceneSolver.Workspace output) {
		LocalPlaneSceneSolver.solve(
				SLAB_SOURCE[0], SLAB_SOURCE[1], SLAB_SOURCE[2],
				SLAB_LISTENER[0], SLAB_LISTENER[1], SLAB_LISTENER[2],
				SLAB_PATCHES, OPEN, SLAB_OCCLUSION, 64, output
		);
	}

	private static void solveEight(LocalPlaneSceneSolver.Workspace output) {
		LocalPlaneSceneSolver.solve(
				0.0, 2.0, 0.0,
				0.5, 2.0, 0.2,
				EIGHT_PATCHES, OPEN, 64, output
		);
	}

	private static String sceneJson(
			int patchCount,
			LocalPlaneSceneSolver.Workspace scene
	) {
		StringBuilder selected = new StringBuilder("[");
		for (int index = 0; index < scene.selectedCount(); index++) {
			if (index > 0) {
				selected.append(',');
			}
			selected.append(String.format(
					Locale.ROOT,
					"{\"patch_index\":%d,\"reflection_m\":[%.17g,%.17g,%.17g],"
							+ "\"path_length_m\":%.17g,"
							+ "\"energy\":[%.17g,%.17g,%.17g]}",
					scene.patchIndex(index),
					scene.reflectionX(index),
					scene.reflectionY(index),
					scene.reflectionZ(index),
					scene.pathLengthMeters(index),
					scene.low(index),
					scene.mid(index),
					scene.high(index)
			));
		}
		selected.append(']');
		StringBuilder clusters = new StringBuilder("[");
		for (int index = 0; index < scene.clusterCount(); index++) {
			if (index > 0) {
				clusters.append(',');
			}
			clusters.append(String.format(
					Locale.ROOT,
					"{\"arrival_samples\":%.17g,\"path_count\":%d,"
							+ "\"direction\":[%.17g,%.17g,%.17g],"
							+ "\"energy\":[%.17g,%.17g,%.17g]}",
					scene.clusterArrivalSamples(index),
					scene.clusterPathCount(index),
					scene.clusterDirectionX(index),
					scene.clusterDirectionY(index),
					scene.clusterDirectionZ(index),
					scene.clusterLow(index),
					scene.clusterMid(index),
					scene.clusterHigh(index)
			));
		}
		clusters.append(']');
		return String.format(
				Locale.ROOT,
				"{\"patch_count\":%d,\"geometry_candidates\":%d,"
						+ "\"occluded_candidates\":%d,"
						+ "\"incomplete_candidates\":%d,"
						+ "\"selected_count\":%d,\"selected\":%s,"
						+ "\"cluster_count\":%d,\"clusters\":%s}",
				patchCount,
				scene.geometryCandidates(),
				scene.occludedCandidates(),
				scene.incompleteCandidates(),
				scene.selectedCount(),
				selected,
				scene.clusterCount(),
				clusters
		);
	}

	private static String ledgerJson(EarlyLateEnergyLedger.Workspace ledger) {
		return String.format(
				Locale.ROOT,
				"{\"normalization_candidates\":6,"
						+ "\"candidate\":[%.17g,%.17g,%.17g],"
						+ "\"early_allocated\":[%.17g,%.17g,%.17g],"
						+ "\"early_rejected\":[%.17g,%.17g,%.17g],"
						+ "\"late_residual\":[%.17g,%.17g,%.17g],"
						+ "\"environment_budget\":[%.17g,%.17g,%.17g],"
						+ "\"late_wet_gain\":%.17g}",
				ledger.explicitCandidate(0),
				ledger.explicitCandidate(1),
				ledger.explicitCandidate(2),
				ledger.explicitAllocated(0),
				ledger.explicitAllocated(1),
				ledger.explicitAllocated(2),
				ledger.explicitRejected(0),
				ledger.explicitRejected(1),
				ledger.explicitRejected(2),
				ledger.lateResidual(0),
				ledger.lateResidual(1),
				ledger.lateResidual(2),
				ledger.environmentBudget(0),
				ledger.environmentBudget(1),
				ledger.environmentBudget(2),
				ledger.lateWetGain()
		);
	}

	private static boolean ledgerConserved(
			EarlyLateEnergyLedger.Workspace ledger
	) {
		for (int band = 0; band < 3; band++) {
			if (Math.abs(
					ledger.explicitAllocated(band)
							+ ledger.lateResidual(band)
							- ledger.environmentBudget(band)
			) > 1.0e-12) {
				return false;
			}
		}
		return true;
	}

	private static CacheEvidence cacheEvidence() {
		LocalPlaneSceneCacheKey cache = new LocalPlaneSceneCacheKey();
		boolean initial =
				cache.matches(9, 0, 2, 0, 0.5, 2, 0.2);
		cache.update(9, 0, 2, 0, 0.5, 2, 0.2);
		return new CacheEvidence(
				initial,
				cache.matches(9, 0, 2, 0, 0.5, 2, 0.2),
				cache.matches(9, 1.0e-9, 2, 0, 0.5, 2, 0.2),
				cache.matches(9, 0, 2, 0, 0.5, 2, 0.200000001),
				cache.matches(10, 0, 2, 0, 0.5, 2, 0.2)
		);
	}

	private static String cacheJson(CacheEvidence cache) {
		return String.format(
				Locale.ROOT,
				"{\"initial_match\":%s,\"exact_match\":%s,"
						+ "\"source_moved_match\":%s,"
						+ "\"listener_moved_match\":%s,"
						+ "\"generation_changed_match\":%s}",
				cache.initialMatch(),
				cache.exactMatch(),
				cache.sourceMovedMatch(),
				cache.listenerMovedMatch(),
				cache.generationChangedMatch()
		);
	}

	private static Benchmark benchmark(
			LocalPlaneSceneSolver.Workspace slab,
			LocalPlaneSceneSolver.Workspace bounded
	) {
		for (int iteration = 0; iteration < 150_000; iteration++) {
			solveSlab(slab);
			solveEight(bounded);
		}
		ThreadMXBean bean = allocationBean();
		long threadId = Thread.currentThread().threadId();
		int pairs = 50_000;
		long[] allocation = new long[5];
		double checksum = 0.0;
		for (int window = 0; window < allocation.length; window++) {
			long before = bean.getThreadAllocatedBytes(threadId);
			for (int iteration = 0; iteration < pairs; iteration++) {
				solveSlab(slab);
				solveEight(bounded);
				checksum += slab.selectedCount()
						+ bounded.selectedCount()
						+ bounded.clusterArrivalSamples(0);
			}
			allocation[window] =
					bean.getThreadAllocatedBytes(threadId) - before;
		}
		long[] elapsed = new long[400];
		for (int batch = 0; batch < elapsed.length; batch++) {
			long started = System.nanoTime();
			for (int iteration = 0; iteration < 64; iteration++) {
				solveSlab(slab);
				solveEight(bounded);
				checksum += bounded.selectedMidSum();
			}
			elapsed[batch] = System.nanoTime() - started;
		}
		Arrays.sort(elapsed);
		long[] sortedAllocation = allocation.clone();
		Arrays.sort(sortedAllocation);
		if (!Double.isFinite(checksum) || checksum <= 0.0) {
			throw new IllegalStateException("benchmark checksum changed");
		}
		return new Benchmark(
				pairs,
				allocation,
				sortedAllocation[2] / (double) (pairs * 2L),
				percentile(elapsed, 0.99) / 128.0,
				checksum
		);
	}

	private static AxisAlignedPlanePatch patch(
			int axis,
			int normal,
			double coordinate,
			double minimumFirst,
			double maximumFirst,
			double minimumSecond,
			double maximumSecond
	) {
		return new AxisAlignedPlanePatch(
				axis,
				normal,
				coordinate,
				minimumFirst,
				maximumFirst,
				minimumSecond,
				maximumSecond,
				AcousticMaterials.STONE
		);
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

	private static long percentile(long[] sorted, double quantile) {
		int index = (int) Math.ceil(quantile * sorted.length) - 1;
		return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
	}

	private static String longArray(long[] values) {
		return String.format(
				Locale.ROOT,
				"[%d,%d,%d,%d,%d]",
				values[0], values[1], values[2], values[3], values[4]
		);
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(bytes)
			);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 unavailable", exception);
		}
	}

	private record Benchmark(
			int scenePairsPerWindow,
			long[] allocationWindows,
			double allocatedBytesPerScene,
			double p99NanosPerScene,
			double checksum
	) {
	}

	private record CacheEvidence(
			boolean initialMatch,
			boolean exactMatch,
			boolean sourceMovedMatch,
			boolean listenerMovedMatch,
			boolean generationChangedMatch
	) {
		private boolean invalidates() {
			return !initialMatch
					&& exactMatch
					&& !sourceMovedMatch
					&& !listenerMovedMatch
					&& !generationChangedMatch;
		}
	}
}
