package net.minecraft.data.worldgen;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

public class BastionHoglinStablePools {
    public static void bootstrap(BootstrapContext<StructureTemplatePool> context) {
        HolderGetter<StructureProcessorList> holderGetter = context.lookup(Registries.PROCESSOR_LIST);
        Holder<StructureProcessorList> orThrow = holderGetter.getOrThrow(ProcessorLists.STABLE_DEGRADATION);
        Holder<StructureProcessorList> orThrow1 = holderGetter.getOrThrow(ProcessorLists.SIDE_WALL_DEGRADATION);
        HolderGetter<StructureTemplatePool> holderGetter1 = context.lookup(Registries.TEMPLATE_POOL);
        Holder<StructureTemplatePool> orThrow2 = holderGetter1.getOrThrow(Pools.EMPTY);
        Pools.register(
            context,
            "bastion/hoglin_stable/starting_pieces",
            new StructureTemplatePool(
                orThrow2,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/starting_pieces/starting_stairs_0", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/starting_pieces/starting_stairs_1", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/starting_pieces/starting_stairs_2", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/starting_pieces/starting_stairs_3", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/starting_pieces/starting_stairs_4", orThrow), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/hoglin_stable/mirrored_starting_pieces",
            new StructureTemplatePool(
                orThrow2,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/starting_pieces/stairs_0_mirrored", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/starting_pieces/stairs_1_mirrored", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/starting_pieces/stairs_2_mirrored", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/starting_pieces/stairs_3_mirrored", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/starting_pieces/stairs_4_mirrored", orThrow), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/hoglin_stable/wall_bases",
            new StructureTemplatePool(
                orThrow2,
                ImmutableList.of(Pair.of(StructurePoolElement.single("bastion/hoglin_stable/walls/wall_base", orThrow), 1)),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/hoglin_stable/walls",
            new StructureTemplatePool(
                orThrow2,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/walls/side_wall_0", orThrow1), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/walls/side_wall_1", orThrow1), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/hoglin_stable/stairs",
            new StructureTemplatePool(
                orThrow2,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/stairs/stairs_1_0", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/stairs/stairs_1_1", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/stairs/stairs_1_2", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/stairs/stairs_1_3", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/stairs/stairs_1_4", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/stairs/stairs_2_0", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/stairs/stairs_2_1", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/stairs/stairs_2_2", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/stairs/stairs_2_3", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/stairs/stairs_2_4", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/stairs/stairs_3_0", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/stairs/stairs_3_1", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/stairs/stairs_3_2", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/stairs/stairs_3_3", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/stairs/stairs_3_4", orThrow), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/hoglin_stable/small_stables/inner",
            new StructureTemplatePool(
                orThrow2,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/small_stables/inner_0", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/small_stables/inner_1", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/small_stables/inner_2", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/small_stables/inner_3", orThrow), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/hoglin_stable/small_stables/outer",
            new StructureTemplatePool(
                orThrow2,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/small_stables/outer_0", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/small_stables/outer_1", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/small_stables/outer_2", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/small_stables/outer_3", orThrow), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/hoglin_stable/large_stables/inner",
            new StructureTemplatePool(
                orThrow2,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/large_stables/inner_0", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/large_stables/inner_1", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/large_stables/inner_2", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/large_stables/inner_3", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/large_stables/inner_4", orThrow), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/hoglin_stable/large_stables/outer",
            new StructureTemplatePool(
                orThrow2,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/large_stables/outer_0", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/large_stables/outer_1", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/large_stables/outer_2", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/large_stables/outer_3", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/large_stables/outer_4", orThrow), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/hoglin_stable/posts",
            new StructureTemplatePool(
                orThrow2,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/posts/stair_post", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/posts/end_post", orThrow), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/hoglin_stable/ramparts",
            new StructureTemplatePool(
                orThrow2,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/ramparts/ramparts_1", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/ramparts/ramparts_2", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/hoglin_stable/ramparts/ramparts_3", orThrow), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/hoglin_stable/rampart_plates",
            new StructureTemplatePool(
                orThrow2,
                ImmutableList.of(Pair.of(StructurePoolElement.single("bastion/hoglin_stable/rampart_plates/rampart_plate_1", orThrow), 1)),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/hoglin_stable/connectors",
            new StructureTemplatePool(
                orThrow2,
                ImmutableList.of(Pair.of(StructurePoolElement.single("bastion/hoglin_stable/connectors/end_post_connector", orThrow), 1)),
                StructureTemplatePool.Projection.RIGID
            )
        );
    }
}
