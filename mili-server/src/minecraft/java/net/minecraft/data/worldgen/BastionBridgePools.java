package net.minecraft.data.worldgen;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

public class BastionBridgePools {
    public static void bootstrap(BootstrapContext<StructureTemplatePool> context) {
        HolderGetter<StructureProcessorList> holderGetter = context.lookup(Registries.PROCESSOR_LIST);
        Holder<StructureProcessorList> orThrow = holderGetter.getOrThrow(ProcessorLists.ENTRANCE_REPLACEMENT);
        Holder<StructureProcessorList> orThrow1 = holderGetter.getOrThrow(ProcessorLists.BASTION_GENERIC_DEGRADATION);
        Holder<StructureProcessorList> orThrow2 = holderGetter.getOrThrow(ProcessorLists.BRIDGE);
        Holder<StructureProcessorList> orThrow3 = holderGetter.getOrThrow(ProcessorLists.RAMPART_DEGRADATION);
        HolderGetter<StructureTemplatePool> holderGetter1 = context.lookup(Registries.TEMPLATE_POOL);
        Holder<StructureTemplatePool> orThrow4 = holderGetter1.getOrThrow(Pools.EMPTY);
        Pools.register(
            context,
            "bastion/bridge/starting_pieces",
            new StructureTemplatePool(
                orThrow4,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/bridge/starting_pieces/entrance", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/bridge/starting_pieces/entrance_face", orThrow1), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/bridge/bridge_pieces",
            new StructureTemplatePool(
                orThrow4,
                ImmutableList.of(Pair.of(StructurePoolElement.single("bastion/bridge/bridge_pieces/bridge", orThrow2), 1)),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/bridge/legs",
            new StructureTemplatePool(
                orThrow4,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/bridge/legs/leg_0", orThrow1), 1),
                    Pair.of(StructurePoolElement.single("bastion/bridge/legs/leg_1", orThrow1), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/bridge/walls",
            new StructureTemplatePool(
                orThrow4,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/bridge/walls/wall_base_0", orThrow3), 1),
                    Pair.of(StructurePoolElement.single("bastion/bridge/walls/wall_base_1", orThrow3), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/bridge/ramparts",
            new StructureTemplatePool(
                orThrow4,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/bridge/ramparts/rampart_0", orThrow3), 1),
                    Pair.of(StructurePoolElement.single("bastion/bridge/ramparts/rampart_1", orThrow3), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/bridge/rampart_plates",
            new StructureTemplatePool(
                orThrow4,
                ImmutableList.of(Pair.of(StructurePoolElement.single("bastion/bridge/rampart_plates/plate_0", orThrow3), 1)),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/bridge/connectors",
            new StructureTemplatePool(
                orThrow4,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/bridge/connectors/back_bridge_top", orThrow1), 1),
                    Pair.of(StructurePoolElement.single("bastion/bridge/connectors/back_bridge_bottom", orThrow1), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
    }
}
