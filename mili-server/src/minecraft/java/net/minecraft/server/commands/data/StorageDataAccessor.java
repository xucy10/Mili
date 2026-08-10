package net.minecraft.server.commands.data;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Locale;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.CommandStorage;

public class StorageDataAccessor implements DataAccessor {
    static final SuggestionProvider<CommandSourceStack> SUGGEST_STORAGE = (context, builder) -> SharedSuggestionProvider.suggestResource(
        getGlobalTags(context).keys(), builder
    );
    public static final Function<String, DataCommands.DataProvider> PROVIDER = argumentName -> new DataCommands.DataProvider() {
        @Override
        public DataAccessor access(CommandContext<CommandSourceStack> context) {
            return new StorageDataAccessor(StorageDataAccessor.getGlobalTags(context), IdentifierArgument.getId(context, argumentName));
        }

        @Override
        public ArgumentBuilder<CommandSourceStack, ?> wrap(
            ArgumentBuilder<CommandSourceStack, ?> builder, Function<ArgumentBuilder<CommandSourceStack, ?>, ArgumentBuilder<CommandSourceStack, ?>> action
        ) {
            return builder.then(
                Commands.literal("storage")
                    .then(action.apply(Commands.argument(argumentName, IdentifierArgument.id()).suggests(StorageDataAccessor.SUGGEST_STORAGE)))
            );
        }
    };
    private final CommandStorage storage;
    private final Identifier id;

    static CommandStorage getGlobalTags(CommandContext<CommandSourceStack> context) {
        return context.getSource().getServer().getCommandStorage();
    }

    StorageDataAccessor(CommandStorage storage, Identifier id) {
        this.storage = storage;
        this.id = id;
    }

    @Override
    public void setData(CompoundTag other) {
        this.storage.set(this.id, other);
    }

    @Override
    public CompoundTag getData() {
        return this.storage.get(this.id);
    }

    @Override
    public Component getModifiedSuccess() {
        return Component.translatable("commands.data.storage.modified", Component.translationArg(this.id));
    }

    @Override
    public Component getPrintSuccess(Tag tag) {
        return Component.translatable("commands.data.storage.query", Component.translationArg(this.id), NbtUtils.toPrettyComponent(tag));
    }

    @Override
    public Component getPrintSuccess(NbtPathArgument.NbtPath path, double scale, int value) {
        return Component.translatable(
            "commands.data.storage.get", path.asString(), Component.translationArg(this.id), String.format(Locale.ROOT, "%.2f", scale), value
        );
    }
}
