package com.tenicana.dronecraft.client.sound;

import com.mojang.blaze3d.audio.ListenerTransform;
import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticEmissionFrame;
import com.tenicana.dronecraft.acoustics.AcousticListenerFrame;
import com.tenicana.dronecraft.acoustics.AcousticSourceFrame;
import com.tenicana.dronecraft.acoustics.AcousticSourceProfile;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.RotorAcousticState;
import com.tenicana.dronecraft.acoustics.TonalComponent;
import com.tenicana.dronecraft.acoustics.reverb.FdnEnvironmentMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroneCallbackControlCoordinatorTest {
	@Test
	void blockedOwnerCoalescesStagedFramesAndCommitsOnlyNewestWhenRunnableRuns() {
		List<Runnable> queue = new ArrayList<>();
		List<DroneAudioControlFrame> commits = new ArrayList<>();
		var coordinator = new DroneCallbackControlCoordinator((frame, engine) -> {
			commits.add(frame);
			return true;
		});
		var initial = frame(0, 1, 10);
		coordinator.stageForTest(initial, queue::add);
		queue.remove(0).run();
		assertSame(initial, coordinator.committedFrame());

		var v1 = frame(0, 2, 20);
		var v2 = frame(0, 3, 30);
		var v3 = frame(0, 4, 40);
		coordinator.stageForTest(v1, queue::add);
		coordinator.stageForTest(v2, queue::add);
		coordinator.stageForTest(v3, queue::add);
		assertEquals(1, queue.size(), "a blocked owner has one scheduled drain");
		assertSame(initial, coordinator.committedFrame(), "staging must not publish ahead of the owner");
		assertSame(v3, coordinator.pendingFrame());

		queue.remove(0).run();
		assertEquals(List.of(initial, v3), commits);
		assertSame(v3, coordinator.committedFrame());
		assertSame(v3, coordinator.pendingFrame(), "last client publication remains observable after drain");
	}

	@Test
	void stagingDuringCommitQueuesOneFollowingTurnAndPublishesLatestPendingFrame() {
		List<Runnable> queue = new ArrayList<>();
		List<DroneAudioControlFrame> commits = new ArrayList<>();
		DroneCallbackControlCoordinator[] reference = new DroneCallbackControlCoordinator[1];
		var coordinator = new DroneCallbackControlCoordinator((frame, engine) -> {
			commits.add(frame);
			if (frame.version() == 2) {
				reference[0].stageForTest(frame(0, 3, 30), queue::add);
				reference[0].stageForTest(frame(0, 4, 40), queue::add);
			}
			return true;
		});
		reference[0] = coordinator;
		var first = frame(0, 1, 10);
		var second = frame(0, 2, 20);
		coordinator.stageForTest(first, queue::add);
		queue.remove(0).run();
		coordinator.stageForTest(second, queue::add);
		assertEquals(1, queue.size());

		queue.remove(0).run();
		assertSame(second, coordinator.committedFrame());
		assertEquals(1, queue.size(), "staging inside native commit is deferred to one next turn");
		queue.remove(0).run();
		assertEquals(List.of(first, second, coordinator.committedFrame()), commits);
		assertEquals(4, coordinator.committedFrame().version());
		assertEquals(3, commits.size());
	}

	@Test
	void staleScheduledTurnCannotClearOrOverwriteNewGenerationTask() {
		List<Runnable> queue = new ArrayList<>();
		List<DroneAudioControlFrame> commits = new ArrayList<>();
		var coordinator = new DroneCallbackControlCoordinator((frame, engine) -> {
			commits.add(frame);
			return true;
		});
		var stale = frame(0, 1, 10);
		coordinator.stageForTest(stale, queue::add);
		Runnable oldTurn = queue.remove(0);

		coordinator.invalidate();
		assertNull(coordinator.pendingFrame(), "camera/world invalidation clears the last prepared publication");
		var current = frame(coordinator.generation(), 2, 20);
		coordinator.stageForTest(current, queue::add);
		assertEquals(1, queue.size());
		Runnable newTurn = queue.remove(0);

		oldTurn.run();
		assertTrue(commits.isEmpty());
		assertSame(current, coordinator.pendingFrame(), "stale completion must preserve the newer pending frame");
		newTurn.run();
		assertEquals(List.of(current), commits);
		assertSame(current, coordinator.committedFrame());
	}

	@Test
	void rejectedNativeCommitKeepsLastCommittedFrame() {
		List<Runnable> queue = new ArrayList<>();
		var accept = new AtomicBoolean(true);
		var coordinator = new DroneCallbackControlCoordinator((frame, engine) -> accept.get());
		var committed = frame(0, 1, 10);
		coordinator.stageForTest(committed, queue::add);
		queue.remove(0).run();
		assertSame(committed, coordinator.committedFrame());

		accept.set(false);
		var rejected = frame(0, 2, 20);
		coordinator.stageForTest(rejected, queue::add);
		queue.remove(0).run();
		assertSame(committed, coordinator.committedFrame());
	}

	@Test
	void generationInvalidatedInsideSuccessfulCommitRejectsStalePublication() {
		List<Runnable> queue = new ArrayList<>();
		DroneCallbackControlCoordinator[] reference = new DroneCallbackControlCoordinator[1];
		var coordinator = new DroneCallbackControlCoordinator((frame, engine) -> {
			reference[0].invalidate();
			return true;
		});
		reference[0] = coordinator;
		var staged = frame(0, 1, 10);
		coordinator.stageForTest(staged, queue::add);
		queue.remove(0).run();
		assertEquals(1, coordinator.generation());
		assertNull(coordinator.committedFrame());
	}

	@Test
	void lastNativeCloseReleasesGlobalFrameAfterInvalidationWhileOtherOwnersRetainIt() {
		List<Runnable> queue = new ArrayList<>();
		var coordinator = new DroneCallbackControlCoordinator((frame, engine) -> true);
		var committed = frame(0, 1, 10);
		coordinator.stageForTest(committed, queue::add);
		queue.remove(0).run();
		coordinator.channelEnrolled();
		coordinator.channelEnrolled();
		coordinator.invalidate();
		assertSame(committed, coordinator.committedFrame());
		coordinator.channelClosing();
		assertSame(committed, coordinator.committedFrame(), "remaining native owner still uses its complete frame");
		coordinator.channelClosing();
		assertNull(coordinator.committedFrame(), "final native close releases old SoundInstances/world references");
		assertNull(coordinator.pendingFrame());
	}

	@Test
	void clientStagingKeepsCommittedFrameReferenceAndItsControlsStable() {
		List<Runnable> queue = new ArrayList<>();
		var coordinator = new DroneCallbackControlCoordinator((frame, engine) -> true);
		var oldFrame = frame(0, 1, 10);
		coordinator.stageForTest(oldFrame, queue::add);
		queue.remove(0).run();
		var oldSource = oldFrame.sources().get(0);
		var stagedNew = frame(0, 2, 99);
		coordinator.stageForTest(stagedNew, queue::add);

		assertSame(oldFrame, coordinator.committedFrame());
		assertSame(oldSource, coordinator.committedFrame().sources().get(0));
		assertEquals(10, coordinator.committedFrame().sources().get(0).entityId());
		assertSame(stagedNew, coordinator.pendingFrame());
	}

	private static DroneAudioControlFrame frame(long generation, long version, int entityId) {
		AcousticListenerFrame listener = new AcousticListenerFrame(AcousticVector.ZERO, AcousticVector.ZERO);
		AcousticEmissionFrame emission = new AcousticEmissionFrame(List.of(
				new TonalComponent(TonalComponent.Kind.ELECTRICAL, 0, 1, 700, .08, 0),
				new TonalComponent(TonalComponent.Kind.BLADE_PASS, 0, 1, 1_000, .1, .2)),
				new AcousticBands(.00001, .00002, .00001));
		var sourceFrame = new AcousticSourceFrame(entityId, version, new AcousticVector(entityId, 0, 0),
				AcousticVector.ZERO, new AcousticVector(0, 1, 0), .127,
				List.of(new RotorAcousticState(12_000, .5, .5, .0635, 3, 7, 1, 0)));
		var snapshot = new DroneAcousticRenderState.AudioSnapshot(sourceFrame, listener, emission, emission, 1, 343,
				AcousticSourceProfile.Calibration.researchFallback());
		var source = new DroneAudioControlFrame.SourceControl(entityId, new DroneAcousticRenderState(), snapshot,
				emission, null, null, null, null, null, null, .5, .6, .4f, .5f, 1, 1);
		return new DroneAudioControlFrame(generation, version, ListenerTransform.INITIAL, listener, List.of(source),
				new ListenerReverbState(), new FdnEnvironmentMapper.Controls(version,
						new AcousticBands(.8, .6, .4), .3, .2), null, null, null, .4f, 1.0f);
	}
}
