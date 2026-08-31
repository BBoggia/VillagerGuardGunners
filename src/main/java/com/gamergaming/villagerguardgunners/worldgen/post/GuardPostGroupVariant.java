package com.gamergaming.villagerguardgunners.worldgen.post;

import com.gamergaming.villagerguardgunners.spawn.NaturalGuardDifficulty;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** One weighted, difficulty-gated guard-post composition. */
public record GuardPostGroupVariant(
        String name,
        int weight,
        Set<NaturalGuardDifficulty> difficulties,
        List<GuardPostGroupMember> members) {
    private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9._/-]{1,64}");

    public GuardPostGroupVariant {
        if (name == null || !VALID_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Variant name must be a 1-64 character lowercase path key");
        }
        if (weight <= 0) {
            throw new IllegalArgumentException("Variant weight must be positive");
        }
        difficulties = Set.copyOf(Objects.requireNonNull(difficulties, "difficulties"));
        if (difficulties.isEmpty() || difficulties.contains(NaturalGuardDifficulty.PEACEFUL)) {
            throw new IllegalArgumentException(
                    "Variants must target at least one non-Peaceful difficulty");
        }
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        if (members.isEmpty()
                || members.size() > GuardPostGroupFormat.MAXIMUM_MEMBERS_PER_VARIANT) {
            throw new IllegalArgumentException(
                    "Variant member count must be from 1 through "
                            + GuardPostGroupFormat.MAXIMUM_MEMBERS_PER_VARIANT);
        }
        if (members.stream().noneMatch(GuardPostGroupMember::required)) {
            throw new IllegalArgumentException(
                    "A variant must contain at least one required member");
        }
    }

    public boolean isEligible(NaturalGuardDifficulty difficulty) {
        return difficulties.contains(Objects.requireNonNull(difficulty, "difficulty"));
    }
}
