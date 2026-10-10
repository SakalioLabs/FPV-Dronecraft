package com.tenicana.dronecraft.client.sound;

import com.mojang.blaze3d.audio.ListenerTransform;
import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticEmissionFrame;
import com.tenicana.dronecraft.acoustics.AcousticListenerFrame;
import com.tenicana.dronecraft.acoustics.AcousticSourceProfile;
import com.tenicana.dronecraft.acoustics.AcousticSourceFrame;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer;
import com.tenicana.dronecraft.acoustics.RotorAcousticState;
import com.tenicana.dronecraft.acoustics.TonalComponent;
import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DroneAudioControlFrameTest {
	private static final FdnEnvironmentMapper.Controls ENVIRONMENT = new FdnEnvironmentMapper.Controls(
			5, new AcousticBands(0.8, 0.6, 0.4), 0.3, 0.2);

	@Test
	void wetControlsUseFrozenGeometryAndPreserveLegacyDistanceEnvelopeCalibrationOrder() {
		AcousticListenerFrame listener = listener(0, 0, 0);
		Object motorStream = new Object(), propellerStream = new Object();
		var first = source(11, listener, new AcousticVector(3, 4, 0), .7, .25,
				new AcousticSourceProfile.Calibration(true, true, 6, -6), motorStream, propellerStream);
		var aboveOne = source(12, listener, new AcousticVector(0, 0, 2), 1.8, 2.0,
				new AcousticSourceProfile.Calibration(true, true, 0, 0), new Object(), new Object());
		var sourceList = new ArrayList<>(List.of(first, aboveOne));
		var frame = frame(listener, sourceList);
		sourceList.clear();

		assertEquals(2, frame.sources().size());
		assertThrows(UnsupportedOperationException.class, () -> frame.sources().clear());
		var wet = frame.wetSnapshot().sources();
		assertEquals(List.of(11, 12), wet.stream().map(ListenerReverbState.Source::entityId).toList());
		assertEquals(.2 * .7 * Math.pow(10, 6 / 20.0), wet.get(0).motorAmplitude(), 1e-15);
		assertEquals(.2 * .25 * Math.pow(10, -6 / 20.0), wet.get(0).propellerAmplitude(), 1e-15);
		assertEquals(.5, wet.get(1).motorAmplitude(), 1e-15,
				"effective envelope clamps to one before calibration");
		assertEquals(.5, wet.get(1).propellerAmplitude(), 1e-15,
				"effective envelope clamps to one before calibration");
		assertSame(listener, first.audioSnapshot().listener());
	}

	@Test
	void endpointLookupUsesStateAndStreamIdentity() {
		AcousticListenerFrame listener = listener(0, 0, 0);
		Object motorStream = new Object(), propellerStream = new Object(), wetStream = new Object();
		var control = source(21, listener, new AcousticVector(1, 0, 0), .5, .6,
				AcousticSourceProfile.Calibration.researchFallback(), motorStream, propellerStream);
		var wetState = new ListenerReverbState();
		var frame = new DroneAudioControlFrame(1, 2, ListenerTransform.INITIAL, listener,
				List.of(control), wetState, ENVIRONMENT, null, null, wetStream, .4f, 1.0f);
		assertSame(control.audioSnapshot(), frame.drySnapshot(control.state(), PhaseContinuousSynthesizer.Layer.MOTOR, motorStream));
		assertSame(control.audioSnapshot(), frame.drySnapshot(control.state(), PhaseContinuousSynthesizer.Layer.PROPELLER, propellerStream));
		assertNull(frame.drySnapshot(control.state(), PhaseContinuousSynthesizer.Layer.MOTOR, propellerStream));
		assertNull(frame.drySnapshot(new DroneAcousticRenderState(), PhaseContinuousSynthesizer.Layer.MOTOR, motorStream));
		assertSame(frame.wetSnapshot(), frame.wetSnapshotFor(wetState, wetStream));
		assertNull(frame.wetSnapshotFor(new ListenerReverbState(), wetStream));
		assertNull(frame.wetSnapshotFor(wetState, new Object()));
	}

	@Test
	void rejectsDuplicateEntityOrStateAndMoreThanSixSources() {
		AcousticListenerFrame listener = listener(0, 0, 0);
		var duplicateIdA = source(30, listener, AcousticVector.ZERO, .2, .3,
				AcousticSourceProfile.Calibration.researchFallback(), new Object(), new Object());
		var duplicateIdB = source(30, listener, new AcousticVector(1, 0, 0), .2, .3,
				AcousticSourceProfile.Calibration.researchFallback(), new Object(), new Object());
		assertThrows(IllegalArgumentException.class, () -> frame(listener, List.of(duplicateIdA, duplicateIdB)));

		var duplicateState = source(31, listener, AcousticVector.ZERO, .2, .3,
				AcousticSourceProfile.Calibration.researchFallback(), new Object(), new Object());
		var sameStateDifferentId = new DroneAudioControlFrame.SourceControl(32, duplicateState.state(),
				duplicateState.audioSnapshot(), duplicateState.unoccludedEmission(), null, null,
				null, null, new Object(), new Object(), .2, .3, .5f, .5f, 1, 1);
		assertThrows(IllegalArgumentException.class,
				() -> frame(listener, List.of(duplicateState, sameStateDifferentId)));

		var seven = new ArrayList<DroneAudioControlFrame.SourceControl>();
		for (int id = 40; id < 47; id++) seven.add(source(id, listener, new AcousticVector(id, 0, 0), .2, .3,
				AcousticSourceProfile.Calibration.researchFallback(), new Object(), new Object()));
		assertThrows(IllegalArgumentException.class, () -> frame(listener, seven));
	}

	private static DroneAudioControlFrame frame(AcousticListenerFrame listener,
			List<DroneAudioControlFrame.SourceControl> sources) {
		return new DroneAudioControlFrame(1, 1, ListenerTransform.INITIAL, listener, sources,
				new ListenerReverbState(), ENVIRONMENT, null, null, null, 1, 1);
	}

	private static AcousticListenerFrame listener(double x, double y, double z) {
		return new AcousticListenerFrame(new AcousticVector(x, y, z), AcousticVector.ZERO);
	}

	private static DroneAudioControlFrame.SourceControl source(int id, AcousticListenerFrame listener,
			AcousticVector position, double motorEnvelope, double propellerEnvelope,
			AcousticSourceProfile.Calibration calibration, Object motorStream, Object propellerStream) {
		AcousticEmissionFrame emission = new AcousticEmissionFrame(List.of(
				new TonalComponent(TonalComponent.Kind.ELECTRICAL, 0, 1, 700, .08, 0),
				new TonalComponent(TonalComponent.Kind.BLADE_PASS, 0, 1, 1_000, .1, .2)),
				new AcousticBands(.00001, .00002, .00001));
		AcousticSourceFrame source = new AcousticSourceFrame(id, 1, position, AcousticVector.ZERO,
				new AcousticVector(0, 1, 0), .127,
				List.of(new RotorAcousticState(12_000, .5, .5, .0635, 3, 7, 1, 0)));
		var snapshot = new DroneAcousticRenderState.AudioSnapshot(source, listener, emission, emission, 1, 343, calibration);
		return new DroneAudioControlFrame.SourceControl(id, new DroneAcousticRenderState(), snapshot, emission,
				null, null, null, null, motorStream, propellerStream, motorEnvelope, propellerEnvelope,
				.5f, .6f, 1.0f, 1.0f);
	}
}
