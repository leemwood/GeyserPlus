# GeyserPlus

> **GeyserPlus** 是 [GeyserExtras](https://github.com/GeyserExtras/GeyserExtras) 的社区延续版。上游仓库已于 2026-06-19 归档，本 fork 继续维护并适配新版 Geyser（当前支持 **Geyser 2.11.1 / Minecraft 26.x**）。
>
> **GeyserPlus** is a community continuation of [GeyserExtras](https://github.com/GeyserExtras/GeyserExtras). The upstream repository was archived on 2026-06-19; this fork keeps it maintained and up to date with recent Geyser releases (currently **Geyser 2.11.1 / Minecraft 26.x**).

# GeyserExtras (upstream README)
A plugin which attempts to unify features for Bedrock Edition players on Java Edition Servers with GeyserMC, aswell as other handy features for Bedrock Players.

[![CodeFactor](https://www.codefactor.io/repository/github/geyserextras/geyserextras/badge/master)](https://www.codefactor.io/repository/github/geyserextras/geyserextras/overview/master)

[![generic](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/documentation/generic_vector.svg)](https://geyserextras.letsgoaway.dev)
[![discord-singular](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/social/discord-singular_vector.svg)](https://discord.gg/2FfuShKQgy)

[![modrinth](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/modrinth_vector.svg)](https://modrinth.com/plugin/geyserextras) 
[![hangar](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/hangar_vector.svg)](https://hangar.papermc.io/GeyserExtras/GeyserExtras)
[![github](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/github_vector.svg)](https://github.com/GeyserExtras/GeyserExtras/releases)

![spigot](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/supported/spigot_vector.svg)
![paper](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact-minimal/supported/paper_vector.svg)
![purpur](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact-minimal/supported/purpur_vector.svg)

![bungeecord](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/supported/bungeecord_vector.svg)
![waterfall](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact-minimal/supported/waterfall_vector.svg)

![velocity](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/supported/velocity_vector.svg)

**The extension version** of GeyserExtras for the `Geyser/extensions` folder is available at [https://github.com/GeyserExtras/GeyserExtras/releases](https://github.com/GeyserExtras/GeyserExtras/releases)

# Features
## Combat
- ~~Changes the Attack Cooldown to look like Java Editions~~ This feature has been added into the GeyserIntegratedPack
- Adds the Java Edition combat sounds
## Parity
- Sends emote chat to Java players (can be muted by `/muteemotechat`)
- Ports some Java Edition only menus to Bedrock Forms
- Experimental Block Display Entity support using FMBE
## Utility
- Allows rebinding of certain actions to Java Actions (e.g swap offhand)
- Allows players to load resource packs from the `GeyserExtras/optionalpacks` folder as they please
- Allows saving player skin, geometry and animation data to `GeyserExtras/skins`
- Accessible menu by double tapping Inventory or typing `/ge`

## Plans
[Trello](https://trello.com/b/9UHPTQST)

## Other parity Spigot plugins that work great with GeyserExtras
### Legacy Console features
- [MapXYZ](https://modrinth.com/plugin/mapxyz) - Shows coordinates on maps instead of in the debug menu or on the hud if you are on bedrock.
### Bedrock features
- [Bedrock Armorstands](https://modrinth.com/plugin/armorstand) - Adds arms to armor stands for both Bedrock and Java
### Java features
- [Geyser Recipe Fix](https://modrinth.com/plugin/geyser-recipe-fix) - Fixes smithing table recipes to work properly on Bedrock
