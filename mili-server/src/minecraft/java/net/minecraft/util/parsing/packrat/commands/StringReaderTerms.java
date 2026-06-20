package net.minecraft.util.parsing.packrat.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.chars.CharList;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.util.parsing.packrat.Control;
import net.minecraft.util.parsing.packrat.DelayedException;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.Scope;
import net.minecraft.util.parsing.packrat.SuggestionSupplier;
import net.minecraft.util.parsing.packrat.Term;

public interface StringReaderTerms {
    static Term<StringReader> word(String value) {
        return new StringReaderTerms.TerminalWord(value);
    }

    static Term<StringReader> character(final char value) {
        return new StringReaderTerms.TerminalCharacters(CharList.of(value)) {
            @Override
            protected boolean isAccepted(char character) {
                return value == character;
            }
        };
    }

    static Term<StringReader> characters(final char value1, final char value2) {
        return new StringReaderTerms.TerminalCharacters(CharList.of(value1, value2)) {
            @Override
            protected boolean isAccepted(char character) {
                return character == value1 || character == value2;
            }
        };
    }

    static StringReader createReader(String input, int cursor) {
        StringReader stringReader = new StringReader(input);
        stringReader.setCursor(cursor);
        return stringReader;
    }

    public abstract static class TerminalCharacters implements Term<StringReader> {
        private final DelayedException<CommandSyntaxException> error;
        private final SuggestionSupplier<StringReader> suggestions;

        public TerminalCharacters(CharList characters) {
            String string = characters.intStream().mapToObj(Character::toString).collect(Collectors.joining("|"));
            this.error = DelayedException.create(CommandSyntaxException.BUILT_IN_EXCEPTIONS.literalIncorrect(), string);
            this.suggestions = parseState -> characters.intStream().mapToObj(Character::toString);
        }

        @Override
        public boolean parse(ParseState<StringReader> parseState, Scope scope, Control control) {
            parseState.input().skipWhitespace();
            int i = parseState.mark();
            if (parseState.input().canRead() && this.isAccepted(parseState.input().read())) {
                return true;
            } else {
                parseState.errorCollector().store(i, this.suggestions, this.error);
                return false;
            }
        }

        protected abstract boolean isAccepted(char character);
    }

    public static final class TerminalWord implements Term<StringReader> {
        private final String value;
        private final DelayedException<CommandSyntaxException> error;
        private final SuggestionSupplier<StringReader> suggestions;

        public TerminalWord(String value) {
            this.value = value;
            this.error = DelayedException.create(CommandSyntaxException.BUILT_IN_EXCEPTIONS.literalIncorrect(), value);
            this.suggestions = parseState -> Stream.of(value);
        }

        @Override
        public boolean parse(ParseState<StringReader> parseState, Scope scope, Control control) {
            parseState.input().skipWhitespace();
            int i = parseState.mark();
            String unquotedString = parseState.input().readUnquotedString();
            if (!unquotedString.equals(this.value)) {
                parseState.errorCollector().store(i, this.suggestions, this.error);
                return false;
            } else {
                return true;
            }
        }

        @Override
        public String toString() {
            return "terminal[" + this.value + "]";
        }
    }
}
