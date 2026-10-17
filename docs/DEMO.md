# 10 分钟演示：从启动到截图

前置：已完成 [安装](INSTALL.md) 的步骤 1–4，插件 v0.3.0，测试工程 `lab/forge-1.20.1-test`。

## 1. 启动 server + client

```text
mcdev_prepare
  projectDir=F:\...\lab\forge-1.20.1-test
  kind=both
```

期望返回两个 ready：

```json
{"ok":true,"results":[
  {"kind":"server","pid":...,"port":39001,"ready":true},
  {"kind":"client","pid":...,"port":39002,"ready":true}
]}
```

## 2. 看两侧状态

```text
mcdev_status side=server
mcdev_status side=client
```

server 返回 tick/version；client 返回 screen/fps。

## 3. 准备充电器

```text
mcdev_command side=server cmd="fill -2 63 -2 2 63 2 minecraft:stone"
mcdev_command side=server cmd="setblock 0 64 0 examplemod:charger"
mcdev_command side=server cmd="tp Dev 0.5 64 1.5 180 0"
mcdev_command side=server cmd="item replace entity Dev weapon.mainhand with minecraft:air"
```

## 4. 打开 GUI

```text
mcdev_use x=0 y=64 z=0 face=up
```

期望：

```json
{"used":true,"result":"SUCCESS","screen":"...ChargerScreen"}
```

## 5. 读 UI 树

```text
mcdev_ui
```

期望：

- title=`Charger`
- 37 个槽位（1 充能槽 + 36 玩家背包）
- slot 0 可放 `charged_item`

## 6. 截图并让模型看

```text
mcdev_screenshot
```

返回 `path` 后：

```text
read_image path=<返回的 path>
```

能看到标题、能量文本、槽位框、玩家背包、能量条。

## 7. shift-click 验证

```text
mcdev_click slot=0 shift=true
mcdev_ui
```

slot 0 物品移动到玩家背包；再：

```text
mcdev_click slot=36 shift=true
mcdev_ui
```

物品回到 slot 0，总数不增加。

## 8. 跑 GameTest

```text
mcdev_gametest projectDir=F:\...\lab\forge-1.20.1-test
```

期望：

```text
exitCode=0
summary=All 12 required tests passed
```

## 9. 停止

```text
mcdev_stop side=all
```

## 10. 更多

- 卡片：[F-001](../lab/forge-1.20.1-test/cards/F-001.yml) … [F-006](../lab/forge-1.20.1-test/cards/F-006.yml)
- 报告：[REPORT.md](../lab/forge-1.20.1-test/REPORT.md)
- 团队演练：[M4-TEAM-WORKFLOW.md](../lab/forge-1.20.1-test/M4-TEAM-WORKFLOW.md)
