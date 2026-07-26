package com.tenicana.dronecraft.acoustics.tools;

import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.diffraction.UdfaFiniteWedgeFilter;
import com.tenicana.dronecraft.acoustics.diffraction.InfiniteWedgeGeometry;
import com.tenicana.dronecraft.acoustics.diffraction.UdfaInfiniteWedgeFilter;
import com.tenicana.dronecraft.acoustics.diffraction.UdfaSerialShelvingIirDesigner;
import com.tenicana.dronecraft.acoustics.diffraction.VoxelDiffractionEdge;
import com.tenicana.dronecraft.acoustics.diffraction.VoxelWedgeGeometryMapper;
import com.tenicana.dronecraft.acoustics.voxel.VoxelDda;

import java.util.List;
import java.util.Locale;

/**
 * Error and sample-throughput baseline for the four-shelf UDFA IIR design.
 */
public final class UdfaIirBenchmark {
	private static final double SAMPLE_RATE = 48_000.0;
	private static final int RESPONSE_POINTS = 192;
	private static final int WARMUP_SAMPLES = 100_000;

	private UdfaIirBenchmark() {
	}

	public static void main(String[] arguments) {
		int measuredSamples = arguments.length == 0
				? 2_000_000
				: Integer.parseInt(arguments[0]);
		UdfaInfiniteWedgeFilter target = new UdfaInfiniteWedgeFilter(
				UdfaInfiniteWedgeFilter.Parameters.published2023()
		);
		List<InfiniteWedgeGeometry> geometries = List.of(
				geometry(1.0, 1.0, 0.15),
				geometry(2.0, 3.0, 0.60),
				geometry(8.0, 2.0, 1.20),
				geometry(24.0, 12.0, 1.50)
		);
		UdfaSerialShelvingIirDesigner.Design throughputDesign = null;
		for (int index = 0; index < geometries.size(); index++) {
			InfiniteWedgeGeometry geometry = geometries.get(index);
			for (int shelfCount : new int[] {4, 6}) {
				UdfaSerialShelvingIirDesigner.Design design =
						UdfaSerialShelvingIirDesigner.design(
								target, geometry, SAMPLE_RATE, shelfCount
						);
				ErrorMetrics error = error(target, design, geometry);
				System.out.printf(
						Locale.ROOT,
						"geometry=%d shelves=%d ds=%.1f dr=%.1f bend=%.2f rad rms=%.3f dB max=%.3f dB%n",
						index,
						shelfCount,
						geometry.sourceDistanceMeters(),
						geometry.receiverDistanceMeters(),
						geometry.receiverAzimuthRadians() - Math.PI,
						error.rmsDb(),
						error.maxAbsoluteDb()
				);
				if (shelfCount == 4) {
					throughputDesign = design;
				}
			}
		}

		UdfaSerialShelvingIirDesigner.Processor processor =
				throughputDesign.newProcessor();
		double sink = 0.0;
		for (int index = 0; index < WARMUP_SAMPLES; index++) {
			sink += processor.process((index & 1) == 0 ? 0.25 : -0.25);
		}
		long started = System.nanoTime();
		for (int index = 0; index < measuredSamples; index++) {
			sink += processor.process((index & 1) == 0 ? 0.25 : -0.25);
		}
		long elapsed = System.nanoTime() - started;
		System.out.printf(
				Locale.ROOT,
				"samples=%d ns/sample=%.2f realtime-48k-channels=%.1f sink=%.6g%n",
				measuredSamples,
				(double) elapsed / measuredSamples,
				measuredSamples / (elapsed / 1.0e9) / SAMPLE_RATE,
				sink
		);
		System.out.printf(
				Locale.ROOT,
				"finite-edge endpoint-scan frequency=1000Hz step=0.005m max-step=%.6f dB%n",
				finiteEdgeMaximumStepDb()
		);
	}

	private static double finiteEdgeMaximumStepDb() {
		VoxelDiffractionEdge edge = new VoxelDiffractionEdge(
				2,
				new VoxelDda.Cell(1, 0, 3),
				new VoxelDda.Cell(2, 0, 2),
				new AcousticVector(2.0, 0.5, 3.0),
				new VoxelDiffractionEdge.AxisDirection(0, 1, 0),
				new VoxelDiffractionEdge.AxisDirection(0, 0, 1),
				new VoxelDiffractionEdge.AxisDirection(1, 0, 0)
		);
		UdfaFiniteWedgeFilter finite = new UdfaFiniteWedgeFilter(
				UdfaInfiniteWedgeFilter.Parameters.published2023()
		);
		double previous = Double.NaN;
		double maximum = 0.0;
		for (int index = 0; index <= 80; index++) {
			double sourceAxial = 0.8 + index * 0.005;
			VoxelWedgeGeometryMapper.Result mapped =
					VoxelWedgeGeometryMapper.map(
							edge,
							new AcousticVector(2.0, 0.5 + sourceAxial, 1.0),
							new AcousticVector(4.0, 0.5, 3.0),
							343.0
					).orElseThrow();
			double magnitude = finite.pressureMagnitude(1_000.0, mapped);
			if (Double.isFinite(previous)) {
				maximum = Math.max(
						maximum,
						Math.abs(20.0 * Math.log10(magnitude / previous))
				);
			}
			previous = magnitude;
		}
		return maximum;
	}

	private static InfiniteWedgeGeometry geometry(
			double sourceDistance,
			double receiverDistance,
			double bendRadians
	) {
		return new InfiniteWedgeGeometry(
				sourceDistance,
				receiverDistance,
				0.0,
				Math.PI + bendRadians,
				2.0 * Math.PI,
				Math.PI / 2.0,
				343.0
		);
	}

	private static ErrorMetrics error(
			UdfaInfiniteWedgeFilter target,
			UdfaSerialShelvingIirDesigner.Design design,
			InfiniteWedgeGeometry geometry
	) {
		double squared = 0.0;
		double maximum = 0.0;
		for (int index = 0; index < RESPONSE_POINTS; index++) {
			double frequency = 20.0 * Math.pow(
					16_000.0 / 20.0,
					(double) index / (RESPONSE_POINTS - 1)
			);
			double targetMagnitude = target.pressureMagnitude(frequency, geometry);
			double actualMagnitude = design.pressureMagnitude(frequency);
			double errorDb = 20.0 * Math.log10(actualMagnitude / targetMagnitude);
			squared += errorDb * errorDb;
			maximum = Math.max(maximum, Math.abs(errorDb));
		}
		return new ErrorMetrics(
				Math.sqrt(squared / RESPONSE_POINTS),
				maximum
		);
	}

	private record ErrorMetrics(double rmsDb, double maxAbsoluteDb) {
	}
}
