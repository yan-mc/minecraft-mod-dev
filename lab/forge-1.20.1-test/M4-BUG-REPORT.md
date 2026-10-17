# M4 注入 bug 报告：检测 → 修复 → 回归

日期：2026-10-18
工程：`lab/forge-1.20.1-test`
结论：5 个 bug 全部检出，全部修复，最终 GameTest 10/10，静态审计 0 缺失。

## 1. 基线

注入前：

```text
mcdev_build      exitCode=0
mcdev_gametest   exitCode=0   All 10 required tests passed
static audit     required=8 missing=0
```

新增的 `chargerMenuShiftClickNoDuplication` 使用 `GameTestHelper.makeMockPlayer()`
直接测试 `ChargerMenu.quickMoveStack`，不需要客户端即可抓 shift-click 复制。

## 2. 注入

见 `BUGS.md`。一次注入 5 个：

- B1 `setEnergy` 负值 NPE；
- B2 blockstate 缺失；
- B3 recipe 输出 `minecraft:stone`；
- B4 `quickMoveStack` 源槽留副本；
- B5 comparator 恒 0。

## 3. 检测结果

### GameTest

```text
mcdev_gametest
exitCode=1
summary=4 required tests failed

chargerclampsenergy failed! energy must not be negative
chargercomparator failed! comparator signal should be 7, got 0
chargermenushiftclicknoduplication failed! charger slot should be empty
chargerrecipeexists failed! recipe result should be charger
```

4 个 GameTest 全部命中，对应 B1/B5/B4/B3。

### 静态审计

```text
required=8 missing=1
MISSING assets\examplemod\blockstates\charger.json
```

命中 B2。

### 汇总

| Bug | 检测层 | 是否检出 |
|---|---|---|
| B1 | GameTest | ✅ |
| B2 | 静态审计 | ✅ |
| B3 | GameTest + 静态 | ✅ |
| B4 | GameTest（mock player） | ✅ |
| B5 | GameTest | ✅ |

5/5 检出。

## 4. 修复与回归

修复内容：

- B1：恢复 `Math.max(0, Math.min(MAX_ENERGY, value))`；
- B2：恢复 blockstate 文件；
- B3：recipe result 改回 `examplemod:charger`；
- B4：删除源槽副本 + 提前 return，恢复统一清理逻辑；
- B5：恢复 `energy * 15 / MAX_ENERGY`。

回归：

```text
mcdev_build      exitCode=0
mcdev_gametest   exitCode=0   All 10 required tests passed
static audit     required=8 missing=0
```

## 5. 经验

1. **B4 第一次注入无效**：源槽副本被后面的 `slot.set(ItemStack.EMPTY)` 清掉，测试仍全过。
   说明“注入 bug”必须真的改变可观察行为，不能只看代码看起来像 bug。
2. **B2 只能静态审计**：资源缺失不会让 GameTest 失败，必须单独检查。
3. **mock player 很有用**：`GameTestHelper.makeMockPlayer()` 让 Menu/quickMoveStack
   可以在 GameTest 里直接测，不必每次都开客户端。
4. 客户端 UI 测试仍然有价值：视觉重叠（F-002 的标题/能量文本）只有截图能发现。
