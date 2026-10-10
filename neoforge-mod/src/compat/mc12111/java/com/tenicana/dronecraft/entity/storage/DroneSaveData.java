package com.tenicana.dronecraft.entity.storage;

import java.util.Optional;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The same persisted flight keys through the native 1.21.11 storage facade. */
public final class DroneSaveData {
    private final ValueInput input;
    private final ValueOutput output;
    public DroneSaveData(ValueInput input) { this.input = input; this.output = null; }
    public DroneSaveData(ValueOutput output) { this.output = output; this.input = null; }
    public Optional<String> getString(String key) { return input.getString(key); }
    public double getDoubleOr(String key, double fallback) { return input.getDoubleOr(key, fallback); }
    public void putString(String key, String value) { output.putString(key, value); }
    public void putDouble(String key, double value) { output.putDouble(key, value); }
}
