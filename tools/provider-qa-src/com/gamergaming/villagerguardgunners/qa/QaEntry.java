package com.gamergaming.villagerguardgunners.qa;

import net.minecraftforge.fml.common.Mod;

/** Selects one installed-server suite without loading the other providers' classes. */
@Mod("vgg_core_qa")
public final class QaEntry {
    public QaEntry() {
        String suite = System.getProperty("vgg.qa.suite", "cgm");
        switch (suite) {
            case "none" -> net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(NoGunAcceptance::started);
            case "cgm" -> new CgmGunAcceptance();
            case "pointblank", "jelly" -> new PointBlankGunAcceptance();
            default -> throw new IllegalArgumentException("Unknown Forge provider QA suite: " + suite);
        }
    }

}
