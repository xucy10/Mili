package net.minecraft.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BaseCoralWallFanBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jspecify.annotations.Nullable;

public class BoneMealItem extends Item {
    public static final int GRASS_SPREAD_WIDTH = 3;
    public static final int GRASS_SPREAD_HEIGHT = 1;
    public static final int GRASS_COUNT_MULTIPLIER = 3;

    public BoneMealItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // CraftBukkit start - extract bonemeal application logic to separate, static method
        return BoneMealItem.applyBonemeal(context);
    }
    public static InteractionResult applyBonemeal(UseOnContext context) {
        // CraftBukkit end
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockPos blockPos = clickedPos.relative(context.getClickedFace());
        ItemStack itemInHand = context.getItemInHand();
        if (growCrop(itemInHand, level, clickedPos)) {
            if (!level.isClientSide()) {
                if (context.getPlayer() != null) itemInHand.causeUseVibration(context.getPlayer(), GameEvent.ITEM_INTERACT_FINISH); // CraftBukkit - SPIGOT-7518
                level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, clickedPos, 15);
            }

            return InteractionResult.SUCCESS;
        } else {
            BlockState blockState = level.getBlockState(clickedPos);
            boolean isFaceSturdy = blockState.isFaceSturdy(level, clickedPos, context.getClickedFace());
            if (isFaceSturdy && growWaterPlant(itemInHand, level, blockPos, context.getClickedFace())) {
                if (!level.isClientSide()) {
                    if (context.getPlayer() != null) itemInHand.causeUseVibration(context.getPlayer(), GameEvent.ITEM_INTERACT_FINISH); // CraftBukkit - SPIGOT-7518
                    level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, blockPos, 15);
                }

                return InteractionResult.SUCCESS;
            } else {
                return InteractionResult.PASS;
            }
        }
    }

    public static boolean growCrop(ItemStack stack, Level level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos);
        if (blockState.getBlock() instanceof BonemealableBlock bonemealableBlock && bonemealableBlock.isValidBonemealTarget(level, pos, blockState)) {
            if (level instanceof ServerLevel) {
                if (bonemealableBlock.isBonemealSuccess(level, level.random, pos, blockState)) {
                    bonemealableBlock.performBonemeal((ServerLevel)level, level.random, pos, blockState);
                }

                stack.shrink(1);
            }

            return true;
        } else {
            return false;
        }
    }

    public static boolean growWaterPlant(ItemStack stack, Level level, BlockPos pos, @Nullable Direction clickedSide) {
        if (level.getBlockState(pos).is(Blocks.WATER) && level.getFluidState(pos).getAmount() == 8) {
            if (!(level instanceof ServerLevel)) {
                return true;
            } else {
                RandomSource random = level.getRandom();

                label80:
                for (int i = 0; i < 128; i++) {
                    BlockPos blockPos = pos;
                    BlockState blockState = Blocks.SEAGRASS.defaultBlockState();

                    for (int i1 = 0; i1 < i / 16; i1++) {
                        blockPos = blockPos.offset(random.nextInt(3) - 1, (random.nextInt(3) - 1) * random.nextInt(3) / 2, random.nextInt(3) - 1);
                        if (level.getBlockState(blockPos).isCollisionShapeFullBlock(level, blockPos)) {
                            continue label80;
                        }
                    }

                    Holder<Biome> biome = level.getBiome(blockPos);
                    if (biome.is(BiomeTags.PRODUCES_CORALS_FROM_BONEMEAL)) {
                        if (i == 0 && clickedSide != null && clickedSide.getAxis().isHorizontal()) {
                            blockState = BuiltInRegistries.BLOCK
                                .getRandomElementOf(BlockTags.WALL_CORALS, level.random)
                                .map(holder -> holder.value().defaultBlockState())
                                .orElse(blockState);
                            if (blockState.hasProperty(BaseCoralWallFanBlock.FACING)) {
                                blockState = blockState.setValue(BaseCoralWallFanBlock.FACING, clickedSide);
                            }
                        } else if (random.nextInt(4) == 0) {
                            blockState = BuiltInRegistries.BLOCK
                                .getRandomElementOf(BlockTags.UNDERWATER_BONEMEALS, level.random)
                                .map(holder -> holder.value().defaultBlockState())
                                .orElse(blockState);
                        }
                    }

                    if (blockState.is(BlockTags.WALL_CORALS, blockStateBase -> blockStateBase.hasProperty(BaseCoralWallFanBlock.FACING))) {
                        for (int i2 = 0; !blockState.canSurvive(level, blockPos) && i2 < 4; i2++) {
                            blockState = blockState.setValue(BaseCoralWallFanBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random));
                        }
                    }

                    if (blockState.canSurvive(level, blockPos)) {
                        BlockState blockState1 = level.getBlockState(blockPos);
                        if (blockState1.is(Blocks.WATER) && level.getFluidState(blockPos).getAmount() == 8) {
                            level.setBlock(blockPos, blockState, Block.UPDATE_ALL);
                        } else if (blockState1.is(Blocks.SEAGRASS)
                            && ((BonemealableBlock)Blocks.SEAGRASS).isValidBonemealTarget(level, blockPos, blockState1)
                            && random.nextInt(10) == 0) {
                            ((BonemealableBlock)Blocks.SEAGRASS).performBonemeal((ServerLevel)level, random, blockPos, blockState1);
                        }
                    }
                }

                stack.shrink(1);
                return true;
            }
        } else {
            return false;
        }
    }

    public static void addGrowthParticles(LevelAccessor level, BlockPos pos, int data) {
        BlockState blockState = level.getBlockState(pos);
        if (blockState.getBlock() instanceof BonemealableBlock bonemealableBlock) {
            BlockPos particlePos = bonemealableBlock.getParticlePos(pos);
            switch (bonemealableBlock.getType()) {
                case NEIGHBOR_SPREADER:
                    ParticleUtils.spawnParticles(level, particlePos, data * 3, 3.0, 1.0, false, ParticleTypes.HAPPY_VILLAGER);
                    break;
                case GROWER:
                    ParticleUtils.spawnParticleInBlock(level, particlePos, data, ParticleTypes.HAPPY_VILLAGER);
            }
        } else if (blockState.is(Blocks.WATER)) {
            ParticleUtils.spawnParticles(level, pos, data * 3, 3.0, 1.0, false, ParticleTypes.HAPPY_VILLAGER);
        }
    }
}
