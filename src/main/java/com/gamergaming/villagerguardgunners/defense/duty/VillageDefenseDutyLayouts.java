package com.gamergaming.villagerguardgunners.defense.duty;

import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostSpawner;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingIdentity;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageWatchtowerGuardPlacement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;

/** Built-in duty-slot authoring tied to the templates' reviewed safe cells. */
public final class VillageDefenseDutyLayouts {
    private VillageDefenseDutyLayouts() {
    }

    public static VillageDefenseDutyLayout forBuilding(
            VillageBuildingIdentity building,
            Rotation rotation) {
        Objects.requireNonNull(building, "building");
        Objects.requireNonNull(rotation, "rotation");
        List<SlotDefinition> definitions = switch (building.buildingType()) {
            case BARRACKS -> List.of(
                    definition("center_muster", 0, 0,
                            Direction.NORTH, VillageDefenseDutySlotKind.MUSTER,
                            VillageDefenseDutyWeaponRequirement.ANY),
                    definition("west_muster", 1, 0,
                            Direction.WEST, VillageDefenseDutySlotKind.MUSTER,
                            VillageDefenseDutyWeaponRequirement.ANY),
                    definition("east_muster", 2, 0,
                            Direction.EAST, VillageDefenseDutySlotKind.MUSTER,
                            VillageDefenseDutyWeaponRequirement.ANY),
                    definition("entry_sentry", 3, 0,
                            Direction.NORTH, VillageDefenseDutySlotKind.SENTRY,
                            VillageDefenseDutyWeaponRequirement.ANY));
            case ARMORY -> List.of(
                    definition("service_sentry", 0, 0,
                            Direction.NORTH, VillageDefenseDutySlotKind.SENTRY,
                            VillageDefenseDutyWeaponRequirement.ANY));
            case SENTRY_POST -> List.of(
                    definition("road_sentry", 0, 0,
                            Direction.NORTH, VillageDefenseDutySlotKind.SENTRY,
                            VillageDefenseDutyWeaponRequirement.ANY));
            case WATCHTOWER -> List.of(
                    definition("deck_center", 0,
                            VillageWatchtowerGuardPlacement.UPPER_DECK_OFFSET,
                            Direction.NORTH, VillageDefenseDutySlotKind.OVERWATCH,
                            VillageDefenseDutyWeaponRequirement.RANGED),
                    definition("deck_west", 1,
                            VillageWatchtowerGuardPlacement.UPPER_DECK_OFFSET,
                            Direction.NORTH, VillageDefenseDutySlotKind.OVERWATCH,
                            VillageDefenseDutyWeaponRequirement.RANGED));
            case TRAINING_YARD -> List.of(
                    definition("firing_center", 0, 0,
                            Direction.SOUTH, VillageDefenseDutySlotKind.DRILL,
                            VillageDefenseDutyWeaponRequirement.RANGED),
                    definition("firing_west", 1, 0,
                            Direction.SOUTH, VillageDefenseDutySlotKind.DRILL,
                            VillageDefenseDutyWeaponRequirement.RANGED));
        };

        ArrayList<VillageDefenseDutySlot> slots = new ArrayList<>(definitions.size());
        for (SlotDefinition definition : definitions) {
            BlockPos position = GuardPostSpawner.spawnPosition(
                    building.anchorPosition(), rotation, definition.memberIndex())
                    .above(definition.verticalOffset());
            slots.add(new VillageDefenseDutySlot(
                    VillageDefenseDutySlot.CURRENT_SCHEMA_VERSION,
                    VillageDefenseDutyIds.slotId(
                            building.buildingId(), definition.slotKey()),
                    building.buildingId(),
                    building.dimensionId(),
                    definition.slotKey(),
                    position,
                    rotation.rotate(definition.localFacing()),
                    definition.kind(),
                    definition.weaponRequirement()));
        }
        return new VillageDefenseDutyLayout(
                VillageDefenseDutyLayout.CURRENT_SCHEMA_VERSION,
                building,
                rotation,
                slots);
    }

    /** Resolves an exact pack-authored layout without reading blocks or chunks. */
    public static VillageDefenseDutyLayout forBuilding(
            VillageBuildingIdentity building,
            Rotation rotation,
            List<VillageDefenseDutySlotDefinition> definitions) {
        Objects.requireNonNull(building, "building");
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(definitions, "definitions");
        if (definitions.isEmpty()
                || definitions.size() > VillageDefenseDutyLayout.MAXIMUM_SLOTS_PER_BUILDING) {
            throw new IllegalArgumentException(
                    "Pack-authored defense-duty slot count is outside its hard bound");
        }
        List<VillageDefenseDutySlot> slots = definitions.stream()
                .map(definition -> Objects.requireNonNull(
                        definition, "duty-slot definition").resolve(building, rotation))
                .toList();
        return new VillageDefenseDutyLayout(
                VillageDefenseDutyLayout.CURRENT_SCHEMA_VERSION,
                building,
                rotation,
                slots);
    }

    private static SlotDefinition definition(
            String slotKey,
            int memberIndex,
            int verticalOffset,
            Direction localFacing,
            VillageDefenseDutySlotKind kind,
            VillageDefenseDutyWeaponRequirement weaponRequirement) {
        return new SlotDefinition(
                slotKey,
                memberIndex,
                verticalOffset,
                localFacing,
                kind,
                weaponRequirement);
    }

    private record SlotDefinition(
            String slotKey,
            int memberIndex,
            int verticalOffset,
            Direction localFacing,
            VillageDefenseDutySlotKind kind,
            VillageDefenseDutyWeaponRequirement weaponRequirement) {
    }
}
