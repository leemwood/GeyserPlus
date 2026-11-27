package dev.letsgoaway.geyserextras.core.features.skins;

import dev.letsgoaway.geyserextras.core.ExtrasPlayer;
import dev.letsgoaway.geyserextras.core.GeyserExtras;
import org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin;
import org.geysermc.geyser.entity.type.player.AvatarEntity;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.event.bedrock.SessionSkinApplyEvent;
import org.geysermc.geyser.api.network.AuthType;
import org.geysermc.geyser.api.skin.SkinData;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.util.PlayerListUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static dev.letsgoaway.geyserextras.core.GeyserExtras.SERVER;

public final class LinkedJavaSkin {
    private final GeyserExtras plugin;
    private final JavaSkinLoader loader;
    private final Map<UUID, Appearance> appearances = new ConcurrentHashMap<>();
    private final Map<GeyserConnection, SerializedSkin> loginSkins = new ConcurrentHashMap<>();
    private FloodgateSkinBridge floodgate;
    private PlayerSkinCache skinCache;
    private BedrockCapeResolver capeResolver;

    public LinkedJavaSkin(GeyserExtras plugin) {
        this(plugin, new JavaSkinLoader());
    }

    LinkedJavaSkin(GeyserExtras plugin, JavaSkinLoader loader) {
        this(plugin, loader, null);
    }

    LinkedJavaSkin(GeyserExtras plugin, JavaSkinLoader loader, BedrockCapeResolver capeResolver) {
        this.plugin = plugin;
        this.loader = loader;
        this.capeResolver = capeResolver;
    }

    public void initialize() {
        skinCache = new PlayerSkinCache(SERVER.getPluginFolder().resolve("cache/player-skins"));
        capeResolver = new BedrockCapeResolver(SERVER.getPluginFolder().resolve("cache/cape-textures"));
        floodgate = FloodgateSkinBridge.create(plugin, this, loader, skinCache);
    }

    public boolean usesJavaSkin(GeyserConnection connection) {
        if (!(connection instanceof GeyserSession session) || !isLinked(session)) {
            return false;
        }
        ExtrasPlayer player = plugin.connections == null ? null : plugin.connections.get(connection.xuid());
        boolean serverDefault = plugin.getConfig().isUseLinkedJavaSkin();
        return player == null ? serverDefault : player.getPreferences().useLinkedJavaSkin(serverDefault);
    }

    public boolean usesJavaCape(GeyserConnection connection) {
        if (!(connection instanceof GeyserSession session) || !isLinked(session)) return false;
        ExtrasPlayer player = plugin.connections == null ? null : plugin.connections.get(connection.xuid());
        boolean skinSource = usesJavaSkin(connection);
        return player == null ? skinSource : player.getPreferences().useLinkedJavaCape(skinSource);
    }

    private boolean needsJava(GeyserSession session) {
        return usesJavaSkin(session) || usesJavaCape(session);
    }

    public void captureBedrock(GeyserConnection connection) {
        GeyserSession session = (GeyserSession) connection;
        if (skinCache != null) {
            skinCache.load(session.xuid());
        }
        if (capeResolver != null && (session.getClientData().getCapeData() == null || session.getClientData().getCapeData().length == 0)) {
            // Warm the shared cache before the Java login completes. The result
            // is applied only to the matching authenticated session in prepare.
            capeResolver.resolve(session.getClientData().getCapeId());
        }
        try {
            loginSkins.put(connection, BedrockSkinLoader.serialized(session.getClientData(), session.playFabId()));
        } catch (RuntimeException e) {
            SERVER.warn("Could not preserve extended Bedrock appearance for " + session.bedrockUsername()
                    + " (" + e.getClass().getSimpleName() + "); using its basic skin data.");
        }
    }

    public void onJoin(GeyserConnection connection) {
        GeyserSession session = (GeyserSession) connection;
        prepare(session);
        // Start applying as soon as the Java UUID is assigned; retry after the
        // joining client and delayed vanilla skin packets have settled.
        broadcast(session.javaUuid());
        session.scheduleInEventLoop(() -> {
            if (current(session)) broadcast(session.javaUuid());
        }, 3, TimeUnit.SECONDS);
        session.scheduleInEventLoop(() -> {
            if (!current(session)) {
                return;
            }
            for (GeyserConnection target : plugin.geyserApi.onlineConnections()) {
                applyCached(session, target.javaUuid());
            }
            refresh(session);
            if (floodgate != null) {
                floodgate.refresh(session, false);
            }
        }, 1, TimeUnit.SECONDS);
    }

    public void prepare(GeyserSession session) {
        Appearance existing = appearances.get(session.javaUuid());
        if (existing != null && existing.connection == session) return;
        SkinData bedrock = null;
        try {
            bedrock = BedrockSkinLoader.read(session.getClientData());
            if (skinCache != null) {
                String hash = PlayerSkinCache.hash(session.getClientData());
                SkinData cached = skinCache.bedrockSkin(session.xuid(), hash);
                if (cached != null) bedrock = cached;
                else skinCache.saveBedrock(session.xuid(), hash, bedrock);
            }
        } catch (RuntimeException e) {
            SERVER.warn("Could not read Bedrock skin for " + session.bedrockUsername() + ": " + e.getMessage());
        }
        Appearance prepared = new Appearance(session, bedrock, loginSkins.remove(session));
        prepared.java = skinCache == null ? loader.cached(session.javaUuid()) : skinCache.javaSkin(session.xuid(), session.javaUuid());
        appearances.put(session.javaUuid(), prepared);
        resolveBedrockCape(session, prepared);
        if (needsJava(session)) refresh(session);
    }

    private void resolveBedrockCape(GeyserSession session, Appearance appearance) {
        if (capeResolver == null || appearance.bedrock == null || appearance.bedrock.cape().capeData().length > 0) return;
        String id = session.getClientData().getCapeId();
        var definition = BedrockCapeResolver.definition(id);
        if (definition == null) {
            if (id != null && !id.isBlank()) {
                SERVER.log("Bedrock cape has no pixels and no known mapping for " + session.bedrockUsername() + " (cape ID: " + id + ").");
            }
            return;
        }
        capeResolver.resolve(id).whenComplete((cape, error) -> session.executeInEventLoop(() -> {
            if (!current(session) || appearances.get(session.javaUuid()) != appearance) return;
            if (error != null) {
                SERVER.warn("Could not resolve mapped Bedrock cape " + definition.name() + " for " + session.bedrockUsername()
                        + " (" + error.getClass().getSimpleName() + ").");
                return;
            }
            if (cape == null) return;
            SkinData source = appearance.bedrock;
            appearance.bedrock = new SkinData(source.skin(), cape, source.geometry());
            if (skinCache != null) skinCache.saveBedrock(session.xuid(), PlayerSkinCache.hash(session.getClientData()), appearance.bedrock);
            broadcast(session.javaUuid());
            SERVER.log("Resolved Bedrock cape " + definition.name() + " for " + session.bedrockUsername() + " from equipped cape ID.");
        }));
    }

    public void setUseJavaSkin(ExtrasPlayer player, boolean enabled) {
        setSources(player, enabled, player.getPreferences().getUseLinkedJavaCape());
    }

    public void setSources(ExtrasPlayer player, boolean javaSkin, Boolean javaCape) {
        GeyserSession session = player.getSession();
        if (!isLinked(session)) {
            return;
        }
        player.getPreferences().setUseLinkedJavaSkin(javaSkin);
        player.getPreferences().setUseLinkedJavaCape(javaCape);
        player.getPreferences().save();
        session.executeInEventLoop(() -> {
            if (current(session)) {
                refresh(session);
                if (floodgate != null) {
                    floodgate.refresh(session, true);
                }
            }
        });
    }

    private void refresh(GeyserSession session) {
        Appearance appearance = appearances.get(session.javaUuid());
        if (appearance == null || appearance.connection != session) {
            return;
        }
        long revision = ++appearance.revision;
        broadcast(session.javaUuid());
        if (!needsJava(session)) {
            SERVER.log("Using Bedrock skin for " + session.bedrockUsername() + ".");
            return;
        }
        loader.load(session.javaUuid()).whenComplete((skin, error) -> session.executeInEventLoop(() -> {
            if (!current(session) || appearances.get(session.javaUuid()) != appearance
                    || appearance.revision != revision || !needsJava(session)) {
                return;
            }
            if (error != null) {
                SERVER.warn("Could not load linked Java skin for " + session.javaUuid()
                        + "; keeping the current appearance: " + error.getMessage());
                return;
            }
            appearance.java = skin;
            appearance.serializedJava = null;
            if (skinCache != null) skinCache.saveJava(session.xuid(), session.javaUuid(), skin);
            broadcast(session.javaUuid());
            SERVER.log("Applied linked Java skin for " + session.bedrockUsername() + " (" + session.javaUuid()
                    + ", cape: " + !skin.cape().capeId().equals("no-cape") + ").");
        }));
    }

    public void onSkinApply(SessionSkinApplyEvent event) {
        SkinData skin = skinFor(event.uuid());
        if (skin != null) {
            event.skin(skin.skin());
            event.cape(skin.cape());
            event.geometry(skin.geometry());
            Appearance appearance = appearances.get(event.uuid());
            if (appearance != null && appearance.serialized != null) {
                // The public SkinData API omits persona pieces and animations.
                // Restore the full captured appearance after Geyser's skin update.
                GeyserSession viewer = (GeyserSession) event.connection();
                viewer.scheduleInEventLoop(() -> applyCached(viewer, event.uuid()), 150, TimeUnit.MILLISECONDS);
            }
        }
    }

    public void applyCached(GeyserSession viewer, UUID uuid) {
        if (skinFor(uuid) != null) {
            viewer.executeInEventLoop(() -> {
                // Re-read after scheduling so a stale update cannot undo an opt-out.
                SkinData selected = skinFor(uuid);
                if (selected != null && current(viewer)) {
                    Appearance appearance = appearances.get(uuid);
                    if (appearance != null && appearance.serialized != null) {
                        // Vanilla fallback skin packets are delayed by 100 ms.
                        // Send after them, and recheck the current choice again.
                        viewer.scheduleInEventLoop(() -> {
                            Appearance latest = appearances.get(uuid);
                            if (latest == appearance && current(viewer) && current(appearance.connection)) {
                                SerializedSkin latestSkin = serializedFor(uuid);
                                if (latestSkin != null) sendAppearance(viewer, uuid, latestSkin);
                            }
                        }, 150, TimeUnit.MILLISECONDS);
                    } else {
                        viewer.sendSkin(uuid, selected);
                    }
                }
            });
        }
    }

    private void broadcast(UUID uuid) {
        for (GeyserConnection viewer : plugin.geyserApi.onlineConnections()) {
            applyCached((GeyserSession) viewer, uuid);
        }
    }

    public void onDisconnect(GeyserConnection connection) {
        loginSkins.remove(connection);
        UUID uuid = connection.javaUuid();
        if (uuid != null) {
            appearances.computeIfPresent(uuid, (id, value) -> value.connection == connection ? null : value);
        }
        if (floodgate != null) {
            floodgate.onDisconnect(connection);
        }
    }

    public void close() {
        if (floodgate != null) {
            floodgate.close();
        }
        appearances.clear();
        loginSkins.clear();
        if (skinCache != null) skinCache.close();
    }

    public SerializedSkin serializedFor(UUID uuid) {
        Appearance appearance = appearances.get(uuid);
        if (appearance == null || !current(appearance.connection)) return null;
        boolean javaSkin = usesJavaSkin(appearance.connection) && appearance.java != null;
        SerializedSkin base = javaSkin ? serializedJava(appearance) : appearance.serialized;
        if (base == null) return null;
        SkinData selected = skinFor(uuid);
        if (selected == null) return base;
        if (base.getCapeId().equals(selected.cape().capeId())
                && java.util.Arrays.equals(base.getCapeData().getImage(), selected.cape().capeData())) return base;
        // Keep Bedrock animation/persona/geometry metadata intact when swapping
        // just the cape. A new combined id invalidates the client's cached cape.
        String id = base.getSkinId() + "_cape_" + selected.cape().capeId();
        return base.toBuilder().skinId(id).fullSkinId(id).capeId(selected.cape().capeId())
                .capeData(org.cloudburstmc.protocol.bedrock.data.skin.ImageData.of(selected.cape().capeData()))
                .capeOnClassic(true).profileHash("").build();
    }

    private SerializedSkin serializedJava(Appearance appearance) {
        SkinData skin = appearance.java;
        SerializedSkin cached = appearance.serializedJava;
        if (cached != null) return cached;
        String geometry = skin.geometry().geometryData();
        if (geometry.isBlank()) {
            geometry = new String(org.geysermc.geyser.util.FileUtils.readAllBytes("bedrock/geometries/geo.json"), java.nio.charset.StandardCharsets.UTF_8);
        }
        SerializedSkin selected = SerializedSkin.builder().skinId(skin.skin().textureUrl()).fullSkinId(skin.skin().textureUrl())
                .skinResourcePatch(skin.geometry().geometryName()).skinData(org.cloudburstmc.protocol.bedrock.data.skin.ImageData.of(skin.skin().skinData()))
                .capeId(skin.cape().capeId()).capeData(org.cloudburstmc.protocol.bedrock.data.skin.ImageData.of(skin.cape().capeData()))
                .geometryData(geometry).geometryDataEngineVersion(appearance.connection.getClientData().getGameVersion())
                .capeOnClassic(true).premium(true).overridingPlayerAppearance(true).trusted(true).profileHash("").build();
        if (appearance.java == skin) appearance.serializedJava = selected;
        return selected;
    }

    private void sendAppearance(GeyserSession viewer, UUID uuid, SerializedSkin skin) {
        AvatarEntity entity = viewer.getEntityCache().getEntityByUuid(uuid) instanceof AvatarEntity avatar
                ? avatar : viewer.getEntityCache().getPlayerEntity(uuid);
        if (entity != null) {
            var entry = PlayerListUtils.buildEntryManually(viewer, uuid, entity.getUsername(), entity.geyserId(), skin);
            PlayerListUtils.sendSkinUsingPlayerList(viewer, entry, entity, entity.isListed());
        }
    }

    private boolean current(GeyserSession session) {
        return plugin.geyserApi.connectionByUuid(session.javaUuid()) == session;
    }

    private SkinData skinFor(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        Appearance appearance = appearances.get(uuid);
        if (appearance == null || !current(appearance.connection)) {
            return null;
        }
        SkinData skin = usesJavaSkin(appearance.connection) && appearance.java != null ? appearance.java : appearance.bedrock;
        SkinData cape = usesJavaCape(appearance.connection) && appearance.java != null ? appearance.java : appearance.bedrock;
        if (skin == null) return null;
        return cape == null || skin == cape ? skin : new SkinData(skin.skin(), cape.cape(), skin.geometry());
    }

    private static final class Appearance {
        final GeyserSession connection;
        volatile SkinData bedrock;
        final SerializedSkin serialized;
        volatile SkinData java;
        volatile SerializedSkin serializedJava;
        long revision;

        Appearance(GeyserSession connection, SkinData bedrock, SerializedSkin serialized) {
            this.connection = connection;
            this.bedrock = bedrock;
            this.serialized = serialized;
        }
    }

    public static boolean isLinked(GeyserSession session) {
        // Geyser 2.12.1's isLinked() is a stub. The completed Floodgate handshake
        // supplies a version-4 Java UUID for linked accounts, version 0 otherwise.
        boolean handshake = session.remoteServer().authType() == AuthType.FLOODGATE
                && session.javaUuid() != null && session.javaUuid().version() == 4;
        if (!handshake) return false;
        Boolean verified = FloodgateSkinBridge.linkedIdentity(session);
        return verified == null || verified;
    }
}
