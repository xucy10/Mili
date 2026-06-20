package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class SpawnerBlock extends BaseEntityBlock {
    public static final MapCodec<SpawnerBlock> CODEC = simpleCodec(SpawnerBlock::new);

    @Override
    public MapCodec<SpawnerBlock> codec() {
        return CODEC;
    }

    protected SpawnerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpawnerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return createTickerHelper(
            blockEntityType, BlockEntityType.MOB_SPAWNER, level.isClientSide() ? SpawnerBlockEntity::clientTick : SpawnerBlockEntity::serverTick
        );
    }

    @Override
    protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos, ItemStack stack, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, stack, dropExperience);
        // CraftBukkit start - Delegate to getExpDrop
    }

    @Override
    public int getExpDrop(BlockState state, ServerLevel level, BlockPos pos, ItemStack stack, boolean dropExperience) {
        if (dropExperience) {
            int i = 15 + level.random.nextInt(15) + level.random.nextInt(15);
            // this.popExperience(level, pos, i);
            return  i;
        }
        return 0;
        // CraftBukkit end
     }
}
