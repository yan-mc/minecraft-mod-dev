# Charger TESTPLAN

| 卡 | 功能 | 静态 | GameTest | 服务端 | 客户端 | 证据 |
|---|---|---|---|---|---|---|
| F-001 | 充能方块 | 注册/模型/语言 | 能量转移/比较器/NBT | `/charger info` | 比较器视觉（可选） | run_id + 断言 |
| F-002 | 充能 GUI | 菜单注册 | — | 打开菜单 | 截图 + 槽位 + shift-click | 截图 + db |
| F-003 | 充能物品 | 模型/贴图/语言 | NBT/tooltip 数据 | 右键空中 | tooltip 文本 | run_id + 截图 |
| F-004 | 配方/掉落 | recipe/loot JSON | 合成/破坏 | — | — | 断言 + 审计 |
| F-005 | 命令/进度 | advancement JSON | 命令执行 | `/charger info` | — | 日志 + 断言 |

## 执行顺序

1. F-001 静态 + GameTest
2. F-003 静态 + GameTest
3. F-004 静态 + GameTest
4. F-002 客户端
5. F-005 命令/进度
6. 注入 5 个 bug，按卡回归
7. `REPORT.md` 矩阵

## 卡片文件

`cards/F-001.yml` … `cards/F-005.yml`。每张卡至少包含：
- `id` / `title` / `spec`
- `tiers`（static/gametest/server_scenario/client_ui）
- `setup` / `actions` / `assert`
- `ui`（仅客户端卡）
- `evidence` / `status`
