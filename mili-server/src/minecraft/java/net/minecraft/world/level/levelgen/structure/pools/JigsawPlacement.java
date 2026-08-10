package net.minecraft.world.level.levelgen.structure.pools;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.SequencedPriorityIterator;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.lang3.mutable.MutableObject;
import org.slf4j.Logger;

public class JigsawPlacement {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final int UNSET_HEIGHT = Integer.MIN_VALUE;

    public static Optional<Structure.GenerationStub> addPieces(
        Structure.GenerationContext context,
        Holder<StructureTemplatePool> startPool,
        Optional<Identifier> startJigsawName,
        int maxDepth,
        BlockPos pos,
        boolean useExpansionHack,
        Optional<Heightmap.Types> projectStartToHeightmap,
        JigsawStructure.MaxDistance maxDistanceFromCenter,
        PoolAliasLookup aliasLookup,
        DimensionPadding dimensionPadding,
        LiquidSettings liquidSettings
    ) {
        RegistryAccess registryAccess = context.registryAccess();
        ChunkGenerator chunkGenerator = context.chunkGenerator();
        StructureTemplateManager structureTemplateManager = context.structureTemplateManager();
        LevelHeightAccessor levelHeightAccessor = context.heightAccessor();
        // Leaf start - Matter - Secure Seed
        WorldgenRandom worldgenRandom = me.earthme.luminol.config.modules.function.SecureSeedConfig.enabled
            ? new su.plo.matter.WorldgenCryptoRandom(context.chunkPos().x, context.chunkPos().z, su.plo.matter.Globals.Salt.JIGSAW_PLACEMENT, 0)
            : context.random();
        // Leaf end - Matter - Secure Seed
        Registry<StructureTemplatePool> registry = registryAccess.lookupOrThrow(Registries.TEMPLATE_POOL);
        Rotation random = Rotation.getRandom(worldgenRandom);
        StructureTemplatePool structureTemplatePool = startPool.unwrapKey()
            .flatMap(key -> registry.getOptional(aliasLookup.lookup((ResourceKey<StructureTemplatePool>)key)))
            .orElse(startPool.value());
        StructurePoolElement randomTemplate = structureTemplatePool.getRandomTemplate(worldgenRandom);
        if (randomTemplate == EmptyPoolElement.INSTANCE) {
            return Optional.empty();
        } else {
            BlockPos blockPos;
            if (startJigsawName.isPresent()) {
                Identifier identifier = startJigsawName.get();
                Optional<BlockPos> randomNamedJigsaw = getRandomNamedJigsaw(randomTemplate, identifier, pos, random, structureTemplateManager, worldgenRandom);
                if (randomNamedJigsaw.isEmpty()) {
                    LOGGER.error(
                        "No starting jigsaw {} found in start pool {}",
                        identifier,
                        startPool.unwrapKey().map(resourceKey -> resourceKey.identifier().toString()).orElse("<unregistered>")
                    );
                    return Optional.empty();
                }

                blockPos = randomNamedJigsaw.get();
            } else {
                blockPos = pos;
            }

            Vec3i vec3i = blockPos.subtract(pos);
            BlockPos blockPos1 = pos.subtract(vec3i);
            PoolElementStructurePiece poolElementStructurePiece = new PoolElementStructurePiece(
                structureTemplateManager,
                randomTemplate,
                blockPos1,
                randomTemplate.getGroundLevelDelta(),
                random,
                randomTemplate.getBoundingBox(structureTemplateManager, blockPos1, random),
                liquidSettings
            );
            BoundingBox boundingBox = poolElementStructurePiece.getBoundingBox();
            int i = (boundingBox.maxX() + boundingBox.minX()) / 2;
            int i1 = (boundingBox.maxZ() + boundingBox.minZ()) / 2;
            int i2 = projectStartToHeightmap.isEmpty()
                ? blockPos1.getY()
                : pos.getY() + chunkGenerator.getFirstFreeHeight(i, i1, projectStartToHeightmap.get(), levelHeightAccessor, context.randomState());
            int i3 = boundingBox.minY() + poolElementStructurePiece.getGroundLevelDelta();
            poolElementStructurePiece.move(0, i2 - i3, 0);
            if (isStartTooCloseToWorldHeightLimits(levelHeightAccessor, dimensionPadding, poolElementStructurePiece.getBoundingBox())) {
                LOGGER.debug(
                    "Center piece {} with bounding box {} does not fit dimension padding {}",
                    randomTemplate,
                    poolElementStructurePiece.getBoundingBox(),
                    dimensionPadding
                );
                return Optional.empty();
            } else {
                int i4 = i2 + vec3i.getY();
                return Optional.of(
                    new Structure.GenerationStub(
                        new BlockPos(i, i4, i1),
                        builder -> {
                            List<PoolElementStructurePiece> list = Lists.newArrayList();
                            list.add(poolElementStructurePiece);
                            if (maxDepth > 0) {
                                AABB aabb = new AABB(
                                    i - maxDistanceFromCenter.horizontal(),
                                    Math.max(i4 - maxDistanceFromCenter.vertical(), levelHeightAccessor.getMinY() + dimensionPadding.bottom()),
                                    i1 - maxDistanceFromCenter.horizontal(),
                                    i + maxDistanceFromCenter.horizontal() + 1,
                                    Math.min(i4 + maxDistanceFromCenter.vertical() + 1, levelHeightAccessor.getMaxY() + 1 - dimensionPadding.top()),
                                    i1 + maxDistanceFromCenter.horizontal() + 1
                                );
                                VoxelShape voxelShape = Shapes.join(Shapes.create(aabb), Shapes.create(AABB.of(boundingBox)), BooleanOp.ONLY_FIRST);
                                addPieces(
                                    context.randomState(),
                                    maxDepth,
                                    useExpansionHack,
                                    chunkGenerator,
                                    structureTemplateManager,
                                    levelHeightAccessor,
                                    worldgenRandom,
                                    registry,
                                    poolElementStructurePiece,
                                    list,
                                    voxelShape,
                                    aliasLookup,
                                    liquidSettings
                                );
                                list.forEach(builder::addPiece);
                            }
                        }
                    )
                );
            }
        }
    }

    private static boolean isStartTooCloseToWorldHeightLimits(LevelHeightAccessor level, DimensionPadding padding, BoundingBox boundingBox) {
        if (padding == DimensionPadding.ZERO) {
            return false;
        } else {
            int i = level.getMinY() + padding.bottom();
            int i1 = level.getMaxY() - padding.top();
            return boundingBox.minY() < i || boundingBox.maxY() > i1;
        }
    }

    private static Optional<BlockPos> getRandomNamedJigsaw(
        StructurePoolElement element,
        Identifier startJigsawName,
        BlockPos pos,
        Rotation rotation,
        StructureTemplateManager structureTemplateManager,
        WorldgenRandom random
    ) {
        for (StructureTemplate.JigsawBlockInfo jigsawBlockInfo : element.getShuffledJigsawBlocks(structureTemplateManager, pos, rotation, random)) {
            if (startJigsawName.equals(jigsawBlockInfo.name())) {
                return Optional.of(jigsawBlockInfo.info().pos());
            }
        }

        return Optional.empty();
    }

    private static void addPieces(
        RandomState randomState,
        int maxDepth,
        boolean useExpansionHack,
        ChunkGenerator chunkGenerator,
        StructureTemplateManager structureTemplateManager,
        LevelHeightAccessor level,
        RandomSource random,
        Registry<StructureTemplatePool> pools,
        PoolElementStructurePiece startPiece,
        List<PoolElementStructurePiece> pieces,
        VoxelShape free,
        PoolAliasLookup aliasLookup,
        LiquidSettings liquidSettings
    ) {
        JigsawPlacement.Placer placer = new JigsawPlacement.Placer(pools, maxDepth, chunkGenerator, structureTemplateManager, pieces, random);
        placer.tryPlacingChildren(startPiece, new MutableObject<>(free), 0, useExpansionHack, level, randomState, aliasLookup, liquidSettings);

        while (placer.placing.hasNext()) {
            JigsawPlacement.PieceState pieceState = placer.placing.next();
            placer.tryPlacingChildren(pieceState.piece, pieceState.free, pieceState.depth, useExpansionHack, level, randomState, aliasLookup, liquidSettings);
        }
    }

    public static boolean generateJigsaw(
        ServerLevel level, Holder<StructureTemplatePool> startPool, Identifier startJigsawName, int maxDepth, BlockPos pos, boolean keepJigsaws
    ) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        StructureTemplateManager structureManager = level.getStructureManager();
        StructureManager structureManager1 = level.structureManager();
        RandomSource random = level.getRandom();
        Structure.GenerationContext generationContext = new Structure.GenerationContext(
            level.registryAccess(),
            generator,
            generator.getBiomeSource(),
            level.getChunkSource().randomState(),
            structureManager,
            level.getSeed(),
            new ChunkPos(pos),
            level,
            holder -> true
        );
        Optional<Structure.GenerationStub> optional = addPieces(
            generationContext,
            startPool,
            Optional.of(startJigsawName),
            maxDepth,
            pos,
            false,
            Optional.empty(),
            new JigsawStructure.MaxDistance(128),
            PoolAliasLookup.EMPTY,
            JigsawStructure.DEFAULT_DIMENSION_PADDING,
            JigsawStructure.DEFAULT_LIQUID_SETTINGS
        );
        if (optional.isPresent()) {
            StructurePiecesBuilder piecesBuilder = optional.get().getPiecesBuilder();

            for (StructurePiece structurePiece : piecesBuilder.build().pieces()) {
                if (structurePiece instanceof PoolElementStructurePiece poolElementStructurePiece) {
                    poolElementStructurePiece.place(level, structureManager1, generator, random, BoundingBox.infinite(), pos, keepJigsaws);
                }
            }

            return true;
        } else {
            return false;
        }
    }

    record PieceState(PoolElementStructurePiece piece, MutableObject<VoxelShape> free, int depth) {
    }

    static final class Placer {
        private final Registry<StructureTemplatePool> pools;
        private final int maxDepth;
        private final ChunkGenerator chunkGenerator;
        private final StructureTemplateManager structureTemplateManager;
        private final List<? super PoolElementStructurePiece> pieces;
        private final RandomSource random;
        final SequencedPriorityIterator<JigsawPlacement.PieceState> placing = new SequencedPriorityIterator<>();

        Placer(
            Registry<StructureTemplatePool> pools,
            int maxDepth,
            ChunkGenerator chunkGenerator,
            StructureTemplateManager structureTemplateManager,
            List<? super PoolElementStructurePiece> pieces,
            RandomSource random
        ) {
            this.pools = pools;
            this.maxDepth = maxDepth;
            this.chunkGenerator = chunkGenerator;
            this.structureTemplateManager = structureTemplateManager;
            this.pieces = pieces;
            this.random = random;
        }

        void tryPlacingChildren(
            PoolElementStructurePiece piece,
            MutableObject<VoxelShape> free,
            int depth,
            boolean useExpansionHack,
            LevelHeightAccessor level,
            RandomState random,
            PoolAliasLookup poolAliasLookup,
            LiquidSettings liquidSettings
        ) {
            StructurePoolElement element = piece.getElement();
            BlockPos position = piece.getPosition();
            Rotation rotation = piece.getRotation();
            StructureTemplatePool.Projection projection = element.getProjection();
            boolean flag = projection == StructureTemplatePool.Projection.RIGID;
            MutableObject<VoxelShape> mutableObject = new MutableObject<>();
            BoundingBox boundingBox = piece.getBoundingBox();
            int minY = boundingBox.minY();

            label129:
            for (StructureTemplate.JigsawBlockInfo jigsawBlockInfo : element.getShuffledJigsawBlocks(
                this.structureTemplateManager, position, rotation, this.random
            )) {
                StructureTemplate.StructureBlockInfo structureBlockInfo = jigsawBlockInfo.info();
                Direction frontFacing = JigsawBlock.getFrontFacing(structureBlockInfo.state());
                BlockPos blockPos = structureBlockInfo.pos();
                BlockPos blockPos1 = blockPos.relative(frontFacing);
                int i = blockPos.getY() - minY;
                int i1 = Integer.MIN_VALUE;
                ResourceKey<StructureTemplatePool> resourceKey = poolAliasLookup.lookup(jigsawBlockInfo.pool());
                Optional<? extends Holder<StructureTemplatePool>> optional = this.pools.get(resourceKey);
                if (optional.isEmpty()) {
                    JigsawPlacement.LOGGER.warn("Empty or non-existent pool: {}", resourceKey.identifier());
                } else {
                    Holder<StructureTemplatePool> holder = (Holder<StructureTemplatePool>)optional.get();
                    if (holder.value().size() == 0 && !holder.is(Pools.EMPTY)) {
                        JigsawPlacement.LOGGER.warn("Empty or non-existent pool: {}", resourceKey.identifier());
                    } else {
                        Holder<StructureTemplatePool> fallback = holder.value().getFallback();
                        if (fallback.value().size() == 0 && !fallback.is(Pools.EMPTY)) {
                            JigsawPlacement.LOGGER
                                .warn(
                                    "Empty or non-existent fallback pool: {}",
                                    fallback.unwrapKey().map(key -> key.identifier().toString()).orElse("<unregistered>")
                                );
                        } else {
                            boolean isInside = boundingBox.isInside(blockPos1);
                            MutableObject<VoxelShape> mutableObject1;
                            if (isInside) {
                                mutableObject1 = mutableObject;
                                if (mutableObject.get() == null) {
                                    mutableObject.setValue(Shapes.create(AABB.of(boundingBox)));
                                }
                            } else {
                                mutableObject1 = free;
                            }

                            List<StructurePoolElement> list = Lists.newArrayList();
                            if (depth != this.maxDepth) {
                                list.addAll(holder.value().getShuffledTemplates(this.random));
                            }

                            list.addAll(fallback.value().getShuffledTemplates(this.random));
                            int placementPriority = jigsawBlockInfo.placementPriority();

                            for (StructurePoolElement structurePoolElement : list) {
                                if (structurePoolElement == EmptyPoolElement.INSTANCE) {
                                    break;
                                }

                                for (Rotation rotation1 : Rotation.getShuffled(this.random)) {
                                    List<StructureTemplate.JigsawBlockInfo> shuffledJigsawBlocks = structurePoolElement.getShuffledJigsawBlocks(
                                        this.structureTemplateManager, BlockPos.ZERO, rotation1, this.random
                                    );
                                    BoundingBox boundingBox1 = structurePoolElement.getBoundingBox(this.structureTemplateManager, BlockPos.ZERO, rotation1);
                                    int i2;
                                    if (useExpansionHack && boundingBox1.getYSpan() <= 16) {
                                        i2 = shuffledJigsawBlocks.stream()
                                            .mapToInt(
                                                info -> {
                                                    StructureTemplate.StructureBlockInfo structureBlockInfo1 = info.info();
                                                    if (!boundingBox1.isInside(
                                                        structureBlockInfo1.pos().relative(JigsawBlock.getFrontFacing(structureBlockInfo1.state()))
                                                    )) {
                                                        return 0;
                                                    } else {
                                                        ResourceKey<StructureTemplatePool> resourceKey1 = poolAliasLookup.lookup(info.pool());
                                                        Optional<? extends Holder<StructureTemplatePool>> optional1 = this.pools.get(resourceKey1);
                                                        Optional<Holder<StructureTemplatePool>> optional2 = optional1.map(
                                                            holder1 -> holder1.value().getFallback()
                                                        );
                                                        int i8 = optional1.<Integer>map(holder1 -> holder1.value().getMaxSize(this.structureTemplateManager))
                                                            .orElse(0);
                                                        int i9 = optional2.<Integer>map(holder1 -> holder1.value().getMaxSize(this.structureTemplateManager))
                                                            .orElse(0);
                                                        return Math.max(i8, i9);
                                                    }
                                                }
                                            )
                                            .max()
                                            .orElse(0);
                                    } else {
                                        i2 = 0;
                                    }

                                    for (StructureTemplate.JigsawBlockInfo jigsawBlockInfo1 : shuffledJigsawBlocks) {
                                        if (JigsawBlock.canAttach(jigsawBlockInfo, jigsawBlockInfo1)) {
                                            BlockPos blockPos2 = jigsawBlockInfo1.info().pos();
                                            BlockPos blockPos3 = blockPos1.subtract(blockPos2);
                                            BoundingBox boundingBox2 = structurePoolElement.getBoundingBox(this.structureTemplateManager, blockPos3, rotation1);
                                            int minY1 = boundingBox2.minY();
                                            StructureTemplatePool.Projection projection1 = structurePoolElement.getProjection();
                                            boolean flag1 = projection1 == StructureTemplatePool.Projection.RIGID;
                                            int y = blockPos2.getY();
                                            int i3 = i - y + JigsawBlock.getFrontFacing(structureBlockInfo.state()).getStepY();
                                            int i4;
                                            if (flag && flag1) {
                                                i4 = minY + i3;
                                            } else {
                                                if (i1 == Integer.MIN_VALUE) {
                                                    i1 = this.chunkGenerator
                                                        .getFirstFreeHeight(blockPos.getX(), blockPos.getZ(), Heightmap.Types.WORLD_SURFACE_WG, level, random);
                                                }

                                                i4 = i1 - y;
                                            }

                                            int i5 = i4 - minY1;
                                            BoundingBox boundingBox3 = boundingBox2.moved(0, i5, 0);
                                            BlockPos blockPos4 = blockPos3.offset(0, i5, 0);
                                            if (i2 > 0) {
                                                int max = Math.max(i2 + 1, boundingBox3.maxY() - boundingBox3.minY());
                                                boundingBox3.encapsulate(new BlockPos(boundingBox3.minX(), boundingBox3.minY() + max, boundingBox3.minZ()));
                                            }

                                            if (!Shapes.joinIsNotEmpty(
                                                mutableObject1.get(), Shapes.create(AABB.of(boundingBox3).deflate(0.25)), BooleanOp.ONLY_SECOND
                                            )) {
                                                mutableObject1.setValue(
                                                    Shapes.joinUnoptimized(mutableObject1.get(), Shapes.create(AABB.of(boundingBox3)), BooleanOp.ONLY_FIRST)
                                                );
                                                int max = piece.getGroundLevelDelta();
                                                int i6;
                                                if (flag1) {
                                                    i6 = max - i3;
                                                } else {
                                                    i6 = structurePoolElement.getGroundLevelDelta();
                                                }

                                                PoolElementStructurePiece poolElementStructurePiece = new PoolElementStructurePiece(
                                                    this.structureTemplateManager, structurePoolElement, blockPos4, i6, rotation1, boundingBox3, liquidSettings
                                                );
                                                int i7;
                                                if (flag) {
                                                    i7 = minY + i;
                                                } else if (flag1) {
                                                    i7 = i4 + y;
                                                } else {
                                                    if (i1 == Integer.MIN_VALUE) {
                                                        i1 = this.chunkGenerator
                                                            .getFirstFreeHeight(
                                                                blockPos.getX(), blockPos.getZ(), Heightmap.Types.WORLD_SURFACE_WG, level, random
                                                            );
                                                    }

                                                    i7 = i1 + i3 / 2;
                                                }

                                                piece.addJunction(new JigsawJunction(blockPos1.getX(), i7 - i + max, blockPos1.getZ(), i3, projection1));
                                                poolElementStructurePiece.addJunction(
                                                    new JigsawJunction(blockPos.getX(), i7 - y + i6, blockPos.getZ(), -i3, projection)
                                                );
                                                this.pieces.add(poolElementStructurePiece);
                                                if (depth + 1 <= this.maxDepth) {
                                                    JigsawPlacement.PieceState pieceState = new JigsawPlacement.PieceState(
                                                        poolElementStructurePiece, mutableObject1, depth + 1
                                                    );
                                                    this.placing.add(pieceState, placementPriority);
                                                }
                                                continue label129;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
