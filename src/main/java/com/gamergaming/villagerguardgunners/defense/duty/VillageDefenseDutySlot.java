package com.gamergaming.villagerguardgunners.defense.duty;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/** Immutable, persistent destination authored by one Village Defense Building. */
public record VillageDefenseDutySlot(
        int schemaVersion,
        UUID slotId,
        UUID buildingId,
        ResourceLocation dimensionId,
        String slotKey,
        BlockPos position,
        Direction facing,
        VillageDefenseDutySlotKind kind,
        VillageDefenseDutyWeaponRequirement weaponRequirement) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MAXIMUM_SLOT_KEY_LENGTH = 32;

    private static final UUID NIL_UUID = new UUID(0L, 0L);
    private static final Pattern SLOT_KEY_PATTERN = Pattern.compile("[a-z0-9_]+");

    public VillageDefenseDutySlot {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported defense-duty slot schema");
        }
        slotId = Objects.requireNonNull(slotId, "slotId");
        buildingId = Objects.requireNonNull(buildingId, "buildingId");
        if (NIL_UUID.equals(slotId) || NIL_UUID.equals(buildingId)) {
            throw new IllegalArgumentException("Defense-duty identities cannot be nil UUIDs");
        }
        Objects.requireNonNull(dimensionId, "dimensionId");
        Objects.requireNonNull(slotKey, "slotKey");
        if (slotKey.isEmpty()
                || slotKey.length() > MAXIMUM_SLOT_KEY_LENGTH
                || !SLOT_KEY_PATTERN.matcher(slotKey).matches()) {
            throw new IllegalArgumentException("Defense-duty slot key is not canonical");
        }
        if (!VillageDefenseDutyIds.slotId(buildingId, slotKey).equals(slotId)) {
            throw new IllegalArgumentException("Defense-duty slot ID is not deterministic");
        }
        position = Objects.requireNonNull(position, "position").immutable();
        facing = Objects.requireNonNull(facing, "facing");
        if (facing.getAxis().isVertical()) {
            throw new IllegalArgumentException("Defense-duty facing must be horizontal");
        }
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(weaponRequirement, "weaponRequirement");
    }
}
