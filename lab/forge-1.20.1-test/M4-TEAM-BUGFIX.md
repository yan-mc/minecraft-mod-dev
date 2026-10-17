# M4 团队修 bug 演练：F-006 能量条宽度回归

日期：2026-10-18
目标：Lead 注入回归 → Builder 定位/修复 → Reviewer 只读复核 → Lead 动态验收。

## 1. 注入

`ChargedItem.getBarWidth`：

```java
// 正确
Math.max(1, energy * 13 / MAX_ENERGY)
// 注入
Math.max(1, energy * 14 / MAX_ENERGY)
```

## 2. 检测

`mcdev_gametest`：

```text
exitCode=1
summary=1 required tests failed
chargedItemBar failed! bar width should be 6 at energy 5000, got 7
```

## 3. 团队修复

共享任务：

- `task-4` Builder：修复回归，写范围 `ChargedItem.java`；
- `task-5` Reviewer：只读复核，`blockedBy=[task-4]`。

### builder-fix

- 根因：整数除法乘数写成 14，`5000 * 14 / 10000 = 7`；
- 改动：`ChargedItem.java:63` 改回 13；
- 自测：`mcdev_gametest` → `All 12 required tests passed`；
- 只改 1 行，未改测试，未 commit；task-4 completed。

### reviewer-fix

只读复核，结论 **通过**：

- `getBarWidth = max(1, energy * 13 / MAX_ENERGY)`；
- `isBarVisible` / `getBarColor` 与 SPEC 一致；
- NBT/tooltip/use 未被改坏；
- `chargedItemBar` 断言 5000→6、2000→2、500→1，未被改成迎合错误实现；
- `git status` / `git diff` 为空，working tree 与 HEAD `596ba8f` 一致；
- 诚实边界：Reviewer 未独立跑构建，只做静态审查。

### Lead 动态验收

Reviewer 之后 Lead 独立重跑：

```text
mcdev_gametest
exitCode=0
All 12 required tests passed
```

最终代码与修复前 HEAD 一致（回归被完全清除），仓库干净。

## 4. 结论

- 注入 → 检测 → 团队定位 → 修复 → 只读复核 → Lead 动态验收，闭环跑通。
- Reviewer 的“未跑构建”边界被明确记录，Lead 补了动态验证，没有把静态审查当成动态通过。
- 后续可把这类演练做成固定脚本：注入 → 建 task-4/5 → 起 builder/reviewer → 出报告。
