# GeyserPlus

A plugin which attempts to unify features for Bedrock Edition players on Java Edition servers with [GeyserMC](https://geysermc.org), as well as other handy features for Bedrock players.

> **GeyserPlus** is a community continuation of [GeyserExtras](https://github.com/GeyserExtras/GeyserExtras). The upstream repository was archived by its author on 2026-06-19 — this fork keeps the plugin maintained and up to date with recent Geyser releases (currently **Geyser 2.11.1 / Minecraft 26.x**).

[![GitHub release](https://img.shields.io/github/v/release/leemwood/GeyserPlus?logo=github)](https://github.com/leemwood/GeyserPlus/releases)
[![Modrinth](https://img.shields.io/modrinth/v/geyserplus?logo=modrinth)](https://modrinth.com/plugin/geyserplus)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**Supported platforms:** Spigot · Paper · Purpur · BungeeCord · Waterfall · Velocity · Geyser Standalone (extension)

## Download & Install

| File | Where it goes |
|---|---|
| `GeyserPlus-Spigot.jar` | `plugins/` on Spigot / Paper / Purpur |
| `GeyserPlus-Velocity.jar` | `plugins/` on Velocity |
| `GeyserPlus-BungeeCord.jar` | `plugins/` on BungeeCord / Waterfall |
| `GeyserPlus-Extension.jar` | `extensions/` next to Geyser Standalone |

Get the latest release on [GitHub Releases](https://github.com/leemwood/GeyserPlus/releases) or [Modrinth](https://modrinth.com/plugin/geyserplus).

### Requirements

- **Geyser 2.11.1** on the same server/proxy
- Java **21+** (note: Minecraft 26.x servers themselves require Java 25)
- Velocity users: **Velocity 4.0.0+** (older Velocity ships an incompatible adventure version)
- [Floodgate](https://geysermc.org/download#floodgate) recommended — menus and per-player settings are only available to Floodgate players

### Upgrading from GeyserExtras

GeyserPlus keeps the original plugin id (`geyserextras`), commands (`/geyserextras`, `/ge`) and extension id, so existing setups keep working. Note that on Bukkit platforms the config folder follows the plugin name, so it becomes `plugins/GeyserPlus/` — copy your old `plugins/GeyserExtras/` contents over if you want to keep your settings.

## Features

### Combat
- ~~Changes the Attack Cooldown to look like Java Editions~~ This feature has been added into the GeyserIntegratedPack
- Adds the Java Edition combat sounds

### Parity
- Sends emote chat to Java players (can be muted by `/muteemotechat`)
- Ports some Java Edition only menus to Bedrock Forms
- Experimental Block Display Entity support using FMBE

### Utility
- Allows rebinding of certain actions to Java Actions (e.g. swap offhand)
- Allows players to load resource packs from the `GeyserExtras/optionalpacks` folder as they please
- Allows saving player skin, geometry and animation data to `GeyserExtras/skins`
- Accessible menu by double tapping Inventory or typing `/ge`

## Building

```bash
./gradlew build
```

Requires a JDK 21 toolchain. Artifacts land in each module's `build/libs/` (`GeyserPlus*.jar`).

## Community

- QQ group (964498276): [join link](https://qun.qq.com/universal-share/share?ac=1&authKey=DhxH11nrAz5U%2FQPPj9FstiqHebFfwmRefoLi5GIzlU43NhxE%2BvBuFisM3OU%2B03ZO&busi_data=eyJncm91cENvZGUiOiI5NjQ0OTgyNzYiLCJ0b2tlbiI6IlNFU1ZXbExBZXp0b3dSbWZNTDhDK3pBNkdHMlNkMHYxOWdMbmFHQm9OVENRaFlTYThwN0ZHNm1YOUtzeDV4K2ciLCJ1aW4iOiIzNDM2NDY0MTgxIn0%3D&data=rWc0I9kOJlc5G-zkAOo4JsrMCQKsWUh9oM77ZfJMu5cJbnn8QUQ0xI3nyHfkO4is8IOy3mQbV6feCVowE0AQRQ&svctype=4&tempid=h5_group_info)
- Issues: [GitHub Issues](https://github.com/leemwood/GeyserPlus/issues)

## Credits

- Original plugin by [LetsGoAway](https://github.com/GeyserExtras) (MIT License)
- Continuation maintained by [leemwood](https://github.com/leemwood)
- Wiki (upstream, may be outdated): https://geyserextras.letsgoaway.dev/

## Other parity plugins that work great with GeyserPlus

### Legacy Console features
- [MapXYZ](https://modrinth.com/plugin/mapxyz) - Shows coordinates on maps instead of in the debug menu or on the hud if you are on bedrock.

### Bedrock features
- [Bedrock Armorstands](https://modrinth.com/plugin/armorstand) - Adds arms to armor stands for both Bedrock and Java

### Java features
- [Geyser Recipe Fix](https://modrinth.com/plugin/geyser-recipe-fix) - Fixes smithing table recipes to work properly on Bedrock
