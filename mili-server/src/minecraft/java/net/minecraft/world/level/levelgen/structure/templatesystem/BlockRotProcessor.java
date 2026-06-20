package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

public class BlockRotProcessor extends StructureProcessor {
    public static final MapCodec<BlockRotProcessor> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                RegistryCodecs.homogeneousList(Registries.BLOCK)
                    .optionalFieldOf("rottable_blocks")
                    .forGetter(blockRotProcessor -> blockRotProcessor.rottableBlocks),
                Codec.floatRange(0.0F, 1.0F).fieldOf("integrity").forGetter(blockRotProcessor -> blockRotProcessor.integrity)
            )
            .apply(instance, BlockRotProcessor::new)
    );
    private final Optional<HolderSet<Block>> rottableBlocks;
    private final float integrity;

    public BlockRotProcessor(HolderSet<Block> rottableBlocks, float integrity) {
        this(Optional.of(rottableBlocks), integrity);
    }

    public BlockRotProcessor(float integrity) {
        this(Optional.empty(), integrity);
    }

    private BlockRotProcessor(Optional<HolderSet<Block>> rottableBlocks, float integrity) {
        this.integrity = integrity;
        this.rottableBlocks = rottableBlocks;
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
        RandomSource random = settings.getRandom(relativeBlockInfo.pos());
        return (!this.rottableBlocks.isPresent() || blockInfo.state().is(this.rottableBlocks.get())) && !(random.nextFloat() <= this.integrity)
            ? null
            : relativeBlockInfo;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return StructureProcessorType.BLOCK_ROT;
    }
}
