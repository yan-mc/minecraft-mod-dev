# 团队模式

插件挂载 DSH 官方**实验性 Agent Teams**（`@deepseek-ai/dsh-experimental-agent-team`），团队模式默认开启，传统 subagent 四行被禁用。

## 角色

| 角色 | 职责 |
|---|---|
| Lead | SPEC/TESTPLAN、建卡、拆任务、分派、最终验收、提交 |
| Builder | 只改 `src/main`；不写测试、不提交 |
| Tester | 只改 `src/test` 或 GameTest；不修改实现来让测试过 |
| Analyst | 读失败日志/数据库，定位根因，给修复建议 |
| Reviewer | 只读复核 diff/测试是否对 SPEC；不改文件 |
| Researcher | 外部资料/版本调研；不改代码 |

## 任务板

工具：

- `team_task_create`：建任务，支持 `blocked_by`、`write_scopes`
- `team_task_list` / `team_task_get`
- `team_task_update`：`claim` / `reassign` / `complete` / `edit` / `set_dependencies`
- `spawn_teammate` / `send_message` / `wait_agent` / `list_agents` / `interrupt_agent`

规则：

- 每个任务写清：目标文件、验收命令、禁止事项。
- 写范围用 `writeScopes` 标出来；并行任务不要重叠。
- 有依赖时用 `blocked_by`，不要靠“等一会儿”。
- Teammate 完成后要自己 `team_task_update complete`；Lead 复核后再提交。
- Lead 必须独立重跑构建/测试，不把 teammate 的“已通过”直接当最终结果。

## 示例：F-006 物品能量条

```text
task-2 Builder: 只改 ChargedItem.java，实现 isBarVisible/getBarWidth/getBarColor
task-3 Tester: blocked_by task-2，只改 ExampleGameTests.java，写 chargedItemBar
```

结果：

- builder-item：`mcdev_build` exit 0；
- tester-item：`mcdev_gametest` 12/12；
- Lead 复跑 12/12 后提交。

记录：[M4-TEAM-WORKFLOW.md](../lab/forge-1.20.1-test/M4-TEAM-WORKFLOW.md)

## 示例：注入 bug + 修复

```text
Lead 注入 getBarWidth=14 回归
→ gametest 1 失败
→ task-4 Builder 修复（14→13）
→ task-5 Reviewer 只读复核
→ Lead 重跑 12/12
```

记录：[M4-TEAM-BUGFIX.md](../lab/forge-1.20.1-test/M4-TEAM-BUGFIX.md)

## 已知坑

1. **Tester 忘记收口**：完成后不标 task completed；Lead 复核后要补。
2. **Reviewer 不跑构建**：只读复核 + Lead 动态验收配合，不要混淆静态/动态。
3. **写范围重叠**：两个 teammate 改同一文件会冲突；拆到不同文件或用依赖。
4. **实验性**：Agent Teams 仍可能不稳定；回退方式是把 `cordis.patch.yml` 里的团队行禁用、恢复传统 subagent 行。
