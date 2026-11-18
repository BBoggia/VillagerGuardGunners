package com.gamergaming.villagerguardgunners.qa;

import net.neoforged.fml.common.Mod;

/**
 * Entry point of the installed-server test mod. It names no gun mod, so a
 * suite can run on a server without TaCZ; the TaCZ suites are loaded only
 * when one of them is selected.
 */
@Mod("vgg_core_qa")
public final class QaEntry {
    public QaEntry() {
        String suite = System.getProperty("vgg.qa.suite", "core");
        switch (suite) {
            case "cgm" -> new CgmGunAcceptance();
            case "pointblank", "jelly", "pointblank_no_tacz" -> new PointBlankGunAcceptance();
            default -> TaczSuites.start();
        }
    }

    /** Keeps the TaCZ suites' classes from loading on a server without TaCZ. */
    private static final class TaczSuites {
        static void start() {
            new CoreGunAcceptance();
        }
    }
}
