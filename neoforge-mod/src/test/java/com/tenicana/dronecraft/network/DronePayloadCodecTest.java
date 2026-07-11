package com.tenicana.dronecraft.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.connection.ConnectionType;

class DronePayloadCodecTest {
	@Test
	void controlPayloadRoundTripsEveryField() {
		DroneControlPayload payload = new DroneControlPayload(0.625f, -0.25f, 0.5f, -0.75f, true, 2);

		DroneControlPayload decoded = roundTrip(DroneControlPayload.CODEC, payload);

		assertEquals(payload, decoded);
		assertEquals(DroneControlPayload.TYPE, decoded.type());
	}

	@Test
	void viewPayloadRoundTrips() {
		DroneViewPayload payload = new DroneViewPayload(true);

		DroneViewPayload decoded = roundTrip(DroneViewPayload.CODEC, payload);

		assertEquals(payload, decoded);
		assertEquals(DroneViewPayload.TYPE, decoded.type());
	}

	private static <T> T roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
				Unpooled.buffer(),
				RegistryAccess.EMPTY,
				ConnectionType.NEOFORGE
		);
		try {
			codec.encode(buffer, value);
			T decoded = codec.decode(buffer);
			assertFalse(buffer.isReadable(), "codec must consume the complete payload");
			return decoded;
		} finally {
			buffer.release();
		}
	}
}
