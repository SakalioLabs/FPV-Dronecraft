# Fabric recovery milestone

Tag: `v0.1.1-playtest.11-fabric`. Minecraft 1.21.11, Java 21, Fabric Loader 0.19.3; the default source build now uses mod version `0.1.1-playtest.11`.

This milestone freezes the playable Fabric implementation before NeoForge becomes the next development target. It includes the agile flight preset and continuous ACRO rotation, imported drone/controller models, independent drone placement and explicit controller UUID binding, the LOS → FPV → drone third-person view cycle, remote piloting with a stationary physical player, separate remote chunk streaming, visible native pilot bodies, hidden foreground hands in drone views, pose/render optimizations, and the runtime frame/GPU/server/save monitor.

The source also includes the audio/core changes required by the tested artifact. These remain experimental acoustic behavior, rather than a claim that measured target-aircraft acoustic accuracy has been established. Uncommitted acoustic research in other checkouts is outside this milestone.

## Build

```powershell
./gradlew.bat --no-daemon :fabric-mod:build
```

The resulting production artifact is `fabric-mod/build/libs/fpv-dronecraft-fabric-0.1.1-playtest.11.jar`. Development/performance helpers are separate artifacts and must not be installed as production mods.

## Verified artifact

The delivered production JAR has SHA-256 `02e73a452ecac4415d95bb59ab037190350b8d270571fab9947bca1c85431d60`. Its verification completed on 2026-10-10:

- 1,392 JUnit tests: Fabric 631, flight core 491, acoustic core 270; no failures, errors, or skips.
- 16/16 required server GameTests and a client functional smoke.
- Two ordinary Sodium client runs using that exact production JAR: native placement/binding/input, three views, actual pilot-body rendering and foreground-hand suppression, save/reopen UUID persistence, and physical-controller remote streaming over 640 m followed by seven further chunk crossings.
- Real native mob damage, knockback, gravity, death/respawn, session/ticket cleanup, and runtime-monitor CPU/GPU/export checks.
- Independent delivery ZIP/file/hash validation, six installer behavior scenarios, and actual installation with old-version backup, dependency pins, unchanged settings/configuration, and unchanged saved-world file metadata.

Sodium was a separately verified third-party client dependency. Build outputs, user instances, test worlds, downloaded datasets and frozen local delivery ZIPs are not source files and are excluded from Git. The milestone is a source tag; it does not claim a new GitHub release asset was uploaded.

NeoForge migration should preserve these gameplay contracts, reuse the loader-independent flight/acoustic cores and supplied model assets, retain existing optional integrations, and verify client/server boundaries independently on NeoForge.
