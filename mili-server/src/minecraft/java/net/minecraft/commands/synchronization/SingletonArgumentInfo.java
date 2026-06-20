package net.minecraft.commands.synchronization;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.ArgumentType;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.FriendlyByteBuf;

public class SingletonArgumentInfo<A extends ArgumentType<?>> implements ArgumentTypeInfo<A, SingletonArgumentInfo<A>.Template> {
    private final SingletonArgumentInfo<A>.Template template;

    private SingletonArgumentInfo(Function<CommandBuildContext, A> constructor) {
        this.template = new SingletonArgumentInfo.Template(constructor);
    }

    public static <T extends ArgumentType<?>> SingletonArgumentInfo<T> contextFree(Supplier<T> argumentTypeSupplier) {
        return new SingletonArgumentInfo<>(context -> argumentTypeSupplier.get());
    }

    public static <T extends ArgumentType<?>> SingletonArgumentInfo<T> contextAware(Function<CommandBuildContext, T> argumentType) {
        return new SingletonArgumentInfo<>(argumentType);
    }

    @Override
    public void serializeToNetwork(SingletonArgumentInfo<A>.Template template, FriendlyByteBuf buffer) {
    }

    @Override
    public void serializeToJson(SingletonArgumentInfo<A>.Template template, JsonObject json) {
    }

    @Override
    public SingletonArgumentInfo<A>.Template deserializeFromNetwork(FriendlyByteBuf buffer) {
        return this.template;
    }

    @Override
    public SingletonArgumentInfo<A>.Template unpack(A argument) {
        return this.template;
    }

    public final class Template implements ArgumentTypeInfo.Template<A> {
        private final Function<CommandBuildContext, A> constructor;

        public Template(final Function<CommandBuildContext, A> constructor) {
            this.constructor = constructor;
        }

        @Override
        public A instantiate(CommandBuildContext context) {
            return this.constructor.apply(context);
        }

        @Override
        public ArgumentTypeInfo<A, ?> type() {
            return SingletonArgumentInfo.this;
        }
    }
}
