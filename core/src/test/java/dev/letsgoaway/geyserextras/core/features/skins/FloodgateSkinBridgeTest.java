package dev.letsgoaway.geyserextras.core.features.skins;

import dev.letsgoaway.geyserextras.core.GeyserExtras;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.event.FloodgateEventBus;
import org.geysermc.floodgate.api.event.skin.SkinApplyEvent;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.geysermc.floodgate.util.LinkedPlayer;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.network.AuthType;
import org.geysermc.geyser.api.network.RemoteServer;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.util.PluginMessageUtils;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FloodgateSkinBridgeTest {
    @Test
    void localLiveBindingMustMatchAuthenticatedJavaUuidAndBedrockXuid() {
        Fixture f = new Fixture();
        when(f.session.xuid()).thenReturn("123");
        FloodgatePlayer player = mock(FloodgatePlayer.class);
        when(f.api.getPlayer(f.uuid)).thenReturn(player);
        when(player.isLinked()).thenReturn(true);
        when(player.getCorrectUniqueId()).thenReturn(f.uuid);
        when(player.getXuid()).thenReturn("123");
        when(player.getLinkedPlayer()).thenReturn(LinkedPlayer.of("Account", f.uuid, new UUID(0, 123)));
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.TRUE, FloodgateSkinBridge.linkedIdentity(f.api, f.session));
        when(player.isLinked()).thenReturn(false);
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.FALSE, FloodgateSkinBridge.linkedIdentity(f.api, f.session));
        when(player.isLinked()).thenReturn(true);
        when(player.getXuid()).thenReturn("456");
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.FALSE, FloodgateSkinBridge.linkedIdentity(f.api, f.session));
    }
    @Test
    void optOutAllowsFloodgateToApplyUploadedBedrockSkinForLinkedPlayer() {
        Fixture f = new Fixture();
        f.bridge.onApply(f.event);
        verify(f.event).setCancelled(false);
        when(f.appearance.usesJavaSkin(f.session)).thenReturn(true);
        clearInvocations(f.event);
        f.bridge.onApply(f.event);
        verify(f.event).setCancelled(true);
    }

    @Test
    void lateJavaUpdateIsReplacedWithSelectedBedrockSkin() {
        Fixture f = new Fixture();
        f.bridge.onApply(f.event);
        SkinApplyEvent.SkinData java = texture(f.uuid);
        when(f.event.newSkin()).thenReturn(java);
        clearInvocations(f.event);
        f.bridge.onApply(f.event);
        verify(f.event).newSkin(f.bedrock);
        verify(f.event).setCancelled(false);
    }

    @Test
    void preferenceChangeDiscardsPendingSignedJavaDownload() {
        Fixture f = new Fixture();
        when(f.appearance.usesJavaSkin(f.session)).thenReturn(true);
        CompletableFuture<JavaSkinLoader.SignedTexture> pending = new CompletableFuture<>();
        when(f.loader.signedTexture(f.uuid)).thenReturn(pending);
        doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; }).when(f.session).executeInEventLoop(any());
        try (var messages = mockStatic(PluginMessageUtils.class)) {
            f.bridge.refresh(f.session, true);
            when(f.appearance.usesJavaSkin(f.session)).thenReturn(false);
            SkinApplyEvent.SkinData java = texture(f.uuid);
            pending.complete(new JavaSkinLoader.SignedTexture(java.value(), java.signature()));
            messages.verifyNoInteractions();
        }
    }

    @Test
    void unrelatedPlayerAndUnlinkedPlayerKeepFloodgatePolicy() {
        Fixture f = new Fixture();
        when(f.plugin.geyserApi.connectionByUuid(f.uuid)).thenReturn(null);
        f.bridge.onApply(f.event);
        verify(f.event, never()).setCancelled(anyBoolean());
        when(f.plugin.geyserApi.connectionByUuid(f.uuid)).thenReturn(f.session);
        when(f.session.javaUuid()).thenReturn(new UUID(0, 12));
        f.bridge.onApply(f.event);
        verify(f.event, never()).setCancelled(anyBoolean());
    }

    private static SkinApplyEvent.SkinData texture(UUID uuid) {
        SkinApplyEvent.SkinData data = mock(SkinApplyEvent.SkinData.class);
        when(data.value()).thenReturn(Base64.getEncoder().encodeToString(("{\"profileId\":\"" + uuid.toString().replace("-", "") + "\"}").getBytes(StandardCharsets.UTF_8)));
        when(data.signature()).thenReturn("signed-test-property");
        return data;
    }

    private static final class Fixture {
        final UUID uuid = UUID.randomUUID();
        final GeyserExtras plugin = mock(GeyserExtras.class);
        final GeyserSession session = mock(GeyserSession.class);
        final LinkedJavaSkin appearance = mock(LinkedJavaSkin.class);
        final JavaSkinLoader loader = mock(JavaSkinLoader.class);
        final FloodgateApi api = mock(FloodgateApi.class);
        final SkinApplyEvent event = mock(SkinApplyEvent.class, RETURNS_DEEP_STUBS);
        final SkinApplyEvent.SkinData bedrock = texture(UUID.randomUUID());
        final FloodgateSkinBridge bridge;

        Fixture() {
            when(api.getEventBus()).thenReturn(mock(FloodgateEventBus.class));
            plugin.geyserApi = mock(GeyserApi.class);
            when(plugin.geyserApi.connectionByUuid(uuid)).thenReturn(session);
            when(session.javaUuid()).thenReturn(uuid);
            RemoteServer remote = mock(RemoteServer.class);
            when(session.remoteServer()).thenReturn(remote);
            when(remote.authType()).thenReturn(AuthType.FLOODGATE);
            when(event.player().getCorrectUniqueId()).thenReturn(uuid);
            when(event.newSkin()).thenReturn(bedrock);
            bridge = new FloodgateSkinBridge(plugin, appearance, loader, api);
        }
    }
}
