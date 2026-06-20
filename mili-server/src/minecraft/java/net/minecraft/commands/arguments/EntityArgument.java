package net.minecraft.commands.arguments;

import com.google.common.collect.Iterables;
import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;

public class EntityArgument implements ArgumentType<EntitySelector> {
    private static final Collection<String> EXAMPLES = Arrays.asList("Player", "0123", "@e", "@e[type=foo]", "dd12be42-52a9-4a91-a8a1-11c01849e498");
    public static final SimpleCommandExceptionType ERROR_NOT_SINGLE_ENTITY = new SimpleCommandExceptionType(Component.translatable("argument.entity.toomany"));
    public static final SimpleCommandExceptionType ERROR_NOT_SINGLE_PLAYER = new SimpleCommandExceptionType(Component.translatable("argument.player.toomany"));
    public static final SimpleCommandExceptionType ERROR_ONLY_PLAYERS_ALLOWED = new SimpleCommandExceptionType(
        Component.translatable("argument.player.entities")
    );
    public static final SimpleCommandExceptionType NO_ENTITIES_FOUND = new SimpleCommandExceptionType(Component.translatable("argument.entity.notfound.entity"));
    public static final SimpleCommandExceptionType NO_PLAYERS_FOUND = new SimpleCommandExceptionType(Component.translatable("argument.entity.notfound.player"));
    public static final SimpleCommandExceptionType ERROR_SELECTORS_NOT_ALLOWED = new SimpleCommandExceptionType(
        Component.translatable("argument.entity.selector.not_allowed")
    );
    final boolean single;
    final boolean playersOnly;

    protected EntityArgument(boolean single, boolean playersOnly) {
        this.single = single;
        this.playersOnly = playersOnly;
    }

    public static EntityArgument entity() {
        return new EntityArgument(true, false);
    }

    public static Entity getEntity(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return context.getArgument(name, EntitySelector.class).findSingleEntity(context.getSource());
    }

    public static EntityArgument entities() {
        return new EntityArgument(false, false);
    }

    public static Collection<? extends Entity> getEntities(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        Collection<? extends Entity> optionalEntities = getOptionalEntities(context, name);
        if (optionalEntities.isEmpty()) {
            throw NO_ENTITIES_FOUND.create();
        } else {
            return optionalEntities;
        }
    }

    public static Collection<? extends Entity> getOptionalEntities(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return context.getArgument(name, EntitySelector.class).findEntities(context.getSource());
    }

    public static Collection<ServerPlayer> getOptionalPlayers(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return context.getArgument(name, EntitySelector.class).findPlayers(context.getSource());
    }

    public static EntityArgument player() {
        return new EntityArgument(true, true);
    }

    public static ServerPlayer getPlayer(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return context.getArgument(name, EntitySelector.class).findSinglePlayer(context.getSource());
    }

    public static EntityArgument players() {
        return new EntityArgument(false, true);
    }

    public static Collection<ServerPlayer> getPlayers(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        List<ServerPlayer> list = context.getArgument(name, EntitySelector.class).findPlayers(context.getSource());
        if (list.isEmpty()) {
            throw NO_PLAYERS_FOUND.create();
        } else {
            return list;
        }
    }

    @Override
    public EntitySelector parse(StringReader reader) throws CommandSyntaxException {
        return this.parse(reader, true);
    }

    @Override
    public <S> EntitySelector parse(StringReader reader, S suggestionProvider) throws CommandSyntaxException {
        return this.parse(reader, EntitySelectorParser.allowSelectors(suggestionProvider));
    }

    private EntitySelector parse(StringReader reader, boolean allowSelectors) throws CommandSyntaxException {
        // CraftBukkit start
        return this.parse(reader, allowSelectors, false);
    }
    public EntitySelector parse(StringReader reader, boolean allowSelectors, boolean overridePermissions) throws CommandSyntaxException {
        // CraftBukkit end
        int i = 0;
        EntitySelectorParser entitySelectorParser = new EntitySelectorParser(reader, allowSelectors);
        EntitySelector entitySelector = entitySelectorParser.parse(overridePermissions); // CraftBukkit
        if (entitySelector.getMaxResults() > 1 && this.single) {
            if (this.playersOnly) {
                reader.setCursor(0);
                throw ERROR_NOT_SINGLE_PLAYER.createWithContext(reader);
            } else {
                reader.setCursor(0);
                throw ERROR_NOT_SINGLE_ENTITY.createWithContext(reader);
            }
        } else if (entitySelector.includesEntities() && this.playersOnly && !entitySelector.isSelfSelector()) {
            reader.setCursor(0);
            throw ERROR_ONLY_PLAYERS_ALLOWED.createWithContext(reader);
        } else {
            return entitySelector;
        }
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (context.getSource() instanceof SharedSuggestionProvider sharedSuggestionProvider) {
            StringReader stringReader = new StringReader(builder.getInput());
            stringReader.setCursor(builder.getStart());
            // Paper start - Fix EntityArgument permissions
            final boolean permission = sharedSuggestionProvider instanceof CommandSourceStack stack
                ? stack.bypassSelectorPermissions || stack.hasPermission(Permissions.COMMANDS_ENTITY_SELECTORS, "minecraft.command.selector")
                // Only CommandSourceStack implements SharedSuggestionProvider. If *somehow* anything else ends up here, try to query its permission level, otherwise yield false.
                : sharedSuggestionProvider.permissions().hasPermission(Permissions.COMMANDS_ENTITY_SELECTORS);
            EntitySelectorParser entitySelectorParser = new EntitySelectorParser(
                stringReader, permission
            );
            // Paper end - Fix EntityArgument permissions

            try {
                entitySelectorParser.parse();
            } catch (CommandSyntaxException var7) {
            }

            return entitySelectorParser.fillSuggestions(
                builder,
                offsetBuilder -> {
                    Collection<String> onlinePlayerNames = sharedSuggestionProvider.getOnlinePlayerNames();
                    Iterable<String> iterable = (Iterable<String>)(this.playersOnly
                        ? onlinePlayerNames
                        : Iterables.concat(onlinePlayerNames, sharedSuggestionProvider.getSelectedEntities()));
                    SharedSuggestionProvider.suggest(iterable, offsetBuilder);
                }
            );
        } else {
            return Suggestions.empty();
        }
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public static class Info implements ArgumentTypeInfo<EntityArgument, EntityArgument.Info.Template> {
        private static final byte FLAG_SINGLE = 1;
        private static final byte FLAG_PLAYERS_ONLY = 2;

        @Override
        public void serializeToNetwork(EntityArgument.Info.Template template, FriendlyByteBuf buffer) {
            int i = 0;
            if (template.single) {
                i |= 1;
            }

            if (template.playersOnly) {
                i |= 2;
            }

            buffer.writeByte(i);
        }

        @Override
        public EntityArgument.Info.Template deserializeFromNetwork(FriendlyByteBuf buffer) {
            byte _byte = buffer.readByte();
            return new EntityArgument.Info.Template((_byte & 1) != 0, (_byte & 2) != 0);
        }

        @Override
        public void serializeToJson(EntityArgument.Info.Template template, JsonObject json) {
            json.addProperty("amount", template.single ? "single" : "multiple");
            json.addProperty("type", template.playersOnly ? "players" : "entities");
        }

        @Override
        public EntityArgument.Info.Template unpack(EntityArgument argument) {
            return new EntityArgument.Info.Template(argument.single, argument.playersOnly);
        }

        public final class Template implements ArgumentTypeInfo.Template<EntityArgument> {
            final boolean single;
            final boolean playersOnly;

            Template(final boolean single, final boolean playersOnly) {
                this.single = single;
                this.playersOnly = playersOnly;
            }

            @Override
            public EntityArgument instantiate(CommandBuildContext context) {
                return new EntityArgument(this.single, this.playersOnly);
            }

            @Override
            public ArgumentTypeInfo<EntityArgument, ?> type() {
                return Info.this;
            }
        }
    }
}
