# 原初 Primordial

**ProjectE:Primordial** 是一个围绕 ProjectE EMC 经济扩展的 Minecraft 模组。它把 EMC 接入自动化机器、资源转换、物品回收、装备能力和存储网络，让已经学会的资源可以参与更多自动化流程。

> English: Primordial extends ProjectE's EMC economy with machine automation, resource conversion, recovery, equipment, and storage-network integrations. Separate source lines target Minecraft 1.20.1 with Forge and Minecraft 1.21.1 with NeoForge.

正式下载：[Forge 1.20.1 / 2.0.0](https://github.com/Herrscher-of-Voids/ProjectE-Primordial/releases/tag/v2.0.0-forge-1.20.1) · [NeoForge 1.21.1 / 2.0.3](https://github.com/Herrscher-of-Voids/ProjectE-Primordial/releases/tag/v2.0.3-neoforge-1.21.1)。安装所需前置见各发布说明。

## 版本与分支

| 分支 | 目标版本 | 状态 |
| --- | --- | --- |
| `forge/1.20.1` | Minecraft 1.20.1 + Forge | 原版源码维护线 |
| `neoforge/1.21.1` | Minecraft 1.21.1 + NeoForge | 2.0.3 修复 JEI 默认列表遗漏定价器；NeoForge 下限 21.1.119 |

### NeoForge 1.21.1 当前进度（2026-10-31）

已迁移 EMC 账本与 HUD、黑洞回收、资源知识和阶段、定价器、转化桌物品/流体/化学品/合成页、AE2/RS 2/Beyond Dimensions 网络、机器配置器与自动任务、独立资源和玩家补充、护符、实体生成器、加速器、黑洞材料/装备/武器、奇点与生存配方。完整范围、运行要求及验证限制见 [NeoForge 迁移说明](neoforge/README.md)。

2.0.3 修复定价器只有开启 JEI「Show Hidden Ingredients」才能找到的问题；通过附加物品注册加入默认列表，创造栏显示和服务端定价权限保持原样。保留 2.0.2 的 NeoForge 范围 `[21.1.119,21.2)`，与必需前置 ProjectE 1.1.0 的下限一致；其他模组自身的加载器要求仍适用。详见 [更新日志](neoforge/CHANGELOG.md)。

最近六项实测修复：补齐 33 项能力名称和说明及其他翻译（中英共 513 键）；修复黑洞/碎块及绊线回调的同步区块加载路径；修复 JEI“＋”转移和返回合成页；同步最新 1.20.1 定价器贴图；删除开发进度提示；恢复配置器邻接方块图标、六面布局并修复空面板、重叠与默认值。配置器网络协议升级为 **2**，联机时客户端和服务端须同时更新。

最终基础 GameTest **259/259**；实际 43 包开发类路径加隔离 Botania Revived **302/302**；正式构建与模型/贴图/语言/Mixin 检查通过。此前 AE2/RS 2/BD 与机器配置 write/read/verify 三个独立正常停服 JVM 各 **1/1**。2026-10-31 维护者完成游戏内试用，反馈基本无大问题，决定正式发布；后续按玩家反馈维护。

中文 Prism 测试实例已更新并备份旧包。星辉魔法（Astral Sorcery）、奥瑞科技（Oritech）和新增功能暂缓；两者保留禁用预留选项，不代表已兼容。Oritech 的 EMC/碰撞能匹配仍后置。Botania Revived 是隔离测试中的非官方移植，未自动加入实例；BD/PNC 旧 Botania API 联动被跳过，不代表第三方功能已复刻。

使用 JDK 21 在仓库根目录构建和运行基础测试：

```powershell
.\gradlew.bat -p neoforge build
.\gradlew.bat -p neoforge runGameTestServer
```

产物位于 `neoforge/build/libs/`。可选模组组合、跨进程验证及测试实例清单见 [迁移说明](neoforge/README.md) 和 [测试包记录](neoforge/testpack/README.md)。Forge 1.20.1 构建要求见下文。

## 主要内容

- 使用配置器为机器、玩家物品栏和支持的装备安排物品、流体、气体、Forge 能源及其它资源的供给。
- 将 EMC 转换为若干联动模组的资源，并通过黑洞方块和护符回收资源价值。
- 提供黑洞装备、可配置目标的时间加速器，以及 AE2、Refined Storage 和 Beyond Dimensions 存储网络接口。
- 根据已安装的可选模组启用相应适配；NeoForge 1.21.1 的 FTB Quests、AE2、Refined Storage、Beyond Dimensions 采用同版本族的范围型前置要求，API 不兼容时仅停用对应联动。

## 运行要求

- Minecraft 1.20.1
- Forge 47.x
- Java 17
- 必需前置：ProjectE 1.0.1 或更新版本、Curios API 5.9.1 或更新版本

其它联动模组为可选项。依赖的实际可用版本以对应整合包和构建配置为准。

## 从源码构建

在仓库根目录使用 JDK 17：

```powershell
.\gradlew.bat build
```

运行测试：

```powershell
.\gradlew.bat test
```

构建产物位于 `build/libs/`。当前根 Gradle 工程配置面向 Forge 1.20.1；NeoForge 1.21.1 的独立构建命令见 `neoforge/README.md`，迁移完成前不能用作发布包。

## 许可

本项目的模组源码声明为 **All Rights Reserved（保留所有权利）**。公开仓库不代表授予复制、修改、再发布或分发整合包的许可；如需使用，请先取得作者授权。
