package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.Spawner;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class SpawnerBlockEntity extends BlockEntity implements Spawner {
    private final BaseSpawner spawner = new BaseSpawner() {
        @Override
        public void broadcastEvent(Level level, BlockPos pos, int eventId) {
            level.blockEvent(pos, Blocks.SPAWNER, eventId, 0);
        }

        @Override
        public void setNextSpawnData(@Nullable Level level, BlockPos pos, SpawnData nextSpawnData) {
            super.setNextSpawnData(level, pos, nextSpawnData);
            if (level != null) {
                BlockState blockState = level.getBlockState(pos);
                level.sendBlockUpdated(pos, blockState, blockState, Block.UPDATE_NONE);
            }
        }
    };

    public SpawnerBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.MOB_SPAWNER, pos, blockState);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.spawner.load(this.level, this.worldPosition, input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        this.spawner.save(output);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, SpawnerBlockEntity blockEntity) {
        blockEntity.spawner.clientTick(level, pos);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SpawnerBlockEntity blockEntity) {
        blockEntity.spawner.serverTick((ServerLevel)level, pos);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag compoundTag = this.saveCustomOnly(registries);
        compoundTag.remove("SpawnPotentials");
        return compoundTag;
    }

    @Override
    public boolean triggerEvent(int id, int type) {
        return this.spawner.onEventTriggered(this.level, id) || super.triggerEvent(id, type);
    }

    @Override
    public void setEntityId(EntityType<?> type, RandomSource random) {
        this.spawner.setEntityId(type, this.level, random, this.worldPosition);
        this.setChanged();
    }

    public BaseSpawner getSpawner() {
        return this.spawner;
    }
}
