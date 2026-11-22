package com.gamergaming.villagerguardgunners.quartermaster.management;

import java.util.*;
import net.minecraft.network.FriendlyByteBuf;

/** Server-owned editor state; clients send choices and identities, never settings batches or costs. */
public final class BulkProtocol {
    public static final int OPEN=0,TOGGLE=1,SQUAD=2,CLEAR=3,POLICY=4,PROFILE=5,REPLACE=6,ASSIGN=7,PREVIEW=8,APPLY=9,PAGE=10,VIEW=11,CLOSE=12;
    // The loaded index holds 4096 guards; retain up to 64 already-selected identities too.
    public static final int PAGE_SIZE=5,MAX_PAGE=831,MAX_PACKET=32768;
    public record Navigation(int menu,UUID session,int sequence,boolean active) {
        public Navigation {
            if(menu<1||menu>100||session==null||sequence<1)throw new IllegalArgumentException("Invalid bulk navigation");
        }
        public void encode(FriendlyByteBuf b){b.writeVarInt(menu);b.writeUUID(session);b.writeVarInt(sequence);b.writeBoolean(active);}
        public static Navigation decode(FriendlyByteBuf b) {
            if(b.readableBytes()>96)throw new IllegalArgumentException("Navigation too large");
            var v=new Navigation(b.readVarInt(),b.readUUID(),b.readVarInt(),b.readBoolean());
            if(b.isReadable())throw new IllegalArgumentException("Trailing navigation data");return v;
        }
    }
    public record Action(int menu,UUID session,int sequence,int action,int value,UUID guard,UUID token) {
        public Action {
            if(menu<1||menu>100||session==null||sequence<1||action<OPEN||action>CLOSE||value< -1||value>MAX_PAGE
                    ||(action==TOGGLE||action==SQUAD)!=(guard!=null)||(action==APPLY)!=(token!=null))throw new IllegalArgumentException("Invalid bulk action");
        }
        public void encode(FriendlyByteBuf b) {
            b.writeVarInt(menu);b.writeUUID(session);b.writeVarInt(sequence);b.writeVarInt(action);b.writeVarInt(value);
            b.writeBoolean(guard!=null);if(guard!=null)b.writeUUID(guard);b.writeBoolean(token!=null);if(token!=null)b.writeUUID(token);
        }
        public static Action decode(FriendlyByteBuf b) {
            if(b.readableBytes()>96)throw new IllegalArgumentException("Bulk action too large");
            var a=new Action(b.readVarInt(),b.readUUID(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean()?b.readUUID():null,b.readBoolean()?b.readUUID():null);
            if(b.isReadable())throw new IllegalArgumentException("Trailing bulk action");return a;
        }
    }
    public record Line(UUID guard,String label,String detail,boolean selected,boolean squad) {
        public Line {if(label==null||label.length()>96||detail==null||detail.length()>1024)throw new IllegalArgumentException("Invalid bulk line");}
        void encode(FriendlyByteBuf b){b.writeBoolean(guard!=null);if(guard!=null)b.writeUUID(guard);b.writeUtf(label,96);b.writeUtf(detail,1024);b.writeBoolean(selected);b.writeBoolean(squad);}
        static Line decode(FriendlyByteBuf b){return new Line(b.readBoolean()?b.readUUID():null,b.readUtf(96),b.readUtf(1024),b.readBoolean(),b.readBoolean());}
    }
    public record State(int menu,UUID session,int revision,int view,int page,int pages,int selected,int excluded,boolean limited,
            int policy,String profile,int replacement,int assignment,int currentPolicy,String currentProfile,int currentReplacement,int currentAssignment,
            UUID token,long expiresAt,String status,int applied,boolean refreshing,List<Line> rows) {
        public State {
            rows=List.copyOf(rows);
            if(menu<1||menu>100||session==null||revision<1||view<0||view>2||page<0||page>MAX_PAGE||pages<1||pages>MAX_PAGE+1||page>=pages
                    ||selected<0||selected>64||excluded<0||excluded>4096||applied<0||applied>64||policy< -1||policy>2||replacement< -1||replacement>1
                    ||assignment< -1||assignment>1||currentPolicy< -2||currentPolicy>2||currentReplacement< -2||currentReplacement>1
                    ||currentAssignment< -2||currentAssignment>1||profile==null||profile.length()>96||currentProfile==null||currentProfile.length()>96
                    ||expiresAt<0||status==null||status.length()>64||rows.size()>PAGE_SIZE)throw new IllegalArgumentException("Invalid bulk state");
        }
        public void encode(FriendlyByteBuf b) {
            b.writeVarInt(menu);b.writeUUID(session);b.writeVarInt(revision);b.writeVarInt(view);b.writeVarInt(page);b.writeVarInt(pages);
            b.writeVarInt(selected);b.writeVarInt(excluded);b.writeBoolean(limited);b.writeVarInt(policy);b.writeUtf(profile,96);b.writeVarInt(replacement);b.writeVarInt(assignment);
            b.writeVarInt(currentPolicy);b.writeUtf(currentProfile,96);b.writeVarInt(currentReplacement);b.writeVarInt(currentAssignment);
            b.writeBoolean(token!=null);if(token!=null)b.writeUUID(token);b.writeLong(expiresAt);b.writeUtf(status,64);b.writeVarInt(applied);b.writeBoolean(refreshing);
            b.writeVarInt(rows.size());rows.forEach(r->r.encode(b));
        }
        public static State decode(FriendlyByteBuf b) {
            if(b.readableBytes()>MAX_PACKET)throw new IllegalArgumentException("Bulk state too large");
            int menu=b.readVarInt();var session=b.readUUID();int revision=b.readVarInt(),view=b.readVarInt(),page=b.readVarInt(),pages=b.readVarInt();
            int selected=b.readVarInt(),excluded=b.readVarInt();boolean limited=b.readBoolean();int policy=b.readVarInt();String profile=b.readUtf(96);
            int replacement=b.readVarInt(),assignment=b.readVarInt(),currentPolicy=b.readVarInt();String currentProfile=b.readUtf(96);
            int currentReplacement=b.readVarInt(),currentAssignment=b.readVarInt();var token=b.readBoolean()?b.readUUID():null;
            long expires=b.readLong();String status=b.readUtf(64);int applied=b.readVarInt();boolean refreshing=b.readBoolean();int size=b.readVarInt();
            if(size<0||size>PAGE_SIZE)throw new IllegalArgumentException("Too many bulk rows");
            List<Line> rows=new ArrayList<>();for(int i=0;i<size;i++)rows.add(Line.decode(b));
            if(b.isReadable())throw new IllegalArgumentException("Trailing bulk state");
            return new State(menu,session,revision,view,page,pages,selected,excluded,limited,policy,profile,replacement,assignment,currentPolicy,currentProfile,currentReplacement,currentAssignment,token,expires,status,applied,refreshing,rows);
        }
    }
    private BulkProtocol(){}
}
