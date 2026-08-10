package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.minecraft.world.level.gamerules.GameRules;

public class GameRuleCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext commandBuildContext) {
        final LiteralArgumentBuilder<CommandSourceStack> literalArgumentBuilder = Commands.literal("gamerule")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
        new GameRules(commandBuildContext.enabledFeatures())
            .visitGameRuleTypes(
                new GameRuleTypeVisitor() {
                    @Override
                    public <T> void visit(GameRule<T> rule) {
                        LiteralArgumentBuilder<CommandSourceStack> literalArgumentBuilder1 = Commands.literal(rule.id());
                        LiteralArgumentBuilder<CommandSourceStack> literalArgumentBuilder2 = Commands.literal(rule.getIdentifier().toString());
                        literalArgumentBuilder.then(GameRuleCommand.buildRuleArguments(rule, literalArgumentBuilder1))
                            .then(GameRuleCommand.buildRuleArguments(rule, literalArgumentBuilder2));
                    }
                }
            );
        dispatcher.register(literalArgumentBuilder);
    }

    static <T> LiteralArgumentBuilder<CommandSourceStack> buildRuleArguments(GameRule<T> rule, LiteralArgumentBuilder<CommandSourceStack> ruleLiteral) {
        return ruleLiteral.executes(context -> queryRule(context.getSource(), rule))
            .then(Commands.argument("value", rule.argument()).executes(context -> setRule(context, rule)));
    }

    private static <T> int setRule(CommandContext<CommandSourceStack> context, GameRule<T> rule) {
        CommandSourceStack commandSourceStack = context.getSource();
        T argument = org.bukkit.craftbukkit.event.CraftEventFactory.handleGameRuleSet(rule, context.getArgument("value", rule.valueClass()), commandSourceStack.getLevel(), context.getSource().getBukkitSender()).value(); // Paper - per-world game rules and event
        commandSourceStack.sendSuccess(() -> Component.translatable("commands.gamerule.set", rule.id(), rule.serialize(argument)), true);
        return rule.getCommandResult(argument);
    }

    private static <T> int queryRule(CommandSourceStack source, GameRule<T> rule) {
        T object = source.getLevel().getGameRules().get(rule);
        source.sendSuccess(() -> Component.translatable("commands.gamerule.query", rule.id(), rule.serialize(object)), false);
        return rule.getCommandResult(object);
    }
}
