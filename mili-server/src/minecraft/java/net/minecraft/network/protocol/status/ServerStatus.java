package net.minecraft.network.protocol.status;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.WorldVersion;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.players.NameAndId;

public record ServerStatus(
    Component description,
    Optional<ServerStatus.Players> players,
    Optional<ServerStatus.Version> version,
    Optional<ServerStatus.Favicon> favicon,
    boolean enforcesSecureChat
) {
    public static final Codec<ServerStatus> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                ComponentSerialization.CODEC.lenientOptionalFieldOf("description", CommonComponents.EMPTY).forGetter(ServerStatus::description),
                ServerStatus.Players.CODEC.lenientOptionalFieldOf("players").forGetter(ServerStatus::players),
                ServerStatus.Version.CODEC.lenientOptionalFieldOf("version").forGetter(ServerStatus::version),
                ServerStatus.Favicon.CODEC.lenientOptionalFieldOf("favicon").forGetter(ServerStatus::favicon),
                Codec.BOOL.lenientOptionalFieldOf("enforcesSecureChat", false).forGetter(ServerStatus::enforcesSecureChat)
            )
            .apply(instance, ServerStatus::new)
    );

    public record Favicon(byte[] iconBytes) {
        private static final String PREFIX = "data:image/png;base64,";
        public static final Codec<ServerStatus.Favicon> CODEC = Codec.STRING.comapFlatMap(string -> {
            if (!string.startsWith("data:image/png;base64,")) {
                return DataResult.error(() -> "Unknown format");
            } else {
                try {
                    String string1 = string.substring("data:image/png;base64,".length()).replaceAll("\n", "");
                    byte[] bytes = Base64.getDecoder().decode(string1.getBytes(StandardCharsets.UTF_8));
                    return DataResult.success(new ServerStatus.Favicon(bytes));
                } catch (IllegalArgumentException var3) {
                    return DataResult.error(() -> "Malformed base64 server icon");
                }
            }
        }, favicon -> "data:image/png;base64," + new String(Base64.getEncoder().encode(favicon.iconBytes), StandardCharsets.UTF_8));
    }

    public record Players(int max, int online, List<NameAndId> sample) {
        public static final Codec<ServerStatus.Players> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.INT.fieldOf("max").forGetter(ServerStatus.Players::max),
                    Codec.INT.fieldOf("online").forGetter(ServerStatus.Players::online),
                    NameAndId.CODEC.listOf().lenientOptionalFieldOf("sample", List.of()).forGetter(ServerStatus.Players::sample)
                )
                .apply(instance, ServerStatus.Players::new)
        );
    }

    public record Version(String name, int protocol) {
        public static final Codec<ServerStatus.Version> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.STRING.fieldOf("name").forGetter(ServerStatus.Version::name), Codec.INT.fieldOf("protocol").forGetter(ServerStatus.Version::protocol)
                )
                .apply(instance, ServerStatus.Version::new)
        );

        public static ServerStatus.Version current() {
            WorldVersion currentVersion = SharedConstants.getCurrentVersion();
            return new ServerStatus.Version(currentVersion.name(), currentVersion.protocolVersion());
        }
    }
}
