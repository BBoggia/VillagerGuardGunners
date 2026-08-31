package com.gamergaming.villagerguardgunners.command;

import java.util.List;

/** Stable command and output-bound contract for the loadout diagnostic subtree. */
public final class NaturalGuardLoadoutCommandContract {
    public static final int REQUIRED_PERMISSION_LEVEL = 2;
    public static final int ENTRIES_PER_PAGE = 8;
    public static final int MAX_VALIDATION_ISSUES_IN_SUMMARY = 20;
    public static final int MAX_PREVIEW_ENTRIES = 20;
    public static final int MAX_DETAIL_LENGTH = 512;

    public static final String ROOT_LITERAL = "loadouts";
    public static final String VALIDATE_LITERAL = "validate";
    public static final String INSPECT_LITERAL = "inspect";
    public static final String PREVIEW_LITERAL = "preview";

    public static final List<String> DOCUMENTED_PATHS = List.of(
            "/vgg loadouts validate [pool]",
            "/vgg loadouts inspect <pool> [page]",
            "/vgg loadouts preview <difficulty> <spawn_source> [pool]");

    private NaturalGuardLoadoutCommandContract() {
    }
}
