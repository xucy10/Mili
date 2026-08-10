package net.minecraft.world.level.levelgen.carver;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.material.Fluids;
import org.apache.commons.lang3.mutable.MutableBoolean;

public class NetherWorldCarver extends CaveWorldCarver {
    public NetherWorldCarver(Codec<CaveCarverConfiguration> codec) {
        super(codec);
        this.liquids = ImmutableSet.of(Fluids.LAVA, Fluids.WATER);
    }

    @Override
    protected int getCaveBound() {
        return 10;
    }

    @Override
    protected float getThickness(RandomSource random) {
        return (random.nextFloat() * 2.0F + random.nextFloat()) * 2.0F;
    }

    @Override
    protected double getYScale() {
        return 5.0;
    }

    @Override
    protected boolean carveBlock(
        CarvingContext context,
        CaveCarverConfiguration config,
        ChunkAccess chunk,
        Function<BlockPos, Holder<Biome>> biomeGetter,
        CarvingMask carvingMask,
        BlockPos.MutableBlockPos pos,
        BlockPos.MutableBlockPos checkPos,
        Aquifer aquifer,
        MutableBoolean reachedSurface
    ) {
        if (this.canReplaceBlock(config, chunk.getBlockState(pos))) {
            BlockState blockState;
            if (pos.getY() <= context.getMinGenY() + 31) {
                blockState = LAVA.createLegacyBlock();
            } else {
                blockState = CAVE_AIR;
            }

            chunk.setBlockState(pos, blockState);
            return true;
        } else {
            return false;
        }
    }
}
