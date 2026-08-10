package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import net.minecraft.SharedConstants;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class JigsawReplacementProcessor extends StructureProcessor {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<JigsawReplacementProcessor> CODEC = MapCodec.unit(() -> JigsawReplacementProcessor.INSTANCE);
    public static final JigsawReplacementProcessor INSTANCE = new JigsawReplacementProcessor();

    private JigsawReplacementProcessor() {
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
        BlockState blockState = relativeBlockInfo.state();
        if (!blockState.is(Blocks.JIGSAW) || SharedConstants.DEBUG_KEEP_JIGSAW_BLOCKS_DURING_STRUCTURE_GEN) {
            return relativeBlockInfo;
        } else if (relativeBlockInfo.nbt() == null) {
            LOGGER.warn("Jigsaw block at {} is missing nbt, will not replace", offset);
            return relativeBlockInfo;
        } else {
            String stringOr = relativeBlockInfo.nbt().getStringOr("final_state", "minecraft:air");

            BlockState blockState1;
            try {
                BlockStateParser.BlockResult blockResult = BlockStateParser.parseForBlock(level.holderLookup(Registries.BLOCK), stringOr, true);
                blockState1 = blockResult.blockState();
            } catch (CommandSyntaxException var11) {
                LOGGER.error("Failed to parse jigsaw replacement state '{}' at {}: {}", stringOr, offset, var11.getMessage());
                return null;
            }

            return blockState1.is(Blocks.STRUCTURE_VOID) ? null : new StructureTemplate.StructureBlockInfo(relativeBlockInfo.pos(), blockState1, null);
        }
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return StructureProcessorType.JIGSAW_REPLACEMENT;
    }
}
