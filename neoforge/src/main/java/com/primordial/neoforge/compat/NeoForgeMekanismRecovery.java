/**
 * 【文件职责】隔离 Mekanism 1.21.1 统一化学品 API，为黑洞注册原生 chemical_handler 接收能力。
 * 【变更记录 #1】2026-10-22 22:19:39（第 1 次备注 · 2.0.0-neo-dev Mekanism 化学品回收）
 * - 删除：无。
 * - 新增：单虚拟槽、所有方向和无方向端口、原生标签查询以及 long 数量剩余栈转换。
 * - 修改：能力 ID 和类型与 Mekanism 一致；额度、归属、报价和学习全部委托公共方块实体服务。
 * - 用途：主启动器确认可选模组存在后反射调用 register；未装 Mekanism 时不解析本类或第三方类型。
 * 【变更记录 #2】2026-10-22 22:33:12（第 2 次备注 · 2.0.0-neo-dev 可选 API 消费前预检）
 * - 删除：收益确认后才复制原生剩余栈的分支，防止链接或分配失败令源端重投已入账资源。
 * - 新增：注册 provider 前严格核对原生方法/字段签名并用独立空栈试运行，运行期消费前故障仅记录一次并关闭端口。
 * - 修改：先复制返回栈、读取 ID/标签和 Action 并预热数量 setter，再调用公共回收；确认收益后仅更新已验证的数量字段。
 * - 用途：不兼容 API 由公共反射启动器跳过，公共回收异常不吞成全额拒收，避免重复 EMC 与资源丢失。
 */
package com.primordial.neoforge.compat;

import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.mojang.logging.LogUtils;
import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.blackhole.NeoForgeBlackholeBlockEntity;
import com.primordial.neoforge.config.NeoForgeServerConfig;

import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

public final class NeoForgeMekanismRecovery {
    private static final AtomicBoolean API_FAILED = new AtomicBoolean();
    // NeoForge 会按 ID、接口类和上下文类返回同一能力实例，无须依赖 Mekanism common 实现包。
    public static final BlockCapability<IChemicalHandler, @Nullable Direction> CHEMICAL =
            BlockCapability.createSided(ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"), IChemicalHandler.class);

    private NeoForgeMekanismRecovery() {}

    public static void register(RegisterCapabilitiesEvent event) {
        preflight();
        event.registerBlockEntity(CHEMICAL, NeoForgeModRegistries.BLACKHOLE_ENTITY.get(),
                (blackhole, side) -> new ChemicalSink(blackhole));
    }

    /** 在 provider 注册前解析实际使用的方法；独立空栈试运行不会写注册表、世界或共享 EMPTY。 */
    private static void preflight() {
        try {
            requireMethod(ChemicalStack.class, "isEmpty", boolean.class);
            requireMethod(ChemicalStack.class, "getAmount", long.class);
            requireMethod(ChemicalStack.class, "getChemicalHolder", Holder.class);
            requireMethod(ChemicalStack.class, "getTags", Stream.class);
            requireMethod(ChemicalStack.class, "copy", ChemicalStack.class);
            requireMethod(ChemicalStack.class, "setAmount", void.class, long.class);
            requireMethod(Action.class, "simulate", boolean.class);
            requireMethod(IChemicalHandler.class, "getChemicalTanks", int.class);
            requireMethod(IChemicalHandler.class, "getChemicalInTank", ChemicalStack.class, int.class);
            requireMethod(IChemicalHandler.class, "setChemicalInTank", void.class, int.class, ChemicalStack.class);
            requireMethod(IChemicalHandler.class, "getChemicalTankCapacity", long.class, int.class);
            requireMethod(IChemicalHandler.class, "isValid", boolean.class, int.class, ChemicalStack.class);
            requireMethod(IChemicalHandler.class, "insertChemical", ChemicalStack.class, int.class, ChemicalStack.class, Action.class);
            requireMethod(IChemicalHandler.class, "extractChemical", ChemicalStack.class, int.class, long.class, Action.class);
            requireMethod(IChemicalHandler.class, "insertChemical", ChemicalStack.class, ChemicalStack.class, Action.class);
            requireMethod(IChemicalHandler.class, "extractChemical", ChemicalStack.class, long.class, Action.class);
            requireMethod(IChemicalHandler.class, "extractChemical", ChemicalStack.class, ChemicalStack.class, Action.class);
            for (var method : IChemicalHandler.class.getMethods()) {
                if (Modifier.isAbstract(method.getModifiers()) && Modifier.isAbstract(ChemicalSink.class
                        .getMethod(method.getName(), method.getParameterTypes()).getModifiers())) {
                    throw new IllegalStateException("Unsupported abstract chemical handler method: " + method);
                }
            }
            if (ChemicalStack.class.getField("EMPTY").getType() != ChemicalStack.class
                    || MekanismAPI.class.getField("CHEMICAL_REGISTRY_NAME").getType() != ResourceKey.class) {
                throw new IllegalStateException("Unsupported chemical API field type");
            }
            ChemicalStack.class.getConstructor(Holder.class, long.class);
            ChemicalStack trial = new ChemicalStack(ChemicalStack.EMPTY.getChemicalHolder(), 0);
            trial.setAmount(1);
            trial.setAmount(0);
            if (!trial.isEmpty() || trial.getAmount() != 0 || !trial.copy().isEmpty()
                    || !trial.getChemicalHolder().unwrapKey().orElseThrow().equals(MekanismAPI.EMPTY_CHEMICAL_KEY)
                    || !Action.SIMULATE.simulate() || Action.EXECUTE.simulate()) {
                throw new IllegalStateException("Unsupported chemical API empty-stack/action behavior");
            }
            trial.getTags().forEach(tag -> tag.location());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Mekanism chemical recovery API is incompatible", failure);
        }
    }

    private static void requireMethod(Class<?> owner, String name, Class<?> result, Class<?>... arguments)
            throws ReflectiveOperationException {
        if (owner.getMethod(name, arguments).getReturnType() != result) {
            throw new IllegalStateException("Unsupported return type for " + owner.getName() + "." + name);
        }
    }

    private static void rejectApiFailure(Throwable failure) {
        if (API_FAILED.compareAndSet(false, true)) LogUtils.getLogger().warn(
                "[Primordial] Mekanism chemical API unavailable before resource reception; chemical ports disabled", failure);
    }

    private record Prepared(ChemicalStack remainder, ChemicalStack empty, ResourceLocation id, long amount,
            Set<ResourceLocation> tags, boolean simulate) {}

    private static final class ChemicalSink implements IChemicalHandler {
        private final NeoForgeBlackholeBlockEntity block;

        private ChemicalSink(NeoForgeBlackholeBlockEntity block) { this.block = block; }

        @Override public int getChemicalTanks() { return 1; }
        @Override public ChemicalStack getChemicalInTank(int tank) { return ChemicalStack.EMPTY; }
        @Override public long getChemicalTankCapacity(int tank) { return tank == 0 ? NeoForgeServerConfig.maxGas() : 0; }

        @Override
        public void setChemicalInTank(int tank, ChemicalStack stack) {
            throw new UnsupportedOperationException("A recovery sink has no writable chemical inventory");
        }

        @Override public ChemicalStack extractChemical(int tank, long amount, Action action) { return ChemicalStack.EMPTY; }

        @Override
        public boolean isValid(int tank, ChemicalStack stack) {
            if (tank != 0 || stack == null || API_FAILED.get() || !block.canRecycleResources()) return false;
            ResourceLocation id;
            Set<ResourceLocation> tags;
            try {
                if (stack.isEmpty()) return false;
                id = stack.getChemicalHolder().unwrapKey().map(key -> key.location()).orElse(null);
                tags = stack.getTags().map(tag -> tag.location()).collect(Collectors.toUnmodifiableSet());
            } catch (RuntimeException | LinkageError failure) {
                rejectApiFailure(failure);
                return false;
            }
            return id != null && block.isChemicalValid(id, tags::contains);
        }

        /** 剩余量只用非负 long 相减；调用方的栈始终不变，所有端口共用方块实体的每刻额度。 */
        @Override
        public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
            if (stack == null) return ChemicalStack.EMPTY;
            if (API_FAILED.get()) return stack;
            Prepared prepared;
            try {
                if (stack.isEmpty()) return ChemicalStack.EMPTY;
                ChemicalStack remainder = stack.copy();
                if (tank != 0 || !block.canRecycleResources()) return remainder;
                ResourceLocation id = stack.getChemicalHolder().unwrapKey().map(key -> key.location()).orElse(null);
                if (id == null) return remainder;
                long amount = stack.getAmount();
                remainder.setAmount(amount);
                Set<ResourceLocation> tags = stack.getTags().map(tag -> tag.location()).collect(Collectors.toUnmodifiableSet());
                prepared = new Prepared(remainder, ChemicalStack.EMPTY, id, amount, tags, action.simulate());
            } catch (RuntimeException | LinkageError failure) {
                rejectApiFailure(failure);
                return stack;
            }
            // 消费边界外不捕公共服务异常；Mekanism 标签/复制/Action 调用与分配全部已在确认收益前完成。
            long accepted = block.recycleChemical(prepared.id, prepared.amount, prepared.simulate, prepared.tags::contains);
            if (accepted <= 0) return prepared.remainder;
            if (accepted >= prepared.amount) return prepared.empty;
            prepared.remainder.setAmount(prepared.amount - accepted);
            return prepared.remainder;
        }
    }
}
