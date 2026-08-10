package net.minecraft.commands.arguments;

import com.google.common.collect.Lists;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

public class ObjectiveCriteriaArgument implements ArgumentType<ObjectiveCriteria> {
    private static final Collection<String> EXAMPLES = Arrays.asList("foo", "foo.bar.baz", "minecraft:foo");
    public static final DynamicCommandExceptionType ERROR_INVALID_VALUE = new DynamicCommandExceptionType(
        criterion -> Component.translatableEscape("argument.criteria.invalid", criterion)
    );

    private ObjectiveCriteriaArgument() {
    }

    public static ObjectiveCriteriaArgument criteria() {
        return new ObjectiveCriteriaArgument();
    }

    public static ObjectiveCriteria getCriteria(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, ObjectiveCriteria.class);
    }

    @Override
    public ObjectiveCriteria parse(StringReader reader) throws CommandSyntaxException {
        int cursor = reader.getCursor();

        while (reader.canRead() && reader.peek() != ' ') {
            reader.skip();
        }

        String sub = reader.getString().substring(cursor, reader.getCursor());
        return ObjectiveCriteria.byName(sub).orElseThrow(() -> {
            reader.setCursor(cursor);
            return ERROR_INVALID_VALUE.createWithContext(reader, sub);
        });
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        List<String> list = Lists.newArrayList(ObjectiveCriteria.getCustomCriteriaNames());

        for (StatType<?> statType : BuiltInRegistries.STAT_TYPE) {
            for (Object object : statType.getRegistry()) {
                String name = this.getName(statType, object);
                list.add(name);
            }
        }

        return SharedSuggestionProvider.suggest(list, builder);
    }

    public <T> String getName(StatType<T> type, Object value) {
        return Stat.buildName(type, (T)value);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
