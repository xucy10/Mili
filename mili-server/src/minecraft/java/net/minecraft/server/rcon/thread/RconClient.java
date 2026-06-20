package net.minecraft.server.rcon.thread;

import com.mojang.logging.LogUtils;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import net.minecraft.server.ServerInterface;
import net.minecraft.server.rcon.PktUtils;
import org.slf4j.Logger;

public class RconClient extends GenericThread {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SERVERDATA_AUTH = 3;
    private static final int SERVERDATA_EXECCOMMAND = 2;
    private static final int SERVERDATA_RESPONSE_VALUE = 0;
    private static final int SERVERDATA_AUTH_RESPONSE = 2;
    private static final int SERVERDATA_AUTH_FAILURE = -1;
    private boolean authed;
    private final Socket client;
    private final byte[] buf = new byte[1460];
    private final String rconPassword;
    // CraftBukkit start
    private final net.minecraft.server.dedicated.DedicatedServer serverInterface;
    private final net.minecraft.server.rcon.RconConsoleSource rconConsoleSource;
    // CraftBukkit end

    RconClient(ServerInterface serverInterface, String rconPassword, Socket client) {
        super("RCON Client " + client.getInetAddress());
        this.serverInterface = (net.minecraft.server.dedicated.DedicatedServer) serverInterface; // CraftBukkit
        this.client = client;

        try {
            this.client.setSoTimeout(0);
        } catch (Exception var5) {
            this.running = false;
        }

        this.rconPassword = rconPassword;
        this.rconConsoleSource = new net.minecraft.server.rcon.RconConsoleSource(this.serverInterface, client.getRemoteSocketAddress()); // CraftBukkit
    }

    @Override
    public void run() {
        try {
            try {
                while (this.running) {
                    BufferedInputStream bufferedInputStream = new BufferedInputStream(this.client.getInputStream());
                    int i = bufferedInputStream.read(this.buf, 0, 1460);
                    if (10 > i) {
                        return;
                    }

                    int i1 = 0;
                    int i2 = PktUtils.intFromByteArray(this.buf, 0, i);
                    if (i2 != i - 4) {
                        return;
                    }

                    i1 += 4;
                    int i3 = PktUtils.intFromByteArray(this.buf, i1, i);
                    i1 += 4;
                    int i4 = PktUtils.intFromByteArray(this.buf, i1);
                    i1 += 4;
                    switch (i4) {
                        case 2:
                            if (this.authed) {
                                String string1 = PktUtils.stringFromByteArray(this.buf, i1, i);

                                try {
                                    this.sendCmdResponse(i3, this.serverInterface.runCommand(this.rconConsoleSource, string1)); // CraftBukkit
                                } catch (Exception var15) {
                                    this.sendCmdResponse(i3, "Error executing: " + string1 + " (" + var15.getMessage() + ")");
                                }
                                break;
                            }

                            this.sendAuthFailure();
                            break;
                        case 3:
                            String string = PktUtils.stringFromByteArray(this.buf, i1, i);
                            i1 += string.length();
                            if (!string.isEmpty() && string.equals(this.rconPassword)) {
                                this.authed = true;
                                this.send(i3, 2, "");
                                break;
                            }

                            this.authed = false;
                            this.sendAuthFailure();
                            break;
                        default:
                            this.sendCmdResponse(i3, String.format(Locale.ROOT, "Unknown request %s", Integer.toHexString(i4)));
                    }
                }

                return;
            } catch (IOException var16) {
            } catch (Exception var17) {
                LOGGER.error("Exception whilst parsing RCON input", (Throwable)var17);
            }
        } finally {
            this.closeSocket();
            LOGGER.info("Thread {} shutting down", this.name);
            this.running = false;
        }
    }

    private void send(int id, int type, String message) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(1248);
        DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        dataOutputStream.writeInt(Integer.reverseBytes(bytes.length + 10));
        dataOutputStream.writeInt(Integer.reverseBytes(id));
        dataOutputStream.writeInt(Integer.reverseBytes(type));
        dataOutputStream.write(bytes);
        dataOutputStream.write(0);
        dataOutputStream.write(0);
        this.client.getOutputStream().write(byteArrayOutputStream.toByteArray());
    }

    private void sendAuthFailure() throws IOException {
        this.send(-1, 2, "");
    }

    private void sendCmdResponse(int id, String message) throws IOException {
        int len = message.length();

        do {
            int i = 4096 <= len ? 4096 : len;
            this.send(id, 0, message.substring(0, i));
            message = message.substring(i);
            len = message.length();
        } while (0 != len);
    }

    @Override
    public void stop() {
        this.running = false;
        this.closeSocket();
        super.stop();
    }

    private void closeSocket() {
        try {
            this.client.close();
        } catch (IOException var2) {
            LOGGER.warn("Failed to close socket", (Throwable)var2);
        }
    }
}
