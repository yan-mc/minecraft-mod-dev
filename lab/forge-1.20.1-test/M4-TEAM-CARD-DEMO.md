# M4 团队卡片执行演示：F-004 + F-006

日期：2026-10-18
目标：让 Tester 用 `mcdev_scenario card=...` 跑卡片，Reviewer 只读复核，Lead 收口。

## 1. 任务

| 任务 | 角色 | 内容 |
|---|---|---|
| task-6 | Tester（tester-cards） | `mcdev_prepare server` → 跑 F-004/F-006 卡片 → `mcdev_stop` |
| task-7 | Reviewer（reviewer-cards） | 只读复核 task-6 结果、卡片/SPEC、git 状态 |

## 2. Tester 结果（task-6）

```text
mcdev_prepare server → ready=true, port=39001
mcdev_scenario card=F-004 → ok=true, warnings=[]
  recipe: found=true, item=examplemod:charger, count=1
  loot:   total=1, drops=[charger×1]
mcdev_scenario card=F-006 → ok=true, warnings=[]
  item:   visible=true, width=6, color=65280
mcdev_stop server → stopped=true
```

## 3. Reviewer 发现（task-7）

Reviewer 结论：**通过**（卡片 assert 层面），但提出 3 个非阻塞问题：

1. **setup give 静默失败**：server-only 没玩家 Dev，4 条 `give Dev ...` 全部
   `No player was found`；command 步骤不对 result 断言，所以卡片仍 ok=true。
2. **F-006 客户端视觉未覆盖**：`item_bar` 是服务端虚拟 ItemStack 直接调
   `isBarVisible/getBarWidth/getBarColor`，不是客户端物品栏截图证据。
   F-004 loot 也是 `Block.getDrops` 数据包级校验，不是玩家破坏 E2E。
3. **卡片 evidence 是预置文档**：不能当本次运行的独立证据；Reviewer 用
   task-6 JSON + 启动/停止日志时间线 + 静态 JSON 交叉验证。

## 4. Lead 修正

- 删除 F-004/F-006 server-only 卡片里没有意义的 `give` setup：
  - F-004 的 recipe/loot 走注册表/数据包，不依赖玩家背包；
  - F-006 的 item_bar 用虚拟 ItemStack，不依赖 give。
- 重跑：

```text
F-004 → ok=true, warnings=[], 6 steps（无 setup）
F-006 → ok=true, warnings=[], 4 steps（无 setup）
```

不再出现 `No player was found`，也不再被 `ok=true` 掩盖。

## 5. 保留的限制

- F-006 客户端视觉已补验：`kind=both` + 热栏截图 `M4-F006-hotbar-full.png` / 放大 `M4-F006-hotbar.png` 可见绿色 6/13 条；`/item/mainhand` 返回 visible/width/color。
- F-004 loot 不是玩家真实破坏 E2E；recipe assert 不校验 shaped 类型（静态 JSON 补足）。
- 卡片 `ui.screenshot` 字段仍是文档，不自动执行截图。

## 6. 结论

- 团队“Tester 跑卡 + Reviewer 复核 + Lead 修正”闭环跑通。
- Reviewer 的发现直接导致卡片修正，而不是只写“通过”。
- 下一步：F-004 用真实玩家破坏补 E2E；`ui.screenshot` 做成自动执行。
