package net.minecraft.commands.synchronization;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.FriendlyByteBuf;

public interface ArgumentTypeInfo<A extends ArgumentType<?>, T extends ArgumentTypeInfo.Template<A>> {
    void serializeToNetwork(T template, FriendlyByteBuf buffer);

    T deserializeFromNetwork(FriendlyByteBuf buffer);

    void serializeToJson(T template, JsonObject json);

    T unpack(A argument);

    public interface Template<A extends ArgumentType<?>> {
        A instantiate(CommandBuildContext context);

        ArgumentTypeInfo<A, ?> type();
    }
}
