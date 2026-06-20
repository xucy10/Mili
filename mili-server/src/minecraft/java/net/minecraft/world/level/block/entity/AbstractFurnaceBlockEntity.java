package net.minecraft.world.level.block.entity;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap.Entry;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.RecipeCraftingHolder;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public abstract class AbstractFurnaceBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, RecipeCraftingHolder, StackedContentsCompatible, org.leavesmc.leaves.lithium.common.block.entity.inventory_change_tracking.InventoryChangeTracker, org.leavesmc.leaves.lithium.common.block.entity.SleepingBlockEntity, org.leavesmc.leaves.lithium.common.block.entity.SetChangedHandlingBlockEntity, org.leavesmc.leaves.lithium.api.inventory.LithiumInventory { // Leaves - Lithium Sleeping Block Entity
    protected static final int SLOT_INPUT = 0;
    protected static final int SLOT_FUEL = 1;
    protected static final int SLOT_RESULT = 2;
    public static final int DATA_LIT_TIME = 0;
    private static final int[] SLOTS_FOR_UP = new int[]{0};
    private static final int[] SLOTS_FOR_DOWN = new int[]{2, 1};
    private static final int[] SLOTS_FOR_SIDES = new int[]{1};
    public static final int DATA_LIT_DURATION = 1;
    public static final int DATA_COOKING_PROGRESS = 2;
    public static final int DATA_COOKING_TOTAL_TIME = 3;
    public static final int NUM_DATA_VALUES = 4;
    public static final int BURN_TIME_STANDARD = 200;
    public static final int BURN_COOL_SPEED = 2;
    private static final Codec<Map<ResourceKey<Recipe<?>>, Integer>> RECIPES_USED_CODEC = Codec.unboundedMap(Recipe.KEY_CODEC, Codec.INT);
    private static final short DEFAULT_COOKING_TIMER = 0;
    private static final short DEFAULT_COOKING_TOTAL_TIME = 0;
    private static final short DEFAULT_LIT_TIME_REMAINING = 0;
    private static final short DEFAULT_LIT_TOTAL_TIME = 0;
    protected NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
    public int litTimeRemaining;
    int litTotalTime;
    public int cookingTimer;
    public int cookingTotalTime;
    protected final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            switch (index) {
                case 0:
                    return AbstractFurnaceBlockEntity.this.litTimeRemaining;
                case 1:
                    return AbstractFurnaceBlockEntity.this.litTotalTime;
                case 2:
                    return AbstractFurnaceBlockEntity.this.cookingTimer;
                case 3:
                    return AbstractFurnaceBlockEntity.this.cookingTotalTime;
                default:
                    return 0;
            }
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0:
                    AbstractFurnaceBlockEntity.this.litTimeRemaining = value;
                    break;
                case 1:
                    AbstractFurnaceBlockEntity.this.litTotalTime = value;
                    break;
                case 2:
                    AbstractFurnaceBlockEntity.this.cookingTimer = value;
                    break;
                case 3:
                    AbstractFurnaceBlockEntity.this.cookingTotalTime = value;
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };
    public final Reference2IntOpenHashMap<ResourceKey<Recipe<?>>> recipesUsed = new Reference2IntOpenHashMap<>();
    private final RecipeManager.CachedCheck<SingleRecipeInput, ? extends AbstractCookingRecipe> quickCheck;
    public final RecipeType<? extends AbstractCookingRecipe> recipeType; // Paper - cook speed multiplier API
    public double cookSpeedMultiplier = 1.0; // Paper - cook speed multiplier API

    protected AbstractFurnaceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState, RecipeType<? extends AbstractCookingRecipe> recipeType) {
        super(type, pos, blockState);
        this.quickCheck = RecipeManager.createCheck(recipeType);
        this.recipeType = recipeType; // Paper - cook speed multiplier API
    }

    // CraftBukkit start - add fields and methods
    private int maxStack = MAX_STACK;
    public List<org.bukkit.entity.HumanEntity> transaction = new java.util.ArrayList<>();

    @Override
    public List<ItemStack> getContents() {
        return this.items;
    }

    @Override
    public void onOpen(org.bukkit.craftbukkit.entity.CraftHumanEntity player) {
        this.transaction.add(player);
    }

    @Override
    public void onClose(org.bukkit.craftbukkit.entity.CraftHumanEntity player) {
        this.transaction.remove(player);
    }

    @Override
    public List<org.bukkit.entity.HumanEntity> getViewers() {
        return this.transaction;
    }

    @Override
    public int getMaxStackSize() {
        return this.maxStack;
    }

    @Override
    public void setMaxStackSize(int size) {
        this.maxStack = size;
    }
    // CraftBukkit end

    private boolean isLit() {
        return this.litTimeRemaining > 0;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
        this.cookingTimer = input.getShortOr("cooking_time_spent", (short)0);
        this.cookingTotalTime = input.getShortOr("cooking_total_time", (short)0);
        this.litTimeRemaining = input.getShortOr("lit_time_remaining", (short)0);
        this.litTotalTime = input.getShortOr("lit_total_time", (short)0);
        this.recipesUsed.clear();
        this.recipesUsed.putAll(input.read("RecipesUsed", RECIPES_USED_CODEC).orElse(Map.of()));
        this.cookSpeedMultiplier = input.getDoubleOr("Paper.CookSpeedMultiplier", 1); // Paper - cook speed multiplier API
        if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled && this.isSleeping() && this.level != null) this.wakeUpNow(); // Leaves - Lithium Sleeping Block Entity
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putShort("cooking_time_spent", (short)this.cookingTimer);
        output.putShort("cooking_total_time", (short)this.cookingTotalTime);
        output.putShort("lit_time_remaining", (short)this.litTimeRemaining);
        output.putShort("lit_total_time", (short)this.litTotalTime);
        output.putDouble("Paper.CookSpeedMultiplier", this.cookSpeedMultiplier); // Paper - cook speed multiplier API
        ContainerHelper.saveAllItems(output, this.items);
        output.store("RecipesUsed", RECIPES_USED_CODEC, this.recipesUsed);
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace) {
        boolean isLit = furnace.isLit();
        boolean flag = false;
        if (furnace.isLit()) {
            furnace.litTimeRemaining--;
        }

        ItemStack itemStack = furnace.items.get(1);
        ItemStack itemStack1 = furnace.items.get(0);
        boolean flag1 = !itemStack1.isEmpty();
        boolean flag2 = !itemStack.isEmpty();
        if (furnace.isLit() || flag2 && flag1) {
            SingleRecipeInput singleRecipeInput = new SingleRecipeInput(itemStack1);
            RecipeHolder<? extends AbstractCookingRecipe> recipeHolder;
            if (flag1) {
                recipeHolder = furnace.quickCheck.getRecipeFor(singleRecipeInput, level).orElse(null);
            } else {
                recipeHolder = null;
            }

            int maxStackSize = furnace.getMaxStackSize();
            if (!furnace.isLit() && canBurn(level.registryAccess(), recipeHolder, singleRecipeInput, furnace.items, maxStackSize)) {
                // CraftBukkit start
                org.bukkit.craftbukkit.inventory.CraftItemStack fuel = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemStack);

                org.bukkit.event.inventory.FurnaceBurnEvent furnaceBurnEvent = new org.bukkit.event.inventory.FurnaceBurnEvent(
                    org.bukkit.craftbukkit.block.CraftBlock.at(level, pos),
                    fuel,
                    furnace.getBurnDuration(level.fuelValues(), itemStack)
                );
                if (!furnaceBurnEvent.callEvent()) return;
                // CraftBukkit end

                furnace.litTimeRemaining = furnaceBurnEvent.getBurnTime(); // CraftBukkit - respect event output
                furnace.litTotalTime = furnace.litTimeRemaining;
                if (furnace.isLit() && furnaceBurnEvent.isBurning()) { // CraftBukkit - respect event output
                    flag = true;
                    if (flag2 && furnaceBurnEvent.willConsumeFuel()) { // Paper - add consumeFuel to FurnaceBurnEvent
                        Item item = itemStack.getItem();
                        itemStack.shrink(1);
                        if (itemStack.isEmpty()) {
                            furnace.items.set(1, item.getCraftingRemainder());
                        }
                    }
                }
            }

            if (furnace.isLit() && canBurn(level.registryAccess(), recipeHolder, singleRecipeInput, furnace.items, maxStackSize)) {
                // CraftBukkit start
                if (recipeHolder != null && furnace.cookingTimer == 0) {
                    org.bukkit.craftbukkit.inventory.CraftItemStack source = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(furnace.items.get(0));
                    org.bukkit.inventory.CookingRecipe<?> recipe = (org.bukkit.inventory.CookingRecipe<?>) recipeHolder.toBukkitRecipe();

                    org.bukkit.event.inventory.FurnaceStartSmeltEvent event = new org.bukkit.event.inventory.FurnaceStartSmeltEvent(
                        org.bukkit.craftbukkit.block.CraftBlock.at(level, pos),
                        source,
                        recipe,
                        getTotalCookTime(level, furnace, furnace.recipeType, furnace.cookSpeedMultiplier) // Paper - cook speed multiplier API
                    );
                    event.callEvent();

                    furnace.cookingTotalTime = event.getTotalCookTime();
                }
                // CraftBukkit end

                furnace.cookingTimer++;
                if (furnace.cookingTimer >= furnace.cookingTotalTime) { // Paper - cook speed multiplier API
                    furnace.cookingTimer = 0;
                    furnace.cookingTotalTime = getTotalCookTime(level, furnace, furnace.recipeType, furnace.cookSpeedMultiplier); // Paper - cook speed multiplier API
                    if (burn(level.registryAccess(), recipeHolder, singleRecipeInput, furnace.items, maxStackSize, level, furnace.worldPosition)) { // CraftBukkit
                        furnace.setRecipeUsed(recipeHolder);
                    }

                    flag = true;
                }
            } else {
                furnace.cookingTimer = 0;
            }
        } else if (!furnace.isLit() && furnace.cookingTimer > 0) {
            furnace.cookingTimer = Mth.clamp(furnace.cookingTimer - 2, 0, furnace.cookingTotalTime);
        }

        if (isLit != furnace.isLit()) {
            flag = true;
            state = state.setValue(AbstractFurnaceBlock.LIT, furnace.isLit());
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }

        if (flag) {
            setChanged(level, pos, state);
        }
        if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled) furnace.checkSleep(state); // Leaves - Lithium Sleeping Block Entity
    }

    private static boolean canBurn(
        RegistryAccess registryAccess,
        @Nullable RecipeHolder<? extends AbstractCookingRecipe> recipe,
        SingleRecipeInput recipeInput,
        NonNullList<ItemStack> items,
        int maxStackSize
    ) {
        if (!items.get(0).isEmpty() && recipe != null) {
            ItemStack itemStack = recipe.value().assemble(recipeInput, registryAccess);
            if (itemStack.isEmpty()) {
                return false;
            } else {
                ItemStack itemStack1 = items.get(2);
                return itemStack1.isEmpty()
                    || ItemStack.isSameItemSameComponents(itemStack1, itemStack)
                        && (
                            itemStack1.getCount() < maxStackSize && itemStack1.getCount() < itemStack1.getMaxStackSize()
                                || itemStack1.getCount() < itemStack.getMaxStackSize()
                        );
            }
        } else {
            return false;
        }
    }

    private static boolean burn(
        RegistryAccess registryAccess,
        @Nullable RecipeHolder<? extends AbstractCookingRecipe> recipe,
        SingleRecipeInput recipeInput,
        NonNullList<ItemStack> items,
        int maxStackSize,
        net.minecraft.world.level.Level level, // CraftBukkit
        BlockPos blockPos // CraftBukkit
    ) {
        if (recipe != null && canBurn(registryAccess, recipe, recipeInput, items, maxStackSize)) {
            ItemStack itemStack = items.get(0); final ItemStack ingredient = itemStack; // Paper - OBFHELPER
            ItemStack itemStack1 = recipe.value().assemble(recipeInput, registryAccess); ItemStack result = itemStack1; // Paper - OBFHELPER
            ItemStack itemStack2 = items.get(2); final ItemStack existingResults = itemStack2; // Paper - OBFHELPER
            // CraftBukkit start - fire FurnaceSmeltEvent
            org.bukkit.craftbukkit.inventory.CraftItemStack apiIngredient = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(ingredient);
            org.bukkit.inventory.ItemStack apiResult = org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(result);

            org.bukkit.event.inventory.FurnaceSmeltEvent furnaceSmeltEvent = new org.bukkit.event.inventory.FurnaceSmeltEvent(
                org.bukkit.craftbukkit.block.CraftBlock.at(level, blockPos),
                apiIngredient,
                apiResult,
                (org.bukkit.inventory.CookingRecipe<?>) recipe.toBukkitRecipe() // Paper - Add recipe to cook events
            );
            if (!furnaceSmeltEvent.callEvent()) return false;

            apiResult = furnaceSmeltEvent.getResult();
            itemStack1 = result = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(apiResult);

            if (!result.isEmpty()) {
                if (existingResults.isEmpty()) {
                    items.set(SLOT_RESULT, result.copy());
                } else if (org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(existingResults).isSimilar(apiResult)) {
                    existingResults.grow(result.getCount());
                } else {
                    return false;
                }
            }

            /*
            if (itemStack2.isEmpty()) {
                items.set(2, itemStack1.copy());
            } else if (ItemStack.isSameItemSameComponents(itemStack2, itemStack1)) {
                itemStack2.grow(1);
            }
            */
            // CraftBukkit end

            if (itemStack.is(Blocks.WET_SPONGE.asItem()) && !items.get(1).isEmpty() && items.get(1).is(Items.BUCKET)) {
                items.set(1, new ItemStack(Items.WATER_BUCKET));
            }

            itemStack.shrink(1);
            return true;
        } else {
            return false;
        }
    }

    protected int getBurnDuration(FuelValues fuelValues, ItemStack stack) {
        return fuelValues.burnDuration(stack);
    }

    public static int getTotalCookTime(@Nullable ServerLevel level, AbstractFurnaceBlockEntity furnace, RecipeType<? extends AbstractCookingRecipe> recipeType, double cookSpeedMultiplier) { // Paper - cook speed multiplier API
        SingleRecipeInput singleRecipeInput = new SingleRecipeInput(furnace.getItem(0));
        // Paper start - cook speed multiplier API
        /* Scale the recipe's cooking time to the current cookSpeedMultiplier */
        int cookTime = level != null
            ? furnace.quickCheck.getRecipeFor(singleRecipeInput, level).map(recipeHolder -> recipeHolder.value().cookingTime()).orElse(BURN_TIME_STANDARD)
            /* passing a null level here is safe. world is only used for map extending recipes which won't happen here */
            : (net.minecraft.server.MinecraftServer.getServer().getRecipeManager().getRecipeFor(recipeType, singleRecipeInput, level).map(recipeHolder -> recipeHolder.value().cookingTime()).orElse(BURN_TIME_STANDARD));
        return (int) Math.ceil (cookTime / cookSpeedMultiplier);
        // Paper end - cook speed multiplier API
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        if (side == Direction.DOWN) {
            return SLOTS_FOR_DOWN;
        } else {
            return side == Direction.UP ? SLOTS_FOR_UP : SLOTS_FOR_SIDES;
        }
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction direction) {
        return this.canPlaceItem(index, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        return direction != Direction.DOWN || index != 1 || stack.is(Items.WATER_BUCKET) || stack.is(Items.BUCKET);
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        ItemStack itemStack = this.items.get(index);
        boolean flag = !stack.isEmpty() && ItemStack.isSameItemSameComponents(itemStack, stack);
        this.items.set(index, stack);
        stack.limitSize(this.getMaxStackSize(stack));
        if (index == 0 && !flag && this.level instanceof ServerLevel serverLevel) {
            this.cookingTotalTime = getTotalCookTime(serverLevel, this, this.recipeType, this.cookSpeedMultiplier); // Paper - cook speed multiplier API
            this.cookingTimer = 0;
            this.setChanged();
        }
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        if (index == 2) {
            return false;
        } else if (index != 1) {
            return true;
        } else {
            ItemStack itemStack = this.items.get(1);
            return this.level.fuelValues().isFuel(stack) || stack.is(Items.BUCKET) && !itemStack.is(Items.BUCKET);
        }
    }

    @Override
    public void setRecipeUsed(@Nullable RecipeHolder<?> recipe) {
        if (recipe != null) {
            ResourceKey<Recipe<?>> resourceKey = recipe.id();
            this.recipesUsed.addTo(resourceKey, 1);
        }
    }

    @Override
    public @Nullable RecipeHolder<?> getRecipeUsed() {
        return null;
    }

    @Override
    public void awardUsedRecipes(Player player, List<ItemStack> items) {
    }

    public void awardUsedRecipesAndPopExperience(ServerPlayer player, ItemStack itemstack, int amount) { // CraftBukkit
        List<RecipeHolder<?>> recipesToAwardAndPopExperience = this.getRecipesToAwardAndPopExperience(player.level(), player.position(), this.worldPosition, player, itemstack, amount); // CraftBukkit - overload for exp spawn events
        player.awardRecipes(recipesToAwardAndPopExperience);

        for (RecipeHolder<?> recipeHolder : recipesToAwardAndPopExperience) {
            player.triggerRecipeCrafted(recipeHolder, this.items);
        }

        this.recipesUsed.clear();
    }

    public List<RecipeHolder<?>> getRecipesToAwardAndPopExperience(ServerLevel level, Vec3 popVec) {
    // CraftBukkit start
        return this.getRecipesToAwardAndPopExperience(level, popVec, this.worldPosition, null, null, 0);
    }
    public List<RecipeHolder<?>> getRecipesToAwardAndPopExperience(ServerLevel level, Vec3 popVec, BlockPos blockPos, ServerPlayer serverPlayer, ItemStack itemStack, int amount) {
    // CraftBukkit end
        List<RecipeHolder<?>> list = Lists.newArrayList();

        for (Entry<ResourceKey<Recipe<?>>> entry : this.recipesUsed.reference2IntEntrySet()) {
            level.recipeAccess().byKey(entry.getKey()).ifPresent(recipeHolder -> {
                if (!(recipeHolder.value() instanceof AbstractCookingRecipe)) return; // Paper - don't process non-cooking recipes
                list.add((RecipeHolder<?>)recipeHolder);
                createExperience(level, popVec, entry.getIntValue(), ((AbstractCookingRecipe)recipeHolder.value()).experience(), blockPos, serverPlayer, itemStack, amount); // Paper - don't process non-cooking recipes
            });
        }

        return list;
    }

    private static void createExperience(ServerLevel level, Vec3 popVec, int recipeIndex, float experience, BlockPos blockPos, ServerPlayer serverPlayer, ItemStack itemStack, int amount) { // CraftBukkit
        int floor = Mth.floor(recipeIndex * experience);
        float fraction = Mth.frac(recipeIndex * experience);
        if (fraction != 0.0F && level.random.nextFloat() < fraction) {
            floor++;
        }

        // CraftBukkit start - fire FurnaceExtractEvent / BlockExpEvent
        org.bukkit.event.block.BlockExpEvent event;
        if (amount != 0) {
            event = new org.bukkit.event.inventory.FurnaceExtractEvent(
                serverPlayer.getBukkitEntity(),
                org.bukkit.craftbukkit.block.CraftBlock.at(level, blockPos),
                org.bukkit.craftbukkit.inventory.CraftItemType.minecraftToBukkit(itemStack.getItem()),
                amount,
                floor
            );
        } else {
            event = new org.bukkit.event.block.BlockExpEvent(
                org.bukkit.craftbukkit.block.CraftBlock.at(level, blockPos),
                floor
            );
        }
        event.callEvent();
        floor = event.getExpToDrop();
        // CraftBukkit end

        ExperienceOrb.awardWithDirection(level, popVec, net.minecraft.world.phys.Vec3.ZERO, floor, org.bukkit.entity.ExperienceOrb.SpawnReason.FURNACE, serverPlayer, null); // Paper
    }

    @Override
    public void fillStackedContents(StackedItemContents stackedContents) {
        // Paper start - don't account fuel stack (fixes MC-243057)
        stackedContents.accountStack(this.items.get(SLOT_INPUT));
        stackedContents.accountStack(this.items.get(SLOT_RESULT));
        // Paper end - don't account fuel stack (fixes MC-243057)
        for (ItemStack itemStack : this.items) {
            stackedContents.accountStack(itemStack);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level instanceof ServerLevel serverLevel) {
            this.getRecipesToAwardAndPopExperience(serverLevel, Vec3.atCenterOf(pos));
        }
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

    private void checkSleep(BlockState state) {
        if (!this.isLit() && this.cookingTimer == 0 && (state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER)) && this.level != null) {
            this.lithium$startSleeping();
        }
    }

    @Override
    public void lithium$handleSetChanged() {
        if (this.isSleeping() && this.level != null && !this.level.isClientSide()) {
            this.wakeUpNow();
        }
    }

    @Override
    public net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> getInventoryLithium() {
        return items;
    }

    @Override
    public void setInventoryLithium(net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> inventory) {
        items = inventory;
    }
    // Leaves end - Lithium Sleeping Block Entity
}
