package dev.letsgoaway.geyserextras.core.features.skins;

import com.google.gson.JsonObject;
import org.geysermc.geyser.api.skin.SkinData;
import org.geysermc.geyser.api.skin.SkinGeometry;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class JavaSkinLoaderTest {
    private static final UUID UUID = java.util.UUID.fromString("12345678-1234-4234-9234-123456789abc");

    @Test
    void loadsOfficialSlimSkinAndCapeAsRgba() {
        JavaSkinLoader loader = loader(profile(true), (url, cape) -> {
            assertTrue(url.startsWith("https://textures.minecraft.net/texture/"));
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, cape ? 0xff12ab34 : 0xff5678cd);
            return image;
        });
        SkinData data = loader.load(UUID).join();
        assertArrayEquals(new byte[]{0x56, 0x78, (byte) 0xcd, (byte) 0xff}, data.skin().skinData());
        assertArrayEquals(new byte[]{0x12, (byte) 0xab, 0x34, (byte) 0xff}, data.cape().capeData());
        assertEquals("def456", data.cape().capeId());
        assertEquals(SkinGeometry.SLIM, data.geometry());
    }

    @Test
    void missingOfficialCapeProducesEmptyCape() {
        SkinData skin = loader(profile(false), (url, cape) -> new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)).load(UUID).join();
        assertEquals(0, skin.cape().capeData().length);
        assertFalse(skin.cape().failed());
    }

    @Test
    void concurrentLoadsShareRequestAndCacheResult() {
        AtomicInteger requests = new AtomicInteger();
        CompletableFuture<String> profile = new CompletableFuture<>();
        JavaSkinLoader loader = new JavaSkinLoader(uuid -> {
            requests.incrementAndGet();
            return profile;
        }, (url, cape) -> new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), Runnable::run);
        CompletableFuture<SkinData> first = loader.load(UUID);
        assertSame(first, loader.load(UUID));
        profile.complete(profile(false));
        assertSame(first.join(), loader.cached(UUID));
        assertEquals(1, requests.get());
    }

    @Test
    void failedDownloadCanBeRetriedWithoutCachingFailure() {
        AtomicInteger attempts = new AtomicInteger();
        JavaSkinLoader loader = loader(profile(false), (url, cape) -> {
            if (attempts.getAndIncrement() == 0) {
                throw new IOException("test download failure");
            }
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        });
        assertThrows(CompletionException.class, () -> loader.load(UUID).join());
        assertNull(loader.cached(UUID));
        assertNotNull(loader.load(UUID).join());
        assertEquals(2, attempts.get());
    }

    @Test
    void rejectsOtherProfilesAndNonOfficialTextureUrlsBeforeDownloading() {
        JavaSkinLoader.ImageLoader forbiddenDownload = (url, cape) -> {
            fail("Unexpected texture download: " + url);
            return null;
        };
        String wrongAccount = new String(Base64.getDecoder().decode(profile(false)), StandardCharsets.UTF_8)
                .replace(UUID.toString().replace("-", ""), java.util.UUID.randomUUID().toString().replace("-", ""));
        assertThrows(CompletionException.class, () -> loader(encode(wrongAccount), forbiddenDownload).load(UUID).join());
        String wrongUrl = new String(Base64.getDecoder().decode(profile(false)), StandardCharsets.UTF_8)
                .replace("textures.minecraft.net", "localhost");
        assertThrows(CompletionException.class, () -> loader(encode(wrongUrl), forbiddenDownload).load(UUID).join());
    }

    private JavaSkinLoader loader(String profile, JavaSkinLoader.ImageLoader images) {
        return new JavaSkinLoader(uuid -> CompletableFuture.completedFuture(profile), images, Runnable::run);
    }

    private String profile(boolean cape) {
        JsonObject root = new JsonObject();
        root.addProperty("profileId", UUID.toString().replace("-", ""));
        JsonObject textures = new JsonObject();
        JsonObject skin = new JsonObject();
        skin.addProperty("url", "http://textures.minecraft.net/texture/abc123");
        JsonObject metadata = new JsonObject();
        metadata.addProperty("model", "slim");
        skin.add("metadata", metadata);
        textures.add("SKIN", skin);
        if (cape) {
            JsonObject capeTexture = new JsonObject();
            capeTexture.addProperty("url", "http://textures.minecraft.net/texture/def456");
            textures.add("CAPE", capeTexture);
        }
        root.add("textures", textures);
        return encode(root.toString());
    }

    private String encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
