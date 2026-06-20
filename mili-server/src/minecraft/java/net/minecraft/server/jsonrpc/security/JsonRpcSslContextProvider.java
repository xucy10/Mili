package net.minecraft.server.jsonrpc.security;

import com.mojang.logging.LogUtils;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import org.slf4j.Logger;

public class JsonRpcSslContextProvider {
    private static final String PASSWORD_ENV_VARIABLE_KEY = "MINECRAFT_MANAGEMENT_TLS_KEYSTORE_PASSWORD";
    private static final String PASSWORD_SYSTEM_PROPERTY_KEY = "management.tls.keystore.password";
    private static final Logger log = LogUtils.getLogger();

    public static SslContext createFrom(String keystore, String password) throws Exception {
        if (keystore.isEmpty()) {
            throw new IllegalArgumentException("TLS is enabled but keystore is not configured");
        } else {
            File file = new File(keystore);
            if (file.exists() && file.isFile()) {
                String keystorePassword = getKeystorePassword(password);
                return loadKeystoreFromPath(file, keystorePassword);
            } else {
                throw new IllegalArgumentException("Supplied keystore is not a file or does not exist: '" + keystore + "'");
            }
        }
    }

    private static String getKeystorePassword(String password) {
        String string = System.getenv().get("MINECRAFT_MANAGEMENT_TLS_KEYSTORE_PASSWORD");
        if (string != null) {
            return string;
        } else {
            String property = System.getProperty("management.tls.keystore.password", null);
            return property != null ? property : password;
        }
    }

    private static SslContext loadKeystoreFromPath(File path, String password) throws Exception {
        KeyStore instance = KeyStore.getInstance("PKCS12");

        try (InputStream inputStream = new FileInputStream(path)) {
            instance.load(inputStream, password.toCharArray());
        }

        KeyManagerFactory instance1 = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        instance1.init(instance, password.toCharArray());
        TrustManagerFactory instance2 = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        instance2.init(instance);
        return SslContextBuilder.forServer(instance1).trustManager(instance2).build();
    }

    public static void printInstructions() {
        log.info("To use TLS for the management server, please follow these steps:");
        log.info("1. Set the server property 'management-server-tls-enabled' to 'true' to enable TLS");
        log.info("2. Create a keystore file of type PKCS12 containing your server certificate and private key");
        log.info("3. Set the server property 'management-server-tls-keystore' to the path of your keystore file");
        log.info(
            "4. Set the keystore password via the environment variable 'MINECRAFT_MANAGEMENT_TLS_KEYSTORE_PASSWORD', or system property 'management.tls.keystore.password', or server property 'management-server-tls-keystore-password'"
        );
        log.info("5. Restart the server to apply the changes.");
    }
}
