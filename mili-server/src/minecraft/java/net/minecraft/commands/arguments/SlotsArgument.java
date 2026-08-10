package net.minecraft.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.ParserUtils;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.SlotRange;
import net.minecraft.world.inventory.SlotRanges;

public class SlotsArgument implements ArgumentType<SlotRange> {
    private static final Collection<String> EXAMPLES = List.of("container.*", "container.5", "weapon");
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_SLOT = new DynamicCommandExceptionType(
        object -> Component.translatableEscape("slot.unknown", object)
    );

    public static SlotsArgument slots() {
        return new SlotsArgument();
    }

    public static SlotRange getSlots(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, SlotRange.class);
    }

    @Override
    public SlotRange parse(StringReader reader) throws CommandSyntaxException {
        String _while = ParserUtils.readWhile(reader, value -> value != ' ');
        SlotRange slotRange = SlotRanges.nameToIds(_while);
        if (slotRange == null) {
            throw ERROR_UNKNOWN_SLOT.createWithContext(reader, _while);
        } else {
            return slotRange;
        }
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder suggestionBuilder) {
        return SharedSuggestionProvider.suggest(SlotRanges.allNames(), suggestionBuilder);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
