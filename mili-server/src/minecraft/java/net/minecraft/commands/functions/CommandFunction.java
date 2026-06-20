package net.minecraft.commands.functions;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.Commands;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.commands.execution.tasks.BuildContexts;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public interface CommandFunction<T> {
    Identifier id();

    InstantiatedFunction<T> instantiate(@Nullable CompoundTag arguments, CommandDispatcher<T> dispatcher) throws FunctionInstantiationException;

    private static boolean shouldConcatenateNextLine(CharSequence line) {
        int len = line.length();
        return len > 0 && line.charAt(len - 1) == '\\';
    }

    static <T extends ExecutionCommandSource<T>> CommandFunction<T> fromLines(Identifier id, CommandDispatcher<T> dispatcher, T source, List<String> lines) {
        FunctionBuilder<T> functionBuilder = new FunctionBuilder<>();

        for (int i = 0; i < lines.size(); i++) {
            int i1 = i + 1;
            String trimmed = lines.get(i).trim();
            String string;
            if (shouldConcatenateNextLine(trimmed)) {
                StringBuilder stringBuilder = new StringBuilder(trimmed);

                do {
                    if (++i == lines.size()) {
                        throw new IllegalArgumentException("Line continuation at end of file");
                    }

                    stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                    String trimmed1 = lines.get(i).trim();
                    stringBuilder.append(trimmed1);
                    checkCommandLineLength(stringBuilder);
                } while (shouldConcatenateNextLine(stringBuilder));

                string = stringBuilder.toString();
            } else {
                string = trimmed;
            }

            checkCommandLineLength(string);
            StringReader stringReader = new StringReader(string);
            if (stringReader.canRead() && stringReader.peek() != '#') {
                if (stringReader.peek() == '/') {
                    stringReader.skip();
                    if (stringReader.peek() == '/') {
                        throw new IllegalArgumentException(
                            "Unknown or invalid command '" + string + "' on line " + i1 + " (if you intended to make a comment, use '#' not '//')"
                        );
                    }

                    String trimmed1 = stringReader.readUnquotedString();
                    throw new IllegalArgumentException(
                        "Unknown or invalid command '"
                            + string
                            + "' on line "
                            + i1
                            + " (did you mean '"
                            + trimmed1
                            + "'? Do not use a preceding forwards slash.)"
                    );
                }

                if (stringReader.peek() == '$') {
                    functionBuilder.addMacro(string.substring(1), i1, source);
                } else {
                    try {
                        functionBuilder.addCommand(parseCommand(dispatcher, source, stringReader));
                    } catch (CommandSyntaxException var11) {
                        throw new IllegalArgumentException("Whilst parsing command on line " + i1 + ": " + var11.getMessage());
                    }
                }
            }
        }

        return functionBuilder.build(id);
    }

    static void checkCommandLineLength(CharSequence command) {
        if (command.length() > 2000000) {
            CharSequence charSequence = command.subSequence(0, Math.min(512, 2000000));
            throw new IllegalStateException("Command too long: " + command.length() + " characters, contents: " + charSequence + "...");
        }
    }

    static <T extends ExecutionCommandSource<T>> UnboundEntryAction<T> parseCommand(CommandDispatcher<T> dispatcher, T source, StringReader command) throws CommandSyntaxException {
        ParseResults<T> parseResults = dispatcher.parse(command, source);
        Commands.validateParseResults(parseResults);
        Optional<ContextChain<T>> optional = ContextChain.tryFlatten(parseResults.getContext().build(command.getString()));
        if (optional.isEmpty()) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parseResults.getReader());
        } else {
            return new BuildContexts.Unbound<>(command.getString(), optional.get());
        }
    }
}
