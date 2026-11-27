package dev.letsgoaway.geyserextras.core.menus.settings.sections;

import dev.letsgoaway.geyserextras.core.ExtrasPlayer;
import dev.letsgoaway.geyserextras.core.features.skins.LinkedJavaSkin;
import dev.letsgoaway.geyserextras.core.form.BedrockForm;
import dev.letsgoaway.geyserextras.core.form.elements.Label;
import dev.letsgoaway.geyserextras.core.form.elements.Dropdown;
import dev.letsgoaway.geyserextras.core.form.elements.Toggle;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.geyser.session.GeyserSession;

import java.util.List;

import static dev.letsgoaway.geyserextras.core.GeyserExtras.GE;

public final class SkinSection extends Section {
    private boolean chinese(ExtrasPlayer player) {
        return player.getSession().locale().toLowerCase(java.util.Locale.ROOT).startsWith("zh");
    }

    @Override
    public void build(BedrockForm menu, GeyserSession session, ExtrasPlayer player) {
        boolean zh = chinese(player);
        if (LinkedJavaSkin.isLinked(session)) {
            menu.add(new Label(zh ? "已确认绑定 Java 正版账号。\n皮肤和披风来源可以分别选择，提交后应用并保存。\n选择的账号没有装备披风时，不显示披风。" :
                    "Java account link confirmed.\nChoose skin and cape sources independently; submit to apply and save.\nA source without an equipped cape shows no cape."));
            boolean[] javaSkin = {GE.getLinkedJavaSkin().usesJavaSkin(session)};
            menu.add(new Toggle(zh ? "同步正版账号皮肤" : "Use linked Java skin", javaSkin[0], enabled -> javaSkin[0] = enabled));
            String javaOption = zh ? "绑定 Java 正版账号" : "Linked Java account";
            String bedrockOption = zh ? "基岩版" : "Bedrock Edition";
            menu.add(new Dropdown(zh ? "披风来源" : "Cape source", List.of(javaOption, bedrockOption),
                    GE.getLinkedJavaSkin().usesJavaCape(session) ? javaOption : bedrockOption, selected ->
                    GE.getLinkedJavaSkin().setSources(player, javaSkin[0], javaOption.equals(selected))));
            menu.add(new Label(zh ? "独立披风来源对基岩观看者生效。Java 原版客户端的披风随签名皮肤档案显示，不能任意混搭两套来源。" :
                    "Independent cape selection applies to Bedrock viewers. Vanilla Java uses the cape included in its signed skin profile; arbitrary cross-source combinations cannot be reproduced there."));
        } else {
            menu.add(new Label(zh ? "尚未绑定 Java 正版账号，当前使用基岩版皮肤和披风。\n通过 Geyser Live 完成账号绑定后，这里会开放正版皮肤和披风选项。" :
                    "No linked Java account was found; your Bedrock skin and cape are used.\nLink your account through Geyser Live to unlock Java appearance options."));
        }
        menu.add(new Label(zh ? "其他基岩玩家可看到基岩皮肤及兼容的自定义模型。\nJava 玩家通过 Floodgate 皮肤转换显示普通皮肤；Java 不支持基岩专属 3D 模型、装扮组件和动态效果。\n更衣室换肤后请重新进服。" :
                "Bedrock viewers receive your skin and compatible custom geometry.\nJava viewers receive Floodgate's converted skin; Bedrock-only 3D models, persona pieces and animations cannot be reproduced on Java.\nReconnect after changing your Bedrock dressing-room skin."));
    }

    @Override
    public List<String> getHeader(ExtrasPlayer player) {
        return List.of(chinese(player) ? "皮肤与披风" : "Skin and Cape", "");
    }

    @Override
    public FormImage getImage() {
        return FormImage.of(FormImage.Type.PATH, "textures/ui/dressing_room_customization.png");
    }
}
