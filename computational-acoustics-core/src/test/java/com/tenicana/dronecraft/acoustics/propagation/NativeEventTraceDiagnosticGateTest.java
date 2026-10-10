package com.tenicana.dronecraft.acoustics.propagation;

import org.junit.jupiter.api.Test;

import static com.tenicana.dronecraft.acoustics.propagation.NativeEventTraceDiagnosticGate.Reason.GENERAL_ACOUSTIC_COMMANDS_NOT_SUPPRESSED;
import static com.tenicana.dronecraft.acoustics.propagation.NativeEventTraceDiagnosticGate.Reason.OPENAL_NULL_BACKEND_NOT_EXCLUSIVE;
import static com.tenicana.dronecraft.acoustics.propagation.NativeEventTraceDiagnosticGate.Reason.SAFE_TO_START_RUNTIME_VERIFICATION;
import static com.tenicana.dronecraft.acoustics.propagation.NativeEventTraceDiagnosticGate.Reason.TRACE_NOT_REQUESTED;
import static com.tenicana.dronecraft.acoustics.propagation.NativeEventTraceDiagnosticGate.State.ARMED;
import static com.tenicana.dronecraft.acoustics.propagation.NativeEventTraceDiagnosticGate.State.NORMAL;
import static com.tenicana.dronecraft.acoustics.propagation.NativeEventTraceDiagnosticGate.State.REJECTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeEventTraceDiagnosticGateTest {
	@Test
	void normalLaunchDoesNotRequireDiagnosticInputs() {
		NativeEventTraceDiagnosticGate.Decision decision =
				NativeEventTraceDiagnosticGate.evaluate(
						false, false, null, true, false, false
				);
		assertEquals(NORMAL, decision.state());
		assertEquals(TRACE_NOT_REQUESTED, decision.reason());
		assertFalse(decision.armed());
	}

	@Test
	void onlyExclusiveNullBackendAndSuppressedWritersArm() {
		NativeEventTraceDiagnosticGate.Decision decision =
				NativeEventTraceDiagnosticGate.evaluate(
						true, true, "null", false, true, true
				);
		assertEquals(ARMED, decision.state());
		assertEquals(
				SAFE_TO_START_RUNTIME_VERIFICATION,
				decision.reason()
		);
		assertTrue(decision.armed());
	}

	@Test
	void driverSpellingWhitespaceOrFallbackListRejects() {
		for (String drivers : new String[] {
				"", "Null", " null", "null ", "null,", "null,wasapi",
				"wasapi,null"
		}) {
			NativeEventTraceDiagnosticGate.Decision decision =
					NativeEventTraceDiagnosticGate.evaluate(
							true, true, drivers, false, true, true
					);
			assertEquals(REJECTED, decision.state(), drivers);
			assertEquals(
					OPENAL_NULL_BACKEND_NOT_EXCLUSIVE,
					decision.reason(),
					drivers
			);
		}
	}

	@Test
	void anyUnsuppressedWriterRejects() {
		NativeEventTraceDiagnosticGate.Decision commands =
				NativeEventTraceDiagnosticGate.evaluate(
						true, true, "null", false, true, false
				);
		assertEquals(REJECTED, commands.state());
		assertEquals(
				GENERAL_ACOUSTIC_COMMANDS_NOT_SUPPRESSED,
				commands.reason()
		);
		assertEquals(
				REJECTED,
				NativeEventTraceDiagnosticGate.evaluate(
						true, true, "null", true, true, true
				).state()
		);
		assertEquals(
				REJECTED,
				NativeEventTraceDiagnosticGate.evaluate(
						true, true, "null", false, false, true
				).state()
		);
	}
}
