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

public class SavannaVillagePools {
    public static final ResourceKey<StructureTemplatePool> START = Pools.createKey("village/savanna/town_centers");
    private static final ResourceKey<StructureTemplatePool> TERMINATORS_KEY = Pools.createKey("village/savanna/terminators");
    private static final ResourceKey<StructureTemplatePool> ZOMBIE_TERMINATORS_KEY = Pools.createKey("village/savanna/zombie/terminators");

    public static void bootstrap(BootstrapContext<StructureTemplatePool> context) {
        HolderGetter<PlacedFeature> holderGetter = context.lookup(Registries.PLACED_FEATURE);
        Holder<PlacedFeature> orThrow = holderGetter.getOrThrow(VillagePlacements.ACACIA_VILLAGE);
        Holder<PlacedFeature> orThrow1 = holderGetter.getOrThrow(VillagePlacements.PILE_HAY_VILLAGE);
        Holder<PlacedFeature> orThrow2 = holderGetter.getOrThrow(VillagePlacements.PILE_MELON_VILLAGE);
        HolderGetter<StructureProcessorList> holderGetter1 = context.lookup(Registries.PROCESSOR_LIST);
        Holder<StructureProcessorList> orThrow3 = holderGetter1.getOrThrow(ProcessorLists.ZOMBIE_SAVANNA);
        Holder<StructureProcessorList> orThrow4 = holderGetter1.getOrThrow(ProcessorLists.STREET_SAVANNA);
        Holder<StructureProcessorList> orThrow5 = holderGetter1.getOrThrow(ProcessorLists.FARM_SAVANNA);
        HolderGetter<StructureTemplatePool> holderGetter2 = context.lookup(Registries.TEMPLATE_POOL);
        Holder<StructureTemplatePool> orThrow6 = holderGetter2.getOrThrow(Pools.EMPTY);
        Holder<StructureTemplatePool> orThrow7 = holderGetter2.getOrThrow(TERMINATORS_KEY);
        Holder<StructureTemplatePool> orThrow8 = holderGetter2.getOrThrow(ZOMBIE_TERMINATORS_KEY);
        context.register(
            START,
            new StructureTemplatePool(
                orThrow6,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/savanna/town_centers/savanna_meeting_point_1"), 100),
                    Pair.of(StructurePoolElement.legacy("village/savanna/town_centers/savanna_meeting_point_2"), 50),
                    Pair.of(StructurePoolElement.legacy("village/savanna/town_centers/savanna_meeting_point_3"), 150),
                    Pair.of(StructurePoolElement.legacy("village/savanna/town_centers/savanna_meeting_point_4"), 150),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/town_centers/savanna_meeting_point_1", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/town_centers/savanna_meeting_point_2", orThrow3), 1),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/town_centers/savanna_meeting_point_3", orThrow3), 3),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/town_centers/savanna_meeting_point_4", orThrow3), 3)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/savanna/streets",
            new StructureTemplatePool(
                orThrow7,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/savanna/streets/corner_01", orThrow4), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/streets/corner_03", orThrow4), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/streets/straight_02", orThrow4), 4),
                    Pair.of(StructurePoolElement.legacy("village/savanna/streets/straight_04", orThrow4), 7),
                    Pair.of(StructurePoolElement.legacy("village/savanna/streets/straight_05", orThrow4), 3),
                    Pair.of(StructurePoolElement.legacy("village/savanna/streets/straight_06", orThrow4), 4),
                    Pair.of(StructurePoolElement.legacy("village/savanna/streets/straight_08", orThrow4), 4),
                    Pair.of(StructurePoolElement.legacy("village/savanna/streets/straight_09", orThrow4), 4),
                    Pair.of(StructurePoolElement.legacy("village/savanna/streets/straight_10", orThrow4), 4),
                    Pair.of(StructurePoolElement.legacy("village/savanna/streets/straight_11", orThrow4), 4),
                    Pair.of(StructurePoolElement.legacy("village/savanna/streets/crossroad_02", orThrow4), 1),
                    Pair.of(StructurePoolElement.legacy("village/savanna/streets/crossroad_03", orThrow4), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/streets/crossroad_04", orThrow4), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/streets/crossroad_05", orThrow4), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/streets/crossroad_06", orThrow4), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/streets/crossroad_07", orThrow4), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/streets/split_01", orThrow4), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/streets/split_02", orThrow4), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/streets/turn_01", orThrow4), 3)
                ),
                StructureTemplatePool.Projection.TERRAIN_MATCHING
            )
        );
        Pools.register(
            context,
            "village/savanna/zombie/streets",
            new StructureTemplatePool(
                orThrow8,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/streets/corner_01", orThrow4), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/streets/corner_03", orThrow4), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/streets/straight_02", orThrow4), 4),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/streets/straight_04", orThrow4), 7),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/streets/straight_05", orThrow4), 3),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/streets/straight_06", orThrow4), 4),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/streets/straight_08", orThrow4), 4),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/streets/straight_09", orThrow4), 4),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/streets/straight_10", orThrow4), 4),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/streets/straight_11", orThrow4), 4),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/streets/crossroad_02", orThrow4), 1),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/streets/crossroad_03", orThrow4), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/streets/crossroad_04", orThrow4), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/streets/crossroad_05", orThrow4), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/streets/crossroad_06", orThrow4), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/streets/crossroad_07", orThrow4), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/streets/split_01", orThrow4), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/streets/split_02", orThrow4), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/streets/turn_01", orThrow4), 3)
                ),
                StructureTemplatePool.Projection.TERRAIN_MATCHING
            )
        );
        Pools.register(
            context,
            "village/savanna/houses",
            new StructureTemplatePool(
                orThrow7,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_small_house_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_small_house_2"), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_small_house_3"), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_small_house_4"), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_small_house_5"), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_small_house_6"), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_small_house_7"), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_small_house_8"), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_medium_house_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_medium_house_2"), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_butchers_shop_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_butchers_shop_2"), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_tool_smith_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_fletcher_house_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_shepherd_1"), 7),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_armorer_1"), 1),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_fisher_cottage_1"), 3),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_tannery_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_cartographer_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_library_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_mason_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_weaponsmith_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_weaponsmith_2"), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_temple_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_temple_2"), 3),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_large_farm_1", orThrow5), 4),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_large_farm_2", orThrow5), 6),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_small_farm", orThrow5), 4),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_animal_pen_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_animal_pen_2"), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_animal_pen_3"), 2),
                    Pair.of(StructurePoolElement.empty(), 5)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/savanna/zombie/houses",
            new StructureTemplatePool(
                orThrow8,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/houses/savanna_small_house_1", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/houses/savanna_small_house_2", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/houses/savanna_small_house_3", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/houses/savanna_small_house_4", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/houses/savanna_small_house_5", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/houses/savanna_small_house_6", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/houses/savanna_small_house_7", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/houses/savanna_small_house_8", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/houses/savanna_medium_house_1", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/houses/savanna_medium_house_2", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_butchers_shop_1", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_butchers_shop_2", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_tool_smith_1", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_fletcher_house_1", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_shepherd_1", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_armorer_1", orThrow3), 1),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_fisher_cottage_1", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_tannery_1", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_cartographer_1", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_library_1", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_mason_1", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_weaponsmith_1", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_weaponsmith_2", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_temple_1", orThrow3), 1),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_temple_2", orThrow3), 3),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_large_farm_1", orThrow3), 4),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/houses/savanna_large_farm_2", orThrow3), 4),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_small_farm", orThrow3), 4),
                    Pair.of(StructurePoolElement.legacy("village/savanna/houses/savanna_animal_pen_1", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/houses/savanna_animal_pen_2", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/houses/savanna_animal_pen_3", orThrow3), 2),
                    Pair.of(StructurePoolElement.empty(), 5)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        context.register(
            TERMINATORS_KEY,
            new StructureTemplatePool(
                orThrow6,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/plains/terminators/terminator_01", orThrow4), 1),
                    Pair.of(StructurePoolElement.legacy("village/plains/terminators/terminator_02", orThrow4), 1),
                    Pair.of(StructurePoolElement.legacy("village/plains/terminators/terminator_03", orThrow4), 1),
                    Pair.of(StructurePoolElement.legacy("village/plains/terminators/terminator_04", orThrow4), 1),
                    Pair.of(StructurePoolElement.legacy("village/savanna/terminators/terminator_05", orThrow4), 1)
                ),
                StructureTemplatePool.Projection.TERRAIN_MATCHING
            )
        );
        context.register(
            ZOMBIE_TERMINATORS_KEY,
            new StructureTemplatePool(
                orThrow6,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/plains/terminators/terminator_01", orThrow4), 1),
                    Pair.of(StructurePoolElement.legacy("village/plains/terminators/terminator_02", orThrow4), 1),
                    Pair.of(StructurePoolElement.legacy("village/plains/terminators/terminator_03", orThrow4), 1),
                    Pair.of(StructurePoolElement.legacy("village/plains/terminators/terminator_04", orThrow4), 1),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/terminators/terminator_05", orThrow4), 1)
                ),
                StructureTemplatePool.Projection.TERRAIN_MATCHING
            )
        );
        Pools.register(
            context,
            "village/savanna/trees",
            new StructureTemplatePool(orThrow6, ImmutableList.of(Pair.of(StructurePoolElement.feature(orThrow), 1)), StructureTemplatePool.Projection.RIGID)
        );
        Pools.register(
            context,
            "village/savanna/decor",
            new StructureTemplatePool(
                orThrow6,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/savanna/savanna_lamp_post_01"), 4),
                    Pair.of(StructurePoolElement.feature(orThrow), 4),
                    Pair.of(StructurePoolElement.feature(orThrow1), 4),
                    Pair.of(StructurePoolElement.feature(orThrow2), 1),
                    Pair.of(StructurePoolElement.empty(), 4)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/savanna/zombie/decor",
            new StructureTemplatePool(
                orThrow6,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/savanna/savanna_lamp_post_01", orThrow3), 4),
                    Pair.of(StructurePoolElement.feature(orThrow), 4),
                    Pair.of(StructurePoolElement.feature(orThrow1), 4),
                    Pair.of(StructurePoolElement.feature(orThrow2), 1),
                    Pair.of(StructurePoolElement.empty(), 4)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/savanna/villagers",
            new StructureTemplatePool(
                orThrow6,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/savanna/villagers/nitwit"), 1),
                    Pair.of(StructurePoolElement.legacy("village/savanna/villagers/baby"), 1),
                    Pair.of(StructurePoolElement.legacy("village/savanna/villagers/unemployed"), 10)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/savanna/zombie/villagers",
            new StructureTemplatePool(
                orThrow6,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/villagers/nitwit"), 1),
                    Pair.of(StructurePoolElement.legacy("village/savanna/zombie/villagers/unemployed"), 10)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
    }
}
