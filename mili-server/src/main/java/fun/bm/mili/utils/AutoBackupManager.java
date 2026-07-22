package fun.bm.mili.utils;

import fun.bm.mili.config.modules.function.AutoBackupConfig;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.stream.Stream;
import java.util.zip.*;
import org.jspecify.annotations.Nullable;

public class AutoBackupManager {
    private static ScheduledExecutorService scheduler;
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static volatile boolean running = false;
    private static long lastBackupTime = 0;
    private static String lastBackupResult = "None";

    public static void start() {
        if (running) return;
        running = true;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Mili-Backup-Thread");
            t.setDaemon(true);
            return t;
        });

        long intervalMs = AutoBackupConfig.intervalMinutes * 60_000L;
        scheduler.scheduleWithFixedDelay(AutoBackupManager::performBackup, intervalMs, intervalMs,
                TimeUnit.MILLISECONDS);
    }

    public static void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    public static boolean isRunning() { return running; }
    public static long getLastBackupTime() { return lastBackupTime; }
    public static String getLastBackupResult() { return lastBackupResult; }

    public static CompletableFuture<Boolean> backupNow() {
        return backupNow((String) null);
    }

    public static CompletableFuture<Boolean> backupNow(String worldName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                doBackup(worldName);
                return true;
            } catch (Exception e) {
                lastBackupResult = "Error: " + e.getMessage();
                return false;
            }
        }, scheduler != null ? scheduler : ForkJoinPool.commonPool());
    }

    private static void performBackup() {
        try {
            doBackup(null);
            cleanupOldBackups();
        } catch (Exception e) {
            lastBackupResult = "Error: " + e.getMessage();
            Bukkit.getLogger().log(Level.WARNING, "[Mili Backup] Backup failed", e);
        }
    }

    private static void doBackup(@Nullable String specificWorld) throws IOException {
        if (AutoBackupConfig.notifyPlayers) {
            Bukkit.broadcast(net.kyori.adventure.text.Component.text(
                    "[Mili] World backup in progress...", net.kyori.adventure.text.format.NamedTextColor.YELLOW));
        }

        String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
        Path backupDir = Path.of(AutoBackupConfig.backupPath);
        Files.createDirectories(backupDir);

        List<World> worlds = new ArrayList<>();
        if (specificWorld != null) {
            World w = Bukkit.getWorld(specificWorld);
            if (w == null) {
                throw new IOException("World not found: " + specificWorld);
            }
            worlds.add(w);
        } else if (AutoBackupConfig.backupWorlds.isEmpty()) {
            worlds.addAll(Bukkit.getWorlds());
        } else {
            for (String name : AutoBackupConfig.backupWorlds) {
                World w = Bukkit.getWorld(name);
                if (w != null) worlds.add(w);
            }
        }

        int worldCount = worlds.size();
        long totalSize = 0;

        for (World world : worlds) {
            Path worldFolder = world.getWorldFolder().toPath();
            String displayName = world.getName();
            if (AutoBackupConfig.compress) {
                Path zipFile = backupDir.resolve(timestamp + "_" + displayName + ".zip");
                totalSize += compressDirectory(worldFolder, zipFile);
            } else {
                Path dest = backupDir.resolve(timestamp + "_" + displayName);
                copyDirectory(worldFolder, dest);
                totalSize += dirSize(dest);
            }
        }

        lastBackupTime = System.currentTimeMillis();
        String worldNames = worlds.stream().map(World::getName).collect(java.util.stream.Collectors.joining(", "));
        lastBackupResult = String.format("OK - %d worlds (%s), %s",
                worldCount, worldNames, formatSize(totalSize));

        if (AutoBackupConfig.notifyPlayers) {
            String msg = specificWorld != null
                    ? "[Mili] Backup complete! World: " + specificWorld + " (" + formatSize(totalSize) + ")"
                    : "[Mili] Backup complete! All worlds (" + formatSize(totalSize) + ")";
            Bukkit.broadcast(net.kyori.adventure.text.Component.text(
                    msg, net.kyori.adventure.text.format.NamedTextColor.GREEN));
        }

        Bukkit.getLogger().info("[Mili Backup] Completed: " + worldNames + " (" + formatSize(totalSize) + ")");
    }

    private static long compressDirectory(Path source, Path target) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(target));
             Stream<Path> paths = Files.walk(source)) {
            long totalSize = 0;
            for (Path path : (Iterable<Path>) paths::iterator) {
                if (Files.isRegularFile(path)) {
                    String relative = source.relativize(path).toString().replace('\\', '/');
                    ZipEntry entry = new ZipEntry(relative);
                    zos.putNextEntry(entry);
                    byte[] bytes = readAllBytesRetry(path);
                    zos.write(bytes);
                    zos.closeEntry();
                    totalSize += bytes.length;
                }
            }
            return totalSize;
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            paths.forEach(src -> {
                try {
                    Path dest = target.resolve(source.relativize(src));
                    if (Files.isDirectory(src)) {
                        Files.createDirectories(dest);
                    } else {
                        copyFileRetry(src, dest);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    private static byte[] readAllBytesRetry(Path path) throws IOException {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                return Files.readAllBytes(path);
            } catch (IOException e) {
                if (attempt < 4) {
                    try { Thread.sleep(100); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                } else {
                    throw e;
                }
            }
        }
        throw new IOException("Unreachable");
    }

    private static void copyFileRetry(Path src, Path dest) throws IOException {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException e) {
                if (attempt < 4) {
                    try { Thread.sleep(100); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                } else {
                    throw e;
                }
            }
        }
    }

    private static void cleanupOldBackups() {
        try {
            Path backupDir = Path.of(AutoBackupConfig.backupPath);
            if (!Files.exists(backupDir)) return;

            List<Path> backups = new ArrayList<>();
            try (Stream<Path> files = Files.list(backupDir)) {
                files.filter(p -> {
                    String name = p.getFileName().toString();
                    return name.endsWith(".zip") || (Files.isDirectory(p) && name.contains("_"));
                }).sorted(Comparator.reverseOrder()).forEach(backups::add);
            }

            while (backups.size() > AutoBackupConfig.maxBackups) {
                Path oldest = backups.remove(backups.size() - 1);
                deleteRecursively(oldest);
            }
        } catch (IOException e) {
            Bukkit.getLogger().log(Level.WARNING, "[Mili Backup] Cleanup failed", e);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (Stream<Path> files = Files.walk(path)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.delete(p); } catch (IOException ignored) {}
                });
            }
        } else {
            Files.delete(path);
        }
    }

    private static long dirSize(Path path) throws IOException {
        try (Stream<Path> walk = Files.walk(path)) {
            return walk.filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try { return Files.size(p); } catch (IOException e) { return 0; }
                    }).sum();
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
