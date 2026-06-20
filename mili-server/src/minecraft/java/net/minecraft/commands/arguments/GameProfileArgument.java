package net.minecraft.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.NameAndId;

public class GameProfileArgument implements ArgumentType<GameProfileArgument.Result> {
    private static final Collection<String> EXAMPLES = Arrays.asList("Player", "0123", "dd12be42-52a9-4a91-a8a1-11c01849e498", "@e");
    public static final SimpleCommandExceptionType ERROR_UNKNOWN_PLAYER = new SimpleCommandExceptionType(Component.translatable("argument.player.unknown"));

    public static Collection<NameAndId> getGameProfiles(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return context.getArgument(name, GameProfileArgument.Result.class).getNames(context.getSource());
    }

    public static GameProfileArgument gameProfile() {
        return new GameProfileArgument();
    }

    @Override
    public <S> GameProfileArgument.Result parse(StringReader reader, S suggestionProvider) throws CommandSyntaxException {
        return parse(reader, EntitySelectorParser.allowSelectors(suggestionProvider));
    }

    @Override
    public GameProfileArgument.Result parse(StringReader reader) throws CommandSyntaxException {
        return parse(reader, true);
    }

    private static GameProfileArgument.Result parse(StringReader reader, boolean allowSelectors) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '@') {
            EntitySelectorParser entitySelectorParser = new EntitySelectorParser(reader, allowSelectors);
            EntitySelector entitySelector = entitySelectorParser.parse();
            if (entitySelector.includesEntities()) {
                throw EntityArgument.ERROR_ONLY_PLAYERS_ALLOWED.createWithContext(reader);
            } else {
                return new GameProfileArgument.SelectorResult(entitySelector);
            }
        } else {
            int cursor = reader.getCursor();

            while (reader.canRead() && reader.peek() != ' ') {
                reader.skip();
            }

            String sub = reader.getString().substring(cursor, reader.getCursor());
            return source -> {
                Optional<NameAndId> optional = source.getServer().services().nameToIdCache().get(sub);
                return Collections.singleton(optional.orElseThrow(ERROR_UNKNOWN_PLAYER::create));
            };
        }
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (context.getSource() instanceof SharedSuggestionProvider sharedSuggestionProvider) {
            StringReader stringReader = new StringReader(builder.getInput());
            stringReader.setCursor(builder.getStart());
            EntitySelectorParser entitySelectorParser = new EntitySelectorParser(
                stringReader, sharedSuggestionProvider.permissions().hasPermission(Permissions.COMMANDS_ENTITY_SELECTORS)
            );

            try {
                entitySelectorParser.parse();
            } catch (CommandSyntaxException var7) {
            }

            return entitySelectorParser.fillSuggestions(
                builder, suggestionsBuilder -> SharedSuggestionProvider.suggest(sharedSuggestionProvider.getOnlinePlayerNames(), suggestionsBuilder)
            );
        } else {
            return Suggestions.empty();
        }
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    @FunctionalInterface
    public interface Result {
        Collection<NameAndId> getNames(CommandSourceStack source) throws CommandSyntaxException;
    }

    public static class SelectorResult implements GameProfileArgument.Result {
        private final EntitySelector selector;

        public SelectorResult(EntitySelector selector) {
            this.selector = selector;
        }

        @Override
        public Collection<NameAndId> getNames(CommandSourceStack source) throws CommandSyntaxException {
            List<ServerPlayer> list = this.selector.findPlayers(source);
            if (list.isEmpty()) {
                throw EntityArgument.NO_PLAYERS_FOUND.create();
            } else {
                List<NameAndId> list1 = new ArrayList<>();

                for (ServerPlayer serverPlayer : list) {
                    list1.add(serverPlayer.nameAndId());
                }

                return list1;
            }
        }
    }
}
