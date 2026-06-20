package net.minecraft.server.jsonrpc.security;

import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AttributeKey;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Sharable
public class AuthenticationHandler extends ChannelDuplexHandler {
    private final Logger LOGGER = LogUtils.getLogger();
    private static final AttributeKey<Boolean> AUTHENTICATED_KEY = AttributeKey.valueOf("authenticated");
    private static final AttributeKey<Boolean> ATTR_WEBSOCKET_ALLOWED = AttributeKey.valueOf("websocket_auth_allowed");
    private static final String SUBPROTOCOL_VALUE = "minecraft-v1";
    private static final String SUBPROTOCOL_HEADER_PREFIX = "minecraft-v1,";
    public static final String BEARER_PREFIX = "Bearer ";
    private final SecurityConfig securityConfig;
    private final Set<String> allowedOrigins;

    public AuthenticationHandler(SecurityConfig securityConfig, String allowedOrigins) {
        this.securityConfig = securityConfig;
        this.allowedOrigins = Sets.newHashSet(allowedOrigins.split(","));
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        String clientIp = this.getClientIp(ctx);
        if (msg instanceof HttpRequest httpRequest) {
            AuthenticationHandler.SecurityCheckResult securityCheckResult = this.performSecurityChecks(httpRequest);
            if (!securityCheckResult.isAllowed()) {
                this.LOGGER.debug("Authentication rejected for connection with ip {}: {}", clientIp, securityCheckResult.getReason());
                ctx.channel().attr(AUTHENTICATED_KEY).set(false);
                this.sendUnauthorizedResponse(ctx, securityCheckResult.getReason());
                return;
            }

            ctx.channel().attr(AUTHENTICATED_KEY).set(true);
            if (securityCheckResult.isTokenSentInSecWebsocketProtocol()) {
                ctx.channel().attr(ATTR_WEBSOCKET_ALLOWED).set(Boolean.TRUE);
            }
        }

        Boolean _boolean = ctx.channel().attr(AUTHENTICATED_KEY).get();
        if (Boolean.TRUE.equals(_boolean)) {
            super.channelRead(ctx, msg);
        } else {
            this.LOGGER.debug("Dropping unauthenticated connection with ip {}", clientIp);
            ctx.close();
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof HttpResponse httpResponse
            && httpResponse.status().code() == HttpResponseStatus.SWITCHING_PROTOCOLS.code()
            && ctx.channel().attr(ATTR_WEBSOCKET_ALLOWED).get() != null
            && ctx.channel().attr(ATTR_WEBSOCKET_ALLOWED).get().equals(Boolean.TRUE)) {
            httpResponse.headers().set(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL, "minecraft-v1");
        }

        super.write(ctx, msg, promise);
    }

    private AuthenticationHandler.SecurityCheckResult performSecurityChecks(HttpRequest request) {
        String string = this.parseTokenInAuthorizationHeader(request);
        if (string != null) {
            return this.isValidApiKey(string)
                ? AuthenticationHandler.SecurityCheckResult.allowed()
                : AuthenticationHandler.SecurityCheckResult.denied("Invalid API key");
        } else {
            String string1 = this.parseTokenInSecWebsocketProtocolHeader(request);
            if (string1 != null) {
                if (!this.isAllowedOriginHeader(request)) {
                    return AuthenticationHandler.SecurityCheckResult.denied("Origin Not Allowed");
                } else {
                    return this.isValidApiKey(string1)
                        ? AuthenticationHandler.SecurityCheckResult.allowed(true)
                        : AuthenticationHandler.SecurityCheckResult.denied("Invalid API key");
                }
            } else {
                return AuthenticationHandler.SecurityCheckResult.denied("Missing API key");
            }
        }
    }

    private boolean isAllowedOriginHeader(HttpRequest request) {
        String string = request.headers().get(HttpHeaderNames.ORIGIN);
        return string != null && !string.isEmpty() && this.allowedOrigins.contains(string);
    }

    private @Nullable String parseTokenInAuthorizationHeader(HttpRequest request) {
        String string = request.headers().get(HttpHeaderNames.AUTHORIZATION);
        return string != null && string.startsWith("Bearer ") ? string.substring("Bearer ".length()).trim() : null;
    }

    private @Nullable String parseTokenInSecWebsocketProtocolHeader(HttpRequest request) {
        String string = request.headers().get(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL);
        return string != null && string.startsWith("minecraft-v1,") ? string.substring("minecraft-v1,".length()).trim() : null;
    }

    public boolean isValidApiKey(String key) {
        if (key.isEmpty()) {
            return false;
        } else {
            byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] bytes1 = this.securityConfig.secretKey().getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(bytes, bytes1);
        }
    }

    private String getClientIp(ChannelHandlerContext ctx) {
        InetSocketAddress inetSocketAddress = (InetSocketAddress)ctx.channel().remoteAddress();
        return inetSocketAddress.getAddress().getHostAddress();
    }

    private void sendUnauthorizedResponse(ChannelHandlerContext ctx, String message) {
        String string = "{\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}";
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        DefaultFullHttpResponse defaultFullHttpResponse = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED, Unpooled.wrappedBuffer(bytes)
        );
        defaultFullHttpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        defaultFullHttpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        defaultFullHttpResponse.headers().set(HttpHeaderNames.CONNECTION, "close");
        ctx.writeAndFlush(defaultFullHttpResponse).addListener(future -> ctx.close());
    }

    static class SecurityCheckResult {
        private final boolean allowed;
        private final String reason;
        private final boolean tokenSentInSecWebsocketProtocol;

        private SecurityCheckResult(boolean allowed, String reason, boolean tokenSentInSecWebsocketProtocol) {
            this.allowed = allowed;
            this.reason = reason;
            this.tokenSentInSecWebsocketProtocol = tokenSentInSecWebsocketProtocol;
        }

        public static AuthenticationHandler.SecurityCheckResult allowed() {
            return new AuthenticationHandler.SecurityCheckResult(true, null, false);
        }

        public static AuthenticationHandler.SecurityCheckResult allowed(boolean tokenSentInSecWebsocketProtocol) {
            return new AuthenticationHandler.SecurityCheckResult(true, null, tokenSentInSecWebsocketProtocol);
        }

        public static AuthenticationHandler.SecurityCheckResult denied(String reason) {
            return new AuthenticationHandler.SecurityCheckResult(false, reason, false);
        }

        public boolean isAllowed() {
            return this.allowed;
        }

        public String getReason() {
            return this.reason;
        }

        public boolean isTokenSentInSecWebsocketProtocol() {
            return this.tokenSentInSecWebsocketProtocol;
        }
    }
}
