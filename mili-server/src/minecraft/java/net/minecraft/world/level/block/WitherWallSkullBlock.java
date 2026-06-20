package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class WitherWallSkullBlock extends WallSkullBlock {
    public static final MapCodec<WitherWallSkullBlock> CODEC = simpleCodec(WitherWallSkullBlock::new);

    @Override
    public MapCodec<WitherWallSkullBlock> codec() {
        return CODEC;
    }

    protected WitherWallSkullBlock(BlockBehaviour.Properties properties) {
        super(SkullBlock.Types.WITHER_SKELETON, properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        WitherSkullBlock.checkSpawn(level, pos);
    }
}
