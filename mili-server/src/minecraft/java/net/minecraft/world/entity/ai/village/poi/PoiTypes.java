package net.minecraft.world.entity.ai.village.poi;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

public class PoiTypes {
    public static final ResourceKey<PoiType> ARMORER = createKey("armorer");
    public static final ResourceKey<PoiType> BUTCHER = createKey("butcher");
    public static final ResourceKey<PoiType> CARTOGRAPHER = createKey("cartographer");
    public static final ResourceKey<PoiType> CLERIC = createKey("cleric");
    public static final ResourceKey<PoiType> FARMER = createKey("farmer");
    public static final ResourceKey<PoiType> FISHERMAN = createKey("fisherman");
    public static final ResourceKey<PoiType> FLETCHER = createKey("fletcher");
    public static final ResourceKey<PoiType> LEATHERWORKER = createKey("leatherworker");
    public static final ResourceKey<PoiType> LIBRARIAN = createKey("librarian");
    public static final ResourceKey<PoiType> MASON = createKey("mason");
    public static final ResourceKey<PoiType> SHEPHERD = createKey("shepherd");
    public static final ResourceKey<PoiType> TOOLSMITH = createKey("toolsmith");
    public static final ResourceKey<PoiType> WEAPONSMITH = createKey("weaponsmith");
    public static final ResourceKey<PoiType> HOME = createKey("home");
    public static final ResourceKey<PoiType> MEETING = createKey("meeting");
    public static final ResourceKey<PoiType> BEEHIVE = createKey("beehive");
    public static final ResourceKey<PoiType> BEE_NEST = createKey("bee_nest");
    public static final ResourceKey<PoiType> NETHER_PORTAL = createKey("nether_portal");
    public static final ResourceKey<PoiType> LODESTONE = createKey("lodestone");
    public static final ResourceKey<PoiType> LIGHTNING_ROD = createKey("lightning_rod");
    public static final ResourceKey<PoiType> TEST_INSTANCE = createKey("test_instance");
    private static final Set<BlockState> BEDS = ImmutableList.of(
            Blocks.RED_BED,
            Blocks.BLACK_BED,
            Blocks.BLUE_BED,
            Blocks.BROWN_BED,
            Blocks.CYAN_BED,
            Blocks.GRAY_BED,
            Blocks.GREEN_BED,
            Blocks.LIGHT_BLUE_BED,
            Blocks.LIGHT_GRAY_BED,
            Blocks.LIME_BED,
            Blocks.MAGENTA_BED,
            Blocks.ORANGE_BED,
            Blocks.PINK_BED,
            Blocks.PURPLE_BED,
            Blocks.WHITE_BED,
            Blocks.YELLOW_BED
        )
        .stream()
        .flatMap(block -> block.getStateDefinition().getPossibleStates().stream())
        .filter(blockState -> blockState.getValue(BedBlock.PART) == BedPart.HEAD)
        .collect(ImmutableSet.toImmutableSet());
    private static final Set<BlockState> CAULDRONS = ImmutableList.of(Blocks.CAULDRON, Blocks.LAVA_CAULDRON, Blocks.WATER_CAULDRON, Blocks.POWDER_SNOW_CAULDRON)
        .stream()
        .flatMap(block -> block.getStateDefinition().getPossibleStates().stream())
        .collect(ImmutableSet.toImmutableSet());
    private static final Set<BlockState> LIGHTNING_RODS = ImmutableList.of(
            Blocks.LIGHTNING_ROD,
            Blocks.EXPOSED_LIGHTNING_ROD,
            Blocks.WEATHERED_LIGHTNING_ROD,
            Blocks.OXIDIZED_LIGHTNING_ROD,
            Blocks.WAXED_LIGHTNING_ROD,
            Blocks.WAXED_EXPOSED_LIGHTNING_ROD,
            Blocks.WAXED_WEATHERED_LIGHTNING_ROD,
            Blocks.WAXED_OXIDIZED_LIGHTNING_ROD
        )
        .stream()
        .flatMap(block -> block.getStateDefinition().getPossibleStates().stream())
        .collect(ImmutableSet.toImmutableSet());
    private static final Map<BlockState, Holder<PoiType>> TYPE_BY_STATE = Maps.newHashMap();

    private static Set<BlockState> getBlockStates(Block block) {
        return ImmutableSet.copyOf(block.getStateDefinition().getPossibleStates());
    }

    private static ResourceKey<PoiType> createKey(String name) {
        return ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, Identifier.withDefaultNamespace(name));
    }

    private static PoiType register(Registry<PoiType> key, ResourceKey<PoiType> value, Set<BlockState> matchingStates, int maxTickets, int validRange) {
        PoiType poiType = new PoiType(matchingStates, maxTickets, validRange);
        Registry.register(key, value, poiType);
        registerBlockStates(key.getOrThrow(value), matchingStates);
        return poiType;
    }

    private static void registerBlockStates(Holder<PoiType> poi, Set<BlockState> states) {
        states.forEach(
            state -> {
                Holder<PoiType> holder = TYPE_BY_STATE.put(state, poi);
                if (holder != null) {
                    throw (IllegalStateException)Util.pauseInIde(
                        new IllegalStateException(String.format(Locale.ROOT, "%s is defined in more than one PoI type", state))
                    );
                }
            }
        );
    }

    public static Optional<Holder<PoiType>> forState(BlockState state) {
        return Optional.ofNullable(TYPE_BY_STATE.get(state));
    }

    public static boolean hasPoi(BlockState state) {
        return TYPE_BY_STATE.containsKey(state);
    }

    public static PoiType bootstrap(Registry<PoiType> registry) {
        register(registry, ARMORER, getBlockStates(Blocks.BLAST_FURNACE), 1, 1);
        register(registry, BUTCHER, getBlockStates(Blocks.SMOKER), 1, 1);
        register(registry, CARTOGRAPHER, getBlockStates(Blocks.CARTOGRAPHY_TABLE), 1, 1);
        register(registry, CLERIC, getBlockStates(Blocks.BREWING_STAND), 1, 1);
        register(registry, FARMER, getBlockStates(Blocks.COMPOSTER), 1, 1);
        register(registry, FISHERMAN, getBlockStates(Blocks.BARREL), 1, 1);
        register(registry, FLETCHER, getBlockStates(Blocks.FLETCHING_TABLE), 1, 1);
        register(registry, LEATHERWORKER, CAULDRONS, 1, 1);
        register(registry, LIBRARIAN, getBlockStates(Blocks.LECTERN), 1, 1);
        register(registry, MASON, getBlockStates(Blocks.STONECUTTER), 1, 1);
        register(registry, SHEPHERD, getBlockStates(Blocks.LOOM), 1, 1);
        register(registry, TOOLSMITH, getBlockStates(Blocks.SMITHING_TABLE), 1, 1);
        register(registry, WEAPONSMITH, getBlockStates(Blocks.GRINDSTONE), 1, 1);
        register(registry, HOME, BEDS, 1, 1);
        register(registry, MEETING, getBlockStates(Blocks.BELL), 32, 6);
        register(registry, BEEHIVE, getBlockStates(Blocks.BEEHIVE), 0, 1);
        register(registry, BEE_NEST, getBlockStates(Blocks.BEE_NEST), 0, 1);
        register(registry, NETHER_PORTAL, getBlockStates(Blocks.NETHER_PORTAL), 0, 1);
        register(registry, LODESTONE, getBlockStates(Blocks.LODESTONE), 0, 1);
        register(registry, TEST_INSTANCE, getBlockStates(Blocks.TEST_INSTANCE_BLOCK), 0, 1);
        return register(registry, LIGHTNING_ROD, LIGHTNING_RODS, 0, 1);
    }
}
