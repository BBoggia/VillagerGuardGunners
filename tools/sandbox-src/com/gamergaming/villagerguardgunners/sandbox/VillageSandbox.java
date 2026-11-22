package com.gamergaming.villagerguardgunners.sandbox;

import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.init.*;
import com.gamergaming.villagerguardgunners.medical.*;
import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import com.gamergaming.villagerguardgunners.quartermaster.service.*;
import com.gamergaming.villagerguardgunners.village.*;
import com.gamergaming.villagerguardgunners.village.coverage.live.VillageCoverageRuntime;
import com.google.gson.GsonBuilder;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.*;
import com.tacz.guns.api.item.gun.FireMode;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

/** Test scene and attacker director only. Guards retain their normal production AI. */
@Mod.EventBusSubscriber(modid = "villagerguardgunners")
public final class VillageSandbox {
    static final Path DIR = Path.of(System.getProperty("vgg.sandbox.dir"));
    static final boolean POSE_CHECK = Boolean.getBoolean("vgg.sandbox.poseCheck");
    static volatile String poseView;
    static volatile int poseEntityId;
    static final List<Guard> posePatients = new ArrayList<>();
    static final List<Direction> poseDirections = List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST);
    static final boolean ONE_COT = Boolean.getBoolean("vgg.sandbox.oneCot");
    static final boolean VERIFY = Boolean.getBoolean("vgg.sandbox.verify");
    static final String TAG = "vgg_sandbox";
    static final int Y = 61;
    static ServerLevel level;
    static UUID owner, village;
    static final List<Guard> guards = new ArrayList<>(), patients = new ArrayList<>(), medics = new ArrayList<>();
    static final List<Mob> attackers = new ArrayList<>();
    static final Map<UUID, CompoundTag> stagedLives = new HashMap<>();
    static final Map<UUID, ItemStack> patientGuns = new HashMap<>();
    static Guard dry, serviceGuard;
    static volatile boolean ready;
    static boolean running;
    static long started;
    static int pending, wave, shots, dryShots, kills;
    static final Set<UUID> killed = new HashSet<>();
    static boolean carried, resupplied, serviceSupplied;
    static final List<Map<String,Object>> careTrace = new ArrayList<>();
    static volatile String failure;
    static volatile boolean complete;
    static volatile boolean careView;
    static final Set<UUID> delivered = new HashSet<>();
    static final Map<UUID,Map<String,Long>> careEvents = new LinkedHashMap<>();

    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("vggtest").requires(s -> s.hasPermission(2))
                .then(Commands.literal("reset").executes(c -> { setup(c.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("start").executes(c -> { start(); return 1; }))
                .then(Commands.literal("wave").executes(c -> { if (!running) start(); else { pending += 18; wave++; } return 1; }))
                .then(Commands.literal("peaceful").executes(c -> { pending = 0; attackers.forEach(Entity::discard); attackers.clear(); guards.forEach(g -> g.setTarget(null)); announce("Attackers cleared. Guards can recover, restock, and patrol."); return 1; }))
                .then(Commands.literal("status").executes(c -> { c.getSource().sendSuccess(() -> Component.literal(status()), false); return 1; })));
    }
    static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    static BlockPos p(int x, int z) { return new BlockPos(x, Y, z); }
    static void block(int x, int y, int z, BlockState state) { level.setBlock(new BlockPos(x, y, z), state, 3); }
    static void announce(String text) { level.getServer().getPlayerList().broadcastSystemMessage(Component.literal("[Playground] " + text), false); }
    static void write(String name, Object data) {
        try { Files.createDirectories(DIR); Files.writeString(DIR.resolve(name), new GsonBuilder().setPrettyPrinting().create().toJson(data) + "\n"); }
        catch (Exception error) { failure = "Could not record scenario: " + error; }
    }
    static void label(int x, int z, String a, String b, String c, String d) {
        var pos = p(x,z);
        block(x,Y-1,z,Blocks.STONE_BRICKS.defaultBlockState());
        level.setBlock(pos, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 4), 3);
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            var text = sign.getFrontText();
            String[] lines = {a,b,c,d};
            for (int i=0;i<4;i++) text = text.setMessage(i,Component.literal(lines[i]));
            sign.setText(text,true); sign.setChanged();
        }
    }
    static void house(int x, int z) {
        for (int dx=0;dx<7;dx++) for(int dz=0;dz<7;dz++) {
            block(x+dx,Y-1,z+dz,Blocks.COBBLESTONE.defaultBlockState());
            for(int dy=0;dy<4;dy++) if(dx==0||dx==6||dz==0||dz==6)
                block(x+dx,Y+dy,z+dz,(dx%6==0&&dz%6==0?Blocks.OAK_LOG:Blocks.OAK_PLANKS).defaultBlockState());
            block(x+dx,Y+4,z+dz,Blocks.OAK_SLAB.defaultBlockState());
        }
        block(x+6,Y,z+3,Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.EAST));
        block(x+6,Y+1,z+3,Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.EAST).setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));
        block(x+3,Y+1,z,Blocks.GLASS_PANE.defaultBlockState());
        block(x+3,Y+1,z+6,Blocks.GLASS_PANE.defaultBlockState());
        block(x+3,Y+3,z+3,Blocks.LANTERN.defaultBlockState());
        bed(p(x+2,z+2), Blocks.RED_BED.defaultBlockState());
    }
    static void bed(BlockPos head, BlockState state) {
        state=state.setValue(BedBlock.FACING,Direction.EAST);
        level.setBlock(head.west(),state.setValue(BedBlock.PART,BedPart.FOOT),3);
        level.setBlock(head,state.setValue(BedBlock.PART,BedPart.HEAD),3);
    }
    static ItemStack gun(String name,int rounds) {
        return GunItemBuilder.create().setId(id("tacz:"+name)).setFireMode(name.equals("glock_17")?FireMode.SEMI:FireMode.AUTO)
                .setAmmoCount(rounds).setAmmoInBarrel(rounds>0).build();
    }
    static ItemStack ammo(int count) { return AmmoItemBuilder.create().setId(id("tacz:762x39")).setCount(count).build(); }
    static Guard guard(String name,int x,int z,int rounds,boolean hold) {
        Guard g=GuardEntityType.GUARD.get().create(level);
        g.setPos(x+.5,Y,z+.5);g.setOwnerId(owner);g.setPersistenceRequired();g.addTag(TAG);
        g.setCustomName(Component.literal(name));g.setCustomNameVisible(true);
        g.setItemSlot(EquipmentSlot.MAINHAND,gun("ak47",rounds));
        var nativeData=IGunOperator.fromLivingEntity(g).getDataHolder();nativeData.drawTimestamp=nativeData.shootTimestamp=-1;nativeData.meleePrepTickCount=-1;
        GuardOrdersAccess.setOrders(g,GuardOrders.DEFAULT.withAmmoSharing(GuardOrders.ToggleOrder.ENABLED).withMaximumRange(GuardOrders.RangeOrder.BLOCKS_48)
                .withMovement(hold?GuardOrders.MovementOrder.HOLD_POSITION:GuardOrders.MovementOrder.TACTICAL));
        if(rounds>0) GuardAmmoAccess.getAmmoHandler(g).setStackInSlot(0,ammo(192));
        if(hold)g.restrictTo(p(x,z),2);
        g.setNoAi(true); level.addFreshEntity(g); guards.add(g);
        VillageRegistrySavedData.get(level).affiliateGuard(g.getUUID(),village);
        VillageCoverageRuntime.synchronizeGuard(g);return g;
    }
    static Villager villager(int x,int z) {
        Villager v=new Villager(EntityType.VILLAGER,level);v.setPos(x+.5,Y,z+.5);v.setPersistenceRequired();v.addTag(TAG);level.addFreshEntity(v);return v;
    }
    static void setup(ServerPlayer player) {
        level=player.serverLevel();owner=player.getUUID();ready=false;running=false;
        posePatients.clear(); poseView=null; careTrace.clear(); careEvents.clear(); delivered.clear(); careView=false;
        pending=wave=shots=dryShots=kills=0;killed.clear();carried=resupplied=serviceSupplied=false;complete=false;failure=null;
        for(Guard g:guards) { TransportRuntime.cancel(g);MedicRuntime.cancel(g,MedicState.Status.INTERRUPTED); }
        for(Entity e:level.getEntitiesOfClass(Entity.class,new AABB(-64,40,-48,100,85,48),e -> !(e instanceof net.minecraft.world.entity.player.Player))) e.discard();
        guards.clear();patients.clear();medics.clear();attackers.clear();stagedLives.clear();patientGuns.clear();
        if (!(level.getChunkSource().getGenerator() instanceof FlatLevelSource)) throw new IllegalStateException("Playground requires the generated flat test world");
        level.setDayTime(4000);level.setWeatherParameters(999999,0,false,false);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,level.getServer());
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true,level.getServer());
        level.getServer().setDifficulty(Difficulty.NORMAL,true);
        ModConfigs.GUARD_GUNNERS.enabled.accept(true);ModConfigs.GUARD_GUNNERS.medical.enabled.accept(true);
        ModConfigs.GUARD_GUNNERS.medical.medicAi.accept(true);ModConfigs.GUARD_GUNNERS.medical.downed.accept(true);ModConfigs.GUARD_GUNNERS.medical.transport.accept(true);
        ModConfigs.GUARD_GUNNERS.villageDefense.automaticCoverage.accept(true);
        // This rectangle is exclusively the disposable exercise, rebuilt by reset.
        for(int x=-48;x<=80;x++)for(int z=-34;z<=34;z++) {
            block(x,Y-1,z,(Math.abs(z)<=2||x>=-6&&x<=-3?Blocks.DIRT_PATH:Blocks.GRASS_BLOCK).defaultBlockState());
            for(int y=Y;y<=Y+8;y++)block(x,y,z,Blocks.AIR.defaultBlockState());
        }
        var identity=new VillageIdentity(level.dimension().location(),id("villagerguardgunners:sandbox_village"),ChunkPos.asLong(p(-32,0)));
        village=identity.id();var pieces=new ArrayList<VillageFootprint.Piece>();var homes=new ArrayList<BlockPos>();
        for(int x:new int[]{-43,-31})for(int z:new int[]{-25,13}) {
            house(x,z);homes.add(p(x+2,z+2));
            pieces.add(new VillageFootprint.Piece(p(x,z),"NONE",id("minecraft:village/plains/houses/plains_small_house_1"),new VillageFootprint.Bounds(x,Y-1,z,x+6,Y+5,z+6)));
            villager(x+3,z+3);
        }
        var bounds=new VillageFootprint.Bounds(-45,Y-1,-27,12,Y+7,24);
        pieces.add(new VillageFootprint.Piece(p(-45,-27),"NONE",id("test:village_square"),bounds));
        var registry=VillageRegistrySavedData.get(level);registry.observe(identity,VillageFootprint.of(pieces));
        Map<Long,List<BlockPos>> sections=new HashMap<>();homes.forEach(home -> sections.computeIfAbsent(SectionPos.asLong(home),k -> new ArrayList<>()).add(home));sections.forEach(registry::observeHomes);
        block(-11,Y,0,Blocks.BELL.defaultBlockState());
        // Open clinic roof: enough headroom for a medic carrying a patient.
        for(int x=-38;x<=-27;x++)for(int z=-5;z<=6;z++) {
            block(x,Y-1,z,Blocks.WHITE_CONCRETE.defaultBlockState());
            block(x,Y+5,z,Blocks.RED_WOOL.defaultBlockState());
        }
        for(int x:new int[]{-38,-27})for(int z:new int[]{-5,6})for(int y=Y;y<Y+5;y++)block(x,y,z,Blocks.OAK_LOG.defaultBlockState());
        for(int z:new int[]{-2,3}) {
            if(ONE_COT&&z==3)continue;
            var head=p(-33,z);var state=ModBlocks.RECOVERY_COT.get().defaultBlockState().setValue(RecoveryCotBlock.FACING,Direction.EAST);
            level.setBlock(head.west(),state.setValue(RecoveryCotBlock.PART,BedPart.FOOT),3);level.setBlock(head,state.setValue(RecoveryCotBlock.PART,BedPart.HEAD),3);
        }
        level.setBlock(p(-15,-7),ModBlocks.QUARTERMASTER.get().defaultBlockState(),3);
        var station=(QuartermasterBlockEntity)level.getBlockEntity(p(-15,-7));station.placedBy(player);
        station.inventory().setStackInSlot(0,ammo(64));station.inventory().setStackInSlot(1,ammo(64));station.inventory().setStackInSlot(2,new ItemStack(ModItems.BANDAGE.get(),64));
        station.inventory().setStackInSlot(3,gun("ak47",30));station.inventory().setStackInSlot(4,gun("glock_17",17));
        Villager operator=villager(-15,-6);operator.setCustomName(Component.literal("Quartermaster"));
        var stationPos=p(-15,-7);
        level.getPoiManager().take(type -> type.value()==ForgeRegistries.POI_TYPES.getValue(QuartermasterProfession.ID),
                (type,pos) -> pos.equals(stationPos),stationPos,1).orElseThrow(() -> new IllegalStateException("Quartermaster POI not reservable"));
        operator.getBrain().setMemory(MemoryModuleType.POTENTIAL_JOB_SITE,GlobalPos.of(level.dimension(),stationPos));
        net.minecraft.world.entity.ai.behavior.AssignProfessionFromJobSite.create().tryStart(level,operator,level.getGameTime());
        if(operator.getVillagerData().getProfession()!=ForgeRegistries.VILLAGER_PROFESSIONS.getValue(QuartermasterProfession.ID))throw new IllegalStateException("Quartermaster job assignment failed");
        station.operator=operator.getUUID();
        level.setBlock(p(-2,12),ModBlocks.ARMORY.get().defaultBlockState(),3);
        var armory=(com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity)level.getBlockEntity(p(-2,12));
        for(int i=0;i<6;i++)armory.getInventory().setStackInSlot(i,ammo(64));
        for(int z:new int[]{-15,-9,-3,3,9,15}) {
            guard("Defender "+(z+16),7,z,30,true);
            for(int dz=-1;dz<=1;dz++)block(9,Y,z+dz,Blocks.STONE_BRICK_SLAB.defaultBlockState());
        }
        dry=guard("Empty ammo - watch sharing",10,12,0,true);
        serviceGuard=guard("Quartermaster recruit",-11,-9,0,false);
        serviceGuard.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
        var policy=GuardEquipmentServiceAccess.get(serviceGuard);policy.policy(GuardServiceState.Policy.PROFILE);policy.profile(id("villagerguardgunners:rifleman"));
        guard("Village patrol",-20,10,30,false);
        for(int z:new int[]{-3,4}) {
            Guard medic=guard("Medic "+(z<0?"A":"B"),-27,z,17,false);
            medic.setItemSlot(EquipmentSlot.MAINHAND,gun("glock_17",17));GuardAmmoAccess.getAmmoHandler(medic).setStackInSlot(0,ItemStack.EMPTY);
            GuardMedicAccess.get(medic).medic(true);GuardMedicalAccess.get(medic).inventory().setStackInSlot(0,new ItemStack(ModItems.BANDAGE.get(),12));medics.add(medic);
            Guard patient=guard("Casualty "+(z<0?"A":"B"),-24,z,17,false);
            patient.hurt(level.damageSources().generic(),1000);patients.add(patient);patientGuns.put(patient.getUUID(),patient.getMainHandItem().copy());
            CompoundTag life=new CompoundTag();GuardLifeAccess.get(patient).save(life);stagedLives.put(patient.getUUID(),life);
        }
        if(POSE_CHECK) for(int i=0;i<4;i++) {
            var facing=poseDirections.get(i); var head=p(45+i*8,25);
            var state=ModBlocks.RECOVERY_COT.get().defaultBlockState().setValue(RecoveryCotBlock.FACING,facing);
            level.setBlock(head.relative(facing.getOpposite()),state.setValue(RecoveryCotBlock.PART,BedPart.FOOT),3);
            level.setBlock(head,state.setValue(RecoveryCotBlock.PART,BedPart.HEAD),3);
            var patient=guard("Cot facing "+facing.getName(),head.getX(),head.getZ(),17,false);
            patient.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.IRON_CHESTPLATE));
            patient.hurt(level.damageSources().generic(),1000); GuardLifeRuntime.completeTreatment(patient,4,40);
            patient.setPos(TransportRuntime.cotPosition(level,head));
            if(!CotRestRuntime.attach(patient,head))throw new IllegalStateException("Pose cot unavailable: "+facing);
            posePatients.add(patient);
        }
        label(-12,5,"Village Defense","/vggtest start","/vggtest reset","/vggtest status");
        label(-28,8,"MEDICAL AREA","2 downed guards","2 supplied medics",ONE_COT?"1 Recovery Cot":"2 Recovery Cots");
        label(-15,-10,"QUARTERMASTER","Guns + ammo","Medical supplies","Recruit opted in");
        label(1,17,"AMMO TEST","One empty guard","Stocked Armory","Nearby donors");
        label(23,20,"ATTACKERS","Approach from east","/vggtest wave","adds 18 husks");
        player.setGameMode(GameType.CREATIVE);player.removeAllEffects();
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,999999,0,false,false));
        player.teleportTo(level,17,Y+12,28,135,28);
        player.getInventory().clearContent();player.getInventory().setItem(0,new ItemStack(ModItems.ORDERS_LEDGER.get()));
        player.getInventory().setItem(1,new ItemStack(ModItems.SQUAD_COMMAND_LEDGER.get()));player.getInventory().setItem(2,new ItemStack(ModItems.BANDAGE.get(),64));
        player.getInventory().setItem(3,ammo(64));player.getInventory().setItem(4,new ItemStack(ModItems.ARMORY.get()));
        player.getInventory().setItem(5,new ItemStack(ModItems.RECOVERY_COT.get()));
        var book=new ItemStack(Items.WRITTEN_BOOK);var bookTag=book.getOrCreateTag();bookTag.putString("title","Village Defense Playground");bookTag.putString("author","Villager Guard Gunners");
        var page=Component.literal("VILLAGE DEFENSE\n\nClick an action:\n\n");
        for(String[] action:new String[][]{{"START EXERCISE","start"},{"RESET VILLAGE","reset"},{"ADD 18 ATTACKERS","wave"},{"CLEAR ATTACKERS","peaceful"},{"SHOW STATUS","status"}}) {
            page.append(Component.literal(action[0]+"\n\n").withStyle(style -> style.withColor(net.minecraft.ChatFormatting.DARK_BLUE)
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,"/vggtest "+action[1]))));
        }
        var pages=new net.minecraft.nbt.ListTag();pages.add(net.minecraft.nbt.StringTag.valueOf(Component.Serializer.toJson(page)));bookTag.put("pages",pages);
        for(int i=5;i>=0;i--)player.getInventory().setItem(i+1,player.getInventory().getItem(i));player.getInventory().setItem(0,book);player.getInventory().selected=0;
        player.getAbilities().flying=true;player.onUpdateAbilities();
        ready=true;write("ready.json",Map.of("ready",true,"flatWorld",true,"guards",guards.size(),"downed",patients.size(),"medics",medics.size(),"emptyAmmoGuard",dry.getUUID().toString()));
        announce("Ready: "+guards.size()+" guards, two casualties, one empty-ammo guard. /vggtest start begins care and an eastern attack. /vggtest reset rebuilds this exercise.");
    }
    static void start() {
        if (!ready||running) return;
        running=true;started=level.getGameTime();pending=24;wave=1;
        guards.forEach(g -> g.setNoAi(false));announce("Exercise started. 24 husks approach from the EAST. /vggtest peaceful clears attackers; /vggtest reset rebuilds.");
    }
    static void spawnAttacker() {
        var mob=EntityType.HUSK.create(level);int lane=(pending%7-3)*4;
        mob.setPos(44.5,Y,lane+.5);mob.setPersistenceRequired();mob.addTag(TAG);
        mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.20);level.addFreshEntity(mob);attackers.add(mob);pending--;
    }
    static String status() {
        return "Wave "+wave+" | gunshots "+shots+" | kills "+kills+" | attackers "+attackers.stream().filter(Entity::isAlive).count()
                +" (+"+pending+") | dry guard fired "+dryShots+" | revived "+patients.stream().filter(g -> g.isAlive()&&!GuardLifeRuntime.blocked(g)).count()+"/2"
                +" | carry observed "+carried+" | Quartermaster equipped "+serviceSupplied;
    }
    @SubscribeEvent public static void shot(GunShootEvent event) {
        if(event.getLogicalSide()==LogicalSide.SERVER&&event.getShooter() instanceof Guard g&&guards.contains(g)) { shots++;if(g==dry)dryShots++; }
    }
    @SubscribeEvent public static void death(LivingDeathEvent event) { if(attackers.contains(event.getEntity())&&killed.add(event.getEntity().getUUID()))kills++; }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!ready||level==null||level.getServer()!=event.getServer()||complete)return;
        try {
            if(!running) { for(Guard g:patients) { GuardLifeAccess.get(g).load(stagedLives.get(g.getUUID()));g.setHealth(1); } return; }
            long elapsed=level.getGameTime()-started;
            if(POSE_CHECK&&elapsed>=300&&elapsed<500) {
                int index=(int)((elapsed-300)/50); var patient=posePatients.get(index);
                if((elapsed-300)%50==0) { var viewer=level.getServer().getPlayerList().getPlayer(owner); if(viewer!=null)viewer.teleportTo(level,patient.getX()+3,64,patient.getZ()+5,150,22); }
                if((elapsed-300)%50==10) {
                    String view="cot-"+poseDirections.get(index).getName();
                    write(view+"-server.json",Map.of("position",patient.position().toString(),"onCot",TransportRuntime.onCot(patient),"cot",GuardLifeAccess.cot(patient).head().toShortString(),"pose",patient.getPose().name()));
                    poseEntityId=patient.getId();poseView=view+".png";
                }
            }
            if(VERIFY&&elapsed==180) { var viewer=level.getServer().getPlayerList().getPlayer(owner); if(viewer!=null)viewer.teleportTo(level,-25,64,10,150,20); }
            if(elapsed==220)careView=true;
            if(elapsed%5==0&&elapsed<=1800) for(Guard patient:patients) careTrace.add(Map.of(
                    "tick",elapsed,"patient",patient.getUUID().toString(),"stage",GuardLifeAccess.get(patient).stage().name(),
                    "position",patient.position().toString(),"onCot",TransportRuntime.onCot(patient),"passenger",patient.isPassenger(),
                    "medics",medics.stream().map(m->m.position()+" "+m.goalSelector.getAvailableGoals().stream().map(g->g.getGoal()).filter(TransportMovementGoal.class::isInstance).map(g->((TransportMovementGoal)g).status()).toList()).toList()));
            if(pending>0&&elapsed%20==0)for(int i=0;i<3&&pending>0;i++)spawnAttacker();
            if(elapsed%20==0)for(Mob enemy:attackers)if(enemy.isAlive()) {
                Guard target=guards.stream().filter(g -> g.isAlive()&&!GuardLifeRuntime.blocked(g))
                        .min(Comparator.comparingDouble(enemy::distanceToSqr)).orElse(null);
                enemy.setTarget(target);
            }
            for(Guard patient:patients) {
                var events=careEvents.computeIfAbsent(patient.getUUID(),ignored->new LinkedHashMap<>());
                String stage=GuardLifeAccess.get(patient).stage().name(); events.putIfAbsent(stage,elapsed);
                if(TransportRuntime.onCot(patient)) { delivered.add(patient.getUUID()); events.putIfAbsent("DELIVERED",elapsed); }
                if(stage.equals("RECOVERING"))events.putIfAbsent(TransportRuntime.onCot(patient)?"COT_REVIVAL":"FIELD_REVIVAL",elapsed);
                var treatment=MedicRuntime.lease(patient);
                if(treatment!=null&&treatment.patient==patient&&treatment.care!=null&&treatment.preparedAt>=0)
                    events.putIfAbsent(treatment.care.mode().name()+"_CHANNEL",elapsed);
            }
            carried|=patients.stream().anyMatch(Entity::isPassenger);
            resupplied|=dry.getMainHandItem().getItem() instanceof IGun gun&&gun.getCurrentAmmoCount(dry.getMainHandItem())>0;
            serviceSupplied|=serviceGuard.getMainHandItem().getItem() instanceof IGun;
            if(elapsed%100==0) { write("status.json",Map.of("status",status(),"elapsedTicks",elapsed,"dryReserve",GuardAmmoAccess.getAmmoHandler(dry).getStackInSlot(0).getCount(),
                    "serviceStatus",GuardEquipmentServiceAccess.get(serviceGuard).status.toString(),"dryPosition",dry.position().toString(),
                    "dryDiagnostics",dry.goalSelector.getAvailableGoals().stream().map(g -> g.getGoal()).filter(com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal.class::isInstance).map(g -> ((com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal)g).captureDiagnostics().toString()).findFirst().orElse("No gun goal"),"serviceEligibility",ServiceEligibility.guard(serviceGuard).toString(),
                    "stationStatus",(level.getBlockEntity(p(-15,-7)) instanceof QuartermasterBlockEntity station ? ServiceEligibility.station(station).toString() : "MISSING")));System.out.println("VGG_SANDBOX "+status()); }
            if(VERIFY&&elapsed>=600) {
                boolean revived=patients.stream().allMatch(g -> g.isAlive()&&!GuardLifeRuntime.blocked(g));
                boolean carePassed=delivered.size()==(ONE_COT?1:2)&&careEvents.values().stream().allMatch(e->e.containsKey("ACTIVE"));
                if(ONE_COT) {
                    var cot=careEvents.values().stream().filter(e->e.containsKey("COT_REVIVAL")).findFirst();
                    var field=careEvents.values().stream().filter(e->e.containsKey("FIELD_REVIVAL")).findFirst();
                    carePassed &= cot.isPresent()&&field.isPresent()&&cot.get().getOrDefault("ACTIVE",Long.MAX_VALUE)<field.get().getOrDefault("ACTIVE",Long.MIN_VALUE);
                }
                boolean pass=shots>=12&&kills>=3&&resupplied&&dryShots>0&&carried&&revived&&serviceSupplied&&carePassed;
                if(pass||elapsed>=1800) {
                    write("care-trace.json",careTrace);
                    write("verification.json",Map.of("passed",pass,"status",status(),"resupplied",resupplied,"elapsedTicks",elapsed,"oneCot",ONE_COT,"delivered",delivered.size(),"careEvents",careEvents,
                            "patientIdentityPreserved",patients.stream().allMatch(g -> level.getEntity(g.getUUID())==g&&patientGuns.containsKey(g.getUUID()))));complete=true;
                }
            }
        } catch(Throwable error) {failure=error.toString();write("verification.json",Map.of("passed",false,"error",failure));complete=true;}
    }
}
