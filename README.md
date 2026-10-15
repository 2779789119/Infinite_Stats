# 无限加点（Infinite Stats）

> 泰拉瑞亚（Terraria）风格的**无限属性加点系统** —— 打怪升级 → 自由分配属性点 → 打造属于自己的 Build。

| | |
|---|---|
| **Mod ID** | `infinitestats` |
| **当前版本** | `0.16.3`（以 `gradle.properties` 的 `mod_version` 为准） |
| **运行环境** | Minecraft `1.21.1` / NeoForge `21.1.234` 或更高 |
| **前置模组** | 无 |
| **联机** | 支持单人 / 多人服务器 |
| **许可证** | GPL-3.0-only |

---

## 🎮 快速上手

1. 打怪、挂机获取经验
2. 升级获得属性点（默认每级 **10** 点）
3. 按 **`P`** 打开属性面板，点击属性右侧的 `+` 加点
4. 随时可以重置，点数**全额退还**

属性分为 **攻击 / 防御 / 机动 / 功能** 四大类，共 **112 个**。安装其它模组后，它们注册的属性会自动收入「外部属性」分类，同样可以加点。

等级**没有上限**，可以放心分配。

---

## ✨ 核心特性

### 属性加点

- **攻击**（21）：攻击伤害 / 攻速、暴击率 / 暴击伤害、护甲穿透、击退力度、远程伤害、生命偷取、范围吸血、反伤、处决、真实伤害、范围攻击、斥力、多重射击、额外打击、削弱最大生命、无限弓箭……
- **防御**（17）：最大生命、护甲、盔甲韧性、生命恢复、伤害减免、击退抗性、摔落抗性、格挡几率、闪避几率、吸收护盾、自动复活、火焰 / 弹射物 / 爆炸 / 窒息免疫、效果过滤、无敌（隐藏）……
- **机动**（10）：移动速度、游泳速度、跳跃高度、多段跳、抬腿高度、自动抬腿、飞行速度、飞行、爬梯加速、免摔落伤害……
- **功能**（64）：幸运、挖掘速度 / 挖掘等级、交互距离、经验获取、掉落幸运、双倍战利品、合成加成、冷却缩减、修理量、时间加速……

完整清单见下方 [内置属性一览](#-内置属性一览共-112-个)。

### 开关型属性

- 飞行、夜视、水下呼吸、免饥饿、隐身、免摔落伤害、火焰 / 弹射物 / 爆炸 / 窒息免疫……
- **连锁挖掘**：一次破坏成片同类方块；右键还可整片剥皮 / 铺路 / 耕地 / 收割作物
- 自动钓鱼、立即咬钩、自动冶炼、自动修理、繁殖无冷却、一键长大、物品 / 经验磁铁
- **满饱食度进食**：饱食度已满时仍可继续进食
- 死亡不掉落、死亡不掉经验、跨维度传送、定点传送、附魔上限突破、天气控制、区块强加载、生物面板、自动入库

### 随身工具（共 11 种）

- 工作台、熔炉、铁砧、末影箱、锻造台、酿造台、切石机、织布机、制图台、磨石、进阶附魔台
- 从**属性面板内的「随身工具」入口**进入（没有独立快捷键）：**左键**解锁 / 打开，**右键**关闭并退还点数
- 材料可以直接从 RS / AE2 / 汤姆存储等存储网络中提取
- 随身熔炉支持 Shift 连续放料、待炼仓排队，空闲时保留余热
- 随身末影箱与原版末影箱**共用同一份库存**
- 随身酿造台自带无限燃料，关闭界面后仍会继续酿造

### 附魔系统

- **进阶高级附魔台**：附魔不再随机抽取，可以自选
- 支持升级 / 降级 / 移除已有附魔，降级会**退还差价**
- 花经验将耐久**直接修满**；拆解全部附魔返还 **60%** 经验
- 槽内放入**书**即可制作附魔书
- 付款可使用**经验等级或属性点数**，不消耗青金石
- 互斥附魔（锋利 / 亡灵杀手 / 节肢杀手等）可同时附在同一件装备上
- **附魔上限突破**（1 点）：附魔台可自选到任意等级，铁砧可将同等级附魔继续叠加
  - 原版对附魔等级有一道 255 的**存档校验**（界面与内存都不限，只有写进存档 / 复制物品时会撞上）。本模组把它一并放开，所以超过 255 级的附魔**不会在退出重进后被打回**

### 生存辅助

- **死亡不掉落**：死亡时保留背包 + 盔甲 + 副手；**装了 Curios 时连饰品栏一起保**（饰品不在原版背包里，也不受 `keepInventory` 保护）。即使服务器关闭 `keepInventory` 也不会掉落
- **死亡不掉经验**
- **原地复活**：可配置冷却时间，复活后短暂无敌
- **村民交易折扣**（最高 -90%），交易后**立即补货**
- **铁砧经验减免**（最高 -90%）
- **自动入库**：每隔 1 秒将背包物品存入存储网络，可调整存储优先级、设置白 / 黑名单
- 时间加速

### 枪械模组联动（卓越前线 / SuperbWarfare）

- **无限子弹** —— 弹匣保持满仓，无需换弹
- **零热量** —— 持续射击也不会过热
- **无后坐力** —— 准心不会被后坐力顶起
- 卓越前线的**载具武器**同样有效

> TaCZ 在 1.21.1 尚无官方版本（只有第三方移植），本分支暂未适配。

### 其它模组联动

- **无限能源** —— 背包 / 饰品 / 坐骑 / 自己放置的能量方块上的能量保持满格（FE / RF）
- **无限载具血量**（卓越前线）、**无限应力**（机械动力）、**无限灵魂能量**（诡厄巫法）
- **无限魔力** —— 一个属性同时覆盖 Iron's 法术与魔法书（玩家法力）、Ars Nouveau（魔源）、
  Botania（魔力石板 / 魔力戒指等携带魔力 + 你自己放下的魔力池，放下去就永久满、不限距离），装哪个哪个生效
- **免口渴**（口渴）、**免体温**（冷汗）：口渴条恒满、体温钉在舒适区
- **传送点面板**里直接列出**已激活的传送石碑**（Waystones），点「传送」即免费传送（不扣经验、不进冷却）
- **物品编辑器**可直接修改拔刀剑的杀敌数 / 耀魂数 / 锻造数 / SA / SE
- **Apotheosis** 的自定义附魔同样吃「附魔上限突破」的上限
- **Polymorph**（多态合成）兼容 —— 随身工作台与它共用同一套配方选择

各联动均为「装了才生效」，**没装的模组不会造成任何影响，也不需要额外前置**。

### 面板与界面

- 属性面板 `P`、HUD `H`、物品编辑器 `O`、EMC 转化桌 `V`、成就 / 统计 `U`、传送点 `Y`，随身工具入口在属性面板内部
- **生物面板**：解锁后获得一根**生物观察杖**（带标记的木棍），手持它右键任意生物即可打开该生物的面板
  - **属性**页：列出它身上的**全部属性**（生命上限 / 护甲 / 攻击力 / 移动速度……连其它模组添加的属性也在内），
    悬停某行能看到注册名、基础值 / 最终值、**默认值**与取值范围；**左键点一行即可改它的基础值**
    （回车确认，`Shift + 左键` 一键恢复该生物类型的默认值）
  - **开关**页：点一下就能直接改这只生物 —— **AI**（关掉就是木头人）、**幼年 / 成年**、**静音**、**不可见**、
    **发光**、**无敌**、**无重力**
  - 改动一律由服务端执行并回传权威状态（客户端改不出来），数值会被夹进该属性自身的取值范围
  - 关闭属性时木棍自动收回
- **传送点**：保存 / 删除 / 传送，支持**跨维度传送**
- **天气控制**：一键切换晴天 / 下雨 / 雷暴
- **区块强加载**：将指定区块设为强加载，重启后依然有效
- **功能开关**：112 条属性可逐条关闭（每个玩家独立），关闭后从面板隐藏，对应功能入口一并消失
- **效果过滤**：黑名单模式「拦截勾选的效果」（需开启 `debuff_immunity` 开关），白名单模式「保护勾选的效果」——
  自然结束前不会被任何外力清除（其它模组、牛奶桶、`/effect clear`、本模组的清效果逻辑都算外力）。
  名单条目支持 `minecraft:poison`、`somemod:*`（整个模组的全部效果）、`#c:effect_tag`（效果标签）、`*`（全部效果），
  通配与标签会在列表里显示「覆盖 N 个效果」，拼错一眼可见

---

## ⌨️ 按键绑定

| 按键（默认） | 功能 |
|---|---|
| `P` | 打开属性面板 |
| `=`（等号键） | 快速加点 |
| `H` | 开关 HUD 显示 |
| `O` | 打开物品编辑器 |
| `V` | 打开 EMC 转化桌 |
| `U` | 打开成就 / 统计面板 |
| `Y` | 打开传送点面板 |

> 以上均可在「设置 → 按键绑定 → 无限加点」中修改。
> 随身工具面板、天气控制、区块强加载、自动入库、效果过滤等界面从**属性面板内的按钮**进入。

---

## 💻 命令

### EMC 等价交换（`/emc`）

| 命令 | 说明 | 权限 |
|---|---|---|
| `/emc` | 查看自己当前的 EMC 值 | 玩家 |
| `/emc learn` | 学习手持物品 | 玩家 |
| `/emc learn all` | 学习背包中所有可学习的物品 | 玩家 |
| `/emc give <玩家> <数量>` | 给予指定玩家 EMC | 管理员（≥2） |
| `/emc reload` | 重新加载 EMC 数据库 | 管理员（≥2） |

### 传送 / 随身工具（`/infstats`）

> 下列命令均需要对应属性处于**激活状态**才能使用。

| 命令 | 说明 | 需求属性 |
|---|---|---|
| `/infstats crossdim` | 列出当前世界所有维度 | — |
| `/infstats crossdim <维度名>` | 跨维度传送（如 `overworld` / `nether` / `the_end` 或 `<modid>:<维度>`） | 跨维度传送 |
| `/infstats wp set <名称>` | 保存当前位置为传送点 | 定点传送 |
| `/infstats wp del <名称>` | 删除传送点 | 定点传送 |
| `/infstats wp list` | 列出所有传送点 | 定点传送 |
| `/infstats wp <名称>` | 传送到指定传送点 | 定点传送 |
| `/infstats craft` | 打开随身工作台 | 随身工作台 |
| `/infstats furnace` | 打开随身熔炉 | 随身熔炉 |
| `/infstats anvil` | 打开随身铁砧 | 随身铁砧 |
| `/infstats enchant <附魔ID> <等级>` | 给手持物品直接设置某条附魔的等级 | 附魔上限突破（超过原版上限时） |

### 功能开关（`/infstats feature`，需管理员权限 ≥2）

| 命令 | 作用 |
|---|---|
| `/infstats feature list` | 列出当前已关闭的功能 |
| `/infstats feature <属性ID>` | 切换该功能（开 ↔ 关） |
| `/infstats feature all on\|off` | 全部内置属性一次性开启 / 关闭 |
| `/infstats feature mode` | 查看 / 切换「关掉时」的执行模式（`keep` / `refund` / `hide`） |

---

## 📦 安装

1. 安装 Minecraft **1.21.1** 与对应 NeoForge（**21.1.234** 或更高）
2. 把 `infinite_stats-无限加点-*.jar` 放进 `.minecraft/mods/`
3. 启动游戏即可，**无需任何前置依赖**

模组本身**只有一个 jar**（另附一个 `-sources.jar` 仅供查阅源码，不要装进游戏）。下方的可选联动与拼音搜索都按「装了才生效」处理，缺了不影响运行。

---

## ⚙️ 配置

配置文件位于 **`config/infinitestats-common.toml`**（全局生效）。
游戏内打开方式：**暂停菜单 → 模组 → 选中本模组 →「Config」按钮**（需安装 Cloth Config），修改后即时生效。

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `Experience.xpPerKillBase` | `20` | 击杀怪物基础经验值 |
| `Experience.xpPerKillHealthFactor` | `3.0` | 怪物最大生命值每点附加的经验系数 |
| `Experience.passiveXpAmount` | `2` | 被动获取的经验值 |
| `Experience.passiveXpInterval` | `20` | 被动经验间隔（tick，20 = 1 秒） |
| `Leveling.baseXpPerLevel` | `60` | 升到 2 级所需基础经验 |
| `Leveling.xpPerLevelIncrement` | `30` | 每级增加的经验需求 |
| `Leveling.pointsPerLevel` | `10` | 每次升级获得的属性点 |
| `AutoRevive.autoReviveCooldown` | `0` | 自动复活冷却（秒，0 = 无冷却） |
| `AutoRevive.autoReviveHealthPercent` | `0.3` | 复活后恢复生命百分比 |
| `AutoRevive.autoReviveInvulnSeconds` | `3` | 复活后的伤害免疫时间（秒） |
| `AutoRevive.autoReviveRefillFood` | `true` | 复活时补满饥饿值与饱食度 |
| `AutoRevive.autoReviveClearDebuffsOnly` | `true` | 仅清除负面效果、保留增益 |
| `PassiveEffects.healthRegenInterval` | `20` | 生命恢复间隔（tick） |
| `PassiveEffects.magnetRange` | `10` | 物品 / 经验磁铁吸引范围 |
| `PassiveEffects.veinMinerMaxBlocks` | `64` | 连锁挖掘单次方块数的默认值 |
| `PassiveEffects.veinMinerMatchTags` | `*:ores` 等 | 连锁「按标签组匹配」的方块标签 |
| `PassiveEffects.projectileTrackingRange` | `64` | 弹射物追踪扫描半径（方块，范围 8–256） |
| `GUI.showHiddenStats` | `false` | 是否显示隐藏属性（如 `invincibility` 无敌） |
| `GUI.featureDisableMode` | `keep` | 功能开关「关掉时」的执行模式（`keep` / `refund` / `hide`） |
| `GUI.disabledStats` | `[]` | 全局默认关闭的属性列表 |
| `Compatibility.enableAttributeDiscovery` | `true` | 自动发现其它模组属性（**需重启**） |
| `EMC.emcEnabled` | `true` | 是否启用 EMC 系统 |
| `EMC.emcLossRate` | `0.0` | EMC 转换损耗率（0 = 无损耗） |
| `EMC.autoLearnProjectE` | `true` | ProjectE 自动学习开关 |
| `EMC.emcFallbackValue` | `0` | 未知物品兜底 EMC（0 = 关闭） |
| `TimeAccel.timeAccelRadius` | `4` | 「加速」属性的基础影响半径 |
| `Furnace.furnaceSpeedCost` | `5` | 每级随身熔炉速度消耗的属性点 |
| `Crafting.craftingMultiplierCost` | `5` | 每级随身工作台倍率消耗的属性点 |
| `NetworkPriority.networkPriority` | `[RS, AE2, TOMS, BACKPACK, BD]` | 存储网络自动选择的优先级（**需重启**） |
| `WeatherControl.weatherCycleDuration` | `6000` | 天气切换后的持续时间（tick，默认 5 分钟） |
| `ChunkLoader.maxForcedChunks` | `16` | 每个维度最多同时强加载的区块数量（上限 256） |
| `AutoDeposit.autoDepositInterval` | `20` | 「自动入库」扫描间隔（tick） |
| `AutoDeposit.autoDepositKeepHotbar` | `true` | 是否保留快捷栏物品不被自动入库 |
| `AutoDeposit.autoDepositBlacklist` | `[]` | 服务端级自动入库黑名单（所有玩家生效） |
| `EntityPanel.allowEntityEditing` | `true` | 是否允许从**生物面板**直接改生物（开关 + 属性数值）；关掉则面板只读 |
| `ExtraStrike.extraStrikeDamageRatio` | `0.2` | 「额外打击」每次追加的伤害比例 |
| `AlwaysEat.alwaysEatOnlyList` | `[]` | 「满饱食度进食」限制名单（留空 = 所有食物） |
| `AlwaysEat.alwaysEatExcludeList` | `[]` | 「满饱食度进食」例外名单（优先级更高） |
| `InfiniteEnergy.infiniteEnergyBlocks` | `true` | 「无限能源」是否也补满自己放置的能量方块 |
| `InfiniteEnergy.energyBlockRadius` | `16` | 能量方块的扫描半径（方块） |
| `InfiniteEnergy.energyBlockInterval` | `20` | 能量方块的扫描 / 补电间隔（tick） |
| `CreateStress.infiniteStressCapacity` | `1000000000` | 「无限应力」（机械动力）把动力网络产能改写成的数值（SU）；调小 = 给无限应力设上限，超出的大网络仍会超载 |
| `AutoFish.autoFishRecastDelayMin` | `5` | 「自动钓鱼」收杆后重新抛竿的最短延迟（tick） |
| `AutoFish.autoFishRecastDelayMax` | `15` | 上项的最长延迟；每次实际延迟在「最短 ~ 最长」之间随机，两者相同则为固定延迟 |

> 大部分配置修改后即时生效，标注「需重启」的除外。

---

## 🧬 内置属性一览（共 112 个）

> 标 **[开关]** 的为功能型开关，激活后即时生效，可随时开启 / 关闭。

### ⚔️ 攻击（21）

`attack_damage` 攻击伤害 · `attack_speed` 攻击速度 · `crit_chance` 暴击率 · `crit_damage` 暴击伤害 · `armor_penetration` 护甲穿透 · `knockback_power` 击退力度 · `projectile_damage` 远程伤害 · `life_steal` 生命偷取 · `life_steal_aoe` 范围吸血 · `damage_reflection` 反伤 · `execute` 处决 · `true_damage` 真实伤害 · `reduce_max_health` 削弱最大生命 · `scope_attack` 范围攻击 · `repulsion` 斥力 · `infinite_arrows` **[开关]** 无限弓箭（弓 / 弩） · `multi_shot` 多重射击 · `extra_strike` 额外打击 · `infinite_ammo` **[开关]** 无限子弹 · `no_heat` **[开关]** 零热量 · `no_recoil` **[开关]** 无后坐力

### 🛡️ 防御（17，含 1 隐藏）

`max_health` 最大生命 · `armor` 护甲 · `armor_toughness` 盔甲韧性 · `health_regen` 生命恢复 · `damage_reduction` 伤害减免 · `knockback_resist` 击退抗性 · `fall_resist` 摔落抗性 · `block_chance` 格挡几率 · `dodge_chance` 闪避几率 · `absorption_shield` 吸收护盾 · `auto_revive` 自动复活 · `fire_immunity` **[开关]** 火焰免疫 · `projectile_immunity` **[开关]** 弹射物免疫 · `explosion_immunity` **[开关]** 爆炸免疫 · `suffocation_immunity` **[开关]** 窒息免疫 · `debuff_immunity` **[开关]** 效果过滤 · `invincibility`（隐藏）无敌

### 🏃 机动（10）

`movement_speed` 移动速度 · `swim_speed` 游泳速度 · `jump_height` 跳跃高度 · `multi_jump` N 段跳 · `step_height` 抬腿高度 · `auto_step` **[开关]** 自动抬腿 · `fly_speed` 飞行速度 · `fly` **[开关]** 飞行 · `climb_speed` 爬梯加速 · `no_fall_damage` **[开关]** 免摔落伤害

### 🧰 功能（53 + 11 个随身工具）

> 11 个 `portable_*`「随身工具」属性统一在**随身工具面板**里用左键解锁 / 右键关闭，因此不再显示在属性面板。

`luck` 幸运 · `mining_speed` 挖掘速度 · `mining_level` 挖掘等级 · `reach` 方块交互距离 · `entity_reach` 实体交互距离 · `xp_gain` 经验获取 · `loot_luck` 掉落幸运 · `double_loot` 双倍战利品 · `item_magnet` **[开关]** 物品磁铁 · `xp_magnet` **[开关]** 经验磁铁 · `auto_deposit` **[开关]** 自动入库 · `vein_miner` **[开关]** 连锁挖掘 · `auto_smelt` **[开关]** 自动冶炼 · `auto_fish` **[开关]** 自动钓鱼 · `instant_bite` **[开关]** 立即咬钩 · `breed_no_cooldown` **[开关]** 繁殖无冷却 · `instant_grow` **[开关]** 一键长大 · `crafting_bonus` 合成加成 · `auto_repair` **[开关]** 自动修理 · `repair_amount` 修理量 · `use_speed` 使用速度 · `bow_draw_speed` 拉弓加速 · `cooldown_reduction` 冷却缩减 · `projectile_tracking` **[开关]** 弹射物追踪 · `no_invincibility_frames` **[开关]** 取消无敌帧 · `night_vision` **[开关]** 夜视 · `water_breathing` **[开关]** 水下呼吸 · `no_hunger` **[开关]** 免饥饿 · `no_thirst` **[开关]** 免口渴 · `no_temperature` **[开关]** 免体温 · `always_eat` **[开关]** 满饱食度进食 · `invisibility` **[开关]** 隐身 · `keep_inventory` 死亡不掉落 · `keep_xp` **[开关]** 死亡不掉经验 · `time_accel` **[开关]** 时间加速 · `time_accel_radius` 加速半径 · `cross_dimension_teleport` **[开关]** 跨维度传送 · `fixed_point_teleport` **[开关]** 定点传送 · `enchant_limit` **[开关]** 附魔上限突破 · `weather_control` **[开关]** 天气控制 · `chunk_loader` **[开关]** 区块强加载 · `entity_panel` **[开关]** 生物面板 · `trade_discount` 村民交易折扣 · `trade_restock` **[开关]** 交易即刻补货 · `anvil_cost` 铁砧经验减免 · `pe_auto_learn` **[开关]** PE 自动学习 · `emc_learn_bonus` EMC 学习收益 · `emc_extract_discount` EMC 转化折扣 · `infinite_energy` **[开关]** 无限能源（FE/RF） · `sbw_infinite_health` **[开关]** 无限载具血量 · `goety_infinite_soul` **[开关]** 无限灵魂能量 · `create_infinite_stress` **[开关]** 无限应力 · `infinite_mana` **[开关]** 无限魔力

### 🌐 外部属性（动态）

开启 `Compatibility.enableAttributeDiscovery`（默认开）后，其它模组注册的能力属性会自动归并到「外部属性」分类，可像内置属性一样加点。遇到兼容性问题时可关闭该选项（**需重启**）。

---

## 🌏 外部属性的中文翻译

不少模组**自己没有提供中文译文**，GUI 中会显示成 `[gunsmithlib:bullet_damage]` 这样的原始键名。本模组已将常见模组的译文**内置进语言文件**，安装即可使用，所有玩家都能看到中文。

已覆盖（部分）：

`gunsmithlib`、`attributeslib`、`goety` / `goety_revelation` / `goeticlegacy` / `goetytranscended`、`taa`、`touhou_little_maid`、`wizard_terra_cuiros`、`l2damagetracker`、`l2hostility`、`celestial_core`、`flame_chase_artifacts`、`additional_attributes`、`fallen_gems_affixes`、`terra_entity`、`ending_library`、`cataclysm`、`apotheosis_modern_ragnarok`、`reach-entity-attributes`、`qiyuemod`、`blessingofpandora`、`curseofpandora`、`tacz`、`tcc`、`alexscaves_torpedoes`、`ftbultimine`、`caelus`、`vengeance`、`slashblade`、`block_factorys_bosses`、`monsterexpansion`、`confluence`、`sal_fishs_attribute_lib`、`additionalentityattributes`、`obscure_api`。

启动时若发现「连所属模组都没提供译文」的属性，会把清单写入 `config/infinitestats/external_translations.json`，方便自行制作资源包补充。该文件**已存在时不会被覆盖**。

> **原理**：MC 的语言表是**跨 namespace 按 key 合并**的，所以 `infinitestats` 的 lang 里放别的模组的键照样生效；而这些键在原模组处本来就不存在，不会产生覆盖冲突。

---

## 🔍 拼音搜索

属性面板 / 效果过滤器 / 物品编辑器 / 物品选择 / 入库过滤 / EMC 转化桌 / 成就统计面板的搜索框均支持拼音，例如 `zsj` → 钻石剑、`tiezh` → 铁砧。

拼音能力来自**外部库**，装下列任意一个即可（本模组**不内置**拼音库）：

| 模组 | 说明 |
|---|---|
| **JustEnoughCharacters**（`jecharacters`） | 优先使用；通过反射调用其匹配 API |
| **Pinyin Search**（`pinyin_search`） | 次选；走它的 `PinyinSearch` / `Matcher` API |

两个都没装时，搜索框**退化为标准的字符串包含匹配**（其它功能完全不受影响）。

**已知限制**：英文词首字母无法搜索（`Diamond Sword` 搜不到 `DS`）；简体与繁体不互通（`钻石剑` 与 `鑽石劍` 互相搜不到）。

---

## 🔧 开发 / 构建

NeoForge **ModDevGradle** 工程，Java 21。

```bash
./gradlew build              # 构建（产物在 build/libs/）
./gradlew compileJava        # 只验证代码能否编译，比 build 快得多
./gradlew runClient          # 开发环境直接启动客户端（不需打包）
./gradlew runServer          # 开发环境启动专用服务端
./gradlew runGameTestServer  # 跑 GameTest 回归测试
```

源码结构：

```
src/main/java/com/infinitestats/
├── InfiniteStats.java     # 模组入口
├── Config.java            # NeoForge 配置
├── client/                # GUI 与客户端逻辑（属性面板 / HUD / 物品编辑器 / EMC / 传送点 / 随身工具 / 生物面板 / 成就…）
├── command/               # 服务端命令（/infstats）
├── compat/                # 兼容层（存储网络桥接、机械动力 / 诡厄巫法 / 卓越前线 / 传送石碑 / Curios / 拼音…）
├── crafting/              # 随身工作台
├── emc/                   # EMC 等价交换
├── event/                 # 事件总线（属性效果总入口）
├── furnace/               # 随身熔炉 / 燃料缓冲
├── handler/               # 属性效果处理器（攻击 / 防御 / 机动 / 功能 / 时间加速 / 冷却…）
├── mixin/                 # Mixin 注入（附魔等级校验、机械动力应力、卓越前线枪械…）与按模组在场的加载把关
├── network/               # 网络同步数据包
├── stats/                 # 属性数据、属性类型、分类、传送点
└── util/                  # 工具类

src/compatApiStub/         # 第三方模组的「编译期存根」：只进编译期类路径，不会打进 jar
src/gameTest/              # GameTest 回归测试源码
```

> **关于 `src/compatApiStub`**：Mixin 注解处理器要求注入目标的类在编译期类路径上，而机械动力之类的联动对本模组只是「装了才生效」的可选依赖。存根只把被注入 / 被读取的成员按真实签名抄一遍，让编译能通过；运行时若目标模组缺席，对应的 Mixin 会被 `InfiniteStatsMixinPlugin` 按**目标包前缀**跳过，所以本模组始终**无需任何前置**。

> **1.21.1 适配说明**：本分支由 Forge 1.20.1 移植而来，已处理的平台差异包括 `ItemStack#save/parse` 需带注册表（`HolderLookup.Provider`）、`SavedData` 的存取签名变化、NeoForge 的事件总线模型、Curios 返回值由 `LazyOptional` 改为 `Optional` 等。

---

## 📄 许可证

本项目以 **GNU General Public License v3.0（GPLv3）** 发布。详见仓库 `LICENSE`。

你可以自由使用、修改、分发本模组（包括商用），但**分发时必须同样以 GPLv3 开源并提供完整源码**。

```
Infinite Stats — Copyright (C) 2026 佚名既无名

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, version 3 of the License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
```
