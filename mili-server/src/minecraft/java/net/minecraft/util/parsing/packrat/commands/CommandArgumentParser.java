package net.minecraft.util.parsing.packrat.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface CommandArgumentParser<T> {
    T parseForCommands(StringReader reader) throws CommandSyntaxException;

    CompletableFuture<Suggestions> parseForSuggestions(SuggestionsBuilder builder);

    default <S> CommandArgumentParser<S> mapResult(final Function<T, S> mapper) {
        return new CommandArgumentParser<S>() {
            @Override
            public S parseForCommands(StringReader reader) throws CommandSyntaxException {
                return mapper.apply((T)CommandArgumentParser.this.parseForCommands(reader));
            }

            @Override
            public CompletableFuture<Suggestions> parseForSuggestions(SuggestionsBuilder builder) {
                return CommandArgumentParser.this.parseForSuggestions(builder);
            }
        };
    }

    default <T, O> CommandArgumentParser<T> withCodec(
        final DynamicOps<O> ops, final CommandArgumentParser<O> parser, final Codec<T> codec, final DynamicCommandExceptionType error
    ) {
        return new CommandArgumentParser<T>() {
            @Override
            public T parseForCommands(StringReader reader) throws CommandSyntaxException {
                int cursor = reader.getCursor();
                O object = parser.parseForCommands(reader);
                DataResult<T> dataResult = codec.parse(ops, object);
                return dataResult.getOrThrow(string -> {
                    reader.setCursor(cursor);
                    return error.createWithContext(reader, string);
                });
            }

            @Override
            public CompletableFuture<Suggestions> parseForSuggestions(SuggestionsBuilder builder) {
                return CommandArgumentParser.this.parseForSuggestions(builder);
            }
        };
    }
}
