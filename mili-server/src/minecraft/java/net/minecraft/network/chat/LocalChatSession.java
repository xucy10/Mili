package net.minecraft.network.chat;

import java.util.UUID;
import net.minecraft.util.Signer;
import net.minecraft.world.entity.player.ProfileKeyPair;

public record LocalChatSession(UUID sessionId, ProfileKeyPair keyPair) {
    public static LocalChatSession create(ProfileKeyPair keyPair) {
        return new LocalChatSession(UUID.randomUUID(), keyPair);
    }

    public SignedMessageChain.Encoder createMessageEncoder(UUID sender) {
        return new SignedMessageChain(sender, this.sessionId).encoder(Signer.from(this.keyPair.privateKey(), "SHA256withRSA"));
    }

    public RemoteChatSession asRemote() {
        return new RemoteChatSession(this.sessionId, this.keyPair.publicKey());
    }
}
