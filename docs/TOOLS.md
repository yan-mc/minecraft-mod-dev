# mcdev_* 工具参考

插件版本：`0.3.0`，14 个工具。
全部工具前缀 `mcdev_`；探针默认端口：server `39001`，client `39002`；token `mcdev`。

## 探针类（server/client）

### `mcdev_status`

参数：`side`（server|client，必填）、`port`（可选）。

返回 JSON：

- server：`tick`、`gameTime`、`dayTime`、`players`、`version`、`frozen`、`stepBudget`
- client：`screen`、`title`、`fps`、`width`、`height`、`player`、`dimension`

### `mcdev_command`

参数：`side`、`cmd`（必填，不带 `/`）、`port`。

- server：控制台命令；
- client：以玩家身份发命令。

### `mcdev_ui`

客户端当前界面：

- `screen` / `title`
- `widgets`：class/x/y/width/height/message/active/visible
- `slots`：index/x/y/screenX/screenY/item/count
- `guiLeft` / `guiTop`

### `mcdev_click`

参数：`x`/`y`（GUI scaled 坐标）或 `slot`（容器槽位，优先生效）、`button`、`shift`、`port`。

- GUI 坐标是 **427×240 这类 GUI scaled 坐标**，不是 854×480 窗口像素。
- `shift=true` 走 `ClickType.QUICK_MOVE`（shift-click）。

### `mcdev_use`

参数：`x`、`y`、`z`、`face`（up/down/north/south/east/west）、`port`。

客户端右键方块：开箱子/工作台、触发方块交互。

### `mcdev_screenshot`

客户端截图，返回：

```json
{"path":"...png","exists":true,"size":79471,"width":854,"height":480,"screen":"..."}
```

拿到 `path` 后可用 `read_image` 直接看。

### `mcdev_run_dump`

参数：`side`、`n`（日志行数，默认 200）。

写 `<runDir>/.mcdev/runs/<id>/snapshot-<ts>.json`，返回 runId/tick/gameTime/frozen/logs/path。

## 编排类

### `mcdev_install_probe`

参数：`probeJar`（绝对路径）、`runDirs`（数组）。

校验：

1. 文件名必须是 `*-dev.jar`；
2. `jar xf` 解出 `META-INF/MANIFEST.MF`；
3. manifest 必须含 `MixinConfigs: mcdev_probe.mixins.json`；
4. 通过后复制到每个 `<runDir>/mods/<name>`。

### `mcdev_scenario`

参数（二选一）：

- `steps`（数组）：直接执行；
- `card`（字符串）：YAML 卡片绝对路径，先翻译成 steps；
- `dryRun`（可选）：只翻译/报告，不执行。

卡片转换支持：

- `setup.commands` → `command`；
- `actions.give` → `give`；
- `actions.use_on` / `use_item_on` → `use`；
- `actions.command` → `command`；
- `assert.screen_title` → `ui` + `data.title`；
- `assert.slot_exists` → `ui` + `data.slots.<n>.index`；
- `assert.block_entity_nbt` → `data get block ...` + `data.result`；
- `assert.no_exception` → 跳过（probe 错误本身会让步骤失败）。

暂不支持的 action/assert 会进入 `warnings`，不会静默当通过。

步骤：

| type | 主要字段 |
|---|---|
| `status` | `side`, `port` |
| `command` | `side`, `cmd`, `port` |
| `use` | `x`, `y`, `z`, `face`, `port` |
| `ui` | `port` |
| `click` | `slot` 或 `x/y`, `button`, `shift`, `port` |
| `screenshot` | `port` |
| `run_dump` | `side`, `n`, `port` |
| `wait` | `ms` |
| `assert` | `path`, `op`, `value`, `step`（可选） |

`assert` 的 `path` 是点路径（如 `data.version`、`data.slots.0.item`），
`op` 支持 `eq/ne/contains/gt/gte/lt/lte/exists`；默认对最近一个数据步骤断言。
失败立即停止，返回：

```json
{"ok":false,"failedAt":1,"steps":[...]}
```

卡片示例：

```text
mcdev_scenario card=<repo>/lab/forge-1.20.1-test/cards/F-002.yml dryRun=true
mcdev_scenario card=<repo>/lab/forge-1.20.1-test/cards/S-001-server-smoke.yml
```

### `mcdev_build`

参数：`projectDir`（含 `gradlew.bat` 的绝对路径）。

运行 `gradlew build --console=plain`，返回 `{task, exitCode, tail}`。

### `mcdev_gametest`

参数：`projectDir`。

运行 `gradlew runGameTestServer`，返回：

```json
{"task":"runGameTestServer","exitCode":0,"summary":"All 12 required tests passed","tail":"..."}
```

`exitCode` 等于失败 required 测试数。

### `mcdev_prepare`

参数：`projectDir`、`kind`（server|client|both）、`timeoutMs`（默认 180000）。

- 用 `gradlew runServer/runClient` 启动；
- 等待对应 probe `/status` 返回正确 side；
- 返回 pid/port/logPath；已 ready 时 `alreadyRunning=true`；
- 日志写 `<projectDir>/.mcdev/launch-<kind>-<ts>.log`。

> 等待的是 **probe ready**，不是“客户端进服”。

### `mcdev_stop`

参数：`side`（server|client|all）。

先 probe `stop`/`quit`，2 秒后仍在跑则 Windows `taskkill /T /F`。

### `mcdev_launch_status`

无参数，列出 `mcdev_prepare` 启动的进程：kind/pid/projectDir/logPath/port/startedAt/running/exitCode。

## 典型组合

```text
mcdev_install_probe → mcdev_prepare kind=both
→ mcdev_status / mcdev_use / mcdev_ui / mcdev_click / mcdev_screenshot
→ mcdev_gametest
→ mcdev_stop side=all
```

```text
mcdev_scenario steps=[
  {"type":"status","side":"server"},
  {"type":"assert","path":"data.version","op":"eq","value":"1.20.1"},
  {"type":"command","side":"server","cmd":"time set day"}
]
```
