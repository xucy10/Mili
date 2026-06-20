package net.minecraft.util.parsing.packrat.commands;

import com.mojang.brigadier.StringReader;
import net.minecraft.util.parsing.packrat.CachedParseState;
import net.minecraft.util.parsing.packrat.ErrorCollector;

public class StringReaderParserState extends CachedParseState<StringReader> {
    private final StringReader input;

    public StringReaderParserState(ErrorCollector<StringReader> errorCollector, StringReader input) {
        super(errorCollector);
        this.input = input;
    }

    @Override
    public StringReader input() {
        return this.input;
    }

    @Override
    public int mark() {
        return this.input.getCursor();
    }

    @Override
    public void restore(int cursor) {
        this.input.setCursor(cursor);
    }
}
