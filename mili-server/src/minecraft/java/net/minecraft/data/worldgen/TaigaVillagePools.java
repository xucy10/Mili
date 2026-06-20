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

public class TaigaVillagePools {
    public static final ResourceKey<StructureTemplatePool> START = Pools.createKey("village/taiga/town_centers");
    private static final ResourceKey<StructureTemplatePool> TERMINATORS_KEY = Pools.createKey("village/taiga/terminators");

    public static void bootstrap(BootstrapContext<StructureTemplatePool> context) {
        HolderGetter<PlacedFeature> holderGetter = context.lookup(Registries.PLACED_FEATURE);
        Holder<PlacedFeature> orThrow = holderGetter.getOrThrow(VillagePlacements.SPRUCE_VILLAGE);
        Holder<PlacedFeature> orThrow1 = holderGetter.getOrThrow(VillagePlacements.PINE_VILLAGE);
        Holder<PlacedFeature> orThrow2 = holderGetter.getOrThrow(VillagePlacements.PILE_PUMPKIN_VILLAGE);
        Holder<PlacedFeature> orThrow3 = holderGetter.getOrThrow(VillagePlacements.PATCH_TAIGA_GRASS_VILLAGE);
        Holder<PlacedFeature> orThrow4 = holderGetter.getOrThrow(VillagePlacements.PATCH_BERRY_BUSH_VILLAGE);
        HolderGetter<StructureProcessorList> holderGetter1 = context.lookup(Registries.PROCESSOR_LIST);
        Holder<StructureProcessorList> orThrow5 = holderGetter1.getOrThrow(ProcessorLists.MOSSIFY_10_PERCENT);
        Holder<StructureProcessorList> orThrow6 = holderGetter1.getOrThrow(ProcessorLists.ZOMBIE_TAIGA);
        Holder<StructureProcessorList> orThrow7 = holderGetter1.getOrThrow(ProcessorLists.STREET_SNOWY_OR_TAIGA);
        Holder<StructureProcessorList> orThrow8 = holderGetter1.getOrThrow(ProcessorLists.FARM_TAIGA);
        HolderGetter<StructureTemplatePool> holderGetter2 = context.lookup(Registries.TEMPLATE_POOL);
        Holder<StructureTemplatePool> orThrow9 = holderGetter2.getOrThrow(Pools.EMPTY);
        Holder<StructureTemplatePool> orThrow10 = holderGetter2.getOrThrow(TERMINATORS_KEY);
        context.register(
            START,
            new StructureTemplatePool(
                orThrow9,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/taiga/town_centers/taiga_meeting_point_1", orThrow5), 49),
                    Pair.of(StructurePoolElement.legacy("village/taiga/town_centers/taiga_meeting_point_2", orThrow5), 49),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/town_centers/taiga_meeting_point_1", orThrow6), 1),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/town_centers/taiga_meeting_point_2", orThrow6), 1)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/taiga/streets",
            new StructureTemplatePool(
                orThrow10,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/taiga/streets/corner_01", orThrow7), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/streets/corner_02", orThrow7), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/streets/corner_03", orThrow7), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/streets/straight_01", orThrow7), 4),
                    Pair.of(StructurePoolElement.legacy("village/taiga/streets/straight_02", orThrow7), 4),
                    Pair.of(StructurePoolElement.legacy("village/taiga/streets/straight_03", orThrow7), 4),
                    Pair.of(StructurePoolElement.legacy("village/taiga/streets/straight_04", orThrow7), 7),
                    Pair.of(StructurePoolElement.legacy("village/taiga/streets/straight_05", orThrow7), 7),
                    Pair.of(StructurePoolElement.legacy("village/taiga/streets/straight_06", orThrow7), 4),
                    Pair.of(StructurePoolElement.legacy("village/taiga/streets/crossroad_01", orThrow7), 1),
                    Pair.of(StructurePoolElement.legacy("village/taiga/streets/crossroad_02", orThrow7), 1),
                    Pair.of(StructurePoolElement.legacy("village/taiga/streets/crossroad_03", orThrow7), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/streets/crossroad_04", orThrow7), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/streets/crossroad_05", orThrow7), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/streets/crossroad_06", orThrow7), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/streets/turn_01", orThrow7), 3)
                ),
                StructureTemplatePool.Projection.TERRAIN_MATCHING
            )
        );
        Pools.register(
            context,
            "village/taiga/zombie/streets",
            new StructureTemplatePool(
                orThrow10,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/streets/corner_01", orThrow7), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/streets/corner_02", orThrow7), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/streets/corner_03", orThrow7), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/streets/straight_01", orThrow7), 4),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/streets/straight_02", orThrow7), 4),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/streets/straight_03", orThrow7), 4),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/streets/straight_04", orThrow7), 7),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/streets/straight_05", orThrow7), 7),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/streets/straight_06", orThrow7), 4),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/streets/crossroad_01", orThrow7), 1),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/streets/crossroad_02", orThrow7), 1),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/streets/crossroad_03", orThrow7), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/streets/crossroad_04", orThrow7), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/streets/crossroad_05", orThrow7), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/streets/crossroad_06", orThrow7), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/streets/turn_01", orThrow7), 3)
                ),
                StructureTemplatePool.Projection.TERRAIN_MATCHING
            )
        );
        Pools.register(
            context,
            "village/taiga/houses",
            new StructureTemplatePool(
                orThrow10,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_small_house_1", orThrow5), 4),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_small_house_2", orThrow5), 4),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_small_house_3", orThrow5), 4),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_small_house_4", orThrow5), 4),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_small_house_5", orThrow5), 4),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_medium_house_1", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_medium_house_2", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_medium_house_3", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_medium_house_4", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_butcher_shop_1", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_tool_smith_1", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_fletcher_house_1", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_shepherds_house_1", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_armorer_house_1", orThrow5), 1),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_armorer_2", orThrow5), 1),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_fisher_cottage_1", orThrow5), 3),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_tannery_1", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_cartographer_house_1", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_library_1", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_masons_house_1", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_weaponsmith_1", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_weaponsmith_2", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_temple_1", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_large_farm_1", orThrow8), 6),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_large_farm_2", orThrow8), 6),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_small_farm_1", orThrow5), 1),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_animal_pen_1", orThrow5), 2),
                    Pair.of(StructurePoolElement.empty(), 6)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/taiga/zombie/houses",
            new StructureTemplatePool(
                orThrow10,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/houses/taiga_small_house_1", orThrow6), 4),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/houses/taiga_small_house_2", orThrow6), 4),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/houses/taiga_small_house_3", orThrow6), 4),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/houses/taiga_small_house_4", orThrow6), 4),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/houses/taiga_small_house_5", orThrow6), 4),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/houses/taiga_medium_house_1", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/houses/taiga_medium_house_2", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/houses/taiga_medium_house_3", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/houses/taiga_medium_house_4", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_butcher_shop_1", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/houses/taiga_tool_smith_1", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_fletcher_house_1", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/houses/taiga_shepherds_house_1", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_armorer_house_1", orThrow6), 1),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/houses/taiga_fisher_cottage_1", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_tannery_1", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/houses/taiga_cartographer_house_1", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/houses/taiga_library_1", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_masons_house_1", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_weaponsmith_1", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/houses/taiga_weaponsmith_2", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/houses/taiga_temple_1", orThrow6), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_large_farm_1", orThrow6), 6),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/houses/taiga_large_farm_2", orThrow6), 6),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_small_farm_1", orThrow6), 1),
                    Pair.of(StructurePoolElement.legacy("village/taiga/houses/taiga_animal_pen_1", orThrow6), 2),
                    Pair.of(StructurePoolElement.empty(), 6)
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
            "village/taiga/decor",
            new StructureTemplatePool(
                orThrow9,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/taiga/taiga_lamp_post_1"), 10),
                    Pair.of(StructurePoolElement.legacy("village/taiga/taiga_decoration_1"), 4),
                    Pair.of(StructurePoolElement.legacy("village/taiga/taiga_decoration_2"), 1),
                    Pair.of(StructurePoolElement.legacy("village/taiga/taiga_decoration_3"), 1),
                    Pair.of(StructurePoolElement.legacy("village/taiga/taiga_decoration_4"), 1),
                    Pair.of(StructurePoolElement.legacy("village/taiga/taiga_decoration_5"), 2),
                    Pair.of(StructurePoolElement.legacy("village/taiga/taiga_decoration_6"), 1),
                    Pair.of(StructurePoolElement.feature(orThrow), 4),
                    Pair.of(StructurePoolElement.feature(orThrow1), 4),
                    Pair.of(StructurePoolElement.feature(orThrow2), 2),
                    Pair.of(StructurePoolElement.feature(orThrow3), 4),
                    Pair.of(StructurePoolElement.feature(orThrow4), 1),
                    Pair.of(StructurePoolElement.empty(), 4)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/taiga/zombie/decor",
            new StructureTemplatePool(
                orThrow9,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/taiga/taiga_decoration_1"), 4),
                    Pair.of(StructurePoolElement.legacy("village/taiga/taiga_decoration_2"), 1),
                    Pair.of(StructurePoolElement.legacy("village/taiga/taiga_decoration_3"), 1),
                    Pair.of(StructurePoolElement.legacy("village/taiga/taiga_decoration_4"), 1),
                    Pair.of(StructurePoolElement.feature(orThrow), 4),
                    Pair.of(StructurePoolElement.feature(orThrow1), 4),
                    Pair.of(StructurePoolElement.feature(orThrow2), 2),
                    Pair.of(StructurePoolElement.feature(orThrow3), 4),
                    Pair.of(StructurePoolElement.feature(orThrow4), 1),
                    Pair.of(StructurePoolElement.empty(), 4)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/taiga/villagers",
            new StructureTemplatePool(
                orThrow9,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/taiga/villagers/nitwit"), 1),
                    Pair.of(StructurePoolElement.legacy("village/taiga/villagers/baby"), 1),
                    Pair.of(StructurePoolElement.legacy("village/taiga/villagers/unemployed"), 10)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/taiga/zombie/villagers",
            new StructureTemplatePool(
                orThrow9,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/villagers/nitwit"), 1),
                    Pair.of(StructurePoolElement.legacy("village/taiga/zombie/villagers/unemployed"), 10)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
    }
}
