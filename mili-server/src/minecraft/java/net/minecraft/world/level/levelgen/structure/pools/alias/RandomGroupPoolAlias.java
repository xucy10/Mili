package net.minecraft.world.level.levelgen.structure.pools.alias;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

public record RandomGroupPoolAlias(WeightedList<List<PoolAliasBinding>> groups) implements PoolAliasBinding {
    static MapCodec<RandomGroupPoolAlias> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(WeightedList.nonEmptyCodec(Codec.list(PoolAliasBinding.CODEC)).fieldOf("groups").forGetter(RandomGroupPoolAlias::groups))
            .apply(instance, RandomGroupPoolAlias::new)
    );

    @Override
    public void forEachResolved(RandomSource random, BiConsumer<ResourceKey<StructureTemplatePool>, ResourceKey<StructureTemplatePool>> structurePoolKey) {
        this.groups.getRandom(random).ifPresent(list -> list.forEach(poolAliasBinding -> poolAliasBinding.forEachResolved(random, structurePoolKey)));
    }

    @Override
    public Stream<ResourceKey<StructureTemplatePool>> allTargets() {
        return this.groups.unwrap().stream().flatMap(weighted -> weighted.value().stream()).flatMap(PoolAliasBinding::allTargets);
    }

    @Override
    public MapCodec<RandomGroupPoolAlias> codec() {
        return CODEC;
    }
}
