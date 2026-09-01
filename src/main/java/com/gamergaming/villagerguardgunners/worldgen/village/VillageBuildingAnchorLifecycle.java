package com.gamergaming.villagerguardgunners.worldgen.village;

import com.gamergaming.villagerguardgunners.spawn.NaturalGuardDifficulty;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostSpawner;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;

/** Server-thread handoff for one persistent anchor and its bounded one-shot actions. */
public final class VillageBuildingAnchorLifecycle {
    static final int MAXIMUM_FUNCTIONAL_LOAD_RETRIES = 40;

    public static VillageBuildingIdentity record(
            WorldGenLevel worldGenLevel,
            ResourceLocation entryId,
            VillageBuildingType buildingType,
            BlockPos anchorPosition) {
        return recordInternal(
                worldGenLevel,
                entryId,
                buildingType,
                anchorPosition,
                null,
                null,
                false);
    }

    /** Records the anchor and activates its bounded building behavior in one server task. */
    public static VillageBuildingIdentity recordAndPopulate(
            WorldGenLevel worldGenLevel,
            ResourceLocation entryId,
            VillageBuildingType buildingType,
            BlockPos anchorPosition,
            Rotation rotation) {
        Objects.requireNonNull(worldGenLevel, "worldGenLevel");
        return recordInternal(
                worldGenLevel,
                entryId,
                buildingType,
                anchorPosition,
                Objects.requireNonNull(rotation, "rotation"),
                GuardPostSpawner.mapDifficulty(
                        worldGenLevel.getLevel().getDifficulty()),
                true);
    }

    private static VillageBuildingIdentity recordInternal(
            WorldGenLevel worldGenLevel,
            ResourceLocation entryId,
            VillageBuildingType buildingType,
            BlockPos anchorPosition,
            Rotation rotation,
            NaturalGuardDifficulty generationDifficulty,
            boolean populate) {
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
                VillageBuildingSavedData.ClaimResult anchorClaim =
                        VillageBuildingSavedData.get(liveLevel).claimAnchor(identity);
                if (populate
                        && rotation != null
                        && generationDifficulty != null
                        && (anchorClaim == VillageBuildingSavedData.ClaimResult.CLAIMED
                                || anchorClaim
                                        == VillageBuildingSavedData.ClaimResult.ALREADY_CLAIMED)) {
                    activateWhenLoaded(
                            server,
                            dimension,
                            identity,
                            rotation,
                            generationDifficulty,
                            0);
                }
            }
        };
        if (server.isSameThread()) {
            claim.run();
        } else {
            server.execute(claim);
        }
        return identity;
    }

    private static void activateWhenLoaded(
            MinecraftServer server,
            ResourceKey<Level> dimension,
            VillageBuildingIdentity identity,
            Rotation rotation,
            NaturalGuardDifficulty generationDifficulty,
            int attempt) {
        ServerLevel liveLevel = server.getLevel(dimension);
        if (liveLevel == null) {
            return;
        }
        boolean unloaded;
        if (identity.buildingType() == VillageBuildingType.ARMORY) {
            unloaded = VillageBuildingArmoryLifecycle.activate(liveLevel, identity, rotation)
                            .status()
                    == VillageBuildingArmoryLifecycle.Status.UNLOADED;
        } else {
            unloaded = VillageBuildingGarrisonLifecycle.populate(
                                    liveLevel, identity, rotation, generationDifficulty)
                            .status()
                    == VillageBuildingGarrisonLifecycle.Status.UNLOADED;
        }
        if (unloaded && attempt < MAXIMUM_FUNCTIONAL_LOAD_RETRIES) {
            server.tell(new TickTask(
                    server.getTickCount() + 1,
                    () -> activateWhenLoaded(
                            server,
                            dimension,
                            identity,
                            rotation,
                            generationDifficulty,
                            attempt + 1)));
        }
    }

    private VillageBuildingAnchorLifecycle() {
    }
}
