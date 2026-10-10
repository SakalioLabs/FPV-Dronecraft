# Minecraft 1.21.1 native client world validation

This source set is a separate development mod, `fpvdrone_client_validation`, with
its own Java package and metadata. Include it only in the dedicated validation run.
The normal client and production jar must load only the production mod.

The harness is inert unless `-Dfpvdrone.client_validation=true` is explicit. It
requires a valid UUID in `fpvdrone.client_validation.run_id`. The report contains
that UUID and passes only after all 25 required observation keys are true and the
state machine reaches `COMPLETE`. A failed or timed-out stage writes a failed
report and exits. While client ticks continue, individual stages time out after
60 seconds; native world startup has four minutes and the whole run has twelve
minutes. The launcher must also enforce an external process timeout to bound
synchronous native calls that block client ticks.

Configure a fresh named disposable world using:

```text
-Dfpvdrone.client_validation=true
-Dfpvdrone.client_validation.run_id=<fresh UUID>
-Dfpvdrone.client_validation.world=fpv-validation-<fresh UUID>
-Dfpvdrone.client_validation.create_world=true
-Dfpvdrone.client_validation.report=<absolute report.json path>
```

Fresh creation calls native `WorldOpenFlows.createFreshLevel`, and rejects an
existing world directory. Alternatively pass the exact existing disposable world
directory as the `world` property, omit `create_world`, and launch using native
`--quickPlaySingleplayer <world directory>`. The loaded integrated server's world
path must match the requested directory. The harness never substitutes a title
screen or model check for a loaded world.

Gradle integration required on the Minecraft 1.21.1 profile:

```groovy
sourceSets {
    clientValidation {
        compileClasspath += sourceSets.main.output
        runtimeClasspath += sourceSets.main.output
    }
}
configurations {
    clientValidationImplementation.extendsFrom implementation
    clientValidationRuntimeOnly.extendsFrom runtimeOnly
}
// Add sourceSets.clientValidation to neoForge.enable.enabledSourceSets.
// Register neoForge.mods.fpvdrone_client_validation from that source set.
// Dedicated clientWorldValidation run:
// client(); sourceSet = sourceSets.clientValidation
// loadedMods = [mods.fpvdrone, mods.fpvdrone_client_validation] as Set
```

The run task must remove the old report before launching, generate a fresh run
UUID, and reject missing reports, mismatched UUIDs, `passed != true`,
`all_required_stages_observed != true`, a final stage other than `COMPLETE`,
missing required check keys, and unverified screenshots. The required keys are
listed in `NativeClientWorldValidation.REQUIRED`; that list must be treated as
the report contract. It includes four independent signed turn cases.

The checks use ordinary client interaction packets for placement and Shift
binding, production keys for arm/yaw/view switching and full turns, real
`RenderPlayerEvent.Post` observations, and a hand-render mixin that only counts
native calls. FPV and third-person body screenshots require a stable render
window, actual held items, original physical position, a camera facing the body,
and zero first-person hand calls. LOS requires native hands and no own-body draw.

Full turn checks hold production keyboard pitch and roll inputs in both signs.
Each case requires at least 720 degrees of measured body-frame rotation, 80
distinct server entity pose advances, 40 native camera frames, a matching server
playable quaternion/render basis, an independent client quaternion camera basis,
and no reversed held-input response. Camera latency, tilt and vibration are set
to zero in memory for this deterministic fixture and restored before config is
saved. Native camera interpolation remains active.

Distant streaming deliberately moves only the drone with a server pose fixture,
up to 4 meters per native drone tick. A test-only tick-tail hook advances it before
the ordinary post-tick lease refresh. The fixture waits at chunk boundaries until
the next chunk is already loaded and entity-ticking through the existing native
lease; it never calls `getChunk` to prepare that step. It first verifies the
640-meter destination was not loaded, and does not preload or force destination
chunks. Route checkpoints record actual chunk readiness and any target removal.
Actual remote leases
must stream both body and drone blocks, the same drone UUID, a native ticking
chicken, two additional chunk crossings, and all three views. This is remote
streaming evidence, not a claim that a pilot flew the full route with stick input.

The physical player remains in Survival with ordinary gravity. A real Husk AI
attack must produce a native damage event, and removal of the temporary floor
must cause a real fall while FPV remains active. Controller removal must then
expire the production lease and all remote tickets without calling the session
end API. A copied real bound controller must reconnect before final cleanup.

Temporary blocks, entities, inventory, health, food, position, camera preferences
and difficulty are restored. Restoration occurs before native disconnect because
vanilla can save a player during removal before `ServerStoppingEvent`. Therefore
the final close is tested after fixture cleanup; its report separately records
the mounted lease observed before that cleanup. Native disconnect must clear
client state, and `ServerStoppedEvent` must observe zero sessions and tickets.
The close follows the native pause-menu sequence: `ClientLevel.disconnect()`
closes the network connection before `Minecraft.disconnect(screen)` tears down
the client and waits for the integrated server.
This harness does not currently prove save/reopen persistence, death/respawn,
controller hardware input, or an untouched active-lease connection close.
