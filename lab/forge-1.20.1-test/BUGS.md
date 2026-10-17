# M4 注入 bug 记录（已修复）

日期：2026-10-18
状态：5 个 bug 全部注入、检测、修复；最终 GameTest 10/10，静态审计 0 缺失。

| # | 文件 | 注入 | 检测 | 结果 |
|---|---|---|---|---|
| B1 | `ChargerBlockEntity.setEnergy` | `value < 0` 抛 `NullPointerException` | GameTest `chargerClampsEnergy` | 已修，10/10 |
| B2 | `assets/examplemod/blockstates/charger.json` | 文件缺失（改名 `.bak`） | 静态审计 1 缺失 | 已修，0 缺失 |
| B3 | `data/examplemod/recipes/charger.json` | result 改 `minecraft:stone` | GameTest `chargerRecipeExists` | 已修，10/10 |
| B4 | `ChargerMenu.quickMoveStack` | 源槽留副本 + 提前 return | GameTest `chargerMenuShiftClickNoDuplication` | 已修，10/10 |
| B5 | `ChargerBlockEntity.getComparatorOutput` | 永远返回 0 | GameTest `chargerComparator` | 已修，10/10 |

## B4 注入的调整

第一次注入只加了：

```java
slot.set(result.copy());
```

随后统一的清理逻辑 `if (stack.isEmpty()) slot.set(ItemStack.EMPTY);`
又把副本清掉了，所以 10 个测试仍全过——说明**这个 bug 被后续清理逻辑掩盖**。
第二次改成在分支里直接 `return result;`，跳过清理，才复现“源槽 + 玩家背包各 1 个”的复制。
GameTest 随即失败：

```text
chargermenushiftclicknoduplication failed! charger slot should be empty
```

这条记录保留在报告里，作为“测试通过不等于没有 bug；注入要能真正触发症状”的例子。

## 最终

```text
mcdev_build      exitCode=0
mcdev_gametest   exitCode=0   All 10 required tests passed
static audit     required=8 missing=0
```
