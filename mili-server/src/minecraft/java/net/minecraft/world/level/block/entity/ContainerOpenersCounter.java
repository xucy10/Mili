package net.minecraft.world.level.block.entity;

import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;

public abstract class ContainerOpenersCounter {
    private static final int CHECK_TICK_DELAY = 5;
    private int openCount;
    private double maxInteractionRange;
    // CraftBukkit start
    public boolean opened;
    public void onAPIOpen(Level level, BlockPos blockPos, BlockState blockState) {
        this.onOpen(level, blockPos, blockState);
    }

    public void onAPIClose(Level level, BlockPos blockPos, BlockState blockState) {
        this.onClose(level, blockPos, blockState);
    }

    public void openerAPICountChanged(Level level, BlockPos blockPos, BlockState blockState, int count, int openCount) {
        this.openerCountChanged(level, blockPos, blockState, count, openCount);
    }
    // CraftBukkit end

    protected abstract void onOpen(Level level, BlockPos pos, BlockState state);

    protected abstract void onClose(Level level, BlockPos pos, BlockState state);

    protected abstract void openerCountChanged(Level level, BlockPos pos, BlockState state, int count, int openCount);

    public abstract boolean isOwnContainer(Player player);

    public void incrementOpeners(LivingEntity entity, Level level, BlockPos pos, BlockState state, double interactionRange) {
        int oldPower = Math.max(0, Math.min(15, this.openCount)); // CraftBukkit - Get power before new viewer is added
        int i = this.openCount++;

        // CraftBukkit start - Call redstone event
        if (level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.TRAPPED_CHEST)) {
            int newPower = Math.max(0, Math.min(15, this.openCount));

            if (oldPower != newPower) {
                org.bukkit.craftbukkit.event.CraftEventFactory.callRedstoneChange(level, pos, oldPower, newPower);
            }
        }
        // CraftBukkit end

        if (i == 0) {
            this.onOpen(level, pos, state);
            level.gameEvent(entity, GameEvent.CONTAINER_OPEN, pos);
            scheduleRecheck(level, pos, state);
        }

        this.openerCountChanged(level, pos, state, i, this.openCount);
        this.maxInteractionRange = Math.max(interactionRange, this.maxInteractionRange);
    }

    public void decrementOpeners(LivingEntity entity, Level level, BlockPos pos, BlockState state) {
        int oldPower = Math.max(0, Math.min(15, this.openCount)); // CraftBukkit - Get power before new viewer is added
        if (this.openCount == 0) return; // Paper - Prevent ContainerOpenersCounter openCount from going negative
        int i = this.openCount--;

        // CraftBukkit start - Call redstone event
        if (level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.TRAPPED_CHEST)) {
            int newPower = Math.max(0, Math.min(15, this.openCount));

            if (oldPower != newPower) {
                org.bukkit.craftbukkit.event.CraftEventFactory.callRedstoneChange(level, pos, oldPower, newPower);
            }
        }
        // CraftBukkit end

        if (this.openCount == 0) {
            this.onClose(level, pos, state);
            level.gameEvent(entity, GameEvent.CONTAINER_CLOSE, pos);
            this.maxInteractionRange = 0.0;
        }

        this.openerCountChanged(level, pos, state, i, this.openCount);
    }

    public List<ContainerUser> getEntitiesWithContainerOpen(Level level, BlockPos pos) {
        double d = this.maxInteractionRange + 4.0;
        AABB aabb = new AABB(pos).inflate(d);
        return level.getEntities((Entity)null, aabb, entity -> this.hasContainerOpen(entity, pos))
            .stream()
            .map(entity -> (ContainerUser)entity)
            .collect(Collectors.toList());
    }

    private boolean hasContainerOpen(Entity entity, BlockPos pos) {
        return entity instanceof ContainerUser containerUser && !containerUser.getLivingEntity().isSpectator() && containerUser.hasContainerOpen(this, pos);
    }

    public void recheckOpeners(Level level, BlockPos pos, BlockState state) {
        List<ContainerUser> entitiesWithContainerOpen = this.getEntitiesWithContainerOpen(level, pos);
        this.maxInteractionRange = 0.0;

        for (ContainerUser containerUser : entitiesWithContainerOpen) {
            this.maxInteractionRange = Math.max(containerUser.getContainerInteractionRange(), this.maxInteractionRange);
        }

        int size = entitiesWithContainerOpen.size();
        if (this.opened) size++; // CraftBukkit - add dummy count from API
        int i = this.openCount;
        if (i != size) {
            boolean flag = size != 0;
            boolean flag1 = i != 0;
            if (flag && !flag1) {
                this.onOpen(level, pos, state);
                level.gameEvent(null, GameEvent.CONTAINER_OPEN, pos);
            } else if (!flag) {
                this.onClose(level, pos, state);
                level.gameEvent(null, GameEvent.CONTAINER_CLOSE, pos);
            }

            this.openCount = size;
        }

        this.openerCountChanged(level, pos, state, i, size);
        if (size > 0) {
            scheduleRecheck(level, pos, state);
        }
    }

    public int getOpenerCount() {
        return this.openCount;
    }

    private static void scheduleRecheck(Level level, BlockPos pos, BlockState state) {
        level.scheduleTick(pos, state.getBlock(), 5);
    }
}
