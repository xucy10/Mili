package net.minecraft.server.jsonrpc;

import com.google.gson.JsonElement;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Holder;

public record PendingRpcRequest<Result>(
    Holder.Reference<? extends OutgoingRpcMethod<?, ? extends Result>> method, CompletableFuture<Result> resultFuture, long timeoutTime
) {
    public void accept(JsonElement result) {
        try {
            Result object = (Result)this.method.value().decodeResult(result);
            this.resultFuture.complete(Objects.requireNonNull(object));
        } catch (Exception var3) {
            this.resultFuture.completeExceptionally(var3);
        }
    }

    public boolean timedOut(long time) {
        return time > this.timeoutTime;
    }
}
