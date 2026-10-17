# 发布说明

## 版本号

- 插件版本在 `plugin/minecraft-mod-dev/package.json`；
- 文档/仓库里程碑版本用 git tag `vX.Y.Z`；
- 当前：插件 `0.3.7`，tag `v0.3.7`，标记为 **pre-release**（公开 beta）。

## 发布前检查

```text
mcdev_build    projectDir=lab/forge-1.20.1-test   → exit 0
mcdev_gametest projectDir=lab/forge-1.20.1-test   → All 12 required tests passed
git status                                        → clean
```

## 打 tag

```powershell
git tag -a v0.3.7 -m "v0.3.7 public beta: client join recovery, card YAML, comparator"
git push origin v0.3.7
```

## GitHub Release

用 GitHub API 建 release（token 从本地 git 凭据取，不落盘）：

```text
POST /repos/yan-mc/minecraft-mod-dev/releases
{
  "tag_name": "v0.3.7",
  "name": "v0.3.7 — public beta",
  "prerelease": true,
  "body": "..."
}
```

Release notes 应包含：

- 14 个工具列表；
- `mcdev_scenario` YAML 卡片 + F-001/F-002 live；
- `mcdev_prepare` 最多 3 次等待/重试，恢复演练 attempt3 joined；
- Charger 12/12、5/5 bug；
- 安装入口 `docs/INSTALL.md`；
- 已知限制（Windows + 1.20.1 Forge only、dev-only Mixin、实验 Agent Teams）。

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
