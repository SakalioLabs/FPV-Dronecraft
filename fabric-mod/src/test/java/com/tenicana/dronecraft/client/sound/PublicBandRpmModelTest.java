package com.tenicana.dronecraft.client.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticEmissionFrame;
import com.tenicana.dronecraft.acoustics.AcousticListenerFrame;
import com.tenicana.dronecraft.acoustics.AcousticSourceFrame;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.AxisymmetricSourceDirectivity;
import com.tenicana.dronecraft.acoustics.OrderTrackedRotorModel;
import com.tenicana.dronecraft.acoustics.RotorAcousticState;
import com.tenicana.dronecraft.acoustics.RotorBroadbandGainCurve;
import com.tenicana.dronecraft.acoustics.RotorOperatingPointGainCurve;

/** Public relative-band evidence; no measured profile or target FPV calibration is created. */
final class PublicBandRpmModelTest {

	private static final List<String> BANDS = List.of("low", "mid", "high");

	@Test
	void javaSourceAndDirectivityReproduceFrozenPublicHoldoutPredictions() throws Exception {
		var resource = getClass().getResourceAsStream("/acoustics/enola-ms1101-band-rpm-result-v1.json");
		assertNotNull(resource);
		JsonObject report;
		try (var reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
			report = JsonParser.parseReader(reader).getAsJsonObject();
		}
		assertEquals("68cfefb62459845a66b8e0bd58f05f534c7d4241143cd65a82ee72dfaf732577",
				report.get("protocol_sha256").getAsString());
		assertFalse(report.get("release_profile_eligible").getAsBoolean());
		for (String modelName : List.of("shared_total_gain", "independent_band_gain")) {
			JsonObject model = report.getAsJsonObject("models").getAsJsonObject(modelName);
			JsonObject reference = model.getAsJsonObject("reference_band_level_db");
			double totalReference = BANDS.stream().mapToDouble(band ->
					Math.pow(10.0, number(reference, band) / 10.0)).sum();
			AcousticBands fractions = new AcousticBands(
					Math.pow(10.0, number(reference, "low") / 10.0) / totalReference,
					Math.pow(10.0, number(reference, "mid") / 10.0) / totalReference,
					Math.pow(10.0, number(reference, "high") / 10.0) / totalReference
			);
			List<RotorOperatingPointGainCurve.Anchor> scalarAnchors = new ArrayList<>();
			List<RotorBroadbandGainCurve.Anchor> bandAnchors = new ArrayList<>();
			for (var anchor : model.getAsJsonObject("anchors").entrySet()) {
				double rpm = Double.parseDouble(anchor.getKey());
				JsonObject values = anchor.getValue().getAsJsonObject();
				if (modelName.equals("shared_total_gain")) {
					assertEquals(number(values, "low"), number(values, "mid"));
					assertEquals(number(values, "low"), number(values, "high"));
					scalarAnchors.add(new RotorOperatingPointGainCurve.Anchor(rpm, 0, 0, number(values, "low")));
				} else {
					bandAnchors.add(new RotorBroadbandGainCurve.Anchor(rpm, number(values, "low"),
							number(values, "mid"), number(values, "high")));
				}
			}
			OrderTrackedRotorModel sourceModel = new OrderTrackedRotorModel(new OrderTrackedRotorModel.Parameters(
					1, 1, 0, 0, 0, 0, 1,
					scalarAnchors.isEmpty() ? RotorOperatingPointGainCurve.unity()
							: new RotorOperatingPointGainCurve(scalarAnchors), fractions,
					bandAnchors.isEmpty() ? RotorBroadbandGainCurve.unity() : new RotorBroadbandGainCurve(bandAnchors)
			));
			JsonObject coefficients = model.getAsJsonObject("directivity");
			var directivity = new AxisymmetricSourceDirectivity.Parameters(150, 1000, 8000,
					polynomial(coefficients, "low"), polynomial(coefficients, "mid"), polynomial(coefficients, "high"));
			JsonObject validation = report.getAsJsonObject("validation").getAsJsonObject(modelName);
			List<Double> planeErrors = new ArrayList<>();
			int compared = 0;
			for (var item : validation.getAsJsonArray("validation_predictions")) {
				JsonObject prediction = item.getAsJsonObject();
				double rpm = number(prediction, "rpm");
				int mic = prediction.get("microphone").getAsInt();
				JsonObject observation = null;
				for (var measured : report.getAsJsonArray("observations")) {
					JsonObject row = measured.getAsJsonObject();
					if (number(row, "rpm") == rpm && row.get("microphone").getAsInt() == mic) {
						observation = row;
						break;
					}
				}
				assertNotNull(observation);
				double angle = Math.toRadians(number(observation, "elevation_deg"));
				// Unit power/load probes isolate the fitted source gains; these are not aircraft metadata.
				var source = new AcousticSourceFrame(1, 1, AcousticVector.ZERO, AcousticVector.ZERO,
						new AcousticVector(0, 1, 0), 0.1,
						List.of(new RotorAcousticState(rpm, 1, 1, 0.1, 2, 1, 1, 0)));
				var listener = new AcousticListenerFrame(new AcousticVector(Math.cos(angle), Math.sin(angle), 0),
						AcousticVector.ZERO);
				AcousticEmissionFrame output = AxisymmetricSourceDirectivity.apply(
						sourceModel.evaluate(source, 48000), source, listener, directivity);
				double[] energies = {output.broadbandEnergy().low(), output.broadbandEnergy().mid(), output.broadbandEnergy().high()};
				for (int bandIndex = 0; bandIndex < BANDS.size(); bandIndex++) {
					String band = BANDS.get(bandIndex);
					double level = 10.0 * Math.log10(energies[bandIndex] * totalReference);
					assertEquals(number(prediction.getAsJsonObject("predicted_db"), band), level, 1e-9,
							modelName + " rpm=" + rpm + " mic=" + mic + " band=" + band);
					double error = level - number(observation.getAsJsonObject("residual_band_level_db"), band);
					assertEquals(number(prediction.getAsJsonObject("signed_error_db"), band), error, 1e-9);
					if (mic == 3) {
						planeErrors.add(error);
					}
					compared++;
				}
			}
			assertEquals(36, compared);
			assertEquals(6, planeErrors.size());
			JsonObject primary = validation.getAsJsonObject("primary_plane");
			assertEquals(number(primary, "pooled_rmse_db"),
					Math.sqrt(planeErrors.stream().mapToDouble(value -> value * value).average().orElseThrow()), 1e-9);
			assertEquals(number(primary, "maximum_absolute_error_db"),
					planeErrors.stream().mapToDouble(Math::abs).max().orElseThrow(), 1e-9);
		}
	}

	private static double number(JsonObject object, String field) {
		return object.get(field).getAsDouble();
	}

	private static AxisymmetricSourceDirectivity.EvenPolynomial polynomial(JsonObject models, String band) {
		JsonObject model = models.getAsJsonObject(band);
		return new AxisymmetricSourceDirectivity.EvenPolynomial(number(model, "c2_db"), number(model, "c4_db"), -120, 60);
	}
}
