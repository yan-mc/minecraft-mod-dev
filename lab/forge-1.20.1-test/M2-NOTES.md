# M2 预检记录：Carpet: NeoForged 在 Forge dev 不可用

日期：2026-10-17
结论：**v1 不依赖 Carpet**；fake player / tick freeze 改由 probe 自带（ADR-009）。

## 尝试

1. 从 Modrinth 下载 `forge-carpet-1.20.1-1.0.8+v251027.jar`，放入 `run/mods`。
2. 准备 `run/eula.txt`、`run/server.properties`（`online-mode=false`、`enable-rcon=true`、`rcon.port=25575`、`rcon.password=mcdev`、flat 世界）。
3. `gradlew runServer`。

## 结果

Forge **47.4.28** 与 **47.3.0** 都在启动时失败，错误相同：

```
Mixin apply failed carpet.mixins.json:MinecraftServer_coreMixin -> net.minecraft.server.MinecraftServer:
InvalidMixinException @Shadow method m_129783_ ... was not located in the target class ...
Using refmap carpet.refmap.json
```

检查 jar：

- `META-INF/mods.toml`：`displayName="Carpet: NeoForged (Forge Backport)"`，
  `loaderVersion="[47,)"`，依赖 `forge [47,)` / `minecraft [1.20,1.20.1]`。
- `carpet.mixins.json`：`compatibilityLevel: JAVA_17`，`refmap: carpet.refmap.json`。
- `carpet.refmap.json`：**没有 `m_129783_` 的映射**。

## 根因

这个 1.20.1 jar 是 **NeoForge 的 Forge backport**，Mixin 里用的是 SRG 名
（`m_129783_`），但 refmap 没有为 Forge dev 的 official 名（`tickServer` 等）
生成映射。Forge 生产环境用 SRG 名，可能能跑；Forge dev 环境用 official 名，
所以 `@Shadow` 找不到目标方法。

## 决策

- 不用 Carpet；不降到某个"能跑"的 Forge 版本（47.3.0 也失败）。
- probe 自带：
  - `FakePlayerFactory` 造 Forge 假玩家；
  - `ServerLevel.tick` Mixin 做 freeze/step；
  - scarpet 用 scenario DSL 替代。
- 详见冻结文档 ADR-009。

## 影响

- `run/mods` 里的 Carpet 也会让 `runGameTestServer` 崩（同一个 mixin 错误）；
  已移到 `lab/carpet-disabled/`。
- 移除后 `gradlew build runGameTestServer` 恢复 **3/3 passed, exit 0**。
- M2 的 probe 工作量增加约 150–250 行 Java；换掉一个外部依赖，值得。

---

## Probe v0（M2 第一步）已跑通

工程：`lab/mcdev-probe`（独立 Forge mod，`mod_id=mcdev_probe`，47.4.28）。

已实现：
- `GET /status`
- `GET /log/tail?n=N`
- `POST /command`（`{"cmd":"..."}`，主线程执行）
- `GET /world/block?x=&y=&z=&dim=`
- `GET /screenshot`、`/ui/snapshot`、`/ui/click` 返回 501（客户端 probe 后续）
- `/mcdev status|freeze|step|fakeplayer spawn|use|attack`

实测（`gradlew runServer` + `Invoke-RestMethod`）：
- `/status` → `{"side":"server","tick":162,...,"frozen":false}`
- `/log/tail?n=3` → 3 条结构化日志
- `/world/block?x=0&y=64&z=0` → `minecraft:air`
- `/command {"cmd":"time set day"}` → result 1000
- `/command {"cmd":"mcdev fakeplayer spawn Steve"}` → result 1
- `/command {"cmd":"mcdev fakeplayer use Steve 0 64 0"}` → result 1
- `/command {"cmd":"mcdev freeze true"}` → `/status` 显示 `frozen:true`

**已知限制**：v0 的 `freeze` 只设置状态 + 改 gamerule，**没有真正取消 ServerLevel tick**（实测 tick 162→164）。真正的 freeze/step 需要 `ServerLevel.tick` Mixin，作为 M2b。

下一步：
1. 加 `ServerLevel.tick` Mixin（ForgeGradle + MixinGradle），做真正的 freeze/step。
2. 把 probe 的 `/command` 返回命令输出（不只 result code）。
3. 把一次 run 写入 SQLite（`runs/ticks/logs/events/blocks/assertions`）。

---

## M2b：真 freeze/step + 外部 dev jar（已完成）

### 外部探针的坑

- `build/libs/mcdev_probe-0.1.0.jar` 是 **reobf（SRG）** jar，放进 dev 的 `run/mods` 会：
  `NoSuchMethodError: Commands.m_82127_(String)`（应为 `Commands.literal`）。
- 手动组了一个 **dev-mapped jar**：`build/libs/mcdev_probe-0.1.0-dev.jar`
  - 内容 = `build/classes/java/main` + `build/resources/main` + `mcdev_probe.refmap.json`；
  - manifest 必须带 `MixinConfigs: mcdev_probe.mixins.json`（Carpet 也是这样）；
  - 否则 Mixin 配置不会被注册，日志里没有 `Mixing ServerLevelMixin`。
- 结论：**探针 jar 必须是 dev-mapped + MixinConfigs manifest**；产品里要专门出这个 jar，不能直接发 `build` 的 reobf jar。

### 真 freeze/step 实测（目标工程 = `forge-1.20.1-test` + 外部 probe）

`ServerLevelMixin` 已应用：
`Mixing ServerLevelMixin from mcdev_probe.mixins.json into net.minecraft.server.level.ServerLevel`

结果：

| 操作 | gameTime 变化 |
|---|---|
| freeze 后 3s | +2（命令延迟；基本冻结） |
| `step 20` 后 2s | **+20** |
| step 后再等 2s | **0**（保持冻结） |
| unfreeze 后 2s | +40（恢复 20 TPS） |

`/run/dump?n=5` 返回并落盘：
`run/.mcdev/runs/20261017-162648/snapshot-1792254424806.json`

fake player：
- `mcdev fakeplayer spawn Steve` → 日志 `spawned fake player Steve at BlockPos{x=0, y=60, z=0}`
- `mcdev fakeplayer use Steve 0 64 0` → 日志 `useOn BlockPos{x=0, y=64, z=0} -> PASS`

### 下一步

1. 在 probe 的 `build.gradle` 里加一个 `devJar` task，自动产出 dev-mapped + MixinConfigs 的 jar（不再手工组）。
2. 把 snapshot JSON 导入 SQLite，跑 `mcdev_sql` 的查询形态。
3. 进 M3：Forge 1.20.1 客户端 probe（UI 树、鼠标点击、截图）。

---

## M2c：devJar task + SQLite 导入（已完成）

### devJar task

`lab/mcdev-probe/build.gradle` 新增：

```groovy
tasks.register('devJar', Jar) {
    dependsOn tasks.named('classes')
    archiveClassifier = 'dev'
    from sourceSets.main.output
    from(layout.buildDirectory.file('tmp/compileJava/mcdev_probe.refmap.json')) {
        rename { 'mcdev_probe.refmap.json' }
    }
    manifest { attributes('MixinConfigs': 'mcdev_probe.mixins.json') }
}
tasks.named('build') { dependsOn tasks.named('devJar') }
```

产出 `build/libs/mcdev_probe-0.1.0-dev.jar`：
- 类名是 official（`Commands.literal`，不是 `m_82127_`）；
- manifest 有 `MixinConfigs: mcdev_probe.mixins.json`；
- 包含 `mcdev_probe.mixins.json` + `mcdev_probe.refmap.json`。

### SQLite 导入

`lab/tools/import_snapshot.py` 把 `/run/dump` 的 snapshot JSON 导入 SQLite：
- `runs(id, started_at, tick, game_time, frozen)`
- `logs(id, run_id, time, level, logger, thread, message, throwable)`

实测（snapshot `20261017-162648`）：

```
runs: [('20261017-162648', 1792254408428, 328, 336, 0)]
levels: [('INFO', 5)]
last 6:
  INFO useOn BlockPos{x=0, y=64, z=0} -> PASS
  INFO spawned fake player Steve at BlockPos{x=0, y=60, z=0}
  INFO frozen=false
  INFO step=20
  INFO frozen=true
```

DB 文件：`lab/run-m2.sqlite`。

### 已知限制

- Mixin 用 `remap=false` + 字面量 `tick`，是 **dev-only**；生产 SRG 环境需要另一套配置。
- `devJar` 是探针的发布形态；普通 `build` 的 reobf jar 只能用于生产，不能放进 dev 的 `run/mods`。
- SQLite 导入目前是 Python 原型；产品里由 DSH Host 插件的 `mcdev_sql` 承担。

### 下一步

- M2 收尾：把 `devJar` 安装/版本检查接进插件（`mcdev_doctor` 检查 probe 版本与 manifest）。
- M3：Forge 1.20.1 客户端 probe（UI 树、鼠标点击、截图）。
