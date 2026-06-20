package net.minecraft.server.packs;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;

public class DownloadCacheCleaner {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void vacuumCacheDir(Path path, int maxEntries) {
        try {
            List<DownloadCacheCleaner.PathAndTime> list = listFilesWithModificationTimes(path);
            int i = list.size() - maxEntries;
            if (i <= 0) {
                return;
            }

            list.sort(DownloadCacheCleaner.PathAndTime.NEWEST_FIRST);
            List<DownloadCacheCleaner.PathAndPriority> list1 = prioritizeFilesInDirs(list);
            Collections.reverse(list1);
            list1.sort(DownloadCacheCleaner.PathAndPriority.HIGHEST_PRIORITY_FIRST);
            Set<Path> set = new HashSet<>();

            for (int i1 = 0; i1 < i; i1++) {
                DownloadCacheCleaner.PathAndPriority pathAndPriority = list1.get(i1);
                Path path1 = pathAndPriority.path;

                try {
                    Files.delete(path1);
                    if (pathAndPriority.removalPriority == 0) {
                        set.add(path1.getParent());
                    }
                } catch (IOException var12) {
                    LOGGER.warn("Failed to delete cache file {}", path1, var12);
                }
            }

            set.remove(path);

            for (Path path2 : set) {
                try {
                    Files.delete(path2);
                } catch (DirectoryNotEmptyException var10) {
                } catch (IOException var11) {
                    LOGGER.warn("Failed to delete empty(?) cache directory {}", path2, var11);
                }
            }
        } catch (UncheckedIOException | IOException var13) {
            LOGGER.error("Failed to vacuum cache dir {}", path, var13);
        }
    }

    private static List<DownloadCacheCleaner.PathAndTime> listFilesWithModificationTimes(final Path path) throws IOException {
        try {
            final List<DownloadCacheCleaner.PathAndTime> list = new ArrayList<>();
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (attributes.isRegularFile() && !file.getParent().equals(path)) {
                        FileTime fileTime = attributes.lastModifiedTime();
                        list.add(new DownloadCacheCleaner.PathAndTime(file, fileTime));
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
            return list;
        } catch (NoSuchFileException var2) {
            return List.of();
        }
    }

    private static List<DownloadCacheCleaner.PathAndPriority> prioritizeFilesInDirs(List<DownloadCacheCleaner.PathAndTime> paths) {
        List<DownloadCacheCleaner.PathAndPriority> list = new ArrayList<>();
        Object2IntOpenHashMap<Path> map = new Object2IntOpenHashMap<>();

        for (DownloadCacheCleaner.PathAndTime pathAndTime : paths) {
            int i = map.addTo(pathAndTime.path.getParent(), 1);
            list.add(new DownloadCacheCleaner.PathAndPriority(pathAndTime.path, i));
        }

        return list;
    }

    record PathAndPriority(Path path, int removalPriority) {
        public static final Comparator<DownloadCacheCleaner.PathAndPriority> HIGHEST_PRIORITY_FIRST = Comparator.comparing(
                DownloadCacheCleaner.PathAndPriority::removalPriority
            )
            .reversed();
    }

    record PathAndTime(Path path, FileTime modifiedTime) {
        public static final Comparator<DownloadCacheCleaner.PathAndTime> NEWEST_FIRST = Comparator.comparing(DownloadCacheCleaner.PathAndTime::modifiedTime)
            .reversed();
    }
}
