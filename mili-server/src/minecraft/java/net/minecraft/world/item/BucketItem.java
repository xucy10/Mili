package net.minecraft.world.item;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

public class BucketItem extends Item implements DispensibleContainerItem {
    private static @Nullable ItemStack itemLeftInHandAfterPlayerBucketEmptyEvent = null; // Paper - Fix PlayerBucketEmptyEvent result itemstack
    public final Fluid content;

    public BucketItem(Fluid content, Item.Properties properties) {
        super(properties);
        this.content = content;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        BlockHitResult playerPovHitResult = getPlayerPOVHitResult(
            level, player, this.content == Fluids.EMPTY ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE
        );
        if (playerPovHitResult.getType() == HitResult.Type.MISS) {
            return InteractionResult.PASS;
        } else if (playerPovHitResult.getType() != HitResult.Type.BLOCK) {
            return InteractionResult.PASS;
        } else {
            BlockPos blockPos = playerPovHitResult.getBlockPos();
            Direction direction = playerPovHitResult.getDirection();
            BlockPos blockPos1 = blockPos.relative(direction);
            if (!level.mayInteract(player, blockPos) || !player.mayUseItemAt(blockPos1, direction, itemInHand)) {
                return InteractionResult.FAIL;
            } else if (this.content == Fluids.EMPTY) {
                BlockState blockState = level.getBlockState(blockPos);
                if (blockState.getBlock() instanceof BucketPickup bucketPickup) {
                    // CraftBukkit start
                    ItemStack dummyFluid = bucketPickup.pickupBlock(player, org.bukkit.craftbukkit.util.DummyGeneratorAccess.INSTANCE, blockPos, blockState);
                    if (dummyFluid.isEmpty()) return InteractionResult.FAIL; // Don't fire event if the bucket won't be filled.
                    org.bukkit.event.player.PlayerBucketFillEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerBucketFillEvent(level, player, blockPos, blockPos, playerPovHitResult.getDirection(), itemInHand, dummyFluid.getItem(), hand);

                    if (event.isCancelled()) {
                        player.containerMenu.sendAllDataToRemote(); // SPIGOT-4541
                        return InteractionResult.FAIL;
                    }
                    // CraftBukkit end
                    ItemStack itemStack = bucketPickup.pickupBlock(player, level, blockPos, blockState);
                    if (!itemStack.isEmpty()) {
                        player.awardStat(Stats.ITEM_USED.get(this));
                        bucketPickup.getPickupSound().ifPresent(sound -> player.playSound(sound, 1.0F, 1.0F));
                        level.gameEvent(player, GameEvent.FLUID_PICKUP, blockPos);
                        ItemStack itemStack1 = ItemUtils.createFilledResult(itemInHand, player, org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItemStack())); // CraftBukkit
                        if (!level.isClientSide()) {
                            CriteriaTriggers.FILLED_BUCKET.trigger((ServerPlayer)player, itemStack);
                        }

                        return InteractionResult.SUCCESS.heldItemTransformedTo(itemStack1);
                    }
                }

                return InteractionResult.FAIL;
            } else {
                BlockState blockState = level.getBlockState(blockPos);
                BlockPos blockPos2 = blockState.getBlock() instanceof LiquidBlockContainer && this.content == Fluids.WATER ? blockPos : blockPos1;
                if (this.emptyContents(player, level, blockPos2, playerPovHitResult, playerPovHitResult.getDirection(), blockPos, itemInHand, hand)) { // CraftBukkit
                    this.checkExtraContent(player, level, itemInHand, blockPos2);
                    if (player instanceof ServerPlayer) {
                        CriteriaTriggers.PLACED_BLOCK.trigger((ServerPlayer)player, blockPos2, itemInHand);
                    }

                    player.awardStat(Stats.ITEM_USED.get(this));
                    ItemStack itemStack = ItemUtils.createFilledResult(itemInHand, player, getEmptySuccessItem(itemInHand, player));
                    return InteractionResult.SUCCESS.heldItemTransformedTo(itemStack);
                } else {
                    return InteractionResult.FAIL;
                }
            }
        }
    }

    public static ItemStack getEmptySuccessItem(ItemStack bucketStack, Player player) {
        // Paper start - Fix PlayerBucketEmptyEvent result itemstack
        if (itemLeftInHandAfterPlayerBucketEmptyEvent != null) {
            ItemStack itemInHand = itemLeftInHandAfterPlayerBucketEmptyEvent;
            itemLeftInHandAfterPlayerBucketEmptyEvent = null;
            return itemInHand;
        }
        // Paper end - Fix PlayerBucketEmptyEvent result itemstack
        return !player.hasInfiniteMaterials() ? new ItemStack(Items.BUCKET) : bucketStack;
    }

    @Override
    public void checkExtraContent(@Nullable LivingEntity entity, Level level, ItemStack stack, BlockPos pos) {
    }

    @Override
    public boolean emptyContents(@Nullable LivingEntity entity, Level level, BlockPos pos, @Nullable BlockHitResult hitResult) {
        // CraftBukkit start
        return this.emptyContents(entity, level, pos, hitResult, null, null, null, InteractionHand.MAIN_HAND);
    }

    public boolean emptyContents(@Nullable LivingEntity entity, Level level, BlockPos pos, @Nullable BlockHitResult hitResult, Direction direction, BlockPos clicked, ItemStack itemstack, InteractionHand hand) {
        // CraftBukkit end
        if (!(this.content instanceof FlowingFluid flowingFluid)) {
            return false;
        } else {
            BlockState blockState = level.getBlockState(pos);
            Block block = blockState.getBlock();
            boolean canBeReplaced = blockState.canBeReplaced(this.content);
            boolean flag = entity != null && entity.isShiftKeyDown();
            boolean flag1 = canBeReplaced
                || block instanceof LiquidBlockContainer liquidBlockContainer
                    && liquidBlockContainer.canPlaceLiquid(entity, level, pos, blockState, this.content);
            boolean flag2 = blockState.isAir() || flag1 && (!flag || hitResult == null);
            // CraftBukkit start
            if (flag2 && entity instanceof Player player) {
                org.bukkit.event.player.PlayerBucketEmptyEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerBucketEmptyEvent(level, player, pos, clicked, direction, itemstack, hand);
                if (event.isCancelled()) {
                    player.containerMenu.sendAllDataToRemote(); // SPIGOT-4541
                    return false;
                }
                itemLeftInHandAfterPlayerBucketEmptyEvent = event.getItemStack() != null ? event.getItemStack().equals(org.bukkit.craftbukkit.inventory.CraftItemStack.asNewCraftStack(net.minecraft.world.item.Items.BUCKET)) ? null : org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItemStack()) : ItemStack.EMPTY; // Paper - Fix PlayerBucketEmptyEvent result itemstack
            }
            // CraftBukkit end
            if (!flag2) {
                return hitResult != null && this.emptyContents(entity, level, hitResult.getBlockPos().relative(hitResult.getDirection()), null, direction, clicked, itemstack, hand); // CraftBukkit
            } else if (level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, pos) && this.content.is(FluidTags.WATER)) {
                int x = pos.getX();
                int y = pos.getY();
                int z = pos.getZ();
                level.playSound(
                    entity, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 2.6F + (level.random.nextFloat() - level.random.nextFloat()) * 0.8F
                );

                for (int i = 0; i < 8; i++) {
                    level.addParticle(
                        ParticleTypes.LARGE_SMOKE, x + level.random.nextFloat(), y + level.random.nextFloat(), z + level.random.nextFloat(), 0.0, 0.0, 0.0
                    );
                }

                return true;
            } else if (block instanceof LiquidBlockContainer liquidBlockContainer1 && this.content == Fluids.WATER) {
                liquidBlockContainer1.placeLiquid(level, pos, blockState, flowingFluid.getSource(false));
                this.playEmptySound(entity, level, pos);
                return true;
            } else {
                if (!level.isClientSide() && canBeReplaced && !blockState.liquid()) {
                    level.destroyBlock(pos, true);
                }

                if (!level.setBlock(pos, this.content.defaultFluidState().createLegacyBlock(), Block.UPDATE_ALL_IMMEDIATE)
                    && !blockState.getFluidState().isSource()) {
                    return false;
                } else {
                    this.playEmptySound(entity, level, pos);
                    return true;
                }
            }
        }
    }

    protected void playEmptySound(@Nullable LivingEntity entity, LevelAccessor level, BlockPos pos) {
        SoundEvent soundEvent = this.content.is(FluidTags.LAVA) ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY;
        level.playSound(entity, pos, soundEvent, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(entity, GameEvent.FLUID_PLACE, pos);
    }

    public Fluid getContent() {
        return this.content;
    }
}
