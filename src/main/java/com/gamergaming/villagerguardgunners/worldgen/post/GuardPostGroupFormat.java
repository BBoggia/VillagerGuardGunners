package com.gamergaming.villagerguardgunners.worldgen.post;

/** Hard bounds and the on-disk version for guard-post group data. */
public final class GuardPostGroupFormat {
    public static final int VERSION = 1;
    public static final int MAXIMUM_DOCUMENTS = 128;
    public static final int MAXIMUM_DOCUMENT_CHARACTERS = 65_536;
    public static final int MAXIMUM_JSON_DEPTH = 32;
    public static final int MAXIMUM_PARENTS = 8;
    /** Maximum documents in one inheritance path, including the selected document. */
    public static final int MAXIMUM_INHERITANCE_DEPTH = 16;
    public static final int MAXIMUM_VARIANTS_PER_GROUP = 64;
    public static final int MAXIMUM_MEMBERS_PER_VARIANT = 4;
    public static final int MAXIMUM_CHANCE_PERCENT = 100;

    private GuardPostGroupFormat() {
    }
}
