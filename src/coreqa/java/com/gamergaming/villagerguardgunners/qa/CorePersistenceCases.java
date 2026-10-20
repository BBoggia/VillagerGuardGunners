package com.gamergaming.villagerguardgunners.qa;

import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.inventory.GuardAmmoHandler;
import com.gamergaming.villagerguardgunners.inventory.ItemCustomData;
import com.gamergaming.villagerguardgunners.quartermaster.trade.TradeOfferAccess;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import tallestegg.guardvillagers.GuardEntityType;

final class CorePersistenceCases {
    static void run(ServerLevel level) {
        var registries = level.registryAccess();
        var ammo = AmmoItemBuilder.create().setId(ResourceLocation.parse("tacz:762x39")).setCount(14).build();
        var gun = GunItemBuilder.create().setId(ResourceLocation.parse("tacz:ak47")).setAmmoCount(7).build(registries);
        var encodedAmmo = (CompoundTag) ammo.save(registries); encodedAmmo.putInt("Slot", 99);
        var encodedGun = (CompoundTag) gun.save(registries); encodedGun.putInt("Slot", 0);
        var entries = new ListTag(); entries.add(encodedAmmo); entries.add(encodedGun);
        var encoded = new CompoundTag(); encoded.putInt("Size", 9000); encoded.put("Items", entries);
        var inventory = new GuardAmmoHandler();
        var rejected = inventory.loadSafely(registries, encoded);
        require(inventory.getSlots() == 3 && ItemStack.matches(ammo, inventory.getStackInSlot(0)), "Invalid saved slot or size lost ammunition");
        require(rejected.size() == 1 && ItemStack.matches(gun, rejected.getFirst()), "Invalid reserve gun was not returned for recovery");

        ItemCustomData.update(gun, tag -> tag.putString("first", "keep"));
        ItemCustomData.update(gun, tag -> tag.putInt("second", 2));
        ItemCustomData.read(gun).remove("first");
        require(ItemCustomData.read(gun).getString("first").equals("keep"), "Custom data leaked a mutable reference");
        require(ItemStack.matches(gun, ItemStack.parseOptional(registries, (CompoundTag) gun.save(registries))), "Gun components failed round trip");

        var guard = GuardEntityType.GUARD.get().create(level);
        guard.setNoAi(true); guard.moveTo(0.5, 160, 3.5, 0, 0);
        GuardAmmoAccess.getAmmoHandler(guard).setStackInSlot(0, ammo.copy());
        require(level.addFreshEntity(guard), "Capability fixture spawn failed");
        var oldView = GuardAmmoAccess.getTaCZInventoryHandler(guard);
        require(oldView.getStackInSlot(6).getCount() == 14, "Combined capability mapped reserve to the wrong slot");
        guard.discard();
        require(oldView.getStackInSlot(6).isEmpty() && oldView.extractItem(6, 14, false).isEmpty(), "Removed guard left a writable capability view");
        require(GuardAmmoAccess.getAmmoHandler(guard).getStackInSlot(0).getCount() == 14, "Invalid capability changed backing storage");

        // The support migration must preserve server-side Quartermaster offer identities.
        var offer = new MerchantOffer(new ItemCost(Items.EMERALD), Optional.empty(), gun, 5, 1, 0.05F);
        var identity = new CompoundTag(); identity.putInt("Version", 99);
        ((TradeOfferAccess) offer).villagerGuardGunners$setTradeIdentity(identity);
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        var savedOffer = MerchantOffer.CODEC.encodeStart(ops, offer).getOrThrow();
        var restoredOffer = MerchantOffer.CODEC.parse(ops, savedOffer).getOrThrow();
        require(identity.equals(((TradeOfferAccess) restoredOffer).villagerGuardGunners$getTradeIdentity()), "Offer codec lost opaque identity");
        require(identity.equals(((TradeOfferAccess) restoredOffer.copy()).villagerGuardGunners$getTradeIdentity()), "Offer copy lost identity");
        LogUtils.getLogger().info("VGG_CORE_QA_STORAGE_PASS invalid_slots=preserved components=preserved removed_capability=closed offer_identity=preserved");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
