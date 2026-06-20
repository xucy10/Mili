package net.minecraft.util.parsing.packrat.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.util.parsing.packrat.DelayedException;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.Rule;

public final class GreedyPatternParseRule implements Rule<StringReader, String> {
    private final Pattern pattern;
    private final DelayedException<CommandSyntaxException> error;

    public GreedyPatternParseRule(Pattern pattern, DelayedException<CommandSyntaxException> error) {
        this.pattern = pattern;
        this.error = error;
    }

    @Override
    public String parse(ParseState<StringReader> parseState) {
        StringReader stringReader = parseState.input();
        String string = stringReader.getString();
        Matcher matcher = this.pattern.matcher(string).region(stringReader.getCursor(), string.length());
        if (!matcher.lookingAt()) {
            parseState.errorCollector().store(parseState.mark(), this.error);
            return null;
        } else {
            stringReader.setCursor(matcher.end());
            return matcher.group(0);
        }
    }
}
