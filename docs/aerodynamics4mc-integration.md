# Aerodynamics4MC Integration Notes

## Current Boundary

Aerodynamics4MC is useful to FPV Dronecraft as a world wind provider. Its public API exposes trusted gameplay wind samples with mean wind, bounded gusts, pressure anomaly/proxy, turbulence, shear, shelter, confidence, humidity, temperature, and ABL stability/mixing diagnostics. It does not replace FPV Dronecraft's multirotor model: rotor thrust, induced flow, vortex ring state, propwash, battery, motor dynamics, and airframe forces still live in `drone-sim-core`.

The first integration keeps that boundary explicit:

- `fabric-mod` samples Aerodynamics4MC only when the mod is present at runtime.
- The bridge uses `GameplayWindSample` with `GAMEPLAY_SERVER_ONLY`, so server physics ignores client-local L2 wind.
- Missing or incompatible Aerodynamics4MC falls back to FPV Dronecraft's existing Minecraft-weather wind.
- `/fpvdrone environment wind ...` remains the highest-priority override.
- The simulation core stays Minecraft-free and does not depend on Aerodynamics4MC classes.

## Implemented Path

`Aerodynamics4McWindBridge` reflects against `AeroMinecraftWindApi.sampleGameplay(ServerLevel, Vec3, SamplePolicy)`. It binds A4MC's explicit `gustVelocityVector()` when available and falls back to `effectiveVelocityVector() - meanVelocityVector()` for older bridge-compatible APIs. A trusted sample feeds `DroneEnvironment` as natural wind, including the current stage-one playable environment when Aerodynamics4MC is installed:

- effective wind becomes the base wind vector after confidence/freshness weighting against Minecraft weather or calm fallback,
- API turbulence becomes the natural turbulence floor,
- shear and shelter add bounded dirty-air turbulence,
- A4MC shear/updraft/shelter diagnostics add a bounded terrain-shear gust component to the effective air mass, making blackbox wind-shear acceleration respond to local voxel flow instead of only mean wind changes,
- gust vector, with gust-speed fallback for older bridges, adds a bounded unresolved turbulence boost plus a small coherent A4MC source-gust air-mass component, so effective-wind steps also excite Dryden-scale rotor response without impersonating terrain shear,
- API temperature and humidity are confidence/freshness weighted before replacing biome-derived ambient temperature or contributing to moist-air density relief,
- pressure anomaly is confidence/freshness weighted before adjusting air-density ratio and barometer pressure while staying bounded for L2 proxy values,
- source level, authority, and freshness age are preserved for L0/L1/L2 regression analysis, with stale source samples faded out of base wind, atmospheric scalar, local voxel, disk-gradient, and turbulence coupling,
- mean/effective/gust speed split is confidence/freshness weighted so blackbox analysis separates adopted A4MC gust forcing from stale or rejected source wind,
- ABL stability and mixing modulate the core Dryden turbulence intensity and correlation time: unstable mixed layers strengthen and quicken gust response, while stable layers damp and slow it.

When trusted Aerodynamics4MC samples are available, `fabric-mod` samples each rotor center plus weighted disk-edge points and passes both disk-mean wind and a bounded in-plane axial wind gradient into `drone-sim-core`. Missing or low-quality disk-edge samples remain in the disk quadrature as center-wind fallback, with confidence/freshness fading applied before the disk blend, making partial local voxel coverage conservative instead of amplifying one-sided samples into a full-disk gradient. The core keeps its existing global Dryden and dirty-air wind model, folds the quality-weighted A4MC gust vector into a small coherent source-gust air-mass vector, then applies the per-rotor wind delta to axial gust and lateral inflow calculations. When only the gust speed scalar is available, the core preserves the earlier bounded synthetic gust direction. A4MC shear/updraft diagnostics also drive a small correlated terrain-shear wind component in the core air-mass model, with A4MC shelter increasing the wake-like component only when mean wind is present, so cliffs, openings, and sheltered voxels can produce time-varying relative-air changes even when the mean source wind is steady. The disk gradient adds first-order asymmetric disk loading: small thrust loss, extra aerodynamic load/vibration, bounded local dynamic-stall onset, and flapping tilt toward the higher axial-flow side. This keeps Minecraft-specific wind sampling outside the physics core while allowing rotor-to-rotor and disk-scale flow differences near cliffs, openings, and sheltered blocks.

`DroneEnvironment` now carries wind-source telemetry through to the blackbox CSV and summary digest. The log records whether the frame used calm, Minecraft weather, an explicit environment override, or Aerodynamics4MC, plus source confidence, trusted state, source level, authority, freshness age, mean/effective/gust speed split, source gust vector, pressure anomaly, shear, shelter, updraft, local voxel-flow availability, source temperature, humidity, ABL stability/mixing, per-rotor disk-gradient magnitudes, the A4MC source-gust split including its world-vector components, and the A4MC terrain-shear gust split. The summary reports A4MC sample count, trusted/local-L2 counts, L0/L1/L2 counts, stale/freshness age, source mean/effective/gust peaks, peak confidence, pressure anomaly, shelter, source shear, updraft, ABL stability/mixing, disk-gradient magnitude, peak A4MC source gust, and peak A4MC terrain-shear gust, making later terrain-flow tuning auditable instead of relying on inferred wind state.

In the advanced environment path, local obstacle scans, drone wake, water, precipitation, ground effect, ceiling effect, and rotor disk obstruction remain active on top of the sampled wind. If Aerodynamics4MC reports trusted local L2 voxel flow, FPV Dronecraft treats A4MC shelter as having already explained part of the wall/tunnel wind shadow and attenuates only the duplicated local obstacle airflow plus rotor side-flow obstruction. The same trusted local shelter also reduces motor and ESC ventilation cooling by a bounded amount, so flying through sheltered voxel wakes can carry a small thermal penalty without replacing the existing electrical and propwash heat models. Rotor ground effect, ceiling effect, wet props, precipitation, and drone wake stay independent. In the stage-one playable path, the environment remains calm unless Aerodynamics4MC or an explicit `/fpvdrone environment` override provides wind or turbulence.

## Next Research Steps

- Calibrate the disk-gradient thrust-loss, load, vibration, and flapping coefficients against blackbox traces near block edges and tunnel mouths.
- Build blackbox regression traces that compare Aerodynamics4MC confidence, shelter, and shear against observed rotor axial gust response near block edges and tunnel mouths.
- Validate A4MC-driven gust response against the existing `wind_gust_calibration_packet` and `rotor_forward_flow_reference` data.
- Explore optional client-only visualization using A4MC local L2 while keeping server flight dynamics authoritative.
