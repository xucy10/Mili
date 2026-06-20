package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.ServerLevelAccessor;

public class CappedProcessor extends StructureProcessor {
    public static final MapCodec<CappedProcessor> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                StructureProcessorType.SINGLE_CODEC.fieldOf("delegate").forGetter(processor -> processor.delegate),
                IntProvider.POSITIVE_CODEC.fieldOf("limit").forGetter(processor -> processor.limit)
            )
            .apply(instance, CappedProcessor::new)
    );
    private final StructureProcessor delegate;
    private final IntProvider limit;

    public CappedProcessor(StructureProcessor delegate, IntProvider limit) {
        this.delegate = delegate;
        this.limit = limit;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return StructureProcessorType.CAPPED;
    }

    @Override
    public final List<StructureTemplate.StructureBlockInfo> finalizeProcessing(
        ServerLevelAccessor level,
        BlockPos offset,
        BlockPos pos,
        List<StructureTemplate.StructureBlockInfo> originalBlockInfos,
        List<StructureTemplate.StructureBlockInfo> processedBlockInfos,
        StructurePlaceSettings settings
    ) {
        if (this.limit.getMaxValue() != 0 && !processedBlockInfos.isEmpty()) {
            if (originalBlockInfos.size() != processedBlockInfos.size()) {
                Util.logAndPauseIfInIde(
                    "Original block info list not in sync with processed list, skipping processing. Original size: "
                        + originalBlockInfos.size()
                        + ", Processed size: "
                        + processedBlockInfos.size()
                );
                return processedBlockInfos;
            } else {
                RandomSource randomSource = RandomSource.create(level.getLevel().getSeed()).forkPositional().at(offset);
                int min = Math.min(this.limit.sample(randomSource), processedBlockInfos.size());
                if (min < 1) {
                    return processedBlockInfos;
                } else {
                    IntArrayList list = Util.toShuffledList(IntStream.range(0, processedBlockInfos.size()), randomSource);
                    IntIterator intIterator = list.intIterator();
                    int i = 0;

                    while (intIterator.hasNext() && i < min) {
                        int i1 = intIterator.nextInt();
                        StructureTemplate.StructureBlockInfo structureBlockInfo = originalBlockInfos.get(i1);
                        StructureTemplate.StructureBlockInfo structureBlockInfo1 = processedBlockInfos.get(i1);
                        StructureTemplate.StructureBlockInfo structureBlockInfo2 = this.delegate
                            .processBlock(level, offset, pos, structureBlockInfo, structureBlockInfo1, settings);
                        if (structureBlockInfo2 != null && !structureBlockInfo1.equals(structureBlockInfo2)) {
                            i++;
                            processedBlockInfos.set(i1, structureBlockInfo2);
                        }
                    }

                    return processedBlockInfos;
                }
            }
        } else {
            return processedBlockInfos;
        }
    }
}
