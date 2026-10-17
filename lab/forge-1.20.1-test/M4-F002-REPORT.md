# M4 F-002 报告：Charger GUI（第一版）

日期：2026-10-18
工程：`lab/forge-1.20.1-test`
状态：GUI 可开、槽位可点、shift-click 不复制；关闭再打开持久化待补。

## 已实现

- `ChargerBlockEntity` 实现 `Container` + `ContainerData`：
  - 1 个充能槽（`SLOT=0`），只接受 `charged_item`；
  - NBT 保存/加载 `Item`；
  - 数据槽：`0=energy`、`1=MAX_ENERGY`；
  - `stillValid` 距离检查。
- `ChargerMenu`：
  - 1 个充能槽 + 36 个玩家背包槽；
  - `quickMoveStack`：0 ↔ 1..37，正确扣减，不复制；
  - `getEnergy()` / `getMaxEnergy()`。
- `ChargerScreen`：
  - 176×166 自绘背景：面板、37 个槽位框、能量条、`Energy: N/10000` 文本；
  - 标题 `Charger`；
  - 无自定义贴图，纯 `GuiGraphics.fill` 绘制。
- `ClientSetup`：注册 `MenuScreens`。
- `ModRegistry`：`MenuType<ChargerMenu>` 注册。
- `ChargerBlock.use`：空手/非充能物品右键打开 GUI。

## 客户端实测

环境：
- `lab/forge-1.20.1-test` 的 `runServer` + `runClient`；
- 两个目录独立：`run-server/`、`run-client/`；
- 两边 `mods/` 放 `mcdev_probe-0.1.0-dev.jar`；
- 客户端 `--quickPlayMultiplayer 127.0.0.1:25566`。

步骤与结果：

| 步骤 | 结果 |
|---|---|
| 服务端 `setblock 0 64 0 examplemod:charger` | 成功 |
| `item replace block 0 64 0 container.0 with examplemod:charged_item 1` | 槽位有物品 |
| 客户端 `/player/use` (0,64,0) face=up | `{"used":true,"result":"SUCCESS"}` |
| `/status` | `screen=com.example.examplemod.ChargerScreen`, title=`Charger` |
| `/ui/snapshot` | 37 个槽位；slot 0 = Charged Item ×1；`guiLeft=125, guiTop=37` |
| `/screenshot` + `read_image` | 能看到标题、能量文本、槽位框、玩家背包、能量条 |
| shift-click slot 0 | 物品从 slot 0 → slot 36（玩家背包），count 1 |
| shift-click slot 36 | 物品从 slot 36 → slot 0，count 1，无复制 |

截图：`run-client/screenshots/mcdev-1792257239553.png`（854×480，47288 bytes）。

## 发现并修复的视觉问题

第一版把能量文本画在标题同一行（`y+6`），截图里出现
`Charger0/10...` 重叠。已改为：
- 标题保持默认 `y+6`；
- 能量文本下移到 `y+20`；
- 给全部 37 个槽位画背景框；
- 能量条放右侧 149..159。

重测截图确认不再重叠。

## 未覆盖 / 下一步

1. 关闭再打开 GUI，确认物品和能量持久化（可加 probe `/ui/close` 或按键注入）。
2. 能量 > 0 时能量条填充的视觉检查。
3. 充能槽只接受 `charged_item` 的负向测试。
4. F-005 `/charger info` + advancement。
5. 注入 5 个 bug 后回归。
