package com.gamergaming.villagerguardgunners.worldgen.post;

import com.gamergaming.villagerguardgunners.spawn.NaturalGuardDifficulty;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Immutable, prevalidated pool of weighted guard-post compositions. */
public final class GuardPostGroupPool {
    private static final long OPTIONAL_MEMBER_SALT = 0x9E3779B97F4A7C15L;

    private final ResourceLocation id;
    private final List<ResourceLocation> parents;
    private final List<GuardPostGroupVariant> variants;
    private final long identitySalt;

    public GuardPostGroupPool(
            ResourceLocation id,
            List<ResourceLocation> parents,
            List<GuardPostGroupVariant> variants) {
        this.id = Objects.requireNonNull(id, "id");
        this.parents = List.copyOf(Objects.requireNonNull(parents, "parents"));
        if (this.parents.size() > GuardPostGroupFormat.MAXIMUM_PARENTS
                || new HashSet<>(this.parents).size() != this.parents.size()
                || this.parents.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Group parents are duplicated, null, or over the limit");
        }
        this.variants = List.copyOf(Objects.requireNonNull(variants, "variants"));
        if (this.variants.isEmpty()
                || this.variants.size() > GuardPostGroupFormat.MAXIMUM_VARIANTS_PER_GROUP) {
            throw new IllegalArgumentException(
                    "Group variant count must be from 1 through "
                            + GuardPostGroupFormat.MAXIMUM_VARIANTS_PER_GROUP);
        }

        Set<String> names = new HashSet<>();
        long[] weights = new long[NaturalGuardDifficulty.values().length];
        for (GuardPostGroupVariant variant : this.variants) {
            Objects.requireNonNull(variant, "variant");
            if (!names.add(variant.name())) {
                throw new IllegalArgumentException("Duplicate group variant name " + variant.name());
            }
            for (NaturalGuardDifficulty difficulty : variant.difficulties()) {
                weights[difficulty.ordinal()] += variant.weight();
                if (weights[difficulty.ordinal()] > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException(
                            "Eligible group weight exceeds the supported limit for "
                                    + difficulty.name().toLowerCase());
                }
            }
        }
        identitySalt = stableHash64(id.toString());
    }

    public ResourceLocation id() {
        return id;
    }

    public List<ResourceLocation> parents() {
        return parents;
    }

    public List<GuardPostGroupVariant> variants() {
        return variants;
    }

    /**
     * Selects one variant and all passing optional members from a single seed.
     * No shared random source is retained, and all work is capped at 64 variants plus four members.
     */
    public Optional<GuardPostGroupSelection> select(
            NaturalGuardDifficulty difficulty,
            long selectionSeed) {
        Objects.requireNonNull(difficulty, "difficulty");
        if (difficulty == NaturalGuardDifficulty.PEACEFUL) {
            return Optional.empty();
        }

        int totalWeight = 0;
        for (GuardPostGroupVariant variant : variants) {
            if (variant.isEligible(difficulty)) {
                totalWeight = Math.addExact(totalWeight, variant.weight());
            }
        }
        if (totalWeight == 0) {
            return Optional.empty();
        }

        long baseSeed = mix64(selectionSeed ^ identitySalt ^ difficulty.ordinal());
        int remaining = (int) Long.remainderUnsigned(baseSeed, totalWeight);
        GuardPostGroupVariant selected = null;
        for (GuardPostGroupVariant variant : variants) {
            if (!variant.isEligible(difficulty)) {
                continue;
            }
            if (remaining < variant.weight()) {
                selected = variant;
                break;
            }
            remaining -= variant.weight();
        }
        if (selected == null) {
            throw new IllegalStateException("Prevalidated group weights did not select a variant");
        }

        List<GuardPostGroupMember> selectedMembers = new ArrayList<>(selected.members().size());
        for (int index = 0; index < selected.members().size(); index++) {
            GuardPostGroupMember member = selected.members().get(index);
            if (member.required()) {
                selectedMembers.add(member);
                continue;
            }
            long memberSeed = mix64(baseSeed + OPTIONAL_MEMBER_SALT * (index + 1L));
            int roll = (int) Long.remainderUnsigned(
                    memberSeed,
                    GuardPostGroupFormat.MAXIMUM_CHANCE_PERCENT);
            if (roll < member.chancePercent()) {
                selectedMembers.add(member);
            }
        }
        return Optional.of(new GuardPostGroupSelection(id, selected.name(), selectedMembers));
    }

    private static long stableHash64(String value) {
        long hash = 0xcbf29ce484222325L;
        for (byte character : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= character & 0xffL;
            hash *= 0x100000001b3L;
        }
        return mix64(hash);
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }
}
