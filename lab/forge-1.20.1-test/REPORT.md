# Charger 参考模组报告

日期：2026-10-18
工程：`lab/forge-1.20.1-test`
范围：7 张功能卡 + GameTest + 服务端/客户端检查 + 注入 bug。

## 总结

| 项 | 结果 |
|---|---|
| 构建 | `mcdev_build` exit 0 |
| GameTest | `mcdev_gametest` exit 0，**12/12** |
| 注入 bug | 5 个全部检出，5 个全部修复 |
| 静态审计 | 8 个必需资源，missing=0 |
| 客户端 | GUI/槽位/能量条/shift-click/关闭重开/tooltip/toast/生存挖掘 |
| 服务端 | 充能、NBT、comparator、recipe、loot、craft、item bar |
| 卡片 | F-001..F-007 live，warnings=[] |

## 卡片矩阵

| 卡 | 功能 | 静态 | GameTest | 服务端 | 客户端 | 状态 |
|---|---|---|---|---|---|---|
| F-001 | 充能方块 | ✅ 注册/模型/lang | ✅ 能量/比较器/NBT | ✅ 右键充能 | ✅ 交互 | **通过** |
| F-002 | 充能 GUI | ✅ Menu/Screen 注册 | ✅ shift-click 不复制（mock player） | ✅ 打开/关闭 | ✅ 截图/槽位/能量条/重开 | **通过** |
| F-003 | 充能物品 | ✅ 模型/lang | ✅ 能量 clamp | ✅ 空中右键 500→400 | ✅ tooltip 截图 `Energy: 5000/10000` | **通过** |
| F-004 | 配方/掉落 | ✅ recipe/loot JSON | ✅ recipe 结果 + loot 掉落 + craft | — | — | **通过** |
| F-005 | 命令/进度 | ✅ advancement JSON | ✅ `/charger info <pos>` | ✅ advancement 触发 | ✅ raycast + toast 截图 | **通过** |
| F-006 | 物品能量条 | ✅ 模型/lang | ✅ bar visible/width/color | — | ✅ 热栏截图绿色 6/13 条 | **通过**（团队实现） |
| F-007 | 生存挖掘 | ✅ mineable/pickaxe tag | ✅ client START_DESTROY_BLOCK | ✅ 服务端方块变 air | ✅ items charger×1 | **通过** |

## 注入 bug 矩阵

| Bug | 检测层 | 检出 | 修复 |
|---|---|---|---|
| B1 setEnergy NPE | GameTest | ✅ | ✅ |
| B2 缺 blockstate | 静态审计 | ✅ | ✅ |
| B3 recipe 错输出 | GameTest | ✅ | ✅ |
| B4 shift-click 复制 | GameTest（mock player） | ✅ | ✅ |
| B5 comparator 恒 0 | GameTest | ✅ | ✅ |

详见 `BUGS.md`。

## 证据

- GameTest：`All 12 required tests passed`
- F-006：builder-item + tester-item 团队实现，Lead 复核 12/12（见 [TEAM.md](../../docs/TEAM.md)）
- 团队修 bug：注入 `getBarWidth=14` 回归 → gametest 1 失败 → builder-fix 修复 → reviewer-fix 只读复核通过 → Lead 重跑 12/12（见 [TEAM.md](../../docs/TEAM.md)）
- 卡片执行：`mcdev_scenario card=cards/F-002.yml` 客户端 live，warnings=[]，wait+use+ui/title/slot 全过
- 卡片执行：`mcdev_scenario card=cards/F-001.yml` 客户端 live，warnings=[]，give.select+use+data get（5200）+comparator（analog=7）全过
- 比较器：`block` 步骤 + `/world/block` 返回 `analog=7`，assert eq 7 通过
- F-004 live：`recipe`×1、`loot` total=1、`loot_break` drops charger×1；`craft` crafted=true/result charger×1；warnings=[]
- F-004 client break：creative `/player/break` before=charger → after=air，server 端也是 air（无掉落）
- F-007 生存挖掘：补 `mineable/pickaxe` 标签后，客户端 `START_DESTROY_BLOCK` 包 → 服务端 block air，`/items` charger×1，warnings=[]
- F-006 live：`item` 返回 visible=true/width=6/color=65280，warnings=[]
- F-005 client raycast：`/charger info` → chat `Charger energy: 0/10000 at 0, 64, 0`；截图 [raycast.png](../../docs/assets/raycast.png)
- F-005 advancement toast：revoke + give charged_item → 截图 [advancement-toast.png](../../docs/assets/advancement-toast.png) 显示 `Advancement Made! Charger`
- F-003 tooltip：inventory hover `charged_item{Energy:5000}` → `Energy: 5000/10000`，截图 [tooltip.png](../../docs/assets/tooltip.png)
- 启动稳定性：`mcdev_prepare` 等 TitleScreen 后 `/connect`；连续 3 次 `kind=both` 均 attempt1 `joined=true`
- 团队卡片执行：Tester（tester-cards）跑 F-004/F-006，Reviewer（reviewer-cards）复核；发现 server-only `give` 静默失败后，Lead 删除无意义 setup 并重跑 warnings=[]
- GameTest 日志：`Charger energy: 777/10000`

## 后续

1. 把 Charger 作为参考实现，接 reference-mod 沙箱。
2. 扩展 item NBT / 容器交互断言。
