package com.primordial.neoforge.transmutation;

import java.util.List;

import com.mojang.serialization.Lifecycle;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeTransmutationRegistryGameTests {
    private NeoForgeTransmutationRegistryGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void rejectedForeignEnchantmentCannotPoisonValidPlayerItemSaving(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var smite = registries.registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SMITE);
        var foreign = new MappedRegistry<Enchantment>(Registries.ENCHANTMENT, Lifecycle.stable());
        var foreignSmite = foreign.register(Enchantments.SMITE, smite.value(), RegistrationInfo.BUILT_IN);
        foreign.freeze();
        var foreignRegistries = new RegistryAccess.ImmutableRegistryAccess(List.of(foreign)).freeze();
        check(foreignSmite != smite && foreignSmite.equals(smite),
                "The fixture must have equal enchantment keys with different registry owners");
        ItemStack foreignBook = book(foreignSmite, 4);
        var original = foreignBook.save(foreignRegistries);

        NeoForgeTransmutationNbtPolicy.normalize(ItemInfo.fromStack(foreignBook), 7, registries);

        // Saving the legal item first would warm the shared encoder cache and hide the failure.
        ItemStack serverBook = book(smite, 4);
        var saved = serverBook.save(registries);
        ItemStack restored = ItemStack.parse(registries, saved).orElseThrow();
        check(restored.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(smite) == 4,
                "Rejecting a foreign component must not prevent an unrelated valid Smite IV book from saving");
        check(original.equals(foreignBook.save(foreignRegistries))
                        && foreignBook.get(DataComponents.STORED_ENCHANTMENTS).keySet().iterator().next() == foreignSmite,
                "Normalizing foreign data must leave the source item and its registry owner unchanged");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void nativePersistentBookAndOutputKeepServerEnchantmentsAfterSaveReload(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var smite = registries.registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SMITE);
        ItemStack source = book(smite, 5);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("Preserved book"));
        source.set(DataComponents.STORED_ENCHANTMENTS, source.get(DataComponents.STORED_ENCHANTMENTS).withTooltip(false));
        var before = source.save(registries);
        ItemStack persistent = IEMCProxy.INSTANCE.getPersistentInfo(ItemInfo.fromStack(source)).createStack();
        ItemStack output = NeoForgeTransmutationNbtPolicy.safeOutput(persistent);
        ItemStack restored = ItemStack.parse(registries, output.save(registries)).orElseThrow();
        check(ItemStack.isSameItemSameComponents(source, restored),
                "The native persistence hook and output policy must retain stored enchantments, tooltip visibility and name");
        check(restored.get(DataComponents.STORED_ENCHANTMENTS).keySet().iterator().next() == smite,
                "Restored output must use the authoritative server enchantment holder");
        check(before.equals(source.save(registries)), "Producing a repeatable book must not mutate the source");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void foreignHoverItemCannotPoisonValidNestedComponentSaving(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var smite = registries.registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SMITE);
        var foreign = new MappedRegistry<Enchantment>(Registries.ENCHANTMENT, Lifecycle.stable());
        var foreignSmite = foreign.register(Enchantments.SMITE, smite.value(), RegistrationInfo.BUILT_IN);
        foreign.freeze();
        var foreignRegistries = new RegistryAccess.ImmutableRegistryAccess(List.of(foreign)).freeze();
        ItemStack foreignPaper = namedWithHoverBook(foreignSmite);
        var original = foreignPaper.save(foreignRegistries);

        NeoForgeTransmutationNbtPolicy.normalize(ItemInfo.fromStack(foreignPaper), 7, registries);

        ItemStack serverPaper = namedWithHoverBook(smite);
        ItemStack restored = ItemStack.parse(registries, serverPaper.save(registries)).orElseThrow();
        check(ItemStack.isSameItemSameComponents(serverPaper, restored),
                "A rejected nested holder must not poison serialization of the equivalent valid custom name");
        var restoredBook = restored.get(DataComponents.CUSTOM_NAME).getStyle().getHoverEvent()
                .getValue(HoverEvent.Action.SHOW_ITEM).getItemStack();
        var stored = restoredBook.get(DataComponents.STORED_ENCHANTMENTS);
        check(stored.getLevel(smite) == 3 && stored.keySet().iterator().next() == smite,
                "Nested show-item data must retain the server's Smite III enchantment after saving and loading");
        check(original.equals(foreignPaper.save(foreignRegistries)),
                "Rejecting a foreign holder nested inside text must not mutate the source text or item");
        helper.succeed();
    }

    private static ItemStack namedWithHoverBook(Holder<Enchantment> enchantment) {
        ItemStack paper = new ItemStack(Items.PAPER);
        paper.set(DataComponents.CUSTOM_NAME, Component.literal("Nested enchanted book").withStyle(style ->
                style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM,
                        new HoverEvent.ItemStackInfo(book(enchantment, 3))))));
        return paper;
    }

    private static ItemStack book(Holder<Enchantment> enchantment, int level) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(enchantment, level);
        book.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
        return book;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
