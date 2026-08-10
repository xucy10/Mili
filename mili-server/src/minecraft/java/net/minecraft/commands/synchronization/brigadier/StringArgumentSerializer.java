package net.minecraft.commands.synchronization.brigadier;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType.StringType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.network.FriendlyByteBuf;

public class StringArgumentSerializer implements ArgumentTypeInfo<StringArgumentType, StringArgumentSerializer.Template> {
    @Override
    public void serializeToNetwork(StringArgumentSerializer.Template template, FriendlyByteBuf buffer) {
        buffer.writeEnum(template.type);
    }

    @Override
    public StringArgumentSerializer.Template deserializeFromNetwork(FriendlyByteBuf buffer) {
        StringType stringType = buffer.readEnum(StringType.class);
        return new StringArgumentSerializer.Template(stringType);
    }

    @Override
    public void serializeToJson(StringArgumentSerializer.Template template, JsonObject json) {
        json.addProperty("type", switch (template.type) {
            case SINGLE_WORD -> "word";
            case QUOTABLE_PHRASE -> "phrase";
            case GREEDY_PHRASE -> "greedy";
        });
    }

    @Override
    public StringArgumentSerializer.Template unpack(StringArgumentType argument) {
        return new StringArgumentSerializer.Template(argument.getType());
    }

    public final class Template implements ArgumentTypeInfo.Template<StringArgumentType> {
        final StringType type;

        public Template(final StringType type) {
            this.type = type;
        }

        @Override
        public StringArgumentType instantiate(CommandBuildContext context) {
            return switch (this.type) {
                case SINGLE_WORD -> StringArgumentType.word();
                case QUOTABLE_PHRASE -> StringArgumentType.string();
                case GREEDY_PHRASE -> StringArgumentType.greedyString();
            };
        }

        @Override
        public ArgumentTypeInfo<StringArgumentType, ?> type() {
            return StringArgumentSerializer.this;
        }
    }
}
