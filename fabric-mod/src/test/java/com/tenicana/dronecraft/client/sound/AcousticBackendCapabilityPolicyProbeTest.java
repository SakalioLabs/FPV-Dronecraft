package com.tenicana.dronecraft.client.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class AcousticBackendCapabilityPolicyProbeTest {
	@Test
	void separatesLiveCapabilityFromCounterfactualPolicyCases() {
		OpenAlNativeCapabilityProbe.Result capability =
				new OpenAlNativeCapabilityProbe.Result(
						"Sound engine",
						true,
						"OpenAL Soft",
						"OpenAL Community",
						"OpenAL Soft",
						"1.1 ALSOFT",
						true,
						true,
						2,
						true,
						false,
						true,
						true,
						0
				);
		AcousticBackendCapabilityPolicyProbe.Result result =
				AcousticBackendCapabilityPolicyProbe.evaluate(capability);
		assertTrue(result.actualNativeEfxEligible());
		Map<String, AcousticBackendCapabilityPolicyProbe.Case> cases =
				result.cases().stream().collect(Collectors.toMap(
						AcousticBackendCapabilityPolicyProbe.Case::name,
						item -> item
				));
		assertFalse(cases.get("actual-device").simulated());
		assertEquals(
				AcousticBackendSelector.RuntimeBackend.OPENAL_EFX,
				cases.get("actual-device").resolvedBackend()
		);
		assertEquals(
				AcousticBackendSelector.RuntimeBackend.JAVA_FDN,
				cases.get("simulated-efx-unavailable-with-java")
						.resolvedBackend()
		);
		assertEquals(
				AcousticBackendSelector.RuntimeBackend.CLEAN,
				cases.get("simulated-efx-unavailable-without-java")
						.resolvedBackend()
		);
		assertEquals(
				AcousticBackendSelector.RuntimeBackend.OPENAL_EFX_PENDING,
				cases.get("simulated-efx-pending-with-java")
						.resolvedBackend()
		);
		assertEquals(
				AcousticBackendSelector.RuntimeBackend.OPENAL_EFX_PENDING,
				cases.get("simulated-explicit-efx-failure")
						.resolvedBackend()
		);
		assertEquals(
				AcousticBackendSelector.RuntimeBackend.CLEAN,
				cases.get("simulated-procedural-audio-disabled")
						.resolvedBackend()
		);
	}
}
