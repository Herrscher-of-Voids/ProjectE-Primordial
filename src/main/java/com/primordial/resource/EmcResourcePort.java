/**
 * 【文件职责】描述一种可以由 EMC 结算的第三方资源端口。
 *
 * 端口只负责报告当前数量、容量以及原生读写操作；EMC 扣款、退款和比例换算统一由
 * {@code EmcService} 完成。实现类必须在 simulate=true 时不修改第三方方块实体。
 *
 * 变更记录 #1（2026-09-16）：新增独立资源端口抽象，供 Botania、PneumaticCraft、
 * Nature's Aura、Ars Nouveau 和 Create 的可选适配器共享；不直接引用这些模组的类，
 * 防止未安装对应模组时发生类加载失败。
 */
package com.primordial.resource;

import net.minecraft.resources.ResourceLocation;

/** 可被 EMC 服务购买或回收的单一资源端口。数量单位由 {@link #unit()} 定义。 */
public interface EmcResourcePort {
    /** 资源注册单位，例如 botania:mana 或 create:stress。 */
    ResourceLocation unit();

    /** 当前端口中的资源数量。返回值必须为非负数。 */
    long amount();

    /** 端口允许的最大资源数量；未知容量返回 Long.MAX_VALUE。 */
    long capacity();

    /** 端口是否接受 EMC 生成的资源。 */
    boolean canReceive();

    /** 端口是否允许资源回收为 EMC。 */
    boolean canExtract();

    /** 模拟或执行接收，返回实际接收数量。 */
    long receive(long amount, boolean simulate);

    /** 模拟或执行抽取，返回实际抽取数量。 */
    long extract(long amount, boolean simulate);

    /** 稳定端口身份，用于配置器绑定和日志诊断。 */
    default String key() { return unit().toString(); }
}
