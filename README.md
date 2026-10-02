# 无限加点（Infinite Stats）

> 泰拉瑞亚（Terraria）风格的**无限属性加点系统** —— 打怪升级 → 自由分配属性点 → 打造属于自己的 Build。

| | |
|---|---|
| **Mod ID** | `infinitestats` |
| **当前版本** | `1.17.2`（以 `gradle.properties` 的 `mod_version` 为准） |
| **运行环境** | Minecraft `1.20.1` / Forge `47.3.0+` |
| **许可证** | MIT |

---

## ✨ 核心特性

- 🎯 **经验升级加点** —— 击杀怪物与挂机被动获取经验，升级发放可分配属性点
- 📊 **72 个内置属性**，分为攻击 / 防御 / 机动 / 功能四大类
- 🔌 **自动发现外部属性** —— 其他模组注册的属性自动收进「外部属性」分类，无需手动适配
- 🌏 **内置 30+ 模组的外部属性中文译文** —— 装包即用，不再显示 `[gunsmithlib:bullet_damage]` 这类原始键名
- 🖥️ **属性面板 GUI**（默认 `P`）—— 分类浏览 / 搜索 / 一键加点 / 重置
- 📟 **HUD 实时显示**（默认 `H` 开关）
- 🛠️ **物品编辑器**（默认 `O`）—— 可视化编辑物品的附魔、词条、NBT
- 💎 **内置 EMC 等价交换**（默认 `V`）—— 学习 / 查询 / 转化，支持 ProjectE 联动
- 🎒 **随身工作台 / 随身熔炉 / 随身铁砧** —— 自动补料、待炼仓排队、支持存储网络抽取
- 🔀 **Polymorph（多态合成）兼容** —— 装了 Polymorph 时，随身工作台与它共用同一套配方选择，冲突配方可在工作台里直接切换
- 🧭 **传送点与跨维度传送**
- 🏆 **成就 / 统计面板**（默认 `U`）
- 👥 **完整多人联机支持** —— 属性、EMC、传送点等数据随玩家存档同步到服务端

---

## 📦 安装

1. 安装 Minecraft `1.20.1` 与对应 Forge（`47.3.0` 或更高）
2. 把 `infinite_stats-*.jar` 放入 `.minecraft/mods/`
3. 启动游戏即可，**无需任何前置依赖**

---

## 🎮 快速上手

1. 进入世界后，击杀怪物或挂机即可积累经验
2. 升级时获得属性点（默认每级 **10** 点）
3. 按 **`P`** 打开属性面板，选择分类，点击属性右侧的 `+` 加点；也可按 **`=`** 快速加点
4. 按 **`H`** 开关屏幕 HUD，随时查看属性与剩余点数
5. 「开关型」属性（飞行、夜视、连锁挖掘等）激活后即时生效，可随时开关

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

---

## 💻 命令

### EMC 等价交换（`/emc`）

| 命令 | 说明 | 权限 |
|------|------|------|
| `/emc` | 查看自己当前的 EMC 值 | 玩家 |
| `/emc learn` | 学习手持物品 | 玩家 |
| `/emc learn all` | 学习背包中所有可学习的物品 | 玩家 |
| `/emc give <玩家> <数量>` | 给予指定玩家 EMC | 管理员（≥2） |
| `/emc reload` | 重新加载 EMC 数据库 | 管理员（≥2） |

### 传送 / 随身工具（`/infstats`）

> 下列命令均需要对应属性处于**激活状态**才能使用。

| 命令 | 说明 | 需求属性 |
|------|------|---------|
| `/infstats crossdim` | 列出当前世界所有维度 | — |
| `/infstats crossdim <维度名>` | 跨维度传送（如 `overworld` / `nether` / `end` 或 `<modid>:<维度>`） | 跨维度传送 |
| `/infstats wp set <名称>` | 保存当前位置为传送点 | 定点传送 |
| `/infstats wp del <名称>` | 删除传送点 | 定点传送 |
| `/infstats wp list` | 列出所有传送点 | 定点传送 |
| `/infstats wp <名称>` | 传送到指定传送点 | 定点传送 |
| `/infstats craft` | 打开随身工作台 | 随身工作台 |
| `/infstats furnace` | 打开随身熔炉 | 随身熔炉 |
| `/infstats anvil` | 打开随身铁砧 | 随身铁砧 |

随身熔炉支持 **Shift 连续放料**：燃料优先放入燃料槽，可熔炼材料先补入当前输入，不同材料进入待炼仓排队。手动将木头放入材料槽或待炼仓可烧木炭。主界面和成品仓都能一键收取，背包放不下的成品继续留仓。空闲时保留余热，缺燃料时保留进度；仓库内按 Esc 可返回熔炉。物品悬停显示精确库存数量。

本次熔炉改动的验证状态见 [优化与验证记录](docs/portable-furnace-optimization.md)。

---

## ⚙️ 配置

配置文件位于 **`config/infinitestats-common.toml`**（注册类型为 `COMMON`，全局生效，非 per-world）。

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
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
| `PassiveEffects.veinMinerMaxBlocks` | `64` | 连锁挖掘最大方块数 |
| `PassiveEffects.projectileTrackingRange` | `64` | 弹射物追踪扫描半径（方块，范围 8–256） |
| `GUI.showHiddenStats` | `false` | 是否显示隐藏属性（如 `invincibility` 无敌） |
| `Compatibility.enableAttributeDiscovery` | `true` | 自动发现其他模组属性（**需重启**） |
| `EMC.emcEnabled` | `true` | 是否启用 EMC 系统 |
| `EMC.emcLossRate` | `0.0` | EMC 转换损耗率（0 = 无损耗） |
| `EMC.autoLearnProjectE` | `true` | ProjectE 自动学习主开关（需已装 ProjectE） |
| `EMC.emcFallbackValue` | `0` | 未知物品兜底 EMC（0 = 关闭） |
| `TimeAccel.timeAccelRadius` | `4` | 「加速」属性的基础影响半径 |
| `Furnace.furnaceSpeedCost` | `5` | 每级随身熔炉速度消耗的属性点 |
| `Crafting.craftingMultiplierCost` | `5` | 每级随身工作台倍率消耗的属性点 |
| `NetworkPriority.networkPriority` | `[RS, AE2, TOMS, BACKPACK, BD]` | 存储网络自动选择的优先级（**需重启**） |

> 大部分配置改后即时生效，标注「需重启」的除外。

---

## 🧬 内置属性一览（共 73 个）

> 标 **[开关]** 的为功能型开关，激活后即时生效，可随时开启 / 关闭。

### ⚔️ 攻击（16）

`attack_damage` 攻击伤害 · `attack_speed` 攻击速度 · `crit_chance` 暴击率 · `crit_damage` 暴击伤害 · `armor_penetration` 护甲穿透 · `knockback_power` 击退力度 · `projectile_damage` 远程伤害 · `life_steal` 生命偷取 · `life_steal_aoe` 范围吸血 · `damage_reflection` 反伤 · `execute` 处决 · `true_damage` 真实伤害 · `reduce_max_health` 削弱最大生命 · `scope_attack` 范围攻击 · `repulsion` 斥力 · `infinite_arrows` **[开关]** 无限弓箭（弓 / 弩）

### 🛡️ 防御（17，含 1 隐藏）

`max_health` 最大生命 · `armor` 护甲 · `armor_toughness` 盔甲韧性 · `health_regen` 生命恢复 · `damage_reduction` 伤害减免 · `knockback_resist` 击退抗性 · `fall_resist` 摔落抗性 · `fire_immunity` **[开关]** 火焰免疫 · `projectile_immunity` **[开关]** 弹射物免疫 · `explosion_immunity` **[开关]** 爆炸免疫 · `suffocation_immunity` **[开关]** 窒息免疫 · `auto_revive` 自动复活 · `block_chance` 格挡几率 · `absorption_shield` 吸收护盾 · `dodge_chance` 闪避几率 · `debuff_immunity` **[开关]** 效果过滤 · `invincibility`（隐藏）无敌

### 🏃 机动（8）

`movement_speed` 移动速度 · `swim_speed` 游泳速度 · `jump_height` 跳跃高度 · `step_height` 抬腿高度 · `fly_speed` 飞行速度 · `fly` **[开关]** 飞行 · `no_fall_damage` **[开关]** 免摔落伤害 · `auto_step` **[开关]** 自动抬腿

### 🧰 功能（32）

`luck` 幸运 · `mining_speed` 挖掘速度 · `mining_level` 挖掘等级 · `reach` 方块交互距离 · `entity_reach` 实体交互距离 · `xp_gain` 经验获取 · `loot_luck` 掉落幸运 · `night_vision` **[开关]** 夜视 · `water_breathing` **[开关]** 水下呼吸 · `no_hunger` **[开关]** 免饥饿 · `item_magnet` **[开关]** 物品磁铁 · `invisibility` **[开关]** 隐身 · `vein_miner` **[开关]** 连锁挖掘 · `auto_smelt` **[开关]** 自动冶炼 · `xp_magnet` **[开关]** 经验磁铁 · `projectile_tracking` **[开关]** 弹射物追踪 · `no_invincibility_frames` **[开关]** 取消无敌帧 · `double_loot` 双倍战利品 · `crafting_bonus` 合成加成 · `bow_draw_speed` 拉弓加速 · `use_speed` 使用速度 · `auto_repair` **[开关]** 自动修理 · `repair_amount` 修理量 · `cooldown_reduction` 冷却缩减 · `time_accel` **[开关]** 时间加速 · `time_accel_radius` 加速半径 · `cross_dimension_teleport` **[开关]** 跨维度传送 · `fixed_point_teleport` **[开关]** 定点传送 · `portable_crafting` **[开关]** 随身工作台 · `portable_furnace` **[开关]** 随身熔炉 · `portable_anvil` **[开关]** 随身铁砧 · `pe_auto_learn` **[开关]** PE 自动学习

### 🌐 外部属性（动态）

开启 `Compatibility.enableAttributeDiscovery`（默认开）后，其他模组注册的能力属性会自动归并到「外部属性」分类，并可像内置属性一样加点。遇到兼容性问题时可关闭该选项（**需重启**）。

---

## 🌏 外部属性的中文翻译

外部属性的显示名取自属性自带的 `getDescriptionId()`。不少模组**自己都没提供中文译文**，GUI 里便会显示成 `[gunsmithlib:bullet_damage]` 这样的原始键名。

本模组已把**常见模组的译文直接内置进语言文件**（`assets/infinitestats/lang/zh_cn.json`），因此**装包即用**，所有玩家都能看到中文，不需要每个整合包各填一份。已覆盖：

`gunsmithlib`、`attributeslib`、`goety` / `goety_revelation` / `goeticlegacy` / `goetytranscended`、`taa`、`touhou_little_maid`、`wizard_terra_cuiros`、`l2damagetracker`、`l2hostility`、`celestial_core`、`flame_chase_artifacts`、`additional_attributes`、`fallen_gems_affixes`、`terra_entity`、`ending_library`、`cataclysm`、`apotheosis_modern_ragnarok`、`reach-entity-attributes`、`qiyuemod`、`blessingofpandora`、`curseofpandora`、`tacz`、`tcc`、`alexscaves_torpedoes`、`ftbultimine`、`caelus`、`vengeance`、`slashblade`、`block_factorys_bosses`、`monsterexpansion`、`confluence`、`sal_fishs_attribute_lib`、`additionalentityattributes`、`obscure_api`。

> **原理**：MC 的语言表是**跨 namespace 按 key 合并**的，所以 `infinitestats` 的 lang 里放别的模组的键照样生效；而这些键在原模组处本来就不存在，不会产生覆盖冲突。

### 遇到未覆盖的模组

启动时若发现「连所属模组都没提供译文」的属性，模组会把清单写到：

```
config/infinitestats/external_translations.json
```

⚠️ **这只是一个参考清单，模组不会读取它。** 里面每一项的值默认是 `[modid:attr]` 占位符，仅用于告诉你「哪些属性缺译文、它们的键名是什么」。要补上翻译，需要自行做成资源包（在 `assets/<任意namespace>/lang/zh_cn.json` 里写 `"<键名>": "<中文>"`），或直接修改模组语言文件后重新打包。

该文件**已存在时不会被覆盖**，所以手工整理过的内容不会在下次启动时被冲掉。

---

## 🔧 开发 / 构建

标准 ForgeGradle 工程，Java 17 / Gradle 8.5。

```bash
./gradlew build          # 构建（产物在 build/libs/）
./gradlew compileJava    # 只验证代码能否编译，比 build 快得多
./gradlew runClient      # 开发环境直接启动客户端（不需打包）
./gradlew genIntellijRuns  # 生成 IDE 运行配置
```

> `build` 的耗时大头是 `reobfJar`（Forge 全量重映射，**不可跳过**，跳过产物无法在正式客户端加载）。只想验证改动时用 `compileJava`，要进游戏测用 `runClient`。

源码结构：

```
src/main/java/com/infinitestats/
├── InfiniteStats.java     # 模组入口
├── Config.java            # Forge 配置
├── client/                # GUI 与客户端逻辑（属性面板 / HUD / 物品编辑器 / EMC / 传送点 / 成就…）
├── command/               # 服务端命令（/infstats）
├── compat/                # 兼容层（JEI、存储网络桥接等）
├── crafting/              # 随身工作台
├── emc/                   # EMC 等价交换
├── event/                 # 事件总线（属性效果总入口）
├── furnace/               # 随身熔炉 / 燃料缓冲
├── handler/               # 属性效果处理器（攻击 / 防御 / 机动 / 功能 / 时间加速 / 冷却…）
├── network/               # 网络同步数据包
├── stats/                 # 属性数据、属性类型、分类、传送点
└── util/                  # 工具类
```

---

## 📄 许可证

本项目以 **MIT 许可证** 发布。详见仓库 `LICENSE`。
