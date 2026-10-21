/**
 * 【文件职责】回归目标协议、查询工作量及可选 API 类加载边界。
 * 【变更记录 #1】2026-09-14 00:55:10（第 1 次备注 · 1.0.0-stage5）
 * - 删除：无；新增协议/隔离测试。
 * - 新增：百万槽分页只读 27 槽、网络往返、非所有者拒绝，以及隐藏可选 API 类的启动用例。
 * - 修改：通过受控类加载器模拟三个可选模组缺失，重复 bootstrap 不生成注册项。
 * - 用途：不做虚假的无限机器 TPS 承诺；此处验证算法边界和接口契约。
 */
/**
 * 【变更记录 #2】2026-10-13 22:57:15（第 2 次备注 · 1.2.0-relative-target-system）
 * - 删除：端口协议未携带朝向的旧假设。新增：朝向往返、旋转陈旧请求及非法参照拒绝用例。
 * - 修改：继续真实编解码，拒绝路径验证不访问端口和存档。用途：客户端显示与服务器最终绑定使用同一方向参照。
 */
package com.primordial.machine;

import com.primordial.menu.ConfiguratorMenu;
import com.primordial.network.C2SMachineTargetPacket;
import com.primordial.network.S2CMachinePortsPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.net.URLClassLoader;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证分页工作量、网络数据边界、编辑权限和没有安装三个可选模组的类加载隔离。 */
class MachinePortProtocolTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    @Test void hugeInventoryOnlyReadsRequestedPage() {
        BlockEntity machine = mock(BlockEntity.class);
        when(machine.getBlockState()).thenReturn(Blocks.CHEST.defaultBlockState());
        IItemHandler items = mock(IItemHandler.class);
        when(items.getSlots()).thenReturn(1_000_001);
        when(items.getStackInSlot(anyInt())).thenReturn(ItemStack.EMPTY);
        var proxy = new ModAdapterProxy("test_page") {
            @Override public IItemHandler itemHandler(BlockEntity block, Direction side) { return items; }
        };
        MachineProxyRegistry.register("test:page", candidate -> candidate == machine, proxy);
        var page = MachinePortPage.read(machine, 0, Direction.NORTH, 2);
        assertEquals(1_000_001, page.total());
        assertEquals(27, page.ports().size());
        assertEquals(54, page.ports().get(0).index());
        verify(items, times(27)).getStackInSlot(anyInt());
        verify(items, never()).insertItem(anyInt(), any(), anyBoolean());
        verify(items, never()).extractItem(anyInt(), anyInt(), anyBoolean());
    }

    @Test void selectedTargetAndSnapshotSurviveWireRoundTrip() {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var request = new C2SMachineTargetPacket(7, 2, 8, 4, 65537, true, "native-tank", false, true, 2);
            C2SMachineTargetPacket.encode(request, buf);
            assertEquals(request, C2SMachineTargetPacket.decode(buf));
            buf.clear();
            var snapshot = new S2CMachinePortsPacket(7, 2, 8, 4,
                    new MachinePortPage("mekanism", 0, 1, List.of(new MachinePortPage.Port(0, "tank",
                            new ItemStack(Items.IRON_INGOT), new FluidStack(Fluids.WATER, 1000), 1_000_000, Integer.MAX_VALUE))), false, 2);
            S2CMachinePortsPacket.encode(snapshot, buf);
            var decoded = S2CMachinePortsPacket.decode(buf);
            assertEquals(snapshot.containerId(), decoded.containerId());
            assertEquals(snapshot.side(), decoded.side());
            assertEquals(snapshot.referenceFront(), decoded.referenceFront());
            assertEquals(Integer.MAX_VALUE, decoded.page().ports().get(0).capacity());
            assertEquals(1000, decoded.page().ports().get(0).fluid().getAmount());
            assertEquals(Items.IRON_INGOT, decoded.page().ports().get(0).item().getItem());
        } finally { buf.release(); }
    }

    @Test void forgedRequestFromNonOwnerNeverReadsMachine() {
        var player = mock(ServerPlayer.class);
        var menu = mock(ConfiguratorMenu.class);
        player.containerMenu = menu;
        when(menu.canEdit(player)).thenReturn(false);
        var context = mock(NetworkEvent.Context.class);
        when(context.getSender()).thenReturn(player);
        when(context.enqueueWork(any(Runnable.class))).thenAnswer(call -> {
            ((Runnable) call.getArgument(0)).run();
            return CompletableFuture.completedFuture(null);
        });
        C2SMachineTargetPacket.handle(new C2SMachineTargetPacket(menu.containerId, 0, 0, 2, 0, true, "forged"), () -> context);
        verify(menu, never()).targetMachine();
        verify(context).setPacketHandled(true);
    }

    @Test void rotatedOrInvalidReferenceNeverReadsPortsOrWritesConfiguration() {
        var player = mock(ServerPlayer.class);
        var menu = mock(ConfiguratorMenu.class);
        player.containerMenu = menu;
        when(menu.canEdit(player)).thenReturn(true);
        var machine = mock(BlockEntity.class);
        when(menu.targetMachine()).thenReturn(machine);
        when(machine.getBlockState()).thenReturn(Blocks.FURNACE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        var context = mock(NetworkEvent.Context.class);
        when(context.getSender()).thenReturn(player);
        when(context.enqueueWork(any(Runnable.class))).thenAnswer(call -> {
            call.<Runnable>getArgument(0).run(); return CompletableFuture.completedFuture(null);
        });
        try (var pages = mockStatic(MachinePortPage.class);
             var store = mockStatic(com.primordial.data.MachineConfigSavedData.class)) {
            for (int reference : new int[]{-1, 0, 1, 2, 3, 4, 6, 127}) {
                assertFalse(C2SMachineTargetPacket.matchesOrientation(machine.getBlockState(), reference));
                C2SMachineTargetPacket.handle(new C2SMachineTargetPacket(menu.containerId, 0, 0, 2,
                        0, true, "old-front", false, false, reference), () -> context);
            }
            assertTrue(C2SMachineTargetPacket.matchesOrientation(machine.getBlockState(), Direction.EAST.get3DDataValue()));
            pages.verifyNoInteractions(); store.verifyNoInteractions();
        }
    }

    @Test void bootstrapWorksWithOptionalApiClassesHidden() throws Exception {
        var code = MachineProxy.class.getProtectionDomain().getCodeSource().getLocation();
        try (var mods = mockStatic(ModList.class);
             var loader = new URLClassLoader(new java.net.URL[]{code}, getClass().getClassLoader()) {
                 @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                     if (name.startsWith("mekanism.") || name.startsWith("appeng.") || name.startsWith("com.hrznstudio.titanium."))
                         throw new ClassNotFoundException(name);
                     if (name.startsWith("com.primordial.machine.")) {
                         synchronized (getClassLoadingLock(name)) {
                             Class<?> result = findLoadedClass(name);
                             if (result == null) result = findClass(name);
                             if (resolve) resolveClass(result);
                             return result;
                         }
                     }
                     return super.loadClass(name, resolve);
                 }
             }) {
            mods.when(ModList::get).thenReturn(mock(ModList.class));
            Class<?> bootstrap = loader.loadClass("com.primordial.machine.ModAdapterBootstrap");
            bootstrap.getMethod("registerAll").invoke(null);
            bootstrap.getMethod("registerAll").invoke(null);
            Class<?> registry = loader.loadClass("com.primordial.machine.MachineProxyRegistry");
            assertTrue(((List<?>) registry.getMethod("registeredIds").invoke(null)).isEmpty());
        }
    }
}
