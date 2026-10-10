package com.tenicana.dronecraft.client.render;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

import com.tenicana.dronecraft.entity.RotorLayoutCodec;

public class DroneEntityRenderState extends EntityRenderState {
	public float pitchRadians;
	public float yawRadians;
	public float rollRadians;
	public float motorPower;
	public int rotorCount = 4;
	public final float[] rotorRpm = new float[RotorLayoutCodec.MAX_RENDER_ROTORS];
	public final float[] rotorXModelUnits = new float[RotorLayoutCodec.MAX_RENDER_ROTORS];
	public final float[] rotorYModelUnits = new float[RotorLayoutCodec.MAX_RENDER_ROTORS];
	public final float[] rotorZModelUnits = new float[RotorLayoutCodec.MAX_RENDER_ROTORS];
	public final int[] rotorSpinDirection = new int[RotorLayoutCodec.MAX_RENDER_ROTORS];
	public boolean armed;
	public boolean hiddenInFpv;
}
