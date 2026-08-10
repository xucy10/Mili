package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;

public class ServerPackCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("serverpack")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.literal("push")
                        .then(
                            Commands.argument("url", StringArgumentType.string())
                                .then(
                                    Commands.argument("uuid", UuidArgument.uuid())
                                        .then(
                                            Commands.argument("hash", StringArgumentType.word())
                                                .executes(
                                                    commandContext -> pushPack(
                                                        commandContext.getSource(),
                                                        StringArgumentType.getString(commandContext, "url"),
                                                        Optional.of(UuidArgument.getUuid(commandContext, "uuid")),
                                                        Optional.of(StringArgumentType.getString(commandContext, "hash"))
                                                    )
                                                )
                                        )
                                        .executes(
                                            commandContext -> pushPack(
                                                commandContext.getSource(),
                                                StringArgumentType.getString(commandContext, "url"),
                                                Optional.of(UuidArgument.getUuid(commandContext, "uuid")),
                                                Optional.empty()
                                            )
                                        )
                                )
                                .executes(
                                    commandContext -> pushPack(
                                        commandContext.getSource(), StringArgumentType.getString(commandContext, "url"), Optional.empty(), Optional.empty()
                                    )
                                )
                        )
                )
                .then(
                    Commands.literal("pop")
                        .then(
                            Commands.argument("uuid", UuidArgument.uuid())
                                .executes(commandContext -> popPack(commandContext.getSource(), UuidArgument.getUuid(commandContext, "uuid")))
                        )
                )
        );
    }

    private static void sendToAllConnections(CommandSourceStack source, Packet<?> packet) {
        source.getServer().getConnection().getConnections().forEach(connection -> connection.send(packet));
    }

    private static int pushPack(CommandSourceStack source, String url, Optional<UUID> uuid, Optional<String> hash) {
        UUID uuid1 = uuid.orElseGet(() -> UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8)));
        String string = hash.orElse("");
        ClientboundResourcePackPushPacket clientboundResourcePackPushPacket = new ClientboundResourcePackPushPacket(uuid1, url, string, false, null);
        sendToAllConnections(source, clientboundResourcePackPushPacket);
        return 0;
    }

    private static int popPack(CommandSourceStack source, UUID uuid) {
        ClientboundResourcePackPopPacket clientboundResourcePackPopPacket = new ClientboundResourcePackPopPacket(Optional.of(uuid));
        sendToAllConnections(source, clientboundResourcePackPopPacket);
        return 0;
    }
}
