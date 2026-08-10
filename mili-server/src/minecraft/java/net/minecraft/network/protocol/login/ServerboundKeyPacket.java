package net.minecraft.network.protocol.login;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;
import javax.crypto.SecretKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.util.Crypt;
import net.minecraft.util.CryptException;

public class ServerboundKeyPacket implements Packet<ServerLoginPacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundKeyPacket> STREAM_CODEC = Packet.codec(ServerboundKeyPacket::write, ServerboundKeyPacket::new);
    private final byte[] keybytes;
    private final byte[] encryptedChallenge;

    public ServerboundKeyPacket(SecretKey secretKey, PublicKey publicKey, byte[] challenge) throws CryptException {
        this.keybytes = Crypt.encryptUsingKey(publicKey, secretKey.getEncoded());
        this.encryptedChallenge = Crypt.encryptUsingKey(publicKey, challenge);
    }

    private ServerboundKeyPacket(FriendlyByteBuf buffer) {
        this.keybytes = buffer.readByteArray();
        this.encryptedChallenge = buffer.readByteArray();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeByteArray(this.keybytes);
        buffer.writeByteArray(this.encryptedChallenge);
    }

    @Override
    public PacketType<ServerboundKeyPacket> type() {
        return LoginPacketTypes.SERVERBOUND_KEY;
    }

    @Override
    public void handle(ServerLoginPacketListener handler) {
        handler.handleKey(this);
    }

    public SecretKey getSecretKey(PrivateKey key) throws CryptException {
        return Crypt.decryptByteToSecretKey(key, this.keybytes);
    }

    public boolean isChallengeValid(byte[] expected, PrivateKey key) {
        try {
            return Arrays.equals(expected, Crypt.decryptUsingKey(key, this.encryptedChallenge));
        } catch (CryptException var4) {
            return false;
        }
    }
}
