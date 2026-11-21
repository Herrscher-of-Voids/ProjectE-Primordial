package com.primordial.neoforge.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import moze_intel.projecte.api.mapper.collector.IMappingCollector;
import moze_intel.projecte.config.CustomEMCParser;
import moze_intel.projecte.config.MappingConfig;
import moze_intel.projecte.emc.mappers.CustomEMCMapper;
import moze_intel.projecte.emc.mappers.OreBlacklistMapper;
import moze_intel.projecte.emc.mappers.RawMaterialsBlacklistMapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = {RawMaterialsBlacklistMapper.class, OreBlacklistMapper.class}, remap = false)
public abstract class ProjectECustomPriceBlacklistMixin {
    @WrapWithCondition(method = "addMappings", at = {
            @At(value = "INVOKE", target = "Lmoze_intel/projecte/api/mapper/collector/IMappingCollector;setValueBefore(Ljava/lang/Object;Ljava/lang/Comparable;)V"),
            @At(value = "INVOKE", target = "Lmoze_intel/projecte/api/mapper/collector/IMappingCollector;setValueAfter(Ljava/lang/Object;Ljava/lang/Comparable;)V")
    }, remap = false, require = 2)
    private boolean primordial$preserveCustomPrice(IMappingCollector<?, ?> collector, Object stack, Comparable<?> value) {
        return !MappingConfig.isEnabled(new CustomEMCMapper()) || !CustomEMCParser.currentEntries.entries().containsKey(stack);
    }
}
