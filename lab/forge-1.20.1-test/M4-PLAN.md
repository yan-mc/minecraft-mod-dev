# M4 计划：5 张功能卡 + 5 类注入 bug

日期：2026-10-18
工程：`lab/forge-1.20.1-test`（Forge 1.20.1 / 47.4.28 / Java 17）
目标：验证“功能卡 → 团队开发 → GameTest/服务端/客户端 → 证据 → 修复”的完整闭环。

## 1. 测试模组：Charger

一个最小但真实的“充能方块”模组：

| 功能 | 卡 | 内容 |
|---|---|---|
| 充能方块 | F-001 | `examplemod:charger` 方块 + 方块实体，0..10000 FE；右键用 `charged_item` 充 100 FE；比较器输出 0..15 |
| 充能 GUI | F-002 | 空手右键打开 `ChargerScreen`：能量条 + 1 个槽位；shift-click 快速移动 |
| 充能物品 | F-003 | `examplemod:charged_item`，NBT `Energy`；tooltip 显示 `Energy: N/10000`；右键释放 100 FE |
| 配方与掉落 | F-004 | 充电器配方（铁 + 红石 + 铜）；方块掉落自身；loot table |
| 命令与进度 | F-005 | `/charger info` 打印看向的方块能量；首次充能触发 advancement |

## 2. 5 类注入 bug（实现后由 Lead/Builder 故意注入）

| # | Bug | 位置 | 期望被哪层抓到 |
|---|---|---|---|
| B1 | `ChargerBlockEntity.setEnergy` 在 `energy < 0` 时 NPE（未 clamp） | 方块实体 | GameTest / 服务端 scenario |
| B2 | `charger` 缺 blockstate / block model / item model / texture / lang | `resources` | 静态审计（T1） |
| B3 | 配方 JSON 输出 `minecraft:stone` 而不是 `examplemod:charger` | `data/.../recipes` | 静态审计 + GameTest |
| B4 | `ChargerMenu.quickMoveStack` 在 shift-click 时复制物品 | Menu | 客户端 UI（slot + shift） |
| B5 | 比较器 `getAnalogOutputSignal` 永远返回 0 | Block | GameTest / 服务端 |

> 注入 bug 时保留 `BUGS.md` 记录：文件、行、症状、期望。修复后不能留 `BUGS.md` 的“已知失败”标记。

## 3. 卡片顺序

1. **F-001 充能方块**（先做，其他卡依赖方块实体）
2. **F-003 充能物品**（方块交互需要）
3. **F-004 配方与掉落**
4. **F-002 充能 GUI**（需要 Menu/Screen）
5. **F-005 命令与进度**

## 4. 验收

| 项 | 目标 |
|---|---|
| 构建 | `mcdev_build` exit 0 |
| GameTest | `mcdev_gametest` exit 0，≥3 个测试 |
| 客户端 | `mcdev_use` 打开 GUI；`mcdev_ui` 有槽位；`mcdev_click` shift-click 不复制 |
| 证据 | 每张卡有 run_id / 截图 / 日志摘要 |
| 修复率 | 5 个注入 bug 至少修 3 个 |
| 报告 | `REPORT.md` 矩阵：卡 × 检查项 × 通过/失败/证据 |
| 团队 | task board 上每张卡有 build/verify 任务；Reviewer 审过 |

## 5. 团队分工（本次）

| 角色 | 任务 |
|---|---|
| Lead | SPEC/TESTPLAN、建卡、分派、最终报告 |
| Builder | 实现 Charger（block/entity/item/menu/screen/recipe/command） |
| Tester | 写 GameTest + 客户端卡 + 证据 |
| Analyst | 读失败 run 的 DB/日志，给根因 |
| Reviewer | 审 diff、审测试是否对应 spec |

## 6. 备注

- 注入 bug 的实现细节放在 `BUGS.md`，不放进 `SPEC.md`；Tester 只从 SPEC 写测试。
- 每张卡一个 `cards/F-00x.yml`；`TESTPLAN.md` 是总表。
- 客户端测试用 `run-server` + `run-client`（端口 39001/39002）；服务端/客户端不要共用 `run/`。
- 本计划是 M4 的输入；下一步按 F-001 → F-003 → F-004 → F-002 → F-005 实现。
