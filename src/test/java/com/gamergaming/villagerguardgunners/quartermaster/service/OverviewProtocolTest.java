package com.gamergaming.villagerguardgunners.quartermaster.service;

import com.gamergaming.villagerguardgunners.quartermaster.management.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OverviewProtocolTest {
    private final UUID session=UUID.randomUUID();
    private OverviewProtocol.Row row(int kind){return new OverviewProtocol.Row(kind,null,"tacz:762x39","Reserved and tagged variants stay distinct","",64,20,44,4,0);}
    private OverviewProtocol.Page page(List<OverviewProtocol.Row> rows,boolean stock,boolean manage){return new OverviewProtocol.Page(2,session,7,3,ManagementProtocol.NEEDS,1,2,400,false,true,true,stock,manage,32,3,1,rows);}
    @Test void boundedPageRoundTrip(){
        var value=page(Collections.nCopies(20,row(1)),true,true);var b=new FriendlyByteBuf(Unpooled.buffer());
        try{value.encode(b);assertTrue(b.readableBytes()<OverviewProtocol.MAX_PACKET);assertEquals(value,OverviewProtocol.Page.decode(b));}finally{b.release();}
    }
    @Test void guardsNeverFitIntoStockOnlyOrVisitorPage(){
        assertThrows(IllegalArgumentException.class,()->page(List.of(row(2)),true,false));
        assertThrows(IllegalArgumentException.class,()->page(List.of(row(0)),false,false));
        assertThrows(IllegalArgumentException.class,()->page(List.of(),false,true));
    }
    @Test void rejectsTooManyRowsAndLongText(){
        assertThrows(IllegalArgumentException.class,()->page(Collections.nCopies(21,row(0)),true,true));
        assertThrows(IllegalArgumentException.class,()->new OverviewProtocol.Row(0,null,"x".repeat(97),"","",0,0,0,0,-1));
        assertThrows(IllegalArgumentException.class,()->new OverviewProtocol.Row(0,null,"","x".repeat(257),"",0,0,0,0,-1));
    }
    @Test void rejectsOversizedTrailingAndTruncatedPackets(){
        var b=new FriendlyByteBuf(Unpooled.buffer());try{
            page(List.of(),true,true).encode(b);b.writeByte(0);assertThrows(IllegalArgumentException.class,()->OverviewProtocol.Page.decode(b));
            b.clear();b.writeZero(OverviewProtocol.MAX_PACKET+1);assertThrows(IllegalArgumentException.class,()->OverviewProtocol.Page.decode(b));
            b.clear();b.writeByte(2);assertThrows(IndexOutOfBoundsException.class,()->OverviewProtocol.Page.decode(b));
        }finally{b.release();}
    }
    @Test void filtersCarrySnapshotGenerationAndPageBounds(){
        var action=new ManagementProtocol.Action(2,session,5,ManagementProtocol.FILTER,0,19,6);var b=new FriendlyByteBuf(Unpooled.buffer());
        try{action.encode(b);assertEquals(action,ManagementProtocol.Action.decode(b));}finally{b.release();}
        assertThrows(IllegalArgumentException.class,()->new ManagementProtocol.Action(2,session,5,ManagementProtocol.GUARDS,64));
        assertThrows(IllegalArgumentException.class,()->new ManagementProtocol.Action(2,session,5,ManagementProtocol.FILTER,0,1280,6));
    }
    @Test void safeLabelsStripFormattingAndRespectCaps(){assertEquals("A B C",OverviewProtocol.clean("A\nB§C",96));assertEquals("abcd",OverviewProtocol.clean("abcdef",4));}
    @Test void incomingCountRoundTripsAndMustBalanceNeeds() {
        var row=new OverviewProtocol.Row(OverviewProtocol.NEED_ROW,null,"ammo","","",64,0,4,60,0,0);
        var value=page(List.of(row),true,true);var b=new FriendlyByteBuf(Unpooled.buffer());
        try{value.encode(b);assertEquals(value,OverviewProtocol.Page.decode(b));}finally{b.release();}
        assertThrows(IllegalArgumentException.class,()->new OverviewProtocol.Row(1,null,"","","",64,0,64,60,0,0));
        assertThrows(IllegalArgumentException.class,()->new OverviewProtocol.Row(1,null,"","","",64,0,64,-1,0,0));
        assertThrows(IllegalArgumentException.class,()->new OverviewProtocol.Row(0,null,"","","",64,0,4,60,0,0));
    }
}
