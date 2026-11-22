package com.gamergaming.villagerguardgunners.compat.cgm;

import com.mrcrayfish.guns.common.ProjectileManager;
import com.mrcrayfish.guns.init.ModItems;
import com.mrcrayfish.guns.interfaces.IProjectileFactory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public enum CgmProjectileKind {
    BULLET, GRENADE, MISSILE, UNKNOWN;

    private static IProjectileFactory grenadeFactory;
    private static IProjectileFactory missileFactory;

    public static void recordNative(Item ammo, IProjectileFactory factory) {
        if (ammo == ModItems.GRENADE.get()) grenadeFactory = factory;
        if (ammo == ModItems.MISSILE.get()) missileFactory = factory;
    }

    static CgmProjectileKind forAmmo(ResourceLocation id) {
        return id == null ? UNKNOWN : classify(ProjectileManager.getInstance().getFactory(id));
    }

    static CgmProjectileKind classify(IProjectileFactory factory) {
        if (factory == null) return UNKNOWN;
        if (factory == grenadeFactory) return GRENADE;
        if (factory == missileFactory) return MISSILE;
        // The manager returns its ordinary bullet factory for every unregistered key.
        if (factory == ProjectileManager.getInstance().getFactory(null)) return BULLET;
        return UNKNOWN;
    }

    boolean explosive() {
        return this == GRENADE || this == MISSILE;
    }
}
