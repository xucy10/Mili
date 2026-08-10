package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

public class HarvestFarmland extends Behavior<Villager> {
    private static final int HARVEST_DURATION = 200;
    public static final float SPEED_MODIFIER = 0.5F;
    private @Nullable BlockPos aboveFarmlandPos;
    private long nextOkStartTime;
    private int timeWorkedSoFar;
    private final List<BlockPos> validFarmlandAroundVillager = Lists.newArrayList();

    public HarvestFarmland() {
        super(
            ImmutableMap.of(
                MemoryModuleType.LOOK_TARGET,
                MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.WALK_TARGET,
                MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.SECONDARY_JOB_SITE,
                MemoryStatus.VALUE_PRESENT
            )
        );
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager owner) {
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) {
            return false;
        } else if (!owner.getVillagerData().profession().is(VillagerProfession.FARMER)) {
            return false;
        } else {
            BlockPos.MutableBlockPos mutableBlockPos = owner.blockPosition().mutable();
            this.validFarmlandAroundVillager.clear();

            for (int i = -1; i <= 1; i++) {
                for (int i1 = -1; i1 <= 1; i1++) {
                    for (int i2 = -1; i2 <= 1; i2++) {
                        mutableBlockPos.set(owner.getX() + i, owner.getY() + i1, owner.getZ() + i2);
                        if (this.validPos(mutableBlockPos, level)) {
                            this.validFarmlandAroundVillager.add(new BlockPos(mutableBlockPos));
                        }
                    }
                }
            }

            this.aboveFarmlandPos = this.getValidFarmland(level);
            return this.aboveFarmlandPos != null;
        }
    }

    private @Nullable BlockPos getValidFarmland(ServerLevel level) {
        return this.validFarmlandAroundVillager.isEmpty()
            ? null
            : this.validFarmlandAroundVillager.get(level.getRandom().nextInt(this.validFarmlandAroundVillager.size()));
    }

    private boolean validPos(BlockPos pos, ServerLevel level) {
        BlockState blockState = level.getBlockState(pos);
        Block block = blockState.getBlock();
        Block block1 = level.getBlockState(pos.below()).getBlock();
        return block instanceof CropBlock && ((CropBlock)block).isMaxAge(blockState) || blockState.isAir() && block1 instanceof FarmBlock;
    }

    @Override
    protected void start(ServerLevel level, Villager entity, long gameTime) {
        if (gameTime > this.nextOkStartTime && this.aboveFarmlandPos != null) {
            entity.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(this.aboveFarmlandPos));
            entity.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPosTracker(this.aboveFarmlandPos), 0.5F, 1));
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager entity, long gameTime) {
        entity.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        this.timeWorkedSoFar = 0;
        this.nextOkStartTime = gameTime + 40L;
    }

    @Override
    protected void tick(ServerLevel level, Villager owner, long gameTime) {
        if (this.aboveFarmlandPos == null || this.aboveFarmlandPos.closerToCenterThan(owner.position(), 1.0)) {
            if (this.aboveFarmlandPos != null && gameTime > this.nextOkStartTime) {
                BlockState blockState = level.getBlockState(this.aboveFarmlandPos);
                Block block = blockState.getBlock();
                Block block1 = level.getBlockState(this.aboveFarmlandPos.below()).getBlock();
                if (block instanceof CropBlock && ((CropBlock)block).isMaxAge(blockState)) {
                    if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(owner, this.aboveFarmlandPos, blockState.getFluidState().createLegacyBlock())) { // CraftBukkit // Paper - fix wrong block state
                    level.destroyBlock(this.aboveFarmlandPos, true, owner);
                    } // CraftBukkit
                }

                if (blockState.isAir() && block1 instanceof FarmBlock && owner.hasFarmSeeds()) {
                    SimpleContainer inventory = owner.getInventory();

                    for (int i = 0; i < inventory.getContainerSize(); i++) {
                        ItemStack item = inventory.getItem(i);
                        boolean flag = false;
                        if (!item.isEmpty() && item.is(ItemTags.VILLAGER_PLANTABLE_SEEDS) && item.getItem() instanceof BlockItem blockItem) {
                            BlockState blockState1 = blockItem.getBlock().defaultBlockState();
                            if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(owner, this.aboveFarmlandPos, blockState1)) { // CraftBukkit
                            level.setBlockAndUpdate(this.aboveFarmlandPos, blockState1);
                            level.gameEvent(GameEvent.BLOCK_PLACE, this.aboveFarmlandPos, GameEvent.Context.of(owner, blockState1));
                            flag = true;
                            } // CraftBukkit
                        }

                        if (flag) {
                            level.playSound(
                                null,
                                this.aboveFarmlandPos.getX(),
                                this.aboveFarmlandPos.getY(),
                                this.aboveFarmlandPos.getZ(),
                                SoundEvents.CROP_PLANTED,
                                SoundSource.BLOCKS,
                                1.0F,
                                1.0F
                            );
                            item.shrink(1);
                            if (item.isEmpty()) {
                                inventory.setItem(i, ItemStack.EMPTY);
                            }
                            break;
                        }
                    }
                }

                if (block instanceof CropBlock && !((CropBlock)block).isMaxAge(blockState)) {
                    this.validFarmlandAroundVillager.remove(this.aboveFarmlandPos);
                    this.aboveFarmlandPos = this.getValidFarmland(level);
                    if (this.aboveFarmlandPos != null) {
                        this.nextOkStartTime = gameTime + 20L;
                        owner.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPosTracker(this.aboveFarmlandPos), 0.5F, 1));
                        owner.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(this.aboveFarmlandPos));
                    }
                }
            }

            this.timeWorkedSoFar++;
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager entity, long gameTime) {
        return this.timeWorkedSoFar < 200;
    }
}
