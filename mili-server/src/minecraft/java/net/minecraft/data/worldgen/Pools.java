package net.minecraft.data.worldgen;

import com.google.common.collect.ImmutableList;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

public class Pools {
    public static final ResourceKey<StructureTemplatePool> EMPTY = createKey("empty");

    public static ResourceKey<StructureTemplatePool> createKey(Identifier location) {
        return ResourceKey.create(Registries.TEMPLATE_POOL, location);
    }

    public static ResourceKey<StructureTemplatePool> createKey(String name) {
        return createKey(Identifier.withDefaultNamespace(name));
    }

    public static ResourceKey<StructureTemplatePool> parseKey(String key) {
        return createKey(Identifier.parse(key));
    }

    public static void register(BootstrapContext<StructureTemplatePool> context, String name, StructureTemplatePool pool) {
        context.register(createKey(name), pool);
    }

    public static void bootstrap(BootstrapContext<StructureTemplatePool> context) {
        HolderGetter<StructureTemplatePool> holderGetter = context.lookup(Registries.TEMPLATE_POOL);
        Holder<StructureTemplatePool> orThrow = holderGetter.getOrThrow(EMPTY);
        context.register(EMPTY, new StructureTemplatePool(orThrow, ImmutableList.of(), StructureTemplatePool.Projection.RIGID));
        BastionPieces.bootstrap(context);
        PillagerOutpostPools.bootstrap(context);
        VillagePools.bootstrap(context);
        AncientCityStructurePieces.bootstrap(context);
        TrailRuinsStructurePools.bootstrap(context);
        TrialChambersStructurePools.bootstrap(context);
    }
}
