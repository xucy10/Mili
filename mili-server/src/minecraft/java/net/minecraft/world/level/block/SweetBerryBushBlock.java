package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SweetBerryBushBlock extends VegetationBlock implements BonemealableBlock {
    public static final MapCodec<SweetBerryBushBlock> CODEC = simpleCodec(SweetBerryBushBlock::new);
    private static final float HURT_SPEED_THRESHOLD = 0.003F;
    public static final int MAX_AGE = 3;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    private static final VoxelShape SHAPE_SAPLING = Block.column(10.0, 0.0, 8.0);
    private static final VoxelShape SHAPE_GROWING = Block.column(14.0, 0.0, 16.0);

    @Override
    public MapCodec<SweetBerryBushBlock> codec() {
        return CODEC;
    }

    public SweetBerryBushBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(Items.SWEET_BERRIES);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(AGE)) {
            case 0 -> SHAPE_SAPLING;
            case 3 -> Shapes.block();
            default -> SHAPE_GROWING;
        };
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(AGE) < 3;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int ageValue = state.getValue(AGE);
        if (ageValue < 3 && random.nextFloat() < (level.spigotConfig.sweetBerryModifier / (100.0F * 5)) && level.getRawBrightness(pos.above(), 0) >= 9) { // Spigot - SPIGOT-7159: Better modifier resolution
            BlockState blockState = state.setValue(AGE, ageValue + 1);
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockGrowEvent(level, pos, blockState, Block.UPDATE_CLIENTS)) return; // CraftBukkit
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(blockState));
        }
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean pastEdges) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, pos)).callEvent()) { return; } // Paper - Add EntityInsideBlockEvent
        if (entity instanceof LivingEntity && entity.getType() != EntityType.FOX && entity.getType() != EntityType.BEE) {
            entity.makeStuckInBlock(state, new Vec3(0.8F, 0.75, 0.8F));
            if (level instanceof ServerLevel serverLevel && state.getValue(AGE) != 0) {
                Vec3 vec3 = entity.isClientAuthoritative() ? entity.getKnownMovement() : entity.oldPosition().subtract(entity.position());
                if (vec3.horizontalDistanceSqr() > 0.0) {
                    double abs = Math.abs(vec3.x());
                    double abs1 = Math.abs(vec3.z());
                    if (abs >= 0.003F || abs1 >= 0.003F) {
                        entity.hurtServer(serverLevel, level.damageSources().sweetBerryBush().eventBlockDamager(level, pos), 1.0F); // CraftBukkit
                    }
                }
            }
        }
    }

    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        int ageValue = state.getValue(AGE);
        boolean flag = ageValue == 3;
        return (InteractionResult)(!flag && stack.is(Items.BONE_MEAL)
            ? InteractionResult.PASS
            : super.useItemOn(stack, state, level, pos, player, hand, hitResult));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (state.getValue(AGE) > 1) {
            if (level instanceof ServerLevel serverLevel) {
                java.util.List<net.minecraft.world.item.ItemStack> drops = new java.util.ArrayList<>(); // Paper
                Block.dropFromBlockInteractLootTable(
                    serverLevel,
                    BuiltInLootTables.HARVEST_SWEET_BERRY_BUSH,
                    state,
                    level.getBlockEntity(pos),
                    null,
                    player,
                    (serverLevel1, itemStack) -> drops.add(itemStack) // Paper
                );
                // CraftBukkit start - useWithoutItem is always MAIN_HAND
                org.bukkit.event.player.PlayerHarvestBlockEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerHarvestBlockEvent(level, pos, player, InteractionHand.MAIN_HAND, drops);
                if (event.isCancelled()) {
                    return InteractionResult.SUCCESS; // We need to return a success either way, because making it PASS or FAIL will result in a bug where cancelling while harvesting w/ block in hand places block
                }
                for (org.bukkit.inventory.ItemStack itemStack : event.getItemsHarvested()) {
                    popResource(level, pos, org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(itemStack));
                }
                // CraftBukkit end
                serverLevel.playSound(
                    null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.8F + serverLevel.random.nextFloat() * 0.4F
                );
                BlockState blockState = state.setValue(AGE, 1);
                serverLevel.setBlock(pos, blockState, Block.UPDATE_CLIENTS);
                serverLevel.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, blockState));
            }

            return InteractionResult.SUCCESS;
        } else {
            return super.useWithoutItem(state, level, pos, player, hitResult);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return state.getValue(AGE) < 3;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        int min = Math.min(3, state.getValue(AGE) + 1);
        level.setBlock(pos, state.setValue(AGE, min), Block.UPDATE_CLIENTS);
    }
}
