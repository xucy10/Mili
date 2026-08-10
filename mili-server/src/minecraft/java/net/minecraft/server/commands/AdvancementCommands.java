package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class AdvancementCommands {
    private static final DynamicCommandExceptionType ERROR_NO_ACTION_PERFORMED = new DynamicCommandExceptionType(object -> (Component)object);
    private static final Dynamic2CommandExceptionType ERROR_CRITERION_NOT_FOUND = new Dynamic2CommandExceptionType(
        (criteriaName, criterion) -> Component.translatableEscape("commands.advancement.criterionNotFound", criteriaName, criterion)
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("advancement")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.literal("grant")
                        .then(
                            Commands.argument("targets", EntityArgument.players())
                                .then(
                                    Commands.literal("only")
                                        .then(
                                            Commands.argument("advancement", ResourceKeyArgument.key(Registries.ADVANCEMENT))
                                                .executes(
                                                    commandContext -> perform(
                                                        commandContext.getSource(),
                                                        EntityArgument.getPlayers(commandContext, "targets"),
                                                        AdvancementCommands.Action.GRANT,
                                                        getAdvancements(
                                                            commandContext,
                                                            ResourceKeyArgument.getAdvancement(commandContext, "advancement"),
                                                            AdvancementCommands.Mode.ONLY
                                                        )
                                                    )
                                                )
                                                .then(
                                                    Commands.argument("criterion", StringArgumentType.greedyString())
                                                        .suggests(
                                                            (commandContext, suggestionsBuilder) -> SharedSuggestionProvider.suggest(
                                                                ResourceKeyArgument.getAdvancement(commandContext, "advancement").value().criteria().keySet(),
                                                                suggestionsBuilder
                                                            )
                                                        )
                                                        .executes(
                                                            context -> performCriterion(
                                                                context.getSource(),
                                                                EntityArgument.getPlayers(context, "targets"),
                                                                AdvancementCommands.Action.GRANT,
                                                                ResourceKeyArgument.getAdvancement(context, "advancement"),
                                                                StringArgumentType.getString(context, "criterion")
                                                            )
                                                        )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("from")
                                        .then(
                                            Commands.argument("advancement", ResourceKeyArgument.key(Registries.ADVANCEMENT))
                                                .executes(
                                                    context -> perform(
                                                        context.getSource(),
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        AdvancementCommands.Action.GRANT,
                                                        getAdvancements(
                                                            context, ResourceKeyArgument.getAdvancement(context, "advancement"), AdvancementCommands.Mode.FROM
                                                        )
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("until")
                                        .then(
                                            Commands.argument("advancement", ResourceKeyArgument.key(Registries.ADVANCEMENT))
                                                .executes(
                                                    context -> perform(
                                                        context.getSource(),
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        AdvancementCommands.Action.GRANT,
                                                        getAdvancements(
                                                            context, ResourceKeyArgument.getAdvancement(context, "advancement"), AdvancementCommands.Mode.UNTIL
                                                        )
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("through")
                                        .then(
                                            Commands.argument("advancement", ResourceKeyArgument.key(Registries.ADVANCEMENT))
                                                .executes(
                                                    context -> perform(
                                                        context.getSource(),
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        AdvancementCommands.Action.GRANT,
                                                        getAdvancements(
                                                            context,
                                                            ResourceKeyArgument.getAdvancement(context, "advancement"),
                                                            AdvancementCommands.Mode.THROUGH
                                                        )
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("everything")
                                        .executes(
                                            context -> perform(
                                                context.getSource(),
                                                EntityArgument.getPlayers(context, "targets"),
                                                AdvancementCommands.Action.GRANT,
                                                context.getSource().getServer().getAdvancements().getAllAdvancements(),
                                                false
                                            )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("revoke")
                        .then(
                            Commands.argument("targets", EntityArgument.players())
                                .then(
                                    Commands.literal("only")
                                        .then(
                                            Commands.argument("advancement", ResourceKeyArgument.key(Registries.ADVANCEMENT))
                                                .executes(
                                                    context -> perform(
                                                        context.getSource(),
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        AdvancementCommands.Action.REVOKE,
                                                        getAdvancements(
                                                            context, ResourceKeyArgument.getAdvancement(context, "advancement"), AdvancementCommands.Mode.ONLY
                                                        )
                                                    )
                                                )
                                                .then(
                                                    Commands.argument("criterion", StringArgumentType.greedyString())
                                                        .suggests(
                                                            (commandContext, suggestionsBuilder) -> SharedSuggestionProvider.suggest(
                                                                ResourceKeyArgument.getAdvancement(commandContext, "advancement").value().criteria().keySet(),
                                                                suggestionsBuilder
                                                            )
                                                        )
                                                        .executes(
                                                            context -> performCriterion(
                                                                context.getSource(),
                                                                EntityArgument.getPlayers(context, "targets"),
                                                                AdvancementCommands.Action.REVOKE,
                                                                ResourceKeyArgument.getAdvancement(context, "advancement"),
                                                                StringArgumentType.getString(context, "criterion")
                                                            )
                                                        )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("from")
                                        .then(
                                            Commands.argument("advancement", ResourceKeyArgument.key(Registries.ADVANCEMENT))
                                                .executes(
                                                    context -> perform(
                                                        context.getSource(),
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        AdvancementCommands.Action.REVOKE,
                                                        getAdvancements(
                                                            context, ResourceKeyArgument.getAdvancement(context, "advancement"), AdvancementCommands.Mode.FROM
                                                        )
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("until")
                                        .then(
                                            Commands.argument("advancement", ResourceKeyArgument.key(Registries.ADVANCEMENT))
                                                .executes(
                                                    context -> perform(
                                                        context.getSource(),
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        AdvancementCommands.Action.REVOKE,
                                                        getAdvancements(
                                                            context, ResourceKeyArgument.getAdvancement(context, "advancement"), AdvancementCommands.Mode.UNTIL
                                                        )
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("through")
                                        .then(
                                            Commands.argument("advancement", ResourceKeyArgument.key(Registries.ADVANCEMENT))
                                                .executes(
                                                    context -> perform(
                                                        context.getSource(),
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        AdvancementCommands.Action.REVOKE,
                                                        getAdvancements(
                                                            context,
                                                            ResourceKeyArgument.getAdvancement(context, "advancement"),
                                                            AdvancementCommands.Mode.THROUGH
                                                        )
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("everything")
                                        .executes(
                                            context -> perform(
                                                context.getSource(),
                                                EntityArgument.getPlayers(context, "targets"),
                                                AdvancementCommands.Action.REVOKE,
                                                context.getSource().getServer().getAdvancements().getAllAdvancements()
                                            )
                                        )
                                )
                        )
                )
        );
    }

    private static int perform(
        CommandSourceStack source, Collection<ServerPlayer> targets, AdvancementCommands.Action action, Collection<AdvancementHolder> advancements
    ) throws CommandSyntaxException {
        return perform(source, targets, action, advancements, true);
    }

    private static int perform(
        CommandSourceStack source,
        Collection<ServerPlayer> targets,
        AdvancementCommands.Action action,
        Collection<AdvancementHolder> advancements,
        boolean grantEverything
    ) throws CommandSyntaxException {
        int i = 0;

        for (ServerPlayer serverPlayer : targets) {
            // Folia start - region threading
            i += 1;
            serverPlayer.getBukkitEntity().taskScheduler.schedule((ServerPlayer player) -> {
                action.perform(player, advancements, grantEverything);
            }, null, 1L);
            // Folia end - region threading
        }

        if (i == 0) {
            if (advancements.size() == 1) {
                if (targets.size() == 1) {
                    throw ERROR_NO_ACTION_PERFORMED.create(
                        Component.translatable(
                            action.getKey() + ".one.to.one.failure",
                            Advancement.name(advancements.iterator().next()),
                            targets.iterator().next().getDisplayName()
                        )
                    );
                } else {
                    throw ERROR_NO_ACTION_PERFORMED.create(
                        Component.translatable(action.getKey() + ".one.to.many.failure", Advancement.name(advancements.iterator().next()), targets.size())
                    );
                }
            } else if (targets.size() == 1) {
                throw ERROR_NO_ACTION_PERFORMED.create(
                    Component.translatable(action.getKey() + ".many.to.one.failure", advancements.size(), targets.iterator().next().getDisplayName())
                );
            } else {
                throw ERROR_NO_ACTION_PERFORMED.create(Component.translatable(action.getKey() + ".many.to.many.failure", advancements.size(), targets.size()));
            }
        } else {
            if (advancements.size() == 1) {
                if (targets.size() == 1) {
                    source.sendSuccess(
                        () -> Component.translatable(
                            action.getKey() + ".one.to.one.success",
                            Advancement.name(advancements.iterator().next()),
                            targets.iterator().next().getDisplayName()
                        ),
                        true
                    );
                } else {
                    source.sendSuccess(
                        () -> Component.translatable(action.getKey() + ".one.to.many.success", Advancement.name(advancements.iterator().next()), targets.size()),
                        true
                    );
                }
            } else if (targets.size() == 1) {
                source.sendSuccess(
                    () -> Component.translatable(action.getKey() + ".many.to.one.success", advancements.size(), targets.iterator().next().getDisplayName()),
                    true
                );
            } else {
                source.sendSuccess(() -> Component.translatable(action.getKey() + ".many.to.many.success", advancements.size(), targets.size()), true);
            }

            return i;
        }
    }

    private static int performCriterion(
        CommandSourceStack source, Collection<ServerPlayer> targets, AdvancementCommands.Action action, AdvancementHolder advancement, String criterionName
    ) throws CommandSyntaxException {
        int i = 0;
        Advancement advancement1 = advancement.value();
        if (!advancement1.criteria().containsKey(criterionName)) {
            throw ERROR_CRITERION_NOT_FOUND.create(Advancement.name(advancement), criterionName);
        } else {
            for (ServerPlayer serverPlayer : targets) {
                // Folia start - region threading
                ++i;
                serverPlayer.getBukkitEntity().taskScheduler.schedule((ServerPlayer player) -> {
                    action.performCriterion(player, advancement, criterionName);
                }, null, 1L);
                // Folia end - region threading
            }

            if (i == 0) {
                if (targets.size() == 1) {
                    throw ERROR_NO_ACTION_PERFORMED.create(
                        Component.translatable(
                            action.getKey() + ".criterion.to.one.failure",
                            criterionName,
                            Advancement.name(advancement),
                            targets.iterator().next().getDisplayName()
                        )
                    );
                } else {
                    throw ERROR_NO_ACTION_PERFORMED.create(
                        Component.translatable(action.getKey() + ".criterion.to.many.failure", criterionName, Advancement.name(advancement), targets.size())
                    );
                }
            } else {
                if (targets.size() == 1) {
                    source.sendSuccess(
                        () -> Component.translatable(
                            action.getKey() + ".criterion.to.one.success",
                            criterionName,
                            Advancement.name(advancement),
                            targets.iterator().next().getDisplayName()
                        ),
                        true
                    );
                } else {
                    source.sendSuccess(
                        () -> Component.translatable(
                            action.getKey() + ".criterion.to.many.success", criterionName, Advancement.name(advancement), targets.size()
                        ),
                        true
                    );
                }

                return i;
            }
        }
    }

    private static List<AdvancementHolder> getAdvancements(
        CommandContext<CommandSourceStack> context, AdvancementHolder advancement, AdvancementCommands.Mode mode
    ) {
        AdvancementTree advancementTree = context.getSource().getServer().getAdvancements().tree();
        AdvancementNode advancementNode = advancementTree.get(advancement);
        if (advancementNode == null) {
            return List.of(advancement);
        } else {
            List<AdvancementHolder> list = new ArrayList<>();
            if (mode.parents) {
                for (AdvancementNode advancementNode1 = advancementNode.parent(); advancementNode1 != null; advancementNode1 = advancementNode1.parent()) {
                    list.add(advancementNode1.holder());
                }
            }

            list.add(advancement);
            if (mode.children) {
                addChildren(advancementNode, list);
            }

            return list;
        }
    }

    private static void addChildren(AdvancementNode node, List<AdvancementHolder> output) {
        for (AdvancementNode advancementNode : node.children()) {
            output.add(advancementNode.holder());
            addChildren(advancementNode, output);
        }
    }

    static enum Action {
        GRANT("grant") {
            @Override
            protected boolean perform(ServerPlayer player, AdvancementHolder advancement) {
                AdvancementProgress orStartProgress = player.getAdvancements().getOrStartProgress(advancement);
                if (orStartProgress.isDone()) {
                    return false;
                } else {
                    for (String string : orStartProgress.getRemainingCriteria()) {
                        player.getAdvancements().award(advancement, string);
                    }

                    return true;
                }
            }

            @Override
            protected boolean performCriterion(ServerPlayer player, AdvancementHolder advancement, String criterionName) {
                return player.getAdvancements().award(advancement, criterionName);
            }
        },
        REVOKE("revoke") {
            @Override
            protected boolean perform(ServerPlayer player, AdvancementHolder advancement) {
                AdvancementProgress orStartProgress = player.getAdvancements().getOrStartProgress(advancement);
                if (!orStartProgress.hasProgress()) {
                    return false;
                } else {
                    for (String string : orStartProgress.getCompletedCriteria()) {
                        player.getAdvancements().revoke(advancement, string);
                    }

                    return true;
                }
            }

            @Override
            protected boolean performCriterion(ServerPlayer player, AdvancementHolder advancement, String criterionName) {
                return player.getAdvancements().revoke(advancement, criterionName);
            }
        };

        private final String key;

        Action(final String key) {
            this.key = "commands.advancement." + key;
        }

        public int perform(ServerPlayer player, Iterable<AdvancementHolder> advancements, boolean grantEverything) {
            int i = 0;
            if (!grantEverything) {
                player.getAdvancements().flushDirty(player, true);
            }

            for (AdvancementHolder advancementHolder : advancements) {
                if (this.perform(player, advancementHolder)) {
                    i++;
                }
            }

            if (!grantEverything) {
                player.getAdvancements().flushDirty(player, false);
            }

            return i;
        }

        protected abstract boolean perform(ServerPlayer player, AdvancementHolder advancement);

        protected abstract boolean performCriterion(ServerPlayer player, AdvancementHolder advancement, String criterionName);

        protected String getKey() {
            return this.key;
        }
    }

    static enum Mode {
        ONLY(false, false),
        THROUGH(true, true),
        FROM(false, true),
        UNTIL(true, false),
        EVERYTHING(true, true);

        final boolean parents;
        final boolean children;

        private Mode(final boolean parents, final boolean children) {
            this.parents = parents;
            this.children = children;
        }
    }
}
