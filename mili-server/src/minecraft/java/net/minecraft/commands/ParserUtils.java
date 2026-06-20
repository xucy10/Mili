package net.minecraft.commands;

import com.mojang.brigadier.StringReader;
import net.minecraft.CharPredicate;

public class ParserUtils {
    public static String readWhile(StringReader reader, CharPredicate predicate) {
        int cursor = reader.getCursor();

        while (reader.canRead() && predicate.test(reader.peek())) {
            reader.skip();
        }

        return reader.getString().substring(cursor, reader.getCursor());
    }
}
