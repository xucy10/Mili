package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

public class CaveVinesBlock extends GrowingPlantHeadBlock implements CaveVines {
    public static final MapCodec<CaveVinesBlock> CODEC = simpleCodec(CaveVinesBlock::new);
    private static final float CHANCE_OF_BERRIES_ON_GROWTH = 0.11F;

    @Override
    public MapCodec<CaveVinesBlock> codec() {
        return CODEC;
    }

    public CaveVinesBlock(BlockBehaviour.Properties properties) {
        super(properties, Direction.DOWN, SHAPE, false, 0.1);
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0).setValue(BERRIES, false));
    }

    @Override
    protected int getBlocksToGrowWhenBonemealed(RandomSource random) {
        return 1;
    }

    @Override
    protected boolean canGrowInto(BlockState state) {
        return state.isAir();
    }

    @Override
    protected Block getBodyBlock() {
        return Blocks.CAVE_VINES_PLANT;
    }

    @Override
    protected BlockState updateBodyAfterConvertedFromHead(BlockState head, BlockState body) {
        return body.setValue(BERRIES, head.getValue(BERRIES));
    }

    @Override
    protected BlockState getGrowIntoState(BlockState state, RandomSource random) {
    // Paper start - Fix Spigot growth modifiers
        return this.getGrowIntoState(state, random, null);
    }
    @Override
    protected BlockState getGrowIntoState(BlockState state, RandomSource random, @javax.annotation.Nullable Level level) {
        final boolean value = random.nextFloat() < (level != null ? (0.11F * (level.spigotConfig.glowBerryModifier / 100.0F)) : 0.11F);
        return super.getGrowIntoState(state, random).setValue(CaveVinesBlock.BERRIES, value);
    }
    // Paper end - Fix Spigot growth modifiers

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(Items.GLOW_BERRIES);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return CaveVines.use(player, state, level, pos);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BERRIES);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return !state.getValue(BERRIES);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(BERRIES, true), Block.UPDATE_CLIENTS);
    }
}
