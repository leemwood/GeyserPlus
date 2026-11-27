package dev.letsgoaway.geyserextras.core.features.skins;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.geysermc.geyser.api.skin.Cape;
import org.geysermc.geyser.api.skin.Skin;
import org.geysermc.geyser.api.skin.SkinData;
import org.geysermc.geyser.api.skin.SkinGeometry;
import org.geysermc.geyser.skin.SkinProvider;
import org.geysermc.geyser.util.WebUtils;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public final class JavaSkinLoader {
    private final Cache<UUID, CompletableFuture<SkinData>> cache = CacheBuilder.newBuilder()
            .maximumSize(256).expireAfterWrite(15, TimeUnit.MINUTES).build();
    private final Cache<UUID, CompletableFuture<SignedTexture>> signedProfiles = CacheBuilder.newBuilder()
            .maximumSize(256).expireAfterWrite(15, TimeUnit.MINUTES).build();
    private final Function<UUID, CompletableFuture<String>> profiles;
    private final ImageLoader images;
    private final Executor executor;

    public JavaSkinLoader() {
        this(SkinProvider::requestTexturesFromUUID, SkinProvider::requestImage, SkinProvider.getExecutorService());
    }

    JavaSkinLoader(Function<UUID, CompletableFuture<String>> profiles, ImageLoader images, Executor executor) {
        this.profiles = profiles;
        this.images = images;
        this.executor = executor;
    }

    public CompletableFuture<SkinData> load(UUID uuid) {
        try {
            CompletableFuture<SkinData> future = cache.get(uuid,
                    () -> profiles.apply(uuid).thenApplyAsync(encoded -> decode(uuid, encoded), executor));
            future.whenComplete((skin, error) -> {
                if (error != null) {
                    cache.asMap().remove(uuid, future);
                }
            });
            return future;
        } catch (ExecutionException e) {
            return CompletableFuture.failedFuture(e.getCause());
        }
    }

    public SkinData cached(UUID uuid) {
        CompletableFuture<SkinData> future = cache.getIfPresent(uuid);
        return future == null || future.isCompletedExceptionally() ? null : future.getNow(null);
    }

    public CompletableFuture<SignedTexture> signedTexture(UUID uuid) {
        try {
            CompletableFuture<SignedTexture> future = signedProfiles.get(uuid, () -> CompletableFuture.supplyAsync(() -> {
                try {
                    JsonObject profile = WebUtils.getJson("https://sessionserver.mojang.com/session/minecraft/profile/"
                            + uuid.toString().replace("-", "") + "?unsigned=false");
                    for (var entry : profile.getAsJsonArray("properties")) {
                        JsonObject property = entry.getAsJsonObject();
                        if ("textures".equals(property.get("name").getAsString()) && property.has("signature")) {
                            SignedTexture texture = new SignedTexture(property.get("value").getAsString(), property.get("signature").getAsString());
                            if (!texture.belongsTo(uuid)) {
                                throw new IllegalArgumentException("Signed Java skin belongs to a different profile");
                            }
                            return texture;
                        }
                    }
                    throw new IllegalArgumentException("No signed Java textures returned");
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            }, executor));
            future.whenComplete((value, error) -> {
                if (error != null) {
                    signedProfiles.asMap().remove(uuid, future);
                }
            });
            return future;
        } catch (ExecutionException e) {
            return CompletableFuture.failedFuture(e.getCause());
        }
    }

    public record SignedTexture(String value, String signature) {
        public boolean belongsTo(UUID uuid) {
            try {
                JsonObject profile = JsonParser.parseString(new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)).getAsJsonObject();
                return uuid.toString().replace("-", "").equalsIgnoreCase(profile.get("profileId").getAsString());
            } catch (RuntimeException e) {
                return false;
            }
        }
    }

    private SkinData decode(UUID uuid, String encoded) {
        if (encoded == null) {
            throw new IllegalArgumentException("No official Java textures returned for " + uuid);
        }
        JsonObject profile = JsonParser.parseString(new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)).getAsJsonObject();
        if (!uuid.toString().replace("-", "").equalsIgnoreCase(profile.get("profileId").getAsString())) {
            throw new IllegalArgumentException("Java texture profile does not match linked UUID");
        }
        JsonObject textures = profile.getAsJsonObject("textures");
        JsonObject skinTexture = textures.getAsJsonObject("SKIN");
        String skinUrl = textureUrl(skinTexture);
        Skin skin = new Skin(skinUrl, imageData(skinUrl, false));
        Cape cape = new Cape("", "no-cape", new byte[0], false);
        if (textures.has("CAPE")) {
            String capeUrl = textureUrl(textures.getAsJsonObject("CAPE"));
            cape = new Cape(capeUrl, URI.create(capeUrl).getPath().substring("/texture/".length()), imageData(capeUrl, true), false);
        }
        JsonObject metadata = skinTexture.getAsJsonObject("metadata");
        boolean slim = metadata != null && metadata.has("model") && "slim".equals(metadata.get("model").getAsString());
        return new SkinData(skin, cape, slim ? SkinGeometry.SLIM : SkinGeometry.WIDE);
    }

    private byte[] imageData(String url, boolean cape) {
        try {
            BufferedImage image = images.load(url, cape);
            try {
                byte[] pixels = new byte[image.getWidth() * image.getHeight() * 4];
                int offset = 0;
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int argb = image.getRGB(x, y);
                        pixels[offset++] = (byte) (argb >> 16);
                        pixels[offset++] = (byte) (argb >> 8);
                        pixels[offset++] = (byte) argb;
                        pixels[offset++] = (byte) (argb >> 24);
                    }
                }
                return pixels;
            } finally {
                image.flush();
            }
        } catch (IOException e) {
            throw new CompletionException(e);
        }
    }

    private static String textureUrl(JsonObject texture) {
        URI uri = URI.create(texture.get("url").getAsString());
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || !"textures.minecraft.net".equals(uri.getHost()) || uri.getRawUserInfo() != null
                || uri.getPort() != -1 || uri.getQuery() != null || uri.getFragment() != null
                || !uri.getPath().matches("/texture/[0-9a-fA-F]+")) {
            throw new IllegalArgumentException("Invalid official Java texture URL");
        }
        return "https://textures.minecraft.net" + uri.getPath();
    }

    @FunctionalInterface
    interface ImageLoader {
        BufferedImage load(String url, boolean cape) throws IOException;
    }
}
