package net.minecraft.server.jsonrpc.methods;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.jsonrpc.api.PlayerDto;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

public class PlayerService {
    private static final Component DEFAULT_KICK_MESSAGE = Component.translatable("multiplayer.disconnect.kicked");

    public static List<PlayerDto> get(MinecraftApi api) {
        return api.playerListService().getPlayers().stream().map(PlayerDto::from).toList();
    }

    public static List<PlayerDto> kick(MinecraftApi api, List<PlayerService.KickDto> kicks, ClientInfo client) {
        List<PlayerDto> list = new ArrayList<>();

        for (PlayerService.KickDto kickDto : kicks) {
            ServerPlayer serverPlayer = getServerPlayer(api, kickDto.player());
            if (serverPlayer != null) {
                api.playerListService().remove(serverPlayer, client);
                serverPlayer.connection.disconnect(kickDto.message.flatMap(Message::asComponent).orElse(DEFAULT_KICK_MESSAGE));
                list.add(kickDto.player());
            }
        }

        return list;
    }

    private static @Nullable ServerPlayer getServerPlayer(MinecraftApi api, PlayerDto player) {
        if (player.id().isPresent()) {
            return api.playerListService().getPlayer(player.id().get());
        } else {
            return player.name().isPresent() ? api.playerListService().getPlayerByName(player.name().get()) : null;
        }
    }

    public record KickDto(PlayerDto player, Optional<Message> message) {
        public static final MapCodec<PlayerService.KickDto> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    PlayerDto.CODEC.codec().fieldOf("player").forGetter(PlayerService.KickDto::player),
                    Message.CODEC.optionalFieldOf("message").forGetter(PlayerService.KickDto::message)
                )
                .apply(instance, PlayerService.KickDto::new)
        );
    }
}
