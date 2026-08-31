package com.gamergaming.villagerguardgunners.compat.tacz.network;

import com.tacz.guns.network.message.ServerMessageSyncGunPack;
import com.tacz.guns.resource.network.DataType;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.CRC32;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Pure bounded serialization, fragmentation, and reassembly for TaCZ's native cache format. */
public final class TaCZGunPackSyncProtocol {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MINECRAFT_CUSTOM_PAYLOAD_LIMIT = 1_048_576;
    public static final int NATIVE_SAFE_PAYLOAD_BYTES = 900 * 1024;
    public static final int MAXIMUM_CHUNK_BYTES = 512 * 1024;
    public static final int MAXIMUM_CHUNKS = 64;
    public static final int MAXIMUM_TRANSFER_BYTES = MAXIMUM_CHUNK_BYTES * MAXIMUM_CHUNKS;

    private TaCZGunPackSyncProtocol() {
    }

    public static byte[] encodeCache(
            Map<DataType, Map<ResourceLocation, String>> cache) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ServerMessageSyncGunPack.encode(new ServerMessageSyncGunPack(cache), buffer);
            int length = buffer.readableBytes();
            if (length > MAXIMUM_TRANSFER_BYTES) {
                throw new IllegalArgumentException("TaCZ common-data cache exceeds the chunked-sync bound");
            }
            byte[] encoded = new byte[length];
            buffer.getBytes(buffer.readerIndex(), encoded);
            return encoded;
        } finally {
            buffer.release();
        }
    }

    public static Map<DataType, Map<ResourceLocation, String>> decodeCache(byte[] encoded) {
        if (encoded == null || encoded.length > MAXIMUM_TRANSFER_BYTES) {
            throw new IllegalArgumentException("TaCZ common-data payload exceeds the decode bound");
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(encoded));
        try {
            ServerMessageSyncGunPack message = ServerMessageSyncGunPack.decode(buffer);
            if (buffer.isReadable()) {
                throw new IllegalArgumentException("TaCZ common-data payload contains trailing bytes");
            }
            return message.getCache();
        } finally {
            buffer.release();
        }
    }

    public static List<TaCZGunPackSyncPacket> split(UUID transferId, byte[] encoded) {
        if (transferId == null || encoded == null || encoded.length > MAXIMUM_TRANSFER_BYTES) {
            throw new IllegalArgumentException("Invalid TaCZ common-data transfer");
        }
        int chunkCount = Math.max(1, (encoded.length + MAXIMUM_CHUNK_BYTES - 1)
                / MAXIMUM_CHUNK_BYTES);
        long checksum = checksum(encoded);
        List<TaCZGunPackSyncPacket> chunks = new ArrayList<>(chunkCount);
        for (int index = 0; index < chunkCount; index++) {
            int start = index * MAXIMUM_CHUNK_BYTES;
            int end = Math.min(start + MAXIMUM_CHUNK_BYTES, encoded.length);
            chunks.add(new TaCZGunPackSyncPacket(
                    CURRENT_SCHEMA_VERSION,
                    transferId,
                    index,
                    chunkCount,
                    encoded.length,
                    checksum,
                    Arrays.copyOfRange(encoded, start, end)));
        }
        return List.copyOf(chunks);
    }

    static int expectedChunkBytes(int chunkIndex, int chunkCount, int totalBytes) {
        int expectedChunks = Math.max(1, (totalBytes + MAXIMUM_CHUNK_BYTES - 1)
                / MAXIMUM_CHUNK_BYTES);
        if (chunkCount != expectedChunks) {
            return -1;
        }
        int start = chunkIndex * MAXIMUM_CHUNK_BYTES;
        return Math.max(0, Math.min(MAXIMUM_CHUNK_BYTES, totalBytes - start));
    }

    static long checksum(byte[] encoded) {
        CRC32 checksum = new CRC32();
        checksum.update(encoded);
        return checksum.getValue();
    }
}
