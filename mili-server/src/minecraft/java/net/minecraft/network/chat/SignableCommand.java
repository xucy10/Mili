package net.minecraft.network.chat;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.arguments.SignedArgument;
import org.jspecify.annotations.Nullable;

public record SignableCommand<S>(List<SignableCommand.Argument<S>> arguments) {
    public static <S> boolean hasSignableArguments(ParseResults<S> parseResults) {
        return !of(parseResults).arguments().isEmpty();
    }

    public static <S> SignableCommand<S> of(ParseResults<S> results) {
        String string = results.getReader().getString();
        CommandContextBuilder<S> context = results.getContext();
        CommandContextBuilder<S> commandContextBuilder = context;
        List<SignableCommand.Argument<S>> list = collectArguments(string, context);

        CommandContextBuilder<S> commandContextBuilder1;
        while ((commandContextBuilder1 = commandContextBuilder.getChild()) != null && commandContextBuilder1.getRootNode() != context.getRootNode()) {
            list.addAll(collectArguments(string, commandContextBuilder1));
            commandContextBuilder = commandContextBuilder1;
        }

        return new SignableCommand<>(list);
    }

    private static <S> List<SignableCommand.Argument<S>> collectArguments(String key, CommandContextBuilder<S> contextBuilder) {
        List<SignableCommand.Argument<S>> list = new ArrayList<>();

        for (ParsedCommandNode<S> parsedCommandNode : contextBuilder.getNodes()) {
            if (parsedCommandNode.getNode() instanceof ArgumentCommandNode<S, ?> argumentCommandNode && argumentCommandNode.getType() instanceof SignedArgument
                )
             {
                ParsedArgument<S, ?> parsedArgument = contextBuilder.getArguments().get(argumentCommandNode.getName());
                if (parsedArgument != null) {
                    String string = parsedArgument.getRange().get(key);
                    list.add(new SignableCommand.Argument<>(argumentCommandNode, string));
                }
            }
        }

        return list;
    }

    public SignableCommand.@Nullable Argument<S> getArgument(String argument) {
        for (SignableCommand.Argument<S> argument1 : this.arguments) {
            if (argument.equals(argument1.name())) {
                return argument1;
            }
        }

        return null;
    }

    public record Argument<S>(ArgumentCommandNode<S, ?> node, String value) {
        public String name() {
            return this.node.getName();
        }
    }
}
