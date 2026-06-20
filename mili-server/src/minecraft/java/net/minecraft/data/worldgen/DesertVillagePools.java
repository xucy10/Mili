package net.minecraft.data.worldgen;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.placement.VillagePlacements;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

public class DesertVillagePools {
    public static final ResourceKey<StructureTemplatePool> START = Pools.createKey("village/desert/town_centers");
    private static final ResourceKey<StructureTemplatePool> TERMINATORS_KEY = Pools.createKey("village/desert/terminators");
    private static final ResourceKey<StructureTemplatePool> ZOMBIE_TERMINATORS_KEY = Pools.createKey("village/desert/zombie/terminators");

    public static void bootstrap(BootstrapContext<StructureTemplatePool> context) {
        HolderGetter<PlacedFeature> holderGetter = context.lookup(Registries.PLACED_FEATURE);
        Holder<PlacedFeature> orThrow = holderGetter.getOrThrow(VillagePlacements.PATCH_CACTUS_VILLAGE);
        Holder<PlacedFeature> orThrow1 = holderGetter.getOrThrow(VillagePlacements.PILE_HAY_VILLAGE);
        HolderGetter<StructureProcessorList> holderGetter1 = context.lookup(Registries.PROCESSOR_LIST);
        Holder<StructureProcessorList> orThrow2 = holderGetter1.getOrThrow(ProcessorLists.ZOMBIE_DESERT);
        Holder<StructureProcessorList> orThrow3 = holderGetter1.getOrThrow(ProcessorLists.FARM_DESERT);
        HolderGetter<StructureTemplatePool> holderGetter2 = context.lookup(Registries.TEMPLATE_POOL);
        Holder<StructureTemplatePool> orThrow4 = holderGetter2.getOrThrow(Pools.EMPTY);
        Holder<StructureTemplatePool> orThrow5 = holderGetter2.getOrThrow(TERMINATORS_KEY);
        Holder<StructureTemplatePool> orThrow6 = holderGetter2.getOrThrow(ZOMBIE_TERMINATORS_KEY);
        context.register(
            START,
            new StructureTemplatePool(
                orThrow4,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/desert/town_centers/desert_meeting_point_1"), 98),
                    Pair.of(StructurePoolElement.legacy("village/desert/town_centers/desert_meeting_point_2"), 98),
                    Pair.of(StructurePoolElement.legacy("village/desert/town_centers/desert_meeting_point_3"), 49),
                    Pair.of(StructurePoolElement.legacy("village/desert/zombie/town_centers/desert_meeting_point_1", orThrow2), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/zombie/town_centers/desert_meeting_point_2", orThrow2), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/zombie/town_centers/desert_meeting_point_3", orThrow2), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/desert/streets",
            new StructureTemplatePool(
                orThrow5,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/desert/streets/corner_01"), 3),
                    Pair.of(StructurePoolElement.legacy("village/desert/streets/corner_02"), 3),
                    Pair.of(StructurePoolElement.legacy("village/desert/streets/straight_01"), 4),
                    Pair.of(StructurePoolElement.legacy("village/desert/streets/straight_02"), 4),
                    Pair.of(StructurePoolElement.legacy("village/desert/streets/straight_03"), 3),
                    Pair.of(StructurePoolElement.legacy("village/desert/streets/crossroad_01"), 3),
                    Pair.of(StructurePoolElement.legacy("village/desert/streets/crossroad_02"), 3),
                    Pair.of(StructurePoolElement.legacy("village/desert/streets/crossroad_03"), 3),
                    Pair.of(StructurePoolElement.legacy("village/desert/streets/square_01"), 3),
                    Pair.of(StructurePoolElement.legacy("village/desert/streets/square_02"), 3),
                    Pair.of(StructurePoolElement.legacy("village/desert/streets/turn_01"), 3)
                ),
                StructureTemplatePool.Projection.TERRAIN_MATCHING
            )
        );
        Pools.register(
            context,
            "village/desert/zombie/streets",
            new StructureTemplatePool(
                orThrow6,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/desert/zombie/streets/corner_01"), 3),
                    Pair.of(StructurePoolElement.legacy("village/desert/zombie/streets/corner_02"), 3),
                    Pair.of(StructurePoolElement.legacy("village/desert/zombie/streets/straight_01"), 4),
                    Pair.of(StructurePoolElement.legacy("village/desert/zombie/streets/straight_02"), 4),
                    Pair.of(StructurePoolElement.legacy("village/desert/zombie/streets/straight_03"), 3),
                    Pair.of(StructurePoolElement.legacy("village/desert/zombie/streets/crossroad_01"), 3),
                    Pair.of(StructurePoolElement.legacy("village/desert/zombie/streets/crossroad_02"), 3),
                    Pair.of(StructurePoolElement.legacy("village/desert/zombie/streets/crossroad_03"), 3),
                    Pair.of(StructurePoolElement.legacy("village/desert/zombie/streets/square_01"), 3),
                    Pair.of(StructurePoolElement.legacy("village/desert/zombie/streets/square_02"), 3),
                    Pair.of(StructurePoolElement.legacy("village/desert/zombie/streets/turn_01"), 3)
                ),
                StructureTemplatePool.Projection.TERRAIN_MATCHING
            )
        );
        Pools.register(
            context,
            "village/desert/houses",
            new StructureTemplatePool(
                orThrow5,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_small_house_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_small_house_2"), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_small_house_3"), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_small_house_4"), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_small_house_5"), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_small_house_6"), 1),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_small_house_7"), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_small_house_8"), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_medium_house_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_medium_house_2"), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_butcher_shop_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_tool_smith_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_fletcher_house_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_shepherd_house_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_armorer_1"), 1),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_fisher_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_tannery_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_cartographer_house_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_library_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_mason_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_weaponsmith_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_temple_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_temple_2"), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_large_farm_1", orThrow3), 11),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_farm_1", orThrow3), 4),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_farm_2", orThrow3), 4),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_animal_pen_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_animal_pen_2"), 2),
                    Pair.of(StructurePoolElement.empty(), 5)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/desert/zombie/houses",
            new StructureTemplatePool(
                orThrow6,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/desert/zombie/houses/desert_small_house_1", orThrow2), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/zombie/houses/desert_small_house_2", orThrow2), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/zombie/houses/desert_small_house_3", orThrow2), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/zombie/houses/desert_small_house_4", orThrow2), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/zombie/houses/desert_small_house_5", orThrow2), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/zombie/houses/desert_small_house_6", orThrow2), 1),
                    Pair.of(StructurePoolElement.legacy("village/desert/zombie/houses/desert_small_house_7", orThrow2), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/zombie/houses/desert_small_house_8", orThrow2), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/zombie/houses/desert_medium_house_1", orThrow2), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/zombie/houses/desert_medium_house_2", orThrow2), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_butcher_shop_1", orThrow2), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_tool_smith_1", orThrow2), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_fletcher_house_1", orThrow2), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_shepherd_house_1", orThrow2), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_armorer_1", orThrow2), 1),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_fisher_1", orThrow2), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_tannery_1", orThrow2), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_cartographer_house_1", orThrow2), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_library_1", orThrow2), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_mason_1", orThrow2), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_weaponsmith_1", orThrow2), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_temple_1", orThrow2), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_temple_2", orThrow2), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_large_farm_1", orThrow2), 7),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_farm_1", orThrow2), 4),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_farm_2", orThrow2), 4),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_animal_pen_1", orThrow2), 2),
                    Pair.of(StructurePoolElement.legacy("village/desert/houses/desert_animal_pen_2", orThrow2), 2),
                    Pair.of(StructurePoolElement.empty(), 5)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        context.register(
            TERMINATORS_KEY,
            new StructureTemplatePool(
                orThrow4,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/desert/terminators/terminator_01"), 1),
                    Pair.of(StructurePoolElement.legacy("village/desert/terminators/terminator_02"), 1)
                ),
                StructureTemplatePool.Projection.TERRAIN_MATCHING
            )
        );
        context.register(
            ZOMBIE_TERMINATORS_KEY,
            new StructureTemplatePool(
                orThrow4,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/desert/terminators/terminator_01"), 1),
                    Pair.of(StructurePoolElement.legacy("village/desert/zombie/terminators/terminator_02"), 1)
                ),
                StructureTemplatePool.Projection.TERRAIN_MATCHING
            )
        );
        Pools.register(
            context,
            "village/desert/decor",
            new StructureTemplatePool(
                orThrow4,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/desert/desert_lamp_1"), 10),
                    Pair.of(StructurePoolElement.feature(orThrow), 4),
                    Pair.of(StructurePoolElement.feature(orThrow1), 4),
                    Pair.of(StructurePoolElement.empty(), 10)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/desert/zombie/decor",
            new StructureTemplatePool(
                orThrow4,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/desert/desert_lamp_1", orThrow2), 10),
                    Pair.of(StructurePoolElement.feature(orThrow), 4),
                    Pair.of(StructurePoolElement.feature(orThrow1), 4),
                    Pair.of(StructurePoolElement.empty(), 10)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/desert/villagers",
            new StructureTemplatePool(
                orThrow4,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/desert/villagers/nitwit"), 1),
                    Pair.of(StructurePoolElement.legacy("village/desert/villagers/baby"), 1),
                    Pair.of(StructurePoolElement.legacy("village/desert/villagers/unemployed"), 10)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/desert/camel",
            new StructureTemplatePool(
                orThrow4, ImmutableList.of(Pair.of(StructurePoolElement.legacy("village/desert/camel_spawn"), 1)), StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/desert/zombie/villagers",
            new StructureTemplatePool(
                orThrow4,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/desert/zombie/villagers/nitwit"), 1),
                    Pair.of(StructurePoolElement.legacy("village/desert/zombie/villagers/unemployed"), 10)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
    }
}
