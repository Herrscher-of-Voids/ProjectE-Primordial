# 1.21.1 测试实例模组清单

> 2026-10-31 六项修复：用户六项实测反馈修复：补齐33项能力中英文名称/说明及其他缺失翻译（共513键）；同步1.20.1定价器贴图；移除开发进度物品提示；修复JEI加号的界面判断和返回页签；恢复配置器邻接方块图标、六面布局、分页、首次FE面板与默认值，修复隐藏样本槽重叠和旋转错绑。黑洞/碎块跳过隐式加载区块的物理更新，地形及绊线/钩/支撑回调检查已加载路径，范围、收费和预算不变。基础259/259，43包开发类路径加隔离Revived 302/302，正式build及资源检查通过。新版网络协议2，客户端与服务端同时更新。 已确认Prism实例未运行后更新，并备份旧包。 未操作桌面，界面及实际帧率待用户复测；星辉、奥瑞和新增功能暂缓。

> 前一轮后台验证记录：同43包文件的开发服务端类路径加隔离Botania Revived通过299/299项原初测试，基础256/256；三网及机器配置独立JVM重启write/read/verify各1/1。中文Prism客户端尚未运行，完整整包与长时间性能验收仍未完成。下文历史记录的“未启动专服/功能待迁移”不再代表最新后台状态。Revived没有自动安装到实例，官方Botania缺席事实不变；BD/PNC旧Botania联动被跳过。

已部署本轮正式构建：1,119,128 字节，SHA-256 `6C235F50DD7A223532018D067728A36B986E1975A7BAE281D5DFAE29968959B5`，源目标一致。旧 JAR 与清单备份至实例 `migration-backups/20261031-015737/`；仍为43个JAR、`lang:zh_cn`。用户已明确当前不方便占用桌面，保留已验证版本，未启动客户端。100%最终验收保持开放。


2026-10-24 按 PrismLauncher 的 `原初 test`（Forge 1.20.1，46 个 JAR）核对，配置到 `ProjectE-Primordial-1.21.1-Test`（NeoForge 21.1.264）。补入 Spear of Void 后，当前为 43 个顶层 JAR：原清单中 42 个有对应实现，另加 Beyond Dimensions 必需的 Compixel 前置。

完整文件名、来源发行页、哈希、46 项逐项映射、备份位置及静态依赖检查见 [mod-migration-2026-10-24.json](mod-migration-2026-10-24.json)。实例根目录另有同份记录，`testpack-manifest.json` 为实际安装文件哈希清单。

- 初次新增 33 个包、升级 5 个包，后续再补入 Spear of Void；操作前的 9 个包均保存在实例 `migration-backups/20261024-234354/`。
- 未找到原项目对应发行：Blood Magic、Botania、Construction Wand；这些项目有其他维护者的移植/重制分支，尚未替换。
- 用户确认 Platform Builder（`platformbuilder`）是其自制模组，没有 1.21.1 版本。
- Spear of Void（`sov`）已从本机 `FTB StoneBlock 4` 实例补入 `Spear_of_Void-neoforge-1.21.1-1.3.0fix4.jar`，作者与旧版相同，源文件和目标 SHA-256 一致。其他两个实例也有同哈希副本；具体来源见 JSON。
- Goety 3.2.1 官方包有三个未展开的版本占位符。安装文件 `goety-3.2.1-primordial-metadatafix.jar` 只按作者 `gradle.properties` 修补 `META-INF/neoforge.mods.toml` 的三个声明；逐条比对确认其他 ZIP 条目内容不变。原包和补丁记录保存在备份目录。游戏兼容性仍待实际加载验证。
- Create 的 Flywheel/Ponder、JEI 的 MezzConfig 等已内置，不重复安装。Compixel 使用内置 Kotlin 版本。
- 安装和 SHA-256 校验完成；静态依赖检查通过。尚未启动这套 43 包实例的客户端或专用服务器，不能据此宣称整包运行验收通过。实例语言为 `zh_cn`，游戏内核验须先约定桌面可用时间。
- 2026-10-25 更新 Primordial 开发包：AE2 原生物品网格接线、归属保存和旧库存引用失效已补齐，开发环境有 AE2 组合 181/181、无 AE2 组合 178/178 GameTest 通过。正式构建已复制到实例，原包和清单备份在 `migration-backups/20261025-002212/`；未启动这套 43 包实例，不代表整包运行通过。
- 2026-10-25 后续更新：AE2 流体目录、精确买卖、模拟只读、同账户独立流体预算及生命周期测试已补齐；开发环境有 AE2 185/185、无 AE2 179/179 与正式构建通过。新包 392658 字节，SHA-256 `AC324E261C3DDDBD1D2DE90B3A76001ED4B2A48158A300D1F1415C0282AAE792`，源目标及清单一致；上一包与清单备份在 `migration-backups/20261025-004204/`。
- 2026-10-25 Beyond Dimensions 更新：移植 1.20.1 虚拟物品/流体目录到 0.8.2，限定相邻且获 owner/manager/member 授权的网络；真实库存优先、只购买同键缺额，模拟只读，EMC 与 AE2/RS 共用账户级每 tick 额度，虚拟库存不写入 BD 存档。撤权、网络改绑、接口拆除均即时失效。GameTest 覆盖授权隔离、精确物品/流体扣款、真实库存优先、模拟、非持久化和生命周期；有 BD + AE2 + RS 2 + Mekanism + FTB Quests + JEI 192/192，无 BD 保留同组合 192/192，正式构建通过。新包 422057 字节，SHA-256 `15B9AC3264CEBFFF616F34CC77FDC3526F18BE04959C2DEBC4183B1A3B9672E6`，已备份后同步测试实例，备份位于 `migration-backups/20261025-024214/`。未启动客户端；目录缓存每 20 tick 刷新，与 1.20.1 相同；BD 0.8.2 的可选 KubeJS 自定义 ExtractRule 不在本测试包内，命中该规则的资源不经父类提取 Mixin，后续须单独适配。
- 2026-10-25 玩家自动补充配置器迁移：补齐物品入口、36 槽只读菜单、规则编辑/启停/解绑 Payload 和客户端屏幕，沿用 1.20.1 屏幕尺寸与控件布局；接入主手 FE 充能，异常/过期/伪造请求不扣费不改物品。基础与 AE2 + RS 2 + Beyond Dimensions + Mekanism + FTB Quests + JEI 联测共 201/201 GameTest，正式构建通过。生产 JAR 461173 字节、282 项，SHA-256 `0F5675A5492E6861480AF5FB8EFFDA9AD8592AD7F7D3ADF7ACE3CB6062AB1C84`；含中英语言、配置器配方/模型/纹理，不含 GameTest 与 MouseTweaks API。已备份至 `migration-backups/20261025-165800/` 后同步测试实例；43 个模组 JAR 与 `zh_cn` 保持一致，客户端未启动。
- 2026-10-29 机器目标访问基础：移植相对面 NBT 解析，新增严格结构 key、服务端即时能力解析、指定单槽视图和每页 27 项只读端口快照。四朝向/六面旋转、内部 FE、NBT 损坏、结构变化、面回退拒绝、失效/异常 provider、多罐流体禁绑定及百万槽分页均有 GameTest。AE2 + RS 2 + Beyond Dimensions + Mekanism + FTB Quests + JEI 组合 206/206 GameTest 与正式构建通过。生产 JAR 477083 字节，SHA-256 `356A8347C80C3B720B9E6CF72D69BD47F2A058EF80E1DD26CC3C721825AB210C`；已备份后同步实例，43 模组及 `zh_cn` 未变，客户端未启动。当前只完成访问/枚举后端；机器配置器菜单、owner/距离/同维权限、编辑 Payload、配置持久化和自动任务仍待迁移。
- 2026-10-29 机器配置数据切片：新增 NeoForge SavedData，沿用 `primordial_machine_configs` 与 `ii/io/fi/fo/ri/ro/en` 格式；owner 锁定，目标绑定可追加/解绑且保留规则字段。组合服务端测试 208/208、构建通过；产物 482267 字节，SHA-256 `79BD9ED2BF1659F89DE311973836562F10006B6FA219F2851888048ACA8A770C`。本轮未复制实例，实例仍保留上一版 JAR；机器菜单、距离/维度权限、编辑 Payload、屏幕和调度待接入。
- 后续代码迁移：机器配置菜单有效性与权限、查询/编辑 Payload、沿用 1.20.1 布局的屏幕、自动任务调度；再做独立资源界面/实际资源端口、实体生成器、EMC 加速器、黑洞 GUI/材料/装备、奇点和生存配方。43 包中文客户端验收、AE2/RS/BD 独立 JVM 重启及 1.20.1 风格转化桌水目录/化学页显示仍待安排；游戏内核验前先通知并等用户确认桌面空闲。
- 奥瑞科技（Oritech）的 EMC/碰撞能匹配，待整个模组复刻完整后处理。
- 配置器和独立资源界面保留星辉魔法（Astral Sorcery）、奥瑞科技（Oritech）两项待接入选项；界面尚未迁移，当前先记录需求。

## 星辉魔法发行查询（2026-10-24）

[Astral Sorcery 原项目](https://www.curseforge.com/minecraft/mc-mods/astral-sorcery/files/9281073) 已有 1.21.1 NeoForge 发行：2.0.3 Alpha，2026-10-21 发布，实际文件名 `AstralSorcery-2.0.3.59.jar`。已读取官方 CDN 发行包核验：Minecraft `[1.21.1,1.22)`，ObserverLib `[1.10.3,)` 和 Curios `[9.5.1,)` 为必需前置。此次仅查询，未装入测试实例。

1.20.1 未找到原作者发行；[社区 Forge 1.20.1 移植源码](https://github.com/MrWizard94-Compile/Astral-Sorcery-1.20.1-Port) 存在，但查询时未发现公开 Release 成品，未验证游戏完整度。不要将源码项目等同于成熟发行。原项目 [1.21.1 配置](https://github.com/HellFirePvP/AstralSorcery/blob/1.21.1/gradle.properties) 可核对当前目标版本与前置要求。
