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

public class SnowyVillagePools {
    public static final ResourceKey<StructureTemplatePool> START = Pools.createKey("village/snowy/town_centers");
    private static final ResourceKey<StructureTemplatePool> TERMINATORS_KEY = Pools.createKey("village/snowy/terminators");

    public static void bootstrap(BootstrapContext<StructureTemplatePool> context) {
        HolderGetter<PlacedFeature> holderGetter = context.lookup(Registries.PLACED_FEATURE);
        Holder<PlacedFeature> orThrow = holderGetter.getOrThrow(VillagePlacements.SPRUCE_VILLAGE);
        Holder<PlacedFeature> orThrow1 = holderGetter.getOrThrow(VillagePlacements.PILE_SNOW_VILLAGE);
        Holder<PlacedFeature> orThrow2 = holderGetter.getOrThrow(VillagePlacements.PILE_ICE_VILLAGE);
        HolderGetter<StructureProcessorList> holderGetter1 = context.lookup(Registries.PROCESSOR_LIST);
        Holder<StructureProcessorList> orThrow3 = holderGetter1.getOrThrow(ProcessorLists.STREET_SNOWY_OR_TAIGA);
        Holder<StructureProcessorList> orThrow4 = holderGetter1.getOrThrow(ProcessorLists.FARM_SNOWY);
        Holder<StructureProcessorList> orThrow5 = holderGetter1.getOrThrow(ProcessorLists.ZOMBIE_SNOWY);
        HolderGetter<StructureTemplatePool> holderGetter2 = context.lookup(Registries.TEMPLATE_POOL);
        Holder<StructureTemplatePool> orThrow6 = holderGetter2.getOrThrow(Pools.EMPTY);
        Holder<StructureTemplatePool> orThrow7 = holderGetter2.getOrThrow(TERMINATORS_KEY);
        context.register(
            START,
            new StructureTemplatePool(
                orThrow6,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/snowy/town_centers/snowy_meeting_point_1"), 100),
                    Pair.of(StructurePoolElement.legacy("village/snowy/town_centers/snowy_meeting_point_2"), 50),
                    Pair.of(StructurePoolElement.legacy("village/snowy/town_centers/snowy_meeting_point_3"), 150),
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/town_centers/snowy_meeting_point_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/town_centers/snowy_meeting_point_2"), 1),
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/town_centers/snowy_meeting_point_3"), 3)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/snowy/streets",
            new StructureTemplatePool(
                orThrow7,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/snowy/streets/corner_01", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/streets/corner_02", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/streets/corner_03", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/streets/square_01", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/streets/straight_01", orThrow3), 4),
                    Pair.of(StructurePoolElement.legacy("village/snowy/streets/straight_02", orThrow3), 4),
                    Pair.of(StructurePoolElement.legacy("village/snowy/streets/straight_03", orThrow3), 4),
                    Pair.of(StructurePoolElement.legacy("village/snowy/streets/straight_04", orThrow3), 7),
                    Pair.of(StructurePoolElement.legacy("village/snowy/streets/straight_06", orThrow3), 4),
                    Pair.of(StructurePoolElement.legacy("village/snowy/streets/straight_08", orThrow3), 4),
                    Pair.of(StructurePoolElement.legacy("village/snowy/streets/crossroad_02", orThrow3), 1),
                    Pair.of(StructurePoolElement.legacy("village/snowy/streets/crossroad_03", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/streets/crossroad_04", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/streets/crossroad_05", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/streets/crossroad_06", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/streets/turn_01", orThrow3), 3)
                ),
                StructureTemplatePool.Projection.TERRAIN_MATCHING
            )
        );
        Pools.register(
            context,
            "village/snowy/zombie/streets",
            new StructureTemplatePool(
                orThrow7,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/streets/corner_01", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/streets/corner_02", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/streets/corner_03", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/streets/square_01", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/streets/straight_01", orThrow3), 4),
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/streets/straight_02", orThrow3), 4),
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/streets/straight_03", orThrow3), 4),
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/streets/straight_04", orThrow3), 7),
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/streets/straight_06", orThrow3), 4),
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/streets/straight_08", orThrow3), 4),
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/streets/crossroad_02", orThrow3), 1),
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/streets/crossroad_03", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/streets/crossroad_04", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/streets/crossroad_05", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/streets/crossroad_06", orThrow3), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/streets/turn_01", orThrow3), 3)
                ),
                StructureTemplatePool.Projection.TERRAIN_MATCHING
            )
        );
        Pools.register(
            context,
            "village/snowy/houses",
            new StructureTemplatePool(
                orThrow7,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_small_house_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_small_house_2"), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_small_house_3"), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_small_house_4"), 3),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_small_house_5"), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_small_house_6"), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_small_house_7"), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_small_house_8"), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_medium_house_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_medium_house_2"), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_medium_house_3"), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_butchers_shop_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_butchers_shop_2"), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_tool_smith_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_fletcher_house_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_shepherds_house_1"), 3),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_armorer_house_1"), 1),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_armorer_house_2"), 1),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_fisher_cottage"), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_tannery_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_cartographer_house_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_library_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_masons_house_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_masons_house_2"), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_weapon_smith_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_temple_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_farm_1", orThrow4), 3),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_farm_2", orThrow4), 3),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_animal_pen_1"), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_animal_pen_2"), 2),
                    Pair.of(StructurePoolElement.empty(), 6)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/snowy/zombie/houses",
            new StructureTemplatePool(
                orThrow7,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/houses/snowy_small_house_1", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/houses/snowy_small_house_2", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/houses/snowy_small_house_3", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/houses/snowy_small_house_4", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/houses/snowy_small_house_5", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/houses/snowy_small_house_6", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/houses/snowy_small_house_7", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/houses/snowy_small_house_8", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/houses/snowy_medium_house_1", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/houses/snowy_medium_house_2", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/houses/snowy_medium_house_3", orThrow5), 1),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_butchers_shop_1", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_butchers_shop_2", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_tool_smith_1", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_fletcher_house_1", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_shepherds_house_1", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_armorer_house_1", orThrow5), 1),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_armorer_house_2", orThrow5), 1),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_fisher_cottage", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_tannery_1", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_cartographer_house_1", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_library_1", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_masons_house_1", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_masons_house_2", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_weapon_smith_1", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_temple_1", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_farm_1", orThrow5), 3),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_farm_2", orThrow5), 3),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_animal_pen_1", orThrow5), 2),
                    Pair.of(StructurePoolElement.legacy("village/snowy/houses/snowy_animal_pen_2", orThrow5), 2),
                    Pair.of(StructurePoolElement.empty(), 6)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        context.register(
            TERMINATORS_KEY,
            new StructureTemplatePool(
                orThrow6,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/plains/terminators/terminator_01", orThrow3), 1),
                    Pair.of(StructurePoolElement.legacy("village/plains/terminators/terminator_02", orThrow3), 1),
                    Pair.of(StructurePoolElement.legacy("village/plains/terminators/terminator_03", orThrow3), 1),
                    Pair.of(StructurePoolElement.legacy("village/plains/terminators/terminator_04", orThrow3), 1)
                ),
                StructureTemplatePool.Projection.TERRAIN_MATCHING
            )
        );
        Pools.register(
            context,
            "village/snowy/trees",
            new StructureTemplatePool(orThrow6, ImmutableList.of(Pair.of(StructurePoolElement.feature(orThrow), 1)), StructureTemplatePool.Projection.RIGID)
        );
        Pools.register(
            context,
            "village/snowy/decor",
            new StructureTemplatePool(
                orThrow6,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/snowy/snowy_lamp_post_01"), 4),
                    Pair.of(StructurePoolElement.legacy("village/snowy/snowy_lamp_post_02"), 4),
                    Pair.of(StructurePoolElement.legacy("village/snowy/snowy_lamp_post_03"), 1),
                    Pair.of(StructurePoolElement.feature(orThrow), 4),
                    Pair.of(StructurePoolElement.feature(orThrow1), 4),
                    Pair.of(StructurePoolElement.feature(orThrow2), 1),
                    Pair.of(StructurePoolElement.empty(), 9)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/snowy/zombie/decor",
            new StructureTemplatePool(
                orThrow6,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/snowy/snowy_lamp_post_01", orThrow5), 1),
                    Pair.of(StructurePoolElement.legacy("village/snowy/snowy_lamp_post_02", orThrow5), 1),
                    Pair.of(StructurePoolElement.legacy("village/snowy/snowy_lamp_post_03", orThrow5), 1),
                    Pair.of(StructurePoolElement.feature(orThrow), 4),
                    Pair.of(StructurePoolElement.feature(orThrow1), 4),
                    Pair.of(StructurePoolElement.feature(orThrow2), 4),
                    Pair.of(StructurePoolElement.empty(), 7)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/snowy/villagers",
            new StructureTemplatePool(
                orThrow6,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/snowy/villagers/nitwit"), 1),
                    Pair.of(StructurePoolElement.legacy("village/snowy/villagers/baby"), 1),
                    Pair.of(StructurePoolElement.legacy("village/snowy/villagers/unemployed"), 10)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
        Pools.register(
            context,
            "village/snowy/zombie/villagers",
            new StructureTemplatePool(
                orThrow6,
                ImmutableList.of(
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/villagers/nitwit"), 1),
                    Pair.of(StructurePoolElement.legacy("village/snowy/zombie/villagers/unemployed"), 10)
                ),
                StructureTemplatePool.Projection.RIGID
            )
        );
    }
}
