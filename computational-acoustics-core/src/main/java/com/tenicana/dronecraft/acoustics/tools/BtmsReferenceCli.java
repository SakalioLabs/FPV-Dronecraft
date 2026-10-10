package com.tenicana.dronecraft.acoustics.tools;

import com.tenicana.dronecraft.acoustics.diffraction.BtmsInfiniteWedgeGeometry;
import com.tenicana.dronecraft.acoustics.diffraction.BtmsInfiniteWedgeReference;
import com.tenicana.dronecraft.acoustics.diffraction.ComplexPressure;

import java.util.Locale;

/**
 * Command-line adapter for generating auditable BTMS reference fixtures.
 */
public final class BtmsReferenceCli {
	private BtmsReferenceCli() {
	}

	public static void main(String[] arguments) {
		if (arguments.length != 7) {
			throw new IllegalArgumentException(
					"expected comma-separated btmsArgs: frequencyHz,"
							+ "sourceRadiusM,receiverRadiusM,axialSeparationM,"
							+ "sourceAzimuthRad,receiverAzimuthRad,"
							+ "exteriorWedgeAngleRad"
			);
		}
		double frequencyHertz = parse(arguments[0], "frequencyHz");
		BtmsInfiniteWedgeGeometry geometry =
				new BtmsInfiniteWedgeGeometry(
						parse(arguments[1], "sourceRadiusM"),
						parse(arguments[2], "receiverRadiusM"),
						parse(arguments[3], "axialSeparationM"),
						parse(arguments[4], "sourceAzimuthRad"),
						parse(arguments[5], "receiverAzimuthRad"),
						parse(arguments[6], "exteriorWedgeAngleRad"),
						343.0
				);
		BtmsInfiniteWedgeReference reference =
				new BtmsInfiniteWedgeReference(
						BtmsInfiniteWedgeReference.Settings.reference()
				);
		ComplexPressure pressure =
				reference.complexPressure(frequencyHertz, geometry);
		double normalizedMagnitude = pressure.magnitude()
				* geometry.shortestDiffractedPathMeters();
		System.out.printf(
				Locale.ROOT,
				"{\"frequency_hz\":%.17g,\"real\":%.17g,"
						+ "\"imaginary\":%.17g,\"magnitude\":%.17g,"
						+ "\"normalized_magnitude\":%.17g,"
						+ "\"normalized_db\":%.17g}%n",
				frequencyHertz,
				pressure.real(),
				pressure.imaginary(),
				pressure.magnitude(),
				normalizedMagnitude,
				20.0 * Math.log10(normalizedMagnitude)
		);
	}

	private static double parse(String value, String name) {
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException(
					name + " must be a floating-point number",
					exception
			);
		}
	}
}
