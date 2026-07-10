package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * Compares the measured DA4002 surface with published 2000-RPM CFD results
 * from Oliveira's 2019 UFJF thesis. Values are transcribed at the precision
 * printed in Tables 11 and 17; the source PDF is not vendored.
 */
public final class UiucDa4002PublishedCfdComparison {
	public static final String DATA_SOURCE_ID = "ufjf-oliveira-2019-da4002-cfd";
	public static final String SOURCE_TITLE =
			"Simulacao em Dinamica dos Fluidos Computacional de Helices";
	public static final String SOURCE_URL =
			"https://repositorio.ufjf.br/jspui/bitstream/123456789/10185/1/"
					+ "nicolaslimaoliveira.pdf";
	public static final String SOURCE_PDF_SHA256 =
			"53b8f5e439778fb30049a26258269d2298be7997758e9fd8c093d6388ae9e37a";
	public static final String CFX_SOURCE_LOCATOR = "Table 11 (printed page 84)";
	public static final String OPENFOAM_SOURCE_LOCATOR = "Table 17 (printed page 94)";
	public static final double SOURCE_RPM = 2_000.0;
	public static final double SOURCE_DIAMETER_METERS = 0.2286;
	public static final double INFERRED_AIR_DENSITY_KG_PER_CUBIC_METER = 1.18;
	public static final double DYNAMIC_VISCOSITY_PASCAL_SECONDS = 1.81e-5;
	public static final double SOURCE_LOAD_CLOSURE_RELATIVE_TOLERANCE = 0.03;
	private static final UiucDa4002MeasuredRotorModel.Propeller PROPELLER =
			UiucDa4002MeasuredRotorModel.Propeller.DA4002_9X6_75;

	private static final List<PublishedCfxRow> CFX_ROWS = List.of(
			row(CfxTurbulenceModel.K_EPSILON, 0.0, 0.00, 0.496, 0.0088,
					0.139, 0.0676, 0.00),
			row(CfxTurbulenceModel.K_EPSILON, 0.2, 1.52, 0.430, 0.0083,
					0.120, 0.0639, 0.38),
			row(CfxTurbulenceModel.K_EPSILON, 0.4, 3.05, 0.418, 0.0083,
					0.117, 0.0637, 0.73),
			row(CfxTurbulenceModel.K_EPSILON, 0.5, 3.81, 0.404, 0.0082,
					0.113, 0.0633, 0.89),
			row(CfxTurbulenceModel.K_EPSILON, 0.6, 4.57, 0.194, 0.0054,
					0.054, 0.0411, 0.79),
			row(CfxTurbulenceModel.K_OMEGA, 0.0, 0.00, 0.417, 0.0078,
					0.116, 0.0599, 0.00),
			// Table 11 prints 0.076 Nm. It is retained and fails source closure.
			row(CfxTurbulenceModel.K_OMEGA, 0.2, 1.52, 0.415, 0.076,
					0.116, 0.0583, 0.40),
			row(CfxTurbulenceModel.K_OMEGA, 0.4, 3.05, 0.332, 0.0075,
					0.093, 0.0576, 0.64),
			row(CfxTurbulenceModel.K_OMEGA, 0.5, 3.81, 0.266, 0.0066,
					0.074, 0.0507, 0.73),
			row(CfxTurbulenceModel.K_OMEGA, 0.6, 4.57, 0.194, 0.0054,
					0.0542, 0.0415, 0.78)
	);

	private UiucDa4002PublishedCfdComparison() {
	}

	public enum CfxTurbulenceModel {
		K_EPSILON,
		K_OMEGA
	}

	public record PublishedCfxRow(
			CfxTurbulenceModel turbulenceModel,
			double advanceRatioJ,
			double publishedFreestreamVelocityMetersPerSecond,
			double publishedThrustNewtons,
			double publishedTorqueNewtonMeters,
			double publishedThrustCoefficientKt,
			double publishedPowerCoefficientKp,
			double publishedEfficiency
	) {
	}

	public record NormalizedLoads(
			double thrustNewtons,
			double shaftPowerWatts,
			double shaftTorqueNewtonMeters
	) {
	}

	public record CfxComparison(
			PublishedCfxRow published,
			UiucDa4002MeasuredRotorModel.DimensionalSample measuredModel,
			NormalizedLoads publishedCoefficientLoads
	) {
		public double expectedFreestreamVelocityMetersPerSecond() {
			return published.advanceRatioJ()
					* (SOURCE_RPM / 60.0)
					* SOURCE_DIAMETER_METERS;
		}

		public double publishedFreestreamClosureResidualMetersPerSecond() {
			return published.publishedFreestreamVelocityMetersPerSecond()
					- expectedFreestreamVelocityMetersPerSecond();
		}

		public double inferredDensityFromPublishedThrust() {
			return UiucDa4002PublishedCfdComparison
					.inferredDensityFromPublishedThrust(published);
		}

		public double inferredDensityFromPublishedTorque() {
			return UiucDa4002PublishedCfdComparison
					.inferredDensityFromPublishedTorque(published);
		}

		public double sourceDensityClosureResidualFraction() {
			return inferredDensityFromPublishedTorque()
					/ inferredDensityFromPublishedThrust() - 1.0;
		}

		public boolean publishedTorqueCoefficientClosureSatisfied() {
			return Math.abs(sourceDensityClosureResidualFraction())
					<= SOURCE_LOAD_CLOSURE_RELATIVE_TOLERANCE;
		}

		public double publishedEtaClosureResidual() {
			double derived = published.advanceRatioJ()
					* published.publishedThrustCoefficientKt()
					/ published.publishedPowerCoefficientKp();
			return published.publishedEfficiency() - derived;
		}

		public double modelMinusPublishedCt() {
			return measuredModel.lookup().thrustCoefficientCt()
					- published.publishedThrustCoefficientKt();
		}

		public double modelMinusPublishedCp() {
			return measuredModel.lookup().powerCoefficientCp()
					- published.publishedPowerCoefficientKp();
		}

		public double modelMinusPublishedEta() {
			return measuredModel.lookup().propulsiveEfficiencyEta()
					- published.publishedEfficiency();
		}

		public double modelMinusPublishedNormalizedThrustNewtons() {
			return measuredModel.thrustNewtons() - publishedCoefficientLoads.thrustNewtons();
		}

		public double modelMinusPublishedNormalizedPowerWatts() {
			return measuredModel.shaftPowerWatts()
					- publishedCoefficientLoads.shaftPowerWatts();
		}

		public double modelMinusPublishedNormalizedTorqueNewtonMeters() {
			return measuredModel.shaftTorqueNewtonMeters()
					- publishedCoefficientLoads.shaftTorqueNewtonMeters();
		}

		public double modelMinusPublishedThrustNewtons() {
			return measuredModel.thrustNewtons() - published.publishedThrustNewtons();
		}

		public double modelMinusPublishedTorqueNewtonMeters() {
			return measuredModel.shaftTorqueNewtonMeters()
					- published.publishedTorqueNewtonMeters();
		}
	}

	public record OpenFoamSummaryComparison(
			double publishedStaticThrustCoefficientKt,
			double publishedStaticPowerCoefficientKp,
			double publishedMaximumEfficiency,
			double publishedMaximumEfficiencyAdvanceRatioJ,
			UiucDa4002MeasuredRotorModel.DimensionalSample measuredStaticModel,
			UiucDa4002MeasuredRotorModel.DimensionalSample measuredEfficiencyModel,
			NormalizedLoads publishedStaticCoefficientLoads
	) {
		public double modelMinusPublishedStaticCt() {
			return measuredStaticModel.lookup().thrustCoefficientCt()
					- publishedStaticThrustCoefficientKt;
		}

		public double modelMinusPublishedStaticCp() {
			return measuredStaticModel.lookup().powerCoefficientCp()
					- publishedStaticPowerCoefficientKp;
		}

		public double modelMinusPublishedMaximumEfficiency() {
			return measuredEfficiencyModel.lookup().propulsiveEfficiencyEta()
					- publishedMaximumEfficiency;
		}

		public double modelMinusPublishedStaticNormalizedThrustNewtons() {
			return measuredStaticModel.thrustNewtons()
					- publishedStaticCoefficientLoads.thrustNewtons();
		}

		public double modelMinusPublishedStaticNormalizedPowerWatts() {
			return measuredStaticModel.shaftPowerWatts()
					- publishedStaticCoefficientLoads.shaftPowerWatts();
		}

		public double modelMinusPublishedStaticNormalizedTorqueNewtonMeters() {
			return measuredStaticModel.shaftTorqueNewtonMeters()
					- publishedStaticCoefficientLoads.shaftTorqueNewtonMeters();
		}
	}

	public record Report(
			List<CfxComparison> cfxComparisons,
			OpenFoamSummaryComparison openFoamSummaryComparison
	) {
		public Report {
			cfxComparisons = List.copyOf(cfxComparisons);
		}
	}

	public static List<PublishedCfxRow> publishedCfxRows() {
		return CFX_ROWS;
	}

	public static double consistentPublishedDensityMedianKgPerCubicMeter() {
		List<Double> densities = new ArrayList<>();
		for (PublishedCfxRow row : CFX_ROWS) {
			double thrustDensity = inferredDensityFromPublishedThrust(row);
			double torqueDensity = inferredDensityFromPublishedTorque(row);
			if (Math.abs(torqueDensity / thrustDensity - 1.0)
					<= SOURCE_LOAD_CLOSURE_RELATIVE_TOLERANCE) {
				densities.add(thrustDensity);
				densities.add(torqueDensity);
			}
		}
		densities.sort(Double::compare);
		int upperMiddle = densities.size() / 2;
		return (densities.get(upperMiddle - 1) + densities.get(upperMiddle)) * 0.5;
	}

	public static Report compare() {
		List<CfxComparison> comparisons = CFX_ROWS.stream()
				.map(UiucDa4002PublishedCfdComparison::compare)
				.toList();
		UiucDa4002MeasuredRotorModel.DimensionalSample staticModel = sample(0.0);
		UiucDa4002MeasuredRotorModel.DimensionalSample efficiencyModel = sample(0.6);
		Coefficients openFoamStatic = new Coefficients(0.13, 0.08);
		return new Report(
				comparisons,
				new OpenFoamSummaryComparison(
						openFoamStatic.ct(),
						openFoamStatic.cp(),
						0.55,
						0.6,
						staticModel,
						efficiencyModel,
						normalizedLoads(openFoamStatic)
				)
		);
	}

	private static CfxComparison compare(PublishedCfxRow published) {
		UiucDa4002MeasuredRotorModel.DimensionalSample measured = sample(
				published.advanceRatioJ()
		);
		return new CfxComparison(
				published,
				measured,
				normalizedLoads(new Coefficients(
						published.publishedThrustCoefficientKt(),
						published.publishedPowerCoefficientKp()
				))
		);
	}

	private static UiucDa4002MeasuredRotorModel.DimensionalSample sample(
			double advanceRatioJ
	) {
		UiucDa4002MeasuredRotorModel.DimensionalSample sample =
				UiucDa4002MeasuredRotorModel.sample(
						PROPELLER,
						advanceRatioJ,
						SOURCE_RPM,
						INFERRED_AIR_DENSITY_KG_PER_CUBIC_METER,
						DYNAMIC_VISCOSITY_PASCAL_SECONDS
				);
		if (sample.blocked()) {
			throw new IllegalStateException("published CFD point is outside measured surface.");
		}
		return sample;
	}

	private static NormalizedLoads normalizedLoads(Coefficients coefficients) {
		double n = SOURCE_RPM / 60.0;
		double thrust = coefficients.ct()
				* INFERRED_AIR_DENSITY_KG_PER_CUBIC_METER
				* n * n * Math.pow(SOURCE_DIAMETER_METERS, 4.0);
		double power = coefficients.cp()
				* INFERRED_AIR_DENSITY_KG_PER_CUBIC_METER
				* Math.pow(n, 3.0) * Math.pow(SOURCE_DIAMETER_METERS, 5.0);
		return new NormalizedLoads(thrust, power, power / (2.0 * Math.PI * n));
	}

	private static double inferredDensityFromPublishedThrust(PublishedCfxRow row) {
		double n = SOURCE_RPM / 60.0;
		return row.publishedThrustNewtons()
				/ (row.publishedThrustCoefficientKt()
				* n * n * Math.pow(SOURCE_DIAMETER_METERS, 4.0));
	}

	private static double inferredDensityFromPublishedTorque(PublishedCfxRow row) {
		double n = SOURCE_RPM / 60.0;
		double omega = 2.0 * Math.PI * n;
		return row.publishedTorqueNewtonMeters() * omega
				/ (row.publishedPowerCoefficientKp()
				* Math.pow(n, 3.0) * Math.pow(SOURCE_DIAMETER_METERS, 5.0));
	}

	private static PublishedCfxRow row(
			CfxTurbulenceModel turbulenceModel,
			double advanceRatioJ,
			double freestreamVelocity,
			double thrust,
			double torque,
			double thrustCoefficient,
			double powerCoefficient,
			double efficiency
	) {
		return new PublishedCfxRow(
				turbulenceModel,
				advanceRatioJ,
				freestreamVelocity,
				thrust,
				torque,
				thrustCoefficient,
				powerCoefficient,
				efficiency
		);
	}

	private record Coefficients(double ct, double cp) {
	}
}
