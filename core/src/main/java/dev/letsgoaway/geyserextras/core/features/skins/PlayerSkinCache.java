package dev.letsgoaway.geyserextras.core.features.skins;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import org.geysermc.geyser.api.skin.*;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.geysermc.geyser.session.auth.BedrockClientData;
import java.util.concurrent.*;

/** Bounded memory cache with atomic per-XUID disk snapshots; no login tokens. */
public final class PlayerSkinCache {
    private static final Gson GSON = new Gson();
    private static final long TTL = TimeUnit.DAYS.toMillis(7);
    private final Path directory;
    private final Cache<String, Entry> memory = CacheBuilder.newBuilder().maximumSize(256)
            .expireAfterAccess(30, TimeUnit.MINUTES).build();
    private final ExecutorService io = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "GeyserPlus skin cache");
        thread.setDaemon(true);
        return thread;
    });

    public PlayerSkinCache(Path directory) {
        this.directory = directory;
        io.execute(() -> {
            try {
                if (!Files.isDirectory(directory)) return;
                try (var files = Files.list(directory)) {
                    var snapshots = files.filter(file -> file.getFileName().toString().matches("[0-9]{1,20}\\.json"))
                            .sorted(java.util.Comparator.comparingLong(file -> file.toFile().lastModified())).toList();
                    for (int i = 0; i < snapshots.size(); i++) {
                        Path file = snapshots.get(i);
                        if (i < snapshots.size() - 512 || System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis() >= TTL) {
                            Files.deleteIfExists(file);
                        }
                    }
                }
            } catch (IOException ignored) {}
        });
    }

    public CompletableFuture<Entry> load(String xuid) {
        Entry existing = memory.getIfPresent(xuid);
        if (existing != null) {
            return CompletableFuture.completedFuture(existing);
        }
        return CompletableFuture.supplyAsync(() -> {
            Entry cached = memory.getIfPresent(xuid);
            if (cached != null) return cached;
            Entry entry = new Entry();
            try {
                Path path = path(xuid);
                if (Files.isRegularFile(path) && Files.size(path) <= 2 * 1024 * 1024) {
                    Entry saved = GSON.fromJson(Files.readString(path), Entry.class);
                    if (saved != null && System.currentTimeMillis() - saved.updated < TTL) {
                        entry = saved;
                    }
                }
            } catch (IOException | RuntimeException ignored) {
                // Missing/corrupt/expired cache never prevents login.
            }
            memory.put(xuid, entry);
            return entry;
        }, io);
    }

    public SkinData javaSkin(String xuid, UUID uuid) {
        Entry entry = memory.getIfPresent(xuid);
        try {
            if (entry == null) return null;
            synchronized (entry) {
                return uuid.toString().equals(entry.javaUuid) && entry.javaSkin != null ? entry.javaSkin.decode() : null;
            }
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public JavaSkinLoader.SignedTexture bedrockTexture(String xuid, String hash) {
        Entry entry = memory.getIfPresent(xuid);
        if (entry == null) return null;
        synchronized (entry) {
            return hash.equals(entry.bedrockHash) ? entry.bedrockTexture : null;
        }
    }

    public void saveJava(String xuid, UUID uuid, SkinData skin) {
        update(xuid, entry -> {
            entry.javaUuid = uuid.toString();
            entry.javaSkin = Snapshot.encode(skin);
        });
    }

    public void saveBedrock(String xuid, String hash, SkinData skin) {
        update(xuid, entry -> {
            entry.bedrockSnapshotHash = hash;
            entry.bedrockSkin = Snapshot.encode(skin);
        });
    }

    public SkinData bedrockSkin(String xuid, String hash) {
        Entry entry = memory.getIfPresent(xuid);
        if (entry == null) return null;
        synchronized (entry) {
            try {
                return hash.equals(entry.bedrockSnapshotHash) && entry.bedrockSkin != null ? entry.bedrockSkin.decode() : null;
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }

    public void saveBedrockTexture(String xuid, String hash, JavaSkinLoader.SignedTexture texture) {
        update(xuid, entry -> {
            entry.bedrockHash = hash;
            entry.bedrockTexture = texture;
        });
    }

    private void update(String xuid, java.util.function.Consumer<Entry> change) {
        io.execute(() -> {
            Entry entry = memory.getIfPresent(xuid);
            if (entry == null) entry = new Entry();
            synchronized (entry) {
                change.accept(entry);
                entry.updated = System.currentTimeMillis();
            }
            memory.put(xuid, entry);
            try {
                Files.createDirectories(directory);
                Path target = path(xuid);
                Path temporary = Files.createTempFile(directory, xuid + "-", ".tmp");
                try {
                    Files.writeString(temporary, GSON.toJson(entry));
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(temporary);
                }
            } catch (IOException | RuntimeException ignored) {
                // A read-only disk should not stop in-memory skin selection.
            }
        });
    }

    private Path path(String xuid) {
        if (!xuid.matches("[0-9]{1,20}")) throw new IllegalArgumentException("Invalid XUID cache key");
        return directory.resolve(xuid + ".json");
    }

    public static String hash(byte[] rgba) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rgba));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String hash(BedrockClientData client) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(client.getSkinData());
            if (client.getArmSize() != null) digest.update(client.getArmSize().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (client.getGeometryName() != null) digest.update(client.getGeometryName());
            if (client.getGeometryData() != null) digest.update(client.getGeometryData());
            if (client.getCapeId() != null) digest.update(client.getCapeId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (client.getCapeData() != null) digest.update(client.getCapeData());
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public void close() {
        io.shutdown();
    }

    CompletableFuture<Void> flush() {
        return CompletableFuture.runAsync(() -> {}, io);
    }

    public static final class Entry {
        volatile long updated;
        volatile String javaUuid;
        volatile Snapshot javaSkin;
        volatile String bedrockHash;
        volatile JavaSkinLoader.SignedTexture bedrockTexture;
        volatile String bedrockSnapshotHash;
        volatile Snapshot bedrockSkin;
    }

    private record Snapshot(String skinId, String pixels, String capeUrl, String capeId, String capePixels,
                            String geometryName, String geometryData) {
        static Snapshot encode(SkinData skin) {
            return new Snapshot(skin.skin().textureUrl(), Base64.getEncoder().encodeToString(skin.skin().skinData()),
                    skin.cape().textureUrl(), skin.cape().capeId(), Base64.getEncoder().encodeToString(skin.cape().capeData()),
                    skin.geometry().geometryName(), skin.geometry().geometryData());
        }

        SkinData decode() {
            return new SkinData(new Skin(skinId, Base64.getDecoder().decode(pixels)),
                    new Cape(capeUrl, capeId, Base64.getDecoder().decode(capePixels), false),
                    new SkinGeometry(geometryName, geometryData));
        }
    }
}
