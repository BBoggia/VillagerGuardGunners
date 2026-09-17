package com.gamergaming.villagerguardgunners.quartermaster.service;

import com.gamergaming.villagerguardgunners.quartermaster.management.*;
import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class BulkProtocolTest {
    @Test void navigationAcknowledgmentIsBoundedAndRoundTrips() {
        var b=new FriendlyByteBuf(Unpooled.buffer());try {
            for(boolean active:new boolean[]{false,true}) {
                var value=new BulkProtocol.Navigation(100,UUID.randomUUID(),9,active);
                b.clear();value.encode(b);assertEquals(value,BulkProtocol.Navigation.decode(b));
                b.clear();value.encode(b);b.writeByte(1);assertThrows(IllegalArgumentException.class,()->BulkProtocol.Navigation.decode(b));
            }
            b.clear();b.writeZero(97);assertThrows(IllegalArgumentException.class,()->BulkProtocol.Navigation.decode(b));
            b.clear();b.writeByte(1);assertThrows(IndexOutOfBoundsException.class,()->BulkProtocol.Navigation.decode(b));
            assertThrows(IllegalArgumentException.class,()->new BulkProtocol.Navigation(1,UUID.randomUUID(),0,true));
        }finally{b.release();}
    }
    @Test void discoveryPaginationDoesNotIncreaseSelectionOrPacketBounds() {
        var id=UUID.randomUUID();var last=new BulkProtocol.Action(1,id,1,BulkProtocol.PAGE,BulkProtocol.MAX_PAGE,null,null);
        var b=new FriendlyByteBuf(Unpooled.buffer());try {last.encode(b);assertEquals(last,BulkProtocol.Action.decode(b));}finally{b.release();}
        assertTrue((BulkProtocol.MAX_PAGE+1)*BulkProtocol.PAGE_SIZE>=4096+64);
        assertThrows(IllegalArgumentException.class,()->new BulkProtocol.Action(1,id,1,BulkProtocol.PAGE,BulkProtocol.MAX_PAGE+1,null,null));
        assertThrows(IllegalArgumentException.class,()->new BulkProtocol.State(1,id,1,0,0,1,65,0,false,-1,"",-1,-1,-1,"",-1,-1,null,0,"select",0,false,List.of()));
    }
    @Test void independentKeepPreservesMixedSettings() {
        var profile=ResourceLocation.parse("villagerguardgunners:rifleman");
        var station=new ServiceStationAssignment(UUID.randomUUID(),ResourceLocation.parse("minecraft:overworld"),BlockPos.ZERO);
        for(var policy:GuardServiceState.Policy.values())for(boolean replace:new boolean[]{true,false}) {
            var current=new ServiceSettings(policy,profile,replace,station);
            assertEquals(current,BulkChoices.KEEP.resolve(current,null));
            var resolved=new BulkChoices(1,null,-1,0).resolve(current,station);
            assertEquals(GuardServiceState.Policy.RESTOCK,resolved.policy());assertEquals(profile,resolved.profile());
            assertEquals(replace,resolved.replaceExisting());assertNull(resolved.assignment());
            var explicit=new BulkChoices(-1,null,0,1).resolve(current,station);
            assertEquals(policy,explicit.policy());assertFalse(explicit.replaceExisting());assertEquals(station,explicit.assignment());
        }
    }
    @Test void allActionsRoundTripAndRequireCorrectIdentityFields() {
        var id=UUID.randomUUID();
        for(int op=0;op<=BulkProtocol.CLOSE;op++) {
            var a=new BulkProtocol.Action(1,id,1,op,0,op==BulkProtocol.TOGGLE||op==BulkProtocol.SQUAD?id:null,op==BulkProtocol.APPLY?id:null);
            var b=new FriendlyByteBuf(Unpooled.buffer());try{a.encode(b);assertEquals(a,BulkProtocol.Action.decode(b));}finally{b.release();}
        }
        assertThrows(IllegalArgumentException.class,()->new BulkProtocol.Action(1,id,1,BulkProtocol.APPLY,0,null,null));
        assertThrows(IllegalArgumentException.class,()->new BulkProtocol.Action(1,id,1,BulkProtocol.TOGGLE,0,null,null));
        assertThrows(IllegalArgumentException.class,()->new BulkProtocol.Action(1,id,1,BulkProtocol.CLEAR,0,id,null));
        assertThrows(IllegalArgumentException.class,()->new BulkChoices(3,null,-1,-1));
        assertThrows(IllegalArgumentException.class,()->new BulkChoices(-1,null,2,-1));
    }
    private BulkProtocol.State state(List<BulkProtocol.Line> rows) {
        return new BulkProtocol.State(1,UUID.randomUUID(),2,1,0,1,2,3,false,-1,"",-1,1,-2,"Mixed",-2,0,UUID.randomUUID(),600,"preview",0,true,rows);
    }
    @Test void mixedStateAndExactIdentitiesRoundTrip() {
        var s=state(List.of(new BulkProtocol.Line(UUID.randomUUID(),"A guard","Before / After",true,true)));
        var b=new FriendlyByteBuf(Unpooled.buffer());try{s.encode(b);assertEquals(s,BulkProtocol.State.decode(b));}finally{b.release();}
    }
    @Test void boundedRowsAndPayloadsRejectMalformedData() {
        var row=new BulkProtocol.Line(null,"Guard","Details",false,false);
        assertThrows(IllegalArgumentException.class,()->state(Collections.nCopies(6,row)));
        assertThrows(IllegalArgumentException.class,()->new BulkProtocol.Line(null,"a".repeat(97),"",false,false));
        var b=new FriendlyByteBuf(Unpooled.buffer());try {
            state(List.of(row)).encode(b);b.writeByte(1);assertThrows(IllegalArgumentException.class,()->BulkProtocol.State.decode(b));
            b.clear();b.writeZero(BulkProtocol.MAX_PACKET+1);assertThrows(IllegalArgumentException.class,()->BulkProtocol.State.decode(b));
            b.clear();b.writeZero(97);assertThrows(IllegalArgumentException.class,()->BulkProtocol.Action.decode(b));
            b.clear();b.writeByte(1);assertThrows(IndexOutOfBoundsException.class,()->BulkProtocol.State.decode(b));
        }finally{b.release();}
    }
}
