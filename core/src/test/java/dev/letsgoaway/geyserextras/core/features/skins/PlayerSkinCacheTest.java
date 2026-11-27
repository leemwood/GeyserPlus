package dev.letsgoaway.geyserextras.core.features.skins;

import org.geysermc.geyser.api.skin.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerSkinCacheTest {
    @TempDir Path directory;

    @Test
    void diskSnapshotSurvivesRestartWithoutMixingLinkedAccountOrChangedBedrockSkin() throws Exception {
        UUID uuid = UUID.randomUUID();
        SkinData skin = new SkinData(new Skin("java-texture", new byte[]{1, 2, 3, 4}),
                new Cape("cape", "cape-id", new byte[]{5, 6, 7, 8}, false), new SkinGeometry("name", "模型"));
        PlayerSkinCache first = new PlayerSkinCache(directory);
        first.load("123").join();
        first.saveJava("123", uuid, skin);
        first.saveBedrock("123", "hash-a", skin);
        first.saveBedrockTexture("123", "hash-a", new JavaSkinLoader.SignedTexture("signed-value", "signature"));
        first.flush().join();
        first.close();

        PlayerSkinCache restarted = new PlayerSkinCache(directory);
        try {
            restarted.load("123").join();
            SkinData loaded = restarted.javaSkin("123", uuid);
            assertArrayEquals(skin.skin().skinData(), loaded.skin().skinData());
            assertArrayEquals(skin.cape().capeData(), loaded.cape().capeData());
            assertEquals("模型", loaded.geometry().geometryData());
            assertNull(restarted.javaSkin("123", UUID.randomUUID()));
            assertEquals("signed-value", restarted.bedrockTexture("123", "hash-a").value());
            assertNull(restarted.bedrockTexture("123", "hash-b"));
            assertEquals("模型", restarted.bedrockSkin("123", "hash-a").geometry().geometryData());
            assertNull(restarted.bedrockSkin("123", "hash-b"));
            assertFalse(Files.readString(directory.resolve("123.json")).contains("client_data"));
        } finally {
            restarted.close();
        }
    }

    @Test
    void corruptOrExpiredSnapshotsFallBackWithoutBlockingLogin() throws Exception {
        Files.writeString(directory.resolve("123.json"), "not-json");
        Files.writeString(directory.resolve("456.json"), "{\"updated\":1,\"javaUuid\":\"old\"}");
        PlayerSkinCache cache = new PlayerSkinCache(directory);
        try {
            assertNotNull(cache.load("123").join());
            assertNotNull(cache.load("456").join());
            assertNull(cache.javaSkin("123", UUID.randomUUID()));
            assertNull(cache.javaSkin("456", UUID.randomUUID()));
            assertEquals(PlayerSkinCache.hash(new byte[]{1, 2}), PlayerSkinCache.hash(new byte[]{1, 2}));
            assertNotEquals(PlayerSkinCache.hash(new byte[]{1, 2}), PlayerSkinCache.hash(new byte[]{2, 1}));
        } finally {
            cache.close();
        }
    }
}
