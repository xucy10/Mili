package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

public class BlackstoneReplaceProcessor extends StructureProcessor {
    public static final MapCodec<BlackstoneReplaceProcessor> CODEC = MapCodec.unit(() -> BlackstoneReplaceProcessor.INSTANCE);
    public static final BlackstoneReplaceProcessor INSTANCE = new BlackstoneReplaceProcessor();
    private final Map<Block, Block> replacements = Util.make(Maps.newHashMap(), map -> {
        map.put(Blocks.COBBLESTONE, Blocks.BLACKSTONE);
        map.put(Blocks.MOSSY_COBBLESTONE, Blocks.BLACKSTONE);
        map.put(Blocks.STONE, Blocks.POLISHED_BLACKSTONE);
        map.put(Blocks.STONE_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICKS);
        map.put(Blocks.MOSSY_STONE_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICKS);
        map.put(Blocks.COBBLESTONE_STAIRS, Blocks.BLACKSTONE_STAIRS);
        map.put(Blocks.MOSSY_COBBLESTONE_STAIRS, Blocks.BLACKSTONE_STAIRS);
        map.put(Blocks.STONE_STAIRS, Blocks.POLISHED_BLACKSTONE_STAIRS);
        map.put(Blocks.STONE_BRICK_STAIRS, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS);
        map.put(Blocks.MOSSY_STONE_BRICK_STAIRS, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS);
        map.put(Blocks.COBBLESTONE_SLAB, Blocks.BLACKSTONE_SLAB);
        map.put(Blocks.MOSSY_COBBLESTONE_SLAB, Blocks.BLACKSTONE_SLAB);
        map.put(Blocks.SMOOTH_STONE_SLAB, Blocks.POLISHED_BLACKSTONE_SLAB);
        map.put(Blocks.STONE_SLAB, Blocks.POLISHED_BLACKSTONE_SLAB);
        map.put(Blocks.STONE_BRICK_SLAB, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB);
        map.put(Blocks.MOSSY_STONE_BRICK_SLAB, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB);
        map.put(Blocks.STONE_BRICK_WALL, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
        map.put(Blocks.MOSSY_STONE_BRICK_WALL, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
        map.put(Blocks.COBBLESTONE_WALL, Blocks.BLACKSTONE_WALL);
        map.put(Blocks.MOSSY_COBBLESTONE_WALL, Blocks.BLACKSTONE_WALL);
        map.put(Blocks.CHISELED_STONE_BRICKS, Blocks.CHISELED_POLISHED_BLACKSTONE);
        map.put(Blocks.CRACKED_STONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS);
        map.put(Blocks.IRON_BARS, Blocks.IRON_CHAIN);
    });

    private BlackstoneReplaceProcessor() {
    }

    @Override
    public StructureTemplate.StructureBlockInfo processBlock(
        LevelReader level,
        BlockPos offset,
        BlockPos pos,
        StructureTemplate.StructureBlockInfo blockInfo,
        StructureTemplate.StructureBlockInfo relativeBlockInfo,
        StructurePlaceSettings settings
    ) {
        Block block = this.replacements.get(relativeBlockInfo.state().getBlock());
        if (block == null) {
            return relativeBlockInfo;
        } else {
            BlockState blockState = relativeBlockInfo.state();
            BlockState blockState1 = block.defaultBlockState();
            if (blockState.hasProperty(StairBlock.FACING)) {
                blockState1 = blockState1.setValue(StairBlock.FACING, blockState.getValue(StairBlock.FACING));
            }

            if (blockState.hasProperty(StairBlock.HALF)) {
                blockState1 = blockState1.setValue(StairBlock.HALF, blockState.getValue(StairBlock.HALF));
            }

            if (blockState.hasProperty(SlabBlock.TYPE)) {
                blockState1 = blockState1.setValue(SlabBlock.TYPE, blockState.getValue(SlabBlock.TYPE));
            }

            return new StructureTemplate.StructureBlockInfo(relativeBlockInfo.pos(), blockState1, relativeBlockInfo.nbt());
        }
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return StructureProcessorType.BLACKSTONE_REPLACE;
    }
}
