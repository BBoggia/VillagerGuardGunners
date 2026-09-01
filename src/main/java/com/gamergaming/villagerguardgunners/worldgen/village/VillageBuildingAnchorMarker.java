package com.gamergaming.villagerguardgunners.worldgen.village;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Closed grammar for the one optional anchor marker in a building template. */
public record VillageBuildingAnchorMarker(VillageBuildingType buildingType) {
    public static final String PREFIX = "village_anchor:";
    public static final int MAXIMUM_METADATA_LENGTH = 64;

    public static Optional<VillageBuildingAnchorMarker> parse(@Nullable String metadata) {
        if (metadata == null
                || metadata.length() > MAXIMUM_METADATA_LENGTH
                || !metadata.startsWith(PREFIX)) {
            return Optional.empty();
        }
        return VillageBuildingType.byName(metadata.substring(PREFIX.length()))
                .map(VillageBuildingAnchorMarker::new);
    }

    public static boolean isAnchorMetadata(@Nullable String metadata) {
        return metadata != null && metadata.startsWith("village_anchor");
    }

    static boolean validateTemplateMarkers(
            List<StructureTemplate.StructureBlockInfo> markers,
            VillageBuildingType expectedType) {
        int anchors = 0;
        for (StructureTemplate.StructureBlockInfo marker : markers) {
            String metadata = metadata(marker.nbt());
            if (!isAnchorMetadata(metadata)) {
                continue;
            }
            anchors++;
            Optional<VillageBuildingAnchorMarker> parsed = parse(metadata);
            if (anchors > 1
                    || parsed.isEmpty()
                    || parsed.get().buildingType() != expectedType) {
                return false;
            }
        }
        return true;
    }

    static @Nullable String metadata(@Nullable CompoundTag tag) {
        return tag != null && tag.contains("metadata", Tag.TAG_STRING)
                ? tag.getString("metadata")
                : null;
    }
}
