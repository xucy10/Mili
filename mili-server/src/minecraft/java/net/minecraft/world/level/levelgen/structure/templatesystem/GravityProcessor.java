package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

public class GravityProcessor extends StructureProcessor {
    public static final MapCodec<GravityProcessor> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Heightmap.Types.CODEC.fieldOf("heightmap").orElse(Heightmap.Types.WORLD_SURFACE_WG).forGetter(gravityProcessor -> gravityProcessor.heightmap),
                Codec.INT.fieldOf("offset").orElse(0).forGetter(gravityProcessor -> gravityProcessor.offset)
            )
            .apply(instance, GravityProcessor::new)
    );
    private final Heightmap.Types heightmap;
    private final int offset;

    public GravityProcessor(Heightmap.Types heightmap, int offset) {
        this.heightmap = heightmap;
        this.offset = offset;
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
        Heightmap.Types types;
        if (level instanceof ServerLevel) {
            if (this.heightmap == Heightmap.Types.WORLD_SURFACE_WG) {
                types = Heightmap.Types.WORLD_SURFACE;
            } else if (this.heightmap == Heightmap.Types.OCEAN_FLOOR_WG) {
                types = Heightmap.Types.OCEAN_FLOOR;
            } else {
                types = this.heightmap;
            }
        } else {
            types = this.heightmap;
        }

        BlockPos blockPos = relativeBlockInfo.pos();
        int i = level.getHeight(types, blockPos.getX(), blockPos.getZ()) + this.offset;
        int y = blockInfo.pos().getY();
        return new StructureTemplate.StructureBlockInfo(
            new BlockPos(blockPos.getX(), i + y, blockPos.getZ()), relativeBlockInfo.state(), relativeBlockInfo.nbt()
        );
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return StructureProcessorType.GRAVITY;
    }
}
