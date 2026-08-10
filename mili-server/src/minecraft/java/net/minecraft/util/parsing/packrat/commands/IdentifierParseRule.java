package net.minecraft.util.parsing.packrat.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.resources.Identifier;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.Rule;
import org.jspecify.annotations.Nullable;

public class IdentifierParseRule implements Rule<StringReader, Identifier> {
    public static final Rule<StringReader, Identifier> INSTANCE = new IdentifierParseRule();

    private IdentifierParseRule() {
    }

    @Override
    public @Nullable Identifier parse(ParseState<StringReader> parseState) {
        parseState.input().skipWhitespace();

        try {
            return Identifier.readNonEmpty(parseState.input());
        } catch (CommandSyntaxException var3) {
            return null;
        }
    }
}
