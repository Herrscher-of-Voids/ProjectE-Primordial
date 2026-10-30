package com.primordial.neoforge.knowledge;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import com.primordial.api.MediaKnowledgeApi;
import com.primordial.api.MediaKnowledgeApi.Media;
import com.primordial.neoforge.blackhole.NeoForgeRecoveryKnowledgeSavedData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;

/** Exercises the public Forge-compatible package, without loading Forge or optional mod classes. */
@GameTestHolder("primordial")
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class NeoForgeStableKnowledgeApiGameTests {
    @GameTest(template = "capability_proxy_empty", templateNamespace = "primordial")
    public static void stableAuthorApiSharesOfflineLedgerAndRejectsInvalidContexts(GameTestHelper helper)
            throws InterruptedException {
        var server = helper.getLevel().getServer();
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
        ResourceLocation water = ResourceLocation.parse("minecraft:water");
        check(MediaKnowledgeApi.validResource(Media.FLUID, water)
                        && !MediaKnowledgeApi.validResource(Media.FLUID, ResourceLocation.parse("minecraft:flowing_water"))
                        && !MediaKnowledgeApi.validResource(Media.GAS, ResourceLocation.parse("primordial:missing_chemical")),
                "The stable package must validate actual source fluids and reject fake optional chemicals");
        check(MediaKnowledgeApi.learn(server, owner, Media.FLUID, water)
                        && !MediaKnowledgeApi.learn(server, owner, Media.FLUID, water)
                        && MediaKnowledgeApi.hasLearned(server, owner, Media.FLUID, water)
                        && !MediaKnowledgeApi.hasLearned(server, other, Media.FLUID, water),
                "The Forge-compatible API must grant idempotent offline knowledge only to its owner");
        check(com.primordial.neoforge.api.MediaKnowledgeApi.hasLearned(server, owner,
                        com.primordial.neoforge.api.MediaKnowledgeApi.Media.FLUID, water)
                        && MediaKnowledgeApi.isPurchaseUnlocked(server, owner, Media.FLUID, water)
                        == com.primordial.neoforge.api.MediaKnowledgeApi.isPurchaseUnlocked(server, owner,
                                com.primordial.neoforge.api.MediaKnowledgeApi.Media.FLUID, water),
                "Both package entry points must observe the same authoritative ledger and purchase rules");
        check(MediaKnowledgeApi.forget(server, owner, Media.FLUID, water)
                        && !MediaKnowledgeApi.forget(server, owner, Media.FLUID, water)
                        && !MediaKnowledgeApi.isPurchaseUnlocked(server, owner, Media.FLUID, water),
                "Stable-package revocation must be idempotent and block subsequent purchases");
        var data = NeoForgeRecoveryKnowledgeSavedData.get(server);
        CompoundTag before = data.save(new CompoundTag(), server.registryAccess());
        AtomicBoolean rejected = new AtomicBoolean();
        Thread worker = new Thread(() -> rejected.set(
                !MediaKnowledgeApi.learn(server, owner, Media.FLUID, water)
                        && !MediaKnowledgeApi.forget(server, owner, Media.FLUID, water)
                        && !MediaKnowledgeApi.hasLearned(server, owner, Media.FLUID, water)
                        && !MediaKnowledgeApi.isPurchaseUnlocked(server, owner, Media.FLUID, water)), "stable-knowledge-api-test");
        worker.start();
        worker.join(5000);
        check(!worker.isAlive() && rejected.get()
                        && !MediaKnowledgeApi.validResource(null, water)
                        && !MediaKnowledgeApi.learn(server, owner, null, water)
                        && !MediaKnowledgeApi.forget(null, owner, Media.FLUID, water)
                        && !MediaKnowledgeApi.hasLearned(server, null, Media.FLUID, water)
                        && before.equals(data.save(new CompoundTag(), server.registryAccess())),
                "Invalid or off-thread public calls must not mutate the knowledge ledger");
        helper.succeed();
    }

    private static void check(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
