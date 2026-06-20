package net.minecraft.util.parsing.packrat;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.util.parsing.packrat.commands.StringReaderTerms;

public interface DelayedException<T extends Exception> {
    T create(String message, int cursor);

    static DelayedException<CommandSyntaxException> create(SimpleCommandExceptionType exception) {
        return (message, cursor) -> exception.createWithContext(StringReaderTerms.createReader(message, cursor));
    }

    static DelayedException<CommandSyntaxException> create(DynamicCommandExceptionType exception, String argument) {
        return (message, cursor) -> exception.createWithContext(StringReaderTerms.createReader(message, cursor), argument);
    }
}
