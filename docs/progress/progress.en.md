# FPV Dronecraft Progress Archive (English Summary)

This page is a curated English summary of the progress stream that previously lived in the root README. The full original Chinese archive is preserved in [progress.zh-CN.md](progress.zh-CN.md).

## 2026-06-23: v0.1.0 Release and Documentation Cleanup

- Tagged `v0.1.0` and published a GitHub Release.
- Release assets include `fpv-dronecraft-fabric-0.1.0.jar` and the sources jar.
- Reworked the root README from a progress log into a project landing page with a logo, language navigation, install steps, quick start notes, and documentation links.

## 2026-06-23: Yaw Heading Fold Fix

- Fixed the left/right yaw oscillation caused by converting attitude quaternions through folded Euler Y angles after +/-90 degrees.
- Added a heading extractor based on the attitude quaternion's projected body-forward vector.
- Routed playable entity yaw, simulation telemetry yaw, and adapter resolved-state yaw through the new heading projection.
- Added regression tests covering headings past 90 degrees and ACRO yaw past 120 degrees.

## 2026-06-22: V1 Unified Flight-Model Gates

- Added tick-by-tick playable route equivalence between `DirectRouteHarness` and `LegacyPlayableFlightModelAdapter -> FlightModelRouter -> step`.
- Added tick-by-tick simulation route equivalence between direct `DronePhysics` and `SimulationFlightModelAdapter -> FlightModelRouter -> step`.
- Added real `DroneEntity` GameTest coverage for initialization, continuous ticks, reset, collision-free movement, resolved-state writeback, model IDs/capabilities, and finite-state checks.
- Adopted runtime switch policy option 1: each existing `DroneEntity` fixes and persists its own `flight_model_id`; global debug model changes affect only newly spawned entities.
- Added a GitHub Actions CI matrix covering core/fabric tests, full build, golden/route-equivalence tests, dependency boundary tests, serialization round trips, GameTest integration, and server self-tests.

## 2026-06-22: Real Controller Input Path

- Fixed the mismatch where the calibration screen and runtime flight path could select different GLFW joystick devices.
- Added GUID/name-based controller device resolution; GLFW IDs are session-local indices only.
- Added visible controller enable-state UX to the calibration/settings screen.
- Added controller diagnostics for raw axes, mapping, calibration, shaping, smoothing, input-source arbitration, arm-block reasons, payloads, and server-received input.
- Added fake-provider tests for multi-device selection, reconnect, throttle inversion, low-throttle arming, high-throttle rejection, button-edge behavior, GAMEPAD input arbitration, and keyboard-only regression.

## 2026-06-21: ACRO Feel and 3D Attitude Convergence

- Iteratively addressed sustained side-flight after full rolls, high-speed diagonal flight that felt like planar sliding, and pitch/roll/yaw behavior that did not feel like one coherent 3D rigid body.
- Borrowed the core attitude idea from `do-a-barrel-roll`: rotate around the current body frame rather than treating pitch/yaw/roll as flat screen-space sliders.
- Added regression coverage around sidewash, crossflow, dynamic inflow, weathercock yaw, rotor flapping, advance-ratio thrust loss, turn load, and gyro/load behavior.
- Preserved ACRO semantics: no automatic self-leveling, no stealing active yaw, and no blunt global drag increase.

## Research Data and Next Directions

- `docs/fpv-sim-model-validation.md` tracks RotorPy, UIUC/RMIT/IMAV, RATM, Blackbird, APdrone, ZJU ground effect, NASA, battery, ESC, and other reference sources.
- V2 remains for numeric model convergence, canonical state/config ownership, propulsion/motor interface unification, environment/collision input unification, and more real hardware feel validation.
