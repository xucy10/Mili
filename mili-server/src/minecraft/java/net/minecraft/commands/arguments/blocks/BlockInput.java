package net.minecraft.commands.arguments.blocks;

import com.mojang.logging.LogUtils;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class BlockInput implements Predicate<BlockInWorld> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final BlockState state;
    private final Set<Property<?>> properties;
    public final @Nullable CompoundTag tag;

    public BlockInput(BlockState state, Set<Property<?>> properties, @Nullable CompoundTag tag) {
        this.state = state;
        this.properties = properties;
        this.tag = tag;
    }

    public BlockState getState() {
        return this.state;
    }

    public Set<Property<?>> getDefinedProperties() {
        return this.properties;
    }

    @Override
    public boolean test(BlockInWorld block) {
        BlockState state = block.getState();
        if (!state.is(this.state.getBlock())) {
            return false;
        } else {
            for (Property<?> property : this.properties) {
                if (state.getValue(property) != this.state.getValue(property)) {
                    return false;
                }
            }

            if (this.tag == null) {
                return true;
            } else {
                BlockEntity entity = block.getEntity();
                return entity != null && NbtUtils.compareNbt(this.tag, entity.saveWithFullMetadata(block.getLevel().registryAccess()), true);
            }
        }
    }

    public boolean test(ServerLevel level, BlockPos pos) {
        return this.test(new BlockInWorld(level, pos, false));
    }

    public boolean place(ServerLevel level, BlockPos pos, @Block.UpdateFlags int flags) {
        BlockState blockState = (flags & Block.UPDATE_KNOWN_SHAPE) != 0 ? this.state : Block.updateFromNeighbourShapes(this.state, level, pos);
        if (blockState.isAir()) {
            blockState = this.state;
        }

        blockState = this.overwriteWithDefinedProperties(blockState);
        boolean flag = false;
        if (level.setBlock(pos, blockState, flags)) {
            flag = true;
        }

        if (this.tag != null) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) {
                try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(LOGGER)) {
                    HolderLookup.Provider provider = level.registryAccess();
                    ProblemReporter problemReporter = scopedCollector.forChild(blockEntity.problemPath());
                    TagValueOutput tagValueOutput = TagValueOutput.createWithContext(problemReporter.forChild(() -> "(before)"), provider);
                    blockEntity.saveWithoutMetadata(tagValueOutput);
                    CompoundTag compoundTag = tagValueOutput.buildResult();
                    blockEntity.loadWithComponents(TagValueInput.create(scopedCollector, provider, this.tag));
                    TagValueOutput tagValueOutput1 = TagValueOutput.createWithContext(problemReporter.forChild(() -> "(after)"), provider);
                    blockEntity.saveWithoutMetadata(tagValueOutput1);
                    CompoundTag compoundTag1 = tagValueOutput1.buildResult();
                    if (!compoundTag1.equals(compoundTag)) {
                        flag = true;
                        blockEntity.setChanged();
                        level.getChunkSource().blockChanged(pos);
                    }
                }
            }
        }

        return flag;
    }

    private BlockState overwriteWithDefinedProperties(BlockState state) {
        if (state == this.state) {
            return state;
        } else {
            for (Property<?> property : this.properties) {
                state = copyProperty(state, this.state, property);
            }

            return state;
        }
    }

    private static <T extends Comparable<T>> BlockState copyProperty(BlockState source, BlockState target, Property<T> property) {
        return source.trySetValue(property, target.getValue(property));
    }
}
