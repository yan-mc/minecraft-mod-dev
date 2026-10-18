# Changelog

## v0.4.0 — 2026-10-18

- **根因修复**：`charger` 有 `requiresCorrectToolForDrops()`，但缺少 `data/minecraft/tags/blocks/mineable/pickaxe.json`；生存玩家挖掘一直不掉落。
- 新增客户端 `/player/mine`（`keyAttack` 真实输入路径）与服务端 `/world/mine`（在线玩家生存破坏）、`/items`。
- `mcdev_scenario` 新增 `mine` / `client_mine` / `items` 步骤，卡片 `loot_mine` / `block_state` / `loot_items`。
- F-007 卡片 live：client_mine → block air → items charger×1，warnings=[]。
- 手工验证：服务端 player mine removed=true/drops charger×1；客户端生存挖掘 before=charger/after=air，`/items` total=1。

## v0.3.9 — 2026-10-18

- 探针新增 `/world/break`：`destroyBlock(pos,true)` + 收集 ItemEntity。
- `mcdev_scenario` 新增 `break` 步骤与卡片 `assert.loot_break`。
- F-004 live：setblock charger → break → `removed=true`、drops charger×1、total=1；warnings=[]。
- 客户端探针新增 `/player/break`（`startDestroyBlock` + `continueDestroyBlock` + `stopDestroyBlock`）；creative 实测 before=charger / after=air，server 端也变 air。
- 这是服务端实际破坏 + loot 掉落 E2E，但仍不是客户端玩家采矿操作。

## v0.3.8 — 2026-10-18

- 探针新增 `/recipe`、`/loot`、`/item`。
- `mcdev_scenario` 新增 `recipe`/`loot`/`item` 步骤。
- 卡片 `assert.recipe_output` / `loot_drop` / `item_bar` 可执行。
- F-004 live：recipe=examplemod:charger×1，loot total=1，warnings=[]。
- F-006 live：item_bar visible=true/width=6/color=65280，warnings=[]。

## v0.3.7 — 2026-10-18

- `mcdev_prepare` 客户端最多 3 次尝试；`DisconnectedScreen` 提前判失败。
- 返回 `attempts[]`（每次 pid/logPath/lastScreen）；重试成功带 `retry=true`。
- 恢复演练：无 server 先起 client，延迟 35s 起 server → attempt1/2 Disconnected，attempt3 `joined=true`。
- 无 server 失败演练：3 次失败，`joined=false, retry=true, attempts[]`。

## v0.3.6 — 2026-10-18

- `mcdev_prepare` 失败时返回 `lastScreen`，并在 `firstAttempt` 里保留第一次 pid/logPath/lastScreen。
- 强制失败演练：无 server 时 `joined=false, lastScreen=DisconnectedScreen, retry=true`。
- 成功路径复测：`kind=both` → `joined=true, lastScreen=""`；F-002 卡片随后通过。

## v0.3.5 — 2026-10-18

- `mcdev_prepare` client/both 默认等待进服（`joined`），卡 ConnectScreen/Disconnected 自动 stop + 重启 client 一次。
- quickPlay handshake 偶发卡死：记录客户端 `Starting new vanilla impl connection`、server 22 个 handshake 包、30s 超时；改为 clean devJar + waitJoin/retry 兜底。
- F-001/F-002 卡片 live 全过：F-001 warnings=[]、energy 5200、analog=7；F-002 warnings=[]。
- `cards/F-002.yml` 增加 use 后的 wait，避免 UI 切换竞态。

## v0.3.4 — 2026-10-18

- 卡片 actions 支持 `wait`（`{wait: 300}`）。

## v0.3.3 — 2026-10-18

- 新增 `block` 步骤：调用 server `/world/block`，返回 block state、block entity、`analog` 比较器输出。
- 卡片 `assert.comparator` 现在可执行：`block` + `data.analog`。
- 服务端实测：Energy=5000 → `analog=7`，assert eq 7 通过。

## v0.3.2 — 2026-10-18

- 卡片 actions 支持 `give.select=true` 与 `select_item`（放主手，可带 NBT）。
- `cards/F-001.yml` 客户端 live：give/select/use/data get/assert 通过（energy 223 ≥ 100）。
- 修正 `mcdev_scenario` 可选参数 schema（可选字段省略 `required`，不能写 `required:false`）。

## v0.3.1 — 2026-10-18

- `mcdev_scenario` 支持 `card=<path>` 解析 YAML 卡片 + `dryRun`。
- 卡片子集：`setup.commands`、`actions.give/use_on/use_item_on/command`、`assert.screen_title/slot_exists/block_entity_nbt/no_exception`。
- 不支持的 action/assert 进入 `warnings`，不静默当通过。
- 新增 `cards/S-001-server-smoke.yml`，服务端实跑通过。
- `cards/F-002.yml` 客户端 live 执行：8 步全过，打开 `ChargerScreen`，title/slot assert 通过。
- `assert` 新增 `gte` / `lte`。

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
