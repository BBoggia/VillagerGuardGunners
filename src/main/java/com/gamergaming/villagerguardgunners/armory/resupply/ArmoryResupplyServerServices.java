package com.gamergaming.villagerguardgunners.armory.resupply;

import com.gamergaming.villagerguardgunners.api.GuardArmoryResupplyAccess;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import tallestegg.guardvillagers.entities.Guard;

/** Owns one transient loaded index and reservation ledger per running server. */
public final class ArmoryResupplyServerServices {
    private static final Map<MinecraftServer, Services> BY_SERVER = new IdentityHashMap<>();

    private ArmoryResupplyServerServices() {
    }

    public static synchronized Services forLevel(ServerLevel level) {
        if (level == null) {
            throw new NullPointerException("level");
        }
        return BY_SERVER.computeIfAbsent(level.getServer(), ignored -> new Services());
    }

    public static synchronized Services existingForLevel(ServerLevel level) {
        return level == null ? null : BY_SERVER.get(level.getServer());
    }

    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof Guard guard)) {
            return;
        }
        GuardArmoryResupplyAccess.cancel(guard);
        Services services = existingForLevel(level);
        if (services != null) {
            services.reservations().releaseGuard(guard.getUUID());
        }
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Services services = existingForLevel(level);
        if (services == null) {
            return;
        }
        services.loadedArmories().removeLevel(level.dimension());
        services.reservations().releaseDimension(level.dimension().location());
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        Services services;
        synchronized (ArmoryResupplyServerServices.class) {
            services = BY_SERVER.remove(event.getServer());
        }
        if (services != null) {
            services.loadedArmories().clear();
            services.reservations().clear();
        }
    }

    public record Services(
            LoadedArmoryIndex loadedArmories,
            ArmoryReservationBook reservations) {
        private Services() {
            this(new LoadedArmoryIndex(), new ArmoryReservationBook());
        }
    }
}
