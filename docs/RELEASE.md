# 发布说明

## 版本号

- 插件版本在 `plugin/minecraft-mod-dev/package.json`；
- 文档/仓库里程碑版本用 git tag `vX.Y.Z`；
- 当前：插件 `0.5.0`，tag `v0.5.0`，标记为 **pre-release**（公开 beta）。

## 发布前检查

```text
mcdev_build    projectDir=lab/forge-1.20.1-test   → exit 0
mcdev_gametest projectDir=lab/forge-1.20.1-test   → All 12 required tests passed
git status                                        → clean
```

## 打 tag

```powershell
git tag -a v0.5.0 -m "v0.5.0 public beta: craft, explicit connect, client packet mine, demo"
git push origin v0.5.0
```

## GitHub Release

用 GitHub API 建 release（token 从本地 git 凭据取，不落盘）：

```text
POST /repos/yan-mc/minecraft-mod-dev/releases
{
  "tag_name": "v0.5.0",
  "name": "v0.5.0 — public beta",
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
dist/minecraft-mod-dev-0.5.0.zip
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

当前是**本地 link 安装**：

```text
plugin_manager install_bundle target=<repo>\plugin\minecraft-mod-dev
```

从 GitHub Release 安装时：

1. 下载源码 zip 并解压；
2. 用解压目录里的 `plugin/minecraft-mod-dev` 作为 target；
3. 探针 devJar 仍需本机 `gradlew devJar` 构建。

后续正式版计划：

- npm 包 / DSH 插件目录包；
- release asset 带 `mcdev-probe` devJar；
- 版本兼容检查（DSH/Forge/Java）。
