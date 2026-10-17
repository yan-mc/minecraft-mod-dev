# 常见问题

## 1. `NoSuchMethodError: Commands.m_82127_(String)`

用了普通 `build` 的 reobf jar 进 dev `run/mods`。

修复：用 `gradlew devJar` 产出的 `mcdev_probe-0.1.0-dev.jar`，
并用 `mcdev_install_probe` 校验后再放。

## 2. Mixin 没生效（日志没有 `Mixing ServerLevelMixin`）

dev jar 的 `META-INF/MANIFEST.MF` 缺少：

```text
MixinConfigs: mcdev_probe.mixins.json
```

`mcdev_install_probe` 会检查这一行；普通 jar 会被拒绝。

## 3. `BindException: Address already in use: bind`

server 和 client 都想用 39001。

- server probe：`39001`
- client probe：`39002`

`McdevProbe` 已按 `Dist` 拆分；客户端用 `mcdev.probe.client.port` 覆盖。
检查是否还有旧 Java 进程占端口：`Get-NetTCPConnection -LocalPort 39001,39002`。

## 4. 客户端登录 `Timed out`

最常见原因：server 和 client 共用同一个 `run/` 目录（日志/文件锁）。

修复：

- 给 server/client 配独立目录，例如 `run-server/`、`run-client/`；
- 等待 server `Done (...)` 后再启动 client；
- 客户端用 `--quickPlayMultiplayer 127.0.0.1:25566`；
- 重启时确认旧 client/server 进程已退出。

### quickPlay 偶发 handshake 卡死

症状：

- client `Starting new vanilla impl connection` 后没有 modded handshake；
- server 已发 22 个 handshake 包，30 秒后 `lost connection: Disconnected`；
- client Render thread 正常 tick，Netty 线程 selector 空闲。

已做的稳定性处理：

1. 改 probe 后执行 `.\gradlew.bat clean devJar`，再 `mcdev_install_probe`；
2. 用 `mcdev_prepare kind=client|both`，默认等待进服；卡 ConnectScreen/Disconnected 会自动重试一次；
3. 卡片里 use 前后加 `wait`，避免 UI 切换竞态。

根因尚未完全定位（疑与 quickPlay 启动时机/增量 devJar 有关），但重试 + clean 构建可稳定跑通。

## 5. `mcdev_prepare` 超时

看它返回的 `logPath`：

```text
<projectDir>/.mcdev/launch-<kind>-<ts>.log
```

常见：

- Gradle 还在下载依赖；
- 端口被占；
- `runClient` 没有配置 quickPlay；
- 客户端卡在 `ConnectScreen`：`mcdev_prepare` 会等 `joined` 并重试一次；仍失败时看 client `logs/latest.log` 的 handshake。

## 6. `mcdev_install_probe` 拒绝 jar

- 文件名不是 `*-dev.jar`；
- manifest 没有 `MixinConfigs`；
- 路径不存在/不是文件。

## 7. `mcdev_scenario` assert 失败

检查 `path` 是否带 `data.` 前缀：

```json
{"type":"status","side":"server"},
{"type":"assert","path":"data.version","op":"eq","value":"1.20.1"}
```

`assert` 默认对最近一个数据步骤断言；`wait` 不产生数据。
失败时看 `failedAt` 和 `steps[failedAt].error`。

## 8. GameTest 结构/偏移问题

`empty.nbt` 模板的结构方块占相对 `(0,0,0)`，模板内容从 `(1,1,1)` 开始；
5×5 模板在 5×5 测试区会被裁到相对 1..4。
断言坐标要按这个偏移写。

## 9. 客户端截图 `exists:false`

`Screenshot.grab` 异步落盘；probe 已改为轮询最多 2s。
如果仍 false，检查 `run-client/screenshots/` 权限/磁盘。

## 10. 端口/令牌速查

| 项 | 值 |
|---|---|
| server probe | `127.0.0.1:39001` |
| client probe | `127.0.0.1:39002` |
| token | `mcdev` |
| 测试服端口 | `25566` |
