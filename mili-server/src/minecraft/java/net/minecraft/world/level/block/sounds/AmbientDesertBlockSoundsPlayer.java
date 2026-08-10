package net.minecraft.world.level.block.sounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public class AmbientDesertBlockSoundsPlayer {
    private static final int IDLE_SOUND_CHANCE = 2100;
    private static final int DRY_GRASS_SOUND_CHANCE = 200;
    private static final int DEAD_BUSH_SOUND_CHANCE = 130;
    private static final int DEAD_BUSH_SOUND_BADLANDS_DECREASED_CHANCE = 3;
    private static final int SURROUNDING_BLOCKS_PLAY_SOUND_THRESHOLD = 3;
    private static final int SURROUNDING_BLOCKS_DISTANCE_HORIZONTAL_CHECK = 8;
    private static final int SURROUNDING_BLOCKS_DISTANCE_VERTICAL_CHECK = 5;
    private static final int HORIZONTAL_DIRECTIONS = 4;

    public static void playAmbientSandSounds(Level level, BlockPos pos, RandomSource random) {
        if (level.getBlockState(pos.above()).is(Blocks.AIR)) {
            if (random.nextInt(2100) == 0 && shouldPlayAmbientSandSound(level, pos)) {
                level.playLocalSound(pos.getX(), pos.getY(), pos.getZ(), SoundEvents.SAND_IDLE, SoundSource.AMBIENT, 1.0F, 1.0F, false);
            }
        }
    }

    public static void playAmbientDryGrassSounds(Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(200) == 0 && shouldPlayDesertDryVegetationBlockSounds(level, pos.below())) {
            level.playPlayerSound(SoundEvents.DRY_GRASS, SoundSource.AMBIENT, 1.0F, 1.0F);
        }
    }

    public static void playAmbientDeadBushSounds(Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(130) == 0) {
            BlockState blockState = level.getBlockState(pos.below());
            if ((blockState.is(Blocks.RED_SAND) || blockState.is(BlockTags.TERRACOTTA)) && random.nextInt(3) != 0) {
                return;
            }

            if (shouldPlayDesertDryVegetationBlockSounds(level, pos.below())) {
                level.playLocalSound(pos.getX(), pos.getY(), pos.getZ(), SoundEvents.DEAD_BUSH_IDLE, SoundSource.AMBIENT, 1.0F, 1.0F, false);
            }
        }
    }

    public static boolean shouldPlayDesertDryVegetationBlockSounds(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(BlockTags.TRIGGERS_AMBIENT_DESERT_DRY_VEGETATION_BLOCK_SOUNDS)
            && level.getBlockState(pos.below()).is(BlockTags.TRIGGERS_AMBIENT_DESERT_DRY_VEGETATION_BLOCK_SOUNDS);
    }

    private static boolean shouldPlayAmbientSandSound(Level level, BlockPos pos) {
        int i = 0;
        int i1 = 0;
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            mutableBlockPos.set(pos).move(direction, 8);
            if (columnContainsTriggeringBlock(level, mutableBlockPos) && i++ >= 3) {
                return true;
            }

            i1++;
            int i2 = 4 - i1;
            int i3 = i2 + i;
            boolean flag = i3 >= 3;
            if (!flag) {
                return false;
            }
        }

        return false;
    }

    private static boolean columnContainsTriggeringBlock(Level level, BlockPos.MutableBlockPos pos) {
        int i = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos) - 1;
        if (Math.abs(i - pos.getY()) > 5) {
            pos.move(Direction.UP, 6);
            BlockState blockState = level.getBlockState(pos);
            pos.move(Direction.DOWN);

            for (int i1 = 0; i1 < 10; i1++) {
                BlockState blockState1 = level.getBlockState(pos);
                if (blockState.isAir() && canTriggerAmbientDesertSandSounds(blockState1)) {
                    return true;
                }

                blockState = blockState1;
                pos.move(Direction.DOWN);
            }

            return false;
        } else {
            boolean isAir = level.getBlockState(pos.setY(i + 1)).isAir();
            return isAir && canTriggerAmbientDesertSandSounds(level.getBlockState(pos.setY(i)));
        }
    }

    private static boolean canTriggerAmbientDesertSandSounds(BlockState state) {
        return state.is(BlockTags.TRIGGERS_AMBIENT_DESERT_SAND_BLOCK_SOUNDS);
    }
}
