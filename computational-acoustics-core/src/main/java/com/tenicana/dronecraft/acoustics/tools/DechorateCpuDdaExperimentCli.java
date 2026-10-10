package com.tenicana.dronecraft.acoustics.tools;

import com.sun.management.ThreadMXBean;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.propagation.DdaFirstOrderPathSolver;
import com.tenicana.dronecraft.acoustics.propagation.DdaFirstOrderPathSolver.Facet;
import com.tenicana.dronecraft.acoustics.propagation.DdaFirstOrderPathSolver.Path;
import com.tenicana.dronecraft.acoustics.propagation.DdaFirstOrderPathSolver.RoomBounds;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * D116 real-CPU-DDA corpus for the 66 hash-pinned dEchorate scenarios.
 * Coordinates are repeated in the report so an independent verifier can bind
 * them back to the SOFA payload instead of trusting these constants.
 */
public final class DechorateCpuDdaExperimentCli {
	private static final double SAMPLE_RATE_HZ = 48_000.0;
	private static final double SPEED_OF_SOUND_MPS = 346.98;
	private static final int MAXIMUM_CELLS_PER_LEG = 32;
	private static final int CANDIDATE_LIMIT = 4;
	private static final RoomBounds VOXEL_BOUNDS =
			new RoomBounds(6.0, 6.0, 2.0);
	private static final RoomBounds CONTINUOUS_BOUNDS =
			new RoomBounds(5.705, 5.965, 2.355);
	private static final String[] ROOM_CODES = {
			"000000", "000001", "000010", "000100", "001000",
			"010000", "011000", "011100", "011110", "011111",
			"020002"
	};
	private static final List<Pair> PAIRS = List.of(
			new Pair(
					4,
					10,
					1,
					new AcousticVector(1.6330509, 0.6820041, 1.16493109),
					new AcousticVector(2.21190695, 1.71556362, 1.30742631)
			),
			new Pair(
					4,
					19,
					15,
					new AcousticVector(1.6330509, 0.6820041, 1.16493109),
					new AcousticVector(3.50149512, 2.61934068, 1.38561103)
			),
			new Pair(
					4,
					20,
					30,
					new AcousticVector(1.6330509, 0.6820041, 1.16493109),
					new AcousticVector(3.72758061, 4.02235716, 0.95058622)
			),
			new Pair(
					6,
					14,
					1,
					new AcousticVector(3.651, 1.004, 1.38),
					new AcousticVector(2.44071254, 1.6029892, 1.30742631)
			),
			new Pair(
					6,
					29,
					15,
					new AcousticVector(3.651, 1.004, 1.38),
					new AcousticVector(2.87343067, 3.56761246, 1.49048013)
			),
			new Pair(
					6,
					0,
					30,
					new AcousticVector(3.651, 1.004, 1.38),
					new AcousticVector(0.80316092, 3.83141445, 1.04391528)
			)
	);

	private DechorateCpuDdaExperimentCli() {
	}

	public static void main(String[] args) throws IOException {
		Locale.setDefault(Locale.ROOT);
		if (args.length != 2) {
			throw new IllegalArgumentException(
					"usage: <output-json> <D115-report-json>"
			);
		}
		java.nio.file.Path output = java.nio.file.Path.of(args[0]);
		java.nio.file.Path sourceReport = java.nio.file.Path.of(args[1]);
		if (!Files.isRegularFile(sourceReport)) {
			throw new IllegalArgumentException(
					"D115 source report does not exist: " + sourceReport
			);
		}

		Corpus corpus = buildCorpus();
		Benchmark benchmark = benchmark();
		String json = render(
				corpus,
				benchmark,
				sha256(Files.readAllBytes(sourceReport))
		);
		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.writeString(output, json, StandardCharsets.UTF_8);
		System.out.printf(
				"{\"status\":\"valid-dechorate-cpu-dda-experiment\","
						+ "\"scenarios\":%d,\"paths\":%d,"
						+ "\"output\":\"%s\"}%n",
				corpus.scenarios().size(),
				corpus.totalPaths(),
				escape(output.toAbsolutePath().toString())
		);
	}

	private static Corpus buildCorpus() {
		Map<GeometryKey, Geometry> cache = new HashMap<>();
		List<Scenario> scenarios = new ArrayList<>();
		int cacheHits = 0;
		int cacheMisses = 0;
		int totalPaths = 0;
		for (String roomCode : ROOM_CODES) {
			String effectiveCode = effectiveCode(roomCode);
			for (Pair pair : PAIRS) {
				GeometryKey key = new GeometryKey(
						pair.sourceId(),
						pair.microphoneId()
				);
				Geometry geometry = cache.get(key);
				if (geometry == null) {
					geometry = geometry(pair);
					cache.put(key, geometry);
					cacheMisses++;
				} else {
					cacheHits++;
				}
				List<Facet> selected = selectCandidates(
						effectiveCode,
						geometry.continuousReflections()
				);
				scenarios.add(
						new Scenario(
								roomCode,
								effectiveCode,
								roomCode.equals("020002"),
								split(roomCode),
								pair,
								geometry,
								selected
						)
				);
				totalPaths += 14;
			}
		}
		return new Corpus(
				List.copyOf(scenarios),
				totalPaths,
				cacheHits,
				cacheMisses
		);
	}

	private static Geometry geometry(Pair pair) {
		Path voxelDirect = DdaFirstOrderPathSolver.direct(
				pair.source(),
				pair.microphone(),
				VOXEL_BOUNDS,
				MAXIMUM_CELLS_PER_LEG
		);
		Path continuousDirect = DdaFirstOrderPathSolver.direct(
				pair.source(),
				pair.microphone(),
				CONTINUOUS_BOUNDS,
				MAXIMUM_CELLS_PER_LEG
		);
		List<Path> voxelReflections = new ArrayList<>();
		List<Path> continuousReflections = new ArrayList<>();
		for (Facet facet : Facet.values()) {
			voxelReflections.add(
					DdaFirstOrderPathSolver.reflected(
							pair.source(),
							pair.microphone(),
							VOXEL_BOUNDS,
							facet,
							MAXIMUM_CELLS_PER_LEG
					)
			);
			continuousReflections.add(
					DdaFirstOrderPathSolver.reflected(
							pair.source(),
							pair.microphone(),
							CONTINUOUS_BOUNDS,
							facet,
							MAXIMUM_CELLS_PER_LEG
					)
			);
		}
		return new Geometry(
				voxelDirect,
				List.copyOf(voxelReflections),
				continuousDirect,
				List.copyOf(continuousReflections)
		);
	}

	private static List<Facet> selectCandidates(
			String effectiveCode,
			List<Path> paths
	) {
		List<Candidate> candidates = new ArrayList<>();
		for (int index = 0; index < paths.size(); index++) {
			Path path = paths.get(index);
			double materialPrior = effectiveCode.charAt(index) == '1'
					? 1.0
					: 0.2;
			double score = materialPrior
					/ (path.lengthMeters() * path.lengthMeters());
			candidates.add(new Candidate(path.facet(), score));
		}
		candidates.sort(
				Comparator.comparingDouble(Candidate::score)
						.reversed()
						.thenComparingInt(candidate ->
								candidate.facet().ordinal())
		);
		return candidates.stream()
				.limit(CANDIDATE_LIMIT)
				.map(Candidate::facet)
				.toList();
	}

	private static Benchmark benchmark() {
		for (int index = 0; index < 20; index++) {
			runBenchmarkBatch();
		}
		long[] nanos = new long[300];
		long[] allocated = new long[300];
		ThreadMXBean bean = allocationBean();
		long threadId = Thread.currentThread().threadId();
		for (int index = 0; index < nanos.length; index++) {
			long beforeAllocated = bean == null
					? -1L
					: bean.getThreadAllocatedBytes(threadId);
			long started = System.nanoTime();
			double checksum = runBenchmarkBatch();
			nanos[index] = System.nanoTime() - started;
			long afterAllocated = bean == null
					? -1L
					: bean.getThreadAllocatedBytes(threadId);
			allocated[index] = bean == null
					? -1L
					: afterAllocated - beforeAllocated
							+ (long) (checksum * 0.0);
		}
		Arrays.sort(nanos);
		if (bean != null) {
			Arrays.sort(allocated);
		}
		return new Benchmark(
				nanos.length,
				66,
				7,
				percentile(nanos, 0.50) / 66.0,
				percentile(nanos, 0.95) / 66.0,
				percentile(nanos, 0.99) / 66.0,
				bean == null
						? null
						: percentile(allocated, 0.50) / 66.0,
				bean == null
						? null
						: percentile(allocated, 0.95) / 66.0,
				bean != null
		);
	}

	private static double runBenchmarkBatch() {
		double checksum = 0.0;
		for (String ignored : ROOM_CODES) {
			for (Pair pair : PAIRS) {
				Path direct = DdaFirstOrderPathSolver.direct(
						pair.source(),
						pair.microphone(),
						CONTINUOUS_BOUNDS,
						MAXIMUM_CELLS_PER_LEG
				);
				checksum += direct.lengthMeters() + direct.visitedCells();
				for (Facet facet : Facet.values()) {
					Path reflected = DdaFirstOrderPathSolver.reflected(
							pair.source(),
							pair.microphone(),
							CONTINUOUS_BOUNDS,
							facet,
							MAXIMUM_CELLS_PER_LEG
					);
					checksum += reflected.lengthMeters()
							+ reflected.visitedCells();
				}
			}
		}
		return checksum;
	}

	private static ThreadMXBean allocationBean() {
		java.lang.management.ThreadMXBean base =
				ManagementFactory.getThreadMXBean();
		if (!(base instanceof ThreadMXBean bean)
				|| !bean.isThreadAllocatedMemorySupported()) {
			return null;
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

	private static String render(
			Corpus corpus,
			Benchmark benchmark,
			String sourceReportSha256
	) {
		StringBuilder json = new StringBuilder(196_608);
		json.append("{\n")
				.append("  \"schema_version\": 1,\n")
				.append("  \"status\": \"valid-dechorate-cpu-dda-experiment\",\n")
				.append("  \"source_d115_report_sha256\": \"")
				.append(sourceReportSha256)
				.append("\",\n")
				.append("  \"sample_rate_hz\": 48000,\n")
				.append("  \"speed_of_sound_mps\": 346.98,\n")
				.append("  \"maximum_cells_per_leg\": ")
				.append(MAXIMUM_CELLS_PER_LEG)
				.append(",\n")
				.append("  \"candidate_policy\": {")
				.append("\"limit\":4,\"reflective_prior\":1.0,")
				.append("\"absorptive_prior\":0.2,")
				.append("\"score\":\"material_prior/path_length_squared\",")
				.append("\"fitted\":false},\n")
				.append("  \"geometry_cache\": {")
				.append("\"hits\":").append(corpus.cacheHits())
				.append(",\"misses\":").append(corpus.cacheMisses())
				.append(",\"hit_rate\":")
				.append(number(
						corpus.cacheHits()
								/ (double) (corpus.cacheHits()
										+ corpus.cacheMisses())
				))
				.append("},\n")
				.append("  \"benchmark\": ")
				.append(benchmarkJson(benchmark))
				.append(",\n")
				.append("  \"scenarios\": [\n");
		for (int index = 0; index < corpus.scenarios().size(); index++) {
			if (index > 0) {
				json.append(",\n");
			}
			json.append(scenarioJson(corpus.scenarios().get(index)));
		}
		json.append("\n  ],\n")
				.append("  \"summary\": {\"scenarios\":")
				.append(corpus.scenarios().size())
				.append(",\"dda_paths\":")
				.append(corpus.totalPaths())
				.append(",\"all_topology_visible\":")
				.append(allVisible(corpus.scenarios()))
				.append("},\n")
				.append("  \"captures_audio\": false,\n")
				.append("  \"physical_endpoint_opened\": false,\n")
				.append("  \"release_calibrated\": false,\n")
				.append("  \"claim_boundary\": ")
				.append("\"Real Java CPU DDA traversal of direct and first-order ")
				.append("shoebox legs for the hash-bound D115 coordinate subset. ")
				.append("The candidate score is an unfitted scheduling heuristic; ")
				.append("this report does not fit release materials, execute CUDA, ")
				.append("open an audio endpoint, or establish perceptual realism.\"\n")
				.append("}\n");
		return json.toString();
	}

	private static String scenarioJson(Scenario scenario) {
		StringBuilder json = new StringBuilder(2_048);
		Pair pair = scenario.pair();
		json.append("    {\"room_code\":\"")
				.append(scenario.roomCode())
				.append("\",\"effective_facet_code\":\"")
				.append(scenario.effectiveCode())
				.append("\",\"furniture\":")
				.append(scenario.furniture())
				.append(",\"split\":\"")
				.append(scenario.split())
				.append("\",\"source_id\":")
				.append(pair.sourceId())
				.append(",\"microphone_id\":")
				.append(pair.microphoneId())
				.append(",\"distance_rank\":")
				.append(pair.distanceRank())
				.append(",\"source_position_m\":")
				.append(vector(pair.source()))
				.append(",\"microphone_position_m\":")
				.append(vector(pair.microphone()))
				.append(",\"voxel_boundary\":")
				.append(geometryJson(
						scenario.geometry().voxelDirect(),
						scenario.geometry().voxelReflections()
				))
				.append(",\"continuous_boundary\":")
				.append(geometryJson(
						scenario.geometry().continuousDirect(),
						scenario.geometry().continuousReflections()
				))
				.append(",\"bounded_candidates\":{\"selected_facets\":[");
		for (int index = 0; index < scenario.selectedFacets().size(); index++) {
			if (index > 0) {
				json.append(',');
			}
			json.append('"')
					.append(facetName(scenario.selectedFacets().get(index)))
					.append('"');
		}
		json.append("]}}");
		return json.toString();
	}

	private static String geometryJson(Path direct, List<Path> reflections) {
		StringBuilder json = new StringBuilder(1_024);
		json.append("{\"direct\":")
				.append(pathJson(direct))
				.append(",\"reflections\":[");
		for (int index = 0; index < reflections.size(); index++) {
			if (index > 0) {
				json.append(',');
			}
			json.append(pathJson(reflections.get(index)));
		}
		return json.append("]}").toString();
	}

	private static String pathJson(Path path) {
		String facet = path.facet() == null
				? "direct"
				: facetName(path.facet());
		String point = path.reflectionPoint() == null
				? "null"
				: vector(path.reflectionPoint());
		return "{\"facet\":\"" + facet
				+ "\",\"length_m\":" + number(path.lengthMeters())
				+ ",\"arrival_sample\":"
				+ number(path.lengthMeters() / SPEED_OF_SOUND_MPS
						* SAMPLE_RATE_HZ)
				+ ",\"visited_cells\":" + path.visitedCells()
				+ ",\"topology_visible\":" + path.topologyVisible()
				+ ",\"reflection_point_m\":" + point + "}";
	}

	private static String benchmarkJson(Benchmark benchmark) {
		return "{\"batches\":" + benchmark.batches()
				+ ",\"scenarios_per_batch\":"
				+ benchmark.scenariosPerBatch()
				+ ",\"paths_per_scenario\":"
				+ benchmark.pathsPerScenario()
				+ ",\"p50_ns_per_scenario\":"
				+ number(benchmark.p50NanosPerScenario())
				+ ",\"p95_ns_per_scenario\":"
				+ number(benchmark.p95NanosPerScenario())
				+ ",\"p99_ns_per_scenario\":"
				+ number(benchmark.p99NanosPerScenario())
				+ ",\"allocation_supported\":"
				+ benchmark.allocationSupported()
				+ ",\"p50_allocated_bytes_per_scenario\":"
				+ nullableNumber(benchmark.p50AllocatedBytesPerScenario())
				+ ",\"p95_allocated_bytes_per_scenario\":"
				+ nullableNumber(benchmark.p95AllocatedBytesPerScenario())
				+ "}";
	}

	private static boolean allVisible(List<Scenario> scenarios) {
		for (Scenario scenario : scenarios) {
			Geometry geometry = scenario.geometry();
			if (!geometry.voxelDirect().topologyVisible()
					|| !geometry.continuousDirect().topologyVisible()) {
				return false;
			}
			for (Path path : geometry.voxelReflections()) {
				if (!path.topologyVisible()) {
					return false;
				}
			}
			for (Path path : geometry.continuousReflections()) {
				if (!path.topologyVisible()) {
					return false;
				}
			}
		}
		return true;
	}

	private static String effectiveCode(String roomCode) {
		return roomCode.equals("020002") ? "010001" : roomCode;
	}

	private static String split(String roomCode) {
		return switch (roomCode) {
			case "011110", "011111", "020002" -> "holdout";
			default -> "discovery";
		};
	}

	private static String facetName(Facet facet) {
		return facet.name().toLowerCase(Locale.ROOT);
	}

	private static String vector(AcousticVector vector) {
		return "[" + number(vector.x()) + "," + number(vector.y())
				+ "," + number(vector.z()) + "]";
	}

	private static String number(double value) {
		return String.format(Locale.ROOT, "%.17g", value);
	}

	private static String nullableNumber(Double value) {
		return value == null ? "null" : number(value);
	}

	private static String sha256(byte[] bytes) {
		try {
			return java.util.HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(bytes)
			);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 unavailable", exception);
		}
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private record Pair(
			int sourceId,
			int microphoneId,
			int distanceRank,
			AcousticVector source,
			AcousticVector microphone
	) {
	}

	private record Geometry(
			Path voxelDirect,
			List<Path> voxelReflections,
			Path continuousDirect,
			List<Path> continuousReflections
	) {
	}

	private record GeometryKey(int sourceId, int microphoneId) {
	}

	private record Scenario(
			String roomCode,
			String effectiveCode,
			boolean furniture,
			String split,
			Pair pair,
			Geometry geometry,
			List<Facet> selectedFacets
	) {
	}

	private record Candidate(Facet facet, double score) {
	}

	private record Corpus(
			List<Scenario> scenarios,
			int totalPaths,
			int cacheHits,
			int cacheMisses
	) {
	}

	private record Benchmark(
			int batches,
			int scenariosPerBatch,
			int pathsPerScenario,
			double p50NanosPerScenario,
			double p95NanosPerScenario,
			double p99NanosPerScenario,
			Double p50AllocatedBytesPerScenario,
			Double p95AllocatedBytesPerScenario,
			boolean allocationSupported
	) {
	}
}
