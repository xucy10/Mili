package net.minecraft.server.jsonrpc.websocket;

import com.google.gson.JsonElement;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import java.util.List;

public class JsonToWebSocketEncoder extends MessageToMessageEncoder<JsonElement> {
    @Override
    protected void encode(ChannelHandlerContext ctx, JsonElement message, List<Object> out) {
        out.add(new TextWebSocketFrame(message.toString()));
    }
}
