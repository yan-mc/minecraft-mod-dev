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
| F-001 | dryRun | 生成 setblock/give/use/data get/assert gte；comparator 警告 |
| F-002 | dryRun + live | warnings=[]；客户端执行 8 步全过 |
| F-006 | dryRun | set_item_energy / item_bar_* 警告，只生成 give |
| S-001 | live（server） | setblock + data merge + data get + assert 123 全过 |

## 5. 限制

1. `actions.give` 只发 `/give`，**不会自动选中主手**；F-001 若要 live 执行，需要加 `select_item` 或手动清/放主手。
2. 暂不支持 `comparator`、`recipe_output`、`loot_drop`、`item_nbt`、`item_bar_*` assert，会进 `warnings`。
3. 客户端卡片要求 client 已进服（不是只在 `ConnectScreen`）。
4. 卡片的 `spec`/`tiers`/`ui` 字段只作为文档，不参与执行。

## 6. 结论

- `mcdev_scenario card=F-002.yml` 一条命令跑通客户端打开 GUI + UI 断言。
- YAML 解析器覆盖现有卡片结构；不支持的字段显式警告，不静默通过。
- 下一步扩展 comparator/recipe/loot/item_bar assert，并给 `give` 加“选中主手”选项。
