package dev.letsgoaway.geyserextras.core.features.skins;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.cloudburstmc.protocol.bedrock.data.skin.*;
import org.geysermc.geyser.api.skin.Cape;
import org.geysermc.geyser.api.skin.Skin;
import org.geysermc.geyser.api.skin.SkinData;
import org.geysermc.geyser.api.skin.SkinGeometry;
import org.geysermc.geyser.session.auth.BedrockClientData;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Preserves the login skin independently of Geyser's Java-account fallback. */
public final class BedrockSkinLoader {
    private BedrockSkinLoader() {}

    public static SkinData read(BedrockClientData client) {
        byte[] pixels = client.getSkinData();
        int width = client.getSkinImageWidth();
        int height = client.getSkinImageHeight();
        int maximum = client.isPersonaSkin() ? 256 : 128;
        if (pixels == null || width <= 0 || height <= 0 || width > maximum || height > maximum
                || pixels.length != width * height * 4) {
            throw new IllegalArgumentException("Invalid Bedrock skin dimensions");
        }
        Skin skin = new Skin(client.getSkinId(), pixels.clone());
        Cape cape = new Cape("", "no-cape", new byte[0], false);
        byte[] capePixels = client.getCapeData();
        if (client.getCapeId() != null && !client.getCapeId().isBlank() && capePixels != null && capePixels.length > 0
                && client.getCapeImageWidth() > 0 && client.getCapeImageWidth() <= 128
                && client.getCapeImageHeight() > 0 && client.getCapeImageHeight() <= 128
                && capePixels.length == client.getCapeImageWidth() * client.getCapeImageHeight() * 4) {
            cape = new Cape(client.getCapeId(), client.getCapeId(), capePixels.clone(), false);
        }
        SkinGeometry geometry = "slim".equals(client.getArmSize()) ? SkinGeometry.SLIM : SkinGeometry.WIDE;
        byte[] name = client.getGeometryName();
        byte[] data = client.getGeometryData();
        if (name != null && data != null && name.length > 0 && name.length + data.length <= 512 * 1024) {
            geometry = new SkinGeometry(new String(name, StandardCharsets.UTF_8), new String(data, StandardCharsets.UTF_8));
        }
        return new SkinData(skin, cape, geometry);
    }

    /** Capture before Geyser clears the login JSON. Retain skin fields only. */
    public static SerializedSkin serialized(BedrockClientData client, String playFabId) {
        SkinData data = read(client);
        JsonObject extra = skinMetadata(client.getOriginalString());
        List<AnimationData> animations = new ArrayList<>();
        int animationBytes = 0;
        if (extra.has("AnimatedImageData")) {
            for (var entry : extra.getAsJsonArray("AnimatedImageData")) {
                JsonObject animation = entry.getAsJsonObject();
                byte[] image = Base64.getDecoder().decode(string(animation, "Image", ""));
                animationBytes += image.length;
                if (animations.size() >= 32 || animationBytes > 4 * 1024 * 1024) {
                    throw new IllegalArgumentException("Bedrock skin animations exceed size limit");
                }
                int width = animation.get("ImageWidth").getAsInt();
                int height = animation.get("ImageHeight").getAsInt();
                if (width <= 0 || height <= 0 || width > 2048 || height > 2048 || (long) width * height * 4 != image.length) {
                    throw new IllegalArgumentException("Invalid animated skin dimensions");
                }
                animations.add(new AnimationData(ImageData.of(width, height, image),
                        AnimatedTextureType.from(animation.get("Type").getAsInt()), animation.get("Frames").getAsFloat(),
                        AnimationExpressionType.from(animation.has("AnimationExpression") ? animation.get("AnimationExpression").getAsInt() : 0)));
            }
        }
        List<PersonaPieceData> pieces = new ArrayList<>();
        if (extra.has("PersonaPieces")) {
            for (var entry : extra.getAsJsonArray("PersonaPieces")) {
                if (pieces.size() >= 128) {
                    throw new IllegalArgumentException("Too many persona skin pieces");
                }
                JsonObject piece = entry.getAsJsonObject();
                pieces.add(new PersonaPieceData(string(piece, "PieceId", ""), string(piece, "PieceType", ""),
                        string(piece, "PackId", "00000000-0000-0000-0000-000000000000"),
                        piece.has("IsDefault") && piece.get("IsDefault").getAsBoolean(), string(piece, "ProductId", "")));
            }
        }
        List<PersonaPieceTintData> tints = new ArrayList<>();
        if (extra.has("PieceTintColors")) {
            for (var entry : extra.getAsJsonArray("PieceTintColors")) {
                if (tints.size() >= 128) {
                    throw new IllegalArgumentException("Too many persona tint entries");
                }
                JsonObject tint = entry.getAsJsonObject();
                List<String> colors = new ArrayList<>();
                for (var color : tint.getAsJsonArray("Colors")) {
                    if (colors.size() >= 16) {
                        throw new IllegalArgumentException("Too many persona tint colors");
                    }
                    colors.add(color.getAsString());
                }
                tints.add(new PersonaPieceTintData(string(tint, "PieceType", ""), colors));
            }
        }
        String geometry = data.geometry().geometryData();
        if (geometry.isBlank()) {
            geometry = new String(org.geysermc.geyser.util.FileUtils.readAllBytes("bedrock/geometries/geo.json"), StandardCharsets.UTF_8);
        }
        return SerializedSkin.builder().skinId(data.skin().textureUrl()).fullSkinId(data.skin().textureUrl())
                .playFabId(playFabId == null ? "" : playFabId).skinData(ImageData.of(client.getSkinImageWidth(), client.getSkinImageHeight(), data.skin().skinData()))
                .skinResourcePatch(data.geometry().geometryName()).geometryData(geometry)
                .geometryDataEngineVersion(decoded(extra, "SkinGeometryDataEngineVersion", client.getGameVersion()))
                .animationData(decoded(extra, "SkinAnimationData", "")).animations(animations)
                .capeId(data.cape().capeId()).capeData(ImageData.of(data.cape().capeData()))
                .premium(client.isPremiumSkin()).persona(client.isPersonaSkin()).capeOnClassic(client.isCapeOnClassicSkin())
                .armSize(client.getArmSize() == null ? "wide" : client.getArmSize())
                .skinColor(client.getSkinColor() == null ? "#0" : client.getSkinColor()).personaPieces(pieces).tintColors(tints)
                .primaryUser(extra.has("PrimaryUser") && extra.get("PrimaryUser").getAsBoolean())
                .profileHash(client.getProfileHash() == null ? "" : client.getProfileHash())
                .trusted(true).overridingPlayerAppearance(true).build();
    }

    private static String decoded(JsonObject source, String key, String fallback) {
        if (!source.has(key) || source.get(key).getAsString().isBlank()) {
            return fallback == null ? "" : fallback;
        }
        byte[] decoded = Base64.getDecoder().decode(source.get(key).getAsString());
        if (decoded.length > 512 * 1024) {
            throw new IllegalArgumentException("Skin metadata exceeds size limit");
        }
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private static JsonObject skinMetadata(String original) {
        if (original == null || original.isBlank()) {
            return new JsonObject();
        }
        if (original.length() > 8 * 1024 * 1024) {
            throw new IllegalArgumentException("Skin metadata exceeds size limit");
        }
        String json = original.stripLeading();
        if (!json.startsWith("{")) {
            // Geyser stores the validated client-data JWT here, not its decoded
            // JSON. Read its payload without retaining the signed login token.
            String[] parts = original.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid skin metadata format");
            }
            json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        }
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static String string(JsonObject source, String key, String fallback) {
        return source.has(key) ? source.get(key).getAsString() : fallback;
    }
}
