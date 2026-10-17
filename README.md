# minecraft-mod-dev

DeepSeek Harness（DSH）插件 + Minecraft 1.20.1 Forge 探针：让 AI 团队写模组、跑 GameTest、开真实客户端、点 UI、截图、读日志、修 bug。

> 这是研究/原型快照，不是生产级产品。当前只支持 **Windows + Minecraft 1.20.1 + Forge 47.4.x + Java 17**。

## 当前状态

| 里程碑 | 状态 |
|---|---|
| M1 Forge 构建 + GameTest | ✅ `gradlew build` / `runGameTestServer` 3/3，exit 0 |
| M2 服务端 probe | ✅ HTTP `/status` `/log/tail` `/command` `/world/block`；fake player；tick freeze/step；run dump |
| M3 客户端 probe | ✅ UI 树、鼠标/槽位/shift-click、截图；容器 GUI 实测 |
| 插件 v0.2.0 | ✅ 9 个 `mcdev_*` 工具；团队模式（Agent Teams）默认 |
| M4 Charger 测试模组 | 🚧 5 张功能卡 + 5 类注入 bug 的计划已建，实现进行中 |

## 仓库结构

```
plugin/minecraft-mod-dev/     DSH 插件（Host 工具 + mcmod preset + Agent Teams 挂载）
lab/forge-1.20.1-test/        Forge 1.20.1 测试工程、M1/M2 报告、M4 SPEC/TESTPLAN/cards
lab/mcdev-probe/              服务端/客户端探针 mod（Forge 1.20.1）+ M3 记录
方案冻结-v1.1-...md           冻结的范围/契约/团队方案/路线图
MC模组开发模式-方案v2-...md   1.20.1 Forge 方案
MC模组开发模式-可行性与方案.md 通用调研
```

## 快速开始

### 1. 安装 DSH 插件

```text
plugin_manager install_bundle target=<repo>/plugin/minecraft-mod-dev
```

插件会注册 9 个 `mcdev_*` 工具，并挂载 Agent Teams、声明 `mcmod` 模式。

### 2. 构建探针 dev jar

```text
cd lab/mcdev-probe
.\gradlew.bat devJar
```

产出 `build/libs/mcdev_probe-0.1.0-dev.jar`。**必须用这个 dev-mapped jar**：
普通 `build` 的 reobf jar 在 dev 环境会 `NoSuchMethodError`；且 jar 的 manifest 必须带 `MixinConfigs: mcdev_probe.mixins.json`。

### 3. 启动服务端 / 客户端

```text
# 服务端（run-server，端口 25566；probe 39001）
cd lab/mcdev-probe
.\gradlew.bat runServer

# 客户端（run-client，probe 39002；自动连 127.0.0.1:25566）
.\gradlew.bat runClient
```

### 4. 调用工具

```text
mcdev_status side=server
mcdev_command side=server cmd="time set day"
mcdev_build projectDir=<repo>/lab/forge-1.20.1-test
mcdev_gametest projectDir=<repo>/lab/forge-1.20.1-test
mcdev_screenshot
mcdev_ui
mcdev_click slot=0 button=0
mcdev_use x=0 y=64 z=0 face=up
```

## 关键限制

- 只支持 MC 1.20.1 + Forge 47.4.x + Java 17 + Windows。
- 探针是 **dev-only**：`ServerLevel.tick` Mixin 使用 `remap=false` + 字面量 `tick`；生产 SRG 环境需要另一套配置。
- 团队模式依赖 DSH **实验性 Agent Teams**；不稳定时回退传统 subagent。
- 客户端/服务端必须使用独立 `run-server/` 与 `run-client/`，否则登录会超时。
- 不是通用 MC agent，不做 WASD 实时操作，不替代主观验收。

## 文档

- [方案冻结 v1.1](方案冻结-v1.1-1.20.1Forge-Windows.md)：范围、团队编制、工具契约、路线图。
- [v2 方案](MC模组开发模式-方案v2-1.20.1Forge.md)：1.20.1 Forge 设计。
- [通用调研](MC模组开发模式-可行性与方案.md)：先例与可行性。
- [M1 报告](lab/forge-1.20.1-test/M1-REPORT.md)、[M2 记录](lab/forge-1.20.1-test/M2-NOTES.md)、[M3 记录](lab/mcdev-probe/M3-NOTES.md)。
- [M4 计划](lab/forge-1.20.1-test/M4-PLAN.md)、[SPEC](lab/forge-1.20.1-test/SPEC.md)、[TESTPLAN](lab/forge-1.20.1-test/TESTPLAN.md)。

## License

Original code and docs: MIT (see [LICENSE](LICENSE)).
Minecraft, Forge, Gradle wrapper and other third-party components keep their own licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). No Minecraft assets are redistributed.
