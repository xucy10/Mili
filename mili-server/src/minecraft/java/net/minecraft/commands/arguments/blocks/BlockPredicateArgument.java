package net.minecraft.commands.arguments.blocks;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

public class BlockPredicateArgument implements ArgumentType<BlockPredicateArgument.Result> {
    private static final Collection<String> EXAMPLES = Arrays.asList("stone", "minecraft:stone", "stone[foo=bar]", "#stone", "#stone[foo=bar]{baz=nbt}");
    private final HolderLookup<Block> blocks;

    public BlockPredicateArgument(CommandBuildContext context) {
        this.blocks = context.lookupOrThrow(Registries.BLOCK);
    }

    public static BlockPredicateArgument blockPredicate(CommandBuildContext context) {
        return new BlockPredicateArgument(context);
    }

    @Override
    public BlockPredicateArgument.Result parse(StringReader reader) throws CommandSyntaxException {
        return parse(this.blocks, reader);
    }

    public static BlockPredicateArgument.Result parse(HolderLookup<Block> lookup, StringReader reader) throws CommandSyntaxException {
        return BlockStateParser.parseForTesting(lookup, reader, true)
            .map(
                blockResult -> new BlockPredicateArgument.BlockPredicate(blockResult.blockState(), blockResult.properties().keySet(), blockResult.nbt()),
                tagResult -> new BlockPredicateArgument.TagPredicate(tagResult.tag(), tagResult.vagueProperties(), tagResult.nbt())
            );
    }

    public static Predicate<BlockInWorld> getBlockPredicate(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return context.getArgument(name, BlockPredicateArgument.Result.class);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return BlockStateParser.fillSuggestions(this.blocks, builder, true, true);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    static class BlockPredicate implements BlockPredicateArgument.Result {
        private final BlockState state;
        private final Set<Property<?>> properties;
        private final @Nullable CompoundTag nbt;

        public BlockPredicate(BlockState state, Set<Property<?>> properties, @Nullable CompoundTag nbt) {
            this.state = state;
            this.properties = properties;
            this.nbt = nbt;
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

                if (this.nbt == null) {
                    return true;
                } else {
                    BlockEntity entity = block.getEntity();
                    return entity != null && NbtUtils.compareNbt(this.nbt, entity.saveWithFullMetadata(block.getLevel().registryAccess()), true);
                }
            }
        }

        @Override
        public boolean requiresNbt() {
            return this.nbt != null;
        }
    }

    public interface Result extends Predicate<BlockInWorld> {
        boolean requiresNbt();
    }

    static class TagPredicate implements BlockPredicateArgument.Result {
        private final HolderSet<Block> tag;
        private final @Nullable CompoundTag nbt;
        private final Map<String, String> vagueProperties;

        TagPredicate(HolderSet<Block> tag, Map<String, String> vagueProperties, @Nullable CompoundTag nbt) {
            this.tag = tag;
            this.vagueProperties = vagueProperties;
            this.nbt = nbt;
        }

        @Override
        public boolean test(BlockInWorld block) {
            BlockState state = block.getState();
            if (!state.is(this.tag)) {
                return false;
            } else {
                for (Entry<String, String> entry : this.vagueProperties.entrySet()) {
                    Property<?> property = state.getBlock().getStateDefinition().getProperty(entry.getKey());
                    if (property == null) {
                        return false;
                    }

                    Comparable<?> comparable = (Comparable<?>)property.getValue(entry.getValue()).orElse(null);
                    if (comparable == null) {
                        return false;
                    }

                    if (state.getValue(property) != comparable) {
                        return false;
                    }
                }

                if (this.nbt == null) {
                    return true;
                } else {
                    BlockEntity entity = block.getEntity();
                    return entity != null && NbtUtils.compareNbt(this.nbt, entity.saveWithFullMetadata(block.getLevel().registryAccess()), true);
                }
            }
        }

        @Override
        public boolean requiresNbt() {
            return this.nbt != null;
        }
    }
}
