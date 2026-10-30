package com.primordial.neoforge.charm;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.blackhole.NeoForgeRecoveryKnowledge;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.neoforge.trade.NeoForgeResourceTrades;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/** Only real, active Curios charm slots absorb drops; holding the item opens its Forge-layout editor. */
@EventBusSubscriber(modid = PrimordialNeoForgeMod.MOD_ID)
public final class NeoForgeBlackholeCharmItem extends Item implements ICurioItem {
    private static final Map<MinecraftServer, Map<UUID, Budget>> BUDGETS = new HashMap<>();
    private static final class Budget {
        int tick, entities, items;
        boolean busy, warned;
        Budget(int tick) { this.tick = tick; }
    }

    public NeoForgeBlackholeCharmItem(Properties properties) { super(properties.stacksTo(1)); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) serverPlayer.openMenu(new SimpleMenuProvider(
                (id, inventory, owner) -> new NeoForgeBlackholeCharmMenu(id, inventory, hand),
                Component.translatable("screen.primordial.charm.title")), buf -> buf.writeEnum(hand));
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> lines, TooltipFlag flags) {
        lines.add(Component.translatable("tooltip.primordial.charm_filters"));
    }

    @Override public boolean canEquip(SlotContext context, ItemStack stack) {
        return context != null && "charm".equals(context.identifier());
    }

    @Override public void curioTick(SlotContext context, ItemStack charm) {
        if (!(context != null && context.entity() instanceof ServerPlayer player) || !equipped(context, charm, player)) return;
        MinecraftServer server = player.server;
        int interval = NeoForgeServerConfig.BLACKHOLE_CHARM_SCAN_INTERVAL_TICKS.get();
        if (Math.floorMod(player.level().getGameTime() + player.getId(), interval) != 0) return;
        Budget budget = BUDGETS.computeIfAbsent(server, ignored -> new HashMap<>())
                .computeIfAbsent(player.getUUID(), ignored -> new Budget(server.getTickCount()));
        if (budget.busy) return;
        if (budget.tick != server.getTickCount()) { budget.tick = server.getTickCount(); budget.entities = budget.items = 0; }
        int entityLimit = NeoForgeServerConfig.BLACKHOLE_CHARM_MAX_ENTITIES_PER_TICK.get();
        int itemLimit = NeoForgeServerConfig.BLACKHOLE_CHARM_MAX_ITEMS_PER_TICK.get();
        if (budget.entities >= entityLimit || budget.items >= itemLimit || !NeoForgeRecoveryKnowledge.available(server, player.getUUID())) return;
        budget.busy = true;
        try {
            var filters = NeoForgeCharmFilters.data(charm);
            for (ItemEntity entity : player.serverLevel().getEntitiesOfClass(ItemEntity.class,
                    player.getBoundingBox().inflate(NeoForgeServerConfig.BLACKHOLE_CHARM_RADIUS.get()), e -> !e.isRemoved())) {
                if (budget.entities >= entityLimit || budget.items >= itemLimit || !equipped(context, charm, player)
                        || !NeoForgeCharmFilters.data(charm).equals(filters)) break;
                ItemStack source = entity.getItem();
                ItemStack expected = source.copy();
                if (source.isEmpty() || !safeToConsume(source) || !NeoForgeCharmFilters.accepts(charm, source, player.registryAccess())) continue;
                int requested = Math.min(source.getCount(), itemLimit - budget.items);
                long actual = NeoForgeResourceTrades.reclaimItem(server, player.getUUID(), source, requested, units -> {
                    if (!equipped(context, charm, player) || !NeoForgeCharmFilters.data(charm).equals(filters)
                            || entity.isRemoved() || entity.level() != player.level() || !ItemStack.matches(entity.getItem(), expected)
                            || !player.getBoundingBox().inflate(NeoForgeServerConfig.BLACKHOLE_CHARM_RADIUS.get()).intersects(entity.getBoundingBox())
                            || !safeToConsume(entity.getItem())) return 0;
                    int count = (int) units;
                    if (count >= source.getCount()) entity.discard();
                    else { entity.setItem(source.copyWithCount(source.getCount() - count)); entity.setPickUpDelay(10); }
                    return count;
                });
                if (actual > 0) { budget.entities++; budget.items += (int) actual; }
            }
            budget.warned = false;
        } catch (RuntimeException | LinkageError failure) {
            // A failed provider/quote cannot consume the next drop; unknown delivery is never retried in this callback.
            if (!budget.warned) com.mojang.logging.LogUtils.getLogger().warn("[Primordial] Charm scan paused for {}", player.getUUID(), failure);
            budget.warned = true;
        } finally { budget.busy = false; }
    }

    private static boolean equipped(SlotContext context, ItemStack charm, ServerPlayer player) {
        if (context.cosmetic() || context.index() < 0 || !"charm".equals(context.identifier()) || !player.isAlive() || !player.server.isSameThread()
                || player.server.getPlayerList().getPlayer(player.getUUID()) != player) return false;
        return CuriosApi.getCuriosInventory(player).map(inventory -> inventory.isSlotActive("charm", context.index())
                && inventory.getStacksHandler("charm").map(handler -> context.index() >= 0
                && context.index() < handler.getStacks().getSlots()
                && handler.getStacks().getStackInSlot(context.index()) == charm).orElse(false)).orElse(false);
    }

    /** Automated recycling leaves storage contents and charged containers available for manual handling. */
    private static boolean safeToConsume(ItemStack stack) {
        if (stack.has(DataComponents.CONTAINER_LOOT) || stack.has(DataComponents.BLOCK_ENTITY_DATA)) return false;
        var contents = stack.get(DataComponents.CONTAINER);
        if (contents != null && contents.nonEmptyStream().findAny().isPresent()) return false;
        var bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null && !bundle.isEmpty()) return false;
        var inventory = stack.getCapability(Capabilities.ItemHandler.ITEM);
        if (inventory != null) for (int i = 0; i < inventory.getSlots(); i++) if (!inventory.getStackInSlot(i).isEmpty()) return false;
        ItemStack base = new ItemStack(stack.getItem());
        var energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        if (energy != null) {
            var defaults = base.getCapability(Capabilities.EnergyStorage.ITEM);
            if (defaults == null || energy.getEnergyStored() != defaults.getEnergyStored()) return false;
        }
        var fluids = stack.getCapability(Capabilities.FluidHandler.ITEM);
        if (fluids != null) {
            var defaults = base.getCapability(Capabilities.FluidHandler.ITEM);
            if (defaults == null || defaults.getTanks() != fluids.getTanks()) return false;
            for (int tank = 0; tank < fluids.getTanks(); tank++) {
                var actual = fluids.getFluidInTank(tank);
                var expected = defaults.getFluidInTank(tank);
                if (actual.getAmount() != expected.getAmount() || actual.isEmpty() != expected.isEmpty()
                        || !actual.isEmpty() && !net.neoforged.neoforge.fluids.FluidStack.isSameFluidSameComponents(actual, expected)) return false;
            }
        }
        return true;
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { BUDGETS.remove(event.getServer()); }
}
