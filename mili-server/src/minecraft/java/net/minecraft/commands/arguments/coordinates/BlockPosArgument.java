package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public class BlockPosArgument implements ArgumentType<Coordinates> {
    private static final Collection<String> EXAMPLES = Arrays.asList("0 0 0", "~ ~ ~", "^ ^ ^", "^1 ^ ^-5", "~0.5 ~1 ~-5");
    public static final SimpleCommandExceptionType ERROR_NOT_LOADED = new SimpleCommandExceptionType(Component.translatable("argument.pos.unloaded"));
    public static final SimpleCommandExceptionType ERROR_OUT_OF_WORLD = new SimpleCommandExceptionType(Component.translatable("argument.pos.outofworld"));
    public static final SimpleCommandExceptionType ERROR_OUT_OF_BOUNDS = new SimpleCommandExceptionType(Component.translatable("argument.pos.outofbounds"));

    public static BlockPosArgument blockPos() {
        return new BlockPosArgument();
    }

    public static BlockPos getLoadedBlockPos(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        return getLoadedBlockPos(context, level, name);
    }

    public static BlockPos getLoadedBlockPos(CommandContext<CommandSourceStack> context, ServerLevel level, String name) throws CommandSyntaxException {
        BlockPos blockPos = getBlockPos(context, name);
        if (!level.hasChunkAt(blockPos)) {
            throw ERROR_NOT_LOADED.create();
        } else if (!level.isInWorldBounds(blockPos)) {
            throw ERROR_OUT_OF_WORLD.create();
        } else {
            return blockPos;
        }
    }

    public static BlockPos getBlockPos(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, Coordinates.class).getBlockPos(context.getSource());
    }

    public static BlockPos getSpawnablePos(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        BlockPos blockPos = getBlockPos(context, name);
        if (!Level.isInSpawnableBounds(blockPos)) {
            throw ERROR_OUT_OF_BOUNDS.create();
        } else {
            return blockPos;
        }
    }

    @Override
    public Coordinates parse(StringReader reader) throws CommandSyntaxException {
        return (Coordinates)(reader.canRead() && reader.peek() == '^' ? LocalCoordinates.parse(reader) : WorldCoordinates.parseInt(reader));
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof SharedSuggestionProvider)) {
            return Suggestions.empty();
        } else {
            String remaining = builder.getRemaining();
            Collection<SharedSuggestionProvider.TextCoordinates> collection;
            if (!remaining.isEmpty() && remaining.charAt(0) == '^') {
                collection = Collections.singleton(SharedSuggestionProvider.TextCoordinates.DEFAULT_LOCAL);
            } else {
                collection = ((SharedSuggestionProvider)context.getSource()).getRelevantCoordinates();
            }

            return SharedSuggestionProvider.suggestCoordinates(remaining, collection, builder, Commands.createValidator(this::parse));
        }
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
