package com.tenicana.dronecraft.blackbox;

import java.util.ArrayList;
import java.util.List;

public final class DroneBlackboxRecorder {
	private static final int DEFAULT_CAPACITY = 20 * 60 * 5;

	private final DroneBlackboxSample[] samples;
	private int nextIndex;
	private int size;

	public DroneBlackboxRecorder() {
		this(DEFAULT_CAPACITY);
	}

	public DroneBlackboxRecorder(int capacity) {
		this.samples = new DroneBlackboxSample[capacity];
	}

	public void record(DroneBlackboxSample sample) {
		samples[nextIndex] = sample;
		nextIndex = (nextIndex + 1) % samples.length;
		if (size < samples.length) {
			size++;
		}
	}

	public int size() {
		return size;
	}

	public int capacity() {
		return samples.length;
	}

	public void clear() {
		for (int i = 0; i < samples.length; i++) {
			samples[i] = null;
		}
		nextIndex = 0;
		size = 0;
	}

	public List<DroneBlackboxSample> snapshot() {
		List<DroneBlackboxSample> ordered = new ArrayList<>(size);
		int start = size == samples.length ? nextIndex : 0;
		for (int i = 0; i < size; i++) {
			DroneBlackboxSample sample = samples[(start + i) % samples.length];
			if (sample != null) {
				ordered.add(sample);
			}
		}
		return ordered;
	}

	public String toCsv() {
		StringBuilder builder = new StringBuilder(DroneBlackboxSample.CSV_HEADER).append('\n');
		for (DroneBlackboxSample sample : snapshot()) {
			builder.append(sample.toCsvLine()).append('\n');
		}
		return builder.toString();
	}
}
