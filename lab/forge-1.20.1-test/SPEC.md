# Charger 测试模组 SPEC

环境：Windows + Minecraft 1.20.1 + Forge 47.4.28 + Java 17
命名空间：`examplemod`
版本：0.1.0

## F-001 充能方块

- 方块 ID：`examplemod:charger`，有方块实体。
- 能量范围 0..10000，默认 0，NBT key `Energy`，保存/加载后不丢。
- 手持 `examplemod:charged_item`（Energy > 0）右键：从物品转移 100 FE 到方块，方块不超过 10000，物品不低于 0。
- 空手右键：打开 GUI（F-002）。
- 比较器输出：`floor(energy * 15 / 10000)`，即 0..15。
- 被破坏时掉落自身。

## F-002 充能 GUI

- 空手右键 `charger` 打开标题为 `Charger` 的容器界面。
- 界面包含：
  - 1 个充能槽（放 `charged_item`）；
  - 能量条（0..10000）；
  - 玩家背包。
- shift-click 在充能槽与玩家背包之间移动物品，**不得复制/丢失**。
- 关闭再打开，方块实体能量和槽位内容保持。

## F-003 充能物品

- 物品 ID：`examplemod:charged_item`，最大堆叠 1。
- NBT key `Energy`，范围 0..10000。
- tooltip 显示 `Energy: N/10000`。
- 右键空中：消耗 100 FE（不低于 0），无其它副作用要求。
- 物品模型/贴图/语言键齐全。

## F-004 配方与掉落

- 有序配方：`examplemod:charger`，材料为铁锭、红石、铜锭（具体形状见 recipe JSON）。
- 配方输出必须是 `examplemod:charger`，数量 1。
- loot table：普通破坏掉落 1 个 charger。
- 配方/标签 JSON 通过静态审计。

## F-005 命令与进度

- 命令 `/charger info`：打印玩家看向的 charger 的当前能量；看向非 charger 时给出明确反馈。
- Advancement `examplemod:charger/root`：玩家首次获得 `charged_item` 时触发；JSON 合法。
- 命令在单人/服务端均可执行；无权限崩溃。

## F-006 充能物品能量条

- `examplemod:charged_item` 在物品栏显示类似耐久条的能量条。
- Energy 为 0 或 MAX（10000）时不显示能量条。
- 0 < Energy < MAX 时显示；宽度 = `max(1, energy * 13 / 10000)`。
- 颜色：
  - energy >= 5000：绿色；
  - energy >= 2000：黄色；
  - 其他：红色。
- 不改变现有 NBT、tooltip、右键使用行为。
- 客户端物品栏/手持时可见能量条。

## F-007 生存挖掘

- `charger` 必须有 `minecraft:mineable/pickaxe` 标签。
- 玩家 survival + 铁镐挖掘后，服务端方块变 air。
- 掉落 1 个 `examplemod:charger`（ItemEntity）。
- 客户端本地 block 状态滞后不影响服务端断言。

## 非目标

- 不要求真实 FE 能量网络（Forge Energy capability 可选）。
- 不要求复杂美术、音效、粒子。
- 不要求多人同步之外的网络优化。

## 完成定义

- `mcdev_build` exit 0。
- `mcdev_gametest` exit 0（12/12）。
- F-001..F-007 卡片 live，warnings=[]。
- F-002/F-003/F-005/F-006/F-007 有客户端截图或游戏内证据。
- 5 个注入 bug 全部修复，且 `BUGS.md` 有记录。
- `REPORT.md` 有卡 × 检查项矩阵。
