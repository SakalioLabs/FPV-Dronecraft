# FPV Dronecraft

High-frequency multirotor drone simulation prototype for Minecraft/Fabric.

The project is intentionally split into two modules:

- `drone-sim-core`: pure Java 6DOF multirotor physics, PID rate control, motor dynamics, and mixer logic.
- `fabric-mod`: Fabric shell that registers the drone entity, controller item, client controls, renderer, and networking.

The simulator uses Minecraft as the world, renderer, input, and networking layer. Flight dynamics live in `drone-sim-core` so they can be tested and tuned without starting the game.

## Build

```powershell
.\gradlew.bat build
```

The playable Fabric jar is generated at:

```text
fabric-mod/build/libs/fpv-dronecraft-fabric-0.1.0.jar
```

## Offline Physics Benchmark

You can generate a deterministic flight CSV without launching Minecraft:

```powershell
.\gradlew.bat :drone-sim-core:offlineFlight
```

By default this writes:

```text
drone-sim-core/build/offline-flight/racing_quad.csv
```

The scripted profile runs the same 200 Hz physics loop used by the mod and samples a CSV at 50 Hz. It includes takeoff, hover, zero-throttle descent, high-throttle propwash recovery, crosswind slip, pitch/roll/yaw steps, throttle punch, and throttle-chop active braking. The columns include position, velocity, true acceleration, true and estimated attitude, estimator error and accelerometer trust, true and gyro-measured body rates, thermal/vibration-driven gyro bias, g-sensitive gyro error, and clipping, motor-fundamental and blade-pass dynamic gyro notch frequency/attenuation, accelerometer specific force at the configured IMU mount plus scale-factor/cross-axis error, bias, and clipping, pressure-altitude barometer altitude/vertical-speed/pressure/error telemetry with propwash, ground-pressure, static-port dynamic-pressure, and rapid-rotation disturbance, raw and processed RC commands, receiver frame age/interval/error telemetry, flight mode, link-loss/failsafe telemetry, ESC output plus command frame age/interval/error telemetry, ESC desync, per-ESC temperature/cooling/thermal-limit telemetry, per-motor RPM/target RPM/tracking error/actuator authority/angular acceleration/aerodynamic torque/mechanical-loss torque/commutation torque ripple/shaft power/battery current/phase current/current ripple/electrical-efficiency/voltage-headroom with voltage-limit loss, battery nonlinear LiPo open-circuit voltage, low-SOC resistance rise, ohmic sag, transient sag recovery, active-braking regenerative current, battery bus voltage spike, battery bus ripple voltage, battery pack temperature/cooling/thermal-limit telemetry, battery current-limit foldback, motor heat, local motor cooling factor, rotor thrust plus body-frame force/torque vector telemetry, per-rotor spring-damped arm-flex intensity, healthy-prop rotor imbalance with rotating lateral force, axis-aware induced inflow, effective translational lift, rotor advance ratio, rotor tip Mach, rotor blade angle-of-attack, blade-element stall, advancing/retreating blade lift-dissymmetry, blade-pass thrust-ripple intensity, rotor aerodynamic load, rotor inflow-skew torque, same-frame rotor wake-interference intensity, crossflow-convected wake sweep, and wake-swirl velocity, rotor inertia/gyroscopic torque, dynamic flapping tilt/force, elastic blade-coning intensity, rotor stall intensity, rotor vibration, propwash wake retention, vortex-ring-state intensity, mixer saturation, split low/high mixer saturation and headroom, achieved mixer torque and per-axis mixer authority, PID setpoint/error and P/I/D/feedforward/output terms, self-level target/error/blend telemetry, PID attenuation, dynamic D-term low-pass cutoff, anti-gravity boost, scalar and per-axis I-term relax/anti-windup telemetry, propwash torque, airframe aerodynamic torque, dynamic-pressure airframe angular-drag torque, airframe lift/sideforce and separated-flow drag rise/buffeting, near-ground cushion drag, rotor-wash slipstream drag, rotor near-wall sidewash/cushion force, body-frame relative air velocity, airspeed, angle of attack, sideslip, turbulence intensity, obstacle proximity, standard-atmosphere and wet-air effective air density, ambient temperature, average and per-rotor water-immersion intensity, precipitation wetness, raw weather wind and boundary-layer-adjusted effective air-mass wind, gust speed, wind-shear acceleration, ceiling clearance/effect, per-rotor environment thrust multipliers, per-rotor side-flow obstruction, wind-turbulence torque, and appended rotor 4..7 extension telemetry for six- and eight-rotor frames.

To compare airframe presets or choose a custom output file:

```powershell
.\gradlew.bat :drone-sim-core:offlineFlight -Ppreset=cinewhoop
.\gradlew.bat :drone-sim-core:offlineFlight -Ppreset=heavy_lift -Poutput=C:\temp\heavy_lift.csv
.\gradlew.bat :drone-sim-core:offlineFlight -Ppreset=hex_lift
.\gradlew.bat :drone-sim-core:offlineFlight -Ppreset=octo_lift
.\gradlew.bat :drone-sim-core:offlineFlight -Ppreset=coaxial_x8
```

## Prototype Controls

1. Add the `FPV Drone Controller` item from the FPV Dronecraft creative tab.
2. Right-click with the controller to spawn and bind a drone, or run `/fpvdrone spawn` to spawn and bind the default racing quad.
3. Hold the controller while flying, or press `V` to toggle the virtual controller for your nearest bound drone.
4. Press `R` to arm or disarm.
5. Use `Space` / `Left Shift` for throttle.
6. Use `W` / `S` for pitch, `A` / `D` for roll, and `Q` / `E` for yaw.
7. Press `M` to cycle `ACRO`, `ANGLE`, and `HORIZON` flight modes.
8. Press `G` to toggle joystick/gamepad input. Press `H` to reload the client input config after editing it.
9. Right-click an existing drone with the controller to bind it. Sneak-right-click it to repair frame and rotor damage.

For command-based setup and testing, use:

```text
/fpvdrone spawn
/fpvdrone spawn racing_quad
/fpvdrone spawn cinewhoop
/fpvdrone spawn heavy_lift
/fpvdrone spawn hex_lift
/fpvdrone spawn octo_lift
/fpvdrone spawn coaxial_x8
/fpvdrone status
/fpvdrone fault rotor 2 0.25
/fpvdrone fault propstrike 1 0.12
/fpvdrone repair
```

After spawning by command, press `V` to enable client-side virtual control, then `R` to arm. Press `V` again to return normal player movement keys to Minecraft. `/fpvdrone status` prints a one-line flight readiness digest for the nearest bound drone: flight mode, armed/link/failsafe state, receiver frame age/interval/error, processed stick command, speed, contact impact/slip/bounce speed, contact angular impulse, airspeed, airframe lift/sideforce, near-ground cushion drag, rotor-wash slipstream drag, rotor near-wall sidewash/cushion force, barometer altitude/vertical-speed/pressure/error, battery sag/current/regenerative-current/bus-spike/limit/current-limit foldback, IMU clipping, dynamic D-term low-pass cutoff, frame and rotor health, motor and ESC thermal limiting/cooling, ESC command signal age/interval/error, ESC desync, rotor aerodynamic load, prop-surface scrape load, propwash, vortex-ring-state intensity, effective translational lift, rotor advance ratio, rotor tip Mach, rotor inflow skew, same-frame rotor wake interference, nearby-drone wake, ceiling effect, per-rotor environment thrust asymmetry, rotor side-flow blockage, water immersion, rain wetness, ambient temperature, rotor stall, vibration, blade coning, rotor flapping tilt, mixer saturation, raw/effective air-mass wind, gust, wind shear, turbulence/obstacle airflow, blackbox sample count, diagnostic state, and active warnings such as `battery-limit`, `current-limit`, `bus-spike`, `gusty-air`, `contact-impact`, `ground-slide`, `contact-tumble`, `prop-scrape`, `wall-effect`, `water-ingress`, `rain-wet`, `cold-air`, `hot-air`, `imu-clip`, `thermal-limit`, `esc-thermal-limit`, `esc-desync`, `mixer-saturation`, `rotor-stall`, `rotor-coning`, `rotor-flapping`, `tip-mach`, `high-advance`, `rotor-wake`, `vrs`, `propwash`, `baro-disturbed`, `ceiling-effect`, `env-asymmetry`, `rotor-flow-blocked`, or `dirty-air`. `fault rotor` injects direct single-rotor efficiency loss without pretending a collision happened. `fault propstrike` injects a prop-strike event as if that rotor hit an obstacle, so HUD/status prop-strike counters and blackbox prop-strike columns update. `repair` resets frame, rotor health, and prop-strike telemetry.

This first prototype runs the authoritative physics on the server at 200 Hz by doing ten 5 ms substeps per Minecraft tick.
The in-game blackbox records the substep count, physics step duration, and resulting physics rate for each sampled entity tick so diagnostic logs can prove which integration loop generated the flight data.
Controller packets pass through a simulated RC link before the flight controller: receiver frame-rate holding, channel-resolution quantization, command latency, stick smoothing, last-valid-frame hold during short packet loss, and a configurable failsafe cut after sustained link loss. After the mixer and ESC curve/slew/deadband stage, motor commands also pass through a separate ESC signal model with command-frame holding, per-motor sub-frame phase staggering, and output-resolution quantization, so low-rate PWM-style and high-rate digital ESC behavior can be compared.

When the drone is armed and you are holding the controller, the client switches into an FPV camera and renders a compact flight HUD with flight mode, throttle, RC link state, processed flight-controller command, rate setpoints, gyro-measured rates, IMU clipping, PID output torque, PID attenuation, dynamic D-term low-pass cutoff, I-term relax, anti-gravity boost, estimated attitude, estimator error, accelerometer trust, average and per-motor output, average motor RPM, per-rotor thrust, battery voltage, state of charge, net current draw, active-braking regenerative current, bus voltage spike, current-limit foldback, low-battery power limiting, motor temperature, ESC temperature, motor/ESC thermal thrust limiting, rotor aerodynamic load, speed, contact impact/slip/bounce speed and angular impulse, altitude, synced barometer state, attitude, damage, rotor vibration, prop-surface scrape load, airspeed, angle of attack, sideslip, effective translational lift, rotor advance ratio, rotor tip Mach, rotor inflow skew, same-frame rotor wake interference, mixer saturation, propwash intensity, vortex-ring-state intensity, rotor stall intensity, rotor flapping tilt, airframe lift/sideforce force, near-ground cushion drag, rotor-wash slipstream drag, rotor near-wall sidewash/cushion force, nearby-drone wake intensity, ceiling-effect intensity, turbulence intensity, obstacle proximity, water immersion, rain wetness, ambient temperature, ground-effect multiplier, raw/effective wind speed, gust speed, and wind-shear telemetry. Multi-rotor HUD motor bars, thrust readout, and rotor-health summary use the synced rotor count up to eight rotors, so `hex_lift` shows all six motor outputs instead of only the first four. The entity renderer also consumes a synced rotor-layout string derived from the active `RotorSpec` geometry, including vertical rotor offsets for stacked coaxial layouts, so six-rotor and future larger frames render their actual rotor positions instead of a fixed quad shell. The collision footprint is recomputed from the active rotor geometry and refreshed when tuning or presets change, so a larger six-rotor frame no longer keeps the compact racing-quad body box. The rendered rotor blades are attached to the airframe transform and spin from the synced per-motor RPM telemetry, including opposite spin directions from the configured rotor layout.
The FPV camera uses the drone body attitude, configurable camera tilt/offset, a configurable wide FPV field of view, speed/throttle-based dynamic FOV stretch, video-link pose latency, rotor/propwash vibration, and rolling-shutter jello driven by motor RPM and rotor roughness. The HUD shows whether the current command source is keyboard or gamepad, and keyboard input remains the fallback if no compatible joystick is detected.
The HUD also shows accumulated crash damage, per-rotor health, cumulative prop-strike count, and the last struck rotor/severity. Contact response separates blocked-axis impact, tangential slip, rebound, and angular impulse before applying damage, so a hard vertical landing, a wall bounce, and a fast scraping slide no longer look identical to the damage model or blackbox. Minecraft block material is sampled at the contact patch: ice keeps slides long, slime boosts rebound, honey/mud/soul-sand surfaces brake harder, and abrasive cactus contact increases prop scrape damage. Off-center contact uses the current attitude, rotor-arm geometry, and configured body inertia to inject a short angular-velocity kick, letting glancing wall hits and tilted landings tumble the frame instead of only changing linear velocity. Hard impacts reduce frame integrity and individual rotor efficiency; a badly damaged drone will lose thrust authority or disarm until repaired. Spinning prop disks are sampled independently against nearby block collision, so clipping a gate, wall edge, floor, or ceiling can damage one rotor before the whole frame hits. While a disk keeps scraping a surface, the physics core applies a decaying surface-scrape load that drags that motor RPM down, raises ESC desync risk, increases current/load, and adds vibration without counting a new damage event every tick. The damaged rotor then loses thrust authority and behaves like a bent prop: rotor health is folded into effective 1x RPM imbalance, adding rotating lateral force, mechanical loss, commutation ripple, current ripple, and vibration through the same physics path used by healthy-prop imbalance. `/fpvdrone status` reports the same prop-strike count and last struck rotor/severity for command-based checks, and `/fpvdrone fault rotor` lets you inject that asymmetric damage on demand for tuning.

## Scripted In-Game Diagnostic

After binding a drone, you can run a repeatable server-side diagnostic flight in a clear open area:

```text
/fpvdrone diagnostic start
/fpvdrone diagnostic start 24
/fpvdrone diagnostic record
/fpvdrone diagnostic record 24
/fpvdrone diagnostic status
/fpvdrone diagnostic stop
```

The optional `seconds` argument accepts `6..60`; the default is 16 seconds. `start` clears that drone's blackbox and runs the scripted profile. `record` does the same but automatically saves a CSV under `fpvdrone-blackbox` when the diagnostic finishes. The diagnostic temporarily overrides player stick packets with a deterministic Horizon-mode command profile. The profile uses the active airframe's `hoverThrottle()` plus a small altitude-hold outer loop, then runs takeoff, roll step, pitch step, yaw step, throttle punch, descent, settle, and disarm phases. The entity still moves through the normal 200 Hz physics path, so wind, air-mass inertia, gusts, wind shear, ambient temperature, ground effect, ceiling effect, obstacle turbulence, water immersion, rain wetness, propwash, vortex ring state, rotor blade stall, ESC slew, ESC command frame holding/quantization, active braking, battery sag/regeneration/bus spike, thermal limits, contact response/collisions, HUD sync, and blackbox telemetry are all exercised in-game rather than in a separate fake harness.

After the script finishes, use `/fpvdrone blackbox status` and `/fpvdrone blackbox save` to inspect or export the resulting CSV.

## Repeatable Wind Tunnel Conditions

For controlled tuning runs, the nearest bound drone can override selected parts of the Minecraft environment while still using the normal entity physics, obstacle wind shadow, ground effect, ceiling effect, and blackbox path:

```text
/fpvdrone environment status
/fpvdrone environment wind 6 0 -2
/fpvdrone environment turbulence 0.35
/fpvdrone environment density 0.85
/fpvdrone diagnostic record 20
/fpvdrone blackbox summary
/fpvdrone environment clear
```

`wind` sets a fixed world-space wind vector in meters per second before near-ground boundary-layer attenuation, near-obstacle wind shadow, and dirty-air turbulence are sampled. The physics core does not apply that vector as a perfectly rigid field: near the ground it reduces horizontal wind through a surface boundary-layer profile, filters the result through a finite-response air-mass model, then adds deterministic gust, wind-shear telemetry, and body disturbance torque from turbulence, ground-layer shear, obstacle proximity, nearby-drone wake, and ceiling effect. `turbulence` sets the baseline turbulence intensity from `0.0` to `1.5`; ground-layer shear, obstacle, nearby-drone wake, and near-ceiling turbulence are still added on top. `density` sets the air-density ratio from `0.35` to `1.35`, which changes thrust scaling, drag, induced inflow, and thermal cooling. Natural density is also shaped by altitude and ambient temperature sampled from biome baseline, weather, time of day, and height; rain/thunder wetness applies an additional hot-wet-air density correction before thrust, drag, induced inflow, dynamic-pressure disturbance, and cooling calculations use it. Minecraft water immersion is always sampled naturally from the drone body and rotor positions, while rain/thunder exposure is sampled from the drone's sky visibility and current weather to produce rain wetness; both remain active during wind/density override runs, so water-ingress and rain-wet behavior remain tied to the world. Rotor ground effect and ceiling effect are sampled across each prop disk instead of only at the rotor hub, so platform edges and partial ceiling proximity produce continuous per-rotor thrust multipliers. Each channel can be cleared independently with `wind clear`, `turbulence clear`, or `density clear`; `environment clear` restores natural Minecraft weather, altitude/temperature-based density, wet-air effective density, ground effect, ceiling effect, and obstacle airflow. The override is saved on the drone entity so repeated blackbox runs can be compared after reloading the world.

For a non-interactive Minecraft server smoke test, run the dev server with the self-test environment flag:

```powershell
.\gradlew.bat :fabric-mod:runServerSelfTest
```

The first server launch may create `fabric-mod/run/eula.txt` and stop before the world starts; review Mojang's EULA and set `eula=true` yourself before rerunning the smoke test. The self-test spawns a drone in the overworld, runs the same diagnostic command profile through the normal server entity tick path, writes `server-selftest-*.csv` and `server-selftest-*.json` under `fabric-mod/run/fpvdrone-selftest`, then stops the server. The Gradle task fails unless the current run produced a JSON report with `passed: true`, so an EULA stop or failed flight cannot be mistaken for a valid test. To change duration, use `.\gradlew.bat :fabric-mod:runServerSelfTest -PfpvdroneSelfTestSeconds=20`. To re-check an existing report, use `.\gradlew.bat :fabric-mod:validateServerSelfTestReport` or pass `-PfpvdroneSelfTestReport=C:\path\server-selftest.json`. The JSON and CSV validator checks sample count, per-row column alignment, required blackbox columns such as `physics_substeps`, `physics_dt_s`, `physics_rate_hz`, `flight_mode`, `control_frame_error`, `esc_command_error`, `pid_dterm_lpf_hz`, `pid_integral_relax_pitch`, `pid_integral_relax_yaw`, `pid_integral_relax_roll`, `gyro_blade_pass_notch_hz`, `gyro_blade_pass_notch_attenuation`, `motor_commutation_ripple`, `motor_phase_current_a`, `motor_current_ripple_a`, `motor_torque_ripple_nm`, `avg_motor_mechanical_loss_torque_nm`, `motor_electrical_efficiency`, `motor_voltage_headroom`, `avg_motor_target_rpm`, `avg_motor_tracking_error`, `avg_motor_actuator_authority`, `mixer_saturation`, `mixer_yaw_authority`, `mixer_min_axis_authority`, `mixer_low_saturation`, `mixer_high_saturation`, `mixer_low_headroom`, `mixer_high_headroom`, `battery_bus_ripple_v`, `battery_temp_c`, `battery_cooling_factor`, `battery_thermal_limit`, `tune_cg_x_m`, `tune_cg_z_m`, `tune_imu_x_m`, `tune_imu_z_m`, `tune_cp_x_m`, `tune_rotor_outward_cant_deg`, `tune_rotor_imbalance`, `rotor_advance_ratio`, `rotor_stall_intensity`, `rotor_coning`, `rotor_5_coning`, `rotor_arm_flex`, `rotor_surface_scrape`, `rotor_wake_interference`, `rotor_wake_swirl_mps`, `rotor_5_wake_swirl_mps`, `rotor_angular_drag_roll_torque_nm`, `contact_impact_mps`, `contact_slip_mps`, `contact_bounce_mps`, `contact_angular_impulse_dps`, `barometer_error_m`, `effective_wind_x_mps`, `wind_gust_speed_mps`, `wind_shear_accel_mps2`, `water_immersion`, `precipitation_wetness`, `effective_air_density_ratio`, `ambient_temperature_c`, `rotor_0_water_immersion`, `rotor_5_water_immersion`, `battery_regen_current_a`, `battery_voltage_spike_v`, `max_esc_temp_c`, `esc_thermal_limit`, `tune_esc_command_frame_rate_hz`, `tune_esc_command_resolution_steps`, `tune_rotor_blade_pitch_m`, `rotor_blade_aoa_deg`, `rotor_5_blade_element_stall`, `rotor_blade_dissymmetry`, `rotor_5_blade_dissymmetry`, `rotor_blade_pass_ripple`, `rotor_5_blade_pass_ripple`, `rotor_flapping_tilt_deg`, `rotor_5_flapping_tilt_deg`, and `tune_rotor_stall_loss`, non-finite values, climb/motion, motor power, battery current, and voltage sag.

## Gamepad / Radio Mapping

The client creates `config/fpvdrone-client.json` on first launch. The default mapping is:

```json
{
  "gamepadEnabled": true,
  "rollAxis": 0,
  "pitchAxis": 1,
  "yawAxis": 2,
  "throttleAxis": 3,
  "rollInverted": false,
  "pitchInverted": true,
  "yawInverted": false,
  "throttleInverted": true,
  "gamepadDeadband": 0.06,
  "cameraTiltDegrees": 25.0,
  "cameraForwardOffsetMeters": 0.16,
  "cameraUpOffsetMeters": 0.16,
  "cameraVibrationScale": 1.0,
  "cameraRollingShutterScale": 0.55,
  "cameraLatencySeconds": 0.035,
  "cameraFovDegrees": 105.0,
  "cameraDynamicFovDegrees": 6.0
}
```

Stick axes use a centered deadband and are remapped back to full `-1..1` authority. Throttle is treated as a travel axis and mapped to `0..1`, with a small endpoint snap so real radio idle and full throttle values feel stable. The FPV camera has a fixed airframe-relative tilt, mount offset, configurable wide FOV, optional speed/throttle FOV stretch, and a small buffered pose delay, so you can tune a low-angle cinewhoop view, a steeper racing-camera view, or an analog/HD video-link feel without changing the physics. Camera vibration is driven by synced motor RPM, rotor-damage vibration, and propwash telemetry; set `cameraVibrationScale` to `0.0` for a locked-off view or up to `2.0` for a rougher action-camera feel. `cameraRollingShutterScale` adds CMOS-style jello from high-RPM roughness, and `cameraLatencySeconds` is clamped to `0.0..0.20` seconds. Edit the file, then press `H` in game to reload it.

## Blackbox

The server records a five-minute rolling blackbox log for each drone at Minecraft tick rate. Use these commands after a flight:

```text
/fpvdrone blackbox status
/fpvdrone blackbox summary
/fpvdrone blackbox save
/fpvdrone blackbox clear
```

`summary` prints an in-game tuning digest with sample count, authoritative physics loop rate, max speed/airspeed, max contact impact/slip/bounce speed, max contact angular impulse, minimum voltage, max sag/current/regenerative-current/bus-spike/bus-ripple, battery temperature and thermal limit, minimum current-limit foldback, propwash, vortex-ring-state intensity, effective translational lift, rotor advance ratio, rotor aerodynamic load, motor mechanical-loss torque, actuator tracking error and authority, rotor inflow skew, same-frame rotor wake interference, peak rotor wake-swirl velocity, rotor angular-damping torque, airframe angular-drag torque, airframe lift/sideforce and separated-flow drag rise/buffeting, near-ground cushion drag, rotor-wash slipstream drag, rotor near-wall sidewash/cushion force, max barometer error, max barometer static-port disturbance, minimum barometer pressure, nearby-drone wake intensity, max local water immersion, max rain wetness, ambient temperature range, max gust speed, max wind-shear acceleration, ceiling-effect multiplier/clearance, environment thrust asymmetry, rotor side-flow blockage, rotor stall intensity, rotor vibration, rotor coning, rotor flapping tilt, rotor arm flex, rotor surface scrape, mixer saturation, split low/high mixer saturation/headroom, and minimum per-axis mixer authority, ESC command error/desync, motor temperature, minimum motor electrical efficiency and voltage headroom, ESC temperature and thermal limit, worst single-rotor health, prop-strike samples/max severity/count, altitude, link loss, receiver frame age/error, failsafe samples, and collision samples. `save` writes a CSV file under `fpvdrone-blackbox` in the server directory. The CSV includes physics substep count, physics step duration, physics rate, position, speed, contact impact/slip/bounce speed and angular impulse, true and estimated attitude, estimator error and accelerometer trust, true body rates, gyro-measured body rates, thermal/vibration-driven gyro bias, g-sensitive gyro error, and clipping, motor-fundamental and blade-pass dynamic gyro notch frequency/attenuation, true world acceleration, accelerometer specific force plus scale-factor/cross-axis error, bias, and clipping in the drone body frame, pressure-altitude barometer altitude/vertical-speed/pressure/error telemetry with propwash, ground-pressure, static-port dynamic-pressure, and rapid-rotation disturbance, raw player inputs, processed flight-controller commands, receiver frame age/interval/error telemetry, flight mode, link-loss/failsafe state, average motor output, ESC output command frame age/interval/error, ESC desync, ESC temperature/cooling/thermal-limit telemetry, per-motor output, per-motor RPM/target RPM/tracking error/actuator authority/angular acceleration/aerodynamic torque/mechanical-loss torque/shaft power/current/electrical-efficiency/voltage-headroom, motor temperature, local motor cooling factor, thermal thrust limiting, per-rotor thrust, induced inflow velocity, effective translational lift, rotor advance ratio, rotor tip Mach, rotor blade angle-of-attack, blade-element stall, blade lift-dissymmetry, rotor aerodynamic load, rotor surface scrape, rotor arm-flex intensity, rotor inflow-skew torque, same-frame rotor wake-interference intensity, crossflow-convected wake sweep, and wake-swirl velocity, rotor inertia/gyroscopic torque, rotor angular-drag torque, dynamic flapping tilt/force, elastic blade-coning intensity, healthy-prop rotor imbalance tuning and its resulting force/torque vector path, rotor stall intensity, rotor vibration, mixer saturation, split low/high mixer saturation and headroom, achieved mixer torque, per-axis mixer authority, PID setpoint/error, self-level target/error/blend telemetry, per-axis P/I/D/feedforward/output torque terms, PID attenuation, dynamic D-term low-pass cutoff, scalar and per-axis I-term relax, anti-gravity boost, battery voltage, open-circuit voltage, ohmic sag, transient sag recovery, active-braking regenerative current, bus voltage spike, bus ripple voltage, battery pack temperature/cooling/thermal-limit telemetry, state of charge, current draw, current-limit foldback, low-battery power limiting, frame health, average and per-rotor health, collision severity, prop-strike event rotor/severity/count/per-rotor severity pulses, weather wind, effective air-mass wind, gust speed, wind-shear acceleration, air-density ratio, wet-air effective density, ambient temperature, average and per-rotor water immersion, precipitation wetness, ground clearance, ground-effect multiplier, ceiling clearance, ceiling-effect multiplier, disk-averaged per-rotor environment thrust multipliers, per-rotor side-flow obstruction, propwash intensity, propwash wake retention, vortex-ring-state intensity, nearby-drone wake intensity, propwash torque, airframe aerodynamic torque, dynamic-pressure airframe angular-drag torque, airframe lift/sideforce, near-ground cushion drag, rotor-wash slipstream drag, rotor near-wall sidewash/cushion force, body-frame relative air velocity, airspeed, angle of attack, sideslip, turbulence intensity, obstacle proximity, and wind-turbulence torque.
It also records the active airframe, motor, battery, rotor, rotor-stall, rate, self-level, PID, feedforward, D-term filter, TPA, I-term relax, RC receiver, ESC command signal, and drag tuning values so a CSV can be interpreted later even after you change settings.

## Runtime Tuning

Use presets to switch the currently linked drone to a coherent airframe baseline:

```text
/fpvdrone preset list
/fpvdrone preset racing_quad
/fpvdrone preset cinewhoop
/fpvdrone preset heavy_lift
/fpvdrone preset hex_lift
/fpvdrone preset octo_lift
/fpvdrone preset coaxial_x8
```

`racing_quad` is the default fast 5-inch-style acro frame. `cinewhoop` is slower, draggier, and more protected-feeling. `heavy_lift` uses larger rotors, higher mass and inertia, slower motor response, and lower rates for stable camera or cargo-style flight. `hex_lift` is a six-rotor X/flat-hex lift frame that exercises the generic rotor-geometry mixer with three clockwise and three counter-clockwise rotors. `octo_lift` is an eight-rotor lift frame with heavier inertia, slower rates, wider collision footprint, and the full rotor 4..7 telemetry extension range active. `coaxial_x8` is a compact four-arm X8 with upper/lower counter-rotating prop pairs, so the lower disks fly in same-frame rotor wake and expose the wake-interference and wake-swirl model in normal gameplay. When a preset changes rotor count, the Fabric entity rebuilds its physics stack around the new airframe while carrying over position, velocity, attitude, and angular velocity. Preset name and tuning values are saved on the drone entity, then the saved preset is restored before tuning overrides are applied, so a saved multi-rotor drone reloads with the same airframe. Blackbox and offline CSV logs keep the legacy rotor 0..3 columns stable and append rotor 4..7 extension columns, including wake-interference and wake-swirl telemetry, for six- and eight-rotor tuning.

Use the tuning commands on your currently linked drone to refine a preset:

```text
/fpvdrone tune status
/fpvdrone tune reset
/fpvdrone tune set pitch_p 0.05
/fpvdrone tune set pitch_i 0.016
/fpvdrone tune set pitch_d 0.0008
/fpvdrone tune set feedforward 0.000018
/fpvdrone tune set dterm_lpf 90
/fpvdrone tune set anti_gravity 1.7
/fpvdrone tune set tpa_breakpoint 0.65
/fpvdrone tune set tpa_strength 0.22
/fpvdrone tune set iterm_relax 0.70
/fpvdrone tune set pitch_rate 720
/fpvdrone tune set pitch_expo 0.35
/fpvdrone tune set pitch_super_rate 0.45
/fpvdrone tune set yaw_rate 520
/fpvdrone tune set yaw_super_rate 0.20
/fpvdrone tune set roll_rate 720
/fpvdrone tune set roll_super_rate 0.45
/fpvdrone tune set mass_kg 1.10
/fpvdrone tune set inertia_x 0.012
/fpvdrone tune set cg_z 0.025
/fpvdrone tune set imu_z 0.030
/fpvdrone tune set cp_y 0.020
/fpvdrone tune set motor_tau 0.045
/fpvdrone tune set esc_curve 1.0
/fpvdrone tune set esc_slew 160
/fpvdrone tune set esc_down_slew 360
/fpvdrone tune set esc_deadband 0.018
/fpvdrone tune set motor_brake 0.55
/fpvdrone tune set voltage_compensation 0.85
/fpvdrone tune set esc_frame_rate 400
/fpvdrone tune set esc_resolution 2048
/fpvdrone tune set motor_heat_rate 12.0
/fpvdrone tune set motor_temp_limit 95
/fpvdrone tune set gyro_lpf 120
/fpvdrone tune set accel_lpf 80
/fpvdrone tune set accel_noise 0.22
/fpvdrone tune set attitude_accel_gain 1.8
/fpvdrone tune set attitude_accel_trust 4.0
/fpvdrone tune set control_latency 0.015
/fpvdrone tune set rc_smoothing 0.018
/fpvdrone tune set rc_latency 0.018
/fpvdrone tune set rc_failsafe 0.35
/fpvdrone tune set rc_frame_rate 150
/fpvdrone tune set rc_resolution 2048
/fpvdrone tune set battery_resistance 0.018
/fpvdrone tune set battery_capacity_ah 1.5
/fpvdrone tune set linear_drag 0.18
/fpvdrone tune set body_drag_z 0.20
/fpvdrone tune set rotor_max_thrust 13.5
/fpvdrone tune set rotor_thrust_coefficient 0.000018
/fpvdrone tune set rotor_radius 0.0635
/fpvdrone tune set rotor_blade_pitch 0.108
/fpvdrone tune set rotor_transverse_lift 0.08
/fpvdrone tune set rotor_axial_loss 0.16
/fpvdrone tune set rotor_disk_drag 0.0028
/fpvdrone tune set rotor_flapping 0.055
/fpvdrone tune set rotor_stall_loss 0.34
/fpvdrone tune set rotor_yaw_torque 0.018
/fpvdrone tune set rotor_outward_cant 0
/fpvdrone tune set rotor_inertia 0.000016
/fpvdrone tune set rotor_inflow_tau 0.035
/fpvdrone tune set rotor_inflow_lag 0.16
/fpvdrone tune set ground_effect_height 0.6
/fpvdrone tune set ground_effect_boost 0.18
/fpvdrone tune set propwash_start 2.2
/fpvdrone tune set propwash_full 7.5
/fpvdrone tune set propwash_torque 0.035
/fpvdrone tune set motor_idle 0.055
/fpvdrone tune set airmode_strength 1.0
```

`rotor_imbalance` models healthy-prop 1x RPM imbalance, while rotor health damage is folded into the same effective imbalance path for bent-prop force, mechanical loss, commutation ripple, current ripple, and vibration.

Available PID keys are `pitch_p`, `pitch_i`, `pitch_d`, `pitch_limit`, `yaw_p`, `yaw_i`, `yaw_d`, `yaw_limit`, `roll_p`, `roll_i`, `roll_d`, and `roll_limit`. Flight-controller assist keys are `feedforward`, `dterm_lpf`, `anti_gravity`, `tpa_breakpoint`, `tpa_strength`, and `iterm_relax`; they apply to all three axes. The D term is applied to gyro measurement derivative rather than target-rate error derivative, so sharp stick setpoint changes do not create D-kick; `feedforward` handles intentional setpoint acceleration instead. The configured `dterm_lpf` is the dynamic filter's high-throttle ceiling: low throttle and low motor RPM lower the effective cutoff, while rotor vibration, blade-pass roughness, rotor stall, and vortex-ring-state buffet add extra foldback. `iterm_relax` ranges from `0.0` to `1.0` and reduces each axis' integrator accumulation during that axis' fast setpoint changes or measured mixer authority loss. Rate values are degrees per second. Expo keys are `pitch_expo`, `yaw_expo`, and `roll_expo`, from `0.0` to `1.0`; higher values soften mid-stick control. Super-rate keys are `pitch_super_rate`, `yaw_super_rate`, and `roll_super_rate`, from `0.0` to `0.95`; they reshape mid-stick response while preserving full-stick maximum rate. Self-level keys are `level_angle` in degrees, `level_gain`, `horizon_start`, and `horizon_end`; they control Angle-mode maximum tilt and the Horizon-mode transition from self-level to Acro authority. Airframe keys are `mass_kg`, `inertia_x`, `inertia_y`, `inertia_z`, `cg_x`, `cg_y`, `cg_z`, `imu_x`, `imu_y`, `imu_z`, `cp_x`, `cp_y`, `cp_z`, and `angular_drag`; the `cg_*` values are meters in body axes and shift the simulated center of mass used by the mixer, rotor local airflow, and force-arm torque, `imu_*` values place the flight-controller sensor package relative to the center of mass so accelerometer specific force includes angular and centripetal lever-arm acceleration, and `cp_*` values place the baseline airframe center of pressure before angle-of-attack, sideslip, and separated-flow migration, so body drag/lift can produce an additional force-arm torque. Motor and battery keys are `motor_tau`, `esc_curve`, `esc_slew`, `esc_down_slew`, `esc_deadband`, `motor_brake`, `voltage_compensation`, `esc_frame_rate`, `esc_resolution`, `motor_heat_rate`, `motor_cooling_rate`, `motor_temp_limit`, `motor_temp_cutoff`, `battery_nominal_voltage`, `battery_empty_voltage`, `battery_resistance`, `battery_capacity_ah`, and `battery_max_current`; `motor_tau` is the baseline response time and is dynamically lengthened by low voltage, power limiting, high rotor inertia, high prop aerodynamic load, and back-EMF saturation near the inferred KV no-load speed. `battery_nominal_voltage`, `battery_max_current`, rotor max RPM, and rotor inertia are also used to infer motor KV, torque constant, and winding resistance for torque-limited spin-up. `esc_slew` limits command rise, `esc_down_slew` limits command fall, `esc_deadband` suppresses tiny commands, `motor_brake` controls active braking strength during spin-down, `esc_frame_rate` controls how often the ESC accepts a new output command, `esc_resolution` controls command quantization steps, and `battery_max_current` scales load current and drives dynamic over-current thrust foldback. Set ESC frame rate or resolution to `0` to disable that part of the ESC signal model for idealized tests. Flight-controller sensor keys are `gyro_lpf` in Hz, `gyro_noise` in rad/s, `accel_lpf` in Hz, `accel_noise` in m/s^2, and `control_latency` in seconds. Attitude-estimator keys are `attitude_accel_gain` for complementary accelerometer correction strength and `attitude_accel_trust` in m/s^2 for how far measured specific force may drift from gravity before correction is faded out. RC link keys are `rc_smoothing` in seconds, `rc_latency` in seconds, `rc_failsafe` in seconds before a lost link cuts to idle, `rc_frame_rate` in Hz for the receiver command-frame rate, and `rc_resolution` for channel quantization steps; set frame rate or resolution to `0` to disable that part of the receiver model for idealized tests. Drag keys are `linear_drag`, `body_drag_x`, `body_drag_y`, `body_drag_z`, and `rotor_disk_drag`; body drag also scales airframe pitch/yaw/roll moments from angle of attack and sideslip, while `rotor_disk_drag` also damps body angular rates through the spinning prop disk. Rotor keys are `rotor_max_thrust`, `rotor_thrust_coefficient`, `rotor_radius`, `rotor_transverse_lift`, `rotor_axial_loss`, `rotor_flapping` for transverse-flow thrust-vector tilt, `rotor_stall_loss` from `0.0` to `0.65` for high-advance-ratio and reverse-axial-flow thrust loss, `rotor_imbalance` from `0.0` to `0.35` for healthy-prop 1x RPM vibration and current ripple, `rotor_yaw_torque` in meters of reaction-torque leverage along the current flapped disk axis, `rotor_outward_cant` in degrees for symmetric inward/outward motor tilt, `rotor_inertia` in kg*m^2 for spin-up reaction torque and gyroscopic coupling along the same disk axis, `rotor_inflow_tau` in seconds, and `rotor_inflow_lag` as a transient thrust-loss coefficient. Ground-effect keys are `ground_effect_height` in meters and `ground_effect_boost` from `0.0` to `0.6`. Propwash keys are `propwash_start` and `propwash_full` in meters per second of descent, plus `propwash_torque` in N-m. Mixer keys are `motor_idle` as a rotor max-thrust fraction and `airmode_strength` from `0.0` to `1.0`. Tuning is saved on the drone entity.

`rotor_blade_pitch` is the prop pitch distance in meters; raising it increases pitch-speed margin and prop load, while lowering it makes the prop unload sooner in high axial climb flow.

Rotor advance ratio, flapping, blade stall, induced inflow, disk drag, same-frame wake checks, and spring-damped arm-flex feedback use each rotor's current axis, so canted motors and flexed motor mounts change both force direction and local airflow.

The core model includes:

- body-frame quadratic drag plus global drag, with angle-of-attack/sideslip aerodynamic lift, sideforce, dynamic separated-flow build-up/recovery, angle-dependent pressure-center migration, near-ground cushion drag, finite-response projected-area rotor-wash slipstream drag plus pressure-center moment, and moments;
- relative-air drag, rotor-induced slipstream drag over the airframe, dynamic-pressure, separated-flow, body-rotation, and finite-response rotor-wash-enhanced airframe angular damping, deterministic weather wind, near-ground boundary-layer wind attenuation and shear, finite-response air-mass inertia, deterministic gust vectors, wind-shear acceleration telemetry, wind-speed/weather/ground-proximity turbulence torque, near-obstacle wind shadow and dirty-air turbulence, standard-atmosphere altitude/temperature air density and barometric pressure, rain/thunder wet-air effective-density correction for thrust/drag/induced-flow/cooling, ambient temperature effects on battery sag/current limiting and motor/ESC cooling, Minecraft water immersion sampled from the body and each rotor disk, water drag plus per-rotor air-prop thrust/load/vibration/desync effects during local water ingress, precipitation wetness sampled from exposed rain/thunder that adds wet-prop load, vibration, mild thrust loss, turbulence, and ESC risk without water-drag force, disk-averaged per-rotor ground-effect thrust boost plus low-altitude lateral cushion drag, disk-averaged per-rotor near-ceiling suction/thrust boost with added turbulence, finite-response per-rotor surface-pressure lag for ground/ceiling effect build-up and release, per-rotor side-flow obstruction near walls/gates before prop strike, rotor near-wall sidewash/cushion force from obstruction direction and disk pressure, obstruction-induced rotor vibration that feeds gyro/accelerometer noise and dynamic notch telemetry, and body-frame airspeed/angle-of-attack/sideslip telemetry;
- per-rotor local airflow from body velocity and angular velocity, including effective translational lift in clean transverse flow, reduced induced inflow at speed, airflow-dependent rotor aerodynamic load/unload for motor current and heat, ambient dirty-air load roughness for turbulence/ground-shear/obstacle wake, zero-throttle axial windmilling RPM with prop-disk drag during fast descents, tunable prop blade pitch with blade-element angle-of-attack telemetry, pitch-speed axial unloading, blade-element stall load/vibration, advancing/retreating blade lift-dissymmetry load/vibration/thrust loss, small two-blade blade-pass thrust ripple that grows under load, stall, dirty air, obstruction, or prop scrape, high-advance blade-stall low-frequency thrust and side-force buffeting, dynamic rotor-stall hysteresis with slower attached-flow recovery, finite-response blade coning thrust loss/load/vibration, low-Reynolds small-prop efficiency loss from density and temperature-dependent air viscosity, and pitch-dependent motor load, temperature-dependent rotor tip Mach telemetry with high-speed compressibility thrust loss/load/vibration, inflow-skew hub torque from high-speed disk-plane flow, transverse-flow lift, rotor-disk drag, spinning-disk angular damping, first-order dynamic transverse-flow flapping tilt/force, reaction torque and rotor-inertia torque aligned to the flapped disk axis, high-advance-ratio/reverse-axial-flow blade stall, descending axial-flow thrust loss, and vortex-ring/washout thrust loss plus low-frequency per-rotor thrust and side-force buffeting when the drone descends into retained rotor wake;
- per-rotor dynamic induced inflow, so each prop disk has a finite wake build-up time during throttle punches and rapid unloading;
- rotor-axis-aware retained propwash wake memory, so low-throttle descents along the prop-disk axes can build dirty wake that hits harder on the next punch-out while disk-plane crossflow flushes it away;
- same-frame rotor wake interference and wake-swirl velocity for overlapping or vertically stacked rotors, reducing lower-disk thrust while adding load, vibration, and local tangential inflow telemetry;
- spring-damped arm and motor-mount flex resonance driven by per-rotor force/torque transients, with short throttle-chop ring-down feeding rotor vibration, IMU noise, dynamic notch telemetry, blackbox/offline CSV traces, and a small load-dependent rotor force-arm/thrust-axis deflection;
- deterministic propwash disturbance torque during retained high-throttle descent through the drone's own wake;
- nearby-drone downwash and wake turbulence, so a drone flying under another active drone receives downward local wind, added turbulence, and dedicated wake telemetry;
- tunable airframe mass, inertia, center-of-mass offset, IMU mounting offset, center-of-pressure offset, ESC response, motor response, battery sag, rotor thrust, and rotor aerodynamic coefficients, with per-motor RPM telemetry for filter and powertrain tuning;
- motor spool-up lag with runtime load/voltage/inertia/back-EMF-headroom/hot-winding-resistance-dependent response, inferred KV/Kt/winding-resistance torque limits, temperature-adjusted copper resistance and torque authority, low-RPM static breakaway/cogging torque with cold-bearing viscosity drag, active braking spin-down, temperature-sensitive bearing friction, windage, healthy-prop and damage-driven imbalance with spinning lateral force injection, wet-prop drag, prop-scrape mechanical-loss torque, ESC output curve, command deadband, separate rise/fall slew limiting, dynamic voltage compensation, ESC command-frame rate/resolution signal modeling with per-motor sub-frame phase staggering, and BLDC commutation/phase-current ripple driven by duty cycle, load, voltage headroom, desync stress, rotor imbalance, and rotor speed, with torque ripple fed back into the rotor reaction-torque path;
- deterministic ESC desync/stutter under dirty airflow, water ingress, rain wetness, rotor stall, high load, low voltage, low motor voltage headroom, battery rail ripple/active-braking overvoltage spikes, or motor/ESC thermal stress, with RPM drop, current spike, HUD/status warnings, and blackbox telemetry;
- rotor rotational inertia, motor acceleration reaction torque, prop aerodynamic shaft torque, mechanical-loss shaft torque, inertial spin-up shaft power/current load, rotor gyroscopic coupling, spinning-prop angular drag torque, and blackbox telemetry for the resulting body torque, motor angular acceleration, mechanical loss, and motor shaft power;
- motor and ESC thermal rise, MOSFET-style current/switching/braking/desync heat, per-motor local airflow/RPM/air-density/obstruction cooling with recirculated dirty-air cooling loss near ground, walls, ceilings, and wake, ESC cooling-factor telemetry, and heat-based thrust limiting;
- gyro and accelerometer low-pass filtering, thermal/vibration-driven MEMS bias drift, gyro specific-force sensitivity, accelerometer high-g scale/cross-axis error, realistic full-scale gyro/accelerometer clipping, separate motor-fundamental and blade-pass dynamic notch-style gyro attenuation, deterministic vibration noise, IMU-mount specific-force sensing with angular and centripetal lever-arm acceleration, pressure-altitude barometer sensing with low-pass lag plus propwash, ground-pressure, static-port dynamic-pressure, rapid-rotation, and battery-bus-ripple supply-noise error, configurable control-loop latency, receiver-side control-frame rate/quantization telemetry, and ESC command signal age/error telemetry;
- complementary IMU attitude estimation from gyro integration and accelerometer gravity correction, with correction trust reduced during high specific-force maneuvers, rotor vibration, or accelerometer clipping;
- RC receiver frame holding, channel quantization, command latency, stick smoothing, short link-loss hold, failsafe disarm, and raw-vs-processed command telemetry;
- armed motor idle, coupled least-squares mixer allocation across pitch/yaw/roll that preserves collective thrust on canted/asymmetric rotor layouts, airmode-style mixer desaturation, plus mixer saturation, split low/high saturation and headroom, achieved torque, and per-axis control-authority telemetry;
- per-motor RPM/angular acceleration/thrust/airflow-load/mechanical-loss torque/commutation torque ripple, shaft-power and back-EMF-based current draw with phase-current/current-ripple/RPM/load/thermal/desync electrical-efficiency telemetry and battery-voltage headroom telemetry, battery state-of-charge, pack heat capacity with current/ripple I2R heating and airflow/water cooling, temperature-dependent internal resistance and current capability, instantaneous internal-resistance voltage sag, slower transient LiPo sag/recovery, active-braking regenerative current, battery bus voltage spike that feeds ESC rail-stability stress, current-ripple-driven battery bus ripple with ESC rail-stability stress and mild IMU supply-noise coupling, low-battery and battery-thermal power limiting, and dynamic over-current power limiting;
- blocked-axis contact response with separate impact, slip, bounce, and angular-impulse telemetry feeding tumble response plus crash-induced frame and rotor efficiency damage;
- per-rotor prop-strike checks against Minecraft block collision, so a spinning prop clipping an obstacle can damage only that corner before full-frame impact;
- Minecraft contact materials for block-aware friction, rebound, and prop-scrape severity on ice, slime, honey, sand, mud, soul-sand, and cactus-style surfaces;
- decaying prop-surface scrape loads from sustained disk contact, feeding per-rotor load, motor RPM drag, ESC desync risk, current draw, vibration, HUD/status warnings, and blackbox telemetry;
- rotor damage produces deterministic high-frequency gyro and accelerometer vibration plus effective bent-prop imbalance, so damaged props inject rotating force, commutation/current ripple, control feel changes, and blackbox traces, with motor-fundamental and blade-pass notch frequency/attenuation telemetry for filter tuning;
- per-axis rate/expo/super-rate input curves that preserve configured full-stick maximum rate;
- Acro/rate-mode PID control with dynamic D-term low-pass filtering, target-rate feedforward, anti-gravity I-term boost on throttle punches, throttle PID attenuation (TPA), IMU clipping-aware PID authority foldback, and per-axis I-term relax/anti-windup during rapid setpoint changes or measured mixer authority loss;
- tunable Angle and Horizon self-level modes that convert attitude error into rate setpoints while still reusing the same PID, mixer, ESC, motor, and airframe physics path as Acro;
- blackbox-style rate setpoint/error and per-axis P/I/D/feedforward/output torque telemetry, with gyro-derivative D-term damping and setpoint feedforward separated for tuning;
- geometry-derived multirotor mixer using each rotor's force arm and actual thrust axis relative to the configured center of mass, plus configurable rotor outward cant and airframe center-of-pressure moment arms for drag/lift torque, currently exposed through quad, cinewhoop, heavy-lift quad, six-rotor hex-lift, eight-rotor octo-lift, and compact coaxial-X8 presets, with per-rotor thrust and yaw reaction torque.
