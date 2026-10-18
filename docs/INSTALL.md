# 安装与快速开始

插件版本：`0.5.0`  
适用：**Windows 10/11 x64 + Minecraft 1.20.1 + Forge 47.4.x + Java 17 + DSH 0.2.1-rc.2**

完整说明书见 [MANUAL.md](MANUAL.md)。

## 1. 环境

- JDK 17（实测 Temurin 17.0.19）
- DSH 0.2.1-rc.2（Desktop 版）
- Minecraft 1.20.1 + Forge 47.4.28
- Gradle 由仓库自带 wrapper 提供（8.8）

克隆仓库：

```powershell
git clone https://github.com/yan-mc/minecraft-mod-dev.git
cd minecraft-mod-dev
```

## 2. 安装 DSH 插件

### 方式 A：GitHub Release（推荐）

下载 [v0.5.0 release](https://github.com/yan-mc/minecraft-mod-dev/releases/tag/v0.5.0)：

- `minecraft-mod-dev-0.5.0.zip`
- `mcdev_probe-0.1.0-dev.jar`

解压后：

```text
plugin_manager install_bundle target=<解压目录>\minecraft-mod-dev
```

### 方式 B：源码 link

```text
plugin_manager install_bundle target=<仓库绝对路径>\plugin\minecraft-mod-dev
```

安装后应看到 **14 个 `mcdev_*` 工具**，插件版本 `0.5.0`，并挂载实验性 Agent Teams（团队模式默认开启）。

## 3. 构建探针 dev jar

```powershell
cd lab\mcdev-probe
.\gradlew.bat clean devJar
```

产出：

```text
build\libs\mcdev_probe-0.1.0-dev.jar
```

**必须使用 `*-dev.jar`**：

- 普通 `build` 的 reobf jar 在 dev 环境会 `NoSuchMethodError: Commands.m_82127_(String)`；
- `mcdev_install_probe` 会拒绝非 `*-dev.jar`。

## 4. 安装探针到 run 目录

```text
mcdev_install_probe
  probeJar=F:\...\lab\mcdev-probe\build\libs\mcdev_probe-0.1.0-dev.jar
  runDirs=["F:\...\lab\forge-1.20.1-test\run-server",
           "F:\...\lab\forge-1.20.1-test\run-client"]
```

工具会校验 manifest 的 `MixinConfigs: mcdev_probe.mixins.json`，再复制到 `<runDir>\mods\`。

## 5. 启动

测试工程：

- `run-server/`：专用服务端，端口 `25566`，probe `39001`
- `run-client/`：客户端，probe `39002`

```text
mcdev_prepare projectDir=F:\...\lab\forge-1.20.1-test kind=both
```

插件会：

1. 启动 server，等 probe ready；
2. 启动 client，等 probe ready；
3. 等客户端到 TitleScreen，调用客户端 `/connect` 显式连接；
4. 等玩家进服，返回 `joined=true`。

> 客户端**不使用 quickPlay**；不要共用 `run/` 目录，否则登录会超时。

## 6. 验证

```text
mcdev_status side=server
mcdev_status side=client
mcdev_screenshot
mcdev_build projectDir=F:\...\lab\forge-1.20.1-test
mcdev_gametest projectDir=F:\...\lab\forge-1.20.1-test
```

期望：

```text
mcdev_gametest → exitCode=0, All 12 required tests passed
```

## 7. 跑一张卡片

```text
mcdev_scenario card=F:\...\lab\forge-1.20.1-test\cards\F-002.yml
```

期望：

```json
{"ok":true,"warnings":[],"steps":[...]}
```

## 8. 停止

```text
mcdev_stop side=all
```

## 9. 下一步

- [完整说明书](MANUAL.md)
- [工具与卡片](TOOLS.md)
- [团队模式](TEAM.md)
- [排障](TROUBLESHOOTING.md)
- [演示](DEMO.md)
- [当前状态](STATUS.md)
