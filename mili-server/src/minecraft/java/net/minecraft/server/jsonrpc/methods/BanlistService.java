package net.minecraft.server.jsonrpc.methods;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;
import net.minecraft.server.jsonrpc.api.PlayerDto;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class BanlistService {
    private static final String BAN_SOURCE = "Management server";

    public static List<BanlistService.UserBanDto> get(MinecraftApi api) {
        return api.banListService()
            .getUserBanEntries()
            .stream()
            .filter(userBanListEntry -> userBanListEntry.getUser() != null)
            .map(BanlistService.UserBan::from)
            .map(BanlistService.UserBanDto::from)
            .toList();
    }

    public static List<BanlistService.UserBanDto> add(MinecraftApi api, List<BanlistService.UserBanDto> users, ClientInfo client) {
        List<CompletableFuture<Optional<BanlistService.UserBan>>> list = users.stream()
            .map(
                userBanDto -> api.playerListService()
                    .getUser(userBanDto.player().id(), userBanDto.player().name())
                    .thenApply(optional1 -> optional1.map(userBanDto::toUserBan))
            )
            .toList();

        for (Optional<BanlistService.UserBan> optional : Util.sequence(list).join()) {
            if (!optional.isEmpty()) {
                BanlistService.UserBan userBan = optional.get();
                api.banListService().addUserBan(userBan.toBanEntry(), client);
                ServerPlayer player = api.playerListService().getPlayer(optional.get().player().id());
                if (player != null) {
                    player.connection.disconnect(Component.translatable("multiplayer.disconnect.banned"));
                }
            }
        }

        return get(api);
    }

    public static List<BanlistService.UserBanDto> clear(MinecraftApi api, ClientInfo client) {
        api.banListService().clearUserBans(client);
        return get(api);
    }

    public static List<BanlistService.UserBanDto> remove(MinecraftApi api, List<PlayerDto> players, ClientInfo client) {
        List<CompletableFuture<Optional<NameAndId>>> list = players.stream()
            .map(playerDto -> api.playerListService().getUser(playerDto.id(), playerDto.name()))
            .toList();

        for (Optional<NameAndId> optional : Util.sequence(list).join()) {
            if (!optional.isEmpty()) {
                api.banListService().removeUserBan(optional.get(), client);
            }
        }

        return get(api);
    }

    public static List<BanlistService.UserBanDto> set(MinecraftApi api, List<BanlistService.UserBanDto> users, ClientInfo client) {
        List<CompletableFuture<Optional<BanlistService.UserBan>>> list = users.stream()
            .map(
                userBanDto -> api.playerListService()
                    .getUser(userBanDto.player().id(), userBanDto.player().name())
                    .thenApply(optional -> optional.map(userBanDto::toUserBan))
            )
            .toList();
        Set<BanlistService.UserBan> set = Util.sequence(list).join().stream().flatMap(Optional::stream).collect(Collectors.toSet());
        Set<BanlistService.UserBan> set1 = api.banListService()
            .getUserBanEntries()
            .stream()
            .filter(userBanListEntry -> userBanListEntry.getUser() != null)
            .map(BanlistService.UserBan::from)
            .collect(Collectors.toSet());
        set1.stream().filter(userBan -> !set.contains(userBan)).forEach(userBan -> api.banListService().removeUserBan(userBan.player(), client));
        set.stream().filter(userBan -> !set1.contains(userBan)).forEach(userBan -> {
            api.banListService().addUserBan(userBan.toBanEntry(), client);
            ServerPlayer player = api.playerListService().getPlayer(userBan.player().id());
            if (player != null) {
                player.connection.disconnect(Component.translatable("multiplayer.disconnect.banned"));
            }
        });
        return get(api);
    }

    record UserBan(NameAndId player, @Nullable String reason, String source, Optional<Instant> expires) {
        static BanlistService.UserBan from(UserBanListEntry entry) {
            return new BanlistService.UserBan(
                Objects.requireNonNull(entry.getUser()), entry.getReason(), entry.getSource(), Optional.ofNullable(entry.getExpires()).map(Date::toInstant)
            );
        }

        UserBanListEntry toBanEntry() {
            return new UserBanListEntry(
                new NameAndId(this.player().id(), this.player().name()), null, this.source(), this.expires().map(Date::from).orElse(null), this.reason()
            );
        }
    }

    public record UserBanDto(PlayerDto player, Optional<String> reason, Optional<String> source, Optional<Instant> expires) {
        public static final MapCodec<BanlistService.UserBanDto> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    PlayerDto.CODEC.codec().fieldOf("player").forGetter(BanlistService.UserBanDto::player),
                    Codec.STRING.optionalFieldOf("reason").forGetter(BanlistService.UserBanDto::reason),
                    Codec.STRING.optionalFieldOf("source").forGetter(BanlistService.UserBanDto::source),
                    ExtraCodecs.INSTANT_ISO8601.optionalFieldOf("expires").forGetter(BanlistService.UserBanDto::expires)
                )
                .apply(instance, BanlistService.UserBanDto::new)
        );

        private static BanlistService.UserBanDto from(BanlistService.UserBan user) {
            return new BanlistService.UserBanDto(PlayerDto.from(user.player()), Optional.ofNullable(user.reason()), Optional.of(user.source()), user.expires());
        }

        public static BanlistService.UserBanDto from(UserBanListEntry entry) {
            return from(BanlistService.UserBan.from(entry));
        }

        private BanlistService.UserBan toUserBan(NameAndId nameAndId) {
            return new BanlistService.UserBan(nameAndId, this.reason().orElse(null), this.source().orElse("Management server"), this.expires());
        }
    }
}
