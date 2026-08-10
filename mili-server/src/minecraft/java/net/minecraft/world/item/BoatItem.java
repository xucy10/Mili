package net.minecraft.world.item;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class BoatItem extends Item {
    private final EntityType<? extends AbstractBoat> entityType;

    public BoatItem(EntityType<? extends AbstractBoat> entityType, Item.Properties properties) {
        super(properties);
        this.entityType = entityType;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        net.minecraft.world.phys.BlockHitResult playerPovHitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY); // Paper
        if (playerPovHitResult.getType() == HitResult.Type.MISS) {
            return InteractionResult.PASS;
        } else {
            Vec3 viewVector = player.getViewVector(1.0F);
            double d = 5.0;
            List<Entity> entities = level.getEntities(
                player, player.getBoundingBox().expandTowards(viewVector.scale(5.0)).inflate(1.0), EntitySelector.CAN_BE_PICKED
            );
            if (!entities.isEmpty()) {
                Vec3 eyePosition = player.getEyePosition();

                for (Entity entity : entities) {
                    AABB aabb = entity.getBoundingBox().inflate(entity.getPickRadius());
                    if (aabb.contains(eyePosition)) {
                        return InteractionResult.PASS;
                    }
                }
            }

            if (playerPovHitResult.getType() == HitResult.Type.BLOCK) {
                // CraftBukkit start - Boat placement
                org.bukkit.event.player.PlayerInteractEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerInteractEvent(player, org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK, playerPovHitResult.getBlockPos(), playerPovHitResult.getDirection(), itemInHand, false, hand, playerPovHitResult.getLocation());

                if (event.isCancelled()) {
                    return InteractionResult.PASS;
                }
                // CraftBukkit end
                AbstractBoat boat = this.getBoat(level, playerPovHitResult, itemInHand, player);
                if (boat == null) {
                    return InteractionResult.FAIL;
                } else {
                    boat.setYRot(player.getYRot());
                    if (!level.noCollision(boat, boat.getBoundingBox())) {
                        return InteractionResult.FAIL;
                    } else {
                        if (!level.isClientSide()) {
                            // CraftBukkit start
                            if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityPlaceEvent(level, playerPovHitResult.getBlockPos(), player.getDirection(), player, boat, hand).isCancelled()) {
                                return InteractionResult.FAIL;
                            }

                            if (!level.addFreshEntity(boat)) {
                                return InteractionResult.PASS;
                            }
                            // CraftBukkit end
                            level.gameEvent(player, GameEvent.ENTITY_PLACE, playerPovHitResult.getLocation());
                            itemInHand.consume(1, player);
                        }

                        player.awardStat(Stats.ITEM_USED.get(this));
                        return InteractionResult.SUCCESS;
                    }
                }
            } else {
                return InteractionResult.PASS;
            }
        }
    }

    private @Nullable AbstractBoat getBoat(Level level, HitResult hitResult, ItemStack stack, Player player) {
        AbstractBoat abstractBoat = this.entityType.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (abstractBoat != null) {
            Vec3 location = hitResult.getLocation();
            abstractBoat.setInitialPos(location.x, location.y, location.z);
            if (level instanceof ServerLevel serverLevel) {
                EntityType.<AbstractBoat>createDefaultStackConfig(serverLevel, stack, player).accept(abstractBoat);
            }
        }

        return abstractBoat;
    }
}
