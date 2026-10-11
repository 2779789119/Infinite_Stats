![无限加点 Infinite Stats](cover.jpg)

# 无限加点（Infinite Stats）

> 泰拉瑞亚（Terraria）风格的**无限属性加点系统** —— 打怪升级 → 自由分配属性点 → 打造属于自己的 Build。

| | |
|---|---|
| **Mod ID** | `infinitestats` |
| **当前版本** | `1.54.0`（以 `gradle.properties` 的 `mod_version` 为准） |
| **运行环境** | Minecraft `1.20.1` / Forge `47.3.0+` |
| **许可证** | GPL-3.0 |

---

## ✨ 核心特性

- 🎯 **经验升级加点** —— 击杀怪物与挂机被动获取经验，升级发放可分配属性点
- 📊 **108 个内置属性**，分为攻击 / 防御 / 机动 / 功能四大类
- 🔌 **自动发现外部属性** —— 其他模组注册的属性自动收进「外部属性」分类，无需手动适配
- 🌏 **内置 30+ 模组的外部属性中文译文** —— 装包即用，不再显示 `[gunsmithlib:bullet_damage]` 这类原始键名
- 🖥️ **属性面板 GUI**（默认 `P`）—— 分类浏览 / 搜索 / 一键加点 / 重置
- 📟 **HUD 实时显示**（默认 `H` 开关）
- 🛠️ **物品编辑器**（默认 `O`）—— 可视化编辑物品的附魔、词条、NBT；主手是**拔刀剑**时还能改刀的杀敌数 / 耀魂数 / 锻造数 / SA / SE（见下方「拔刀剑联动」）
- 💎 **内置 EMC 等价交换**（默认 `V`）—— 学习 / 查询 / 转化，支持 ProjectE 联动
- 🎒 **随身工作台 / 随身熔炉 / 随身铁砧** —— 自动补料、待炼仓排队、支持存储网络抽取
- ✨ **随身进阶高级附魔台** —— 自己挑附魔、不用随机（参考 Enchanting Infuser 的进阶档）：
  可升级 / 降级 / 移除已有附魔，花经验修满耐久，把附魔拆下来换回经验，放本书就能做附魔书；
  付款可在**经验等级 ⇄ 属性点数**之间切换，不消耗青金石
- 🧪 **附魔上限突破** —— 花 1 点解锁后附魔等级**不再受原版上限约束**：附魔台能自选到任意等级、
  铁砧能把同等级附魔继续往上叠（锋利 VI、VII…）、`/infstats enchant` 直接指定；越突破越贵
- 🔀 **Polymorph（多态合成）兼容** —— 装了 Polymorph 时，随身工作台与它共用同一套配方选择，冲突配方可在工作台里直接切换
- 🧭 **传送点与跨维度传送**
- 🛡️ **死亡不掉落 / 村民交易折扣 / 自动入库** —— 保命、省钱、自动整理背包（自动入库的存储优先级与白 / 黑名单过滤都在属性面板直接调整）
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
| `G` | 打开随身工具面板 |

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
| `/infstats enderchest` | 打开随身末影箱 | 随身末影箱 |
| `/infstats smithing` | 打开随身锻造台 | 随身锻造台 |
| `/infstats brewing` | 打开随身酿造台 | 随身酿造台 |

随身熔炉支持 **Shift 连续放料**：燃料优先放入燃料槽，可熔炼材料先补入当前输入，不同材料进入待炼仓排队。手动将木头放入材料槽或待炼仓可烧木炭。主界面和成品仓都能一键收取，背包放不下的成品继续留仓。空闲时保留余热，缺燃料时保留进度；仓库内按 Esc 可返回熔炉。物品悬停显示精确库存数量。

本次熔炉改动的验证状态见 [优化与验证记录](docs/portable-furnace-optimization.md)。

---

## 🆕 新增功能说明

### 🛡️ 死亡不掉落（`keep_inventory`）

- 投入 **5 点**解锁。解锁后**死亡时保留主背包 36 格 + 盔甲 4 格 + 副手 1 格**，即使服务器的 `keepInventory` 游戏规则是关的也**不会掉落任何物品**。
- 原理：死亡瞬间把上述物品移入玩家存档暂存，重生时原样归还；暂存数据写进玩家 NBT，因此在死亡界面断线、服务器重启也不会丢。
- 与「原地复活」互不冲突：原地复活会取消死亡，此时不会动背包。Curios 饰品槽等**其他模组自己的掉落逻辑不在保护范围内**。
- 两点已知行为：**经验值仍按原版规则掉落与扣除**（该属性只保护物品）；带「消失诅咒」的物品不会被销毁（清空发生在原版销毁逻辑之前），相当于一并保住。

### 💰 村民交易折扣（`trade_discount`）

- 每点使交易价格 **-1%**，最高 **-90%**。
- 与村民声望 / 英雄效果折扣**叠加**：原版在 `Villager#startTrading` 里按声望与英雄效果 `addToSpecialPriceDiff`，本模组再把折扣加在同一份 `specialPriceDiff` 上。
- 对**流浪商人**同样生效。
- **不会累加**：`specialPriceDiff` 会随商人一起存档，而只有村民在 `stopTrading` 里清零、流浪商人不会。所以每个报价都有记账（本模组写进去的折扣 + 写入后的值），每次打开界面先剥离上次的贡献得到「原版此刻的价值」再重新叠加 —— 反复开关界面价格稳定，也不会把流浪商人刷到 1；把点数退掉后折扣同样会被收回。

### 🎒 自动入库（`auto_deposit`）

- 投入 **3 点**解锁。解锁后每隔 `AutoDeposit.autoDepositInterval`（默认 1 秒）扫描一次背包，把物品写入已连接的存储网络（RS / AE2 / 汤姆存储 / 背包）。
- **存储优先级在主面板直接调整** —— 点属性面板搜索框右侧的「自动入库：RS › AE2 › …」按钮打开「**存储优先级**」界面：
  - 每行一个存储，右侧 **▲ / ▼** 把它与相邻一位交换（**一次一位**，顺序一眼可见）；
  - 行内标注该存储**是否已安装**（未安装的排在前面不会生效，浮窗里也有提示）；
  - 底部「**重置为默认**」恢复配置文件 `NetworkPriority.networkPriority` 的顺序，「完成」返回属性面板。
- **存储优先级按功能分开**（各功能一份，互不影响；没调整过的一律沿用配置顺序）：

  | 功能 | 入口 | 作用范围 |
  |---|---|---|
  | **自动入库** | 主面板搜索框右侧「自动入库：…」 | 背包物品写入网络的顺序 |
  | **随身工作台** | 工作台界面右栏「存储优先级」 | 取料 / 补料 / 退回材料 / 成品入库 |
  | **随身熔炉** | 熔炉界面「网络」一栏「优先级」 | 抽矿物 / 抽燃料 / 成品入库 |
  | **成品仓** | 成品仓界面右上角「优先级」 | 成品仓出库到网络 |

  - 网络物品列表（供 JEI 判断哪些配方能从网络取材）**没有**优先级概念 —— 它取的是所有可用网络的并集，顺序不影响结果。
  背包物品按该顺序写入，装不下的自动回落到下一个网络。
- 默认**不碰快捷栏**（0-8 号槽），随身工具 / 武器不会被吸走；把 `autoDepositKeepHotbar` 设为 `false` 可改为「除手持那一格外全部入库」。
- **白 / 黑名单过滤**：主面板「入库过滤」按钮打开过滤界面，可精确控制「哪些物品才入库」：
  - **白名单** = 只入库名单内的物品；**黑名单** = 名单内的物品不入库；
  - 点击列表中的物品行即**加入 / 移出**名单（在名单内的行有绿色指示条），也可用「手持加入」一键把主手物品加入；
  - 支持按物品名或物品 ID 搜索（装了 `pinyin_search` 时支持拼音）；「只看已选」可把列表收窄到已选条目方便核对；
  - **名单为空 = 不做限制**（避免清空列表后自动入库整体失效）；
  - 名单属于**玩家个人数据**，随存档同步、跟人走。
- **安全保护**：存储无线终端（RS / AE2）与背包本身**永不入库**（否则一次扫描就会把连网凭证自己存走），另有服务端配置黑名单兜底。
- 打开其它容器（箱子 / 交易 / 随身工作台）时不会打扰；没有可用存储网络时自动跳过。

### 🎛️ 功能开关（每个玩家独立，不是全局配置）

- **入口**：属性面板（默认 `P`）标题栏**右上角的「功能开关」按钮**（关闭后返回属性面板）。
- **作用**：108 条内置属性可以逐条关掉 —— 关掉后一律**从属性面板隐藏、不能继续加点**，页脚里对应的功能入口也一起消失；**「整条失效」还是「只隐藏」由下面的模式决定**（**全局设置，由整合包作者定**，配置项 `GUI.featureDisableMode`；OP 也可用指令改）：

  | 模式 | 效果 | 已投入的点数 |
  |---|---|---|
  | `keep` 失效·保留点数（默认） | 整条失效：数值按 0 计（`PlayerStats` 构建数值缓存时跳过关闭项，所有读 `getStatValue()` 的效果与属性加成都归零）；开关型功能（随身工作台 / 熔炉 / 铁砧 / 末影箱 / 锻造台、定点传送 / 跨维度传送、自动入库…）的**效果与 `/infstats` 指令一并停用** | **保留**，重新开启立刻恢复 |
  | `refund` 失效·返还点数 | 同上（数值按 0 计、效果与指令停用） | **全额退回**可用点数；重新开启后从 0 点开始，需要重新加点 |
  | `hide` 只隐藏（不返还） | 效果与指令**照常生效**，只是面板里看不到、不能加点 | 保留 |

  - 关闭 / 开启 / 切换模式都会**当场重算属性**，不必等下次加点或重登；OP 切换模式时会为全服在线玩家重算一遍。
- **分层**：**开关是按玩家存的**（写在玩家数据里，随存档同步、跟人走，多人服务器里每个人可以有自己的面板）；**执行模式是全局配置**（整合包作者一份设置，所有人共用，玩家界面上只读显示）。
- 界面支持分类筛选 + 搜索（属性名 / ID / 说明，装 `pinyin_search` 时支持拼音），点行即切换；底栏「全部开启 / 全部关闭」作用于**当前筛选结果**，下面一行显示当前执行模式（鼠标悬停看该模式的含义）。
- 不想开界面也行，等价指令（**需要 OP，权限等级 2**：整合包里这是给管理员用的，普通玩家用界面即可；`<属性ID>` 支持 Tab 补全）：

  | 指令 | 作用 |
  |---|---|
  | `/infstats feature` / `feature list` | 列出当前已关闭的功能 |
  | `/infstats feature <属性ID>` | 切换该功能（开 ↔ 关） |
  | `/infstats feature <属性ID> on\|off` | 明确开启 / 关闭 |
  | `/infstats feature all on\|off` | 全部内置属性一次性开启 / 关闭 |
  | `/infstats feature mode` | 查看当前「关掉时」的执行模式 |
  | `/infstats feature mode <keep\|refund\|hide>` | 切换**全局**执行模式（写回配置，等同改 `GUI.featureDisableMode`） |
- 点数怎么处理由模式决定（保留 / 全额返还），**任何模式都不会销毁数据**。

### 🪜 N 段跳 / 爬梯加速（`multi_jump` / `climb_speed`）

- 这两条是**客户端实现**（与服务端属性类加成不同）：玩家的移动由客户端主导、服务端只做校验，所以「空中再跳一次」「梯子上爬得更快」必须在本地改速度才真的动得了人。
- `multi_jump`：等级＝空中可跳次数（最多 10 次）。**起跳后松开跳跃键、在空中再按一次**才会触发（按住不放不算），触发时带云雾粒子与音效；落地 / 入水 / 上梯子后次数重置。
- `climb_speed`：梯子 / 藤蔓 / 脚手架的上升速度 **+20% / 点**。

### 🏹 多重射击（`multi_shot`）

- `multi_shot`：等级＝每次射击的额外箭数（最多 8 支）。额外箭矢由**首发箭矢的 NBT 复制**而来（光谱箭、模组自定义箭矢都能正确复制），按小角度左右散开，并一律设为**不可拾取** —— 否则它们没消耗任何背包资源，落地被捡回就等于凭空刷箭。

### 🔫 无限子弹（`infinite_ammo`，枪械模组联动）

- 投入 **5 点**解锁（`infinite_ammo`，开关型）。**支持 [TACZ（永恒枪械工坊：零）](https://github.com/MCModderAnchor/TACZ)
  1.1.x 与 [Superb Warfare（卓越前线）](https://github.com/Mercurows/SuperbWarfare) 0.8.x**；
  两个都没装时这条属性放在面板里也不会报错，只是不产生任何效果 —— 对应的 Mixin 根本不会被应用
  （见下面的「可选注入点」）。
- **TACZ 枪械**：**弹匣里的子弹永远是满的** —— 射击**不扣子弹**、也**永远不需要换弹**，
  空背包、一颗子弹都没有也能一路打下去。弹匣式与「背包直读」式都覆盖，栓动 / 闭膛 / 开膛同样适用。
  实现（两半）：
  1. **不再扣弹**：Mixin 注入 `ModernKineticGunScriptAPI#reduceAmmoOnce`（每次击发都走它）与
     `#removeAmmoFromMagazine`（拉栓供弹 / 换弹收尾推弹进枪膛都走它）—— 前者改成「先把弹匣补满、再当作扣弹成功」，
     后者变成空操作。这两处**必须在 Mixin 里做**：扣弹与生成子弹在同一次调用内完成，任何「事后补回来」的方案
     都赶不上同一 tick 的读数，HUD 会按射速在满匣与满匣-1 之间闪。
  2. **补满已有的弹匣**：每 5 tick 把**手持**（主手 + 副手）枪械的弹匣补到上限 —— Mixin 只保证「不少」，
     保证不了「本来就半匣的枪也是满的」。弹匣上限连同扩容弹匣等配件一起问 TACZ 自己
     （反射 `ModernKineticGunScriptAPI#getMaxAmmoCount`）。
  - **顺带堵了口子**：无限子弹生效时，**更换弹匣类配件不会再「卸弹」变成一堆真弹药物品**
    （`AbstractGunItem#dropAllAmmo` 被取消），否则「换配件拿弹药 → 弹匣又被补满」就是刷弹药。
- **Superb Warfare 枪械**：**弹匣同样恒满、同样不用换弹**，两边一起做（缺一不可）：
  1. **备弹侧**：`GunData.countBackupAmmo(Entity)` 报无限 + `GunData.consumeBackupAmmo(Entity, int)` 整个取消 ——
     这是「还有多少备弹 / 真正扣多少」的唯一口径：物品弹药、玩家背包弹药（`@RifleAmmo`）、能量弹匣、
     经验 / 饥饿 / 生命各策略都由它汇总，射击前够不够与装填取多少也都看它，所以**一次覆盖所有弹药类型**，
     也不再有「必须先带弹药」的前置（载具机炮那种吃 `small_shell_*` 物品、弹匣容量为 0 的武器因此才生效）。
     **注意**模组自带的 `GunData.hasInfiniteBackupAmmo(Entity)` **不是**消耗闸门 —— 反汇编核对过
     `GunData` 自己从不调用它，全模组只有 HUD（`AmmoBarOverlay`）与脚本代理（`GunDataProxy`）读它；
     只挂那一条只会让 HUD 显示「∞」而弹药照扣。我们两条都挂：它管界面语义，上面两条才是真正拦下消耗。
  2. **弹匣侧**：每次开火后把弹匣写回容量上限，外加每 5 tick 兜底。
     **只做第 1 步是不行的** —— 那会变成「能一直打，但弹匣照常掉空、游戏仍要求你换弹」。
     容量取模组自己的属性 `GunData.get(GunProp.MAGAZINE)`（扩容弹匣、Perk 修正都已算进去，
     它的换弹逻辑用的也是同一个属性）；**不调用**它的 `GunData.reloadAmmo(...)` ——
     那是换弹收尾函数，顺手还会改换弹状态机（`reload.setState(NOT_RELOADING)` / `bolt` /
     `fireIndex` / `nbtVersion`），拿它当补弹工具会打乱换弹动画与拉栓状态。
- **补弹的三层收口**（一层比一层外，全部幂等、可叠加 —— 弹匣满了不写、热量为 0 不写）：
  1. **收口 A（全枪型）**：`GunData.shoot(...)` 各入口的 RETURN。这是主收口，选它是因为反汇编核对过：
     这几个重载都是 `public final` 的**薄包装**（内部直接 `item.shoot(this, ...)`，方向是
     `GunData.shoot → GunItem.shoot`，`GunItem` 那边没有反向调用），而它们的**外部**调用者只有
     开火网络包 `ShootMessage`（玩家）与 `GunShootGoal`（AI），载具走 `shoot(ShootParameters)` 重载。
     返回时整条开火链路已经跑完，所以**不挑枪型** —— 连自己覆写了 `shoot` / `afterShoot`、
     覆写里又**不回 `super`** 的那几把（Igla / Javelin / Bocek / Ql1031 / Sentinel / Taser）也覆盖；
     挂在 `GunItem.shoot(ShootParameters)` 上恰恰做不到这点。
  2. **收口 B**：`GunItem.shoot(ShootParameters)` 与 `GunItem.afterShoot(ShootParameters)` 的 RETURN，
     兜「直接调 item、不经过 `GunData.shoot(...)`」的路径。
  3. **每 5 tick 兜底**：扫手持两格，管「捡来的 / 属性解锁前就已经半匣」的枪。
- **判定口径**：默认按**玩家**判定（`CompatToggles`），生物持枪不受影响；
  唯一的例外是 **Superb Warfare 的载具武器**（开火时传进来的实体是载具本体，它的炮手在另一个字段里）——
  口径是「该载具的乘员里有解锁者」即生效，详见下面的「载具武器」小节。

### ❄️ 零热量（`no_heat`，枪械模组联动）

- 投入 **3 点**解锁（`no_heat`，开关型）。同样支持 **TACZ 1.1.x 与 Superb Warfare 0.8.x**。
- 解锁后**手持带热量数据的枪**（机枪那类）即生效：**热量恒为 0** ——
  既不会升温、也不会因为打满而进「过热锁」（锁上就打不出子弹），HUD 上的热量条一直是空的。
  在 TACZ 上顺便消掉了热量带来的 RPM / 精度惩罚（热量为 0 时它取的是无惩罚那一档）。
- **TACZ**（三步，覆盖三条加热路径）：
  1. **击发加热**：Mixin 取消 `ModernKineticGunScriptAPI#handleShootHeat`（默认实现里加 heat、满值上锁的地方）；
  2. **脚本加热**：Mixin 取消同一类上的 `setHeatAmount`（枪械 Lua 脚本直接写热量走的是这个包装方法）。
     注意只拦这一个包装方法 —— TACZ 自己的**散热**（`ModernKineticGunItem#tickHeat`，走枪械物品上的同名方法）不受影响；
  3. **兜底清零**：每 5 tick 把**手持**枪械的 `HeatAmount` 清零、`OverHeated` 解锁。
     这一步管的是「本来就热了 / 已经被锁了」的枪（比如属性刚解锁、或捡来的枪），只在真的非零/已锁时才写 NBT。
- **Superb Warfare**：它唯一的加热点是 `GunItem.shoot(ShootParameters)`（`javap` 扫全类确认：只有它读
  `GunProp.HEAT_PER_SHOOT`），Mixin 在这个方法 **RETURN** 处把 `heat` / `overHeat` 清零 ——
  同一 tick 内就归零，HUD 看不到升温；外加每 5 tick 的兜底清零（同样只处理「解锁前 / 捡来时就已经热了」的枪）。
- 两边的兜底由同一个每 5 tick 的入口驱动，各自独立判定：都没解锁时连背包扫描都不做。

### 🎯 无后坐力（`no_recoil`，枪械模组联动）

- 投入 **3 点**解锁（`no_recoil`，开关型）。同样支持 **TACZ 1.1.x 与 Superb Warfare 0.8.x**。
- 解锁后开火时**视角不再被后坐力往上顶**：弹着点完全由你自己的准心控制，连续扫射时准心不会越打越高。
  枪的**散布（精准度）不受影响** —— 这条只管后坐力那一下抬手。
- **TACZ**：后坐力全在客户端的 `CameraSetupEvent` 里，改的是**玩家真实朝向**（`player.setXRot/setYRot`），
  所以两个方法都要取消：`initialCameraRecoil`（开火瞬间算后坐力曲线）+
  `applyCameraRecoil`（每帧把曲线增量减到视角上）。只拦一个会残留上一次曲线的状态。
- **Superb Warfare**：三处一起压，手法统一为「**只吞掉对 `LocalPlayer` 朝向的写入，其余逻辑照常跑**」，
  不整段取消方法（整段取消会让状态量不衰减而残留，见下）：
  1. **后坐力** `ClientEventHandler.handleGunRecoil()`：只 `@Redirect` 掉它对 `LocalPlayer` 的
     `setYRot`（左右偏）/ `setXRot`（往上顶）两处写入。**必须保留方法本身** ——
     它同时负责 `recoilHorizon` / `recoilY` 的衰减（全类只有它写这两个量），
     而枪模渲染器（`Aa12ItemModel` / `SentinelItemRenderer`）和开火动画都在**读**它们，
     整段取消会让这些量停在上次开火的值上不再衰减（早先版本就是这么做的，枪模表现会不对）。
  2. **呼吸 / 瞄准晃动** `handleWeaponBreathSway()`：同样只吞那两处写入 ——
     它每帧用 `RandomSource` 往 pitch / yaw 上加**随机**偏移（屏息、架脚架才会减弱），
     这才是「镜头一直抖」的正主；属性承诺的是「弹着点自己控制」，所以一并压掉。
  3. **开火时的相机偏移**：`handleWeaponFire` 每帧把 `cameraRot[2]` 往开火目标值 lerp，
     `handlePlayerCamera` 再把它加到相机角度上；本模组在 `handlePlayerCamera` 的 **HEAD** 处把这个分量清零
     （反射写对方 `public static double[]`，取不到就静默跳过）—— 放在「读」这一侧是为了不依赖同一帧内两者的先后顺序。
- **Superb Warfare 侧不动的东西**（它们不是后坐力，属于该模组的手感 / 其它机制）：枪模自身的开火动画
  （`firePosZ` / `fireRotTimer`）、枪身随视角转动的跟随（`turnRot`，枪模动画也在读它）、准星散布（`fireSpread`）、
  换弹抖动（`handleReloadShake`）。它自己还有个客户端配置 `DisplayConfig.CAMERA_ROTATE`，可以整体关掉相机角度偏移。
- **为什么不用事件**：这两家在这块本身就是 Forge 事件处理器（TACZ 的相机事件、SBW 的 `computeCameraAngles`），
  且共享一批跨帧静态状态（SBW 的 `fireRecoilTime` / `recoilForce` / `turnRot` 等）——
  在事件层事后把角度掰回来既压不住同一帧内对玩家朝向的写入，也容易和别的改视角模组打架；
  让后坐力这一段干脆不执行才是干净的。
- 两个后坐力 Mixin 都放在 mixin 配置的 `client` 列表里（目标类是纯客户端类，服务端不会加载它们）。

### 🚙 载具武器（`infinite_ammo` / `no_heat` / `no_recoil` 对卓越前线载具同样生效）

载具武器和手持枪是**两套数据、两条链路**，所以三条属性要分别补口径（TACZ 没有载具，这部分只涉及卓越前线）：

- **弹药 / 热量**：载具的武器不放在物品栏里 —— 每个武器一份 `GunData` 挂在载具的同步数据
  `GUN_DATA_MAP` 上（`getGunData(int)` / `modifyGunData(...)`），开火同样走
  `GunData.shoot(ShootParameters)`（反汇编核对：`VehicleEntity.vehicleShoot(...)` 里就是 `data.shoot(params)`），
  所以**补弹匣 / 清热量的注入点对载具天然覆盖**。
  **机炮（物品弹药）**：这类武器吃的是 `small_shell_ap/he/gs` 这类**弹药物品**、弹药口径是「备弹」而不是弹匣，
  所以靠的是上面那条「备弹侧」接管（`countBackupAmmo` / `consumeBackupAmmo`）—— 现在不装弹也能一直打。
  **另加一道每 5 tick 的兜底**：反射 `VehicleEntity#getGunDataMap()` 把玩家所在载具的每个武器都顶满 ——
  开火收尾的注入点只有「已经打过一发」之后才触发，而**空弹匣连 `canShoot` 都过不了**、
  压根进不了开火链路，否则会表现为「必须先手动塞一发弹药才能打」（进车约 0.25 秒后即满匣）。
  **坑在「谁解锁了」**：载具开火时 `ShootParameters.shooter` 是**载具本体**
  （`VehicleEntity.getAmmoSupplier()` 就是 `return this`），真炮手在 `ammoSupplier` 字段里 ——
  只读 `shooter` 会判定失败、整条被跳过。现在的口径：**优先取活体那一个**（载具时即炮手），
  拿不到才退回载具；`hasInfiniteBackupAmmo(载具)` 也改成往**乘员**里找解锁者。
- **后坐力**：载具的镜头抖不走 `handleGunRecoil`（那条要求手持枪），而是
  `VehicleEntity.afterShoot(...)` 里的 `GunData.shakePlayers(载具)` —— 读 `GunProp.SHOOT_SHAKE`，
  然后发 `ShakeClientMessage` 给附近玩家（全模组只有载具类调它）。无后坐力解锁时**整条取消**，
  与手持枪口径一致：**炮口后坐状态（`CANNON_RECOIL_FORCE` / `CANNON_RECOIL_TIME`，供枪模与 HUD 动画用）
  照常保留**，只是玩家镜头不再震。
- **干扰弹（decoy，跟随「无限子弹」）**：载具干扰弹是「装填数 + 补充冷却」两段 ——
  `VehicleWeaponUtils#reloadDecoy` 拿车上的 `FLYING_FLARE_AMMO` 物品装填
  （装填量 = `min(携带量, computed().decoyMagazineSize)`），发射扣的是 `DECOY_COUNT`。
  所以每 5 tick 把 `DECOY_COUNT` 顶满即可：**不用补弹、也不会真吃你的干扰弹物品**。
  携带量 `DECOY_ITEM_COUNT`（由载具自己的 `tick()` 每 tick 从乘员背包重算，写了也会被覆盖）与
  补充冷却 `DECOY_RELOAD_COOLDOWN`（射速限制，不是弹药）都不动。
- **多人同车的判定**：载具开火的震屏由**整台载具统一派发**（`ShakeClientMessage` 包里没有逐个玩家的身份），
  而它同时也被爆炸（`CustomExplosion` / `ParticleTool`）复用、客户端区分不出来源 ——
  所以只在载具开火这一侧拦，口径是「**该载具的乘员里有解锁者**」即生效；
  爆炸等其它来源的震屏不受影响。
- **载具上保留不动的**：炮口后坐状态、准星 / HUD 抖动（`VehicleCrosshairOverlay`、`LandVehicleHud` 等读
  `recoilShake`）、载具模型的后坐动画 —— 都不是玩家镜头，属于该模组的手感。

### 🔋 无限能源 / 无限载具血量（`infinite_energy` / `sbw_infinite_health`）

两条开关型属性，**各投入 3 点解锁**：

- **无限能源（`infinite_energy`）**：你「拥有 / 正在使用」的**所有能量源保持满电**，不挑能源类型 ——
  在 1.20.1 里 RF（红石通量）就是 FE（Forge Energy）的别名，Mekanism / 热力系列（Thermal）/
  EnderIO / 沉浸工程（Immersive Engineering）/ 机械动力附属（Create Crafts &amp; Additions 等）的
  物品、方块实体、实体最终都通过 `ForgeCapabilities.ENERGY`（`IEnergyStorage`）暴露能量，
  所以「把 FE 补满」这一件事就能覆盖这些全部。
  范围＝**你身边的能量源**：背包 36 格 + 盔甲 4 格 + 副手 1 格、Curios 饰品槽（装了 Curios 才扫）、
  正骑乘的实体（卓越前线载具 / 任何挂 FE 能力的坐骑），以及**你自己放置的能量方块**
  （扫描半径 16 格、默认每 1 秒补一次，只补本人放置的方块 —— 见 `util/EnergyOwnershipStore`）。
- **无限载具血量（`sbw_infinite_health`）**：卓越前线载具血量恒满 —— 打不坏、打不死。
  载具的**部件血量**（履带 / 引擎 / 炮塔）不用单独处理：那套逻辑（`VehicleEffectUtils.handlePartHealth`）
  只在「主血量低于上限 5%」时把部件清零，主血量恒满就永远走不到那一步。

实现分「不再变少」（注入点，管卓越前线载具）和「每 tick 补满」（兜底，管所有 FE 能量源）两半：

| 属性 | 注入点（卓越前线，`javap` 核对 0.8.9.2） | 为什么是它 |
|---|---|---|
| 无限能源（载具那一半） | `VehicleEnergyStorage#extractEnergy(int, boolean)` | 载具电量**唯一会变小**的地方。载具自己的扣电入口 `VehicleEntity.consumeEnergy(int)` 与按 Forge Energy 能力扣电（能量弹药 / 外部机器）最终都汇到它 —— 拦一处即全覆盖；只拦 `consumeEnergy` 会漏掉能量弹药那条 |
| 无限载具血量 | `VehicleEntity#setHealth(float)` | 载具血量**唯一写入口**（只有它把血量写进同步数据 `HEALTH`）。用 `@ModifyVariable` 把入参改成满血 |

- **无限能源的兜底**（`compat/EnergyCompat`）：给「背包 + 饰品 + 骑乘实体」上的每个
  `ForgeCapabilities.ENERGY` **每 tick** 补到上限，并给「玩家自己放置的能量方块」按配置间隔（默认 1 秒）
  扫描补满 —— 这是**其它所有模组**的能量源（电池、能量背包、能量工具、世界里的机器 / 储能方块等）
  共用的充能入口；它们没有统一的「扣电注入点」可拦，所以靠周期性补满，对读数与 HUD 来说就是恒满。
  已经是满的就不写，不会反复标脏物品 / 同步。方块的归属由 `event/BlockOwnershipEvents` 在放置 / 破坏时
  记进 `util/EnergyOwnershipStore`（按维度持久化），只认**玩家本人**放置的坐标，不影响别人与公共机器。
  方块扫描只查**已加载区块**（绝不为了补电强制生成新区块），并逐面尝试能量能力以兼容只在特定面暴露的机器。
- **为什么血量是「改入参」而不是「取消那次写入」**：取消只能让血量停在旧值上，改入参则
  「无论谁要写多少、写进去的都是满血」—— 读数、HUD、它自己的低血提示 / 冒烟特效自然全都跟着满血走。
  也不能放在每 tick 的兜底里回血：一炮打掉满血坦克是常有的事，**同一 tick 内**血量归零就会走 `destroy()`，
  事后补根本来不及。
- **为什么载具电量返回的是「本该抽到的量」而不是 0**：调用方拿它当「这次抽电成功了」，
  于是载具自己的「够不够电」判断（引擎 / 弹药策略）继续放行，电量却一点没少。
- **判定口径**：与三条枪械属性完全一致 —— **该载具的乘员里有解锁者**即生效（载具自己不可能是玩家）。
- **各自独立降级**：载具句柄与枪械句柄分开解析 —— 卓越前线换了版本导致某一边取不到时，
  只让那一边退化成原版行为（注入点 `require = 0`，反射失败静默跳过），不影响另一边，也不会让游戏起不来。

> ⚠️ 不含 IC2 EU / 格雷科技 EU（GTCEu）：这俩用的是各自的能量 API、不走 Forge Energy，且本整合包未安装，
> 所以「无限能源」暂不覆盖它们；若日后加入，只需在 `EnergyCompat` 里补一个对应能量类型的充能适配器。

### ⚙️ 无限应力（`create_infinite_stress`，机械动力联动）

- 投入 **3 点**解锁（开关型）。解锁后 **你自己放置的**动力网络不再因为应力（Stress, SU）不足而超载停机：
  机器照常运转、应力表指针回到低位、护目镜读数正常。
  判定范围是**整张网络**：网络里只要有**任意一块**是你放的（且你在线并已解锁），整张网络都生效 ——
  动力网络是一整套连在一起的传动结构，一部分转、一部分停会直接穿帮。
  别人基地与公共机器不受影响（与「无限能源」共用同一套归属表，见 `util/BlockOwnershipStore`）。
- **为什么是「改产能」而不是「别让它超载」**：Create 的超载判定有两处 ——
  服务端在 `KineticBlockEntity.updateFromNetwork` 里算出 `overStressed`，
  **客户端**则在收到同步包后拿包里的 `Capacity` / `Stress` **自己重算**同一个判据。
  只拦服务端那个布尔量的话，机器会在世界里转、在屏幕上不动。所以落点选在
  **产能的唯一汇总点** `KineticNetwork.calculateCapacity()` 的返回值上：改这一处，
  成员方块、应力表（指针位置＝耗能 ÷ 产能）、发给客户端的同步包会同时拿到「无限产能」，
  两端结论天然一致。
- **两个注入点，一个负责「显」、一个负责「存」**：
  1. `CreateKineticNetworkMixin` —— `calculateCapacity()` 的 RETURN：是解锁者的网络就返回 10 亿 SU；
  2. `CreateKineticBlockEntityMixin` —— `write(compound, clientPacket)` 的 HEAD / RETURN：
     **发包给客户端时保留改写值**（客户端靠它算超载），**写存档时换回真值**。
     不这么做的话，10 亿会被写进存档，重登后 `initFromTE` 会把它当成「未加载区块的产能」收进网络，
     这张网络从此永久不过载 —— 属性关掉、甚至卸载本模组都退不回来，等于污染存档。
- **用的是「够大但有限」的 10 亿，而不是 `Float.MAX_VALUE`**：真实网络的耗能总量不过几十万 SU，
  10 亿永远够用；而 `Float.MAX_VALUE` 参与乘法 / 界面显示时容易变成 `Infinity` 或 `3.4E38` 这类读数。
- **边界**：改动的只是网络产能，**转速（RPM）与传动逻辑一概不动** —— 没有动力源还是不会转；
  方块必须是**玩家本人放置**的（`/setblock`、结构蓝图粘贴、其它模组生成的不进归属表），
  且解锁者**在线**时才生效（离线读不到属性数据，与「无限能源」补方块电的边界一致）。
- 与其它联动一样：没装机械动力时这两个 Mixin **根本不会被应用**
  （`InfiniteStatsMixinPlugin` 按 `com.simibubi.create.` 前缀门控），属性放在面板里只是不产生效果。

### 🧩 第三方模组的注入点怎么保证不拖崩游戏

- 所有针对外部模组的 Mixin 目标类都在 `com.tacz.*` / `com.atsuishio.superbwarfare.*` /
  `com.simibubi.create.*` 下，
  通过 `InfiniteStatsMixinPlugin`（`shouldApplyMixin` 按**目标类名前缀** → mod id 判定）
  在**对应模组没装时根本不应用** —— 本模组的 Mixin 配置是 `required: true` 的，
  若不这样处理，缺目标类会让整个游戏起不来。
- 注入点本身一律 `require = 0`：对方版本变化导致锚点对不上时只打印一条警告、对应功能退化为原版行为。
- 编译期只借用 `src/compatApiStub/java` 里的**空壳类**（把用到的成员按真实签名抄一遍），
  这样 Mixin 注解处理器能校验注入点名字，而对方的 jar 不用进仓库、产物也**不会**被打进本模组的 jar。

### ⚡ 额外打击（`extra_strike`）

- `extra_strike`：等级＝每次造成伤害后追加的**额外打击次数**（最多 8 次），每次额外打击造成**本次伤害 20%** 的额外属性伤害
  （比例由配置 `ExtraStrike.extraStrikeDamageRatio` 决定，默认 0.2；满 8 级合计 **+160%**）。
- **触发范围＝你造成的所有伤害**：近战挥砍、弓箭 / 三叉戟 / 模组弹射物、以你为来源的法术，
  以及**你的召唤物 / 宠物 / 坐骑**打出的伤害。后者的伤害来源实体是召唤物自己，
  靠「主人关系」（`OwnableEntity`）归到你头上 —— 这是本模组里唯一采用这种宽口径的属性，
  暴击 / 吸血等仍只认玩家本体与其发射的弹射物。
- **独立结算、无视护甲与减伤**：额外打击不叠进本次伤害，而是在结算完成后再单独扣血，
  因此不会被目标护甲、抗性提升、保护附魔或其它模组的「单次伤害上限」削掉。
- **不触发的情形**：自伤（自己炸自己、箭落回自己身上）；本模组自己的次级直接伤害（范围攻击 / 真实伤害 / 额外打击本身），
  否则会层层叠加；以及**被格挡 / 闪避 / 免疫**取消的攻击（走的是取消后的结算，不会出现"打空也掉血"）。
- **实现要点**：挂在 `LivingDamageEvent`（此时护甲与减伤已结算，取到的是**实际伤害**）上，
  用 `setHealth` 直接扣血而**不嵌套 `hurt()`** —— 此刻正处在目标本次受伤的 `actuallyHurt` 内部，
  嵌套一次会造成同一次受击走两遍死亡收尾（掉落 / 成就 / 击杀进度重复触发）。死亡仍由外层本次受伤的原版收尾处理，
  所以**击杀归属、掉落、击杀类任务照常算在玩家头上**。

### 💰 铁砧经验减免 / 交易即刻补货 / 死亡不掉经验

- `anvil_cost`：铁砧等级消耗 **-5% / 点**（最多 -90%）。减免在「过于昂贵」判定**之前**写入最终消耗，所以该门槛也一并被压低。实现走 Mixin（`AnvilMenu#createResult`）—— Forge 的 `AnvilUpdateEvent#setCost` 只在同时设置 `output` 时才被 `ForgeHooks.onAnvilChange` 采用，而且事件在附魔消耗计算之前触发，只改 cost 是彻底无效的。
- `trade_restock`（3 点解锁）：交易后**立刻补货**，同一条目可以连续买。只重置 `uses`，不调用 `Villager#restock()` —— 后者的 `updateDemand()` 反复执行会把需求加价越补越贵。
- `keep_xp`（3 点解锁）：死亡**经验等级与经验条保留**（配合 `keep_inventory` 可完整保命）。

### 🧰 随身工具面板（统一入口）

- 所有随身站点（工作台 / 熔炉 / 铁砧 / 末影箱 / 锻造台 / 进阶附魔台 / 切石机 / 织布机 / 制图台 / 磨石 / 酿造台）
  收敛到**一个**「随身工具」入口，点开后是独立面板：
  - **左键**：已解锁 → 打开对应界面；未解锁 → **直接花 1 点解锁**（不必再回属性面板加点）；
  - **右键**：关闭该工具并**退还投入的点数**；
  - 未解锁的按钮文字呈灰色，悬停会提示解锁价格；服务端还会再校验一次开关（改造过的客户端无法绕过）。
- 页脚导航行只有 3 个相关入口：**随身工具 / 天气 / 强加载**（后两个需先激活对应属性才出现）。
- 全部复用原版菜单类型，客户端自动套用原版界面，无需自建界面与菜单类型。
- **随身铁砧是唯一例外**：它要放宽原版「过于昂贵」门槛并把消耗封顶到 **50 级**，而客户端必须能区分「随身铁砧」与「真铁砧」，因此它拥有自己的菜单类型 `PortableAnvilMenu`（客户端仍套用原版铁砧界面），由 Mixin 在两端识别后只对随身铁砧生效。
- 切石机 / 织布机 / 制图台 / 磨石 / 锻造台走 `crafting/PortableStationMenus`：传真实的
  `ContainerLevelAccess`（玩家脚下的维度 + 坐标）以保证世界侧回调正常执行（制图台成品、
  关闭时归还物品），再用子类覆写 `stillValid` 去掉「必须站在对应方块旁」的校验。
  - **随身末影箱**与原版末影箱**共用同一份库存**。
- **随身酿造台**走 `crafting/PortableBrewing` + `crafting/PortableBrewingData`：酿造台没有
  「读世界数据算成品」这一步（药水配方、400 tick 进度都在方块实体里），状态因此挂在
  玩家数据上（随存档保存）—— **关掉界面也继续酿，东西留在台子里**，下次打开接着看；
  **自带无限燃料、不需要烈焰粉**（界面燃料条恒满）；原版菜单与界面直接套用，
  其它模组扩展的酿造配方同样生效。
- 对比：`/infstats craft | furnace | anvil | enderchest | smithing | brewing` 指令保持不变；
  新增的站点只从「随身工具」面板进入。

### ✨ 随身进阶高级附魔台（`portable_enchanting`）

投入 **1 点**解锁，之后随时从「随身工具」面板打开。玩法参考 Enchanting Infuser 的**进阶档**：
**不再随机三档，而是自己挑附魔**。菜单与界面是自建的（`PortableInfuserMenu` / `PortableInfuserScreen`），
规则与定价全在 `crafting/PortableInfuser`（双端同源的纯函数）：

- **自选附魔**：左边放物品，右侧列出它**能附的所有魔**，逐条用 `<` / `>` 调等级（`0` = 不附），
  按住 Shift 点击直接清零 / 拉满；清单可滚轮滚动，悬停某一行会给出完整名与价格。
- **改已有附魔**：物品身上已有的附魔同样在清单里，升 / 降 / 清就是升级、降级、移除。
- **定价**：稀有度基础价（常见 3 / 少见 4 / 稀有 6 / 极稀有 8，单位就是付款货币）× 等级，
  仅限宝藏的附魔（经验修补、冰霜行者…）**×2**。**按差额结算** —— 物品上已有的附魔不重复收费，
  降级还会**退差价**（线性定价，先买 5 级再降到 3 级与直接买 3 级等价，不存在套利）。
- **付款方式**：界面右上角一键切换 **经验等级 ⇄ 可用属性点数**，两种都**不消耗青金石**。
  创造模式免经验（与原版附魔台一致），属性点数照扣（那是本模组自己的账）。
- **经验修复**：花等级把耐久**直接修满**，不需要修复材料。每 1 级修回「总耐久 ÷ 25」点耐久
  （钻石镐从空修满约 25 级），按钮提示里写着总价。
- **经验回收**：拆掉全部附魔并返还**计价总额的 60%**（向下取整，至少 1），同时清掉铁砧的
  **累积惩罚**（附魔都没了还留着惩罚，拿去铁砧改会莫名其妙贵一大截）。
- **做附魔书**：槽里放**书**即可 —— 书能承载全部附魔，选好后点附魔就产出对应的附魔书
  （可直接搭自己的附魔库）。
- **铁砧限定组合**：原版铁砧组合时不校验附魔类别，这里同样放宽到**同一大类**
  （斧头加锋利、头盔加深海探索者、剑加效率…），但不会到「任意物品随便附」的地步。
- **互斥附魔可以共存**：原版互斥的组合（锋利 / 亡灵杀手 / 节肢杀手、保护 / 爆炸保护…）
  都能同时附在一件物品上 —— 附魔台的定位就是「想怎么配就怎么配」，不做互斥校验，
  价格照常按条计价（它们的效果本来就是各自独立结算的）。
- **等级上限**：默认只能点到**整合包实际生效的上限**（装了 Apotheosis 时是它配置的等级上限，
  本整合包里锋利是 9 级；没有这类模组时就是原版上限）；解锁「附魔上限突破」后不再受限（见下一节）。
- **旧存档迁移**：以前投在「随身附魔台」上的点数（含被拆到隐藏属性 `enchant_power` 上的）会
  **全部退回可用点数**，只留 1 点在开关上 —— 附魔强度这个隐藏属性已随本次改造取消。

### 🧪 附魔上限突破（`enchant_limit`）

投入 **1 点**解锁（`enchant_limit`，开关型）。开启后附魔等级**不再受原版 `getMaxLevel()` 约束**
（上限直接放开到 int 上限，实际能点到多高由价格决定），共三处生效：

- **进阶高级附魔台**：每一行都能继续点 `>` 往上加，价格仍按等级线性收取（等级越高越贵）；
  按住 Shift 点击是「清零 / 回到原版满级」—— 刻意不跳到 int 上限，否则一跳就是 21 亿级、既付不起也没意义。
- **铁砧（含随身铁砧）**：两件同等级附魔继续叠（锋利 V + 锋利 V = 锋利 VI），消耗按真实等级算。
- **OP 指令**：`/infstats enchant <附魔> <等级>` 直接给主手物品写入任意等级（需权限等级 2；
  该指令刻意**不**要求解锁突破，方便发物品与调试，相当于参考实现的 `/cenchant`）。

**三个必须一起改的地方**（只放开上限判断是不够的 —— 都对着 1.20.1 源码核过）：

1. **上限判断**：铁砧的上限只卡在 `createResult` 里的
   `if (j2 > enchantment1.getMaxLevel()) j2 = enchantment1.getMaxLevel();`（全类仅此两处调用），
   用 `@Redirect` 只替换**比较**里的取数：解锁时返回 int 上限 → 比较恒为 false → 等级原样保留；
   未解锁时返回的是**整合包实际生效的上限**。进阶附魔台与指令则各自取
   `EnchantLimits.maxLevel(player, ench)`（同一套口径：未解锁 = 整合包上限，解锁 = 不限）。
2. **NBT 存不下**：原版 `storeEnchantment` / `setEnchantmentLevel` 写 `putShort("lvl")`（超过 32767 截断），
   `getEnchantmentLevel` 读回来还要 `clamp(..., 0, 255)` —— **物品上写着 1000 级，读回来也是 255**。
   这三处由 `EnchantmentHelperMixin` 接管：能塞进 short 的照旧写 short（不突破的存档 NBT 与其它模组
   看到的完全一样），超出才写 int；读取不再夹到 255。
3. **名字显示**：原版语言文件只有 `enchantment.level.1` ~ `.10`，11 级以上会原样显示成
   `enchantment.level.11` 这种键名。由 `EnchantmentMixin#getFullname` 接管：
   1~10 继续用原版译文（跟随客户端语言），11~100 用罗马数字，再往上用阿拉伯数字。

> ⚠️ 上限虽然放开到 int 上限，但**别手动点到百万级以上**：等级本身不花钱的地方（如纯调试）
> 可能让原版某些按等级做 `int` 乘法的效果（例如摔落保护的 `等级 × 3`）整型溢出。
> 正常玩法下等级由价格与铁砧的「每次 +1」自然限制在合理范围内。

> 兼容性：`EnchantmentHelper` 的读写与 `AnvilMenu#createResult` 都是热门注入点。
> 铁砧两处注入刻意只用**比较**里的调用（ordinal=0），不与任何 `@ModifyConstant`
> （例如 Apotheosis 的 `apoth_removeLevelCap`）抢同一处字节码。
>
> **Apotheosis 的 coremod 另有一手**（1.39.1 修）：它会把 AnvilMenu 里那两处
> `Enchantment#getMaxLevel()` 整段换成自己的静态钩子 `EnchHooks.getMaxLevel`，coremod 又跑在 Mixin 之前，
> 于是针对原版方法的 `@Redirect` 会一个目标都命中不了。现在**两套调用点各有一个兼容注入**：
> 命中 Apotheosis 那一套时，**未解锁返回 Apotheosis 自己的上限**（不会把人家放宽的等级上限收紧），
> 解锁后才返回不限；反射调用它的钩子，没装 Apotheosis 时不会给 AnvilMenu 添类加载风险。
> 这份「包里的上限」由 `compat/ApotheosisEnchantCompat` 读出、经 `EnchantLimits.packCap` 对外提供，
> **进阶附魔台 / 随身铁砧 / 指令三处用的是同一个值** —— 不会出现「铁砧能合到 9、附魔台只让选到 5」。
>
> 另外，本模组所有 Mixin 注入点现在都是**可选**的（`AnvilMenuMixin` 逐条 `require = 0`，
> 配置层 `injectors.defaultRequire = 0`）：大包里锚点被别的模组挪走是常态，注入失败只打印一条警告、
> 对应功能退化为原版行为，**不会再让游戏起不来**。

### 🌦️ 天气控制（`weather_control`）

- 投入 **1 点**解锁。激活后页脚导航行出现「天气」按钮，点开是**天气面板**：里面列出全部天气档位
  （**晴天 / 下雨 / 雷暴**），**点哪一档就切哪一档**，并在聊天栏提示当前天气；当前档位带绿色描边，面板顶部显示当前天气。
- ⚠️ 原版 MC **没有天气注册表**（天气只是 `Level` 上的两个布尔 + 两个计时器，
  下雪是「下雨 + 寒冷生物群系」的渲染表现），所以能列的档位就是这三档；
  天气类模组各写各的字段，也没有统一注册表可枚举。面板里的档位抽成了一处列表，后续要加档位只改那一处。
- 作用于**主世界**：玩家在下界 / 末地时切换的仍是主世界天气，这样按钮在任何维度都有意义。
- 持续时长由配置 `WeatherControl.weatherCycleDuration` 决定（默认 6000 tick = 5 分钟），到时后天气按原版规则自行演变。

### 🧭 区块强加载（`chunk_loader`）

- 投入 **1 点**解锁。激活后页脚导航行出现「强加载」按钮，点开是独立的区块强加载面板：
  - 输入**方块坐标**（可点「填入当前位置」一键填入），面板会实时显示换算出的区块坐标，点「加载」把对应区块设为强加载；
  - 下方列出**当前维度**已强加载的区块（逐行显示区块坐标与对应方块范围，可单独卸载，超过 5 行自动分页）；
  - 服务端在每次加载 / 卸载后都会把最新列表回推给客户端，界面不会与真实状态脱节。
- 与原版 `/forceload` 同一套机制（`ServerLevel#setChunkForced` + `ForcedChunksSavedData`）：强加载数据随存档保存，重启后依然有效；并做世界边界校验。
- 数量上限由配置 `ChunkLoader.maxForcedChunks` 限制（默认 16，上限 256），超限时拒绝并提示，避免"一次点满"把服务器拖垮。

### 🍎 满饱食度进食（`always_eat`）

- 投入 **3 点**解锁：**饱食度已经满了也能继续吃普通食物**（原版这时右键只会失败）——
  按在原版 `Item#use` 卡住的那一步上，与「无限弓箭」接管的是同一处，
  自己调 `startUsingItem` 启动进食再取消事件，**手感与原版进食完全一致**（一样的 1.6 秒、一样的音效与粒子）。
- **只多给一种可能，不改变任何已有行为**：金苹果 / 附魔金苹果这类原版 `canAlwaysEat`
  的食物本来就能满饱食度吃，本功能直接交回原版处理；还饿着的时候同样不插手。
  因此不存在「加了本模组反而吃不了」的情况。
- 与 **免饥饿（`no_hunger`）** 是天然搭配：免饥饿会把饱食度永久锁满，此时想吃东西就得靠这个属性。
- 想限定范围可用配置 **`AlwaysEat`** 分组的两份名单（改 `config/infinitestats-common.toml`，
  或走「模组列表 → 本模组 → Config」的配置界面）：
  - `alwaysEatOnlyList`【限制名单】：留空（默认）= 所有食物都能这样吃；填了就**只有名单里的食物**能；
  - `alwaysEatExcludeList`【例外名单】：名单里的食物**不适用**本功能，一律交回原版（优先级更高）。
  两份名单都支持物品 ID（`minecraft:cake`）与 `#` 开头的物品标签（`#forge:foods`）。

### 🗡️ 拔刀剑联动（SlashBlade: Resharped）—— 物品编辑器改刀

- 物品编辑器（默认 `O`）底部新增 **「拔刀剑」** 按钮：**主手是拔刀剑时可点**（未装拔刀剑或手持别物时置灰），
  点开进入独立的拔刀剑编辑界面。可改五项：**杀敌数 / 耀魂数 / 锻造数 / SA（特殊攻击）/ SE（特殊效果）**。
- **杀敌数 / 耀魂数 / 锻造数**：顶部一行三个整数输入框，对应刀状态的
  `killCount` / `proudSoul` / `RepairCounter`。
- **SA（特殊攻击）**：左栏**单选**列表，列出 `SlashArtsRegistry` 里**全部已注册剑技**
  （含整合包 / KubeJS 追加的），点击即选中；显示名走刀自己的译文（`slash_art.<ns>.<path>`）。
- **SE（特殊效果）**：右栏**多选**列表，列出 `SpecialEffectsRegistry` 全部效果，点击切换勾选，
  显示名走 `se.<ns>.<path>`。两栏各有独立搜索框（支持拼音）。
- **写入口径**：刀状态由 Forge 能力 `ItemSlashBlade.BLADESTATE`（`ISlashBladeState`）承载，
  序列化后写在物品 NBT 的 `bladeState` 子标签下。**战斗逻辑（放 SA、结算 SE）读的是内存里的能力对象，
  不是现读 NBT** —— 所以写入流程是「`getCapability(BLADESTATE)` → 调 setter → `serializeNBT()` 回写 NBT」，
  保证**当场生效**且存档 / 同步不缺数据；能力取不到时退化为「直接改 NBT」的兜底。
- **服务端二次校验**：SA 必须是注册表内的合法剑技（非法则整包拒绝）；SE 逐项过滤、非法项丢弃；
  只校验「槽位一致 + 物品 id 一致 + 确实是拔刀剑」，刻意不做原始值快照校验
  （杀敌数随战斗实时增长，快照会频繁误判）。
- 实现全程**反射**（`compat/SlashBladeCompat`）：未装拔刀剑时零开销短路，本模组不声明编译期依赖，
  也不需要 `compatApiStub` 存根。

### 🔍 拼音搜索（已内置，无需前置）

- 属性面板 / 效果过滤器 / 物品编辑器 / 物品选择 / 入库过滤 / EMC 转化桌 / 成就统计面板的搜索框支持拼音，
  例如 `zsj` → 钻石剑、`tiezh` → 铁砧。
- 拼音能力来自 **`pinyin_search`（通用拼音搜索库）**，通过 **JAR-in-JAR 内嵌在 `-all` 产物里**
  （`build.gradle` 用 `compileOnly` + `jarJar` 引用官方坐标 `com.github.2779789119:pinyinsearch`）。
  装 `-all` 那份就**无需另装**；装标准名那份（不含内嵌）则需要整合包 / `mods\` 里另有 `pinyin_search`，
  否则搜索框退化成「纯原文包含」匹配（启动与其它功能不受影响）。
- 代码侧走它的官方 API（`com.pinyinsearch.api.PinyinSearch#matches`），桥接类见 `compat/PinyinSearchBridge`
  （按该库 `docs/INTEGRATION.md` §2 的模板实现：库的类型只出现在方法体内，没装库时自动退化为纯原文包含）。
- 已知限制（来自 `pinyin_search` 的实测结论）：英文**词首字母**搜不到（`Diamond Sword` 搜不到 `DS`）；**简繁不互搜**（`钻石剑` 与 `鑽石劍` 互相搜不到）。

---

## ⚙️ 配置

配置文件位于 **`config/infinitestats-common.toml`**（注册类型为 `COMMON`，全局生效，非 per-world）。

游戏内打开方式：**暂停菜单 → 模组 → 选中本模组 →「Config」按钮** —— 这是 Forge 原生的配置编辑界面，下面这些配置项都能在里面直接查看 / 修改（改完即时生效）。

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
| `PassiveEffects.veinMinerMaxBlocks` | `64` | 连锁挖掘单次方块数的**默认值**（玩家可在「连锁设置」面板里单独调整） |
| `PassiveEffects.veinMinerMatchTags` | `*:ores` 等 | 连锁「按标签组匹配」时视为同一类的方块标签（支持 `*` / `?` 通配） |
| `PassiveEffects.projectileTrackingRange` | `64` | 弹射物追踪扫描半径（方块，范围 8–256） |
| `GUI.showHiddenStats` | `false` | 是否显示隐藏属性（如 `invincibility` 无敌） |
| `GUI.featureDisableMode` | `keep` | 功能开关「关掉时」的**全局**执行模式（`keep`/`refund`/`hide`），改完即时生效 |
| `Compatibility.enableAttributeDiscovery` | `true` | 自动发现其他模组属性（**需重启**） |
| `EMC.emcEnabled` | `true` | 是否启用 EMC 系统 |
| `EMC.emcLossRate` | `0.0` | EMC 转换损耗率（0 = 无损耗） |
| `EMC.autoLearnProjectE` | `true` | ProjectE 自动学习主开关（需已装 ProjectE） |
| `EMC.emcFallbackValue` | `0` | 未知物品兜底 EMC（0 = 关闭） |
| `TimeAccel.timeAccelRadius` | `4` | 「加速」属性的基础影响半径 |
| `Furnace.furnaceSpeedCost` | `5` | 每级随身熔炉速度消耗的属性点 |
| `Crafting.craftingMultiplierCost` | `5` | 每级随身工作台倍率消耗的属性点 |
| `NetworkPriority.networkPriority` | `[RS, AE2, TOMS, BACKPACK, BD]` | 存储网络自动选择的优先级（**需重启**） |
| `AutoDeposit.autoDepositInterval` | `20` | 「自动入库」扫描间隔（tick，20 = 1 秒） |
| `AutoDeposit.autoDepositKeepHotbar` | `true` | 是否保留快捷栏（0-8 号槽）物品不被自动入库 |
| `AutoDeposit.autoDepositBlacklist` | `[]` | 服务端级自动入库黑名单（物品 ID，`#` 前缀表示物品标签，**所有玩家生效**） |
| `WeatherControl.weatherCycleDuration` | `6000` | 「天气」按钮切换后新天气持续的时间（tick，默认 5 分钟） |
| `ChunkLoader.maxForcedChunks` | `16` | 「强加载」每个维度最多可同时保持加载的区块数量（上限 256） |
| `ExtraStrike.extraStrikeDamageRatio` | `0.2` | 「额外打击」每次打击造成的伤害占本次伤害的比例（× 等级＝追加总量，满 8 级 = 160%） |
| `InfiniteEnergy.infiniteEnergyBlocks` | `true` | 「无限能源」是否也补满玩家自己放置的能量方块（方块实体上的 FE 能量源） |
| `InfiniteEnergy.energyBlockRadius` | `16` | 能量方块的扫描半径（方块） |
| `InfiniteEnergy.energyBlockInterval` | `20` | 能量方块的扫描 / 补电间隔（tick） |
| `AlwaysEat.alwaysEatOnlyList` | `[]` | 「满饱食度进食」限制名单：留空 = 所有食物都适用；填写后只有名单内食物适用（物品 ID 或 `#` 标签） |
| `AlwaysEat.alwaysEatExcludeList` | `[]` | 「满饱食度进食」例外名单：名单内食物不适用本功能，交回原版判定（优先级高于限制名单） |

> 大部分配置改后即时生效，标注「需重启」的除外。

---

## 🧬 内置属性一览（共 108 个）

> 标 **[开关]** 的为功能型开关，激活后即时生效，可随时开启 / 关闭。

### ⚔️ 攻击（21）

`attack_damage` 攻击伤害 · `attack_speed` 攻击速度 · `crit_chance` 暴击率 · `crit_damage` 暴击伤害 · `armor_penetration` 护甲穿透 · `knockback_power` 击退力度 · `projectile_damage` 远程伤害 · `life_steal` 生命偷取 · `life_steal_aoe` 范围吸血 · `damage_reflection` 反伤 · `execute` 处决 · `true_damage` 真实伤害 · `reduce_max_health` 削弱最大生命 · `scope_attack` 范围攻击 · `repulsion` 斥力 · `infinite_arrows` **[开关]** 无限弓箭（弓 / 弩） · `infinite_ammo` **[开关]** 无限子弹（TACZ / 卓越前线枪械） · `no_heat` **[开关]** 零热量（TACZ / 卓越前线枪械） · `no_recoil` **[开关]** 无后坐力（TACZ / 卓越前线枪械） · `multi_shot` 多重射击 · `extra_strike` 额外打击

### 🛡️ 防御（17，含 1 隐藏）

`max_health` 最大生命 · `armor` 护甲 · `armor_toughness` 盔甲韧性 · `health_regen` 生命恢复 · `damage_reduction` 伤害减免 · `knockback_resist` 击退抗性 · `fall_resist` 摔落抗性 · `block_chance` 格挡几率 · `dodge_chance` 闪避几率 · `absorption_shield` 吸收护盾 · `auto_revive` 自动复活 · `fire_immunity` **[开关]** 火焰免疫 · `projectile_immunity` **[开关]** 弹射物免疫 · `explosion_immunity` **[开关]** 爆炸免疫 · `suffocation_immunity` **[开关]** 窒息免疫 · `debuff_immunity` **[开关]** 效果过滤 · `invincibility`（隐藏）无敌

### 🏃 机动（10）

`movement_speed` 移动速度 · `swim_speed` 游泳速度 · `jump_height` 跳跃高度 · `multi_jump` N 段跳 · `step_height` 抬腿高度 · `auto_step` **[开关]** 自动抬腿 · `fly_speed` 飞行速度 · `fly` **[开关]** 飞行 · `climb_speed` 爬梯加速 · `no_fall_damage` **[开关]** 免摔落伤害

### 🧰 功能（49 + 11 个随身工具）

> 11 个 `portable_*`「随身工具」属性（工作台 / 熔炉 / 铁砧 / 末影箱 / 锻造台 / 进阶附魔台 /
> 切石机 / 织布机 / 制图台 / 磨石 / 酿造台）统一在**随身工具面板**里用**左键解锁 / 右键关闭**，
> 因此不再显示在属性面板（把配置 `showHiddenStats` 设为 true 仍可看到）。

`luck` 幸运 · `mining_speed` 挖掘速度 · `mining_level` 挖掘等级 · `reach` 方块交互距离 · `entity_reach` 实体交互距离 · `xp_gain` 经验获取 · `loot_luck` 掉落幸运 · `double_loot` 双倍战利品 · `item_magnet` **[开关]** 物品磁铁 · `xp_magnet` **[开关]** 经验磁铁 · `auto_deposit` **[开关]** 自动入库 · `vein_miner` **[开关]** 连锁挖掘 · `auto_smelt` **[开关]** 自动冶炼 · `auto_fish` **[开关]** 自动钓鱼 · `instant_bite` **[开关]** 立即咬钩 · `breed_no_cooldown` **[开关]** 繁殖无冷却 · `instant_grow` **[开关]** 一键长大 · `crafting_bonus` 合成加成 · `auto_repair` **[开关]** 自动修理 · `repair_amount` 修理量 · `use_speed` 使用速度 · `bow_draw_speed` 拉弓加速 · `cooldown_reduction` 冷却缩减 · `projectile_tracking` **[开关]** 弹射物追踪 · `no_invincibility_frames` **[开关]** 取消无敌帧 · `night_vision` **[开关]** 夜视 · `water_breathing` **[开关]** 水下呼吸 · `no_hunger` **[开关]** 免饥饿 · `always_eat` **[开关]** 满饱食度进食 · `invisibility` **[开关]** 隐身 · `keep_inventory` **[开关]** 死亡不掉落 · `keep_xp` **[开关]** 死亡不掉经验 · `time_accel` **[开关]** 时间加速 · `time_accel_radius` 加速半径 · `cross_dimension_teleport` **[开关]** 跨维度传送 · `fixed_point_teleport` **[开关]** 定点传送 · `enchant_limit` **[开关]** 附魔上限突破 · `weather_control` **[开关]** 天气控制 · `chunk_loader` **[开关]** 区块强加载 · `trade_discount` 村民交易折扣 · `trade_restock` **[开关]** 交易即刻补货 · `anvil_cost` 铁砧经验减免 · `pe_auto_learn` **[开关]** PE 自动学习 · `emc_learn_bonus` EMC 学习收益 · `emc_extract_discount` EMC 转化折扣 · `infinite_energy` **[开关]** 无限能源（FE/RF 等） · `sbw_infinite_health` **[开关]** 无限载具血量（卓越前线载具） · `goety_infinite_soul` **[开关]** 无限灵魂能量（诡厄巫法） · `create_infinite_stress` **[开关]** 无限应力（机械动力）

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

**两个产物**（`gradlew build` / `build.bat` 一起产出，同时出现在 `build\libs\` 与 `dist\`）：

| 产物 | 内容 | 用途 |
|---|---|---|
| `infinite_stats-无限加点-<MC版本>-<模组版本>.jar` | 编译产物 + 资源 + refmap，**不含内嵌 jar** | 整合包/玩家**已自带 `pinyin_search`** 时用这个，避免两份 jar 声明同一个 modId |
| `infinite_stats-无限加点-<MC版本>-<模组版本>-all.jar` | 上面那份 + 内嵌 `pinyin_search`（Forge JAR-in-JAR） | **没另装拼音库时用这个**（本模组不再把内嵌内容并进标准名产物，两个包内容不重复） |

> `build.bat deploy` 会自动挑：目标 `mods\` 里已有 `pinyin_search` 就装标准名那份，否则装 `-all` 那份。

**Windows 上一键构建**：仓库根目录的 `build.bat`（双击即可，参数写在脚本名后）：

| 命令 | 作用 |
|---|---|
| `build.bat` | 增量构建 + 把产物复制到 `dist\` |
| `build.bat clean` | 先 `clean` 再构建（全量） |
| `build.bat check` | 只编译（`compileJava` + `compileGameTestJava`，最快） |
| `build.bat nocopy` | 只构建，不复制 |
| `build.bat deploy` | 构建 + 复制到 `dist\` + 部署进整合包 `mods\`（自动探测已装有本模组的整合包，也可用第二个参数指定目录） |

脚本会校验 jar 里含 refmap、构建时一并编译 `gameTest` 源集，`deploy` 会按目标 `mods\` 里有没有
`pinyin_search` 自动挑「标准名 / `-all`」中的一份装进去。

> `gradle.properties` 里带了一行 `systemProp.net.minecraftforge.gradle.check.certs=false`：
> 本机网络（代理 / 杀软中间人证书）过不了 ForgeGradle 对 `maven.minecraftforge.net` 的证书预检时，
> 插件 apply 阶段会直接失败并报 `Failed to validate certificate for host`。这只是 FG 的一次连通性预检，
> 关掉后真正的依赖下载依旧走 JDK 正常的 TLS 校验；换到网络正常的环境可以删掉这一行。
要分发的就是 `build\libs\` 里那两个包（标准名 / `-all`），按目标整合包有没有 `pinyin_search` 二选一。
注：`build.bat` 内容保持**纯 ASCII** —— cmd 按 OEM 代码页解析 .bat，文件里出现中文会导致批处理语法报错。

> `build` 的耗时大头是 `reobfJar`（Forge 全量重映射，**不可跳过**，跳过产物无法在正式客户端加载）。只想验证改动时用 `compileJava`，要进游戏测用 `runClient`。

拼音搜索库 `pinyin_search` 用官方 JitPack 坐标
（`com.github.2779789119:pinyinsearch`，仓库 `https://jitpack.io`）接两路：
`compileOnly` 取编译期 API，`jarJar` 负责把那一个 mod jar 内嵌进 `-all` 产物
（Forge 的 JAR-in-JAR，嵌套模组运行时加载）。

**关键：不再把内嵌内容并进标准名产物**（`build.gradle` 的「打包」一节）。并进去的话
主产物与 `-all` 逐字节相同，一次构建出三个包（主产物 / `-all` / 过滤出来的 `-noembed`），
分发时只能靠肉眼分辨 —— 现在两个包各自对应一种内容，`-noembed` 变体因此取消。

`jarJar` 这一行有两个坑（都在 `build.gradle` 里处理好了，改依赖时别改坏）：

- 版本必须写 **maven 区间**（如 `[1.1.0,2.0.0)`），写精确版本会报
  `The given version specification is invalid`。
- 必须加 **`transitive = false`**：该库的 POM 把 Forge / MC 的一堆库算作依赖，不切断的话
  会被全部嵌进来（实测主 jar 从 875 KB 涨到 91.7 MB / 116 个嵌套 jar）。

源码结构（`src/compatApiStub/java/` 是**第三方模组联动的编译期存根**：TACZ 与 Superb Warfare 各几个空壳类，
**不会**被打进 jar、运行时也不会被加载 —— 只为让 Mixin 注解处理器能在编译期找到 `com.tacz.*` /
`com.atsuishio.superbwarfare.*` 目标；详见 `build.gradle` 里 `compatApiStub` 源集与
`mixin/TaczGunScriptApiMixin` 的说明）：

```
src/main/java/com/infinitestats/
├── InfiniteStats.java     # 模组入口
├── Config.java            # Forge 配置
├── client/                # GUI 与客户端逻辑（属性面板 / HUD / 物品编辑器 / EMC / 传送点 / 成就…）
├── command/               # 服务端命令（/infstats）
├── compat/                # 兼容层（JEI、存储网络桥接等）
├── crafting/              # 随身工作台 / 随身站点菜单（进阶附魔台、切石机…）
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
