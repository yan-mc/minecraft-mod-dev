# MC 模组开发模式 v2：1.20.1 Forge / Windows / 公开插件

> 基于 2026-10-17 的第二轮决策与补充调研
> 本文只讨论方案，不写实现代码。
> **实验修订（2026-10-17）**：Carpet: NeoForged 1.0.8 在 Forge dev 下因 Mixin refmap 缺失不可用（ADR-009）。v1 已改为 **probe 自带 fake player / tick freeze**，不再依赖 Carpet；最新冻结见 [方案冻结 v1.1](方案冻结-v1.1-1.20.1Forge-Windows.md)。
> 上一版（通用调研）：[MC模组开发模式-可行性与方案.md](MC模组开发模式-可行性与方案.md)

---

## 0. 本轮定下的范围

| 维度 | 决策 |
|---|---|
| MC 版本 | **只做 1.20.1** |
| 加载器 | **只做 Forge** |
| Java | 17 |
| Forge | 47.4.x（当前 47.4.28） |
| 构建 | ForgeGradle 6.x + Gradle 8.x |
| 用户平台 | **Windows 10/11 本机**，不做 Linux 用户 |
| 用户隔离 | **不建独立 Windows 用户**；同用户 + 每次 run 独立目录 |
| 产品形态 | 公开 DSH 插件，先单版本单环境 |
| 测试方式 | **一个功能一张测试卡，逐项验收** |
| 游戏操作 | 不做 WASD 实时操作；**保留鼠标点击、键盘输入、截图** |
| 复用 | `minecraft-dev`、DebugBridge（协议）、whale-craft（模式/看门狗）等，MIT，可魔改 |
| 多智能体 | 可选接入 DSH 官方实验性 Agent Teams |
| 记忆 | 项目内记忆树 + 可选 `dsh-mnemon` 等记忆插件 |

**一句话**：v1 是"1.20.1 Forge 的功能级自动验收器"，不是通用 MC agent，也不是多版本平台。

### 0.1 与 v1 方案的主要差异

| 维度 | v1 草案 | v2 定稿 |
|---|---|---|
| 版本 | NeoForge 1.21.1 / 26.x 为主 | **1.20.1 唯一** |
| 加载器 | NeoForge/Forge/Fabric 多选 | **Forge 唯一** |
| 客户端自动化 | 先 Fabric Client Gametest，再 NeoForge | **Forge 1.20.1 自研 probe** |
| 隔离 | VM/WSL2/独立用户 | **同用户 + 独立目录 + 进程内 probe** |
| 测试方法 | 分层 + 自由探索 | **功能卡逐项验收**（本轮新增） |
| 多智能体 | 子代理分工 | **可选 Agent Teams（9 工具 + 任务板）** |
| 记忆 | 项目记忆树 | **项目记忆树 + 可选 `dsh-mnemon`** |
| 产品 | 自用/可能商业化 | **公开插件，单版本单环境 Beta** |

---

## 1. 1.20.1 Forge 技术基线（已核实）

### 1.1 版本与构建

- MC 1.20.1，Java 17；Forge **47.4.28**（[Forge 1.20.1 下载页](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html)）。
- ForgeGradle 6.x，Gradle 8.x；`runClient` / `runServer` / `runData` / `runGameTestServer`。
- 映射：official 1.20.1 + Parchment（可选）；1.20.1 仍有混淆，符号索引要靠 `genSources`/反编译。
- `run` 目录默认 `run/`；日志 `run/logs/latest.log`，崩溃 `run/crash-reports/`。

### 1.2 GameTest（这是 v1 的主判据）

Forge 1.20.1 官方支持 GameTest（[Forge 文档](https://docs.minecraftforge.net/en/1.20.1/misc/gametest/)）：

- `@GameTest` + `GameTestHelper`；`@GameTestHolder(MODID)` 或 `RegisterGameTestsEvent` 注册。
- 结构模板放 `data/<namespace>/structures/*.nbt`。
- `/test run|runall|runthis|runthese|runfailed`。
- `runGameTestServer`：**退出码 = 必需测试失败数**，适合 CI。
- `forge.enabledGameTestNamespaces` 控制命名空间；`forge.enableGameTest=true` 让其它 run 也启用。
- 能测：方块/实体/物品/配方/红石/世界交互、模拟玩家、按 tick 调度。
- 不能测：客户端渲染/UI/按键/音效、真实多客户端、线程/异步、性能。
- **准备一个通用模板**（如 3×3×3 平台/空结构，`data/<modid>/structures/empty.nbt`），否则每个测试都要单独建结构，成本很高。

> 1.20.1 没有 1.21.5+ 的 `test_instance` 数据包注册表，也没有 `net.minecraft.gametest.Main --report`。所以 v1 的 CI 判据就是 **Forge 的 `runGameTestServer` + 退出码 + 日志/报告解析**。

### 1.3 假玩家与 tick 控制（1.20.1 的坑）

- **1.20.1 没有 `/tick freeze|step`**（该命令 1.20.2+ 才有）。
- **Carpet 原生是 Fabric**；但有一个 Forge 移植：[Carpet: NeoForged](https://modrinth.com/mod/neoforge-carpet)，Modrinth 明确支持 **1.20.1 + forge/neoforge**，服务端必需、客户端可选。它带来：
  - `/player <name> spawn` 假玩家；
  - `/tick freeze|step`；
  - Scarpet 脚本；
  - 大量可控 vanilla 行为。
- Forge 自带 `FakePlayerFactory`（`net.minecraftforge.common.util.FakePlayerFactory`）也能造假玩家，适合在 probe/GameTest 里做简单交互；但不如 Carpet 的 `/player` 完整。
- **结论**：v1 用 **Carpet: NeoForged** 做假玩家和 tick 控制；probe 只做它拿不到的东西（注册表、NBT、UI、截图、包、覆盖率）。

### 1.4 客户端自动化（v1 最大的自研量）

- Fabric 有官方 Client Gametest；**Forge 1.20.1 没有等价物**。
- [DebugBridge](https://github.com/use-ai-for-mc/debugbridge) 支持 1.19 / 1.21.11 / 26.1 / 26.2，**不支持 1.20.1**；只能借它的协议和 endpoint 设计，不能直接装。
- [mc-runtime-test](https://github.com/headlesshq/mc-runtime-test) 支持 **1.20.1 Forge**，能在 Linux CI 里用 HeadlessMC + Xvfb 起客户端、进单机世界、跑 `/test runall`、退出；但它**不做 UI 截图/鼠标点击**，而且面向 CI 不是本地 Windows。
- **结论**：1.20.1 Forge 的客户端探针必须自己写（或把 DebugBridge 移植到 1.20.1 Forge）。这是 v1 的核心工作量，也是护城河。

### 1.5 可复用的上游

| 上游 | 对 1.20.1 Forge 的可用性 | 用法 |
|---|---|---|
| [`minecraft-dev`](https://github.com/sikadi233-hub/minecraft-dev) | 支持 Forge 1.20.1（FG6） | 脚手架、Gradle wrapper、构建技能、版本矩阵；**优先复用源码/模板**。注意：它的 peer 只枚举到 `^0.2.0-rc.2`，当前 DSH 0.2.1-rc.2 可能被兼容门禁拒绝，需实测/改 peer/版本豁免 |
| DebugBridge | 不支持 1.20.1 | 借 WebSocket 协议、endpoint 划分、安全模型 |
| whale-craft | DSH 模式/看门狗/记忆，通用 | 借 preset、策略、watchdog 设计 |
| Carpet: NeoForged | 支持 1.20.1 Forge | 假玩家、`/tick`、scarpet |
| `mc-runtime-test` | 支持 1.20.1 Forge（Linux CI） | 公开仓库的 CI 冒烟；不作为本地 Windows 方案 |
| `minecraft-dev` 的 `mc_gradle` | 通用 | 跑 `gradlew`、超时杀进程树、输出截断 |

---

## 2. 运行时拓扑：同用户、同桌面、进程内探针

### 2.1 为什么不需要独立用户/VM

- 客户端操作由 **进程内 probe** 完成：`Screen.mouseClicked`、`KeyMapping.setDown`、`Minecraft.gameMode`，不依赖 OS 前台窗口，也不抢用户鼠标。
- 截图由 **进程内 `glReadPixels`/FBO** 完成，不依赖窗口是否可见。
- 因此：**同一个 Windows 用户、同一个桌面、独立 gameDir** 就够 v1。
- 用户仍然可以正常用电脑；MC 窗口放在其他窗口后面、第二显示器、或第二个虚拟桌面即可。
- 不要最小化：MC 1.21.2+ 有最小化降帧；1.20.1 虽无该限制，但最小化仍可能让 framebuffer 退化。保持在桌面（可被遮挡）最稳。

### 2.2 为什么不用"桌面分身"

你提到的"桌面分身"其实是 **BetterGI 的功能，不是 Windows 自带功能**：

- BetterGI 文档写得很清楚：它**创建一个独立的 Windows 会话，并通过本机 RDP 把画面嵌进窗口**（[桌面分身启动](https://www.bettergi.com/feats/command/session.html)）。
- 它适合原神这类 **D3D** 游戏；但 **RDP 不支持 OpenGL > 1.1**（[ESRI 官方说明](https://support.esri.com/en-us/knowledge-base/unable-to-start-cityengine-via-remote-desktop-connectio-000012119)），Minecraft Java 需要 OpenGL 3.2+。微软也明确建议不要通过 RDP/VM 启动 MC（[Microsoft Q&A](https://learn.microsoft.com/en-nz/answers/questions/5984489/how-to-fix-the-glfw-error-65542-wgl-the-driver-doe)）。
- 它还要求非家庭版、RDP/凭据设置、一个 Windows 只能一个分身、与 RDPWrap 不兼容、可能影响关机/重启。
- **结论：桌面分身不适合 MC，不要作为 v1 方案。** 同桌面 + 进程内 probe 更简单、更快、更稳。

> 如果一定要"视觉上隔离"，用 Windows 的**虚拟桌面**（Task View）把 MC 窗口放过去即可；但注意它不是独立会话，非当前桌面的窗口会被 DWM cloak，渲染可能被节流。**输入不受影响，因为 probe 是进程内注入。**

### 2.3 进程/目录/端口

- 每次 run：`<project>/.mcdev/runs/<uuid>/`，内含 `gameDir/`、`logs/`、`screens/`、`db.sqlite`。
- 服务端和客户端各一个进程；`taskkill /T /F` 兜底；可选 Job Object（KILL_ON_JOB_CLOSE）。
- 端口：25565 起随机；先 bind 0 探活；注意 Hyper-V/WSL 的保留端口段（`netsh int ipv4 show excludedportrange protocol=tcp`）。
- 服务器：`online-mode=false`、固定种子、`eula=true`（开发环境）、固定 view/simulation distance。
- 客户端：854×480 或 1280×720；关 vsync、关 FPS limiter、`pauseOnLostFocus=false`、低渲染距离、关声音。
- 清理：先杀进程再删目录；保留 `db.sqlite` + 证据截图；世界目录可删。

### 2.4 隔离分档

| 档 | 场景 | 做法 |
|---|---|---|
| A 同用户 + 独立目录 | **v1 默认**，自己的模组/可信依赖 | 独立 gameDir + 进程内 probe + 端口隔离 |
| B Windows Sandbox | 不可信参考模组，只做功能冒烟 | Mesa llvmpipe（GL 4.5 软渲染，10–25 FPS）；不适合性能/图形 |
| C VM / WSL2 | 批量兼容、需要快照 | 可选；不作为 v1 依赖 |

---

## 3. 功能级测试：一张功能一张卡

这是本轮最重要的方法变化。**不要"跑一遍看看"，而是"每个功能一张测试卡，逐项验收"。**

### 3.1 三层文档

| 文件 | 作用 | 谁维护 |
|---|---|---|
| `SPEC.md` | 用户需求 → 可测的验收标准 | Lead + 用户 |
| `TESTPLAN.md` | 功能 × 检查项矩阵，每行一张卡 | Lead + Tester |
| `RUNS/` | 每次运行的 DB + 截图 + 日志 + 报告 | 工具自动 |

### 3.2 功能卡模板

```yaml
id: F-001
title: 充能方块基础
spec:
  - 放置后方块实体有 10000 FE 缓存
  - 右键打开 GUI，标题为 "Charger"
  - 比较器输出随能量 0..15 变化
  - 漏斗可从上方输入
risk: [block-entity, gui, capability, comparator]
tiers:
  static: true          # 注册/模型/贴图/语言/配方检查
  gametest: true        # runGameTestServer
  server_scenario: true # Carpet /player + /tick
  client_ui: true       # 打开 GUI、点击、截图
setup:
  commands:
    - "setblock 0 64 0 mod:charger"
    - "give @p mod:charged_item"
actions:
  - use_item_on: {pos: [0, 64, 0], face: up}
  - wait_ticks: 20
assert:
  - block_entity_nbt: {pos: [0, 64, 0], path: "Energy", op: ">=", value: 100}
  - screen_title: "Charger"
  - no_exception: true
ui:
  open: true
  click: [{slot: 1, button: 0, shift: true}]
  screenshot: true
evidence:
  - run_id
  - screenshots
  - gametest_xml
status: pending
```

### 3.3 功能卡的执行顺序

1. **静态审计**（不启动游戏）：注册表、模型、贴图、语言键、配方、标签、loot table、blockstate 变体、mixin 配置。
2. **GameTest**：`runGameTestServer`，测逻辑/方块/实体/物品/配方。
3. **服务端 scenario**：Carpet `/player`、`/tick freeze`、命令、多人、权限、重载。
4. **客户端 UI**：打开 GUI、鼠标点击、shift-click、tooltip、截图、视觉 diff。
5. **多人/兼容**（按需）：两个客户端、参考模组、整合包。
6. **报告**：卡片状态 + 证据 + "需要人看"的项。

### 3.4 鼠标/键盘/截图：保留，但只用于该用的地方

- **保留**：
  - `ui.snapshot`：控件树、容器槽位、tooltip、`drawString` 抓取。
  - `ui.click`：`Screen.mouseClicked` / `mouseReleased` / `mouseScrolled`。
  - `ui.key`：`keyPressed` / `charTyped`；`KeyMapping.setDown` 用于按键绑定测试。
  - `ui.screenshot`：下一帧 `glReadPixels`/FBO，保存 PNG；AI 用 `read_image` 看。
  - `ui.assert_*`：标题、按钮存在/可用、槽位物品、tooltip 文本、文本不越界。
- **不做**：WASD 实时走位、实时看画面点鼠标。需要移动时用 `tp`、`/player`、命令或 scenario 的 `walk_to`。
- **为什么**：LLM 实时控制慢、贵、脆；UI 点击/截图是"针对性测试"，不是"代替人玩"。

### 3.5 报告矩阵

最终报告是一张矩阵：

| 功能 | 静态 | GameTest | 服务端 | 客户端 | 多人 | 性能 | 结论 |
|---|---|---|---|---|---|---|---|
| 充能方块 | ✅ | ✅ | ✅ | ✅ | ➖ | ✅ | 通过 |
| 充能 GUI | ✅ | ➖ | ✅ | ❌ | ➖ | ➖ | 槽位错位，已修 |

每格都带证据链接（run id、截图、日志、DB 查询）。**没有证据 = 没测。**

---

## 4. 多智能体：用 DSH 官方 Agent Teams 拆测试工程

### 4.1 这个插件是什么（已核实）

DSH 自带一个**官方实验性** bundle：`@deepseek-ai/dsh-experimental-agent-team-profile`（0.2.1-rc.2，默认关闭）。它包含：

- `@deepseek-ai/dsh-experimental-agent-team`：Lead + 具名 teammate + 直接消息 + 持久任务板。
- `@deepseek-ai/dsh-experimental-tool-agent-team`：9 个工具。
- `@deepseek-ai/dsh-experimental-client-ui-agent-team`：Web UI。
- 它会**覆盖**传统 `tool-subagent` 系列（同名工具冲突），所以二者不能随便同时挂。

9 个工具：

| 能力 | 工具 |
|---|---|
| 创建 teammate | `spawn_teammate`（仅 Lead） |
| 发消息 | `send_message` |
| 查看/等待 | `list_agents` / `wait_agent` |
| 打断 | `interrupt_agent`（仅 Lead） |
| 任务板 | `team_task_create` / `team_task_list` / `team_task_get` / `team_task_update` |

约束（官方 README）：
- 最多 16 个 teammate、256 个任务、单条消息 64 KiB。
- **共享 cwd**；没有文件系统隔离；`writeScopes` 只是重叠警告，不阻塞。
- 实验性，schema 可能变；不保证稳定。
- 同一进程、同一 checkout；不做 worktree/远程成员/合并。

### 4.2 推荐的团队编制（从 3 人起步）

| 角色 | 负责 | 拥有的路径 | 主要工具 |
|---|---|---|---|
| **Lead** | SPEC/TESTPLAN、分卡、验收、最终报告 | `SPEC.md`、`TESTPLAN.md`、`REPORT.md` | `team_task_*`、`wait_agent` |
| **Builder** | 写实现代码 | `src/main/java`、`src/main/resources` | 文件/编辑/构建 |
| **Tester** | 写 GameTest/scenario、跑卡、收证据 | `src/test`、`scenarios/`、`RUNS/` | `mcdev_gametest`、`mcdev_scenario`、`mcdev_ui` |
| **Analyst**（后加） | 读 run DB/日志、定位根因 | `analysis/` | `mcdev_sql`、`mcdev_logs` |
| **Reviewer**（后加） | 审 diff、审测试质量、防"改测试作弊" | `reviews/` | 读/搜索 |

**起步只开 Lead + Builder + Tester。** 等 run DB 和卡片稳定后，再加 Analyst/Reviewer。人越多，协调成本越高。

### 4.3 任务板 = 功能卡

- 每个功能卡 = 一个 `team_task`；依赖关系 = 卡的前置（如"先有方块注册，再有 GUI"）。
- `writeScopes` 写清路径，减少并发写冲突。
- Tester 跑完一张卡，把 `run_id`/截图/失败摘要写回任务；Lead 决定"修复/重跑/接受"。
- `wait_agent` 等长构建/长测试，替代轮询。
- **不要让 Builder 改 Tester 的测试目录**；测试 hash/审查兜底。
- 任务板有 Web UI 只读面板（会话头部）：roster + task board，显示 owner、blockers、readiness、writeScopes、overlap 警告；**用户可以直接把它当功能矩阵看**。面板不能改任务，改任务仍走 9 个工具。
- teammate 可选 fresh（无 Lead 历史，适合 Builder/Tester）或 fork（继承已完成轮次，适合 Reviewer）。

### 4.4 怎么接入（建议，不急着做）

0. **能力检测优先**：插件启动时看 `spawn_teammate` / `team_task_create` 是否可用；可用则走团队编排，否则自动退回 `subagent`/`workflow`。不要把实验特性做成硬依赖。
1. 先在普通会话里**以 profile bundle 方式启用**官方实验 bundle，做一次 2 teammate 的小试（Builder + Tester），确认稳定。
2. 稳定后，做一个**独立的"MC 模组开发（团队）"preset**，优先复用 profile 级已启用的 team 服务，再挂：
   - `@deepseek-ai/dsh-experimental-agent-team`
   - `@deepseek-ai/dsh-experimental-tool-agent-team`
   - 我们的 `mcdev` Host 插件
   - persona / 文件 / shell / 技能 / 压缩组
   - **禁用**传统 `tool-subagent*`，避免同名冲突
   > 注意：team 包可能是 Host/Profile 级服务，**能不能直接挂进 preset 需要实测**；如果不行，就保持 profile 级启用 + 模式里只挂工具/策略。
3. 默认的"MC 模组开发"preset 仍用稳定的 `subagent`/`workflow`；团队版是 opt-in。
4. 如果实验版不稳定，退回 `subagent` + 显式角色 prompt，功能不受影响。

> 公开插件不要默认依赖实验特性；把它做成"团队模式"开关/独立模式，是更稳的产品策略。

---

## 5. 记忆：项目记忆树 + 可选记忆插件

### 5.1 项目内记忆树（v1 必须）

```
<project>/
  AGENTS.md            # DSH 自动注入的工作区指令（短）
  MCDEV.md             # 本项目的开发约定：版本/命令/目录/禁忌
  SPEC.md              # 需求与验收标准
  TESTPLAN.md          # 功能卡矩阵
  DECISIONS.md         # ADR：为什么这么选
  KNOWN_BUGS.md        # 症状/复现/根因/状态
  API_NOTES.md         # 1.20.1 Forge API 的坑与符号
  scenarios/           # 可执行的 scenario
  .mcdev/runs/         # 每次运行的 DB + 证据
```

原则：
- **语义记忆**（决策、约定、坑）进 Markdown；**情景记忆**（这次运行发生了什么）进 run DB。
- 不把原始日志写进记忆；只写摘要、复现步骤、结论。
- `AGENTS.md` 保持短；长文档放 `MCDEV.md`/`SPEC.md`，按需读。
- 每张功能卡完成后，把"学到的东西"写进 `API_NOTES.md`/`KNOWN_BUGS.md`，而不是只留在对话里。

### 5.2 可选：`dsh-mnemon`（当前最成熟的 DSH 记忆插件之一）

[`dsh-mnemon`](https://www.dshbase.com/plugins/dsh-mnemon/)：542 stars，2026-10 仍在更新，支持 DSH 0.2.0-rc.2（0.2.1-rc.2 需实测，可能需版本豁免）。三层记忆：

- **Runtime memory**：`USER.md` / `MEMORY.md`，每轮注入，放偏好/工作约定。
- **Project Documents**：Markdown 项目文档，按需检索；正好可以指向 `SPEC.md`/`TESTPLAN.md`/`KNOWN_BUGS.md`。
- **Memory Spaces**：长期事实/决策/实体关系，按需召回；支持本地 Mnemon Native 和多种 Provider。

它还有 UI、turn memory bar、一键保存、ZIP 备份、Git sync、Memory reconciliation。**适合跨会话记住"这个项目为什么这么设计"和"用户偏好"**。

其他可参考：
- [`@jiangdaoli/dsh-memory`](https://www.npmjs.com/package/@jiangdaoli/dsh-memory)：自动沉淀事实/决策/做法 + 用户画像，0.1.0，较轻。
- [`dsh-agentmemory`](https://dshbase.com/zh/plugins/dsh-agentmemory/)：接本地 agentmemory server（:3111），54 个 `memory_*` 工具，早期项目，依赖外部服务。
- `dsh-tool-memory`：3 个 prompt section + 6 个工具，跨会话持久记忆。

**建议**：
- v1 不硬依赖任何记忆插件；项目记忆树 + `AGENTS.md` 已够用。
- v1.5 把 `dsh-mnemon` 作为可选增强：Project Documents 指向项目文档，Runtime memory 放用户偏好；不要让它把原始日志灌进上下文。
- 记忆插件的价值是"跨会话/跨项目"，而 run DB 的价值是"这次运行到底发生了什么"，两者不冲突。

---

## 6. 公开插件形态

### 6.0 对开发者的价值（一句话）

> "你不用手动开游戏、装前置、点 GUI、翻日志；AI 按功能卡自动跑，给你证据。"

具体价值：
- **回归不漏**：每个功能卡 = 一个可重复的 GameTest/scenario/UI 检查；改一行代码就能重跑。
- **客户端也能测**：GUI、按钮、shift-click、tooltip、截图对比，不再只靠人眼。
- **兼容有依据**：AI 能进参考模组看注册表/配方/GUI，写兼容代码，而不是猜。
- **报告可复现**：run DB + 截图 + 日志 + 最小复现；bug 不再靠玩家口述。
- **人只做主观验收**：好玩/好看/平衡留给人；重复验证交给 AI。

**Beta 演示脚本**（也是最好的说明书）：
1. 装插件，新建"MC 模组开发"会话。
2. `mcdev_init` 创建一个 1.20.1 Forge 示例模组（方块 + 物品 + GUI + 配方）。
3. AI 写 `SPEC.md` 和 5 张功能卡。
4. AI 跑 GameTest → 修 2 个 bug → 跑服务端 scenario → 开客户端点 GUI、截图。
5. `mcdev_report` 输出矩阵；用户点"加入 LAN"进去玩 5 分钟。
6. 用户说"这个按钮位置不对"，AI 改布局、重跑 UI 卡、给出新截图。

### 6.1 包与模式

- npm 包名：`mcdev` 已被 Accenture 的 Salesforce 工具占用（10.1.0）；`minecraft-mod-dev` 与 `mcdev-dsh` 目前空着。建议 **`minecraft-mod-dev`** 作为包名，工具前缀仍用 `mcdev_*`，二进制/模式名用 `mcdev`。
- `package.json`：
  - `dsh.bundle.patch`: `["./cordis.patch.yml", "./presets/mcmod.patch.yml", "./presets/mcmod-team.patch.yml"]`
  - `dsh.client`: Web UI。
  - peer 依赖逐条枚举 DSH 版本线（参考 `minecraft-dev` 的兼容门禁教训）。
- 两个 preset：
  - `mcmod`（稳定）：persona + 文件 + pwsh + jobs + subagent/workflow + todo + web + present + `mcdev` 工具 + 技能 + 压缩。
  - `mcmod-team`（实验）：同上，但把 subagent 换成 Agent Teams 的 9 工具。
- 运行时策略：文件限制在项目 + `.mcdev/`；危险操作审批；网络白名单（Maven/Modrinth/Mojang）；工具按模式可见。

### 6.2 Host 插件提供什么

- **服务**：`mcdev`（EnvManager / BuildManager / ScenarioRunner / ArtifactStore / SymbolIndex / LabManager / Reporter）。
- **工具（v1 10 个）**：
  1. `mcdev_init` — 创建/导入 1.20.1 Forge 项目
  2. `mcdev_doctor` — JDK 17、Gradle、端口、防火墙、probe 版本检查
  3. `mcdev_build` — `gradlew build` + 结构化错误
  4. `mcdev_gametest` — `runGameTestServer` + 退出码 + 报告
  5. `mcdev_scenario` — 跑功能卡/场景（Carpet + probe）
  6. `mcdev_client` — 启停客户端、状态、就绪
  7. `mcdev_ui` — snapshot / click / key / type / screenshot
  8. `mcdev_sql` — 只读查询 run DB
  9. `mcdev_report` — 功能矩阵 + 证据 + 待人工确认
  10. `mcdev_cleanup` — 杀进程、删世界、GC
- **看门狗**：服务器就绪、客户端崩溃、测试失败、用户聊天 → 唤醒 agent。
- **UI**：状态条、运行列表、功能矩阵、截图浏览、"加入 LAN"、"人类接管"。
- **Probe 安装**：把预编译的 `mcdev-probe-1.20.1-forge.jar` 复制到 `run/mods/`；不改 `build.gradle`。配置 `run/config/mcdev-probe.toml`（端口/token/开关）。

### 6.3 探针（Forge 1.20.1）

一个独立的 Forge mod（不是目标模组的一部分），模块：

- `core`：localhost HTTP/WS、token、线程调度、超时。
- `log`：log4j2 Appender、环形缓冲、异常、崩溃。
- `world` / `player` / `registry`：方块/实体/背包/注册表/配方/标签。
- `ui`：Screen 树、容器槽位、tooltip、`drawString` 抓取。
- `input`：`Screen.mouseClicked` / `keyPressed` / `charTyped`；`KeyMapping`；可选 GLFW。
- `capture`：下一帧 `glReadPixels`/FBO、PNG、contact sheet。
- `test`：命令、tick 冻结钩子、随机种子、快照、断言钩子。
- `perf`：MSPT、FPS、内存、GC、实体/区块数。

**关键实现细节**：
- 1.20.1 用 `GuiGraphics`；`drawString` 有多个重载，Mixin 要覆盖常用入口。
- 容器 GUI 的槽位在 `AbstractContainerScreen.menu.slots`，不在 widget 树；坐标相对 `leftPos/topPos`。
- 截图必须在 render 线程、framebuffer 当前绑定、当帧绘制完成后；"下一帧"语义。
- 输入切主线程；不要从网络线程直接动世界。
- 端口只绑 127.0.0.1；token 写在 `run/config`；首次启动打印开发者提示。
- 只做"结构化观测 + 定向输入 + 截图"；**不做**寻路、WASD 实时操作、渲染注入、作弊。

**最小 probe v0（E4 的目标，约 300–500 行 Java）**：
1. `GET /status`：MC 版本、Forge 版本、是否在客户端、当前 Screen 类名、tick、FPS。
2. `GET /log/tail?n=200`：环形缓冲的最近日志（结构化 JSON）。
3. `POST /command`：以服务器控制台/玩家身份执行命令（白名单）。
4. `GET /screenshot`：下一帧截图，返回 PNG 路径。
5. `GET /ui/snapshot`：当前 Screen 的控件树 + 容器槽位。
6. `POST /ui/click`：按坐标/槽位点击。
7. `GET /world/block?pos=...`：方块/方块实体 NBT。

先这 7 个，够跑通"打开 GUI → 点击 → 截图 → 断言"的最小闭环。

### 6.4 安装与上手（目标：3 条命令）

```text
dsh plugin add minecraft-mod-dev
# 新会话选 "MC 模组开发"
mcdev_init          # 创建/导入 1.20.1 Forge 项目
mcdev_doctor        # 检查环境
mcdev_build         # 第一次构建
mcdev_gametest      # 跑 GameTest
mcdev_scenario F-001
mcdev_ui screenshot
mcdev_report
```

### 6.5 公开 Beta 的门槛

- 只支持 **MC 1.20.1 + Forge 47.4.x + Java 17 + Windows 10/11**；其它版本明确拒绝，不"尽力而为"。
- 不要求管理员；不建独立用户；不改系统设置；不装驱动。
- 默认不上传任何代码/日志/截图；遥测 opt-in。
- 文档：Quickstart、功能卡写法、Probe API、FAQ、故障排查、安全说明。
- 示例：一个 1.20.1 Forge 示例模组 + 5 张功能卡 + 一份报告。
- 先行用户：10–20 个 1.20.1 Forge 模组作者；收集"节省时间/误报/崩溃"三类反馈。

---

## 7. 架构（v2）

```
┌─────────────────────────────────────────────────────────────┐
│ DSH：MC 模组开发模式（preset）+ Host 插件 + 技能              │
│  可选：Agent Teams（实验）/ dsh-mnemon（记忆）                │
│  mcdev_* 工具：init/doctor/build/gametest/scenario/client/   │
│                ui/sql/report/cleanup                         │
└───────────────────────────┬─────────────────────────────────┘
                            │ localhost HTTP/WS + 子进程
┌───────────────────────────▼─────────────────────────────────┐
│ 编排器（Node，DSH Host 进程内）                               │
│  EnvManager  BuildManager  ScenarioRunner  ArtifactStore     │
│  SymbolIndex  LabManager   Reporter        Cleaner           │
└───────┬───────────────────────────────┬─────────────────────┘
        │                               │
┌───────▼──────────────┐      ┌─────────▼─────────────────────┐
│ Forge 1.20.1 服务端   │      │ Forge 1.20.1 客户端            │
│ - mcdev probe         │      │ - mcdev probe（UI/输入/截图）   │
│ - Carpet: NeoForged   │      │ - 目标模组                      │
│ - GameTest            │      │ - 可选第二个客户端（多人）       │
└───────┬──────────────┘      └─────────┬─────────────────────┘
        │                               │
        └──────────────┬────────────────┘
                       ▼
        ┌─────────────────────────────────┐
        │ run DB（SQLite）+ 证据仓库        │
        │ logs/events/state/screens/       │
        │ assertions/coverage/packets      │
        └─────────────────────────────────┘
```

---

## 8. 路线图（1.20.1 Forge only）

### P0：内部 Alpha（3–4 周）

- 1.20.1 Forge 模板（复用 `minecraft-dev` 或 MDK）。
- `mcdev_init` / `mcdev_doctor` / `mcdev_build` / `mcdev_gametest`。
- `runGameTestServer` + 退出码 + 日志解析。
- SQLite run DB + `mcdev_sql`。
- 3 张功能卡 + 一个示例模组。
- **验收**：AI 能在 30 分钟内定位并修复 ≥3/5 个注入 bug；T0–T2 循环 < 3 分钟。

### P1：服务端闭环（4–6 周）

- Probe 服务端模块（log/registry/world/command）。
- Carpet: NeoForged（`/player`、`/tick freeze`、scarpet）。
- Scenario DSL + 功能卡执行器。
- 覆盖率（JaCoCo on Java 17）+ 报告。
- **验收**：一个 1.20.1 Forge 模组的 10 张卡可在 CI/本地跑；失败有最小复现。

### P2：客户端闭环（6–8 周）

- Forge 1.20.1 客户端 probe：UI 树、容器槽位、`drawString`、截图、鼠标/键盘。
- 视觉基线 + diff + contact sheet。
- 客户端功能卡（GUI/按键/tooltip/布局）。
- **验收**：AI 能打开 GUI、点按钮、shift-click、截图对比；能抓一个 UI 回归。

### P3：团队 + 记忆 + 公开 Beta（4–6 周）

- `mcmod-team` preset（Agent Teams 9 工具）。
- `dsh-mnemon` 可选集成（Project Documents/Runtime memory）。
- 打包、文档、示例、CI、安装器、版本门禁。
- **验收**：10–20 个外部用户能按 Quickstart 跑通；收集反馈。

### P4：参考模组与兼容矩阵（持续）

- 反编译 + 符号索引 + API 查询。
- 参考模组实验室（独立实例、注册表/配方 dump、GUI 观察）。
- 1.20.1 常用 mod 兼容矩阵（JEI/Create/AE2 等）。
- **验收**：AI 能回答"这个参考模组怎么注册 GUI/配方"并写兼容代码。

---

## 9. 风险

| 风险 | 严重度 | 说明 | 缓解 |
|---|---|---|---|
| 1.20.1 Forge 客户端探针从零写 | 高 | 无 Client Gametest、DebugBridge 不支持 1.20.1 | 先做最小 probe（log+command+screenshot）；借 DebugBridge 协议；分阶段 |
| 桌面分身/RDP 不可用 | 高 | RDP OpenGL 1.1；MC 需要 3.2 | 不用；同桌面 + 进程内 probe |
| 判据/测试作弊 | 高 | AI 可能改测试让它过 | 测试目录归 Tester；hash/审查；Builder 不改测试 |
| 上下文爆炸 | 高 | 日志/截图太多 | run DB + SQL + 摘要 + 按需截图 |
| 1.20.1 生态老 | 中 | 新 API/工具优先 1.21+/26.x | 只做 1.20.1；复用成熟 MDK/教程；不追新 |
| Agent Teams 实验性 | 中 | schema 可能变、共享 cwd | 作为 opt-in；先 2 人试点；回退 subagent |
| 记忆插件依赖 | 中 | 第三方、上下文膨胀 | 项目记忆树为主；记忆插件可选 |
| 公开维护/DSH 升级 | 中 | preset 无继承、peer 门禁 | 薄适配；逐条枚举版本；跟随 `minecraft-dev` |
| 安全/供应链 | 中 | 跑未知 mod、Gradle 插件 | 同用户 + 目录隔离；不可信 mod 用 Sandbox；网络白名单 |

---

## 10. 验证实验（1.20.1 专用）

| # | 实验 | 目的 | 成本 |
|---|---|---|---|
| E1 | Forge 47.4.28 MDK + `runGameTestServer` + 3 个 GameTest | 验证主判据/退出码/日志 | 半天 |
| E2 | Carpet: NeoForged + `/player` + `/tick freeze` + scarpet | 验证假玩家/tick 控制 | 半天 |
| E3 | `minecraft-dev` 起 1.20.1 Forge 项目并 build | 评估复用 | 半天 |
| E4 | 最小 Forge 1.20.1 probe：HTTP + log + command + screenshot | 验证客户端探针 | 2–3 天 |
| E5 | 把 GameTest 日志 + 状态写 SQLite，AI 用 SQL 查 | 验证 run DB | 1 天 |
| E6 | 注入 5 类 bug，看 AI 只靠报告修复 | 验证闭环价值 | 2–3 天 |
| E7 | Agent Teams 2 人试点（Builder + Tester） | 验证团队协作 | 1 天 |
| E8 | `dsh-mnemon` 安装 + Project Documents 检索 | 验证记忆 | 半天 |
| E9 | 客户端 GUI 测试：打开、点击、shift-click、截图 | 验证鼠标/截图 | 1–2 天 |
| E10 | 测一次完整循环的时间/token | 验证经济性 | 1 天 |

---

## 11. 还需要你定的

1. **公开插件名字**：`mcdev` / `minecraft-mod-dev` / `craftdev`？（npm 上先查重）
2. **团队模式默认开还是 opt-in**？建议 opt-in（实验性）。
3. **记忆插件默认装不装**？建议不硬依赖，文档推荐 `dsh-mnemon`。
4. **公开 Beta 是否只限 Windows + 1.20.1 Forge**？建议是。
5. **是否接受先只做服务端闭环（P0/P1），客户端探针放 P2**？这是最快出可用版本的路。
6. **是否要我在下一步直接做 E1–E4 的实验设计/环境清单**（仍然不写代码，只出可执行的实验步骤）？

---

## 12. 参考资料

- Forge 1.20.1 下载：https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html
- Forge 1.20.1 GameTest：https://docs.minecraftforge.net/en/1.20.1/misc/gametest/
- Carpet: NeoForged：https://modrinth.com/mod/neoforge-carpet
- `minecraft-dev`：https://github.com/sikadi233-hub/minecraft-dev
- DebugBridge：https://github.com/use-ai-for-mc/debugbridge
- `mc-runtime-test`：https://github.com/headlesshq/mc-runtime-test
- BetterGI 桌面分身：https://www.bettergi.com/feats/command/session.html
- RDP OpenGL 1.1（ESRI）：https://support.esri.com/en-us/knowledge-base/unable-to-start-cityengine-via-remote-desktop-connectio-000012119
- Microsoft Q&A（不要用 RDP 跑 MC）：https://learn.microsoft.com/en-nz/answers/questions/5984489/how-to-fix-the-glfw-error-65542-wgl-the-driver-doe
- DSH Agent Teams（实验）：`@deepseek-ai/dsh-experimental-agent-team-profile`（本机 DSH 0.2.1-rc.2 内置，默认关闭）
- `dsh-mnemon`：https://www.dshbase.com/plugins/dsh-mnemon/
- `@jiangdaoli/dsh-memory`：https://www.npmjs.com/package/@jiangdaoli/dsh-memory
- `dsh-agentmemory`：https://dshbase.com/zh/plugins/dsh-agentmemory/

---

## 13. 决策记录（ADR）

| # | 决策 | 理由 | 代价 |
|---|---|---|---|
| ADR-001 | 只做 MC 1.20.1 + Forge | 生态最大、API 稳定、资料最多、Forge 47.4.x 仍在维护；公开用户大多在 1.20.1 | 放弃新版本/新 API；后续要另开分支 |
| ADR-002 | 同用户 + 独立目录，不上 VM/独立用户 | 进程内 probe 不需要 OS 隔离；RDP 桌面分身 OpenGL 1.1 不可用；VM 成本高 | 不能防恶意模组；用 Windows Sandbox 兜底 |
| ADR-003 | 功能卡逐项验收 | 用户明确要求"一个功能一个功能测"；卡片可回归、可报告、可分工 | 前期写卡成本；需要 SPEC 质量 |
| ADR-004 | Forge 1.20.1 客户端 probe 自研 | Fabric Client Gametest 不可用；DebugBridge 不支持 1.20.1；这是最大空白 | 6–8 周工作量；版本维护 |
| ADR-005 | Agent Teams 作为 opt-in | 官方实验性、共享 cwd、无 FS 隔离；不能默认依赖 | 团队模式需要额外验证；回退 subagent |
| ADR-006 | 记忆插件不硬依赖 | 第三方、上下文/兼容风险；项目记忆树 + run DB 已够 | 跨会话体验弱一些；文档推荐 `dsh-mnemon` |
| ADR-007 | 公开 Beta 只限 Windows + 1.20.1 Forge | 降低支持矩阵；先把单点做深 | 初期用户面窄 |
| ADR-008 | 复用 `minecraft-dev` 源码/模板 | MIT；脚手架/版本矩阵已成熟；避免重造 | 需要处理 peer 门禁/上游差异 |

**如果未来要扩版本**：先加 1.20.1 Fabric（可复用 DebugBridge/Fabric Client Gametest），再加 1.21.1 NeoForge；不要一次铺开。
