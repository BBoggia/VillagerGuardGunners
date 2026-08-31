package com.gamergaming.villagerguardgunners.compat.tacz.network;

import java.util.Arrays;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/** One bounded fragment of an oversized TaCZ common-data synchronization. */
public record TaCZGunPackSyncPacket(
        int schemaVersion,
        UUID transferId,
        int chunkIndex,
        int chunkCount,
        int totalBytes,
        long checksum,
        byte[] data) {
    public TaCZGunPackSyncPacket {
        if (schemaVersion != TaCZGunPackSyncProtocol.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported TaCZ sync schema");
        }
        if (transferId == null) {
            throw new IllegalArgumentException("TaCZ sync transfer ID is required");
        }
        if (chunkCount <= 0 || chunkCount > TaCZGunPackSyncProtocol.MAXIMUM_CHUNKS) {
            throw new IllegalArgumentException("TaCZ sync chunk count is outside its hard bound");
        }
        if (chunkIndex < 0 || chunkIndex >= chunkCount) {
            throw new IllegalArgumentException("TaCZ sync chunk index is outside its transfer");
        }
        if (totalBytes < 0 || totalBytes > TaCZGunPackSyncProtocol.MAXIMUM_TRANSFER_BYTES) {
            throw new IllegalArgumentException("TaCZ sync transfer size is outside its hard bound");
        }
        if (data == null) {
            throw new IllegalArgumentException("TaCZ sync chunk data is required");
        }
        data = Arrays.copyOf(data, data.length);
        if (data.length != TaCZGunPackSyncProtocol.expectedChunkBytes(
                chunkIndex,
                chunkCount,
                totalBytes)) {
            throw new IllegalArgumentException("TaCZ sync chunk length is inconsistent");
        }
    }

    @Override
    public byte[] data() {
        return Arrays.copyOf(data, data.length);
    }

    public static void encode(TaCZGunPackSyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.schemaVersion());
        buffer.writeUUID(packet.transferId());
        buffer.writeVarInt(packet.chunkIndex());
        buffer.writeVarInt(packet.chunkCount());
        buffer.writeVarInt(packet.totalBytes());
        buffer.writeLong(packet.checksum());
        buffer.writeByteArray(packet.data);
    }

    public static TaCZGunPackSyncPacket decode(FriendlyByteBuf buffer) {
        int schemaVersion = buffer.readVarInt();
        UUID transferId = buffer.readUUID();
        int chunkIndex = buffer.readVarInt();
        int chunkCount = buffer.readVarInt();
        int totalBytes = buffer.readVarInt();
        long checksum = buffer.readLong();
        byte[] data = buffer.readByteArray(TaCZGunPackSyncProtocol.MAXIMUM_CHUNK_BYTES);
        return new TaCZGunPackSyncPacket(
                schemaVersion,
                transferId,
                chunkIndex,
                chunkCount,
                totalBytes,
                checksum,
                data);
    }
}
