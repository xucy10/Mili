package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBinding;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;

public final class JigsawStructure extends Structure {
    public static final DimensionPadding DEFAULT_DIMENSION_PADDING = DimensionPadding.ZERO;
    public static final LiquidSettings DEFAULT_LIQUID_SETTINGS = LiquidSettings.APPLY_WATERLOGGING;
    public static final int MAX_TOTAL_STRUCTURE_RANGE = 128;
    public static final int MIN_DEPTH = 0;
    public static final int MAX_DEPTH = 20;
    public static final MapCodec<JigsawStructure> CODEC = RecordCodecBuilder.<JigsawStructure>mapCodec(
            instance -> instance.group(
                    settingsCodec(instance),
                    StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(structure -> structure.startPool),
                    Identifier.CODEC.optionalFieldOf("start_jigsaw_name").forGetter(structure -> structure.startJigsawName),
                    Codec.intRange(0, 20).fieldOf("size").forGetter(structure -> structure.maxDepth),
                    HeightProvider.CODEC.fieldOf("start_height").forGetter(structure -> structure.startHeight),
                    Codec.BOOL.fieldOf("use_expansion_hack").forGetter(structure -> structure.useExpansionHack),
                    Heightmap.Types.CODEC.optionalFieldOf("project_start_to_heightmap").forGetter(structure -> structure.projectStartToHeightmap),
                    JigsawStructure.MaxDistance.CODEC.fieldOf("max_distance_from_center").forGetter(structure -> structure.maxDistanceFromCenter),
                    Codec.list(PoolAliasBinding.CODEC).optionalFieldOf("pool_aliases", List.of()).forGetter(structure -> structure.poolAliases),
                    DimensionPadding.CODEC.optionalFieldOf("dimension_padding", DEFAULT_DIMENSION_PADDING).forGetter(structure -> structure.dimensionPadding),
                    LiquidSettings.CODEC.optionalFieldOf("liquid_settings", DEFAULT_LIQUID_SETTINGS).forGetter(structure -> structure.liquidSettings)
                )
                .apply(instance, JigsawStructure::new)
        )
        .validate(JigsawStructure::verifyRange);
    private final Holder<StructureTemplatePool> startPool;
    private final Optional<Identifier> startJigsawName;
    private final int maxDepth;
    private final HeightProvider startHeight;
    private final boolean useExpansionHack;
    private final Optional<Heightmap.Types> projectStartToHeightmap;
    private final JigsawStructure.MaxDistance maxDistanceFromCenter;
    private final List<PoolAliasBinding> poolAliases;
    private final DimensionPadding dimensionPadding;
    private final LiquidSettings liquidSettings;

    private static DataResult<JigsawStructure> verifyRange(JigsawStructure structure) {
        int i = switch (structure.terrainAdaptation()) {
            case NONE -> 0;
            case BURY, BEARD_THIN, BEARD_BOX, ENCAPSULATE -> 12;
        };
        return structure.maxDistanceFromCenter.horizontal() + i > 128
            ? DataResult.error(() -> "Horizontal structure size including terrain adaptation must not exceed 128")
            : DataResult.success(structure);
    }

    public JigsawStructure(
        Structure.StructureSettings settings,
        Holder<StructureTemplatePool> startPool,
        Optional<Identifier> startJigsawName,
        int maxDepth,
        HeightProvider startHeight,
        boolean useExpansionHack,
        Optional<Heightmap.Types> projectStartToHeightmap,
        JigsawStructure.MaxDistance maxDistanceFromCenter,
        List<PoolAliasBinding> poolAliases,
        DimensionPadding dimensionPadding,
        LiquidSettings liquidSettings
    ) {
        super(settings);
        this.startPool = startPool;
        this.startJigsawName = startJigsawName;
        this.maxDepth = maxDepth;
        this.startHeight = startHeight;
        this.useExpansionHack = useExpansionHack;
        this.projectStartToHeightmap = projectStartToHeightmap;
        this.maxDistanceFromCenter = maxDistanceFromCenter;
        this.poolAliases = poolAliases;
        this.dimensionPadding = dimensionPadding;
        this.liquidSettings = liquidSettings;
    }

    public JigsawStructure(
        Structure.StructureSettings settings,
        Holder<StructureTemplatePool> startPool,
        int maxDepth,
        HeightProvider startHeight,
        boolean useExpansionHack,
        Heightmap.Types projectStartToHeightmap
    ) {
        this(
            settings,
            startPool,
            Optional.empty(),
            maxDepth,
            startHeight,
            useExpansionHack,
            Optional.of(projectStartToHeightmap),
            new JigsawStructure.MaxDistance(80),
            List.of(),
            DEFAULT_DIMENSION_PADDING,
            DEFAULT_LIQUID_SETTINGS
        );
    }

    public JigsawStructure(
        Structure.StructureSettings settings, Holder<StructureTemplatePool> startPool, int maxDepth, HeightProvider startHeight, boolean useExpansionHack
    ) {
        this(
            settings,
            startPool,
            Optional.empty(),
            maxDepth,
            startHeight,
            useExpansionHack,
            Optional.empty(),
            new JigsawStructure.MaxDistance(80),
            List.of(),
            DEFAULT_DIMENSION_PADDING,
            DEFAULT_LIQUID_SETTINGS
        );
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        ChunkPos chunkPos = context.chunkPos();
        int i = this.startHeight.sample(context.random(), new WorldGenerationContext(context.chunkGenerator(), context.heightAccessor()));
        BlockPos blockPos = new BlockPos(chunkPos.getMinBlockX(), i, chunkPos.getMinBlockZ());
        return JigsawPlacement.addPieces(
            context,
            this.startPool,
            this.startJigsawName,
            this.maxDepth,
            blockPos,
            this.useExpansionHack,
            this.projectStartToHeightmap,
            this.maxDistanceFromCenter,
            PoolAliasLookup.create(this.poolAliases, blockPos, context.seed()),
            this.dimensionPadding,
            this.liquidSettings
        );
    }

    @Override
    public StructureType<?> type() {
        return StructureType.JIGSAW;
    }

    @VisibleForTesting
    public Holder<StructureTemplatePool> getStartPool() {
        return this.startPool;
    }

    @VisibleForTesting
    public List<PoolAliasBinding> getPoolAliases() {
        return this.poolAliases;
    }

    public record MaxDistance(int horizontal, int vertical) {
        private static final Codec<Integer> HORIZONTAL_VALUE_CODEC = Codec.intRange(1, 128);
        private static final Codec<JigsawStructure.MaxDistance> FULL_CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    HORIZONTAL_VALUE_CODEC.fieldOf("horizontal").forGetter(JigsawStructure.MaxDistance::horizontal),
                    ExtraCodecs.intRange(1, DimensionType.Y_SIZE)
                        .optionalFieldOf("vertical", DimensionType.Y_SIZE)
                        .forGetter(JigsawStructure.MaxDistance::vertical)
                )
                .apply(instance, JigsawStructure.MaxDistance::new)
        );
        public static final Codec<JigsawStructure.MaxDistance> CODEC = Codec.either(FULL_CODEC, HORIZONTAL_VALUE_CODEC)
            .xmap(
                either -> either.map(Function.identity(), JigsawStructure.MaxDistance::new),
                maxDistance -> maxDistance.horizontal == maxDistance.vertical ? Either.right(maxDistance.horizontal) : Either.left(maxDistance)
            );

        public MaxDistance(int distance) {
            this(distance, distance);
        }
    }
}
