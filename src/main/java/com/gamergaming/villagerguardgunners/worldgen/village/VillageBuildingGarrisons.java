package com.gamergaming.villagerguardgunners.worldgen.village;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Closed mapping from village-building types to their bounded datapack group. */
public final class VillageBuildingGarrisons {
    public static final ResourceLocation BARRACKS_GROUP = id("village_barracks");
    public static final ResourceLocation SENTRY_POST_GROUP = id("village_sentry_post");
    public static final ResourceLocation WATCHTOWER_GROUP = id("village_watchtower");

    private static final Plan BARRACKS_PLAN =
            new Plan(BARRACKS_GROUP, 0, "Barracks");
    private static final Plan SENTRY_POST_PLAN =
            new Plan(SENTRY_POST_GROUP, 0, "Sentry Post");
    private static final Plan WATCHTOWER_PLAN =
            new Plan(WATCHTOWER_GROUP, 5, "Village Watchtower");
    private static final Map<VillageBuildingType, Plan> PLANS = Map.of(
            VillageBuildingType.BARRACKS, BARRACKS_PLAN,
            VillageBuildingType.SENTRY_POST, SENTRY_POST_PLAN,
            VillageBuildingType.WATCHTOWER, WATCHTOWER_PLAN);
    private static final Map<ResourceLocation, Plan> GROUP_PLANS = Map.of(
            BARRACKS_GROUP, BARRACKS_PLAN,
            SENTRY_POST_GROUP, SENTRY_POST_PLAN,
            WATCHTOWER_GROUP, WATCHTOWER_PLAN);

    private VillageBuildingGarrisons() {
    }

    public static Optional<Plan> planFor(VillageBuildingType buildingType) {
        return Optional.ofNullable(PLANS.get(Objects.requireNonNull(
                buildingType, "buildingType")));
    }

    public static boolean isVillageGroup(ResourceLocation groupId) {
        return groupId != null && GROUP_PLANS.containsKey(groupId);
    }

    public static String squadDisplayName(ResourceLocation groupId, UUID buildingId) {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(buildingId, "buildingId");
        Plan plan = GROUP_PLANS.get(groupId);
        String prefix = plan == null ? "Village Guard Post" : plan.displayPrefix();
        return prefix + " " + buildingId.toString().substring(0, 8);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(VillagerGuardGunners.MODID, path);
    }

    public record Plan(
            ResourceLocation groupId,
            int verticalSpawnOffset,
            String displayPrefix) {
        public Plan {
            Objects.requireNonNull(groupId, "groupId");
            if (verticalSpawnOffset < 0 || verticalSpawnOffset > 8) {
                throw new IllegalArgumentException("Garrison vertical offset is outside its bound");
            }
            if (displayPrefix == null || displayPrefix.isBlank()
                    || displayPrefix.length() > 32) {
                throw new IllegalArgumentException("Invalid garrison display prefix");
            }
        }
    }
}
