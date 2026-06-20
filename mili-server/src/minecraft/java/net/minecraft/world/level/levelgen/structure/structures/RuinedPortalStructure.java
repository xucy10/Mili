package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public class RuinedPortalStructure extends Structure {
    private static final String[] STRUCTURE_LOCATION_PORTALS = new String[]{
        "ruined_portal/portal_1",
        "ruined_portal/portal_2",
        "ruined_portal/portal_3",
        "ruined_portal/portal_4",
        "ruined_portal/portal_5",
        "ruined_portal/portal_6",
        "ruined_portal/portal_7",
        "ruined_portal/portal_8",
        "ruined_portal/portal_9",
        "ruined_portal/portal_10"
    };
    private static final String[] STRUCTURE_LOCATION_GIANT_PORTALS = new String[]{
        "ruined_portal/giant_portal_1", "ruined_portal/giant_portal_2", "ruined_portal/giant_portal_3"
    };
    private static final float PROBABILITY_OF_GIANT_PORTAL = 0.05F;
    private static final int MIN_Y_INDEX = 15;
    private final List<RuinedPortalStructure.Setup> setups;
    public static final MapCodec<RuinedPortalStructure> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                settingsCodec(instance),
                ExtraCodecs.nonEmptyList(RuinedPortalStructure.Setup.CODEC.listOf()).fieldOf("setups").forGetter(structure -> structure.setups)
            )
            .apply(instance, RuinedPortalStructure::new)
    );

    public RuinedPortalStructure(Structure.StructureSettings settings, List<RuinedPortalStructure.Setup> setups) {
        super(settings);
        this.setups = setups;
    }

    public RuinedPortalStructure(Structure.StructureSettings settings, RuinedPortalStructure.Setup setup) {
        this(settings, List.of(setup));
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        RuinedPortalPiece.Properties properties = new RuinedPortalPiece.Properties();
        WorldgenRandom worldgenRandom = context.random();
        RuinedPortalStructure.Setup setup = null;
        if (this.setups.size() > 1) {
            float f = 0.0F;

            for (RuinedPortalStructure.Setup setup1 : this.setups) {
                f += setup1.weight();
            }

            float randomFloat = worldgenRandom.nextFloat();

            for (RuinedPortalStructure.Setup setup2 : this.setups) {
                randomFloat -= setup2.weight() / f;
                if (randomFloat < 0.0F) {
                    setup = setup2;
                    break;
                }
            }
        } else {
            setup = this.setups.get(0);
        }

        if (setup == null) {
            throw new IllegalStateException();
        } else {
            RuinedPortalStructure.Setup setup3 = setup;
            properties.airPocket = sample(worldgenRandom, setup3.airPocketProbability());
            properties.mossiness = setup3.mossiness();
            properties.overgrown = setup3.overgrown();
            properties.vines = setup3.vines();
            properties.replaceWithBlackstone = setup3.replaceWithBlackstone();
            Identifier identifier;
            if (worldgenRandom.nextFloat() < 0.05F) {
                identifier = Identifier.withDefaultNamespace(STRUCTURE_LOCATION_GIANT_PORTALS[worldgenRandom.nextInt(STRUCTURE_LOCATION_GIANT_PORTALS.length)]);
            } else {
                identifier = Identifier.withDefaultNamespace(STRUCTURE_LOCATION_PORTALS[worldgenRandom.nextInt(STRUCTURE_LOCATION_PORTALS.length)]);
            }

            StructureTemplate structureTemplate = context.structureTemplateManager().getOrCreate(identifier);
            Rotation rotation = Util.getRandom(Rotation.values(), worldgenRandom);
            Mirror mirror = worldgenRandom.nextFloat() < 0.5F ? Mirror.NONE : Mirror.FRONT_BACK;
            BlockPos blockPos = new BlockPos(structureTemplate.getSize().getX() / 2, 0, structureTemplate.getSize().getZ() / 2);
            ChunkGenerator chunkGenerator = context.chunkGenerator();
            LevelHeightAccessor levelHeightAccessor = context.heightAccessor();
            RandomState randomState = context.randomState();
            BlockPos worldPosition = context.chunkPos().getWorldPosition();
            BoundingBox boundingBox = structureTemplate.getBoundingBox(worldPosition, rotation, blockPos, mirror);
            BlockPos center = boundingBox.getCenter();
            int i = chunkGenerator.getBaseHeight(
                    center.getX(), center.getZ(), RuinedPortalPiece.getHeightMapType(setup3.placement()), levelHeightAccessor, randomState
                )
                - 1;
            int i1 = findSuitableY(
                worldgenRandom,
                chunkGenerator,
                setup3.placement(),
                properties.airPocket,
                i,
                boundingBox.getYSpan(),
                boundingBox,
                levelHeightAccessor,
                randomState
            );
            BlockPos blockPos1 = new BlockPos(worldPosition.getX(), i1, worldPosition.getZ());
            return Optional.of(
                new Structure.GenerationStub(
                    blockPos1,
                    structurePiecesBuilder -> {
                        if (setup3.canBeCold()) {
                            properties.cold = isCold(
                                blockPos1,
                                context.chunkGenerator()
                                    .getBiomeSource()
                                    .getNoiseBiome(
                                        QuartPos.fromBlock(blockPos1.getX()),
                                        QuartPos.fromBlock(blockPos1.getY()),
                                        QuartPos.fromBlock(blockPos1.getZ()),
                                        randomState.sampler()
                                    ),
                                chunkGenerator.getSeaLevel()
                            );
                        }

                        structurePiecesBuilder.addPiece(
                            new RuinedPortalPiece(
                                context.structureTemplateManager(),
                                blockPos1,
                                setup3.placement(),
                                properties,
                                identifier,
                                structureTemplate,
                                rotation,
                                mirror,
                                blockPos
                            )
                        );
                    }
                )
            );
        }
    }

    private static boolean sample(WorldgenRandom random, float threshold) {
        return threshold != 0.0F && (threshold == 1.0F || random.nextFloat() < threshold);
    }

    private static boolean isCold(BlockPos pos, Holder<Biome> biome, int seaLevel) {
        return biome.value().coldEnoughToSnow(pos, seaLevel);
    }

    private static int findSuitableY(
        RandomSource random,
        ChunkGenerator chunkGenerator,
        RuinedPortalPiece.VerticalPlacement verticalPlacement,
        boolean airPocket,
        int height,
        int blockCountY,
        BoundingBox box,
        LevelHeightAccessor level,
        RandomState randomState
    ) {
        int i = level.getMinY() + 15;
        int i1;
        if (verticalPlacement == RuinedPortalPiece.VerticalPlacement.IN_NETHER) {
            if (airPocket) {
                i1 = Mth.randomBetweenInclusive(random, 32, 100);
            } else if (random.nextFloat() < 0.5F) {
                i1 = Mth.randomBetweenInclusive(random, 27, 29);
            } else {
                i1 = Mth.randomBetweenInclusive(random, 29, 100);
            }
        } else if (verticalPlacement == RuinedPortalPiece.VerticalPlacement.IN_MOUNTAIN) {
            int i2 = height - blockCountY;
            i1 = getRandomWithinInterval(random, 70, i2);
        } else if (verticalPlacement == RuinedPortalPiece.VerticalPlacement.UNDERGROUND) {
            int i2 = height - blockCountY;
            i1 = getRandomWithinInterval(random, i, i2);
        } else if (verticalPlacement == RuinedPortalPiece.VerticalPlacement.PARTLY_BURIED) {
            i1 = height - blockCountY + Mth.randomBetweenInclusive(random, 2, 8);
        } else {
            i1 = height;
        }

        List<BlockPos> list = ImmutableList.of(
            new BlockPos(box.minX(), 0, box.minZ()),
            new BlockPos(box.maxX(), 0, box.minZ()),
            new BlockPos(box.minX(), 0, box.maxZ()),
            new BlockPos(box.maxX(), 0, box.maxZ())
        );
        List<NoiseColumn> list1 = list.stream()
            .map(pos -> chunkGenerator.getBaseColumn(pos.getX(), pos.getZ(), level, randomState))
            .collect(Collectors.toList());
        Heightmap.Types types = verticalPlacement == RuinedPortalPiece.VerticalPlacement.ON_OCEAN_FLOOR
            ? Heightmap.Types.OCEAN_FLOOR_WG
            : Heightmap.Types.WORLD_SURFACE_WG;

        int i3;
        for (i3 = i1; i3 > i; i3--) {
            int i4 = 0;

            for (NoiseColumn noiseColumn : list1) {
                BlockState block = noiseColumn.getBlock(i3);
                if (types.isOpaque().test(block)) {
                    if (++i4 == 3) {
                        return i3;
                    }
                }
            }
        }

        return i3;
    }

    private static int getRandomWithinInterval(RandomSource random, int min, int max) {
        return min < max ? Mth.randomBetweenInclusive(random, min, max) : max;
    }

    @Override
    public StructureType<?> type() {
        return StructureType.RUINED_PORTAL;
    }

    public record Setup(
        RuinedPortalPiece.VerticalPlacement placement,
        float airPocketProbability,
        float mossiness,
        boolean overgrown,
        boolean vines,
        boolean canBeCold,
        boolean replaceWithBlackstone,
        float weight
    ) {
        public static final Codec<RuinedPortalStructure.Setup> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    RuinedPortalPiece.VerticalPlacement.CODEC.fieldOf("placement").forGetter(RuinedPortalStructure.Setup::placement),
                    Codec.floatRange(0.0F, 1.0F).fieldOf("air_pocket_probability").forGetter(RuinedPortalStructure.Setup::airPocketProbability),
                    Codec.floatRange(0.0F, 1.0F).fieldOf("mossiness").forGetter(RuinedPortalStructure.Setup::mossiness),
                    Codec.BOOL.fieldOf("overgrown").forGetter(RuinedPortalStructure.Setup::overgrown),
                    Codec.BOOL.fieldOf("vines").forGetter(RuinedPortalStructure.Setup::vines),
                    Codec.BOOL.fieldOf("can_be_cold").forGetter(RuinedPortalStructure.Setup::canBeCold),
                    Codec.BOOL.fieldOf("replace_with_blackstone").forGetter(RuinedPortalStructure.Setup::replaceWithBlackstone),
                    ExtraCodecs.POSITIVE_FLOAT.fieldOf("weight").forGetter(RuinedPortalStructure.Setup::weight)
                )
                .apply(instance, RuinedPortalStructure.Setup::new)
        );
    }
}
