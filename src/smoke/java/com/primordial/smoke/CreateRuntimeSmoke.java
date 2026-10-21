/**
 * 【文件职责】在 Create 6.0.8 真实世界摆放动力轴和负载，执行供给、扣款、停机及重新供给测试。
 * 【变更记录 #1】2026-09-16 20:09:32（第 1 次备注 · 1.0.0-create-kinetics）
 * - 删除：无。
 * - 新增：机器配置至 TaskExecutor 至 EMC 结算至 Create 传播的整链验证。
 * - 修改：仅安装 Create 时加载本测试类；隔离存档不读取用户世界。
 * - 用途：检验 Mixin 真实应用、邻接机器旋转、保存不残留临时容量及失去资金后的停机。
 */
/**
 * 【变更记录 #2】2026-09-16 20:37:07（第 2 次备注 · 1.0.0-create-kinetics）
 * - 删除：旧测试方块和规则对重复验证的干扰。
 * - 新增：运行中保存/读回、原生电动机接管、较低 EMC 转速不能压低原生转速和停止后继续原生供能断言。
 * - 修改：使用准确的 mechanical_drill 注册名；共运行 57 个服务端 tick。
 * - 用途：真实原生网络、Mixin、配置绑定、结算和存档共同验证，不把纯 JUnit 当作游戏实测。
 */
package com.primordial.smoke;

import com.primordial.data.*;
import com.primordial.resource.*;
import com.primordial.service.EmcService;
import com.primordial.util.MachineKey;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import static com.primordial.smoke.RuntimeSmoke.check;

final class CreateRuntimeSmoke {
    private static final BlockPos SHAFT = new BlockPos(0, 100, 0);
    private static final BlockPos LOAD = SHAFT.east();
    private static MachineConfig config;
    private static UUID owner;
    private static BigInteger lastBalance;
    private static net.minecraft.nbt.CompoundTag sourceSave;
    private static net.minecraft.nbt.CompoundTag loadSave;

    static void start(MinecraftServer server, UUID player) {
        owner = player;
        var level = server.overworld();
        level.removeBlock(LOAD, false);
        level.removeBlock(SHAFT, false);
        level.removeBlock(SHAFT.west(), false);
        MachineConfigSavedData.get(server).remove(MachineKey.of(level.dimension(), SHAFT));
        var shaft = BuiltInRegistries.BLOCK.get(new ResourceLocation("create", "shaft"));
        var drill = BuiltInRegistries.BLOCK.get(new ResourceLocation("create", "mechanical_drill"));
        level.setBlockAndUpdate(SHAFT, shaft.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        level.setBlockAndUpdate(LOAD, drill.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
        check(level.getBlockEntity(SHAFT) instanceof KineticBlockEntity, "shaft entity");
        var ports = ResourcePortRegistry.find(level.getBlockEntity(SHAFT), null);
        check(ports.size() == 2, "expected speed and stress ports, got " + ports.size());
        config = MachineConfigSavedData.get(server).getOrCreate(MachineKey.of(level.dimension(), SHAFT), owner);
        for (int i = 0; i < ports.size(); i++) {
            EmcResourcePort port = ports.get(i);
            int rule = ResourceExchangeConversions.CREATE_SPEED.equals(port.unit()) ? 4 : 5;
            ResourceSampleConfig sample = config.resourceIn[rule];
            sample.setTargets(List.of(new MachineTarget(MachineTarget.INTERNAL_SIDE, i, port.key())));
            sample.mode = SampleMode.FIXED_RATE;
            sample.amount = rule == 4 ? 32 : 4096;
            sample.intervalTicks = 1;
            sample.enabled = true;
            check(!port.canExtract(), "kinetic power cannot be recycled");
        }
        EmcService.add(server, owner, BigInteger.valueOf(100000));
        lastBalance = EmcService.balance(server, owner);
    }

    static boolean tick(MinecraftServer server, int tick) {
        if (config == null) return false;
        var level = server.overworld();
        var source = (KineticBlockEntity) level.getBlockEntity(SHAFT);
        var load = (KineticBlockEntity) level.getBlockEntity(LOAD);
        if (tick == 12) {
            check(Math.abs(source.getSpeed()) == 32, "source speed=" + source.getSpeed());
            check(Math.abs(load.getSpeed()) == 32, "load speed=" + load.getSpeed());
            check(source.getOrCreateNetwork().calculateCapacity() >= 4096, "stress must reach network");
            check(EmcService.balance(server, owner).compareTo(lastBalance) < 0, "actual EMC debit");
            check(load.saveWithoutMetadata().getCompound("Network").getFloat("Capacity") == 0, "saved EMC stress must be zero");
            config.resourceIn[4].enabled = false;
            config.resourceIn[5].enabled = false;
        }
        if (tick == 17) {
            check(source.getSpeed() == 0 && load.getSpeed() == 0, "pause must stop network");
            config.resourceIn[4].enabled = true;
            config.resourceIn[5].enabled = true;
        }
        if (tick == 25) {
            check(Math.abs(load.getSpeed()) == 32, "resume must restore load");
            sourceSave = source.saveWithFullMetadata();
            loadSave = load.saveWithFullMetadata();
            com.primordial.service.EmcSettlement.clear(server, owner);
        }
        if (tick == 30) {
            check(source.getSpeed() == 0 && load.getSpeed() == 0, "insufficient EMC must stop network");
            check(EmcService.balance(server, owner).signum() == 0, "no negative EMC");
            // 用运行时保存的 NBT 重建源和负载；已付款动力不应从保存数据复活。
            source.load(sourceSave);
            load.load(loadSave);
        }
        if (tick == 40) {
            check(source.getSpeed() == 0 && load.getSpeed() == 0, "saved state must not resurrect unpaid power");
            var motor = BuiltInRegistries.BLOCK.get(new ResourceLocation("create", "creative_motor"));
            level.setBlockAndUpdate(SHAFT.west(), motor.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
        }
        if (tick == 46) {
            check(source.getSpeed() != 0 && load.getSpeed() != 0, "native generator must remain functional");
            EmcService.add(server, owner, BigInteger.valueOf(100000));
            config.resourceIn[5].enabled = false;
            config.resourceIn[4].amount = 8; // 原生电动机默认更快，EMC 不应降低原生转速。
        }
        if (tick == 52) {
            check(Math.abs(load.getSpeed()) > 8, "stronger native generator must retain speed");
            config.resourceIn[4].enabled = false;
        }
        if (tick == 57) {
            check(Math.abs(load.getSpeed()) > 8, "stopping EMC must preserve native generation");
            var motor = level.getBlockEntity(SHAFT.west());
            var motorPorts = ResourcePortRegistry.find(motor, null);
            check(motorPorts.size() == 1 && motorPorts.get(0).unit().equals(ResourceExchangeConversions.CREATE_STRESS),
                    "native generator should expose stress only");
            return true;
        }
        return false;
    }
}
