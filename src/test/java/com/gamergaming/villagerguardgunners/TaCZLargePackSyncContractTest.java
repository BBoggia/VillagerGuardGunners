package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class TaCZLargePackSyncContractTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void compatibilityMixinCancelsOnlyTheOversizedNativePath() throws IOException {
        String mixin = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "mixin/TaCZCommonAssetsManagerMixin.java");
        String sender = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "compat/tacz/network/TaCZGunPackSync.java");

        assertAll(
                () -> assertTrue(mixin.contains("method = \"OnDatapackSync\"")),
                () -> assertTrue(mixin.contains("cancellable = true")),
                () -> assertTrue(mixin.contains("replaceIfOversized(event)")),
                () -> assertTrue(mixin.indexOf("replaceIfOversized(event)")
                        < mixin.indexOf("callback.cancel()")),
                () -> assertTrue(sender.contains(
                        "encoded.length <= TaCZGunPackSyncProtocol.NATIVE_SAFE_PAYLOAD_BYTES")),
                () -> assertTrue(sender.contains("return false;")),
                () -> assertFalse(sender.contains("ClientboundCustomPayloadPacket")),
                () -> assertFalse(sender.contains("MAX_PAYLOAD_SIZE")));
    }

    @Test
    void chunkingIsBoundedChecksumVerifiedAndAtomic() throws IOException {
        String protocol = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "compat/tacz/network/TaCZGunPackSyncProtocol.java");
        String assembly = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "compat/tacz/network/TaCZGunPackSyncAssembly.java");
        String client = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "compat/tacz/network/client/TaCZGunPackSyncClient.java");

        assertAll(
                () -> assertTrue(protocol.contains("NATIVE_SAFE_PAYLOAD_BYTES = 900 * 1024")),
                () -> assertTrue(protocol.contains("MAXIMUM_CHUNK_BYTES = 512 * 1024")),
                () -> assertTrue(protocol.contains("MAXIMUM_CHUNKS = 64")),
                () -> assertTrue(protocol.contains("CRC32")),
                () -> assertTrue(assembly.contains("receivedChunks != chunkCount")),
                () -> assertTrue(assembly.contains("checksum(assembled) == checksum")),
                () -> assertTrue(client.indexOf("decodeCache(encoded)")
                        < client.indexOf("CommonNetworkCache.INSTANCE.fromNetwork(cache)")),
                () -> assertTrue(client.indexOf("CommonNetworkCache.INSTANCE.fromNetwork(cache)")
                        < client.indexOf("ClientIndexManager.reload()")),
                () -> assertFalse(protocol.contains("setChunkForced")),
                () -> assertFalse(protocol.contains("TickEvent")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
