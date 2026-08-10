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
import net.minecraft.server.level.ColumnPos;

public class ColumnPosArgument implements ArgumentType<Coordinates> {
    private static final Collection<String> EXAMPLES = Arrays.asList("0 0", "~ ~", "~1 ~-2", "^ ^", "^-1 ^0");
    public static final SimpleCommandExceptionType ERROR_NOT_COMPLETE = new SimpleCommandExceptionType(Component.translatable("argument.pos2d.incomplete"));

    public static ColumnPosArgument columnPos() {
        return new ColumnPosArgument();
    }

    public static ColumnPos getColumnPos(CommandContext<CommandSourceStack> context, String name) {
        BlockPos blockPos = context.getArgument(name, Coordinates.class).getBlockPos(context.getSource());
        return new ColumnPos(blockPos.getX(), blockPos.getZ());
    }

    @Override
    public Coordinates parse(StringReader reader) throws CommandSyntaxException {
        int cursor = reader.getCursor();
        if (!reader.canRead()) {
            throw ERROR_NOT_COMPLETE.createWithContext(reader);
        } else {
            WorldCoordinate worldCoordinate = WorldCoordinate.parseInt(reader);
            if (reader.canRead() && reader.peek() == ' ') {
                reader.skip();
                WorldCoordinate worldCoordinate1 = WorldCoordinate.parseInt(reader);
                return new WorldCoordinates(worldCoordinate, new WorldCoordinate(true, 0.0), worldCoordinate1);
            } else {
                reader.setCursor(cursor);
                throw ERROR_NOT_COMPLETE.createWithContext(reader);
            }
        }
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

            return SharedSuggestionProvider.suggest2DCoordinates(remaining, collection, builder, Commands.createValidator(this::parse));
        }
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
