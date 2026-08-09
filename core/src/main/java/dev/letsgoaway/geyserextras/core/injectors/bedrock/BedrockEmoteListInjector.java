package dev.letsgoaway.geyserextras.core.injectors.bedrock;

import dev.letsgoaway.geyserextras.core.ExtrasPlayer;
import org.cloudburstmc.protocol.bedrock.packet.EmoteListPacket;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;

@Translator(packet = EmoteListPacket.class)
public class BedrockEmoteListInjector extends PacketTranslator<EmoteListPacket> {
    @Override
    public void translate(GeyserSession session, EmoteListPacket packet) {
        ExtrasPlayer.get(session).getEmotesList().addAll(packet.getPieceIds());
    }
}
