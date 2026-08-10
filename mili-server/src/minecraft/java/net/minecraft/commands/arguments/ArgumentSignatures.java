package net.minecraft.commands.arguments;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.SignableCommand;
import org.jspecify.annotations.Nullable;

public record ArgumentSignatures(List<ArgumentSignatures.Entry> entries) {
    public static final ArgumentSignatures EMPTY = new ArgumentSignatures(List.of());
    private static final int MAX_ARGUMENT_COUNT = 8;
    private static final int MAX_ARGUMENT_NAME_LENGTH = 16;

    public ArgumentSignatures(FriendlyByteBuf buffer) {
        this(buffer.readCollection(FriendlyByteBuf.<List<ArgumentSignatures.Entry>>limitValue(ArrayList::new, 8), ArgumentSignatures.Entry::new));
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeCollection(this.entries, (buffer1, entry) -> entry.write(buffer1));
    }

    public static ArgumentSignatures signCommand(SignableCommand<?> command, ArgumentSignatures.Signer signer) {
        List<ArgumentSignatures.Entry> list = command.arguments().stream().map(signedArgument -> {
            MessageSignature messageSignature = signer.sign(signedArgument.value());
            return messageSignature != null ? new ArgumentSignatures.Entry(signedArgument.name(), messageSignature) : null;
        }).filter(Objects::nonNull).toList();
        return new ArgumentSignatures(list);
    }

    public record Entry(String name, MessageSignature signature) {
        public Entry(FriendlyByteBuf buffer) {
            this(buffer.readUtf(16), MessageSignature.read(buffer));
        }

        public void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(this.name, 16);
            MessageSignature.write(buffer, this.signature);
        }
    }

    @FunctionalInterface
    public interface Signer {
        @Nullable MessageSignature sign(String argumentText);
    }
}
