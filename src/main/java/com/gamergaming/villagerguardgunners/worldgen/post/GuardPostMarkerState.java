package com.gamergaming.villagerguardgunners.worldgen.post;

import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostMarker.Action;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Mutable piece-owned state that bounds and persists one-time marker work. */
public final class GuardPostMarkerState {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MAXIMUM_RECOGNIZED_MARKERS = 8;

    private static final String VERSION_TAG = "Version";
    private static final String ENABLED_TAG = "Enabled";
    private static final String SELECTION_SEED_TAG = "SelectionSeed";
    private static final String CLAIMED_MARKERS_TAG = "ClaimedMarkers";
    private static final String CLAIMED_ACTIONS_TAG = "ClaimedActions";
    private static final String POST_ID_TAG = "PostId";
    private static final String SELECTION_TAG = "Selection";
    private static final String GROUP_TAG = "Group";
    private static final String VARIANT_TAG = "Variant";
    private static final String SPAWNED_GUARDS_TAG = "SpawnedGuards";
    private static final Set<String> ENABLED_KEYS = Set.of(
            VERSION_TAG,
            ENABLED_TAG,
            SELECTION_SEED_TAG,
            CLAIMED_MARKERS_TAG,
            CLAIMED_ACTIONS_TAG,
            POST_ID_TAG,
            SELECTION_TAG,
            SPAWNED_GUARDS_TAG);
    private static final Set<String> SELECTION_KEYS = Set.of(GROUP_TAG, VARIANT_TAG);
    private static final UUID NIL_UUID = new UUID(0L, 0L);
    private static final UUID VALIDATION_UUID = new UUID(1L, 1L);

    private final boolean enabled;
    private final long selectionSeed;
    private final LinkedHashSet<Long> claimedMarkers;
    private int claimedActions;
    @Nullable
    private UUID postId;
    @Nullable
    private ResourceLocation selectedGroup;
    @Nullable
    private String selectedVariant;
    private int spawnedGuards;

    private GuardPostMarkerState(
            boolean enabled,
            long selectionSeed,
            Set<Long> claimedMarkers,
            int claimedActions,
            @Nullable UUID postId,
            @Nullable ResourceLocation selectedGroup,
            @Nullable String selectedVariant,
            int spawnedGuards) {
        this.enabled = enabled;
        this.selectionSeed = selectionSeed;
        this.claimedMarkers = new LinkedHashSet<>(claimedMarkers);
        this.claimedActions = claimedActions;
        this.postId = postId;
        this.selectedGroup = selectedGroup;
        this.selectedVariant = selectedVariant;
        this.spawnedGuards = spawnedGuards;
    }

    public static GuardPostMarkerState fresh(long selectionSeed) {
        return new GuardPostMarkerState(
                true,
                selectionSeed,
                Set.of(),
                0,
                null,
                null,
                null,
                0);
    }

    /** Missing, future, or malformed piece state is disabled to prevent retroactive work. */
    public static GuardPostMarkerState load(@Nullable CompoundTag encoded) {
        if (encoded == null
                || !encoded.contains(VERSION_TAG, Tag.TAG_INT)
                || encoded.getInt(VERSION_TAG) != CURRENT_SCHEMA_VERSION
                || !encoded.contains(ENABLED_TAG, Tag.TAG_BYTE)) {
            return disabled();
        }
        boolean enabled = encoded.getBoolean(ENABLED_TAG);
        if (!enabled) {
            return disabled();
        }
        if (!encoded.getAllKeys().stream().allMatch(ENABLED_KEYS::contains)
                || !encoded.contains(SELECTION_SEED_TAG, Tag.TAG_LONG)
                || !encoded.contains(CLAIMED_MARKERS_TAG, Tag.TAG_LONG_ARRAY)
                || !encoded.contains(CLAIMED_ACTIONS_TAG, Tag.TAG_INT)
                || !encoded.contains(SPAWNED_GUARDS_TAG, Tag.TAG_INT)) {
            return disabled();
        }

        long[] encodedMarkers = encoded.getLongArray(CLAIMED_MARKERS_TAG);
        if (encodedMarkers.length > MAXIMUM_RECOGNIZED_MARKERS) {
            return disabled();
        }
        LinkedHashSet<Long> markers = new LinkedHashSet<>();
        for (long marker : encodedMarkers) {
            if (!markers.add(marker)) {
                return disabled();
            }
        }
        int actions = encoded.getInt(CLAIMED_ACTIONS_TAG);
        int spawned = encoded.getInt(SPAWNED_GUARDS_TAG);
        if ((actions & ~Action.supportedMask()) != 0
                || (actions != 0 && markers.isEmpty())
                || spawned < 0
                || spawned > GuardPostGroupFormat.MAXIMUM_MEMBERS_PER_VARIANT) {
            return disabled();
        }

        UUID postId = null;
        if (encoded.contains(POST_ID_TAG)) {
            if (!encoded.hasUUID(POST_ID_TAG)) {
                return disabled();
            }
            try {
                postId = encoded.getUUID(POST_ID_TAG);
            } catch (RuntimeException exception) {
                return disabled();
            }
            if (NIL_UUID.equals(postId)) {
                return disabled();
            }
        }

        ResourceLocation group = null;
        String variant = null;
        if (encoded.contains(SELECTION_TAG)) {
            if (!encoded.contains(SELECTION_TAG, Tag.TAG_COMPOUND)) {
                return disabled();
            }
            CompoundTag selection = encoded.getCompound(SELECTION_TAG);
            if (!selection.getAllKeys().equals(SELECTION_KEYS)
                    || !selection.contains(GROUP_TAG, Tag.TAG_STRING)
                    || !selection.contains(VARIANT_TAG, Tag.TAG_STRING)) {
                return disabled();
            }
            String encodedGroup = selection.getString(GROUP_TAG);
            group = ResourceLocation.tryParse(encodedGroup);
            variant = selection.getString(VARIANT_TAG);
            if (group == null || !group.toString().equals(encodedGroup)) {
                return disabled();
            }
            try {
                new GuardPostIdentity(
                        GuardPostIdentity.CURRENT_SCHEMA_VERSION,
                        postId == null ? VALIDATION_UUID : postId,
                        group,
                        variant,
                        0);
            } catch (RuntimeException exception) {
                return disabled();
            }
        }
        if ((group != null && ((actions & Action.GUARD_POST.mask()) == 0 || postId == null))
                || (spawned > 0 && (group == null || postId == null))) {
            return disabled();
        }
        return new GuardPostMarkerState(
                true,
                encoded.getLong(SELECTION_SEED_TAG),
                markers,
                actions,
                postId,
                group,
                variant,
                spawned);
    }

    private static GuardPostMarkerState disabled() {
        return new GuardPostMarkerState(
                false,
                0L,
                Set.of(),
                Action.supportedMask(),
                null,
                null,
                null,
                0);
    }

    public synchronized CompoundTag save() {
        CompoundTag encoded = new CompoundTag();
        encoded.putInt(VERSION_TAG, CURRENT_SCHEMA_VERSION);
        encoded.putBoolean(ENABLED_TAG, enabled);
        if (!enabled) {
            return encoded;
        }
        encoded.putLong(SELECTION_SEED_TAG, selectionSeed);
        List<Long> positions = new ArrayList<>(claimedMarkers);
        encoded.putLongArray(CLAIMED_MARKERS_TAG, positions);
        encoded.putInt(CLAIMED_ACTIONS_TAG, claimedActions);
        encoded.putInt(SPAWNED_GUARDS_TAG, spawnedGuards);
        if (postId != null) {
            encoded.putUUID(POST_ID_TAG, postId);
        }
        if (selectedGroup != null && selectedVariant != null) {
            CompoundTag selection = new CompoundTag();
            selection.putString(GROUP_TAG, selectedGroup.toString());
            selection.putString(VARIANT_TAG, selectedVariant);
            encoded.put(SELECTION_TAG, selection);
        }
        return encoded;
    }

    public synchronized ClaimResult claim(BlockPos position, Action action) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(action, "action");
        if (!enabled) {
            return ClaimResult.DISABLED;
        }
        long markerKey = position.asLong();
        if (claimedMarkers.contains(markerKey)) {
            return ClaimResult.ALREADY_CLAIMED;
        }
        if (claimedMarkers.size() >= MAXIMUM_RECOGNIZED_MARKERS) {
            return ClaimResult.LIMIT_REACHED;
        }
        claimedMarkers.add(markerKey);
        if ((claimedActions & action.mask()) != 0) {
            return ClaimResult.DUPLICATE_ACTION;
        }
        claimedActions |= action.mask();
        return ClaimResult.CLAIMED;
    }

    public synchronized UUID ensurePostId(
            long worldSeed,
            ResourceLocation dimensionId,
            BlockPos templateOrigin) {
        if (!enabled) {
            throw new IllegalStateException("Disabled marker state has no post identity");
        }
        if (postId == null) {
            postId = GuardPostIds.postId(worldSeed, dimensionId, templateOrigin);
        }
        return postId;
    }

    public synchronized void recordSelection(GuardPostGroupSelection selection) {
        Objects.requireNonNull(selection, "selection");
        if (!enabled || (claimedActions & Action.GUARD_POST.mask()) == 0) {
            throw new IllegalStateException("Cannot record an unclaimed guard-post selection");
        }
        if (selectedGroup == null) {
            selectedGroup = selection.groupId();
            selectedVariant = selection.variantName();
        } else if (!selectedGroup.equals(selection.groupId())
                || !selectedVariant.equals(selection.variantName())) {
            throw new IllegalStateException("Guard-post selection cannot be replaced");
        }
    }

    public synchronized void recordSpawnedGuard() {
        if (!enabled
                || selectedGroup == null
                || postId == null
                || spawnedGuards >= GuardPostGroupFormat.MAXIMUM_MEMBERS_PER_VARIANT) {
            throw new IllegalStateException("Cannot record another generated guard");
        }
        spawnedGuards++;
    }

    public boolean enabled() {
        return enabled;
    }

    public long selectionSeed() {
        return selectionSeed;
    }

    public synchronized int claimedMarkerCount() {
        return claimedMarkers.size();
    }

    public synchronized int spawnedGuards() {
        return spawnedGuards;
    }

    public synchronized @Nullable UUID postId() {
        return postId;
    }

    public synchronized @Nullable ResourceLocation selectedGroup() {
        return selectedGroup;
    }

    public synchronized @Nullable String selectedVariant() {
        return selectedVariant;
    }

    public enum ClaimResult {
        CLAIMED,
        ALREADY_CLAIMED,
        DUPLICATE_ACTION,
        LIMIT_REACHED,
        DISABLED
    }
}
