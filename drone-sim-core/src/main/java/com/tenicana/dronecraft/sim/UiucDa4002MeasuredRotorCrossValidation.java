package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Internal interpolation cross-validation for the measured DA4002 surface.
 * Source-row predictions remove the target row before calling the production
 * lookup. Nominal-RPM predictions use only the two adjacent measured tracks.
 * This measures interpolation behavior, not independent aerodynamic accuracy.
 */
public final class UiucDa4002MeasuredRotorCrossValidation {
	public static final String DATA_SOURCE_ID =
			"uiuc-propdb-volume-2-da4002-interpolation-cross-validation";
	public static final double STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER =
			PropellerArchiveCtCpJDimensionalRotorResponse
					.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
	public static final String AGGREGATE_TARGET_ID = "ALL";
	private static final double EPSILON = 1.0e-12;

	private UiucDa4002MeasuredRotorCrossValidation() {
	}

	public enum ValidationKind {
		STATIC_ROW_LEAVE_ONE_OUT,
		ADVANCE_ROW_LEAVE_ONE_OUT,
		NOMINAL_RPM_TRACK_LEAVE_ONE_OUT
	}

	public enum SupportAxis {
		RPM,
		ADVANCE_RATIO_J
	}

	public record Coefficients(double thrustCoefficientCt, double powerCoefficientCp) {
		public Coefficients {
			if (!Double.isFinite(thrustCoefficientCt)
					|| !Double.isFinite(powerCoefficientCp)) {
				throw new IllegalArgumentException("CT and CP must be finite.");
			}
		}
	}

	public record DimensionalLoads(
			double thrustNewtons,
			double shaftPowerWatts,
			double shaftTorqueNewtonMeters
	) {
		public DimensionalLoads {
			if (!Double.isFinite(thrustNewtons)
					|| !Double.isFinite(shaftPowerWatts)
					|| !Double.isFinite(shaftTorqueNewtonMeters)) {
				throw new IllegalArgumentException("dimensional loads must be finite.");
			}
		}
	}

	public record ResidualSample(
			ValidationKind validationKind,
			UiucDa4002MeasuredRotorModel.Propeller propeller,
			String targetId,
			double targetRpm,
			double advanceRatioJ,
			SupportAxis supportAxis,
			double lowerSupportCoordinate,
			double upperSupportCoordinate,
			double interpolationFraction,
			boolean zeroThrustBracketNeighbor,
			Coefficients measuredCoefficients,
			Coefficients predictedCoefficients,
			DimensionalLoads measuredLoads,
			DimensionalLoads predictedLoads
	) {
		public ResidualSample {
			if (validationKind == null || propeller == null || supportAxis == null) {
				throw new IllegalArgumentException(
						"validationKind, propeller, and supportAxis must not be null."
				);
			}
			targetId = targetId == null ? "" : targetId.trim();
			if (targetId.isEmpty()) {
				throw new IllegalArgumentException("targetId must not be blank.");
			}
			if (!Double.isFinite(targetRpm) || targetRpm <= 0.0
					|| !Double.isFinite(advanceRatioJ) || advanceRatioJ < 0.0
					|| !Double.isFinite(lowerSupportCoordinate)
					|| !Double.isFinite(upperSupportCoordinate)
					|| lowerSupportCoordinate >= upperSupportCoordinate
					|| !Double.isFinite(interpolationFraction)
					|| interpolationFraction <= 0.0
					|| interpolationFraction >= 1.0) {
				throw new IllegalArgumentException("residual sample coordinates are invalid.");
			}
			if (measuredCoefficients == null || predictedCoefficients == null
					|| measuredLoads == null || predictedLoads == null) {
				throw new IllegalArgumentException(
						"coefficient and dimensional samples must not be null."
				);
			}
		}

		public double signedCtResidual() {
			return predictedCoefficients.thrustCoefficientCt()
					- measuredCoefficients.thrustCoefficientCt();
		}

		public double absoluteCtResidual() {
			return Math.abs(signedCtResidual());
		}

		public boolean relativeCtResidualAvailable() {
			return !zeroThrustBracketNeighbor
					&& Math.abs(measuredCoefficients.thrustCoefficientCt()) > EPSILON;
		}

		public double signedRelativeCtResidualFraction() {
			return relativeCtResidualAvailable()
					? signedCtResidual() / measuredCoefficients.thrustCoefficientCt()
					: 0.0;
		}

		public double signedCpResidual() {
			return predictedCoefficients.powerCoefficientCp()
					- measuredCoefficients.powerCoefficientCp();
		}

		public double absoluteCpResidual() {
			return Math.abs(signedCpResidual());
		}

		public boolean relativeCpResidualAvailable() {
			return Math.abs(measuredCoefficients.powerCoefficientCp()) > EPSILON;
		}

		public double signedRelativeCpResidualFraction() {
			return relativeCpResidualAvailable()
					? signedCpResidual() / measuredCoefficients.powerCoefficientCp()
					: 0.0;
		}

		public double signedThrustResidualNewtons() {
			return predictedLoads.thrustNewtons() - measuredLoads.thrustNewtons();
		}

		public double absoluteThrustResidualNewtons() {
			return Math.abs(signedThrustResidualNewtons());
		}

		public double signedPowerResidualWatts() {
			return predictedLoads.shaftPowerWatts() - measuredLoads.shaftPowerWatts();
		}

		public double absolutePowerResidualWatts() {
			return Math.abs(signedPowerResidualWatts());
		}

		public double signedTorqueResidualNewtonMeters() {
			return predictedLoads.shaftTorqueNewtonMeters()
					- measuredLoads.shaftTorqueNewtonMeters();
		}

		public double absoluteTorqueResidualNewtonMeters() {
			return Math.abs(signedTorqueResidualNewtonMeters());
		}
	}

	public record ZeroThrustBracket(
			UiucDa4002MeasuredRotorModel.Propeller propeller,
			String sourceCurveId,
			double sourceRpm,
			double lowerAdvanceRatioJ,
			double lowerThrustCoefficientCt,
			double upperAdvanceRatioJ,
			double upperThrustCoefficientCt,
			double linearZeroThrustAdvanceRatioJ
	) {
	}

	public record BlockedCandidate(
			ValidationKind validationKind,
			UiucDa4002MeasuredRotorModel.Propeller propeller,
			String targetId,
			double targetRpm,
			double advanceRatioJ,
			String reason
	) {
		public BlockedCandidate {
			targetId = targetId == null ? "" : targetId.trim();
			reason = reason == null ? "" : reason.trim();
			if (validationKind == null || propeller == null
					|| targetId.isEmpty() || reason.isEmpty()) {
				throw new IllegalArgumentException("blocked candidate fields must be complete.");
			}
		}
	}

	public record ResidualSummary(
			ValidationKind validationKind,
			UiucDa4002MeasuredRotorModel.Propeller propeller,
			String targetId,
			int candidateCount,
			int supportedCount,
			int blockedCount,
			int zeroThrustBracketNeighborCount,
			int relativeCtSampleCount,
			double meanAbsoluteCtResidual,
			double maximumAbsoluteCtResidual,
			double meanAbsoluteRelativeCtResidualFraction,
			double meanAbsoluteCpResidual,
			double maximumAbsoluteCpResidual,
			double meanAbsoluteRelativeCpResidualFraction,
			double meanAbsoluteThrustResidualNewtons,
			double maximumAbsoluteThrustResidualNewtons,
			double meanAbsolutePowerResidualWatts,
			double maximumAbsolutePowerResidualWatts,
			double meanAbsoluteTorqueResidualNewtonMeters,
			double maximumAbsoluteTorqueResidualNewtonMeters
	) {
	}

	public record Report(
			List<ResidualSample> residualSamples,
			List<ResidualSummary> residualSummaries,
			List<BlockedCandidate> blockedCandidates,
			List<ZeroThrustBracket> zeroThrustBrackets
	) {
		public Report {
			residualSamples = List.copyOf(residualSamples);
			residualSummaries = List.copyOf(residualSummaries);
			blockedCandidates = List.copyOf(blockedCandidates);
			zeroThrustBrackets = List.copyOf(zeroThrustBrackets);
		}

		public ResidualSummary aggregateSummary(
				ValidationKind validationKind,
				UiucDa4002MeasuredRotorModel.Propeller propeller
		) {
			return residualSummaries.stream()
					.filter(summary -> summary.validationKind() == validationKind
							&& summary.propeller() == propeller
							&& summary.targetId().equals(AGGREGATE_TARGET_ID))
					.findFirst()
					.orElseThrow();
		}
	}

	public static Report analyze() {
		List<ResidualSample> samples = new ArrayList<>();
		List<BlockedCandidate> blockedCandidates = new ArrayList<>();
		Map<SummaryKey, SummaryAccumulator> accumulators = new LinkedHashMap<>();
		addStaticRowCrossValidation(samples, accumulators);
		addAdvanceRowCrossValidation(samples, accumulators);
		addNominalRpmTrackCrossValidation(samples, blockedCandidates, accumulators);
		List<ResidualSummary> summaries = accumulators.entrySet().stream()
				.sorted(summaryEntryComparator())
				.map(entry -> entry.getValue().summary(entry.getKey()))
				.toList();
		return new Report(samples, summaries, blockedCandidates, zeroThrustBrackets());
	}

	private static void addStaticRowCrossValidation(
			List<ResidualSample> samples,
			Map<SummaryKey, SummaryAccumulator> accumulators
	) {
		for (UiucDa4002StaticPerformanceLookup.StaticCurve curve
				: UiucDa4002StaticPerformanceLookup.curves()) {
			UiucDa4002MeasuredRotorModel.Propeller propeller = propellerForDiameter(
					curve.referenceDiameterMeters()
			);
			for (int rowIndex = 1; rowIndex < curve.rows().size() - 1; rowIndex++) {
				UiucDa4002StaticPerformanceLookup.StaticRow target = curve.rows().get(rowIndex);
				countCandidate(accumulators, ValidationKind.STATIC_ROW_LEAVE_ONE_OUT,
						propeller, curve.id());
				UiucDa4002StaticPerformanceLookup.LookupResult prediction =
						UiucDa4002StaticPerformanceLookup.evaluate(
								withoutStaticRow(curve, rowIndex),
								target.rpm(),
								UiucDa4002StaticPerformanceLookup.EnvelopePolicy
										.BLOCK_OUT_OF_ENVELOPE
						);
				if (prediction.blocked()) {
					throw new IllegalStateException("internal static leave-one-out row blocked.");
				}
				ResidualSample sample = sample(
						ValidationKind.STATIC_ROW_LEAVE_ONE_OUT,
						propeller,
						curve.id(),
						target.rpm(),
						0.0,
						SupportAxis.RPM,
						prediction.lowerRpm(),
						prediction.upperRpm(),
						prediction.interpolationFraction(),
						false,
						new Coefficients(target.thrustCoefficientCt(), target.powerCoefficientCp()),
						new Coefficients(
								prediction.thrustCoefficientCt(),
								prediction.powerCoefficientCp()
						)
				);
				recordSupportedSample(samples, accumulators, sample);
			}
		}
	}

	private static void addAdvanceRowCrossValidation(
			List<ResidualSample> samples,
			Map<SummaryKey, SummaryAccumulator> accumulators
	) {
		for (UiucDa4002AdvancePerformanceLookup.AdvanceCurve curve
				: UiucDa4002AdvancePerformanceLookup.curves()) {
			UiucDa4002MeasuredRotorModel.Propeller propeller = propellerForDiameter(
					curve.referenceDiameterMeters()
			);
			for (int rowIndex = 1; rowIndex < curve.rows().size() - 1; rowIndex++) {
				UiucDa4002AdvancePerformanceLookup.AdvanceRow target = curve.rows().get(rowIndex);
				countCandidate(accumulators, ValidationKind.ADVANCE_ROW_LEAVE_ONE_OUT,
						propeller, curve.id());
				UiucDa4002AdvancePerformanceLookup.LookupResult prediction =
						UiucDa4002AdvancePerformanceLookup.evaluate(
								withoutAdvanceRow(curve, rowIndex),
								target.advanceRatioJ(),
								UiucDa4002AdvancePerformanceLookup.EnvelopePolicy
										.BLOCK_OUT_OF_ENVELOPE
						);
				if (prediction.blocked()) {
					throw new IllegalStateException("internal advance leave-one-out row blocked.");
				}
				ResidualSample sample = sample(
						ValidationKind.ADVANCE_ROW_LEAVE_ONE_OUT,
						propeller,
						curve.id(),
						curve.rpm(),
						target.advanceRatioJ(),
						SupportAxis.ADVANCE_RATIO_J,
						prediction.lowerAdvanceRatioJ(),
						prediction.upperAdvanceRatioJ(),
						prediction.interpolationFraction(),
						isZeroThrustBracketNeighbor(curve.rows(), rowIndex),
						new Coefficients(target.thrustCoefficientCt(), target.powerCoefficientCp()),
						new Coefficients(
								prediction.thrustCoefficientCt(),
								prediction.powerCoefficientCp()
						)
				);
				recordSupportedSample(samples, accumulators, sample);
			}
		}
	}

	private static void addNominalRpmTrackCrossValidation(
			List<ResidualSample> samples,
			List<BlockedCandidate> blockedCandidates,
			Map<SummaryKey, SummaryAccumulator> accumulators
	) {
		for (TrackHoldout holdout : trackHoldouts()) {
			String targetId = holdout.propeller().id()
					+ "-nominal-rpm-" + Math.round(holdout.targetRpm());
			for (Map.Entry<Double, Boolean> candidate : trackCandidates(holdout).entrySet()) {
				double advanceRatio = candidate.getKey();
				countCandidate(accumulators,
						ValidationKind.NOMINAL_RPM_TRACK_LEAVE_ONE_OUT,
						holdout.propeller(), targetId);
				UiucDa4002MeasuredRotorModel.LookupResult measured =
						UiucDa4002MeasuredRotorModel.evaluate(
								holdout.propeller(), advanceRatio, holdout.targetRpm()
						);
				UiucDa4002MeasuredRotorModel.LookupResult lower =
						UiucDa4002MeasuredRotorModel.evaluate(
								holdout.propeller(), advanceRatio, holdout.lowerRpm()
						);
				UiucDa4002MeasuredRotorModel.LookupResult upper =
						UiucDa4002MeasuredRotorModel.evaluate(
								holdout.propeller(), advanceRatio, holdout.upperRpm()
						);
				if (measured.blocked() || lower.blocked() || upper.blocked()) {
					blockedCandidates.add(new BlockedCandidate(
							ValidationKind.NOMINAL_RPM_TRACK_LEAVE_ONE_OUT,
							holdout.propeller(),
							targetId,
							holdout.targetRpm(),
							advanceRatio,
							blockedReason(measured, lower, upper)
					));
					continue;
				}
				double fraction = (holdout.targetRpm() - holdout.lowerRpm())
						/ (holdout.upperRpm() - holdout.lowerRpm());
				ResidualSample sample = sample(
						ValidationKind.NOMINAL_RPM_TRACK_LEAVE_ONE_OUT,
						holdout.propeller(),
						targetId,
						holdout.targetRpm(),
						advanceRatio,
						SupportAxis.RPM,
						holdout.lowerRpm(),
						holdout.upperRpm(),
						fraction,
						candidate.getValue(),
						new Coefficients(
								measured.thrustCoefficientCt(),
								measured.powerCoefficientCp()
						),
						new Coefficients(
								lerp(lower.thrustCoefficientCt(),
										upper.thrustCoefficientCt(), fraction),
								lerp(lower.powerCoefficientCp(),
										upper.powerCoefficientCp(), fraction)
						)
				);
				recordSupportedSample(samples, accumulators, sample);
			}
		}
	}

	private static ResidualSample sample(
			ValidationKind validationKind,
			UiucDa4002MeasuredRotorModel.Propeller propeller,
			String targetId,
			double targetRpm,
			double advanceRatioJ,
			SupportAxis supportAxis,
			double lowerSupportCoordinate,
			double upperSupportCoordinate,
			double interpolationFraction,
			boolean zeroThrustBracketNeighbor,
			Coefficients measured,
			Coefficients predicted
	) {
		return new ResidualSample(
				validationKind,
				propeller,
				targetId,
				targetRpm,
				advanceRatioJ,
				supportAxis,
				lowerSupportCoordinate,
				upperSupportCoordinate,
				interpolationFraction,
				zeroThrustBracketNeighbor,
				measured,
				predicted,
				dimensionalLoads(propeller, targetRpm, measured),
				dimensionalLoads(propeller, targetRpm, predicted)
		);
	}

	private static DimensionalLoads dimensionalLoads(
			UiucDa4002MeasuredRotorModel.Propeller propeller,
			double rpm,
			Coefficients coefficients
	) {
		double revolutionsPerSecond = rpm / 60.0;
		double diameter = propeller.diameterMeters();
		double thrust = coefficients.thrustCoefficientCt()
				* STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER
				* Math.pow(revolutionsPerSecond, 2.0)
				* Math.pow(diameter, 4.0);
		double power = coefficients.powerCoefficientCp()
				* STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER
				* Math.pow(revolutionsPerSecond, 3.0)
				* Math.pow(diameter, 5.0);
		double torque = power / (2.0 * Math.PI * revolutionsPerSecond);
		return new DimensionalLoads(thrust, power, torque);
	}

	private static UiucDa4002StaticPerformanceLookup.StaticCurve withoutStaticRow(
			UiucDa4002StaticPerformanceLookup.StaticCurve curve,
			int removedRowIndex
	) {
		List<UiucDa4002StaticPerformanceLookup.StaticRow> rows = new ArrayList<>(curve.rows());
		rows.remove(removedRowIndex);
		return new UiucDa4002StaticPerformanceLookup.StaticCurve(
				curve.id() + "-leave-one-out",
				curve.displayName() + " leave-one-out",
				curve.sourceUrl(),
				curve.geometry(),
				curve.referenceDiameterMeters(),
				rows
		);
	}

	private static UiucDa4002AdvancePerformanceLookup.AdvanceCurve withoutAdvanceRow(
			UiucDa4002AdvancePerformanceLookup.AdvanceCurve curve,
			int removedRowIndex
	) {
		List<UiucDa4002AdvancePerformanceLookup.AdvanceRow> rows = new ArrayList<>(curve.rows());
		rows.remove(removedRowIndex);
		return new UiucDa4002AdvancePerformanceLookup.AdvanceCurve(
				curve.id() + "-leave-one-out",
				curve.label() + " leave-one-out",
				curve.resourceName(),
				curve.sourceUrl(),
				curve.geometry(),
				curve.referenceDiameterMeters(),
				curve.rpm(),
				rows
		);
	}

	private static List<TrackHoldout> trackHoldouts() {
		List<UiucDa4002AdvancePerformanceLookup.AdvanceCurve> five =
				UiucDa4002AdvancePerformanceLookup.fiveInchCurves();
		List<UiucDa4002AdvancePerformanceLookup.AdvanceCurve> nine =
				UiucDa4002AdvancePerformanceLookup.nineInchCurves();
		return List.of(
				new TrackHoldout(
						UiucDa4002MeasuredRotorModel.Propeller.DA4002_5X3_75,
						4_000.0,
						5_000.0,
						6_000.0,
						List.of(five.get(1))
				),
				new TrackHoldout(
						UiucDa4002MeasuredRotorModel.Propeller.DA4002_9X6_75,
						2_000.0,
						3_000.0,
						4_000.0,
						List.of(nine.get(1))
				),
				new TrackHoldout(
						UiucDa4002MeasuredRotorModel.Propeller.DA4002_9X6_75,
						3_000.0,
						4_000.0,
						5_000.0,
						List.of(nine.get(2), nine.get(3))
				)
		);
	}

	private static Map<Double, Boolean> trackCandidates(TrackHoldout holdout) {
		Map<Double, Boolean> candidates = new TreeMap<>();
		for (UiucDa4002AdvancePerformanceLookup.AdvanceCurve curve
				: holdout.targetSourceCurves()) {
			for (int rowIndex = 0; rowIndex < curve.rows().size(); rowIndex++) {
				boolean zeroNeighbor = isZeroThrustBracketNeighbor(curve.rows(), rowIndex);
				candidates.merge(
						curve.rows().get(rowIndex).advanceRatioJ(),
						zeroNeighbor,
						Boolean::logicalOr
				);
			}
		}
		return candidates;
	}

	private static List<ZeroThrustBracket> zeroThrustBrackets() {
		List<ZeroThrustBracket> brackets = new ArrayList<>();
		for (UiucDa4002AdvancePerformanceLookup.AdvanceCurve curve
				: UiucDa4002AdvancePerformanceLookup.curves()) {
			UiucDa4002MeasuredRotorModel.Propeller propeller = propellerForDiameter(
					curve.referenceDiameterMeters()
			);
			for (int rowIndex = 1; rowIndex < curve.rows().size(); rowIndex++) {
				UiucDa4002AdvancePerformanceLookup.AdvanceRow lower =
						curve.rows().get(rowIndex - 1);
				UiucDa4002AdvancePerformanceLookup.AdvanceRow upper =
						curve.rows().get(rowIndex);
				if (!bracketsZeroThrust(lower, upper)) {
					continue;
				}
				double zeroAdvanceRatio = lower.advanceRatioJ()
						- lower.thrustCoefficientCt()
						* (upper.advanceRatioJ() - lower.advanceRatioJ())
						/ (upper.thrustCoefficientCt() - lower.thrustCoefficientCt());
				brackets.add(new ZeroThrustBracket(
						propeller,
						curve.id(),
						curve.rpm(),
						lower.advanceRatioJ(),
						lower.thrustCoefficientCt(),
						upper.advanceRatioJ(),
						upper.thrustCoefficientCt(),
						zeroAdvanceRatio
				));
			}
		}
		return brackets.stream()
				.sorted(Comparator
						.comparing((ZeroThrustBracket bracket) -> bracket.propeller().ordinal())
						.thenComparingDouble(ZeroThrustBracket::sourceRpm))
				.toList();
	}

	private static boolean isZeroThrustBracketNeighbor(
			List<UiucDa4002AdvancePerformanceLookup.AdvanceRow> rows,
			int rowIndex
	) {
		return rowIndex > 0 && bracketsZeroThrust(rows.get(rowIndex - 1), rows.get(rowIndex))
				|| rowIndex + 1 < rows.size()
				&& bracketsZeroThrust(rows.get(rowIndex), rows.get(rowIndex + 1));
	}

	private static boolean bracketsZeroThrust(
			UiucDa4002AdvancePerformanceLookup.AdvanceRow lower,
			UiucDa4002AdvancePerformanceLookup.AdvanceRow upper
	) {
		double lowerCt = lower.thrustCoefficientCt();
		double upperCt = upper.thrustCoefficientCt();
		return lowerCt == 0.0 || upperCt == 0.0 || Math.signum(lowerCt) != Math.signum(upperCt);
	}

	private static UiucDa4002MeasuredRotorModel.Propeller propellerForDiameter(
			double diameterMeters
	) {
		for (UiucDa4002MeasuredRotorModel.Propeller propeller
				: UiucDa4002MeasuredRotorModel.Propeller.values()) {
			if (Math.abs(propeller.diameterMeters() - diameterMeters) <= EPSILON) {
				return propeller;
			}
		}
		throw new IllegalArgumentException("unsupported UIUC DA4002 diameter.");
	}

	private static void countCandidate(
			Map<SummaryKey, SummaryAccumulator> accumulators,
			ValidationKind validationKind,
			UiucDa4002MeasuredRotorModel.Propeller propeller,
			String targetId
	) {
		accumulator(accumulators, validationKind, propeller, targetId).countCandidate();
		accumulator(accumulators, validationKind, propeller, AGGREGATE_TARGET_ID)
				.countCandidate();
	}

	private static void recordSupportedSample(
			List<ResidualSample> samples,
			Map<SummaryKey, SummaryAccumulator> accumulators,
			ResidualSample sample
	) {
		samples.add(sample);
		accumulator(accumulators, sample.validationKind(), sample.propeller(), sample.targetId())
				.add(sample);
		accumulator(accumulators, sample.validationKind(), sample.propeller(),
				AGGREGATE_TARGET_ID).add(sample);
	}

	private static SummaryAccumulator accumulator(
			Map<SummaryKey, SummaryAccumulator> accumulators,
			ValidationKind validationKind,
			UiucDa4002MeasuredRotorModel.Propeller propeller,
			String targetId
	) {
		return accumulators.computeIfAbsent(
				new SummaryKey(validationKind, propeller, targetId),
				ignored -> new SummaryAccumulator()
		);
	}

	private static Comparator<Map.Entry<SummaryKey, SummaryAccumulator>>
			summaryEntryComparator() {
		return Comparator
				.comparingInt((Map.Entry<SummaryKey, SummaryAccumulator> entry) ->
						entry.getKey().validationKind().ordinal())
				.thenComparingInt(entry -> entry.getKey().propeller().ordinal())
				.thenComparingInt(entry -> entry.getKey().targetId()
						.equals(AGGREGATE_TARGET_ID) ? 0 : 1)
				.thenComparing(entry -> entry.getKey().targetId());
	}

	private static double lerp(double lower, double upper, double fraction) {
		return lower + (upper - lower) * fraction;
	}

	private static String blockedReason(
			UiucDa4002MeasuredRotorModel.LookupResult measured,
			UiucDa4002MeasuredRotorModel.LookupResult lower,
			UiucDa4002MeasuredRotorModel.LookupResult upper
	) {
		List<String> reasons = new ArrayList<>();
		if (measured.blocked()) {
			reasons.add("target:" + measured.message());
		}
		if (lower.blocked()) {
			reasons.add("lower:" + lower.message());
		}
		if (upper.blocked()) {
			reasons.add("upper:" + upper.message());
		}
		return String.join(";", reasons);
	}

	private record TrackHoldout(
			UiucDa4002MeasuredRotorModel.Propeller propeller,
			double lowerRpm,
			double targetRpm,
			double upperRpm,
			List<UiucDa4002AdvancePerformanceLookup.AdvanceCurve> targetSourceCurves
	) {
		private TrackHoldout {
			targetSourceCurves = List.copyOf(targetSourceCurves);
		}
	}

	private record SummaryKey(
			ValidationKind validationKind,
			UiucDa4002MeasuredRotorModel.Propeller propeller,
			String targetId
	) {
	}

	private static final class SummaryAccumulator {
		private int candidateCount;
		private int supportedCount;
		private int zeroThrustBracketNeighborCount;
		private int relativeCtSampleCount;
		private int relativeCpSampleCount;
		private double absoluteCtSum;
		private double maximumAbsoluteCt;
		private double absoluteRelativeCtSum;
		private double absoluteCpSum;
		private double maximumAbsoluteCp;
		private double absoluteRelativeCpSum;
		private double absoluteThrustSum;
		private double maximumAbsoluteThrust;
		private double absolutePowerSum;
		private double maximumAbsolutePower;
		private double absoluteTorqueSum;
		private double maximumAbsoluteTorque;

		private void countCandidate() {
			candidateCount++;
		}

		private void add(ResidualSample sample) {
			supportedCount++;
			if (sample.zeroThrustBracketNeighbor()) {
				zeroThrustBracketNeighborCount++;
			}
			absoluteCtSum += sample.absoluteCtResidual();
			maximumAbsoluteCt = Math.max(maximumAbsoluteCt, sample.absoluteCtResidual());
			if (sample.relativeCtResidualAvailable()) {
				relativeCtSampleCount++;
				absoluteRelativeCtSum += Math.abs(sample.signedRelativeCtResidualFraction());
			}
			absoluteCpSum += sample.absoluteCpResidual();
			maximumAbsoluteCp = Math.max(maximumAbsoluteCp, sample.absoluteCpResidual());
			if (sample.relativeCpResidualAvailable()) {
				relativeCpSampleCount++;
				absoluteRelativeCpSum += Math.abs(sample.signedRelativeCpResidualFraction());
			}
			absoluteThrustSum += sample.absoluteThrustResidualNewtons();
			maximumAbsoluteThrust = Math.max(
					maximumAbsoluteThrust,
					sample.absoluteThrustResidualNewtons()
			);
			absolutePowerSum += sample.absolutePowerResidualWatts();
			maximumAbsolutePower = Math.max(
					maximumAbsolutePower,
					sample.absolutePowerResidualWatts()
			);
			absoluteTorqueSum += sample.absoluteTorqueResidualNewtonMeters();
			maximumAbsoluteTorque = Math.max(
					maximumAbsoluteTorque,
					sample.absoluteTorqueResidualNewtonMeters()
			);
		}

		private ResidualSummary summary(SummaryKey key) {
			return new ResidualSummary(
					key.validationKind(),
					key.propeller(),
					key.targetId(),
					candidateCount,
					supportedCount,
					candidateCount - supportedCount,
					zeroThrustBracketNeighborCount,
					relativeCtSampleCount,
					mean(absoluteCtSum, supportedCount),
					maximumAbsoluteCt,
					mean(absoluteRelativeCtSum, relativeCtSampleCount),
					mean(absoluteCpSum, supportedCount),
					maximumAbsoluteCp,
					mean(absoluteRelativeCpSum, relativeCpSampleCount),
					mean(absoluteThrustSum, supportedCount),
					maximumAbsoluteThrust,
					mean(absolutePowerSum, supportedCount),
					maximumAbsolutePower,
					mean(absoluteTorqueSum, supportedCount),
					maximumAbsoluteTorque
			);
		}

		private static double mean(double sum, int count) {
			return count > 0 ? sum / count : 0.0;
		}
	}
}
