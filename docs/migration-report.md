# ModMind migration report

- Source: forge 26.2
- Target: neoforge 1.21.1
- Loader version: 21.1.252
- Source snapshot: 2026-10-02T13-12-42-391Z
- Generated: 2026-10-02T13:13:00.613Z

## Automatic changes

- 创建源项目快照并复制到新的项目目录
- 更新 ModMind 项目清单、Gradle 配置和加载器描述文件
- 保留源码、资源、数据包与项目文档
- 转换 Forge 与 NeoForge 的标准 Java 包名前缀

## Verification required

- Run the ModMind preflight check.
- Build the generated project.
- Complete a Minecraft launch smoke test.

## Warnings

- 跨加载器 API 并非一一对应，迁移后需要构建和 Minecraft 启动验证
- Minecraft API、映射名称和资源格式可能已经变化
- 原始 Gradle 配置和 Wrapper 已备份到 docs/migration-source-build；请按目标 Loader 检查自定义依赖和任务