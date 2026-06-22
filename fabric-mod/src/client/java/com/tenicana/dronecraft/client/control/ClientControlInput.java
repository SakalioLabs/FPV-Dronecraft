package com.tenicana.dronecraft.client.control;

import com.tenicana.dronecraft.client.DroneClientState.InputSource;

record ClientControlInput(float throttle, float pitch, float roll, float yaw, InputSource source) {
}
