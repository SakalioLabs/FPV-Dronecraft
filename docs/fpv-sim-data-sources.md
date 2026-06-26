# FPV simulation data sources

Date: 2026-06-12

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

- `docs/data/atmosphere_reynolds_mach_summary.csv` now also includes `uiuc_static_reynolds_point` and `uiuc_static_reynolds_summary` rows derived from those static `RPM CT CP` files. The `Re75` values are proxy values using 25 C air and the project's representative chord convention, but the high/low-Re CT/CP ratios are useful trend anchors for validating low-Reynolds behavior.

Forward-flow examples that map to `rotorAdvanceRatio`, axial/transverse loss, effective translational lift, and same-RPM `CP`/power behavior:

- DA4052 5x3.75 3b at 7044 rpm: `J` from 0.163 to 0.571 drives `CT` from 0.139 to 0.0476, while efficiency peaks around `eta = 0.576`.
- MicroInvent 5x4 at 6057 rpm: `J` from 0.094 to 0.564 drives `CT` from 0.182 to 0.0799, while efficiency peaks around `eta = 0.644`.
- APC SF 10x4.7 at 6513 rpm: `J` from 0.378 to 0.599 drives `CT` from 0.0698 to 0.0176.
- [Stanford STARMAC II quadrotor dynamics paper](https://ai.stanford.edu/~gabeh/papers/Quadrotor_Dynamics_GNC07.pdf) includes a rotor flapping wind-speed/deflection comparison in Fig. 9, plus blade-stiffness and effective-hinge-offset context. The generated CSV carries a low-precision manual digitization for flapping scale checks only.

Generated forward-flow file:

- `docs/data/rotor_forward_flow_reference.csv` converts UIUC 5-inch forward-flow fits into `CT/static CT`, `CP/static CP`, and relative `Q/T = (CP/CT)/(static CP/static CT)` at reference `J` values, mirrors the current Java level-forward formulas for each preset at `5`, `10`, `12.5`, `15`, `20`, and `30 m/s`, and includes the STARMAC II Fig. 9 flapping digitization with a `racingQuad` same-speed proxy.
- `docs/data/uiuc_forward_flow_mu_guard_packet.csv` adds a `1696`-row J/mu conversion guard and handoff table, mirrored into `docs/data/fpv_model_validation_summary.csv` as `uiuc_mu_guard_packet_*` categories. It unifies UIUC 5-inch fits, current level-forward points, current high-advance thresholds, APC axial high-J boundaries, and the Mejzlik wind-tunnel table with both published `J` and code `mu`.
- `docs/scripts/analyze_tyto_wind_tunnel_lead_packet.py` generates `docs/data/tyto_wind_tunnel_lead_packet.csv`, a `38`-row forward-flow lead packet mirrored as `tyto_wind_tunnel_lead_packet_*`. It captures Tyto's public Windshaper article-level setup: a 9-inch propeller, `0..17 m/s` (`0..38 mph`) airspeeds, four throttle steps, thrust/torque/RPM/current/voltage/airspeed instrumentation, and a reported 9000-RPM trend where thrust drops `75%` while power drops only `19%` between 0 and 17 m/s.
- `docs/scripts/analyze_imav2021_forward_flow_packet.py` generates `docs/data/imav2021_forward_flow_packet.csv`, a `56`-row source packet mirrored as `imav2021_forward_flow_packet_*`. It captures the [IMAV 2021 RMIT propulsive-efficiency paper](https://www.imavs.org/papers/2021/21.pdf): 5-inch, 3-blade HQProp V1S propellers on a T-Motor F80 2500kv, wind speeds `10/15/20 m/s`, flow angles `30/35/40/45/50/90 deg`, RPM ramp `10000..~30000`, RCBenchmark 1580 thrust/torque logging, electrical power logging, and `J = U/(nD)` nondimensionalization. The packet is a source/coverage handoff, not a fitted CT/CP curve: the public PDF has figure curves but no raw CSV/table rows.
- `docs/scripts/analyze_imav2021_figure_inventory_packet.py` generates `docs/data/imav2021_figure_inventory_packet.csv`, a `253`-row digitization handoff mirrored as `imav2021_figure_inventory_packet_*`. It extracts the PDF's `8` embedded images into `docs/data/raw/imav2021_forward_flow/extracted_images/`, inventories Figs. 5-8 as `18` efficiency-vs-RPM panels, and records the line-color mapping for `HQ 5x4`, `HQ 5x4.3`, `HQ 5x4.8`, and `HQ 5x5`. This is an `eta(RPM)` digitization target only; the paper text explicitly treats propulsive efficiency as insufficient for fully characterizing propeller performance, so CT/CP fitting still needs raw thrust/torque logs or additional numeric curves.
- `docs/scripts/analyze_kolaei2018_inflow_angle_rotor_packet.py` generates `docs/data/kolaei2018_inflow_angle_rotor_packet.csv`, a `74`-row source packet mirrored as `kolaei2018_inflow_angle_rotor_packet_*`. It captures the open [Kolaei, Barcelos, and Bramesfeld 2018 inflow-angle rotor paper](https://doi.org/10.1155/2018/2560370): 11x7 and T-Motor 18x6.1 rotors, `3000/4000/5000 rpm`, inflow angles from `90` to `-90 deg` with dense near-zero curves, `mu=V/(Omega*R)` up to about `0.30`, and measured `CT`, `CP`, and roll-moment coefficient `CMx`. This source uses the same advance-ratio definition as the Java `rotorAdvanceRatio`, so no UIUC `J/pi` conversion is needed. The packet also marks Figs. 9-11 as pending vector/page-render digitization targets for CT, CP, and CMx.
- Important unit mapping: UIUC uses `J = V/(nD)`, while this project uses `rotorAdvanceRatio = V/(omega R)`. Because `omega = 2*pi*n` and `D = 2R`, `J = pi * rotorAdvanceRatio`.
- At `J = 0.45`, the UIUC 5-inch mean fitted `CT/static CT` is about `0.55` (`0.52-0.59` across the three 5-inch rows), `CP/static CP` is about `0.69` (`0.59-0.77`), and relative `Q/T` rises to about `1.25`, corresponding to code `mu = 0.143`.
- For `racingQuad` at hover RPM and `12.5 m/s` level forward speed, the generated table gives equivalent `J ~= 0.453`; the UIUC 5-inch fitted CT/CP ratios are about `0.54/0.68`, but the current Java airflow multiplier and same-RPM constant-`Q/T` power proxy are about `1.06`. That means the present forward-flow model boosts thrust and power where UIUC prop data would predict substantial CT and CP rolloff, unless this is intentionally a gameplay translational-lift assist.
- The guard packet makes the mismatch explicit: at `12.5 m/s`, `racingQuad` current airflow multiplier is `1.96x` the UIUC 5-inch mean CT ratio; at `20 m/s`, the UIUC extrapolated CT ratio is `0.081` and the current multiplier is `13.23x` that value. Treat high-speed UIUC extrapolation carefully, but do not compare raw `J` against code `mu`.
- Tyto's Windshaper article gives a useful dynamic trend but not raw curve rows. At 17 m/s, its 9-inch/9000-RPM condition maps to `J=0.496` / code `mu=0.158`, while the current `racingQuad` 5-inch hover-RPM proxy at 17 m/s maps to `J=0.617` / `mu=0.196`. The article's 0-to-17 m/s trend retains only `0.25x` thrust and `0.81x` power, so `T/P` falls to about `0.309x`; use this as a digitization target and sanity trend, not as a raw 5-inch fit.
- IMAV 2021 is the closest public direct 5-inch forward-flow source found so far. Its high-quality cropped RPM band maps to `J=0.157..0.315` at `10 m/s`, `0.236..0.472` at `15 m/s`, and `0.315..0.630` at `20 m/s`. Current `racingQuad` hover-RPM proxies are `J=0.363/0.453/0.544/0.726` at `10/12.5/15/20 m/s`, while max-RPM proxies are `J=0.162/0.203/0.243/0.324`. This overlaps the runtime FPV range well enough to prioritize eta-curve digitization plus a raw RMIT log/thesis search before changing `forward_flow_*` constants.
- Kolaei 2018 is not a 5-inch FPV prop fit, but it is a strong nondimensional shape check for the current `rotorAdvanceRatio` range. Current `racingQuad` hover-RPM `mu` is `0.144` at `12.5 m/s`, `0.231` at `20 m/s`, and `0.346` at `30 m/s`; the source covers about `mu <= 0.30`, while current max-RPM `mu` stays below that range even at `40 m/s` (`0.206`). Use it to check transverse-flow thrust/power trends and roll-moment sign/scale before trusting the gameplay forward-flow curve.
- Current high-advance loss does not start until code `mu = 0.46`, which is UIUC `J ~= 1.45`, far beyond the common 5-inch prop forward-flow data range used here.

### Rotor blade dissymmetry and retreating-blade stall

Useful open rotorcraft anchors:

- [FAA Helicopter Flying Handbook, Chapter 11](https://www.faa.gov/sites/faa.gov/files/regulations_policies/handbooks_manuals/aviation/helicopter_flying_handbook/hfh_ch11.pdf) gives qualitative retreating-blade-stall symptoms and high-speed/high-load contributors. It is not a numeric small-prop threshold source.
- [NASA/CR-2008-215370 high-advance-ratio rotor review](https://rotorcraft.arc.nasa.gov/Publications/files/NASA%20CR%202008-215370%20Harris.pdf) covers rotor performance theory versus test data at high advance ratio, including operation approaching `mu = 1`.
- [UH-60A slowed-rotor high-advance-ratio test paper](https://rotorcraft.arc.nasa.gov/Publications/files/A-6-F_Kottapalli.pdf) gives a public high-`mu` slowed-rotor context where reverse flow and stall are central effects.
- [DLR ERF2023 dynamic-stall rotor-code validation paper](https://elib.dlr.de/191749/1/vanderWall%20-%20ERF2023_0001_vanderWall.pdf) uses model-rotor data at `mu = 0.4` for dynamic-stall validation.

Generated blade-dissymmetry file:

- `docs/data/rotor_blade_dissymmetry_reference.csv` records those reference anchors, mirrors the current Java `calculateSteadyRotorBladeDissymmetryAerodynamics(...)` curve, and scans current presets at `5..60 m/s` for both hover RPM and max RPM.
- Current Java thresholds are `mu = 0.08..0.34` for lift dissymmetry and `mu = 0.42..0.82` for retreating-blade stall. The nearby high-advance loss starts at `mu = 0.46`.
- For `racingQuad`, the generated speed map puts hover-RPM `mu` near `0.346` at `30 m/s` and `0.462` at `40 m/s`; at max RPM, `40 m/s` is only about `mu = 0.206`. That makes retreating-blade stall mainly a low-throttle/high-forward-speed edge for the current 5-inch preset.
- Important limitation: UIUC 5-inch forward-flow propeller data used above only reaches about `J = 0.57`, or code `mu ~= 0.18`. It can validate low-to-mid forward-flow CT/CP rolloff, but it cannot validate the current `mu = 0.42..0.82` retreating-blade-stall curve directly.
- New high-advance packet: `docs/scripts/analyze_high_advance_rotor_prop_sources.py` downloads 7 APC performance files and generates `docs/data/apc_high_advance_propeller_reference.csv` (`7590` axial-prop prediction rows), `docs/data/high_advance_rotor_source_inventory.csv` (`16` APC/NASA/UMD/DLR source rows), and `docs/data/high_advance_rotor_prop_packet.csv` (`637` handoff rows mirrored as `high_advance_packet_*`). The selected APC files extend axial coverage to `J = 2.466` / equivalent code `mu = 0.785`, but APC data is a proprietary vortex-method prediction from actual propeller geometry, not wind-tunnel or edgewise rotor data.
- APC high-J result: moderate 5-inch props lose positive axial CT long before the current retreating-stall band. APC `5x4.5E` reaches only equivalent `mu = 0.345` with positive CT, and APC `5.1x5.0E 3-blade` reaches `0.360`; at current `mu = 0.34` (`J = 1.068`) their nearest CT/static ratios are `0.006` and `0.079`. Only high-pitch axial props such as `5x7.5E` and especially `5x11E` cover `mu >= 0.42`; the `5x11E` row near current stall start has CT/static about `1.012`, which mostly shows pitch/design dependence rather than validating FPV retreating-blade stall.
- New measured high-J axial table: `docs/scripts/analyze_mejzlik_wind_tunnel_prop_packet.py` encodes the public AirShaper/Mejzlik propeller study into `docs/data/mejzlik_wind_tunnel_prop_packet.csv` (`129` rows mirrored as `mejzlik_prop_packet_*`). The source reports wind-tunnel testing at `4900 rpm`, `0..35 m/s`, and table rows at `J = 0.2..0.8`. The wind-tunnel `CT` row is `0.0411` at `J=0.6` and `-0.0035` at `J=0.8`; linear interpolation gives `CT=0` near `J=0.784` / code `mu=0.250`. At the current `racingQuad` hover RPM, that maps to about `21.62 m/s`, while the current high-advance-loss start is `J=1.445` / about `39.84 m/s`. Treat this as a measured axial windmilling sanity check, not as direct edgewise retreating-blade-stall calibration.
- The new J/mu guard packet summarizes threshold distance: current lift-dissymmetry full is `J=1.068`, retreating-stall start is `J=1.319`, and high-advance-loss start is `J=1.445`. That high-advance-loss start is `2.53x` the selected UIUC 5-inch experimental `J` max (`0.571`) and `1.84x` the Mejzlik wind-tunnel CT-zero boundary (`J=0.784`).
- Source separation to keep clear for the coding agent: use APC high-J rows for axial propeller CT/CP and zero-thrust/windmilling shape checks; use NASA UH-60A slowed-rotor, UMD Mach-scale, and related high-`mu` rotor papers for edgewise reverse-flow/retreating-side phenomenology. Do not calibrate the 5-inch FPV retreating-blade-stall curve directly from APC axial `J`.

### Mini Quad Test Bench FPV motor/prop data

Source:

- [Mini Quad Test Bench, Emax Eco 2306 2400kv](https://www.miniquadtestbench.com/assets/components/motordata/motorinfo.php?uid=259)

This source is useful because it measures a real FPV motor plus prop plus ESC/power setup, not just propeller aerodynamics. The page includes a static HTML summary table and a JSONP data loader from its `datarecorder.miniquadtestbench.com/admin/getdata.php` endpoint.

Generated file:

- `docs/data/mqtb_hq5x4x3_current_model_reference.csv` fits the HQ v1s 5x4x3 IDLE/25/50/75/100% rows as current and electrical power versus thrust, keeps per-point residuals, and projects current `racingQuad` hover/current-limit/max-thrust operating points onto that measured curve.

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

Current/power fit note:

- The generated HQ v1s 5x4x3 fit gives `I = 1.430 * T^1.158` with log-space R2 `0.9946` and RMS current residual `10.8%`.
- Projected through current `racingQuad`, hover is about `18.0 A` total motor current, the configured `90 A` pack limit maps to about `10.8 N` per motor, and the configured `13.5 N` per motor would draw about `116.4 A` total on this representative 5-inch prop curve.

Data note for the coding agent: the original `racingQuad()` preset used `maxRotorThrust = 13.5 N` and `thrustCoefficient = 1.8e-5`, which implied max RPM around 8270. The calibrated 5-inch FPV scale should stay near `k = 0.9e-6..1.6e-6` and roughly 27k-30k RPM for 12-13 N static thrust unless a separate gameplay scaling layer is introduced.

### Tyto Robotics public static powertrain tests

Useful sources:

- [Tyto Robotics Database](https://database.tytorobotics.com)
- [Five33 2207 Azure Vanover test](https://database.tytorobotics.com/tests/x3nm/five33-2207-azure-vanover)
- [T-Motor LF40 2305 with Gemfan 5040 R test](https://database.tytorobotics.com/tests/dnq/lf40-2305-with-5040-prop)
- [Tmotor-F80 test](https://database.tytorobotics.com/tests/69k7/tmotor-f80)
- [1700kvlumenierfolding test](https://database.tytorobotics.com/tests/q3xn/1700kvlumenierfolding)
- [Mendeley 30-Inch Propellers Performance for UAVs](https://data.mendeley.com/datasets/69hhwc3fd3/1) / DOI [10.17632/69hhwc3fd3.1](https://doi.org/10.17632/69hhwc3fd3.1), collected with a Tyto Robotics Flight Stand 50 at 100 Hz for 60 s per hover case.

Generated file:

- `docs/data/tyto_fpv_static_powertrain_reference.csv` caches selected small public Tyto test pages, extracts the embedded `benchmark-data-table` arrays, normalizes thrust/current/voltage/RPM/torque/power samples, fits `T = k * omega^2`, and compares each selected static test with the current `apDrone()` max-thrust and thrust-coefficient fields.
- `docs/data/tyto_fpv_static_torque_ratio_reference.csv` derives measured propeller `Q/T = torque / thrust` from the same Tyto samples, verifies `torque * omega` against Tyto mechanical power, and compares fitted `Q/T` with current `racingQuad()`/`apDrone()` `yawTorquePerThrustMeter`.
- `docs/scripts/analyze_tyto_5in_static_prop_packet.py` scans the public Tyto propeller catalog for 4.8..5.2 inch candidates, queries the public test search API for each propeller hash, parses embedded `benchmark-data-table` arrays from matching test pages, and generates `docs/data/tyto_5in_static_prop_packet.csv`. This handoff packet has `4532` rows mirrored as `tyto_5in_static_packet_*`: `28` candidate 5-inch propellers, `89` unique public tests, `1548` normalized samples, plus compact static-thrust/current/RPM/torque summaries.
- `docs/scripts/analyze_mendeley_30in_prop_stand_packet.py` downloads the Mendeley public zip endpoint, expands the nested source archive, and generates `docs/data/mendeley_30in_prop_stand_packet.csv`, a 3593-row handoff packet mirrored as `mendeley_30in_prop_packet_*`. It parses `35` raw 100 Hz `.xls` files and `20` AVG/STD/MIN/MAX `.xlsx` workbooks for `P1..P5`, `500..3500 rpm`, thrust, torque, voltage, current, RPM, and electrical power.

Full Tyto 5-inch static scan:

| Packet metric | Value |
|---|---:|
| candidate 5-inch propellers | `28` |
| unique public tests | `89` |
| parsed static samples | `1548` |
| test max-thrust P50 / P90 | `9.51 / 10.74 N` |
| test max-current P50 / P90 | `26.5 / 34.5 A` |
| median test `T/omega^2` | `1.3924e-6 N/(rad/s)^2` |
| median `T/omega^2` / current `racingQuad.k` | `0.960x` |
| highest parsed test max thrust | `12.55 N` |
| highest parsed max / current `racingQuad` max rotor thrust | `0.929x` |

The systematic Tyto 5-inch scan supports the current `racingQuad` static thrust coefficient scale: the median per-test static coefficient is within about `4%` of the configured `1.45e-6 N/(rad/s)^2`. It does not by itself support the configured per-rotor thrust ceiling, because none of the parsed public 5-inch Tyto tests reaches the current `13.5 N` max-thrust setting; the strongest parsed test is [Five33 2207 Azure Vanover](https://database.tytorobotics.com/tests/x3nm/five33-2207-azure-vanover) at `12.55 N`.

Important limitation: the parsed public Tyto pages are static performance tables. `docs/data/tyto_5in_static_prop_packet.csv` intentionally marks `static_only_no_airspeed_or_wind_speed = 1`; use it for static RPM/thrust/current/torque scale, CT/CP sanity, and `T/omega^2`, not for forward-flow or wind-on-thrust-stand rolloff.

Selected extracted values:

| Tyto test | Hardware context | Max thrust | RPM | Current | Power | Fit k | Fit R2 | apDrone max-thrust ratio | apDrone k ratio |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Five33 2207 Azure Vanover | 2207 1980KV + Azure VanoverProp, about 6S voltage | 12.55 N | 25001 | 22.19 A | 537 W | 1.800e-6 | 0.9989 | 1.08x | 0.773x |
| LF40 2305 with 5040 prop | T-Motor LF40 2305 + Gemfan 5040 R, about 3S voltage | 4.60 N | 13363 | 13.51 A | 168 W | 2.349e-6 | 1.0000 | 2.94x | 0.593x |
| Tmotor-F80 | component metadata missing on page | 4.34 N | 19144 | 9.20 A | 108 W | 1.076e-6 | 0.9992 | 3.11x | 1.29x |
| 1700kvlumenierfolding | component metadata missing on page | 8.48 N | 16382 | 17.27 A | 215 W | 2.744e-6 | 0.9902 | 1.59x | 0.507x |

The Five33 2207 row is the closest high-output FPV-class static source in this selected Tyto subset: `apDrone()` at `13.5 N` is about `1.08x` that test's measured max thrust, while its current `1.3919e-6` thrust coefficient is about `0.77x` the Tyto fitted coefficient. That supports the APdrone max-thrust order of magnitude. The APdrone-specific YSIDO 2507 PDF rows below now add a closer motor-side bound, but the exact YSIDO 2507 plus Foxeer Donut 5145 toroidal prop curve remains unresolved.

Tyto torque/yaw coefficient extraction:

- Five33 2207 + Azure VanoverProp gives fitted `Q/T = 0.01139 m` with `R2 = 0.9999`; current `apDrone()` yaw torque ratio `0.01357 m` is `1.19x` this fit.
- T-Motor LF40 2305 + Gemfan 5040 R gives fitted `Q/T = 0.01459 m` with `R2 = 0.9996`; current `apDrone()` is `0.930x` this fit and current `racingQuad()` at `0.0140 m` is `0.960x`.
- The two selected Tyto pages without explicit prop/motor metadata give lower fitted ratios: Tmotor-F80 `0.01019 m` and 1700KV Lumenier folding `0.00943 m`.
- Across these selected Tyto FPV-class static tests, `Q/T` spans about `0.0094..0.0146 m`; this supports the current 5-inch yaw-torque order of magnitude and suggests `apDrone()` is near the high side of the selected Tyto range, not an obviously impossible value.
- In the generated torque CSV, recomputed mechanical power `abs(torque) * omega` matches Tyto's embedded `mechanical_power_w` for all nonzero mechanical-power samples, which is a direct unit sanity check for using Tyto torque as N*m.
- The Mendeley 30-inch packet is a clean large-prop hover/Flight Stand schema source, not an FPV coefficient source. The five no-intercept `T = k * omega^2` fits span `6.69e-4..8.47e-4 N/(rad/s)^2` with R2 `0.99956..0.99985`; raw 60 s files have RPM CV median `0.136%`, thrust CV median `0.821%`, and median raw `Q/T = 0.03899 m`. The fitted `k` values are `462..584x` current `racingQuad` and `14.9..18.8x` current `heavyLift`, so keep them as large-prop/static-test and measurement-noise anchors.

### Foxeer Donut 5145 public thrust images

Useful sources:

- [Official Foxeer Donut 5145 prop page](https://www.foxeer.com/foxeer-donut-5145-props-g-520)
- [Unmanned Tech Foxeer Donut 5145 test post](https://blog.unmanned.tech/quiet-skies-powerful-flights-exploring-the-efficiency-of-foxeer-donut-5145-propellers/)

Generated file:

- `docs/data/foxeer_donut_5145_thrust_image_reference.csv` records the official Foxeer Donut 5145 specs and manually transcribes the public Tyto Robotics screen-capture images for Flash 2207 1850KV at 24 V with DALPROP 5146.5, Foxeer Donut 5145, and DALPROP Nepal N2.

High-signal transcribed max points:

| Prop | Thrust | RPM | Current | Elec power | Torque | Derived k | Q/T |
|---|---:|---:|---:|---:|---:|---:|---:|
| DALPROP 5146.5 | 13.73 N | 30129 | 35.74 A | 848 W | 0.187 N*m | 1.380e-6 | 0.01362 m |
| Foxeer Donut 5145 | 13.56 N | 29802 | 34.83 A | 826 W | 0.184 N*m | 1.392e-6 | 0.01357 m |
| DALPROP Nepal N2 | 13.77 N | 30944 | 31.13 A | 739 W | 0.167 N*m | 1.311e-6 | 0.01213 m |

- The official Foxeer page lists Donut 5145 as `5.1 x 4.5 x 3`, 5 mm hub, PC material, `4.3 g`, and `2CW + 2CCW` per package, matching the APdrone component-spec prop identity.
- The public test uses Flash 2207 1850KV, not APdrone's YSIDO 2507 1800KV, so it is a prop-family anchor rather than an exact APdrone powertrain map. Still, it is the closest direct Foxeer Donut 5145 thrust/RPM/current source found so far.
- Current `apDrone()` at `maxRotorThrust = 13.5 N` is `0.996x` the Donut test max thrust, its `thrustCoefficient = 1.3919e-6` is essentially equal to the Donut derived coefficient, and its implied max RPM is `0.998x` the Donut test RPM. This strongly supports the current APdrone static-thrust/RPM order of magnitude.
- In the same public test, Donut 5145 produces `0.987x` the DALPROP 5146.5 thrust with `0.975x` current/power and `0.984x` torque. Compared with DALPROP Nepal N2 it produces `0.984x` thrust but uses `1.119x` current and `1.118x` electric power. Treat the efficiency comparison as image-transcribed, not raw Tyto data.

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

Rotor inflow time-scale note:

- `docs/data/rotor_inflow_time_scale_reference.csv` uses hover momentum theory `vi = sqrt(T/(2 rho A))` to derive `R/vi` and `2R/vi` wake-transit proxies for each preset, then compares the configured `rotorInflowTauSeconds` against those scales.
- This is a first-order sanity calculation for response magnitude, not a full Pitt-Peters/finite-state dynamic-inflow model. Use it to decide whether the configured lag is a bare aerodynamic transit scale or a deliberately damped gameplay/wake-history term.
- For `racingQuad`, the generated row gives `R/vi` around `0.0068 s` and `2R/vi` around `0.0136 s`; the configured `rotor_inflow_tau = 0.035 s` is several times slower than the one-radius wake-transit proxy.

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

### PX4 Gazebo Classic Typhoon H480

Source:

- [PX4 SITL Gazebo Classic Typhoon H480 SDF](https://raw.githubusercontent.com/PX4/PX4-SITL_gazebo-classic/main/models/typhoon_h480/typhoon_h480.sdf.jinja)

Extracted values:

| Field | Value |
|---|---:|
| base mass | 2.02 kg |
| base inertia | `ixx = 0.011`, `iyy = 0.015`, `izz = 0.021 kg m^2` |
| rotor count | 6 |
| rotor radius in collision model | 0.128 m |
| motorConstant | 8.54858e-6 |
| momentConstant | 0.06 m |
| timeConstantUp / timeConstantDown | 0.0125 s / 0.025 s |
| maxRotVelocity | 1500 rad/s |
| derived max thrust/weight | about 5.83 |
| derived hover omega / max omega | about 0.414 |

This is a useful open-source hexacopter thrust-margin anchor for `hexLift` and other larger presets. The SDF contains additional camera/gimbal/leg placeholder links, so treat the base mass/inertia row as the model's published simulator parameter rather than a weighed full aircraft.

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
- [NACA full-scale propeller compressibility survey](https://ntrs.nasa.gov/citations/19930091714) gives a practical propeller compressibility-loss onset band: effects on efficiency begin around tip speed `0.5..0.7` times sound speed for take-off/climb blade-angle range.
- [NACA 4(3)(08)-045 high-speed propeller test](https://ntrs.nasa.gov/citations/19930092056) reports serious efficiency losses above tip Mach about `0.91`, with losses of about `9..22%` per `0.1` Mach depending on advance-diameter ratio.

Values relevant to `DronePhysics`:

- Sea-level standard density is conventionally `1.225 kg/m^3`, matching `SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER`.
- Standard gravity `9.80665 m/s^2` matches the project preset value.
- Standard sea-level temperature `288.15 K`, lapse rate `0.0065 K/m`, and pressure exponent about `5.255` match `DroneEnvironment.standardAtmospherePressureRatio`.
- The speed-of-sound path `sqrt(gamma * R * T)` with `gamma = 1.4` and `R = 287.05 J/(kg*K)` matches `DroneEnvironment.speedOfSoundMetersPerSecond`.
- Sutherland-law constants for air around `S = 110.4 K` and `beta = 1.458e-6 kg/(s*m*K^0.5)` are consistent with the project's `AIR_SUTHERLAND_CONSTANT_KELVIN` and viscosity-ratio path.

Generated atmosphere file:

- `docs/data/atmosphere_reynolds_mach_summary.csv` compares current presets across ISA sea level, 25 C project default, cold sea level, hot sea level, 1500 m, 3000 m ISA, and 3000 m hot cases.
- `docs/data/tip_mach_compressibility_reference.csv` separates compressibility from the general atmosphere table. It records NACA threshold rows, the current `DronePhysics.rotorCompressibility*` curve at selected Mach values, current preset positions in several atmosphere cases, and UIUC static tip-Mach coverage.
- The same generated CSV includes UIUC static Re-proxy rows. Across the three 5-inch static files, the derived Re75-proxy sweep is roughly the same order as the current `racingQuad` high-altitude max-Re proxy, while the smaller `cinewhoop` proxy sits much lower; this supports keeping low-Re loss active for very small props and off for the current 5-inch racing preset unless a direct 5-inch degradation source says otherwise.
- In the hot 3000 m / 30 C stress case, the generated standard-atmosphere density ratio is `0.658`, so a simple `T proportional rho * omega^2` model needs `1.233x` RPM for the same thrust before battery/motor limits.
- In the cold sea-level / -10 C case, `racingQuad` max tip Mach rises to about `0.60` because speed of sound drops to about `325 m/s`.
- Current `racingQuad` project-default/cold max Mach values are about `0.56/0.60`, inside the NACA `0.5..0.7` loss-onset band. The current compressibility thrust scale is about `0.962/0.936`, so the onset direction and magnitude are plausible as a soft gameplay/physics penalty. The model is far below the NACA `0.91` serious-loss threshold for these rotational-only max-RPM cases.
- The public UIUC static files used in this packet top out around tip Mach `0.12..0.25`, so they cannot validate high-Mach compressibility losses; they remain Re/CT/CP trend anchors only.
- The current low-Reynolds loss path is size-gated: `racingQuad` stays at `0.000` low-Re loss in the hot 3000 m case, while `cinewhoop` reaches `1.000` in the same generated proxy calculation.

### Wind, turbulence, and gusts

Useful open-source/model anchors:

- [pyfly Dryden turbulence implementation](https://raw.githubusercontent.com/eivindeb/pyfly/master/pyfly/dryden.py) exposes the common low-altitude Dryden formulas using `L_w = h`, `L_u = L_v = h / (0.177 + 0.000823h)^1.2`, `sigma_w = 0.1 W20`, and `sigma_u = sigma_v = sigma_w / (0.177 + 0.000823h)^0.4` with altitude in feet.
- [Open UAV wind modeling survey](https://arxiv.org/abs/1905.09954) gives broader context for Dryden/Von Karman-style wind fields in small-UAV simulation.
- [ICAS 2020 "Numerical Simulation of Quadcopter Drone in Adverse Situations"](https://www.icas.org/icas_archive/ICAS2020/data/papers/ICAS2020_0482_paper.pdf) gives CFD hover CT changes under `10 m/s` gusts from multiple inflow directions.
- Generated file `docs/data/wind_gust_dryden_reference.csv` compares the current hybrid Dryden-plus-burble gust model against a Dryden low-altitude intensity reference at 6 m altitude. It also carries spectral-shape metrics from the pyfly/MIL-F-8785C transfer functions and `reference_icas_hover_gust_ct` rows from the ICAS gust tables.
- `docs/scripts/analyze_wind_gust_calibration_packet.py` generates `docs/data/wind_gust_calibration_packet.csv`, a 632-row narrow packet mirrored into `docs/data/fpv_model_validation_summary.csv` as `wind_gust_packet_*`.

Current high-signal outputs:

- For `wind = 10 m/s` and `dirtyAir = 1.5`, the current target-gust RMS is about `1.65 m/s` horizontal X and `0.84 m/s` vertical after combining a reduced dirty-air burble with the Dryden target.
- Against a low-altitude Dryden reference with `W20 = 10 m/s`, that is roughly `0.86x` longitudinal intensity and `0.84x` vertical intensity.
- At that same point, the current one-pole gust corner is about `1.37 Hz`, while the Dryden longitudinal/vertical poles are about `0.037/0.265 Hz`. At 1 Hz the current one-pole shape is roughly `22x` the Dryden longitudinal magnitude and `1.9x` the Dryden vertical magnitude, so the model can match RMS while still being spectrally too fast.
- ICAS 2020 hover CFD shows direct rotor-thrust effects can be much larger than a kinematic gust-field RMS check: at `4319 rpm`, a `10 m/s` -90 deg downdraft changes CT by about `-131%` while a 90 deg updraft changes CT by about `+120%`; at `6528 rpm`, the same endpoints are about `-68%` and `+42%`.
- The packet crosscheck keeps the separation explicit: current X RMS / Dryden longitudinal RMS spans `0.142..1.047x` across the generated grid, but current corner / Dryden longitudinal pole spans `13.6..78.8x`, and the ICAS CT envelope spans `-131..+120%`. Tune atmospheric turbulence, dirty-air burble, and rotor-inflow thrust perturbation as separate terms.
- The Dryden component now runs as a reproducible colored-noise process with the physical low-altitude length scales and sigma targets, while the reduced deterministic burble remains a tunable FPV obstacle/wake feel layer.

### Airframe inertia and body drag

Generated report sections:

- `Airframe inertia sanity` compares `sqrt(I/m)` radius of gyration for the current presets against RotorS, PX4, gym-pybullet-drones, and RotorPy.
- `Body drag sanity` computes 10 m/s and 20 m/s base drag from the current `linearDragCoefficient` and `bodyDragCoefficients`, then compares with RotorPy Hummingbird.
- Generated file `docs/data/airframe_drag_reference.csv` converts IMAV linear drag, UZH-FPV racing speed-envelope rows, RATM/Blackbird/AI-IO flight-log context rows, NASA wind-tunnel speed/dynamic-pressure context, open-source quadratic coefficients, UZH/RPG linear rotor-drag coefficients, ICAS CFD forward/freefall drag tables, and current presets into common force, effective-linear-k, equivalent-CdA, passive coastdown fields, `current_vs_imav_drag_calibration_target`, `current_vs_uzh_fpv_speed_envelope`, `current_vs_ratm_speed_floor`, `current_vs_flight_log_dataset_speed_envelope`, `current_vs_rpg_rotor_drag_*`, and `current_vs_icas_*` rows.
- Generated file `docs/data/airframe_drag_calibration_packet.csv` condenses airframe-drag source inventory, current `racingQuad` drag magnitude, IMAV/NASA/RPG comparisons, flight-envelope checks, APdrone log-fit caveats, AI-IO sample extremes, WAVELab Pelican system-ID context, and Manchester manoeuvrability thesis uncertainty anchors into one narrow table; it is mirrored into `docs/data/fpv_model_validation_summary.csv` as `airframe_drag_packet_*` categories.
- Generated file `docs/data/airframe_cda_guard_packet.csv` builds a narrower CdA and force-law guard from IMAV, NASA, RotorPy, RPG, ICAS, RATM, APdrone, and Manchester anchors; it is mirrored as `airframe_cda_guard_packet_*` categories and records the current `racingQuad` X/Z speed-specific equivalent `CdA = 0.033/0.037 m^2`, current scale-to-target values of `0.979x` for IMAV 10 m/s, `0.625x` for NASA bare-frame, and `1.338x` for NASA powered full-airframe.
- Generated file `docs/data/aiio_flight_log_sample_reference.csv` parses all discovered AI-IO v1.0 `processed_data/test/data.hdf5` slices into sample rate, speed, throttle, gyro/accelerometer dynamic RMS, reported `rotor_spd`, blade-pass-if-RPM, and current `racingQuad` drag-at-vmax comparison fields.
- Generated file `docs/data/aiio_rotor_speed_unit_reference.csv` audits the AI-IO preprocessing code path and confirms `rotor_spd` is copied from MAVROS `ESCStatus.rpm`; it then converts all extracted AI-IO test HDF5 rotor-speed rows into mechanical-RPM, motor-frequency, and three-blade blade-pass references against current project preset RPM.
- Generated file `docs/data/aiio_low_dynamic_rotor_rpm_reference.csv` filters extracted AI-IO HDF5 rows by low ground-truth speed, low ground-truth acceleration, and low calibrated gyro norm to create slow-flight/near-hover rotor-RPM reference bands.
- Generated file `docs/data/apdrone_mendeley_file_inventory.csv` inventories APdrone Mendeley dataset v2 public files by folder, size, type, public file ID, download URL, and usefulness class.
- Generated file `docs/data/apdrone_selected_flight_reference.csv` summarizes APdrone `Selected Flight.csv` and the Betaflight F722 dump: Blackbox metadata, Betaflight motor/filter/battery settings, sample rate, GPS speed, voltage, baro altitude, setpoint ranges, and current-column unit ambiguity.
- Generated file `docs/data/apdrone_inertia_reference.csv` transcribes the APdrone inertia PDF into component mass/inertia rows, derives radius of gyration and yaw/roll-pitch ratios, and compares the current `racingQuad` preset against the APdrone frame with project/source yaw-axis mapping made explicit.
- Generated file `docs/data/apdrone_pid_tuning_reference.csv` downloads and summarizes APdrone's nine small `RESULT_*` PID/MAE sweep CSVs into candidate rows, per-axis/per-stage best rows, staged-improvement rows, and Betaflight dump PID-versus-sweep-best comparisons.
- Generated file `docs/data/apdrone_component_specs_reference.csv` downloads five APdrone component datasheet PDFs and records structured prop/motor/battery/ESC/flight-controller specs from APdrone filename evidence plus official/vendor pages. The PDFs are cached but image-only under pypdf text extraction, so spec rows explicitly label whether a value comes from APdrone filename evidence, an official page, a comparable product page, or a derived calculation.
- Generated file `docs/data/apdrone_motor_thrust_pdf_reference.csv` manually transcribes the image-only APdrone 2507 1800KV motor PDF and matching YSIDO product page into motor specs, 23 thrust-test points, per-prop summaries, current `apDrone()` max-thrust/thrust-coefficient comparisons, power-law current/power fits, APdrone battery-log current inversions, and full-throttle back-EMF estimates of loaded RPM / `T/omega^2`.
- Generated file `docs/data/foxeer_donut_5145_thrust_image_reference.csv` records official Foxeer Donut 5145 prop specs and manually transcribes public Tyto Robotics screen-capture max points for Flash 2207 1850KV at 24 V with Donut 5145 plus two same-size prop comparisons.
- Generated file `docs/data/apdrone_powertrain_calibration_packet.csv` condenses the current `apDrone()` propulsion model, APdrone YSIDO motor PDF thrust/current rows, Foxeer Donut 5145 public image max point, selected Tyto static tests, Tyto torque-ratio fits, and the MQTB HQ5x4x3 current model into one narrow metric table. Those rows are mirrored into `docs/data/fpv_model_validation_summary.csv` as `apdrone_powertrain_*` categories.
- Generated file `docs/data/apdrone_control_response_reference.csv` scans APdrone Blackbox `setpoint[0..2]` and `gyroADC[0..2]`, downsamples logs to about `500 Hz`, searches `+/-80 ms` setpoint-to-gyro lag on active samples, and reports per-axis best lag/correlation/gain/error against current `apDrone()` control-latency and RC smoothing fields.
- Generated file `docs/data/apdrone_rate_envelope_reference.csv` separates APdrone's Betaflight Actual Rates target from the `rate_limit` clamp, scans APdrone Blackbox setpoint/gyro envelopes across all selected, open-field, urban, and battery-autonomy logs, and compares those envelopes with current `apDrone()` rate/expo/super-rate fields.
- Generated file `docs/data/apdrone_throttle_curve_reference.csv` separates Betaflight throttle-limit and thrust-linearization formulas from the current project throttle-power curve, scans APdrone Blackbox throttle/setpoint fields across all logs, and uses urban `eRPM[0..3]` rows as an APdrone-specific RPM sanity check.
- Generated file `docs/data/apdrone_urban_motor_rpm_reference.csv` decodes APdrone urban-environment `motor[0..3]` plus `eRPM[0..3]` telemetry, normalizes Betaflight `motorOutput = 158,2047`, converts 14-pole `eRPM/100` rows to mechanical RPM, fits RPM against normalized motor command, and records command/RPM lag plus first-order time-constant diagnostics.
- Generated file `docs/data/motor_response_dynamics_packet.csv` condenses RotorS/PX4 actuator-lag rows, Betaflight PR #12562 decoded RPM slew metrics, APdrone urban eRPM command/RPM timing, AI-IO low-dynamic hover-RPM scale, and current ESC/braking proxies into one narrow table; it is mirrored into `docs/data/fpv_model_validation_summary.csv` as `motor_response_packet_*` categories.
- Generated file `docs/data/apdrone_battery_autonomy_reference.csv` parses APdrone's `Max Power Time Flights.rar` and `Normal Power Time Flights.rar` Blackbox CSVs into ten flight rows plus scenario summaries for duration, voltage curve, throttle command, and candidate current scaling.
- Generated file `docs/data/betaflight_apdrone_current_unit_reference.csv` audits Betaflight 4.5 current-source units and Blackbox export paths, cross-checks APdrone battery-autonomy logs against the 1500 mAh pack capacity, and now adds the official Foxeer Reaper F4 65A ESC `Current Scaling = 70` hardware anchor.
- Generated file `docs/data/apdrone_flight_archive_reference.csv` parses APdrone's `Flight Data in Open Field.rar` and `Flight Data in Urban Environment.rar` into ten real-flight rows plus scenario summaries for duration, log rate, GPS speed, voltage, candidate current, throttle, gyro-vector activity, and setpoint-vs-gyro MAE.
- Generated file `docs/data/apdrone_open_field_speed_current_bins_reference.csv` bins every valid APdrone open-field GPS-speed sample into `0-2`, `2-5`, `5-8`, `8-12`, `12-16`, and `16-20 m/s` rows, with per-bin throttle, voltage, candidate current, gyro/accel activity, dynamic pressure, and current-preset drag projections.
- Generated file `docs/data/apdrone_flight_vs_model_reference.csv` compares APdrone selected/open-field/urban/battery-log speed and current summaries against the current `racingQuad()` and `apDrone()` Java presets for drag-limited level speed, thrust-margin usage, battery-current-limit usage, and MQTB 5-inch hover-current scale.
- Generated file `docs/data/apdrone_drag_speed_envelope_reference.csv` expands APdrone logged GPS speeds into a per-axis level-flight drag envelope: for each speed point, preset, and project X/Z axis it records required drag force, residual horizontal thrust margin, allowable maximum quadratic drag coefficient, and force-equivalent `CdA`.
- Generated file `docs/data/apdrone_open_field_speed_dynamics_reference.csv` collapses APdrone open-field Blackbox rows to unique GPS events, fits local 1 s and 2 s slopes of scalar GPS ground speed, derives GPS speed/course vector acceleration, labels accelerating/quasi-steady/decelerating events, and stores diagnostic apparent deceleration coefficients against current preset drag coefficients.
- Generated file `docs/data/apdrone_open_field_trim_candidate_reference.csv` filters those APdrone GPS-event dynamics into powered near-trim, relaxed-trim, high-speed straight-ish, and straight-ish deceleration candidates using speed, throttle, along-track acceleration, cross-track acceleration, and turn-rate thresholds.
- Generated file `docs/data/apdrone_drag_calibration_packet.csv` condenses the APdrone drag-speed envelope, GPS dynamics bins, and trim/deceleration filters into a narrow metric table, and those rows are mirrored into `docs/data/fpv_model_validation_summary.csv` as `apdrone_drag_*` categories. Treat it as envelope/diagnostic evidence, not a final `CdA` fit because attitude, wind, and horizontal thrust are not removed.
- Generated file `docs/data/apdrone_imu_noise_log_reference.csv` scans APdrone selected, battery-autonomy, open-field, and urban Blackbox CSVs for strict static and zero-throttle/low-motion windows, converts `gyroADC` and `accSmooth` with Blackbox header metadata, and compares segment noise/vibration RMS against current `apDrone()` sensor-noise fields.
- Generated file `docs/data/apdrone_baro_noise_log_reference.csv` scans the same APdrone Blackbox CSVs for strict static and zero-throttle/low-motion windows, interprets `baroAlt / 100` as meters, and records raw/detrended altitude noise, peak-to-peak variation, and linear drift against current barometer-noise and DPS310 pressure-noise anchors.
- Generated file `docs/data/apdrone_preset_source_match_reference.csv` audits current `apDrone()` fields against APdrone source evidence, including inertia-axis mapping, prop unit conversions, battery C-rating/capacity, Betaflight rate/filter/ESC settings, and fields that still lack a direct APdrone source.
- Generated file `docs/data/apdrone_article_performance_reference.csv` records APdrone article-level performance claims and compares them with the extracted battery-autonomy logs and current `apDrone()` static thrust margin.
- Generated file `docs/data/apdrone_battery_esr_proxy_reference.csv` derives APdrone log-based voltage-drop/internal-resistance proxies from the normal-power and max-power battery-autonomy summaries and compares them with the current `apDrone()` configured pack resistance.
- Generated file `docs/data/apdrone_battery_resistance_envelope.csv` expands that check across Betaflight literal current units, Foxeer official `Current Scaling = 70`, the round `raw_per_amp=20` capacity-consistency candidate, and scenario-specific 1500mAh capacity matches. It also compares the current `apDrone()` `0.016 ohm` pack resistance against the Oscar 4S charger-IR rows from `docs/data/high_c_lipo_reference.csv`.
- Generated file `docs/data/nasa_bare_airframe_drag_digitized_reference.csv` digitizes NASA multicopter wind-tunnel Fig. 30 bare-airframe `Drag/q` curves, converts ft^2 flat-plate area to SI `CdA`, and compares current `racingQuad`/`cinewhoop` quadratic drag against the small-quad 0 deg median.
- Generated file `docs/data/nasa_powered_full_airframe_drag_digitized_reference.csv` digitizes NASA multicopter wind-tunnel Figs. 18/20/22 powered full-airframe drag at `q = 0.48 lb/ft^2`, yaw/pitch 0 deg, converts lbf to N/equivalent `CdA`, and compares current `racingQuad`/`cinewhoop` quadratic drag against the small-quad mid-RPM median.
- Generated file `docs/data/nasa_powered_lift_digitized_reference.csv` digitizes NASA multicopter wind-tunnel Figs. 17/19/21 powered lift at `q = 0.48 lb/ft^2`, yaw/pitch 0 deg, converts lbf to N, effective `T/omega^2`, Table 1 `lift/weight`, and total-disk CT, then adds a cautious `racingQuad` same-RPM/required-RPM scale check.

Coordinate-system note:

- Current project presets use `Y` as the vertical/yaw axis, with rotor positions in `X/Z`.
- URDF/SDF/Python open-source models usually use `Z` as vertical/yaw.
- The generated report computes yaw inertia ratio using each source's own vertical axis.

Current high-signal outputs:

- `racingQuad` has radius of gyration `0.104/0.138/0.113 m` and yaw-axis inertia about `1.62x` the roll/pitch mean, close to RotorS Hummingbird and gym-pybullet Crazyflie ratios.
- APdrone's inertia PDF gives a compact FPV drone model with mass `0.6284 kg`, motor-center radius `0.095 m`, and source-axis inertia `Ixx/Iyy/Izz = 0.001346/0.001410/0.002480 kg m^2` where source `Z` is yaw. Mapping APdrone source `Z` yaw to this project's `Y` yaw, current `racingQuad` is `1.1 kg`, motor-center radius `0.18 m`, and inertia `0.012/0.021/0.014 kg m^2`; its radius of gyration is about `2.26x/2.38x/2.20x` APdrone on mapped X/Z/yaw axes, and its inertia is about `5.09x/5.67x/4.84x` APdrone after scaling APdrone to the same mass. Treat current `racingQuad` as a much larger/slower mass distribution than the APdrone compact frame unless that is an intentional gameplay scale choice.
- `racingQuad` runtime base drag at 10 m/s is `2.05 N` on body X and `2.25 N` on body Z before separated-flow additions; the old `18.25`/`18.45 N` figures are retained only as a linear-as-quadratic projection guard.
- The IMAV 2022 5-inch three-blade quadrotor fit gives the current `racingQuad` mass a low-speed drag reference of about `0.201 N/(m/s)`, or `2.01 N` at `10 m/s`. The generated airframe-drag table puts runtime X/Z drag at roughly `1.02x`/`1.12x` that mass-fit reference at `10 m/s`.
- The CdA guard packet makes the force-law issue explicit: at the IMAV 10 m/s target, keeping `linearDragCoefficient = 0.18 N/(m/s)` leaves a positive X-axis body-drag target of about `0.00207 N/(m/s)^2`, close to the current `0.0025`.
- Low-precision digitization of NASA Fig. 30 bare-airframe wind-tunnel `Drag/q` gives a small-quad 0 deg median of `0.225 ft^2`, equivalent to `CdA = 0.0209 m^2` and `1.28 N` drag at `10 m/s`. Runtime `racingQuad` X-axis base drag at 10 m/s is `2.05 N`, about `1.60x` that bare-frame wind-tunnel median. Treat this as a lower-bound body-drag anchor, because it excludes powered rotor/body interaction.
- Low-precision digitization of NASA Figs. 18/20/22 powered full-airframe drag at `q = 0.48 lb/ft^2` gives a mid-RPM small-quad median of `0.36 lbf` / `1.60 N` at about `6.13 m/s`, equivalent to `CdA = 0.0697 m^2`. Runtime `racingQuad` X-axis base drag at that same dynamic-pressure speed is `1.20 N`, about `0.75x` the powered wind-tunnel median.
- Low-precision digitization of NASA Figs. 17/19/21 powered lift at the same condition gives a mid-RPM small-quad median of `3.43 lbf` / `15.26 N` at median `5400 rpm`, about `1.14x` Table 1 nominal flight weight, with total-disk CT about `0.012` and effective per-rotor `T/omega^2 = 1.17e-5 N/(rad/s)^2`. Current `racingQuad` uses `1.45e-6`, so at `5400 rpm` it would make only `1.85 N` total thrust; matching the NASA median lift would require about `15488 rpm`, roughly `1.19x` current hover RPM and `0.53x` max RPM. This is a prop/vehicle-scale check, not a direct 5-inch thrust fit.
- UZH/RPG's FPV rotor-drag controller gives mass-normalized horizontal `k_drag_x/y = 0.544/0.386 1/s`. Mapping RPG x/y to this project's horizontal X/Z, `racingQuad` at `10 m/s` has RPG-derived forces `5.98/4.25 N`; current X/Z forces are `3.05x`/`4.35x` larger.
- ICAS 2020 CFD reports `1.076 N` drag for a `1.4 kg` quadrotor at `10.7 m/s` forward flight. At the same speed, current `racingQuad` X/Z base drag is `20.89/21.12 N`, about `19.4x`/`19.6x` that CFD value. Its vertical drag-only terminal-speed estimate is `7.70 m/s` versus the ICAS no-prop freefall value `29.31 m/s`.
- UZH-FPV's public SplitS1 sequence lists peak speed `26.79 m/s` (`96.4 km/h`). At that speed, runtime `racingQuad` X-axis drag is about `6.62 N`, `0.13x` the preset's level-flight horizontal thrust margin, requiring `0.23x` the configured max total thrust. Base drag no longer blocks the speed envelope; use these rows for residual aero, prop unloading, and control-limit fitting.
- Race Against the Machine adds a second high-speed FPV anchor: 36 open-design racing flights, `500 Hz` synchronized CSVs, `thrust[0-3]`, `vbat`, IMU, RC channels, and motion-capture pose/velocity. The local Range-extraction packet reads all 36 `_500hz_freq_sync.csv` files from the v3.0.0 split release without downloading the full `15.92 GiB` archive: `996104` telemetry rows, `13/36` flights at or above `21 m/s`, fastest sample `21.853 m/s`, and strongest P99 speed `21.259 m/s`. At the `21 m/s` README floor, runtime `racingQuad` X-axis drag is `4.88 N` and required total thrust is `0.22x` configured max. Its inferred KV is `1.16x` the RATM `2020KV` motor, while per-motor current limit is `0.41x` the RATM `55 A` ESC rating.
- The RATM acceleration/drag residual packet adds a powered-flight kinematic check on top of the speed-envelope rows. Across the six fastest 1 s windows, runtime drag at each vmax implies median `4.65 m/s^2` drag-only deceleration, while median observed absolute speed-rate at vmax is `5.24 m/s^2`; current drag-decel P95 is `0.08x` the observed-decel P95. This is not a passive coastdown fit, but it confirms the corrected runtime force law is in the same order as the powered-flight envelope.
- Blackbird and AI-IO add rotor-speed/IMU/motion-capture flight-log anchors below the RATM/UZH racing envelope. Blackbird has `168` flights / `17` trajectories up to `7 m/s`, with `100 Hz` IMU, about `190 Hz` motor speed, and `360 Hz` mocap; current `racingQuad` X-axis drag at `7 m/s` is `8.94 N` (`0.17x` horizontal thrust margin). The local Blackbird source-inventory packet parses `166` README preview links / `15` trajectory names and confirms the official downloader expects `9` CSV files including `blackbird_slash_rotor_rpm.csv`, `blackbird_slash_pwm.csv`, `blackbird_slash_state.csv`, and `groundTruthPoses.csv`; however, `12/12` sample raw CSV HEAD probes returned HTTP `502`. The follow-up mirror probe confirms the official root plus four sample raw CSVs still return `502`, while the available Academic Torrents metadata lists `747` files / `4.79 TB` but `0` CSV or rotor/PWM/state/groundTruth filename matches, so do not build a Blackbird residual fit until a true raw CSV mirror appears or the MIT endpoint recovers. AI-IO has `22` sequences over `7267 m` and `2636 s`, bidirectional-DShot rotor speed, BMI270 IMU, VICON, and high-speed manual flight up to `14 m/s`; current X-axis drag there is `35.77 N` (`0.68x` horizontal thrust margin). The local AI-IO HDF5 parse covers all `22` `test` slices; the fastest parsed slice is `manual_high/seq_4` with max speed `13.60 m/s` and current `racingQuad` X drag `33.74 N`, while `manual_high/seq_2` reaches max confirmed rotor speed `29146.8 rpm`.
- The AI-IO rotor-speed unit audit resolves the earlier `rotor_spd` ambiguity. In the AI-IO repository, `src/learning/data_management/prepare_datasets/our2.py` reads `/mavros/esc`, copies `ros_msg.esc_status[i].rpm` for all four motors, interpolates ESC data to IMU time, and writes those columns as HDF5 `rotor_spd`; `src/learning/network/model.py` then squares `rotor_spd` before empirical normalization, with no rad/s conversion in that path. Across the extracted test HDF5 files, the maximum confirmed speed is `29146.8 rpm`, essentially `1.0003x` the current `racingQuad` max RPM and `0.980x` the current `apDrone` max RPM implied by `maxRotorThrust = 13.5 N` and `thrustCoefficient = 1.3919e-6`. For a three-blade FPV prop this is `1457.3 Hz` blade-pass, but AI-IO's `100 Hz` HDF5 cadence is telemetry/feature cadence, not a vibration bandwidth capable of resolving that kHz content directly.
- The AI-IO low-dynamic filter adds a hover-RPM scale check from flight logs. The strict criterion (`speed <= 1.0 m/s`, ground-truth acceleration `<= 1.5 m/s^2`, gyro norm `<= 0.5 rad/s`) keeps `37,089` samples over `370.9 s` from `20` files and gives mean/P50/P95 rotor speed `13642/13792/14119 rpm`; its mean is `1.05x` current `racingQuad` hover RPM and `1.39x` current `apDrone` hover RPM. The relaxed criterion (`speed <= 2.0 m/s`, acceleration `<= 3.0 m/s^2`, gyro `<= 1.0 rad/s`) keeps `66,017` samples over `660.2 s` from `21` files and gives mean/P50/P95 `13703/13797/14264 rpm`. Treat this as a same-order flight telemetry anchor, not as an APdrone-specific hover fit, because AI-IO's vehicle mass/propulsion setup is not identical to the APdrone frame.
- APdrone adds a current Betaflight 4.5 low-speed FPV log/config anchor plus a directly useful inertia calculation PDF. Its selected open-field Blackbox CSV has `166936` rows over `83.32 s` at about `2004 Hz`, GPS speed max `5.75 m/s`, median pack voltage `15.93 V`, and a Betaflight dump with DShot600, bidirectional DShot on, `motor_poles = 14`, `motor_kv = 1960`, `bat_capacity = 1500`, `blackbox_disable_motors = ON`, and `blackbox_disable_rpm = ON`. Use the inertia PDF for airframe mass-distribution checks and the log/config for controller/filter/battery-voltage and low-speed envelope checks; do not use the selected flight for direct motor/RPM dynamics unless the larger archives expose motor/RPM logs.
- APdrone's real-flight archives broaden the selected-flight view. The five open-field CSVs average `32.7 s` each at about `2004 Hz`, all have GPS speed rows, and the fastest logged GPS speed is `18.72 m/s`; using the battery-derived `raw_per_amp=20` candidate gives mean current about `7.9 A` and mean throttle command about `40.2`. The five urban-environment CSVs average `144.7 s`, mix about `2006 Hz` and `4007 Hz` log rates, have no valid GPS speed rows, and show mean throttle about `45.5` with candidate mean current about `7.8 A`. Treat Open Field as the speed-envelope source and Urban as a longer control/sensor/electrical activity source.
- The APdrone open-field speed-bin table shows the highest-speed rows are short dynamic segments, not a steady full-throttle cruise fit. The `16-20 m/s` bin has `2991` samples over about `1.49 s`, mostly from `Flight_2.csv` (`2793` samples / `1.39 s`) plus a small `Flight_1.csv` segment (`198` samples / `0.10 s`). In that bin, mean/max GPS speed is `17.40/18.72 m/s`, candidate current mean/P95 is `14.51/19.85 A`, and throttle mean/P95 is `60.6/67.4`. The `12-16 m/s` bin is still only about `4.17 s`, with mean/max speed `13.90/15.86 m/s`, candidate current mean/P95 `10.57/19.0 A`, and throttle mean/P95 `52.8/67.1`. Use these bins to filter future drag fitting: they are useful speed-envelope evidence, but acceleration, wind, and attitude must be removed before treating them as steady force-balance data.
- The GPS-event dynamics table sharpens that caveat. After collapsing repeated high-rate rows to unique GPS updates, the open-field logs contain `1284` GPS events. The `16-20 m/s` bin has only `13` events from `2` flights, with mean/median scalar speed slope `-0.46/+2.45 m/s^2`, `61.5%` accelerating, `7.7%` quasi-steady, and `30.8%` decelerating. GPS speed/course vector fitting shows median/P90 vector acceleration `3.10/7.24 m/s^2`, median/P90 absolute cross-track acceleration `1.48/2.33 m/s^2`, and median/P90 absolute turn rate `4.97/7.23 deg/s`, so these are still maneuvering points rather than clean straight-line trim points. The fastest points alternate between deceleration and acceleration: `18.72 m/s` and `18.63 m/s` are decelerating, while `18.40 m/s` is accelerating. Mean throttle in that bin is still about `60.5%`, so the table's apparent deceleration coefficient `-m*a/v^2` is only a diagnostic flag; its decelerating-event median is `0.011 kg/m`, about `0.09x` current `apDrone()` X drag, but that assumes zero horizontal thrust and zero wind. Do not treat it as a fitted `CdA`.
- The trim-candidate filter makes the drag-fitting limitation explicit. With powered samples (`throttle >= 20`), the strict near-trim filter (`speed >= 8 m/s`, `|along accel| <= 0.5 m/s^2`, `|cross accel| <= 0.75 m/s^2`, `|turn rate| <= 5 deg/s`) finds `0` events; the same strict filter above `12 m/s` also finds `0`. A relaxed trim filter above `12 m/s` finds only `2` events, at `16.02` and `18.09 m/s`, from two flights; the `18.09 m/s` event is still accelerating at `0.57 m/s^2`, while the `16.02 m/s` event is nearly scalar-steady but has `0.86 m/s^2` cross-track acceleration. The straight-ish high-speed filter above `16 m/s` finds `10` events, but median along-track acceleration is `+2.49 m/s^2`, so those are envelope evidence, not trim. Straight-ish deceleration events above `16 m/s` are only `3` events from `Flight_2.csv`; their median diagnostic `-m*a/v^2` coefficient is `0.00841 kg/m`, about `0.069x` current `apDrone()` X drag and `0.046x` current `racingQuad()` X drag under the zero-thrust/no-wind assumption. This is a strong warning against fitting physical drag from APdrone open-field high-speed points without attitude/wind or a controlled coastdown.
- The APdrone article reports about `8 min` standard flight time and `0.98 kg` maximum load capacity. The normal-power battery-autonomy logs average `511.1 s`, or `1.065x` the article's eight-minute scale; max-power logs average `205.9 s`, or `0.429x`. With current `apDrone()` mass `0.6284 kg` and max total thrust `54 N`, the article maximum payload gives gross mass `1.6084 kg` and static thrust-to-weight about `3.42`; that supports the payload thrust-margin order of magnitude, while leaving thermal, controllability, and exact motor-prop validation separate.
- The APdrone flight-vs-model table turns that `18.72 m/s` open-field GPS point into a simple level-flight drag check. Current `racingQuad()` has drag-limited level speeds of about `17.03/16.93 m/s` on project X/Z axes, so the APdrone fastest GPS point is `1.10x` above that limit and consumes `1.21x/1.22x` the available horizontal thrust margin under the current quadratic base-drag equation. Current `apDrone()` has drag-limited level speeds of about `20.99/20.85 m/s`, so the same APdrone point is `0.89x/0.90x` of its limit and consumes about `0.80x/0.81x` the margin. This supports treating `racingQuad()` as over-damped for APdrone-class high-speed flight unless the damping is intentional gameplay feel, while the new `apDrone()` preset is much closer to the observed APdrone envelope.
- The APdrone drag-speed-envelope table remains useful for APdrone open-field diagnostics, but its older `racingQuad()` force-equivalent rows predate the runtime linear-plus-quadratic correction. Use the corrected airframe drag and CdA guard packets for `racingQuad` drag conclusions until the APdrone envelope packet is regenerated with the same force-law helper.
- The APdrone drag calibration packet makes the same constraints one-table accessible for the other agent: current `apDrone()` X drag coefficient is `0.1218 N/(m/s)^2` with a drag-limited level speed of `20.99 m/s`, while `racingQuad()` X is `0.1825 N/(m/s)^2` with `17.03 m/s`. Its `16-20 m/s` GPS dynamic speed bin has only `13` events from `2` flights; the decelerating-event apparent coefficient P50 is `0.0111 N/(m/s)^2`, only `0.091x` current `apDrone()` X under the zero-horizontal-thrust/no-wind assumption. The straight-ish deceleration filter above `16 m/s` is even thinner at `3` events from `1` file, with median diagnostic coefficient `0.00841 N/(m/s)^2` (`0.069x` `apDrone()` X, `0.046x` `racingQuad()` X). Use these rows to reject overconfident drag fits, not to fit final airframe drag directly.
- The `apDrone()` source-match table confirms exact or unit-converted matches for APdrone mass `0.6284 kg`, source-X/project-X inertia `0.001346 kg m^2`, source-Z/project-Y yaw inertia `0.002480 kg m^2`, source-Y/project-Z inertia `0.001410 kg m^2`, motor-center radius `0.095 m`, 5.1 inch prop radius `0.06477 m`, 3 blades, 4.5/5.1 pitch-to-diameter ratio, 1.5 Ah battery capacity, 150 A battery C-rating claim, 125 Hz gyro LPF, 480 Hz motor PWM rate, DSHOT600, and `670 deg/s` pitch/roll/yaw selected Actual-rate targets. The Betaflight `rate_limit = 1998 deg/s` remains a clamp, not the current project full-stick target. The new motor-PDF table reduces the previous `13.5 N` max-thrust gap: the PDF headline claim is `1488 g = 14.59 N`, while visible thrust-table maxima are `12.83 N` for 4S/7056 3R, `12.03 N` for 4S/6045R, `13.67 N` for 6S/5043, and `14.16 N` for 6S/5045. Remaining APdrone-specific gaps are direct lab battery ESR/source data for `0.016 ohm`, drag coefficients, and the exact YSIDO 2507 plus Foxeer Donut 5145 thrust/RPM curve needed to pin `1.3919e-6` directly.
- The APdrone powertrain calibration packet makes the propulsion evidence one-table accessible. Current `apDrone()` max rotor thrust is `0.953..1.122x` the four visible APdrone motor-PDF prop maxima, the 4S loaded-RPM back-EMF estimate brackets current `k` at `0.761..1.075x`, and the public Foxeer Donut 5145 image max point gives `apDrone()` thrust-coefficient ratio `1.000x`, max-RPM ratio `0.998x`, and max-thrust ratio `0.996x`. Treat the Donut point as the closest public prop-family anchor, not as an exact YSIDO 2507 + Donut 5145 curve.
- The current `apDrone()` pair `maxRotorThrust = 13.5 N` and `thrustCoefficient = 1.3919e-6` implies max rotor speed `29740 rpm` and hover speed `10047 rpm`. Against the motor PDF's `1800KV`, that max RPM is `0.984x` a 4S full-charge `16.8 V` no-load speed and `1.116x` a 4S nominal `14.8 V` no-load speed. Against the Betaflight dump's `motor_kv = 1960`, the same max RPM is `0.903x` full-charge no-load and `1.025x` nominal no-load. The preset's `150 A` pack limit is `37.5 A/motor`, or `0.893x` the PDF's `42 A` max continuous current. In short: the current max-thrust/current limits are plausible on full-charge 4S and especially under the Betaflight 1960KV metadata, but the exact max RPM remains sensitive to the unresolved 1800KV-vs-1960KV source mismatch.
- A separate full-throttle back-EMF estimate uses the PDF's `0.0586 ohm` motor resistance and `RPM ~= KV*(V - I*R)` at the visible 100% throttle rows. This is not measured RPM, but it adds a direct sanity check for `thrustCoefficient`. On the two 4S full-throttle rows (`7056 3R` and `6045R`), the mean estimated `T/omega^2` is `1.68e-6` using the PDF's `1800KV`, so current `1.3919e-6` is `0.829x` that estimate; using Betaflight's `1960KV`, the same rows give `1.42e-6`, so current `1.3919e-6` is `0.980x`. On the 6S 5-inch `5043/5045` rows, the same method gives much lower `6.54e-7..7.75e-7` estimates, so do not treat the back-EMF method as an exact prop coefficient. It is best read as evidence that current `1.3919e-6` is very plausible for APdrone's 4S/Betaflight-KV interpretation, while the exact Foxeer Donut 5145 curve still needs measured RPM.
- Fitting the visible 4S YSIDO rows (`7056 3R` and `6045R`) gives `I = 0.624 * T^1.545` with log-space `R2 = 0.9906` and RMS relative current error about `3.94%` over the bench range. Projecting current `apDrone()` max thrust through that 4S fit gives about `139 A` total and `2250 W`, or `0.928x` the configured `150 A` pack limit; this is a slight thrust extrapolation because `13.5 N` is just above the 4S table maxima. Hover projection gives only about `4.87 A` and is below the bench thrust range, so treat it as a low-current extrapolation. Inverting the same 4S fit with APdrone battery-log currents gives normal-power mean/P95 thrust-to-weight about `1.65x/1.84x` and max-power mean/P95 about `2.98x/4.07x`; only the max-power P95 current is inside the bench current range, while the mean currents are below-range extrapolations.
- The APdrone `amperageLatest` column is now split into source-unit and calibration evidence. Betaflight 4.5 source defines current-meter `amperageLatest` as centiamps, converts ADC voltage to centiamps using `currentMeterScale`, writes `getAmperageLatest()` into Blackbox, and the Blackbox Log Viewer displays Betaflight 3.1.7+ logs as `value/100 A`. APdrone battery logs all report Betaflight 4.5.0 with `currentMeterScale = 400` and offset `0`; taken literally as `raw/100`, the max-power and normal-power tests integrate only about `296 mAh` and `264 mAh` per flight. Matching one 1500 mAh pack per flight requires `raw_per_amp = 19.72` for max-power and `17.61` for normal-power, equivalent to `ibata_scale` about `78.9` and `70.4`. The official Foxeer Reaper F4 65A ESC page lists `Current Scaling = 70`, so the normal-power capacity fit is essentially exact and the max-power fit is only `1.13x` that official scale. Treat `raw_per_amp ~= 18-20` as an APdrone current-sensor calibration correction caused by the logged/configured `400` scale, not a new Blackbox field unit.
- On current scale, APdrone max-power battery tests at the `raw/20` candidate average `25.9 A` with mean P95 `42.1 A`. Those are `0.29x/0.47x` of the `racingQuad()` 90 A battery limit and `0.17x/0.28x` of the `apDrone()` 150 A limit. The MQTB HQ v1s 5x4x3 fit estimates hover current at `18.04 A` for `racingQuad()` and `9.43 A` for `apDrone()`; APdrone normal-power mean current is `9.30 A`, essentially one APdrone-hover equivalent, while max-power mean/P95 is about `2.75x/4.46x` the `apDrone()` hover estimate.
- APdrone log-derived resistance proxies put the configured `apDrone()` pack resistance `0.016 ohm` in a plausible but low fresh-pack range. The new envelope table shows why current scale matters: Betaflight literal `raw/100` would imply an unrealistic `0.1106 ohm` pack proxy, Foxeer official `scale=70` mapped from logged `scale=400` implies `0.0194 ohm`, the round `raw_per_amp=20` capacity candidate implies `0.0221 ohm`, and scenario-specific 1500mAh capacity matching implies `0.0234 ohm`. Current `0.016 ohm` is `0.68..0.83x` of the three capacity/hardware-supported cross-scenario proxies and `0.58..0.80x` of the Oscar measured 4S charger-IR rows. Treat this as a direct APdrone voltage-log sanity check, not a laboratory ESR measurement.
- APdrone's PID sweep CSVs provide a Betaflight-style tuning anchor, not a direct Java `PidGains` unit conversion. The generated best P+I rows are pitch `Kp/Ki = 135/155` with mean MAE `1.84`, roll `65/85` with mean MAE `2.57`, and yaw `140/100` with mean MAE `2.37`. The D-sweep best rows are pitch/roll/yaw `Kd = 24/40/50` with mean MAE `5.08/13.02/7.01` at those fixed Kp/Ki values. In the Betaflight dump, `p_*` and `i_*` match the sweep bests, `d_min_pitch/roll/yaw = 24/40/50` matches the D-sweep bests, and `d_pitch/roll/yaw = 90/60/90` is higher; treat this as Betaflight dynamic-D context, not a mismatch. Because the D-sweep MAE files are not monotonic versus the P+I-stage files, use the stage-internal best rows rather than cross-stage MAE ratios unless raw trial conditions are checked.
- APdrone's control-response table adds a direct Blackbox timing sanity check. It interprets `setpoint[0]/gyroADC[0]` as roll, `[1]` as pitch, `[2]` as yaw, downsamples to about `500 Hz`, and searches the lag that maximizes active-sample correlation. Across all reliable file/axis rows, roll best-lag P10/P50/P90 is about `20/24/38 ms` with median absolute correlation `0.93`, pitch is about `11/14/16 ms` with median absolute correlation `0.94`, and yaw is about `6/16/46 ms` over only `6` reliable rows. Median fitted gyro/setpoint gain is about `1.04` on roll/pitch and `1.01` on yaw. This supports the current `apDrone()` timing order of magnitude (`10 ms` control latency, `10 ms` RC command latency, `12 ms` RC smoothing), but do not read the correlation lag as pure plant or motor delay; it is closed-loop setpoint/gyro timing and is sensitive to maneuvers, axis coupling, and low-activity yaw segments.
- APdrone's rate-envelope table now resolves the earlier rate-source mismatch. Betaflight 4.5 source maps `rates_type = 3` to `ACTUAL`, where `rc_rate = 7` gives center sensitivity `70 deg/s` and `rates * 10` gives the full-stick Actual target; `rate_limit = 1998 deg/s` is only a final clamp. The APdrone dump and open-field logs use `rates = 30` (`300 deg/s`), while battery and urban logs use `rates = 67` (`670 deg/s`). Current `apDrone()` now uses `maxRate = 670 deg/s`, `rateExpo = 0.5`, and `rateSuper = 0.791044776119403`, giving center sensitivity `70 deg/s`, exactly matching the urban/battery Actual target and sitting `2.23x` above the dump/open-field target. Across the 20 archive logs excluding the selected-flight duplicate (`4471.5 s`), setpoint P99 is about `49/48/28 deg/s` on pitch/roll/yaw (`0.073/0.072/0.042x` current 670), while exact open-field transient maxima reach `413/367/218 deg/s` (`0.616/0.548/0.325x` current 670). Treat `1998 deg/s` as a Betaflight safety clamp only, not as an APdrone normal full-stick rate.
- APdrone's throttle-curve table adds a direct check of the current hover-throttle calibration. Current `apDrone()` has hover thrust fraction `0.11412`, reference throttle `0.54396`, and therefore `throttleCommandCurveExponent = 3.565`; this maps `0.54396` throttle to exactly `1.00x` static thrust-to-weight and about `10047 rpm`. The normal-power battery-autonomy logs have throttle mean/P50/P95 `0.54396/0.547/0.557`, so they strongly support the chosen hover/reference throttle. The max-power battery logs sit at P50/P95 `1.0/1.0`, which the current static model maps to `8.76x` thrust-to-weight and `29740 rpm`; read that as command-envelope evidence, not sustained available thrust, because current, voltage sag, thermal limiting, and prop unloading are separate.
- The same throttle table flags a low-throttle RPM mismatch in urban logs. Urban flights use `thrust_linear = 20`, expose `motor[0..3]` and `eRPM[0..3]`, and have throttle P50/P95 `0.484/0.535`; current `apDrone()` maps those throttle values to project RPM P50/P95 about `8158/9753`, while the decoded 14-pole eRPM/100 average mechanical RPM has P50/P95 about `12607/13079`, or `1.53x/2.26x` the project RPM at the same logged throttle. This does not invalidate the hover-throttle anchor because motor mixing, thrust linearization, attitude/altitude demand, and Betaflight mixer outputs differ from a pure zero-mix throttle command, but it warns against treating the current power-law throttle curve as an exact Betaflight motor/RPM curve below hover without motor telemetry fitting.
- The urban motor-RPM table turns that warning into a motor-output fit. Across the five APdrone urban flights, deterministic all-motor sampling keeps `426649` points with valid eRPM fraction `0.965`; normalized Betaflight motor command P50/P95 is `0.470/0.557`, decoded mechanical RPM P50/P95 is `12429/14100`, and those speeds are `0.418/0.474x` current `apDrone()` max RPM but `1.24/1.40x` current hover RPM. Projecting the logged RPM through the current static `T = k * omega^2` model gives same-RPM quad thrust-to-weight P50/P95 `1.53/1.97`, so these urban segments are mostly above static hover despite only mid-range motor commands. The all-motor linear fit is `rpm ~= 18811 * motor_norm + 3546` with `R2 = 0.865`, extrapolated `norm = 1` speed `22357 rpm`, and hover-RPM motor command `0.346`; the log-space power fit is `rpm_fraction ~= 0.685 * motor_norm^0.657` with `R2 = 0.881`. Effective loaded KV P50/P95 is `1748/1946 rpm/V`, between the motor PDF's `1800KV` and the Betaflight dump's `1960KV` in the high-percentile band. Treat the fitted curve as a loaded in-flight urban command/RPM map, not a bench full-throttle prop curve.
- Urban motor-RPM timing diagnostics should stay diagnostic, not become hard plant constants. File-level all-motor command/RPM level-correlation lag is about `40-80 ms` and delta-correlation lag about `40-64 ms` with correlations `0.94-0.98`, while first-order tau candidates have P50 about `2.9-4.1 ms` and P90 about `8.1-34.0 ms`. The split indicates that closed-loop command alignment, logging/downsampling, and maneuver content dominate the correlation lag; use the tau band only as a weak ESC/motor response sanity check until controlled step data is available.
- APdrone's component specs add hardware bounds for the same airframe: Foxeer Donut `5145` props are `5.1 x 4.5 in`, `4.3 g`, PC material, with a vendor page listing `3` blades; the APdrone battery filename gives `4S 1500 mAh 14.8 V 100C`, so the claimed C-rating current is `150 A`; the Foxeer Reaper F4 ESC official page gives `65 A x 4` continuous and `100 A x 4` burst with DShot600 support; the FC source is an F722/MPU6000/8S-class board with DPS310 barometer and 16 MB flash. The APdrone motor PDF is a YSIDO `2507 1800KV` image sheet: it lists `3-5S`/`3-6S` support depending page/source, `12N14P`, `0.0586 ohm` motor resistance, `42 A` max continuous current, `840 W`, `39-43.2 g`, and `1488 g` headline max thrust. The Betaflight dump still sets `motor_kv = 1960`, a `1.089x` difference versus the motor PDF's `1800KV`; keep KV unresolved until flight/RPM telemetry or exact motor firmware usage clarifies whether this is only Betaflight metadata.
- At that same 10 m/s reference point, matching IMAV with the runtime linear-plus-quadratic drag form would require a body-X coefficient about `0.00207 N/(m/s)^2` if the shared linear term is left unchanged; reducing only `bodyDragCoefficients` is now a valid local tuning lever for the low-speed IMAV target.
- Passive coastdown from 20 to 5 m/s makes the scale easier to tune against: the IMAV mass-fit reference takes about `7.60 s` and `82.2 m`, while current `racingQuad` X/Z drag slows in about `7.38/6.71 s` and `78.3/70.3 m`; initial 20 m/s decel is about `1.15x`/`1.35x` the IMAV fit.
- Against the RPG linear rotor-drag shape, `racingQuad` current X-axis 20 to 5 m/s coastdown time is `2.90x` the RPG-derived time, with `0.38x` the initial 20 m/s deceleration.
- The airframe drag packet makes those corrected numbers one-table accessible: current `racingQuad` X drag is `2.05 N` at `10 m/s` and `4.6 N` at `20 m/s`; it is `1.02x` the IMAV 10 m/s mass-fit, `1.60x` the NASA bare-frame 10 m/s median, `0.75x` the NASA powered full-airframe reference-speed median, and `0.34x`/`0.38x` the RPG rotor-drag shape at 10/20 m/s.
- New source triage: WAVELab's AscTec Pelican dataset gives `54` indoor Vicon/motor-speed/motor-command flights for dynamics identification, while the Manchester manoeuvrability thesis reports axial-flight drag tests within `+/-6%` of wind-tunnel drag coefficients and a drag build-up model with `+/-20%` confidence interval. Both are method/data anchors, not direct FPV coastdown fits.
- The current drag path therefore looks very strong if interpreted as physical CdA; if it is a gameplay/stability damper, document it separately from measured aerodynamic drag.

Open-source drag and speed-envelope anchors:

- [IMAV 2022 "Evaluation of drag coefficient for a quadrotor model"](https://www.imavs.org/papers/2022/4.pdf) reports a 5-inch, three-blade quadrotor drag fit `D = kV`, `k = 0.105 + 0.087m`, over a `0.547..1.067 kg` mass range and `2.5..12.5 m/s` airspeed range.
- [UZH-FPV Drone Racing Dataset](https://fpv.ifi.uzh.ch/datasets/) and its [ICRA 2019 paper](https://rpg.ifi.uzh.ch/docs/ICRA19_Delmerico.pdf) provide FPV racing sequence metadata with camera/IMU/ground-truth context. Use the sequence speeds as flight-envelope anchors, not direct force-balance drag measurements.
- [Race Against the Machine high-speed FPV dataset](https://github.com/tii-racing/drone-racing-dataset), its [v3.0.0 release](https://github.com/tii-racing/drone-racing-dataset/releases/tag/v3.0.0), [BOM](https://raw.githubusercontent.com/tii-racing/drone-racing-dataset/main/quadrotor/bom.md), [Betaflight backup](https://raw.githubusercontent.com/tii-racing/drone-racing-dataset/main/quadrotor/BTFL_cli_backup.txt), and [IEEE RA-L paper DOI](https://doi.org/10.1109/LRA.2024.3371288) provide high-speed FPV logs and hardware context. `docs/scripts/analyze_ratm_selective_zip_sample_packet.py` uses HTTP Range extraction to generate `docs/data/ratm_500hz_sync_file_inventory.csv`, `docs/data/ratm_high_speed_flight_metrics.csv`, `docs/data/ratm_high_speed_window_reference.csv`, and `docs/data/ratm_high_speed_flight_packet.csv`; use those files for trajectory/control/battery-voltage fitting, and treat peak-speed comparisons as flight-envelope feasibility checks rather than isolated drag coefficients.
- `docs/scripts/analyze_ratm_accel_drag_residual_packet.py` reuses `docs/data/ratm_high_speed_window_reference.csv` and generates `docs/data/ratm_accel_drag_residual_packet.csv`, a 640-row residual-feasibility packet mirrored as `ratm_accel_drag_packet_*`. It central-differences the six fastest 1 s windows, compares observed speed-rate with current `racingQuad` drag-deceleration demand, and keeps decimated traceability samples around each vmax event.
- `docs/scripts/analyze_ratm_thrust_vector_feasibility_packet.py` reuses the same RATM windows and generates `docs/data/ratm_thrust_vector_feasibility_packet.csv`, a `205`-row attitude/thrust-vector feasibility packet mirrored as `ratm_thrust_vector_packet_*`. It projects the logged body-Z axis onto the velocity direction with a sign-safe absolute projection, combines that with mean `thrust[0..3]` as a command proxy, and compares it with current `racingQuad` drag/max-thrust demand.
- RATM thrust-vector takeaway: across `827` samples at or above `21 m/s`, the high-speed command-projection proxy median is `0.69x` the corrected drag/max-thrust demand; at the six vmax samples, the median proxy is `0.42x`. This is not a calibrated force fit, but it is a useful sign/scale guard for attitude and thrust semantics.
- [MIT Blackbird Dataset](https://github.com/mit-aera/Blackbird-Dataset), [README](https://raw.githubusercontent.com/mit-aera/Blackbird-Dataset/master/README.md), [sequenceDownloader.py](https://raw.githubusercontent.com/mit-aera/Blackbird-Dataset/master/fileTreeUtilities/sequenceDownloader.py), and [ISER arXiv paper](https://arxiv.org/abs/1810.01987) provide aggressive-flight trajectories with IMU, motor RPM/PWM messages, motion-capture ground truth, and rendered camera data. The full dataset is large (`4.9 TB`), so use it selectively for RPM/IMU/trajectory response rather than broad local mirroring. `docs/scripts/analyze_blackbird_source_inventory_packet.py` generates `docs/data/blackbird_source_inventory_packet.csv`, a `209`-row inventory mirrored as `blackbird_source_inventory_packet_*`: it parses `166` README preview-flight links, `14` expected file/schema rows from the downloader, and `12` sample raw CSV download probes. At the time of extraction all `12` sample probes failed with HTTP `502`, so this is an inventory/download-status packet, not a residual-force data packet.
- `docs/scripts/analyze_blackbird_mirror_probe_packet.py` generates `docs/data/blackbird_mirror_probe_packet.csv`, a `58`-row availability/mirror packet mirrored as `blackbird_mirror_probe_packet_*`. It records the official root plus four sample raw CSV probes at HTTP `502`, `7` open GitHub issues whose titles mention download/server/link/access problems, a reachable [Academic Torrents Blackbird entry](https://academictorrents.com/details/eb542a231dbeb2125e4ec88ddd18841a867c2656), and OpenDataLab page probes. The Academic Torrents `.torrent` metadata is useful as a mirror warning: it lists `747` files and `4.7896 TB`, but only `560` tar archives and `187` mp4 files, with `0` CSV files and `0` rotor/PWM/state/groundTruth/IMU/mocap filename matches.
- [AI-IO](https://github.com/SJTU-ViSYS-team/AI-IO), its [paper page](https://arxiv.org/html/2603.00597v1), and [v1.0 release](https://github.com/SJTU-ViSYS-team/AI-IO/releases/tag/v1.0) provide a smaller (`346 MiB`) IMU/rotor-speed/VICON dataset with high-speed manual flight up to `14 m/s`. The release archive is cached under `docs/data/raw/aiio/`, all discovered test HDF5 slices are parsed into `docs/data/aiio_flight_log_sample_reference.csv`, `docs/data/aiio_rotor_speed_unit_reference.csv` confirms the HDF5 `rotor_spd` field is MAVROS ESC telemetry in mechanical RPM, and `docs/data/aiio_low_dynamic_rotor_rpm_reference.csv` extracts low-dynamic slow-flight RPM bands.
- [NeuroBEM](https://rpg.ifi.uzh.ch/NeuroBEM.html), [public download directory](https://download.ifi.uzh.ch/rpg/NeuroBEM/), and [Readme](https://download.ifi.uzh.ch/rpg/NeuroBEM/Readme.md) provide a quadrotor residual-aerodynamics dataset with 400 Hz Vicon-derived state, body velocity, body acceleration, motor speeds in rad/s, battery voltage, predicted force/torque, and residual force/torque columns. `docs/scripts/analyze_neurobem_source_inventory.py` caches the small metadata files under `docs/data/raw/neurobem/` and generates `docs/data/neurobem_source_inventory.csv` (`120` rows, mirrored as `neurobem_packet_*`). `docs/scripts/analyze_neurobem_drag_residual_packet.py` then reads the public `predictions.tar.xz` raw cache and generates `docs/data/neurobem_drag_residual_packet.csv` (`17293` rows, mirrored as `neurobem_residual_packet_*`): `251` CSV files, `1,816,329` rows, `76.05` min, `247/251` files matched to `Flights.txt`, all `13` official test-set segments marked, body-speed sample P95/max `11.74/17.72 m/s`, residual-force P95 `0.915 N` (`0.121x` the 0.772 kg vehicle weight), residual-torque P50/P95/max `0.00419/0.02276/0.1757 N*m`, and a 0.1 voltage-column scale audit giving P50/P05 battery voltage `15.33/14.30 V`. The torque P95 is `0.650x` current `racingQuad` `propwashMaxTorqueNewtonMeters = 0.035`, and equivalent angular-damping P95 is `0.0123 N*m/(rad/s)` (`0.684x` current `angularDragCoefficient = 0.018`). The packet now includes per-file metadata, speed bins, trajectory-family summaries, and target-velocity summaries; target-velocity `1.6`/`2.4 m/s` groups show torque P95 `0.0391/0.0380 N*m`. The full processed archive is `627 MB` and the prediction archive is `225 MB`; use this as a residual-structure source, not a high-speed racing envelope source. Its residuals are model residuals, not total drag.
- [WAVELab AscTec Pelican Dataset](https://raw.githubusercontent.com/wavelab/pelican_dataset/master/README.md) provides a `238.1 MB` MATLAB dataset with `54` indoor flights, Vicon position/orientation, numerical velocity/body rates, actual motor speed, and commanded motor speed. Use it for system-identification method checks; its indoor Pelican platform is not a direct 5-inch FPV drag target.
- [Manchester "Theoretical and Practical Limits on Multi-Rotor Manoeuvrability" thesis](https://research.manchester.ac.uk/en/studentTheses/theoretical-and-practical-limits-on-multi-rotor-manoeuvrability/) and [PDF](https://research.manchester.ac.uk/files/295567271/FULL_TEXT.PDF) report axial-flight multi-rotor drag testing, wind-tunnel validation, and a manoeuvrability dataset including drag coefficients, achievable airspeed, available acceleration, and powertrain performance.
- [APdrone Mendeley Data v2](https://data.mendeley.com/datasets/zgsvdtxnfh/2) / DOI [10.17632/zgsvdtxnfh.2](https://doi.org/10.17632/zgsvdtxnfh.2) provides Betaflight 4.5 F722 configuration, real-flight Blackbox CSV, tuning-result CSVs, battery-test archives, component datasheets, an inertia-calculation PDF, and videos for a multipurpose FPV quad. The local cache currently downloads `Selected Flight.csv`, the Betaflight text dump, `Moment of Inertia Calculations FPV Drone and Test Platforms.pdf`, the nine small `RESULT_*` PID/MAE CSVs, five selected component datasheet PDFs, the two battery-autonomy RAR archives, and the two real-flight RAR archives under `docs/data/raw/apdrone_zgsvdtxnfh_v2/`; larger unrelated `.rar` and video files are inventoried but not mirrored.
- [YSIDO 2507 1800KV product page mirror at RCDrone](https://rcdrone.top/products/ysido-2507-1800kv-brushless-motor) corroborates the APdrone motor PDF identity as a YSIDO 2507 1800KV motor for 5-inch FPV racing drones and states that the product imagery includes throttle/voltage/current/power/thrust/efficiency/temperature test data. The numeric thrust rows in `docs/data/apdrone_motor_thrust_pdf_reference.csv` are still labeled as manual transcription from the APdrone PDF images.
- [NASA multicopter wind-tunnel paper](https://rotorcraft.arc.nasa.gov/Publications/files/72-2016-374.pdf) gives small-UAS wind-tunnel force/moment context for 3DR Solo, DJI Phantom 3, 3DR Iris, Drone America DAx8, and SUI Endurance. Table 1 supplies rotor diameter and nominal flight weight for the `lift/weight` and CT conversions. Fig. 30 bare-airframe `Drag/q` is digitized into `docs/data/nasa_bare_airframe_drag_digitized_reference.csv`; Figs. 18/20/22 powered drag at `q = 0.48 lb/ft^2` are digitized into `docs/data/nasa_powered_full_airframe_drag_digitized_reference.csv`; Figs. 17/19/21 powered lift at the same condition are digitized into `docs/data/nasa_powered_lift_digitized_reference.csv`.
- [NASA drag equation](https://www1.grc.nasa.gov/beginners-guide-to-aeronautics/drag-equation/) is used for equivalent `CdA` conversion from `F = cV^2`.
- [RotorPy Hummingbird parameters](https://raw.githubusercontent.com/spencerfolk/rotorpy/main/rotorpy/vehicles/hummingbird_params.py) gives direct quadratic body drag `c_D`.
- [UZH/RPG rpg_quadrotor_control FPV parameters](https://raw.githubusercontent.com/uzh-rpg/rpg_quadrotor_control/master/control/position_controller/parameters/fpv.yaml), [controller implementation](https://raw.githubusercontent.com/uzh-rpg/rpg_quadrotor_control/master/control/position_controller/src/position_controller.cpp), and [Faessler et al. rotor-drag flatness paper](http://rpg.ifi.uzh.ch/docs/RAL18_Faessler.pdf) provide mass-normalized linear rotor-drag compensation coefficients for a high-speed FPV quadrotor controller.
- [ICAS 2020 "Numerical Simulation of Quadcopter Drone in Adverse Situations"](https://www.icas.org/icas_archive/ICAS2020/data/papers/ICAS2020_0482_paper.pdf) provides CFD table values for free-falling terminal speeds/drag coefficients, heavy-rain CT loss, gust response, and a `10.7 m/s` forward-flight drag-force case.
- [gym-pybullet-drones BaseAviary.py](https://raw.githubusercontent.com/utiasDSL/gym-pybullet-drones/main/gym_pybullet_drones/envs/BaseAviary.py) applies a rotor-speed-scaled linear drag model based on Forster's Crazyflie system identification.

### Rotor inertia and gyroscopic torque

Useful open-source/model anchors:

- [RotorS Firefly xacro](https://raw.githubusercontent.com/ethz-asl/rotors_simulator/master/rotors_description/urdf/firefly.xacro) and [RotorS Hummingbird xacro](https://raw.githubusercontent.com/ethz-asl/rotors_simulator/master/rotors_description/urdf/hummingbird.xacro) define `mass_rotor`, `radius_rotor`, `rotor_velocity_slowdown_sim`, and a cuboid `rotor_inertia` block.
- [ZJU FAST-Lab Ground-effect-controller supplementary material](https://raw.githubusercontent.com/ZJU-FAST-Lab/Ground-effect-controller/master/README.md) gives the dynamic motor model `M_i = k_I n_i^2 + J_R dot(n_i)`, reports `J_R = 1.0556e-4`, and lists a 7-inch, 7.5 g propeller in the BOM.
- Official 5-inch racing prop mass anchors: [HQProp 5x4.3x3 V1S](https://www.hqprop.com/hq-durable-prop-5x43x3v1s-2cw2ccw-poly-carbonate-p0048.html), [HQProp 5x4.5x3 V1S](https://www.hqprop.com/hq-durable-prop-5x45x3v1s-2cw2ccw-poly-carbonate-p0052.html), [HQProp 5x5x3 V1S](https://www.hqprop.com/hq-durable-prop-5x5x3v1s-2cw2ccw-poly-carbonate-p0085.html), and [Gemfan 51466 MCK V2](https://www.gemfanhobby.com/hurricane-51466-v2-pc-3-blade.html).
- Official small/ducted and 10-inch prop mass anchors: [HQProp T3x3x3](https://www.hqprop.com/hq-durable-prop-t3x3x3-2cw2ccw-poly-carbonate-p0091.html), [Gemfan D90 ducted](https://www.gemfanhobby.com/d90-ducted-pc-3-blade-m5.html), [Gemfan 1045 3-blade](https://www.gemfanhobby.com/1045-glass-fiber-nylon-3-blade.html), and [Gemfan 1050 Cinelifter 3-blade](https://www.gemfanhobby.com/1050-cinelifter-glass-fiber-nylon-3-blade.html).

Generated rotor-inertia file:

- `docs/data/rotor_inertia_gyro_reference.csv` compares each current preset's `rotorInertia` with open-source/reference rows, converts inertia to equivalent prop mass by `I = c * m * R^2`, estimates per-rotor angular momentum and gyroscopic torque, and now adds physical prop-mass rows plus same-size current-vs-prop comparisons.

Current high-signal outputs:

- Official HQProp/Gemfan 5-inch tri-blade masses span `3.81..4.48 g`; using `I = c*m*R^2`, `racingQuad.rotorInertia = 3.0e-6 kg*m^2` is about `0.49..0.59x` those uniform-blade inertia estimates and `0.50..0.59x` their listed masses when converted back to equivalent uniform mass. It is low for prop-only inertia unless the intended mass distribution is strongly hub-biased or the value is response-scaled.
- `cinewhoop.rotorInertia = 9.0e-6 kg*m^2`, equivalent to about `18.70 g` under the same proxy despite the smaller radius. Against HQProp T3x3x3 and Gemfan D90 official masses, it is several times above prop-only inertia, so treat it as duct/fan/motor/gameplay inertia unless measured 3-inch ducted-rotor inertia supports it.
- The `heavyLift` 10-inch preset is closer to official Gemfan 10-inch tri-blade masses, making it more plausible as prop-only inertia than the smaller presets.
- RotorS Hummingbird's physical cuboid rotor inertia is `7.67e-6 kg*m^2`; its simulation-slowdown row is `7.67e-5 kg*m^2` because the xacro multiplies rotor mass by `rotor_velocity_slowdown_sim = 10`.
- ZJU's `J_R = 1.0556e-4` is useful as a dynamic motor-model scale, but its source model uses RPM-based coefficients, so check the unit convention before direct SI use.

Formula notes:

- Equivalent mass is only a geometry proxy. `c = 0.25` means hub-biased mass, `c = 1/3` means uniform slender blades, `c = 0.5` means tip-biased blades, and `c = 1.0` is a ring upper bound.
- Current code uses rotor inertia in acceleration reaction torque, active-braking/coast response, gyroscopic reaction torque, and coning-related vibration/load terms, so this value should not be tuned from hover thrust data alone.

### Rotor arm flex and blade coning

Useful open-source/reference anchors:

- [FAA Helicopter Flying Handbook, Chapter 2](https://www.faa.gov/sites/faa.gov/files/regulations_policies/handbooks_manuals/aviation/helicopter_flying_handbook/hfh_ch02.pdf) gives qualitative coning background as a lift/centrifugal-force balance.
- [NASA CR-2017-219428 multicopter blade-deflection study](https://rotorcraft.arc.nasa.gov/Publications/files/Nowicki_CR-2017-219428_Final.pdf) directly measures small-UAS propeller out-of-plane deflection and coning for DJI Phantom 3 and T-motor 15x5 propellers.
- [NASA cantilever deflection note](https://ntrs.nasa.gov/api/citations/19690026842/downloads/19690026842.pdf) and [NASA cantilever tip-mass frequency note](https://ntrs.nasa.gov/api/citations/19760006440/downloads/19760006440.pdf) provide first-order beam formula anchors for arm deflection and bending frequency.
- [ACP carbon-fiber composite material properties](https://acpcomposites.com/wp-content/uploads/2023/12/Mechanical-Properties-of-Carbon-Fiber-Composite-Materials.pdf) gives laminate modulus ranges used as carbon-arm bending sensitivity inputs.

Generated arm-flex/coning file:

- `docs/data/rotor_arm_flex_coning_reference.csv` contains reference formula rows, ACP carbon modulus rows, low-volume NASA prop coning/deflection measurements, current Java mirror rows, and beam-theory arm sensitivity rows.
- NASA's DSLR mean rows for DJI Phantom 3 propellers give about `1.06 mm / 0.51 deg` at `3000 rpm`, `2.47 mm / 1.18 deg` at `5000 rpm`, `3.73 mm / 1.78 deg` at `7500 rpm`, and `4.08 mm / 1.95 deg` at `8500 rpm`.
- The T-motor 15x5 carbon prop DSLR row gives about `1.87 mm / 0.89 deg` at `5000 rpm`; the photogrammetry table gives `1.12 mm` tip deflection at the same RPM.
- Current `rotorConingIntensity` is not a literal angle. In the generated `racingQuad` max-steady row, coning target intensity is about `0.639`, but the direct thrust scale is still about `0.976`, so it is primarily a small load/vibration/thrust-loss proxy.
- Current `racingQuad` arm flex gives `1.58 mm / 0.55 deg` at max steady load and `5.94 mm / 2.08 deg` in the max-snap proxy. Full-flex cap alone is less useful than the target rows because the Java target rarely reaches `1.0`.
- Simple 5-inch carbon-arm sensitivity rows span about `1.87..11.72 mm` deflection at `13.5 N` max thrust and `28..69 Hz` first-bending frequency for the assumed 6x4 mm and 10x5 mm sections across `70` and `135 GPa`. The current flex tune is plausible for a thin/flexible arm, but high for a stiff arm.
- The beam sections are deliberately sensitivity cases, not measured frame dimensions. Use real arm width/thickness, layup direction, motor/prop mass, and joint stiffness before fitting the runtime coefficients.

### Prop pitch geometry

Useful official prop-spec anchors:

- The same HQProp/Gemfan product pages above also carry diameter and pitch labels for same-size geometry checks: 5-inch racing props (`5x4.3`, `5x4.5`, `5x5`, Gemfan `51466`), 3-inch/ducted props (`T3x3x3`, Gemfan `D90`), and 10-inch lift props (`1045`, `1050`).
- UIUC geometry TXT files add station-level `r/R, c/R, beta` data for selected prop families: [DA4052 5x3.75](https://m-selig.ae.illinois.edu/props/volume-2/data/da4052_5x3.75_geom.txt), [NR640 5in 15deg](https://m-selig.ae.illinois.edu/props/volume-2/data/nr640_5_15deg_geom.txt), [MicroInvent 5x4](https://m-selig.ae.illinois.edu/props/volume-2/data/mit_5x4_geom.txt), [APC SF 10x4.7](https://m-selig.ae.illinois.edu/props/volume-1/data/apcsf_10x4.7_geom.txt), [APC SF 10x7](https://m-selig.ae.illinois.edu/props/volume-1/data/apcsf_10x7_geom.txt), [APC Thin Electric 10x5](https://m-selig.ae.illinois.edu/props/volume-1/data/apce_10x5_geom.txt), and [APC Thin Electric 10x7](https://m-selig.ae.illinois.edu/props/volume-1/data/apce_10x7_geom.txt).

Generated prop-geometry file:

- `docs/data/prop_geometry_pitch_reference.csv` mirrors each current preset's effective `bladePitchMeters`, converts it to inches and `P/D`, records hover/max pitch speed, computes the same 70% radius geometric pitch angle used by the blade-element path, records official prop `diameter x pitch`, adds same-size current-vs-reference ratios, and now includes UIUC geometry point/summary/current-comparison rows.
- `docs/scripts/analyze_prop_pitch_geometry_packet.py` generates `docs/data/prop_pitch_geometry_packet.csv`, a 294-row narrow packet mirrored into `docs/data/fpv_model_validation_summary.csv` as `prop_pitch_packet_*`.
- The UIUC additions are 126 raw station rows, 7 geometry summaries, and 15 current-vs-UIUC comparison rows. The generated fields include `reference_chord_to_radius_70r`, `reference_beta_deg_70r`, derived local `P/D` at 70%R, a simple planform-solidity proxy, and current chord/pitch-angle ratios.

Current high-signal outputs:

- Current presets without `withRotorBladePitchMeters(...)` use `RotorSpec.defaultBladePitchMeters(radius) = max(0.01, 1.70R)`, which is `P/D = 0.85` for all current preset radii.
- `racingQuad`'s 5-inch effective pitch is `4.25 in`; same-size official prop comparisons span `0.85..1.18x` reference pitch, and its hover/max pitch speed is about `23.4/52.4 m/s`, so the default is a reasonable coarse 5-inch FPV proxy.
- `heavyLift`'s 10-inch effective pitch is `8.50 in`, which is `1.70..1.89x` official Gemfan 1050/1045 pitch. Its 70% radius geometric pitch angle is about `1.65..1.83x` the official 1050/1045 angle. Any model path using pitch speed, windmilling reverse axial speed, chord/Re proxy, or geometric stall angle should prefer a per-preset prop pitch instead of inheriting the fixed 5-inch-style `P/D = 0.85`.
- Against UIUC station geometry at 70%R, `racingQuad`'s chord proxy is `0.58..0.89x` same-size 5-inch references and its pitch angle is `1.01..1.22x`; this suggests the 5-inch default pitch angle is close, while the `0.12R` representative chord can be low for wide-blade FPV props.
- Against UIUC 10-inch references, `heavyLift`'s current 70%R pitch angle is `1.18..1.86x`, and its current `P/D = 0.85` is `1.19..1.92x` the UIUC local 70%R pitch ratio. This supports treating large-lift pitch/chord/Re/stall geometry as preset-specific rather than inherited from the 5-inch default.
- The packet crosscheck keeps the tuning split visible: `racingQuad` official pitch ratio `0.85..1.18x` and UIUC angle ratio `1.01..1.22x`, but `heavyLift` official pitch ratio `1.70..1.89x`, UIUC local P/D ratio `1.19..1.92x`, and chord proxy `0.57..0.83x`; large-lift presets should get explicit blade-pitch/chord geometry.
- DA4052/NR640 three-blade performance rows do not publish separate three-blade geometry TXT files; the script uses the same-family tested blade geometry rows and labels that limitation in `source_context`.

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
- `docs/data/zju_ground_effect_model_reference.csv` maps the ZJU equations `F_G(h)=g2/(h^2+g1)` and `M_G(h)=g5*h/(h^2+g3*h+g4)^2`, scans height, compares current `DroneConfig` ground-effect multipliers at the same physical clearance, and records the paper's low-altitude rotor-drag observation and mixing-matrix sensitivity note.
- ZJU's rotor-drag observation is especially important for model review: the paper reports `0.1 m / 2.0 m` measured drag-coefficient ratios around `0.596` on X and `0.618` on Y, while a sqrt-thrust scaling would predict `0.949`. The current Java near-ground drag proxy adds a separate lateral force, so it should be treated as a different physical term unless fitted against near-ground flight data.

### Ceiling, wall, and near-surface effects

Useful sources:

- [AAU quadcopter thesis PDF](https://projekter.aau.dk/projekter/files/454478098/finalquad.pdf) summarizes ground, ceiling, partial-ground, and wall effects; it uses the Kan/Thomas/Tanner/Kumar ground-effect model and explicitly treats wall effect as both attraction toward the wall and reduced thrust for rotors closest to the wall.
- [Springer JIRS 2024 surface-effect evaluation](https://link.springer.com/article/10.1007/s10846-024-02155-7) reports an experimental setup for ground, ceiling, and wall effects using fixed 6-DOF force/torque sensors and controlled pressure/temperature conditions. Its [supplementary repository DOI](https://doi.org/10.5281/zenodo.11384638) and Springer supplementary zip expose CSV files used for the article graphs and derived quantities.
- [Carter, Bouchard, and Quinn AIAA 2020 Crazyflie wall-effect paper](https://par.nsf.gov/servlets/purl/10267925) gives a small-quad sidewall anchor: sidewall total lift changes are reported below `5%`, and noticeable `>2%` sidewall lift change appears only very close to the wall (`z/R < 0.3`).
- [Cai, Gunasekaran, and Ol partial ground/ceiling paper via Wichita SOAR](https://soar.wichita.edu/items/20c3f481-4556-41fa-8c79-a4b0b6d3c620) / DOI [10.2514/1.C036974](https://doi.org/10.2514/1.C036974) gives finite-size ground/ceiling threshold evidence for a small fixed-pitch propeller in hover. The repository exposes metadata/abstract but no public full-text bundle.
- [Robinson, Chung, and Ryan near-wall micro-rotorcraft CFD record](https://research.monash.edu/en/publications/computational-investigation-of-micro-rotorcraft-near-wall-hoverin/) is the wall-attraction/thrust-loss study cited by the AAU report.
- [Nature 2025 confined-duct surface-effect study](https://www.nature.com/articles/s44182-025-00032-5) is not an FPV free-flight benchmark, but it is a useful recent reminder that wall/duct proximity can dominate small multirotor control authority in tight passages.

Generated surface files:

- `docs/data/surface_proximity_effect_reference.csv` contains two scans: current ground/ceiling multipliers versus `h/R`, and current wall-force proxy versus rotor obstruction and transverse speed.
- `docs/data/surface_obstruction_geometry_reference.csv` adds wall-effect literature anchors, ideal flat-wall disk-overlap geometry, current `RotorFlowObstructionModel` flat-wall mappings, and the offline A4MC `wall_skim` residual/shelter/pressure-gradient decomposition for `racingQuad`, `apDrone`, `cinewhoop`, and `heavyLift`.
- `docs/data/surface_nearfield_calibration_packet.csv` condenses selected ground/ceiling `h/R` rows, ZJU ground-effect checks, wall clearance mappings, wall-force scans, offline A4MC `wall_skim` closest-rotor metrics, and published sidewall anchors into one `739`-row narrow metric table. These rows are mirrored into `docs/data/fpv_model_validation_summary.csv` as `surface_nearfield_*` categories.
- `docs/data/wall_obstruction_response_packet.csv` adds a `1450`-row wall-obstruction response matrix mirrored as `wall_obstruction_packet_*`. It separates ideal flat-wall disk overlap, runtime side-flow obstruction, dirty-air thrust loss, wall-attraction force, and transverse-speed washout for the current presets.
- `docs/data/a4mc_source_quality_response_packet.csv` adds a `11782`-row A4MC source-quality response matrix mirrored as `a4mc_source_quality_packet_*`. It varies trust, confidence, freshness, shelter, shear, source turbulence, dedicated ABL stability/mixing, and signed updraft/downwash adoption, then records the core-adopted source turbulence, natural turbulence, ABL Dryden intensity/time-scale, source gust, adopted updraft target/settling response, terrain-shear, local-voxel ventilation, hover axial-gust scaling, and local-obstacle residual responses. Runtime blackbox separates adopted A4MC source gust, signed updraft, and terrain-shear air-mass splits, so the packet can now calibrate those channels independently.
- `docs/data/a4mc_disk_gradient_response_packet.csv` adds a `6227`-row A4MC rotor disk-gradient response surface mirrored as `a4mc_disk_gradient_packet_*`. It varies preset, spin state, raw A4MC disk gradient, and source-quality gate, then records adopted gradient, tip-speed ratio, thrust-loss scale, load, vibration, stall, and flapping proxies from the current core formulas. The runtime blackbox now logs the same disk-gradient thrust-loss/load/vibration/stall response components separately from raw gradient magnitude for trace-to-packet calibration.
- `docs/data/a4mc_local_voxel_coupling_packet.csv` adds a `1620`-row A4MC local-voxel bridge audit mirrored as `a4mc_local_voxel_packet_*`. It isolates the fabric-side preprocessing that turns trusted local L2 A4MC shelter and pressure samples into source-quality-gated duplicate-obstacle residuals, rotor residual fallback behavior, pressure-gradient disk-wind equivalents, and shelter-gradient side obstruction before the core disk-gradient response. Runtime blackbox and offline CSV output now expose aggregate and per-rotor `local_voxel_obstacle_residual` columns, so trace analysis can line up live wall/tunnel attenuation with this packet directly.
- `docs/data/partial_surface_effect_lead_packet.csv` is a `74`-row partial-ground/partial-ceiling handoff mirrored as `partial_surface_effect_lead_packet_*`. It records SOAR metadata, abstract text checks, finite-plate threshold rows, a conservative initial area gate candidate, and current-preset diameter mappings.
- `docs/data/surface_jirs2024_effect_packet.csv` is a `309`-row raw-measurement handoff from the JIRS 2024 supplementary CSV/MAT files, mirrored as `surface_jirs2024_packet_*`. It caches the Springer supplementary zip, records `9` CSV files / `4` MAT files / `1` measurement-uncertainty PDF, parses `225` numeric measurement rows (`40` ground, `40` ceiling, `145` wall), and adds `40` direct MAT uncertainty summary rows for Fz, horizontal wall force, and wall moment.
- `docs/data/surface_jirs2024_curve_fit_packet.csv` is a `196`-row curve-fit handoff mirrored as `surface_jirs2024_curve_fit_*`. It fits ground/ceiling extra-thrust curves and wall absolute force/moment distance curves, then compares those fits with the current `racingQuad` runtime surface mappings.
- `docs/data/fpv_model_validation_summary.csv` now includes these near-surface and A4MC rows under `surface_proximity_ground_reference_curve`, `surface_proximity_ground_ceiling_current`, `surface_proximity_wall_force_current`, `surface_jirs2024_packet_*`, `surface_jirs2024_curve_fit_*`, `wall_obstruction_packet_*`, `a4mc_source_quality_packet_*`, `a4mc_disk_gradient_packet_*`, and `a4mc_local_voxel_packet_*`, so automated analysis can use the summary file without reopening the wide source CSV.
- The reference columns include Cheeseman-Bennett ground-effect boost and an AAU/Kan hover boost proxy. They are sanity curves, not direct fit targets, because the source equations use different thrust-factor conventions.
- Partial-surface threshold takeaway: the SOAR abstract reports a plate diameter equal to the propeller diameter gives ground/power effects comparable to an infinite plate, while a plate less than half the propeller diameter has negligible ground or ceiling effect. For `racingQuad`, that maps to about `0.127 m` full-like patch diameter and `0.0635 m` negligible-patch diameter. A full Minecraft `1 m` block is `7.87D` for `racingQuad`, so it behaves as a large/infinite patch for a single 5-inch rotor. Runtime rotor-disk sampling now converts supported sample coverage into an equivalent circular patch diameter, then applies the same `0.5D..1.0D` gate, so narrow ledges, holes, partial block edges, or rotor-disk overlap below roughly `0.25` area ratio fade out near-field ground/ceiling lift. Blackbox and offline CSV output now expose the per-rotor supported-surface coverage and gate columns, allowing edge traces to separate raw clearance from partial-surface attenuation.
- JIRS 2024 raw CSV takeaway: using the farthest `100 cm` row as a same-PWM baseline, the closest ground rows at `h/R = 0.328..0.394` have median/max thrust ratios `1.291x/1.315x`, while closest ceiling rows have `1.227x/1.289x`. Across all non-far ground rows the ratio range is `0.863..1.315x`, so the measured curve is not perfectly monotonic and should be fitted with uncertainty rather than forced through a simple boost shape.
- JIRS 2024 curve-fit takeaway: the conservative fits give `h/R=1` multipliers of `1.0856x` for ground (`R2=0.980`) and `1.0961x` for ceiling (`R2=0.951`). Current `racingQuad` at `h/R=1` is `1.054x` the JIRS ground fit and `1.016x` the JIRS ceiling fit, so current ground is stronger than this fitted JIRS curve but weaker than the ZJU formula-level anchor.
- JIRS 2024 wall takeaway: wall rows cover `d/R = 0.965..3.0` with median/max absolute wall force `0.0986/0.4278 N` and median/max absolute wall moment `0.0277/0.1195 N*m`. Wall force is signed and facility-dependent, so use it for attraction/moment calibration and distance scaling, not as a vehicle-wide clean thrust-loss scalar.
- JIRS 2024 uncertainty takeaway: direct terraXcube wall-force/wall-moment uncertainty medians are `0.0389 N` / `0.00687 N*m`, while DU2SRI wall-force/wall-moment uncertainty medians are `1.110 N` / `0.0560 N*m`. Treat facility as a first-class fit condition or weight source, and do not use coefficient-uncertainty maxima blindly because nondimensional coefficients can blow up near zero denominators.
- JIRS 2024 wall-fit takeaway: terraXcube absolute wall force fits cleanly (`R2=0.923`, predicted `0.1077 N` at `d/R=1`), but the pooled wall-force fit is weak (`R2=0.225`, predicted `0.1655 N` at `d/R=1`). The current `racingQuad` two-affected-rotor wall-force proxy at `d/R=1` is `0.3492 N`, or `2.11x` the pooled JIRS fit; treat that as attraction/moment evidence until a geometry-matched wall experiment supports a clean thrust-loss scale.
- For `racingQuad`, the current ceiling boost is intentionally softer than ground boost: ceiling max boost is `0.75x` the ground boost and clamped to `0.22`.
- At hover and full wall obstruction, the current wall proxy gives about `0.56 N` per affected `racingQuad` rotor; two affected rotors equal about `0.10x` vehicle weight.
- Ideal disk-overlap geometry is much narrower than the current side-flow proximity heuristic: a flat wall at `d/R = 0.5` cuts only `0.196` of the disk area, `d/R = 0.75` cuts `0.072`, and `d/R = 1.0` cuts `0`.
- In the current runtime wall sampler, `racingQuad` uses a side-flow scan distance of `0.4128 m`, or `6.50R`. At `d/R = 1.0`, where geometric disk overlap is already zero, the current flat-wall obstruction is `0.487`, the affected-rotor thrust multiplier is `0.988`, two affected rotors give an equivalent whole-vehicle thrust multiplier of `0.994`, and the wall-attraction force is `0.175 N` per affected rotor / `0.032x` vehicle weight for two rotors.
- The current offline `wall_skim` scene keeps the closest rotor at about `0.04 m` from the wall. That corresponds to `d/R = 0.63` for `racingQuad`, `0.62` for `apDrone`, `1.05` for `cinewhoop`, and `0.31` for `heavyLift`; the A4MC proxy keeps the local-obstacle residual at `0.62x`, adds `0.150` side-shelter obstruction plus `0.33 m/s` pressure-gradient-equivalent disk wind, and produces combined closest-rotor obstruction values of `0.461`, `0.464`, `0.416`, and `0.524`. For `racingQuad`, that maps the raw geometric obstruction `0.591` to residual `0.366` plus shelter, with an affected-rotor thrust multiplier of `0.990`.
- The A4MC source-quality packet keeps the synthetic wall-skim source gate explicit: the reference `confidence=0.86`, fresh, `shelter=0.74`, `shear=0.58/block`, `source_turbulence=0.24`, `updraft=0.18 m/s` case adopts source quality `0.86`, source turbulence `0.206`, natural turbulence proxy `0.786`, source-gust Y peak proxy `0.409 m/s`, updraft target/1 s settled values `0.078/0.077 m/s`, a terrain-vector peak proxy of `0.192 m/s`, and local-voxel ventilation efficiency `0.873`. The trusted mixed-ABL rows bracket Dryden response with an unstable intensity proxy of `0.890` and vertical time-scale `0.627x` versus stable intensity `0.499` and vertical time-scale `1.243x`; the full-trust `+/-12 m/s` vertical-flow endpoints clamp to signed `+/-4.5 m/s` targets and settle to `+/-4.499 m/s` after `1 s` at `200 Hz`. A stale `160 tick` source drops quality and transient forcing to zero, so real block-edge traces should keep raw updraft, adopted updraft, and terrain-vector peaks separate during calibration.
- The A4MC disk-gradient packet shows the same `wall_skim` pressure-gradient probe is intentionally near-threshold: `0.33 m/s` raw becomes `0.284 m/s` after the `0.86` quality gate. For `racingQuad` hover, flapping tilt starts near `0.363 m/s` raw and thrust-loss starts near `0.564 m/s` raw at that quality, while a full `12 m/s` raw gradient at max spin reaches `4.5 deg` tilt and `3.77%` thrust loss in the current formula.
- The A4MC local-voxel coupling packet keeps the fabric bridge separate from the older offline surface handoff: the reference `confidence=0.86`, fresh, `shelter=0.74` case maps duplicate local-obstacle airflow to a `0.419328x` residual, and missing/coarse/stale/untrusted rotor-center samples keep that body residual instead of restoring duplicate geometric obstruction. Usable rotor-center samples still refine the residual per rotor, with a trusted exposed rotor at `0.68x` and a trusted fully sheltered rotor clamped to `0.28x`. A one-sided `220 Pa` disk-edge pressure step maps to `0.0801 m/s` quality-weighted disk gradient, and the largest `3200 Pa` pressure step maps to `1.355 m/s`. For the shelter-gradient wall-skim proxy, center shelter `0.35` plus input edge delta `0.74` is clamped by runtime `WindSample` sanitization to edge shelter `1.0` and adopted delta `0.65`, yielding `0.0442` quality-weighted side obstruction; a stale `160 tick` source drops that obstruction to zero.
- The nearfield calibration packet summarizes the tuning warning numerically: for `racingQuad` at `h/R = 1`, current ground multiplier is `1.144` versus ZJU `1.332`, so current extra thrust is `0.433x` the ZJU extra while still `1.072x` the Cheeseman-Bennett boost. At a flat wall `d/R = 0.25`, the current runtime mapping gives two affected rotors an equivalent vehicle thrust loss of `2.05%` and wall force `0.063x` weight; at `d/R = 1.0`, where ideal disk overlap is zero, it gives `0.58%` vehicle thrust loss and `0.032x` wall force. The offline A4MC `wall_skim` handoff adds `racingQuad_offline_a4mc_wall_skim_combined_obstruction = 0.461`, `combined_over_geometry = 0.781`, and `pressure_disk_gradient = 0.33 m/s`, making that scene a synthetic contact/dirty-air case rather than a pure sidewall disk-overlap target. Published sidewall anchors report total lift change below `5%` and negligible total-thrust wall effect, so ordinary sidewall proximity should be modeled primarily as attraction/moment/dirty air rather than a large clean thrust-loss term.
- Calibration takeaway: treat `rotorFlowObstruction` as a side-flow/dirty-air proximity heuristic, not as physical rotor-disk area overlap. Published wall-effect anchors support wall attraction and attitude moment close to surfaces, but they do not support a large total-thrust loss for ordinary sidewall proximity. The current `RotorFlowObstructionModel.thrustMultiplier` curve is now a capped dirty-air term (`1 - 0.10 * obstruction^3`, clamped to `0.90..1.00`), while wall force is tracked separately for attraction/moment calibration.

### Contact, collision, and prop-strike data

Useful open-source/reference anchors:

- [Engineering ToolBox friction coefficient table](https://www.engineeringtoolbox.com/friction-coefficients-d_778.html) provides material-pair friction examples such as ice/ice, rubber/asphalt, rubber/concrete, and wood/concrete.
- [Physics Factbook coefficient-of-restitution table](https://hypertextbook.com/facts/2006/restitution.shtml) gives simple ball-drop restitution examples on concrete, including wood, steel, glass, and rubber-band-ball rows.
- [RocFall coefficient-of-restitution background](https://www.rocscience.com/help/rocfall/documentation/slope/materials/coefficient-of-restitution) and the [RocFall thesis PDF](https://static.rocscience.cloud/assets/verification-and-theory/RocFall/thesis_body.pdf) provide a low-bounce hard-surface reference range for normal/tangential restitution in rockfall-style rigid contact.
- [UAV Realistic Fault Dataset](https://github.com/tiiuae/UAV-Realistic-Fault-Dataset) and the related [ICRA 2023 paper](https://secplab.ppgia.pucpr.br/files/papers/2023icra.pdf) are useful public fault/log-shape anchors for broken-propeller fault detection. The repository stores ROS bag exports as JSONL by class `0..4`, where the class is the number of broken propellers. The paper describes a Holybro X500 test vehicle, ReSpeaker microphone audio, 100 flights total, 20 normal / 80 fault flights, 100 Hz acceleration/gyroscope data, and 640 MFCC audio features per second.
- [DronePropB Mendeley dataset](https://data.mendeley.com/datasets/xkvfjmm8zg/1) / DOI [10.17632/xkvfjmm8zg.1](https://doi.org/10.17632/xkvfjmm8zg.1) provides ground-test `.mat` vibration files across fault type, severity, speed, and measurement channel. The full archive is about `0.5 GB`, so the local packets sample representative C3 files plus a targeted speed/channel/fault-severity subset instead of mirroring all raw data.
- [Scientific Data 2025 DJI Mini 2 propeller damage dataset](https://www.nature.com/articles/s41597-025-05692-4) and its [Figshare data record](https://doi.org/10.6084/m9.figshare.28765640) provide labeled multiaxial vibration data for healthy, damaged, and unbalanced propeller conditions.
- [PADRE UAV measurement data](https://github.com/AeroLabPUT/UAV_measurement_data) and its [JINT paper](https://link.springer.com/article/10.1007/s10846-024-02101-7) provide public 3DR Solo and Parrot Bebop 2 actuator-fault sensor data, including raw, normalized, FFT, DWT, EMD, and HHT variants.

Generated contact file:

- `docs/data/contact_collision_reference.csv` contains four groups: public friction/restitution rows, public UAV fault/damage dataset rows, current Minecraft block surface multipliers, and current `ContactDynamics`/prop-strike/rotor-health formula scans.
- The current surface rows mirror `DroneEntity` mappings: default, ice, slime, honey, loose, sticky dirt, and abrasive surfaces with friction/restitution/scrape multipliers.
- The current contact-response rows scan impact speeds `0.5`, `1`, `3`, `5`, `8`, and `12 m/s` and report vertical/horizontal bounce multipliers, floor/wall tangential velocity retention, and impulse-scale proxies.
- The prop-strike rows scan representative tip speeds and frame speeds for default/ice/abrasive surfaces, reporting scrape intensity, strike severity, thrust scale, and vibration proxy.
- The rotor-health rows isolate the damage-only curve by setting healthy prop imbalance to zero, then reporting thrust scale, effective imbalance, and damage vibration at spin ratios `0.5` and `1.0`.
- `docs/data/propeller_damage_vibration_reference.csv` downloads the Figshare `Data.zip` cache, reads the five raw Excel time-series files, and reports sampling rate, duration, axis dynamic RMS, vector dynamic RMS, RMS ratios versus healthy, Welch vector-PSD peaks, band RMS, and a current rotor-health vibration scan. It also samples up to three UAV Realistic `SensorCombined.jsonl` missions per broken-prop class, up to two raw UAV Realistic `Imu.jsonl` files per class, one UAV Realistic `AudioBuffer.jsonl` mission per class, and a lightweight PADRE 3DR Solo FFT subset (`ACCEL_X/Y/Z`, `16_noWindow_1_9`, labels `0000`, `2000`, `2010`) for public actuator-fault feature-scale ratios.
- `docs/scripts/analyze_propeller_damage_vibration_packet.py` generates `docs/data/propeller_damage_vibration_packet.csv`, a 469-row narrow packet mirrored into `docs/data/fpv_model_validation_summary.csv` as `prop_damage_packet_*`.
- `docs/scripts/analyze_dronepropb_sample_packet.py` generates `docs/data/dronepropb_sample_packet.csv`, a 1195-row DronePropB inventory/sample packet mirrored as `dronepropb_sample_packet_*`. It records the full public file tree (`111` `.mat` files, `570,546,367` bytes by file sizes) and downloads `12` representative C3 files: healthy `SP1/SP2/SP3` plus fault classes `F1..F3`, severities `SV1..SV3`, at `SP2`.
- `docs/scripts/analyze_dronepropb_stratified_vibration_packet.py` generates `docs/data/dronepropb_stratified_vibration_packet.csv`, a 774-row targeted expansion mirrored as `dronepropb_stratified_packet_*`. It downloads/analyzes `26` selected files (`88,062,625` cached bytes, about `15.4%` of the public inventory): healthy baselines for C3 speed and C1/C2/C3 channel checks, a C3 severity sweep at `SP2`, a C3 speed sweep at severity `SV2`, and a C1/C2/C3 channel sweep at `SV2/SP2`.
- The UAV Realistic sample and aggregate rows compute timestamp rate, gyro vector dynamic RMS, accelerometer vector dynamic RMS, class-vs-class-0 RMS ratios, mean acceleration norm, clipping count, and per-class median/min/max ranges. These are full takeoff-to-landing missions, so treat them as flight-phase-inclusive fault/noise envelopes rather than steady hover vibration calibration.
- The UAV Realistic raw Imu rows compute frame-id-specific x/z angular velocity and linear-acceleration dynamic RMS without sample-rate estimates because these JSONL rows do not carry explicit timestamps. Treat them as local sensor-layout or local-vibration amplitude evidence. In the current generated aggregate, raw Imu gyro medians sit around `7.65-10.10 rad/s`, while raw Imu acceleration medians peak at class 3 with about `1.52x` class 0.
- The UAV Realistic AudioBuffer rows compute raw JSON payload length, payload RMS/std, absolute-value percentiles, block RMS, class-vs-class-0 ratios, and modulo-6 phase statistics. Treat these as dataset-internal audio features rather than calibrated dB. In the current sampled rows, raw payload RMS is much less separated than SensorCombined acceleration, which reinforces using audio as a feature/spectrum source rather than a simple total-amplitude threshold.
- The DJI Mini 2 dataset's raw signals sample at about `1024 Hz`, giving a `512 Hz` Nyquist limit. Its dominant vector-PSD peak is around `160-164 Hz`, which corresponds to roughly `4800-4920 rpm` if interpreted as a two-blade blade-pass peak. The generated CSV now includes `current_bladepass_dataset_alias` rows showing where each current preset's true motor/blade-pass frequencies would fold under that sample rate; use the DJI data as a fault-vibration signature source, not as a direct validation of the current `racingQuad` three-blade hover blade-pass above `600 Hz`.
- The PADRE sampled FFT rows are normalized/preprocessed feature matrices, not acceleration units. In the current generated summary, the sampled 3DR Solo `2000`/`2010` fault labels have combined XYZ feature RMS about `3.00x`/`3.13x` healthy, giving a stronger relative fault-feature separation than the DJI raw RMS rows.
- The DronePropB sample packet keeps speed and fault effects separate. In the selected C3 files, healthy `SP3` already has `3.30x` the external-accelerometer dynamic RMS of healthy `SP2`, while selected `SP2` fault samples have median/max RMS ratios `1.20x/1.49x` versus healthy `SP2`. Welch peak frequencies cluster around either `~101 Hz` or `~590 Hz` in this C3 subset. Use these rows as a ground-test fault-feature lead; do not collapse them into one monotonic damage scalar without speed/channel normalization.
- The DronePropB stratified packet normalizes every selected fault row against a healthy file with the same speed and channel where possible. Across `21` fault rows with same-baseline matches, external-accelerometer RMS ratios are median/P90/max `1.184x/1.488x/2.246x`, and the strongest sample is `F3_SV2_SP3_C3.mat`. Speed still matters strongly: at `SV2/C3`, `SP3` fault rows have median/max ratios `1.808x/2.246x`; at fixed `SV2/SP2`, channel medians are `1.147x` for C1, `1.034x` for C2, and `1.320x` for C3. Treat these as feature-scale and placement/speed-conditioning anchors, not a final flight vibration curve.

### Rain, wet prop, and water immersion

Useful sources:

- [NWS Los Angeles rain-rate visualizer](https://www.weather.gov/lox/rainrate) lists visual examples at `0.05`, `0.25`, `0.50`, `0.75`, `1.00`, and `1.50 in/h`, with the page explaining that examples are computed from a 10-minute accumulation window and multiplied by 6 to get hourly rate.
- [Met Office rainfall measurement guide](https://www.metoffice.gov.uk/weather/guides/observations/how-we-measure-rainfall) provides the rainfall-depth measurement context used to convert `mm/h` into water depth flux.
- [CIRES vapor-pressure formula summary](https://cires1.colorado.edu/~voemel/vp.html) documents common saturation-vapor-pressure equations, including the Bolton/Magnus-style form mirrored by `DroneEnvironment.saturationVaporPressureHectopascals`.
- [NIST water thermophysical data](https://webbook.nist.gov/cgi/fluid.cgi?ID=C7732185&Action=Page) is the reference page for treating liquid water density as about `997 kg/m^3` near room temperature.
- [ICAS 2020 "Numerical Simulation of Quadcopter Drone in Adverse Situations"](https://www.icas.org/icas_archive/ICAS2020/data/papers/ICAS2020_0482_paper.pdf) gives heavy-rain CFD CT loss for `LWC = 19 g/m^3`, droplet diameter `0.0028 m`, and terminal velocity `8.06 m/s`.
- [Villeneuve et al. 2022 "An Experimental Apparatus for Icing Tests of Low Altitude Hovering Drones"](https://www.mdpi.com/2504-446X/6/3/68) / DOI [10.3390/drones6030068](https://doi.org/10.3390/drones6030068) gives icing-only CT/CQ degradation-rate rows for a 0.66 m four-blade hovering rotor, plus Table 1 LWC/MVD/precipitation-rate conversions.

Generated water/rain file:

- `docs/data/precipitation_water_effect_reference.csv` converts rainfall rates to water mass flux and rotor-disk water encounter rate, then evaluates the current `precipitationWetness`, `waterImmersion`, thrust-loss, load, vibration, moist-air-density, and water-drag formulas. It now includes `reference_icas_heavy_rain_ct_loss` and `current_vs_icas_heavy_rain_ct_loss` rows.
- `docs/scripts/analyze_precipitation_water_packet.py` generates `docs/data/precipitation_water_packet.csv`, a 1608-row narrow packet mirrored into `docs/data/fpv_model_validation_summary.csv` as `precip_water_packet_*`. The packet also parses current `DronePhysics.java` so stale wide-CSV rain coefficients are visible.
- `docs/scripts/analyze_icing_rotor_mdpi_packet.py` generates `docs/data/icing_rotor_mdpi_packet.csv`, a 362-row icing-only packet mirrored as `icing_rotor_packet_*`. It records the MDPI source inventory, Table 1 icing condition grid, Table 4 `C*T`, `C*Q`, `C+Q`, `P+`, icing time rows, rate*time endpoint projections, height/temperature/droplet-size ratios, and current-rain-formula comparisons.
- For a concrete analysis convention, the generated file maps NWS `1.50 in/h = 38.1 mm/h` to `precipitationWetness = 1.0`; this is not a measured FPV wet-prop calibration.
- For `racingQuad`, even the `100 mm/h` stress rain case only sends about `1.40 g/s` of water through all four rotor disks. The rain impact-force proxy is far below vehicle weight, so large rain effects should be interpreted as wet-prop/electrical/vibration behavior unless measured prop data says otherwise.
- ICAS `LWC = 19 g/m^3` corresponds to about `553 mm/h` using its `8.06 m/s` droplet terminal velocity. At that extreme rain load, ICAS CT losses are `2.64%` at `4319 rpm` and `1.67%` at `6528 rpm`; the older wide CSV's full-wetness thrust loss is `5.5%`, about `2.08x`/`3.29x` those CFD losses, while current `DronePhysics.java` parses as `3.0%`, or `1.14x`/`1.79x`.
- The precipitation thrust scale is mild either way: the older generated wide CSV gives `5.5%` full-wetness thrust loss, while current Java source gives `3.0%`; both keep rain far milder than water immersion. The precipitation load factor remains `0.13` at full wetness before downstream motor-response effects.
- MDPI Table 4 is not ordinary rain. Its eight icing cases at `4950 rpm`, `11.7 deg`, and `lambda=80 g/dm^2/h` project CT losses of `9.13..23.96%` over `106..761 s` and required-power `P+` endpoint increases of `20.87..89.49%`, or `3.04..7.99x` the current full-rain thrust loss. Use it only for a separate ice-accretion state.
- The current water-immersion model is intentionally severe: `waterImmersion = 0.5` removes roughly half the rotor thrust and adds large body drag at normal flight speeds.

### Vortex ring state, propwash, wake interference

Useful open-source anchors:

- [RotorS](https://github.com/ethz-asl/rotors_simulator) and [PX4 SITL Gazebo Classic](https://github.com/PX4/PX4-SITL_gazebo-classic) model core motor thrust and drag, but their default Iris/Firefly files do not provide the same detailed VRS/propwash model as this project.
- [gym-pybullet-drones cf2x URDF](https://raw.githubusercontent.com/utiasDSL/gym-pybullet-drones/main/gym_pybullet_drones/assets/cf2x.urdf) includes downwash coefficients `dw_coeff_1 = 2267.18`, `dw_coeff_2 = 0.16`, `dw_coeff_3 = -0.11`.
- [Cambridge Flow dual-rotor axial-descent paper](https://www.cambridge.org/core/journals/flow/article/effects-of-rotor-separation-on-the-axial-descent-performance-of-dualrotor-configurations/BE7FE0D2E732E777CBD43F8E65CA0692) reports strongest thrust loss and oscillation around `1.2-1.3` times hover induced velocity, with losses up to roughly one third in fully developed VRS.
- [Bucherelli/Granata/Savino/Zanotti side-by-side propeller VRS paper](https://doi.org/10.1063/5.0311688) reports a 2026 Physics of Fluids experiment on two 0.300 m propellers in axial descent. The useful open values are side-by-side VRS onset around `DR = 0.9` versus isolated `DR = 1.1`, plus Table II outer-vortex radius and circulation at disk-tip gaps `0.1..1.0 R`.
- [NASA Johnson VRS model PDF](https://rotorcraft.arc.nasa.gov/Publications/files/Johnson_TP-2005-213477.pdf) is a broad rotorcraft VRS regime reference; use it for normalized descent-rate sanity, not FPV-specific buffet torque.
- [NASA/Drexel COTS quadrotor vertical-descent wind-tunnel paper](https://researchdiscovery.drexel.edu/view/pdfCoverPage?download=true&filePid=13594340130004721&instCode=01DRXU_INST) gives an absolute multirotor descent anchor: `50 ft/s = 15.24 m/s` vertical descent with about `8-10%` net thrust loss, plus windmilling context near `55 ft/s`.
- [Shetty/Selig small-scale propeller VRS paper](https://m-selig.ae.illinois.edu/pubs/ShettySelig-2011-AIAA-2011-1254-LRN-VSR-Props.pdf) tested 26 fixed-pitch propellers from 9-11 in at `J = -0.8..0`, holding `4000 rpm` while wind-tunnel speed swept `8..40 ft/s` (`2.44..12.19 m/s`) in `2 ft/s` steps. Each full run used 3 repeats per condition for 51 data points; the time-history tests used 10 props, 9 advance ratios, 90 s records, 120 Hz sampling, and a 10 Hz low-pass filter. The text reports maximum thrust fluctuations of about `+/-30%` near the minimum-thrust advance ratio, which is a useful VRS buffet-amplitude upper-bound even before figure digitization.
- [Shetty IDEALS thesis repository](https://www.ideals.illinois.edu/items/18490) is the open thesis landing page for the same small-prop VRS work; it is useful for source traceability, but no public machine-readable raw time-history CSV has been located yet.
- [NASA Stack/Leishman model-rotor VRS report](https://ntrs.nasa.gov/citations/20040000835) adds a time-scale/upper-bound anchor: VRS load histories may need up to `500` rotor revolutions, peak-to-peak thrust fluctuation can reach `95%` of mean thrust in fully developed VRS, and characteristic intervals are about `20-50` rotor revolutions. This is rotorcraft-scale evidence, so use it for timing and upper bounds, not direct 5-inch prop coefficients.
- [NASA Betzina tiltrotor VRS report](https://ntrs.nasa.gov/citations/20010062358) is a small-scale tiltrotor wind-tunnel context source for VRS boundaries and unsteady-load awareness; it is not a direct FPV propwash torque calibration.

Data status:

- There is enough open-source support for including downwash and wake terms.
- `docs/data/vrs_propwash_reference.csv` mirrors the current Java VRS descent-ratio formula at zero transverse flow, scans each preset over `0.0..2.5 vi`, and separates current VRS thrust loss from `propwash_start/full` handling disturbance.
- `docs/data/vrs_propwash_calibration_packet.csv` condenses VRS reference anchors, Shetty/Selig digitized fluctuation envelopes, current preset VRS scans, current-vs-Shetty comparisons, and summary rows into one narrow handoff table. These rows are mirrored into `docs/data/fpv_model_validation_summary.csv` as `vrs_packet_*` categories.
- `docs/data/vrs_time_history_source_inventory.csv` adds a VRS time-history/source-inventory packet with `168` rows, mirrored into `docs/data/fpv_model_validation_summary.csv` as `vrs_time_*` categories. It records open source URLs, Shetty/Selig timing metadata, Stack/Leishman rotor-revolution intervals, current amplitude ratios, and current preset RPM-to-Hz conversions.
- `docs/data/vrs_shetty_digitization_packet.csv` adds a focused Shetty/Selig digitization handoff with `894` rows, mirrored into `docs/data/fpv_model_validation_summary.csv` as `vrs_digitization_packet_*` categories. It splits source context, CT envelope points, mean CT loss, measured half-amplitude, `J`/`V/vi`/speed conversion, current-code comparisons, and frequency proxies.
- `docs/data/vrs_johnson_regime_packet.csv` adds a `272`-row NASA Johnson regime handoff, mirrored as `vrs_johnson_packet_*`. It encodes Johnson Table 4 parameters, converts the `D/N/X/E/C/M` boundaries to current-preset m/s, and interpolates current VRS/propwash scan values at the Johnson descent boundaries.
- `docs/data/side_by_side_vrs_2026_packet.csv` adds a `94`-row adjacent-propeller VRS interaction handoff, mirrored as `side_by_side_vrs_packet_*`. It encodes the 2026 Physics of Fluids side-by-side propeller source, Table II vortex radius/circulation values, co-rotating spacing trends, and current `racingQuad` adjacent-rotor gap mapping.
- The code's VRS thresholds are already induced-velocity normalized; the generated report converts them to m/s per preset and compares against the Cambridge `1.2-1.3 vi` peak-loss band.
- Johnson Table 4 refines the broad NASA regime anchor: the vertical zero-damping/stability-boundary points are `N = 0.45 vh` and `X = 1.50 vh`, with VRS-increment joins at `D = 0.20 vh` and `E = 2.00 vh`; the VRS increment is suppressed by forward speed at `VxM = 0.95 vh`. For `racingQuad`, the `N..X` band maps to `4.19..13.98 m/s` descent. Current VRS scan is still zero at `N`, peaks at `1.20 vi`, and has interpolated intensity `0.475` at `X`, so the current peak sits inside the Johnson regime while the sampled nonzero onset is later than Johnson's lower zero-damping point.
- The side-by-side VRS packet separates adjacent-rotor interaction from single-rotor buffet: in the 2026 table, side-by-side onset is `DR = 0.9` instead of isolated `DR = 1.1`, the tight `0.1R` co-rotating case has outer-vortex radius/circulation about `0.69x/0.66x` of isolated, and the tight counter-rotating case has `1.25x` the co-rotating circulation. Current `racingQuad` adjacent disk-tip gap is about `2.01R`, outside the tested `0.1..1.0R` range, so use these rows as a compact-frame coupling upper-bound/trend source rather than a direct force fit.
- For `racingQuad`, the current-code scan gives `32.96%` max-spin VRS mean thrust loss and a logged `27.9%` buffet envelope at `1.2 vi`. That is essentially on the Cambridge one-third peak-loss anchor and just below the Shetty/Selig `+/-30%` small-prop fluctuation text anchor.
- The new time-history packet compares that same `27.9%` half-amplitude against time-series anchors: it is `0.93x` the Shetty/Selig `+/-30%` text bound, `0.421x` the largest low-precision digitized Shetty measured-limit envelope, and `0.587x` the Stack/Leishman `95%` peak-to-peak rotorcraft upper bound if treated symmetrically.
- Stack/Leishman's `20-50` rotor-revolution VRS intervals convert to about `4.34-10.85 Hz` at `racingQuad` hover RPM and `9.71-24.28 Hz` at max RPM. Treat this as a low-frequency aperiodic VRS buffet scale, separate from blade-pass vibration and separate from the earlier dirty-air propwash torque tune.
- `docs/data/vrs_propwash_reference.csv` now also includes low-precision manual digitization rows from Shetty/Selig Figs. 11-13. These convert plotted measured CT limits into half-amplitude/mean and map the propeller advance ratio to a `V/vi` proxy using `vi/(nD)=sqrt(2CT_hover/pi)`.
- The digitized measured-limit envelope peaks near `V/vi ~= 1.2..1.4`, matching the current VRS full-entry/peak band. The envelope half-amplitudes can be larger than the paper's text `+/-30%` statement because the digitized rows use measured min/max limits, not RMS or a standardized 3-sigma statistic. At the largest hand-digitized `10x5 J=-0.30` point (`~1.24 vi`, `66%` half-amplitude over mean CT), current `racingQuad` is `27.9%`, or `0.42x` of that measured-limit envelope.
- The VRS calibration packet keeps this semantic split visible: Cambridge peak band is `1.2..1.3 vi` with `0.33` peak-loss fraction, the largest Shetty/Selig digitized half-amplitude is `0.6625` at `~1.24 vi`, current `racingQuad` peak hover-spin loss is `17.4%`, current max-spin buffet half-amplitude is `27.9%`, and current buffet is `0.421x` the largest digitized measured-limit envelope. The best current-vs-Shetty ratio in the low-precision rows is `0.760x`, and current mean-loss at that point is `0.999x` the Cambridge one-third peak-loss anchor.
- The new digitization handoff makes the Shetty/Selig envelope more directly fit-ready: across the 13 low-precision points, median half-amplitude is `0.258`, P90 is `0.503`, and the three points in `1.0..1.5 vi` have median half-amplitude `0.536`. Four digitized points exceed the paper's text `+/-30%` amplitude statement, which is a warning about envelope semantics rather than proof that the RMS buffet should be doubled.
- The separate `propwash_start/full` torque disturbance currently starts earlier than the VRS peak band, so it should be treated as handling/dirty-air feel unless real FPV blackbox recovery logs or wind-tunnel torque data are added.

### Coaxial rotor interference

Useful open-source anchors:

- [New Dexterity Coaxial Benchmarking Platform README](https://raw.githubusercontent.com/newdexterity/Coaxial-Benchmarking-Platform/master/README.md) describes an open rig measuring thrust, torque, RPM, voltage, and current for coaxial rotor pairs.
- [New Dexterity `Data.rar` raw archive](https://github.com/newdexterity/Coaxial-Benchmarking-Platform/blob/main/Data.rar) contains the machine-readable bench CSVs used by the paper/repo. The local cache currently expands to 86 CSV files with PWM, thrust, torque, voltage, current, RPM, and time columns.
- [Hackaday coaxial experiment result log](https://hackaday.io/project/181977/log/199225-some-results-of-coaxial-rotor-experiments-on-the-benchmarking-platform) describes 7 spacing values across `z/D = 0.1..1.0`, 100 command-map points per spacing, and 700 points per rotor set.
- [IEEE RA-L coaxial benchmarking/allocation paper DOI](https://doi.org/10.1109/LRA.2022.3153999) is the formal paper record for the platform and allocation method; public previews report up to `11%` mechanical-efficiency improvement from allocation.

Current mapping:

- Generated file `docs/data/coaxial_interference_reference.csv` contains the platform measurement capabilities, spacing-sweep metadata, qualitative efficiency regions, a public allocation-efficiency claim, the current `coaxialX8` geometry, and a mirrored current wake-interference scan over `z/D = 0.10..1.00`.
- Generated file `docs/data/coaxial_benchmark_11in_target_efficiency.csv` parses the raw 11-inch full-range CSVs, interpolates equal-command curves at 500/1000/1500/1800 g pair thrust, reconstructs mechanical power from measured `abs(torque) * rpm`, and compares those points against the current wake-loss formula.
- Generated file `docs/data/coaxial_benchmark_multi_size_target_efficiency.csv` generalizes the raw archive analysis across usable 11/16.2/22 inch full-range maps, using 35%/60%/85% of each group's common equal-command maximum thrust so different prop sizes can be compared on normalized load.
- Generated file `docs/data/coaxial_benchmark_command_map_envelope.csv` searches the raw 10x10 command maps for the highest measured mechanical-efficiency point within a +/-5% thrust band at the same normalized loads, recording the PWM split and the electrical-efficiency delta separately.
- Generated file `docs/data/coaxial_runtime_allocation_lookup.csv` linearly interpolates those raw envelope points to the current `coaxialX8` spacing `z/D = 0.72`, giving a compact initial lookup of recommended PWM ratio, left/right scale versus equal command, and mechanical/electrical efficiency deltas at 35%/60%/85% normalized load. It also includes `coaxial_runtime_allocation_model_point` rows at 35%/45%/60%/75%/85% load: the central prior uses the nearest measured prop-diameter group, and all-group P10/P50/P90 columns expose uncertainty across the 11/16.2/22 inch data.
- Generated file `docs/data/coaxial_benchmark_surface_fit.csv` fits cubic polynomial command surfaces for `total_thrust_g`, `total_torque_nm`, `mechanical_power_w`, and `electrical_power_w` using normalized commands `u=(PWM-1000)/1000`, with deterministic 5-fold CV metrics for every raw 10x10 command map; use these physical quantities to derive efficiency instead of fitting noisy `g/W` directly.
- Generated file `docs/data/coaxial_allocation_calibration_packet.csv` condenses the current `coaxialX8` geometry/wake scan, 11-inch `z/D = 0.70` allocation rows, multi-size 60% shape/allocation rows, command-map envelope, runtime allocation prior, and surface-fit quality into one narrow table; it is mirrored into `docs/data/fpv_model_validation_summary.csv` as `coaxial_packet_*` categories.
- `coaxialX8` uses `verticalOffset = 0.72R` above and below the arm plane, so the actual upper/lower separation is `1.44R = 0.72D`.
- The Hackaday result log reports 11-inch rotor mechanical-efficiency local maxima around `0.25 < z/D < 0.4` and `0.7 < z/D < 0.85`, so the current X8 spacing lands near the second local maximum.
- Mirroring the current Java axial-wake formula at `z/D = 0.72` with no crossflow gives lower-rotor wake thrust scale `0.930` at hover and `0.810` at max spin, i.e. about `7.0%` and `19.0%` thrust loss before controller/mixer compensation.
- At 1500 g pair thrust in the raw 11-inch equal-command curves, reconstructed mechanical efficiency is `7.16 g/W_mech` at `z/D = 0.70` and `6.95 g/W_mech` at `z/D = 0.55`; the current model changes only from `7.0%` to `7.1%` hover wake loss across those points, so it does not express the measured local efficiency valley.
- The dedicated 11-inch `z/D = 0.70` optimal-fit command file shows unequal commands improve reconstructed mechanical efficiency by about `5.1%` at 1000 g pair thrust and `6.6%` at 1500 g pair thrust versus equal-command interpolation.
- Across the 11/16.2/22 inch full-range groups at 60% of each group's common maximum thrust, the largest measured `z/D` mechanical-efficiency spread is `5.6%` for the 11-inch Propdrive set; the strongest 60%-load optimal-fit mechanical-efficiency gain is `11.6%` for the 22-inch MN501S set at `z/D = 0.40`.
- The raw 10x10 command-map envelope at 60% load finds a strongest mechanical-efficiency gain of `9.4%` over equal-command interpolation for the 22-inch MN501S set, but its electrical-efficiency delta is about `0%`; this is a useful warning to keep torque/RPM mechanical efficiency and voltage/current electrical efficiency as separate calibration targets.
- The z/D=0.72 runtime lookup now gives both raw measured-group lookup rows and a smoothed current-vehicle allocation prior. It is still not a controller or continuous optimizer; because the current simulated rotor diameter is below the measured 11-inch minimum, treat the nearest-group central value as an extrapolated mixer starting point and keep the cross-group bounds visible during tuning.
- The packet summary rows make the current allocation prior easy to pull: at 60% normalized load the smoothed current-vehicle prior recommends PWM R/L `1.328`, left/right scale `0.827/1.095`, mechanical/electrical gain `4.41%/2.84%`, and all-group PWM-ratio bounds `1.218/1.384/1.447` for P10/P50/P90.
- The cubic 10x10 command-surface fits are intended as a compact model-calibration bridge: they preserve full raw-file traceability while exposing coefficients and train/CV fit quality. Median CV RMSE/range is `1.07%` for thrust, `1.06%` for torque, `1.24%` for mechanical power, and `1.08%` for electrical power; the worst CV rows are the repeated 11-inch Propdrive `z/D = 0.40` maps, so keep that file pair as a repeatability warning.
- The current max-spin scan clamps wake swirl velocity at `8 m/s` for the aligned lower rotor, so future calibration should separate thrust-loss magnitude, swirl/vibration telemetry, and mechanical-efficiency/allocation effects instead of fitting one scalar to all of them.

## Battery and power data

Open datasets:

- [NASA PCoE Prognostic Data Repository](https://ti.arc.nasa.gov/tech/dash/groups/pcoe/prognostic-data-repository/)
- [NASA battery data set landing page](https://www.nasa.gov/content/prognostics-center-of-excellence-data-set-repository)
- [NASA/DASHlink Li-ion Battery Aging Datasets page](https://data.nasa.gov/dataset/li-ion-battery-aging-datasets)
- [NASA battery data direct zip, about 210 MB](https://phm-datasets.s3.amazonaws.com/NASA/5.+Battery+Data+Set.zip)
- [Figshare high-current Li-ion EIS dataset](https://figshare.com/articles/dataset/Lithium-ion_battery_dataset_with_impedance_measurements_at_every_cycle_under_high_current_conditions/32578461)
- [CALCE Battery Data](https://calce.umd.edu/battery-data)
- [Open LiPo battery dataset on PMC](https://pmc.ncbi.nlm.nih.gov/articles/PMC10518458/)
- [Mendeley direct LiPo EIS/capacity/ECM dataset](https://data.mendeley.com/datasets/stcppt2r68/1)
- [Mendeley C-rate/temperature galvanostatic discharge dataset](https://data.mendeley.com/datasets/kxsbr4x3j2/2), DOI `10.17632/kxsbr4x3j2.2`, gives NCA/NMC/LFP discharge curves at multiple C-rates and chamber temperatures. It is cylindrical Li-ion cell data, not FPV LiPo pouch-pack ESR, but it is directly useful for C-rate/temperature voltage-shape checks.
- [CHL LiPo internal resistance explainer](https://chinahobbyline.com/blogs/news/lipo-internal-resistance-explained)
- [Oscar Liang 4S FPV LiPo charger-IR measurements](https://oscarliang.com/acehe-formula-4s-95c-lipo-batteries/)
- [Oscar Liang LiPo retirement/internal-resistance guide](https://oscarliang.com/when-retire-lipo-battery/)
- [Wayne Giles / Mark Forsyth LiPo ESR Meter Mark II guide](https://www.baronerosso.it/forum/attachments/batterie-e-caricabatterie/386965d1652293513-calcolo-c-di-scarica-lipo-meter.pdf)
- [Jeffco Aeromodlers LiPo ESR temperature PDF](https://jeffcoaeromodlers.com/wp-content/uploads/2019/05/Lithium-Polymer-Batteries-96-website.pdf)
- [Battery University low/high-temperature discharge guide](https://batteryuniversity.com/article/bu-502-discharging-at-high-and-low-temperatures)
- [Battery University internal-resistance performance guide](https://batteryuniversity.com/article/bu-802a-how-does-rising-internal-resistance-affect-performance)
- High-C product-spec anchors: [Tattu R-Line 4S 1550mAh 150C](https://genstattu.com/tattu-r-line-version-5-0-1550mah-4s-150c-14-8v-lipo-battery-pack-with-xt60-plug/), [Tattu R-Line 6S 1300mAh 160C](https://genstattu.com/tattu-r-line-version-6-0-1300mah-160c-6s-22-2v-st-lipo-battery-pack-with-xt60-plug/), [Tattu R-Line 8S 1300mAh 130C](https://www.rotorama.com/product/tattu-r-line-1300mah-8s-130c-v4), [Gens Ace 6S 5000mAh 45C](https://gensace.de/products/gens-ace-g-tech-5000mah-22-2v-45c-6s1p-lipo-battery-pack-with-ec5-plug), [Tattu Plus 6S 23000mAh 25C HV](https://tattuworld.com/high-voltage/tattu-23000mah-6s-22-8v-25c-high-voltage-g-tech-lipo-battery.html), and [Tattu 6S 22000mAh 25C](https://www.yangdaonline.com/gens-tattu-22000mah-6s-25c-22-2v-lipo-battery-pack-with-as150-xt150-plug/).
- Betaflight current-source anchors: [current.h](https://github.com/betaflight/betaflight/blob/4.5.0/src/main/sensors/current.h#L35-L78), [current.c](https://github.com/betaflight/betaflight/blob/4.5.0/src/main/sensors/current.c#L115-L131), [blackbox.c field/write path](https://github.com/betaflight/betaflight/blob/4.5.0/src/main/blackbox/blackbox.c#L206-L214), and [Blackbox Log Viewer formatting](https://github.com/betaflight/blackbox-log-viewer/blob/master/src/flightlog_fields_presenter.js#L1769-L1784).
- [Foxeer Reaper F4 128K 65A BL32 4-in-1 ESC official page](https://www.foxeer.com/foxeer-reaper-f4-128k-65a-bl32-4in1-9-40v-esc-30-5-30-5mm-m3-g-420), which lists `Current Scaling = 70` for the APdrone ESC family.

How to use them:

- Use NASA/CALCE/PMC/Mendeley datasets for OCV/SOC curve shape, thermal dependency, capacity fade, and internal-resistance trend.
- `docs/data/battery_ocv_soc_reference.csv` extracts CALCE low-current OCV/SOC curves, selected NASA loaded discharge curves, and the current Java OCV/SOC power/sag/spike/ripple formulas into one comparable table.
- `docs/data/battery_dynamic_pulse_response_reference.csv` extracts CALCE SP20-2 DST dynamic current profiles and compares slow post-discharge voltage recovery against the current Java short sag/spike/ripple time constants.
- `docs/data/apdrone_battery_autonomy_reference.csv` extracts APdrone 4S 1500mAh battery-autonomy Blackbox CSVs from the max-power and normal-power RAR archives. It records the APdrone plotting-script thresholds, per-flight voltage/current/throttle/duration rows, and scenario summaries with candidate `amperageLatest` scalings.
- `docs/data/apdrone_battery_resistance_envelope.csv` converts those APdrone battery-autonomy summaries into scale-sensitive resistance proxies. Use the Foxeer-scale and capacity-match rows as practical `apDrone()` pack-R sanity checks; use the Betaflight literal row mostly as evidence that `raw/100 A` is inconsistent with a 1500mAh flight.
- `docs/data/betaflight_apdrone_current_unit_reference.csv` records the Betaflight source-code unit audit, APdrone Blackbox header/config evidence, Foxeer official ESC current-scale evidence, and APdrone battery-capacity current-scale back-calculation.
- Use the Figshare high-current EIS dataset for 18650 impedance-growth shape under sustained high-current cycling: 6 BAK N18650CNP cells, 400 cycles, 4C discharge, 1.6C charge, and EIS every cycle from 50 mHz to 50 kHz.
- Mendeley's fitted ECM CSVs include `SOC, R_0, R_1, Q_1, a_1, R_2, Q_2, a_2, Q, L`; `R_0(SOC, SOH)` is a direct candidate for state-dependent internal resistance.
- NASA's impedance fields include `Battery_impedance`, `Rectified_impedance`, `Re`, and `Rct`.
- Use FPV-specific thrust-stand pages such as Mini Quad Test Bench for high-current voltage sag under prop load.
- `docs/data/high_c_lipo_reference.csv` converts public product specs into continuous-current, energy-density, current-density, and preset-vs-reference rows, and now adds public 4S FPV charger-IR measurements plus Oscar's 1500mAh mini-quad IR health bands as absolute `mOhm/cell` anchors. Manufacturer C-ratings are not ESR measurements.
- `docs/data/lipo_esr_temperature_soc_method_reference.csv` encodes ESR-meter method rules and the conservative IR-based `true C = 2500 / sqrt(capacity_mAh * highest_cell_IR_mOhm)` formula, then applies it to the current battery presets plus `apDrone()`. Its rows are mirrored into `docs/data/fpv_model_validation_summary.csv` as `lipo_esr_*` categories.
- `docs/data/mendeley_c_rate_temperature_file_inventory.csv` inventories the full Mendeley `kxsbr4x3j2` C-rate/temperature dataset: `334` files, `325` xlsx files, and `638.23 MB` total. The script intentionally caches only a representative `84.68 MB` subset under `docs/data/raw/mendeley_kxsbr4x3j2_subset/`.
- `docs/data/lipo_c_rate_temperature_subset_reference.csv` parses `33` selected `k1` curves across NCA/NMC/LFP, `5/25/35 C`, and key `0.05C/1C/2C` rates, plus LFP `10C/20C` high-rate rows. `docs/data/lipo_c_rate_temperature_calibration_packet.csv` condenses those curves into `502` narrow metrics mirrored as `lipo_crate_packet_*`.
- `docs/data/fpv_lipo_esr_calibration_packet.csv` is the targeted handoff layer for runtime battery tuning. `docs/scripts/analyze_fpv_lipo_esr_calibration_packet.py` combines FPV charger-IR anchors, Mendeley `RO(SOC,SOH)` projection, ESR-method guardrails, C-rate/temperature shape rows, and Jeffco cold/warm field ratios into `774` narrow rows mirrored as `fpv_lipo_esr_packet_*`.
- The current `racingQuad()` battery values are `16.8 V nominal`, `13.2 V empty`, `0.018 ohm pack resistance`, `1.5 Ah`, `90 A max`. That is `4.5 mOhm/cell` if interpreted as a 4S pack, which is plausible for a high-C pack but should be validated against a real FPV battery test or manufacturer ESR data before treating it as measured.
- APdrone's battery-autonomy logs are a direct FPV 4S 1500mAh pack anchor. Five max-power tests average `205.9 s` total duration, `198.8 s` inside the interpreted `12-18 V` range, start around `16.65 V`, end around `9.79 V`, and sit at about `98.7` mean throttle command. Five normal-power tests average `511.1 s`, `493.5 s` in `12-18 V`, start around `16.58 V`, end around `9.15 V`, and sit at about `54.4` mean throttle command.
- In those APdrone logs, `currentMeterScale = 400` and `vbatLatest` is best interpreted as centivolts despite the dataset script labeling it as mV. Betaflight source evidence says the logged `amperageLatest` unit is centiamps, so `raw/100` is the source-defined display current; that interpretation only integrates about `296 mAh` per max-power flight and `264 mAh` per normal-power flight. Capacity consistency points instead to `raw/19.72` and `raw/17.61` as scenario-specific physical-current estimates, close to the earlier `raw/20` candidate: max-power mean/P95 current becomes about `25.9/42.1 A` with `21.2 Wh` and `1479 mAh` integrated, while normal-power becomes about `9.30/10.99 A` with `19.4 Wh` and `1321 mAh`. This is best treated as an APdrone current-sensor calibration issue; because Betaflight ADC current is inversely proportional to `ibata_scale`, the capacity-match scale would be roughly `79` for max-power and `70` for normal-power instead of the configured `400`, while Foxeer's official Reaper F4 65A ESC page lists `Current Scaling = 70`.
- Against the same-cell Tattu R-Line 4S 1550mAh 150C reference, `racingQuad` capacity is `0.97x` and current limit is `0.39x` of the listed continuous-current spec. However, using the current preset's `4.5 mOhm/cell` resistance at that reference continuous current would sag about `24.9%` of nominal voltage, so the model should not infer ESR from C-rating alone.
- CHL's practical bands put many fresh high-performance LiPos around `2-5 mOhm/cell`, below `10 mOhm/cell` as a strong fresh-pack target, `10-20 mOhm/cell` as usable/healthy, and above `20 mOhm/cell` as tired for high-performance use.
- Oscar Liang's 1500mAh mini-quad field thresholds are more FPV-specific: below `10 mOhm/cell` is a great pack, `10-15` is fine, `15-20` is old/high-resistance, and above `20` is a retire/relegate signal. Treat these as same-temperature/SOC/capacity/C-rating/age comparisons, not universal lab ESR boundaries.
- Oscar Liang's 4S FPV comparison reports charger IR at the end of charge on packs under 20 cycles; the five usable rows in the script span `4.975..6.850 mOhm/cell`. At the current `racingQuad` 90 A limit, those measured pack resistances imply about `1.79..2.47 V` pack sag.
- The current `racingQuad` `4.5 mOhm/cell` and all five Oscar-measured under-20-cycle 4S packs land in `oscar_1500mah_great`; `racingQuad` is still aggressive because it is `0.90x` the lowest measured row, Acehe Formula 4S 95C 1500mAh, and `0.69x` the measured Tattu 4S 45C 1550mAh row. Treat it as a fresh high-performance pack value, not a generic 4S FPV default.
- The ESR Meter guide adds method constraints: compare packs near `23 C / 72 F`, do not mix temperature/SOC when judging IR, expect a `10 C` drop to nearly double IR, and treat SOC above about `10%` differently from the rapid low-SOC IR rise. Applying the IR-based conservative continuous-current formula to configured mean per-cell IR gives `racingQuad` about `30.43 C / 45.64 A`, so the configured `90 A` limit is `1.97x` that guardrail and `2.79x` a simple 10 C colder/IR-doubled guardrail. For `apDrone()`, `4.0 mOhm/cell` and `1500 mAh` give `32.27 C / 48.41 A`; the configured `150 A` limit is `3.10x` that guardrail and `4.38x` under the same cold-IR-doubled check. Use this as a thermal/sag warning, not a hard burst-current ban.
- `docs/data/battery_temperature_derating_summary.csv` evaluates the current temperature model for `racingQuad`. At 0 C it gives `2.015x` resistance and `0.725x` current scale; at 70 C it gives about `1.068x` resistance, `0.832x` current scale, and `0.784x` thermal power limit.
- Jeffco's RC LiPo ESR temperature PDF gives paired 71F-to-42F total-IR rows for 3S 1000mAh SkyLipo, NanoTech, and GensAce packs. Using the paired ESR Meter and iCharger rows, cold/warm total-IR ratios span `1.677..2.034x`; the current temperature model gives `1.521x` for the same warm/cold pair, about `0.748..0.907x` of those field measurements.
- The new C-rate/temperature subset gives direct loaded-discharge shape checks. At `2C`, 5 C versus 25 C capacity ratios are `0.616` for NCA, `0.830` for NMC, and `0.893` for LFP; mid-discharge voltage also falls by about `0.054`, `0.209`, and `0.211 V/cell` respectively. LFP `20C` at 25 C delivers `2.40 Ah` with `35.0 C` surface-temperature rise, while the selected 5 C row collapses to `0.0045x` the 25 C capacity in under a second. Treat these as high-rate temperature-shape anchors, not FPV absolute ESR.
- The same packet flags applicability: current `racingQuad` is configured at `60C`, which is `30x` the NCA/NMC selected max (`2C`) and `3x` the LFP selected max (`20C`). Use the packet to justify cold/high-C limiting shape and to avoid over-trusting C-ratings, not to set a 60C FPV pack ESR.
- Treat the Jeffco rows as a hobby/field RC LiPo temperature-ratio anchor, not lab EIS and not an FPV high-C absolute ESR source. They still indicate the cold ESR coefficient is likely conservative for small packs.

CALCE/NASA OCV and loaded-voltage extraction:

- Output: `docs/data/battery_ocv_soc_reference.csv`.
- CALCE low-current OCV rows use the public SP20-1 and SP20-3 0 C, 25 C, and 45 C low-current OCV zip files. The script identifies the main low-current discharge and charge segments, coulomb-counts each segment, and averages charge/discharge voltage at matched SOC.
- Extracted result: 72 CALCE OCV/SOC point rows, 36 CALCE temperature/SOC median rows, 48 NASA selected loaded-discharge point rows, 72 current OCV/SOC rows, and 96 current transient voltage scan rows.
- CALCE 25 C medians give `3.665 V/cell` at 50% SOC, `3.463 V/cell` at 10% SOC, and `3.423 V/cell` at 5% SOC. The current 4.2/3.3 V code curve gives `3.822`, `3.552`, and `3.453 V/cell` at those same SOCs.
- NASA B0005 first-cycle loaded discharge gives `3.546 V/cell` at 50% SOC under about 2 A load, versus the current OCV curve's `3.822 V/cell`. Treat this as a terminal-voltage sanity check, not rest OCV.
- The current `racingQuad` steady 25 C scan at 50% SOC and 90 A load gives `15.288 V` OCV, `1.999 V` total sag, and `13.747 V` steady bus voltage before thermal dynamics. At 4% SOC and 90 A, SOC limiting clamps combined power to `0.350`.
- Use this CSV to keep OCV shape, loaded sag, SOC reserve limiting, regen spike, and bus ripple as separate knobs. CALCE OCV data is not high-C FPV LiPo data, but it is a better OCV/SOC anchor than discharge cutoff voltage alone.

CALCE dynamic pulse extraction:

- Output: `docs/data/battery_dynamic_pulse_response_reference.csv`.
- Source rows use the CALCE SP20-2 Dynamic Stress Test files at 0 C, 25 C, and 45 C for 50% and 80% nominal SOC. The script caches `SP2_0C_DST.zip`, `SP2_25C_DST.zip`, and `SP2_45C_DST.zip` under `docs/data/raw/calce_dynamic_profiles/`.
- Extracted result: 258 contiguous step-summary rows, 6 main post-discharge recovery rows, 6 current Java transient-tau rows, and 6 current-vs-CALCE comparison rows.
- At 25 C, the main 1 A discharge pulse rows give initial effective resistance around `79.6..80.3 mOhm` for this 2 Ah INR18650-20R cell. The post-discharge rest recovery reaches about `0.106..0.107 V`; the first rest sample already recovers `72..77%`, but the fitted 63% recovery times are `630 s` at 50% SOC and `79 s` at 80% SOC, with log-fit tails around `1676..2150 s`.
- Current `racingQuad` first-order battery taus are much shorter: sag rise `0.057 s`, sag recovery `0.750 s`, voltage-spike rise/recovery `0.015/0.143 s`, and bus-ripple rise/recovery `0.006/0.044 s`.
- Interpretation: CALCE DST validates that slow battery relaxation/hysteresis is a separate state from subsecond electrical sag. Do not stretch the Java sag recovery tau to minutes; add a separate long polarization/relaxation state if gameplay or telemetry needs sustained-load recovery.

NASA impedance extraction:

- Output: `docs/data/nasa_battery_impedance_reference.csv`.
- The script downloads the direct NASA zip to `docs/data/raw/nasa_battery_aging/Battery_Data_Set.zip`, opens the nested ARC battery-aging archives, and de-duplicates repeated B0025-B0028 files by battery id.
- Extracted result: 1,956 impedance cycles across 34 18650 cells, plus 34 battery-summary rows and 6 SOH/temperature lookup rows.
- The room high-SOH baseline median is `Re = 62.0 mOhm/cell` and `Re+Rct = 164.3 mOhm/cell`. These are lab 18650 absolute values, not FPV high-C ESR values.
- Shape anchors from the lookup: room-temperature worn rows below `0.75 SOH` have median `Re` scale `1.224x` and `Re+Rct` scale `1.397x` versus room high-SOH; hot high-SOH rows around 43 C have median `Re` scale `0.657x`.
- Use NASA `Re` as the ohmic-aging shape check and `Re+Rct` as a slower-load polarization bound. Normalize either one onto a separately measured FPV pack ESR before using it for runtime voltage sag.

Figshare high-current EIS extraction:

- Output: `docs/data/battery_high_current_eis_dataset_reference.csv`.
- Source metadata: Gabriele Patrizi's 2026 Figshare dataset `10.6084/m9.figshare.32578461.v1`, CC BY 4.0.
- Extracted metadata: 6 BAK N18650CNP 2.5 Ah 18650 cells, 400 charge/discharge cycles, 4 A/1.6C charge, 10 A/4C discharge, 30 minute rest after charge/discharge, and 62 EIS frequency points per cycle from 0.05 Hz to 50000 Hz at 100% SOC.
- The script caches the small `EIS_aging_dataset_info.pdf` but does not automatically download the 334 MB `.mat` file; the CSV records the download URL and MD5 for later full parsing.
- `docs/data/battery_high_current_cycle_ir_reference.csv` parses `summary.IR`, `summary.Cap_dis`, and temperature arrays from `EIS_aging_dataset.mat` when that large MAT file is manually present in the system temp directory or `docs/data/raw/figshare_high_current_eis`. It records 2,400 cycle rows plus per-cell first10/last10 IR growth summaries without storing the 334 MB source in the repo.
- Use this as a high-current Li-ion impedance-growth source. It is closer to sustained high-current operation than NASA's aging protocols, but still not FPV LiPo absolute ESR.

Mendeley ECM extraction:

- Script: `docs/scripts/analyze_mendeley_lipo_ecm.py`.
- Outputs: `docs/data/lipo_ecm_mendeley_r0_summary.csv` and `docs/data/lipo_ecm_mendeley_soc_soh_lookup.csv`.
- Large archive endpoint: `https://data.mendeley.com/public-api/zip/stcppt2r68/download/1`; the downloaded zip is about 174 MB and is kept in the local temp directory, not this repo.
- The fitted CSVs actually use column name `RO` for ohmic resistance.
- Extracted result: 57 fitted-cycle files across 5 packs. Mean `RO` spans `75.6-93.0 mOhm/cell`; low-SOC `RO` averages `1.037x` high-SOC `RO`; pack-level first-to-last fitted-cycle mean `RO` growth is about `1.05x-1.20x`.
- The SOC/SOH lookup expands the fitted rows into 549 raw `SOC, RO` samples plus 40 SOC-bin/SOH-band rows and 40 runtime scale rows. The runtime rows normalize `RO` to fresh high-SOC median resistance, so they can be multiplied by a separately calibrated FPV absolute ESR.
- Useful shape anchors from the lookup: fresh cells are about `1.00x` at SOC 1.0 and `1.03x` at SOC 0.1; worn rows below `0.75 SOH` are about `1.15x` at SOC 1.0 and `1.19x` at SOC 0.1.
- `docs/data/lipo_ecm_mendeley_runtime_esr_projection.csv` performs that multiplication for every current preset: it projects the Mendeley `RO(SOC,SOH)` scale onto each configured pack resistance, then reports pack/cell ESR, configured-current sag, loaded voltage, and the current that would produce 20% nominal-voltage sag.
- This is not an FPV high-C absolute ESR source. It is useful for the shape of SOC/SOH-dependent resistance if the absolute scale is separately calibrated to FPV packs.
- `docs/data/fpv_lipo_esr_calibration_packet.csv` selects the high-signal projection scenarios for handoff: fresh/full, fresh/10%, used/50%, aged/50%, worn/full, and worn/10%. For `racingQuad`, fresh/full projects to `18.048 mOhm` pack R and `1.624 V` sag at `90 A`; worn/10% projects to `21.425 mOhm` and `1.928 V`. These remain shape-projected values, not measured FPV absolute ESR.

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
- [Mini Quad Test Bench, Emax Eco 2306 2400kv](https://www.miniquadtestbench.com/assets/components/motordata/motorinfo.php?uid=259)
- [Copper temperature coefficient reference](https://www.copper.org/publications/newsletters/innovations/2001/08/intro_fac.html)
- [Motor insulation-class context](https://www.theaemt.com/resource/insulation-classes-for-electric-motors.html)
- [Infineon IRL40SC228 MOSFET thermal context](https://www.infineon.com/part/IRL40SC228)

Generated thermal file:

- `docs/data/motor_esc_thermal_reference.csv` summarizes the open U8 processed temperature/loss/efficiency maps, MQTB FPV 2306 motor/test-stand metadata, current preset motor/ESC thermal limits and cooling proxies, and copper winding resistance scaling versus temperature.
- `docs/scripts/analyze_motor_thermal_packet.py` generates `docs/data/motor_thermal_packet.csv`, a 240-row narrow handoff packet mirrored into `docs/data/fpv_model_validation_summary.csv` as `motor_thermal_packet_*` categories.
- The U8 dyno is a larger motor and driver than an FPV 2306-class setup, so treat it as an open BLDC thermal/efficiency scale reference rather than direct parameter data.
- The MQTB Emax Eco 2306 page reports tested KV `2300 rpm/V`, motor weight `29.7 g`, stator `23 x 6 mm`, XRotor `40 A` ESC test setup, `4 ms` logger rate, and typical ambient `21.1-23.3 C`. It does not report winding or ESC temperature rise.
- The current preset rows mirror this project's thermal equations under sea-level, unobstructed conditions; measured FPV motor winding temperature and ESC case/junction telemetry would still be the best calibration source.
- The generated comparison rows show `racingQuad` inferred KV is about `1.02x` the MQTB tested KV, and its per-motor current limit is about `0.56x` the MQTB ESC rating; that supports FPV-class electrical scale but not thermal coefficients.
- The thermal packet cross-check makes the current limiter behavior explicit: assuming 25 C ambient, `racingQuad` full-power motor steady temperature proxy is `203.6 C`, or `1.79x` the motor cutoff margin; the ESC full-current proxy is `117.6 C`, or `0.975x` the ESC cutoff margin. Use those rows to validate limiter behavior and gameplay severity, not as measured FPV motor temperatures.
- Copper winding resistance scaling gives about `1.39x` resistance at `125 C` and `1.60x` at `180 C` relative to `25 C`, so hot-motor resistance should not remain constant if the simulation tries to model sustained abuse.
- For `racingQuad`, the current proxy gives motor limit/cutoff `95/125 C`; a full-power no-airspeed steady-rise estimate exceeds the limit, which is a useful red flag for sustained-throttle thermal limiting rather than a measured FPV value.

## ESC electrical dynamics, braking, and desync anchors

Useful sources:

- [alspitz/esc_test](https://github.com/alspitz/esc_test): MIT-licensed RCBenchmark/AutoQuad sample logs for motor/prop/ESC characterization, with six 16 V tests using a DALPROP T7056 three-blade 7-inch prop.
- [fpv-geek/prop-bench](https://github.com/fpv-geek/prop-bench): open FPV prop-bench application documenting load-cell thrust, Betaflight FC telemetry, automated max-thrust/acceleration tests, and CSV export fields.
- [VayuESC Studio](https://github.com/varun29ankuS/Vayu): Apache-2.0 AM32 ESC/thrust-test-bench project with step-response, ramp, sweep, thermal, hold-thrust protocols and CSV export schema. The repository is a protocol/schema lead; no public real bench-run CSVs were found in its tree.
- [Betaflight DShot protocol/API notes](https://betaflight.com/docs/development/API/Dshot)
- [Betaflight DShot RPM filtering](https://betaflight.com/docs/wiki/guides/current/DSHOT-RPM-Filtering)
- [Bluejay ESC firmware README](https://raw.githubusercontent.com/bird-sanctuary/bluejay/master/README.md)
- [AM32 ESC firmware README](https://raw.githubusercontent.com/am32-firmware/AM32/main/README.md)
- [TI SPRABQ7 sensorless trapezoidal BLDC/BEMF application report](https://www.ti.com/lit/pdf/sprabq7)

Generated electrical file:

- `docs/data/esc_electrical_dynamics_reference.csv` mirrors the current Java formulas for inferred KV, torque constant, winding-resistance proxy, voltage headroom, active braking, current proxies, slew timing, and commutation electrical frequency.
- `docs/scripts/analyze_motor_response_dynamics_packet.py` generates `docs/data/motor_response_dynamics_packet.csv`, merging current ESC/braking proxies with RotorS/PX4 actuator lag, Betaflight PR #12562 RPM slew, APdrone urban eRPM timing, and AI-IO low-dynamic rotor-RPM rows.
- `docs/scripts/analyze_esc_test_propbench_packet.py` generates `docs/data/esc_test_propbench_packet.csv`, a 402-row handoff packet mirrored as `esc_test_propbench_packet_*`. It downloads the public `alspitz/esc_test` raw RCBenchmark/AutoQuad logs plus `fpv-geek/prop-bench` and Vayu protocol files, then extracts six 7-inch bench test summaries, 102 RPM-binned thrust/current operating points, AutoQuad duty/RPM summaries, observed 50/100 ms RPM slew, PropBench CSV/test-timing schema rows, and Vayu step-response/sweep/CSV schema rows.
- `docs/scripts/analyze_wavelab_pelican_motor_response_packet.py` generates `docs/data/wavelab_pelican_motor_response_packet.csv`, a 2584-row WAVELab AscTec Pelican handoff packet mirrored as `pelican_motor_packet_*`. The source provides 54 flights, 100 Hz Vicon/IMU/motor data, actual `Motors`, and commanded `Motors_CMD`; the loaded MAT has 1,388,410 samples / 3.857 h, matching the PDF. Its motor speed unit is the AscTec integer unit `[0,218]`, not mechanical RPM, and the dataset applies a 5-sample smoothing filter.
- `docs/scripts/analyze_nanodrone_sysid_packet.py` generates `docs/data/nanodrone_sysid_packet.csv`, a 1092-row IDSIA Nano-Quadrotor/Crazyflie 2.1 Brushless system-identification packet mirrored as `nanodrone_sysid_packet_*`. The local Git LFS CSV pull covers `15` files, `75,096` rows, `100 Hz`, and `750.81 s`; the columns include 13D state, body acceleration, and four motor angular velocities in `rad/s`.
- The generated protocol rows anchor raw DShot frame timing: DShot300 is `53.33 us` for a 16-bit frame and DShot600 is `26.67 us`, both with `2000` throttle steps in the Betaflight API description. These are wire-level bounds, not end-to-end ESC update latency.
- Bluejay and AM32 are useful open ESC firmware references because they document DShot300/600, bidirectional DShot/RPM or ESC telemetry, startup behavior, and PWM-frequency features that the simulation currently abstracts into slew, braking, ripple, and desync terms.
- `racingQuad` currently infers about `2341 rpm/V` and `Kt = 0.0041 N*m/A`. At configured max thrust, back-EMF leaves only `0.259` drive-voltage headroom, and the `shaft_power / (V * 0.75)` current proxy is `45.77 A` per motor, about `2.03x` the configured per-motor battery-current budget.
- The same `racingQuad` active-braking proxy gives `29.33 A` and `0.120 N*m` per motor at max RPM with a `14.06 ms` braking response tau proxy. Treat this as an ESC stress/sanity calculation until matched against blackbox RPM deceleration and current traces.
- The motor-response packet makes that caveat quantitative: PR #12562's strongest 50 ms positive slew is `503271 rpm/s`, about `0.78x` current `racingQuad.maxRPM/motor_tau`, while the strongest negative slew is only `0.254x` the current braking proxy. APdrone urban logs add a separate command/RPM level-lag P50 of `47.9 ms` and weak first-order tau P50 of `3.95 ms`, so do not collapse log correlation lag into a single motor time constant.
- The open bench packet adds a controlled-stand but adjacent-scale check: the six 7-inch RCBenchmark logs contain `67,972` samples and give static `T/omega^2` P50 `4.45e-6 N/(rad/s)^2`, about `3.07x` current 5-inch `racingQuad.k`, as expected for the larger prop. The strongest observed 50 ms RPM slew is `253,619 rpm/s`, or `0.392x` the current spin-up proxy; strongest negative slew is `272,067 rpm/s`, only `0.131x` the current active-braking proxy. High-RPM torque/mechanical-power rows are empty, so do not use this source for `yawTorquePerThrustMeter`.
- The same packet records protocol leads: PropBench uses a `5 s` max-thrust ramp, `1 s` hold, `1 s` acceleration ramp, and `200 ms` FC telemetry poll; Vayu's default step-response goes from `20%` to `80%` throttle with `50 ms` response sampling, while its general collection loop samples every `100 ms`. Vayu's README still lists torque measurement integration as future work, so treat its torque export as force-arm-derived schema rather than direct shaft-torque evidence.
- The WAVELab Pelican packet adds a cleaner system-ID response prior: derivative cross-correlation gives command-to-actual lag P50/P90 of `30/50 ms`. After fitting a static `actual ~= gain*command + offset` map, the first-order response tau P10/P50/P90 is `0.182/0.320/1.031 s`; the P50 is `7.11x` current `racingQuad.motor_tau`. Treat this as an in-flight autopilot/platform/smoothing envelope, not a direct FPV ESC step response.
- The Nano-Quadrotor packet is a strong unit/semantics check for `omega^2` thrust: the repository source constant `Kt = 3.72e-08 N/(rad/s)^2` predicts `mass * az_body` with RMSE `0.0147 N` and `R2 = 0.958`; the all-data no-intercept fit is `3.716e-08`, or `0.999x` the source constant, and a train-fit/test-eval still gives `R2 = 0.953`. Its torque proxy rows are intentionally caveated: roll/pitch/yaw no-intercept coefficients are only `0.077x/0.038x/-0.566x` the source values with low R2, so use them for motor-order/coordinate audits, not direct torque constants.
- Scale separation: the Nano-Quadrotor source `Kt` is only `0.0257x` current `racingQuad` rotor `k`, while the dataset motor-input distribution has P95/max `1994/2531 rad/s` (`1.46x` current racingQuad hover-speed proxy but `0.829x` max-speed proxy). Treat it as system-ID and motor-input evidence, not a 5-inch FPV coefficient transplant.
- The heavy-lift presets are mixed: `heavyLift` is also above the per-motor budget at max thrust (`1.80x`), `hexLift` is close (`1.19x`), while `octoLift`/`coaxialX8` are below (`0.78x`). That makes max-thrust/current-limit/voltage-headroom calibration a preset-specific check, not a single global constant.
- The phase-current proxy in the CSV is torque divided by `Kt`; it is not the same as DC battery current. Use the power-current rows for pack sag and battery-limit sanity.

## Sensor, filtering, and control references

Betaflight references:

- [Betaflight rate calculator](https://betaflight.com/docs/wiki/guides/current/Rate-Calculator)
- [Betaflight `rc.c` rate formulas](https://raw.githubusercontent.com/betaflight/betaflight/master/src/main/fc/rc.c)
- [Betaflight 4.5.0 `rc.c` rate formulas](https://raw.githubusercontent.com/betaflight/betaflight/4.5.0/src/main/fc/rc.c)
- [Betaflight 4.5.0 control-rate profile header](https://raw.githubusercontent.com/betaflight/betaflight/4.5.0/src/main/fc/controlrate_profile.h)
- [Betaflight Configurator RateCurve.js](https://raw.githubusercontent.com/betaflight/betaflight-configurator/master/src/js/RateCurve.js)
- [Betaflight PID tuning guide](https://betaflight.com/docs/wiki/guides/current/PID-Tuning-Guide)
- [Betaflight DShot RPM filtering](https://betaflight.com/docs/wiki/guides/current/DSHOT-RPM-Filtering)
- [Betaflight DShot protocol/API notes](https://betaflight.com/docs/development/API/Dshot)
- [Betaflight blackbox logging guide](https://betaflight.com/docs/wiki/guides/current/Black-Box-logging-and-usage)
- [Betaflight PID tuning tab reference](https://betaflight.com/docs/wiki/app/pid-tuning-tab)
- [Betaflight blackbox source field table](https://raw.githubusercontent.com/betaflight/betaflight/master/src/main/blackbox/blackbox.c)
- [Betaflight PR #12562 RPM blackbox logs](https://github.com/betaflight/betaflight/pull/12562)
- [orangebox Python Blackbox parser](https://pypi.org/project/orangebox/)
- [Public Betaflight issue blackbox log attachment](https://github.com/betaflight/betaflight/files/5507542/LOG00078.TXT)
- [blackbox-library parser project](https://github.com/maxlaverse/blackbox-library) and [normal.bfl fixture](https://raw.githubusercontent.com/maxlaverse/blackbox-library/master/fixtures/normal.bfl)
- [ExpressLRS switch/channel resolution](https://www.expresslrs.org/software/switch-config/)
- [ExpressLRS Lua packet-rate notes](https://www.expresslrs.org/quick-start/transmitters/lua-howto/)
- [ExpressLRS RF-mode and sensitivity table](https://www.expresslrs.org/info/signal-health/)

Useful values and concepts:

- RPM filtering uses per-motor RPM telemetry and usually filters the first three harmonics per motor.
- For a quad, Betaflight's RPM filter default can create 36 notches: 4 motors * 3 harmonics * 3 gyro axes.
- Betaflight dynamic notch guidance gives practical frequency bands and Q-factor ranges; the docs mention examples like one notch with high Q when RPM filtering is active, and wider/multiple notches when RPM filtering is absent.
- Betaflight's rate/expo/super-rate behavior is a useful conceptual anchor for `rateExpo`, `rateSuper`, and max rate fields. The current analysis checks the project formula directly: the project curve preserves full-stick `maxRate` and uses `rateSuper` with `rateExpo` to set center/mid-stick authority, so Betaflight UI numbers are not drop-in parameters unless the full-stick target and center sensitivity are converted explicitly.

Generated timing/filter checks:

- The report's `RPM, filtering, and command timing sanity` section derives hover/max RPM, configured blade-pass frequency from `RotorSpec.bladeCount`, three-blade reference frequency, gyro LPF, RC frame interval, ESC command frame interval, and configured latency/smoothing.
- `docs/data/rc_esc_timing_reference.csv` separates protocol references from current preset settings: ELRS packet-rate anchors, DShot raw frame timing and 2000-step throttle resolution, Blackbox log-rate anchors, and current preset RC/ESC frame/latency/quantization ratios.
- `docs/data/control_rate_reference.csv` records Betaflight race/freestyle rate bands, the Betaflight Actual Rates formula, the current `DronePhysics.shapeRateInput` formula, all preset axis max rates/center slopes, and stick scans at 0/0.25/0.50/0.75/1.00 input.
- `docs/data/apdrone_rate_envelope_reference.csv` records Betaflight 4.5 Actual Rates source formulas, APdrone dump/log rate profiles, current `apDrone()` curve scans, and APdrone Blackbox setpoint/gyro envelope summaries. The main takeaway is that current `apDrone()` now matches the `670 deg/s` urban/battery Actual target with `70 deg/s` center sensitivity, while `rate_limit = 1998 deg/s` is only a clamp.
- `docs/data/apdrone_throttle_curve_reference.csv` records Betaflight 4.5 throttle-limit and thrust-linearization formulas, current `apDrone()` throttle-to-thrust scans, APdrone log throttle distributions, and urban eRPM-to-mechanical-RPM checks. The main split is that normal-power logs validate the current hover throttle, while urban eRPM suggests the low-to-mid throttle RPM curve needs a separate motor-telemetry fit if exact Betaflight behavior is required.
- `docs/data/apdrone_urban_motor_rpm_reference.csv` records APdrone urban motor-output/eRPM telemetry fits: all-motor RPM-vs-command regressions, loaded effective KV bands, same-RPM static thrust projections, command/RPM correlation lag, and weak first-order tau diagnostics.
- `docs/data/motor_response_dynamics_packet.csv` records 210 combined motor-response rows from RotorS/PX4, Betaflight PR #12562, APdrone urban eRPM, AI-IO low-dynamic RPM, and current ESC/braking formulas. Use it as the handoff packet for `motor_tau`, active-braking, and command/RPM timing checks.
- `racingQuad` roll/pitch uses `720 deg/s` full-stick rate, above Betaflight's common race-rate guide (`550-650 deg/s`) but below the common freestyle guide (`850-1200 deg/s`); because center slope is `257 deg/s/stick`, the tune is high-endpoint but soft around center.
- `docs/data/blackbox_rpm_log_reference.csv` parses five raw `.bbl` zip attachments from Betaflight PR #12562. The stress log exposes `RPM[0..3]`; four smaller logs expose `eInterval[0..3]`. All five are Betaflight 4.5.0 F411 logs with `motor_poles = 12`, `dshot_bidir = 1`, `looptime = 312 us`, and about `3205 Hz` estimated main logging rate.
- `docs/data/blackbox_rpm_decoded_summary.csv` and `docs/data/blackbox_rpm_decoded_samples.csv` decode those PR #12562 `.bbl` files with `orangebox 0.5.0`. The PR patch shows early `RPM[]` values are `eRPM/100`, while later `eInterval[]` values are one electrical-revolution interval in microseconds.
- `docs/data/blackbox_rpm_response_model_comparison.csv` compares decoded 50 ms RPM slew against the current `racingQuad` first-order `maxRPM / motor_tau` proxy and active-braking `maxRPM / brakingTau` proxy.
- `docs/data/blackbox_rpm_filter_frequency_reference.csv` converts current preset RPM and decoded PR #12562 mechanical RPM into motor fundamental frequency, second/third harmonics, three-blade blade-pass frequency, and gyro-LPF/blade-pass ratios for RPM notch and vibration-band sanity checks.
- `docs/data/blackbox_gyro_rpm_spectrum_reference.csv` decodes `gyroADC[0..2]` from the same PR #12562 logs, computes vector Welch spectra, and compares RPM-derived blade-pass aliases against measured gyro-band RMS.
- The decoded `rpm_bb_stress_002` log has `87279` frames over `27.6 s`; after the 12-pole conversion, all-motor median/max mechanical RPM are about `24817/42917`. That places the current `racingQuad` max RPM (`29138`) inside an actual Betaflight RPM-log magnitude range.
- On that same stress log, the strongest 50 ms positive slew is in the same order as the current `motor_tau` proxy, while decoded negative slews are well below the current active-braking proxy. Treat this as a log-derived sanity check, not a controlled ESC step test.
- The same RPM-frequency table puts current `racingQuad` hover/max blade-pass at about `651/1457 Hz`; PR #12562 `rpm_bb_stress_002` reaches about `2083/2146 Hz` for three-blade-equivalent P95/max. Use this as a frequency-range comparison only because the public log's prop family and flight condition are not guaranteed to match the sim preset.
- The stress log gyro spectrum is dominated by low-frequency energy: `5-60 Hz` vector RMS is about `65.3` decoded gyro units, while the +/-10 Hz band around the decoded max-H3 alias near `1029 Hz` is only about `0.049` decoded gyro units. Use these logs for RPM telemetry, alias bookkeeping, and response-shape sanity, not as high-frequency flight propwash/vibration fits.
- The `stalled fixed 009` log decodes structurally but has `0%` valid RPM frames because every `eInterval[]` sample is `65535`, which the PR source uses as no valid telemetry. Keep it as a missing-telemetry failure anchor rather than a motor-response curve.
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
- Generated file `docs/data/apdrone_imu_noise_log_reference.csv` adds APdrone-specific Blackbox log estimates. It uses `strict_static` windows (`rcCommands[3] == 0` and setpoint axes zero) and broader `zero_throttle_low_motion` windows (`gyro norm <= 15 deg/s` and acceleration norm within `250` counts of the Blackbox `acc_1G` header). The APdrone headers report `acc_1G = 2048` and `blackbox_high_resolution = 0`.
- For `racingQuad`, the configured `0.025 rad/s` gyro noise is about `20.8x` an MPU-6000/6050 electronics-only RMS estimate at 120 Hz LPF and about `37.2x` an ICM-42688-P estimate. Treat it as residual vibration plus electronics, not bare IMU electronics noise.
- For `apDrone`, the strict static APdrone log segment is short (`0.607 s`, `1217` samples) but gives gyro vector RMS `0.0110 rad/s` (`0.44x` current `0.025`) and accelerometer vector RMS `0.0538 m/s^2` (`0.27x` current `0.20`). Across `43` zero-throttle/low-motion segments from `21` files (`89.2 s`, `177k` samples), gyro P50/P90 is `0.00534/0.0249 rad/s` (`0.21x/1.00x` current), while accelerometer P50/P90 is `0.0315/0.121 m/s^2` (`0.16x/0.61x` current). This supports the current APdrone gyro noise as a high-percentile residual-vibration/noise setting and suggests the accelerometer noise field is conservative unless it intentionally includes maneuver/propwash contamination.

Barometer/altimeter anchors:

- [Bosch BMP280 datasheet](https://www.bosch-sensortec.com/media/boschsensortec/downloads/datasheets/bst-bmp280-ds001.pdf)
- [Bosch BMP388 datasheet](https://www.bosch-sensortec.com/media/boschsensortec/downloads/datasheets/bst-bmp388-ds001.pdf)
- [Infineon DPS310 datasheet](https://www.infineon.com/dgdl/Infineon-DPS310-DataSheet-v01_02-EN.pdf?fileId=5546d462576f34750157750826c42242)
- [TE/Measurement Specialties MS5611-01BA03 datasheet](https://www.hpinfotech.ro/MS5611-01BA03.pdf)
- Generated file `docs/data/barometer_reference_summary.csv` converts pressure noise and relative accuracy to equivalent altitude error with `dh = dp / (rho g)`.
- Generated file `docs/data/apdrone_baro_noise_log_reference.csv` adds APdrone Blackbox `baroAlt` checks using the same static/low-motion selectors as the IMU analysis. It interprets `baroAlt / 100` as meters, uses `strict_static` windows (`rcCommands[3] == 0` and setpoint axes zero), and broader `zero_throttle_low_motion` windows (`gyro norm <= 15 deg/s`, acceleration norm within `250` counts of `acc_1G`).
- APdrone's strict static barometer window is short (`0.607 s`, `1217` samples) but gives raw `baroAlt` std `0.0626 m`, detrended std `0.0449 m`, peak-to-peak `0.21 m`, and linear drift about `0.249 m/s`. That detrended std is `8.4x` the current code's quiet `apDrone` barometer-noise RMS (`0.00532 m`) and `2.7x` the DPS310 pressure-noise altitude equivalent (`0.01665 m`).
- Across `43` zero-throttle/low-motion APdrone windows from `21` files (`89.2 s`, `177k` samples), `baroAlt` detrended std P50/P90 is `0.0473/0.131 m`, peak-to-peak P50/P90 is `0.21/0.958 m`, and absolute linear slope P50/P90 is `0.037/0.644 m/s`. Some windows have exactly flat `baroAlt`, indicating output quantization/hold behavior, while high-percentile windows show decimeter-scale local pressure or filter transients even with low gyro/GPS motion.
- Current takeaway: good MEMS pressure noise is centimeters-to-decimeters in altitude, Betaflight `baroAlt` output can show decimeter-scale quantization/filter/environment variation in real APdrone logs, and the project's flow/static-port model can create meter-scale barometer error at FPV airspeeds. Keep these three concepts separate.

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

Targeted handoff for the coding agent:

- `docs/fpv-sim-targeted-calibration-gap-hunt.md` ranks the remaining calibration gaps by model risk and explains which open datasets, papers, or projects should be extracted next.
- `docs/data/targeted_calibration_gap_leads.csv` is the companion machine-readable work queue. It maps each gap to affected model fields, source URLs, extraction targets, fit risks, and suggested next scripts.

## Priority cross-checks for the coding agent

1. Reconcile 5-inch rotor speed scale.
   - The calibrated `racingQuad` k should imply about 27k-30k rpm for 12-13 N static thrust.
   - UIUC/MQTB 5-inch data suggests k around `0.9e-6..1.6e-6`.

2. Reconcile yaw torque coefficient.
   - UIUC 5-inch examples give `Q/T` around `0.0116..0.0141 m`.
   - Tyto selected FPV static tests give fitted `Q/T` around `0.0094..0.0146 m`; the current `apDrone()` value `0.01357 m` sits inside the selected range, at `0.930x` the Gemfan 5040/T-Motor LF40 fit and `1.19x` the Five33/Azure Vanover fit, while `racingQuad()` `0.0140 m` is still inside the UIUC/Tyto combined range.
   - RotorS uses `0.016 m`.
   - PX4 Iris uses `0.06 m`.
   - The calibrated racing preset uses `0.014 m`, close to UIUC 5-inch ranges and still far from PX4 Iris.

3. Separate physical data from gameplay scaling.
   - If Minecraft scale or tick feel requires nonphysical RPM, document that layer explicitly and avoid using physical telemetry labels for the scaled value.

4. Use UIUC forward-flow curves for `advanceRatio`, `transverseFlowLift`, `axialFlowLoss`, low-Re loss, stall/CT rolloff validation, and same-RPM `CP`/power-ratio validation.

5. Use rotorcraft high-advance-ratio sources for `retreatingBladeStall` only as threshold-order anchors.
   - The generated `rotor_blade_dissymmetry_reference.csv` maps current `mu` thresholds and speed points.
   - UIUC small-prop data does not reach the `mu = 0.42..0.82` band, so it cannot validate retreating-blade stall directly.

6. Use MiniQuad Test Bench and Tyto Robotics static tests for per-motor current, voltage, thrust, and RPM scale on FPV racing presets.

7. Use RotorS/PX4/Flightmare/gym-pybullet-drones/RotorPy as sanity checks for airframe mass, inertia, motor time constants, simple `T = k*omega^2` structure, and body-drag order of magnitude.

8. Reconcile drag coefficients.
   - Current `linearDragCoefficient` is runtime linear damping in `N/(m/s)`; `bodyDragCoefficients` are quadratic in speed.
   - `racingQuad` produces about `0.19x` weight of base X-axis drag at 10 m/s and about `0.43x` at 20 m/s before separated-flow terms.
   - UZH-FPV/RATM speed envelopes are not blocked by the corrected base drag; use trajectory/force data to separate physical CdA, rotor drag, prop unloading, and any deliberate gameplay damping.

9. Reconcile blade-pass semantics.
   - Blade-pass ripple and gyro notch frequency now use per-rotor `RotorSpec.bladeCount`.
   - Keep the count aligned with the prop family used for calibration; three-blade FPV logs should compare against 3x mechanical motor frequency, while larger two-blade lift props can remain at 2x until better data is available.
   - If a preset intentionally uses a synthetic harmonic for feel, avoid labeling that notch as measured physical blade-pass frequency.

10. Reconcile wind/gust semantics.
   - Keep the low-altitude Dryden colored-noise turbulence component separate from the reduced deterministic burble used for obstacle/propwash feel.
   - Current RMS intensity can be near Dryden while the one-pole gust corner remains much faster than Dryden's low-altitude longitudinal scale; tune spectrum/length scale separately from amplitude.

11. Reconcile IMU noise semantics.
   - Datasheet electronics noise is much lower than the current configured noise at the project LPF bandwidths.
   - If these values include frame vibration, prop imbalance, and aliasing, label them as residual FPV sensor noise rather than bare IMU electronics.

12. Reconcile barometer error semantics.
   - MEMS pressure-sensor noise should be centimeters-to-decimeters when converted to altitude near sea level.
   - Meter-scale barometer excursions should be attributed to propwash/static-port/dynamic-pressure error, not raw sensor noise.

13. Reconcile battery temperature effects.
   - Current coefficients are directionally plausible: cold raises resistance and reduces current scale; heat drives thermal power limiting.
   - The Jeffco 71F-to-42F RC LiPo rows suggest the current cold-resistance slope is still conservative but closer after the Java deep-cold term: measured paired total-IR ratios are `1.677..2.034x`, while the current model gives `1.521x` for that same temperature pair.
   - Keep this separate from SOC/SOH resistance growth and calibrate coefficients with high-C FPV pack ESR versus temperature if available.

14. Reconcile blackbox RPM units before using logs for validation.
   - Betaflight blackbox `eRPM[]` values are logged as electrical RPM divided by 100.
   - Convert through motor pole count before comparing to simulated mechanical RPM or blade-pass frequency.
   - Public logs without `eRPM[]` should only be used for timing, gyro, accelerometer, and motor-command field validation.

## Gaps still worth filling

- Better FPV-specific numeric data for 5-inch prop dynamic inflow time constants, flapping coefficient, measured arm dimensions/modal response, and blade-pass vibration; STARMAC gives a flapping scale anchor, NASA CR-2017-219428 gives small-UAS prop coning/deflection anchors, beam theory gives arm-flex sensitivity bounds, and the DJI Mini 2 dataset gives raw fault-vibration spectra, but none of these is a direct 5-inch racing-quad flight/bench fit.
- FPV-scale airframe drag measurements, ideally wind-tunnel, coast-down, or onboard-log fits separated by attitude and axis. NASA Fig. 30 now provides a bare-frame small-UAS lower-bound and NASA Figs. 18/20/22 provide low-speed powered full-airframe small-UAS anchors, while RATM and UZH-FPV provide high-speed trajectory/log anchors. APdrone open-field GPS events now add a useful negative result: the powered strict-trim filters find no `>=8 m/s` or `>=12 m/s` candidates, so those logs should constrain the speed envelope and maneuver dynamics but not be used as a clean physical drag fit without attitude/wind.
- Open high-C LiPo internal resistance versus SOC and temperature for FPV-sized 4S/6S packs; CALCE gives OCV/SOC shape, Mendeley/NASA/Figshare give SOC/SOH/high-current aging shape, Oscar Liang gives room-condition 4S absolute anchors, Jeffco gives a small RC LiPo cold/warm ratio, and the new Mendeley C-rate/temperature packet gives direct NCA/NMC/LFP discharge-shape rows, but a full FPV high-C pouch-pack SOC/temperature sweep is still missing.
- Digitized multirotor ground-effect table for thrust multiplier versus `h/R`; ZJU equation-level mapping is now in `docs/data/zju_ground_effect_model_reference.csv`, and flat-wall obstruction geometry/current-model mapping is now in `docs/data/surface_obstruction_geometry_reference.csv`, but raw figure/table digitization is still needed for high-speed ground effect, partial-ground escape, and measured wall force/moment/thrust-loss calibration.
- Higher-accuracy Shetty/Selig thrust-fluctuation digitization or raw time histories, plus FPV-specific propwash torque/noise tables versus normalized descent rate; the current CSVs now have a low-precision measured-envelope digitization, the text `+/-30%` small-prop VRS buffet upper-bound, and a Stack/Leishman rotor-revolution time-scale packet, but not a fitted statistical model or direct FPV propwash torque data.
- Validate/adapt the smoothed z/D=0.72 coaxial allocation prior against the actual simulated rotor mapping, including command limits, motor response, and whether the benchmark left/right channel convention maps cleanly to the project's upper/lower rotor ordering.
- Decoded Betaflight PR #12562 blackbox RPM traces, plus real flight logs for a known 5-inch quad with gyro spectrum and propwash recovery.
