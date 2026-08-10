package net.minecraft.server.jsonrpc.methods;

public class MethodNotFoundJsonRpcException extends RuntimeException {
    public MethodNotFoundJsonRpcException(String message) {
        super(message);
    }
}
