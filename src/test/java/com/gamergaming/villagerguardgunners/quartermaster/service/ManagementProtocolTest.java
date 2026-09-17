package com.gamergaming.villagerguardgunners.quartermaster.service;

import com.gamergaming.villagerguardgunners.quartermaster.management.ManagementProtocol;
import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class ManagementProtocolTest {
    @Test void actionsRoundTripWithExplicitIds() {
        for(int action=0;action<=ManagementProtocol.FORGET;action++) {
            var value=new ManagementProtocol.Action(3,UUID.randomUUID(),7,action,0);
            var b=new FriendlyByteBuf(Unpooled.buffer());
            try {value.encode(b);assertTrue(b.readableBytes()<=ManagementProtocol.MAX_PACKET);assertEquals(value,ManagementProtocol.Action.decode(b));}
            finally {b.release();}
        }
    }
    @Test void visitorSummaryCannotContainPrivateStock() {
        var id=UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,()->new ManagementProtocol.Summary(1,id,1,0,0,1,false,false,0,1,-1,-1));
        assertThrows(IllegalArgumentException.class,()->new ManagementProtocol.Summary(1,id,1,0,0,1,false,true,0,-1,-1,-1));
        var value=new ManagementProtocol.Summary(1,id,1,0,0,1,false,false,0,-1,-1,-1);
        var b=new FriendlyByteBuf(Unpooled.buffer());
        try {value.encode(b);assertEquals(value,ManagementProtocol.Summary.decode(b));}finally{b.release();}
    }
    @Test void summaryBoundsAndPaginationAreStrict() {
        var id=UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,()->new ManagementProtocol.Summary(1,id,1,0,0,1,true,true,0,28,9,4));
        assertThrows(IllegalArgumentException.class,()->new ManagementProtocol.Summary(1,id,1,0,1,1,true,true,0,27,9,4));
        assertThrows(IllegalArgumentException.class,()->new ManagementProtocol.Action(1,id,1,ManagementProtocol.FORGET+1,0));
        assertThrows(IllegalArgumentException.class,()->new ManagementProtocol.Action(1,id,1,0,Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,()->new ManagementProtocol.Action(101,id,1,0,0));
        assertThrows(IllegalArgumentException.class,()->new ManagementProtocol.Action(1,id,0,0,0));
    }
    @Test void extraAndOversizedPayloadsAreRejected() {
        var b=new FriendlyByteBuf(Unpooled.buffer());
        try {
            new ManagementProtocol.Action(1,UUID.randomUUID(),1,0,0).encode(b);b.writeByte(7);
            assertThrows(IllegalArgumentException.class,()->ManagementProtocol.Action.decode(b));
            b.clear();b.writeZero(ManagementProtocol.MAX_PACKET+1);
            assertThrows(IllegalArgumentException.class,()->ManagementProtocol.Action.decode(b));
            assertThrows(IllegalArgumentException.class,()->ManagementProtocol.Summary.decode(b));
        }finally{b.release();}
    }
    @Test void truncatedPayloadFailsWithoutReadingUnboundedData() {
        var b=new FriendlyByteBuf(Unpooled.buffer());
        try {b.writeByte(1);assertThrows(IndexOutOfBoundsException.class,()->ManagementProtocol.Action.decode(b));}finally{b.release();}
    }
    @Test void replayAndOutOfOrderActionsAreRejected() {
        var gate=new ManagementProtocol.Gate();
        assertTrue(gate.accept(2,0,100));assertFalse(gate.accept(2,0,110));assertFalse(gate.accept(1,0,120));
        assertTrue(gate.accept(3,1,120));
    }
    @Test void actionAndRefreshBudgetsAreIndependent() {
        var gate=new ManagementProtocol.Gate();
        assertTrue(gate.accept(1,ManagementProtocol.REFRESH,100));
        assertFalse(gate.accept(2,0,101));assertTrue(gate.accept(3,1,104));
        assertFalse(gate.accept(4,ManagementProtocol.REFRESH,108));
        assertTrue(gate.accept(5,ManagementProtocol.REFRESH,120));
        assertFalse(gate.accept(4,0,130));
    }
}
