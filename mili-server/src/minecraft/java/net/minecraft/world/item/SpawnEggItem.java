package net.minecraft.world.item;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.stats.Stats;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Spawner;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class SpawnEggItem extends Item {
    private static final Map<EntityType<?>, SpawnEggItem> BY_ID = Maps.newIdentityHashMap();

    public SpawnEggItem(Item.Properties properties) {
        super(properties);
        TypedEntityData<EntityType<?>> typedEntityData = this.components().get(DataComponents.ENTITY_DATA);
        if (typedEntityData != null) {
            BY_ID.put(typedEntityData.type(), this);
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        } else {
            ItemStack itemInHand = context.getItemInHand();
            BlockPos clickedPos = context.getClickedPos();
            Direction clickedFace = context.getClickedFace();
            BlockState blockState = level.getBlockState(clickedPos);
            if (level.getBlockEntity(clickedPos) instanceof Spawner spawner) {
                EntityType<?> type = this.getType(itemInHand);
                if (type == null) {
                    return InteractionResult.FAIL;
                } else if (!serverLevel.isSpawnerBlockEnabled()) {
                    if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
                        serverPlayer.sendSystemMessage(Component.translatable("advMode.notEnabled.spawner"));
                    }

                    return InteractionResult.FAIL;
                } else {
                    if (level.paperConfig().entities.spawning.disableMobSpawnerSpawnEggTransformation) return InteractionResult.FAIL; // Paper - Allow disabling mob spawner spawn egg transformation
                    spawner.setEntityId(type, level.getRandom());
                    level.sendBlockUpdated(clickedPos, blockState, blockState, Block.UPDATE_ALL);
                    level.gameEvent(context.getPlayer(), GameEvent.BLOCK_CHANGE, clickedPos);
                    itemInHand.shrink(1);
                    return InteractionResult.SUCCESS;
                }
            } else {
                BlockPos blockPos;
                if (blockState.getCollisionShape(level, clickedPos).isEmpty()) {
                    blockPos = clickedPos;
                } else {
                    blockPos = clickedPos.relative(clickedFace);
                }

                return this.spawnMob(
                    context.getPlayer(), itemInHand, level, blockPos, true, !Objects.equals(clickedPos, blockPos) && clickedFace == Direction.UP
                );
            }
        }
    }

    private InteractionResult spawnMob(
        @Nullable LivingEntity owner, ItemStack stack, Level level, BlockPos pos, boolean shouldOffsetY, boolean shouldOffsetYMore
    ) {
        EntityType<?> type = this.getType(stack);
        if (type == null) {
            return InteractionResult.FAIL;
        } else if (!type.isAllowedInPeaceful(stack.get(DataComponents.ENTITY_DATA).getUnsafe()) && level.getDifficulty() == Difficulty.PEACEFUL) { // Paper - check peaceful override
            return InteractionResult.FAIL;
        } else {
            if (type.spawn((ServerLevel)level, stack, owner, pos, EntitySpawnReason.SPAWN_ITEM_USE, shouldOffsetY, shouldOffsetYMore) != null) {
                stack.consume(1, owner);
                level.gameEvent(owner, GameEvent.ENTITY_PLACE, pos);
            }

            return InteractionResult.SUCCESS;
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        BlockHitResult playerPovHitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (playerPovHitResult.getType() != HitResult.Type.BLOCK) {
            return InteractionResult.PASS;
        } else if (level instanceof ServerLevel serverLevel) {
            BlockPos blockPos = playerPovHitResult.getBlockPos();
            if (!(level.getBlockState(blockPos).getBlock() instanceof LiquidBlock)) {
                return InteractionResult.PASS;
            } else if (level.mayInteract(player, blockPos) && player.mayUseItemAt(blockPos, playerPovHitResult.getDirection(), itemInHand)) {
                InteractionResult interactionResult = this.spawnMob(player, itemInHand, level, blockPos, false, false);
                if (interactionResult == InteractionResult.SUCCESS) {
                    player.awardStat(Stats.ITEM_USED.get(this));
                }

                return interactionResult;
            } else {
                return InteractionResult.FAIL;
            }
        } else {
            return InteractionResult.SUCCESS;
        }
    }

    public boolean spawnsEntity(ItemStack stack, EntityType<?> entityType) {
        return Objects.equals(this.getType(stack), entityType);
    }

    public static @Nullable SpawnEggItem byId(@Nullable EntityType<?> type) {
        return BY_ID.get(type);
    }

    public static Iterable<SpawnEggItem> eggs() {
        return Iterables.unmodifiableIterable(BY_ID.values());
    }

    public @Nullable EntityType<?> getType(ItemStack stack) {
        TypedEntityData<EntityType<?>> typedEntityData = stack.get(DataComponents.ENTITY_DATA);
        return typedEntityData != null ? typedEntityData.type() : null;
    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return Optional.ofNullable(this.components().get(DataComponents.ENTITY_DATA))
            .map(TypedEntityData::type)
            .map(EntityType::requiredFeatures)
            .orElseGet(FeatureFlagSet::of);
    }

    public Optional<Mob> spawnOffspringFromSpawnEgg(Player player, Mob mob, EntityType<? extends Mob> entityType, ServerLevel level, Vec3 pos, ItemStack stack) {
        if (!this.spawnsEntity(stack, entityType)) {
            return Optional.empty();
        } else {
            Mob breedOffspring;
            if (mob instanceof AgeableMob) {
                breedOffspring = ((AgeableMob)mob).getBreedOffspring(level, (AgeableMob)mob);
            } else {
                breedOffspring = entityType.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
            }

            if (breedOffspring == null) {
                return Optional.empty();
            } else {
                breedOffspring.setBaby(true);
                if (!breedOffspring.isBaby()) {
                    return Optional.empty();
                } else {
                    breedOffspring.snapTo(pos.x(), pos.y(), pos.z(), 0.0F, 0.0F);
                    breedOffspring.applyComponentsFromItemStack(stack);
                    level.addFreshEntityWithPassengers(breedOffspring, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER_EGG); // CraftBukkit
                    stack.consume(1, player);
                    return Optional.of(breedOffspring);
                }
            }
        }
    }

    @Override
    public boolean shouldPrintOpWarning(ItemStack stack, @Nullable Player player) {
        if (player != null && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            TypedEntityData<EntityType<?>> typedEntityData = stack.get(DataComponents.ENTITY_DATA);
            if (typedEntityData != null) {
                return typedEntityData.type().onlyOpCanSetNbt();
            }
        }

        return false;
    }
}
