package net.minecraft.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public class DimensionArgument implements ArgumentType<Identifier> {
    private static final Collection<String> EXAMPLES = Stream.of(Level.OVERWORLD, Level.NETHER)
        .map(key -> key.identifier().toString())
        .collect(Collectors.toList());
    public static final DynamicCommandExceptionType ERROR_INVALID_VALUE = new DynamicCommandExceptionType(
        dimension -> Component.translatableEscape("argument.dimension.invalid", dimension)
    );

    @Override
    public Identifier parse(StringReader reader) throws CommandSyntaxException {
        return Identifier.read(reader);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return context.getSource() instanceof SharedSuggestionProvider
            ? SharedSuggestionProvider.suggestResource(((SharedSuggestionProvider)context.getSource()).levels().stream().map(ResourceKey::identifier), builder)
            : Suggestions.empty();
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public static DimensionArgument dimension() {
        return new DimensionArgument();
    }

    public static ServerLevel getDimension(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        Identifier identifier = context.getArgument(name, Identifier.class);
        ResourceKey<Level> resourceKey = ResourceKey.create(Registries.DIMENSION, identifier);
        ServerLevel level = context.getSource().getServer().getLevel(resourceKey);
        if (level == null) {
            throw ERROR_INVALID_VALUE.create(identifier);
        } else {
            return level;
        }
    }
}
