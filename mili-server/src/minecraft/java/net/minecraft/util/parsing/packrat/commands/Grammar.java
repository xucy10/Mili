package net.minecraft.util.parsing.packrat.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.util.parsing.packrat.DelayedException;
import net.minecraft.util.parsing.packrat.Dictionary;
import net.minecraft.util.parsing.packrat.ErrorCollector;
import net.minecraft.util.parsing.packrat.ErrorEntry;
import net.minecraft.util.parsing.packrat.NamedRule;
import net.minecraft.util.parsing.packrat.ParseState;

public record Grammar<T>(Dictionary<StringReader> rules, NamedRule<StringReader, T> top) implements CommandArgumentParser<T> {
    public Grammar(Dictionary<StringReader> rules, NamedRule<StringReader, T> top) {
        rules.checkAllBound();
        this.rules = rules;
        this.top = top;
    }

    public Optional<T> parse(ParseState<StringReader> parseState) {
        return parseState.parseTopRule(this.top);
    }

    @Override
    public T parseForCommands(StringReader reader) throws CommandSyntaxException {
        ErrorCollector.LongestOnly<StringReader> longestOnly = new ErrorCollector.LongestOnly<>();
        StringReaderParserState stringReaderParserState = new StringReaderParserState(longestOnly, reader);
        Optional<T> optional = this.parse(stringReaderParserState);
        if (optional.isPresent()) {
            return optional.get();
        } else {
            List<ErrorEntry<StringReader>> list = longestOnly.entries();
            List<Exception> list1 = list.stream().<Exception>mapMulti((errorEntry, consumer) -> {
                if (errorEntry.reason() instanceof DelayedException<?> delayedException) {
                    consumer.accept(delayedException.create(reader.getString(), errorEntry.cursor()));
                } else if (errorEntry.reason() instanceof Exception exception1) {
                    consumer.accept(exception1);
                }
            }).toList();

            for (Exception exception : list1) {
                if (exception instanceof CommandSyntaxException commandSyntaxException) {
                    throw commandSyntaxException;
                }
            }

            if (list1.size() == 1 && list1.get(0) instanceof RuntimeException runtimeException) {
                throw runtimeException;
            } else {
                throw new IllegalStateException("Failed to parse: " + list.stream().map(ErrorEntry::toString).collect(Collectors.joining(", ")));
            }
        }
    }

    @Override
    public CompletableFuture<Suggestions> parseForSuggestions(SuggestionsBuilder builder) {
        StringReader stringReader = new StringReader(builder.getInput());
        stringReader.setCursor(builder.getStart());
        ErrorCollector.LongestOnly<StringReader> longestOnly = new ErrorCollector.LongestOnly<>();
        StringReaderParserState stringReaderParserState = new StringReaderParserState(longestOnly, stringReader);
        this.parse(stringReaderParserState);
        List<ErrorEntry<StringReader>> list = longestOnly.entries();
        if (list.isEmpty()) {
            return builder.buildFuture();
        } else {
            SuggestionsBuilder suggestionsBuilder = builder.createOffset(longestOnly.cursor());

            for (ErrorEntry<StringReader> errorEntry : list) {
                if (errorEntry.suggestions() instanceof ResourceSuggestion resourceSuggestion) {
                    SharedSuggestionProvider.suggestResource(resourceSuggestion.possibleResources(), suggestionsBuilder);
                } else {
                    SharedSuggestionProvider.suggest(errorEntry.suggestions().possibleValues(stringReaderParserState), suggestionsBuilder);
                }
            }

            return suggestionsBuilder.buildFuture();
        }
    }
}
