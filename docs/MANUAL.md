# minecraft-mod-dev 说明书

版本：`0.5.0`  
更新：2026-10-18  
适用：Windows 10/11 x64 · Minecraft 1.20.1 · Forge 47.4.x · Java 17 · DeepSeek Harness 0.2.1-rc.2

---

## 目录

1. [项目是什么](#1-项目是什么)
2. [能做什么 / 不能做什么](#2-能做什么--不能做什么)
3. [环境要求](#3-环境要求)
4. [安装](#4-安装)
5. [快速开始](#5-快速开始)
6. [架构与端口](#6-架构与端口)
7. [工具参考](#7-工具参考)
8. [卡片与场景 DSL](#8-卡片与场景-dsl)
9. [参考模组 Charger 与测试](#9-参考模组-charger-与测试)
10. [推荐工作流](#10-推荐工作流)
11. [团队模式](#11-团队模式)
12. [排障](#12-排障)
13. [限制与路线图](#13-限制与路线图)
14. [二次开发](#14-二次开发)
15. [License](#15-license)

---

## 1. 项目是什么

`minecraft-mod-dev` 是一个 **DeepSeek Harness（DSH）插件 + Minecraft Forge 探针**，目标是让 AI（或自动化脚本）在真实 Minecraft 环境里完成模组开发闭环：

```text
写代码 → 构建 → 启动真实 server/client → 操作/观察游戏 → 断言 → 看截图/日志 → 修 bug → 回归
```

它不是一个“生成代码就结束”的工具。核心价值是：

- AI 能启动**真实游戏进程**；
- AI 能读取 **UI 控件树、槽位、方块 NBT、配方、掉落、物品条**；
- AI 能**点击、右键、发命令、截图**；
- 这些动作被封装成 `mcdev_*` 工具，可被 Agent Team 的 Builder/Tester/Reviewer 分工调用。

## 2. 能做什么 / 不能做什么

### 能做

- Forge 1.20.1 项目构建、GameTest、dev 环境启动；
- 服务端/客户端探针（HTTP/JSON）；
- GUI 控件树与槽位读取、鼠标/槽位/shift-click；
- 玩家右键、聊天命令、客户端截图；
- 方块 state / block entity NBT / comparator；
- recipe / loot / craft / item bar / 生存挖掘；
- YAML 卡片：可重复执行、可 dryRun、断言失败即停、warnings 显式报告；
- Agent Teams 团队任务板、Tester/Reviewer 复核。

### 不能做

- 不是通用 Minecraft agent：不做实时 WASD/PvP/建造；
- 不替代人的主观验收（手感、美术、平衡性）；
- 不支持 Forge 47.4.x 以外的版本/加载器；
- 探针 Mixin 只支持 dev 环境，生产 SRG 未支持；
- `craft` 是服务端合成模拟，不是 GUI 合成流程；
- 当前不发布 npm 包，使用本地 link + GitHub release 资产。

## 3. 环境要求

| 组件 | 版本 |
|---|---|
| OS | Windows 10/11 x64 |
| JDK | 17（实测 Temurin 17.0.19） |
| Minecraft | 1.20.1 |
| Forge | 47.4.x（实测 47.4.28） |
| Gradle | 仓库自带 wrapper（8.8） |
| DSH | 0.2.1-rc.2 |
| Python（可选） | 仅工具脚本使用 |

客户端需要能启动 OpenGL 4.6；测试服使用离线模式。

## 4. 安装

### 4.1 从 GitHub Release 安装（推荐）

1. 打开 [v0.5.0 release](https://github.com/yan-mc/minecraft-mod-dev/releases/tag/v0.5.0)；
2. 下载：
   - `minecraft-mod-dev-0.5.0.zip`
   - `mcdev_probe-0.1.0-dev.jar`
3. 解压插件 zip；
4. 在 DSH 中：

```text
plugin_manager install_bundle target=<解压目录>/minecraft-mod-dev
```

5. 把 `mcdev_probe-0.1.0-dev.jar` 放入测试工程的 `run-server/mods` 和 `run-client/mods`，或使用 `mcdev_install_probe`。

### 4.2 从源码安装

```powershell
git clone https://github.com/yan-mc/minecraft-mod-dev.git
cd minecraft-mod-dev
```

DSH：

```text
plugin_manager install_bundle target=<仓库绝对路径>\plugin\minecraft-mod-dev
```

构建探针：

```powershell
cd lab\mcdev-probe
.\gradlew.bat clean devJar
```

产物：

```text
lab\mcdev-probe\build\libs\mcdev_probe-0.1.0-dev.jar
```

复制到：

```text
lab\forge-1.20.1-test\run-server\mods\
lab\forge-1.20.1-test\run-client\mods\
```

### 4.3 安装校验

安装后应看到 14 个 `mcdev_*` 工具。检查：

```text
mcdev_launch_status
```

返回 `{"processes":[]}` 即工具已就绪。

## 5. 快速开始

### 5.1 启动 server + client

```text
mcdev_prepare
  projectDir=F:\...\lab\forge-1.20.1-test
  kind=both
```

返回：

```json
{"ok":true,"results":[
  {"kind":"server","pid":...,"port":39001,"ready":true},
  {"kind":"client","pid":...,"port":39002,"ready":true,"joined":true,"attempts":[...]}
]}
```

说明：

- 客户端不再使用 `--quickPlayMultiplayer`；
- 插件等客户端到 `TitleScreen`，调用客户端探针 `/connect` 显式连接；
- 卡在 `DisconnectedScreen` 会提前失败，最多 3 次尝试。

### 5.2 看状态

```text
mcdev_status side=server
mcdev_status side=client
```

### 5.3 打开 Charger GUI

```text
mcdev_command side=server cmd="setblock 0 64 0 examplemod:charger"
mcdev_command side=server cmd="tp Dev 0.5 64 1.5 180 0"
mcdev_command side=server cmd="item replace entity Dev weapon.mainhand with minecraft:air"
mcdev_use x=0 y=64 z=0 face=up
mcdev_ui
mcdev_screenshot
```

### 5.4 跑卡片

```text
mcdev_scenario card=...\cards\F-002.yml
```

### 5.5 跑 GameTest

```text
mcdev_gametest projectDir=F:\...\lab\forge-1.20.1-test
```

期望：

```text
exitCode=0
summary=All 12 required tests passed
```

### 5.6 停止

```text
mcdev_stop side=all
```

## 6. 架构与端口

```text
DSH / Agent Team
   │  mcdev_* tools
   ▼
plugin/minecraft-mod-dev
   │  HTTP + JSON，token: mcdev
   ├──────────────► 127.0.0.1:39001  server probe
   │                   └─ /status /command /world/* /recipe /loot /item /items /craft /run/dump ...
   └──────────────► 127.0.0.1:39002  client probe
                       └─ /status /ui/snapshot /ui/click /ui/hover_slot /screenshot
                          /command /player/use /player/break /player/mine /connect ...
```

| 组件 | 端口/路径 |
|---|---|
| 测试服 | `25566` |
| server probe | `127.0.0.1:39001` |
| client probe | `127.0.0.1:39002` |
| 认证 token | `mcdev`（本地回环） |
| 服务端 run 目录 | `lab/forge-1.20.1-test/run-server/` |
| 客户端 run 目录 | `lab/forge-1.20.1-test/run-client/` |
| GameTest run 目录 | `lab/forge-1.20.1-test/run-gametest/`（或 `run/`） |

### 关键设计

1. **独立 run 目录**：server/client 共用 `run/` 会登录超时。
2. **dev-mapped 探针**：`*-dev.jar` + manifest `MixinConfigs`；普通 reobf jar 会 `NoSuchMethodError`。
3. **服务端权威**：客户端方块状态可能滞后，断言以服务端 `/world/block` 为准。
4. **显式连接**：quickPlay 在客户端 UI/网络初始化完成前连接，偶发 handshake 卡死；改为 TitleScreen 后 `/connect`。
5. **卡片不静默**：不支持的 action/assert 进入 `warnings`，不会当作通过。

## 7. 工具参考

完整参数/返回示例见 [TOOLS.md](TOOLS.md)。以下为速查。

### 7.1 观察与命令

| 工具 | 作用 | 示例 |
|---|---|---|
| `mcdev_status` | probe 状态 | `side=server` |
| `mcdev_command` | server 控制台 / client 玩家命令 | `side=client cmd="charger info"` |
| `mcdev_run_dump` | run 快照 + 最近日志 | `side=server n=200` |
| `mcdev_launch_status` | 查看 prepare 启动的进程 | 无参数 |

### 7.2 客户端交互

| 工具 | 作用 |
|---|---|
| `mcdev_ui` | 当前 Screen、控件树、槽位（GUI 坐标） |
| `mcdev_click` | 点击 GUI 坐标或槽位，支持 shift |
| `mcdev_use` | 右键方块（开 GUI/交互） |
| `mcdev_screenshot` | 截图，返回 path/exists/size |

> 截图路径是异步的；工具会轮询到文件出现（最多 2s）。

### 7.3 构建与测试

| 工具 | 作用 |
|---|---|
| `mcdev_build` | `gradlew build`，返回 exitCode/tail |
| `mcdev_gametest` | `gradlew runGameTestServer`，exitCode = 失败数 |

### 7.4 编排

| 工具 | 作用 |
|---|---|
| `mcdev_install_probe` | 校验 `*-dev.jar` + manifest，复制到 runDir/mods |
| `mcdev_prepare` | 启动 runServer/runClient，等 probe/进服，失败重试 |
| `mcdev_stop` | 停止 prepare 启动的进程（probe stop/quit → taskkill） |
| `mcdev_scenario` | 执行 steps 数组，或把 YAML 卡片翻译成 steps；支持 dryRun |

### 7.5 `mcdev_prepare` 参数

| 参数 | 说明 |
|---|---|
| `projectDir` | Forge 工程绝对路径 |
| `kind` | `server` / `client` / `both` |
| `timeoutMs` | 单进程就绪超时，默认 180000 |
| `waitJoin` | 是否等待进服，默认 true |
| `joinPort` | 显式连接端口，默认 25566 |

返回关键字段：

- `ready`：probe 就绪；
- `joined`：玩家进服（client/both）；
- `attempts[]`：每次 pid/logPath/lastScreen；
- `retry`：是否重试过；
- `lastScreen`：失败时最后界面。

## 8. 卡片与场景 DSL

### 8.1 两种执行方式

```text
mcdev_scenario steps=[...]
mcdev_scenario card=<绝对路径>.yml
mcdev_scenario card=... dryRun=true
```

`dryRun` 只翻译/报告，不执行。

### 8.2 步骤类型

| type | 主要字段 | 说明 |
|---|---|---|
| `status` | `side`, `port` | 读 probe 状态 |
| `command` | `side`, `cmd` | server 控制台 / client 玩家命令 |
| `block` | `x/y/z` | server 方块 state + block entity + analog |
| `break` | `x/y/z` | `destroyBlock(pos,true)` + 收集 ItemEntity |
| `mine` | `x/y/z`, `player` | 在线玩家 survival 破坏 |
| `client_mine` | `x/y/z`, `timeoutMs` | 客户端发 `START_DESTROY_BLOCK` 包 |
| `items` | `x/y/z`, `r` | 附近 ItemEntity |
| `recipe` | `id` | 配方 result item/count |
| `loot` | `block`, `x/y/z` | loot 表 drops/total |
| `item` | `item`, `nbt` | item bar visible/width/color |
| `craft` | `id`, `player` | 检查/消耗背包材料并产出 |
| `use` | `x/y/z`, `face` | 客户端右键方块 |
| `ui` | — | 读 UI 快照 |
| `click` | `slot` 或 `x/y`, `button`, `shift` | 点击 GUI |
| `screenshot` | — | 客户端截图 |
| `run_dump` | `side`, `n` | 日志/run 快照 |
| `wait` | `ms` | 等待 |
| `assert` | `path`, `op`, `value`, `step` | 对最近数据步骤断言 |
| `ui_close` | — | 关闭当前客户端界面 |

断言 `op`：`eq/ne/contains/gt/gte/lt/lte/exists`。路径为点路径，例如 `data.block`、`data.slots.0.item`、`data.drops.0.item`。

### 8.3 卡片结构

```yaml
id: F-002
title: 充能 GUI
tiers:
  static: true
  gametest: true
  server_scenario: true
  client_ui: true
setup:
  commands:
    - "setblock 0 64 0 examplemod:charger"
    - "tp Dev 0.5 64 1.5 180 0"
actions:
  - {wait: 300}
  - {use_on: {x: 0, y: 64, z: 0, face: up}}
  - {wait: 300}
assert:
  - {screen_title: "Charger"}
  - {slot_exists: 0}
  - {no_exception: true}
ui:
  open: false
  screenshot: true
evidence:
  - "ChargerScreen open: 37 slots, title Charger"
```

`spec` / `tiers` / `ui` / `evidence` 是文档字段，不直接执行。

### 8.4 支持的 actions

| action | 说明 |
|---|---|
| `give` | `give` 物品；`select:true` 放主手，支持 `nbt` |
| `select_item` | 直接放主手，支持 `nbt` |
| `use_on` / `use_item_on` | 客户端右键方块 |
| `command` | server 命令 |
| `wait` | 等待毫秒 |
| `close_ui` | 关闭客户端界面 |
| `craft` | 调用 `/craft` 并断言 `data.crafted=true` |
| `mine` | 服务端在线玩家生存破坏 |
| `client_mine` | 客户端发包破坏 |

### 8.5 支持的 asserts

| assert | 实际步骤 |
|---|---|
| `screen_title` | `ui` + `data.title` |
| `slot_exists` | `ui` + `data.slots.<n>.index` |
| `block_entity_nbt` | `data get block` + `data.result` |
| `comparator` | `block` + `data.analog` |
| `recipe_output` | `recipe` + `data.item/count` |
| `loot_drop` | `loot` + `data.drops.0.item/total` |
| `loot_break` | `setblock` + `break` + `data.drops.0.item/total` |
| `loot_mine` | `setblock` + `mine` + `data.drops.0.item/total` |
| `block_state` | `block` + `data.block` |
| `loot_items` | `items` + `data.drops.0.item/total` |
| `item_bar` | `item` + `data.visible/width/color` |
| `no_exception` | 跳过（probe 错误本身会失败） |

### 8.6 示例：craft

```yaml
setup:
  commands:
    - "clear Dev"
    - "give Dev minecraft:iron_ingot 5"
    - "give Dev minecraft:redstone 4"
    - "give Dev minecraft:copper_ingot 1"
actions:
  - {craft: {recipe: "examplemod:charger", player: Dev}}
assert:
  - {recipe_output: {recipe: "examplemod:charger", item: "examplemod:charger", count: 1}}
```

### 8.7 示例：生存挖掘

```yaml
setup:
  commands:
    - "setblock 0 64 0 examplemod:charger"
    - "gamemode survival Dev"
    - "tp Dev 0.5 64 2.5 180 30"
    - "item replace entity Dev weapon.mainhand with minecraft:iron_pickaxe 1"
actions:
  - {close_ui: true}
  - {wait: 1000}
  - {client_mine: {x: 0, y: 64, z: 0, timeoutMs: 8000}}
assert:
  - {block_state: {x: 0, y: 64, z: 0, block: "minecraft:air"}}
  - {loot_items: {x: 0, y: 64, z: 0, r: 4, item: "examplemod:charger", count: 1}}
```

> 客户端本地 block 可能短暂滞后；断言走服务端 `/world/block`。

## 9. 参考模组 Charger 与测试

### 9.1 功能

- 充能方块：能量 0..10000，右键转移 100 FE，comparator 输出 `floor(energy*15/10000)`；
- 充能物品：tooltip `Energy: N/10000`，热栏能量条；
- GUI：37 槽、shift-click 不复制、关闭重开保持；
- 配方：IRI/RCR/IRI = 5 铁 + 4 红石 + 1 铜 → 1 charger；
- loot：普通破坏掉 1 charger；
- `/charger info`：pos 路径 + 玩家 raycast；
- advancement：获得 charged_item 触发；
- 生存挖掘：需要 `mineable/pickaxe` 标签（已补）。

### 9.2 测试层

| 层 | 内容 |
|---|---|
| `mcdev_build` | Gradle 构建 |
| `mcdev_gametest` | 12 个 GameTest |
| `mcdev_scenario` 服务端卡片 | recipe/loot/craft/item bar |
| `mcdev_scenario` 客户端卡片 | GUI/tooltip/raycast/生存挖掘 |
| 截图 | UI、tooltip、toast、能量条 |
| 注入 bug | 5 类，全部检出并修复 |

当前结果：

```text
mcdev_gametest → exitCode=0, All 12 required tests passed
F-001..F-007 live → warnings=[]
```

完整报告：[lab/forge-1.20.1-test/REPORT.md](../lab/forge-1.20.1-test/REPORT.md)；验收标准：[SPEC.md](../lab/forge-1.20.1-test/SPEC.md)。

## 10. 推荐工作流

### 10.1 改一个功能

```text
1. 改代码
2. mcdev_build projectDir=...
3. mcdev_gametest projectDir=...
4. mcdev_prepare kind=both
5. 跑相关卡片 / mcdev_ui / mcdev_screenshot
6. 失败 → mcdev_run_dump 读日志 → 修复
7. mcdev_stop side=all
```

### 10.2 新增一张卡

1. 复制 `cards/F-002.yml`；
2. 写 `setup/actions/assert`；
3. 先 `dryRun`：

```text
mcdev_scenario card=... dryRun=true
```

4. 去掉 `dryRun` live 跑；
5. 确认 `warnings=[]`、`ok=true`；
6. 把证据写入卡片 `evidence`。

### 10.3 用团队

```text
1. Lead 建任务（team_task_create）
2. Builder 改代码；Tester 加 GameTest/卡片；Reviewer 只读复核
3. Lead 跑 build/gametest/card 收口
```

## 11. 团队模式

`mcmod` preset 默认使用 DSH **实验性 Agent Teams**：

- Lead：拆分任务、收口、最终验证；
- Builder：实现代码/资源；
- Tester：GameTest、卡片、复现；
- Reviewer：只读复核，不直接改文件。

工具：

- `spawn_teammate` / `send_message` / `wait_agent` / `list_agents`；
- `team_task_create/list/get/update`：任务板、blocked_by、write_scopes。

约束：

- 同一文件只给一个 owner；
- 卡片顺序执行，避免 UI/物品竞态；
- Reviewer 的结论要基于 task 输出 + 日志/截图，不接受“看起来没问题”。

更多：[TEAM.md](TEAM.md)。

## 12. 排障

### 12.1 登录失败 / Timed out

- 不要共用 `run/`；
- server 先 ready，再启动 client；
- 客户端不要 quickPlay；等 TitleScreen 后 `/connect`；
- 旧进程未退出会占端口。

### 12.2 quickPlay handshake 卡死

根因：quickPlay 在客户端 UI/网络初始化完成前连接。  
修复：`build.gradle` 去掉 `--quickPlayMultiplayer`；`mcdev_prepare` 显式 `/connect`。

### 12.3 `NoSuchMethodError: Commands.m_82127_`

用了普通 reobf jar。必须：

```powershell
cd lab\mcdev-probe
.\gradlew.bat clean devJar
```

再用 `mcdev_install_probe`。

### 12.4 `mcdev_install_probe` 拒绝 jar

- 文件名不是 `*-dev.jar`；
- manifest 缺 `MixinConfigs: mcdev_probe.mixins.json`；
- 改 probe 后未 clean。

### 12.5 卡片 UI 读不到 / 卡在旧界面

- 卡片顺序执行；
- use 前后加 `wait`；
- 先用 `close_ui` 关闭旧 Screen；
- 客户端本地 block 可能滞后，用服务端断言。

### 12.6 `mcdev_prepare` 超时

看返回 `logPath`：

```text
<projectDir>/.mcdev/launch-<kind>-<ts>.log
```

常见：Gradle 首次下载、端口占用、客户端 onboarding/TitleScreen 未到、probe 未 ready。

### 12.7 probe 端口占用

```text
netstat -ano | findstr 39001
netstat -ano | findstr 39002
```

停止旧进程或改探针端口。

完整排障：[TROUBLESHOOTING.md](TROUBLESHOOTING.md)。

## 13. 限制与路线图

### 限制

- 仅 Windows / MC 1.20.1 / Forge 47.4.x / Java 17 / DSH 0.2.1-rc.2；
- 探针 Mixin dev-only；
- Agent Teams 实验性；
- 客户端本地状态可能滞后；
- `craft` 服务端模拟，非 GUI；
- 正式 npm 未发布。

### 路线图

- reference-mod 沙箱：把 Charger 作为模板生成新模组；
- 更多卡片断言（item NBT、容器交互）；
- 更完整的团队演示脚本/视频；
- 生产 SRG 探针支持（远期）。

## 14. 二次开发

### 目录

```text
plugin/minecraft-mod-dev/   DSH 插件（index.js、presets、cordis patch）
lab/mcdev-probe/            Forge 探针（server/client HTTP + Mixin）
lab/forge-1.20.1-test/      Charger 参考模组 + GameTest + 卡片
```

### 加一个工具

1. 在 `plugin/minecraft-mod-dev/index.js` 用 `ctx.tools.register(stringTool(...))`；
2. 参数 schema 不要写 `required:false`（会被 schema 校验拒绝；可选参数直接省略）；
3. 在 preset/patch 中确认工具可见；
4. 重装插件并实测。

### 加一个探针端点

1. 服务端：`ProbeServer` 加 context/handler；
2. 客户端：`ClientProbeServer` 加 context/handler（所有游戏访问走 client thread）；
3. `clean devJar` + `mcdev_install_probe`；
4. 加 scenario 步骤/卡片断言；
5. 写报告/证据。

### 安全

- 探针只绑定 `127.0.0.1`；
- token 固定 `mcdev`，仅本地开发；
- 不要暴露到公网。

## 15. License

MIT（原创代码/文档）。Minecraft、Forge、Gradle、Mixin 等第三方组件遵循各自许可证；本仓库不重新分发 Minecraft 客户端或资源。详见 [LICENSE](../LICENSE) 与 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)。
