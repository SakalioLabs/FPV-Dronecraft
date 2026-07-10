package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.UiucDa4002PublishedCfdComparison.CfxComparison;
import com.tenicana.dronecraft.sim.UiucDa4002PublishedCfdComparison.CfxTurbulenceModel;
import com.tenicana.dronecraft.sim.UiucDa4002PublishedCfdComparison.OpenFoamSummaryComparison;
import com.tenicana.dronecraft.sim.UiucDa4002PublishedCfdComparison.PublishedCfxRow;
import com.tenicana.dronecraft.sim.UiucDa4002PublishedCfdComparison.Report;

class UiucDa4002PublishedCfdComparisonTest {
	@Test
	void preservesPublishedRowsAndExposesThePrintedTorqueInconsistency() {
		List<PublishedCfxRow> rows = UiucDa4002PublishedCfdComparison.publishedCfxRows();
		Report report = UiucDa4002PublishedCfdComparison.compare();

		assertEquals(10, rows.size());
		assertEquals(10, report.cfxComparisons().size());
		assertEquals(1.17931175815697, UiucDa4002PublishedCfdComparison
				.consistentPublishedDensityMedianKgPerCubicMeter(), 1.0e-14);
		assertEquals(1.18, UiucDa4002PublishedCfdComparison
				.INFERRED_AIR_DENSITY_KG_PER_CUBIC_METER, 0.001);
		assertEquals(9, report.cfxComparisons().stream()
				.filter(CfxComparison::publishedTorqueCoefficientClosureSatisfied)
				.count());
		CfxComparison inconsistent = report.cfxComparisons().stream()
				.filter(comparison -> !comparison
						.publishedTorqueCoefficientClosureSatisfied())
				.findFirst()
				.orElseThrow();
		assertEquals(CfxTurbulenceModel.K_OMEGA,
				inconsistent.published().turbulenceModel());
		assertEquals(0.2, inconsistent.published().advanceRatioJ(), 0.0);
		assertEquals(0.076, inconsistent.published().publishedTorqueNewtonMeters(), 0.0);
		assertTrue(inconsistent.sourceDensityClosureResidualFraction() > 8.0);

		for (CfxComparison comparison : report.cfxComparisons()) {
			assertTrue(Math.abs(comparison
					.publishedFreestreamClosureResidualMetersPerSecond()) <= 0.0041);
			assertTrue(Math.abs(comparison.publishedEtaClosureResidual()) <= 0.006);
			assertTrue(comparison.inferredDensityFromPublishedThrust() > 1.17);
			assertTrue(comparison.inferredDensityFromPublishedThrust() < 1.20);
			if (comparison.publishedTorqueCoefficientClosureSatisfied()) {
				assertTrue(comparison.inferredDensityFromPublishedTorque() > 1.17);
				assertTrue(comparison.inferredDensityFromPublishedTorque() < 1.20);
			}
		}
	}

	@Test
	void comparesFiniteCoefficientsAndSiLoadsAtOneDeclaredDensity() {
		Report report = UiucDa4002PublishedCfdComparison.compare();
		double n = UiucDa4002PublishedCfdComparison.SOURCE_RPM / 60.0;
		double diameter = UiucDa4002PublishedCfdComparison.SOURCE_DIAMETER_METERS;
		double density = UiucDa4002PublishedCfdComparison
				.INFERRED_AIR_DENSITY_KG_PER_CUBIC_METER;
		double thrustScale = density * n * n * Math.pow(diameter, 4.0);
		double powerScale = density * Math.pow(n, 3.0) * Math.pow(diameter, 5.0);
		double omega = 2.0 * Math.PI * n;

		for (CfxComparison comparison : report.cfxComparisons()) {
			var published = comparison.published();
			var normalized = comparison.publishedCoefficientLoads();
			var measured = comparison.measuredModel();
			assertFalse(measured.blocked());
			assertEquals(density, measured.airDensityKgPerCubicMeter(), 0.0);
			assertEquals(published.publishedThrustCoefficientKt() * thrustScale,
					normalized.thrustNewtons(), 1.0e-15);
			assertEquals(published.publishedPowerCoefficientKp() * powerScale,
					normalized.shaftPowerWatts(), 1.0e-15);
			assertEquals(normalized.shaftPowerWatts() / omega,
					normalized.shaftTorqueNewtonMeters(), 1.0e-15);
			assertEquals(measured.lookup().thrustCoefficientCt() * thrustScale,
					measured.thrustNewtons(), 1.0e-15);
			assertEquals(measured.lookup().powerCoefficientCp() * powerScale,
					measured.shaftPowerWatts(), 1.0e-15);
			assertEquals(measured.shaftPowerWatts() / omega,
					measured.shaftTorqueNewtonMeters(), 1.0e-15);
			assertTrue(Double.isFinite(comparison.modelMinusPublishedCt()));
			assertTrue(Double.isFinite(comparison.modelMinusPublishedCp()));
			assertTrue(Double.isFinite(
					comparison.modelMinusPublishedNormalizedThrustNewtons()));
			assertTrue(Double.isFinite(
					comparison.modelMinusPublishedNormalizedPowerWatts()));
			assertTrue(Double.isFinite(
					comparison.modelMinusPublishedNormalizedTorqueNewtonMeters()));
		}
	}

	@Test
	void keepsRoundedOpenFoamAnchorsSeparateFromTheTabulatedCfxRows() {
		OpenFoamSummaryComparison comparison = UiucDa4002PublishedCfdComparison
				.compare().openFoamSummaryComparison();

		assertEquals(0.13, comparison.publishedStaticThrustCoefficientKt(), 0.0);
		assertEquals(0.08, comparison.publishedStaticPowerCoefficientKp(), 0.0);
		assertEquals(0.55, comparison.publishedMaximumEfficiency(), 0.0);
		assertEquals(0.6, comparison.publishedMaximumEfficiencyAdvanceRatioJ(), 0.0);
		assertFalse(comparison.measuredStaticModel().blocked());
		assertFalse(comparison.measuredEfficiencyModel().blocked());
		assertTrue(Math.abs(comparison.modelMinusPublishedStaticCt()) < 0.002);
		assertTrue(comparison.modelMinusPublishedStaticCp() > 0.0);
		assertTrue(comparison.modelMinusPublishedMaximumEfficiency() > 0.0);
		assertTrue(Double.isFinite(
				comparison.modelMinusPublishedStaticNormalizedThrustNewtons()));
		assertTrue(Double.isFinite(
				comparison.modelMinusPublishedStaticNormalizedPowerWatts()));
		assertTrue(Double.isFinite(
				comparison.modelMinusPublishedStaticNormalizedTorqueNewtonMeters()));
	}
}
