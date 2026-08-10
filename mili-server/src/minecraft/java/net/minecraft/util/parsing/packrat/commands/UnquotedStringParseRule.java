package net.minecraft.util.parsing.packrat.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.util.parsing.packrat.DelayedException;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.Rule;
import org.jspecify.annotations.Nullable;

public class UnquotedStringParseRule implements Rule<StringReader, String> {
    private final int minSize;
    private final DelayedException<CommandSyntaxException> error;

    public UnquotedStringParseRule(int minSize, DelayedException<CommandSyntaxException> error) {
        this.minSize = minSize;
        this.error = error;
    }

    @Override
    public @Nullable String parse(ParseState<StringReader> parseState) {
        parseState.input().skipWhitespace();
        int i = parseState.mark();
        String unquotedString = parseState.input().readUnquotedString();
        if (unquotedString.length() < this.minSize) {
            parseState.errorCollector().store(i, this.error);
            return null;
        } else {
            return unquotedString;
        }
    }
}
