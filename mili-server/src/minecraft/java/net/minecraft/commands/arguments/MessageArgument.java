package net.minecraft.commands.arguments;

import com.google.common.collect.Lists;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSigningContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.network.chat.ChatDecorator;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;
import net.minecraft.server.permissions.Permissions;
import org.jspecify.annotations.Nullable;

public class MessageArgument implements SignedArgument<MessageArgument.Message> {
    private static final Collection<String> EXAMPLES = Arrays.asList("Hello world!", "foo", "@e", "Hello @p :)");
    static final Dynamic2CommandExceptionType TOO_LONG = new Dynamic2CommandExceptionType(
        (object, object1) -> Component.translatableEscape("argument.message.too_long", object, object1)
    );

    public static MessageArgument message() {
        return new MessageArgument();
    }

    public static Component getMessage(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        MessageArgument.Message message = context.getArgument(name, MessageArgument.Message.class);
        return message.resolveComponent(context.getSource());
    }

    public static void resolveChatMessage(CommandContext<CommandSourceStack> context, String key, Consumer<PlayerChatMessage> callback) throws CommandSyntaxException {
        MessageArgument.Message message = context.getArgument(key, MessageArgument.Message.class);
        // Paper start - brig message argument support
        resolveChatMessage(message, context, key, callback);
    }
    public static void resolveChatMessage(MessageArgument.Message message, CommandContext<CommandSourceStack> context, String key, Consumer<PlayerChatMessage> callback) throws CommandSyntaxException {
        // Paper end - brig message argument support
        CommandSourceStack commandSourceStack = context.getSource();
        Component component = message.resolveComponent(commandSourceStack);
        CommandSigningContext signingContext = commandSourceStack.getSigningContext();
        PlayerChatMessage argument = signingContext.getArgument(key);
        if (argument != null) {
            resolveSignedMessage(callback, commandSourceStack, argument.withUnsignedContent(component));
        } else {
            resolveDisguisedMessage(callback, commandSourceStack, PlayerChatMessage.system(message.text).withUnsignedContent(component));
        }
    }

    private static void resolveSignedMessage(Consumer<PlayerChatMessage> callback, CommandSourceStack source, PlayerChatMessage message) {
        MinecraftServer server = source.getServer();
        CompletableFuture<FilteredText> completableFuture = filterPlainText(source, message);
        // Paper start - support asynchronous chat decoration
        CompletableFuture<Component> componentFuture = server.getChatDecorator().decorate(source.getPlayer(), source, message.decoratedContent());
        source.getChatMessageChainer().append(CompletableFuture.allOf(completableFuture, componentFuture), filtered -> {
            PlayerChatMessage playerChatMessage = message.withUnsignedContent(componentFuture.join()).filter(completableFuture.join().mask());
            // Paper end - support asynchronous chat decoration
            callback.accept(playerChatMessage);
        });
    }

    private static void resolveDisguisedMessage(Consumer<PlayerChatMessage> callback, CommandSourceStack source, PlayerChatMessage message) {
        ChatDecorator chatDecorator = source.getServer().getChatDecorator();
        // Paper start - support asynchronous chat decoration
        CompletableFuture<Component> componentFuture = chatDecorator.decorate(source.getPlayer(), source, message.decoratedContent());
        source.getChatMessageChainer().append(componentFuture, (result) -> callback.accept(message.withUnsignedContent(result)));
        // Paper end - support asynchronous chat decoration
    }

    private static CompletableFuture<FilteredText> filterPlainText(CommandSourceStack source, PlayerChatMessage message) {
        ServerPlayer player = source.getPlayer();
        return player != null && message.hasSignatureFrom(player.getUUID())
            ? player.getTextFilter().processStreamMessage(message.signedContent())
            : CompletableFuture.completedFuture(FilteredText.passThrough(message.signedContent()));
    }

    @Override
    public MessageArgument.Message parse(StringReader reader) throws CommandSyntaxException {
        return MessageArgument.Message.parseText(reader, true);
    }

    @Override
    public <S> MessageArgument.Message parse(StringReader stringReader, @Nullable S object) throws CommandSyntaxException {
        return MessageArgument.Message.parseText(stringReader, EntitySelectorParser.allowSelectors(object));
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public record Message(String text, MessageArgument.Part[] parts) {
        Component resolveComponent(CommandSourceStack source) throws CommandSyntaxException {
            return this.toComponent(source, source.permissions().hasPermission(Permissions.COMMANDS_ENTITY_SELECTORS));
        }

        public Component toComponent(CommandSourceStack source, boolean allowSelectors) throws CommandSyntaxException {
            if (this.parts.length != 0 && allowSelectors) {
                MutableComponent mutableComponent = Component.literal(this.text.substring(0, this.parts[0].start()));
                int start = this.parts[0].start();

                for (MessageArgument.Part part : this.parts) {
                    Component component = part.toComponent(source);
                    if (start < part.start()) {
                        mutableComponent.append(this.text.substring(start, part.start()));
                    }

                    mutableComponent.append(component);
                    start = part.end();
                }

                if (start < this.text.length()) {
                    mutableComponent.append(this.text.substring(start));
                }

                return mutableComponent;
            } else {
                return Component.literal(this.text);
            }
        }

        public static MessageArgument.Message parseText(StringReader reader, boolean allowSelectors) throws CommandSyntaxException {
            if (reader.getRemainingLength() > 256) {
                throw MessageArgument.TOO_LONG.create(reader.getRemainingLength(), 256);
            } else {
                String remaining = reader.getRemaining();
                if (!allowSelectors) {
                    reader.setCursor(reader.getTotalLength());
                    return new MessageArgument.Message(remaining, new MessageArgument.Part[0]);
                } else {
                    List<MessageArgument.Part> list = Lists.newArrayList();
                    int cursor = reader.getCursor();

                    while (true) {
                        int cursor1;
                        EntitySelector entitySelector;
                        while (true) {
                            if (!reader.canRead()) {
                                return new MessageArgument.Message(remaining, list.toArray(new MessageArgument.Part[0]));
                            }

                            if (reader.peek() == '@') {
                                cursor1 = reader.getCursor();

                                try {
                                    EntitySelectorParser entitySelectorParser = new EntitySelectorParser(reader, true);
                                    entitySelector = entitySelectorParser.parse();
                                    break;
                                } catch (CommandSyntaxException var8) {
                                    if (var8.getType() != EntitySelectorParser.ERROR_MISSING_SELECTOR_TYPE
                                        && var8.getType() != EntitySelectorParser.ERROR_UNKNOWN_SELECTOR_TYPE) {
                                        throw var8;
                                    }

                                    reader.setCursor(cursor1 + 1);
                                }
                            } else {
                                reader.skip();
                            }
                        }

                        list.add(new MessageArgument.Part(cursor1 - cursor, reader.getCursor() - cursor, entitySelector));
                    }
                }
            }
        }
    }

    public record Part(int start, int end, EntitySelector selector) {
        public Component toComponent(CommandSourceStack source) throws CommandSyntaxException {
            return EntitySelector.joinNames(this.selector.findEntities(source));
        }
    }
}
