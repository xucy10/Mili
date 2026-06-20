package net.minecraft.server.chase;

import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Scanner;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.ChaseCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.io.IOUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ChaseClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int RECONNECT_INTERVAL_SECONDS = 5;
    private final String serverHost;
    private final int serverPort;
    private final MinecraftServer server;
    private volatile boolean wantsToRun;
    private @Nullable Socket socket;
    private @Nullable Thread thread;

    public ChaseClient(String serverHost, int serverPort, MinecraftServer server) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.server = server;
    }

    public void start() {
        if (this.thread != null && this.thread.isAlive()) {
            LOGGER.warn("Remote control client was asked to start, but it is already running. Will ignore.");
        }

        this.wantsToRun = true;
        this.thread = new Thread(this::run, "chase-client");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    public void stop() {
        this.wantsToRun = false;
        IOUtils.closeQuietly(this.socket);
        this.socket = null;
        this.thread = null;
    }

    public void run() {
        String string = this.serverHost + ":" + this.serverPort;

        while (this.wantsToRun) {
            try {
                LOGGER.info("Connecting to remote control server {}", string);
                this.socket = new Socket(this.serverHost, this.serverPort);
                LOGGER.info("Connected to remote control server! Will continuously execute the command broadcasted by that server.");

                try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(this.socket.getInputStream(), StandardCharsets.US_ASCII))) {
                    while (this.wantsToRun) {
                        String line = bufferedReader.readLine();
                        if (line == null) {
                            LOGGER.warn("Lost connection to remote control server {}. Will retry in {}s.", string, 5);
                            break;
                        }

                        this.handleMessage(line);
                    }
                } catch (IOException var8) {
                    LOGGER.warn("Lost connection to remote control server {}. Will retry in {}s.", string, 5);
                }
            } catch (IOException var9) {
                LOGGER.warn("Failed to connect to remote control server {}. Will retry in {}s.", string, 5);
            }

            if (this.wantsToRun) {
                try {
                    Thread.sleep(5000L);
                } catch (InterruptedException var5) {
                }
            }
        }
    }

    private void handleMessage(String message) {
        try (Scanner scanner = new Scanner(new StringReader(message))) {
            scanner.useLocale(Locale.ROOT);
            String string = scanner.next();
            if ("t".equals(string)) {
                this.handleTeleport(scanner);
            } else {
                LOGGER.warn("Unknown message type '{}'", string);
            }
        } catch (NoSuchElementException var7) {
            LOGGER.warn("Could not parse message '{}', ignoring", message);
        }
    }

    private void handleTeleport(Scanner scanner) {
        this.parseTarget(scanner)
            .ifPresent(
                teleportTarget -> this.executeCommand(
                    String.format(
                        Locale.ROOT,
                        "execute in %s run tp @s %.3f %.3f %.3f %.3f %.3f",
                        teleportTarget.level.identifier(),
                        teleportTarget.pos.x,
                        teleportTarget.pos.y,
                        teleportTarget.pos.z,
                        teleportTarget.rot.y,
                        teleportTarget.rot.x
                    )
                )
            );
    }

    private Optional<ChaseClient.TeleportTarget> parseTarget(Scanner scanner) {
        ResourceKey<Level> resourceKey = ChaseCommand.DIMENSION_NAMES.get(scanner.next());
        if (resourceKey == null) {
            return Optional.empty();
        } else {
            float f = scanner.nextFloat();
            float f1 = scanner.nextFloat();
            float f2 = scanner.nextFloat();
            float f3 = scanner.nextFloat();
            float f4 = scanner.nextFloat();
            return Optional.of(new ChaseClient.TeleportTarget(resourceKey, new Vec3(f, f1, f2), new Vec2(f4, f3)));
        }
    }

    private void executeCommand(String command) {
        this.server
            .execute(
                () -> {
                    List<ServerPlayer> players = this.server.getPlayerList().getPlayers();
                    if (!players.isEmpty()) {
                        ServerPlayer serverPlayer = players.get(0);
                        ServerLevel serverLevel = this.server.overworld();
                        CommandSourceStack commandSourceStack = new CommandSourceStack(
                            serverPlayer.commandSource(),
                            Vec3.atLowerCornerOf(serverLevel.getRespawnData().pos()),
                            Vec2.ZERO,
                            serverLevel,
                            LevelBasedPermissionSet.OWNER,
                            "",
                            CommonComponents.EMPTY,
                            this.server,
                            serverPlayer
                        );
                        Commands commands = this.server.getCommands();
                        commands.performPrefixedCommand(commandSourceStack, command);
                    }
                }
            );
    }

    record TeleportTarget(ResourceKey<Level> level, Vec3 pos, Vec2 rot) {
    }
}
