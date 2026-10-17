# M4 REPORT：Charger 测试模组

日期：2026-10-18
工程：`lab/forge-1.20.1-test`
范围：5 张功能卡 + F-006 追加载荷 + 5 类注入 bug + GameTest/服务端/客户端检查。

## 总结

| 项 | 结果 |
|---|---|
| 构建 | `mcdev_build` exit 0 |
| GameTest | `mcdev_gametest` exit 0，**12/12** |
| 注入 bug | 5 个全部检出，5 个全部修复 |
| 静态审计 | 8 个必需资源，missing=0 |
| 客户端 | GUI 打开/槽位/能量条/shift-click/关闭再打开通过 |
| 服务端交互 | 方块充能 500→100+400；物品空中使用 500→400 |
| advancement | `inventory_changed` 触发成功 |
| loot | `chargerLootDrop` GameTest：掉落 1 个 charger |

## 卡片矩阵

| 卡 | 功能 | 静态 | GameTest | 服务端 | 客户端 | 状态 |
|---|---|---|---|---|---|---|
| F-001 | 充能方块 | ✅ 注册/模型/lang | ✅ 能量/比较器/NBT | ✅ 右键充能 | ✅ 交互 | **通过** |
| F-002 | 充能 GUI | ✅ Menu/Screen 注册 | ✅ shift-click 不复制（mock player） | ✅ 打开/关闭 | ✅ 截图/槽位/能量条/重开 | **通过** |
| F-003 | 充能物品 | ✅ 模型/lang | ✅ 能量 clamp | ✅ 空中右键 500→400 | ⚠️ tooltip 未视觉验证 | 基本通过 |
| F-004 | 配方/掉落 | ✅ recipe/loot JSON | ✅ recipe 结果 + loot 掉落 | — | — | **通过** |
| F-005 | 命令/进度 | ✅ advancement JSON | ✅ `/charger info <pos>` | ✅ advancement 触发 | ⚠️ 玩家 raycast 未测 | 基本通过 |
| F-006 | 物品能量条 | ✅ 模型/lang | ✅ bar visible/width/color | — | ✅ 热栏截图绿色 6/13 条 | **通过**（团队实现） |

## 注入 bug 矩阵

| Bug | 检测层 | 检出 | 修复 |
|---|---|---|---|
| B1 setEnergy NPE | GameTest | ✅ | ✅ |
| B2 缺 blockstate | 静态审计 | ✅ | ✅ |
| B3 recipe 错输出 | GameTest | ✅ | ✅ |
| B4 shift-click 复制 | GameTest（mock player） | ✅ | ✅ |
| B5 comparator 恒 0 | GameTest | ✅ | ✅ |

详见 `M4-BUG-REPORT.md`。

## 证据

- GameTest：`All 12 required tests passed`
- F-006：builder-item + tester-item 团队实现，Lead 复核 12/12（见 `M4-TEAM-WORKFLOW.md`）
- 团队修 bug：注入 `getBarWidth=14` 回归 → gametest 1 失败 → builder-fix 修复 → reviewer-fix 只读复核通过 → Lead 重跑 12/12（见 `M4-TEAM-BUGFIX.md`）
- 卡片执行：`mcdev_scenario card=cards/F-002.yml` 客户端 live，warnings=[]，wait+use+ui/title/slot 全过（见 `M4-SCENARIO-CARD.md`）
- 卡片执行：`mcdev_scenario card=cards/F-001.yml` 客户端 live，warnings=[]，give.select+use+data get（5200）+comparator（analog=7）全过
- 比较器：`block` 步骤 + `/world/block` 返回 `analog=7`，assert eq 7 通过
- F-004 live：`recipe`×1、`loot` total=1、`loot_break` removed=true+drops charger×1；warnings=[]
- F-006 live：`item` 返回 visible=true/width=6/color=65280，warnings=[]
- 启动稳定性：`mcdev_prepare` 最多 3 次尝试；恢复演练 attempt1/2 Disconnected、attempt3 `joined=true`；无 server 失败演练 `joined=false, retry=true, attempts[]`
- 团队卡片执行：Tester（tester-cards）跑 F-004/F-006，Reviewer（reviewer-cards）复核；发现 server-only `give` 静默失败后，Lead 删除无意义 setup 并重跑 warnings=[]（见 `M4-TEAM-CARD-DEMO.md`）
- 客户端截图：
  - `run-client/screenshots/mcdev-1792257239553.png`（GUI 第一版）
  - `run-client/screenshots/mcdev-1792258312165.png`（Energy 5000/10000）
  - `run-client/screenshots/mcdev-1792258351039.png`（关闭再打开）
- 日志：
  - `final-server.log`：`Dev has made the advancement [Charger]`
  - GameTest：`Charger energy: 777/10000`

## 未完成 / 下一步

1. `/charger info` 玩家 raycast 路径（本轮客户端重连时 `Timed out`，未完成；命令 pos 路径已测）。
2. F-003 tooltip 视觉。
3. advancement toast 视觉。
4. F-006 客户端视觉已验证：热栏截图 `M4-F006-hotbar-full.png` / 放大 `M4-F006-hotbar.png` 可见绿色 6/13 条；`/item/mainhand` 返回 visible/width/color。
5. F-004 loot 已用服务端 `destroyBlock(pos,true)` + ItemEntity 收集做 E2E；仍不是客户端玩家采矿操作。
6. 收口后把 Charger 作为参考实现，接 reference-mod 沙箱。
