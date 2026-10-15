# 更新日志 (Changelog)

> 版本线说明：本仓库是**同一模组的高版本分支**（NeoForge `1.21.1`），编号 `0.1.x`；
> 下方 `1.x` 条目为该模组 Forge 分支（`1.20.1` → `26.2`）的历史日志，两条分支的编号各自独立、不连续。

## [0.16.3] - 2026-10-15

### ⚖️ 协议统一为 GPL-3.0-only

本仓库的 license 一直沿用脚手架默认的 **MIT**，而 1.20.1 主线是 **GPL-3.0-only** ——
同一模组的两条分支用了两套协议。按作者决定统一：

- `neoforge.mods.toml`：`license="MIT"` → `license="GPL-3.0-only"`
  （游戏内「模组列表」里显示的就是这一项）；
- `gradle.properties`：`mod_license=MIT` → `mod_license=GPL-3.0-only`（属性名与主线对齐）；
- 仓库根目录的 `LICENSE`：替换成 GPL-3.0 全文（与主线同一份文件），原来是 MIT 的短文本；
- `README.md`：元信息表里的「许可证」与文末的授权说明同步改成 GPL-3.0-only
  （全文扫过一遍，没有其它残留）。

版本号 `0.16.2 → 0.16.3`。

## [0.16.2] - 2026-10-15

### 📇 补全模组元信息：描述 / 作者 / 图标 / 可选依赖

`neoforge.mods.toml` 之前还是脚手架状态 —— `description='''Created with ModMind'''`、
没有作者与图标、依赖只声明了 neoforge / minecraft。这次按 1.20.1 主线的结构补齐：

- **描述**：换成真实的模组简介（112 条属性 / 四分类、自动发现其它模组的属性、
  13 项联动逐条列出且标明「缺该模组时自动失效」、面板快捷键、随身站点、多人同步、
  可选依赖的降级行为）；
- **作者**：`authors="佚名既无名"`（与 1.20.1 一致）；
- **图标**：从 1.20.1 复制 `logo.png` 到 jar 根目录，并用 `logoFile="logo.png"` 声明 ——
  之前游戏内「模组列表」里这一项是空白的；
- **issueTrackerURL**：指向同一个 GitHub 仓库；
- **可选依赖**：补两条 `type="optional"` + `side="CLIENT"`：
  - `cloth_config` —— 游戏内配置界面（没装时配置仍然生效，只是没有图形界面）；
  - `pinyin_search` —— 搜索框的拼音匹配（没装则退化为原文包含）。

  其余 11 项联动（Create / 卓越前线 / 拔刀剑 / Curios / Waystones / Goety / 口渴 / 体温 /
  Iron's / Ars / Botania / Apotheosis / JustEnoughCharacters）**刻意不声明**：
  「本模组无需任何前置」是明确承诺，而 `optional` 依赖在 NeoForge 上只影响模组列表的展示、
  不构成实际约束 —— 全列上去只会让依赖列表变长，还得跟着对方的版本号跑。
  它们是否生效由运行时反射决定（装了就生效，没装零开销）。
- **`pack.mcmeta` 的资源包格式**：`pack_format` 从 `15`（1.20.1 的值）改成 **`34`**（1.21.1）——
  之前游戏会在日志里报「资源包格式过旧」，语言文件虽然仍能被读到，但格式号不该留错。
- **待确认**：`license` 仍是脚手架留下的 `MIT`，而 1.20.1 主线写的是 `GPL-3.0-only` ——
  同一模组的两条分支协议不一致，需要作者确认该用哪一个。

版本号 `0.16.1 → 0.16.2`。

## [0.16.1] - 2026-10-15

### 🔧 用真实 jar 核对卓越前线 / 拔刀剑：修掉两处「静默失效」

拿到 `superbwarfare-0.8.9.2-mc1.21.1` 与 `SlashBladeResharped-2.1.9-1.21.1` 后，用 `javap`
把 0.16.0 里所有反射锚点与 Mixin 注入点逐项复核。**类路径全部对得上**，但挖出两处会静默失效的错：

- **拔刀剑：整套状态存取方式都变了**（0.16.0 的实现读不到任何数据）。
  1.21 的重铸版把刀状态搬进了**数据组件**，并给了干净的访问器：
  `BladeStateAccess.of(ItemStack)` → `Optional<ISlashBladeState>`。
  1.20.1 用的 `ItemSlashBlade.BLADESTATE` 能力字段与物品 NBT 里的 `"bladeState"` 子标签
  **都不存在了** —— 所以 0.16.0 里基于 `DataComponents.CUSTOM_DATA` 的读写是白写的。
  现在全部走 `BladeStateAccess.of()`：返回的实例（`ComponentBackedState`）**setter 直接写回组件**，
  「改完当场生效 + 自动同步 + 自动存档」一次到位，比 1.20.1 的
  「改内存态 → `serializeNBT()` → 塞回 NBT」还少一步。
  另：`getSpecialEffects()` 在 1.21 返回 `Collection<ResourceLocation>`（1.20.1 是 `ListTag`），
  而 setter 仍收 `ListTag` —— 读写两侧类型不对称是上游接口本身的样子。
- **卓越前线干扰弹：装填数是同步实体数据，不是普通 getter/setter**。
  1.21.1 的 `VehicleEntity` 没有 `getDecoyCount()` / `setDecoyCount(int)`（1.20.1 有），
  只有 `DECOY_COUNT` 这个 `EntityDataAccessor<Integer>`，只能走 `Entity#getEntityData()` 的
  `get` / `set`。容量仍旧取 `DefaultVehicleData#getDecoyMagazineSize()` ✓

**核对通过、无需改动的部分**（逐项 `javap` 确认）：

- 卓越前线的枪械那半全对：`GunData.DATA_CACHE`（`LoadingCache`）、`ammo` / `heat` / `overHeat`
  三个值对象、`hasInfiniteBackupAmmo` / `countBackupAmmo` / `consumeBackupAmmo` / `shakePlayers`、
  `shoot` 的三个重载、`GunProp.MAGAZINE`、`ShootParameters` 的 `data` / `shooter` / `ammoSupplier`、
  `GunItem.shoot / afterShoot(ShootParameters)`；
- **客户端注入点全部存在**，并用 `javap -c` 反汇编确认了视角写入的位置：
  `handleGunRecoil` 里有 `setYRot` / `setXRot`，`handleWeaponBreathSway` 里也各有一处 ——
  0.16.0 里「把 SRG 名换成 official 名」那个判断是对的，四处 `@Redirect` 都能命中；
- `handlePlayerCamera` 仍存在（private，参数 `ViewportEvent$ComputeCameraAngles`，由公开的
  `computeCameraAngles` 调用）—— 注入点没变，但回调参数已改成照抄真实签名；
- 载具那半：`getHealth` / `setHealth` / `getMaxHealth` / `getGunDataMap` / `computed` /
  `VehicleEnergyStorage#extractEnergy(int, boolean)` / `getVehicle()`（protected）全部存在；
- 拔刀剑的 `ISlashBladeState` 五个 setter 名与两个注册表 `REGISTRY`（原生 `Registry<T>`）全对。

版本号 `0.16.0 → 0.16.1`。**剩下唯一没核对的是 TaCZ**（按决定不做）。

## [0.16.0] - 2026-10-15

### 🔫 「兼容联动」批（四）：卓越前线四条 + 拔刀剑编辑 + 拼音双后端

内置属性数 `108 → 112` —— **与 1.20.1 主线的属性表持平**。

- **卓越前线（Superb Warfare）四条**：
  - `infinite_ammo`「无限子弹」（5 点）：备弹侧拦 `countBackupAmmo` / `consumeBackupAmmo`
    （后者是唯一的实扣入口），弹匣侧把弹匣写回 `GunProp.MAGAZINE` 算出的容量。三层收口
    （`GunData.shoot` 各入口的 RETURN → `GunItem.shoot / afterShoot` 的 RETURN → 每 5 tick 兜底），
    载具机炮与干扰弹一并无限；
  - `no_heat`「零热量」（3 点）：开火流程返回时清掉 `heat` / `overHeat`
    （同一 tick 内，HUD 看不到升温）；兜底负责「解锁之前就已经热了 / 已被锁死」的枪；
  - `no_recoil`「无后坐力」（3 点）：客户端只吞掉对 `LocalPlayer` 朝向的写入
    （后坐力与呼吸晃动共四处），方法本身照常跑 —— 状态量该衰减还是要衰减；
    载具开火震屏拦 `shakePlayers`；开火相机偏移每帧清零；
  - `sbw_infinite_health`「无限载具血量」（3 点）：`@ModifyVariable` 把 `setHealth` 的入参改成满血 ——
    它是载具血量的唯一写入口，且就在受伤结算的同一调用链里（每 tick 回血赶不上同 tick 内的血量归零）。
  - 新增 `compat/SuperbWarfareCompat` + 5 个 Mixin + 6 个编译存根；
    `InfiniteStatsMixinPlugin` 的 gating 表加上 `com.atsuishio.superbwarfare.`。
  - **1.21 适配要点**：`IEnergyStorage` 换 NeoForge 包；客户端 Mixin 的注入目标从 SRG 名
    （`m_146922_` / `m_146926_`）改成 **official 名**（`setYRot` / `setXRot`）——
    NeoForge 跑在 official 映射上，不再需要 refmap 转换（这条不改，无后坐力会静默失效）。
- **拔刀剑编辑**：新增 `compat/SlashBladeCompat` + `network/EditItemBladePacket` +
  `client/SlashBladeEditScreen` —— 改主手那把刀的杀敌数 / 耀魂数 / 锻造数 / SA / SE。
  入口在物品编辑器底栏（主手是拔刀剑时才出现，空隙只有 40px 所以按钮文字用单字「刀」）。
  - **1.21 适配要点（三处硬变化）**：
    1. 物品 NBT：`getTag()` / `getOrCreateTag()` 已移除 → 改走 `DataComponents.CUSTOM_DATA`；
       写入用 `ItemStack#update` 而**不是** `set` —— `set` 会整块覆盖自定义数据，
       把刀身上其它模组写进去的东西一起冲掉；
    2. 能力系统：Forge 的 `Capability + LazyOptional` → NeoForge 的 **Data Attachment**，
       取状态对象时两条路都试（拿不到就退化为直接改数据，至少保证落盘）；
    3. 注册表：`IForgeRegistry` 被移除 → 改试原生 `Registry#keySet()`，旧接口 `getKeys()` 兜底。
- **拼音搜索双后端**：`compat/PinyinSearchBridge`（+2 个编译存根）接到 `JechCompat.matches` 上 ——
  装了 JEC 走 JEC，否则走 `pinyin_search`，都没装就是原文包含。
  **一处改动覆盖 9 个搜索界面**（原来只支持 JEC，用 pinyin_search 的整合包搜索框打不出拼音）。
- **验证**：NeoForge `21.1.234` 下 `compileJava` 0 错误；`jar` 产出。
- **按决定跳过**：TaCZ 的枪械联动（1.21.1 无官方版，只有第三方移植版）。
  三条枪械属性的文案已改成只提卓越前线。
- **待实测**：卓越前线 / 拔刀剑在 1.21.1 上的类名尚未用 `javap` 逐项复核
  （手上没有这两个的 1.21.1 jar），所有注入点都写成可选（`require = 0`）、
  各句柄独立解析 —— 对不上时只让对应的一半失效，不影响启动与其它功能。

## [0.15.0] - 2026-10-15

### ⚙️ 「兼容联动」批（三）：能源 / 魔力 / 口渴体温 + 附魔上限修正 + 保护模式

内置属性数 `104 → 108`。

- **`infinite_energy`「无限能源」**（3 点）：`compat/EnergyCompat` 统一走 NeoForge Energy，
  把背包 36 格 + 盔甲 + 副手 + Curios 饰品 + 骑乘实体 + **自己放置的能量方块**的 FE 补满
  （RF / Mekanism / 热力 / EnderIO / 沉浸工程等最终都走它）。方块那一半按配置节流
  （`InfiniteEnergy.energyBlockRadius` / `energyBlockInterval`），归属走 `BlockOwnershipStore`，
  不会顺手补别人基地。1.21 适配：方块能力从 `BlockEntity#getCapability` 挪到
  `Level#getCapability(capability, pos, side)`，实体能力带方向上下文泛型（传 `null` 表示无方向）。
- **`infinite_mana`「无限魔力」**（3 点）：`compat/ManaCompat` 同时覆盖三条线 ——
  Iron's 法术（`MagicData` 补到 `MAX_MANA` 属性）、Ars Nouveau（`IManaCap`）、
  Botania（携带魔力物品 + **自己放下的魔力池**，不限距离，靠归属表的反向索引）。
  三块各自懒解析、互不拖累；1.21 适配：`RegistryObject<Attribute>` → `Holder<Attribute>`
  （`getAttributeValue` 现在收 Holder），能力返回值兼容 `Optional` / `LazyOptional`。
- **`no_thirst`「免口渴」+ `no_temperature`「免体温」**（各 3 点）：
  `compat/ThirstCompat` / `compat/ColdSweatCompat`，每 tick 兜底补满 ——
  口渴的消耗散落在 `PlayerThirst#tick` 的多条分支、体温要钉在 BODY 层（不是环境层），
  逐个拦截既脆又容易漏，而补满只有一处。两条都按候选包名 / 多种接入路径做多套尝试
  （NeoForge 的 Data Attachment 与旧的 Capability 都能接），取不到就静默退化。
- **附魔上限修正（存档这一环）**：1.21 的 `ItemEnchantments` 内存是 `int`、网络同步是
  `ByteBufCodecs.VAR_INT`（都不限），但**存档 codec 仍是 `Codec.intRange(0, 255)`** ——
  超过 255 的等级在序列化时会被拒（表现：点到 300 级，退出重进掉回 255）。
  新增 `mixin/ItemEnchantmentsMixin`，在 `ItemEnchantments` 的静态初始化里把那次
  `Codec.intRange(0, 255)` 换成 0~32767 —— 落点必须在**构造源头**，因为
  `LEVEL_CODEC → LEVELS_CODEC → FULL_CODEC → CODEC` 是同一个 `<clinit>` 里依次捕获的，
  只改字段值影响不到已构造好的 codec。等价于 1.20.1 的 `EnchantmentHelperMixin`
  （那边卡在 `putShort` 与读时 `clamp(0,255)`）。
- **效果过滤「保护」模式真正落地**：白名单不再只是「不拦截」，而是**保护** ——
  名单里的效果在自然结束前不会被任何外力清除（本模组自己的清理、其它模组、牛奶桶、
  `/effect clear` 都算外力）。实现：`PlayerStats.ProtectedEffect` 快照 + `DefenseHandler`
  每 tick 采样 / 补回（补回走 `forceAddEffect` 绕过其它模组的拦截）；
  自动复活的「全部清除」路径清完立刻把保护项补回来。
- **新增「重生继承」开关**：死亡重生时保护快照是否继承（默认关，与 1.20.1 的旧行为一致）；
  换维度（非死亡克隆）无论开关如何都必搬。开关挂在效果过滤面板**「模式」按钮的右键**上，
  悬停提示里显示当前状态与操作方式（顶栏已被标题与模式按钮占满，没有第二个按钮的位置）。
- **黑名单拦截改成每 tick 清扫**：原先挂在 `tickCount % 40` 上，而绕过原版事件加进来的效果
  只能等清扫才被移除 —— 毒 / 凋零 / 燃烧会在那段最长 2 秒的空窗里白跳好几跳伤害。
- **Apotheosis 附魔联动**：新增 `compat/ApotheosisEnchantCompat` 读
  `EnchHooks#getMaxLevel`，`EnchantLimits.packCap` 现在显式问它一次 ——
  避免出现「铁砧能合到锋利 9、我们自己附魔台只让选到 5」这种同包内自相矛盾的行为。
- **拼音搜索桥接**：新增 `compat/PinyinSearchBridge`（库的类型只出现在方法体里，
  未装时完全退化为原文包含），配套编译期存根 `src/compatApiStub/java/com/pinyinsearch/api`。
- **Create 存根已按真实签名修正**：用 `create-1.21.1-6.0.9.jar` 逐项 `javap` 核对 ——
  `KineticNetwork.members / sources / calculateCapacity()` 与 1.20.1 一致；
  `KineticBlockEntity#write` **多了一个 `HolderLookup.Provider` 参数**
  （1.21 的 NBT 序列化签名统一变化），存根与两处注入点已同步。
- **验证**：NeoForge `21.1.234` 下 `compileJava` 0 错误。
- **下一批（尚未移植）**：枪械四条（`infinite_ammo` / `no_heat` / `no_recoil` /
  `sbw_infinite_health`，需卓越前线 39.4KB Compat + 5 Mixin + 6 存根、TaCZ 12.6KB + 3 Mixin + 4 存根）、
  拔刀剑（14.8KB Compat + 编辑界面 + 网络包）、拼音搜索在物品编辑界面里的接入。

## [0.14.0] - 2026-10-15

### 🔌 「兼容联动」批（二）：机械动力 / 诡厄巫法 / Curios / 传送石碑

内置属性数 `102 → 104`。

- **`create_infinite_stress`「无限应力」**（3 点，需机械动力）：
  - `compat/CreateStressCompat` + `mixin/CreateKineticNetworkMixin`（在 `KineticNetwork#calculateCapacity`
    的 RETURN 上改写产能）+ `mixin/CreateKineticBlockEntityMixin`（存档时把改写值换回真值，
    避免把「无限产能」烤进存档）；
  - 只认**你自己放置**的动力网络（走新增的方块归属表），整网一起生效 ——
    动力网络是一整套传动结构，一部分转一部分停会直接穿帮；
  - 改写值可配置（`CreateStress.infiniteStressCapacity`，默认 10 亿 SU；用「够大但仍有限」而不是
    `Float.MAX_VALUE`，后者参与乘法 / 界面显示时容易出意外）；
  - 为编译这两处注入点新增**编译期存根源集** `src/compatApiStub`：Mixin 注解处理器要求目标类在
    编译期类路径上，而存根只进编译、不打进 jar（`build.gradle` 的 `compatApiStub` sourceSet）。
- **`goety_infinite_soul`「无限灵魂能量」**（3 点，需诡厄巫法）：
  `compat/GoetyCompat` 纯反射补满 Goety 的两套灵魂池（Arca 能力值 + Curios 灵魂图腾），
  由 `UtilityHandler` 每 tick 驱动；没装该模组或版本对不上时永久短路、零开销。
- **Curios 联动**：新增 `compat/CuriosBridge`（纯反射 + 可持久化 NBT），让「死亡不掉落」连**饰品栏**
  一起保 —— 饰品不在原版 `Inventory` 里、也不受 `keepInventory` 游戏规则保护。
  原则是「宁可掉落，绝不凭空消失」：抓取阶段出错就整份作废（饰品按原版掉落），
  归还时槽位找不到 / 放不下就退回背包或丢在脚下。
- **传送石碑联动**：新增 `compat/WaystonesCompat` + `network/WaystoneEntry` + 三个包
  （`WaystoneListRequestPacket` / `WaystoneListPacket` / `WaystoneTeleportPacket`），
  传送点面板现在会在自有点之后追加一行行**已激活的石碑**（金色名称区分），点「传送」走
  `forceTeleportToWaystone` —— 不扣经验、不进冷却；服务端只认该玩家已激活过的石碑，
  改包也传不到没踩过的点。
- **工具件补齐**：`util/BlockOwnershipStore`（方块归属表，随存档持久化，带按 owner 的反向索引）+
  `event/BlockOwnershipEvents`（放置 / 破坏时维护）+ `compat/CompatToggles`（联动属性开关判定）。
- **新增 `InfiniteStatsMixinPlugin`**：按**目标类包前缀**（目前是 `com.simibubi.create.`）判定该模组在不在场，
  不在场就不应用对应 Mixin —— `required: true` 的配置里若目标类缺席会直接让游戏起不来，
  这道把关保证「本模组无需任何前置」的承诺。
- **1.21.1 适配要点**：

  | 1.20.1 写法 | 1.21.1 写法 |
  |---|---|
  | Curios 返回 `LazyOptional` | 改为 `Optional`（NeoForge 版 Curios 9.x） |
  | `net.minecraftforge.items.IItemHandler` | `net.neoforged.neoforge.items.IItemHandler` |
  | `ItemStack#save(new CompoundTag())` / `ItemStack.of(tag)` | 都要注册表：`save(registryAccess())` / `parse(registryAccess(), tag)` |
  | `SavedData#save(CompoundTag)`、`computeIfAbsent(loader, factory, name)` | `save(CompoundTag, HolderLookup.Provider)`、`computeIfAbsent(SavedData.Factory, name)` |
  | `@Mod.EventBusSubscriber(bus = Bus.FORGE)` | `@EventBusSubscriber(modid)`（NeoForge 只有 game / mod 两条总线） |
  | `net.minecraftforge.fml.loading.LoadingModList` | `net.neoforged.fml.loading.LoadingModList` |
  | 传送石碑 `IWaystone#getWaystoneUid` 等 | 21.x 改了 API 形状（record 访问器）→ 类型名与方法名都做多套尝试，命中哪套用哪套 |
- **验证**：NeoForge `21.1.234` 下 `compileJava` 0 错误；`jar` 产出
  `infinite_stats-无限加点-0.14.0-NeoForge-1.21.1.jar`。
- **尚未移植**：TaCZ / 卓越前线的枪械四条、无限能源 / 无限魔力、口渴 / 体温、Apotheosis 附魔、
  拔刀剑、拼音搜索（按 `memory: project_compat_availability` 的核查结果排期）。

## [0.13.0] - 2026-10-15

### ⚙️ 「兼容联动」批（一）：Mixin 体系 + 3 条免第三方的属性

内置属性数 `99 → 102`。

- **引入 Mixin 基础设施**（本分支此前完全没有 Mixin）：
  - 新增 `src/main/resources/infinitestats.mixins.json`（`compatibilityLevel: JAVA_21`），
    并在 `neoforge.mods.toml` 里用 `[[mixins]] config="infinitestats.mixins.json"` 注册；
  - NeoForge 自带 Mixin 运行时（由 FML 依赖提供），**不需要** MixinGradle 插件，也**不生成 refmap**
    —— NeoForge 用 official 映射，运行时不重映射成员名，`@Mixin` 里可以直接写可读的成员名；
  - 延续 1.20.1 主线的约定：`injectors.defaultRequire = 0` 全部注入点都可选，
    任何锚点被别的模组挪走时只打印一条警告、对应功能退化为原版行为，不会让游戏起不来。
- **`enchant_limit`「附魔上限突破」**（1 点开关）：
  - `util/EnchantLimits`（上限判定）+ `util/EnchantText`（11 级以上的等级名）+
    `EnchantmentMixin`（接管 `Enchantment.getFullname`）+ `AnvilMenuMixin`（放开合成时的等级钳制）；
  - 附赠 `/infstats enchant <附魔> <等级>`（等级 0 = 移除该附魔）；未解锁时只允许设到整合包上限。
  - **1.21.1 适配（与 1.20.1 差别很大）**：1.21 起附魔等级存在 `ItemEnchantments` 组件里、
    底层就是 `int`，所以 1.20.1 那两个「等级读写接管」的 Mixin（`putShort("lvl")` 截断 /
    读取 `clamp(...,0,255)`）在本分支**不再需要**；`getFullname` 从实例方法变成静态
    `getFullname(Holder<Enchantment>, int)`；`Enchantment` 已是 record，「是不是诅咒附魔」
    改成查 `EnchantmentTags.CURSE`（没有 `isCurse()` 可用了）；附魔的 `max_level` 变成数据包驱动字段，
    装了会改写它的模组（Apotheosis 等）时 `getMaxLevel()` 读到的就是它们改过的值，因此不必再单开第三方分支。
- **`anvil_cost`「铁砧经验减免」**（每点 -5%，最多 -90%）：
  - 同一个 `AnvilMenuMixin` 里接管「最终写进 `cost` 的那次 `Mth.clamp`」，减免发生在「过于昂贵」判定之前；
  - 同时**取消「过于昂贵」门槛**：`@Redirect` 掉门槛比较里的 `DataSlot#get()`（原版硬编码 40），
    客户端由 `AnvilScreenMixin` 同步不再显示 `container.repair.expensive` ——
    注入的是 `Abilities#instabuild` 的读数，只影响「是否显示提示」，不改真实的消耗数字。
- **`breed_no_cooldown`「繁殖无冷却」**（3 点）：`AnimalMixin` 在
  `Animal#spawnChildFromBreeding` 的 `TAIL` 把两只亲本的年龄归 0（原版这里写死 6000 = 5 分钟冷却），
  两只亲本各自按「喂它的人」是否解锁来判定，混养时不会互相蹭效果。
- **未做**：随身铁砧的「50 级消耗封顶」需要先能区分「随身铁砧」菜单（`ContainerLevelAccess.NULL`），
  留到后续批次；其余 10 条依赖第三方模组的属性按 `memory: project_compat_availability` 的核查结果排期
  （Botania 无 1.21.1 版本、TaCZ 只有第三方移植）。
- **验证**：NeoForge `21.1.234` 下 `compileJava` 0 错误；`jar` 产出
  `infinite_stats-无限加点-0.13.0-NeoForge-1.21.1.jar`（内含 `infinitestats.mixins.json`）。

## [0.12.1] - 2026-10-15

### 🖥️ 界面自适应（第二批）：其余 18 个面板全部接入 PanelScreen

- 把余下所有**自绘界面**全部接到 `PanelScreen`（第一批只接了天气控制 / 跨维度传送）：
  属性面板、生物面板、物品编辑器、条目选择、元数据编辑、EMC 转化石板、效果过滤、
  成就管理、自动入库过滤、存储优先级、传送点面板 / 重命名、区块强加载、
  连锁设置 / 连锁名单、随身工具。
- 接入方式统一：`render` → `renderPanel`、鼠标钩子 → `onPanelXxx`（参数已是面板坐标，
  各自手写的 `leftPos + x` 位移与 `screenToPanelX/Y` 换算全部删掉）、
  裁剪区改用 `beginPanelScissor / endPanelScissor`、浮窗与状态提示挪到 `renderPanelOverlay`
  以保证画在控件之上。
- **属性面板**原先自带一套缩放 + 拖动（`guiScale` + `dragOffset` + 自绘百分比角标 + 中键复位），
  本次整体删除，改由基类统一负责（含角标切换、`Ctrl+滚轮`、`Ctrl+0`、中键复位、右键拖动）。
- **生物面板**自带的 `toPanelX / toPanelY` 换成基类同名方法（换算已把界面缩放算进去）。
- **容器类界面**（随身工作台 / 熔炉 / 注魔台、储备箱 / 缓冲箱 / 矿石优先顺序）仍用原版
  `AbstractContainerScreen`：它们有原生的菜单布局与原版自动缩放，不受"固定尺寸面板被切掉"影响，
  与 1.20.1 主线保持一致。
- **1.21.1 适配要点**：`Screen#render` 自 1.21 起自己调用 `renderBackground`，
  所以基类绘制控件时改成直接遍历 `renderables`（不走 `super.render`，否则背景被糊两次）；
  滚轮事件多了水平分量，签名是 `mouseScrolled(double, double, double, double)`。
- **验证**：NeoForge `21.1.234` 下 `compileJava` 0 错误，`jar` 产出
  `infinite_stats-无限加点-0.12.1-NeoForge-1.21.1.jar`。

## [0.12.0] - 2026-10-15

### 🖥️ 模组配置界面（Cloth Config）+ 界面自适应基类 + 构建产物命名

- **接入 Cloth Config 配置界面**：引入 `me.shedaniel.cloth:cloth-config-neoforge:15.0.140`，
  并在 `InfiniteStats` 里注册 NeoForge 的 `IConfigScreenFactory` 扩展点 ——
  「模组列表 → 本模组 → Config」按钮现在打开一张**按分组摊开**的配置表：
  布尔 / 整型 / 长整型 / 双精度 / 枚举 / 字符串列表全部支持，
  取值范围与每条说明分别取自配置项自身的 range 与 comment，不必手写第二遍界面。
  - 新增 `client/ClothConfigScreen`；分类标题与条目名走
    `config.infinitestats.category.<分组>` / `config.infinitestats.<分组>.<键>`，
    中英译文随本次一起补齐（含 `GUI.featureDisableMode` 的三个枚举值）。
  - **1.21.1 适配**：1.20.1 用的是 `ForgeConfigSpec` + `ModLoadingContext#registerExtensionPoint`，
    这里换成 NeoForge 的 `ModConfigSpec` + `ModContainer#registerExtensionPoint`，
    且 `IConfigScreenFactory#createScreen` 的签名是 `(ModContainer, Screen)`
    （不是 1.20.1 那套 `(Minecraft, Screen)`）。
- **新增 `client/PanelScreen`：固定尺寸面板的缩放基类**（界面自适应的基础）：
  - 子类只实现 `panelW()` / `panelH()`（面板逻辑尺寸），之后一律使用**面板坐标**
    （左上角恒为 0,0），居中、缩放、拖动全部由基类负责；
  - 默认「自动适配」：`min(1, 窗口宽/面板宽, 窗口高/面板高)` 并夹在 `0.5~2.0`；
    面板右下角百分比角标可点击切换自动 / 手动，`Ctrl+滚轮` 手动缩放、`Ctrl+0` 回自动、
    中键复位位置、右键拖动面板，设置持久化到 `config/infinite_stats-client.json`；
  - 提供 `beginPanelScissor` / `endPanelScissor`：按**面板坐标**开裁剪区（先压平姿态再换算屏幕坐标），
    避免「放大后裁剪区整体上移、把列表下半截剪掉」这类只在缩放时才暴露的问题；
  - `ClientSettings` 新增 `autoFit` 与 `MIN/MAX/DEFAULT_UI_SCALE`，并纳入存档读写。
  - **已接入面板**：天气控制、跨维度传送 —— 正好覆盖基类的两种典型用法
    （前者由基类统一绘控件，后者 `baseRendersWidgets() == false` 在裁剪区里自绘）；
    **其余面板留到下一批**逐块接入。
  - **1.21.1 适配**：`Screen#render` 自 1.21 起会自己调用 `renderBackground`，
    所以基类绘制控件时改成直接遍历 `renderables`，不再走 `super.render`
    （否则背景会被再糊一次、把面板内容盖掉）；滚轮事件也多了一个水平分量，
    签名改为 `mouseScrolled(double, double, double, double)`。
- **构建产物命名**改为「英文名称-中文名称-模组版本-模组加载器-游戏版本」，
  例如 `infinite_stats-无限加点-0.12.0-NeoForge-1.21.1.jar`（sources 包同名加 `-sources`）。
  中文名在 `gradle.properties` 里按 properties 规范写成 `\u` 转义，避免编码问题。
- **验证**：NeoForge `21.1.234` 下 `compileJava` 0 错误，`clean jar` 产出上述命名产物。

## [0.11.0] - 2026-10-15

### ✨ 从 Forge 1.20.1 主线移植：14 条属性 + 自动钓鱼 + 功能开关

本批补齐 1.20.1 主线里**不依赖第三方模组联动 / 不依赖 Mixin** 的 14 条属性，以及自动钓鱼整个模块。
内置属性数 `85 → 99`。

- **攻击类 2 条**：
  - `multi_shot`「多重射击」（最多 8 级）：每次射击额外射出一支箭，额外箭矢小幅散开、不可拾取；
  - `extra_strike`「额外打击」（最多 8 级）：每次造成伤害后追加 N 次独立结算的属性伤害，
    每次为本次伤害的 20%（`ExtraStrike.extraStrikeDamageRatio` 可调），无视护甲与减伤。
- **机动类 2 条**：
  - `multi_jump`「N 段跳」（最多 10 级）：空中松开再按跳跃键即可再跳一次；
  - `climb_speed`「爬梯加速」：爬梯 / 藤蔓 / 脚手架上升速度 +20% / 点。
    两条都在**客户端**每 tick 校正（玩家移动是客户端权威的），并在登出 / 落地时清零计数。
- **功能类 10 条**：
  - `always_eat`「满饱食度进食」（3 点）：饱食度满时也能继续吃普通食物；
    配置 `AlwaysEat` 分组提供限制 / 例外两份名单（物品 ID 或 `#` 标签）；
  - `keep_inventory`「死亡不掉落」（5 点）+ `keep_xp`「死亡不掉经验」（3 点）：死亡时暂存背包与经验，
    重生时原样归还（不依赖游戏规则 `keepInventory`）；
  - `trade_discount`「村民交易折扣」：每点 -1%（最高 90%），与原版声望折扣**叠加**且不会因反复开界面累加；
  - `trade_restock`「交易即刻补货」（3 点）：成交后立刻补货，可连续交易同一条目；
  - `instant_grow`「一键长大」（2 点）：手持饲料右键幼年动物一次喂饱长大；
  - `emc_learn_bonus`「EMC 学习收益」(+5%/点) 与 `emc_extract_discount`「EMC 转化折扣」(-6%/点)：
    新增 `emc/EmcPricing` 统一给 EMC 买卖定价套加成（学习 / 卖出 / 提取 / 合成四条路径全部接入）；
  - `auto_fish`「自动钓鱼」（3 点）+ `instant_bite`「立即咬钩」（3 点）：主手持竿看向水面自动抛竿、
    咬钩即收杆并按配置延迟重抛；`instant_bite` 把原版两段等待压到 1 tick，手动钓鱼也生效。
    新增 `handler/AutoFish`，配置 `AutoFish` 分组可调重抛延迟区间（默认 5~15 tick）。
- **1.21.1 适配要点**：

  | 1.20.1 主线写法 | 1.21.1 实际写法 |
  |---|---|
  | `ItemStack#isEdible()` | **已移除** → 用 `getFoodProperties(player) == null` 判断 |
  | `Entity#getPersistentData()`（多重射击复制体标记） | **已移除** → 改用实体标签 `Entity#addTag / getTags` |
  | `MerchantMenuAccessor`（Mixin 取 `trader`） | 改用 1.21 新增的 `TradeWithVillagerEvent`，可直接拿到 offer 与 `AbstractVillager` |
  | `FishingHookAccessor`（Mixin 暴露 `nibble` / `timeUntilLured` / `timeUntilHooked`） | 本分支无 Mixin → 反射取 Field 并缓存，失败时功能静默退化 |
  | `AttackHandler` 额外打击挂在 `LivingDamageEvent` | 同样挂在 `LivingDamageEvent.Post`（1.21 仍存在） |
- **实现**：改写 `stats/StatType`（16 条定义）、`Config`（ExtraStrike / AlwaysEat / AutoFish 三个分组）、
  `event/StatEventHandler`（死亡掉落 / 经验 / 交易折扣 / 交易补货 / 满饱食度进食 / 一键长大 / 多重射击 /
  额外打击挂钩）、`handler/AttackHandler`（额外打击）、`handler/UtilityHandler`（补货兜底 + 钓鱼接入）、
  `client/ClientEventHandler`（爬梯 / N 段跳）、`emc/EmcPricing`（新增）+ `EmcMenu` / `EmcEvents` / `EmcTransactions` 接入，
  以及中英语言文件与属性面板图标。
- **验证**：NeoForge `21.1.234` 下 `compileJava` 0 错误。（待进游戏实测）

### 🎛️ 新增「功能开关」：101 条内置属性可以逐条关掉

- 新增 `stats/FeatureDisableMode` 与配置 `GUI.featureDisableMode`（三种执行模式，整合包作者的全局设置）：
  - `keep`（默认）：整条失效，已投入的点数保留；
  - `refund`：整条失效，并把该属性已投入的点数全额退回可用点数；
  - `hide`：只从属性面板隐藏，效果与指令照常生效，点数不返还。
- 关闭后的表现：属性面板不再列出该条目、服务端拒绝其加点请求（客户端界面没了也拦住改包）；
  「整条失效」模式下数值按 0 计 —— `PlayerStats` 的**值缓存与开关缓存都跳过它**，
  因此所有读 `getStatValue` / `isToggleActive` 的功能（效果、随身站点指令）一并停用，无需逐个改。
- 配置 `GUI.disabledStats`：作者预设的「开局默认禁用」属性列表，只在玩家数据首次加载时写入一次
  （用 NBT 标志保证幂等）；之后改配置不影响已有玩家，后续开放交给下面的指令。
- 新增 `/infstats feature` 指令（需 OP，权限等级 2）：
  - `/infstats feature <属性> on|off` 单独开关某一条；
  - `/infstats feature all on|off` 全部开关；
  - `/infstats feature list` 列出当前模式与被关闭的属性；
  - `/infstats feature mode <keep|refund|hide>` 切换执行模式（写入配置并让在线玩家重算缓存）。
- **实现**：新增 `stats/FeatureDisableMode`；改写 `Config`（GUI 分组两个新项）、
  `PlayerStats`（`disabledStats` 集合 + 缓存零值 + NBT + copyFrom + 快照）、
  `SyncStatsPacket`（协议新增「已关闭属性」字段，客户端面板据此过滤）、
  `StatsScreen`（面板过滤）、`NetworkHandler`（加点服务端兜底）、
  `ModServerCommands`（feature 子命令）、`StatEventHandler`（登录时应用作者预设）。
- **下一批**：Cloth Config 配置界面（模组列表的 Config 按钮）、界面自适应（`PanelScreen` 缩放基类 + 各面板接入）。

## [0.10.0] - 2026-10-15

### ✨ 从 Forge 1.20.1 主线移植：连锁挖掘重做 + 自动冶炼重做（第五批）

- **连锁挖掘改成逐人可配置**（新面板：属性面板页脚「连锁」）：
  - 匹配方式：**自动**（对齐 FTB Ultimine：矿物/原木按共享标签、作物按同一种作物、其余只认同方块）/ **只认同方块** / **按标签组** / **自定义名单**；
  - 白 / 黑名单：可搜索的名单编辑器（方块 id + 显示名搜索，点一下加入/移出，最多各 256 条）；
  - 单次方块数（跟随服务端 / 8~256）、搜索半径（不限 / 1~48）、6 或 26 邻域；
  - 需正确工具、需潜行、保护工具（耐久剩 1 点停手）、**掉落直接进背包**、**准星范围预览**；
  - 每格饥饿消耗、每格经验消耗；**右键整片**开关。
  - 设置随玩家属性数据一起存档，改动走「客户端提交 → 服务端夹范围 → 回发权威值」。
- **准星范围预览**：用准星对着方块时描出会被连锁的方块（金框 = 瞄准的那格，绿框 = 会连锁的方块）；
  客户端用与服务端**同一套** `VeinMinerLogic` 现算，只在「瞄准方块 / 手上工具 / 配置」变化时重算，不每帧跑 BFS。
- **右键整片**：斧整片剥皮 / 铲整片铺路 / 锄整片耕地 / 锄整片收割成熟作物并补种；前三种对每一格走一次原版 `useOn`，
  所以木种、土类、音效、耐久、统计、其它模组注册的扩展全都自动跟随原版。
- **自动冶炼重做为「额外产出」**：手挖的那一格与被连锁带走的方块一视同仁 —— 原矿照掉，**另加一份**
  （有熔炼配方的给成品，没有配方的原样再给一份）；精准采集时不冶炼。
  开启自动冶炼或「掉落进背包」时由本模组接管该批方块的破坏与掉落（自行补发经验、统计、工具耐久），
  其余情况完全交给原版（其它模组的破坏事件与保护照常生效）。
- **顺带修掉旧实现的两个问题**：
  1. 旧版在事件里补主方块掉落，而原版掉落发生得更晚 → **主方块会掉两份**；现在接管模式下由本模组一次性完成；
  2. 旧版连锁只会连「完全相同的方块」，半径 / 匹配 / 代价 / 预览一概没有。
- **1.21.1 适配要点**：

  | 1.20.1 主线写法 | 1.21.1 实际写法 |
  |---|---|
  | `ForgeRegistries.BLOCKS` | `BuiltInRegistries.BLOCK`（`getTagNames()` 仍在，标签枚举逻辑一字未改） |
  | 方块经验 `block.getExpDrop(...)` | **已移除** → 改用 `BlockState#spawnAfterBreak(level, pos, tool, dropExperience)`，精准采集由末位参数控制 |
  | `RightClickBlock#setUseBlock(Event.Result.DENY)` | NeoForge 1.21 改成三态：`setUseBlock(TriState.FALSE)` |
  | `@Mod.EventBusSubscriber` / `net.minecraftforge.*` | `@EventBusSubscriber` / `net.neoforged.*` |
  | `ForgeMod.BLOCK_REACH` | 原版 `Attributes.BLOCK_INTERACTION_RANGE`（1.21 起原版自带该属性） |
  | `RenderLevelStageEvent#getPartialTick()` 返回 float | 返回 `DeltaTracker` → `getPartialTick().getGameTimeDeltaPartialTick(false)` |
  | 界面继承自适应的 `PanelScreen` 缩放基类 | 按本分支 `Screen` + `EditorUi` 风格重写（选项、默认值、交互全部对齐主线） |

- **实现**：新增 `stats/VeinMinerConfig`（每玩家设置 + NBT + 范围夹取）、`handler/VeinMinerLogic`（搜索与匹配）、
  `handler/RightClickVeinLogic` + `event/RightClickVeinEvents`（右键整片）、`client/VeinMinerOverlay`（准星预览）、
  `client/VeinMinerSettingsScreen` + `client/VeinMinerFilterScreen`（设置面板与名单编辑器）、
  `network/VeinMinerConfigPacket` + `UpdateVeinMinerConfigPacket`；
  改写 `UtilityHandler` 执行层、`Config` 新增 `veinMinerMatchTags`、`PlayerStats` 存档接线、
  `StatsScreen` 页脚新增「连锁」入口（并让页脚按钮宽度自适应，避免入口变多挤出面板）。
- **验证**：NeoForge `21.1.234` 下 `compileJava` 0 错误；`clean jar` 产出 `build/libs/infinitestats-0.10.0.jar`（待进游戏实测）。
- **第五批完成**。下一批：属性补齐 39 个（73→112）。

## [0.9.0] - 2026-10-15

### ✨ 从 Forge 1.20.1 主线移植：生物面板（第四批）

- **新属性 `entity_panel`**（功能类，1 点开关）：解锁后获得一根「生物观察杖」（带标记的木棍）——
  **属性关掉时自动收回**、被烧掉 / 死亡掉落时 1 秒内自动补发；手持它右键任意生物即可打开该生物的面板。
- **面板两页**：
  - **属性**：列出该生物身上存在的**全部**属性（含其它模组添加的），显示「当前值（基础值）」，
    被修饰符改过的用强调色标出、悬停看修饰符明细与取值范围；**点某一行即可直接改基础值**
    （回车提交 / Esc 取消，服务端会夹进该属性自身的取值范围，改小生命上限时把当前生命一起夹回）；
  - **开关**：AI / 幼年 / 静音 / 不可见 / 发光 / 无敌 / 无重力 —— 点一下切换（服务端执行并回包刷新成**权威状态**，
    客户端只做乐观更新，改不动就原地回弹）。
- **安全闸门**（全在服务端）：观察杖在手 + 属性已激活 + 目标在 8 格内 + 配置 `EntityPanel.allowEntityEditing`
  （默认 true；多人服可关成**只读面板**）。客户端发来的只是「想改成什么」，改不动就是改不动。
- **实现**：`util/EntityPanelWand`（观察杖）、`util/EntityPanelToggles`（7 个开关 + 位掩码）、
  `client/EntityPanelScreen`（自绘两页界面：页签 / 滚动条 / 行内输入框 / 悬停浮窗）、三个数据包
  （`OpenEntityPanelPacket` 下发、`EntityPanelActionPacket` 切开关、`EntityPanelAttributePacket` 改属性）、
  `NetworkHandler#canEditEntity` 统一权限链、`StatEventHandler` 两处右键入口 + 每秒对齐观察杖、
  `Config` 新增 `EntityPanel` 配置段、中英语言文件。
- **1.21.1 适配要点（这套代码几乎全部重写，不是照抄）**：

  | 1.20.1 主线写法 | 1.21.1 实际写法 |
  |---|---|
  | 观察杖用 NBT 标记 + `setHoverName` + 手拼 `display.Lore` | `CUSTOM_DATA`（`CustomData`）+ `CUSTOM_NAME` + `LORE`（`ItemLore`）数据组件，不再手写 JSON |
  | `ForgeRegistries.ATTRIBUTES` | `BuiltInRegistries.ATTRIBUTE`，属性以 `Holder<Attribute>` 参与 `getAttribute(...)` |
  | 属性 id 带 `generic.` 前缀（`minecraft:generic.max_health`） | 前缀已去掉（`minecraft:max_health`）—— 置顶清单同步改，否则一个都匹配不上、置顶失效 |
  | 修饰符 `getName()/getAmount()/getOperation()` | record 访问器 `id()/amount()/operation()`，且**不再有「名字」**（按 id 试 `attribute.modifier.<ns>.<path>` 译文） |
  | `Operation.ADDITION / MULTIPLY_BASE / MULTIPLY_TOTAL` | `ADD_VALUE / ADD_MULTIPLIED_BASE / ADD_MULTIPLIED_TOTAL` |
  | `PlayerInteractEvent.EntityInteract` 打开面板 | 同样可用（1.21.1 未变），额外补了 `EntityInteractSpecific` 覆盖精确交互路径 |
  | 界面继承自适应的 `PanelScreen` 缩放基类 | 该基类依赖本分支尚未移植的 `ClientSettings` 缩放字段，属后续「界面自适应」批次；本次按本分支 `Screen` + `EditorUi` 风格重写（功能对齐） |

- **验证**：NeoForge `21.1.234` 下 `compileJava` 0 错误；`clean jar` 产出 `build/libs/infinitestats-0.9.0.jar`（待进游戏实测）。
- **第四批完成**。下一批：连锁挖掘重做 + 自动冶炼重做。

## [0.8.2] - 2026-10-15

### 🐞 修复：无限弓箭「拉得起弓、射不出箭」

- **现象**：解锁「无限弓箭」、背包无箭时，弓能正常拉满蓄力，松手却没有箭矢射出（弩不受影响）。
- **根因**：1.21.1 的原版 `BowItem.releaseUsing` 把 `ArrowLooseEvent` 的触发点挪进了
  `if (!itemstack.isEmpty())` **内部** —— 背包无箭时该事件<b>根本不会触发</b>，于是本模组原先
  「在 `ArrowLooseEvent` 里取消事件 + 自行补箭」的做法彻底失效（1.20.1 时代的补丁把事件放在这个判定<b>之外</b>，
  两版行为不同）。而「开始拉弓」那一步走的是 `PlayerInteractEvent.RightClickItem`，与此无关、一直正常，
  所以症状正好是半残的「拉得起来、射不出去」。
- **修复**：弓的无箭放箭改挂 `LivingEntityUseItemEvent.Stop` —— 经核对原版源码，该事件在
  `ItemStack#releaseUsing` **之前**触发（弩的虚拟装填用的也是这个落点）：
  - 判定条件与第一步 `onRightClickItem` 严格一致（非创造 / 背包无箭 / 已解锁无限弓箭）；
  - 蓄力 tick = `useDuration - 剩余 tick`；强度阈值、暴击、力量与火矢附魔、弓的耐久损耗全部沿用原版；
  - `onArrowLoose` 里弓那一路降级为**兜底**（万一将来 NeoForge 又把事件挪回判定之外），
    并新增 `AMMO_LESS_SHOTS` 同 tick 去重，保证一次松手只出一支箭（登出时清理）。
- **影响范围**：仅 `event/StatEventHandler.java`。
- **验证**：`compileJava` 0 错误，产出 `build/libs/infinitestats-0.8.2.jar`。
  事件触发点差异是直接对比本机 `build/moddev/artifacts/neoforge-21.1.234-sources.jar` 里的
  原版 `BowItem.java` / `LivingEntity.java` 源码确认的，不是凭版本号推测。

## [0.8.1] - 2026-10-15

### 🔧 主属性面板不再罗列「随身站点」（11 条属性改为隐藏）

- **改了什么**：`portable_crafting` / `portable_furnace` / `portable_anvil` / `portable_ender_chest` / `portable_smithing` /
  `portable_stonecutter` / `portable_loom` / `portable_cartography` / `portable_grindstone` / `portable_enchanting` /
  `portable_brewing` 全部加上 `StatType.Builder.hidden()`，**不再出现在属性面板的属性列表里**。
- **为什么**：这 11 条随身站点在「随身工具」面板里已经有带图标、开关状态、投点解锁的完整入口，
  主属性面板再列一遍纯属重复，还把真正的属性挤下去了。
- **是隐藏而不是删除**（刻意这么选）：
  - 随身工具面板的左键解锁 / 右键开关仍然读写这些属性，真删掉会让那套入口直接失效；
  - 想让它们回来，把配置 `GUI.showHiddenStats` 改成 `true` 即可（面板按 `!stat.isHidden() || SHOW_HIDDEN_STATS` 过滤）；
  - 顺带补上 `portable_brewing` / `portable_enchanting` 在属性面板里缺失的图标（截图里那两行没图标）。
- **入口不受影响**：属性面板底部的「随身工具」按钮直接打开随身面板（不依赖任何属性行）；
  未解锁的站点在面板里左键点一下就能花 1 点解锁。
- **影响范围**：`stats/StatType.java`（11 条属性加 `.hidden()`）、`client/StatsScreen.java`（补 2 个站点图标）。

## [0.8.0] - 2026-10-14

### ✨ 从 Forge 1.20.1 主线移植：随身进阶高级附魔台（第三批 3c，第三批收尾）

- **新属性 `portable_enchanting`**（功能类，1 点开关）：「随身工具」面板新增一行，打开**自选附魔**的进阶附魔台（参考 Enchanting Infuser 的进阶档）：
  - 清单里逐条挑附魔与等级（0 = 拆掉）：左键点行 +1 / 右键 -1 / Shift 跳到两端；
  - **改已有附魔**：升级、降级、移除都按**差额**结算（已有的不重复收费，降级退差价）；
  - **经验修复**：花等级或点数直接把耐久修满；**经验回收**：拆掉全部附魔按 60% 折算返还（并清掉铁砧累积惩罚）；
  - **做附魔书**：往槽里放书，选好附魔即产出附魔书；
  - 付款方式可在「经验等级 / 属性点数」间切换，**不消耗青金石**；允许原版互斥附魔共存（锋利 + 亡灵杀手…）。
- **实现**：新增 `crafting/PortableInfuser`（规则与结算，双端同源纯函数）、`crafting/PortableInfuserMenu`（一物品槽 + 背包）、
  `crafting/PortableEnchanting`（打开入口）、`network/InfuserActionPacket`（附魔 / 修复 / 回收：服务端整体复算 + 三道闸门
  「必须是本菜单 / 属性已激活 / 条目数封顶」）、`client/PortableInfuserScreen`（自绘清单：滚动 + 行内箭头 + 逐行提示框）；
  `ModMenuTypes` 注册 `portable_enchanting_menu`，客户端在 `ClientEventHandler#onRegisterMenuScreens` 登记屏幕；
  `StatType` / 随身工具面板 / 中英语言文件同步。
- **1.21.1 适配要点（这套代码几乎全部按新附魔 API 重写，不是照抄）**：

  | 1.20.1 主线写法 | 1.21.1 实际写法 |
  |---|---|
  | `Enchantment` 实体 + `BuiltInRegistries.ENCHANTMENT` | **数据驱动注册表**，必须经 `RegistryAccess`（客户端取 `Minecraft#level`）；全程用 `Holder<Enchantment>` |
  | `EnchantmentCategory` + 自维护「铁砧限定组合」放宽表 | 1.21 的 `supported_items` 标签本身就比类别宽松（锋利支持 `#minecraft:weapons`，斧头可附），直接用 `isSupportedItem` |
  | `getRarity()` 定价 | 1.21 删掉了稀有度，改用等价的 `getWeight()` 分档 |
  | `isTreasureOnly()` 加价 | 宝藏变成 `#treasure` 标签，而标签是否同步到客户端不由本模组掌控 → 为保证**客户端显示价与服务端结算价永远一致**，只用 weight 定价 |
  | `EnchantmentHelper.getEnchantments/setEnchantments(Map)` | `ItemEnchantments` 组件：读 `getEnchantmentsForCrafting`、写 `setEnchantments`（自动区分普通物品 / 附魔书组件），构造走 `ItemEnchantments.Mutable` |
  | `enchantment.getFullname(level)`、`isCurse()` | `Enchantment.getFullname(Holder, level)`（**静态**）、诅咒判定用 `#curse` 标签 |
  | `stack.setRepairCost(0)` | 累积惩罚走 `DataComponents.REPAIR_COST` 组件 |
  | `EnchantLimits`（附魔上限突破） | 该属性在后续批次移植，本次上限暂用原版 `getMaxLevel()`（`PortableInfuser#maxLevel` 是将来唯一改动点） |

  上述签名均从**本机 NeoForge 21.1.234 的类文件与原版源码**（`build/moddev/artifacts/`，javap + 源码对照）核对，非照 1.20.1 推测。
- **验证**：NeoForge `21.1.234` 下 `compileJava` 0 错误；`clean jar` 产出 `build/libs/infinitestats-0.8.0.jar`（待进游戏实测）。
- **第三批完成**：6 个复用原版菜单的随身站点 + 随身工具面板（0.6.0）、随身酿造台（0.7.0）、随身进阶高级附魔台（0.8.0）。

## [0.7.0] - 2026-10-14

### 🧪 从 Forge 1.20.1 主线移植：随身酿造台（第三批 3b）

- **新属性 `portable_brewing`**（功能类，1 点开关）：「随身工具」面板新增一行，点击打开原版酿造台界面。
- **自带无限燃料**：起酿只看「原料槽能不能酿」，不需要也不消耗烈焰粉；界面燃料条恒为满格
  （`ContainerData` 第 1 项恒返回 20），免得看着像缺燃料。
- **台子跟着玩家走**：内容与进度收进玩家数据（`crafting/PortableBrewingData`），随存档保存、死亡 / 重生经 `copyFrom` 继承；
  酿造由 `StatEventHandler` 的**每玩家 tick** 驱动，**与界面是否打开无关** —— 关掉界面、跑去别的维度都照常酿，物品留在台子里。
- **实现要点（与主线的关键差异）**：主线用的是 Forge 的 `BrewingRecipeRegistry` —— **1.21.1 已经没有这个类**
  （酿造在 1.20.5+ 起改为数据驱动）。本版改用原版 `Level#potionBrewing()`：
  - 可酿判定：`brewing.isIngredient(原料)` + 对三个药水槽 `brewing.hasMix(药水, 原料)`（**药水在前、原料在后**）；
  - 实际酿造：`brewing.mix(原料, 药水)`（**原料在前、药水在后**），并照原版调用
    `EventHooks.onPotionAttemptBrew` / `onPotionBrewed`，因此数据包 / 其它模组扩展的酿造配方一样生效；
  - 容器残留物（如龙息用完变玻璃瓶）按原版处理：原料用尽时顶替它，否则丢在玩家脚下；
  - 以上签名是从**本机 NeoForge 21.1.234 的类文件与原版源码**（`build/moddev/artifacts/`）里核对过的，不是照 1.20.1 猜的。
- **验证**：NeoForge `21.1.234` 下 `compileJava` 0 错误；`clean jar` 产出 `build/libs/infinitestats-0.7.0.jar`（待进游戏实测）。
- **本批剩余**：3c 进阶高级附魔台（自选附魔的自建菜单与界面，不复用原版三档随机附魔）。

## [0.6.0] - 2026-10-14

### 🎒 从 Forge 1.20.1 主线移植：随身站点扩展（6 个）+ 随身工具面板

- **新属性 6 条**（功能类，各 1 点开关）：`portable_ender_chest` 随身末影箱、`portable_smithing` 随身锻造台、
  `portable_stonecutter` 随身切石机、`portable_loom` 随身织布机、`portable_cartography` 随身制图台、`portable_grindstone` 随身磨石。
- **新面板「随身工具」**：属性面板页脚新增入口；页脚入口由 13 个回到 **11 个**
  （原先的「工作台 / 熔炉 / 铁砧」三个入口合并进这一个面板，与主线「随身工具统一到一个面板」的做法一致）：
  - **左键 = 打开**该站点（未解锁则先花 1 点解锁，点数不足会提示「点数不足」）；
  - **右键 = 开启 / 关闭**；
  - 每行显示站点名与「已开启 / 未开启」，顶部实时显示可用点数。
- **实现**：
  - `crafting/PortableStationMenus`：继承原版 `StonecutterMenu` / `LoomMenu` / `CartographyTableMenu` / `GrindstoneMenu` / `SmithingMenu`，
    **传真实的 `ContainerLevelAccess`（玩家脚下）**，从而保留全部世界侧回调（制图台的地图运算、关界面时清空输入槽等），
    只把 `stillValid` 覆写为 `true` 以解除「必须站在方块旁」——**不能**用 `ContainerLevelAccess.NULL`：那样制图台算不出成品、输入槽材料会在关界面时消失；
  - `crafting/PortableStations`：统一打开入口（末影箱直接接 `player.getEnderChestInventory()`，与原版末影箱共用同一份库存）；
  - 新增 `network/PortableStationOpenPacket`（客户端 → 服务器）：服务端二次校验属性是否激活，未激活只回提示、不开界面；
  - `client/PortableToolsScreen` 按本分支 `Screen` + `EditorUi` 风格实现（未引入主线的 `PanelScreen` 基类）；
  - `stats/StatType` 追加 6 条属性；`client/StatsScreen` 补 6 个图标、页脚导航重排；中英语言文件各补 27 条文案。
- **验证**：NeoForge `21.1.234` 下 `compileJava` 0 错误；`clean jar` 产出 `build/libs/infinitestats-0.6.0.jar`（待进游戏实测）。
- **本批未含（继续做）**：随身酿造台 —— 需要「台子跟着玩家走」的独立数据（`PortableBrewingData`）+ 每 tick 驱动的酿造逻辑；
  进阶高级附魔台 —— 自选附魔的自建菜单与界面（不复用原版附魔算法）。两者分别作为 3b / 3c 继续。

## [0.5.0] - 2026-10-14

### ✨ 按功能的存储优先级（作用域）

- **新界面「存储优先级」**：入口在「自动入库过滤」界面的「优先级」按钮。
  每个用到存储网络的功能各有一份**独立**的优先级，顶部作用域按钮可在四者之间循环切换：

  | 作用域 | 影响 |
  |---|---|
  | 自动入库 | 自动把背包物品写入存储的顺序 |
  | 随身工作台 | 取料 / 存放成品的顺序 |
  | 随身熔炉 | 补矿 / 补燃料的顺序 |
  | 熔炉成品仓 | 存入成品的顺序 |

- **交互**：每行 ▲ / ▼ 只与相邻的存储交换一位，顺序一眼可见；行尾标出该存储模组「已安装 / 未安装」（未安装的排在前面也不会被选中）；
  底部「恢复默认」把当前作用域清回配置顺序（给服务端发空列表，服务端按配置补全）。
- **数据与同步**：
  - `PlayerStats` 新增作用域常量（`SCOPE_AUTO_DEPOSIT` / `SCOPE_CRAFTING` / `SCOPE_FURNACE` / `SCOPE_PRODUCT_BUFFER` + `NETWORK_SCOPES`）
    与 `networkPriorities` 表，提供 `getEffectiveNetworkPriority(scope)` / `setNetworkPriority(scope, order)`；
    随 NBT 存档、死亡 / 重生经 `copyFrom` 继承、进 `StatsSnapshot` 与 `SyncStatsPacket` 同步；
  - `NetworkIO` 新增 `getNetworks(player, scope)` 与 `getNetworks(player, order)`：玩家自定义优先，未设置时回退配置 `NetworkPriority.networkPriority`；
  - **四处调用点改为按各自作用域取网络**：`compat/AutoDeposit`、`crafting/PortableCraftingMenu`、`furnace/PortableFurnaceMenu`、`furnace/FurnaceProductBufferMenu`；
  - 新增 `NetworkHandler.SetNetworkPriorityPacket`：服务端校验作用域与存储键、剔除重复、按配置补齐缺失键，并限制顺序长度；
    自动入库作用域额外要求该属性已激活。
- **验证**：在 NeoForge `21.1.234` 下 `compileJava` 0 错误；`clean jar` 产出 `build/libs/infinitestats-0.5.0.jar`（待进游戏实测）。
- **实现差异（与主线）**：主线把「优先级」按钮分别放在属性面板 / 随身工作台界面 / 随身熔炉界面 / 成品仓界面四处；
  本版做成**一个界面 + 作用域切换按钮**（少改 3 个随身界面），功能等价，入口在「自动入库过滤」界面里。

## [0.4.0] - 2026-10-14

### ✨ 自动入库过滤界面（白 / 黑名单）

- **新界面**：属性面板页脚新增「入库」入口（页脚导航由 12 个增至 **13 个**，按钮宽度 33→30 才塞得下），打开「自动入库过滤」面板：
  - **点击行即切换**该物品「在名单内 / 不在名单内」，名单内的行有绿色指示条；
  - 搜索框按物品名与物品 ID 过滤；「只看已选」把列表收窄到已选条目；「手持加入」一键把主手物品加入 / 移出名单；
  - 模式按钮在**黑名单 / 白名单**之间切换，两种模式的名单**各自独立**（切模式不会互相污染）。
- **语义**：白名单 = 只入库名单内的物品；黑名单 = 名单内的物品不入库；**名单为空时不做任何限制**
  （白名单为空若不限制，玩家清空列表后自动入库才不会整体失效）。
- **数据与同步**：
  - `PlayerStats` 新增「自动入库过滤」字段与读写方法（`allowsAutoDeposit` 等），随 NBT 存档、死亡 / 重生经 `copyFrom` 继承；
  - `StatsSnapshot` 与 `SyncStatsPacket` 各扩展 3 个字段（模式 + 黑名单 + 白名单）—— **必须进快照**：客户端收到同步后会 `restoreFromSnapshot` 整体覆盖，不带上就会被清空；
  - 新增 `NetworkHandler.UpdateDepositFilterPacket`（客户端 → 服务端）：服务端剔除非法物品 ID、限制条目数（≤4096），并要求 `auto_deposit` 已激活才受理；
  - `compat/AutoDeposit` 扫描时按玩家名单放行 / 拦截。
- **实现说明**：过滤界面按本分支的 `Screen` + `EditorUi` 风格实现（未引入主线 1.20.1 的 `PanelScreen` 基类）；
  搜索为「名称 / ID 子串匹配」，**未接入拼音搜索**（本分支没有 `PinyinSearchBridge`，对应位置是 `JechCompat`）。
- **验证**：`gradle compileJava` 在 NeoForge `21.1.234` 下 0 错误；`gradle clean jar` 产出 `build/libs/infinitestats-0.4.0.jar`（待进游戏实测）。
- **待办（可选）**：主线那套「按功能（随身工作台 / 随身熔炉 / 成品仓 / 自动入库）分别设置存储优先级」的界面尚未移植 ——
  本分支的存储优先级目前仍是配置项 `NetworkPriority.networkPriority` 的**全局顺序**；要做成按玩家、按作用域的优先级，需要
  `PlayerStats` 作用域结构 + `NetworkIO` 作用域重载 + 独立界面，等你确认是否要做。

## [0.3.0] - 2026-10-14

### ✨ 从 Forge 1.20.1 主线移植：自动入库（核心）

- **自动入库（`auto_deposit`，功能类，3 点解锁）**：解锁后按配置间隔（默认 1 秒）扫描一次主背包，
  把物品自动写入当前可用的存储网络（RS / AE2 / 汤姆存储 / 背包 / BD）；靠前的网络先写入，装不下的部分自动回落到下一个网络。
- **安全约束**（与主线一致）：
  - 存储终端（RS / AE2 无线终端）与背包装备**永不入库** —— 否则一次扫描就会把「连网凭证」自己存走，玩家当场失去网络访问能力；
  - 玩家正在打开别的容器（箱子 / 交易 / 随身工作台）时**不打扰**；
  - 默认**不碰快捷栏**（0-8 号槽），避免把随身工具 / 武器吸走；可由配置改为只保留当前手持那一格；
  - 支持黑名单：物品 ID，`#` 开头表示物品标签。
- **新配置**：`AutoDeposit.autoDepositInterval`（默认 20 tick，范围 5~1200）、`autoDepositKeepHotbar`（默认 true）、`autoDepositBlacklist`（默认空）。
- **实现**：新增 `compat/AutoDeposit`（移植自主线，按本分支 API 改写：`isSameItemSameComponents`、`Inventory#getItem/setItem`、`displayClientMessage` 等）；
  `handler/UtilityHandler#onTick` 挂钩并按配置间隔节流、异常隔离（单个物品出错不影响其它功能）；
  `stats/StatType` 追加属性；`Config` 追加 `AutoDeposit` 段；`client/StatsScreen` 补图标；中英语言文件同步。
- **拆批说明**：主线里「白 / 黑名单过滤界面」与「按功能的存储优先级界面」依赖一套本分支尚不具备的
  「按玩家、按作用域（scope）的存储优先级」结构（`PlayerStats` 字段 + NBT + `SyncStatsPacket` 扩展 + 两个界面 + 各自数据包）。
  为避免一次交付过大，本版先交付核心逻辑；**过滤界面与优先级界面放在下一步**（届时黑名单可界面维护，无需改配置）。
- **验证**：`gradle compileJava` 在 NeoForge `21.1.234` 下 0 错误；`gradle clean jar` 产出 `build/libs/infinitestats-0.3.0.jar`（待进游戏实测）。

## [0.2.0] - 2026-10-14

### ✨ 从 Forge 1.20.1 主线移植：天气控制 + 区块强加载

- **天气控制（`weather_control`，功能类，1 点开关）**：属性面板页脚新增「天气」入口，打开面板后可**点哪档切哪档** —— 晴天 / 下雨 / 雷暴，不再靠循环按钮。
  切换作用于**主世界**（在下界 / 末地切的也是主世界天气），当前档位在面板上以绿框高亮；每档持续时间由新配置
  `WeatherControl.weatherCycleDuration`（默认 6000 tick = 5 分钟）决定，到时后按原版规则自行演变。
- **区块强加载（`chunk_loader`，功能类，1 点开关）**：属性面板页脚新增「强加载」入口，输入方块坐标（或点「填入当前位置」）即可把对应区块设为强加载；
  面板下方按**当前维度**列出已强加载的区块（5 行/页、支持翻页），每行可单独卸载。列表由服务端在每次操作后回推，界面不会与真实状态脱节。
  数量上限由新配置 `ChunkLoader.maxForcedChunks`（默认 16，范围 1~256）控制，超限由服务端拒绝并提示 —— 防止误操作把服务器拖垮。
  强加载区块随存档保存，与 `/forceload` 写的是同一份数据。
- **实现**：
  - 新增 `network/WeatherControlPacket`、`network/ChunkLoaderActionPacket`、`network/ChunkLoaderSyncPacket`（沿用本分支自研 `SimpleChannel`，协议 id 由 `packetId++` 分配）；
  - 新增 `client/WeatherScreen`、`client/ChunkLoaderScreen`（按本分支 `Screen` + `EditorUi` 风格实现，不引入新基类）；
  - `network/NetworkHandler` 新增 `sendForcedChunks(player, level)` 同步辅助；`stats/StatType` 追加两条属性；
  - `Config` 新增 `WeatherControl` / `ChunkLoader` 两段；`client/StatsScreen` 补两个属性图标，页脚导航由 10 个入口增至 **12 个**（按钮宽度 / 间隔由 `40 / 3` 收窄为 `33 / 2`）；
  - 中英文语言文件各补 47 条文案。
- **验证**：`gradle compileJava` 在 NeoForge `21.1.234` 下 **0 错误**；`gradle clean jar` 产出 `build/libs/infinitestats-0.2.0.jar`（待进游戏实测确认功能）。
- **本批说明**：只做「不依赖 Mixin」的两项。原计划的「自动入库」依赖主线那套「存储优先级作用域（`SCOPE_AUTO_DEPOSIT`）+ 白 / 黑名单过滤界面（`AutoDepositFilterScreen`）」，体量更大，已调整到后续批次与存储优先级一起做。

## [0.1.1] - 2026-10-14

### 🔧 NeoForge 目标版本下调：21.1.252 → 21.1.234

- **改动**：
  - `gradle.properties`：`neo_version`、`loader_version` 由 `21.1.252` 降为 `21.1.234`；
  - `src/main/resources/META-INF/neoforge.mods.toml`：NeoForge 依赖下限 `[21.1.252,)` → `[21.1.234,)`。
- **为什么可以降**：`21.1.x` 是同一 Minecraft 版本（`1.21.1`）的补丁线，降 build 号不涉及 Minecraft 版本变化；
  且 `21.1.234` 与 `21.1.252` 使用的 neoform 版本相同（`1.21.1-20240808.144430`），Minecraft 侧产物完全一致。
- **验证**（在本机实际执行）：
  - `gradle compileJava` 在 **21.1.234** 下通过，0 错误；
  - `gradle clean jar` 产出 `build/libs/infinitestats-0.1.1.jar`。
  - 主代码所用 API 在 21.1.234 中全部存在，因此**未改动任何 Java 代码**：tick 事件
    `PlayerTickEvent.Post` / `ServerTickEvent.Post` / `ClientTickEvent.Post`、`RegisterGuiLayersEvent`、
    `RegisterKeyMappingsEvent`、attachment（`AttachmentType` / `IAttachmentSerializer`）、
    `RegisterPayloadHandlersEvent` / `PayloadRegistrar`、`RegisterClientCommandsEvent`、`IMenuTypeExtension` 等。
- **遗留问题（与本次降级无关）**：`src/gameTest` 下两个回归测试文件仍是 1.20.1 时期的 API
  （`LivingHurtEvent`、`ItemStack#getTag`、部分 `FriendlyByteBuf` 方法），`compileGameTestJava` 报 49 个错误。
  已核对 `LivingHurtEvent` 在 **21.1.234 与 21.1.252 的 NeoForge 中都不存在**（早已更名 `LivingIncomingDamageEvent`），
  属移植时未同步的测试代码，不影响主代码与打包，待后续单独修复。
- **影响范围**：`gradle.properties`、`src/main/resources/META-INF/neoforge.mods.toml`。

## [1.18.1] - 2026-10-02

### 🐛 修复：模组无法加载（启动即崩溃）

- **现象**：进游戏时模组实例创建失败，游戏崩溃：
  `Failed to create mod instance. ModID: infinitestats`，
  根因异常为 `java.lang.IllegalArgumentException: Failed to register
  com.infinitestats.event.StatEventHandler.onMobEffectApplicable:
  Return type boolean is only valid for cancellable events`。
- **原因**：26.2 的 `MobEffectEvent.Applicable` 实现的是 `HasResult` 特征，
  而**不是** `Cancellable`；其取消语义由 `setResult(Result.DENY)` 表达。
  迁移时误将其按「可取消事件」写成 `boolean` 返回值，导致 EventBus 7 在注册监听器时直接抛异常。
- **修复**：`StatEventHandler#onMobEffectApplicable` 改为 `void`，改用
  `event.setResult(Result.DENY)` 拒绝药水效果施加；补充 `net.minecraftforge.common.util.Result` 导入。
- **核对**：同文件中其余 4 个返回 `boolean` 的监听器（`LivingDeathEvent`、
  `PlayerInteractEvent.RightClickItem`、`ArrowLooseEvent`、`LivingHurtEvent`）
  均实现 `Cancellable`，写法正确无需改动。

## [1.18.0] - 2026-10-02

### 🚚 平台迁移：Minecraft 1.20.1 (Forge 47) → Minecraft 26.2 (Forge 26.2-65.1.3)

> 这是一次跨 5 个大版本的断层式迁移。Java 17 → **Java 25**，ForgeGradle 6 → 7，映射名、
> 事件总线、网络层、渲染层、注册表体系全部重写。
> **迁移已完成：编译 0 错误，成功产出 `build/libs/infinitestats-1.18.0.jar`。**
>
> ⚠️ 按需求未做运行时测试（未启动游戏验证）。首次进游戏时建议留意日志中的
> `[InfiniteStats]` 输出与异常堆栈。

### ✅ 已完成

- **构建环境**
  - 接入 Forge 26.2-65.1.3 + Java 25 工具链；Gradle Wrapper 升到 9.5.0。
  - 关键坑：ForgeGradle 7 的 mavenizer 在 `modifyAccess` 阶段**强制要求 JDK 8**，
    会把 JDK 8 当作工具链依赖去 GitHub 下载；本机无 JDK 8 且直连 GitHub 超时，
    已改用镜像下载 JDK 8 并通过 `JAVA_HOME_X64` 注入（详见迁移文档）。
- **全局 API 重命名**：`ResourceLocation→Identifier`、`GuiGraphics→GuiGraphicsExtractor`、
  `drawString→text`、`drawCenteredString→centeredText`、`renderItem→item`、
  `renderItemDecorations→itemDecorations`、`blit(...)` 补 `RenderPipeline`、
  `CompoundTag` 的 getter 全面 `Optional` 化（`getStringOr` / `getCompoundOrEmpty` / `getListOrEmpty`）、
  `isSameItemSameTags→isSameItemSameComponents`、`EventPriority→Priority`。
- **网络层**：41 个数据包从 `NetworkRegistry.newSimpleChannel` + `registerMessage(encode/decode/handle)`
  改造为 `ChannelBuilder.named(...).simpleChannel().play().bidirectional().add(...)`；
  用 `codec(...)` 适配器把旧 `encode/decode` 包成 `StreamCodec`；
  `handle` 改为 `CustomPayloadEvent.Context`；`sendToServer`/`sendToClient` → `send(pkt, PacketDistributor.*)`。
- **事件总线**：改用 EventBus 7 —— `BusGroup.DEFAULT.register(MethodHandles.lookup(), XxxHandler.class)`
  显式注册，不再依赖 `@Mod.EventBusSubscriber` 扫描；`TickEvent.Phase` 改为
  `TickEvent.PlayerTickEvent.Post` / `ServerTickEvent.Post` / `ClientTickEvent.Post`。
- **事件取消模型**：26.2 的 `Cancellable` 是空标记接口，**取消改为「监听器返回 true」**；
  `StatEventHandler` 的死亡/右键/放箭/受伤/伤害五处分支已改为返回 boolean（lambda 内用数组带出判定）。
- **Capability**：`RegisterCapabilitiesEvent` 已取消，改为 `@AutoRegisterCapability` 注解；
  `AttachCapabilitiesEvent<Entity>` → `AttachCapabilitiesEvent.Entities`；
  `INBTSerializable` 签名加上 `HolderLookup.Provider`。
- **客户端**：Screen 渲染 `render→extractRenderState`、`renderBg→extractBackground`（参数重排）、
  `renderLabels→extractLabels`；输入回调全部改为事件对象
  （`mouseClicked(MouseButtonEvent, boolean)`、`keyPressed(KeyEvent)`、`mouseScrolled(x, y, scrollX, scrollY)`）；
  `KeyMapping` 需要 `KeyMapping.Category`；HUD 从已废弃的 `RegisterGuiOverlaysEvent`
  改为 `AddGuiOverlayLayersEvent` + `ForgeLayeredDraw`。
- **其它适配**：`new Identifier(...)`→`Identifier.fromNamespaceAndPath/parse/withDefaultNamespace`；
  配方输入全面走 `placementInfo().ingredients()`、`Ingredient.items()`；
  配方产出走 `recipe.display()` + `SlotDisplay.resolveForFirstStack`；
  `ContainerHelper` 改收 `ValueInput/ValueOutput`（用 `TagValueOutput/TagValueInput` 包一层）；
  `AttributeModifier` 改用 `Identifier` + record 访问器；`AttributeModifier.Operation`
  常量为 `ADD_VALUE/ADD_MULTIPLIED_BASE/ADD_MULTIPLIED_TOTAL`；
  `Component.Serializer` 移除 → 新增 `util/TextJson`；`ForgeRegistries.ENCHANTMENTS` 移除 →
  走数据驱动附魔注册表；`TierSortingRegistry` 移除 → 挖掘等级加成改为直接放行；
  属性面板 `Minecraft.setScreen/screen` → `Minecraft.gui.setScreen/screen()`。

### ⚠️ 迁移中采用的功能近似实现（26.2 无对应 API）

| 功能 | 1.20.1 行为 | 26.2 迁移后 |
|---|---|---|
| 挖掘等级加成 | 按 `TierSortingRegistry` 提升工具材质等级 | 26.2 已移除该 API → 改为**有点数即允许采集** |
| 物品编辑器·内置属性回填 | `Item#getAttributeModifiers` 读出内置属性作为初值 | 26.2 属性走数据组件 → 初值改为空，仅编辑已存在的 NBT |
| 熔炉燃料残留物 | `ItemStack#getCraftingRemainingItem` | 改用 `Item#getCraftingRemainder()`（模板转 ItemStack） |
| 随身工作台·配方使用记录 | `ResultSlot#setRecipeUsed(level, player, recipe)` | 26.2 已移除该接口 → 跳过记录（不影响合成产出） |
| EMC 界面·点击背包取物 | `MultiPlayerGameMode#handleInventoryMouseClick` | 26.2 已移除 → 该交互移除，其余界面功能保留 |
| 跨维度强制传送 | 手工拼装重生包 + 反射绕开门控 | 委托 `ServerPlayer#teleportTo(…, Set<Relative>, …, boolean)` |
| JEI 集成 | `compat/jei/` 两个类 | 26.2 无对应 JEI 坐标 → 在 `build.gradle` 中 exclude（源码保留） |
| 物品「display」NBT | 读写 `display` 标签 | 26.2 已组件化 → 相关读写退化为空标签 |
| 箭矢击退附魔 | `AbstractArrow#setKnockback` | 26.2 无该 setter → 移除击退加成（力量/火矢保留） |

### 📄 相关文件

- 迁移状态与后续清单：`docs/forge-26.2-migration-status.md`
- 迁移脚本：`tools/*.ps1`（22 个按批次执行的批量改写工具，可回溯每一步改动）

## [1.17.2] - 2026-10-01

### 🌏 补充外部属性中文译文（+22 条）

- **来源**：整合包 `Nemesis of Demons` 启动后生成的缺译文清单 `config/infinitestats/external_translations.json`，其中仍有 22 个属性的值停留在 `[modid:attr]` 占位符（其余 43 条此前已覆盖）。
- **新增覆盖的两个模组**：
  - `additionalentityattributes`（11 条）：暴击额外伤害、水中移动速度 / 水下视野、肺活量、岩浆中移动速度 / 视野、挖掘速度、额外稀有掉落次数、额外掉落次数、掉落经验、魔法保护；
  - `obscure_api`（11 条）：暴击率、暴击伤害、闪避、招架、魔法伤害、魔法抗性、穿透、生命恢复、治疗强度、精准度、韧性。
- **实现方式**：全部直接写入 `assets/infinitestats/lang/zh_cn.json` 的「外部模组属性补充翻译」区块尾部，沿用既有做法 —— 借 MC 语言表跨 namespace 合并 key 的特性，装包即用，不需要各整合包自行做资源包。
- **影响范围**：`src/main/resources/assets/infinitestats/lang/zh_cn.json`、`README.md`（已覆盖模组清单同步补上这两个模组）。
- **验证**：用脚本比对清单与语言文件，65 条键全部命中，且无重复键。

## [1.17.1] - 2026-10-01

### 🔀 随身工作台接入 Polymorph（多态合成）

- **背景**：Polymorph 让「多个配方产出同一物品」时由玩家自己选。但**自定义合成容器不会自动被它接管** —— 它的集成接口面向两类宿主：方块实体（熔炉等）与物品（背包里的合成升级等），而随身工作台两者都不是。
- **切入点**：Polymorph 的配方宿主其实有**第三类 —— 玩家本身**（`IPlayerRecipeData`），这正是它为「没有方块实体的合成容器」准备的，原版工作台走的就是它：

  ```java
  Optional<IPlayerRecipeData> data = PolymorphApi.common().getRecipeData(player);
  data.get().setContainerMenu(menu);                       // 必须先挂菜单，见下
  Optional<CraftingRecipe> picked =
        data.get().getRecipe(RecipeType.CRAFTING, craftSlots, level, List.of());
  ```

  传空列表时 `AbstractRecipeData#getRecipe` 会**自己完成全部工作**：收集所有匹配配方 → 按玩家上次的选择挑一个 → 整理候选列表并按需同步给客户端控件。因此这里几乎不需要自己写逻辑。
- **`setContainerMenu` 不能省**：`PlayerRecipeData#sendRecipesListToListeners` 只在「配方数据的容器菜单 == 玩家当前打开的菜单」时才真正发送，不挂上菜单客户端就收不到候选列表、选择控件也就空着。该方法在 `getRecipe` 读完会自动清空，无需手动还原。
- **客户端零改动**：Polymorph 客户端按「槽位容器是不是 `ResultContainer`」识别合成结果槽（`findCraftingResultSlot`），而随身工作台的结果槽正是 `ResultSlot` + `ResultContainer`，于是界面一打开就会被自动接管，配方选择控件直接出现在成品槽旁。
- **实现方式**：沿用本模组既有的兼容层风格（与 RS / AE2 / 背包桥接一致）——**纯反射，不加编译期依赖**。新增 `compat/PolymorphCompat`，运行时用 `ModList` 探测并缓存方法句柄。
- **安全降级**：Polymorph 未加载、API 版本对不上或反射调用抛错时一律返回 `null`，`PortableCraftingMenu#slotsChanged` 随即回退到原版「取第一个匹配配方」的行为 —— 不装 Polymorph 的整合包完全不受影响，也不会白跑一次全量配方扫描。
- **影响范围**：新增 `compat/PolymorphCompat`；`crafting/PortableCraftingMenu#slotsChanged` 改为先问 Polymorph、拿不到再回退。

## [1.17.0] - 2026-10-01

### 🔨 新增「随身铁砧」（功能分类，1 点解锁）

- **效果**：解锁后可在任意位置打开一台铁砧，用于**修复 / 合并附魔 / 重命名**，与站在真铁砧前完全一致 —— 包括正常的**经验消耗**。
- **实现**：直接复用原版 `AnvilMenu`，只把 `ContainerLevelAccess` 传成 `ContainerLevelAccess.NULL`：

  ```java
  new SimpleMenuProvider(
          (windowId, inv, p) -> new AnvilMenu(windowId, inv, ContainerLevelAccess.NULL),
          Component.translatable("container.repair"))
  ```

  - 铁砧的**方块损伤、砸落铁砧伤害、音效与统计**全都写在 `access.execute(...)` 回调里，而 `NULL` 实现是空操作 —— 于是随身铁砧天然**不会损耗**，也不会在世界里留下任何痕迹；
  - **经验扣减**位于该回调之外（`AnvilMenu#onTake` 中），照常生效，不会变成免费修理。
- **零新增客户端代码**：菜单类型沿用原版 `MenuType.ANVIL`，客户端会自动套用原版 `AnvilScreen`。新增的只有服务端侧 `crafting/PortableAnvil` 与一个 `AnvilOpenPacket`。
- **入口**：`/infstats anvil`，或属性面板页脚导航栏的「铁砧」按钮。

### 🧰 属性面板导航栏扩容

- 页脚导航从 9 个入口增至 **10 个**（新增「铁砧」）。`GUI_WIDTH = 440` 原本已被 `9 × 45 + 8 × 4 = 437` 占满，因此按钮宽度与间隔收窄为 `40 / 3`（合计 427，居中后左右各留 6）。
- **顺带修掉一处国际化遗漏**：导航按钮此前是 `Component.literal("传送")` 这类硬编码中文，英文客户端会直接显示中文。现已全部改用 `gui.infinitestats.nav.*` 语言键。

### ⚙️ 随身工作台优化

- **倍率按钮支持批量调整**：按住 **Shift** 点 `+` / `-` 一次调整 **10 级**。`CraftingMultiplierPacket` 增加 `amount` 字段（上限 10），服务端按「点数够几级就升几级」批量结算，只弹一条汇总提示；连一级都升不动时才提示点数不足。
- **自动补料扩展到玩家背包**：原先只有玩家连着存储网络（RS / AE2 / 汤姆存储 / 背包等）时才自动补料，否则取一次成品后网格就空掉、每次都得手动重摆。现在**先向网络要，要不够的差额再从玩家背包补足** —— 一个网络都没连也能连续制作。关闭界面时网格余料仍照旧退回网络 / 背包，不会丢物品。
  - 补料口径与配方一致：只补「取出前存在、取出后正好少掉的那部分」，且按 `isSameItemSameTags` 匹配，不会误拉其它同种但不同 NBT 的物品。
- **按钮文案国际化**：成品去向按钮的「包」/「储」原为硬编码中文，改用 `gui.infinitestats.crafting.output_bag` / `output_storage`。

- **影响范围**：`stats/StatType`、`network/NetworkHandler`、`command/ModServerCommands`、`client/StatsScreen`、`client/PortableCraftingScreen`、`crafting/PortableCraftingMenu`，新增 `crafting/PortableAnvil`；中英文语言文件同步（含 10 条导航键与铁砧属性文案）。

## [1.16.1] - 2026-10-01

### 🏹 「无限弓箭」补齐弩

- **背景**：`infinite_arrows` 此前只作用于**弓**，弩完全没覆盖（1.16.0 的说明里也写着「未覆盖：弩」）。玩家拿弩时会发现开关"失灵"。
- **为什么不能照搬弓的做法**：弓是在 `ArrowLooseEvent` 里补一支箭；弩的发射完全依赖装备自身的 `ChargedProjectiles`（原版 `performShooting` 只遍历这个列表），**在发射阶段插手已经太晚**，必须往前挪到「装填」。
- **原版卡在哪两处**：

  ```
  CrossbowItem.use()
    ├─ isCharged(stack) → performShooting            ← 想走这里，得先 charged
    ├─ else if (!player.getProjectile(stack).isEmpty()) → startUsingItem
    └─ else return fail                               ← 空手连「架弩」都做不到

  CrossbowItem.releaseUsing()
    ├─ f = getPowerForTime(已蓄力 tick, crossbow)
    └─ if (f >= 1.0F && !isCharged(stack) && tryLoadProjectiles(...))   ← 空手时第三个条件挂掉
  ```

  `tryLoadProjectiles` 取的是 `player.getProjectile(crossbow)`，空手时为 `EMPTY`，直接返回 `false`，弩永远装不上。
- **落点（两步，与弓同构但钩子不同）**：

  1. **起手** —— 扩展现有的 `PlayerInteractEvent.RightClickItem`：手持弩、未装填、背包无箭且已解锁时，自己 `startUsingItem` 并取消事件返回 `CONSUME`；已装填的右键是「发射」，原样交回 `performShooting`。
  2. **装填完成** —— 新增 `LivingEntityUseItemEvent.Stop`。Forge 在 1.20.1 的 `LivingEntity` 补丁里把它挂在 `releaseUsingItem()` 中、**正好在 `ItemStack.releaseUsing` 之前**：

     ```java
     if (!ForgeEventFactory.onUseItemStop(this, useItem, getUseItemRemainingTicks()))
        useItem.releaseUsing(this.level(), this, getUseItemRemainingTicks());
     ```

     于是先把箭补进 `ChargedProjectiles` 并 `setCharged(true)`，原版那一步就会因 `!isCharged(stack)` 不成立而整体短路 —— 状态原地保留，一行原版逻辑都不用改。
- **与原版手感一致**：装填是否蓄满沿用原版判据（`getUseDuration - 剩余 >= getChargeDuration`，等价于 `getPowerForTime >= 1.0F`，提前松手同样不装填）；虚拟箭数量跟随**多重射击**附魔取 1 / 3 支；原版挂在 `releaseUsing` 成功分支里的装填完成音效由模组补播。
- **不碰背包**：写进 `ChargedProjectiles` 的是全新构造的 `ItemStack`，既不读也不扣玩家背包。
- **顺手修掉一个会误伤弩的隐患**：`ArrowLooseEvent` 原先对所有武器生效，而弩发射时原版 `performShooting` 也会以 `charge=1 / hasAmmo=true` 触发它 —— 若玩家背包无箭，旧代码会把这次事件取消掉，**整发弩箭直接射不出去**。现在弩在该处理器里提前分流，只「记录 + 放行」。
- **防刷箭**：原版 `shootProjectile` 只对「创造模式」或「多重射击第 2、3 支」设 `CREATIVE_ONLY`，生存玩家的第一支仍可拾取；虚拟箭白白得来，落地被捡回就等于凭空刷箭。因此由 `ArrowLooseEvent` 记下「发射那一刻背包无箭」的玩家与 tick，`EntityJoinLevelEvent` 据此把同一 tick 生成的弩箭改成 `CREATIVE_ONLY`。判定取的是**发射瞬间**而非实体生成之后，所以「多重射击把最后一支箭消耗掉」的真箭不会被误伤。
- **两端收敛**：NBT 改写与拾取权限都只在服务端执行（1.20.1 的 `performShooting` / `EntityJoinLevelEvent` 两端都会触发），避免客户端产生分叉状态。
- **影响范围**：`event/StatEventHandler` —— `onRightClickItem` 增加弩分支、`onArrowLoose` 增加弩分流、新增 `onUseItemStop` / `onEntityJoinLevel` / `loadVirtualArrows` / `hasArrowInInventory` / `onPlayerLogout` 与 `VIRTUAL_CROSSBOW_SHOTS` 表；`stats/StatType` 与中英文语言文件同步属性描述。
- **仍未覆盖**：**弩装烟花**（`FIREWORK_ROCKET`）与自定义弩类物品 —— 它们的 `ChargedProjectiles` 非空，模组不会介入，行为保持原版。

## [1.16.0] - 2026-10-01

### ✨ 新增「无限弓箭」（攻击分类，5 点解锁）

- **效果**：投入 5 点解锁后，**背包里一支箭都没有也能拉弓蓄力射击**，射出的箭不消耗背包。
- **实现要点**（原版没有现成钩子，需要两步接管）：

  1. **开始蓄力** —— 原版 `BowItem.use` 先判断 `player.getProjectile(bow)` 是否为空，为空直接返回 `fail`，玩家**连拉弓动作都做不出来**：

     ```
     BowItem.use()
       ├─ flag = !player.getProjectile(bow).isEmpty()
       ├─ ArrowNockEvent（hasAmmo 是 final 改不了，事件本身也不是 @Cancelable）
       └─ if (!instabuild && !flag) return fail;      ← 卡在这里
     ```

     改用副作用最小的 `PlayerInteractEvent.RightClickItem`（在 `Item#use` **之前**触发且可取消），自己调 `startUsingItem` 启动蓄力，并返回 `CONSUME` 告知两端已处理。

  2. **放箭** —— `ArrowLooseEvent.hasAmmo` 同样是 `final`、没有 setter，而且 `BowItem.releaseUsing` 的判定用的是局部变量 `itemstack = player.getProjectile(bow)`，与事件参数无关：

     ```
     releaseUsing()
       ├─ itemstack = player.getProjectile(bow)         ← 空
       ├─ i = ForgeEventFactory.onArrowLoose(...)       ← 取消后返回 -1
       ├─ if (i < 0) return;                            ← 拦在这里，原版不再继续
       └─ if (!itemstack.isEmpty() || flag) { ... }     ← 本来也进不去
     ```

     因此取消该事件，由模组按原版规则自行补一支箭 —— 这是唯一可行的落点。
- **不碰背包**：补的箭是**独立新建的 `ItemStack`**，既不读也不改背包 —— 不会扣掉玩家装的箭，也不会凭空塞进物品栏。拾取权限沿用原版「无限附魔」的做法设为 `CREATIVE_ONLY`，否则落地的箭被捡回等于无限刷箭。
- **手感与原版一致**：蓄力曲线、暴击阈值、力量 / 冲击 / 火矢附魔加成、弓的耐久损耗均复刻 `releaseUsing`。
- **条件一致性**：两步的判定（创造模式 / 背包有箭 / 是否解锁）统一走 `hasInfiniteArrows`，避免出现「拉得起来但射不出去」的半残状态。
- **未覆盖**：**弩**。`CrossbowItem` 的装填走 `getChargedProjectiles` / `setCharged` 另一套逻辑，需要单独处理，本次未做。

### 🌏 内置 30+ 模组的外部属性中文翻译

- **背景**：「外部属性」分类会自动收录其他模组注册的属性，显示名取自属性自带的 `getDescriptionId()`。但不少模组**自己都没提供中文译文**，于是 GUI 里直接显示成 `[gunsmithlib:bullet_damage]` 这样的原始键名。
- **此前的问题**：模组会把这些「缺译文」的属性列进 `config/infinitestats/external_translations.json`，但**没有任何代码读回它** —— 在整合包里手填既不会生效，还会被下次启动时的 `Files.writeString` 整file覆盖。
- **现在**：把 246 条译文**直接内置进模组语言文件** `assets/infinitestats/lang/zh_cn.json`。

  - MC 的语言表是**跨 namespace 按 key 合并**的，所以 `infinitestats` 的 lang 里放别的模组的键照样生效；而这些键在原模组处本来就不存在，不会产生覆盖冲突。
  - 写在模组 jar 里意味着**装包即用**，所有玩家都能看到中文，不需要每个整合包各填一份。
  - 覆盖模组：`gunsmithlib`、`attributeslib`、`goety` / `goety_revelation` / `goeticlegacy`、`taa`、`touhou_little_maid`、`wizard_terra_cuiros`、`l2damagetracker`、`celestial_core`、`flame_chase_artifacts`、`additional_attributes`、`terra_entity`、`ending_library`、`cataclysm`、`apotheosis_modern_ragnarok`、`reach-entity-attributes`、`qiyuemod`、`blessingofpandora`、`curseofpandora`、`fallen_gems_affixes`、`tacz`、`tcc`、`alexscaves_torpedoes`、`ftbultimine`、`caelus`、`vengeance`、`slashblade`、`block_factorys_bosses`、`l2hostility`、`monsterexpansion`、`confluence`、`sal_fishs_attribute_lib` 等。
- **`external_translations.json` 不再被覆盖**：该清单现在只在**文件不存在**时生成一次，作者手填的补充内容不会再被冲掉，仍可作为「还有哪些新属性没译文」的参考。
- **文档同步**：`无限加点模组介绍.txt` 更新了外部属性一节的说明，并移除了早已删除的「魔法」属性列表。

### 🔧 改进「弹射物追踪」的目标筛选

- **以前**：在配置半径内挑一个**离弹射物最近**的敌人就把速度转过去 —— 不看方向、也不看中间有没有墙，于是会追到墙后面的怪，或者扭头去追玩家背对着的怪。
- **现在**改成两级筛选：
  1. **隔着方块的直接排除** —— 用 `player.hasLineOfSight` 判断玩家与目标之间是否被方块挡住，挡住的不追（追过去也只会撞墙）；
  2. **视野锥内的优先** —— 同样看得见的目标里，落在玩家前方约 **±60°** 视锥内的先选；视锥内一个都没有时，才退回到视锥外但仍可见的目标。
- **性能**：视线与视锥判定都放在弹射物循环**之外**、每个敌人只算一次，不随弹射物数量重复计算；`hasLineOfSight` 自带 128 格上限与一次方块射线，开销可控。原有「配置半径扫描 + 每 4 tick 节流」不变。
- **影响范围**：`handler/UtilityHandler.applyProjectileTracking`，新增 `isInPlayerViewCone` / `closestTo` 两个辅助方法与 `VIEW_CONE_COS` 常量；属性描述文案同步更新。

## [1.15.0] - 2026-10-01

### 🔥 移除「魔法」属性分类

- **移除内容**：整个 MAGIC 分类及其 6 条属性 —— 最大法力值、法力恢复、魔法伤害、法力护盾、法力窃取、击杀回蓝。
- **移除原因**：这套法力系统在实际使用中不成体系：
  - 法力**只有「法力护盾」一个消耗口**。不点护盾时，其余 4 条产蓝属性（最大法力值 / 法力恢复 / 法力窃取 / 击杀回蓝）全是无用投入 —— 蓝只涨不花；
  - 「魔法伤害」的判定依赖 `infinitestats:magic` 伤害类型标签，只覆盖原版 `magic` / `indirect_magic` / `dragon_breath` / `wither` 与可选的 `#forge:is_magic`。整合包里的魔法模组若不注册该标签就完全不生效；
  - 「法力窃取」与已修复的吸血同源（在 `LivingHurtEvent` 里按面板伤害结算），数值虚高。
- **点数退还**：旧存档（含整合包内现存存档）里投入魔法属性的点数**在加载时全额退还到可用点数**，玩家不亏。
  - 实现位于 `PlayerStats.deserializeNBT`，且**必须置于 `StatType.fromId` 判断之前** —— 属性定义删除后 `fromId` 返回 `null`，条目会被直接丢弃，点数就白丢了；
  - 退还按绝对值累计，负值（透支方向）同样返还；逻辑幂等，重复加载不会重复退还。
- **涉及改动**：
  - `stats/StatCategory`（删 `MAGIC`）、`stats/StatType`（删 6 条定义）、`stats/PlayerStats`（删 `currentMana` 字段与 `getCurrentMana` / `setCurrentMana` / `getMaxMana`，NBT / `StatsSnapshot` / `copyFrom` / `restoreFromSnapshot` 同步清理，新增 `LEGACY_MAGIC_STATS` 常量与退还逻辑）
  - 删除 `handler/MagicHandler` 及 `HandlerRegistry` 中的注册
  - `handler/AttackHandler`（删 `MAGIC_DAMAGE` 标签、`calculateMagicBonus`、`applyManaSteal`）
  - `event/StatEventHandler`（删魔法增伤、法力窃取、法力护盾、击杀回蓝四处调用）
  - `network/SyncStatsPacket`（删 `currentMana` 字段，编码 / 解码 / 构造 / 快照四处同步调整）
  - UI：`StatsScreen`（删 6 个属性图标与进度条下方的法力条）、`StatsHudOverlay` / `HudEditScreen`（删 HUD 法力条并同步面板高度计算）
  - 资源：删除 `data/infinitestats/tags/damage_type/magic.json`；中英文语言文件移除 `category.infinitestats.magic` 与 12 个 `stat.infinitestats.*` 键
  - 配置：删除 `MANA_REGEN_INTERVAL`
  - 测试：`RegressionTests` 移除魔法伤害标签断言，`configuredRegenerationIntervals` 精简为只覆盖生命恢复间隔
- **界面变化**：顶部标签栏由 6 个减为 5 个（攻击 / 防御 / 机动 / 功能 / 外部属性），标签宽度按分类数量自动重新均分，无需手工调整。

### ⚖️ 调整默认配置数值

按整合包实测节奏上调三个基础参数：

| 配置项 | 原默认 | 新默认 | 说明 |
|---|---|---|---|
| `pointsPerLevel` | 3 | **10** | 每次升级获得的属性点数 |
| `healthRegenInterval` | 100 | **20** | 生命恢复间隔（tick），即 5 秒 → 1 秒 |
| `passiveXpInterval` | 80 | **20** | 被动经验获取间隔（tick），即 4 秒 → 1 秒 |

- ⚠️ **只影响新生成的配置文件**。Forge 不会覆盖已存在的 `config/infinitestats-*.toml`，装过旧版本的整合包需**手动改值或删掉配置文件重新生成**，否则仍是旧默认。

## [1.14.2] - 2026-10-01

### 🐞 修复「取消无敌帧」在目标带有他人无敌帧时完全失效

- **问题**：目标身上带着**其他来源**设置的无敌帧时（被别人打、被别的怪打、着火、中毒都会把 `invulnerableTime` 重设为 20），玩家这一击会被原版 `hurt()` 的无敌帧检查直接丢弃 —— 表现为"开了这个开关还是每 0.5 秒才掉一次血"。
- **原因**：清零点写在 `LivingHurtEvent` 里，而该事件触发在无敌帧检查**之后**：

  ```
  hurt()
    ├─ ForgeHooks.onLivingAttack(...)
    ├─ if (invulnerableTime > 10) return false;      ← 被吞，后续事件全都不触发
    └─ else { invulnerableTime = 20; actuallyHurt() → LivingHurtEvent }
  ```

  一旦在上面被 `return false` 丢弃，连补救的机会都没有；原有的清零只能覆盖"连续攻击同一目标"这种理想情况。
- **修复（三层覆盖，且全部只作用于"玩家自己打出的伤害"）**：

  | 层 | 时机 | 作用 |
  |---|---|---|
  | `AttackEntityEvent` | `Player.attack()` 里、施伤之前 | 最早；覆盖"在 `hurt()` 之前自查无敌帧并跳过"的实现 |
  | `LivingAttackEvent` | `hurt()` 内、无敌帧检查之前 | 覆盖投射物等全部路径，并挡住"反复重设无敌帧" |
  | `LivingHurtEvent` | 结算过程中（原有） | 兜底，并为下一次攻击铺路 |

  无论对方在什么时候重复添加无敌帧（受伤后、结算后、自己的 tick 里），玩家下一次攻击的一开始就会被清零。
- **刻意不采用的做法**：每 tick 扫描附近实体持续清零。那会把目标的无敌帧长期压成 0，变成**全局状态** —— 别的玩家、别的伤害来源、怪物之间互殴都会一起受益，等于给全服开挂，也背离该属性"让我的攻击更连贯"的语义。
- **影响范围**：`event/StatEventHandler`（新增 `onLivingIncomingDamage` 与 `onAttackEntity`）。

## [1.14.1] - 2026-10-01

### 🔧 「冷却缩减」上限由 80% 放宽到 100%

- **改动**：`cooldown_reduction` 的封顶值从 `-80%` 提到 `-100%`（100 点即可点满）。
- **满额走的是另一条路径**：100% 的语义是「零冷却」，用加速实现不了 ——
  - 额外流速公式是 `r/(1-r)`，`r = 1` 时**除零**；
  - 而且无论把计时器推多快，结果都只是"接近零"而不是真的零。

  所以满额时改为**直接清除**玩家身上的冷却（遍历主栏 + 护甲 + 副手约 41 格调用 `removeCooldown`），未满额时仍走原来的等比加速。
- **顺手收敛逻辑**：两端合流为 `CooldownHandler.apply(player, stats, reduction)`，服务端与客户端调用同一个方法，避免以后再出现两边算法漂移。
- **已知局限**：满额时若某个物品不在玩家身上（刚丢出去、放进箱子），它的冷却不会被清除 —— `ItemCooldowns` 没有公开的「列出全部冷却」接口，只能按玩家物品栏来清。
- **影响范围**：`handler/CooldownHandler`、`client/ClientEventHandler`、`stats/StatType` 描述、`zh_cn` / `en_us` 文案。

## [1.14.0] - 2026-10-01

### ✨ 新增「冷却缩减」属性 —— 缩短物品冷却时间

- **新属性**：`cooldown_reduction`（功能类），每点 **-0.5%** 冷却时间，**硬上限 -80%**。100 点即可把末影珍珠、三叉戟、盾牌以及各类技能物品的冷却压到只剩原本的 20%（等效 5 倍速）。
- **上限为什么是 80%**：100% 等于零冷却，末影珍珠 / 三叉戟 / 盾牌会变成无限使用，可能刷物品或卡住 AI，因此在 Handler 里强制封顶，与玩家堆多少点无关。
- **实现**：物品冷却完全由 `ItemCooldowns` 的公开方法 `tick()` 驱动（每次调用推进一次内部计时器并清理到期冷却），所以「每 tick 多调几次 `tick()`」就等于等比加速全部冷却。
  - **两端一起加**：客户端也持有自己的一份冷却（`LocalPlayer`），只加速服务端会导致"客户端认为还在冷却而拦住使用"（表现为右键点了没反应），因此 `ClientEventHandler` 里跑同一套算法，保证步调一致。
  - **倍率换算**：属性语义是「冷却时间减少的百分比」，剩余冷却 = 原时长 × (1 - reduction)，所以计时器需要跑到 `1/(1-reduction)` 倍速，即额外流速为 `reduction/(1-reduction)`。小数部分累加，避免低点数时不触发（与时间加速同一套写法）。
- **关于 Mixin（重要结论）**：原计划用 Accessor Mixin 直接改 `ItemCooldowns.tickCount`，实施时确认 —— **ForgeGradle 6 在 official mappings 下不会自动生成 Mixin refmap**：注解处理器只支持 `searge/notch` 环境，编译期报 `Unable to locate obfuscation mapping for @Accessor target tickCount`，且 refmap 文件根本不会产出，若硬上会在生产环境（SRG 名）找不到字段而崩溃。既然公开的 `tick()` 已经够用，最终**没有引入 Mixin**，本项目保持零 Mixin 依赖。
- **影响范围**：新增 `handler/CooldownHandler`；`stats/StatType`（新属性）；`stats/PlayerStats`（冷却累加器）；`handler/HandlerRegistry`（注册）；`client/ClientEventHandler`（客户端同步）；`client/StatsScreen`（图标）；`zh_cn` / `en_us` 文案。

## [1.13.1] - 2026-10-01

### 💄 效果过滤器：去掉行内的「增益 / 中性 / 负面」文字标签

- **问题**：列表行里同时存在三套类别标识 —— 左侧色条、名称着色、以及行中间的「增益 / 中性 / 负面」文字。文字标签没有带来额外信息，却挤掉约 40px 的名称宽度，长效果名（如 `twilightfinalexpansion:moon_boost` 这类）被截断到几乎看不见。
- **修复**：移除行内类别文字标签，名称直接占满「勾选图标 → ID」之间的空间；类别仍由左侧色条与名称着色体现，悬停浮窗里的「类别: xxx」保留，ID 截断宽度由 150px 收到 140px 让位给名称。
- **影响范围**：`client/DebuffFilterScreen.drawList`。

## [1.13.0] - 2026-10-01

### ✨ 外部属性自动获取翻译，不再需要手工逐条补

- **背景**：其他模组注册的属性会被自动发现并归入「外部属性」分类，但它们的翻译键此前是本模组自建的 `stat.infinitestats.attr.<命名空间>.<路径>`。模组的语言文件里当然没有这个键，于是只能靠人一条条往 `external_translations.json` 里填。
- **做法**：外部属性改为直接把**属性自身**的 `Attribute#getDescriptionId()` 当作翻译键 —— 这个键正是模组在游戏内显示属性名时使用的键，其语言文件里通常已带中文/英文译文，因此立刻可用，零手工维护。
- **回退**：万一某个属性连它所属的模组都没提供译文，客户端会回退成「命名空间: 可读路径」（例如 `forge: reach distance`），不会再出现 `attribute.name.xxx` 这种原始键名。
- **搜索**：属性搜索现在同时匹配显示名、说明、内部 ID 与属性注册名，可以直接搜 `forge`、`attributeslib` 之类找出某个模组带来的全部属性。
- **清单**：`config/infinitestats/external_translations.json` 改为**只列出真正缺译文**的属性；若所有外部属性都有自带译文，则不再生成该文件。启动日志也会打印 `N/M auto-translated from their own mods`，方便确认命中率。
- **影响范围**：`stats/StatType`（发现逻辑 + 模板生成 + 启动统计）、`client/StatsScreen`（显示名回退、搜索匹配）。

## [1.12.0] - 2026-10-01

### 🎨 重写「效果过滤器」界面：增益 / 中性 / 负面三分类配色 + 效果说明

**分类配色**

- 按原版 `MobEffectCategory` 把效果分为**增益 / 中性 / 负面**三类，用「左侧色条 + 名称着色 + 类别标签」三重标识：增益绿、中性琥珀、负面红；未注册的自定义 ID 用灰色。
- 新增**类别筛选**：搜索框右侧加入 全部 / 增益 / 中性 / 负面 四个按钮，激活项以下划线高亮为对应类别色，可与搜索关键字叠加使用。

**效果说明**

- 悬停条目时在浮窗中显示该效果的**作用说明**（自动折行，中英文通用）。
- 取值顺序：先查通用约定键 `effect.<命名空间>.<路径>.desc` —— 任何模组或资源包按此格式提供即可自动生效；再回退到本模组内置的 `infinitestats.effect.<命名空间>.<路径>`。
- 已内置原版全部 33 种效果的说明文案；搜索框现在也会匹配说明内容（例如搜「失血」可以同时找到中毒与凋零）。

**布局与交互修复**

- 修复旧版搜索框位于 `topPos + 6`、与 24px 高的标题栏同层，导致标题文字和右侧模式标签被搜索框压盖的问题；标题栏独立成行，模式按钮移到标题栏右侧并显示当前模式（`模式: 黑名单`）。
- 统一到 `EditorUi` 规范（配色 / 面板 / 分隔线 / 滚动条 / 浮窗），移除全项目唯一一套私有控件 `PixelButton`、`HintLabel`；tooltip 从原版灰色框改为统一浮窗。
- 底栏重构：左侧 清空 / 全选，中间显示「拦截选中 N 项」（黑名单红字 / 白名单绿字），右侧 完成。新增的「全选」作用于当前筛选结果，配合类别筛选即可一键拦截全部负面效果。
- 滚动条拖动改用与绘制一致的几何计算；键盘滚动只占用方向键（旧版同时占用 W/S，与搜索框输入冲突）；自定义 ID 先做 `ResourceLocation` 校验再入库。
- **影响范围**：重写 `client/DebuffFilterScreen`、`client/EditorUi`（新增折行工具）；`zh_cn` 新增 45 条、`en_us` 新增 49 条文案（含 33 条效果说明与此前缺失的 4 条 `custom_*` 文案）。

## [1.11.1] - 2026-10-01

### 🐞 修复「选择属性」界面底部控件重叠与多余冒号

- **问题**：「选择属性」界面底部，「返回」按钮与「槽位」下拉框**完全重叠**（两者 x 坐标相同）；「操作」「槽位」下拉框前还多出一个冒号，显示成「: 操作: 加算」。
- **原因**：
  - 槽位下拉框与返回按钮都从 `leftPos + GAP` 起排，宽度分别为 118 / 100，前者把后者完全盖住；
  - `CycleButton` 会把 `name` 参数与当前值渲染成 `name: value`。新代码把「操作: 」直接拼进了值里、`name` 传了 `Component.empty()`，于是渲染成「: 操作: 加算」。
- **修复**：
  - 槽位下拉框改从返回按钮右侧起排（`leftPos + GAP + 返回按钮宽 + GAP`）；
  - 下拉框标签改由 `CycleButton` 的 `name` 参数提供（`item_editor.op` / `item_editor.slot`），值只保留纯选项文本；
  - 列表下方补一条分隔线，输入区整体下移 4px，缓解固定 9 行后底部偏挤的问题。
- **影响范围**：`client/ItemEditSelectScreen`。

## [1.11.0] - 2026-10-01

### 🎨 重写「物品编辑器」整套界面（主编辑 / 条目选择 / 元数据编辑）

三处界面的布局、绘制与交互全部推翻重做，并抽出共用的视觉规范类 `client/EditorUi`（配色 / 尺寸 / 面板 / 分隔线 / 滚动条 / 浮窗集中管理），三处风格与间距统一，不再各自维护一套魔法数字。

**主编辑界面 `ItemEditorScreen`（400×340 → 420×308）**

- 新增顶部**物品预览条**：主手物品图标、名称、堆叠数量、耐久条（按剩余比例变色）与物品 ID；
- 两栏**行数一致**（各 7 行）：修复旧版左栏 6 行 / 右栏 5 行导致底部不对齐、右侧空出 26px 的问题；
- 每栏独立**搜索框**（模糊匹配显示名与 ID），栏头显示「命中/总数」，无结果时给出占位提示；
- 每栏独立**滚动条**，带滑块比例与悬停高亮；
- **拖拽排序**：按住条目上下拖动即可调整顺序（移动阈值 4px，与单击编辑区分开）；
- **批量操作**：每栏底部提供 添加 / 复制 / 粘贴 / 清空，剪贴板在同一会话内保留，粘贴遇到同 ID 条目自动合并；
- 删除按钮由 14×14 的 `x` 字符放大为 16×16 方块，悬停变红；
- 带扩展 NBT 的附魔条目以 `+` 标记；
- 布局坐标全部收敛为顶部常量（`ITEM_BAR_Y` / `SECTION_Y` / `SEARCH_Y` / `LIST_Y` / `TOOLBAR_Y` / `FOOTER_Y`）。

**条目选择界面 `ItemEditSelectScreen`（360×320 → 420×320）**

- 列表高度**固定 9 行**，不再随选中状态伸缩，切换选中时界面不再跳动；
- **编辑已有条目时保留候选列表**，可直接改选成别的条目（旧版编辑附魔会隐藏整张列表）；
- **新增「编辑属性」支持**（旧版只能编辑附魔）；
- 选中行高亮 + 左侧强调色条；操作 / 槽位下拉框自带「操作: 」「槽位: 」前缀，避免与数值混淆；
- 未选条目、数值非法、扩展 NBT 格式错误均给出明确的底部提示；
- **回车即确认**。

**元数据编辑界面 `EditItemMetaScreen`（固定 300 → 高度自适应）**

- 面板高度随 Lore 行数自适应，不再把控件挤出面板；
- Lore **每行自带删除按钮**，可删除任意一行（旧版只能删末行），并新增「+ 添加行」；
- Lore 行数上限 8 行，超出时给出提示而不是静默失败；
- 同样带物品预览条，与主界面保持一致。

**影响范围**：新增 `client/EditorUi`；重写 `client/ItemEditorScreen`、`client/ItemEditSelectScreen`、`client/EditItemMetaScreen`；`zh_cn` / `en_us` 各新增 21 条界面文案。

## [1.10.1] - 2026-10-01

### 🐞 修复「生命偷取 / 范围吸血」按面板伤害结算导致的虚高与幻影回血

- **问题**：
  - 打高护甲 / 高抗性 / 带「单次伤害上限（限伤）」的目标时，实际只掉几点血，却能按未削减的面板伤害吸血，甚至一击回满；
  - 攻击被格挡、闪避或被无敌取消、完全没造成伤害时，玩家照样回血（「幻影回血」）；
  - 范围吸血同理：周围敌人护甲 / 免疫挡掉的伤害也计入回血。
- **原因**：`生命偷取` 在 `LivingHurtEvent` 里用 `amount` 计算——此时既没过护甲、也没过减伤，只是攻击加成后的面板值；范围吸血则用「打出去的 AOE 伤害」而非「敌人实际掉血」计算。
- **修复**：
  - `life_steal` 改到 `LivingDamageEvent`（LOWEST）结算，用护甲 / 减伤结算后的实际伤害作为基数。该分支在事件被取消时提前返回，因此被格挡 / 闪避 / 无敌取消的伤害不再吸血；次级直接伤害（范围攻击 / 真伤 / 降上限）不属于玩家主动攻击，不计入吸血；
  - `life_steal_aoe` 改为按每个周围敌人的**实际被承受伤害（含护盾消耗）**累计回血，被削减或完全免疫的部分不再计入；
  - 新增 `StatEventHandler.getPlayerAttacker(DamageSource)` 重载，供伤害结算阶段反查攻击者。
- **影响范围**：`event/StatEventHandler`（`onLivingHurt` 移除吸血调用、`onLivingDamage` 新增吸血结算、`getPlayerAttacker` 重载）、`handler/AttackHandler.applyAoeLifeSteal`。

## [1.10.0] - 2026-10-01

### 🎨 重写「矿石优先顺序」设置界面

界面的布局、绘制与交互全部推翻重做，`FurnaceOrePriorityScreen` 整体重写（旧实现约 460 行 → 新实现按「布局常量 / 状态 / 输入 / 三层绘制」重新组织）。

**布局（面板由 300×240 扩至 320×258）**
- 五段式结构：标题栏 → 待炼仓矿石条 → 双栏列表（分区标题 + 列表）→ 分隔线 → 物品栏；
- 列表可见行数由 **3 行提升到 5 行**，两栏各占更宽的横向空间（左栏 `10~152`、右栏 `162~304`），名称截断宽度同步放宽（左 52px / 右 92px）；
- 各段之间加入统一分隔线，标题栏高度固定 24px，标题 + 副标题垂直分层，不再相互挤压。

**绘制分层（修复旧实现的层级问题）**
- 旧实现在 `render` 中手动 `renderBackground` 并在父类渲染后补齐内容，导致背景/标题重复绘制、自绘内容可能盖住槽位 tooltip；
- 新实现严格分层：`renderBg` 只画底板、列表底、悬停高亮、拖拽指示线与背包槽位（绝对坐标）；`renderLabels` 画全部文字、图标与自绘按钮（父类已平移坐标系，使用相对坐标）；`render` 在父类渲染之后仅补画拖拽残影与自定义 tooltip。

**交互**
- **拖拽排序**：左栏行按下后可直接上下拖动重新排序，拖动过程中显示跟随鼠标的残影、源行半透明占位，并在落点位置绘制金色插入指示线（`mouseDragged` / `mouseReleased` 已重写，拖拽期间不再透传给容器槽位逻辑）；
- **自绘按钮**：`↑` `↓` `✕` `＋` `清空` 全部改为自绘（带悬停高亮与禁用态），不再每帧增删 `Button` widget，消除了重建控件带来的开销与闪烁；
- **新增「清空」按钮**：一键清空优先顺序（列表为空时自动置灰）；
- **悬停高亮**：鼠标悬停所在行整行高亮；
- **空状态提示**：优先列表为空提示「点击右侧矿石加入」，右栏无结果提示「无匹配矿石」，待炼仓为空提示「（空）」；
- **仓内矿石标识**：右栏中待炼仓已有存货的矿石改用金色名称并对图标加 1px 金色描边（同时保留待炼仓条上的 `✔` 已加入标记）；
- **搜索过滤缓存**：`filteredAvailable()` 由「每次调用都全量过滤」改为在搜索内容 / 同步数据变化时重建缓存，避免每帧重复遍历数百条矿石；
- 其余交互保持并优化：点击右栏行 / 顶部待炼仓矿石 / 背包内可熔炼物品均可加入；滚轮按所在列独立滚动；tooltip 现在显示显示名 + 优先序号 / 仓内存货状态 / 拖拽提示。

**配套改动**
- `FurnaceOrePriorityMenu` 背包槽位坐标随面板调整：`INV_X 69 → 79`、`INV_MAIN_Y 160 → 176`、`INV_HOTBAR_Y 218 → 236`；
- 新增中英文语言条目：`clear`、`empty_priority`、`empty_available`、`empty_reserve`、`tip_priority`、`tip_reserve`、`tip_amount`、`tip_drag`，并更新 `hint` 文案。
- **影响范围**：`client/FurnaceOrePriorityScreen`（整体重写）、`furnace/FurnaceOrePriorityMenu`（槽位坐标）、`zh_cn` / `en_us` 文案。
- 网络协议未变更，与 1.9.22 存档 / 客户端无兼容问题。

## [1.9.22] - 2026-10-01

### 🐞 修复「真实伤害」被限伤 / 抗性提升 / 吸收护盾削减

- **问题**：给「真实伤害」加点后，遇到带「单次伤害上限（限伤）」的目标、或高抗性提升 / 保护附魔 / 吸收护盾的目标时，真伤被大量削减甚至完全无效。
- **原因**：真伤此前是叠加进 `LivingDamageEvent.getAmount()` 与主伤害合并结算，因此它仍在伤害管线内，凡是在其之后处理该数值的环节都会连带削掉真伤：
  - 原版 `actuallyHurt` 在 `LivingDamageEvent` **之后**才执行 `getDamageAfterMagicAbsorb`（抗性提升、保护附魔）与吸收（absorption）结算——1.9.19 说明中「抗性、附魔与吸收均已结算」的判断有误，实际只有护甲已结算；
  - 同优先级（LOWEST）注册顺序靠后的监听器（各类「限伤」实现）会对 `amount` 再 clamp 一次；
  - 直接 Mixin `actuallyHurt` 的限伤实现，事件层完全拦不住。
- **修复**：真伤改为**直接扣血**结算，完全脱离伤害管线：
  - `LivingDamageEvent`（LOWEST）不再叠加 `event.getAmount()`，改为调用新增的 `AttackHandler.applyUnavoidableTrueDamage` 直接 `setHealth` 扣血——无视护甲、抗性提升、保护附魔、吸收护盾、无敌帧与一切限伤；
  - 死亡、掉落与击杀归属仍由原版收尾：原版 `actuallyHurt` 结尾的 `setHealth(getHealth() - f3)` 读取的是当前血量，提前扣掉的血不会丢失，紧随其后的 `getHealth() <= 0` 判定照常触发图腾检查与 `die(playerSource)`；
  - 主伤害被完全减免时原版会在 `LivingHurtEvent` 后提前 return、不走死亡判定，此时真伤最多扣到 0.01，避免产生「0 血存活」的实体；
  - 仍保留对「完全免疫」的尊重：创造模式 / 无敌目标不产生伤害事件，被格挡、闪避或无敌取消（`event.isCanceled()`）的伤害不结算真伤。
- **测试**：`trueDamageCancellationAndIsolation` 断言改为校验目标血量（不再检查 `event.getAmount()`）；`damageTypesAndPenetration` 新增直接扣血路径覆盖（抗性 + 吸收 + 无敌帧 + 零血保护）。
- **影响范围**：`handler/AttackHandler`（新增 `applyUnavoidableTrueDamage`）、`event/StatEventHandler.onLivingDamage`、`gameTest` 两个测试文件。

## [1.9.21] - 2026-09-26

### 🔄 回滚「飞行」开关为原版创造飞行手感

- **改动**：移除 1.9.20 新增的客户端 `ClientEventHandler#maintainFlight`（按跳跃自动起飞、空中持续维持 `flying`、落地后可再次按跳跃起飞）。「飞行」开关现在只负责授予 `mayfly`，飞行状态完全交回原版客户端逻辑：**双击跳跃开启/关闭飞行，落地自动退出飞行，再次双击继续飞**，与创造模式完全一致。
- **保留**：
  - 服务端 `MobilityHandler#updateFlight` 仍只在 `mayfly` / 提供状态真正变化时才同步能力，不再每 5 tick 无条件发包覆盖客户端飞行状态；
  - `mayfly` 期间不摔伤（与原版 `Player#causeFallDamage` 的 `abilities.mayfly` 判断一致，非本模组额外行为）；
  - `auto_step` / `step_height` 的客户端 `syncStepHeight` 同步不受影响。
- **文案**：「创造模式飞行」描述恢复为原版操作说明（双击跳跃开启/关闭飞行）。
- **影响范围**：`client/ClientEventHandler`（删除 `maintainFlight`）、`handler/MobilityHandler#updateFlight`（注释/说明）、`zh_cn` / `en_us` 文案。

## [1.9.20] - 2026-09-26

### 🐞 修复「飞行」开关起飞后落地即掉飞、必须反复双击跳跃

- **问题**：给「飞行」加点后（服务端已授予 `mayfly`），生存模式下仍要双击跳跃才能进入飞行，且脚一沾地飞行立刻被取消。
- **原因**：原版非创造/旁观的飞行状态由客户端主导——`LocalPlayer#aiStep` 只在 `gameMode.isAlwaysFlying()` 时自动保持 `flying`，否则需要"双击跳跃"切换，并在 `onGround()` 时强制把 `flying` 重置为 `false`；而服务端 `MobilityHandler.updateFlight` 每 5 tick 无条件 `onUpdateAbilities()`，会在客户端刚起飞时把 `flying=false` 再次发回，造成抖动/掉飞。
- **修复**：
  - 客户端 `ClientEventHandler.onClientTick` 新增 `maintainFlight`：飞行开关激活且玩家处于可控空中状态（非骑乘、非鞘翅滑翔、非水中/岩浆/梯子）时，自动把 `flying` 补回 `true` 并同步服务端，形成"按跳跃起飞 → 持续飞行 → 按 Shift 降落"的完整飞行模式；落地后按跳跃即可再次起飞。
  - 服务端 `MobilityHandler.updateFlight` 改为仅在 `mayfly`/提供状态真正变化时才发包，不再覆盖客户端主导的飞行状态。
- **影响范围**：`client/ClientEventHandler`（新增 `maintainFlight`）、`handler/MobilityHandler#updateFlight`。

### 🐞 修复「自动跨越 / 跨越高度」客户端不生效、上台阶被卡住

- **问题**：开启 `auto_step`（或加 `step_height`）后台阶仍然上不去，走到台阶前会被卡住/被服务端拉回。
- **原因**：台阶高度由客户端本地碰撞参与移动预判（`Entity#collide` 读取 `maxUpStep`），旧实现只在服务端 `setMaxUpStep`，客户端仍按默认 0.6 计算，本地预判失败。
- **修复**：客户端 `ClientEventHandler.onClientTick` 新增 `syncStepHeight`，按与服务端 `MobilityHandler#updateStepHeight` 完全一致的公式（`0.6`，`auto_step` 时 `1.0`，再乘 `1 + step_height`）同步本地 `maxUpStep`。
- **影响范围**：`client/ClientEventHandler`（新增 `syncStepHeight`）。

### 📝 文案

- 更新 `zh_cn` / `en_us` 语言文件中「创造模式飞行」「自动跨越」的描述，说明起飞/降落操作与叠加规则。

## [1.9.19] - 2026-09-25

### 🐞 修复「真实伤害」完全没有效果

- **问题**：为「真实伤害」加点后，攻击高护甲/高抗性目标时看不到任何额外掉血，真伤形同虚设。
- **原因**：旧实现 `AttackHandler.applyTrueDamage` 在 `LivingHurtEvent` 里对目标再调用一次 `hurt()`。同一次攻击内目标的无敌帧（`invulnerableTime`）已经生效，嵌套的 `hurt()` 被原版直接拒绝，真伤一点血都掉不下来。
- **修复**：改为两阶段结算，不再嵌套 hurt：
  - `LivingHurtEvent`（LOW）按**减伤前的基数**算出真伤额并暂存（`AttackHandler.calculateTrueDamage` / `rememberTrueDamage`，ThreadLocal + 以「目标 + DamageSource」为键，多目标/次级攻击互不干扰）；
  - 同一次 hurt 的 `LivingDamageEvent`（LOWEST、`receiveCanceled = true`）再把暂存值叠加进 `event.getAmount()`——此时护甲、抗性、附魔、吸收均已结算，真伤全额生效且只结算一次，击杀仍归属原攻击者；
  - 被取消的伤害不叠加；后续模组取消伤害或提前返回留下的暂存值在服务端 tick 结束时清空，不会串到下一次攻击。
- **影响范围**：`AttackHandler`（新增 `calculateTrueDamage` / `rememberTrueDamage` / `takeTrueDamage` / `clearPendingTrueDamage`）、`StatEventHandler.onLivingHurt` / `onLivingDamage` / `onServerTickEnd`。

### 🐞 修复「编辑物品属性会把物品变成白板」

- **问题**：在物品编辑器里改属性（或顺带保存）后，物品自带的攻击力/攻速、原有词缀、UUID 与外部模组 NBT 一起消失，物品变成"白板"。
- **原因**：旧 `EditItemPacket` 把附魔与属性两个列表**整体覆盖重写**。而编辑器打开时只读 NBT 里的 `AttributeModifiers`——普通装备（原版剑/甲等）的属性来自物品内置而非 NBT，列表本来就是空的；点「应用」就把空/不完整的列表写回，并重新生成 UUID 覆盖原词缀 UUID，于是内置属性与扩展数据被抹掉。
- **修复**：新增 `ItemEditUtil` 统一读写：
  - NBT 中没有 `AttributeModifiers` 时，从 `stack.getItem().getAttributeModifiers(slot, stack)` 读取物品**内置属性**作为编辑初始值，不再显示为空；
  - 保存时只发送**实际改动过**的列表（未改动传 `null` = 保持原样），空列表表示「明确清空」；
  - 每条修饰符保留原始 UUID / Name / 扩展字段，附魔保留扩展 NBT（附魔书写入 `StoredEnchantments`）；
  - 写入前校验物品 ID、快捷栏槽位与 NBT 快照（过期编辑直接拒绝），全部在物品副本上修改，成功后刷新容器同步客户端；
  - 显式清空属性时保留空列表标签，避免内置属性"复活"。
- **影响范围**：新增 `util/ItemEditUtil.java`；`EditItemPacket`、`ItemEditorScreen`、`EditItemMetaPacket`、`NetworkHandler`、语言文件。

### 🧪 回归测试

- 新增 `src/gameTest/java/com/infinitestats/ItemAndDamageRegressionTests.java`，覆盖：真伤穿甲/穿抗/不被吸收吸收、取消命中不叠加、多目标隔离、暂存不跨 tick；属性编辑保留内置攻击力/攻速与 UUID、保留附魔与 display/外部 NBT、附魔书标签写入、过期编辑拒绝、清空语义与无效数值拒绝。

## [1.9.18] - 2026-09-22

### ⚙️ 重做「矿石优先顺序」界面并优化交互

- **背景**：原优先顺序界面存在可加入列表过长（全配方扫描、成百上千项）却只能逐行滚动的痛点，且看不到待炼仓（矿石储备箱）里实际有什么矿石，两列共用滚动导致翻找时优先级列表被滚出视野。
- **改动**：
  - 左右两列改为**独立滚动**，并绘制**滚动条**指示当前位置（修复原共用滚动把优先级列表滚出视野的问题）。
  - 新增**搜索框**：按物品名称或注册名实时过滤「可加入」列表。
  - 界面顶部新增**仓内矿石条**：显示待炼仓中的矿石图标与数量（压缩计数），点击即可直接加入优先顺序；悬停显示名称与完整数量。
  - 「可加入」列表中待炼仓已有存货的矿石自动**置顶**（带 ◆ 标记），其余按注册名字典序排列。
  - 优先顺序列表行首显示**序号**（1、2、3…），直观对应“数字越小越优先”。
  - 点击「可加入」列表行本身即可加入，无需再点小按钮。
- **协议**：`FurnaceOrePrioritySyncPacket` 附带待炼仓矿石（id + 数量），网络协议版本由 4 升为 5。
- 影响范围：`FurnaceOrePriorityScreen`（重做）、`NetworkHandler`（SyncPacket/同步逻辑）、语言文件。

## [1.9.17] - 2026-09-19

### 🐞 修复储备箱/缓冲箱界面中「大数量数字被物品挡住」

- **问题**：矿石储备箱（燃料缓冲箱）、产物缓冲箱、便携熔炉的槽位中，物品右下角用白字显示的大数量（如 40、1K）会被物品图标遮住一部分，表现为"数字被物品压住"。
- **原因**：数量文字在 `renderLabels` 阶段用普通 `drawString` 以 Z=0 绘制，而物品是以更高 Z 值（约 100~232）渲染、且会写入深度缓冲的 3D 几何；两者位置重叠时深度测试会把文字裁掉。原版堆叠数量使用 `renderItemDecorations`（带 POLYGON_OFFSET 深度偏移）所以不会被挡，本模组自绘的数量没有该偏移。
- **修复**：`BulkCountRenderer.render` 在绘制数量前把渲染层级抬到物品之上（`pose().translate(0, 0, 300)`），数字始终显示在物品上层，与原版堆叠数量的观感一致。
- 影响范围：仅 `BulkCountRenderer.render` 一处（矿石储备箱、产物缓冲箱、便携熔炉共用）。

## [1.9.16] - 2026-08-16

### 🐞 修复“挖掘速度加成”在飞行/水下时被原版惩罚削减

- **问题**：玩家投资「挖掘速度」加成后，一旦飞行（创造飞行、鞘翅等）或水下挖掘，加成效果会被原版 `Player.getDestroySpeed` 的惩罚（`!onGround` 与 `isEyeInFluid(WATER)` 各 ÷5）大幅削弱，表现为“有加成却挖得比站在地面时慢”。
- **原因**：原版在玩家「不在地面」或「眼睛浸水」时对挖掘速度执行 ÷5 惩罚；旧逻辑只在 `BreakSpeed` 事件里把加成 `bonus` 直接乘到 `event.getOriginalSpeed()` 上，而 `getOriginalSpeed()` 本身已被上述惩罚除过，于是飞行/水下时加成被同比例抹掉。
- **修复**：`StatEventHandler.onBreakSpeed` 在玩家拥有挖掘速度加成（`bonus > 1`）时，对「不在地面」「眼睛浸水且无水下速掘附魔」两种情形分别预乘 ×5，恰好抵消原版 ÷5 惩罚。最终飞行/水下挖掘速度 = 地面无惩罚速度 × 加成，加成全额生效。
- 影响范围：仅 `StatEventHandler.onBreakSpeed` 一处。

## [1.9.15] - 2026-08-15

### ⚙️ 移除“攻击降低目标最大生命值”的频率限制

- **改动**：`AttackHandler.applyReduceMaxHealth` 删除频率限制——不再记录 `REDUCE_MAX_HEALTH_LAST_TICK`、不再按攻击速度/固定 5 tick 节流。每次伤害事件都会直接削减 `reduce` 点（乘算倍率折算逻辑 1.9.14 保留，莱特兰等级下的削减依然精确）。
- **影响**：恢复“每次伤害事件触发即削减”的行为。若整合包中存在多段伤害/连续伤害事件，削减次数会随之增多（此前 1.9.12/1.9.13 的频率限制就是为压制该现象而加）；叠加乘算倍率折算后，每段削减仍恰好等于面板值。
- 影响范围：仅 `AttackHandler.applyReduceMaxHealth` 一处。

## [1.9.14] - 2026-08-15

### 🐞 修复“攻击降低目标最大生命值”被莱特兰等级（L2Hostility）乘算放大——不同等级削减不同

- **问题**：1.9.13 修复频率放大后，玩家实测“打没有莱特兰等级的怪削减正常，打有莱特兰等级的怪削减量不同，等级越高削减越多”。
- **原因**：Minecraft 属性公式中 `ADDITION` 修饰符先加进基数、再被 `MULTIPLY_BASE` / `MULTIPLY_TOTAL` 乘算放大：`总值 = (基础 + ΣADD) × (1 + ΣMB) × (1 + ΣMT)`。本模组的削减修饰符是 `ADDITION`，而 L2Hostility（莱特兰）给高等级怪提升 `MAX_HEALTH` 用的是乘算修饰符——于是本模组 -1 的削减被放大为 `-1 × 倍率`，莱特兰等级越高倍率越大、削减越多（无等级时倍率 = 1，削减正常）。
- **修复**：`AttackHandler.applyReduceMaxHealth` 在削减前遍历目标 `MAX_HEALTH` 的全部修饰符，算出乘算倍率 `scale = (1 + ΣMB) × (1 + ΣMT)`，把累计削减量按 `actualReduce / scale` 折算后再写入 `ADDITION` 修饰符。任何莱特兰等级下，每刀削减都恰好等于面板值（Lv.1 每下 -1、Lv.2 每下 -2），触底逻辑不变。
- 影响范围：仅 `AttackHandler.applyReduceMaxHealth` 一处。

## [1.9.13] - 2026-08-15

### 🐞 修复“攻击降低目标最大生命值”仍一次削减过多——频率限制在攻击速度被无限加点拉高时失效

- **问题**：1.9.12 加入的频率限制仍不够。玩家实测“取消无敌帧开不开都一样”，一次攻击依然削减约 11 点（Lv.1）、23 点（Lv.2）。
- **原因**：1.9.12 的冷却换算 `max(1, (int)(20 / 攻击速度))` 在**攻击速度 ≥ 10** 时退化为 `1 tick`——本模组是“无限加点”，玩家可把攻击速度点到很高（远超过原版上限 20），此时频率限制形同虚设：按住左键 0.5 秒（10 tick）就会削减 10+ 次。且与 `no_invincibility_frames`（取消无敌帧）无关——巨型整合包中其他战斗模组通常已取消目标无敌帧，开不开该属性目标都会连续受伤，所以“开不开都一样”。
- **修复**：给攻击冷却窗口强制 **5 tick 下限**（= 标准攻速 4 的完整攻击周期），`Math.max(5, round(20 / 攻速))`。无论攻速/无敌帧如何，**每秒至多削减 4 次**，每次削减恰好等于面板值（Lv.1 每下 -1、Lv.2 每下 -2）。标准攻速（4）玩家的攻击节奏完全不受影响。
- 影响范围：仅 `AttackHandler.applyReduceMaxHealth` 的频率限制一处。

## [1.9.12] - 2026-08-15

### 🐞 修复“攻击降低目标最大生命值”一次攻击削减过多（面板 +1.0 实际却减 11 点）

- **问题**：面板显示「攻击削减生命上限 Lv.1 +1.0」，但空手打目标一下实际削减了约 11 点生命上限；加点为 2 时一次削减约 23 点，远超面板值。
- **原因**：`reduce_max_health` 的削减挂在每次 `LivingHurtEvent` 上。当玩家开启 `no_invincibility_frames`（取消无敌帧）或攻速极快、或一次攻击被其他模组拆成多段伤害事件时，一个攻击冷却窗口内会连续触发多次伤害事件，而旧逻辑每次事件都削减 `reduce` 点——面板 1.0 的削减被乘上了伤害事件次数（约 11 次）。
- **修复**：`AttackHandler.applyReduceMaxHealth` 增加频率限制——按玩家攻击速度换算攻击冷却周期（`(1 / 攻击速度) × 20` tick），**一个攻击冷却周期内至多削减一次**。保证“每次攻击动作只减面板所写的值”（Lv.1 每次攻击减 1 点、Lv.2 减 2 点），取消无敌帧/高攻速下不再放大削减量。
- 影响范围：仅 `AttackHandler.applyReduceMaxHealth` 一处。

## [1.9.11] - 2026-08-13

### 🐞 修复“攻击降低目标最大生命值”在一次攻击中出现两个伤害实例（观感为多重伤害）

- **问题**：1.9.10 修复了满血目标被 `clampDrop` + `extraDrop` 双倍扣血后，玩家反馈“还是有多重伤害，但没那么多了”。原因是未满血目标（当前血量低于新上限）不再走 clamp 夹取，旧代码仍会调用 `applyDirectDamage` 额外补扣 `extraDrop` 真实伤害，于是一次攻击同时出现“主伤害 + 降上限伤害”两个伤害数字。
- **修复**：`AttackHandler.applyReduceMaxHealth` 移除 `extraDrop` 的 `applyDirectDamage` 调用。本属性只负责“降低生命上限”——削减 `MAX_HEALTH` 后仅用 `setHealth(min(oldHealth, 新总值))` 把当前血量夹取到新上限之下（原血量高于新上限则自然跟随下降，低于新上限则保持不变），**不再额外触发一次伤害事件**。一次攻击现在只有一个主伤害实例，符合“每次攻击降低目标最大生命值”的属性描述（原描述本就不含“额外造成直接伤害”）。
- 影响范围：仅 `AttackHandler.applyReduceMaxHealth` 一处。

## [1.9.10] - 2026-08-13

### 🐞 修复“攻击降低目标最大生命值”对满血目标双倍扣血（打一下造成多次伤害）

- **问题**：玩家一次攻击命中满血/接近满血的怪时，`reduce_max_health`（攻击减血上限）会令怪“掉一大块血”，表现为“打一下却造成了多次/过量伤害”，且只有这个属性有此问题。
- **原因**：`AttackHandler.applyReduceMaxHealth` 在削减 `MAX_HEALTH` 总值后，`target.getHealth()` 因上限下降被 clamp 夹取，已经把当前血量扣掉了一部分（`clampDrop`）；但旧代码用 `drop = max(actualReduce, 当前血 - 新总值)` 又额外以真实伤害补扣了 `actualReduce` 点，导致满血目标实际丢失 `2 × actualReduce` 的血。其它直接伤害效果（真实伤害/范围/降上限以外）不触碰 `MAX_HEALTH` 属性，故不会触发该叠加，因而仅本属性异常。
- **修复**：削减上限前先记录旧血量 `oldHealth`；削减后用 `setHealth(min(oldHealth, newTotal))` 形成确定性夹取，算出夹取已自然扣掉的血量 `clampDrop = max(0, oldHealth - newTotal)`；最终只补扣 `extraDrop = actualReduce - clampDrop`。保证“降多少上限、当前血就降多少”且**绝不重复扣血**（满血目标 `clampDrop == actualReduce` → `extraDrop == 0`，不再额外掉血）。
- 影响范围：仅 `AttackHandler.applyReduceMaxHealth` 一处（与 1.9.9 同一方法）。

## [1.9.9] - 2026-08-12

### 🐞 改进“攻击降低目标最大生命值”为真正削减“总值”（含其他模组加成）

- **背景**：1.9.8 版本改为“削减基础值、保留加成”，但带生命加成的怪（Apotheosis 词缀/宝石/盔甲/饰品等）的总血量里，加成部分仍削不掉，表现为“上限削到基础 1+加成 后就停了”。
- **改动**：`AttackHandler.applyReduceMaxHealth` 改用本模组专属的固定 UUID 负 `ADDITION` 修饰符（`REDUCE_MAX_HEALTH_UUID`）**累计削减 `MAX_HEALTH` 总值**。每刀稳定下降 `reduce` 点，会把其他模组的加成也一同削掉——即真正“减总值”。
- **细节**：用单个修饰符累计（先读旧值再 `removeModifier` 后重加），避免每刀新增一个 modifier；`actualReduce = 旧总值 - max(1, 旧总值 - reduce)`，`actualReduce=0` 时直接返回，触底（总值=1）后不再造成任何额外伤害；当前血同步下降 `max(actualReduce, 当前血 - 新总值)`。
- 影响范围：仅 `AttackHandler.java` 一处；属性定义（`reduce_max_health`，每点 -1、最低保留 1 点）不变。

## [1.9.8] - 2026-08-12

### 🐞 修复“攻击降低目标最大生命值”数值不对（卡住且持续掉血）

- **问题**：`reduce_max_health`（攻击减血上限）属性打在带有生命加成的怪（Apotheosis 词缀/宝石/盔甲/饰品等，即 `modifiers > 0`）身上时，生命上限削到基础值 `1` 后便卡住不再下降，但每次攻击仍持续造成 `reduce` 点真实伤害，表现为“上限削不下来、怪却一直在掉血”。
- **原因**：旧实现用 `newBase = max(1.0, newMax - modifiers)` 从总值反推基础值；当基础值被夹到 `1.0` 后，`curMax = 1.0 + modifiers` 不再改变，而 `newMax = curMax - reduce` 仍算出 `lose = reduce`，于是每次攻击都 `applyDirectDamage(reduce)`，形成“上限不动、反复掉血”的异常。
- **修复**：改为直接削减 `MAX_HEALTH` 的**基础值**（`getBaseValue()`），其他模组的加成部分（`modifiers`）原样保留，因此总值 `= 新基础值 + 加成` 会正确、持续地下降；并用 `actualReduce = 旧基础值 - 新基础值` 判断，触底（基础值已为 `1`）时 `actualReduce = 0` 直接返回，**不再造成任何额外伤害**。当前生命同步下降的量取 `max(actualReduce, 当前血 - 新上限)`，保证降多少上限当前血就减多少。
- 影响：`AttackHandler.applyReduceMaxHealth` 一处；属性定义（`reduce_max_health`，每点 -1、最低保留 1 点）不变。

## [1.9.7] - 2026-08-11

### 🐞 彻底修复“玩家攻击”可能拖垮服务端的崩溃

- **背景**：1.9.6 仅把范围吸血改走 `direct_damage` 伤害类型，依赖 `isDirectDamageSource` 按 `msgId` 判断来拦截递归。但该判断依赖 `infinitestats:direct_damage` 伤害类型在运行时成功解析——一旦解析失败（回退为普通 `playerAttack` 伤害源），递归防护即失效，攻击密集生物群（鱼群等）时仍会同步无限递归 → `StackOverflowError` → 被 log4j 类加载冲突（`LinkageError`）放大为服务端崩溃。`crash-2026-08-11_23.33.24-server.txt` 证实崩溃发生在加载了 1.9.6 的情况下。
- **修复**：
  1. 用 `ThreadLocal<Boolean>` 重入锁取代脆弱的 `msgId` 判断：攻击分支执行期间置锁，次级受击（范围/真实/降上限直接伤害触发的同步 `onLivingHurt`）检测到锁即跳过攻击分支。**与 `direct_damage` 是否解析无关，彻底杜绝同步无限递归**。
  2. 整个 `onLivingHurt` 用 `try { ... } catch (Throwable t)` 包裹：任何异常（含 `StackOverflowError` 等 `Error`）都就地捕获并打印真实堆栈到 `System.err`，**绝不冒泡到 Forge 事件总线**——因为 `EventBus.handleException` 记录日志时会撞上 log4j 类加载冲突（`LinkageError`）从而拖垮整个服务端。这样即便本模组代码存在未知异常，也只会被记录、不会让服务器崩溃，并能在日志里暴露真正的根因。
- 影响范围：`StatEventHandler.onLivingHurt` 一处，其余逻辑不变。

## [1.9.6] - 2026-08-11

### 🐞 修复范围吸血导致的服务器崩溃（无限递归）

- **问题**：玩家在成群生物（如鱼群）中攻击实体、且拥有 `life_steal_aoe`（范围吸血）属性时，服务器崩溃。
- **原因**：`AttackHandler.applyAoeLifeSteal` 对周围实体使用 `playerAttack(player)` 造成伤害，其伤害来源 `msgId = "player"` 不匹配 `isDirectDamageSource` 的递归拦截条件，于是每个受击实体再次进入 `onLivingHurt` 的攻击分支、再次触发范围吸血，形成同步无限递归 → `StackOverflowError`。该异常被 Forge `EventBus.handleException` 记录日志时又撞上 log4j 类加载冲突，最终表现为 `LinkageError` 崩溃（崩溃报告指向 `EventBus.handleException`）。
- **修复**：范围吸血改为调用 `applyDirectDamage`（使用本模组 `direct_damage` 伤害类型，其 `message_id` 命中 `isDirectDamageSource`），受击实体再次进入事件时被正确拦截，递归消除。
- 影响：`infinitestats` 攻击分支的其它直接伤害路径（`applyTrueDamage` / `applyScopeAttack` / `applyReduceMaxHealth`）本来就走 `applyDirectDamage`，不受影响。

## [1.9.5] - 2026-08-11

### 🐞 移除范围击杀的死亡提示翻译

- 移除 `data/infinitestats/damagetype/direct_damage` 对应的 `death.attack.infinite_stats.direct_damage` 与 `.player` 翻译条目，避免范围/真实伤害击杀大量生物时在聊天栏刷屏。
- 说明：原版中生物的死亡提示默认不会在聊天栏广播（仅玩家死亡会），但为保险仍移除该提示文本，且击杀归属（掉落物、成就/进度）逻辑不受影响。

## [1.9.4] - 2026-08-11

### 🐞 修复范围击杀/真实伤害不掉物品、无成就

- **根因**：范围攻击（`scope_attack`）、真实伤害（`true_damage`）、降低目标最大生命值（`reduce_max_health`）都通过 `setHealth()` 直接扣血，没有走正式的受伤/死亡流程。
  - `setHealth` 不会设置 `lastHurtByPlayerTime`，而掉落物正是靠该字段判断是否归玩家所有，所以被范围/真实伤害杀死的怪**不掉落物品**；
  - 死亡来源不是玩家，导致**成就/进度（FTB kill 任务、L2Hostility 难度判定）不触发**，表现为“有时不掉、有时没成就”。
- **修复**：改为通过 `target.hurt(玩家来源的自定义伤害类型, 伤害量)` 施加伤害，并新增数据驱动伤害类型 `infinitestats:direct_damage`（`bypasses_armor: true`，保留“无视护甲/真实伤害”语义）。
  - 死亡会经由 `die(playerSource)` 正确归属玩家：掉落物、成就/进度、FTB kill 任务、L2Hostility 难度判定均按玩家击杀处理。
  - 在 `StatEventHandler` 的玩家攻击分支增加递归防护：本模组自定义直接伤害来源不再二次触发范围/真实伤害逻辑，避免范围伤害互相递归造成无限循环或伤害异常放大。

## [1.9.3] - 2026-08-09

### 🖥 矿石优先顺序界面改为左右两列

- **布局调整**：将「优先放入」与「可加入的矿石」从上下排列改为**左右并排两列**，可同时查看两边内容，避免长列表来回滚动。
- **界面加宽**：屏幕宽度从 220 调整为 300，底部物品栏在新宽度下居中显示。
- **标题与标签居中**：顶部标题、副标题和「物品栏」标签改为居中，整体更整齐。
- 功能不变：点击背包矿物或右列「＋」加入优先列表，左列「↑/↓」调整顺序、「✕」移出。

## [1.9.2] - 2026-08-09

### 🐞 修复矿石优先顺序界面无法打开

- **根因**：`FurnaceOrePriorityMenu` 构造时菜单类型传了 `null`，导致客户端收到打开数据包后无法定位界面，`NetworkHooks.openScreen` 失败，点「优先级」按钮无反应。
- **修复**：菜单改为使用已注册的 `furnace_ore_priority_menu` 类型，与客户端 `MenuScreens.register(..., FurnaceOrePriorityScreen)` 映射对应，界面可正常打开。

## [1.9.1] - 2026-08-09

### 🐞 修复矿石优先顺序界面

- **修复空列表问题**：原“可加入的矿石”列表仅取自身边熔炉矿石储备箱中已有的矿物，储备箱为空时界面无任何内容、无法设置。现改为列出**游戏内所有可熔炼/高炉的矿物**，界面永远有内容可选。
- **原版风格背包栏位**：界面底部新增玩家真实背包槽位（原版物品栏），可直接**点击背包中的矿物**加入优先列表，无需先存入储备箱。
- **操作方式**：点背包矿物或列表「＋」加入、列表「↑/↓」调整顺序、「✕」移出，滚轮滚动长列表，ESC 或「关闭」返回熔炉。

## [1.9.0] - 2026-08-09

### ⚙️ 随身熔炉“矿石优先顺序”设置

- **新增矿石优先顺序界面**：在随身熔炉界面新增「优先级」按钮，可打开设置界面，指定自动冶炼时**优先放入输入槽**的矿石顺序。
- **排序规则**：优先顺序列表靠前的矿石会先被从矿石储备箱取出并熔炼；列表之外的矿石按储备箱格子顺序排在之后。一种熔完再放下一种。
- **界面操作**：
  - 优先列表支持「↑ / ↓」调整顺序、「✕」移出。
  - 「可加入的矿石」列出当前储备箱中可熔炼且尚未加入优先列表的矿石，点击「＋」即可加入优先列表。
  - 列表过长时支持鼠标滚轮滚动。
- **数据持久化**：优先顺序随玩家数据保存（NBT），并在死亡/重生时继承，跨游戏会话保留。
- 仅作为取出顺序的依据，不改变熔炼逻辑与配方。

## [1.8.1] - 2026-08-09

### 🔥 自动冶炼兼容模组矿

- **移除硬编码矿物映射表**：原 `SMELT_MAP` 只覆盖原版铁/金/铜/远古残骸等有限矿物，无法识别模组矿石。
- **改为真实配方查询**：自动冶炼（`auto_smelt`）现在通过 `RecipeManager` 查询熔炼结果，**优先匹配高炉（Blasting）配方，其次回退普通熔炉（Smelting）配方**。
- **完全数据驱动**：所有在游戏内注册了熔炼/高炉配方（含数据包自定义）的矿物都会被正确冶炼，包括各类模组矿石及其深层变体，无需手动维护映射表。
- 掉落数量与配方产出倍数均正确保留。

## [1.4.0] - 2026-07-13

### 🪙 EMC 转化系统（全新）

参照 ProjectE 设计，在模组内建了完整的 EMC（能量-物质共价）系统：

- **Bellman-Ford 自动计算引擎**：遍历所有合成/烧炼/锻造/切石配方，迭代收敛计算 EMC 值。使用向下取整（floor division）防止 EMC 复制漏洞，与 ProjectE 算法保持一致。
- **手动锚点配置**：`config/infinitestats/emc_values.json` 内置 120+ 基础物品锚点（原木、矿石、作物、怪物掉落等），支持 `after` 字段实现计算后强制覆盖。
- **漏洞防护**：
  - 多轮循环漏洞检测（检测输入 EMC < 输出 EMC 的配方，自动清零）
  - 原矿/矿石块黑名单（防止"挖矿→烧炼→合块→拆解"循环）
  - 0-EMC 物品不参与配方计算
- **ProjectE 集成**：若检测到 ProjectE 已安装，自动通过反射调用 `IEMCProxy.getValue()`，完全互通 ProjectE 的 EMC 值。
- **调试输出**：自动计算后生成 `emc_calculated.json`，含算法元信息和按 EMC 降序排列的全部物品。

**EMC 转化桌 GUI**（默认无快捷键，通过配置开启）：

```
┌──────────────────────────────────────────┐
│  [搜索栏]          EMC: 12,345  ┌────┐  │
├──────────┬───────────────────────┤学习├──┤
│ 已学物品  │  选中物品详情          │槽  │  │
│ (滚动列表)│  [图标] 物品名        └────┘  │
│          │  EMC: 256                    │
│  ▪ 石头   │  [提取 x1] [x10] [x64]      │
│  ▪ 钻石   │                              │
├──────────┴──────────────────────────────┤
│ [玩家物品栏]                             │
└──────────────────────────────────────────┘
```

- **学习槽**：放入有 EMC 的物品自动消耗 → 标记已学 + 获得 EMC
- **搜索列表**：搜索已学物品名称/ID，实时过滤
- **数量提取**：x1 / x10 / x64 三种模式，右键点击提取
- **EMC 余额**：顶部实时显示，过亿自动格式化为 B/M/K

**玩家数据系统**：
- `EmcPlayerData` Capability：存储 EMC 余额 + 已学物品集合
- NBT 持久化到玩家数据，死亡不掉落（`PlayerEvent.Clone` 保留）
- 登录/重生时自动客户端同步

**命令系统**（`/emc`）：
| 命令 | 功能 |
|------|------|
| `/emc` | 查看自己的 EMC 余额和已学物品数 |
| `/emc learn` | 学习手持物品（消耗 1 个 → 获得 EMC） |
| `/emc learn all` | 学习背包中所有有 EMC 的物品 |
| `/emc give <玩家> <数量>` | 管理员给予 EMC |
| `/emc reload` | 重载 EMC 数据库（管理员） |

### ⚙ 属性加点系统修复

- **允许属性降入负数**：`removePoints` 不再用 `Math.max(0, ...)` 封底，属性等级可跨越 0 进入负值
  - 正→0 段：返还可用点数
  - 0→负段：消耗可用点数
  - 例：等级 5 按 `-8` → 到 -3，返还 5 点 + 消耗 3 点 = 净返还 2 点

---

## [1.3.0] - 2026-07-12

### 🖥 物品编辑器（附魔扩展 NBT 编辑）

- **支持编辑附魔的扩展字段**：Apotheosis 等模组的附魔会在 `Enchantments` 条目内附带额外 NBT 数据（如宝石、词缀、特殊修饰键），现在可以原样读取并回写，不再被丢弃。
- **附魔行点击二次编辑**：在物品编辑器中点击任意已有附魔行（非删除按钮）即可打开编辑框，修改等级与扩展字段。
- **扩展 NBT 输入框**：选择/编辑附魔后新增「扩展NBT(可选,JSON)」输入框，已存在的附加数据自动回填；确认时解析 JSON/SNBT，留空表示无扩展，格式非法则提示「扩展NBT格式无效」并不提交。
- **列表标记**：带有扩展 NBT 的附魔在列表中显示金色 `[+]` 标记，便于识别。
- 该方案为**模组无关**实现，不依赖任何特定模组的 API。

### 🔧 底层改动

- `EnchantData` 数据包新增 `extra` 字段，通过 `writeNbt` 序列化随附魔条目传输；服务端重写附魔时将其所有键合并进对应 `CompoundTag`。
- 新增翻译键：`edit_enchant` / `extra_nbt_hint` / `level_nbt_hint` / `invalid_extra_nbt`（中英文）。

---

## [1.2.0] - 2026-07-10

### 🆕 新增属性

**功能 (UTILITY) — 5 个新属性**

| 属性 | 类型 | 说明 |
|------|------|------|
| `auto_repair` | 开关 (2级) | 每秒自动修复背包、装备栏、Curios 饰品栏中所有可损坏物品 |
| `repair_amount` | 叠加 | 每次修理恢复的耐久值，每点 +1 耐久/秒 |
| `mining_level` | 叠加 | 每点使手持工具挖掘等级 +1，可挖掘更高级方块 |
| `crafting_bonus` | 百分比 | 合成物品时概率额外获得一份产物，每点 +0.5% |
| `teleport_distance` | 叠加 | 传送/TP 类能力的基础距离，每点 +5 格 |

**机动 (MOBILITY) — 1 个新属性**

| 属性 | 类型 | 说明 |
|------|------|------|
| `follow_range` | 叠加 | 生物跟踪距离，每点 +0.5，叠加到原版属性 |

**防御 (DEFENSE) — 3 个新属性 + 1 个隐藏属性**

| 属性 | 类型 | 说明 |
|------|------|------|
| `debuff_immunity` | 开关 (4级) | 免疫所有负面药水效果，支持自定义黑名单/白名单过滤 |
| `auto_revive` | 开关 (8级) | 死亡时自动复活，恢复 30% 血量，冷却 5 分钟（可配置） |
| `block_chance` | 百分比 | 概率完全格挡一次伤害，每点 +1.0% |
| `invincibility` | 开关 (1级) | 🔒隐藏属性，需在配置中开启 `showHiddenStats = true` 才可见——免疫一切伤害并保持满血 |

**魔法 (MAGIC) — 2 个新属性**

| 属性 | 类型 | 说明 |
|------|------|------|
| `spell_power` | 百分比 | 全面提升所有法术效果，每点 +2.5% |
| `mana_on_kill` | 叠加 | 击杀生物时回复法力值，每点 +2 |

### 🖥 GUI 系统（全面重写）

- **5 大分类标签页**：攻击 / 防御 / 机动 / 功能 / 魔法 + 外部属性
- **外部属性按模组折叠**：EXTERNAL 标签中第三方属性按命名空间自动分组，点击展开/折叠
- **配置控制隐藏属性**：`invincibility` 等隐藏属性通过配置文件 `showHiddenStats` 控制是否可见，无需快捷键
- **右键拖动面板**、**Ctrl+滚轮缩放**、**中键重置**
- **属性卡片**：图标 + 名称 + 数值 + +/- 按钮的现代 UI 布局
- **Debuff 过滤 GUI**：点击防御分类中的「Debuff 过滤」按钮，可搜索/勾选需要拦截的负面效果

### 🛡 伤害处理链

完整的伤害判定优先级：
1. 免疫判定（火焰 / 弹射物 / 爆炸 / 窒息免疫）
2. 自动复活（死亡时触发）
3. 闪避 (`dodge_chance`)
4. 格挡 (`block_chance`)
5. 法力护盾 (`mana_shield`)
6. 伤害减免 (`damage_reduction`)
7. 摔落减免 (`fall_resist` / `no_fall_damage`)
8. 伤害反射 (`damage_reflection`)

### 🧹 负面免疫过滤系统

- `debuff_immunity` 激活后，可在 GUI 中打开过滤器编辑界面
- **黑名单模式**：打勾的效果 = 拦截，其余放行
- **白名单模式**：打勾的效果 = 绝对不拦截（保护）
- 支持搜索、滚动、实时同步到服务端，无需重启

### 🔧 修复

- 修复 HUD 快捷键（H 键）在属性面板打开时仍然触发的问题——GUI 打开时不再触发 HUD 切换
- 修复外部属性折叠/展开时滚动条跳回顶部——保持当前滚动位置

---

## [1.1.0] - 初始版本

- 泰拉瑞亚式无限属性加点系统
- 击杀/挂机获取经验，无限等级上限
- 5 大分类、60+ 内置属性
- 外部属性自动发现（EXTERNAL 标签页）
- HUD 状态栏显示
- 客户端-服务端自动同步
- 配置系统（经验值、复活冷却、磁铁范围等）
