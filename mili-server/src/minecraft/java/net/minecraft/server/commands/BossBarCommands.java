package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Collection;
import java.util.Collections;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.player.Player;

public class BossBarCommands {
    private static final DynamicCommandExceptionType ERROR_ALREADY_EXISTS = new DynamicCommandExceptionType(
        bossBarId -> Component.translatableEscape("commands.bossbar.create.failed", bossBarId)
    );
    private static final DynamicCommandExceptionType ERROR_DOESNT_EXIST = new DynamicCommandExceptionType(
        bossBarId -> Component.translatableEscape("commands.bossbar.unknown", bossBarId)
    );
    private static final SimpleCommandExceptionType ERROR_NO_PLAYER_CHANGE = new SimpleCommandExceptionType(
        Component.translatable("commands.bossbar.set.players.unchanged")
    );
    private static final SimpleCommandExceptionType ERROR_NO_NAME_CHANGE = new SimpleCommandExceptionType(
        Component.translatable("commands.bossbar.set.name.unchanged")
    );
    private static final SimpleCommandExceptionType ERROR_NO_COLOR_CHANGE = new SimpleCommandExceptionType(
        Component.translatable("commands.bossbar.set.color.unchanged")
    );
    private static final SimpleCommandExceptionType ERROR_NO_STYLE_CHANGE = new SimpleCommandExceptionType(
        Component.translatable("commands.bossbar.set.style.unchanged")
    );
    private static final SimpleCommandExceptionType ERROR_NO_VALUE_CHANGE = new SimpleCommandExceptionType(
        Component.translatable("commands.bossbar.set.value.unchanged")
    );
    private static final SimpleCommandExceptionType ERROR_NO_MAX_CHANGE = new SimpleCommandExceptionType(
        Component.translatable("commands.bossbar.set.max.unchanged")
    );
    private static final SimpleCommandExceptionType ERROR_ALREADY_HIDDEN = new SimpleCommandExceptionType(
        Component.translatable("commands.bossbar.set.visibility.unchanged.hidden")
    );
    private static final SimpleCommandExceptionType ERROR_ALREADY_VISIBLE = new SimpleCommandExceptionType(
        Component.translatable("commands.bossbar.set.visibility.unchanged.visible")
    );
    public static final SuggestionProvider<CommandSourceStack> SUGGEST_BOSS_BAR = (context, builder) -> SharedSuggestionProvider.suggestResource(
        context.getSource().getServer().getCustomBossEvents().getIds(), builder
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("bossbar")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.literal("add")
                        .then(
                            Commands.argument("id", IdentifierArgument.id())
                                .then(
                                    Commands.argument("name", ComponentArgument.textComponent(context))
                                        .executes(
                                            context1 -> createBar(
                                                context1.getSource(),
                                                IdentifierArgument.getId(context1, "id"),
                                                ComponentArgument.getResolvedComponent(context1, "name")
                                            )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("remove")
                        .then(
                            Commands.argument("id", IdentifierArgument.id())
                                .suggests(SUGGEST_BOSS_BAR)
                                .executes(context1 -> removeBar(context1.getSource(), getBossBar(context1)))
                        )
                )
                .then(Commands.literal("list").executes(context1 -> listBars(context1.getSource())))
                .then(
                    Commands.literal("set")
                        .then(
                            Commands.argument("id", IdentifierArgument.id())
                                .suggests(SUGGEST_BOSS_BAR)
                                .then(
                                    Commands.literal("name")
                                        .then(
                                            Commands.argument("name", ComponentArgument.textComponent(context))
                                                .executes(
                                                    context1 -> setName(
                                                        context1.getSource(), getBossBar(context1), ComponentArgument.getResolvedComponent(context1, "name")
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("color")
                                        .then(
                                            Commands.literal("pink")
                                                .executes(context1 -> setColor(context1.getSource(), getBossBar(context1), BossEvent.BossBarColor.PINK))
                                        )
                                        .then(
                                            Commands.literal("blue")
                                                .executes(context1 -> setColor(context1.getSource(), getBossBar(context1), BossEvent.BossBarColor.BLUE))
                                        )
                                        .then(
                                            Commands.literal("red")
                                                .executes(context1 -> setColor(context1.getSource(), getBossBar(context1), BossEvent.BossBarColor.RED))
                                        )
                                        .then(
                                            Commands.literal("green")
                                                .executes(context1 -> setColor(context1.getSource(), getBossBar(context1), BossEvent.BossBarColor.GREEN))
                                        )
                                        .then(
                                            Commands.literal("yellow")
                                                .executes(context1 -> setColor(context1.getSource(), getBossBar(context1), BossEvent.BossBarColor.YELLOW))
                                        )
                                        .then(
                                            Commands.literal("purple")
                                                .executes(context1 -> setColor(context1.getSource(), getBossBar(context1), BossEvent.BossBarColor.PURPLE))
                                        )
                                        .then(
                                            Commands.literal("white")
                                                .executes(context1 -> setColor(context1.getSource(), getBossBar(context1), BossEvent.BossBarColor.WHITE))
                                        )
                                )
                                .then(
                                    Commands.literal("style")
                                        .then(
                                            Commands.literal("progress")
                                                .executes(context1 -> setStyle(context1.getSource(), getBossBar(context1), BossEvent.BossBarOverlay.PROGRESS))
                                        )
                                        .then(
                                            Commands.literal("notched_6")
                                                .executes(context1 -> setStyle(context1.getSource(), getBossBar(context1), BossEvent.BossBarOverlay.NOTCHED_6))
                                        )
                                        .then(
                                            Commands.literal("notched_10")
                                                .executes(context1 -> setStyle(context1.getSource(), getBossBar(context1), BossEvent.BossBarOverlay.NOTCHED_10))
                                        )
                                        .then(
                                            Commands.literal("notched_12")
                                                .executes(context1 -> setStyle(context1.getSource(), getBossBar(context1), BossEvent.BossBarOverlay.NOTCHED_12))
                                        )
                                        .then(
                                            Commands.literal("notched_20")
                                                .executes(context1 -> setStyle(context1.getSource(), getBossBar(context1), BossEvent.BossBarOverlay.NOTCHED_20))
                                        )
                                )
                                .then(
                                    Commands.literal("value")
                                        .then(
                                            Commands.argument("value", IntegerArgumentType.integer(0))
                                                .executes(
                                                    context1 -> setValue(
                                                        context1.getSource(), getBossBar(context1), IntegerArgumentType.getInteger(context1, "value")
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("max")
                                        .then(
                                            Commands.argument("max", IntegerArgumentType.integer(1))
                                                .executes(
                                                    context1 -> setMax(
                                                        context1.getSource(), getBossBar(context1), IntegerArgumentType.getInteger(context1, "max")
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("visible")
                                        .then(
                                            Commands.argument("visible", BoolArgumentType.bool())
                                                .executes(
                                                    context1 -> setVisible(
                                                        context1.getSource(), getBossBar(context1), BoolArgumentType.getBool(context1, "visible")
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("players")
                                        .executes(context1 -> setPlayers(context1.getSource(), getBossBar(context1), Collections.emptyList()))
                                        .then(
                                            Commands.argument("targets", EntityArgument.players())
                                                .executes(
                                                    context1 -> setPlayers(
                                                        context1.getSource(), getBossBar(context1), EntityArgument.getOptionalPlayers(context1, "targets")
                                                    )
                                                )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("get")
                        .then(
                            Commands.argument("id", IdentifierArgument.id())
                                .suggests(SUGGEST_BOSS_BAR)
                                .then(Commands.literal("value").executes(context1 -> getValue(context1.getSource(), getBossBar(context1))))
                                .then(Commands.literal("max").executes(context1 -> getMax(context1.getSource(), getBossBar(context1))))
                                .then(Commands.literal("visible").executes(context1 -> getVisible(context1.getSource(), getBossBar(context1))))
                                .then(Commands.literal("players").executes(context1 -> getPlayers(context1.getSource(), getBossBar(context1))))
                        )
                )
        );
    }

    private static int getValue(CommandSourceStack source, CustomBossEvent bossbar) {
        source.sendSuccess(() -> Component.translatable("commands.bossbar.get.value", bossbar.getDisplayName(), bossbar.getValue()), true);
        return bossbar.getValue();
    }

    private static int getMax(CommandSourceStack source, CustomBossEvent bossbar) {
        source.sendSuccess(() -> Component.translatable("commands.bossbar.get.max", bossbar.getDisplayName(), bossbar.getMax()), true);
        return bossbar.getMax();
    }

    private static int getVisible(CommandSourceStack source, CustomBossEvent bossbar) {
        if (bossbar.isVisible()) {
            source.sendSuccess(() -> Component.translatable("commands.bossbar.get.visible.visible", bossbar.getDisplayName()), true);
            return 1;
        } else {
            source.sendSuccess(() -> Component.translatable("commands.bossbar.get.visible.hidden", bossbar.getDisplayName()), true);
            return 0;
        }
    }

    private static int getPlayers(CommandSourceStack source, CustomBossEvent bossbar) {
        if (bossbar.getPlayers().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.bossbar.get.players.none", bossbar.getDisplayName()), true);
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.bossbar.get.players.some",
                    bossbar.getDisplayName(),
                    bossbar.getPlayers().size(),
                    ComponentUtils.formatList(bossbar.getPlayers(), Player::getDisplayName)
                ),
                true
            );
        }

        return bossbar.getPlayers().size();
    }

    private static int setVisible(CommandSourceStack source, CustomBossEvent bossbar, boolean visible) throws CommandSyntaxException {
        if (bossbar.isVisible() == visible) {
            if (visible) {
                throw ERROR_ALREADY_VISIBLE.create();
            } else {
                throw ERROR_ALREADY_HIDDEN.create();
            }
        } else {
            bossbar.setVisible(visible);
            if (visible) {
                source.sendSuccess(() -> Component.translatable("commands.bossbar.set.visible.success.visible", bossbar.getDisplayName()), true);
            } else {
                source.sendSuccess(() -> Component.translatable("commands.bossbar.set.visible.success.hidden", bossbar.getDisplayName()), true);
            }

            return 0;
        }
    }

    private static int setValue(CommandSourceStack source, CustomBossEvent bossbar, int value) throws CommandSyntaxException {
        if (bossbar.getValue() == value) {
            throw ERROR_NO_VALUE_CHANGE.create();
        } else {
            bossbar.setValue(value);
            source.sendSuccess(() -> Component.translatable("commands.bossbar.set.value.success", bossbar.getDisplayName(), value), true);
            return value;
        }
    }

    private static int setMax(CommandSourceStack source, CustomBossEvent bossbar, int max) throws CommandSyntaxException {
        if (bossbar.getMax() == max) {
            throw ERROR_NO_MAX_CHANGE.create();
        } else {
            bossbar.setMax(max);
            source.sendSuccess(() -> Component.translatable("commands.bossbar.set.max.success", bossbar.getDisplayName(), max), true);
            return max;
        }
    }

    private static int setColor(CommandSourceStack source, CustomBossEvent bossbar, BossEvent.BossBarColor color) throws CommandSyntaxException {
        if (bossbar.getColor().equals(color)) {
            throw ERROR_NO_COLOR_CHANGE.create();
        } else {
            bossbar.setColor(color);
            source.sendSuccess(() -> Component.translatable("commands.bossbar.set.color.success", bossbar.getDisplayName()), true);
            return 0;
        }
    }

    private static int setStyle(CommandSourceStack source, CustomBossEvent bossbar, BossEvent.BossBarOverlay style) throws CommandSyntaxException {
        if (bossbar.getOverlay().equals(style)) {
            throw ERROR_NO_STYLE_CHANGE.create();
        } else {
            bossbar.setOverlay(style);
            source.sendSuccess(() -> Component.translatable("commands.bossbar.set.style.success", bossbar.getDisplayName()), true);
            return 0;
        }
    }

    private static int setName(CommandSourceStack source, CustomBossEvent bossbar, Component name) throws CommandSyntaxException {
        Component component = ComponentUtils.updateForEntity(source, name, null, 0);
        if (bossbar.getName().equals(component)) {
            throw ERROR_NO_NAME_CHANGE.create();
        } else {
            bossbar.setName(component);
            source.sendSuccess(() -> Component.translatable("commands.bossbar.set.name.success", bossbar.getDisplayName()), true);
            return 0;
        }
    }

    private static int setPlayers(CommandSourceStack source, CustomBossEvent bossbar, Collection<ServerPlayer> players) throws CommandSyntaxException {
        boolean flag = bossbar.setPlayers(players);
        if (!flag) {
            throw ERROR_NO_PLAYER_CHANGE.create();
        } else {
            if (bossbar.getPlayers().isEmpty()) {
                source.sendSuccess(() -> Component.translatable("commands.bossbar.set.players.success.none", bossbar.getDisplayName()), true);
            } else {
                source.sendSuccess(
                    () -> Component.translatable(
                        "commands.bossbar.set.players.success.some",
                        bossbar.getDisplayName(),
                        players.size(),
                        ComponentUtils.formatList(players, Player::getDisplayName)
                    ),
                    true
                );
            }

            return bossbar.getPlayers().size();
        }
    }

    private static int listBars(CommandSourceStack source) {
        Collection<CustomBossEvent> events = source.getServer().getCustomBossEvents().getEvents();
        if (events.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.bossbar.list.bars.none"), false);
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.bossbar.list.bars.some", events.size(), ComponentUtils.formatList(events, CustomBossEvent::getDisplayName)
                ),
                false
            );
        }

        return events.size();
    }

    private static int createBar(CommandSourceStack source, Identifier id, Component displayName) throws CommandSyntaxException {
        CustomBossEvents customBossEvents = source.getServer().getCustomBossEvents();
        if (customBossEvents.get(id) != null) {
            throw ERROR_ALREADY_EXISTS.create(id.toString());
        } else {
            CustomBossEvent customBossEvent = customBossEvents.create(id, ComponentUtils.updateForEntity(source, displayName, null, 0));
            source.sendSuccess(() -> Component.translatable("commands.bossbar.create.success", customBossEvent.getDisplayName()), true);
            return customBossEvents.getEvents().size();
        }
    }

    private static int removeBar(CommandSourceStack source, CustomBossEvent bossbar) {
        CustomBossEvents customBossEvents = source.getServer().getCustomBossEvents();
        bossbar.removeAllPlayers();
        customBossEvents.remove(bossbar);
        source.sendSuccess(() -> Component.translatable("commands.bossbar.remove.success", bossbar.getDisplayName()), true);
        return customBossEvents.getEvents().size();
    }

    public static CustomBossEvent getBossBar(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Identifier id = IdentifierArgument.getId(context, "id");
        CustomBossEvent customBossEvent = context.getSource().getServer().getCustomBossEvents().get(id);
        if (customBossEvent == null) {
            throw ERROR_DOESNT_EXIST.create(id.toString());
        } else {
            return customBossEvent;
        }
    }
}
