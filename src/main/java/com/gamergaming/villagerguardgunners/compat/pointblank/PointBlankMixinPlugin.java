package com.gamergaming.villagerguardgunners.compat.pointblank;

import com.gamergaming.villagerguardgunners.guns.InstalledTargetMixinPlugin;

public final class PointBlankMixinPlugin extends InstalledTargetMixinPlugin {
    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        PointBlankApi api = PointBlankApi.INSTALLED;
        return api != PointBlankApi.NONE && (mixin.endsWith(".HeldItemRendererMixin") || api.matchesTarget(target));
    }
}
