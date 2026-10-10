# NeoForge version development

The inventory covers 16 Minecraft releases from 1.21.1 through 26.3. The recorded all-target matrix, 1,255 native unit tests, and World 05 results are historical `0.2.0-neoforge-dev.1` migration-baseline evidence; they do not establish full `dev.2` coverage. The `0.2.0-neoforge-dev.2` exact-256 validation passed packaging, 465 adapter unit tests and 28 native-world checks; default 21.1.257 packaging/client startup also passed. See `docs/neoforge-installation.md` for the exact report paths and run UUID. All profiles remain `adapting`: full native-world and third-party functional validation currently apply only to 1.21.1. The original matrix `build/reports/neoforge-matrix/20261010-103859-388` preserves its pre-fix 26.1 client failure; that target's build/client retry passed in `build/reports/neoforge-matrix/20261010-112848-528`. The inventory records the actual passing source for each stage.

In `dev.2`, the new `mc12101` change fixes the 1.21.1 terrain camera anchor. While FPV or third-person drone view is active, terrain-ring positioning follows the actual camera rather than the stationary local player, so the distant camera can keep terrain sections in its render area. It does not move the player body or change saved-world or chunk-generation rules. Inspection of all 16 patched target jars confirms that Minecraft 1.21.2–1.21.11 and 26.x already follow the camera for this path and do not need this hook; only the 1.21.1 profile registers it.

The extended native route still uses a paced drone pose fixture that waits for
native loading/entity ticking before crossing a chunk boundary. Its terrain
probes observe native compiled and visible sections, without adding draws or
moving the terrain grid themselves. This does not validate an unpaced physical
flight into chunks whose generation has fallen behind. That separate regression
should cover a transition into a native `HIDDEN` entity section: NeoForge's
tracking-end `EntityLeaveLevelEvent` can occur before `isRemoved()`, while
`DroneNetworking` currently treats the event as unloading and ends the lease.
Any eventual repair needs continuous client tracking and bounded loading ahead,
not just event suppression. This conditional server path was found during source
and bytecode inspection, not observed in the reported rendering failure.

The pure `drone-sim-core` and `computational-acoustics-core` modules remain Java 21. Minecraft 1.21.x adapters use Java 21. Minecraft 26.x adapters use Java 25. Mojang's [26.3 version metadata](https://launchermeta.mojang.com/v1/packages/702fe59163c6ee6578607daa85811d9bc9c7cc40/26.3.json) declares Java 25, and the [NeoForge 26.1 migration primer](https://docs.neoforged.net/primer/docs/26.1/) describes that toolchain transition.

Physical controller/gamepad input uses GLFW through Minecraft 26.2. Minecraft
26.3 moves the native input backend to SDL, so its adapter uses SDL input.
This backend mapping describes implementation and does not imply physical
hardware validation; hardware-controller testing remains outstanding.

## Build selection

The profile script is applied in `neoforge-mod/build.gradle` immediately after `plugins`, before any tasks or verification scripts read versions and build directories:

```groovy
apply from: rootProject.file("gradle/fpvdrone-version-profiles.gradle")
```

The selected `:neoforge-mod` is built with `-PtargetMinecraft=1.21.1`; omitting this property selects the inventory's `defaultTarget` (1.21.1). Its output directory is `build/targets/1.21.1/neoforge-mod`. Each target has a separate generated source tree, jar directory, and report directory. Never add sources from another checkout to these source sets.

The existing hardcoded `java.toolchain.languageVersion = ...` line in the mod build must use `fpvDroneTargetJava`. The pure-core Java 21 policy in the root build stays in place. Metadata must use exact Minecraft and NeoForge pins: `[${minecraft_version}]` and `[${neoforge_version}]`. Existing verification tasks must validate the same exact pins.

An explicit non-pinned `-PtargetNeoForge=<version>` selects a different NeoForge artifact within that row's `neoforgeFamily`. It cannot select another Minecraft family. A same-family override clears the pinned verification state and must be checked as its own combination; it does not expand jar compatibility metadata. Each build produces a jar for one exact Minecraft target, so use that target's jar with its matching Minecraft/NeoForge pair; there is no universal jar for all versions.

## Inventory contract

```json
{
  "schemaVersion": 1,
  "defaultTarget": "1.21.1",
  "targets": [
    {
      "minecraft": "1.21.1",
      "neoforge": "21.1.257",
      "java": 21,
      "adapterFamily": "mc12101",
      "sourceOverlays": ["mc12101"],
      "status": "adapting",
      "implemented": true,
      "identifierTransform": false
    }
  ]
}
```

`status` is `planned`, `adapting`, or `verified`. `implemented: true` permits compilation while a row is being adapted; it does not mean its tests pass. Planned or unimplemented rows fail during Gradle configuration. Add a row to the implemented set only when its source adapter exists. A verified row needs traceable evidence for that exact Minecraft, NeoForge, Java, and integration combination.

`sourceOverlays` supplies an ordered list of compatibility directory names; later directories win. The active stacks are:

| Minecraft | Java | `sourceOverlays` |
| --- | ---: | --- |
| 1.21.1 | 21 | `mc12101` |
| 1.21.2 | 21 | `mc1212` |
| 1.21.3 | 21 | `mc1212` |
| 1.21.4 | 21 | `mc1212`, `mc1214` |
| 1.21.5 | 21 | `mc1212`, `mc1214`, `mc1215` |
| 1.21.6 | 21 | `mc1212`, `mc1214`, `mc1215`, `mc1216` |
| 1.21.7–1.21.8 | 21 | `mc1212`, `mc1214`, `mc1215`, `mc1216`, `mc1217` |
| 1.21.9–1.21.10 | 21 | `mc1212`, `mc1214`, `mc1215`, `mc1216`, `mc1217`, `mc1219` |
| 1.21.11 | 21 | `mc12111` |
| 26.1, 26.1.1, 26.1.2 | 25 | `mc12111`, `mc261` |
| 26.2 | 25 | `mc12111`, `mc261`, `mc262` |
| 26.3 | 25 | `mc12111`, `mc263` |

These 16 stacks are enabled for ongoing adaptation. The listed compile, packaging, and runtime passes are `dev.1` baseline evidence. Runtime and client/server behavior for `dev.2` still require separate evidence. The base implementation is the current 1.21.1 source tree.

## Source boundaries

The published Fabric comparison baseline is `v0.1.1-playtest.11-fabric`
(`e278e7cfd13ea14ca7c165502f66cf7929bb6fe0`). The acoustic core and imported drone
and controller geometry retain that baseline. The flight core also retains the
existing NeoForge atmosphere, local-flow, target-RPM and non-finite-input
extensions, with their compatibility constructors and regression tests. These
intentional differences are recorded in the [historical migration
ledger](../neoforge-migration.md); loader migration keeps those integration APIs.

`generateEffectiveMinecraftSources` copies `src/main/java` and `src/main/resources`, then applies each `sourceOverlays` directory in order from `src/compat/<overlay>/java` and `resources`, with later layers winning by relative path. Overrides must mirror the base class or resource path. A sorted manifest records the winning source for every generated file. Source symlinks pointing outside `neoforge-mod` are rejected. Editing generated files has no effect on the next build.

Keep flight integration, acoustic solvers, immutable control frames, and integration policy independent of Minecraft APIs where practical. Stable textures, sounds, translations, and presets can be shared. Item-definition JSON, recipes, shaders, mixin descriptors, and access transformers need version-specific resource overlays whenever their formats or targets change.

The boundaries requiring actual adapters include:

| Area | Changes relevant to this mod |
| --- | --- |
| Entity persistence and registration | CompoundTag getters, ValueInput/ValueOutput, entity builders, components, item definitions |
| Server remote view | Ticket registries and loading/simulation APIs, ChunkMap mixin targets, ChunkPos representations, lifecycle events |
| Client remote view | ClientChunkCache.Storage fields and packet signatures, empty-section tracking, camera and level extraction |
| Rendering and GUI | EntityRenderState, SubmitNodeCollector, GUI input events, material pipelines, texture submission |
| Audio | SoundEngine and Library methods, device tracking, Channel mixins and accessors |
| Third-party integration | Actual available mod jars, public energy APIs, recipes, A4MC world adapters |

For 1.21.11 and 26.x, an explicit `identifierTransform: true` permits the narrowly scoped Java type rename `net.minecraft.resources.ResourceLocation` to `Identifier`. The transform preserves literals and comments. It does not rewrite mixin descriptor strings, NBT, render methods, tickets, or inputs. Those changes need actual overrides and successful compilation. No runtime reflection selects Minecraft API families.

Useful official API migration boundaries are [1.21.2 rendering](https://docs.neoforged.net/primer/docs/1.21.2/), [1.21.5 tickets](https://docs.neoforged.net/primer/docs/1.21.5/), [1.21.9 submission and input](https://docs.neoforged.net/primer/docs/1.21.9/), and [1.21.11 identifiers](https://docs.neoforged.net/primer/docs/1.21.11/). The [26.1 primer](https://docs.neoforged.net/primer/docs/26.1/) adds camera state and audio device changes. [26.2](https://docs.neoforged.net/primer/docs/26.2/) replaces direct MultiBufferSource rendering and reorganizes GUI/level extraction. [26.3](https://docs.neoforged.net/primer/docs/26.3/) moves input to SDL and rendering APIs to Renderpearl. These families need separate client adapters.

## Matrix verification

List the entire development inventory without starting Gradle:

```powershell
./tools/build-neoforge-matrix.ps1 -ListOnly -IncludePlanned
```

Build and run unit tests for the implemented 1.21.1 target. The `dev.1` final native rerun passed 1,255 tests (520 core, 270 acoustics, 465 NeoForge), recorded in `build/mc12101-final-native-06.log`; 18 GameTests passed. Native unit/GameTest fixtures currently exist for 1.21.1 only:

```powershell
./tools/build-neoforge-matrix.ps1 -MinecraftVersions 1.21.1 -Stages build,unit
```

Add the four existing dedicated-server flight self-tests:

```powershell
./tools/build-neoforge-matrix.ps1 -MinecraftVersions 1.21.1 -Stages build,unit,server
```

The startup/model smoke validator is separate from native-world validation. Its Gradle task has an external 4-minute timeout. All 16 pinned targets passed this smoke on `dev.1`; the 26.1 supplemental build/client evidence is in `build/reports/neoforge-matrix/20261010-112848-528`. Run it for 1.21.1 with:

```powershell
.\gradlew.bat --no-daemon -PtargetMinecraft=1.21.1 :neoforge-mod:runClientSmoke
```

The smoke does not test a world or flight behavior.

The actual native integrated-world validator is also 1.21.1-only:

```powershell
.\gradlew.bat --no-daemon --no-parallel --max-workers=1 -PtargetMinecraft=1.21.1 :neoforge-mod:runClientWorldValidation
```

It records native item placement, binding/ownership, four signed continuous turns
over 720 degrees, FPV/third-person/LOS body and camera behavior, remote chunk
streaming, damage and disconnect cleanup, and 11 verified screenshots. Its
Gradle task has a 14-minute external timeout; internally it allows 12 minutes
total, 4 minutes for world startup, and 60 seconds for each later stage. World
`dev.1` run 05 passed on `1.21.1` only: all 25 required observations, four signed turns
over 720 degrees, and 11 real PNG captures; report UUID is
`f7d2c17a-da69-4a12-8d9f-36b4a1ef0b5d` at
`build/targets/1.21.1/neoforge-mod/run-client-validation/fpvdrone-client-validation/report.json`.
It covers native placement, pilot body rendering/hidden hands, a paced 640 m
server-pose fixture using the actual lease, Husk damage/fall, and lease
expiry/reconnect/disconnect cleanup. The 640 m pose fixture is server-driven and
is not a complete pilot-stick flight route. Death, save/reopen, and
hardware-controller testing remain outstanding. A startup/model pass alone does
not satisfy this validator.
For the matrix runner's `client` stage, pass `-ClientVerificationTask :neoforge-mod:runClientWorldValidation` only for 1.21.1. A plain game launch or `runClientSmoke` cannot satisfy native-world validation. The runner marks client evidence unavailable as a failed requested stage when no validator is supplied.

The runner invokes Gradle sequentially, retains per-stage logs, and writes `build/reports/neoforge-matrix/<run>/results.json`. It records each stage as passed, failed, unavailable, skipped, or not run. Any requested unimplemented row or unsuccessful stage gives a nonzero exit code. Successful build/unit stages never change inventory status automatically. Run `-IncludePlanned` for the complete release gate; every planned row prevents the gate passing.

Integration evidence must name the actual Mekanism and Aerodynamics4MC jars used for the exact target. On a production 1.21.1 server with Mekanism `10.7.19.85` and A4MC `0.2.1`, all 13 functional integration checks passed. The validated report is `build/production-server-12101/integration-report-fb292e39.json` (run ID `fb292e39-1c0e-423a-8841-8b2e3d3b533d`); see the reproducible report-validation command in the installation guide. Evidence included actual FE transfer of 200, an empty battery remaining at 0 after NBT reload and tick, and a trusted native `GAMEPLAY_SERVER_ONLY` A4MC flow with freshness 0. This applies to the tested 1.21.1 jars only. The `dev.1` baseline has passing per-stage build/package/server/client evidence for all 16 targets: 15 complete rows and the 26.1 server pass are in `build/reports/neoforge-matrix/20261010-103859-388`, while 26.1 build/client passes are in `build/reports/neoforge-matrix/20261010-112848-528`. The original `103859-388` aggregate remains failed because it preserves the pre-fix 26.1 client failure. Refer to each target's latest `sourceMatrixReport`; capability adapters compiling without either third-party mod do not verify behavior on another target.
