package com.gamergaming.villagerguardgunners.worldgen.post;

import com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity;
import com.gamergaming.villagerguardgunners.mixin.accessor.LootPoolAccessor;
import com.gamergaming.villagerguardgunners.mixin.accessor.LootPoolEntryAccessor;
import com.gamergaming.villagerguardgunners.mixin.accessor.LootPoolSingletonAccessor;
import com.gamergaming.villagerguardgunners.mixin.accessor.LootTableAccessor;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.phys.Vec3;

/** Server-thread-only, bounded completion of generated Armory loot. */
public final class GuardPostArmoryLoot {
    public static final int MAXIMUM_LOOT_STACKS = 64;
    public static final int MAXIMUM_DEFINITION_COMPONENTS = 256;

    private GuardPostArmoryLoot() {
    }

    public static void completePending(ServerLevel level, ArmoryBlockEntity armory) {
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("Generated Armory loot must run on the server thread");
        }
        GuardPostArmoryIdentity identity = armory.getGeneratedIdentity();
        if (identity == null || !identity.pending() || identity.lootTableId() == null) {
            return;
        }
        List<ItemStack> generated = evaluate(level, armory.getBlockPos(), identity);
        if (generated == null
                || !armory.initializeGenerated(
                        identity.postId(),
                        identity.lootTableId(),
                        armory.getSquadLock(),
                        generated)) {
            armory.completeGeneratedWithoutLoot(identity.postId());
        }
    }

    private static List<ItemStack> evaluate(
            ServerLevel level,
            net.minecraft.core.BlockPos position,
            GuardPostArmoryIdentity identity) {
        try {
            LootTable table = level.getServer()
                    .getLootData()
                    .getLootTable(identity.lootTableId());
            LootParams parameters = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(position))
                    .create(LootContextParamSets.CHEST);
            LootContext context = new LootContext.Builder(parameters)
                    .withOptionalRandomSeed(identity.lootSeed())
                    .create(null);
            if (!hasBoundedDefinition(table, context)) {
                return null;
            }
            List<ItemStack> generated = new ArrayList<>();
            table.getRandomItemsRaw(
                    context,
                    LootTable.createStackSplitter(level, stack -> collect(generated, stack)));
            for (ItemStack stack : generated) {
                if (!isUsableAmmo(stack)) {
                    return null;
                }
            }
            return List.copyOf(generated);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static boolean hasBoundedDefinition(LootTable table, LootContext context) {
        LootTableAccessor tableAccess = (LootTableAccessor) table;
        List<LootPool> pools = tableAccess.villagerGuardGunners$getPools();
        int components = tableAccess.villagerGuardGunners$getFunctions().length;
        int rolls = 0;
        if (pools.size() > MAXIMUM_LOOT_STACKS
                || components > MAXIMUM_DEFINITION_COMPONENTS) {
            return false;
        }
        for (LootPool pool : pools) {
            if (!(pool.getRolls() instanceof ConstantValue)
                    || !(pool.getBonusRolls() instanceof ConstantValue)) {
                return false;
            }
            int poolRolls = Math.max(0, pool.getRolls().getInt(context));
            if (pool.getBonusRolls().getFloat(context) != 0.0F
                    || poolRolls > MAXIMUM_LOOT_STACKS - rolls) {
                return false;
            }
            rolls += poolRolls;

            LootPoolAccessor poolAccess = (LootPoolAccessor) pool;
            LootPoolEntryContainer[] entries = poolAccess.villagerGuardGunners$getEntries();
            components += entries.length
                    + poolAccess.villagerGuardGunners$getConditions().length
                    + poolAccess.villagerGuardGunners$getFunctions().length;
            if (entries.length > MAXIMUM_LOOT_STACKS
                    || components > MAXIMUM_DEFINITION_COMPONENTS) {
                return false;
            }
            for (LootPoolEntryContainer entry : entries) {
                if (!(entry instanceof LootItem) && !(entry instanceof EmptyLootItem)) {
                    return false;
                }
                components += ((LootPoolEntryAccessor) entry)
                        .villagerGuardGunners$getConditions()
                        .length;
                components += ((LootPoolSingletonAccessor) (LootPoolSingletonContainer) entry)
                        .villagerGuardGunners$getFunctions()
                        .length;
                if (components > MAXIMUM_DEFINITION_COMPONENTS) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void collect(List<ItemStack> generated, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (generated.size() >= MAXIMUM_LOOT_STACKS) {
            throw LootLimitExceeded.INSTANCE;
        }
        generated.add(stack.copy());
    }

    static boolean isUsableAmmo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation ammoId;
        if (stack.getItem() instanceof IAmmo ammo) {
            ammoId = ammo.getAmmoId(stack);
        } else if (stack.getItem() instanceof IAmmoBox box) {
            if (box.isAllTypeCreative(stack)) {
                return true;
            }
            if (!box.isCreative(stack) && box.getAmmoCount(stack) <= 0) {
                return false;
            }
            ammoId = box.getAmmoId(stack);
        } else {
            return false;
        }
        return ammoId != null && TimelessAPI.getCommonAmmoIndex(ammoId).isPresent();
    }

    /** Stackless singleton used only to abort loot-pool iteration at the cap. */
    private static final class LootLimitExceeded extends RuntimeException {
        private static final LootLimitExceeded INSTANCE = new LootLimitExceeded();

        private LootLimitExceeded() {
            super(null, null, false, false);
        }
    }
}
