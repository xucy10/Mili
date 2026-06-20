package net.minecraft.server.rcon.thread;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;
import net.minecraft.server.ServerInterface;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class RconThread extends GenericThread {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ServerSocket socket;
    private final String rconPassword;
    private final List<RconClient> clients = Lists.newArrayList();
    private final ServerInterface serverInterface;

    private RconThread(ServerInterface serverInterface, ServerSocket socket, String rconPassword) {
        super("RCON Listener");
        this.serverInterface = serverInterface;
        this.socket = socket;
        this.rconPassword = rconPassword;
    }

    private void clearClients() {
        this.clients.removeIf(rconClient -> !rconClient.isRunning());
    }

    @Override
    public void run() {
        try {
            while (this.running) {
                try {
                    Socket socket = this.socket.accept();
                    RconClient rconClient = new RconClient(this.serverInterface, this.rconPassword, socket);
                    rconClient.start();
                    this.clients.add(rconClient);
                    this.clearClients();
                } catch (SocketTimeoutException var7) {
                    this.clearClients();
                } catch (IOException var8) {
                    if (this.running) {
                        LOGGER.info("IO exception: ", (Throwable)var8);
                    }
                }
            }
        } finally {
            this.closeSocket(this.socket);
        }
    }

    public static @Nullable RconThread create(ServerInterface serverInterface) {
        DedicatedServerProperties properties = serverInterface.getProperties();
        String serverIp = properties.rconIp; // Paper - Configurable rcon ip
        if (serverIp.isEmpty()) {
            serverIp = "0.0.0.0";
        }

        int i = properties.rconPort;
        if (0 < i && 65535 >= i) {
            String string = properties.rconPassword;
            if (string.isEmpty()) {
                LOGGER.warn("No rcon password set in server.properties, rcon disabled!");
                return null;
            } else {
                try {
                    ServerSocket serverSocket = new ServerSocket(i, 0, InetAddress.getByName(serverIp));
                    serverSocket.setSoTimeout(500);
                    RconThread rconThread = new RconThread(serverInterface, serverSocket, string);
                    if (!rconThread.start()) {
                        return null;
                    } else {
                        LOGGER.info("RCON running on {}:{}", serverIp, i);
                        return rconThread;
                    }
                } catch (IOException var7) {
                    LOGGER.warn("Unable to initialise RCON on {}:{}", serverIp, i, var7);
                    return null;
                }
            }
        } else {
            LOGGER.warn("Invalid rcon port {} found in server.properties, rcon disabled!", i);
            return null;
        }
    }

    @Override
    public void stop() {
        this.running = false;
        this.closeSocket(this.socket);
        super.stop();

        for (RconClient rconClient : this.clients) {
            if (rconClient.isRunning()) {
                rconClient.stop();
            }
        }

        this.clients.clear();
    }
    // Paper start - don't wait for remote connections
    public void stopNonBlocking() {
        this.running = false;
        for (RconClient client : this.clients) {
            client.running = false;
        }
    }
    // Paper end - don't wait for remote connections

    private void closeSocket(ServerSocket socket) {
        LOGGER.debug("closeSocket: {}", socket);

        try {
            socket.close();
        } catch (IOException var3) {
            LOGGER.warn("Failed to close socket", (Throwable)var3);
        }
    }
}
