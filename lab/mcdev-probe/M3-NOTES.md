# M3：Forge 1.20.1 客户端 probe（v0）

日期：2026-10-18
工程：`lab/mcdev-probe`
目标：验证“AI 能看见并点击真实客户端 UI”。

## 已实现

`ClientProbeServer`（客户端专用，127.0.0.1:39001，token `mcdev`）：

| Endpoint | 作用 |
|---|---|
| `GET /status` | side/screen/title/fps/GUI 尺寸/player/dimension |
| `GET /ui/snapshot` | 当前 Screen、标题、AbstractWidget 树（class/x/y/w/h/message/active/visible）、容器槽位（index/x/y/screenX/screenY/item/count）、guiLeft/guiTop |
| `POST /ui/click` | 原始坐标 `{x,y,button}` 或槽位 `{slot,button,shift}`；shift 用 `ClickType.QUICK_MOVE` |
| `GET /screenshot` | `Screenshot.grab` 写 PNG；轮询最多 2s 等文件落盘；返回 path/exists/size |
| `POST /quit` | `Minecraft.stop()` 退出客户端 |
| `GET /log/tail` | 结构化日志环形缓冲 |

服务端 probe 只在 `Dist.DEDICATED_SERVER` 启动；客户端不会和它抢 39001。

## 实测（`gradlew runClient`）

- 客户端离线用户 `Dev`，FPS 59–60，窗口 854×480，GUI 427×240。
- 启动后首个 Screen：`AccessibilityOnboardingScreen`。
- `/ui/snapshot` 返回 5 个控件，包括：
  - `CycleButton` "Narrator: OFF"
  - `TextAndImageButton` "Accessibility Settings..."
  - `TextAndImageButton` "Language..."
  - `Button` "Continue"
- `/ui/click {"x":213,"y":222}` 点击 Continue 后：
  - `/status` → `TitleScreen`
  - `/ui/snapshot` → Singleplayer / Multiplayer / Minecraft Realms / Mods / Options... / Quit Game 等按钮及坐标。
- `/screenshot` 产出 PNG（854×480，约 471 KB / 570 KB），`read_image` 能直接看到画面。

## 踩坑

1. **`Screenshot.grab` 是异步落盘**：调用后立刻 `exists` 会是 false。改为 HTTP 线程轮询最多 2s 再返回。
2. **文件名**：`Screenshot.grab` 不会自动加 `.png`；现在传入 `mcdev-<ts>.png`。
3. **客户端/服务端 probe 冲突**：同一端口，服务端 probe 加 `Dist.DEDICATED_SERVER` 守卫。
4. 客户端 UI 坐标是 **GUI scaled**（427×240），不是窗口像素（854×480）；点击要用 GUI 坐标。

## 下一步

1. 容器 GUI：进入世界 → 打开箱子/工作台 → `/ui/snapshot` 槽位 → `/ui/click slot` → 校验物品变化。
2. 把 `client probe` 的截图/UI 能力接进 DSH 插件的 `mcdev_ui`。
3. 进入 M4：5 张功能卡 + 5 类注入 bug 的闭环。

---

## M3b：容器 GUI + 槽位点击 + shift-click（已完成）

### 连接问题

第一次 `runClient` + `runServer` 共用同一个 `run/` 目录，客户端报
`Failed to connect to the server / Timed out`。原因是服务端和客户端同时写
`run/logs/latest.log` 等文件（日志里 `Unable to delete file ... latest.log`）。
修复：拆成
- `run-server/`（server）
- `run-client/`（client）
- `run-gametest/`（gameTestServer）

并让客户端 probe 用 **39002**、服务端 probe 用 **39001**，避免同端口冲突。
客户端用 `--quickPlayMultiplayer 127.0.0.1:25566` 自动进服。

### 容器实测

服务端准备：

```
fill -2 63 -2 2 63 2 minecraft:stone
setblock 0 64 0 minecraft:chest
item replace block 0 64 0 container.0 with minecraft:diamond 1
tp Dev 0.5 64 1.5 180 0
```

客户端 `/player/use {"x":0,"y":64,"z":0,"face":"up"}`：

- 返回 `{"used":true,"result":"SUCCESS"}`
- `/status` → `screen=ContainerScreen`，title=`Chest`
- `/ui/snapshot` → **63 个槽位**：0..26 箱子，27..62 玩家背包；
  slot 0 = Diamond count 1；`guiLeft=125, guiTop=36`；slot 0 屏幕坐标 (141,62)。

点击：

| 操作 | 结果 |
|---|---|
| `/ui/click {"slot":0}` | 拿起 Diamond |
| `/ui/click {"slot":27}` | 放入玩家背包 slot 27 |
| 快照 | slot 0 空；slot 27 Diamond count 1 |
| 服务端再放 Diamond 到 slot 0 | — |
| `/ui/click {"slot":0,"shift":true}` | 快速移动 |
| 快照 | slot 0 空；slot 27 Diamond **count 2**（与已有堆叠合并） |

这验证了后续查“shift-click 复制/丢失”类 bug 的关键路径。

### 结论

客户端 probe 现在具备：
- 真实 UI 控件树；
- 真实鼠标点击（Screen 层 + 容器槽位 + shift 快速移动）；
- 真实截图（可给模型看）；
- 进服/开箱子/点击/断言的完整工具面。

### 下一步

1. 把客户端 probe 接进 DSH 插件的 `mcdev_ui` / `mcdev_client`。
2. M4：5 张功能卡 + 5 类注入 bug 的闭环。
