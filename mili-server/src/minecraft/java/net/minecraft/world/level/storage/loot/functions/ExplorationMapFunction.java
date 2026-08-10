package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;

public class ExplorationMapFunction extends LootItemConditionalFunction {
    public static final TagKey<Structure> DEFAULT_DESTINATION = StructureTags.ON_TREASURE_MAPS;
    public static final Holder<MapDecorationType> DEFAULT_DECORATION = MapDecorationTypes.WOODLAND_MANSION;
    public static final byte DEFAULT_ZOOM = 2;
    public static final int DEFAULT_SEARCH_RADIUS = 50;
    public static final boolean DEFAULT_SKIP_EXISTING = true;
    public static final MapCodec<ExplorationMapFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
            .and(
                instance.group(
                    TagKey.codec(Registries.STRUCTURE)
                        .optionalFieldOf("destination", DEFAULT_DESTINATION)
                        .forGetter(explorationMapFunction -> explorationMapFunction.destination),
                    MapDecorationType.CODEC
                        .optionalFieldOf("decoration", DEFAULT_DECORATION)
                        .forGetter(explorationMapFunction -> explorationMapFunction.mapDecoration),
                    Codec.BYTE.optionalFieldOf("zoom", (byte)2).forGetter(explorationMapFunction -> explorationMapFunction.zoom),
                    Codec.INT.optionalFieldOf("search_radius", 50).forGetter(explorationMapFunction -> explorationMapFunction.searchRadius),
                    Codec.BOOL.optionalFieldOf("skip_existing_chunks", true).forGetter(explorationMapFunction -> explorationMapFunction.skipKnownStructures)
                )
            )
            .apply(instance, ExplorationMapFunction::new)
    );
    private final TagKey<Structure> destination;
    private final Holder<MapDecorationType> mapDecoration;
    private final byte zoom;
    private final int searchRadius;
    private final boolean skipKnownStructures;

    ExplorationMapFunction(
        List<LootItemCondition> predicates,
        TagKey<Structure> destination,
        Holder<MapDecorationType> mapDecoration,
        byte zoom,
        int searchRadius,
        boolean skipKnownStructures
    ) {
        super(predicates);
        this.destination = destination;
        this.mapDecoration = mapDecoration;
        this.zoom = zoom;
        this.searchRadius = searchRadius;
        this.skipKnownStructures = skipKnownStructures;
    }

    @Override
    public LootItemFunctionType<ExplorationMapFunction> getType() {
        return LootItemFunctions.EXPLORATION_MAP;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.ORIGIN);
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        if (!stack.is(Items.MAP)) {
            return stack;
        } else {
            Vec3 vec3 = context.getOptionalParameter(LootContextParams.ORIGIN);
            if (vec3 != null) {
                ServerLevel level = context.getLevel();
                // Paper start - Configurable cartographer treasure maps
                if (!level.paperConfig().environment.treasureMaps.enabled) {
                    /*
                     * NOTE: I fear users will just get a plain map as their "treasure"
                     * This is preferable to disrespecting the config.
                     */
                    return stack;
                }
                // Paper end - Configurable cartographer treasure maps
                BlockPos blockPos = level.findNearestMapStructure(this.destination, BlockPos.containing(vec3), this.searchRadius, !level.paperConfig().environment.treasureMaps.findAlreadyDiscoveredLootTable.or(!this.skipKnownStructures)); // Paper - Configurable cartographer treasure maps
                if (blockPos != null) {
                    ItemStack itemStack = MapItem.create(level, blockPos.getX(), blockPos.getZ(), this.zoom, true, true);
                    MapItem.renderBiomePreviewMap(level, itemStack);
                    MapItemSavedData.addTargetDecoration(itemStack, blockPos, "+", this.mapDecoration);
                    return itemStack;
                }
            }

            return stack;
        }
    }

    public static ExplorationMapFunction.Builder makeExplorationMap() {
        return new ExplorationMapFunction.Builder();
    }

    public static class Builder extends LootItemConditionalFunction.Builder<ExplorationMapFunction.Builder> {
        private TagKey<Structure> destination = ExplorationMapFunction.DEFAULT_DESTINATION;
        private Holder<MapDecorationType> mapDecoration = ExplorationMapFunction.DEFAULT_DECORATION;
        private byte zoom = 2;
        private int searchRadius = 50;
        private boolean skipKnownStructures = true;

        @Override
        protected ExplorationMapFunction.Builder getThis() {
            return this;
        }

        public ExplorationMapFunction.Builder setDestination(TagKey<Structure> destination) {
            this.destination = destination;
            return this;
        }

        public ExplorationMapFunction.Builder setMapDecoration(Holder<MapDecorationType> mapDecoration) {
            this.mapDecoration = mapDecoration;
            return this;
        }

        public ExplorationMapFunction.Builder setZoom(byte zoom) {
            this.zoom = zoom;
            return this;
        }

        public ExplorationMapFunction.Builder setSearchRadius(int searchRadius) {
            this.searchRadius = searchRadius;
            return this;
        }

        public ExplorationMapFunction.Builder setSkipKnownStructures(boolean skipKnownStructures) {
            this.skipKnownStructures = skipKnownStructures;
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new ExplorationMapFunction(
                this.getConditions(), this.destination, this.mapDecoration, this.zoom, this.searchRadius, this.skipKnownStructures
            );
        }
    }
}
