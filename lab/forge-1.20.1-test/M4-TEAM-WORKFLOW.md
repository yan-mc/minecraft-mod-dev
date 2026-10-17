# M4 团队工作流实录：F-006 物品能量条

日期：2026-10-18
目标：用 Agent Teams + `mcdev_*` 工具完成一个真实小功能，验证“建卡 → 分派 → 实现 → 测试 → Lead 复核”。

## 1. 角色与任务

| 角色 | 成员 | 任务 |
|---|---|---|
| Lead | lead | 建 F-006 卡/SPEC、建 task-2/3、复核、提交 |
| Builder | builder-item | 只改 `ChargedItem.java`，实现能量条 |
| Tester | tester-item | 只改 `ExampleGameTests.java`，写 `chargedItemBar` |

共享任务：

- `task-2` F-006 Builder，写范围 `ChargedItem.java`；
- `task-3` F-006 Tester，`blockedBy=[task-2]`，写范围 `ExampleGameTests.java`。

## 2. 执行过程

1. Lead 在 `SPEC.md` 增加 F-006，写 `cards/F-006.yml`。
2. Lead 创建 task-2、task-3；`spawn_teammate` 起 `builder-item`，把 task-2 分配给 builder。
3. builder-item：
   - 读卡/SPEC，只改 `ChargedItem.java`；
   - 新增 `isBarVisible` / `getBarWidth` / `getBarColor`；
   - 跑 `mcdev_build`：exit 0；
   - 标记 task-2 completed，回报 Lead。
4. task-3 解除阻塞后，Lead `spawn_teammate` 起 `tester-item`，分配 task-3。
5. tester-item：
   - 只改 `ExampleGameTests.java`；
   - 新增 `chargedItemBar`（11 条断言，覆盖 0/10000/5000/2000/500）；
   - 跑 `mcdev_gametest`：`All 12 required tests passed`；
   - 回报 Lead。
6. Lead 独立复核：
   - 读 diff；
   - 再次跑 `mcdev_build`（exit 0）；
   - 再次跑 `mcdev_gametest`（12/12）；
   - 把 task-3 标 completed（Tester 没有自己收口）；
   - 提交。

## 3. 结果

```text
chargedItemBar:
  energy 0      -> hidden
  energy 10000  -> hidden
  energy 5000   -> visible, width 6, green
  energy 2000   -> visible, width 2, yellow
  energy 500    -> visible, width 1, red
```

```text
mcdev_build      exit 0
mcdev_gametest   exit 0, All 12 required tests passed
```

## 4. 观察

- 两个 fresh teammate 都能直接使用 `mcdev_build` / `mcdev_gametest`，不需要额外配置。
- 写范围拆分有效：builder 只动 `ChargedItem.java`，tester 只动 `ExampleGameTests.java`，没有互相覆盖。
- 依赖 `blockedBy` 让 Tester 等 Builder 完成后再开始。
- **Tester 没有自己把 task-3 标 completed**，需要 Lead 复核后收口；后续可在 Tester prompt 里明确“完成后 `team_task_update complete`”。
- 本轮没有起 Reviewer；Lead 自己做了 diff review。正式流程应加 Reviewer 只读复核。

## 5. 后续

1. 加 Reviewer 角色：只读审 diff + 审测试是否对 SPEC。
2. `mcdev_scenario` 接卡片解析，让 Tester 直接跑卡。
3. 用同样流程做一次“注入 bug → 团队修”的完整演练。
