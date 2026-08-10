package net.minecraft.commands.execution.tasks;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.context.ContextChain.Stage;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.execution.CommandQueueEntry;
import net.minecraft.commands.execution.CustomCommandExecutor;
import net.minecraft.commands.execution.CustomModifierExecutor;
import net.minecraft.commands.execution.EntryAction;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.ExecutionControl;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.TraceCallbacks;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.network.chat.Component;

public class BuildContexts<T extends ExecutionCommandSource<T>> {
    @VisibleForTesting
    public static final DynamicCommandExceptionType ERROR_FORK_LIMIT_REACHED = new DynamicCommandExceptionType(
        forkLimit -> Component.translatableEscape("command.forkLimit", forkLimit)
    );
    private final String commandInput;
    private final ContextChain<T> command;

    public BuildContexts(String commandInput, ContextChain<T> command) {
        this.commandInput = commandInput;
        this.command = command;
    }

    protected void execute(T originalSource, List<T> sources, ExecutionContext<T> context, Frame frame, ChainModifiers chainModifiers) {
        ContextChain<T> contextChain = this.command;
        ChainModifiers chainModifiers1 = chainModifiers;
        List<T> list = sources;
        if (contextChain.getStage() != Stage.EXECUTE) {
            context.profiler().push(() -> "prepare " + this.commandInput);

            try {
                for (int forkLimit = context.forkLimit(); contextChain.getStage() != Stage.EXECUTE; contextChain = contextChain.nextStage()) {
                    CommandContext<T> topContext = contextChain.getTopContext();
                    if (topContext.isForked()) {
                        chainModifiers1 = chainModifiers1.setForked();
                    }

                    RedirectModifier<T> redirectModifier = topContext.getRedirectModifier();
                    if (redirectModifier instanceof CustomModifierExecutor.ModifierAdapter<T> customModifierExecutor) {
                        customModifierExecutor.apply(originalSource, list, contextChain, chainModifiers1, ExecutionControl.create(context, frame));
                        return;
                    }

                    if (redirectModifier != null) {
                        context.incrementCost();
                        boolean isForked = chainModifiers1.isForked();
                        List<T> list1 = new ObjectArrayList<>();

                        for (T executionCommandSource : list) {
                            try {
                                Collection<T> collection = ContextChain.runModifier(
                                    topContext, executionCommandSource, (commandContext, flag, i) -> {}, isForked
                                );
                                if (list1.size() + collection.size() >= forkLimit) {
                                    originalSource.handleError(ERROR_FORK_LIMIT_REACHED.create(forkLimit), isForked, context.tracer());
                                    return;
                                }

                                list1.addAll(collection);
                            } catch (CommandSyntaxException var20) {
                                executionCommandSource.handleError(var20, isForked, context.tracer());
                                if (!isForked) {
                                    return;
                                }
                            }
                        }

                        list = list1;
                    }
                }
            } finally {
                context.profiler().pop();
            }
        }

        if (list.isEmpty()) {
            if (chainModifiers1.isReturn()) {
                context.queueNext(new CommandQueueEntry<T>(frame, FallthroughTask.instance()));
            }
        } else {
            CommandContext<T> topContext1 = contextChain.getTopContext();
            if (topContext1.getCommand() instanceof CustomCommandExecutor.CommandAdapter<T> customCommandExecutor) {
                ExecutionControl<T> executionControl = ExecutionControl.create(context, frame);

                for (T executionCommandSource1 : list) {
                    customCommandExecutor.run(executionCommandSource1, contextChain, chainModifiers1, executionControl);
                }
            } else {
                if (chainModifiers1.isReturn()) {
                    T executionCommandSource2 = list.get(0);
                    executionCommandSource2 = executionCommandSource2.withCallback(
                        CommandResultCallback.chain(executionCommandSource2.callback(), frame.returnValueConsumer())
                    );
                    list = List.of(executionCommandSource2);
                }

                ExecuteCommand<T> executeCommand = new ExecuteCommand<>(this.commandInput, chainModifiers1, topContext1);
                ContinuationTask.schedule(context, frame, list, (frame1, argument) -> new CommandQueueEntry<>(frame1, executeCommand.bind(argument)));
            }
        }
    }

    protected void traceCommandStart(ExecutionContext<T> executionContext, Frame frame) {
        TraceCallbacks traceCallbacks = executionContext.tracer();
        if (traceCallbacks != null) {
            traceCallbacks.onCommand(frame.depth(), this.commandInput);
        }
    }

    @Override
    public String toString() {
        return this.commandInput;
    }

    public static class Continuation<T extends ExecutionCommandSource<T>> extends BuildContexts<T> implements EntryAction<T> {
        private final ChainModifiers modifiers;
        private final T originalSource;
        private final List<T> sources;

        public Continuation(String commandInput, ContextChain<T> command, ChainModifiers modifiers, T originalSource, List<T> sources) {
            super(commandInput, command);
            this.originalSource = originalSource;
            this.sources = sources;
            this.modifiers = modifiers;
        }

        @Override
        public void execute(ExecutionContext<T> context, Frame frame) {
            this.execute(this.originalSource, this.sources, context, frame, this.modifiers);
        }
    }

    public static class TopLevel<T extends ExecutionCommandSource<T>> extends BuildContexts<T> implements EntryAction<T> {
        private final T source;

        public TopLevel(String commandInput, ContextChain<T> command, T source) {
            super(commandInput, command);
            this.source = source;
        }

        @Override
        public void execute(ExecutionContext<T> context, Frame frame) {
            this.traceCommandStart(context, frame);
            this.execute(this.source, List.of(this.source), context, frame, ChainModifiers.DEFAULT);
        }
    }

    public static class Unbound<T extends ExecutionCommandSource<T>> extends BuildContexts<T> implements UnboundEntryAction<T> {
        public Unbound(String commandInput, ContextChain<T> command) {
            super(commandInput, command);
        }

        @Override
        public void execute(T source, ExecutionContext<T> executionContext, Frame frame) {
            this.traceCommandStart(executionContext, frame);
            this.execute(source, List.of(source), executionContext, frame, ChainModifiers.DEFAULT);
        }
    }
}
