package com.gamergaming.villagerguardgunners.integration;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.TacHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.configuration.GuardConfig;
import tallestegg.guardvillagers.entities.Guard;

@GameTestHolder(VillagerGuardGunners.MODID)
@PrefixGameTestTemplate(false)
public final class MedicalIntegrationGameTests {
    @GameTest(templateNamespace = VillageIntegrationGameTests.NAMESPACE, template = VillageIntegrationGameTests.ARENA,
            batch = "integration_medic_settings", setupTicks = 50)
    public static void medicTreatmentCoexistsWithUpstreamHealingSettings(GameTestHelper helper) {
        boolean cleric=GuardConfig.ClericHealing, smith=GuardConfig.BlackSmithHealing, repair=GuardConfig.armorerRepairGuardArmor;
        float regen=GuardConfig.amountOfHealthRegenerated;
        try {
            for (boolean enabled : new boolean[]{false,true}) {
                GuardConfig.ClericHealing=enabled; GuardConfig.BlackSmithHealing=enabled;
                GuardConfig.armorerRepairGuardArmor=enabled; GuardConfig.amountOfHealthRegenerated=enabled?1:0;
                Guard medic=guard(helper), patient=guard(helper);
                try {
                    medic.setNoAi(false);patient.setNoAi(false);patient.setPos(medic.position().add(1,0,0)); patient.setHealth(8);
                    for (Guard actor : new Guard[]{medic,patient}) {
                        var data=com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(actor).getDataHolder();
                        data.drawTimestamp=-1;data.shootTimestamp=-1;data.meleePrepTickCount=-1;
                    }
                    var armor=new ItemStack(Items.IRON_CHESTPLATE);armor.setDamageValue(20);patient.setItemSlot(EquipmentSlot.CHEST,armor);
                    com.gamergaming.villagerguardgunners.api.GuardMedicAccess.get(medic).medic(true);
                    var pouch=com.gamergaming.villagerguardgunners.api.GuardMedicalAccess.get(medic);
                    pouch.inventory().setStackInSlot(0,new ItemStack(com.gamergaming.villagerguardgunners.init.ModItems.BANDAGE.get(),3));
                    helper.assertTrue(com.gamergaming.villagerguardgunners.medical.MedicRuntime.reserve(medic,patient),"Settings blocked medic reservation");
                    var lease=com.gamergaming.villagerguardgunners.medical.MedicRuntime.lease(medic);
                    com.gamergaming.villagerguardgunners.medical.MedicRuntime.treat(lease);lease.preparedAt-=40;
                    com.gamergaming.villagerguardgunners.medical.MedicRuntime.treat(lease);
                    helper.assertTrue(patient.getHealth()==12 && pouch.inventory().getStackInSlot(0).getCount()==2,"Settings changed finite medical treatment");
                    patient.eat(helper.getLevel(),new ItemStack(Items.BREAD));
                    helper.assertTrue(patient.getHealth()>12 && armor.getDamageValue()==20,"Medic interfered with food or repaired armor");
                    helper.assertTrue(GuardConfig.ClericHealing==enabled&&GuardConfig.armorerRepairGuardArmor==enabled,"Medic changed upstream settings");
                } finally {com.gamergaming.villagerguardgunners.medical.MedicRuntime.cancel(medic,com.gamergaming.villagerguardgunners.medical.MedicState.Status.INTERRUPTED);medic.discard();patient.discard();}
            }
        } finally {GuardConfig.ClericHealing=cleric;GuardConfig.BlackSmithHealing=smith;GuardConfig.armorerRepairGuardArmor=repair;GuardConfig.amountOfHealthRegenerated=regen;}
        helper.succeed();
    }

    @GameTest(templateNamespace = VillageIntegrationGameTests.NAMESPACE, template = VillageIntegrationGameTests.ARENA)
    public static void guardEatingUsesCancelableHealingAndDoesNotRequireAMedic(GameTestHelper helper) {
        Guard guard = guard(helper);
        guard.setHealth(5);
        try (MedicalTrace trace = new MedicalTrace(guard)) {
            trace.cancelHeal = true;
            guard.heal(4);
            helper.assertTrue(guard.getHealth() == 5, "Canceled healing changed health");
            trace.cancelHeal = false;
            guard.eat(helper.getLevel(), new ItemStack(Items.BREAD));
            helper.assertTrue(guard.getHealth() > 5 && trace.events.stream().filter("heal"::equals).count() >= 2,
                    "Upstream food healing did not pass through the healing event");
            System.out.println("VGG_INTEGRATION food/heal events=" + trace.events);
        }
        guard.discard();
        helper.succeed();
    }

    @GameTest(templateNamespace = VillageIntegrationGameTests.NAMESPACE, template = VillageIntegrationGameTests.ARENA,
            batch = "integration_death")
    public static void deathEventCancellationIsTooLateForUpstreamZombieConversion(GameTestHelper helper) {
        withZombieConversion(helper, () -> {
            Guard guard = guard(helper);
            Zombie zombie = zombie(helper);
            guard.setHealth(2);
            try (MedicalTrace trace = new MedicalTrace(guard)) {
                trace.cancelDeath = true;
                guard.hurt(helper.getLevel().damageSources().mobAttack(zombie), 100);
                helper.assertTrue(guard.isRemoved() && trace.events.contains("conversion.pre")
                                && trace.events.indexOf("conversion.pre") < trace.events.indexOf("death"),
                        "Expected conversion before Forge death cancellation: " + trace.events);
                System.out.println("VGG_INTEGRATION late death cancellation=" + trace.events);
            }
            zombie.discard();
        });
        helper.succeed();
    }

    @GameTest(templateNamespace = VillageIntegrationGameTests.NAMESPACE, template = VillageIntegrationGameTests.ARENA,
            batch = "integration_early_death")
    public static void earlyGuardBoundaryPreservesIdentityAndDefersConversionAndDrops(GameTestHelper helper) {
        withZombieConversion(helper, () -> {
            Guard guard = guard(helper);
            Zombie zombie = zombie(helper);
            var id = guard.getUUID();
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            sword.getOrCreateTag().putString("ProbeOwnership", "original");
            guard.setItemSlot(EquipmentSlot.MAINHAND, sword.copy());
            guard.setHealth(2);
            try (MedicalTrace trace = new MedicalTrace(guard)) {
                trace.interceptGuardDeath = true;
                guard.hurt(helper.getLevel().damageSources().mobAttack(zombie), 100);
                helper.assertTrue(guard.isAlive() && !guard.isRemoved() && trace.intercepted == 1
                                && guard.getUUID().equals(id) && ItemStack.matches(sword, guard.getMainHandItem())
                                && !trace.events.contains("death") && !trace.events.contains("conversion.pre")
                                && !trace.events.contains("drops"),
                        "Early prototype did not preserve the live original guard: " + trace.events);
                guard.invulnerableTime = 0;
                guard.hurt(helper.getLevel().damageSources().fellOutOfWorld(), 1_000);
                helper.assertTrue(trace.events.stream().filter("death"::equals).count() == 1
                                && trace.events.stream().filter("drops"::equals).count() == 1,
                        "Bypass death did not reach the normal death/drop path exactly once: " + trace.events);
                System.out.println("VGG_INTEGRATION early boundary and true death=" + trace.events);
            }
            guard.discard();
            zombie.discard();
        });
        helper.succeed();
    }

    @GameTest(templateNamespace = VillageIntegrationGameTests.NAMESPACE, template = VillageIntegrationGameTests.ARENA)
    public static void totemResolvesBeforeTheEarlyGuardDeathBoundary(GameTestHelper helper) {
        Guard guard = guard(helper);
        guard.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        guard.setHealth(2);
        try (MedicalTrace trace = new MedicalTrace(guard)) {
            trace.interceptGuardDeath = true;
            guard.hurt(helper.getLevel().damageSources().generic(), 100);
            helper.assertTrue(guard.isAlive() && guard.getOffhandItem().isEmpty()
                            && trace.intercepted == 0 && !trace.events.contains("death"),
                    "Early hook bypassed a usable totem: " + trace.events);
            System.out.println("VGG_INTEGRATION totem precedes downing=" + trace.events);
        }
        guard.discard();
        helper.succeed();
    }

    @GameTest(templateNamespace = VillageIntegrationGameTests.NAMESPACE, template = VillageIntegrationGameTests.ARENA)
    public static void nativeTaczBulletCanReachTheDeathBoundaryTwiceForOneHit(GameTestHelper helper) {
        Guard guard = guard(helper);
        Zombie attacker = zombie(helper);
        guard.setHealth(2);
        try (MedicalTrace trace = new MedicalTrace(guard)) {
            trace.interceptGuardDeath = true;
            ProbeBullet bullet = new ProbeBullet(helper, attacker);
            bullet.hit(guard);
            helper.assertTrue(guard.isAlive() && trace.intercepted == 2,
                    "Expected both native damage components to enter the boundary: " + trace.events);
            System.out.println("VGG_INTEGRATION TaCZ split damage=" + trace.events);
            bullet.discard();
        }
        guard.discard();
        attacker.discard();
        helper.succeed();
    }

    @GameTest(templateNamespace = VillageIntegrationGameTests.NAMESPACE, template = VillageIntegrationGameTests.ARENA)
    public static void nativeInteractionsStockTreatAndRestoreWithoutAQuartermaster(GameTestHelper helper) {
        Guard guard = guard(helper);
        var player = new net.minecraftforge.common.util.FakePlayer(helper.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "MedicalInteraction"));
        player.setPos(guard.position().add(1, 0, 0)); guard.setOwnerId(player.getUUID());
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE, 1000));
        var pouch = com.gamergaming.villagerguardgunners.api.GuardMedicalAccess.get(guard);
        var bandage = com.gamergaming.villagerguardgunners.init.ModItems.BANDAGE.get();
        var config = com.gamergaming.villagerguardgunners.init.ModConfigs.GUARD_GUNNERS.medical;
        var enabled = config.enabled;
        try {
            guard.setHealth(8); player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(bandage, 5));
            config.enabled = new me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean(false);
            guard.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND);
            helper.assertTrue(guard.getHealth() == 8 && player.getMainHandItem().getCount() == 5, "Disabled treatment consumed or healed");
            player.setShiftKeyDown(true); guard.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND);
            helper.assertTrue(player.getMainHandItem().isEmpty() && pouch.inventory().getStackInSlot(0).getCount() == 5, "Disabled treatment prevented manual stocking");
            config.enabled = enabled; player.setShiftKeyDown(false);
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(bandage, 2));
            guard.interact(player, net.minecraft.world.InteractionHand.OFF_HAND);
            helper.assertTrue(guard.getHealth() == 12 && player.getOffhandItem().getCount() == 1 && pouch.inventory().getStackInSlot(0).getCount() == 5, "Offhand treatment used the wrong source");
            var packed = pouch.packed(); pouch.clearAfterPacking();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, packed);
            guard.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND);
            helper.assertTrue(player.getMainHandItem().isEmpty() && pouch.inventory().getStackInSlot(0).getCount() == 5, "Native pouch restore failed");
            player.removeEffect(net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE);
            guard.setOwnerId(java.util.UUID.randomUUID());
            guard.setTarget(player); pouch.readyAt(0);
            helper.assertTrue(guard.getTarget() == player, "Fixture did not create a hostile guard");
            guard.interact(player, net.minecraft.world.InteractionHand.OFF_HAND);
            helper.assertTrue(player.getOffhandItem().getCount() == 1 && guard.getHealth() == 12, "Hostile guard accepted treatment");
        } finally { config.enabled = enabled; guard.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = VillageIntegrationGameTests.NAMESPACE, template = VillageIntegrationGameTests.ARENA,
            batch = "integration_medical_conversion")
    public static void zombieConversionMovesMedicalSuppliesIntoRecovery(GameTestHelper helper) {
        withZombieConversion(helper, () -> {
            Guard guard = guard(helper); Zombie zombie = zombie(helper);
            var pouch = com.gamergaming.villagerguardgunners.api.GuardMedicalAccess.get(guard);
            pouch.inventory().setStackInSlot(2, new ItemStack(com.gamergaming.villagerguardgunners.init.ModItems.BANDAGE.get(), 19));
            var queue = com.gamergaming.villagerguardgunners.medical.MedicalRecovery.get(helper.getLevel()); int before = queue.pendingCount();
            guard.setHealth(2); guard.hurt(helper.getLevel().damageSources().mobAttack(zombie), 100);
            helper.assertTrue(guard.isRemoved() && pouch.released() && queue.pendingCount() == before + 1, "Zombie conversion did not preserve medical supplies");
            zombie.discard();
        }); helper.succeed();
    }


    @GameTest(templateNamespace = VillageIntegrationGameTests.NAMESPACE, template = VillageIntegrationGameTests.ARENA)
    public static void productionDowningSurvivesBothNativeBulletDamageParts(GameTestHelper helper) {
        var config = com.gamergaming.villagerguardgunners.init.ModConfigs.GUARD_GUNNERS.medical;
        boolean old = config.downed.get(); config.downed.accept(true);
        Guard guard = guard(helper); Zombie attacker = zombie(helper);
        try (MedicalTrace trace = new MedicalTrace(guard)) {
            guard.setHealth(2); ProbeBullet bullet = new ProbeBullet(helper, attacker);
            try { bullet.hit(guard); } finally { bullet.discard(); }
            var life = com.gamergaming.villagerguardgunners.api.GuardLifeAccess.get(guard);
            helper.assertTrue(guard.isAlive() && life.stage() == com.gamergaming.villagerguardgunners.medical.GuardLifeState.Stage.DOWNED
                    && life.remaining() == 1200 && !trace.events.contains("death") && !trace.events.contains("conversion.pre"),
                    "Native split bullet bypassed or immediately finished casualty: " + trace.events);
        } finally { guard.discard(); attacker.discard(); config.downed.accept(old); }
        helper.succeed();
    }

    @GameTest(templateNamespace = VillageIntegrationGameTests.NAMESPACE, template = VillageIntegrationGameTests.ARENA,
            batch = "casualty_conversion")
    public static void productionDowningDefersZombieConversionUntilTrueDeath(GameTestHelper helper) {
        withZombieConversion(helper, () -> {
            var config = com.gamergaming.villagerguardgunners.init.ModConfigs.GUARD_GUNNERS.medical;
            boolean old = config.downed.get(); config.downed.accept(true);
            Guard guard = guard(helper); Zombie attacker = zombie(helper);
            try (MedicalTrace trace = new MedicalTrace(guard)) {
                guard.setHealth(2); guard.hurt(helper.getLevel().damageSources().mobAttack(attacker), 100);
                helper.assertTrue(guard.isAlive() && !guard.isRemoved() && !trace.events.contains("conversion.pre"),
                        "Zombie converted downed guard early: " + trace.events);
                var life = com.gamergaming.villagerguardgunners.api.GuardLifeAccess.get(guard);
                for (int i=0;i<1199;i++) life.tick();
                com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime.tick(guard);
                guard.die(helper.getLevel().damageSources().mobAttack(attacker));
                helper.assertTrue(guard.isRemoved() && trace.events.stream().filter("conversion.pre"::equals).count() == 1,
                        "Bleedout lost or duplicated zombie conversion: " + trace.events);
            } finally { guard.discard(); attacker.discard(); config.downed.accept(old); }
        });
        helper.succeed();
    }

    @GameTest(templateNamespace = VillageIntegrationGameTests.NAMESPACE, template = VillageIntegrationGameTests.ARENA)
    public static void downedGuardCancelsNativeFireReloadAndMeleeEvents(GameTestHelper helper) {
        var config = com.gamergaming.villagerguardgunners.init.ModConfigs.GUARD_GUNNERS.medical;
        boolean old = config.downed.get(); config.downed.accept(true); Guard guard = guard(helper);
        try {
            guard.setHealth(2); guard.hurt(helper.getLevel().damageSources().generic(), 100);
            var gun = com.tacz.guns.api.item.builder.GunItemBuilder.create().setId(ResourceLocation.parse("tacz:m16a4")).build();
            helper.assertTrue(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.tacz.guns.api.event.common.GunFireEvent(guard, gun, net.minecraftforge.fml.LogicalSide.SERVER)), "Queued native round not canceled");
            helper.assertTrue(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.tacz.guns.api.event.common.GunReloadEvent(guard, gun, net.minecraftforge.fml.LogicalSide.SERVER)), "Native reload not canceled");
            helper.assertTrue(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.tacz.guns.api.event.common.GunMeleeEvent(guard, gun, net.minecraftforge.fml.LogicalSide.SERVER)), "Native melee not canceled");
        } finally { guard.discard(); config.downed.accept(old); }
        helper.succeed();
    }

    static Guard guard(GameTestHelper helper) {
        Guard guard = GuardEntityType.GUARD.get().create(helper.getLevel());
        BlockPos pos = helper.absolutePos(new BlockPos(8, 2, 8));
        guard.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        guard.setNoAi(true);
        helper.getLevel().addFreshEntity(guard);
        return guard;
    }

    private static Zombie zombie(GameTestHelper helper) {
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        zombie.setNoAi(true);
        return zombie;
    }

    private static void withZombieConversion(GameTestHelper helper, Runnable body) {
        Difficulty difficulty = helper.getLevel().getDifficulty();
        boolean convert = GuardConfig.COMMON.convertGuardOnDeath.get();
        try {
            helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
            GuardConfig.COMMON.convertGuardOnDeath.set(true);
            body.run();
        } finally {
            GuardConfig.COMMON.convertGuardOnDeath.set(convert);
            helper.getLevel().getServer().setDifficulty(difficulty, true);
        }
    }

    private static final class ProbeBullet extends EntityKineticBullet {
        ProbeBullet(GameTestHelper helper, Zombie attacker) {
            super(EntityKineticBullet.TYPE, helper.getLevel());
            setOwner(attacker);
            setField("gunId", ResourceLocation.fromNamespaceAndPath("tacz", "glock_17"));
            setField("gunDisplayId", ResourceLocation.fromNamespaceAndPath("tacz", "default"));
            setField("ammoId", ResourceLocation.fromNamespaceAndPath("tacz", "9mm"));
            setField("armorIgnore", 0.5F);
        }

        @Override
        public float getDamage(Vec3 position) {
            return 8;
        }

        void hit(Guard guard) {
            Vec3 pos = guard.position();
            onHitEntity(new TacHitResult(new EntityKineticBullet.EntityResult(guard, pos, false)), pos.subtract(1, 0, 0), pos);
        }

        private void setField(String name, Object value) {
            try {
                var field = EntityKineticBullet.class.getDeclaredField(name);
                field.setAccessible(true);
                field.set(this, value);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("TaCZ fixture field changed: " + name, exception);
            }
        }
    }
}
