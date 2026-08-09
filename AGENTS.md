# GeyserExtras 项目记忆

## 项目概述

GeyserExtras 是为 GeyserMC（Java↔基岩互通代理）提供附加功能的插件/扩展，多模块 Gradle（Kotlin DSL）项目：

- `core/` — 主要逻辑，产物 `GeyserExtras.jar`
- `extension/` — Geyser Extension 形态（`extension.yml`，`api: 2.9.0`）
- `spigot/` / `velocity/` / `bungee/` — 各平台插件形态
- 本仓库是 `leemwood/GeyserExtras`，上游 `GeyserExtras/GeyserExtras`（origin/upstream 均已配置）

## 构建与环境

- 构建命令：`./gradlew build`（子模块各自 `shadowJar`，`jar` 依赖 `shadowJar`）
- 当前 Java toolchain 17（`build.gradle.kts:77`）；插件：lombok 8.6、com.gradleup.shadow 8.3.0
- 关键仓库：`https://repo.opencollab.dev/main/`（Geyser/Floodgate 构件；旧 `repo.geysermc.org` 已废弃，本机网络下不可达）

## 依赖坐标（当前）

- `org.geysermc.geyser:core:2.9.1-SNAPSHOT`（core/extension），spigot/velocity/bungee 还是 `2.9.0-SNAPSHOT`（不一致，升级时统一）
- `org.geysermc.floodgate:core:2.2.5-SNAPSHOT`（仍为最新主线，无需改坐标，刷新快照即可拿 build 18）
- `org.spongepowered:configurate-yaml:4.2.0-GeyserMC-SNAPSHOT`、jackson 2.17.0

## Geyser 生态现状（2026-08 调研）

- 最新 Geyser：**2.11.1**（build #1210，2026-08-07），maven 快照 `org.geysermc.geyser:core:2.11.1-SNAPSHOT`
- Geyser 无 GitHub release/tag，发布渠道为 download API：`https://download.geysermc.org/v2/projects/geyser/versions`
- Minecraft 已进入年份制版本号：支持 Bedrock 26.0–26.40 / Java 26.2；MCProtocolLib 26.2、Cloudburst Protocol 3.0.0.Beta13
- **Geyser 2.10.0 起本体编译目标为 Java 21**（CI 用 Java 25）——升级到 2.10+ 必须同步升 Java toolchain
- 扩展应使用的依赖坐标是 `org.geysermc.geyser:api`（不是 `geyser.api:extensions`）；官方模板 GeyserExtensionTemplate 已用 `api:2.11.0-SNAPSHOT` + Java 21
- `extension.yml` 的 `api` 字段校验：major 不高于运行时即兼容（`api: 2.9.0` 在 2.11.x 仍可加载，但应同步改为 `2.11.0`）
- Floodgate 3.0（`development-3.0.0-SNAPSHOT`）开发线存在但不建议跟进；2.x 中废弃 API（旧 cumulus `form.Form` 等）仍可用

## 上游动态

- 上游 master 最新即本地 HEAD `d49ad51`（2026-03-14，Merge PR #71），本地无落后
- 唯一新版适配工作：**PR #75 "26.1 Port"**（mas6y6，未合并）：目标 Geyser core 2.10.0-SNAPSHOT / MC 26.1.2 / Java 25，41 文件 +350/−608，混有 gradle 大重构与 neoforge 骨架，建议只提取 `core/` 下 API 适配部分（约 ±30 行）参考，不要整体合并
- 兼容性问题 issue：#69（NoSuchMethodError）、#72、#73（请求更新 26.1.2）、#47

## 代码结构风险图（升级工作量评估）

- **最大改动面 `core/injectors/`（约 20 个类）**：全部 `extends` Geyser 内部 `PacketTranslator<T>` + `@Translator` 注解 + `Registries` 注册，translator 包/签名变动即 break
- **次风险 `core/parity/java/blockdisplay/`（3 个类）**：深度依赖内部实体/物品/方块模型（`EntityDefinition`、`Items`、`BlockState`）
- `GeyserSession` 使用面广（20+ 文件）但多为简单调用，机械适配
- 公开 API 层（`api.*`、事件、pack、camera、floodgate）基本兼容，改动小
- 无 `com.nukkitx.*` 残留；反射仅用于 `IsAvailable`（adventure 包名探测）和 `JavaTabListInjector`（防御性探测）

## 已确认的破坏性变更（2.9.1 → 2.11.1，必须修改）

1. `core/menus/settings/sections/VideoSection.java:39` — `gameplay().showCooldown()` 已改名 `cooldownType()`，返回 `CooldownUtils.CooldownType`（枚举现仅 `CROSSHAIR/HOTBAR/DISABLED`，`TITLE` 已移除——commit `274d4bb` 已处理枚举值，但方法名还要改）
2. `core/injectors/bedrock/BedrockEmoteListInjector.java` — 上游 `BedrockEmoteListTranslator` 整个被删、`GeyserSession.refreshEmotes` 不存在；需重写为直接继承 `PacketTranslator<EmoteListPacket>`（不调用 super）或换方案
3. `core/preferences/bindings/Action.java:53` — MCPL `ClientCommand.STATS` 改名 `REQUEST_STATS`

注意但已验证兼容：`JavaDimension` record 删除 `worldCoordinateScale` 字段（本仓库只用访问器，源码兼容）；`GeyserConnection.entityByJavaId` 删除（本仓库用内部 `PlayerEntity`，不受影响）；表单 cumulus 1.1.2 API 不变；`BossBar` 7 参构造、`MessageTranslator`、`InventoryUtils.openInventory`、`EntityCache.addBossBar` 等均不变；全部 24 个用到的 MCPL packet/data 类在 26.2 快照中确认存在。

## 2.11.1 实体系统新 API 映射（blockdisplay 适配实测）

Geyser 2.11.x 实体系统大重构（`EntityDefinition` 已删除），新旧对应：

- `EntityDefinition.builder(factory)` → `EntityTypeBase.baseBuilder(Entity.class)`（无需 factory）
- `EntityDefinition.inherited(factory, parent)` → `VanillaEntityType.inherited(factory, parent)`（可 `.type(EntityType.X)` 链式）；中间层用 `EntityTypeBase.baseInherited(Class, parent)`
- `.identifier("minecraft:fox")` → `.bedrockDefinition(BedrockEntityDefinition.getVanilla(Identifier.of("minecraft:fox")))`——注意 `build()` 注册时若 bedrockDefinition 为 null 会按 identifier 注册 BedrockEntityDefinition，vanilla 标识符会抛 "Duplicate bedrock identifier"
- `VanillaEntityType.Builder.build()` 会 `putIfAbsent` 注册进 `Registries.JAVA_ENTITY_TYPES`——`buildEntityDef()` 仅靠调用即完成注册（InitializeLogger 里调用不接收返回值是有意为之）
- 实体构造器：旧 10 参 `(session, entityId, geyserId, uuid, definition, position, motion, yaw, pitch, headYaw)` → 新 `Entity(EntitySpawnContext)`；`EntityFactory.create(EntitySpawnContext)`
- `Entity.dirtyMetadata`（EntityDataMap）→ `Entity.metadata`（`GeyserEntityDataManager`，`put(EntityDataType<T>, T)` 泛型化）
- `Entity::setDisplayName/setDisplayNameVisible` → `setCustomName/setCustomNameVisible`
- `BlockItem.byBlock(block)` → `block.asItem()`
- `Item.newItemStack(int, DataComponents)` → `newItemStack(GeyserSession, int, DataComponents)`
- `addTranslator(null)` 跳过元数据索引的用法在新 API 保留（上游 VanillaEntities 同款）；`MetadataTypes` 各常量（BYTE/INT/BOOLEAN/POSE/VECTOR3/QUATERNION/BLOCK_STATE/OPTIONAL_COMPONENT）在 MCPL 26.2 均在
- `Entity.getGeyserId()` → `geyserId()`（fluent 命名）
- `BedrockClientData.getSkinData()/getGeometryName()/getGeometryData()` 返回类型 String → byte[]（去掉 `.getBytes(UTF_8)` 再 Base64 decode）；`getPlayFabId()` 删除，改用 `GeyserSession.playFabId()`
- 新版 `Entity` 新增 `getScale()`（返回 float）——`BlockDisplayBaseEntity` 原有的 `Vector3f getScale()` 与之冲突，已改名 `getDisplayScale()`

## 升级计划（目标：Geyser 2.11.1 / MC 26.x）

**状态（2026-08-09）：第 1–2 步已完成，`./gradlew build` 全模块编译通过**（core/extension/spigot/velocity/bungee 均出包）。修改共 17 文件、+75/−76，参考 PR #75 但以实测新 jar 签名为准（PR 基于 2.10.0，blockdisplay 部分在 2.11.1 还需按上文"实体系统新 API 映射"再改一轮）。

1. ~~**构建层**~~（已完成）：toolchain 21；坐标统一 `2.11.1-SNAPSHOT`；`extension.yml` api `2.11.0`；新增 `repo.opencollab.dev/maven-snapshots/` 仓库
2. ~~**代码层**~~（已完成）：3 处计划内破坏点 + 编译暴露的 7 处残留（geyserId()、SkinSaver byte[]、playFabId()、getDisplayScale() 改名）
3. **功能去重评估**（未做）：Geyser 2.9.4+ 内置 Integrated Pack 1.1.0 已提供原生冷却显示与自定义容器尺寸，评估 GeyserExtras 对应功能是否保留
4. **运行时验证**（已完成）：2026-08-09 四平台启动冒烟测试全部通过（`test-servers/` 下，Standalone 扩展 / Paper 26.2 / Velocity 4.0.0 / BungeeCord，Geyser 均为 2.11.1-b1210，加载零 ERROR）；同日 Paper 端（+ floodgate 2.2.5-b138、BellCommand、GeyserMenu，`auth-type: floodgate`）真实基岩客户端进服**功能验证通过**
5. **CI**：如有 GitHub Actions，JDK 同步升到 21+

## 运行时环境要点（2026-08-09 冒烟测试实测）

- **运行 MC 26.x 服务端需 Java 25**（Paper 26.2 拒绝在 Java 21 上启动）；编译插件用 21 即可
- **Velocity 必须 4.0.0+**：3.5.1 的 adventure 过旧，Geyser 2.11.1 报 `NoSuchMethodError: GsonComponentSerializer.toBuilder()`；Velocity 4.0.0 本身也需 Java 25
- PaperMC 旧下载 API `api.papermc.io/v2` 已停服（sunset），改用 `fill.papermc.io/v3`
- Termux 环境固有 WARN（与插件无关）：jline/netty 缺 glibc 库回退、无网卡 MAC 地址
- Standalone 下未装 Floodgate 时 Geyser 会提示扩展从外部加载 `SimpleFloodgateApi` 类，属已知设计
- 冒烟测试目录 `test-servers/`（约 450MB，含各端日志），不需要时可整个删除
- frpc 二进制由 `~/frp` 源码 `make frpc` 编译（`~/frp/bin/frpc`）；隧道配置 `test-servers/frpc.toml`（frps: mc.lemwood.cn:7000，token 认证，UDP 19133 → 本地 Geyser 19133）
- 在线测试环境：Paper 端以 nohup 常驻运行时，基岩客户端可通过 `mc.lemwood.cn:19133` 进服
