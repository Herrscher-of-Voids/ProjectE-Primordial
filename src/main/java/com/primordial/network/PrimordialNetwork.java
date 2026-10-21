/**
 * 【文件职责】注册模组网络通道及 C2S/S2C 数据包。
 * 【变更记录 #23】2026-10-10 02:33:01（第 23 次备注 · 1.2.0-item-price-inherit）
 * - 删除：无。
 * - 新增：无新增数据包；价格提交包现在接受 KIND_ITEM 的 inherit 重置指令。
 * - 修改：通道版本从 22 升至 23，确保新旧客户端不会对价格重置能力产生不一致预期。
 * - 用途：客户端和服务端需同步更新；所有旧包编号及注册顺序保持不变。
 * 【变更记录 #1】2026-09-14 00:55:10（第 1 次备注 · 1.0.0-stage5）
 * - 删除：删除未限定收发方向的注册方式。
 * - 新增：端口查询/绑定和分页快照两种数据包。
 * - 修改：通道版本升为 3，各包显式限定 PLAY_TO_SERVER 或 PLAY_TO_CLIENT。
 * - 用途：避免旧协议混连及客户端状态包被发往服务端调用客户端类。
 */
/**
 * 【变更记录 #7】2026-09-16（流体与气体价格编辑网络包）
 * - 删除：无；保留既有包编号顺序和可选 Mekanism 气体包的固定占位。
 * - 新增：C2SSetPriceTargetPacket 用于保存 JEI 目标，C2SSetMediaPricePacket 用于提交流体/气体价格。
 * - 修改：协议版本升为 7，新包追加在末尾；客户端和服务端必须同时更新，避免旧协议误解码。
 * - 用途：让价格编辑界面的目标选择与价格提交分离，服务端可分别校验注册名、菜单身份和管理员权限。
 */
/**
 * 【变更记录 #2】2026-09-14 12:20:00（第 2 次备注 · 1.0.0-stage5-feedback）
 * - 删除：无。
 * - 新增：C2SSetFluidFilterPacket 注册，用于 ingredient/JEI 的无容器流体投放。
 * - 修改：网络版本保持 3，新增包追加到末尾以兼容本模组当前客户端/服务端。
 * - 用途：配置器服务端仍掌握过滤器权威数据，客户端只提交受校验的 FluidStack。
 */
/**
 * 【变更记录 #3】2026-09-15 10:59:49（第 3 次备注 · 补充规则暂停修复）
 * - 删除：无；保留协议版本 3 及既有包注册顺序。
 * - 新增：注册 C2SSetPlayerInventoryEnabledPacket，用于批量暂停或恢复玩家背包补充规则。
 * - 修改：新包追加在已有包末尾，并限定为 PLAY_TO_SERVER，避免改变旧客户端包的编号和收发方向。
 * - 用途：让服务端权威修改 enabled 状态，客户端不能借开关包提交物品、价格或其它配置数据。
 */
/**
 * 【变更记录 #4】2026-09-15 11:05:49（第 4 次备注 · 1.0.0-player-inventory-pause）
 * - 删除：沿用版本 3 接受缺少暂停包的旧客户端或服务器的兼容声明。
 * - 新增：协议版本 4，客户端和服务端均需更新才能使用暂停与状态同步。
 * - 修改：开关包增加菜单身份，旧包顺序保留；握手提前拒绝不能识别新包的版本。
 * - 用途：避免客户端显示暂停按钮但连接的旧服务器无法执行，造成补充仍在运行。
 */
/**
 * 【变更记录 #5】2026-09-15 12:04:51（第 5 次备注 · 1.0.0-configurator-fixes）
 * - 删除：沿用协议 4 的握手声明，以及可选气体包影响后续包 ID 的编号方式。
 * - 新增：批量取消背包绑定包，协议版本 5，预留固定气体包编号。
 * - 修改：客户端和服务端必须同时更新；气体包仍仅在 Mekanism 存在时注册。
 * - 用途：确保解绑可执行，避免可选依赖差异把背包包解码为其它操作。
 */
/**
 * 【变更记录 #6】2026-09-15 15:11:36（第 6 次备注 · 1.0.0-emc-service）
 * - 删除：协议 5 的浮点价格兼容声明。
 * - 新增：分块人工价格同步包，限定服务端发往客户端。
 * - 修改：协议升为 6；价格编辑携带菜单 ID 和 BigInteger 文本，两端必须一起更新。
 * - 用途：阻止旧客户端将价格包误解码，保持已有气体固定包编号。
 */
/**
 * 【变更记录 #8】2026-09-16 01:24:55（第 8 次备注 · 1.0.0-price-editor-save-fix）
 * - 删除：协议 7 的兼容握手，旧端缺少价格确认包不能正确使用新界面。
 * - 新增：在已有编号末尾注册仅服务端到客户端的 S2CPriceEditorStatePacket。
 * - 修改：协议升为 8，保留所有既有包编号和可选气体包占位，两端必须同时更新。
 * - 用途：保证价格选择/提交/确认完整通信，避免旧客户端收不到结果或误解码。
 */
/**
 * 【变更记录 #9】2026-09-16 01:52:17（第 9 次备注 · 1.0.0-projecte-shared-prices）
 * - 删除：独立物品价格覆盖包的注册，其数据不再存入另一份客户端缓存。
 * - 新增：无；物品价表和燃料表通过 ProjectE 自带的同步包发送。
 * - 修改：协议升为 9，删除的旧包编号留空，后续价格页面请求和回显编号不变。
 * - 用途：两端一致切换到 ProjectE 唯一价表，防止旧客户端继续显示独立覆盖。
 */
/**
 * 【变更记录 #10】2026-09-16 13:36:00（第 10 次备注 · 1.0.0-acceleration-safety）
 * - 删除：协议 9 的兼容握手声明。
 * - 新增：高倍率警告 S2C 和一次性确认 C2S，追加编号并明确消息方向。
 * - 修改：协议升为 10，服务端与客户端必须同时更新。
 * - 用途：让高倍率必须经可见弹窗确认，旧客户端不会静默运行未确认加速。
 */
/**
 * 【变更记录 #11】2026-09-16 14:41:00（第 11 次备注 · 1.0.0-acceleration-multiple-targets）
 * - 删除：协议 10 的握手声明。
 * - 新增：余额包的实际消费字段和高倍率警告的目标总数字段。
 * - 修改：协议升为 11，编号和方向保持；两端必须一起更新以正确读取新增字段。
 * - 用途：支持多目标报价及精确扣款显示，避免旧客户端误读载荷。
 */
/**
 * 【文件职责】注册有向网络消息及版本握手。
 * 【变更记录 #12】2026-09-16 19:41:50（第 12 次备注 · 1.0.0-review-ready）
 * - 删除：移除协议11兼容声明。
 * - 新增：护符状态、编辑动作及世界倍率标记包。
 * - 修改：协议升为12，新增包追加编号且限定发送方向。
 * - 用途：防止旧客户端错误解码新载荷，联机两端必须一起更新。
 */
/**
 * 【变更记录 #13】2026-09-18 01:50:43（第 13 次备注 · 1.0.0-acceleration-modes）
 * - 删除：协议 12 兼容声明，旧端无法读取实体标记字段。
 * - 新增：加速器管理 C2S 动作与 S2C 摘要包，均在旧编号末尾追加并限定方向。
 * - 修改：协议升为 13，客户端和服务器必须同时更新。
 * - 用途：完整支持新菜单和实体显示，保留可选气体包编号，避免缺模组时编号错位。
 */
/**
 * 【变更记录 #14】2026-10-02 16:24:00（第 14 次备注 · 1.2.0-transmutation-media-input）
 * - 删除：协议 13 的旧端兼容声明。
 * - 新增：尾部追加转化桌光标介质卖入消息。
 * - 修改：协议升级为 14；旧包编号不变。
 * - 用途：联机时服务端校验当前菜单与真实容器，两端必须同时升级。
 */
/**
 * 【变更记录 #15】2026-10-02 16:50:00（第 15 次备注 · 1.2.0-transmutation-media-output）
 * - 删除：协议 14 的旧端握手。
 * - 新增：尾部追加光标容器介质购买消息。
 * - 修改：协议升级为 15，保留所有旧消息编号。
 * - 用途：联机两端统一识别介质买入动作并在服务端校验。
 */
/**
 * 【变更记录 #16】2026-10-02 17:05:00（第 16 次备注 · 1.2.0-transmutation-media-selection）
 * - 删除：协议 15 的旧端握手。
 * - 新增：购买消息中的介质类别与注册名字段。
 * - 修改：协议升级为 16，消息编号保持不变，联机两端必须一起升级。
 * - 用途：空容器选种请求不会被旧客户端或旧服务端误读。
 */
/**
 * 【变更记录 #17】2026-10-02 17:18:00（第 17 次备注 · 1.2.0-media-catalog）
 * - 删除：协议 16 的旧端握手。
 * - 新增：转化桌目录查询 C2S 和有界结果 S2C 两个消息。
 * - 修改：协议升级至 17，既有消息编号保持不变。
 * - 用途：两端统一识别已学习介质的分页搜索结果。
 */
/**
 * 【变更记录 #18】2026-10-02 18:48:00（第 18 次备注 · 1.2.0-media-forgetting）
 * - 删除：协议 17 兼容握手。
 * - 新增：仅发往服务端的单种介质遗忘消息。
 * - 修改：协议升至 18，原消息编号不变。
 * - 用途：联机两端必须同步升级，避免旧端缺少遗忘消息。
 */
/**
 * 【变更记录 #19】2026-10-02 19:29:06（第 19 次备注 · 1.2.0-transmutation-nbt）
 * - 删除：无。
 * - 新增：菜单绑定的 NBT 设置请求和回复，分别限定服务端与客户端方向。
 * - 修改：协议 18 升级至 19，防止旧客户端缺少设置与知识规则仍然连接。
 * - 用途：以服务端玩家存档为准同步三个选项，客户端类仅在回复处理器加载。
 */
/**
 * 【变更记录 #20】2026-10-07 00:44:19（第 20 次备注 · 1.2.0-knowledge-crafting）
 * - 删除：协议 19 的兼容握手。
 * - 新增：尾部注册知识合成请求，仅允许客户端发往服务端。
 * - 修改：协议升级为 20，旧消息编号不变，两端需要同步更新。
 * - 用途：服务端接收配方身份并自行执行知识兑换配料。
 */
/**
 * 【变更记录 #21】2026-10-08（1.2.0-price-mode-sync）
 * - 删除：协议 20 的兼容声明。
 * - 新增：协议 21 握手。
 * - 修改：价格回包新增模式字段后要求两端同步更新，消息编号保持不变。
 * - 用途：避免旧端把模式字段误读为权限和提交结果。
 */
/** 【变更记录 #22】2026-10-09 01:36:12（第 22 次备注 · 1.2.0-jei-media-knowledge）
 * - 删除：协议 21 握手。新增：可见介质报价请求与响应。
 * - 修改：协议升为 22，消息追加注册。用途：两端一致使用服务端知识和价格判定 JEI 数量。 */
/**
 * 【变更记录 #24】2026-10-10 03:53:52（第 24 次备注 · 1.2.0-resource-system-one）
 * - 删除：协议 23 握手。
 * - 新增：服务端到客户端的资源交易结果消息。
 * - 修改：协议升级到 24；新增回包位于旧 JEI 报价响应之前，两端须使用同一构建。
 * - 用途：让资源系统提供菜单绑定的成交反馈，避免旧客户端无法解码。
 */
/**
 * 【变更记录 #25】2026-10-10 03:58:22（第 25 次备注 · 1.2.0-resource-system-one）
 * - 删除：交易回包插入旧消息中间导致后续编号改变的安排。
 * - 新增：无新消息；把本修订新增回包移到注册末尾。
 * - 修改：协议保持 24，旧消息的编号与方向完整保留。
 * - 用途：符合网络追加约定，防止后续维护把旧包身份与新反馈混淆。
 */
/**
 * 【变更记录 #26】2026-10-12 23:38:49（第 26 次备注 · 1.2.0-media-real-slots）
 * - 删除：旧介质页不完整的交互路径。
 * - 新增：协议 25 与菜单页签请求，保持旧包编号。
 * - 修改：对照 ProjectE 真实槽位及原生控件处理。
 * - 用途：介质页状态、容器安全与显示一致，保留全部历史。
 */
/**
 * 【变更记录 #27】2026-10-13 22:57:15（第 27 次备注 · 1.2.0-relative-target-system）
 * - 删除：新绑定只保存世界方向、机器旋转后仍操作旧面的路径；所有历史备注保留。
 * - 新增/修改：通信版本由 25 升为 26，对齐端口查询与回包新增朝向字段；不匹配的客户端不能误读协议。
 * - 用途：第 9 系统相对面绑定与显示、网络、存档、执行保持一致；已有绝对目标重新绑定才转为相对，避免误改输入输出。
 */
/**
 * 【变更记录 #28】2026-10-14 00:55:00（第 28 次备注 · 1.2.0-blackhole-equipment-system）
 * - 删除：协议 26 的旧端握手；新增尾部手持装备菜单请求包。
 * - 修改：版本升至 27，所有旧包 ID 和方向保持不变。
 * - 用途：客户端/服务端同步识别装备模块菜单，服务端只接受权威手持物。
 */
/**
 * 【变更记录 #29】2026-10-14 01:18:08（第 29 次备注 · 1.2.0-equipment-emc-features）
 * - 删除：实体模块物品关联。
 * - 新增：装备内购买功能关联。
 * - 修改：协议 27 升为 28，拒绝旧实物模块界面客户端。
 * - 用途：功能由装备界面 EMC 解锁，避免继续提供旧实物安装入口。
 */
/**
 * 【变更记录 #30】2026-10-14 01:41:14（第 30 次备注 · 1.2.0-blackhole-equipment-expansion）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：等级调整请求注册与协议 29；旧版菜单客户端必须更新。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
/**
 * 【变更记录 #31】2026-10-14 02:37:36（第 31 次备注 · 1.2.0-singularity-signatures）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：增加主动能力空载荷请求，协议30。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
/**
 * 【变更记录 #32】2026-10-14 03:22:29（第 32 次备注 · 1.2.0-exponential-equipment）
 * - 删除：本文件原有线性/对数等级结算或过时提示。
 * - 新增：Protocol 31 requires matching client/server exponential pricing and Rapid Draw support.
 * - 修改：统一为等级L对应2^(L-1)，使用费与升级价格逐级翻倍，0关闭。
 * - 用途：落实用户指数成长规则；保留历史等级，服务端余额校验和逐刻预算。
 */
/**
 * 【变更记录 #33】2026-10-14 03:32:08（第 33 次备注 · 1.2.0-singularity-animation）
 * - 删除：黑洞瞬间完整出现、寿命结束直接消失的视觉路径。
 * - 新增：协议32要求两端具备相同黑洞实体同步字段，防止旧客户端解析错误。
 * - 修改：服务端同步出生进度与末段寿命，客户端插值三维模型缩放和透明度。
 * - 用途：黑洞展开、坍缩均随实际寿命；保留指数寿命与原EMC结算。
 */
package com.primordial.network;

import com.primordial.PrimordialMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class PrimordialNetwork {
    private static final String VERSION = "32";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(PrimordialMod.MODID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private PrimordialNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, C2SUpdateConfigPacket.class,
                C2SUpdateConfigPacket::encode, C2SUpdateConfigPacket::decode, C2SUpdateConfigPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, S2CFullConfigSyncPacket.class,
                S2CFullConfigSyncPacket::encode, S2CFullConfigSyncPacket::decode, S2CFullConfigSyncPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, S2CSyncBalancePacket.class,
                S2CSyncBalancePacket::encode, S2CSyncBalancePacket::decode, S2CSyncBalancePacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, C2SPlayerActionPacket.class,
                C2SPlayerActionPacket::encode, C2SPlayerActionPacket::decode, C2SPlayerActionPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, C2SMachineTargetPacket.class,
                C2SMachineTargetPacket::encode, C2SMachineTargetPacket::decode, C2SMachineTargetPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, S2CMachinePortsPacket.class,
                S2CMachinePortsPacket::encode, S2CMachinePortsPacket::decode, S2CMachinePortsPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, C2SSetFluidFilterPacket.class,
                C2SSetFluidFilterPacket::encode, C2SSetFluidFilterPacket::decode, C2SSetFluidFilterPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, C2SSetItemFilterPacket.class,
                C2SSetItemFilterPacket::encode, C2SSetItemFilterPacket::decode, C2SSetItemFilterPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        // Mekanism 化学气体包只在对应模组存在时注册，避免可选依赖缺失导致主模组加载失败。
        int gasPacketId = id++;
        if (net.minecraftforge.fml.ModList.get().isLoaded("mekanism")) {
            CHANNEL.registerMessage(gasPacketId, C2SSetGasFilterPacket.class,
                    C2SSetGasFilterPacket::encode, C2SSetGasFilterPacket::decode, C2SSetGasFilterPacket::handle,
                    java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        }
        CHANNEL.registerMessage(id++, C2SPlayerInventoryRulePacket.class,
                C2SPlayerInventoryRulePacket::encode, C2SPlayerInventoryRulePacket::decode, C2SPlayerInventoryRulePacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, C2SSetPlayerInventoryEnabledPacket.class,
                C2SSetPlayerInventoryEnabledPacket::encode, C2SSetPlayerInventoryEnabledPacket::decode,
                C2SSetPlayerInventoryEnabledPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, C2SClearPlayerInventoryRulesPacket.class,
                C2SClearPlayerInventoryRulesPacket::encode, C2SClearPlayerInventoryRulesPacket::decode,
                C2SClearPlayerInventoryRulesPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        id++; // 已移除独立物品覆盖同步；保留编号，物品价格改用 ProjectE 原生网络包。
        CHANNEL.registerMessage(id++, C2SSetPriceTargetPacket.class,
                C2SSetPriceTargetPacket::encode, C2SSetPriceTargetPacket::decode, C2SSetPriceTargetPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, C2SSetMediaPricePacket.class,
                C2SSetMediaPricePacket::encode, C2SSetMediaPricePacket::decode, C2SSetMediaPricePacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, S2CPriceEditorStatePacket.class,
                S2CPriceEditorStatePacket::encode, S2CPriceEditorStatePacket::decode, S2CPriceEditorStatePacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, S2CAccelerationWarningPacket.class,
                S2CAccelerationWarningPacket::encode, S2CAccelerationWarningPacket::decode, S2CAccelerationWarningPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, C2SConfirmAccelerationPacket.class,
                C2SConfirmAccelerationPacket::encode, C2SConfirmAccelerationPacket::decode, C2SConfirmAccelerationPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, S2CCharmFilterPacket.class,
                S2CCharmFilterPacket::encode, S2CCharmFilterPacket::decode, S2CCharmFilterPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, S2CAccelerationMarkersPacket.class,
                S2CAccelerationMarkersPacket::encode, S2CAccelerationMarkersPacket::decode, S2CAccelerationMarkersPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, C2SCharmFilterActionPacket.class,
                C2SCharmFilterActionPacket::encode, C2SCharmFilterActionPacket::decode, C2SCharmFilterActionPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, C2SAccelerationActionPacket.class,
                C2SAccelerationActionPacket::encode, C2SAccelerationActionPacket::decode, C2SAccelerationActionPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, S2CAccelerationStatePacket.class,
                S2CAccelerationStatePacket::encode, S2CAccelerationStatePacket::decode, S2CAccelerationStatePacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, C2SSellCarriedMediaPacket.class,
                C2SSellCarriedMediaPacket::encode, C2SSellCarriedMediaPacket::decode, C2SSellCarriedMediaPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, C2SBuyCarriedMediaPacket.class,
                C2SBuyCarriedMediaPacket::encode, C2SBuyCarriedMediaPacket::decode, C2SBuyCarriedMediaPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, C2SRequestMediaCatalogPacket.class,
                C2SRequestMediaCatalogPacket::encode, C2SRequestMediaCatalogPacket::decode,
                C2SRequestMediaCatalogPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, S2CMediaCatalogPacket.class,
                S2CMediaCatalogPacket::encode, S2CMediaCatalogPacket::decode, S2CMediaCatalogPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, C2SForgetMediaPacket.class,
                C2SForgetMediaPacket::encode, C2SForgetMediaPacket::decode, C2SForgetMediaPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        PrimordialMod.LOGGER.info("[Primordial] network registered");
        CHANNEL.registerMessage(id++, C2SNbtSettingsPacket.class,
                C2SNbtSettingsPacket::encode, C2SNbtSettingsPacket::decode, C2SNbtSettingsPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, S2CNbtSettingsPacket.class,
                S2CNbtSettingsPacket::encode, S2CNbtSettingsPacket::decode, S2CNbtSettingsPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, C2SKnowledgeCraftPacket.class,
                C2SKnowledgeCraftPacket::encode, C2SKnowledgeCraftPacket::decode, C2SKnowledgeCraftPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, C2SJeiEmcPurchasePacket.class,
                C2SJeiEmcPurchasePacket::encode, C2SJeiEmcPurchasePacket::decode, C2SJeiEmcPurchasePacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, C2SJeiMediaQuotesPacket.class,
                C2SJeiMediaQuotesPacket::encode, C2SJeiMediaQuotesPacket::decode, C2SJeiMediaQuotesPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, S2CJeiMediaQuotesPacket.class,
                S2CJeiMediaQuotesPacket::encode, S2CJeiMediaQuotesPacket::decode, S2CJeiMediaQuotesPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, S2CMediaTradeResultPacket.class,
                S2CMediaTradeResultPacket::encode, S2CMediaTradeResultPacket::decode, S2CMediaTradeResultPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, C2SMediaPagePacket.class,
                C2SMediaPagePacket::encode, C2SMediaPagePacket::decode, C2SMediaPagePacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, C2SSignatureActionPacket.class,
                C2SSignatureActionPacket::encode,C2SSignatureActionPacket::decode,C2SSignatureActionPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, C2SEquipmentFeaturePacket.class,
                C2SEquipmentFeaturePacket::encode, C2SEquipmentFeaturePacket::decode,
                C2SEquipmentFeaturePacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, C2SOpenBlackholeEquipmentPacket.class,
                C2SOpenBlackholeEquipmentPacket::encode, C2SOpenBlackholeEquipmentPacket::decode,
                C2SOpenBlackholeEquipmentPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
    }
}
