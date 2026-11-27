package dev.letsgoaway.geyserextras.core.features.skins;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import org.geysermc.geyser.api.skin.Cape;
import org.geysermc.geyser.skin.SkinProvider;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Resolves a known equipped cape ID when Bedrock omits the actual pixels. */
public final class BedrockCapeResolver {
    private static final Map<UUID, Definition> MAPPINGS = mappings();
    private final Cache<UUID, CompletableFuture<Cape>> cache = CacheBuilder.newBuilder()
            .maximumSize(64).expireAfterWrite(1, TimeUnit.DAYS).build();
    private final Path directory;
    private final JavaSkinLoader.ImageLoader images;
    private final Executor executor;

    public BedrockCapeResolver(Path directory) {
        this(directory, SkinProvider::requestImage, SkinProvider.getExecutorService());
    }

    BedrockCapeResolver(Path directory, JavaSkinLoader.ImageLoader images, Executor executor) {
        this.directory = directory;
        this.images = images;
        this.executor = executor;
    }

    public CompletableFuture<Cape> resolve(String bedrockId) {
        Definition definition = definition(bedrockId);
        if (definition == null) return CompletableFuture.completedFuture(null);
        try {
            UUID id = UUID.fromString(bedrockId);
            CompletableFuture<Cape> future = cache.get(id,
                    () -> CompletableFuture.supplyAsync(() -> load(definition), executor));
            future.whenComplete((cape, error) -> {
                if (error != null) cache.asMap().remove(id, future);
            });
            return future;
        } catch (ExecutionException e) {
            return CompletableFuture.failedFuture(e.getCause());
        }
    }

    public static Definition definition(String id) {
        if (id == null || !id.matches("(?i)[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")) return null;
        return MAPPINGS.get(UUID.fromString(id));
    }

    private Cape load(Definition definition) {
        String url = "https://textures.minecraft.net/texture/" + definition.texture();
        Path file = directory.resolve(definition.texture() + ".png");
        BufferedImage image = null;
        try {
            if (Files.isRegularFile(file) && Files.size(file) <= 256 * 1024) {
                try {
                    image = ImageIO.read(file.toFile());
                } catch (IOException ignored) {
                    // Corrupt cache entries should be downloaded again.
                }
                if (!valid(image)) {
                    if (image != null) image.flush();
                    image = null;
                }
            }
            if (image == null) {
                image = images.load(url, true);
                if (!valid(image)) throw new IOException("Invalid mapped cape texture dimensions");
                try {
                    Files.createDirectories(directory);
                    Path temporary = Files.createTempFile(directory, definition.name() + "-", ".tmp");
                    try {
                        ImageIO.write(image, "png", temporary.toFile());
                        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                    } finally {
                        Files.deleteIfExists(temporary);
                    }
                } catch (IOException ignored) {
                    // Successful downloads remain usable when disk caching fails.
                }
            }
            // Java's official cape layout is 64x32. Geyser normally pads legacy
            // 22x17 capes; normalize here as well for cache and injected loaders.
            byte[] pixels = new byte[64 * 32 * 4];
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getRGB(x, y);
                    int offset = (y * 64 + x) * 4;
                    pixels[offset] = (byte) (argb >> 16);
                    pixels[offset + 1] = (byte) (argb >> 8);
                    pixels[offset + 2] = (byte) argb;
                    pixels[offset + 3] = (byte) (argb >> 24);
                }
            }
            return new Cape(url, "bedrock-" + definition.bedrockId(), pixels, false);
        } catch (IOException e) {
            throw new CompletionException(e);
        } finally {
            if (image != null) image.flush();
        }
    }

    private static boolean valid(BufferedImage image) {
        return image != null && ((image.getWidth() == 64 && image.getHeight() == 32)
                || (image.getWidth() == 22 && image.getHeight() == 17));
    }

    private static Map<UUID, Definition> mappings() {
        try (var source = BedrockCapeResolver.class.getResourceAsStream("/capes/bedrock-mappings.json")) {
            if (source == null) throw new IllegalStateException("Missing Bedrock cape mappings");
            Definition[] definitions = new Gson().fromJson(new InputStreamReader(source, StandardCharsets.UTF_8), Definition[].class);
            Map<UUID, Definition> result = new HashMap<>();
            for (Definition definition : definitions) {
                if (!definition.texture().matches("[0-9a-f]{40,64}") || !definition.name().matches("[a-z0-9]+")) {
                    throw new IllegalStateException("Invalid bundled cape mapping");
                }
                if (result.put(UUID.fromString(definition.bedrockId()), definition) != null) {
                    throw new IllegalStateException("Duplicate Bedrock cape mapping");
                }
            }
            return Map.copyOf(result);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public record Definition(String bedrockId, String name, String texture) {}
}
