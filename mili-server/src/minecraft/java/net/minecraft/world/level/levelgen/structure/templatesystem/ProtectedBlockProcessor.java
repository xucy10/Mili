package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.Feature;
import org.jspecify.annotations.Nullable;

public class ProtectedBlockProcessor extends StructureProcessor {
    public final TagKey<Block> cannotReplace;
    public static final MapCodec<ProtectedBlockProcessor> CODEC = TagKey.hashedCodec(Registries.BLOCK)
        .xmap(ProtectedBlockProcessor::new, protectedBlockProcessor -> protectedBlockProcessor.cannotReplace)
        .fieldOf("value");

    public ProtectedBlockProcessor(TagKey<Block> cannotReplace) {
        this.cannotReplace = cannotReplace;
    }

    @Override
    public StructureTemplate.@Nullable StructureBlockInfo processBlock(
        LevelReader level,
        BlockPos offset,
        BlockPos pos,
        StructureTemplate.StructureBlockInfo blockInfo,
        StructureTemplate.StructureBlockInfo relativeBlockInfo,
        StructurePlaceSettings settings
    ) {
        return Feature.isReplaceable(this.cannotReplace).test(level.getBlockState(relativeBlockInfo.pos())) ? relativeBlockInfo : null;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return StructureProcessorType.PROTECTED_BLOCKS;
    }
}
