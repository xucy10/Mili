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

public class PlainVillagePools {
    public static final ResourceKey<StructureTemplatePool> START = Pools.createKey("village/plains/town_centers");
    private static final ResourceKey<StructureTemplatePool> TERMINATORS_KEY = Pools.createKey("village/plains/terminators");

    public static void bootstrap(BootstrapContext<StructureTemplatePool> context) {
        HolderGetter<PlacedFeature> holderGetter = context.lookup(Registries.PLACED_FEATURE);
        Holder<PlacedFeature> orThrow = holderGetter.getOrThrow(VillagePlacements.OAK_VILLAGE);
        Holder<PlacedFeature> orThrow1 = holderGetter.getOrThrow(VillagePlacements.FLOWER_PLAIN_VILLAGE);
        Holder<PlacedFeature> orThrow2 = holderGetter.getOrThrow(VillagePlacements.PILE_HAY_VILLAGE);
        HolderGetter<StructureProcessorList> holderGetter1 = context.lookup(Registries.PROCESSOR_LIST);
        Holder<StructureProcessorList> orThrow3 = holderGetter1.getOrThrow(ProcessorLists.MOSSIFY_10_PERCENT);
        Holder<StructureProcessorList> orThrow4 = holderGetter1.getOrThrow(ProcessorLists.MOSSIFY_20_PERCENT);
        Holder<StructureProcessorList> orThrow5 = holderGetter1.getOrThrow(ProcessorLists.MOSSIFY_70_PERCENT);
        Holder<StructureProcessorList> orThrow6 = holderGetter1.getOrThrow(ProcessorLists.ZOMBIE_PLAINS);
        Holder<StructureProcessorList> orThrow7 = holderGetter1.getOrThrow(ProcessorLists.STREET_PLAINS);
        Holder<StructureProcessorList> orThrow8 = holderGetter1.getOrThrow(ProcessorLists.FARM_PLAINS);
        HolderGetter<StructureTemplatePool> holderGetter2 = context.lookup(Registries.TEMPLATE_POOL);
        Holder<StructureTemplatePool> orThrow9 = holderGetter2.getOrThrow(Pools.EMPTY);
        Holder<StructureTemplatePool> orThrow10 = holderGetter2.getOrThrow(TERMINATORS_KEY);
        context.register(
            START,
            new StructureTemplatePool(
                orThrow9,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/plains/town_centers/plains_fountain_01", orThrow4), 50),
                    Pair.of(StructurePoolElement.legacy("village/plains/town_centers/plains_meeting_point_1", orThrow4), 50),
                    Pair.of(StructurePoolElement.legacy("village/plains/town_centers/plains_meeting_point_2"), 50),
                    Pair.of(StructurePoolElement.legacy("village/plains/town_centers/plains_meeting_point_3", orThrow5), 50),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/town_centers/plains_fountain_01", orThrow6), 1),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/town_centers/plains_meeting_point_1", orThrow6), 1),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/town_centers/plains_meeting_point_2", orThrow6), 1),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/town_centers/plains_meeting_point_3", orThrow6), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/plains/streets",
            new StructureTemplatePool(
                orThrow10,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/plains/streets/corner_01", orThrow7), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/streets/corner_02", orThrow7), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/streets/corner_03", orThrow7), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/streets/straight_01", orThrow7), 4),
                    Pair.of(StructurePoolElement.legacy("village/plains/streets/straight_02", orThrow7), 4),
                    Pair.of(StructurePoolElement.legacy("village/plains/streets/straight_03", orThrow7), 7),
                    Pair.of(StructurePoolElement.legacy("village/plains/streets/straight_04", orThrow7), 7),
                    Pair.of(StructurePoolElement.legacy("village/plains/streets/straight_05", orThrow7), 3),
                    Pair.of(StructurePoolElement.legacy("village/plains/streets/straight_06", orThrow7), 4),
                    Pair.of(StructurePoolElement.legacy("village/plains/streets/crossroad_01", orThrow7), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/streets/crossroad_02", orThrow7), 1),
                    Pair.of(StructurePoolElement.legacy("village/plains/streets/crossroad_03", orThrow7), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/streets/crossroad_04", orThrow7), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/streets/crossroad_05", orThrow7), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/streets/crossroad_06", orThrow7), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/streets/turn_01", orThrow7), 3)
                ),
                StructureTemplatePool.Projection.TERRAIN_MATCHING
            )
        );
        Pools.register(
            context,
            "village/plains/zombie/streets",
            new StructureTemplatePool(
                orThrow10,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/streets/corner_01", orThrow7), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/streets/corner_02", orThrow7), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/streets/corner_03", orThrow7), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/streets/straight_01", orThrow7), 4),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/streets/straight_02", orThrow7), 4),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/streets/straight_03", orThrow7), 7),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/streets/straight_04", orThrow7), 7),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/streets/straight_05", orThrow7), 3),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/streets/straight_06", orThrow7), 4),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/streets/crossroad_01", orThrow7), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/streets/crossroad_02", orThrow7), 1),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/streets/crossroad_03", orThrow7), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/streets/crossroad_04", orThrow7), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/streets/crossroad_05", orThrow7), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/streets/crossroad_06", orThrow7), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/streets/turn_01", orThrow7), 3)
                ),
                StructureTemplatePool.Projection.TERRAIN_MATCHING
            )
        );
        Pools.register(
            context,
            "village/plains/houses",
            new StructureTemplatePool(
                orThrow10,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_small_house_1", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_small_house_2", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_small_house_3", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_small_house_4", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_small_house_5", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_small_house_6", orThrow3), 1),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_small_house_7", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_small_house_8", orThrow3), 3),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_medium_house_1", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_medium_house_2", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_big_house_1", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_butcher_shop_1", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_butcher_shop_2", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_tool_smith_1", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_fletcher_house_1", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_shepherds_house_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_armorer_house_1", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_fisher_cottage_1", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_tannery_1", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_cartographer_1", orThrow3), 1),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_library_1", orThrow3), 5),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_library_2", orThrow3), 1),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_masons_house_1", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_weaponsmith_1", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_temple_3", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_temple_4", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_stable_1", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_stable_2"), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_large_farm_1", orThrow8), 4),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_small_farm_1", orThrow8), 4),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_animal_pen_1"), 1),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_animal_pen_2"), 1),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_animal_pen_3"), 5),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_accessory_1"), 1),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_meeting_point_4", orThrow5), 3),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_meeting_point_5"), 1),
                    Pair.of(StructurePoolElement.empty(), 10)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/plains/zombie/houses",
            new StructureTemplatePool(
                orThrow10,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/houses/plains_small_house_1", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/houses/plains_small_house_2", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/houses/plains_small_house_3", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/houses/plains_small_house_4", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/houses/plains_small_house_5", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/houses/plains_small_house_6", orThrow6), 1),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/houses/plains_small_house_7", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/houses/plains_small_house_8", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/houses/plains_medium_house_1", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/houses/plains_medium_house_2", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/houses/plains_big_house_1", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_butcher_shop_1", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/houses/plains_butcher_shop_2", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_tool_smith_1", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/houses/plains_fletcher_house_1", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/houses/plains_shepherds_house_1", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_armorer_house_1", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_fisher_cottage_1", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_tannery_1", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_cartographer_1", orThrow6), 1),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_library_1", orThrow6), 3),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_library_2", orThrow6), 1),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_masons_house_1", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_weaponsmith_1", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_temple_3", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_temple_4", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/houses/plains_stable_1", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_stable_2", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_large_farm_1", orThrow6), 4),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_small_farm_1", orThrow6), 4),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_animal_pen_1", orThrow6), 1),
                    Pair.of(StructurePoolElement.legacy("village/plains/houses/plains_animal_pen_2", orThrow6), 1),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/houses/plains_animal_pen_3", orThrow6), 5),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/houses/plains_meeting_point_4", orThrow6), 3),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/houses/plains_meeting_point_5", orThrow6), 1),
                    Pair.of(StructurePoolElement.empty(), 10)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        context.register(
            TERMINATORS_KEY,
            new StructureTemplatePool(
                orThrow9,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/plains/terminators/terminator_01", orThrow7), 1),
                    Pair.of(StructurePoolElement.legacy("village/plains/terminators/terminator_02", orThrow7), 1),
                    Pair.of(StructurePoolElement.legacy("village/plains/terminators/terminator_03", orThrow7), 1),
                    Pair.of(StructurePoolElement.legacy("village/plains/terminators/terminator_04", orThrow7), 1)
                ),
                StructureTemplatePool.Projection.TERRAIN_MATCHING
            )
        );
        Pools.register(
            context,
            "village/plains/trees",
            new StructureTemplatePool(orThrow9, ImmutableList.of(Pair.of(StructurePoolElement.feature(orThrow), 1)), StructureTemplatePool.Projection.RIGID)
        );
        Pools.register(
            context,
            "village/plains/decor",
            new StructureTemplatePool(
                orThrow9,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/plains/plains_lamp_1"), 2),
                    Pair.of(StructurePoolElement.feature(orThrow), 1),
                    Pair.of(StructurePoolElement.feature(orThrow1), 1),
                    Pair.of(StructurePoolElement.feature(orThrow2), 1),
                    Pair.of(StructurePoolElement.empty(), 2)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/plains/zombie/decor",
            new StructureTemplatePool(
                orThrow9,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/plains/plains_lamp_1", orThrow6), 1),
                    Pair.of(StructurePoolElement.feature(orThrow), 1),
                    Pair.of(StructurePoolElement.feature(orThrow1), 1),
                    Pair.of(StructurePoolElement.feature(orThrow2), 1),
                    Pair.of(StructurePoolElement.empty(), 2)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/plains/villagers",
            new StructureTemplatePool(
                orThrow9,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/plains/villagers/nitwit"), 1),
                    Pair.of(StructurePoolElement.legacy("village/plains/villagers/baby"), 1),
                    Pair.of(StructurePoolElement.legacy("village/plains/villagers/unemployed"), 10)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/plains/zombie/villagers",
            new StructureTemplatePool(
                orThrow9,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/villagers/nitwit"), 1),
                    Pair.of(StructurePoolElement.legacy("village/plains/zombie/villagers/unemployed"), 10)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/common/animals",
            new StructureTemplatePool(
                orThrow9,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/common/animals/cows_1"), 7),
                    Pair.of(StructurePoolElement.legacy("village/common/animals/pigs_1"), 7),
                    Pair.of(StructurePoolElement.legacy("village/common/animals/horses_1"), 1),
                    Pair.of(StructurePoolElement.legacy("village/common/animals/horses_2"), 1),
                    Pair.of(StructurePoolElement.legacy("village/common/animals/horses_3"), 1),
                    Pair.of(StructurePoolElement.legacy("village/common/animals/horses_4"), 1),
                    Pair.of(StructurePoolElement.legacy("village/common/animals/horses_5"), 1),
                    Pair.of(StructurePoolElement.legacy("village/common/animals/sheep_1"), 1),
                    Pair.of(StructurePoolElement.legacy("village/common/animals/sheep_2"), 1),
                    Pair.of(StructurePoolElement.empty(), 5)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/common/sheep",
            new StructureTemplatePool(
                orThrow9,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/common/animals/sheep_1"), 1),
                    Pair.of(StructurePoolElement.legacy("village/common/animals/sheep_2"), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/common/cats",
            new StructureTemplatePool(
                orThrow9,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/common/animals/cat_black"), 1),
                    Pair.of(StructurePoolElement.legacy("village/common/animals/cat_british"), 1),
                    Pair.of(StructurePoolElement.legacy("village/common/animals/cat_calico"), 1),
                    Pair.of(StructurePoolElement.legacy("village/common/animals/cat_persian"), 1),
                    Pair.of(StructurePoolElement.legacy("village/common/animals/cat_ragdoll"), 1),
                    Pair.of(StructurePoolElement.legacy("village/common/animals/cat_red"), 1),
                    Pair.of(StructurePoolElement.legacy("village/common/animals/cat_siamese"), 1),
                    Pair.of(StructurePoolElement.legacy("village/common/animals/cat_tabby"), 1),
                    Pair.of(StructurePoolElement.legacy("village/common/animals/cat_white"), 1),
                    Pair.of(StructurePoolElement.legacy("village/common/animals/cat_jellie"), 1),
                    Pair.of(StructurePoolElement.empty(), 3)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/common/butcher_animals",
            new StructureTemplatePool(
                orThrow9,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/common/animals/cows_1"), 3),
                    Pair.of(StructurePoolElement.legacy("village/common/animals/pigs_1"), 3),
                    Pair.of(StructurePoolElement.legacy("village/common/animals/sheep_1"), 1),
                    Pair.of(StructurePoolElement.legacy("village/common/animals/sheep_2"), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/common/iron_golem",
            new StructureTemplatePool(
                orThrow9, ImmutableList.of(Pair.of(StructurePoolElement.legacy("village/common/iron_golem"), 1)), StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/common/well_bottoms",
            new StructureTemplatePool(
                orThrow9, ImmutableList.of(Pair.of(StructurePoolElement.legacy("village/common/well_bottom"), 1)), StructureTemplatePool.Projection.RIGID
            )
        );
    }
}
