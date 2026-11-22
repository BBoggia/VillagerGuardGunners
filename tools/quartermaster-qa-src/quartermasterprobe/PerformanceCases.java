package quartermasterprobe;

import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity;
import com.gamergaming.villagerguardgunners.init.ModBlocks;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import com.gamergaming.villagerguardgunners.quartermaster.management.*;
import com.gamergaming.villagerguardgunners.quartermaster.service.*;
import com.gamergaming.villagerguardgunners.menu.QuartermasterManagementMenu;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.*;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

final class PerformanceCases {
    private static final List<Guard> guards=new ArrayList<>();
    private static final List<QuartermasterBlockEntity> stations=new ArrayList<>();
    private static final List<Villager> operators=new ArrayList<>();
    private static final List<FakePlayer> viewers=new ArrayList<>();
    private static final List<Counter> connections=new ArrayList<>();
    private static final List<Map<String,Object>> stages=new ArrayList<>();
    private static final List<Long> ticks=new ArrayList<>(),menuTimes=new ArrayList<>(),queryTimes=new ArrayList<>();
    private static ArmoryBlockEntity armory;
    private static int stage=-1,started,forced,maxWork,maxServedPerSecond,previousServed,first,last,completedAt;
    private static long tickStart,lastTickNanos;
    static void beforeTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if("performance".equals(System.getProperty("vgg.acceptance.mode"))&&event.phase==net.minecraftforge.event.TickEvent.Phase.START)tickStart=System.nanoTime();
    }
    static void afterTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if("performance".equals(System.getProperty("vgg.acceptance.mode"))&&event.phase==net.minecraftforge.event.TickEvent.Phase.END)lastTickNanos=System.nanoTime()-tickStart;
    }
    private static final Set<UUID> served=new HashSet<>();
    private static ResourceLocation id(String s){return ResourceLocation.parse(s);}
    private static class Counter extends Connection {
        long packets,bytes;
        Counter(){super(PacketFlow.SERVERBOUND);}
        @Override public void send(Packet<?> packet){var buf=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try{packet.write(buf);packets++;bytes+=buf.readableBytes();}finally{buf.release();}}
        @Override public void send(Packet<?> packet,PacketSendListener listener){send(packet);}
    }
    private static int reserve(Guard g){int n=0;for(int i=0;i<3;i++)n+=GuardAmmoAccess.getAmmoHandler(g).getStackInSlot(i).getCount();return n;}
    private static void setup(ServerLevel l) {
        l.setDayTime(3000);l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,l.getServer());l.getGameRules().getRule(GameRules.RULE_MAX_ENTITY_CRAMMING).set(0,l.getServer());
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){l.getChunk(x,z);l.setChunkForced(x,z,true);}
        for(int x=-20;x<=20;x++)for(int z=-20;z<=20;z++)l.setBlockAndUpdate(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState());
        for(int edge=-20;edge<=20;edge++)for(int y=80;y<=82;y++)for(var p:List.of(new BlockPos(-20,y,edge),new BlockPos(20,y,edge),new BlockPos(edge,y,-20),new BlockPos(edge,y,20)))l.setBlockAndUpdate(p,Blocks.STONE_BRICKS.defaultBlockState());
        for(int i=0;i<4;i++) {
            var p=new FakePlayer(l,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"PerfViewer"+i));p.setPos(i%2==0?-5.5:6.5,80,i<2?-4.5:7.5);viewers.add(p);
            var counter=new Counter();p.connection=new ServerGamePacketListenerImpl(l.getServer(),counter,p);connections.add(counter);l.getServer().getPlayerList().op(p.getGameProfile());p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE,30000));
            var pos=new BlockPos(i%2==0?-6:6,80,i<2?-6:6);l.setBlockAndUpdate(pos,ModBlocks.QUARTERMASTER.get().defaultBlockState());
            // A zero-speed operator cannot walk back after being pushed by a crowd.
            // A physical work booth keeps it nearby without teleporting or disabling AI.
            for(var wall:List.of(pos.offset(-1,0,1),pos.offset(1,0,1),pos.offset(0,0,2)))for(int y=0;y<2;y++)l.setBlockAndUpdate(wall.above(y),Blocks.STONE_BRICKS.defaultBlockState());
            l.setBlockAndUpdate(pos.above(),Blocks.STONE_BRICKS.defaultBlockState());
            var s=(QuartermasterBlockEntity)l.getBlockEntity(pos);s.placedBy(p);s.changeSetting(p,3);s.changeSetting(p,3);stations.add(s);
            var v=new Villager(EntityType.VILLAGER,l);v.setPos(pos.getX()+.5,80,pos.getZ()+1.5);v.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);v.setInvulnerable(true);
            v.setVillagerData(v.getVillagerData().setProfession(ForgeRegistries.VILLAGER_PROFESSIONS.getValue(QuartermasterProfession.ID)));v.refreshBrain(l);v.getBrain().setMemory(MemoryModuleType.JOB_SITE,GlobalPos.of(l.dimension(),pos));l.addFreshEntity(v);s.operator=v.getUUID();operators.add(v);
        }
        var ap=new BlockPos(0,80,0);l.setBlockAndUpdate(ap,ModBlocks.ARMORY.get().defaultBlockState());armory=(ArmoryBlockEntity)l.getBlockEntity(ap);
        int count=Integer.getInteger("vgg.acceptance.guards",25);
        for(int i=0;i<count;i++) {
            var s=stations.get(i%4);var pos=((BlockEntity)s).getBlockPos();var g=GuardEntityType.GUARD.get().create(l);
            g.setPos(pos.getX()-2+(i/4)%5,80,pos.getZ()+3+(i/20));g.setInvulnerable(true);g.setNoAi(true);g.setOwnerId(viewers.get(0).getUUID());l.addFreshEntity(g);
            var gun=GunItemBuilder.create().setId(id("tacz:ak47")).build();((IGun)gun.getItem()).setCurrentAmmoCount(gun,30);((IGun)gun.getItem()).setBulletInBarrel(gun,true);g.setItemSlot(EquipmentSlot.MAINHAND,gun);
            GuardEquipmentServiceAccess.get(g).assignment(new ServiceStationAssignment(s.stationId(),l.dimension().location(),pos));guards.add(g);
            var data=IGunOperator.fromLivingEntity(g).getDataHolder();data.drawTimestamp=-1;data.shootTimestamp=-1;data.meleePrepTickCount=-1;
        }
        forced=l.getForcedChunks().size();
    }
    private static void start(ServerLevel l,int tick,int next) {
        stage=next;started=tick;ticks.clear();menuTimes.clear();queryTimes.clear();served.clear();first=last=completedAt=0;maxWork=previousServed=maxServedPerSecond=0;
        for(var p:viewers)p.closeContainer();
        for(var g:guards){QuartermasterServiceRuntime.cancel(g,ServiceStatus.RETRY);GuardEquipmentServiceAccess.get(g).policy(stage==0?GuardServiceState.Policy.MANUAL:GuardServiceState.Policy.RESTOCK);GuardEquipmentServiceAccess.get(g).nextAttempt=0;
            GuardAmmoAccess.getAmmoHandler(g).setStackInSlot(0,AmmoItemBuilder.create().setId(id("tacz:762x39")).setCount(60).build());GuardAmmoAccess.getAmmoHandler(g).setStackInSlot(1,AmmoItemBuilder.create().setId(id("tacz:762x39")).setCount(3).build());GuardAmmoAccess.getAmmoHandler(g).setStackInSlot(2,ItemStack.EMPTY);g.setNoAi(false);}
        armory.getInventory().setStackInSlot(0,AmmoItemBuilder.create().setId(id("tacz:762x39")).setCount(60).build());armory.getInventory().setStackInSlot(1,AmmoItemBuilder.create().setId(id("tacz:762x39")).setCount(60).build());
        if(stage==2)for(int i=0;i<4;i++)AcceptanceProbe.check("open-scale-view-"+i,QuartermasterManagement.open(viewers.get(i),stations.get(i),null));
        for(var c:connections){c.packets=0;c.bytes=0;}
        System.out.println("VGG performance stage="+stage+" guards="+guards.size());
    }
    static boolean tick(ServerLevel l,int tick) throws Exception {
        if(tick==20)setup(l);
        if(tick==50) {
            for(int i=0;i<4;i++){var s=stations.get(i);var pos=((BlockEntity)s).getBlockPos();l.getPoiManager().take(t->t.value()==ForgeRegistries.POI_TYPES.getValue(QuartermasterProfession.ID),(t,p)->p.equals(pos),pos,1);s.changeSetting(viewers.get(i),4);AcceptanceProbe.check("shared-armory-linked-"+i,s.linkedArmories().contains(new BlockPos(0,80,0)));}
            start(l,tick,0);
        }
        if(stage<0)return false;
        int elapsed=tick-started;
        if(elapsed%200==0)for(var s:stations)if(ServiceEligibility.inspectStation(s)!=ServiceStatus.READY)throw new AssertionError("Scale fixture operator unavailable at "+((BlockEntity)s).getBlockPos());
        if(stage>0&&elapsed>0&&elapsed%600==0)System.out.println("VGG performance progress stage="+stage+" elapsed="+elapsed+" served="+served.size()+"/"+guards.size());
        long begin=System.nanoTime();
        if(stage==2)for(var p:viewers)((AbstractContainerMenu)p.containerMenu).broadcastChanges();
        long menuNanos=System.nanoTime()-begin;
        if(elapsed>100){if(lastTickNanos<=0)throw new AssertionError("Missing complete tick timer");ticks.add(lastTickNanos);menuTimes.add(menuNanos);}
        if(stage==2&&elapsed%20==0){int loaded=l.getChunkSource().getLoadedChunksCount();begin=System.nanoTime();for(int i=0;i<4;i++)OverviewRuntime.scope(viewers.get(i),new ManagementSession(viewers.get(i),stations.get(i),null));queryTimes.add(System.nanoTime()-begin);if(loaded!=l.getChunkSource().getLoadedChunksCount())throw new AssertionError("Overview scope loaded chunks");}
        maxWork=Math.max(maxWork,OverviewRuntime.lastWork(l));
        for(var g:guards)if(reserve(g)==64&&served.add(g.getUUID())){if(first==0)first=elapsed;last=elapsed;}
        if(elapsed%20==0){maxServedPerSecond=Math.max(maxServedPerSecond,served.size()-previousServed);previousServed=served.size();}
        if(stage>0&&served.size()==guards.size()&&completedAt==0)completedAt=elapsed;
        int duration=stage==0?400:guards.size()*200+400;
        if(elapsed<duration&&(completedAt==0||elapsed<completedAt+100))return false;
        duration=elapsed;
        if(stage>0&&served.size()!=guards.size()) {
            System.out.println("VGG performance incomplete served="+served.size()+"/"+guards.size());
            for(var s:stations)System.out.println("VGG station "+((BlockEntity)s).getBlockPos()+" eligibility="+ServiceEligibility.inspectStation(s));
            for(var g:guards)System.out.println("VGG guard pos="+g.position()+" ammo="+reserve(g)+" state="+GuardEquipmentServiceAccess.get(g).status+" eligibility="+ServiceEligibility.inspectGuard(g)+" plan="+(QuartermasterServiceRuntime.plan(g)!=null));
        }
        if(stage>0)AcceptanceProbe.check("all-guards-served-stage-"+stage,served.size()==guards.size());
        AcceptanceProbe.check("no-extra-forced-chunks-stage-"+stage,l.getForcedChunks().size()==forced);
        AcceptanceProbe.check("capture-budget-stage-"+stage,maxWork<=16);
        AcceptanceProbe.check("one-transfer-per-second-stage-"+stage,maxServedPerSecond<=1);
        int total=guards.stream().mapToInt(PerformanceCases::reserve).sum();for(int i=0;i<armory.getInventory().getSlots();i++)total+=armory.getInventory().getStackInSlot(i).getCount();
        AcceptanceProbe.check("ammo-conserved-stage-"+stage,total==guards.size()*63+120);
        if(stage==2)AcceptanceProbe.check("open-menus-emit-encoded-packets",connections.stream().mapToLong(c->c.packets).sum()>0&&connections.stream().mapToLong(c->c.bytes).sum()>0);
        var row=new LinkedHashMap<String,Object>();row.put("stage",List.of("manual-no-screens","service-no-screens","service-four-screens").get(stage));row.put("samples",ticks.size());row.put("serverTickMillis",metrics(ticks));row.put("fourMenuBroadcastMillis",metrics(menuTimes));row.put("fourScopeQueryMillis",metrics(queryTimes));row.put("maxCapturesPerTick",maxWork);row.put("served",served.size());row.put("firstDeliveryTicks",first);row.put("lastDeliveryTicks",last);row.put("maxTransfersPerSecond",maxServedPerSecond);
        row.put("encodedPackets",connections.stream().mapToLong(c->c.packets).sum());row.put("encodedPayloadBytes",connections.stream().mapToLong(c->c.bytes).sum());row.put("measuredTicks",duration);stages.add(row);
        if(stage<2){start(l,tick,stage+1);return false;}
        for(var g:guards)GuardEquipmentServiceAccess.get(g).assignment(new ServiceStationAssignment(stations.get(0).stationId(),l.dimension().location(),((BlockEntity)stations.get(0)).getBlockPos()));
        var scope=OverviewRuntime.scope(viewers.get(0),new ManagementSession(viewers.get(0),stations.get(0),null));
        AcceptanceProbe.check("coverage-cap-reports-truncation",scope.members().size()==Math.min(64,guards.size())&&scope.truncated()==(guards.size()>64));
        Files.writeString(AcceptanceProbe.out().resolveSibling("performance.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("guards",guards.size(),"stations",4,"sharedArmories",1,"stages",stages,"timingNote","Installed Forge; same entities and no-screen manual baseline. AI-enabled guards, immobile AI-enabled operators. First 100 ticks excluded from latency samples. Four FakePlayer menus generate real encoded packets without sockets/rendering; payload bytes exclude transport framing. Explicit four-scope query samples are additional observer work. Fixture resets ammo between stages. Hardware-specific measurements."))+"\n");
        for(var p:viewers)p.closeContainer();return true;
    }
    private static Map<String,Double> metrics(List<Long> samples){if(samples.isEmpty())return Map.of();var v=samples.stream().mapToLong(Long::longValue).sorted().toArray();return Map.of("p50",v[(v.length-1)/2]/1e6,"p95",v[(int)((v.length-1)*.95)]/1e6,"p99",v[(int)((v.length-1)*.99)]/1e6,"max",v[v.length-1]/1e6);}
}
