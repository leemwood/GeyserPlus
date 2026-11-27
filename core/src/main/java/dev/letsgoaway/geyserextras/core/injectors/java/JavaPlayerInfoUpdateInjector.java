package dev.letsgoaway.geyserextras.core.injectors.java;

import dev.letsgoaway.geyserextras.core.ExtrasPlayer;
import dev.letsgoaway.geyserextras.core.injectors.GeyserHandler;
import org.geysermc.geyser.session.GeyserSession;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;
import org.geysermc.geyser.skin.SkinManager;
import org.geysermc.geyser.util.PlayerListUtils;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.translator.protocol.java.entity.player.JavaPlayerInfoUpdateTranslator;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntryAction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerInfoUpdatePacket;

import java.util.Set;
import java.util.ArrayList;
import java.util.EnumSet;

import static dev.letsgoaway.geyserextras.core.GeyserExtras.GE;

// this could probably be better but im lazy
// TODO figure out why this doesnt remove players properly
@Translator(packet = ClientboundPlayerInfoUpdatePacket.class)
public class JavaPlayerInfoUpdateInjector extends JavaPlayerInfoUpdateTranslator {
    @Override
    public void translate(GeyserSession session, ClientboundPlayerInfoUpdatePacket packet) {
        if (packet.getActions().contains(PlayerListEntryAction.UPDATE_LISTED)
                && java.util.Arrays.stream(packet.getEntries()).anyMatch(entry -> GE.getLinkedJavaSkin().serializedFor(entry.getProfileId()) != null)) {
            // Let Geyser create/update player entities first, but compose their
            // very first list entry with the selected appearance, not Steve.
            EnumSet<PlayerListEntryAction> remaining = EnumSet.copyOf(packet.getActions());
            remaining.remove(PlayerListEntryAction.UPDATE_LISTED);
            super.translate(session, packet.withActions(remaining));
            var added = new ArrayList<PlayerListPacket.Entry>();
            var removed = new ArrayList<PlayerListPacket.Entry>();
            for (PlayerListEntry entry : packet.getEntries()) {
                var entity = session.getEntityCache().getPlayerEntity(entry.getProfileId());
                if (entity == null) continue;
                if (entry.isListed() && !PlayerListUtils.shouldLimitPlayerListEntries(session)) {
                    var selected = GE.getLinkedJavaSkin().serializedFor(entry.getProfileId());
                    var listed = selected == null ? SkinManager.buildEntryFromCachedSkin(session, entity)
                            : PlayerListUtils.buildEntryManually(session, entry.getProfileId(), entity.getUsername(), entity.geyserId(), selected);
                    listed.setAction(PlayerListPacket.Action.ADD);
                    added.add(listed);
                } else if (!entry.isListed() && entity.isListed()) {
                    var listed = new PlayerListPacket.Entry(entity.getTabListUuid());
                    listed.setAction(PlayerListPacket.Action.REMOVE);
                    removed.add(listed);
                }
                entity.setListed(entry.isListed());
            }
            if (!added.isEmpty()) PlayerListUtils.batchSendPlayerList(session, added, PlayerListPacket.Action.ADD);
            if (!removed.isEmpty()) PlayerListUtils.batchSendPlayerList(session, removed, PlayerListPacket.Action.REMOVE);
        } else {
            super.translate(session, packet);
        }
        ExtrasPlayer player = ExtrasPlayer.get(session);
        Set<PlayerListEntryAction> actions = packet.getActions();
        /* Updaters */
        for (PlayerListEntry entry : packet.getEntries()) {
            GE.getLinkedJavaSkin().applyCached(session, entry.getProfileId());
            PlayerListEntry cached = getCached(player, entry);

            if (actions.contains(PlayerListEntryAction.UPDATE_LISTED)) {
                if (cached == null) {
                    // Add it because we havent cached it yet.
                    if (entry.isListed()) {
                        player.getTabListData().getPlayers().put(entry.getProfileId(), entry);
                    }
                } else {
                    // Edit the current listed status because the player is cached.
                    cached.setListed(entry.isListed());
                    if (entry.isListed()) {
                        player.getTabListData().getPlayers().put(entry.getProfileId(), cached);
                    } else {
                        player.getTabListData().getPlayers().remove(entry.getProfileId());
                    }
                    continue;
                }
            }

            if (cached != null) {
                if (actions.contains(PlayerListEntryAction.UPDATE_DISPLAY_NAME)) {
                    cached.setDisplayName(entry.getDisplayName());
                }
                if (actions.contains(PlayerListEntryAction.UPDATE_LATENCY)) {
                    cached.setLatency(entry.getLatency());
                }
                if (actions.contains(PlayerListEntryAction.UPDATE_GAME_MODE)) {
                    cached.setGameMode(entry.getGameMode());
                }
                player.getTabListData().getPlayers().put(entry.getProfileId(), cached);
            }
        }
    }

    private PlayerListEntry getCached(ExtrasPlayer player, PlayerListEntry entry) {
        return player.getTabListData().getPlayers().get(entry.getProfileId());
    }
}
