# 发布说明

## 版本号

- 插件版本在 `plugin/minecraft-mod-dev/package.json`；
- 文档/仓库里程碑版本用 git tag `vX.Y.Z`；
- 当前：插件 `0.5.1`，tag `v0.5.1`，标记为 **pre-release**（公开 beta）。

## 发布前检查

```text
mcdev_build    projectDir=lab/forge-1.20.1-test   → exit 0
mcdev_gametest projectDir=lab/forge-1.20.1-test   → All 12 required tests passed
git status                                        → clean
```

## 打 tag

```powershell
git tag -a v0.5.1 -m "v0.5.1 public beta: craft, explicit connect, client packet mine, demo"
git push origin v0.5.1
```

## GitHub Release

用 GitHub API 建 release（token 从本地 git 凭据取，不落盘）：

```text
POST /repos/yan-mc/minecraft-mod-dev/releases
{
  "tag_name": "v0.5.1",
  "name": "v0.5.1 — public beta",
  "prerelease": true,
  "body": "..."
}
```

Release notes 应包含：

- 14 个工具列表；
- `mcdev_scenario` YAML 卡片 + recipe/loot/loot_break/craft/mine/client_mine/items/item_bar；
- **craft action**：`/craft` 检查/消耗在线玩家背包并产出 result；
- **quickPlay 根因修复**：移除 quickPlay，TitleScreen 后 `/connect` 显式连接；3/3 attempt1 joined；
- F-007 客户端 `START_DESTROY_BLOCK` 包 + 服务端权威；
- F-001..F-007 live；GameTest 12/12；
- F-003 tooltip、F-005 raycast/toast、F-006 能量条截图；
- `docs/DEMO.md` 演示；
- 安装入口 `docs/INSTALL.md`；
- 已知限制（Windows + 1.20.1 Forge only、dev-only Mixin、实验 Agent Teams）。

## Release 附件

```powershell
dist/minecraft-mod-dev-0.5.1.zip
dist/mcdev_probe-0.1.0-dev.jar
```

- 插件 zip：`plugin/minecraft-mod-dev/` 目录；
- 探针 jar：`lab/mcdev-probe/build/libs/mcdev_probe-0.1.0-dev.jar`（dev-mapped，仅 dev run 可用）。

上传：

```text
POST /repos/yan-mc/minecraft-mod-dev/releases/{id}/assets?name=<file>
Content-Type: application/octet-stream
```

## 安装方式（beta）

### 从 GitHub Release

1. 下载 `minecraft-mod-dev-<version>.zip` 并解压；
2. `plugin_manager install_bundle target=<解压目录>/minecraft-mod-dev`；
3. 下载 `mcdev_probe-0.1.0-dev.jar`，放入 runDir/mods（或 `mcdev_install_probe`）。

### 从源码

```text
plugin_manager install_bundle target=<repo>\plugin\minecraft-mod-dev
```

探针 devJar 用本机 `gradlew clean devJar` 构建。

## 后续计划

- npm 包 / DSH 插件目录包；
- release asset 带完整 reference mod + cards；
- 版本兼容检查（DSH/Forge/Java）。
