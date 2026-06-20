package net.minecraft.network.chat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.function.IntFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

public record ChatTypeDecoration(String translationKey, List<ChatTypeDecoration.Parameter> parameters, Style style) {
    public static final Codec<ChatTypeDecoration> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                Codec.STRING.fieldOf("translation_key").forGetter(ChatTypeDecoration::translationKey),
                ChatTypeDecoration.Parameter.CODEC.listOf().fieldOf("parameters").forGetter(ChatTypeDecoration::parameters),
                Style.Serializer.CODEC.optionalFieldOf("style", Style.EMPTY).forGetter(ChatTypeDecoration::style)
            )
            .apply(instance, ChatTypeDecoration::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ChatTypeDecoration> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8,
        ChatTypeDecoration::translationKey,
        ChatTypeDecoration.Parameter.STREAM_CODEC.apply(ByteBufCodecs.list()),
        ChatTypeDecoration::parameters,
        Style.Serializer.TRUSTED_STREAM_CODEC,
        ChatTypeDecoration::style,
        ChatTypeDecoration::new
    );

    public static ChatTypeDecoration withSender(String translationKey) {
        return new ChatTypeDecoration(translationKey, List.of(ChatTypeDecoration.Parameter.SENDER, ChatTypeDecoration.Parameter.CONTENT), Style.EMPTY);
    }

    public static ChatTypeDecoration incomingDirectMessage(String translationKey) {
        Style style = Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(true);
        return new ChatTypeDecoration(translationKey, List.of(ChatTypeDecoration.Parameter.SENDER, ChatTypeDecoration.Parameter.CONTENT), style);
    }

    public static ChatTypeDecoration outgoingDirectMessage(String translationKey) {
        Style style = Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(true);
        return new ChatTypeDecoration(translationKey, List.of(ChatTypeDecoration.Parameter.TARGET, ChatTypeDecoration.Parameter.CONTENT), style);
    }

    public static ChatTypeDecoration teamMessage(String translationKey) {
        return new ChatTypeDecoration(
            translationKey,
            List.of(ChatTypeDecoration.Parameter.TARGET, ChatTypeDecoration.Parameter.SENDER, ChatTypeDecoration.Parameter.CONTENT),
            Style.EMPTY
        );
    }

    public Component decorate(Component content, ChatType.Bound boundChatType) {
        Object[] objects = this.resolveParameters(content, boundChatType);
        return Component.translatable(this.translationKey, objects).withStyle(this.style);
    }

    private Component[] resolveParameters(Component content, ChatType.Bound boundChatType) {
        Component[] components = new Component[this.parameters.size()];

        for (int i = 0; i < components.length; i++) {
            ChatTypeDecoration.Parameter parameter = this.parameters.get(i);
            components[i] = parameter.select(content, boundChatType);
        }

        return components;
    }

    public static enum Parameter implements StringRepresentable {
        SENDER(0, "sender", (content, boundChatType) -> boundChatType.name()),
        TARGET(1, "target", (content, boundChatType) -> boundChatType.targetName().orElse(CommonComponents.EMPTY)),
        CONTENT(2, "content", (content, boundChatType) -> content);

        private static final IntFunction<ChatTypeDecoration.Parameter> BY_ID = ByIdMap.continuous(
            parameter -> parameter.id, values(), ByIdMap.OutOfBoundsStrategy.ZERO
        );
        public static final Codec<ChatTypeDecoration.Parameter> CODEC = StringRepresentable.fromEnum(ChatTypeDecoration.Parameter::values);
        public static final StreamCodec<ByteBuf, ChatTypeDecoration.Parameter> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, parameter -> parameter.id);
        private final int id;
        private final String name;
        private final ChatTypeDecoration.Parameter.Selector selector;

        private Parameter(final int id, final String name, final ChatTypeDecoration.Parameter.Selector selector) {
            this.id = id;
            this.name = name;
            this.selector = selector;
        }

        public Component select(Component content, ChatType.Bound boundChatType) {
            return this.selector.select(content, boundChatType);
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public interface Selector {
            Component select(Component content, ChatType.Bound boundChatType);
        }
    }
}
