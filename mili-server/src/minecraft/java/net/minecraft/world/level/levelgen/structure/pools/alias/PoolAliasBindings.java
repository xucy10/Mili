package net.minecraft.world.level.levelgen.structure.pools.alias;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

public class PoolAliasBindings {
    public static MapCodec<? extends PoolAliasBinding> bootstrap(Registry<MapCodec<? extends PoolAliasBinding>> registry) {
        Registry.register(registry, "random", RandomPoolAlias.CODEC);
        Registry.register(registry, "random_group", RandomGroupPoolAlias.CODEC);
        return Registry.register(registry, "direct", DirectPoolAlias.CODEC);
    }

    public static void registerTargetsAsPools(
        BootstrapContext<StructureTemplatePool> context, Holder<StructureTemplatePool> pool, List<PoolAliasBinding> poolAliasBindings
    ) {
        poolAliasBindings.stream()
            .flatMap(PoolAliasBinding::allTargets)
            .map(resourceKey -> resourceKey.identifier().getPath())
            .forEach(
                string -> Pools.register(
                    context,
                    string,
                    new StructureTemplatePool(pool, List.of(Pair.of(StructurePoolElement.single(string), 1)), StructureTemplatePool.Projection.RIGID)
                )
            );
    }
}
