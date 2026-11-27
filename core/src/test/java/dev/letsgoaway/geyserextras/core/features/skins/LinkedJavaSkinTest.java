package dev.letsgoaway.geyserextras.core.features.skins;

import dev.letsgoaway.geyserextras.Server;
import dev.letsgoaway.geyserextras.core.ExtrasPlayer;
import dev.letsgoaway.geyserextras.core.GeyserExtras;
import dev.letsgoaway.geyserextras.core.config.GeyserExtrasConfig;
import dev.letsgoaway.geyserextras.core.preferences.PreferencesData;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.bedrock.SessionSkinApplyEvent;
import org.geysermc.geyser.api.network.AuthType;
import org.geysermc.geyser.api.network.RemoteServer;
import org.geysermc.geyser.api.skin.*;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.auth.BedrockClientData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LinkedJavaSkinTest {
    private final Server previousServer = GeyserExtras.SERVER;

    @AfterEach
    void restoreServer() {
        GeyserExtras.SERVER = previousServer;
    }

    @Test
    void onlyCompletedFloodgateIdentityCountsAsLinked() {
        Fixture f = new Fixture(true);
        assertTrue(LinkedJavaSkin.isLinked(f.session));
        when(f.session.javaUuid()).thenReturn(new UUID(0, 1234));
        assertFalse(LinkedJavaSkin.isLinked(f.session));
        when(f.session.javaUuid()).thenReturn(f.uuid);
        when(f.remote.authType()).thenReturn(AuthType.OFFLINE);
        assertFalse(LinkedJavaSkin.isLinked(f.session));
    }

    @Test
    void sessionKeepsJavaSkinAfterDownloadCacheExpiresAndReleasesOnDisconnect() {
        Fixture f = new Fixture(true);
        f.join();
        f.download.complete(f.javaSkin);
        verify(f.session).sendSkin(f.uuid, f.javaSkin);
        f.assertEventSkin(f.javaSkin);
        f.service.onDisconnect(f.session);
        SessionSkinApplyEvent event = mock(SessionSkinApplyEvent.class);
        when(event.uuid()).thenReturn(f.uuid);
        f.service.onSkinApply(event);
        verify(event, never()).skin(any());
    }

    @Test
    void explicitOptOutBeatsServerDefaultAndRetainsBedrockGeometryAndCape() {
        Fixture f = new Fixture(true);
        f.choice.set(false);
        f.join();
        verify(f.loader, never()).load(any());
        ArgumentCaptor<SkinData> sent = ArgumentCaptor.forClass(SkinData.class);
        verify(f.session, atLeastOnce()).sendSkin(eq(f.uuid), sent.capture());
        assertArrayEquals(f.bedrockPixels, sent.getValue().skin().skinData());
        assertEquals("bedrock-cape", sent.getValue().cape().capeId());
        assertTrue(sent.getValue().geometry().geometryData().contains("自定义"));
        f.assertEventSkin(sent.getValue());
    }

    @Test
    void optInWorksWhenServerDefaultIsOffAndLateDownloadCannotUndoOptOut() {
        Fixture f = new Fixture(false);
        f.join();
        f.service.setUseJavaSkin(f.player, true);
        verify(f.loader).load(f.uuid);
        f.service.setUseJavaSkin(f.player, false);
        clearInvocations(f.session);
        f.download.complete(f.javaSkin);
        verify(f.session, never()).sendSkin(f.uuid, f.javaSkin);
        SessionSkinApplyEvent event = mock(SessionSkinApplyEvent.class);
        when(event.uuid()).thenReturn(f.uuid);
        f.service.onSkinApply(event);
        verify(event).skin(argThat(skin -> skin.textureUrl().equals("bedrock-skin")));
    }

    @Test
    void joiningViewerReceivesOtherPlayersSelectedSkinAndQueuedUpdatesUseLatestChoice() {
        Fixture f = new Fixture(true);
        f.join();
        f.download.complete(f.javaSkin);
        GeyserSession viewer = mock(GeyserSession.class);
        UUID viewerId = UUID.randomUUID();
        when(viewer.javaUuid()).thenReturn(viewerId);
        when(f.plugin.geyserApi.connectionByUuid(viewerId)).thenReturn(viewer);
        f.service.applyCached(viewer, f.uuid);
        ArgumentCaptor<Runnable> update = ArgumentCaptor.forClass(Runnable.class);
        verify(viewer).executeInEventLoop(update.capture());
        f.service.setUseJavaSkin(f.player, false);
        update.getValue().run();
        verify(viewer).sendSkin(eq(f.uuid), argThat(skin -> skin.skin().textureUrl().equals("bedrock-skin")));
    }

    @Test
    void disconnectOfOldSessionCannotRemoveReplacementAppearance() {
        Fixture f = new Fixture(true);
        f.join();
        GeyserSession old = mock(GeyserSession.class);
        when(old.javaUuid()).thenReturn(f.uuid);
        f.download.complete(f.javaSkin);
        f.service.onDisconnect(old);
        f.assertEventSkin(f.javaSkin);
    }

    @Test
    void appearanceIsPreparedBeforeJoinAndPendingJavaDownloadUsesActualBedrockSkin() {
        Fixture f = new Fixture(true);
        f.service.captureBedrock(f.session);
        f.service.prepare(f.session);
        assertEquals("bedrock-skin", f.service.serializedFor(f.uuid).getSkinId());
        f.download.complete(f.javaSkin);
        f.assertEventSkin(f.javaSkin);
        verify(f.session, never()).scheduleInEventLoop(any(), eq(1L), eq(TimeUnit.SECONDS));
    }

    @Test
    void bedrockSkinCanUseJavaCapeWithoutReplacingCustomGeometry() {
        Fixture f = new Fixture(false);
        f.service.captureBedrock(f.session);
        f.join();
        f.service.setSources(f.player, false, true);
        f.download.complete(f.javaSkin);
        var event = mock(SessionSkinApplyEvent.class);
        when(event.uuid()).thenReturn(f.uuid);
        when(event.connection()).thenReturn(f.session);
        f.service.onSkinApply(event);
        verify(event).skin(argThat(skin -> skin.textureUrl().equals("bedrock-skin")));
        verify(event).cape(f.javaSkin.cape());
        verify(event).geometry(argThat(geometry -> geometry.geometryData().contains("自定义")));
        var serialized = f.service.serializedFor(f.uuid);
        assertEquals("cape", serialized.getCapeId());
        assertArrayEquals(f.javaSkin.cape().capeData(), serialized.getCapeData().getImage());
        assertTrue(serialized.getGeometryData().contains("自定义"));
        assertTrue(serialized.getSkinId().startsWith("bedrock-skin"));
    }

    @Test
    void javaSkinCanUseBedrockCapeAndUnlinkedAccountsCannotSelectOfficialCape() {
        Fixture f = new Fixture(true);
        f.join();
        f.service.setSources(f.player, true, false);
        f.download.complete(f.javaSkin);
        var event = mock(SessionSkinApplyEvent.class);
        when(event.uuid()).thenReturn(f.uuid);
        f.service.onSkinApply(event);
        verify(event).skin(f.javaSkin.skin());
        verify(event).cape(argThat(cape -> cape.capeId().equals("bedrock-cape")));
        verify(event).geometry(f.javaSkin.geometry());
        var serialized = f.service.serializedFor(f.uuid);
        assertEquals("bedrock-cape", serialized.getCapeId());
        assertArrayEquals(f.javaSkin.skin().skinData(), serialized.getSkinData().getImage());
        when(f.session.javaUuid()).thenReturn(new UUID(0, 123));
        assertFalse(f.service.usesJavaSkin(f.session));
        assertFalse(f.service.usesJavaCape(f.session));
        clearInvocations(f.preferences);
        f.service.setSources(f.player, true, true);
        verify(f.preferences, never()).setUseLinkedJavaCape(any());
    }

    @Test
    void selectedJavaSourceWithNoCapeStaysEmptyInsteadOfUsingOtherSource() {
        Fixture f = new Fixture(false);
        f.join();
        f.service.setSources(f.player, false, true);
        var noCape = new Cape("", "no-cape", new byte[0], false);
        f.download.complete(new SkinData(f.javaSkin.skin(), noCape, f.javaSkin.geometry()));
        var event = mock(SessionSkinApplyEvent.class);
        when(event.uuid()).thenReturn(f.uuid);
        f.service.onSkinApply(event);
        verify(event).cape(noCape);
    }

    @Test
    void missingBedrockPixelsResolveByEquippedIdAndReachBothSerializedAndEventSkins() {
        BedrockCapeResolver resolver = mock(BedrockCapeResolver.class);
        CompletableFuture<Cape> pending = new CompletableFuture<>();
        when(resolver.resolve(BedrockCapeResolverTest.AURORA_ID)).thenReturn(pending);
        Fixture f = new Fixture(false, resolver);
        when(f.session.getClientData().getCapeId()).thenReturn(BedrockCapeResolverTest.AURORA_ID);
        when(f.session.getClientData().getCapeData()).thenReturn(new byte[0]);
        f.service.captureBedrock(f.session);
        f.join();
        assertEquals(0, f.service.serializedFor(f.uuid).getCapeData().getImage().length);
        Cape mapped = new Cape("official", "bedrock-aurora", new byte[8192], false);
        pending.complete(mapped);
        assertEquals(mapped.capeId(), f.service.serializedFor(f.uuid).getCapeId());
        assertArrayEquals(mapped.capeData(), f.service.serializedFor(f.uuid).getCapeData().getImage());
        var event = mock(SessionSkinApplyEvent.class);
        when(event.uuid()).thenReturn(f.uuid);
        when(event.connection()).thenReturn(f.session);
        f.service.onSkinApply(event);
        verify(event).cape(mapped);
    }

    @Test
    void submittedPixelsTakePriorityAndLateMappedCapeCannotOverrideJavaSelection() {
        BedrockCapeResolver resolver = mock(BedrockCapeResolver.class);
        Fixture submitted = new Fixture(false, resolver);
        when(submitted.session.getClientData().getCapeId()).thenReturn(BedrockCapeResolverTest.AURORA_ID);
        submitted.join();
        verify(resolver, never()).resolve(any());

        Fixture f = new Fixture(false, resolver);
        CompletableFuture<Cape> pending = new CompletableFuture<>();
        when(resolver.resolve(BedrockCapeResolverTest.AURORA_ID)).thenReturn(pending);
        when(f.session.getClientData().getCapeId()).thenReturn(BedrockCapeResolverTest.AURORA_ID);
        when(f.session.getClientData().getCapeData()).thenReturn(new byte[0]);
        f.join();
        f.service.setSources(f.player, true, true);
        f.download.complete(f.javaSkin);
        pending.complete(new Cape("official", "bedrock-aurora", new byte[8192], false));
        f.assertEventSkin(f.javaSkin);
    }

    private static final class Fixture {
        final UUID uuid = UUID.randomUUID();
        final GeyserExtras plugin = mock(GeyserExtras.class);
        final GeyserExtrasConfig config = mock(GeyserExtrasConfig.class);
        final GeyserSession session = mock(GeyserSession.class);
        final RemoteServer remote = mock(RemoteServer.class);
        final ExtrasPlayer player = mock(ExtrasPlayer.class);
        final PreferencesData preferences = mock(PreferencesData.class);
        final JavaSkinLoader loader = mock(JavaSkinLoader.class);
        final AtomicReference<Boolean> choice = new AtomicReference<>();
        final AtomicReference<Boolean> capeChoice = new AtomicReference<>();
        final CompletableFuture<SkinData> download = new CompletableFuture<>();
        final SkinData javaSkin = new SkinData(new Skin("java", new byte[64 * 64 * 4]), new Cape("cape", "cape", new byte[64 * 32 * 4], false),
                new SkinGeometry(SkinGeometry.SLIM.geometryName(), "{\"java-test-geometry\":true}"));
        final byte[] bedrockPixels = new byte[64 * 64 * 4];
        final LinkedJavaSkin service;

        Fixture(boolean serverDefault) {
            this(serverDefault, null);
        }

        Fixture(boolean serverDefault, BedrockCapeResolver capeResolver) {
            GeyserExtras.SERVER = mock(Server.class);
            when(plugin.getConfig()).thenReturn(config);
            when(config.isUseLinkedJavaSkin()).thenReturn(serverDefault);
            plugin.geyserApi = mock(GeyserApi.class);
            plugin.connections = new ConcurrentHashMap<>(Map.of("123", player));
            when(session.xuid()).thenReturn("123");
            when(session.javaUuid()).thenReturn(uuid);
            when(session.bedrockUsername()).thenReturn("BedrockUser");
            when(session.remoteServer()).thenReturn(remote);
            when(remote.authType()).thenReturn(AuthType.FLOODGATE);
            when(plugin.geyserApi.connectionByUuid(uuid)).thenReturn(session);
            doReturn(List.of(session)).when(plugin.geyserApi).onlineConnections();
            when(player.getSession()).thenReturn(session);
            when(player.getPreferences()).thenReturn(preferences);
            when(preferences.useLinkedJavaSkin(anyBoolean())).thenAnswer(call -> choice.get() == null ? call.getArgument(0) : choice.get());
            when(preferences.useLinkedJavaCape(anyBoolean())).thenAnswer(call -> capeChoice.get() == null ? call.getArgument(0) : capeChoice.get());
            when(preferences.getUseLinkedJavaCape()).thenAnswer(call -> capeChoice.get());
            doAnswer(call -> { choice.set(call.getArgument(0)); return null; }).when(preferences).setUseLinkedJavaSkin(anyBoolean());
            doAnswer(call -> { capeChoice.set(call.getArgument(0)); return null; }).when(preferences).setUseLinkedJavaCape(any());
            when(loader.load(uuid)).thenReturn(download);
            doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; }).when(session).executeInEventLoop(any());
            BedrockClientData data = mock(BedrockClientData.class);
            when(data.getSkinId()).thenReturn("bedrock-skin");
            when(data.getSkinImageWidth()).thenReturn(64);
            when(data.getSkinImageHeight()).thenReturn(64);
            when(data.getSkinData()).thenReturn(bedrockPixels);
            when(data.getCapeId()).thenReturn("bedrock-cape");
            when(data.getCapeData()).thenReturn(new byte[64 * 32 * 4]);
            when(data.getCapeImageWidth()).thenReturn(64);
            when(data.getCapeImageHeight()).thenReturn(32);
            when(data.getGeometryName()).thenReturn("{\"geometry\":{\"default\":\"custom\"}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            when(data.getGeometryData()).thenReturn("{\"自定义\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            when(session.getClientData()).thenReturn(data);
            service = new LinkedJavaSkin(plugin, loader, capeResolver);
        }

        void join() {
            service.onJoin(session);
            ArgumentCaptor<Runnable> joined = ArgumentCaptor.forClass(Runnable.class);
            verify(session).scheduleInEventLoop(joined.capture(), eq(1L), eq(TimeUnit.SECONDS));
            joined.getValue().run();
        }

        void assertEventSkin(SkinData selected) {
            SessionSkinApplyEvent event = mock(SessionSkinApplyEvent.class);
            when(event.uuid()).thenReturn(uuid);
            when(event.connection()).thenReturn(session);
            service.onSkinApply(event);
            verify(event).skin(selected.skin());
            verify(event).cape(selected.cape());
            verify(event).geometry(selected.geometry());
        }
    }
}
