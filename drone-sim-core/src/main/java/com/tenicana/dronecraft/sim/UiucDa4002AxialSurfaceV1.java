package com.tenicana.dronecraft.sim;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import com.tenicana.dronecraft.sim.RotorHoverBladeProfilePowerModel.BladeGeometry;
import com.tenicana.dronecraft.sim.RotorHoverBladeProfilePowerModel.BladeStation;
import com.tenicana.dronecraft.sim.UiucDa4002MeasuredRotorModel.DimensionalSample;
import com.tenicana.dronecraft.sim.UiucDa4002MeasuredRotorModel.LookupResult;
import com.tenicana.dronecraft.sim.UiucDa4002MeasuredRotorModel.NominalTrackEnvelope;
import com.tenicana.dronecraft.sim.UiucDa4002MeasuredRotorModel.Propeller;
import com.tenicana.dronecraft.sim.UiucDa4002MeasuredRotorModel.StaticRpmEnvelope;

/** Stable, strictly bounded entry point for the measured DA4002 axial surface v1. */
public final class UiucDa4002AxialSurfaceV1 {
	public static final String VERSION_ID = "uiuc-da4002-axial-surface-v1";
	public static final String INTERPOLATION_ALGORITHM_ID = String.join(";",
			"j0=piecewise-linear-static-rpm",
			"positive-j=linear-static-bridge+piecewise-linear-j",
			"overlap=linear-low-high-tunnel-run-blend",
			"rpm=linear-between-adjacent-nominal-tracks",
			"envelope=strict-block-no-clamp-no-extrapolation",
			"eta=j*ct/cp",
			"si=t:ct*rho*n^2*d^4,p:cp*rho*n^3*d^5,q:p/omega"
	);
	public static final double REFERENCE_AIR_DENSITY_KG_PER_CUBIC_METER = 1.225;
	public static final double REFERENCE_DYNAMIC_VISCOSITY_PASCAL_SECONDS = 1.81e-5;
	public static final double REFERENCE_ADVANCE_RATIO_STEP = 0.025;
	public static final double REFERENCE_MAXIMUM_ADVANCE_RATIO = 1.0;

	private UiucDa4002AxialSurfaceV1() {
	}

	public enum ReferenceSliceKind {
		NOMINAL_TRACK,
		INTER_TRACK_MIDPOINT
	}

	public static LookupResult evaluate(Propeller propeller, double advanceRatioJ,
			double rpm) {
		return UiucDa4002MeasuredRotorModel.evaluate(propeller, advanceRatioJ, rpm);
	}

	public static DimensionalSample sample(
			Propeller propeller,
			double advanceRatioJ,
			double rpm,
			double airDensityKgPerCubicMeter,
			double dynamicViscosityPascalSeconds
	) {
		return UiucDa4002MeasuredRotorModel.sample(
				propeller,
				advanceRatioJ,
				rpm,
				airDensityKgPerCubicMeter,
				dynamicViscosityPascalSeconds
		);
	}

	public static PropellerEnvelope envelope(Propeller propeller) {
		StaticRpmEnvelope staticEnvelope = UiucDa4002MeasuredRotorModel
				.staticRpmEnvelope(propeller);
		List<NominalTrackEnvelope> tracks = UiucDa4002MeasuredRotorModel
				.nominalTrackEnvelopes(propeller);
		return new PropellerEnvelope(
				propeller,
				propeller.diameterMeters(),
				staticEnvelope,
				tracks,
				referenceReynoldsEnvelope(propeller, staticEnvelope, tracks)
		);
	}

	public static List<ReferenceSlice> referenceSlices() {
		List<ReferenceSlice> slices = new ArrayList<>();
		for (Propeller propeller : Propeller.values()) {
			List<NominalTrackEnvelope> tracks = UiucDa4002MeasuredRotorModel
					.nominalTrackEnvelopes(propeller);
			for (int index = 0; index < tracks.size(); index++) {
				NominalTrackEnvelope track = tracks.get(index);
				slices.add(new ReferenceSlice(
						propeller,
						track.nominalRpm(),
						ReferenceSliceKind.NOMINAL_TRACK
				));
				if (index + 1 < tracks.size()) {
					double midpoint = (track.nominalRpm()
							+ tracks.get(index + 1).nominalRpm()) * 0.5;
					slices.add(new ReferenceSlice(
							propeller,
							midpoint,
							ReferenceSliceKind.INTER_TRACK_MIDPOINT
					));
				}
			}
		}
		return List.copyOf(slices);
	}

	public static int staticSourceRowCount() {
		return UiucDa4002StaticPerformanceLookup.curves().stream()
				.mapToInt(curve -> curve.rows().size())
				.sum();
	}

	public static int advanceSourceRowCount() {
		return UiucDa4002AdvancePerformanceLookup.curves().stream()
				.mapToInt(curve -> curve.rows().size())
				.sum();
	}

	public static String sourceDataSha256() {
		StringBuilder canonical = new StringBuilder("uiuc-da4002-source-data-v1\n");
		for (Propeller propeller : Propeller.values()) {
			BladeGeometry geometry = propeller.geometry();
			line(canonical, "propeller", propeller.name(), propeller.id(),
					hex(propeller.diameterMeters()), geometry.id(), geometry.sourceUrl(),
					Integer.toString(geometry.bladeCount()));
			for (BladeStation station : geometry.stations()) {
				line(canonical, "geometry-row", geometry.id(),
						hex(station.radialFraction()), hex(station.chordToRadius()),
						hex(station.pitchAngleRadians()));
			}
		}
		for (UiucDa4002StaticPerformanceLookup.StaticCurve curve
				: UiucDa4002StaticPerformanceLookup.curves()) {
			line(canonical, "static-curve", curve.id(), curve.sourceUrl(),
					curve.geometry().id(), hex(curve.referenceDiameterMeters()));
			for (UiucDa4002StaticPerformanceLookup.StaticRow row : curve.rows()) {
				line(canonical, "static-row", curve.id(), hex(row.rpm()),
						hex(row.thrustCoefficientCt()), hex(row.powerCoefficientCp()));
			}
		}
		for (UiucDa4002AdvancePerformanceLookup.AdvanceCurve curve
				: UiucDa4002AdvancePerformanceLookup.curves()) {
			line(canonical, "advance-curve", curve.id(), curve.resourceName(),
					curve.sourceUrl(), curve.geometry().id(),
					hex(curve.referenceDiameterMeters()), hex(curve.rpm()));
			for (UiucDa4002AdvancePerformanceLookup.AdvanceRow row : curve.rows()) {
				line(canonical, "advance-row", curve.id(), hex(row.advanceRatioJ()),
						hex(row.thrustCoefficientCt()), hex(row.powerCoefficientCp()),
						hex(row.propulsiveEfficiencyEta()));
			}
		}
		return sha256(canonical.toString());
	}

	public static String interpolationAlgorithmSha256() {
		return sha256(INTERPOLATION_ALGORITHM_ID + "\n");
	}

	public static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(
					value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}

	private static ReferenceReynoldsEnvelope referenceReynoldsEnvelope(
			Propeller propeller,
			StaticRpmEnvelope staticEnvelope,
			List<NominalTrackEnvelope> tracks
	) {
		double minimumForwardRotational = Double.POSITIVE_INFINITY;
		double maximumForwardRotational = Double.NEGATIVE_INFINITY;
		double minimumForwardResultant = Double.POSITIVE_INFINITY;
		double maximumForwardResultant = Double.NEGATIVE_INFINITY;
		for (NominalTrackEnvelope track : tracks) {
			double rotational = reynolds75(propeller, track.nominalRpm(), 0.0);
			double resultant = reynolds75(
					propeller,
					track.nominalRpm(),
					track.maximumSupportedAdvanceRatioJ()
			);
			minimumForwardRotational = Math.min(minimumForwardRotational, rotational);
			maximumForwardRotational = Math.max(maximumForwardRotational, rotational);
			minimumForwardResultant = Math.min(minimumForwardResultant, rotational);
			maximumForwardResultant = Math.max(maximumForwardResultant, resultant);
		}
		return new ReferenceReynoldsEnvelope(
				REFERENCE_AIR_DENSITY_KG_PER_CUBIC_METER,
				REFERENCE_DYNAMIC_VISCOSITY_PASCAL_SECONDS,
				reynolds75(propeller, staticEnvelope.minimumRpm(), 0.0),
				reynolds75(propeller, staticEnvelope.maximumRpm(), 0.0),
				minimumForwardRotational,
				maximumForwardRotational,
				minimumForwardResultant,
				maximumForwardResultant,
				true
		);
	}

	private static double reynolds75(Propeller propeller, double rpm,
			double advanceRatioJ) {
		double radius = propeller.diameterMeters() * 0.5;
		double revolutionsPerSecond = rpm / 60.0;
		double omega = 2.0 * Math.PI * revolutionsPerSecond;
		double rotationalSpeed = 0.75 * omega * radius;
		double axialSpeed = advanceRatioJ * revolutionsPerSecond
				* propeller.diameterMeters();
		double relativeSpeed = Math.hypot(rotationalSpeed, axialSpeed);
		double chord75 = propeller.geometry().chordToRadiusAt(0.75) * radius;
		return REFERENCE_AIR_DENSITY_KG_PER_CUBIC_METER
				* relativeSpeed * chord75
				/ REFERENCE_DYNAMIC_VISCOSITY_PASCAL_SECONDS;
	}

	private static void line(StringBuilder builder, String... fields) {
		for (int index = 0; index < fields.length; index++) {
			if (index > 0) {
				builder.append('|');
			}
			builder.append(fields[index]);
		}
		builder.append('\n');
	}

	private static String hex(double value) {
		return Long.toUnsignedString(Double.doubleToLongBits(value), 16);
	}

	public record PropellerEnvelope(
			Propeller propeller,
			double diameterMeters,
			StaticRpmEnvelope staticRpmEnvelope,
			List<NominalTrackEnvelope> nominalTrackEnvelopes,
			ReferenceReynoldsEnvelope referenceReynoldsEnvelope
	) {
		public PropellerEnvelope {
			nominalTrackEnvelopes = List.copyOf(nominalTrackEnvelopes);
		}

		public double minimumForwardRpm() {
			return nominalTrackEnvelopes.get(0).nominalRpm();
		}

		public double maximumForwardRpm() {
			return nominalTrackEnvelopes.get(nominalTrackEnvelopes.size() - 1)
					.nominalRpm();
		}
	}

	public record ReferenceReynoldsEnvelope(
			double airDensityKgPerCubicMeter,
			double dynamicViscosityPascalSeconds,
			double minimumStaticRotationalReynolds75,
			double maximumStaticRotationalReynolds75,
			double minimumForwardRotationalReynolds75,
			double maximumForwardRotationalReynolds75,
			double minimumForwardResultantReynolds75,
			double maximumForwardResultantReynolds75,
			boolean diagnosticOnly
	) {
	}

	public record ReferenceSlice(
			Propeller propeller,
			double rpm,
			ReferenceSliceKind kind
	) {
	}
}
