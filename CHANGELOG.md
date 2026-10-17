# Changelog

## v0.3.0 — 2026-10-18（public beta）

- 插件升级到 14 个 `mcdev_*` 工具。
- 新增 `mcdev_prepare` / `mcdev_stop` / `mcdev_launch_status`：
  自动启动 server/client、等待 probe ready、清理进程。
- `mcdev_install_probe`：校验 `*-dev.jar` + `MixinConfigs`，复制到 run/mods。
- `mcdev_scenario`：顺序执行 status/command/use/ui/click/screenshot/run_dump/wait/assert。
- `mcdev_build` / `mcdev_gametest`：Gradle 编排。
- Charger reference mod：GameTest **12/12**；5 个注入 bug 5/5 检出并修复。
- 团队流程：Builder/Tester 实现 F-006；Builder/Reviewer 修 bug 演练。
- 新文档：安装、工具、团队、排错、状态、演示。

## v0.2.4

- 新增 `mcdev_scenario` 步骤执行器（JSON steps）。
- 成功/失败场景实测：8 步全过；错误 assert `ok=false, failedAt=1`。

## v0.2.3

- 新增 `mcdev_install_probe`。
- dev jar 校验：文件名 `*-dev.jar` + manifest `MixinConfigs: mcdev_probe.mixins.json`。
- 修复 Windows 下 `jar` 路径带空格时 shell 截断的问题（`shell: false`）。

## v0.2.0

- 9 个 `mcdev_*` 工具。
- 挂载官方实验 Agent Teams；新增 `mcmod` preset。
- `mcdev_build` / `mcdev_gametest`。

## v0.1.0

- 初始插件：6 个探针工具（status/command/ui/click/screenshot/run_dump）。
- 服务端/客户端 probe、devJar、容器 GUI、截图验证。
