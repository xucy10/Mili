package net.minecraft.util;

import com.mojang.logging.LogUtils;
import java.security.PrivateKey;
import java.security.Signature;
import org.slf4j.Logger;

public interface Signer {
    Logger LOGGER = LogUtils.getLogger();

    byte[] sign(SignatureUpdater updater);

    default byte[] sign(byte[] signature) {
        return this.sign(output -> output.update(signature));
    }

    static Signer from(PrivateKey privateKey, String algorithm) {
        return updater -> {
            try {
                Signature instance = Signature.getInstance(algorithm);
                instance.initSign(privateKey);
                updater.update(instance::update);
                return instance.sign();
            } catch (Exception var4) {
                throw new IllegalStateException("Failed to sign message", var4);
            }
        };
    }
}
