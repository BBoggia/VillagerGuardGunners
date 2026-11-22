package com.gamergaming.villagerguardgunners.compat.tacz.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tacz.guns.resource.network.DataType;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class TaCZGunPackSyncProtocolTest {
    @Test
    void nativeCacheFormatRoundTripsWithoutTranslation() {
        Map<DataType, Map<ResourceLocation, String>> cache = new LinkedHashMap<>();
        cache.put(DataType.GUN_DATA, Map.of(
                id("test", "rifle"), "{\"ammo\":\"test:rifle_round\"}"));
        cache.put(DataType.AMMO_INDEX, Map.of(
                id("test", "rifle_round"), "{\"name\":\"Rifle Round\"}"));

        byte[] encoded = TaCZGunPackSyncProtocol.encodeCache(cache);

        assertEquals(cache, TaCZGunPackSyncProtocol.decodeCache(encoded));
    }

    @Test
    void oversizedNativePayloadBecomesIndividuallySafePackets() {
        byte[] encoded = new byte[TaCZGunPackSyncProtocol.NATIVE_SAFE_PAYLOAD_BYTES + 31];
        for (int index = 0; index < encoded.length; index++) {
            encoded[index] = (byte) (index * 31);
        }
        List<TaCZGunPackSyncPacket> chunks = TaCZGunPackSyncProtocol.split(
                UUID.fromString("4d301574-fbe1-4caf-92bc-34844fd946fe"),
                encoded);

        assertTrue(chunks.size() > 1);
        for (TaCZGunPackSyncPacket chunk : chunks) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                TaCZGunPackSyncPacket.encode(chunk, buffer);
                assertTrue(buffer.readableBytes()
                        < TaCZGunPackSyncProtocol.MINECRAFT_CUSTOM_PAYLOAD_LIMIT);
                TaCZGunPackSyncPacket decoded = TaCZGunPackSyncPacket.decode(buffer);
                assertEquals(chunk.transferId(), decoded.transferId());
                assertEquals(chunk.chunkIndex(), decoded.chunkIndex());
                assertArrayEquals(chunk.data(), decoded.data());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void assemblyIsOrderIndependentAfterChunkZeroAndPublishesOnlyWhenComplete() {
        byte[] encoded = new byte[TaCZGunPackSyncProtocol.MAXIMUM_CHUNK_BYTES * 2 + 17];
        Arrays.fill(encoded, (byte) 0x5a);
        List<TaCZGunPackSyncPacket> chunks = TaCZGunPackSyncProtocol.split(
                UUID.fromString("aa1221d3-2ad2-4d77-91be-145c7e255770"),
                encoded);
        TaCZGunPackSyncAssembly assembly = new TaCZGunPackSyncAssembly();

        assertTrue(assembly.accept(chunks.get(1)).isEmpty());
        assertEquals(0, assembly.retainedBytes());
        assertTrue(assembly.accept(chunks.get(0)).isEmpty());
        assertTrue(assembly.accept(chunks.get(2)).isEmpty());
        Optional<byte[]> complete = assembly.accept(chunks.get(1));

        assertTrue(complete.isPresent());
        assertArrayEquals(encoded, complete.orElseThrow());
        assertEquals(0, assembly.retainedBytes());
    }

    @Test
    void duplicateConflictAndChecksumFailureDiscardTheTransfer() {
        byte[] encoded = new byte[TaCZGunPackSyncProtocol.MAXIMUM_CHUNK_BYTES + 5];
        Arrays.fill(encoded, (byte) 7);
        List<TaCZGunPackSyncPacket> chunks = TaCZGunPackSyncProtocol.split(UUID.randomUUID(), encoded);
        TaCZGunPackSyncAssembly assembly = new TaCZGunPackSyncAssembly();
        assembly.accept(chunks.get(0));

        byte[] changed = chunks.get(0).data();
        changed[0] ^= 1;
        TaCZGunPackSyncPacket conflicting = new TaCZGunPackSyncPacket(
                chunks.get(0).schemaVersion(),
                chunks.get(0).transferId(),
                0,
                chunks.get(0).chunkCount(),
                chunks.get(0).totalBytes(),
                chunks.get(0).checksum(),
                changed);
        assertTrue(assembly.accept(conflicting).isEmpty());
        assertEquals(0, assembly.retainedBytes());

        assembly.accept(chunks.get(0));
        TaCZGunPackSyncPacket wrongChecksum = new TaCZGunPackSyncPacket(
                chunks.get(1).schemaVersion(),
                chunks.get(1).transferId(),
                1,
                chunks.get(1).chunkCount(),
                chunks.get(1).totalBytes(),
                chunks.get(1).checksum() + 1,
                chunks.get(1).data());
        assertTrue(assembly.accept(wrongChecksum).isEmpty());
        assertEquals(0, assembly.retainedBytes());
    }

    @Test
    void packetAndTransferBoundsRejectMalformedInput() {
        UUID transfer = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> TaCZGunPackSyncProtocol.split(
                        transfer,
                        new byte[TaCZGunPackSyncProtocol.MAXIMUM_TRANSFER_BYTES + 1]));
        assertThrows(IllegalArgumentException.class,
                () -> new TaCZGunPackSyncPacket(
                        2,
                        transfer,
                        0,
                        1,
                        0,
                        0L,
                        new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> new TaCZGunPackSyncPacket(
                        1,
                        transfer,
                        1,
                        1,
                        0,
                        0L,
                        new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> new TaCZGunPackSyncPacket(
                        1,
                        transfer,
                        0,
                        1,
                        0,
                        0L,
                        null));
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
