# M4 卡片执行实录：mcdev_scenario card

日期：2026-10-18
插件：v0.3.1
目标：验证 `mcdev_scenario card=<cards/F-002.yml>` 能直接翻译并执行客户端卡片。

## 1. 卡片

`cards/F-002.yml` 的 setup 增加清空主手：

```yaml
setup:
  commands:
    - "setblock 0 64 0 examplemod:charger"
    - "tp Dev 0.5 64 1.5 180 0"
    - "item replace entity Dev weapon.mainhand with minecraft:air"
```

## 2. 启动

```text
mcdev_prepare projectDir=<...>/forge-1.20.1-test kind=both
→ server pid=25620 port=39001 ready
→ client pid=18276 port=39002 ready
```

## 3. 执行

```text
mcdev_scenario card=<...>/cards/F-002.yml
```

返回：

```json
{"ok":true,"warnings":[],"steps":[
  {"index":0,"type":"command","ok":true,...},
  {"index":1,"type":"command","ok":true,...},
  {"index":2,"type":"command","ok":true,...},
  {"index":3,"type":"use","ok":true,"data":{"ok":true,"data":{"used":true,"result":"SUCCESS"}}},
  {"index":4,"type":"ui","ok":true,...},
  {"index":5,"type":"assert","ok":true,"actual":"Charger"},
  {"index":6,"type":"ui","ok":true,...},
  {"index":7,"type":"assert","ok":true,"actual":0}
]}
```

对应：

- 打开 `ChargerScreen`；
- title=`Charger`；
- 37 槽位；
- `slot_exists: 0` 通过；
- `warnings=[]`。

## 4. 其他卡片

| 卡片 | 模式 | 结果 |
|---|---|---|
| F-001 | dryRun + live | `warnings=[]`；give.select + use + data get（5200 ≥ 100）+ block analog=7 全过 |
| F-002 | dryRun + live | `warnings=[]`；wait + use + wait + ui/title/slot 全过 |
| F-004 | dryRun + live | recipe×1、loot total=1、loot_break removed=true+drops charger×1；warnings=[] |
| F-006 | dryRun + live | item_bar visible/width=6/color=65280，warnings=[] |
| S-001 | live（server） | setblock + data merge + data get + assert 123 全过 |

## 5. 限制

1. `actions.give` 新增 `select=true`（放主手）；也可以用独立 `select_item` action。F-001 已用 `give.select` + `use` live 通过。
2. `comparator`/`recipe_output`/`loot_drop`/`loot_break`/`item_bar` 已支持（`block`/`recipe`/`loot`/`break`/`item` 步骤）；`item_nbt`、`craft` action 仍未支持；F-004 用 `recipe_output` 断言，不模拟合成界面。
3. 客户端卡片要求 client 已进服（不是只在 `ConnectScreen`）。
4. 卡片的 `spec`/`tiers`/`ui` 字段只作为文档，不参与执行。
5. server-only 卡片不再放 `give` setup（无玩家会静默失败）；command 步骤本身不对 `result` 断言。
6. F-006 客户端视觉已补验：热栏截图 `M4-F006-hotbar-full.png` / 放大 `M4-F006-hotbar.png` 可见绿色 6/13 条；F-004 `loot_break` 已做服务端 `destroyBlock(pos,true)` + ItemEntity E2E，但仍不是客户端玩家采矿。

## 6. 客户端 handshake 排查

失败时：

- client：`Starting new vanilla impl connection` 后没有 modded handshake；
- server：已发 22 个 handshake 包，30 秒后 `lost connection: Disconnected`；
- client Render thread 正常 tick，Netty thread 在 selector 空闲。

处理：

1. 改 probe 后 `clean devJar` + `mcdev_install_probe`；
2. `mcdev_prepare client/both` 等待进服，失败自动重试一次；
3. 卡片 use 前后加 `wait`。

根因未完全定位（疑 quickPlay 启动时机/增量 devJar），但重试 + clean 构建已稳定跑通本轮 F-001/F-002。

### 强制失败 / 恢复演练

- 不启动 server，直接 `mcdev_prepare kind=client timeoutMs=20000`：
  `joined=false, retry=true, lastScreen=DisconnectedScreen`；`attempts[]` 带每次 pid/logPath/lastScreen；
- 恢复演练：先起 client，延迟 35s 起 server，`timeoutMs=30000`：
  attempt1/2 `DisconnectedScreen`，attempt3 `joined=true, lastScreen=""`；
- 正常 `kind=both`：`joined=true, lastScreen=""`，attempts 长度 1；
- F-002 卡片随后 `warnings=[]` 通过。

## 7. 结论

- `mcdev_scenario card=F-001.yml` / `card=F-002.yml` 客户端 live 全过，warnings=[]。
- YAML 解析器覆盖现有卡片结构；不支持的字段显式警告，不静默通过。
- 下一步扩展 recipe/loot/item_bar assert；quickPlay 根因继续查。
