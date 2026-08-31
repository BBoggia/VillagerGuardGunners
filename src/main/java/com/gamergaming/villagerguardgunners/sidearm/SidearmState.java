package com.gamergaming.villagerguardgunners.sidearm;

/**
 * Structural ownership of a guard's weapon stacks.
 *
 * <p>This state does not describe TaCZ's draw, reload, or firing state. It
 * describes only which weapon role is expected in the main hand and whether
 * the private storage invariants remain trustworthy.</p>
 */
public enum SidearmState {
    /** The ordinary main-hand weapon is active and no primary is stowed. */
    PRIMARY_ACTIVE(0),

    /** The sidearm is in the main hand and the primary is safely stowed. */
    SIDEARM_ACTIVE(1),

    /** Stored roles are inconsistent and require lossless recovery. */
    RECOVERY_REQUIRED(2);

    private static final SidearmState[] BY_NETWORK_ID = createNetworkIndex();

    private final int networkId;

    SidearmState(int networkId) {
        this.networkId = networkId;
    }

    public int networkId() {
        return networkId;
    }

    /** Unknown values from a newer server fail closed until synchronization recovers. */
    public static SidearmState fromNetworkId(int networkId) {
        return networkId >= 0 && networkId < BY_NETWORK_ID.length
                ? BY_NETWORK_ID[networkId]
                : RECOVERY_REQUIRED;
    }

    private static SidearmState[] createNetworkIndex() {
        int maximumId = 0;
        for (SidearmState state : values()) {
            maximumId = Math.max(maximumId, state.networkId);
        }
        SidearmState[] index = new SidearmState[maximumId + 1];
        java.util.Arrays.fill(index, RECOVERY_REQUIRED);
        for (SidearmState state : values()) {
            index[state.networkId] = state;
        }
        return index;
    }
}
