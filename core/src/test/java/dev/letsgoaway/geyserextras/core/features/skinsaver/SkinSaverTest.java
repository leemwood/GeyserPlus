package dev.letsgoaway.geyserextras.core.features.skinsaver;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.letsgoaway.geyserextras.Server;
import dev.letsgoaway.geyserextras.core.ExtrasPlayer;
import dev.letsgoaway.geyserextras.core.GeyserExtras;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.auth.BedrockClientData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkinSaverTest {
    @TempDir
    Path output;

    @Test
    void savesDecodedSkinCapeAndGeometryFromClientData() throws Exception {
        String geometryName = "{\"geometry\":{\"default\":\"geometry.test\"}}";
        String geometryData = "{\"description\":\"测试皮肤\"}";
        String animationData = "{\"animations\":{}}";
        JsonObject payload = new JsonObject();
        payload.addProperty("SkinId", "test-skin");
        payload.addProperty("SkinImageWidth", 1);
        payload.addProperty("SkinImageHeight", 1);
        payload.addProperty("SkinData", Base64.getEncoder().encodeToString(new byte[]{(byte) 255, 0, (byte) 128, (byte) 255}));
        payload.addProperty("SkinResourcePatch", encode(geometryName));
        payload.addProperty("SkinGeometryData", encode(geometryData));
        payload.addProperty("SkinAnimationData", encode(animationData));
        payload.addProperty("CapeId", "test-cape");
        payload.addProperty("CapeImageWidth", 1);
        payload.addProperty("CapeImageHeight", 1);
        payload.addProperty("CapeData", Base64.getEncoder().encodeToString(new byte[]{0, (byte) 255, 0, (byte) 128}));
        BedrockClientData data = new Gson().fromJson(payload, BedrockClientData.class);

        GeyserSession session = mock(GeyserSession.class);
        when(session.getClientData()).thenReturn(data);
        ExtrasPlayer player = mock(ExtrasPlayer.class);
        when(player.getSession()).thenReturn(session);
        Server server = mock(Server.class);
        when(server.getPluginFolder()).thenReturn(output);
        Server previousServer = GeyserExtras.SERVER;
        GeyserExtras.SERVER = server;
        try {
            SkinSaver.save(player);

            Path skinFolder = output.resolve("skins/test-skin");
            assertEquals(0xffff0080, ImageIO.read(skinFolder.resolve("skin.png").toFile()).getRGB(0, 0));
            assertEquals(geometryName, Files.readString(skinFolder.resolve("skin.geometry_name.json")));
            assertEquals(geometryData, Files.readString(skinFolder.resolve("skin.geometry.json")));
            assertEquals(animationData, Files.readString(skinFolder.resolve("skin.animation.json")));
            assertEquals(0x8000ff00, ImageIO.read(output.resolve("capes/test-cape/cape.png").toFile()).getRGB(0, 0));
        } finally {
            GeyserExtras.SERVER = previousServer;
        }
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
