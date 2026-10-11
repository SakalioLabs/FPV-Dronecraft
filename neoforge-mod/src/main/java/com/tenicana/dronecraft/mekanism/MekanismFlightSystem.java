package com.tenicana.dronecraft.mekanism;

import com.tenicana.dronecraft.sim.DroneInput;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Optional loadout state. Only the entity's server tick may update its energy. */
public final class MekanismFlightSystem {
	public static final int QUANTUM_SEARCH_RADIUS_BLOCKS = 8;
	public static final int IDLE_DRAIN_FE_PER_TICK = 200;
	public static final int THROTTLE_DRAIN_FE_PER_TICK = 800;
	private static final String QUANTUM_ENTANGLOPORTER_ID = "mekanism:quantum_entangloporter";

	private boolean configured;
	private boolean assemblyHardwareDetached;
	private DroneLoadout loadout = DroneLoadout.BASIC;
	private long storedEnergyFe;
	private Vec3 launchPosition = Vec3.ZERO;
	private String launchDimension = "";
	private BlockPos quantumSource;
	private boolean quantumActive;
	private double distanceFromLaunchMeters;

	/** Call once when placing a drone item with a loadout component, never when binding a controller. */
	public void configure(DroneLoadout newLoadout, Level level, Vec3 position) {
		DroneLoadout selected = newLoadout == null ? DroneLoadout.BASIC : newLoadout;
		configure(selected, level.dimension().location().toString(), position,
				selected.quantumLink() ? findQuantumSource(level, BlockPos.containing(position)) : null);
	}

	void configure(DroneLoadout newLoadout, String dimension, Vec3 position, BlockPos source) {
		configured = true;
		assemblyHardwareDetached = false;
		loadout = newLoadout == null ? DroneLoadout.BASIC : newLoadout;
		storedEnergyFe = loadout.powerTier().capacityFe();
		launchPosition = position;
		launchDimension = dimension;
		quantumSource = loadout.quantumLink() && source != null ? source.immutable() : null;
		quantumActive = false;
		distanceFromLaunchMeters = 0.0;
	}

	/** Call once per server tick after retrieving control input and before running flight physics. */
	public FlightStatus update(Level level, Vec3 position, DroneInput input) {
		if (!configured) {
			return FlightStatus.unrestricted();
		}
		if (level.isClientSide) {
			return status(storedEnergyFe > 0L, quantumActive
					|| distanceFromLaunchMeters <= loadout.radioTier().rangeMeters());
		}
		int requestedEnergy = energyUsePerTick(input);
		return updateFromSupply(level.dimension().location().toString(), position, input,
				drawQuantumEnergy(level, requestedEnergy));
	}

	FlightStatus updateFromSupply(String dimension, Vec3 position, DroneInput input, QuantumSupply supply) {
		if (!configured) {
			return FlightStatus.unrestricted();
		}
		distanceFromLaunchMeters = Math.hypot(position.x - launchPosition.x, position.z - launchPosition.z);
		boolean sameDimension = launchDimension.equals(dimension);
		int requestedEnergy = energyUsePerTick(input);
		boolean usableSource = sameDimension && loadout.quantumLink();
		int suppliedEnergy = usableSource ? Math.max(0, Math.min(requestedEnergy, supply.extractedFe())) : 0;
		quantumActive = usableSource && supply.active()
				&& (requestedEnergy == 0 || suppliedEnergy == requestedEnergy);
		int batteryDemand = requestedEnergy - suppliedEnergy;
		boolean hasPower = quantumActive || (batteryDemand == 0 ? storedEnergyFe > 0L : storedEnergyFe >= batteryDemand);
		storedEnergyFe = Math.max(0L, storedEnergyFe - batteryDemand);
		boolean inRange = sameDimension && (quantumActive || distanceFromLaunchMeters <= loadout.radioTier().rangeMeters());
		return status(hasPower, inRange);
	}

	private FlightStatus status(boolean hasPower, boolean inRange) {
		return new FlightStatus(hasPower, inRange, quantumActive, chargeFraction(),
				distanceFromLaunchMeters, loadout.radioTier().rangeMeters());
	}

	/** Replacing hardware retains the launch link and uses the battery item's saved energy. */
	public void replaceAssemblyHardware(DronePowerTier power, DroneRadioTier radio, long energyFe, Level level, Vec3 position) {
		if (power == null) {
			assemblyHardwareDetached |= configured;
			configured = false;
			storedEnergyFe = 0L;
			quantumActive = false;
			return;
		}
		DroneRadioTier selectedRadio = radio == null ? DroneRadioTier.BASIC : radio;
		if (!configured && !assemblyHardwareDetached) {
			configure(new DroneLoadout(power, selectedRadio, false), level, position);
		} else {
			loadout = new DroneLoadout(power, selectedRadio, loadout.quantumLink());
		}
		configured = true;
		assemblyHardwareDetached = false;
		storedEnergyFe = Math.max(0L, Math.min(power.capacityFe(), energyFe));
		quantumActive = false;
	}

	public boolean configured() {
		return configured;
	}

	public DroneLoadout loadout() {
		return loadout;
	}

	public long storedEnergyFe() {
		return storedEnergyFe;
	}

	public float chargeFraction() {
		return configured ? (float) Math.max(0.0, Math.min(1.0,
				storedEnergyFe / (double) loadout.powerTier().capacityFe())) : 1.0f;
	}

	public boolean quantumActive() {
		return configured && quantumActive;
	}

	public double distanceFromLaunchMeters() {
		return distanceFromLaunchMeters;
	}

	public double maxRangeMeters() {
		return configured ? loadout.radioTier().rangeMeters() : Double.POSITIVE_INFINITY;
	}

	public BlockPos quantumSource() {
		return quantumSource;
	}

	public void save(CompoundTag output) {
		output.putBoolean("mek_configured", configured);
		output.putBoolean("mek_assembly_hardware_detached", assemblyHardwareDetached);
		if (!configured && !assemblyHardwareDetached) {
			return;
		}
		output.putString("mek_power_tier", loadout.powerTier().getSerializedName());
		output.putString("mek_radio_tier", loadout.radioTier().getSerializedName());
		output.putBoolean("mek_quantum_link", loadout.quantumLink());
		output.putLong("mek_energy_fe", storedEnergyFe);
		output.putDouble("mek_launch_x", launchPosition.x);
		output.putDouble("mek_launch_y", launchPosition.y);
		output.putDouble("mek_launch_z", launchPosition.z);
		output.putString("mek_launch_dimension", launchDimension);
		if (quantumSource != null) {
			output.putLong("mek_quantum_source", quantumSource.asLong());
		} else {
			output.remove("mek_quantum_source");
		}
	}

	/** Loading never refills the battery or searches for a replacement quantum source. */
	public void load(CompoundTag input) {
		configured = input.getBoolean("mek_configured");
		assemblyHardwareDetached = !configured && input.getBoolean("mek_assembly_hardware_detached");
		loadout = DroneLoadout.BASIC;
		storedEnergyFe = 0L;
		launchPosition = Vec3.ZERO;
		launchDimension = "";
		quantumSource = null;
		quantumActive = false;
		distanceFromLaunchMeters = 0.0;
		if (!configured && !assemblyHardwareDetached) {
			return;
		}
		loadout = new DroneLoadout(DronePowerTier.byName(input.getString("mek_power_tier")),
				DroneRadioTier.byName(input.getString("mek_radio_tier")), input.getBoolean("mek_quantum_link"));
		storedEnergyFe = Math.max(0L, Math.min(loadout.powerTier().capacityFe(), input.getLong("mek_energy_fe")));
		launchPosition = new Vec3(finiteOrZero(input.getDouble("mek_launch_x")),
				finiteOrZero(input.getDouble("mek_launch_y")), finiteOrZero(input.getDouble("mek_launch_z")));
		launchDimension = input.getString("mek_launch_dimension");
		if (loadout.quantumLink() && input.contains("mek_quantum_source")) {
			quantumSource = BlockPos.of(input.getLong("mek_quantum_source"));
		}
	}

	static int energyUsePerTick(DroneInput input) {
		if (input == null || !input.armed()) {
			return 0;
		}
		double throttle = Double.isFinite(input.throttle()) ? Math.max(0.0, Math.min(1.0, input.throttle())) : 0.0;
		return IDLE_DRAIN_FE_PER_TICK + (int) Math.round(THROTTLE_DRAIN_FE_PER_TICK * throttle);
	}

	private QuantumSupply drawQuantumEnergy(Level level, int requestedEnergy) {
		if (!loadout.quantumLink() || quantumSource == null
				|| !launchDimension.equals(level.dimension().location().toString())
				|| !level.isLoaded(quantumSource) || !isQuantumEntangloporter(level, quantumSource)) {
			return QuantumSupply.NONE;
		}
		// Sided queries honor the Entangloporter's energy-output configuration.
		for (Direction side : Direction.values()) {
			IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, quantumSource, side);
			QuantumSupply supply = drawFromStorage(storage, requestedEnergy);
			if (supply.active() || supply.extractedFe() > 0) {
				return supply;
			}
		}
		return QuantumSupply.NONE;
	}

	static QuantumSupply drawFromStorage(IEnergyStorage storage, int requestedEnergy) {
		int demand = Math.max(1, requestedEnergy);
		if (storage == null || !storage.canExtract() || storage.extractEnergy(demand, true) < demand) {
			return QuantumSupply.NONE;
		}
		if (requestedEnergy == 0) {
			return new QuantumSupply(0, true);
		}
		int extracted = Math.max(0, Math.min(demand, storage.extractEnergy(demand, false)));
		// IEnergyStorage has no transaction rollback. Credit any actual partial extraction
		// to this tick and let the onboard battery cover only the remainder.
		return new QuantumSupply(extracted, extracted == demand);
	}

	private static BlockPos findQuantumSource(Level level, BlockPos center) {
		BlockPos best = null;
		double bestDistance = Double.POSITIVE_INFINITY;
		int radius = QUANTUM_SEARCH_RADIUS_BLOCKS;
		for (int y = -radius; y <= radius; y++) {
			for (int x = -radius; x <= radius; x++) {
				for (int z = -radius; z <= radius; z++) {
					BlockPos candidate = center.offset(x, y, z);
					double distance = candidate.distSqr(center);
					if (distance <= radius * radius && distance < bestDistance
							&& level.isLoaded(candidate) && isQuantumEntangloporter(level, candidate)) {
						best = candidate.immutable();
						bestDistance = distance;
					}
				}
			}
		}
		return best;
	}

	private static boolean isQuantumEntangloporter(Level level, BlockPos pos) {
		return QUANTUM_ENTANGLOPORTER_ID.equals(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString());
	}

	private static double finiteOrZero(double value) {
		return Double.isFinite(value) ? value : 0.0;
	}

	record QuantumSupply(int extractedFe, boolean active) {
		static final QuantumSupply NONE = new QuantumSupply(0, false);
	}

	public record FlightStatus(boolean hasPower, boolean inRange, boolean quantumActive, float chargeFraction,
			double distanceFromLaunchMeters, double maxRangeMeters) {
		private static FlightStatus unrestricted() {
			return new FlightStatus(true, true, false, 1.0f, 0.0, Double.POSITIVE_INFINITY);
		}
	}
}
