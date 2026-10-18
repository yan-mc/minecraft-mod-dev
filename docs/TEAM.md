# 团队模式

插件挂载 DSH 官方**实验性 Agent Teams**，团队模式默认开启；传统 subagent 四行被禁用。

> 实验性：成员/任务状态可能不同步。需要时可在 `plugin/minecraft-mod-dev/cordis.patch.yml` 禁用团队行，恢复传统 subagent。

## 角色

| 角色 | 职责 |
|---|---|
| Lead | 拆任务、分派、收口、独立重跑 build/gametest/card、提交 |
| Builder | 只改实现文件（`src/main`）；不写测试、不提交 |
| Tester | 写 GameTest / 卡片 / 复现；不改实现让测试过 |
| Reviewer | 只读复核 diff、测试、卡片输出、git 状态；不改文件 |
| Researcher | 外部资料/版本调研；不改代码 |

## 任务板

工具：

- `team_task_create`：建任务，支持 `blocked_by`、`write_scopes`
- `team_task_list` / `team_task_get`
- `team_task_update`：`claim` / `reassign` / `complete` / `edit` / `set_dependencies`
- `spawn_teammate` / `send_message` / `wait_agent` / `list_agents` / `interrupt_agent`

规则：

1. 每个任务写清目标文件、验收命令、禁止事项；
2. 写范围用 `writeScopes` 标出；并行任务不重叠；
3. 有依赖用 `blocked_by`，不靠等待；
4. Teammate 完成后自己 `team_task_update complete`；
5. Lead 必须独立重跑构建/测试/卡片，不把 teammate 的“已通过”当最终结果。

## 示例：F-006 物品能量条

```text
task-2 Builder: 只改 ChargedItem.java，实现 isBarVisible/getBarWidth/getBarColor
task-3 Tester: blocked_by task-2，只改 ExampleGameTests.java，写 chargedItemBar
```

结果：

- Builder：`mcdev_build` exit 0；
- Tester：`mcdev_gametest` 12/12；
- Lead 复跑 12/12 后提交。

## 示例：卡片执行 + Reviewer

```text
task-6 Tester：mcdev_prepare server → F-004/F-006 卡片 → mcdev_stop
task-7 Reviewer：只读复核 task-6 输出、卡片、SPEC、git 状态
Lead：修正 server-only give 静默失败 → 重跑 warnings=[]
```

结果：F-004/F-006 均 `ok=true, warnings=[]`，Reviewer 结论“通过（assert 层面）”。

## 示例：注入 bug + 修复

```text
Lead 注入 getBarWidth=14 回归
→ gametest 1 失败
→ Builder 修复（14→13）
→ Reviewer 只读复核
→ Lead 重跑 12/12
```

## 已知坑

1. **Tester 忘记收口**：完成后不标 task completed；Lead 复核后补。
2. **Reviewer 不跑构建**：只读复核 + Lead 动态验收配合。
3. **写范围重叠**：两个 teammate 改同一文件会冲突；拆文件或依赖。
4. **并行卡片竞态**：客户端卡片必须顺序执行。
5. **实验性**：团队状态可能不同步；以 Lead 独立验证为准。

## 相关文档

- [完整说明书](MANUAL.md)
- [演示](DEMO.md)
- [参考模组报告](../lab/forge-1.20.1-test/REPORT.md)
