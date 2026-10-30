package com.primordial.api;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** Stable author API shared with Forge; NeoForge keeps its platform implementation separate. */
public final class MediaKnowledgeApi {
    public enum Media { FLUID, GAS }
    private MediaKnowledgeApi() {}

    public static boolean validResource(Media media, ResourceLocation id) {
        return com.primordial.neoforge.api.MediaKnowledgeApi.validResource(platform(media), id);
    }

    /** Reports explicit per-resource learning, excluding category-certificate authorization. */
    public static boolean hasLearned(MinecraftServer server, UUID player, Media media, ResourceLocation id) {
        return com.primordial.neoforge.api.MediaKnowledgeApi.hasLearned(server, player, platform(media), id);
    }

    /** Author-only entry point; no EMC or resources are granted. Requires the server thread. */
    public static boolean learn(MinecraftServer server, UUID player, Media media, ResourceLocation id) {
        return com.primordial.neoforge.api.MediaKnowledgeApi.learn(server, player, platform(media), id);
    }

    /** Revocation overrides category certificates; a later successful sale or grant can restore it. */
    public static boolean forget(MinecraftServer server, UUID player, Media media, ResourceLocation id) {
        return com.primordial.neoforge.api.MediaKnowledgeApi.forget(server, player, platform(media), id);
    }

    /** Checks knowledge, stage and price authorization, without promising balance or delivery. */
    public static boolean isPurchaseUnlocked(MinecraftServer server, UUID player, Media media, ResourceLocation id) {
        return com.primordial.neoforge.api.MediaKnowledgeApi.isPurchaseUnlocked(server, player, platform(media), id);
    }

    private static com.primordial.neoforge.api.MediaKnowledgeApi.Media platform(Media media) {
        if (media == null) return null;
        return media == Media.GAS ? com.primordial.neoforge.api.MediaKnowledgeApi.Media.GAS
                : com.primordial.neoforge.api.MediaKnowledgeApi.Media.FLUID;
    }
}
