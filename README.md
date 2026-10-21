# 原初 Primordial

**ProjectE:Primordial** 是一个围绕 ProjectE EMC 经济扩展的 Minecraft 模组。它把 EMC 接入自动化机器、资源转换、物品回收、装备能力和存储网络，让已经学会的资源可以参与更多自动化流程。

> English: Primordial extends ProjectE's EMC economy with machine automation, resource conversion, recovery, equipment, and storage-network integrations. The playable source line currently targets Minecraft 1.20.1 with Forge.

## 版本与分支

| 分支 | 目标版本 | 状态 |
| --- | --- | --- |
| `forge/1.20.1` | Minecraft 1.20.1 + Forge | 当前源码维护线 |
| `neoforge/1.21.1` | Minecraft 1.21.1 + NeoForge | 迁移目标线；当前尚未完成移植，不代表可游玩或可发布版本 |

NeoForge 1.21.1 正在按系统逐步移植；各功能会分别验证，当前不代表可游玩或可发布版本。

## 主要内容

- 使用配置器为机器、玩家物品栏和支持的装备安排物品、流体、气体、Forge 能源及其它资源的供给。
- 将 EMC 转换为若干联动模组的资源，并通过黑洞方块和护符回收资源价值。
- 提供黑洞装备、可配置目标的时间加速器，以及 AE2、Refined Storage 和 Beyond Dimensions 存储网络接口。
- 根据已安装的可选模组启用相应适配；具体行为取决于目标模组版本及其公开 API。

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

构建产物位于 `build/libs/`。当前 Gradle 工程配置面向 Forge 1.20.1；NeoForge 分支在迁移完成前不能用作 1.21.1 发布包。

## 许可

本项目的模组源码声明为 **All Rights Reserved（保留所有权利）**。公开仓库不代表授予复制、修改、再发布或分发整合包的许可；如需使用，请先取得作者授权。
