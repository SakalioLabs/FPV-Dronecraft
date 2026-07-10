package com.tenicana.dronecraft.sim;

/**
 * Allocation-free runtime projection of the measured UIUC DA4002 5x3.75
 * axial CT/CP surface frozen on {@code sim/lab}.
 *
 * <p>The lookup deliberately exposes only positive-thrust samples inside the
 * measured, non-rectangular J/RPM envelope. It never clamps or extrapolates,
 * and it does not include shadow residuals, validation gates, exporters, CFD
 * tooling, or the measured negative-thrust tail.</p>
 */
public final class UiucDa4002FiveInchRuntimeLookup {
	public static final String VERSION_ID = "uiuc-da4002-5x3.75-positive-axial-runtime-v1";
	public static final String SOURCE_DATA_SHA256 =
			"abf67ed5ba706cba92f97fc18834e846ee7241a03a1179d09c381969421951ad";
	public static final String CURVE_BUNDLE_SHA256 =
			"49f20e2f7ea42771ce07bc2b4b1f371b54e6966616921da09c8bbf82612043cf";
	public static final float PROPELLER_DIAMETER_METERS = 0.127f;

	private static final float EPSILON = 1.0e-7f;
	private static final long UNAVAILABLE = pack(Float.NaN, Float.NaN);

	private static final float[] STATIC_RPM = {
			1_410.000f, 1_976.667f, 2_446.667f, 3_060.000f, 3_550.000f,
			3_986.667f, 4_513.333f, 4_966.667f, 5_533.333f, 5_930.000f,
			6_483.333f, 6_913.333f, 7_440.000f
	};
	private static final float[] STATIC_CT = {
			0.117996f, 0.124035f, 0.123977f, 0.122526f, 0.123508f,
			0.125492f, 0.125092f, 0.124781f, 0.122616f, 0.125383f,
			0.128062f, 0.129589f, 0.129020f
	};
	private static final float[] STATIC_CP = {
			0.097429f, 0.095690f, 0.095134f, 0.093445f, 0.091795f,
			0.091880f, 0.089659f, 0.090452f, 0.087334f, 0.088644f,
			0.091539f, 0.089348f, 0.092551f
	};

	private static final float[] TRACK_RPM = {4_000.0f, 5_000.0f, 6_000.0f};
	private static final float[][] TRACK_J = {
			{
					0.289856f, 0.355047f, 0.430736f, 0.494491f, 0.567634f,
					0.636573f, 0.717168f, 0.782846f, 0.857870f
			},
			{
					0.227509f, 0.285234f, 0.342505f, 0.397835f, 0.458006f,
					0.514076f, 0.567800f, 0.626719f, 0.684240f, 0.739094f,
					0.800665f, 0.851340f
			},
			{
					0.194068f, 0.236641f, 0.288717f, 0.330812f, 0.378295f,
					0.424751f, 0.472195f, 0.522899f, 0.574100f, 0.616845f,
					0.663208f, 0.711810f, 0.766780f, 0.807060f, 0.852601f,
					0.895451f
			}
	};
	private static final float[][] TRACK_CT = {
			{
					0.103789f, 0.094491f, 0.082005f, 0.071249f, 0.057767f,
					0.044160f, 0.027085f, 0.013266f, -0.002333f
			},
			{
					0.111128f, 0.105898f, 0.099586f, 0.091236f, 0.082270f,
					0.073906f, 0.063216f, 0.050390f, 0.039207f, 0.027097f,
					0.014532f, 0.001860f
			},
			{
					0.115679f, 0.111282f, 0.107194f, 0.102525f, 0.097062f,
					0.089987f, 0.082454f, 0.075170f, 0.065411f, 0.056108f,
					0.046247f, 0.036776f, 0.024892f, 0.015865f, 0.004944f,
					-0.004916f
			}
	};
	private static final float[][] TRACK_CP = {
			{
					0.082921f, 0.077769f, 0.072391f, 0.066598f, 0.059487f,
					0.051524f, 0.041231f, 0.030942f, 0.019114f
			},
			{
					0.086026f, 0.082456f, 0.079005f, 0.074911f, 0.070651f,
					0.066739f, 0.061715f, 0.053635f, 0.046547f, 0.038367f,
					0.029222f, 0.019385f
			},
			{
					0.086629f, 0.084481f, 0.082326f, 0.079433f, 0.076695f,
					0.073238f, 0.069392f, 0.065797f, 0.060353f, 0.054763f,
					0.048703f, 0.042590f, 0.034253f, 0.027701f, 0.019074f,
					0.011186f
			}
	};

	private UiucDa4002FiveInchRuntimeLookup() {
	}

	/**
	 * Returns packed positive-thrust CT/CP coefficients. The upper 32 bits are
	 * CT and the lower 32 bits are CP. Use the accessors below; unavailable
	 * queries carry NaN in both lanes.
	 */
	public static long lookupPositiveThrust(float advanceRatioJ, float rpm) {
		if (!Float.isFinite(advanceRatioJ) || !Float.isFinite(rpm)
				|| advanceRatioJ < 0.0f || rpm <= 0.0f) {
			return UNAVAILABLE;
		}
		if (advanceRatioJ <= EPSILON) {
			return staticCoefficients(rpm);
		}
		if (rpm < TRACK_RPM[0] || rpm > TRACK_RPM[TRACK_RPM.length - 1]) {
			return UNAVAILABLE;
		}

		int lowerTrack = rpm < TRACK_RPM[1] ? 0 : 1;
		if (Float.compare(rpm, TRACK_RPM[lowerTrack]) == 0) {
			return positiveOnly(trackCoefficients(lowerTrack, advanceRatioJ));
		}
		if (Float.compare(rpm, TRACK_RPM[lowerTrack + 1]) == 0) {
			return positiveOnly(trackCoefficients(lowerTrack + 1, advanceRatioJ));
		}

		long lower = trackCoefficients(lowerTrack, advanceRatioJ);
		long upper = trackCoefficients(lowerTrack + 1, advanceRatioJ);
		if (!available(lower) || !available(upper)) {
			return UNAVAILABLE;
		}
		float fraction = (rpm - TRACK_RPM[lowerTrack])
				/ (TRACK_RPM[lowerTrack + 1] - TRACK_RPM[lowerTrack]);
		return positiveOnly(interpolate(lower, upper, fraction));
	}

	public static boolean available(long packedCoefficients) {
		return Float.isFinite(thrustCoefficient(packedCoefficients))
				&& Float.isFinite(powerCoefficient(packedCoefficients));
	}

	public static float thrustCoefficient(long packedCoefficients) {
		return Float.intBitsToFloat((int) (packedCoefficients >>> 32));
	}

	public static float powerCoefficient(long packedCoefficients) {
		return Float.intBitsToFloat((int) packedCoefficients);
	}

	public static float normalizedThrustScale(float advanceRatioJ, float rpm) {
		long forward = lookupPositiveThrust(advanceRatioJ, rpm);
		long zeroAdvance = staticCoefficients(rpm);
		if (!available(forward) || !available(zeroAdvance)) {
			return Float.NaN;
		}
		return thrustCoefficient(forward) / thrustCoefficient(zeroAdvance);
	}

	public static float normalizedPowerScale(float advanceRatioJ, float rpm) {
		long forward = lookupPositiveThrust(advanceRatioJ, rpm);
		long zeroAdvance = staticCoefficients(rpm);
		if (!available(forward) || !available(zeroAdvance)) {
			return Float.NaN;
		}
		return powerCoefficient(forward) / powerCoefficient(zeroAdvance);
	}

	private static long staticCoefficients(float rpm) {
		if (rpm < STATIC_RPM[0] || rpm > STATIC_RPM[STATIC_RPM.length - 1]) {
			return UNAVAILABLE;
		}
		for (int index = 0; index < STATIC_RPM.length; index++) {
			if (Float.compare(rpm, STATIC_RPM[index]) == 0) {
				return pack(STATIC_CT[index], STATIC_CP[index]);
			}
			if (rpm < STATIC_RPM[index]) {
				int lower = index - 1;
				float fraction = (rpm - STATIC_RPM[lower])
						/ (STATIC_RPM[index] - STATIC_RPM[lower]);
				return pack(
						lerp(STATIC_CT[lower], STATIC_CT[index], fraction),
						lerp(STATIC_CP[lower], STATIC_CP[index], fraction)
				);
			}
		}
		return UNAVAILABLE;
	}

	private static long trackCoefficients(int trackIndex, float advanceRatioJ) {
		float[] advanceRatios = TRACK_J[trackIndex];
		if (advanceRatioJ > advanceRatios[advanceRatios.length - 1]) {
			return UNAVAILABLE;
		}
		if (advanceRatioJ < advanceRatios[0]) {
			long zeroAdvance = staticCoefficients(TRACK_RPM[trackIndex]);
			float fraction = advanceRatioJ / advanceRatios[0];
			return pack(
					lerp(thrustCoefficient(zeroAdvance), TRACK_CT[trackIndex][0], fraction),
					lerp(powerCoefficient(zeroAdvance), TRACK_CP[trackIndex][0], fraction)
			);
		}

		for (int index = 0; index < advanceRatios.length; index++) {
			if (Float.compare(advanceRatioJ, advanceRatios[index]) == 0) {
				return pack(TRACK_CT[trackIndex][index], TRACK_CP[trackIndex][index]);
			}
			if (advanceRatioJ < advanceRatios[index]) {
				int lower = index - 1;
				float fraction = (advanceRatioJ - advanceRatios[lower])
						/ (advanceRatios[index] - advanceRatios[lower]);
				return pack(
						lerp(TRACK_CT[trackIndex][lower], TRACK_CT[trackIndex][index], fraction),
						lerp(TRACK_CP[trackIndex][lower], TRACK_CP[trackIndex][index], fraction)
				);
			}
		}
		return UNAVAILABLE;
	}

	private static long positiveOnly(long packedCoefficients) {
		return available(packedCoefficients) && thrustCoefficient(packedCoefficients) > EPSILON
				? packedCoefficients
				: UNAVAILABLE;
	}

	private static long interpolate(long lower, long upper, float fraction) {
		return pack(
				lerp(thrustCoefficient(lower), thrustCoefficient(upper), fraction),
				lerp(powerCoefficient(lower), powerCoefficient(upper), fraction)
		);
	}

	private static float lerp(float lower, float upper, float fraction) {
		return lower + (upper - lower) * fraction;
	}

	private static long pack(float thrustCoefficient, float powerCoefficient) {
		return ((long) Float.floatToRawIntBits(thrustCoefficient) << 32)
				| (Float.floatToRawIntBits(powerCoefficient) & 0xffff_ffffL);
	}
}
