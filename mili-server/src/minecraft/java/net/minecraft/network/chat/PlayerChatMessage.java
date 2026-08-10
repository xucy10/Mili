package net.minecraft.network.chat;

import com.google.common.primitives.Ints;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.security.SignatureException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.util.SignatureUpdater;
import net.minecraft.util.SignatureValidator;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public record PlayerChatMessage(
    SignedMessageLink link, @Nullable MessageSignature signature, SignedMessageBody signedBody, @Nullable Component unsignedContent, FilterMask filterMask
) {
    // Paper start - adventure; support signed messages
    public final class AdventureView implements net.kyori.adventure.chat.SignedMessage {
        private AdventureView() {
        }
        @Override
        public Instant timestamp() {
            return PlayerChatMessage.this.timeStamp();
        }
        @Override
        public long salt() {
            return PlayerChatMessage.this.salt();
        }
        @Override
        public @Nullable Signature signature() {
            return PlayerChatMessage.this.signature == null ? null : PlayerChatMessage.this.signature.adventure();
        }
        @Override
        public net.kyori.adventure.text.@Nullable Component unsignedContent() {
            return PlayerChatMessage.this.unsignedContent() == null ? null : io.papermc.paper.adventure.PaperAdventure.asAdventure(PlayerChatMessage.this.unsignedContent());
        }
        @Override
        public String message() {
            return PlayerChatMessage.this.signedContent();
        }
        @Override
        public net.kyori.adventure.identity.Identity identity() {
            return net.kyori.adventure.identity.Identity.identity(PlayerChatMessage.this.sender());
        }
        public PlayerChatMessage playerChatMessage() {
            return PlayerChatMessage.this;
        }
    }
    public AdventureView adventureView() {
        return new AdventureView();
    }
    // Paper end - adventure; support signed messages

    public static final MapCodec<PlayerChatMessage> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                SignedMessageLink.CODEC.fieldOf("link").forGetter(PlayerChatMessage::link),
                MessageSignature.CODEC.optionalFieldOf("signature").forGetter(chatMessage -> Optional.ofNullable(chatMessage.signature)),
                SignedMessageBody.MAP_CODEC.forGetter(PlayerChatMessage::signedBody),
                ComponentSerialization.CODEC.optionalFieldOf("unsigned_content").forGetter(chatMessage -> Optional.ofNullable(chatMessage.unsignedContent)),
                FilterMask.CODEC.optionalFieldOf("filter_mask", FilterMask.PASS_THROUGH).forGetter(PlayerChatMessage::filterMask)
            )
            .apply(
                instance,
                (messageLink, signature, signedBody, unsignedContent, filterMask) -> new PlayerChatMessage(
                    messageLink, signature.orElse(null), signedBody, unsignedContent.orElse(null), filterMask
                )
            )
    );
    private static final UUID SYSTEM_SENDER = Util.NIL_UUID;
    public static final Duration MESSAGE_EXPIRES_AFTER_SERVER = Duration.ofMinutes(5L);
    public static final Duration MESSAGE_EXPIRES_AFTER_CLIENT = MESSAGE_EXPIRES_AFTER_SERVER.plus(Duration.ofMinutes(2L));

    public static PlayerChatMessage system(String content) {
        return unsigned(SYSTEM_SENDER, content);
    }

    public static PlayerChatMessage unsigned(UUID sender, String content) {
        SignedMessageBody signedMessageBody = SignedMessageBody.unsigned(content);
        SignedMessageLink signedMessageLink = SignedMessageLink.unsigned(sender);
        return new PlayerChatMessage(signedMessageLink, null, signedMessageBody, null, FilterMask.PASS_THROUGH);
    }

    public PlayerChatMessage withUnsignedContent(Component message) {
        // Paper start - adventure
        final Component component;
        if (message instanceof io.papermc.paper.adventure.AdventureComponent advComponent) {
            component = this.signedContent().equals(io.papermc.paper.adventure.AdventureCodecs.tryCollapseToString(advComponent.adventure$component())) ? null : message;
        } else {
            component = !message.equals(Component.literal(this.signedContent())) ? message : null;
        }
        // Paper end - adventure
        return new PlayerChatMessage(this.link, this.signature, this.signedBody, component, this.filterMask);
    }

    public PlayerChatMessage removeUnsignedContent() {
        return this.unsignedContent != null ? new PlayerChatMessage(this.link, this.signature, this.signedBody, null, this.filterMask) : this;
    }

    public PlayerChatMessage filter(FilterMask mask) {
        return this.filterMask.equals(mask) ? this : new PlayerChatMessage(this.link, this.signature, this.signedBody, this.unsignedContent, mask);
    }

    public PlayerChatMessage filter(boolean shouldFilter) {
        return this.filter(shouldFilter ? this.filterMask : FilterMask.PASS_THROUGH);
    }

    public PlayerChatMessage removeSignature() {
        SignedMessageBody signedMessageBody = SignedMessageBody.unsigned(this.signedContent());
        SignedMessageLink signedMessageLink = SignedMessageLink.unsigned(this.sender());
        return new PlayerChatMessage(signedMessageLink, null, signedMessageBody, this.unsignedContent, this.filterMask);
    }

    public static void updateSignature(SignatureUpdater.Output output, SignedMessageLink link, SignedMessageBody body) throws SignatureException {
        output.update(Ints.toByteArray(1));
        link.updateSignature(output);
        body.updateSignature(output);
    }

    public boolean verify(SignatureValidator validator) {
        return this.signature != null && this.signature.verify(validator, output -> updateSignature(output, this.link, this.signedBody));
    }

    public String signedContent() {
        return this.signedBody.content();
    }

    public Component decoratedContent() {
        return Objects.requireNonNullElseGet(this.unsignedContent, () -> Component.literal(this.signedContent()));
    }

    public Instant timeStamp() {
        return this.signedBody.timeStamp();
    }

    public long salt() {
        return this.signedBody.salt();
    }

    public boolean hasExpiredServer(Instant timestamp) {
        return timestamp.isAfter(this.timeStamp().plus(MESSAGE_EXPIRES_AFTER_SERVER));
    }

    public boolean hasExpiredClient(Instant timestamp) {
        return timestamp.isAfter(this.timeStamp().plus(MESSAGE_EXPIRES_AFTER_CLIENT));
    }

    public UUID sender() {
        return this.link.sender();
    }

    public boolean isSystem() {
        return this.sender().equals(SYSTEM_SENDER);
    }

    public boolean hasSignature() {
        return this.signature != null;
    }

    public boolean hasSignatureFrom(UUID uuid) {
        return this.hasSignature() && this.link.sender().equals(uuid);
    }

    public boolean isFullyFiltered() {
        return this.filterMask.isFullyFiltered();
    }

    public static String describeSigned(PlayerChatMessage chatMessage) {
        return "'"
            + chatMessage.signedBody.content()
            + "' @ "
            + chatMessage.signedBody.timeStamp()
            + "\n - From: "
            + chatMessage.link.sender()
            + "/"
            + chatMessage.link.sessionId()
            + ", message #"
            + chatMessage.link.index()
            + "\n - Salt: "
            + chatMessage.signedBody.salt()
            + "\n - Signature: "
            + MessageSignature.describe(chatMessage.signature)
            + "\n - Last Seen: [\n"
            + chatMessage.signedBody
                .lastSeen()
                .entries()
                .stream()
                .map(signature -> "     " + MessageSignature.describe(signature) + "\n")
                .collect(Collectors.joining())
            + " ]\n";
    }
}
