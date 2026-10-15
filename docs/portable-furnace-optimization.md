# 随身熔炉操作优化

日期：2026-09-22。目标：Forge 47.3.0 / Minecraft 1.20.1 / Java 17。

## 已实现的源码改动

- 主界面将熔炼、背包与仓库/速度/网络操作分区，显示缺燃料、等待材料、无配方、输出受阻等状态；仓库按钮显示占用格数。
- Shift 点击燃料优先补燃料；其他可熔炼材料先补当前输入，放不下或类型不同时进入待炼队列。同类库存优先合并，队列满时余量保留在背包。
- 待炼仓也接受木头等同时可燃烧、可熔炼的材料；要烧木炭时手动放入输入或待炼仓。
- 主界面和成品仓可一键收取。只转移背包容纳得下的数量，保留物品标签、正常堆叠上限和仓内余量，创造模式也不丢弃余量。
- 空闲及输出受阻时保留余热；缺燃料时保留进度。高倍率可一次处理多份材料，保留不足一份的进度，使用有界计算避免大量循环和整数溢出。
- 主槽校验物品用途，输出槽禁止放入物品；恢复物品提示并显示精确库存数量。
- 待炼仓、成品仓增加返回按钮，Esc 返回熔炉；优先级页面修复按钮刷新与名称遮挡，悬停可看全名。
- 服务端同步速度成本、升级可用性、工作状态和仓库占用。菜单同步结构已变化，网络协议由 3 升为 4，客户端和服务器需使用本次对应构建。原有存档数据结构不变。

## 验证状态

通过的静态检查：相关源码差异无空白错误；中英文语言文件可解析，熔炉相关的 59 个键对应一致、无重复键，格式占位符数量一致；新增菜单按钮、提示和库存 API 已核对 1.20.1 映射。

在 src/gameTest/java/com/infinitestats/RegressionTests.java 增加 4 个 GameTest：

- furnaceRoutingAndQueueCapacity：槽位约束、混合输入排队、优先合并、仓满余量和输出取物。
- furnaceCollectConservesItems：背包只余一件空间、满背包/重复收取、创造模式、大容量库存、物品标签和副手保护。
- furnaceHeatProgressAndBatchSpeed：空闲余热、存档恢复、多件高速产出、进度余数与极限倍率。
- furnaceFuelPauseAndOutputBackpressure：缺燃料暂停/续烧、输出受阻/释放容量、非法旧输入状态。

现有库存网络同步用例已覆盖新增菜单数据。上述 GameTest 尚未运行。

以下托管调用均在执行前返回同一错误：

MCP tool call requires approval, but approval policy is never

- modmind_build_project
- modmind_test_session 的 capabilities
- modmind_test_matrix 的 gametest、client 目标
- modmind_test_rendered

因此没有本次构建成功、新 JAR、游戏启动或截图证据。没有启动需清理的测试会话。阻塞是会话的工具审批策略，所选测试功能本轮均已勾选。

待工具权限恢复后，运行托管构建、GameTest、无头客户端与 rendered 启动检查；在真实游戏内检查混合材料 Shift 排队、一键收取、仓库返回、优先级调整和不同 GUI 缩放下的显示。游戏互动与视觉效果尚未验收。
