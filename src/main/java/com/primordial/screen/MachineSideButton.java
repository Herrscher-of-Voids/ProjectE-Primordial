/**
 * 【文件职责】绘制机器展开图中的单个可选面，使用目标方块模型对应面的纹理。
 * 【变更记录 #1】2026-10-02 21:47:28（第 1 次备注 · 1.2.0-relative-sides）
 * - 删除：无。
 * - 新增：面纹理、着色、选中描边、键盘焦点和无独立面纹理时的提示。
 * - 修改：模型无公开面或读取失败时回退到粒子纹理/问号，不影响端口查询。
 * - 用途：由 MachineTargetPicker 创建六面按钮；不持有或修改机器库存。
 */
/**
 * 【变更记录 #2】2026-10-02 22:18:22（第 2 次备注 · 1.2.0-neighbor-side-icons）
 * - 删除：读取被配置机器各面模型四边形及面着色的实现。
 * - 新增：读取对应方向相邻方块的物品图标、名称、世界方向和空气/未加载提示。
 * - 修改：每帧检查相邻方块状态，仅在状态变化时更新图标；无物品形态时使用该邻居通用纹理。
 * - 用途：展开图表达机器周围放置了什么方块，点击仍选择被配置机器的原实际侧面。
 */
package com.primordial.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.Supplier;

final class MachineSideButton extends Button {
    private TextureAtlasSprite sprite;
    private ItemStack icon = ItemStack.EMPTY;
    private final Supplier<BlockState> neighborState;
    private final Direction side;
    private BlockState lastState;
    private boolean initialized;
    private final boolean selected;

    MachineSideButton(int x, int y, Component name, Supplier<BlockState> neighborState,
                      Direction side, boolean selected, OnPress action) {
        super(x, y, 30, 34, name, action, DEFAULT_NARRATION);
        this.selected = selected;
        this.neighborState = neighborState;
        this.side = side;
        updateNeighbor();
    }

    /** 只读取客户端已加载方块，不加载区块，也不复制方块实体库存或能量 NBT。 */
    private void updateNeighbor() {
        BlockState state = neighborState.get();
        if (initialized && state == lastState) return;
        initialized = true;
        lastState = state;
        icon = ItemStack.EMPTY;
        sprite = null;
        Component neighborName = Component.translatable(state == null
                ? "screen.primordial.side.neighbor_unavailable" : "screen.primordial.side.neighbor_air");
        if (state != null && !state.isAir()) {
            icon = new ItemStack(state.getBlock().asItem());
            neighborName = icon.isEmpty() ? state.getBlock().getName() : icon.getHoverName();
            if (icon.isEmpty()) {
                try {
                    sprite = Minecraft.getInstance().getBlockRenderer().getBlockModel(state).getParticleIcon();
                } catch (RuntimeException ignored) {
                    // 无物品形态的特殊模型读取失败时显示占位符，名称和选面功能仍保留。
                }
            }
        }
        Component hint = Component.translatable("screen.primordial.side.neighbor_hint", getMessage(),
                Component.translatable("screen.primordial.side." + side.getName()), neighborName)
                .append("\n").append(Component.translatable("screen.primordial.side.face_hint", getMessage()));
        setTooltip(Tooltip.create(hint));
    }

    @Override
    protected void renderWidget(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        updateNeighbor();
        int x = getX(), y = getY();
        gg.fill(x, y, x + width, y + height, isHoveredOrFocused() ? 0xFF405363 : 0xFF202B35);
        gg.renderOutline(x, y, width, height, selected ? 0xFF66E5BB : isHoveredOrFocused() ? 0xFFFFFFFF : 0xFF657583);
        if (!icon.isEmpty()) {
            gg.renderItem(icon, x + 7, y + 3);
        } else if (sprite != null) {
            gg.blit(x + 7, y + 3, 0, 16, 16, sprite);
        } else gg.drawCenteredString(Minecraft.getInstance().font,
                lastState != null && lastState.isAir() ? "—" : "?", x + 15, y + 6, 0xFFFFFF);
        gg.drawCenteredString(Minecraft.getInstance().font, getMessage(), x + 15, y + 22,
                selected ? 0xFF66E5BB : 0xFFFFFFFF);
    }
}
