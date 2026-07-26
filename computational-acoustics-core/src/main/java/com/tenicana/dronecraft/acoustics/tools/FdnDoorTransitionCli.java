package com.tenicana.dronecraft.acoustics.tools;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.reverb.ListenerSharedFdn;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Drives the actual FDN parameter smoother between the D078 closed and
 * full-wall-open doorway endpoints.
 */
public final class FdnDoorTransitionCli {
	private static final int SAMPLE_RATE = 48_000;
	private static final double TRANSITION_SECONDS = 0.2;
	private static final int[] CHECKPOINT_SAMPLES =
			{0, 1, 2_400, 4_800, 9_600, 19_200, 48_000};

	private FdnDoorTransitionCli() {
	}

	public static void main(String[] arguments) throws IOException {
		if (arguments.length != 1) {
			throw new IllegalArgumentException(
					"usage: FdnDoorTransitionCli <output-json>"
			);
		}
		Locale.setDefault(Locale.ROOT);
		DoorwayReverbContinuityCli.Result closed =
				DoorwayReverbContinuityCli.run(0, 0);
		DoorwayReverbContinuityCli.Result open =
				DoorwayReverbContinuityCli.run(11, 5);
		ListenerSharedFdn fdn = new ListenerSharedFdn(SAMPLE_RATE);
		fdn.configure(closed.rt60(), closed.wetGain(), 0.0);
		List<Sample> opening = transition(
				fdn,
				open.rt60(),
				open.wetGain()
		);
		List<Sample> closing = transition(
				fdn,
				closed.rt60(),
				closed.wetGain()
		);
		Path output = Path.of(arguments[0]).toAbsolutePath().normalize();
		Files.createDirectories(output.getParent());
		Files.writeString(
				output,
				json(closed, open, opening, closing),
				StandardCharsets.UTF_8
		);
		System.out.printf(
				Locale.ROOT,
				"{\"status\":\"valid-diagnostic\",\"opening_samples\":%d,"
						+ "\"closing_samples\":%d,\"output\":\"%s\"}%n",
				opening.size(),
				closing.size(),
				escape(output.toString())
		);
	}

	private static List<Sample> transition(
			ListenerSharedFdn fdn,
			AcousticBands targetRt60,
			double targetWetGain
	) {
		fdn.configure(targetRt60, targetWetGain, TRANSITION_SECONDS);
		List<Sample> samples = new ArrayList<>();
		int rendered = 0;
		for (int checkpoint : CHECKPOINT_SAMPLES) {
			int length = checkpoint - rendered;
			if (length > 0) {
				float[] silence = new float[length];
				fdn.process(silence, new float[length], 0, length);
				rendered = checkpoint;
			}
			samples.add(new Sample(
					checkpoint,
					(double) checkpoint / SAMPLE_RATE,
					fdn.diagnosticCurrentWetGain(),
					fdn.diagnosticTargetWetGain(),
					fdn.diagnosticMeanCurrentFeedbackGain(),
					fdn.diagnosticMeanTargetFeedbackGain()
			));
		}
		return samples;
	}

	private static String json(
			DoorwayReverbContinuityCli.Result closed,
			DoorwayReverbContinuityCli.Result open,
			List<Sample> opening,
			List<Sample> closing
	) {
		return String.format(
				Locale.ROOT,
				"{%n"
						+ "  \"schema_version\": 1,%n"
						+ "  \"status\": \"valid-diagnostic\",%n"
						+ "  \"sample_rate_hz\": %d,%n"
						+ "  \"environment_update_ticks\": 20,%n"
						+ "  \"transition_seconds\": %.17g,%n"
						+ "  \"release_calibrated\": false,%n"
						+ "  \"closed_endpoint\": %s,%n"
						+ "  \"open_endpoint\": %s,%n"
						+ "  \"opening\": [%n    %s%n  ],%n"
						+ "  \"closing\": [%n    %s%n  ],%n"
						+ "  \"claim_boundary\": \"Actual FDN one-pole "
						+ "parameter smoothing between D078 static endpoints; "
						+ "not a simulated moving door or audio-device "
						+ "scheduling test.\"%n"
						+ "}%n",
				SAMPLE_RATE,
				TRANSITION_SECONDS,
				endpoint(closed),
				endpoint(open),
				join(opening),
				join(closing)
		);
	}

	private static String endpoint(
			DoorwayReverbContinuityCli.Result result
	) {
		return String.format(
				Locale.ROOT,
				"{\"rt60_s\":%s,\"wet_gain\":%.17g}",
				bands(result.rt60()),
				result.wetGain()
		);
	}

	private static String join(List<Sample> samples) {
		return String.join(
				",\n    ",
				samples.stream().map(FdnDoorTransitionCli::sample).toList()
		);
	}

	private static String sample(Sample sample) {
		return String.format(
				Locale.ROOT,
				"{\"samples\":%d,\"seconds\":%.17g,"
						+ "\"current_wet_gain\":%.17g,"
						+ "\"target_wet_gain\":%.17g,"
						+ "\"current_feedback_gain\":%s,"
						+ "\"target_feedback_gain\":%s}",
				sample.samples(),
				sample.seconds(),
				sample.currentWetGain(),
				sample.targetWetGain(),
				bands(sample.currentFeedbackGain()),
				bands(sample.targetFeedbackGain())
		);
	}

	private static String bands(AcousticBands value) {
		return String.format(
				Locale.ROOT,
				"{\"low\":%.17g,\"mid\":%.17g,\"high\":%.17g}",
				value.low(),
				value.mid(),
				value.high()
		);
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private record Sample(
			int samples,
			double seconds,
			double currentWetGain,
			double targetWetGain,
			AcousticBands currentFeedbackGain,
			AcousticBands targetFeedbackGain
	) {
	}
}
