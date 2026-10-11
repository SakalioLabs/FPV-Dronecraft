package com.tenicana.dronecraft.assembly;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.tenicana.dronecraft.registry.DroneItems;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** The fixed component inventory used by an assembled drone. */
public final class DroneAssemblyInventory implements Container {
	public static final int MOTOR_0 = 0;
	public static final int MOTOR_1 = 1;
	public static final int MOTOR_2 = 2;
	public static final int MOTOR_3 = 3;
	public static final int PROPELLER_0 = 4;
	public static final int PROPELLER_1 = 5;
	public static final int PROPELLER_2 = 6;
	public static final int PROPELLER_3 = 7;
	public static final int ESC = 8;
	public static final int FLIGHT_CONTROLLER = 9;
	public static final int RECEIVER = 10;
	public static final int BATTERY = 11;
	public static final int CAMERA = 12;
	public static final int SLOT_COUNT = 13;
	public static final int FULL_MASK = (1 << SLOT_COUNT) - 1;
	public static final int REQUIRED_FLIGHT_MASK = FULL_MASK & ~(1 << CAMERA);

	private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
	private final Runnable changedListener;
	private final Runnable beforeMutation;

	public DroneAssemblyInventory(Runnable changedListener) {
		this(changedListener, () -> { });
	}

	public DroneAssemblyInventory(Runnable changedListener, Runnable beforeMutation) {
		this.changedListener = Objects.requireNonNull(changedListener, "changedListener");
		this.beforeMutation = Objects.requireNonNull(beforeMutation, "beforeMutation");
	}

	@Override
	public int getContainerSize() {
		return SLOT_COUNT;
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) return false;
		}
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		checkSlot(slot);
		return items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		checkSlot(slot);
		if (amount <= 0 || items.get(slot).isEmpty()) return ItemStack.EMPTY;
		beforeMutation.run();
		ItemStack removed = items.get(slot).split(amount);
		if (items.get(slot).isEmpty()) items.set(slot, ItemStack.EMPTY);
		if (!removed.isEmpty()) setChanged();
		return removed;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		checkSlot(slot);
		if (items.get(slot).isEmpty()) return ItemStack.EMPTY;
		beforeMutation.run();
		ItemStack removed = items.set(slot, ItemStack.EMPTY);
		if (!removed.isEmpty()) setChanged();
		return removed;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		checkSlot(slot);
		Objects.requireNonNull(stack, "stack");
		if (!stack.isEmpty() && (stack.getCount() != 1 || !canPlaceItem(slot, stack))) {
			throw new IllegalArgumentException("Invalid item stack for drone assembly slot " + slot);
		}
		beforeMutation.run();
		items.set(slot, stack);
		setChanged();
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	public void setChanged() {
		changedListener.run();
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		if (slot < 0 || slot >= SLOT_COUNT || stack == null || stack.isEmpty()) return false;
		return switch (slot) {
			case MOTOR_0, MOTOR_1, MOTOR_2, MOTOR_3 -> stack.is(DroneItems.brushlessMotor());
			case PROPELLER_0, PROPELLER_1, PROPELLER_2, PROPELLER_3 -> stack.is(DroneItems.propeller());
			case ESC -> stack.is(DroneItems.esc());
			case FLIGHT_CONTROLLER -> stack.is(DroneItems.flightController());
			case RECEIVER -> stack.is(DroneItems.receiver()) || DroneItems.radioTier(stack) != null;
			case BATTERY -> stack.is(DroneItems.lipoBattery()) || DroneItems.powerTier(stack) != null;
			case CAMERA -> stack.is(DroneItems.fpvCamera());
			default -> false;
		};
	}

	public int mask() {
		int mask = 0;
		for (int slot = 0; slot < SLOT_COUNT; slot++) {
			if (!items.get(slot).isEmpty()) mask |= 1 << slot;
		}
		return mask;
	}

	public boolean isComplete() {
		return mask() == FULL_MASK;
	}

	/** A camera is optional for flying the aircraft in line of sight. */
	public boolean isFlightReady() {
		return (mask() & REQUIRED_FLIGHT_MASK) == REQUIRED_FLIGHT_MASK;
	}

	/** Replaces all contents with the standard starter assembly and notifies once. */
	public void fillPrototype() {
		beforeMutation.run();
		for (int slot = MOTOR_0; slot <= MOTOR_3; slot++) items.set(slot, new ItemStack(DroneItems.brushlessMotor()));
		for (int slot = PROPELLER_0; slot <= PROPELLER_3; slot++) items.set(slot, new ItemStack(DroneItems.propeller()));
		items.set(ESC, new ItemStack(DroneItems.esc()));
		items.set(FLIGHT_CONTROLLER, new ItemStack(DroneItems.flightController()));
		items.set(RECEIVER, new ItemStack(DroneItems.receiver()));
		items.set(BATTERY, new ItemStack(DroneItems.lipoBattery()));
		items.set(CAMERA, new ItemStack(DroneItems.fpvCamera()));
		setChanged();
	}

	/** Strictly loads an exact-size, valid snapshot, copying stacks and notifying once. */
	public void loadContents(List<ItemStack> contents) {
		Objects.requireNonNull(contents, "contents");
		if (contents.size() != SLOT_COUNT) throw new IllegalArgumentException("Expected exactly " + SLOT_COUNT + " assembly stacks");
		NonNullList<ItemStack> validated = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
		for (int slot = 0; slot < SLOT_COUNT; slot++) {
			ItemStack stack = Objects.requireNonNull(contents.get(slot), "contents[" + slot + "]");
			if (!stack.isEmpty() && (stack.getCount() != 1 || !canPlaceItem(slot, stack))) {
				throw new IllegalArgumentException("Invalid item stack for drone assembly slot " + slot);
			}
			validated.set(slot, stack.copy());
		}
		beforeMutation.run();
		for (int slot = 0; slot < SLOT_COUNT; slot++) items.set(slot, validated.get(slot));
		setChanged();
	}

	public List<ItemStack> contentsSnapshot() {
		List<ItemStack> snapshot = new ArrayList<>(SLOT_COUNT);
		for (ItemStack stack : items) snapshot.add(stack.copy());
		return List.copyOf(snapshot);
	}

	@Override
	public void clearContent() {
		if (isEmpty()) return;
		beforeMutation.run();
		for (int slot = 0; slot < SLOT_COUNT; slot++) items.set(slot, ItemStack.EMPTY);
		setChanged();
	}

	private static void checkSlot(int slot) {
		if (slot < 0 || slot >= SLOT_COUNT) throw new IndexOutOfBoundsException("Invalid assembly slot " + slot);
	}
}
