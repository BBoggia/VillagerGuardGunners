package com.gamergaming.villagerguardgunners.orders;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.orders.GuardOrderTemplate.DecodeResult;
import com.gamergaming.villagerguardgunners.orders.GuardOrderTemplate.DecodeStatus;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.EngagementOrder;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.MovementOrder;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.RangeOrder;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.TacticalOrder;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.ToggleOrder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class GuardOrderTemplateTest {
    private static final GuardOrders COMPLETE_ORDERS = new GuardOrders(
            EngagementOrder.HOSTILE_MOBS_ONLY,
            TacticalOrder.SUPPORT,
            ToggleOrder.DISABLED,
            ToggleOrder.ENABLED,
            ToggleOrder.DISABLED,
            MovementOrder.PATROL_BOUND,
            RangeOrder.BLOCKS_48);

    @Test
    void completeRawOrdersRoundTripWithoutResolvingInheritedBehavior() {
        GuardOrders rawOrders = GuardOrders.DEFAULT
                .withEngagement(EngagementOrder.INHERIT)
                .withTactics(TacticalOrder.INHERIT)
                .withExplosives(ToggleOrder.ENABLED)
                .withAmmoPickup(ToggleOrder.DISABLED)
                .withAmmoSharing(ToggleOrder.INHERIT)
                .withMovement(MovementOrder.HOLD_POSITION)
                .withMaximumRange(RangeOrder.BLOCKS_32);
        CompoundTag itemTag = new CompoundTag();

        GuardOrderTemplate.writeTo(itemTag, rawOrders);
        DecodeResult decoded = GuardOrderTemplate.readFrom(itemTag);

        assertAll(
                () -> assertEquals(DecodeStatus.VALID, decoded.status()),
                () -> assertTrue(decoded.isValid()),
                () -> assertEquals(rawOrders, decoded.orders()),
                () -> assertEquals(-1, decoded.storedVersion()));
    }

    @Test
    void versionTwoRoundTripsRoleWhileVersionOneLeavesExistingRoleUntouched() {
        ResourceLocation role = ResourceLocation.fromNamespaceAndPath("test", "marksman");
        CompoundTag current = new CompoundTag();
        GuardOrderTemplate.writeTo(current, COMPLETE_ORDERS, role);
        DecodeResult currentDecoded = GuardOrderTemplate.readFrom(current);

        CompoundTag legacy = encoded(COMPLETE_ORDERS);
        templateTag(legacy).putInt("Version", GuardOrderTemplate.LEGACY_DATA_VERSION);
        templateTag(legacy).remove("Role");
        DecodeResult legacyDecoded = GuardOrderTemplate.readFrom(legacy);

        assertAll(
                () -> assertEquals(role, currentDecoded.roleId()),
                () -> assertTrue(currentDecoded.includesRoleAssignment()),
                () -> assertNull(legacyDecoded.roleId()),
                () -> assertFalse(legacyDecoded.includesRoleAssignment()),
                () -> assertEquals(COMPLETE_ORDERS, legacyDecoded.orders()));
    }

    @Test
    void missingTemplateIsBlankButWrongTagTypeIsCorrupt() {
        CompoundTag unrelatedItemTag = new CompoundTag();
        unrelatedItemTag.putString("display", "unrelated");
        CompoundTag wrongType = new CompoundTag();
        wrongType.putString(GuardOrderTemplate.TEMPLATE_TAG, "not a compound");

        assertAll(
                () -> assertEquals(
                        DecodeStatus.BLANK,
                        GuardOrderTemplate.readFrom(null).status()),
                () -> assertEquals(
                        DecodeStatus.BLANK,
                        GuardOrderTemplate.readFrom(new CompoundTag()).status()),
                () -> assertEquals(
                        DecodeStatus.BLANK,
                        GuardOrderTemplate.readFrom(unrelatedItemTag).status()),
                () -> assertEquals(
                        DecodeStatus.CORRUPT,
                        GuardOrderTemplate.readFrom(wrongType).status()));
    }

    @Test
    void writingAndRewritingPreservesUnrelatedItemData() {
        CompoundTag itemTag = new CompoundTag();
        CompoundTag display = new CompoundTag();
        display.putString("Name", "Custom Marksman Ledger");
        itemTag.put("display", display);
        itemTag.putInt("UnrelatedValue", 73);

        GuardOrderTemplate.writeTo(itemTag, GuardOrders.DEFAULT);
        GuardOrderTemplate.writeTo(itemTag, COMPLETE_ORDERS);

        DecodeResult decoded = GuardOrderTemplate.readFrom(itemTag);
        assertAll(
                () -> assertEquals(COMPLETE_ORDERS, decoded.orders()),
                () -> assertEquals("Custom Marksman Ledger", itemTag
                        .getCompound("display")
                        .getString("Name")),
                () -> assertEquals(73, itemTag.getInt("UnrelatedValue")));
    }

    @Test
    void incompleteWronglyTypedAndUnknownFieldsAreRejectedAtomically() {
        CompoundTag missingField = encoded(COMPLETE_ORDERS);
        ordersTag(missingField).remove("AmmoSharing");

        CompoundTag wrongType = encoded(COMPLETE_ORDERS);
        ordersTag(wrongType).putInt("Movement", 2);

        CompoundTag unknownValue = encoded(COMPLETE_ORDERS);
        ordersTag(unknownValue).putString("Tactics", "future_profile");

        CompoundTag whitespaceKnownValue = encoded(COMPLETE_ORDERS);
        ordersTag(whitespaceKnownValue).putString("Tactics", "  marksman  ");

        assertAll(
                () -> assertCorrupt(missingField),
                () -> assertCorrupt(wrongType),
                () -> assertCorrupt(unknownValue),
                () -> assertEquals(
                        TacticalOrder.MARKSMAN,
                        GuardOrderTemplate.readFrom(whitespaceKnownValue)
                                .orders()
                                .tactics()));
    }

    @Test
    void malformedContainersAreRejectedWithoutProducingPartialOrders() {
        CompoundTag missingTemplateVersion = encoded(COMPLETE_ORDERS);
        templateTag(missingTemplateVersion).remove("Version");

        CompoundTag missingOrders = encoded(COMPLETE_ORDERS);
        templateTag(missingOrders).remove("Orders");

        CompoundTag missingOrdersVersion = encoded(COMPLETE_ORDERS);
        ordersTag(missingOrdersVersion).remove("Version");

        assertAll(
                () -> assertCorrupt(missingTemplateVersion),
                () -> assertCorrupt(missingOrders),
                () -> assertCorrupt(missingOrdersVersion));
    }

    @Test
    void futureTemplateAndOrdersVersionsAreReportedAsUnsupported() {
        CompoundTag futureTemplate = encoded(COMPLETE_ORDERS);
        templateTag(futureTemplate).putInt(
                "Version",
                GuardOrderTemplate.DATA_VERSION + 1);

        CompoundTag futureOrders = encoded(COMPLETE_ORDERS);
        ordersTag(futureOrders).putInt("Version", GuardOrders.DATA_VERSION + 1);

        DecodeResult futureTemplateResult = GuardOrderTemplate.readFrom(futureTemplate);
        DecodeResult futureOrdersResult = GuardOrderTemplate.readFrom(futureOrders);
        assertAll(
                () -> assertEquals(
                        DecodeStatus.UNSUPPORTED_VERSION,
                        futureTemplateResult.status()),
                () -> assertEquals(
                        GuardOrderTemplate.DATA_VERSION + 1,
                        futureTemplateResult.storedVersion()),
                () -> assertNull(futureTemplateResult.orders()),
                () -> assertFalse(futureTemplateResult.isValid()),
                () -> assertEquals(
                        DecodeStatus.UNSUPPORTED_VERSION,
                        futureOrdersResult.status()),
                () -> assertEquals(
                        GuardOrders.DATA_VERSION + 1,
                        futureOrdersResult.storedVersion()),
                () -> assertNull(futureOrdersResult.orders()));
    }

    private static CompoundTag encoded(GuardOrders orders) {
        CompoundTag itemTag = new CompoundTag();
        GuardOrderTemplate.writeTo(itemTag, orders);
        return itemTag;
    }

    private static CompoundTag templateTag(CompoundTag itemTag) {
        return itemTag.getCompound(GuardOrderTemplate.TEMPLATE_TAG);
    }

    private static CompoundTag ordersTag(CompoundTag itemTag) {
        return templateTag(itemTag).getCompound("Orders");
    }

    private static void assertCorrupt(CompoundTag itemTag) {
        DecodeResult result = GuardOrderTemplate.readFrom(itemTag);
        assertAll(
                () -> assertEquals(DecodeStatus.CORRUPT, result.status()),
                () -> assertNull(result.orders()),
                () -> assertFalse(result.isValid()));
    }
}
