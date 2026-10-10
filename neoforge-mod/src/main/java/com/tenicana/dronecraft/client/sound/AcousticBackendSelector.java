package com.tenicana.dronecraft.client.sound;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runtime backend selection for a bounded audio-lab session.
 *
 * <p>Normal gameplay always uses {@link Mode#DEFAULT}; the three explicit
 * modes exist only while an operator-started diagnostic session is active.
 */
final class AcousticBackendSelector {
	enum Mode {
		DEFAULT("default"),
		DRY("dry"),
		JAVA_FDN("java-fdn"),
		OPENAL_EFX("openal-efx");

		private final String token;

		Mode(String token) {
			this.token = token;
		}

		String token() {
			return token;
		}
	}

	enum RuntimeBackend {
		CLEAN,
		OPENAL_EFX_PENDING,
		OPENAL_EFX,
		JAVA_FDN
	}

	private static final AtomicReference<Mode> MODE =
			new AtomicReference<>(Mode.DEFAULT);

	private AcousticBackendSelector() {
	}

	static Mode mode() {
		return MODE.get();
	}

	static boolean activate(Mode mode) {
		Objects.requireNonNull(mode, "mode");
		if (mode == Mode.DEFAULT) {
			throw new IllegalArgumentException(
					"an audio-lab session requires an explicit backend"
			);
		}
		return MODE.compareAndSet(Mode.DEFAULT, mode);
	}

	static void restoreDefault() {
		MODE.set(Mode.DEFAULT);
	}

	static boolean javaFdnEnabled() {
		Mode selected = mode();
		if (selected != Mode.DEFAULT) {
			return selected == Mode.JAVA_FDN;
		}
		return Boolean.parseBoolean(
				System.getProperty("fpvdrone.listenerReverb", "false")
		);
	}

	static boolean openAlEfxEnabled() {
		Mode selected = mode();
		if (selected != Mode.DEFAULT) {
			return selected == Mode.OPENAL_EFX;
		}
		return Boolean.parseBoolean(
				System.getProperty(
						OpenAlEfxController.ENABLED_PROPERTY,
						"false"
				)
		);
	}

	/**
	 * Resolves one mutually exclusive runtime owner for late reverberation.
	 *
	 * <p>An explicit audio-lab selection never falls through to another
	 * backend. During normal gameplay EFX is preferred when requested, while
	 * Java FDN is allowed to take over only after EFX has reported a terminal
	 * failure for the current OpenAL context. Pending EFX states stay dry so
	 * startup cannot briefly run both wet paths.
	 */
	static RuntimeBackend runtimeBackend(
			OpenAlEfxController.Status efxStatus
	) {
		Objects.requireNonNull(efxStatus, "efxStatus");
		return resolve(
				mode(),
				Boolean.parseBoolean(System.getProperty(
						"fpvdrone.proceduralAudio",
						"true"
				)),
				javaFdnEnabled(),
				openAlEfxEnabled(),
				efxStatus
		);
	}

	static RuntimeBackend resolve(
			Mode selected,
			boolean proceduralAudio,
			boolean javaRequested,
			boolean efxRequested,
			OpenAlEfxController.Status efxStatus
	) {
		Objects.requireNonNull(selected, "selected");
		Objects.requireNonNull(efxStatus, "efxStatus");
		if (!proceduralAudio) {
			return RuntimeBackend.CLEAN;
		}
		if (selected == Mode.DRY) {
			return RuntimeBackend.CLEAN;
		}
		if (selected == Mode.JAVA_FDN) {
			return RuntimeBackend.JAVA_FDN;
		}
		if (selected == Mode.OPENAL_EFX) {
			return efxRoute(efxStatus);
		}

		if (!efxRequested) {
			return javaRequested
					? RuntimeBackend.JAVA_FDN
					: RuntimeBackend.CLEAN;
		}
		if (efxStatus.terminalFailure()) {
			return javaRequested
					? RuntimeBackend.JAVA_FDN
					: RuntimeBackend.CLEAN;
		}
		return efxRoute(efxStatus);
	}

	private static RuntimeBackend efxRoute(
			OpenAlEfxController.Status status
	) {
		return status == OpenAlEfxController.Status.OPERATIONAL
				? RuntimeBackend.OPENAL_EFX
				: RuntimeBackend.OPENAL_EFX_PENDING;
	}
}
