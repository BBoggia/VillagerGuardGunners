package com.gamergaming.villagerguardgunners.compat.tacz.network;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/** Client-side bounded accumulator; publication happens only after full verification. */
public final class TaCZGunPackSyncAssembly {
    private UUID transferId;
    private int chunkCount;
    private int totalBytes;
    private long checksum;
    private byte[][] chunks;
    private int receivedChunks;

    public synchronized Optional<byte[]> accept(TaCZGunPackSyncPacket packet) {
        if (packet == null) {
            clear();
            return Optional.empty();
        }
        if (!packet.transferId().equals(transferId)) {
            if (packet.chunkIndex() != 0) {
                return Optional.empty();
            }
            begin(packet);
        }
        if (!matches(packet)) {
            clear();
            return Optional.empty();
        }

        byte[] data = packet.data();
        byte[] existing = chunks[packet.chunkIndex()];
        if (existing != null) {
            if (!Arrays.equals(existing, data)) {
                clear();
            }
            return Optional.empty();
        }
        chunks[packet.chunkIndex()] = data;
        receivedChunks++;
        if (receivedChunks != chunkCount) {
            return Optional.empty();
        }

        byte[] assembled = new byte[totalBytes];
        int offset = 0;
        for (byte[] chunk : chunks) {
            if (chunk == null || offset + chunk.length > assembled.length) {
                clear();
                return Optional.empty();
            }
            System.arraycopy(chunk, 0, assembled, offset, chunk.length);
            offset += chunk.length;
        }
        boolean valid = offset == assembled.length
                && TaCZGunPackSyncProtocol.checksum(assembled) == checksum;
        clear();
        return valid ? Optional.of(assembled) : Optional.empty();
    }

    public synchronized void clear() {
        transferId = null;
        chunkCount = 0;
        totalBytes = 0;
        checksum = 0L;
        chunks = null;
        receivedChunks = 0;
    }

    public synchronized int retainedBytes() {
        if (chunks == null) {
            return 0;
        }
        int retained = 0;
        for (byte[] chunk : chunks) {
            if (chunk != null) {
                retained += chunk.length;
            }
        }
        return retained;
    }

    private void begin(TaCZGunPackSyncPacket packet) {
        transferId = packet.transferId();
        chunkCount = packet.chunkCount();
        totalBytes = packet.totalBytes();
        checksum = packet.checksum();
        chunks = new byte[chunkCount][];
        receivedChunks = 0;
    }

    private boolean matches(TaCZGunPackSyncPacket packet) {
        return packet.transferId().equals(transferId)
                && packet.chunkCount() == chunkCount
                && packet.totalBytes() == totalBytes
                && packet.checksum() == checksum;
    }
}
