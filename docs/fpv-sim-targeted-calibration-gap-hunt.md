# FPV simulation targeted calibration gap hunt

Generated: 2026-06-12

This is a handoff note for the coding agent that is tuning the FPV simulation. It does not change the runtime model. It identifies calibration gaps that still look data-limited after the existing validation packets, then points to open datasets, papers, and projects that are worth extracting next.

Companion machine-readable table: `docs/data/targeted_calibration_gap_leads.csv`.

## Priority read

The current strongest risk is not the static 5-inch thrust scale. That part is now reasonably supported by UIUC, Mini Quad Test Bench, Tyto-style static data, and the APdrone/Foxeer proxy packet. The largest remaining gaps are dynamic and coupled effects:

1. Airframe drag and high-speed residual aerodynamics.
2. Rotor forward-flow/high-advance thrust and power rolloff.
3. FPV-sized high-C LiPo ESR versus SOC, temperature, and age.
4. Ground, wall, ceiling, and partial-ground proximity forces.
5. VRS/propwash buffet statistics and torque/noise, not just mean thrust loss.
6. Motor/ESC response and active braking from controlled steps or RPM telemetry.
7. Frame flex, blade-pass vibration, and prop-damage amplitude semantics.
8. Rain/wetness/icing as separate phenomena.

## P0 gaps

### Airframe Drag And High-Speed Residuals

Affected fields and code paths:

- `DroneConfig.linearDragCoefficient`
- `DroneConfig.bodyDragCoefficients`
- separated-flow and pressure-center terms in `DronePhysics`
- any controller or gameplay layer that assumes the current `racingQuad` can reach real FPV racing speed envelopes

Current symptom:

- Existing validation shows `racingQuad` drag at 10 m/s is multiple times above IMAV/RPG/RotorPy/NASA references, and it blocks UZH/RATM racing speed envelopes unless treated as gameplay damping.

Targeted data leads:

- [Race Against the Machine dataset](https://github.com/tii-racing/drone-racing-dataset): high-speed 5-inch racing data with synchronized flight CSVs, control inputs, battery voltage, IMU, and mocap. Use it for speed-envelope feasibility and residual-force fitting, with the caveat that it is powered/aggressive flight rather than passive coast-down.
- [NeuroBEM dataset/project](https://rpg.ifi.uzh.ch/NeuroBEM.html) and [dataset readme](https://download.ifi.uzh.ch/rpg/NeuroBEM/Readme.md): 400 Hz agile quadrotor data with body velocity, motor speeds, battery voltage, predicted force/torque, and residual force/torque columns. Use it to separate residual-aero structure from thrust and attitude effects, but not as a racing-speed envelope: the cached `Flights.txt` target-velocity comments span only `0.25..3.25 m/s`.
- [Blackbird Dataset](https://github.com/mit-aera/Blackbird-Dataset): motor RPM/PWM, IMU, and motion-capture ground truth. Lower speed than RATM/UZH, but cleaner actuator/state pairing. The local source-inventory packet `docs/data/blackbird_source_inventory_packet.csv` parses `166` README preview links and the official downloader schema, but current sample raw CSV probes return HTTP `502`. The mirror probe `docs/data/blackbird_mirror_probe_packet.csv` verifies that the reachable Academic Torrents entry is camera/video oriented (`747` files, `4.79 TB`, `0` CSV or rotor/PWM/state/groundTruth matches), so Blackbird remains a raw-CSV mirror/retry lead before residual fitting.
- [WAVELab Pelican dataset](https://github.com/wavelab/pelican_dataset): 54 flights with Vicon, IMU, actual motor speeds, and commanded motor speeds. Good for system-ID method cross-checks.
- NASA/IMAV/Manchester wind-tunnel or drag-fit references already in the local validation docs should remain lower-bound and uncertainty anchors.

Recommended extraction:

- Build a residual-force packet from NeuroBEM/RATM/Blackbird/Pelican: transform velocity into body axes, reconstruct acceleration from ground truth, subtract gravity and thrust proxy, then regress residual force against body-axis velocity, attitude, throttle/RPM, and dynamic pressure.
- Start from `docs/data/neurobem_source_inventory.csv`, which maps NeuroBEM columns `15..17` to body velocity, `21..24` to motor speeds, `29` to battery voltage, `36..38` to residual force, and `39..41` to residual torque.
- `docs/data/neurobem_drag_residual_packet.csv` now adds the NeuroBEM force/torque residual pass over `1,816,329` prediction rows. It emits `17293` packet rows with file-level `Flights.txt` metadata joins, all `13` official test-set segment flags, speed bins, trajectory-family summaries, and target-velocity summaries. Global residual-force P95 is `0.915 N`; global residual-torque P95 is `0.02276 N*m`, or `0.650x` current `racingQuad` propwash max torque. It is useful for residual-force/residual-torque scale and structure, but its residuals are not total drag or isolated moment coefficients.
- `docs/data/ratm_accel_drag_residual_packet.csv` now adds a RATM high-speed acceleration/drag feasibility pass from the six fastest 1 s windows. Current `racingQuad` drag at each vmax implies median `78.87 m/s^2` drag-only deceleration, while median observed absolute speed-rate at vmax is `5.24 m/s^2`; use this as a powered-flight feasibility check before any direct CdA fit.
- `docs/data/ratm_thrust_vector_feasibility_packet.csv` now adds a RATM high-speed attitude/thrust-command pass from the same six fastest windows. Across `827` samples at or above `21 m/s`, median body-Z/velocity angle is `77.95 deg`, median mean thrust command is `0.325`, and the median command-projection proxy is only `0.0406x` current drag/max-thrust demand; at the six vmax samples the median is `0.0245x`. Use this as a sign/scale guard before accepting current high-speed drag magnitude.
- `docs/data/airframe_cda_guard_packet.csv` now adds the first CdA/force-law guard pass from IMAV, NASA, RotorPy, RPG, ICAS, RATM, APdrone, and Manchester. Use it before changing `linearDragCoefficient` or `bodyDragCoefficients`: the current shared quadratic `linearDragCoefficient` alone exceeds the IMAV/NASA total drag targets, so a physical CdA fit cannot be achieved by tuning only per-axis body drag.
- `docs/data/blackbird_source_inventory_packet.csv` is the Blackbird schema handoff: `209` rows, `166` README preview flights, `9` expected CSV files, and `12` raw CSV endpoint probes. `docs/data/blackbird_mirror_probe_packet.csv` is the current availability handoff: official root plus four raw CSV probes return `502`, `7` open GitHub issues mention download/server/link/access problems, and the Academic Torrents metadata has `0` usable CSV/RPM/mocap files. Do not schedule Blackbird residual fitting until the official endpoint recovers or a mirror explicitly lists `csv/blackbird_slash_rotor_rpm.csv`, `csv/blackbird_slash_pwm.csv`, `csv/blackbird_slash_state.csv`, and `groundTruthPoses.csv`.
- Do not fit a single `CdA` from APdrone urban/open-field deceleration unless wind, attitude, and thrust are separable.

### Rotor Forward-Flow And High-Advance Power Rolloff

Affected fields:

- `RotorSpec.transverseFlowLift`
- `RotorSpec.axialFlowLoss`
- `RotorSpec.diskDragCoefficient`
- `HighAdvanceRotorCalibration`
- the code's `mu = V/(omega R)` mapping versus propeller-database `J = V/(nD) = pi * mu`

Current symptom:

- UIUC 5-inch fits predict substantial same-RPM CT/CP rolloff at `J ~= 0.45`, while the current runtime airflow multiplier can still be above 1 in that region. The new J/mu guard packet makes the pi-factor explicit, but direct 5-inch wind-on-thrust-stand dynamic data are still missing.

Targeted data leads:

- [UIUC propeller database](https://m-selig.ae.illinois.edu/props/propDB.html): static, wind-tunnel, and geometry files; use it for `J`, CT, CP, pitch/chord geometry, and Re dependence.
- [AirShaper Mejzlik propeller study](https://www.airshaper.com/research/propeller-study): public wind-tunnel/CFD comparison table at `J=0.2..0.8`. The local packet `docs/data/mejzlik_wind_tunnel_prop_packet.csv` extracts the table and finds wind-tunnel `CT=0` near `J=0.784` / code `mu=0.250`; this is useful as a measured axial high-`J`/windmilling boundary, but it is not an FPV 5-inch edgewise RBS dataset.
- [Tyto Robotics database](https://database.tytorobotics.com/) and public [test pages such as DYS/HQ 5045](https://database.tytorobotics.com/tests/6zd/dys-samguk-shu-hq-5045): motor/prop/ESC thrust, torque, voltage, current, RPM samples. Use for static and selected dynamic propulsion systems; login may be needed for some exports.
- The local Tyto 5-inch static scan `docs/data/tyto_5in_static_prop_packet.csv` has `4532` rows across `28` candidate 5-inch propellers, `89` public tests, and `1548` static samples. It supports the current `racingQuad` static coefficient scale (`0.960x` median test `T/omega^2` versus current `k`), but none of the parsed tests reaches the current `13.5 N` per-rotor max thrust (`12.55 N`, `0.929x`, is the highest parsed test).
- Tyto Windshaper/Flight Stand material documents prop tests up to about 17 m/s and CSV workflows; use as a lead for forward-flight prop data even when exact 5-inch files are not immediately public. The local article-level lead packet `docs/data/tyto_wind_tunnel_lead_packet.csv` records a 9-inch/9000-RPM trend where thrust retention at 17 m/s is `0.25x` while power retention is `0.81x`, i.e. `T/P` retention about `0.309x`.
- [IMAV 2021-21 Propulsive efficiency of small multirotor propellers in fast forward flight](https://www.imavs.org/papers/2021/21.pdf): RMIT wind-tunnel paper using 5-inch, 3-blade HQProp V1S propellers, T-Motor F80 2500kv, RCBenchmark 1580 thrust/torque logging, `10/15/20 m/s` wind speeds, `30..90 deg` flow angles, and `10000..~30000 rpm` ramps. The local source packet `docs/data/imav2021_forward_flow_packet.csv` records the matrix, equations, logged schema, and J overlap. The companion `docs/data/imav2021_figure_inventory_packet.csv` extracts `8` embedded images and lists `18` Figs. 5-8 panels for eta-vs-RPM digitization. It has no raw public CSV curves, so CT/CP fitting still needs author/thesis raw-data search.
- [Kolaei, Barcelos, and Bramesfeld 2018](https://doi.org/10.1155/2018/2560370): open inflow-angle rotor paper with 11x7 and 18x6.1 rotors, `CT`, `CP`, and roll-moment coefficient versus `mu` and inflow angle. The local packet `docs/data/kolaei2018_inflow_angle_rotor_packet.csv` records the same-code-definition `mu=V/(Omega*R)`, reported uncertainty, `mu<=0.30` range, current `racingQuad` overlap, and Figs. 9-11 digitization targets. Use as a transverse-flow/roll-moment shape source, not a direct 5-inch coefficient fit.
- NeuroBEM can provide in-flight residual aerodynamic effects at speed, but it is not an isolated propeller wind-tunnel curve.

Recommended extraction:

- Use `docs/data/uiuc_forward_flow_mu_guard_packet.csv` for the current pi-factor handoff: it stores `1696` rows across UIUC, current operating points, APC, and Mejzlik with both published `J` and code `mu`. It finds `racingQuad` 12.5 m/s at `J=0.453` / UIUC CT ratio `0.543` / code-to-UIUC CT `1.96x`, and current high-advance-loss start at `J=1.445`, `1.84x` the Mejzlik CT-zero boundary.
- Initial high-`J` axial measured pass is done in `docs/data/mejzlik_wind_tunnel_prop_packet.csv`; next priority is still direct 5-inch FPV wind-on-thrust-stand rows with wind speed, RPM, thrust, torque/current, and prop geometry.
- Treat `docs/data/tyto_5in_static_prop_packet.csv` as a static-scale/current/RPM/torque handoff only. It deliberately marks `static_only_no_airspeed_or_wind_speed = 1`, so it does not close the forward-flow rolloff gap.
- Treat `docs/data/tyto_wind_tunnel_lead_packet.csv` as a dynamic trend and digitization target only. It has article-level endpoint ratios and J/mu mappings, but no raw 5-inch thrust/RPM/current curve rows.
- Use `docs/data/imav2021_forward_flow_packet.csv` as the current best direct 5-inch forward-flow source packet. It records cropped high-quality J ranges `0.157..0.315`, `0.236..0.472`, and `0.315..0.630` at `10`, `15`, and `20 m/s`, plus current `racingQuad` hover/max overlap rows.
- Use `docs/data/imav2021_figure_inventory_packet.csv` for the PDF-image handoff: it maps Figs. 5-8 into `18` eta-vs-RPM digitization panels and line colors. Digitizing these panels can support efficiency trend checks, but do not fit CT/CP until raw RMIT thrust/torque logs or additional numeric coefficient curves are found.
- Use `docs/data/kolaei2018_inflow_angle_rotor_packet.csv` to audit `rotorAdvanceRatio` behavior without a `J/pi` mistake. It directly overlaps current `racingQuad` hover-RPM `mu` through about `20 m/s`, and its roll-moment coefficient rows are the best current lead for transverse-flow asymmetric rotor moments. Next useful step is digitizing Figs. 9-11 for CT/CP/CMx curves before changing constants.

### High-C FPV LiPo ESR

Affected fields and code paths:

- `batteryInternalResistanceOhms`
- `maxBatteryCurrentAmps`
- SOC/SOH resistance lookup
- cold/hot derating and thermal power limiting

Current symptom:

- The current `racingQuad` absolute resistance is aggressive but plausible for a fresh high-performance pack. `docs/data/fpv_lipo_esr_calibration_packet.csv` now gathers the existing absolute FPV charger-IR anchors, SOC/SOH projection rows, C-rate/temperature shape rows, and temperature guardrails into one handoff table. The remaining weak point is still direct FPV pouch-pack DCIR/loaded-step data across SOC and temperature.

Targeted data leads:

- [LiPo batteries dataset: capacity, EIS, and equivalent-circuit fits](https://pmc.ncbi.nlm.nih.gov/articles/PMC10518458/): real LiPo cells with EIS at SOC/SOH states. Good for SOC/SOH shape; not high-C FPV absolute sag.
- Existing Mendeley C-rate/temperature dataset and NASA/Figshare EIS packets: useful for shape and aging priors, not absolute FPV ESR.
- Oscar Liang and Jeffco RC LiPo field IR rows already in local docs remain the best hobby-scale absolute/cold-ratio anchors, but they are charger/field measurements.

Recommended extraction:

- Use `docs/data/fpv_lipo_esr_calibration_packet.csv` first when changing battery runtime parameters. It keeps absolute ESR anchored to FPV field measurements and keeps Mendeley/NASA/Figshare/C-rate rows as shape priors rather than absolute FPV pack resistance.
- Search specifically for 4S/6S 1300-1800 mAh pouch packs with DCIR or loaded-step data at multiple temperatures; this is still the missing gold source.

## P1 gaps

### Ground, Wall, Ceiling, And Partial-Ground Effects

Affected fields:

- `groundEffectHeightMeters`
- `groundEffectMaxThrustBoost`
- wall/ceiling obstruction and surface-force terms
- near-ground rotor-drag and mixer terms

Current symptom:

- The current model now has raw and fitted packets for ground/ceiling/wall rows, but sidewall total-thrust data are weak and current wall loss/force behavior still needs a runtime mapping decision: attraction/moment versus gameplay dirty-air thrust loss.

Targeted data leads:

- [Ground-Effect-Aware Modeling and Control for Multicopters](https://arxiv.org/html/2506.19424v1) and [ZJU-FAST-Lab Ground-effect-controller](https://github.com/ZJU-FAST-Lab/Ground-effect-controller): force-platform and real-flight ground-effect modeling, plus controller code.
- [Sanchez-Cuevas et al. 2017 ground-effect characterization](https://onlinelibrary.wiley.com/doi/10.1155/2017/1823056): experimental multirotor ground-effect and partial-ground data, useful for digitizing thrust multipliers versus distance.
- [Cai/Gunasekaran/Ol partial ground and partial ceiling paper](https://soar.wichita.edu/items/20c3f481-4556-41fa-8c79-a4b0b6d3c620) / DOI [10.2514/1.C036974](https://doi.org/10.2514/1.C036974): finite-size circular/annular plate experiments behind/ahead of a propeller. The local packet `docs/data/partial_surface_effect_lead_packet.csv` extracts abstract-level thresholds: `<0.5D` plate diameter is negligible, `1.0D` plate diameter is comparable to infinite plate, and curve fits are reported within `6%`; full curve digitization is still needed.
- [Conyers dissertation on ground/ceiling/wall effect](https://digitalcommons.du.edu/context/etd/article/2570/viewcontent/Conyers_denver_0061D_11857.pdf): force/moment/attraction evidence for small vehicles near surfaces.
- [JIRS 2024 ground/ceiling/wall effect evaluation](https://link.springer.com/article/10.1007/s10846-024-02155-7) and [supplement DOI](https://doi.org/10.5281/zenodo.11384638): raw supplementary CSV/MAT rows for 10/12/13-inch propeller ground, ceiling, and wall tests. The local packet `docs/data/surface_jirs2024_effect_packet.csv` parses `225` numeric measurements and `40` direct uncertainty summary rows, while `docs/data/surface_jirs2024_curve_fit_packet.csv` adds `196` fitted/bin/comparison rows for ground, ceiling, and wall distance scaling.
- [Flying in air ducts](https://www.nature.com/articles/s44182-025-00032-5): wall/ceiling/ground force interpretation for confined quadrotor flight.

Recommended extraction:

- ZJU equation-level rows are already in `docs/data/surface_nearfield_calibration_packet.csv` and mirrored as `surface_nearfield_*`: at `racingQuad h/R=1`, current ground multiplier is `1.144` versus ZJU `1.332`, and the current extra thrust is `0.433x` ZJU's extra. Keep these as formula-level anchors.
- `docs/data/partial_surface_effect_lead_packet.csv` now maps the partial-surface threshold to current presets: `racingQuad` should treat support patches below about `0.0635 m` diameter as negligible and around `0.127 m` as full-like for one rotor; one full Minecraft block is `7.87D`, so full blocks still behave as large/infinite surfaces. Runtime rotor-disk ground/ceiling sampling now converts supported sample weight into an equivalent circular patch diameter and applies the same `0.5D..1.0D` gate, so local edges, ledges, and holes attenuate near-field lift before raw curve digitization is available.
- `docs/data/surface_jirs2024_curve_fit_packet.csv` now provides fitted surface rows: at `h/R=1`, ground and ceiling fit to `1.0856x` and `1.0961x`; current `racingQuad` is `1.054x` and `1.016x` those fits. For walls, terraXcube absolute force has usable distance fit quality (`R2=0.923`), but the pooled fit is weak (`R2=0.225`) and current `racingQuad d/R=1` two-rotor wall force is `3.39x` the pooled fit.
- Keep wall attraction/moment calibration separate from vehicle-wide thrust loss. Next work is not raw data collection for this source; it is deciding whether the existing runtime wall force is a physical attraction/moment term, a dirty-air/gameplay term, or two capped terms.

### VRS And Propwash

Affected fields:

- `propwashStartDescentMetersPerSecond`
- `propwashFullDescentMetersPerSecond`
- `propwashMaxTorqueNewtonMeters`
- VRS mean thrust loss, buffet, lateral disturbance, and torque paths

Current symptom:

- Existing packets align current mean loss with a Cambridge-style one-third peak anchor, and the new Shetty/Selig digitization handoff splits mean CT loss, measured-envelope half-amplitude, `J`/`V/vi`, and timing proxies. The remaining gap is a statistical fit: the public data are still low-precision figure/envelope rows, not raw time histories or RMS spectra.

Targeted data leads:

- [Shetty/Selig small-prop VRS paper](https://m-selig.ae.illinois.edu/pubs/ShettySelig-2011-AIAA-2011-1254-LRN-VSR-Props.pdf): 26 small propellers, `J=-0.8..0`, thrust coefficients, and time-history discussion.
- [Shetty UIUC thesis landing page](https://www.ideals.illinois.edu/items/18490): open source traceability for the same work.
- [NASA Johnson VRS model](https://rotorcraft.arc.nasa.gov/Publications/files/Johnson_TP-2005-213477.pdf): normalized rotorcraft VRS regime model, useful for onset/shape, not FPV-specific torque.
- [2026 Physics of Fluids side-by-side propeller VRS paper](https://doi.org/10.1063/5.0311688): adjacent-propeller VRS interaction source with isolated `DR=1.1`, side-by-side `DR=0.9`, and Table II vortex radius/circulation rows for `0.1..1.0R` disk-tip gaps.

Recommended extraction:

- Use `docs/data/vrs_shetty_digitization_packet.csv` for the current handoff: it stores `13` Shetty/Selig Fig. 11-13 points, median/max measured-envelope half-amplitude `0.258/0.6625`, current `racingQuad` half-amplitude `0.421x` of the largest envelope, and `4.34..10.85 Hz` hover / `9.71..24.28 Hz` max-RPM timing proxies.
- Use `docs/data/vrs_johnson_regime_packet.csv` for broad NASA regime bounds: Johnson Table 4 gives zero-damping/stability-boundary points `0.45..1.50 vh`, VRS-increment joins `0.20..2.00 vh`, and forward cutoff `VxM=0.95 vh`. For `racingQuad`, `N..X` maps to `4.19..13.98 m/s`; current VRS intensity is `0` at `N`, peaks at `1.20 vi`, and is `0.475` at `X`.
- Use `docs/data/side_by_side_vrs_2026_packet.csv` for adjacent-rotor VRS coupling bounds: tight `0.1R` co-rotating vortex radius/circulation is `0.692x/0.656x` isolated, tight counter-rotating circulation is `1.246x` co-rotating, and current `racingQuad` adjacent disk-tip gap is `2.009R`, outside the paper's `0.1..1.0R` range.
- Next extraction: redigitize Shetty/Selig figures with calibrated image axes or locate raw time histories, then add RMS/PSD/frequency rows if visible.
- Keep propwash torque/noise as a separate tuning surface; do not derive it directly from mean thrust loss.

### Motor/ESC Response And Active Braking

Affected fields:

- `motorTimeConstantSeconds`
- `escOutputSlewRatePerSecond`
- `escOutputFallSlewRatePerSecond`
- `motorActiveBrakingStrength`
- ESC frame/telemetry timing

Current symptom:

- Existing blackbox RPM packets put spin-up in the right order but make active braking look stronger than observed public RPM traces. APdrone logs mix alignment delay with physical motor response.

Targeted data leads:

- Betaflight PR #12562 blackbox RPM logs already decoded locally: continue using these for RPM slew and telemetry semantics.
- [alspitz/esc_test](https://github.com/alspitz/esc_test): MIT-licensed RCBenchmark/AutoQuad logs for six ESC/motor bench tests using a DALPROP T7056 three-blade 7-inch prop at 16 V. The local packet `docs/data/esc_test_propbench_packet.csv` extracts static thrust/RPM/current bins and observed RPM slew; use it as adjacent open bench evidence, not direct 5-inch coefficients.
- [fpv-geek/prop-bench](https://github.com/fpv-geek/prop-bench): open FPV prop-bench software with load-cell thrust, Betaflight FC telemetry, automated max-thrust/acceleration tests, and CSV export. The local packet records its test timings and schema as a reproducible future data-collection lead.
- [VayuESC Studio](https://github.com/varun29ankuS/Vayu): AM32 ESC/thrust-test-bench software with auto-ramp, endurance, step-response, thermal, throttle-sweep, and hold-thrust protocols. The local packet records its default `20% -> 80%` step-response and `50 ms` response sampling as a schema lead; no public real bench-run CSVs were found.
- [Tyto Flight Stand software manual](https://www.tytorobotics.com/blogs/manuals-and-datasheets/flight-stand-software-user-manual): controlled thrust-stand workflows include CSV upload/flight replay and scripted dynamic tests; useful as a lead for step/chirp/slew bench protocols.
- [Mendeley 30-Inch Propellers Performance dataset](https://data.mendeley.com/datasets/69hhwc3fd3/1): open Tyto Flight Stand 50 hover workbooks with 100 Hz raw XLS files and AVG/STD/MIN/MAX summaries. The local packet `docs/data/mendeley_30in_prop_stand_packet.csv` is useful for static hover curve/noise/schema checks, but it has no forward-flow or motor-step dynamics.
- [WAVELab Pelican dataset](https://github.com/wavelab/pelican_dataset): 54 indoor system-ID flights with 100 Hz actual `Motors` and commanded `Motors_CMD`. The local packet `docs/data/wavelab_pelican_motor_response_packet.csv` extracts command-to-actual lag and response envelopes; use it as a clean in-flight prior, not FPV mechanical RPM.
- [Nano-Quadrotor System Identification Benchmark](https://github.com/idsia-robotics/nanodrone-sysid-benchmark): Crazyflie 2.1 Brushless, 100 Hz full-state CSVs, and four motor angular-velocity inputs in `rad/s`. The local packet `docs/data/nanodrone_sysid_packet.csv` validates the source `omega^2` thrust coefficient against body-z acceleration, but its torque rows are noisy and should be used only for motor-order/coordinate audits.
- Blackbird, AI-IO, NeuroBEM, WAVELab Pelican, and Nano-Quadrotor can provide in-flight actuator/state data but are not controlled FPV motor-step tests. Blackbird's official downloader exposes `blackbird_slash_rotor_rpm.csv` and `blackbird_slash_pwm.csv`, but direct example CSV fetches returned HTTP 502 on 2026-06-13, and the Academic Torrents mirror metadata does not include those CSV files. Treat it as a server-retry or raw-CSV-mirror lead until a chunk downloads cleanly.

Recommended extraction:

- Separate command transport delay, RPM telemetry delay, first-order motor lag, ESC slew limit, and active-braking fall response.
- Initial open bench pass is done in `docs/data/esc_test_propbench_packet.csv`; it has `402` rows, `67,972` RCBenchmark samples, `102` RPM-binned operating points, and observed 50 ms slew maxima of `253,619 rpm/s` positive / `272,067 rpm/s` negative. These are only `0.392x` current `racingQuad` spin-up proxy and `0.131x` current active-braking proxy. High-RPM torque rows are absent, and Vayu's README still marks torque measurement integration as future work, so this packet should not tune `yawTorquePerThrustMeter`.
- Initial Mendeley 30-inch Flight Stand pass is done in `docs/data/mendeley_30in_prop_stand_packet.csv`; it finds no-intercept hover thrust fits `6.69e-4..8.47e-4 N/(rad/s)^2` with R2 above `0.9995`, plus raw RPM/thrust CV medians `0.136%`/`0.821%`. Keep it as a static large-prop/noise/schema packet, not a dynamic motor-response packet.
- Initial WAVELab pass is done in `docs/data/wavelab_pelican_motor_response_packet.csv`; it finds lag P50/P90 `30/50 ms` and static-map tau P50 `0.320 s`, but the AscTec units and 5-sample smoothing mean it should not override direct FPV blackbox or bench-step evidence.
- Initial Nano-Quadrotor pass is done in `docs/data/nanodrone_sysid_packet.csv`; it finds `75,096` loaded samples and a thrust `Kt` fit of `3.716e-08 N/(rad/s)^2` (`0.999x` the source constant). Keep it as a motor-rad/s and `omega^2` semantics check, not as a 5-inch FPV coefficient source.
- Prefer controlled bench step/chirp data when found; use in-flight logs only as plausibility bounds. The next useful search target is direct 5-inch FPV PropBench/Tyto-style CSV with command timestamps, mechanical RPM, thrust, current, voltage, and torque at high RPM.

### Frame Flex, Vibration, And Prop Damage

Affected fields:

- arm-flex constants in `DronePhysics`
- gyro/accelerometer noise values
- blade-pass vibration/notch semantics
- rotor-damage vibration and imbalance paths

Current symptom:

- The new prop-damage packet gives useful amplitude ratios, but sample-rate aliases and sensor-placement effects make most sources unsuitable for direct notch-frequency fitting.

Targeted data leads:

- [UAV Realistic Fault Dataset](https://github.com/tiiuae/UAV-Realistic-Fault-Dataset): flight-phase-inclusive broken-prop missions.
- [DronePropB Mendeley dataset](https://data.mendeley.com/datasets/xkvfjmm8zg): ground vibration data across drones, fault types, severity, and speeds. The local packets `docs/data/dronepropb_sample_packet.csv` and `docs/data/dronepropb_stratified_vibration_packet.csv` record all `111` public `.mat` files, analyze `12` representative C3 samples, and expand to a `26`-file speed/channel/severity subset.
- [DJI Mini 2 multiaxial blade-fault vibration data](https://www.nature.com/articles/s41597-025-05692-4): raw multiaxial vibration benchmark for blade faults.
- [PADRE paper/repository lead](https://d-nb.info/1344245242/34): open UAV propeller-fault measurement repository.

Recommended extraction:

- Keep three layers: electronics noise, normal frame vibration, and fault/imbalance vibration.
- Initial DronePropB C3 and stratified passes are done in `docs/data/dronepropb_sample_packet.csv` and `docs/data/dronepropb_stratified_vibration_packet.csv`; selected same-speed/channel fault rows have median/P90/max external-accelerometer RMS ratios `1.184x/1.488x/2.246x`, while the earlier C3 pass already showed healthy `SP3` at `3.30x` healthy `SP2`. Next extraction should either mirror the full archive or fit only conditional damage curves keyed by speed, channel, and fault type.
- Store sample rate, Nyquist, blade count, inferred mechanical RPM, true BPF, and aliased BPF for every source.

## P2 gaps

### Rain, Wetness, And Icing

Affected fields:

- rotor wetness accumulation/decay
- wet-surface thrust loss
- immersion/water drag force
- future icing if added

Current symptom:

- Current Java full-wetness thrust loss is about 3%, close to ICAS heavy-rain CFD order of magnitude. The older wide CSV had a stale 5.5% value. The remaining missing data are wetness accumulation/decay and frozen-contamination behavior.

Targeted data leads:

- [ICAS 2020 quadrotor adverse-situation CFD](https://www.icas.org/icas_archive/ICAS2020/data/papers/ICAS2020_0482_paper.pdf): heavy-rain CT loss at LWC 19 g/m^3.
- [Experimental apparatus for icing tests of low-altitude hovering rotors](https://www.mdpi.com/2504-446X/6/3/68): CT/CQ degradation versus icing time and liquid-water content; use only for icing, not ordinary rain.

Recommended extraction:

- Initial icing extraction is now in `docs/data/icing_rotor_mdpi_packet.csv` from `docs/scripts/analyze_icing_rotor_mdpi_packet.py`. It mirrors 362 `icing_rotor_packet_*` rows into the global summary and keeps Table 1 LWC/MVD/lambda rows separate from Table 4 CT/CQ/C+Q/P+ time-rate rows.
- Keep ordinary wet/rain thrust loss separate from ice accretion. The MDPI icing packet projects CT losses of `9.13..23.96%` and required-power increases of `20.87..89.49%` over the published icing times, far above the current `3.00%` full-rain loss. Next model work should add a distinct frozen-contamination state with temperature, exposure time, shedding/de-icing, and droplet-size gates rather than tuning `precipitationWetness`.

### APdrone Exact Powertrain

Affected fields:

- `DroneConfig.apDrone()`
- YSIDO/Foxeer static thrust coefficient
- current, voltage, and torque/thrust scale

Current symptom:

- The exact YSIDO 2507 + Foxeer Donut 5145 curve is still missing. Current static thrust/RPM scale is supported by APdrone PDF visible rows, Foxeer Donut image data, Tyto alternatives, and MQTB current-shape rows, so this is no longer a blocking P0 gap.
- A broader Tyto 5-inch static packet now supports the generic FPV static coefficient scale: `docs/data/tyto_5in_static_prop_packet.csv` has median test `T/omega^2 = 1.3924e-6 N/(rad/s)^2`, `0.960x` current `racingQuad.k`. It also warns that the current `13.5 N` per-rotor max is above every parsed public Tyto 5-inch max point.

Recommended extraction:

- Continue searching for exact YSIDO 2507 bench tables, but use Tyto/MQTB/UIUC as the fitted fallback.
- If only images are available, digitize thrust, RPM, current, voltage, and prop/motor combination metadata, then tag the row as image-derived.

### Coaxial X8 Allocation

Affected fields:

- coaxial wake loss
- command-map allocation tables
- upper/lower rotor ordering and command limits

Current symptom:

- The New Dexterity packet supplies raw maps and a z/D=0.72 prior, but the left/right convention still needs mapping to project upper/lower rotor order.

Recommended extraction:

- Before tuning runtime, explicitly map benchmark channel convention to simulated upper/lower rotor ordering and verify command bounds.

## Immediate next scripts

Suggested next extraction scripts, in order:

1. `docs/scripts/analyze_neurobem_drag_residual_packet.py`
   - Done for the residual force/torque pass: it emits global, per-file, speed-bin, trajectory-family, and target-velocity rows from the NeuroBEM prediction archive.
   - Next use: feed `residual_torque_sample_p95_nm`, `torque_damping_like_*`, and `trajectory_family_summary` rows into the model audit before syncing any Java calibration constants.

2. `docs/scripts/analyze_lipo_eis_soc_soh_packet.py`
   - Parse LiPo EIS/ECM fitted values into SOC/SOH normalized resistance shapes.
   - Keep absolute ESR calibration separate.

3. `docs/scripts/analyze_ground_wall_digitization_packet.py`
   - Store digitized `h/R` and `d/R` rows from Sanchez-Cuevas, Conyers, ZJU, and air-duct sources.

4. `docs/scripts/analyze_vrs_digitization_packet.py`
   - Initial handoff done in `docs/data/vrs_shetty_digitization_packet.csv`; next replace low-precision figure rows with calibrated image digitization or raw time-history statistics if found.

5. `docs/scripts/analyze_fpv_dynamic_prop_and_esc_packet.py`
   - Combine Tyto dynamic/bench leads, Betaflight RPM logs, and Blackbird/AI-IO/WAVELab/Nano-Quadrotor actuator data into command-to-RPM/thrust response envelopes.

## Guidance For The Coding Agent

- Treat `docs/data/targeted_calibration_gap_leads.csv` as the work queue.
- Do not collapse all aerodynamic residuals into one drag coefficient. Split body drag, rotor drag, thrust rolloff, wake interference, and gust/dirty-air perturbations.
- Do not use the same dataset for incompatible roles. For example, DJI Mini 2 fault vibration is useful for damage signatures, but not direct 5-inch blade-pass notch placement.
- Prefer narrow packet CSVs with `row_type` prefixes and source URLs so rows can be mirrored into `docs/data/fpv_model_validation_summary.csv` later.
