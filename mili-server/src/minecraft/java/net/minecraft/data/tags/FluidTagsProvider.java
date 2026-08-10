package net.minecraft.data.tags;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

public class FluidTagsProvider extends IntrinsicHolderTagsProvider<Fluid> {
    public FluidTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> provider) {
        super(output, Registries.FLUID, provider, fluid -> fluid.builtInRegistryHolder().key());
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        this.tag(FluidTags.WATER).add(Fluids.WATER, Fluids.FLOWING_WATER);
        this.tag(FluidTags.LAVA).add(Fluids.LAVA, Fluids.FLOWING_LAVA);
    }
}
