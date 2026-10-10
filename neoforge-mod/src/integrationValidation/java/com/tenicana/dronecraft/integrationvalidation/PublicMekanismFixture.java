package com.tenicana.dronecraft.integrationvalidation;

import java.lang.reflect.Method;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Uses public methods in the real Mekanism 10.7.19.85 jar; it never substitutes an energy provider. */
final class PublicMekanismFixture {
	private final Object tile;
	private final Class<?> energyContainerType;
	private Object container;

	PublicMekanismFixture(ServerLevel level, BlockPos position, UUID owner) throws ReflectiveOperationException {
		tile = level.getBlockEntity(position);
		Class<?> quantumType = Class.forName("mekanism.common.tile.TileEntityQuantumEntangloporter");
		if (!quantumType.isInstance(tile)) {
			throw new IllegalStateException("Placed block has no native Quantum Entangloporter tile");
		}
		Object security = tile.getClass().getMethod("getSecurity").invoke(tile);
		security.getClass().getMethod("setOwnerUUID", UUID.class).invoke(security, owner);
		Class<?> securityMode = Class.forName("mekanism.api.security.SecurityMode");
		Object publicMode = constant(securityMode, "PUBLIC");
		security.getClass().getMethod("setMode", securityMode).invoke(security, publicMode);
		Class<?> frequencyType = Class.forName("mekanism.common.lib.frequency.FrequencyType");
		Object inventoryType = frequencyType.getField("INVENTORY").get(null);
		Class<?> identityType = Class.forName("mekanism.common.lib.frequency.Frequency$FrequencyIdentity");
		Object identity = identityType.getConstructor(Object.class, securityMode, UUID.class)
				.newInstance("fpvdrone-validation-" + owner, publicMode, owner);
		tile.getClass().getMethod("setFrequency", frequencyType, identityType, UUID.class)
				.invoke(tile, inventoryType, identity, owner);

		Class<?> transmissionType = Class.forName("mekanism.common.lib.transmitter.TransmissionType");
		Object energy = constant(transmissionType, "ENERGY");
		Object component = tile.getClass().getMethod("getConfig").invoke(tile);
		Object config = component.getClass().getMethod("getConfig", transmissionType).invoke(component, energy);
		Class<?> dataType = Class.forName("mekanism.common.tile.component.config.DataType");
		Object output = constant(dataType, "OUTPUT");
		Class<?> relativeSideType = Class.forName("mekanism.api.RelativeSide");
		Method setSide = config.getClass().getMethod("setDataType", dataType, relativeSideType);
		Method getSide = config.getClass().getMethod("getDataType", relativeSideType);
		Method notifySide = component.getClass().getMethod("sideChanged", transmissionType, relativeSideType);
		for (Object side : relativeSideType.getEnumConstants()) {
			setSide.invoke(config, output, side);
			if (!output.equals(getSide.invoke(config, side))) {
				throw new IllegalStateException("Native energy config rejected OUTPUT on " + side);
			}
			notifySide.invoke(component, energy, side);
		}
		config.getClass().getMethod("setEjecting", boolean.class).invoke(config, false);
		energyContainerType = Class.forName("mekanism.api.energy.IEnergyContainer");
	}

	boolean frequencyReady() throws ReflectiveOperationException {
		Object frequency = tile.getClass().getMethod("getFreq").invoke(tile);
		if (frequency == null) return false;
		container = frequency.getClass().getField("storedEnergy").get(frequency);
		return energyContainerType.isInstance(container)
				&& (boolean) tile.getClass().getMethod("hasFrequency").invoke(tile);
	}

	void fill() throws ReflectiveOperationException {
		long maximum = ((Number) energyContainerType.getMethod("getMaxEnergy").invoke(container)).longValue();
		if (maximum <= 0L) throw new IllegalStateException("Native frequency has no energy capacity");
		setEnergy(Math.min(maximum, 1_000_000L));
	}

	void setEnergy(long value) throws ReflectiveOperationException {
		energyContainerType.getMethod("setEnergy", long.class).invoke(container, value);
	}

	long energy() throws ReflectiveOperationException {
		return ((Number) energyContainerType.getMethod("getEnergy").invoke(container)).longValue();
	}

	static IEnergyStorage extractingCapability(ServerLevel level, BlockPos position, int demand) {
		for (Direction side : Direction.values()) {
			IEnergyStorage capability = level.getCapability(Capabilities.EnergyStorage.BLOCK, position, side);
			if (capability != null && capability.canExtract() && capability.extractEnergy(demand, true) >= demand) {
				return capability;
			}
		}
		throw new IllegalStateException("No real sided FE capability can extract " + demand + " FE");
	}

	static int availableFe(ServerLevel level, BlockPos position, int demand) {
		int available = -1;
		for (Direction side : Direction.values()) {
			IEnergyStorage capability = level.getCapability(Capabilities.EnergyStorage.BLOCK, position, side);
			if (capability != null && capability.canExtract()) {
				available = Math.max(available, capability.extractEnergy(demand, true));
			}
		}
		if (available < 0) throw new IllegalStateException("Configured real source exposes no extracting FE side");
		return available;
	}

	private static Object constant(Class<?> type, String name) throws ReflectiveOperationException {
		return type.getField(name).get(null);
	}
}
