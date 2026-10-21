/**
 * 【文件职责】提供服务端权威护符样本与筛选菜单。
 * 【变更记录 #1】2026-09-16 19:41:50（第 1 次备注 · 1.0.0-review-ready）
 * - 删除：移除玩家缺少筛选编辑入口的限制。
 * - 新增：手持实例校验、只读取样、规则增删、状态修订与回包。
 * - 修改：所有库存点击禁止移动实物，样本最大8KiB；候选与所选规则由服务端提供。
 * - 用途：重开菜单读取护符NBT，切换手持物后拒绝修改另一把工具。
 */
package com.primordial.menu;

import com.primordial.item.*;
import com.primordial.network.PrimordialNetwork;
import com.primordial.network.S2CCharmFilterPacket;
import com.primordial.registry.ModMenus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.List;

/** 绑定打开时的手持护符；所有库存交互只取样，禁止转移、数字键交换和丢弃实物。 */
@org.anti_ad.mc.ipn.api.IPNIgnore
public final class BlackholeCharmMenu extends AbstractContainerMenu {
    private final Player owner;
    private final InteractionHand hand;
    private final ItemStack charm;
    private final ItemStackHandler sample = new ItemStackHandler(1);
    private CompoundTag state = new CompoundTag();
    private final List<String> selected = new ArrayList<>();
    private int type;
    private boolean blacklist;
    private int revision;
    private boolean initial = true;
    private boolean accepted = true;
    private List<String> clientCandidates = List.of();

    public BlackholeCharmMenu(int id, Inventory inventory, InteractionHand hand) {
        super(ModMenus.BLACKHOLE_CHARM.get(), id);
        this.owner = inventory.player; this.hand = hand; this.charm = owner.getItemInHand(hand);
        state = CharmFilterEditor.snapshot(charm);
        addSlot(new SlotItemHandler(sample, 0, 14, 42) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, 9 + row * 9 + col, 45 + col * 18, 148 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 45 + col * 18, 206));
    }
    @Override public boolean stillValid(Player player) {
        return player == owner && player.isAlive() && player.getItemInHand(hand) == charm
                && charm.getItem() instanceof BlackholeCharmItem;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean canDragTo(Slot slot) { return false; }
    @Override public void clicked(int index, int button, ClickType click, Player player) {
        if (!(owner instanceof ServerPlayer) || !stillValid(player)
                || (click != ClickType.PICKUP && click != ClickType.QUICK_MOVE)) return;
        if (index == 0 && button == 1) { sample.setStackInSlot(0, ItemStack.EMPTY); selected.clear(); send(); }
        else if (index > 0 && index < slots.size() && button == 0) {
            ItemStack source = slots.get(index).getItem();
            accepted = !source.isEmpty() && (!source.hasTag() || source.getTag().sizeInBytes() <= 8192);
            if (accepted) { sample.setStackInSlot(0, source.copyWithCount(1)); selected.clear(); }
            send();
        }
    }
    /** 有界动作只编辑服务端样本；自定义 VarInt 包避免原版按钮的单字节截断。 */
    @Override public boolean clickMenuButton(Player player, int action) {
        if (!(owner instanceof ServerPlayer) || !stillValid(player)) return false;
        accepted = true;
        if (action >= 0 && action <= 2) { type = action; selected.clear(); }
        else if (action == 3) blacklist = !blacklist;
        else if (action == 4) accepted = CharmFilterEditor.add(charm, sample.getStackInSlot(0), type, blacklist, selected);
        else if (action >= 1000 && action < 1256) accepted = CharmFilterEditor.remove(charm, type, blacklist, action - 1000);
        else if (action >= 2000 && action < 2128) {
            List<String> candidates = candidates(); int index = action - 2000;
            if (index >= candidates.size()) return false;
            String value = candidates.get(index);
            if (!selected.remove(value)) selected.add(value);
        } else return false;
        owner.getInventory().setChanged();
        send(); return true;
    }
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (initial && owner instanceof ServerPlayer) { initial = false; send(); }
    }
    private void send() {
        revision++;
        state = CharmFilterEditor.snapshot(charm);
        if (owner instanceof ServerPlayer player) {
            // 超旧版巨大列表只回显为空，删除/重新编辑不允许放大网络载荷。
            CompoundTag snapshot = state.sizeInBytes() <= CharmFilterEditor.MAX_BYTES ? state.copy() : new CompoundTag();
            PrimordialNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new S2CCharmFilterPacket(containerId, revision, type, blacklist, snapshot,
                            sample().copy(), candidates(), List.copyOf(selected), accepted));
        }
        super.broadcastChanges();
    }
    public void accept(S2CCharmFilterPacket packet) {
        if (packet.containerId() != containerId) return;
        state = packet.rules(); type = packet.type(); blacklist = packet.blacklist();
        selected.clear(); selected.addAll(packet.selected()); accepted = packet.accepted(); revision = packet.revision();
        sample.setStackInSlot(0, packet.sample()); clientCandidates = packet.candidates();
    }
    public int type() { return type; }
    public boolean blacklist() { return blacklist; }
    public int revision() { return revision; }
    public boolean accepted() { return accepted; }
    public ItemStack sample() { return sample.getStackInSlot(0); }
    public ListTag rules() { return CharmFilterEditor.rules(state, type, blacklist); }
    public boolean selected(String value) { return selected.contains(value); }
    public List<String> candidates() {
        if (!(owner instanceof ServerPlayer)) return clientCandidates;
        if (type == 1) return new ArrayList<>(CharmNbtFields.fields(sample().getTag()).keySet());
        if (type == 2) return sample().getTags().map(tag -> tag.location().toString()).sorted().limit(128).toList();
        return List.of();
    }
}
