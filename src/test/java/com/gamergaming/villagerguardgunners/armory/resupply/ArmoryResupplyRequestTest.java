package com.gamergaming.villagerguardgunners.armory.resupply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary;
import com.gamergaming.villagerguardgunners.ammunition.GuardWeaponAmmunition;
import com.gamergaming.villagerguardgunners.sidearm.SidearmState;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class ArmoryResupplyRequestTest {
    private static final UUID GUARD_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SQUAD_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final ResourceLocation DIMENSION =
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
    private static final ResourceLocation GUN_ID =
            ResourceLocation.fromNamespaceAndPath("test", "rifle");

    @Test
    void factoryCopiesAnActionableNeedIntoAnExpiringIdentity() {
        BlockPos.MutableBlockPos mutableOrigin = new BlockPos.MutableBlockPos(4, 70, -8);
        ArmoryResupplyRequest request = ArmoryResupplyRequest.create(
                7L,
                GUARD_ID,
                DIMENSION,
                mutableOrigin,
                SQUAD_ID,
                requiredNeed(),
                1_000L,
                600);
        mutableOrigin.set(100, 100, 100);

        assertEquals(ArmoryResupplyRequest.CURRENT_CONTRACT_VERSION, request.contractVersion());
        assertEquals(new BlockPos(4, 70, -8), request.origin());
        assertNotSame(mutableOrigin, request.origin());
        assertEquals(
                new ArmoryResupplyRequest.RequestKey(GUARD_ID, 7L, 1_000L),
                request.key());
        assertEquals(61L, request.requestedUnits());
        assertFalse(request.isExpired(1_599L));
        assertTrue(request.isExpired(1_600L));
        assertTrue(request.isExpired(999L));
    }

    @Test
    void liveWeaponMatchRequiresDimensionRoleAndGunIdentity() {
        ArmoryResupplyRequest request = request();

        assertTrue(request.matchesLiveWeapon(
                DIMENSION,
                ArmoryResupplyWeaponRole.PRIMARY,
                GUN_ID));
        assertFalse(request.matchesLiveWeapon(
                ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether"),
                ArmoryResupplyWeaponRole.PRIMARY,
                GUN_ID));
        assertFalse(request.matchesLiveWeapon(
                DIMENSION,
                ArmoryResupplyWeaponRole.SIDEARM,
                GUN_ID));
        assertFalse(request.matchesLiveWeapon(
                DIMENSION,
                ArmoryResupplyWeaponRole.PRIMARY,
                ResourceLocation.fromNamespaceAndPath("test", "other")));
    }

    @Test
    void nonActionableNeedAndUnsafeLifetimeAreRejected() {
        ArmoryResupplyNeed.Assessment sufficient = new ArmoryResupplyNeed.Assessment(
                ArmoryResupplyNeed.NeedStatus.SUFFICIENT_RESERVE,
                ArmoryResupplyWeaponRole.PRIMARY,
                GUN_ID,
                null,
                30,
                30,
                90L,
                90L,
                0L);
        assertThrows(
                IllegalArgumentException.class,
                () -> ArmoryResupplyRequest.create(
                        0L, GUARD_ID, DIMENSION, BlockPos.ZERO, null,
                        sufficient, 0L, 600));
        assertThrows(
                IllegalArgumentException.class,
                () -> ArmoryResupplyRequest.create(
                        0L, GUARD_ID, DIMENSION, BlockPos.ZERO, null,
                        requiredNeed(), 0L,
                        ArmoryResupplyLimits.HARD_MAX_REQUEST_LIFETIME_TICKS + 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> ArmoryResupplyRequest.create(
                        0L, GUARD_ID, DIMENSION, BlockPos.ZERO, null,
                        requiredNeed(), Long.MAX_VALUE, 1));
    }

    @Test
    void nilIdentityAndUnsupportedContractFailClosed() {
        UUID nil = new UUID(0L, 0L);
        assertThrows(
                IllegalArgumentException.class,
                () -> new ArmoryResupplyRequest.RequestKey(nil, 0L, 0L));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ArmoryResupplyRequest(
                        99,
                        0L,
                        GUARD_ID,
                        DIMENSION,
                        BlockPos.ZERO,
                        null,
                        ArmoryResupplyWeaponRole.PRIMARY,
                        GUN_ID,
                        ArmoryResupplyUrgency.LOW_RESERVE,
                        30,
                        10,
                        29L,
                        90L,
                        61L,
                        0L,
                        600L));
    }

    private static ArmoryResupplyRequest request() {
        return ArmoryResupplyRequest.create(
                0L,
                GUARD_ID,
                DIMENSION,
                BlockPos.ZERO,
                null,
                requiredNeed(),
                0L,
                ArmoryResupplyLimits.DEFAULT_REQUEST_LIFETIME_TICKS);
    }

    private static ArmoryResupplyNeed.Assessment requiredNeed() {
        GuardAmmoSummary.Snapshot primary = new GuardAmmoSummary.Snapshot(
                GuardAmmoSummary.Status.READY,
                GUN_ID,
                null,
                10,
                30,
                false,
                false,
                29L,
                false,
                GuardAmmoSummary.ReserveSource.PHYSICAL,
                1,
                0,
                0,
                true);
        GuardAmmoSummary.Snapshot noSidearm = new GuardAmmoSummary.Snapshot(
                GuardAmmoSummary.Status.NO_GUN,
                null,
                null,
                -1,
                -1,
                false,
                false,
                0L,
                false,
                GuardAmmoSummary.ReserveSource.NONE,
                0,
                0,
                0,
                false);
        return ArmoryResupplyNeed.assess(
                new GuardWeaponAmmunition.LoadoutSummary(
                        SidearmState.PRIMARY_ACTIVE,
                        GuardWeaponAmmunition.ActiveRole.PRIMARY,
                        primary,
                        noSidearm),
                1,
                3);
    }
}
