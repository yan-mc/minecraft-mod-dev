# M1 实验报告：Forge 1.20.1 环境 + GameTest

日期：2026-10-17
工程：`F:\Deepseek_harness\MC模组开发\lab\forge-1.20.1-test`
目标：验证 1.20.1 Forge 的构建与 `runGameTestServer` 主判据。

## 环境

- Windows 11，JDK 17.0.19（Temurin），`JAVA_HOME` 已设。
- Forge 47.4.28 MDK（1.20.1 线最新）。
- Gradle wrapper 8.8；ForgeGradle `[6.0,6.2)`。
- 网络：Forge Maven / Gradle / Maven Central / Modrinth 均可达。

## 工程改动

1. 解压 MDK 到本目录。
2. 保留 MDK 自带 `ExampleMod` / `Config`。
3. 重写 `ExampleGameTests`：3 个 GameTest，模板 `empty`。
4. 新增 `tools/make_structure.py`，生成 5x5x5 石质地板结构：
   `src/main/resources/data/examplemod/structures/empty.nbt`。
5. 保留 MDK 自带 `gameTestServer` run config（`forge.enabledGameTestNamespaces=examplemod`）。

## 结果

### 构建

```text
gradlew build
BUILD SUCCESSFUL in 1m 15s
7 actionable tasks: 7 executed
```

仅 2 条 `ResourceLocation(String)` 弃用警告，无错误。

### GameTest

```text
gradlew runGameTestServer
Running test batch 'defaultBatch:1' (3 tests)...
3 tests are now running!
========= 3 GAME TESTS COMPLETE ======================
All 3 required tests passed :)
BUILD SUCCESSFUL in 39s
EXIT=0
```

证据：`build-m1.log`、`gametest-m1d.log`。

## 踩坑记录

1. **结构 NBT 的 `palette` 必须是 LIST（tag 9）。**
   写成 COMPOUND 时，服务端报 `ReportedException: Loading NBT data` + `EOFException`，
   然后回退找 `gameteststructures/empty.snbt`，最终 crash。
2. **GameTest 的 structure block 占 relative (0,0,0)。**
   模板从 (1,1,1) 开始；地板在 relative y=1。
   5x5 模板在 5x5 测试区里会被裁到 4x4，所以远端断言用 (4,1,4)，不是 (5,1,5)。
3. **退出码语义已实测。**
   2 个 required 失败时 Gradle 进程 exit 2；全过 exit 0。
   这正是后续 `mcdev_gametest` 的判据。

## 结论

M1 通过。`runGameTestServer` 可以作为 v1 的第一层硬判据：
- 构建可无人值守；
- 测试可无人值守；
- 退出码可直接判定；
- 日志足够定位结构/断言失败。

## 下一步（M2）

1. 下载 Carpet: NeoForged 1.20.1，放入 `run/mods`，确认服务端加载。
2. 起 `runServer`（eula/online-mode=false），用 RCON 或 stdin 执行 `/player`、`/tick freeze`。
3. 写最小服务端 probe（`/status`、`/log/tail`、`/command`、`/world/block`）。
4. 把一次 run 写入 SQLite，跑通 `mcdev_sql` 的查询形态。
