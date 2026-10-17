# M4 REPORT：Charger 测试模组

日期：2026-10-18
工程：`lab/forge-1.20.1-test`
范围：5 张功能卡 + 5 类注入 bug + GameTest/服务端/客户端检查。

## 总结

| 项 | 结果 |
|---|---|
| 构建 | `mcdev_build` exit 0 |
| GameTest | `mcdev_gametest` exit 0，**11/11** |
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

- GameTest：`All 10 required tests passed`
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
4. 收口后把 Charger 作为参考实现，接 reference-mod 沙箱。
