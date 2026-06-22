package com.tenicana.dronecraft.client.control;

import java.util.List;

@FunctionalInterface
public interface JoystickProvider {
	List<JoystickSnapshot> snapshots();
}
