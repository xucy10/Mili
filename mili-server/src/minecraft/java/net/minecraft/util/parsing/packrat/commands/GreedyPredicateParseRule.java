package net.minecraft.util.parsing.packrat.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.util.parsing.packrat.DelayedException;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.Rule;
import org.jspecify.annotations.Nullable;

public abstract class GreedyPredicateParseRule implements Rule<StringReader, String> {
    private final int minSize;
    private final int maxSize;
    private final DelayedException<CommandSyntaxException> error;

    public GreedyPredicateParseRule(int minSize, DelayedException<CommandSyntaxException> error) {
        this(minSize, Integer.MAX_VALUE, error);
    }

    public GreedyPredicateParseRule(int minSize, int maxSize, DelayedException<CommandSyntaxException> error) {
        this.minSize = minSize;
        this.maxSize = maxSize;
        this.error = error;
    }

    @Override
    public @Nullable String parse(ParseState<StringReader> parseState) {
        StringReader stringReader = parseState.input();
        String string = stringReader.getString();
        int cursor = stringReader.getCursor();
        int i = cursor;

        while (i < string.length() && this.isAccepted(string.charAt(i)) && i - cursor < this.maxSize) {
            i++;
        }

        int i1 = i - cursor;
        if (i1 < this.minSize) {
            parseState.errorCollector().store(parseState.mark(), this.error);
            return null;
        } else {
            stringReader.setCursor(i);
            return string.substring(cursor, i);
        }
    }

    protected abstract boolean isAccepted(char character);
}
