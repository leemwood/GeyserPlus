package dev.letsgoaway.geyserextras.core.injectors.java;

import dev.letsgoaway.geyserextras.core.ExtrasPlayer;
import dev.letsgoaway.geyserextras.core.parity.java.blockdisplay.BlockDisplayEntity;
import org.geysermc.geyser.entity.GeyserEntityType;
import org.geysermc.geyser.entity.spawn.EntitySpawnContext;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.translator.protocol.java.entity.JavaAddEntityTranslator;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;

import static dev.letsgoaway.geyserextras.core.GeyserExtras.GE;

@Translator(packet = ClientboundAddEntityPacket.class)
public class JavaAddEntityInjector extends JavaAddEntityTranslator {
    @Override
    public void translate(GeyserSession session, ClientboundAddEntityPacket packet) {
        GeyserEntityType type = GeyserEntityType.of(packet.getType());
        if (type.is(EntityType.BLOCK_DISPLAY) && GE.getConfig().isEnableBlockDisplayWorkaround()) {
            EntitySpawnContext context = EntitySpawnContext.fromPacket(session, BlockDisplayEntity.buildEntityDef(), packet);
            if (!context.callServerSpawnEvent()) {
                return;
            }

            BlockDisplayEntity entity = new BlockDisplayEntity(context);
            if (context.consumers() != null) {
                context.consumers().forEach(consumer -> consumer.accept(entity));
            }
            session.getEntityCache().spawnEntity(entity);
            return;
        }

        super.translate(session, packet);
        if (type.is(EntityType.PLAYER)) {
            GE.getLinkedJavaSkin().applyCached(session, packet.getUuid());
            ExtrasPlayer player = ExtrasPlayer.get(session);
            player.getPlayerDimensionsMap().put(packet.getEntityId(), session.getDimensionType());
        }
    }
}
