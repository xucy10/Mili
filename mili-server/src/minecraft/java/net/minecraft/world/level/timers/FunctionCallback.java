package net.minecraft.world.level.timers;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionManager;

public record FunctionCallback(Identifier functionId) implements TimerCallback<MinecraftServer> {
    public static final MapCodec<FunctionCallback> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(Identifier.CODEC.fieldOf("Name").forGetter(FunctionCallback::functionId)).apply(instance, FunctionCallback::new)
    );

    @Override
    public void handle(MinecraftServer server, TimerQueue<MinecraftServer> manager, long gameTime) {
        ServerFunctionManager functions = server.getFunctions();
        functions.get(this.functionId)
            .ifPresent(commandFunction -> functions.execute((CommandFunction<CommandSourceStack>)commandFunction, functions.getGameLoopSender()));
    }

    @Override
    public MapCodec<FunctionCallback> codec() {
        return CODEC;
    }
}
