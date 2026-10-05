# 无限加点（Infinite Stats）

> 泰拉瑞亚（Terraria）风格的**无限属性加点系统** —— 打怪升级 → 自由分配属性点 → 打造属于自己的 Build。

| | |
|---|---|
| **Mod ID** | `infinitestats` |
| **当前版本** | `1.33.0`（以 `gradle.properties` 的 `mod_version` 为准） |
| **运行环境** | Minecraft `1.20.1` / Forge `47.3.0+` |
| **许可证** | MIT |

---

## ✨ 核心特性

- 🎯 **经验升级加点** —— 击杀怪物与挂机被动获取经验，升级发放可分配属性点
- 📊 **96 个内置属性**，分为攻击 / 防御 / 机动 / 功能四大类
- 🔌 **自动发现外部属性** —— 其他模组注册的属性自动收进「外部属性」分类，无需手动适配
- 🌏 **内置 30+ 模组的外部属性中文译文** —— 装包即用，不再显示 `[gunsmithlib:bullet_damage]` 这类原始键名
- 🖥️ **属性面板 GUI**（默认 `P`）—— 分类浏览 / 搜索 / 一键加点 / 重置
- 📟 **HUD 实时显示**（默认 `H` 开关）
- 🛠️ **物品编辑器**（默认 `O`）—— 可视化编辑物品的附魔、词条、NBT
- 💎 **内置 EMC 等价交换**（默认 `V`）—— 学习 / 查询 / 转化，支持 ProjectE 联动
- 🎒 **随身工作台 / 随身熔炉 / 随身铁砧** —— 自动补料、待炼仓排队、支持存储网络抽取
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
- **作用**：96 条内置属性可以逐条关掉 —— 关掉后一律**从属性面板隐藏、不能继续加点**，页脚里对应的功能入口也一起消失；**「整条失效」还是「只隐藏」由下面的模式决定**（**全局设置，由整合包作者定**，配置项 `GUI.featureDisableMode`；OP 也可用指令改）：

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

### 💰 铁砧经验减免 / 交易即刻补货 / 死亡不掉经验

- `anvil_cost`：铁砧等级消耗 **-5% / 点**（最多 -90%）。减免在「过于昂贵」判定**之前**写入最终消耗，所以该门槛也一并被压低。实现走 Mixin（`AnvilMenu#createResult`）—— Forge 的 `AnvilUpdateEvent#setCost` 只在同时设置 `output` 时才被 `ForgeHooks.onAnvilChange` 采用，而且事件在附魔消耗计算之前触发，只改 cost 是彻底无效的。
- `trade_restock`（3 点解锁）：交易后**立刻补货**，同一条目可以连续买。只重置 `uses`，不调用 `Villager#restock()` —— 后者的 `updateDemand()` 反复执行会把需求加价越补越贵。
- `keep_xp`（3 点解锁）：死亡**经验等级与经验条保留**（配合 `keep_inventory` 可完整保命）。

### 🧰 随身工具面板（统一入口）

- 所有随身站点（工作台 / 熔炉 / 铁砧 / 末影箱 / 锻造台 / 附魔台 / 切石机 / 织布机 / 制图台 / 磨石）
  收敛到**一个**「随身工具」入口，点开后是独立面板：
  - 未解锁的工具按钮**灰色不可点**，悬停会提示需要解锁哪条属性；
  - 已解锁的点一下即打开，服务端还会再校验一次开关（改造过的客户端无法绕过）。
- 页脚导航行只有 3 个相关入口：**随身工具 / 天气 / 强加载**（后两个需先激活对应属性才出现）。
- 全部复用原版菜单类型，客户端自动套用原版界面，无需自建界面与菜单类型。
- **随身铁砧是唯一例外**：它要放宽原版「过于昂贵」门槛并把消耗封顶到 **50 级**，而客户端必须能区分「随身铁砧」与「真铁砧」，因此它拥有自己的菜单类型 `PortableAnvilMenu`（客户端仍套用原版铁砧界面），由 Mixin 在两端识别后只对随身铁砧生效。
- 附魔台 / 切石机 / 织布机 / 制图台 / 磨石 / 锻造台走 `crafting/PortableStationMenus`：传真实的
  `ContainerLevelAccess`（玩家脚下的维度 + 坐标）以保证世界侧回调正常执行（附魔等级计算、点击附魔、
  制图台成品、关闭时归还物品），再用子类覆写 `stillValid` 去掉「必须站在对应方块旁」的校验。
  - **随身附魔台**是 **1 点解锁的开关**（与其余站点一致）：可随时打开附魔台，
    **不再依赖周围书架**。附魔强度拆成**隐藏属性 `enchant_power`**，**加点入口就在附魔界面里**——
    打开后界面右侧多出一块面板，显示「附魔强度 `Lv.N / 15`」与 `−` / `+` 按钮，每点 +1
    （等价于原版 1 个书架），**强度越高三档附魔等级越高，15 点即原版满级（30 级附魔）**；
    点一下 ± 就会当场重算三档附魔，能一边加点一边看效果。工具面板的悬停提示会显示当前强度
    （如「当前附魔强度 7 / 15」）；
  - **随身末影箱**与原版末影箱**共用同一份库存**。
- 对比：`/infstats craft | furnace | anvil | enderchest | smithing` 指令保持不变；
  新增的 5 个站点只从「随身工具」面板进入。

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

### 🔍 拼音搜索（已内置，无需前置）

- 属性面板 / 效果过滤器 / 物品编辑器 / 物品选择 / 入库过滤 / EMC 转化桌 / 成就统计面板的搜索框支持拼音，
  例如 `zsj` → 钻石剑、`tiezh` → 铁砧。
- 拼音能力来自 **`pinyin_search`（通用拼音搜索库）**，已通过 **JAR-in-JAR 内嵌**在本模组里，
  **玩家 / 整合包无需另装**（`build.gradle` 用 `compileOnly` + `jarJar` 引用官方坐标
  `com.github.2779789119:pinyinsearch`）。
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
| `PassiveEffects.veinMinerMaxBlocks` | `64` | 连锁挖掘最大方块数 |
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

> 大部分配置改后即时生效，标注「需重启」的除外。

---

## 🧬 内置属性一览（共 96 个）

> 标 **[开关]** 的为功能型开关，激活后即时生效，可随时开启 / 关闭。

### ⚔️ 攻击（17）

`attack_damage` 攻击伤害 · `attack_speed` 攻击速度 · `crit_chance` 暴击率 · `crit_damage` 暴击伤害 · `armor_penetration` 护甲穿透 · `knockback_power` 击退力度 · `projectile_damage` 远程伤害 · `life_steal` 生命偷取 · `life_steal_aoe` 范围吸血 · `damage_reflection` 反伤 · `execute` 处决 · `true_damage` 真实伤害 · `reduce_max_health` 削弱最大生命 · `scope_attack` 范围攻击 · `repulsion` 斥力 · `infinite_arrows` **[开关]** 无限弓箭（弓 / 弩） · `multi_shot` 多重射击

### 🛡️ 防御（17，含 1 隐藏）

`max_health` 最大生命 · `armor` 护甲 · `armor_toughness` 盔甲韧性 · `health_regen` 生命恢复 · `damage_reduction` 伤害减免 · `knockback_resist` 击退抗性 · `fall_resist` 摔落抗性 · `block_chance` 格挡几率 · `dodge_chance` 闪避几率 · `absorption_shield` 吸收护盾 · `auto_revive` 自动复活 · `fire_immunity` **[开关]** 火焰免疫 · `projectile_immunity` **[开关]** 弹射物免疫 · `explosion_immunity` **[开关]** 爆炸免疫 · `suffocation_immunity` **[开关]** 窒息免疫 · `debuff_immunity` **[开关]** 效果过滤 · `invincibility`（隐藏）无敌

### 🏃 机动（10）

`movement_speed` 移动速度 · `swim_speed` 游泳速度 · `jump_height` 跳跃高度 · `multi_jump` N 段跳 · `step_height` 抬腿高度 · `auto_step` **[开关]** 自动抬腿 · `fly_speed` 飞行速度 · `fly` **[开关]** 飞行 · `climb_speed` 爬梯加速 · `no_fall_damage` **[开关]** 免摔落伤害

### 🧰 功能（52）

`luck` 幸运 · `mining_speed` 挖掘速度 · `mining_level` 挖掘等级 · `reach` 方块交互距离 · `entity_reach` 实体交互距离 · `xp_gain` 经验获取 · `loot_luck` 掉落幸运 · `double_loot` 双倍战利品 · `item_magnet` **[开关]** 物品磁铁 · `xp_magnet` **[开关]** 经验磁铁 · `auto_deposit` **[开关]** 自动入库 · `vein_miner` **[开关]** 连锁挖掘 · `auto_smelt` **[开关]** 自动冶炼 · `auto_fish` **[开关]** 自动钓鱼 · `instant_bite` **[开关]** 立即咬钩 · `breed_no_cooldown` **[开关]** 繁殖无冷却 · `instant_grow` **[开关]** 一键长大 · `crafting_bonus` 合成加成 · `auto_repair` **[开关]** 自动修理 · `repair_amount` 修理量 · `use_speed` 使用速度 · `bow_draw_speed` 拉弓加速 · `cooldown_reduction` 冷却缩减 · `projectile_tracking` **[开关]** 弹射物追踪 · `no_invincibility_frames` **[开关]** 取消无敌帧 · `night_vision` **[开关]** 夜视 · `water_breathing` **[开关]** 水下呼吸 · `no_hunger` **[开关]** 免饥饿 · `invisibility` **[开关]** 隐身 · `keep_inventory` **[开关]** 死亡不掉落 · `keep_xp` **[开关]** 死亡不掉经验 · `time_accel` **[开关]** 时间加速 · `time_accel_radius` 加速半径 · `cross_dimension_teleport` **[开关]** 跨维度传送 · `fixed_point_teleport` **[开关]** 定点传送 · `portable_crafting` **[开关]** 随身工作台 · `portable_furnace` **[开关]** 随身熔炉 · `portable_anvil` **[开关]** 随身铁砧 · `portable_ender_chest` **[开关]** 随身末影箱 · `portable_smithing` **[开关]** 随身锻造台 · `portable_enchanting` **[开关]** 随身附魔台 · `enchant_power`（隐藏）附魔强度 · `portable_stonecutter` **[开关]** 随身切石机 · `portable_loom` **[开关]** 随身织布机 · `portable_cartography` **[开关]** 随身制图台 · `portable_grindstone` **[开关]** 随身磨石 · `weather_control` **[开关]** 天气控制 · `chunk_loader` **[开关]** 区块强加载 · `trade_discount` 村民交易折扣 · `trade_restock` **[开关]** 交易即刻补货 · `anvil_cost` 铁砧经验减免 · `pe_auto_learn` **[开关]** PE 自动学习

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

**Windows 上一键构建**：仓库根目录的 `build.bat`（双击即可，参数写在脚本名后）：

| 命令 | 作用 |
|---|---|
| `build.bat` | 增量构建 + 把产物复制到 `dist\` |
| `build.bat clean` | 先 `clean` 再构建（全量） |
| `build.bat check` | 只编译（`compileJava` + `compileGameTestJava`，最快） |
| `build.bat nocopy` | 只构建，不复制 |
| `build.bat deploy` | 构建 + 复制到 `dist\` + 部署进整合包 `mods\`（自动探测已装有本模组的整合包，也可用第二个参数指定目录） |

脚本会自动跳过 `-all.jar` 中间产物、校验 jar 里含 refmap，并在构建时一并编译 `gameTest` 源集。

> `gradle.properties` 里带了一行 `systemProp.net.minecraftforge.gradle.check.certs=false`：
> 本机网络（代理 / 杀软中间人证书）过不了 ForgeGradle 对 `maven.minecraftforge.net` 的证书预检时，
> 插件 apply 阶段会直接失败并报 `Failed to validate certificate for host`。这只是 FG 的一次连通性预检，
> 关掉后真正的依赖下载依旧走 JDK 正常的 TLS 校验；换到网络正常的环境可以删掉这一行。
要分发的始终是 `build\libs\infinite_stats-无限加点-1.20.1-<版本>.jar`（内嵌的 `pinyin_search` 已并入其中）。
注：`build.bat` 内容保持**纯 ASCII** —— cmd 按 OEM 代码页解析 .bat，文件里出现中文会导致批处理语法报错。

> `build` 的耗时大头是 `reobfJar`（Forge 全量重映射，**不可跳过**，跳过产物无法在正式客户端加载）。只想验证改动时用 `compileJava`，要进游戏测用 `runClient`。

拼音搜索库 `pinyin_search` 用官方 JitPack 坐标
（`com.github.2779789119:pinyinsearch`，仓库 `https://jitpack.io`）接：`compileOnly` 取编译期 API，
`jarJar` 把它**内嵌**进产物 jar（Forge 的 JAR-in-JAR，嵌套模组运行时加载），玩家无需另装。

`jarJar` 这一行有三个坑（都已在 `build.gradle` 里处理好，改依赖时别改坏）：

- 版本必须写 **maven 区间**（如 `[1.1.0,2.0.0)`），写精确版本会报
  `The given version specification is invalid`。
- 必须加 **`transitive = false`**：该库的 POM 把 Forge / MC 的一堆库算作依赖，不切断的话
  会被全部嵌进来（实测主 jar 从 875 KB 涨到 91.7 MB / 116 个嵌套 jar）。
- FG 的 `jarJar` 默认只把内嵌内容放进单独的 `-all.jar`；本工程已在 `jar` 任务里把
  `META-INF/jarjar/**` 合并进主产物，**照旧分发主 jar 即可**（构建后可确认主 jar 里有
  `META-INF/jarjar/pinyinsearch-<ver>.jar` 与 `META-INF/jarjar/metadata.json`）。

源码结构：

```
src/main/java/com/infinitestats/
├── InfiniteStats.java     # 模组入口
├── Config.java            # Forge 配置
├── client/                # GUI 与客户端逻辑（属性面板 / HUD / 物品编辑器 / EMC / 传送点 / 成就…）
├── command/               # 服务端命令（/infstats）
├── compat/                # 兼容层（JEI、存储网络桥接等）
├── crafting/              # 随身工作台 / 随身站点菜单（附魔台、切石机…）
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
