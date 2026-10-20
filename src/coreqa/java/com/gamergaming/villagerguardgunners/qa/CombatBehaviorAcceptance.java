package com.gamergaming.villagerguardgunners.qa;

import com.gamergaming.villagerguardgunners.ai.GuardAutomaticDutyMovementGoal;
import com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal;
import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.combat.aim.GuardAimPoint;
import com.gamergaming.villagerguardgunners.defense.duty.GuardPatrolRouteMovementController;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.init.ModItems;
import com.gamergaming.villagerguardgunners.inventory.ItemCustomData;
import com.gamergaming.villagerguardgunners.orders.*;
import com.gamergaming.villagerguardgunners.sidearm.*;
import com.gamergaming.villagerguardgunners.squad.*;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.EntityKineticBullet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.common.entities.Guard;

/** Server-side behavioral acceptance using the complete production AI and published mod JARs. */
final class CombatBehaviorAcceptance {
    private static final Path EXPECTED = Path.of("behavior-qa-expected.nbt");
    private static final BlockPos ORIGIN = new BlockPos(0, 160, 0);
    private static final String[] CASES = {
        "tactical_dry", "tactical_reload", "shield_fire", "friendly_lane",
        "event_hold_fire", "burst_hold_fire", "aim_mob_zero", "aim_mob_error", "aim_player_override",
        "sidearm_return", "explosive_sidearm_return", "squad_rally", "squad_fallback", "squad_defend", "squad_focus",
        "patrol_resume", "patrol_obstacle"
    };
    private ServerLevel level;
    private Guard guard;
    private LivingEntity target;
    private Mob ally;
    private FakePlayer commander;
    private ItemStack ledger = ItemStack.EMPTY;
    private final List<Entity> entities = new ArrayList<>();
    private final List<UUID> squads = new ArrayList<>();
    private final List<BlockPos> obstacles = new ArrayList<>();
    private int index = -1, ticks, bullets, events, initialRounds, changedAt = -1;
    private boolean done, restoring, reload, activeSidearm, restoredSidearm, visitedSecond, resumed, reachedCommand;
    private double initialDistance, minimumDistance, maximumTravel, maximumAimError;
    private Vec3 heldPosition;
    private CompoundTag expected;
    private ItemStack primaryBefore;

    CombatBehaviorAcceptance() {
        NeoForge.EVENT_BUS.addListener(this::started);
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::joined);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, this::fire);
    }

    private String name() { return index >= 0 && index < CASES.length ? CASES[index] : "persistence"; }

    private void started(ServerStartedEvent event) {
        level = event.getServer().overworld();
        try {
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) level.setChunkForced(x, z, true);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, event.getServer());
            level.setDayTime(6000);
            if (Files.exists(EXPECTED)) {
                expected = NbtIo.readCompressed(EXPECTED, NbtAccounter.create(1_048_576));
                restoring = true;
                return;
            }
            for (int x = -24; x <= 28; x++) for (int z = -20; z <= 20; z++) {
                level.setBlockAndUpdate(ORIGIN.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                for (int y = 0; y < 4; y++) level.setBlockAndUpdate(ORIGIN.offset(x, y, z), Blocks.AIR.defaultBlockState());
            }
            commander = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "VGG commander"));
            commander.setGameMode(GameType.SURVIVAL);
            ConfigPersistenceAcceptance.run();
            for (String role : List.of("rifleman", "breacher", "demolitions", "support", "marksman", "sentry", "medic")) {
                require(com.gamergaming.villagerguardgunners.role.GuardRoles.get(
                        ResourceLocation.fromNamespaceAndPath("villagerguardgunners", role)) != null, "Role did not load: " + role);
            }
            require(EntityType.SNOW_GOLEM.is(GuardAllyProtection.GUARD_ALLIES), "Built-in ally tag did not load");
            var entityTypes = level.registryAccess().registryOrThrow(Registries.ENTITY_TYPE);
            require(entityTypes.getTag(com.gamergaming.villagerguardgunners.combat.engagement.GuardRulesOfEngagement.ALWAYS_ENGAGE).isPresent()
                    && entityTypes.getTag(com.gamergaming.villagerguardgunners.combat.engagement.GuardRulesOfEngagement.NEVER_ENGAGE).isPresent(),
                    "Engagement tags did not load");
            for (boolean squad : List.of(false, true)) {
                var ingredients = new ArrayList<ItemStack>();
                ingredients.add(new ItemStack(Items.WRITABLE_BOOK)); ingredients.add(new ItemStack(Items.EMERALD));
                if (squad) ingredients.add(new ItemStack(Items.GOAT_HORN));
                var input = CraftingInput.of(ingredients.size(), 1, ingredients);
                var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
                require(recipe.value().assemble(input, level.registryAccess()).is(squad ? ModItems.SQUAD_COMMAND_LEDGER.get() : ModItems.ORDERS_LEDGER.get()),
                        "Ledger cannot be crafted from its documented ingredients");
            }
            String startCase = System.getProperty("vgg.qa.startCase");
            if (startCase != null) {
                int start = List.of(CASES).indexOf(startCase);
                require(start >= 0, "Unknown starting case: " + startCase);
                index = start - 1;
            }
            next();
        } catch (Throwable failure) { fail(failure); }
    }

    private void next() throws Exception {
        cleanup();
        index++;
        ticks = bullets = events = 0; changedAt = -1;
        reload = activeSidearm = restoredSidearm = visitedSecond = resumed = reachedCommand = false;
        maximumTravel = maximumAimError = 0;
        var aiming = ModConfigs.GUARD_GUNNERS.combat.aiming;
        aiming.errorDegrees.accept(0.0); aiming.overridePlayers.accept(false);
        aiming.aimPoint.accept(GuardAimPoint.EYE_LEVEL);
        if (index == CASES.length) { preparePersistence(); return; }
        boolean dry = name().equals("tactical_dry") || name().equals("tactical_reload") || name().equals("sidearm_return");
        boolean burst = name().contains("hold_fire");
        boolean explosive = name().equals("explosive_sidearm_return");
        guard = newGuard(explosive ? "rpg7" : burst ? "m16a4" : "ak47",
                explosive ? FireMode.SEMI : burst ? FireMode.BURST : FireMode.AUTO, dry ? 0 : explosive ? 1 : 30);
        boolean movement = name().startsWith("tactical_") || name().startsWith("squad_") || name().startsWith("patrol_");
        orders(movement ? GuardOrders.MovementOrder.TACTICAL : GuardOrders.MovementOrder.HOLD_POSITION,
                GuardOrders.EngagementOrder.GUARD_TARGETS);
        if (name().startsWith("patrol_")) { preparePatrol(); return; }
        if (name().startsWith("squad_")) { prepareCommand(); return; }
        target = enemy(explosive ? 3 : name().startsWith("tactical_") ? 10 : 16, 0);
        if (name().startsWith("tactical_")) { enemy(10, -2); enemy(10, 2); }
        if (name().equals("tactical_reload")) {
            GuardAmmoAccess.getAmmoHandler(guard).setStackInSlot(0, AmmoItemBuilder.create()
                    .setId(ResourceLocation.parse("tacz:762x39")).setCount(64).build());
        }
        if (name().equals("shield_fire")) guard.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        if (name().equals("friendly_lane")) {
            ally = EntityType.VILLAGER.create(level);
            ally.setNoAi(true); ally.setPersistenceRequired();
            ally.moveTo(8.5, 160, .5); add(ally);
        }
        if (name().startsWith("aim_")) {
            aiming.overridePlayers.accept(true);
            aiming.errorDegrees.accept(name().equals("aim_mob_error") ? 6.0 : 0.0);
            aiming.playerErrorDegrees.accept(6.0);
            aiming.playerAimPoint.accept(GuardAimPoint.CENTER_MASS);
            if (name().equals("aim_player_override")) {
                target.discard();
                // A server-side player exercises the actual target-type policy. Network clients
                // and visual synchronization are a separate installed-client acceptance pass.
                FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "VGG target"));
                player.setGameMode(GameType.SURVIVAL);
                player.setPos(16.5, 160, .5); target = player;
            }
        }
        if (name().endsWith("sidearm_return")) {
            ItemCustomData.update(guard.getMainHandItem(), tag -> tag.putString("qa_primary", "preserve-me"));
            primaryBefore = guard.getMainHandItem().copy();
            require(GuardSidearmTransactions.configureNaturalSidearm(guard, gun("glock_17", FireMode.SEMI, 16)).configured(),
                    "Sidearm configuration failed");
        }
        guard.setTarget(target);
        initialDistance = minimumDistance = guard.distanceTo(target);
        initialRounds = rounds();
        LogUtils.getLogger().info("VGG_BEHAVIOR_QA_BEGIN case={}", name());
    }

    private void tick(ServerTickEvent.Post event) {
        if (level == null || done) return;
        try {
            ticks++;
            if (restoring) { checkRestart(); return; }
            maximumTravel = Math.max(maximumTravel, guard.position().distanceTo(Vec3.atBottomCenterOf(ORIGIN)));
            if (target != null) minimumDistance = Math.min(minimumDistance, guard.distanceTo(target));
            reload |= IGunOperator.fromLivingEntity(guard).getSynReloadState().getStateType().isReloading();
            String owners = goal().movementOwner();
            if (target != null && target.isAlive()) require(!owners.toLowerCase().contains("melee"), "Melee took ownership: " + owners);
            require(guard.getY() >= 159.9, "Guard left the prepared platform");
            if (name().equals("friendly_lane") && ticks == 70) {
                require(bullets == 0 && rounds() == initialRounds, "Blocked lane fired or consumed ammo");
                ally.discard();
            }
            if (name().endsWith("sidearm_return")) tickSidearm();
            if (name().startsWith("patrol_")) tickPatrol();
            if (name().equals("squad_rally") || name().equals("squad_fallback")) {
                // Rally completes on arrival and then releases ordinary duties; it is not HOLD.
                reachedCommand |= guard.distanceToSqr(Vec3.atBottomCenterOf(ORIGIN.offset(-10, 0, 0))) < 4;
            }
            if (name().equals("squad_focus") && ticks == 90) {
                require(guard.getTarget() == target && bullets > 0, "Focus did not acquire and fire at the requested target");
                dispatch(SquadCommandType.RESUME_ORDERS, null, null);
                require(guard.getTarget() == null, "Resume retained the command-owned target");
            }
            if (ticks % 100 == 0) LogUtils.getLogger().info("VGG_BEHAVIOR_QA_PROGRESS case={} tick={} bullets={} travel={} owners={}",
                    name(), ticks, bullets, maximumTravel, owners);
            if (ticks < (name().startsWith("patrol_") ? 300 : 200)) return;
            switch (name()) {
                case "tactical_dry" -> {
                    require(bullets == 0 && rounds() == 0, "Dry guard fired");
                    require(minimumDistance >= initialDistance - 1.0, "Dry guard advanced into the enemy group");
                    require(maximumTravel > 2 && guard.distanceTo(target) > initialDistance + 1, "Dry guard did not withdraw");
                }
                case "tactical_reload" -> require(reload && maximumTravel > 2 && bullets > 0, "Reload/withdraw/resume was not exercised");
                case "shield_fire", "friendly_lane" -> require(bullets > 0, "Guard never resumed safe fire");
                case "event_hold_fire" -> require(events > 0 && bullets == 0 && rounds() == initialRounds,
                        "Fire-event order change did not cancel the pending round");
                case "burst_hold_fire" -> require(bullets == 1 && initialRounds - rounds() == 1,
                        "Hold Fire failed to cancel the rest of the queued burst: " + bullets);
                case "aim_mob_zero" -> require(bullets >= 3 && maximumAimError < .03, "Player override changed zero-error mob aim");
                case "aim_mob_error", "aim_player_override" -> require(bullets >= 3 && maximumAimError > .1 && maximumAimError < 6.03,
                        "Configured aim error was not bounded/applied: " + maximumAimError);
                case "sidearm_return", "explosive_sidearm_return" -> require(activeSidearm && restoredSidearm && bullets > 0, "Sidearm did not fire and return");
                case "squad_rally", "squad_fallback" -> require(reachedCommand,
                        "Positional command was not reached: " + guard.position());
                case "squad_defend" -> require(guard.getX() < -2, "Defend did not return to the requested area");
                case "patrol_resume", "patrol_obstacle" -> require(visitedSecond && resumed, "Patrol did not visit its second point and resume: " + patrolDiagnostics());
                default -> { }
            }
            if (name().startsWith("aim_")) require(initialRounds - rounds() == bullets
                    && GuardNativeShotAccess.controller(guard).successfulAimRounds() == bullets, "Aim/ammo accounting diverged");
            LogUtils.getLogger().info("VGG_BEHAVIOR_QA_CASE_PASS case={} bullets={} travel={} maxAimError={}",
                    name(), bullets, maximumTravel, maximumAimError);
            next();
        } catch (Throwable failure) { fail(failure); }
    }

    private void fire(GunFireEvent event) {
        if (done || restoring || event.getShooter() != guard) return;
        events++;
        if (name().equals("event_hold_fire")) orders(GuardOrders.MovementOrder.HOLD_POSITION, GuardOrders.EngagementOrder.HOLD_FIRE);
    }

    private void joined(EntityJoinLevelEvent event) {
        if (done || restoring || event.isCanceled() || !(event.getEntity() instanceof EntityKineticBullet bullet)
                || bullet.getOwner() != guard) return;
        try {
            bullets++;
            if (name().equals("explosive_sidearm_return")) require(((IGun) guard.getMainHandItem().getItem())
                    .getGunId(guard.getMainHandItem()).getPath().equals("glock_17"), "Explosive primary fired inside its standoff");
            if (name().startsWith("aim_")) {
                var aim = GuardNativeShotAccess.controller(guard).approvedShotAim();
                require(aim != null, "TaCZ projectile has no approved aim sample");
                Vec3 intended = name().equals("aim_player_override") ? target.getBoundingBox().getCenter() : target.getEyePosition();
                require(aim.targetPoint().distanceToSqr(intended) < 1e-10, "Wrong aim point for target policy");
                double dot = Math.clamp(aim.direction().dot(intended.subtract(aim.aimOrigin()).normalize()), -1, 1);
                maximumAimError = Math.max(maximumAimError, Math.toDegrees(Math.acos(dot)));
            }
            if (name().equals("burst_hold_fire")) orders(GuardOrders.MovementOrder.HOLD_POSITION, GuardOrders.EngagementOrder.HOLD_FIRE);
        } catch (Throwable failure) { fail(failure); }
    }

    private void tickSidearm() {
        if (GuardSidearmAccess.getState(guard) == SidearmState.SIDEARM_ACTIVE) {
            if (!activeSidearm) {
                activeSidearm = true;
                CompoundTag saved = guard.saveWithoutId(new CompoundTag());
                if (name().equals("explosive_sidearm_return")) require(saved.getCompound(GuardSidearmStorage.STORAGE_TAG)
                        .getString("ActivationReason").equals("EXPLOSIVE_STANDOFF"), "Sidearm used the wrong activation reason");
                Guard copy = GuardEntityType.GUARD.get().create(level); copy.load(saved);
                require(GuardSidearmAccess.getState(copy) == SidearmState.SIDEARM_ACTIVE, "Active sidearm lost authentication on load");
                require(ItemStack.matches(copy.getMainHandItem(), guard.getMainHandItem())
                        && ItemStack.matches(GuardSidearmAccess.getEffectivePrimaryCopy(copy), primaryBefore),
                        "Active swap lost gun components: hand=" + guard.getMainHandItem().save(level.registryAccess())
                                + ", loadedHand=" + copy.getMainHandItem().save(level.registryAccess())
                                + ", initialPrimary=" + primaryBefore.save(level.registryAccess())
                                + ", loadedPrimary=" + GuardSidearmAccess.getEffectivePrimaryCopy(copy).save(level.registryAccess()));
            }
            if (bullets > 0 && changedAt < 0) {
                changedAt = ticks;
                orders(GuardOrders.MovementOrder.HOLD_POSITION, GuardOrders.EngagementOrder.HOLD_FIRE);
            }
        }
        if (activeSidearm && GuardSidearmAccess.getState(guard) == SidearmState.PRIMARY_ACTIVE) {
            restoredSidearm = true;
            require(ItemStack.matches(guard.getMainHandItem(), primaryBefore), "Restored primary changed");
            ItemStack sidearm = GuardSidearmAccess.getConfiguredSidearmInventory(guard).getStackInSlot(0);
            require(sidearm.getItem() instanceof IGun && loaded(sidearm) + bullets == 17, "Sidearm ammo duplicated or lost");
        }
    }

    private void bindSquad() {
        commander.setPos(guard.getX(), guard.getY(), guard.getZ() + 3);
        guard.setOwnerId(commander.getUUID());
        ledger = new ItemStack(ModItems.SQUAD_COMMAND_LEDGER.get());
        commander.setItemInHand(InteractionHand.MAIN_HAND, ledger);
        commander.setShiftKeyDown(true);
        var binding = SquadLedgerService.interact(commander, guard, InteractionHand.MAIN_HAND);
        commander.setShiftKeyDown(false);
        require(binding.changedGuard(), "Could not bind the squad ledger: " + binding);
        UUID squad = GuardSquadAccess.getSquadId(guard);
        require(squad != null, "Membership not created"); squads.add(squad);
        require(SquadLedgerBindingCodec.readFrom(ItemCustomData.read(ledger)).binding().squadId().equals(squad), "Ledger binding mismatch");
    }

    private void dispatch(SquadCommandType type, BlockPos position, LivingEntity focus) {
        if (type == SquadCommandType.HOLD && position == null) position = guard.blockPosition();
        var result = SquadCommandDispatcher.dispatch(commander, ledger, type, position, focus);
        require(result.completed() && result.applied() == 1, "Command failed: " + result);
    }

    private void prepareCommand() {
        bindSquad();
        if (name().equals("squad_rally")) {
            verifyLedgerOrders();
            // Another squad member is loaded but hostile to this commander. It must not
            // receive the command just because the ledger matches its membership.
            Guard hostile = newGuard("ak47", FireMode.AUTO, 0);
            hostile.setNoAi(true); hostile.setPos(2.5, 160, 4.5); hostile.setTarget(commander);
            GuardSquadAccess.setMembership(hostile, GuardSquadAccess.getMembership(guard));
            var result = SquadCommandDispatcher.dispatch(commander, ledger, SquadCommandType.RALLY, ORIGIN.offset(-10, 0, 0), null);
            require(result.applied() == 1 && result.unauthorized() == 1 && GuardSquadCommandAccess.getCommand(hostile) == null,
                    "Dispatch ignored per-guard authorization: " + result);
        } else if (name().equals("squad_fallback")) {
            target = enemy(16, 0); guard.setTarget(target);
            dispatch(SquadCommandType.FALL_BACK, ORIGIN.offset(-10, 0, 0), null);
        } else if (name().equals("squad_defend")) {
            dispatch(SquadCommandType.DEFEND, ORIGIN.offset(-14, 0, 0), null);
        } else {
            target = enemy(12, 0);
            commander.setPos(8.5, 160, 3.5);
            dispatch(SquadCommandType.FOCUS_TARGET, null, target);
        }
        LogUtils.getLogger().info("VGG_BEHAVIOR_QA_BEGIN case={}", name());
    }

    private void verifyLedgerOrders() {
        var template = GuardOrders.DEFAULT.withMovement(GuardOrders.MovementOrder.HOLD_POSITION)
                .withEngagement(GuardOrders.EngagementOrder.HOLD_FIRE).withMaximumRange(GuardOrders.RangeOrder.BLOCKS_16);
        GuardOrdersAccess.setOrders(guard, template);
        var role = ResourceLocation.parse("villagerguardgunners:marksman");
        GuardRoleAccess.setRoleId(guard, role);
        ItemStack ordersLedger = new ItemStack(ModItems.ORDERS_LEDGER.get());
        commander.setItemInHand(InteractionHand.OFF_HAND, ordersLedger);
        require(GuardOrderTemplateService.capture(commander, guard, InteractionHand.OFF_HAND, false).completed(), "Template capture failed");
        GuardOrdersAccess.setOrders(guard, GuardOrders.DEFAULT);
        GuardRoleAccess.setRoleId(guard, null);
        require(GuardOrderTemplateService.apply(commander, guard, InteractionHand.OFF_HAND).changedGuard()
                && GuardOrdersAccess.getOrders(guard).equals(template)
                && role.equals(GuardRoleAccess.getRoleId(guard)), "Template apply failed to restore orders and role");
        int revision = GuardOrdersAccess.getRevision(guard);
        require(GuardOrderTemplateService.apply(commander, guard, InteractionHand.OFF_HAND).completed()
                && GuardOrdersAccess.getRevision(guard) == revision, "Idempotent apply changed revision");
        commander.setGameMode(GameType.SPECTATOR);
        require(GuardOrderTemplateService.apply(commander, guard, InteractionHand.OFF_HAND).status()
                == GuardOrderTemplateService.OperationStatus.UNAUTHORIZED, "Spectator changed guard orders");
        commander.setGameMode(GameType.SURVIVAL);
        GuardRoleAccess.setRoleId(guard, null);
        orders(GuardOrders.MovementOrder.TACTICAL, GuardOrders.EngagementOrder.GUARD_TARGETS);
    }

    private void preparePatrol() {
        bindSquad();
        if (name().equals("patrol_obstacle")) {
            for (int z = -2; z <= 2; z++) for (int y = 0; y < 3; y++) {
                BlockPos block = ORIGIN.offset(6, y, z);
                level.setBlockAndUpdate(block, Blocks.STONE.defaultBlockState()); obstacles.add(block);
            }
        }
        for (BlockPos pos : List.of(ORIGIN, ORIGIN.offset(12, 0, 0))) {
            commander.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 3.5);
            require(SquadPatrolRouteEditor.edit(commander, ledger, SquadPatrolRouteEditor.EditAction.ADD_WAYPOINT, pos).changed(),
                    "Could not add route waypoint");
        }
        commander.setPos(.5, 160, 3.5);
        guard.setPatrolling(true); guard.setPatrolPos(ORIGIN.offset(-15, 0, 0));
        // Keep the full upstream goal list: the checkpoint goal must yield to the route.
        LogUtils.getLogger().info("VGG_BEHAVIOR_QA_BEGIN case={} route={}", name(), patrolDiagnostics());
    }

    private void tickPatrol() {
        if (guard.distanceToSqr(Vec3.atBottomCenterOf(ORIGIN.offset(12, 0, 0))) < 4) visitedSecond = true;
        if (visitedSecond && changedAt < 0) {
            dispatch(SquadCommandType.HOLD, null, null);
            heldPosition = guard.position(); changedAt = ticks;
        }
        if (changedAt >= 0 && ticks < changedAt + 40) require(guard.position().distanceTo(heldPosition) < 1.5, "HOLD failed to pause patrol");
        if (changedAt >= 0 && ticks == changedAt + 40) dispatch(SquadCommandType.RESUME_ORDERS, null, null);
        if (changedAt >= 0 && ticks > changedAt + 45 && guard.position().distanceTo(heldPosition) > 3) resumed = true;
    }

    private void preparePersistence() throws Exception {
        guard = newGuard("ak47", FireMode.AUTO, 0);
        orders(GuardOrders.MovementOrder.PATROL_BOUND, GuardOrders.EngagementOrder.DEFENSIVE);
        bindSquad();
        var registry = SquadRegistrySavedData.get(level.getServer());
        UUID squad = GuardSquadAccess.getSquadId(guard);
        registry.replacePatrolRoute(squad, level.dimension().location(), List.of(ORIGIN, ORIGIN.offset(12, 0, 0)), level.getGameTime());
        // An entirely unloaded route must yield without loading destination chunks.
        BlockPos unloaded = ORIGIN.offset(8192, 0, 0);
        require(!level.hasChunkAt(unloaded), "Unloaded fixture was already loaded");
        registry.replacePatrolRoute(squad, level.dimension().location(), List.of(unloaded, unloaded.offset(4, 0, 0)), level.getGameTime());
        var movement = new GuardPatrolRouteMovementController(guard);
        require(!movement.hasExecutableRoute() && !movement.canStart() && !level.hasChunkAt(unloaded), "Patrol loaded a distant waypoint");
        registry.replacePatrolRoute(squad, level.dimension().location(), List.of(ORIGIN, ORIGIN.offset(12, 0, 0)), level.getGameTime());
        ItemStack ordersLedger = new ItemStack(ModItems.ORDERS_LEDGER.get());
        commander.setItemInHand(InteractionHand.OFF_HAND, ordersLedger);
        require(GuardOrderTemplateService.capture(commander, guard, InteractionHand.OFF_HAND, false).completed(), "Persistence template capture failed");
        require(GuardSidearmTransactions.configureNaturalSidearm(guard, gun("glock_17", FireMode.SEMI, 11)).configured(), "Persistence sidearm failed");
        ItemCustomData.update(guard.getMainHandItem(), tag -> tag.putString("qa_primary", "restart-components"));
        require(GuardSidearmTransactions.activate(guard, SidearmActivationReason.PRIMARY_DRY, level.getGameTime(), UUID.randomUUID(), true)
                .status() == SidearmTransitionStatus.ACTIVATED, "Persistence activation failed");
        guard.setNoAi(true);
        dispatch(SquadCommandType.HOLD, null, null);
        BlockPos chestPos = ORIGIN.offset(0, 0, 6);
        level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        var chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
        chest.setItem(0, ledger.copy()); chest.setItem(1, ordersLedger.copy()); chest.setChanged();
        expected = new CompoundTag(); expected.putUUID("Guard", guard.getUUID()); expected.putUUID("Squad", squad);
        expected.put("Entity", guard.saveWithoutId(new CompoundTag()));
        expected.put("Ledger", ledger.save(level.registryAccess())); expected.put("OrdersLedger", ordersLedger.save(level.registryAccess()));
        NbtIo.writeCompressed(expected, EXPECTED);
        done = true;
        LogUtils.getLogger().info("VGG_BEHAVIOR_QA_READY_TO_RESTART uuid={}", guard.getUUID());
    }

    private void checkRestart() {
        Entity loaded = level.getEntity(expected.getUUID("Guard"));
        if (!(loaded instanceof Guard restored)) { require(ticks < 400, "Saved guard not loaded"); return; }
        guard = restored;
        Guard snapshot = GuardEntityType.GUARD.get().create(level); snapshot.load(expected.getCompound("Entity"));
        require(GuardOrdersAccess.getOrders(guard).equals(GuardOrdersAccess.getOrders(snapshot)), "Orders changed on restart");
        require(expected.getUUID("Squad").equals(GuardSquadAccess.getSquadId(guard)), "Membership changed on restart");
        require(GuardSidearmAccess.getState(guard) == SidearmState.SIDEARM_ACTIVE
                && ItemStack.matches(guard.getMainHandItem(), snapshot.getMainHandItem())
                && ItemStack.matches(GuardSidearmAccess.getEffectivePrimaryCopy(guard), GuardSidearmAccess.getEffectivePrimaryCopy(snapshot)),
                "Sidearm/primary components changed on restart");
        var route = SquadRegistrySavedData.get(level.getServer()).findPatrolRoute(expected.getUUID("Squad")).orElseThrow();
        require(route.waypoints().equals(List.of(ORIGIN, ORIGIN.offset(12, 0, 0))), "Route changed on restart");
        var chest = (ChestBlockEntity) level.getBlockEntity(ORIGIN.offset(0, 0, 6));
        require(ItemStack.matches(chest.getItem(0), ItemStack.parseOptional(level.registryAccess(), expected.getCompound("Ledger")))
                && ItemStack.matches(chest.getItem(1), ItemStack.parseOptional(level.registryAccess(), expected.getCompound("OrdersLedger"))),
                "Ledger components changed on restart");
        require(GuardSquadCommandAccess.getCommand(snapshot).equals(SquadCommandRuntime.activeCommand(guard)),
                "Unexpired squad command changed on restart");
        var expired = SquadCommand.positional(SquadCommandType.HOLD, UUID.randomUUID(), 0, 1,
                level.dimension().location(), guard.blockPosition(), 0);
        GuardSquadCommandAccess.setCommand(guard, expired);
        require(SquadCommandRuntime.activeCommand(guard) == null, "Expired command remained active");
        done = true;
        LogUtils.getLogger().info("VGG_BEHAVIOR_QA_RESTART_PASS uuid={} sidearm={} routePoints={}", guard.getUUID(),
                GuardSidearmAccess.getState(guard), route.waypoints().size());
    }

    private Guard newGuard(String id, FireMode mode, int ammo) {
        Guard result = GuardEntityType.GUARD.get().create(level);
        result.setPersistenceRequired(); result.setInvulnerable(true);
        result.moveTo(.5, 160, .5, -90, 0);
        result.setItemSlot(EquipmentSlot.MAINHAND, gun(id, mode, ammo));
        add(result); return result;
    }
    private ItemStack gun(String id, FireMode mode, int ammo) {
        return GunItemBuilder.create().setId(ResourceLocation.fromNamespaceAndPath("tacz", id))
                .setFireMode(mode).setAmmoCount(ammo).setAmmoInBarrel(ammo > 0 && !id.equals("rpg7")).build(level.registryAccess());
    }
    private Husk enemy(int x, int z) {
        Husk result = EntityType.HUSK.create(level);
        result.setNoAi(true); result.setPersistenceRequired(); result.setInvulnerable(true);
        result.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024); result.setHealth(1024);
        result.moveTo(x + .5, 160, z + .5); add(result); return result;
    }
    private void add(Entity entity) { require(level.addFreshEntity(entity), "Entity spawn rejected"); entities.add(entity); }
    private void orders(GuardOrders.MovementOrder movement, GuardOrders.EngagementOrder engagement) {
        GuardOrdersAccess.setOrders(guard, GuardOrders.DEFAULT.withMovement(movement).withEngagement(engagement));
    }
    private int rounds() { return loaded(guard.getMainHandItem()); }
    private static int loaded(ItemStack stack) {
        IGun gun = (IGun) stack.getItem(); return gun.getCurrentAmmoCount(stack) + (gun.hasBulletInBarrel(stack) ? 1 : 0);
    }
    private GuardGunCombatGoal goal() {
        return guard.goalSelector.getAvailableGoals().stream().map(w -> w.getGoal()).filter(GuardGunCombatGoal.class::isInstance)
                .map(GuardGunCombatGoal.class::cast).findFirst().orElseThrow();
    }
    private Object patrolDiagnostics() {
        return guard.goalSelector.getAvailableGoals().stream().map(w -> w.getGoal()).filter(GuardAutomaticDutyMovementGoal.class::isInstance)
                .map(GuardAutomaticDutyMovementGoal.class::cast).map(GuardAutomaticDutyMovementGoal::capturePatrolDiagnostics).toList();
    }
    private void cleanup() {
        for (Entity entity : entities) entity.discard(); entities.clear();
        for (UUID squad : squads) SquadRegistrySavedData.get(level.getServer()).remove(squad); squads.clear();
        var projectiles = new ArrayList<Entity>();
        for (Entity entity : level.getAllEntities()) if (entity instanceof EntityKineticBullet) projectiles.add(entity);
        projectiles.forEach(Entity::discard);
        for (BlockPos block : obstacles) level.setBlockAndUpdate(block, Blocks.AIR.defaultBlockState()); obstacles.clear();
        target = null; ally = null;
    }
    private void fail(Throwable failure) {
        done = true;
        LogUtils.getLogger().error("VGG_BEHAVIOR_QA_FAIL case={} tick={} position={} status={} patrol={}", name(), ticks,
                guard == null ? null : guard.position(), guard == null ? null : goal().getPlayerFacingStatus(),
                guard == null ? null : patrolDiagnostics(), failure);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
