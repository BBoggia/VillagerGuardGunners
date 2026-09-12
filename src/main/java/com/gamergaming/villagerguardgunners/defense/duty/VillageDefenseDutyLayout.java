package com.gamergaming.villagerguardgunners.defense.duty;

import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingIdentity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;

/** One building's exact, rotation-aware set of persistent duty destinations. */
public record VillageDefenseDutyLayout(
        int schemaVersion,
        VillageBuildingIdentity building,
        Rotation rotation,
        List<VillageDefenseDutySlot> slots) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MAXIMUM_SLOTS_PER_BUILDING = 4;
    public static final int MAXIMUM_SLOT_OFFSET = 16;

    public VillageDefenseDutyLayout {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported defense-duty layout schema");
        }
        Objects.requireNonNull(building, "building");
        Objects.requireNonNull(rotation, "rotation");
        if (slots == null || slots.isEmpty()
                || slots.size() > MAXIMUM_SLOTS_PER_BUILDING) {
            throw new IllegalArgumentException(
                    "Defense-duty slot count is outside its hard bound");
        }
        ArrayList<VillageDefenseDutySlot> normalized = new ArrayList<>(slots);
        Set<UUID> slotIds = new HashSet<>();
        Set<String> slotKeys = new HashSet<>();
        Set<BlockPos> positions = new HashSet<>();
        BlockPos anchor = building.anchorPosition();
        for (VillageDefenseDutySlot slot : normalized) {
            Objects.requireNonNull(slot, "duty slot");
            BlockPos position = slot.position();
            if (!building.buildingId().equals(slot.buildingId())
                    || !building.dimensionId().equals(slot.dimensionId())
                    || !slotIds.add(slot.slotId())
                    || !slotKeys.add(slot.slotKey())
                    || !positions.add(position)
                    || Math.abs(position.getX() - anchor.getX()) > MAXIMUM_SLOT_OFFSET
                    || Math.abs(position.getY() - anchor.getY()) > MAXIMUM_SLOT_OFFSET
                    || Math.abs(position.getZ() - anchor.getZ()) > MAXIMUM_SLOT_OFFSET) {
                throw new IllegalArgumentException(
                        "Defense-duty slot conflicts with its owning building");
            }
        }
        slots = List.copyOf(normalized);
    }
}
