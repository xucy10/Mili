package net.minecraft.commands;

import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerFunctionManager;

public class CacheableFunction {
    public static final Codec<CacheableFunction> CODEC = Identifier.CODEC.xmap(CacheableFunction::new, CacheableFunction::getId);
    private final Identifier id;
    private boolean resolved;
    private Optional<CommandFunction<CommandSourceStack>> function = Optional.empty();

    public CacheableFunction(Identifier id) {
        this.id = id;
    }

    public Optional<CommandFunction<CommandSourceStack>> get(ServerFunctionManager functionManager) {
        if (!this.resolved) {
            this.function = functionManager.get(this.id);
            this.resolved = true;
        }

        return this.function;
    }

    public Identifier getId() {
        return this.id;
    }

    @Override
    public boolean equals(Object other) {
        return other == this || other instanceof CacheableFunction cacheableFunction && this.getId().equals(cacheableFunction.getId());
    }
}
