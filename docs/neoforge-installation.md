# NeoForge Installation and Verification

## Current status

`dev.3` adds placed bare frames, a native 13-slot assembly menu and split-part
models. Minecraft 1.21.1 / NeoForge 21.1.256 passed 469 adapter unit tests,
26 GameTests, packaging and 35 native-world observations with 17 real PNGs.
The final world run is `ecca876e-33ca-4106-8a3e-c080bfebe2fe`, with Mekanism
`10.7.19.85` and Aerodynamics4MC `0.2.1` installed. Its evidence is frozen in
`../MCFPV/deliverables/drone-assembly-runtime-neoforge-21.1.256-20261011/evidence`;
the source validation log is `build/assembly-256-native-final-03.log`.
GameTests cover native menu transfer, ownership and flight locks, every missing
component, typed ItemStack save/reload, ordinary LiPo and FE battery state, and
ordinary damage destruction drops. Native-world observations additionally
exercise the actual empty-hand interaction, registered screen and client/server
inventory click packets, followed by production arming and flight controls.

All 16 pinned `dev.3` targets passed build/package and native title/controller
startup smoke across `build/reports/neoforge-matrix/20261011-035035-251`,
`20261011-040319-465` and `20261011-041610-192`. The first aggregate remains
failed to preserve missing-import and 26.x message-API errors; the second
remains failed to preserve the 1.21.7 dependency-download TLS interruption.
The third is the successful 1.21.7 client-only retry. The assembly bundle's
`multiversion-manifest.json` records the passing source for each stage and the
SHA-256 of all 16 jars. Later fixes only touched excluded newer-version
overlays, so the earlier passing targets retain their effective source.
Startup smoke does not exercise assembly menus or flight in those other worlds.

The current NeoForge development build is `0.2.0-neoforge-dev.3`. The recorded
sixteen-target matrix, 1,255 native unit tests, and World 05 validation below
were completed on `0.2.0-neoforge-dev.1` and remain migration-baseline evidence;
they do not establish current-build coverage. The `dev.2` Minecraft 1.21.1 /
NeoForge 21.1.256 deployment passed packaging, 465 adapter unit tests and 28
native-world checks with Mekanism and A4MC installed, using view/simulation
distance 12. Native rendering observations found 41 nonempty visible sections
near the FPV camera 637 meters from the pilot, 43 in third-person 675 meters
away, and terrain around the body again after LOS switching. Run
`f1d140f6-48a4-4470-aeba-9aeeb8907901` is recorded in
`build/remote-terrain-256-validation.log` and
`../MCFPV/deliverables/remote-terrain-fix-neoforge-21.1.256-20261011/evidence/native-world-report.json`.
Default 21.1.257 packaging and client startup also passed in
`build/remote-terrain-257-default-validation.log`. The 1.21.2 overlay also passed
packaging without the 1.21.1-only hook in
`build/remote-terrain-12102-overlay-validation.log`. Its new 1.21.1 change addresses
the terrain camera anchor: when FPV or third-person drone view is active,
terrain-ring positioning follows the actual camera instead of the stationary
local player. It does not move the player body or change saved-world or chunk
generation rules. Sixteen Minecraft profiles from 1.21.1 through 26.3 have
source overlays enabled and all remain under adaptation. Historical `dev.1`
evidence has passing build/package, four-mode dedicated-server, and client
startup/model stages. Fifteen complete records are
in `build/reports/neoforge-matrix/20261010-103859-388`; the 26.1 server pass is
there as well, while its supplemental build/client pass is in
`build/reports/neoforge-matrix/20261010-112848-528`. The original `103859-388`
aggregate remains failed because it preserves the pre-fix 26.1 client failure.
These checks do not establish native-world behavior or complete support. Consult
[`gradle/minecraft-targets.json`](../gradle/minecraft-targets.json) and the
[multi-version development notes](development/neoforge-multiversion.md) for
pins and source-overlay stacks.

The published Git tag [`v0.1.1-playtest.11-fabric`](https://github.com/SakalioLabs/FPV-Dronecraft/tree/v0.1.1-playtest.11-fabric) points to the Fabric build. It is separate from this NeoForge branch and is not a NeoForge installer.

## Build and install

Install Java 21, Minecraft `1.21.1`, and NeoForge `21.1.257`. From the repository
root, build the default profile:

```powershell
.\gradlew.bat --no-daemon -PtargetMinecraft=1.21.1 :neoforge-mod:verifyNeoForgePackaging
```

The production jar is
`build/targets/1.21.1/neoforge-mod/libs/fpv-dronecraft-neoforge-mc1.21.1-0.2.0-neoforge-dev.3.jar`.
Install that target-specific jar in the `mods` folder of the client and server.
Each Minecraft target has its own jar; do not reuse one target's jar with a
different Minecraft version. Fabric Loader and Fabric API are not required.
Use the matching NeoForge loader for the selected Minecraft profile. A
non-pinned `-PtargetNeoForge=<version>` override within the target's NeoForge
family clears the pinned verification state and must be checked independently.

The production jar JarJar-bundles three libraries: `drone-sim-core`,
`computational-acoustics-core`, and MixinExtras NeoForge `0.5.5`. MixinExtras is
bundled because older NeoForge releases include a 0.4.x version that does not
provide the expression hooks used by the mod; players do not install it as a
separate mod.

On Windows, Gradle discovers the installed Java toolchain. If it cannot locate
it, set `org.gradle.java.installations.paths` in `gradle.properties` to the
installed JDK directory or directories, for example:

```properties
org.gradle.java.installations.paths=C:/Program Files/Eclipse Adoptium/jdk-21.0.8.9-hotspot,C:/Program Files/Eclipse Adoptium/jdk-25.0.1.8-hotspot
```

Use only the path or paths that exist on the build machine. Java 21 is used for
1.21.x; Java 25 is used for 26.x.

## Verification evidence

Evidence is target- and stage-specific:

| Stage | Current evidence |
| --- | --- |
| `dev.3` 1.21.1 / NeoForge 21.1.256 | 469 adapter unit tests, 26 GameTests, packaging and 35 native-world checks passed; 4 signed continuous turns and 17 PNG captures. Final world UUID: `ecca876e-33ca-4106-8a3e-c080bfebe2fe`. Frozen evidence is in the assembly runtime bundle above. |
| `dev.3` all 16 pinned targets | Build/package and native title/controller startup smoke passed across `20261011-035035-251`, code-fix retry `20261011-040319-465` and 1.21.7 client retry `20261011-041610-192`. First and second raw aggregates remain failed; the frozen multiversion manifest maps every passing stage to its actual report. |
| Compile/package, dedicated-server flight, and client smoke | Historical `dev.1` evidence passed for all 16 pinned targets. Fifteen complete records are in `build/reports/neoforge-matrix/20261010-103859-388`; 26.1 server passed there and its supplemental build/client pass is in `build/reports/neoforge-matrix/20261010-112848-528`. The original `103859-388` aggregate remains failed because it preserves the pre-fix 26.1 client failure. |
| Native unit tests | Historical `dev.1` final `1.21.1` rerun passed 1,255 (520 core, 270 acoustics, 465 NeoForge), recorded in `build/mc12101-final-native-06.log` |
| NeoForge GameTests | Historical `dev.1` evidence: 18 passed on `1.21.1` |
| Dedicated-server flight self-tests | Historical `dev.1` evidence: four modes passed for all 16 pinned targets; see each target’s recorded `sourceMatrixReport` in the inventory. |
| Client startup/model smoke | Historical `dev.1` evidence passed for all 16 pinned targets; 26.1 uses the supplemental report above. This does not load a validation world. |
| Native integrated-world validation | Historical `dev.1` World 05 passed on `1.21.1` only: 25 required observations, 4 signed continuous turns over 720°, and 11 real PNG captures; death, save/reopen, and hardware tests remain outstanding |
| Mekanism + A4MC integration | Historical `dev.1` evidence: all 13 functional checks passed on a production `1.21.1` server with Mekanism `10.7.19.85` and A4MC `0.2.1` installed |
| All-target native build/package/server/client matrix | Historical `dev.1` evidence: all 16 targets have passing per-stage evidence across `103859-388` and supplemental `112848-528`; the original `103859-388` aggregate remains failed because it preserves the pre-fix 26.1 client failure. |

The earlier partial run `build/reports/neoforge-matrix/20261010-095244-936` is
historical diagnostic-fixture/probe evidence and is superseded by the passing
per-target records above. Keep the original `103859-388` aggregate marked failed
even though supplemental 26.1 build/client stages passed.

These `dev.1` results are migration-baseline evidence. Passing compile/package,
unit, server-flight, startup, or third-party integration checks does not
establish native-world behavior or complete support.

## Gameplay and optional integrations

For ordinary play, get a drone item and controller from the FPV Dronecraft
creative tab, then place the drone item to spawn an unbound drone. Hold the
controller and Shift-right-click the drone to bind it; ownership is checked.
Lower throttle and center roll, pitch, and yaw before pressing `R` to arm. Press
`B` to cycle line-of-sight, FPV, and third-person views. `/fpvdrone spawn` is an
optional debug shortcut; normal play does not require it. The player stays
stationary and vulnerable while piloting. ACRO attitude control uses continuous
quaternion orientation. The controller and drone have dedicated assets, and
performance telemetry reports frame, GPU, and save timing.

Mekanism integration is optional and exposes public Forge Energy support, four
tiers, quantum supply, and conditioned recipes. A production server with
Mekanism `10.7.19.85` and A4MC `0.2.1` installed passed all 13 historical `dev.1` functional
integration checks for Minecraft `1.21.1`. The report observed real FE transfer
of 200, an empty battery remaining at 0 after NBT reload and tick, and a trusted
native `GAMEPLAY_SERVER_ONLY` A4MC flow with freshness age 0. These integration
results apply to the tested 1.21.1 jars and do not establish native-world
validation or support for other targets.

## Component assembly

Place `carbon_frame` or `drone_airframe` on a supported surface. The frame belongs
to its placer and starts with 13 empty slots. Empty-hand right-click opens the
native assembly container: four `brushless_motor`, four `propeller`, and one
each of `esc`, `flight_controller`, `receiver`, `lipo_battery` and `fpv_camera`.
Native pickup, drag and Shift-click transfer actual inventory items, with one
item per typed slot. Only the owner can edit a landed, disarmed aircraft within
8 meters. A missing component blocks both normal and diagnostic flight input
at the server. The model follows the synchronized installed-part mask.

Existing tiered power/radio modules also fit their corresponding slots. FE
charge stays with the physical battery when removed; ordinary LiPo stacks
also carry depletion, cycle count and battery temperature/transient state. Reinstalling it, changing
the receiver or saving/reloading does not refill it. Existing complete-drone
items, debug spawns and worlds without assembly data migrate to a complete
assembly. New partial assemblies persist their exact ItemStacks and components.
The existing complete-drone recipes are preserved; new survival component
recipes and ingredient balance will be designed separately.

## Development checks

```powershell
.\gradlew.bat --no-daemon -PtargetMinecraft=1.21.1 :neoforge-mod:build :neoforge-mod:verifyNeoForgePackaging
.\gradlew.bat --no-daemon -PtargetMinecraft=1.21.1 :neoforge-mod:runGameTestServer
```

Native unit tests and GameTests currently run only for `1.21.1`. Four dedicated-
server self-tests are configured per active profile: simulation angle, playable
angle, playable horizon, and playable ACRO. Accept the Mojang EULA only after
reading it, by placing `eula=true` in
`build/targets/<Minecraft version>/neoforge-mod/run/eula.txt` for the profile
being tested. CI uploads artifacts and logs under target-specific names.

Client validators are distinct. `:neoforge-mod:runClientSmoke` checks title
screen readiness, controller model baking, and at least 20 stable ticks. The
`dev.1` smoke passed for all 16 pinned targets; the 26.1 supplemental pass is in
`build/reports/neoforge-matrix/20261010-112848-528`. The smoke does not load a
validation world. The Gradle task has an external 4-minute timeout. The
`1.21.1` native-world validator is:

```powershell
.\gradlew.bat --no-daemon --no-parallel --max-workers=1 -PtargetMinecraft=1.21.1 :neoforge-mod:runClientWorldValidation
```

It exercises native placement/binding, four signed turns of at least 720°,
camera/body views, native frame/component assembly, missing-part arming rejection,
chunk streaming, pilot damage, cleanup, and 17 verified
screenshots. Its Gradle task has a 14-minute external timeout; internally it
allows 12 minutes total, 4 minutes for world startup, and 60 seconds for each
later stage. Historical `dev.1` World run 05 passed on `1.21.1` only: all 25
required observations,
four signed continuous turns over 720°, and 11 real PNG captures; report UUID is
`f7d2c17a-da69-4a12-8d9f-36b4a1ef0b5d` at
`../MCFPV/deliverables/neoforge-multiversion-20261010/evidence/1.21.1/native-world-report.json`.
It covers native placement, pilot-body rendering and hidden hands, a paced
640 m server-pose fixture through the actual lease, Husk damage/fall, and lease
expiry/reconnect/disconnect cleanup. The 640 m fixture is server-driven, not a
complete pilot-stick flight route. Death, save/reopen, and hardware-controller
tests remain outstanding.

For the isolated third-party integration report, validate the completed JSON
report against its own recorded run ID:

The production-server report passed validation with:

```powershell
.\tools\verify-neoforge-integration-report.ps1 -ReportPath "build\production-server-12101\integration-report-fb292e39.json" -ExpectedRunId "fb292e39-1c0e-423a-8841-8b2e3d3b533d"
```

This report is complete for Minecraft `1.21.1` and all 13 required
Mekanism/A4MC gates passed. The validator checks the saved report and matching
run ID; it does not launch or repeat the isolated integration test.

The original all-target matrix `build/reports/neoforge-matrix/20261010-103859-388`
remains failed because it preserves the pre-fix 26.1 client failure. Historical
`dev.1` evidence has passing per-stage build/package/server/client results for
all 16 targets: 15
complete rows are in `103859-388`, 26.1 server is also there, and its
supplemental build/client pass is in
`build/reports/neoforge-matrix/20261010-112848-528`. Refer to the target
inventory for each stage's latest `sourceMatrixReport`. Compile/package,
server-flight, and startup checks do not establish native-world behavior or
complete support.
