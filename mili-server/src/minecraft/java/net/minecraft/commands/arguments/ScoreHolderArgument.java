package net.minecraft.commands.arguments;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.ScoreHolder;

public class ScoreHolderArgument implements ArgumentType<ScoreHolderArgument.Result> {
    public static final SuggestionProvider<CommandSourceStack> SUGGEST_SCORE_HOLDERS = (context, builder) -> {
        StringReader stringReader = new StringReader(builder.getInput());
        stringReader.setCursor(builder.getStart());
        EntitySelectorParser entitySelectorParser = new EntitySelectorParser(
            stringReader, context.getSource().permissions().hasPermission(Permissions.COMMANDS_ENTITY_SELECTORS)
        );

        try {
            entitySelectorParser.parse();
        } catch (CommandSyntaxException var5) {
        }

        return entitySelectorParser.fillSuggestions(
            builder, offsetBuilder -> SharedSuggestionProvider.suggest(context.getSource().getOnlinePlayerNames(), offsetBuilder)
        );
    };
    private static final Collection<String> EXAMPLES = Arrays.asList("Player", "0123", "*", "@e");
    private static final SimpleCommandExceptionType ERROR_NO_RESULTS = new SimpleCommandExceptionType(Component.translatable("argument.scoreHolder.empty"));
    final boolean multiple;

    public ScoreHolderArgument(boolean multiple) {
        this.multiple = multiple;
    }

    public static ScoreHolder getName(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return getNames(context, name).iterator().next();
    }

    public static Collection<ScoreHolder> getNames(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return getNames(context, name, Collections::emptyList);
    }

    public static Collection<ScoreHolder> getNamesWithDefaultWildcard(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return getNames(context, name, context.getSource().getServer().getScoreboard()::getTrackedPlayers);
    }

    public static Collection<ScoreHolder> getNames(CommandContext<CommandSourceStack> context, String name, Supplier<Collection<ScoreHolder>> objectives) throws CommandSyntaxException {
        Collection<ScoreHolder> names = context.getArgument(name, ScoreHolderArgument.Result.class).getNames(context.getSource(), objectives);
        if (names.isEmpty()) {
            throw EntityArgument.NO_ENTITIES_FOUND.create();
        } else {
            return names;
        }
    }

    public static ScoreHolderArgument scoreHolder() {
        return new ScoreHolderArgument(false);
    }

    public static ScoreHolderArgument scoreHolders() {
        return new ScoreHolderArgument(true);
    }

    @Override
    public ScoreHolderArgument.Result parse(StringReader reader) throws CommandSyntaxException {
        return this.parse(reader, true);
    }

    @Override
    public <S> ScoreHolderArgument.Result parse(StringReader reader, S suggestionProvider) throws CommandSyntaxException {
        return this.parse(reader, EntitySelectorParser.allowSelectors(suggestionProvider));
    }

    private ScoreHolderArgument.Result parse(StringReader reader, boolean allowSelectors) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '@') {
            EntitySelectorParser entitySelectorParser = new EntitySelectorParser(reader, allowSelectors);
            EntitySelector entitySelector = entitySelectorParser.parse();
            if (!this.multiple && entitySelector.getMaxResults() > 1) {
                throw EntityArgument.ERROR_NOT_SINGLE_ENTITY.createWithContext(reader);
            } else {
                return new ScoreHolderArgument.SelectorResult(entitySelector);
            }
        } else {
            int cursor = reader.getCursor();

            while (reader.canRead() && reader.peek() != ' ') {
                reader.skip();
            }

            String sub = reader.getString().substring(cursor, reader.getCursor());
            if (sub.equals("*")) {
                return (source, objectives) -> {
                    Collection<ScoreHolder> collection = objectives.get();
                    if (collection.isEmpty()) {
                        throw ERROR_NO_RESULTS.create();
                    } else {
                        return collection;
                    }
                };
            } else {
                List<ScoreHolder> list = List.of(ScoreHolder.forNameOnly(sub));
                if (sub.startsWith("#")) {
                    return (source, objectives) -> list;
                } else {
                    try {
                        UUID uuid = UUID.fromString(sub);
                        return (source, objectives) -> {
                            MinecraftServer server = source.getServer();
                            ScoreHolder scoreHolder = null;
                            List<ScoreHolder> list1 = null;

                            for (ServerLevel serverLevel : server.getAllLevels()) {
                                Entity entity = serverLevel.getEntity(uuid);
                                if (entity != null) {
                                    if (scoreHolder == null) {
                                        scoreHolder = entity;
                                    } else {
                                        if (list1 == null) {
                                            list1 = new ArrayList<>();
                                            list1.add(scoreHolder);
                                        }

                                        list1.add(entity);
                                    }
                                }
                            }

                            if (list1 != null) {
                                return list1;
                            } else {
                                return scoreHolder != null ? List.of(scoreHolder) : list;
                            }
                        };
                    } catch (IllegalArgumentException var7) {
                        return (source, objectives) -> {
                            MinecraftServer server = source.getServer();
                            ServerPlayer playerByName = server.getPlayerList().getPlayerByName(sub);
                            return playerByName != null ? List.of(playerByName) : list;
                        };
                    }
                }
            }
        }
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public static class Info implements ArgumentTypeInfo<ScoreHolderArgument, ScoreHolderArgument.Info.Template> {
        private static final byte FLAG_MULTIPLE = 1;

        @Override
        public void serializeToNetwork(ScoreHolderArgument.Info.Template template, FriendlyByteBuf buffer) {
            int i = 0;
            if (template.multiple) {
                i |= 1;
            }

            buffer.writeByte(i);
        }

        @Override
        public ScoreHolderArgument.Info.Template deserializeFromNetwork(FriendlyByteBuf buffer) {
            byte _byte = buffer.readByte();
            boolean flag = (_byte & 1) != 0;
            return new ScoreHolderArgument.Info.Template(flag);
        }

        @Override
        public void serializeToJson(ScoreHolderArgument.Info.Template template, JsonObject json) {
            json.addProperty("amount", template.multiple ? "multiple" : "single");
        }

        @Override
        public ScoreHolderArgument.Info.Template unpack(ScoreHolderArgument argument) {
            return new ScoreHolderArgument.Info.Template(argument.multiple);
        }

        public final class Template implements ArgumentTypeInfo.Template<ScoreHolderArgument> {
            final boolean multiple;

            Template(final boolean multiple) {
                this.multiple = multiple;
            }

            @Override
            public ScoreHolderArgument instantiate(CommandBuildContext context) {
                return new ScoreHolderArgument(this.multiple);
            }

            @Override
            public ArgumentTypeInfo<ScoreHolderArgument, ?> type() {
                return Info.this;
            }
        }
    }

    @FunctionalInterface
    public interface Result {
        Collection<ScoreHolder> getNames(CommandSourceStack source, Supplier<Collection<ScoreHolder>> objectives) throws CommandSyntaxException;
    }

    public static class SelectorResult implements ScoreHolderArgument.Result {
        private final EntitySelector selector;

        public SelectorResult(EntitySelector selector) {
            this.selector = selector;
        }

        @Override
        public Collection<ScoreHolder> getNames(CommandSourceStack source, Supplier<Collection<ScoreHolder>> objectives) throws CommandSyntaxException {
            List<? extends Entity> list = this.selector.findEntities(source);
            if (list.isEmpty()) {
                throw EntityArgument.NO_ENTITIES_FOUND.create();
            } else {
                return List.copyOf(list);
            }
        }
    }
}
