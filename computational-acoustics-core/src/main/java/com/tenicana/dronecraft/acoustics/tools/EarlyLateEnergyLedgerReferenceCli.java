package com.tenicana.dronecraft.acoustics.tools;

import com.sun.management.ThreadMXBean;
import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticMaterial;
import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.propagation.BoundedFirstOrderGainSolver;
import com.tenicana.dronecraft.acoustics.propagation.DdaFirstOrderBatchSolver;
import com.tenicana.dronecraft.acoustics.propagation.DdaFirstOrderPathSolver.RoomBounds;
import com.tenicana.dronecraft.acoustics.propagation.EarlyLateEnergyLedger;
import com.tenicana.dronecraft.acoustics.reverb.LateReverbEstimator;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** Emits deterministic D121 early/late energy-accounting evidence. */
public final class EarlyLateEnergyLedgerReferenceCli {
	private static final AcousticBands[] BUDGETS = {
			new AcousticBands(0.30, 0.20, 0.10),
			new AcousticBands(0.02, 0.03, 0.04),
			AcousticBands.SILENT
	};
	private static final double[] OPENNESS = {0.10, 0.20, 1.0};

	private EarlyLateEnergyLedgerReferenceCli() {
	}

	public static void main(String[] args) throws IOException {
		Locale.setDefault(Locale.ROOT);
		if (args.length != 2) {
			throw new IllegalArgumentException(
					"usage: <output-json> <D121-contract-json>"
			);
		}
		Path output = Path.of(args[0]);
		Path contract = Path.of(args[1]);
		BoundedFirstOrderGainSolver.Workspace gains = gains();
		EarlyLateEnergyLedger.Workspace ledger =
				new EarlyLateEnergyLedger.Workspace();
		StringBuilder scenarios = new StringBuilder("[");
		for (int scenario = 0; scenario < BUDGETS.length; scenario++) {
			if (scenario > 0) {
				scenarios.append(',');
			}
			EarlyLateEnergyLedger.partition(
					gains,
					environment(scenario),
					ledger
			);
			scenarios.append(String.format(
					Locale.ROOT,
					"{\"openness\":%.17g,\"environment_budget\":%s,"
							+ "\"explicit_candidate\":%s,"
							+ "\"explicit_allocated\":%s,"
							+ "\"explicit_rejected\":%s,"
							+ "\"late_residual\":%s,"
							+ "\"late_wet_gain\":%.17g}",
					OPENNESS[scenario],
					bands(BUDGETS[scenario]),
					ledgerValues(ledger, 0),
					ledgerValues(ledger, 1),
					ledgerValues(ledger, 2),
					ledgerValues(ledger, 3),
					ledger.lateWetGain()
			));
		}
		scenarios.append(']');
		Benchmark benchmark = benchmark(gains, ledger);
		String report = String.format(
				Locale.ROOT,
				"""
				{
				  "schema_version": 1,
				  "status": "valid-minecraft-early-late-energy-ledger-reference",
				  "source_contract_sha256": "%s",
				  "scenarios": %s,
				  "benchmark": {
				    "partitions_per_window": %d,
				    "allocation_windows_bytes": %s,
				    "median_allocated_bytes_per_partition": %.17g,
				    "p99_ns_per_partition": %.17g,
				    "checksum": %.17g
				  },
				  "gates": {
				    "three_scenarios": true,
				    "zero_allocation_hot_path": %s,
				    "p99_below_10_microseconds": %s
				  },
				  "captures_audio": false,
				  "physical_endpoint_opened": false,
				  "cuda_executed": false,
				  "minecraft_integration_enabled": false,
				  "release_calibrated": false
				}
				""",
				sha256(Files.readAllBytes(contract)),
				scenarios,
				benchmark.partitionsPerWindow(),
				longArray(benchmark.allocationWindows()),
				benchmark.allocatedBytesPerPartition(),
				benchmark.p99NanosPerPartition(),
				benchmark.checksum(),
				benchmark.allocatedBytesPerPartition() == 0.0,
				benchmark.p99NanosPerPartition() <= 10_000.0
		);
		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.writeString(output, report, StandardCharsets.UTF_8);
		System.out.printf(
				"{\"status\":\"valid-minecraft-early-late-energy-ledger-reference\","
						+ "\"allocation_bytes\":%.17g,\"p99_ns\":%.17g}%n",
				benchmark.allocatedBytesPerPartition(),
				benchmark.p99NanosPerPartition()
		);
	}

	private static Benchmark benchmark(
			BoundedFirstOrderGainSolver.Workspace gains,
			EarlyLateEnergyLedger.Workspace ledger
	) {
		LateReverbEstimator.Parameters[] environments = {
				environment(0), environment(1), environment(2)
		};
		for (int iteration = 0; iteration < 200_000; iteration++) {
			EarlyLateEnergyLedger.partition(
					gains, environments[iteration % 3], ledger
			);
		}
		ThreadMXBean bean = allocationBean();
		long threadId = Thread.currentThread().threadId();
		int partitions = 100_000;
		long[] allocation = new long[5];
		double checksum = 0.0;
		for (int window = 0; window < allocation.length; window++) {
			long before = bean.getThreadAllocatedBytes(threadId);
			for (int iteration = 0; iteration < partitions; iteration++) {
				EarlyLateEnergyLedger.partition(
						gains, environments[iteration % 3], ledger
				);
				checksum += ledger.lateWetGain()
						+ ledger.explicitAllocated(iteration % 3);
			}
			allocation[window] =
					bean.getThreadAllocatedBytes(threadId) - before;
		}
		long[] elapsed = new long[400];
		double latencyChecksum = 0.0;
		for (int batch = 0; batch < elapsed.length; batch++) {
			long started = System.nanoTime();
			for (int iteration = 0; iteration < 128; iteration++) {
				EarlyLateEnergyLedger.partition(
						gains, environments[iteration % 3], ledger
				);
				latencyChecksum += ledger.lateWetGain()
						+ ledger.lateResidual(iteration % 3);
			}
			elapsed[batch] = System.nanoTime() - started;
		}
		checksum += latencyChecksum;
		java.util.Arrays.sort(elapsed);
		if (!Double.isFinite(checksum) || checksum <= 0.0) {
			throw new IllegalStateException("benchmark checksum changed");
		}
		long medianAllocation = allocation.clone()[0];
		long[] sortedAllocation = allocation.clone();
		java.util.Arrays.sort(sortedAllocation);
		medianAllocation = sortedAllocation[sortedAllocation.length / 2];
		return new Benchmark(
				partitions,
				allocation,
				medianAllocation / (double) partitions,
				percentile(elapsed, 0.99) / 128.0,
				checksum
		);
	}

	private static BoundedFirstOrderGainSolver.Workspace gains() {
		DdaFirstOrderBatchSolver.Workspace paths =
				new DdaFirstOrderBatchSolver.Workspace();
		DdaFirstOrderBatchSolver.solve(
				1.6330509, 0.6820041, 1.16493109,
				3.50149512, 2.61934068, 1.38561103,
				new RoomBounds(5.705, 5.965, 2.355),
				32,
				paths
		);
		AcousticMaterial[] materials = {
				AcousticMaterials.STONE,
				AcousticMaterials.SOFT,
				AcousticMaterials.WOOD,
				AcousticMaterials.GLASS,
				AcousticMaterials.METAL,
				AcousticMaterials.FOLIAGE
		};
		double[] zero = new double[6];
		BoundedFirstOrderGainSolver.Workspace gains =
				new BoundedFirstOrderGainSolver.Workspace();
		BoundedFirstOrderGainSolver.solve(
				paths, materials, zero, zero, zero, false, gains
		);
		return gains;
	}

	private static LateReverbEstimator.Parameters environment(int scenario) {
		return new LateReverbEstimator.Parameters(
				scenario + 1,
				OPENNESS[scenario],
				3.0,
				0.3,
				new AcousticBands(0.8, 0.6, 0.4),
				new AcousticBands(0.5, 0.4, 0.3),
				new AcousticBands(5.0, 6.0, 7.0),
				BUDGETS[scenario]
		);
	}

	private static String ledgerValues(
			EarlyLateEnergyLedger.Workspace ledger,
			int field
	) {
		double[] values = new double[3];
		for (int band = 0; band < 3; band++) {
			values[band] = switch (field) {
				case 0 -> ledger.explicitCandidate(band);
				case 1 -> ledger.explicitAllocated(band);
				case 2 -> ledger.explicitRejected(band);
				case 3 -> ledger.lateResidual(band);
				default -> throw new IllegalStateException();
			};
		}
		return doubles(values);
	}

	private static String bands(AcousticBands bands) {
		return doubles(new double[] {bands.low(), bands.mid(), bands.high()});
	}

	private static String doubles(double[] values) {
		return String.format(
				Locale.ROOT,
				"[%.17g,%.17g,%.17g]",
				values[0], values[1], values[2]
		);
	}

	private static String longArray(long[] values) {
		return String.format(
				Locale.ROOT,
				"[%d,%d,%d,%d,%d]",
				values[0], values[1], values[2], values[3], values[4]
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
			int partitionsPerWindow,
			long[] allocationWindows,
			double allocatedBytesPerPartition,
			double p99NanosPerPartition,
			double checksum
	) {
	}
}
