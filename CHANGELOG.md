# 更新日志 (Changelog)

## [1.49.0] - 2026-10-08

### 🔗 随身工作台 / 随身熔炉接入 EMC（材料与燃料的第二来源）

- **随身工作台**：取料链新增最后一级 **EMC 采购** —— 「背包 → 存储网络 → EMC」，
  只要该物品**已学过**且有 EMC 值，就能直接花 EMC 拿料摆进网格：
  - **JEI 一键转移**（`PortableCraftingMenu#fillGridFromIngredients`）：网格 / 余料 / 背包 / 网络都取不到时
    按 EMC 采购 1 个；整批配方有任何一项凑不齐时**全额退款**（回滚已花的 EMC），不会「买了一半又失败」。
  - **成品取出后的自动补料**（`AutoRefillResultSlot#refill`）：网络与背包补不齐的差额改由 EMC 兜底 ——
    于是「用 EMC 续料连续合成」成立，对齐奥术石板「合成网格直接从转化库存取料」的语义。
- **随身熔炉**：「抽矿物 / 抽燃料」在**没有连接任何存储网络**、或**网络里没有该物品**时，
  改用**槽内现有物品作模板**从 EMC 知识库采购补齐（矿物单次 ≤64、燃料单次 ≤1024）；
  槽内为空时不做采购，仍走原来的提示 / 诊断。
- **防悄悄扣钱**：新增 `EmcTransactions.purchase(...)`（按物品扣 EMC 直接换货，不碰背包）
  与 `EmcTransactions.format(...)`；任何一次 EMC 补齐都会同步余额并给一条动作栏提示
  「已用 X EMC 自动补齐材料」。工作台 / 熔炉右栏各加一行 **EMC 余额**
  （客户端直接读 capability，**没有新增同步包**）。
- 熔炉采购时若目标槽位已满，会把刚买到的那份**退回 EMC**，避免白扣。
- 中英文案 5 条；**不新增网络包，协议版本不变**。

## [1.48.0] - 2026-10-08

### 🔗 等价交换（EMC）与其他功能联动

- **属性系统联动**：新增两个功能类属性 —— **`emc_learn_bonus`「EMC 学习收益」**（+5% / 点，最多 +25%）
  与 **`emc_extract_discount`「EMC 转化折扣」**（-6% / 点，最多 -30%），属性总数 **104 → 106**。
  学习 / 卖出物品的入账与提取 / 以 EMC 合成的支付价统一走新增的 `emc/EmcPricing`，
  基础值仍取自 `EmcDatabase`（装了 ProjectE 时即 ProjectE 的 EMC 值），属性只在其上做增减。
- **存储网络联动**：从 EMC 取出物品时，若背包放不下，剩余部分会**自动写入已连接的存储网络**
  （RS / AE2 / 汤姆存储 / Sophisticated Backpacks / BD），仍然放不下才掉在地上 ——
  复用「自动入库」的 `NetworkIO.insert`，不再出现「取出一堆结果掉一地」。
- **背包联动**：EMC 界面左下新增「**回收背包**」按钮 —— **Shift + 点击**把背包（0–35 格）中所有
  **已学**物品一次性卖出为 EMC（复用既有 `EmcSellAllPacket`，服务端二次校验）；不带 Shift 点击只在动作栏
  提示用法，避免误卖随身物品。
- 客户端合成价格预览同步应用转化折扣（与服务端 `EmcPricing` 使用同一系数）；
  中英文案补齐（2 组属性键 + 回收按钮 2 条），README 属性总数与清单同步。
- **不新增网络包，协议版本不变**。

## [1.47.0] - 2026-10-08

### ✨ 等价交换（EMC）：自动学习常驻 + 界面重做为「奥术转化石板」

- **自动学习常驻**：`pe_auto_learn` 属性不再因「未安装 ProjectE」而被隐藏，也不再依赖 ProjectE 才能生效 ——
  未装 ProjectE 时仍会把物品记入本模组的 EMC 知识库；装了 ProjectE 则**两套知识库同时写入**
  （本模组 EMC 系统 + ProjectE 转化知识库）。新增 `ProjectEBridge#hasKnowledge` 反射查询，
  已有知识时不再重复调用 API；本地缓存避免每 2 秒重复写库 / 发包。
- **界面重做为「奥术转化石板」（Arcane Transmutation Tablet）**（默认 `V` 键打开）：342×256 新布局 ——
  顶部 EMC 余额与搜索框、左侧 12×7 已学物品网格（按 EMC 升序，支持 `@模组` / `#标签` / 拼音搜索，
  滚轮与按钮翻页）、右侧 3×3 合成网格 + 结果槽 + 学习槽、底部玩家背包与快捷栏；悬停显示物品名与 EMC 值。
- **取物交互对齐原版转化石板**：左键取**一整组**、右键取 **1 个**、Shift + 左键**用尽 EMC 取满背包**
  （原实现为「左键 1 个 / Shift 一组 / 右键买满」）。
- **新增 3×3 EMC 合成网格**（奥术石板的标志性功能）：网格中的材料**不被消耗**，
  点击结果槽时按配方材料的 **EMC 总额扣款**后给货；材料中存在没有 EMC 值的物品时拒绝合成并提示。
  服务端用 `TransientCraftingContainer` + `RecipeManager#getRecipeFor` 匹配工作台配方，
  客户端只做价格预览（服务端二次校验余额）。
- 退出界面时网格内物品归还玩家（`EmcMenu#removed`）；`EmcMenu` 新增槽位索引常量
  （37–45 = 合成网格，46 = 结果槽），**既有槽位顺序与索引保持不变**（学习槽 0、快捷栏 1–9、主背包 10–36）。
- 中英文案同步（新增 10 条）；配置项 `autoLearnProjectE` 注释更新为「常驻功能」。
- 未新增网络包，**协议版本不变**；界面内的自制定价器入口随重做移除
  （自定义定价仍可用 `config/infinitestats/custom_emc_values.json` 设置）。

## [1.46.7] - 2026-10-08

### 🗡️ 新联动：拔刀剑（SlashBlade: Resharped）—— 物品编辑器可直接改刀的成长数值

- 物品编辑器（默认 `O`）底部新增 **「拔刀剑」** 按钮：**主手是拔刀剑时可点**（未装拔刀剑或手持别物时置灰），
  点开进入独立的拔刀剑编辑界面，可修改 **杀敌数 / 耀魂数 / 锻造数 / SA（特殊攻击）/ SE（特殊效果）**。
- **杀敌数、耀魂数、锻造数**：顶部一行三个整数输入框，分别对应刀状态里的
  `killCount` / `proudSoul` / `RepairCounter`。
- **SA（特殊攻击）**：左栏**单选**列表，列出 `SlashArtsRegistry` 里**全部已注册剑技**
  （含整合包 / KubeJS 追加的），点击即选中；显示名走刀自己的译文（`slash_art.<ns>.<path>`）。
- **SE（特殊效果）**：右栏**多选**列表，列出 `SpecialEffectsRegistry` 全部效果，点击切换勾选，
  显示名走 `se.<ns>.<path>`。两栏各有独立搜索框（支持拼音）。
- **写入口径**（对着 1.20.1 重铸版源码逐项核对）：刀状态由 Forge 能力
  `ItemSlashBlade.BLADESTATE`（`ISlashBladeState`）承载，序列化后写在物品 NBT 的
  `bladeState` 子标签下。**战斗逻辑（放 SA、结算 SE）读的是内存里的能力对象，不是现读 NBT** ——
  所以写入流程是「`getCapability(BLADESTATE)` → 调 setter → `serializeNBT()` 回写 NBT」，
  保证**当场生效**且存档 / 同步不缺数据；能力取不到时退化为「直接改 NBT」的兜底。
- **服务端二次校验**：SA 必须是注册表内的合法剑技（非法则整包拒绝，避免战斗时取到空剑技）；
  SE 逐项过滤，非法项丢弃；只校验「槽位一致 + 物品 id 一致 + 确实是拔刀剑」，
  刻意不做原始值快照校验（杀敌数随战斗实时增长，快照会频繁误判）。
- 新增 `compat/SlashBladeCompat`（**全程反射**，未装拔刀剑时零开销短路，本模组不声明编译期依赖，
  也不需要 `compatApiStub` 存根）、`network/EditItemBladePacket`、`client/SlashBladeEditScreen`；
  物品编辑器底部按钮区改为**四键等宽居中**布局。**协议版本 18 → 19**。
- 中英文案同步（`zh_cn` / `en_us`）。

## [1.46.6] - 2026-10-08

### ⚙️ 新联动：机械动力（Create）「无限应力」（`create_infinite_stress`）

- 新增开关型属性 **`create_infinite_stress`「无限应力」**（投入 **3 点**解锁）：**你自己放置的**
  动力网络不再因为应力（Stress, SU）不足而超载停机 —— 机器照常运转、应力表指针回到低位、
  护目镜读数正常。判定范围是**整张网络**（网络里只要有**任意一块**是你放的，且你在线并已解锁），
  别人基地与公共机器不受影响。属性总数 **103 → 104**。
- 归属口径复用「无限能源」那套方块归属表：`util/EnergyOwnershipStore` 因此更名为
  **`util/BlockOwnershipStore`**（新增 `getOwner(BlockPos)`），由两条属性共用；
  **存档名 `infinitestats_energy_owners` 保持不变**，已有世界不会丢归属数据。
- 落点选在 **`KineticNetwork.calculateCapacity()` 的返回值**上 —— 它是整个动力网络产能的唯一汇总点。
  改这一处，成员方块（`overStressed`）、应力表（指针位置＝耗能 ÷ 产能）与发给客户端的同步包
  会**同时**拿到「无限产能」，服务端与客户端结论天然一致。
  只拦服务端那个超载布尔量是不行的：客户端收到包后会拿 `Capacity` / `Stress` **自己重算**
  同一个判据，结果是机器在世界里转、屏幕上不动。
- **存档不落改写值**：新增 `CreateKineticBlockEntityMixin`，在
  `write(compound, clientPacket)` 的 HEAD / RETURN 上「发包给客户端时保留改写值、写存档时换回真值」
  （真值由 `CreateStressCompat` 在改写前记下）。不这么做的话，10 亿会被写进存档，
  重登后被 `initFromTE` 当成「未加载区块的产能」收进网络，这张网络从此永久不过载 ——
  属性关掉、甚至卸载本模组都退不回来，等于污染存档。
- 产能改写值取 **10 亿 SU**（够大但有限），刻意不用 `Float.MAX_VALUE`：真实网络的耗能不过几十万 SU，
  10 亿永远够用；而 `Float.MAX_VALUE` 参与乘法 / 界面显示时容易变成 `Infinity` 或 `3.4E38` 这类读数。
- **转速（RPM）与传动逻辑一概不动** —— 没有动力源还是不会转；方块必须是玩家本人放置的
  （`/setblock`、蓝图粘贴、其它模组生成的不进归属表），且解锁者在线时才生效。
- 新增编译期存根 `src/compatApiStub/java/com/simibubi/create/content/kinetics/KineticNetwork.java` 与
  `.../base/KineticBlockEntity.java`；`InfiniteStatsMixinPlugin` 增加 `com.simibubi.create.` 前缀门控
  （未装机械动力时两个 Mixin 根本不会应用，属性放在面板里也不会报错）；两个注入点均 `require = 0`。
- 属性描述、中英文案、属性面板图标、README、`mod_description` 同步；**协议版本不变**。
- 已用 `javap` 对着整合包里的 `create-1.20.1-6.0.8.jar` 逐项核对注入点与成员签名
  （`calculateCapacity` 的汇总逻辑、`write` 的两个出口、`initFromTE` 的持久化路径）。

## [1.46.5] - 2026-10-08

### 🔋 「无限能源」扩展到玩家自己放置的能量方块

- 「无限能源」（`infinite_energy`）此前只覆盖玩家**自身携带 / 骑乘**的能量源，世界里的方块
  （储能方块、耗电机器、发电机等）一个都不补。现在新增覆盖：**玩家自己放置的能量方块**也会被补满。
- 新增 `util/EnergyOwnershipStore`：按维度持久化的「方块放置归属」表（`BlockPos → 放置者 UUID`），
  随存档保存。只记录带方块实体的方块（纯方块永远不会有能量能力），因此表里只有机器 / 储能这一小撮。
- 新增 `event/BlockOwnershipEvents`：监听 `BlockEvent.EntityPlaceEvent` 记录玩家放置的方块实体、
  `BlockEvent.BreakEvent` 清除归属。只认**玩家本人**放置的坐标 —— 不会顺手把别人基地 / 公共机器补满，
  非玩家来源（发射器 / 其它模组生成 / `/setblock`）一概不计。
- `compat/EnergyCompat` 新增方块扫描：只查玩家周围**已加载区块**里的方块实体（绝不为了补电强制生成新区块），
  归属为本人且暴露 `ForgeCapabilities.ENERGY` 的才补；逐面尝试能量能力，兼容「只在特定面暴露能量」的机器。
  方块扫描单独走配置间隔，玩家自身携带 / 骑乘的仍是**每 tick** 补满。
- 新增配置（`InfiniteEnergy` 分组）：`infiniteEnergyBlocks`（默认 true）、`energyBlockRadius`（默认 16）、
  `energyBlockInterval`（默认 20 tick = 1 秒）。
- 属性描述、中英文案、README 同步；**不新增属性条目，属性总数不变**；协议版本不变。

## [1.46.4] - 2026-10-08

### 🔋 「无限电量」改名「无限能源」，并泛化到所有 FE 系能源

- 属性 id 从 `sbw_infinite_energy` 改为 `infinite_energy`；显示名「无限电量」→「无限能源」。
- **从「只对卓越前线载具生效」泛化为「你拥有的所有能量源恒满」**：统一走 `ForgeCapabilities.ENERGY`（FE）。
  在 1.20.1 里 RF 就是 FE 的别名，Mekanism / 热力（Thermal）/ EnderIO / 沉浸工程 / 机械动力附属等
  最终都通过 `IEnergyStorage` 暴露能量 —— 所以「把 FE 补满」这一件事就覆盖了这些全部能源类型。
- 新增 `compat/EnergyCompat`：每 tick 给「背包 36 + 盔甲 4 + 副手 1、Curios 饰品槽（未装则跳过）、
  正骑乘的实体」上的每个 `ForgeCapabilities.ENERGY` 补到上限；已经是满的就不写、不标脏物品。
  - 背包 / 饰品里其它模组的能量源（电池、能量背包、能量工具等）没有统一的扣电注入点可拦，
    只能靠每 tick 补满 —— 对读数与 HUD 而言就是恒满。
  - `CuriosBridge` 新增只读的 `forEachStack` 供这里复用（与「死亡不掉落」用的 `takeAll` 不同，不写、不清空）。
- 卓越前线载具仍走**注入点**那一半（`SbwVehicleEnergyStorageMixin` 拦 `extractEnergy`，
  不让载具在 tick 内被引擎 / 能量弹药武器扣到闪）；载具电量的每 tick 补满改由 `EnergyCompat` 统一管，
  `SuperbWarfareCompat.onTick` 不再自己补电量（只留补血量）。
- 判定口径：载具那一半仍按「乘员里有解锁者」；背包 / 饰品那一半按「玩家本人解锁」。
- 不覆盖 IC2 EU / 格雷科技 EU（GTCEu）：两者走各自能量 API、不经 Forge Energy，且本整合包未安装
  （README 已注明；日后加入只需在 `EnergyCompat` 补一个对应能量类型的充能适配器）。
- 属性描述、中英文案、README、mods.toml、mod_description 同步；属性总数统一记作 **103**
  （本项只是改名、不新增条目，那个 +1 是 1.46.3 的 Goety 灵魂能量此前漏改的计数）。
- 协议版本不变。

## [1.46.3] - 2026-10-08

### 🕯️ 新增 Goety 联动：无限灵魂能量

- 新增一条开关型属性 `goety_infinite_soul`（投 **3 点**解锁），只对诡厄巫法（Goety）生效。
  解锁后玩家的灵魂能量**恒满** —— 施法 / 仪式 / 灵魂修补器 / 巫妖回血等所有消耗灵魂的路径
  都不会再把灵魂用光，灵魂能量条一直满格。
- Goety 的灵魂有两套独立的池子，各补一份（覆盖它自己的 `getSoulsAmount` 门槛判定与 HUD 读数）：
  - **Arca / 灵魂能量池**（`SEActive = true` 的玩家）：走 `SEHelper#increaseSESouls(Player, int)`，
    它内部 `Math.min(当前 + 增量, maxArcaSouls)` 自己夹到上限；返回 `true` 才补发 `sendSEUpdatePacket`
    （已满时返回 `false`，不产生每 tick 发包）；
  - **灵魂图腾池**（Curios 里的 Totem of Souls）：走 `ITotem` 静态读写，把物品 NBT 的 `"Souls"`
    补到 `"Max Souls"` 上限；上限读不到（刚捡的图腾还没写上限）就跳过，等图腾自己 tick 补上。
- 纯**每 tick 兜底**（无 Mixin）：`SEImp` 不持有玩家引用，无法在能力层判断「这个玩家解锁了」，
  所以不注入能力类，而是在玩家 tick 里按玩家判定补满 —— 与 `sbw_infinite_energy` 的兜底同一套思路。
- 全程反射（与 `SuperbWarfareCompat` 同思路），没装 Goety 或版本对不上时整体短路，只让这一条属性退化为原版行为。
- 协议版本不变。

## [1.46.2] - 2026-10-08

### 🚙 补充：卓越前线载具的干扰弹（decoy）也随「无限子弹」一起无限

- **现象**：载具的干扰弹打完不补，得自己往车上塞 `FLYING_FLARE_AMMO`。
- **链路**（反汇编核对）：装填走 `VehicleWeaponUtils#reloadDecoy` —— 拿车上的
  `ModItems.FLYING_FLARE_AMMO` 装填，装填量 = `min(携带量, computed().decoyMagazineSize)`；
  发射扣的是同步量 `DECOY_COUNT`，容量口径即 `DefaultVehicleData#getDecoyMagazineSize()`。
- **改法**：每 5 tick 的兜底里把玩家所在载具的 `DECOY_COUNT` 顶到 `computed().decoyMagazineSize` ——
  既不用补弹，也不会真吃玩家的干扰弹物品。沿用「无限子弹」这一条属性，不新增属性。
- **不动**：携带量 `DECOY_ITEM_COUNT`（由载具自己的 `tick()` 每 tick 从乘员背包重算，写了也会被覆盖，
  HUD 上的携带量读数照旧）、补充冷却 `DECOY_RELOAD_COOLDOWN`（射速限制，不是弹药）。
- 干扰弹句柄**单独一层懒解析**：取不到只让「干扰弹无限」这一半失效。
- README「载具武器」小节补齐；协议版本不变。

## [1.46.1] - 2026-10-08

### 🚙 修正：卓越前线的载具武器不用再手动塞一发弹药了

- **现象**：载具武器仍然「必须先手动放一发弹药才能开火」。
- **原因**：1.45.0 补好的是**判定口径**（让每次开火后的补弹 / 清热量对载具生效），但补不到「没打过一发」的时候 ——
  载具武器不在物品栏里，每个武器一份 `GunData` 挂在载具的同步数据 `GUN_DATA_MAP` 上，
  而开火收尾那几个注入点只有「已经打过一发」之后才会被触发；**空弹匣连 `canShoot(Entity)` 都过不了**，
  压根进不了开火链路 → 没有任何一处会把空弹匣顶起来，只能手动塞一发当引子。
- **改法**：每 5 tick 的兜底多走一趟「玩家正乘坐的载具」—— 反射 `VehicleEntity#getGunDataMap()`
  取出该车每个武器的 `GunData`，逐个 `fillMagazine`（容量仍走模组自己的 `GunProp.MAGAZINE`）并清热量。
  进车约 0.25 秒后全部武器满匣，直接可以打。
- 载具武器表句柄**单独一层懒解析**：取不到只让「载具补弹」这一半失效，
  不影响载具血量 / 电量与三条枪械属性。
- README「载具武器」小节补齐这一说明。
- 协议版本不变。

## [1.46.0] - 2026-10-08

### 🚙 新增两条卓越前线载具属性：无限电量 / 无限载具血量

- 两条新的开关型属性（各投 **3 点**解锁），只对卓越前线（Superb Warfare）生效，口径与前三条枪械属性一致
  —— **该载具的乘员里有解锁者**即生效：
  - `sbw_infinite_energy` **无限电量**：载具电量恒满 —— 行驶耗电 / 能量弹药武器 / 外部机器抽电都不再扣，
    载具面板上的电量条也一直是满的；
  - `sbw_infinite_health` **无限载具血量**：载具血量恒满 —— 打不坏、打不死。
- **无限电量的落点**：`VehicleEnergyStorage#extractEnergy(int, boolean)`。
  用 `javap` 扫过整合包里的 `superbwarfare-0.8.9.2`：扣电有两条路 ——
  载具自己的 `VehicleEntity.consumeEnergy(int)`（引擎行驶 `VehicleEngineUtils`、部分载具武器、炮塔 AI）
  与按 Forge Energy 能力扣电（能量弹药策略 `EnergyAmmoStrategy`、外部机器抽电），
  而它们最终都会走到这个电量存储的 `extractEnergy`（它同时是载具内部读数 / 扣电的对象，
  也是 `ForgeCapabilities.ENERGY` 暴露出去的那个对象）—— 拦一处即全覆盖，且不挑来源；
  只拦 `consumeEnergy` 会漏掉能量弹药那条。
  返回的是「本该抽到的量」（`min(想抽的量, 当前电量)`）而不是 0：调用方据此认为这次抽电成功，
  载具自己的「够不够电」判断（`canConsume` / 弹药策略）继续放行，电量却一点没少。
- **无限载具血量的落点**：`VehicleEntity#setHealth(float)`，用 `@ModifyVariable` 把**入参**改成满血。
  已核对它是载具血量的**唯一写入口**（全类只有它把血量写进同步数据 `HEALTH`，写入时钳到
  `[-最大血量-10, 最大血量]`），子弹 / 炮弹 / 撞击 / 爆炸无论走哪条链路最后都是这一句。
  - 为什么改入参而不是取消那次写入：取消只能让血量停在旧值上；改入参是「无论谁要写多少、
    写进去的都是满血」—— 读数、HUD、它自己的低血提示 / 冒烟特效自然全都跟着满血走，
    而且不会递归（兼容层只反射读 `getMaxHealth()`，不回写）。
  - 为什么不放在每 tick 兜底里回血：一炮打掉满血坦克是常有的事，**同一 tick 内**血量归零
    就会走 `destroy()`，事后补根本来不及。
  - **部件血量（履带 / 引擎 / 炮塔）不需要单独处理**：`VehicleEffectUtils.handlePartHealth(...)`
    只在「主血量低于上限 5%」时把部件清零，主血量恒满就永远走不到那一步。
- **每 5 tick 兜底**：`SuperbWarfareCompat.onTick` 把「属性解锁之前就已经掉了电 / 掉了血」的那台补满
  （注入点只保证「以后不再变少」）；已经是满的就不写，不会反复标脏同步数据、反复推给客户端。
- **各自独立降级**：载具句柄（`getHealth` / `setHealth` / `getMaxHealth`）与枪械句柄**分开解析**，
  「电量存储 → 载具」的反查（`VehicleEnergyStorage#getVehicle()`，Kotlin `protected var` 的编译产物）
  也单独懒解析 —— 卓越前线换版本导致某一边取不到时，只让那一边退化成原版行为
  （注入点 `require = 0`、反射失败静默跳过），不影响另一边，更不会让游戏起不来。
- 编译期存根新增两份：`src/compatApiStub/java/com/atsuishio/superbwarfare/` 下的
  `entity/vehicle/base/VehicleEntity.java` 与 `capability/energy/VehicleEnergyStorage.java`
  （只抄被注入 / 被反射用到的成员，不进 jar、运行时不加载）。
- 新增两个 Mixin：`SbwVehicleMixin`（载具血量）、`SbwVehicleEnergyStorageMixin`（载具电量）——
  都在 mixin 配置的公共列表里，并由 `InfiniteStatsMixinPlugin` 按 `com.atsuishio.superbwarfare.`
  前缀在没装卓越前线时**根本不应用**。
- 属性面板图标、中英文案、README 同步；内置属性 100 → **102**（功能类 52 → 54）。
- 协议版本不变。

## [1.45.0] - 2026-10-08

### 🚙 三条枪械属性对卓越前线载具武器同样生效（无限子弹 / 零热量 / 无后坐力）

- **为什么之前不生效**：载具武器和手持枪是两套数据、两条链路，逐个反汇编核对后定位到三处：
  1. 载具武器不放在物品栏 —— 每个武器一份 `GunData` 挂在载具的同步数据 `GUN_DATA_MAP` 上，
     开火同样走 `GunData.shoot(ShootParameters)`（`VehicleEntity.vehicleShoot(...)` 里就是 `data.shoot(params)`），
     所以补弹匣 / 清热量这两个注入点**本来就覆盖载具**；卡住的是「谁解锁了」——
     载具开火时 `ShootParameters.shooter` 是**载具本体**（`VehicleEntity.getAmmoSupplier()` 就是 `return this`），
     真炮手在 `ammoSupplier` 字段里，只读 `shooter` 会判定失败、整条被跳过。
  2. `hasInfiniteBackupAmmo(Entity)` 收到的也是载具（不是玩家），同样判定不出来。
  3. 载具的镜头抖不走 `handleGunRecoil`（那条要求手持枪），而是 `VehicleEntity.afterShoot(...)` 里的
     `GunData.shakePlayers(载具)`：读 `GunProp.SHOOT_SHAKE` 后发 `ShakeClientMessage` 给附近玩家。
- **改法**：
  - `CompatToggles` 新增 `isActiveForShooter(Entity, String)`：先按本体判定，本体不是玩家时再看**乘员**；
    手持枪走这里等于零额外开销（第一句就命中、乘员表为空）。
  - `SuperbWarfareCompat#onShootParams`：优先取 `shooter` / `ammoSupplier` 里**活体那一个**（载具时即炮手），
    拿不到才退回载具；`hasInfiniteBackupAmmo` 的注入改用同一个判定。
  - 新增 `GunData.shakePlayers(Entity)` 的 HEAD 取消：解锁无后坐力时载具开火不再震屏 ——
    与手持枪口径一致，**炮口后坐状态 `CANNON_RECOIL_FORCE` / `CANNON_RECOIL_TIME`（枪模与 HUD 动画用）
    照常保留**，只是玩家镜头不再震。
- **口径说明（多人同车）**：载具开火的震屏由整台载具统一派发（`ShakeClientMessage` 里没有逐个玩家的身份），
  且同一个包也被爆炸（`CustomExplosion` / `ParticleTool`）复用、客户端区分不出来源 ——
  所以只在载具开火这一侧拦，口径为「该载具的乘员里有解锁者」即生效；爆炸等其它来源的震屏不受影响。
- 载具上**保留不动**的：炮口后坐状态、准星 / HUD 抖动（`VehicleCrosshairOverlay` / `LandVehicleHud` 读
  `recoilShake`）、载具模型的后坐动画 —— 都不是玩家镜头，属于该模组的手感。
- 属性描述与中英文案同步为「手持 … 或操作卓越前线的载具武器时」；README 新增「载具武器」小节。
- 协议版本不变。

## [1.44.3] - 2026-10-08

### 🎯 修正：卓越前线「无后坐力」不再残留抖动（之前开了镜头还会抖）

- **现象**：开了无后坐力，卓越前线的枪开火 / 瞄准时镜头仍然抖。
- **根因（两处，都用 `javap` 反汇编核过）**：
  1. 早先的实现**整段取消** `ClientEventHandler.handleGunRecoil()`，而它同时承担两件事：
     把偏移施加到玩家视角（`LocalPlayer.setYRot` / `setXRot` 各一处）**以及**衰减后坐力状态量
     （`recoilHorizon` / `recoilY`，全类只有它写这两个量）。整段取消 → 视角确实不动了，
     但状态量不再衰减，而枪模渲染器（`Aa12ItemModel` / `SentinelItemRenderer`）与开火动画都在**读**它们。
  2. 「镜头一直抖」的另一半来自 `handleWeaponBreathSway()`：它每帧用 `RandomSource` 往 pitch / yaw 上加
     **随机**偏移（屏息、架脚架才减弱）—— 这条属性原先完全没有碰它。
- **改法**：统一成「**只吞掉对 `LocalPlayer` 朝向的写入，其余逻辑照常跑**」：
  1. `handleGunRecoil`：`@Redirect` 掉 `LocalPlayer.m_146922_`（左右偏）/ `m_146926_`（往上顶）两处写入，
     方法本身照常执行（后坐力状态该衰减照常衰减）；
  2. `handleWeaponBreathSway`：同样 `@Redirect` 掉那两处写入；
  3. 开火时的相机偏移：新增 `SuperbWarfareCompat#zeroFireCameraOffset()`，由 `handlePlayerCamera` 的 HEAD
     注入每帧把 `cameraRot[2]` 清零 —— `handleWeaponFire` 每帧 lerp 它、`handlePlayerCamera` 再把它加到
     相机角度上；放在「读」这一侧是为了不依赖同一帧内两者的先后顺序。该字段属于**纯客户端类**，
     因此单独懒解析、失败只让这一半失效（反射写对方的 `public static double[]`）。
- **保留不动的**（它们不是后坐力，属于该模组的手感 / 其它机制）：枪模自身的开火动画
  （`firePosZ` / `fireRotTimer`）、枪身随视角转动的跟随（`turnRot`，枪模动画也在读它）、
  准星散布（`fireSpread`）、换弹抖动（`handleReloadShake`）。
- 属性描述与中英文案、README 同步为「卓越前线的瞄准呼吸晃动也一并消除」。
- 协议版本不变。

## [1.44.2] - 2026-10-08

### 🔫 卓越前线：补弹收口挪到全枪型都必经的地方（特殊枪一起覆盖）

- **问题**：1.44.1 把补弹注入点挂在 `GunItem.shoot(ShootParameters)` / `afterShoot(ShootParameters)` 上，
  但用 `javap` 逐个核对后发现有几把枪**自己覆写了这两个方法、且覆写里不回 `super`** ——
  `Igla` / `Javelin` / `Bocek` 覆写 `shoot`，`Ql1031` / `Sentinel` / `Taser` 覆写 `afterShoot`
  （基类注入对它们不生效，得靠每 5 tick 的兜底，弹匣会按射速抖一下）。
- **改法**：主收口挪到 **`GunData.shoot(...)` 各入口的 RETURN**（`hasInfiniteBackupAmmo` 也在这个类上，
  同一个 Mixin）。选它的依据是反汇编结果：
  - 这几个重载都是 `public final` 的**薄包装**，内部直接 `item.shoot(this, ...)` ——
    方向是 `GunData.shoot → GunItem.shoot`，`GunItem` 那边调 `data.shoot(...)` 的路径**一条都没有**；
  - 它们的**外部**调用者只有开火网络包 `ShootMessage`（玩家）与 `GunShootGoal`（AI 用枪），
    载具那条走 `shoot(ShootParameters)` 重载（`VehicleEntity` / `Tom6Entity`）；
  - 因此返回时整条开火链路已经跑完，**不挑枪型**，上面 6 把特殊枪一并覆盖。
- **三层收口**（全部幂等、可叠加）：`GunData.shoot(...)`（全枪型）→ `GunItem.shoot/afterShoot(params)`
  （兜直接调 item 的路径）→ 每 5 tick 扫手持两格（兜「捡来的 / 解锁前就已半匣」的枪）。
- 顺带：主收口那条拿得到射手实参，直接 `SuperbWarfareCompat.onGunFired(shooter, this)`，
  不再为取 `shooter` / `data` 走一次反射；只有 `ShootParameters` 重载与 AI 路径仍按字段反射取值。
  射手不是玩家（AI 怪物用枪）时两条属性都不生效 —— 它们是玩家属性。
- 存根 `compatApiStub` 的 `GunData` 补上被注入的那几个 `shoot` 重载声明；README 同步。
- 协议版本不变。

## [1.44.1] - 2026-10-08

### 🔫 修正：卓越前线的枪械现在也是「弹匣恒满、不用换弹」（之前还得换弹）

- **现象**：1.44.0 对 Superb Warfare 只接管了 `GunData.hasInfiniteBackupAmmo(Entity)`
  （备弹无限），于是出现「能一直打，但弹匣照常掉空、游戏仍要求你换弹」。
- **补齐弹匣侧**：每次开火后把弹匣写回容量上限，外加每 5 tick 兜底（捡来的 / 属性解锁前就已经半匣的枪）。
  现在两边的枪械行为一致：**弹匣恒满、不用换弹**。
- **容量怎么来的**：`GunData.get(GunProp.MAGAZINE)` —— 模组自己的口径（扩容弹匣、Perk 修正都已算进去，
  它的 `reloadAmmo` 用的也是同一个属性）。已用 `javap` 核对：`GunProp.MAGAZINE` 是 public static 属性对象，
  `GunData.ammo` 是 `IntValue`（`get()` / `set(int)`），弹匣本身在 `GunData` 里只被
  `reloadAmmo` / `withdrawAmmo` / `changeAmmoConsumer` 写过。
- **为什么不调用它的 `GunData.reloadAmmo(...)` 来补弹**：后者是**换弹收尾**函数，除了填弹匣还会顺手改换弹状态机
  （`reload.setState(NOT_RELOADING)`、`bolt.needed`、`fireIndex.reset()`、`nbtVersion.invalidateStructural()`）——
  拿它当补弹工具会打乱换弹动画 / 拉栓状态。直接写 `ammo` 既准确又无副作用。
- **注入点**：`GunItem.shoot(ShootParameters)` 与 `GunItem.afterShoot(ShootParameters)` 的 RETURN 各挂一个
  （扣弹在主流程还是收尾都不影响结果，也不需要猜先后顺序）；两个注入点与每 5 tick 兜底都调
  `SuperbWarfareCompat#onGunFired` / `#onTick`，两条属性各自独立判定。
- **容错**：弹匣相关句柄（`GUN_PROP_CLASS` / `MAGAZINE` / `ammo`）**单独解析**并各自 try/catch ——
  以后某个版本若把容量属性收成私有，只让「补满弹匣」这一半失效（退化成 1.44.0 那种「备弹无限」），
  不会把零热量、无后坐力一起拖下水。
- 属性描述、中英文案与 README「无限子弹」小节同步改写。
- 协议版本不变。

## [1.44.0] - 2026-10-08

### 🔫 三条枪械属性（无限子弹 / 零热量 / 无后坐力）现在同时支持 **Superb Warfare（卓越前线）**

- 属性还是原来那三条（`infinite_ammo` / `no_heat` / `no_recoil`），没有新增属性、点数也不变；
  **装上 Superb Warfare 后它们对卓越前线的枪械同样生效**（没装则该模组的注入点根本不应用）。
  描述文案改为「TACZ / 卓越前线」。
- **落点全部用 `javap` 对着整合包里的 `superbwarfare-0.8.9.2-mc1.20.1` 逐条核对过**
  （该模组是 Kotlin + Parchment 映射，成员名与结构跟 TACZ 完全不同）：
  - **无限子弹 → `GunData.hasInfiniteBackupAmmo(Entity)`**：这是它<b>自带</b>的无限弹药判定
    （创造模式、创造弹药盒、能量 / 经验 / 饥饿等弹药策略、以及「附加来源」的检查最终都会问到它），
    签名带实体上下文、可以按玩家判定。置为 true 等价于「此人此刻处于创造模式持枪」：
    打完自动补弹、不消耗任何真实弹药，**所有弹药类型一并覆盖**。
    与 TACZ 那条的差别已写进 README：TACZ 是「弹匣恒满」，这里是「自动补弹、不耗备弹」——
    各自使用对方模组最自然的语义，本模组不去硬改它的弹匣计数（那会与它的换弹 / 退弹 / 配件重算逻辑打架）。
  - **零热量 → `GunItem.shoot(ShootParameters)` 的 RETURN**：查字节码确认全类只有这个方法读
    `GunProp.HEAT_PER_SHOOT`（唯一的加热点，也是判定过热的地方），在返回处把枪械 NBT 里的
    `heat` / `overHeat` 清零，同一 tick 内归零、HUD 看不到升温。
  - **无后坐力 → `ClientEventHandler.handleGunRecoil()`**：逐个方法扫过这个客户端处理器的字节码，
    全类只有三处会改玩家 XRot（呼吸晃动 `handleWeaponBreathSway`、辅助方法 `look`、以及它），
    因此只取消它，呼吸晃动等其它视角效果照常保留。
- **共用一份开关判定**：新增 `compat/CompatToggles`（原本散在 `TaczCompat` 里的三个
  `isXxxActive` 搬进来，两个模组的注入点共用），仍是「一次 Capability 查询 + 走缓存」，
  可以被开火路径甚至每帧调用而不产生额外开销。
- **新增 `compat/SuperbWarfareCompat`**（反射，和 `TaczCompat` / `ApotheosisEnchantCompat` 同一套思路）：
  负责「零热量」的兜底清零 —— 把「属性解锁之前 / 捡来时就已经热了、甚至已被过热锁死」的枪拉回 0。
  反射链：`GunData.DATA_CACHE`（`LoadingCache<ItemStack, GunData>`）→ `ShootParameters` 的
  `data` / `shooter` 字段 → `GunData.heat`（`DoubleValue`）/ `overHeat`（`BooleanValue`）的读写。
  没装 Superb Warfare 时 `Class.forName` 失败即永久短路、零开销。
- **新增 3 个 Mixin**：`SbwGunDataMixin`、`SbwGunItemMixin`（双端）、`SbwClientEventHandlerMixin`（客户端）。
- **注入点门控扩成一张表**：`InfiniteStatsMixinPlugin` 从「只认 `com.tacz.`」改成
  「目标包前缀 → mod id」映射（`com.tacz.` → `tacz`，`com.atsuishio.superbwarfare.` → `superbwarfare`），
  对应模组不在场时那两个 / 三个 Mixin 一律不应用；注入点本身仍是 `require = 0`。
- **存根源集改名并扩容**：`src/taczApiStub` → **`src/compatApiStub`**（源集 `taczApiStub` → `compatApiStub`，
  `build.gradle` 与 `compileJava` 的 dependsOn 同步改名），新增 4 个 Superb Warfare 空壳类
  （`GunData` / `ShootParameters` / `GunItem` / `ClientEventHandler`）。
  存根**不进 jar、运行时也不加载**，只为让 Mixin 注解处理器在编译期能解析目标类、并校验注入点名字。
- `UtilityHandler` 的每 5 tick 兜底入口现在同时驱动 TACZ 与 Superb Warfare 两家（各自独立判定与异常隔离）。
- 协议版本不变（未新增 / 修改任何网络包）。

## [1.43.0] - 2026-10-07

### 🎯 TACZ 联动新增属性「无后坐力」（`no_recoil`）

- **新属性**：`no_recoil` 无后坐力（攻击类，**开关型，投入 3 点解锁**；内置属性 99 → **100**）。
  解锁后开火时**视角不再被后坐力往上顶**，弹着点由玩家自己控制。枪械**散布（精准度）不受影响** ——
  这条只管后坐力那一下抬手。
- **落点确认**：TACZ 的后坐力全在客户端的 `CameraSetupEvent`（已对着
  `tacz-1.20.1-1.1.8-hotfix2.jar` 逐字节核对 `initialCameraRecoil` / `applyCameraRecoil`）：
  `initialCameraRecoil(GunFireEvent)` 在开火时按枪械数据 + 配件 / 瞄准 / 趴下修正算出 pitch / yaw
  两条后坐力曲线并记下时间戳；`applyCameraRecoil(ComputeCameraAngles)` 之后**每一帧**求值曲线，把增量
  直接减到 `player.setXRot / setYRot` 上 —— 注意它改的是**玩家真实朝向**（客户端会把朝向发给服务端），
  不是只动渲染相机，所以「无后坐力」必须让这两个方法都不执行，而不是事后把角度掰回来。
- **新增客户端 Mixin `TaczCameraRecoilMixin`**：在 `initialCameraRecoil` 与 `applyCameraRecoil`
  的 HEAD 处按开关取消（两个都要拦，只拦一个会残留上一次曲线的状态）。
  该 Mixin 放在 mixin 配置的 **`client` 列表**里 —— 目标类是纯客户端类，服务端不会加载它。
  之所以不走事件：这段逻辑本身就是 Forge 事件处理器，事件层排在它后面改不了它的副作用，
  只会和其它改视角的模组互相打架。
- **新增编译期存根** `com.tacz.guns.client.event.CameraSetupEvent`（`src/taczApiStub`）：
  两个方法名照抄真实实现，参数统一写 `Object` —— 注入处理器按方法名定位即可，不必把 TACZ 的
  `GunFireEvent`、Forge 的 `ViewportEvent.ComputeCameraAngles` 也搬进存根。
- **判定入口**：`TaczCompat#isNoRecoilActive` 与另外两条 TACZ 属性共用同一个「一次 Capability 查询 +
  走缓存」的实现 —— 它会被**每帧**调用（后坐力求值在渲染帧里），所以刻意不加任何额外开销；
  拿不到本地玩家（主菜单等）时按「未解锁」处理。
- 文档与元数据同步：`README`（属性计数、攻击类清单、新增「无后坐力」小节）、`mods.toml` 描述。
- 协议版本不变（未新增 / 修改任何网络包）。

## [1.42.0] - 2026-10-07

### ❄️ TACZ 联动新增属性「零热量」（`no_heat`）

- **新属性**：`no_heat` 零热量（攻击类，**开关型，投入 3 点解锁**；内置属性 98 → **99**）。
  解锁后手持带热量数据的 TACZ 枪械（机枪那类）**热量恒为 0** —— 不升温、不会进「过热锁」
  （锁上就打不出子弹），HUD 热量条一直为空，顺便也消掉了热量带来的 RPM / 精度惩罚。
- **覆盖 TACZ 的三条加热路径**（只做其中一条都会漏）：
  1. **击发加热**：注入 `ModernKineticGunScriptAPI#handleShootHeat`（默认实现里加 heat、满值顺手
     `setOverheatLocked(true)` 的地方）→ HEAD 取消；
  2. **脚本加热**：注入同一类上的 `setHeatAmount`（枪械 Lua 脚本写热量的包装方法）→ HEAD 取消。
     刻意只拦**这一个**包装方法：TACZ 自己的散热走的是枪械物品上的同名方法
     （`ModernKineticGunItem#tickHeat`），不受影响；
  3. **兜底清零**：`compat/TaczCompat` 每 5 tick 把**手持**枪械的 `HeatAmount` 清零、`OverHeated` 解锁 ——
     管的是「本来就热了 / 已经被锁了」的枪（属性刚解锁、捡来的枪）。只在真的非零或已锁时才写 NBT，
     避免把物品反复标脏同步。
- **两条 TACZ 属性共用一个每 5 tick 的入口**（`TaczCompat#onTick`），各自独立判定；
  两条都没解锁时在第一步返回，连背包扫描都不做。判定开关的 `isNoHeatActive` 与 `isInfiniteAmmoActive`
  共用同一个私有实现，仍是纯 Capability 查询。
- **编译期存根同步更新**：`src/taczApiStub` 里的 `ModernKineticGunScriptAPI` 补上 `handleShootHeat()` /
  `setHeatAmount(float)` 两个空壳方法（注解处理器据此校验注入点名字）。所有要注入 / 反射的成员都已对着
  整合包内的 `tacz-1.20.1-1.1.8-hotfix2.jar` 逐字节核对（`handleShootHeat`、`setHeatAmount`、
  `getHeatAmount`、`hasHeatData`、`isOverheatLocked`、`setOverheatLocked` 均在）。
- 文档与元数据同步：`README`（属性计数、攻击类清单、新增「零热量」小节）、`mods.toml` 描述。
- 协议版本不变（未新增 / 修改任何网络包）。

## [1.41.0] - 2026-10-07

### 🔫 「无限子弹」改成**弹匣永远满**（1.40.0 的「虚拟备弹」方案作废）

- **用户要的效果**：不是「备弹无限、弹匣照常打空再换弹」，而是**弹匣里永远是满的** —— 开了枪子弹不掉、
  也永远不用换弹。1.40.0 写的是前者（`DummyAmmo` 虚拟备弹），本次改成后者。
- **新的实现（两半，缺一不可）**：
  1. **不再扣弹 —— Mixin 注入 TACZ 自己的扣弹落点**：`ModernKineticGunScriptAPI#reduceAmmoOnce`
     （每次击发都走它）改成「先把弹匣补满，再当作扣弹成功返回」，`#removeAmmoFromMagazine`
     （拉栓供弹、换弹收尾「推一发进枪膛」都走它）改为空操作。
     **为什么必须 Mixin**：扣弹与生成子弹在同一次调用内完成，事件层面只能「取消整发」（那是打不出子弹），
     而每 tick 事后补回来也赶不上同一 tick 的读数 —— HUD 会按射速在满匣与满匣 -1 之间闪。
  2. **补满已有的弹匣 —— `compat/TaczCompat` 每 5 tick 补一次**：Mixin 只保证「不少」，
     保证不了「本来就半匣的枪也是满的」（捡到 / 换到半匣的枪、别的模组改过数值之后靠这一步拉回满匣）。
     弹匣上限连同扩容弹匣等配件的影响一律问 TACZ 自己
     （反射 `ModernKineticGunScriptAPI#getMaxAmmoCount`，只需 `setItemStack` 即可读，不需要射手上下文）。
- **顺带堵掉刷弹药漏洞**：无限子弹生效时取消 `AbstractGunItem#dropAllAmmo` ——
  否则「更换弹匣类配件会把弹匣卸成真弹药物品塞进背包，而弹匣又立刻被补满」可以无限刷弹药。
- **不再往枪械 NBT 里写任何本模组数据**：1.40.0 需要写 `DummyAmmo` + 自己的标记（关闭时还要回收），
  现在只剩「TACZ 自己的弹匣计数」在被读写，关闭属性立即回到原版行为、没有残留。
  判定开关的入口 `TaczCompat#isInfiniteAmmoActive` 是纯 Capability 查询（无反射），
  即使 TACZ 版本变动让反射链失效，「不扣弹」这半边依然有效。
- **新增 Mixin 配置插件 `InfiniteStatsMixinPlugin`（必须）**：本模组的 `infinitestats.mixins.json` 是
  `"required": true`，而新加的两个 Mixin 目标类是 `com.tacz.*` —— 没装 TACZ 时目标类不存在，
  Mixin 会抛 `ClassMetadataNotFoundException` 并**直接让游戏起不来**。插件按**目标类名前缀**
  （`com.tacz.`）判定，没装 TACZ 时这两个 Mixin 根本不应用；同时把 `getRefMapperConfig()` 固定为
  `infinitestats.refmap.json`（与 json 里的值一致，不依赖「插件与配置谁优先」的细节）。
  注入点本身依旧是 `require = 0`：TACZ 版本变化导致锚点对不上时只打印警告、退化为原版行为。
- **新增编译期存根 `src/taczApiStub/java`（必须）**：Mixin 注解处理器要求 `@Mixin` 的目标类在
  **编译期**类路径上，缺了直接报 `Mixin target ... could not be found` 让构建失败。
  不把对方几十 MB 的 jar 塞进仓库（会随它升级变味、也违背「无需任何前置」），改为在仓库里放两个
  **空壳目标类**（`com.tacz.guns.item.ModernKineticGunScriptAPI`、`com.tacz.guns.api.item.gun.AbstractGunItem`），
  只列出被注入 / 被反射用到的成员签名。`build.gradle` 里新增源集 `taczApiStub`，
  把它的产物**只并进主源集的编译类路径**（`main.compileClasspath += sourceSets.taczApiStub.output`；
  存根源集自身取 `configurations.compileClasspath`，避免「任务依赖自己产物」的循环依赖）。
  已验证：**存根类不会被写进 jar**（jar 只打包 `sourceSets.main.output`），refmap 内容与原版注入无关、
  未受影响（9 个 Mixin 的 SRG 映射照常生成）。注解处理器的目标校验器因此保持开启 ——
  注入点写错名字会在编译期就报出来，而不是拖到运行时。
- **不用 `@Shadow`，射手改用运行时反射**：影子成员解析失败是 Mixin **应用期硬错误**（`require = 0` 兜不住），
  TACZ 一旦改名就变成启动崩溃。`compat/TaczCompat#shooterOf(Object)` 反射取 `getShooter()`，
  解析不到/调用失败一律当作「未解锁」，TACZ 换版本最多让这条联动失效，不会把游戏带崩。
- **验证**：对整合包内的 `tacz-1.20.1-1.1.8-hotfix2.jar` 逐字节核对了要注入 / 反射的成员
  （`reduceAmmoOnce`、`removeAmmoFromMagazine`、`getNeededAmmoAmount`、`putAmmoInMagazine`、
  `getMaxAmmoCount`、`setItemStack`、`getShooter`、`IGun#get/setCurrentAmmoCount`、`useInventoryAmmo`、
  `AbstractGunItem#dropAllAmmo` 均在）。协议版本不变。

## [1.40.0] - 2026-10-07

> ⚠️ 本版「虚拟备弹」方案已在 **1.41.0** 被「弹匣永远满」取代，下方记录仅作历史留档。

### 🔫 TACZ（永恒枪械工坊：零）联动：新属性「无限子弹」（`infinite_ammo`）

- **新属性**：`infinite_ammo` 无限子弹（攻击类，**开关型，投入 5 点解锁**；内置属性 97 → **98**）。
  解锁后手持 TACZ 枪械，射击与换弹改用枪械的**虚拟备弹**供弹，**不再消耗背包里的子弹** ——
  空背包也能一路打下去。弹匣式（换弹补弹）与「背包直读」式枪械都覆盖，栓动 / 闭膛 / 开膛同样适用。
- **为什么走「虚拟备弹」而不是拦事件**：TACZ 的供弹只有一条入口 `ModernKineticGunScriptAPI#consumeAmmoFromPlayer`，
  它按「背包直读+不检查弹药 → 虚拟备弹 → 扣背包弹药」的优先级分流；而 `useDummyAmmo(gun)` 的判定就是
  「枪的 NBT 里有没有 `DummyAmmo` 这个 int 键」，`canReload`（换弹前检查）与 `hasInventoryAmmo`（背包直读枪的供弹检查）
  也都会在存在虚拟备弹时改走这条路。也就是说**只要枪上带着虚拟备弹，射击与换弹就都不再碰背包**，
  不需要挂 `GunShootEvent` / `GunReloadEvent`、也不需要 Mixin。
  实现上是每 5 tick 把虚拟备弹顶到 9999，换弹扣掉的那一小段下一个 tick 就补回来。
- **只碰自己写进去的那一份**：
  - 只补**手持的两格**（主手 + 副手），背包里躺着的枪不动；
  - 枪上**本来就有的**虚拟备弹（创造模式枪械物品、弹药盒等 TACZ 自带机制）原样放过，不接管也不回收；
  - 关掉属性后**回收本模组写进去的那份**（靠物品 NBT 标记 `InfinitestatsInfiniteAmmo` 区分），不会抹掉玩家原有的。
- **零前置、零开销**：新增 `compat/TaczCompat`（反射，和 `ApotheosisEnchantCompat` 同一套思路）——
  本模组**不声明**对 TACZ 的编译期依赖；没装 TACZ 时 `Class.forName` 直接失败并**永久短路**，
  连背包扫描都不会执行。写入优先走 TACZ 官方 API（`IGun#setDummyAmmoAmount`），
  反射失效时退化为直写 NBT（键名与 `GunItemDataAccessor.GUN_DUMMY_AMMO` 一致）。
- 文档与元数据同步：`README`（属性计数、攻击类清单、新增「无限子弹」小节）、`mods.toml` 描述。
- 协议版本不变（未新增 / 修改任何网络包）。

## [1.39.3] - 2026-10-07

### 📦 构建产物整理：三个包 → **两个包**（一内嵌、一不内嵌），逻辑代码零改动

- **原先为什么是三个**：`jar` 任务会把 jarJar 的 `META-INF/jarjar/**`（内嵌 `pinyin_search`）
  **并进主产物**，于是主产物与 jarJar 自己那个 `-all` **逐字节相同**；再加上过滤掉内嵌内容重打的
  `-noembed` 变体，一次构建输出三个包，分发时只能靠肉眼分辨。
- **现在改成两个**（各自对应一种内容，名字也能看出区别）：
  - `infinite_stats-无限加点-1.20.1-<版本>.jar` —— **不内嵌**，标准产物名；
  - `infinite_stats-无限加点-1.20.1-<版本>-all.jar` —— **内嵌 `pinyin_search`**（JAR-in-JAR）。
  - `-noembed` 变体随之取消（标准名那份本身就是不内嵌的，不必再派生）。
- **取舍变了，选包时注意**：以前推荐的是「含内嵌」的标准名那份，现在**标准名那份不含内嵌** ——
  整合包 / 玩家没自带 `pinyin_search` 时请装 **`-all`** 那份（或另外把 `pinyin_search` 装进 `mods\`）；
  没装也不影响启动与其它功能，只是搜索框退化成「纯原文包含」匹配。
- **`build.bat`**：`dist\` 照旧复制两个包；`deploy` 会看目标 `mods\` 里有没有 `pinyin_search`，
  有就装标准名那份、没有就装 `-all` 那份（并在输出里标明装的是哪一份）。
- 本次**没有改动任何模组逻辑**（Java、语言文件、mixin 配置均未变），协议版本不变。

## [1.39.2] - 2026-10-07

### 🧪 统一「未解锁时的附魔上限」：跟随整合包实际生效的上限（Apotheosis 等）

- **问题**：1.39.1 把铁砧那条路对齐了 Apotheosis 的上限，但本模组另外两处（**进阶高级附魔台**、
  随身铁砧的判断口径）读的仍是写死的原版 `Enchantment#getMaxLevel()`。于是同包内自相矛盾：
  本整合包里 Apotheosis 把锋利配到 **9** 级，铁砧能合到 9，**我们自己的附魔台却只让选到 5**
  （服务端 `PortableInfuser` 的校验也按 5 卡，选了 9 会被判 `bad_selection`）。
- **起因**：Apotheosis 的 coremod 只把原版**几个类里**的 `Enchantment#getMaxLevel()` 调用换成它自己的
  `EnchHooks.getMaxLevel`，`Enchantment#getMaxLevel()` 这个方法本身没被改，所以「包里的真实上限」
  只能从它那里读。
- **修复**：新增 `compat/ApotheosisEnchantCompat`（反射读 `EnchHooks#getMaxLevel`，**不**在字节码里引用
  它的类，没装 Apotheosis 时返回 -1），`util/EnchantLimits` 增加 `packCap(ench)`：
  `maxLevel(player, ench) = 解锁 ? int 上限 : packCap(ench)`，`packCap` = Apotheosis 的值（有且 > 0）
  否则原版值。三处口径（进阶附魔台 / 随身铁砧 / 铁砧的 Apotheosis 兼容注入）现在完全一致：
  **未解锁 = 整合包上限（本包锋利 9）**，解锁后 = 不限。
- **顺带**：
  - 进阶附魔台按住 Shift 的「拉满」在未解锁时跳到**整合包满级**（9），解锁后仍只跳到原版满级
    （int 上限跳过去既付不起也没意义），与界面提示文案一致；
  - 行内悬停提示的「最高 N 级」改为显示实际生效的上限（不再固定显示原版数字）；
  - `AnvilMenuMixin` 里那段本地反射搬进 `ApotheosisEnchantCompat` 复用，注入点本身不变。
- 新增 gameTest `enchantCapFollowsUnlockState`（未解锁 = 整合包上限、解锁 = 不限、功能开关关掉后回落）。
  协议版本不变。

## [1.39.1] - 2026-10-07

### 💥 修「装了 Apotheosis 的包启动即崩」（Critical injection failure）

- **现象**：游戏在加载阶段直接崩掉，进不了主菜单，日志末尾是
  `InjectionError: Critical injection failure: Redirector infinitestats$raiseMergeCap(...) in infinitestats.mixins.json:AnvilMenuMixin from mod infinitestats failed injection check, (0/1) succeeded`。
- **原因**：Apotheosis 的 coremod（`coremods/ench/ench_info_redirector.js`）会把
  `net.minecraft.world.inventory.AnvilMenu` 里那两处 `Enchantment#getMaxLevel()` **整段替换**成
  它自己的静态钩子 `dev.shadowsoffire.apotheosis.ench.asm.EnchHooks.getMaxLevel`（日志里就是那行
  `Replaced 2 calls to Enchantment#getMaxLevel() in ... AnvilMenu`）。coremod 跑在 Mixin 之前，
  于是我们那个针对 `Enchantment#getMaxLevel()` 的 `@Redirect` 在字节码里已经**一个目标都找不到**，
  而 Mixin 默认要求「至少命中 1 处」，直接把整个模组判为加载失败 —— 1.38.0 起带上铁砧注入后，
  凡装了 Apotheosis 的包都起不来。1.38.0 里「改用 `@Redirect` 就能和 Apotheosis 共存」的判断只对
  它的 **Mixin**（`apoth_removeLevelCap` 改常量 40）成立，漏算了它的 **coremod**。
- **修复**：
  1. **兼容注入点**：新增一个针对 `EnchHooks.getMaxLevel(Enchantment)` 的 `@Redirect`
     （`ordinal = 0`，同样是「比较」那一处）。未解锁时返回 **Apotheosis 自己的上限**（不是原版的
     `getMaxLevel()`）—— 大包里 Apotheosis 的等级上限普遍被配置得比原版高，返回原版值等于把人家
     放宽的上限又收紧了；解锁后才返回不限。`EnchHooks` 用**反射**调用，不在字节码里直接引用它的类，
     免得没装 Apotheosis 时给 AnvilMenu 的常量池添一条指向不存在类的引用。
  2. **静态调用点拿不到铁砧实例**：Apotheosis 换上去的是静态方法，重定向处理器也必须是静态的，
     读不到玩家 → 玩家上下文改为在 `createResult` 的 HEAD 捕获到 ThreadLocal、RETURN 释放
     （两条注入同样都是可选注入）。
  3. **所有注入点改为可选**：`AnvilMenuMixin` 的三处注入全部标 `require = 0`，
     并把 `infinitestats.mixins.json` 的 `injectors.defaultRequire` 由 1 改为 **0**。
     AnvilMenu / EnchantmentHelper 这类「大包里的公共战场」被别的模组挪走锚点是常态，
     注入失败现在只打印一条警告、对应功能退化为原版行为，**绝不再让整个游戏起不来**。
     代价是注入失效变成静默降级（日志里仍有 warn），换来的是任何包都能进游戏。
- 本次没有新增属性、没有新增网络包，协议版本不变。

## [1.39.0] - 2026-10-07

### ⚡ 新属性「额外打击」（`extra_strike`，攻击类，97 条内置属性）

每次造成伤害后追加**额外打击**：等级＝额外打击次数（最多 8 次），每次造成**本次伤害 20%** 的额外属性伤害，
比例由配置 `ExtraStrike.extraStrikeDamageRatio` 决定（默认 0.2，满 8 级合计 **+160%**）。

- **覆盖「玩家造成的所有伤害」**：近战、弓箭 / 三叉戟 / 模组弹射物、以玩家为来源的法术，
  以及**玩家的召唤物 / 宠物 / 坐骑**打出的伤害。后者的伤害来源实体是召唤物自己，本模组新增
  `getPlayerDamageOwner(DamageSource)`：在原有 `getPlayerAttacker`（玩家本体 + 玩家发射的弹射物）之外，
  再接受「`OwnableEntity` 且主人是玩家」的实体。**只有这条属性用这个宽口径**，
  暴击 / 吸血 / 处决等依旧只认玩家本体与其弹射物，不动既有平衡。
- **独立结算、无视护甲与减伤**：额外打击不叠进本次伤害，而是在伤害结算完成后再单独扣血，
  因此不会被目标护甲、抗性提升、保护附魔或其它模组的「单次伤害上限」削掉。
- **挂点选在 `LivingDamageEvent`**：这是护甲与减伤都已结算的时机，取到的是**实际伤害**，
  按它计算比例才不会出现「打高护甲目标时额外打击虚高」。
- **不嵌套 `hurt()`**：此刻正处在目标本次受伤的 `actuallyHurt` 内部，再嵌套一次 `hurt()` 会让
  **同一次受击走两遍死亡收尾**（掉落物 / 成就 / 击杀进度重复触发）。因此改用 `setHealth` 直接扣血
  （与本模组「真实伤害」同一套安全结算），死亡依旧由外层本次受伤的原版收尾负责 ——
  **击杀归属、掉落、击杀类任务照常算在玩家头上**。
- **不触发的情形**：自伤（自己炸自己、箭落回自己身上）；本模组自己的次级直接伤害
  （范围攻击 / 真实伤害 / 额外打击本身），否则会层层叠加；被格挡 / 闪避 / 免疫取消的攻击
  （走取消后的结算，不会出现「打空也掉血」）；以及被「功能开关」关闭时（数值按 0 计）。
- **其它**：新增配置项 `ExtraStrike.extraStrikeDamageRatio`（0 ~ 10，默认 0.2）；
  属性面板攻击分类新增图标与中英文文案；新增 gameTest `extraStrikeDealsBonusAttributeDamage`
  （校验追加总量、8 次上限、自伤不触发、非玩家来源不触发、功能开关关闭后失效）。
  协议版本不变（本次没有新增网络包）。

## [1.38.0] - 2026-10-07

### 🧪 新属性「附魔上限突破」（`enchant_limit`，1 点解锁）

开启后附魔等级不再受原版 `getMaxLevel()` 约束（上限放开到 int 上限，实际高度由价格决定）：
**进阶高级附魔台**可以自选到任意等级、**铁砧（含随身铁砧）**能把同等级附魔继续往上叠
（锋利 V + 锋利 V = 锋利 VI…），另配了 OP 指令 `/infstats enchant <附魔> <等级>`（权限等级 2，
刻意不要求解锁，相当于参考实现的 `/cenchant`，方便发物品与调试）。

**为什么不是「改一下上限判断」那么简单** —— 三处都得动（都对着 1.20.1 源码 / 字节码核过）：

1. **上限判断**：铁砧的等级上限只卡在 `createResult` 里的
   `if (j2 > enchantment1.getMaxLevel()) j2 = enchantment1.getMaxLevel();`（`javap` 核过：
   全类仅两处 `getMaxLevel` 调用，就是这一行的比较与赋值）。用 `@Redirect` **只替换比较里的取数**：
   解锁的玩家返回 int 上限 → 比较恒为 false → 等级原样保留，紧随其后的消耗计算也用真实等级
   （越突破越贵）；未解锁时原样返回 `getMaxLevel()`，与原版逐字节一致。
   只碰比较、不动赋值那一处，是为了不与其它铁砧模组（Apotheosis 的 `apoth_removeLevelCap` 等）
   抢同一处字节码 —— 我们原有的消耗注入也是同样的思路。
2. **NBT 根本存不下**：原版 `storeEnchantment` / `setEnchantmentLevel` 写的是
   `putShort("lvl", (short) level)`（超过 32767 直接截断），而 `getEnchantmentLevel` 读回来还要
   `Mth.clamp(..., 0, 255)` —— 也就是说物品上就算写着 1000 级，读回来也是 **255**。
   这三处由新增的 `EnchantmentHelperMixin` 接管：**能塞进 short 的照旧写 short**
   （不突破的存档 NBT 与其它模组看到的完全一样），超出才写 int；读取不再夹到 255。
3. **名字会露馅**：原版语言文件只提供 `enchantment.level.1` ~ `.10`，11 级以上的附魔名
   会原样显示成 `enchantment.level.11` 这种键名（物品提示、铁砧结果、附魔界面到处都会出现）。
   新增 `EnchantmentMixin#getFullname` 接管：1~10 仍走原版译文（跟随客户端语言），
   11~100 用罗马数字，再往上用阿拉伯数字。

**顺带**：

- 进阶附魔台的等级上限、价格换算、界面显示全部改为按玩家上限计算，金额一律用 long 相乘再饱和
  （突破后的等级是天文数字，int 溢出会变成「倒贴钱」或符号翻转）；按住 Shift 点击仍是
  「清零 / 回到原版满级」，刻意不跳到 int 上限。
- 物品编辑器与附魔条目校验改用同一套 int 读写（原来读 `getShort("lvl")`、写 `putShort`，
  会把超上限等级截断）。
- 协议版本不变（本次没有新增网络包）。

## [1.37.0] - 2026-10-07

### ✨ 随身附魔台 → 随身「进阶高级附魔台」（自己挑附魔，不再随机）

参考 Enchanting Infuser 的**进阶档**，把原来的随身附魔台整块换掉：**不再随机三档，而是自己挑附魔**。

- **玩法**：左边放物品（或书），右侧列出它**能附的所有魔**，逐条 `0 ~ 满级` 用 `<` / `>` 调（Shift
  直接清零 / 拉满），清单可滚轮滚动；**物品身上已有的附魔也在清单里**，升 / 降 / 清就是升级、降级、移除。
- **付款方式可切换**：界面右上角一键切换 **经验等级 ⇄ 可用属性点数**，两种都**不消耗青金石**。
  创造模式免经验（与原版附魔台一致），属性点数照扣。
- **定价**（`crafting/PortableInfuser`）：稀有度基础价（常见 3 / 少见 4 / 稀有 6 / 极稀有 8）× 等级，
  仅限宝藏的附魔（经验修补、冰霜行者…）**×2**；**按差额结算** —— 已有的附魔不重复收费，降级**退差价**。
  定价用原版稀有度而不是拍脑袋的常数，好处是对模组附魔自动生效（它们也有自己的稀有度）。
- **另外三项进阶档功能**：
  - **经验修复**：花等级把耐久直接修满（每 1 级修回「总耐久 ÷ 25」，不需要修复材料）；
  - **经验回收**：拆掉全部附魔返还计价总额的 **60%**，同时清掉铁砧的累积惩罚（否则附魔没了、
    惩罚还留着，拿去铁砧改会莫名贵一大截）；
  - **做附魔书**：槽里放书即可，书能承载全部附魔，选好就产出对应附魔书。
- **铁砧限定组合**：原版铁砧组合不校验附魔类别，这里同样放宽到**同一大类**
  （斧头加锋利、头盔加深海探索者、剑加效率…），但不会到「任意物品随便附」的地步。
  互斥的附魔（锋利 / 亡灵杀手 / 节肢杀手…）在界面上标「冲突」并禁止选择。
- **实现**：菜单与界面全部自建 —— `PortableInfuserMenu`（一个物品槽 + 玩家背包，双端同一个类：
  不读书架、不写方块，没有世界侧依赖）+ `PortableInfuserScreen`（自己画的深色扁平界面，
  可滚动清单 + 行内箭头）。原版那套「随机三档 + 数据槽同步线索」的模型（`EnchantmentMenu` 子类、
  免青金石基类、`EnchantPowerPacket`、附魔强度隐藏属性）整体删除。
  客户端用与服务端**同源**的纯函数现算清单与价格，只有点「附魔 / 修复 / 回收」才发
  `InfuserActionPacket`，服务端**重新校验并结算**（附魔条目数还有上限，防止构造超长包）。
- **旧存档迁移**：以前投在「随身附魔台」上的点数、以及被拆到隐藏属性 `enchant_power` 上的点数
  **全部退回可用点数**，只留 1 点在开关上 —— 附魔强度（书架强度）这条限制随本次改造取消，想附多高就多高。
- 协议版本 `17 → 18`（附魔强度包换成了附魔动作包）。

## [1.36.3] - 2026-10-07

### 🖌️ 「使用速度」把刷子刷废了

- **现象**：加了「使用速度」（满级 Lv.100 = +200%）之后，**刷子完全不能用** —— 对着可疑的沙 / 沙砾
  右键毫无反应，既没有刷的声音也没有进度。
- **原因**（对着 1.20.1 的字节码核过）：
  - 原版 `BrushItem` 的 `getUseDuration` = 200，而**真正"刷一下"只发生在 `onUseTick` 的特定节拍上**：
    它按 `i = 200 - 剩余tick + 1` 算出进度，只在 `i % 10 == 5`（即 i = 5 / 15 / 25 … 195）的那些 tick
    里播放刷的声音并调用 `BrushableBlockEntity.brush(...)`；
  - 本属性的加速方式是「**每 tick 从剩余使用时长里多扣若干 tick**」（满级时一刀扣 200），
    结果第一 tick 就把剩余压成 0 —— 而原版 `LivingEntity#updateUsingItem` 只在
    **剩余 > 0 时**才调用 `onUseTick`，于是这次使用从头到尾一次 `onUseTick` 都没跑，
    刷子的全部逻辑（声音 + 刷进度 + 掉耐久）都被跳过，自然"刷不动"。
- **修复**：
  1. **刷子不参与加速**（服务端 `StatEventHandler#onItemUseTick` 与客户端 `ClientEventHandler`
     各加一道 `instanceof BrushItem` 的排除）—— 刷子恢复原版手感，其余物品照常加速；
  2. 顺手把加速后的剩余时长**下限从 0 提到 1**：原版是靠「剩余 ≤ 0」才结算 `completeUsingItem()`、
     靠「剩余 > 0」才调 `onUseTick`，压到 0 会让这次使用一个逐 tick 逻辑都不跑。吃东西虽然照样
     能结算，但任何依赖 `onUseTick` 的物品（含其它模组的）都会失效。
- 已知但**本次未改**：满级下「盾牌 / 望远镜」这类"持续按住"的物品会因为使用时长被压缩而提前结束
  （盾牌约 18 秒后自动放下）。它们的行为由原版使用时长驱动，等有需要再单独排除。

## [1.36.2] - 2026-10-07

### 🩸 修「攻击削减生命上限」扣到自己身上

- **现象**：玩家的最大生命值属性里出现一条 `-100 (infinitestats.reduce_max_health)`，自己的血上限被永久扣掉。
- **原因**：`StatEventHandler.getPlayerAttacker` 把「伤害来源实体是玩家」一律当成玩家发起的攻击。而
  **玩家被自己造成的伤害命中时，来源实体同样是玩家自己**（自己引爆的 TNT / 爆炸箭贴脸炸、
  射上天又落回来的箭、自己召唤物的误伤等）。于是整套进攻属性作用到了自己身上：
  - 「攻击削减生命上限」每命中一次就给自己叠一层负修饰符，累计成截图里的 `-100`；
  - 自伤还会反过来给自己吸血（生命偷取）。
- **修复**：
  1. `getPlayerAttacker(LivingHurtEvent)` 增加「自伤不算攻击」判断 —— 受害者就是攻击者本人时返回
     `null`，整个攻击分支（暴击 / 生命偷取 / 处决 / 范围攻击 / 护甲穿透 / 降上限 / 取消无敌帧…）
     都不再作用到自己身上；
  2. 生命偷取那里读的是 `DamageSource`（拿不到受害者），单独判一次，自伤不再吸血；
  3. `AttackHandler.applyReduceMaxHealth` 兜底：`target == player` 直接返回。
- **已经中招的存档**：那条修饰符是本模组用 `addTransientModifier` 加的**瞬态**修饰符、不写入存档，
  重进存档或死一次重生就会消失；若还在，可手动清除：
  `/attribute <玩家> minecraft:generic.max_health modifier remove a1b2c3d4-0000-4e5f-8a9b-0c1d2e3f4a5b`

## [1.36.1] - 2026-10-06

### 🛠️ 随身工作台：修掉「Shift 连着做一大批」时的卡顿/假死

- **现象**：在随身工作台里按住 Shift 连续取出成品（尤其倍率调高、且接了 RS / AE2 等存储网络
  会自动补料时），客户端会卡住几秒甚至像假死。
- **原因**：原版的 Shift 取出是「一次点击 = 一个循环里反复合成」，而结果槽每次取出后都会从
  存储网络把材料补满，于是这个循环能一直转下去。而每次取出都走了下面这条放大链路：
  - 原版消耗材料会**连续改动 9 个网格槽**，本模组用的 `TransientCraftingContainer` 每改一个槽
    就回调一次 `slotsChanged`；
  - `slotsChanged` 里要做**整张配方表查找**（+ Polymorph 查询）并**给客户端发一次包**；
  - 补水阶段再改几个槽，又是一轮。

  即：一次取出 ≈ 十几次配方查找 + 十几次发包；连做几十次就是上千次，主线程自然被卡住。
- **修复**：
  1. **批量抑制**（主要修复）：新增 `beginGridBatch / endGridBatch`，`AutoRefillResultSlot.onTake`
     把「原版消耗 + 自动补料」整段包起来，期间 `slotsChanged` 直接返回，整段结束只重算并同步一次。
     每次取出的配方查找 / 发包从十几次降到 1 次；
  2. **连做封顶**（保险）：同一游戏刻内最多连做 `MAX_CRAFTS_PER_CLICK = 64` 次，超出后
     `quickMoveStack` 返回空、原版循环随即结束 —— 防止任何情况下被拖进长循环。松手再点一次即可继续。
- 补料逻辑本身未改（仍是「先网络、再背包」），只是把 `onTake` 里那段抽成了 `refill(...)`。
- 单次取出、成品去向（背包 / 存储）、Polymorph 配方选择等行为均不变。

## [1.36.0] - 2026-10-06

### 💍 「死亡不掉落」现在也保留 Curios 饰品栏

- **现象**：开启「死亡不掉落」后死亡，背包 / 盔甲 / 副手都保住了，但**饰品栏（Curios）里的东西
  还是会掉在地上**。
- **原因**：饰品栏是 Curios 挂在实体上的独立 capability，既不在原版 `Inventory` 的
  `items / armor / offhand` 里，也不受原版 `keepInventory` 游戏规则保护；本模组此前只把
  原版那 41 格挪走，饰品自然仍按 Curios 自己的规则掉落。
- **修复**：新增 `compat/CuriosBridge`（纯反射，无需编译期依赖，Curios 未加载时全是空操作），
  死亡瞬间把饰品也抓走清空、重生时放回原槽位：
  - 抓取按 `{slot 槽位ID, index 槽位下标, stack 物品}` 记录，**槽位类型与下标都保留**，
    放回时能精确回到原来那一格（`ring / necklace / back / hands …` 各自归位）；
  - 这份记录跟着 `PlayerStats` 一起落盘、一起随实体克隆，玩家在死亡界面断线 / 服务器重启
    也不会丢；
  - **归还时机放在重生事件（`PlayerEvent.PlayerRespawnEvent`）而不是 `PlayerEvent.Clone`**：
    Curios 自己也监听 Clone 并把旧实体的饰品整份拷到新实体上，若在 Clone 里先放回，
    随后会被那份「已被清空」的数据覆盖掉；
  - **安全策略是"宁可掉落，绝不凭空消失"**：抓取阶段任何异常就整份作废（返回 null，饰品栏
    保持原样，让饰品照原版掉落）；归还阶段槽位不存在 / 类型不匹配 / 放不下，一律退回玩家背包，
    背包也满了就丢在脚下。
- 属性描述（`StatType` 与中英 `lang`）同步补上"饰品栏"。
- **不影响**：未安装 Curios 时行为与之前完全一致；未开启「死亡不掉落」时饰品照常掉落。

## [1.35.0] - 2026-10-06

### 💥 「排斥」改为等价交换（ProjectE）SWRG 护盾式的弹飞效果

- **效果**：排斥不再是"每 0.5 秒轻推一下敌对生物"，而是**每 tick** 把范围内的一切
  沿"你 → 目标"方向弹开，推力大小约 0.65 格/tick **且几乎不随距离衰减** ——
  所以生物一进入范围就会被弹飞好几格，而不是被稳稳挡在边缘。
- **作用目标**（对齐 ProjectE 的 `WorldHelper#validRepelEntity`）：
  - 生物（`Mob`，含动物，**不含玩家**）——旧实现会把队友/其他玩家一起推开，现在不会了；
  - **飞行中的弹射物**（箭、雪球、火球……）会连同方向一起被吹偏；已插在地上的箭不受影响；
  - **不推自己扔出去的弹射物**（否则自己的箭会被自己吹飞）；
  - 目标 100% 击退抗性（铁傀儡、劫掠兽等）时推不动。
- **实现**：重写 `AttackHandler.applyRepulsion`，新增 `isRepellable` / `repelEntity` 两个私有方法，
  公式照搬 ProjectE 的 `WorldHelper#repelEntitiesSWRG` + `#repelEntity`：
  `推力 = (目标位置 - 玩家位置) / (1.5 * (距离 + 0.1))`，每 tick 累加到目标速度上
  （并置 `hasImpulse`），与 ProjectE 一致。
  - 注：ProjectE 里**没有**叫 "Blast Ring" 的物品，这套排斥来自 **SWRG（Swiftwolf's Rending Gale）
    戒指的 Shield 模式**，本模组对齐的就是它。
- **与 ProjectE 的两处有意差异**：
  1. 半径仍由属性点数缩放（每点 +0.5 格），ProjectE 是固定 5 格；
  2. 额外乘目标的击退抗性系数（等同 `Entity#push(Entity)` 的处理），
     ProjectE 的 `Entity#push` 不吃抗性 —— 不这样改的话百抗生物也会被推飞。
- 推力现在是**三维**的（ProjectE 原样）：玩家悬空时，正下方的生物会被向下推开。
- 属性描述（`StatType` 与中英 `lang`）同步更新为新行为。
- 仍在服务端 tick 执行（扫描范围 = 排斥半径），无新增协议 / 存档字段。

## [1.34.1] - 2026-10-05

### 🌏 补齐配置界面里三个分类的中文 / 英文（AutoFish / WeatherControl / ChunkLoader）

- **现象**：模组列表 → 本模组 → Config 打开配置界面后，右侧那几个分类页签显示成裸键
  `config.infinitestats.category.AutoFish` / `…WeatherControl` / `…ChunkLoader`，
  点进去条目名同样是 `config.infinitestats.AutoFish.autoFishRecastDelayMin` 这类原始键名。
- **原因**：这三个配置段是后加的（自动钓鱼 / 天气控制 / 区块强加载），
  `lang/zh_cn.json` 与 `lang/en_us.json` 里只补了段内**注释**（注释直接取自配置文件的 comment，
  所以是中文），漏了**分类名与条目名**的翻译键。
- **修复**：中英各补 7 条键 —— 3 个分类名
  （`category.AutoFish` / `category.WeatherControl` / `category.ChunkLoader`）
  + 4 个条目名（`AutoFish.autoFishRecastDelayMin` / `.autoFishRecastDelayMax`、
  `WeatherControl.weatherCycleDuration`、`ChunkLoader.maxForcedChunks`）。
- 顺带核对：其余 12 个配置段的分类名与全部条目名都已齐全，没有别的漏译。
- 纯文案改动，无代码 / 协议 / 存档变化。

## [1.34.0] - 2026-10-05

### 🧪 随身附魔台不再需要青金石

- **效果**：随身附魔台附魔**不需要青金石**（青金石槽可以空着），**经验等级照常消耗**，
  三档消耗与附魔结果都与原版一致；玩家自己放进青金石槽的青金石**一颗不少**（不会被吃掉），
  该槽从此纯属装饰。
- **实现**（新增 `crafting/AbstractLapisFreeEnchantingMenu`，客户端与服务端的菜单都继承它）：
  原版 `EnchantmentMenu` 有两处离不开青金石 —— `clickMenuButton` 开头的「青金石槽数量 ≥ 档位序号」检查，
  以及附魔成功时的 `itemstack1.shrink(i)` 扣除。关键在于这两处用的是**同一个局部变量**，
  它来自方法开头唯一一次 `enchantSlots.getItem(1)`；因此做法是：调原版逻辑**之前**把青金石槽
  临时换成一整组虚拟青金石，`finally` 里原样还原 —— 检查因此通过、被扣掉的只是那份虚拟的，
  玩家放进去的青金石不受影响（也就不用再特判创造模式）。
  - 临时换槽期间**不同步槽位**（`PortableStationMenus.Enchanting#slotsChanged` 按
    `isSlotSyncSuppressed()` 跳过 `broadcastChanges`），否则客户端会看到青金石槽闪一下「64 颗」；
  - 另外覆写 `getGoldCount()` 恒返回 3：原版界面正是用它决定「显示附魔线索」还是
    「显示还差几颗青金石」的 —— 不装作够用的话，槽里没青金石时玩家就看不到自己要附什么魔
    （那一行只剩一个数字）。
  - **客户端那份菜单也必须继承**：原版 `EnchantmentScreen.mouseClicked` 会先在本地的菜单上
    调一次 `clickMenuButton`，校验不过连点击都不会发给服务端。
- **只影响随身附魔台**：只有它的菜单继承这个基类，世界里的真附魔台行为一字未改。
- 无协议、无存档结构变化。

## [1.33.2] - 2026-10-05

### 📦 构建时同时产出「含内嵌 jar」和「不含内嵌 jar」两个包

- **背景**：本模组把 `pinyin_search` 用 JAR-in-JAR 内嵌在主产物里，好处是玩家 / 整合包无需另装；
  但如果整合包**自己也装了** `pinyin_search`，两份 jar 会声明同一个 modId，可能出问题。
- **新增产物**（`build` / `assemble` 会一起产出，都进 `build\libs\` 与 `dist\`）：

  | 产物 | 内容 | 用途 |
  |---|---|---|
  | `<名>-<版本>.jar` | 含 `META-INF/jarjar/**`（内嵌 `pinyin_search-<版本>.jar` + `metadata.json`） | **默认分发这个**，玩家 / 整合包无需另装 |
  | `<名>-<版本>-noembed.jar` | 不含上述内嵌内容，其余完全一致 | 整合包**已自带 `pinyin_search`** 时用这个，避免重复 modId |

- **实现**：新增 `noEmbedJar` 任务，**直接从主产物里过滤掉 `META-INF/jarjar/**` 再打一个包**
  （而不是从零再打一遍），因此除内嵌内容外与主产物完全一致 —— 同一份 refmap、mixin 配置与重映射结果，
  两个包不会各自漂移。任务 `dependsOn reobfJar`：`jar` 产出的是 official 名的包，`reobf` 是**就地改写**，
  取早了会拿到没重映射的那份；清单由本任务自己写（排除主产物里的 `META-INF/MANIFEST.MF`）。
  两个 jar 共用同一份清单属性生成函数，作者名走按 UTF-8 读出的 `modAuthors`。
- **`build.bat` 同步**：同时列出、复制两个包；`deploy` 时**若目标整合包 mods 里已有 `pinyin_search`，
  自动改用 no-embed 包**并在输出里标明用了哪一个。
- 纯构建 / 打包改动，游戏内行为与产物代码完全不变。

## [1.33.1] - 2026-10-05

### 🖥️ 随身附魔台的加点面板挪到界面正上方（原先被 JEI 物品列表压住）

- **问题**：1.32.0 把「附魔强度 ±」面板画在原版附魔界面的**右侧**，而屏幕右侧正是 JEI 物品列表的位置
  —— JEI 是 GUI 覆盖层、画在界面之后，于是列表直接把面板压住，`Lv.0 / 15`、可用点数与说明文字全都看不全。
- **改动**：面板改画在附魔界面**正上方**（与界面同宽 176，不外扩）：顶部一行标题、
  中间一行「`Lv.N / 15`」+ 左右两端的 `−` / `+`、底部一行「可用点数 N · 每点 +1（1 个书架）」。
  原版附魔界面本身一行未改（仍是不改 `imageWidth`、只另画一块面板，贴图不会被裁切错位）。
- 说明文字的完整版挪进 `+` 按钮的浮窗，面板里只留短句 —— 176 宽的面板放不下长句，硬放会挤成两行压到界面。
- 纯界面布局改动，无协议、无存档结构变化。

## [1.33.0] - 2026-10-05

### 🌦️ 天气控制改为独立面板（点哪档切哪档）

- **入口变化**：属性面板页脚的「天气」按钮不再「点一次切一档」，改为打开新的
  **天气面板 `WeatherScreen`**：
  - 顶部显示当前主世界天气（晴 / 下雨 / 雷暴，客户端本地读，切换后由服务端同步过来）；
  - 列出现有全部天气档位（晴天 / 下雨 / 雷暴），**点哪一档就切哪一档**；当前档位带绿色描边，
    未解锁时整列置灰并在标题下给出提示；
  - 底栏写明作用范围（**主世界** —— 在下界 / 末地切换的也是主世界天气）与持续时长
    （读 `WeatherControl.weatherCycleDuration`，面板里换算成秒显示）。
- **关于「所有已注册的天气」**：原版 MC **没有天气注册表** —— 天气只是 `Level` 上的
  `raining` / `thundering` 两个布尔加两个计时器，所以能列的档位就是**晴天 / 下雨 / 雷暴**三档
  （下雪是「下雨 + 寒冷生物群系」的渲染表现，不是独立天气）；天气类模组各写各的字段，
  也没有统一注册表可以枚举。面板中的档位抽成了一处列表（`WeatherScreen#ENTRY_IDS`），
  以后要加档位（或联动某个天气模组）只改这一处即可。
- **协议**：`WeatherControlPacket` 增加档位字段（`clear` / `rain` / `thunder`；空串仍按旧逻辑循环一档），
  协议版本 `16 → 17`。

### 🪜 修好「跨越高度」加了点却走不上台阶

- **根因**：跨越高度**不是**原版 `maxUpStep` 一个字段说了算。Forge 1.20.1 把 `Entity#collide`
  里读的值换成了 `IForgeEntity#getStepHeight()`，其定义是「原版 `maxUpStep` 字段
  **加上** `forge:step_height_addition` 属性的值」（已对照 Forge 补丁源码逐行确认）。
  旧实现只写原版字段，于是整合包里**只要另有模组也去设置 `maxUpStep`**（大包里很常见），
  我们的数值就会被它盖掉 —— 客户端本地跳上去了、服务端判定不一致，`handleMovePlayer`
  判「moved wrongly」后把玩家拉回原地，表现就是「加了 1700 点、面板写着 +1020%，却一步也上不去」。
- **修复**：改走 **Forge 官方属性通道**（新增 `MobilityHandler#applyStepHeight`，
  客户端与服务端共用同一份实现）：
  - 原版字段只写**基准值**（`0.6`，开了自动跨越则 `1.25`）—— 不再把大数值塞进这个容易被别人读写的字段；
  - 把「超出基准的差额」写进 **`forge:step_height_addition`** 属性，于是
    `getStepHeight()` = 基准 + 差额 = 目标值，正好；
  - 修改器使用**两端固定的 UUID**：属性从服务端同步回客户端时，原版按 UUID 替换同名修改器，
    不会出现「本地一份 + 服务端一份」的重复叠加；
  - 只在数值真正变化时才增删修改器 —— `AttributeInstance` 每次增删都会置脏并触发一次属性同步包，
    每 tick 无脑重写等于刷包。
- 百分比语义不变（基准 × (1 + 加成)，加成为负时不参与），`auto_step` 开关行为不变。

### 🔧 构建

- 本机网络过不了 ForgeGradle 对 `maven.minecraftforge.net` 的证书预检（插件 apply 阶段直接失败，
  报 `Failed to validate certificate for host`），按 ForgeGradle 自己的提示在 `gradle.properties` 里加上
  `systemProp.net.minecraftforge.gradle.check.certs=false`。这只是 FG 的一次仓库连通性预检，
  真正的依赖下载依旧走 JDK 正常的 TLS 校验。

## [1.32.1] - 2026-10-05

### 📝 重写模组描述（模组列表里显示的那段）

- **修正过期内容**：原描述写的是「56 个属性 / 5 大类（含 Magic / 魔法）」，而魔法分类早在 1.15.0 就已
  移除、属性也已增至 96 条。现改为「96 个内置属性，攻击 / 防御 / 机动 / 功能四大类」，
  并写明「等级无上限、点数可随时退回重加」。
- **补充后来才有的功能**：外部属性自动发现 + 内置 30+ 模组中文译文、六个面板（属性 / HUD /
  物品编辑器 / EMC 转化桌 / 成就 / 传送点）、随身站点全套（工作台 / 熔炉 / 铁砧 / 附魔台 / 切石机 /
  织布机 / 制图台 / 磨石 / 末影箱，支持从 RS / AE2 等存储网络取料）、按玩家的功能开关
  （整合包作者可用 `/infstats feature` 逐步开放）、完整多人同步、无必需前置（拼音搜索库已内嵌）。
- **同步 `gradle.properties` 的 `mod_description`**：该属性目前并没有被 `mods.toml` 引用
  （`mods.toml` 用的是自带的多行块），但模板里留着，两边口径不一致容易把人带偏，故一并改成同一份说法。
- **顺带修掉作者名乱码**：`gradle.properties` 是 Java Properties 格式，Gradle 按 ISO-8859-1 读入，
  于是含中文的 `mod_authors` 展开进 `mods.toml` 与 jar 清单后变成「ä½åæ¢æ å」——
  模组列表里的作者一直是乱码。现在 `build.gradle` 额外按 UTF-8 重读一份专用于产物写入，
  作者正确显示为「佚名既无名」（数值属性仍走 Gradle 原本的读法，不受影响）。
- 纯文案 / 元数据改动，无代码 / 协议 / 存档变化。

## [1.32.0] - 2026-10-05

### 🧰 随身附魔台改为 1 点开关，「附魔强度」加点搬进附魔界面

- **属性拆分**：`portable_enchanting`（随身附魔台）由**点数型**（等级＝附魔强度，1~15）改为
  **1 点解锁的开关** —— 与切石机 / 织布机 / 制图台 / 磨石一致，属性面板里显示为一个开关。
  附魔强度单独拆成**隐藏属性** `enchant_power`（**不在属性面板显示、也无法在那里加点**）。
  - 拆开的原因：原先「0 点 = 打不开附魔台」，必须先投点数才能用；开关化后「能不能用」与
    「强度多高」变成两件事，各自独立。
- **加点入口搬进附魔界面**：打开随身附魔台后，界面**右侧多出一块面板**（原版界面保持 176 宽不变，
  扩展面板画在 `leftPos + 176` 之后，贴图不会被裁切错位）：
  - `附魔强度` 标题 + `Lv.N / 15` + `可用点数 N`；
  - `−` / `+` 两个按钮，每次 1 点；`+` 在点数不足或已满 15 时置灰（浮窗说明原因）；
  - 一句自动折行的说明（每点 +1，等价于原版 1 个书架，15 点 = 原版满级）。
  - 点一下 `±` 会**当场重算三档附魔**（服务端按新强度重跑 `slotsChanged`，把新的消耗与线索同步回客户端），
    因此可以一边加点一边看三档附魔等级变化，不必关掉重开。
- **实现**：
  - 新增 `crafting/PortableEnchantingMenu`（**客户端**菜单，`ContainerLevelAccess.NULL`）与
    `ModMenuTypes.PORTABLE_ENCHANTING_MENU`（泛型刻意写成 `EnchantmentMenu`，这样客户端界面才能
    继承原版 `EnchantmentScreen`）—— 服务端仍用 `PortableStationMenus.Enchanting`（真实 access，
    附魔算法必须跑在回调里），两端只在菜单类型上统一，附魔算法一行未改；
  - 新增 `client/PortableEnchantingScreen`：继承原版 `EnchantmentScreen`，只重写
    `renderBg`（补画右侧面板）、`renderLabels`（强度 / 点数 / 说明）与 `containerTick`（按钮状态），
    原版贴图、书页动画、三档附魔文字与点击逻辑全部沿用；
  - 新增 `EnchantPowerPacket`（`+` / `−`），加点复用 `PlayerStats#addPoints` / `removePoints`
    —— 与属性面板同一套校验（可选点数、上限、返还语义），改造过的客户端无法借此白拿点数；
    协议版本 `15 → 16`。
- **旧存档迁移（不丢点数）**：旧存档里 `portable_enchanting` 投了 N 点（N > 1）时，加载时把多出来的
  N-1 点**原样搬进** `enchant_power`、只在开关上留 1 点；强度已满、搬不下的部分退还到可用点数。
  总投入不变，玩家不需要重新加点（`PlayerStats#migratePortableEnchanting`，逻辑幂等）。
- **随身工具面板**：附魔台按钮的浮窗仍显示当前强度，但读数改为 `enchant_power`。
- 顺带修复：**补齐属性面板里新增的 7 个属性图标**（随身附魔台 / 随身切石机 / 随身织布机 /
  随身制图台 / 随身磨石 / 天气控制 / 区块强加载 —— 此前这几行是空图标）。
- 内置属性 95 → **96**（功能类 51 → 52，其中 `enchant_power` 为隐藏属性）。

### 🛠 新增构建脚本 `build.bat`

- 一键构建：`build.bat`（增量）/ `build.bat clean`（全量）/ `build.bat check`（只编译，最快）/
  `build.bat deploy`（构建后自动把 jar 部署进整合包 `mods/`，自动探测已装有本模组的整合包目录，
  也可用第二个参数指定目录）。
- 脚本会自动跳过 `-all.jar` 中间产物、校验 jar 里含 refmap、并把产物复制到工作区内 `dist/`；
  构建时一并编译 `gameTest` 源集，回归测试写坏会当场暴露。
- 脚本内容保持**纯 ASCII**：cmd 按 OEM 代码页解析 .bat，文件里出现中文会让批处理语法报错
  （`文件名、目录名或卷标语法不正确`）。

## [1.31.1] - 2026-10-05

### 🐞 修复构建缓存导致 jar 缺 refmap、启动即崩（Critical injection failure）

- **现象**：整合包里启动直接崩在 Mixin 注入阶段，日志关键两行：
  - `Reference map 'infinitestats.refmap.json' for infinitestats.mixins.json could not be read.`
  - `MixinApplyError: Mixin [infinitestats.mixins.json:EntityMoveRelativeMixin] FAILED during APPLY`
    → `InvalidInjectionException: Critical injection failure: @Redirect annotation on
    infinitestats$scaleSwimAcceleration could not find any targets matching
    'moveRelative(FLnet/minecraft/world/phys/Vec3;)V' in net.minecraft.world.entity.Entity.
    No refMap loaded.`
- **根因（不是 Mixin 写错了，是打包漏了文件）**：refmap 由 Mixin 注解处理器在 `compileJava` 里生成到
  `build/tmp/compileJava/infinitestats.refmap.json`，**但这个路径没有声明成 `compileJava` 的输出**。
  本项目开着 `org.gradle.caching=true`，`clean` 之后第二次构建 `compileJava` 必然 `FROM-CACHE`，
  缓存只还原「**已声明**的输出」，refmap 不在其中 → 它凭空消失 → `addMixinsToJar` 找不到文件 →
  产出的 jar 里只有 `infinitestats.mixins.json`、没有 `infinitestats.refmap.json`。
  没有 refmap，生产环境 Mixin 就没法把 `moveRelative` / `createResult` / `setDeltaMovement`
  这类**字符串形式的成员名**转成 SRG 名（`m_19920_` / `m_6640_` / `m_20256_`），注入锚点全部匹配失败。
- **为什么以前没事**：冷构建（compileJava 真执行）时 refmap 会正常生成并被打进 jar，所以此前一直正常；
  只要某次构建恰好命中编译缓存，就会静默产出这种「能编译、能装、启动即崩」的包 —— 属于偶发、且症状与原因完全对不上。
- **修复**：
  - `build.gradle` 里把 `build/tmp/compileJava/${mod_id}.refmap.json` 显式声明为 `compileJava` 的输出，
    使其随缓存条目一起存取，`FROM-CACHE` 也能正确还原；
  - 给 `jar` 加 doLast 兜底校验：jar 里缺 `${mod_id}.refmap.json` 时**直接构建失败**，
    避免再产出这种注定崩的包。
- **无功能改动**：不涉及任何游戏内行为，纯粹是构建/打包修复。

## [1.31.0] - 2026-10-05

### 🔍 拼音搜索改为接入通用库 `pinyin_search`（移除 JECh 通道）

- **背景**：各搜索框此前只认 JustEnoughCharacters（JECh，反射其内部 `Match.contains`）。JECh 是给
  「它自己收录的模组」打补丁的路线，未被收录就搜不了拼音，且匹配行为还受玩家自己那份 JECh 配置影响。
  现在改为支持**按需主动适配**的通用拼音搜索库
  **`pinyin_search`**（modId `pinyin_search`，API `com.pinyinsearch.api.PinyinSearch`）。
- **移除 JECh**：本次**不再保留** JECh 通道 —— 原 `compat/JechCompat` 及其反射逻辑、`mods.toml` 里
  `jecharacters` 的可选依赖声明一并删除。装了 JECh 的整合包不受影响（只是搜索回到普通包含匹配），
  想用拼音请装 `pinyin_search`。
- **新的后端优先级**：`pinyin_search` › 普通「包含」匹配（没了中间那一级）。
- **实现**：原 `compat/JechCompat` 由 **`compat/PinyinSearchBridge`** 取代 —— 按该库接入文档
  `docs/INTEGRATION.md` §2 的 Bridge 模板**全文照抄**（只改包名），走它的公开 API 而不是反射：
  - 该库的类型在本模组代码里只出现在方法体内（见下），编译期依赖 + jarJar 内嵌；
  - 照抄模板原本就带的两点约定：
    - 库的类型（`PinyinSearch` / `Matcher` / `Profile`）**只出现在方法体里**，不写进字段类型 / 方法签名 /
      继承关系 —— 否则没装库时类加载阶段就 `NoClassDefFoundError`，「没装就回退」直接失效；
      需要长期持有的库对象用 `Object` 装、在方法体内转型（`Index` 内部就是这么做的）；
    - 回退分支自己写（`rawContains`）：没装库时退化为纯原文包含，**不能用 `Matcher.literal` 代替**，
      那时连 `Matcher` 类都加载不了。
  - 模板里除 `matches` 外的能力也一并保留：`index(pool)` / `index(pool, pinyinEnabled)`（形态二：
    大列表建一次索引反复搜，`Matcher.literal` 对应「装了库但要关掉拼音」）目前没有调用点，
    留给后续需要时直接取用。
  - 调用点全部位于客户端 GUI 的搜索框（客户端主线程），符合该库
    「`Matcher` 必须在客户端主线程使用」的约定。
- **依赖（内嵌，玩家无需另装）**：走官方 JitPack 坐标，两行：
  `compileOnly`（编译期 API）+ `jarJar`（**JAR-in-JAR 内嵌**，运行期当嵌套模组加载）。
  `mods.toml` 只留一条 `mandatory = false` 的版本关系声明（`[1.1.0,2.0.0)`）。
  `jarJar` 这一行踩了三个坑，都已处理：
  - **版本必须写 maven 区间**：写精确版本会直接失败
    （`The given version specification is invalid: 1.1.3. ... convert this to a maven compatible format: [2.0,3.0)`），
    所以写 `[1.1.0,2.0.0)`（要钉具体产物再加 `jarJar.pin(it, "<版本>")`）。
    该库 `docs/INTEGRATION.md` §1 方式 B 给的 `jarJar '...:1.1.0'` 在 ForgeGradle 6 上是行不通的写法。
  - **必须 `transitive = false`**：JitPack 发出来的 POM 把 Forge / MC 的一堆库都算作它的依赖，
    不切断传递依赖会把它们全部嵌进来 —— 实测主 jar 从 875 KB 涨到 **91.7 MB / 116 个嵌套 jar**
    （netty、asm、antlr4、oshi、brigadier、patchy、srgutils…）。加上后只剩 `pinyinsearch-1.1.3.jar` 一个。
  - **FG 的 `jarJar` 默认只把内嵌内容放进独立的 `<jar 名>-all.jar`**，主产物不带；
    本模组在 `jar` 任务里把 `META-INF/jarjar/**` 合并进主产物，因此**照旧只需分发主 jar**。
- **依赖来源**：官方坐标 `com.github.2779789119:pinyinsearch` 一开始取不到（tag 1.1.1 / 1.1.2 在 JitPack 上
  构建失败：ForgeGradle 在它容器里报 `ProjectScopeServices has been closed`、取不到 `net.minecraft:joined:...:srg`），
  接入期间一度用工程内 `libs/maven`（坐标与官方一致）顶替；**`1.1.3` 在 JitPack 上构建成功后已切回官方坐标，
  `libs/` 已删除**，仓库里只多一行 `maven { url "https://jitpack.io" }`。
- **验证**：`gradlew clean build` 通过，主 jar 875 KB，内含
  `META-INF/jarjar/pinyinsearch-1.1.3.jar` + `META-INF/jarjar/metadata.json`
  （`"range": "[1.1.0,2.0.0)", "artifactVersion": "1.1.3", "isObfuscated": false`），
  内嵌模组自报 `modId = pinyin_search` / `version = 1.1.3`，满足区间。
- **与单独安装的关系**：整合包若**另外**装了 `pinyin_search`，可能与内嵌的那份撞 modId ——
  这一点需要游戏内确认（该库 `docs/INTEGRATION.md` §6 验收清单里的第 3 条）。
- **改动范围**：7 个界面的搜索调用由 `JechCompat.matches(...)` 换成 `PinyinSearchBridge.matches(...)`
  （`StatsScreen` 属性面板、`DebuffFilterScreen` 效果过滤器、`ItemEditorScreen` / `ItemEditSelectScreen`
  物品编辑器、`AutoDepositFilterScreen` 入库过滤、`EmcScreen` 转化桌、`AchievementManagerScreen` 成就面板），
  **调用方式完全不变**，只是类名替换。
- **未装时零影响**：没装 `pinyin_search` 时退化为纯原文包含（大小写不敏感），
  行为与本模组引入拼音前完全一致。
- `mods.toml`：删除 `jecharacters` 声明，新增 `pinyin_search` 依赖声明
  （`[1.1.0,2.0.0)`，`mandatory = false`，`side = CLIENT`）。
- **期望管理**（库里实测的结论，接入方不额外补丁）：英文**词首字母**搜不到
  （`Diamond Sword` 搜不到 `DS`）；**简繁不互搜**；上下文多音字不做词级控制。
- 无协议、无存档结构变化。

## [1.30.0] - 2026-10-05

### 🧰 随身工具合并为一个入口 + 补齐 5 个新站点

- **合并**：原先散在页脚的两颗按钮（末影箱 / 锻造台）与页脚导航行里的三个入口（工作台 / 熔炉 / 铁砧）
  全部撤掉，改为**一个**「随身工具」入口，点开是独立的随身工具面板（`PortableToolsScreen`）。
  未解锁的工具按钮呈灰色不可点，悬停会提示需要解锁哪条属性；已解锁的点一下即打开。
- **补齐新站点（功能分类）**：
  - `portable_enchanting` 随身附魔台 —— **点数型属性（上限 15 点）**：每点 +1 附魔强度（等价于原版 1 个书架），
    强度越高三档附魔等级越高，15 点即原版满级（30 级附魔），**不再依赖周围书架**；
  - 其余各 **1 点**解锁：`portable_stonecutter` 随身切石机；
  - `portable_loom` 随身织布机；
  - `portable_cartography` 随身制图台（可正常读取 / 绑定地图）；
  - `portable_grindstone` 随身磨石。
- **修复旧 bug：随身铁砧 / 随身锻造台关界面会吞掉输入槽物品。** 原实现给菜单传
  `ContainerLevelAccess.NULL`，而 `ItemCombinerMenu#removed` 的 `clearContainer` 写在
  `access.execute(...)` 回调里 —— NULL 的 `evaluate` 返回 `Optional.empty()`，回调根本不执行，
  于是关界面时留在铁砧 / 锻造台里的材料会随菜单一起被丢弃：
  - 随身铁砧：**仍保留 NULL**（原版 `AnvilMenu#onTake` 的回调里写着"有概率把铁砧砸坏"，
    用真实 access 会让随身铁砧真的在世界里留下破坏），改为在 `PortableAnvilMenu#removed`
    里手动 `clearContainer` 补回物品；
  - 随身锻造台：改用新的随身站点菜单（见下）。
- **新增 `crafting/PortableStationMenus`**：附魔台 / 切石机 / 织布机 / 制图台 / 磨石 / 锻造台
  统一走「真实 `ContainerLevelAccess`（玩家脚下维度 + 坐标）+ 覆写 `stillValid` 恒为 true」：
  - 真实 access 保证世界侧回调正常执行（附魔等级计算、点击附魔、制图台的成品计算、关闭时归还物品）；
  - 覆写 `stillValid` 去掉「必须站在对应方块旁」的校验，从而不依赖世界里的方块；
  - 子类只存在于服务端：客户端仍由原版 `MenuType` 工厂创建原版菜单，因此原版界面照旧套用。
  - **随身附魔台另有一点不同**：原版强度计算写在 `EnchantmentMenu#slotsChanged` 里、直接读世界里的书架，
    所以该菜单子类把整段重写，只把「书架强度」换成点数（见上）。随机种子取自 `getEnchantmentSeed()`、
    三档消耗与附魔线索写回原版那三个 public 数组（本来就会随数据槽同步给客户端）、并照常触发
    `EnchantmentLevelSetEvent` 兼容钩子 —— 因此「客户端显示的消耗与线索」「点击附魔的判定」以及
    「实际产出的附魔」都与站在真附魔台前一致（点击附魔仍走父类 `clickMenuButton`，种子与洗牌算法完全相同）。
- **网络**：新增 `PortableToolOpenPacket`（按属性 id 打开白名单内的站点，服务端会再校验一次开关），
  协议版本 14 → 15。

### 🌦️ 天气控制（`weather_control`，1 点）

- 新增属性 `weather_control`（功能分类，开关型）。激活后页脚导航行出现「天气」按钮，
  **点一次切换一档：晴 → 雨 → 雷 → 晴**，切换后聊天栏提示当前天气。
- 作用于**主世界**：玩家在下界 / 末地时切换的仍是主世界天气，这样按钮在任何维度都有意义。
- 持续时长由配置 `WeatherControl.weatherCycleDuration` 决定（默认 6000 tick = 5 分钟），到时后按原版规则自行演变。
- 新增 `WeatherControlPacket`。

### 🧭 区块强加载（`chunk_loader`，1 点）

- 新增属性 `chunk_loader`（功能分类，开关型）。激活后页脚导航行出现「强加载」按钮，
  点开是独立的区块强加载面板（`ChunkLoaderScreen`）：
  - 输入**方块坐标**（可点「填入当前位置」一键填入），实时显示换算出的区块坐标，点「加载」把对应区块设为强加载；
  - 面板下方列出**当前维度**已强加载的区块（每行显示区块坐标与对应方块范围，逐行可单独卸载，超过 5 行自动分页）；
  - 服务端在每次加载 / 卸载后都会把最新列表回推给客户端，因此界面不会与真实状态脱节。
- 采用原版 `/forceload` 的同一套机制（`ServerLevel#setChunkForced` + `ForcedChunksSavedData`），
  强加载数据随存档保存、重启后依然有效；并做世界边界校验。
- 数量上限由配置 `ChunkLoader.maxForcedChunks` 限制（默认 16，上限 256），超限时拒绝并提示，
  避免"一次点满"把服务器拖垮。
- 新增 `ChunkLoaderActionPacket`（加载 / 卸载 / 请求列表）与 `ChunkLoaderSyncPacket`（列表同步）。

### 其他

- 新增配置段 `WeatherControl` 与 `ChunkLoader`；新增 `gui.infinitestats.tools.*`、
  `gui.infinitestats.chunk.*` 与相关提示文案（中 / 英）。
- 属性面板页脚导航行：`工作台 / 熔炉 / 铁砧` 三个入口替换为 `随身工具 / 天气 / 强加载`；
  移除页脚的「末影箱」「锻造台」按钮（已并入随身工具面板）。按钮总数不变，排版不受影响。
- 内置属性 88 → **95** 条（功能类 44 → 51）。
- `/infstats craft | furnace | anvil | enderchest | smithing` 指令保持不变；新增的 5 个站点只从
  「随身工具」面板进入。

## [1.29.1] - 2026-10-05

### 🎯 自动抛竿加视线限制：只在瞄着水面时抛

- **改动**：`auto_fish` 的自动抛竿增加视线检查 —— 主手持竿但**看着别处（陆地、天空、墙、岩浆）时不再甩竿**，
  避免在家里 / 洞里 / 挖矿时凭空抛竿。收杆行为不变（已有浮标时照常收）。
- **判定口径**：从眼睛沿视线取 12 格做一次 `level.clip`：
  - 用 `ClipContext.Block.COLLIDER`（只算**有碰撞**的方块）—— 草、花、火把、藤蔓这类无碰撞方块
    不会被误判成遮挡，浮标本就能穿过它们落进水里，判定与实际飞行一致；
  - `ClipContext.Fluid.ANY` 命中流体时 `BlockHitResult#getBlockPos()` 就是流体方块本身，
    据此只认**水**（瞄着岩浆不算）。
  - 距离 12 格：原版浮标从眼前 0.6 格处沿视线以约 1 格/tick 抛出，落点最远 10 格上下，12 格留了富余。
- 检查放在重抛延迟之后（延迟未到就不做射线，省开销）。
- 同步更新属性描述与 `zh_cn` / `en_us` 文案（说明需要看向水面）。
- 纯服务端行为调整，无协议、无存档结构变化。

## [1.29.0] - 2026-10-04

### ⚡ 新功能：立即咬钩（`instant_bite`）

- **属性**：功能类新增开关型属性「立即咬钩」`instant_bite`（投入 3 点解锁，与自动钓鱼同档）。
- **行为**：把原版「鱼游过来（`timeUntilLured` 100~600 tick ≈ 5~30 秒）→ 鱼准备咬
  （`timeUntilHooked` 20~80 tick）」两段等待压到 **1 tick**，约两三个 tick 内就会咬钩。
  - **手动钓鱼也生效**（不像自动钓鱼只认主手持竿的自动抛竿）；与自动钓鱼同时开启即高速全自动钓鱼。
  - 值没有直接设 0：原版是在"递减到 ≤0"的那条分支里补放咬钩音效 / 粒子并把 `nibble` 设成 20~40 的，
    留 1 tick 让这条正常路径走完，收杆窗口、战利品、经验与附魔加成都与原版一致。
  - 每 tick 压一次：原版在 `timeUntilLured` 归零时会重新给 `timeUntilHooked` 赋 20~80，只压一次会被覆盖。
- **实现**：`mixin/FishingHookAccessor` 增加 `timeUntilLured` / `timeUntilHooked` 的 getter + setter
  （`nibble` 的访问器沿用上一版），`handler/AutoFish` 新增 `compressBiteTimers()`；
  `UtilityHandler` 的钓鱼分支改为「`auto_fish` 或 `instant_bite` 任一开启即每 tick 检查」。
- 附属：`StatsScreen` 补图标（热带鱼）、`zh_cn` / `en_us` 文案。
- 全流程在服务端，客户端无改动；无协议、无存档结构变化。

## [1.28.0] - 2026-10-04

### 🎣 新功能：自动钓鱼（`auto_fish`）

- **属性**：功能类新增开关型属性「自动钓鱼」`auto_fish`（投入 3 点解锁，与自动入库 / 连锁挖掘同档）。
- **行为**：主手拿着钓鱼竿时自动抛竿；鱼一咬钩**立刻**自动收杆，隔一小段随机延迟再抛出。
  - 所有收杆 / 抛竿都直接复用原版 `FishingRodItem#use`，因此战利品表、经验球、
    耐久损耗、`player.fishing` 清理、音效、统计与附魔（海之眷顾 / 诱饵）全部与原版手动钓鱼一致，
    也不会和其它钓鱼模组跑偏。
  - 咬钩判定用原版 `FishingHook#nibble`（咬钩窗口 20~40 tick，错过鱼就跑了）：
    新增 `mixin/FishingHookAccessor` 暴露该私有字段，窗口内每 tick 检查、命中即收杆。
  - 只在主手持竿时**自动抛竿**（副手挂竿不会在手里拿着别的物品时乱抛）；收杆则主手 / 副手有竿都认。
  - 正在使用物品（吃喝 / 拉弓）时不会打扰；旁观 / 死亡不触发。
- **配置**：新增 `AutoFish` 段 —— `autoFishRecastDelayMin`（默认 5）与 `autoFishRecastDelayMax`（默认 15），
  单位为 tick，实际每次重抛在区间内随机，避免机器式连抛。
- **附属改动**：`UtilityHandler` 每 tick 接入（咬钩窗口短，必须每 tick 检查）、
  `StatsScreen` 补图标（钓鱼竿）、`StatEventHandler#onPlayerLogout` 清理重抛记录、
  `zh_cn` / `en_us` 文案。
- 全流程在服务端完成（浮标 / 咬钩 / 战利品本来就只在服务端），客户端无需任何改动。
- 无协议、无存档结构变化（新增一个按玩家一次性写入的开关属性，点数照常扣）。

## [1.27.5] - 2026-10-04

### 🏊 修好「游泳速度」加点无效（改用 Mixin 放大输入加速度）

- **问题**：加了「游泳速度」后在水里游动完全没变快。
- **原因**（两层）：
  1. 旧实现是在服务端每 tick 把 `deltaMovement` 乘 `(1+bonus)`。玩家移动是客户端权威的，
     服务端改的速度客户端收不到 → 等于没写；
  2. 原版水里的横向速度来自 `LivingEntity#travel` 中写死的 `moveRelative(0.02F, 输入)`
     （0.02 是硬编码的输入加速度，Depth Strider 只在它上面插值），**没有任何属性通道**。
     而"把整条速度乘倍率"会和原版自己的衰减 `delta.multiply(0.8, 0.8, 0.8)` 互相累积：
     终端速度 = a·d/(1−d·(1+bonus))，倍率一过 1.25 分母就变负 → 指数发散（越游越快 / 抖动）。
- **修复**：新增 `mixin/EntityMoveRelativeMixin`，`@Redirect` 掉 `Entity#moveRelative` 里的
  `setDeltaMovement`，**只把本次加进去的那一项输入加速度**乘 `(1+bonus)`：
  终端速度 = a·(1+bonus)·d/(1−d)，与原版成正比 —— 线性、不发散、也保留其它来源的动量。
  仅对「玩家 + 在水中 + 非骑乘 + 非飞行」生效（客户端与服务端都生效，服务端模拟更贴近客户端）。
- **移除** `MobilityHandler#updateSwimSpeed`（旧的服务端改速度实现，既无效又可能让服务端模拟跑飞）；
  `getSwimSpeedMultiplier(stats)` 保留，供 Mixin 复用同一套数值口径。
- 已确认 mixin refmap 正确解析到 `Entity#m_19920_` / `Entity#m_20256_`。
- 无协议、无存档结构变化。

## [1.27.4] - 2026-10-04

### 🪜 修好「自动跨越」走不上一格台阶

- **问题**：开启 `auto_step`（自动跨越）后走到一格高的方块前仍然上不去 / 被卡住。
- **原因**：跨越高度取的是"刚好一格"的 `1.0`。这个值只在几何上刚好够 ——
  原版 `Entity#collide` 的抬升判定（`vec32.y < maxUpStep`、以及抬升后贴着台阶顶面再水平移动）
  在浮点误差、以及客户端/服务端各自模拟的微小差异下会差一点点，于是本地预判失败、上不去。
- **修复**：`auto_step` 的跨越高度由 `1.0` 改为 **`1.25`**（留出余量，能稳稳走上一格），
  并把 `0.6` / `1.25` 提成 `MobilityHandler.DEFAULT_STEP_HEIGHT` / `AUTO_STEP_HEIGHT`
  两个公共常量，客户端 `ClientEventHandler#syncStepHeight` 与服务端
  `MobilityHandler#updateStepHeight` 统一引用，避免两边再写漂。
- `step_height` 的百分比加成规则不变（在跨越高度上再乘 `1 + bonus`）。
- 纯数值调整，无协议、无存档结构变化。

## [1.27.3] - 2026-10-04

### 🕊️ 修好「飞行速度」加点无效 / 老存档会复利放大

- **原因**：飞行时客户端读取的是**本地** `abilities.flyingSpeed`
  （`Player#getFlyingSpeed()` → `LivingEntity#getFrictionInfluencedSpeed()`），
  而加成只写在服务端并靠能力包下发，在整合包里可能被其它模组覆盖或直接丢失 ——
  和之前「跳跃高度」「N 段跳」「自动跨越」是同一个坑。
- **客户端本地校正**：`ClientEventHandler` 新增 `syncFlySpeed()`，每 tick 把
  `abilities.flyingSpeed` 校正为 `0.05 × (1 + bonus)`；退款（bonus ≤ 0）时只回收本模组写过的值，
  不碰其它模组设置的速度。
- **顺带修掉一个隐藏 bug**：旧实现把「当前 abilities 值」当作基准缓存进玩家 NBT，
  而 `Abilities` 的 flySpeed 本身会被写进玩家存档 → 每次重新登录基准都被当成旧结果再乘一次，
  产生 0.05 → 0.25 → 1.25 … 的复利放大。现固定使用原版基准 0.05，并清理旧存档里的残留键
  `infinitestats.base_fly_speed`。
- 服务端 `MobilityHandler#updateFlightSpeed` 保留（保持两端能力值一致），但幅度以客户端为准。
- 无协议、无存档结构变化（仅移除一个旧 NBT 键）。

## [1.27.2] - 2026-10-04

### 💍 修好「无线终端放饰品栏检测不到」

RS / AE2 的无线终端放进 Curios 饰品栏后连不上网络（提示"未持有无线终端"），两处反射都写错了：

- **API 签名错**：Curios 5.x 是 `CuriosApi.getCuriosInventory(LivingEntity)`，参数类型不是 `Player`，
  代码却按 `getMethod("getCuriosInventory", Player.class)` 查找 → 抛 `NoSuchMethodException`，
  被 `catch (Throwable ignored)` 静默吞掉，函数永远返回空。
  现改为先按 `Player` 找、失败回落 `LivingEntity`（与 `BackpackNetworkBridge` 一致）。
- **取槽位方式错**：`ICurioStacksHandler.getStacks()` 在 Curios 5.x 返回 `IDynamicStackHandler`
  （实现 `IItemHandler`），既不是 `List` 也没有 `resolve()`，旧的 `asItemStackList()` 必然拿不到内容。
  现改为按 `IItemHandler.getSlots()/getStackInSlot()` 逐槽遍历，并保留旧版 `List` 回落分支。
- **涉及文件**：`RSNetworkBridge.findInCurios`、`AE2NetworkBridge.findInCurios`、
  `jei/PortableCraftingRecipeTransferHandler.hasStorageTerminal`（JEI 快速转移按钮的终端检测同因失效）。
- 新增 `LazyOptional` / `Optional` 双返回类型兼容；饰品栏扫描失败只 warn 一次，避免每秒扫描刷屏。
- 顺带生效：自动入库的"连网凭证保护"现在也能正确识别饰品栏里的无线终端（不会被存入网络）。
- 纯反射调用修复，无协议、无存档结构变化。

## [1.27.1] - 2026-10-04

### 🦗 修好「跳跃高度」加点无效的问题

跳跃高度加了 400% 却完全跳不高——因为加成只写在了服务端：

- **根因**：玩家移动是客户端权威的（服务端只做校验），`StatEventHandler.handleJumpBoost`
  在服务端 `ServerPlayer` 上改 `deltaMovement`，会被客户端发来的运动包直接覆盖，等于没改。
  服务端的 `LivingJumpEvent` 处理同理（该事件虽然在客户端也会触发，但原处理器过滤了 `ServerPlayer`）。
- **修复**：`ClientEventHandler` 新增 `applyJumpBoost()`，在客户端 `LocalPlayer` 上本地放大起跳速度，
  检测口径与服务端一致（上一tick在地面、本tick离地且 Y 速度为正 → 起跳瞬间），
  并同样用 `setPos` 补偿本 tick 已按原始速度跑完的位移。
  与 N 段跳 / 爬梯加速 / 自动跨越同属"客户端权威移动必须在本地改"的既有处理方式。
- 服务端逻辑保留不动（对假玩家 / 非权威场景仍有兜底意义）。
- 纯客户端运动修复，无协议、无存档结构变化。

## [1.27.0] - 2026-10-03

### 🖥️ 修好「模组列表 → Config」按钮（用 Cloth Config 重画配置界面）

之前 1.26.0 把 `ConfigScreenFactory` 注册删了，导致 Forge 的「Config」按钮一直是灰的——
而 Cloth Config 只是画控件的库，不会替本模组注册界面。这次重新接上：

- **新增 `client/ClothConfigScreen`**：遍历 `Config.SPEC` 自动把全部配置项摊成可编辑的表，按 `Experience / Leveling / AutoRevive / PassiveEffects / GUI / Compatibility / EMC / TimeAccel / Furnace / StoragePriority / Crafting / AutoDeposit` 分组，
  数字带范围限制、枚举（功能禁用模式）带翻译名、列表（开局禁用属性 / 存储优先级 / 入库黑名单）可逐项编辑，每项都带配置注释作为说明。
- **`ClientSetup.registerConfigScreen()` 重新注册 `ConfigScreenHandler.ConfigScreenFactory`**：只在客户端构造期注册
  （专用服务器不加载 `ClothConfigScreen`），`InfiniteStats` 构造器里 `FMLEnvironment.dist.isClient()` 分支调用。
- **`mods.toml` 声明 Cloth Config（cloth_config，可选，仅客户端）**：缺失时按钮点击无反应但不会崩。
- **依赖**：`build.gradle` 从 `maven.shedaniel.me` 拉取 `cloth-config-forge:11.1.106`（compileOnly + runtimeOnly）。
- 配置改动保存即写回 `config/infinitestats-common.toml`；`en_us.json` / `zh_cn.json` 补全了分类、字段、枚举的中文 / 英文翻译。
- 纯客户端配置入口调整，无存档结构变化。

## [1.26.0] - 2026-10-03

### 🎛️ 功能开关改为「作者预设 + 任务指令开放」，移除玩家自编辑界面

原方案是给玩家一个「功能开关」界面（模组列表 → Config），让玩家自己按需开关属性。这对整合包场景是反的：
开关本质是整合包作者的「进度设计」——作者决定开局哪些功能可用，玩家做任务后用奖励指令逐步解锁，玩家不该能自己改。
这次把它拆成「作者预设的开局禁用列表（配置项）+ 指令开放」，玩家不再有任何编辑入口。

- **移除玩家自编辑界面**：删除 `client/FeatureToggleScreen` 及其网络包 `FeatureTogglePacket`（客户端 → 服务器），
  协议版本 `13 → 14`。开关仍然**按玩家独立存储**（`PlayerStats.disabledStats` 不变），只是不再有界面去改它。
- **Config 按钮交还 Configured 配置界面**：删除 `ClientSetup.registerConfigScreen()`（不再注册 `ConfigScreenFactory`）。
  整合包自带的 Configured 会接管「Config」按钮，`config/infinitestats-common.toml` 的全部配置项都能直接查看 / 修改。
- **新增配置项 `GUI.disabledStats`**（`defineList`，属性 id 列表）：整合包作者预设的「开局默认禁用」属性。
  - 新玩家（以及未带标记的老存档）首次登录时，这些属性会**一次性**写入该玩家（`PlayerStats.applyDefaultDisabledStatsIfNeeded`，
    NBT 标记 `defaultDisabledApplied` 保证幂等）；之后作者改这份配置**不影响已有玩家**。
  - 配合既有的 `GUI.featureDisableMode`（keep / refund / hide）决定禁用后是整条失效还是仅隐藏。
- **指令 `/infstats feature` 保留**（要求权限等级 2，仍给管理员 / 任务奖励用）：`<id>` / `<id> on|off` / `all on|off` / `list` / `mode`。
  任务奖励里用 `/infstats feature <属性ID> on` 即可逐个开放功能；相关提示文案同步改为「尚未开放」。
- 纯客户端入口 + 配置 + 协议调整，无存档结构变化（`disabledStats` 序列化格式不变）。

## [1.25.2] - 2026-10-03

### ↩️ 撤掉属性面板的「功能开关」按钮，入口回到模组列表的「Config」

1.25.1 把「功能开关」入口塞进了属性面板标题栏，理由是「Config 按钮已交还给 Forge 原生配置界面」——
但 Forge 47.4.x 压根没有内建配置编辑界面（`ModListScreen` 只认模组自己注册的 `ConfigScreenFactory`），
所以交还之后 Config 按钮等于空的，属性面板上还多了一颗与面板无关的按钮。

- **删掉 `StatsScreen.addFeatureToggleEntry()` 及其调用**：属性面板标题栏右侧不再有「功能开关」按钮。
- **改回注册 `ConfigScreenFactory`**：新增 `ClientSetup.registerConfigScreen()`（只在客户端构造期注册，专用服务器不加载该类），
  模组列表 → 本模组 →「Config」重新打开 `FeatureToggleScreen`（按玩家开关，行为与 1.24.x 一致）。
- `FeatureToggleScreen` 类注释同步改为「入口 = 模组列表 → Config」。
- 纯客户端入口调整，网络包与协议版本不变（仍为 13）。
- 版本号 `1.25.1 → 1.25.2`。

## [1.25.1] - 2026-10-03

### 🔧 模组列表的「Config」按钮交还给 Forge 原生配置界面

1.25.0 把这颗按钮抢过来指向了自制的「功能开关」界面，结果配置菜单里只剩一份开关列表，
`config/infinitestats-common.toml` 的其它配置项（`featureDisableMode`、网络优先级、EMC 速率等）既看不到也改不了。这次把入口还原：

- **删掉 `ClientConfigEntry`**（不再注册 `ConfigScreenFactory`）：模组列表 → 本模组 →「Config」现在打开的是 Forge
  内建的配置编辑界面，`infinitestats-common.toml` 里的全部配置项都能直接查看 / 修改，改完即时生效。
- **「功能开关」入口移入属性面板**：`StatsScreen` 标题栏右侧新增「功能开关」按钮（悬停有说明），点开仍是原来的
  `FeatureToggleScreen`（按玩家开关，行为与之前一致），关闭后返回属性面板。
- 文案同步：`ModServerCommands` 里「『X』已在功能开关中关闭」的提示、`Config` 中 `featureDisableMode` 的注释，
  都从「模组列表 → Config」改为「属性面板 → 功能开关」。
- 新增语言键 `gui.infinitestats.nav.feature_toggle`（中英）。
- 纯客户端入口调整，协议版本不变（仍为 13）。
- 版本号 `1.25.0 → 1.25.1`。

## [1.25.0] - 2026-10-03

### ⚙️ 「关掉时」的执行模式改为**全局配置**（整合包作者用，不再按玩家存）

1.24.0 把三种模式做成了玩家自选，这次按整合包场景收回成**作者的一份全局设置**：

- **模式来源**：只读配置 `GUI.featureDisableMode`（`config/infinitestats-common.toml`，枚举 `keep` / `refund` / `hide`，默认 `keep`），改完即时生效。
  `FeatureDisableMode.current()` 是唯一取值入口，`PlayerStats` 判断失效 / 返还点数时直接查它 —— 不再往玩家数据里写任何模式字段。
- **玩家数据瘦身**：`PlayerStats` 去掉 `disableMode` 字段、NBT 存取、`copyFrom` 拷贝与快照字段；`SyncStatsPacket` 也去掉该字段（同步包回到「只带开关集合」）。
  开关本身**仍然按玩家存**（这是玩家整理自己面板用的）。
- **界面**：底栏那三个可点按钮撤掉，改成**只读一行**「关掉时（整合包设置）：整条失效·保留点数」，鼠标悬停显示该模式的完整含义与「由整合包配置决定，玩家不能改」。
- **指令**（仍在要求 OP 权限等级 2 的 `feature` 子树里，因为它是作者 / 管理员设置）：
  - `/infstats feature mode` —— 查看当前全局模式与配置文件路径；
  - `/infstats feature mode <keep|refund|hide>` —— 改写全局配置（`Config.SPEC.save()` 落盘），并**为全服在线玩家当场重算属性 + 同步**，反馈里会带上受影响人数。
- 顺带新增 `PlayerStats#refreshFeatureMode()`：模式变了之后让数值 / 开关缓存失效，避免读到旧值。
- 协议版本 `12 → 13`（同步包字段减少，两端必须同为 1.25.0）。
- 版本号 `1.24.1 → 1.25.0`。

## [1.24.1] - 2026-10-03

### 🔒 `/infstats feature` 指令改为需要 OP

- `/infstats feature`（含 `list` / `<属性ID>` / `all` / `mode`）现在要求**权限等级 2**（OP）：
  这是给整合包管理员用的命令，普通玩家不会被补全出来、也无法执行。
- 其余 `/infstats` 子命令（`craft` / `furnace` / `anvil` / `enderchest` / `smithing` / `wp` / `crossdim`）**不受影响** ——
  那几条本来就是玩家操作自己的随身功能，仍然人人可用。
- 权限只加在**指令**上：功能开关界面（模组列表 → Config）仍对玩家开放，因为玩家需要能整理自己的面板。
  若希望连界面也一并锁给 OP，说一声即可。
- 界面浮窗与 README 的措辞同步为「管理员也可用 `/infstats feature mode`」。
- 版本号 `1.24.0 → 1.24.1`。

## [1.24.0] - 2026-10-03

### 🎛️ 功能开关：三种「关掉时」模式，玩家自选（配置项管默认）

> 注：1.25.0 起改为**全局配置**（不再按玩家存），下方「按玩家存」的描述已作废，详见 1.25.0。


关闭某项时不再只有一种行为，改成三选一（**按玩家存**，与开关本身一样随存档同步）：

| 模式 | 效果 | 已投入的点数 |
|---|---|---|
| `keep` 失效·保留点数（默认） | 整条失效：数值按 0 计、开关效果与 `/infstats` 指令停用 | 保留，重新开启立刻恢复 |
| `refund` 失效·返还点数 | 同上 | **全额退回**可用点数；重新开启从 0 点开始 |
| `hide` 只隐藏（不返还） | **效果与指令照常生效**，仅面板隐藏 + 禁止加点 | 保留 |

- **实现**：新增 `stats/FeatureDisableMode`（枚举）与 `PlayerStats#disableMode`；把「面板层面」和「效果层面」拆成两个判断 ——
  - `isStatDisabled()`：是否被关闭（三种模式下面板都隐藏、都禁止加点）；
  - `isStatInactive()`：是否**整条失效**（`hide` 模式恒为 `false`，只有它会进数值缓存与 `isToggleActive()`）。
  这样「只隐藏」不会误伤已投入的加成，而另外两种模式依旧整条归零。
- **返还点数**：`setStatDisabled(id, true)` / 批量关闭在 `refund` 模式下会调用 `resetStat()` 全额退回（含负投入按绝对值返还），并立即 `applyAllAttributes()` + 同步；**切换模式只影响之后的关闭操作**，已经关掉的想拿回点数就先开启再关闭。
- **界面**：底栏新增「关掉时：」一行，三个按钮直接点选、当前模式高亮，浮窗写好各自含义；执行模式随玩家数据同步。
- **指令**：`/infstats feature mode`（查看）/ `/infstats feature mode <keep|refund|hide>`（切换，Tab 补全）。
- **配置项**：新增 `GUI.featureDisableMode`（枚举，默认 `keep`）—— 只作为**新玩家**的初始值，整合包作者可改默认，玩家仍能各自修改、互不影响。
- 协议版本 `11 → 12`（新增 `FeatureModePacket`，且同步包多带一个模式字段）；版本号 `1.23.0 → 1.24.0`。

## [1.23.0] - 2026-10-03

### 🎛️ 功能开关：关掉即「整条失效」+ 新增 `/infstats feature` 指令

- **语义调整（覆盖 1.22.0 的行为）**：关闭不再只是「隐藏 + 停用开关」，而是**整条失效**：
  - 数值类属性（攻击力 / 生命上限 / 挖掘速度…）关闭后按 **0** 计 —— `PlayerStats#ensureCacheValid()` 构建数值缓存时跳过被关闭的属性，所以所有读 `getStatValue()` 的效果与属性修饰符（`AttributeHandler`）一并归零；
  - 开关型功能依旧同时停用效果与指令（`isToggleActive()` 对关闭项返回 `false`）；
  - **投入的点数与可用点数都不变**（`allocatedPoints` 原样保留），重新开启立刻恢复；
  - 开关变化会**当场重算属性**（`AttributeHandler.applyAllAttributes`），不必等下一次加点或重登；其余按 tick 核对的效果最多 1 秒内收敛。
  - 顺带：全局唯一按「等级」取量的效果是「自动修理」，已改用新增的 `PlayerStats#getEffectiveStatLevel()` —— 关闭 `repair_amount` 时退化为最小修理量。
- **新增指令**（按玩家，与 Config 界面完全等价，不需要 OP）：

  | 指令 | 作用 |
  |---|---|
  | `/infstats feature` 或 `/infstats feature list` | 列出当前已关闭的功能 |
  | `/infstats feature <属性ID>` | 切换该功能（开 ↔ 关） |
  | `/infstats feature <属性ID> on\|off` | 明确开启 / 关闭 |
  | `/infstats feature all on\|off` | 全部内置属性一次性开启 / 关闭 |

  - `<属性ID>` 支持 **Tab 补全**（87 条内置 + 外部发现的属性），未知 ID 会给出「可用 Tab 补全」的提示。
- 界面浮窗里的说明同步改成「整条失效」并附上指令用法。
- 版本号 `1.22.0 → 1.23.0`。

## [1.22.0] - 2026-10-03

### 🎛️ 新增「功能开关」：87 条内置属性可以逐条关掉（按玩家独立）

- **入口**：暂停菜单 → 模组 → 选中本模组 → **Config** 按钮（走 Forge 的 `ConfigScreenHandler.ConfigScreenFactory` 扩展点；标题界面也能打开，但没有玩家时只能查看，界面会提示「进入世界后才能修改」）。
- **关闭后的表现**：
  - 属性面板**不再显示**该条目（收藏视图同样过滤），也无法继续加点 —— 服务端也会拒绝 `ModifyStatPacket` 的加点请求（返还点数仍允许，避免卡住已投入的点数）；
  - 开关型功能的**效果与指令一并停用**：`isToggleActive()` 对被关闭的属性一律返回 `false`，因此所有开关效果、`/infstats craft|furnace|anvil|enderchest|smithing|wp|crossdim` 与面板里的 GUI 入口（`CraftingOpenPacket` 等）自动全部拦住，不需要在几十个使用点各写一遍判断；
  - 面板页脚的对应入口（传送点 / 跨维度 / 工作台 / 熔炉 / 铁砧 / 末影箱 / 锻造台）一起隐藏并自动收拢排布。
- **点数与效果**：已投入的点数**保留**，重新开启即恢复。因此这是一个**个人功能总闸**（隐藏 + 停用），不是销毁数据。
  > 注：1.23.0 起语义升级为「关掉即整条失效」（数值按 0 计），并新增 `/infstats feature` 指令。
- **按玩家存，不是全局配置**：开关集合存在 `PlayerStats` 里（NBT 落盘 + `SyncStatsPacket` 同步 + 死亡克隆），多人服务器里每个人各有自己的面板。
- **界面**（新 `client/FeatureToggleScreen`）：分类筛选 + 搜索（属性名 / ID / 说明，装 JustEnoughCharacters 支持拼音）+ 滚动 + 行内一键切换 + 底栏「全部开启 / 全部关闭」（作用于当前筛选结果）+ 每行显示已投点数。视觉沿用 `EditorUi` 规范，与效果过滤器 / 物品编辑器一致。
- 指令的拒绝提示会区分原因：被功能开关关掉时提示「『随身工作台』已在「功能开关」中关闭（模组列表 → 本模组 → Config）」，未加点时仍是原来的「未激活…」。
- 协议版本 `10 → 11`（新增 `FeatureTogglePacket`），客户端与服务端版本不一致时会在连接阶段直接报错，而不是无声不同步。
- 版本号 `1.21.1 → 1.22.0`。

## [1.21.1] - 2026-10-03

### 🧰 随身工作台界面重做：改用随身熔炉那套布局

- **问题**：原布局把倍率 ± 按钮塞在 176 宽的方块界面右上角（x=158 处两个 16×16 小按钮），位置又窄又不显眼；实际渲染时整排按钮（含「成品去向」「优先级」）完全不可见。
- **现在**：界面加宽到 **300 × 166**，左侧保留原版合成台槽位（3×3 合成格 + 结果槽 + 背包 + 快捷栏），右侧一整列功能按钮 —— 与随身熔炉同一列几何（起点 x=184、宽 106）：
  - 倍率：`-` / `×N` / `+`（24 宽，与熔炉加速 ± 同款排布），下方一行「每级消耗 N 点」；
  - 「成品去向：背包 / 存储空间」（106 宽，不再用 20 宽的「包」「储」缩写）；
  - 「存储优先级」；
  - 底部一行 JEI 提示（配方界面按 `+` 一键放入）。
- **面板改为程序化绘制**：外框 / 白描边 / 底板 / 中缝分隔线 / 槽位全部自绘（合成箭头取自模组自带的合成台贴图），与随身熔炉完全一致 —— 不再贴原版 GUI 贴图，也就不会再因为贴图裁切或资源包替换而看不见内容。
- **顺手加固**：`init()` 改为**先创建控件、再做** JEI 网络物品预加载，且预加载包了 `try/catch` —— 即使 JEI 缺失或网络未就绪，也不会让整列按钮消失。
- **与熔炉一致的按钮状态**：`+` 在点数不足时置灰并提示「需要 N 点可用属性点」，`-` 在倍率为 1 时置灰；倍率与可升级状态都走容器数据同步（同随身熔炉 `canUpgrade` 的机制），倍率数值也改为读取同步值，显示与实际扣费口径一致。
- 版本号 `1.21.0 → 1.21.1`。

## [1.21.0] - 2026-10-03

### 🐄 新增 2 个属性（内置属性 85 → 87）

| 属性 | 分类 | 形式 | 说明 |
|---|---|---|---|
| `breed_no_cooldown` 繁殖无冷却 | 功能 | 开关（3 点） | 自己喂食繁殖出的动物不再进入 5 分钟冷却，可立刻再次繁殖 |
| `instant_grow` 一键长大 | 功能 | 开关（2 点） | 手持该动物的饲料右键幼年动物，一次喂食即可长大 |

- **`breed_no_cooldown`**（新增 `mixin/AnimalMixin`）：原版在 `Animal#spawnChildFromBreeding` 里把两只亲本的年龄写成 6000（5 分钟），而 `Animal#mobInteract` 要求年龄为 0 才能喂食进入恋爱状态 —— 这就是「冷却」。`BabyEntitySpawnEvent` 是在写年龄**之前**触发的，事件返回后原版照样写 6000，所以只能 Mixin：注入 `TAIL`（方法最后一个 return）把年龄改回 0。
  - **按喂食者判定**：用 `Animal#getLoveCause()`（喂食时记录、`resetLove()` 不会清除），两只亲本各自检查「喂它的人」是否解锁，混养时不会互相蹭效果；
  - **不干扰别人的拦截**：TAIL 只覆盖正常繁殖路径，若事件被其它模组取消（原版走早退分支）我们完全不插手。
- **`instant_grow`**（`StatEventHandler#onEntityInteract`）：走 `PlayerInteractEvent.EntityInteract` —— 它在 `Player#interactOn` 里、原版 `mobInteract` **之前**触发且可取消，因此不会出现「原版先长 10%、我们再补满」的双重结算。判定为「目标是幼年 `Animal` 且手持物是它的饲料」，消耗 1 个后按剩余年龄一次性 `ageUp`（保留原版长大粒子），两端同步预测。

### 🎛️ 存储优先级：按功能分开设置（各界面各一个入口）

- **改动**：原先只有「自动入库」能调优先级，工作台 / 熔炉 / 成品仓统统走配置 `NetworkPriority.networkPriority`。现在**每个用到存储网络的功能各有一份独立优先级**（默认仍沿用配置，没调整过的功能行为与旧版完全一致）：

  | 功能 | 入口 | 作用域键 |
  |---|---|---|
  | 自动入库 | 主面板「自动入库：…」按钮 | `auto_deposit` |
  | 随身工作台（取料 / 补料 / 退回 / 成品入库） | 工作台界面右侧「优先级」 | `crafting` |
  | 随身熔炉（抽矿物 / 抽燃料 / 成品入库） | 熔炉界面「网络」栏「优先级」 | `furnace` |
  | 成品仓（出库到网络） | 成品仓界面右上角「优先级」 | `product_buffer` |

- **界面**：4 个入口共用同一个 `client/NetworkPriorityScreen`（按作用域取标题与说明），逐行 **▲ / ▼** 只与相邻一位交换，「重置为默认」清空自定义、回落到配置顺序；行内标注该存储**是否已安装**。
- **数据与同步**：`PlayerStats` 里单一的 `autoDepositPriority` 换成 `networkPriorities`（作用域 → 顺序表），NBT 键改为 `networkPriorities` —— **老存档的 `autoDepositPriority` 会在读取时自动迁移**成自动入库作用域，设置不丢；`SyncStatsPacket`/快照同步改为携带整张表。
- **协议**：`SetDepositPriorityPacket` → `SetNetworkPriorityPacket`（多带一个作用域字段），`PROTOCOL_VERSION` `9 → 10`。
- **说明**：`RequestNetworkItemsPacket`（网络物品列表，供 JEI 判断哪些配方能从网络取材）**不需要优先级** —— 它取的是所有可用网络的**并集**，顺序不影响结果，因此保持读配置顺序、也不给入口按钮。

### 🎛️ 存储优先级改为独立界面（可逐个调整）

- **改动**：原先靠面板那行按钮「点击 = 把队首移到末尾 / Shift+点击 = 把队尾移到队首」循环调整 —— 想排到目标顺序要点很多次，中途也看不出每一步是谁越过了谁。现在点击按钮打开新的 **`client/DepositPriorityScreen`**：
  - 每行一个存储，右侧 **▲ / ▼** 只与相邻一位交换（一次一位，顺序一眼可见，边界按钮自动禁用）；
  - 行内标注该存储**是否已安装**（`RSNetworkBridge#isRSLoaded` 等客户端查询），未安装的在浮窗里补一句「排在这里不会生效」；
  - 底部「**重置为默认**」= 给服务端发空列表，`normalizeDepositOrder` 会按配置 `NetworkPriority.networkPriority` 补全成完整顺序；「完成」返回属性面板（沿用入库过滤界面的「返回父界面」模式），回来后按钮标签自动刷新。
- **协议未变**：提交仍走既有 `SetDepositPriorityPacket`，服务端归一化与 `syncToClient` 逻辑复用 —— 没有新增数据包、协议版本不变。
- **作用范围不变**：该顺序依旧只作用于「自动入库」的写入顺序（随身工作台 / 熔炉 / 成品仓继续按配置顺序），本次未做统一。
- 语言文件新增 8 条界面键，并把主面板按钮的浮窗从「点击按钮：把队首存储移到末尾…」改成「点击打开优先级界面…」。

### 🧹 属性面板：整理属性排列顺序

- **面板顺序由 `StatType.registerAllStats()` 的数组顺序决定**（`StatsScreen` 先按 `StatCategory.values()` 分页，再按数组顺序列出，之后没有再做排序）。此前新属性一律就地追加，于是分类互相交错：`multi_shot`（攻击）被甩在 30 多条功能属性之后、`climb_speed` / `multi_jump`（机动）被夹在功能属性中间、功能分类本身也被拆成前后两段。
- **现在的排列**：分类连续存放 + 同类按主题成组（仅调整顺序，不额外加注释）——
  - 攻击：输出 → 暴击 → 穿透/击退 → 吸血反伤 → 处决真伤 → 范围 → 弓弩
  - 防御：基础属性 → 受击反应（格挡 / 闪避 / 护盾 / 复活）→ 免疫
  - 机动：移动 → 跳跃 → 跨越 → 飞行 → 攀爬 → 免摔
  - 功能：收获掉落 → 拾取入库 → 生产 → 合成修理 → 使用辅助 → 战斗辅助 → 生存 → 时间 → 传送 → 随身 → 交易铁砧 → 联动
- **条目本身一字未改**（数值、解锁点数、行为、注释全部原样搬运，仅调换位置）；README 的四类清单同步为同一顺序。
- **无存档影响**：属性点数按 id 存储，这次只改排列，不涉及任何 id 的增删或改名。

### 🗑️ 移除 `crit_projectile`（弹射物暴击）

- **原因**：收益不明显 —— 弹射物本来就极少吃到满蓄力判定，独立掷暴击的体感提升有限，却要在伤害管线里长期多养一条分支。
- **行为回退**：伤害管线恢复为**单一暴击分支**（近战与弹射物共用 `player.getAttackStrengthScale(0.5f) > 0.9f` 的蓄力判定 → `AttackHandler.calculateCritMultiplier`）；同时删掉属性定义、界面图标与中英文本地化键。`multi_shot`（多重射击）不受影响。
- **老存档不亏点数**：`crit_projectile` 已加入 `PlayerStats#LEGACY_REMOVED_STATS`（由原 `LEGACY_MAGIC_STATS` 改名并扩充）—— 属性定义删掉后 `StatType.fromId` 会返回 null、条目会被静默丢弃，所以必须在读 NBT 时显式按绝对值退还到可用点数，否则投过点的玩家会凭空少 5 点。
- 内置属性 `87 → 86`（攻击 `18 → 17`）。

### 🩹 修复：交易即刻补货会把村民的「等级 + 经验进度条」抹掉

- **现象**：开着「交易即刻补货」与村民交易时，界面标题里的「等级 N」和经验进度条消失（关掉重开界面又出现）。
- **原因**：补货后重推报价时用了菜单上的同名 getter —— `MerchantMenu#getTraderLevel()`、`showProgressBar()`、`canRestock()` 这三项**只在客户端**由 `ClientPacketListener#handleMerchantOffers` 写入（`setMerchantLevel` / `setShowProgressBar` / `setCanRestock`），服务端恒为 `0 / false / false`。于是下发的包把客户端的 `merchantLevel` 覆盖成 0、进度条开关覆盖成 false，`MerchantScreen#renderLabels` 的 `i > 0 && showProgressBar()` 判定直接不成立。
- **修复**：补货重推时改用**商人实体**的值，与原版 `Merchant#openTradingScreen` 完全对齐 —— 等级取 `Villager#getVillagerData().getLevel()`（流浪商人固定 1），经验取 `Merchant#getVillagerXp()`，进度条与补货开关取 `Merchant#showProgressBar()` / `Merchant#canRestock()`（后者对流浪商人是 false，之前硬编码 true 也会让"可补货"提示出现得不对）。
- **顺带**：新增 `mixin/MerchantMenuAccessor` 暴露 `MerchantMenu#trader`（private final），服务端才能拿到实体。
- **核查结论**：村民的经验值本身没有丢（`uses` 重置与 `MerchantOffer#rewardExp` 无关，`increaseUses` 只做 `++uses`），受影响的只是客户端的等级 / 进度条显示。

### 💰 村民交易折扣：改为与声望 / 英雄效果折扣叠加

- **改动**：`trade_discount` 原先按「取更优惠者」写入 `specialPriceDiff`（只保证至少减这么多，不与声望 / 英雄效果叠加），现在改为**叠加** —— 原版在 `Villager#startTrading` 里按声望（`-floor(声望 × priceMultiplier)`）与英雄效果（`-(30% + 6.25% × 等级) × 基础数量`）`addToSpecialPriceDiff`，之后本模组再把折扣加在同一份 diff 上。
- **为什么不能无脑叠加**：`MerchantOffer#specialPriceDiff` **会随商人一起存档**（`write()` 里的 `specialPrice` 字段），而只有村民会在 `stopTrading → resetSpecialPrices()` 里清零，流浪商人（`AbstractVillager#stopTrading` 只解除交易玩家）不会 —— 每开一次界面加一次，价格会一路跌到 1 并被永久保存。
- **做法**（`StatEventHandler#applyTradeDiscount` + `TRADE_DISCOUNT_STATE` 记账）：每个报价记下「本模组写进去的折扣」与「写入后的 diff」，每次打开界面时先还原出原版此刻的价值：
  - 值没变（流浪商人）或变得更便宜（原版又在我们的基础上加了折扣）→ 减掉我们的贡献；
  - 变贵了（村民关界面时清零并按声望重算）→ 当前值就是原版值。
  反复开关界面价格稳定；玩家把点数退掉（`rate == 0`）后也会把写进流浪商人报价里的折扣收回来。

### ⚒️ 随身铁砧：取消「过于昂贵」门槛，经验消耗封顶 50 级

- **效果**：随身铁砧（`portable_anvil`）不再受原版「≥40 级 = 过于昂贵」限制，任何操作都能完成；**经验最多只扣 50 级**，界面显示与实际扣费都是封顶后的值。
- **为什么事件做不到**：`AnvilUpdateEvent#setCost` 在不设 `output` 时会被 `ForgeHooks.onAnvilChange` 直接忽略（只有 `e.getOutput()` 非空才会 `setMaximumCost`），而且事件在附魔消耗计算**之前**触发，参数只是基础修理费；「过于昂贵」的门槛 40 又是 `AnvilMenu#createResult` 里的硬编码常量，没有任何事件可拦截。因此这一版引入 **Mixin**。
- **实现**：新增 `mixin/AnvilMenuMixin`
  - `@ModifyArg` 挂在 `createResult` 里最终写消耗的 `DataSlot.set(j + i)`（该方法内第 6 次 `DataSlot.set`，序号 5）：先施加 `anvil_cost` 减免，再对随身铁砧取 `min(50)`；
  - `@ModifyConstant` 把门槛常量 40 在随身铁砧上换成 `Integer.MAX_VALUE`（`createResult` 内字面量 40 共 3 处：栈堆叠 `i = 40`、改名钳制、过于昂贵门槛，取最后一处，序号 2）。
  - 两处序号都用 `javap` 反编译字节码核对过（`bipush 40` × 3、`DataSlot.set` × 7），不靠猜。
  - 客户端 `mixin/AnvilScreenMixin` 同步放开界面文案（`renderLabels` 内唯一的 40），否则会出现「界面写着过于昂贵、其实能点」。
- **如何识别随身铁砧**：新增 `PortableAnvilMenu` + `ModMenuTypes.PORTABLE_ANVIL_MENU`，客户端注册原版 `AnvilScreen`。客户端菜单是**由菜单类型工厂创建**的，沿用 `MenuType.ANVIL` 时客户端拿到的是普通 `AnvilMenu`，无从判断来源；改独立类型后两端都能用 `instanceof` 精确识别（`getType()` 需覆写，否则 `NetworkHooks.openScreen` 会把父类构造器写死的 `MenuType.ANVIL` 下发下去）。
- **顺带修复**：`anvil_cost`（铁砧经验减免）此前**完全不生效**，原因就是上面那条 `setCost` 语义坑。现在由 Mixin 直接改写最终消耗，且减免在门槛判定之前生效（保持「同时降低过于昂贵门槛」的原有承诺），界面与实际扣费必然一致。已删除失效的 `StatEventHandler#onAnvilUpdate`。

### 🩹 修复：与 Apotheosis 的 Mixin 撞车导致启动崩溃

- **现象**：装了 Apotheosis 的整合包启动即崩 —— `latest.log` 里先是 `@ModifyConstant conflict. Skipping infinitestats.mixins.json:AnvilMenuMixin`，紧接着 `Critical injection failure: Constant modifier method infinitestats$liftPortableGate(I)I ... (0/1) succeeded`。
- **原因**：Apotheosis 的 `apotheosis.mixins.json:AnvilMenuMixin#apoth_removeLevelCap` 与我一样是在 `AnvilMenu#createResult` 里**同一个常量 40 上做 `@ModifyConstant`**（它也在去掉「过于昂贵」上限）。同优先级（1000）下 Mixin 判为冲突并跳过后者的注入，而配置里的 `defaultRequire = 1` 把「0 个注入点成功」升级成致命错误；又因为 Apotheosis 的 coremod 在启动阶段就会触达 `AnvilMenu`，所以是**启动崩**而不是开铁砧才崩。
- **修复**：门槛那处改用 `@Redirect` 拦门槛比较里的 `DataSlot.get()`（`createResult` 内第 2 次，已用 `javap` 核对序号），随身铁砧返回 `Integer.MIN_VALUE` —— 注入目标与任何 `@ModifyConstant` 都不同，可与 Apotheosis 共存；并且无论门槛被原版还是被别的模组改成多少，比较恒为 false。消耗封顶 / 显示 / 扣费逻辑不变。
- **顺带加固**：客户端 `AnvilScreenMixin` 的 `@ModifyConstant` 加了 `require = 0` —— 它只影响界面文案，万一将来也撞车就按 WARN 跳过（最坏是继续显示「过于昂贵」），不再让游戏崩在启动阶段。

### 🔧 构建

- 接入 **MixinGradle 0.7.38**：`id 'org.spongepowered.mixin'` + `annotationProcessor 'org.spongepowered:mixin:0.8.5:processor'`，`infinitestats.mixins.json` 与 `infinitestats.refmap.json` 会自动打进 jar（清单已含 `MixinConfigs`）。
- dev 运行（`runClient` / `runServer` / `runGameTestServer`）追加 `mixin.env.remapRefMap` 与 `refMapRemappingFile`，否则 refmap 里的 SRG 名在开发环境对不上号。
- 版本号 `1.20.1 → 1.21.0`。

## [1.20.1] - 2026-10-03

### 🔍 修复：Connector 系整合包里「距离」属性完全失效

- **现象**：装了 `reach-entity-attributes` 的整合包里，`reach` / `entity_reach` 加多少点都没有任何变化。
- **原因**：这两个属性原本只写 Forge 的 `forge:block_reach` / `forge:entity_reach`。而 Connector 系整合包（Sinytra Connector + `ConnectorExtras`）会用 **jarJar** 塞进 Fabric 侧的 `reach-entity-attributes`（modId `reach_entity_attributes`，属性为 `reach-entity-attributes:reach` / `reach-entity-attributes:attack_range`），它的 mixin 接管了原版交互距离与攻击距离的读取，Forge 那对属性加了也没人消费。
- **排查要点**：该库**不是 `mods/` 里的独立 jar**，而是嵌套在 `ConnectorExtras-<版本>.jar` 的 `META-INF/jarjar/` 内 —— 按文件名永远搜不到，但 Forge 仍把它当作独立模组加载（因此游戏内模组列表里能看到）。
- **修复**：
  - `StatType.Builder#attribute` 支持镜像写入：`attribute(String 主属性, String... 镜像属性)`；
  - `AttributeHandler` 改为遍历 `StatType#resolveAttributes()`（主属性 + 镜像的注册名 → Attribute），把同一份点数写进所有**已注册**的目标属性，模组不存在时自动跳过、不报错；
  - `reach` → `forge:block_reach` + `reach-entity-attributes:reach`；`entity_reach` → `forge:entity_reach` + `reach-entity-attributes:attack_range`。
- **兼容性**：
  - 主属性沿用原 UUID，旧存档升级后不会残留孤儿修改器；镜像属性使用 `statId@属性名` 派生的新 UUID。
  - 镜像属性一并加入 `COVERED_ATTRIBUTES`，不会再被动态发现成两条独立的外部属性；若此前把点数投在自动发现的 `attr.reach-entity-attributes.*` 上，这些条目会从面板消失（点数保留但不再生效），可用面板的「**全部重置**」退还。
- 版本号 `1.20.0 → 1.20.1`。

## [1.20.0] - 2026-10-03

### ✨ 新增 9 个属性（内置属性 76 → 85）

| 属性 | 分类 | 形式 | 说明 |
|---|---|---|---|
| `crit_projectile` 弹射物暴击 | 攻击 | 开关（5 点） | 弹射物独立掷暴击，不再受近战蓄力限制 |
| `multi_shot` 多重射击 | 攻击 | 每点 +1 支（上限 8） | 每次射击多射出 N 支箭 |
| `climb_speed` 爬梯加速 | 机动 | 每点 +20% | 梯子 / 藤蔓 / 脚手架上升更快 |
| `multi_jump` N 段跳 | 机动 | 每点 +1 段（上限 10） | 空中可再跳 N 次 |
| `trade_restock` 交易即刻补货 | 功能 | 开关（3 点） | 交易后立刻补货，可连续交易 |
| `anvil_cost` 铁砧经验减免 | 功能 | 每点 -5%（上限 -90%） | 铁砧等级消耗降低 |
| `keep_xp` 死亡不掉经验 | 功能 | 开关（3 点） | 保留经验等级与经验条 |
| `portable_ender_chest` 随身末影箱 | 功能 | 开关（1 点） | 随时打开自己的末影箱 |
| `portable_smithing` 随身锻造台 | 功能 | 开关（1 点） | 随时打开锻造台 |

### 🏹 弓弩流派补全：弹射物暴击 + 多重射击

- **`crit_projectile`**：伤害管线里的暴击原本是一条共享路径 —— `isFullAttack = player.getAttackStrengthScale(0.5f) > 0.9f` 成立时，近战与弹射物共用一个 `1.5 + crit_damage` 倍率。解锁本属性后，**弹射物走独立分支**：用同一套 `crit_chance` / `crit_damage` 自己掷一次，且**不再依赖蓄力**；未解锁时完全维持原行为，老玩家不会被削弱。
- **`multi_shot`**：在既有的 `EntityJoinLevelEvent`（原本负责收紧虚拟弩箭的拾取权限）里挂钩 ——
  - 用 `arrow.saveWithoutId(tag)` + 补 `id` 标签 + `EntityType.create(tag, level)` **复制首发箭矢**，而不是自己 `new Arrow`，因此光谱箭、模组自定义箭矢都能被正确复制；
  - 原版箭矢 NBT **不保存发射者**，所以复制后必须手动 `Projectile#setOwner`，否则伤害无法归因到玩家，`life_steal` / `projectile_damage` 等一整套攻击加成都会失效；
  - 复制体一律 `Pickup.CREATIVE_ONLY`（它们没消耗背包资源，落地被捡回＝凭空刷箭），并写入实体持久化 NBT 标记避免复制体再触发一次复制；
  - 用「玩家 UUID → 本 tick 是否已补箭」的表做防重：原版多重射击一次会生成 3 支箭，只有首发那一支负责补箭，不会 3 倍膨胀。

### 🪜 机动补全：N 段跳 + 爬梯加速（客户端实现）

- **为什么放在客户端**：玩家的移动是客户端权威的（服务端只做位置校验），而这两条都没有可用的属性通道 —— 服务端改 `setDeltaMovement` 只会改到服务端自己那份速度，玩家本人感觉不到。所以实现在 `ClientEventHandler#onClientTick`，与已有 `syncStepHeight`（抬腿高度）同一套路数。
- **`multi_jump`**：取跳跃键的**按下边沿**（按住不放不算），并额外要求「离地 ≥ 6 tick」——否则从地面起跳的那一次按击会被同一次边沿误判成空中跳，白白吃掉一段。落地 / 入水 / 上梯子清零；触发时补云雾粒子与 `SLIME_JUMP_SMALL` 音效，并清零 `fallDistance`，避免"空中跳一下反而摔死"。
- **`climb_speed`**：原版爬梯的竖直速度写死为 `0.2`，这里只在 `0.02 < y ≤ 0.21` 的窗口内按比例放大，因此不会干扰跳跃 / 漂浮等其它竖直速度来源。

### 💰 三项"省钱保命"属性

- **`anvil_cost`**：接 Forge 的 `AnvilUpdateEvent`（由 `AnvilMenu#createResult` 触发）改 `cost`。之所以能保证"界面显示 = 实际扣费"，是因为客户端显示的是服务端通过菜单 data 槽同步下来的真实消耗。
- **`trade_restock`**：玩家 tick 里检测 `containerMenu instanceof MerchantMenu`，把 `uses > 0` 的报价 `resetUses()` 后重新 `sendMerchantOffers(...)` 推给客户端（否则界面仍显示售罄）。**刻意不走 `Villager#restock()`**：那条路会顺手 `updateDemand()`，反复调用会把需求加价越补越贵。
- **`keep_xp`**：Forge 的 `LivingExperienceDropEvent` 把掉落量改成 0（经验球不生成），等级/经验条留存在老实体上，由 `PlayerEvent.Clone` 原样搬给新实体。刚好补上 `keep_inventory` 留下的"只保物品、经验照扣"缺口。

### 🎒 随身末影箱 / 随身锻造台

- 复用 `PortableAnvil` 的同一套思路（`SimpleMenuProvider` + 原版菜单，客户端自动套用原版界面）：末影箱用 `ChestMenu.threeRows(id, inv, player.getEnderChestInventory())`（与原版末影箱共用同一份库存），锻造台用 `SmithingMenu(id, inv, ContainerLevelAccess.NULL)`（`NULL` 让 `isValidBlock` 的方块校验失效，从而不依赖世界里的锻造台）。
- 新增 `crafting/PortableGuis`；入口为属性面板页脚新增的「末影箱」/「锻造台」两个按钮（放在上次移除「传送点管理」后空出来的位置），以及新增命令 `/infstats enderchest` / `/infstats smithing`。

### 🔢 其它

- 协议版本 `8 → 9`（新增 `EnderChestOpenPacket` / `SmithingOpenPacket`）。
- 版本号 `1.19.1 → 1.20.0`；README 同步（属性总数、四类属性清单、命令表、新增功能说明章节）；中英文语言文件补齐 18 条属性键与 4 条界面/提示键。

## [1.19.1] - 2026-10-02

### 🧹 属性面板按钮整理

- **移除重复的「传送点管理」按钮**：页脚控制行里的那个按钮（`screen.infinitestats.waypoint`）与页脚导航行的「传送」（`gui.infinitestats.nav.waypoint`）功能完全重复，现只保留导航行里的入口；`Y` 键与 `/infstats wp` 命令不受影响。
- **去掉重名的「过滤」按钮**：1.19.0 新增的自动入库过滤入口原本也叫「过滤」，与页脚导航行里已有的**效果过滤**按钮（`nav.filter` → `DebuffFilterScreen`）重名，容易点错。现改名为「**入库过滤**」（英文 `Deposit Filter`），并把名单明细从按钮文字移到浮窗（写明当前模式与名单条目数），避免按钮文字过长在英文环境下溢出。
- **宽度重新分配**：搜索行内自动入库优先级按钮 `200 → 176`、入库过滤按钮 `72 → 96`，中文与英文标签都能完整显示。
- **影响范围**：`client/StatsScreen`（`addResetButtons` 移除 wpBtn、`repositionSearch` 调整宽度、`updateDepositLabel` 改为动态浮窗）；中英文语言文件更新 `gui.infinitestats.deposit.filter` / `filter_tooltip`，新增 `deposit.mode_whitelist` / `mode_blacklist`，移除已无引用的 `deposit.filter_on`。

## [1.19.0] - 2026-10-02

### 🎯 「自动入库」支持按物品白 / 黑名单过滤

- **效果**：自动入库不再只能「全存」或「全不存」，而是可以精确指定哪些物品才进存储网络。
  - **白名单** = 只入库名单内的物品；**黑名单** = 名单内的物品不入库；
  - **名单为空时不做任何限制** —— 若把「白名单为空」理解为「什么都不入库」，玩家一键清空列表就会彻底失去自动入库能力，属易误操作的高代价行为，因此统一按放行处理。
- **入口在主面板**：自动入库优先级按钮右侧新增「过滤」按钮（有名单时显示为「过滤(N)」），点击打开专属过滤界面；未解锁 `auto_deposit` 时会提示先加点。
- **过滤界面（新增 `client/AutoDepositFilterScreen`）**：沿用 `EditorUi` 视觉规范，与物品编辑器 / 效果过滤保持一致：
  - 列表列出**全部注册物品**（自动跳过空气），按名称排序，每行带物品图标、名称与物品 ID；
  - **点击行即加入 / 移出名单**，在名单内的行有绿色指示条 + 绿色名称；
  - 搜索框支持物品名与物品 ID（装了 JustEnoughCharacters 时走拼音匹配，与模组其它搜索一致）；
  - 底栏「**手持加入**」一键把主手物品加入 / 移出名单，省去搜索；「**只看已选**」把列表收窄到已选条目，方便核对与批量移除；「清空」一键清空；
  - 顶部实时显示当前模式语义与「已选 N 项」；关闭界面用 `onClose` 返回属性面板（沿用物品编辑器的「返回父界面」模式）。
- **数据与同步**：名单属于**玩家个人数据**，存在 `PlayerStats.autoDepositFilterList` + `autoDepositUseWhitelist`，随 NBT 落盘、随 `copyFrom` 过继、随 `SyncStatsPacket` 同步；新增 `UpdateDepositFilterPacket`（客户端 → 服务端），服务端用 `normalizeFilterIds` 校验（剔除非法 `ResourceLocation`、上限 512 条），防止客户端注入垃圾数据。
- **过滤判定**：`PlayerStats.allowsAutoDeposit(itemId)` 用一句 `whitelist == listed` 覆盖四种组合，且列表为空直接放行；`AutoDeposit.deposit` 里把物品 ID 提前取出，顺便复用于服务端配置黑名单判断，避免重复查注册表。
- **与服务端配置的分工**：配置项 `AutoDeposit.autoDepositBlacklist` 是**服务器级**约束（对所有玩家生效、支持 `#物品标签`），玩家界面维护的是**个人级**名单，两者同时生效。

### 🔢 其它

- 协议版本 `7 → 8`（`SyncStatsPacket` 增加过滤模式与名单字段）。
- 版本号 `1.18.0 → 1.19.0`；中英文语言文件补齐 23 条界面键；README 同步（过滤用法、配置项说明、核心特性）。

## [1.18.0] - 2026-10-02

### 🎒 新增「自动入库」（功能分类，3 点解锁）

- **效果**：解锁后每隔一段时间扫描一次背包，把物品自动写入已连接的存储网络（RS / AE2 / 汤姆存储 / Sophisticated Backpacks），实现「边打怪边自动整理背包」。
- **存储优先级由玩家决定**：主面板搜索框右侧新增「自动入库：RS › AE2 › …」按钮，直接显示当前写入顺序；
  - **点击** = 把队首存储移到末尾；**Shift + 点击** = 把队尾移到队首。
  - 顺序存储在玩家自己的属性数据里（`PlayerStats.autoDepositPriority`），随存档同步、跟人走，不是全局配置。
  - 客户端只负责算新顺序并发送，服务端用 `normalizeDepositOrder` 做**归一化校验**（只接受 RS/AE2/TOMS/BACKPACK/BD、去重、按配置顺序补齐缺失项），保证两端对顺序的理解不会分叉。
- **写入顺序即回落顺序**：`NetworkIO` 新增 `getNetworks(player, order)` 重载，`insert` 会按给定顺序逐个网络尝试，装不下的自动进入下一个网络。
- **安全保护（重点）**：
  - 存储**无线终端**（RS 的 `findWirelessTerminals`、AE2 的 `findWirelessTerminal`）与 **Sophisticated Backpacks 背包本身**永不入库 —— 否则一次扫描就会把「连网凭证」自己存进网络，玩家当场失联；汤姆存储的无线终端按命名空间 + 路径识别保护。
  - 新增配置 `AutoDeposit.autoDepositBlacklist`（物品 ID，`#` 前缀表示物品标签）兜底。
  - 玩家**正在和别的容器交互时**（箱子 / 交易 / 随身工作台）跳过本轮；没有可用存储网络时直接跳过。
- **默认不碰快捷栏**：`autoDepositKeepHotbar = true` 时只扫描主背包 27 格，随身工具/武器不会被吸走；设为 `false` 则除「当前手持那一格」外全部入库。
- **性能**：扫描间隔由 `AutoDeposit.autoDepositInterval` 控制（默认 20 tick = 1 秒），异常隔离在独立 try 中，不会影响其它功能处理器。
- **实现位置**：新增 `compat/AutoDeposit`；`stats/StatType`、`stats/PlayerStats`、`network/SyncStatsPacket`（同步优先级）、`network/NetworkHandler`（新增 `SetDepositPriorityPacket`，协议版本 6 → 7）、`handler/UtilityHandler`（tick 调度）、`client/StatsScreen`（优先级按钮）。

### 🛡️ 新增「死亡不掉落」（功能分类，5 点解锁）

- **效果**：解锁后死亡时**保留主背包 36 格 + 盔甲 4 格 + 副手 1 格**，即使服务器未开启 `keepInventory` 游戏规则也不会掉落任何物品。
- **为什么不能用现成事件**：原版的掉落发生在 `Player.dropAllDeathLoot → Inventory.dropAll()`，这一段**没有任何可拦截的 Forge 事件**（`LivingDropsEvent` 只覆盖战利品表掉落，不覆盖玩家背包）。
- **做法**：在 `LivingDeathEvent`（LOWEST）里把三个 `NonNullList`（`items` / `armor` / `offhand`）整体移入 `PlayerStats.pendingKeptInventory` 并清空原列表 —— 原版随后执行的 `dropAll()` 面对空背包自然什么都掉不出来；重生时在 `PlayerEvent.Clone` 里原样归还。
- **两个必须处理的坑**：
  1. 死亡事件对玩家会**触发两次**（`Player.die` 与 `super.die()` 各调一次 `ForgeHooks.onLivingDeath`），因此用 `hasPendingKeptInventory()` 做幂等保护，避免第二轮用空背包覆盖暂存；
  2. 必须挂在 **LOWEST**：自动复活（HIGHEST）会在自己那一轮 `setCanceled(true)`，取消后不应清空背包，所以要先判 `event.isCanceled()`。
- **不丢档**：暂存物品随 `PlayerStats` 写入玩家 NBT，因此在死亡界面断线、服务器重启（只要玩家数据落盘）都能找回；暂存列表也随 `copyFrom` 一起过继给重生后的新玩家实体。
- **已知行为**：**经验值仍按原版规则掉落与扣除**（该属性只保护物品）；带「消失诅咒」的物品不会被销毁（清空发生在原版 `destroyVanishingCursedItems` 之前），相当于一并保住。
- **实现位置**：`event/StatEventHandler` 新增 `onLivingDeathKeepInventory` / `restoreKeptInventory` 并扩展 `onPlayerClone`；`stats/PlayerStats` 新增暂存字段与 NBT（反）序列化。

### 💰 新增「村民交易折扣」（功能分类，每点 -1%）

- **效果**：每点使村民 / 流浪商人的交易价格降低 1%，最高 90%。
- **落点**：价格由 `MerchantOffer.getCostA()` 现算（基础数量 + 需求加价 + `specialPriceDiff`），而 `Merchant#openTradingScreen` 是**先 `openMenu`（`PlayerContainerEvent.Open` 在此触发）再发送交易列表**，所以在这个事件里改价，客户端收到的就是折后价，服务端扣物品走同一份 `offers`，不会出现价格不一致。
- **防刷价**：折扣按「取更优惠者」写入（`if (getSpecialPriceDiff() > -discount) setSpecialPriceDiff(-discount)`），**只保证至少减这么多、不累加** —— 村民在 `stopTrading` 会重置 `specialPriceDiff`，但流浪商人不会，累加会让价格一路跌到 1。
- **实现位置**：`event/StatEventHandler#onContainerOpen`。

### 🔢 其它

- 协议版本 `6 → 7`（`SyncStatsPacket` 增加自动入库优先级字段，两端版本不一致会被 Forge 直接拒绝连接，属预期行为）。
- 版本号 `1.17.2 → 1.18.0`；README 同步（属性总数 73 → 76、新增配置项、新增功能说明章节）；中英文语言文件补齐 6 条属性键与 12 条界面键。

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
