package com.tenicana.dronecraft.client.sound;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.tenicana.dronecraft.acoustics.AcousticSourceProfile;

/**
 * Build-time cross-language gate for a generated profile JSON.
 */
public final class AcousticProfileVerifyCli {
	private AcousticProfileVerifyCli() {
	}

	public static void main(String[] arguments) throws Exception {
		if (arguments.length != 1) {
			throw new IllegalArgumentException(
					"usage: AcousticProfileVerifyCli <profile-json>"
			);
		}
		Path input = Path.of(arguments[0]).toAbsolutePath().normalize();
		AcousticSourceProfile profile;
		try (Reader reader = Files.newBufferedReader(
				input,
				StandardCharsets.UTF_8
		)) {
			profile = AcousticProfileJsonDecoder.decode(reader);
		}
		System.out.printf(
				"verified acoustic profile id=%s rpm_anchors=%d "
						+ "unseen_rpm=%d unseen_angle=%d order_spectrum=%d%n",
				profile.id(),
				profile.sourceModel().operatingPointGainCurve().anchors().size(),
				profile.validation().unseenRpmSamples(),
				profile.validation().unseenAngleSamples(),
				profile.validation().orderSpectrumSamples()
		);
	}
}
