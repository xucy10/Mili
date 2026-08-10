package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class BeehiveBlock extends BaseEntityBlock {
    public static final MapCodec<BeehiveBlock> CODEC = simpleCodec(BeehiveBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty HONEY_LEVEL = BlockStateProperties.LEVEL_HONEY;
    public static final int MAX_HONEY_LEVELS = 5;

    @Override
    public MapCodec<BeehiveBlock> codec() {
        return CODEC;
    }

    public BeehiveBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(HONEY_LEVEL, 0).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return state.getValue(HONEY_LEVEL);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack stack, boolean includeDrops, boolean dropExp) { // Paper - fix drops not preventing stats/food exhaustion
        super.playerDestroy(level, player, pos, state, blockEntity, stack, includeDrops, dropExp); // Paper - fix drops not preventing stats/food exhaustion
        if (!level.isClientSide() && blockEntity instanceof BeehiveBlockEntity beehiveBlockEntity) {
            if (!EnchantmentHelper.hasTag(stack, EnchantmentTags.PREVENTS_BEE_SPAWNS_WHEN_MINING)) {
                beehiveBlockEntity.emptyAllLivingFromHive(player, state, BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
                Containers.updateNeighboursAfterDestroy(state, level, pos);
                this.angerNearbyBees(level, pos);
            }

            // CriteriaTriggers.BEE_NEST_DESTROYED.trigger((ServerPlayer)player, state, stack, beehiveBlockEntity.getOccupantCount()); // Paper - Trigger bee_nest_destroyed trigger in the correct place; moved until after items are dropped
        }
    }

    @Override
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> dropConsumer) {
        super.onExplosionHit(state, level, pos, explosion, dropConsumer);
        this.angerNearbyBees(level, pos);
    }

    private void angerNearbyBees(Level level, BlockPos pos) {
        AABB aabb = new AABB(pos).inflate(8.0, 6.0, 8.0);
        List<Bee> entitiesOfClass = level.getEntitiesOfClass(Bee.class, aabb);
        if (!entitiesOfClass.isEmpty()) {
            List<Player> entitiesOfClass1 = level.getEntitiesOfClass(Player.class, aabb);
            if (entitiesOfClass1.isEmpty()) {
                return;
            }

            for (Bee bee : entitiesOfClass) {
                if (bee.getTarget() == null) {
                    Player player = Util.getRandom(entitiesOfClass1, level.random);
                    bee.setTarget(player, org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_PLAYER); // CraftBukkit
                }
            }
        }
    }

    public static void dropHoneycomb(
        ServerLevel level, ItemStack stack, BlockState state, @Nullable BlockEntity blockEntity, @Nullable Entity entity, BlockPos pos
    ) {
        dropFromBlockInteractLootTable(
            level, BuiltInLootTables.HARVEST_BEEHIVE, state, blockEntity, stack, entity, (serverLevel, itemStack) -> popResource(serverLevel, pos, itemStack) // Paper - diff on change - copied below
        );
    }

    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        int honeyLevelValue = state.getValue(HONEY_LEVEL);
        boolean flag = false;
        if (honeyLevelValue >= 5) {
            Item item = stack.getItem();
            if (level instanceof ServerLevel serverLevel && stack.is(Items.SHEARS)) {
                // Paper start - Add PlayerShearBlockEvent
                List<org.bukkit.inventory.ItemStack> drops = new java.util.ArrayList<>();
                dropFromBlockInteractLootTable(
                    serverLevel, BuiltInLootTables.HARVEST_BEEHIVE, state, level.getBlockEntity(pos), stack, player, (serverLevel1, itemStack) -> drops.add(org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemStack))
                );
                io.papermc.paper.event.block.PlayerShearBlockEvent event = new io.papermc.paper.event.block.PlayerShearBlockEvent((org.bukkit.entity.Player) player.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, pos), org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(stack), org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(hand), drops);
                if (!event.callEvent()) {
                    return InteractionResult.PASS;
                }
                for (org.bukkit.inventory.ItemStack itemStack : event.getDrops()) {
                    popResource(serverLevel, pos, org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(itemStack));
                }
                // Paper end - Add PlayerShearBlockEvent
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEEHIVE_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
                stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
                flag = true;
                level.gameEvent(player, GameEvent.SHEAR, pos);
            } else if (stack.is(Items.GLASS_BOTTLE)) {
                stack.shrink(1);
                level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                if (stack.isEmpty()) {
                    player.setItemInHand(hand, new ItemStack(Items.HONEY_BOTTLE));
                } else if (!player.getInventory().add(new ItemStack(Items.HONEY_BOTTLE))) {
                    player.drop(new ItemStack(Items.HONEY_BOTTLE), false);
                }

                flag = true;
                level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
            }

            if (!level.isClientSide() && flag) {
                player.awardStat(Stats.ITEM_USED.get(item));
            }
        }

        if (flag) {
            if (!CampfireBlock.isSmokeyPos(level, pos)) {
                if (this.hiveContainsBees(level, pos)) {
                    this.angerNearbyBees(level, pos);
                }

                this.releaseBeesAndResetHoneyLevel(level, state, pos, player, BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
            } else {
                this.resetHoneyLevel(level, state, pos);
            }

            return InteractionResult.SUCCESS;
        } else {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }
    }

    private boolean hiveContainsBees(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BeehiveBlockEntity beehiveBlockEntity && !beehiveBlockEntity.isEmpty();
    }

    public void releaseBeesAndResetHoneyLevel(
        Level level, BlockState state, BlockPos pos, @Nullable Player player, BeehiveBlockEntity.BeeReleaseStatus beeReleaseStatus
    ) {
        this.resetHoneyLevel(level, state, pos);
        if (level.getBlockEntity(pos) instanceof BeehiveBlockEntity beehiveBlockEntity) {
            beehiveBlockEntity.emptyAllLivingFromHive(player, state, beeReleaseStatus);
        }
    }

    public void resetHoneyLevel(Level level, BlockState state, BlockPos pos) {
        level.setBlock(pos, state.setValue(HONEY_LEVEL, 0), Block.UPDATE_ALL);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(HONEY_LEVEL) >= 5) {
            for (int i = 0; i < random.nextInt(1) + 1; i++) {
                this.trySpawnDripParticles(level, pos, state);
            }
        }
    }

    private void trySpawnDripParticles(Level level, BlockPos pos, BlockState state) {
        if (state.getFluidState().isEmpty() && !(level.random.nextFloat() < 0.3F)) {
            VoxelShape collisionShape = state.getCollisionShape(level, pos);
            double d = collisionShape.max(Direction.Axis.Y);
            if (d >= 1.0 && !state.is(BlockTags.IMPERMEABLE)) {
                double d1 = collisionShape.min(Direction.Axis.Y);
                if (d1 > 0.0) {
                    this.spawnParticle(level, pos, collisionShape, pos.getY() + d1 - 0.05);
                } else {
                    BlockPos blockPos = pos.below();
                    BlockState blockState = level.getBlockState(blockPos);
                    VoxelShape collisionShape1 = blockState.getCollisionShape(level, blockPos);
                    double d2 = collisionShape1.max(Direction.Axis.Y);
                    if ((d2 < 1.0 || !blockState.isCollisionShapeFullBlock(level, blockPos)) && blockState.getFluidState().isEmpty()) {
                        this.spawnParticle(level, pos, collisionShape, pos.getY() - 0.05);
                    }
                }
            }
        }
    }

    private void spawnParticle(Level level, BlockPos pos, VoxelShape shape, double y) {
        this.spawnFluidParticle(
            level,
            pos.getX() + shape.min(Direction.Axis.X),
            pos.getX() + shape.max(Direction.Axis.X),
            pos.getZ() + shape.min(Direction.Axis.Z),
            pos.getZ() + shape.max(Direction.Axis.Z),
            y
        );
    }

    private void spawnFluidParticle(Level particleData, double x1, double x2, double z1, double z2, double y) {
        particleData.addParticle(
            ParticleTypes.DRIPPING_HONEY,
            Mth.lerp(particleData.random.nextDouble(), x1, x2),
            y,
            Mth.lerp(particleData.random.nextDouble(), z1, z2),
            0.0,
            0.0,
            0.0
        );
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HONEY_LEVEL, FACING);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BeehiveBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide() ? null : createTickerHelper(blockEntityType, BlockEntityType.BEEHIVE, BeehiveBlockEntity::serverTick);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel serverLevel
            && player.preventsBlockDrops()
            && serverLevel.getGameRules().get(GameRules.BLOCK_DROPS)
            && level.getBlockEntity(pos) instanceof BeehiveBlockEntity beehiveBlockEntity) {
            int honeyLevelValue = state.getValue(HONEY_LEVEL);
            boolean flag = !beehiveBlockEntity.isEmpty();
            if (flag || honeyLevelValue > 0) {
                ItemStack itemStack = new ItemStack(this);
                itemStack.applyComponents(beehiveBlockEntity.collectComponents());
                itemStack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(HONEY_LEVEL, honeyLevelValue));
                ItemEntity itemEntity = new ItemEntity(level, pos.getX(), pos.getY(), pos.getZ(), itemStack);
                itemEntity.setDefaultPickUpDelay();
                level.addFreshEntity(itemEntity);
            }
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        Entity entity = params.getOptionalParameter(LootContextParams.THIS_ENTITY);
        if (entity instanceof PrimedTnt
            || entity instanceof Creeper
            || entity instanceof WitherSkull
            || entity instanceof WitherBoss
            || entity instanceof MinecartTNT) {
            BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
            if (blockEntity instanceof BeehiveBlockEntity beehiveBlockEntity) {
                beehiveBlockEntity.emptyAllLivingFromHive(null, state, BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
            }
        }

        return super.getDrops(state, params);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        ItemStack itemStack = super.getCloneItemStack(level, pos, state, includeData);
        if (includeData) {
            itemStack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(HONEY_LEVEL, state.getValue(HONEY_LEVEL)));
        }

        return itemStack;
    }

    @Override
    protected BlockState updateShape(
        BlockState state,
        LevelReader level,
        ScheduledTickAccess scheduledTickAccess,
        BlockPos pos,
        Direction direction,
        BlockPos neighborPos,
        BlockState neighborState,
        RandomSource random
    ) {
        if (level.getBlockState(neighborPos).getBlock() instanceof FireBlock && level.getBlockEntity(pos) instanceof BeehiveBlockEntity beehiveBlockEntity) {
            beehiveBlockEntity.emptyAllLivingFromHive(null, state, BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
        }

        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
