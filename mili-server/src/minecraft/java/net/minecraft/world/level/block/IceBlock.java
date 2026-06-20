package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class IceBlock extends HalfTransparentBlock {
    public static final MapCodec<IceBlock> CODEC = simpleCodec(IceBlock::new);

    @Override
    public MapCodec<? extends IceBlock> codec() {
        return CODEC;
    }

    public IceBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    public static BlockState meltsInto() {
        return Blocks.WATER.defaultBlockState();
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack stack, boolean includeDrops, boolean dropExp) { // Paper - fix drops not preventing stats/food exhaustion
        super.playerDestroy(level, player, pos, state, blockEntity, stack, includeDrops, dropExp); // Paper - fix drops not preventing stats/food exhaustion
        // Paper start - Improve Block#breakNaturally API
        this.afterDestroy(level, pos, stack);
    }
    public void afterDestroy(Level level, BlockPos pos, ItemStack stack) {
        // Paper end - Improve Block#breakNaturally API
        if (!EnchantmentHelper.hasTag(stack, EnchantmentTags.PREVENTS_ICE_MELTING)) {
            if (level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, pos)) {
                level.removeBlock(pos, false);
                return;
            }

            BlockState blockState = level.getBlockState(pos.below());
            if (blockState.blocksMotion() || blockState.liquid()) {
                level.setBlockAndUpdate(pos, meltsInto());
            }
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBrightness(LightLayer.BLOCK, pos) > 11 - state.getLightBlock()) {
            this.melt(state, level, pos);
        }
    }

    protected void melt(BlockState state, Level level, BlockPos pos) {
        // Paper start - call BlockFadeEvent
        final boolean waterEvaporates = level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, pos);
        if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(level, pos, waterEvaporates ? Blocks.AIR.defaultBlockState() : Blocks.WATER.defaultBlockState()).isCancelled()) {
            return;
        }
        if (waterEvaporates) {
            // Paper end - call BlockFadeEvent
            level.removeBlock(pos, false);
        } else {
            level.setBlockAndUpdate(pos, meltsInto());
            level.neighborChanged(pos, meltsInto().getBlock(), null);
        }
    }
}
