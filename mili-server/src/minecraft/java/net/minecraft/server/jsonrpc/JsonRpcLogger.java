package net.minecraft.server.jsonrpc;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import org.slf4j.Logger;

public class JsonRpcLogger {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "RPC Connection #{}: ";

    public void log(ClientInfo client, String message, Object... args) {
        if (args.length == 0) {
            LOGGER.info("RPC Connection #{}: " + message, client.connectionId());
        } else {
            List<Object> list = new ArrayList<>(Arrays.asList(args));
            list.addFirst(client.connectionId());
            LOGGER.info("RPC Connection #{}: " + message, list.toArray());
        }
    }
}
