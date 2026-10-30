package com.primordial.neoforge.equipment;

import java.util.List;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.test.NeoForgeTestPlayers;
import moze_intel.projecte.api.proxy.IEMCProxy;
import moze_intel.projecte.emc.FuelMapper;
import moze_intel.projecte.gameObjs.EnumCollectorTier;
import moze_intel.projecte.gameObjs.block_entities.CollectorMK1BlockEntity;
import moze_intel.projecte.gameObjs.container.CollectorMK1Container;
import moze_intel.projecte.gameObjs.registries.PEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeCollectorGameTests {
    private NeoForgeCollectorGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void immutableHolderFuelMapKeepsIndependentAdjacentChains(GameTestHelper helper) {
        var before = FuelMapper.getFuelMap();
        try {
            var supplied = HolderSet.direct(List.<Holder<Item>>of(new ItemStack(Items.COAL).getItemHolder(),
                    material("kerr").getItemHolder(), new ItemStack(Items.CHARCOAL).getItemHolder(), material("primordial").getItemHolder()));
            FuelMapper.setFuelMap(supplied);
            var fuels = FuelMapper.getFuelMap();
            check(supplied.size() == 4 && fuels.size() == 11, "Immutable caller fuel lists must not be changed or duplicated");
            check(fuels.get(0).value() == Items.COAL && fuels.get(1).value() == Items.CHARCOAL,
                    "Ordinary fuel order must be preserved");
            check(FuelMapper.getFuelUpgrade(new ItemStack(Items.COAL)).is(Items.CHARCOAL)
                    && FuelMapper.isStackMaxFuel(new ItemStack(Items.CHARCOAL))
                    && FuelMapper.getFuelUpgrade(new ItemStack(Items.CHARCOAL)).isEmpty(), "Ordinary fuel cannot cross into blackhole materials");
            for (int i = 0; i < NeoForgeBlackholeCollectorConversion.TIERS.size(); i++) {
                ItemStack input = material(NeoForgeBlackholeCollectorConversion.TIERS.get(i));
                ItemStack next = NeoForgeBlackholeCollectorConversion.next(input);
                check(FuelMapper.isStackFuel(input), "Every blackhole tier must be a native collector input");
                check(ItemStack.isSameItemSameComponents(FuelMapper.getFuelUpgrade(input), next), "Stack upgrade must be adjacent");
                Holder<Item> holder = FuelMapper.getFuelUpgrade(input.getItemHolder());
                check(next.isEmpty() ? holder == null : holder.is(next.getItemHolder()), "Native recipe viewer holder upgrade must match machine behavior");
                check(FuelMapper.isStackMaxFuel(input) == next.isEmpty(), "Only the final blackhole tier may be terminal");
            }
            FuelMapper.loadMap();
            check(FuelMapper.getFuelMap().size() >= 9 && FuelMapper.getFuelUpgrade(material("primordial")).is(material("stellar").getItem()),
                    "Native reload must restore the same adjacent chain");
        } finally { FuelMapper.setFuelMap(before); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void realCollectorChargesAdjacentDifferenceAndDoesNotRecycleOutput(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlockAndUpdate(position, PEBlocks.COLLECTOR.getBlock().defaultBlockState());
        var collector = (CollectorMK1BlockEntity) helper.getLevel().getBlockEntity(position);
        try {
            for (int i = 0; i < 8; i++) {
                String tier = NeoForgeBlackholeCollectorConversion.TIERS.get(i);
                ItemStack input = material(tier), next = NeoForgeBlackholeCollectorConversion.next(input);
                long inputValue = IEMCProxy.INSTANCE.getValue(input), outputValue = IEMCProxy.INSTANCE.getValue(next);
                check(inputValue == (3_733_899L << i) && outputValue == (3_733_899L << (i + 1)), "Material prices must preserve the exact original ladder");
                var aux = (IItemHandlerModifiable) collector.getAux();
                aux.setStackInSlot(0, input.copyWithCount(2)); aux.setStackInSlot(1, ItemStack.EMPTY);
                aux.setStackInSlot(2, material("kerr_newman"));
                long difference = outputValue - inputValue, buffer = difference + 100;
                var data = collector.saveWithoutMetadata(helper.getLevel().registryAccess());
                data.putLong("emc", buffer); data.putDouble("unprocessed_emc", 0);
                collector.loadAdditional(data, helper.getLevel().registryAccess());
                int sunlight = CollectorMK1BlockEntity.getSunLevel(helper.getLevel(), position);
                long generated = (long) (40L * (sunlight / 320F));
                CollectorMK1BlockEntity.tickServer(helper.getLevel(), position, collector.getBlockState(), collector);
                check(aux.getStackInSlot(0).getCount() == 1 && aux.getStackInSlot(1).getCount() == 1
                        && aux.getStackInSlot(1).is(next.getItem()) && aux.getStackInSlot(2).is(next.getItem()),
                        "The real collector must consume one input and replace a forged skip-tier lock with its adjacent output");
                check(collector.getStoredEmc() == buffer + generated - difference, "Native collector must charge exactly output minus input EMC");
                CollectorMK1BlockEntity.tickServer(helper.getLevel(), position, collector.getBlockState(), collector);
                check(aux.getStackInSlot(0).getCount() == 1 && aux.getStackInSlot(1).getCount() == 1,
                        "Output must stay available for extraction and the remaining input cannot upgrade without funds");
            }
            var aux = (IItemHandlerModifiable) collector.getAux();
            aux.setStackInSlot(0, material("kerr_newman")); aux.setStackInSlot(1, ItemStack.EMPTY);
            aux.setStackInSlot(2, material("primordial"));
            CollectorMK1BlockEntity.tickServer(helper.getLevel(), position, collector.getBlockState(), collector);
            check(aux.getStackInSlot(0).is(material("kerr_newman").getItem()) && aux.getStackInSlot(1).isEmpty()
                    && aux.getStackInSlot(2).isEmpty(), "Terminal material cannot turn a stale lock into a free or backward conversion");
        } finally { helper.getLevel().setBlockAndUpdate(position, Blocks.AIR.defaultBlockState()); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void originalCollectorCapacityGenerationAndMenuGuardsAreRetained(GameTestHelper helper) throws Exception {
        BlockPos position = helper.absolutePos(new BlockPos(1, 2, 1));
        Block[] blocks = { PEBlocks.COLLECTOR.getBlock(), PEBlocks.COLLECTOR_MK2.getBlock(), PEBlocks.COLLECTOR_MK3.getBlock() };
        var generation = CollectorMK1BlockEntity.class.getDeclaredField("emcGen"); generation.setAccessible(true);
        try {
            for (int i = 0; i < blocks.length; i++) {
                helper.getLevel().setBlockAndUpdate(position, blocks[i].defaultBlockState());
                var collector = (CollectorMK1BlockEntity) helper.getLevel().getBlockEntity(position);
                check(collector.getMaximumEmc() == EnumCollectorTier.values()[i].getStorage() * 100_000L
                        && generation.getLong(collector) == EnumCollectorTier.values()[i].getGenRate() * 10L,
                        "All native collector tiers must retain original scaled capacity and generation per second");
                helper.getLevel().setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
            }
            helper.getLevel().setBlockAndUpdate(position, blocks[0].defaultBlockState());
            var collector = (CollectorMK1BlockEntity) helper.getLevel().getBlockEntity(position);
            var aux = (IItemHandlerModifiable) collector.getAux(); aux.setStackInSlot(0, material("primordial"));
            var player = NeoForgeTestPlayers.login(helper.getLevel().getServer());
            try {
                player.teleportTo(helper.getLevel(), position.getX() + .5, position.getY(), position.getZ() + .5, 0, 0);
                var menu = new CollectorMK1Container(72, player.getInventory(), collector);
                check(!NeoForgeBlackholeCollectorConversion.selectNext(menu, player), "Unopened menus cannot change collector targets");
                player.containerMenu = menu;
                var inventoryBefore = java.util.stream.IntStream.range(0, player.getInventory().getContainerSize())
                        .mapToObj(slot -> player.getInventory().getItem(slot).copy()).toList();
                menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
                check(aux.getStackInSlot(0).getCount() == 1 && aux.getStackInSlot(2).is(material("stellar").getItem())
                        && java.util.stream.IntStream.range(0, inventoryBefore.size())
                        .allMatch(slot -> ItemStack.matches(inventoryBefore.get(slot), player.getInventory().getItem(slot))),
                        "Shift target selection must set only the adjacent ghost and preserve every real inventory item");
                helper.getLevel().setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
                check(!NeoForgeBlackholeCollectorConversion.selectNext(menu, player), "Removed collector menus must reject target changes");
                player.containerMenu = player.inventoryMenu;
            } finally { NeoForgeTestPlayers.logout(player); }
        } finally { helper.getLevel().setBlockAndUpdate(position, Blocks.AIR.defaultBlockState()); }
        helper.succeed();
    }

    private static ItemStack material(String tier) { return NeoForgeBlackholeCollectorConversion.material(tier); }
    private static void check(boolean success, String message) { if (!success) throw new GameTestAssertException(message); }
}
