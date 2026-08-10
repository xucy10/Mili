package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CropBlock extends VegetationBlock implements BonemealableBlock {
    public static final MapCodec<CropBlock> CODEC = simpleCodec(CropBlock::new);
    public static final int MAX_AGE = 7;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_7;
    private static final VoxelShape[] SHAPES = Block.boxes(7, i -> Block.column(16.0, 0.0, 2 + i * 2));

    @Override
    public MapCodec<? extends CropBlock> codec() {
        return CODEC;
    }

    protected CropBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(this.getAgeProperty(), 0));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[this.getAge(state)];
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(Blocks.FARMLAND);
    }

    protected IntegerProperty getAgeProperty() {
        return AGE;
    }

    public int getMaxAge() {
        return 7;
    }

    public int getAge(BlockState state) {
        return state.getValue(this.getAgeProperty());
    }

    public BlockState getStateForAge(int age) {
        return this.defaultBlockState().setValue(this.getAgeProperty(), age);
    }

    public final boolean isMaxAge(BlockState state) {
        return this.getAge(state) >= this.getMaxAge();
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return !this.isMaxAge(state);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getRawBrightness(pos, 0) >= 9) {
            int age = this.getAge(state);
            if (age < this.getMaxAge()) {
                float growthSpeed = getGrowthSpeed(this, level, pos);
                // Spigot start
                int modifier = 100;
                if (this == Blocks.BEETROOTS) {
                    modifier = level.spigotConfig.beetrootModifier;
                } else if (this == Blocks.CARROTS) {
                    modifier = level.spigotConfig.carrotModifier;
                } else if (this == Blocks.POTATOES) {
                    modifier = level.spigotConfig.potatoModifier;
                // Paper start - Fix Spigot growth modifiers
                } else if (this == Blocks.TORCHFLOWER_CROP) {
                    modifier = level.spigotConfig.torchFlowerModifier;
                // Paper end - Fix Spigot growth modifiers
                } else if (this == Blocks.WHEAT) {
                    modifier = level.spigotConfig.wheatModifier;
                }

                if (random.nextFloat() < (modifier / (100.0F * (Math.floor((25.0F / growthSpeed) + 1))))) { // Spigot - SPIGOT-7159: Better modifier resolution
                    // Spigot end
                    org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockGrowEvent(level, pos, this.getStateForAge(age + 1), Block.UPDATE_CLIENTS); // CraftBukkit
                }
            }
        }
    }

    public void growCrops(Level level, BlockPos pos, BlockState state) {
        int min = Math.min(this.getMaxAge(), this.getAge(state) + this.getBonemealAgeIncrease(level));
        org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockGrowEvent(level, pos, this.getStateForAge(min), Block.UPDATE_CLIENTS); // CraftBukkit
    }

    protected int getBonemealAgeIncrease(Level level) {
        return Mth.nextInt(level.random, 2, 5);
    }

    protected static float getGrowthSpeed(Block block, BlockGetter level, BlockPos pos) {
        float f = 1.0F;
        BlockPos blockPos = pos.below();

        for (int i = -1; i <= 1; i++) {
            for (int i1 = -1; i1 <= 1; i1++) {
                float f1 = 0.0F;
                BlockState blockState = level.getBlockState(blockPos.offset(i, 0, i1));
                if (blockState.is(Blocks.FARMLAND)) {
                    f1 = 1.0F;
                    if (blockState.getValue(FarmBlock.MOISTURE) > 0) {
                        f1 = 3.0F;
                    }
                }

                if (i != 0 || i1 != 0) {
                    f1 /= 4.0F;
                }

                f += f1;
            }
        }

        BlockPos blockPos1 = pos.north();
        BlockPos blockPos2 = pos.south();
        BlockPos blockPos3 = pos.west();
        BlockPos blockPos4 = pos.east();
        boolean flag = level.getBlockState(blockPos3).is(block) || level.getBlockState(blockPos4).is(block);
        boolean flag1 = level.getBlockState(blockPos1).is(block) || level.getBlockState(blockPos2).is(block);
        if (flag && flag1) {
            f /= 2.0F;
        } else {
            boolean flag2 = level.getBlockState(blockPos3.north()).is(block)
                || level.getBlockState(blockPos4.north()).is(block)
                || level.getBlockState(blockPos4.south()).is(block)
                || level.getBlockState(blockPos3.south()).is(block);
            if (flag2) {
                f /= 2.0F;
            }
        }

        return f;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return hasSufficientLight(level, pos) && super.canSurvive(state, level, pos);
    }

    protected static boolean hasSufficientLight(LevelReader level, BlockPos pos) {
        return level.getRawBrightness(pos, 0) >= 8;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean pastEdges) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, pos)).callEvent()) { return; } // Paper - Add EntityInsideBlockEvent
        if (level instanceof ServerLevel serverLevel && entity instanceof Ravager && org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(entity, pos, Blocks.AIR.defaultBlockState(), !serverLevel.getGameRules().get(GameRules.MOB_GRIEFING))) { // CraftBukkit
            serverLevel.destroyBlock(pos, true, entity);
        }

        super.entityInside(state, level, pos, entity, effectApplier, pastEdges);
    }

    protected ItemLike getBaseSeedId() {
        return Items.WHEAT_SEEDS;
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(this.getBaseSeedId());
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return !this.isMaxAge(state);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        this.growCrops(level, pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }
}
