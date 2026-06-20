package net.minecraft.server.jsonrpc.methods;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.server.jsonrpc.api.PlayerDto;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.util.Util;

public class OperatorService {
    public static List<OperatorService.OperatorDto> get(MinecraftApi api) {
        return api.operatorListService()
            .getEntries()
            .stream()
            .filter(serverOpListEntry -> serverOpListEntry.getUser() != null)
            .map(OperatorService.OperatorDto::from)
            .toList();
    }

    public static List<OperatorService.OperatorDto> clear(MinecraftApi api, ClientInfo client) {
        api.operatorListService().clear(client);
        return get(api);
    }

    public static List<OperatorService.OperatorDto> remove(MinecraftApi api, List<PlayerDto> players, ClientInfo client) {
        List<CompletableFuture<Optional<NameAndId>>> list = players.stream()
            .map(playerDto -> api.playerListService().getUser(playerDto.id(), playerDto.name()))
            .toList();

        for (Optional<NameAndId> optional : Util.sequence(list).join()) {
            optional.ifPresent(nameAndId -> api.operatorListService().deop(nameAndId, client));
        }

        return get(api);
    }

    public static List<OperatorService.OperatorDto> add(MinecraftApi api, List<OperatorService.OperatorDto> operators, ClientInfo client) {
        List<CompletableFuture<Optional<OperatorService.Op>>> list = operators.stream()
            .map(
                operatorDto -> api.playerListService()
                    .getUser(operatorDto.player().id(), operatorDto.player().name())
                    .thenApply(
                        optional1 -> optional1.map(
                            nameAndId -> new OperatorService.Op(nameAndId, operatorDto.permissionLevel(), operatorDto.bypassesPlayerLimit())
                        )
                    )
            )
            .toList();

        for (Optional<OperatorService.Op> optional : Util.sequence(list).join()) {
            optional.ifPresent(op -> api.operatorListService().op(op.user(), op.permissionLevel(), op.bypassesPlayerLimit(), client));
        }

        return get(api);
    }

    public static List<OperatorService.OperatorDto> set(MinecraftApi api, List<OperatorService.OperatorDto> operators, ClientInfo client) {
        List<CompletableFuture<Optional<OperatorService.Op>>> list = operators.stream()
            .map(
                operatorDto -> api.playerListService()
                    .getUser(operatorDto.player().id(), operatorDto.player().name())
                    .thenApply(
                        optional -> optional.map(
                            nameAndId -> new OperatorService.Op(nameAndId, operatorDto.permissionLevel(), operatorDto.bypassesPlayerLimit())
                        )
                    )
            )
            .toList();
        Set<OperatorService.Op> set = Util.sequence(list).join().stream().flatMap(Optional::stream).collect(Collectors.toSet());
        Set<OperatorService.Op> set1 = api.operatorListService()
            .getEntries()
            .stream()
            .filter(serverOpListEntry -> serverOpListEntry.getUser() != null)
            .map(
                serverOpListEntry -> new OperatorService.Op(
                    serverOpListEntry.getUser(), Optional.of(serverOpListEntry.permissions().level()), Optional.of(serverOpListEntry.getBypassesPlayerLimit())
                )
            )
            .collect(Collectors.toSet());
        set1.stream().filter(op -> !set.contains(op)).forEach(op -> api.operatorListService().deop(op.user(), client));
        set.stream()
            .filter(op -> !set1.contains(op))
            .forEach(op -> api.operatorListService().op(op.user(), op.permissionLevel(), op.bypassesPlayerLimit(), client));
        return get(api);
    }

    record Op(NameAndId user, Optional<PermissionLevel> permissionLevel, Optional<Boolean> bypassesPlayerLimit) {
    }

    public record OperatorDto(PlayerDto player, Optional<PermissionLevel> permissionLevel, Optional<Boolean> bypassesPlayerLimit) {
        public static final MapCodec<OperatorService.OperatorDto> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    PlayerDto.CODEC.codec().fieldOf("player").forGetter(OperatorService.OperatorDto::player),
                    PermissionLevel.INT_CODEC.optionalFieldOf("permissionLevel").forGetter(OperatorService.OperatorDto::permissionLevel),
                    Codec.BOOL.optionalFieldOf("bypassesPlayerLimit").forGetter(OperatorService.OperatorDto::bypassesPlayerLimit)
                )
                .apply(instance, OperatorService.OperatorDto::new)
        );

        public static OperatorService.OperatorDto from(ServerOpListEntry entry) {
            return new OperatorService.OperatorDto(
                PlayerDto.from(Objects.requireNonNull(entry.getUser())), Optional.of(entry.permissions().level()), Optional.of(entry.getBypassesPlayerLimit())
            );
        }
    }
}
