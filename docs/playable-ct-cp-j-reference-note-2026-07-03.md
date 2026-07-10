# Playable Reference: DA4002 Axial Surface v1

This note is a bounded data reference, not permission to copy `sim/lab` forces into `playable/dev`.

## Usable Reference

- Source only from `UiucDa4002AxialSurfaceV1`: UIUC DA4002 5x3.75 and 9x6.75, two blades, axial non-reversing flow.
- Compute `J = Vaxial / (n D)`, then use the bounded piecewise-linear `CT(J, RPM)` and `CP(J, RPM)` lookup. Outside the measured support, return unavailable; do not extrapolate or edge-clamp.
- Convert coefficients with `T = CT rho n^2 D^4`, `P = CP rho n^3 D^5`, and `Q = P / omega`.
- For low-cost feel work, the positive-thrust rows may supply normalized axial rolloff such as `CT(J, RPM) / CT(0, RPM)` and the matching CP/torque trend. The approach toward zero thrust is a valid qualitative fade direction; stop before the measured negative-CT tail.

| Propeller | Static RPM | Forward nominal RPM | Maximum J by track |
| --- | --- | --- | --- |
| DA4002 5x3.75 | 1410-7440 | 4000 / 5000 / 6000 | 0.857870 / 0.851340 / 0.895451 |
| DA4002 9x6.75 | 1546.667-5943.333 | 2000 / 3000 / 4000 / 5000 | 0.894262 / 0.887498 / 0.865364 / 0.914534 |

Between nominal RPM tracks, valid J ends at the smaller adjacent-track limit. `J=0` uses the separate static RPM envelope.

Run `./gradlew :drone-sim-core:uiucDa4002AxialSurfaceV1` to generate the 12 deterministic J slices and checksums. The frozen curve-bundle SHA-256 is `49f20e2f7ea42771ce07bc2b4b1f371b54e6966616921da09c8bbf82612043cf`.

## Not Usable

- Do not use the negative-thrust tail as gameplay thrust, windmilling, braking, or regenerative behavior.
- Do not infer oblique-flow side force, reverse-flow response, arbitrary blade counts, or another propeller geometry from these curves.
- Do not turn M8 runtime residuals into correction multipliers; their sign and scale vary across J and between the two propellers.
- Do not make Minecraft runtime depend on OpenFOAM or other offline CFD tooling.

The converged status is `DA4002 axial reference v1`: callable and reproducible in `sim/lab`, reference-only for `playable/dev`, and not wired into `DronePhysics`.
