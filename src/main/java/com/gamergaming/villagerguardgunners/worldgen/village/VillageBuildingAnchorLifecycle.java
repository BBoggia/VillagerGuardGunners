package com.gamergaming.villagerguardgunners.worldgen.village;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;

/** Main-thread handoff for one passive, persistent anchor claim. */
public final class VillageBuildingAnchorLifecycle {

    public static VillageBuildingIdentity record(
            WorldGenLevel worldGenLevel,
            ResourceLocation entryId,
            VillageBuildingType buildingType,
            BlockPos anchorPosition) {
        Objects.requireNonNull(worldGenLevel, "worldGenLevel");
        ServerLevel sourceLevel = worldGenLevel.getLevel();
        ResourceLocation dimensionId = sourceLevel.dimension().location();
        VillageBuildingIdentity identity = VillageBuildingIdentity.anchored(
                worldGenLevel.getSeed(),
                dimensionId,
                entryId,
                buildingType,
                anchorPosition);
        MinecraftServer server = sourceLevel.getServer();
        ResourceKey<Level> dimension = sourceLevel.dimension();
        Runnable claim = () -> {
            ServerLevel liveLevel = server.getLevel(dimension);
            if (liveLevel != null) {
                VillageBuildingSavedData.get(liveLevel).claimAnchor(identity);
            }
        };
        if (server.isSameThread()) {
            claim.run();
        } else {
            server.execute(claim);
        }
        return identity;
    }

    private VillageBuildingAnchorLifecycle() {
    }
}
