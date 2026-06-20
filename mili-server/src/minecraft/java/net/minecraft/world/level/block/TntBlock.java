package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public class TntBlock extends Block {
    public static final MapCodec<TntBlock> CODEC = simpleCodec(TntBlock::new);
    public static final BooleanProperty UNSTABLE = BlockStateProperties.UNSTABLE;

    @Override
    public MapCodec<TntBlock> codec() {
        return CODEC;
    }

    public TntBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(UNSTABLE, false));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock())) {
            if (level.hasNeighborSignal(pos) && prime(level, pos, () -> org.bukkit.craftbukkit.event.CraftEventFactory.callTNTPrimeEvent(level, pos, org.bukkit.event.block.TNTPrimeEvent.PrimeCause.REDSTONE, null, null))) { // CraftBukkit - TNTPrimeEvent
                level.removeBlock(pos, false);
            }
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        if (level.hasNeighborSignal(pos) && prime(level, pos, () -> org.bukkit.craftbukkit.event.CraftEventFactory.callTNTPrimeEvent(level, pos, org.bukkit.event.block.TNTPrimeEvent.PrimeCause.REDSTONE, null, null))) { // CraftBukkit - TNTPrimeEvent
            level.removeBlock(pos, false);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.getAbilities().instabuild && state.getValue(UNSTABLE)) {
            prime(level, pos, () -> org.bukkit.craftbukkit.event.CraftEventFactory.callTNTPrimeEvent(level, pos, org.bukkit.event.block.TNTPrimeEvent.PrimeCause.BLOCK_BREAK, player, null)); // CraftBukkit - TNTPrimeEvent
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void wasExploded(ServerLevel level, BlockPos pos, Explosion explosion) {
        if (level.getGameRules().get(GameRules.TNT_EXPLODES)) {
            PrimedTnt primedTnt = new PrimedTnt(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, explosion.getIndirectSourceEntity());
            int fuse = primedTnt.getFuse();
            primedTnt.setFuse((short)(level.random.nextInt(fuse / 4) + fuse / 8));
            level.addFreshEntity(primedTnt);
        }
    }

    public static boolean prime(Level level, BlockPos pos) {
    // Paper start
        return prime(level, pos, null, () -> true);
    }
    public static boolean prime(Level level, BlockPos pos, java.util.function.BooleanSupplier event) {
        return prime(level, pos, null, event);
    // Paper end
    }

    private static boolean prime(Level level, BlockPos pos, @Nullable LivingEntity entity, java.util.function.BooleanSupplier event) { // Paper
        if (level instanceof ServerLevel serverLevel && serverLevel.getGameRules().get(GameRules.TNT_EXPLODES) && event.getAsBoolean()) { // Paper
            PrimedTnt primedTnt = new PrimedTnt(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, entity);
            level.addFreshEntity(primedTnt);
            level.playSound(null, primedTnt.getX(), primedTnt.getY(), primedTnt.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(entity, GameEvent.PRIME_FUSE, pos);
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        if (!stack.is(Items.FLINT_AND_STEEL) && !stack.is(Items.FIRE_CHARGE)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        } else {
            if (prime(level, pos, player, () -> org.bukkit.craftbukkit.event.CraftEventFactory.callTNTPrimeEvent(level, pos, org.bukkit.event.block.TNTPrimeEvent.PrimeCause.PLAYER, player, null))) { // Paper
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL_IMMEDIATE);
                Item item = stack.getItem();
                if (stack.is(Items.FLINT_AND_STEEL)) {
                    stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
                } else {
                    stack.consume(1, player);
                }

                player.awardStat(Stats.ITEM_USED.get(item));
            } else if (level instanceof ServerLevel serverLevel && !serverLevel.getGameRules().get(GameRules.TNT_EXPLODES)) {
                player.displayClientMessage(Component.translatable("block.minecraft.tnt.disabled"), true);
                return InteractionResult.PASS;
            }

            return InteractionResult.SUCCESS;
        }
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (level instanceof ServerLevel serverLevel) {
            BlockPos blockPos = hit.getBlockPos();
            Entity owner = projectile.getOwner();
            if (projectile.isOnFire()
                && projectile.mayInteract(serverLevel, blockPos)
                && prime(level, blockPos, owner instanceof LivingEntity ? (LivingEntity)owner : null, () -> org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(projectile, blockPos, state.getFluidState().createLegacyBlock()) && org.bukkit.craftbukkit.event.CraftEventFactory.callTNTPrimeEvent(level, blockPos, org.bukkit.event.block.TNTPrimeEvent.PrimeCause.PROJECTILE, projectile, null))) { // Paper
                level.removeBlock(blockPos, false);
            }
        }
    }

    @Override
    public boolean dropFromExplosion(Explosion explosion) {
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(UNSTABLE);
    }
}
