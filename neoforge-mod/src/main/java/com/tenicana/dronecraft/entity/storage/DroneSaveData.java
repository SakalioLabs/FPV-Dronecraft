package com.tenicana.dronecraft.entity.storage;

import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Keeps the flight persistence contract independent of the Minecraft NBT facade. */
public final class DroneSaveData {
	private final CompoundTag tag;
	public DroneSaveData(CompoundTag tag) { this.tag = tag; }
	public Optional<String> getString(String key) {
		return tag.contains(key, Tag.TAG_STRING) ? Optional.of(tag.getString(key)) : Optional.empty();
	}
	public double getDoubleOr(String key, double fallback) {
		return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getDouble(key) : fallback;
	}
	public void putString(String key, String value) { tag.putString(key, value); }
	public void putDouble(String key, double value) { tag.putDouble(key, value); }
}
