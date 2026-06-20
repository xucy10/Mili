package net.minecraft.commands.arguments.selector;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

public record SelectorPattern(String pattern, EntitySelector resolved) {
    public static final Codec<SelectorPattern> CODEC = Codec.STRING.comapFlatMap(SelectorPattern::parse, SelectorPattern::pattern);

    public static DataResult<SelectorPattern> parse(String pattern) {
        try {
            EntitySelectorParser entitySelectorParser = new EntitySelectorParser(new StringReader(pattern), true);
            return DataResult.success(new SelectorPattern(pattern, entitySelectorParser.parse()));
        } catch (CommandSyntaxException var2) {
            return DataResult.error(() -> "Invalid selector component"); // Paper - limit selector error message
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SelectorPattern selectorPattern && this.pattern.equals(selectorPattern.pattern);
    }

    @Override
    public int hashCode() {
        return this.pattern.hashCode();
    }

    @Override
    public String toString() {
        return this.pattern;
    }
}
