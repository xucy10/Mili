package net.minecraft.commands.arguments;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

public class TimeArgument implements ArgumentType<Integer> {
    private static final Collection<String> EXAMPLES = Arrays.asList("0d", "0s", "0t", "0");
    private static final SimpleCommandExceptionType ERROR_INVALID_UNIT = new SimpleCommandExceptionType(Component.translatable("argument.time.invalid_unit"));
    private static final Dynamic2CommandExceptionType ERROR_TICK_COUNT_TOO_LOW = new Dynamic2CommandExceptionType(
        (tickCount, minTickCount) -> Component.translatableEscape("argument.time.tick_count_too_low", minTickCount, tickCount)
    );
    private static final Object2IntMap<String> UNITS = new Object2IntOpenHashMap<>();
    final int minimum;

    private TimeArgument(int minimum) {
        this.minimum = minimum;
    }

    public static TimeArgument time() {
        return new TimeArgument(0);
    }

    public static TimeArgument time(int minimum) {
        return new TimeArgument(minimum);
    }

    @Override
    public Integer parse(StringReader reader) throws CommandSyntaxException {
        float _float = reader.readFloat();
        String unquotedString = reader.readUnquotedString();
        int orDefault = UNITS.getOrDefault(unquotedString, 0);
        if (orDefault == 0) {
            throw ERROR_INVALID_UNIT.createWithContext(reader);
        } else {
            int rounded = Math.round(_float * orDefault);
            if (rounded < this.minimum) {
                throw ERROR_TICK_COUNT_TOO_LOW.createWithContext(reader, rounded, this.minimum);
            } else {
                return rounded;
            }
        }
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        StringReader stringReader = new StringReader(builder.getRemaining());

        try {
            stringReader.readFloat();
        } catch (CommandSyntaxException var5) {
            return builder.buildFuture();
        }

        return SharedSuggestionProvider.suggest(UNITS.keySet(), builder.createOffset(builder.getStart() + stringReader.getCursor()));
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    static {
        UNITS.put("d", 24000);
        UNITS.put("s", 20);
        UNITS.put("t", 1);
        UNITS.put("", 1);
    }

    public static class Info implements ArgumentTypeInfo<TimeArgument, TimeArgument.Info.Template> {
        @Override
        public void serializeToNetwork(TimeArgument.Info.Template template, FriendlyByteBuf buffer) {
            buffer.writeInt(template.min);
        }

        @Override
        public TimeArgument.Info.Template deserializeFromNetwork(FriendlyByteBuf buffer) {
            int _int = buffer.readInt();
            return new TimeArgument.Info.Template(_int);
        }

        @Override
        public void serializeToJson(TimeArgument.Info.Template template, JsonObject json) {
            json.addProperty("min", template.min);
        }

        @Override
        public TimeArgument.Info.Template unpack(TimeArgument argument) {
            return new TimeArgument.Info.Template(argument.minimum);
        }

        public final class Template implements ArgumentTypeInfo.Template<TimeArgument> {
            final int min;

            Template(final int min) {
                this.min = min;
            }

            @Override
            public TimeArgument instantiate(CommandBuildContext context) {
                return TimeArgument.time(this.min);
            }

            @Override
            public ArgumentTypeInfo<TimeArgument, ?> type() {
                return Info.this;
            }
        }
    }
}
