package dev.letsgoaway.geyserextras.core.injectors.java;

import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.java.JavaLoginFinishedTranslator;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginFinishedPacket;

import static dev.letsgoaway.geyserextras.core.GeyserExtras.GE;

public final class JavaLoginFinishedInjector extends JavaLoginFinishedTranslator {
    @Override
    public void translate(GeyserSession session, ClientboundLoginFinishedPacket packet) {
        super.translate(session, packet);
        GE.getLinkedJavaSkin().prepare(session);
    }
}
