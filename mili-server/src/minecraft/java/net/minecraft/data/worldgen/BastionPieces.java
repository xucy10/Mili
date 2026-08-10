package net.minecraft.data.worldgen;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

public class BastionPieces {
    public static final ResourceKey<StructureTemplatePool> START = Pools.createKey("bastion/starts");

    public static void bootstrap(BootstrapContext<StructureTemplatePool> context) {
        HolderGetter<StructureProcessorList> holderGetter = context.lookup(Registries.PROCESSOR_LIST);
        Holder<StructureProcessorList> orThrow = holderGetter.getOrThrow(ProcessorLists.BASTION_GENERIC_DEGRADATION);
        HolderGetter<StructureTemplatePool> holderGetter1 = context.lookup(Registries.TEMPLATE_POOL);
        Holder<StructureTemplatePool> orThrow1 = holderGetter1.getOrThrow(Pools.EMPTY);
        context.register(
            START,
            new StructureTemplatePool(
                orThrow1,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/units/air_base", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/air_base", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/big_air_full", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/bridge/starting_pieces/entrance_base", orThrow), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        BastionHousingUnitsPools.bootstrap(context);
        BastionHoglinStablePools.bootstrap(context);
        BastionTreasureRoomPools.bootstrap(context);
        BastionBridgePools.bootstrap(context);
        BastionSharedPools.bootstrap(context);
    }
}
