package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class VoidStartPlatformFeature extends Feature<NoneFeatureConfiguration> {
    private static final BlockPos PLATFORM_OFFSET = new BlockPos(8, 3, 8);
    private static final ChunkPos PLATFORM_ORIGIN_CHUNK = new ChunkPos(PLATFORM_OFFSET);
    private static final int PLATFORM_RADIUS = 16;
    private static final int PLATFORM_RADIUS_CHUNKS = 1;

    public VoidStartPlatformFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    private static int checkerboardDistance(int firstX, int firstZ, int secondX, int secondZ) {
        return Math.max(Math.abs(firstX - secondX), Math.abs(firstZ - secondZ));
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel worldGenLevel = context.level();
        ChunkPos chunkPos = new ChunkPos(context.origin());
        if (checkerboardDistance(chunkPos.x, chunkPos.z, PLATFORM_ORIGIN_CHUNK.x, PLATFORM_ORIGIN_CHUNK.z) > 1) {
            return true;
        } else {
            BlockPos blockPos = PLATFORM_OFFSET.atY(context.origin().getY() + PLATFORM_OFFSET.getY());
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

            for (int blockZ = chunkPos.getMinBlockZ(); blockZ <= chunkPos.getMaxBlockZ(); blockZ++) {
                for (int blockX = chunkPos.getMinBlockX(); blockX <= chunkPos.getMaxBlockX(); blockX++) {
                    if (checkerboardDistance(blockPos.getX(), blockPos.getZ(), blockX, blockZ) <= 16) {
                        mutableBlockPos.set(blockX, blockPos.getY(), blockZ);
                        if (mutableBlockPos.equals(blockPos)) {
                            worldGenLevel.setBlock(mutableBlockPos, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                        } else {
                            worldGenLevel.setBlock(mutableBlockPos, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }

            return true;
        }
    }
}
