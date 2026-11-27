package dev.letsgoaway.geyserextras.core.features.skins;

import org.geysermc.geyser.api.skin.Cape;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BedrockCapeResolverTest {
    static final String AURORA_ID = "b714eea5-bc07-f508-3b67-91669e55696a";
    static final String AURORA_HASH = "7c418dfbd37412a55e9f1425c9807a591ad10cf71b1edb576a587be9114c277f";
    @TempDir Path directory;

    @Test
    void mapsEquippedAuroraIdToItsExactOfficialTextureAndCachesAcrossRestart() {
        AtomicInteger downloads = new AtomicInteger();
        var resolver = new BedrockCapeResolver(directory, (url, cape) -> {
            downloads.incrementAndGet();
            assertTrue(cape);
            assertEquals("https://textures.minecraft.net/texture/" + AURORA_HASH, url);
            return image(64, 32);
        }, Runnable::run);
        Cape cape = resolver.resolve(AURORA_ID).join();
        assertEquals("bedrock-" + AURORA_ID, cape.capeId());
        assertEquals(64 * 32 * 4, cape.capeData().length);
        assertArrayEquals(new byte[]{0x12, 0x34, 0x56, (byte) 0xff}, java.util.Arrays.copyOf(cape.capeData(), 4));
        assertSame(cape, resolver.resolve(AURORA_ID.toUpperCase(java.util.Locale.ROOT)).join());
        assertEquals(1, downloads.get());
        var restarted = new BedrockCapeResolver(directory, (url, isCape) -> {
            fail("Disk cache should avoid another network request");
            return null;
        }, Runnable::run);
        assertArrayEquals(cape.capeData(), restarted.resolve(AURORA_ID).join().capeData());
    }

    @Test
    void unknownEmptyAndMalformedIdsDoNotInventCapeOrFetchArbitraryUrls() {
        var resolver = new BedrockCapeResolver(directory, (url, cape) -> {
            fail("Unknown id must not trigger a request");
            return null;
        }, Runnable::run);
        for (String id : new String[]{"", "no-cape", UUID.randomUUID().toString(), "https://localhost/cape", "../cape"}) {
            assertNull(resolver.resolve(id).join());
        }
        assertNull(resolver.resolve(null).join());
        assertEquals("cherry", BedrockCapeResolver.definition("0302ebba-bfeb-8748-3e66-03acd664c78f").name());
    }

    @Test
    void failedDownloadsCanRetryAndLegacyCapesArePaddedTo64By32() {
        AtomicInteger attempts = new AtomicInteger();
        var resolver = new BedrockCapeResolver(directory, (url, cape) -> {
            if (attempts.getAndIncrement() == 0) throw new IOException("temporary outage");
            return image(22, 17);
        }, Runnable::run);
        assertThrows(CompletionException.class, () -> resolver.resolve(AURORA_ID).join());
        Cape cape = resolver.resolve(AURORA_ID).join();
        assertEquals(2, attempts.get());
        assertEquals(8192, cape.capeData().length);
        assertEquals(0, cape.capeData()[(31 * 64 + 63) * 4 + 3]);
    }

    @Test
    void rejectsInvalidTextureDimensions() {
        var resolver = new BedrockCapeResolver(directory, (url, cape) -> image(128, 128), Runnable::run);
        assertThrows(CompletionException.class, () -> resolver.resolve(AURORA_ID).join());
    }

    private BufferedImage image(int width, int height) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xff123456);
        return image;
    }
}
