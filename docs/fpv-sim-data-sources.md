# FPV simulation data sources

Date: 2026-06-11

This note is a source and data packet for the coding agent working on the FPV physics. It does not propose code changes. It lists open data, papers, and open-source simulators that can support or challenge the current models in `drone-sim-core`.

## Current model fields that need data

Relevant local files:

- `drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DroneConfig.java`
- `drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/RotorSpec.java`
- `drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DronePhysics.java`

Key tunables exposed by the current code:

- Airframe: `massKg`, `inertiaKgMetersSquared`, CG/IMU/CP offsets, body drag coefficients.
- Rotor/prop: `maxThrustNewtons`, `thrustCoefficient`, `yawTorquePerThrustMeter`, `radiusMeters`, `bladePitchMeters`, transverse-flow lift, axial-flow loss, disk drag, rotor inertia, induced inflow lag/tau, flapping, stall loss, imbalance.
- Motor/ESC: motor time constants, ESC curve/deadband/slew, active braking, commutation ripple, desync, voltage headroom.
- Battery: nominal/empty voltage, internal resistance, capacity, max current, OCV/SOC, sag, transient sag, regen spike, bus ripple, thermal derating.
- Sensors/control: gyro/accelerometer LPF/noise/clipping, barometer dynamics, RC/ESC frame rates and resolution, Betaflight-like rate/expo/super-rate/PID features.
- Environment/aero effects: ground effect, propwash/vortex ring state, low Reynolds loss, tip Mach/compressibility, wake interference/coaxial loss, wall/ceiling effects, wind/gust/turbulence, rain/water loading.

## Unit conversions used below

UIUC propeller data uses the standard propeller coefficients:

```text
n = RPM / 60
omega = 2*pi*n
T = CT * rho * n^2 * D^4
P = CP * rho * n^3 * D^5
Q = P / omega = CP * rho * n^2 * D^5 / (2*pi)

Project thrustCoefficient k = T / omega^2 = CT * rho * D^4 / (4*pi^2)
Project yawTorquePerThrustMeter = Q / T = (CP / CT) * D / (2*pi)
```

For the tables below I used `rho = 1.225 kg/m^3` unless the source file reports otherwise.

## Propeller and thrust data

### UIUC Propeller Data Site

Source pages:

- [UIUC Propeller Database, Volume 1](https://m-selig.ae.illinois.edu/props/volume-1/propDB-volume-1.html)
- [UIUC Propeller Database, Volume 2](https://m-selig.ae.illinois.edu/props/volume-2/propDB-volume-2.html)
- Main reference: Deters, Ananda, and Selig, "Reynolds Number Effects on the Performance of Small-Scale Propellers," AIAA 2014-2151, linked from the UIUC page.

Useful raw files:

| Use case | Raw file | Contents |
|---|---|---|
| 5 inch, 3-blade, high-pitch FPV-like prop | [DA4052 5x3.75 3b static](https://m-selig.ae.illinois.edu/props/volume-2/data/da4052_5x3.75_3b_static_1204ga.txt) | `RPM CT CP` |
| Same prop, forward-flow curve | [DA4052 5x3.75 3b 7044 rpm](https://m-selig.ae.illinois.edu/props/volume-2/data/da4052_5x3.75_3b_1209ga_7044.txt) | `J CT CP eta` |
| 5 inch, 3-blade, lower loading comparison | [NR640 5 in 15 deg 3b static](https://m-selig.ae.illinois.edu/props/volume-2/data/nr640_5_15deg_3b_static_0696md.txt) | `RPM CT CP` |
| NR640 forward-flow curve | [NR640 5 in 15 deg 3b 9547 rpm](https://m-selig.ae.illinois.edu/props/volume-2/data/nr640_5_15deg_3b_0702rd_9547.txt) | `J CT CP eta` |
| 5x4 three-blade reference | [MicroInvent 5x4 static](https://m-selig.ae.illinois.edu/props/volume-2/data/mit_5x4_static_0362rd.txt) | `RPM CT CP` |
| 5x4 forward-flow curve | [MicroInvent 5x4 6057 rpm](https://m-selig.ae.illinois.edu/props/volume-2/data/mit_5x4_0366rd_6057.txt) | `J CT CP eta` |
| 10x4.7 slow-fly/heavy-lift reference | [APC SF 10x4.7 static](https://m-selig.ae.illinois.edu/props/volume-1/data/apcsf_10x4.7_static_kt0835.txt) | `RPM CT CP` |
| 10x4.7 forward-flow curve | [APC SF 10x4.7 6513 rpm](https://m-selig.ae.illinois.edu/props/volume-1/data/apcsf_10x4.7_rd0842_6513.txt) | `J CT CP eta` |

Extracted static averages:

| Source | Rows | RPM range | avg CT | avg CP | k = T/omega^2, N/(rad/s)^2 | Q/T, m |
|---|---:|---:|---:|---:|---:|---:|
| DA4052 5x3.75 3-blade | 14 | 1478-7947 | 0.1483 | 0.1032 | 1.197e-6 | 0.0141 |
| NR640 5 in 15 deg 3-blade | 18 | 1482-9898 | 0.1085 | 0.0622 | 8.76e-7 | 0.0116 |
| MicroInvent 5x4 3-blade | 11 | 1596-6396 | 0.1799 | 0.1174 | 1.452e-6 | 0.0132 |
| APC SF 10x4.7 | 16 | 2377-6528 | 0.1185 | 0.0480 | 1.530e-5 | 0.0164 |

Forward-flow examples that map to `rotorAdvanceRatio`, axial/transverse loss, and effective translational lift:

- DA4052 5x3.75 3b at 7044 rpm: `J` from 0.163 to 0.571 drives `CT` from 0.139 to 0.0476, while efficiency peaks around `eta = 0.576`.
- MicroInvent 5x4 at 6057 rpm: `J` from 0.094 to 0.564 drives `CT` from 0.182 to 0.0799, while efficiency peaks around `eta = 0.644`.
- APC SF 10x4.7 at 6513 rpm: `J` from 0.378 to 0.599 drives `CT` from 0.0698 to 0.0176.

### Mini Quad Test Bench FPV motor/prop data

Source:

- [Mini Quad Test Bench, Emax Eco 2306 2400kv](https://www.miniquadtestbench.com/assets/components/motordata/motorinfo.php?uid=259)

This source is useful because it measures a real FPV motor plus prop plus ESC/power setup, not just propeller aerodynamics. The page includes a static HTML summary table and a JSONP data loader from its `datarecorder.miniquadtestbench.com/admin/getdata.php` endpoint.

Extracted summary values:

| Setup | Thrust | RPM | Derived k = T/omega^2 |
|---|---:|---:|---:|
| Emax Eco 2306 2400kv + HQ v1s 5x4x3, avg max | 1187 g = 11.64 N | 29656 | 1.207e-6 |
| Emax Eco 2306 2400kv + HQ 5x4x3GF, avg max | 1271 g = 12.46 N | 28602 | 1.389e-6 |
| Emax Eco 2306 2400kv + HQ v1s 5x4.3x3PC, avg max | 1274 g = 12.49 N | 28428 | 1.410e-6 |
| Emax Eco 2306 2400kv + DAL Cyclone 5x4.5x3, avg max | 1283 g = 12.58 N | 28030 | 1.460e-6 |
| Emax Eco 2306 2400kv + T-Motor 5143 5.1x4.3x3, avg max | 1301 g = 12.76 N | 27590 | 1.528e-6 |
| Emax Eco 2306 2400kv + Gemfan 5149 5.1x4.9x3, avg max | 1353 g = 13.27 N | 27140 | 1.643e-6 |
| Same HQ v1s 5x4x3, 1500 us row | 783 g = 7.68 N | 24082 | 1.207e-6 |
| Same HQ v1s 5x4x3, 1250 us row | 416 g = 4.08 N | 17438 | 1.223e-6 |
| Same HQ v1s 5x4x3, 1100 us row | 139 g = 1.36 N | 10346 | 1.161e-6 |

Voltage/current rows from the same page for HQ v1s 5x4x3:

| Command row | Thrust | Current | Voltage | RPM |
|---|---:|---:|---:|---:|
| 1100 us | 139 g | 1.79 A | 16.11 V | 10346 |
| 1250 us | 416 g | 6.54 A | 16.03 V | 17438 |
| 1500 us | 783 g | 15.2 A | 15.89 V | 24082 |

Data note for the coding agent: the original `racingQuad()` preset used `maxRotorThrust = 13.5 N` and `thrustCoefficient = 1.8e-5`, which implied max RPM around 8270. The calibrated 5-inch FPV scale should stay near `k = 0.9e-6..1.6e-6` and roughly 27k-30k RPM for 12-13 N static thrust unless a separate gameplay scaling layer is introduced.

## Open-source simulator and model parameters

### ETH ASL RotorS

Source files:

- [RotorS Firefly xacro](https://raw.githubusercontent.com/ethz-asl/rotors_simulator/master/rotors_description/urdf/firefly.xacro)
- [RotorS Hummingbird xacro](https://raw.githubusercontent.com/ethz-asl/rotors_simulator/master/rotors_description/urdf/hummingbird.xacro)

Extracted values:

| Vehicle | Rotors | mass | body inertia | arm | rotor radius | motor constant | moment constant | time constants | max omega | rotor drag |
|---|---:|---:|---|---:|---:|---:|---:|---|---:|---:|
| Firefly | 6 | 1.5 kg | `(0.0347563, 0.0458929, 0.0977) kg m^2` | 0.215 m | 0.1 m | 8.54858e-6 | 0.016 m | up 0.0125 s, down 0.025 s | 838 rad/s | 8.06428e-5 |
| Hummingbird | 4 | 0.68 kg | `(0.007, 0.007, 0.012) kg m^2` | 0.17 m | 0.1 m | 8.54858e-6 | 0.016 m | up 0.0125 s, down 0.025 s | 838 rad/s | 8.06428e-5 |

RotorS uses the same basic `T = motor_constant * omega^2` and `Q = moment_constant * T` structure as this project.

Motor-response note: RotorS and PX4 both use `timeConstantUp = 0.0125 s` and `timeConstantDown = 0.025 s`. The generated validation report compares each current preset's `motor_tau` and `rotor_inflow_tau` against those references; `racingQuad.motor_tau = 0.045 s` is `3.6x` the RotorS/PX4 up-lag and `1.8x` the down-lag.

### PX4 Gazebo Classic Iris

Source:

- [PX4 SITL Gazebo Classic Iris SDF](https://raw.githubusercontent.com/PX4/PX4-SITL_gazebo-classic/main/models/iris/iris.sdf.jinja)

Extracted values:

| Field | Value |
|---|---:|
| mass | 1.5 kg |
| inertia | `ixx = 0.029125`, `iyy = 0.029125`, `izz = 0.055225 kg m^2` |
| rotor radius in collision model | 0.128 m |
| rotor positions | about `(0.13, +/-0.22, 0.023)` and `(-0.13, +/-0.20, 0.023)` m |
| motorConstant | 5.84e-6 |
| momentConstant | 0.06 m |
| timeConstantUp / timeConstantDown | 0.0125 s / 0.025 s |
| maxRotVelocity | 1100 rad/s |
| rotorDragCoefficient | 0.000175 |
| rollingMomentCoefficient | 1e-6 |

### gym-pybullet-drones Crazyflie 2.x

Source:

- [gym-pybullet-drones cf2x URDF](https://raw.githubusercontent.com/utiasDSL/gym-pybullet-drones/main/gym_pybullet_drones/assets/cf2x.urdf)

Extracted values:

| Field | Value |
|---|---:|
| mass | 0.027 kg |
| inertia | `ixx = 1.4e-5`, `iyy = 1.4e-5`, `izz = 2.17e-5 kg m^2` |
| arm | 0.0397 m |
| `kf` | 3.16e-10 |
| `km` | 7.94e-12 |
| thrust-to-weight | 2.25 |
| prop radius | 0.0231348 m |
| ground effect coefficient | 11.36859 |
| drag coefficients | `xy = 9.1785e-7`, `z = 10.311e-7` |
| downwash coefficients | `2267.18`, `0.16`, `-0.11` |

Note: this project's `kf` is used with the simulator's own motor-speed units. Do not copy it into `RotorSpec.thrustCoefficient` without checking the corresponding code path.

### Flightmare

Source:

- [Flightmare quadrotor environment config](https://raw.githubusercontent.com/uzh-rpg/flightmare/master/flightlib/configs/quadrotor_env.yaml)

Extracted values:

| Field | Value |
|---|---:|
| mass | 0.73 kg |
| arm length | 0.17 m |
| motor omega min / max | 150 / 3000, source comment says rpm |
| motor tau | 0.0001 s |
| thrust map | `[1.3298253500372892e-06, 0.0038360810526746033, -1.7689986848125325]` |
| kappa / rotor drag coefficient | 0.016 |
| body-rate constraint | `[6.0, 6.0, 6.0] rad/s` |

### RotorPy Hummingbird

Source:

- [RotorPy Hummingbird parameters](https://raw.githubusercontent.com/spencerfolk/rotorpy/main/rotorpy/vehicles/hummingbird_params.py)

Extracted values:

| Field | Value |
|---|---:|
| mass | 0.5 kg |
| inertia | `Ixx = 0.00365`, `Iyy = 0.00368`, `Izz = 0.00703 kg m^2` |
| arm length | 0.17 m |
| `k_eta` thrust coefficient | 5.57e-6 |
| `k_m/k_eta` torque/thrust ratio | 0.0244 m |
| rotor speed max | 1500 rad/s |
| quadratic body drag `c_D` | `[0.005, 0.005, 0.010] N/(m/s)^2` |

This source is particularly useful for `bodyDragCoefficients` because RotorPy's `c_D` has the same quadratic-force unit form as this project's body drag path.

## Environment and aero effects

### Standard atmosphere and air properties

Useful sources:

- [NASA Glenn atmosphere equations](https://www.grc.nasa.gov/www/k-12/airplane/atmosmet.html)
- [NASA Glenn speed of sound equation](https://www.grc.nasa.gov/www/BGH/sound.html)
- [NASA Glenn Sutherland viscosity model](https://www.grc.nasa.gov/www/BGH/viscosity.html)
- [NOAA/NASA/USAF U.S. Standard Atmosphere 1976, PDF](https://ntrs.nasa.gov/citations/19770009539)

Values relevant to `DronePhysics`:

- Sea-level standard density is conventionally `1.225 kg/m^3`, matching `SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER`.
- Standard gravity `9.80665 m/s^2` matches the project preset value.
- Standard sea-level temperature `288.15 K`, lapse rate `0.0065 K/m`, and pressure exponent about `5.255` match `DroneEnvironment.standardAtmospherePressureRatio`.
- The speed-of-sound path `sqrt(gamma * R * T)` with `gamma = 1.4` and `R = 287.05 J/(kg*K)` matches `DroneEnvironment.speedOfSoundMetersPerSecond`.
- Sutherland-law constants for air around `S = 110.4 K` and `beta = 1.458e-6 kg/(s*m*K^0.5)` are consistent with the project's `AIR_SUTHERLAND_CONSTANT_KELVIN` and viscosity-ratio path.

Generated atmosphere file:

- `docs/data/atmosphere_reynolds_mach_summary.csv` compares current presets across ISA sea level, 25 C project default, cold sea level, hot sea level, 1500 m, 3000 m ISA, and 3000 m hot cases.
- In the hot 3000 m / 30 C stress case, the generated standard-atmosphere density ratio is `0.658`, so a simple `T proportional rho * omega^2` model needs `1.233x` RPM for the same thrust before battery/motor limits.
- In the cold sea-level / -10 C case, `racingQuad` max tip Mach rises to about `0.60` because speed of sound drops to about `325 m/s`.
- The current low-Reynolds loss path is size-gated: `racingQuad` stays at `0.000` low-Re loss in the hot 3000 m case, while `cinewhoop` reaches `1.000` in the same generated proxy calculation.

### Wind, turbulence, and gusts

Useful open-source/model anchors:

- [pyfly Dryden turbulence implementation](https://raw.githubusercontent.com/eivindeb/pyfly/master/pyfly/dryden.py) exposes the common low-altitude Dryden formulas using `L_w = h`, `L_u = L_v = h / (0.177 + 0.000823h)^1.2`, `sigma_w = 0.1 W20`, and `sigma_u = sigma_v = sigma_w / (0.177 + 0.000823h)^0.4` with altitude in feet.
- [Open UAV wind modeling survey](https://arxiv.org/abs/1905.09954) gives broader context for Dryden/Von Karman-style wind fields in small-UAV simulation.
- Generated file `docs/data/wind_gust_dryden_reference.csv` compares the current hybrid Dryden-plus-burble gust model against a Dryden low-altitude intensity reference at 6 m altitude.

Current high-signal outputs:

- For `wind = 10 m/s` and `dirtyAir = 1.5`, the current target-gust RMS is about `1.65 m/s` horizontal X and `0.84 m/s` vertical after combining a reduced dirty-air burble with the Dryden target.
- Against a low-altitude Dryden reference with `W20 = 10 m/s`, that is roughly `0.86x` longitudinal intensity and `0.84x` vertical intensity.
- The Dryden component now runs as a reproducible colored-noise process with the physical low-altitude length scales and sigma targets, while the reduced deterministic burble remains a tunable FPV obstacle/wake feel layer.

### Airframe inertia and body drag

Generated report sections:

- `Airframe inertia sanity` compares `sqrt(I/m)` radius of gyration for the current presets against RotorS, PX4, gym-pybullet-drones, and RotorPy.
- `Body drag sanity` computes 10 m/s and 20 m/s base drag from the current `linearDragCoefficient` and `bodyDragCoefficients`, then compares with RotorPy Hummingbird.

Coordinate-system note:

- Current project presets use `Y` as the vertical/yaw axis, with rotor positions in `X/Z`.
- URDF/SDF/Python open-source models usually use `Z` as vertical/yaw.
- The generated report computes yaw inertia ratio using each source's own vertical axis.

Current high-signal outputs:

- `racingQuad` has radius of gyration `0.104/0.138/0.113 m` and yaw-axis inertia about `1.62x` the roll/pitch mean, close to RotorS Hummingbird and gym-pybullet Crazyflie ratios.
- `racingQuad` base drag at 10 m/s is `31 N` on body X and `38 N` on body Z before separated-flow additions, versus RotorPy Hummingbird's `0.5 N` and `1.0 N` body-drag-only values.
- The current drag path therefore looks very strong if interpreted as physical CdA; if it is a gameplay/stability damper, document it separately from measured aerodynamic drag.

Open-source drag anchors:

- [RotorPy Hummingbird parameters](https://raw.githubusercontent.com/spencerfolk/rotorpy/main/rotorpy/vehicles/hummingbird_params.py) gives direct quadratic body drag `c_D`.
- [gym-pybullet-drones BaseAviary.py](https://raw.githubusercontent.com/utiasDSL/gym-pybullet-drones/main/gym_pybullet_drones/envs/BaseAviary.py) applies a rotor-speed-scaled linear drag model based on Forster's Crazyflie system identification.

### Ground effect

Useful open-source data/model anchors:

- [gym-pybullet-drones cf2x URDF](https://raw.githubusercontent.com/utiasDSL/gym-pybullet-drones/main/gym_pybullet_drones/assets/cf2x.urdf) includes `gnd_eff_coeff = 11.36859`.
- [gym-pybullet-drones BaseAviary.py](https://raw.githubusercontent.com/utiasDSL/gym-pybullet-drones/main/gym_pybullet_drones/envs/BaseAviary.py) applies extra ground-effect force proportional to `rpm^2 * kf * gnd_eff_coeff * (R / (4h))^2`.
- [ZJU FAST-Lab Ground-effect-controller supplementary material](https://raw.githubusercontent.com/ZJU-FAST-Lab/Ground-effect-controller/master/README.md) gives motor calibration and ground-effect coefficients from "Ground-Effect-Aware Modeling and Control for Multicopters"; paper page: [arXiv 2506.19424](https://arxiv.org/abs/2506.19424).
- [UIUC prop data](https://m-selig.ae.illinois.edu/props/volume-2/propDB-volume-2.html) does not cover ground effect directly, but supplies free-air baseline CT/CP needed before adding ground-effect multipliers.

Suggested data mapping:

- Use UIUC static CT as free-air baseline.
- Treat ground-effect boost as a multiplier versus rotor height normalized by rotor radius or diameter.
- For the current `racingQuad`, the generated validation report compares current multipliers against the gym-pybullet-drones reference at `h/R = 1, 2, 4`.
- ZJU's reported single-rotor `k_T = 4.0083e-8 N/rpm^2` converts to `3.655e-6 N/(rad/s)^2`; its torque/thrust ratio `k_I/k_T` is about `0.0159 m`, close to the current 5-inch yaw-torque magnitude.

### Vortex ring state, propwash, wake interference

Useful open-source anchors:

- [RotorS](https://github.com/ethz-asl/rotors_simulator) and [PX4 SITL Gazebo Classic](https://github.com/PX4/PX4-SITL_gazebo-classic) model core motor thrust and drag, but their default Iris/Firefly files do not provide the same detailed VRS/propwash model as this project.
- [gym-pybullet-drones cf2x URDF](https://raw.githubusercontent.com/utiasDSL/gym-pybullet-drones/main/gym_pybullet_drones/assets/cf2x.urdf) includes downwash coefficients `dw_coeff_1 = 2267.18`, `dw_coeff_2 = 0.16`, `dw_coeff_3 = -0.11`.
- [Cambridge Flow dual-rotor axial-descent paper](https://www.cambridge.org/core/journals/flow/article/effects-of-rotor-separation-on-the-axial-descent-performance-of-dualrotor-configurations/BE7FE0D2E732E777CBD43F8E65CA0692) reports strongest thrust loss and oscillation around `1.2-1.3` times hover induced velocity, with losses up to roughly one third in fully developed VRS.

Data status:

- There is enough open-source support for including downwash and wake terms.
- The code's VRS thresholds are already induced-velocity normalized; the generated report converts them to m/s per preset and compares against the Cambridge `1.2-1.3 vi` peak-loss band.
- The separate `propwash_start/full` torque disturbance currently starts earlier than the VRS peak band, so it should be treated as handling/dirty-air feel unless a propwash-specific data table is added.

### Coaxial rotor interference

Useful open-source anchors:

- [New Dexterity Coaxial Benchmarking Platform README](https://raw.githubusercontent.com/newdexterity/Coaxial-Benchmarking-Platform/master/README.md) describes an open rig measuring thrust, torque, RPM, voltage, and current for coaxial rotor pairs.
- [Hackaday coaxial experiment result log](https://hackaday.io/project/181977/log/199225-some-results-of-coaxial-rotor-experiments-on-the-benchmarking-platform) describes 7 spacing values across `z/D = 0.1..1.0`, 100 command-map points per spacing, and 700 points per rotor set.

Current mapping:

- `coaxialX8` uses `verticalOffset = 0.72R` above and below the arm plane, so the actual upper/lower separation is `1.44R = 0.72D`.
- The Hackaday result log reports 11-inch rotor mechanical-efficiency local maxima around `0.25 < z/D < 0.4` and `0.7 < z/D < 0.85`, so the current X8 spacing lands near the second local maximum.
- Equal upper/lower commands are usually below the maximum-efficiency boundary in those tests, so an X8 efficiency model should eventually distinguish equal-command behavior from optimal allocation.

## Battery and power data

Open datasets:

- [NASA PCoE Prognostic Data Repository](https://ti.arc.nasa.gov/tech/dash/groups/pcoe/prognostic-data-repository/)
- [NASA battery data set landing page](https://www.nasa.gov/content/prognostics-center-of-excellence-data-set-repository)
- [NASA/DASHlink Li-ion Battery Aging Datasets page](https://data.nasa.gov/dataset/li-ion-battery-aging-datasets)
- [NASA battery data direct zip, about 210 MB](https://phm-datasets.s3.amazonaws.com/NASA/5.+Battery+Data+Set.zip)
- [CALCE Battery Data](https://calce.umd.edu/battery-data)
- [Open LiPo battery dataset on PMC](https://pmc.ncbi.nlm.nih.gov/articles/PMC10518458/)
- [Mendeley direct LiPo EIS/capacity/ECM dataset](https://data.mendeley.com/datasets/stcppt2r68/1)
- [CHL LiPo internal resistance explainer](https://chinahobbyline.com/blogs/news/lipo-internal-resistance-explained)
- [Battery University low/high-temperature discharge guide](https://batteryuniversity.com/article/bu-502-discharging-at-high-and-low-temperatures)
- [Battery University internal-resistance performance guide](https://batteryuniversity.com/article/bu-802a-how-does-rising-internal-resistance-affect-performance)

How to use them:

- Use NASA/CALCE/PMC/Mendeley datasets for OCV/SOC curve shape, thermal dependency, capacity fade, and internal-resistance trend.
- Mendeley's fitted ECM CSVs include `SOC, R_0, R_1, Q_1, a_1, R_2, Q_2, a_2, Q, L`; `R_0(SOC, SOH)` is a direct candidate for state-dependent internal resistance.
- NASA's impedance fields include `Battery_impedance`, `Rectified_impedance`, `Re`, and `Rct`.
- Use FPV-specific thrust-stand pages such as Mini Quad Test Bench for high-current voltage sag under prop load.
- The current `racingQuad()` battery values are `16.8 V nominal`, `13.2 V empty`, `0.018 ohm pack resistance`, `1.5 Ah`, `90 A max`. That is `4.5 mOhm/cell` if interpreted as a 4S pack, which is plausible for a high-C pack but should be validated against a real FPV battery test or manufacturer ESR data before treating it as measured.
- CHL's practical bands put many fresh high-performance LiPos around `2-5 mOhm/cell`, below `10 mOhm/cell` as a strong fresh-pack target, `10-20 mOhm/cell` as usable/healthy, and above `20 mOhm/cell` as tired for high-performance use.
- `docs/data/battery_temperature_derating_summary.csv` evaluates the current temperature model for `racingQuad`. At 0 C it gives `1.47x` resistance and `0.72x` current scale; at 70 C it gives about `1.07x` resistance, `0.83x` current scale, and `0.78x` thermal power limit.

Mendeley ECM extraction:

- Script: `docs/scripts/analyze_mendeley_lipo_ecm.py`.
- Output: `docs/data/lipo_ecm_mendeley_r0_summary.csv`.
- Large archive endpoint: `https://data.mendeley.com/public-api/zip/stcppt2r68/download/1`; the downloaded zip is about 174 MB and is kept in the local temp directory, not this repo.
- The fitted CSVs actually use column name `RO` for ohmic resistance.
- Extracted result: 57 fitted-cycle files across 5 packs. Mean `RO` spans `75.6-93.0 mOhm/cell`; low-SOC `RO` averages `1.037x` high-SOC `RO`; pack-level first-to-last fitted-cycle mean `RO` growth is about `1.05x-1.20x`.
- This is not an FPV high-C absolute ESR source. It is useful for the shape of SOC/SOH-dependent resistance if the absolute scale is separately calibrated to FPV packs.

MiniQuad Test Bench voltage/current examples for one 2306 motor and HQ v1s 5x4x3:

| Thrust | Current | Voltage |
|---:|---:|---:|
| 139 g | 1.79 A | 16.11 V |
| 416 g | 6.54 A | 16.03 V |
| 783 g | 15.2 A | 15.89 V |

These rows can help validate per-motor current and voltage-sag scale. They do not by themselves identify pack internal resistance because test-stand supply, wiring, ESC, and measurement setup are coupled.

## Motor and ESC thermal references

Useful sources:

- [U8 Kv100 dyno processed data](https://github.com/thhsieh/U8-Kv100-Dyno-Data)
- [Copper temperature coefficient reference](https://www.copper.org/publications/newsletters/innovations/2001/08/intro_fac.html)
- [Motor insulation-class context](https://www.theaemt.com/resource/insulation-classes-for-electric-motors.html)
- [Infineon IRL40SC228 MOSFET thermal context](https://www.infineon.com/part/IRL40SC228)

Generated thermal file:

- `docs/data/motor_esc_thermal_reference.csv` summarizes the open U8 processed temperature/loss/efficiency maps, current preset motor/ESC thermal limits and cooling proxies, and copper winding resistance scaling versus temperature.
- The U8 dyno is a larger motor and driver than an FPV 2306-class setup, so treat it as an open BLDC thermal/efficiency scale reference rather than direct parameter data.
- The current preset rows mirror this project's thermal equations under sea-level, unobstructed conditions; measured FPV motor winding temperature and ESC case/junction telemetry would still be the best calibration source.
- Copper winding resistance scaling gives about `1.39x` resistance at `125 C` and `1.60x` at `180 C` relative to `25 C`, so hot-motor resistance should not remain constant if the simulation tries to model sustained abuse.
- For `racingQuad`, the current proxy gives motor limit/cutoff `95/125 C`; a full-power no-airspeed steady-rise estimate exceeds the limit, which is a useful red flag for sustained-throttle thermal limiting rather than a measured FPV value.

## Sensor, filtering, and control references

Betaflight references:

- [Betaflight rate calculator](https://betaflight.com/docs/wiki/guides/current/Rate-Calculator)
- [Betaflight PID tuning guide](https://betaflight.com/docs/wiki/guides/current/PID-Tuning-Guide)
- [Betaflight DShot RPM filtering](https://betaflight.com/docs/wiki/guides/current/DSHOT-RPM-Filtering)
- [Betaflight DShot protocol/API notes](https://betaflight.com/docs/development/API/Dshot)
- [Betaflight blackbox logging guide](https://betaflight.com/docs/wiki/guides/current/Black-Box-logging-and-usage)
- [Betaflight PID tuning tab reference](https://betaflight.com/docs/wiki/app/pid-tuning-tab)
- [Betaflight blackbox source field table](https://raw.githubusercontent.com/betaflight/betaflight/master/src/main/blackbox/blackbox.c)
- [Public Betaflight issue blackbox log attachment](https://github.com/betaflight/betaflight/files/5507542/LOG00078.TXT)
- [blackbox-library parser project](https://github.com/maxlaverse/blackbox-library) and [normal.bfl fixture](https://raw.githubusercontent.com/maxlaverse/blackbox-library/master/fixtures/normal.bfl)
- [ExpressLRS switch/channel resolution](https://www.expresslrs.org/software/switch-config/)
- [ExpressLRS Lua packet-rate notes](https://www.expresslrs.org/quick-start/transmitters/lua-howto/)
- [ExpressLRS RF-mode and sensitivity table](https://www.expresslrs.org/info/signal-health/)

Useful values and concepts:

- RPM filtering uses per-motor RPM telemetry and usually filters the first three harmonics per motor.
- For a quad, Betaflight's RPM filter default can create 36 notches: 4 motors * 3 harmonics * 3 gyro axes.
- Betaflight dynamic notch guidance gives practical frequency bands and Q-factor ranges; the docs mention examples like one notch with high Q when RPM filtering is active, and wider/multiple notches when RPM filtering is absent.
- Betaflight's rate/expo/super-rate behavior is a good conceptual match for `rateExpo`, `rateSuper`, and max rate fields, but the exact formula in this project should be checked before copying UI values.

Generated timing/filter checks:

- The report's `RPM, filtering, and command timing sanity` section derives hover/max RPM, configured blade-pass frequency from `RotorSpec.bladeCount`, three-blade reference frequency, gyro LPF, RC frame interval, ESC command frame interval, and configured latency/smoothing.
- `docs/data/rc_esc_timing_reference.csv` separates protocol references from current preset settings: ELRS packet-rate anchors, DShot raw frame timing and 2000-step throttle resolution, Blackbox log-rate anchors, and current preset RC/ESC frame/latency/quantization ratios.
- `racingQuad` and `cinewhoop` now configure `RotorSpec.bladeCount = 3`, matching the UIUC and Mini Quad Test Bench three-blade FPV prop anchors used here.
- Larger lift presets keep the two-blade default until prop-family-specific data supports a different blade count.
- `racingQuad` uses `150 Hz` RC frame rate, `400 Hz` ESC command rate, `15 ms` control latency, `18 ms` RC command latency, and `18 ms` RC smoothing. These are plausible gameplay/controller values, but they are not equivalent to a modern high-rate Betaflight loop unless explicitly modeled.
- In the generated RC/ESC timing table, `racingQuad`'s `18 ms` RC latency and smoothing each span about `2.7` configured RC frames. Its `400 Hz` ESC frame is intentionally far below raw DShot300/600 frame capacity, so it should be read as a simplified motor-command cadence, not literal DShot wire timing.

IMU noise anchors:

- [MPU-6000/6050 datasheet](https://www.cdiweb.com/datasheets/invensense/mpu-6050_datasheet_v3%204.pdf): gyro noise density `0.005 deg/s/sqrt(Hz)`, accel noise density `400 ug/sqrt(Hz)`.
- [ICM-20602 datasheet](https://bluerobotics.com/wp-content/uploads/2022/05/ICM20602-DATASHEET.pdf): gyro noise density `0.004 deg/s/sqrt(Hz)`, accel noise density `100 ug/sqrt(Hz)`.
- [BMI270 datasheet](https://www.bosch-sensortec.com/media/boschsensortec/downloads/datasheets/bst-bmi270-ds000.pdf): gyro noise density `0.008 deg/s/sqrt(Hz)`, accel noise density `160 ug/sqrt(Hz)`.
- [ICM-42688-P product page](https://invensense.tdk.com/en-us/products/6-axis/icm-42688-p): gyro noise density `0.0028 deg/s/sqrt(Hz)`, accel noise density `70 ug/sqrt(Hz)`.
- Generated file `docs/data/imu_noise_reference_summary.csv` converts those datasheet densities to RMS at each preset's configured gyro/accelerometer LPF using one-pole equivalent noise bandwidth `pi/2 * cutoff`.
- For `racingQuad`, the configured `0.025 rad/s` gyro noise is about `20.8x` an MPU-6000/6050 electronics-only RMS estimate at 120 Hz LPF and about `37.2x` an ICM-42688-P estimate. Treat it as residual vibration plus electronics, not bare IMU electronics noise.

Barometer/altimeter anchors:

- [Bosch BMP280 datasheet](https://www.bosch-sensortec.com/media/boschsensortec/downloads/datasheets/bst-bmp280-ds001.pdf)
- [Bosch BMP388 datasheet](https://www.bosch-sensortec.com/media/boschsensortec/downloads/datasheets/bst-bmp388-ds001.pdf)
- [Infineon DPS310 datasheet](https://www.infineon.com/dgdl/Infineon-DPS310-DataSheet-v01_02-EN.pdf?fileId=5546d462576f34750157750826c42242)
- [TE/Measurement Specialties MS5611-01BA03 datasheet](https://www.hpinfotech.ro/MS5611-01BA03.pdf)
- Generated file `docs/data/barometer_reference_summary.csv` converts pressure noise and relative accuracy to equivalent altitude error with `dh = dp / (rho g)`.
- Current takeaway: good MEMS pressure noise is centimeters-to-decimeters in altitude, while the project's flow/static-port model can create meter-scale barometer error at FPV airspeeds. Keep those concepts separate.

Generated blackbox/log checks:

- `docs/data/blackbox_log_header_summary.csv` extracts public blackbox header fields, including field presence, `looptime`, `pid_process_denom`, DShot/RPM-filter headers, and an estimated main log rate.
- The public Betaflight 4.2.4 log has `looptime = 125 us`, `pid_process_denom = 2`, `dshot_bidir = 1`, three gyro RPM-notch harmonics, and estimated main records around `4000 Hz`.
- That log includes `time`, `gyroADC`, `accSmooth`, and `motor[0..3]`, but no `eRPM[]` columns. It is useful for timing and field-format validation, not for direct motor-RPM curve validation.
- Betaflight's current blackbox source table documents `eRPM / 100` for DShot telemetry fields. For a common 14-pole FPV motor, `mechanical_rpm = logged_eRPM100 * 100 * 2 / 14`, so one logged count is about `14.29 rpm`.
- With the current `racingQuad` RPM scale, a 14-pole motor would map to about `912` logged `eRPM/100` at hover and `2040` at max thrust.

ExpressLRS references:

- [ExpressLRS switch modes and packet-rate context](https://www.expresslrs.org/software/switch-config/)
- [ExpressLRS signal health](https://www.expresslrs.org/info/signal-health/)
- [ExpressLRS telemetry bandwidth](https://www.expresslrs.org/info/telem-bandwidth/)

Use these for RC link update-rate/failsafe plausibility rather than rigid physics constants. The project defaults `rcFrameRateHertz = 150`, `rcChannelResolutionSteps = 2048`, and `rcFailsafeTimeoutSeconds = 0.35`, which are in the range of modern RC-link behavior, but exact values should be tied to a chosen link mode.

## Priority cross-checks for the coding agent

1. Reconcile 5-inch rotor speed scale.
   - The calibrated `racingQuad` k should imply about 27k-30k rpm for 12-13 N static thrust.
   - UIUC/MQTB 5-inch data suggests k around `0.9e-6..1.6e-6`.

2. Reconcile yaw torque coefficient.
   - UIUC 5-inch examples give `Q/T` around `0.0116..0.0141 m`.
   - RotorS uses `0.016 m`.
   - PX4 Iris uses `0.06 m`.
   - The calibrated racing preset uses `0.014 m`, close to UIUC 5-inch ranges and still far from PX4 Iris.

3. Separate physical data from gameplay scaling.
   - If Minecraft scale or tick feel requires nonphysical RPM, document that layer explicitly and avoid using physical telemetry labels for the scaled value.

4. Use UIUC forward-flow curves for `advanceRatio`, `transverseFlowLift`, `axialFlowLoss`, low-Re loss, and stall/CT rolloff validation.

5. Use MiniQuad Test Bench for per-motor current, voltage, thrust, and RPM scale on FPV racing presets.

6. Use RotorS/PX4/Flightmare/gym-pybullet-drones/RotorPy as sanity checks for airframe mass, inertia, motor time constants, simple `T = k*omega^2` structure, and body-drag order of magnitude.

7. Reconcile drag coefficients.
   - Current `linearDragCoefficient` and `bodyDragCoefficients` are both quadratic in speed.
   - `racingQuad` produces more than `2.8x` weight of base X-axis drag at 10 m/s before separated-flow terms.
   - Treat those coefficients as gameplay damping unless wind-tunnel, coast-down, log-fit, or open-source model data supports that magnitude.

8. Reconcile blade-pass semantics.
   - Blade-pass ripple and gyro notch frequency now use per-rotor `RotorSpec.bladeCount`.
   - Keep the count aligned with the prop family used for calibration; three-blade FPV logs should compare against 3x mechanical motor frequency, while larger two-blade lift props can remain at 2x until better data is available.
   - If a preset intentionally uses a synthetic harmonic for feel, avoid labeling that notch as measured physical blade-pass frequency.

9. Reconcile wind/gust semantics.
   - Keep the low-altitude Dryden colored-noise turbulence component separate from the reduced deterministic burble used for obstacle/propwash feel.
   - If the goal is tighter spectrum matching, compare the current OU-style Dryden process against a fuller Dryden/Von Karman filter implementation.

10. Reconcile IMU noise semantics.
   - Datasheet electronics noise is much lower than the current configured noise at the project LPF bandwidths.
   - If these values include frame vibration, prop imbalance, and aliasing, label them as residual FPV sensor noise rather than bare IMU electronics.

11. Reconcile barometer error semantics.
   - MEMS pressure-sensor noise should be centimeters-to-decimeters when converted to altitude near sea level.
   - Meter-scale barometer excursions should be attributed to propwash/static-port/dynamic-pressure error, not raw sensor noise.

12. Reconcile battery temperature effects.
   - Current coefficients are directionally plausible: cold raises resistance and reduces current scale; heat drives thermal power limiting.
   - Keep this separate from SOC/SOH resistance growth and calibrate coefficients with high-C FPV pack ESR versus temperature if available.

13. Reconcile blackbox RPM units before using logs for validation.
   - Betaflight blackbox `eRPM[]` values are logged as electrical RPM divided by 100.
   - Convert through motor pole count before comparing to simulated mechanical RPM or blade-pass frequency.
   - Public logs without `eRPM[]` should only be used for timing, gyro, accelerometer, and motor-command field validation.

## Gaps still worth filling

- Open numeric data for 5-inch prop dynamic inflow time constants, flapping coefficient, coning, arm-flex resonance, and blade-pass vibration.
- Open FPV-scale airframe drag measurements, ideally wind-tunnel, coast-down, or onboard-log fits separated by attitude and axis.
- Open high-C LiPo internal resistance versus SOC and temperature for FPV-sized 4S/6S packs; Mendeley/NASA give SOC/SOH shape but not FPV high-C absolute values.
- Digitized multirotor ground-effect table for thrust multiplier versus `h/R`; ZJU gives coefficients, but the project still needs equation-level mapping before direct replacement.
- Propwash-specific buffeting table for torque/noise versus normalized descent rate; Cambridge supports VRS thrust-loss timing, not FPV propwash feel torque.
- Digitized coaxial/stacked rotor thrust and efficiency curves versus `z/D`; New Dexterity/Hackaday give the experimental map description and qualitative maxima, but not machine-readable curve points in this packet.
- Real Betaflight blackbox logs for a known 5-inch quad with explicit `eRPM[]` columns, gyro spectrum, and propwash recovery.
