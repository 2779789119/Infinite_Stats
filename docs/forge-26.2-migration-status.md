# Forge 1.20.1 → 26.2 迁移状态

- 源：Minecraft 1.20.1 / Forge 47.x / Java 17 / ForgeGradle 6 / Gradle 8.x
- 目标：Minecraft 26.2 / Forge 26.2-65.1.3 / Java 25 / ForgeGradle 7.0.31 / Gradle 9.5.0
- 编译结果：**0 错误**（起始 2094）
- 构建产物：**`build/libs/infinitestats-1.18.0.jar`**（另有 `-sources.jar`）
- 状态：**迁移完成**；未做运行时冒烟测试（按需求未测试）

> 建议首次进游戏时观察日志中的 `[InfiniteStats]` 输出与异常堆栈；
> 26.2 的注册/事件时机与 1.20.1 差异较大，运行期行为需实测确认。

---

## 1. 构建环境（已打通）

### 1.1 JDK 25

`build.gradle` 通过 `toolchain.languageVersion = JavaLanguageVersion.of(25)` 要求 JDK 25。
本机 `C:\jdk25`（OpenJDK 25.0.1）可直接使用；Gradle 会自动探测，也可显式指定：

```powershell
$env:JAVA_HOME = "C:\jdk25"
```

### 1.2 关键坑：ForgeGradle 7 强制要求 JDK 8

ForgeGradle 7 的 **Minecraft Mavenizer** 在 `Patcher.modifyAccess` 阶段会去获取
**JDK 8**（用于跑 AccessTransformer 工具链）。找不到时它会尝试从
`https://github.com/adoptium/...` 下载，本机直连超时，表现为：

```
Failed to provision JDK 8
java.net.http.HttpConnectTimeoutException: HTTP connect timed out
> Process 'command 'C:\jdk25\bin\java.exe'' finished with non-zero exit value 1
```

**解决办法**（已落地）：把 JDK 8 放到工作区并用环境变量喂给它。

```powershell
# 1) 从清华镜像下载（GitHub 直连不通）
curl.exe -L --ssl-no-revoke -o .jdk8\jdk8.zip `
  "https://mirrors.tuna.tsinghua.edu.cn/Adoptium/8/jdk/x64/windows/OpenJDK8U-jdk_x64_windows_hotspot_8u504b01.zip"

# 2) 解压
cd .jdk8 ; tar -xf jdk8.zip        # 得到 .jdk8\jdk8u504-b01

# 3) 构建时注入（mcmaven 的 JavaHomeLocator 会读 JAVA_HOME_X64）
$env:JAVA_HOME_X64 = "d:\mod\infinitestats-forge-26_2\.jdk8\jdk8u504-b01"
```

> 也可以把解压后的目录复制到 `C:\Users\<用户>\.jdks\` —— mcmaven 的
> `JavaDirectoryLocator` 会扫描该目录。

### 1.3 编译命令

```powershell
Set-Location "d:\mod\infinitestats-forge-26_2"
$env:JAVA_HOME = "C:\jdk25"
$env:JAVA_HOME_X64 = "d:\mod\infinitestats-forge-26_2\.jdk8\jdk8u504-b01"
.\gradlew.bat :compileJava --console=plain        # 输出到 build_out.txt 便于统计
```

源码浏览用的反编译源位于 `.refsrc/`（由 ForgeGradle 的 `genSources` 产出，未纳入版本控制）。

---

## 2. 已完成的迁移（要点）

| 领域 | 1.20.1 | 26.2 |
|---|---|---|
| 标识符 | `ResourceLocation` | `Identifier`（`fromNamespaceAndPath` / `parse` / `withDefaultNamespace`） |
| 绘制 | `GuiGraphics` | `GuiGraphicsExtractor`（`text` / `centeredText` / `item` / `extractBackground`） |
| blit | `blit(tex, …)` | `blit(RenderPipeline, tex, …)`，贴图尺寸参数变为必需 |
| 渲染入口 | `render(g, x, y, pt)` | `extractRenderState(g, x, y, pt)`；`renderBg`→`extractBackground`、`renderLabels`→`extractLabels` |
| 姿态栈 | `PoseStack`（3D） | `Matrix3x2fStack`（2D，`translate/scale` 少一个 z 参数） |
| 输入 | `mouseClicked(double,double,int)` 等 | 事件对象：`MouseButtonEvent` / `KeyEvent` |
| NBT getter | `tag.getString(k)` | `tag.getStringOr(k, "")`、`getCompoundOrEmpty`、`getListOrEmpty`… |
| 物品 NBT | `stack.getTag()/setTag()` | `DataComponents.CUSTOM_DATA` + `CustomData` |
| 网络 | `SimpleChannel#registerMessage` | `ChannelBuilder…simpleChannel().play().bidirectional().add(codec, handler)` |
| 网络发送 | `sendToServer(pkt)` | `send(pkt, PacketDistributor.SERVER.noArg())`（注意参数顺序） |
| 事件总线 | `@EventBusSubscriber` + `IEventBus` | `BusGroup.DEFAULT.register(MethodHandles.lookup(), Class)` |
| 事件优先级 | `EventPriority.X` | `Priority.X`（byte 常量） |
| 事件取消 | `event.setCanceled(true)` | **监听器 `return true`**（`Cancellable` 已无方法） |
| Tick | `TickEvent.Phase.END` | `TickEvent.XxxTickEvent.Post` record |
| Capability | `RegisterCapabilitiesEvent` | `@AutoRegisterCapability` |
| 能力附加 | `AttachCapabilitiesEvent<Entity>` | `AttachCapabilitiesEvent.Entities` |
| 序列化 | `INBTSerializable#serializeNBT()` | `serializeNBT(HolderLookup.Provider)` |
| 容器 NBT | `ContainerHelper` + `CompoundTag` | 需 `ValueInput/ValueOutput`（用 `TagValueInput/TagValueOutput` 包一层） |
| 配方输入 | `recipe.getIngredients()` | `recipe.placementInfo().ingredients()`；`Ingredient#items()` |
| 配方产出 | `recipe.getResultItem(ra)` | `recipe.display()` → `SlotDisplay.resolveForFirstStack(ctx)` |
| 属性修饰符 | `AttributeModifier(UUID, name, v, op)` | `AttributeModifier(Identifier, double, Operation)`（记录类） |
| 属性操作 | `ADDITION / MULTIPLY_BASE / MULTIPLY_TOTAL` | `ADD_VALUE / ADD_MULTIPLIED_BASE / ADD_MULTIPLIED_TOTAL` |
| 附魔注册表 | `ForgeRegistries.ENCHANTMENTS` | 数据驱动：`registryAccess().lookupOrThrow(Registries.ENCHANTMENT)` |
| 文本序列化 | `Component.Serializer` | `ComponentSerialization.CODEC`（已封装为 `util/TextJson`） |
| 界面打开 | `NetworkHooks.openScreen` | `ServerPlayer#openMenu` |
| HUD 层 | `RegisterGuiOverlaysEvent` | `AddGuiOverlayLayersEvent` + `ForgeLayeredDraw` |
| 按键 | `KeyMapping(name, key, category)` | `KeyMapping(name, InputConstants.Type, key, KeyMapping.Category)` |
| 挖掘等级 | `TierSortingRegistry` | 已移除 → 现实现为「有点数即放行」（见下） |
| Minecraft | `mc.setScreen(x)` / `mc.screen` | `mc.gui.setScreen(x)` / `mc.gui.screen()` |

### 已有功能取舍（迁移中的近似实现）

1. **挖掘等级加成**：26.2 删除了 Forge 的 `TierSortingRegistry` / `TieredItem`，
   原「按工具材质排序提升 N 级」无对应 API，现改为**只要点了该属性即允许采集**
   （`PlayerEvent.HarvestCheck#setCanHarvest(true)`）。→ `StatEventHandler#onHarvestCheck`
2. **JEI 集成**：`compat/jei/` 两个文件在 26.2 无对应 JEI 坐标与 API，已在
   `build.gradle` 中 `exclude`，源码保留待补齐依赖后恢复。
3. **成就管理器的手动授予/撤销**：26.2 的 `Advancement` 不再暴露 criteria，
   已改为从 `AdvancementProgress.getRemainingCriteria()/getCompletedCriteria()` 取条件名。

---

## 3. 剩余工作

编译与打包均已完成（`gradlew build` → `build/libs/infinitestats-1.18.0.jar`）。

**唯一未做的是运行时验证**（按需求未测试）。建议的验证顺序：

1. `gradlew runClient` 启动客户端，确认模组加载与 `[InfiniteStats] Common setup complete` 日志；
2. 进世界后按 `P` 打开属性面板，确认 Tab 切换、加减点、HUD（`H`）正常；
3. 打一次怪确认经验/等级/属性结算；
4. 打开随身熔炉与工作台，确认配方查询与产出；
5. 死亡一次确认自动复活 / 数据保留（Capability 在新版走 `@AutoRegisterCapability`）。

若上述任一环节异常，优先检查 `.refsrc` 中对应的 26.2 实现，并参考 `tools/` 里的批次脚本回看改动。

---

## 4. 迁移脚本

`tools/` 下是按批次执行的批量改写脚本（PowerShell），可重复运行、便于回溯每一步改了什么。
其中 `fix-lines*.ps1` 采用「解析 javac 错误位置 → 只改报错行」的策略，避免大范围误伤。

> 注意：脚本中 PowerShell 的 `$1` 组引用必须写在**单引号**字符串里，
> 否则会被 PowerShell 当变量展开成空串（迁移过程中曾因此产生过一批语法错误）。
