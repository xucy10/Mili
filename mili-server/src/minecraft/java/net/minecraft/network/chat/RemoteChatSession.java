package net.minecraft.network.chat;

import com.mojang.authlib.GameProfile;
import java.time.Duration;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.SignatureValidator;
import net.minecraft.world.entity.player.ProfilePublicKey;

public record RemoteChatSession(UUID sessionId, ProfilePublicKey profilePublicKey) {
    public SignedMessageValidator createMessageValidator(Duration duration) {
        return new SignedMessageValidator.KeyBased(this.profilePublicKey.createSignatureValidator(), () -> this.profilePublicKey.data().hasExpired(duration));
    }

    public SignedMessageChain.Decoder createMessageDecoder(UUID sender) {
        return new SignedMessageChain(sender, this.sessionId).decoder(this.profilePublicKey);
    }

    public RemoteChatSession.Data asData() {
        return new RemoteChatSession.Data(this.sessionId, this.profilePublicKey.data());
    }

    public boolean hasExpired() {
        return this.profilePublicKey.data().hasExpired();
    }

    public record Data(UUID sessionId, ProfilePublicKey.Data profilePublicKey) {
        public static RemoteChatSession.Data read(FriendlyByteBuf buffer) {
            return new RemoteChatSession.Data(buffer.readUUID(), new ProfilePublicKey.Data(buffer));
        }

        public static void write(FriendlyByteBuf buffer, RemoteChatSession.Data data) {
            buffer.writeUUID(data.sessionId);
            data.profilePublicKey.write(buffer);
        }

        public RemoteChatSession validate(GameProfile profile, SignatureValidator signatureValidator) throws ProfilePublicKey.ValidationException {
            return new RemoteChatSession(this.sessionId, ProfilePublicKey.createValidated(signatureValidator, profile.id(), this.profilePublicKey));
        }
    }
}
