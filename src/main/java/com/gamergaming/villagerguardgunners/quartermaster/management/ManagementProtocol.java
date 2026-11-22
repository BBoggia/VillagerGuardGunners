package com.gamergaming.villagerguardgunners.quartermaster.management;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/** Bounded navigation and capability messages; row pages use OverviewProtocol. */
public final class ManagementProtocol {
    public static final int SUPPLIES=0, GUARDS=1, TRADE=2, STOCK=3, REFRESH=4, NEEDS=5, SERVICE=6, FILTER=7, CLEAR_FILTER=8, FORGET=9;
    public static final int PAGE_SIZE=20, MAX_PAGE=63, MAX_PACKET=96;
    public static final int NO_OPERATOR=0, READY=1, BUSY=2, UNSAFE=3, EMPTY_HAND=4;
    public static boolean view(int id){return id==SUPPLIES||id==GUARDS||id==NEEDS||id==SERVICE;}
    public record Action(int menu, UUID session, int sequence, int action, int page,int selection,int generation) {
        public Action(int menu,UUID session,int sequence,int action,int page){this(menu,session,sequence,action,page,-1,0);}
        public Action {
            if(menu<1||menu>100||session==null||sequence<1||action<0||action>FORGET||page<0||page>MAX_PAGE||selection< -1||selection>1279||generation<0)
                throw new IllegalArgumentException("Invalid Quartermaster action");
        }
        public static Action decode(FriendlyByteBuf b) {
            size(b);var value=new Action(b.readVarInt(),b.readUUID(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt());end(b);return value;
        }
        public void encode(FriendlyByteBuf b) {b.writeVarInt(menu);b.writeUUID(session);b.writeVarInt(sequence);b.writeVarInt(action);b.writeVarInt(page);b.writeVarInt(selection);b.writeVarInt(generation);}
    }
    public record Summary(int menu, UUID session, int revision, int tab, int page, int pages,
            boolean stock, boolean manage, int tradeStatus, int occupied, int freeReturns, int links) {
        public Summary {
            if(menu<1||menu>100||session==null||revision<1||!view(tab)||page<0||page>MAX_PAGE||pages<1||pages>MAX_PAGE+1||page>=pages
                    ||tradeStatus<NO_OPERATOR||tradeStatus>EMPTY_HAND||occupied< -1||occupied>27
                    ||freeReturns< -1||freeReturns>9||links< -1||links>4||manage&&!stock
                    ||!stock&&(occupied!=-1||freeReturns!=-1||links!=-1))throw new IllegalArgumentException("Invalid Quartermaster summary");
        }
        public static Summary decode(FriendlyByteBuf b) {
            size(b);var v=new Summary(b.readVarInt(),b.readUUID(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),
                    b.readBoolean(),b.readBoolean(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt());end(b);return v;
        }
        public void encode(FriendlyByteBuf b) {
            b.writeVarInt(menu);b.writeUUID(session);b.writeVarInt(revision);b.writeVarInt(tab);b.writeVarInt(page);b.writeVarInt(pages);
            b.writeBoolean(stock);b.writeBoolean(manage);b.writeVarInt(tradeStatus);b.writeVarInt(occupied);b.writeVarInt(freeReturns);b.writeVarInt(links);
        }
    }
    private static void size(FriendlyByteBuf b) {if(b.readableBytes()>MAX_PACKET)throw new IllegalArgumentException("Quartermaster packet too large");}
    private static void end(FriendlyByteBuf b) {if(b.isReadable())throw new IllegalArgumentException("Trailing Quartermaster payload");}
    /** Per-menu action budget; rejected/replayed packets never authorize a second action. */
    public static final class Gate {
        private int sequence;
        private long nextAction=Long.MIN_VALUE, nextRefresh=Long.MIN_VALUE;
        public boolean accept(int incoming, int action, long tick) {
            if(incoming<=sequence)return false;
            sequence=incoming;
            if(tick<nextAction||action==REFRESH&&tick<nextRefresh)return false;
            nextAction=tick+4;
            if(action==REFRESH)nextRefresh=tick+20;
            return true;
        }
    }
    private ManagementProtocol() {}
}
