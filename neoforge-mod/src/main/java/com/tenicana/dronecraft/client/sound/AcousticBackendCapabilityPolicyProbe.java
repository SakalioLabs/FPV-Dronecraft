package com.tenicana.dronecraft.client.sound;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Pure capability-policy matrix; it never changes the active OpenAL device.
 */
final class AcousticBackendCapabilityPolicyProbe {
	private AcousticBackendCapabilityPolicyProbe() {
	}

	static Result evaluate(OpenAlNativeCapabilityProbe.Result capability) {
		Objects.requireNonNull(capability, "capability");
		boolean nativeEligible = capability.activeContext()
				&& capability.efxSupported()
				&& capability.maximumAuxiliarySends() >= 1
				&& capability.efxResourcesCreated()
				&& capability.efxResourcesReleased()
				&& capability.alErrorCode() == 0;
		List<Case> cases = List.of(
				resolve(
						"actual-device",
						false,
						AcousticBackendSelector.Mode.DEFAULT,
						true,
						true,
						true,
						nativeEligible
								? OpenAlEfxController.Status.OPERATIONAL
								: OpenAlEfxController.Status
										.EXTENSION_UNAVAILABLE
				),
				resolve(
						"simulated-efx-unavailable-with-java",
						true,
						AcousticBackendSelector.Mode.DEFAULT,
						true,
						true,
						true,
						OpenAlEfxController.Status
								.EXTENSION_UNAVAILABLE
				),
				resolve(
						"simulated-efx-unavailable-without-java",
						true,
						AcousticBackendSelector.Mode.DEFAULT,
						true,
						false,
						true,
						OpenAlEfxController.Status
								.EXTENSION_UNAVAILABLE
				),
				resolve(
						"simulated-efx-pending-with-java",
						true,
						AcousticBackendSelector.Mode.DEFAULT,
						true,
						true,
						true,
						OpenAlEfxController.Status.WAITING_SOURCES
				),
				resolve(
						"simulated-explicit-efx-failure",
						true,
						AcousticBackendSelector.Mode.OPENAL_EFX,
						true,
						true,
						true,
						OpenAlEfxController.Status.CONTEXT_FAILED
				),
				resolve(
						"simulated-procedural-audio-disabled",
						true,
						AcousticBackendSelector.Mode.DEFAULT,
						false,
						true,
						true,
						OpenAlEfxController.Status.OPERATIONAL
				)
		);
		return new Result(
				nativeEligible,
				capability.efxSupported(),
				capability.maximumAuxiliarySends(),
				cases
		);
	}

	private static Case resolve(
			String name,
			boolean simulated,
			AcousticBackendSelector.Mode mode,
			boolean proceduralAudio,
			boolean javaRequested,
			boolean efxRequested,
			OpenAlEfxController.Status status
	) {
		return new Case(
				name,
				simulated,
				mode,
				proceduralAudio,
				javaRequested,
				efxRequested,
				status,
				AcousticBackendSelector.resolve(
						mode,
						proceduralAudio,
						javaRequested,
						efxRequested,
						status
				)
		);
	}

	record Result(
			boolean actualNativeEfxEligible,
			boolean actualEfxExtensionSupported,
			int actualMaximumAuxiliarySends,
			List<Case> cases
	) {
		Result {
			cases = List.copyOf(cases);
		}

		String casesJson() {
			return cases.stream()
					.map(Case::toJson)
					.reduce((left, right) -> left + ",\n    " + right)
					.orElse("");
		}
	}

	record Case(
			String name,
			boolean simulated,
			AcousticBackendSelector.Mode mode,
			boolean proceduralAudio,
			boolean javaRequested,
			boolean efxRequested,
			OpenAlEfxController.Status efxStatus,
			AcousticBackendSelector.RuntimeBackend resolvedBackend
	) {
		Case {
			Objects.requireNonNull(name, "name");
			Objects.requireNonNull(mode, "mode");
			Objects.requireNonNull(efxStatus, "efxStatus");
			Objects.requireNonNull(resolvedBackend, "resolvedBackend");
		}

		private String toJson() {
			return String.format(
					Locale.ROOT,
					"{\"name\":\"%s\",\"simulated\":%s,"
							+ "\"mode\":\"%s\","
							+ "\"procedural_audio\":%s,"
							+ "\"java_requested\":%s,"
							+ "\"efx_requested\":%s,"
							+ "\"efx_status\":\"%s\","
							+ "\"resolved_backend\":\"%s\"}",
					name,
					simulated,
					mode,
					proceduralAudio,
					javaRequested,
					efxRequested,
					efxStatus,
					resolvedBackend
			);
		}
	}
}
