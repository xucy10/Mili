package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

public class WoodlandMansionStructure extends Structure {
    public static final MapCodec<WoodlandMansionStructure> CODEC = simpleCodec(WoodlandMansionStructure::new);

    public WoodlandMansionStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        Rotation random = Rotation.getRandom(context.random());
        BlockPos lowestYIn5by5BoxOffset7Blocks = this.getLowestYIn5by5BoxOffset7Blocks(context, random);
        return lowestYIn5by5BoxOffset7Blocks.getY() < 60
            ? Optional.empty()
            : Optional.of(
                new Structure.GenerationStub(
                    lowestYIn5by5BoxOffset7Blocks,
                    structurePiecesBuilder -> this.generatePieces(structurePiecesBuilder, context, lowestYIn5by5BoxOffset7Blocks, random)
                )
            );
    }

    private void generatePieces(StructurePiecesBuilder builder, Structure.GenerationContext context, BlockPos pos, Rotation rotation) {
        List<WoodlandMansionPieces.WoodlandMansionPiece> list = Lists.newLinkedList();
        WoodlandMansionPieces.generateMansion(context.structureTemplateManager(), pos, rotation, list, context.random());
        list.forEach(builder::addPiece);
    }

    @Override
    public void afterPlace(
        WorldGenLevel level,
        StructureManager structureManager,
        ChunkGenerator chunkGenerator,
        RandomSource random,
        BoundingBox boundingBox,
        ChunkPos chunkPos,
        PiecesContainer pieces
    ) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        int minY = level.getMinY();
        BoundingBox boundingBox1 = pieces.calculateBoundingBox();
        int minY1 = boundingBox1.minY();

        for (int x = boundingBox.minX(); x <= boundingBox.maxX(); x++) {
            for (int z = boundingBox.minZ(); z <= boundingBox.maxZ(); z++) {
                mutableBlockPos.set(x, minY1, z);
                if (!level.isEmptyBlock(mutableBlockPos) && boundingBox1.isInside(mutableBlockPos) && pieces.isInsidePiece(mutableBlockPos)) {
                    for (int i = minY1 - 1; i > minY; i--) {
                        mutableBlockPos.setY(i);
                        if (!level.isEmptyBlock(mutableBlockPos) && !level.getBlockState(mutableBlockPos).liquid()) {
                            break;
                        }

                        level.setBlock(mutableBlockPos, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    @Override
    public StructureType<?> type() {
        return StructureType.WOODLAND_MANSION;
    }
}
