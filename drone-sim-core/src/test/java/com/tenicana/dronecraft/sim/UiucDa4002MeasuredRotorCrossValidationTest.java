package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.UiucDa4002MeasuredRotorCrossValidation.Report;
import com.tenicana.dronecraft.sim.UiucDa4002MeasuredRotorCrossValidation.ResidualSample;
import com.tenicana.dronecraft.sim.UiucDa4002MeasuredRotorCrossValidation.ResidualSummary;
import com.tenicana.dronecraft.sim.UiucDa4002MeasuredRotorCrossValidation.ValidationKind;

class UiucDa4002MeasuredRotorCrossValidationTest {
	@Test
	void sourceRowsAreActuallyHeldOutAndRemainBracketed() {
		Report report = UiucDa4002MeasuredRotorCrossValidation.analyze();
		List<ResidualSample> staticSamples = samples(report,
				ValidationKind.STATIC_ROW_LEAVE_ONE_OUT);
		List<ResidualSample> advanceSamples = samples(report,
				ValidationKind.ADVANCE_ROW_LEAVE_ONE_OUT);

		assertEquals(28, staticSamples.size());
		assertEquals(94, advanceSamples.size());
		assertSummary(report, ValidationKind.STATIC_ROW_LEAVE_ONE_OUT,
				UiucDa4002MeasuredRotorModel.Propeller.DA4002_5X3_75, 11, 11, 0);
		assertSummary(report, ValidationKind.STATIC_ROW_LEAVE_ONE_OUT,
				UiucDa4002MeasuredRotorModel.Propeller.DA4002_9X6_75, 17, 17, 0);
		assertSummary(report, ValidationKind.ADVANCE_ROW_LEAVE_ONE_OUT,
				UiucDa4002MeasuredRotorModel.Propeller.DA4002_5X3_75, 31, 31, 0);
		assertSummary(report, ValidationKind.ADVANCE_ROW_LEAVE_ONE_OUT,
				UiucDa4002MeasuredRotorModel.Propeller.DA4002_9X6_75, 63, 63, 0);

		for (ResidualSample sample : staticSamples) {
			assertEquals(UiucDa4002MeasuredRotorCrossValidation.SupportAxis.RPM,
					sample.supportAxis());
			assertTrue(sample.lowerSupportCoordinate() < sample.targetRpm());
			assertTrue(sample.targetRpm() < sample.upperSupportCoordinate());
			assertEquals(
					(sample.targetRpm() - sample.lowerSupportCoordinate())
							/ (sample.upperSupportCoordinate()
							- sample.lowerSupportCoordinate()),
					sample.interpolationFraction(),
					1.0e-15
			);
		}
		for (ResidualSample sample : advanceSamples) {
			assertEquals(UiucDa4002MeasuredRotorCrossValidation.SupportAxis.ADVANCE_RATIO_J,
					sample.supportAxis());
			assertTrue(sample.lowerSupportCoordinate() < sample.advanceRatioJ());
			assertTrue(sample.advanceRatioJ() < sample.upperSupportCoordinate());
			assertEquals(
					(sample.advanceRatioJ() - sample.lowerSupportCoordinate())
							/ (sample.upperSupportCoordinate()
							- sample.lowerSupportCoordinate()),
					sample.interpolationFraction(),
					1.0e-15
			);
		}
	}

	@Test
	void nominalRpmTracksUseOnlyAdjacentTracksAndExposeCoverageGap() {
		Report report = UiucDa4002MeasuredRotorCrossValidation.analyze();
		List<ResidualSample> samples = samples(report,
				ValidationKind.NOMINAL_RPM_TRACK_LEAVE_ONE_OUT);

		assertEquals(48, samples.size());
		assertSummary(report, ValidationKind.NOMINAL_RPM_TRACK_LEAVE_ONE_OUT,
				UiucDa4002MeasuredRotorModel.Propeller.DA4002_5X3_75, 12, 12, 0);
		assertSummary(report, ValidationKind.NOMINAL_RPM_TRACK_LEAVE_ONE_OUT,
				UiucDa4002MeasuredRotorModel.Propeller.DA4002_9X6_75, 37, 36, 1);
		assertEquals(1, report.blockedCandidates().size());
		UiucDa4002MeasuredRotorCrossValidation.BlockedCandidate blocked =
				report.blockedCandidates().get(0);
		assertEquals("da4002-9x6.75-nominal-rpm-3000", blocked.targetId());
		assertEquals(3_000.0, blocked.targetRpm(), 0.0);
		assertEquals(0.887498, blocked.advanceRatioJ(), 0.0);
		assertEquals("upper:query-outside-uiuc-da4002-nominal-track-j-envelope",
				blocked.reason());

		for (ResidualSample sample : samples) {
			assertEquals(UiucDa4002MeasuredRotorCrossValidation.SupportAxis.RPM,
					sample.supportAxis());
			assertTrue(sample.lowerSupportCoordinate() < sample.targetRpm());
			assertTrue(sample.targetRpm() < sample.upperSupportCoordinate());
			double fraction = (sample.targetRpm() - sample.lowerSupportCoordinate())
					/ (sample.upperSupportCoordinate() - sample.lowerSupportCoordinate());
			UiucDa4002MeasuredRotorModel.LookupResult lower =
					UiucDa4002MeasuredRotorModel.evaluate(
							sample.propeller(),
							sample.advanceRatioJ(),
							sample.lowerSupportCoordinate()
					);
			UiucDa4002MeasuredRotorModel.LookupResult upper =
					UiucDa4002MeasuredRotorModel.evaluate(
							sample.propeller(),
							sample.advanceRatioJ(),
							sample.upperSupportCoordinate()
					);
			assertFalse(lower.blocked());
			assertFalse(upper.blocked());
			assertEquals(lerp(lower.thrustCoefficientCt(), upper.thrustCoefficientCt(),
					fraction), sample.predictedCoefficients().thrustCoefficientCt(), 1.0e-15);
			assertEquals(lerp(lower.powerCoefficientCp(), upper.powerCoefficientCp(),
					fraction), sample.predictedCoefficients().powerCoefficientCp(), 1.0e-15);
		}
	}

	@Test
	void dimensionalResidualsAndMeasuredZeroCrossingsCloseDefinitions() {
		Report report = UiucDa4002MeasuredRotorCrossValidation.analyze();
		assertEquals(170, report.residualSamples().size());
		assertEquals(5, report.zeroThrustBrackets().size());

		for (ResidualSample sample : report.residualSamples()) {
			double n = sample.targetRpm() / 60.0;
			double diameter = sample.propeller().diameterMeters();
			double thrustScale = UiucDa4002MeasuredRotorCrossValidation
					.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER
					* Math.pow(n, 2.0) * Math.pow(diameter, 4.0);
			double powerScale = UiucDa4002MeasuredRotorCrossValidation
					.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER
					* Math.pow(n, 3.0) * Math.pow(diameter, 5.0);
			double omega = 2.0 * Math.PI * n;
			assertEquals(sample.measuredCoefficients().thrustCoefficientCt() * thrustScale,
					sample.measuredLoads().thrustNewtons(), 1.0e-14);
			assertEquals(sample.predictedCoefficients().thrustCoefficientCt() * thrustScale,
					sample.predictedLoads().thrustNewtons(), 1.0e-14);
			assertEquals(sample.measuredCoefficients().powerCoefficientCp() * powerScale,
					sample.measuredLoads().shaftPowerWatts(), 1.0e-14);
			assertEquals(sample.predictedCoefficients().powerCoefficientCp() * powerScale,
					sample.predictedLoads().shaftPowerWatts(), 1.0e-14);
			assertEquals(sample.measuredLoads().shaftPowerWatts() / omega,
					sample.measuredLoads().shaftTorqueNewtonMeters(), 1.0e-14);
			assertEquals(sample.predictedLoads().shaftPowerWatts() / omega,
					sample.predictedLoads().shaftTorqueNewtonMeters(), 1.0e-14);
			if (sample.zeroThrustBracketNeighbor()) {
				assertFalse(sample.relativeCtResidualAvailable());
			}
			assertTrue(Double.isFinite(sample.signedThrustResidualNewtons()));
			assertTrue(Double.isFinite(sample.signedPowerResidualWatts()));
			assertTrue(Double.isFinite(sample.signedTorqueResidualNewtonMeters()));
		}

		report.zeroThrustBrackets().forEach(bracket -> {
			assertTrue(bracket.lowerAdvanceRatioJ()
					<= bracket.linearZeroThrustAdvanceRatioJ());
			assertTrue(bracket.linearZeroThrustAdvanceRatioJ()
					<= bracket.upperAdvanceRatioJ());
			assertTrue(bracket.lowerThrustCoefficientCt()
					* bracket.upperThrustCoefficientCt() <= 0.0);
			double fraction = (bracket.linearZeroThrustAdvanceRatioJ()
					- bracket.lowerAdvanceRatioJ())
					/ (bracket.upperAdvanceRatioJ() - bracket.lowerAdvanceRatioJ());
			assertEquals(0.0, lerp(bracket.lowerThrustCoefficientCt(),
					bracket.upperThrustCoefficientCt(), fraction), 2.0e-17);
		});
	}

	private static List<ResidualSample> samples(Report report, ValidationKind kind) {
		return report.residualSamples().stream()
				.filter(sample -> sample.validationKind() == kind)
				.toList();
	}

	private static void assertSummary(
			Report report,
			ValidationKind kind,
			UiucDa4002MeasuredRotorModel.Propeller propeller,
			int candidates,
			int supported,
			int blocked
	) {
		ResidualSummary summary = report.aggregateSummary(kind, propeller);
		assertEquals(candidates, summary.candidateCount());
		assertEquals(supported, summary.supportedCount());
		assertEquals(blocked, summary.blockedCount());
	}

	private static double lerp(double lower, double upper, double fraction) {
		return lower + (upper - lower) * fraction;
	}
}
