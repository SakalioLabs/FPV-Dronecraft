package com.tenicana.dronecraft.client.sound;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tenicana.dronecraft.acoustics.AcousticProfileKey;
import com.tenicana.dronecraft.acoustics.AcousticSourceProfile;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AudioAssetCalibrationTest {
	private static final AcousticProfileKey KEY =
			new AcousticProfileKey("test_quad", 4, 3, 63, 7);

	@Test
	void signedPcmScalingHasIndependentExactRoundingOracles() {
		Fixture fixture = fixture();
		int source = fixture.samples[0];
		assertEquals(1000, source);
		assertEquals(-source, fixture.samples[1]);
		assertArrayEquals(new short[]{250, -250},
				allSamples(fixture.verified.cursor(false).read(4, .25)));
		assertArrayEquals(new short[]{(short) source, (short) -source},
				allSamples(fixture.verified.cursor(false).read(4, 1.0)));
		assertArrayEquals(new short[]{4_000, -4_000},
				allSamples(fixture.verified.cursor(false).read(4, 4.0)));
		double plus24Amplitude = Math.pow(10, 24.0 / 20.0);
		assertEquals((short) Math.round(source * plus24Amplitude),
				allSamples(fixture.verified.cursor(false).read(2, plus24Amplitude))[0]);
		assertArrayEquals(new short[]{(short) Math.round(source * plus24Amplitude),
				(short) Math.round(-source * plus24Amplitude)},
				allSamples(fixture.verified.cursor(false).read(4, plus24Amplitude)));
	}

	@Test
	void repeatFiniteEofFreshCursorAndCloseHaveDistinctSemantics() {
		Fixture fixture = fixture();
		VerifiedAssetPcm.Cursor repeat = fixture.verified.cursor(true);
		assertArrayEquals(new short[]{1000, -1000, 1000, -1000},
				allSamples(repeat.read(8, 1.0)));

		VerifiedAssetPcm.Cursor finite = fixture.verified.cursor(false);
		assertArrayEquals(new short[]{1000, -1000}, allSamples(finite.read(8, 1.0)));
		assertEquals(0, finite.read(8, 1.0).remaining());
		assertArrayEquals(new short[]{1000, -1000}, allSamples(fixture.verified.cursor(false).read(4, 1.0)));
		finite.close();
		assertEquals(0, finite.read(4, 1.0).remaining());
		assertEquals(0, repeat.read(0, 1.0).remaining());
	}

	@Test
	void verificationRejectsIdentityLengthAndReferenceRmsMismatch() {
		Fixture fixture = fixture();
		byte[] changed = fixture.pcm.clone();
		changed[0] ^= 1;
		assertThrows(IllegalArgumentException.class,
				() -> VerifiedAssetPcm.verify(fixture.calibration, changed));
		assertThrows(IllegalArgumentException.class,
				() -> VerifiedAssetPcm.verify(fixture.calibration, new byte[fixture.pcm.length - 1]));

		AudioAssetCalibration wrongRms = calibration(fixture.pcm, .1, fixture.samples.length,
				fixture.samples.length, fixture.calibration.referencePressureRmsPa(), 0);
		assertThrows(IllegalArgumentException.class,
				() -> VerifiedAssetPcm.verify(wrongRms, fixture.pcm));
	}

	@Test
	void verificationRejectsPlus24DbHeadroomOverflow() {
		short[] peakSparse = new short[100];
		peakSparse[0] = 26_214; // approximately 0.8 full scale; RMS is approximately 0.08.
		byte[] pcm = littleEndian(peakSparse);
		double rms = Math.abs((double) peakSparse[0] / 32768.0) / 10.0;
		AudioAssetCalibration calibration = calibration(pcm, rms, 100, 100,
				rms * .25, 24.0);
		assertThrows(IllegalArgumentException.class, () -> VerifiedAssetPcm.verify(calibration, pcm));
	}

	@Test
	void calibrationCopiesNestedResponseGridAndRejectsMutation() {
		Fixture fixture = fixture();
		List<Double> rpm = new ArrayList<>(List.of(5_000.0, 10_000.0, 20_000.0));
		List<Double> axes = new ArrayList<>(List.of(-1.0, 0.0, 1.0));
		List<List<Double>> grid = new ArrayList<>();
		grid.add(new ArrayList<>(List.of(-12.0, -6.0, 0.0)));
		grid.add(new ArrayList<>(List.of(-12.0, 0.0, 12.0)));
		grid.add(new ArrayList<>(List.of(12.0, 18.0, 24.0)));
		AudioAssetCalibration copy = new AudioAssetCalibration(
				fixture.calibration.encodedSha256(), fixture.calibration.decodedSha256(),
				fixture.calibration.sampleRate(), fixture.calibration.frames(), 0,
				fixture.samples.length, fixture.calibration.referenceDigitalRms(),
				fixture.calibration.referencePressureRmsPa(), fixture.calibration.fullScalePressurePa(),
				10_000, 0, rpm, axes, grid);
		double original = copy.responseGainDb().get(0).get(0);
		grid.get(0).set(0, 99.0);
		rpm.set(0, 1.0);
		axes.set(0, 0.0);
		assertEquals(original, copy.responseGainDb().get(0).get(0));
		assertEquals(5_000.0, copy.rpmAnchors().get(0));
		assertEquals(-1.0, copy.axisCosineAnchors().get(0));
		assertThrows(UnsupportedOperationException.class, () -> copy.rpmAnchors().set(0, 0.0));
		assertThrows(UnsupportedOperationException.class, () -> copy.responseGainDb().get(0).set(0, 0.0));
	}

	@Test
	void responseInterpolatesLogRpmAndSignedAxisAndRejectsExtrapolation() {
		AudioAssetCalibration calibration = fixture().calibration;
		assertTrue(calibration.contains(10_000, -1));
		assertFalse(calibration.contains(4_999, 0));
		assertFalse(calibration.contains(10_000, 1.0001));
		assertFalse(calibration.contains(Double.NaN, 0));
		assertThrows(IllegalArgumentException.class, () -> calibration.amplitude(4_999, 0));
		assertThrows(IllegalArgumentException.class, () -> calibration.amplitude(10_000, 1.01));

		double expectedDb = 13.5; // bilinear midpoint: 6 dB at 10k, 21 dB at 20k.
		double expected = Math.pow(10, expectedDb / 20.0);
		double rpm = Math.sqrt(10_000.0 * 20_000.0);
		assertEquals(expected, calibration.amplitude(rpm, .5), 1e-12);
		assertEquals(1.0, calibration.amplitude(10_000, 0), 1e-12);
		assertThrows(IllegalArgumentException.class,
				() -> calibration.amplitude(20_001, 0));
	}

	@Test
	void unevaluatedOrMissingEvidenceNeverAdmitsReleaseDeclarations() {
		Fixture fixture = fixture();
		AudioAssetProfile profile = new AudioAssetProfile("test:asset", KEY, "MOTOR",
				"test:sounds/test.ogg", fixture.calibration,
				AcousticSourceProfile.Validation.notEvaluated(),
				new AudioAssetProfile.Evidence(URI.create("https://example.invalid/source"),
						"", "", "", "", "", "", "0".repeat(64), "0".repeat(64),
						"1".repeat(64), "2".repeat(64), "3".repeat(64)));
		assertFalse(profile.validation().passesReleaseGate());
		assertThrows(IllegalArgumentException.class, profile::requireReleaseDeclarations);

		AudioAssetProfile missingEvidence = new AudioAssetProfile("test:asset", KEY, "MOTOR",
				"test:sounds/test.ogg", fixture.calibration,
				new AcousticSourceProfile.Validation(true, 1, 1, 1, 0, 0, 0),
				profile.evidence());
		assertTrue(missingEvidence.validation().passesReleaseGate());
		assertThrows(IllegalArgumentException.class, missingEvidence::requireReleaseDeclarations);
	}

	private static Fixture fixture() {
		short[] samples = {1000, -1000};
		byte[] pcm = littleEndian(samples);
		double rms = 1000.0 / 32768.0;
		AudioAssetCalibration calibration = calibration(pcm, rms, samples.length,
				samples.length, rms, 24.0);
		return new Fixture(samples, pcm, calibration, VerifiedAssetPcm.verify(calibration, pcm));
	}

	private static AudioAssetCalibration calibration(byte[] pcm, double rms, int frames,
			int window, double pressure, double maxGain) {
		List<Double> rpms = List.of(5_000.0, 10_000.0, 20_000.0);
		List<Double> axes = List.of(-1.0, 0.0, 1.0);
		List<List<Double>> gains = List.of(
				List.of(-12.0, -6.0, 0.0),
				List.of(-12.0, 0.0, 12.0),
				List.of(12.0, 18.0, maxGain));
		return new AudioAssetCalibration("a".repeat(64), VerifiedAssetPcm.sha256(pcm),
				48_000, frames, 0, window, rms, pressure, 1.0, 10_000, 0,
				rpms, axes, gains);
	}

	private static byte[] littleEndian(short[] samples) {
		ByteBuffer bytes = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
		for (short sample : samples) bytes.putShort(sample);
		return bytes.array();
	}

	private static short[] allSamples(ByteBuffer data) {
		short[] result = new short[data.remaining() / 2];
		for (int i = 0; i < result.length; i++) result[i] = data.getShort();
		return result;
	}
	private record Fixture(short[] samples, byte[] pcm, AudioAssetCalibration calibration,
			VerifiedAssetPcm verified) {}
}
