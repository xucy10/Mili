package net.minecraft.world.level.levelgen.structure.structures;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.util.SortedArraySet;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.SinglePieceStructure;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

public class DesertPyramidStructure extends SinglePieceStructure {
    public static final MapCodec<DesertPyramidStructure> CODEC = simpleCodec(DesertPyramidStructure::new);

    public DesertPyramidStructure(Structure.StructureSettings settings) {
        super(DesertPyramidPiece::new, 21, 21, settings);
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
        Set<BlockPos> set = SortedArraySet.create(Vec3i::compareTo);

        for (StructurePiece structurePiece : pieces.pieces()) {
            if (structurePiece instanceof DesertPyramidPiece desertPyramidPiece) {
                set.addAll(desertPyramidPiece.getPotentialSuspiciousSandWorldPositions());
                placeSuspiciousSand(boundingBox, level, desertPyramidPiece.getRandomCollapsedRoofPos());
            }
        }

        ObjectArrayList<BlockPos> list = new ObjectArrayList<>(set.stream().toList());
        RandomSource randomSource = RandomSource.create(level.getSeed()).forkPositional().at(pieces.calculateBoundingBox().getCenter());
        Util.shuffle(list, randomSource);
        int min = Math.min(set.size(), randomSource.nextInt(5, 8));

        for (BlockPos blockPos : list) {
            if (min > 0) {
                min--;
                placeSuspiciousSand(boundingBox, level, blockPos);
            } else if (boundingBox.isInside(blockPos)) {
                level.setBlock(blockPos, Blocks.SAND.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    private static void placeSuspiciousSand(BoundingBox boundingBox, WorldGenLevel worldGenLevel, BlockPos pos) {
        if (boundingBox.isInside(pos)) {
            // CraftBukkit start
            if (worldGenLevel instanceof org.bukkit.craftbukkit.util.TransformerGeneratorAccess transformerAccess && transformerAccess.canTransformBlocks()) {
                // todo never called cause it's called in afterPlace after the whole capture logic
                org.bukkit.craftbukkit.block.CraftBrushableBlock brushableState = (org.bukkit.craftbukkit.block.CraftBrushableBlock) org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(worldGenLevel, pos, Blocks.SUSPICIOUS_SAND.defaultBlockState(), null);
                brushableState.setLootTable(org.bukkit.craftbukkit.CraftLootTable.minecraftToBukkit(BuiltInLootTables.DESERT_PYRAMID_ARCHAEOLOGY));
                brushableState.setSeed(pos.asLong());
                transformerAccess.setCraftBlock(pos, brushableState, Block.UPDATE_CLIENTS);
                return;
            }
            // CraftBukkit end
            worldGenLevel.setBlock(pos, Blocks.SUSPICIOUS_SAND.defaultBlockState(), Block.UPDATE_CLIENTS);
            worldGenLevel.getBlockEntity(pos, BlockEntityType.BRUSHABLE_BLOCK)
                .ifPresent(brushableBlockEntity -> brushableBlockEntity.setLootTable(BuiltInLootTables.DESERT_PYRAMID_ARCHAEOLOGY, pos.asLong()));
        }
    }

    @Override
    public StructureType<?> type() {
        return StructureType.DESERT_PYRAMID;
    }
}
