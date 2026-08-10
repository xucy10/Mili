package net.minecraft.server.jsonrpc.methods;

public class InvalidRequestJsonRpcException extends RuntimeException {
    public InvalidRequestJsonRpcException(String message) {
        super(message);
    }
}
