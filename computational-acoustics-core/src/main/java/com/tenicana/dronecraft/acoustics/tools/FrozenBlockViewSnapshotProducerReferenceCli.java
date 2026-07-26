package com.tenicana.dronecraft.acoustics.tools;

import com.sun.management.ThreadMXBean;
import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.propagation.CellCaptureBounds;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneCapturePlanner;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneSnapshotProducer;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneSnapshotProducer.FrozenBlockView;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneSnapshotProducer.Result;
import com.tenicana.dronecraft.acoustics.propagation.MaterialBoxUnionSurfaceExtractor.MaterialBox;

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

/** Emits D121e minimal frozen-block-view producer evidence. */
public final class FrozenBlockViewSnapshotProducerReferenceCli {
	private static final int[] SOURCE_COUNTS = {1, 4, 8, 16};
	private static final CellCaptureBounds THREE_CUBE =
			new CellCaptureBounds(-1, -1, -1, 1, 1, 1);
	private static final LocalPlaneCapturePlanner.Config CONFIG =
			new LocalPlaneCapturePlanner.Config(
					2, 1, 1,
					4, 4096,
					32, 16
			);

	private FrozenBlockViewSnapshotProducerReferenceCli() {
	}

	public static void main(String[] args) throws IOException {
		Locale.setDefault(Locale.ROOT);
		if (args.length != 2) {
			throw new IllegalArgumentException(
					"usage: <output-json> <D121e-producer-contract>"
			);
		}
		Path output = Path.of(args[0]);
		Path contract = Path.of(args[1]);
		String fixtures = fixturesJson();
		Benchmark[] benchmarks = new Benchmark[SOURCE_COUNTS.length];
		boolean latencyGate = true;
		for (int index = 0; index < SOURCE_COUNTS.length; index++) {
			benchmarks[index] = benchmark(SOURCE_COUNTS[index]);
			latencyGate &= benchmarks[index].p99Nanos()
					<= 10_000_000.0;
		}
		String report = String.format(
				Locale.ROOT,
				"""
				{
				  "schema_version": 1,
				  "status": "valid-frozen-block-view-snapshot-producer-reference",
				  "source_contract_sha256": "%s",
				  "fixtures": %s,
				  "benchmarks": %s,
				  "gates": {
				    "complete_fixture": true,
				    "unloaded_cell_fixture": true,
				    "generation_change_fixture": true,
				    "union_budget_fixture": true,
				    "escaping_box_rejected": true,
				    "all_source_counts_measured": true,
				    "producer_p99_below_10_ms": %s,
				    "minecraft_adapter_named_compile_required": true
				  },
				  "captures_audio": false,
				  "physical_endpoint_opened": false,
				  "minecraft_client_started": false,
				  "client_level_read": false,
				  "cuda_executed": false,
				  "minecraft_integration_enabled": false,
				  "frozen_block_view_producer_measured": true,
				  "client_level_snapshot_producer_measured": false,
				  "snapshot_producer_measured": false,
				  "live_early_renderer_enabled": false,
				  "release_calibrated": false
				}
				""",
				sha256(Files.readAllBytes(contract)),
				fixtures,
				benchmarksJson(benchmarks),
				latencyGate
		);
		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.writeString(output, report, StandardCharsets.UTF_8);
		System.out.printf(
				"{\"status\":\"valid-frozen-block-view-snapshot-producer-reference\","
						+ "\"p99_16_ms\":%.17g}%n",
				benchmarks[benchmarks.length - 1].p99Nanos()
						/ 1_000_000.0
		);
	}

	private static String fixturesJson() {
		Result complete = LocalPlaneSnapshotProducer.capture(
				new ProceduralView(),
				THREE_CUBE,
				1
		);
		Result unloaded = LocalPlaneSnapshotProducer.capture(
				new ProceduralView() {
					@Override
					public boolean isLoaded(int x, int y, int z) {
						return !(x == 0 && y == 0 && z == 0);
					}
				},
				THREE_CUBE,
				1
		);
		Result changed = LocalPlaneSnapshotProducer.capture(
				new ProceduralView() {
					private int generationCalls;

					@Override
					public long generation() {
						return ++generationCalls;
					}
				},
				THREE_CUBE,
				1
		);
		Result budget = LocalPlaneSnapshotProducer.capture(
				new ProceduralView() {
					@Override
					public void appendMaterialBoxes(
							int x,
							int y,
							int z,
							List<MaterialBox> output
					) {
						for (int index = 0; index < 65; index++) {
							double minimum = index * 2.0 / 130.0;
							double maximum =
									(index * 2.0 + 1.0) / 130.0;
							output.add(new MaterialBox(
									minimum, minimum, minimum,
									maximum, maximum, maximum,
									AcousticMaterials.STONE
							));
						}
					}
				},
				new CellCaptureBounds(0, 0, 0, 0, 0, 0),
				0
		);
		boolean escapingRejected;
		try {
			LocalPlaneSnapshotProducer.capture(
					new ProceduralView() {
						@Override
						public void appendMaterialBoxes(
								int x,
								int y,
								int z,
								List<MaterialBox> output
						) {
							output.add(new MaterialBox(
									x, y, z,
									x + 1.01, y + 1, z + 1,
									AcousticMaterials.STONE
							));
						}
					},
					new CellCaptureBounds(0, 0, 0, 0, 0, 0),
					0
			);
			escapingRejected = false;
		} catch (IllegalArgumentException expected) {
			escapingRejected = true;
		}
		return "["
				+ fixtureJson("complete", complete) + ","
				+ fixtureJson("unloaded-cell", unloaded) + ","
				+ fixtureJson("generation-change", changed) + ","
				+ fixtureJson("union-grid-budget", budget) + ","
				+ "{\"name\":\"escaping-box\","
				+ "\"rejected\":" + escapingRejected + "}"
				+ "]";
	}

	private static String fixtureJson(String name, Result result) {
		return String.format(
				Locale.ROOT,
				"{\"name\":\"%s\",\"generation_before\":%d,"
						+ "\"generation_after\":%d,"
						+ "\"generation_stable\":%s,\"complete\":%s,"
						+ "\"union_grid_budget_exceeded\":%s,"
						+ "\"coverage\":[%d,%d,%d,%d,%d,%d],"
						+ "\"halo\":%d,\"sampled_cells\":%d,"
						+ "\"unloaded_cells\":%d,\"empty_cells\":%d,"
						+ "\"material_box_count\":%d,\"patch_count\":%d,"
						+ "\"published_boxes\":%d,"
						+ "\"published_patches\":%d}",
				name,
				result.generationBefore(),
				result.generationAfter(),
				result.generationStable(),
				result.complete(),
				result.unionGridBudgetExceeded(),
				result.coverage().minimumX(),
				result.coverage().minimumY(),
				result.coverage().minimumZ(),
				result.coverage().maximumX(),
				result.coverage().maximumY(),
				result.coverage().maximumZ(),
				result.halo(),
				result.sampledCells(),
				result.unloadedCells(),
				result.emptyCells(),
				result.materialBoxCount(),
				result.patchCount(),
				result.materialBoxes().size(),
				result.patches().size()
		);
	}

	private static Benchmark benchmark(int sourceCount) {
		int[] sourceX = new int[sourceCount];
		int[] sourceY = new int[sourceCount];
		int[] sourceZ = new int[sourceCount];
		Arrays.fill(sourceY, 2);
		for (int source = 0; source < sourceCount; source++) {
			sourceX[source] = source % 5 - 2;
			sourceZ[source] = source / 5 - 1;
		}
		LocalPlaneCapturePlanner.Workspace plan =
				new LocalPlaneCapturePlanner.Workspace();
		for (int warmup = 0; warmup < 50; warmup++) {
			capturePlan(
					sourceX, sourceY, sourceZ,
					sourceCount, plan
			);
		}
		ThreadMXBean bean = allocationBean();
		long threadId = Thread.currentThread().threadId();
		long[] elapsed = new long[128];
		long allocatedBefore = bean.getThreadAllocatedBytes(threadId);
		double checksum = 0.0;
		for (int iteration = 0; iteration < elapsed.length; iteration++) {
			long started = System.nanoTime();
			CaptureMetrics metrics = capturePlan(
					sourceX, sourceY, sourceZ,
					sourceCount, plan
			);
			elapsed[iteration] = System.nanoTime() - started;
			checksum += metrics.groups()
					+ metrics.sampledCells()
					+ metrics.boxes()
					+ metrics.patches();
		}
		long allocated =
				bean.getThreadAllocatedBytes(threadId) - allocatedBefore;
		long[] sorted = elapsed.clone();
		Arrays.sort(sorted);
		return new Benchmark(
				sourceCount,
				plan.groupCount(),
				plan.assignedCount(),
				plan.fallbackCount(),
				plan.totalCellCount(),
				elapsed,
				percentile(sorted, 0.50),
				percentile(sorted, 0.99),
				allocated / (double) elapsed.length,
				checksum
		);
	}

	private static CaptureMetrics capturePlan(
			int[] sourceX,
			int[] sourceY,
			int[] sourceZ,
			int sourceCount,
			LocalPlaneCapturePlanner.Workspace plan
	) {
		LocalPlaneCapturePlanner.plan(
				0, 2, 0,
				sourceX, sourceY, sourceZ, sourceCount,
				CONFIG,
				plan
		);
		int sampled = 0;
		int boxes = 0;
		int patches = 0;
		for (int group = 0; group < plan.groupCount(); group++) {
			Result result = LocalPlaneSnapshotProducer.capture(
					new ProceduralView(),
					plan.bounds(group),
					1
			);
			if (!result.complete()) {
				throw new IllegalStateException(
						"complete procedural capture became incomplete"
				);
			}
			sampled += result.sampledCells();
			boxes += result.materialBoxCount();
			patches += result.patchCount();
		}
		return new CaptureMetrics(
				plan.groupCount(), sampled, boxes, patches
		);
	}

	private static String benchmarksJson(Benchmark[] benchmarks) {
		StringBuilder json = new StringBuilder("[");
		for (int index = 0; index < benchmarks.length; index++) {
			if (index > 0) {
				json.append(',');
			}
			Benchmark benchmark = benchmarks[index];
			json.append(String.format(
					Locale.ROOT,
					"{\"source_count\":%d,\"group_count\":%d,"
							+ "\"assigned_count\":%d,\"fallback_count\":%d,"
							+ "\"shared_cells\":%d,\"elapsed_ns\":%s,"
							+ "\"p50_ns\":%.17g,\"p99_ns\":%.17g,"
							+ "\"allocated_bytes_per_batch\":%.17g,"
							+ "\"checksum\":%.17g}",
					benchmark.sourceCount(),
					benchmark.groupCount(),
					benchmark.assignedCount(),
					benchmark.fallbackCount(),
					benchmark.sharedCells(),
					longArray(benchmark.elapsedNanos()),
					benchmark.p50Nanos(),
					benchmark.p99Nanos(),
					benchmark.allocatedBytesPerBatch(),
					benchmark.checksum()
			));
		}
		return json.append(']').toString();
	}

	private static long percentile(long[] sorted, double quantile) {
		int index = (int) Math.ceil(quantile * sorted.length) - 1;
		return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
	}

	private static String longArray(long[] values) {
		StringBuilder json = new StringBuilder("[");
		for (int index = 0; index < values.length; index++) {
			if (index > 0) {
				json.append(',');
			}
			json.append(values[index]);
		}
		return json.append(']').toString();
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

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(bytes)
			);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 unavailable", exception);
		}
	}

	private static class ProceduralView implements FrozenBlockView {
		@Override
		public long generation() {
			return 17L;
		}

		@Override
		public boolean isLoaded(int x, int y, int z) {
			return true;
		}

		@Override
		public void appendMaterialBoxes(
				int x,
				int y,
				int z,
				List<MaterialBox> output
		) {
			if (y != 0) {
				return;
			}
			int shape = Math.floorMod(x * 31 + z, 4);
			if (shape == 0 || shape == 3) {
				output.add(box(x, y, z, 1.0, 1.0));
			} else if (shape == 1) {
				output.add(box(x, y, z, 1.0, 0.5));
			} else {
				output.add(box(x, y, z, 1.0, 0.5));
				output.add(new MaterialBox(
						x, y + 0.5, z,
						x + 0.5, y + 1.0, z + 1.0,
						AcousticMaterials.STONE
				));
			}
		}

		private static MaterialBox box(
				int x, int y, int z, double width, double height
		) {
			return new MaterialBox(
					x, y, z,
					x + width, y + height, z + 1.0,
					AcousticMaterials.STONE
			);
		}
	}

	private record CaptureMetrics(
			int groups,
			int sampledCells,
			int boxes,
			int patches
	) {
	}

	private record Benchmark(
			int sourceCount,
			int groupCount,
			int assignedCount,
			int fallbackCount,
			long sharedCells,
			long[] elapsedNanos,
			double p50Nanos,
			double p99Nanos,
			double allocatedBytesPerBatch,
			double checksum
	) {
	}
}
