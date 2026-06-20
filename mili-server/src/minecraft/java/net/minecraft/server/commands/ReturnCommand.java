package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.ContextChain;
import java.util.List;
import net.minecraft.commands.Commands;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.execution.CustomCommandExecutor;
import net.minecraft.commands.execution.CustomModifierExecutor;
import net.minecraft.commands.execution.ExecutionControl;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.tasks.BuildContexts;
import net.minecraft.commands.execution.tasks.FallthroughTask;

public class ReturnCommand {
    public static <T extends ExecutionCommandSource<T>> void register(CommandDispatcher<T> dispatcher) {
        dispatcher.register(
            LiteralArgumentBuilder.<T>literal("return")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    RequiredArgumentBuilder.<T, Integer>argument("value", IntegerArgumentType.integer())
                        .executes(new ReturnCommand.ReturnValueCustomExecutor<>())
                )
                .then(LiteralArgumentBuilder.<T>literal("fail").executes(new ReturnCommand.ReturnFailCustomExecutor<>()))
                .then(LiteralArgumentBuilder.<T>literal("run").forward(dispatcher.getRoot(), new ReturnCommand.ReturnFromCommandCustomModifier<>(), false))
        );
    }

    static class ReturnFailCustomExecutor<T extends ExecutionCommandSource<T>> implements CustomCommandExecutor.CommandAdapter<T> {
        @Override
        public void run(T source, ContextChain<T> contextChain, ChainModifiers chainModifiers, ExecutionControl<T> executionControl) {
            source.callback().onFailure();
            Frame frame = executionControl.currentFrame();
            frame.returnFailure();
            frame.discard();
        }
    }

    static class ReturnFromCommandCustomModifier<T extends ExecutionCommandSource<T>> implements CustomModifierExecutor.ModifierAdapter<T> {
        @Override
        public void apply(T originalSource, List<T> sources, ContextChain<T> contextChain, ChainModifiers chainModifiers, ExecutionControl<T> executionControl) {
            if (sources.isEmpty()) {
                if (chainModifiers.isReturn()) {
                    executionControl.queueNext(FallthroughTask.instance());
                }
            } else {
                executionControl.currentFrame().discard();
                ContextChain<T> contextChain1 = contextChain.nextStage();
                String input = contextChain1.getTopContext().getInput();
                executionControl.queueNext(new BuildContexts.Continuation<>(input, contextChain1, chainModifiers.setReturn(), originalSource, sources));
            }
        }
    }

    static class ReturnValueCustomExecutor<T extends ExecutionCommandSource<T>> implements CustomCommandExecutor.CommandAdapter<T> {
        @Override
        public void run(T source, ContextChain<T> contextChain, ChainModifiers chainModifiers, ExecutionControl<T> executionControl) {
            int integer = IntegerArgumentType.getInteger(contextChain.getTopContext(), "value");
            source.callback().onSuccess(integer);
            Frame frame = executionControl.currentFrame();
            frame.returnSuccess(integer);
            frame.discard();
        }
    }
}
