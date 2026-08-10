package net.minecraft.world.entity.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ThrowingComponent;
import net.minecraft.util.Crypt;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.SignatureValidator;

public record ProfilePublicKey(ProfilePublicKey.Data data) {
    public static final Component EXPIRED_PROFILE_PUBLIC_KEY = Component.translatable("multiplayer.disconnect.expired_public_key");
    private static final Component INVALID_SIGNATURE = Component.translatable("multiplayer.disconnect.invalid_public_key_signature");
    public static final Duration EXPIRY_GRACE_PERIOD = Duration.ofHours(8L);
    public static final Codec<ProfilePublicKey> TRUSTED_CODEC = ProfilePublicKey.Data.CODEC.xmap(ProfilePublicKey::new, ProfilePublicKey::data);

    public static ProfilePublicKey createValidated(SignatureValidator signatureValidator, UUID profileId, ProfilePublicKey.Data data) throws ProfilePublicKey.ValidationException {
        if (!data.validateSignature(signatureValidator, profileId) && (org.bukkit.Bukkit.getServer().getOnlineMode() && me.earthme.luminol.config.modules.misc.PublickeyVerifyConfig.enabled)) { // Luminol - Verify signature only in online-mode
            throw new ProfilePublicKey.ValidationException(INVALID_SIGNATURE, org.bukkit.event.player.PlayerKickEvent.Cause.INVALID_PUBLIC_KEY_SIGNATURE); // Paper - kick event causes
        } else {
            return new ProfilePublicKey(data);
        }
    }

    public SignatureValidator createSignatureValidator() {
        return SignatureValidator.from(this.data.key, "SHA256withRSA");
    }

    public record Data(Instant expiresAt, PublicKey key, byte[] keySignature) {
        private static final int MAX_KEY_SIGNATURE_SIZE = 4096;
        public static final Codec<ProfilePublicKey.Data> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    ExtraCodecs.INSTANT_ISO8601.fieldOf("expires_at").forGetter(ProfilePublicKey.Data::expiresAt),
                    Crypt.PUBLIC_KEY_CODEC.fieldOf("key").forGetter(ProfilePublicKey.Data::key),
                    ExtraCodecs.BASE64_STRING.fieldOf("signature_v2").forGetter(ProfilePublicKey.Data::keySignature)
                )
                .apply(instance, ProfilePublicKey.Data::new)
        );

        public Data(FriendlyByteBuf buffer) {
            this(buffer.readInstant(), buffer.readPublicKey(), buffer.readByteArray(4096));
        }

        public void write(FriendlyByteBuf buffer) {
            buffer.writeInstant(this.expiresAt);
            buffer.writePublicKey(this.key);
            buffer.writeByteArray(this.keySignature);
        }

        boolean validateSignature(SignatureValidator signatureValidator, UUID profileId) {
            return signatureValidator.validate(this.signedPayload(profileId), this.keySignature);
        }

        private byte[] signedPayload(UUID profileId) {
            byte[] encoded = this.key.getEncoded();
            byte[] bytes = new byte[24 + encoded.length];
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
            byteBuffer.putLong(profileId.getMostSignificantBits())
                .putLong(profileId.getLeastSignificantBits())
                .putLong(this.expiresAt.toEpochMilli())
                .put(encoded);
            return bytes;
        }

        public boolean hasExpired() {
            return this.expiresAt.isBefore(Instant.now());
        }

        public boolean hasExpired(Duration gracePeriod) {
            return this.expiresAt.plus(gracePeriod).isBefore(Instant.now());
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ProfilePublicKey.Data data
                && this.expiresAt.equals(data.expiresAt)
                && this.key.equals(data.key)
                && Arrays.equals(this.keySignature, data.keySignature);
        }
    }

    public static class ValidationException extends ThrowingComponent {
        public final org.bukkit.event.player.PlayerKickEvent.Cause kickCause; // Paper

        @Deprecated @io.papermc.paper.annotation.DoNotUse // Paper
        public ValidationException(Component component) {
            // Paper start
            this(component, org.bukkit.event.player.PlayerKickEvent.Cause.UNKNOWN);
        }

        public ValidationException(Component component, org.bukkit.event.player.PlayerKickEvent.Cause kickCause) {
            // Paper end
            super(component);
            this.kickCause = kickCause; // Paper
        }
    }
}
