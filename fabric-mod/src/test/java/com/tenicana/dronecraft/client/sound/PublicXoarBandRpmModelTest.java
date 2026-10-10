package com.tenicana.dronecraft.client.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticSourceFrame;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.OrderTrackedRotorModel;
import com.tenicana.dronecraft.acoustics.RotorAcousticState;
import com.tenicana.dronecraft.acoustics.RotorBroadbandGainCurve;
import com.tenicana.dronecraft.acoustics.RotorOperatingPointGainCurve;

/** Fixed named-observer residual-band evidence, without a measured profile or aircraft calibration. */
final class PublicXoarBandRpmModelTest {
	private static final List<String> BANDS = List.of("low", "mid", "high");
	private static final List<String> MATERIALS = List.of("ABS", "PA12", "Resin");
	private static final List<String> MODELS = List.of("shared_total_gain", "independent_band_gain");
	private static final List<Integer> TRAIN_RPMS = List.of(3900, 9870, 12000);
	private static final double DB_TOLERANCE = 1.0e-9;

	@Test
	void frozenPublicEvidenceRetainsEveryConditionAndFailedQualificationGate() throws Exception {
		JsonObject report = report();
		assertEquals(1, report.get("schema_version").getAsInt());
		assertEquals("enola-xoar-band-rpm-confirmation-v1", report.get("experiment_id").getAsString());
		assertEquals("fresh_rpm_validation", report.get("stage").getAsString());
		assertEquals("5f2ad083f0c50e5ee9ba81318c0bc7ee6f7b5ed32f0405b845a28f2b5f13dc92",
				report.get("protocol_sha256").getAsString());
		assertEquals("9246be7b8d90cd335072b25fc720a10afefa6590873fdf1f7c51263e7c5316fc",
				report.get("analysis_script_sha256").getAsString());
		assertEquals("0691e8a6d6b07708743dc236899a1d7179aaef1f554fe146226e1b8b8f03f11a",
				report.get("model_sha256").getAsString());
		assertEquals("332af708fd2ba80357b0abb74e0d179d4bd8f13cbc94278bb249e3eb3ff7aa78",
				report.get("pre_access_model_lock_sha256").getAsString());
		assertEquals("12064ff9c34ddcc2fe776fe004730053e338941a0259297a787704f2187c40de",
				report.get("training_report_sha256").getAsString());
		assertEquals(4, report.getAsJsonObject("dependencies_sha256").size());
		assertTrue(report.get("passes_registered_improvement_criterion").getAsBoolean());
		assertFalse(report.get("primary_all_errors_within_three_db").getAsBoolean());
		assertFalse(report.get("release_profile_eligible").getAsBoolean());
		assertFalse(report.get("release_profile_fitting_allowed").getAsBoolean());

		Set<String> expectedConditions = new HashSet<>();
		for (String material : MATERIALS) {
			for (int rpm : TRAIN_RPMS) {
				expectedConditions.add(condition(material, rpm, "train"));
			}
			expectedConditions.add(condition(material, 7350, "validation"));
		}
		Set<String> actualConditions = new HashSet<>();
		Set<String> missingPrimary = new HashSet<>();
		Set<String> detectedHigher = new HashSet<>();
		int trainingCount = 0;
		int validationCount = 0;
		assertEquals(12, report.getAsJsonArray("observations").size());
		for (var item : report.getAsJsonArray("observations")) {
			JsonObject observation = item.getAsJsonObject();
			assertEquals(3, observation.get("microphone").getAsInt());
			String split = observation.get("split").getAsString();
			String key = condition(observation.get("material").getAsString(), observation.get("rpm").getAsInt(), split);
			assertTrue(actualConditions.add(key), "duplicate condition " + key);
			if (split.equals("train")) {
				trainingCount++;
			} else {
				assertEquals("validation", split);
				validationCount++;
			}
			assertEquals(Set.copyOf(BANDS), observation.getAsJsonObject("residual_band_level_db").keySet());
			Set<Integer> orders = new HashSet<>();
			assertEquals(16, observation.getAsJsonArray("rotor_tones").size());
			for (var toneItem : observation.getAsJsonArray("rotor_tones")) {
				JsonObject tone = toneItem.getAsJsonObject();
				int order = tone.get("order").getAsInt();
				assertTrue(order >= 1 && order <= 16);
				assertTrue(orders.add(order));
				boolean detected = tone.get("detected").getAsBoolean();
				if (!detected) {
					assertTrue(tone.get("isolated_level_db").isJsonNull());
					assertTrue(Double.isFinite(number(tone, "sideband_upper_bound_db")));
					if (order <= 9) {
						missingPrimary.add(key + "/" + order);
					}
				} else if (order > 9) {
					detectedHigher.add(key + "/" + order);
				}
			}
			assertEquals(16, orders.size());
		}
		assertEquals(expectedConditions, actualConditions);
		assertEquals(9, trainingCount);
		assertEquals(3, validationCount);
		JsonObject diagnostics = report.getAsJsonObject("order_diagnostics");
		assertEquals(9, diagnostics.get("required_primary_order_count_per_condition").getAsInt());
		assertEquals(48, missingPrimary.size());
		assertEquals(6, detectedHigher.size());
		assertEquals(35L, missingPrimary.stream().filter(key -> key.contains("/train/")).count());
		assertEquals(13L, missingPrimary.stream().filter(key -> key.contains("/validation/")).count());
		assertEquals(4L, detectedHigher.stream().filter(key -> key.contains("/train/")).count());
		assertEquals(2L, detectedHigher.stream().filter(key -> key.contains("/validation/")).count());
		assertEquals(missingPrimary, diagnosticOrders(diagnostics, "primary_nondetections", true));
		assertEquals(detectedHigher, diagnosticOrders(diagnostics, "higher_order_detections", false));
		assertFalse(diagnostics.get("complete_numeric_order_coverage").getAsBoolean());
		assertFalse(diagnostics.get("higher_order_cutoff_gate_passes").getAsBoolean());
		assertFalse(diagnostics.get("full_numeric_order_fit_attempted").getAsBoolean());
	}

	@Test
	void actualJavaSourceReproducesBothFrozenFixedObserverModelsAndAllErrors() throws Exception {
		JsonObject report = report();
		assertEquals(Set.copyOf(MODELS), report.getAsJsonObject("models").keySet());
		assertEquals(Set.copyOf(MODELS), report.getAsJsonObject("validation").keySet());
		Map<String, JsonObject> observations = new HashMap<>();
		for (var item : report.getAsJsonArray("observations")) {
			JsonObject row = item.getAsJsonObject();
			if (row.get("split").getAsString().equals("validation")) {
				assertEquals(7350, row.get("rpm").getAsInt());
				assertEquals(3, row.get("microphone").getAsInt());
				assertNull(observations.put(row.get("material").getAsString(), row));
			}
		}
		assertEquals(Set.copyOf(MATERIALS), observations.keySet());
		Map<String, Summary> pooled = new HashMap<>();
		Map<String, Map<String, Summary>> perMaterial = new HashMap<>();
		int comparedPredictions = 0;
		for (String modelName : MODELS) {
			JsonObject models = report.getAsJsonObject("models").getAsJsonObject(modelName);
			assertEquals(Set.copyOf(MATERIALS), models.keySet());
			JsonObject validation = report.getAsJsonObject("validation").getAsJsonObject(modelName);
			assertEquals(3, validation.getAsJsonArray("validation_predictions").size());
			assertEquals(Set.copyOf(MATERIALS), validation.getAsJsonObject("per_material").keySet());
			Map<String, double[]> errorsByMaterial = new HashMap<>();
			Map<String, Summary> materialSummaries = new HashMap<>();
			for (var item : validation.getAsJsonArray("validation_predictions")) {
				JsonObject prediction = item.getAsJsonObject();
				String material = prediction.get("material").getAsString();
				assertEquals(7350, prediction.get("rpm").getAsInt());
				assertEquals(3, prediction.get("microphone").getAsInt());
				JsonObject model = models.getAsJsonObject(material);
				assertNotNull(model);
				JsonObject reference = model.getAsJsonObject("reference_band_level_db");
				double totalReference = BANDS.stream().mapToDouble(band -> energy(number(reference, band))).sum();
				AcousticBands fractions = new AcousticBands(energy(number(reference, "low")) / totalReference,
						energy(number(reference, "mid")) / totalReference, energy(number(reference, "high")) / totalReference);
				OrderTrackedRotorModel sourceModel = sourceModel(modelName, model, fractions);
				// Unit power/load isolate gain arithmetic; these values are not observer geometry or aircraft metadata.
				AcousticSourceFrame source = new AcousticSourceFrame(1, 1, AcousticVector.ZERO, AcousticVector.ZERO,
						new AcousticVector(0, 1, 0), 0.1,
						List.of(new RotorAcousticState(7350, 1, 1, 0.1, 2, 1, 1, 0)));
				var output = sourceModel.evaluate(source, 48_000);
				assertTrue(output.tones().isEmpty());
				double[] energies = {output.broadbandEnergy().low(), output.broadbandEnergy().mid(), output.broadbandEnergy().high()};
				double[] errors = new double[BANDS.size()];
				JsonObject measured = observations.get(material).getAsJsonObject("residual_band_level_db");
				for (int index = 0; index < BANDS.size(); index++) {
					String band = BANDS.get(index);
					double level = 10.0 * Math.log10(energies[index] * totalReference);
					String context = modelName + " material=" + material + " band=" + band;
					assertEquals(number(prediction.getAsJsonObject("predicted_db"), band), level, DB_TOLERANCE, context);
					errors[index] = level - number(measured, band);
					assertEquals(number(prediction.getAsJsonObject("signed_error_db"), band), errors[index], DB_TOLERANCE, context);
					comparedPredictions++;
				}
				assertNull(errorsByMaterial.put(material, errors));
				materialSummaries.put(material, assertMetrics(validation.getAsJsonObject("per_material").getAsJsonObject(material),
						List.of(errors)));
			}
			assertEquals(Set.copyOf(MATERIALS), errorsByMaterial.keySet());
			pooled.put(modelName, assertMetrics(validation.getAsJsonObject("primary_fixed_observer"),
					MATERIALS.stream().map(errorsByMaterial::get).toList()));
			perMaterial.put(modelName, materialSummaries);
		}
		assertEquals(18, comparedPredictions);
		Summary baseline = pooled.get("shared_total_gain");
		Summary candidate = pooled.get("independent_band_gain");
		assertTrue(candidate.rmseDb() < baseline.rmseDb());
		assertTrue(candidate.maximumAbsoluteErrorDb() <= baseline.maximumAbsoluteErrorDb());
		assertTrue(candidate.maximumAbsoluteErrorDb() > 3.0);
		for (String material : MATERIALS) {
			Summary materialBaseline = perMaterial.get("shared_total_gain").get(material);
			Summary materialCandidate = perMaterial.get("independent_band_gain").get(material);
			assertTrue(materialCandidate.rmseDb() < materialBaseline.rmseDb(), material);
			assertTrue(materialCandidate.maximumAbsoluteErrorDb() < materialBaseline.maximumAbsoluteErrorDb(), material);
		}
	}

	private static OrderTrackedRotorModel sourceModel(String modelName, JsonObject model, AcousticBands fractions) {
		JsonObject anchors = model.getAsJsonObject("anchors");
		assertEquals(Set.of("3900", "9870", "12000"), anchors.keySet());
		if (modelName.equals("shared_total_gain")) {
			List<RotorOperatingPointGainCurve.Anchor> gains = new ArrayList<>();
			for (int rpm : TRAIN_RPMS) {
				JsonObject values = anchors.getAsJsonObject(Integer.toString(rpm));
				assertEquals(number(values, "low"), number(values, "mid"));
				assertEquals(number(values, "low"), number(values, "high"));
				gains.add(new RotorOperatingPointGainCurve.Anchor(rpm, 0, 0, number(values, "low")));
			}
			return new OrderTrackedRotorModel(new OrderTrackedRotorModel.Parameters(
					1, 1, 0, 0, 0, 0, 1, new RotorOperatingPointGainCurve(gains), fractions));
		}
		assertEquals("independent_band_gain", modelName);
		List<RotorBroadbandGainCurve.Anchor> gains = new ArrayList<>();
		for (int rpm : TRAIN_RPMS) {
			JsonObject values = anchors.getAsJsonObject(Integer.toString(rpm));
			gains.add(new RotorBroadbandGainCurve.Anchor(rpm, number(values, "low"), number(values, "mid"), number(values, "high")));
		}
		return new OrderTrackedRotorModel(new OrderTrackedRotorModel.Parameters(
				1, 1, 0, 0, 0, 0, 1, RotorOperatingPointGainCurve.unity(), fractions, new RotorBroadbandGainCurve(gains)));
	}

	private static Summary assertMetrics(JsonObject expected, List<double[]> errors) {
		assertEquals(errors.size(), expected.get("observation_count").getAsInt());
		assertEquals(errors.size() * BANDS.size(), expected.get("band_error_count").getAsInt());
		double sumSquares = 0.0;
		double maximum = 0.0;
		for (double[] row : errors) {
			assertEquals(BANDS.size(), row.length);
			for (double error : row) {
				sumSquares += error * error;
				maximum = Math.max(maximum, Math.abs(error));
			}
		}
		double rmse = Math.sqrt(sumSquares / (errors.size() * BANDS.size()));
		assertEquals(number(expected, "pooled_rmse_db"), rmse, DB_TOLERANCE);
		assertEquals(number(expected, "maximum_absolute_error_db"), maximum, DB_TOLERANCE);
		for (int index = 0; index < BANDS.size(); index++) {
			double bandSumSquares = 0.0;
			for (double[] row : errors) {
				bandSumSquares += row[index] * row[index];
			}
			assertEquals(number(expected.getAsJsonObject("per_band_rmse_db"), BANDS.get(index)),
					Math.sqrt(bandSumSquares / errors.size()), DB_TOLERANCE);
		}
		return new Summary(rmse, maximum);
	}

	private static Set<String> diagnosticOrders(JsonObject diagnostics, String field, boolean missing) {
		Set<String> result = new HashSet<>();
		for (var item : diagnostics.getAsJsonArray(field)) {
			JsonObject row = item.getAsJsonObject();
			assertEquals(3, row.get("microphone").getAsInt());
			if (missing) {
				assertTrue(row.get("isolated_level_db").isJsonNull());
			}
			String key = condition(row.get("material").getAsString(), row.get("rpm").getAsInt(), row.get("split").getAsString());
			assertTrue(result.add(key + "/" + row.get("order").getAsInt()));
		}
		return result;
	}

	private static JsonObject report() throws Exception {
		try (var resource = PublicXoarBandRpmModelTest.class.getResourceAsStream("/acoustics/enola-xoar-band-rpm-confirmation-v1.json")) {
			assertNotNull(resource);
			byte[] bytes = resource.readAllBytes();
			assertEquals(168_784, bytes.length);
			assertEquals("6cd4a5b13475d61d287ff92dde4769a6c8ce6eda6c77bab6d4cf3b2fe61ff4e1",
					HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
			return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	private static String condition(String material, int rpm, String split) {
		return material + "/" + rpm + "/3/" + split;
	}

	private static double energy(double levelDb) {
		return Math.pow(10.0, levelDb / 10.0);
	}

	private static double number(JsonObject object, String field) {
		return object.get(field).getAsDouble();
	}

	private record Summary(double rmseDb, double maximumAbsoluteErrorDb) {}
}
