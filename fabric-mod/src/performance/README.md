This diagnostic runs the normal Minecraft client loop in its own build directory.
It does not activate a ClientGameTest entrypoint, test mode, or regulated test
ticks. The client-gametest API module remains present within bundled Fabric API.
It does not change vanilla save IO or enter the production JAR.
Add `apply from: "performance.gradle"` to fabric-mod/build.gradle
to register the separate source set and Loom run.

Run `:fabric-mod:runPerformanceClient`. The default run hides only its own GLFW
window, creates a unique fixed-seed flat world under
`build/performance/normal-client/run/saves`, warms up for 10 seconds, measures
8 seconds before and after an ordinary autosave-equivalent call, performs a
vanilla flush save, closes/reopens the world, and checks a block and the drone's
UUID/owner/model/mass/name. It exits normally and writes
`build/performance/normal-client/report.json`. Worlds are retained for inspection;
the harness does not delete or touch user worlds.

Optional properties: `-PperformanceHidden=false`, `-PperformanceJfr=true`,
`-PperformancePhaseSeconds=8`, `-PperformanceWarmupSeconds=10`, and
`-PperformanceDirtyRadius=2` (clamped 0–4 chunks).

The report contains normal-loop frame intervals, frame/render/client/server tick
durations, drone client/server tick intervals, bounded client-to-server task
queue probes, save spans with flags/thread, and separate paused ticks. This
synthetic hidden scene does not establish display FPS or certify a 115 FPS
target. Its framerate option of 260 is vanilla's unlimited maximum setting,
not a 260 Hz cap. Rendering CPU submission does not measure GPU completion. Queue probes
measure server scheduling, not physical controller or network packet latency.
Opening, intentional teardown, explicit flushing, and reopening are separate
phases. The scene uses one grounded unarmed drone; existing large worlds and
active flight need independent profiling if this smoke run is clean.

An optional `-Dfpvdrone.performance.renderStress=true` JVM property selects a
fixed-seed normal-terrain fixture instead. It defaults to render distance 32,
simulation distance 12, a 3440 by 1351 window and two 20-second measured phases.
The helper queues the normal V/B/M key mappings to check virtual control, FPV,
HORIZON/ACRO/ANGLE, the drone's third-person view and camera return. During each measured
phase a server-thread pose driver repeats the same 96-meter-radius circuit,
commanding 64 meters per simulation second. It returns the drone and player
to the fixture before ordinary vanilla saves. No user worlds are used.

Optional JVM properties `fpvdrone.performance.renderDistance`,
`fpvdrone.performance.simulationDistance`, `fpvdrone.performance.width` and
`fpvdrone.performance.height` override these diagnostic settings. The launcher
must pass JVM properties; these are not additional Gradle project properties.
The report records actual route server ticks and commanded distance, camera
checks and terrain render completion. A slow server can advance fewer ticks
within a wall-clock phase, so comparing FPS alone does not establish equivalent
work. Both phases can contain generation and cache effects; they are not
guaranteed cold/warm phases. This scripted, hidden fixture tests rendering and
camera compatibility, not physical flight or performance in the user's world.
The normal save probe's defaults and complete flush/reopen checks stay in place.

An optional `-Dfpvdrone.performance.threeViewSmoke=true` JVM property exercises
the production B key cycle `LINE_OF_SIGHT -> FPV -> THIRD_PERSON -> LINE_OF_SIGHT`
on the ordinary client loop, including the flat save probe with renderStress off.
The distant observer makes following the drone distinguishable from following
the player. It verifies the shared server camera target in both mounted views,
the FPV mesh hiding rule, actual production mesh submissions in third person,
the five-meter rear/two-meter world-up camera boom and 70-degree production FOV,
and an upright horizon during combined 38-degree pitch/-47-degree roll metadata.
A temporary stone wall checks native client block clipping and removal restores
the full boom. F5 must retain the mounted view and B must restore the player's
current vanilla perspective. Turning off virtual control, a vanilla disconnect
from third person, and reopening must reset to line of sight. It writes three
verified PNGs in `screenshots/three-view-third-person{,-tilted,-wall}.png` and adds
`three_view_smoke.enabled`, `.passed`, `.screenshot`, and `.screenshots` to the
ordinary report. These are camera/renderer functional checks, not flight physics
or a performance benchmark. renderStress enables this flow automatically.

An optional `-Dfpvdrone.performance.modelPreview=true` JVM property adds a bounded
visual fixture before the normal measurement/save/monitor smoke. It selects the
flat world even if renderStress is also requested, fixes noon and clear weather,
and builds a small illuminated quartz platform. A first-person observer about
five meters from the drone captures front perspective, rear perspective, top,
and two spinning combined pitch/roll views. The preview uses the production
renderer/model/texture with explicit diagnostic render attitudes and RPM; it
does not claim flight-physics validation. It waits for an actual submitted model,
observer camera, unobstructed block sightline, completed terrain rendering, and
several rendered frames before each PNG readback. Each view has a 20-second
deadline. Preview options are restored before the original smoke continues.

The preview defaults to a 1600 by 1000 framebuffer and 40-degree FOV. It writes
`model-preview-report.json` beside the ordinary report, including the visual ID
read from the loaded production model, cube count, camera/pose checks, absolute
PNG paths and dimensions, and the final save/reopen/optional monitor verdict.
The ordinary report includes `model_preview.enabled`, `.passed`, and `.report`.
The helper mixin remains in this separate source set and never enters the
production JAR. Launch with `tools/start-runtime-monitor-production-smoke.ps1`,
the remapped helper and production mod already built, `-ExtraModJars` pointing
to the tested Sodium JAR, and `-DiagnosticProperties
@('-Dfpvdrone.performance.modelPreview=true')`. This launcher uses dummy auth and
a fresh directory strictly inside `build/performance`.

Both visual checks can run together with `-DiagnosticProperties
@('-Dfpvdrone.performance.modelPreview=true',
'-Dfpvdrone.performance.threeViewSmoke=true')`; the model preview finishes before
the three-view flow and existing monitor/save/flush/reopen checks continue.

Teardown starts at a normal frame boundary through Minecraft's complete
`disconnectFromWorld` path. This closes the Connection before its saving screen
waits for the integrated server. Deadline checks also run in nested teardown
frames, before the tick driver's reentrancy guard; teardown has a 30-second
failure-report deadline and the entire probe has a 180-second deadline.

Optional JFR is written alongside the JSON after measurements. Its recording
overhead is reported. All fixture creation and persistence checks execute through
the integrated server's queue, with thread assertions; client code receives only
immutable fixture metadata and completed operation results.
