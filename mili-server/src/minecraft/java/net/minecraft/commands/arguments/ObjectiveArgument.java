package net.minecraft.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

public class ObjectiveArgument implements ArgumentType<String> {
    private static final Collection<String> EXAMPLES = Arrays.asList("foo", "*", "012");
    private static final DynamicCommandExceptionType ERROR_OBJECTIVE_NOT_FOUND = new DynamicCommandExceptionType(
        objective -> Component.translatableEscape("arguments.objective.notFound", objective)
    );
    private static final DynamicCommandExceptionType ERROR_OBJECTIVE_READ_ONLY = new DynamicCommandExceptionType(
        objective -> Component.translatableEscape("arguments.objective.readonly", objective)
    );

    public static ObjectiveArgument objective() {
        return new ObjectiveArgument();
    }

    public static Objective getObjective(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        String string = context.getArgument(name, String.class);
        Scoreboard scoreboard = context.getSource().getServer().getScoreboard();
        Objective objective = scoreboard.getObjective(string);
        if (objective == null) {
            throw ERROR_OBJECTIVE_NOT_FOUND.create(string);
        } else {
            return objective;
        }
    }

    public static Objective getWritableObjective(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        Objective objective = getObjective(context, name);
        if (objective.getCriteria().isReadOnly()) {
            throw ERROR_OBJECTIVE_READ_ONLY.create(objective.getName());
        } else {
            return objective;
        }
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        return reader.readUnquotedString();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        S source = context.getSource();
        if (source instanceof CommandSourceStack commandSourceStack) {
            return SharedSuggestionProvider.suggest(commandSourceStack.getServer().getScoreboard().getObjectiveNames(), builder);
        } else {
            return source instanceof SharedSuggestionProvider sharedSuggestionProvider
                ? sharedSuggestionProvider.customSuggestion(context)
                : Suggestions.empty();
        }
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
