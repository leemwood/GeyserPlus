# GeyserExtras 项目记忆

## 项目概述

**项目已改名 GeyserPlus（2026-08-09，v2.1.0 起）**：上游归档后本 fork 独立维护。展示名/jar 名/仓库名改为 GeyserPlus（仓库 `leemwood/GeyserPlus`），但 **plugin id 仍为 `geyserextras`**（命令 `/geyserextras`、配置目录、扩展 id 均不变，老用户兼容）。版本检查（`PluginVersion`）指向未来的 Modrinth 项目 slug `geyserplus`（尚不存在时检查静默跳过）。

GeyserPlus（原 GeyserExtras）是为 GeyserMC（Java↔基岩互通代理）提供附加功能的插件/扩展，多模块 Gradle（Kotlin DSL）项目：

- `core/` — 主要逻辑，产物 `GeyserPlus.jar`
- `extension/` — Geyser Extension 形态（`extension.yml`，`api: 2.12.0`）
- `spigot/` / `velocity/` / `bungee/` — 各平台插件形态
- 本仓库是 `leemwood/GeyserPlus`（原 `leemwood/GeyserExtras`），上游 `GeyserExtras/GeyserExtras` 已归档

## 构建与环境

- 构建命令：`./gradlew build`（子模块各自 `shadowJar`，`jar` 依赖 `shadowJar`）
- 当前 Java toolchain 21；插件：lombok 8.6、com.gradleup.shadow 8.3.0
- 关键仓库：`https://repo.opencollab.dev/main/`（Geyser/Floodgate 构件；旧 `repo.geysermc.org` 已废弃，本机网络下不可达）

## 依赖坐标（当前）

- `org.geysermc.geyser:core:2.12.1-SNAPSHOT`（全部模块统一读取 `gradle.properties` 的 `geyser_version`）
- `org.geysermc.floodgate:core:2.2.5-SNAPSHOT`（仍为最新主线，无需改坐标，刷新快照即可拿 build 18）
- `org.spongepowered:configurate-yaml:4.2.0-GeyserMC-SNAPSHOT`、jackson 2.17.0

## 最新适配（2026-11-25，v2.2.0）

- 目标 Geyser **2.12.1-b1308**（2026-11-23，`d2cf200`）；上游支持 Java **26.3**、Bedrock **26.40–26.66**（具体补丁版本以 Geyser 为准）。Java 编译目标仍为 21。
- 官方下载 API：版本列表取 `https://download.geysermc.org/v2/projects/geyser` 的 `versions`；旧 `/versions` 路径返回 404。最新构建取 `/v2/projects/geyser/versions/latest/builds/latest`。
- 2.12.x 的内建 Java 实体注册表在插件初始化前已冻结。方块展示定义必须 `.build(false)`，由 `JavaAddEntityInjector` 在开关开启时直接使用定义生成实体；保留生成事件、取消和生成前回调，不再向冻结的注册表写入。
- MCProtocolLib 的生成包实体类型改为数字 id，使用 `GeyserEntityType.of(packet.getType()).is(EntityType.X)` 判断。
- `Item.javaIdentifier()` 返回 `Identifier`，名称匹配使用 `.path()`，方块/物品标识符列表直接比较 `Identifier`。
- `BedrockClientData` 的皮肤、披风、geometry 字节已完成 Base64 解码，不能再次解码；动画字段仍为 Base64 字符串。JSON 文件改用 `Files.writeString`，避免内部 `FileUtils.writeFile(char[])` 丢失非 ASCII 字符。
- 验证：JDK 21 下全模块 `gradlew.bat build` 通过，皮肤保存回归测试通过（PNG 像素、披风、Unicode geometry、动画）；Standalone 2.12.1 与 Paper 26.3-b262 / Java 25 启动冒烟测试通过，均开启方块展示兼容开关，插件初始化零 ERROR。
- 本轮未做真实客户端进服或 Velocity/Bungee 运行测试；代理模块已编译打包。验证日志放在忽略的 `build/verification/geyser-2.12.1/`，临时服务端位于系统临时目录，未写入仓库。
- 本机 Java 网络需代理和 Windows 根证书信任；验证构建使用临时 Gradle init script 调整仓库访问，未修改全局配置。

## 玩家皮肤来源与绑定 Java 外观（2026-11-27）

- `/ge → 设置 → 皮肤与披风` 新增“同步正版账号皮肤和披风”个人开关，已绑定玩家可选 Java 正版皮肤/披风或进服时基岩外观。未绑定玩家保留基岩外观。`use-linked-java-skin`（配置版本 6）现在仅作为未保存选择时的默认值，个人 `Boolean useLinkedJavaSkin` 为 null 时继承默认、false 明确关闭、true 明确开启，按 XUID 保存在 `preferences/<xuid>.json`，提交后更新。
- 披风来源已改为独立个人字段 `Boolean useLinkedJavaCape`，菜单提供“绑定 Java 正版账号 / 基岩版”两项；旧文件 null 继承旧皮肤来源以保持兼容，提交新菜单后两个来源分别保存。基岩观看者支持四种皮肤/披风组合，保留原几何和 Persona 元数据，所选来源无披风时显示空披风。Java 原版只接受整套签名纹理档案，不能任意混搭两套账号来源，菜单有明确说明。
- 基岩客户端可能仅提交 `CapeId`、`CapeData` 与宽高均为空。新增 `BedrockCapeResolver` 按 `capes/bedrock-mappings.json` 的精确 UUID 映射官方 `textures.minecraft.net` 纹理（21 项，不依赖可选 PacketEvents）；只有原始披风像素缺失才兜底，未知/空 ID 不猜测、不套用当前 Java 账号披风。你的实际 `b714eea5-bc07-f508-3b67-91669e55696a` 已从本地 `personaCatalogItemCache.json` 确认为 Aurora Cape，Marketplace `f3760a5d-0cac-4839-8dbe-a6f47442b840`；官方纹理 `7c418dfbd37412a55e9f1425c9807a591ad10cf71b1edb576a587be9114c277f`（64×32，https://minecraft.wiki/w/Aurora_Cape 交叉核验）。
- 映射纹理异步下载并共享有界内存缓存，PNG 保存在 `cache/cape-textures/<texture-hash>.png`，并更新玩家快照。映射完成检查当前会话后重新按个人披风来源组合，不会覆盖改回 Java 披风的选择。完整 SerializedSkin 的 capeId/贴图与所选源逐项比较（不能只比较 Java/基岩布尔），变更后复合 fullSkinId 失效旧客户端缓存；统一延迟到原生 100ms 更新之后发送，保留自定义 geometry/Persona 元数据。 10:50:32 部署后，用户确认极光披风显示并提供截图。补充核验：这次重连客户端补交了原始 8192 字节披风 RGBA，因此实际走原贴图优先分支；映射兜底通过测试，本次连接未触发下载，不可把显示成功误记为实际映射日志。
- 显示/接受 Java 外观选择之前，先验证完成的 Floodgate v4 身份；同进程 Floodgate API 可用时进一步核对 `isLinked()`、linkedPlayer Java UUID、正确 UUID 和 XUID。远程/独立扩展没有本地账号记录时以完成的 Floodgate 握手身份回退，不能依据未验证用户名开放选择。
- `LinkedJavaSkin` 保存每个会话的基岩/Java 外观，处理本人、其他在线基岩玩家、新观看者、实体生成、玩家列表更新及 `SessionSkinApplyEvent`。应用前重读选择，下载完成检查会话与递增 revision，避免快速切换或重连后旧请求覆盖新选择。JavaSkinLoader 有 15 分钟有界下载缓存；额外缓存 `?unsigned=false` 官方签名属性用于更新 Java 服务端外观。
- Geyser 2.12.1 对已绑定账号跳过 `handleBedrockSkin`；仅停用 Java 替换无法恢复基岩皮肤。`BedrockSkinLoader` 直接读取已解码 RGBA、UTF-8 geometry 和披风，保留 64/128 皮肤（Persona 可至 256）；`SessionLoginEvent` 在登录数据被清空前捕获 SerializedSkin，保留客户端提交的 AnimatedImageData、SkinAnimationData、PersonaPieces、PieceTintColors 等。**`BedrockClientData.originalString` 实际为已验证 JWT，需先 Base64URL 解码第二段，不能当普通 JSON。任何解析异常不得输出原始数据或异常原文，只记录异常类型。**
- 完整基岩外观通过原版 PlayerListUtils 更新本人/其他观看者，并延迟至 Geyser 自带 100ms 皮肤发送之后；普通 SkinData 事件同步贴图、披风、几何。更衣室换皮仍需重连，Geyser 本身丢弃客户端 PlayerSkinPacket。可提交的自定义模型、动态皮肤和装扮组件尽量原样保留给基岩观看者，受客户端/市场资源兼容限制；Java 客户端不支持这些基岩独有渲染特性。
- `FloodgateSkinBridge` 订阅公开 SkinApplyEvent：默认 Floodgate 会取消绑定账号基岩皮肤上传应用，个人关闭 Java 同步时放行并保存已转换签名皮肤，开启时保留/恢复本人官方签名属性；通过标准 `floodgate:skin` 通道让 Floodgate 自己更新服务端档案并刷新观看者。未安装同进程 Floodgate 的扩展环境只更新基岩端；要让 Java 观看者按选择切换需本地 Floodgate 事件支持和可用转换服务。Spigot/Velocity/Bungee 添加可选 Floodgate 加载顺序依赖。
- 用户确认菜单提交后 Java/基岩两种外观切换均生效，但首次进服先显示史蒂夫。已新增 `JavaLoginFinishedInjector` 在 UUID 确定后、进入游戏前准备个人外观；首次玩家列表的 UPDATE_LISTED 直接使用所选 SerializedSkin，后续本人/实体生成延迟补发。正版纹理未下载完成时先保留真实基岩外观，避免使用默认史蒂夫。
- 新增 `PlayerSkinCache`：最多 256 个内存项、空闲 30 分钟淘汰；`cache/player-skins/<xuid>.json` 原子快照保存基岩/Java 贴图、披风、geometry 及已转换签名皮肤，服务器重启后可复用，后台刷新。磁盘缓存有效期 7 天，启动清理过期并保留至多 512 文件。缓存按 XUID、绑定 Java UUID、基岩 RGBA/手臂类型/geometry 哈希校验，换绑/换肤不复用旧数据；不存登录 JWT/证书链。当前会话完整动画/Persona 元数据仍从新登录捕获。
- 偏好保存改为单线程队列、提交时 JSON 快照和临时文件替换，避免原来的并发写和保存默认值时产生空 JSON；空文件加载回退默认，不再 NPE。支持旧文件缺少个人字段。
- 34 项测试及全模块清理构建通过，覆盖来源组合与绑定校验：选择覆盖默认、JSON/复制、原始字节/模型/动画/Persona/UTF-8、JWT 包装、四种披风组合、无披风来源、所有观看者、旧异步请求、断线生命周期、Floodgate 取消策略、版本比较等。用户确认菜单切换有效且两种基岩可见外观均能应用；JWT 元数据修复后已无捕获报错。缓存、首次进服修正与独立披风已于 10:38:50 部署；10:39 实际登录已生成玩家磁盘快照，Java/基岩快照均存在。Java 观察客户端已确认收到正版签名皮肤，但基岩转换后的 Java 外观仍依赖 Floodgate 转换服务，尚未获得实际新属性记录。
- 旧功能在 11-27 01:13 由真实账号 `lemwoods` 验证官方皮肤/披风可见。Geyser **2.12.1-b1312** 支持 Bedrock 正式版 **26.40–26.66**，不支持 `pre26.70.26`。绑定身份按完成 Floodgate 握手的 version-4 Java UUID 判断，`isLinked()` 仍为固定 false。
- 当前公网入口 `mc.lemwood.cn:19133`，frp 转发 RakNet UDP 19133、信令 TCP 19133、NetherNet UDP 19134；运行目录为 `%TEMP%/geyserplus-2.12.1-compat/`，Paper PID 和日志在 `paper/client-test.pid`、`paper/client-test.stdout.log`，命令队列为 `paper/console.commands`。`clean` 已删除旧 `build/verification/geyser-2.12.1/active-client-test.json`。配置含认证材料，仅留系统临时目录，不得输出/加入仓库。Java25 G1 曾原生崩溃，测试服务使用 `-XX:+UseParallelGC`。
- 2026-11-27 用户已要求删除实验性武器挥动动画，相关代码、配置、模型、资源包及专用测试/脚本/文档已撤销；保留基岩原版动画，不得恢复这项已取消工作。

## Geyser 生态现状（2026-08 调研）

- 最新 Geyser：**2.11.1**（build #1210，2026-08-07），maven 快照 `org.geysermc.geyser:core:2.11.1-SNAPSHOT`
- Geyser 无 GitHub release/tag，发布渠道为 download API：`https://download.geysermc.org/v2/projects/geyser/versions`
- Minecraft 已进入年份制版本号：支持 Bedrock 26.0–26.40 / Java 26.2；MCProtocolLib 26.2、Cloudburst Protocol 3.0.0.Beta13
- **Geyser 2.10.0 起本体编译目标为 Java 21**（CI 用 Java 25）——升级到 2.10+ 必须同步升 Java toolchain
- 扩展应使用的依赖坐标是 `org.geysermc.geyser:api`（不是 `geyser.api:extensions`）；官方模板 GeyserExtensionTemplate 已用 `api:2.11.0-SNAPSHOT` + Java 21
- `extension.yml` 的 `api` 字段校验：major 不高于运行时即兼容（`api: 2.9.0` 在 2.11.x 仍可加载，但应同步改为 `2.11.0`）
- Floodgate 3.0（`development-3.0.0-SNAPSHOT`）开发线存在但不建议跟进；2.x 中废弃 API（旧 cumulus `form.Form` 等）仍可用

## 上游动态

- **上游 GeyserExtras/GeyserExtras 已于 2026-06-19 被作者归档（read-only）**，无法开 PR、issue；README 无迁移说明。本 fork 实质上成为活跃维护线
- 上游 master 最终提交即 `d49ad51`（2026-03-14，Merge PR #71）
- PR #75 "26.1 Port"（mas6y6，目标 2.10.0/26.1.2）随归档永久未合并；其 blockdisplay 适配基于 2.10.0，2.11.1 需按本文件"实体系统新 API 映射"再改
- 兼容性问题 issue（#69、#72、#73、#47）已无法回复；本 fork 的 2.11.1 适配提交 `23a2999` 已覆盖这些问题

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

## 发布渠道（2026-08-09）

- GitHub Release：`leemwood/GeyserPlus`，tag `v2.1.0`，5 个 jar
- **Modrinth 项目 `geyserplus`**：项目 id `MMHzAVoW`，账号 ning-g-mo，v2.1.0 四平台版本已上传并提交审核（status: processing）。版本结构沿用上游惯例：同一 `version_number` 按 loader 分 4 个版本（paper/purpur/spigot、velocity、bungeecord/waterfall、geyser）——`PluginVersion` 的版本检查依赖这个结构
- 发布脚本在 `test-servers/modrinth_*.py`（token 已擦除，复用时从环境变量读）
- Modrinth PAT 由用户在会话中直接提供，**不要写入仓库/记忆文件**

## v2.2.0 发布流程（2026-11-27）

- GitHub Release 使用 `v2.2.0`，五个 JAR 与 `SHA256SUMS`；发布说明在 `CHANGELOG.md`。`build/releases/v2.2.0/` 为忽略的本地产物暂存目录。
- `.github/workflows/publish-modrinth.yml` 在 GitHub Release published 后下载并校验原始发布产物，再用 `.github/scripts/publish_modrinth.py` 同步四个平台。也可手动 dispatch 并指定 tag；相同版本只有 SHA512 一致才跳过，内容冲突即停止。
- 用户已在仓库 Actions secret 设置 `MODRINTH_TOKEN`，仅由 Actions 注入使用，不在本地获取或持久化令牌。Modrinth 项目 `MMHzAVoW` 目前已 approved。
- 四个平台版本共用 `version_number=2.2.0`、游戏标签 `26.3`；核心 JAR 仅在 GitHub 分发。最终 JAR 已通过 Paper 与 Standalone 启动检查，无 ERROR；Velocity/Bungee 本轮仅编译打包。
