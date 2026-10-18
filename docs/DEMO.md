# Demo：Minecraft 1.20.1 Forge 模组开发闭环

版本：插件 `0.5.0`（GitHub Release v0.5.0）
工程：`lab/forge-1.20.1-test`（Charger 参考模组）

## 0. 安装

从 GitHub Release 下载：

- `minecraft-mod-dev-0.5.0.zip`
- `mcdev_probe-0.1.0-dev.jar`

```text
plugin_manager install_bundle target=<解压目录>/minecraft-mod-dev
```

把 probe devJar 放进 `run-server/mods`、`run-client/mods`、`run/mods`，
或构建后调用 `mcdev_install_probe`。

## 1. 启动 server + client

```text
mcdev_prepare
  projectDir=F:\...\lab\forge-1.20.1-test
  kind=both
```

客户端不再用 `--quickPlayMultiplayer`：插件等客户端到 TitleScreen，
调 probe `/connect` 显式连接。期望：

```json
{"ok":true,"results":[
  {"kind":"server","pid":...,"port":39001,"ready":true},
  {"kind":"client","pid":...,"port":39002,"ready":true,"joined":true,"attempts":[{"attempt":1,...}]}
]}
```

## 2. 卡片：功能覆盖

顺序执行（不要并行跑客户端卡片）：

```text
mcdev_scenario card=...\cards\F-001.yml
mcdev_scenario card=...\cards\F-002.yml
mcdev_scenario card=...\cards\F-004.yml
mcdev_scenario card=...\cards\F-006.yml
mcdev_scenario card=...\cards\F-007.yml
```

| 卡片 | 覆盖 | 结果 |
|---|---|---|
| F-001 | 方块右键充能 + NBT + comparator analog | warnings=[]，Energy 5100，analog=7 |
| F-002 | GUI 打开/槽位/shift-click 不复制 | warnings=[] |
| F-004 | recipe/loot/craft（消耗背包材料） | warnings=[]，crafted=true，loot/craft 各 1 charger |
| F-006 | item_bar visible/width/color | warnings=[]，width=6，color=65280 |
| F-007 | 生存挖掘（客户端 START_DESTROY_BLOCK） | warnings=[]，server block air，items charger×1 |

## 3. 视觉证据

- F-003 tooltip：`assets/tooltip.png`（`Energy: 5000/10000`）
- F-005 raycast：`assets/raycast.png`（聊天 `Charger energy: 0/10000 at 0, 64, 0`）
- F-005 advancement toast：`assets/advancement-toast.png`
- F-006 热栏能量条：`assets/hotbar.png`

## 4. GameTest

```text
mcdev_gametest projectDir=F:\...\lab\forge-1.20.1-test
```

```text
exitCode=0
summary=All 12 required tests passed
```

## 5. 团队闭环

- Tester（tester-cards）：跑 F-004/F-006，报告 ok/warnings/data；
- Reviewer（reviewer-cards）：只读复核卡片、SPEC、git 状态；
- Lead：根据 Reviewer 发现修正卡片（删掉 server-only give、补 kill items）。

见 [TEAM.md](TEAM.md)。

## 6. 停止

```text
mcdev_stop side=all
```

## 7. 排障

- 登录：不要 quickPlay；等 TitleScreen 后 `/connect`；
- probe 改完：`clean devJar` + `mcdev_install_probe`；
- 卡片顺序执行，避免 UI/物品残留竞态。

更多：[TROUBLESHOOTING.md](TROUBLESHOOTING.md)、[TOOLS.md](TOOLS.md)。
