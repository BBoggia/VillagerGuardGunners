package quartermasterprobe;

import com.gamergaming.villagerguardgunners.api.GuardEquipmentServiceAccess;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import com.gamergaming.villagerguardgunners.quartermaster.service.*;
import com.gamergaming.villagerguardgunners.quartermaster.trade.*;
import com.tacz.guns.api.item.*;
import java.util.*;
import java.nio.file.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;
import tallestegg.guardvillagers.entities.Guard;

/** Actual vanilla merchant slots and packets against the reobfuscated installed mod. */
final class TradeCases {
    private static final UUID SAVED=UUID.fromString("08e84b6f-bb29-4275-8912-04004cd97c7d");
    private static final List<String> CASES=new ArrayList<>();
    private static Villager progress,saved;
    private static FakePlayer buyer;
    private static MerchantMenu reloadMenu;
    private static MerchantOffer reloadOffer;
    private static java.util.concurrent.CompletableFuture<Void> reload;
    private static int reloadStage,waiting;
    private static Path pack;
    private static boolean complete;
    private static void check(String name,boolean value){System.out.println("VGG trade "+name+"="+value);if(!value)throw new AssertionError("trade-"+name);CASES.add("trade-"+name);}
    private static ResourceLocation id(String path){return ResourceLocation.parse("villagerguardgunners:"+path);}
    private static Villager villager(ServerLevel level,int rank) {
        var v=new Villager(EntityType.VILLAGER,level);v.setPos(3.5,80,1.5);v.setNoAi(true);v.setInvulnerable(true);
        v.setVillagerData(v.getVillagerData().setProfession(ForgeRegistries.VILLAGER_PROFESSIONS.getValue(QuartermasterProfession.ID)).setLevel(rank));return v;
    }
    private static MerchantOffer offer(Villager v,String name){return v.getOffers().stream().filter(o->id(name).equals(QuartermasterTrades.id(o))).findFirst().orElseThrow();}
    private static MerchantMenu menu(FakePlayer player,Villager v) {
        player.closeContainer();v.setTradingPlayer(player);var m=new MerchantMenu(1,player.getInventory(),v);m.setMerchantLevel(v.getVillagerData().getLevel());player.containerMenu=m;return m;
    }
    private static void prepare(MerchantMenu menu,Villager v,MerchantOffer offer) {
        menu.getSlot(0).set(ItemStack.EMPTY);menu.getSlot(1).set(ItemStack.EMPTY);menu.setSelectionHint(v.getOffers().indexOf(offer));
        menu.getSlot(0).set(offer.getCostA().copy());if(!offer.getCostB().isEmpty())menu.getSlot(1).set(offer.getCostB().copy());
    }
    static void run(ServerLevel level,QuartermasterBlockEntity station,Guard guard,Villager operator) throws Exception {
        check("defaults-loaded-after-assets",TradeDefinitions.ready()&&TradeDefinitions.snapshot().size()==12);
        buyer=new FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"TradeBuyer"));buyer.setPos(3.5,80,2.5);
        var settings=ModConfigs.GUARD_GUNNERS.quartermasterTrades;
        check("supply-on-guns-off-default",settings.suppliesEnabled.get()&&!settings.gunSalesEnabled.get());
        var beforeStation=((net.minecraft.world.level.block.entity.BlockEntity)station).saveWithFullMetadata();var beforeGuard=ServiceEquipment.snapshot(guard);
        var v=villager(level,1);
        check("novice-offers",v.getOffers().size()==2);check("repeat-read-idempotent",v.getOffers().size()==2);
        var custom=new MerchantOffer(new ItemStack(Items.EMERALD),new ItemStack(Items.BREAD),9,1,.05f);v.getOffers().add(custom);
        for(int rank=2;rank<=5;rank++){v.setVillagerData(v.getVillagerData().setLevel(rank));check("level-"+rank+"-offers",v.getOffers().size()==rank*2+1&&v.getOffers().contains(custom));}
        check("raw-gun-item-refused",QuartermasterTrades.stack(new TradeDefinition.Stack(TradeDefinition.Kind.ITEM,ResourceLocation.parse("tacz:modern_kinetic_gun"),1)).isEmpty());
        check("native-stack-limit-enforced",QuartermasterTrades.stack(new TradeDefinition.Stack(TradeDefinition.Kind.ITEM,ResourceLocation.parse("minecraft:shield"),2)).isEmpty());
        check("no-guns-by-default",v.getOffers().stream().noneMatch(o->o.getResult().getItem() instanceof IGun));
        var rerolled=villager(level,1);check("job-has-initial-offers",rerolled.getOffers().size()==2);
        rerolled.setVillagerData(rerolled.getVillagerData().setProfession(VillagerProfession.NONE));
        rerolled.setVillagerData(rerolled.getVillagerData().setProfession(ForgeRegistries.VILLAGER_PROFESSIONS.getValue(QuartermasterProfession.ID)));
        check("reclaimed-profession-gets-offers",rerolled.getOffers().size()==2);
        var old=villager(level,5);old.setOffers(new MerchantOffers());check("old-master-empty-initialized",old.getOffers().size()==10);
        var bandage=offer(old,"bandages");var m=menu(buyer,old);prepare(m,old,bandage);
        check("native-result-preview",m.getSlot(2).getItem().getCount()==4);
        int xp=old.getVillagerXp();m.clicked(2,0,ClickType.PICKUP,buyer);
        check("normal-purchase-consumes-input",m.getSlot(0).getItem().isEmpty()&&m.getCarried().getCount()==4&&bandage.getUses()==1&&old.getVillagerXp()>xp);m.setCarried(ItemStack.EMPTY);
        prepare(m,old,bandage);m.quickMoveStack(buyer,2);check("shift-purchase-native-payment",m.getSlot(0).getItem().isEmpty()&&bandage.getUses()==2&&buyer.getInventory().countItem(bandage.getResult().getItem())==4);
        for(int i=0;i<36;i++)buyer.getInventory().setItem(i,new ItemStack(Items.STONE,64));prepare(m,old,bandage);int uses=bandage.getUses();
        check("full-inventory-no-consumption",m.quickMoveStack(buyer,2).isEmpty()&&bandage.getUses()==uses&&m.getSlot(0).getItem().getCount()==bandage.getCostA().getCount());
        buyer.getInventory().clearContent();buyer.closeContainer();
        check("close-returns-payment-once",buyer.getInventory().countItem(Items.EMERALD)==bandage.getCostA().getCount()&&old.getTradingPlayer()==null);buyer.closeContainer();
        check("repeated-close-no-duplicate",buyer.getInventory().countItem(Items.EMERALD)==bandage.getCostA().getCount());buyer.getInventory().clearContent();
        m=menu(buyer,old);bandage.setToOutOfStock();prepare(m,old,bandage);check("uses-limit-blocks-result",m.getSlot(2).getItem().isEmpty());
        old.restock();prepare(m,old,bandage);check("native-restock-retains-offer",bandage.getUses()==0&&!m.getSlot(2).getItem().isEmpty()&&old.getOffers().contains(bandage));
        bandage.setSpecialPriceDiff(-100);check("native-discount-minimum-one",bandage.getCostA().getCount()==1);bandage.resetSpecialPriceDiff();
        bandage.setToOutOfStock();bandage.updateDemand();check("native-demand-price-bounded",bandage.getCostA().getCount()>=bandage.getBaseCostA().getCount()&&bandage.getCostA().getCount()<=64);bandage.resetUses();
        prepare(m,old,bandage);int pay=m.getSlot(0).getItem().getCount();settings.suppliesEnabled.accept(false);
        check("hard-disable-preserves-saved-uses",bandage.isOutOfStock()&&bandage.getUses()==0);
        check("direct-shift-blocks-before-move",m.quickMoveStack(buyer,2).isEmpty()&&m.getSlot(0).getItem().getCount()==pay&&buyer.getInventory().countItem(bandage.getResult().getItem())==0);
        check("disabled-slot-pickup-refused",!m.getSlot(2).mayPickup(buyer));m.clicked(2,0,ClickType.PICKUP,buyer);
        check("normal-disabled-click-keeps-input",m.getCarried().isEmpty()&&m.getSlot(0).getItem().getCount()==pay);
        check("other-mod-offer-unaffected",!custom.isOutOfStock()&&QuartermasterTrades.allowed(custom));
        settings.suppliesEnabled.accept(true);m.broadcastChanges();prepare(m,old,bandage);check("reenable-restores-existing-offer",!m.getSlot(2).getItem().isEmpty()&&bandage.getUses()==0);buyer.closeContainer();
        settings.gunSalesEnabled.accept(true);settings.suppliesEnabled.accept(false);var guns=villager(level,5);
        check("gun-sales-independent",guns.getOffers().size()==2&&guns.getOffers().stream().allMatch(o->o.getResult().getItem() instanceof IGun));
        var gun=offer(guns,"optional_rifle");check("gun-sold-unloaded",gun.getResult().getCount()==1&&((IGun)gun.getResult().getItem()).getCurrentAmmoCount(gun.getResult())==0&&!((IGun)gun.getResult().getItem()).hasBulletInBarrel(gun.getResult()));
        settings.gunSalesEnabled.accept(false);check("saved-guns-disabled",gun.isOutOfStock());settings.suppliesEnabled.accept(true);
        var savedTag=bandage.createTag();var restored=new MerchantOffer(savedTag);check("offer-identity-round-trip",savedTag.equals(restored.createTag())&&id("bandages").equals(QuartermasterTrades.id(restored)));
        var invalid=restored.createTag();invalid.getCompound(QuartermasterTrades.KEY).putInt("Version",999);var future=new MerchantOffer(invalid);
        check("unknown-identity-preserved-and-blocked",future.isOutOfStock()&&invalid.equals(future.createTag()));
        var missing=new MerchantOffer(new ItemStack(Items.EMERALD),com.tacz.guns.api.item.builder.AmmoItemBuilder.create().setId(ResourceLocation.parse("missing:ammo")).setCount(1).build(),2,1,0);
        ((TradeOfferAccess)missing).villagerGuardGunners$setTradeIdentity(savedTag.get(QuartermasterTrades.KEY));check("obsolete-native-ammo-refused",!QuartermasterTrades.allowed(missing));
        var twoInput=new MerchantOffer(new ItemStack(Items.EMERALD,2),new ItemStack(Items.IRON_INGOT,3),new ItemStack(Items.BREAD),4,1,.05f);
        var twoIdentity=savedTag.getCompound(QuartermasterTrades.KEY).copy();twoIdentity.putInt("Inputs",2);
        ((TradeOfferAccess)twoInput).villagerGuardGunners$setTradeIdentity(twoIdentity);
        var twoSeller=villager(level,5);twoSeller.getOffers().add(twoInput);var twoMenu=menu(buyer,twoSeller);prepare(twoMenu,twoSeller,twoInput);
        twoMenu.clicked(2,0,ClickType.PICKUP,buyer);check("two-input-native-payment",twoMenu.getSlot(0).getItem().isEmpty()&&twoMenu.getSlot(1).getItem().isEmpty()&&twoMenu.getCarried().is(Items.BREAD));twoMenu.setCarried(ItemStack.EMPTY);buyer.closeContainer();
        var missingCost=twoInput.createTag();missingCost.getCompound("buyB").putString("id","absent_mod:fee");
        var brokenCost=new MerchantOffer(missingCost);check("removed-second-input-blocks-saved-offer",brokenCost.getCostB().isEmpty()&&!QuartermasterTrades.allowed(brokenCost));
        check("purchases-do-not-touch-station-or-guards",beforeStation.equals(((net.minecraft.world.level.block.entity.BlockEntity)station).saveWithFullMetadata())&&ServiceEquipment.same(guard,beforeGuard));
        var serviceBefore=ServiceEligibility.station(station);operator.setTradingPlayer(buyer);check("service-pauses-for-actual-trading",ServiceEligibility.station(station)==ServiceStatus.OPERATOR_UNAVAILABLE);operator.setTradingPlayer(null);check("service-resumes-after-trading",ServiceEligibility.station(station)==serviceBefore);
        progress=villager(level,1);progress.setPos(4.5,80,1.5);progress.setNoAi(false);level.addFreshEntity(progress);
        var pm=menu(buyer,progress);for(int i=0;i<5;i++){prepare(pm,progress,offer(progress,"bandages"));pm.clicked(2,0,ClickType.PICKUP,buyer);pm.setCarried(ItemStack.EMPTY);}
        check("five-purchases-earn-level-xp",progress.getVillagerXp()==10);buyer.closeContainer();
        saved=old;saved.setUUID(SAVED);saved.setPos(3.5,80,1.5);saved.setPersistenceRequired();level.addFreshEntity(saved);
        reloadOffer=bandage;reloadMenu=menu(buyer,saved);prepare(reloadMenu,saved,reloadOffer);
    }
    static boolean tick(ServerLevel level) throws Exception {
        if(complete)return true;
        if(progress==null)return false;
        if(reloadStage==0) {
            if(progress.getVillagerData().getLevel()<2) {
                if(++waiting%40==0)System.out.println("VGG trade level-up waiting xp="+progress.getVillagerXp()+" noAi="+progress.isNoAi()+" trading="+progress.isTrading()+" age="+progress.tickCount+" pos="+progress.position()+" dead="+!progress.isAlive());
                return false;
            }
            check("actual-trade-unlocks-next-level",progress.getOffers().size()==4);progress.discard();
            pack=level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR).resolve("quartermaster-trade-qa");
            Files.createDirectories(pack.resolve("data/villagerguardgunners/quartermaster_trades"));
            Files.writeString(pack.resolve("pack.mcmeta"),"{\"pack\":{\"pack_format\":15,\"description\":\"Local trade reload fixture\"}}");
            Files.writeString(pack.resolve("data/villagerguardgunners/quartermaster_trades/bandages.json"),"{\"version\":1,\"enabled\":false}");
            level.getServer().getPackRepository().reload();var packs=new ArrayList<>(level.getServer().getPackRepository().getSelectedIds());packs.add("file/quartermaster-trade-qa");
            reload=level.getServer().reloadResources(packs);reloadStage=1;return false;
        }
        if(!reload.isDone())return false;reload.join();
        if(reloadStage==1) {
            check("real-reload-disables-saved-offer",!TradeDefinitions.snapshot().get(id("bandages")).enabled()&&!QuartermasterTrades.allowed(reloadOffer));
            int payment=reloadMenu.getSlot(0).getItem().getCount();check("reload-open-menu-refuses-shift",reloadMenu.quickMoveStack(buyer,2).isEmpty()&&reloadMenu.getSlot(0).getItem().getCount()==payment);
            reloadMenu.broadcastChanges();check("reload-clears-output-without-consuming-input",reloadMenu.getSlot(2).getItem().isEmpty()&&reloadMenu.getSlot(0).getItem().getCount()==payment);
            Files.writeString(pack.resolve("data/villagerguardgunners/quartermaster_trades/bandages.json"),"{\"version\":999}");
            reload=level.getServer().reloadResources(level.getServer().getPackRepository().getSelectedIds());reloadStage=2;return false;
        }
        if(reloadStage==2) {
            check("malformed-reload-retains-whole-generation",TradeDefinitions.snapshot().size()==12&&!TradeDefinitions.snapshot().get(id("bandages")).enabled());
            Files.writeString(pack.resolve("data/villagerguardgunners/quartermaster_trades/bandages.json"),
                    "{\"version\":1,\"level\":1,\"inputs\":[{\"type\":\"item\",\"id\":\"minecraft:emerald\",\"count\":7}],\"output\":{\"type\":\"item\",\"id\":\"villagerguardgunners:bandage\",\"count\":2},\"max_uses\":3,\"xp\":2,\"price_multiplier\":0.05}");
            Files.writeString(pack.resolve("data/villagerguardgunners/quartermaster_trades/absent_optional.json"),
                    "{\"version\":1,\"level\":1,\"inputs\":[{\"type\":\"item\",\"id\":\"minecraft:emerald\",\"count\":1}],\"output\":{\"type\":\"item\",\"id\":\"absent_mod:optional_supply\",\"count\":1},\"max_uses\":3,\"xp\":2,\"price_multiplier\":0.05}");
            reload=level.getServer().reloadResources(level.getServer().getPackRepository().getSelectedIds());reloadStage=3;return false;
        }
        if(reloadStage==3) {
            check("price-reload-keeps-old-offer",reloadOffer.getBaseCostA().getCount()==2&&reloadOffer.getResult().getCount()==4&&reloadOffer.getMaxUses()==12);
            var newer=villager(level,1);var revised=offer(newer,"bandages");
            check("new-villager-uses-reloaded-definition",revised.getBaseCostA().getCount()==7&&revised.getResult().getCount()==2&&revised.getMaxUses()==3);
            check("absent-optional-item-omitted",TradeDefinitions.snapshot().size()==13&&newer.getOffers().size()==2);
            Files.delete(pack.resolve("data/villagerguardgunners/quartermaster_trades/absent_optional.json"));
            Files.delete(pack.resolve("data/villagerguardgunners/quartermaster_trades/bandages.json"));
            reload=level.getServer().reloadResources(level.getServer().getPackRepository().getSelectedIds());reloadStage=4;return false;
        }
        check("real-reload-restores-saved-offer",QuartermasterTrades.allowed(reloadOffer));reloadMenu.broadcastChanges();
        check("reload-keeps-saved-price-and-stock",saved.getOffers().size()==10&&saved.getOffers().contains(reloadOffer)&&reloadOffer.getUses()==0&&reloadOffer.getDemand()>0);
        buyer.closeContainer();saved.setTradingPlayer(null);
        Files.writeString(Path.of(System.getProperty("vgg.quartermaster.output")).resolveSibling("trade-offers.snbt"),saved.getOffers().createTag().toString());
        complete=true;return true;
    }
    static List<String> results(){return List.copyOf(CASES);}
    static List<String> restart(ServerLevel level) throws Exception {
        var v=(Villager)level.getEntity(SAVED);check("restart-merchant-loaded",v!=null);
        var expected=TagParser.parseTag(Files.readString(Path.of(System.getProperty("vgg.quartermaster.output")).resolveSibling("trade-offers.snbt")));
        check("restart-saved-offers-identical",expected.equals(v.getOffers().createTag()));check("restart-initialization-idempotent",v.getOffers().size()==10);
        return results();
    }
}
