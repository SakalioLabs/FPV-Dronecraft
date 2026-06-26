# Aerodynamics4MC Integration Notes

## Current Boundary

Aerodynamics4MC is useful to FPV Dronecraft as a world wind provider. Its public API exposes trusted gameplay wind samples with mean wind, bounded gusts, turbulence, shear, shelter, confidence, humidity, temperature, and ABL stability/mixing diagnostics. It does not replace FPV Dronecraft's multirotor model: rotor thrust, induced flow, vortex ring state, propwash, battery, motor dynamics, and airframe forces still live in `drone-sim-core`.

The first integration keeps that boundary explicit:

- `fabric-mod` samples Aerodynamics4MC only when the mod is present at runtime.
- The bridge uses `GameplayWindSample` with `GAMEPLAY_SERVER_ONLY`, so server physics ignores client-local L2 wind.
- Missing or incompatible Aerodynamics4MC falls back to FPV Dronecraft's existing Minecraft-weather wind.
- `/fpvdrone environment wind ...` remains the highest-priority override.
- The simulation core stays Minecraft-free and does not depend on Aerodynamics4MC classes.

## Implemented Path

`Aerodynamics4McWindBridge` reflects against `AeroMinecraftWindApi.sampleGameplay(ServerLevel, Vec3, SamplePolicy)`. A trusted sample feeds `DroneEnvironment` as natural wind, including the current stage-one playable environment when Aerodynamics4MC is installed:

- effective wind becomes the base wind vector,
- API turbulence becomes the natural turbulence floor,
- shear and shelter add bounded dirty-air turbulence,
- API temperature can replace the biome-derived ambient temperature.
- ABL stability and mixing modulate the core Dryden turbulence intensity: unstable mixed layers strengthen large-scale gust response, while stable layers damp it.

When trusted Aerodynamics4MC samples are available, `fabric-mod` samples each rotor center plus weighted disk-edge points and passes both disk-mean wind and a bounded in-plane axial wind gradient into `drone-sim-core`. The core keeps its existing global Dryden and dirty-air wind model, then applies the per-rotor wind delta to axial gust and lateral inflow calculations. The disk gradient adds first-order asymmetric disk loading: small thrust loss, extra aerodynamic load/vibration, and flapping tilt toward the higher axial-flow side. This keeps Minecraft-specific wind sampling outside the physics core while allowing rotor-to-rotor and disk-scale flow differences near cliffs, openings, and sheltered blocks.

`DroneEnvironment` now carries wind-source telemetry through to the blackbox CSV and summary digest. The log records whether the frame used calm, Minecraft weather, an explicit environment override, or Aerodynamics4MC, plus source confidence, trusted state, shear, shelter, updraft, local voxel-flow availability, source temperature, humidity, ABL stability/mixing, and per-rotor disk-gradient magnitudes. The summary reports A4MC sample count, trusted/local-L2 counts, peak confidence, shelter, source shear, updraft, ABL stability/mixing, and disk-gradient magnitude, making later terrain-flow tuning auditable instead of relying on inferred wind state.

In the advanced environment path, local obstacle scans, drone wake, water, precipitation, ground effect, ceiling effect, and rotor disk obstruction remain active on top of the sampled wind. If Aerodynamics4MC reports trusted local L2 voxel flow, FPV Dronecraft treats A4MC shelter as having already explained part of the wall/tunnel wind shadow and attenuates only the duplicated local obstacle airflow plus rotor side-flow obstruction. Rotor ground effect, ceiling effect, wet props, precipitation, and drone wake stay independent. In the stage-one playable path, the environment remains calm unless Aerodynamics4MC or an explicit `/fpvdrone environment` override provides wind or turbulence.

## Next Research Steps

- Calibrate the disk-gradient thrust-loss, load, vibration, and flapping coefficients against blackbox traces near block edges and tunnel mouths.
- Build blackbox regression traces that compare Aerodynamics4MC confidence, shelter, and shear against observed rotor axial gust response near block edges and tunnel mouths.
- Validate A4MC-driven gust response against the existing `wind_gust_calibration_packet` and `rotor_forward_flow_reference` data.
- Explore optional client-only visualization using A4MC local L2 while keeping server flight dynamics authoritative.
