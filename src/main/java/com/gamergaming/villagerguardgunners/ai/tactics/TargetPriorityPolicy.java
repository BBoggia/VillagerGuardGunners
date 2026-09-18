package com.gamergaming.villagerguardgunners.ai.tactics;

/** Small score changes cannot restart aiming; immediate danger can break persistence. */
public final class TargetPriorityPolicy {
    public static final int PERSISTENCE_TICKS = 30;
    public static final double SWITCH_MARGIN = 8;
    public record Candidate(double distance, boolean inRange, int urgency, int coveringAllies,
                            boolean blockedLane) {
        public double score() {
            return urgency * 30 + (inRange ? 12 : 0) - Math.min(64, distance) * .4
                    - Math.min(2, Math.max(0, coveringAllies)) * 12 - (blockedLane ? 12 : 0);
        }
    }
    public static boolean shouldSwitch(Candidate current, Candidate alternative, long heldTicks) {
        return alternative != null && (current == null ||
                (alternative.score() > current.score() + SWITCH_MARGIN
                && (heldTicks >= PERSISTENCE_TICKS || alternative.urgency() > current.urgency())));
    }
    private TargetPriorityPolicy() { }
}
