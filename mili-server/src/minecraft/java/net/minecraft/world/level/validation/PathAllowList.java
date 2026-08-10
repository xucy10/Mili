package net.minecraft.world.level.validation;

import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

public class PathAllowList implements PathMatcher {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String COMMENT_PREFIX = "#";
    private final List<PathAllowList.ConfigEntry> entries;
    private final Map<String, PathMatcher> compiledPaths = new ConcurrentHashMap<>();

    public PathAllowList(List<PathAllowList.ConfigEntry> entries) {
        this.entries = entries;
    }

    public PathMatcher getForFileSystem(FileSystem fileSystem) {
        return this.compiledPaths.computeIfAbsent(fileSystem.provider().getScheme(), key -> {
            List<PathMatcher> list;
            try {
                list = this.entries.stream().map(configEntry -> configEntry.compile(fileSystem)).toList();
            } catch (Exception var5) {
                LOGGER.error("Failed to compile file pattern list", (Throwable)var5);
                return path -> false;
            }
            return switch (list.size()) {
                case 0 -> path -> false;
                case 1 -> (PathMatcher)list.get(0);
                default -> path -> {
                    for (PathMatcher pathMatcher : list) {
                        if (pathMatcher.matches(path)) {
                            return true;
                        }
                    }

                    return false;
                };
            };
        });
    }

    @Override
    public boolean matches(Path path) {
        return this.getForFileSystem(path.getFileSystem()).matches(path);
    }

    public static PathAllowList readPlain(BufferedReader reader) {
        return new PathAllowList(reader.lines().flatMap(line -> PathAllowList.ConfigEntry.parse(line).stream()).toList());
    }

    public record ConfigEntry(PathAllowList.EntryType type, String pattern) {
        public PathMatcher compile(FileSystem fileSystem) {
            return this.type().compile(fileSystem, this.pattern);
        }

        static Optional<PathAllowList.ConfigEntry> parse(String string) {
            if (string.isBlank() || string.startsWith("#")) {
                return Optional.empty();
            } else if (!string.startsWith("[")) {
                return Optional.of(new PathAllowList.ConfigEntry(PathAllowList.EntryType.PREFIX, string));
            } else {
                int index = string.indexOf(93, 1);
                if (index == -1) {
                    throw new IllegalArgumentException("Unterminated type in line '" + string + "'");
                } else {
                    String sub = string.substring(1, index);
                    String sub1 = string.substring(index + 1);

                    return switch (sub) {
                        case "glob", "regex" -> Optional.of(new PathAllowList.ConfigEntry(PathAllowList.EntryType.FILESYSTEM, sub + ":" + sub1));
                        case "prefix" -> Optional.of(new PathAllowList.ConfigEntry(PathAllowList.EntryType.PREFIX, sub1));
                        default -> throw new IllegalArgumentException("Unsupported definition type in line '" + string + "'");
                    };
                }
            }
        }

        static PathAllowList.ConfigEntry glob(String glob) {
            return new PathAllowList.ConfigEntry(PathAllowList.EntryType.FILESYSTEM, "glob:" + glob);
        }

        static PathAllowList.ConfigEntry regex(String regex) {
            return new PathAllowList.ConfigEntry(PathAllowList.EntryType.FILESYSTEM, "regex:" + regex);
        }

        static PathAllowList.ConfigEntry prefix(String prefix) {
            return new PathAllowList.ConfigEntry(PathAllowList.EntryType.PREFIX, prefix);
        }
    }

    @FunctionalInterface
    public interface EntryType {
        PathAllowList.EntryType FILESYSTEM = FileSystem::getPathMatcher;
        PathAllowList.EntryType PREFIX = (fileSystem, pattern) -> path -> path.toString().startsWith(pattern);

        PathMatcher compile(FileSystem fileSystem, String pattern);
    }
}
