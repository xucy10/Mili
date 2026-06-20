package net.minecraft.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public class MinecartItem extends Item {
    private final EntityType<? extends AbstractMinecart> type;

    public MinecartItem(EntityType<? extends AbstractMinecart> type, Item.Properties properties) {
        super(properties);
        this.type = type;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockState blockState = level.getBlockState(clickedPos);
        if (!blockState.is(BlockTags.RAILS)) {
            return InteractionResult.FAIL;
        } else {
            ItemStack itemInHand = context.getItemInHand();
            RailShape railShape = blockState.getBlock() instanceof BaseRailBlock
                ? blockState.getValue(((BaseRailBlock)blockState.getBlock()).getShapeProperty())
                : RailShape.NORTH_SOUTH;
            double d = 0.0;
            if (railShape.isSlope()) {
                d = 0.5;
            }

            Vec3 vec3 = new Vec3(clickedPos.getX() + 0.5, clickedPos.getY() + 0.0625 + d, clickedPos.getZ() + 0.5);
            AbstractMinecart abstractMinecart = AbstractMinecart.createMinecart(
                level, vec3.x, vec3.y, vec3.z, this.type, EntitySpawnReason.DISPENSER, itemInHand, context.getPlayer()
            );
            if (abstractMinecart == null) {
                return InteractionResult.FAIL;
            } else {
                if (AbstractMinecart.useExperimentalMovement(level)) {
                    for (Entity entity : level.getEntities(null, abstractMinecart.getBoundingBox())) {
                        if (entity instanceof AbstractMinecart) {
                            return InteractionResult.FAIL;
                        }
                    }
                }

                if (level instanceof ServerLevel serverLevel) {
                    // CraftBukkit start
                    if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityPlaceEvent(context, abstractMinecart).isCancelled()) {
                        if (context.getPlayer() != null) context.getPlayer().containerMenu.forceHeldSlot(context.getHand()); // Paper - Fix inventory desync
                        return InteractionResult.FAIL;
                    }
                    // CraftBukkit end
                    if (!serverLevel.addFreshEntity(abstractMinecart)) return InteractionResult.PASS; // CraftBukkit
                    serverLevel.gameEvent(
                        GameEvent.ENTITY_PLACE, clickedPos, GameEvent.Context.of(context.getPlayer(), serverLevel.getBlockState(clickedPos.below()))
                    );
                }

                itemInHand.shrink(1);
                return InteractionResult.SUCCESS;
            }
        }
    }
}
