package net.minecraft.server.jsonrpc.security;

import java.security.SecureRandom;

public record SecurityConfig(String secretKey) {
    private static final String SECRET_KEY_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    public static boolean isValid(String key) {
        return !key.isEmpty() && key.matches("^[a-zA-Z0-9]{40}$");
    }

    public static String generateSecretKey() {
        SecureRandom secureRandom = new SecureRandom();
        StringBuilder stringBuilder = new StringBuilder(40);

        for (int i = 0; i < 40; i++) {
            stringBuilder.append(
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
                    .charAt(secureRandom.nextInt("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".length()))
            );
        }

        return stringBuilder.toString();
    }
}
