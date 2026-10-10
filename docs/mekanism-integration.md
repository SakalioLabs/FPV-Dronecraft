# Optional Mekanism integration

The main NeoForge development target is Minecraft **1.21.1** and NeoForge **21.1.257**, with separate adapters and artifacts through **26.3**. The tested Mekanism integration uses the 1.21.1 target. Mekanism is optional: the ordinary drone and controller have vanilla crafting recipes, and a drone item without a loadout component retains the base flight behavior.

The migration's source and resource inspection uses the official [Mekanism 10.7.19.85 release for Minecraft 1.21.1](https://modrinth.com/mod/mekanism/version/5KzzycBT). That package requires NeoForge 21.1.194 or newer. Its item models and recipe outputs include all Mekanism ingredients used here. This does not establish that the unreleased Minecraft 1.21.11 Mekanism branch is a supported distribution.

## Crafting and placement

The standalone `fpvdrone:drone` recipe uses iron, copper, redstone, and a redstone block. The controller uses a compass, iron, redstone, and quartz. Neither result has a loadout component or requires another mod.

With Mekanism installed, craft the airframe parts and assemble one airframe, one power module, one radio module, and optionally one quantum link module in any crafting-grid arrangement. The assembly result is the same placement item, with the selected modules stored in its persistent, synchronized `fpvdrone:drone_loadout` data component. Duplicate modules, missing required modules, and unrelated ingredients do not match.

Use the drone item to place it. Placement initializes the selected loadout and full onboard charge once. Then hold the controller and use **Shift + right-click** on the intended owned drone to bind its UUID. Binding, reconnecting, and reloading saved chunks preserve that drone's loadout, charge, and original launch point.

Every Mekanism progression recipe, including airframe assembly, uses a `neoforge:mod_loaded` condition. NeoForge evaluates these [data load conditions](https://docs.neoforged.net/docs/1.21.1/resources/server/conditions/) before resolving absent ingredients. The component items and codecs contain no Mekanism class references.

## Power and radio

An armed, configured drone consumes `200 FE/t + throttle × 800 FE/t`; a disarmed drone consumes no flight energy. The module capacities and nominal endurance at 50% throttle are:

| Power tier | Capacity | Nominal endurance |
| --- | ---: | ---: |
| Basic | 3,600,000 FE | 5 minutes |
| Advanced | 10,800,000 FE | 15 minutes |
| Elite | 28,800,000 FE | 40 minutes |
| Ultimate | 72,000,000 FE | 100 minutes |

Each fully paid tick can run. Once neither the source nor the battery can pay a full tick, the entity removes thrust and disarms. Persisted charge is clamped to the module capacity; loading a save never refills it.

Radio distance is measured horizontally from the placement point. Altitude does not consume range. Moving a configured drone to another dimension loses its original radio link.

| Radio tier | Radius |
| --- | ---: |
| Basic | 128 m |
| Advanced | 512 m |
| Elite | 2,048 m |
| Ultimate | 8,192 m |

Outside the radius, the entity marks the input link inactive so the existing flight-model failsafe applies. Returning within range restores the ordinary link when the existing controller session is active.

## Quantum link

The optional quantum module uses a Quantum Entangloporter, Teleportation Cores, Atomic Alloys, and Antimatter. To supply a drone:

1. Select a frequency on a local Quantum Entangloporter and supply the same frequency with energy.
2. Set at least one local Entangloporter energy side to **output** or **input/output**. Keep Mekanism's Forge Energy compatibility enabled.
3. Place the equipped drone within eight blocks of that local Entangloporter. Placement binds the nearest loaded Entangloporter in the radius.
4. Keep that source chunk loaded using the world's existing chunk-loading facilities.

The server queries NeoForge's sided `Capabilities.EnergyStorage.BLOCK` as `IEnergyStorage`, honoring Mekanism's output settings and FE conversion. It simulates a full tick's cost before executing extraction. A disarmed drone only probes whether one FE could be extracted; it does not consume that FE. The implementation does not reflect into the Entangloporter or load Mekanism classes.

While the complete tick cost is supplied, the onboard battery is preserved and radio range is unlimited in the original dimension. A source that unloads, disappears, disables FE compatibility, or cannot supply the full cost immediately falls back to battery and ordinary radio range. This integration does not force-load the source chunk or replace the bound source after reload. Because `IEnergyStorage` has no rollback transaction, an unexpected partial execution is credited to the tick, and the battery pays only the unsupplied remainder.

## Entity integration hooks

`DroneDataComponents.register(modEventBus)` and `DroneRecipeSerializers.register(modEventBus)` register the loadout and assembly serializer. The entity owns one `MekanismFlightSystem` instance:

- Placement calls `configure(loadout, level, position)` only when the placement item carries `DRONE_LOADOUT`.
- The server calls `update(level, position, rawInput)` exactly once per tick after retrieving authorized input and before advancing flight physics.
- `FlightStatus.hasPower()` governs thrust/arming; `inRange()` combines with the existing input link flag. Neither hook changes the controller's UUID authorization.
- `chargeFraction()` provides battery telemetry. While `quantumActive()` is true, physical flight charge can use `1.0` while the onboard stored FE remains preserved.
- The entity calls `save(CompoundTag)` and `load(CompoundTag)` as part of its normal persistence path. Rebinding and reconnecting call neither `configure` nor `load`.
- The configured, quantum-active, charge, distance, and maximum-range values can be synchronized through the entity's data fields for the HUD. An unconfigured drone reports unrestricted status.

## Verification scope

Unit coverage includes unrestricted base drones, clamped energy demand, final paid battery ticks, dimension-aware launch radius, quantum failure and partial extraction, and persistence without recharge. Resource coverage verifies optional recipe conditions, vanilla crafting availability, and target-version ingredient objects.

The production 1.21.1 dedicated-server integration run with the official Mekanism **10.7.19.85** and Aerodynamics4MC **0.2.1** packages passed all **13** required gates (run ID `fb292e39-1c0e-423a-8841-8b2e3d3b533d`). It resolved 21 native Mekanism ingredient IDs, loaded all 17 conditional recipes, crafted 16 module recipes, persisted the assembled loadout, placed its entity, and kept an empty saved battery empty. A real public Entangloporter frequency with a configured energy output side supplied **200 FE** through its native sided capability while preserving the drone battery. Insufficient energy and removal of the source both fell back to the onboard battery. The same run checked native Aerodynamics4MC gameplay samples and the bridge's trust, clock freshness, and field values.

Evidence is in `build/production-server-12101/integration-report-fb292e39.json` and `production-integration-03.log`; validate the report with `tools/verify-neoforge-integration-report.ps1` and its expected run ID. The isolated fixture uses a native FakePlayer level anchor to activate Aerodynamics4MC sampling; it does not install a synthetic wind provider. Source chunk unloading and combined client telemetry still need a manual game test. Higher Minecraft versions require official third-party packages for those versions before their integrations can be tested.
