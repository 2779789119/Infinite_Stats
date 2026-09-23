# 无限加点模组（Infinite Stats）

> 一个泰拉瑞亚（Terraria）风格的 **无限属性加点系统** Minecraft Forge 模组。
> 通过打怪与挂机获取经验升级，获得可自由分配的属性点，打造属于你自己的 Build。

- **Mod ID**：`infinitestats`
- **当前版本**：`1.8.0`（见 `gradle.properties` → `mod_version`）
- **运行环境**：Minecraft `1.20.1` / Forge `47.4.x+`
- **许可证**：MIT

---

## ✨ 核心特性

- 🎯 **经验升级加点**：击杀怪物与挂机被动获取经验，升级获得可分配属性点（默认每级 3 点）。
- 📊 **78+ 内置属性**，分为五大类：攻击 / 防御 / 机动 / 功能 / 魔法。
- 🔌 **自动发现外部属性**：开启后，其他模组注册的属性会自动出现在「外部属性」分类中，无需手动适配。
- 🖥️ **属性面板 GUI**（默认 `P`）：分类浏览、搜索、一键加点、重置。
- 📟 **HUD 实时显示**（默认 `H` 开关）：在游戏界面直接展示当前属性与点数。
- 🛠️ **物品编辑器**（默认 `O`）：可视化编辑物品的附魔、词条、NBT 等元数据。
- 💎 **内置等价交换（EMC）系统**（默认 `V`）：将物品转化为 EMC 并反向兑换，支持学习/查询/管理员发放。
- 🎒 **随身工作台 / 随身熔炉**：通过 `/infstats craft`、`/infstats furnace` 或对应属性随时打开。
- 🧭 **传送点 & 跨维度传送**：保存定点传送（`/infstats wp`）、一键跨维度（`/infstats crossdim`）。
- 🏆 **成就 / 统计面板**（默认 `U`）：查看与模组相关的进度统计。
- 👥 **完整多人联机支持**：属性、EMC、传送点等数据均随玩家存档同步到服务端。

---

## 📦 安装

1. 安装 Minecraft `1.20.1` 与对应 Forge（`47.4.x` 或更高）。
2. 下载本模组 `infinite_stats-*.jar`，放入 `.minecraft/mods/` 文件夹。
3. 启动游戏即可，无需额外前置依赖（Forge 自带 Mixin 支持）。

---

## 🎮 快速上手

1. 进入世界后，击杀怪物或挂机即可在经验条积累经验。
2. 升级时获得属性点（默认每级 3 点）。
3. 按 **`P`** 打开属性面板，选择分类，点击属性右侧的 `+` 即可加点；也可按 **`=`(加号键)** 快速加点。
4. 按 **`H`** 开关屏幕 HUD，随时查看属性与剩余点数。
5. 功能类中的「开关型」属性（如飞行、夜视、连锁挖掘等）在激活后即时生效，可在面板中开关。

---

## ⌨️ 按键绑定

| 按键（默认） | 功能 |
|------------|------|
| `P` | 打开属性面板 |
| `=`（等号/加号键） | 快速加点 |
| `H` | 开关 HUD 显示 |
| `O` | 打开物品编辑器 |
| `V` | 打开 EMC 转化桌 |
| `U` | 打开成就 / 统计面板 |
| `Y` | 打开传送点面板 |

> 以上按键均可在游戏内「设置 → 按键绑定 → 无限加点模组」中修改。

---

## 💻 命令

### EMC 等价交换（`/emc`）

| 命令 | 说明 | 权限 |
|------|------|------|
| `/emc` | 查看自己的当前 EMC 值 | 玩家 |
| `/emc learn` | 学习手持物品（获得其 EMC 价值） | 玩家 |
| `/emc learn all` | 学习背包中所有有 EMC 价值的物品 | 玩家 |
| `/emc give <玩家> <数量>` | 给予指定玩家 EMC | 管理员（≥2） |
| `/emc reload` | 重新加载 EMC 数据库 | 管理员（≥2） |

### 传送 / 随身工具（`/infstats`）

> 下列命令均需要对应属性处于激活状态才能使用。

| 命令 | 说明 | 需求属性 |
|------|------|---------|
| `/infstats crossdim` | 列出当前世界所有维度 | — |
| `/infstats crossdim <维度名>` | 强制跨维度传送（如 `overworld`/`nether`/`end` 或 `<modid>:<维度>`） | 跨维度传送 |
| `/infstats wp set <名称>` | 保存当前位置为传送点 | 定点传送 |
| `/infstats wp del <名称>` | 删除传送点 | 定点传送 |
| `/infstats wp list` | 列出所有传送点 | 定点传送 |
| `/infstats wp <名称>` | 传送到指定传送点 | 定点传送 |
| `/infstats craft` | 打开随身工作台 | 内置工作台 |
| `/infstats furnace` | 打开随身熔炉 | 内置熔炉 |

随身熔炉支持 **Shift 连续放料**：燃料优先放入燃料槽，可熔炼材料先补入当前输入，不同材料进入待炼仓排队。手动将木头放入材料槽或待炼仓可烧木炭。主界面和成品仓都能一键收取，背包放不下的成品继续留仓。空闲时保留余热，缺燃料时保留进度；仓库内按 Esc 可返回熔炉。物品悬停显示精确库存数量。

本次熔炉改动的验证状态见 [优化与验证记录](docs/portable-furnace-optimization.md)。

---

## ⚙️ 配置

配置文件位于 `config/infinitestats-server.toml`（服务器管理员可调整平衡性，修改后无需重启游戏即大部分生效）。

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `experience.xpPerKillBase` | `20` | 击杀怪物基础经验值 |
| `experience.xpPerKillHealthFactor` | `3.0` | 怪物最大生命值每点附加的经验系数 |
| `experience.passiveXpAmount` | `2` | 被动获取的经验值 |
| `experience.passiveXpInterval` | `80` | 被动经验间隔（tick，20tick=1秒） |
| `leveling.baseXpPerLevel` | `60` | 升到 2 级所需基础经验 |
| `leveling.xpPerLevelIncrement` | `30` | 每级增加的经验需求 |
| `leveling.pointsPerLevel` | `3` | 每次升级获得的属性点 |
| `autoRevive.autoReviveCooldown` | `300` | 自动复活冷却（秒，0=无冷却） |
| `autoRevive.autoReviveHealthPercent` | `0.3` | 复活后恢复生命百分比 |
| `passiveEffects.healthRegenInterval` | `100` | 生命恢复间隔（tick） |
| `passiveEffects.manaRegenInterval` | `40` | 法力恢复间隔（tick） |
| `passiveEffects.magnetRange` | `10` | 物品/经验磁铁吸引范围 |
| `passiveEffects.veinMinerMaxBlocks` | `64` | 连锁挖掘最大方块数 |
| `passiveEffects.projectileTrackingRange` | `64` | 弹射物追踪扫描半径（方块），范围 8-256，默认 64 |
| `gui.showHiddenStats` | `false` | 是否显示隐藏属性（如无敌），改后重开面板生效 |
| `compatibility.enableAttributeDiscovery` | `true` | 自动发现其他模组属性（**需重启**） |
| `emc.emcEnabled` | `true` | 是否启用 EMC 系统 |
| `emc.emcLossRate` | `0.0` | EMC 转换损耗率（0=无损耗，1=全损耗） |
| `timeAccel.timeAccelRadius` | `4` | 「加速」属性基础影响半径（方块） |
| `furnace.furnaceSpeedCost` | `5` | 每级随身熔炉速度消耗的属性点 |
| `crafting.craftingMultiplierCost` | `5` | 每级随身工作台倍率消耗的属性点 |

---

## 🧬 内置属性一览

> 共 **80** 个内置属性。标注 **[开关]** 的为功能型开关，激活后即时生效，可随时开启/关闭。

### ⚔️ 攻击（15）

`attack_damage` 攻击伤害 · `attack_speed` 攻击速度 · `crit_chance` 暴击率 · `crit_damage` 暴击伤害 · `armor_penetration` 护甲穿透 · `knockback_power` 击退力度 · `projectile_damage` 远程伤害 · `life_steal` 生命偷取 · `life_steal_aoe` 范围吸血 · `damage_reflection` 反伤 · `execute` 处决 · `true_damage` 真实伤害 · `reduce_max_health` 削弱最大生命 · `scope_attack` 瞄准攻击 · `repulsion` 斥力

### 🛡️ 防御（17，含 1 隐藏）

`max_health` 最大生命 · `armor` 护甲 · `armor_toughness` 盔甲韧性 · `health_regen` 生命恢复 · `damage_reduction` 伤害减免 · `knockback_resist` 击退抗性 · `fall_resist` 摔落抗性 · `fire_immunity` **[开关]** 火焰免疫 · `projectile_immunity` **[开关]** 弹射物免疫 · `explosion_immunity` **[开关]** 爆炸免疫 · `suffocation_immunity` **[开关]** 窒息免疫 · `auto_revive` 自动复活 · `block_chance` 格挡几率 · `absorption_shield` 吸收护盾 · `dodge_chance` 闪避几率 · `debuff_immunity` **[开关]** 负面效果免疫 · `invincibility`（隐藏）无敌

### 🏃 机动（9，含若干开关）

`movement_speed` 移动速度 · `swim_speed` 游泳速度 · `jump_height` 跳跃高度 · `step_height` 跨步高度 · `fly_speed` 飞行速度 · `fly` **[开关]** 飞行 · `no_fall_damage` **[开关]** 免摔落伤害 · `auto_step` **[开关]** 自动跨步 · `follow_range` 仇恨范围

### 🧰 功能（29，含多个开关）

`luck` 幸运 · `mining_speed` 挖掘速度 · `mining_level` 挖掘等级 · `reach` 方块交互距离 · `entity_reach` 实体交互距离 · `xp_gain` 经验获取 · `loot_luck` 战利品幸运 · `night_vision` **[开关]** 夜视 · `water_breathing` **[开关]** 水下呼吸 · `no_hunger` **[开关]** 免饥饿 · `item_magnet` 物品磁铁 · `invisibility` **[开关]** 隐身 · `vein_miner` **[开关]** 连锁挖掘 · `auto_smelt` **[开关]** 自动冶炼 · `xp_magnet` 经验磁铁 · `no_invincibility_frames` **[开关]** 取消无敌帧 · `double_loot` 双倍战利品 · `crafting_bonus` 合成加成 · `bow_draw_speed` 拉弓速度 · `use_speed` 使用速度 · `auto_repair` **[开关]** 自动修复 · `repair_amount` 修复量 · `time_accel` **[开关]** 时间加速 · `time_accel_radius` 加速半径 · `cross_dimension_teleport` **[开关]** 跨维度传送 · `fixed_point_teleport` **[开关]** 定点传送 · `portable_crafting` **[开关]** 内置工作台 · `portable_furnace` **[开关]** 内置熔炉 · `projectile_tracking` 弹射物追踪 **[开关]**

### 🔮 魔法（8）

`max_mana` 最大法力 · `mana_regen` 法力恢复 · `magic_damage` 魔法伤害 · `mana_shield` 法力护盾 · `mana_steal` 法力偷取 · `mana_on_kill` 击杀回蓝

### 🌐 外部属性（动态）

开启 `compatibility.enableAttributeDiscovery`（默认开）后，游戏内其他模组注册的能力属性会自动归并到「外部属性」分类，并可像内置属性一样加点。遇到兼容性问题时可关闭该选项（需重启）。

---

## 🔧 开发 / 构建

本项目为标准 ForgeGradle 工程，使用 Java 17 / Gradle 8.8。

```bash
# 生成可运行客户端（构建产物在 build/libs/）
./gradlew build

# 仅重新生成 IDE 运行配置
./gradlew genEclipseRuns   # 或 gradlew genIntellijRuns
```

源码结构：

```
src/main/java/com/infinitestats/
├── InfiniteStats.java          # 模组入口
├── Config.java                 # Forge 配置
├── client/                     # 各类 GUI 与客户端逻辑（属性面板/EMC/物品编辑器/HUD/传送点/成就…）
├── command/                    # 服务端命令（/infstats）
├── compat/jei/                 # JEI 配方查看集成
├── crafting/                   # 随身工作台
├── emc/                        # EMC 等价交换系统
├── event/                      # 事件总线
├── furnace/                    # 随身熔炉 / 燃料缓冲
├── handler/                    # 属性效果处理器（攻击/防御/机动/魔法/功能/时间加速…）
├── network/                    # 网络同步数据包
├── stats/                      # 玩家属性数据、属性类型、传送点
└── util/                       # 工具类（如传送）
```

---

## 📄 许可证

本项目以 **MIT 许可证** 发布。详见仓库 `LICENSE`（如缺失，遵循 MIT 条款：可自由使用、修改、分发，须保留版权声明）。
