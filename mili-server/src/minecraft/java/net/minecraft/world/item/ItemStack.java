package net.minecraft.world.item;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.DataResult.Error;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.NullOps;
import net.minecraft.util.StringUtil;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TooltipProvider;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Spawner;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.gameevent.GameEvent;
import org.apache.commons.lang3.function.TriConsumer;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
// Leaves start - Lithium Sleeping Block Entity
import org.leavesmc.leaves.lithium.common.util.change_tracking.ChangePublisher;
import org.leavesmc.leaves.lithium.common.util.change_tracking.ChangeSubscriber;
// Leaves end - Lithium Sleeping Block Entity

public final class ItemStack implements DataComponentHolder, ChangePublisher<net.minecraft.world.item.ItemStack>, ChangeSubscriber<PatchedDataComponentMap> { // Leaves - Lithium Sleeping Block Entity
    private static final List<Component> OP_NBT_WARNING = List.of(
        Component.translatable("item.op_warning.line1").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
        Component.translatable("item.op_warning.line2").withStyle(ChatFormatting.RED),
        Component.translatable("item.op_warning.line3").withStyle(ChatFormatting.RED)
    );
    private static final Component UNBREAKABLE_TOOLTIP = Component.translatable("item.unbreakable").withStyle(ChatFormatting.BLUE);
    private static final Component INTANGIBLE_TOOLTIP = Component.translatable("item.intangible").withStyle(ChatFormatting.GRAY);
    public static final MapCodec<ItemStack> MAP_CODEC = MapCodec.recursive(
        "ItemStack",
        codec -> RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Item.CODEC.fieldOf("id").forGetter(ItemStack::getItemHolder),
                    ExtraCodecs.intRange(1, 99).fieldOf("count").orElse(1).forGetter(ItemStack::getCount),
                    DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(stack -> stack.components.asPatch())
                )
                .apply(instance, ItemStack::new)
        )
    );
    public static final Codec<ItemStack> CODEC = Codec.lazyInitialized(MAP_CODEC::codec);
    public static final Codec<ItemStack> SINGLE_ITEM_CODEC = Codec.lazyInitialized(
        () -> RecordCodecBuilder.create(
            instance -> instance.group(
                    Item.CODEC.fieldOf("id").forGetter(ItemStack::getItemHolder),
                    DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(stack -> stack.components.asPatch())
                )
                .apply(instance, (item, components) -> new ItemStack(item, 1, components))
        )
    );
    public static final Codec<ItemStack> STRICT_CODEC = CODEC.validate(ItemStack::validateStrict);
    public static final Codec<ItemStack> STRICT_SINGLE_ITEM_CODEC = SINGLE_ITEM_CODEC.validate(ItemStack::validateStrict);
    public static final Codec<ItemStack> OPTIONAL_CODEC = ExtraCodecs.optionalEmptyMap(CODEC)
        .xmap(optional -> optional.orElse(ItemStack.EMPTY), stack -> stack.isEmpty() ? Optional.empty() : Optional.of(stack));
    public static final Codec<ItemStack> SIMPLE_ITEM_CODEC = Item.CODEC.xmap(ItemStack::new, ItemStack::getItemHolder);
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> OPTIONAL_STREAM_CODEC = createOptionalStreamCodec(DataComponentPatch.STREAM_CODEC);
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> OPTIONAL_UNTRUSTED_STREAM_CODEC = createOptionalStreamCodec(
        DataComponentPatch.DELIMITED_STREAM_CODEC
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> STREAM_CODEC = new StreamCodec<RegistryFriendlyByteBuf, ItemStack>() {
        @Override
        public ItemStack decode(RegistryFriendlyByteBuf buffer) {
            ItemStack itemStack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
            if (itemStack.isEmpty()) {
                throw new DecoderException("Empty ItemStack not allowed");
            } else {
                return itemStack;
            }
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ItemStack value) {
            if (value.isEmpty()) {
                throw new EncoderException("Empty ItemStack not allowed");
            } else {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, value);
            }
        }
    };
    public static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> OPTIONAL_LIST_STREAM_CODEC = OPTIONAL_STREAM_CODEC.apply(
        ByteBufCodecs.collection(NonNullList::createWithCapacity)
    );
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ItemStack EMPTY = new ItemStack((Void)null);
    private static final Component DISABLED_ITEM_TOOLTIP = Component.translatable("item.disabled").withStyle(ChatFormatting.RED);
    private int count;
    private int popTime;
    @Deprecated
    private @Nullable Item item;
    public PatchedDataComponentMap components; // Mili - item over-stack util
    private @Nullable Entity entityRepresentation;

    public static DataResult<ItemStack> validateStrict(ItemStack stack) {
        DataResult<Unit> dataResult = validateComponents(stack.getComponents());
        if (dataResult.isError()) {
            return dataResult.map(unit -> stack);
        } else {
            return stack.getCount() > stack.getMaxStackSize()
                ? DataResult.error(() -> "Item stack with stack size of " + stack.getCount() + " was larger than maximum: " + stack.getMaxStackSize())
                : DataResult.success(stack);
        }
    }

    private static StreamCodec<RegistryFriendlyByteBuf, ItemStack> createOptionalStreamCodec(
        final StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch> codec
    ) {
        return new StreamCodec<RegistryFriendlyByteBuf, ItemStack>() {
            @Override
            public ItemStack decode(RegistryFriendlyByteBuf buffer) {
                int varInt = buffer.readVarInt();
                if (varInt <= 0) {
                    return ItemStack.EMPTY;
                } else {
                    Holder<Item> holder = Item.STREAM_CODEC.decode(buffer);
                    DataComponentPatch dataComponentPatch = codec.decode(buffer);
                    // Mili start - item over-stack util
                    ItemStack itemStack = new ItemStack(holder, varInt, dataComponentPatch);
                    return org.leavesmc.leaves.util.ItemOverstackUtils.decodeMaxStackSize(itemStack);
                    // Mili end - item over-stack util
                }
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, ItemStack value) {
                if (value.isEmpty() || value.getItem() == null) { // CraftBukkit - NPE fix itemstack.getItem()
                    buffer.writeVarInt(0);
                } else {
                    // Mili start - item over-stack util
                    final ItemStack itemStack = org.leavesmc.leaves.util.ItemOverstackUtils.encodeMaxStackSize(value.copy());
                    buffer.writeVarInt(io.papermc.paper.util.sanitizer.ItemComponentSanitizer.sanitizeCount(io.papermc.paper.util.sanitizer.ItemObfuscationSession.currentSession(), itemStack, itemStack.getCount())); // Paper - potentially sanitize count
                    Item.STREAM_CODEC.encode(buffer, itemStack.getItemHolder());
                    // Paper start - adventure; conditionally render translatable components
                    boolean prev = net.minecraft.network.chat.ComponentSerialization.DONT_RENDER_TRANSLATABLES.get();
                    try (final io.papermc.paper.util.SafeAutoClosable ignored = io.papermc.paper.util.sanitizer.ItemObfuscationSession.withContext(c -> c.itemStack(itemStack))) { // pass the itemstack as context to the obfuscation session
                        net.minecraft.network.chat.ComponentSerialization.DONT_RENDER_TRANSLATABLES.set(true);
                    codec.encode(buffer, itemStack.components.asPatch());
                    // Mili end - item over-stack util
                    } finally {
                        net.minecraft.network.chat.ComponentSerialization.DONT_RENDER_TRANSLATABLES.set(prev);
                    }
                    // Paper end - adventure; conditionally render translatable components
                }
            }
        };
    }

    public static StreamCodec<RegistryFriendlyByteBuf, ItemStack> validatedStreamCodec(final StreamCodec<RegistryFriendlyByteBuf, ItemStack> codec) {
        return new StreamCodec<RegistryFriendlyByteBuf, ItemStack>() {
            @Override
            public ItemStack decode(RegistryFriendlyByteBuf buffer) {
                ItemStack itemStack = codec.decode(buffer);
                if (!itemStack.isEmpty()) {
                    RegistryOps<Unit> registryOps = buffer.registryAccess().createSerializationContext(NullOps.INSTANCE);
                    ItemStack.CODEC.encodeStart(registryOps, itemStack).getOrThrow(DecoderException::new);
                }

                return itemStack;
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, ItemStack value) {
                codec.encode(buffer, value);
            }
        };
    }

    public Optional<TooltipComponent> getTooltipImage() {
        return this.getItem().getTooltipImage(this);
    }

    @Override
    public DataComponentMap getComponents() {
        return (DataComponentMap)(!this.isEmpty() ? this.components : DataComponentMap.EMPTY);
    }

    public DataComponentMap getPrototype() {
        return !this.isEmpty() ? this.getItem().components() : DataComponentMap.EMPTY;
    }

    public DataComponentPatch getComponentsPatch() {
        return !this.isEmpty() ? this.components.asPatch() : DataComponentPatch.EMPTY;
    }

    public DataComponentMap immutableComponents() {
        return !this.isEmpty() ? this.components.toImmutableMap() : DataComponentMap.EMPTY;
    }

    public boolean hasNonDefault(DataComponentType<?> component) {
        return !this.isEmpty() && this.components.hasNonDefault(component);
    }

    public ItemStack(ItemLike item) {
        this(item, 1);
    }

    public ItemStack(Holder<Item> tag) {
        this(tag.value(), 1);
    }

    public ItemStack(Holder<Item> tag, int count, DataComponentPatch components) {
        this(tag.value(), count, PatchedDataComponentMap.fromPatch(tag.value().components(), components));
    }

    public ItemStack(Holder<Item> item, int count) {
        this(item.value(), count);
    }

    public ItemStack(ItemLike item, int count) {
        this(item, count, new PatchedDataComponentMap(item.asItem().components()));
    }

    private ItemStack(ItemLike item, int count, PatchedDataComponentMap components) {
        this.item = item.asItem();
        this.count = count;
        this.components = components;
    }

    private ItemStack(@Nullable Void unused) {
        this.item = null;
        this.components = new PatchedDataComponentMap(DataComponentMap.EMPTY);
    }

    public static DataResult<Unit> validateComponents(DataComponentMap components) {
        if (components.has(DataComponents.MAX_DAMAGE) && components.getOrDefault(DataComponents.MAX_STACK_SIZE, 1) > 1) {
            return DataResult.error(() -> "Item cannot be both damageable and stackable");
        } else {
            ItemContainerContents itemContainerContents = components.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);

            for (ItemStack itemStack : itemContainerContents.nonEmptyItems()) {
                int count = itemStack.getCount();
                int maxStackSize = org.leavesmc.leaves.util.ItemOverstackUtils.getItemStackMaxCount(itemStack); // Mili - item over-stack util
                if (count > maxStackSize) {
                    return DataResult.error(() -> "Item stack with count of " + count + " was larger than maximum: " + maxStackSize);
                }
            }

            return DataResult.success(Unit.INSTANCE);
        }
    }

    public boolean isEmpty() {
        return this == EMPTY || this.item == Items.AIR || this.count <= 0;
    }

    public boolean isItemEnabled(FeatureFlagSet enabledFlags) {
        return this.isEmpty() || this.getItem().isEnabled(enabledFlags);
    }

    public ItemStack split(int amount) {
        int min = Math.min(amount, this.getCount());
        ItemStack itemStack = this.copyWithCount(min);
        this.shrink(min);
        return itemStack;
    }

    public ItemStack copyAndClear() {
        if (this.isEmpty()) {
            return EMPTY;
        } else {
            ItemStack itemStack = this.copy();
            this.setCount(0);
            return itemStack;
        }
    }

    public Item getItem() {
        return this.isEmpty() ? Items.AIR : this.item;
    }

    public Holder<Item> getItemHolder() {
        return this.getItem().builtInRegistryHolder();
    }

    public boolean is(TagKey<Item> tag) {
        return this.getItem().builtInRegistryHolder().is(tag);
    }

    public boolean is(Item item) {
        return this.getItem() == item;
    }

    public boolean is(Predicate<Holder<Item>> item) {
        return item.test(this.getItem().builtInRegistryHolder());
    }

    public boolean is(Holder<Item> item) {
        return this.getItem().builtInRegistryHolder() == item;
    }

    public boolean is(HolderSet<Item> item) {
        return item.contains(this.getItemHolder());
    }

    public Stream<TagKey<Item>> getTags() {
        return this.getItem().builtInRegistryHolder().tags();
    }

    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        BlockPos clickedPos = context.getClickedPos();
        if (player != null && !player.getAbilities().mayBuild && !this.canPlaceOnBlockInAdventureMode(new BlockInWorld(context.getLevel(), clickedPos, false))) {
            return InteractionResult.PASS;
        } else {
            Item item = this.getItem();
            // CraftBukkit start - handle all block place event logic here
            DataComponentPatch previousPatch = this.components.asPatch();
            int oldCount = this.getCount();
            ServerLevel serverLevel = (ServerLevel) context.getLevel();
            io.papermc.paper.threadedregions.RegionizedWorldData worldData = serverLevel.getCurrentWorldData(); // Folia - region threading

            if (!(item instanceof BucketItem/* || item instanceof SolidBucketItem*/)) { // if not bucket // Paper - Fix cancelled powdered snow bucket placement
                worldData.captureBlockStates = true; // Folia - region threading
                // special case bonemeal
                if (item == Items.BONE_MEAL) {
                    worldData.captureTreeGeneration = true; // Folia - region threading
                }
            }
            InteractionResult interactionResult;
            // Mili start - fix block place desync due to update suppression
            org.leavesmc.leaves.util.UpdateSuppressionException ue = null;
            try {
                interactionResult = item.useOn(context);
            } catch (org.leavesmc.leaves.util.UpdateSuppressionException ex) {
                interactionResult = InteractionResult.SUCCESS.configurePaper(e -> e.placedBlockAt(clickedPos.immutable()));
                ue = ex;
                ue.provideBlock(context.getLevel(), context.getClickedPos(), serverLevel.getBlockState(context.getClickedPos()).getBlock());
                if (player != null) {
                    ue.providePlayer((net.minecraft.server.level.ServerPlayer) player);
                }
            // Mili end - fix block place desync due to update suppression
            } finally {
                worldData.captureBlockStates = false; // Folia - region threading
            }
            DataComponentPatch newPatch = this.components.asPatch();
            int newCount = this.getCount();
            this.setCount(oldCount);
            this.restorePatch(previousPatch);
            if (interactionResult.consumesAction() && worldData.captureTreeGeneration && !worldData.capturedBlockStates.isEmpty()) { // Folia - region threading
                worldData.captureTreeGeneration = false; // Folia - region threading
                org.bukkit.Location location = org.bukkit.craftbukkit.util.CraftLocation.toBukkit(clickedPos, serverLevel);
                org.bukkit.TreeType treeType = net.minecraft.world.level.block.SaplingBlock.treeTypeRT.get(); // Folia - region threading
                net.minecraft.world.level.block.SaplingBlock.treeTypeRT.set(null); // Folia - region threading
                List<org.bukkit.craftbukkit.block.CraftBlockState> blocks = new java.util.ArrayList<>(worldData.capturedBlockStates.values()); // Folia - region threading
                worldData.capturedBlockStates.clear(); // Folia - region threading
                org.bukkit.event.world.StructureGrowEvent structureEvent = null;
                if (treeType != null) {
                    boolean isBonemeal = this.getItem() == Items.BONE_MEAL;
                    structureEvent = new org.bukkit.event.world.StructureGrowEvent(location, treeType, isBonemeal, (org.bukkit.entity.Player) player.getBukkitEntity(), (List<org.bukkit.block.BlockState>) (List<? extends org.bukkit.block.BlockState>) blocks);
                    org.bukkit.Bukkit.getPluginManager().callEvent(structureEvent);
                }

                org.bukkit.event.block.BlockFertilizeEvent fertilizeEvent = new org.bukkit.event.block.BlockFertilizeEvent(org.bukkit.craftbukkit.block.CraftBlock.at(serverLevel, clickedPos), (org.bukkit.entity.Player) player.getBukkitEntity(), (List<org.bukkit.block.BlockState>) (List<? extends org.bukkit.block.BlockState>) blocks);
                fertilizeEvent.setCancelled(structureEvent != null && structureEvent.isCancelled());
                org.bukkit.Bukkit.getPluginManager().callEvent(fertilizeEvent);

                if (!fertilizeEvent.isCancelled()) {
                    // Change the stack to its new contents if it hasn't been tampered with.
                    if (this.getCount() == oldCount && Objects.equals(this.components.asPatch(), previousPatch)) {
                        this.restorePatch(newPatch);
                        this.setCount(newCount);
                    }
                    for (org.bukkit.craftbukkit.block.CraftBlockState snapshot : blocks) {
                        // SPIGOT-7572 - Move fix for SPIGOT-7248 to CapturedBlockState, to allow bees in bee nest
                        snapshot.place(snapshot.getFlags());
                        serverLevel.checkCapturedTreeStateForObserverNotify(clickedPos, snapshot); // Paper - notify observers even if grow failed
                    }
                    player.awardStat(Stats.ITEM_USED.get(item)); // SPIGOT-7236 - award stat
                }

                SignItem.openSign.set(null); // SPIGOT-6758 - Reset on early return // Folia - region threading
                return interactionResult;
            }
            worldData.captureTreeGeneration = false; // Folia - region threading
            if (player != null && interactionResult instanceof InteractionResult.Success success && success.wasItemInteraction()) {
                InteractionHand hand = context.getHand();
                org.bukkit.event.block.BlockPlaceEvent placeEvent = null;
                List<org.bukkit.block.BlockState> blocks = new java.util.ArrayList<>(worldData.capturedBlockStates.values()); // Folia - region threading
                worldData.capturedBlockStates.clear(); // Folia - region threading
                if (blocks.size() > 1) {
                    placeEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callBlockMultiPlaceEvent(serverLevel, player, hand, blocks, clickedPos);
                } else if (blocks.size() == 1 && item != Items.POWDER_SNOW_BUCKET) { // Paper - Fix cancelled powdered snow bucket placement
                    placeEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callBlockPlaceEvent(serverLevel, player, hand, blocks.getFirst(), clickedPos);
                }

                if (placeEvent != null && (placeEvent.isCancelled() || !placeEvent.canBuild()) && (!(player instanceof org.leavesmc.leaves.bot.ServerBot))) { // Leaves - Fakeplayer skip this check
                    interactionResult = InteractionResult.FAIL; // cancel placement
                    // PAIL: Remove this when MC-99075 fixed
                    player.containerMenu.forceHeldSlot(hand);
                    worldData.capturedTileEntities.clear(); // Paper - Allow chests to be placed with NBT data; clear out block entities as chests and such will pop loot // Folia - region threading
                    // revert back all captured blocks
                    for (org.bukkit.block.BlockState blockstate : blocks) {
                        ((org.bukkit.craftbukkit.block.CraftBlockState) blockstate).revertPlace();
                    }

                    SignItem.openSign.set(null); // SPIGOT-6758 - Reset on early return // Folia - region threading
                } else {
                    // Change the stack to its new contents if it hasn't been tampered with.
                    if (this.getCount() == oldCount && Objects.equals(this.components.asPatch(), previousPatch)) {
                        this.restorePatch(newPatch);
                        this.setCount(newCount);
                    }

                    for (java.util.Map.Entry<BlockPos, net.minecraft.world.level.block.entity.BlockEntity> e : worldData.capturedTileEntities.entrySet()) { // Folia - region threading
                        serverLevel.setBlockEntity(e.getValue());
                    }

                    for (org.bukkit.block.BlockState blockstate : blocks) {
                        int updateFlags = ((org.bukkit.craftbukkit.block.CraftBlockState) blockstate).getFlags();
                        net.minecraft.world.level.block.state.BlockState oldBlock = ((org.bukkit.craftbukkit.block.CraftBlockState) blockstate).getHandle();
                        BlockPos newPos = ((org.bukkit.craftbukkit.block.CraftBlockState) blockstate).getPosition();
                        net.minecraft.world.level.block.state.BlockState block = serverLevel.getBlockState(newPos);

                        if (!(block.getBlock() instanceof net.minecraft.world.level.block.BaseEntityBlock)) { // Containers get placed automatically
                            block.onPlace(serverLevel, newPos, oldBlock, true, context);
                        }

                        serverLevel.notifyAndUpdatePhysics(newPos, null, oldBlock, block, serverLevel.getBlockState(newPos), updateFlags, net.minecraft.world.level.block.Block.UPDATE_LIMIT); // send null chunk as chunk.k() returns false by this point
                    }

                    if (this.item == Items.WITHER_SKELETON_SKULL) { // Special case skulls to allow wither spawns to be cancelled
                        BlockPos bp = clickedPos;
                        if (!serverLevel.getBlockState(clickedPos).canBeReplaced()) {
                            if (!serverLevel.getBlockState(clickedPos).isSolid()) {
                                bp = null;
                            } else {
                                bp = bp.relative(context.getClickedFace());
                            }
                        }
                        if (bp != null) {
                            net.minecraft.world.level.block.entity.BlockEntity te = serverLevel.getBlockEntity(bp);
                            if (te instanceof net.minecraft.world.level.block.entity.SkullBlockEntity) {
                                net.minecraft.world.level.block.WitherSkullBlock.checkSpawn(serverLevel, bp, (net.minecraft.world.level.block.entity.SkullBlockEntity) te);
                            }
                        }
                    }

                    // SPIGOT-4678
                    if (this.item instanceof SignItem && SignItem.openSign.get() != null) { // Folia - region threading
                        try {
                            if (serverLevel.getBlockEntity(SignItem.openSign.get()) instanceof net.minecraft.world.level.block.entity.SignBlockEntity blockEntity) { // Folia - region threading
                                if (serverLevel.getBlockState(SignItem.openSign.get()).getBlock() instanceof net.minecraft.world.level.block.SignBlock signBlock) { // Folia - region threading
                                    signBlock.openTextEdit(player, blockEntity, true, io.papermc.paper.event.player.PlayerOpenSignEvent.Cause.PLACE); // CraftBukkit // Paper - Add PlayerOpenSignEvent
                                }
                            }
                        } finally {
                            SignItem.openSign.set(null);
                        }
                    }

                    // SPIGOT-7315: Moved from BedBlock#setPlacedBy
                    if (placeEvent != null && this.item instanceof BedItem) {
                        BlockPos pos = ((org.bukkit.craftbukkit.block.CraftBlock) placeEvent.getBlock()).getPosition();
                        net.minecraft.world.level.block.state.BlockState state = serverLevel.getBlockState(pos);

                        if (state.getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
                            serverLevel.updateNeighborsAt(pos, net.minecraft.world.level.block.Blocks.AIR);
                            state.updateNeighbourShapes(serverLevel, pos, net.minecraft.world.level.block.Block.UPDATE_ALL);
                        }
                    }

                    // SPIGOT-1288 - play sound stripped from BlockItem
                    if (this.item instanceof BlockItem && success.paperSuccessContext().placedBlockPosition() != null) {
                        // Paper start - Fix spigot sound playing for BlockItem ItemStacks
                        net.minecraft.world.level.block.state.BlockState state = serverLevel.getBlockState(success.paperSuccessContext().placedBlockPosition());
                        net.minecraft.world.level.block.SoundType soundType = state.getSoundType();
                        // Paper end - Fix spigot sound playing for BlockItem ItemStacks
                        serverLevel.playSound(player, clickedPos, soundType.getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS, (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);
                    }

                    player.awardStat(Stats.ITEM_USED.get(item));
                }
            }
            worldData.capturedTileEntities.clear(); // Folia - region threading
            worldData.capturedBlockStates.clear(); // Folia - region threading
            // CraftBukkit end

            if (ue != null) throw ue; // Mili - fix block place desync due to update suppression
            return interactionResult;
        }
    }

    public float getDestroySpeed(BlockState state) {
        return this.getItem().getDestroySpeed(this, state);
    }

    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = this.copy();
        boolean flag = this.getUseDuration(player) <= 0;
        InteractionResult interactionResult = this.getItem().use(level, player, hand);
        return (InteractionResult)(flag && interactionResult instanceof InteractionResult.Success success
            ? success.heldItemTransformedTo(
                success.heldItemTransformedTo() == null
                    ? this.applyAfterUseComponentSideEffects(player, itemStack)
                    : success.heldItemTransformedTo().applyAfterUseComponentSideEffects(player, itemStack)
            )
            : interactionResult);
    }

    public ItemStack finishUsingItem(Level level, LivingEntity livingEntity) {
        ItemStack itemStack = this.copy();
        ItemStack itemStack1 = this.getItem().finishUsingItem(this, level, livingEntity);
        return itemStack1.applyAfterUseComponentSideEffects(livingEntity, itemStack);
    }

    private ItemStack applyAfterUseComponentSideEffects(LivingEntity entity, ItemStack stack) {
        UseRemainder useRemainder = stack.get(DataComponents.USE_REMAINDER);
        UseCooldown useCooldown = stack.get(DataComponents.USE_COOLDOWN);
        int count = stack.getCount();
        ItemStack itemStack = this;
        if (useRemainder != null) {
            itemStack = useRemainder.convertIntoRemainder(this, count, entity.hasInfiniteMaterials(), entity::handleExtraItemsCreatedOnUse);
        }

        if (useCooldown != null) {
            useCooldown.apply(stack, entity);
        }

        return itemStack;
    }

    public int getMaxStackSize() {
        return this.getOrDefault(DataComponents.MAX_STACK_SIZE, 1);
    }

    public boolean isStackable() {
        return this.getMaxStackSize() > 1 && (!this.isDamageableItem() || !this.isDamaged());
    }

    public boolean isDamageableItem() {
        return this.has(DataComponents.MAX_DAMAGE) && !this.has(DataComponents.UNBREAKABLE) && this.has(DataComponents.DAMAGE);
    }

    public boolean isDamaged() {
        return this.isDamageableItem() && this.getDamageValue() > 0;
    }

    public int getDamageValue() {
        return Mth.clamp(this.getOrDefault(DataComponents.DAMAGE, 0), 0, this.getMaxDamage());
    }

    public void setDamageValue(int damage) {
        this.set(DataComponents.DAMAGE, Mth.clamp(damage, 0, this.getMaxDamage()));
    }

    public int getMaxDamage() {
        return this.getOrDefault(DataComponents.MAX_DAMAGE, 0);
    }

    public boolean isBroken() {
        return this.isDamageableItem() && this.getDamageValue() >= this.getMaxDamage();
    }

    public boolean nextDamageWillBreak() {
        return this.isDamageableItem() && this.getDamageValue() >= this.getMaxDamage() - 1;
    }

    public void hurtAndBreak(int damage, ServerLevel level, @Nullable LivingEntity player, Consumer<Item> onBreak) { // Paper - Add EntityDamageItemEvent
        // Paper start - add force boolean overload
        this.hurtAndBreak(damage, level, player, onBreak, false);
    }

    public void hurtAndBreak(int damage, ServerLevel level, @Nullable LivingEntity player, Consumer<Item> onBreak, boolean force) { // Paper - Add EntityDamageItemEvent
        // Paper end
        final int originalDamage = damage; // Paper - Expand PlayerItemDamageEvent
        int i = this.processDurabilityChange(damage, level, player, force); // Paper
        // CraftBukkit start
        if (i > 0 && player instanceof final ServerPlayer serverPlayer) { // Paper - Add EntityDamageItemEvent - limit to positive damage and run for player
            org.bukkit.event.player.PlayerItemDamageEvent event = new org.bukkit.event.player.PlayerItemDamageEvent(serverPlayer.getBukkitEntity(), org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(this), i, originalDamage); // Paper - Add EntityDamageItemEvent
            event.getPlayer().getServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return;
            }

            i = event.getDamage();
            // Paper start - Add EntityDamageItemEvent
        } else if (i > 0 && player != null) {
            io.papermc.paper.event.entity.EntityDamageItemEvent event = new io.papermc.paper.event.entity.EntityDamageItemEvent(player.getBukkitLivingEntity(), org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(this), i);
            if (!event.callEvent()) {
                return;
            }
            i = event.getDamage();
            // Paper end - Add EntityDamageItemEvent
        }
        // CraftBukkit end
        if (i != 0) { // Paper - Add EntityDamageItemEvent - diff on change for above event ifs.
            this.applyDamage(this.getDamageValue() + i, player, onBreak);
        }
    }

    private int processDurabilityChange(int damage, ServerLevel level, @Nullable LivingEntity player) { // Paper - Add EntityDamageItemEvent
        // Paper start - itemstack damage api
        return processDurabilityChange(damage, level, player, false);
    }
    private int processDurabilityChange(int damage, ServerLevel level, @Nullable LivingEntity player, boolean force) {
        // Paper end - itemstack damage api
        if (!this.isDamageableItem()) {
            return 0;
        } else if (player instanceof ServerPlayer && player.hasInfiniteMaterials() && !force) { // Paper - Add EntityDamageItemEvent
            return 0;
        } else {
            return damage > 0 ? EnchantmentHelper.processDurabilityChange(level, this, damage) : damage;
        }
    }

    private void applyDamage(int damage, @Nullable LivingEntity player, Consumer<Item> onBreak) { // Paper - Add EntityDamageItemEvent
        if (player instanceof final ServerPlayer serverPlayer) { // Paper - Add EntityDamageItemEvent
            CriteriaTriggers.ITEM_DURABILITY_CHANGED.trigger(serverPlayer, this, damage); // Paper - Add EntityDamageItemEvent
        }

        this.setDamageValue(damage);
        if (this.isBroken()) {
            Item item = this.getItem();
            // CraftBukkit start - Check for item breaking
            if (this.count == 1 && player instanceof final ServerPlayer serverPlayer) { // Paper - Add EntityDamageItemEvent
                org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerItemBreakEvent(serverPlayer, this); // Paper - Add EntityDamageItemEvent
            }
            // CraftBukkit end
            this.shrink(1);
            onBreak.accept(item);
        }
    }

    public void hurtWithoutBreaking(int damage, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            int i = this.processDurabilityChange(damage, serverPlayer.level(), serverPlayer);
            if (i == 0) {
                return;
            }

            int min = Math.min(this.getDamageValue() + i, this.getMaxDamage() - 1); // Paper - Expand PlayerItemDamageEvent - diff on change as min computation is copied post event.

            // Paper start - Expand PlayerItemDamageEvent
            if (min - this.getDamageValue() > 0) {
                org.bukkit.event.player.PlayerItemDamageEvent event = new org.bukkit.event.player.PlayerItemDamageEvent(
                    serverPlayer.getBukkitEntity(),
                    org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(this),
                    min - this.getDamageValue(),
                    damage
                );
                if (!event.callEvent() || event.getDamage() == 0) {
                    return;
                }

                // Prevent breaking the item in this code path as callers may expect the item to survive
                // (given the method name)
                min = Math.min(this.getDamageValue() + event.getDamage(), this.getMaxDamage() - 1);
            }
            // Paper end - Expand PlayerItemDamageEvent

            this.applyDamage(min, serverPlayer, item -> {});
        }
    }

    public void hurtAndBreak(int amount, LivingEntity entity, InteractionHand hand) {
        this.hurtAndBreak(amount, entity, hand.asEquipmentSlot());
    }

    public void hurtAndBreak(int amount, LivingEntity entity, EquipmentSlot slot) {
        // Paper start - add param to skip infinite mats check
        this.hurtAndBreak(amount, entity, slot, false);
    }
    public void hurtAndBreak(int amount, LivingEntity entity, EquipmentSlot slot, boolean force) {
        // Paper end - add param to skip infinite mats check
        if (entity.level() instanceof ServerLevel serverLevel) {
            this.hurtAndBreak(
                amount, serverLevel, entity, item -> {if (slot != null) entity.onEquippedItemBroken(item, slot); }, force // Paper - Add EntityDamageItemEvent & itemstack damage API - do not process entity related callbacks when damaging from API
            );
        }
    }

    public ItemStack hurtAndConvertOnBreak(int amount, ItemLike item, LivingEntity entity, EquipmentSlot slot) {
        this.hurtAndBreak(amount, entity, slot);
        if (this.isEmpty()) {
            ItemStack itemStack = this.transmuteCopyIgnoreEmpty(item, 1);
            if (itemStack.isDamageableItem()) {
                itemStack.setDamageValue(0);
            }

            return itemStack;
        } else {
            return this;
        }
    }

    public boolean isBarVisible() {
        return this.getItem().isBarVisible(this);
    }

    public int getBarWidth() {
        return this.getItem().getBarWidth(this);
    }

    public int getBarColor() {
        return this.getItem().getBarColor(this);
    }

    public boolean overrideStackedOnOther(Slot slot, ClickAction action, Player player) {
        return this.getItem().overrideStackedOnOther(this, slot, action, player);
    }

    public boolean overrideOtherStackedOnMe(ItemStack stack, Slot slot, ClickAction action, Player player, SlotAccess access) {
        return this.getItem().overrideOtherStackedOnMe(this, stack, slot, action, player, access);
    }

    public boolean hurtEnemy(LivingEntity enemy, LivingEntity attacker) {
        Item item = this.getItem();
        item.hurtEnemy(this, enemy, attacker);
        if (this.has(DataComponents.WEAPON)) {
            if (attacker instanceof Player player) {
                player.awardStat(Stats.ITEM_USED.get(item));
            }

            return true;
        } else {
            return false;
        }
    }

    public void postHurtEnemy(LivingEntity enemy, LivingEntity attacker) {
        this.getItem().postHurtEnemy(this, enemy, attacker);
        Weapon weapon = this.get(DataComponents.WEAPON);
        if (weapon != null) {
            this.hurtAndBreak(weapon.itemDamagePerAttack(), attacker, EquipmentSlot.MAINHAND);
        }
    }

    public void mineBlock(Level level, BlockState state, BlockPos pos, Player player) {
        Item item = this.getItem();
        if (item.mineBlock(this, level, state, pos, player)) {
            player.awardStat(Stats.ITEM_USED.get(item));
        }
    }

    public boolean isCorrectToolForDrops(BlockState state) {
        return this.getItem().isCorrectToolForDrops(this, state);
    }

    public InteractionResult interactLivingEntity(Player player, LivingEntity entity, InteractionHand usedHand) {
        Equippable equippable = this.get(DataComponents.EQUIPPABLE);
        if (equippable != null && equippable.equipOnInteract()) {
            InteractionResult interactionResult = equippable.equipOnTarget(player, entity, this);
            if (interactionResult != InteractionResult.PASS) {
                return interactionResult;
            }
        }

        return this.getItem().interactLivingEntity(this, player, entity, usedHand);
    }

    public ItemStack copy() {
        // Paper start - Perf: Optimize Hoppers
        return this.copy(false);
    }

    public ItemStack copy(final boolean originalItem) {
        if (!originalItem && this.isEmpty()) {
            // Paper end - Perf: Optimize Hoppers
            return EMPTY;
        } else {
            ItemStack itemStack = new ItemStack(originalItem ? this.item : this.getItem(), this.count, this.components.copy()); // Paper - Perf: Optimize Hoppers
            itemStack.setPopTime(this.getPopTime());
            return itemStack;
        }
    }

    public ItemStack copyWithCount(int count) {
        if (this.isEmpty()) {
            return EMPTY;
        } else {
            ItemStack itemStack = this.copy();
            itemStack.setCount(count);
            return itemStack;
        }
    }

    public ItemStack transmuteCopy(ItemLike item) {
        return this.transmuteCopy(item, this.getCount());
    }

    public ItemStack transmuteCopy(ItemLike item, int count) {
        return this.isEmpty() ? EMPTY : this.transmuteCopyIgnoreEmpty(item, count);
    }

    private ItemStack transmuteCopyIgnoreEmpty(ItemLike item, int count) {
        return new ItemStack(item.asItem().builtInRegistryHolder(), count, this.components.asPatch());
    }

    public static boolean matches(ItemStack stack, ItemStack other) {
        return stack == other || stack.getCount() == other.getCount() && isSameItemSameComponents(stack, other);
    }

    @Deprecated
    public static boolean listMatches(List<ItemStack> list, List<ItemStack> other) {
        if (list.size() != other.size()) {
            return false;
        } else {
            for (int i = 0; i < list.size(); i++) {
                if (!matches(list.get(i), other.get(i))) {
                    return false;
                }
            }

            return true;
        }
    }

    public static boolean isSameItem(ItemStack stack, ItemStack other) {
        return stack.is(other.getItem());
    }

    public static boolean isSameItemSameComponents(ItemStack stack, ItemStack other) {
        return stack.is(other.getItem()) && (stack.isEmpty() && other.isEmpty() || Objects.equals(stack.components, other.components));
    }

    public static boolean matchesIgnoringComponents(ItemStack stack, ItemStack other, Predicate<DataComponentType<?>> ignoreType) {
        if (stack == other) {
            return true;
        } else if (stack.getCount() != other.getCount()) {
            return false;
        } else if (!stack.is(other.getItem())) {
            return false;
        } else if (stack.isEmpty() && other.isEmpty()) {
            return true;
        } else if (stack.components.size() != other.components.size()) {
            return false;
        } else {
            for (DataComponentType<?> dataComponentType : stack.components.keySet()) {
                Object object = stack.components.get(dataComponentType);
                Object object1 = other.components.get(dataComponentType);
                if (object == null || object1 == null) {
                    return false;
                }

                if (!Objects.equals(object, object1) && !ignoreType.test(dataComponentType)) {
                    return false;
                }
            }

            return true;
        }
    }

    public static MapCodec<ItemStack> lenientOptionalFieldOf(String fieldName) {
        return CODEC.lenientOptionalFieldOf(fieldName)
            .xmap(optional -> optional.orElse(EMPTY), itemStack -> itemStack.isEmpty() ? Optional.empty() : Optional.of(itemStack));
    }

    public static int hashItemAndComponents(@Nullable ItemStack stack) {
        if (stack != null) {
            int i = 31 + stack.getItem().hashCode();
            return 31 * i + stack.getComponents().hashCode();
        } else {
            return 0;
        }
    }

    @Deprecated
    public static int hashStackList(List<ItemStack> list) {
        int i = 0;

        for (ItemStack itemStack : list) {
            i = i * 31 + hashItemAndComponents(itemStack);
        }

        return i;
    }

    @Override
    public String toString() {
        return this.getCount() + " " + this.getItem();
    }

    public void inventoryTick(Level level, Entity entity, @Nullable EquipmentSlot slot) {
        if (this.popTime > 0) {
            this.popTime--;
        }

        if (level instanceof ServerLevel serverLevel) {
            this.getItem().inventoryTick(this, serverLevel, entity, slot);
        }
    }

    public void onCraftedBy(Player player, int amount) {
        player.awardStat(Stats.ITEM_CRAFTED.get(this.getItem()), amount);
        this.getItem().onCraftedBy(this, player);
    }

    public void onCraftedBySystem(Level level) {
        this.getItem().onCraftedPostProcess(this, level);
    }

    public int getUseDuration(LivingEntity entity) {
        return this.getItem().getUseDuration(this, entity);
    }

    public ItemUseAnimation getUseAnimation() {
        return this.getItem().getUseAnimation(this);
    }

    public void releaseUsing(Level level, LivingEntity livingEntity, int timeLeft) {
        ItemStack itemStack = this.copy();
        if (this.getItem().releaseUsing(this, level, livingEntity, timeLeft)) {
            ItemStack itemStack1 = this.applyAfterUseComponentSideEffects(livingEntity, itemStack);
            if (itemStack1 != this) {
                livingEntity.setItemInHand(livingEntity.getUsedItemHand(), itemStack1);
            }
        }
    }

    public void causeUseVibration(Entity causer, Holder.Reference<GameEvent> event) {
        UseEffects useEffects = this.get(DataComponents.USE_EFFECTS);
        if (useEffects != null && useEffects.interactVibrations()) {
            causer.gameEvent(event);
        }
    }

    // Leaves start - Fakeplayer
    public boolean releaseUsingWithResult(Level level, LivingEntity livingEntity, int timeLeft) {
        ItemStack itemStack = this.copy();
        if (this.getItem().releaseUsing(this, level, livingEntity, timeLeft)) {
            ItemStack itemStack1 = this.applyAfterUseComponentSideEffects(livingEntity, itemStack);
            if (itemStack1 != this) {
                livingEntity.setItemInHand(livingEntity.getUsedItemHand(), itemStack1);
            }
            return true;
        }
        return false;
    }
    // Leaves end - Fakeplayer

    public boolean useOnRelease() {
        return this.getItem().useOnRelease(this);
    }

    // CraftBukkit start
    public void restorePatch(DataComponentPatch datacomponentpatch) {
        this.components.restorePatch(datacomponentpatch);
    }
    // CraftBukkit end

    public <T> @Nullable T set(DataComponentType<T> component, @Nullable T value) {
        if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled && component == DataComponents.ENCHANTMENTS && this.subscriber instanceof ChangeSubscriber.EnchantmentSubscriber<ItemStack> enchantmentSubscriber) enchantmentSubscriber.lithium$notifyAfterEnchantmentChange(this, this.subscriberData); // Leaves - Lithium Sleeping Block Entity
        return this.components.set(component, value);
    }

    public <T> @Nullable T set(TypedDataComponent<T> component) {
        return this.components.set(component);
    }

    public <T> void copyFrom(DataComponentType<T> componentType, DataComponentGetter componentGetter) {
        this.set(componentType, componentGetter.get(componentType));
    }

    public <T, U> @Nullable T update(DataComponentType<T> component, T defaultValue, U updateValue, BiFunction<T, U, T> updater) {
        return this.set(component, updater.apply(this.getOrDefault(component, defaultValue), updateValue));
    }

    public <T> @Nullable T update(DataComponentType<T> component, T defaultValue, UnaryOperator<T> updater) {
        T orDefault = this.getOrDefault(component, defaultValue);
        return this.set(component, updater.apply(orDefault));
    }

    public <T> @Nullable T remove(DataComponentType<? extends T> component) {
        return this.components.remove(component);
    }

    public void applyComponentsAndValidate(DataComponentPatch components) {
        DataComponentPatch patch = this.components.asPatch();
        this.components.applyPatch(components);
        Optional<Error<ItemStack>> optional = validateStrict(this).error();
        if (optional.isPresent()) {
            LOGGER.error("Failed to apply component patch '{}' to item: '{}'", components, optional.get().message());
            this.components.restorePatch(patch);
        }
    }

    public void applyComponents(DataComponentPatch components) {
        this.components.applyPatch(components);
    }

    public void applyComponents(DataComponentMap components) {
        this.components.setAll(components);
    }

    // Paper start - (this is just a good no conflict location)
    public org.bukkit.inventory.ItemStack asBukkitMirror() {
        return org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(this);
    }

    public org.bukkit.inventory.ItemStack asBukkitCopy() {
        return org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(this.copy());
    }

    public static ItemStack fromBukkitCopy(org.bukkit.inventory.ItemStack itemstack) {
        return org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(itemstack);
    }

    private org.bukkit.craftbukkit.inventory.@Nullable CraftItemStack bukkitStack;
    public org.bukkit.inventory.ItemStack getBukkitStack() {
        if (this.bukkitStack == null || this.bukkitStack.handle != this) {
            this.bukkitStack = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(this);
        }
        return this.bukkitStack;
    }
    // Paper end

    public Component getHoverName() {
        Component customName = this.getCustomName();
        return customName != null ? customName : this.getItemName();
    }

    public @Nullable Component getCustomName() {
        Component component = this.get(DataComponents.CUSTOM_NAME);
        if (component != null) {
            return component;
        } else {
            WrittenBookContent writtenBookContent = this.get(DataComponents.WRITTEN_BOOK_CONTENT);
            if (writtenBookContent != null) {
                String string = writtenBookContent.title().raw();
                if (!StringUtil.isBlank(string)) {
                    return Component.literal(string);
                }
            }

            return null;
        }
    }

    public Component getItemName() {
        return this.getItem().getName(this);
    }

    public Component getStyledHoverName() {
        MutableComponent mutableComponent = Component.empty().append(this.getHoverName()).withStyle(this.getRarity().color());
        if (this.has(DataComponents.CUSTOM_NAME)) {
            mutableComponent.withStyle(ChatFormatting.ITALIC);
        }

        return mutableComponent;
    }

    public <T extends TooltipProvider> void addToTooltip(
        DataComponentType<T> component, Item.TooltipContext context, TooltipDisplay tooltipDisplay, Consumer<Component> tooltipAdder, TooltipFlag tooltipFlag
    ) {
        T tooltipProvider = (T)this.get(component);
        if (tooltipProvider != null && tooltipDisplay.shows(component)) {
            tooltipProvider.addToTooltip(context, tooltipAdder, tooltipFlag, this.components);
        }
    }

    public List<Component> getTooltipLines(Item.TooltipContext tooltipContext, @Nullable Player player, TooltipFlag tooltipFlag) {
        TooltipDisplay tooltipDisplay = this.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        if (!tooltipFlag.isCreative() && tooltipDisplay.hideTooltip()) {
            boolean shouldPrintOpWarning = this.getItem().shouldPrintOpWarning(this, player);
            return shouldPrintOpWarning ? OP_NBT_WARNING : List.of();
        } else {
            List<Component> list = Lists.newArrayList();
            list.add(this.getStyledHoverName());
            this.addDetailsToTooltip(tooltipContext, tooltipDisplay, player, tooltipFlag, list::add);
            return list;
        }
    }

    public void addDetailsToTooltip(
        Item.TooltipContext context, TooltipDisplay tooltipDisplay, @Nullable Player player, TooltipFlag tooltipFlag, Consumer<Component> tooltipAdder
    ) {
        this.getItem().appendHoverText(this, context, tooltipDisplay, tooltipAdder, tooltipFlag);
        this.addToTooltip(DataComponents.TROPICAL_FISH_PATTERN, context, tooltipDisplay, tooltipAdder, tooltipFlag);
        this.addToTooltip(DataComponents.INSTRUMENT, context, tooltipDisplay, tooltipAdder, tooltipFlag);
        this.addToTooltip(DataComponents.MAP_ID, context, tooltipDisplay, tooltipAdder, tooltipFlag);
        this.addToTooltip(DataComponents.BEES, context, tooltipDisplay, tooltipAdder, tooltipFlag);
        this.addToTooltip(DataComponents.CONTAINER_LOOT, context, tooltipDisplay, tooltipAdder, tooltipFlag);
        this.addToTooltip(DataComponents.CONTAINER, context, tooltipDisplay, tooltipAdder, tooltipFlag);
        this.addToTooltip(DataComponents.BANNER_PATTERNS, context, tooltipDisplay, tooltipAdder, tooltipFlag);
        this.addToTooltip(DataComponents.POT_DECORATIONS, context, tooltipDisplay, tooltipAdder, tooltipFlag);
        this.addToTooltip(DataComponents.WRITTEN_BOOK_CONTENT, context, tooltipDisplay, tooltipAdder, tooltipFlag);
        this.addToTooltip(DataComponents.CHARGED_PROJECTILES, context, tooltipDisplay, tooltipAdder, tooltipFlag);
        this.addToTooltip(DataComponents.FIREWORKS, context, tooltipDisplay, tooltipAdder, tooltipFlag);
        this.addToTooltip(DataComponents.FIREWORK_EXPLOSION, context, tooltipDisplay, tooltipAdder, tooltipFlag);
        this.addToTooltip(DataComponents.POTION_CONTENTS, context, tooltipDisplay, tooltipAdder, tooltipFlag);
        this.addToTooltip(DataComponents.JUKEBOX_PLAYABLE, context, tooltipDisplay, tooltipAdder, tooltipFlag);
        this.addToTooltip(DataComponents.TRIM, context, tooltipDisplay, tooltipAdder, tooltipFlag);
        this.addToTooltip(DataComponents.STORED_ENCHANTMENTS, context, tooltipDisplay, tooltipAdder, tooltipFlag);
        this.addToTooltip(DataComponents.ENCHANTMENTS, context, tooltipDisplay, tooltipAdder, tooltipFlag);
        this.addToTooltip(DataComponents.DYED_COLOR, context, tooltipDisplay, tooltipAdder, tooltipFlag);
        this.addToTooltip(DataComponents.PROFILE, context, tooltipDisplay, tooltipAdder, tooltipFlag);
        this.addToTooltip(DataComponents.LORE, context, tooltipDisplay, tooltipAdder, tooltipFlag);
        this.addAttributeTooltips(tooltipAdder, tooltipDisplay, player);
        this.addUnitComponentToTooltip(DataComponents.INTANGIBLE_PROJECTILE, INTANGIBLE_TOOLTIP, tooltipDisplay, tooltipAdder);
        this.addUnitComponentToTooltip(DataComponents.UNBREAKABLE, UNBREAKABLE_TOOLTIP, tooltipDisplay, tooltipAdder);
        this.addToTooltip(DataComponents.OMINOUS_BOTTLE_AMPLIFIER, context, tooltipDisplay, tooltipAdder, tooltipFlag);
        this.addToTooltip(DataComponents.SUSPICIOUS_STEW_EFFECTS, context, tooltipDisplay, tooltipAdder, tooltipFlag);
        this.addToTooltip(DataComponents.BLOCK_STATE, context, tooltipDisplay, tooltipAdder, tooltipFlag);
        this.addToTooltip(DataComponents.ENTITY_DATA, context, tooltipDisplay, tooltipAdder, tooltipFlag);
        if ((this.is(Items.SPAWNER) || this.is(Items.TRIAL_SPAWNER)) && tooltipDisplay.shows(DataComponents.BLOCK_ENTITY_DATA)) {
            TypedEntityData<BlockEntityType<?>> typedEntityData = this.get(DataComponents.BLOCK_ENTITY_DATA);
            Spawner.appendHoverText(typedEntityData, tooltipAdder, "SpawnData");
        }

        AdventureModePredicate adventureModePredicate = this.get(DataComponents.CAN_BREAK);
        if (adventureModePredicate != null && tooltipDisplay.shows(DataComponents.CAN_BREAK)) {
            tooltipAdder.accept(CommonComponents.EMPTY);
            tooltipAdder.accept(AdventureModePredicate.CAN_BREAK_HEADER);
            adventureModePredicate.addToTooltip(tooltipAdder);
        }

        AdventureModePredicate adventureModePredicate1 = this.get(DataComponents.CAN_PLACE_ON);
        if (adventureModePredicate1 != null && tooltipDisplay.shows(DataComponents.CAN_PLACE_ON)) {
            tooltipAdder.accept(CommonComponents.EMPTY);
            tooltipAdder.accept(AdventureModePredicate.CAN_PLACE_HEADER);
            adventureModePredicate1.addToTooltip(tooltipAdder);
        }

        if (tooltipFlag.isAdvanced()) {
            if (this.isDamaged() && tooltipDisplay.shows(DataComponents.DAMAGE)) {
                tooltipAdder.accept(Component.translatable("item.durability", this.getMaxDamage() - this.getDamageValue(), this.getMaxDamage()));
            }

            tooltipAdder.accept(Component.literal(BuiltInRegistries.ITEM.getKey(this.getItem()).toString()).withStyle(ChatFormatting.DARK_GRAY));
            int size = this.components.size();
            if (size > 0) {
                tooltipAdder.accept(Component.translatable("item.components", size).withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        if (player != null && !this.getItem().isEnabled(player.level().enabledFeatures())) {
            tooltipAdder.accept(DISABLED_ITEM_TOOLTIP);
        }

        boolean shouldPrintOpWarning = this.getItem().shouldPrintOpWarning(this, player);
        if (shouldPrintOpWarning) {
            OP_NBT_WARNING.forEach(tooltipAdder);
        }
    }

    private void addUnitComponentToTooltip(DataComponentType<?> type, Component message, TooltipDisplay tooltipDisplay, Consumer<Component> tooltipAdder) {
        if (this.has(type) && tooltipDisplay.shows(type)) {
            tooltipAdder.accept(message);
        }
    }

    private void addAttributeTooltips(Consumer<Component> tooltipAdder, TooltipDisplay tooltipDisplay, @Nullable Player player) {
        if (tooltipDisplay.shows(DataComponents.ATTRIBUTE_MODIFIERS)) {
            for (EquipmentSlotGroup equipmentSlotGroup : EquipmentSlotGroup.values()) {
                MutableBoolean mutableBoolean = new MutableBoolean(true);
                this.forEachModifier(
                    equipmentSlotGroup,
                    (holder, attributeModifier, display) -> {
                        if (display != ItemAttributeModifiers.Display.hidden()) {
                            if (mutableBoolean.isTrue()) {
                                tooltipAdder.accept(CommonComponents.EMPTY);
                                tooltipAdder.accept(
                                    Component.translatable("item.modifiers." + equipmentSlotGroup.getSerializedName()).withStyle(ChatFormatting.GRAY)
                                );
                                mutableBoolean.setFalse();
                            }

                            display.apply(tooltipAdder, player, holder, attributeModifier);
                        }
                    }
                );
            }
        }
    }

    public boolean hasFoil() {
        Boolean _boolean = this.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        return _boolean != null ? _boolean : this.getItem().isFoil(this);
    }

    public Rarity getRarity() {
        Rarity rarity = this.getOrDefault(DataComponents.RARITY, Rarity.COMMON);
        if (!this.isEnchanted()) {
            return rarity;
        } else {
            return switch (rarity) {
                case COMMON, UNCOMMON -> Rarity.RARE;
                case RARE -> Rarity.EPIC;
                default -> rarity;
            };
        }
    }

    public boolean isEnchantable() {
        if (!this.has(DataComponents.ENCHANTABLE)) {
            return false;
        } else {
            ItemEnchantments itemEnchantments = this.get(DataComponents.ENCHANTMENTS);
            return itemEnchantments != null && itemEnchantments.isEmpty();
        }
    }

    public void enchant(Holder<Enchantment> enchantment, int level) {
        EnchantmentHelper.updateEnchantments(this, mutable -> mutable.upgrade(enchantment, level));
    }

    public boolean isEnchanted() {
        return !this.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty();
    }

    public ItemEnchantments getEnchantments() {
        return this.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
    }

    public boolean isFramed() {
        return this.entityRepresentation instanceof ItemFrame;
    }

    public void setEntityRepresentation(@Nullable Entity entity) {
        if (!this.isEmpty()) {
            this.entityRepresentation = entity;
        }
    }

    public @Nullable ItemFrame getFrame() {
        return this.entityRepresentation instanceof ItemFrame ? (ItemFrame)this.getEntityRepresentation() : null;
    }

    public @Nullable Entity getEntityRepresentation() {
        return !this.isEmpty() ? this.entityRepresentation : null;
    }

    public void forEachModifier(EquipmentSlotGroup slot, TriConsumer<Holder<Attribute>, AttributeModifier, ItemAttributeModifiers.Display> action) {
        ItemAttributeModifiers itemAttributeModifiers = this.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        itemAttributeModifiers.forEach(slot, action);
        EnchantmentHelper.forEachModifier(
            this, slot, (holder, attributeModifier) -> action.accept(holder, attributeModifier, ItemAttributeModifiers.Display.attributeModifiers())
        );
    }

    public void forEachModifier(EquipmentSlot slot, BiConsumer<Holder<Attribute>, AttributeModifier> action) {
        ItemAttributeModifiers itemAttributeModifiers = this.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        itemAttributeModifiers.forEach(slot, action);
        EnchantmentHelper.forEachModifier(this, slot, action);
    }

    // CraftBukkit start
    @Deprecated
    public void setItem(Item item) {
        this.bukkitStack = null; // Paper
        this.item = item;
        // Paper start - change base component prototype
        final DataComponentPatch patch = this.getComponentsPatch();
        this.components = new PatchedDataComponentMap(this.item.components());
        this.applyComponents(patch);
        // Paper end - change base component prototype
    }
    // CraftBukkit end

    public Component getDisplayName() {
        MutableComponent mutableComponent = Component.empty().append(this.getHoverName());
        if (this.has(DataComponents.CUSTOM_NAME)) {
            mutableComponent.withStyle(ChatFormatting.ITALIC);
        }

        MutableComponent mutableComponent1 = ComponentUtils.wrapInSquareBrackets(mutableComponent);
        if (!this.isEmpty()) {
            mutableComponent1.withStyle(this.getRarity().color()).withStyle(style -> style.withHoverEvent(new HoverEvent.ShowItem(this)));
        }

        return mutableComponent1;
    }

    public SwingAnimation getSwingAnimation() {
        return this.getOrDefault(DataComponents.SWING_ANIMATION, SwingAnimation.DEFAULT);
    }

    public boolean canPlaceOnBlockInAdventureMode(BlockInWorld block) {
        AdventureModePredicate adventureModePredicate = this.get(DataComponents.CAN_PLACE_ON);
        return adventureModePredicate != null && adventureModePredicate.test(block);
    }

    public boolean canBreakBlockInAdventureMode(BlockInWorld block) {
        AdventureModePredicate adventureModePredicate = this.get(DataComponents.CAN_BREAK);
        return adventureModePredicate != null && adventureModePredicate.test(block);
    }

    public int getPopTime() {
        return this.popTime;
    }

    public void setPopTime(int popTime) {
        this.popTime = popTime;
    }

    public int getCount() {
        return this.isEmpty() ? 0 : this.count;
    }

    public void setCount(int count) {
        // Leaves start - Lithium Sleeping Block Entity
        if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled && count != this.count) {
            if (this.subscriber instanceof ChangeSubscriber.CountChangeSubscriber<ItemStack> countChangeSubscriber) {
                countChangeSubscriber.lithium$notifyCount(this, this.subscriberData, count);
            }

            if (count == 0) {
                this.components.lithium$unsubscribe(this);

                if (this.subscriber != null) {
                    this.subscriber.lithium$forceUnsubscribe(this, this.subscriberData);
                    this.subscriber = null;
                    this.subscriberData = 0;
                }
            }
        }
        // Leaves end - Lithium Sleeping Block Entity
        this.count = count;
    }

    public void limitSize(int maxSize) {
        if (!this.isEmpty() && this.getCount() > maxSize) {
            this.setCount(maxSize);
        }
    }

    public void grow(int increment) {
        this.setCount(this.getCount() + increment);
    }

    public void shrink(int decrement) {
        this.grow(-decrement);
    }

    public void consume(int amount, @Nullable LivingEntity entity) {
        if ((entity == null || !entity.hasInfiniteMaterials()) && this != ItemStack.EMPTY) { // CraftBukkit
            this.shrink(amount);
        }
    }

    public ItemStack consumeAndReturn(int amount, @Nullable LivingEntity entity) {
        ItemStack itemStack = this.copyWithCount(amount);
        this.consume(amount, entity);
        return itemStack;
    }

    public void onUseTick(Level level, LivingEntity livingEntity, int remainingUseDuration) {
        Consumable consumable = this.get(DataComponents.CONSUMABLE);
        if (consumable != null && consumable.shouldEmitParticlesAndSounds(remainingUseDuration)) {
            consumable.emitParticlesAndSounds(livingEntity.getRandom(), livingEntity, this, 5);
        }

        KineticWeapon kineticWeapon = this.get(DataComponents.KINETIC_WEAPON);
        if (kineticWeapon != null && !level.isClientSide()) {
            kineticWeapon.damageEntities(this, remainingUseDuration, livingEntity, livingEntity.getUsedItemHand().asEquipmentSlot());
        } else {
            this.getItem().onUseTick(level, livingEntity, this, remainingUseDuration);
        }
    }

    public void onDestroyed(ItemEntity itemEntity) {
        this.getItem().onDestroyed(itemEntity);
    }

    public boolean canBeHurtBy(DamageSource damageSource) {
        DamageResistant damageResistant = this.get(DataComponents.DAMAGE_RESISTANT);
        return damageResistant == null || !damageResistant.isResistantTo(damageSource);
    }

    public boolean isValidRepairItem(ItemStack item) {
        Repairable repairable = this.get(DataComponents.REPAIRABLE);
        return repairable != null && repairable.isValidRepairItem(item);
    }

    public boolean canDestroyBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return this.getItem().canDestroyBlock(this, state, level, pos, player);
    }

    public DamageSource getDamageSource(LivingEntity attacker, Supplier<DamageSource> fallback) {
        return Optional.ofNullable(this.get(DataComponents.DAMAGE_TYPE))
            .flatMap(eitherHolder -> eitherHolder.unwrap(attacker.registryAccess()))
            .map(holder -> new DamageSource((Holder<DamageType>)holder, attacker))
            .or(() -> Optional.ofNullable(this.getItem().getItemDamageSource(attacker)))
            .orElseGet(fallback);
    }

    // Leaves start - Lithium Sleeping Block Entity
    private ChangeSubscriber<ItemStack> subscriber;
    private int subscriberData;

    @Override
    public void lithium$subscribe(ChangeSubscriber<ItemStack> subscriber, int subscriberData) {
        if (this.isEmpty()) {
            throw new IllegalStateException("Cannot subscribe to an empty ItemStack!");
        }

        if (this.subscriber == null) {
            this.startTrackingChanges();
        }
        this.subscriber = ChangeSubscriber.combine(this.subscriber, this.subscriberData, subscriber, subscriberData);
        if (this.subscriber instanceof ChangeSubscriber.Multi<?>) {
            this.subscriberData = 0;
        } else {
            this.subscriberData = subscriberData;
        }
    }

    @Override
    public int lithium$unsubscribe(ChangeSubscriber<ItemStack> subscriber) {
        if (this.isEmpty()) {
            throw new IllegalStateException("Cannot unsubscribe from an empty ItemStack!");
        }

        int retval = ChangeSubscriber.dataOf(this.subscriber, subscriber, this.subscriberData);
        this.subscriberData = ChangeSubscriber.dataWithout(this.subscriber, subscriber, this.subscriberData);
        this.subscriber = ChangeSubscriber.without(this.subscriber, subscriber);

        if (this.subscriber == null) {
            this.components.lithium$unsubscribe(this);
        }
        return retval;
    }

    @Override
    public void lithium$unsubscribeWithData(ChangeSubscriber<ItemStack> subscriber, int subscriberData) {
        if (this.isEmpty()) {
            throw new IllegalStateException("Cannot unsubscribe from an empty ItemStack!");
        }

        this.subscriberData = ChangeSubscriber.dataWithout(this.subscriber, subscriber, this.subscriberData, subscriberData, true);
        this.subscriber = ChangeSubscriber.without(this.subscriber, subscriber, subscriberData, true);

        if (this.subscriber == null) {
            this.components.lithium$unsubscribe(this);
        }
    }

    @Override
    public boolean lithium$isSubscribedWithData(ChangeSubscriber<ItemStack> subscriber, int subscriberData) {
        if (this.isEmpty()) {
            throw new IllegalStateException("Cannot be subscribed to an empty ItemStack!");
        }

        return ChangeSubscriber.containsSubscriber(this.subscriber, this.subscriberData, subscriber, subscriberData);
    }

    @Override
    public void lithium$forceUnsubscribe(PatchedDataComponentMap publisher, int subscriberData) {
        if (publisher != this.components) {
            throw new IllegalStateException("Invalid publisher, expected " + this.components + " but got " + publisher);
        }
        this.subscriber.lithium$forceUnsubscribe(this, this.subscriberData);
        this.subscriber = null;
        this.subscriberData = 0;
    }

    private void startTrackingChanges() {
        this.components.lithium$subscribe(this, 0);
    }

    @Override
    public void lithium$notify(PatchedDataComponentMap publisher, int subscriberData) {
        if (publisher != this.components) {
            throw new IllegalStateException("Invalid publisher, expected " + this.components + " but got " + publisher);
        }

        if (this.subscriber != null) {
            this.subscriber.lithium$notify(this, this.subscriberData);
        }
    }
    // Leaves end - Lithium Sleeping Block Entity
}
