package com.gamergaming.villagerguardgunners.defense.duty;

import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingIdentity;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;

/** Strict, closed NBT codec for one persistent building duty layout. */
public final class VillageDefenseDutyLayoutCodec {
    private static final String VERSION_TAG = "Version";
    private static final String BUILDING_ID_TAG = "BuildingId";
    private static final String ENTRY_ID_TAG = "EntryId";
    private static final String BUILDING_TYPE_TAG = "BuildingType";
    private static final String DIMENSION_TAG = "Dimension";
    private static final String ANCHOR_TAG = "Anchor";
    private static final String ROTATION_TAG = "Rotation";
    private static final String SLOTS_TAG = "Slots";
    private static final String SLOT_ID_TAG = "SlotId";
    private static final String SLOT_KEY_TAG = "SlotKey";
    private static final String POSITION_TAG = "Position";
    private static final String FACING_TAG = "Facing";
    private static final String KIND_TAG = "Kind";
    private static final String WEAPON_REQUIREMENT_TAG = "WeaponRequirement";

    private static final Set<String> LAYOUT_KEYS = Set.of(
            VERSION_TAG,
            BUILDING_ID_TAG,
            ENTRY_ID_TAG,
            BUILDING_TYPE_TAG,
            DIMENSION_TAG,
            ANCHOR_TAG,
            ROTATION_TAG,
            SLOTS_TAG);
    private static final Set<String> SLOT_KEYS = Set.of(
            VERSION_TAG,
            SLOT_ID_TAG,
            SLOT_KEY_TAG,
            POSITION_TAG,
            FACING_TAG,
            KIND_TAG,
            WEAPON_REQUIREMENT_TAG);

    public static CompoundTag encode(VillageDefenseDutyLayout layout) {
        CompoundTag encoded = new CompoundTag();
        VillageBuildingIdentity building = layout.building();
        encoded.putInt(VERSION_TAG, layout.schemaVersion());
        encoded.putUUID(BUILDING_ID_TAG, building.buildingId());
        encoded.putString(ENTRY_ID_TAG, building.entryId().toString());
        encoded.putString(BUILDING_TYPE_TAG, building.buildingType().serializedName());
        encoded.putString(DIMENSION_TAG, building.dimensionId().toString());
        encoded.putLong(ANCHOR_TAG, building.anchorPosition().asLong());
        encoded.putString(ROTATION_TAG, layout.rotation().getSerializedName());
        ListTag slots = new ListTag();
        for (VillageDefenseDutySlot slot : layout.slots()) {
            CompoundTag encodedSlot = new CompoundTag();
            encodedSlot.putInt(VERSION_TAG, slot.schemaVersion());
            encodedSlot.putUUID(SLOT_ID_TAG, slot.slotId());
            encodedSlot.putString(SLOT_KEY_TAG, slot.slotKey());
            encodedSlot.putLong(POSITION_TAG, slot.position().asLong());
            encodedSlot.putString(FACING_TAG, slot.facing().getName());
            encodedSlot.putString(KIND_TAG, slot.kind().serializedName());
            encodedSlot.putString(
                    WEAPON_REQUIREMENT_TAG,
                    slot.weaponRequirement().serializedName());
            slots.add(encodedSlot);
        }
        encoded.put(SLOTS_TAG, slots);
        return encoded;
    }

    public static @Nullable VillageDefenseDutyLayout decode(CompoundTag encoded) {
        if (encoded == null
                || !encoded.getAllKeys().equals(LAYOUT_KEYS)
                || !encoded.contains(VERSION_TAG, Tag.TAG_INT)
                || encoded.getInt(VERSION_TAG)
                        != VillageDefenseDutyLayout.CURRENT_SCHEMA_VERSION
                || !encoded.hasUUID(BUILDING_ID_TAG)
                || !encoded.contains(ENTRY_ID_TAG, Tag.TAG_STRING)
                || !encoded.contains(BUILDING_TYPE_TAG, Tag.TAG_STRING)
                || !encoded.contains(DIMENSION_TAG, Tag.TAG_STRING)
                || !encoded.contains(ANCHOR_TAG, Tag.TAG_LONG)
                || !encoded.contains(ROTATION_TAG, Tag.TAG_STRING)
                || !encoded.contains(SLOTS_TAG, Tag.TAG_LIST)) {
            return null;
        }
        ResourceLocation entryId = canonicalId(encoded.getString(ENTRY_ID_TAG));
        ResourceLocation dimensionId = canonicalId(encoded.getString(DIMENSION_TAG));
        VillageBuildingType buildingType = VillageBuildingType.byName(
                encoded.getString(BUILDING_TYPE_TAG)).orElse(null);
        Rotation rotation = Arrays.stream(Rotation.values())
                .filter(value -> value.getSerializedName().equals(
                        encoded.getString(ROTATION_TAG)))
                .findFirst()
                .orElse(null);
        Tag rawSlots = encoded.get(SLOTS_TAG);
        if (entryId == null
                || dimensionId == null
                || buildingType == null
                || rotation == null
                || !(rawSlots instanceof ListTag encodedSlots)
                || encodedSlots.isEmpty()
                || encodedSlots.size()
                        > VillageDefenseDutyLayout.MAXIMUM_SLOTS_PER_BUILDING
                || encodedSlots.getElementType() != Tag.TAG_COMPOUND) {
            return null;
        }
        try {
            UUID buildingId = encoded.getUUID(BUILDING_ID_TAG);
            VillageBuildingIdentity building = new VillageBuildingIdentity(
                    VillageBuildingIdentity.CURRENT_SCHEMA_VERSION,
                    buildingId,
                    entryId,
                    buildingType,
                    dimensionId,
                    BlockPos.of(encoded.getLong(ANCHOR_TAG)));
            List<VillageDefenseDutySlot> slots = new ArrayList<>(encodedSlots.size());
            for (Tag rawSlot : encodedSlots) {
                if (!(rawSlot instanceof CompoundTag slot)
                        || !slot.getAllKeys().equals(SLOT_KEYS)
                        || !slot.contains(VERSION_TAG, Tag.TAG_INT)
                        || slot.getInt(VERSION_TAG)
                                != VillageDefenseDutySlot.CURRENT_SCHEMA_VERSION
                        || !slot.hasUUID(SLOT_ID_TAG)
                        || !slot.contains(SLOT_KEY_TAG, Tag.TAG_STRING)
                        || !slot.contains(POSITION_TAG, Tag.TAG_LONG)
                        || !slot.contains(FACING_TAG, Tag.TAG_STRING)
                        || !slot.contains(KIND_TAG, Tag.TAG_STRING)
                        || !slot.contains(WEAPON_REQUIREMENT_TAG, Tag.TAG_STRING)) {
                    return null;
                }
                Direction facing = Direction.byName(slot.getString(FACING_TAG));
                VillageDefenseDutySlotKind kind = VillageDefenseDutySlotKind.byName(
                        slot.getString(KIND_TAG)).orElse(null);
                VillageDefenseDutyWeaponRequirement weaponRequirement =
                        VillageDefenseDutyWeaponRequirement.byName(
                                slot.getString(WEAPON_REQUIREMENT_TAG)).orElse(null);
                if (facing == null || kind == null || weaponRequirement == null) {
                    return null;
                }
                slots.add(new VillageDefenseDutySlot(
                        VillageDefenseDutySlot.CURRENT_SCHEMA_VERSION,
                        slot.getUUID(SLOT_ID_TAG),
                        buildingId,
                        dimensionId,
                        slot.getString(SLOT_KEY_TAG),
                        BlockPos.of(slot.getLong(POSITION_TAG)),
                        facing,
                        kind,
                        weaponRequirement));
            }
            return new VillageDefenseDutyLayout(
                    VillageDefenseDutyLayout.CURRENT_SCHEMA_VERSION,
                    building,
                    rotation,
                    slots);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static @Nullable ResourceLocation canonicalId(String encoded) {
        ResourceLocation id = ResourceLocation.tryParse(encoded);
        return id != null && id.toString().equals(encoded) ? id : null;
    }

    private VillageDefenseDutyLayoutCodec() {
    }
}
