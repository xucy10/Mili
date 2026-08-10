package net.minecraft.data.info;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.handshake.HandshakeProtocols;
import net.minecraft.network.protocol.login.LoginProtocols;
import net.minecraft.network.protocol.status.StatusProtocols;

public class PacketReport implements DataProvider {
    private final PackOutput output;

    public PacketReport(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        Path path = this.output.getOutputFolder(PackOutput.Target.REPORTS).resolve("packets.json");
        return DataProvider.saveStable(output, this.serializePackets(), path);
    }

    private JsonElement serializePackets() {
        JsonObject jsonObject = new JsonObject();
        Stream.of(
                HandshakeProtocols.SERVERBOUND_TEMPLATE,
                StatusProtocols.CLIENTBOUND_TEMPLATE,
                StatusProtocols.SERVERBOUND_TEMPLATE,
                LoginProtocols.CLIENTBOUND_TEMPLATE,
                LoginProtocols.SERVERBOUND_TEMPLATE,
                ConfigurationProtocols.CLIENTBOUND_TEMPLATE,
                ConfigurationProtocols.SERVERBOUND_TEMPLATE,
                GameProtocols.CLIENTBOUND_TEMPLATE,
                GameProtocols.SERVERBOUND_TEMPLATE
            )
            .map(ProtocolInfo.DetailsProvider::details)
            .collect(Collectors.groupingBy(ProtocolInfo.Details::id))
            .forEach((connectionProtocol, list) -> {
                JsonObject jsonObject1 = new JsonObject();
                jsonObject.add(connectionProtocol.id(), jsonObject1);
                list.forEach(details -> {
                    JsonObject jsonObject2 = new JsonObject();
                    jsonObject1.add(details.flow().id(), jsonObject2);
                    details.listPackets((packetType, index) -> {
                        JsonObject jsonObject3 = new JsonObject();
                        jsonObject3.addProperty("protocol_id", index);
                        jsonObject2.add(packetType.id().toString(), jsonObject3);
                    });
                });
            });
        return jsonObject;
    }

    @Override
    public String getName() {
        return "Packet Report";
    }
}
