package net.minecraft.server.jsonrpc.websocket;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import java.util.List;

public class WebSocketToJsonCodec extends MessageToMessageDecoder<TextWebSocketFrame> {
    @Override
    protected void decode(ChannelHandlerContext ctx, TextWebSocketFrame message, List<Object> out) {
        JsonElement jsonElement = JsonParser.parseString(message.text());
        out.add(jsonElement);
    }
}
