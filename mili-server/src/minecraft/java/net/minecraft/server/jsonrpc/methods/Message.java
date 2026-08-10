package net.minecraft.server.jsonrpc.methods;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;

public record Message(Optional<String> literal, Optional<String> translatable, Optional<List<String>> translatableParams) {
    public static final Codec<Message> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                Codec.STRING.optionalFieldOf("literal").forGetter(Message::literal),
                Codec.STRING.optionalFieldOf("translatable").forGetter(Message::translatable),
                Codec.STRING.listOf().lenientOptionalFieldOf("translatableParams").forGetter(Message::translatableParams)
            )
            .apply(instance, Message::new)
    );

    public Optional<Component> asComponent() {
        if (this.translatable.isPresent()) {
            String string = this.translatable.get();
            if (this.translatableParams.isPresent()) {
                List<String> list = this.translatableParams.get();
                return Optional.of(Component.translatable(string, list.toArray()));
            } else {
                return Optional.of(Component.translatable(string));
            }
        } else {
            return this.literal.map(Component::literal);
        }
    }
}
