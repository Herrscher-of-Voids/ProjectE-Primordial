package com.primordial.neoforge.mixin;

import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractArrow.class)
public interface NeoForgeBlackholeArrowAccess {
    @Invoker("setPierceLevel")
    void primordial$setPierceLevel(byte level);
}
