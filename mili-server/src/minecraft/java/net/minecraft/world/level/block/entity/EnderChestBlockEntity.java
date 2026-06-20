package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class EnderChestBlockEntity extends BlockEntity implements LidBlockEntity, org.leavesmc.leaves.lithium.common.block.entity.SleepingBlockEntity { // Leaves - Lithium Sleeping Block Entity
    private final ChestLidController chestLidController = new ChestLidController();
    public final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            level.playSound(
                null,
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                SoundEvents.ENDER_CHEST_OPEN,
                SoundSource.BLOCKS,
                0.5F,
                level.random.nextFloat() * 0.1F + 0.9F
            );
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            level.playSound(
                null,
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                SoundEvents.ENDER_CHEST_CLOSE,
                SoundSource.BLOCKS,
                0.5F,
                level.random.nextFloat() * 0.1F + 0.9F
            );
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int count, int openCount) {
            level.blockEvent(EnderChestBlockEntity.this.worldPosition, Blocks.ENDER_CHEST, 1, openCount);
        }

        @Override
        public boolean isOwnContainer(Player player) {
            return player.getEnderChestInventory().isActiveChest(EnderChestBlockEntity.this);
        }
    };

    public EnderChestBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.ENDER_CHEST, pos, blockState);
    }

    public static void lidAnimateTick(Level level, BlockPos pos, BlockState state, EnderChestBlockEntity blockEntity) {
        blockEntity.chestLidController.tickLid();
        if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled) blockEntity.checkSleep(); // Leaves - Lithium Sleeping Block Entity
    }

    @Override
    public boolean triggerEvent(int id, int type) {
        if (id == 1) {
            if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled && this.sleepingTicker != null) this.wakeUpNow(); // Leaves - Lithium Sleeping Block Entity
            this.chestLidController.shouldBeOpen(type > 0);
            return true;
        } else {
            return super.triggerEvent(id, type);
        }
    }

    public void startOpen(ContainerUser containerUser) {
        if (!this.remove && !containerUser.getLivingEntity().isSpectator()) {
            this.openersCounter
                .incrementOpeners(
                    containerUser.getLivingEntity(), this.getLevel(), this.getBlockPos(), this.getBlockState(), containerUser.getContainerInteractionRange()
                );
        }
    }

    public void stopOpen(ContainerUser containerUser) {
        if (!this.remove && !containerUser.getLivingEntity().isSpectator()) {
            this.openersCounter.decrementOpeners(containerUser.getLivingEntity(), this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }

    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    public void recheckOpen() {
        if (!this.remove) {
            this.openersCounter.recheckOpeners(this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }

    @Override
    public float getOpenNess(float partialTick) {
        return this.chestLidController.getOpenness(partialTick);
    }

    // Leaves start - Lithium Sleeping Block Entity
    private net.minecraft.world.level.chunk.LevelChunk.RebindableTickingBlockEntityWrapper tickWrapper = null;
    private TickingBlockEntity sleepingTicker = null;

    @Override
    public net.minecraft.world.level.chunk.LevelChunk.RebindableTickingBlockEntityWrapper lithium$getTickWrapper() {
        return this.tickWrapper;
    }

    @Override
    public void lithium$setTickWrapper(net.minecraft.world.level.chunk.LevelChunk.RebindableTickingBlockEntityWrapper tickWrapper) {
        this.tickWrapper = tickWrapper;
    }

    @Override
    public TickingBlockEntity lithium$getSleepingTicker() {
        return this.sleepingTicker;
    }

    @Override
    public void lithium$setSleepingTicker(TickingBlockEntity sleepingTicker) {
        this.sleepingTicker = sleepingTicker;
    }

    private void checkSleep() {
        //If the animation is finished, it will stay unchanged until the next triggerEvent, which may change shouldBeOpen
        if (this.getOpenNess(0.0F) == this.getOpenNess(1.0F)) {
            this.lithium$startSleeping();
        }
    }
    // Leaves end - Lithium Sleeping Block Entity
}
