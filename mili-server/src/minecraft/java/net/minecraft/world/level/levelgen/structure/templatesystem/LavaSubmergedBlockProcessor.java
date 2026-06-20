package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

public class LavaSubmergedBlockProcessor extends StructureProcessor {
    public static final MapCodec<LavaSubmergedBlockProcessor> CODEC = MapCodec.unit(() -> LavaSubmergedBlockProcessor.INSTANCE);
    public static final LavaSubmergedBlockProcessor INSTANCE = new LavaSubmergedBlockProcessor();

    @Override
    public StructureTemplate.@Nullable StructureBlockInfo processBlock(
        LevelReader level,
        BlockPos offset,
        BlockPos pos,
        StructureTemplate.StructureBlockInfo blockInfo,
        StructureTemplate.StructureBlockInfo relativeBlockInfo,
        StructurePlaceSettings settings
    ) {
        BlockPos blockPos = relativeBlockInfo.pos();
        boolean isLava = level.getBlockState(blockPos).is(Blocks.LAVA);
        return isLava && !Block.isShapeFullBlock(relativeBlockInfo.state().getShape(level, blockPos))
            ? new StructureTemplate.StructureBlockInfo(blockPos, Blocks.LAVA.defaultBlockState(), relativeBlockInfo.nbt())
            : relativeBlockInfo;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return StructureProcessorType.LAVA_SUBMERGED_BLOCK;
    }
}
