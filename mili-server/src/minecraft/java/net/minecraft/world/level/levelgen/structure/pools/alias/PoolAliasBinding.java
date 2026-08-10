package net.minecraft.world.level.levelgen.structure.pools.alias;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

public interface PoolAliasBinding {
    Codec<PoolAliasBinding> CODEC = BuiltInRegistries.POOL_ALIAS_BINDING_TYPE.byNameCodec().dispatch(PoolAliasBinding::codec, Function.identity());

    void forEachResolved(RandomSource random, BiConsumer<ResourceKey<StructureTemplatePool>, ResourceKey<StructureTemplatePool>> structurePoolKey);

    Stream<ResourceKey<StructureTemplatePool>> allTargets();

    static DirectPoolAlias direct(String alias, String target) {
        return direct(Pools.createKey(alias), Pools.createKey(target));
    }

    static DirectPoolAlias direct(ResourceKey<StructureTemplatePool> alias, ResourceKey<StructureTemplatePool> target) {
        return new DirectPoolAlias(alias, target);
    }

    static RandomPoolAlias random(String alias, WeightedList<String> targets) {
        WeightedList.Builder<ResourceKey<StructureTemplatePool>> builder = WeightedList.builder();
        targets.unwrap().forEach(weighted -> builder.add(Pools.createKey(weighted.value()), weighted.weight()));
        return random(Pools.createKey(alias), builder.build());
    }

    static RandomPoolAlias random(ResourceKey<StructureTemplatePool> alias, WeightedList<ResourceKey<StructureTemplatePool>> targets) {
        return new RandomPoolAlias(alias, targets);
    }

    static RandomGroupPoolAlias randomGroup(WeightedList<List<PoolAliasBinding>> groups) {
        return new RandomGroupPoolAlias(groups);
    }

    MapCodec<? extends PoolAliasBinding> codec();
}
