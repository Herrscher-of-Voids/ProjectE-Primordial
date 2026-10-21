/**
 * 【文件职责】通过真实 Brigadier 改价命令验证服务端审计和价格模式记录。
 * 【变更记录 #1】2026-10-10 03:05:29（第 1 次备注 · 1.2.0-price-change-audit）
 * - 删除：无。
 * - 新增：控制台流体分数、气体大整数命令、管理员模式变更、普通玩家拒绝的日志和持久化回归。
 * - 修改：仅替代命令源/玩家和 Forge 路径，命令树、ValueManager 与 PriceChangeAudit 使用真实实现。
 * - 用途：验证命令不会漏审计，控制台无需玩家 UUID，禁用模式保留底层价格且不得记为免费；测试恢复原路径。
 */
/**
 * 【变更记录 #2】2026-10-10 03:07:44（第 2 次备注 · 1.2.0-price-change-audit）
 * - 删除：气体用例未设置 Forge 安装状态的环境缺口。
 * - 新增：限定在气体用例中模拟 Mekanism 已安装，并补充非法分数命令拒绝后无成功日志验证。
 * - 修改：命令仍通过真实注册树执行；未加引号的分数同时覆盖 Brigadier 解析与实际持久化。
 * - 用途：将 API 运行条件与实际气体注册表测试配套，验证错误输入不会污染价格或审计。
 */
/**
 * 【变更记录 #3】2026-10-10 04:14:04（第 3 次备注 · 1.2.0-resource-system-one）
 * - 删除：测试结束时直接单次删除临时目录的易波动清理路径。
 * - 新增：使用 RetryingTestTempDirFactory 的有界清理重试，持续失败仍导致测试失败。
 * - 修改：临时目录仍每项/每类独立，模组持久化代码和交易断言保持原有检查；工厂负责最终删除。
 * - 用途：Windows 短暂目录占用不再误判业务失败，绝不关闭真实保存或回滚验证。
 */
/**
 * 【变更记录 #5】2026-10-11 04:26:53（第 5 次备注 · 1.2.0-admin-pricing-system）
 * - 删除：无。
 * - 新增：GUI 审计测试玩家显式设为创造管理员。
 * - 修改：命令源权限测试仍覆盖非玩家命令规则。
 * - 用途：区分定价器 GUI 门禁与管理员命令入口。
 */
package com.primordial.machine;

import com.mojang.brigadier.CommandDispatcher;
import com.primordial.command.PrimordialCommands;
import com.primordial.service.EmcAmount;
import com.primordial.service.PriceChangeAudit;
import com.primordial.value.PriceMode;
import com.primordial.value.ValueManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PriceAuditCommandsTest {
    @TempDir(factory = com.primordial.util.RetryingTestTempDirFactory.class, cleanup = org.junit.jupiter.api.io.CleanupMode.NEVER) Path directory;
    private final EnumMap<FMLPaths, Path> previousPaths = new EnumMap<>(FMLPaths.class);
    private PriceAuditLogCapture audit;
    private CommandSourceStack console;
    private CommandDispatcher<CommandSourceStack> dispatcher;

    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    @BeforeEach void setup() {
        for (var path : FMLPaths.values()) previousPaths.put(path, path.get());
        FMLPaths.loadAbsolutePaths(directory);
        ValueManager.clear();
        assertTrue(ValueManager.reload());
        audit = new PriceAuditLogCapture();
        console = mock(CommandSourceStack.class);
        when(console.hasPermission(2)).thenReturn(true);
        when(console.getTextName()).thenReturn("Server");
        dispatcher = new CommandDispatcher<>();
        PrimordialCommands.register(dispatcher);
    }

    @AfterEach void cleanup() throws Exception {
        if (audit != null) audit.close();
        ValueManager.clear();
        var field = FMLPaths.class.getDeclaredField("absolutePath");
        field.setAccessible(true);
        for (var entry : previousPaths.entrySet()) field.set(entry.getKey(), entry.getValue());
    }

    @Test void consoleCommandAuditsTheExactFluidFractionAndPersistsIt() throws Exception {
        assertEquals(1, dispatcher.execute("primordial set_value fluid minecraft:water 1/3", console));
        assertEquals(1, audit.entries.size());
        var entry = audit.entries.get(0);
        assertEquals("Server", entry.get("operator").getAsString());
        assertEquals("", entry.get("operator_uuid").getAsString());
        assertEquals("command", entry.get("origin").getAsString());
        assertEquals("EMC/1000mB", entry.get("unit").getAsString());
        assertEquals("1000", entry.getAsJsonObject("before").get("price").getAsString());
        assertEquals("1/3", entry.getAsJsonObject("after").get("price").getAsString());
        assertTrue(ValueManager.reload());
        assertEquals(EmcAmount.parse("1/3"), ValueManager.fluidSnapshot().get("minecraft:water"));
    }

    @Test void gasCommandAuditsBigIntegerWithoutRounding() throws Exception {
        try (var gas = new GasTestRegistry(); var mods = mockStatic(net.minecraftforge.fml.ModList.class)) {
            var installed = mock(net.minecraftforge.fml.ModList.class);
            when(installed.isLoaded("mekanism")).thenReturn(true);
            mods.when(net.minecraftforge.fml.ModList::get).thenReturn(installed);
            String price = "987654321987654321987654321";
            assertEquals(1, dispatcher.execute("primordial set_value gas " + gas.id + " " + price, console));
            assertEquals(1, audit.entries.size());
            var entry = audit.entries.get(0);
            assertEquals("GAS", entry.get("kind").getAsString());
            assertEquals("EMC/mB", entry.get("unit").getAsString());
            assertEquals("INHERIT_NATIVE", entry.getAsJsonObject("before").get("mode").getAsString());
            assertEquals(price, entry.getAsJsonObject("after").get("price").getAsString());
        }
    }

    @Test void invalidFractionCommandLeavesNoSuccessRecordOrPriceChange() throws Exception {
        assertEquals(0, dispatcher.execute("primordial set_value fluid minecraft:water 1/0", console));
        assertTrue(audit.entries.isEmpty());
        assertEquals(EmcAmount.parse("1000"), ValueManager.fluidSnapshot().get("minecraft:water"));
    }

    @Test void disabledModeKeepsItsPriceAndUnauthorizedChangesNeverRun() {
        var id = new ResourceLocation("minecraft:water");
        var player = mock(ServerPlayer.class);
        when(player.hasPermissions(2)).thenReturn(true);
        when(player.isCreative()).thenReturn(true);
        when(player.getName()).thenReturn(Component.literal("Admin\nquoted\"name"));
        when(player.getUUID()).thenReturn(UUID.randomUUID());
        assertTrue(PriceChangeAudit.change(player, PriceChangeAudit.Kind.FLUID, id,
                () -> ValueManager.setFluidMode(id, PriceMode.DISABLED)));
        var entry = audit.entries.get(0);
        assertEquals("Admin\nquoted\"name", entry.get("operator").getAsString());
        assertEquals("DISABLED", entry.getAsJsonObject("after").get("mode").getAsString());
        assertEquals("1000", entry.getAsJsonObject("after").get("price").getAsString());
        audit.entries.clear();
        when(player.hasPermissions(2)).thenReturn(false);
        var save = mock(java.util.function.BooleanSupplier.class);
        assertFalse(PriceChangeAudit.change(player, PriceChangeAudit.Kind.FLUID, id, save));
        verifyNoInteractions(save);
        assertTrue(audit.entries.isEmpty());
    }
}
