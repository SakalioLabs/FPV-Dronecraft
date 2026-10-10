package com.tenicana.dronecraft.mekanism;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.DroneInput;
import com.tenicana.dronecraft.sim.FlightMode;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;

class MekanismFlightSystemTest {
	private static final String DIMENSION = "minecraft:overworld";
	private static final Vec3 LAUNCH = new Vec3(10.0, 64.0, 20.0);

	@Test
	void standaloneDroneRemainsUnrestrictedWithoutLoadout() {
		MekanismFlightSystem system = new MekanismFlightSystem();
		var status = system.updateFromSupply("minecraft:the_nether", new Vec3(50_000, 200, 50_000),
				input(true, 1.0), MekanismFlightSystem.QuantumSupply.NONE);
		assertTrue(status.hasPower());
		assertTrue(status.inRange());
		assertFalse(status.quantumActive());
		assertEquals(1.0f, status.chargeFraction());
		assertEquals(Double.POSITIVE_INFINITY, status.maxRangeMeters());
		assertEquals(0L, system.storedEnergyFe());
	}

	@Test
	void energyCostClampsThrottleAndDoesNotChargeDisarmedDrone() {
		assertEquals(0, MekanismFlightSystem.energyUsePerTick(input(false, 1.0)));
		assertEquals(200, MekanismFlightSystem.energyUsePerTick(input(true, -0.5)));
		assertEquals(200, MekanismFlightSystem.energyUsePerTick(input(true, Double.NaN)));
		assertEquals(600, MekanismFlightSystem.energyUsePerTick(input(true, 0.5)));
		assertEquals(1_000, MekanismFlightSystem.energyUsePerTick(input(true, 2.0)));
		assertEquals(0, MekanismFlightSystem.energyUsePerTick(null));
	}

	@Test
	void batteryProvidesLastFullyPaidTickThenDisarms() {
		MekanismFlightSystem system = configured(false);
		CompoundTag saved = new CompoundTag();
		system.save(saved);
		saved.putLong("mek_energy_fe", 600);
		system.load(saved);
		assertTrue(tick(system, LAUNCH, true, 0.5).hasPower());
		assertEquals(0, system.storedEnergyFe());
		assertFalse(tick(system, LAUNCH, true, 0.5).hasPower());
		assertEquals(0, system.storedEnergyFe());
	}

	@Test
	void radioIsHorizontalAndLaunchCenteredAndCannotCrossDimensions() {
		MekanismFlightSystem system = configured(false);
		assertTrue(tick(system, LAUNCH.add(128, 500, 0), false, 0.0).inRange());
		assertFalse(tick(system, LAUNCH.add(128.01, 0, 0), false, 0.0).inRange());
		assertTrue(tick(system, LAUNCH, false, 0.0).inRange());
		assertFalse(system.updateFromSupply("minecraft:the_nether", LAUNCH, input(false, 0),
				MekanismFlightSystem.QuantumSupply.NONE).inRange());
	}

	@Test
	void poweredQuantumLinkPreservesBatteryAndFallsBackImmediately() {
		MekanismFlightSystem system = configured(true);
		Vec3 remote = LAUNCH.add(10_000, 0, 0);
		long fullCharge = system.storedEnergyFe();
		var powered = system.updateFromSupply(DIMENSION, remote, input(true, 0.5),
				new MekanismFlightSystem.QuantumSupply(600, true));
		assertTrue(powered.hasPower());
		assertTrue(powered.inRange());
		assertTrue(powered.quantumActive());
		assertEquals(fullCharge, system.storedEnergyFe());
		var fallback = tick(system, remote, true, 0.5);
		assertTrue(fallback.hasPower());
		assertFalse(fallback.quantumActive());
		assertFalse(fallback.inRange());
		assertEquals(fullCharge - 600, system.storedEnergyFe());
	}

	@Test
	void failedCommitCreditsPartialSourceExtractionExactlyOnce() {
		MekanismFlightSystem system = configured(true);
		TestStorage source = new TestStorage(600, 250);
		var supply = MekanismFlightSystem.drawFromStorage(source, 600);
		assertFalse(supply.active());
		assertEquals(250, supply.extractedFe());
		var status = system.updateFromSupply(DIMENSION, LAUNCH, input(true, 0.5), supply);
		assertTrue(status.hasPower());
		assertFalse(status.quantumActive());
		assertEquals(DronePowerTier.BASIC.capacityFe() - 350, system.storedEnergyFe());
		assertEquals(1, source.executions);
	}

	@Test
	void sourceIsNotDrainedWhenSimulationCannotPayFullDemandOrDroneIsDisarmed() {
		TestStorage insufficient = new TestStorage(599, 599);
		assertEquals(MekanismFlightSystem.QuantumSupply.NONE,
				MekanismFlightSystem.drawFromStorage(insufficient, 600));
		assertEquals(0, insufficient.executions);
		TestStorage idle = new TestStorage(1, 1);
		var supply = MekanismFlightSystem.drawFromStorage(idle, 0);
		assertTrue(supply.active());
		assertEquals(0, supply.extractedFe());
		assertEquals(0, idle.executions);
		assertEquals(MekanismFlightSystem.QuantumSupply.NONE, MekanismFlightSystem.drawFromStorage(null, 600));
	}

	@Test
	void saveReloadKeepsEnergyTiersLaunchPointAndBoundSourceWithoutRefilling() {
		MekanismFlightSystem original = new MekanismFlightSystem();
		DroneLoadout loadout = new DroneLoadout(DronePowerTier.ELITE, DroneRadioTier.ADVANCED, true);
		BlockPos source = new BlockPos(12, 64, 20);
		original.configure(loadout, DIMENSION, LAUNCH, source);
		tick(original, LAUNCH.add(500, 0, 0), true, 0.5);
		CompoundTag saved = new CompoundTag();
		original.save(saved);
		MekanismFlightSystem reloaded = new MekanismFlightSystem();
		reloaded.load(saved);
		assertEquals(loadout, reloaded.loadout());
		assertEquals(original.storedEnergyFe(), reloaded.storedEnergyFe());
		assertEquals(source, reloaded.quantumSource());
		assertFalse(reloaded.quantumActive());
		assertTrue(tick(reloaded, LAUNCH.add(512, 0, 0), false, 0).inRange());
		assertFalse(tick(reloaded, LAUNCH.add(513, 0, 0), false, 0).inRange());
		assertEquals(original.storedEnergyFe(), reloaded.storedEnergyFe());
	}

	@Test
	void corruptOrUnconfiguredSaveCannotRestoreStaleOrOverfilledBattery() {
		MekanismFlightSystem system = configured(true);
		CompoundTag saved = new CompoundTag();
		system.save(saved);
		saved.putLong("mek_energy_fe", Long.MAX_VALUE);
		system.load(saved);
		assertEquals(DronePowerTier.BASIC.capacityFe(), system.storedEnergyFe());
		saved.putLong("mek_energy_fe", -100);
		system.load(saved);
		assertEquals(0, system.storedEnergyFe());
		system.load(new CompoundTag());
		assertFalse(system.configured());
		assertNull(system.quantumSource());
		assertFalse(system.quantumActive());
		assertEquals(0, system.storedEnergyFe());
	}

	@Test
	void capacitiesAndRadioRangesRetainReviewedProgression() {
		assertEquals(5.0, minutes(DronePowerTier.BASIC));
		assertEquals(15.0, minutes(DronePowerTier.ADVANCED));
		assertEquals(40.0, minutes(DronePowerTier.ELITE));
		assertEquals(100.0, minutes(DronePowerTier.ULTIMATE));
		assertEquals(128.0, DroneRadioTier.BASIC.rangeMeters());
		assertEquals(512.0, DroneRadioTier.ADVANCED.rangeMeters());
		assertEquals(2_048.0, DroneRadioTier.ELITE.rangeMeters());
		assertEquals(8_192.0, DroneRadioTier.ULTIMATE.rangeMeters());
		assertEquals(DronePowerTier.BASIC, DronePowerTier.byId(-1));
		assertEquals(DroneRadioTier.BASIC, DroneRadioTier.byId(100));
	}

	private static double minutes(DronePowerTier tier) {
		return tier.capacityFe() / (600.0 * 20 * 60);
	}

	private static MekanismFlightSystem configured(boolean quantum) {
		MekanismFlightSystem system = new MekanismFlightSystem();
		system.configure(new DroneLoadout(DronePowerTier.BASIC, DroneRadioTier.BASIC, quantum),
				DIMENSION, LAUNCH, quantum ? BlockPos.containing(LAUNCH) : null);
		return system;
	}

	private static MekanismFlightSystem.FlightStatus tick(MekanismFlightSystem system, Vec3 position, boolean armed, double throttle) {
		return system.updateFromSupply(DIMENSION, position, input(armed, throttle), MekanismFlightSystem.QuantumSupply.NONE);
	}

	private static DroneInput input(boolean armed, double throttle) {
		return new DroneInput(throttle, 0, 0, 0, armed, true, FlightMode.ANGLE);
	}

	private static final class TestStorage implements IEnergyStorage {
		private final int simulated;
		private final int executed;
		private int executions;

		private TestStorage(int simulated, int executed) {
			this.simulated = simulated;
			this.executed = executed;
		}

		@Override
		public int extractEnergy(int maxExtract, boolean simulate) {
			if (simulate) return Math.min(maxExtract, simulated);
			executions++;
			return Math.min(maxExtract, executed);
		}

		@Override public int receiveEnergy(int maxReceive, boolean simulate) { return 0; }
		@Override public int getEnergyStored() { return simulated; }
		@Override public int getMaxEnergyStored() { return simulated; }
		@Override public boolean canExtract() { return true; }
		@Override public boolean canReceive() { return false; }
	}
}
