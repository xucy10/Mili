package net.minecraft.data.worldgen;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

public class BastionTreasureRoomPools {
    public static void bootstrap(BootstrapContext<StructureTemplatePool> context) {
        HolderGetter<StructureProcessorList> holderGetter = context.lookup(Registries.PROCESSOR_LIST);
        Holder<StructureProcessorList> orThrow = holderGetter.getOrThrow(ProcessorLists.TREASURE_ROOMS);
        Holder<StructureProcessorList> orThrow1 = holderGetter.getOrThrow(ProcessorLists.HIGH_WALL);
        Holder<StructureProcessorList> orThrow2 = holderGetter.getOrThrow(ProcessorLists.BOTTOM_RAMPART);
        Holder<StructureProcessorList> orThrow3 = holderGetter.getOrThrow(ProcessorLists.HIGH_RAMPART);
        Holder<StructureProcessorList> orThrow4 = holderGetter.getOrThrow(ProcessorLists.ROOF);
        HolderGetter<StructureTemplatePool> holderGetter1 = context.lookup(Registries.TEMPLATE_POOL);
        Holder<StructureTemplatePool> orThrow5 = holderGetter1.getOrThrow(Pools.EMPTY);
        Pools.register(
            context,
            "bastion/treasure/bases",
            new StructureTemplatePool(
                orThrow5,
                ImmutableList.of(Pair.of(StructurePoolElement.single("bastion/treasure/bases/lava_basin", orThrow), 1)),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/treasure/stairs",
            new StructureTemplatePool(
                orThrow5,
                ImmutableList.of(Pair.of(StructurePoolElement.single("bastion/treasure/stairs/lower_stairs", orThrow), 1)),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/treasure/bases/centers",
            new StructureTemplatePool(
                orThrow5,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/treasure/bases/centers/center_0", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/bases/centers/center_1", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/bases/centers/center_2", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/bases/centers/center_3", orThrow), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/treasure/brains",
            new StructureTemplatePool(
                orThrow5,
                ImmutableList.of(Pair.of(StructurePoolElement.single("bastion/treasure/brains/center_brain", orThrow), 1)),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/treasure/walls",
            new StructureTemplatePool(
                orThrow5,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/treasure/walls/lava_wall", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/walls/entrance_wall", orThrow1), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/treasure/walls/outer",
            new StructureTemplatePool(
                orThrow5,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/treasure/walls/outer/top_corner", orThrow1), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/walls/outer/mid_corner", orThrow1), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/walls/outer/bottom_corner", orThrow1), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/walls/outer/outer_wall", orThrow1), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/walls/outer/medium_outer_wall", orThrow1), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/walls/outer/tall_outer_wall", orThrow1), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/treasure/walls/bottom",
            new StructureTemplatePool(
                orThrow5,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/treasure/walls/bottom/wall_0", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/walls/bottom/wall_1", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/walls/bottom/wall_2", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/walls/bottom/wall_3", orThrow), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/treasure/walls/mid",
            new StructureTemplatePool(
                orThrow5,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/treasure/walls/mid/wall_0", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/walls/mid/wall_1", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/walls/mid/wall_2", orThrow), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/treasure/walls/top",
            new StructureTemplatePool(
                orThrow5,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/treasure/walls/top/main_entrance", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/walls/top/wall_0", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/walls/top/wall_1", orThrow), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/treasure/connectors",
            new StructureTemplatePool(
                orThrow5,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/treasure/connectors/center_to_wall_middle", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/connectors/center_to_wall_top", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/connectors/center_to_wall_top_entrance", orThrow), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/treasure/entrances",
            new StructureTemplatePool(
                orThrow5,
                ImmutableList.of(Pair.of(StructurePoolElement.single("bastion/treasure/entrances/entrance_0", orThrow), 1)),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/treasure/ramparts",
            new StructureTemplatePool(
                orThrow5,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/treasure/ramparts/mid_wall_main", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/ramparts/mid_wall_side", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/ramparts/bottom_wall_0", orThrow2), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/ramparts/top_wall", orThrow3), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/ramparts/lava_basin_side", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/ramparts/lava_basin_main", orThrow), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/treasure/corners/bottom",
            new StructureTemplatePool(
                orThrow5,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/treasure/corners/bottom/corner_0", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/corners/bottom/corner_1", orThrow), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/treasure/corners/edges",
            new StructureTemplatePool(
                orThrow5,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/treasure/corners/edges/bottom", orThrow1), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/corners/edges/middle", orThrow1), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/corners/edges/top", orThrow1), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/treasure/corners/middle",
            new StructureTemplatePool(
                orThrow5,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/treasure/corners/middle/corner_0", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/corners/middle/corner_1", orThrow), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/treasure/corners/top",
            new StructureTemplatePool(
                orThrow5,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/treasure/corners/top/corner_0", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/corners/top/corner_1", orThrow), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/treasure/extensions/large_pool",
            new StructureTemplatePool(
                orThrow5,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/treasure/extensions/empty", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/extensions/empty", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/extensions/fire_room", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/extensions/large_bridge_0", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/extensions/large_bridge_1", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/extensions/large_bridge_2", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/extensions/large_bridge_3", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/extensions/roofed_bridge", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/extensions/empty", orThrow), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/treasure/extensions/small_pool",
            new StructureTemplatePool(
                orThrow5,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/treasure/extensions/empty", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/extensions/fire_room", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/extensions/empty", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/extensions/small_bridge_0", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/extensions/small_bridge_1", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/extensions/small_bridge_2", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/extensions/small_bridge_3", orThrow), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/treasure/extensions/houses",
            new StructureTemplatePool(
                orThrow5,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/treasure/extensions/house_0", orThrow), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/extensions/house_1", orThrow), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "bastion/treasure/roofs",
            new StructureTemplatePool(
                orThrow5,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.single("bastion/treasure/roofs/wall_roof", orThrow4), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/roofs/corner_roof", orThrow4), 1),
                    Pair.of(StructurePoolElement.single("bastion/treasure/roofs/center_roof", orThrow4), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
    }
}
