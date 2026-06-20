package net.minecraft.util.parsing.packrat.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.Rule;
import org.jspecify.annotations.Nullable;

public class TagParseRule<T> implements Rule<StringReader, Dynamic<?>> {
    private final TagParser<T> parser;

    public TagParseRule(DynamicOps<T> ops) {
        this.parser = TagParser.create(ops);
    }

    @Override
    public @Nullable Dynamic<T> parse(ParseState<StringReader> parseState) {
        parseState.input().skipWhitespace();
        int i = parseState.mark();

        try {
            return new Dynamic<>(this.parser.getOps(), this.parser.parseAsArgument(parseState.input()));
        } catch (Exception var4) {
            parseState.errorCollector().store(i, var4);
            return null;
        }
    }
}
