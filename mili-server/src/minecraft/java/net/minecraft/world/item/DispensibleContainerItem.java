package net.minecraft.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public interface DispensibleContainerItem {
    default void checkExtraContent(@Nullable LivingEntity entity, Level level, ItemStack stack, BlockPos pos) {
    }

    boolean emptyContents(@Nullable LivingEntity entity, Level level, BlockPos pos, @Nullable BlockHitResult hitResult);
}
