package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.CopperGolemStatueBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class CopperGolemStatueBlockEntity extends BlockEntity {
    public CopperGolemStatueBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.COPPER_GOLEM_STATUE, pos, blockState);
    }

    public void createStatue(CopperGolem copperGolem) {
        this.setComponents(DataComponentMap.builder().addAll(this.components()).set(DataComponents.CUSTOM_NAME, copperGolem.getCustomName()).build());
        super.setChanged();
    }

    public @Nullable CopperGolem removeStatue(BlockState state) {
        CopperGolem copperGolem = EntityType.COPPER_GOLEM.create(this.level, EntitySpawnReason.TRIGGERED);
        if (copperGolem != null) {
            copperGolem.setCustomName(this.components().get(DataComponents.CUSTOM_NAME));
            return this.initCopperGolem(state, copperGolem);
        } else {
            return null;
        }
    }

    private CopperGolem initCopperGolem(BlockState state, CopperGolem copperGolem) {
        BlockPos blockPos = this.getBlockPos();
        copperGolem.snapTo(blockPos.getCenter().x, blockPos.getY(), blockPos.getCenter().z, state.getValue(CopperGolemStatueBlock.FACING).toYRot(), 0.0F);
        copperGolem.yHeadRot = copperGolem.getYRot();
        copperGolem.yBodyRot = copperGolem.getYRot();
        copperGolem.playSpawnSound();
        return copperGolem;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public ItemStack getItem(ItemStack stack, CopperGolemStatueBlock.Pose pose) {
        stack.applyComponents(this.collectComponents());
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(CopperGolemStatueBlock.POSE, pose));
        return stack;
    }
}
