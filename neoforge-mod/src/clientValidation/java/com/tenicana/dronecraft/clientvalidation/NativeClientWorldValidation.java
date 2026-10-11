package com.tenicana.dronecraft.clientvalidation;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.assembly.DroneAssemblyInventory;
import com.tenicana.dronecraft.camera.DroneViewMode;
import com.tenicana.dronecraft.client.DroneClientState;
import com.tenicana.dronecraft.client.control.DroneClientControls;
import com.tenicana.dronecraft.client.screen.DroneAssemblyScreen;
import com.tenicana.dronecraft.client.screen.CncWorkstationScreen;
import com.tenicana.dronecraft.client.config.DroneClientConfig;
import com.tenicana.dronecraft.clientvalidation.mixin.TerrainRenderProbeAccess;
import com.tenicana.dronecraft.debug.DroneDebugSettings;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.item.DroneControllerItem;
import com.tenicana.dronecraft.menu.DroneAssemblyMenu;
import com.tenicana.dronecraft.menu.CncWorkstationMenu;
import com.tenicana.dronecraft.machine.CncWorkstationBlockEntity;
import com.tenicana.dronecraft.network.DroneRemoteSessions;
import com.tenicana.dronecraft.network.DroneViewPayload;
import com.tenicana.dronecraft.registry.DroneItems;
import com.tenicana.dronecraft.registry.DroneBlocks;
import com.tenicana.dronecraft.sim.FlightMode;
import com.tenicana.dronecraft.sim.Quaternion;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Native 1.21.1 client validation in an explicitly named disposable integrated world.
 * Assertions require observations on both threads. A startup/model check never counts
 * as a world pass, and no render probe changes the production render decisions.
 */
public final class NativeClientWorldValidation {
    private static final EntityDataAccessor<Float> PITCH = accessor("PITCH");
    private static final EntityDataAccessor<Float> YAW = accessor("YAW");
    private static final EntityDataAccessor<Float> ROLL = accessor("ROLL");
    private static final int[] TURN_KEYS = {
        GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_LEFT
    };
    private static final String[] TURN_NAMES = {"positive_pitch", "negative_pitch", "positive_roll", "negative_roll"};
    private static final String[] REQUIRED = {
        "native_world_ready", "unbound_controller_air_use", "two_native_survival_placements",
        "native_cnc_survival_placement", "native_empty_hand_cnc_menu",
        "native_cnc_input_transfer_and_120_tick_four_plate_job", "native_cnc_processes_with_menu_closed",
        "native_cnc_output_blocks_extra_processing", "native_cnc_native_output_transfer", "native_cnc_machined_frame_placement",
        "native_bare_frame_placement", "native_empty_hand_assembly_menu", "native_partial_assembly_transfer",
        "native_incomplete_assembly_rejects_arm", "native_camera_optional_los_arm",
        "native_missing_camera_blocks_view_cycle", "native_missing_camera_rejects_remote_payload",
        "native_camera_removal_returns_los_without_losing_control",
        "native_complete_assembly_transfer",
        "native_component_removal_and_reinstall", "native_complete_assembly_model",
        "plain_interaction_does_not_bind", "shift_binds_first_uuid", "shift_rebinds_exact_second_uuid",
        "bound_controller_arm_and_yaw_network", "fpv_visible_stationary_body_and_hidden_hands",
        "third_person_visible_stationary_body_and_hidden_hands", "los_restores_native_hands",
        "positive_pitch_full_turn", "negative_pitch_full_turn", "positive_roll_full_turn", "negative_roll_full_turn",
        "unprepared_region_640m_streams", "dual_body_and_drone_block_updates",
        "far_fpv_native_terrain_rendered", "far_third_person_native_terrain_rendered", "los_native_terrain_returns_to_body",
        "remote_entity_ticks_and_two_chunk_crossings", "far_three_view_cycle_same_uuid",
        "real_husk_melee_damages_remote_pilot", "physical_pilot_falls_in_remote_view",
        "controller_loss_expires_lease_and_tickets", "reconnect_before_native_disconnect",
        "native_disconnect_clears_client_state", "server_stop_releases_remote_resources", "fixture_restored"
    };
    private enum Stage {
        WAIT_WORLD, SETUP, CONTROLLER_AIR, GIVE_DRONES, PLACE_FIRST, PLACE_SECOND,
        CNC_GIVE, CNC_PLACE, CNC_IDLE, CNC_OPEN, CNC_INPUT, CNC_PROCESSING, CNC_CLOSE,
        CNC_WORLD_CUTTING, CNC_WAIT_COMPLETION, CNC_REOPEN, CNC_FINISHED_MENU,
        CNC_TAKE_REMAINING_INPUT, CNC_TAKE_OUTPUT, CNC_FRAME_READY,
        GIVE_FRAME, ASSEMBLY_PREPARE, ASSEMBLY_OPEN, ASSEMBLY_PARTIAL, ASSEMBLY_PARTIAL_BODY,
        ASSEMBLY_BIND_READY, ASSEMBLY_BIND, ASSEMBLY_BLOCKED, ASSEMBLY_REOPEN_READY,
        ASSEMBLY_REOPEN, ASSEMBLY_LOS_READY, ASSEMBLY_LOS_CLOSE, ASSEMBLY_LOS_EQUIP, ASSEMBLY_LOS_ARM,
        ASSEMBLY_LOS_VIEW, ASSEMBLY_LOS_REMOTE_REQUEST, ASSEMBLY_LOS_DISARM,
        ASSEMBLY_CAMERA_OPEN, ASSEMBLY_CAMERA_INSTALL, ASSEMBLY_COMPLETE, ASSEMBLY_REMOVE, ASSEMBLY_REINSTALL, ASSEMBLY_COMPLETE_BODY,
        GIVE_CONTROLLER, PLAIN_CLICK, SHIFT_READY, BIND_FIRST, BIND_SECOND, ARM,
        YAW, DISARM, BODY_FPV, CAMERA_REMOVE_ARM, CAMERA_REMOVE_SYNC, CAMERA_RESTORE, CAMERA_RESTORE_DISARM, CAMERA_RESTORE_FPV, BODY_THIRD, BODY_LOS,
        TURN_SETUP, TURN_ARM, TURN_THROTTLE, TURN_HOLD, TURN_DISARM,
        REMOTE_SETUP, REMOTE_MOVE, REMOTE_REGION, REMOTE_CROSS_ONE, REMOTE_CROSS_TWO,
        REMOTE_THIRD, REMOTE_LOS, REMOTE_REATTACH, ATTACK, FALL,
        RETURN_NEAR, EXPIRE_LEASE, RECONNECT, DISCONNECT, COMPLETE
    }
    private static NativeClientWorldValidation active;
    private final String runId;
    private final String worldName;
    private final long startedAt = System.nanoTime();
    private final JsonObject report = new JsonObject();
    private final JsonObject checks = new JsonObject();
    private final JsonArray stages = new JsonArray();
    private final JsonArray screenshots = new JsonArray();
    private final JsonArray turns = new JsonArray();
    private final JsonArray observations = new JsonArray();
    private final JsonArray routeObservations = new JsonArray();
    private final JsonArray terrainObservations = new JsonArray();
    private final JsonArray cncObservations = new JsonArray();
    private final Map<BlockPos, BlockState> originalBlocks = new LinkedHashMap<>();
    private final List<BlockPos> platform = new ArrayList<>();
    private final Map<String, Float> originalCameraSettings = new LinkedHashMap<>();
    private final List<ItemStack> originalInventory = new ArrayList<>();
    private volatile Snapshot snapshot;
    private volatile CncRecord cncRecord;
    private volatile UUID ownerId, firstId, targetId, chickenId, huskId;
    private volatile Vec3 bodyHome, droneFixture, routeDestination, farHome;
    private volatile boolean fixturePose, fixtureRestored, stoppedClean, mobDamageObserved;
    private volatile int stoppedSessions = -1, stoppedTickets = -1;
    private long previousRouteChunk = Long.MIN_VALUE;
    private boolean previousRouteWait;
    private volatile int routeWaitTicks;
    private volatile String asynchronousFailure;
    private volatile Stage stage = Stage.WAIT_WORLD;
    private long stageStarted = startedAt;
    private int stageTick, worldStableTicks;
    private int assemblyTransferIndex;
    // Written only by the native server tick observation; they never advance or mutate the machine.
    private int cncFirstProcessingTick = -1, cncFirstCompletionTick = -1, cncCompletedJobs;
    private int cncProcessingTicksWithMenuClosed, cncOutputBlockedTicks, cncPreviousOutputCount;
    private volatile int routeCompletedTick, turnIndex;
    private long frames, bodyRenders, handsCalls;
    private int terrainFrames;
    private TerrainObservation terrain;
    private volatile long cameraSamples;
    private double maximumBodyDrift;
    private volatile double maximumCameraError, maximumModelRenderError;
    private volatile int turnAdvances;
    private int previousTurnDroneTick;
    private Quaternion previousTurnAttitude;
    private volatile double signedTurnRadians;
    private boolean createdWorld, windowStarted, originalGamepad, originalBob, clientSettingsCaptured, disconnectRecorded;
    private volatile boolean finished;
    private boolean focusPreferenceCaptured, originalPauseOnLostFocus;
    private CameraType originalCameraType;
    private DroneDebugSettings.FlightModelMode originalModel;
    private GameType originalGameType;
    private Difficulty originalDifficulty;
    private boolean originalDifficultyLocked, originalFlying, originalNoGravity;
    private int originalFood;
    private int originalSelectedSlot;
    private float originalSaturation;
    private Vec3 originalPlayerPosition;
    private float originalHealth;
    private ItemStack boundController;
    private ItemStack removedCamera;
    private BlockPos nearFloor, farFloor, bodyMarker, farMarker;
    private volatile BlockPos cncFloor, cncPosition;
    private int firstChickenTick;
    private CompletableFuture<Boolean> operation;
    private CompletableFuture<JsonObject> capture;
    private Path reportPath;

    public NativeClientWorldValidation() {
        runId = UUID.fromString(System.getProperty("fpvdrone.client_validation.run_id", "")).toString();
        worldName = System.getProperty("fpvdrone.client_validation.world", "fpv-validation-" + runId);
        require(!worldName.isBlank() && !worldName.contains("/") && !worldName.contains("\\")
                && !worldName.equals(".") && !worldName.equals(".."), "Validation requires one explicit world directory name");
        active = this;
        report.addProperty("schema_version", 1);
        report.addProperty("run_id", runId);
        report.addProperty("minecraft", "1.21.1");
        report.addProperty("world", worldName);
        report.addProperty("passed", false);
        report.addProperty("started_at_utc", Instant.now().toString());
        report.addProperty("interaction_path", "native MultiPlayerGameMode useItem/useItemOn/interactAt/interact; actual Shift state and native GameRenderer pick");
        report.addProperty("assembly_path", "Survival frame useItemOn -> empty-hand native entity interaction -> actual registered container screen and inventory click packets -> required-component R rejection -> camera-free R/LOS flight and B/payload rejection -> all 13 individual slots; all assembly installs use native inventory packets; separate camera-removal lifecycle probe mutates only the server camera slot");
        report.addProperty("cnc_path", "Survival block-item useItemOn -> empty-hand native block interaction -> actual registered CNC screen and QUICK_MOVE packets load 8 plates -> server-only 120-tick job consumes exactly 4 -> closed GUI world animation and output-full pause -> native remaining-input/output transfers -> place the actual manufactured frame from the player's inventory; no frame fixture grant");
        report.addProperty("turn_path", "production R/M/B and held keyboard controls -> production payload -> server playable quaternion -> native client camera frame");
        report.addProperty("streaming_fixture", "drone-only pose route on native DroneEntity.tick TAIL, up to 4m per tick; waits for next chunk to be loaded and entity-ticking through existing production lease; no getChunk preload or forced chunks");
        report.addProperty("body_fixture", "one initial server teleport to a temporary solid platform; normal gravity, Survival and real Husk AI; no per-tick body position writes");
        report.addProperty("render_probe", "RenderPlayerEvent.Post, native hands call counter and read-only native ViewArea/visible compiled sections; no additional model submissions or forced compilation");
        report.add("checks", checks);
        report.add("stages", stages);
        report.add("screenshots", screenshots);
        report.add("turns", turns);
        report.add("observations", observations);
        report.add("route_observations", routeObservations);
        report.add("terrain_observations", terrainObservations);
        report.add("cnc_observations", cncObservations);
    }

    public void clientTick(ClientTickEvent.Post event) {
        if (finished) return;
        Minecraft client = Minecraft.getInstance();
        if (!focusPreferenceCaptured) {
            originalPauseOnLostFocus = client.options.pauseOnLostFocus;
            client.options.pauseOnLostFocus = false;
            focusPreferenceCaptured = true;
        }
        if (reportPath == null) reportPath = Path.of(System.getProperty("fpvdrone.client_validation.report",
                client.gameDirectory.toPath().resolve("fpvdrone-client-world-validation/report.json").toString())).toAbsolutePath();
        try {
            if (asynchronousFailure != null) throw new IllegalStateException(asynchronousFailure);
            require(System.nanoTime() - startedAt < TimeUnit.MINUTES.toNanos(12), "Native world validation exceeded the total timeout");
            long timeout = stage == Stage.WAIT_WORLD ? TimeUnit.MINUTES.toNanos(4) : TimeUnit.SECONDS.toNanos(60);
            require(System.nanoTime() - stageStarted < timeout, "Timed out at " + stage + "; last server snapshot=" + snapshot + "; native terrain=" + terrain);
            if (stage == Stage.WAIT_WORLD) { waitForWorld(client); return; }
            if (stage == Stage.DISCONNECT) { finishDisconnect(client); return; }
            require(client.player != null && client.level != null && client.gameMode != null,
                    "Native world disconnected before " + stage);
            // SETUP is queued on the integrated server. It can still be generating
            // the fixture chunks when the next client tick arrives. The stage
            // timeout above bounds this initial observation handshake.
            if (stage == Stage.SETUP && snapshot == null) {
                if (operation != null && operation.isCompletedExceptionally()) operation.join();
                return;
            }
            require(snapshot != null, "Native server observation disappeared before " + stage);
            if (client.getOverlay() != null || client.screen != null
                    && !(client.screen instanceof DroneAssemblyScreen) && !(client.screen instanceof CncWorkstationScreen)) return;
            Snapshot state = snapshot;
            require(state.nativeCameraSelf(), "Server camera moved from the actual pilot at " + stage);
            if (stage.ordinal() >= Stage.ARM.ordinal() && stage.ordinal() < Stage.ATTACK.ordinal()) {
                maximumBodyDrift = Math.max(maximumBodyDrift, state.body().distanceTo(bodyHome));
                require(maximumBodyDrift < 0.30, "Undamaged physical player followed the drone: " + maximumBodyDrift);
            }
            tickStage(client, state);
        } catch (Throwable error) { finish(client, false, error); }
    }

    private void waitForWorld(Minecraft client) {
        if (client.getOverlay() == null && client.screen != null
                && client.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen")) client.setScreen(new TitleScreen());
        if (client.player == null || client.level == null || client.getSingleplayerServer() == null) {
            if (!createdWorld && Boolean.getBoolean("fpvdrone.client_validation.create_world")
                    && client.screen instanceof TitleScreen && client.getOverlay() == null) {
                Path saves = client.gameDirectory.toPath().resolve("saves").resolve(worldName);
                require(!Files.exists(saves), "Fresh validation world already exists: " + saves);
                createdWorld = true;
                client.createWorldOpenFlows().createFreshLevel(worldName,
                        new LevelSettings(worldName, GameType.SURVIVAL, false, Difficulty.NORMAL, true,
                                new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(719381239L, false, false), WorldPresets::createNormalWorldDimensions, new TitleScreen());
            }
            return;
        }
        if (client.getOverlay() != null || client.screen != null) return;
        if (++worldStableTicks < 30) return;
        MinecraftServer server = client.getSingleplayerServer();
        Path actualWorld = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        require(actualWorld.getFileName().toString().equals(worldName), "Validation world scope mismatch: " + actualWorld);
        report.addProperty("world_path", actualWorld.toString());
        report.addProperty("created_fresh_world", createdWorld);
        report.addProperty("world_stable_client_ticks", worldStableTicks);
        originalCameraType = client.options.getCameraType();
        originalBob = client.options.bobView().get();
        originalGamepad = DroneClientControls.config().gamepadEnabled();
        originalModel = DroneDebugSettings.flightModelMode();
        clientSettingsCaptured = true;
        client.options.setCameraType(CameraType.FIRST_PERSON);
        client.options.bobView().set(false);
        client.options.keyShift.setDown(false);
        if (originalGamepad) click(GLFW.GLFW_KEY_G);
        if (DroneClientState.isVirtualControllerEnabled()) click(GLFW.GLFW_KEY_V);
        ownerId = client.player.getUUID();
        operation = submit(client, current -> {
            ServerPlayer player = player(current);
            ServerLevel level = player.serverLevel();
            originalPlayerPosition = player.position();
            originalHealth = player.getHealth();
            originalGameType = player.gameMode.getGameModeForPlayer();
            originalDifficulty = current.getWorldData().getDifficulty();
            originalDifficultyLocked = current.getWorldData().isDifficultyLocked();
            originalFlying = player.getAbilities().flying;
            originalNoGravity = player.isNoGravity();
            originalFood = player.getFoodData().getFoodLevel();
            originalSaturation = player.getFoodData().getSaturationLevel();
            originalSelectedSlot = player.getInventory().selected;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) originalInventory.add(player.getInventory().getItem(i).copy());
            int x = player.blockPosition().getX() + 24, z = player.blockPosition().getZ() + 24;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 6;
            bodyHome = new Vec3(x + 0.5, y, z + 0.5);
            nearFloor = BlockPos.containing(bodyHome).offset(1, -1, 0);
            farFloor = BlockPos.containing(bodyHome).offset(2, -1, 1);
            cncFloor = BlockPos.containing(bodyHome).offset(0, -1, 2);
            cncPosition = cncFloor.above();
            for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
                BlockPos floor = BlockPos.containing(bodyHome).offset(dx, -1, dz);
                platform.add(floor);
                block(level, floor, Blocks.STONE.defaultBlockState());
                for (int dy = 1; dy <= 4; dy++) block(level, floor.above(dy), Blocks.AIR.defaultBlockState());
            }
            player.setGameMode(GameType.SURVIVAL);
            current.setDifficulty(Difficulty.NORMAL, true);
            player.setInvulnerable(false);
            player.setNoGravity(false);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            player.setHealth(20);
            player.getFoodData().setFoodLevel(20);
            player.setDeltaMovement(Vec3.ZERO);
            player.connection.teleport(bodyHome.x, bodyHome.y, bodyHome.z, 0, 0);
            equip(player, new ItemStack(DroneItems.droneController()), ItemStack.EMPTY);
            DroneDebugSettings.setFlightModelMode(DroneDebugSettings.FlightModelMode.PLAYABLE);
        });
        advance(Stage.SETUP);
    }

    private void tickStage(Minecraft client, Snapshot state) {
        switch (stage) {
            case SETUP -> {
                if (!operationDone() || !held(client, DroneItems.droneController(), 1) || !settled(12)
                        || DroneClientControls.config().gamepadEnabled() || DroneClientState.isVirtualControllerEnabled()
                        || !client.player.onGround() || state.body().distanceTo(bodyHome) > 0.10) return;
                require(state.drones().isEmpty() && state.binding() == null, "Placement fixture contains an existing drone or binding");
                check("native_world_ready");
                client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
                advance(Stage.CONTROLLER_AIR);
            }
            case CONTROLLER_AIR -> {
                if (!settled(12)) return;
                require(state.drones().isEmpty() && state.binding() == null, "Unbound controller air use spawned or bound a drone");
                check("unbound_controller_air_use");
                operation = submit(client, server -> equip(player(server), new ItemStack(DroneItems.drone()), ItemStack.EMPTY));
                advance(Stage.GIVE_DRONES);
            }
            case GIVE_DRONES -> { if (operationDone() && held(client, DroneItems.drone(), 1)) { place(client, nearFloor); advance(Stage.PLACE_FIRST); } }
            case PLACE_FIRST -> {
                if (state.drones().size() != 1 || !client.player.getMainHandItem().isEmpty() || !settled(8)) return;
                firstId = state.drones().getFirst().id();
                require(state.drones().getFirst().owner() == null && !state.drones().getFirst().armed(), "Native placement auto-bound or armed the first drone");
                operation = submit(client, server -> {
                    ServerPlayer pilot = player(server);
                    require(inventoryCount(pilot, DroneItems.carbonFrame()) == 0
                            && inventoryCount(pilot, DroneItems.carbonPlate()) == 0, "CNC fixture already contains plate or frame items");
                    pilot.getInventory().setItem(9, new ItemStack(DroneItems.carbonPlate(), 8));
                    equip(pilot, new ItemStack(DroneItems.cncWorkstation()), ItemStack.EMPTY);
                });
                advance(Stage.CNC_GIVE);
            }
            case CNC_GIVE -> {
                if (!operationDone() || !held(client, DroneItems.cncWorkstation(), 1)
                        || client.player.getInventory().getItem(9).getCount() != 8 || !settled(8)) return;
                place(client, cncFloor);
                advance(Stage.CNC_PLACE);
            }
            case CNC_PLACE -> {
                CncRecord cnc = cncRecord;
                if (cnc == null || !cnc.present() || !client.player.getMainHandItem().isEmpty()
                        || !client.level.getBlockState(cncPosition).is(DroneBlocks.cncWorkstation()) || !settled(8)) return;
                require(cnc.progress() == 0 && cnc.inputCount() == 0 && cnc.outputCount() == 0
                        && cnc.playerPlateCount() == 8 && cnc.playerFrameCount() == 0
                        && client.level.getBlockEntity(cncPosition) instanceof CncWorkstationBlockEntity,
                        "Native CNC placement was not an empty Survival machine with one consumed block item");
                check("native_cnc_survival_placement");
                lookAt(client, Vec3.atCenterOf(cncPosition));
                capture(client, "cnc-workstation-idle");
                advance(Stage.CNC_IDLE);
            }
            case CNC_IDLE -> {
                if (!captureDone()) return;
                openCnc(client);
                advance(Stage.CNC_OPEN);
            }
            case CNC_OPEN -> {
                if (!(client.screen instanceof CncWorkstationScreen) || !settled(8)) return;
                CncWorkstationMenu menu = cncMenu(client);
                require(menu.slots.size() == 38 && menu.slots.get(0).getItem().isEmpty()
                        && menu.slots.get(1).getItem().isEmpty() && menu.getProgress() == 0
                        && menu.getProcessingTicks() == 120 && menu.stillValid(client.player), "Native empty CNC menu is invalid");
                check("native_empty_hand_cnc_menu");
                // Machine slots 0/1 are followed by the native player inventory, starting at player slot 9.
                client.gameMode.handleInventoryMouseClick(menu.containerId, 2, 0, ClickType.QUICK_MOVE, client.player);
                advance(Stage.CNC_INPUT);
            }
            case CNC_INPUT -> {
                CncRecord cnc = cncRecord;
                CncWorkstationMenu menu = cncMenu(client);
                if (cnc == null || cnc.progress() < 8 || menu.getProgress() < 8 || !settled(8)) return;
                require(cnc.inputPlate() && cnc.inputCount() == 8 && cnc.outputCount() == 0
                        && cnc.playerPlateCount() == 0 && cnc.playerFrameCount() == 0
                        && menu.slots.get(0).getItem().is(DroneItems.carbonPlate())
                        && menu.slots.get(0).getItem().getCount() == 8 && menu.slots.get(1).getItem().isEmpty(),
                        "Native input click did not transfer the actual eight plates or granted an early frame");
                capture(client, "cnc-processing-menu");
                advance(Stage.CNC_PROCESSING);
            }
            case CNC_PROCESSING -> {
                if (!captureDone()) return;
                require(cncRecord != null && cncRecord.progress() > 0 && cncRecord.outputCount() == 0,
                        "CNC finished before the native menu-closed processing probe");
                client.player.closeContainer();
                advance(Stage.CNC_CLOSE);
            }
            case CNC_CLOSE -> {
                CncRecord cnc = cncRecord;
                if (client.screen != null || cnc == null || cnc.menuOpen() || !settled(8)
                        || !(client.level.getBlockEntity(cncPosition) instanceof CncWorkstationBlockEntity clientMachine)
                        || clientMachine.getProgress() <= 0 || !clientMachine.getItem(0).is(DroneItems.carbonPlate())) return;
                require(cnc.progress() > 0 && cnc.outputCount() == 0, "Closing the native menu ended or completed the CNC job early");
                lookAt(client, Vec3.atCenterOf(cncPosition));
                capture(client, "cnc-cutting-world");
                advance(Stage.CNC_WORLD_CUTTING);
            }
            case CNC_WORLD_CUTTING -> {
                if (!captureDone()) return;
                advance(Stage.CNC_WAIT_COMPLETION);
            }
            case CNC_WAIT_COMPLETION -> {
                CncRecord cnc = cncRecord;
                if (cnc == null || cnc.firstCompletionTick() < 0 || cnc.outputBlockedTicks() < 125) return;
                require(cnc.processingTicks() == 120 && cnc.firstCompletionTick() - cnc.firstProcessingTick() + 1 == 120
                        && cnc.inputPlate() && cnc.inputCount() == 4 && cnc.outputFrame() && cnc.outputCount() == 1
                        && cnc.playerPlateCount() == 0 && cnc.playerFrameCount() == 0 && cnc.completedJobs() == 1,
                        "CNC did not finish one exact 120-tick four-plate job with one existing carbon frame");
                check("native_cnc_input_transfer_and_120_tick_four_plate_job");
                require(cnc.processingTicksWithMenuClosed() >= 30, "CNC did not advance substantially while its native menu was closed");
                check("native_cnc_processes_with_menu_closed");
                require(cnc.progress() == 0 && cnc.completedJobs() == 1 && cnc.outputBlockedTicks() >= 125,
                        "Full CNC output slot allowed another job or consumed the remaining four plates");
                check("native_cnc_output_blocks_extra_processing");
                openCnc(client);
                advance(Stage.CNC_REOPEN);
            }
            case CNC_REOPEN -> {
                if (!(client.screen instanceof CncWorkstationScreen) || !settled(8)) return;
                CncWorkstationMenu menu = cncMenu(client);
                if (menu.slots.get(0).getItem().getCount() != 4 || !menu.slots.get(1).getItem().is(DroneItems.carbonFrame())) return;
                require(menu.slots.get(1).getItem().getCount() == 1 && menu.getProgress() == 0,
                        "Reopened native CNC menu lost its one completed output or paused state");
                capture(client, "cnc-finished-menu");
                advance(Stage.CNC_FINISHED_MENU);
            }
            case CNC_FINISHED_MENU -> {
                if (!captureDone()) return;
                CncWorkstationMenu menu = cncMenu(client);
                // Return unused stock before clearing the output, so no second cycle can start.
                client.gameMode.handleInventoryMouseClick(menu.containerId, 0, 0, ClickType.QUICK_MOVE, client.player);
                advance(Stage.CNC_TAKE_REMAINING_INPUT);
            }
            case CNC_TAKE_REMAINING_INPUT -> {
                CncRecord cnc = cncRecord;
                CncWorkstationMenu menu = cncMenu(client);
                if (cnc == null || cnc.inputCount() != 0 || cnc.playerPlateCount() != 4
                        || !menu.slots.get(0).getItem().isEmpty() || !settled(8)) return;
                require(cnc.outputFrame() && cnc.outputCount() == 1 && cnc.completedJobs() == 1,
                        "Native remaining-input transfer consumed, duplicated, or lost the finished frame");
                client.gameMode.handleInventoryMouseClick(menu.containerId, 1, 0, ClickType.QUICK_MOVE, client.player);
                advance(Stage.CNC_TAKE_OUTPUT);
            }
            case CNC_TAKE_OUTPUT -> {
                CncRecord cnc = cncRecord;
                CncWorkstationMenu menu = cncMenu(client);
                if (cnc == null || cnc.outputCount() != 0 || cnc.playerFrameCount() != 1
                        || !menu.slots.get(1).getItem().isEmpty() || !settled(8)) return;
                require(cnc.inputCount() == 0 && cnc.progress() == 0 && cnc.playerPlateCount() == 4
                        && cnc.completedJobs() == 1 && inventoryCount(client.player, DroneItems.carbonFrame()) == 1,
                        "Native output transfer lost or duplicated the actual manufactured frame");
                check("native_cnc_native_output_transfer");
                client.player.closeContainer();
                advance(Stage.CNC_FRAME_READY);
            }
            case CNC_FRAME_READY -> {
                if (client.screen != null || !settled(8)) return;
                int actualFrameSlot = -1;
                for (int index = 0; index < 9; index++) if (client.player.getInventory().getItem(index).is(DroneItems.carbonFrame())) {
                    require(actualFrameSlot == -1, "CNC output occupies multiple hotbar slots");
                    actualFrameSlot = index;
                }
                require(actualFrameSlot >= 0 && inventoryCount(client.player, DroneItems.carbonFrame()) == 1,
                        "Native QUICK_MOVE did not put its one physical frame into the player's hotbar");
                client.player.getInventory().selected = actualFrameSlot;
                report.addProperty("cnc_manufactured_frame_hotbar_slot", actualFrameSlot);
                advance(Stage.GIVE_FRAME);
            }
            case GIVE_FRAME -> {
                if (!held(client, DroneItems.carbonFrame(), 1) || !settled(8)) return;
                require(cncRecord != null && cncRecord.playerFrameCount() == 1 && cncRecord.completedJobs() == 1,
                        "The second aircraft frame did not come from the observed CNC output");
                place(client, farFloor);
                advance(Stage.PLACE_SECOND);
            }
            case PLACE_SECOND -> {
                if (state.drones().size() != 2 || !client.player.getMainHandItem().isEmpty() || !settled(8)) return;
                targetId = state.drones().stream().filter(drone -> !drone.id().equals(firstId)).findFirst().orElseThrow().id();
                require(record(state, firstId).owner() == null && ownerId.equals(record(state, targetId).owner())
                        && record(state, targetId).assemblyMask() == 0 && !record(state, targetId).armed()
                        && ((DroneEntity) clientEntity(client, targetId)).getAssemblyMask() == 0 && state.binding() == null,
                        "Native bare frame was assembled, armed, or controller-bound during placement");
                check("two_native_survival_placements");
                check("native_bare_frame_placement");
                require(cncRecord != null && cncRecord.playerFrameCount() == 0 && cncRecord.completedJobs() == 1,
                        "Native bare-frame placement did not consume the one manufactured frame");
                check("native_cnc_machined_frame_placement");
                capture(client, "assembly-bare-frame");
                operation = submit(client, server -> {
                    ServerPlayer player = player(server);
                    player.getInventory().setItem(9, new ItemStack(DroneItems.brushlessMotor(), 4));
                    player.getInventory().setItem(10, new ItemStack(DroneItems.propeller(), 4));
                    player.getInventory().setItem(11, new ItemStack(DroneItems.esc()));
                    player.getInventory().setItem(12, new ItemStack(DroneItems.flightController()));
                    player.getInventory().setItem(13, new ItemStack(DroneItems.receiver()));
                    player.getInventory().setItem(14, new ItemStack(DroneItems.lipoBattery()));
                    player.getInventory().setItem(15, new ItemStack(DroneItems.fpvCamera()));
                    equip(player, ItemStack.EMPTY, ItemStack.EMPTY);
                });
                advance(Stage.ASSEMBLY_PREPARE);
            }
            case ASSEMBLY_PREPARE -> {
                if (!captureDone() || !operationDone() || !settled(8)
                        || client.player.getInventory().getItem(9).getCount() != 4) return;
                interact(client, targetId, false);
                advance(Stage.ASSEMBLY_OPEN);
            }
            case ASSEMBLY_OPEN -> {
                if (!(client.screen instanceof DroneAssemblyScreen) || !settled(8)) return;
                DroneAssemblyMenu menu = assemblyMenu(client);
                require(menu.slots.size() == 49 && installed(menu) == 0 && menu.stillValid(client.player), "Native empty-hand assembly menu is invalid");
                check("native_empty_hand_assembly_menu");
                capture(client, "assembly-empty-menu");
                assemblyTransferIndex = 9;
                advance(Stage.ASSEMBLY_PARTIAL);
            }
            case ASSEMBLY_PARTIAL -> {
                if (!captureDone()) return;
                DroneAssemblyMenu menu = assemblyMenu(client);
                if (assemblyTransferIndex <= 14) {
                    int playerSlot = assemblyTransferIndex++;
                    if (playerSlot != 12) transfer(client, menu, DroneAssemblyInventory.SLOT_COUNT + playerSlot - 9);
                    return;
                }
                int partialMask = DroneAssemblyInventory.REQUIRED_FLIGHT_MASK & ~(1 << DroneAssemblyInventory.FLIGHT_CONTROLLER);
                if (record(state, targetId).assemblyMask() != partialMask || installed(menu) != 11
                        || ((DroneEntity) clientEntity(client, targetId)).getAssemblyMask() != partialMask || !settled(8)) return;
                require(menu.slots.get(12).getItem().isEmpty(), "Native transfer installed an unavailable camera");
                check("native_partial_assembly_transfer");
                capture(client, "assembly-partial-menu");
                advance(Stage.ASSEMBLY_PARTIAL_BODY);
            }
            case ASSEMBLY_PARTIAL_BODY -> {
                if (!captureDone()) return;
                if (client.screen != null) { client.player.closeContainer(); return; }
                if (!settled(12)) return;
                lookAt(client, clientEntity(client, targetId).getBoundingBox().getCenter());
                capture(client, "assembly-partial-frame");
                operation = submit(client, server -> equip(player(server), new ItemStack(DroneItems.droneController()), ItemStack.EMPTY));
                client.options.keyShift.setDown(true);
                advance(Stage.ASSEMBLY_BIND_READY);
            }
            case ASSEMBLY_BIND_READY -> {
                if (!captureDone() || !operationDone() || !held(client, DroneItems.droneController(), 1)
                        || !client.player.isShiftKeyDown() || !state.shift() || !settled(8)) return;
                interact(client, targetId, true);
                advance(Stage.ASSEMBLY_BIND);
            }
            case ASSEMBLY_BIND -> {
                if (!bound(client, state, targetId) || !settled(8)) return;
                client.options.keyShift.setDown(false);
                click(GLFW.GLFW_KEY_R);
                advance(Stage.ASSEMBLY_BLOCKED);
            }
            case ASSEMBLY_BLOCKED -> {
                if (!settled(12)) return;
                require(!DroneClientState.armed() && !record(state, targetId).armed()
                        && !((DroneEntity) clientEntity(client, targetId)).isFlightReady(),
                        "Production R armed an aircraft missing its flight controller");
                check("native_incomplete_assembly_rejects_arm");
                boundController = client.player.getMainHandItem().copy();
                operation = submit(client, server -> equip(player(server), ItemStack.EMPTY, ItemStack.EMPTY));
                advance(Stage.ASSEMBLY_REOPEN_READY);
            }
            case ASSEMBLY_REOPEN_READY -> {
                if (!operationDone() || !client.player.getMainHandItem().isEmpty() || state.shift() || !settled(8)) return;
                interact(client, targetId, false);
                advance(Stage.ASSEMBLY_REOPEN);
            }
            case ASSEMBLY_REOPEN -> {
                if (!(client.screen instanceof DroneAssemblyScreen) || !settled(8)) return;
                transfer(client, assemblyMenu(client), DroneAssemblyInventory.SLOT_COUNT + 12 - 9);
                advance(Stage.ASSEMBLY_LOS_READY);
            }
            case ASSEMBLY_LOS_READY -> {
                if (record(state, targetId).assemblyMask() != DroneAssemblyInventory.REQUIRED_FLIGHT_MASK
                        || !((DroneEntity) clientEntity(client, targetId)).isFlightReady() || !settled(8)) return;
                require(!((DroneEntity) clientEntity(client, targetId)).hasCamera(), "Camera-free flight fixture contains a camera");
                capture(client, "assembly-camera-optional-menu");
                advance(Stage.ASSEMBLY_LOS_CLOSE);
            }
            case ASSEMBLY_LOS_CLOSE -> {
                if (!captureDone()) return;
                if (client.screen != null) { client.player.closeContainer(); return; }
                operation = submit(client, server -> equip(player(server), boundController.copy(), ItemStack.EMPTY));
                advance(Stage.ASSEMBLY_LOS_EQUIP);
            }
            case ASSEMBLY_LOS_EQUIP -> {
                if (!operationDone() || !bound(client, state, targetId) || !settled(8)) return;
                click(GLFW.GLFW_KEY_R);
                advance(Stage.ASSEMBLY_LOS_ARM);
            }
            case ASSEMBLY_LOS_ARM -> {
                if (!record(state, targetId).armed() || !DroneClientState.armed() || !settled(12)) return;
                require(los(client, state) && targetId.equals(state.sessionDrone()) && state.tickets() > 0,
                        "Camera-free arming failed to retain LOS and the native control/chunk lease");
                check("native_camera_optional_los_arm");
                click(GLFW.GLFW_KEY_B);
                advance(Stage.ASSEMBLY_LOS_VIEW);
            }
            case ASSEMBLY_LOS_VIEW -> {
                if (!settled(12)) return;
                require(los(client, state) && record(state, targetId).armed() && DroneClientState.armed(),
                        "B enabled a remote camera or interrupted camera-free flight");
                check("native_missing_camera_blocks_view_cycle");
                PacketDistributor.sendToServer(new DroneViewPayload(true));
                // Assert the authoritative production gate immediately, before normal LOS
                // heartbeats can overwrite a wrongly accepted remote flag.
                operation = submit(client, server -> {
                    ServerPlayer pilot = player(server);
                    DroneRemoteSessions.setRemoteView(pilot, true);
                    require(!DroneRemoteSessions.isRemoteView(pilot) && DroneRemoteSessions.trackedDrone(pilot) == drone(server),
                            "Authoritative remote-view gate accepted a camera-free drone or ended its control lease");
                });
                advance(Stage.ASSEMBLY_LOS_REMOTE_REQUEST);
            }
            case ASSEMBLY_LOS_REMOTE_REQUEST -> {
                if (!operationDone() || !settled(12)) return;
                require(los(client, state) && record(state, targetId).armed() && targetId.equals(state.sessionDrone()) && state.tickets() > 0,
                        "Server accepted a camera-free remote request or discarded the native control/chunk lease");
                check("native_missing_camera_rejects_remote_payload");
                click(GLFW.GLFW_KEY_R);
                advance(Stage.ASSEMBLY_LOS_DISARM);
            }
            case ASSEMBLY_LOS_DISARM -> {
                if (record(state, targetId).armed() || DroneClientState.armed() || !settled(12)) return;
                operation = submit(client, server -> equip(player(server), ItemStack.EMPTY, ItemStack.EMPTY));
                advance(Stage.ASSEMBLY_CAMERA_OPEN);
            }
            case ASSEMBLY_CAMERA_OPEN -> {
                if (!operationDone() || !client.player.getMainHandItem().isEmpty() || !settled(8)) return;
                interact(client, targetId, false);
                advance(Stage.ASSEMBLY_CAMERA_INSTALL);
            }
            case ASSEMBLY_CAMERA_INSTALL -> {
                if (!(client.screen instanceof DroneAssemblyScreen) || !settled(8)) return;
                transfer(client, assemblyMenu(client), DroneAssemblyInventory.SLOT_COUNT + 15 - 9);
                advance(Stage.ASSEMBLY_COMPLETE);
            }
            case ASSEMBLY_COMPLETE -> {
                DroneAssemblyMenu menu = assemblyMenu(client);
                if (record(state, targetId).assemblyMask() != DroneAssemblyInventory.FULL_MASK || installed(menu) != 13
                        || !((DroneEntity) clientEntity(client, targetId)).isAssemblyComplete() || !settled(8)) return;
                require(menu.slots.subList(0, 13).stream().allMatch(slot -> slot.getItem().getCount() == 1), "Assembly slots contain multiple physical components");
                check("native_complete_assembly_transfer");
                capture(client, "assembly-complete-menu");
                advance(Stage.ASSEMBLY_REMOVE);
            }
            case ASSEMBLY_REMOVE -> {
                if (!captureDone()) return;
                transfer(client, assemblyMenu(client), DroneAssemblyInventory.CAMERA);
                advance(Stage.ASSEMBLY_REINSTALL);
            }
            case ASSEMBLY_REINSTALL -> {
                DroneAssemblyMenu menu = assemblyMenu(client);
                if (assemblyTransferIndex == -1) {
                    if (record(state, targetId).assemblyMask() != DroneAssemblyInventory.FULL_MASK || installed(menu) != 13 || !settled(8)) return;
                    check("native_component_removal_and_reinstall");
                    advance(Stage.ASSEMBLY_COMPLETE_BODY);
                    return;
                }
                if (record(state, targetId).assemblyMask() != 4095 || installed(menu) != 12 || !settled(8)) return;
                int cameraSlot = -1, cameras = 0;
                for (int index = 13; index < menu.slots.size(); index++) if (menu.slots.get(index).getItem().is(DroneItems.fpvCamera())) {
                    cameraSlot = index;
                    cameras += menu.slots.get(index).getItem().getCount();
                }
                require(cameraSlot >= 0 && cameras == 1, "Removing a component lost or duplicated its physical item");
                transfer(client, menu, cameraSlot);
                assemblyTransferIndex = -1;
            }
            case ASSEMBLY_COMPLETE_BODY -> {
                if (client.screen != null) { client.player.closeContainer(); return; }
                if (!settled(12)) return;
                require(((DroneEntity) clientEntity(client, targetId)).isAssemblyComplete() && !record(state, targetId).armed(), "Assembled world model lost its synchronized state");
                check("native_complete_assembly_model");
                lookAt(client, clientEntity(client, targetId).getBoundingBox().getCenter());
                capture(client, "assembly-complete-frame");
                operation = submit(client, server -> equip(player(server), new ItemStack(DroneItems.droneController()), new ItemStack(Items.SHIELD)));
                advance(Stage.GIVE_CONTROLLER);
            }
            case GIVE_CONTROLLER -> {
                if (!captureDone() || !operationDone() || !held(client, DroneItems.droneController(), 1) || !settled(8)) return;
                interact(client, targetId, false);
                advance(Stage.PLAIN_CLICK);
            }
            case PLAIN_CLICK -> {
                if (!settled(12)) return;
                require(state.binding() == null && DroneControllerItem.boundDroneId(client.player) == null,
                        "Plain entity interaction bound a controller");
                require(record(state, firstId).owner() == null && ownerId.equals(record(state, targetId).owner()), "Plain entity interaction changed frame ownership");
                check("plain_interaction_does_not_bind");
                client.options.keyShift.setDown(true);
                advance(Stage.SHIFT_READY);
            }
            case SHIFT_READY -> { if (client.player.isShiftKeyDown() && state.shift() && settled(8)) { interact(client, firstId, true); advance(Stage.BIND_FIRST); } }
            case BIND_FIRST -> {
                if (!bound(client, state, firstId) || !settled(8)) return;
                check("shift_binds_first_uuid");
                interact(client, targetId, true);
                advance(Stage.BIND_SECOND);
            }
            case BIND_SECOND -> {
                if (!bound(client, state, targetId) || !settled(8)) return;
                require(ownerId.equals(record(state, firstId).owner()) && ownerId.equals(record(state, targetId).owner()), "Shift interaction did not assign real ownership");
                require(record(state, firstId).position().distanceTo(state.body()) < record(state, targetId).position().distanceTo(state.body()), "Exact target fixture was not farther than the owned peer");
                boundController = client.player.getMainHandItem().copy();
                client.options.keyShift.setDown(false);
                check("shift_rebinds_exact_second_uuid");
                report.addProperty("bound_drone_uuid", targetId.toString());
                report.addProperty("nearer_owned_peer_uuid", firstId.toString());
                click(GLFW.GLFW_KEY_R);
                advance(Stage.ARM);
            }
            case ARM -> {
                if (!record(state, targetId).armed() || !DroneClientState.armed() || !settled(12)) return;
                require(!record(state, firstId).armed(), "Arming affected the other owned drone");
                key(GLFW.GLFW_KEY_Z, true);
                advance(Stage.YAW);
            }
            case YAW -> {
                if (!settled(25) || Math.abs(record(state, targetId).yawInput()) < 0.5) return;
                require(!record(state, firstId).armed() && Math.abs(record(state, firstId).yawInput()) < 0.001, "Control payload reached a different owned UUID");
                check("bound_controller_arm_and_yaw_network");
                key(GLFW.GLFW_KEY_Z, false);
                click(GLFW.GLFW_KEY_R);
                advance(Stage.DISARM);
            }
            case DISARM -> {
                if (record(state, targetId).armed() || DroneClientState.armed() || !settled(12)) return;
                cameraSetting("cameraLatencySeconds", 0);
                cameraSetting("cameraVibrationScale", 0);
                cameraSetting("cameraTiltDegrees", 0);
                fixturePose = true;
                droneFixture = bodyHome.add(-2, 1.3, -7);
                click(GLFW.GLFW_KEY_B);
                advance(Stage.BODY_FPV);
            }
            case BODY_FPV -> {
                if (!remote(client, state, DroneViewMode.FPV) || !settled(12) || !renderWindow()) return;
                visibleBody(client, state);
                require(!client.gameRenderer.getMainCamera().isDetached(), "Native FPV camera was detached");
                check("fpv_visible_stationary_body_and_hidden_hands");
                capture(client, "pilot-body-fpv");
                click(GLFW.GLFW_KEY_R);
                advance(Stage.CAMERA_REMOVE_ARM);
            }
            case CAMERA_REMOVE_ARM -> {
                if (!captureDone() || !record(state, targetId).armed() || !DroneClientState.armed() || !settled(12)) return;
                require(remote(client, state, DroneViewMode.FPV), "Camera-removal probe did not start in native FPV");
                operation = submit(client, server -> removedCamera = drone(server).assemblyInventory().removeItem(DroneAssemblyInventory.CAMERA, 1));
                advance(Stage.CAMERA_REMOVE_SYNC);
            }
            case CAMERA_REMOVE_SYNC -> {
                if (!operationDone() || !settled(12)) return;
                require(los(client, state) && bound(client, state, targetId) && record(state, targetId).armed()
                        && DroneClientState.armed() && targetId.equals(state.sessionDrone()) && state.tickets() > 0
                        && ((DroneEntity) clientEntity(client, targetId)).isFlightReady()
                        && !((DroneEntity) clientEntity(client, targetId)).hasCamera(),
                        "Camera metadata removal did not return to the body camera while preserving armed control/chunk lease");
                check("native_camera_removal_returns_los_without_losing_control");
                operation = submit(client, server -> drone(server).assemblyInventory().setItem(DroneAssemblyInventory.CAMERA, removedCamera));
                advance(Stage.CAMERA_RESTORE);
            }
            case CAMERA_RESTORE -> {
                if (!operationDone() || !((DroneEntity) clientEntity(client, targetId)).hasCamera() || !settled(12)) return;
                require(los(client, state) && record(state, targetId).armed() && DroneClientState.armed(),
                        "Camera reinstall unexpectedly entered remote view or changed arming");
                click(GLFW.GLFW_KEY_R);
                advance(Stage.CAMERA_RESTORE_DISARM);
            }
            case CAMERA_RESTORE_DISARM -> {
                if (record(state, targetId).armed() || DroneClientState.armed() || !settled(12)) return;
                click(GLFW.GLFW_KEY_B);
                advance(Stage.CAMERA_RESTORE_FPV);
            }
            case CAMERA_RESTORE_FPV -> {
                if (!remote(client, state, DroneViewMode.FPV) || !settled(12)) return;
                click(GLFW.GLFW_KEY_B);
                advance(Stage.BODY_THIRD);
            }
            case BODY_THIRD -> {
                if (!captureDone() || !remote(client, state, DroneViewMode.THIRD_PERSON) || !settled(12) || !renderWindow()) return;
                visibleBody(client, state);
                require(client.gameRenderer.getMainCamera().isDetached(), "Native drone third-person camera was not detached");
                check("third_person_visible_stationary_body_and_hidden_hands");
                capture(client, "pilot-body-third-person");
                click(GLFW.GLFW_KEY_B);
                advance(Stage.BODY_LOS);
            }
            case BODY_LOS -> {
                if (!captureDone() || !los(client, state) || !settled(12) || !renderWindow()) return;
                require(bodyRenders == 0 && handsCalls >= 4, "LOS failed to restore native hands or rendered own first-person body");
                check("los_restores_native_hands");
                capture(client, "los-native-hands");
                click(GLFW.GLFW_KEY_M);
                click(GLFW.GLFW_KEY_M);
                fixturePose = false;
                advance(Stage.TURN_SETUP);
            }
            case TURN_SETUP -> {
                if (!captureDone() || DroneClientState.flightMode() != FlightMode.ACRO || DroneClientState.armed()) return;
                if (operation != null && !operationDone()) return;
                operation = submit(client, server -> {
                    DroneEntity drone = drone(server);
                    drone.setPos(bodyHome.x, player(server).serverLevel().getMaxBuildHeight() - 12, bodyHome.z + 8);
                    drone.setDeltaMovement(Vec3.ZERO);
                });
                previousTurnAttitude = null;
                previousTurnDroneTick = -1;
                signedTurnRadians = 0;
                turnAdvances = 0;
                cameraSamples = 0;
                maximumCameraError = 0;
                maximumModelRenderError = 0;
                if (DroneClientState.viewMode() == DroneViewMode.LINE_OF_SIGHT) click(GLFW.GLFW_KEY_B);
                click(GLFW.GLFW_KEY_R);
                advance(Stage.TURN_ARM);
            }
            case TURN_ARM -> {
                if (!operationDone() || !remote(client, state, DroneViewMode.FPV) || !record(state, targetId).armed() || !settled(8)) return;
                key(GLFW.GLFW_KEY_PAGE_UP, true);
                advance(Stage.TURN_THROTTLE);
            }
            case TURN_THROTTLE -> {
                if (DroneClientState.throttle() < 0.52f) return;
                key(GLFW.GLFW_KEY_PAGE_UP, false);
                key(TURN_KEYS[turnIndex], true);
                advance(Stage.TURN_HOLD);
            }
            case TURN_HOLD -> {
                require(remote(client, state, DroneViewMode.FPV) && record(state, targetId).armed(), "Held turn lost the real remote control session");
                if (signedTurnRadians < Math.PI * 4 || turnAdvances < 80 || cameraSamples < 40) return;
                key(TURN_KEYS[turnIndex], false);
                JsonObject turn = new JsonObject();
                turn.addProperty("axis", TURN_NAMES[turnIndex]);
                turn.addProperty("signed_body_rotation_degrees", Math.toDegrees(signedTurnRadians));
                turn.addProperty("distinct_entity_pose_advances", turnAdvances);
                turn.addProperty("native_camera_frames", cameraSamples);
                turn.addProperty("maximum_camera_basis_error", maximumCameraError);
                turn.addProperty("maximum_server_model_render_basis_error", maximumModelRenderError);
                turns.add(turn);
                check(TURN_NAMES[turnIndex] + "_full_turn");
                capture(client, TURN_NAMES[turnIndex] + "-720-degree-turn");
                click(GLFW.GLFW_KEY_R);
                advance(Stage.TURN_DISARM);
            }
            case TURN_DISARM -> {
                if (!captureDone() || DroneClientState.armed() || record(state, targetId).armed() || !settled(12)) return;
                // Require a centered throttle before the next native safety arm.
                if (DroneClientState.throttle() > 0.001f) { key(GLFW.GLFW_KEY_PAGE_DOWN, true); return; }
                key(GLFW.GLFW_KEY_PAGE_DOWN, false);
                if (++turnIndex < TURN_KEYS.length) advance(Stage.TURN_SETUP);
                else {
                    fixturePose = true;
                    droneFixture = bodyHome.add(-2, 8, -7);
                    farHome = droneFixture.add(640, 0, 0);
                    advance(Stage.REMOTE_SETUP);
                }
            }
            case REMOTE_SETUP -> {
                if (!remote(client, state, DroneViewMode.FPV) || !settled(12)) return;
                operation = submit(client, server -> {
                    ServerLevel level = player(server).serverLevel();
                    int cx = BlockPos.containing(farHome).getX() >> 4, cz = BlockPos.containing(farHome).getZ() >> 4;
                    require(!level.getChunkSource().hasChunk(cx, cz), "Remote destination was already loaded before the route");
                    routeDestination = farHome;
                });
                advance(Stage.REMOTE_MOVE);
            }
            case REMOTE_MOVE -> {
                if (!operationDone() || !regionReady(client, state, farHome)) return;
                check("unprepared_region_640m_streams");
                operation = submit(client, server -> {
                    ServerLevel level = player(server).serverLevel();
                    bodyMarker = BlockPos.containing(bodyHome).offset(4, 0, 0);
                    farMarker = BlockPos.containing(farHome).offset(0, 0, 6);
                    block(level, bodyMarker, Blocks.GOLD_BLOCK.defaultBlockState());
                    block(level, farMarker, Blocks.EMERALD_BLOCK.defaultBlockState());
                    Chicken chicken = EntityType.CHICKEN.create(level);
                    require(chicken != null, "Could not create the native remote-region chicken");
                    chicken.setPersistenceRequired();
                    chicken.setNoGravity(true);
                    chicken.setPos(farHome.add(2, 0, 2));
                    require(level.addFreshEntity(chicken), "Could not spawn the native remote-region chicken");
                    chickenId = chicken.getUUID();
                });
                advance(Stage.REMOTE_REGION);
            }
            case REMOTE_REGION -> {
                if (!operationDone() || !bothMarkers(client, Blocks.EMERALD_BLOCK) || clientEntity(client, chickenId) == null || state.chickenTick() < 8) return;
                if (!terrainReady(true)) return;
                recordTerrain("far_fpv_native_terrain_rendered");
                firstChickenTick = state.chickenTick();
                check("dual_body_and_drone_block_updates");
                capture(client, "remote-640m-fpv");
                operation = submit(client, server -> { block(player(server).serverLevel(), farMarker, Blocks.DIAMOND_BLOCK.defaultBlockState()); routeDestination = farHome.add(16, 0, 0); });
                advance(Stage.REMOTE_CROSS_ONE);
            }
            case REMOTE_CROSS_ONE -> {
                if (!captureDone() || !operationDone() || !regionReady(client, state, farHome.add(16, 0, 0))) return;
                operation = submit(client, server -> routeDestination = farHome.add(32, 0, 0));
                advance(Stage.REMOTE_CROSS_TWO);
            }
            case REMOTE_CROSS_TWO -> {
                if (!operationDone() || !regionReady(client, state, farHome.add(32, 0, 0)) || !bothMarkers(client, Blocks.DIAMOND_BLOCK)
                        || clientEntity(client, chickenId) == null || state.chickenTick() < firstChickenTick + 16) return;
                check("remote_entity_ticks_and_two_chunk_crossings");
                click(GLFW.GLFW_KEY_B);
                advance(Stage.REMOTE_THIRD);
            }
            case REMOTE_THIRD -> {
                if (!remote(client, state, DroneViewMode.THIRD_PERSON) || !settled(12)) return;
                if (!terrainReady(true)) return;
                recordTerrain("far_third_person_native_terrain_rendered");
                require(client.gameRenderer.getMainCamera().isDetached(), "Far third-person camera was not detached");
                capture(client, "remote-third-person");
                click(GLFW.GLFW_KEY_B);
                advance(Stage.REMOTE_LOS);
            }
            case REMOTE_LOS -> {
                if (!captureDone() || !los(client, state) || !targetId.equals(state.sessionDrone()) || !settled(12)) return;
                if (!terrainReady(false)) return;
                recordTerrain("los_native_terrain_returns_to_body");
                require(client.player.position().distanceTo(bodyHome) < 0.30 && bothMarkers(client, Blocks.DIAMOND_BLOCK), "LOS failed to retain both distant regions and the original pilot");
                click(GLFW.GLFW_KEY_B);
                advance(Stage.REMOTE_REATTACH);
            }
            case REMOTE_REATTACH -> {
                if (!remote(client, state, DroneViewMode.FPV) || !settled(12)) return;
                check("far_three_view_cycle_same_uuid");
                operation = submit(client, server -> {
                    ServerPlayer player = player(server);
                    Husk husk = EntityType.HUSK.create(player.serverLevel());
                    require(husk != null, "Could not create a real Husk damage fixture");
                    husk.setPersistenceRequired();
                    husk.setPos(player.position().add(1.3, 0, 0));
                    husk.setTarget(player);
                    require(player.serverLevel().addFreshEntity(husk), "Could not spawn a real Husk damage fixture");
                    huskId = husk.getUUID();
                });
                advance(Stage.ATTACK);
            }
            case ATTACK -> {
                if (!operationDone() || !mobDamageObserved || state.health() >= 20 || !state.alive() || !settled(8)) return;
                require(remote(client, state, DroneViewMode.FPV), "Real nonfatal mob damage broke the native drone view");
                check("real_husk_melee_damages_remote_pilot");
                report.addProperty("pilot_health_after_native_melee", state.health());
                capture(client, "real-husk-damage-in-fpv");
                operation = submit(client, server -> {
                    discard(server, huskId); huskId = null;
                    for (BlockPos floor : platform) block(player(server).serverLevel(), floor, Blocks.AIR.defaultBlockState());
                });
                advance(Stage.FALL);
            }
            case FALL -> {
                if (!captureDone() || !operationDone() || !state.alive() || state.body().y > bodyHome.y - 2.5 || !settled(12)) return;
                require(remote(client, state, DroneViewMode.FPV), "Removing the physical floor ended the live player's remote view");
                check("physical_pilot_falls_in_remote_view");
                report.addProperty("physical_fall_meters", bodyHome.y - state.body().y);
                operation = submit(client, server -> { droneFixture = player(server).position().add(0, 4, 7); routeDestination = droneFixture; });
                advance(Stage.RETURN_NEAR);
            }
            case RETURN_NEAR -> {
                if (!operationDone() || !regionReady(client, state, droneFixture)) return;
                operation = submit(client, server -> equip(player(server), ItemStack.EMPTY, ItemStack.EMPTY));
                advance(Stage.EXPIRE_LEASE);
            }
            case EXPIRE_LEASE -> {
                if (!operationDone() || !los(client, state) || state.sessions() != 0 || state.tickets() != 0 || !settled(120)) return;
                require(!DroneClientState.isVirtualControllerEnabled(), "Lease expiry used virtual control");
                check("controller_loss_expires_lease_and_tickets");
                operation = submit(client, server -> equip(player(server), boundController.copy(), new ItemStack(Items.SHIELD)));
                advance(Stage.RECONNECT);
            }
            case RECONNECT -> {
                if (!operationDone() || !bound(client, state, targetId) || !settled(12)) return;
                if (DroneClientState.viewMode() == DroneViewMode.LINE_OF_SIGHT) { click(GLFW.GLFW_KEY_B); return; }
                if (!remote(client, state, DroneViewMode.FPV) || state.tickets() == 0) return;
                check("reconnect_before_native_disconnect");
                capture(client, "mounted-before-native-disconnect");
                advance(Stage.DISCONNECT);
            }
            default -> throw new IllegalStateException("Unsupported native client stage: " + stage);
        }
    }

    private void finishDisconnect(Minecraft client) {
        if (client.level != null) {
            if (!captureDone()) return;
            if (!disconnectRecorded) {
                require(snapshot != null && snapshot.remote() && snapshot.sessions() > 0 && snapshot.tickets() > 0,
                        "Final disconnect preparation did not observe an active mounted lease");
                report.addProperty("mounted_lease_observed_before_disconnect_cleanup", true);
                report.addProperty("disconnect_fixture_cleanup", "restore player inventory and remove disposable fixtures on the server before native connection close; lease expiry is independently observed earlier");
                disconnectRecorded = true;
            }
            releaseKeys(client);
            restoreCameraSettings();
            client.options.setCameraType(originalCameraType);
            client.options.bobView().set(originalBob);
            if (!fixtureRestored) {
                // Player removal can precede ServerStoppingEvent and saves that player's
                // inventory. Restore it while the real ServerPlayer is still connected.
                // The report separately records the observed mounted lease before cleanup.
                if (operation == null || operationDone()) operation = submit(client, this::restoreFixture);
                if (!operationDone()) return;
            }
            // Native connection close and integrated-server shutdown, including vanilla save.
            // PauseScreen.onDisconnect closes the level's network connection first.
            // Minecraft.disconnect only tears down the listener and waits for the
            // integrated server, so calling it alone leaves that server running.
            client.level.disconnect();
            client.disconnect(new TitleScreen());
            return;
        }
        if (!stoppedClean || !fixtureRestored || client.getSingleplayerServer() != null) return;
        require(DroneClientState.controlledDrone() == null && !DroneClientState.isDroneViewEnabled()
                && !DroneClientState.armed() && !DroneClientState.hasController(), "Native disconnect retained client drone controls or camera state");
        check("native_disconnect_clears_client_state");
        check("server_stop_releases_remote_resources");
        check("fixture_restored");
        advance(Stage.COMPLETE);
        finish(client, true, null);
    }

    public void serverTick(ServerTickEvent.Post event) {
        if (finished || ownerId == null || bodyHome == null) return;
        MinecraftServer server = event.getServer();
        ServerPlayer player = server.getPlayerList().getPlayer(ownerId);
        if (player == null) return;
        try {
            ServerLevel level = player.serverLevel();
            DroneEntity drone = targetId == null ? null : level.getEntity(targetId) instanceof DroneEntity current ? current : null;
            if (fixturePose && drone != null && droneFixture != null) {
                // Route advancement itself occurs at native DroneEntity.tick TAIL,
                // before the ordinary ServerTick.Post lease refresh. Static camera
                // fixtures are still held here after flight metadata is written.
                drone.setPos(droneFixture);
                drone.setDeltaMovement(Vec3.ZERO);
                Vec3 direction = player.position().add(0, 0.95, 0).subtract(droneFixture);
                drone.getEntityData().set(PITCH, (float) Math.atan2(-direction.y, Math.hypot(direction.x, direction.z)));
                drone.getEntityData().set(YAW, (float) Math.atan2(-direction.x, direction.z));
                drone.getEntityData().set(ROLL, 0f);
            }
            List<DroneRecord> records = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) if (entity instanceof DroneEntity current
                    && (targetId == null && current.position().distanceTo(bodyHome) < 12 || current.getUUID().equals(firstId) || current.getUUID().equals(targetId))) {
                records.add(new DroneRecord(current.getUUID(), current.getOwner(), current.position(), current.isArmed(), current.getControlYaw(), current.getAssemblyMask()));
            }
            if (stage == Stage.TURN_HOLD && drone != null && drone.isArmed() && drone.tickCount > previousTurnDroneTick) {
                validateTurn(drone);
            }
            Entity chicken = chickenId == null ? null : level.getEntity(chickenId);
            DroneEntity tracked = DroneRemoteSessions.trackedDrone(player);
            boolean loaded = drone != null && level.getChunkSource().hasChunk(drone.chunkPosition().x, drone.chunkPosition().z);
            boolean ticking = drone != null && level.getChunkSource().chunkMap.getDistanceManager().inEntityTickingRange(drone.chunkPosition().toLong());
            observeCnc(server, player, level);
            snapshot = new Snapshot(server.getTickCount(), player.position(), player.getHealth(), player.isAlive(), player.isShiftKeyDown(), player.getCamera() == player,
                    DroneControllerItem.boundDroneId(player), tracked == null ? null : tracked.getUUID(), DroneRemoteSessions.isRemoteView(player),
                    DroneRemoteSessions.sessionCount(server), DroneRemoteSessions.ticketCount(level), List.copyOf(records),
                    drone == null ? null : drone.position(), loaded, ticking, chicken == null ? -1 : chicken.tickCount);
        } catch (Throwable failure) { asynchronousFailure = "Server observation failed at " + stage + ": " + failure; }
    }

    /** Read-only observation of the actual block-entity ticker, container and native player's inventory. */
    private void observeCnc(MinecraftServer server, ServerPlayer player, ServerLevel level) {
        if (cncPosition == null) return;
        CncWorkstationBlockEntity machine = level.getBlockEntity(cncPosition) instanceof CncWorkstationBlockEntity found ? found : null;
        int tick = server.getTickCount();
        int input = machine == null ? 0 : machine.getItem(0).getCount();
        int output = machine == null ? 0 : machine.getItem(1).getCount();
        int progress = machine == null ? 0 : machine.getProgress();
        boolean inputPlate = machine != null && machine.getItem(0).is(DroneItems.carbonPlate());
        boolean outputFrame = machine != null && machine.getItem(1).is(DroneItems.carbonFrame());
        boolean menuOpen = player.containerMenu instanceof CncWorkstationMenu menu && menu.machine() == machine;
        if (progress > 0) {
            if (cncFirstProcessingTick < 0) cncFirstProcessingTick = tick - progress + 1;
            if (cncCompletedJobs == 0) require(inputPlate && input == 8 && output == 0
                    && progress == tick - cncFirstProcessingTick + 1, "Native CNC job reset, skipped progress, or consumed stock before its final tick");
            if (!menuOpen) cncProcessingTicksWithMenuClosed++;
        }
        if (outputFrame && output > 0 && cncPreviousOutputCount == 0) {
            cncCompletedJobs++;
            if (cncFirstCompletionTick < 0) cncFirstCompletionTick = tick;
        }
        if (outputFrame && output == 1 && inputPlate && input == 4 && progress == 0) cncOutputBlockedTicks++;
        cncPreviousOutputCount = output;
        CncRecord previous = cncRecord;
        cncRecord = new CncRecord(tick, machine != null, progress, machine == null ? 120 : machine.getProcessingTicks(),
                machine == null ? -1 : machine.getWorkpieceStage(), input, inputPlate, output, outputFrame,
                inventoryCount(player, DroneItems.carbonPlate()), inventoryCount(player, DroneItems.carbonFrame()), menuOpen,
                cncFirstProcessingTick, cncFirstCompletionTick, cncCompletedJobs, cncProcessingTicksWithMenuClosed, cncOutputBlockedTicks);
        if (previous == null || previous.present() != cncRecord.present() || previous.inputCount() != input
                || previous.outputCount() != output || previous.menuOpen() != menuOpen
                || previous.playerFrameCount() != cncRecord.playerFrameCount() || progress > 0 && progress % 10 == 0
                || cncOutputBlockedTicks == 125) {
            synchronized (cncObservations) { cncObservations.add(cncEvidence(cncRecord)); }
        }
    }

    private void validateTurn(DroneEntity drone) {
        require(drone.getFlightMode() == FlightMode.ACRO && !drone.onGround(), "Signed turn left airborne ACRO flight");
        Quaternion rendered = renderedAttitude(drone, 1, false);
        Quaternion model = drone.activeFlightModelAttitudeForDiagnostics();
        double error = basisError(rendered, model);
        maximumModelRenderError = Math.max(maximumModelRenderError, error);
        require(error < 0.0001, "Server entity render attitude differs from playable quaternion during " + TURN_NAMES[turnIndex] + ": " + error);
        double input = turnIndex < 2 ? drone.getControlPitch() : drone.getControlRoll();
        double sign = turnIndex % 2 == 0 ? 1 : -1;
        if (previousTurnAttitude != null && Math.abs(input) > 0.6) {
            Quaternion delta = previousTurnAttitude.conjugate().multiply(rendered).normalized();
            double component = (turnIndex < 2 ? delta.x() : delta.z()) * (delta.w() < 0 ? -1 : 1);
            require(component * sign >= -0.00001, "Held production control reversed its body frame after a full turn: " + component);
            signedTurnRadians += 2 * Math.asin(Math.min(1, Math.max(-1, component))) * sign;
            turnAdvances++;
        }
        previousTurnAttitude = rendered;
        previousTurnDroneTick = drone.tickCount;
    }

    public void renderLevel(RenderLevelStageEvent event) {
        if (finished || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        frames++;
        try { observeTerrain(event.getCamera()); }
        catch (Throwable failure) { asynchronousFailure = "Native terrain observation failed: " + failure; }
        if (stage != Stage.TURN_HOLD) return;
        Minecraft client = Minecraft.getInstance();
        DroneEntity drone = DroneClientState.controlledDrone();
        if (drone == null || !targetId.equals(drone.getUUID()) || !DroneClientState.isFpvActive(client.level)) return;
        try {
            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
            Quaternion reference = renderedAttitude(drone, partialTick, true);
            Camera camera = event.getCamera();
            double error = Math.max(vectorError(reference.rotate(new com.tenicana.dronecraft.sim.Vec3(0, 0, 1)), new Vec3(camera.getLookVector())),
                    vectorError(reference.rotate(new com.tenicana.dronecraft.sim.Vec3(0, 1, 0)), new Vec3(camera.getUpVector())));
            maximumCameraError = Math.max(maximumCameraError, error);
            require(error < 0.002, "Native FPV camera basis diverged across a signed full turn: " + error);
            require(Float.isFinite(camera.rotation().x) && Float.isFinite(camera.rotation().y)
                    && Float.isFinite(camera.rotation().z) && Float.isFinite(camera.rotation().w), "Native camera quaternion became nonfinite");
            cameraSamples++;
        } catch (Throwable failure) { asynchronousFailure = "Native render observation failed: " + failure; }
    }

    private void observeTerrain(Camera camera) {
        Minecraft client = Minecraft.getInstance();
        TerrainRenderProbeAccess probe = (TerrainRenderProbeAccess) client.levelRenderer;
        ViewArea area = probe.fpvValidation$viewArea();
        if (area == null || client.player == null) return;
        BlockPos cameraBlock = BlockPos.containing(camera.getPosition());
        int cx = cameraBlock.getX() >> 4, cz = cameraBlock.getZ() >> 4;
        boolean containsCamera = false;
        int compiledNearCamera = 0, visibleNearCamera = 0;
        for (SectionRenderDispatcher.RenderSection section : area.sections) {
            BlockPos origin = section.getOrigin();
            int dx = Math.abs((origin.getX() >> 4) - cx), dz = Math.abs((origin.getZ() >> 4) - cz);
            if (dx == 0 && dz == 0) containsCamera = true;
            if (dx <= 2 && dz <= 2 && renderable(section)) compiledNearCamera++;
        }
        for (SectionRenderDispatcher.RenderSection section : probe.fpvValidation$visibleSections()) {
            BlockPos origin = section.getOrigin();
            if (Math.abs((origin.getX() >> 4) - cx) <= 2 && Math.abs((origin.getZ() >> 4) - cz) <= 2 && renderable(section)) visibleNearCamera++;
        }
        terrain = new TerrainObservation(stage, camera.getPosition(), client.player.position(), containsCamera, compiledNearCamera, visibleNearCamera);
        if (containsCamera && compiledNearCamera > 0 && visibleNearCamera > 0) terrainFrames++;
        else terrainFrames = 0;
    }

    private static boolean renderable(SectionRenderDispatcher.RenderSection section) {
        SectionRenderDispatcher.CompiledSection compiled = section.getCompiled();
        return compiled != SectionRenderDispatcher.CompiledSection.UNCOMPILED && !compiled.hasNoRenderableLayers();
    }

    private boolean terrainReady(boolean remote) {
        return terrain != null && terrain.stage() == stage && terrainFrames >= 3 && terrain.gridContainsCamera()
                && terrain.compiledNearCamera() > 0 && terrain.visibleNearCamera() > 0
                && (remote ? terrain.camera().distanceTo(terrain.body()) > 600 : terrain.camera().distanceTo(terrain.body()) < 4);
    }

    private void recordTerrain(String name) {
        JsonObject evidence = new JsonObject();
        evidence.addProperty("check", name);
        evidence.addProperty("stage", stage.name());
        evidence.add("camera_position", vector(terrain.camera()));
        evidence.add("physical_player_position", vector(terrain.body()));
        evidence.addProperty("camera_body_distance_meters", terrain.camera().distanceTo(terrain.body()));
        evidence.addProperty("native_view_area_contains_camera_column", terrain.gridContainsCamera());
        evidence.addProperty("nonempty_compiled_sections_within_two_camera_chunks", terrain.compiledNearCamera());
        evidence.addProperty("nonempty_visible_sections_within_two_camera_chunks", terrain.visibleNearCamera());
        evidence.addProperty("consecutive_native_frames", terrainFrames);
        terrainObservations.add(evidence);
        check(name);
    }

    private record TerrainObservation(Stage stage, Vec3 camera, Vec3 body, boolean gridContainsCamera, int compiledNearCamera, int visibleNearCamera) {}

    public void renderPlayer(RenderPlayerEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (!finished && event.getEntity() == client.player && !event.getEntity().isInvisible()) bodyRenders++;
    }

    public static void onFirstPersonHands() {
        NativeClientWorldValidation current = active;
        if (current != null && !current.finished) current.handsCalls++;
    }

    public void damage(LivingDamageEvent.Post event) {
        if (ownerId != null && event.getEntity().getUUID().equals(ownerId) && event.getSource().getEntity() != null
                && event.getSource().getEntity().getUUID().equals(huskId) && event.getNewDamage() > 0) mobDamageObserved = true;
    }

    public static void onServerDroneTick(DroneEntity drone) {
        NativeClientWorldValidation current = active;
        if (current == null || current.finished || drone.level().isClientSide() || !current.fixturePose
                || current.routeDestination == null || !drone.getUUID().equals(current.targetId)) return;
        try { current.advanceRoute(drone); }
        catch (Throwable failure) { current.asynchronousFailure = "Native route fixture failed at " + current.stage + ": " + failure; }
    }

    private void advanceRoute(DroneEntity drone) {
        ServerLevel level = (ServerLevel) drone.level();
        require(level.getServer().isSameThread(), "Native route hook ran off server thread");
        Vec3 destination = routeDestination;
        Vec3 delta = destination.subtract(drone.position());
        Vec3 next = delta.length() <= 4 ? destination : drone.position().add(delta.normalize().scale(4));
        BlockPos nextBlock = BlockPos.containing(next);
        boolean nextLoaded = level.getChunkSource().hasChunk(nextBlock.getX() >> 4, nextBlock.getZ() >> 4);
        boolean nextEntityTicking = level.isPositionEntityTicking(nextBlock);
        boolean wait = !nextLoaded || !nextEntityTicking;
        if (wait) routeWaitTicks++;
        long chunk = drone.chunkPosition().toLong();
        if (chunk != previousRouteChunk || wait != previousRouteWait) {
            JsonObject point = new JsonObject();
            point.addProperty("stage", stage.name());
            point.addProperty("clock", "native_drone_tick_tail");
            point.addProperty("server_tick", level.getServer().getTickCount());
            point.addProperty("drone_entity_tick", drone.tickCount);
            point.add("position", vector(drone.position()));
            point.add("candidate_next_position", vector(next));
            point.addProperty("next_chunk_loaded", nextLoaded);
            point.addProperty("next_chunk_entity_ticking", nextEntityTicking);
            point.addProperty("waiting_for_native_lease_loading", wait);
            point.addProperty("session_count", DroneRemoteSessions.sessionCount(level.getServer()));
            point.addProperty("ticket_count", DroneRemoteSessions.ticketCount(level));
            synchronized (routeObservations) { routeObservations.add(point); }
            previousRouteChunk = chunk;
            previousRouteWait = wait;
        }
        if (wait) return;
        droneFixture = next;
        drone.setPos(next);
        drone.setDeltaMovement(Vec3.ZERO);
        if (delta.length() <= 4) {
            routeDestination = null;
            routeCompletedTick = level.getServer().getTickCount();
        }
    }

    public void entityLeave(EntityLeaveLevelEvent event) {
        if (finished || targetId == null || !targetId.equals(event.getEntity().getUUID()) || routeDestination == null) return;
        JsonObject removed = new JsonObject();
        removed.addProperty("stage", stage.name());
        removed.addProperty("event", "native_target_entity_leave");
        removed.addProperty("client_side", event.getLevel().isClientSide());
        removed.addProperty("entity_tick", event.getEntity().tickCount);
        removed.addProperty("removal_reason", String.valueOf(event.getEntity().getRemovalReason()));
        removed.add("position", vector(event.getEntity().position()));
        synchronized (routeObservations) { routeObservations.add(removed); }
        if (!event.getLevel().isClientSide()) asynchronousFailure = "Native target unloaded during paced route: " + removed;
    }

    public void serverStopping(ServerStoppingEvent event) {
        if (ownerId == null || fixtureRestored) return;
        try { restoreFixture(event.getServer()); }
        catch (Throwable failure) { asynchronousFailure = "Native shutdown fixture restoration failed: " + failure; }
    }

    public void serverStopped(ServerStoppedEvent event) {
        if (ownerId == null) return;
        MinecraftServer server = event.getServer();
        stoppedSessions = DroneRemoteSessions.sessionCount(server);
        stoppedTickets = 0;
        for (ServerLevel level : server.getAllLevels()) stoppedTickets += DroneRemoteSessions.ticketCount(level);
        stoppedClean = stoppedSessions == 0 && stoppedTickets == 0;
    }

    private void restoreFixture(MinecraftServer server) {
        fixturePose = false;
        routeDestination = null;
        ServerPlayer player = server.getPlayerList().getPlayer(ownerId);
        if (stage == Stage.DISCONNECT) require(player != null, "Player was removed before native fixture inventory restoration");
        ServerLevel level = player == null ? server.overworld() : player.serverLevel();
        discard(server, targetId); discard(server, firstId); discard(server, chickenId); discard(server, huskId);
        // Drain only the disposable fixture before native block removal can drop its remaining stock.
        if (cncPosition != null && level.getBlockEntity(cncPosition) instanceof CncWorkstationBlockEntity machine) machine.clearContent();
        originalBlocks.forEach((position, block) -> level.setBlock(position, block, 3));
        if (player != null) {
            for (int i = 0; i < originalInventory.size(); i++) player.getInventory().setItem(i, originalInventory.get(i).copy());
            player.getInventory().selected = originalSelectedSlot;
            player.setGameMode(originalGameType);
            player.setHealth(originalHealth);
            player.setNoGravity(originalNoGravity);
            player.getAbilities().flying = originalFlying;
            player.onUpdateAbilities();
            player.getFoodData().setFoodLevel(originalFood);
            player.getFoodData().setSaturation(originalSaturation);
            player.setDeltaMovement(Vec3.ZERO);
            player.connection.teleport(originalPlayerPosition.x, originalPlayerPosition.y, originalPlayerPosition.z, 0, 0);
            player.inventoryMenu.broadcastChanges();
        }
        if (originalDifficulty != null) server.setDifficulty(originalDifficulty, originalDifficultyLocked);
        DroneDebugSettings.setFlightModelMode(originalModel);
        fixtureRestored = true;
    }

    private void visibleBody(Minecraft client, Snapshot state) {
        require(bodyRenders >= 4 && frames >= 4 && handsCalls == 0, "Remote view did not render the actual pilot body with first-person hands hidden: body=" + bodyRenders + ", frames=" + frames + ", hands=" + handsCalls);
        require(state.body().distanceTo(bodyHome) < 0.30 && client.player.position().distanceTo(bodyHome) < 0.30,
                "Body moved during native visible-body validation");
        require(client.player.getMainHandItem().is(DroneItems.droneController()) && client.player.getOffhandItem().is(Items.SHIELD), "Visible physical player lost its real held items");
        Vec3 toBody = client.player.position().add(0, 0.95, 0).subtract(client.gameRenderer.getMainCamera().getPosition()).normalize();
        require(toBody.dot(new Vec3(client.gameRenderer.getMainCamera().getLookVector())) > 0.75, "Mounted camera was not looking at the actual pilot body");
    }

    private boolean renderWindow() {
        if (!windowStarted) { frames = bodyRenders = handsCalls = 0; windowStarted = true; return false; }
        return frames >= 8;
    }

    private boolean regionReady(Minecraft client, Snapshot state, Vec3 expected) {
        DroneEntity drone = DroneClientState.controlledDrone();
        return routeDestination == null && state.tick() - routeCompletedTick >= 8 && state.loaded() && state.ticking()
                && state.dronePosition() != null && state.dronePosition().distanceTo(expected) < 0.6 && drone != null
                && drone.position().distanceTo(expected) < 1 && targetId.equals(drone.getUUID())
                && client.level.hasChunk(drone.chunkPosition().x, drone.chunkPosition().z)
                && client.level.hasChunk(client.player.chunkPosition().x, client.player.chunkPosition().z);
    }

    private boolean bothMarkers(Minecraft client, net.minecraft.world.level.block.Block distantBlock) {
        return bodyMarker != null && farMarker != null && client.level.getBlockState(bodyMarker).is(Blocks.GOLD_BLOCK)
                && client.level.getBlockState(farMarker).is(distantBlock);
    }

    private void capture(Minecraft client, String name) {
        require(capture == null, "Screenshot observation was overwritten before validation");
        Path directory = reportPath.getParent();
        Path path = directory.resolve("screenshots").resolve(name + ".png");
        try { Files.createDirectories(path.getParent()); }
        catch (IOException failure) { throw new IllegalStateException("Cannot create native screenshot evidence directory", failure); }
        JsonObject evidence = new JsonObject();
        evidence.addProperty("stage", stage.name());
        evidence.addProperty("view", DroneClientState.viewMode().name());
        evidence.addProperty("file", path.toString());
        evidence.addProperty("native_player_render_post_calls", bodyRenders);
        evidence.addProperty("native_first_person_hands_calls", handsCalls);
        evidence.addProperty("native_world_frames", frames);
        evidence.addProperty("server_camera_is_player", snapshot.nativeCameraSelf());
        evidence.addProperty("session_count", snapshot.sessions());
        evidence.addProperty("ticket_count", snapshot.tickets());
        evidence.addProperty("remote_chicken_entity_tick", snapshot.chickenTick());
        evidence.addProperty("drone_chunk_loaded", snapshot.loaded());
        evidence.addProperty("drone_chunk_entity_ticking", snapshot.ticking());
        evidence.addProperty("body_displacement_meters", snapshot.body().distanceTo(bodyHome));
        if (cncRecord != null && name.startsWith("cnc-")) evidence.add("cnc_server", cncEvidence(cncRecord));
        if (name.startsWith("cnc-") && client.level.getBlockEntity(cncPosition) instanceof CncWorkstationBlockEntity machine) {
            JsonObject nativeMachine = new JsonObject();
            nativeMachine.addProperty("progress", machine.getProgress());
            nativeMachine.addProperty("workpiece_stage", machine.getWorkpieceStage());
            nativeMachine.addProperty("input_count", machine.getItem(0).getCount());
            nativeMachine.addProperty("output_count", machine.getItem(1).getCount());
            evidence.add("cnc_native_client_block_entity", nativeMachine);
        }
        CompletableFuture<String> saved = new CompletableFuture<>();
        int width = client.getMainRenderTarget().width, height = client.getMainRenderTarget().height;
        capture = saved.orTimeout(20, TimeUnit.SECONDS).thenApplyAsync(message -> {
            try {
                byte[] data = Files.readAllBytes(path);
                require(data.length > 24 && ByteBuffer.wrap(data).getLong() == 0x89504e470d0a1a0aL, "Native screenshot is not a PNG: " + path);
                require(ByteBuffer.wrap(data, 16, 4).getInt() == width && ByteBuffer.wrap(data, 20, 4).getInt() == height, "Native screenshot dimensions differ from render target");
                evidence.addProperty("png_verified", true);
                return evidence;
            } catch (IOException failure) { throw new IllegalStateException("Could not validate native screenshot", failure); }
        });
        Screenshot.grab(directory.toFile(), path.getFileName().toString(), client.getMainRenderTarget(), message -> saved.complete(message.getString()));
    }

    private boolean captureDone() {
        if (capture == null) return true;
        if (!capture.isDone()) return false;
        screenshots.add(capture.join());
        capture = null;
        return true;
    }

    private void finish(Minecraft client, boolean passed, Throwable failure) {
        finished = true;
        active = null;
        releaseKeys(client);
        restoreCameraSettings();
        if (originalCameraType != null) client.options.setCameraType(originalCameraType);
        if (focusPreferenceCaptured) client.options.pauseOnLostFocus = originalPauseOnLostFocus;
        if (clientSettingsCaptured) client.options.bobView().set(originalBob);
        if (clientSettingsCaptured && DroneClientControls.config().gamepadEnabled() != originalGamepad) {
            DroneClientControls.config().setGamepadEnabled(originalGamepad);
            DroneClientControls.config().save();
        }
        if (!fixtureRestored && client.getSingleplayerServer() != null) {
            operation = submit(client, this::restoreFixture);
        }
        report.addProperty("final_stage", stage.name());
        report.addProperty("elapsed_milliseconds", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
        report.addProperty("maximum_undamaged_body_displacement_meters", maximumBodyDrift);
        report.addProperty("route_wait_ticks_for_native_lease_loading", routeWaitTicks);
        if (failure != null) report.addProperty("failure", failure.toString());
        if (failure != null) FpvDronecraftMod.LOGGER.error("Native client world validation failed at " + stage, failure);
        boolean requiredPassed = true;
        for (String name : REQUIRED) requiredPassed &= checks.has(name) && checks.get(name).getAsBoolean();
        report.addProperty("all_required_stages_observed", requiredPassed);
        report.addProperty("passed", passed && requiredPassed && stage == Stage.COMPLETE);
        try {
            Files.createDirectories(reportPath.getParent());
            Path temporary = reportPath.resolveSibling(reportPath.getFileName() + ".tmp");
            String json;
            synchronized (routeObservations) {
                synchronized (cncObservations) { json = new GsonBuilder().setPrettyPrinting().create().toJson(report); }
            }
            Files.writeString(temporary, json + System.lineSeparator(), StandardCharsets.UTF_8);
            Files.move(temporary, reportPath, StandardCopyOption.REPLACE_EXISTING);
            FpvDronecraftMod.LOGGER.info("Native client world validation {}: {}", report.get("passed"), reportPath);
        } catch (IOException writeFailure) { FpvDronecraftMod.LOGGER.error("Could not write native client world validation report", writeFailure); }
        // This class is constructed only for the explicit validation launch flag.
        if (Boolean.getBoolean("fpvdrone.client_validation")) client.stop();
    }

    private void advance(Stage next) {
        JsonObject item = new JsonObject();
        item.addProperty("completed_stage", stage.name());
        item.addProperty("server_tick", snapshot == null ? -1 : snapshot.tick());
        item.addProperty("elapsed_milliseconds", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stageStarted));
        stages.add(item);
        stage = next;
        FpvDronecraftMod.LOGGER.info("Native client world validation stage: {} (run {})", next, runId);
        stageTick = snapshot == null ? 0 : snapshot.tick();
        stageStarted = System.nanoTime();
        windowStarted = false;
        frames = bodyRenders = handsCalls = 0;
        terrainFrames = 0;
    }

    private void block(ServerLevel level, BlockPos position, BlockState value) {
        originalBlocks.putIfAbsent(position.immutable(), level.getBlockState(position));
        level.setBlock(position, value, 3);
    }
    private void cameraSetting(String name, float value) {
        try {
            Field field = DroneClientConfig.class.getDeclaredField(name);
            field.setAccessible(true);
            originalCameraSettings.putIfAbsent(name, field.getFloat(DroneClientControls.config()));
            field.setFloat(DroneClientControls.config(), value);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot configure deterministic native camera fixture", failure); }
    }
    private void restoreCameraSettings() {
        originalCameraSettings.forEach((name, value) -> {
            try { Field field = DroneClientConfig.class.getDeclaredField(name); field.setAccessible(true); field.setFloat(DroneClientControls.config(), value); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot restore native camera fixture", failure); }
        });
    }
    private static Quaternion renderedAttitude(DroneEntity drone, float partialTick, boolean interpolate) {
        double yaw = interpolate ? drone.getInterpolatedRenderYawRadians(partialTick) : drone.getRenderYawRadians();
        double pitch = interpolate ? drone.getInterpolatedRenderPitchRadians(partialTick) : drone.getRenderPitchRadians();
        double roll = interpolate ? drone.getInterpolatedRenderRollRadians(partialTick) : drone.getRenderRollRadians();
        return axis(0, 1, 0, -yaw).multiply(axis(1, 0, 0, pitch)).multiply(axis(0, 0, 1, roll)).normalized();
    }
    private static Quaternion axis(double x, double y, double z, double radians) {
        double sine = Math.sin(radians / 2);
        return new Quaternion(Math.cos(radians / 2), x * sine, y * sine, z * sine);
    }
    private static double basisError(Quaternion first, Quaternion second) {
        com.tenicana.dronecraft.sim.Vec3 forward = new com.tenicana.dronecraft.sim.Vec3(0, 0, 1);
        com.tenicana.dronecraft.sim.Vec3 up = new com.tenicana.dronecraft.sim.Vec3(0, 1, 0);
        return Math.max(vectorError(first.rotate(forward), mc(second.rotate(forward))), vectorError(first.rotate(up), mc(second.rotate(up))));
    }
    private static Vec3 mc(com.tenicana.dronecraft.sim.Vec3 value) { return new Vec3(value.x(), value.y(), value.z()); }
    private static double vectorError(com.tenicana.dronecraft.sim.Vec3 expected, Vec3 actual) { return mc(expected).distanceTo(actual); }
    private static void place(Minecraft client, BlockPos floor) {
        Vec3 point = Vec3.atCenterOf(floor).add(0, 0.5, 0);
        lookAt(client, point);
        client.gameRenderer.pick(1);
        require(client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(floor) && hit.getDirection() == Direction.UP,
                "Native placement raycast missed the supported target block: " + client.hitResult);
        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, (BlockHitResult) client.hitResult);
    }
    private DroneAssemblyMenu assemblyMenu(Minecraft client) {
        require(client.screen instanceof DroneAssemblyScreen && client.player.containerMenu instanceof DroneAssemblyMenu,
                "Native registered assembly screen/menu is missing at " + stage);
        DroneAssemblyMenu menu = (DroneAssemblyMenu) client.player.containerMenu;
        require(menu.drone() != null && targetId.equals(menu.drone().getUUID()), "Assembly menu is attached to the wrong aircraft");
        return menu;
    }
    private void openCnc(Minecraft client) {
        require(client.player.getMainHandItem().isEmpty() && !client.player.isShiftKeyDown(), "Native CNC opening needs an empty, unshifted hand");
        lookAt(client, Vec3.atCenterOf(cncPosition));
        client.gameRenderer.pick(1);
        require(client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(cncPosition),
                "Native empty-hand raycast missed the placed CNC: " + client.hitResult);
        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, (BlockHitResult) client.hitResult);
    }
    private CncWorkstationMenu cncMenu(Minecraft client) {
        require(client.screen instanceof CncWorkstationScreen && client.player.containerMenu instanceof CncWorkstationMenu,
                "Native registered CNC screen/menu is missing at " + stage);
        CncWorkstationMenu menu = (CncWorkstationMenu) client.player.containerMenu;
        require(menu.machine() != null && menu.machine().getBlockPos().equals(cncPosition), "Native CNC menu is attached to the wrong placed machine");
        return menu;
    }
    private static int installed(DroneAssemblyMenu menu) {
        int result = 0;
        for (int index = 0; index < DroneAssemblyInventory.SLOT_COUNT; index++) if (menu.slots.get(index).hasItem()) result++;
        return result;
    }
    private static void transfer(Minecraft client, DroneAssemblyMenu menu, int slotIndex) {
        client.gameMode.handleInventoryMouseClick(menu.containerId, slotIndex, 0, ClickType.QUICK_MOVE, client.player);
    }
    private void interact(Minecraft client, UUID id, boolean shifted) {
        Entity entity = clientEntity(client, id);
        require(entity instanceof DroneEntity && client.player.isShiftKeyDown() == shifted, "Native entity interaction has the wrong target or Shift state");
        lookAt(client, entity.getBoundingBox().getCenter());
        client.gameRenderer.pick(1);
        require(client.hitResult instanceof EntityHitResult hit && hit.getEntity() == entity, "Native entity raycast missed the intended exact drone UUID");
        InteractionResult result = client.gameMode.interactAt(client.player, entity, (EntityHitResult) client.hitResult, InteractionHand.MAIN_HAND);
        if (!result.consumesAction()) client.gameMode.interact(client.player, entity, InteractionHand.MAIN_HAND);
    }
    private static void lookAt(Minecraft client, Vec3 point) {
        Vec3 delta = point.subtract(client.player.getEyePosition());
        client.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
    }
    private static Entity clientEntity(Minecraft client, UUID id) {
        if (id == null) return null;
        for (Entity entity : client.level.entitiesForRendering()) if (entity.getUUID().equals(id)) return entity;
        return null;
    }
    private boolean bound(Minecraft client, Snapshot state, UUID id) {
        return id.equals(state.binding()) && id.equals(DroneControllerItem.boundDroneId(client.player))
                && DroneClientState.controlledDrone() != null && id.equals(DroneClientState.controlledDrone().getUUID());
    }
    private boolean remote(Minecraft client, Snapshot state, DroneViewMode view) {
        return bound(client, state, targetId) && DroneClientState.viewMode() == view && DroneClientState.isDroneCameraActive(client.level)
                && client.getCameraEntity() == DroneClientState.controlledDrone() && targetId.equals(state.sessionDrone()) && state.remote();
    }
    private static boolean los(Minecraft client, Snapshot state) { return !state.remote() && DroneClientState.viewMode() == DroneViewMode.LINE_OF_SIGHT && client.getCameraEntity() == client.player; }
    private static boolean held(Minecraft client, net.minecraft.world.item.Item item, int count) { return client.player.getMainHandItem().is(item) && client.player.getMainHandItem().getCount() == count; }
    private static void equip(ServerPlayer player, ItemStack main, ItemStack off) { player.setItemInHand(InteractionHand.MAIN_HAND, main); player.setItemInHand(InteractionHand.OFF_HAND, off); player.inventoryMenu.broadcastChanges(); }
    private static DroneRecord record(Snapshot state, UUID id) { return state.drones().stream().filter(drone -> id.equals(drone.id())).findFirst().orElseThrow(() -> new IllegalStateException("Server lost the native placed drone " + id)); }
    private ServerPlayer player(MinecraftServer server) { ServerPlayer result = server.getPlayerList().getPlayer(ownerId); require(result != null, "Integrated server lost the physical validation player"); return result; }
    private DroneEntity drone(MinecraftServer server) { Entity entity = player(server).serverLevel().getEntity(targetId); require(entity instanceof DroneEntity, "Native target drone disappeared"); return (DroneEntity) entity; }
    private static void discard(MinecraftServer server, UUID id) { if (id == null) return; for (ServerLevel level : server.getAllLevels()) { Entity entity = level.getEntity(id); if (entity != null) entity.discard(); } }
    private static CompletableFuture<Boolean> submit(Minecraft client, Consumer<MinecraftServer> action) {
        MinecraftServer server = client.getSingleplayerServer();
        require(server != null, "Native integrated server is unavailable");
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        server.execute(() -> { try { require(server.isSameThread(), "World fixture operation ran off the native server thread"); action.accept(server); result.complete(true); } catch (Throwable failure) { result.completeExceptionally(failure); } });
        return result;
    }
    private boolean operationDone() { if (operation == null || !operation.isDone()) return false; operation.join(); return true; }
    private boolean settled(int ticks) { return snapshot != null && snapshot.tick() - stageTick >= ticks; }
    private void check(String name) {
        checks.addProperty(name, true);
        JsonObject observation = new JsonObject();
        observation.addProperty("check", name);
        observation.addProperty("stage", stage.name());
        Minecraft client = Minecraft.getInstance();
        observation.addProperty("client_view", DroneClientState.viewMode().name());
        observation.addProperty("native_world_frames", frames);
        observation.addProperty("actual_player_renderer_calls", bodyRenders);
        observation.addProperty("native_hands_calls", handsCalls);
        if (snapshot != null) {
            observation.addProperty("server_tick", snapshot.tick());
            observation.addProperty("server_camera_is_physical_player", snapshot.nativeCameraSelf());
            observation.addProperty("server_remote_view", snapshot.remote());
            observation.addProperty("session_count", snapshot.sessions());
            observation.addProperty("ticket_count", snapshot.tickets());
            observation.addProperty("remote_chicken_entity_tick", snapshot.chickenTick());
            observation.addProperty("drone_chunk_loaded", snapshot.loaded());
            observation.addProperty("drone_chunk_entity_ticking", snapshot.ticking());
            observation.addProperty("server_bound_uuid", snapshot.binding() == null ? null : snapshot.binding().toString());
            observation.addProperty("server_session_uuid", snapshot.sessionDrone() == null ? null : snapshot.sessionDrone().toString());
            observation.add("physical_player_position", vector(snapshot.body()));
            if (snapshot.dronePosition() != null) observation.add("server_drone_position", vector(snapshot.dronePosition()));
        }
        if (cncRecord != null && (name.startsWith("native_cnc") || name.equals("native_empty_hand_cnc_menu")))
            observation.add("cnc_server", cncEvidence(cncRecord));
        if (client.player != null) {
            observation.add("client_physical_player_position", vector(client.player.position()));
            observation.addProperty("client_camera_is_player", client.getCameraEntity() == client.player);
            observation.addProperty("client_bound_uuid", DroneControllerItem.boundDroneId(client.player) == null ? null : DroneControllerItem.boundDroneId(client.player).toString());
        }
        if (name.equals("server_stop_releases_remote_resources")) {
            observation.addProperty("native_server_stopped_and_zero_remote_resources", stoppedClean);
            observation.addProperty("session_count", stoppedSessions);
            observation.addProperty("ticket_count", stoppedTickets);
        }
        observations.add(observation);
    }
    private static JsonArray vector(Vec3 value) { JsonArray result = new JsonArray(); result.add(value.x); result.add(value.y); result.add(value.z); return result; }
    private static int inventoryCount(net.minecraft.world.entity.player.Player player, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int index = 0; index < player.getInventory().getContainerSize(); index++) {
            ItemStack stack = player.getInventory().getItem(index);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }
    private static JsonObject cncEvidence(CncRecord cnc) {
        JsonObject result = new JsonObject();
        result.addProperty("server_tick", cnc.tick());
        result.addProperty("native_block_entity_present", cnc.present());
        result.addProperty("progress", cnc.progress());
        result.addProperty("processing_ticks", cnc.processingTicks());
        result.addProperty("workpiece_stage", cnc.workpieceStage());
        result.addProperty("input_count", cnc.inputCount());
        result.addProperty("input_is_carbon_plate", cnc.inputPlate());
        result.addProperty("output_count", cnc.outputCount());
        result.addProperty("output_is_existing_carbon_frame", cnc.outputFrame());
        result.addProperty("actual_player_plate_count", cnc.playerPlateCount());
        result.addProperty("actual_player_frame_count", cnc.playerFrameCount());
        result.addProperty("native_server_cnc_menu_open", cnc.menuOpen());
        result.addProperty("first_processing_server_tick", cnc.firstProcessingTick());
        result.addProperty("first_completion_server_tick", cnc.firstCompletionTick());
        result.addProperty("observed_processing_duration_ticks", cnc.firstCompletionTick() < 0 || cnc.firstProcessingTick() < 0
                ? -1 : cnc.firstCompletionTick() - cnc.firstProcessingTick() + 1);
        result.addProperty("completed_jobs", cnc.completedJobs());
        result.addProperty("processing_ticks_with_menu_closed", cnc.processingTicksWithMenuClosed());
        result.addProperty("full_output_pause_ticks", cnc.outputBlockedTicks());
        return result;
    }
    private static void click(int key) { KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(key)); }
    private static void key(int key, boolean down) { KeyMapping.set(InputConstants.Type.KEYSYM.getOrCreate(key), down); }
    private static void releaseKeys(Minecraft client) {
        for (int key : TURN_KEYS) key(key, false);
        key(GLFW.GLFW_KEY_Z, false); key(GLFW.GLFW_KEY_PAGE_UP, false); key(GLFW.GLFW_KEY_PAGE_DOWN, false);
        client.options.keyShift.setDown(false);
    }
    @SuppressWarnings("unchecked")
    private static EntityDataAccessor<Float> accessor(String name) {
        try { Field field = DroneEntity.class.getDeclaredField(name); field.setAccessible(true); return (EntityDataAccessor<Float>) field.get(null); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Native pose fixture accessor is unavailable", failure); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private record DroneRecord(UUID id, UUID owner, Vec3 position, boolean armed, float yawInput, int assemblyMask) { }
    private record CncRecord(int tick, boolean present, int progress, int processingTicks, int workpieceStage,
            int inputCount, boolean inputPlate, int outputCount, boolean outputFrame, int playerPlateCount,
            int playerFrameCount, boolean menuOpen, int firstProcessingTick, int firstCompletionTick,
            int completedJobs, int processingTicksWithMenuClosed, int outputBlockedTicks) { }
    private record Snapshot(int tick, Vec3 body, float health, boolean alive, boolean shift, boolean nativeCameraSelf,
            UUID binding, UUID sessionDrone, boolean remote, int sessions, int tickets, List<DroneRecord> drones,
            Vec3 dronePosition, boolean loaded, boolean ticking, int chickenTick) { }
}
