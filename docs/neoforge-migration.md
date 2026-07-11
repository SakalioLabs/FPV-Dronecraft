# NeoForge Migration Ledger

This document is the release gate for the `NeoForge` branch. A migration unit is
complete only after its focused checks pass, the resulting commit is pushed, and
the branch remains usable as the base for the next unit.

## Architecture Rules

- `drone-sim-core` remains loader- and Minecraft-independent.
- Loader APIs stay in the `neoforge-mod` composition and event adapter classes.
- Registry holders stay private to registration adapter classes. Gameplay code receives
  vanilla `EntityType`, `Item`, and `SoundEvent` values through narrow accessors.
- Network payload records remain transport-only. Validation and gameplay state
  changes run through the existing control/domain services on the server thread.
- Client-only classes are registered from a client distribution entry point and
  must not be loaded by a dedicated server.
- Physics, tuning, and telemetry behavior stay frozen during loader migration.
  Behavioral changes require a later, separately verified unit.

## Migration Units

| Unit | Scope | Required verification | Status | Evidence |
| --- | --- | --- | --- | --- |
| 0 | Create `NeoForge` from `master` | Local and remote refs resolve to the same base SHA | Complete | Remote branch created from `629006dd` |
| 1 | NeoForge build scaffold and metadata | `:neoforge-mod:build`; inspect metadata and embedded core jar | Complete | `5fac5494`; `DronePhysics.class` present in jar-in-jar core |
| 2 | Stabilize the Fabric comparison gate | `:fabric-mod:runGameTest` passes all required tests | Complete | `3827b9b5`; 9/9 GameTests pass |
| 3 | Enable branch CI | Parse workflow; push triggers `NeoForge` checks | Complete | `5d0fbce7` |
| 4 | Server/common gameplay vertical slice | Core tests, NeoForge compile/tests, payload codec tests, dedicated-server smoke test | Complete | This commit; 305 JUnit tests and 240-sample simulation self-test |
| 5a | Client configuration, state, and input algorithms | Focused input/config tests, full build, dedicated-server smoke | Complete | This commit; 79 focused tests, 384 total tests, server self-test |
| 5b | Client lifecycle, key mappings, controller I/O, and networking | Focused controls tests, full build, dedicated-server smoke | Complete | This commit; 9 focused tests, 393 total tests, server self-test |
| 6 | Client rendering, HUD, audio, and Mixins | Focused tests, client launch, dedicated-server launch | Complete | This commit; 419 JUnit tests, six client Mixins applied at title-screen launch, 240-sample dedicated-server self-test |
| 7 | NeoForge GameTests and server self-tests | GameTest server plus simulation/angle/horizon/acro self-tests | Pending | Pending |
| 8 | CI, distributions, documentation, and final packaging | Full build, clean jar audit, clean client/server install | Pending | Pending |

## Verification Policy

Run checks serially because Loom and ModDevGradle share generated Minecraft and
class-output caches in this workspace. Golden traces must never be regenerated
during migration.

```powershell
$env:FPVDRONE_UPDATE_GOLDEN_TRACES = "false"
.\gradlew.bat --no-daemon --no-parallel --max-workers=1 :drone-sim-core:test
.\gradlew.bat --no-daemon --no-parallel --max-workers=1 :neoforge-mod:build
.\gradlew.bat --no-daemon --no-parallel --max-workers=1 :neoforge-mod:runServerSelfTest
```

Before a public dedicated-server release, add permission gates to the global
debug, fault-injection, environment, and tuning commands in both loader modules.
This is tracked separately so loader migration does not silently change normal
player command behavior.

Every completed unit is committed independently and pushed to
`origin/NeoForge` before work begins on the next dependent unit.
