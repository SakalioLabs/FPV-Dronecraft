package com.tenicana.dronecraft.entity;

import java.util.Arrays;
import java.util.Locale;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.RotorSpec;
import com.tenicana.dronecraft.sim.Vec3;

public final class RotorLayoutCodec {
	public static final int MAX_RENDER_ROTORS = 8;
	private static final double MODEL_UNITS_PER_METER = 110.0;
	private static final double MIN_VISUAL_RADIUS_UNITS = 13.0;
	private static final double MAX_QUAD_VISUAL_RADIUS_UNITS = 22.0;
	private static final double MAX_MULTI_VISUAL_RADIUS_UNITS = 26.0;
	private static final String DEFAULT_LAYOUT = encode(DroneConfig.racingQuad());

	private RotorLayoutCodec() {
	}

	public static String defaultLayout() {
		return DEFAULT_LAYOUT;
	}

	public static String encode(DroneConfig config) {
		int count = Math.max(1, Math.min(MAX_RENDER_ROTORS, config.rotors().size()));
		StringBuilder encoded = new StringBuilder();
		encoded.append(count);
		for (int i = 0; i < count; i++) {
			RotorSpec rotor = config.rotors().get(i);
			Vec3 position = rotor.positionBodyMeters();
			encoded
					.append(';')
					.append(format(position.x()))
					.append(',')
					.append(format(position.y()))
					.append(',')
					.append(format(position.z()))
					.append(',')
					.append(rotor.spinDirection());
		}
		return encoded.toString();
	}

	public static Layout decode(String encoded) {
		try {
			if (encoded == null || encoded.isBlank()) {
				return fromConfig(DroneConfig.racingQuad());
			}

			String[] parts = encoded.split(";", -1);
			int count = Math.max(1, Math.min(MAX_RENDER_ROTORS, Integer.parseInt(parts[0])));
			if (parts.length < count + 1) {
				return fromConfig(DroneConfig.racingQuad());
			}

			double[] physicalX = new double[count];
			double[] physicalY = new double[count];
			double[] physicalZ = new double[count];
			int[] spinDirections = new int[count];
			for (int i = 0; i < count; i++) {
				String[] fields = parts[i + 1].split(",", -1);
				if (fields.length != 3 && fields.length != 4) {
					return fromConfig(DroneConfig.racingQuad());
				}
				physicalX[i] = Double.parseDouble(fields[0]);
				if (fields.length == 3) {
					physicalY[i] = 0.0;
					physicalZ[i] = Double.parseDouble(fields[1]);
					spinDirections[i] = Integer.parseInt(fields[2]) < 0 ? -1 : 1;
				} else {
					physicalY[i] = Double.parseDouble(fields[1]);
					physicalZ[i] = Double.parseDouble(fields[2]);
					spinDirections[i] = Integer.parseInt(fields[3]) < 0 ? -1 : 1;
				}
			}
			return layoutFromPhysical(physicalX, physicalY, physicalZ, spinDirections);
		} catch (NumberFormatException exception) {
			return fromConfig(DroneConfig.racingQuad());
		}
	}

	public static Layout fromConfig(DroneConfig config) {
		int count = Math.max(1, Math.min(MAX_RENDER_ROTORS, config.rotors().size()));
		double[] physicalX = new double[count];
		double[] physicalY = new double[count];
		double[] physicalZ = new double[count];
		int[] spinDirections = new int[count];
		for (int i = 0; i < count; i++) {
			RotorSpec rotor = config.rotors().get(i);
			physicalX[i] = rotor.positionBodyMeters().x();
			physicalY[i] = rotor.positionBodyMeters().y();
			physicalZ[i] = rotor.positionBodyMeters().z();
			spinDirections[i] = rotor.spinDirection();
		}
		return layoutFromPhysical(physicalX, physicalY, physicalZ, spinDirections);
	}

	private static Layout layoutFromPhysical(double[] physicalX, double[] physicalY, double[] physicalZ, int[] spinDirections) {
		int count = Math.max(1, Math.min(MAX_RENDER_ROTORS, physicalX.length));
		double maxRadiusMeters = 0.0;
		for (int i = 0; i < count; i++) {
			maxRadiusMeters = Math.max(maxRadiusMeters, Math.hypot(physicalX[i], physicalZ[i]));
		}

		double[] normalizedX = Arrays.copyOf(physicalX, count);
		double[] normalizedZ = Arrays.copyOf(physicalZ, count);
		if (maxRadiusMeters < 1.0e-6) {
			maxRadiusMeters = 1.0;
			for (int i = 0; i < count; i++) {
				double angle = Math.PI * 2.0 * i / count;
				normalizedX[i] = Math.sin(angle);
				normalizedZ[i] = Math.cos(angle);
			}
		}

		double maxVisualRadius = count <= 4 ? MAX_QUAD_VISUAL_RADIUS_UNITS : MAX_MULTI_VISUAL_RADIUS_UNITS;
		double targetRadiusUnits = clamp(maxRadiusMeters * MODEL_UNITS_PER_METER, MIN_VISUAL_RADIUS_UNITS, maxVisualRadius);
		double scale = targetRadiusUnits / maxRadiusMeters;
		float[] modelX = new float[MAX_RENDER_ROTORS];
		float[] modelY = new float[MAX_RENDER_ROTORS];
		float[] modelZ = new float[MAX_RENDER_ROTORS];
		int[] modelSpinDirections = new int[MAX_RENDER_ROTORS];
		Arrays.fill(modelSpinDirections, 1);
		for (int i = 0; i < count; i++) {
			modelX[i] = (float) (normalizedX[i] * scale);
			modelY[i] = (float) (physicalY[i] * scale);
			modelZ[i] = (float) (normalizedZ[i] * scale);
			modelSpinDirections[i] = spinDirections[i] < 0 ? -1 : 1;
		}
		return new Layout(count, modelX, modelY, modelZ, modelSpinDirections);
	}

	private static String format(double value) {
		return String.format(Locale.ROOT, "%.4f", value);
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	public record Layout(int rotorCount, float[] xModelUnits, float[] yModelUnits, float[] zModelUnits, int[] spinDirections) {
		public Layout {
			rotorCount = Math.max(1, Math.min(MAX_RENDER_ROTORS, rotorCount));
			xModelUnits = copy(xModelUnits);
			yModelUnits = copy(yModelUnits);
			zModelUnits = copy(zModelUnits);
			spinDirections = copy(spinDirections);
		}

		public float xModelUnits(int index) {
			return index >= 0 && index < rotorCount ? xModelUnits[index] : 0.0f;
		}

		public float yModelUnits(int index) {
			return index >= 0 && index < rotorCount ? yModelUnits[index] : 0.0f;
		}

		public float zModelUnits(int index) {
			return index >= 0 && index < rotorCount ? zModelUnits[index] : 0.0f;
		}

		public int spinDirection(int index) {
			return index >= 0 && index < rotorCount ? spinDirections[index] : 1;
		}

		private static float[] copy(float[] values) {
			float[] copy = new float[MAX_RENDER_ROTORS];
			if (values != null) {
				System.arraycopy(values, 0, copy, 0, Math.min(values.length, copy.length));
			}
			return copy;
		}

		private static int[] copy(int[] values) {
			int[] copy = new int[MAX_RENDER_ROTORS];
			Arrays.fill(copy, 1);
			if (values != null) {
				System.arraycopy(values, 0, copy, 0, Math.min(values.length, copy.length));
			}
			return copy;
		}
	}
}
