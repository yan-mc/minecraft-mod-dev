# Minecraft 模组开发模式：可行性研究与方案设计

> 调研日期：2026-10-17
> 状态：讨论稿（未开始写实现代码）
> **2026-10-17 第二轮更新**：已确定 **MC 1.20.1 + Forge + Windows 本机 + 公开插件**。本文件保留通用调研与架构；**最新落地方案见 [MC模组开发模式-方案v2-1.20.1Forge.md](MC模组开发模式-方案v2-1.20.1Forge.md)**。
> 结论先行：**这件事可行，但要把"AI 像人一样进游戏自由玩"降级为"AI 能启动、驱动、观测、断言、修复"。** 前者是演示，后者才是能落地的产品。

---

## 0. 一页结论

### 0.1 能做成的

- **闭环可行**：需求 → 代码 → 构建 → 起服务端/客户端 → 自动测试 → 读日志/状态 → 修复，这条链路在 2026 年已经有足够多的现成零件。
- **模式可行**：DSH 的"模式"就是一个 `@deepseek-ai/dsh-agent-preset` 声明。whale-craft 已经用同一机制加了"MC 模式 / MC+ 模式"，证明插件可以加第五个模式，并在运行时收紧工具面。
- **观测可行**：客户端进程内 probe 可以拿到截图、控件树、容器槽位、聊天、实体/方块/注册表、日志、异常、tick 时间、包流；服务端 probe 还能接 GameTest 和假玩家。
- **判据部分可行**：崩溃、缺资源、注册错误、配方错误、NBT 往返、GUI 打不开、多人不同步、性能回归，这些都能自动判；"好不好玩/平不平衡/好不好看"不能。
- **生态已有底座**：DSH 里已经有人把"脚手架 + Gradle 构建 + 1.7.10–26.3 版本矩阵"做成了 [`minecraft-dev`](https://github.com/sikadi233-hub/minecraft-dev)，但它停在 `gradlew build` 退出码 0。**这恰好说明缺的不是"写"，而是"运行、观测、断言、修复"。**

### 0.2 不能指望的

- **不能指望"自由玩"发现大部分 bug。** 自由玩适合探索性测试和演示；主要判据必须来自规格、不变量、差分和 GameTest。
- **不能指望 AI 判断主观质量。** 手感、节奏、美术、平衡必须留给人。
- **不能指望零维护。** MC 版本、加载器 API、映射、输入层（26.3 的快照起改用 SDL3）都在变；桥接层是持续成本。
- **不能指望"装个 VM 就万事大吉"。** 真正的难点是测试判据、版本矩阵、上下文爆炸和 NeoForge 客户端自动化，不是虚拟化。
- **不能指望"没有竞争"。** `minecraft-dev`、DebugBridge、Orca 都在相邻位置；本项目的壁垒只能是"运行 + 观测 + 判据 + 修复"的闭环深度。

### 0.3 建议的第一步

先做 **4–6 周垂直切片**，不要一上来做通用平台：

0. 先评估：能否直接复用 `minecraft-dev` 的模板/技能，或把本项目做成它的"运行验证层"扩展；不要重复造脚手架。
1. 选定一个主攻版本/加载器（建议 NeoForge 1.21.1 或你实际主攻的 26.x）。
2. 做一个真实的小模组（方块 + 物品 + GUI + 配方 + 一个事件）。
3. 打通：构建 → `runGameTestServer` → 服务端 probe → 日志/状态入库 → 一个 scenario → 报告。
4. 故意注入 5 类 bug（NPE、缺贴图、配方错、GUI 槽位错、shift-click 复制），测量 AI 能不能只靠报告定位并修复。
5. 用数据决定是否继续投入客户端桥接、参考模组沙盒、多版本矩阵。

---

## 1. 把设想拆成能力，再逐项判可行性

你描述的方案其实包含 8 件不同的事，混在一起会显得"要么全能要么不能"，拆开看就清楚得多。

| # | 能力 | 你的设想 | 2026 年的现实 | 可行性 |
|---|---|---|---|---|
| C1 | 环境准备 | 自动装 Forge/前置/模组 | Gradle + Modrinth（免 key）+ packwiz/itzg/Prism；CurseForge 要 key | **高（CurseForge 受限）** |
| C2 | 构建与快速反馈 | 写完就能跑 | Gradle `runClient`/`runServer`/`runData`/`runGameTestServer`，首次 20–60min，之后增量 10–60s | **高** |
| C3 | 服务端/逻辑测试 | AI 进去试 | GameTest + Carpet 假玩家 + `/tick`，成熟；可无头、可并行 | **高** |
| C4 | 客户端启动与驱动 | AI 亲自操作 | Fabric 有 Client Gametest 和 DebugBridge；**NeoForge 基本空白** | **中** |
| C5 | 观测 | 底层数据/界面/报错全给 AI | 可做，但工程量大；需要进程内 probe + 结构化协议 | **中高** |
| C6 | 判据与定位 | AI 自己知道对不对 | 最难；需要 spec + 不变量 + 差分 + 覆盖率 + 人类兜底 | **中** |
| C7 | 参考模组沙盒 | 另开环境研究别的模组 | 与环境准备同构；加反编译/符号索引/注册表 dump | **中高** |
| C8 | 人类协作 | 用户进去一起玩 | LAN + 同版本客户端 + modpack 导出，容易 | **高** |

**加权判断**：
- 如果目标是"AI 自动跑回归、抓崩溃/缺资源/配方/GUI/性能问题、给出可复现报告"——**可行性 8/10**。
- 如果目标是"AI 自由玩几个小时，发现深层设计缺陷"——**可行性 3/10**。
- 如果目标是"AI 替代人类做主观验收"——**可行性 1/10**。

> 一句话：**把它做成"模组开发的 CI + 可观测性 + 自动修复"，而不是"AI 玩家"。**

---

## 2. 先例盘点：碎片都有，完整闭环没有

### 2.1 DSH 内部先例：whale-craft（模式机制已被验证）

[whale-craft](https://github.com/yzi1b/whale-craft) 是一个 DSH 原生插件，用 mineflayer 起一个无头机器人进 MC 服务器，让 agent 通过工具调用游玩。它对本项目最大的价值不是"玩 MC"，而是证明了三件事：

1. **插件可以加模式**：它用 [`presets/minecraft.patch.yml`](https://github.com/yzi1b/whale-craft/blob/main/presets/minecraft.patch.yml) 插入 `@deepseek-ai/dsh-agent-preset` 行，声明 `id: minecraft`、`name: MC模式`、`order: 5`，`plugins` 里只挂 persona + 文件工具 + job + present + 压缩组。
2. **模式可以收紧工具面**：`index.js` 里有 `applyMcModePolicy`，运行时再按模式过滤 `mc_*` 工具；文件工具被限制在 `.whale-craft/` 子目录。
3. **插件可以唤醒 agent**：它有 watchdog 接收游戏事件、记录消息、在需要时打断/唤醒主 agent，还有按工作区的长期记忆。

**局限**：mineflayer 是协议机器人，不加载 Java 模组，没有客户端渲染/UI，看不到 GUI、粒子、模型、按键。它适合"玩"，不适合"开发模组"。

**结论**：模式不是障碍；真正要新建的是"开发-验证"闭环和客户端探针。

### 2.2 客户端探针先例：DebugBridge + mcdev-mcp（接口设计可以直接抄）

[DebugBridge](https://github.com/use-ai-for-mc/debugbridge) 是一个 Fabric 客户端模组，明确"built for AI-assisted Minecraft development and debugging"：

- 在游戏内起 **localhost-only WebSocket**（9876–9886），请求/响应格式 `{id, type, payload}`。
- 原生 endpoint 直接返回结构化 JSON：`snapshot`、`nearbyEntities`、`entityDetails`、`lookedAtEntity`、`nearbyBlocks`、`blockDetails`、`screenInspect`、`chatHistory`、`screenshot`（framebuffer→JPEG）、`record_video`（多帧拼图，temp + 24h TTL）、`getItemTexture`、`setEntityGlow`、`search`、`status`。
- 一个 Groovy `execute` 逃生舱，可在 JVM 内跑脚本、访问 MC API。
- `runCommand`、`joinServer`/`leaveServer`/`quit` 默认关闭，需要显式开。
- 配套 [mcdev-mcp](https://github.com/use-ai-for-mc/mcdev-mcp) 把它包成 MCP 工具，并带静态反编译索引。
- 有 wire-shape 回归测试、smoke test、Vue 检查 UI；安全模型是 loopback + 首次启动开发者警告门。

**它已经解决了"AI 怎么看见客户端"的一半问题。** 但它缺的是：构建/依赖/启动编排、GameTest 集成、断言与 scenario、回归基线、覆盖率、参考模组沙盒、清理、人类接管，以及**服务端侧探针**。而且它只有 Fabric；NeoForge 没有等价物。

**结论**：**协议和 endpoint 划分照抄，不要重新发明**；把精力放在编排层和 NeoForge 适配上。

### 2.3 官方测试设施：GameTest 与 Fabric Client Gametest

**原版 GameTest**（1.21.5+ 重构，[Minecraft Wiki](https://minecraft.wiki/w/GameTest)）：
- `test_instance` 数据包注册表 + `structure/*.nbt` + `test_environment`；`/test run|runall|runthese|runfailed`。
- `server.jar` 里有 `net.minecraft.gametest.Main --report out.xml --tests ...`，**直接输出 JUnit 风格 XML，退出码 = 失败数**。这是最"无头"、最不依赖加载器的一条路。
- 能测：方块/实体/物品/红石/世界交互、模拟玩家、按 tick 调度。
- 不能测：客户端渲染/UI/按键/音效、真实多客户端、线程/异步、性能。

**NeoForge GameTest**（[官方文档](https://docs.neoforged.net/docs/1.21.1/misc/gametest/)）：
- `@GameTest` + `GameTestHelper`，结构模板 `.nbt`，相对坐标，`succeedWhen`/`runAtTickTime`/`assertBlockState` 等。
- `/test run|runall|runclosest|runthese|runfailed` 在游戏内跑。
- `runGameTestServer` 是特殊 run config：**退出码 = 必需测试失败数**，适合 CI。
- `neoforge.enabledGameTestNamespaces`（旧文档）/`neoforge.enableGameTest`（新 run config）控制加载；`@GameTestHolder(MODID)` 注册。
- `RegisterGameTestsEvent`/datagen 也可以注册 test instance/function。

**Forge GameTest**（[官方文档](https://docs.minecraftforge.net/en/latest/misc/gametest/)）：
- `@GameTest`/`@GameTestHolder` + `runGameTestServer` + `forge.enabledGameTestNamespaces`；26.3 的文档可能滞后，按 MDK 核对。

**Fabric GameTest**（[Fabric 文档](https://docs.fabricmc.net/develop/automatic-testing)）：
- `fabric-gametest-api-v1`，`@GameTest` 方法 + `fabric-gametest` entrypoint；`-Dfabric-api.gametest`；`-Dfabric-api.gametest.report-file=...`。
- Loom `runGametest`；`build` 会自动跑 server gametests。

**Fabric Client Gametest**（[Fabric 文档](https://docs.fabricmc.net/develop/automatic-testing)）：
- `FabricClientGameTest` + `ClientGameTestContext`，起真实客户端；`worldBuilder().create()` 建单机世界，也可以连 dedicated server。
- `takeScreenshot` / `assertScreenshotEquals` / `assertScreenshotContains`；`TestInput` 支持按键、鼠标、打字、滚轮、改窗口大小；可以导航 GUI。
- Gradle `runClientGameTest`；CI 可用 Xvfb；官方文档给了 Ubuntu 24.04 + Java 25 的示例。
- 注意：`@Experimental`，截图基线比较脆，版本升级容易碎。

**结论**：**服务端 GameTest 已经成熟到可以直接当 CI 判据；客户端 E2E 在 Fabric 上已被官方证明可行；NeoForge 只有服务端 GameTest，客户端自动化是最大空白，也是最大工作量。**

### 2.4 AI 玩 MC / 具身智能先例

- **MineRL / Project Malmo / MineDojo**：改造 MC 客户端，把观测（画面、背包、生命等）和动作（键鼠、合成）暴露给 Python/RL；证明"进程内改造客户端 + 固定 schema 观测"可规模化。但全部钉在 1.16.5 及更早。
- **Voyager**（NVIDIA）：mineflayer + LLM + 技能库，证明 LLM 能在 MC 里长期自主探索、写技能、自我修正；观测是纯文本，没有像素。
- **JARVIS-1 / Odyssey / Optimus-1/2**：MineRL/MineDojo 系的多模态 agent，证明"像素 + 文本 + 键鼠"路线；但仍然不是"开发-验证"闭环。
- **Cradle**：常被误列为 MC 项目；公开仓库其实只有 RDR2 / Stardew Valley / Cities: Skylines 等商业游戏（[仓库](https://github.com/BAAI-Agents/Cradle)）。它的价值是证明"纯截图 + 真实键鼠、零游戏 API"可行，同时也说明这条路线很慢。
- 这些项目的共同点：**它们是"让 AI 玩"，不是"让 AI 开发并验证模组"**。但它们的观测建模、动作分层、长期记忆、技能库设计都可以借鉴。

### 2.5 AI 做游戏测试的先例

- **TITAN**（[arXiv:2509.22170](https://arxiv.org/abs/2509.22170)，2025-09）：目前最接近本项目的公开工作。用 LLM agent 测两款商业 MMORPG，架构是"高维状态抽象 + 动作优先级 + 轨迹记忆/反思 + LLM oracle 出诊断报告"；论文自报 95% 任务完成、发现 4 个新 bug、部署进 8 条 QA 流水线。**架构可抄，但未开源，且只覆盖 MMO，不是 MC 模组。**
- **modl.ai**（[CB Insights](https://www.cbinsights.com/company/modlai)）：商业 AI QA，用 bot 跑 playthrough，找 bug、性能问题、平衡问题；与 Riot 有合作。**验证了需求，但不懂 MC 模组语义。**
- **VideoGameBench**（[HF Papers](https://huggingface.co/papers/2505.18134)）：纯视觉玩 10 款 90 年代游戏，最强模型只完成 0.48%。**这是"只靠截图 + 实时操作"路线的硬上限，提醒我们不要把截图当唯一判据。**
- **结论**：需求真实存在，架构也有参考；但通用游戏 QA 不懂 MC 模组语义（注册、资源、配方、capability、mixin），这正是本项目的差异化。

### 2.6 AI 写 MC 模组的先例

- **forge-coder**（[HuggingFace](https://huggingface.co/hwding/forge-coder-v1.21.11)）：专门微调的 Forge 模组代码生成模型。
- **ai-minecraft-mobs-creator**（[GitHub](https://github.com/shrimpwagon/ai-minecraft-mobs-creator)）：NeoForge 1.21.1 脚手架，用 Claude Code 驱动生成怪物/方块/物品/武器/护甲。
- **MCreator-Agent**（[GitHub](https://github.com/Nxkoo/MCreator-Agent)）：跑在 MCreator 插件里的本地 MCP server，暴露 workspace 检视、mod element 增删、`build`、`regenerate`、**`run client`、`run server`**。这是"把模组开发环境做成 agent 可调用工具"的最直接先例，接口设计值得借鉴；但它仍然没有"进游戏观测"的闭环。
- **Orca**（YC S24）与 **CreativeMode**：商业产品自述"自然语言 → 可玩的 Fabric/Forge/NeoForge/Bedrock 模组，AI 写代码 + 生成贴图 + 编译 + 测试"，但测试方式未公开，没有第三方可复现证据。
- **结论**：已经有"AI 生成模组代码/脚手架"，甚至"AI 调用 run client"，但**没有"生成 → 进游戏观测 → 断言 → 根据证据修复"的完整闭环**。这正是空白。

### 2.7 DSH 内部最近先例：`minecraft-dev`（只做到 build）

[`minecraft-dev`](https://github.com/sikadi233-hub/minecraft-dev)（npm `minecraft-dev`，MIT，2026-09 仍在更新）是当前 DSH 生态里最接近本项目的插件：

- **8 个技能**：Java/Gradle 构建、Paper 插件、Fabric、Forge（1.7.10/1.12.2/1.16.5/1.20.1 四时代）、NeoForge（1.20.1/1.21.1/1.21.x/26.2/26.3）、Spigot 老线、大型模组附属（28 个 mod 的 curse.maven 坐标与扩展点）。
- **3 个工具**：`mc_scaffold`（五平台脚手架 + 对应时代 Gradle wrapper）、`mc_gradle`（跑 gradlew，超时杀进程树、输出截断）、`mc_codex`（调 Codex 做架构）。
- **4 个子代理**：plan → skeleton → content → verify，串行委派，共享 `PLAN.md`。
- **2 个 preset**：Minecraft 专家、Minecraft 架构师。
- **版本钉死**：26.2 的 fabric `loom 1.17.19 / loader 0.19.3 / api 0.157.0+26.2`，neoforge `moddev 2.0.144 / 26.2.0.59`；26.3 的 neoforge 仍是 `-beta`；Java 25。
- **终点**：`gradlew build` 退出码 0。**没有进游戏、没有观测、没有断言、没有回归。**

**这意味着两件事**：
1. "写 + 构建 + 版本矩阵"这半段已经有开源实现，**不必从零重造**；可以复用它的模板/技能，或直接把它作为上游依赖。
2. 差异化非常清晰：**运行、观测、断言、修复**。这正是本项目要做的。

### 2.8 版本事实（2026-10）

Minecraft 已改用年份版本号：

| 版本 | 名称 | 日期 | Java | 备注 |
|---|---|---|---|---|
| 26.1 | Tiny Takeover | 2026-03-24 | 25 | 26.x 起点；官方 jar 起不再混淆 |
| 26.2 | Chaos Cubed | 2026-06-16 | 25 | |
| 26.3 | Wilderness Bound | 2026-09-15 | 25 | **当前 stable**；协议 777；NeoForge/Paper 仍 beta/alpha |
| 26.4 | — | 快照中 | 25 | 未发布 |

**加载器基线（2026-10）**：

| Loader | 26.3 状态 | Gradle 插件 | 备注 |
|---|---|---|---|
| Forge | 26.3-66.0.13（10-16） | ForgeGradle 7.0.38 | **仍在活跃维护，不是死项目** |
| NeoForge | 26.3.0.x-beta | ModDevGradle 2.0.147 | 1.21+ 主流；NeoGradle 仍可用但推荐 MDG |
| Fabric | 26.3 当日支持 | Loom 1.18 | Loader 0.19 系列；非混淆版本跳过 remap |

**映射**：26.1 起官方 Jar 不再混淆，自带类/方法/参数名，version JSON 不再有 obfuscation maps；Yarn 2025 起弃用，Fabric 要求迁移到 Mojang mappings；Parchment 最新只到 1.21.11，26.x 实际退休。**26.x 的符号索引比 1.20.1 简单得多。**

对本案的影响：
- 主攻 26.x 时，Java 25、Forge/NeoForge 都在跟版；Forge 并没有退出，你仍然可以用 Forge 26.3。
- 主攻 1.21.1 时，NeoForge 21.1.x + Java 21 更稳，社区模组最多。
- 主攻 1.20.1 时，Forge 47.x + Java 17，legacy 生态最大，但 API 老、客户端自动化资料少、映射更麻烦。**注意：Forge 26.3 仍在维护，不要把 Forge 等同于 1.20.1。**
- **学术界的 MineRL/MineDojo 全部钉在 1.16.5**，观测/动作方案不能直接迁移；但这也意味着我们没有历史包袱。

### 2.9 先例对照表

| 项目 | 能做什么 | 缺什么 | 对本案的价值 |
|---|---|---|---|
| whale-craft | DSH 模式 + 协议 bot 玩 MC | 不加载模组、无客户端 UI、无开发闭环 | 模式/策略/看门狗范本 |
| DebugBridge | Fabric 客户端结构化探针 | 无编排、无断言、无服务端、Fabric only | 协议/endpoint 范本 |
| Fabric Client Gametest | 官方客户端 E2E + 截图断言 | 测试形态、Fabric only、无常驻服务 | 客户端自动化可行性证明 |
| NeoForge GameTest | 服务端逻辑测试 + CI 退出码 | 无客户端、无 GUI | 最便宜的验证层 |
| MineRL/Malmo/MineDojo | 改造客户端给 RL 用 | 不是开发闭环 | 观测/动作建模 |
| Voyager/Cradle | LLM 玩 MC | 不是开发闭环 | 长期记忆/技能库 |
| modl.ai / TITAN | 商业/学术 AI 游戏 QA | 不懂 MC 模组 | 需求与架构验证 |
| `minecraft-dev` | DSH 内的 MC 脚手架 + 构建 + 版本矩阵 | 只到 build，无运行/观测/断言 | **上游底座/协作对象** |
| forge-coder / ai-minecraft-mobs-creator | AI 生成模组代码/脚手架 | 无游戏内验证 | 代码生成侧 |
| **本案** | **开发 + 运行 + 观测 + 断言 + 修复闭环** | **要自己搭** | **集成创新** |

---

## 3. 推荐架构：一个编排器 + 两个探针 + 一个运行数据库

### 3.1 总览

```
┌─────────────────────────────────────────────────────────────┐
│ DSH：第五个模式（preset）+ Host 插件 + 技能                    │
│  - persona / 工具策略 / 长任务 / 子代理 / 报告                 │
│  - mcdev_* 工具：build / run / scenario / query / report      │
└───────────────────────────┬─────────────────────────────────┘
                            │ localhost JSON-RPC / WebSocket
┌───────────────────────────▼─────────────────────────────────┐
│ 编排器（Node，跑在 DSH Host 进程或独立 daemon）                │
│  EnvManager  BuildManager  ScenarioRunner  ArtifactStore      │
│  SymbolIndex  LabManager   Reporter        Cleaner            │
└───────┬───────────────────────────────┬─────────────────────┘
        │                               │
┌───────▼──────────────┐      ┌─────────▼─────────────────────┐
│ 专用服务器            │      │ 真实客户端                     │
│ - 服务端 probe        │      │ - 客户端 probe（截图/UI/输入）  │
│ - GameTest            │      │ - 可选 Carpet 假玩家           │
│ - Carpet / 命令       │      │ - 可选 Baritone/寻路           │
└───────┬──────────────┘      └─────────┬─────────────────────┘
        │                               │
        └──────────────┬────────────────┘
                       ▼
        ┌─────────────────────────────────┐
        │ 运行数据库（SQLite）+ 产物仓库     │
        │ logs / events / state / screens  │
        │ assertions / coverage / packets  │
        └─────────────────────────────────┘

另：参考模组实验室（独立实例，不装目标模组）——同一套 EnvManager/Probe
```

### 3.2 四条设计原则

1. **结构化优先，截图其次。**
   控件树、容器槽位、绘制字符串、注册表、NBT、事件、日志都是文本，token 便宜、可 diff、可断言。截图用于渲染 bug、布局、粒子和给人看；`deepseek-flash` 已支持读图（[官方 Vision 文档](https://api-docs.deepseek.com/guides/vision)），所以截图可以直接喂主模型，但不要把截图当唯一感官。

2. **一次运行 = 一个可查询的数据库。**
   不要把几万行日志倒进上下文。把日志、事件、状态快照、断言、包、覆盖率写进 SQLite，让 AI 用 SQL 查。它擅长这个，而且查完只把结论带回上下文。

3. **最便宜的测试层能证伪，就不要上更贵的层。**
   T0 编译 → T1 静态审计 → T2 GameTest → T3 服务端 scenario → T4 客户端 → T5 人类。每层都要能独立失败、独立报告。

4. **每个实验都要沉淀为 scenario。**
   AI 的探索如果只留在对话里，下次还要重来。scenario 是回归资产，也是"AI 到底测了什么"的证据。

### 3.3 为什么必须是"进程内 probe"，不能只靠协议 bot

- **协议 bot 加载不了 Java 模组。** Mineflayer 说的是 vanilla 协议；Forge/NeoForge 需要 FML 握手，`minecraft-protocol-forge` 只能**伪造** mod 列表，而机器人侧没有 mod 的注册表——新方块、新物品、新实体、新维度、新 GUI、新数据包它都不认识。Mindcraft 的 FAQ 直说"只支持 optifine/sodium 这类客户端 mod"。
- 协议 bot 能测的：命令、规则、掉落、生成逻辑、无新内容的玩法调整。测不了的：新内容注册/渲染、GUI/Screen、贴图模型、客户端崩溃、光影。
- 协议 bot 看不到客户端 `Screen`、渲染、粒子、模型、按键，也无法触发客户端 GUI。
- 客户端的事件注入必须进程内：OS 级 `SendInput` 对后台窗口基本无效，且会抢用户鼠标；进程内可以直接调 `Screen.mouseClicked`、`KeyMapping.setDown`、`Minecraft.gameMode`。
- 服务端侧可以只用协议 bot/假玩家，但拿不到注册表、NBT、capability、GameTest 断言这些"开发期真相"。
- 所以：**客户端 probe 为主，协议 bot 为辅，GameTest 为快**。如果只做服务端逻辑，协议 bot + 一个 `/dsh:dump` 自省命令也能凑合，但那不是"测模组"，是"测服务端规则"。

### 3.4 分层：哪些复用，哪些自研

| 层 | 复用 | 自研 |
|---|---|---|
| 脚手架/版本矩阵 | `minecraft-dev` 的模板与技能（1.7.10–26.3、五平台） | 按需裁剪/对接 |
| 构建/运行 | Gradle、ModDevGradle/ForgeGradle/Loom、`minecraft-dev` 的 `mc_gradle` | 编排、错误解析、日志定位 |
| 服务端测试 | NeoForge GameTest、Carpet、`/tick`、HeadlessMC、mc-runtime-test | 场景生成、断言 DSL、报告 |
| 客户端桥 | DebugBridge 协议（Fabric）、Fabric Client Gametest | NeoForge 适配、UI 树 schema、输入抽象 |
| 观测 | log4j2 Appender、spark/JFR、mclo.gs | 运行数据库、事件模型、飞行记录仪 |
| 依赖 | Modrinth/CurseForge API、packwiz | 依赖解析、冲突检测、锁文件 |
| 反编译 | Vineflower、Gradle `genSources` | 符号索引、API 查询 |
| 视觉 | `deepseek-flash` 视觉、SSIM | 截图基线、区域 diff、contact sheet |
| 模式 | DSH preset、whale-craft/`minecraft-dev` 策略范本 | 第五个 preset、工具、技能、UI |

### 3.5 v1 范围与取舍（建议）

| 优先级 | 做 | 不做 |
|---|---|---|
| Must | 构建/启动服务端、GameTest、日志/状态入库、scenario DSL、报告、清理 | 自由玩 agent、主观评分、全版本矩阵 |
| Should | 服务端 probe 的 registry/world/command、Carpet 假玩家、覆盖率、飞行记录仪 | 参考模组实验室、兼容矩阵 |
| Could | NeoForge 客户端桥、截图/UI 树、视觉 diff、多版本 | 云端 CI、商业化 UI |
| Won't（v1） | 纯像素端到端、实时 twitch 操作、自动改美术 | — |

**按模组类型选测试面**：

| 模组类型 | 主测试面 | 备注 |
|---|---|---|
| 纯服务端（命令/规则/掉落） | T2 GameTest + T3 服务端 scenario + 协议 bot | 最便宜，最先做 |
| 内容型（方块/物品/实体/配方） | T1 静态审计 + T2 GameTest + T3 | 重点是注册/资源/数据 |
| 客户端型（GUI/渲染/按键/HUD） | T4 客户端 probe + 截图 + UI 树 | NeoForge 需自研桥 |
| 网络型（自定义包/同步） | T3 双端 probe + 包日志 + 延迟注入 | 需要服务端+客户端同时跑 |
| 兼容型（JEI/Create/AE2） | T3/T4 + 参考模组实验室 + 矩阵 | 组合爆炸，按需 |

### 3.6 一个具体的"AI 在游戏里做什么"例子

不是"WASD 走过去砍一刀"，而是：

```text
mcdev_scenario_run("charger_gui_shift_click")
  → 起服务端 + 客户端
  → /give @p mod:charger
  → /setblock 0 64 0 mod:charger
  → probe: use_item_on(0,64,0)
  → probe: wait_until_screen("ChargerScreen")
  → probe: ui_snapshot()          # 控件树 + 槽位
  → probe: click_slot(0, 1, shift=true)
  → probe: wait_ticks(5)
  → assert: inventory 里物品数量不变/正确
  → assert: 服务端 block entity NBT 能量正确
  → screenshot + 数据库
  → 报告：通过/失败 + 证据
```

AI 的"亲自操作"体现在：它决定场景、参数、断言，并读取结果；具体点击由 probe 执行。这比让模型实时看画面点鼠标可靠得多，也便宜得多。

---

## 4. 一次完整迭代长什么样

以"充能方块"为例（方块 + 物品 + GUI + 配方 + 比较器输出 + 漏斗输入）：

### 4.1 时序

1. **规格**：AI 先写 `SPEC.md`：方块 ID、能量上限、GUI 布局、比较器等级、漏斗行为、配方、掉落、语言键、模型/贴图。每条都是可测的验收标准。
2. **实现**：AI 写 Java + datagen + 资源 + 测试骨架。
3. **T0 构建**：`gradlew build`；编译错误结构化返回（文件/行/列/符号/建议）。
4. **T1 静态审计**：注册表完整性、模型/贴图/语言键、配方引用、标签、loot table、blockstate 变体、mixin 配置。**不启动游戏**就能抓一大半"低级但致命"的错。
5. **T2 GameTest**：`runGameTestServer`；测放置/破坏/能量增减/漏斗输入/比较器输出/NBT 往返/保存加载。
6. **T3 服务端 scenario**：起专用服务器 + probe + 假玩家；测多人、聊天、权限、重载、维度切换、延迟。
7. **T4 客户端**：起真实客户端 + probe；打开 GUI、点按钮、拖物品、shift-click、看 tooltip、截图、视觉 diff、F3 数据、粒子/模型检查。
8. **诊断**：失败 → 飞行记录仪 dump 最近 N tick → 最小复现 → 修复 → 重跑。
9. **报告**：JUnit XML + Markdown + 截图/视频 + 覆盖率 + 性能 + "AI 不能判断、需要人看"的清单。
10. **人类**：用户通过 LAN 进同一个服务器，玩 5–10 分钟；AI 记录、把反馈转成新 scenario。
11. **清理**：停进程、释放端口、删世界、保留产物和数据库。

### 4.2 成本/时延（估算，需实测）

| 层 | 启动 | 单次 | 何时跑 |
|---|---|---|---|
| T0 编译 | 0 | 10–60s（增量） | 每次改代码 |
| T1 静态审计 | 0 | 2–10s | 每次改资源/注册 |
| T2 GameTest | 10–30s | 30–90s | 每次逻辑改动 |
| T3 服务端 scenario | 10–30s | 1–3min | 每次提交前 |
| T4 客户端 | 20–60s | 2–5min | 每天/发版前/UI 改动 |
| T5 人类 | — | 5–30min | 主观验收 |

> 一个功能 5–20 次迭代是正常的；按 T0–T2 为主，T4 按需，单功能 1–3 小时可接受。

---

## 5. 关键设计

### 5.1 Probe 协议

**传输**：localhost WebSocket 或 HTTP+SSE；JSON；请求 `{id, method, params}`，响应 `{id, ok, result|error}`，事件 `{event, tick, side, payload}`。

**为什么不用 MC 的 SimpleChannel/Payload**：那是给客户端-服务端通信用的，走游戏连接，受登录/握手/模组匹配影响；本地 RPC 用独立端口更简单、更稳、更容易做多实例。

**必备模块（v1）**：
- `core`：生命周期、鉴权 token、线程调度（所有游戏访问切主线程）、超时、错误。
- `world`：方块/方块实体/实体/区块/时间/天气/维度。
- `player`：背包、状态、效果、成就、游戏模式、位置。
- `registry`：物品/方块/实体/配方/标签/loot/成就/声音/粒子/菜单/属性/数据组件。
- `log`：环形缓冲、结构化、异常、崩溃检测。
- `ui`：当前 Screen、控件树、容器槽位、tooltip、打开/关闭、点击/按键。
- `capture`：下一帧截图、离屏 FBO、contact sheet、可选的逐帧录制。
- `input`：`Screen` 层、`KeyMapping` 层、可选的 GLFW/SDL3 层。
- `test`：命令执行、tick 冻结/步进、随机种子、断言钩子、快照/恢复。
- `perf`：MSPT、FPS、内存、GC、实体/区块数。

**v2**：`trace`（事件总线/方法/包）、`net`（包日志/延迟注入/断线重连）、`flightrec`（环形事件 + 状态快照）、`coverage`（JaCoCo dump）、`compat`（mod 列表/mixin 审计）。

**关键实现细节**：
- 截图必须在 render 线程、当前 framebuffer 绑定、当帧绘制完成后执行；用"下一帧回调"队列，语义是"下一帧"，不是"立刻"。
- 窗口被遮挡不影响渲染；**最小化会退化 framebuffer**，所以不要最小化，把窗口放到屏幕外负坐标或第二虚拟桌面。
- 容器 GUI 的槽位不在 widget 树里，要特判 `AbstractContainerScreen.menu.slots` 和 `leftPos/topPos`。
- 自绘文本（tooltip、F3、聊天、HUD）不在 widget 树里；在 `GuiGraphics.drawString`/`Font` 上挂 Mixin 抓"这一帧画了哪些字符串 + 坐标 + 颜色"，这是性价比最高的"结构化视觉"。
- 输入层要做薄、可替换：26.3 快照起输入从 GLFW 换到 SDL3，不要把逻辑焊死在 `Keyboard.onKey`。
- 单机/多人：客户端 probe 同时支持集成服务器和远程服务器；服务端 probe 独立部署。

### 5.2 运行数据库（核心差异化）

一次运行写一个 SQLite（或 DuckDB）文件，表大致如下：

```sql
runs(id, started_at, mc_version, loader, loader_version, mod_sha, seed,
     scenario, status, jvm_args, os, gpu)
ticks(run_id, tick, mspt, fps, players, entities, chunks, heap_mb)
logs(run_id, seq, tick, side, level, logger, thread, message, throwable_id)
throwables(id, class, message, stack, count, first_tick, last_tick)
events(run_id, tick, side, type, payload_json)
blocks(run_id, snap_tick, dim, x, y, z, id, state_json, be_json)
entities(run_id, snap_tick, id, type, pos, nbt_json, removed_tick, death_cause)
items(run_id, snap_tick, container, slot, item, count, nbt_json)
screens(run_id, tick, screen_class, title, widgets_json)
packets(run_id, tick, side, direction, channel, type, size, digest)
assertions(run_id, scenario, name, status, expected, actual, tick)
artifacts(id, run_id, kind, path, sha256, meta_json)
coverage(run_id, class, method, covered, hits)
registries(run_id, kind, id, payload_json)
```

**为什么强**：
- AI 可以用 SQL 问："哪些 tick 的 MSPT > 50？""哪些实体的死亡原因不是玩家？""GUI 关闭后还有哪些包在发？""哪些配方引用了不存在的物品？""这次运行和上次运行的注册表差在哪？"
- 上下文只带回查询结果，不回带原始日志。
- 产物（截图/视频/堆栈）按需取，路径稳定。
- 运行可复现：manifest 里有 mod hash、依赖锁、seed、JVM 参数、OS/GPU。

**工具**：`mcdev_sql`（只读、带 schema 提示）、`mcdev_artifact_get`、`mcdev_log_digest`（预聚合的摘要）。

### 5.3 Scenario DSL

```yaml
name: charger_block_charges_item
setup:
  gamemode: creative
  time: noon
  weather: clear
  commands:
    - "setblock 0 64 0 mod:charger"
    - "give @p mod:charged_item"
actions:
  - use_item_on: {pos: [0, 64, 0], face: up}
  - wait_ticks: 20
assert:
  - block_entity_nbt: {pos: [0, 64, 0], path: "Energy", op: ">=", value: 100}
  - item_in_inventory: {item: "mod:charged_item", count: 1}
  - no_exception: true
  - screen_title: "Charger"
capture:
  - screenshot: {name: "gui"}
  - contact_sheet: {frames: 8, interval: 5}
```

**设计要点**：
- 声明式，AI 好写、好改、好 diff；执行器负责翻译成 probe RPC + 命令。
- 每个 scenario 是回归资产；失败时自动保存数据库 + 产物 + 最小复现。
- 支持参数化/矩阵（版本、mod 组合、难度、多人数量）。
- 支持"记录模式"：AI 自由操作 → 录制 → 转成 scenario。

### 5.4 判据（Oracle）：这是最难的部分

| 判据类型 | 能抓什么 | 怎么实现 |
|---|---|---|
| 崩溃/异常 | NPE、类加载、mixin、注册 | 日志 + 崩溃报告 + 进程退出码 |
| 日志不变量 | 缺贴图/模型、配方警告、线程警告 | 规则 + 去重 + 与基线 diff |
| 内容完整性 | 物品无模型/无语言/无配方、标签错 | 静态扫描 + datagen 对照 |
| GameTest 断言 | 方块/实体/物品/配方行为 | `GameTestHelper` |
| Scenario 断言 | GUI/交互/多人/状态 | DSL + probe |
| 差分 | 与上一版/无 mod/参考 mod 的差异 | 注册表/NBT/截图/行为 diff |
| 金标准 | 回归：这次和上次不一样 | 基线快照 + 阈值 |
| 属性/变形 | 序列化往返、放置/破坏幂等、加倍输入 | 生成式测试 |
| 覆盖率 | "你根本没测到这段" | JaCoCo + 变更行 |
| 性能 | MSPT/FPS/内存回归 | 基线 + 阈值 |
| 人类 | 好玩/好看/平衡 | 报告 + 视频 + 试玩 |

**红线**：AI 不能通过"改测试"来让测试通过。测试目录归 tester 角色/独立目录，改动要留痕；构建器改测试要显式说明理由。

### 5.5 覆盖率：让 AI 知道"哪里没测"

- 在服务端/客户端 JVM 上加 JaCoCo agent（`-javaagent:jacocoagent.jar`），运行结束 dump `jacoco.exec`。
- 只统计模组自己的包，过滤 MC/loader/库。
- 报告：类/方法/分支覆盖率 + 变更行覆盖 + 每个 scenario 的增量覆盖。
- AI 的测试计划从"变更行 + 未覆盖分支"生成，比自由玩高效一个数量级。

### 5.6 视觉：结构化 + 截图 + 数值 diff

- **首选**：控件树 + 容器槽位 + `drawString` 抓取 + 颜色/状态字段。
- **次选**：截图，直接给 `deepseek-flash` 看（每图 ≤1024 tokens）。
- **回归**：基线截图 + SSIM/区域 ΔE，返回"哪个区域变了、变了多少"，文本模型也能判断。
- **视频**：给人类看；给 AI 看时用 contact sheet（多帧拼一张），一张图讲一个动作序列。
- **渲染专项**：缺贴图/模型错误通常有日志；z-fighting、透明度、粒子需要人眼或视觉模型。

### 5.7 符号索引 / API 查询（解决"AI 瞎编 API"）

- 用 Gradle `genSources` 或 Vineflower 反编译 MC + loader + 依赖。
- 建索引：类、方法签名、字段、继承、引用；支持 `mcdev_api_search` / `mcdev_api_members` / `mcdev_api_refs`。
- 同时索引 NeoForge/Forge/Fabric 文档、示例模组、本项目的代码。
- 规则：**AI 不许凭记忆写 loader API；必须查索引或编译错误驱动**。
- 26.x 起 Mojang 直接发不混淆 jar（DebugBridge README 也确认 1.21.11+ mapping 是 no-op），索引会更简单；1.20.1 仍需映射。

### 5.8 环境与依赖

- 项目清单：`mcdev.toml`（mc_version、loader、loader_version、java_version、mappings、deps、run args）。
- 模板：优先复用 `minecraft-dev` 的模板；否则用 NeoForge MDK、Forge MDK、Fabric template、Architectury 多加载器。
- **版本基线（2026-10）**：26.3 用 Java 25；Forge `26.3-66.0.13` + ForgeGradle `7.0.38`；NeoForge `26.3.0.x-beta` + ModDevGradle `2.0.147`；Fabric 26.3 + Loom `1.18` + Loader `0.19`。1.21.1 用 Java 21，1.20.1 用 Java 17。
- JDK：Gradle toolchain 自动下载（foojay）；或按版本预装。
- **Gradle run 配置**（ModDevGradle 已支持，见[官方文档](https://docs.neoforged.net/toolchain/docs/plugins/mdg/)）：
  - `runs { client { client() }; server { server() }; gameTestServer { type = "gameTestServer" }; data { data() } }`。
  - 每个 run 可设 `gameDirectory`、`loadedMods`、`jvmArguments`、`programArguments`、`systemProperties`、`environment`、`logLevel`。
  - `runs { client { devLogin = true } }` 可用正版账号；默认离线 profile。
  - `unitTest { enable(); testedMod = mods.<name> }` + `EphemeralTestServerProvider` 可在 JUnit 里拿到 `MinecraftServer`。
  - CI 里 MDG 2.0.136+ 默认跳过反编译/重编译；本地保留以支持调试。
  - `-XX:+AllowEnhancedClassRedefinition`（JBR）可作为热替换的起点；但只能改方法体/增删成员，Mixin/结构改动仍需重启，别把热替换当万能。
- **首次 sync + 反编译 20–60 分钟**，之后热启动 client 30–90 秒、server 15–40 秒、GameTest 30–90 秒；这是"第一次很贵，之后可接受"的典型。
- 依赖解析：
  - Modrinth API v2 读接口免 key，300 rpm，需要 User-Agent；`/v2/project/{slug}/version?loaders=[...]&game_versions=[...]`，version.dependencies 标 required/optional。
  - CurseForge 必须 `x-api-key`，2026-06 起连文件下载也要 key；**AI 不能自助申请 key，这是现实 blocker**——有 key 才接，否则只走 Modrinth/Maven。
  - packwiz：TOML 元数据，`packwiz modrinth add`/`curseforge add`，`packwiz-installer` 给 Prism/MultiMC 自动更新；适合导出给人类。
  - Prism CLI 只能启动已有实例/离线名/直连，**不能 CLI 创建实例**（issue #4037）→ 需预置实例。
  - itzg/docker-minecraft-server 可用 `MODRINTH_PROJECTS` + `MODRINTH_DOWNLOAD_DEPENDENCIES=required` + `VERSION_FROM_MODRINTH_PROJECTS=true` 一条龙起服。
- 启动：优先直接 `java -cp ...` 而不是每次走 Gradle，减少启动开销；Gradle 只负责构建和生成 classpath。
- 服务器：`online-mode=false`、固定端口、固定种子、`eula=true`（开发环境）、固定保存策略；`runGameTestServer` 会自行退出并返回失败数。
- 客户端：小窗口（854×480）、低渲染距离（4–6）、关垂直同步、关 FPS limiter、关声音；`pauseOnLostFocus=false`（否则单人会被暂停，多人会弹菜单/丢输入）；不要最小化（MC 1.21.2+ 最小化会降到 10 FPS；最小化还会让 framebuffer 退化）。
- 清理：每次 run 一个 UUID 目录；结束后杀进程树、释放端口、删世界；保留 artifacts + DB；定期 GC。

### 5.9 沙盒与隔离（Windows 视角）

先说结论：**v1 不需要 VM，也不要用 Windows Sandbox 跑日常迭代。**

| 方案 | 隔离 | OpenGL | 性能 | 快照/清理 | 适用 |
|---|---|---|---|---|---|
| 原生独立用户/目录 | 无内核隔离 | 原生 4.6 | 100% | 目录/Dev Drive block clone | **可信 mod，v1 默认** |
| Windows Sandbox | 强（HCS） | 只有 1.1；Mesa llvmpipe 4.5 软渲染 | 10–25 FPS | 无快照，关闭即毁；默认 4 GB、单实例 | 不可信 mod 的功能冒烟 |
| Hyper-V GPU-P | 强（VM） | 4.6（厂商驱动） | 70–95% | **不支持 checkpoint**；VHDX 复制 | 需要 VM 隔离且能接受非官方支持 |
| WSL2 + WSLg | 强（VM） | Mesa d3d12 4.6 | 50–80% | export/import、vhdx 复制 | Linux 客户端/CI，能接受无 Windows 注入 |
| Docker Desktop | 容器 | Windows 容器无 GUI；Linux 走 WSLg | 同 WSL2/更差 | 镜像 | 不推荐 |
| App Sandbox（新） | 强（HCS + GPU-PV） | GL/Vulkan/DX12 | 70–95% | snapshot/branch/template | 值得 POC，v0.x 风险 |

**关键事实**：
- **Windows Sandbox 不提供硬件 OpenGL**。vGPU 只虚拟化 DirectX/WDDM，OpenGL 通常只有 1.1；要跑 MC 必须把 Mesa3D 的 `opengl32.dll` 放到 `javaw.exe` 旁边走 llvmpipe，能到 4.5 但只有 10–25 FPS。而且它只在 Pro/Enterprise/Education 可用，默认 4 GB、单实例、**没有快照**。**只适合隔离不可信代码。**
- **Hyper-V GPU-P 在 Win11 客户端 + 消费级显卡上不受微软支持**；能跑（社区方案 Easy-GPU-PV），但 Vulkan/DX12 不可用、GL 时好时坏、**GPU-P VM 不能 checkpoint**、宿主驱动更新容易把 guest 弄挂。别把它当生产依赖。
- **WSL2 + WSLg 的 OpenGL 反而最好**（Mesa d3d12，GL 4.6），但跑的是 Linux 客户端，**没有 Windows 进程内注入**；WSLg 下鼠标还有已知问题。适合 Fabric/NeoForge 的 Linux 客户端 + Xvfb CI。
- **虚拟显示器不提供 OpenGL ICD**：Parsec VDD 等只加桌面，OpenGL 游戏仍可能报 "missing OpenGL 2.0"。要"无头"就用真实显示器/HDMI dummy plug，或干脆用 FBO 离屏渲染。
- **遮挡不等于可跑**：被遮挡窗口通常还在渲染，但 DXGI Present 可能返回 occluded 或阻塞；关 vsync、限帧、把窗口留在可见桌面/第二显示器更稳。**不要最小化**（MC 1.21.2+ 会降到 10 FPS，framebuffer 也会退化）。
- **虚拟桌面不是独立会话**：非当前桌面的窗口被 DWM cloak，`SendInput` 只作用于活动桌面；锁屏/RDP 断开还可能让 OpenGL 上下文创建失败。自动化机要自动登录、保持解锁、禁睡眠。
- **进程内注入是唯一可靠输入**：`SendInput` 只给前台窗口且受 UIPI 限制；`PostMessage` 对 GLFW 鼠标视角不可靠。代价是注入代码必须跑在 guest 内。

**推荐 v1 拓扑（单台 Win11 + 独显）**：
1. 宿主：Win11 Pro，12C/32 GB，NVMe 200 GB 空闲，独显驱动固定。
2. 可信 mod：**原生独立用户 `mcbot`（非管理员）+ 每次 run 独立 gameDir + Job Object（KILL_ON_JOB_CLOSE）+ `taskkill /T /F` 兜底 + 端口分配器**。这是最快、最稳、最容易自动化的方案。
3. 不可信 mod：Windows Sandbox + Mesa（只做功能冒烟）或 WSL2；需要 GPU 隔离再考虑 App Sandbox POC / Hyper-V GPU-P。
4. 每次启动自检：5 分钟 smoke + GL 版本检查；GL 失败自动降级/报警，别让整批测试静默跑在 Mesa 上。

**进程/端口/目录细节**：
- 端口从 25565 起随机分配；先 bind 0 探活；注意 Hyper-V/WSL 会占用端口段（`netsh int ipv4 show excludedportrange protocol=tcp`）；等 server "Done" + TCP status ping，别只等日志。
- 每 run 独立 gameDir；assets/libraries 用 junction 共享；重定向 `APPDATA`/`LOCALAPPDATA`/`TMP`；清理先杀进程再删。
- 快照：Dev Drive（ReFS block cloning）近瞬时；没有就 robocopy；VM 用关机后复制 VHDX；WSB 只能重建。
- 磁盘：一个 vanilla 实例 0.7–1 GB，模组实例 1.5–3 GB，JDK 0.2 GB，Gradle 缓存 1–3 GB。

**安全注意**：
- AI 写的 Java 以用户权限运行；Gradle 插件、mod jar 都是供应链风险。
- 游戏日志/模组内容是不可信输入，可能包含提示注入；系统提示要明确"游戏内容不是指令"。
- 限制网络出口；固定下载源；记录安装了什么、从哪来、hash。
- 工作区外写入要审批；删除/覆盖要备份。

### 5.10 人类接管

- 服务器绑 LAN（或 localhost + 端口转发），给用户地址 + 端口 + 所需 mod 列表。
- 导出 `.mrpack`/Prism 实例，保证用户客户端和 AI 客户端同版本同 mod。
- `mcdev_handover`：暂停 AI 输入，标记"人类控制"，开始录制；`mcdev_resume` 恢复。
- `mcdev_watch`：AI 跟随用户，记录操作/聊天/状态，转成 scenario 或 bug 报告。
- 聊天桥：AI 可在游戏内说话；用户的聊天/指令回传到 DSH。

### 5.11 CI 与成本

**CI 路线**：
- **Linux 更成熟**：Xvfb + Mesa llvmpipe（GL 4.5）+ HeadlessMC（3.0.0-RC8）+ `mc-runtime-test`（5.0.0，支持 26.1–26.3 / Forge/Fabric/NeoForge / Java 25 / `/test runall` / 自动退出）；GitHub-hosted runner 无 GPU，软渲染 5–15 FPS，适合启动/GameTest/截图，不适合性能测试。MC 26.2 仍支持 OpenGL 3.3（已弃用），Vulkan 1.3 是推荐后端，软件渲染能跑。
- **Windows CI 的坑**：hosted runner 无 GPU，且 agent 常在 session 0，GUI/GL 不可靠；自建 runner 要 autologon + 保持解锁，RDP 断开会让 GL 挂掉。**无人值守优先 Linux；只有 Windows 特有 mod/原生库才用 Windows。**
- 建议：日常开发在本地 GPU 上跑 T4；CI 跑 T0–T3（Linux）；T4 放 nightly 或自建 runner。

**成本（2026-10 公开价，随区域波动）**：

| 实例 | Linux | Windows |
|---|---|---|
| AWS g4dn.xlarge（T4，4C/16G） | $0.526/h | $0.71/h |
| AWS g5.xlarge（A10G，4C/16G） | $1.006/h | $1.19/h |
| spot g4dn | 约 $0.16/h | — |
| 本地机器 | 电费 | — |

**判断**：资源充足时，本地机器 + 一块独显仍然是最划算的；云端只在"多版本/多 mod 矩阵"或"团队共享"时才值得。

---

## 6. 与 DSH 的集成：第五个模式怎么加

### 6.1 模式机制（已被 whale-craft 验证）

DSH 的四个内置 preset 是 `standard`、`ptc`、`minimal`、`cordis`（见 `dsh-web-app/presets/*.patch.yml`）。加第五个模式就是安装一个 bundle，其 patch 插入一行：

```yaml
- insert:
    - id: preset-mc-mod-dev
      name: '@deepseek-ai/dsh-agent-preset'
      config:
        id: mc-mod-dev
        name: MC 模组开发
        description: 写模组、起游戏、自己测试、读日志修 bug。
        order: 7
        plugins:
          - id: persona
            name: '@deepseek-ai/dsh-persona'
            config:
              prefix: |
                你是一名 Minecraft 模组工程师。你拥有完整的闭环：
                设计 → 实现 → 构建 → 启动 → 观测 → 断言 → 修复 → 报告。
                没有运行证据，不得声称功能可用。
          - id: tool-fs
            name: '@deepseek-ai/dsh-tool-fs'
          - id: tool-fs-search
            name: '@deepseek-ai/dsh-tool-fs-search'
          - id: tool-pwsh
            name: '@deepseek-ai/dsh-tool-pwsh'
            disabled: !!js process.platform !== 'win32'
          - id: tool-bash
            name: '@deepseek-ai/dsh-tool-bash'
            disabled: !!js process.platform === 'win32'
          - id: tool-jobs
            name: '@deepseek-ai/dsh-tool-jobs'
          - id: tool-subagent
            name: '@deepseek-ai/dsh-tool-subagent'
          - id: tool-workflow
            name: '@deepseek-ai/dsh-tool-workflow'
          - id: tool-todo
            name: '@deepseek-ai/dsh-tool-todo'
          - id: tool-web
            name: '@deepseek-ai/dsh-tool-web'
          - id: present
            name: '@deepseek-ai/dsh-tool-present'
          - id: mcdev-tools
            name: 'mcdev'          # 我们自己的 Host 插件
          - id: mcdev-skills
            name: '@deepseek-ai/dsh-skill-filesystem'
          # + compaction / goal / ask-user ...
```

> 注意：preset **没有继承机制**（whale-craft 的注释也写了），所以要么抄一份 standard，要么只挂必要工具。抄一份的代价是 DSH 升级时要对照刷新。
>
> 另一个坑：DSH 0.2.0 起有插件兼容性门禁（`evaluatePluginCompatibility`），会拿插件的 peer 声明和运行版本比对，**任一条不匹配就拒绝导入整包**（不是警告）。像 `minecraft-dev` 那样把支持的 dsh 线逐条枚举，别用一条范围糊过去。

### 6.2 Host 插件提供什么

- **服务**：`mcdev` 服务（EnvManager/BuildManager/ScenarioRunner/ArtifactStore/SymbolIndex/LabManager），按会话隔离，带锁文件。
- **工具**（v1 控制在 10–15 个）：
  - `mcdev_project_init` / `mcdev_project_info`
  - `mcdev_build`
  - `mcdev_audit`
  - `mcdev_env_start` / `mcdev_env_stop` / `mcdev_env_status`
  - `mcdev_scenario_run`
  - `mcdev_sql`
  - `mcdev_screenshot`
  - `mcdev_log_digest`
  - `mcdev_api_search`
  - `mcdev_lab_start` / `mcdev_decompile`
  - `mcdev_report`
  - `mcdev_cleanup`
- **策略**：文件限制在项目 + `.mcdev/` 缓存；危险操作（删目录、装未知 jar、开公网）要审批；网络出口白名单；工具按模式/角色可见。
- **看门狗**：服务器就绪、崩溃、测试失败、用户聊天 → 唤醒 agent（参考 whale-craft 的 watchdog + job）。
- **UI**：状态面板（服务端/客户端/端口/CPU/内存）、启停按钮、截图浏览、报告入口、"人类接管"按钮。

### 6.3 子代理分工

- **Builder**：写实现。
- **Tester**：写/跑 scenario 和 GameTest，只改测试目录。
- **Reviewer**：读 diff、查 API 用法、查测试质量。
- **Researcher**：反编译/索引参考模组，回答"这个 API 怎么用"。
- 主 agent 编排，用 `tool-workflow` 做多版本/多 mod 组合的并行矩阵。

> 一开始别上多代理；先把"单 agent + 验证技能"跑通，再拆角色。

### 6.4 技能（Skills）

- `mcdev-workflow`：什么时候用哪层测试、怎么写 scenario、怎么读报告。
- `mcdev-probe-api`：probe 方法/字段参考（可从 schema 生成）。
- `mcdev-scenario`：DSL 参考 + 示例。
- `mcdev-bug-taxonomy`：注册/资源/数据/逻辑/UI/网络/兼容/性能/多人检查清单。
- `mcdev-version-*`：各版本/加载器的 API 差异与坑。
- `mcdev-debug`：crash/log/mixin 模式与定位。

---

## 7. 风险与反方观点

| 风险 | 严重度 | 说明 | 缓解 |
|---|---|---|---|
| 判据问题 | 高 | AI 不知道"正确"是什么，容易自欺 | SPEC + 不变量 + 差分 + 人类验收 |
| 客户端自动化 | 高 | NeoForge 无现成 Client Gametest；Windows 输入/窗口有坑 | 先 Fabric 验证，再移植；进程内注入；FBO 截图 |
| 版本矩阵 | 高 | 1.20.1/1.21.1/26.x，Forge/NeoForge/Fabric，API 差异大 | 窄矩阵 + 适配层 + Stonecutter 多版本 |
| 上下文爆炸 | 高 | 日志/截图/状态太多 | 运行数据库 + 摘要 + 按需取 |
| 模型 API 知识 | 中高 | 模组 API 庞大，容易瞎编 | 符号索引 + 编译驱动 + 模板 |
| 安全/供应链 | 中高 | 跑未知 mod、Gradle 插件、日志注入 | 沙盒 + 白名单 + hash + 提示隔离 |
| 测试作弊 | 中 | AI 改测试让它过 | 角色/目录隔离 + 测试 hash + review |
| 法律/EULA | 中 | 分发客户端/资源、反编译他人 mod | 本地开发、不 redistribution、尊重 license |
| 成本/时延 | 中 | 客户端启动 20–60s，GPU 占用 | 分层 + 缓存 + 限帧 + 按需 |
| DSH 升级 | 中 | preset 无继承、插件 API 变动 | 固定版本 + 薄适配 + 跟随 whale-craft |

**反方观点**（值得认真对待）：
- "模组作者本来就会自己测，为什么不直接用 IDE + GameTest？"——因为大部分作者不写 GameTest，客户端 UI/多人/兼容测试更没人写；AI 的价值是**把测试写出来并跑起来**，不是替代 IDE。
- "LLM 写模组代码质量不够。"——对复杂模组确实不够；但对中小模组、脚手架、bug 修复，2026 年的模型已经可用，且编译/测试会兜底。
- "维护成本太高，不如做个好用的 CI 模板。"——如果只做 CI 模板，AI 无法根据失败自动修；闭环的价值在"修"，不在"测"。
- "MC 版本变太快。"——窄矩阵 + 只支持 LTS 版本 + 社区共建，是唯一可行路径。

---

## 8. 路线图（建议）

### P0：垂直切片（4–6 周；若复用 `minecraft-dev` 可压到 2–4 周）

**目标**：证明"AI 能靠证据修一个真实模组的 bug"。

- 先评估 `minecraft-dev` 的模板/技能/`mc_gradle` 能否直接复用；能复用就不重造脚手架。
- NeoForge 1.21.1（或你主攻版本）项目模板。
- `mcdev_build` + `mcdev_audit` + `runGameTestServer` + 服务端 probe（只做 log/registry/world/command）。
- SQLite 运行库 + `mcdev_sql` + `mcdev_log_digest`。
- 3–5 个 scenario（放置/破坏/配方/GUI 打开/多人）。
- 一个真实小模组 + 5 类注入 bug。
- **验收**：AI 在 30 分钟内定位并修复 ≥3/5 个 bug；单次 T0–T2 循环 < 3 分钟；token 成本可接受。

### P1：服务端闭环（4–8 周）

- 依赖解析 + 锁文件 + modpack 导出。
- Carpet 假玩家 + `/tick` + 多人 scenario。
- 飞行记录仪（最近 N tick 事件/状态）。
- 覆盖率（JaCoCo）+ 变更行覆盖。
- 报告（JUnit XML + Markdown + 产物）。
- **验收**：一个中等模组的回归套件可在 CI 跑；失败能给最小复现。

### P2：客户端闭环（8–12 周）

- Fabric 侧复用/对接 DebugBridge 协议；NeoForge 侧自研桥。
- UI 树/容器槽位/绘制字符串/截图/输入。
- 视觉基线 + diff + contact sheet。
- 客户端 scenario + 人类接管。
- **验收**：AI 能打开 GUI、点按钮、拖物品、shift-click、截图对比；能抓一个 UI 回归。

### P3：参考模组与兼容矩阵（8–12 周）

- 反编译 + 符号索引 + API 查询。
- 参考模组实验室（独立实例、注册表/配方 dump、GUI 观察）。
- 兼容矩阵（JEI/Create/AE2 等常用 mod + 目标 mod）。
- **验收**：AI 能回答"这个参考模组怎么注册 GUI/配方"，并写出兼容代码。

### P4：自主探索与产品化（持续）

- 覆盖率引导的探索测试、变异测试、 bug 最小化。
- 多代理（builder/tester/reviewer/researcher）。
- CI 导出、团队协作、云端矩阵。
- **验收**：AI 在没有人工指路的情况下发现新 bug 的比例 / 误报率。

---

## 9. 立即可做的验证实验（不写正式插件）

| # | 实验 | 目的 | 成本 |
|---|---|---|---|
| E0 | 装 `minecraft-dev`，用它起一个 NeoForge 26.2 项目并 build 通过 | 评估复用价值，避免重造 | 半天 |
| E1 | 用 NeoForge MDK 起 `runGameTestServer`，写 3 个 GameTest | 验证 T2 闭环/退出码/日志 | 半天 |
| E2 | Carpet 假玩家 + `/tick freeze/step` 跑一个交互 | 验证服务端 scenario | 1 天 |
| E3 | Fabric Client Gametest 起客户端、截图、点按钮 | 验证客户端自动化 | 1–2 天 |
| E4 | 用 DebugBridge 连一个 Fabric 客户端，调 `screenInspect`/`screenshot` | 验证探针接口 | 半天 |
| E5 | 把 `latest.log` + 状态写 SQLite，让 AI 用 SQL 查 | 验证运行数据库 | 1 天 |
| E6 | 故意注入 5 类 bug，看 AI 能否只靠报告修复 | 验证判据/闭环 | 2–3 天 |
| E7 | 测 Windows 下客户端窗口遮挡/后台/截图/输入 | 验证本机拓扑 | 1 天 |
| E8 | 测一次完整循环的 token/时间成本 | 验证经济性 | 1 天 |
| E9 | 用 HeadlessMC / mc-runtime-test 在 CI 跑一个 GameTest | 验证无头客户端路线 | 半天 |

---

## 10. 前景判断

### 10.1 这不是"AI 玩游戏"，是"模组开发的验证基础设施"

- 目标用户：模组作者（尤其独立/小团队）、整合包作者、服主、模组团队 CI。
- 痛点：客户端 UI/多人/兼容/性能测试没人写；发版前靠人肉；bug 复现靠玩家口述。
- 价值：把"人肉试玩"变成"可复现的自动验证 + 报告"，人只做主观验收。

### 10.2 为什么是现在

- 2026 年编码 agent 能写中小模组、能读日志、能改代码。
- `minecraft-dev` 已经把"写 + 构建 + 版本矩阵"做掉；DebugBridge 已经把"客户端探针接口"做掉；Fabric Client Gametest 已经把"客户端 E2E"做掉；NeoForge GameTest 已经把"服务端断言"做掉。
- 剩下的不是"能不能"，而是"把它们串起来，并补上 NeoForge 客户端这唯一的大空白"。
- DSH 插件生态刚起步，whale-craft 证明了模式和机器人；先做的人有定义权。
- MC 26.x 不混淆、SDL3、新版本节奏，降低了部分门槛，也带来维护窗口。

### 10.3 护城河

- **运行数据库 + 判据库**：越用越知道"什么算 bug"。
- **版本矩阵 + 兼容矩阵**：大量琐碎但难维护的资产。
- **Scenario 库**：可复用的测试资产。
- **DSH 集成体验**：模式、工具、UI、报告、人类接管。
- **不是单点技术**：probe 谁都能写，闭环和判据是积累。

### 10.4 竞争与协作

| 对象 | 关系 | 策略 |
|---|---|---|
| `minecraft-dev` | 上游/相邻 | 复用模板/技能，或做它的"运行验证层"；避免重复造脚手架 |
| DebugBridge | 相邻（Fabric 探针） | 复用协议/端点；NeoForge 侧自研；可 upstream 适配 |
| Fabric Client Gametest | 官方设施 | 直接用于 Fabric 客户端层 |
| Orca / CreativeMode | 商业相邻 | 它们卖"生成"，我们卖"验证+修复"；可观察其测试方式 |
| MCreator-Agent | 相邻（工具化） | 借鉴"run client/run server 作为工具"的接口 |
| whale-craft | DSH 内相邻（玩） | 借鉴模式/看门狗/记忆；不冲突 |

**判断**：这个赛道不会只有一家；但"能进游戏、能读底层、能断言、能修"的闭环深度是稀缺的。先做深，再做广。

### 10.5 商业化（如果要做）

- 开源核心（probe + scenario + DSH 插件）+ 付费云 CI（多版本/多 mod 矩阵、GPU 客户端、报告托管）。
- 团队版：共享 scenario、基线、兼容矩阵、PR 检查。
- 服务版：帮模组团队搭 CI / 做兼容认证。
- 风险：受众技术强、付费意愿有限；先自用/开源，再考虑商业化。

### 10.6 战略价值（对 DSH）

- 这是"agent 能写软件并自己验证"的旗舰垂直场景，演示价值极高。
- 可复用到其他 Java 游戏（Terraria tModLoader）、其他游戏 QA、甚至一般桌面 GUI 测试。

---

## 11. 开放问题（需要你定）

1. **主攻版本/加载器**：Forge 1.20.1？NeoForge 1.21.1？NeoForge 26.x？Fabric？这决定第一块骨头。
2. **只做自用，还是做给别人用的插件/产品？** 决定是否投入多版本、UI、文档、商业化。
3. **Windows 单机，还是允许 WSL2/Linux？** 决定客户端自动化路线（Windows 本机 vs Linux + Xvfb）。
4. **是否接受"AI 不能判断主观质量"？** 决定报告/人类接管的比重。
5. **是否接受"先只支持一个版本"？** 决定矩阵策略。
6. **视觉**：`deepseek-flash` 已支持读图，是否把它作为一等感官，还是坚持结构化优先？
7. **安全**：是否愿意为未知模组开 Windows Sandbox/WSL2/VM？
8. **复用还是重造**：是否接受直接复用/扩展 `minecraft-dev`（脚手架、版本矩阵、`mc_gradle`），把精力全部放在运行验证层？这能把 P0 从 4–6 周压到 2–4 周。

---

## 12. 附录

### 12.1 关键链接

- whale-craft：https://github.com/yzi1b/whale-craft
- whale-craft MC 模式 preset：https://github.com/yzi1b/whale-craft/blob/main/presets/minecraft.patch.yml
- `minecraft-dev`：https://github.com/sikadi233-hub/minecraft-dev
- DebugBridge：https://github.com/use-ai-for-mc/debugbridge
- mcdev-mcp：https://github.com/use-ai-for-mc/mcdev-mcp
- NeoForge GameTest：https://docs.neoforged.net/docs/1.21.1/misc/gametest/
- Fabric Automated Testing：https://docs.fabricmc.net/develop/automatic-testing
- DeepSeek Vision：https://api-docs.deepseek.com/guides/vision
- HeadlessMC：https://github.com/headlesshq/headlessmc
- mc-runtime-test：https://github.com/headlesshq/mc-runtime-test
- TITAN（LLM 游戏 QA）：https://arxiv.org/abs/2509.22170
- VideoGameBench：https://huggingface.co/papers/2505.18134
- modl.ai：https://www.cbinsights.com/company/modlai
- MCreator-Agent：https://github.com/Nxkoo/MCreator-Agent
- ai-minecraft-mobs-creator：https://github.com/shrimpwagon/ai-minecraft-mobs-creator
- forge-coder：https://huggingface.co/hwding/forge-coder-v1.21.11
- Orca：https://orcaclient.com/ai-minecraft-mod-maker

**版本/工具链**：
- Minecraft 26.3：https://minecraft.wiki/w/Java_Edition_26.3
- 版本号规则：https://www.minecraft.net/en-us/article/minecraft-new-version-numbering-system
- Forge 下载：https://files.minecraftforge.net/net/minecraftforge/forge/
- ForgeGradle 7.0.38：https://plugins.gradle.org/plugin/net.minecraftforge.gradle/7.0.38
- ModDevGradle 2.0.147：https://plugins.gradle.org/plugin/net.neoforged.moddev/2.0.147
- Fabric 文档：https://docs.fabricmc.net/develop/automatic-testing
- 原版 GameTest：https://minecraft.wiki/w/GameTest
- `/tick`：https://minecraft.wiki/w/Commands/tick
- Modrinth API：https://docs.modrinth.com/api/
- packwiz：https://packwiz.infra.link/
- itzg 服务器：https://docker-minecraft-server.readthedocs.io/en/latest/mods-and-plugins/modrinth/
- Prism CLI：https://prismlauncher.org/wiki/getting-started/command-line-interface/
- Fabric 映射迁移：https://docs.fabricmc.net/develop/porting/mappings/

**Windows/沙盒**：
- Windows Sandbox 架构：https://learn.microsoft.com/en-us/windows/security/application-security/application-isolation/windows-sandbox/windows-sandbox-architecture
- Job Objects：https://learn.microsoft.com/en-us/windows/win32/procthread/job-objects
- Dev Drive：https://learn.microsoft.com/en-us/windows/dev-drive/
- Easy-GPU-PV：https://github.com/jamesstringer90/Easy-GPU-PV
- GLFW context/FBO：https://www.glfw.org/docs/3.1.1/context.html
- MC FPS limiter：https://www.minecraft.net/en-us/article/save-energy-with-the-frames-per-second-limiter-feature

### 12.2 术语

- **Probe**：装进游戏进程的探针模组，暴露结构化观测/操作。
- **Bridge**：外部编排器与 probe 之间的本地 RPC/WS。
- **Scenario**：声明式的测试场景（setup/actions/assert/capture）。
- **Run DB**：一次运行的所有日志/状态/断言/产物索引。
- **Flight Recorder**：环形缓冲，保留最近 N tick 的事件与状态，失败时 dump。
- **Golden Master**：基线产物，后续运行与之 diff。
- **Oracle**：判断"对不对"的依据。
- **T0–T5**：编译/静态审计/GameTest/服务端场景/客户端/人类。

---

> 下一步建议：先做 E1/E3/E6 三个实验（各 1–3 天），用真实数据回答"闭环时间、bug 发现率、客户端可靠性"三个问题，再决定是否进入 P0。
