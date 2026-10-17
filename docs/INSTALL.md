# 安装与快速开始

本仓库是 **DeepSeek Harness（DSH）插件 + Minecraft 1.20.1 Forge 探针** 的研究原型。
当前只支持：**Windows 10/11 x64 + Minecraft 1.20.1 + Forge 47.4.x + Java 17 + DSH 0.2.1-rc.2**。

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

在 DSH 会话里执行：

```text
plugin_manager install_bundle target=<仓库绝对路径>\plugin\minecraft-mod-dev
```

安装后应看到 14 个 `mcdev_*` 工具，插件版本 `0.3.0`。

> 本地 link 安装不需要构建、不需要 npm install。插件会同时挂载实验性 Agent Teams（团队模式默认开启）。

## 3. 构建探针 dev jar

```powershell
cd lab\mcdev-probe
.\gradlew.bat devJar
```

产出：

```text
build\libs\mcdev_probe-0.1.0-dev.jar
```

**必须用 `*-dev.jar`**：
普通 `build` 的 reobf jar 在 dev 环境会 `NoSuchMethodError: Commands.m_82127_(String)`；
`mcdev_install_probe` 会拒绝非 `*-dev.jar`。

## 4. 安装探针到 run 目录

示例（测试工程）：

```text
mcdev_install_probe
  probeJar=F:\...\lab\mcdev-probe\build\libs\mcdev_probe-0.1.0-dev.jar
  runDirs=["F:\...\lab\forge-1.20.1-test\run-server",
           "F:\...\lab\forge-1.20.1-test\run-client"]
```

工具会校验 manifest 里的 `MixinConfigs: mcdev_probe.mixins.json`，再复制到 `<runDir>\mods\`。

## 5. 启动

测试工程已配置好：

- `run-server/`：专用服务端，端口 `25566`，probe `39001`
- `run-client/`：客户端，probe `39002`，`--quickPlayMultiplayer 127.0.0.1:25566`

一条命令：

```text
mcdev_prepare projectDir=F:\...\lab\forge-1.20.1-test kind=both
```

会依次启动 server、等待 probe ready、再启动 client；返回 pid/port/logPath。

手动启动也可以：

```powershell
cd lab\forge-1.20.1-test
.\gradlew.bat runServer
.\gradlew.bat runClient
```

> **不要共用 `run/` 目录**：server 和 client 同目录会登录 `Timed out`。
> 测试工程的 `build.gradle` 已把 client/server 分到 `run-client`/`run-server`。

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

## 7. 停止

```text
mcdev_stop side=all
```

## 8. 下一步

- 工具完整列表：[TOOLS.md](TOOLS.md)
- 团队分工：[TEAM.md](TEAM.md)
- 常见问题：[TROUBLESHOOTING.md](TROUBLESHOOTING.md)
- 当前状态与路线图：[STATUS.md](STATUS.md)
- 10 分钟演示：[DEMO.md](DEMO.md)
