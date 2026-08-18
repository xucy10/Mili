package fun.bm.mili.utils;

import fun.bm.mili.config.modules.function.AutoBackupConfig;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class AutoBackupManager {
    private static ScheduledExecutorService scheduler;
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss_SSS");
    private static final Object BACKUP_LOCK = new Object();
    private static volatile boolean running = false;
    // Mili start - fix: lastBackupTime 和 lastBackupResult 非 volatile，多线程可见性问题
    private static volatile long lastBackupTime = 0;
    private static volatile String lastBackupResult = "None";
    // Mili end

    // Mili start - fix: start() 中 running=true 在 scheduler 创建之前设置，stop() 可在两者间被调用导致竞态
    public static synchronized void start() {
        if (running) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Mili-Backup-Thread");
            t.setDaemon(true);
            return t;
        });

        long intervalMs = AutoBackupConfig.intervalMinutes * 60_000L;
        scheduler.scheduleWithFixedDelay(AutoBackupManager::performBackup, intervalMs, intervalMs,
                TimeUnit.MILLISECONDS);
        running = true;
    }
    // Mili end

    public static void stop() {
        running = false;
        // Mili start - fix: stop() 调用 shutdownNow() 但不等待终止，改为优雅关闭
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        Bukkit.getLogger().warning("[Mili Backup] Scheduler did not terminate");
                    }
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }
        // Mili end
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
                runBackupCycle(worldName);
                return true;
            // Mili start - fix: catch Throwable instead of Exception to handle Errors
            } catch (Throwable e) {
                lastBackupResult = "Error: " + e.getMessage();
                return false;
            }
            // Mili end
        }, scheduler != null ? scheduler : ForkJoinPool.commonPool());
    }

    private static void performBackup() {
        try {
            runBackupCycle(null);
        // Mili start - fix: catch Throwable instead of Exception to handle Errors in scheduled backup
        } catch (Throwable e) {
            lastBackupResult = "Error: " + e.getMessage();
            Bukkit.getLogger().log(Level.WARNING, "[Mili Backup] Backup failed", e);
        }
        // Mili end
    }

    // Mili start - fix: serialize auto/manual backups and always apply retention cleanup
    private static void runBackupCycle(@Nullable String specificWorld) throws IOException {
        synchronized (BACKUP_LOCK) {
            doBackup(specificWorld);
            cleanupOldBackups();
        }
    }
    // Mili end

    private static void doBackup(@Nullable String specificWorld) throws IOException {
        // Mili start - fix: doBackup 从异步线程调用 Bukkit.broadcast() 等非线程安全 Bukkit API
        org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("Mili");
        // Mili end
        if (AutoBackupConfig.notifyPlayers) {
            // Mili start - fix: 将 Bukkit API 调用调度到主线程执行
            final org.bukkit.plugin.Plugin p = plugin;
            Bukkit.getScheduler().runTask(p, () ->
                    Bukkit.broadcast(net.kyori.adventure.text.Component.text(
                            "[Mili] World backup in progress...", net.kyori.adventure.text.format.NamedTextColor.YELLOW)));
            // Mili end
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
            // Mili start - fix: 将 Bukkit API 调用调度到主线程执行
            final org.bukkit.plugin.Plugin p = plugin;
            final String fmsg = msg;
            Bukkit.getScheduler().runTask(p, () ->
                    Bukkit.broadcast(net.kyori.adventure.text.Component.text(
                            fmsg, net.kyori.adventure.text.format.NamedTextColor.GREEN)));
            // Mili end
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
