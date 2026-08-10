package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

public class RedStoneOreBlock extends Block {
    public static final MapCodec<RedStoneOreBlock> CODEC = simpleCodec(RedStoneOreBlock::new);
    public static final BooleanProperty LIT = RedstoneTorchBlock.LIT;

    @Override
    public MapCodec<RedStoneOreBlock> codec() {
        return CODEC;
    }

    public RedStoneOreBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(LIT, false));
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        interact(state, level, pos, player); // CraftBukkit - add entityhuman
        super.attack(state, level, pos, player);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!entity.isSteppingCarefully()) {
            // CraftBukkit start
            if (entity instanceof Player player) {
                org.bukkit.event.player.PlayerInteractEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerInteractEvent(player, org.bukkit.event.block.Action.PHYSICAL, pos, null, null, null);
                if (!event.isCancelled()) {
                    RedStoneOreBlock.interact(level.getBlockState(pos), level, pos, entity); // add entity
                }
            } else {
                org.bukkit.event.entity.EntityInteractEvent event = new org.bukkit.event.entity.EntityInteractEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, pos));
                level.getCraftServer().getPluginManager().callEvent(event);
                if (!event.isCancelled()) {
                    RedStoneOreBlock.interact(level.getBlockState(pos), level, pos, entity); // add entity
                }
            }
            // CraftBukkit end
        }

        super.stepOn(level, pos, state, entity);
    }

    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        if (level.isClientSide()) {
            spawnParticles(level, pos);
        } else {
            interact(state, level, pos, player); // CraftBukkit - add entityhuman
        }

        return (InteractionResult)(stack.getItem() instanceof BlockItem && new BlockPlaceContext(player, hand, stack, hitResult).canPlace()
            ? InteractionResult.PASS
            : InteractionResult.SUCCESS);
    }

    private static void interact(BlockState state, Level level, BlockPos pos, Entity entity) { // CraftBukkit - add Entity
        spawnParticles(level, pos);
        if (!state.getValue(LIT)) {
            // CraftBukkit start
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(entity, pos, state.setValue(RedStoneOreBlock.LIT, true))) {
                return;
            }
            // CraftBukkit end
            level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
        }
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(LIT);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT)) {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(level, pos, state.setValue(RedStoneOreBlock.LIT, false)).isCancelled()) {
                return;
            }
            // CraftBukkit end
            level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos, ItemStack stack, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, stack, dropExperience);
        // CraftBukkit start - Delegated to getExpDrop
    }

    @Override
    public int getExpDrop(BlockState state, ServerLevel level, BlockPos pos, ItemStack stack, boolean dropExperience) {
        if (dropExperience) {
            return this.tryDropExperience(level, pos, stack, UniformInt.of(1, 5));
        }

        return 0;
        // CraftBukkit end
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT)) {
            spawnParticles(level, pos);
        }
    }

    private static void spawnParticles(Level level, BlockPos pos) {
        double d = 0.5625;
        RandomSource randomSource = level.random;

        for (Direction direction : Direction.values()) {
            BlockPos blockPos = pos.relative(direction);
            if (!level.getBlockState(blockPos).isSolidRender()) {
                Direction.Axis axis = direction.getAxis();
                double d1 = axis == Direction.Axis.X ? 0.5 + 0.5625 * direction.getStepX() : randomSource.nextFloat();
                double d2 = axis == Direction.Axis.Y ? 0.5 + 0.5625 * direction.getStepY() : randomSource.nextFloat();
                double d3 = axis == Direction.Axis.Z ? 0.5 + 0.5625 * direction.getStepZ() : randomSource.nextFloat();
                level.addParticle(DustParticleOptions.REDSTONE, pos.getX() + d1, pos.getY() + d2, pos.getZ() + d3, 0.0, 0.0, 0.0);
            }
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }
}
