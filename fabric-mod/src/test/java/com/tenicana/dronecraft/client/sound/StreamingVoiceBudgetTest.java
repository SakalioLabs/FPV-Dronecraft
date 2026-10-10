package com.tenicana.dronecraft.client.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StreamingVoiceBudgetTest {
	@Test
	void reservesTheDeclaredDroneVoiceSetWithinThePinnedDeviceBudget() {
		assertEquals(6, StreamingVoiceBudget.MAX_DRONES);
		assertEquals(2, StreamingVoiceBudget.LAYERS_PER_DRONE);
		assertEquals(1, StreamingVoiceBudget.WET_BUSES);
		assertEquals(13, StreamingVoiceBudget.RESERVED_DRONE_STREAMS);

		assertEquals(21, StreamingVoiceBudget.streamingLimit(255, 8));
		assertEquals(234, 255 - StreamingVoiceBudget.streamingLimit(255, 8));
	}

	@Test
	void reservesEightStaticChannelsAtTheLowAndTypicalCapacityBoundaries() {
		assertEquals(8, StreamingVoiceBudget.streamingLimit(16, 2));
		assertEquals(8, 16 - StreamingVoiceBudget.streamingLimit(16, 2));
		assertEquals(2, StreamingVoiceBudget.streamingLimit(10, 2));
		assertEquals(8, 10 - StreamingVoiceBudget.streamingLimit(10, 2));
	}

	@Test
	void smallDevicesKeepTheVanillaAllowanceAndNeverExceedAvailableChannels() {
		assertEquals(0, StreamingVoiceBudget.streamingLimit(0, 2));
		assertEquals(1, StreamingVoiceBudget.streamingLimit(1, 2));
		assertEquals(2, StreamingVoiceBudget.streamingLimit(9, 2));
		assertEquals(8, StreamingVoiceBudget.streamingLimit(9, 8));
		for (int total = 0; total < 10; total++) {
			for (int vanilla = 2; vanilla <= 8; vanilla++) {
				int limit = StreamingVoiceBudget.streamingLimit(total, vanilla);
				assertTrue(limit >= 0 && limit <= total);
				assertTrue(limit <= vanilla);
			}
		}
	}

	@Test
	void preservesVanillaAndStaticCapacityBoundsAcrossSupportedInputs() {
		for (int total = 10; total <= 255; total++) {
			for (int vanilla = 2; vanilla <= 8; vanilla++) {
				int limit = StreamingVoiceBudget.streamingLimit(total, vanilla);
				assertTrue(limit >= vanilla && limit <= total);
				assertTrue(limit <= vanilla + StreamingVoiceBudget.RESERVED_DRONE_STREAMS);
				if (total >= vanilla + 8) {
					assertTrue(total - limit >= 8);
				}
			}
		}
	}

	@Test
	void rejectsValuesOutsideMinecraftCapacityAndAllowanceDomains() {
		assertThrows(IllegalArgumentException.class, () -> StreamingVoiceBudget.streamingLimit(-1, 2));
		assertThrows(IllegalArgumentException.class, () -> StreamingVoiceBudget.streamingLimit(10, 1));
		assertThrows(IllegalArgumentException.class, () -> StreamingVoiceBudget.streamingLimit(10, 9));
	}
}
