# M4 F-001/F-004 报告：充能方块 + 充能物品 + 配方（第一版）

日期：2026-10-18
工程：`lab/forge-1.20.1-test`
状态：GameTest 通过；玩家交互/客户端卡待补。

## 已实现

- `ModRegistry`：`charger` 方块、`charger` BlockItem、`charged_item`、`charger` BlockEntityType。
- `ChargerBlock`：
  - `EntityBlock`，右键用 `charged_item` 转移 100 FE；
  - `getAnalogOutputSignal` 返回 `energy * 15 / 10000`；
  - 空手右键显示当前能量（GUI 在 F-002）。
- `ChargerBlockEntity`：
  - `MAX_ENERGY=10000`；`setEnergy` 做 0..10000 clamp；
  - NBT `Energy` 保存/加载；
  - `getUpdateTag`/`getUpdatePacket` 同步；
  - `getComparatorOutput()`。
- `ChargedItem`：
  - NBT `Energy`，clamp；
  - tooltip `Energy: N/10000`；
  - 右键空中消耗 100 FE。
- 资源：
  - `blockstates/charger.json`
  - `models/block/charger.json`（复用 `minecraft:block/iron_block` 贴图）
  - `models/item/charger.json`
  - `models/item/charged_item.json`（复用 `minecraft:item/redstone` 贴图）
  - `lang/en_us.json`
  - `loot_tables/blocks/charger.json`
- F-004 配方：`recipes/charger.json`（铁锭 + 红石 + 铜锭 → `examplemod:charger` ×1）。

## GameTest

新增 5 个测试：

| 测试 | 覆盖 |
|---|---|
| `chargerClampsEnergy` | 负数→0、超上限→10000、正常 5000 |
| `chargerComparator` | 5000 → 比较器 7 |
| `chargerNbtRoundTrip` | 1234 写入 NBT 可读 |
| `chargedItemEnergy` | 物品能量 500 / clamp 10000 |
| `chargerRecipeExists` | recipe 注册、结果 `examplemod:charger`、数量 1 |

结果：

```text
mcdev_gametest projectDir=lab/forge-1.20.1-test
exitCode=0
summary=All 8 required tests passed
```

## 未覆盖 / 下一步

1. 玩家右键交互（把 `charged_item` 的 100 FE 转给方块）——需要服务端 scenario/假玩家。
2. `ChargedItem.use` 右键空中 100 FE——需要服务端 scenario。
3. F-002 GUI；F-005 命令/进度。
4. 注入 5 个 bug 后回归。
