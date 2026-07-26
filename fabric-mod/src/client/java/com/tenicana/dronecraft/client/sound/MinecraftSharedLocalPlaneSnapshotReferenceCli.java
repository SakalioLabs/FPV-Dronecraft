package com.tenicana.dronecraft.client.sound;

import com.sun.management.ThreadMXBean;
import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.propagation.AxisAlignedPlanePatch;
import com.tenicana.dronecraft.acoustics.propagation.CellCaptureBounds;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneCapturePlanner;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneReflectionSolver;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneSceneSolver;
import com.tenicana.dronecraft.acoustics.propagation.MaterialBoxSegmentBlockQuery;
import com.tenicana.dronecraft.acoustics.propagation.MaterialBoxUnionSurfaceExtractor;
import com.tenicana.dronecraft.acoustics.propagation.MaterialBoxUnionSurfaceExtractor.MaterialBox;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Offline native-shape D121d shared capture planning/union benchmark. */
public final class MinecraftSharedLocalPlaneSnapshotReferenceCli {
	private static final int[] SOURCE_COUNTS = {1, 4, 8, 16};
	private static final String[] LAYOUTS = {
			"coincident", "clustered", "corridor", "dispersed"
	};
	private static final int LISTENER_X = 0;
	private static final int LISTENER_Y = 2;
	private static final int LISTENER_Z = 0;
	private static final LocalPlaneCapturePlanner.Config CONFIG =
			new LocalPlaneCapturePlanner.Config(
					2, 1, 1,
					4, 4096,
					32, 16
			);
	private static final LocalPlaneReflectionSolver.CellBlockQuery OPEN =
			(x, y, z) -> false;
	private static final VoxelShape FULL = Shapes.block();
	private static final VoxelShape SLAB =
			Shapes.box(0, 0, 0, 1, 0.5, 1);
	private static final VoxelShape STAIR = Shapes.or(
			SLAB,
			Shapes.box(0, 0.5, 0, 0.5, 1, 1)
	).optimize();

	private MinecraftSharedLocalPlaneSnapshotReferenceCli() {
	}

	public static void main(String[] args) throws IOException {
		Locale.setDefault(Locale.ROOT);
		if (args.length != 2) {
			throw new IllegalArgumentException(
					"usage: <output-json> <D121d-shared-contract>"
			);
		}
		Path output = Path.of(args[0]);
		Path contract = Path.of(args[1]);
		StringBuilder cases = new StringBuilder("[");
		boolean savingsGate = true;
		boolean latencyGate = true;
		boolean solveGate = true;
		int caseCount = 0;
		for (String layout : LAYOUTS) {
			for (int sourceCount : SOURCE_COUNTS) {
				if (caseCount++ > 0) {
					cases.append(',');
				}
				CaseEvidence evidence = runCase(layout, sourceCount);
				cases.append(evidence.json());
				savingsGate &= evidence.sharedCells()
						<= evidence.independentAssignedCells();
				latencyGate &= evidence.p99Nanos() <= 10_000_000.0;
				solveGate &= evidence.p99SolveBatchNanos()
						<= 5_000_000.0;
			}
		}
		cases.append(']');
		CoverageEvidence coverage = coverageEvidence();
		String report = String.format(
				Locale.ROOT,
				"""
				{
				  "schema_version": 1,
				  "status": "valid-minecraft-shared-local-plane-snapshot-reference",
				  "minecraft_version": "1.21.11",
				  "native_shape_api": "named Shapes plus VoxelShape.optimize().toAabbs()",
				  "source_contract_sha256": "%s",
				  "planner_config": {
				    "reflection_horizontal_radius": 2,
				    "reflection_vertical_radius": 1,
				    "halo": 1,
				    "maximum_groups": 4,
				    "maximum_sampled_block_states": 4096,
				    "maximum_horizontal_span": 32,
				    "maximum_vertical_span": 16
				  },
				  "coverage_fixture": %s,
				  "cases": %s,
				  "gates": {
				    "native_voxel_shape_executed": true,
				    "coverage_unknown_is_incomplete": %s,
				    "covered_blocker_is_complete_occlusion": %s,
				    "all_16_cases_executed": %s,
				    "shared_cells_not_above_assigned_independent": %s,
				    "native_shape_planning_union_p99_below_10_ms": %s,
				    "shared_scene_solve_p99_below_5_ms": %s
				  },
				  "captures_audio": false,
				  "physical_endpoint_opened": false,
				  "minecraft_client_started": false,
				  "client_level_read": false,
				  "synthetic_native_shape_field": true,
				  "cuda_executed": false,
				  "minecraft_integration_enabled": false,
				  "snapshot_producer_measured": false,
				  "native_shape_construction_measured": true,
				  "release_calibrated": false
				}
				""",
				sha256(Files.readAllBytes(contract)),
				coverage.json(),
				cases,
				coverage.unknownCoverageMiss()
						&& !coverage.unknownComplete(),
				coverage.blockedComplete()
						&& !coverage.blockedVisible(),
				caseCount == 16,
				savingsGate,
				latencyGate,
				solveGate
		);
		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.writeString(output, report, StandardCharsets.UTF_8);
		System.out.printf(
				"{\"status\":\"valid-minecraft-shared-local-plane-snapshot-reference\","
						+ "\"cases\":%d,\"coverage_miss\":%s}%n",
				caseCount,
				coverage.unknownCoverageMiss()
		);
	}

	private static CaseEvidence runCase(String layout, int sourceCount) {
		int[] sourceX = new int[sourceCount];
		int[] sourceY = new int[sourceCount];
		int[] sourceZ = new int[sourceCount];
		fillLayout(layout, sourceX, sourceY, sourceZ);
		LocalPlaneCapturePlanner.Workspace plan =
				new LocalPlaneCapturePlanner.Workspace();
		LocalPlaneCapturePlanner.plan(
				LISTENER_X, LISTENER_Y, LISTENER_Z,
				sourceX, sourceY, sourceZ, sourceCount,
				CONFIG,
				plan
		);
		long independent = independentAssignedCells(
				sourceX, sourceY, sourceZ, sourceCount, plan
		);
		SceneField field = constructField(plan);
		SnapshotMetrics metrics = field.metrics();
		for (int warmup = 0; warmup < 20; warmup++) {
			LocalPlaneCapturePlanner.plan(
					LISTENER_X, LISTENER_Y, LISTENER_Z,
					sourceX, sourceY, sourceZ, sourceCount,
					CONFIG,
					plan
			);
			constructField(plan);
		}
		ThreadMXBean bean = allocationBean();
		long threadId = Thread.currentThread().threadId();
		long[] elapsed = new long[64];
		long before = bean.getThreadAllocatedBytes(threadId);
		double checksum = 0.0;
		for (int iteration = 0; iteration < elapsed.length; iteration++) {
			long started = System.nanoTime();
			LocalPlaneCapturePlanner.plan(
					LISTENER_X, LISTENER_Y, LISTENER_Z,
					sourceX, sourceY, sourceZ, sourceCount,
					CONFIG,
					plan
			);
			SnapshotMetrics measured = constructField(plan).metrics();
			elapsed[iteration] = System.nanoTime() - started;
			checksum += measured.boxes()
					+ measured.patches()
					+ measured.coordinateGridCells();
		}
		long allocated =
				bean.getThreadAllocatedBytes(threadId) - before;
		long[] sorted = elapsed.clone();
		Arrays.sort(sorted);
		SolveBenchmark solveBenchmark = solveBenchmark(
				plan,
				field,
				sourceX,
				sourceY,
				sourceZ
		);
		return new CaseEvidence(
				layout,
				sourceCount,
				sourceX,
				sourceY,
				sourceZ,
				sourceGroups(sourceCount, plan),
				groupsJson(plan),
				plan.assignedCount(),
				plan.fallbackCount(),
				independent,
				plan.totalCellCount(),
				metrics.boxes(),
				metrics.patches(),
				metrics.coordinateGridCells(),
				elapsed,
				percentile(sorted, 0.50),
				percentile(sorted, 0.99),
				allocated / (double) elapsed.length,
				checksum,
				solveBenchmark.elapsedNanos(),
				solveBenchmark.p99Nanos(),
				solveBenchmark.checksum()
		);
	}

	private static SceneField constructField(
			LocalPlaneCapturePlanner.Workspace plan
	) {
		int boxes = 0;
		int patches = 0;
		long coordinateGridCells = 0L;
		List<List<MaterialBox>> groupBoxes = new ArrayList<>();
		List<List<AxisAlignedPlanePatch>> groupPatches =
				new ArrayList<>();
		for (int group = 0; group < plan.groupCount(); group++) {
			CellCaptureBounds bounds = plan.bounds(group);
			List<MaterialBox> materialBoxes = new ArrayList<>();
			BlockPos.MutableBlockPos position =
					new BlockPos.MutableBlockPos();
			if (bounds.minimumY() <= 0 && bounds.maximumY() >= 0) {
				for (int x = bounds.minimumX();
						x <= bounds.maximumX(); x++) {
					for (int z = bounds.minimumZ();
							z <= bounds.maximumZ(); z++) {
						position.set(x, 0, z);
						MinecraftLocalPlaneSnapshot.appendShapeBoxes(
								materialBoxes,
								position,
								shape(x, z),
								AcousticMaterials.STONE
						);
					}
				}
			}
			List<AxisAlignedPlanePatch> exposed =
					MaterialBoxUnionSurfaceExtractor.extract(
							materialBoxes
					);
			boxes += materialBoxes.size();
			patches += exposed.size();
			coordinateGridCells += coordinateGridCells(materialBoxes);
			groupBoxes.add(List.copyOf(materialBoxes));
			groupPatches.add(exposed);
		}
		return new SceneField(
				List.copyOf(groupBoxes),
				List.copyOf(groupPatches),
				new SnapshotMetrics(boxes, patches, coordinateGridCells)
		);
	}

	private static SolveBenchmark solveBenchmark(
			LocalPlaneCapturePlanner.Workspace plan,
			SceneField field,
			int[] sourceX,
			int[] sourceY,
			int[] sourceZ
	) {
		MaterialBoxSegmentBlockQuery[] exact =
				new MaterialBoxSegmentBlockQuery[plan.groupCount()];
		CellCaptureBounds[] coverage =
				new CellCaptureBounds[plan.groupCount()];
		for (int group = 0; group < plan.groupCount(); group++) {
			exact[group] = new MaterialBoxSegmentBlockQuery(
					field.groupBoxes().get(group)
			);
			coverage[group] = plan.bounds(group);
		}
		LocalPlaneSceneSolver.Workspace[] workspaces =
				new LocalPlaneSceneSolver.Workspace[sourceX.length];
		for (int source = 0; source < sourceX.length; source++) {
			workspaces[source] = new LocalPlaneSceneSolver.Workspace();
		}
		double checksum = 0.0;
		for (int warmup = 0; warmup < 100; warmup++) {
			checksum += solveAssigned(
					plan, field, exact, coverage,
					sourceX, sourceY, sourceZ, workspaces
			);
		}
		long[] elapsed = new long[128];
		for (int iteration = 0; iteration < elapsed.length; iteration++) {
			long started = System.nanoTime();
			checksum += solveAssigned(
					plan, field, exact, coverage,
					sourceX, sourceY, sourceZ, workspaces
			);
			elapsed[iteration] = System.nanoTime() - started;
		}
		long[] sorted = elapsed.clone();
		Arrays.sort(sorted);
		return new SolveBenchmark(
				elapsed,
				percentile(sorted, 0.99),
				checksum
		);
	}

	private static double solveAssigned(
			LocalPlaneCapturePlanner.Workspace plan,
			SceneField field,
			MaterialBoxSegmentBlockQuery[] exact,
			CellCaptureBounds[] coverage,
			int[] sourceX,
			int[] sourceY,
			int[] sourceZ,
			LocalPlaneSceneSolver.Workspace[] workspaces
	) {
		double checksum = 0.0;
		for (int source = 0; source < sourceX.length; source++) {
			int group = plan.sourceGroup(source);
			if (group < 0) {
				continue;
			}
			LocalPlaneSceneSolver.solve(
					sourceX[source] + 0.5,
					sourceY[source] + 0.5,
					sourceZ[source] + 0.5,
					LISTENER_X + 0.5,
					LISTENER_Y + 0.5,
					LISTENER_Z + 0.5,
					field.groupPatches().get(group),
					OPEN,
					exact[group],
					coverage[group],
					64,
					workspaces[source]
			);
			checksum += workspaces[source].selectedCount()
					+ workspaces[source].clusterCount()
					+ workspaces[source].geometryCandidates();
		}
		return checksum;
	}

	private static VoxelShape shape(int x, int z) {
		return switch (Math.floorMod(x * 31 + z, 4)) {
			case 0 -> FULL;
			case 1 -> SLAB;
			case 2 -> STAIR;
			default -> FULL;
		};
	}

	private static long coordinateGridCells(List<MaterialBox> boxes) {
		if (boxes.isEmpty()) {
			return 0L;
		}
		double[] x = new double[boxes.size() * 2];
		double[] y = new double[boxes.size() * 2];
		double[] z = new double[boxes.size() * 2];
		for (int index = 0; index < boxes.size(); index++) {
			MaterialBox box = boxes.get(index);
			x[index * 2] = box.minimumX();
			x[index * 2 + 1] = box.maximumX();
			y[index * 2] = box.minimumY();
			y[index * 2 + 1] = box.maximumY();
			z[index * 2] = box.minimumZ();
			z[index * 2 + 1] = box.maximumZ();
		}
		return ((long) unique(x) - 1L)
				* ((long) unique(y) - 1L)
				* ((long) unique(z) - 1L);
	}

	private static int unique(double[] values) {
		Arrays.sort(values);
		int unique = 1;
		for (int index = 1; index < values.length; index++) {
			if (Double.compare(values[index], values[index - 1]) != 0) {
				unique++;
			}
		}
		return unique;
	}

	private static long independentAssignedCells(
			int[] sourceX,
			int[] sourceY,
			int[] sourceZ,
			int sourceCount,
			LocalPlaneCapturePlanner.Workspace plan
	) {
		long total = 0L;
		for (int source = 0; source < sourceCount; source++) {
			if (plan.sourceGroup(source) < 0) {
				continue;
			}
			int minimumX = Math.min(LISTENER_X, sourceX[source]) - 3;
			int maximumX = Math.max(LISTENER_X, sourceX[source]) + 3;
			int minimumY = Math.min(LISTENER_Y, sourceY[source]) - 2;
			int maximumY = Math.max(LISTENER_Y, sourceY[source]) + 2;
			int minimumZ = Math.min(LISTENER_Z, sourceZ[source]) - 3;
			int maximumZ = Math.max(LISTENER_Z, sourceZ[source]) + 3;
			total += (long) (maximumX - minimumX + 1)
					* (maximumY - minimumY + 1)
					* (maximumZ - minimumZ + 1);
		}
		return total;
	}

	private static CoverageEvidence coverageEvidence() {
		AxisAlignedPlanePatch patch = new AxisAlignedPlanePatch(
				1, 1, 0.5,
				0, 1, 0, 1,
				AcousticMaterials.STONE
		);
		LocalPlaneReflectionSolver.Workspace unknown =
				new LocalPlaneReflectionSolver.Workspace();
		LocalPlaneReflectionSolver.solve(
				0.2, 1.5, 0.4,
				0.8, 1.5, 0.6,
				patch,
				(x, y, z) -> false,
				LocalPlaneReflectionSolver.SegmentBlockQuery.NONE,
				(x, y, z) -> y >= 1,
				32,
				unknown
		);
		LocalPlaneReflectionSolver.Workspace blocked =
				new LocalPlaneReflectionSolver.Workspace();
		LocalPlaneReflectionSolver.solve(
				0.2, 1.5, 0.4,
				0.8, 1.5, 0.6,
				patch,
				(x, y, z) -> y == 0,
				LocalPlaneReflectionSolver.SegmentBlockQuery.NONE,
				new CellCaptureBounds(-1, 0, -1, 1, 2, 1),
				32,
				blocked
		);
		return new CoverageEvidence(
				unknown.complete(),
				unknown.topologyVisible(),
				unknown.coverageMiss(),
				blocked.complete(),
				blocked.topologyVisible(),
				blocked.coverageMiss()
		);
	}

	private static void fillLayout(
			String layout,
			int[] x,
			int[] y,
			int[] z
	) {
		Arrays.fill(y, LISTENER_Y);
		for (int source = 0; source < x.length; source++) {
			switch (layout) {
				case "coincident" -> {
					x[source] = 0;
					z[source] = 0;
				}
				case "clustered" -> {
					x[source] = source % 5 - 2;
					z[source] = source / 5 - 1;
				}
				case "corridor" -> {
					x[source] = source * 2;
					z[source] = source % 3 - 1;
				}
				case "dispersed" -> {
					int distance = 40 + source * 3;
					x[source] = switch (source & 3) {
						case 0 -> distance;
						case 1 -> -distance;
						default -> 0;
					};
					z[source] = switch (source & 3) {
						case 2 -> distance;
						case 3 -> -distance;
						default -> 0;
					};
				}
				default -> throw new IllegalArgumentException(layout);
			}
		}
	}

	private static int[] sourceGroups(
			int sourceCount,
			LocalPlaneCapturePlanner.Workspace plan
	) {
		int[] groups = new int[sourceCount];
		for (int source = 0; source < sourceCount; source++) {
			groups[source] = plan.sourceGroup(source);
		}
		return groups;
	}

	private static String groupsJson(
			LocalPlaneCapturePlanner.Workspace plan
	) {
		StringBuilder json = new StringBuilder("[");
		for (int group = 0; group < plan.groupCount(); group++) {
			if (group > 0) {
				json.append(',');
			}
			CellCaptureBounds bounds = plan.bounds(group);
			json.append(String.format(
					Locale.ROOT,
					"{\"bounds\":[%d,%d,%d,%d,%d,%d],"
							+ "\"cell_count\":%d}",
					bounds.minimumX(),
					bounds.minimumY(),
					bounds.minimumZ(),
					bounds.maximumX(),
					bounds.maximumY(),
					bounds.maximumZ(),
					plan.cellCount(group)
			));
		}
		return json.append(']').toString();
	}

	private static long percentile(long[] sorted, double quantile) {
		int index = (int) Math.ceil(quantile * sorted.length) - 1;
		return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
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

	private static String intArray(int[] values) {
		StringBuilder json = new StringBuilder("[");
		for (int index = 0; index < values.length; index++) {
			if (index > 0) {
				json.append(',');
			}
			json.append(values[index]);
		}
		return json.append(']').toString();
	}

	private static String positions(
			int[] x, int[] y, int[] z
	) {
		StringBuilder json = new StringBuilder("[");
		for (int index = 0; index < x.length; index++) {
			if (index > 0) {
				json.append(',');
			}
			json.append(String.format(
					Locale.ROOT,
					"[%d,%d,%d]",
					x[index], y[index], z[index]
			));
		}
		return json.append(']').toString();
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

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(bytes)
			);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 unavailable", exception);
		}
	}

	private record CoverageEvidence(
			boolean unknownComplete,
			boolean unknownVisible,
			boolean unknownCoverageMiss,
			boolean blockedComplete,
			boolean blockedVisible,
			boolean blockedCoverageMiss
	) {
		private String json() {
			return String.format(
					Locale.ROOT,
					"{\"unknown\":{\"complete\":%s,\"visible\":%s,"
							+ "\"coverage_miss\":%s},"
							+ "\"blocked\":{\"complete\":%s,\"visible\":%s,"
							+ "\"coverage_miss\":%s}}",
					unknownComplete,
					unknownVisible,
					unknownCoverageMiss,
					blockedComplete,
					blockedVisible,
					blockedCoverageMiss
			);
		}
	}

	private record SnapshotMetrics(
			int boxes,
			int patches,
			long coordinateGridCells
	) {
	}

	private record SceneField(
			List<List<MaterialBox>> groupBoxes,
			List<List<AxisAlignedPlanePatch>> groupPatches,
			SnapshotMetrics metrics
	) {
	}

	private record SolveBenchmark(
			long[] elapsedNanos,
			double p99Nanos,
			double checksum
	) {
	}

	private record CaseEvidence(
			String layout,
			int sourceCount,
			int[] sourceX,
			int[] sourceY,
			int[] sourceZ,
			int[] sourceGroups,
			String groupsJson,
			int assignedCount,
			int fallbackCount,
			long independentAssignedCells,
			long sharedCells,
			int nativeBoxes,
			int exposedPatches,
			long coordinateGridCells,
			long[] elapsedNanos,
			double p50Nanos,
			double p99Nanos,
			double allocatedBytesPerConstruction,
			double checksum,
			long[] solveBatchElapsedNanos,
			double p99SolveBatchNanos,
			double solveChecksum
	) {
		private String json() {
			return String.format(
					Locale.ROOT,
					"{\"layout\":\"%s\",\"source_count\":%d,"
							+ "\"listener_cell\":[0,2,0],\"source_cells\":%s,"
							+ "\"source_groups\":%s,\"groups\":%s,"
							+ "\"assigned_count\":%d,\"fallback_count\":%d,"
							+ "\"independent_assigned_cells\":%d,"
							+ "\"shared_cells\":%d,\"native_boxes\":%d,"
							+ "\"exposed_patches\":%d,"
							+ "\"coordinate_grid_cells\":%d,"
							+ "\"elapsed_ns\":%s,\"p50_ns\":%.17g,"
							+ "\"p99_ns\":%.17g,"
							+ "\"allocated_bytes_per_construction\":%.17g,"
							+ "\"checksum\":%.17g,"
							+ "\"solve_batch_elapsed_ns\":%s,"
							+ "\"p99_solve_batch_ns\":%.17g,"
							+ "\"solve_checksum\":%.17g}",
					layout,
					sourceCount,
					positions(sourceX, sourceY, sourceZ),
					intArray(sourceGroups),
					groupsJson,
					assignedCount,
					fallbackCount,
					independentAssignedCells,
					sharedCells,
					nativeBoxes,
					exposedPatches,
					coordinateGridCells,
					longArray(elapsedNanos),
					p50Nanos,
					p99Nanos,
					allocatedBytesPerConstruction,
					checksum,
					longArray(solveBatchElapsedNanos),
					p99SolveBatchNanos,
					solveChecksum
			);
		}
	}
}
