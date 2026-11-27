package dev.letsgoaway.geyserextras.core.features.skins;

import dev.letsgoaway.geyserextras.core.GeyserExtras;
import dev.letsgoaway.geyserextras.core.utils.IsAvailable;
import org.geysermc.event.PostOrder;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.event.FloodgateSubscriber;
import org.geysermc.floodgate.api.event.skin.SkinApplyEvent;
import org.geysermc.floodgate.api.player.PropertyKey;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.util.PluginMessageUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static dev.letsgoaway.geyserextras.core.GeyserExtras.SERVER;

/** Lets Floodgate apply its signed Bedrock conversion even for linked players. */
final class FloodgateSkinBridge {
    static Boolean linkedIdentity(GeyserSession session) {
        if (!IsAvailable.classExists("org.geysermc.floodgate.api.FloodgateApi")) return null;
        try {
            return linkedIdentity(FloodgateApi.getInstance(), session);
        } catch (RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    static Boolean linkedIdentity(FloodgateApi api, GeyserSession session) {
        var player = api.getPlayer(session.javaUuid());
        if (player == null) return null; // Remote Floodgate / early login.
        return player.isLinked() && session.javaUuid().equals(player.getCorrectUniqueId())
                && session.xuid().equals(player.getXuid()) && player.getLinkedPlayer() != null
                && session.javaUuid().equals(player.getLinkedPlayer().getJavaUniqueId());
    }
    private final GeyserExtras plugin;
    private final LinkedJavaSkin appearance;
    private final JavaSkinLoader loader;
    private final FloodgateApi api;
    private final PlayerSkinCache cache;
    private final FloodgateSubscriber<SkinApplyEvent> subscription;
    private final Map<UUID, Upload> uploads = new ConcurrentHashMap<>();

    static FloodgateSkinBridge create(GeyserExtras plugin, LinkedJavaSkin appearance, JavaSkinLoader loader, PlayerSkinCache cache) {
        if (!IsAvailable.classExists("org.geysermc.floodgate.api.FloodgateApi")) {
            return null;
        }
        try {
            return new FloodgateSkinBridge(plugin, appearance, loader, FloodgateApi.getInstance(), cache);
        } catch (RuntimeException | LinkageError error) {
            SERVER.warn("Floodgate skin synchronization is unavailable: " + error.getMessage());
            return null;
        }
    }

    FloodgateSkinBridge(GeyserExtras plugin, LinkedJavaSkin appearance, JavaSkinLoader loader, FloodgateApi api) {
        this(plugin, appearance, loader, api, null);
    }

    private FloodgateSkinBridge(GeyserExtras plugin, LinkedJavaSkin appearance, JavaSkinLoader loader, FloodgateApi api, PlayerSkinCache cache) {
        this.plugin = plugin;
        this.appearance = appearance;
        this.loader = loader;
        this.api = api;
        this.cache = cache;
        subscription = api.getEventBus().subscribe(SkinApplyEvent.class, this::onApply, PostOrder.LAST);
    }

    void onApply(SkinApplyEvent event) {
        UUID uuid = event.player().getCorrectUniqueId();
        if (!(plugin.geyserApi.connectionByUuid(uuid) instanceof GeyserSession session) || !LinkedJavaSkin.isLinked(session)) {
            return;
        }
        Upload upload = uploads.compute(uuid, (id, existing) -> existing != null && existing.connection == session ? existing : new Upload(session));
        SkinApplyEvent.SkinData incoming = event.newSkin();
        boolean java = new JavaSkinLoader.SignedTexture(incoming.value(), incoming.signature()).belongsTo(uuid);
        if (!java) {
            upload.bedrock = incoming;
            if (cache != null) cache.saveBedrockTexture(session.xuid(), skinHash(session),
                    new JavaSkinLoader.SignedTexture(incoming.value(), incoming.signature()));
        }
        if (appearance.usesJavaSkin(session)) {
            // Leave the official profile in place for ordinary Bedrock uploads;
            // allow our signed official-profile refresh after a menu opt-in.
            event.setCancelled(!java);
        } else if (java) {
            if (upload.bedrock == null) {
                event.setCancelled(true);
            } else {
                event.newSkin(upload.bedrock);
                event.setCancelled(false);
            }
        } else {
            event.setCancelled(false);
        }
    }

    void refresh(GeyserSession session, boolean notifyPending) {
        if (!LinkedJavaSkin.isLinked(session)) {
            return;
        }
        if (appearance.usesJavaSkin(session)) {
            // Initial linked login already carries official Java textures.
            if (!notifyPending) {
                return;
            }
            loader.signedTexture(session.javaUuid()).whenComplete((texture, error) -> session.executeInEventLoop(() -> {
                if (!current(session) || !appearance.usesJavaSkin(session)) {
                    return;
                }
                if (error != null) {
                    SERVER.warn("Could not refresh signed Java skin for " + session.javaUuid() + ": " + error.getMessage());
                    session.sendMessage("Java skin refresh failed; reconnect to retry.");
                    return;
                }
                send(session, texture.value(), texture.signature());
            }));
            return;
        }
        Upload upload = uploads.get(session.javaUuid());
        SkinApplyEvent.SkinData skin = upload != null && upload.connection == session ? upload.bedrock : null;
        if (skin == null) {
            var player = api.getPlayer(session.javaUuid());
            if (player != null) {
                SkinApplyEvent.SkinData candidate = player.getProperty(PropertyKey.SKIN_UPLOADED);
                if (candidate != null && !new JavaSkinLoader.SignedTexture(candidate.value(), candidate.signature()).belongsTo(session.javaUuid())) {
                    skin = candidate;
                }
            }
        }
        if (skin != null) {
            send(session, skin.value(), skin.signature());
        } else if (cache != null && cache.bedrockTexture(session.xuid(), skinHash(session)) != null) {
            var saved = cache.bedrockTexture(session.xuid(), skinHash(session));
            send(session, saved.value(), saved.signature());
        } else if (notifyPending) {
            boolean chinese = session.locale().toLowerCase().startsWith("zh");
            session.sendMessage(chinese ? "已切换基岩皮肤。Java 玩家显示将在 Floodgate 完成皮肤转换后更新。" :
                    "Bedrock skin selected. Java viewers will update when Floodgate finishes converting your skin.");
        }
    }

    private void send(GeyserSession session, String value, String signature) {
        if (current(session)) {
            PluginMessageUtils.sendMessage(session, "floodgate:skin", (value + '\0' + signature).getBytes(StandardCharsets.UTF_8));
        }
    }

    private String skinHash(GeyserSession session) {
        return PlayerSkinCache.hash(session.getClientData());
    }

    private boolean current(GeyserSession session) {
        return plugin.geyserApi.connectionByUuid(session.javaUuid()) == session;
    }

    void onDisconnect(GeyserConnection connection) {
        if (connection.javaUuid() != null) {
            uploads.computeIfPresent(connection.javaUuid(), (id, upload) -> upload.connection == connection ? null : upload);
        }
    }

    void close() {
        api.getEventBus().unsubscribe(subscription);
        uploads.clear();
    }

    private static final class Upload {
        final GeyserSession connection;
        volatile SkinApplyEvent.SkinData bedrock;

        Upload(GeyserSession connection) {
            this.connection = connection;
        }
    }
}
