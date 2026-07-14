# Infinite Stats / 无限加点

[![MC Version](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.3.0%2B-orange)](https://files.minecraftforge.net/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)
[![Version](https://img.shields.io/badge/Version-1.4.0-purple)](https://github.com/example/infinitestats)

A **Terraria-style infinite stat leveling system** for Minecraft 1.20.1 Forge.  
Kill mobs, earn XP, level up, and freely allocate stat points to customize your character.

类似**泰拉瑞亚的无限属性加点系统**。击杀怪物获取经验升级，获得属性点自由分配，打造独一无二的角色。

---

## 作者 / Author

**佚名既无名**

---

## 核心特色 / Highlights

| 特色 | 说明 |
|------|------|
| 🎯 **60+ 属性** | 攻击/防御/机动/功能/魔法 五大分类 + 外部属性自动发现 |
| ♾️ **无限升级** | 无等级上限，属性可无限叠加，开关类能力到达阈值激活 |
| 🪙 **内置 EMC 系统** | 完整的等价交换系统，Bellman-Ford 自动计算，可转化物品 |
| 🖥 **物品编辑器** | 内置 NBT 编辑器，支持 Apotheosis 附魔扩展字段编辑 |
| 🌍 **多人联机** | 属性数据自动同步，客户端-服务端完整通信 |
| ⚙ **高度可配置** | 经验获取/升级曲线/冷却时间/磁铁范围等均可配置 |

---

## 属性分类 / Stat Categories

### 🟥 攻击 / Attack（11 个）

| 属性 | 类型 | 效果 |
|------|------|------|
| `attack_damage` | 叠加 | 攻击伤害，每点 +0.05 |
| `attack_speed` | 叠加 | 攻击速度，每点 +0.005 |
| `crit_chance` | 百分比 | 暴击率，每点 +0.5% |
| `crit_damage` | 百分比 | 暴击伤害，每点 +2.0% |
| `armor_penetration` | 百分比 | 护甲穿透，每点 +1.0% |
| `knockback_power` | 百分比 | 击退力，每点 +2.0% |
| `projectile_damage` | 百分比 | 弹射物伤害，每点 +3.0% |
| `life_steal` | 百分比 | 生命偷取，每点 +1.0% |
| `life_steal_aoe` | 百分比 | 范围吸血（击杀时），每点 +1.0% |
| `damage_reflection` | 百分比 | 伤害反射，每点 +1.0% |
| `execute` | 百分比 | 处决（目标低于 30% 血时增伤），每点 +2.0% |

### 🟦 防御 / Defense（17 个）

| 属性 | 类型 | 效果 |
|------|------|------|
| `max_health` | 叠加 | 最大生命值，每点 +2.0 |
| `armor` | 叠加 | 护甲值，每点 +0.5 |
| `armor_toughness` | 叠加 | 盔甲韧性，每点 +0.25 |
| `health_regen` | 叠加 | 生命恢复，每点 +0.05/5秒 |
| `damage_reduction` | 百分比 | 全伤害减免，每点 +0.2% |
| `knockback_resist` | 百分比 | 击退抗性，每点 +1.0% |
| `fall_resist` | 百分比 | 摔落抗性，每点 +1.0% |
| `block_chance` | 百分比 | 格挡率，每点 +1.0% |
| `absorption_shield` | 叠加 | 吸收护盾（黄心），每 4 点 +1 级 |
| `dodge_chance` | 百分比 | 闪避率，每点 +0.8% |
| `fire_immunity` | 3 级开关 | 免疫火焰伤害 |
| `projectile_immunity` | 5 级开关 | 免疫弹射物 |
| `explosion_immunity` | 5 级开关 | 免疫爆炸 |
| `suffocation_immunity` | 2 级开关 | 免疫窒息 |
| `debuff_immunity` | 4 级开关 | 免疫负面效果（支持黑白名单过滤） |
| `invincibility` | 1 级开关 | 🔒 隐藏属性 — 免疫一切伤害并保持满血 |
| `auto_revive` | 8 级开关 | 死亡自动复活（冷却可配，默认 5 分钟） |

### 🟩 机动 / Mobility（12 个）

| 属性 | 类型 | 效果 |
|------|------|------|
| `movement_speed` | 叠加 | 移动速度，每点 +0.001 |
| `swim_speed` | 百分比 | 游泳速度，每点 +0.3% |
| `jump_height` | 百分比 | 跳跃高度，每点 +0.5% |
| `step_height` | 百分比 | 跨越高度，每点 +0.6% |
| `fly_speed` | 百分比 | 飞行速度，每点 +0.2% |
| `fly` | 8 级开关 | 创造模式飞行 |
| `no_fall_damage` | 2 级开关 | 完全免疫摔落 |
| `auto_step` | 2 级开关 | 自动跨越 1 格方块 |
| `follow_range` | 叠加 | 生物跟踪范围，每点 +0.5 |

### 🟨 功能 / Utility（18 个）

| 属性 | 类型 | 效果 |
|------|------|------|
| `luck` | 叠加 | 幸运值，每点 +0.1 |
| `mining_speed` | 百分比 | 挖掘速度，每点 +1.0% |
| `reach` | 叠加 | 方块触及距离，每点 +0.04 |
| `entity_reach` | 叠加 | 实体触及距离，每点 +0.04 |
| `xp_gain` | 百分比 | 经验获取加成，每点 +2.0% |
| `loot_luck` | 百分比 | 掉落幸运，每点 +1.0% |
| `night_vision` | 2 级开关 | 永久夜视 |
| `water_breathing` | 2 级开关 | 水下呼吸 |
| `no_hunger` | 3 级开关 | 永不饥饿 |
| `item_magnet` | 2 级开关 | 物品磁铁（自动吸取掉落物） |
| `xp_magnet` | 2 级开关 | 经验磁铁（自动吸取经验球） |
| `invisibility` | 3 级开关 | 永久隐身 |
| `vein_miner` | 3 级开关 | 连锁挖掘（最大方块数可配） |
| `auto_smelt` | 2 级开关 | 自动冶炼（挖掘直接出冶炼品） |
| `no_invincibility_frames` | 5 级开关 | 取消无敌帧 |
| `double_loot` | 百分比 | 双倍掉落，每点 +0.5% |
| `teleport_distance` | 叠加 | 传送距离，每点 +5.0 |
| `crafting_bonus` | 百分比 | 额外合成，每点 +0.5% |
| `bow_draw_speed` | 百分比 | 拉弓加速，每点 +3.0% |
| `use_speed` | 百分比 | 使用速度（吃东西/喝药/盾牌），每点 +2.0% |
| `auto_repair` | 2 级开关 | 自动修理背包和装备栏所有物品 |
| `repair_amount` | 叠加 | 每次修理耐久值，每点 +1 |

### 🟪 魔法 / Magic（8 个）

| 属性 | 类型 | 效果 |
|------|------|------|
| `max_mana` | 叠加 | 最大法力值，每点 +10.0 |
| `mana_regen` | 叠加 | 法力恢复速度，每点 +0.5 |
| `magic_damage` | 百分比 | 魔法伤害，每点 +3.0% |
| `cooldown_reduction` | 百分比 | 冷却缩减，每点 +0.3% |
| `mana_shield` | 百分比 | 法力护盾，每点 +1.0%（消耗法力抵消伤害） |
| `mana_steal` | 百分比 | 法力窃取，每点 +1.0% |
| `spell_power` | 百分比 | 法术强度，每点 +2.5% |
| `mana_on_kill` | 叠加 | 击杀回蓝，每点 +2.0 |

### 🟪 外部属性 / External — 自动发现

开启 `enableAttributeDiscovery`（默认开启）后，自动扫描所有其他模组注册的属性并加入 GUI 的「外部属性」分类。按命名空间自动分组折叠，支持自定义翻译。

---

## EMC 等价交换系统 / EMC System

v1.4.0 起内置完整的等价交换系统：

- **Bellman-Ford 自动计算**：遍历所有合成/烧炼/锻造/切石配方，迭代收敛计算 EMC 值
- **手动锚点**：`config/infinitestats/emc_values.json` 预设 120+ 基础物品锚点
- **漏洞防护**：循环检测、原矿黑名单、0-EMC 物品过滤
- **ProjectE 互通**：若检测到 ProjectE 安装，自动通过反射完全互通 EMC 值
- **转化桌 GUI**：搜索 → 学习 → 提取（x1/x10/x64），EMC 余额实时显示

### EMC 命令 / Commands

| 命令 | 功能 |
|------|------|
| `/emc` | 查看 EMC 余额和已学物品数 |
| `/emc learn` | 学习手持物品 |
| `/emc learn all` | 学习背包中全部物品 |
| `/emc give <玩家> <数量>` | 管理员给予 EMC |
| `/emc reload` | 重载 EMC 数据库（管理员） |

---

## 物品编辑器 / Item Editor

v1.3.0 起内置物品 NBT 编辑器：

- **附魔编辑**：添加/修改/删除附魔，支持超过原版等级上限
- **扩展 NBT 支持**：完整读写 Apotheosis 等模组的附魔扩展字段（宝石、词缀等）
- **通用物品编辑**：修改物品名称、Lore、耐久、堆叠数量等

---

## 键位与操作 / Controls

| 按键 | 功能 |
|------|------|
| `P` | 打开属性加点面板 |
| 左键点击属性 | 分配 1 点 |
| 右键点击属性 | 撤回 1 点 |
| Shift + 左键 | 分配 10 点 |
| Shift + 右键 | 撤回 10 点 |
| 分类重置按钮 | 返还该分类全部已分配点数 |

GUI 支持**右键拖动面板**、**Ctrl+滚轮缩放**、**中键重置视角**。

---

## 伤害处理链 / Damage Processing Chain

玩家受到伤害时按以下优先级依次判定：

1. **免疫判定** — 火焰 / 弹射物 / 爆炸 / 窒息免疫
2. **自动复活** — 死亡时触发
3. **闪避** — `dodge_chance`
4. **格挡** — `block_chance`
5. **法力护盾** — `mana_shield`
6. **伤害减免** — `damage_reduction`
7. **摔落减免** — `fall_resist` / `no_fall_damage`
8. **伤害反射** — `damage_reflection`

---

## 配置 / Configuration

配置文件位于 `config/infinitestats-common.toml`：

```toml
[Experience]
xpPerKillBase = 20              # 击杀基础经验
xpPerKillHealthFactor = 3.0     # 按血量加成的经验系数
passiveXpAmount = 2            # 被动挂机每轮经验
passiveXpInterval = 80          # 被动经验间隔（tick）

[Leveling]
baseXpPerLevel = 60             # 升 2 级所需经验
xpPerLevelIncrement = 30        # 每级递增经验值
pointsPerLevel = 3              # 每次升级获得点数

[AutoRevive]
autoReviveCooldown = 300        # 自动复活冷却（秒）
autoReviveHealthPercent = 0.3   # 复活后血量百分比

[PassiveEffects]
healthRegenInterval = 100       # 生命恢复间隔（tick）
manaRegenInterval = 40          # 法力恢复间隔（tick）
magnetRange = 10                # 磁铁吸引范围
veinMinerMaxBlocks = 64         # 连锁挖掘最大方块数

[GUI]
showHiddenStats = false         # 显示隐藏属性（如无敌）

[Compatibility]
enableAttributeDiscovery = true # 自动发现第三方属性

[EMC]
emcEnabled = true               # 启用内置 EMC 系统
emcLossRate = 0.0               # EMC 转换损耗率
```

---

## 经验获取 / XP System

| 来源 | 公式 |
|------|------|
| 击杀怪物 | `xpPerKillBase + 怪物最大生命值 × xpPerKillHealthFactor` |
| 被动挂机 | 每 `passiveXpInterval` tick 获得 `passiveXpAmount` 经验 |

经验获取受 `xp_gain` 属性加成影响。

---

## 兼容性 / Compatibility

- **纯服务端模组**，客户端可选安装（推荐安装以使用 GUI）
- **独立运行**，无前置依赖，仅需 Forge 47+ 和 MC 1.20.1
- 自动兼容所有模组的 Attribute 属性（外部属性发现）
- 支持 Curios API 饰品槽
- 支持 FTB Teams 团队经验共享
- 与 Apotheosis / Iron's Spells / Ars Nouveau / Goety 等模组良好共存

---

## 构建 / Build

**环境要求：**
- JDK 17
- Gradle（使用项目自带的 `gradlew`）

```bash
# 克隆项目
git clone <repo-url>
cd infinite-stats-mod

# 构建
./gradlew build

# 构建产物位于 build/libs/
```

---

## 项目结构 / Project Structure

```
src/main/java/com/infinitestats/
├── InfiniteStats.java            # 模组入口
├── Config.java                   # 配置文件
├── client/                       # 客户端 UI 和渲染
│   ├── StatsScreen.java          # 属性加点面板
│   ├── EmcScreen.java            # EMC 转化桌 GUI
│   ├── ItemEditorScreen.java     # 物品编辑器
│   ├── DebuffFilterScreen.java   # 负面效果过滤器
│   └── StatsHudOverlay.java      # HUD 状态栏
├── emc/                          # EMC 等价交换系统
│   ├── EmcDatabase.java          # EMC 数据库与计算引擎
│   ├── EmcPlayerData.java        # 玩家 EMC 数据 Capability
│   └── EmcMenu.java              # EMC 转化桌容器
├── handler/                      # 属性效果处理器
│   ├── AttackHandler.java        # 攻击属性处理
│   ├── DefenseHandler.java       # 防御属性处理
│   ├── MobilityHandler.java      # 机动属性处理
│   ├── UtilityHandler.java       # 功能属性处理
│   └── MagicHandler.java         # 魔法属性处理
├── network/                      # 网络通信
├── stats/                        # 属性定义与数据
│   ├── StatType.java             # 属性类型定义（60+ 属性）
│   ├── PlayerStats.java          # 玩家属性数据 Capability
│   └── StatCategory.java         # 属性分类枚举
└── event/                        # 事件处理
```

---

## 更新日志 / Changelog

详见 [CHANGELOG.md](CHANGELOG.md)。

---

## 许可证 / License

MIT — 自由使用和修改。

---

## 鸣谢 / Credits

- 灵感来源：泰拉瑞亚的无限属性加点系统
- 构建工具：Minecraft Forge
