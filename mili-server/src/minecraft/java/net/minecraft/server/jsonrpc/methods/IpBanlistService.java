package net.minecraft.server.jsonrpc.methods;

import com.google.common.net.InetAddresses;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;
import net.minecraft.server.jsonrpc.api.PlayerDto;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.IpBanListEntry;
import net.minecraft.util.ExtraCodecs;
import org.jspecify.annotations.Nullable;

public class IpBanlistService {
    private static final String BAN_SOURCE = "Management server";

    public static List<IpBanlistService.IpBanDto> get(MinecraftApi api) {
        return api.banListService().getIpBanEntries().stream().map(IpBanlistService.IpBan::from).map(IpBanlistService.IpBanDto::from).toList();
    }

    public static List<IpBanlistService.IpBanDto> add(MinecraftApi api, List<IpBanlistService.IncomingIpBanDto> ipBan, ClientInfo client) {
        ipBan.stream()
            .map(incomingIpBanDto -> banIp(api, incomingIpBanDto, client))
            .flatMap(Collection::stream)
            .forEach(serverPlayer -> serverPlayer.connection.disconnect(Component.translatable("multiplayer.disconnect.ip_banned")));
        return get(api);
    }

    private static List<ServerPlayer> banIp(MinecraftApi api, IpBanlistService.IncomingIpBanDto ipBan, ClientInfo client) {
        IpBanlistService.IpBan ipBan1 = ipBan.toIpBan();
        if (ipBan1 != null) {
            return banIp(api, ipBan1, client);
        } else {
            if (ipBan.player().isPresent()) {
                Optional<ServerPlayer> player = api.playerListService().getPlayer(ipBan.player().get().id(), ipBan.player().get().name());
                if (player.isPresent()) {
                    return banIp(api, ipBan.toIpBan(player.get()), client);
                }
            }

            return List.of();
        }
    }

    private static List<ServerPlayer> banIp(MinecraftApi api, IpBanlistService.IpBan ipBan, ClientInfo client) {
        api.banListService().addIpBan(ipBan.toIpBanEntry(), client);
        return api.playerListService().getPlayersWithAddress(ipBan.ip());
    }

    public static List<IpBanlistService.IpBanDto> clear(MinecraftApi api, ClientInfo client) {
        api.banListService().clearIpBans(client);
        return get(api);
    }

    public static List<IpBanlistService.IpBanDto> remove(MinecraftApi api, List<String> ips, ClientInfo client) {
        ips.forEach(string -> api.banListService().removeIpBan(string, client));
        return get(api);
    }

    public static List<IpBanlistService.IpBanDto> set(MinecraftApi api, List<IpBanlistService.IpBanDto> ipBans, ClientInfo client) {
        Set<IpBanlistService.IpBan> set = ipBans.stream()
            .filter(ipBanDto -> InetAddresses.isInetAddress(ipBanDto.ip()))
            .map(IpBanlistService.IpBanDto::toIpBan)
            .collect(Collectors.toSet());
        Set<IpBanlistService.IpBan> set1 = api.banListService().getIpBanEntries().stream().map(IpBanlistService.IpBan::from).collect(Collectors.toSet());
        set1.stream().filter(ipBan -> !set.contains(ipBan)).forEach(ipBan -> api.banListService().removeIpBan(ipBan.ip(), client));
        set.stream().filter(ipBan -> !set1.contains(ipBan)).forEach(ipBan -> api.banListService().addIpBan(ipBan.toIpBanEntry(), client));
        set.stream()
            .filter(ipBan -> !set1.contains(ipBan))
            .flatMap(ipBan -> api.playerListService().getPlayersWithAddress(ipBan.ip()).stream())
            .forEach(serverPlayer -> serverPlayer.connection.disconnect(Component.translatable("multiplayer.disconnect.ip_banned")));
        return get(api);
    }

    public record IncomingIpBanDto(Optional<PlayerDto> player, Optional<String> ip, Optional<String> reason, Optional<String> source, Optional<Instant> expires) {
        public static final MapCodec<IpBanlistService.IncomingIpBanDto> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    PlayerDto.CODEC.codec().optionalFieldOf("player").forGetter(IpBanlistService.IncomingIpBanDto::player),
                    Codec.STRING.optionalFieldOf("ip").forGetter(IpBanlistService.IncomingIpBanDto::ip),
                    Codec.STRING.optionalFieldOf("reason").forGetter(IpBanlistService.IncomingIpBanDto::reason),
                    Codec.STRING.optionalFieldOf("source").forGetter(IpBanlistService.IncomingIpBanDto::source),
                    ExtraCodecs.INSTANT_ISO8601.optionalFieldOf("expires").forGetter(IpBanlistService.IncomingIpBanDto::expires)
                )
                .apply(instance, IpBanlistService.IncomingIpBanDto::new)
        );

        IpBanlistService.IpBan toIpBan(ServerPlayer player) {
            return new IpBanlistService.IpBan(player.getIpAddress(), this.reason().orElse(null), this.source().orElse("Management server"), this.expires());
        }

        IpBanlistService.@Nullable IpBan toIpBan() {
            return !this.ip().isEmpty() && InetAddresses.isInetAddress(this.ip().get())
                ? new IpBanlistService.IpBan(this.ip().get(), this.reason().orElse(null), this.source().orElse("Management server"), this.expires())
                : null;
        }
    }

    record IpBan(String ip, @Nullable String reason, String source, Optional<Instant> expires) {
        static IpBanlistService.IpBan from(IpBanListEntry entry) {
            return new IpBanlistService.IpBan(
                Objects.requireNonNull(entry.getUser()), entry.getReason(), entry.getSource(), Optional.ofNullable(entry.getExpires()).map(Date::toInstant)
            );
        }

        IpBanListEntry toIpBanEntry() {
            return new IpBanListEntry(this.ip(), null, this.source(), this.expires().map(Date::from).orElse(null), this.reason());
        }
    }

    public record IpBanDto(String ip, Optional<String> reason, Optional<String> source, Optional<Instant> expires) {
        public static final MapCodec<IpBanlistService.IpBanDto> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Codec.STRING.fieldOf("ip").forGetter(IpBanlistService.IpBanDto::ip),
                    Codec.STRING.optionalFieldOf("reason").forGetter(IpBanlistService.IpBanDto::reason),
                    Codec.STRING.optionalFieldOf("source").forGetter(IpBanlistService.IpBanDto::source),
                    ExtraCodecs.INSTANT_ISO8601.optionalFieldOf("expires").forGetter(IpBanlistService.IpBanDto::expires)
                )
                .apply(instance, IpBanlistService.IpBanDto::new)
        );

        private static IpBanlistService.IpBanDto from(IpBanlistService.IpBan ipBan) {
            return new IpBanlistService.IpBanDto(ipBan.ip(), Optional.ofNullable(ipBan.reason()), Optional.of(ipBan.source()), ipBan.expires());
        }

        public static IpBanlistService.IpBanDto from(IpBanListEntry entry) {
            return from(IpBanlistService.IpBan.from(entry));
        }

        private IpBanlistService.IpBan toIpBan() {
            return new IpBanlistService.IpBan(this.ip(), this.reason().orElse(null), this.source().orElse("Management server"), this.expires());
        }
    }
}
