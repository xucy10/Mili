package net.minecraft.commands.arguments.selector.options;

import com.google.common.collect.Maps;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.logging.LogUtils;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import org.slf4j.Logger;

public class EntitySelectorOptions {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, EntitySelectorOptions.Option> OPTIONS = Maps.newHashMap();
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_OPTION = new DynamicCommandExceptionType(
        option -> Component.translatableEscape("argument.entity.options.unknown", option)
    );
    public static final DynamicCommandExceptionType ERROR_INAPPLICABLE_OPTION = new DynamicCommandExceptionType(
        option -> Component.translatableEscape("argument.entity.options.inapplicable", option)
    );
    public static final SimpleCommandExceptionType ERROR_RANGE_NEGATIVE = new SimpleCommandExceptionType(
        Component.translatable("argument.entity.options.distance.negative")
    );
    public static final SimpleCommandExceptionType ERROR_LEVEL_NEGATIVE = new SimpleCommandExceptionType(
        Component.translatable("argument.entity.options.level.negative")
    );
    public static final SimpleCommandExceptionType ERROR_LIMIT_TOO_SMALL = new SimpleCommandExceptionType(
        Component.translatable("argument.entity.options.limit.toosmall")
    );
    public static final DynamicCommandExceptionType ERROR_SORT_UNKNOWN = new DynamicCommandExceptionType(
        sort -> Component.translatableEscape("argument.entity.options.sort.irreversible", sort)
    );
    public static final DynamicCommandExceptionType ERROR_GAME_MODE_INVALID = new DynamicCommandExceptionType(
        gameMode -> Component.translatableEscape("argument.entity.options.mode.invalid", gameMode)
    );
    public static final DynamicCommandExceptionType ERROR_ENTITY_TYPE_INVALID = new DynamicCommandExceptionType(
        type -> Component.translatableEscape("argument.entity.options.type.invalid", type)
    );

    private static void register(String id, EntitySelectorOptions.Modifier handler, Predicate<EntitySelectorParser> predicate, Component tooltip) {
        OPTIONS.put(id, new EntitySelectorOptions.Option(handler, predicate, tooltip));
    }

    public static void bootStrap() {
        if (OPTIONS.isEmpty()) {
            register("name", parser -> {
                int cursor = parser.getReader().getCursor();
                boolean shouldInvertValue = parser.shouldInvertValue();
                String string = parser.getReader().readString();
                if (parser.hasNameNotEquals() && !shouldInvertValue) {
                    parser.getReader().setCursor(cursor);
                    throw ERROR_INAPPLICABLE_OPTION.createWithContext(parser.getReader(), "name");
                } else {
                    if (shouldInvertValue) {
                        parser.setHasNameNotEquals(true);
                    } else {
                        parser.setHasNameEquals(true);
                    }

                    parser.addPredicate(entity -> entity.getPlainTextName().equals(string) != shouldInvertValue);
                }
            }, parser -> !parser.hasNameEquals(), Component.translatable("argument.entity.options.name.description"));
            register(
                "distance",
                parser -> {
                    int cursor = parser.getReader().getCursor();
                    MinMaxBounds.Doubles doubles = MinMaxBounds.Doubles.fromReader(parser.getReader());
                    if ((!doubles.min().isPresent() || !((Double)doubles.min().get() < 0.0))
                        && (!doubles.max().isPresent() || !((Double)doubles.max().get() < 0.0))) {
                        parser.setDistance(doubles);
                        parser.setWorldLimited();
                    } else {
                        parser.getReader().setCursor(cursor);
                        throw ERROR_RANGE_NEGATIVE.createWithContext(parser.getReader());
                    }
                },
                parser -> parser.getDistance() == null,
                Component.translatable("argument.entity.options.distance.description")
            );
            register("level", parser -> {
                int cursor = parser.getReader().getCursor();
                MinMaxBounds.Ints ints = MinMaxBounds.Ints.fromReader(parser.getReader());
                if ((!ints.min().isPresent() || (Integer)ints.min().get() >= 0) && (!ints.max().isPresent() || (Integer)ints.max().get() >= 0)) {
                    parser.setLevel(ints);
                    parser.setIncludesEntities(false);
                } else {
                    parser.getReader().setCursor(cursor);
                    throw ERROR_LEVEL_NEGATIVE.createWithContext(parser.getReader());
                }
            }, parser -> parser.getLevel() == null, Component.translatable("argument.entity.options.level.description"));
            register("x", parser -> {
                parser.setWorldLimited();
                parser.setX(parser.getReader().readDouble());
            }, parser -> parser.getX() == null, Component.translatable("argument.entity.options.x.description"));
            register("y", parser -> {
                parser.setWorldLimited();
                parser.setY(parser.getReader().readDouble());
            }, parser -> parser.getY() == null, Component.translatable("argument.entity.options.y.description"));
            register("z", parser -> {
                parser.setWorldLimited();
                parser.setZ(parser.getReader().readDouble());
            }, parser -> parser.getZ() == null, Component.translatable("argument.entity.options.z.description"));
            register("dx", parser -> {
                parser.setWorldLimited();
                parser.setDeltaX(parser.getReader().readDouble());
            }, parser -> parser.getDeltaX() == null, Component.translatable("argument.entity.options.dx.description"));
            register("dy", parser -> {
                parser.setWorldLimited();
                parser.setDeltaY(parser.getReader().readDouble());
            }, parser -> parser.getDeltaY() == null, Component.translatable("argument.entity.options.dy.description"));
            register("dz", parser -> {
                parser.setWorldLimited();
                parser.setDeltaZ(parser.getReader().readDouble());
            }, parser -> parser.getDeltaZ() == null, Component.translatable("argument.entity.options.dz.description"));
            register(
                "x_rotation",
                parser -> parser.setRotX(MinMaxBounds.FloatDegrees.fromReader(parser.getReader())),
                parser -> parser.getRotX() == null,
                Component.translatable("argument.entity.options.x_rotation.description")
            );
            register(
                "y_rotation",
                parser -> parser.setRotY(MinMaxBounds.FloatDegrees.fromReader(parser.getReader())),
                parser -> parser.getRotY() == null,
                Component.translatable("argument.entity.options.y_rotation.description")
            );
            register("limit", parser -> {
                int cursor = parser.getReader().getCursor();
                int _int = parser.getReader().readInt();
                if (_int < 1) {
                    parser.getReader().setCursor(cursor);
                    throw ERROR_LIMIT_TOO_SMALL.createWithContext(parser.getReader());
                } else {
                    parser.setMaxResults(_int);
                    parser.setLimited(true);
                }
            }, parser -> !parser.isCurrentEntity() && !parser.isLimited(), Component.translatable("argument.entity.options.limit.description"));
            register(
                "sort",
                parser -> {
                    int cursor = parser.getReader().getCursor();
                    String unquotedString = parser.getReader().readUnquotedString();
                    parser.setSuggestions(
                        (builder, consumer) -> SharedSuggestionProvider.suggest(Arrays.asList("nearest", "furthest", "random", "arbitrary"), builder)
                    );

                    parser.setOrder(switch (unquotedString) {
                        case "nearest" -> EntitySelectorParser.ORDER_NEAREST;
                        case "furthest" -> EntitySelectorParser.ORDER_FURTHEST;
                        case "random" -> EntitySelectorParser.ORDER_RANDOM;
                        case "arbitrary" -> EntitySelector.ORDER_ARBITRARY;
                        default -> {
                            parser.getReader().setCursor(cursor);
                            throw ERROR_SORT_UNKNOWN.createWithContext(parser.getReader(), unquotedString);
                        }
                    });
                    parser.setSorted(true);
                },
                parser -> !parser.isCurrentEntity() && !parser.isSorted(),
                Component.translatable("argument.entity.options.sort.description")
            );
            register("gamemode", parser -> {
                parser.setSuggestions((builder, consumer) -> {
                    String string = builder.getRemaining().toLowerCase(Locale.ROOT);
                    boolean flag = !parser.hasGamemodeNotEquals();
                    boolean flag1 = true;
                    if (!string.isEmpty()) {
                        if (string.charAt(0) == '!') {
                            flag = false;
                            string = string.substring(1);
                        } else {
                            flag1 = false;
                        }
                    }

                    for (GameType gameType1 : GameType.values()) {
                        if (gameType1.getName().toLowerCase(Locale.ROOT).startsWith(string)) {
                            if (flag1) {
                                builder.suggest("!" + gameType1.getName());
                            }

                            if (flag) {
                                builder.suggest(gameType1.getName());
                            }
                        }
                    }

                    return builder.buildFuture();
                });
                int cursor = parser.getReader().getCursor();
                boolean shouldInvertValue = parser.shouldInvertValue();
                if (parser.hasGamemodeNotEquals() && !shouldInvertValue) {
                    parser.getReader().setCursor(cursor);
                    throw ERROR_INAPPLICABLE_OPTION.createWithContext(parser.getReader(), "gamemode");
                } else {
                    String unquotedString = parser.getReader().readUnquotedString();
                    GameType gameType = GameType.byName(unquotedString, null);
                    if (gameType == null) {
                        parser.getReader().setCursor(cursor);
                        throw ERROR_GAME_MODE_INVALID.createWithContext(parser.getReader(), unquotedString);
                    } else {
                        parser.setIncludesEntities(false);
                        parser.addPredicate(entity -> {
                            if (entity instanceof ServerPlayer serverPlayer) {
                                GameType gameType1 = serverPlayer.gameMode();
                                return gameType1 == gameType ^ shouldInvertValue;
                            } else {
                                return false;
                            }
                        });
                        if (shouldInvertValue) {
                            parser.setHasGamemodeNotEquals(true);
                        } else {
                            parser.setHasGamemodeEquals(true);
                        }
                    }
                }
            }, parser -> !parser.hasGamemodeEquals(), Component.translatable("argument.entity.options.gamemode.description"));
            register("team", parser -> {
                boolean shouldInvertValue = parser.shouldInvertValue();
                String unquotedString = parser.getReader().readUnquotedString();
                parser.addPredicate(entity -> {
                    Team team = entity.getTeam();
                    String string = team == null ? "" : team.getName();
                    return string.equals(unquotedString) != shouldInvertValue;
                });
                if (shouldInvertValue) {
                    parser.setHasTeamNotEquals(true);
                } else {
                    parser.setHasTeamEquals(true);
                }
            }, parser -> !parser.hasTeamEquals(), Component.translatable("argument.entity.options.team.description"));
            register(
                "type",
                parser -> {
                    parser.setSuggestions(
                        (suggestionsBuilder, consumer) -> {
                            SharedSuggestionProvider.suggestResource(BuiltInRegistries.ENTITY_TYPE.keySet(), suggestionsBuilder, String.valueOf('!'));
                            SharedSuggestionProvider.suggestResource(
                                BuiltInRegistries.ENTITY_TYPE.getTags().map(named -> named.key().location()), suggestionsBuilder, "!#"
                            );
                            if (!parser.isTypeLimitedInversely()) {
                                SharedSuggestionProvider.suggestResource(BuiltInRegistries.ENTITY_TYPE.keySet(), suggestionsBuilder);
                                SharedSuggestionProvider.suggestResource(
                                    BuiltInRegistries.ENTITY_TYPE.getTags().map(named -> named.key().location()), suggestionsBuilder, String.valueOf('#')
                                );
                            }

                            return suggestionsBuilder.buildFuture();
                        }
                    );
                    int cursor = parser.getReader().getCursor();
                    boolean shouldInvertValue = parser.shouldInvertValue();
                    if (parser.isTypeLimitedInversely() && !shouldInvertValue) {
                        parser.getReader().setCursor(cursor);
                        throw ERROR_INAPPLICABLE_OPTION.createWithContext(parser.getReader(), "type");
                    } else {
                        if (shouldInvertValue) {
                            parser.setTypeLimitedInversely();
                        }

                        if (parser.isTag()) {
                            TagKey<EntityType<?>> tagKey = TagKey.create(Registries.ENTITY_TYPE, Identifier.read(parser.getReader()));
                            parser.addPredicate(entity -> entity.getType().is(tagKey) != shouldInvertValue);
                        } else {
                            Identifier identifier = Identifier.read(parser.getReader());
                            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(identifier).orElseThrow(() -> {
                                parser.getReader().setCursor(cursor);
                                return ERROR_ENTITY_TYPE_INVALID.createWithContext(parser.getReader(), identifier.toString());
                            });
                            if (Objects.equals(EntityType.PLAYER, entityType) && !shouldInvertValue) {
                                parser.setIncludesEntities(false);
                            }

                            parser.addPredicate(entity -> Objects.equals(entityType, entity.getType()) != shouldInvertValue);
                            if (!shouldInvertValue) {
                                parser.limitToType(entityType);
                            }
                        }
                    }
                },
                parser -> !parser.isTypeLimited(),
                Component.translatable("argument.entity.options.type.description")
            );
            register(
                "tag",
                parser -> {
                    boolean shouldInvertValue = parser.shouldInvertValue();
                    String unquotedString = parser.getReader().readUnquotedString();
                    parser.addPredicate(
                        entity -> "".equals(unquotedString)
                            ? entity.getTags().isEmpty() != shouldInvertValue
                            : entity.getTags().contains(unquotedString) != shouldInvertValue
                    );
                },
                parser -> true,
                Component.translatable("argument.entity.options.tag.description")
            );
            register("nbt", parser -> {
                boolean shouldInvertValue = parser.shouldInvertValue();
                CompoundTag compoundTag = TagParser.parseCompoundAsArgument(parser.getReader());
                parser.addPredicate(entity -> {
                    boolean var9;
                    try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {
                        TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, entity.registryAccess());
                        entity.saveWithoutId(tagValueOutput);
                        if (entity instanceof ServerPlayer serverPlayer) {
                            ItemStack selectedItem = serverPlayer.getInventory().getSelectedItem();
                            if (!selectedItem.isEmpty()) {
                                tagValueOutput.store("SelectedItem", ItemStack.CODEC, selectedItem);
                            }
                        }

                        var9 = NbtUtils.compareNbt(compoundTag, tagValueOutput.buildResult(), true) != shouldInvertValue;
                    }

                    return var9;
                });
            }, parser -> true, Component.translatable("argument.entity.options.nbt.description"));
            register("scores", parser -> {
                StringReader reader = parser.getReader();
                Map<String, MinMaxBounds.Ints> map = Maps.newHashMap();
                reader.expect('{');
                reader.skipWhitespace();

                while (reader.canRead() && reader.peek() != '}') {
                    reader.skipWhitespace();
                    String unquotedString = reader.readUnquotedString();
                    reader.skipWhitespace();
                    reader.expect('=');
                    reader.skipWhitespace();
                    MinMaxBounds.Ints ints = MinMaxBounds.Ints.fromReader(reader);
                    map.put(unquotedString, ints);
                    reader.skipWhitespace();
                    if (reader.canRead() && reader.peek() == ',') {
                        reader.skip();
                    }
                }

                reader.expect('}');
                if (!map.isEmpty()) {
                    parser.addPredicate(entity -> {
                        Scoreboard scoreboard = entity.level().getServer().getScoreboard();

                        for (Entry<String, MinMaxBounds.Ints> entry : map.entrySet()) {
                            Objective objective = scoreboard.getObjective(entry.getKey());
                            if (objective == null) {
                                return false;
                            }

                            ReadOnlyScoreInfo playerScoreInfo = scoreboard.getPlayerScoreInfo(entity, objective);
                            if (playerScoreInfo == null) {
                                return false;
                            }

                            if (!entry.getValue().matches(playerScoreInfo.value())) {
                                return false;
                            }
                        }

                        return true;
                    });
                }

                parser.setHasScores(true);
            }, parser -> !parser.hasScores(), Component.translatable("argument.entity.options.scores.description"));
            register("advancements", parser -> {
                StringReader reader = parser.getReader();
                Map<Identifier, Predicate<AdvancementProgress>> map = Maps.newHashMap();
                reader.expect('{');
                reader.skipWhitespace();

                while (reader.canRead() && reader.peek() != '}') {
                    reader.skipWhitespace();
                    Identifier identifier = Identifier.read(reader);
                    reader.skipWhitespace();
                    reader.expect('=');
                    reader.skipWhitespace();
                    if (reader.canRead() && reader.peek() == '{') {
                        Map<String, Predicate<CriterionProgress>> map1 = Maps.newHashMap();
                        reader.skipWhitespace();
                        reader.expect('{');
                        reader.skipWhitespace();

                        while (reader.canRead() && reader.peek() != '}') {
                            reader.skipWhitespace();
                            String unquotedString = reader.readUnquotedString();
                            reader.skipWhitespace();
                            reader.expect('=');
                            reader.skipWhitespace();
                            boolean _boolean = reader.readBoolean();
                            map1.put(unquotedString, criterionProgress -> criterionProgress.isDone() == _boolean);
                            reader.skipWhitespace();
                            if (reader.canRead() && reader.peek() == ',') {
                                reader.skip();
                            }
                        }

                        reader.skipWhitespace();
                        reader.expect('}');
                        reader.skipWhitespace();
                        map.put(identifier, advancementProgress -> {
                            for (Entry<String, Predicate<CriterionProgress>> entry : map1.entrySet()) {
                                CriterionProgress criterion = advancementProgress.getCriterion(entry.getKey());
                                if (criterion == null || !entry.getValue().test(criterion)) {
                                    return false;
                                }
                            }

                            return true;
                        });
                    } else {
                        boolean _boolean1 = reader.readBoolean();
                        map.put(identifier, advancementProgress -> advancementProgress.isDone() == _boolean1);
                    }

                    reader.skipWhitespace();
                    if (reader.canRead() && reader.peek() == ',') {
                        reader.skip();
                    }
                }

                reader.expect('}');
                if (!map.isEmpty()) {
                    parser.addPredicate(entity -> {
                        if (!(entity instanceof ServerPlayer serverPlayer)) {
                            return false;
                        } else {
                            PlayerAdvancements advancements = serverPlayer.getAdvancements();
                            ServerAdvancementManager advancements1 = serverPlayer.level().getServer().getAdvancements();

                            for (Entry<Identifier, Predicate<AdvancementProgress>> entry : map.entrySet()) {
                                AdvancementHolder advancementHolder = advancements1.get(entry.getKey());
                                if (advancementHolder == null || !entry.getValue().test(advancements.getOrStartProgress(advancementHolder))) {
                                    return false;
                                }
                            }

                            return true;
                        }
                    });
                    parser.setIncludesEntities(false);
                }

                parser.setHasAdvancements(true);
            }, parser -> !parser.hasAdvancements(), Component.translatable("argument.entity.options.advancements.description"));
            register(
                "predicate",
                parser -> {
                    boolean shouldInvertValue = parser.shouldInvertValue();
                    ResourceKey<LootItemCondition> resourceKey = ResourceKey.create(Registries.PREDICATE, Identifier.read(parser.getReader()));
                    parser.addPredicate(
                        entity -> {
                            if (entity.level() instanceof ServerLevel serverLevel) {
                                Optional<LootItemCondition> optional = serverLevel.getServer()
                                    .reloadableRegistries()
                                    .lookup()
                                    .get(resourceKey)
                                    .map(Holder::value);
                                if (optional.isEmpty()) {
                                    return false;
                                } else {
                                    LootParams lootParams = new LootParams.Builder(serverLevel)
                                        .withParameter(LootContextParams.THIS_ENTITY, entity)
                                        .withParameter(LootContextParams.ORIGIN, entity.position())
                                        .create(LootContextParamSets.SELECTOR);
                                    LootContext lootContext = new LootContext.Builder(lootParams).create(Optional.empty());
                                    lootContext.pushVisitedElement(LootContext.createVisitedEntry(optional.get()));
                                    return shouldInvertValue ^ optional.get().test(lootContext);
                                }
                            } else {
                                return false;
                            }
                        }
                    );
                },
                parser -> true,
                Component.translatable("argument.entity.options.predicate.description")
            );
        }
    }

    public static EntitySelectorOptions.Modifier get(EntitySelectorParser parser, String id, int cursor) throws CommandSyntaxException {
        EntitySelectorOptions.Option option = OPTIONS.get(id);
        if (option != null) {
            if (option.canUse.test(parser)) {
                return option.modifier;
            } else {
                throw ERROR_INAPPLICABLE_OPTION.createWithContext(parser.getReader(), id);
            }
        } else {
            parser.getReader().setCursor(cursor);
            throw ERROR_UNKNOWN_OPTION.createWithContext(parser.getReader(), id);
        }
    }

    public static void suggestNames(EntitySelectorParser parser, SuggestionsBuilder builder) {
        String string = builder.getRemaining().toLowerCase(Locale.ROOT);

        for (Entry<String, EntitySelectorOptions.Option> entry : OPTIONS.entrySet()) {
            if (entry.getValue().canUse.test(parser) && entry.getKey().toLowerCase(Locale.ROOT).startsWith(string)) {
                builder.suggest(entry.getKey() + "=", entry.getValue().description);
            }
        }
    }

    @FunctionalInterface
    public interface Modifier {
        void handle(EntitySelectorParser parser) throws CommandSyntaxException;
    }

    record Option(EntitySelectorOptions.Modifier modifier, Predicate<EntitySelectorParser> canUse, Component description) {
    }
}
