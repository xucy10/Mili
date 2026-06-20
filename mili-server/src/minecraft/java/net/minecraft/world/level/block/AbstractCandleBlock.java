package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public abstract class AbstractCandleBlock extends Block {
    public static final int LIGHT_PER_CANDLE = 3;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    @Override
    protected abstract MapCodec<? extends AbstractCandleBlock> codec();

    protected AbstractCandleBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    protected abstract Iterable<Vec3> getParticleOffsets(BlockState state);

    public static boolean isLit(BlockState state) {
        return state.hasProperty(LIT) && (state.is(BlockTags.CANDLES) || state.is(BlockTags.CANDLE_CAKES)) && state.getValue(LIT);
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (!level.isClientSide() && projectile.isOnFire() && this.canBeLit(state)) {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(level, hit.getBlockPos(), projectile).isCancelled()) {
                return;
            }
            // CraftBukkit end
            setLit(level, state, hit.getBlockPos(), true);
        }
    }

    protected boolean canBeLit(BlockState state) {
        return !state.getValue(LIT);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT)) {
            this.getParticleOffsets(state).forEach(offset -> addParticlesAndSound(level, offset.add(pos.getX(), pos.getY(), pos.getZ()), random));
        }
    }

    private static void addParticlesAndSound(Level level, Vec3 offset, RandomSource random) {
        float randomFloat = random.nextFloat();
        if (randomFloat < 0.3F) {
            level.addParticle(ParticleTypes.SMOKE, offset.x, offset.y, offset.z, 0.0, 0.0, 0.0);
            if (randomFloat < 0.17F) {
                level.playLocalSound(
                    offset.x + 0.5,
                    offset.y + 0.5,
                    offset.z + 0.5,
                    SoundEvents.CANDLE_AMBIENT,
                    SoundSource.BLOCKS,
                    1.0F + random.nextFloat(),
                    random.nextFloat() * 0.7F + 0.3F,
                    false
                );
            }
        }

        level.addParticle(ParticleTypes.SMALL_FLAME, offset.x, offset.y, offset.z, 0.0, 0.0, 0.0);
    }

    public static void extinguish(@Nullable Player player, BlockState state, LevelAccessor level, BlockPos pos) {
        setLit(level, state, pos, false);
        if (state.getBlock() instanceof AbstractCandleBlock) {
            ((AbstractCandleBlock)state.getBlock())
                .getParticleOffsets(state)
                .forEach(
                    offset -> level.addParticle(ParticleTypes.SMOKE, pos.getX() + offset.x(), pos.getY() + offset.y(), pos.getZ() + offset.z(), 0.0, 0.1F, 0.0)
                );
        }

        level.playSound(null, pos, SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
    }

    private static void setLit(LevelAccessor level, BlockState state, BlockPos pos, boolean lit) {
        level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_ALL_IMMEDIATE);
    }

    @Override
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> dropConsumer) {
        if (explosion.canTriggerBlocks() && state.getValue(LIT)) {
            extinguish(null, state, level, pos);
        }

        super.onExplosionHit(state, level, pos, explosion, dropConsumer);
    }
}
