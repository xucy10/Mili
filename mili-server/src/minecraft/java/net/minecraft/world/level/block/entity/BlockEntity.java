package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.CrashReportCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.debug.DebugValueSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
// Leaves start - Lithium Sleeping Block Entity
import net.minecraft.core.Direction;
import org.leavesmc.leaves.lithium.common.block.entity.inventory_comparator_tracking.ComparatorTracker;
import org.leavesmc.leaves.lithium.common.block.entity.inventory_comparator_tracking.ComparatorTracking;
import org.leavesmc.leaves.lithium.common.block.entity.inventory_change_tracking.InventoryChangeTracker;
import org.leavesmc.leaves.lithium.common.block.entity.SetBlockStateHandlingBlockEntity;
import org.leavesmc.leaves.lithium.common.block.entity.SetChangedHandlingBlockEntity;
// Leaves end - Lithium Sleeping Block Entity

public abstract class BlockEntity implements DebugValueSource, ComparatorTracker, SetBlockStateHandlingBlockEntity, SetChangedHandlingBlockEntity { // Leaves - Lithium Sleeping Block Entity
    //static final ThreadLocal<Boolean> IGNORE_TILE_UPDATES = ThreadLocal.withInitial(() -> Boolean.FALSE);; // Paper - Perf: Optimize Hoppers // Folia - region threading // Luminol - vanilla hopper
    // CraftBukkit start - data containers
    private static final org.bukkit.craftbukkit.persistence.CraftPersistentDataTypeRegistry DATA_TYPE_REGISTRY = new org.bukkit.craftbukkit.persistence.CraftPersistentDataTypeRegistry();
    public final org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer persistentDataContainer;
    // CraftBukkit end
    private static final Codec<BlockEntityType<?>> TYPE_CODEC = BuiltInRegistries.BLOCK_ENTITY_TYPE.byNameCodec();
    private static final Logger LOGGER = LogUtils.getLogger();
    private final BlockEntityType<?> type;
    protected @Nullable Level level;
    protected final BlockPos worldPosition;
    protected boolean remove;
    private BlockState blockState;
    private DataComponentMap components = DataComponentMap.EMPTY;

    // Folia start - region ticking
    public void updateTicks(final long fromTickOffset, final long fromRedstoneTimeOffset) {

    }
    // Folia end - region ticking

    public BlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        this.type = type;
        this.worldPosition = pos.immutable();
        this.validateBlockState(blockState);
        this.blockState = blockState;
        this.persistentDataContainer = new org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer(DATA_TYPE_REGISTRY); // Paper - always init
        this.hasComparators = UNKNOWN; // Leaves - Lithium Sleeping Block Entity
    }

    private void validateBlockState(BlockState state) {
        if (!this.isValidBlockState(state)) {
            throw new IllegalStateException("Invalid block entity " + this.getNameForReporting() + " state at " + this.worldPosition + ", got " + state);
        }
    }

    public boolean isValidBlockState(BlockState state) {
        return this.type.isValid(state);
    }

    public static BlockPos getPosFromTag(ChunkPos chunkPos, CompoundTag tag) {
        int intOr = tag.getIntOr("x", 0);
        int intOr1 = tag.getIntOr("y", 0);
        int intOr2 = tag.getIntOr("z", 0);
        if (chunkPos != null) { // Paper - allow reading non-validated pos from tag - used to parse block entities on items
        int sectionPosCoord = SectionPos.blockToSectionCoord(intOr);
        int sectionPosCoord1 = SectionPos.blockToSectionCoord(intOr2);
        if (sectionPosCoord != chunkPos.x || sectionPosCoord1 != chunkPos.z) {
            LOGGER.warn("Block entity {} found in a wrong chunk, expected position from chunk {}", tag, chunkPos);
            intOr = chunkPos.getBlockX(SectionPos.sectionRelative(intOr));
            intOr2 = chunkPos.getBlockZ(SectionPos.sectionRelative(intOr2));
        }
        } // Paper - allow reading non-validated pos from tag - used to parse block entities on items

        return new BlockPos(intOr, intOr1, intOr2);
    }

    public @Nullable Level getLevel() {
        return this.level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public boolean hasLevel() {
        return this.level != null;
    }

    protected void loadAdditional(ValueInput input) {
        // Paper start - read persistent data container
        this.persistentDataContainer.clear(); // Paper - clear instead of init

        input.read("PublicBukkitValues", CompoundTag.CODEC)
            .ifPresent(this.persistentDataContainer::putAll);
        // Paper end - read persistent data container
    }

    public final void loadWithComponents(ValueInput input) {
        this.loadAdditional(input);
        this.components = input.read("components", DataComponentMap.CODEC).orElse(DataComponentMap.EMPTY);
    }

    public final void loadCustomOnly(ValueInput input) {
        this.loadAdditional(input);
    }

    protected void saveAdditional(ValueOutput output) {
    }

    public final CompoundTag saveWithFullMetadata(HolderLookup.Provider registries) {
        CompoundTag var4;
        try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
            TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, registries);
            this.saveWithFullMetadata(tagValueOutput);
            var4 = tagValueOutput.buildResult();
        }

        return var4;
    }

    public void saveWithFullMetadata(ValueOutput output) {
        this.saveWithoutMetadata(output);
        this.saveMetadata(output);
    }

    public void saveWithId(ValueOutput output) {
        this.saveWithoutMetadata(output);
        this.saveId(output);
    }

    public final CompoundTag saveWithoutMetadata(HolderLookup.Provider registries) {
        CompoundTag var4;
        try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
            TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, registries);
            this.saveWithoutMetadata(tagValueOutput);
            var4 = tagValueOutput.buildResult();
        }

        return var4;
    }

    public void saveWithoutMetadata(ValueOutput output) {
        this.saveAdditional(output);
        output.store("components", DataComponentMap.CODEC, this.components);
        // CraftBukkit start - store container
        if (!this.persistentDataContainer.isEmpty()) {
            output.store("PublicBukkitValues", CompoundTag.CODEC, this.persistentDataContainer.toTagCompound());
        }
        // CraftBukkit end
    }

    public final CompoundTag saveCustomOnly(HolderLookup.Provider registries) {
        CompoundTag var4;
        try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
            TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, registries);
            this.saveCustomOnly(tagValueOutput);
            var4 = tagValueOutput.buildResult();
        }

        return var4;
    }

    public void saveCustomOnly(ValueOutput output) {
        this.saveAdditional(output);
        // Paper start - store PDC here as well
        if (!this.persistentDataContainer.isEmpty()) {
            output.store("PublicBukkitValues", CompoundTag.CODEC, this.persistentDataContainer.toTagCompound());
        }
        // Paper end
    }

    public void saveId(ValueOutput output) {
        addEntityType(output, this.getType());
    }

    public static void addEntityType(ValueOutput output, BlockEntityType<?> entityType) {
        output.store("id", TYPE_CODEC, entityType);
    }

    private void saveMetadata(ValueOutput output) {
        this.saveId(output);
        output.putInt("x", this.worldPosition.getX());
        output.putInt("y", this.worldPosition.getY());
        output.putInt("z", this.worldPosition.getZ());
    }

    public static @Nullable BlockEntity loadStatic(BlockPos pos, BlockState state, CompoundTag tag, HolderLookup.Provider registries) {
        BlockEntityType<?> blockEntityType = tag.read("id", TYPE_CODEC).orElse(null);
        if (blockEntityType == null) {
            LOGGER.error("Skipping block entity with invalid type: {}", tag.get("id"));
            return null;
        } else {
            BlockEntity blockEntity;
            try {
                blockEntity = blockEntityType.create(pos, state);
            } catch (Throwable var12) {
                LOGGER.error("Failed to create block entity {} for block {} at position {} ", blockEntityType, pos, state, var12);
                return null;
            }

            try {
                BlockEntity var7;
                try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(blockEntity.problemPath(), LOGGER)) {
                    blockEntity.loadWithComponents(TagValueInput.create(scopedCollector, registries, tag));
                    var7 = blockEntity;
                }

                return var7;
            } catch (Throwable var11) {
                LOGGER.error("Failed to load data for block entity {} for block {} at position {}", blockEntityType, pos, state, var11);
                return null;
            }
        }
    }

    public void setChanged() {
        if (this.level != null) {
            //if (IGNORE_TILE_UPDATES.get().booleanValue()) return; // Paper - Perf: Optimize Hoppers // Folia - region threading // Luminol - vanilla hopper
            setChanged(this.level, this.worldPosition, this.blockState);
            if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled) lithium$handleSetChanged(); // Leaves - Lithium Sleeping Block Entity
        }
    }

    protected static void setChanged(Level level, BlockPos pos, BlockState state) {
        level.blockEntityChanged(pos);
        if (!state.isAir()) {
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
    }

    public BlockPos getBlockPos() {
        return this.worldPosition;
    }

    public BlockState getBlockState() {
        return this.blockState;
    }

    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return null;
    }

    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return new CompoundTag();
    }

    public boolean isRemoved() {
        return this.remove;
    }

    public void setRemoved() {
        this.hasComparators = UNKNOWN; // Leaves - Lithium Sleeping Block Entity
        this.remove = true;
        if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled && this.level != null && !this.level.isClientSide() && this instanceof InventoryChangeTracker inventoryChangeTracker) inventoryChangeTracker.lithium$emitRemoved(); // Leaves - Lithium Sleeping Block Entity
    }

    public void clearRemoved() {
        this.remove = false;
    }

    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this instanceof Container container && this.level != null) {
            Containers.dropContents(this.level, pos, container);
        }
    }

    public boolean triggerEvent(int id, int type) {
        return false;
    }

    public void fillCrashReportCategory(CrashReportCategory category) {
        category.setDetail("Name", this::getNameForReporting);
        category.setDetail("Cached block", this.getBlockState()::toString);
        if (this.level == null) {
            category.setDetail("Block location", () -> this.worldPosition + " (world missing)");
        } else {
            category.setDetail("Actual block", this.level.getBlockState(this.worldPosition)::toString);
            CrashReportCategory.populateBlockLocationDetails(category, this.level, this.worldPosition);
        }
    }

    public String getNameForReporting() {
        return BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(this.getType()) + " // " + this.getClass().getCanonicalName();
    }

    public BlockEntityType<?> getType() {
        return this.type;
    }

    @Deprecated
    public void setBlockState(BlockState blockState) {
        this.validateBlockState(blockState);
        this.blockState = blockState;
        if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled) this.lithium$handleSetBlockState(); // Leaves - Lithium Sleeping Block Entity
    }

    protected void applyImplicitComponents(DataComponentGetter componentGetter) {
    }

    public final void applyComponentsFromItemStack(ItemStack stack) {
        this.applyComponents(stack.getPrototype(), stack.getComponentsPatch());
    }

    public final void applyComponents(DataComponentMap components, DataComponentPatch patch) {
        // CraftBukkit start
        this.applyComponentsSet(components, patch);
    }

    public final Set<DataComponentType<?>> applyComponentsSet(DataComponentMap components, DataComponentPatch patch) {
        // CraftBukkit end
        final Set<DataComponentType<?>> set = new HashSet<>();
        set.add(DataComponents.BLOCK_ENTITY_DATA);
        set.add(DataComponents.BLOCK_STATE);
        final DataComponentMap dataComponentMap = PatchedDataComponentMap.fromPatch(components, patch);
        this.applyImplicitComponents(new DataComponentGetter() {
            @Override
            public <T> @Nullable T get(DataComponentType<? extends T> component) {
                set.add(component);
                return dataComponentMap.get(component);
            }

            @Override
            public <T> T getOrDefault(DataComponentType<? extends T> component, T defaultValue) {
                set.add(component);
                return dataComponentMap.getOrDefault(component, defaultValue);
            }
        });
        DataComponentPatch dataComponentPatch = patch.forget(set::contains);
        this.components = dataComponentPatch.split().added();
        // CraftBukkit start
        set.remove(DataComponents.BLOCK_ENTITY_DATA); // Remove as never actually added by applyImplicitComponents
        return set;
        // CraftBukkit end
    }

    protected void collectImplicitComponents(DataComponentMap.Builder components) {
    }

    @Deprecated
    public void removeComponentsFromTag(ValueOutput output) {
    }

    public final DataComponentMap collectComponents() {
        DataComponentMap.Builder builder = DataComponentMap.builder();
        builder.addAll(this.components);
        this.collectImplicitComponents(builder);
        return builder.build();
    }

    public DataComponentMap components() {
        return this.components;
    }

    public void setComponents(DataComponentMap components) {
        this.components = components;
    }

    public static @Nullable Component parseCustomNameSafe(ValueInput input, String customName) {
        return input.read(customName, ComponentSerialization.CODEC).orElse(null);
    }

    public ProblemReporter.PathElement problemPath() {
        return new BlockEntity.BlockEntityPathElement(this);
    }

    @Override
    public void registerDebugValues(ServerLevel level, DebugValueSource.Registration registrar) {
    }

    // CraftBukkit start - add method
    public org.bukkit.inventory.@Nullable InventoryHolder getOwner() {
        return getOwner(true);
    }

    public org.bukkit.inventory.@Nullable InventoryHolder getOwner(boolean useSnapshot) {
        if (this.level == null) return null;
        org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.at(this.level, this.worldPosition);
        org.bukkit.block.BlockState state = block.getState(useSnapshot); // Paper
        return state instanceof final org.bukkit.inventory.InventoryHolder inventoryHolder ? inventoryHolder : null;
    }
    // CraftBukkit end

    // Paper start - Sanitize sent data
    public CompoundTag sanitizeSentNbt(CompoundTag tag) {
        tag.remove("PublicBukkitValues");

        return tag;
    }
    // Paper end - Sanitize sent data

    record BlockEntityPathElement(BlockEntity blockEntity) implements ProblemReporter.PathElement {
        @Override
        public String get() {
            return this.blockEntity.getNameForReporting() + "@" + this.blockEntity.getBlockPos();
        }
    }

    // Leaves start - Lithium Sleeping Block Entity
    private static final byte UNKNOWN = (byte) -1;
    private static final byte COMPARATOR_PRESENT = (byte) 1;
    private static final byte COMPARATOR_ABSENT = (byte) 0;

    byte hasComparators;

    @Override
    public void lithium$onComparatorAdded(Direction direction, int offset) {
        byte hasComparators = this.hasComparators;
        if (direction.getAxis() != Direction.Axis.Y && hasComparators != COMPARATOR_PRESENT && offset >= 1 && offset <= 2) {
            this.hasComparators = COMPARATOR_PRESENT;

            if (this instanceof InventoryChangeTracker inventoryChangeTracker) {
                inventoryChangeTracker.lithium$emitFirstComparatorAdded();
            }
        }
    }

    @Override
    public boolean lithium$hasAnyComparatorNearby() {
        if (this.hasComparators == UNKNOWN) {
            this.hasComparators = ComparatorTracking.findNearbyComparators(this.level, this.worldPosition) ? COMPARATOR_PRESENT : COMPARATOR_ABSENT;
        }
        return this.hasComparators == COMPARATOR_PRESENT;
    }
    // Leaves end - Lithium Sleeping Block Entity
}
