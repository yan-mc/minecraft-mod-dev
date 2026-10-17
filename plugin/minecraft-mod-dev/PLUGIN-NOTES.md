# minecraft-mod-dev plugin v0.3.3

日期：2026-10-18
位置：`plugin/minecraft-mod-dev`

## 已安装

```text
plugin_manager install_bundle target=...\plugin\minecraft-mod-dev
application=applied, version=0.3.3
```

v0.3.3 包含：
- 14 个 `mcdev_*` 工具；
- `mcmod` preset（团队默认，Agent Teams 9 工具）；
- 内置官方实验 Agent Teams 挂载（禁用传统 subagent 四行）；
- `mcdev_scenario` 支持 YAML 卡片 + `dryRun` + `give.select`/`select_item` + `block` 步骤（比较器 analog）。

## 工具

| 工具 | 作用 |
|---|---|
| `mcdev_status` | server/client probe 状态 |
| `mcdev_command` | server 控制台 / client 玩家命令 |
| `mcdev_ui` | 客户端 Screen、控件树、容器槽位 |
| `mcdev_click` | GUI 坐标/槽位点击（支持 shift） |
| `mcdev_use` | 右键方块（开箱子/交互） |
| `mcdev_screenshot` | 客户端截图，返回 path/exists/size |
| `mcdev_run_dump` | run 快照写到 `.mcdev/runs/<id>/snapshot-*.json` |
| `mcdev_build` | 在指定 Forge 项目跑 `gradlew build` |
| `mcdev_gametest` | 跑 `gradlew runGameTestServer` |
| `mcdev_install_probe` | 校验并把 dev probe jar 放进一个或多个 `<runDir>/mods` |
| `mcdev_scenario` | 顺序执行 steps，或把 card YAML 转成 steps；支持 dryRun |
| `mcdev_prepare` | 启动 `runServer`/`runClient`，等待 probe ready，返回 pid/port/logPath |
| `mcdev_stop` | 停止 prepare 启动的进程（先 probe stop/quit，再强杀进程树） |
| `mcdev_launch_status` | 查看 prepare 启动的进程列表/pid/port/logPath/running |

## 实测

| 调用 | 结果 |
|---|---|
| `mcdev_status side=server` | tick/gameTime/players/version |
| `mcdev_command cmd="time set day"` | result=1000 |
| `mcdev_run_dump` | runId/tick/logs/path |
| `mcdev_screenshot` | exists=true, 854×480 |
| `mcdev_build` | exitCode=0 |
| `mcdev_gametest` | exitCode=0，11/11 GameTests |
| `mcdev_install_probe`（dev jar） | `manifestChecked=true, hasMixinConfigs=true`，3 个 runDir 已复制 |
| `mcdev_install_probe`（普通 jar） | 被拒绝：`probe jar must be a *-dev.jar` |
| `mcdev_scenario` 成功 | 8 步全过：status/assert/command/wait/status/assert/run_dump/assert |
| `mcdev_scenario` 失败 | `ok=false, failedAt=1`，错误 `expected eq "1.12.2"`，第 3 步未执行 |
| `mcdev_prepare kind=server` | pid=12620, port=39001, logPath=`.mcdev/launch-server-*.log`, ready=true |
| `mcdev_prepare kind=both` | server pid=26068 + client pid=7944，两个 probe 都 ready |
| `mcdev_stop side=all` | 两个进程都 stopped，之后 `launch_status=[]`，无 Java 残留 |
| YAML card dryRun | F-001/F-002 正常翻译；F-006 对不支持的 action/assert 给 warnings |
| YAML card live | S-001：setblock + data merge + data get + assert 123 全过 |
| YAML card live F-002 | 客户端 `card=F-002`：8 步全过，打开 ChargerScreen，title/slot 断言通过，warnings=[] |
| YAML card live F-001 | `give.select` 主手 + use + data get：assert 223 ≥ 100 通过；comparator 警告 |
| comparator analog | `scenario` 的 `block` 步骤：Energy=5000 → `/world/block` 返回 `analog=7`，assert eq 7 通过 |

## install_probe 的校验

1. 文件名必须是 `*-dev.jar`；
2. 用 `jar xf <jar> META-INF/MANIFEST.MF` 解出 manifest；
3. manifest 必须包含 `MixinConfigs: mcdev_probe.mixins.json`；
4. 通过后复制到 `<runDir>/mods/<name>`。

`jar` 路径优先取 `JAVA_HOME/bin/jar.exe`，否则用 PATH 里的 `jar`。
Windows 下 `spawnSync` 用 `shell: false`，避免 `C:\Program Files\...` 被 shell 截断。

## 团队/模式

- `task-1` 创建 → 分配给 docs-checker → `wait_agent` 收到总结 → complete；
- `mcmod` preset 需要在**新会话**里选一次，确认 roster/UI 正常。

## 待办

1. 用 Charger 作为 reference mod，跑完整团队流程。
2. 卡片解析覆盖更多 assert（recipe/loot/item_bar）。
3. 公开 beta 的安装包/版本化（v0.3.1 release）。
