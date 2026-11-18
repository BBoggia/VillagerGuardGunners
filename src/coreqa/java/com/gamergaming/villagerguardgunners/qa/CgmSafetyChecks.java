package com.gamergaming.villagerguardgunners.qa;

import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary;
import com.gamergaming.villagerguardgunners.guns.GunProviders;
import com.gamergaming.villagerguardgunners.guns.managed.ManagedGunBackend;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.mrcrayfish.guns.common.Gun;
import com.mrcrayfish.guns.common.ProjectileManager;
import com.mrcrayfish.guns.entity.ProjectileEntity;
import com.mrcrayfish.guns.init.ModEnchantments;
import com.mrcrayfish.guns.item.GunItem;
import com.mrcrayfish.guns.util.GunItemData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.common.entities.Guard;

/** Native projectile/blast regressions, loaded only by the installed CGM suite. */
final class CgmSafetyChecks {
    private CgmSafetyChecks() {}

    static void run(ServerLevel level, BlockPos origin) throws Exception {
        List<Entity> fixtures = new ArrayList<>();
        boolean preserve = ModConfigs.GUARD_GUNNERS.safety.preserveBlocksFromGuardExplosions.get();
        BlockPos glass = origin.offset(0, 0, 1);
        var previousBlock = level.getBlockState(glass);
        try {
            var provider = GunProviders.byId("cgm").orElseThrow();
            var ctor = Class.forName("com.gamergaming.villagerguardgunners.compat.cgm.CgmGunBackend")
                    .getDeclaredConstructor();
            ctor.setAccessible(true);
            ManagedGunBackend backend = (ManagedGunBackend) ctor.newInstance();
            Guard guard = GuardEntityType.GUARD.get().create(level);
            Villager ally = EntityType.VILLAGER.create(level);
            Husk enemy = EntityType.HUSK.create(level);
            for (LivingEntity entity : List.of(guard, ally, enemy)) {
                entity.setNoGravity(true);
                ((net.minecraft.world.entity.Mob) entity).setNoAi(true);
                entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
                entity.setHealth(1000);
                int offset = entity == ally ? 1 : entity == enemy ? -1 : 0;
                entity.moveTo(origin.getX() + offset + .5, origin.getY(), origin.getZ() + .5, 0, 0);
                require(level.addFreshEntity(entity), "Safety fixture spawn rejected");
                fixtures.add(entity);
            }

            ItemStack launcher = provider.createGun(ResourceLocation.parse("cgm:grenade_launcher"),
                    level.registryAccess(), true).orElseThrow();
            guard.setItemSlot(EquipmentSlot.MAINHAND, launcher);
            assertRadius(level, guard, launcher, backend);
            var tag = GunItemData.getTag(launcher);
            tag.putFloat("AdditionalDamage", 15);
            GunItemData.setTag(launcher, tag);
            assertRadius(level, guard, launcher, backend);
            launcher.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(ModEnchantments.ACCELERATOR), 2);
            assertRadius(level, guard, launcher, backend);

            for (String gun : List.of("grenade_launcher", "bazooka")) {
                ItemStack weapon = provider.createGun(ResourceLocation.parse("cgm:" + gun),
                        level.registryAccess(), true).orElseThrow();
                guard.setItemSlot(EquipmentSlot.MAINHAND, weapon);
                for (boolean blockProtection : List.of(true, false)) {
                    ModConfigs.GUARD_GUNNERS.safety.preserveBlocksFromGuardExplosions.accept(blockProtection);
                    reset(ally); reset(enemy);
                    level.setBlock(glass, Blocks.GLASS.defaultBlockState(), 3);
                    ProjectileEntity projectile = projectile(level, guard, weapon);
                    projectile.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
                    ProjectileEntity.createExplosion(projectile, 3, true);
                    require(ally.getHealth() == 1000, gun + " explosion hurt a protected villager");
                    require(enemy.getHealth() < 1000, gun + " explosion failed to damage an enemy");
                    require(level.getBlockState(glass).is(Blocks.GLASS) == blockProtection,
                            gun + " explosion ignored block preservation=" + blockProtection);
                }
            }
            ModConfigs.GUARD_GUNNERS.safety.preserveBlocksFromGuardExplosions.accept(true);
            reset(ally);
            level.setBlock(glass, Blocks.GLASS.defaultBlockState(), 3);
            ProjectileEntity hostile = projectile(level, enemy, launcher);
            hostile.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
            ProjectileEntity.createExplosion(hostile, 3, true);
            require(ally.getHealth() < 1000 && level.getBlockState(glass).isAir(),
                    "Guard-only protection changed a non-guard explosion");

            tag = GunItemData.getTag(launcher);
            tag.putBoolean("IgnoreAmmo", true);
            GunItemData.setTag(launcher, tag);
            var infinite = provider.captureAmmo(launcher, null);
            require(infinite.infiniteReserve() && infinite.reserveSource() == GuardAmmoSummary.ReserveSource.NATIVE_INFINITE,
                    "CGM IgnoreAmmo gun was reported as a finite physical supply consumer");
        } finally {
            fixtures.forEach(Entity::discard);
            level.setBlock(glass, previousBlock, 3);
            ModConfigs.GUARD_GUNNERS.safety.preserveBlocksFromGuardExplosions.accept(preserve);
        }
    }

    private static ProjectileEntity projectile(ServerLevel level, LivingEntity shooter, ItemStack stack) {
        GunItem item = (GunItem) stack.getItem();
        Gun gun = item.getModifiedGun(stack);
        ProjectileEntity projectile = ProjectileManager.getInstance().getFactory(gun.getProjectile().getItem())
                .create(level, shooter, stack, item, gun);
        projectile.setWeapon(stack);
        projectile.setAdditionalDamage(Gun.getAdditionalDamage(stack));
        return projectile;
    }

    private static void assertRadius(ServerLevel level, Guard guard, ItemStack stack, ManagedGunBackend backend) {
        ProjectileEntity nativeProjectile = projectile(level, guard, stack);
        double expected = nativeProjectile.getDamage() / 5.0D;
        double actual = backend.stats(stack).orElseThrow().explosionRadius();
        require(Math.abs(actual - expected) < 0.00001, "Predicted grenade radius " + actual + " != native " + expected);
    }

    private static void reset(LivingEntity entity) { entity.invulnerableTime = 0; entity.setHealth(1000); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
