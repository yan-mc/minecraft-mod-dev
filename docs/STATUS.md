# 当前状态

更新：2026-10-18  
插件：`0.5.0` · 参考模组：Charger · GameTest：**12/12**

## 已完成

| 能力 | 结果 |
|---|---|
| 插件工具 | 14 个 `mcdev_*` 工具 |
| 构建 / GameTest | `mcdev_build` exit 0；`mcdev_gametest` 12/12 |
| 服务端探针 | 状态、命令、方块/NBT、recipe、loot、items、craft、world break、run dump |
| 客户端探针 | UI 树、槽位、点击、右键、截图、inventory hover、显式 connect、packet mine |
| 启动 | server/client/both；TitleScreen 后 `/connect`；失败最多 3 次重试 |
| 卡片 | F-001..F-007 全部 live，`warnings=[]` |
| 视觉证据 | tooltip、raycast、advancement toast、热栏能量条 |
| 团队 | Builder/Tester/Reviewer 流程；卡片 Tester/Reviewer 复核 |
| 发布 | GitHub Release [v0.5.0](https://github.com/yan-mc/minecraft-mod-dev/releases/tag/v0.5.0)，含插件 zip + probe jar |

## 关键数字

```text
mcdev_build    → exitCode=0
mcdev_gametest → exitCode=0, All 12 required tests passed
F-001..F-007   → ok=true, warnings=[]
注入 bug        → 5/5 检出并修复
```

## 已知缺口

1. 仅 Windows + MC 1.20.1 + Forge 47.4.x + Java 17 + DSH 0.2.1-rc.2。
2. 探针 Mixin 是 dev-only；生产 SRG 未支持。
3. 只在 `lab/forge-1.20.1-test` 实测；其他工程目录/构建差异需适配。
4. `mcdev_scenario` 已支持 recipe/loot/loot_break/loot_mine/craft/mine/client_mine/items/item_bar/comparator；`item_nbt` 仍未支持。
5. 客户端本地 block 可能短暂滞后；断言以服务端 `/world/block` 为准。
6. Agent Teams 是 DSH 实验特性，成员/任务状态可能不同步。
7. 正式 npm 包未发布；使用本地 link + GitHub release 资产。
8. quickPlay 已移除；若手动 runClient，请用 `/connect`，不要加 `--quickPlayMultiplayer`。

## 路线图

- reference-mod 沙箱：从 Charger 模板生成新模组；
- 更多 item/container 断言；
- 完整团队 demo 脚本/视频；
- 生产 SRG 探针（远期）；
- npm/插件目录分发（远期）。

## 相关文档

- [完整说明书](MANUAL.md)
- [安装](INSTALL.md)
- [工具](TOOLS.md)
- [团队](TEAM.md)
- [排错](TROUBLESHOOTING.md)
- [演示](DEMO.md)
- [参考模组报告](../lab/forge-1.20.1-test/REPORT.md)
