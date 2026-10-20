package com.gamergaming.villagerguardgunners.qa;

import static com.gamergaming.villagerguardgunners.qa.SupplyMedicalAcceptance.*;
import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.entity.GuardGunnerContainer;
import com.gamergaming.villagerguardgunners.orders.GuardOrderPermissions;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import tallestegg.guardvillagers.configuration.GuardConfig;
import tallestegg.guardvillagers.networking.GuardFollowPacket;
import tallestegg.guardvillagers.networking.GuardSetPatrolPosPacket;

/** Calls the actual installed upstream handlers; mixins must secure those entry points. */
final class NativeCommandChecks {
    static void run(SupplyMedicalAcceptance q) {
        var g = q.guard(ORIGIN, false);
        var p = q.owner;
        boolean followHero = GuardConfig.COMMON.followHero.get(), patrolHero = GuardConfig.COMMON.setGuardPatrolHotv.get();
        var context = (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),
                new Class<?>[]{IPayloadContext.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "player" -> p;
                    case "enqueueWork" -> { ((Runnable) args[0]).run(); yield CompletableFuture.completedFuture(null); }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        Runnable send = () -> {
            GuardFollowPacket.handle(new GuardFollowPacket(g.getId()), context);
            GuardSetPatrolPosPacket.setPatrolPosition(new GuardSetPatrolPosPacket(g.getId(), false), context);
        };
        try {
            GuardConfig.COMMON.followHero.set(false); GuardConfig.COMMON.setGuardPatrolHotv.set(false);
            p.removeEffect(MobEffects.HERO_OF_THE_VILLAGE); p.setPos(.5, 160, 1.5);
            p.containerMenu = new GuardGunnerContainer(1, p.getInventory(), g.guardInventory, GuardAmmoAccess.getAmmoHandler(g), g);
            UUID original = UUID.randomUUID(); g.setOwnerId(original);
            require(!GuardOrderPermissions.mayManage(p, g), "Unauthorized fixture has permissions");
            send.run();
            require(original.equals(g.getOwnerId()) && !g.isFollowing() && !g.isPatrolling(), "Unauthorized packet changed guard");
            passed("native_commands_permission_denied");
            g.setOwnerId(p.getUUID()); p.containerMenu = p.inventoryMenu;
            send.run(); require(!g.isFollowing() && !g.isPatrolling(), "No-menu packet accepted");
            passed("native_commands_require_menu");
            p.containerMenu = new GuardGunnerContainer(2, p.getInventory(), g.guardInventory, GuardAmmoAccess.getAmmoHandler(g), g);
            p.setPos(100, 160, 100); send.run(); require(!g.isFollowing() && !g.isPatrolling(), "Remote packet accepted");
            passed("native_commands_require_range"); p.setPos(.5, 160, 1.5);
            var other = q.guard(ORIGIN.offset(2, 0, 0), false);
            require(q.level.getEntity(other.getId()) == other, "Second guard is not loaded");
            GuardFollowPacket.handle(new GuardFollowPacket(other.getId()), context);
            GuardSetPatrolPosPacket.setPatrolPosition(new GuardSetPatrolPosPacket(other.getId(), false), context);
            require(!other.isFollowing() && !other.isPatrolling(), "Packet targeted a guard outside the open menu");
            passed("native_commands_require_same_guard");
            p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR); send.run();
            require(!g.isFollowing() && !g.isPatrolling(), "Spectator packet accepted");
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            passed("native_commands_reject_spectators");
            GuardConfig.COMMON.followHero.set(true); GuardConfig.COMMON.setGuardPatrolHotv.set(true);
            send.run(); require(!g.isFollowing() && !g.isPatrolling(), "Hero requirement bypassed by owner");
            passed("native_commands_require_hero_when_configured");
            p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 24000));
            send.run(); require(g.isFollowing() && g.isPatrolling(), "Authorized packet refused");
            GuardSetPatrolPosPacket.setPatrolPosition(new GuardSetPatrolPosPacket(g.getId(), false), context);
            require(g.isPatrolling(), "Stale patrol packet changed state");
            GuardFollowPacket.handle(new GuardFollowPacket(g.getId()), context);
            GuardSetPatrolPosPacket.setPatrolPosition(new GuardSetPatrolPosPacket(g.getId(), true), context);
            require(!g.isFollowing() && !g.isPatrolling(), "Authorized stop refused");
            passed("native_commands_authorized_toggle_and_stale_patrol");
            p.removeEffect(MobEffects.HERO_OF_THE_VILLAGE); send.run();
            require(!g.isFollowing() && !g.isPatrolling(), "Revoked Hero permission accepted");
            passed("native_commands_live_permission_revocation");
            p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 24000));
            g.hurt(q.level.damageSources().generic(), 1000); send.run();
            require(!g.isFollowing() && !g.isPatrolling(), "Downed guard accepted commands");
            g.discard(); send.run();
            require(!g.isFollowing() && !g.isPatrolling(), "Removed guard accepted commands");
            passed("native_commands_reject_downed_and_removed_guards");
        } finally {
            p.containerMenu = p.inventoryMenu;
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            GuardConfig.COMMON.followHero.set(followHero); GuardConfig.COMMON.setGuardPatrolHotv.set(patrolHero);
            p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 24000)); q.clearActors();
        }
    }
}
