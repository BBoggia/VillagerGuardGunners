package com.gamergaming.villagerguardgunners.combat.engagement;

/** Registry-independent tick-age arithmetic for defensive aggression memory. */
final class EngagementMemory {
    private EngagementMemory() {
    }

    static boolean isRecent(int currentTick, int eventTick, int memoryTicks) {
        int age = currentTick - eventTick;
        return age >= 0 && age <= memoryTicks;
    }
}
