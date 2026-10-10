package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticEmissionFrame;
import com.tenicana.dronecraft.acoustics.AcousticSourceFrame;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.RotorAcousticState;
import com.tenicana.dronecraft.acoustics.TonalComponent;
import com.tenicana.dronecraft.acoustics.propagation.DopplerShift;
import com.tenicana.dronecraft.acoustics.voxel.VoxelDda;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcousticCameraListenerTest {
	private static final int FIRST_PERSON = 0;
	private static final int THIRD_PERSON = 1;
	private static final int SPECTATOR = 2;

	@Test
	void offsetCameraCanCrossWallEvenWhenEntityEyeRayIsClear() {
		AcousticVector source = new AcousticVector(0.5, 0.5, 0.5);
		AcousticVector entityEye = new AcousticVector(0.5, 1.6, 0.5);
		AcousticVector actualCamera = new AcousticVector(1.7, 2.3, 0.5);
		AcousticCameraListener.Sample sample = new AcousticCameraListener().capture(
				view(new Object(), new Object(), new Object(), 4),
				actualCamera, AcousticVector.ZERO
		);
		VoxelDda.VoxelQuery wall = (x, y, z) -> x == 1;

		assertFalse(VoxelDda.trace(source, entityEye, wall, 16).blocked());
		assertTrue(VoxelDda.trace(source, sample.frame().positionMeters(), wall, 16).blocked());
		assertTrue(sample.frame().positionMeters().subtract(source).length()
				> entityEye.subtract(source).length());
	}

	@Test
	void captureUsesCameraOriginAndOwnerVelocityWithoutDifferencingCameraMotion() {
		AcousticCameraListener listener = new AcousticCameraListener();
		Object world = new Object();
		Object owner = new Object();
		Object camera = new Object();
		AcousticVector ownerVelocity = new AcousticVector(3.0, 0.0, 0.0);
		AcousticCameraListener.View view = view(world, camera, owner, FIRST_PERSON);

		AcousticCameraListener.Sample first = listener.capture(
				view, new AcousticVector(0.0, 1.6, 0.0), ownerVelocity);
		AcousticCameraListener.Sample offsetCamera = listener.capture(
				view, new AcousticVector(0.0, 2.3, 1.2), ownerVelocity);

		assertEquals(new AcousticVector(0.0, 2.3, 1.2), offsetCamera.frame().positionMeters());
		assertEquals(ownerVelocity, offsetCamera.frame().velocityMetersPerSecond());
		assertEquals(first.epoch(), offsetCamera.epoch());
	}

	@Test
	void coMovingFpvListenerAndDroneHaveNeutralDopplerRatio() {
		AcousticVector sharedVelocity = new AcousticVector(14.0, 0.0, 0.0);
		AcousticCameraListener.Sample sample = new AcousticCameraListener().capture(
				view(new Object(), new Object(), new Object(), FIRST_PERSON),
				new AcousticVector(0.0, 1.0, 0.0), sharedVelocity);
		AcousticSourceFrame source = source(new AcousticVector(8.0, 1.0, 0.0), sharedVelocity);

		DopplerShift.Result shifted = DopplerShift.apply(
				emission(), source, sample.frame(), 343.0);

		assertEquals(1.0, shifted.frequencyRatio(), 1.0e-12);
	}

	@Test
	void cameraVibrationDoesNotBecomeVelocityOrDopplerSpike() {
		AcousticCameraListener listener = new AcousticCameraListener();
		Object world = new Object();
		Object owner = new Object();
		Object camera = new Object();
		AcousticCameraListener.View view = view(world, camera, owner, FIRST_PERSON);
		AcousticVector trueVelocity = new AcousticVector(2.0, 0.0, 0.0);
		AcousticCameraListener.Sample left = listener.capture(
				view, new AcousticVector(-0.08, 1.0, 0.0), trueVelocity);
		AcousticCameraListener.Sample right = listener.capture(
				view, new AcousticVector(0.08, 1.0, 0.0), trueVelocity);
		AcousticSourceFrame source = source(new AcousticVector(10.0, 1.0, 0.0), trueVelocity);

		assertEquals(trueVelocity, left.frame().velocityMetersPerSecond());
		assertEquals(trueVelocity, right.frame().velocityMetersPerSecond());
		assertEquals(1.0, DopplerShift.apply(emission(), source, left.frame(), 343.0)
				.frequencyRatio(), 1.0e-12);
		assertEquals(1.0, DopplerShift.apply(emission(), source, right.frame(), 343.0)
				.frequencyRatio(), 1.0e-12);
	}

	@Test
	void cameraOwnerAndViewModeChangesStartFreshEpochs() {
		AcousticCameraListener listener = new AcousticCameraListener();
		Object world = new Object();
		Object owner = new Object();
		Object fpvCamera = new Object();
		AcousticVector position = AcousticVector.ZERO;
		AcousticVector velocity = AcousticVector.ZERO;
		long epoch = listener.capture(view(world, fpvCamera, owner, FIRST_PERSON), position, velocity).epoch();

		for (AcousticCameraListener.View changed : List.of(
				view(world, fpvCamera, owner, THIRD_PERSON),
				view(world, fpvCamera, owner, SPECTATOR),
				view(world, new Object(), owner, FIRST_PERSON),
				view(world, fpvCamera, new Object(), FIRST_PERSON),
				view(new Object(), fpvCamera, owner, FIRST_PERSON))) {
			AcousticCameraListener.Sample next = listener.capture(changed, position, velocity);
			assertTrue(next.epoch() > epoch, "view/world/entity identity change must invalidate old frame");
			assertFalse(listener.isCurrent(epoch));
			epoch = next.epoch();
		}
	}

	@Test
	void largeCameraJumpAndResetInvalidateFramesWithoutReusingEpoch() {
		AcousticCameraListener listener = new AcousticCameraListener();
		Object world = new Object();
		Object owner = new Object();
		AcousticCameraListener.View view = view(world, new Object(), owner, FIRST_PERSON);
		AcousticCameraListener.Sample beforeJump = listener.capture(view, AcousticVector.ZERO, AcousticVector.ZERO);
		AcousticCameraListener.Sample afterJump = listener.capture(
				view, new AcousticVector(16.01, 0.0, 0.0), AcousticVector.ZERO);

		assertTrue(afterJump.epoch() > beforeJump.epoch());
		assertFalse(listener.isCurrent(beforeJump.epoch()));
		listener.reset();
		assertNull(listener.latestFor(world));
		AcousticCameraListener.Sample afterReset = listener.capture(view, AcousticVector.ZERO, AcousticVector.ZERO);
		assertTrue(afterReset.epoch() > afterJump.epoch(), "reset must not recycle an epoch");
		assertFalse(listener.isCurrent(afterJump.epoch()));
	}

	@Test
	void latestForRequiresWorldIdentity() {
		AcousticCameraListener listener = new AcousticCameraListener();
		Object world = new Object();
		AcousticCameraListener.Sample sample = listener.capture(
				view(world, new Object(), new Object(), FIRST_PERSON),
				AcousticVector.ZERO, AcousticVector.ZERO);

		assertSame(sample, listener.latestFor(world));
		assertNull(listener.latestFor(new Object()));
	}

	@Test
	void subThresholdMovementKeepsEpoch() {
		AcousticCameraListener listener = new AcousticCameraListener();
		AcousticCameraListener.View view = view(new Object(), new Object(), new Object(), FIRST_PERSON);
		AcousticCameraListener.Sample first = listener.capture(view, AcousticVector.ZERO, AcousticVector.ZERO);
		AcousticCameraListener.Sample moved = listener.capture(
				view, new AcousticVector(15.99, 0.0, 0.0), AcousticVector.ZERO);

		assertEquals(first.epoch(), moved.epoch());
		assertTrue(listener.isCurrent(first.epoch()));
	}

	@Test
	void aCapturedFrameRemainsAnImmutableSnapshot() {
		AcousticCameraListener listener = new AcousticCameraListener();
		Object world = new Object();
		AcousticCameraListener.Sample old = listener.capture(
				view(world, new Object(), new Object(), FIRST_PERSON),
				new AcousticVector(1.0, 2.0, 3.0), new AcousticVector(4.0, 5.0, 6.0));
		AcousticCameraListener.Sample newer = listener.capture(
				view(world, new Object(), new Object(), FIRST_PERSON),
				new AcousticVector(7.0, 8.0, 9.0), new AcousticVector(1.0, 0.0, 0.0));

		assertNotSame(old, newer);
		assertEquals(new AcousticVector(1.0, 2.0, 3.0), old.frame().positionMeters());
		assertEquals(new AcousticVector(4.0, 5.0, 6.0), old.frame().velocityMetersPerSecond());
		assertEquals(new AcousticVector(7.0, 8.0, 9.0), newer.frame().positionMeters());
	}

	private static AcousticCameraListener.View view(
			Object world,
			Object cameraEntity,
			Object velocityOwner,
			int mode
	) {
		return new AcousticCameraListener.View(world, cameraEntity, velocityOwner, mode);
	}

	private static AcousticSourceFrame source(AcousticVector position, AcousticVector velocity) {
		return new AcousticSourceFrame(
				7L,
				0L,
				position,
				velocity,
				new AcousticVector(0.0, 1.0, 0.0),
				0.1,
				List.of(new RotorAcousticState(0.0, 0.0, 0.0, 0.1, 2, 1, 1, 0.0))
		);
	}

	private static AcousticEmissionFrame emission() {
		return new AcousticEmissionFrame(
				List.of(new TonalComponent(TonalComponent.Kind.SHAFT, 0, 1, 800.0, 1.0, 0.0)),
				AcousticBands.SILENT
		);
	}
}
