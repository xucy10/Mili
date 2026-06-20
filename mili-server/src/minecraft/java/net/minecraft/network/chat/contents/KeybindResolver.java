package net.minecraft.network.chat.contents;

import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

public class KeybindResolver {
    static Function<String, Supplier<Component>> keyResolver = text -> () -> Component.literal(text);

    public static void setKeyResolver(Function<String, Supplier<Component>> keyResolver) {
        KeybindResolver.keyResolver = keyResolver;
    }
}
