package net.minecraft.world.level.levelgen.structure.pools.alias;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

public record RandomPoolAlias(ResourceKey<StructureTemplatePool> alias, WeightedList<ResourceKey<StructureTemplatePool>> targets) implements PoolAliasBinding {
    static MapCodec<RandomPoolAlias> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                ResourceKey.codec(Registries.TEMPLATE_POOL).fieldOf("alias").forGetter(RandomPoolAlias::alias),
                WeightedList.nonEmptyCodec(ResourceKey.codec(Registries.TEMPLATE_POOL)).fieldOf("targets").forGetter(RandomPoolAlias::targets)
            )
            .apply(instance, RandomPoolAlias::new)
    );

    @Override
    public void forEachResolved(RandomSource random, BiConsumer<ResourceKey<StructureTemplatePool>, ResourceKey<StructureTemplatePool>> structurePoolKey) {
        this.targets.getRandom(random).ifPresent(resourceKey -> structurePoolKey.accept(this.alias, (ResourceKey<StructureTemplatePool>)resourceKey));
    }

    @Override
    public Stream<ResourceKey<StructureTemplatePool>> allTargets() {
        return this.targets.unwrap().stream().map(Weighted::value);
    }

    @Override
    public MapCodec<RandomPoolAlias> codec() {
        return CODEC;
    }
}
