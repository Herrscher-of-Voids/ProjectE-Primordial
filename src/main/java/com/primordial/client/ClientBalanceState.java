/**
 * 变更记录 #2（2026-09-13）：接入 ProjectE 1.20.1 BigInteger EMC。
 * - 删除：玩家 PM 余额读写和 microPM 结算路径。
 * - 新增：ProjectE capability、IEMCProxy、BigInteger 网络同步及离线增量保存。
 * - 修改：物品、流体、能量结算统一使用 EMC，避免 long 溢出。
 */
/**
 * 【文件职责】
 * - 保存客户端当前显示用的 EMC 数值和余额 HUD 开关状态。
 * - 接收网络层写入的余额，向 HUD、配置器界面等客户端组件提供只读访问。
 *
 * 【变更记录 #1】2026-09-13 22:52:14（第 1 次备注·EMC 显示改造）
 * - 删除：原 long EMC 缓存字段。
 * - 新增：BigInteger EMC 缓存、非负值校验和旧网络包兼容转换方法。
 * - 修改：客户端余额从 EMC 微单位模型改为 EMC 显示模型；旧包暂按 1000 EMC = 1 EMC 转换。
 * - 用途：为后续接入 ProjectE 原生 EMC 与高位扩展存储提供不会溢出的客户端数据类型；旧同步协议暂时仍可编译运行。
 */
/**
 * 【变更记录 #3】2026-09-16 14:41:00（第 3 次备注 · 1.0.0-acceleration-multiple-targets）
 * - 删除：无。
 * - 新增：最近 10 tick 的服务端加速净扣款和接收时间，2 秒无更新自动隐藏。
 * - 修改：余额与消费明细分别保存，不从紧凑格式化后的余额反推支出。
 * - 用途：为 HUD 提供真实扣款；停用、离线或无工作后不会永久显示旧的消费数值。
 */
package com.primordial.client;

import java.math.BigInteger;

/** 客户端 EMC 缓存 + HUD 开关状态。 */
public final class ClientBalanceState {
    private static volatile BigInteger emc = BigInteger.ZERO;
    private static BigInteger accelerationCharge = BigInteger.ZERO;
    private static long chargeReceived;
    public static boolean hudVisible = true;

    private ClientBalanceState() {}

    public static BigInteger getEmc() {
        return emc;
    }

    public static void setEmc(BigInteger value) {
        emc = value == null || value.signum() < 0 ? BigInteger.ZERO : value;
    }

    public static void setAccelerationCharge(BigInteger value) {
        accelerationCharge = value.max(BigInteger.ZERO);
        chargeReceived = System.nanoTime();
    }

    public static BigInteger getAccelerationCharge() {
        return System.nanoTime() - chargeReceived < 2_000_000_000L ? accelerationCharge : BigInteger.ZERO;
    }

}
