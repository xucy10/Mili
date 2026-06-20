package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;

public class WorkAtComposter extends WorkAtPoi {
    private static final List<Item> COMPOSTABLE_ITEMS = ImmutableList.of(Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS);

    @Override
    protected void useWorkstation(ServerLevel level, Villager villager) {
        Optional<GlobalPos> memory = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (!memory.isEmpty()) {
            GlobalPos globalPos = memory.get();
            BlockState blockState = level.getBlockState(globalPos.pos());
            if (blockState.is(Blocks.COMPOSTER)) {
                this.makeBread(level, villager);
                this.compostItems(level, villager, globalPos, blockState);
            }
        }
    }

    private void compostItems(ServerLevel level, Villager villager, GlobalPos global, BlockState state) {
        BlockPos blockPos = global.pos();
        if (state.getValue(ComposterBlock.LEVEL) == 8) {
            state = ComposterBlock.extractProduce(villager, state, level, blockPos);
        }

        int i = 20;
        int i1 = 10;
        int[] ints = new int[COMPOSTABLE_ITEMS.size()];
        SimpleContainer inventory = villager.getInventory();
        int containerSize = inventory.getContainerSize();
        BlockState blockState = state;

        for (int i2 = containerSize - 1; i2 >= 0 && i > 0; i2--) {
            ItemStack item = inventory.getItem(i2);
            int index = COMPOSTABLE_ITEMS.indexOf(item.getItem());
            if (index != -1) {
                int count = item.getCount();
                int i3 = ints[index] + count;
                ints[index] = i3;
                int min = Math.min(Math.min(i3 - 10, i), count);
                if (min > 0) {
                    i -= min;

                    for (int i4 = 0; i4 < min; i4++) {
                        blockState = ComposterBlock.insertItem(villager, blockState, level, item, blockPos);
                        if (blockState.getValue(ComposterBlock.LEVEL) == 7) {
                            this.spawnComposterFillEffects(level, state, blockPos, blockState);
                            return;
                        }
                    }
                }
            }
        }

        this.spawnComposterFillEffects(level, state, blockPos, blockState);
    }

    private void spawnComposterFillEffects(ServerLevel level, BlockState preState, BlockPos pos, BlockState postState) {
        level.levelEvent(LevelEvent.COMPOSTER_FILL, pos, postState != preState ? 1 : 0);
    }

    private void makeBread(ServerLevel level, Villager villager) {
        SimpleContainer inventory = villager.getInventory();
        if (inventory.countItem(Items.BREAD) <= 36) {
            int i = inventory.countItem(Items.WHEAT);
            int i1 = 3;
            int i2 = 3;
            int min = Math.min(3, i / 3);
            if (min != 0) {
                int i3 = min * 3;
                inventory.removeItemType(Items.WHEAT, i3);
                ItemStack itemStack = inventory.addItem(new ItemStack(Items.BREAD, min));
                if (!itemStack.isEmpty()) {
                    villager.forceDrops = true; // Paper - Add missing forceDrop toggles
                    villager.spawnAtLocation(level, itemStack, 0.5F);
                    villager.forceDrops = false; // Paper - Add missing forceDrop toggles
                }
            }
        }
    }
}
