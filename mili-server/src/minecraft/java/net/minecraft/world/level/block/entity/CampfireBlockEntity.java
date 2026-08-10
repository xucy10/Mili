package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class CampfireBlockEntity extends BlockEntity implements Clearable, org.leavesmc.leaves.lithium.common.block.entity.SleepingBlockEntity { // Leaves - Lithium Sleeping Block Entity
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int BURN_COOL_SPEED = 2;
    private static final int NUM_SLOTS = 4;
    private final NonNullList<ItemStack> items = NonNullList.withSize(4, ItemStack.EMPTY);
    public final int[] cookingProgress = new int[4];
    public final int[] cookingTime = new int[4];
    public final boolean[] stopCooking = new boolean[4]; // Paper - Add more Campfire API

    public CampfireBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.CAMPFIRE, pos, blockState);
    }

    public static void cookTick(
        ServerLevel level,
        BlockPos pos,
        BlockState state,
        CampfireBlockEntity campfire,
        RecipeManager.CachedCheck<SingleRecipeInput, CampfireCookingRecipe> check
    ) {
        boolean flag = false;

        for (int i = 0; i < campfire.items.size(); i++) {
            ItemStack itemStack = campfire.items.get(i);
            if (!itemStack.isEmpty()) {
                flag = true;
                if (!campfire.stopCooking[i]) { // Paper - Add more Campfire API
                campfire.cookingProgress[i]++;
                } // Paper - Add more Campfire API
                if (campfire.cookingProgress[i] >= campfire.cookingTime[i]) {
                    SingleRecipeInput singleRecipeInput = new SingleRecipeInput(itemStack);
                    // Paper start - add recipe to cook events
                    final var optionalCookingRecipe = check.getRecipeFor(singleRecipeInput, level);
                    ItemStack itemStack1 = optionalCookingRecipe
                        .map(recipe -> recipe.value().assemble(singleRecipeInput, level.registryAccess()))
                        .orElse(itemStack);
                    // Paper end - add recipe to cook events
                    if (itemStack1.isItemEnabled(level.enabledFeatures())) {
                        // CraftBukkit start - fire BlockCookEvent
                        org.bukkit.craftbukkit.inventory.CraftItemStack source = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemStack);
                        org.bukkit.inventory.ItemStack result = org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(itemStack1);

                        org.bukkit.event.block.BlockCookEvent blockCookEvent = new org.bukkit.event.block.BlockCookEvent(
                            org.bukkit.craftbukkit.block.CraftBlock.at(level, pos),
                            source,
                            result,
                            (org.bukkit.inventory.CookingRecipe<?>) optionalCookingRecipe.map(RecipeHolder::toBukkitRecipe).orElse(null) // Paper -Add recipe to cook events
                        );

                        if (!blockCookEvent.callEvent()) {
                            return;
                        }

                        result = blockCookEvent.getResult();
                        itemStack1 = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(result);
                        // CraftBukkit end
                        // Paper start - Fix item locations dropped from campfires
                        double deviation = 0.05F * RandomSource.GAUSSIAN_SPREAD_FACTOR;
                        while (!itemStack1.isEmpty()) {
                            net.minecraft.world.entity.item.ItemEntity droppedItem = new net.minecraft.world.entity.item.ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, itemStack1.split(level.random.nextInt(21) + 10));
                            droppedItem.setDeltaMovement(level.random.triangle(0.0D, deviation), level.random.triangle(0.2D, deviation), level.random.triangle(0.0D, deviation));
                            level.addFreshEntity(droppedItem);
                        }
                        // Paper end - Fix item locations dropped from campfires
                        campfire.items.set(i, ItemStack.EMPTY);
                        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
                        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(state));
                    }
                }
            }
        }

        if (flag) {
            setChanged(level, pos, state);
        } else if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled) campfire.lithium$startSleeping(); // Leaves - Lithium Sleeping Block Entity
    }

    public static void cooldownTick(Level level, BlockPos pos, BlockState state, CampfireBlockEntity blockEntity) {
        boolean flag = false;

        for (int i = 0; i < blockEntity.items.size(); i++) {
            if (blockEntity.cookingProgress[i] > 0) {
                flag = true;
                blockEntity.cookingProgress[i] = Mth.clamp(blockEntity.cookingProgress[i] - 2, 0, blockEntity.cookingTime[i]);
            }
        }

        if (flag) {
            setChanged(level, pos, state);
        } else if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled) blockEntity.lithium$startSleeping(); // Leaves - Lithium Sleeping Block Entity
    }

    public static void particleTick(Level level, BlockPos pos, BlockState state, CampfireBlockEntity blockEntity) {
        RandomSource randomSource = level.random;
        if (randomSource.nextFloat() < 0.11F) {
            for (int i = 0; i < randomSource.nextInt(2) + 2; i++) {
                CampfireBlock.makeParticles(level, pos, state.getValue(CampfireBlock.SIGNAL_FIRE), false);
            }
        }

        int i = state.getValue(CampfireBlock.FACING).get2DDataValue();

        for (int i1 = 0; i1 < blockEntity.items.size(); i1++) {
            if (!blockEntity.items.get(i1).isEmpty() && randomSource.nextFloat() < 0.2F) {
                Direction direction = Direction.from2DDataValue(Math.floorMod(i1 + i, 4));
                float f = 0.3125F;
                double d = pos.getX() + 0.5 - direction.getStepX() * 0.3125F + direction.getClockWise().getStepX() * 0.3125F;
                double d1 = pos.getY() + 0.5;
                double d2 = pos.getZ() + 0.5 - direction.getStepZ() * 0.3125F + direction.getClockWise().getStepZ() * 0.3125F;

                for (int i2 = 0; i2 < 4; i2++) {
                    level.addParticle(ParticleTypes.SMOKE, d, d1, d2, 0.0, 5.0E-4, 0.0);
                }
            }
        }
    }

    public NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items.clear();
        ContainerHelper.loadAllItems(input, this.items);
        input.getIntArray("CookingTimes")
            .ifPresentOrElse(
                ints -> System.arraycopy(ints, 0, this.cookingProgress, 0, Math.min(this.cookingTime.length, ints.length)),
                () -> Arrays.fill(this.cookingProgress, 0)
            );
        input.getIntArray("CookingTotalTimes")
            .ifPresentOrElse(
                ints -> System.arraycopy(ints, 0, this.cookingTime, 0, Math.min(this.cookingTime.length, ints.length)), () -> Arrays.fill(this.cookingTime, 0)
            );

        // Paper start - Add more Campfire API
        input.read("Paper.StopCooking", com.mojang.serialization.Codec.BYTE_BUFFER).ifPresent(bytes -> {
            final boolean[] cookingState = new boolean[4];
            for (int index = 0; bytes.hasRemaining() && index < cookingState.length; index++) {
                cookingState[index] = bytes.get() == 1;
            }
            System.arraycopy(cookingState, 0, this.stopCooking, 0, Math.min(this.stopCooking.length, bytes.capacity()));
        });
        // Paper end - Add more Campfire API
        if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled) this.wakeUpNow(); // Leaves - Lithium Sleeping Block Entity
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items, true);
        output.putIntArray("CookingTimes", this.cookingProgress);
        output.putIntArray("CookingTotalTimes", this.cookingTime);
        // Paper start - Add more Campfire API
        byte[] cookingState = new byte[4];
        for (int index = 0; index < cookingState.length; index++) {
            cookingState[index] = (byte) (this.stopCooking[index] ? 1 : 0);
        }
        output.store("Paper.StopCooking", com.mojang.serialization.Codec.BYTE_BUFFER, java.nio.ByteBuffer.wrap(cookingState));
        // Paper end - Add more Campfire API
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag var4;
        try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
            TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, registries);
            ContainerHelper.saveAllItems(tagValueOutput, this.items, true);
            var4 = tagValueOutput.buildResult();
        }

        return var4;
    }

    public boolean placeFood(ServerLevel level, @Nullable LivingEntity entity, ItemStack stack) {
        for (int i = 0; i < this.items.size(); i++) {
            ItemStack itemStack = this.items.get(i);
            if (itemStack.isEmpty()) {
                Optional<RecipeHolder<CampfireCookingRecipe>> recipeFor = level.recipeAccess()
                    .getRecipeFor(RecipeType.CAMPFIRE_COOKING, new SingleRecipeInput(stack), level);
                if (recipeFor.isEmpty()) {
                    return false;
                }

                // CraftBukkit start
                org.bukkit.event.block.CampfireStartEvent event = new org.bukkit.event.block.CampfireStartEvent(
                    org.bukkit.craftbukkit.block.CraftBlock.at(this.level,this.worldPosition),
                    org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(stack),
                    (org.bukkit.inventory.CampfireRecipe) recipeFor.get().toBukkitRecipe()
                );
                this.level.getCraftServer().getPluginManager().callEvent(event);
                this.cookingTime[i] = event.getTotalCookTime(); // i -> event.getTotalCookTime()
                // CraftBukkit end
                this.cookingProgress[i] = 0;
                if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled) this.wakeUpNow(); // Leaves - Lithium Sleeping Block Entity
                this.items.set(i, stack.consumeAndReturn(1, entity));
                level.gameEvent(GameEvent.BLOCK_CHANGE, this.getBlockPos(), GameEvent.Context.of(entity, this.getBlockState()));
                this.markUpdated();
                return true;
            }
        }

        return false;
    }

    private void markUpdated() {
        this.setChanged();
        this.getLevel().sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), Block.UPDATE_ALL);
    }

    @Override
    public void clearContent() {
        this.items.clear();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this.level != null) {
            Containers.dropContents(this.level, pos, this.getItems());
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter componentGetter) {
        super.applyImplicitComponents(componentGetter);
        componentGetter.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(this.getItems());
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(this.getItems()));
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        output.discard("Items");
    }

    // Leaves start - Lithium Sleeping Block Entity
    private net.minecraft.world.level.chunk.LevelChunk.RebindableTickingBlockEntityWrapper tickWrapper = null;
    private TickingBlockEntity sleepingTicker = null;

    @Override
    public net.minecraft.world.level.chunk.LevelChunk.RebindableTickingBlockEntityWrapper lithium$getTickWrapper() {
        return tickWrapper;
    }

    @Override
    public void lithium$setTickWrapper(net.minecraft.world.level.chunk.LevelChunk.RebindableTickingBlockEntityWrapper tickWrapper) {
        this.tickWrapper = tickWrapper;
        this.lithium$setSleepingTicker(null);
    }

    @Override
    public TickingBlockEntity lithium$getSleepingTicker() {
        return sleepingTicker;
    }

    @Override
    public void lithium$setSleepingTicker(TickingBlockEntity sleepingTicker) {
        this.sleepingTicker = sleepingTicker;
    }
    // Leaves end - Lithium Sleeping Block Entity
}
