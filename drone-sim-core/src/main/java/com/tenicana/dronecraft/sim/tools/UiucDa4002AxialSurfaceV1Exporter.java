package com.tenicana.dronecraft.sim.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.tenicana.dronecraft.sim.UiucDa4002AxialSurfaceV1;
import com.tenicana.dronecraft.sim.UiucDa4002AxialSurfaceV1.PropellerEnvelope;
import com.tenicana.dronecraft.sim.UiucDa4002AxialSurfaceV1.ReferenceSlice;
import com.tenicana.dronecraft.sim.UiucDa4002MeasuredRotorCrossValidation;
import com.tenicana.dronecraft.sim.UiucDa4002MeasuredRotorCrossValidation.ResidualSample;
import com.tenicana.dronecraft.sim.UiucDa4002MeasuredRotorModel.NominalTrackEnvelope;
import com.tenicana.dronecraft.sim.UiucDa4002PublishedCfdComparison;
import com.tenicana.dronecraft.sim.UiucDa4002PublishedCfdComparison.CfxComparison;
import com.tenicana.dronecraft.sim.UiucDa4002PublishedCfdComparison.CfxTurbulenceModel;

/** Writes the deterministic curve bundle and evidence summary for axial surface v1. */
public final class UiucDa4002AxialSurfaceV1Exporter {
	public static final String MANIFEST_FILE_NAME = "manifest.md";
	public static final String CHECKSUM_FILE_NAME = "checksums.sha256";
	public static final String CURVE_DIRECTORY_NAME = "curves";

	private UiucDa4002AxialSurfaceV1Exporter() {
	}

	public static void main(String[] args) throws IOException {
		Path outputDirectory = args.length >= 1 && !args[0].isBlank()
				? Path.of(args[0])
				: Path.of("build", "uiuc-da4002-axial-surface-v1");
		write(outputDirectory);
	}

	public static BundleReport write(Path outputDirectory) throws IOException {
		if (outputDirectory == null) {
			throw new IllegalArgumentException("outputDirectory must not be null.");
		}
		Path curveDirectory = outputDirectory.resolve(CURVE_DIRECTORY_NAME);
		Files.createDirectories(curveDirectory);
		List<CurveArtifact> artifacts = new ArrayList<>();
		for (ReferenceSlice slice : UiucDa4002AxialSurfaceV1.referenceSlices()) {
			List<String> lines = UiucDa4002MeasuredRotorCurveExporter.csvLines(
					slice.propeller(),
					slice.rpm(),
					UiucDa4002AxialSurfaceV1.REFERENCE_ADVANCE_RATIO_STEP,
					UiucDa4002AxialSurfaceV1.REFERENCE_MAXIMUM_ADVANCE_RATIO,
					UiucDa4002AxialSurfaceV1.REFERENCE_AIR_DENSITY_KG_PER_CUBIC_METER,
					UiucDa4002AxialSurfaceV1
							.REFERENCE_DYNAMIC_VISCOSITY_PASCAL_SECONDS
			);
			String content = String.join("\n", lines) + "\n";
			String fileName = curveFileName(slice);
			Files.writeString(curveDirectory.resolve(fileName), content,
					StandardCharsets.UTF_8);
			artifacts.add(new CurveArtifact(
					CURVE_DIRECTORY_NAME + "/" + fileName,
					slice,
					lines.size() - 1,
					UiucDa4002AxialSurfaceV1.sha256(content)
			));
		}
		String curveBundleSha256 = curveBundleSha256(artifacts);
		BundleReport report = new BundleReport(artifacts, curveBundleSha256);
		Files.writeString(
				outputDirectory.resolve(CHECKSUM_FILE_NAME),
				String.join("\n", checksumLines(report)) + "\n",
				StandardCharsets.US_ASCII
		);
		Files.writeString(
				outputDirectory.resolve(MANIFEST_FILE_NAME),
				String.join("\n", manifestMarkdownLines(report)) + "\n",
				StandardCharsets.UTF_8
		);
		return report;
	}

	public static List<String> checksumLines(BundleReport report) {
		if (report == null) {
			throw new IllegalArgumentException("report must not be null.");
		}
		return report.curveArtifacts().stream()
				.map(artifact -> artifact.sha256() + "  " + artifact.relativePath())
				.toList();
	}

	public static List<String> manifestMarkdownLines(BundleReport bundle) {
		if (bundle == null) {
			throw new IllegalArgumentException("bundle must not be null.");
		}
		UiucDa4002MeasuredRotorCrossValidation.Report internal =
				UiucDa4002MeasuredRotorCrossValidation.analyze();
		UiucDa4002PublishedCfdComparison.Report external =
				UiucDa4002PublishedCfdComparison.compare();
		List<String> lines = new ArrayList<>();
		lines.add("# UIUC DA4002 Axial Surface V1");
		lines.add("");
		lines.add("- Version: `" + UiucDa4002AxialSurfaceV1.VERSION_ID + "`");
		lines.add("- Source-data SHA-256: `"
				+ UiucDa4002AxialSurfaceV1.sourceDataSha256() + "`");
		lines.add("- Interpolation algorithm SHA-256: `"
				+ UiucDa4002AxialSurfaceV1.interpolationAlgorithmSha256() + "`");
		lines.add("- Curve-bundle SHA-256: `" + bundle.curveBundleSha256() + "`");
		lines.add("- Source rows: `" + UiucDa4002AxialSurfaceV1.staticSourceRowCount()
				+ " static + " + UiucDa4002AxialSurfaceV1.advanceSourceRowCount()
				+ " advancing`");
		lines.add("");
		lines.add("## Algorithm");
		lines.add("");
		lines.add("`" + UiucDa4002AxialSurfaceV1.INTERPOLATION_ALGORITHM_ID + "`");
		lines.add("");
		lines.add("Queries outside the non-rectangular measured J/RPM surface block. "
				+ "V1 never clamps or extrapolates.");
		lines.add("");
		lines.add("## Query Envelope");
		lines.add("");
		lines.add("| Propeller | Diameter (m) | Static RPM | Forward RPM | "
				+ "Nominal track max J | Static Re75 | Forward rotational Re75 | "
				+ "Forward resultant Re75 |");
		lines.add("| --- | ---: | ---: | ---: | --- | ---: | ---: | ---: |");
		for (var propeller : com.tenicana.dronecraft.sim
				.UiucDa4002MeasuredRotorModel.Propeller.values()) {
			PropellerEnvelope envelope = UiucDa4002AxialSurfaceV1.envelope(propeller);
			var re = envelope.referenceReynoldsEnvelope();
			lines.add(String.format(Locale.ROOT,
					"| %s | %s | %s-%s | %s-%s | %s | %s-%s | %s-%s | %s-%s |",
					propeller.id(),
					number(envelope.diameterMeters()),
					number(envelope.staticRpmEnvelope().minimumRpm()),
					number(envelope.staticRpmEnvelope().maximumRpm()),
					number(envelope.minimumForwardRpm()),
					number(envelope.maximumForwardRpm()),
					trackMaximums(envelope.nominalTrackEnvelopes()),
					number(re.minimumStaticRotationalReynolds75()),
					number(re.maximumStaticRotationalReynolds75()),
					number(re.minimumForwardRotationalReynolds75()),
					number(re.maximumForwardRotationalReynolds75()),
					number(re.minimumForwardResultantReynolds75()),
					number(re.maximumForwardResultantReynolds75())));
		}
		lines.add("");
		lines.add("Re75 ranges use `rho="
				+ number(UiucDa4002AxialSurfaceV1
						.REFERENCE_AIR_DENSITY_KG_PER_CUBIC_METER)
				+ " kg/m^3` and `mu="
				+ number(UiucDa4002AxialSurfaceV1
						.REFERENCE_DYNAMIC_VISCOSITY_PASCAL_SECONDS)
				+ " Pa*s`; they describe the frozen reference export and are diagnostic, "
				+ "not an independent interpolation axis.");
		lines.add("");
		lines.add("At an RPM between nominal tracks, maximum supported J is the "
				+ "smaller adjacent-track maximum. J=0 uses the wider static RPM envelope.");
		lines.add("");
		lines.add("## Deterministic Curves");
		lines.add("");
		lines.add("The bundle contains `" + bundle.curveArtifacts().size()
				+ "` nominal/midpoint RPM slices at `delta J="
				+ number(UiucDa4002AxialSurfaceV1.REFERENCE_ADVANCE_RATIO_STEP)
				+ "` through `J="
				+ number(UiucDa4002AxialSurfaceV1.REFERENCE_MAXIMUM_ADVANCE_RATIO)
				+ "`, including explicit blocked rows.");
		lines.add("");
		lines.add("## Internal Interpolation Error");
		lines.add("");
		lines.add("- Supported residual samples: `" + internal.residualSamples().size()
				+ "`; blocked nominal-track candidates: `"
				+ internal.blockedCandidates().size() + "`.");
		lines.add("- Maximum absolute CT/CP residual: `"
				+ number(maximum(internal.residualSamples(), ResidualSample::absoluteCtResidual))
				+ " / "
				+ number(maximum(internal.residualSamples(), ResidualSample::absoluteCpResidual))
				+ "`.");
		lines.add("- Maximum absolute T/P/Q residual: `"
				+ number(maximum(internal.residualSamples(), sample -> Math.abs(
						sample.signedThrustResidualNewtons()))) + " N / "
				+ number(maximum(internal.residualSamples(), sample -> Math.abs(
						sample.signedPowerResidualWatts()))) + " W / "
				+ number(maximum(internal.residualSamples(), sample -> Math.abs(
						sample.signedTorqueResidualNewtonMeters()))) + " Nm`.");
		lines.add("");
		lines.add("## Published CFD Comparison");
		lines.add("");
		lines.add("| CFX model | Rows | Mean abs CT | Max abs CT | Mean abs CP | "
				+ "Max abs CP | Max abs T (N) | Max abs P (W) | Max abs Q (Nm) |");
		lines.add("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |");
		for (CfxTurbulenceModel model : CfxTurbulenceModel.values()) {
			ExternalAggregate aggregate = aggregate(external, model);
			lines.add(String.format(Locale.ROOT,
					"| %s | %d | %s | %s | %s | %s | %s | %s | %s |",
					model.name(), aggregate.count(),
					number(aggregate.meanAbsoluteCt()),
					number(aggregate.maximumAbsoluteCt()),
					number(aggregate.meanAbsoluteCp()),
					number(aggregate.maximumAbsoluteCp()),
					number(aggregate.maximumAbsoluteThrustNewtons()),
					number(aggregate.maximumAbsolutePowerWatts()),
					number(aggregate.maximumAbsoluteTorqueNewtonMeters())));
		}
		lines.add("");
		lines.add("External CFD coverage is limited to DA4002 9x6.75, 2000 RPM, "
				+ "and `0 <= J <= 0.6`. It does not independently validate the 5x3.75, "
				+ "oblique flow, or reverse flow, and it is not used as a fit target.");
		return List.copyOf(lines);
	}

	private static String curveFileName(ReferenceSlice slice) {
		return slice.propeller().id() + "-rpm"
				+ String.format(Locale.ROOT, "%.0f", slice.rpm()) + ".csv";
	}

	private static String curveBundleSha256(List<CurveArtifact> artifacts) {
		StringBuilder canonical = new StringBuilder();
		for (CurveArtifact artifact : artifacts) {
			canonical.append(artifact.relativePath()).append('=')
					.append(artifact.sha256()).append('\n');
		}
		return UiucDa4002AxialSurfaceV1.sha256(canonical.toString());
	}

	private static String trackMaximums(List<NominalTrackEnvelope> tracks) {
		return tracks.stream()
				.map(track -> String.format(Locale.ROOT, "%s:%s",
						number(track.nominalRpm()),
						number(track.maximumSupportedAdvanceRatioJ())))
				.collect(java.util.stream.Collectors.joining(", "));
	}

	private static ExternalAggregate aggregate(
			UiucDa4002PublishedCfdComparison.Report report,
			CfxTurbulenceModel model
	) {
		List<CfxComparison> rows = report.cfxComparisons().stream()
				.filter(row -> row.published().turbulenceModel() == model)
				.toList();
		return new ExternalAggregate(
				rows.size(),
				mean(rows, row -> Math.abs(row.modelMinusPublishedCt())),
				maximum(rows, row -> Math.abs(row.modelMinusPublishedCt())),
				mean(rows, row -> Math.abs(row.modelMinusPublishedCp())),
				maximum(rows, row -> Math.abs(row.modelMinusPublishedCp())),
				maximum(rows, row -> Math.abs(
						row.modelMinusPublishedNormalizedThrustNewtons())),
				maximum(rows, row -> Math.abs(
						row.modelMinusPublishedNormalizedPowerWatts())),
				maximum(rows, row -> Math.abs(
						row.modelMinusPublishedNormalizedTorqueNewtonMeters()))
		);
	}

	private static <T> double mean(List<T> rows, Metric<T> metric) {
		return rows.stream().mapToDouble(metric::value).average().orElseThrow();
	}

	private static <T> double maximum(List<T> rows, Metric<T> metric) {
		return rows.stream().mapToDouble(metric::value).max().orElseThrow();
	}

	private static String number(double value) {
		return String.format(Locale.ROOT, "%.15g", value);
	}

	@FunctionalInterface
	private interface Metric<T> {
		double value(T row);
	}

	public record CurveArtifact(
			String relativePath,
			ReferenceSlice slice,
			int sampleCount,
			String sha256
	) {
	}

	public record BundleReport(
			List<CurveArtifact> curveArtifacts,
			String curveBundleSha256
	) {
		public BundleReport {
			curveArtifacts = List.copyOf(curveArtifacts);
		}
	}

	private record ExternalAggregate(
			int count,
			double meanAbsoluteCt,
			double maximumAbsoluteCt,
			double meanAbsoluteCp,
			double maximumAbsoluteCp,
			double maximumAbsoluteThrustNewtons,
			double maximumAbsolutePowerWatts,
			double maximumAbsoluteTorqueNewtonMeters
	) {
	}
}
