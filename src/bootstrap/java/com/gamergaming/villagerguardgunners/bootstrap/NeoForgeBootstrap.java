package com.gamergaming.villagerguardgunners.bootstrap;

import com.mojang.logging.LogUtils;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import java.io.IOException;
import java.util.Properties;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import tallestegg.guardvillagers.common.entities.Guard;

/** Dependency smoke probe only. Never included in the gameplay artifact. */
@Mod(NeoForgeBootstrap.MOD_ID)
public final class NeoForgeBootstrap {
    public static final String MOD_ID = "vgg_port_bootstrap";

    public NeoForgeBootstrap() {
        NeoForge.EVENT_BUS.addListener(this::serverStarted);
    }

    private void serverStarted(ServerStartedEvent event) {
        Properties expected = new Properties();
        try (var stream = getClass().getResourceAsStream("/bootstrap.properties")) {
            if (stream == null) throw new IllegalStateException("Missing bootstrap version pins");
            expected.load(stream);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read bootstrap version pins", exception);
        }
        expected.forEach((id, version) -> {
            var container = ModList.get().getModContainerById(id.toString()).orElseThrow();
            String actual = container.getModInfo().getVersion().toString();
            if (!actual.equals(version.toString())) {
                throw new IllegalStateException(id + " expected " + version + ", found " + actual);
            }
            LogUtils.getLogger().info("VGG_PORT_DEPENDENCY {}={}", id, actual);
        });
        if (Runtime.version().feature() != 21) throw new IllegalStateException("Expected Java 21");
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse("guardvillagers:guard"))) {
            throw new IllegalStateException("Guard entity is not registered");
        }
        var gun = BuiltInRegistries.ITEM.get(ResourceLocation.parse("tacz:modern_kinetic_gun"));
        if (!(gun instanceof IGun)) throw new IllegalStateException("TaCZ gun API is unavailable");
        // Resolve both APIs without equipping or altering entities in the test world.
        LogUtils.getLogger().info("VGG_PORT_APIS guard={} operator={}", Guard.class.getName(), IGunOperator.class.getName());
        if ("providers-baseline".equals(System.getProperty("vgg.qa.worldgenScenario"))) {
            var level = event.getServer().overworld();
            var origin = new net.minecraft.core.BlockPos(0, 200, 0);
            for (int x=-1;x<=2;x++) for (int z=-1;z<=2;z++) level.getChunk(x,z);
            var template = level.getStructureManager().get(ResourceLocation.parse("skyvillages:house_1")).orElseThrow();
            if (!template.placeInWorld(level, origin, origin,
                    new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
                    net.minecraft.util.RandomSource.create(42), 2)) {
                throw new IllegalStateException("Published Sky Villages template placement failed");
            }
            LogUtils.getLogger().info("VGG_PORT_PROVIDER_TEMPLATE_PLACED skyvillages:house_1 without Villager Guard Gunners or companion data");
        }
        LogUtils.getLogger().info("VGG_PORT_BOOTSTRAP_PASS dependencies={} java=21 registries=ready", expected.size());
    }
}
