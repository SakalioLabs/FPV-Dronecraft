package com.tenicana.dronecraft.client.sound;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AcousticBackendSelectorTest {
	@AfterEach
	void restore() {
		AcousticBackendSelector.restoreDefault();
		System.clearProperty("fpvdrone.proceduralAudio");
		System.clearProperty("fpvdrone.listenerReverb");
		System.clearProperty(OpenAlEfxController.ENABLED_PROPERTY);
	}

	@Test
	void defaultModeUsesExistingOptInProperties() {
		assertFalse(AcousticBackendSelector.javaFdnEnabled());
		assertFalse(AcousticBackendSelector.openAlEfxEnabled());
		System.setProperty("fpvdrone.listenerReverb", "true");
		System.setProperty(OpenAlEfxController.ENABLED_PROPERTY, "true");
		assertTrue(AcousticBackendSelector.javaFdnEnabled());
		assertTrue(AcousticBackendSelector.openAlEfxEnabled());
	}

	@Test
	void dryOverridesBothPropertiesAndRestoresThem() {
		System.setProperty("fpvdrone.listenerReverb", "true");
		System.setProperty(OpenAlEfxController.ENABLED_PROPERTY, "true");
		assertTrue(AcousticBackendSelector.activate(
				AcousticBackendSelector.Mode.DRY
		));
		assertFalse(AcousticBackendSelector.javaFdnEnabled());
		assertFalse(AcousticBackendSelector.openAlEfxEnabled());
		assertFalse(AcousticBackendSelector.activate(
				AcousticBackendSelector.Mode.JAVA_FDN
		));
		AcousticBackendSelector.restoreDefault();
		assertTrue(AcousticBackendSelector.javaFdnEnabled());
		assertTrue(AcousticBackendSelector.openAlEfxEnabled());
	}

	@Test
	void explicitBackendsAreMutuallyExclusive() {
		assertTrue(AcousticBackendSelector.activate(
				AcousticBackendSelector.Mode.JAVA_FDN
		));
		assertTrue(AcousticBackendSelector.javaFdnEnabled());
		assertFalse(AcousticBackendSelector.openAlEfxEnabled());
		AcousticBackendSelector.restoreDefault();
		assertTrue(AcousticBackendSelector.activate(
				AcousticBackendSelector.Mode.OPENAL_EFX
		));
		assertFalse(AcousticBackendSelector.javaFdnEnabled());
		assertTrue(AcousticBackendSelector.openAlEfxEnabled());
	}

	@Test
	void defaultCannotBeUsedAsLabOverride() {
		assertThrows(
				IllegalArgumentException.class,
				() -> AcousticBackendSelector.activate(
						AcousticBackendSelector.Mode.DEFAULT
				)
		);
	}

	@Test
	void defaultPrefersEfxAndFallsBackOnlyAfterTerminalFailure() {
		System.setProperty("fpvdrone.listenerReverb", "true");
		System.setProperty(OpenAlEfxController.ENABLED_PROPERTY, "true");
		assertRuntime(
				AcousticBackendSelector.RuntimeBackend.OPENAL_EFX_PENDING,
				OpenAlEfxController.Status.INACTIVE
		);
		assertRuntime(
				AcousticBackendSelector.RuntimeBackend.OPENAL_EFX_PENDING,
				OpenAlEfxController.Status.WAITING_CONTEXT
		);
		assertRuntime(
				AcousticBackendSelector.RuntimeBackend.OPENAL_EFX_PENDING,
				OpenAlEfxController.Status.WAITING_SOURCES
		);
		assertRuntime(
				AcousticBackendSelector.RuntimeBackend.OPENAL_EFX,
				OpenAlEfxController.Status.OPERATIONAL
		);
		assertRuntime(
				AcousticBackendSelector.RuntimeBackend.JAVA_FDN,
				OpenAlEfxController.Status.EXTENSION_UNAVAILABLE
		);
		assertRuntime(
				AcousticBackendSelector.RuntimeBackend.JAVA_FDN,
				OpenAlEfxController.Status.CONTEXT_FAILED
		);
	}

	@Test
	void explicitEfxLabNeverSilentlyChangesBackend() {
		System.setProperty("fpvdrone.listenerReverb", "true");
		assertTrue(AcousticBackendSelector.activate(
				AcousticBackendSelector.Mode.OPENAL_EFX
		));
		assertRuntime(
				AcousticBackendSelector.RuntimeBackend.OPENAL_EFX_PENDING,
				OpenAlEfxController.Status.CONTEXT_FAILED
		);
	}

	@Test
	void fallbackRequiresJavaOptInAndProceduralAudio() {
		System.setProperty(OpenAlEfxController.ENABLED_PROPERTY, "true");
		assertRuntime(
				AcousticBackendSelector.RuntimeBackend.CLEAN,
				OpenAlEfxController.Status.EXTENSION_UNAVAILABLE
		);
		System.setProperty("fpvdrone.listenerReverb", "true");
		System.setProperty("fpvdrone.proceduralAudio", "false");
		assertRuntime(
				AcousticBackendSelector.RuntimeBackend.CLEAN,
				OpenAlEfxController.Status.OPERATIONAL
		);
	}

	@Test
	void contextFailureRecoverySequenceNeverSelectsTwoWetPaths() {
		System.setProperty("fpvdrone.listenerReverb", "true");
		System.setProperty(OpenAlEfxController.ENABLED_PROPERTY, "true");
		OpenAlEfxController.Status[] injected = {
				OpenAlEfxController.Status.OPERATIONAL,
				OpenAlEfxController.Status.CONTEXT_FAILED,
				// Disabled EFX observes the replacement context first.
				OpenAlEfxController.Status.INACTIVE,
				OpenAlEfxController.Status.WAITING_CONTEXT,
				OpenAlEfxController.Status.WAITING_SOURCES,
				OpenAlEfxController.Status.OPERATIONAL
		};
		AcousticBackendSelector.RuntimeBackend[] expected = {
				AcousticBackendSelector.RuntimeBackend.OPENAL_EFX,
				AcousticBackendSelector.RuntimeBackend.JAVA_FDN,
				AcousticBackendSelector.RuntimeBackend.OPENAL_EFX_PENDING,
				AcousticBackendSelector.RuntimeBackend.OPENAL_EFX_PENDING,
				AcousticBackendSelector.RuntimeBackend.OPENAL_EFX_PENDING,
				AcousticBackendSelector.RuntimeBackend.OPENAL_EFX
		};
		for (int index = 0; index < injected.length; index++) {
			AcousticBackendSelector.RuntimeBackend actual =
					AcousticBackendSelector.runtimeBackend(injected[index]);
			org.junit.jupiter.api.Assertions.assertEquals(
					expected[index],
					actual
			);
			boolean javaWet = actual
					== AcousticBackendSelector.RuntimeBackend.JAVA_FDN;
			boolean efxWet = injected[index]
					== OpenAlEfxController.Status.OPERATIONAL
					&& actual == AcousticBackendSelector
							.RuntimeBackend.OPENAL_EFX;
			assertFalse(javaWet && efxWet);
		}
	}

	private static void assertRuntime(
			AcousticBackendSelector.RuntimeBackend expected,
			OpenAlEfxController.Status status
	) {
		org.junit.jupiter.api.Assertions.assertEquals(
				expected,
				AcousticBackendSelector.runtimeBackend(status)
		);
	}
}
