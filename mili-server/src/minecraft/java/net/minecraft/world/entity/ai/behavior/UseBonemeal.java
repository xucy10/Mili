package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;

public class UseBonemeal extends Behavior<Villager> {
    private static final int BONEMEALING_DURATION = 80;
    private long nextWorkCycleTime;
    private long lastBonemealingSession;
    private int timeWorkedSoFar;
    private Optional<BlockPos> cropPos = Optional.empty();

    public UseBonemeal() {
        super(ImmutableMap.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager owner) {
        if (owner.tickCount % 10 == 0 && (this.lastBonemealingSession == 0L || this.lastBonemealingSession + 160L <= owner.tickCount)) {
            if (owner.getInventory().countItem(Items.BONE_MEAL) <= 0) {
                return false;
            } else {
                this.cropPos = this.pickNextTarget(level, owner);
                return this.cropPos.isPresent();
            }
        } else {
            return false;
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager entity, long gameTime) {
        return this.timeWorkedSoFar < 80 && this.cropPos.isPresent();
    }

    private Optional<BlockPos> pickNextTarget(ServerLevel level, Villager villager) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        Optional<BlockPos> optional = Optional.empty();
        int i = 0;

        for (int i1 = -1; i1 <= 1; i1++) {
            for (int i2 = -1; i2 <= 1; i2++) {
                for (int i3 = -1; i3 <= 1; i3++) {
                    mutableBlockPos.setWithOffset(villager.blockPosition(), i1, i2, i3);
                    if (this.validPos(mutableBlockPos, level)) {
                        if (level.random.nextInt(++i) == 0) {
                            optional = Optional.of(mutableBlockPos.immutable());
                        }
                    }
                }
            }
        }

        return optional;
    }

    private boolean validPos(BlockPos pos, ServerLevel level) {
        BlockState blockState = level.getBlockState(pos);
        Block block = blockState.getBlock();
        return block instanceof CropBlock && !((CropBlock)block).isMaxAge(blockState);
    }

    @Override
    protected void start(ServerLevel level, Villager entity, long gameTime) {
        this.setCurrentCropAsTarget(entity);
        entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BONE_MEAL));
        this.nextWorkCycleTime = gameTime;
        this.timeWorkedSoFar = 0;
    }

    private void setCurrentCropAsTarget(Villager villager) {
        this.cropPos.ifPresent(pos -> {
            BlockPosTracker blockPosTracker = new BlockPosTracker(pos);
            villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, blockPosTracker);
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(blockPosTracker, 0.5F, 1));
        });
    }

    @Override
    protected void stop(ServerLevel level, Villager entity, long gameTime) {
        entity.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        this.lastBonemealingSession = entity.tickCount;
    }

    @Override
    protected void tick(ServerLevel level, Villager owner, long gameTime) {
        BlockPos blockPos = this.cropPos.get();
        if (gameTime >= this.nextWorkCycleTime && blockPos.closerToCenterThan(owner.position(), 1.0)) {
            ItemStack itemStack = ItemStack.EMPTY;
            SimpleContainer inventory = owner.getInventory();
            int containerSize = inventory.getContainerSize();

            for (int i = 0; i < containerSize; i++) {
                ItemStack item = inventory.getItem(i);
                if (item.is(Items.BONE_MEAL)) {
                    itemStack = item;
                    break;
                }
            }

            if (!itemStack.isEmpty() && BoneMealItem.growCrop(itemStack, level, blockPos)) {
                level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, blockPos, 15);
                this.cropPos = this.pickNextTarget(level, owner);
                this.setCurrentCropAsTarget(owner);
                this.nextWorkCycleTime = gameTime + 40L;
            }

            this.timeWorkedSoFar++;
        }
    }
}
