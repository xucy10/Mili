package net.minecraft.util.context;

import net.minecraft.resources.Identifier;

public class ContextKey<T> {
    private final Identifier name;

    public ContextKey(Identifier name) {
        this.name = name;
    }

    public static <T> ContextKey<T> vanilla(String name) {
        return new ContextKey<>(Identifier.withDefaultNamespace(name));
    }

    public Identifier name() {
        return this.name;
    }

    @Override
    public String toString() {
        return "<parameter " + this.name + ">";
    }
}
