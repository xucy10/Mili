package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public class TestInstanceBlock extends BaseEntityBlock implements GameMasterBlock {
    public static final MapCodec<TestInstanceBlock> CODEC = simpleCodec(TestInstanceBlock::new);

    public TestInstanceBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TestInstanceBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof TestInstanceBlockEntity testInstanceBlockEntity) {
            if (!player.canUseGameMasterBlocks()) {
                return InteractionResult.PASS;
            } else {
                if (player.level().isClientSide()) {
                    player.openTestInstanceBlock(testInstanceBlockEntity);
                }

                return InteractionResult.SUCCESS;
            }
        } else {
            return InteractionResult.PASS;
        }
    }

    @Override
    protected MapCodec<TestInstanceBlock> codec() {
        return CODEC;
    }
}
