# Rotor Physics Research Notes

Date: 2026-06-11

This note is for the agent working on the rotor simulator. It collects measured
or openly published multirotor/propeller data and translates it into the units
used by this project.

## Executive summary

The most important finding is that the current preset `thrustCoefficient`
values look too large if `omega` is meant to be a real rotor angular velocity in
`rad/s`.

The project currently computes rotor thrust roughly as:

```text
T = kT * omega^2 * densityRatio * modifiers
```

and `RotorSpec.maxOmegaRadiansPerSecond()` is derived from:

```text
maxOmega = sqrt(maxThrust / kT)
```

That coupling makes `kT` do two jobs at once: it sets the physical propeller
coefficient and also indirectly sets the maximum RPM. With current values, a
5-inch racing quad reaches only about 8,270 RPM at 13.5 N per rotor. Public prop
data suggests that the same thrust level on a 5-inch prop is closer to
26,000-32,000 RPM, depending on the prop and motor.

Recommended direction:

1. Treat `thrustCoefficient` as a real aerodynamic coefficient in
   `N/(rad/s)^2`.
2. Add an explicit rotor `maxOmegaRadiansPerSecond` or `maxRpm` field instead of
   deriving it from `maxThrust / kT`.
3. Use measured prop data for `kT`, then set `maxThrust` from the chosen motor,
   battery, ESC, and maximum RPM.
4. Keep `yawTorquePerThrustMeter` close to measured `Q/T`. Most 5-12 inch prop
   sources below land around `0.012-0.016 m`; current heavy presets use
   `0.023-0.030 m`, which is probably high unless intentionally modeling a very
   inefficient or high-pitch system.
5. Keep `DroneEnvironment.effectiveAirDensityRatio()` dimensionless. Do not put
   absolute air density there; the conversion from prop coefficients should use
   a reference density, normally `rho = 1.225 kg/m^3`.

## Unit conversions

UIUC and APC propeller data use nondimensional coefficients:

```text
J  = V / (n * D)
Ct = T / (rho * n^2 * D^4)
Cp = P / (rho * n^3 * D^5)
```

where:

- `V` is airspeed in `m/s`
- `n` is revolutions per second
- `D` is propeller diameter in meters
- `rho` is air density in `kg/m^3`
- `T` is thrust in newtons
- `P` is shaft power in watts

This project uses angular velocity in `rad/s`, so:

```text
omega = rpm * 2*pi / 60
kT_project = Ct * rho * D^4 / (4*pi^2)
yawTorquePerThrustMeter = Q / T = (Cp / Ct) * D / (2*pi)
```

Do not paste a coefficient reported in `N/RPM^2` directly into
`RotorSpec.thrustCoefficient`. Convert it first:

```text
kT_rad = kT_rpm * (60 / (2*pi))^2
```

## Public data sources

### UIUC Propeller Data Site

Source:

- https://m-selig.ae.illinois.edu/props/propDB.html
- https://m-selig.ae.illinois.edu/props/download/UIUC-propDB.zip

The UIUC database is the best first source for measured small-prop data. It
contains static and wind-tunnel measurements, including `Ct`, `Cp`, advance
ratio, RPM, thrust, power, and efficiency.

Useful static examples converted to this project's units:

| Source | Prop | Diameter | RPM range | Avg Ct | Avg Cp | kT, N/(rad/s)^2 | Q/T, m | Max measured thrust |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| UIUC | GWS DD 3x3 | 3.0 in | 2,963-14,920 | 0.19204 | 0.14302 | 2.009e-7 | 0.00903 | 0.49 N |
| UIUC | DA4022 5x3.75 | 5.0 in | 2,077-7,323 | 0.15111 | 0.09724 | 1.220e-6 | 0.01301 | 0.72 N |
| UIUC | DA4052 5x4.92 | 5.0 in | 1,527-7,940 | 0.14006 | 0.10962 | 1.131e-6 | 0.01582 | 0.78 N |
| UIUC | APC E 8x4 | 8.0 in | 3,037-6,989 | 0.09382 | 0.03960 | 4.963e-6 | 0.01365 | 2.66 N |
| UIUC | APC E 9x4.5 | 9.0 in | 2,499-6,922 | 0.09677 | 0.03873 | 8.200e-6 | 0.01456 | 4.31 N |
| UIUC | APC E 10x5 | 10.0 in | 2,508-6,708 | 0.09573 | 0.03710 | 1.236e-5 | 0.01567 | 6.10 N |
| UIUC | APC E 12x6 | 12.0 in | 1,047-7,547 | 0.09817 | 0.03263 | 2.629e-5 | 0.01612 | 16.42 N |

Notes:

- UIUC is measured data, but many files are at lower RPM than modern FPV racing
  props. Use it for coefficient sanity and wind-speed behavior.
- For high-RPM modern setups, combine UIUC coefficient ranges with a motor/prop
  test database such as Tyto Robotics.

### APC official performance files

Source:

- https://www.apcprop.com/technical-information/performance-data/
- https://www.apcprop.com/files/PER3_5x45E.dat
- https://www.apcprop.com/files/PER3_8x45MR.dat
- https://www.apcprop.com/files/PER3_9x45MR.dat
- https://www.apcprop.com/files/PER3_10x45MR.dat
- https://www.apcprop.com/files/PER3_12x45MR.dat

APC's files are generated from prop geometry using APC's own vortex-theory
software, so they are not the same as measured thrust-stand data. They are still
useful because they cover the high-RPM envelope of modern multirotor props.

Static rows converted to project units:

| Source | Prop | Diameter | RPM range | Avg Ct | Avg Cp | kT, N/(rad/s)^2 | Q/T, m | Max file thrust | Tip Mach at max |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| APC sim | APC 5x4.5E | 5.0 in | 1,000-38,000 | 0.19443 | 0.11371 | 1.569e-6 | 0.01182 | 25.60 N | 0.75 |
| APC sim | APC 6x4.5E | 6.0 in | 1,000-27,000 | 0.18404 | 0.09445 | 3.081e-6 | 0.01245 | 25.41 N | 0.64 |
| APC sim | APC 8x4.5MR | 8.0 in | 1,000-26,000 | 0.12645 | 0.05151 | 6.689e-6 | 0.01317 | 52.46 N | 0.83 |
| APC sim | APC 9x4.5MR | 9.0 in | 1,000-23,000 | 0.11824 | 0.04547 | 1.002e-5 | 0.01399 | 61.89 N | 0.82 |
| APC sim | APC 10x4.5MR | 10.0 in | 1,000-22,000 | 0.11062 | 0.04054 | 1.429e-5 | 0.01482 | 81.87 N | 0.87 |
| APC sim | APC 12x4.5MR | 12.0 in | 1,000-19,000 | 0.09369 | 0.03133 | 2.509e-5 | 0.01622 | 108.62 N | 0.90 |

### RotorS open-source simulator

Source:

- https://github.com/ethz-asl/rotors_simulator
- https://raw.githubusercontent.com/ethz-asl/rotors_simulator/master/rotors_description/urdf/firefly.xacro
- https://raw.githubusercontent.com/ethz-asl/rotors_simulator/master/rotors_description/urdf/hummingbird.xacro
- https://raw.githubusercontent.com/ethz-asl/rotors_simulator/master/rotors_description/urdf/pelican.xacro

RotorS uses the same basic model shape as this project:

```text
thrust = motor_constant * omega^2
reaction_moment = moment_constant * thrust
```

Open-source platform parameters:

| Platform | Mass | Arm length | Rotor radius | kT, N/(rad/s)^2 | Moment constant | Motor time constants |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Firefly hex | 1.5 kg | 0.215 m | 0.100 m | 8.54858e-6 | 0.016 m | up 0.0125 s, down 0.025 s |
| Hummingbird quad | 0.68 kg | 0.170 m | 0.100 m | 8.54858e-6 | 0.016 m | up 0.0125 s, down 0.025 s |
| Pelican quad | 1.0 kg | 0.210 m | 0.128 m | 9.9865e-6 | 0.016 m | up 0.0125 s, down 0.025 s |

These coefficients line up with 8-10 inch prop data much better than the
current 5-inch racing preset does.

### ZJU FAST-Lab ground-effect controller

Source:

- https://github.com/ZJU-FAST-Lab/Ground-effect-controller
- https://arxiv.org/abs/2506.19424

This is useful because it publishes a fitted thrust model for a 7-inch quad and
includes experimental data for ground-effect work.

Published parameters:

| Parameter | Value |
| --- | ---: |
| Mass | 0.701 kg |
| Arm length | 0.1778 m |
| Inertia | diag(3.44, 4.27, 6.37)e-3 kg*m^2 |
| Rotor inertia | 1.0556e-4 kg*m^2 |
| kT | 4.0083e-8 N/RPM^2 |
| kT converted to project units | 3.655e-6 N/(rad/s)^2 |
| Translational drag | diag(0.79, 0.79, 0.00) |
| Angular drag | diag(6, 6, 10)e-3 |

This source is especially relevant if improving the project's ground-effect,
wake, or near-surface thrust model.

### Bitcraze Crazyflie thrust data

Source:

- https://www.bitcraze.io/documentation/repository/crazyflie-firmware/master/functional-areas/pwm-to-thrust/

This is micro-UAV data, so it should not be used to tune 5-inch or 10-inch
props directly. It is still useful for checking that the simulator handles
quadratic RPM-to-thrust behavior and battery-voltage effects.

Example measured points from Bitcraze's thrust stand:

| PWM | Voltage | Current | RPM | Thrust |
| ---: | ---: | ---: | ---: | ---: |
| 50.00% | 3.71 V | 1.83 A | 15,924 | 24.4 g |
| 75.00% | 3.56 V | 3.06 A | 20,539 | 41.7 g |
| 93.75% | 3.30 V | 4.44 A | 23,882 | 57.9 g |

The documented leading coefficient of the RPM-to-thrust polynomial is
approximately `1.0942e-7 g/RPM^2`, which converts to about
`9.78e-8 N/(rad/s)^2`.

### Tyto Robotics public database

Source:

- https://database.tytorobotics.com/
- https://database.tytorobotics.com/tests

Tyto's public database is the best next place to get full motor+prop+ESC
thrust-stand curves. The site exposes fields such as throttle, RPM, thrust,
torque, voltage, current, electrical power, mechanical power, motor efficiency,
propeller efficiency, and overall efficiency.

Use Tyto for propulsion-system fitting:

- fit `thrust = f(rpm, voltage, throttle)` for a specific motor/prop/ESC
- fit motor torque and electrical power, not only prop thrust
- fit battery sag and current draw under load
- compare static stepped tests only; avoid mixing dynamic, burst, thermal, and
  endurance tests into the same coefficient fit

## Current preset audit

Approximate comparison against the public sources above:

| Preset | Current diameter | Current max thrust | Current kT | Reference kT | Ratio | Current max RPM | RPM for same thrust with reference kT | Current Q/T | Suggested Q/T source |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| `racingQuad` | 5.0 in | 13.5 N | 1.800e-5 | 1.569e-6 | 11.5x | 8,270 | 28,011 | 0.018 | APC 5x4.5E |
| `cinewhoop` | 3.0 in | 8.0 N | 1.150e-5 | 2.009e-7 | 57.2x | 7,965 | 60,260 | 0.013 | UIUC GWS DD 3x3 |
| `heavyLift` | 10.0 in | 38.0 N | 4.500e-5 | 1.429e-5 | 3.1x | 8,775 | 15,572 | 0.030 | APC 10x4.5MR |
| `hexLift` | 8.27 in | 26.0 N | 3.600e-5 | 7.639e-6 | 4.7x | 8,115 | 17,618 | 0.024 | APC 8x4.5MR scaled |
| `octoLift` / `coaxialX8` | 9.06 in | 24.0 N | 3.200e-5 | 1.029e-5 | 3.1x | 8,270 | 14,584 | 0.023 | APC 9x4.5MR scaled |

Interpretation:

- If the simulator wants plausible real RPM telemetry, current `kT` values
  should be reduced substantially.
- Reducing `kT` without adding explicit `maxRpm` will also increase the derived
  max RPM. That is physically desirable, but it may change controller tuning and
  motor response.
- The current presets may have been chosen to get the desired thrust at a
  convenient internal RPM. If so, the simulator should rename that internal value
  or explicitly separate "normalized motor speed" from physical RPM.

## Suggested preset targets

These are not final truth; they are practical ranges for the next calibration
pass.

| Preset family | Suggested kT, N/(rad/s)^2 | Suggested max RPM | Suggested Q/T |
| --- | ---: | ---: | ---: |
| 5-inch racing quad | 1.3e-6 to 1.8e-6 | 26,000-32,000 | 0.012-0.016 m |
| 3-inch cinewhoop | 2.0e-7 to 5.0e-7 without duct model; higher only if ducted and fitted | 35,000-60,000 | 0.009-0.013 m |
| 7-inch quad | 3.5e-6 to 5.0e-6 | 18,000-26,000 | 0.012-0.016 m |
| 8-inch lift / utility | 6.0e-6 to 8.0e-6 | 15,000-22,000 | 0.013-0.016 m |
| 9-inch lift / utility | 9.0e-6 to 1.1e-5 | 13,000-18,000 | 0.014-0.016 m |
| 10-inch heavy lift | 1.2e-5 to 1.6e-5 | 12,000-17,000 | 0.015-0.017 m |
| 12-inch heavy lift | 2.4e-5 to 2.8e-5 | 9,000-14,000 | 0.015-0.018 m |

## Validation checks to add

1. Add a unit test that computes hover RPM:

   ```text
   hoverOmega = sqrt((mass * g / rotorCount) / kT)
   ```

   For a 1.1 kg 5-inch quad with `kT = 1.569e-6`, hover is roughly
   `12,500 RPM`. With the current `1.8e-5`, hover is only about `3,700 RPM`.

2. Add a unit test or startup warning for impossible max RPM:

   ```text
   maxRpm = sqrt(maxThrust / kT) * 60 / (2*pi)
   ```

   A 5-inch racing rotor producing more than `10 N` should not max out below
   about `20,000 RPM`.

3. Validate tip Mach in offline CSV:

   - 5-inch at about `28,000 RPM`: tip Mach around `0.54`
   - 5-inch at about `38,000 RPM`: tip Mach around `0.75`

   If full throttle reports a 5-inch tip Mach near `0.15-0.20`, the RPM model is
   likely not physical.

4. Validate `yawTorquePerThrustMeter` by comparing `Q/T` against the tables
   above. Values around `0.012-0.016 m` are a good default for many 5-12 inch
   electric props.

5. Use UIUC/APC to validate prop-only aerodynamics. Use Tyto or an equivalent
   thrust-stand dataset to validate motor, ESC, current, voltage sag, and
   thermal-limited maximum thrust.

6. Use the ZJU ground-effect dataset before changing ground effect constants.
   Ground effect should be calibrated separately from free-air `kT`; otherwise
   the same coefficient may be forced to hide two different physical effects.

## Implementation notes for this project

Relevant files:

- `drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/RotorSpec.java`
- `drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DroneConfig.java`
- `drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DronePhysics.java`
- `drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DroneEnvironment.java`

Concrete next patch sequence:

1. Add optional explicit `maxOmegaRadiansPerSecond` or `maxRpm` to `RotorSpec`.
2. Change `maxOmegaRadiansPerSecond()` to return the explicit value when present;
   otherwise keep the current derived behavior for backward compatibility.
3. Refit `DroneConfig` presets with physical `kT` and explicit max RPM.
4. Recompute `maxThrustNewtons` from the fitted `kT` and max RPM, or choose
   max RPM from motor data and let the resulting thrust be the preset's real max.
5. Add validation tests for hover RPM, max RPM, tip Mach, and `Q/T`.
6. Keep old behavior available behind a compatibility preset if the current
   flight feel depends on the nonphysical coefficients.

The most important design choice is to stop deriving physical RPM from a
coefficient that was probably tuned for gameplay feel. Once `kT`, max RPM, and
max thrust are separated, the simulator can be both fun and physically
traceable.
