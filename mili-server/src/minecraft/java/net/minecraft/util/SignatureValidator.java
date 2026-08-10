package net.minecraft.util;

import com.mojang.authlib.yggdrasil.ServicesKeyInfo;
import com.mojang.authlib.yggdrasil.ServicesKeySet;
import com.mojang.authlib.yggdrasil.ServicesKeyType;
import com.mojang.logging.LogUtils;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Collection;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public interface SignatureValidator {
    SignatureValidator NO_VALIDATION = (updater, signature) -> true;
    Logger LOGGER = LogUtils.getLogger();

    boolean validate(SignatureUpdater updater, byte[] signature);

    default boolean validate(byte[] digest, byte[] signature) {
        return this.validate(output -> output.update(digest), signature);
    }

    private static boolean verifySignature(SignatureUpdater updater, byte[] signatureBytes, Signature signature) throws SignatureException {
        updater.update(signature::update);
        return signature.verify(signatureBytes);
    }

    static SignatureValidator from(PublicKey publicKey, String algorithm) {
        return (updater, signatureBytes) -> {
            try {
                Signature instance = Signature.getInstance(algorithm);
                instance.initVerify(publicKey);
                return verifySignature(updater, signatureBytes, instance);
            } catch (Exception var5) {
                LOGGER.error("Failed to verify signature", (Throwable)var5);
                return false;
            }
        };
    }

    static @Nullable SignatureValidator from(ServicesKeySet serviceKeySet, ServicesKeyType serviceKeyType) {
        Collection<ServicesKeyInfo> collection = serviceKeySet.keys(serviceKeyType);
        return collection.isEmpty() ? null : (updater, signatureBytes) -> collection.stream().anyMatch(servicesKeyInfo -> {
            Signature signature = servicesKeyInfo.signature();

            try {
                return verifySignature(updater, signatureBytes, signature);
            } catch (SignatureException var5) {
                LOGGER.error("Failed to verify Services signature", (Throwable)var5);
                return false;
            }
        });
    }
}
