# M4 F-005 报告：/charger info + advancement

日期：2026-10-18
工程：`lab/forge-1.20.1-test`
状态：命令与 advancement 已实现；GameTest 9/9；玩家 raycast 路径待客户端实测。

## 已实现

### 命令

`ChargerCommand`：

- `/charger info`：玩家看向的方块，若是 charger 打印
  `Charger energy: N/10000 at x,y,z`；不是 charger 给失败反馈。
- `/charger info <pos>`：指定方块坐标，便于服务端/GameTest 测试。
- 注册：`@EventBusSubscriber(bus = FORGE)` 监听 `RegisterCommandsEvent`。
- 非玩家执行 `/charger info` 且不带坐标：明确提示。

### Advancement

`data/examplemod/advancements/charger/root.json`：

- `display`：icon `charged_item`，title/description 走 lang；
- 触发：`minecraft:inventory_changed`，items 包含 `examplemod:charged_item`；
- `requirements`：一条 `has_charged_item`；
- 背景：vanilla stone。

`en_us.json` 增加：

```json
"advancements.examplemod.charger.root.title": "Charger",
"advancements.examplemod.charger.root.description": "Obtain a Charged Item"
```

## 实测

### GameTest

新增 `chargerCommandInfo`：

1. 放 charger，设能量 777；
2. `server.getCommands().performPrefixedCommand(source, "charger info <pos>")`；
3. 断言返回 1。

结果：

```text
[Server thread/INFO] [minecraft/MinecraftServer]: Charger energy: 777/10000 at 1, -59, 11
9 tests are now running!
All 9 required tests passed :)
```

### Datapack

服务端启动日志：

```text
AdvancementList: Loaded 1272 advancements
```

（之前 1271；新增的 charger/root 被加载，无 JSON 错误。）

### 客户端 raycast

`mcdev_prepare kind=both`，客户端站在 charger 前面，执行 `/charger info`：

```text
Charger energy: 0/10000 at 0, 64, 0
```

截图：`M4-F005-raycast.png`。  
覆盖了 `player.pick(5.0D)` → `BlockHitResult` → charger 查询路径。

### advancement toast

revoke 后给客户端 `charged_item`：

```text
Advancement Made! Charger
```

截图：`M4-F005-advancement-toast.png`（toast 黄色标题 + Charger）。

## 未覆盖 / 下一步

1. 关闭再打开 GUI 的持久化。
2. 注入 5 个 bug，开始回归闭环。
