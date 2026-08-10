package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Locale;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.execution.CustomCommandExecutor;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.ExecutionControl;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.TraceCallbacks;
import net.minecraft.commands.execution.tasks.CallFunction;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.ProfileResults;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

public class DebugCommand {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final SimpleCommandExceptionType ERROR_NOT_RUNNING = new SimpleCommandExceptionType(Component.translatable("commands.debug.notRunning"));
    private static final SimpleCommandExceptionType ERROR_ALREADY_RUNNING = new SimpleCommandExceptionType(
        Component.translatable("commands.debug.alreadyRunning")
    );
    static final SimpleCommandExceptionType NO_RECURSIVE_TRACES = new SimpleCommandExceptionType(Component.translatable("commands.debug.function.noRecursion"));
    static final SimpleCommandExceptionType NO_RETURN_RUN = new SimpleCommandExceptionType(Component.translatable("commands.debug.function.noReturnRun"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("debug")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(Commands.literal("start").executes(context -> start(context.getSource())))
                .then(Commands.literal("stop").executes(context -> stop(context.getSource())))
                .then(
                    Commands.literal("function")
                        .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .then(
                            Commands.argument("name", FunctionArgument.functions())
                                .suggests(FunctionCommand.SUGGEST_FUNCTION)
                                .executes(new DebugCommand.TraceCustomExecutor())
                        )
                )
        );
    }

    private static int start(CommandSourceStack source) throws CommandSyntaxException {
        MinecraftServer server = source.getServer();
        if (server.isTimeProfilerRunning()) {
            throw ERROR_ALREADY_RUNNING.create();
        } else {
            server.startTimeProfiler();
            source.sendSuccess(() -> Component.translatable("commands.debug.started"), true);
            return 0;
        }
    }

    private static int stop(CommandSourceStack source) throws CommandSyntaxException {
        MinecraftServer server = source.getServer();
        if (!server.isTimeProfilerRunning()) {
            throw ERROR_NOT_RUNNING.create();
        } else {
            ProfileResults profileResults = server.stopTimeProfiler();
            double d = (double)profileResults.getNanoDuration() / TimeUtil.NANOSECONDS_PER_SECOND;
            double d1 = profileResults.getTickDuration() / d;
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.debug.stopped", String.format(Locale.ROOT, "%.2f", d), profileResults.getTickDuration(), String.format(Locale.ROOT, "%.2f", d1)
                ),
                true
            );
            return (int)d1;
        }
    }

    static class TraceCustomExecutor
        extends CustomCommandExecutor.WithErrorHandling<CommandSourceStack>
        implements CustomCommandExecutor.CommandAdapter<CommandSourceStack> {
        @Override
        public void runGuarded(
            CommandSourceStack source,
            ContextChain<CommandSourceStack> contextChain,
            ChainModifiers chainModifiers,
            ExecutionControl<CommandSourceStack> executionControl
        ) throws CommandSyntaxException {
            if (chainModifiers.isReturn()) {
                throw DebugCommand.NO_RETURN_RUN.create();
            } else if (executionControl.tracer() != null) {
                throw DebugCommand.NO_RECURSIVE_TRACES.create();
            } else {
                CommandContext<CommandSourceStack> topContext = contextChain.getTopContext();
                Collection<CommandFunction<CommandSourceStack>> functions = FunctionArgument.getFunctions(topContext, "name");
                MinecraftServer server = source.getServer();
                String string = "debug-trace-" + Util.getFilenameFormattedDateTime() + ".txt";
                CommandDispatcher<CommandSourceStack> dispatcher = source.getServer().getFunctions().getDispatcher();
                int i = 0;

                try {
                    Path file = server.getFile("debug");
                    Files.createDirectories(file);
                    final PrintWriter printWriter = new PrintWriter(Files.newBufferedWriter(file.resolve(string), StandardCharsets.UTF_8));
                    DebugCommand.Tracer tracer = new DebugCommand.Tracer(printWriter);
                    executionControl.tracer(tracer);

                    for (final CommandFunction<CommandSourceStack> commandFunction : functions) {
                        try {
                            CommandSourceStack commandSourceStack = source.withSource(tracer).withMaximumPermission(LevelBasedPermissionSet.GAMEMASTER);
                            InstantiatedFunction<CommandSourceStack> instantiatedFunction = commandFunction.instantiate(null, dispatcher);
                            executionControl.queueNext((new CallFunction<CommandSourceStack>(instantiatedFunction, CommandResultCallback.EMPTY, false) {
                                @Override
                                public void execute(CommandSourceStack source1, ExecutionContext<CommandSourceStack> executionContext, Frame frame) {
                                    printWriter.println(commandFunction.id());
                                    super.execute(source1, executionContext, frame);
                                }
                            }).bind(commandSourceStack));
                            i += instantiatedFunction.entries().size();
                        } catch (FunctionInstantiationException var18) {
                            source.sendFailure(var18.messageComponent());
                        }
                    }
                } catch (IOException | UncheckedIOException var19) {
                    DebugCommand.LOGGER.warn("Tracing failed", (Throwable)var19);
                    source.sendFailure(Component.translatable("commands.debug.function.traceFailed"));
                }

                int i1 = i;
                executionControl.queueNext(
                    (executionContext, frame) -> {
                        if (functions.size() == 1) {
                            source.sendSuccess(
                                () -> Component.translatable(
                                    "commands.debug.function.success.single", i1, Component.translationArg(functions.iterator().next().id()), string
                                ),
                                true
                            );
                        } else {
                            source.sendSuccess(() -> Component.translatable("commands.debug.function.success.multiple", i1, functions.size(), string), true);
                        }
                    }
                );
            }
        }
    }

    static class Tracer implements CommandSource, TraceCallbacks {
        public static final int INDENT_OFFSET = 1;
        private final PrintWriter output;
        private int lastIndent;
        private boolean waitingForResult;

        Tracer(PrintWriter output) {
            this.output = output;
        }

        private void indentAndSave(int indent) {
            this.printIndent(indent);
            this.lastIndent = indent;
        }

        private void printIndent(int indent) {
            for (int i = 0; i < indent + 1; i++) {
                this.output.write("    ");
            }
        }

        private void newLine() {
            if (this.waitingForResult) {
                this.output.println();
                this.waitingForResult = false;
            }
        }

        @Override
        public void onCommand(int depth, String command) {
            this.newLine();
            this.indentAndSave(depth);
            this.output.print("[C] ");
            this.output.print(command);
            this.waitingForResult = true;
        }

        @Override
        public void onReturn(int depth, String command, int returnValue) {
            if (this.waitingForResult) {
                this.output.print(" -> ");
                this.output.println(returnValue);
                this.waitingForResult = false;
            } else {
                this.indentAndSave(depth);
                this.output.print("[R = ");
                this.output.print(returnValue);
                this.output.print("] ");
                this.output.println(command);
            }
        }

        @Override
        public void onCall(int depth, Identifier function, int commands) {
            this.newLine();
            this.indentAndSave(depth);
            this.output.print("[F] ");
            this.output.print(function);
            this.output.print(" size=");
            this.output.println(commands);
        }

        @Override
        public void onError(String errorMessage) {
            this.newLine();
            this.indentAndSave(this.lastIndent + 1);
            this.output.print("[E] ");
            this.output.print(errorMessage);
        }

        @Override
        public void sendSystemMessage(Component message) {
            this.newLine();
            this.printIndent(this.lastIndent + 1);
            this.output.print("[M] ");
            this.output.println(message.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }

        @Override
        public boolean alwaysAccepts() {
            return true;
        }

        // Paper start
        @Override
        public org.bukkit.command.CommandSender getBukkitSender(final CommandSourceStack wrapper) {
            throw new UnsupportedOperationException();
        }
        // Paper end

        @Override
        public void close() {
            IOUtils.closeQuietly((Writer)this.output);
        }
    }
}
