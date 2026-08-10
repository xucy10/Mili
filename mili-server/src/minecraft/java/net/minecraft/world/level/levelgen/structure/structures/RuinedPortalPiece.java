package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlackstoneReplaceProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockAgeProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.LavaSubmergedBlockProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.ProcessorRule;
import net.minecraft.world.level.levelgen.structure.templatesystem.ProtectedBlockProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.RandomBlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public class RuinedPortalPiece extends TemplateStructurePiece {
    private static final float PROBABILITY_OF_GOLD_GONE = 0.3F;
    private static final float PROBABILITY_OF_MAGMA_INSTEAD_OF_NETHERRACK = 0.07F;
    private static final float PROBABILITY_OF_MAGMA_INSTEAD_OF_LAVA = 0.2F;
    private final RuinedPortalPiece.VerticalPlacement verticalPlacement;
    private final RuinedPortalPiece.Properties properties;

    public RuinedPortalPiece(
        StructureTemplateManager structureTemplateManager,
        BlockPos templatePosition,
        RuinedPortalPiece.VerticalPlacement verticalPlacement,
        RuinedPortalPiece.Properties properties,
        Identifier location,
        StructureTemplate template,
        Rotation rotation,
        Mirror mirror,
        BlockPos pivotPos
    ) {
        super(
            StructurePieceType.RUINED_PORTAL,
            0,
            structureTemplateManager,
            location,
            location.toString(),
            makeSettings(mirror, rotation, verticalPlacement, pivotPos, properties),
            templatePosition
        );
        this.verticalPlacement = verticalPlacement;
        this.properties = properties;
    }

    public RuinedPortalPiece(StructureTemplateManager structureTemplateManager, CompoundTag tag) {
        super(StructurePieceType.RUINED_PORTAL, tag, structureTemplateManager, identifier -> makeSettings(structureTemplateManager, tag, identifier));
        this.verticalPlacement = tag.read("VerticalPlacement", RuinedPortalPiece.VerticalPlacement.CODEC).orElseThrow();
        this.properties = tag.read("Properties", RuinedPortalPiece.Properties.CODEC).orElseThrow();
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.store("Rotation", Rotation.LEGACY_CODEC, this.placeSettings.getRotation());
        tag.store("Mirror", Mirror.LEGACY_CODEC, this.placeSettings.getMirror());
        tag.store("VerticalPlacement", RuinedPortalPiece.VerticalPlacement.CODEC, this.verticalPlacement);
        tag.store("Properties", RuinedPortalPiece.Properties.CODEC, this.properties);
    }

    private static StructurePlaceSettings makeSettings(StructureTemplateManager structureTemplateManager, CompoundTag tag, Identifier location) {
        StructureTemplate structureTemplate = structureTemplateManager.getOrCreate(location);
        BlockPos blockPos = new BlockPos(structureTemplate.getSize().getX() / 2, 0, structureTemplate.getSize().getZ() / 2);
        return makeSettings(
            tag.read("Mirror", Mirror.LEGACY_CODEC).orElseThrow(),
            tag.read("Rotation", Rotation.LEGACY_CODEC).orElseThrow(),
            tag.read("VerticalPlacement", RuinedPortalPiece.VerticalPlacement.CODEC).orElseThrow(),
            blockPos,
            RuinedPortalPiece.Properties.CODEC.parse(new Dynamic<>(NbtOps.INSTANCE, tag.get("Properties"))).getPartialOrThrow()
        );
    }

    private static StructurePlaceSettings makeSettings(
        Mirror mirror, Rotation rotation, RuinedPortalPiece.VerticalPlacement verticalPlacement, BlockPos pos, RuinedPortalPiece.Properties properties
    ) {
        BlockIgnoreProcessor blockIgnoreProcessor = properties.airPocket ? BlockIgnoreProcessor.STRUCTURE_BLOCK : BlockIgnoreProcessor.STRUCTURE_AND_AIR;
        List<ProcessorRule> list = Lists.newArrayList();
        list.add(getBlockReplaceRule(Blocks.GOLD_BLOCK, 0.3F, Blocks.AIR));
        list.add(getLavaProcessorRule(verticalPlacement, properties));
        if (!properties.cold) {
            list.add(getBlockReplaceRule(Blocks.NETHERRACK, 0.07F, Blocks.MAGMA_BLOCK));
        }

        StructurePlaceSettings structurePlaceSettings = new StructurePlaceSettings()
            .setRotation(rotation)
            .setMirror(mirror)
            .setRotationPivot(pos)
            .addProcessor(blockIgnoreProcessor)
            .addProcessor(new RuleProcessor(list))
            .addProcessor(new BlockAgeProcessor(properties.mossiness))
            .addProcessor(new ProtectedBlockProcessor(BlockTags.FEATURES_CANNOT_REPLACE))
            .addProcessor(new LavaSubmergedBlockProcessor());
        if (properties.replaceWithBlackstone) {
            structurePlaceSettings.addProcessor(BlackstoneReplaceProcessor.INSTANCE);
        }

        return structurePlaceSettings;
    }

    private static ProcessorRule getLavaProcessorRule(RuinedPortalPiece.VerticalPlacement verticalPlacement, RuinedPortalPiece.Properties properties) {
        if (verticalPlacement == RuinedPortalPiece.VerticalPlacement.ON_OCEAN_FLOOR) {
            return getBlockReplaceRule(Blocks.LAVA, Blocks.MAGMA_BLOCK);
        } else {
            return properties.cold ? getBlockReplaceRule(Blocks.LAVA, Blocks.NETHERRACK) : getBlockReplaceRule(Blocks.LAVA, 0.2F, Blocks.MAGMA_BLOCK);
        }
    }

    @Override
    public void postProcess(
        WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos
    ) {
        BoundingBox boundingBox = this.template.getBoundingBox(this.placeSettings, this.templatePosition);
        if (box.isInside(boundingBox.getCenter())) {
            box.encapsulate(boundingBox);
            super.postProcess(level, structureManager, generator, random, box, chunkPos, pos);
            this.spreadNetherrack(random, level);
            this.addNetherrackDripColumnsBelowPortal(random, level);
            if (this.properties.vines || this.properties.overgrown) {
                BlockPos.betweenClosedStream(this.getBoundingBox()).forEach(blockPos -> {
                    if (this.properties.vines) {
                        this.maybeAddVines(random, level, blockPos);
                    }

                    if (this.properties.overgrown) {
                        this.maybeAddLeavesAbove(random, level, blockPos);
                    }
                });
            }
        }
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
    }

    private void maybeAddVines(RandomSource random, LevelAccessor level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos);
        if (!blockState.isAir() && !blockState.is(Blocks.VINE)) {
            Direction randomHorizontalDirection = getRandomHorizontalDirection(random);
            BlockPos blockPos = pos.relative(randomHorizontalDirection);
            BlockState blockState1 = level.getBlockState(blockPos);
            if (blockState1.isAir()) {
                if (Block.isFaceFull(blockState.getCollisionShape(level, pos), randomHorizontalDirection)) {
                    BooleanProperty propertyForFace = VineBlock.getPropertyForFace(randomHorizontalDirection.getOpposite());
                    level.setBlock(blockPos, Blocks.VINE.defaultBlockState().setValue(propertyForFace, true), Block.UPDATE_ALL);
                }
            }
        }
    }

    private void maybeAddLeavesAbove(RandomSource random, LevelAccessor level, BlockPos pos) {
        if (random.nextFloat() < 0.5F && level.getBlockState(pos).is(Blocks.NETHERRACK) && level.getBlockState(pos.above()).isAir()) {
            level.setBlock(pos.above(), Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true), Block.UPDATE_ALL);
        }
    }

    private void addNetherrackDripColumnsBelowPortal(RandomSource random, LevelAccessor level) {
        for (int i = this.boundingBox.minX() + 1; i < this.boundingBox.maxX(); i++) {
            for (int i1 = this.boundingBox.minZ() + 1; i1 < this.boundingBox.maxZ(); i1++) {
                BlockPos blockPos = new BlockPos(i, this.boundingBox.minY(), i1);
                if (level.getBlockState(blockPos).is(Blocks.NETHERRACK)) {
                    this.addNetherrackDripColumn(random, level, blockPos.below());
                }
            }
        }
    }

    private void addNetherrackDripColumn(RandomSource random, LevelAccessor level, BlockPos pos) {
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();
        this.placeNetherrackOrMagma(random, level, mutableBlockPos);
        int i = 8;

        while (i > 0 && random.nextFloat() < 0.5F) {
            mutableBlockPos.move(Direction.DOWN);
            i--;
            this.placeNetherrackOrMagma(random, level, mutableBlockPos);
        }
    }

    private void spreadNetherrack(RandomSource random, LevelAccessor level) {
        boolean flag = this.verticalPlacement == RuinedPortalPiece.VerticalPlacement.ON_LAND_SURFACE
            || this.verticalPlacement == RuinedPortalPiece.VerticalPlacement.ON_OCEAN_FLOOR;
        BlockPos center = this.boundingBox.getCenter();
        int x = center.getX();
        int z = center.getZ();
        float[] floats = new float[]{1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.9F, 0.9F, 0.8F, 0.7F, 0.6F, 0.4F, 0.2F};
        int i = floats.length;
        int i1 = (this.boundingBox.getXSpan() + this.boundingBox.getZSpan()) / 2;
        int randomInt = random.nextInt(Math.max(1, 8 - i1 / 2));
        int i2 = 3;
        BlockPos.MutableBlockPos mutableBlockPos = BlockPos.ZERO.mutable();

        for (int i3 = x - i; i3 <= x + i; i3++) {
            for (int i4 = z - i; i4 <= z + i; i4++) {
                int i5 = Math.abs(i3 - x) + Math.abs(i4 - z);
                int max = Math.max(0, i5 + randomInt);
                if (max < i) {
                    float f = floats[max];
                    if (random.nextDouble() < f) {
                        int surfaceY = getSurfaceY(level, i3, i4, this.verticalPlacement);
                        int i6 = flag ? surfaceY : Math.min(this.boundingBox.minY(), surfaceY);
                        mutableBlockPos.set(i3, i6, i4);
                        if (Math.abs(i6 - this.boundingBox.minY()) <= 3 && this.canBlockBeReplacedByNetherrackOrMagma(level, mutableBlockPos)) {
                            this.placeNetherrackOrMagma(random, level, mutableBlockPos);
                            if (this.properties.overgrown) {
                                this.maybeAddLeavesAbove(random, level, mutableBlockPos);
                            }

                            this.addNetherrackDripColumn(random, level, mutableBlockPos.below());
                        }
                    }
                }
            }
        }
    }

    private boolean canBlockBeReplacedByNetherrackOrMagma(LevelAccessor level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos);
        return !blockState.is(Blocks.AIR)
            && !blockState.is(Blocks.OBSIDIAN)
            && !blockState.is(BlockTags.FEATURES_CANNOT_REPLACE)
            && (this.verticalPlacement == RuinedPortalPiece.VerticalPlacement.IN_NETHER || !blockState.is(Blocks.LAVA));
    }

    private void placeNetherrackOrMagma(RandomSource random, LevelAccessor level, BlockPos pos) {
        if (!this.properties.cold && random.nextFloat() < 0.07F) {
            level.setBlock(pos, Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        } else {
            level.setBlock(pos, Blocks.NETHERRACK.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static int getSurfaceY(LevelAccessor level, int x, int z, RuinedPortalPiece.VerticalPlacement verticalPlacement) {
        return level.getHeight(getHeightMapType(verticalPlacement), x, z) - 1;
    }

    public static Heightmap.Types getHeightMapType(RuinedPortalPiece.VerticalPlacement verticalPlacement) {
        return verticalPlacement == RuinedPortalPiece.VerticalPlacement.ON_OCEAN_FLOOR ? Heightmap.Types.OCEAN_FLOOR_WG : Heightmap.Types.WORLD_SURFACE_WG;
    }

    private static ProcessorRule getBlockReplaceRule(Block block, float probability, Block replaceBlock) {
        return new ProcessorRule(new RandomBlockMatchTest(block, probability), AlwaysTrueTest.INSTANCE, replaceBlock.defaultBlockState());
    }

    private static ProcessorRule getBlockReplaceRule(Block block, Block replaceBlock) {
        return new ProcessorRule(new BlockMatchTest(block), AlwaysTrueTest.INSTANCE, replaceBlock.defaultBlockState());
    }

    public static class Properties {
        public static final Codec<RuinedPortalPiece.Properties> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.BOOL.fieldOf("cold").forGetter(properties -> properties.cold),
                    Codec.FLOAT.fieldOf("mossiness").forGetter(properties -> properties.mossiness),
                    Codec.BOOL.fieldOf("air_pocket").forGetter(properties -> properties.airPocket),
                    Codec.BOOL.fieldOf("overgrown").forGetter(properties -> properties.overgrown),
                    Codec.BOOL.fieldOf("vines").forGetter(properties -> properties.vines),
                    Codec.BOOL.fieldOf("replace_with_blackstone").forGetter(properties -> properties.replaceWithBlackstone)
                )
                .apply(instance, RuinedPortalPiece.Properties::new)
        );
        public boolean cold;
        public float mossiness;
        public boolean airPocket;
        public boolean overgrown;
        public boolean vines;
        public boolean replaceWithBlackstone;

        public Properties() {
        }

        public Properties(boolean cold, float mossiness, boolean airPocket, boolean overgrown, boolean vines, boolean replaceWithBlackstone) {
            this.cold = cold;
            this.mossiness = mossiness;
            this.airPocket = airPocket;
            this.overgrown = overgrown;
            this.vines = vines;
            this.replaceWithBlackstone = replaceWithBlackstone;
        }
    }

    public static enum VerticalPlacement implements StringRepresentable {
        ON_LAND_SURFACE("on_land_surface"),
        PARTLY_BURIED("partly_buried"),
        ON_OCEAN_FLOOR("on_ocean_floor"),
        IN_MOUNTAIN("in_mountain"),
        UNDERGROUND("underground"),
        IN_NETHER("in_nether");

        public static final Codec<RuinedPortalPiece.VerticalPlacement> CODEC = StringRepresentable.fromEnum(RuinedPortalPiece.VerticalPlacement::values);
        private final String name;

        private VerticalPlacement(final String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
