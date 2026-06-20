package net.minecraft.world.phys.shapes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

public interface CollisionContext {
    static CollisionContext empty() {
        return EntityCollisionContext.Empty.WITHOUT_FLUID_COLLISIONS;
    }

    static CollisionContext emptyWithFluidCollisions() {
        return EntityCollisionContext.Empty.WITH_FLUID_COLLISIONS;
    }

    static CollisionContext of(Entity entity) {
        return (CollisionContext)(switch (entity) {
            case AbstractMinecart abstractMinecart -> AbstractMinecart.useExperimentalMovement(abstractMinecart.level())
                ? new MinecartCollisionContext(abstractMinecart, false)
                : new EntityCollisionContext(entity, false, false);
            default -> new EntityCollisionContext(entity, false, false);
        });
    }

    static CollisionContext of(Entity entity, boolean alwaysCollideWithFluid) {
        return new EntityCollisionContext(entity, alwaysCollideWithFluid, false);
    }

    static CollisionContext placementContext(@Nullable Player player) {
        return new EntityCollisionContext(
            player != null && player.isDescending(),
            true,
            player != null ? player.getY() : -Double.MAX_VALUE,
            player instanceof LivingEntity ? player.getMainHandItem() : ItemStack.EMPTY,
            false,
            player
        );
    }

    static CollisionContext withPosition(@Nullable Entity entity, double bottom) {
        return new EntityCollisionContext(
            entity != null && entity.isDescending(),
            true,
            entity != null ? bottom : -Double.MAX_VALUE,
            entity instanceof LivingEntity livingEntity ? livingEntity.getMainHandItem() : ItemStack.EMPTY,
            false,
            entity
        );
    }

    boolean isDescending();

    boolean isAbove(VoxelShape shape, BlockPos pos, boolean canAscend);

    boolean isHoldingItem(Item item);

    boolean alwaysCollideWithFluid();

    boolean canStandOnFluid(FluidState fluid1, FluidState fluid2);

    VoxelShape getCollisionShape(BlockState state, CollisionGetter collisionGetter, BlockPos pos);

    default boolean isPlacement() {
        return false;
    }
}
