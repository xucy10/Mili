package net.minecraft.server.chase;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.ClosedByInterruptException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import net.minecraft.server.commands.ChaseCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.Util;
import org.apache.commons.io.IOUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ChaseServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final String serverBindAddress;
    private final int serverPort;
    private final PlayerList playerList;
    private final int broadcastIntervalMs;
    private volatile boolean wantsToRun;
    private @Nullable ServerSocket serverSocket;
    private final CopyOnWriteArrayList<Socket> clientSockets = new CopyOnWriteArrayList<>();

    public ChaseServer(String serverBindAddress, int serverPort, PlayerList playerList, int broadcastIntervalMs) {
        this.serverBindAddress = serverBindAddress;
        this.serverPort = serverPort;
        this.playerList = playerList;
        this.broadcastIntervalMs = broadcastIntervalMs;
    }

    public void start() throws IOException {
        if (this.serverSocket != null && !this.serverSocket.isClosed()) {
            LOGGER.warn("Remote control server was asked to start, but it is already running. Will ignore.");
        } else {
            this.wantsToRun = true;
            this.serverSocket = new ServerSocket(this.serverPort, 50, InetAddress.getByName(this.serverBindAddress));
            Thread thread = new Thread(this::runAcceptor, "chase-server-acceptor");
            thread.setDaemon(true);
            thread.start();
            Thread thread1 = new Thread(this::runSender, "chase-server-sender");
            thread1.setDaemon(true);
            thread1.start();
        }
    }

    private void runSender() {
        ChaseServer.PlayerPosition playerPosition = null;

        while (this.wantsToRun) {
            if (!this.clientSockets.isEmpty()) {
                ChaseServer.PlayerPosition playerPosition1 = this.getPlayerPosition();
                if (playerPosition1 != null && !playerPosition1.equals(playerPosition)) {
                    playerPosition = playerPosition1;
                    byte[] bytes = playerPosition1.format().getBytes(StandardCharsets.US_ASCII);

                    for (Socket socket : this.clientSockets) {
                        if (!socket.isClosed()) {
                            Util.ioPool().execute(() -> {
                                try {
                                    OutputStream outputStream = socket.getOutputStream();
                                    outputStream.write(bytes);
                                    outputStream.flush();
                                } catch (IOException var3x) {
                                    LOGGER.info("Remote control client socket got an IO exception and will be closed", (Throwable)var3x);
                                    IOUtils.closeQuietly(socket);
                                }
                            });
                        }
                    }
                }

                List<Socket> list = this.clientSockets.stream().filter(Socket::isClosed).collect(Collectors.toList());
                this.clientSockets.removeAll(list);
            }

            if (this.wantsToRun) {
                try {
                    Thread.sleep(this.broadcastIntervalMs);
                } catch (InterruptedException var6) {
                }
            }
        }
    }

    public void stop() {
        this.wantsToRun = false;
        IOUtils.closeQuietly(this.serverSocket);
        this.serverSocket = null;
    }

    private void runAcceptor() {
        try {
            while (this.wantsToRun) {
                if (this.serverSocket != null) {
                    LOGGER.info("Remote control server is listening for connections on port {}", this.serverPort);
                    Socket socket = this.serverSocket.accept();
                    LOGGER.info("Remote control server received client connection on port {}", socket.getPort());
                    this.clientSockets.add(socket);
                }
            }
        } catch (ClosedByInterruptException var6) {
            if (this.wantsToRun) {
                LOGGER.info("Remote control server closed by interrupt");
            }
        } catch (IOException var7) {
            if (this.wantsToRun) {
                LOGGER.error("Remote control server closed because of an IO exception", (Throwable)var7);
            }
        } finally {
            IOUtils.closeQuietly(this.serverSocket);
        }

        LOGGER.info("Remote control server is now stopped");
        this.wantsToRun = false;
    }

    private ChaseServer.@Nullable PlayerPosition getPlayerPosition() {
        List<ServerPlayer> players = this.playerList.getPlayers();
        if (players.isEmpty()) {
            return null;
        } else {
            ServerPlayer serverPlayer = players.get(0);
            String string = ChaseCommand.DIMENSION_NAMES.inverse().get(serverPlayer.level().dimension());
            return string == null
                ? null
                : new ChaseServer.PlayerPosition(
                    string, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(), serverPlayer.getYRot(), serverPlayer.getXRot()
                );
        }
    }

    record PlayerPosition(String dimensionName, double x, double y, double z, float yRot, float xRot) {
        String format() {
            return String.format(Locale.ROOT, "t %s %.2f %.2f %.2f %.2f %.2f\n", this.dimensionName, this.x, this.y, this.z, this.yRot, this.xRot);
        }
    }
}
