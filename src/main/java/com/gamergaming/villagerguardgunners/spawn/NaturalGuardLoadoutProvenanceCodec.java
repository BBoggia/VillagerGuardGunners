package com.gamergaming.villagerguardgunners.spawn;

import com.gamergaming.villagerguardgunners.api.GuardSidearmAccess;
import com.tacz.guns.api.item.IGun;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Strict NBT codec and authenticity check for naturally issued guard guns.
 *
 * <p>The guard and its gun carry matching copies of the same issuance record.
 * A record is active only while both copies decode under the current schema,
 * agree exactly, and TaCZ still reports the recorded gun ID. This prevents a
 * manually supplied replacement gun, copied item marker, or malformed future
 * data from reserving a natural-loadout population slot.</p>
 */
public final class NaturalGuardLoadoutProvenanceCodec {
    public static final String GUARD_PROVENANCE_TAG =
            "villagerguardgunners:natural_loadout";
    public static final String GUN_PROVENANCE_TAG =
            "villagerguardgunners:natural_loadout_issuance";

    private static final String VERSION_TAG = "Version";
    private static final String POOL_TAG = "Pool";
    private static final String GUN_TAG = "Gun";
    private static final String ISSUANCE_ID_TAG = "IssuanceId";
    private static final String DIFFICULTY_TAG = "Difficulty";
    private static final String SPAWN_SOURCE_TAG = "SpawnSource";
    private static final Set<String> RECORD_KEYS = Set.of(
            VERSION_TAG,
            POOL_TAG,
            GUN_TAG,
            ISSUANCE_ID_TAG,
            DIFFICULTY_TAG,
            SPAWN_SOURCE_TAG);
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private NaturalGuardLoadoutProvenanceCodec() {
    }

    public static void writeGuardRecord(
            CompoundTag guardTag,
            @Nullable NaturalGuardLoadoutProvenance provenance) {
        if (provenance == null) {
            guardTag.remove(GUARD_PROVENANCE_TAG);
            return;
        }
        guardTag.put(GUARD_PROVENANCE_TAG, encode(provenance));
    }

    @Nullable
    public static NaturalGuardLoadoutProvenance readGuardRecord(CompoundTag guardTag) {
        if (!guardTag.contains(GUARD_PROVENANCE_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }
        return decode(guardTag.getCompound(GUARD_PROVENANCE_TAG));
    }

    /** Stamps the freshly built gun before the issuance transaction commits. */
    public static boolean stampGun(
            ItemStack gunStack,
            NaturalGuardLoadoutProvenance provenance) {
        if (!isTaCZGun(gunStack, provenance.gunId())) {
            return false;
        }
        gunStack.getOrCreateTag().put(GUN_PROVENANCE_TAG, encode(provenance));
        return provenance.equals(readGunRecord(gunStack));
    }

    /**
     * Returns whether the current item is the exact natural issuance recorded
     * on this guard. This is intentionally a one-shot/load or spawn-query check,
     * not combat-tick work.
     */
    public static boolean matchesIssuedGun(
            @Nullable ItemStack gunStack,
            @Nullable NaturalGuardLoadoutProvenance provenance) {
        if (provenance == null
                || provenance.schemaVersion()
                        != NaturalGuardLoadoutProvenance.CURRENT_SCHEMA_VERSION
                || !isTaCZGun(gunStack, provenance.gunId())) {
            return false;
        }
        return provenance.equals(readGunRecord(gunStack));
    }

    /**
     * Authenticates the active primary role rather than assuming it is always
     * in the main hand. A valid sidearm-active layout keeps the issued primary
     * in private storage; contradictory or unreadable layouts fail closed.
     */
    public static boolean matchesIssuedGun(
            Guard guard,
            @Nullable NaturalGuardLoadoutProvenance provenance) {
        if (guard == null) {
            return false;
        }
        try {
            return matchesIssuedGun(
                    GuardSidearmAccess.getEffectivePrimaryCopy(guard),
                    provenance);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Nullable
    private static NaturalGuardLoadoutProvenance readGunRecord(
            @Nullable ItemStack gunStack) {
        if (gunStack == null || gunStack.isEmpty()) {
            return null;
        }
        CompoundTag itemTag = gunStack.getTag();
        if (itemTag == null || !itemTag.contains(GUN_PROVENANCE_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }
        return decode(itemTag.getCompound(GUN_PROVENANCE_TAG));
    }

    private static boolean isTaCZGun(
            @Nullable ItemStack gunStack,
            ResourceLocation expectedGunId) {
        if (gunStack == null
                || gunStack.isEmpty()
                || gunStack.getCount() != 1
                || !(gunStack.getItem() instanceof IGun gun)) {
            return false;
        }
        try {
            return expectedGunId.equals(gun.getGunId(gunStack));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static CompoundTag encode(NaturalGuardLoadoutProvenance provenance) {
        CompoundTag encoded = new CompoundTag();
        encoded.putInt(VERSION_TAG, provenance.schemaVersion());
        encoded.putString(POOL_TAG, provenance.poolId().toString());
        encoded.putString(GUN_TAG, provenance.gunId().toString());
        encoded.putUUID(ISSUANCE_ID_TAG, provenance.issuanceId());
        encoded.putString(DIFFICULTY_TAG, provenance.difficulty().name());
        encoded.putString(SPAWN_SOURCE_TAG, provenance.spawnSource().name());
        return encoded;
    }

    @Nullable
    private static NaturalGuardLoadoutProvenance decode(CompoundTag encoded) {
        if (!encoded.getAllKeys().equals(RECORD_KEYS)
                || !encoded.contains(VERSION_TAG, Tag.TAG_INT)
                || encoded.getInt(VERSION_TAG)
                        != NaturalGuardLoadoutProvenance.CURRENT_SCHEMA_VERSION
                || !encoded.contains(POOL_TAG, Tag.TAG_STRING)
                || !encoded.contains(GUN_TAG, Tag.TAG_STRING)
                || !encoded.hasUUID(ISSUANCE_ID_TAG)
                || !encoded.contains(DIFFICULTY_TAG, Tag.TAG_STRING)
                || !encoded.contains(SPAWN_SOURCE_TAG, Tag.TAG_STRING)) {
            return null;
        }

        ResourceLocation poolId = parseCanonicalId(encoded.getString(POOL_TAG));
        ResourceLocation gunId = parseCanonicalId(encoded.getString(GUN_TAG));
        UUID issuanceId;
        NaturalGuardDifficulty difficulty;
        NaturalGuardSpawnSource spawnSource;
        try {
            issuanceId = encoded.getUUID(ISSUANCE_ID_TAG);
            difficulty = NaturalGuardDifficulty.valueOf(encoded.getString(DIFFICULTY_TAG));
            spawnSource = NaturalGuardSpawnSource.valueOf(encoded.getString(SPAWN_SOURCE_TAG));
        } catch (RuntimeException exception) {
            return null;
        }
        if (poolId == null || gunId == null || NIL_UUID.equals(issuanceId)) {
            return null;
        }
        return NaturalGuardLoadoutProvenance.issued(
                poolId,
                gunId,
                issuanceId,
                difficulty,
                spawnSource);
    }

    @Nullable
    private static ResourceLocation parseCanonicalId(String value) {
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        return parsed != null && parsed.toString().equals(value) ? parsed : null;
    }
}
