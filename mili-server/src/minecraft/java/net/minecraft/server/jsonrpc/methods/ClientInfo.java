package net.minecraft.server.jsonrpc.methods;

public record ClientInfo(Integer connectionId) {
    public static ClientInfo of(Integer connectionId) {
        return new ClientInfo(connectionId);
    }
}
