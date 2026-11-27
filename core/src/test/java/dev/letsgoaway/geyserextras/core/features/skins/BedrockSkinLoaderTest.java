package dev.letsgoaway.geyserextras.core.features.skins;

import org.geysermc.geyser.api.skin.SkinData;
import org.geysermc.geyser.api.skin.SkinGeometry;
import org.geysermc.geyser.session.auth.BedrockClientData;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BedrockSkinLoaderTest {
    @Test
    void preserves128SkinWithoutDecodingAgainAndCopiesSourcePixels() {
        BedrockClientData client = client(128, 128);
        byte[] pixels = client.getSkinData();
        pixels[0] = 27;
        when(client.getArmSize()).thenReturn("slim");
        SkinData skin = BedrockSkinLoader.read(client);
        pixels[0] = 0;
        assertEquals(27, skin.skin().skinData()[0]);
        assertEquals(SkinGeometry.SLIM, skin.geometry());
        assertEquals(0, skin.cape().capeData().length);
    }

    @Test
    void rejectsDimensionsThatDoNotMatchTheSubmittedRgbaData() {
        BedrockClientData client = client(64, 64);
        when(client.getSkinImageWidth()).thenReturn(128);
        assertThrows(IllegalArgumentException.class, () -> BedrockSkinLoader.read(client));
        when(client.getSkinImageWidth()).thenReturn(Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> BedrockSkinLoader.read(client));
    }

    @Test
    void serializedAppearanceRetainsAnimationsPersonaPiecesTintsAndUnicodeGeometry() {
        BedrockClientData client = client(64, 64);
        when(client.getGeometryName()).thenReturn("{\"geometry\":{\"default\":\"custom\"}}".getBytes(StandardCharsets.UTF_8));
        when(client.getGeometryData()).thenReturn("{\"模型\":true}".getBytes(StandardCharsets.UTF_8));
        when(client.isPersonaSkin()).thenReturn(true);
        when(client.getGameVersion()).thenReturn("26.65");
        when(client.getArmSize()).thenReturn("wide");
        String image = Base64.getEncoder().encodeToString(new byte[16 * 16 * 4]);
        String animation = Base64.getEncoder().encodeToString("{\"动画\":true}".getBytes(StandardCharsets.UTF_8));
        when(client.getOriginalString()).thenReturn("""
                {"AnimatedImageData":[{"Image":"%s","ImageWidth":16,"ImageHeight":16,"Type":1,"Frames":1,"AnimationExpression":0}],
                "PersonaPieces":[{"PieceId":"body","PieceType":"persona_body","PackId":"00000000-0000-0000-0000-000000000000","IsDefault":true,"ProductId":""}],
                "PieceTintColors":[{"PieceType":"persona_body","Colors":["#ff112233"]}],"SkinAnimationData":"%s"}
                """.formatted(image, animation));
        var skin = BedrockSkinLoader.serialized(client, "playfab");
        assertTrue(skin.isPersona());
        assertEquals(1, skin.getAnimations().size());
        assertEquals(1, skin.getPersonaPieces().size());
        assertEquals(1, skin.getTintColors().size());
        assertTrue(skin.getGeometryData().contains("模型"));
        assertTrue(skin.getAnimationData().contains("动画"));
        String metadata = client.getOriginalString();
        when(client.getOriginalString()).thenReturn("header." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(metadata.getBytes(StandardCharsets.UTF_8)) + ".signature");
        var jwtSkin = BedrockSkinLoader.serialized(client, "playfab");
        assertEquals(skin.getAnimationData(), jwtSkin.getAnimationData());
        assertEquals(skin.getAnimations(), jwtSkin.getAnimations());
        assertEquals(skin.getPersonaPieces(), jwtSkin.getPersonaPieces());
    }

    private BedrockClientData client(int width, int height) {
        BedrockClientData client = mock(BedrockClientData.class);
        when(client.getSkinId()).thenReturn("skin");
        when(client.getSkinImageWidth()).thenReturn(width);
        when(client.getSkinImageHeight()).thenReturn(height);
        when(client.getSkinData()).thenReturn(new byte[width * height * 4]);
        return client;
    }
}
