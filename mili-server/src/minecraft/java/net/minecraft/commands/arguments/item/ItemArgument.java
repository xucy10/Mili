package net.minecraft.commands.arguments.item;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandBuildContext;

public class ItemArgument implements ArgumentType<ItemInput> {
    private static final Collection<String> EXAMPLES = Arrays.asList("stick", "minecraft:stick", "stick{foo=bar}");
    private final ItemParser parser;

    public ItemArgument(CommandBuildContext context) {
        this.parser = new ItemParser(context);
    }

    public static ItemArgument item(CommandBuildContext context) {
        return new ItemArgument(context);
    }

    @Override
    public ItemInput parse(StringReader reader) throws CommandSyntaxException {
        ItemParser.ItemResult itemResult = this.parser.parse(reader);
        return new ItemInput(itemResult.item(), itemResult.components());
    }

    public static <S> ItemInput getItem(CommandContext<S> context, String name) {
        return context.getArgument(name, ItemInput.class);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return this.parser.fillSuggestions(builder);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
