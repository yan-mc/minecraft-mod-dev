# 当前状态与路线图

更新：2026-10-18

## 已完成

| 里程碑 | 结果 |
|---|---|
| M1 Forge 构建 + GameTest | `gradlew build` / `runGameTestServer` 3/3，exit 0 |
| M2 服务端 probe | HTTP `/status` `/log/tail` `/command` `/world/block`；fake player；tick freeze/step；run dump |
| M2b 外部 jar/Mixin | dev-mapped jar + `MixinConfigs` 才能在外部 dev run 生效 |
| M2c run DB | snapshot JSON → SQLite（`runs`/`logs`） |
| M3 客户端 probe | UI 控件树、鼠标/槽位/shift-click、截图；容器 63 槽实测 |
| M3b 连接/容器 | 独立 `run-server`/`run-client`；39001/39002；箱子 shift-click 1→2 |
| 插件 v0.3.0 | 14 个 `mcdev_*` 工具；团队模式；`mcmod` preset |
| M4 Charger | GameTest **12/12**；5 个注入 bug 5/5 检出并修复 |
| M4 团队流程 | Builder/Tester 实现 F-006；Builder/Reviewer 修 bug 演练 |

## 关键数字

```text
mcdev_gametest → exit 0, All 12 required tests passed
mcdev_build    → exit 0
bug drill      → 1 failure → fix → 12/12
```

## 已知缺口

1. F-003 tooltip、F-005 raycast/toast、F-006 能量条均已视觉验证（截图见 `lab/forge-1.20.1-test/M4-*.png`）。
2. 探针 Mixin 是 dev-only（`remap=false` + 字面量 `tick`）；生产 SRG 未支持。
3. `mcdev_prepare`/`mcdev_scenario` 只在 `lab/forge-1.20.1-test` 实测过。
4. `mcdev_scenario` 已支持 setup/actions/screen_title/slot/block_entity_nbt/comparator/recipe/loot/loot_break/loot_mine/mine/client_mine/items/item_bar；`craft` action 未支持；F-004 用 `recipe_output` 断言，不模拟合成界面。
5. 生存挖掘已修复：`charger` 缺 `mineable/pickaxe` 标签导致玩家挖掘无掉落；F-007 用服务端在线玩家 survival mine 验证 drops，客户端 keyAttack 端点仍实验性。
6. Agent Teams 是实验性的，可能有成员/任务状态不同步。
7. 公开 beta 的安装包/版本化还未做；目前是本地 link 安装。
8. quickPlay 偶发 handshake 卡死：已加 `clean devJar`、`mcdev_prepare` 最多 3 次等待/重试、`attempts` 诊断；已演练“前两次 Disconnected、第三次 joined”，根因仍未完全定位。

## 路线图

### M5：可重复演示

- `mcdev_scenario` 卡片解析 + recipe/loot/item_bar assert 已完成；补 `craft` action。
- 把“注入 bug → 建任务 → builder/reviewer → 报告”做成可重复脚本。
- 录一段从空项目到可玩模组的 demo。

### 公开 beta

- 打包/版本化插件（npm 包或 GitHub Release）。
- 安装文档、工具文档、已知限制（本 `docs/`）。
- 一个 reference mod（Charger）+ 5～6 张卡作为模板。
- 收集外部项目反馈：Forge 版本、目录结构、Gradle 差异。

## 相关文档

- [安装](INSTALL.md)
- [工具](TOOLS.md)
- [团队](TEAM.md)
- [排错](TROUBLESHOOTING.md)
- [演示](DEMO.md)
- [M4 报告](../lab/forge-1.20.1-test/REPORT.md)
