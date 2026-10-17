# minecraft-mod-dev plugin v0.2.0

日期：2026-10-18
位置：`plugin/minecraft-mod-dev`

## 已安装

```text
plugin_manager install_bundle target=...\plugin\minecraft-mod-dev
application=applied, version=0.2.0
```

v0.2.0 包含：
- 9 个 `mcdev_*` 工具；
- `mcmod` preset（团队默认，Agent Teams 9 工具）；
- `cordis.patch.yml` 内置官方实验 Agent Teams 挂载（禁用传统 subagent 四行）。

## 工具

| 工具 | 作用 |
|---|---|
| `mcdev_status` | server/client probe 状态（tick/gameTime 或 screen/fps/player） |
| `mcdev_command` | server 控制台命令 / client 以玩家身份发命令 |
| `mcdev_ui` | 客户端当前 Screen、控件树、容器槽位 |
| `mcdev_click` | GUI 坐标点击或容器槽位点击（支持 shift） |
| `mcdev_use` | 右键方块（开箱子/工作台/触发交互） |
| `mcdev_screenshot` | 客户端截图，返回 path/exists/size |
| `mcdev_run_dump` | run 快照写到 `.mcdev/runs/<id>/snapshot-*.json` |
| `mcdev_build` | 在指定 Forge 项目目录跑 `gradlew build` |
| `mcdev_gametest` | 跑 `gradlew runGameTestServer`，返回 exitCode/summary/tail |

## 实测

| 调用 | 结果 |
|---|---|
| `mcdev_status side=server` | `tick=71, gameTime=1501, players=0, version=1.20.1` |
| `mcdev_command cmd="time set day"` | `result=1000` |
| `mcdev_command cmd="mcdev freeze true"` | `result=1` |
| `mcdev_run_dump n=3` | 返回 runId/tick/logs/path |
| `mcdev_status side=client` | `ReceivingLevelScreen` → 进服 |
| `mcdev_screenshot` | `exists=true, size=79471`，854×480 |
| `read_image` | 能看到玩家、箱子、WASD 教程、聊天警告 |
| `mcdev_build projectDir=lab/forge-1.20.1-test` | `exitCode=0`，BUILD SUCCESSFUL |
| `mcdev_gametest projectDir=lab/forge-1.20.1-test` | `exitCode=0`，`All 3 required tests passed` |

## 团队验证

- `team_task_create` 建了 `task-1`；
- `spawn_teammate` 起 `docs-checker`（fresh）；
- `team_task_update` 把 task-1 分配给 docs-checker；
- `wait_agent` 收到 teammate 的总结；
- 说明 Agent Teams 的建/派/等/回传链路可用。

## 待办

1. M4：5 张功能卡 + 5 类注入 bug 的完整闭环。
2. `mcmod` preset 需要在**新会话**里选一次，确认 roster/UI 正常。
3. 后续：`mcdev_install_probe`（自动把 devJar 放进 run/mods）、`mcdev_scenario`（卡执行器）。
