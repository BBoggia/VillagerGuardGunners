package com.gamergaming.villagerguardgunners.quartermaster.management;

import java.util.*;
import net.minecraft.network.FriendlyByteBuf;

/** Text and scalar pages only: never serialize item NBT, components or arbitrary collection sizes. */
public final class OverviewProtocol {
    public static final int STOCK_ROW=0, NEED_ROW=1, GUARD_ROW=2, HISTORY_ROW=3;
    public static final int MAX_PACKET=32768, MAX_TEXT=256;
    public record Row(int kind,UUID guard,String label,String detail,String status,int requested,int available,int missing,int incoming,int flags,int selection) {
        public Row(int kind,UUID guard,String label,String detail,String status,int requested,int available,int missing,int flags,int selection) {
            this(kind,guard,label,detail,status,requested,available,missing,0,flags,selection);
        }
        public Row {
            if(kind<0||kind>3||label==null||label.length()>96||detail==null||detail.length()>MAX_TEXT||status==null||status.length()>64
                    ||requested<0||requested>1000000||available<0||available>1000000||missing<0||missing>1000000
                    ||incoming<0||incoming>1000000||kind!=NEED_ROW&&incoming!=0||kind==NEED_ROW&&available+incoming+missing!=requested
                    ||flags<0||flags>0xfffff||selection< -1||selection>1279)throw new IllegalArgumentException("Invalid overview row");
        }
        void encode(FriendlyByteBuf b) {
            b.writeVarInt(kind);b.writeBoolean(guard!=null);if(guard!=null)b.writeUUID(guard);
            b.writeUtf(label,96);b.writeUtf(detail,MAX_TEXT);b.writeUtf(status,64);b.writeVarInt(requested);b.writeVarInt(available);b.writeVarInt(missing);b.writeVarInt(incoming);b.writeVarInt(flags);b.writeVarInt(selection);
        }
        static Row decode(FriendlyByteBuf b) {
            return new Row(b.readVarInt(),b.readBoolean()?b.readUUID():null,b.readUtf(96),b.readUtf(MAX_TEXT),b.readUtf(64),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt());
        }
    }
    public record Page(int menu,UUID session,int revision,int generation,int view,int page,int pages,long observedAt,
            boolean refreshing,boolean truncated,boolean restricted,boolean stock,boolean manage,int inspected,int unavailable,
            int stationStatus,List<Row> rows) {
        public Page {
            rows=List.copyOf(rows);
            if(menu<1||menu>100||session==null||revision<1||generation<0||!ManagementProtocol.view(view)||page<0||page>ManagementProtocol.MAX_PAGE
                    ||pages<1||pages>ManagementProtocol.MAX_PAGE+1||page>=pages||observedAt<0||inspected<0||inspected>64||unavailable<0||unavailable>64
                    ||stationStatus<0||stationStatus>=com.gamergaming.villagerguardgunners.quartermaster.service.ServiceStatus.values().length
                    ||rows.size()>ManagementProtocol.PAGE_SIZE||manage&&!stock||!stock&&!rows.isEmpty()
                    ||!manage&&rows.stream().anyMatch(r->r.kind()!=STOCK_ROW))throw new IllegalArgumentException("Invalid overview page");
        }
        public void encode(FriendlyByteBuf b) {
            b.writeVarInt(menu);b.writeUUID(session);b.writeVarInt(revision);b.writeVarInt(generation);b.writeVarInt(view);b.writeVarInt(page);b.writeVarInt(pages);b.writeLong(observedAt);
            b.writeBoolean(refreshing);b.writeBoolean(truncated);b.writeBoolean(restricted);b.writeBoolean(stock);b.writeBoolean(manage);
            b.writeVarInt(inspected);b.writeVarInt(unavailable);b.writeVarInt(stationStatus);b.writeVarInt(rows.size());rows.forEach(r->r.encode(b));
        }
        public static Page decode(FriendlyByteBuf b) {
            if(b.readableBytes()>MAX_PACKET)throw new IllegalArgumentException("Overview packet too large");
            int menu=b.readVarInt();UUID session=b.readUUID();int revision=b.readVarInt(),generation=b.readVarInt(),view=b.readVarInt(),page=b.readVarInt(),pages=b.readVarInt();long at=b.readLong();
            boolean refreshing=b.readBoolean(),truncated=b.readBoolean(),restricted=b.readBoolean(),stock=b.readBoolean(),manage=b.readBoolean();
            int inspected=b.readVarInt(),unavailable=b.readVarInt(),status=b.readVarInt(),size=b.readVarInt();
            if(size<0||size>ManagementProtocol.PAGE_SIZE)throw new IllegalArgumentException("Too many overview rows");
            var rows=new ArrayList<Row>();for(int i=0;i<size;i++)rows.add(Row.decode(b));
            if(b.isReadable())throw new IllegalArgumentException("Trailing overview bytes");
            return new Page(menu,session,revision,generation,view,page,pages,at,refreshing,truncated,restricted,stock,manage,inspected,unavailable,status,rows);
        }
    }
    public static String clean(String text,int max) {
        if(text==null)return "";String s=text.replaceAll("[\\p{Cntrl}§]", " ");return s.substring(0,Math.min(max,s.length()));
    }
    private OverviewProtocol(){}
}
