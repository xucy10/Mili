package net.minecraft.server.jsonrpc.methods;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.server.jsonrpc.api.PlayerDto;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.StoredUserEntry;
import net.minecraft.server.players.UserWhiteListEntry;
import net.minecraft.util.Util;

public class AllowlistService {
    public static List<PlayerDto> get(MinecraftApi api) {
        return api.allowListService()
            .getEntries()
            .stream()
            .filter(userWhiteListEntry -> userWhiteListEntry.getUser() != null)
            .map(userWhiteListEntry -> PlayerDto.from(userWhiteListEntry.getUser()))
            .toList();
    }

    public static List<PlayerDto> add(MinecraftApi api, List<PlayerDto> players, ClientInfo client) {
        List<CompletableFuture<Optional<NameAndId>>> list = players.stream()
            .map(playerDto -> api.playerListService().getUser(playerDto.id(), playerDto.name()))
            .toList();

        for (Optional<NameAndId> optional : Util.sequence(list).join()) {
            optional.ifPresent(nameAndId -> api.allowListService().add(new UserWhiteListEntry(nameAndId), client));
        }

        return get(api);
    }

    public static List<PlayerDto> clear(MinecraftApi api, ClientInfo client) {
        api.allowListService().clear(client);
        return get(api);
    }

    public static List<PlayerDto> remove(MinecraftApi api, List<PlayerDto> players, ClientInfo client) {
        List<CompletableFuture<Optional<NameAndId>>> list = players.stream()
            .map(playerDto -> api.playerListService().getUser(playerDto.id(), playerDto.name()))
            .toList();

        for (Optional<NameAndId> optional : Util.sequence(list).join()) {
            optional.ifPresent(nameAndId -> api.allowListService().remove(nameAndId, client));
        }

        api.allowListService().kickUnlistedPlayers(client);
        return get(api);
    }

    public static List<PlayerDto> set(MinecraftApi api, List<PlayerDto> players, ClientInfo client) {
        List<CompletableFuture<Optional<NameAndId>>> list = players.stream()
            .map(playerDto -> api.playerListService().getUser(playerDto.id(), playerDto.name()))
            .toList();
        Set<NameAndId> set = Util.sequence(list).join().stream().flatMap(Optional::stream).collect(Collectors.toSet());
        Set<NameAndId> set1 = api.allowListService().getEntries().stream().map(StoredUserEntry::getUser).collect(Collectors.toSet());
        set1.stream().filter(nameAndId -> !set.contains(nameAndId)).forEach(nameAndId -> api.allowListService().remove(nameAndId, client));
        set.stream().filter(nameAndId -> !set1.contains(nameAndId)).forEach(nameAndId -> api.allowListService().add(new UserWhiteListEntry(nameAndId), client));
        api.allowListService().kickUnlistedPlayers(client);
        return get(api);
    }
}
