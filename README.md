![无限加点 Infinite Stats](cover.jpg)

# 无限加点（Infinite Stats）

> 泰拉瑞亚（Terraria）风格的**无限属性加点系统** —— 打怪升级 → 自由分配属性点 → 打造属于自己的 Build。

| | |
|---|---|
| **Mod ID** | `infinitestats` |
| **当前版本** | 1.65.0 |
| **运行环境** | Minecraft `1.20.1` / Forge `47.3.0` 或更高 |
| **前置模组** | 无 |
| **联机** | 支持单人 / 多人服务器 |
| **许可证** | GPL-3.0 |

---

## 🎮 快速上手

1. 打怪、挂机获取经验
2. 升级获得属性点（默认每级 **10** 点）
3. 按 **`P`** 打开属性面板，点击属性右侧的 `+` 加点
4. 随时可以重置，点数**全额退还**

属性分为 **攻击 / 防御 / 机动 / 功能** 四大类，共 **113 个**。安装其它模组后，它们注册的属性会自动收入「外部属性」分类，同样可以加点。

等级**没有上限**，可以放心分配。

---

## ✨ 核心特性

### 属性加点

- **攻击**：攻击伤害 / 攻速、暴击率 / 暴击伤害、护甲穿透、击退、远程伤害、吸血、反伤、处决、真实伤害、范围攻击、多重射击、额外打击……
- **防御**：最大生命、护甲、韧性、生命恢复、伤害减免、击退抗性、摔落抗性、格挡、闪避、吸收护盾、自动复活……
- **机动**：移速、游泳速度、跳跃、多段跳、抬腿、飞行速度、爬梯加速……
- **功能**：幸运、挖掘速度、掉落幸运、双倍战利品、合成加成、冷却缩减、修理量……

### 开关型属性

- 飞行、夜视、水下呼吸、免饥饿、隐身、免摔落伤害、火焰 / 弹射物 / 爆炸免疫……
- **连锁挖掘**：一次破坏成片同类方块；右键还可整片剥皮 / 铺路 / 耕地 / 收割作物
- **矿透**：周围已加载区块里的矿石描边并**穿透方块**显示，按矿物种类着色；半径、描边上限与要显示的种类在「矿透设置」面板里调
- 自动钓鱼、立即咬钩、自动冶炼、自动修理、繁殖无冷却、一键长大、物品 / 经验磁铁
- **满饱食度进食**：饱食度已满时仍可继续进食

### 随身工具

- 工作台、熔炉、铁砧、末影箱、锻造台、酿造台、切石机、织布机、制图台、磨石
- 从「随身工具」面板进入（默认 `G`）：**左键**解锁 / 打开，**右键**关闭并退还点数
- 材料可以直接从 RS / AE2 / 汤姆存储等存储网络中提取
- 随身熔炉支持 Shift 连续放料、待炼仓排队，空闲时保留余热
- 随身末影箱与原版末影箱**共用同一份库存**
- 随身酿造台自带无限燃料，关闭界面后仍会继续酿造

### 附魔系统

- **随身进阶高级附魔台**：附魔不再随机抽取，可以自选
- 支持升级 / 降级 / 移除已有附魔，降级会**退还差价**
- 花经验将耐久**直接修满**；拆解全部附魔返还 **60%** 经验
- 槽内放入**书**即可制作附魔书
- 付款可使用**经验等级或属性点数**，不消耗青金石
- 互斥附魔（锋利 / 亡灵杀手 / 节肢杀手等）可同时附在同一件装备上
- **附魔上限突破**（1 点）：附魔台可自选到任意等级，铁砧可将同等级附魔继续叠加

### 生存辅助

- **死亡不掉落**：死亡时保留背包 + 盔甲 + 副手，即使服务器关闭 `keepInventory` 也不会掉落
- **死亡不掉经验**
- **原地复活**：可配置冷却时间，复活后短暂无敌
- **村民交易折扣**（最高 -90%），交易后**立即补货**
- **铁砧经验减免**（最高 -90%）
- **自动入库**：每隔 1 秒将背包物品存入存储网络，可调整存储优先级、设置白 / 黑名单
- 时间加速

### 枪械模组联动（TACZ / 卓越前线）

- **无限子弹** —— 弹匣保持满仓，无需换弹
- **零热量** —— 持续射击也不会过热
- **无后坐力** —— 准心不会被后坐力顶起
- 卓越前线的**载具武器**同样有效

### 其它模组联动

- **无限能源** —— 背包 / 饰品 / 坐骑 / 自己放置的能量方块上的能量保持满格（FE / RF）
- **无限载具血量**（卓越前线）、**无限应力**（机械动力）、**无限灵魂能量**（诡厄巫法）
- **无限魔力** —— 一个属性同时覆盖 Iron's 法术与魔法书（玩家法力）、Ars Nouveau（魔源）、
  Botania（魔力石板 / 魔力戒指等携带魔力 + 你自己放下的魔力池，放下去就永久满、不限距离），装哪个哪个生效
- **免口渴**（口渴）、**免体温**（冷汗）：口渴条恒满、体温钉在舒适区
- **传送点面板**里直接列出**已激活的传送石碑**（Waystones），点「传送」即免费传送（不扣经验、不进冷却）
- **物品编辑器**可直接修改拔刀剑的杀敌数 / 耀魂数 / 锻造数 / SA / SE

### 面板与界面

- 属性面板 `P`、HUD `H`、物品编辑器 `O`、EMC 转化桌 `V`、成就 / 统计 `U`、传送点 `Y`、随身工具 `G`
- **生物面板**：解锁后获得一根**生物观察杖**（带标记的木棍），手持它右键任意生物即可打开该生物的面板
  - **属性**页：列出它身上的**全部属性**（生命上限 / 护甲 / 攻击力 / 移动速度……连其它模组添加的属性也在内），
    悬停某行能看到注册名、基础值 / 最终值、**默认值**与取值范围；**左键点一行即可把它的最终值设为任意数字**
    （回车确认；带 buff / 装备时也会自动扣掉修饰符那部分再写基础值，`Shift + 左键` 恢复该生物类型的默认值）
  - **开关**页：点一下就能直接改这只生物 —— **AI**（关掉就是木头人）、**幼年 / 成年**、**静音**、**不可见**、
    **发光**、**无敌**、**无重力**
  - 改动一律由服务端执行并回传权威状态（客户端改不出来），数值会被夹进该属性自身的取值范围
  - 关闭属性时木棍自动收回
- **传送点**：保存 / 删除 / 传送，支持**跨维度传送**
- **天气控制**：一键切换晴天 / 下雨 / 雷暴
- **区块强加载**：将指定区块设为强加载，重启后依然有效
- **功能开关**：113 条属性可逐条关闭（每个玩家独立），关闭后从面板隐藏，对应功能入口一并消失
- **效果过滤**：黑名单模式「拦截勾选的效果」（需开启 `debuff_immunity` 开关），白名单模式「保护勾选的效果」——
  自然结束前不会被任何外力清除（其它模组、牛奶桶、`/effect clear`、本模组的清效果逻辑都算外力）。
  名单条目支持 `minecraft:poison`、`somemod:*`（整个模组的全部效果）、`#forge:effect_tag`（效果标签）、`*`（全部效果），
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
| `G` | 打开随身工具面板 |

> 以上均可在「设置 → 按键绑定 → 无限加点」中修改。

---

## 💻 命令

### EMC 等价交换（`/emc`）

| 命令 | 说明 | 权限 |
|---|---|---|
| `/emc` | 查看自己当前的 EMC 值 | 玩家 |
| `/emc learn` | 学习手持物品 | 玩家 |
| `/emc learn all` | 学习背包中所有可学习的物品 | 玩家 |
| `/emc give <玩家> <数量>` | 给予指定玩家 EMC | 管理员 |
| `/emc reload` | 重新加载 EMC 数据库 | 管理员 |

### 传送 / 随身工具（`/infstats`）

> 下列命令均需要对应属性处于**激活状态**才能使用。

| 命令 | 说明 | 需求属性 |
|---|---|---|
| `/infstats crossdim` | 列出当前世界所有维度 | — |
| `/infstats crossdim <维度名>` | 跨维度传送（如 `overworld` / `nether` / `end`） | 跨维度传送 |
| `/infstats wp set <名称>` | 保存当前位置为传送点 | 定点传送 |
| `/infstats wp del <名称>` | 删除传送点 | 定点传送 |
| `/infstats wp list` | 列出所有传送点 | 定点传送 |
| `/infstats wp <名称>` | 传送到指定传送点 | 定点传送 |
| `/infstats craft` | 打开随身工作台 | 随身工作台 |
| `/infstats furnace` | 打开随身熔炉 | 随身熔炉 |
| `/infstats anvil` | 打开随身铁砧 | 随身铁砧 |
| `/infstats enderchest` | 打开随身末影箱 | 随身末影箱 |
| `/infstats smithing` | 打开随身锻造台 | 随身锻造台 |
| `/infstats brewing` | 打开随身酿造台 | 随身酿造台 |

### 功能开关（`/infstats feature`，需管理员权限）

| 命令 | 作用 |
|---|---|
| `/infstats feature` | 列出当前已关闭的功能 |
| `/infstats feature <属性ID>` | 切换该功能（开 ↔ 关） |
| `/infstats feature all on\|off` | 全部内置属性一次性开启 / 关闭 |
| `/infstats feature mode` | 查看「关掉时」的执行模式 |

### 属性点数（`/infstats points`）

| 命令 | 作用 | 权限 |
|---|---|---|
| `/infstats points` | 查看自己的等级、可用点数与已分配点数 | 玩家 |
| `/infstats points get <玩家>` | 查看指定玩家的点数情况 | 玩家（多目标需 OP） |
| `/infstats points add <数量>` | 给自己增加可用点数（负数 = 扣除） | 管理员 |
| `/infstats points add <玩家> <数量>` | 给指定玩家增加可用点数（负数 = 扣除） | 管理员 |
| `/infstats points set <玩家> <数量>` | 把指定玩家的可用点数设为固定值 | 管理员 |

> 点数只能加到「可用点数」上，不会自动分配；增加后客户端 HUD / 面板立即刷新。
> 整合包的任务奖励可以直接用 `/infstats points add @p 50` 这类写法。

---

## 📦 安装

1. 安装 Minecraft **1.20.1** 与对应 Forge（**47.3.0** 或更高）
2. 把 `infinite_stats-无限加点-*.jar` 放进 `.minecraft/mods/`
3. 启动游戏即可，**无需任何前置依赖**

下载时有两个包，选一个安装即可：

| 产物 | 使用场景 |
|---|---|
| `...-<版本>-all.jar` | **推荐**。自带拼音搜索库，搜索框支持拼音输入 |
| `...-<版本>.jar` | 整合包中已有 `pinyin_search` 时使用 |

---

## ⚙️ 配置

配置文件位于 **`config/infinitestats-common.toml`**（全局生效）。
游戏内打开方式：**暂停菜单 → 模组 → 选中本模组 →「Config」按钮**，修改后即时生效。

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
| `PassiveEffects.projectileTrackingRange` | `64` | 弹射物追踪扫描半径（8–256） |
| `GUI.showHiddenStats` | `false` | 是否显示隐藏属性（如无敌） |
| `GUI.featureDisableMode` | `keep` | 功能开关「关掉时」的执行模式 |
| `GUI.hide*Panel` | `false` | 12 个面板入口开关（传送点 / 跨维度 / 过滤 / EMC / 成就 / 物品编辑器 / HUD / 随身工具 / 天气 / 强加载 / 连锁 / 矿透），配置界面里勾选即可 |
| `Compatibility.enableAttributeDiscovery` | `true` | 自动发现其它模组属性（需重启） |
| `EMC.emcEnabled` | `true` | 是否启用 EMC 系统 |
| `EMC.emcLossRate` | `0.0` | EMC 转换损耗率（0 = 无损耗） |
| `EMC.autoLearnProjectE` | `true` | ProjectE 自动学习开关 |
| `EMC.emcFallbackValue` | `0` | 未知物品兜底 EMC（0 = 关闭） |
| `TimeAccel.timeAccelRadius` | `4` | 「加速」属性的基础影响半径 |
| `Furnace.furnaceSpeedCost` | `5` | 每级随身熔炉速度消耗的属性点 |
| `Crafting.craftingMultiplierCost` | `5` | 每级随身工作台倍率消耗的属性点 |
| `NetworkPriority.networkPriority` | `[RS, AE2, TOMS, BACKPACK, BD]` | 存储网络自动选择的优先级（需重启） |
| `AutoDeposit.autoDepositInterval` | `20` | 「自动入库」扫描间隔（tick） |
| `AutoDeposit.autoDepositKeepHotbar` | `true` | 是否保留快捷栏物品不被自动入库 |
| `AutoDeposit.autoDepositBlacklist` | `[]` | 服务端级自动入库黑名单（所有玩家生效） |
| `WeatherControl.weatherCycleDuration` | `6000` | 天气切换后的持续时间（tick，默认 5 分钟） |
| `ChunkLoader.maxForcedChunks` | `16` | 每个维度最多同时强加载的区块数量（上限 256） |
| `ExtraStrike.extraStrikeDamageRatio` | `0.2` | 「额外打击」每次追加的伤害比例 |
| `InfiniteEnergy.infiniteEnergyBlocks` | `true` | 「无限能源」是否也补满自己放置的能量方块 |
| `InfiniteEnergy.energyBlockRadius` | `16` | 能量方块的扫描半径（方块） |
| `InfiniteEnergy.energyBlockInterval` | `20` | 能量方块的扫描 / 补电间隔（tick） |
| `AlwaysEat.alwaysEatOnlyList` | `[]` | 「满饱食度进食」限制名单（留空 = 所有食物） |
| `AlwaysEat.alwaysEatExcludeList` | `[]` | 「满饱食度进食」例外名单（优先级更高） |
| `EntityPanel.allowEntityEditing` | `true` | 是否允许从**生物面板**直接改生物（开关 + 属性数值）；关掉则面板只读 |
| `CreateStress.infiniteStressCapacity` | `1000000000` | 「无限应力」（机械动力）把动力网络产能改写成的数值（SU）；调小 = 给无限应力设上限，超出的大网络仍会超载 |
| `Progression.passiveXpEnabled` | `true` | 被动经验总开关（关掉后在线玩家不再每秒获得经验） |
| `Progression.xpPerKillCountSummons` | `false` | 击杀经验是否计入召唤物 / 宠物造成的击杀 |
| `Combat.*` | 见文件 | 21 项战斗结算细节：暴击基础倍率 / 暴击率·闪避率·格挡率·减伤·反伤·双倍掉落上限、吸血上限、范围吸血半径与比例、额外打击次数、范围攻击比例、处决阈值、村民折扣上限、冷却缩减上限、自动修复量、追踪视野锥、经验加成上限 |
| `Modules.*` | 见文件 | 23 项模块间隔与阈值：磁铁 / 追踪 / 免饥饿 / 自动修复 / 属性重写 / 移动状态的执行间隔、自动跨越高度、无敌回血与护盾刷新、钓鱼视线与咬钩、酿造时长、单次合成上限、附魔台修复与回收、魔力池、自动学习扫描、生物面板距离、矿石透视、连锁消耗 |
| `Stats.<属性id>_perPoint` | 各属性原值 | **每个内置属性**的每点收益（开关型属性没有此项） |
| `Stats.<属性id>_maxPoints` | 各属性原值 | **每个内置属性**的可投入点数上限；开关型属性即解锁所需点数 |

> 大部分配置修改后即时生效，标注「需重启」的除外。
> `Stats.*` 的调参会在配置加载 / 重载后自动重新应用；专用服务器上客户端不会收到服务端的调参值，
> GUI 里显示的「每点收益」以本地配置为准，实际效果始终以服务端为准。

---

## 🧬 内置属性一览（共 113 个）

> 标 **[开关]** 的为功能型开关，激活后即时生效，可随时开启 / 关闭。

### ⚔️ 攻击（21）

`attack_damage` 攻击伤害 · `attack_speed` 攻击速度 · `crit_chance` 暴击率 · `crit_damage` 暴击伤害 · `armor_penetration` 护甲穿透 · `knockback_power` 击退力度 · `projectile_damage` 远程伤害 · `life_steal` 生命偷取 · `life_steal_aoe` 范围吸血 · `damage_reflection` 反伤 · `execute` 处决 · `true_damage` 真实伤害 · `reduce_max_health` 削弱最大生命 · `scope_attack` 范围攻击 · `repulsion` 斥力 · `infinite_arrows` **[开关]** 无限弓箭（弓 / 弩） · `infinite_ammo` **[开关]** 无限子弹 · `no_heat` **[开关]** 零热量 · `no_recoil` **[开关]** 无后坐力 · `multi_shot` 多重射击 · `extra_strike` 额外打击

### 🛡️ 防御（17，含 1 隐藏）

`max_health` 最大生命 · `armor` 护甲 · `armor_toughness` 盔甲韧性 · `health_regen` 生命恢复 · `damage_reduction` 伤害减免 · `knockback_resist` 击退抗性 · `fall_resist` 摔落抗性 · `block_chance` 格挡几率 · `dodge_chance` 闪避几率 · `absorption_shield` 吸收护盾 · `auto_revive` 自动复活 · `fire_immunity` **[开关]** 火焰免疫 · `projectile_immunity` **[开关]** 弹射物免疫 · `explosion_immunity` **[开关]** 爆炸免疫 · `suffocation_immunity` **[开关]** 窒息免疫 · `debuff_immunity` **[开关]** 效果过滤 · `invincibility`（隐藏）无敌

### 🏃 机动（10）

`movement_speed` 移动速度 · `swim_speed` 游泳速度 · `jump_height` 跳跃高度 · `multi_jump` N 段跳 · `step_height` 抬腿高度 · `auto_step` **[开关]** 自动抬腿 · `fly_speed` 飞行速度 · `fly` **[开关]** 飞行 · `climb_speed` 爬梯加速 · `no_fall_damage` **[开关]** 免摔落伤害

### 🧰 功能（54 + 11 个随身工具）

> 11 个 `portable_*`「随身工具」属性统一在**随身工具面板**里用左键解锁 / 右键关闭，因此不再显示在属性面板。

`luck` 幸运 · `mining_speed` 挖掘速度 · `mining_level` 挖掘等级 · `reach` 方块交互距离 · `entity_reach` 实体交互距离 · `xp_gain` 经验获取 · `loot_luck` 掉落幸运 · `double_loot` 双倍战利品 · `item_magnet` **[开关]** 物品磁铁 · `xp_magnet` **[开关]** 经验磁铁 · `auto_deposit` **[开关]** 自动入库 · `vein_miner` **[开关]** 连锁挖掘 · `ore_vision` **[开关]** 矿透 · `auto_smelt` **[开关]** 自动冶炼 · `auto_fish` **[开关]** 自动钓鱼 · `instant_bite` **[开关]** 立即咬钩 · `breed_no_cooldown` **[开关]** 繁殖无冷却 · `instant_grow` **[开关]** 一键长大 · `crafting_bonus` 合成加成 · `auto_repair` **[开关]** 自动修理 · `repair_amount` 修理量 · `use_speed` 使用速度 · `bow_draw_speed` 拉弓加速 · `cooldown_reduction` 冷却缩减 · `projectile_tracking` **[开关]** 弹射物追踪 · `no_invincibility_frames` **[开关]** 取消无敌帧 · `night_vision` **[开关]** 夜视 · `water_breathing` **[开关]** 水下呼吸 · `no_hunger` **[开关]** 免饥饿 · `no_thirst` **[开关]** 免口渴 · `no_temperature` **[开关]** 免体温 · `always_eat` **[开关]** 满饱食度进食 · `invisibility` **[开关]** 隐身 · `keep_inventory` **[开关]** 死亡不掉落 · `keep_xp` **[开关]** 死亡不掉经验 · `time_accel` **[开关]** 时间加速 · `time_accel_radius` 加速半径 · `cross_dimension_teleport` **[开关]** 跨维度传送 · `fixed_point_teleport` **[开关]** 定点传送 · `enchant_limit` **[开关]** 附魔上限突破 · `weather_control` **[开关]** 天气控制 · `chunk_loader` **[开关]** 区块强加载 · `entity_panel` **[开关]** 生物面板 · `trade_discount` 村民交易折扣 · `trade_restock` **[开关]** 交易即刻补货 · `anvil_cost` 铁砧经验减免 · `pe_auto_learn` **[开关]** PE 自动学习 · `emc_learn_bonus` EMC 学习收益 · `emc_extract_discount` EMC 转化折扣 · `infinite_energy` **[开关]** 无限能源（FE/RF） · `sbw_infinite_health` **[开关]** 无限载具血量 · `goety_infinite_soul` **[开关]** 无限灵魂能量 · `create_infinite_stress` **[开关]** 无限应力 · `infinite_mana` **[开关]** 无限魔力

### 🌐 外部属性（动态）

开启 `Compatibility.enableAttributeDiscovery`（默认开）后，其它模组注册的能力属性会自动归并到「外部属性」分类，可像内置属性一样加点。遇到兼容性问题时可关闭该选项（**需重启**）。

---

## 🌏 外部属性的中文翻译

不少模组**自己没有提供中文译文**，GUI 中会显示成 `[gunsmithlib:bullet_damage]` 这样的原始键名。本模组已将常见模组的译文**内置进语言文件**，安装即可使用，所有玩家都能看到中文。

已覆盖（部分）：

`gunsmithlib`、`attributeslib`、`goety` / `goety_revelation` / `goeticlegacy` / `goetytranscended`、`taa`、`touhou_little_maid`、`wizard_terra_cuiros`、`l2damagetracker`、`l2hostility`、`celestial_core`、`flame_chase_artifacts`、`additional_attributes`、`fallen_gems_affixes`、`terra_entity`、`ending_library`、`cataclysm`、`apotheosis_modern_ragnarok`、`reach-entity-attributes`、`qiyuemod`、`blessingofpandora`、`curseofpandora`、`tacz`、`tcc`、`alexscaves_torpedoes`、`ftbultimine`、`caelus`、`vengeance`、`slashblade`、`block_factorys_bosses`、`monsterexpansion`、`confluence`、`sal_fishs_attribute_lib`、`additionalentityattributes`、`obscure_api`。

启动时若发现「连所属模组都没提供译文」的属性，会把清单写入 `config/infinitestats/external_translations.json`，方便自行制作资源包补充。

---

## 🔍 拼音搜索

属性面板 / 效果过滤器 / 物品编辑器 / 物品选择 / 入库过滤 / EMC 转化桌 / 成就统计面板的搜索框均支持拼音，例如 `zsj` → 钻石剑、`tiezh` → 铁砧。

- 安装 `-all` 版（自带拼音库）即可使用；安装标准名版本则需整合包内另有 `pinyin_search`，否则搜索框退化为纯文本匹配（其它功能不受影响）。
- 已知限制：英文词首字母无法搜索（`Diamond Sword` 搜不到 `DS`）；简体与繁体不互通（`钻石剑` 与 `鑽石劍` 互相搜不到）。

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
