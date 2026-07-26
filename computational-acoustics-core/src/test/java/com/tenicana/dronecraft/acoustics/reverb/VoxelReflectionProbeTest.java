package com.tenicana.dronecraft.acoustics.reverb;

import com.tenicana.dronecraft.acoustics.AcousticMaterial;
import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.propagation.DirectPathSolver;
import com.tenicana.dronecraft.acoustics.propagation.SparseMaterialSnapshot;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoxelReflectionProbeTest {
	private static final AcousticVector LISTENER =
			new AcousticVector(0.5, 0.5, 0.5);
	private static final VoxelReflectionProbe.Config CONFIG =
			new VoxelReflectionProbe.Config(128, 12, 64.0, 256, 1.0e-9);

	@Test
	void closedRoomIsDeterministicAndDoesNotEscape() {
		ReflectionVolume volume = closedRoom(AcousticMaterials.STONE, 7L);

		ReflectionStatistics first = VoxelReflectionProbe.analyze(
				LISTENER,
				volume,
				CONFIG
		);
		ReflectionStatistics second = VoxelReflectionProbe.analyze(
				LISTENER,
				volume,
				CONFIG
		);

		assertEquals(first, second);
		assertEquals(0, first.escapedRays());
		assertEquals(128 * 12, first.surfaceHits());
		assertEquals(128, first.maximumBounceTerminatedRays());
		assertEquals(0, first.truncatedLegs());
		assertTrue(first.meanFreePathMeters() > 3.0);
		assertTrue(first.meanFreePathMeters() < 10.0);
		assertEquals(
				AcousticMaterials.STONE.scattering(),
				first.meanScattering(),
				1.0e-12
		);
	}

	@Test
	void diagnosticObserverSeesEveryRecordedSurfaceHit() {
		ReflectionVolume volume = closedRoom(AcousticMaterials.STONE, 9L);
		AtomicInteger observed = new AtomicInteger();
		ReflectionStatistics statistics = VoxelReflectionProbe.analyze(
				LISTENER,
				volume,
				new VoxelReflectionProbe.Config(
						32,
						4,
						64.0,
						128,
						1.0e-9
				),
				(rayIndex, bounce, material, normal, distanceMeters) -> {
					assertEquals(AcousticMaterials.STONE, material);
					assertTrue(normal.length() > 0.99);
					assertTrue(distanceMeters > 0.0);
					observed.incrementAndGet();
				}
		);
		assertEquals(statistics.surfaceHits(), observed.get());
	}

	@Test
	void researchDirectionPolicyCanDiffuseOnlyLateBounces() {
		ReflectionVolume volume = closedRoom(AcousticMaterials.STONE, 10L);
		AtomicInteger earlyHits = new AtomicInteger();
		AtomicInteger lateHits = new AtomicInteger();
		AtomicInteger policyCalls = new AtomicInteger();
		ReflectionStatistics statistics = VoxelReflectionProbe.analyze(
				LISTENER,
				volume,
				new VoxelReflectionProbe.Config(
						32,
						4,
						64.0,
						128,
						1.0e-9
				),
				(rayIndex, bounce, material, normal, distanceMeters) -> {
					if (bounce < 2) {
						earlyHits.incrementAndGet();
					} else {
						lateHits.incrementAndGet();
					}
				},
				(material, bounce, cumulativePathMeters) -> {
					assertTrue(cumulativePathMeters > 0.0);
					policyCalls.incrementAndGet();
					return bounce < 2 ? material.scattering() : 1.0;
				}
		);
		assertEquals(64, earlyHits.get());
		assertEquals(64, lateHits.get());
		assertEquals(128, statistics.surfaceHits());
		assertEquals(128, policyCalls.get());
		assertEquals(
				AcousticMaterials.STONE.scattering(),
				statistics.meanScattering(),
				1.0e-12
		);
		assertThrows(
				IllegalArgumentException.class,
				() -> VoxelReflectionProbe.analyze(
						LISTENER,
						volume,
						CONFIG,
						VoxelReflectionProbe.SurfaceHitObserver.NONE,
						(material, bounce, cumulativePathMeters) -> 1.01
				)
		);
	}

	@Test
	void emptyVolumeIsAnechoicAndFullyOpen() {
		SparseMaterialSnapshot empty = SparseMaterialSnapshot.builder().build();
		ReflectionVolume volume = new ReflectionVolume(
				-4,
				-4,
				-4,
				5,
				5,
				5,
				11L,
				true,
				empty
		);

		ReflectionStatistics statistics = VoxelReflectionProbe.analyze(
				LISTENER,
				volume,
				CONFIG
		);
		LateReverbEstimator.Parameters parameters =
				LateReverbEstimator.estimate(statistics, 343.0);

		assertEquals(128, statistics.escapedRays());
		assertEquals(0, statistics.surfaceHits());
		assertEquals(1.0, parameters.openness());
		assertEquals(0.0, parameters.rt60Seconds().totalEnergy());
		assertEquals(0.0, parameters.firstReflectionEnergy().totalEnergy());
		assertEquals(60.0, parameters.directToReverberantDb().low());
	}

	@Test
	void incompleteVolumeAndTruncatedLegsFailClosed() {
		SparseMaterialSnapshot empty = SparseMaterialSnapshot.builder().build();
		ReflectionVolume incomplete = new ReflectionVolume(
				-4,
				-4,
				-4,
				5,
				5,
				5,
				12L,
				false,
				empty
		);
		assertThrows(
				IllegalArgumentException.class,
				() -> VoxelReflectionProbe.analyze(
						LISTENER,
						incomplete,
						CONFIG
				)
		);

		ReflectionVolume complete = new ReflectionVolume(
				-100,
				-100,
				-100,
				101,
				101,
				101,
				13L,
				true,
				empty
		);
		ReflectionStatistics truncated = VoxelReflectionProbe.analyze(
				LISTENER,
				complete,
				new VoxelReflectionProbe.Config(
						16,
						2,
						64.0,
						1,
						1.0e-9
				)
		);
		assertEquals(16, truncated.truncatedLegs());
		assertThrows(
				IllegalArgumentException.class,
				() -> LateReverbEstimator.estimate(truncated, 343.0)
		);
	}

	static ReflectionVolume closedRoom(
			AcousticMaterial material,
			long generation
	) {
		int minimumX = -4;
		int minimumY = -3;
		int minimumZ = -5;
		int maximumX = 5;
		int maximumY = 4;
		int maximumZ = 6;
		SparseMaterialSnapshot.Builder builder =
				SparseMaterialSnapshot.builder();
		DirectPathSolver.MaterialSample surface =
				DirectPathSolver.MaterialSample.full(material);
		for (int x = minimumX; x < maximumX; x++) {
			for (int y = minimumY; y < maximumY; y++) {
				for (int z = minimumZ; z < maximumZ; z++) {
					if (x == minimumX || x == maximumX - 1
							|| y == minimumY || y == maximumY - 1
							|| z == minimumZ || z == maximumZ - 1) {
						builder.put(x, y, z, surface);
					}
				}
			}
		}
		return new ReflectionVolume(
				minimumX,
				minimumY,
				minimumZ,
				maximumX,
				maximumY,
				maximumZ,
				generation,
				true,
				builder.build()
		);
	}
}
