# M4 客户端/服务端补充检查

日期：2026-10-18
环境：`lab/forge-1.20.1-test` + `mcdev_probe-0.1.0-dev.jar`
- `run-server/`（probe 39001，端口 25566）
- `run-client/`（probe 39002，quickPlay 进服）

## 探针新增

- `POST /ui/close`：关闭当前容器界面，验证关闭再打开。
- `POST /player/use_item`：客户端右键空中（`MultiPlayerGameMode.useItem`），验证物品主动使用。

重新 `devJar` 后复制到 `run-server/mods`、`run-client/mods`、`run/mods`。

## 结果

### F-001 方块充能交互

- 服务端：block Energy=0，主手 `charged_item{Energy:500}`；
- 客户端 `/player/use` 右键 charger；
- 结果：

```text
use result=SUCCESS
block Energy = 100
item Energy  = 400
```

转移 100 FE 正确。

### F-002 GUI

- 打开：`screen=com.example.examplemod.ChargerScreen`，title=`Charger`；
- 37 个槽位，slot 0 = Charged Item ×1；
- `data merge block ... {Energy:5000}` 后截图：
  `Energy: 5000/10000`，右侧能量条有红色填充；
- `/ui/close` 后 `status.screen` 消失；
- 再 `/player/use` 重新打开：slot 0 仍是 Charged Item，能量仍 5000/10000；
- shift-click 0→36→0，count 始终 1（前一轮已验证，本轮菜单逻辑未变）。

截图：
- `run-client/screenshots/mcdev-1792258312165.png`（5000/10000）
- `run-client/screenshots/mcdev-1792258351039.png`（关闭再打开后）

### F-003 充能物品

- 服务端：主手 `charged_item{Energy:500}`；
- 客户端 `POST /player/use_item`；
- 结果：`used=true, result=PASS`；服务端物品 Energy 500 → 400。

说明 `ChargedItem.use` 的空中右键路径生效。

tooltip 视觉已补测：inventory hover `charged_item{Energy:5000}`，截图
`M4-F003-tooltip.png` 显示 `Charged Item / Energy: 5000/10000`。

### F-005 命令与进度

- 命令 `/charger info <pos>` 已由 GameTest 覆盖（777 → result 1）；
- `give Dev examplemod:charged_item 1` 后，服务端日志：

```text
Dev has made the advancement [Charger]
```

- 再执行 `advancement grant Dev only examplemod:charger/root`：

```text
Couldn't grant advancement [Charger] to Dev as they already have it
```

说明 advancement 的 `inventory_changed` 触发正常。

玩家 raycast 路径已补测：客户端站在 charger 前执行 `/charger info`，聊天输出
`Charger energy: 0/10000 at 0, 64, 0`，截图 `M4-F005-raycast.png`。

advancement toast 已补测：revoke 后给 `charged_item`，截图
`M4-F005-advancement-toast.png` 显示 `Advancement Made! Charger`。

## 未覆盖

1. loot table 实际破坏掉落已由服务端 `loot_break` 覆盖；客户端 creative 破坏无掉落。
