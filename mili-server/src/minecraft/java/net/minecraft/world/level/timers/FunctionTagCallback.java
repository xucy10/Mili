package net.minecraft.world.level.timers;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionManager;

public record FunctionTagCallback(Identifier tagId) implements TimerCallback<MinecraftServer> {
    public static final MapCodec<FunctionTagCallback> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(Identifier.CODEC.fieldOf("Name").forGetter(FunctionTagCallback::tagId)).apply(instance, FunctionTagCallback::new)
    );

    @Override
    public void handle(MinecraftServer server, TimerQueue<MinecraftServer> manager, long gameTime) {
        ServerFunctionManager functions = server.getFunctions();

        for (CommandFunction<CommandSourceStack> commandFunction : functions.getTag(this.tagId)) {
            functions.execute(commandFunction, functions.getGameLoopSender());
        }
    }

    @Override
    public MapCodec<FunctionTagCallback> codec() {
        return CODEC;
    }
}
